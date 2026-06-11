package it.netseven.raglocale.retrieval

import it.netseven.raglocale.store.CoerenzaEmbedder
import it.netseven.raglocale.store.EsitoCoerenza
import it.netseven.raglocale.store.VectorStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementazione "tool-shaped" della retrieval (design M2 D4): embedding della query con
 * l'embedder attivo, poi cosine top-K sul [VectorStore]. È il confine stabile che tutti i
 * consumatori (chat grounded, pannello didattico) attraversano.
 *
 * Coerenza indice/query (spec semantic-retrieval): prima di cercare confronta l'embedder
 * dell'indice con quello attivo. Invece di restituire risultati incoerenti, segnala il caso
 * con un'eccezione tipata ([RetrievalIndisponibile]) che il chiamante traduce in un messaggio
 * e — per l'incoerenza — nella proposta di ri-indicizzare.
 */
@Singleton
class RicercaSemantica
    @Inject
    constructor(
        private val fornitore: FornitoreEmbedder,
        private val store: VectorStore,
    ) : RicercaDocumenti {
        override suspend fun cerca(
            query: String,
            topK: Int,
        ): List<ChunkRecuperato> {
            val embedder = fornitore.embedder() ?: throw RetrievalIndisponibile.EmbedderMancante
            when (val esito = CoerenzaEmbedder.verifica(store.embedderIndice(), embedder.id)) {
                EsitoCoerenza.IndiceVuoto -> throw RetrievalIndisponibile.IndiceVuoto
                is EsitoCoerenza.Incoerente ->
                    throw RetrievalIndisponibile.IndiceIncoerente(esito.embedderIndice, esito.embedderAttivo)
                EsitoCoerenza.Coerente -> Unit
            }
            val embeddingQuery = embedder.embedQuery(query)
            return store.cerca(embeddingQuery, topK)
        }
    }

/**
 * Motivi per cui la ricerca non può restituire risultati validi. Ogni variante porta un
 * [messaggio] pronto per la UI; [IndiceIncoerente] è la condizione che richiede la
 * ri-indicizzazione del documento con l'embedder attivo.
 */
sealed class RetrievalIndisponibile(
    val messaggio: String,
) : Exception(messaggio) {
    /** Nessun embedder importato/attivo: impossibile calcolare l'embedding della query. */
    object EmbedderMancante : RetrievalIndisponibile(
        "Nessun embedder attivo: importane/selezionane uno nella scheda Modelli.",
    )

    /** Nessun documento ancora indicizzato. */
    object IndiceVuoto : RetrievalIndisponibile(
        "Nessun documento indicizzato: aggiungine uno nella scheda Documenti.",
    )

    /** L'indice è stato costruito con un embedder diverso da quello attivo. */
    class IndiceIncoerente(
        val embedderIndice: String,
        val embedderAttivo: String,
    ) : RetrievalIndisponibile(
            "L'indice è stato creato con l'embedder \"$embedderIndice\" ma quello attivo è " +
                "\"$embedderAttivo\". Ri-indicizza il documento dalla scheda Documenti.",
        )
}
