package it.netseven.raglocale.modelmanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    @Test
    fun `il default e' Gemma 4 E2B-it`() {
        assertEquals("gemma-4-e2b-it", ModelCatalog.default.id)
        assertTrue(ModelCatalog.default.isDefault)
        assertEquals("litert-community/gemma-4-E2B-it-litert-lm", ModelCatalog.default.repo)
    }

    @Test
    fun `il file del default e' un artefatto litertlm`() {
        assertTrue(ModelCatalog.default.fileName.endsWith(".litertlm"))
    }

    @Test
    fun `byId ritrova il modello e null se assente`() {
        assertNotNull(ModelCatalog.byId("gemma-4-e2b-it"))
        assertEquals(null, ModelCatalog.byId("inesistente"))
    }

    @Test
    fun `in M1 il catalogo contiene solo LLM esattamente un default`() {
        assertEquals(1, ModelCatalog.models.count { it.isDefault })
    }
}
