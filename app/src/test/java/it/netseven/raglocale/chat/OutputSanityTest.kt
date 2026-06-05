package it.netseven.raglocale.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputSanityTest {
    @Test
    fun `non marca come corrotto testo italiano normale`() {
        val text =
            "Ciao, posso aiutarti con un riassunto breve del documento e indicare " +
                "i passaggi principali con citazioni essenziali."

        assertFalse(OutputSanity.looksCorrupt(text))
    }

    @Test
    fun `marca come corrotto testo con molti caratteri di controllo`() {
        val text =
            buildString {
                repeat(80) { append('\u0000') }
                append("abc")
            }

        assertTrue(OutputSanity.looksCorrupt(text))
    }

    @Test
    fun `marca come corrotto testo non latino senza spazi`() {
        val text = "漢字".repeat(40)

        assertTrue(OutputSanity.looksCorrupt(text))
    }

    @Test
    fun `ignora campioni troppo brevi`() {
        assertFalse(OutputSanity.looksCorrupt("\u0000\u0000\u0000"))
    }
}
