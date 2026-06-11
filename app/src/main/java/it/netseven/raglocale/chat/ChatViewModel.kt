package it.netseven.raglocale.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.netseven.raglocale.data.PreferencesRepository
import it.netseven.raglocale.inference.ConversationRequest
import it.netseven.raglocale.inference.EngineState
import it.netseven.raglocale.inference.InferenceEngine
import it.netseven.raglocale.modelmanager.ModelRepository
import it.netseven.raglocale.retrieval.RetrievalIndisponibile
import it.netseven.raglocale.retrieval.RicercaDocumenti
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
 * Orchestrazione della chat sopra [InferenceEngine], in due modalità:
 * - **chat semplice** (M1): cronologia di sessione, prompt diretto;
 * - **modalità RAG** (task 6.1): la domanda passa per il retrieval ([RicercaDocumenti]),
 *   diventa un prompt grounded ([RagPromptBuilder]) e la risposta cita i chunk usati.
 *
 * Streaming, cap di output e stop manuale che mantiene il prodotto sono condivisi dalle due
 * modalità ([runStreaming]) e restano invariati rispetto a M1 (requisito 6.1).
 */
@HiltViewModel
class ChatViewModel
    @Inject
    constructor(
        private val engine: InferenceEngine,
        private val prefs: PreferencesRepository,
        private val modelRepository: ModelRepository,
        private val ricerca: RicercaDocumenti,
    ) : ViewModel() {
        val engineState: StateFlow<EngineState> = engine.state

        private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
        val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

        private val _isGenerating = MutableStateFlow(false)
        val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        /** Modalità RAG attiva: di default sì (è il senso dell'app); disattivabile per il confronto. */
        private val _ragEnabled = MutableStateFlow(true)
        val ragEnabled: StateFlow<Boolean> = _ragEnabled.asStateFlow()

        private var generationJob: Job? = null

        /** Indice del messaggio del modello del turno corrente (per attaccarvi la traccia di retrieval). */
        private var currentModelIndex: Int = -1

        fun setRagEnabled(enabled: Boolean) {
            _ragEnabled.value = enabled
        }

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
            _messages.value = history +
                ChatMessage(Role.USER, trimmed) +
                ChatMessage(Role.MODEL, "", streaming = true)
            currentModelIndex = _messages.value.lastIndex
            _isGenerating.value = true
            _error.value = null

            generationJob =
                viewModelScope.launch {
                    try {
                        val request =
                            if (_ragEnabled.value) {
                                buildRagRequest(trimmed) ?: return@launch
                            } else {
                                ChatContextBuilder.buildRequest(history, trimmed)
                            }
                        val risposta = runStreaming(request)
                        if (risposta != null && _ragEnabled.value) aggiornaCitazioni(risposta)
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

        /**
         * Retrieval → prompt grounded. Ritorna `null` (e segnala il caso) se il retrieval non è
         * possibile: la trasparenza del pannello (chunk + score) viene impostata prima della
         * generazione, così resta visibile anche se la risposta fallisce (spec 6.2).
         */
        private suspend fun buildRagRequest(domanda: String): ConversationRequest? {
            val topK = prefs.topK.first()
            val chunks =
                try {
                    ricerca.cerca(domanda, topK)
                } catch (e: RetrievalIndisponibile) {
                    _error.value = e.messaggio
                    finishStreaming("[${e.messaggio}]")
                    return null
                }
            setRetrievalTrace(RetrievalTrace(chunks = chunks))
            return ConversationRequest(
                systemInstruction = RAG_SYSTEM_INSTRUCTION,
                initialMessages = emptyList(),
                userMessage = RagPromptBuilder.costruisci(domanda, chunks),
            )
        }

        /**
         * Genera in streaming gestendo cap, timeout, output corrotto e stop. Aggiorna il
         * messaggio del modello man mano; ritorna il testo finale (anche parziale, al cap o
         * allo stop) o `null` se la generazione è fallita.
         */
        private suspend fun runStreaming(request: ConversationRequest): String? {
            val builder = StringBuilder()
            return try {
                val path = modelRepository.activeModelFile()?.absolutePath
                if (path == null) {
                    _error.value = NO_MODEL_MESSAGE
                    finishStreaming("[Nessun modello attivo]")
                    return null
                }
                engine.setKeepAliveMinutes(prefs.keepAliveMinutes.first())
                engine.load(path, prefs.backend.first())

                val cap = prefs.maxOutputTokens.first()
                withTimeout(GENERATION_TIMEOUT_MS) {
                    engine.generate(request).collect { chunk ->
                        builder.append(chunk)
                        if (OutputSanity.looksCorrupt(builder.toString())) throw CorruptOutputException
                        updateStreaming(builder.toString())
                        if (OutputBudget.reachedCap(builder.toString(), cap)) throw CapReachedException
                    }
                }
                finishStreaming(builder.toString())
                builder.toString()
            } catch (e: CapReachedException) {
                finishStreamingKeepCurrent()
                builder.toString()
            } catch (e: CorruptOutputException) {
                engine.unload()
                _error.value = CORRUPT_OUTPUT_MESSAGE
                finishStreaming("[$CORRUPT_OUTPUT_MESSAGE]")
                null
            } catch (e: TimeoutCancellationException) {
                engine.unload()
                _error.value = GENERATION_TIMEOUT_MESSAGE
                finishStreaming("[$GENERATION_TIMEOUT_MESSAGE]")
                null
            } catch (e: CancellationException) {
                finishStreamingKeepCurrent()
                throw e
            } catch (e: Exception) {
                _error.value = e.message
                finishStreaming("[Errore: ${e.message}]")
                null
            }
        }

        private fun aggiornaCitazioni(risposta: String) {
            val trace = _messages.value.getOrNull(currentModelIndex)?.retrieval ?: return
            val citati = RagPromptBuilder.estraiCitazioni(risposta, trace.chunks.size)
            setRetrievalTrace(trace.copy(citati = citati))
        }

        private fun setRetrievalTrace(trace: RetrievalTrace) {
            val idx = currentModelIndex
            val list = _messages.value.toMutableList()
            if (idx in list.indices && list[idx].role == Role.MODEL) {
                list[idx] = list[idx].copy(retrieval = trace)
                _messages.value = list
            }
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
            private const val RAG_SYSTEM_INSTRUCTION =
                "Rispondi SOLO usando le informazioni del contesto fornito. Se la risposta non è nel contesto, " +
                    "dichiara che l'informazione non è presente nel documento e non inventare nulla. " +
                    "Cita i passaggi usati nel formato [n]. Rispondi in italiano, in modo conciso."
        }
    }
