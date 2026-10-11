package io.github.tukiza7_debug.vibecast.addonx.ui.builds

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tukiza7_debug.vibecast.addonx.data.HistoryStore
import io.github.tukiza7_debug.vibecast.addonx.data.SavedBuild
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BuildsViewModel
    @Inject
    constructor(
        private val historyStore: HistoryStore,
    ) : ViewModel() {
        val builds =
            historyStore.builds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        fun remove(build: SavedBuild) {
            viewModelScope.launch { historyStore.removeBuild(build) }
        }

        fun clearAll() {
            viewModelScope.launch { historyStore.clearBuilds() }
        }
    }

/** My Builds: every exported extension with its code, ready to copy or share again. */
@Composable
fun BuildsScreen(viewModel: BuildsViewModel = hiltViewModel()) {
    val builds by viewModel.builds.collectAsState()
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("My builds", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Exported extensions stay here with their UNIX code so you can reinstall or share them anytime.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (builds.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }) { Text("Clear all") }
                }
            }
        }
        if (builds.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Outlined.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(Modifier.padding(8.dp))
                    Text("No builds yet", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Analyze a link on the Home tab, then Validate & Export. Every finished extension lands here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(builds, key = { it.createdAtMs }) { build ->
            BuildCard(
                build = build,
                onCopyCode = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("extension code", build.unixCode))
                },
                onShare = {
                    val send =
                        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, build.unixCode)
                        }
                    context.startActivity(android.content.Intent.createChooser(send, "Share extension code"))
                },
                onRemove = { viewModel.remove(build) },
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all builds?") },
            text = { Text("Saved extensions and their codes are removed from this device. Anything already installed in VibeCast stays.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    confirmClear = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun BuildCard(
    build: SavedBuild,
    onCopyCode: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(build.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${build.id} - v1 - ${build.sourceUrl.removePrefix("https://").removePrefix("http://")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove build", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row {
                TextButton(onClick = onCopyCode) { Text("Copy UNIX code") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onShare) { Text("Share") }
            }
        }
    }
}
