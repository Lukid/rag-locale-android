package it.netseven.raglocale.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.netseven.raglocale.huggingface.HfAuthState
import it.netseven.raglocale.modelmanager.CardStato
import it.netseven.raglocale.modelmanager.ImportTarget
import it.netseven.raglocale.modelmanager.ModelInfo
import it.netseven.raglocale.modelmanager.ModelManagerViewModel
import it.netseven.raglocale.modelmanager.ModelRow
import it.netseven.raglocale.modelmanager.ModelType
import it.netseven.raglocale.modelmanager.download.DownloadState
import java.util.Locale

/**
 * Model manager con card per modello (download-modelli-in-app, gruppo 7): stato leggibile,
 * download/ripresa/annullamento con progresso ricco, import da file, selezione e rimozione, e in
 * cima il chip di accesso a HuggingFace per i modelli gated.
 */
@Composable
fun ModelManagerScreen(
    modifier: Modifier = Modifier,
    viewModel: ModelManagerViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val hfState by viewModel.hfState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val loggedIn = hfState is HfAuthState.LoggedIn

    val loginLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            viewModel.onLoginResult(result.data)
        }

    var pendingTarget by remember { mutableStateOf<Pair<ModelInfo, ImportTarget>?>(null) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val p = pendingTarget
            if (uri != null && p != null) viewModel.import(p.first, uri, p.second)
            pendingTarget = null
        }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("Modelli", style = MaterialTheme.typography.titleLarge)

        HuggingFaceChip(
            state = hfState,
            configured = viewModel.loginConfigured,
            onLogin = { activity?.let { viewModel.login(it, loginLauncher) } },
            onLogout = viewModel::logout,
        )

        Text(
            "Scarica i modelli con un tap, oppure importa un file già sul device (anche via adb push). " +
                "L'embedder richiede due file: il modello .tflite e il tokenizer sentencepiece.",
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
                        loggedIn = loggedIn,
                        onDownload = { viewModel.download(row.model) },
                        onCancel = { viewModel.cancelDownload(row.model) },
                        onClearPartial = { viewModel.clearPartial(row.model) },
                        onImport = { target ->
                            pendingTarget = row.model to target
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
private fun HuggingFaceChip(
    state: HfAuthState,
    configured: Boolean,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                !configured ->
                    Text(
                        "HuggingFace: login non configurato (i modelli pubblici si scaricano comunque)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                state is HfAuthState.LoggedIn -> {
                    Text(
                        "Accesso HuggingFace: ${state.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onLogout) { Text("Esci") }
                }
                else -> {
                    Text(
                        "Per i modelli gated serve l'accesso a HuggingFace",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = onLogin) { Text("Accedi a HuggingFace") }
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    row: ModelRow,
    loggedIn: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onClearPartial: () -> Unit,
    onImport: (ImportTarget) -> Unit,
    onActivate: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                        "${row.model.quantization} · ${formatBytes(row.model.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatoPill(row.stato)
            }

            if (row.model.gated && !loggedIn && row.stato == CardStato.ASSENTE) {
                Spacer(Modifier.padding(top = 4.dp))
                Text(
                    "Richiede l'accesso a HuggingFace",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            // Progresso ricco durante download e import (stessa barra).
            (row.download as? DownloadState.Downloading)?.let { DownloadProgress(it) }
            if (row.download is DownloadState.CheckingAccess ||
                row.download is DownloadState.Connecting ||
                row.download is DownloadState.Retrying
            ) {
                IndeterminateProgress(row.download)
            }

            if (!row.storage.sufficient && row.stato == CardStato.ASSENTE) {
                Spacer(Modifier.padding(top = 4.dp))
                WarningBanner(
                    "Spazio insufficiente: liberi ${formatBytes(row.storage.freeBytes)}, " +
                        "servono ~${formatBytes(row.storage.requiredBytes)} " +
                        "(mancano ${formatBytes(row.storage.missingBytes)}).",
                )
            }

            Spacer(Modifier.padding(top = 8.dp))
            CardActions(
                row = row,
                onDownload = onDownload,
                onCancel = onCancel,
                onClearPartial = onClearPartial,
                onImport = onImport,
                onActivate = onActivate,
                onRemove = onRemove,
            )
        }
    }
}

@Composable
private fun CardActions(
    row: ModelRow,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onClearPartial: () -> Unit,
    onImport: (ImportTarget) -> Unit,
    onActivate: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (row.stato) {
            CardStato.IN_DOWNLOAD -> {
                OutlinedButton(onClick = onCancel) { Text("Annulla") }
            }
            CardStato.PARZIALE -> {
                TextButton(onClick = onClearPartial) { Text("Cancella parziale") }
                Spacer(Modifier.width(8.dp))
                if (row.model.scaricabile) Button(onClick = onDownload) { Text("Riprendi") }
            }
            CardStato.PRONTO -> {
                TextButton(onClick = onRemove) { Text("Elimina") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onActivate) { Text("Usa") }
            }
            CardStato.ATTIVO -> {
                TextButton(onClick = onRemove) { Text("Elimina") }
            }
            CardStato.ASSENTE -> {
                // Import come sorgente alternativa (sideload).
                row.model.targets().forEachIndexed { i, target ->
                    if (i > 0) Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onImport(target) }) {
                        Text(if (row.model.targets().size > 1) "Importa ${target.etichetta}" else "Importa")
                    }
                }
                if (row.model.scaricabile) {
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onDownload) { Text("Scarica") }
                }
            }
        }
    }
}

