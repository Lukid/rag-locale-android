package it.netseven.raglocale.retrieval

import com.google.ai.edge.localagents.rag.models.EmbedData
import com.google.ai.edge.localagents.rag.models.EmbeddingRequest
import com.google.ai.edge.localagents.rag.models.GemmaEmbeddingModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Adapter reale dell'[Embedder] su **EmbeddingGemma** via `GemmaEmbeddingModel` del modulo
 * embedder di `localagents-rag` (design M2 D2, spike `EmbedderSmokeTest`). Backend **CPU**
 * (D10: i benchmark ufficiali danno la CPU come scelta giusta per questo modello).
 *
 * I prefissi prompt di EmbeddingGemma (RETRIEVAL_DOCUMENT vs RETRIEVAL_QUERY) sono gestiti
 * internamente dall'SDK tramite [EmbedData.TaskType]: documento e query usano task distinti.
 *
 * Codice solo-device (JNI nativo): non unit-testabile in JVM. L'orchestrazione che lo usa è
 * testata con un embedder finto dietro [Embedder]/[FornitoreEmbedder]; questo adapter è
 * coperto dallo smoke strumentato sul Poco.
 */
class GemmaEmbedder(
    override val id: String,
    private val modelPath: String,
    private val tokenizerPath: String,
    private val useGpu: Boolean = false,
    private val timeoutSeconds: Long = TIMEOUT_DEFAULT_S,
) : Embedder,
    AutoCloseable {
    // Init nativo costoso: rinviato al primo embedding e poi residente (design M2 D7).
    private val modelLazy = lazy { GemmaEmbeddingModel(modelPath, tokenizerPath, useGpu) }

    override suspend fun embedDocumento(testo: String): FloatArray =
        embed(testo, EmbedData.TaskType.RETRIEVAL_DOCUMENT, isQuery = false)

    override suspend fun embedQuery(testo: String): FloatArray =
        embed(testo, EmbedData.TaskType.RETRIEVAL_QUERY, isQuery = true)

    private suspend fun embed(
        testo: String,
        task: EmbedData.TaskType,
        isQuery: Boolean,
    ): FloatArray =
        withContext(Dispatchers.Default) {
            val richiesta = EmbeddingRequest.create(listOf(EmbedData.create(testo, task, isQuery)))
            val vettore = modelLazy.value.getEmbeddings(richiesta).get(timeoutSeconds, TimeUnit.SECONDS)
            FloatArray(vettore.size) { vettore[it] }
        }

    override fun close() {
        if (modelLazy.isInitialized()) {
            runCatching { (modelLazy.value as? AutoCloseable)?.close() }
        }
    }

    companion object {
        /** Margine ampio: ~2,2 s/documento misurati sul Poco (seq512, CPU), ma init incluso. */
        const val TIMEOUT_DEFAULT_S = 180L
    }
}
