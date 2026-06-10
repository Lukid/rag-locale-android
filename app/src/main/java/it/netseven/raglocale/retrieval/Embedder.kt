package it.netseven.raglocale.retrieval

/**
 * Embedder testuale del RAG: trasforma un testo in un vettore denso. Seam che disaccoppia
 * la pipeline (chunking, indicizzazione, ricerca) dal runtime concreto (EmbeddingGemma via
 * `GemmaEmbeddingModel`, design M2 D2) — così l'orchestrazione è unit-testabile in JVM con
 * un embedder finto e il runtime reale resta confinato all'adapter Android.
 *
 * Documento e query usano prompt/prefissi diversi (EmbeddingGemma: RETRIEVAL_DOCUMENT vs
 * RETRIEVAL_QUERY): da qui i due metodi distinti.
 */
interface Embedder {
    /** Identità dell'embedder, registrata come metadato dell'indice per il controllo di coerenza. */
    val id: String

    /** Embedding di un chunk in fase di indicizzazione. */
    suspend fun embedDocumento(testo: String): FloatArray

    /** Embedding di una query in fase di ricerca. */
    suspend fun embedQuery(testo: String): FloatArray
}
