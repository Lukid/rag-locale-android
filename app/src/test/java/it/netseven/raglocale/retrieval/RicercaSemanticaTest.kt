package it.netseven.raglocale.retrieval

import it.netseven.raglocale.store.ChunkDaIndicizzare
import it.netseven.raglocale.store.VectorStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ricerca semantica (task 6.1): cablaggio tra embedder attivo, controllo di coerenza
 * indice/query e cosine top-K dello store. Componenti finti (embedder e store) per restare
 * un unit test JVM puro: la correttezza del cosine è coperta in `RankingCosineTest`, qui si
 * verifica il flusso e la gestione dei casi di indisponibilità.
 */
class RicercaSemanticaTest {
    /** Embedder finto: id configurabile, vettore della query registrato per le asserzioni. */
    private class EmbedderFinto(
        override val id: String = "embedder-x",
    ) : Embedder {
        var ultimaQuery: String? = null

        override suspend fun embedDocumento(testo: String): FloatArray = floatArrayOf(1f, 0f)

        override suspend fun embedQuery(testo: String): FloatArray {
            ultimaQuery = testo
            return floatArrayOf(0f, 1f)
        }
    }

    /** Store finto: embedder dell'indice e risultati di `cerca` configurabili. */
    private class StoreFinto(
        private val embedderIndice: String?,
        private val risultati: List<ChunkRecuperato> = emptyList(),
    ) : VectorStore {
        var topKRichiesto: Int? = null

        override fun indicizza(
            documento: String,
            chunks: List<ChunkDaIndicizzare>,
            embedderId: String,
        ) = Unit

        override fun cerca(
            embeddingQuery: FloatArray,
            topK: Int,
        ): List<ChunkRecuperato> {
            topKRichiesto = topK
            return risultati
        }

        override fun embedderIndice(): String? = embedderIndice

        override fun svuota() = Unit
    }

    private fun fornitore(embedder: Embedder?): FornitoreEmbedder =
        object : FornitoreEmbedder {
            override suspend fun embedder(): Embedder? = embedder
        }

    @Test
    fun `indice coerente restituisce i chunk dello store ed embedda la query`() =
        runBlocking {
            val embedder = EmbedderFinto(id = "embedder-x")
            val chunk = ChunkRecuperato(testo = "il gatto dorme", score = 0.9, documento = "doc", indiceChunk = 0)
            val store = StoreFinto(embedderIndice = "embedder-x", risultati = listOf(chunk))
            val ricerca = RicercaSemantica(fornitore(embedder), store)

            val risultati = ricerca.cerca("dove dorme il micio?", topK = 3)

            assertEquals(listOf(chunk), risultati)
            assertEquals("dove dorme il micio?", embedder.ultimaQuery)
            assertEquals(3, store.topKRichiesto)
        }

    @Test
    fun `nessun embedder attivo solleva EmbedderMancante`() =
        runBlocking {
            val ricerca = RicercaSemantica(fornitore(null), StoreFinto(embedderIndice = "embedder-x"))

            val errore =
                assertThrows(RetrievalIndisponibile.EmbedderMancante::class.java) {
                    runBlocking { ricerca.cerca("domanda", topK = 5) }
                }
            assertTrue(errore.messaggio.isNotBlank())
        }

    @Test
    fun `indice vuoto solleva IndiceVuoto`() {
        runBlocking {
            val ricerca = RicercaSemantica(fornitore(EmbedderFinto()), StoreFinto(embedderIndice = null))

            assertThrows(RetrievalIndisponibile.IndiceVuoto::class.java) {
                runBlocking { ricerca.cerca("domanda", topK = 5) }
            }
        }
    }

    @Test
    fun `embedder diverso dall'indice solleva IndiceIncoerente coi due id`() =
        runBlocking {
            val store = StoreFinto(embedderIndice = "gecko")
            val ricerca = RicercaSemantica(fornitore(EmbedderFinto(id = "embeddinggemma-300m")), store)

            val errore =
                assertThrows(RetrievalIndisponibile.IndiceIncoerente::class.java) {
                    runBlocking { ricerca.cerca("domanda", topK = 5) }
                }
            assertEquals("gecko", errore.embedderIndice)
            assertEquals("embeddinggemma-300m", errore.embedderAttivo)
        }
}
