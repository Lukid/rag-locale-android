package it.netseven.raglocale.huggingface

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client HTTP per HuggingFace: valida un access token e ne ricava lo **username** via endpoint
 * userinfo OAuth. Il parsing della risposta è una funzione pura ([parseUsername]), unit-testabile.
 */
@Singleton
class HuggingFaceApiClient(
    private val client: OkHttpClient,
) {
    // Hilt non fornisce OkHttpClient: questo costruttore ne crea uno di default. Quello primario
    // (con client iniettabile) serve ai test.
    @Inject
    constructor() : this(OkHttpClient())

    sealed interface ValidationResult {
        data class Success(val username: String) : ValidationResult

        data class Error(val message: String) : ValidationResult
    }

    suspend fun validateToken(token: String): ValidationResult =
        withContext(Dispatchers.IO) {
            if (token.isBlank()) return@withContext ValidationResult.Error("Token vuoto")
            val request =
                Request.Builder()
                    .url(HuggingFaceOAuthConfig.USERINFO_ENDPOINT)
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            try {
                client.newCall(request).execute().use { response ->
                    when (response.code) {
                        200 -> {
                            val username = response.body?.string()?.let { parseUsername(it) }
                            if (username != null) {
                                ValidationResult.Success(username)
                            } else {
                                ValidationResult.Error("Username non trovato nella risposta")
                            }
                        }
                        401 -> ValidationResult.Error("Token non valido o scaduto")
                        403 -> ValidationResult.Error("Permessi insufficienti per il token")
                        else -> ValidationResult.Error("Errore HTTP ${response.code}")
                    }
                }
            } catch (e: IOException) {
                ValidationResult.Error("Errore di rete: ${e.message ?: "connessione assente"}")
            }
        }

    companion object {
        /**
         * Estrae lo username dalla risposta userinfo: preferisce `preferred_username`, poi
         * `name`, poi `sub`. Pura: testabile senza rete.
         */
        fun parseUsername(json: String): String? {
            for (field in listOf("preferred_username", "name", "sub")) {
                val match = """"$field"\s*:\s*"([^"]+)"""".toRegex().find(json)
                val value = match?.groupValues?.get(1)
                if (!value.isNullOrBlank()) return value
            }
            return null
        }
    }
}
