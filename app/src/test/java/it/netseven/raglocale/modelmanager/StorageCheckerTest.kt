package it.netseven.raglocale.modelmanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageCheckerTest {
    @Test
    fun `spazio sufficiente quando libero copre modello piu' margine`() {
        val r = StorageChecker.check(freeBytes = 2_000, modelSizeBytes = 1_000, marginBytes = 500)
        assertTrue(r.sufficient)
        assertEquals(1_500, r.requiredBytes)
        assertEquals(0, r.missingBytes)
    }

    @Test
    fun `spazio insufficiente calcola i byte mancanti`() {
        val r = StorageChecker.check(freeBytes = 1_000, modelSizeBytes = 1_000, marginBytes = 500)
        assertFalse(r.sufficient)
        assertEquals(1_500, r.requiredBytes)
        assertEquals(500, r.missingBytes)
    }

    @Test
    fun `confine esatto e' considerato sufficiente`() {
        val r = StorageChecker.check(freeBytes = 1_500, modelSizeBytes = 1_000, marginBytes = 500)
        assertTrue(r.sufficient)
        assertEquals(0, r.missingBytes)
    }
}
