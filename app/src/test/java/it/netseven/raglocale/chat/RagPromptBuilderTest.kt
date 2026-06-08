package it.netseven.raglocale.chat

import it.netseven.raglocale.retrieval.ChunkRecuperato
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RagPromptBuilderTest {
    @Test
    fun `il prompt contiene i chunk numerati in ordine e la domanda`() {
        val prompt =
            RagPromptBuilder.costruisci(
                domanda = "Dove dorme il gatto?",
                chunks =
                    listOf(
                        chunk("Il gatto dorme sul divano.", indice = 0),
                        chunk("La luna illumina la stanza.", indice = 1),
                    ),
            )
        assertTrue("Manca il marcatore [1]", prompt.contains("[1]"))
        assertTrue("Manca il marcatore [2]", prompt.contains("[2]"))
        assertTrue("Manca il testo del primo chunk", prompt.contains("Il gatto dorme sul divano."))
        assertTrue("Manca il testo del secondo chunk", prompt.contains("La luna illumina la stanza."))
        assertTrue("Manca la domanda", prompt.contains("Dove dorme il gatto?"))
        // Il primo chunk compare prima del secondo.
        assertTrue(prompt.indexOf("[1]") < prompt.indexOf("[2]"))
    }

    @Test
    fun `il prompt istruisce a rispondere solo dal contesto e a citare`() {
        val prompt = RagPromptBuilder.costruisci("Domanda?", listOf(chunk("Testo.", 0)))
        val promptMinuscolo = prompt.lowercase()
        assertTrue("Manca l'istruzione sul contesto", promptMinuscolo.contains("contesto"))
        assertTrue("Manca l'istruzione di citare", promptMinuscolo.contains("cita"))
        assertTrue(
            "Manca l'istruzione per l'informazione assente",
            promptMinuscolo.contains("non è presente") || promptMinuscolo.contains("non e' presente"),
        )
    }

    @Test
    fun `estrae le citazioni valide dalla risposta`() {
        val citazioni =
            RagPromptBuilder.estraiCitazioni(
                risposta = "Il gatto dorme sul divano [1], come conferma anche il contesto [3].",
                numeroChunk = 4,
            )
        assertEquals(setOf(1, 3), citazioni)
    }

    @Test
    fun `scarta i marcatori fuori dall'intervallo dei chunk`() {
        val citazioni = RagPromptBuilder.estraiCitazioni("Risposta [7] e [0] e [2].", numeroChunk = 3)
        assertEquals(setOf(2), citazioni)
    }

    @Test
    fun `risposta senza marcatori produce insieme vuoto`() {
        assertTrue(RagPromptBuilder.estraiCitazioni("Nessuna citazione qui.", numeroChunk = 5).isEmpty())
    }

    @Test
    fun `i marcatori duplicati contano una volta sola`() {
        val citazioni = RagPromptBuilder.estraiCitazioni("[2] e poi ancora [2].", numeroChunk = 3)
        assertEquals(setOf(2), citazioni)
    }

    @Test
    fun `gestisce i marcatori a piu cifre`() {
        val citazioni = RagPromptBuilder.estraiCitazioni("Vedi [12].", numeroChunk = 15)
        assertEquals(setOf(12), citazioni)
    }

    private fun chunk(
        testo: String,
        indice: Int,
    ): ChunkRecuperato = ChunkRecuperato(testo = testo, score = 0.9, documento = "doc", indiceChunk = indice)
}
