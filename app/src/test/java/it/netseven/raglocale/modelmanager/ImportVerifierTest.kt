package it.netseven.raglocale.modelmanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica d'integrità dell'import (task 5.3/5.4). La lezione M1 (file corrotto con stessa
 * dimensione ma md5 diverso) impone il confronto del checksum, non della sola dimensione.
 */
class ImportVerifierTest {
    @Test
    fun `checksum corrispondente e dimensione plausibile sono accettati`() {
        val esito =
            ImportVerifier.verifica(
                fileSizeBytes = 1_000,
                expectedSizeBytes = 1_000,
                computedMd5 = "abc123",
                expectedMd5 = "abc123",
            )
        assertEquals(ImportVerifier.Esito.Ok, esito)
    }

    @Test
    fun `il confronto del checksum e' case-insensitive`() {
        val esito =
            ImportVerifier.verifica(
                fileSizeBytes = 1_000,
                expectedSizeBytes = 1_000,
                computedMd5 = "ABC123",
                expectedMd5 = "abc123",
            )
        assertEquals(ImportVerifier.Esito.Ok, esito)
    }

    @Test
    fun `file con dimensione attesa ma checksum diverso e' scartato (lezione M1)`() {
        val esito =
            ImportVerifier.verifica(
                fileSizeBytes = 1_000,
                expectedSizeBytes = 1_000,
                computedMd5 = "deadbeef",
                expectedMd5 = "abc123",
            )
        assertTrue("Atteso scarto su checksum", esito is ImportVerifier.Esito.Rifiutato)
    }

    @Test
    fun `checksum atteso ma non calcolato e' scartato`() {
        val esito =
            ImportVerifier.verifica(
                fileSizeBytes = 1_000,
                expectedSizeBytes = 1_000,
                computedMd5 = null,
                expectedMd5 = "abc123",
            )
        assertTrue(esito is ImportVerifier.Esito.Rifiutato)
    }

    @Test
    fun `senza checksum atteso ricade sulla dimensione e accetta un file plausibile`() {
        val esito =
            ImportVerifier.verifica(
                fileSizeBytes = 800,
                expectedSizeBytes = 1_000,
                computedMd5 = null,
                expectedMd5 = null,
            )
        assertEquals(ImportVerifier.Esito.Ok, esito)
    }

    @Test
    fun `senza checksum atteso un file troppo piccolo e' scartato`() {
        val esito =
            ImportVerifier.verifica(
                fileSizeBytes = 10,
                expectedSizeBytes = 1_000,
                computedMd5 = null,
                expectedMd5 = null,
            )
        assertTrue(esito is ImportVerifier.Esito.Rifiutato)
    }

    @Test
    fun `checksum giusto ma file troncato e' scartato come incompleto`() {
        val esito =
            ImportVerifier.verifica(
                fileSizeBytes = 10,
                expectedSizeBytes = 1_000,
                computedMd5 = "abc123",
                expectedMd5 = "abc123",
            )
        assertTrue(esito is ImportVerifier.Esito.Rifiutato)
    }
}
