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
    fun resolve(
        fileExists: Boolean,
        fileSizeBytes: Long,
        downloadInProgress: Boolean,
    ): ModelStatus =
        when {
            downloadInProgress -> ModelStatus.DOWNLOADING
            fileExists && fileSizeBytes > 0 -> ModelStatus.READY
            else -> ModelStatus.NOT_DOWNLOADED
        }
}
