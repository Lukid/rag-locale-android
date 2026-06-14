package it.netseven.raglocale.huggingface

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Flusso OAuth HuggingFace con **AppAuth + Custom Tabs** (PKCE S256, `prompt=consent` per ottenere
 * il refresh token). Unico componente che conosce AppAuth (dipendenza esterna confinata).
 * Implementa [TokenRefresher] così il rinnovo è usabile da [HuggingFaceTokenProvider].
 *
 * Porting da anti-vocale, adattato: Client ID da `BuildConfig`, niente entry "manual token".
 */
@Singleton
class HuggingFaceAuthManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val tokenManager: HuggingFaceTokenManager,
        private val apiClient: HuggingFaceApiClient,
    ) : TokenRefresher {
        private var authService: AuthorizationService? = null
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Esito del login per la UI. */
        sealed interface AuthResult {
            data class Success(val username: String) : AuthResult

            data class Cancelled(val reason: String = "Login annullato") : AuthResult

            data class Error(val message: String) : AuthResult
        }

        fun isConfigured(): Boolean = HuggingFaceOAuthConfig.isConfigured()

        private fun service(): AuthorizationService =
            authService ?: AuthorizationService(context).also { authService = it }

        private fun authorizationRequest(): AuthorizationRequest =
            AuthorizationRequest.Builder(
                HuggingFaceOAuthConfig.serviceConfig,
                HuggingFaceOAuthConfig.clientId,
                ResponseTypeValues.CODE,
                Uri.parse(HuggingFaceOAuthConfig.REDIRECT_URI),
            )
                .setScope(HuggingFaceOAuthConfig.SCOPE)
                .setPrompt("consent")
                .build()

        /** Avvia il login aprendo la Custom Tab; il risultato arriva al [launcher] registrato. */
        fun startAuthFlow(
            activity: Activity,
            launcher: ActivityResultLauncher<Intent>,
        ) {
            check(isConfigured()) { "OAuth HuggingFace non configurato (Client ID assente)" }
            val intent = service().getAuthorizationRequestIntent(authorizationRequest())
            Log.i(TAG, "Avvio OAuth, redirect: ${HuggingFaceOAuthConfig.REDIRECT_URI}")
            launcher.launch(intent)
        }

        /** Gestisce il callback OAuth: scambia il codice con i token, poi recupera lo username. */
        fun handleAuthResult(
            data: Intent?,
            onResult: (AuthResult) -> Unit,
        ) {
            if (data == null) {
                onResult(AuthResult.Cancelled("Nessun dato di risposta"))
                return
            }
            val response = AuthorizationResponse.fromIntent(data)
            val exception = AuthorizationException.fromIntent(data)
            when {
                response != null -> exchangeCode(response, onResult)
                exception != null -> {
                    val desc = exception.errorDescription
                    if (desc?.contains("cancel", ignoreCase = true) == true) {
                        onResult(AuthResult.Cancelled(desc))
                    } else {
                        onResult(AuthResult.Error(desc ?: "Autorizzazione fallita"))
                    }
                }
                else -> onResult(AuthResult.Error("Risposta di autorizzazione non valida"))
            }
        }

        private fun exchangeCode(
            response: AuthorizationResponse,
            onResult: (AuthResult) -> Unit,
        ) {
            service().performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, ex ->
                val accessToken = tokenResponse?.accessToken
                if (accessToken == null) {
                    onResult(AuthResult.Error(ex?.errorDescription ?: "Scambio token fallito"))
                    return@performTokenRequest
                }
                val expiresAt = tokenResponse.accessTokenExpirationTime ?: (System.currentTimeMillis() + 60 * 60 * 1000L)
                // Salva subito i token (persistono anche se il fetch dello username fallisce).
                tokenManager.saveTokens(accessToken, tokenResponse.refreshToken, expiresAt, username = "")
                scope.launch {
                    when (val r = apiClient.validateToken(accessToken)) {
                        is HuggingFaceApiClient.ValidationResult.Success -> {
                            tokenManager.saveUsername(r.username)
                            onResult(AuthResult.Success(r.username))
                        }
                        is HuggingFaceApiClient.ValidationResult.Error -> {
                            // Login comunque riuscito, username sconosciuto.
                            tokenManager.saveUsername("utente HF")
                            onResult(AuthResult.Success("utente HF"))
                        }
                    }
                }
            }
        }

        override suspend fun refresh(refreshToken: String): RefreshResult =
            suspendCancellableCoroutine { cont ->
                val request =
                    TokenRequest.Builder(HuggingFaceOAuthConfig.serviceConfig, HuggingFaceOAuthConfig.clientId)
                        .setGrantType("refresh_token")
                        .setRefreshToken(refreshToken)
                        .build()
                service().performTokenRequest(request) { tokenResponse, ex ->
                    val access = tokenResponse?.accessToken
                    val expiresAt = tokenResponse?.accessTokenExpirationTime
                    if (access != null && expiresAt != null) {
                        cont.resume(RefreshResult.Success(access, tokenResponse.refreshToken, expiresAt))
                    } else {
                        cont.resume(RefreshResult.Failure(ex?.errorDescription ?: "Rinnovo token fallito"))
                    }
                }
            }

        fun dispose() {
            authService?.dispose()
            authService = null
        }

        companion object {
            private const val TAG = "HfAuthManager"
        }
    }
