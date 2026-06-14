package it.netseven.raglocale.modelmanager.download

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadRateTrackerTest {
    /** Clock finto: avanza manualmente per test deterministici. */
    private class FakeClock(var t: Long = 0L) {
        fun now(): Long = t
    }

    @Test
    fun `senza abbastanza campioni la velocita' e' zero`() {
        val clock = FakeClock()
        val tracker = DownloadRateTracker(now = clock::now)
        assertEquals(0f, tracker.getRateBytesPerSec(), 0f)
        tracker.record(1000L)
        assertEquals("Un solo campione non basta", 0f, tracker.getRateBytesPerSec(), 0f)
    }

    @Test
    fun `calcola la velocita' media su due campioni`() {
        val clock = FakeClock()
        val tracker = DownloadRateTracker(now = clock::now)
        tracker.record(0L)
        clock.t = 2000L // +2 s
        tracker.record(2_000_000L) // +2 MB
        // 2 MB in 2 s = 1 MB/s
        assertEquals(1_000_000f, tracker.getRateBytesPerSec(), 1f)
    }

    @Test
    fun `la finestra scorre e tiene solo gli ultimi campioni`() {
        val clock = FakeClock()
        val tracker = DownloadRateTracker(windowSize = 2, now = clock::now)
        tracker.record(0L)
        clock.t = 1000L
        tracker.record(500L) // campione vecchio, verrà scartato
        clock.t = 2000L
        tracker.record(2500L)
        // Finestra = [(500 @ 1000ms), (2500 @ 2000ms)] → 2000 byte in 1 s
        assertEquals(2000f, tracker.getRateBytesPerSec(), 1f)
    }

    @Test
    fun `eta e' meno uno quando la velocita' e' nulla`() {
        val tracker = DownloadRateTracker()
        assertEquals(-1L, tracker.getEtaSeconds(0L, 1000L, 0f))
    }

    @Test
    fun `eta stima i secondi rimanenti`() {
        val tracker = DownloadRateTracker()
        // 8 MB rimanenti a 2 MB/s = 4 s
        assertEquals(4L, tracker.getEtaSeconds(2_000_000L, 10_000_000L, 2_000_000f))
    }

    @Test
    fun `eta e' zero a download completato`() {
        val tracker = DownloadRateTracker()
        assertEquals(0L, tracker.getEtaSeconds(1000L, 1000L, 500f))
    }
}
