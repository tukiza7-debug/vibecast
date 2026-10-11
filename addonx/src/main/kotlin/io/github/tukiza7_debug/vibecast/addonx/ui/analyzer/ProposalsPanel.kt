package io.github.tukiza7_debug.vibecast.addonx.ui.analyzer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProposalsPanel(viewModel: AnalyzerViewModel, modifier: Modifier = Modifier) {
    val analysis by viewModel.analysis.collectAsState()
    val picked by viewModel.picked.collectAsState()
    val assignments by viewModel.assignments.collectAsState()
    val recordedJson by viewModel.recordedJson.collectAsState()
    val recordedMedia by viewModel.recordedMedia.collectAsState()

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp)) {
        Text("Selector proposals", style = MaterialTheme.typography.titleLarge)
        val current = analysis
        if (current == null) {
            Text("Load a page and switch here once it finished rendering.")
        } else {
            if (current.proposals.isEmpty()) {
                Text(
                    "No confident repeated pattern found on this page. Use the Pick tab to assign elements manually.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            current.proposals.forEach { proposal ->
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("items: ${proposal.itemSelector}", style = MaterialTheme.typography.titleMedium)
                        Text("title: ${proposal.titleSelector ?: "-"}  link: ${proposal.linkSelector ?: "-"}")
                        Text("cover: ${proposal.coverSelector ?: "-"}")
                        Text(
                            "${proposal.itemCount} repeats - confidence ${proposal.confidence}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (current.pagination.isNotEmpty()) {
                Text("Pagination", style = MaterialTheme.typography.titleMedium)
                current.pagination.forEach { p ->
                    Text("${p.linkSelector} -> ${p.sampleUrl} (${p.confidence})")
                }
            }
        }
        Text("Recorded JSON endpoints", style = MaterialTheme.typography.titleMedium)
        recordedJson.forEach { endpoint ->
            Text("${endpoint.itemCount} items; keys: ${endpoint.sampleKeys.joinToString()}")
            OutlinedButton(onClick = { viewModel.inspectJsonEndpoint(endpoint.url) }) { Text("Inspect again") }
        }
        Text("Recorded media URLs", style = MaterialTheme.typography.titleMedium)
        recordedMedia.forEach { Text(it, maxLines = 1) }
        Text("Picked element", style = MaterialTheme.typography.titleMedium)
        val currentPick = picked
        if (currentPick != null) {
            Text(currentPick.selector)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("title", "synopsis", "cover", "episodes", "episodeName", "episodeUrl").forEach { field ->
                    OutlinedButton(onClick = { viewModel.assignToField(field) }) { Text(field) }
                }
            }
        }
        if (assignments.isNotEmpty()) {
            Text("Assignments", style = MaterialTheme.typography.titleMedium)
            assignments.forEach { (field, selector) -> Text("$field = $selector") }
        }
    }
}
