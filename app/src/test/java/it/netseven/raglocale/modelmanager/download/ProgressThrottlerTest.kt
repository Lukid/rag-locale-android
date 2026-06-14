package it.netseven.raglocale.modelmanager.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressThrottlerTest {
    private class FakeClock(var t: Long = 0L) {
        fun now(): Long = t
    }

    @Test
    fun `il primo report passa sempre`() {
        val clock = FakeClock(t = 5_000L)
        val throttler = ProgressThrottler(intervalMs = 1000L, now = clock::now)
        assertTrue(throttler.shouldReport())
    }

    @Test
    fun `report ravvicinati vengono soppressi finche' non passa l'intervallo`() {
        val clock = FakeClock(t = 5_000L)
        val throttler = ProgressThrottler(intervalMs = 1000L, now = clock::now)
        assertTrue(throttler.shouldReport())
        clock.t = 5_500L // +0,5 s < intervallo
        assertFalse(throttler.shouldReport())
        clock.t = 6_000L // +1 s dall'ultimo report ammesso
        assertTrue(throttler.shouldReport())
    }

    @Test
    fun `reset riapre la finestra`() {
        val clock = FakeClock(t = 10_000L)
        val throttler = ProgressThrottler(intervalMs = 1000L, now = clock::now)
        assertTrue(throttler.shouldReport())
        assertFalse(throttler.shouldReport())
        throttler.reset()
        assertTrue("Dopo reset il prossimo report passa", throttler.shouldReport())
    }
}
