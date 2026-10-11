package io.github.tukiza7_debug.vibecast.addonx.ui.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tukiza7_debug.vibecast.addonx.analyzer.ExtensionBuilder
import io.github.tukiza7_debug.vibecast.addonx.data.BuildStore
import io.github.tukiza7_debug.vibecast.addonx.data.TokenStore
import io.github.tukiza7_debug.vibecast.addonx.github.GitHubApi
import io.github.tukiza7_debug.vibecast.addonx.github.GitHubWorkflowRunner
import io.github.tukiza7_debug.vibecast.addonx.ui.analyzer.AnalyzerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExportViewModel
    @Inject
    constructor(
        private val tokenStore: TokenStore,
        private val buildStore: BuildStore,
        private val gitHubApi: GitHubApi,
        private val workflowRunner: GitHubWorkflowRunner,
    ) : ViewModel() {
        val repoOwner: kotlinx.coroutines.flow.StateFlow<String> =
            buildStore.repoOwner
                .map { it ?: "tukiza7-debug" }
                .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, "tukiza7-debug")

        val repoName: kotlinx.coroutines.flow.StateFlow<String> =
            buildStore.repoName
                .map { it ?: "vibecast" }
                .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, "vibecast")

        var lastStatus = mutableStateOf<String?>(null)
            private set

        fun saveRepo(
            owner: String,
            name: String,
        ) {
            viewModelScope.launch { buildStore.setRepo(owner, name) }
        }

        fun saveJsonToUri(
            context: android.content.Context,
            uri: android.net.Uri,
            json: String,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                }.onSuccess { lastStatus.value = "extension JSON saved" }
                    .onFailure { lastStatus.value = "save failed: ${it.message}" }
            }
        }

        fun publish(
            json: String,
            path: String,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                val token = tokenStore.token()
                if (token == null) {
                    lastStatus.value = "Add a GitHub token in Settings first"
                    return@launch
                }
                val owner = repoOwner.value
                val repo = repoName.value
                runCatching { gitHubApi.publishExtension(token, owner, repo, path, json, "Add $path") }
                    .onSuccess { lastStatus.value = "Published. GC: ${it.gcCode}" }
                    .onFailure { lastStatus.value = "publish failed: ${it.message}" }
            }
        }

        fun dispatchBuild(
            code: String,
            appName: String,
            packageId: String,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                val token = tokenStore.token()
                if (token == null) {
                    lastStatus.value = "Add a GitHub token in Settings first"
                    return@launch
                }
                runCatching {
                    workflowRunner.buildSiteApk(token, repoOwner.value, repoName.value, code, appName, packageId)
                }.onSuccess { result ->
                    when (result) {
                        is GitHubWorkflowRunner.SiteBuildResult.Apk -> lastStatus.value = "APK downloaded (${result.bytes.size} bytes)"
                        is GitHubWorkflowRunner.SiteBuildResult.Failed -> lastStatus.value = "build failed: ${result.reason}"
                    }
                }.onFailure { lastStatus.value = "build failed: ${it.message}" }
            }
        }
    }

@Composable
fun ExportPanel(
    viewModel: AnalyzerViewModel,
    modifier: Modifier = Modifier,
    exportViewModel: ExportViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("extensions/built.json") }
    var status by remember { mutableStateOf<String?>(null) }
    var exportedJson by remember { mutableStateOf<String?>(null) }
    var exportedCode by remember { mutableStateOf<String?>(null) }

    val saveLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null && exportedJson != null) {
                exportViewModel.saveJsonToUri(context, uri, exportedJson ?: return@rememberLauncherForActivityResult)
            }
        }

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Export", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Extension name") }, singleLine = true)
        OutlinedTextField(value = path, onValueChange = { path = it }, label = { Text("Publish path in your repo") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val draft =
                    viewModel.buildDraft(
                        id =
                            io.github.tukiza7_debug.vibecast.addonx.analyzer.ExtensionBuilder.idForHost(
                                viewModel.pageUrl.value.ifEmpty { "https://invalid.test" },
                            ),
                        name = name.ifBlank { "Built extension" },
                        version = 1,
                    )
                if (draft == null) {
                    status = "load a page and pick at least one item pattern first"
                } else {
                    viewModel.exportExtension(draft) { result ->
                        result.onSuccess { (json, code) ->
                            exportedJson = json
                            exportedCode = code
                            status = "extension is valid; ${json.length} chars"
                        }.onFailure { status = "invalid extension: ${it.message}" }
                    }
                }
            }) { Text("Validate + build") }
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (exportedJson != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { saveLauncher.launch("extension.json") }) { Text("Save .json") }
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    clipboard?.setPrimaryClip(
                        android.content.ClipData.newPlainText("extension code", exportedCode ?: ""),
                    )
                    status = "UNIX code copied to clipboard"
                }) { Text("Copy UNIX code") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    viewModel.addToVibeCast(exportedJson ?: return@OutlinedButton) { result ->
                        status = if (result.isSuccess) "added to VibeCast" else "not added: ${result.exceptionOrNull()?.message}"
                    }
                }) { Text("Add to VibeCast") }
                OutlinedButton(onClick = {
                    val send =
                        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, exportedCode ?: "")
                        }
                    context.startActivity(android.content.Intent.createChooser(send, "Share extension code"))
                }) { Text("Share code") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportViewModel.publish(exportedJson ?: return@OutlinedButton, path) }) {
                    Text("Publish to GitHub")
                }
                OutlinedButton(onClick = {
                    exportViewModel.dispatchBuild(
                        exportedCode ?: return@OutlinedButton,
                        name.ifBlank { "My VibeCast" },
                        "io.github.vibecast.site." + (Math.abs(name.hashCode()) % 100000),
                    )
                }) { Text("Build site APK") }
            }
        }
        var repoOwner by remember { mutableStateOf("") }
        var repoName by remember { mutableStateOf("") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = repoOwner, onValueChange = {
                repoOwner = it
            }, label = { Text("GitHub owner") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = repoName, onValueChange = {
                repoName = it
            }, label = { Text("Repo") }, modifier = Modifier.weight(1f), singleLine = true)
        }
        OutlinedButton(onClick = { exportViewModel.saveRepo(repoOwner, repoName) }) { Text("Save repository") }
        exportViewModel.lastStatus.value?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
