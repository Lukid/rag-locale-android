package it.netseven.raglocale.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.netseven.raglocale.data.PreferencesRepository
import it.netseven.raglocale.inference.EngineState
import it.netseven.raglocale.inference.InferenceEngine
import it.netseven.raglocale.modelmanager.ModelRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * Orchestrazione della chat solo-testo sopra [InferenceEngine].
 * Streaming token-per-token (4.1), cronologia di sessione (4.2), cap output (4.3),
 * stop manuale mantenendo il prodotto (4.4).
 */
@HiltViewModel
class ChatViewModel
    @Inject
    constructor(
        private val engine: InferenceEngine,
        private val prefs: PreferencesRepository,
        private val modelRepository: ModelRepository,
    ) : ViewModel() {
        val engineState: StateFlow<EngineState> = engine.state

        private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
        val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

        private val _isGenerating = MutableStateFlow(false)
        val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        private var generationJob: Job? = null

        /** Pre-carica (residente) il modello attivo: utile per mostrare subito lo spinner. */
        fun loadActiveModel() {
            viewModelScope.launch {
                val path = modelRepository.activeModelFile()?.absolutePath
                if (path == null) {
                    _error.value = NO_MODEL_MESSAGE
                    return@launch
                }
                engine.setKeepAliveMinutes(prefs.keepAliveMinutes.first())
                engine.load(path, prefs.backend.first())
            }
        }

        fun sendMessage(text: String) {
            val trimmed = text.trim()
            if (trimmed.isEmpty() || _isGenerating.value) return

            val history = _messages.value
            val request = ChatContextBuilder.buildRequest(history, trimmed)

            _messages.value = history +
                ChatMessage(Role.USER, trimmed) +
                ChatMessage(Role.MODEL, "", streaming = true)
            _isGenerating.value = true
            _error.value = null

            generationJob =
                viewModelScope.launch {
                    val builder = StringBuilder()
                    try {
                        val path = modelRepository.activeModelFile()?.absolutePath
                        if (path == null) {
                            _error.value = NO_MODEL_MESSAGE
                            finishStreaming("[Nessun modello attivo]")
                            return@launch
                        }
                        engine.setKeepAliveMinutes(prefs.keepAliveMinutes.first())
                        engine.load(path, prefs.backend.first())

                        val cap = prefs.maxOutputTokens.first()
                        withTimeout(GENERATION_TIMEOUT_MS) {
                            engine.generate(request).collect { chunk ->
                                builder.append(chunk)
                                if (OutputSanity.looksCorrupt(builder.toString())) {
                                    throw CorruptOutputException
                                }
                                updateStreaming(builder.toString())
                                if (OutputBudget.reachedCap(builder.toString(), cap)) {
                                    // cap raggiunto: interruzione pulita mantenendo il prodotto (4.3)
                                    throw CapReachedException
                                }
                            }
                        }
                        finishStreaming(builder.toString())
                    } catch (e: CapReachedException) {
                        finishStreamingKeepCurrent()
                    } catch (e: CorruptOutputException) {
                        engine.unload()
                        _error.value = CORRUPT_OUTPUT_MESSAGE
                        finishStreaming("[$CORRUPT_OUTPUT_MESSAGE]")
                    } catch (e: TimeoutCancellationException) {
                        engine.unload()
                        _error.value = GENERATION_TIMEOUT_MESSAGE
                        finishStreaming("[$GENERATION_TIMEOUT_MESSAGE]")
                    } catch (e: CancellationException) {
                        finishStreamingKeepCurrent()
                        throw e
                    } catch (e: Exception) {
                        _error.value = e.message
                        finishStreaming("[Errore: ${e.message}]")
                    } finally {
                        _isGenerating.value = false
                    }
                }
        }

        /** Interruzione manuale: ferma la generazione mantenendo il testo già prodotto (4.4). */
        fun stop() {
            generationJob?.cancel()
            generationJob = null
            finishStreamingKeepCurrent()
            _isGenerating.value = false
        }

        fun clearError() {
            _error.value = null
        }

        private fun updateStreaming(textSoFar: String) {
            mutateStreamingMessage { it.copy(text = textSoFar, streaming = true) }
        }

        private fun finishStreaming(finalText: String) {
            mutateStreamingMessage { it.copy(text = finalText, streaming = false) }
        }

        private fun finishStreamingKeepCurrent() {
            mutateStreamingMessage { it.copy(streaming = false) }
        }

        private fun mutateStreamingMessage(transform: (ChatMessage) -> ChatMessage) {
            val list = _messages.value.toMutableList()
            val idx = list.indexOfLast { it.role == Role.MODEL && it.streaming }
            if (idx >= 0) {
                list[idx] = transform(list[idx])
                _messages.value = list
            }
        }

        private object CapReachedException : Exception()

        private object CorruptOutputException : Exception()

        companion object {
            private const val NO_MODEL_MESSAGE = "Nessun modello attivo: importane/selezionane uno nel Model manager."
            private const val GENERATION_TIMEOUT_MS = 120_000L
            private const val CORRUPT_OUTPUT_MESSAGE =
                "Generazione interrotta: output non valido dal runtime locale. Il modello potrebbe essere incompatibile."
            private const val GENERATION_TIMEOUT_MESSAGE =
                "Generazione interrotta: il runtime locale non ha completato la risposta in tempo."
        }
    }
