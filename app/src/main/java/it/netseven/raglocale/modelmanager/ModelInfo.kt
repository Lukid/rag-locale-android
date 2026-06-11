package it.netseven.raglocale.modelmanager

/** Tipo di modello del catalogo: motore di generazione (LLM) o embedder della pipeline RAG. */
enum class ModelType { LLM, EMBEDDER }

/** Stato di un modello nel ciclo di vita del Model manager. */
enum class ModelStatus { NOT_DOWNLOADED, DOWNLOADING, READY }

/**
 * File accessorio richiesto da un modello oltre al file principale. Per EmbeddingGemma è il
 * tokenizer `sentencepiece.model`: senza di esso l'embedder non è utilizzabile (design M2 D2).
 */
data class CompanionArtifact(
    val fileName: String,
    val sizeBytes: Long,
    val expectedMd5: String? = null,
)

/** File importabile di un modello (principale o companion), con l'etichetta per la UI. */
data class ImportTarget(
    val fileName: String,
    val sizeBytes: Long,
    val expectedMd5: String?,
    val etichetta: String,
)

/** Metadati di un modello del catalogo (LLM o embedder). */
data class ModelInfo(
    val id: String,
    val displayName: String,
    val type: ModelType,
    val repo: String,
    val fileName: String,
    val sizeBytes: Long,
    val quantization: String,
    val isDefault: Boolean = false,
    /** Checksum md5 atteso del file principale; null se non noto (lezione M1: la dimensione non basta). */
    val expectedMd5: String? = null,
    /** File accessorio richiesto (es. tokenizer dell'embedder); null per i modelli a file singolo. */
    val companion: CompanionArtifact? = null,
) {
    /** I file da importare per rendere il modello pronto: il principale e l'eventuale companion. */
    fun targets(): List<ImportTarget> =
        buildList {
            add(ImportTarget(fileName, sizeBytes, expectedMd5, etichetta = "modello"))
            companion?.let { add(ImportTarget(it.fileName, it.sizeBytes, it.expectedMd5, etichetta = "tokenizer")) }
        }
}

/**
 * Macchina a stati (pura) del modello. Dato lo stato dei file su disco e l'eventuale download
 * in corso, decide lo [ModelStatus]. L'embedder ha un companion (tokenizer): è pronto solo se
 * **entrambi** i file sono presenti e plausibili (task 5.4 — macchina a stati per tipo).
 */
object ModelStatusResolver {
    const val MIN_READY_FILE_BYTES: Long = 100_000_000L
    private const val EXPECTED_SIZE_TOLERANCE = 0.70

    fun resolve(
        fileExists: Boolean,
        fileSizeBytes: Long,
        downloadInProgress: Boolean,
        expectedSizeBytes: Long? = null,
    ): ModelStatus =
        when {
            downloadInProgress -> ModelStatus.DOWNLOADING
            fileExists && isPlausibleReadySize(fileSizeBytes, expectedSizeBytes) -> ModelStatus.READY
            else -> ModelStatus.NOT_DOWNLOADED
        }

    /**
     * Stato complessivo del [model]: per un embedder con [CompanionArtifact] richiede modello
     * **e** companion pronti; per un modello a file singolo coincide con [resolve] sul principale.
     */
    fun resolveModel(
        model: ModelInfo,
        primaryExists: Boolean,
        primarySizeBytes: Long,
        companionExists: Boolean,
        companionSizeBytes: Long,
        downloadInProgress: Boolean = false,
    ): ModelStatus {
        val primary = resolve(primaryExists, primarySizeBytes, downloadInProgress, model.sizeBytes)
        val companion = model.companion ?: return primary
        val companionStatus = resolve(companionExists, companionSizeBytes, downloadInProgress, companion.sizeBytes)
        return when {
            primary == ModelStatus.DOWNLOADING || companionStatus == ModelStatus.DOWNLOADING -> ModelStatus.DOWNLOADING
            primary == ModelStatus.READY && companionStatus == ModelStatus.READY -> ModelStatus.READY
            else -> ModelStatus.NOT_DOWNLOADED
        }
    }

    fun isPlausibleReadySize(
        fileSizeBytes: Long,
        expectedSizeBytes: Long?,
    ): Boolean {
        val minBytes =
            expectedSizeBytes
                ?.takeIf { it > 0L }
                ?.let { (it * EXPECTED_SIZE_TOLERANCE).toLong() }
                ?: MIN_READY_FILE_BYTES
        return fileSizeBytes >= minBytes
    }
}
