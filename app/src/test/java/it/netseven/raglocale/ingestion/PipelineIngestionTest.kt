package it.netseven.raglocale.ingestion

import it.netseven.raglocale.retrieval.Embedder
import it.netseven.raglocale.store.SqliteVectorStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Pipeline di ingestion end-to-end (task 4.2): `NormalizedText` → chunking → embedding →
 * indicizzazione persistente, con progresso. Esercitata su `SqliteVectorStore` reale via
 * Robolectric (niente device) e un embedder finto deterministico (frequenza delle lettere):
 * la correttezza del ranking è già coperta altrove, qui si verifica il cablaggio completo,
 * la persistenza e i limiti del documento.
 */
@RunWith(RobolectricTestRunner::class)
class PipelineIngestionTest {
    private lateinit var store: SqliteVectorStore

    /** Embedder deterministico: vettore di frequenza delle 26 lettere → testi uguali, vettori uguali. */
    private class EmbedderFinto(
        override val id: String = "fake-embedder",
    ) : Embedder {
        override suspend fun embedDocumento(testo: String): FloatArray = vettore(testo)

        override suspend fun embedQuery(testo: String): FloatArray = vettore(testo)

        private fun vettore(testo: String): FloatArray {
            val v = FloatArray(26)
            for (c in testo.lowercase()) {
                val i = c - 'a'
                if (i in 0..25) v[i] += 1f
            }
            return v
        }
    }

    private val embedder = EmbedderFinto()

    @Before
    fun setUp() {
        store = SqliteVectorStore(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() {
        store.close()
    }

    private fun pipeline(limiteMaxChunk: Int = 1000) =
        PipelineIngestion(
            chunker = Chunker(dimensione = 50, overlap = 10),
            embedder = embedder,
            store = store,
            limiteMaxChunk = limiteMaxChunk,
        )

    private val testoLungo =
        "Il gatto dorme sul divano del soggiorno. La pianta cresce verso la luce della finestra. " +
            "Il cane corre nel parco vicino al fiume. Il libro racconta una storia di mare."

    @Test
    fun `indicizza tutti i chunk e li rende ricercabili dopo la riapertura del database`() =
        runBlocking {
            val documento = NormalizedText(testo = testoLungo, titolo = "natura.txt")

            val esito = pipeline().indicizza(documento)

            assertTrue(esito is EsitoIngestion.Completata)
            val completata = esito as EsitoIngestion.Completata
            assertEquals("natura.txt", completata.documento)
            assertTrue("Attesi più chunk dal testo lungo", completata.chunkIndicizzati > 1)
            store.close()

            // Riapertura: l'indice sopravvive ed è interrogabile.
            val altroStore = SqliteVectorStore(RuntimeEnvironment.getApplication())
            try {
                assertEquals("fake-embedder", altroStore.embedderIndice())
                val risultati = altroStore.cerca(embedder.embedQuery("Il gatto dorme sul divano del soggiorno."), topK = 1)
                assertEquals(1, risultati.size)
                assertTrue("Il chunk recuperato non contiene il testo della query", risultati[0].testo.contains("gatto"))
            } finally {
                altroStore.close()
            }
        }

    @Test
    fun `riporta il progresso chunk per chunk fino al totale`() =
        runBlocking {
            val progresso = mutableListOf<Pair<Int, Int>>()

            val esito =
                pipeline().indicizza(NormalizedText(testo = testoLungo, titolo = "doc.txt")) { processati, totale ->
                    progresso += processati to totale
                }

            val totale = (esito as EsitoIngestion.Completata).chunkIndicizzati
            assertEquals(totale, progresso.size)
            assertEquals(1 to totale, progresso.first())
            assertEquals(totale to totale, progresso.last())
        }

    @Test
    fun `un documento vuoto non indicizza nulla`() =
        runBlocking {
            val esito = pipeline().indicizza(NormalizedText(testo = "   \n  "))

            assertTrue(esito is EsitoIngestion.Errore)
            assertEquals(ErroreIngestion.DocumentoVuoto, (esito as EsitoIngestion.Errore).errore)
            assertTrue(store.cerca(FloatArray(26), topK = 5).isEmpty())
        }

    @Test
    fun `un documento oltre il limite di chunk viene rifiutato senza lasciare stato parziale`() =
        runBlocking {
            val esito = pipeline(limiteMaxChunk = 2).indicizza(NormalizedText(testo = testoLungo, titolo = "grande.txt"))

            assertTrue(esito is EsitoIngestion.Errore)
            val errore = (esito as EsitoIngestion.Errore).errore
            assertTrue(errore is ErroreIngestion.DocumentoTroppoGrande)
            assertEquals(2, (errore as ErroreIngestion.DocumentoTroppoGrande).limite)
            // Nessuna scrittura: il limite è controllato prima di embeddare e indicizzare.
            assertTrue(store.cerca(FloatArray(26), topK = 5).isEmpty())
            assertEquals(null, store.embedderIndice())
        }
}
