package it.netseven.raglocale.ingestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Estrazione del contenuto da HTML (parte pura di `UrlSource`, task 4.4/4.5). Readability4J
 * isola l'articolo scartando il boilerplate; se non ci riesce si ripiega sul testo grezzo
 * della pagina (Jsoup) segnalandolo. Interamente unit-testabile in JVM con fixture HTML.
 */
class EstrazioneHtmlTest {
    private val estrazione = EstrazioneHtml()

    private val articolo =
        """
        <html><head><title>La fotosintesi spiegata</title></head>
        <body>
          <header><nav>Home Chi siamo Contatti Accedi</nav></header>
          <article>
            <h1>La fotosintesi spiegata</h1>
            <p>La fotosintesi clorofilliana è il processo con cui le piante trasformano l'energia
            luminosa del sole in energia chimica. Avviene nelle foglie, dentro organuli chiamati
            cloroplasti, dove si trova la clorofilla.</p>
            <p>Durante il processo le piante assorbono anidride carbonica dall'aria attraverso gli
            stomi e acqua dal terreno tramite le radici. Grazie alla luce solare queste sostanze
            vengono combinate per produrre glucosio e ossigeno.</p>
            <p>Il glucosio prodotto nutre la pianta, mentre l'ossigeno viene rilasciato
            nell'atmosfera. Questo scambio è fondamentale per la vita sulla Terra, perché rinnova
            l'ossigeno che respiriamo ogni giorno.</p>
          </article>
          <footer>Iscriviti alla newsletter per ricevere aggiornamenti settimanali.</footer>
        </body></html>
        """.trimIndent()

    @Test
    fun `isola il contenuto principale di un articolo e scarta il boilerplate`() {
        val esito = estrazione.estrai(articolo, urlBase = "https://esempio.it/fotosintesi")

        assertTrue(esito is EsitoEstrazione.Ok)
        val ok = esito as EsitoEstrazione.Ok
        assertNull("Estrazione riuscita: nessun avviso di fallback atteso", ok.avviso)
        assertTrue("Manca il corpo dell'articolo", ok.documento.testo.contains("fotosintesi clorofilliana"))
        assertTrue("Il boilerplate del footer non è stato scartato", !ok.documento.testo.contains("newsletter"))
        assertTrue("Titolo non estratto", ok.documento.titolo?.contains("fotosintesi", ignoreCase = true) == true)
    }

    @Test
    fun `ripiega sul testo grezzo quando non riesce a isolare l'articolo e lo segnala`() {
        val paginaMinima =
            "<html><head><title>Pagina minima</title></head>" +
                "<body><div>Solo un breve avviso di servizio per gli utenti.</div></body></html>"

        val esito = estrazione.estrai(paginaMinima, urlBase = "https://esempio.it/avviso")

        assertTrue(esito is EsitoEstrazione.Ok)
        val ok = esito as EsitoEstrazione.Ok
        assertNotNull("Il fallback al testo grezzo deve essere segnalato", ok.avviso)
        assertTrue(ok.documento.testo.contains("breve avviso di servizio"))
    }

    @Test
    fun `una pagina senza testo è un documento vuoto`() {
        val esito = estrazione.estrai("<html><body></body></html>", urlBase = "https://esempio.it/vuota")

        assertTrue(esito is EsitoEstrazione.Errore)
        assertEquals(ErroreIngestion.DocumentoVuoto, (esito as EsitoEstrazione.Errore).errore)
    }
}
