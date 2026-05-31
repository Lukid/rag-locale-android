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
import it.netseven.raglocale.modelmanager.ModelInfo
import it.netseven.raglocale.modelmanager.ModelManagerViewModel
import it.netseven.raglocale.modelmanager.ModelRow
import it.netseven.raglocale.modelmanager.ModelStatus

/** Schermata Model manager: catalogo, stato, import (staging), selezione attivo, rimozione (6.1). */
@Composable
fun ModelManagerScreen(
    modifier: Modifier = Modifier,
    viewModel: ModelManagerViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var pendingModel by remember { mutableStateOf<ModelInfo?>(null) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val model = pendingModel
            if (uri != null && model != null) viewModel.import(uri, model)
            pendingModel = null
        }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("Modelli LLM", style = MaterialTheme.typography.titleLarge)
        Text(
            "Importa un file .litertlm già sul device, oppure usa adb push (download in-app: prossima iterazione).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp)) }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows) { row ->
                ModelCard(
                    row = row,
                    onImport = {
                        pendingModel = row.model
                        picker.launch(arrayOf("*/*"))
                    },
                    onActivate = { viewModel.setActive(row.model) },
                    onRemove = { viewModel.remove(row.model) },
                )
            }
        }
    }
}

@Composable
private fun ModelCard(
    row: ModelRow,
    onImport: () -> Unit,
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
                    Text(row.model.displayName, fontWeight = FontWeight.Bold)
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
                OutlinedButton(onClick = onImport) { Text("Importa file") }
                if (row.status == ModelStatus.READY) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onRemove) { Text("Rimuovi") }
                }
            }
        }
    }
}

private fun statusLabel(status: ModelStatus): String =
    when (status) {
        ModelStatus.NOT_DOWNLOADED -> "non scaricato"
        ModelStatus.DOWNLOADING -> "in download"
        ModelStatus.READY -> "pronto"
    }
