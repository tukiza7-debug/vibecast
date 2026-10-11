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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import io.github.tukiza7_debug.vibecast.addonx.data.HistoryStore
import io.github.tukiza7_debug.vibecast.addonx.data.SavedBuild
import io.github.tukiza7_debug.vibecast.addonx.data.TokenStore
import io.github.tukiza7_debug.vibecast.addonx.github.GitHubApi
import io.github.tukiza7_debug.vibecast.addonx.github.GitHubWorkflowRunner
import io.github.tukiza7_debug.vibecast.addonx.ui.analyzer.AnalyzerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExportViewModel
    @Inject
    constructor(
        private val tokenStore: TokenStore,
        private val buildStore: BuildStore,
        private val historyStore: HistoryStore,
        private val gitHubApi: GitHubApi,
        private val workflowRunner: GitHubWorkflowRunner,
    ) : ViewModel() {
        val repoOwner =
            buildStore.repoOwner.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        val repoName = buildStore.repoName.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        val publishPath =
            buildStore.publishPath
                .stateIn(viewModelScope, SharingStarted.Eagerly, "extensions/built.json")

        var lastStatus = mutableStateOf<String?>(null)
            private set

        fun saveRepo(
            owner: String,
            name: String,
        ) {
            viewModelScope.launch { buildStore.setRepo(owner, name) }
        }

        fun savePublishPath(path: String) {
            viewModelScope.launch { buildStore.setPublishPath(path) }
        }

        fun rememberBuild(
            name: String,
            id: String,
            json: String,
            code: String,
            sourceUrl: String,
        ) {
            viewModelScope.launch {
                historyStore.addBuild(
                    SavedBuild(
                        name = name,
                        id = id,
                        json = json,
                        unixCode = code,
                        createdAtMs = System.currentTimeMillis(),
                        sourceUrl = sourceUrl,
                    ),
                )
            }
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
                if (owner.isNullOrBlank() || repo.isNullOrBlank()) {
                    lastStatus.value = "Set the target repository in Settings > Export defaults"
                    return@launch
                }
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
                val owner = repoOwner.value
                val repo = repoName.value
                if (owner.isNullOrBlank() || repo.isNullOrBlank()) {
                    lastStatus.value = "Set the target repository in Settings > Export defaults"
                    return@launch
                }
                runCatching {
                    workflowRunner.buildSiteApk(token, owner, repo, code, appName, packageId)
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
    var path by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var exportedJson by remember { mutableStateOf<String?>(null) }
    var exportedCode by remember { mutableStateOf<String?>(null) }
    val defaultPath by exportViewModel.publishPath.collectAsState()
    val effectivePath = path ?: defaultPath ?: "extensions/built.json"

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
        OutlinedTextField(
            value = effectivePath,
            onValueChange = { path = it },
            label = { Text("Publish path in your repo") },
            singleLine = true,
        )
        Button(onClick = {
            val draft =
                viewModel.buildDraft(
                    id = ExtensionBuilder.idForHost(viewModel.pageUrl.value.ifEmpty { "https://invalid.test" }),
                    name = name.ifBlank { "Built extension" },
                    version = 1,
                )
            if (draft == null) {
                status = "load a page and pick at least one item pattern first"
            } else {
                viewModel.exportExtension(draft) { result ->
                    result
                        .onSuccess { (json, code) ->
                            exportedJson = json
                            exportedCode = code
                            exportViewModel.rememberBuild(
                                name = draft.name,
                                id = draft.id,
                                json = json,
                                code = code,
                                sourceUrl = viewModel.pageUrl.value,
                            )
                            status = "extension is valid and saved to My Builds; ${json.length} chars"
                        }
                        .onFailure {
                            status =
                                when (it) {
                                    is io.github.tukiza7_debug.vibecast.core.codes.ExtensionValidationException ->
                                        "invalid extension:\n" + it.issues.joinToString("\n") { issue -> "- $issue" }
                                    else -> "invalid extension: ${it.message}"
                                }
                        }
                }
            }
        }) { Text("Validate + build") }
        status?.let {
            Card(Modifier.fillMaxWidth()) {
                Text(it, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
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
                OutlinedButton(onClick = { exportViewModel.publish(exportedJson ?: return@OutlinedButton, effectivePath) }) {
                    Text("Publish to GitHub")
                }
                OutlinedButton(onClick = {
                    exportViewModel.savePublishPath(effectivePath)
                    exportViewModel.dispatchBuild(
                        exportedCode ?: return@OutlinedButton,
                        name.ifBlank { "My VibeCast" },
                        "io.github.vibecast.site." + (Math.abs(name.hashCode()) % 100000),
                    )
                }) { Text("Build site APK") }
            }
            Text(
                "Repository owner/name live in Settings > Export defaults; publishing creates both a GC code and a gx1 index entry.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        exportViewModel.lastStatus.value?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
