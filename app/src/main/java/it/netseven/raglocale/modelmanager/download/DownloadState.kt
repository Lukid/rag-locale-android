package it.netseven.raglocale.modelmanager.download

import java.io.File

/**
 * Stato (puro) di un download di modello, condiviso tra motore, ViewModel e UI a card.
 * Porting da anti-vocale, ridotto ai casi che servono qui (niente Extracting/CopyingFiles:
 * non scarichiamo archivi tar). Vedi design download-modelli-in-app, D2.
 */
sealed class DownloadState {
    /** Nessun download in corso. */
    data object Idle : DownloadState()

    /** Verifica public-first: l'URL è pubblico o richiede il token? (HEAD pre-flight). */
    data class CheckingAccess(val url: String) : DownloadState()

    /** Apertura della connessione al server. */
    data class Connecting(val url: String) : DownloadState()

    /** Trasferimento in corso. */
    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val progressPercent: Float,
        val downloadRateBytesPerSec: Float = 0f,
        val etaSeconds: Long = -1L,
    ) : DownloadState()

    /** Retry transitorio (rete) prima di rinunciare. */
    data class Retrying(val attempt: Int, val maxRetries: Int, val reason: String) : DownloadState()

    /** Download parziale rilevato (file `.tmp` presente): ripresa disponibile. */
    data class PartiallyDownloaded(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val progressPercent: Int,
    ) : DownloadState()

    /** Download concluso con successo (file finale verificato a monte). */
    data class Complete(val file: File) : DownloadState()

    /** Download fallito. */
    data class Error(val message: String, val throwable: Throwable? = null) : DownloadState()

    /** Download annullato dall'utente. */
    data class Cancelled(val reason: String) : DownloadState()

    /** Stato terminale: non seguono altre transizioni senza una nuova azione utente. */
    val isTerminal: Boolean
        get() = this is Complete || this is Error || this is Cancelled

    /** True se c'è un trasferimento attivo o in fase di apertura/verifica. */
    val isActive: Boolean
        get() = this is CheckingAccess || this is Connecting || this is Downloading || this is Retrying
}
