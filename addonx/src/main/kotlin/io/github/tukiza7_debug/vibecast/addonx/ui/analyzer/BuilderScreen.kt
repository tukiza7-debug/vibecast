package io.github.tukiza7_debug.vibecast.addonx.ui.analyzer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.tukiza7_debug.vibecast.addonx.ui.export.ExportPanel

@Composable
fun BuilderScreen(viewModel: AnalyzerViewModel = hiltViewModel()) {
    var url by remember { mutableStateOf("https://archive.org") }
    var loadedUrl by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Website link") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { loadedUrl = url.trim() }) { Text("Load") }
        }
        if (loadedUrl == null) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Build an extension from a website", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Load a page, let the analyzer propose selectors, refine them by tapping elements, " +
                        "test every step against the real engine, then export the JSON, a UNIX code or a site APK.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            TabRow(selectedTabIndex = tab) {
                listOf("Page", "Proposals", "Test", "Export").forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
                }
            }
            when (tab) {
                0 -> AnalyzerWebView(url = loadedUrl ?: "", viewModel = viewModel, modifier = Modifier.weight(1f))
                1 -> ProposalsPanel(viewModel, Modifier.weight(1f))
                2 -> TestPanel(viewModel, Modifier.weight(1f))
                3 -> ExportPanel(viewModel, Modifier.weight(1f))
            }
        }
    }
}
