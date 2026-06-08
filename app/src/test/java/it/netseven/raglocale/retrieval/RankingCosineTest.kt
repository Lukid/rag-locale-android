package it.netseven.raglocale.retrieval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RankingCosineTest {
    @Test
    fun `vettori identici hanno similarita uno`() {
        val v = floatArrayOf(0.3f, -0.5f, 0.8f)
        assertEquals(1.0, RankingCosine.similarita(v, v), TOLLERANZA)
    }

    @Test
    fun `vettori ortogonali hanno similarita zero`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        assertEquals(0.0, RankingCosine.similarita(a, b), TOLLERANZA)
    }

    @Test
    fun `vettori opposti hanno similarita meno uno`() {
        val a = floatArrayOf(2f, -1f, 0.5f)
        val b = floatArrayOf(-2f, 1f, -0.5f)
        assertEquals(-1.0, RankingCosine.similarita(a, b), TOLLERANZA)
    }

    @Test
    fun `la similarita non dipende dalla scala dei vettori`() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(10f, 20f, 30f)
        assertEquals(1.0, RankingCosine.similarita(a, b), TOLLERANZA)
    }

    @Test
    fun `vettore nullo produce similarita zero senza errori`() {
        val zero = floatArrayOf(0f, 0f, 0f)
        val v = floatArrayOf(1f, 2f, 3f)
        assertEquals(0.0, RankingCosine.similarita(zero, v), TOLLERANZA)
    }

    @Test
    fun `topK restituisce gli indici ordinati per score decrescente`() {
        val query = floatArrayOf(1f, 0f)
        // Indice 0 ortogonale (score 0), 1 identico (score 1), 2 a 45 gradi (score ~0,707).
        val vettori =
            listOf(
                floatArrayOf(0f, 1f),
                floatArrayOf(1f, 0f),
                floatArrayOf(1f, 1f),
            )
        val risultato = RankingCosine.topK(query, vettori, k = 3)
        assertEquals(listOf(1, 2, 0), risultato.map { it.indice })
        assertEquals(1.0, risultato[0].score, TOLLERANZA)
        assertTrue(risultato[1].score > risultato[2].score)
    }

    @Test
    fun `topK con k minore dei candidati ne restituisce solo k`() {
        val query = floatArrayOf(1f, 0f)
        val vettori =
            listOf(
                floatArrayOf(0f, 1f),
                floatArrayOf(1f, 0f),
                floatArrayOf(1f, 1f),
            )
        val risultato = RankingCosine.topK(query, vettori, k = 2)
        assertEquals(listOf(1, 2), risultato.map { it.indice })
    }

    @Test
    fun `topK con k maggiore dei candidati li restituisce tutti`() {
        val query = floatArrayOf(1f, 0f)
        val vettori = listOf(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f))
        assertEquals(2, RankingCosine.topK(query, vettori, k = 10).size)
    }

    @Test
    fun `topK su lista vuota restituisce lista vuota`() {
        assertTrue(RankingCosine.topK(floatArrayOf(1f), emptyList(), k = 5).isEmpty())
    }

    companion object {
        private const val TOLLERANZA = 1e-6
    }
}
