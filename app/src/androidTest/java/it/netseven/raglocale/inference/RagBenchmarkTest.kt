package it.netseven.raglocale.inference

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.netseven.raglocale.chat.RagPromptBuilder
import it.netseven.raglocale.retrieval.ChunkRecuperato
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Benchmark **on-device** GPU vs CPU sul workload RAG (task 7.4 del Milestone 2, recupera
 * il benchmark formale rinviato da M1): prefill lungo — prompt grounded costruito con il
 * [RagPromptBuilder] di produzione su `topK = 5` chunk da ~1000 caratteri (i default
 * confermati in 7.2) — seguito dalla generazione in streaming.
 *
 * Metriche per run, lette da logcat (tag `RagBenchmark`):
 * - caricamento del modello (engine freddo);
 * - TTFT = tempo al primo chunk dello stream (dominato dal prefill: è qui che la GPU
 *   dovrebbe vincere, design M2 D10);
 * - durata totale e caratteri/secondo della fase di decode (al netto del TTFT).
 *
 * Non è un test di correttezza: le asserzioni sono il minimo per fidarsi dei numeri
 * (backend effettivo = richiesto, output non vuoto). Prerequisito: LLM importato
 * (`gemma-4-E2B-it.litertlm`); se assente il test viene saltato.
 *
 * Esecuzione sicura sul device di sviluppo (NIENTE `connectedAndroidTest`: wipe dei dati!):
 * `adb install -r -t` dei due APK, poi
 * `adb shell am instrument -w -e class it.netseven.raglocale.inference.RagBenchmarkTest \
 *   it.netseven.raglocale.test/androidx.test.runner.AndroidJUnitRunner`.
 */
@RunWith(AndroidJUnit4::class)
class RagBenchmarkTest {
    @Test
    fun benchmarkWorkloadRagSuGpu() {
        eseguiBenchmark(Backend.GPU)
    }

    @Test
    fun benchmarkWorkloadRagSuCpu() {
        eseguiBenchmark(Backend.CPU)
    }

    private fun eseguiBenchmark(backend: Backend) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = File(context.filesDir, "models/$MODEL_FILE_NAME")
        assumeTrue("Modello non importato nell'app: ${modelFile.path}", modelFile.exists())

        val prompt = RagPromptBuilder.costruisci(DOMANDA, chunkFinti())
        Log.i(TAG, "backend=$backend prompt=${prompt.length} caratteri (topK=$TOP_K, chunk~$CHUNK_SIZE)")

        val engine = InferenceEngine(context)
        try {
            runBlocking {
                val tLoad = System.currentTimeMillis()
                engine.load(modelFile.absolutePath, backend)
                val durataLoad = System.currentTimeMillis() - tLoad
                assertTrue("Engine non pronto su $backend: ${engine.state.value}", engine.isReady.value)

                // Il fallback silenzioso GPU→CPU falserebbe il confronto: se il backend
                // effettivo non è quello richiesto, il run GPU si salta (non fallisce).
                val ready = engine.state.value as EngineState.Ready
                assumeTrue(
                    "Backend effettivo ${ready.backend} ≠ richiesto $backend (fallback): benchmark non significativo",
                    ready.backend == backend,
                )

                repeat(RUNS) { run ->
                    var ttftMs = -1L
                    val output = StringBuilder()
                    val tGen = System.currentTimeMillis()
                    withTimeout(GENERATION_TIMEOUT_MS) {
                        engine
                            .generate(
                                ConversationRequest(
                                    systemInstruction = RAG_SYSTEM_INSTRUCTION,
                                    initialMessages = emptyList(),
                                    userMessage = prompt,
                                ),
                            ).collect { chunk ->
                                if (ttftMs < 0) ttftMs = System.currentTimeMillis() - tGen
                                output.append(chunk)
                            }
                    }
                    val totaleMs = System.currentTimeMillis() - tGen
                    assertTrue("Output vuoto su $backend (run $run)", output.isNotBlank())

                    val decodeMs = (totaleMs - ttftMs).coerceAtLeast(1)
                    val charsPerSec = output.length * 1000.0 / decodeMs
                    Log.i(
                        TAG,
                        "backend=$backend run=$run load=${durataLoad}ms ttft=${ttftMs}ms " +
                            "totale=${totaleMs}ms output=${output.length} caratteri " +
                            "decode=%.1f caratteri/s".format(charsPerSec),
                    )
                }
            }
        } finally {
            engine.unload()
        }
    }

    /**
     * Cinque chunk deterministici da ~1000 caratteri di italiano vario (frasi ruotate per
     * chunk, niente testo casuale: run ripetibili). La risposta alla [DOMANDA] sta nel
     * primo chunk, come in un retrieval riuscito.
     */
    private fun chunkFinti(): List<ChunkRecuperato> =
        (0 until TOP_K).map { indice ->
            val frasi = FRASI.drop(indice % FRASI.size) + FRASI.take(indice % FRASI.size)
            val testo =
                buildString {
                    append("Sezione ${indice + 1} del manuale dell'osservatorio. ")
                    if (indice == 0) append(FATTO)
                    while (length < CHUNK_SIZE) append(frasi[(length / 80) % frasi.size]).append(' ')
                }.take(CHUNK_SIZE).trim()
            ChunkRecuperato(testo = testo, score = 0.9 - indice * 0.1, documento = "manuale.txt", indiceChunk = indice)
        }

    companion object {
        private const val TAG = "RagBenchmark"
        private const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
        private const val GENERATION_TIMEOUT_MS = 240_000L
        private const val RUNS = 2
        private const val TOP_K = 5
        private const val CHUNK_SIZE = 1000

        private const val DOMANDA = "In quale anno è stato inaugurato l'osservatorio e da chi?"
        private const val FATTO =
            "L'osservatorio astronomico di Monte Chiaro è stato inaugurato nel 1962 dal direttore Elena Sartori. "

        // Stessa istruzione di sistema della modalità RAG in chat (ChatViewModel): il
        // benchmark deve misurare il workload reale, prompt compreso.
        private const val RAG_SYSTEM_INSTRUCTION =
            "Rispondi SOLO usando le informazioni del contesto fornito. Se la risposta non è nel contesto, " +
                "dichiara che l'informazione non è presente nel documento e non inventare nulla. " +
                "Cita i passaggi usati nel formato [n]. Rispondi in italiano, in modo conciso."

        private val FRASI =
            listOf(
                "La cupola principale ruota su un binario circolare lubrificato due volte all'anno dal personale tecnico.",
                "Il telescopio riflettore ha uno specchio primario di novanta centimetri rivestito in alluminio.",
                "Le osservazioni notturne sono riservate ai ricercatori, mentre il pubblico accede il sabato pomeriggio.",
                "La stazione meteorologica registra temperatura, umidità e velocità del vento ogni quindici minuti.",
                "L'archivio fotografico conserva oltre dodicimila lastre catalogate per data e regione di cielo.",
                "Il sentiero di accesso parte dal borgo e sale per tre chilometri attraverso un bosco di castagni.",
                "La biblioteca interna raccoglie trattati di astronomia pubblicati a partire dal diciottesimo secolo.",
                "Durante l'inverno la strada può essere chiusa per neve e si raggiunge la vetta solo a piedi.",
            )
    }
}
