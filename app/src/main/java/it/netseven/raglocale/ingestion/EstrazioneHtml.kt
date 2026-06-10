package it.netseven.raglocale.ingestion

import net.dankito.readability4j.Readability4J
import org.jsoup.Jsoup

/**
 * Estrazione del contenuto testuale da una pagina HTML (parte pura di [UrlSource], task 4.4).
 * Tenta prima con Readability4J di isolare l'articolo scartando il boilerplate (menu, footer);
 * se il contenuto isolato è troppo scarno (sotto [SOGLIA_CONTENUTO_MINIMO]) ripiega sul testo
 * grezzo della pagina (Jsoup) e lo segnala con un [EsitoEstrazione.Ok.avviso].
 *
 * Separata dal fetch di rete così l'estrazione è unit-testabile in JVM con fixture HTML.
 */
class EstrazioneHtml(
    private val normalizer: TextNormalizer = TextNormalizer(),
) {
    fun estrai(
        html: String,
        urlBase: String,
    ): EsitoEstrazione {
        val articolo = runCatching { Readability4J(urlBase, html).parse() }.getOrNull()
        val testoArticolo = normalizer.normalizza(articolo?.textContent ?: "")
        val titolo = articolo?.title?.takeIf { it.isNotBlank() } ?: titoloDa(html)

        if (testoArticolo.length >= SOGLIA_CONTENUTO_MINIMO) {
            return EsitoEstrazione.Ok(NormalizedText(testoArticolo, titolo, urlBase))
        }

        // Readability non ha isolato un articolo sufficiente → testo grezzo dell'intera pagina.
        val grezzo =
            normalizer.normalizza(
                runCatching { Jsoup.parse(html, urlBase).body()?.text() }.getOrNull() ?: "",
            )
        if (grezzo.isBlank()) return EsitoEstrazione.Errore(ErroreIngestion.DocumentoVuoto)

        return EsitoEstrazione.Ok(NormalizedText(grezzo, titolo, urlBase), avviso = AVVISO_FALLBACK)
    }

    private fun titoloDa(html: String): String? = runCatching { Jsoup.parse(html).title() }.getOrNull()?.takeIf { it.isNotBlank() }

    companion object {
        /** Sotto questa lunghezza il contenuto isolato è considerato insufficiente → fallback. */
        private const val SOGLIA_CONTENUTO_MINIMO = 200
        private const val AVVISO_FALLBACK =
            "Non è stato possibile isolare l'articolo: indicizzato il testo grezzo della pagina."
    }
}
