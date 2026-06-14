package it.netseven.raglocale.modelmanager.download

/** Range parsato dall'header `Content-Range: bytes <start>-<end>/<total>`. */
data class ContentRange(val start: Long, val end: Long, val total: Long?)

/**
 * Decisione di ripresa dopo la risposta del server a una richiesta con `Range`.
 * - [StartFresh]: HTTP 200, il server ha ignorato il `Range` → si riparte da zero (cancellare il `.tmp`).
 * - [Resume]: HTTP 206, append sul `.tmp` a partire da [Resume.offsetBytes].
 */
sealed interface ResumeDecision {
    val totalBytes: Long

    data class StartFresh(override val totalBytes: Long) : ResumeDecision

    data class Resume(val offsetBytes: Long, override val totalBytes: Long) : ResumeDecision
}

/**
 * Logica pura HTTP del download (testabile in JVM, senza rete): costruzione header,
 * parsing `Content-Range` e decisione 200-vs-206. L'IO vive in [ResumeDownloadHelper].
 */
object DownloadHttp {
    /** Header `Range` per riprendere da [partialSize] byte; null se non c'è nulla da riprendere. */
    fun rangeHeader(partialSize: Long): String? = if (partialSize > 0L) "bytes=$partialSize-" else null

    /** Header `Authorization: Bearer …` quando il token serve; null per i download anonimi. */
    fun authHeader(token: String?): String? = token?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }

    /**
     * Parsa `Content-Range`, es. `bytes 12345-67890/67891` → start=12345, end=67890, total=67891.
     * Tollera il totale sconosciuto (`*`). Ritorna null se l'header è assente o malformato.
     */
    fun parseContentRange(header: String?): ContentRange? {
        val value = header?.trim()?.removePrefix("bytes")?.trim() ?: return null
        val slash = value.split("/", limit = 2)
        val range = slash[0].split("-", limit = 2)
        if (range.size != 2) return null
        val start = range[0].trim().toLongOrNull() ?: return null
        val end = range[1].trim().toLongOrNull() ?: return null
        val total = slash.getOrNull(1)?.trim()?.takeIf { it != "*" }?.toLongOrNull()
        return ContentRange(start, end, total)
    }

    /**
     * Decide la ripresa dato il codice di risposta a una richiesta con `Range`.
     * @return [ResumeDecision] per 200/206, oppure null se il codice non è di successo
     *   (gli errori sono classificati da [classifyHttpError]).
     */
    fun decideResume(
        responseCode: Int,
        partialSize: Long,
        contentRangeHeader: String?,
        contentLength: Long,
        estimatedSizeBytes: Long,
    ): ResumeDecision? =
        when (responseCode) {
            HTTP_OK -> {
                // Il server ha ignorato il Range: si riparte da zero.
                val total = contentLength.takeIf { it > 0L } ?: estimatedSizeBytes
                ResumeDecision.StartFresh(total)
            }
            HTTP_PARTIAL -> {
                val parsed = parseContentRange(contentRangeHeader)
                val offset = parsed?.start ?: partialSize
                val total = parsed?.total ?: estimatedSizeBytes
                ResumeDecision.Resume(offset, total)
            }
            else -> null
        }

    const val HTTP_OK = 200
    const val HTTP_PARTIAL = 206
    const val HTTP_UNAUTHORIZED = 401
    const val HTTP_FORBIDDEN = 403
    const val HTTP_RANGE_NOT_SATISFIABLE = 416
}
