package it.netseven.raglocale.ingestion

/**
 * Testo normalizzato prodotto da una sorgente di ingestion. Confine chiave del design:
 * da qui in poi la pipeline (chunking, embedding, indicizzazione) è identica e ignara
 * dell'origine del documento (file di testo, PDF o URL).
 */
data class NormalizedText(
    val testo: String,
    val titolo: String? = null,
    val origine: String? = null,
)