@Composable
private fun DownloadProgress(state: DownloadState.Downloading) {
    Spacer(Modifier.padding(top = 8.dp))
    LinearProgressIndicator(
        progress = { (state.progressPercent / 100f).coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth(),
    )
    val parts =
        buildList {
            add("${state.progressPercent.toInt()}%")
            add("${formatBytes(state.bytesDownloaded)} / ${formatBytes(state.totalBytes)}")
            if (state.downloadRateBytesPerSec > 0f) add("${formatBytes(state.downloadRateBytesPerSec.toLong())}/s")
            if (state.etaSeconds >= 0L) add("ETA ${formatEta(state.etaSeconds)}")
        }
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun IndeterminateProgress(state: DownloadState) {
    Spacer(Modifier.padding(top = 8.dp))
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    val label =
        when (state) {
            is DownloadState.CheckingAccess -> "Verifica accesso…"
            is DownloadState.Connecting -> "Connessione…"
            is DownloadState.Retrying -> "Nuovo tentativo ${state.attempt}/${state.maxRetries} (${state.reason})…"
            else -> ""
        }
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StatoPill(stato: CardStato) {
    val (testo, colore) =
        when (stato) {
            CardStato.ASSENTE -> "assente" to MaterialTheme.colorScheme.onSurfaceVariant
            CardStato.IN_DOWNLOAD -> "in download" to MaterialTheme.colorScheme.primary
            CardStato.PARZIALE -> "parziale" to MaterialTheme.colorScheme.tertiary
            CardStato.PRONTO -> "pronto" to MaterialTheme.colorScheme.primary
            CardStato.ATTIVO -> "ATTIVO" to MaterialTheme.colorScheme.primary
        }
    Surface(color = colore.copy(alpha = 0.12f)) {
        Text(
            testo,
            style = MaterialTheme.typography.labelMedium,
            color = colore,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private fun etichettaTipo(tipo: ModelType): String =
    when (tipo) {
        ModelType.LLM -> "LLM (generazione)"
        ModelType.EMBEDDER -> "Embedder (RAG)"
    }

private fun formatEta(seconds: Long): String {
    if (seconds < 60) return "${seconds}s"
    val m = seconds / 60
    val s = seconds % 60
    if (m < 60) return String.format(Locale.ITALY, "%d:%02d", m, s)
    val h = m / 60
    return String.format(Locale.ITALY, "%dh %02dm", h, m % 60)
}

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
