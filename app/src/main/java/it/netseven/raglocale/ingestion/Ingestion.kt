package it.netseven.raglocale.ingestion

/**
 * Errori di ingestione comunicabili all'utente con un messaggio chiaro (design M2 D9 e
 * spec `document-ingestion`). Ogni variante porta il proprio [messaggio] pronto per la UI.
 */
sealed class ErroreIngestion(
    val messaggio: String,
) {
    /** La sorgente non ha prodotto testo utile (file vuoto, pagina senza contenuto). */
    object DocumentoVuoto : ErroreIngestion(
        "Il documento è vuoto: non c'è testo da indicizzare.",
    )

    /** PDF privo di layer testuale (probabile scansione): l'OCR è fuori scope. */
    object PdfSenzaTesto : ErroreIngestion(
        "Il PDF non contiene testo estraibile (probabile scansione): l'OCR non è supportato.",
    )

    /** Il documento produce più chunk del limite consentito: ingestion interrotta. */
    data class DocumentoTroppoGrande(
        val chunkProdotti: Int,
        val limite: Int,
    ) : ErroreIngestion(
            "Il documento è troppo grande: $chunkProdotti porzioni superano il limite di $limite. Riduci il testo.",
        )

    /** L'URL è offline, in timeout o risponde con un errore HTTP: indice invariato. */
    data class ReteNonRaggiungibile(
        val dettaglio: String,
    ) : ErroreIngestion(
            "Impossibile raggiungere la pagina: $dettaglio",
        )

    /** Lettura o parsing della sorgente fallita (I/O, formato corrotto). */
    data class LetturaFallita(
        val dettaglio: String,
    ) : ErroreIngestion(
            "Lettura del documento fallita: $dettaglio",
        )
}

/**
 * Esito dell'estrazione di una sorgente. Confine chiave del design: a valle si lavora solo
 * su [NormalizedText], ignari dell'origine. [Ok.avviso] è valorizzato quando l'estrazione
 * è riuscita ma in modo degradato (es. fallback al testo grezzo di una pagina web).
 */
sealed interface EsitoEstrazione {
    data class Ok(
        val documento: NormalizedText,
        val avviso: String? = null,
    ) : EsitoEstrazione

    data class Errore(
        val errore: ErroreIngestion,
    ) : EsitoEstrazione
}

/**
 * Esito dell'indicizzazione di un documento da parte della [PipelineIngestion].
 */
sealed interface EsitoIngestion {
    data class Completata(
        val documento: String,
        val chunkIndicizzati: Int,
    ) : EsitoIngestion

    data class Errore(
        val errore: ErroreIngestion,
    ) : EsitoIngestion
}
