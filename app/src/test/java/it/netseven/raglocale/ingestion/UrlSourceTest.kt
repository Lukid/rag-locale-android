package it.netseven.raglocale.ingestion

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Sorgente da URL (task 4.4). Il fetch di rete reale non è unit-testabile, ma la mappatura
 * degli errori (URL irraggiungibile/HTTP → errore chiaro, nessuno stato parziale) lo è
 * tramite un [FetcherHttp] iniettato. L'estrazione del contenuto è coperta in
 * `EstrazioneHtmlTest`.
 */
class UrlSourceTest {
    @Test
    fun `una pagina scaricata viene passata all'estrazione del contenuto`() =
        runBlocking {
            val html =
                "<html><head><title>T</title></head><body><div>Un breve contenuto di prova.</div></body></html>"
            val source = UrlSource(fetcher = FetcherHttp { html })

            val esito = source.estrai("https://esempio.it/pagina")

            assertTrue(esito is EsitoEstrazione.Ok)
            assertTrue((esito as EsitoEstrazione.Ok).documento.testo.contains("breve contenuto di prova"))
        }

    @Test
    fun `un errore HTTP diventa ReteNonRaggiungibile senza lasciare stato parziale`() =
        runBlocking {
            val source = UrlSource(fetcher = FetcherHttp { throw IOException("HTTP 404") })

            val esito = source.estrai("https://esempio.it/inesistente")

            assertTrue(esito is EsitoEstrazione.Errore)
            val errore = (esito as EsitoEstrazione.Errore).errore
            assertTrue(errore is ErroreIngestion.ReteNonRaggiungibile)
            assertTrue((errore as ErroreIngestion.ReteNonRaggiungibile).dettaglio.contains("404"))
        }

    @Test
    fun `un URL malformato non propaga eccezioni ma diventa un errore di rete`() =
        runBlocking {
            val source = UrlSource(fetcher = FetcherHttp { throw IllegalArgumentException("schema mancante") })

            val esito = source.estrai("non-un-url")

            assertTrue(esito is EsitoEstrazione.Errore)
            assertTrue((esito as EsitoEstrazione.Errore).errore is ErroreIngestion.ReteNonRaggiungibile)
        }
}
