package it.netseven.raglocale.ingestion

/**
 * Normalizzazione condivisa da tutte le sorgenti di ingestion (design M2, "ogni sorgente
 * produce `NormalizedText`"): rimuove gli artefatti di formato (trattini morbidi, caratteri
 * a larghezza zero, BOM, spazi insecabili) e uniforma la spaziatura, **preservando** i
 * confini di paragrafo (doppio newline) su cui il [Chunker] basa i tagli.
 *
 * I caratteri invisibili sono espressi con escape Unicode (non come letterali nel sorgente):
 * un BOM letterale a metà file fa fallire Android Lint (ByteOrderMark) ed è illeggibile in review.
 *
 * Componente puro (nessuna dipendenza Android), unit-testato in JVM.
 */
class TextNormalizer {
    fun normalizza(grezzo: String): String {
        val senzaArtefatti =
            grezzo
                // Fine riga eterogenei (Windows \r\n, Mac classico \r) → newline singolo.
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                // Trattino morbido U+00AD (a fine riga nei PDF impaginati): ricuce la parola.
                .replace("\u00AD", "")
                // Caratteri a larghezza zero e BOM: rumore invisibile.
                .replace(ZERO_WIDTH, "")
                // Spazio insecabile U+00A0 → spazio normale (poi compresso col resto).
                .replace('\u00A0', ' ')

        return senzaArtefatti
            // Sequenze di spazi/tab orizzontali → un solo spazio (i newline restano).
            .replace(ORIZZONTALE, " ")
            // Tre o più newline → confine di paragrafo doppio.
            .replace(NEWLINE_MULTIPLI, "\n\n")
            .trim()
    }

    companion object {
        // U+200B zero-width space, U+200C/U+200D zero-width (non-)joiner, U+FEFF BOM.
        private val ZERO_WIDTH = Regex("[\u200B\u200C\u200D\uFEFF]")
        private val ORIZZONTALE = Regex("[ \t]+")
        private val NEWLINE_MULTIPLI = Regex("\n{3,}")
    }
}
