package it.netseven.raglocale.modelmanager.download

/**
 * Stima la velocità di download su una finestra scorrevole di campioni recenti, e da essa
 * deriva l'ETA. Il clock è iniettabile ([now]) per test deterministici in JVM (porting da
 * anti-vocale, reso testabile senza dipendere dal tempo reale).
 */
class DownloadRateTracker(
    private val windowSize: Int = 3,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private data class Sample(val bytesDownloaded: Long, val timestampMs: Long)

    private val samples = ArrayDeque<Sample>(windowSize)

    fun reset() {
        samples.clear()
    }

    /** Registra il numero cumulativo di byte scaricati al tempo corrente. */
    fun record(bytesDownloaded: Long) {
        if (samples.size >= windowSize) {
            samples.removeFirst()
        }
        samples.addLast(Sample(bytesDownloaded, now()))
    }

    /** Velocità media in byte/secondo sulla finestra, o 0 se i campioni non bastano. */
    fun getRateBytesPerSec(): Float {
        if (samples.size < 2) return 0f
        val first = samples.first()
        val last = samples.last()
        val elapsedSec = (last.timestampMs - first.timestampMs) / 1000.0
        if (elapsedSec <= 0) return 0f
        val bytesDelta = last.bytesDownloaded - first.bytesDownloaded
        return (bytesDelta / elapsedSec).toFloat()
    }

    /** Secondi stimati alla fine, o -1 se non calcolabile. Passare il rate già calcolato. */
    fun getEtaSeconds(
        bytesDownloaded: Long,
        totalBytes: Long,
        rateBytesPerSec: Float,
    ): Long {
        if (rateBytesPerSec <= 0f || totalBytes <= 0) return -1L
        val remaining = totalBytes - bytesDownloaded
        if (remaining <= 0) return 0L
        return (remaining / rateBytesPerSec).toLong()
    }
}
