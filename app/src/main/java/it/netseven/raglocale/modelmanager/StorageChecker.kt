package it.netseven.raglocale.modelmanager

/**
 * Verifica (pura) dello spazio di storage prima di scaricare/importare un modello
 * (spec model-manager, task 5.3/5.6). Aggiunge un margine di sicurezza al peso del modello.
 */
object StorageChecker {
    /** Margine di sicurezza oltre la dimensione del modello (default 300 MB). */
    const val DEFAULT_MARGIN_BYTES = 300L * 1024 * 1024

    data class Result(
        val sufficient: Boolean,
        val freeBytes: Long,
        val requiredBytes: Long,
        val missingBytes: Long,
    )

    fun check(
        freeBytes: Long,
        modelSizeBytes: Long,
        marginBytes: Long = DEFAULT_MARGIN_BYTES,
    ): Result {
        val required = modelSizeBytes + marginBytes
        val sufficient = freeBytes >= required
        val missing = if (sufficient) 0L else required - freeBytes
        return Result(sufficient, freeBytes, required, missing)
    }
}
