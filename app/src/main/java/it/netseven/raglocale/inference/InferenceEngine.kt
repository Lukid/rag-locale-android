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
import com.google.ai.edge.litertlm.SamplerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
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
 *   `Conversation` viene ricreata a ogni generazione per un controllo *esplicito* del
 *   contesto — la cronologia è inclusa nel prompt da
 *   [it.netseven.raglocale.chat.ChatContextBuilder]. L'Engine resta caldo.
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
        private var currentBackend: Backend? = null

        private val generationMutex = Mutex()
        private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        private var keepAliveJob: Job? = null

        @Volatile
        private var keepAliveMinutes: Int = DEFAULT_KEEP_ALIVE_MINUTES

        fun setKeepAliveMinutes(minutes: Int) {
            keepAliveMinutes = if (minutes > 0) minutes else DEFAULT_KEEP_ALIVE_MINUTES
        }

        /**
         * Carica il modello sul backend preferito, con fallback GPU→CPU.
         * Idempotente: se è già pronto con lo stesso path, resetta solo il keep-alive (D5).
         */
        suspend fun load(
            modelPath: String,
            preferred: Backend,
        ) = withContext(Dispatchers.IO) {
            if (_isReady.value && currentModelPath == modelPath) {
                resetKeepAlive()
                return@withContext
            }
            unloadInternal()
            _state.value = EngineState.Loading

            if (!File(modelPath).exists()) {
                _state.value = EngineState.Error("File modello non trovato: $modelPath")
                return@withContext
            }

            var gpuFailed = false
            val effective: Backend
            if (preferred == Backend.GPU) {
                if (tryInit(modelPath, Backend.GPU)) {
                    effective = Backend.GPU
                } else {
                    gpuFailed = true
                    if (!tryInit(modelPath, Backend.CPU)) {
                        _state.value = EngineState.Error("Inizializzazione del modello fallita su GPU e CPU.")
                        return@withContext
                    }
                    effective = Backend.CPU
                }
            } else {
                if (!tryInit(modelPath, Backend.CPU)) {
                    _state.value = EngineState.Error("Inizializzazione del modello fallita su CPU.")
                    return@withContext
                }
                effective = Backend.CPU
            }

            val resolution = BackendSelection.resolve(preferred, gpuInitFailed = gpuFailed)
            currentModelPath = modelPath
            currentBackend = effective
            _isReady.value = true
            _state.value = EngineState.Ready(resolution.effective, resolution.didFallback, resolution.warning)
            startKeepAlive()
        }

        private fun tryInit(
            path: String,
            backend: Backend,
        ): Boolean =
            try {
                Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
                val config =
                    EngineConfig(
                        modelPath = path,
                        backend = backend.toLiteRt(),
                        cacheDir = appContext.cacheDir.absolutePath,
                    )
                val newEngine = Engine(config)
                newEngine.initialize()
                engine = newEngine
                Log.i(TAG, "Engine inizializzato su $backend")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Init fallita su $backend", e)
                engine = null
                false
            } catch (e: Error) {
                // UnsatisfiedLinkError e simili (es. GPU/OpenCL assente)
                Log.e(TAG, "Errore nativo init su $backend", e)
                engine = null
                false
            }

        /**
         * Genera la risposta in **streaming** (D4): emette ogni chunk man mano che LiteRT-LM
         * lo produce. Serializzata (una conversazione alla volta). La cronologia di sessione
         * deve essere già inclusa nel [prompt].
         */
        fun generate(prompt: String): Flow<String> =
            flow {
                val activeEngine = engine ?: throw IllegalStateException("Modello non caricato")
                generationMutex.withLock {
                    resetKeepAlive()
                    val conversation: Conversation = activeEngine.createConversation(defaultConversationConfig())
                    try {
                        conversation.sendMessageAsync(Contents.of(Content.Text(prompt)))
                            .catch { err -> Log.e(TAG, "Errore streaming", err) }
                            .collect { message -> emit(message.toString()) }
                    } finally {
                        conversation.close()
                        resetKeepAlive()
                    }
                }
            }.flowOn(Dispatchers.IO)

        private fun defaultConversationConfig(): ConversationConfig =
            ConversationConfig(
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.8),
            )

        /** Scarica il modello dalla memoria. */
        fun unload() {
            unloadInternal()
            _state.value = EngineState.Idle
        }

        private fun unloadInternal() {
            cancelKeepAlive()
            try {
                engine?.close()
            } catch (t: Throwable) {
                Log.w(TAG, "Errore in chiusura engine", t)
            }
            engine = null
            currentModelPath = null
            currentBackend = null
            _isReady.value = false
        }

        private fun startKeepAlive() {
            cancelKeepAlive()
            keepAliveJob =
                scope.launch {
                    delay(keepAliveMinutes * 60_000L)
                    if (_isReady.value) {
                        Log.i(TAG, "Keep-alive scaduto: auto-unload del modello")
                        unload()
                    }
                }
        }

        private fun resetKeepAlive() {
            if (_isReady.value) startKeepAlive()
        }

        private fun cancelKeepAlive() {
            keepAliveJob?.cancel()
            keepAliveJob = null
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
