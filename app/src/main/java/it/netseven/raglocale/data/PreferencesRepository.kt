package it.netseven.raglocale.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import it.netseven.raglocale.inference.Backend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "raglocale_prefs")

/** Persistenza delle preferenze utente (backend, modello attivo, cap token, keep-alive). */
@Singleton
class PreferencesRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private object Keys {
            val BACKEND = stringPreferencesKey("backend")
            val ACTIVE_MODEL_ID = stringPreferencesKey("active_model_id")
            val ACTIVE_EMBEDDER_ID = stringPreferencesKey("active_embedder_id")
            val MAX_OUTPUT_TOKENS = intPreferencesKey("max_output_tokens")
            val KEEP_ALIVE_MINUTES = intPreferencesKey("keep_alive_minutes")
        }

        val backend: Flow<Backend> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.BACKEND]?.let { runCatching { Backend.valueOf(it) }.getOrNull() } ?: DEFAULT_BACKEND
            }
        val activeModelId: Flow<String?> = context.dataStore.data.map { it[Keys.ACTIVE_MODEL_ID] }
        val activeEmbedderId: Flow<String?> = context.dataStore.data.map { it[Keys.ACTIVE_EMBEDDER_ID] }
        val maxOutputTokens: Flow<Int> = context.dataStore.data.map { it[Keys.MAX_OUTPUT_TOKENS] ?: DEFAULT_MAX_OUTPUT_TOKENS }
        val keepAliveMinutes: Flow<Int> = context.dataStore.data.map { it[Keys.KEEP_ALIVE_MINUTES] ?: DEFAULT_KEEP_ALIVE_MINUTES }

        suspend fun setBackend(value: Backend) {
            context.dataStore.edit { it[Keys.BACKEND] = value.name }
        }

        suspend fun setActiveModelId(id: String) {
            context.dataStore.edit { it[Keys.ACTIVE_MODEL_ID] = id }
        }

        suspend fun setActiveEmbedderId(id: String) {
            context.dataStore.edit { it[Keys.ACTIVE_EMBEDDER_ID] = id }
        }

        suspend fun setMaxOutputTokens(value: Int) {
            context.dataStore.edit { it[Keys.MAX_OUTPUT_TOKENS] = value }
        }

        suspend fun setKeepAliveMinutes(value: Int) {
            context.dataStore.edit { it[Keys.KEEP_ALIVE_MINUTES] = value }
        }

        companion object {
            val DEFAULT_BACKEND = Backend.GPU
            const val DEFAULT_MAX_OUTPUT_TOKENS = 200
            const val DEFAULT_KEEP_ALIVE_MINUTES = 5
        }
    }
