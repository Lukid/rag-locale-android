package it.netseven.raglocale.ingestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Normalizzazione condivisa da tutte le sorgenti (design M2, "ogni sorgente produce
 * NormalizedText"): pulizia degli artefatti di formato e uniformazione della spaziatura,
 * preservando i confini di paragrafo che il [Chunker] usa per i tagli. Componente puro.
 */
class TextNormalizerTest {
    private val normalizer = TextNormalizer()

    @Test
    fun `uniforma i fine riga Windows e Mac classico in newline singoli`() {
        assertEquals("riga uno\nriga due\nriga tre", normalizer.normalizza("riga uno\r\nriga due\rriga tre"))
    }

    @Test
    fun `comprime sequenze di spazi e tab in un solo spazio`() {
        assertEquals("parola uno due", normalizer.normalizza("parola   uno\t\tdue"))
    }

    @Test
    fun `comprime tre o piu newline consecutivi in un confine di paragrafo doppio`() {
        assertEquals("primo paragrafo\n\nsecondo paragrafo", normalizer.normalizza("primo paragrafo\n\n\n\nsecondo paragrafo"))
    }

    @Test
    fun `taglia spazi e righe vuote in testa e in coda`() {
        assertEquals("contenuto utile", normalizer.normalizza("\n\n   contenuto utile  \n\n"))
    }

    @Test
    fun `ricuce le parole spezzate dal trattino morbido dei PDF`() {
        // U+00AD (soft hyphen): nei PDF impaginati spezza una parola a fine riga.
        assertEquals("computer", normalizer.normalizza("compu­ter"))
    }

    @Test
    fun `rimuove caratteri a larghezza zero e BOM`() {
        // U+FEFF (BOM) in testa, U+200B (zero-width space) interno.
        assertEquals("testo pulito", normalizer.normalizza("﻿testo​ pulito"))
    }

    @Test
    fun `sostituisce lo spazio insecabile con uno spazio normale`() {
        // U+00A0 (NBSP) tra le due parole.
        assertEquals("dieci euro", normalizer.normalizza("dieci euro"))
    }

    @Test
    fun `preserva gli accenti italiani`() {
        val testo = "Però è così: città, università, perché."
        assertEquals(testo, normalizer.normalizza(testo))
    }

    @Test
    fun `un testo gia pulito resta invariato`() {
        val testo = "Prima frase. Seconda frase.\n\nNuovo paragrafo."
        assertEquals(testo, normalizer.normalizza(testo))
    }

    @Test
    fun `testo di soli spazi diventa vuoto`() {
        assertTrue(normalizer.normalizza("   \n\t  \n  ").isEmpty())
    }
}
