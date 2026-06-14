package it.netseven.raglocale.modelmanager.download

/**
 * Limita la frequenza degli aggiornamenti di progresso verso la UI: [shouldReport] è vero solo
 * se è passato almeno [intervalMs] dall'ultimo report. Clock iniettabile ([now]) per test
 * deterministici (porting da anti-vocale).
 */
class ProgressThrottler(
    private val intervalMs: Long = 1000L,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var lastReportTimeMs: Long = 0L

    fun shouldReport(): Boolean {
        val t = now()
        if (t - lastReportTimeMs >= intervalMs) {
            lastReportTimeMs = t
            return true
        }
        return false
    }

    fun reset() {
        lastReportTimeMs = 0L
    }
}
