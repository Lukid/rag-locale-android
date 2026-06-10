package it.netseven.raglocale.store

import it.netseven.raglocale.retrieval.ChunkRecuperato

/**
 * Chunk pronto per l'indicizzazione: testo più l'embedding già calcolato dall'embedder.
 * `indiceChunk` è la posizione del chunk nel documento d'origine (per citazioni e debug).
 */
data class ChunkDaIndicizzare(
    val indiceChunk: Int,
    val testo: String,
    val embedding: FloatArray,
) {
    // FloatArray in una data class non ha uguaglianza strutturale di default: la
    // sovrascriviamo perché i test e la deduplica si aspettano confronti per valore.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChunkDaIndicizzare) return false
        return indiceChunk == other.indiceChunk &&
            testo == other.testo &&
            embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var risultato = indiceChunk
        risultato = 31 * risultato + testo.hashCode()
        risultato = 31 * risultato + embedding.contentHashCode()
        return risultato
    }
}

/**
 * Vector store persistente del RAG (design M2 D3): persistenza su SQLite, ricerca con
 * scan completo in memoria + cosine top-K. A scala demo (un documento lungo → centinaia
 * di chunk) lo scan è corretto, semplice e didattico: niente ANN né estensioni vettoriali.
 *
 * L'interfaccia è il confine stabile sotto a [it.netseven.raglocale.retrieval.RicercaDocumenti]:
 * l'implementazione SQLite può essere sostituita senza toccare i consumatori.
 */
interface VectorStore {
    /**
     * Indicizza i chunk di un documento e registra quale embedder li ha prodotti
     * (metadato necessario al controllo di coerenza indice/query, vedi [CoerenzaEmbedder]).
     */
    fun indicizza(
        documento: String,
        chunks: List<ChunkDaIndicizzare>,
        embedderId: String,
    )

    /** Scan completo dell'indice + cosine top-K, ordinati per score decrescente (D3). */
    fun cerca(
        embeddingQuery: FloatArray,
        topK: Int,
    ): List<ChunkRecuperato>

    /** Embedder con cui è stato costruito l'indice corrente, o `null` se l'indice è vuoto. */
    fun embedderIndice(): String?

    /** Svuota l'indice (preludio alla ri-indicizzazione). */
    fun svuota()
}
