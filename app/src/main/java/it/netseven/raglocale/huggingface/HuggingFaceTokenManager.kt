package it.netseven.raglocale.huggingface

import kotlinx.coroutines.flow.StateFlow

/** Stato del login HuggingFace per la UI (il "non configurato" è una condizione di config, non di token). */
sealed interface HfAuthState {
    data object LoggedOut : HfAuthState

    data class LoggedIn(val username: String) : HfAuthState
}

/**
 * Seam (interfaccia) di persistenza e stato dei token HuggingFace, unit-testabile in JVM con un
 * fake. NON conosce la rete: il refresh effettivo è orchestrato da [HuggingFaceTokenProvider]
 * usando questa interfaccia + un [TokenRefresher]. L'impl reale ([EncryptedHuggingFaceTokenManager])
 * persiste i token in modo cifrato.
 */
interface HuggingFaceTokenManager {
    val state: StateFlow<HfAuthState>

    fun isLoggedIn(): Boolean

    fun username(): String?

    fun accessToken(): String?

    fun refreshToken(): String?

    fun expiresAtMs(): Long?

    fun saveTokens(
        accessToken: String,
        refreshToken: String?,
        expiresAtMs: Long,
        username: String,
    )

    fun updateAccessToken(
        accessToken: String,
        expiresAtMs: Long,
    )

    fun saveUsername(username: String)

    fun logout()

    /** Il token è scaduto rispetto a [nowMs]? Logica pura sulla scadenza memorizzata ([expiresAtMs]). */
    fun isExpired(nowMs: Long): Boolean {
        val exp = expiresAtMs() ?: return false
        return nowMs >= exp
    }

    /** Va rinfrescato entro il [bufferMs] prima della scadenza rispetto a [nowMs]? Logica pura. */
    fun needsRefresh(
        nowMs: Long,
        bufferMs: Long = DEFAULT_REFRESH_BUFFER_MS,
    ): Boolean {
        val exp = expiresAtMs() ?: return false
        return nowMs >= exp - bufferMs
    }

    companion object {
        /** Margine di rinnovo: si rinfresca 5 minuti prima della scadenza effettiva. */
        const val DEFAULT_REFRESH_BUFFER_MS: Long = 5 * 60 * 1000L
    }
}
