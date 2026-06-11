package it.netseven.raglocale.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.netseven.raglocale.ingestion.IngestionViewModel
import it.netseven.raglocale.ingestion.StatoIngestion

/** Schermata Documenti: scelta sorgente (file/PDF/URL), progresso, errori per caso (task 6.3). */
@Composable
fun IngestionScreen(
    modifier: Modifier = Modifier,
    viewModel: IngestionViewModel = hiltViewModel(),
) {
    val stato by viewModel.stato.collectAsStateWithLifecycle()
    val indiceEmbedder by viewModel.indiceEmbedder.collectAsStateWithLifecycle()
    var url by rememberSaveable { mutableStateOf("") }

    val inCorso = stato is StatoIngestion.Estrazione || stato is StatoIngestion.Indicizzazione

    val pickerTesto =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.ingestTesto(uri)
        }
    val pickerPdf =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.ingestPdf(uri)
        }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("Documenti", style = MaterialTheme.typography.titleLarge)
        Text(
            "Indicizza un documento per la modalità RAG. Un nuovo documento sostituisce il precedente.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        Text(
            indiceEmbedder?.let { "Indice presente (embedder: $it)." } ?: "Nessun documento indicizzato.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.padding(8.dp))
        HorizontalDivider()
        Spacer(Modifier.padding(8.dp))

        Text("Da file", style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { pickerTesto.launch(arrayOf("text/plain", "text/markdown", "application/octet-stream")) },
                enabled = !inCorso,
            ) { Text("Testo (.txt/.md)") }
            OutlinedButton(
                onClick = { pickerPdf.launch(arrayOf("application/pdf")) },
                enabled = !inCorso,
            ) { Text("PDF") }
        }

        Spacer(Modifier.padding(8.dp))
        Text("Da URL", style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("https://…") },
                singleLine = true,
                enabled = !inCorso,
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { viewModel.ingestUrl(url) }, enabled = !inCorso && url.isNotBlank()) {
                Text("Indicizza")
            }
        }

        Spacer(Modifier.padding(12.dp))
        StatoIngestionView(stato)
    }
}

@Composable
private fun StatoIngestionView(stato: StatoIngestion) {
    when (stato) {
        StatoIngestion.Inattivo -> {}
        StatoIngestion.Estrazione -> {
            InfoBanner("Estrazione del testo in corso…", loading = true)
        }
        is StatoIngestion.Indicizzazione -> {
            Column {
                Text(
                    "Indicizzazione: ${stato.processati}/${stato.totale} chunk",
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
        is StatoIngestion.Completato -> {
            Column {
                InfoBanner("Indicizzato \"${stato.documento}\": ${stato.chunk} chunk pronti per la ricerca.")
                stato.avviso?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
        is StatoIngestion.Errore -> WarningBanner(stato.messaggio)
    }
}
