package it.netseven.raglocale.ingestion

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.IOException
import java.io.InputStream

/**
 * Sorgente di ingestion da PDF (task 4.3, design M2 D9): estrae il layer testuale con
 * PdfBox-Android e produce un [NormalizedText]. Un PDF senza testo estraibile (scansione)
 * è rilevato e segnalato come [ErroreIngestion.PdfSenzaTesto] — l'OCR è fuori scope.
 *
 * Nota di lifecycle: l'app deve chiamare una volta `PDFBoxResourceLoader.init(context)` allo
 * startup (cablaggio nel gruppo 6) perché PdfBox carichi le risorse font dal proprio AAR.
 */
class PdfSource(
    private val normalizer: TextNormalizer = TextNormalizer(),
) {
    fun estrai(
        input: InputStream,
        nomeFile: String? = null,
    ): EsitoEstrazione {
        val grezzo =
            try {
                PDDocument.load(input).use { documento -> PDFTextStripper().getText(documento) }
            } catch (e: IOException) {
                return EsitoEstrazione.Errore(ErroreIngestion.LetturaFallita(e.message ?: "PDF illeggibile"))
            }

        val normalizzato = normalizer.normalizza(grezzo)
        // Nessun testo estraibile = nessun layer testuale (probabile scansione): non DocumentoVuoto
        // ma il caso più informativo per l'utente, l'OCR non supportato.
        if (normalizzato.isBlank()) return EsitoEstrazione.Errore(ErroreIngestion.PdfSenzaTesto)

        return EsitoEstrazione.Ok(
            NormalizedText(testo = normalizzato, titolo = nomeFile, origine = nomeFile),
        )
    }
}
