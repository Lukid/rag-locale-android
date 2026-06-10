package it.netseven.raglocale.store

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Round-trip di persistenza e ricerca del vector store (task 3.4) su SQLite reale in JVM
 * tramite Robolectric — niente device, niente wipe (lezione M1). Gli embedding sono finti
 * e deterministici: la correttezza del ranking è già coperta in `RankingCosineTest`, qui
 * si verifica che la persistenza e il cablaggio con il cosine reggano il giro completo.
 */
@RunWith(RobolectricTestRunner::class)
class SqliteVectorStoreTest {
    private lateinit var store: SqliteVectorStore

    @Before
    fun setUp() {
        store = SqliteVectorStore(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() {
        store.close()
    }

    private fun chunk(
        indice: Int,
        testo: String,
        vararg embedding: Float,
    ) = ChunkDaIndicizzare(indice, testo, embedding)

    @Test
    fun `round-trip - un chunk indicizzato si recupera con il suo testo e riferimento`() {
        store.indicizza(
            documento = "gatti.txt",
            chunks = listOf(chunk(0, "il gatto dorme sul divano", 1.0f, 0.0f, 0.0f)),
            embedderId = "embeddinggemma-300m",
        )

        val risultati = store.cerca(floatArrayOf(1.0f, 0.0f, 0.0f), topK = 5)

        assertEquals(1, risultati.size)
        assertEquals("il gatto dorme sul divano", risultati[0].testo)
        assertEquals("gatti.txt", risultati[0].documento)
        assertEquals(0, risultati[0].indiceChunk)
        assertEquals(1.0, risultati[0].score, 1e-6)
    }

    @Test
    fun `cerca ordina per similarità decrescente e rispetta topK`() {
        store.indicizza(
            documento = "doc.txt",
            chunks =
                listOf(
                    chunk(0, "ortogonale", 0.0f, 1.0f, 0.0f),
                    chunk(1, "identico", 1.0f, 0.0f, 0.0f),
                    chunk(2, "intermedio", 0.7f, 0.7f, 0.0f),
                ),
            embedderId = "embeddinggemma-300m",
        )

        val risultati = store.cerca(floatArrayOf(1.0f, 0.0f, 0.0f), topK = 2)

        assertEquals(2, risultati.size)
        assertEquals("identico", risultati[0].testo)
        assertEquals("intermedio", risultati[1].testo)
        assertTrue(risultati[0].score > risultati[1].score)
    }

    @Test
    fun `embedderIndice è null su indice vuoto e riflette l'embedder dopo l'indicizzazione`() {
        assertNull(store.embedderIndice())

        store.indicizza("doc.txt", listOf(chunk(0, "x", 1.0f, 0.0f)), embedderId = "gecko-768")

        assertEquals("gecko-768", store.embedderIndice())
    }

    @Test
    fun `svuota azzera chunk e metadati dell'indice`() {
        store.indicizza("doc.txt", listOf(chunk(0, "x", 1.0f, 0.0f)), embedderId = "embeddinggemma-300m")

        store.svuota()

        assertTrue(store.cerca(floatArrayOf(1.0f, 0.0f), topK = 5).isEmpty())
        assertNull(store.embedderIndice())
    }

    @Test
    fun `cerca su indice vuoto restituisce lista vuota`() {
        assertTrue(store.cerca(floatArrayOf(1.0f, 0.0f), topK = 5).isEmpty())
    }

    @Test
    fun `la persistenza sopravvive alla riapertura del database`() {
        store.indicizza(
            documento = "persistente.txt",
            chunks = listOf(chunk(7, "sopravvivi alla chiusura", 0.6f, 0.8f, 0.0f)),
            embedderId = "embeddinggemma-300m",
        )
        store.close()

        val altroStore = SqliteVectorStore(RuntimeEnvironment.getApplication())
        try {
            val risultati = altroStore.cerca(floatArrayOf(0.6f, 0.8f, 0.0f), topK = 5)
            assertEquals(1, risultati.size)
            assertEquals("sopravvivi alla chiusura", risultati[0].testo)
            assertEquals(7, risultati[0].indiceChunk)
            assertEquals("embeddinggemma-300m", altroStore.embedderIndice())
        } finally {
            altroStore.close()
        }
    }
}
