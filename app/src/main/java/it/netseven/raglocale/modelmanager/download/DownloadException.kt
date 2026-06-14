package it.netseven.raglocale.modelmanager.download

/**
 * Errori tipati del download con codice HTTP esplicito (porting da anti-vocale).
 * Mappa gli stati di errore a messaggi/decisioni chiare per UI e retry.
 */
sealed class DownloadException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** 401: serve il token HuggingFace (modello gated, utente non loggato o token scaduto). */
    class Unauthorized(message: String) : DownloadException(message)

    /** 403: accesso negato (licenza non accettata o permessi insufficienti). */
    class Forbidden(message: String) : DownloadException(message)

    /** 416: range non valido → il `.tmp` va eliminato e il download riparte pulito. */
    class RangeNotSatisfiable(message: String) : DownloadException(message)

    /** Altro errore HTTP non di successo. */
    class HttpError(val statusCode: Int, message: String) : DownloadException(message)

    /** Errore di rete (timeout, connessione persa): tipicamente ritentabile. */
    class NetworkError(message: String, cause: Throwable? = null) : DownloadException(message, cause)

    /** Download annullato dall'utente. */
    class Cancelled(message: String) : DownloadException(message)

    /**
     * Ritentabile con backoff? Lo sono solo gli errori transitori di rete; auth, licenza,
     * range non valido e annullamento NON si risolvono ritentando (servono azioni diverse).
     */
    val isRetriable: Boolean
        get() = this is NetworkError

    /** True se l'errore indica che serve l'autenticazione/licenza HuggingFace (401/403). */
    val isAuthRelated: Boolean
        get() = this is Unauthorized || this is Forbidden
}

/**
 * Classifica un codice di risposta HTTP in un [DownloadException], oppure null per i codici di
 * successo (200/206). Logica pura, testabile in JVM.
 */
fun classifyHttpError(responseCode: Int): DownloadException? =
    when (responseCode) {
        DownloadHttp.HTTP_OK, DownloadHttp.HTTP_PARTIAL -> null
        DownloadHttp.HTTP_UNAUTHORIZED -> DownloadException.Unauthorized("HTTP 401 Unauthorized")
        DownloadHttp.HTTP_FORBIDDEN -> DownloadException.Forbidden("HTTP 403 Forbidden")
        DownloadHttp.HTTP_RANGE_NOT_SATISFIABLE ->
            DownloadException.RangeNotSatisfiable("HTTP 416 Range Not Satisfiable")
        else -> DownloadException.HttpError(responseCode, "Download fallito: HTTP $responseCode")
    }
