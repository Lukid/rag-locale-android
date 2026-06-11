package it.netseven.raglocale.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.netseven.raglocale.inference.Backend
import it.netseven.raglocale.inference.BackendSelection

/** Impostazioni: backend GPU/CPU, cap token di output, keep-alive (6.3). */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val backend by viewModel.backend.collectAsStateWithLifecycle()
    val maxTokens by viewModel.maxOutputTokens.collectAsStateWithLifecycle()
    val keepAlive by viewModel.keepAliveMinutes.collectAsStateWithLifecycle()
    val topK by viewModel.topK.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("Impostazioni", style = MaterialTheme.typography.titleLarge)

        Spacer16()
        Text("Backend di inferenza", style = MaterialTheme.typography.titleMedium)
        BackendOption("GPU (default)", Backend.GPU, backend) { viewModel.setBackend(it) }
        BackendOption("CPU", Backend.CPU, backend) { viewModel.setBackend(it) }
        Text(
            BackendSelection.FALLBACK_WARNING,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer16()
        HorizontalDivider()
        Spacer16()

        Text("Lunghezza massima risposta: $maxTokens token", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = maxTokens.toFloat(),
            onValueChange = { viewModel.setMaxOutputTokens(it.toInt()) },
            valueRange = 50f..500f,
            steps = 8,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Risposte brevi by design: a ~10 token/s di decode, 200 token ≈ 20s.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer16()
        HorizontalDivider()
        Spacer16()

        Text("Auto-unload dopo inattività: $keepAlive min", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = keepAlive.toFloat(),
            onValueChange = { viewModel.setKeepAliveMinutes(it.toInt()) },
            valueRange = 1f..15f,
            steps = 13,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Il modello resta caldo in memoria; viene scaricato dopo questo tempo di inattività.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer16()
        HorizontalDivider()
        Spacer16()

        Text("Chunk recuperati (RAG): $topK", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = topK.toFloat(),
            onValueChange = { viewModel.setTopK(it.toInt()) },
            valueRange = 1f..12f,
            steps = 10,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Quanti chunk il retrieval passa al prompt grounded. Il prefill su GPU è quasi gratis: " +
                "un topK generoso aiuta il richiamo, il limite è la RAM e la lunghezza della risposta.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BackendOption(
    label: String,
    value: Backend,
    selected: Backend,
    onSelect: (Backend) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = value == selected, onClick = { onSelect(value) })
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = value == selected, onClick = { onSelect(value) })
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun Spacer16() {
    androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
}
