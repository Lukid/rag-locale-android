package it.netseven.raglocale.huggingface

import android.net.Uri
import it.netseven.raglocale.BuildConfig
import net.openid.appauth.AuthorizationServiceConfiguration

/**
 * Configurazione OAuth 2.0 (Authorization Code + PKCE) per il login a HuggingFace, usata da
 * AppAuth. Porting da anti-vocale, con il **Client ID letto da `BuildConfig`** (da
 * `local.properties`, non committato — vedi design D5 e `.env.example`).
 *
 * ## Setup (una volta)
 * 1. https://huggingface.co/settings/oauth/apps → New OAuth Application
 * 2. Redirect URI: `it.netseven.raglocale://oauth2callback`; scope `read-repos`
 * 3. Copiare il Client ID in `local.properties`: `hfOauthClientId=...`
 *
 * Se il Client ID manca, [isConfigured] è falso: il login è disattivato ma i modelli pubblici
 * restano scaricabili (degrado con grazia).
 */
object HuggingFaceOAuthConfig {
    /** Client ID dell'OAuth app HF (da `BuildConfig.HF_OAUTH_CLIENT_ID`, vuoto se non configurato). */
    val clientId: String = BuildConfig.HF_OAUTH_CLIENT_ID

    /** Deve combaciare con `manifestPlaceholders["appAuthRedirectScheme"]` in build.gradle.kts. */
    const val REDIRECT_URI = "it.netseven.raglocale://oauth2callback"

    /** Scope: lettura dei repository (necessario per scaricare i modelli gated). */
    const val SCOPE = "read-repos"

    private const val AUTH_ENDPOINT = "https://huggingface.co/oauth/authorize"
    private const val TOKEN_ENDPOINT = "https://huggingface.co/oauth/token"

    /** Endpoint userinfo OAuth: ricava lo username dall'access token. */
    const val USERINFO_ENDPOINT = "https://huggingface.co/oauth/userinfo"

    val serviceConfig: AuthorizationServiceConfiguration by lazy {
        AuthorizationServiceConfiguration(Uri.parse(AUTH_ENDPOINT), Uri.parse(TOKEN_ENDPOINT))
    }

    /** True se il Client ID è valorizzato: senza, il login HF non è disponibile. */
    fun isConfigured(): Boolean = clientId.isNotBlank()
}
