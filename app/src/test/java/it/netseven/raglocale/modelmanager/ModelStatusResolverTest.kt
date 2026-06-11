package it.netseven.raglocale.modelmanager

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelStatusResolverTest {
    @Test
    fun `download in corso ha precedenza`() {
        assertEquals(
            ModelStatus.DOWNLOADING,
            ModelStatusResolver.resolve(fileExists = true, fileSizeBytes = 10, downloadInProgress = true),
        )
    }

    @Test
    fun `file presente sopra soglia minima e' pronto`() {
        assertEquals(
            ModelStatus.READY,
            ModelStatusResolver.resolve(
                fileExists = true,
                fileSizeBytes = ModelStatusResolver.MIN_READY_FILE_BYTES,
                downloadInProgress = false,
            ),
        )
    }

    @Test
    fun `file assente e' non scaricato`() {
        assertEquals(
            ModelStatus.NOT_DOWNLOADED,
            ModelStatusResolver.resolve(fileExists = false, fileSizeBytes = 0, downloadInProgress = false),
        )
    }

    @Test
    fun `file presente ma vuoto e' non scaricato`() {
        assertEquals(
            ModelStatus.NOT_DOWNLOADED,
            ModelStatusResolver.resolve(fileExists = true, fileSizeBytes = 0, downloadInProgress = false),
        )
    }

    @Test
    fun `file piccolo non basta per essere pronto`() {
        assertEquals(
            ModelStatus.NOT_DOWNLOADED,
            ModelStatusResolver.resolve(fileExists = true, fileSizeBytes = 10, downloadInProgress = false),
        )
    }

    @Test
    fun `dimensione attesa accetta file entro tolleranza`() {
        assertEquals(
            ModelStatus.READY,
            ModelStatusResolver.resolve(
                fileExists = true,
                fileSizeBytes = 700,
                downloadInProgress = false,
                expectedSizeBytes = 1_000,
            ),
        )
    }

    @Test
    fun `dimensione attesa rifiuta file troppo parziale`() {
        assertEquals(
            ModelStatus.NOT_DOWNLOADED,
            ModelStatusResolver.resolve(
                fileExists = true,
                fileSizeBytes = 699,
                downloadInProgress = false,
                expectedSizeBytes = 1_000,
            ),
        )
    }

    // --- Macchina a stati per tipo (task 5.4): l'embedder è pronto solo con modello + companion ---

    private val llm =
        ModelInfo(
            id = "llm",
            displayName = "LLM",
            type = ModelType.LLM,
            repo = "",
            fileName = "x.litertlm",
            sizeBytes = 1_000,
            quantization = "int4",
        )

    private val embedder =
        ModelInfo(
            id = "emb",
            displayName = "Embedder",
            type = ModelType.EMBEDDER,
            repo = "",
            fileName = "x.tflite",
            sizeBytes = 1_000,
            quantization = "int4",
            companion = CompanionArtifact(fileName = "tok.model", sizeBytes = 100),
        )

    @Test
    fun `LLM senza companion e' pronto col solo file modello`() {
        assertEquals(
            ModelStatus.READY,
            ModelStatusResolver.resolveModel(
                model = llm,
                primaryExists = true,
                primarySizeBytes = 1_000,
                companionExists = false,
                companionSizeBytes = 0,
            ),
        )
    }

    @Test
    fun `embedder e' pronto solo con modello e companion entrambi presenti`() {
        assertEquals(
            ModelStatus.READY,
            ModelStatusResolver.resolveModel(
                model = embedder,
                primaryExists = true,
                primarySizeBytes = 1_000,
                companionExists = true,
                companionSizeBytes = 100,
            ),
        )
    }

    @Test
    fun `embedder col solo modello e senza tokenizer non e' pronto`() {
        assertEquals(
            ModelStatus.NOT_DOWNLOADED,
            ModelStatusResolver.resolveModel(
                model = embedder,
                primaryExists = true,
                primarySizeBytes = 1_000,
                companionExists = false,
                companionSizeBytes = 0,
            ),
        )
    }

    @Test
    fun `embedder col solo tokenizer e senza modello non e' pronto`() {
        assertEquals(
            ModelStatus.NOT_DOWNLOADED,
            ModelStatusResolver.resolveModel(
                model = embedder,
                primaryExists = false,
                primarySizeBytes = 0,
                companionExists = true,
                companionSizeBytes = 100,
            ),
        )
    }
}
