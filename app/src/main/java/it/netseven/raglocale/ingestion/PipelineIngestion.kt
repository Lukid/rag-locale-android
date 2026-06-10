package it.netseven.raglocale.ingestion

import it.netseven.raglocale.retrieval.Embedder
import it.netseven.raglocale.store.ChunkDaIndicizzare
import it.netseven.raglocale.store.VectorStore

/**
 * Orchestratore dell'ingestion (task 4.2): trasforma un [NormalizedText] — qualunque sia la
 * sorgente — in un indice persistente, passando per `chunking → embedding → indicizzazione`.
 * Riporta il progresso (chunk processati sul totale) e applica i limiti del documento
 * (vuoto, troppo grande) come da spec `document-ingestion`.
 *
 * **Niente stato parziale (spec URL):** tutti gli embedding sono calcolati prima della
 * scrittura, e [VectorStore.indicizza] è transazionale — se l'embedding fallisce a metà,
 * l'indice resta invariato. La sostituzione dell'indice esistente (modello a documento
 * singolo) è responsabilità del chiamante, che decide insieme al controllo di coerenza
 * dell'embedder (gruppo 6).
 */
class PipelineIngestion(
    private val chunker: Chunker,
    private val embedder: Embedder,
    private val store: VectorStore,
    private val limiteMaxChunk: Int = LIMITE_MAX_CHUNK_DEFAULT,
) {
    suspend fun indicizza(
        documento: NormalizedText,
        onProgress: (processati: Int, totale: Int) -> Unit = { _, _ -> },
    ): EsitoIngestion {
        if (documento.testo.isBlank()) return EsitoIngestion.Errore(ErroreIngestion.DocumentoVuoto)

        val chunks = chunker.spezza(documento.testo)
        if (chunks.isEmpty()) return EsitoIngestion.Errore(ErroreIngestion.DocumentoVuoto)
        if (chunks.size > limiteMaxChunk) {
            return EsitoIngestion.Errore(ErroreIngestion.DocumentoTroppoGrande(chunks.size, limiteMaxChunk))
        }

        val daIndicizzare = ArrayList<ChunkDaIndicizzare>(chunks.size)
        chunks.forEachIndexed { indice, chunk ->
            val embedding = embedder.embedDocumento(chunk.testo)
            daIndicizzare += ChunkDaIndicizzare(indiceChunk = indice, testo = chunk.testo, embedding = embedding)
            onProgress(indice + 1, chunks.size)
        }

        val nome = documento.titolo ?: documento.origine ?: DOCUMENTO_SENZA_NOME
        store.indicizza(nome, daIndicizzare, embedder.id)
        return EsitoIngestion.Completata(nome, daIndicizzare.size)
    }

    companion object {
        /** Tetto di sicurezza sul numero di chunk per documento (tarato in validazione, D7). */
        const val LIMITE_MAX_CHUNK_DEFAULT = 2000
        private const val DOCUMENTO_SENZA_NOME = "documento"
    }
}
