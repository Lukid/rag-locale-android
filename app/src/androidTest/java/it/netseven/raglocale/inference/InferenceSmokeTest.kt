package it.netseven.raglocale.inference

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Smoke test **on-device** dell'inferenza reale, senza UI: carica il modello già importato
 * nell'app (`files/models/`) e verifica che una generazione text-only produca testo plausibile.
 *
 * Contesto (vedi `docs/m1-inference-review-2026-06-04.md`): il sintomo "Input tensor 11
 * lacks data" + gibberish era causato da una copia del modello corrotta a parità di
 * dimensione. Questo test becca sia lo stream vuoto sia l'output degenerato, e resta
 * come guardia di regressione per import/runtime.
 *
 * Prerequisito: `gemma-4-E2B-it.litertlm` sano importato nell'app (md5 atteso
 * `1b8446203a216cfd31f6a2a22f75e5e5`). Se assente il test viene saltato, non fallito.
 */
@RunWith(AndroidJUnit4::class)
class InferenceSmokeTest {
    @Test
    fun generazioneTextOnlySuCpuProduceTestoPlausibile() {
        eseguiSmoke(Backend.CPU)
    }

    @Test
    fun generazioneTextOnlySuGpuProduceTestoPlausibile() {
        eseguiSmoke(Backend.GPU)
    }

    private fun eseguiSmoke(backend: Backend) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = File(context.filesDir, "models/$MODEL_FILE_NAME")
        assumeTrue("Modello non importato nell'app: ${modelFile.path}", modelFile.exists())

        val engine = InferenceEngine(context)
        val output = StringBuilder()
        try {
            runBlocking {
                engine.load(modelFile.absolutePath, backend)
                assertTrue(
                    "Engine non pronto dopo load su $backend: ${engine.state.value}",
                    engine.isReady.value,
                )
                withTimeout(GENERATION_TIMEOUT_MS) {
                    engine
                        .generate(
                            ConversationRequest(
                                systemInstruction = "Rispondi in italiano, in una sola frase.",
                                initialMessages = emptyList(),
                                userMessage = "Presentati in una frase.",
                            ),
                        ).collect { chunk -> output.append(chunk) }
                }
            }
        } finally {
            engine.unload()
        }

        val text = output.toString().trim()
        assertTrue("Risposta vuota su $backend", text.isNotEmpty())
        // Quota minima di caratteri "di parola": l'output corrotto osservato sul campo
        // (token casuali, simboli) scende ben sotto questa soglia.
        val wordChars = text.count { it.isLetter() || it.isWhitespace() }
        assertTrue(
            "Risposta sospetta su $backend (gibberish?): $text",
            text.length >= MIN_TEXT_LENGTH && wordChars.toDouble() / text.length > MIN_WORD_CHAR_RATIO,
        )
    }

    companion object {
        private const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
        private const val GENERATION_TIMEOUT_MS = 240_000L
        private const val MIN_TEXT_LENGTH = 10
        private const val MIN_WORD_CHAR_RATIO = 0.6
    }
}
