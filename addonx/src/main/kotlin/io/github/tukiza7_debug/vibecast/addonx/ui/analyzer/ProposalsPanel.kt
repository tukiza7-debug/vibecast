package io.github.tukiza7_debug.vibecast.addonx.ui.analyzer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.tukiza7_debug.vibecast.addonx.analyzer.DomAnalyzer

/** Proposals tab: ranked selector proposals, recordings and the manual assignments. */
@Composable
fun ProposalsPanel(
    viewModel: AnalyzerViewModel,
    modifier: Modifier = Modifier,
) {
    val analysis by viewModel.analysis.collectAsState()
    val picked by viewModel.picked.collectAsState()
    val assignments by viewModel.assignments.collectAsState()
    val recordedJson by viewModel.recordedJson.collectAsState()
    val recordedMedia by viewModel.recordedMedia.collectAsState()
    val spaHint by viewModel.spaHint.collectAsState()
    val detailFields by viewModel.detailFields.collectAsState()

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp)) {
        Text("Selector proposals", style = MaterialTheme.typography.titleLarge)
        val current = analysis
        if (current == null) {
            Text(
                "Load a page and come back once it finished rendering.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            if (current.proposals.isEmpty()) {
                if (spaHint) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("This page looks client-rendered", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "The server HTML has no list structure. Let the page load fully in the Page tab - " +
                                    "scroll a little and come back; the analyzer reads what the WebView rendered.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    Text(
                        "No confident repeated pattern found on this page. Use Pick in the Page tab to mark one card manually.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            current.proposals.forEach { proposal ->
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint =
                                    when (proposal.confidence) {
                                        DomAnalyzer.Confidence.HIGH -> io.github.tukiza7_debug.vibecast.addonx.ui.theme.ConfidenceHigh
                                        DomAnalyzer.Confidence.MEDIUM -> io.github.tukiza7_debug.vibecast.addonx.ui.theme.ConfidenceMedium
                                        else -> io.github.tukiza7_debug.vibecast.addonx.ui.theme.ConfidenceLow
                                    },
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("items: ${proposal.itemSelector}", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            "title: ${proposal.titleSelector ?: "-"}   link: ${proposal.linkSelector ?: "-"}   " +
                                "cover: ${proposal.coverSelector ?: "-"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = {}, label = { Text("${proposal.itemCount} repeats") })
                            AssistChip(onClick = {}, label = { Text(proposal.confidence.name.lowercase()) })
                        }
                    }
                }
            }
            if (current.pagination.isNotEmpty()) {
                Text("Pagination", style = MaterialTheme.typography.titleMedium)
                current.pagination.forEach { p ->
                    Text(
                        "${p.linkSelector} -> ${p.sampleUrl} (${p.confidence.name.lowercase()})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }

        if (detailFields.isNotEmpty()) {
            Text("Detail fields", style = MaterialTheme.typography.titleMedium)
            detailFields.forEach { (field, selector) ->
                Text("$field -> $selector", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text("Recorded JSON endpoints", style = MaterialTheme.typography.titleMedium)
        if (recordedJson.isEmpty()) {
            Text(
                "Toggle Record in the Page tab and browse - XHR endpoints with JSON arrays land here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        recordedJson.forEach { endpoint ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text("${endpoint.itemCount} items", style = MaterialTheme.typography.titleMedium)
                    Text(
                        endpoint.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                    Text(
                        "keys: ${endpoint.sampleKeys.joinToString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { viewModel.inspectJsonEndpoint(endpoint.url) }) { Text("Inspect again") }
                }
            }
        }

        if (recordedMedia.isNotEmpty()) {
            Text("Recorded media URLs", style = MaterialTheme.typography.titleMedium)
            recordedMedia.take(6).forEach { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
        }

        Text("Picked element", style = MaterialTheme.typography.titleMedium)
        val currentPick = picked
        if (currentPick != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(currentPick.selector, style = MaterialTheme.typography.titleMedium)
                    Text(
                        currentPick.text.take(120),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Assign this element to:", style = MaterialTheme.typography.bodyMedium)
                    listOf("title", "synopsis", "cover", "episodes", "episodeName", "episodeUrl").forEach { field ->
                        OutlinedButton(onClick = { viewModel.assignToField(field) }, modifier = Modifier.fillMaxWidth()) {
                            Text(field)
                        }
                    }
                }
            }
        } else {
            Text(
                "Enable Pick in the Page tab, then tap an element to assign it to a field.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (assignments.isNotEmpty()) {
            Text("Assignments", style = MaterialTheme.typography.titleMedium)
            assignments.forEach { (field, selector) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$field = $selector", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { viewModel.clearAssignment(field) }) { Text("Clear") }
                }
            }
            Text(
                "Assignments flow into Test and Export automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
