package it.netseven.raglocale.chat

import it.netseven.raglocale.retrieval.ChunkRecuperato

/**
 * Costruisce il prompt grounded sui chunk recuperati (numerati in modo stabile, da [1])
 * ed estrae dalla risposta generata i marcatori di citazione `[n]`. La numerazione è
 * posizionale rispetto alla lista passata: chi mostra i chunk in UI deve usare lo
 * stesso ordine. Componente puro, unit-testato in JVM.
 */
object RagPromptBuilder {
    fun costruisci(
        domanda: String,
        chunks: List<ChunkRecuperato>,
    ): String =
        buildString {
            appendLine("Rispondi alla domanda usando solo le informazioni del contesto qui sotto.")
            appendLine("Cita i passaggi che usi col loro numero, nel formato [n].")
            appendLine("Se la risposta non si trova nel contesto, scrivi che l'informazione non è presente nel documento.")
            appendLine()
            appendLine("Contesto:")
            chunks.forEachIndexed { indice, chunk ->
                appendLine("[${indice + 1}] ${chunk.testo}")
            }
            appendLine()
            append("Domanda: ")
            append(domanda)
        }

    fun estraiCitazioni(
        risposta: String,
        numeroChunk: Int,
    ): Set<Int> =
        MARCATORE_CITAZIONE
            .findAll(risposta)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .filter { it in 1..numeroChunk }
            .toSet()

    private val MARCATORE_CITAZIONE = Regex("""\[(\d+)]""")
}
