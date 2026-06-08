package it.netseven.raglocale.retrieval

/**
 * Chunk restituito dalla ricerca semantica, con lo score di similarità coseno e il
 * riferimento al documento d'origine.
 */
data class ChunkRecuperato(
    val testo: String,
    val score: Double,
    val documento: String,
    val indiceChunk: Int,
)

/**
 * Interfaccia "tool-shaped" della retrieval (design M2, D4): query e topK in ingresso,
 * chunk con score in uscita. Tutti i consumatori (pannello didattico, PromptBuilder)
 * passano da qui; in un eventuale M3 diventa un tool MCP locale senza riscritture.
 */
interface RicercaDocumenti {
    suspend fun cerca(
        query: String,
        topK: Int,
    ): List<ChunkRecuperato>
}
