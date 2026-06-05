package it.netseven.raglocale.chat

import it.netseven.raglocale.inference.ConversationRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatContextBuilderTest {
    @Test
    fun `con cronologia vuota produce system instruction e nuovo messaggio`() {
        val request =
            ChatContextBuilder.buildRequest(
                history = emptyList(),
                userMessage = "Ciao",
                systemInstruction = "PREAMBOLO",
            )
        assertEquals("PREAMBOLO", request.systemInstruction)
        assertTrue(request.initialMessages.isEmpty())
        assertEquals("Ciao", request.userMessage)
    }

    @Test
    fun `mantiene la cronologia nell'ordine dei turni`() {
        val history =
            listOf(
                ChatMessage(Role.USER, "Primo"),
                ChatMessage(Role.MODEL, "Risposta"),
            )
        val request = ChatContextBuilder.buildRequest(history, "Secondo", systemInstruction = null)
        assertEquals(ConversationRole.USER, request.initialMessages[0].role)
        assertEquals("Primo", request.initialMessages[0].text)
        assertEquals(ConversationRole.MODEL, request.initialMessages[1].role)
        assertEquals("Risposta", request.initialMessages[1].text)
        assertEquals("Secondo", request.userMessage)
    }

    @Test
    fun `system instruction nulla resta nulla`() {
        val request = ChatContextBuilder.buildRequest(emptyList(), "X", systemInstruction = null)
        assertNull(request.systemInstruction)
    }
}
