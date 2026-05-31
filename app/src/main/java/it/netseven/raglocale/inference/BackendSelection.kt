package it.netseven.raglocale.inference

/**
 * Logica **pura** di risoluzione del backend dopo il tentativo di inizializzazione.
 *
 * Implementa il fallback GPU→CPU con avviso *sfumato* (design D3): la CPU non è un
 * mero degrado — è più lenta sul prefill ma può essere più rapida su decode e caricamento.
 * Essendo pura (nessuna dipendenza Android) è coperta da unit test JVM (task 3.5).
 */
object BackendSelection {
    /** Esito: backend effettivo + se c'è stato fallback + eventuale avviso da mostrare. */
    data class Resolution(
        val effective: Backend,
        val didFallback: Boolean,
        val warning: String?,
    )

    /** Avviso per il fallback GPU→CPU, accurato sul compromesso di prestazioni. */
    const val FALLBACK_WARNING: String =
        "GPU non disponibile su questo dispositivo: passo a CPU. " +
            "Non è solo \"più lento\": la CPU è più lenta nella fase iniziale (prefill) " +
            "ma può essere più rapida nella generazione (decode) e nel caricamento del modello."

    /**
     * @param preferred backend scelto/preferito dall'utente (default GPU)
     * @param gpuInitFailed true se l'init su GPU è fallito (rilevante solo se [preferred] == GPU)
     */
    fun resolve(
        preferred: Backend,
        gpuInitFailed: Boolean,
    ): Resolution =
        when {
            preferred == Backend.CPU -> Resolution(Backend.CPU, didFallback = false, warning = null)
            gpuInitFailed -> Resolution(Backend.CPU, didFallback = true, warning = FALLBACK_WARNING)
            else -> Resolution(Backend.GPU, didFallback = false, warning = null)
        }
}
