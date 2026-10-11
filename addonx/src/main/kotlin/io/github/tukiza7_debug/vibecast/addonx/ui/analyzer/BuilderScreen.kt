package io.github.tukiza7_debug.vibecast.addonx.ui.analyzer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.tukiza7_debug.vibecast.addonx.ui.export.ExportPanel

/**
 * Manual analyzer: load a page, review proposals, test steps against the real
 * engine, then export. Also hosts drafts handed over from Auto Import.
 */
@Composable
fun BuilderScreen(viewModel: AnalyzerViewModel = hiltViewModel()) {
    var url by remember { mutableStateOf("https://archive.org") }
    var loadedUrl by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    val session by viewModel.draftSession.session.collectAsState()

    // Auto Import hands drafts over with a request to open the Test tab directly.
    LaunchedEffect(session?.openTestTab) {
        if (viewModel.draftSession.consumeTestTabRequest()) tab = 2
    }
    LaunchedEffect(session != null) {
        if (session != null && loadedUrl == null) loadedUrl = session?.draft?.baseUrl
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Website link") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = {
                loadedUrl = url.trim()
                viewModel.draftSession.clear()
            }) { Text("Load") }
        }
        session?.let { current ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(
                            "Editing: ${current.draft.name}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            "from Auto Import - proposals below match this draft",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Button(onClick = { viewModel.draftSession.clear() }) { Text("New") }
                }
            }
        }
        if (loadedUrl == null) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Manual analyzer", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Prefer full control? Load a page, review the proposed selectors, refine them with the " +
                        "visual picker, test every step, then export. For the one-tap path use the Home tab.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // Scrollable tabs guarantee every label stays readable on narrow screens
            // (the old fixed TabRow clipped "Proposals" on 360 dp devices).
            ScrollableTabRow(
                selectedTabIndex = tab,
                edgePadding = 8.dp,
            ) {
                listOf("Page", "Proposals", "Test", "Export").forEachIndexed { index, label ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(label, maxLines = 1) },
                    )
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
