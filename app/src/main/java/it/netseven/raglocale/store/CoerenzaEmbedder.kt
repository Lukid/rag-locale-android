package it.netseven.raglocale.store

/**
 * Esito del controllo di coerenza tra l'embedder con cui è stato costruito l'indice e
 * quello attivo al momento della query (design M2 D3, spec semantic-retrieval).
 */
sealed interface EsitoCoerenza {
    /** Nessun indice ancora costruito: qualunque embedder va bene. */
    data object IndiceVuoto : EsitoCoerenza

    /** Indice e embedder attivo coincidono: la ricerca è lecita. */
    data object Coerente : EsitoCoerenza

    /** Embedder diversi: la ricerca darebbe risultati incoerenti, serve ri-indicizzare. */
    data class Incoerente(
        val embedderIndice: String,
        val embedderAttivo: String,
    ) : EsitoCoerenza
}

/**
 * Confronta l'embedder dell'indice con quello attivo. Logica pura, separata dallo store
 * concreto: i consumatori (flusso di query RAG) decidono se cercare o proporre la
 * ri-indicizzazione in base all'esito.
 */
object CoerenzaEmbedder {
    fun verifica(
        embedderIndice: String?,
        embedderAttivo: String,
    ): EsitoCoerenza =
        when {
            embedderIndice == null -> EsitoCoerenza.IndiceVuoto
            embedderIndice == embedderAttivo -> EsitoCoerenza.Coerente
            else -> EsitoCoerenza.Incoerente(embedderIndice, embedderAttivo)
        }
}
