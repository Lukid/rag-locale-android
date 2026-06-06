package it.netseven.raglocale.retrieval

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.localagents.rag.models.EmbedData
import com.google.ai.edge.localagents.rag.models.EmbeddingRequest
import com.google.ai.edge.localagents.rag.models.GemmaEmbeddingModel
import it.netseven.raglocale.inference.Backend
import it.netseven.raglocale.inference.ConversationRequest
import it.netseven.raglocale.inference.InferenceEngine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Spike on-device dell'embedder (task 1.2/1.3 del Milestone 2): verifica che
 * `GemmaEmbeddingModel` (modulo embedder di localagents-rag 0.3.0) carichi EmbeddingGemma
 * sul device e produca embedding semanticamente sensati su frasi italiane.
 *
 * Verifica anche la convivenza in-process con il motore LLM LiteRT-LM (Open Question
 * del design M2, D2): JNI separate, nessun conflitto atteso — ma va dimostrato.
 *
 * Prerequisiti (importati in `files/models/`, vedi design M2 D8):
 * - `embeddinggemma-300M_seq512_mixed-precision.tflite` (md5 `edd86dab69e9333794ed983b4ab6d0d3`)
 * - `sentencepiece.model` (md5 `b0cab25d6777ffdf26856aaf6316fbbc`)
 * Se assenti il test viene saltato, non fallito.
 */
@RunWith(AndroidJUnit4::class)
class EmbedderSmokeTest {
    @Test
    fun embeddingSuCpuDistingueLaParafrasi() {
        val embedder = creaEmbedder()

        // Documenti: A e la query sono parafrasi (nessuna parola chiave in comune
        // forzata), C è l'estraneo di controllo.
        val docA = "Il gatto dorme tranquillo sul divano del soggiorno."
        val docB = "Un felino riposa sereno sul sofà in salotto."
        val docC = "La fotosintesi clorofilliana trasforma la luce solare in energia chimica."
        val query = "Dove sta dormendo il micio?"

        val tInit = System.currentTimeMillis()
        val vQuery = embedQuery(embedder, query)
        val latenzaPrimaChiamata = System.currentTimeMillis() - tInit

        val tDocs = System.currentTimeMillis()
        val vA = embedDocument(embedder, docA)
        val vB = embedDocument(embedder, docB)
        val vC = embedDocument(embedder, docC)
        val latenzaMediaDoc = (System.currentTimeMillis() - tDocs) / 3

        // Dimensione attesa: 768 (EmbeddingGemma, output pieno senza troncamento MRL).
        assertEquals("Dimensione embedding inattesa", DIMENSIONE_ATTESA, vQuery.size)
        assertEquals(DIMENSIONE_ATTESA, vA.size)

        val normaQuery = norma(vQuery)
        assertTrue("Embedding degenere (norma ~0)", normaQuery > 1e-6)

        val simQA = cosine(vQuery, vA)
        val simQB = cosine(vQuery, vB)
        val simQC = cosine(vQuery, vC)

        Log.i(
            TAG,
            "prima chiamata=${latenzaPrimaChiamata}ms, media doc=${latenzaMediaDoc}ms, " +
                "norma query=$normaQuery (normalizzato=${abs(normaQuery - 1.0) < 0.01}), " +
                "sim(q,A)=$simQA sim(q,B)=$simQB sim(q,C)=$simQC",
        )

        // Il senso del test: la query sul micio deve stare più vicina ai divani
        // che alla fotosintesi, con margine netto.
        assertTrue("sim(q,A)=$simQA non supera sim(q,C)=$simQC", simQA > simQC + MARGINE_MINIMO)
        assertTrue("sim(q,B)=$simQB non supera sim(q,C)=$simQC", simQB > simQC + MARGINE_MINIMO)
    }

    @Test
    fun embedderConviveColMotoreLlm() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val llmFile = File(context.filesDir, "models/$LLM_FILE_NAME")
        assumeTrue("LLM non importato nell'app: ${llmFile.path}", llmFile.exists())

        val embedder = creaEmbedder()
        val engine = InferenceEngine(context)
        try {
            runBlocking {
                engine.load(llmFile.absolutePath, Backend.CPU)
                assertTrue("Engine LLM non pronto", engine.isReady.value)

                // Embedding con il motore LLM residente: la convivenza è il punto.
                val vettore = embedQuery(embedder, "Una frase di prova per la convivenza.")
                assertEquals(DIMENSIONE_ATTESA, vettore.size)
                assertTrue(norma(vettore) > 1e-6)

                // E una generazione dopo l'embedding, per chiudere il giro.
                val output = StringBuilder()
                withTimeout(GENERATION_TIMEOUT_MS) {
                    engine
                        .generate(
                            ConversationRequest(
                                systemInstruction = "Rispondi in italiano, in una sola frase.",
                                initialMessages = emptyList(),
                                userMessage = "Dimmi ciao.",
                            ),
                        ).collect { chunk -> output.append(chunk) }
                }
                assertTrue("Generazione vuota con embedder attivo", output.isNotBlank())
            }
        } finally {
            engine.unload()
        }
    }

    private fun creaEmbedder(): GemmaEmbeddingModel {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = File(context.filesDir, "models/$EMBEDDER_FILE_NAME")
        val tokenizerFile = File(context.filesDir, "models/$TOKENIZER_FILE_NAME")
        assumeTrue("Embedder non importato: ${modelFile.path}", modelFile.exists())
        assumeTrue("Tokenizer non importato: ${tokenizerFile.path}", tokenizerFile.exists())
        // CPU: dai benchmark ufficiali è il backend giusto per l'embedder (design M2, D10).
        return GemmaEmbeddingModel(modelFile.absolutePath, tokenizerFile.absolutePath, false)
    }

    private fun embedQuery(
        embedder: GemmaEmbeddingModel,
        testo: String,
    ): List<Float> = embed(embedder, testo, EmbedData.TaskType.RETRIEVAL_QUERY, isQuery = true)

    private fun embedDocument(
        embedder: GemmaEmbeddingModel,
        testo: String,
    ): List<Float> = embed(embedder, testo, EmbedData.TaskType.RETRIEVAL_DOCUMENT, isQuery = false)

    private fun embed(
        embedder: GemmaEmbeddingModel,
        testo: String,
        task: EmbedData.TaskType,
        isQuery: Boolean,
    ): List<Float> {
        val richiesta = EmbeddingRequest.create(listOf(EmbedData.create(testo, task, isQuery)))
        return embedder.getEmbeddings(richiesta).get(EMBEDDING_TIMEOUT_S, TimeUnit.SECONDS)
    }

    private fun norma(v: List<Float>): Double = sqrt(v.sumOf { (it * it).toDouble() })

    private fun cosine(
        a: List<Float>,
        b: List<Float>,
    ): Double {
        var dot = 0.0
        for (i in a.indices) dot += a[i].toDouble() * b[i].toDouble()
        return dot / (norma(a) * norma(b))
    }

    companion object {
        private const val TAG = "EmbedderSmoke"
        private const val EMBEDDER_FILE_NAME = "embeddinggemma-300M_seq512_mixed-precision.tflite"
        private const val TOKENIZER_FILE_NAME = "sentencepiece.model"
        private const val LLM_FILE_NAME = "gemma-4-E2B-it.litertlm"
        private const val DIMENSIONE_ATTESA = 768
        private const val MARGINE_MINIMO = 0.05
        private const val EMBEDDING_TIMEOUT_S = 180L
        private const val GENERATION_TIMEOUT_MS = 240_000L
    }
}
