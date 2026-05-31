package it.netseven.raglocale.inference

/** Stato osservabile del motore di inferenza, per la UI. */
sealed interface EngineState {
    /** Nessun modello caricato. */
    data object Idle : EngineState

    /** Inizializzazione in corso (può richiedere diversi secondi). */
    data object Loading : EngineState

    /** Pronto a rispondere sul [backend] indicato (con eventuale info di fallback). */
    data class Ready(
        val backend: Backend,
        val didFallback: Boolean,
        val warning: String?,
    ) : EngineState

    /** Caricamento fallito. */
    data class Error(val message: String) : EngineState
}
