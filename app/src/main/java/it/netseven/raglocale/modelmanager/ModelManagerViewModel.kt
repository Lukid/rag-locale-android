package it.netseven.raglocale.modelmanager

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Riga del Model manager: modello + stato + se attivo + esito del check storage. */
data class ModelRow(
    val model: ModelInfo,
    val status: ModelStatus,
    val isActive: Boolean,
    val storage: StorageChecker.Result,
)

@HiltViewModel
class ModelManagerViewModel
    @Inject
    constructor(
        private val repository: ModelRepository,
    ) : ViewModel() {
        private val _rows = MutableStateFlow<List<ModelRow>>(emptyList())
        val rows: StateFlow<List<ModelRow>> = _rows.asStateFlow()

        private val _message = MutableStateFlow<String?>(null)
        val message: StateFlow<String?> = _message.asStateFlow()

        init {
            refresh()
        }

        fun refresh() {
            viewModelScope.launch {
                val activeId = repository.activeModelId() ?: ModelCatalog.default.id
                _rows.value =
                    ModelCatalog.models.map { model ->
                        ModelRow(
                            model = model,
                            status = repository.statusFor(model),
                            isActive = model.id == activeId,
                            storage = repository.storageCheck(model),
                        )
                    }
            }
        }

        fun import(
            uri: Uri,
            model: ModelInfo,
        ) {
            viewModelScope.launch {
                repository.importFromUri(uri, model)
                    .onSuccess { _message.value = "Modello importato: ${model.displayName}" }
                    .onFailure { _message.value = "Import fallito: ${it.message}" }
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
                if (repository.remove(model)) {
                    _message.value = "Modello rimosso: ${model.displayName}"
                }
                refresh()
            }
        }

        fun clearMessage() {
            _message.value = null
        }
    }
