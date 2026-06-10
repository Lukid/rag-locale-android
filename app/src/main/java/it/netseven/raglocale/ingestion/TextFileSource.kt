package it.netseven.raglocale.ingestion

import java.io.IOException
import java.io.InputStream

/**
 * Sorgente di ingestion da file di testo `.txt`/`.md` (task 4.1). Legge il contenuto da un
 * [InputStream] (il cablaggio SAF/Uri vive nel ViewModel) e produce un [NormalizedText]
 * tramite il [TextNormalizer] condiviso. Un file vuoto o di soli spazi è
 * [ErroreIngestion.DocumentoVuoto]; un errore di I/O è [ErroreIngestion.LetturaFallita].
 *
 * Componente puro rispetto ad Android (solo I/O su stream), unit-testabile in JVM.
 */
class TextFileSource(
    private val normalizer: TextNormalizer = TextNormalizer(),
) {
    fun estrai(
        input: InputStream,
        nomeFile: String? = null,
    ): EsitoEstrazione {
        val grezzo =
            try {
                input.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } catch (e: IOException) {
                return EsitoEstrazione.Errore(ErroreIngestion.LetturaFallita(e.message ?: "errore di I/O"))
            }

        val normalizzato = normalizer.normalizza(grezzo)
        if (normalizzato.isBlank()) return EsitoEstrazione.Errore(ErroreIngestion.DocumentoVuoto)

        return EsitoEstrazione.Ok(
            NormalizedText(testo = normalizzato, titolo = nomeFile, origine = nomeFile),
        )
    }
}
