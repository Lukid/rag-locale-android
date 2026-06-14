package it.netseven.raglocale.modelmanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    @Test
    fun `il default LLM e' Gemma 4 E2B-it`() {
        val llm = ModelCatalog.defaultFor(ModelType.LLM)
        assertEquals("gemma-4-e2b-it", llm.id)
        assertEquals(ModelType.LLM, llm.type)
        assertTrue(llm.isDefault)
        assertEquals("litert-community/gemma-4-E2B-it-litert-lm", llm.repo)
    }

    @Test
    fun `il default embedder e' EmbeddingGemma`() {
        val embedder = ModelCatalog.defaultFor(ModelType.EMBEDDER)
        assertEquals("embeddinggemma-300m", embedder.id)
        assertEquals(ModelType.EMBEDDER, embedder.type)
        assertTrue(embedder.isDefault)
    }

    @Test
    fun `Gecko e' un embedder alternativo non default`() {
        val embedders = ModelCatalog.forType(ModelType.EMBEDDER)
        val gecko = embedders.firstOrNull { it.id == "gecko" }
        assertNotNull("Gecko deve essere presente come alternativa di riserva", gecko)
        assertEquals(ModelType.EMBEDDER, gecko!!.type)
        assertTrue("Gecko non e' il default", !gecko.isDefault)
    }

    @Test
    fun `esattamente un default per tipo`() {
        assertEquals(1, ModelCatalog.forType(ModelType.LLM).count { it.isDefault })
        assertEquals(1, ModelCatalog.forType(ModelType.EMBEDDER).count { it.isDefault })
    }

    @Test
    fun `l'embedder di default e' a due file modello tflite piu' tokenizer con checksum noti`() {
        val embedder = ModelCatalog.defaultFor(ModelType.EMBEDDER)
        assertTrue("Il modello embedder e' un .tflite", embedder.fileName.endsWith(".tflite"))
        assertNotNull("Il checksum del modello e' noto (lezione M1)", embedder.expectedMd5)
        val tokenizer = embedder.companion
        assertNotNull("L'embedder richiede il tokenizer sentencepiece", tokenizer)
        assertEquals("sentencepiece.model", tokenizer!!.fileName)
        assertNotNull("Il checksum del tokenizer e' noto", tokenizer.expectedMd5)
    }

    @Test
    fun `il default LLM resta un artefatto litertlm senza companion`() {
        val llm = ModelCatalog.defaultFor(ModelType.LLM)
        assertTrue(llm.fileName.endsWith(".litertlm"))
        assertNull("Il LLM non ha file companion", llm.companion)
    }

    @Test
    fun `targets elenca il file del modello e il companion quando presente`() {
        val llm = ModelCatalog.defaultFor(ModelType.LLM)
        assertEquals(listOf(llm.fileName), llm.targets().map { it.fileName })

        val embedder = ModelCatalog.defaultFor(ModelType.EMBEDDER)
        assertEquals(
            listOf(embedder.fileName, "sentencepiece.model"),
            embedder.targets().map { it.fileName },
        )
    }

    @Test
    fun `byId ritrova il modello e null se assente`() {
        assertNotNull(ModelCatalog.byId("gemma-4-e2b-it"))
        assertNotNull(ModelCatalog.byId("embeddinggemma-300m"))
        assertEquals(null, ModelCatalog.byId("inesistente"))
    }

    @Test
    fun `il LLM di default ha url di download pubblico, md5 canonico e size reale`() {
        val llm = ModelCatalog.defaultFor(ModelType.LLM)
        assertEquals(2_588_147_712L, llm.sizeBytes)
        assertEquals("1b8446203a216cfd31f6a2a22f75e5e5", llm.expectedMd5)
        assertTrue("Il LLM non e' gated (scaricabile anonimo)", !llm.gated)
        assertNotNull("Il LLM ha un url di download", llm.downloadUrl)
        assertTrue(llm.downloadUrl!!.endsWith("/gemma-4-E2B-it.litertlm"))
        assertTrue("Il LLM e' scaricabile in-app", llm.scaricabile)
    }

    @Test
    fun `l'embedder di default e' gated con url di download per modello e tokenizer`() {
        val embedder = ModelCatalog.defaultFor(ModelType.EMBEDDER)
        assertTrue("L'embedder e' gated (richiede login HF)", embedder.gated)
        assertNotNull("Il modello embedder ha un url di download", embedder.downloadUrl)
        assertTrue(embedder.downloadUrl!!.endsWith("/embeddinggemma-300M_seq512_mixed-precision.tflite"))
        val tokenizer = embedder.companion
        assertNotNull("Il tokenizer ha un url di download", tokenizer!!.downloadUrl)
        assertTrue(tokenizer.downloadUrl!!.endsWith("/sentencepiece.model"))
        assertTrue("L'embedder e' scaricabile in-app (modello + tokenizer)", embedder.scaricabile)
    }

    @Test
    fun `Gecko non e' scaricabile in-app (coordinate non verificate)`() {
        val gecko = ModelCatalog.byId("gecko")!!
        assertNull("Gecko non ha url di download", gecko.downloadUrl)
        assertTrue("Gecko non e' scaricabile in-app", !gecko.scaricabile)
    }
}
