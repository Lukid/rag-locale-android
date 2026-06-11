package it.netseven.raglocale.modelmanager

/**
 * Verifica (pura) dell'integrità di un file appena importato prima di marcarlo come pronto
 * (spec model-manager, task 5.3/5.4). Quando il checksum atteso è noto, lo confronta col
 * checksum calcolato sul file in staging: la **lezione M1** è che un file corrotto può avere
 * la dimensione attesa ma md5 diverso, quindi la sola dimensione non basta. Se il checksum
 * atteso non è noto (es. LLM importato senza md5 canonico), ricade sul controllo di dimensione.
 */
object ImportVerifier {
    sealed interface Esito {
        data object Ok : Esito

        data class Rifiutato(val motivo: String) : Esito
    }

    fun verifica(
        fileSizeBytes: Long,
        expectedSizeBytes: Long?,
        computedMd5: String?,
        expectedMd5: String?,
    ): Esito {
        if (expectedMd5 != null && !expectedMd5.equals(computedMd5, ignoreCase = true)) {
            return Esito.Rifiutato("Checksum non corrispondente: il file è corrotto o non è il modello atteso.")
        }
        if (!ModelStatusResolver.isPlausibleReadySize(fileSizeBytes, expectedSizeBytes)) {
            return Esito.Rifiutato("File incompleto o di dimensione inattesa.")
        }
        return Esito.Ok
    }
}
