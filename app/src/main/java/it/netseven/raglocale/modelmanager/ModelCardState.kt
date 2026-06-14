package it.netseven.raglocale.modelmanager

/** Stato della card di un modello nella UI del Model manager. */
enum class CardStato { ASSENTE, IN_DOWNLOAD, PARZIALE, PRONTO, ATTIVO }

/**
 * Derivazione **pura** dello stato-card dal modello, per pilotare icona e azioni della UI senza
 * logica nella view. Precedenze: download attivo ha priorità; poi pronto/attivo; poi parziale
 * (`.tmp` presente con ripresa); altrimenti assente.
 */
object ModelCardState {
    fun stato(
        status: ModelStatus,
        downloadActive: Boolean,
        hasPartial: Boolean,
        isActive: Boolean,
    ): CardStato =
        when {
            downloadActive -> CardStato.IN_DOWNLOAD
            status == ModelStatus.READY && isActive -> CardStato.ATTIVO
            status == ModelStatus.READY -> CardStato.PRONTO
            hasPartial -> CardStato.PARZIALE
            else -> CardStato.ASSENTE
        }
}
