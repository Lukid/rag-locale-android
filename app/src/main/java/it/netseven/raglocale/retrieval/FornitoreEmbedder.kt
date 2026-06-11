package it.netseven.raglocale.retrieval

/**
 * Fornisce l'[Embedder] attivo per la pipeline RAG. Seam che disaccoppia chi usa l'embedder
 * (ricerca, ingestion) dal modo in cui viene costruito e tenuto in vita il runtime nativo
 * ([GemmaEmbedder] su `GemmaEmbeddingModel`): così l'orchestrazione resta unit-testabile in
 * JVM con un fornitore finto.
 *
 * Restituisce `null` quando nessun embedder è pronto (file non importati / non attivi):
 * il chiamante traduce in un messaggio chiaro invece di fallire in modo opaco.
 */
interface FornitoreEmbedder {
    suspend fun embedder(): Embedder?
}
