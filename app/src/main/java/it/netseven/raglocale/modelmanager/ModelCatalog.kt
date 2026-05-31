package it.netseven.raglocale.modelmanager

/**
 * Catalogo dei modelli LLM disponibili. In M1 contiene **solo LLM** (nessun embedder).
 * Default: **Gemma 4 E2B-it** (`litert-community/gemma-4-E2B-it-litert-lm`).
 */
object ModelCatalog {
    val GEMMA_4_E2B =
        ModelInfo(
            id = "gemma-4-e2b-it",
            displayName = "Gemma 4 E2B-it",
            repo = "litert-community/gemma-4-E2B-it-litert-lm",
            fileName = "gemma-4-E2B-it.litertlm",
            // Stima: il file reale va misurato sul device (TODO al primo import/download).
            sizeBytes = 3_100_000_000L,
            quantization = "int4",
            isDefault = true,
        )

    val models: List<ModelInfo> = listOf(GEMMA_4_E2B)

    val default: ModelInfo get() = models.first { it.isDefault }

    fun byId(id: String): ModelInfo? = models.firstOrNull { it.id == id }
}
