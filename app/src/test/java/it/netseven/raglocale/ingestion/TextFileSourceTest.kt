package it.netseven.raglocale.ingestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Estrazione `NormalizedText` dalla sorgente file di testo (task 4.1). La sorgente legge
 * da un `InputStream` (il cablaggio SAF/Uri sta nel ViewModel, gruppo 6) così l'estrazione
 * è interamente unit-testabile in JVM senza device.
 */
class TextFileSourceTest {
    private val source = TextFileSource()

    private fun streamUtf8(testo: String): InputStream = ByteArrayInputStream(testo.toByteArray(Charsets.UTF_8))

    @Test
    fun `estrae il testo normalizzato e usa il nome file come titolo`() {
        val esito = source.estrai(streamUtf8("Prima frase.   Seconda frase."), nomeFile = "appunti.txt")

        assertTrue(esito is EsitoEstrazione.Ok)
        val ok = esito as EsitoEstrazione.Ok
        assertEquals("Prima frase. Seconda frase.", ok.documento.testo)
        assertEquals("appunti.txt", ok.documento.titolo)
    }

    @Test
    fun `preserva gli accenti italiani letti come UTF-8`() {
        val esito = source.estrai(streamUtf8("L'università è in città."), nomeFile = "doc.md")

        assertEquals("L'università è in città.", (esito as EsitoEstrazione.Ok).documento.testo)
    }

    @Test
    fun `un file di soli spazi è un documento vuoto`() {
        val esito = source.estrai(streamUtf8("   \n\t  \n "), nomeFile = "vuoto.txt")

        assertTrue(esito is EsitoEstrazione.Errore)
        assertEquals(ErroreIngestion.DocumentoVuoto, (esito as EsitoEstrazione.Errore).errore)
    }

    @Test
    fun `un errore di lettura diventa LetturaFallita senza propagare l'eccezione`() {
        val streamRotto =
            object : InputStream() {
                override fun read(): Int = throw IOException("disco staccato")
            }

        val esito = source.estrai(streamRotto, nomeFile = "boom.txt")

        assertTrue(esito is EsitoEstrazione.Errore)
        assertTrue((esito as EsitoEstrazione.Errore).errore is ErroreIngestion.LetturaFallita)
    }
}
