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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.netseven.raglocale.modelmanager.ImportTarget
import it.netseven.raglocale.modelmanager.ModelManagerViewModel
import it.netseven.raglocale.modelmanager.ModelRow
import it.netseven.raglocale.modelmanager.ModelStatus
import it.netseven.raglocale.modelmanager.ModelType

/** Schermata Model manager: catalogo per tipo (LLM/embedder), stato, import, selezione, rimozione (task 5.1-5.3). */
@Composable
fun ModelManagerScreen(
    modifier: Modifier = Modifier,
    viewModel: ModelManagerViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var pendingTarget by remember { mutableStateOf<ImportTarget?>(null) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val target = pendingTarget
            if (uri != null && target != null) viewModel.import(uri, target)
            pendingTarget = null
        }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("Modelli", style = MaterialTheme.typography.titleLarge)
        Text(
            "Importa i file già sul device (o via adb push). L'embedder richiede due file: " +
                "il modello .tflite e il tokenizer sentencepiece.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp)) }

        val perTipo = rows.groupBy { it.model.type }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (tipo in listOf(ModelType.LLM, ModelType.EMBEDDER)) {
                val righe = perTipo[tipo] ?: continue
                item(key = "header-$tipo") {
                    Text(
                        etichettaTipo(tipo),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(righe, key = { it.model.id }) { row ->
                    ModelCard(
                        row = row,
                        onImport = { target ->
                            pendingTarget = target
                            picker.launch(arrayOf("*/*"))
                        },
                        onActivate = { viewModel.setActive(row.model) },
                        onRemove = { viewModel.remove(row.model) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    row: ModelRow,
    onImport: (ImportTarget) -> Unit,
    onActivate: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = row.isActive,
                    onClick = onActivate,
                    enabled = row.status == ModelStatus.READY,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row {
                        Text(row.model.displayName, fontWeight = FontWeight.Bold)
                        if (row.model.isDefault) {
                            Text(
                                "  · consigliato",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        "${row.model.quantization} · ${formatBytes(row.model.sizeBytes)} · ${statusLabel(row.status)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!row.storage.sufficient) {
                Spacer(Modifier.padding(top = 4.dp))
                WarningBanner(
                    "Spazio insufficiente: liberi ${formatBytes(row.storage.freeBytes)}, " +
                        "servono ~${formatBytes(row.storage.requiredBytes)} " +
                        "(mancano ${formatBytes(row.storage.missingBytes)}).",
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                row.model.targets().forEachIndexed { i, target ->
                    if (i > 0) Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { onImport(target) }) {
                        Text(if (row.model.targets().size > 1) "Importa ${target.etichetta}" else "Importa file")
                    }
                }
                if (row.status == ModelStatus.READY) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onRemove) { Text("Rimuovi") }
                }
            }
        }
    }
}

private fun etichettaTipo(tipo: ModelType): String =
    when (tipo) {
        ModelType.LLM -> "LLM (generazione)"
        ModelType.EMBEDDER -> "Embedder (RAG)"
    }

private fun statusLabel(status: ModelStatus): String =
    when (status) {
        ModelStatus.NOT_DOWNLOADED -> "non scaricato"
        ModelStatus.DOWNLOADING -> "in download"
        ModelStatus.READY -> "pronto"
    }
