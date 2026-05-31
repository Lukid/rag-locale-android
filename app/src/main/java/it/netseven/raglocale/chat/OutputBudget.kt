package it.netseven.raglocale.chat

/**
 * Budget di output e decisione di stop al raggiungimento del cap (design D6).
 *
 * Stima grezza dei token (~1 token ogni 4 caratteri): sufficiente a tenere le risposte
 * entro tempi "guardabili" dato il decode ~10 tok/s del device. Logica **pura** → unit test.
 */
object OutputBudget {
    const val CHARS_PER_TOKEN = 4

    /** Stima (per eccesso) del numero di token in [text]. */
    fun estimateTokens(text: String): Int = if (text.isEmpty()) 0 else (text.length + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN

    /** true se l'output ha raggiunto/superato il cap e la generazione va interrotta. */
    fun reachedCap(
        text: String,
        maxTokens: Int,
    ): Boolean = maxTokens > 0 && estimateTokens(text) >= maxTokens
}
