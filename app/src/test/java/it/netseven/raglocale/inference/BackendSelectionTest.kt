package it.netseven.raglocale.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendSelectionTest {
    @Test
    fun `GPU preferita e init ok resta su GPU senza avviso`() {
        val r = BackendSelection.resolve(Backend.GPU, gpuInitFailed = false)
        assertEquals(Backend.GPU, r.effective)
        assertFalse(r.didFallback)
        assertNull(r.warning)
    }

    @Test
    fun `GPU preferita ma init fallita ripiega su CPU con avviso`() {
        val r = BackendSelection.resolve(Backend.GPU, gpuInitFailed = true)
        assertEquals(Backend.CPU, r.effective)
        assertTrue(r.didFallback)
        assertEquals(BackendSelection.FALLBACK_WARNING, r.warning)
    }

    @Test
    fun `CPU preferita resta su CPU senza fallback`() {
        val r = BackendSelection.resolve(Backend.CPU, gpuInitFailed = false)
        assertEquals(Backend.CPU, r.effective)
        assertFalse(r.didFallback)
        assertNull(r.warning)
    }

    @Test
    fun `CPU preferita ignora il flag gpuInitFailed`() {
        val r = BackendSelection.resolve(Backend.CPU, gpuInitFailed = true)
        assertEquals(Backend.CPU, r.effective)
        assertFalse(r.didFallback)
    }

    @Test
    fun `l'avviso di fallback non descrive la CPU come mero degrado`() {
        // Deve menzionare il compromesso accurato (prefill vs decode/load).
        val w = BackendSelection.FALLBACK_WARNING.lowercase()
        assertTrue(w.contains("prefill"))
        assertTrue(w.contains("decode"))
    }
}
