package io.github.tukiza7_debug.vibecast.ui.extensions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import io.github.tukiza7_debug.vibecast.data.repo.ImportErrorException
import io.github.tukiza7_debug.vibecast.importcode.ImportPromptBus
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ExtensionsViewModel @Inject constructor(
    private val repository: ExtensionRepository,
    val importBus: ImportPromptBus,
) : ViewModel() {

    val extensions = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var lastError = mutableStateOf<String?>(null)
        private set

    fun addByCode(code: String, origin: String = "typed") {
        viewModelScope.launch {
            runCatching { repository.importByCode(code, origin) }
                .onFailure { lastError.value = it.message }
        }
    }

    fun installFromFile(text: String) {
        viewModelScope.launch {
            runCatching { repository.importJson(text, "file") }
                .onFailure { lastError.value = it.message }
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
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

    fun clearError() {
        lastError.value = null
    }
}

@Composable
fun ExtensionsScreen(
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    viewModel: ExtensionsViewModel = hiltViewModel(),
) {
    val extensions by viewModel.extensions.collectAsState()
    var codeInput by remember { mutableStateOf("") }
    var lastMessage by remember { mutableStateOf<String?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text != null) viewModel.installFromFile(text) else lastMessage = "Could not read the selected file"
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                "Extensions",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it },
                    label = { Text("Add by code (unix1: / gc1: / gx1:)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        enabled = codeInput.isNotBlank(),
                        onClick = {
                            viewModel.addByCode(codeInput.trim())
                            codeInput = ""
                        },
                    ) { Text("Add code") }
                    OutlinedButton(onClick = { filePicker.launch(arrayOf("application/json")) }) {
                        Text("Install from file")
                    }
                }
                if (viewModel.lastError.value != null) {
                    Text(
                        viewModel.lastError.value ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (lastMessage != null) {
                    Text(lastMessage ?: "", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        items(extensions, key = { it.id }) { extension ->
            ExtensionCard(
                extension = extension,
                onToggle = { viewModel.setEnabled(extension.id, it) },
                onUpdate = { viewModel.update(extension.id) },
                onRemove = { viewModel.remove(extension.id) },
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun ExtensionCard(
    extension: ExtensionEntity,
    onToggle: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier
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
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
    }
}
