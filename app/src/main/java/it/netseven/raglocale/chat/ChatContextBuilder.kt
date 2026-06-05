package it.netseven.raglocale.chat

import it.netseven.raglocale.inference.ConversationRequest
import it.netseven.raglocale.inference.ConversationRole
import it.netseven.raglocale.inference.ConversationTurn

/**
 * Costruisce la richiesta chat strutturata da passare a LiteRT-LM. La cronologia resta
 * separata dal nuovo turno utente cosi' il runtime puo' applicare il chat template del modello.
 */
object ChatContextBuilder {
    const val DEFAULT_SYSTEM_INSTRUCTION = "Sei un assistente utile. Rispondi in modo conciso e in italiano."

    fun buildRequest(
        history: List<ChatMessage>,
        userMessage: String,
        systemInstruction: String? = DEFAULT_SYSTEM_INSTRUCTION,
    ): ConversationRequest =
        ConversationRequest(
            systemInstruction = systemInstruction?.trim()?.takeIf { it.isNotEmpty() },
            initialMessages =
                history
                    .filter { it.text.isNotBlank() }
                    .map { message ->
                        ConversationTurn(
                            role = if (message.role == Role.USER) ConversationRole.USER else ConversationRole.MODEL,
                            text = message.text.trim(),
                        )
                    },
            userMessage = userMessage.trim(),
        )
}
