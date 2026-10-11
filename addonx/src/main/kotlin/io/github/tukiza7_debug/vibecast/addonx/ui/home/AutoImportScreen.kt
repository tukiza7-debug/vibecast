package io.github.tukiza7_debug.vibecast.addonx.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.tukiza7_debug.vibecast.addonx.ui.common.dottedGrid
import io.github.tukiza7_debug.vibecast.addonx.ui.theme.ConfidenceHigh
import io.github.tukiza7_debug.vibecast.addonx.ui.theme.ConfidenceLow
import io.github.tukiza7_debug.vibecast.addonx.ui.theme.ConfidenceMedium

/**
 * AddOnX home: the One-Link Auto Import hero. Paste any site link, tap Analyze,
 * and the analyzer proposes a ready-to-test extension with confidence levels.
 */
@Composable
fun AutoImportScreen(
    onOpenResult: () -> Unit,
    onOpenBuilds: () -> Unit,
    viewModel: AutoImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val recents by viewModel.recents.collectAsState()
    val builds by viewModel.builds.collectAsState()
    var url by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(state) {
        if (state is AutoUiState.Done) onOpenResult()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Box(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 18.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            ),
                        ),
                    )
                    .dottedGrid(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    .padding(20.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Build an extension\nfrom one link", style = MaterialTheme.typography.headlineMedium)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "AddOnX reads the page and proposes every step with a confidence level. Nothing is " +
                            "invented - low confidence leads to the visual picker.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Website link") },
                    placeholder = { Text("https://example.com/search?q=movie") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = {
                            val pasted = clipboard.getText()?.text.orEmpty().trim()
                            if (pasted.isNotEmpty()) {
                                url = pasted
                                viewModel.analyze(pasted)
                            }
                        }) {
                            Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste and analyze")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.analyze(url) },
                    enabled = url.isNotBlank() && state !is AutoUiState.Analyzing,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (state is AutoUiState.Analyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text((state as AutoUiState.Analyzing).stage)
                    } else {
                        Text("Analyze & Build", style = MaterialTheme.typography.titleMedium)
                    }
                }
                when (val current = state) {
                    is AutoUiState.Failed -> {
                        Spacer(Modifier.height(10.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "Could not analyze",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Text(
                                    current.reason,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }
                    is AutoUiState.Analyzing -> {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    else -> {}
                }
            }
        }

        if (recents.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("Recent links", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.clearHistory() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Clear history", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(recents, key = { it.url }) { entry ->
                        Card(
                            onClick = {
                                url = entry.url
                                viewModel.analyze(entry.url)
                            },
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                // confidence dot: green/amber/red from the stored analysis
                                Box(
                                    Modifier
                                        .size(9.dp)
                                        .background(
                                            when (entry.confidence) {
                                                "high" -> ConfidenceHigh
                                                "medium" -> ConfidenceMedium
                                                else -> ConfidenceLow
                                            },
                                            androidx.compose.foundation.shape.CircleShape,
                                        ),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.width(150.dp)) {
                                    Text(
                                        entry.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        entry.url.removePrefix("https://").removePrefix("http://"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text("My builds", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (builds.isNotEmpty()) {
                    Text(
                        "${builds.size} saved",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (builds.isEmpty()) {
            item {
                Card(
                    onClick = onOpenBuilds,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Nothing built yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Analyze a link above; the finished extension appears here, ready to export as JSON, code or site APK.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            items(builds.take(4), key = { it.createdAtMs }) { build ->
                BuildRow(build)
            }
            item {
                Text(
                    "See all builds",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                            .clickable(onClick = onOpenBuilds),
                )
            }
        }
    }
}

@Composable
private fun BuildRow(build: io.github.tukiza7_debug.vibecast.addonx.data.SavedBuild) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(build.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                "${build.id} - saved ${java.text.DateFormat.getDateTimeInstance(
                    java.text.DateFormat.SHORT,
                    java.text.DateFormat.SHORT,
                ).format(java.util.Date(build.createdAtMs))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
