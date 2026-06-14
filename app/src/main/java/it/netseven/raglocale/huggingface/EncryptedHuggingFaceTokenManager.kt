package it.netseven.raglocale.huggingface

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistenza **cifrata** dei token HuggingFace ([EncryptedSharedPreferences], AES256). Sopravvive
 * ai riavvii (lo stato loggato viene ripristinato in [restore]) e gestisce la corruzione del
 * KeyStore ricreando le preferenze (porting da anti-vocale, semplificato al solo flusso OAuth).
 */
@Singleton
class EncryptedHuggingFaceTokenManager
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : HuggingFaceTokenManager {
        private val prefs: SharedPreferences = createEncryptedPrefs(context)

        private val _state = MutableStateFlow<HfAuthState>(HfAuthState.LoggedOut)
        override val state: StateFlow<HfAuthState> = _state.asStateFlow()

        init {
            restore()
        }

        /** Ripristina lo stato loggato dai token cifrati (chiamato all'avvio). */
        private fun restore() {
            val access = accessToken()
            val user = username()
            _state.value = if (!access.isNullOrEmpty() && !user.isNullOrEmpty()) HfAuthState.LoggedIn(user) else HfAuthState.LoggedOut
        }

        override fun isLoggedIn(): Boolean = !accessToken().isNullOrEmpty()

        override fun username(): String? = prefs.getString(KEY_USERNAME, null)

        override fun accessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

        override fun refreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

        override fun expiresAtMs(): Long? = prefs.getLong(KEY_EXPIRES_AT, 0L).takeIf { it > 0L }

        override fun saveTokens(
            accessToken: String,
            refreshToken: String?,
            expiresAtMs: Long,
            username: String,
        ) {
            prefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken ?: "")
                .putLong(KEY_EXPIRES_AT, expiresAtMs)
                .putString(KEY_USERNAME, username)
                .apply()
            _state.value = if (username.isNotEmpty()) HfAuthState.LoggedIn(username) else _state.value
        }

        override fun updateAccessToken(
            accessToken: String,
            expiresAtMs: Long,
        ) {
            prefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putLong(KEY_EXPIRES_AT, expiresAtMs)
                .apply()
        }

        override fun saveUsername(username: String) {
            prefs.edit().putString(KEY_USERNAME, username).apply()
            if (username.isNotEmpty() && !accessToken().isNullOrEmpty()) {
                _state.value = HfAuthState.LoggedIn(username)
            }
        }

        override fun logout() {
            prefs.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_EXPIRES_AT)
                .remove(KEY_USERNAME)
                .apply()
            _state.value = HfAuthState.LoggedOut
        }

        private fun createEncryptedPrefs(context: Context): SharedPreferences =
            try {
                buildEncryptedPrefs(context)
            } catch (e: Exception) {
                Log.e(TAG, "Errore KeyStore in init, recupero: ${e.message}", e)
                deleteCorruptedPrefs(context)
                try {
                    buildEncryptedPrefs(context)
                } catch (e2: Exception) {
                    Log.e(TAG, "KeyStore ancora rotto, fallback a prefs in chiaro: ${e2.message}", e2)
                    context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
                }
            }

        private fun buildEncryptedPrefs(context: Context): SharedPreferences {
            val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            return EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        private fun deleteCorruptedPrefs(context: Context) {
            File(context.applicationInfo.dataDir, "shared_prefs")
                .listFiles()
                ?.filter { it.name.startsWith(PREFS_FILE) }
                ?.forEach { it.delete() }
            try {
                val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            } catch (e: Exception) {
                Log.w(TAG, "MasterKey non eliminata: ${e.message}")
            }
        }

        companion object {
            private const val TAG = "HfTokenManager"
            private const val PREFS_FILE = "huggingface_encrypted_prefs"
            private const val KEY_ACCESS_TOKEN = "hf_access_token"
            private const val KEY_REFRESH_TOKEN = "hf_refresh_token"
            private const val KEY_EXPIRES_AT = "hf_token_expires_at"
            private const val KEY_USERNAME = "hf_username"
        }
    }
