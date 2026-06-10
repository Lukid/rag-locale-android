package it.netseven.raglocale.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test del controllo di coerenza tra l'embedder dell'indice e quello attivo (design M2 D3,
 * spec semantic-retrieval). Logica pura: decide se la ricerca è lecita o se serve
 * ri-indicizzare, senza dipendere dallo store concreto.
 */
class CoerenzaEmbedderTest {
    @Test
    fun `indice vuoto quando non c'è ancora un embedder registrato`() {
        val esito = CoerenzaEmbedder.verifica(embedderIndice = null, embedderAttivo = "embeddinggemma-300m")
        assertTrue(esito is EsitoCoerenza.IndiceVuoto)
    }

    @Test
    fun `coerente quando indice e attivo coincidono`() {
        val esito = CoerenzaEmbedder.verifica("embeddinggemma-300m", "embeddinggemma-300m")
        assertTrue(esito is EsitoCoerenza.Coerente)
    }

    @Test
    fun `incoerente quando differiscono, riportando entrambi gli embedder`() {
        val esito = CoerenzaEmbedder.verifica("gecko-768", "embeddinggemma-300m")
        assertTrue(esito is EsitoCoerenza.Incoerente)
        esito as EsitoCoerenza.Incoerente
        assertEquals("gecko-768", esito.embedderIndice)
        assertEquals("embeddinggemma-300m", esito.embedderAttivo)
    }
}
