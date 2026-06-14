package it.netseven.raglocale.huggingface

import javax.inject.Inject
import javax.inject.Singleton

/** Esito di un rinnovo del token (refresh_token grant). */
sealed interface RefreshResult {
    data class Success(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAtMs: Long,
    ) : RefreshResult

    data class Failure(val message: String) : RefreshResult
}

/**
 * Seam del rinnovo del token: separa l'orchestrazione (pura, in [HuggingFaceTokenProvider]) dalla
 * rete (AppAuth, in [HuggingFaceAuthManager]). Permette di testare il refresh in JVM con un fake.
 */
interface TokenRefresher {
    suspend fun refresh(refreshToken: String): RefreshResult
}

/**
 * Fornisce il **token effettivo valido** per i download gated: legge il token persistito e, se è
 * scaduto o vicino alla scadenza, lo rinnova tramite il [TokenRefresher] aggiornando il
 * [HuggingFaceTokenManager]. Clock iniettabile per test deterministici.
 *
 * Ritorna null quando l'utente non è loggato o il rinnovo fallisce su un token già scaduto: in
 * quel caso il download gated va guidato verso un nuovo login.
 */
@Singleton
class HuggingFaceTokenProvider(
    private val tokenManager: HuggingFaceTokenManager,
    private val refresher: TokenRefresher,
    private val now: () -> Long,
) {
    // Costruttore usato da Hilt: clock reale. Quello primario (con clock iniettabile) serve ai test.
    @Inject
    constructor(
        tokenManager: HuggingFaceTokenManager,
        refresher: TokenRefresher,
    ) : this(tokenManager, refresher, System::currentTimeMillis)

    suspend fun getEffectiveToken(): String? {
        if (!tokenManager.isLoggedIn()) return null
        val t = now()
        if (!tokenManager.needsRefresh(t)) return tokenManager.accessToken()

        val refresh = tokenManager.refreshToken()
        if (refresh.isNullOrBlank()) {
            // Niente refresh token: il token vale finché non è davvero scaduto.
            return if (tokenManager.isExpired(t)) null else tokenManager.accessToken()
        }
        return when (val r = refresher.refresh(refresh)) {
            is RefreshResult.Success -> {
                tokenManager.updateAccessToken(r.accessToken, r.expiresAtMs)
                r.accessToken
            }
            is RefreshResult.Failure ->
                if (tokenManager.isExpired(t)) null else tokenManager.accessToken()
        }
    }
}
