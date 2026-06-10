package it.netseven.raglocale.store

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Test della serializzazione embedding ↔ BLOB. Un round-trip corrotto rovinerebbe
 * l'indice in silenzio (lezione M1 sull'integrità dei dati): qui è coperto in JVM puro.
 */
class EmbeddingBlobTest {
    @Test
    fun `il round-trip preserva i valori`() {
        val originale = floatArrayOf(0.0f, 1.0f, -1.0f, 0.123456f, -987.654f)
        val ricostruito = EmbeddingBlob.daBytes(EmbeddingBlob.aBytes(originale))
        assertArrayEquals(originale, ricostruito, 0.0f)
    }

    @Test
    fun `un vettore vuoto resta vuoto`() {
        val ricostruito = EmbeddingBlob.daBytes(EmbeddingBlob.aBytes(floatArrayOf()))
        assertEquals(0, ricostruito.size)
    }

    @Test
    fun `ogni float occupa 4 byte`() {
        assertEquals(0, EmbeddingBlob.aBytes(floatArrayOf()).size)
        assertEquals(4, EmbeddingBlob.aBytes(floatArrayOf(1.0f)).size)
        assertEquals(12, EmbeddingBlob.aBytes(floatArrayOf(1.0f, 2.0f, 3.0f)).size)
    }

    @Test
    fun `valori speciali sopravvivono al round-trip`() {
        val originale = floatArrayOf(Float.MAX_VALUE, Float.MIN_VALUE, Float.NaN, Float.POSITIVE_INFINITY)
        val ricostruito = EmbeddingBlob.daBytes(EmbeddingBlob.aBytes(originale))
        assertEquals(Float.MAX_VALUE, ricostruito[0], 0.0f)
        assertEquals(Float.MIN_VALUE, ricostruito[1], 0.0f)
        assertEquals(true, ricostruito[2].isNaN())
        assertEquals(Float.POSITIVE_INFINITY, ricostruito[3], 0.0f)
    }

    @Test
    fun `un numero di byte non multiplo di 4 è rifiutato`() {
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingBlob.daBytes(byteArrayOf(1, 2, 3))
        }
    }
}
