package it.netseven.raglocale.huggingface

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Fake in-memory del seam di persistenza dei token, per i test JVM (niente cifratura/Android). */
class FakeHuggingFaceTokenManager : HuggingFaceTokenManager {
    private val _state = MutableStateFlow<HfAuthState>(HfAuthState.LoggedOut)
    override val state: StateFlow<HfAuthState> = _state.asStateFlow()

    private var access: String? = null
    private var refresh: String? = null
    private var expiresAt: Long? = null
    private var user: String? = null

    override fun isLoggedIn(): Boolean = !access.isNullOrEmpty()

    override fun username(): String? = user

    override fun accessToken(): String? = access

    override fun refreshToken(): String? = refresh

    override fun expiresAtMs(): Long? = expiresAt

    override fun saveTokens(
        accessToken: String,
        refreshToken: String?,
        expiresAtMs: Long,
        username: String,
    ) {
        access = accessToken
        refresh = refreshToken
        expiresAt = expiresAtMs
        user = username
        if (username.isNotEmpty()) _state.value = HfAuthState.LoggedIn(username)
    }

    override fun updateAccessToken(
        accessToken: String,
        expiresAtMs: Long,
    ) {
        access = accessToken
        expiresAt = expiresAtMs
    }

    override fun saveUsername(username: String) {
        user = username
        if (username.isNotEmpty() && !access.isNullOrEmpty()) _state.value = HfAuthState.LoggedIn(username)
    }

    override fun logout() {
        access = null
        refresh = null
        expiresAt = null
        user = null
        _state.value = HfAuthState.LoggedOut
    }
}
