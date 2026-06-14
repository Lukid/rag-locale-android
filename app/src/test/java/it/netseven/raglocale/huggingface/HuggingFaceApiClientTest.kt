package it.netseven.raglocale.huggingface

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HuggingFaceApiClientTest {
    @Test
    fun `preferisce preferred_username`() {
        val json = """{"sub":"abc","name":"Luca B","preferred_username":"lucab","email":"x@y.z"}"""
        assertEquals("lucab", HuggingFaceApiClient.parseUsername(json))
    }

    @Test
    fun `ripiega su name se manca preferred_username`() {
        val json = """{"sub":"abc","name":"Luca B"}"""
        assertEquals("Luca B", HuggingFaceApiClient.parseUsername(json))
    }

    @Test
    fun `ripiega su sub se mancano gli altri`() {
        val json = """{"sub":"user-123"}"""
        assertEquals("user-123", HuggingFaceApiClient.parseUsername(json))
    }

    @Test
    fun `ritorna null se non c'e' nessun campo utile`() {
        assertNull(HuggingFaceApiClient.parseUsername("""{"foo":"bar"}"""))
        assertNull(HuggingFaceApiClient.parseUsername("non json"))
    }
}
