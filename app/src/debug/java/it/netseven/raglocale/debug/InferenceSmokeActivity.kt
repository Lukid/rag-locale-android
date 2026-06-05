package it.netseven.raglocale.debug

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Capabilities
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import dagger.hilt.android.AndroidEntryPoint
import it.netseven.raglocale.chat.ChatContextBuilder
import it.netseven.raglocale.data.PreferencesRepository
import it.netseven.raglocale.inference.InferenceEngine
import it.netseven.raglocale.modelmanager.ModelRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import javax.inject.Inject

@AndroidEntryPoint
class InferenceSmokeActivity : ComponentActivity() {
    @Inject
    lateinit var engine: InferenceEngine

    @Inject
    lateinit var prefs: PreferencesRepository

    @Inject
    lateinit var modelRepository: ModelRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prompt = intent.getStringExtra(EXTRA_PROMPT)?.takeIf { it.isNotBlank() } ?: "Ciao"
        val variant = intent.getStringExtra(EXTRA_VARIANT)?.takeIf { it.isNotBlank() } ?: VARIANT_ENGINE
        lifecycleScope.launch {
            val response = StringBuilder()
            try {
                val modelFile =
                    modelRepository.activeModelFile()
                        ?: error("Nessun modello attivo pronto")
                val backend = prefs.backend.first()
                Log.i(TAG, "Smoke start: variant=$variant, backend=$backend, prompt=${prompt.take(80)}")
                if (variant == VARIANT_ENGINE) {
                    engine.setKeepAliveMinutes(1)
                    engine.load(modelFile.absolutePath, backend)

                    val request = ChatContextBuilder.buildRequest(history = emptyList(), userMessage = prompt)
                    withTimeout(SMOKE_TIMEOUT_MS) {
                        engine.generate(request).collect { chunk ->
                            response.append(chunk)
                            if (response.length >= SMOKE_MAX_CHARS) {
                                throw SmokeStop
                            }
                        }
                    }
                } else {
                    response.append(runDirectSmoke(modelFile.absolutePath, variant, prompt))
                }
                Log.i(TAG, "Smoke success: ${response.toString().take(SMOKE_MAX_CHARS)}")
            } catch (_: SmokeStopException) {
                Log.i(TAG, "Smoke capped: ${response.toString().take(SMOKE_MAX_CHARS)}")
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Smoke timeout dopo ${SMOKE_TIMEOUT_MS}ms: ${response.toString().take(SMOKE_MAX_CHARS)}", e)
            } catch (t: Throwable) {
                Log.e(TAG, "Smoke failed: ${response.toString().take(SMOKE_MAX_CHARS)}", t)
            } finally {
                finish()
            }
        }
    }

    @OptIn(ExperimentalApi::class)
    private suspend fun runDirectSmoke(
        modelPath: String,
        variant: String,
        prompt: String,
    ): String =
        withContext(Dispatchers.IO) {
            val useCpu = variant.contains("cpu")
            val runtimeBackend = if (useCpu) Backend.CPU() else Backend.GPU()
            val enableSpeculativeDecoding = variant.contains("spec")
            val engineConfig =
                when (variant) {
                    VARIANT_DIRECT_ALL_TEXT ->
                        directConfig(
                            modelPath = modelPath,
                            backend = runtimeBackend,
                            visionBackend = Backend.GPU(),
                            audioBackend = Backend.CPU(),
                        )
                    VARIANT_DIRECT_ALL_TEXT_SPEC ->
                        directConfig(
                            modelPath = modelPath,
                            backend = runtimeBackend,
                            visionBackend = Backend.GPU(),
                            audioBackend = Backend.CPU(),
                        )
                    VARIANT_DIRECT_AUDIO_EMPTY ->
                        directConfig(
                            modelPath = modelPath,
                            backend = runtimeBackend,
                            visionBackend = null,
                            audioBackend = Backend.CPU(),
                        )
                    VARIANT_DIRECT_ALL_PLACEHOLDER ->
                        directConfig(
                            modelPath = modelPath,
                            backend = runtimeBackend,
                            visionBackend = Backend.GPU(),
                            audioBackend = Backend.CPU(),
                            maxNumImages = 1,
                        )
                    else ->
                        directConfig(
                            modelPath = modelPath,
                            backend = runtimeBackend,
                            visionBackend = null,
                            audioBackend = null,
                        )
                }
            val supportsSpeculativeDecoding = modelSupportsSpeculativeDecoding(modelPath)
            Log.i(
                TAG,
                "Direct config: variant=$variant, backend=${if (useCpu) "CPU" else "GPU"}, " +
                    "spec=$enableSpeculativeDecoding, supportsSpec=$supportsSpeculativeDecoding",
            )
            ExperimentalFlags.enableSpeculativeDecoding = enableSpeculativeDecoding
            val engine = Engine(engineConfig)
            var conversation: com.google.ai.edge.litertlm.Conversation? = null
            try {
                try {
                    engine.initialize()
                } finally {
                    ExperimentalFlags.enableSpeculativeDecoding = false
                }
                conversation =
                    engine.createConversation(
                        ConversationConfig(
                            samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.8),
                        ),
                    )
                val contents =
                    when (variant) {
                        VARIANT_DIRECT_AUDIO_EMPTY ->
                            Contents.of(Content.AudioBytes(silenceWav()), Content.Text(prompt))
                        VARIANT_DIRECT_ALL_PLACEHOLDER ->
                            Contents.of(Content.ImageBytes(onePixelPng()), Content.AudioBytes(silenceWav()), Content.Text(prompt))
                        else -> Contents.of(Content.Text(prompt))
                    }
                val done = CompletableDeferred<String>()
                val response = StringBuilder()
                withTimeout(SMOKE_TIMEOUT_MS) {
                    conversation.sendMessageAsync(
                        contents,
                        object : MessageCallback {
                            override fun onMessage(message: Message) {
                                response.append(message.textChunk())
                                if (response.length >= SMOKE_MAX_CHARS) {
                                    done.complete(response.toString())
                                }
                            }

                            override fun onDone() {
                                done.complete(response.toString())
                            }

                            override fun onError(throwable: Throwable) {
                                done.completeExceptionally(throwable)
                            }
                        },
                    )
                    done.await()
                }
            } finally {
                conversation?.close()
                engine.close()
            }
        }

    private fun directConfig(
        modelPath: String,
        backend: Backend,
        visionBackend: Backend?,
        audioBackend: Backend?,
        maxNumImages: Int? = null,
    ): EngineConfig =
        EngineConfig(
            modelPath = modelPath,
            backend = backend,
            visionBackend = visionBackend,
            audioBackend = audioBackend,
            maxNumTokens = 4000,
            maxNumImages = maxNumImages,
            cacheDir = null,
        )

    private fun modelSupportsSpeculativeDecoding(modelPath: String): Boolean =
        try {
            Capabilities(modelPath).use { it.hasSpeculativeDecodingSupport() }
        } catch (e: Exception) {
            Log.w(TAG, "Impossibile leggere le capabilities del modello", e)
            false
        }

    private fun onePixelPng(): ByteArray {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    private fun silenceWav(): ByteArray {
        val sampleRate = 16_000
        val channels = 1
        val bitsPerSample = 16
        val pcm = ByteArray(sampleRate / 4 * channels * bitsPerSample / 8)
        val header = ByteArray(WAV_HEADER_BYTES)
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val fileSizeMinusEight = 36 + pcm.size

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        writeIntLe(header, 4, fileSizeMinusEight)
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        writeIntLe(header, 16, 16)
        writeShortLe(header, 20, 1)
        writeShortLe(header, 22, channels)
        writeIntLe(header, 24, sampleRate)
        writeIntLe(header, 28, byteRate)
        writeShortLe(header, 32, blockAlign)
        writeShortLe(header, 34, bitsPerSample)
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        writeIntLe(header, 40, pcm.size)
        return header + pcm
    }

    private fun writeIntLe(
        target: ByteArray,
        offset: Int,
        value: Int,
    ) {
        target[offset] = (value and 0xff).toByte()
        target[offset + 1] = (value shr 8 and 0xff).toByte()
        target[offset + 2] = (value shr 16 and 0xff).toByte()
        target[offset + 3] = (value shr 24 and 0xff).toByte()
    }

    private fun writeShortLe(
        target: ByteArray,
        offset: Int,
        value: Int,
    ) {
        target[offset] = (value and 0xff).toByte()
        target[offset + 1] = (value shr 8 and 0xff).toByte()
    }

    private fun Message.textChunk(): String {
        val text =
            contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString(separator = "") { it.text }
        return text.ifEmpty { toString() }
    }

    private object SmokeStop : SmokeStopException()

    private open class SmokeStopException : RuntimeException()

    companion object {
        private const val TAG = "InferenceSmoke"
        private const val EXTRA_PROMPT = "prompt"
        private const val EXTRA_VARIANT = "variant"
        private const val VARIANT_ENGINE = "engine"
        private const val VARIANT_DIRECT_TEXT = "direct_text"
        private const val VARIANT_DIRECT_TEXT_CPU = "direct_text_cpu"
        private const val VARIANT_DIRECT_TEXT_GPU_SPEC = "direct_text_gpu_spec"
        private const val VARIANT_DIRECT_TEXT_CPU_SPEC = "direct_text_cpu_spec"
        private const val VARIANT_DIRECT_ALL_TEXT = "direct_all_text"
        private const val VARIANT_DIRECT_ALL_TEXT_SPEC = "direct_all_text_spec"
        private const val VARIANT_DIRECT_AUDIO_EMPTY = "direct_audio_empty"
        private const val VARIANT_DIRECT_ALL_PLACEHOLDER = "direct_all_placeholder"
        private const val WAV_HEADER_BYTES = 44
        private const val SMOKE_MAX_CHARS = 200
        private const val SMOKE_TIMEOUT_MS = 60_000L
    }
}
