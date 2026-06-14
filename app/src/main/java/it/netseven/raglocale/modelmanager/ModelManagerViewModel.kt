package it.netseven.raglocale.modelmanager

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.netseven.raglocale.huggingface.HfAuthState
import it.netseven.raglocale.huggingface.HuggingFaceAuthManager
import it.netseven.raglocale.huggingface.HuggingFaceTokenManager
import it.netseven.raglocale.huggingface.HuggingFaceTokenProvider
import it.netseven.raglocale.modelmanager.download.DownloadState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Riga del Model manager: modello + stato + se attivo + check storage + stato di download/import
 * corrente + presenza di un parziale. Lo [stato] della card è derivato in modo puro.
 */
data class ModelRow(
    val model: ModelInfo,
    val status: ModelStatus,
    val isActive: Boolean,
    val storage: StorageChecker.Result,
    val download: DownloadState = DownloadState.Idle,
    val hasPartial: Boolean = false,
) {
    val stato: CardStato
        get() = ModelCardState.stato(status, download.isActive, hasPartial, isActive)
}

@HiltViewModel
class ModelManagerViewModel
    @Inject
    constructor(
        private val repository: ModelRepository,
        private val authManager: HuggingFaceAuthManager,
        private val tokenManager: HuggingFaceTokenManager,
        private val tokenProvider: HuggingFaceTokenProvider,
    ) : ViewModel() {
        private val baseRows = MutableStateFlow<List<ModelRow>>(emptyList())

        /** Stato di download/import per id modello (sovrapposto alle righe base). */
        private val downloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())

        /** Id dei download di cui è stato richiesto l'annullamento (flag cooperativo). */
        private val cancelledIds = MutableStateFlow<Set<String>>(emptySet())
        private val jobs = mutableMapOf<String, Job>()

        val rows: StateFlow<List<ModelRow>> =
            combine(baseRows, downloads) { base, dl ->
                base.map { it.copy(download = dl[it.model.id] ?: DownloadState.Idle) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        /** Stato del login HuggingFace (loggato/non loggato). */
        val hfState: StateFlow<HfAuthState> = tokenManager.state

        /** True se l'OAuth app HF è configurata (altrimenti il login è disattivato — degrado con grazia). */
        val loginConfigured: Boolean = authManager.isConfigured()

        private val _message = MutableStateFlow<String?>(null)
        val message: StateFlow<String?> = _message.asStateFlow()

        init {
            refresh()
        }

        fun refresh() {
            viewModelScope.launch {
                val activeLlmId = repository.activeModelId() ?: ModelCatalog.defaultFor(ModelType.LLM).id
                val activeEmbedderId = repository.activeEmbedderId()
                baseRows.value =
                    ModelCatalog.models.map { model ->
                        val activeIdForType = if (model.type == ModelType.LLM) activeLlmId else activeEmbedderId
                        ModelRow(
                            model = model,
                            status = repository.statusFor(model),
                            isActive = model.id == activeIdForType,
                            storage = repository.storageCheck(model),
                            hasPartial = repository.hasPartial(model),
                        )
                    }
            }
        }

        /** Avvia (o riprende dal `.tmp`) il download del modello. */
        fun download(model: ModelInfo) {
            if (jobs[model.id]?.isActive == true) return
            cancelledIds.value = cancelledIds.value - model.id
            val job =
                viewModelScope.launch {
                    setDownloadState(model.id, DownloadState.Connecting(model.displayName))
                    val esito =
                        repository.download(
                            model = model,
                            getToken = { tokenProvider.getEffectiveToken() },
                            onState = { state -> setDownloadState(model.id, state) },
                            isCancelled = { model.id in cancelledIds.value },
                        )
                    esito
                        .onSuccess { _message.value = "Scaricato: ${model.displayName}" }
                        .onFailure { _message.value = "Download fallito (${model.displayName}): ${it.message}" }
                    clearDownloadState(model.id)
                    cancelledIds.value = cancelledIds.value - model.id
                    refresh()
                }
            jobs[model.id] = job
        }

        /** Annulla un download in corso (il `.tmp` resta per la ripresa). */
        fun cancelDownload(model: ModelInfo) {
            cancelledIds.value = cancelledIds.value + model.id
        }

        /** Cancella un parziale (`.tmp` + sidecar) e riporta il modello ad "assente". */
        fun clearPartial(model: ModelInfo) {
            viewModelScope.launch {
                repository.clearPartial(model)
                _message.value = "Parziale eliminato: ${model.displayName}"
                refresh()
            }
        }

        fun import(
            model: ModelInfo,
            uri: Uri,
            target: ImportTarget,
        ) {
            viewModelScope.launch {
                setDownloadState(model.id, DownloadState.Connecting(target.fileName))
                repository
                    .importFromUri(uri, target) { copied, total ->
                        val pct = if (total > 0) (copied.toFloat() / total).coerceIn(0f, 1f) else 0f
                        setDownloadState(model.id, DownloadState.Downloading(copied, total, pct * 100))
                    }
                    .onSuccess { _message.value = "Importato: ${target.fileName}" }
                    .onFailure { _message.value = "Import fallito (${target.etichetta}): ${it.message}" }
                clearDownloadState(model.id)
                refresh()
            }
        }

        fun setActive(model: ModelInfo) {
            viewModelScope.launch {
                repository.setActive(model)
                refresh()
            }
        }

        fun remove(model: ModelInfo) {
            viewModelScope.launch {
                if (repository.remove(model)) _message.value = "Modello rimosso: ${model.displayName}"
                refresh()
            }
        }

        // --- Login HuggingFace ---

        /** Avvia il login HF aprendo la Custom Tab; il risultato va a [onLoginResult]. */
        fun login(
            activity: Activity,
            launcher: ActivityResultLauncher<Intent>,
        ) {
            if (!loginConfigured) {
                _message.value = "Login HuggingFace non configurato (manca il Client ID)."
                return
            }
            runCatching { authManager.startAuthFlow(activity, launcher) }
                .onFailure { _message.value = "Impossibile avviare il login: ${it.message}" }
        }

        fun onLoginResult(data: Intent?) {
            authManager.handleAuthResult(data) { result ->
                _message.value =
                    when (result) {
                        is HuggingFaceAuthManager.AuthResult.Success -> "Accesso HuggingFace: ${result.username}"
                        is HuggingFaceAuthManager.AuthResult.Cancelled -> "Login annullato"
                        is HuggingFaceAuthManager.AuthResult.Error -> "Login fallito: ${result.message}"
                    }
                refresh()
            }
        }

        fun logout() {
            tokenManager.logout()
            _message.value = "Disconnesso da HuggingFace"
            refresh()
        }

        fun clearMessage() {
            _message.value = null
        }

        private fun setDownloadState(
            id: String,
            state: DownloadState,
        ) {
            downloads.value = downloads.value + (id to state)
        }

        private fun clearDownloadState(id: String) {
            downloads.value = downloads.value - id
        }
    }
