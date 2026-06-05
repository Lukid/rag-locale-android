package it.netseven.raglocale.inference

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager singleton dell'inferenza on-device su **LiteRT-LM** (vedi memory reference-anti-vocale).
 *
 * - **Modello residente** (D5): l'init costoso (~9s su GPU) avviene una sola volta; la
 *   `Conversation` viene ricreata a ogni generazione passando system prompt e cronologia
 *   strutturata a `ConversationConfig`. L'Engine resta caldo.
 * - **Default GPU** con **fallback automatico GPU→CPU** all'init fallita (D3), risolto
 *   da [BackendSelection].
 * - **Una sola conversazione alla volta**: gli accessi sono serializzati con un [Mutex].
 * - **Keep-alive con auto-unload** dopo inattività (igiene RAM).
 */
@Singleton
class InferenceEngine
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
    ) {
        private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
        val state: StateFlow<EngineState> = _state.asStateFlow()

        private val _isReady = MutableStateFlow(false)
        val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

        private var engine: Engine? = null
        private var currentModelPath: String? = null
        private var currentRequestedBackend: Backend? = null
        private var currentEffectiveBackend: Backend? = null

        private val generationMutex = Mutex()
        private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        private var keepAliveJob: Job? = null

        @Volatile
        private var keepAliveMinutes: Int = DEFAULT_KEEP_ALIVE_MINUTES

        fun setKeepAliveMinutes(minutes: Int) {
            keepAliveMinutes = if (minutes > 0) minutes else DEFAULT_KEEP_ALIVE_MINUTES
        }

        /**
         * Carica il modello sul backend preferito, con fallback GPU->CPU.
         * Idempotente solo su path + backend richiesto: cambiare GPU/CPU ricrea l'engine.
         */
        suspend fun load(
            modelPath: String,
            preferred: Backend,
        ) = withContext(Dispatchers.IO) {
            generationMutex.withLock {
                if (_isReady.value && currentModelPath == modelPath && currentRequestedBackend == preferred) {
                    startKeepAlive()
                    return@withLock
                }

                unloadInternalLocked()
                _state.value = EngineState.Loading

                if (!File(modelPath).exists()) {
                    _state.value = EngineState.Error("File modello non trovato: $modelPath")
                    return@withLock
                }

                var gpuFailed = false
                val effective: Backend
                val initializedEngine: Engine
                if (preferred == Backend.GPU) {
                    val gpuEngine = tryCreateEngine(modelPath, Backend.GPU)
                    if (gpuEngine != null) {
                        initializedEngine = gpuEngine
                        effective = Backend.GPU
                    } else {
                        gpuFailed = true
                        val cpuEngine = tryCreateEngine(modelPath, Backend.CPU)
                        if (cpuEngine == null) {
                            _state.value = EngineState.Error("Inizializzazione del modello fallita su GPU e CPU.")
                            return@withLock
                        }
                        initializedEngine = cpuEngine
                        effective = Backend.CPU
                    }
                } else {
                    val cpuEngine = tryCreateEngine(modelPath, Backend.CPU)
                    if (cpuEngine == null) {
                        _state.value = EngineState.Error("Inizializzazione del modello fallita su CPU.")
                        return@withLock
                    }
                    initializedEngine = cpuEngine
                    effective = Backend.CPU
                }

                val resolution = BackendSelection.resolve(preferred, gpuInitFailed = gpuFailed)
                engine = initializedEngine
                currentModelPath = modelPath
                currentRequestedBackend = preferred
                currentEffectiveBackend = effective
                _isReady.value = true
                _state.value = EngineState.Ready(resolution.effective, resolution.didFallback, resolution.warning)
                startKeepAlive()
            }
        }

        private fun tryCreateEngine(
            path: String,
            backend: Backend,
        ): Engine? {
            var newEngine: Engine? = null
            return try {
                Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
                Log.i(
                    TAG,
                    "Init LiteRT-LM chat text-only: backend=$backend, visionBackend=null, " +
                        "audioBackend=null, maxNumTokens=4000",
                )
                val config =
                    EngineConfig(
                        modelPath = path,
                        backend = backend.toLiteRt(),
                        // AI Edge Gallery inizializza il task "AI Chat" con supportImage=false e
                        // supportAudio=false; i backend vision/audio si accendono solo nei task
                        // immagine/audio. Forzarli qui provoca "Input tensor 11 lacks data" in
                        // generazione e token corrotti sul Poco.
                        visionBackend = null,
                        audioBackend = null,
                        maxNumTokens = 4000,
                        cacheDir = if (path.startsWith("/data/local/tmp")) appContext.cacheDir.absolutePath else null,
                    )
                newEngine = Engine(config)
                newEngine.initialize()
                Log.i(TAG, "Engine inizializzato su $backend")
                newEngine
            } catch (e: Exception) {
                Log.e(TAG, "Init fallita su $backend", e)
                closeQuietly(newEngine)
                null
            } catch (e: Error) {
                // UnsatisfiedLinkError e simili (es. GPU/OpenCL assente)
                Log.e(TAG, "Errore nativo init su $backend", e)
                closeQuietly(newEngine)
                null
            }
        }

        /**
         * Genera la risposta in **streaming** (D4): emette ogni chunk man mano che LiteRT-LM
         * lo produce. Serializzata (una conversazione alla volta).
         */
        fun generate(request: ConversationRequest): Flow<String> =
            channelFlow {
                generationMutex.withLock {
                    cancelKeepAlive()
                    val activeEngine = engine ?: throw IllegalStateException("Modello non caricato")
                    val conversation: Conversation = activeEngine.createConversation(conversationConfigFor(request))
                    val done = CompletableDeferred<Unit>()
                    try {
                        conversation.sendMessageAsync(
                            Contents.of(Content.Text(request.userMessage.trim())),
                            object : MessageCallback {
                                override fun onMessage(message: Message) {
                                    trySend(message.textChunk())
                                }

                                override fun onDone() {
                                    done.complete(Unit)
                                }

                                override fun onError(throwable: Throwable) {
                                    Log.e(TAG, "Errore streaming", throwable)
                                    done.completeExceptionally(throwable)
                                }
                            },
                        )
                        done.await()
                    } finally {
                        conversation.close()
                        startKeepAlive()
                    }
                }
            }.flowOn(Dispatchers.IO)

        private fun conversationConfigFor(request: ConversationRequest): ConversationConfig =
            ConversationConfig(
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.8),
                systemInstruction = request.systemInstruction?.let { Contents.of(it) },
                initialMessages =
                    request.initialMessages.map { turn ->
                        when (turn.role) {
                            ConversationRole.USER -> Message.user(turn.text)
                            ConversationRole.MODEL -> Message.model(turn.text)
                        }
                    },
            )

        /** Scarica il modello dalla memoria. */
        fun unload() {
            scope.launch {
                generationMutex.withLock {
                    unloadInternalLocked()
                    _state.value = EngineState.Idle
                }
            }
        }

        private fun unloadInternalLocked(cancelTimer: Boolean = true) {
            if (cancelTimer) cancelKeepAlive()
            closeQuietly(engine)
            engine = null
            currentModelPath = null
            currentRequestedBackend = null
            currentEffectiveBackend = null
            _isReady.value = false
        }

        private fun startKeepAlive() {
            cancelKeepAlive()
            keepAliveJob =
                scope.launch {
                    delay(keepAliveMinutes * 60_000L)
                    generationMutex.withLock {
                        if (_isReady.value) {
                            Log.i(TAG, "Keep-alive scaduto: auto-unload del modello")
                            unloadInternalLocked(cancelTimer = false)
                            keepAliveJob = null
                            _state.value = EngineState.Idle
                        }
                    }
                }
        }

        private fun cancelKeepAlive() {
            keepAliveJob?.cancel()
            keepAliveJob = null
        }

        private fun closeQuietly(engineToClose: Engine?) {
            try {
                engineToClose?.close()
            } catch (t: Throwable) {
                Log.w(TAG, "Errore in chiusura engine", t)
            }
        }

        companion object {
            private const val TAG = "InferenceEngine"
            const val DEFAULT_KEEP_ALIVE_MINUTES = 5
        }
    }

/** Mappa il nostro enum [Backend] sul tipo backend di LiteRT-LM. */
private fun Backend.toLiteRt(): com.google.ai.edge.litertlm.Backend =
    when (this) {
        Backend.GPU -> com.google.ai.edge.litertlm.Backend.GPU()
        Backend.CPU -> com.google.ai.edge.litertlm.Backend.CPU()
    }

private fun Message.textChunk(): String {
    val text =
        contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString(separator = "") { it.text }
    return text.ifEmpty { toString() }
}
