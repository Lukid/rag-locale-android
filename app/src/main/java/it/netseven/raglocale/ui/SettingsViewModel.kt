package it.netseven.raglocale.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.netseven.raglocale.data.PreferencesRepository
import it.netseven.raglocale.inference.Backend
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val prefs: PreferencesRepository,
    ) : ViewModel() {
        val backend: StateFlow<Backend> =
            prefs.backend.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreferencesRepository.DEFAULT_BACKEND)

        val maxOutputTokens: StateFlow<Int> =
            prefs.maxOutputTokens.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                PreferencesRepository.DEFAULT_MAX_OUTPUT_TOKENS,
            )

        val keepAliveMinutes: StateFlow<Int> =
            prefs.keepAliveMinutes.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                PreferencesRepository.DEFAULT_KEEP_ALIVE_MINUTES,
            )

        fun setBackend(value: Backend) = viewModelScope.launch { prefs.setBackend(value) }.let { }

        fun setMaxOutputTokens(value: Int) = viewModelScope.launch { prefs.setMaxOutputTokens(value) }.let { }

        fun setKeepAliveMinutes(value: Int) = viewModelScope.launch { prefs.setKeepAliveMinutes(value) }.let { }
    }
