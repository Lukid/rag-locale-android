package it.netseven.raglocale.ingestion

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.netseven.raglocale.retrieval.FornitoreEmbedder
import it.netseven.raglocale.store.VectorStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject

/** Stato della schermata di ingestion (spec 6.3): progresso ed errori per caso. */
sealed interface StatoIngestion {
    data object Inattivo : StatoIngestion

    data object Estrazione : StatoIngestion

    data class Indicizzazione(
        val processati: Int,
        val totale: Int,
    ) : StatoIngestion

    data class Completato(
        val documento: String,
        val chunk: Int,
        val avviso: String?,
    ) : StatoIngestion

    data class Errore(
        val messaggio: String,
    ) : StatoIngestion
}

/**
 * Orchestrazione dell'ingestion lato UI (task 6.3): risolve la sorgente (file/PDF/URL) in un
 * [NormalizedText] e la indicizza con la [PipelineIngestion], riportando progresso ed errori.
 *
 * Modello a documento singolo (non-goal: KB multi-documento): ogni nuova ingestion svuota
 * l'indice prima di scrivere — così il nuovo documento sostituisce il precedente e l'indice
 * resta coerente con l'embedder attivo.
 */
@HiltViewModel
class IngestionViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val fornitore: FornitoreEmbedder,
        private val store: VectorStore,
    ) : ViewModel() {
        private val _stato = MutableStateFlow<StatoIngestion>(StatoIngestion.Inattivo)
        val stato: StateFlow<StatoIngestion> = _stato.asStateFlow()

        private val _indiceEmbedder = MutableStateFlow<String?>(null)
        val indiceEmbedder: StateFlow<String?> = _indiceEmbedder.asStateFlow()

        private val textSource = TextFileSource()
        private val pdfSource = PdfSource()
        private val urlSource = UrlSource()

        init {
            refreshIndice()
        }

        fun ingestTesto(uri: Uri) = ingestDaStream(uri) { input, nome -> textSource.estrai(input, nome) }

        fun ingestPdf(uri: Uri) = ingestDaStream(uri) { input, nome -> pdfSource.estrai(input, nome) }

        fun ingestUrl(url: String) {
            val pulito = url.trim()
            if (pulito.isEmpty()) return
            avvia { indicizza(urlSource.estrai(pulito)) }
        }

        private fun ingestDaStream(
            uri: Uri,
            estrai: (InputStream, String?) -> EsitoEstrazione,
        ) = avvia {
            val nome = nomeFile(uri)
            val esito =
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri).use { input ->
                        if (input == null) {
                            EsitoEstrazione.Errore(ErroreIngestion.LetturaFallita("file non apribile"))
                        } else {
                            estrai(input, nome)
                        }
                    }
                }
            indicizza(esito)
        }

        private fun avvia(blocco: suspend () -> Unit) {
            if (_stato.value is StatoIngestion.Estrazione || _stato.value is StatoIngestion.Indicizzazione) return
            viewModelScope.launch {
                _stato.value = StatoIngestion.Estrazione
                try {
                    blocco()
                } catch (e: Exception) {
                    _stato.value = StatoIngestion.Errore(e.message ?: "errore imprevisto durante l'ingestion")
                }
                refreshIndice()
            }
        }

        private suspend fun indicizza(esito: EsitoEstrazione) {
            when (esito) {
                is EsitoEstrazione.Errore -> _stato.value = StatoIngestion.Errore(esito.errore.messaggio)
                is EsitoEstrazione.Ok -> {
                    val embedder =
                        fornitore.embedder() ?: run {
                            _stato.value =
                                StatoIngestion.Errore("Nessun embedder attivo: importane/selezionane uno nella scheda Modelli.")
                            return
                        }
                    withContext(Dispatchers.IO) { store.svuota() }
                    val pipeline = PipelineIngestion(Chunker(), embedder, store)
                    val res =
                        withContext(Dispatchers.IO) {
                            pipeline.indicizza(esito.documento) { processati, totale ->
                                _stato.value = StatoIngestion.Indicizzazione(processati, totale)
                            }
                        }
                    _stato.value =
                        when (res) {
                            is EsitoIngestion.Completata ->
                                StatoIngestion.Completato(res.documento, res.chunkIndicizzati, esito.avviso)
                            is EsitoIngestion.Errore -> StatoIngestion.Errore(res.errore.messaggio)
                        }
                }
            }
        }

        private fun refreshIndice() {
            viewModelScope.launch {
                _indiceEmbedder.value = withContext(Dispatchers.IO) { store.embedderIndice() }
            }
        }

        private fun nomeFile(uri: Uri): String? =
            runCatching {
                context.contentResolver
                    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursore -> if (cursore.moveToFirst()) cursore.getString(0) else null }
            }.getOrNull() ?: uri.lastPathSegment
    }
