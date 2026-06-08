package it.netseven.raglocale.ingestion

/**
 * Porzione di testo prodotta dal [Chunker]. `inizio` è l'offset (in caratteri) nel testo
 * originale: serve a verificare la copertura e, in prospettiva, a evidenziare il chunk
 * nel documento sorgente.
 */
data class ChunkDiTesto(
    val testo: String,
    val inizio: Int,
)

/**
 * Spezza un testo normalizzato in chunk di dimensione massima configurabile, con overlap
 * tra chunk consecutivi e taglio preferenziale ai confini di frase (in mancanza, di parola).
 * I casi limite (testo corto, overlap ≥ dimensione) sono gestiti senza errori: l'overlap
 * viene riportato nel range valido e il progresso tra chunk è sempre garantito.
 * Componente puro (nessuna dipendenza Android), unit-testato in JVM.
 */
class Chunker(
    private val dimensione: Int = DIMENSIONE_DEFAULT,
    private val overlap: Int = OVERLAP_DEFAULT,
) {
    init {
        require(dimensione > 0) { "La dimensione del chunk deve essere positiva" }
    }

    fun spezza(testo: String): List<ChunkDiTesto> {
        if (testo.isBlank()) return emptyList()
        val overlapEffettivo = overlap.coerceIn(0, dimensione - 1)
        val chunks = mutableListOf<ChunkDiTesto>()
        var inizio = 0
        while (inizio < testo.length) {
            val fineMassima = (inizio + dimensione).coerceAtMost(testo.length)
            val fine = if (fineMassima < testo.length) trovaTaglio(testo, inizio, fineMassima) else fineMassima
            chunks += ChunkDiTesto(testo.substring(inizio, fine), inizio)
            if (fine >= testo.length) break
            // Il chunk successivo arretra dell'overlap, ma avanza sempre di almeno
            // un carattere rispetto al precedente: niente loop infiniti.
            inizio = (fine - overlapEffettivo).coerceAtLeast(inizio + 1)
        }
        return chunks
    }

    /**
     * Cerca il punto di taglio migliore nella finestra `(inizio, fineMassima]`:
     * prima il confine di frase più a destra, poi un confine di parola, altrimenti
     * il taglio duro a fine finestra.
     */
    private fun trovaTaglio(
        testo: String,
        inizio: Int,
        fineMassima: Int,
    ): Int {
        for (i in fineMassima downTo inizio + 2) {
            val precedente = testo[i - 1]
            val successivo = if (i < testo.length) testo[i] else ' '
            if (precedente in FINE_FRASE && successivo.isWhitespace()) return i
        }
        for (i in fineMassima downTo inizio + 2) {
            if (testo[i - 1].isWhitespace()) return i
        }
        return fineMassima
    }

    companion object {
        const val DIMENSIONE_DEFAULT = 1000
        const val OVERLAP_DEFAULT = 150
        private const val FINE_FRASE = ".!?…"
    }
}
