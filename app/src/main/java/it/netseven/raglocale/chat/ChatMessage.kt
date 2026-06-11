package it.netseven.raglocale.chat

import it.netseven.raglocale.retrieval.ChunkRecuperato

/** Ruolo di un turno della conversazione (solo testo). */
enum class Role { USER, MODEL }

/**
 * Un messaggio della sessione di chat.
 * [streaming] è true mentre il turno del modello sta ancora arrivando token-per-token.
 * [retrieval] è valorizzato sui turni del modello generati in modalità RAG: porta i chunk
 * recuperati e (a generazione finita) quali sono stati citati — è il dato del pannello
 * didattico (spec 6.2).
 */
data class ChatMessage(
    val role: Role,
    val text: String,
    val streaming: Boolean = false,
    val retrieval: RetrievalTrace? = null,
)

/**
 * Contesto recuperato per una risposta grounded. [chunks] sono numerati posizionalmente da 1
 * (coerente con la numerazione del prompt di [RagPromptBuilder]); [citati] sono le posizioni
 * 1-based citate nella risposta. La trasparenza non dipende dalla qualità della risposta:
 * i chunk si mostrano comunque, anche senza citazioni valide (spec 6.2).
 */
data class RetrievalTrace(
    val chunks: List<ChunkRecuperato>,
    val citati: Set<Int> = emptySet(),
    val avviso: String? = null,
)
