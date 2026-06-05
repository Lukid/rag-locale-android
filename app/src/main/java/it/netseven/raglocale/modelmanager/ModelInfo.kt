package it.netseven.raglocale.modelmanager

/** Stato di un modello nel ciclo di vita del Model manager. */
enum class ModelStatus { NOT_DOWNLOADED, DOWNLOADING, READY }

/** Metadati di un modello LLM del catalogo. */
data class ModelInfo(
    val id: String,
    val displayName: String,
    val repo: String,
    val fileName: String,
    val sizeBytes: Long,
    val quantization: String,
    val isDefault: Boolean = false,
)

/**
 * Macchina a stati (pura) del modello: dato lo stato del file su disco e l'eventuale
 * download in corso, decide lo [ModelStatus]. Coperta da unit test (task 5.6).
 */
object ModelStatusResolver {
    const val MIN_READY_FILE_BYTES: Long = 100_000_000L
    private const val EXPECTED_SIZE_TOLERANCE = 0.70

    fun resolve(
        fileExists: Boolean,
        fileSizeBytes: Long,
        downloadInProgress: Boolean,
        expectedSizeBytes: Long? = null,
    ): ModelStatus =
        when {
            downloadInProgress -> ModelStatus.DOWNLOADING
            fileExists && isPlausibleReadySize(fileSizeBytes, expectedSizeBytes) -> ModelStatus.READY
            else -> ModelStatus.NOT_DOWNLOADED
        }

    private fun isPlausibleReadySize(
        fileSizeBytes: Long,
        expectedSizeBytes: Long?,
    ): Boolean {
        val minBytes =
            expectedSizeBytes
                ?.takeIf { it > 0L }
                ?.let { (it * EXPECTED_SIZE_TOLERANCE).toLong() }
                ?: MIN_READY_FILE_BYTES
        return fileSizeBytes >= minBytes
    }
}
