package it.netseven.raglocale.modelmanager.download

import android.util.Log
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Configurazione di un download ripartibile. */
data class DownloadConfig(
    val url: String,
    val tempFile: File,
    val targetFile: File,
    val estimatedSizeBytes: Long,
    /** Header `Authorization` quando il file richiede il token (vedi [DownloadHttp.authHeader]); null per i pubblici. */
    val authHeader: String? = null,
    val connectTimeoutMs: Int = 30_000,
    val readTimeoutMs: Int = 60_000,
    val isCancelled: () -> Boolean = { false },
)

/** Esito della verifica public-first (HEAD) sull'URL del file. */
sealed interface AccessResult {
    /** Il file è pubblico: scaricabile senza token. */
    data class Public(val contentLength: Long, val acceptsRanges: Boolean) : AccessResult

    /** Il file richiede autenticazione/licenza (401/403): serve il token HuggingFace. */
    data object NeedsAuth : AccessResult

    /** Errore non riconducibile ad auth (rete o HTTP inatteso). */
    data class Failure(val error: DownloadException) : AccessResult
}

/**
 * Motore di download con ripresa via HTTP `Range` (porting da anti-vocale). Usa
 * [HttpURLConnection] per la gestione affidabile del range e i componenti puri del gruppo 3
 * ([DownloadHttp], [DownloadException], [DownloadRateTracker], [ProgressThrottler]).
 *
 * Coroutine, **niente foreground service** (design D2): se l'app va in background il download si
 * ferma e riprende dal `.tmp` (più il sidecar `.size` per il totale) alla riapertura.
 */
object ResumeDownloadHelper {
    private const val TAG = "ResumeDownloadHelper"
    private const val BUFFER_SIZE = 8192
    private const val SIZE_SIDECAR_SUFFIX = ".size"

    /** File sidecar `.size` che memorizza il totale reale, per rilevare il completamento tra riavvii. */
    fun sizeSidecar(tempFile: File): File = File("${tempFile.path}$SIZE_SIDECAR_SUFFIX")

    /** Totale memorizzato nel sidecar `.size`, o [estimatedBytes] se assente/illeggibile. */
    fun readStoredTotalBytes(
        tempFile: File,
        estimatedBytes: Long,
    ): Long =
        try {
            sizeSidecar(tempFile).readText().trim().toLongOrNull() ?: estimatedBytes
        } catch (e: Exception) {
            estimatedBytes
        }

    /** Elimina il `.tmp` e il suo sidecar `.size` (cancellazione di un parziale). */
    fun clearPartial(tempFile: File) {
        sizeSidecar(tempFile).delete()
        tempFile.delete()
    }

