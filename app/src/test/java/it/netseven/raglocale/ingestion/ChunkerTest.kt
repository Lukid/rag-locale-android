package it.netseven.raglocale.ingestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkerTest {
    @Test
    fun `testo vuoto o di soli spazi produce lista vuota`() {
        val chunker = Chunker(dimensione = 100, overlap = 20)
        assertTrue(chunker.spezza("").isEmpty())
        assertTrue(chunker.spezza("   \n\t  ").isEmpty())
    }

    @Test
    fun `testo piu corto della dimensione produce un solo chunk con l'intero testo`() {
        val chunker = Chunker(dimensione = 100, overlap = 20)
        val risultato = chunker.spezza("Una frase breve.")
        assertEquals(1, risultato.size)
        assertEquals("Una frase breve.", risultato[0].testo)
        assertEquals(0, risultato[0].inizio)
    }

    @Test
    fun `nessun chunk supera la dimensione configurata`() {
        val chunker = Chunker(dimensione = 50, overlap = 10)
        val testo = FRASI.repeat(5)
        val risultato = chunker.spezza(testo)
        assertTrue("Atteso più di un chunk", risultato.size > 1)
        risultato.forEach { chunk ->
            assertTrue(
                "Chunk di ${chunk.testo.length} caratteri supera la dimensione 50",
                chunk.testo.length <= 50,
            )
        }
    }

    @Test
    fun `i chunk coprono tutto il testo senza buchi`() {
        val chunker = Chunker(dimensione = 60, overlap = 15)
        val testo = FRASI.repeat(4)
        val risultato = chunker.spezza(testo)
        // Il primo parte dall'inizio, l'ultimo arriva alla fine.
        assertEquals(0, risultato.first().inizio)
        val ultimo = risultato.last()
        assertEquals(testo.length, ultimo.inizio + ultimo.testo.length)
        // Ogni chunk successivo inizia dentro (o subito dopo) la copertura del precedente.
        risultato.zipWithNext { prima, dopo ->
            assertTrue(
                "Buco tra chunk: ${prima.inizio}+${prima.testo.length} -> ${dopo.inizio}",
                dopo.inizio <= prima.inizio + prima.testo.length,
            )
            assertTrue("Nessun progresso tra chunk", dopo.inizio > prima.inizio)
        }
    }

    @Test
    fun `chunk consecutivi si sovrappongono quando l'overlap e positivo`() {
        val chunker = Chunker(dimensione = 60, overlap = 15)
        val risultato = chunker.spezza(FRASI.repeat(4))
        risultato.zipWithNext { prima, dopo ->
            assertTrue(
                "Atteso overlap tra chunk consecutivi",
                dopo.inizio < prima.inizio + prima.testo.length,
            )
        }
    }

    @Test
    fun `taglia preferibilmente a confine di frase`() {
        // Frasi da ~28 caratteri: nel budget di 60 ce ne stanno due intere.
        val testo = "La prima frase del testo qui. La seconda frase continua qui. La terza frase chiude il testo qui."
        val chunker = Chunker(dimensione = 65, overlap = 0)
        val risultato = chunker.spezza(testo)
        // Tutti i chunk tranne eventualmente l'ultimo terminano a confine di frase.
        risultato.dropLast(1).forEach { chunk ->
            assertTrue(
                "Chunk non termina a confine di frase: \"${chunk.testo}\"",
                chunk.testo.trimEnd().endsWith("."),
            )
        }
    }

    @Test
    fun `l'offset inizio corrisponde alla posizione reale nel testo originale`() {
        val testo = FRASI.repeat(3)
        val chunker = Chunker(dimensione = 70, overlap = 20)
        chunker.spezza(testo).forEach { chunk ->
            assertEquals(
                "Il testo del chunk non corrisponde all'offset dichiarato",
                chunk.testo,
                testo.substring(chunk.inizio, chunk.inizio + chunk.testo.length),
            )
        }
    }

    @Test
    fun `overlap maggiore o uguale alla dimensione non manda in loop e copre il testo`() {
        val chunker = Chunker(dimensione = 30, overlap = 30)
        val testo = FRASI.repeat(3)
        val risultato = chunker.spezza(testo)
        assertTrue(risultato.isNotEmpty())
        val ultimo = risultato.last()
        assertEquals(testo.length, ultimo.inizio + ultimo.testo.length)
        // Progresso garantito: niente chunk duplicati nella stessa posizione.
        risultato.zipWithNext { prima, dopo ->
            assertTrue(dopo.inizio > prima.inizio)
        }
    }

    companion object {
        private const val FRASI = "Il gatto dorme sul divano. La luna illumina la stanza vuota. Il treno parte alle otto in punto. "
    }
}
