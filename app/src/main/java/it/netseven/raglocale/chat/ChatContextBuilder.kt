package it.netseven.raglocale.chat

/**
 * Costruisce il contesto testuale da passare al modello, assemblando la **cronologia di
 * sessione + il nuovo messaggio utente** (spec on-device-chat, task 4.2). Solo testo,
 * nessun grounding/documento. Logica **pura** → unit test (task 4.5).
 */
object ChatContextBuilder {
    const val DEFAULT_SYSTEM_PREAMBLE = "Sei un assistente utile. Rispondi in modo conciso e in italiano."

    private const val USER_TAG = "Utente"
    private const val MODEL_TAG = "Assistente"

    fun build(
        history: List<ChatMessage>,
        userMessage: String,
        systemPreamble: String? = DEFAULT_SYSTEM_PREAMBLE,
    ): String {
        val sb = StringBuilder()
        if (!systemPreamble.isNullOrBlank()) {
            sb.append(systemPreamble.trim()).append("\n\n")
        }
        for (message in history) {
            val tag = if (message.role == Role.USER) USER_TAG else MODEL_TAG
            sb.append(tag).append(": ").append(message.text.trim()).append("\n")
        }
        sb.append(USER_TAG).append(": ").append(userMessage.trim()).append("\n")
        sb.append(MODEL_TAG).append(": ")
        return sb.toString()
    }
}
