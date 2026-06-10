package it.netseven.raglocale.ingestion

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Estrazione `NormalizedText` dalla sorgente PDF (task 4.3/4.5). I PDF di fixture sono
 * generati in-test con PdfBox (niente binari nel repo). Robolectric fornisce il Context che
 * serve a [PDFBoxResourceLoader] per le risorse font dell'AAR.
 */
@RunWith(RobolectricTestRunner::class)
class PdfSourceTest {
    private val source = PdfSource()

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication())
    }

    private fun pdfConTesto(vararg righe: String): ByteArray {
        val doc = PDDocument()
        val page = PDPage()
        doc.addPage(page)
        PDPageContentStream(doc, page).use { content ->
            content.beginText()
            content.setFont(PDType1Font.HELVETICA, 12f)
            content.newLineAtOffset(50f, 700f)
            righe.forEach { riga ->
                content.showText(riga)
                content.newLineAtOffset(0f, -16f)
            }
            content.endText()
        }
        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()
        return out.toByteArray()
    }

    private fun pdfPaginaVuota(): ByteArray {
        val doc = PDDocument()
        doc.addPage(PDPage())
        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()
        return out.toByteArray()
    }

    @Test
    fun `estrae il testo da un PDF con layer testuale`() {
        val pdf = pdfConTesto("La relazione annuale descrive", "i risultati del progetto.")

        val esito = source.estrai(ByteArrayInputStream(pdf), nomeFile = "relazione.pdf")

        assertTrue(esito is EsitoEstrazione.Ok)
        val ok = esito as EsitoEstrazione.Ok
        assertTrue("Manca il testo del PDF", ok.documento.testo.contains("relazione annuale"))
        assertEquals("relazione.pdf", ok.documento.titolo)
    }

    @Test
    fun `un PDF senza layer testo è segnalato come non supportato (OCR)`() {
        val esito = source.estrai(ByteArrayInputStream(pdfPaginaVuota()), nomeFile = "scansione.pdf")

        assertTrue(esito is EsitoEstrazione.Errore)
        assertEquals(ErroreIngestion.PdfSenzaTesto, (esito as EsitoEstrazione.Errore).errore)
    }

    @Test
    fun `un input non-PDF diventa LetturaFallita senza propagare l'eccezione`() {
        val esito = source.estrai(ByteArrayInputStream("non sono un pdf".toByteArray()), nomeFile = "rotto.pdf")

        assertTrue(esito is EsitoEstrazione.Errore)
        assertTrue((esito as EsitoEstrazione.Errore).errore is ErroreIngestion.LetturaFallita)
    }
}
