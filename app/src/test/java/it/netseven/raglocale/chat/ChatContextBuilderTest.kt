package it.netseven.raglocale.chat

import org.junit.Assert.assertTrue
import org.junit.Test

class ChatContextBuilderTest {
    @Test
    fun `con cronologia vuota include preambolo e nuovo messaggio`() {
        val out =
            ChatContextBuilder.build(
                history = emptyList(),
                userMessage = "Ciao",
                systemPreamble = "PREAMBOLO",
            )
        assertTrue(out.startsWith("PREAMBOLO"))
        assertTrue(out.contains("Utente: Ciao"))
        assertTrue(out.trimEnd().endsWith("Assistente:"))
    }

    @Test
    fun `assembla la cronologia nell'ordine dei turni`() {
        val history =
            listOf(
                ChatMessage(Role.USER, "Primo"),
                ChatMessage(Role.MODEL, "Risposta"),
            )
        val out = ChatContextBuilder.build(history, "Secondo", systemPreamble = null)
        val idxPrimo = out.indexOf("Utente: Primo")
        val idxRisposta = out.indexOf("Assistente: Risposta")
        val idxSecondo = out.indexOf("Utente: Secondo")
        assertTrue(idxPrimo in 0 until idxRisposta)
        assertTrue(idxRisposta in 0 until idxSecondo)
    }

    @Test
    fun `preambolo nullo non aggiunge testo iniziale`() {
        val out = ChatContextBuilder.build(emptyList(), "X", systemPreamble = null)
        assertTrue(out.startsWith("Utente: X"))
    }
}
