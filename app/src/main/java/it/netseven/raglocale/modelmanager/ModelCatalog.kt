package it.netseven.raglocale.modelmanager

/**
 * Catalogo dei modelli per **tipo** (spec model-manager M2): LLM ed embedder.
 * Default suggeriti: **Gemma 4 E2B-it** tra gli LLM, **EmbeddingGemma** tra gli embedder
 * (con **Gecko** come alternativa di riserva). I checksum md5 noti (lezione M1) abilitano
 * la verifica d'integrità all'import.
 */
object ModelCatalog {
    val GEMMA_4_E2B =
        ModelInfo(
            id = "gemma-4-e2b-it",
            displayName = "Gemma 4 E2B-it",
            type = ModelType.LLM,
            repo = "litert-community/gemma-4-E2B-it-litert-lm",
            fileName = "gemma-4-E2B-it.litertlm",
            // Stima: il file reale va misurato sul device. Nessun md5 canonico committato
            // (l'utente importa il proprio .litertlm) → l'import ricade sul controllo dimensione.
            sizeBytes = 3_100_000_000L,
            quantization = "int4",
            isDefault = true,
        )

    /**
     * EmbeddingGemma 300M, variante seq512 mixed-precision (design M2 D2, spike riuscito).
     * Due file: il `.tflite` e il tokenizer `sentencepiece.model`. Checksum md5 dallo spike
     * `EmbedderSmokeTest`. Dimensione del `.tflite` ~196 MB (da misurare in validazione).
     */
    val EMBEDDING_GEMMA =
        ModelInfo(
            id = "embeddinggemma-300m",
            displayName = "EmbeddingGemma 300M",
            type = ModelType.EMBEDDER,
            repo = "litert-community/embeddinggemma-300m",
            fileName = "embeddinggemma-300M_seq512_mixed-precision.tflite",
            sizeBytes = 196_000_000L,
            quantization = "mixed-precision (int4/int8)",
            isDefault = true,
            expectedMd5 = "edd86dab69e9333794ed983b4ab6d0d3",
            companion =
                CompanionArtifact(
                    fileName = "sentencepiece.model",
                    sizeBytes = 4_680_000L,
                    expectedMd5 = "b0cab25d6777ffdf26856aaf6316fbbc",
                ),
        )

    /**
     * Gecko: alternativa di riserva storica dell'SDK (768 dim). **Non** spiked in M2 →
     * coordinate e checksum non verificati (l'import ricade sul controllo dimensione).
     */
    val GECKO =
        ModelInfo(
            id = "gecko",
            displayName = "Gecko (riserva)",
            type = ModelType.EMBEDDER,
            // Embedder di riserva del modulo localagents-rag; coordinate da verificare se servisse.
            repo = "",
            fileName = "gecko.tflite",
            sizeBytes = 120_000_000L,
            quantization = "quant",
            isDefault = false,
            companion = CompanionArtifact(fileName = "sentencepiece.model", sizeBytes = 4_680_000L),
        )

    val models: List<ModelInfo> = listOf(GEMMA_4_E2B, EMBEDDING_GEMMA, GECKO)

    /** Modelli di un dato tipo, nell'ordine del catalogo. */
    fun forType(type: ModelType): List<ModelInfo> = models.filter { it.type == type }

    /** Default suggerito per un tipo. */
    fun defaultFor(type: ModelType): ModelInfo = forType(type).first { it.isDefault }

    fun byId(id: String): ModelInfo? = models.firstOrNull { it.id == id }
}