    /**
     * Verifica public-first: una richiesta **HEAD** sull'URL. `200` → pubblico (con
     * `Content-Length`/`Accept-Ranges`); `401/403` → serve il token; altro → errore tipato.
     */
    fun checkAccess(
        url: String,
        authHeader: String? = null,
        connectTimeoutMs: Int = 30_000,
        readTimeoutMs: Int = 30_000,
    ): AccessResult {
        var connection: HttpURLConnection? = null
        return try {
            connection =
                (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "HEAD"
                    connectTimeout = connectTimeoutMs
                    readTimeout = readTimeoutMs
                    if (authHeader != null) setRequestProperty("Authorization", authHeader)
                }
            when (val code = connection.responseCode) {
                DownloadHttp.HTTP_OK -> {
                    val acceptsRanges = connection.getHeaderField("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
                    AccessResult.Public(connection.contentLengthLong, acceptsRanges)
                }
                DownloadHttp.HTTP_UNAUTHORIZED, DownloadHttp.HTTP_FORBIDDEN -> AccessResult.NeedsAuth
                else -> AccessResult.Failure(classifyHttpError(code) ?: DownloadException.HttpError(code, "HTTP $code"))
            }
        } catch (e: IOException) {
            AccessResult.Failure(DownloadException.NetworkError("Rete non raggiungibile: ${e.message}", e))
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Scarica con ripresa. Invia un header `Range` se esiste un `.tmp` parziale; su HTTP 200 il
     * server ha ignorato il range → riparte da zero; su 206 appende. A fine corsa rinomina
     * `.tmp`→target. È **bloccante**: chiamare in un dispatcher IO (vedi [downloadWithRetry]).
     */
    fun downloadWithResume(
        config: DownloadConfig,
        onProgress: (Long, Long, Float) -> Unit = { _, _, _ -> },
        onStateChange: (DownloadState) -> Unit = {},
    ): Result<File> {
        val throttler = ProgressThrottler()
        val rateTracker = DownloadRateTracker()
        rateTracker.reset()

        val connection: HttpURLConnection
        try {
            connection = (URL(config.url).openConnection() as HttpURLConnection)
            connection.connectTimeout = config.connectTimeoutMs
            connection.readTimeout = config.readTimeoutMs
            connection.setRequestProperty("Accept-Encoding", "identity")
            config.authHeader?.let { connection.setRequestProperty("Authorization", it) }
        } catch (e: IOException) {
            return Result.failure(DownloadException.NetworkError("Connessione fallita: ${e.message}", e))
        }

        val partialSize = config.tempFile.length()
        DownloadHttp.rangeHeader(partialSize)?.let {
            Log.i(TAG, "Ripresa da $partialSize byte: ${config.url}")
            connection.setRequestProperty("Range", it)
        }

        val responseCode: Int
        val contentRange: String?
        val contentLength: Long
        try {
            connection.connect()
            responseCode = connection.responseCode
            contentRange = connection.getHeaderField("Content-Range")
            contentLength = connection.contentLengthLong
        } catch (e: IOException) {
            connection.disconnect()
            return Result.failure(DownloadException.NetworkError("Connessione fallita: ${e.message}", e))
        }

        classifyHttpError(responseCode)?.let { error ->
            // 416: il range non è più valido → ripartenza pulita (il chiamante ritenta da zero).
            if (error is DownloadException.RangeNotSatisfiable) clearPartial(config.tempFile)
            onStateChange(DownloadState.Error(error.message ?: "Errore di download"))
            connection.disconnect()
            return Result.failure(error)
        }

        val decision =
            DownloadHttp.decideResume(responseCode, partialSize, contentRange, contentLength, config.estimatedSizeBytes)
                ?: run {
                    connection.disconnect()
                    return Result.failure(DownloadException.HttpError(responseCode, "Risposta inattesa: HTTP $responseCode"))
                }

        var downloadedBytes: Long
        val totalBytes: Long
        when (decision) {
            is ResumeDecision.StartFresh -> {
                if (partialSize > 0) config.tempFile.delete()
                downloadedBytes = 0L
                totalBytes = decision.totalBytes
            }
            is ResumeDecision.Resume -> {
                downloadedBytes = decision.offsetBytes
                totalBytes = decision.totalBytes
            }
        }

        // Persiste il totale reale per rilevare il completamento tra riavvii dell'app.
        try {
            val sidecar = sizeSidecar(config.tempFile)
            sidecar.parentFile?.mkdirs()
            sidecar.writeText(totalBytes.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Sidecar .size non scritto: ${e.message}")
        }

        config.tempFile.parentFile?.mkdirs()
        rateTracker.record(downloadedBytes)

        try {
            connection.inputStream.use { input ->
                FileOutputStream(config.tempFile, true).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (config.isCancelled()) {
                            onStateChange(DownloadState.Cancelled("Annullato dall'utente"))
                            return Result.failure(DownloadException.Cancelled("Download annullato"))
                        }
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (throttler.shouldReport()) {
                            rateTracker.record(downloadedBytes)
                            val progress = (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                            val rate = rateTracker.getRateBytesPerSec()
                            val eta = rateTracker.getEtaSeconds(downloadedBytes, totalBytes, rate)
                            onProgress(downloadedBytes, totalBytes, progress)
                            onStateChange(
                                DownloadState.Downloading(downloadedBytes, totalBytes, progress * 100, rate, eta),
                            )
                        }
                    }
                }
            }
        } catch (e: IOException) {
            return Result.failure(DownloadException.NetworkError("Trasferimento interrotto: ${e.message}", e))
        } finally {
            connection.disconnect()
        }

        // Progresso finale al 100% (il throttle può aver saltato l'ultimo update).
        onProgress(totalBytes, totalBytes, 1f)
        onStateChange(DownloadState.Downloading(totalBytes, totalBytes, 100f))

        if (config.tempFile != config.targetFile) {
            if (!config.tempFile.renameTo(config.targetFile)) {
                config.tempFile.delete()
                val msg = "Impossibile rinominare il file temporaneo in ${config.targetFile.name}"
                onStateChange(DownloadState.Error(msg))
                return Result.failure(DownloadException.NetworkError(msg))
            }
        }
        sizeSidecar(config.tempFile).delete()
        Log.i(TAG, "Download completato: ${config.targetFile.path} (${config.targetFile.length()} byte)")
        return Result.success(config.targetFile)
    }

    /**
     * Esegue [downloadWithResume] con retry e backoff sui soli errori di rete (ritentabili).
     * Auth/licenza, range non valido e annullamento non vengono ritentati. Tra un tentativo e
     * l'altro attende [backoffMs] crescente (`delay`, niente clock reale).
     */
    suspend fun downloadWithRetry(
        config: DownloadConfig,
        maxRetries: Int = 3,
        backoffMs: (attempt: Int) -> Long = { attempt -> 1000L * (1L shl (attempt - 1)) },
        onProgress: (Long, Long, Float) -> Unit = { _, _, _ -> },
        onStateChange: (DownloadState) -> Unit = {},
    ): Result<File> {
        var lastError: DownloadException? = null
        for (attempt in 1..maxRetries) {
            if (config.isCancelled()) return Result.failure(DownloadException.Cancelled("Download annullato"))
            val result = downloadWithResume(config, onProgress, onStateChange)
            result.onSuccess { return result }
            val error = result.exceptionOrNull() as? DownloadException ?: DownloadException.NetworkError("Errore sconosciuto")
            if (!error.isRetriable || attempt == maxRetries) return Result.failure(error)
            lastError = error
            onStateChange(DownloadState.Retrying(attempt, maxRetries, error.message ?: "errore di rete"))
            delay(backoffMs(attempt))
        }
        return Result.failure(lastError ?: DownloadException.NetworkError("Download fallito"))
    }
}
