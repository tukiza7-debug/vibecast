package io.github.tukiza7_debug.vibecast.ui.extensions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tukiza7_debug.vibecast.data.db.ExtensionEntity
import io.github.tukiza7_debug.vibecast.data.repo.ExtensionRepository
import io.github.tukiza7_debug.vibecast.importcode.ImportPromptBus
import io.github.tukiza7_debug.vibecast.sample.SampleInstaller
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExtensionsViewModel
    @Inject
    constructor(
        private val repository: ExtensionRepository,
        val importBus: ImportPromptBus,
        private val sampleInstaller: SampleInstaller,
        val adblockSettings: io.github.tukiza7_debug.vibecast.adblock.prefs.AdBlockSettings,
    ) : ViewModel() {
        val extensions =
            repository.observeAll()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val bypassHosts =
            adblockSettings.bypassHosts
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

        /** Base host per extension id, used for the per-extension ad blocking switch. */
        val hostsByExtension: kotlinx.coroutines.flow.StateFlow<Map<String, String>> =
            extensions
                .map { list ->
                    list.associate { entity ->
                        val base = runCatching { repository.parse(entity.json).baseUrl }.getOrDefault("")
                        entity.id to (io.github.tukiza7_debug.vibecast.core.engine.UrlResolver.hostOf(base) ?: "")
                    }
                }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

        var lastError = mutableStateOf<String?>(null)
            private set

        var busy = mutableStateOf(false)
            private set

        fun addByCode(
            code: String,
            origin: String = "typed",
        ) {
            viewModelScope.launch {
                busy.value = true
                runCatching { repository.importByCode(code, origin) }
                    .onFailure { lastError.value = it.message }
                busy.value = false
            }
        }

        fun installFromFile(text: String) {
            viewModelScope.launch {
                busy.value = true
                runCatching { repository.importJson(text, "file") }
                    .onFailure { lastError.value = it.message }
                busy.value = false
            }
        }

        fun installSample() {
            viewModelScope.launch {
                busy.value = true
                runCatching { sampleInstaller.install() }
                    .onSuccess { if (it == null) lastError.value = "The Internet Archive sample is already installed" }
                    .onFailure { lastError.value = it.message }
                busy.value = false
            }
        }

        fun setEnabled(
            id: String,
            enabled: Boolean,
        ) {
            viewModelScope.launch { repository.setEnabled(id, enabled) }
        }

        fun remove(id: String) {
            viewModelScope.launch { repository.remove(id) }
        }

        fun update(id: String) {
            viewModelScope.launch {
                runCatching { repository.checkForUpdates() }
                    .onSuccess { if (it.isEmpty()) lastError.value = "No newer version available" }
                    .onFailure { lastError.value = it.message }
            }
        }

        fun undoAdd(id: String) {
            viewModelScope.launch { repository.remove(id) }
        }

        /** Toggles ad blocking for one extension's base host. */
        fun toggleAdblockBypass(
            extension: ExtensionEntity,
            allowAds: Boolean,
        ) {
            viewModelScope.launch {
                val host =
                    io.github.tukiza7_debug.vibecast.core.engine.UrlResolver.hostOf(
                        runCatching { repository.parse(extension.json).baseUrl }.getOrDefault(""),
                    ) ?: return@launch
                val current = adblockSettings.bypassHosts.first()
                adblockSettings.setBypassHosts(if (allowAds) current + host else current - host)
            }
        }

        fun clearError() {
            lastError.value = null
        }
    }

/** Extensions: guided add actions, the bundled sample, and per-extension controls. */
@Composable
fun ExtensionsScreen(
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    viewModel: ExtensionsViewModel = hiltViewModel(),
) {
    val extensions by viewModel.extensions.collectAsState()
    val bypassHosts by viewModel.bypassHosts.collectAsState()
    val hostsByExtension by viewModel.hostsByExtension.collectAsState()
    var codeInput by remember { mutableStateOf("") }
    var lastMessage by remember { mutableStateOf<String?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val filePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val text =
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                if (text != null) viewModel.installFromFile(text) else lastMessage = "Could not read the selected file"
            }
        }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                Text("Extensions", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Extensions are declarative recipe files - no executable code, validated before anything is stored.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it },
                    label = { Text("Paste a code (unix1: / gc1: / gx1:)") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.AddLink, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        enabled = codeInput.isNotBlank() && !viewModel.busy.value,
                        onClick = {
                            viewModel.addByCode(codeInput.trim())
                            codeInput = ""
                        },
                    ) { Text("Add code") }
                    OutlinedButton(
                        enabled = !viewModel.busy.value,
                        onClick = { filePicker.launch(arrayOf("application/json")) },
                    ) {
                        Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open file")
                    }
                }
                if (extensions.isEmpty()) {
                    SampleCard(onClick = { viewModel.installSample() }, enabled = !viewModel.busy.value)
                }
                viewModel.lastError.value?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                lastMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        items(extensions, key = { it.id }) { extension ->
            val host = hostsByExtension[extension.id].orEmpty()
            ExtensionCard(
                extension = extension,
                adsAllowed = host.isNotEmpty() && host in bypassHosts,
                onToggle = { viewModel.setEnabled(extension.id, it) },
                onToggleAds = { allowAds -> viewModel.toggleAdblockBypass(extension, allowAds) },
                onUpdate = { viewModel.update(extension.id) },
                onRemove = { viewModel.remove(extension.id) },
            )
        }
        if (extensions.isEmpty()) {
            item {
                EmptyHint()
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SampleCard(
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Extension,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    "Install the Internet Archive sample",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    "Public-domain films straight from archive.org - the quickest way to see VibeCast working.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    Text(
        "Tip: you can also share a code from any app - VibeCast picks it up automatically.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ExtensionCard(
    extension: ExtensionEntity,
    adsAllowed: Boolean,
    onToggle: (Boolean) -> Unit,
    onToggleAds: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(extension.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${extension.id} - v${extension.version} - via ${extension.source}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = extension.enabled, onCheckedChange = onToggle)
            }
            extension.lastError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row {
                    TextButton(onClick = onUpdate) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Update")
                    }
                    TextButton(onClick = onRemove) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Remove")
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Ad blocking",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = !adsAllowed, onCheckedChange = { onToggleAds(!it) })
            }
            Text(
                if (adsAllowed) "Requests from this extension bypass the filter lists" else "Filter lists apply to this extension",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
