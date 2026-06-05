package it.netseven.raglocale.inference

/** Richiesta strutturata per una generazione chat LiteRT-LM. */
data class ConversationRequest(
    val systemInstruction: String?,
    val initialMessages: List<ConversationTurn>,
    val userMessage: String,
) {
    init {
        require(userMessage.isNotBlank()) { "Il messaggio utente non puo' essere vuoto" }
    }
}

/** Turno storico da passare a ConversationConfig.initialMessages. */
data class ConversationTurn(
    val role: ConversationRole,
    val text: String,
)

enum class ConversationRole { USER, MODEL }
