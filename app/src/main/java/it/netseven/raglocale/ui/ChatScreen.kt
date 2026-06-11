package it.netseven.raglocale.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.netseven.raglocale.chat.ChatMessage
import it.netseven.raglocale.chat.ChatViewModel
import it.netseven.raglocale.chat.RetrievalTrace
import it.netseven.raglocale.chat.Role
import it.netseven.raglocale.inference.EngineState
import java.util.Locale

/** Schermata chat: modalità RAG, risposta in streaming con citazioni, pannello didattico (6.1, 6.2). */
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val engineState by viewModel.engineState.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val ragEnabled by viewModel.ragEnabled.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.loadActiveModel() }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(modifier.fillMaxSize().padding(8.dp)) {
        when (val state = engineState) {
            is EngineState.Loading -> InfoBanner("Caricamento del modello in corso…", loading = true)
            is EngineState.Ready -> if (state.didFallback && state.warning != null) WarningBanner(state.warning)
            is EngineState.Error -> WarningBanner(state.message)
            EngineState.Idle -> {}
        }
        error?.let { WarningBanner(it) }

        RagToggle(enabled = ragEnabled, onToggle = { viewModel.setRagEnabled(it) })

        if (messages.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (ragEnabled) {
                        "Chiedi qualcosa sul documento indicizzato.\n" +
                            "Aggiungi un documento nella scheda \"Documenti\" e un embedder nella scheda \"Modelli\"."
                    } else {
                        "Chat libera col modello locale (senza documenti).\n" +
                            "Attiva \"Usa i documenti\" per le risposte grounded."
                    },
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(messages) { _, message ->
                    Column {
                        MessageBubble(message)
                        message.retrieval?.let { RetrievalPanel(it) }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (ragEnabled) "Fai una domanda sul documento…" else "Scrivi un messaggio…") },
                enabled = !isGenerating,
            )
            Spacer(Modifier.width(8.dp))
            if (isGenerating) {
                IconButton(onClick = { viewModel.stop() }) {
                    Icon(Icons.Filled.Stop, contentDescription = "Interrompi")
                }
            } else {
                IconButton(
                    onClick = {
                        if (input.isNotBlank()) {
                            viewModel.sendMessage(input)
                            input = ""
                        }
                    },
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Invia")
                }
            }
        }
    }
}

@Composable
private fun RagToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Usa i documenti (RAG)", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (enabled) "La risposta è grounded sui chunk recuperati, con citazioni." else "Chat libera, senza retrieval.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == Role.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color =
                if (isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Text(
                text = message.text.ifEmpty { if (message.streaming) "…" else "" },
                modifier = Modifier.padding(12.dp),
                color =
                    if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}

/**
 * Pannello didattico (spec 6.2): per ogni risposta grounded mostra i chunk recuperati con
 * score, evidenziando quelli citati. Visibile a prescindere dalla qualità della risposta.
 */
@Composable
private fun RetrievalPanel(trace: RetrievalTrace) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(0.95f).padding(top = 4.dp, start = 4.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            val intestazione =
                if (trace.chunks.isEmpty()) {
                    "Nessun chunk recuperato"
                } else {
                    "Contesto recuperato (${trace.chunks.size})" +
                        if (trace.citati.isNotEmpty()) " · citati: ${trace.citati.sorted().joinToString { "[$it]" }}" else ""
                }
            Text(
                intestazione,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            trace.avviso?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            trace.chunks.forEachIndexed { indice, chunk ->
                val numero = indice + 1
                val citato = numero in trace.citati
                ChunkRow(numero = numero, score = chunk.score, testo = chunk.testo, citato = citato)
            }
        }
    }
}

@Composable
private fun ChunkRow(
    numero: Int,
    score: Double,
    testo: String,
    citato: Boolean,
) {
    Surface(
        color = if (citato) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                "[$numero] · score ${String.format(Locale.ITALY, "%.2f", score)}" + if (citato) " · citato" else "",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (citato) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                testo.take(MAX_ANTEPRIMA).let { if (testo.length > MAX_ANTEPRIMA) "$it…" else it },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private const val MAX_ANTEPRIMA = 240
