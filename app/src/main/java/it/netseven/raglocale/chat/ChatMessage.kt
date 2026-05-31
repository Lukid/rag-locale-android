package it.netseven.raglocale.chat

/** Ruolo di un turno della conversazione (solo testo in M1). */
enum class Role { USER, MODEL }

/**
 * Un messaggio della sessione di chat.
 * [streaming] è true mentre il turno del modello sta ancora arrivando token-per-token.
 */
data class ChatMessage(
    val role: Role,
    val text: String,
    val streaming: Boolean = false,
)
