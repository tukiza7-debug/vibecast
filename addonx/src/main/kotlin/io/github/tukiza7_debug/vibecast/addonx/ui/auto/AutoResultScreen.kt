package io.github.tukiza7_debug.vibecast.addonx.ui.auto

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SendToMobile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.tukiza7_debug.vibecast.addonx.analyzer.AutoAnalyzer
import io.github.tukiza7_debug.vibecast.addonx.analyzer.DomAnalyzer
import io.github.tukiza7_debug.vibecast.addonx.ui.common.CompletenessDialog
import io.github.tukiza7_debug.vibecast.addonx.ui.home.AutoImportViewModel
import io.github.tukiza7_debug.vibecast.addonx.ui.home.AutoUiState

/**
 * One-Link preview card: name, base URL, every proposed step with its confidence
 * and the full action row (Test All / Edit / Export / Send to VibeCast / Build APK).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoResultScreen(
    onBack: () -> Unit,
    onOpenAnalyzer: () -> Unit,
    viewModel: AutoImportViewModel,
) {
    val state by viewModel.state.collectAsState()
    val result = (state as? AutoUiState.Done)?.result

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extension preview") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (result == null) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No analysis yet - go back and analyze a link")
            }
            return@Scaffold
        }
        AutoResultContent(
            result = result,
            modifier = Modifier.padding(padding),
            onTestAll = {
                viewModel.openInAnalyzer(result, openTestTab = true)
                onOpenAnalyzer()
            },
            onEdit = {
                viewModel.openInAnalyzer(result, openTestTab = false)
                onOpenAnalyzer()
            },
            onExport = { name, onDone -> viewModel.export(result, name, onDone) },
            onSendToVibeCast = { json, onResult -> viewModel.sendToVibeCast(json, onResult) },
            onPublish = { json, path, onResult -> viewModel.publishToGitHub(json, path, onResult) },
            onBuildApk = { code, name, onResult -> viewModel.buildSiteApk(code, name, onResult) },
            onBack = onBack,
        )
    }
}

@Composable
private fun AutoResultContent(
    result: AutoAnalyzer.AutoResult,
    modifier: Modifier = Modifier,
    onTestAll: () -> Unit,
    onEdit: () -> Unit,
    onExport: (String, (Result<Pair<String, String>>) -> Unit) -> Unit,
    onSendToVibeCast: (String, (Result<Unit>) -> Unit) -> Unit,
    onPublish: (String, String, (Result<io.github.tukiza7_debug.vibecast.addonx.github.GitHubApi.PublishResult>) -> Unit) -> Unit,
    onBuildApk: (String, String, (Result<ByteArray>) -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var name by remember(result.url) { mutableStateOf(result.suggestedName) }
    var exported by remember { mutableStateOf<Pair<String, String>?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var publishPath by remember { mutableStateOf("extensions/built.json") }
    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }
    var checklistSeen by remember { mutableStateOf(false) }

    pendingAction?.let { action ->
        CompletenessDialog(
            draft = result.draft,
            confirmLabel = action.confirmLabel,
            onConfirm = {
                checklistSeen = true
                val actionToRun = action
                pendingAction = null
                actionToRun.run()
            },
            onDismiss = { pendingAction = null },
        )
    }

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(result.suggestedName, style = MaterialTheme.typography.headlineMedium)
                Text(
                    result.host + " - " + result.baseUrl,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text(result.pageType.name.lowercase().replaceFirstChar { it.uppercase() }) })
                    ConfidenceChip(result.overallConfidence)
                    if (result.needsPicker) {
                        AssistChip(onClick = onEdit, label = { Text("picker needed") })
                    }
                }
            }
        }

        Text("Proposed steps", style = MaterialTheme.typography.titleLarge)
        result.steps.forEach { step ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        when (step.step) {
                            "video" -> Icons.Filled.PlayCircle
                            else -> Icons.Filled.CheckCircle
                        },
                        contentDescription = null,
                        tint =
                            when (step.confidence) {
                                DomAnalyzer.Confidence.HIGH -> io.github.tukiza7_debug.vibecast.addonx.ui.theme.ConfidenceHigh
                                DomAnalyzer.Confidence.MEDIUM -> io.github.tukiza7_debug.vibecast.addonx.ui.theme.ConfidenceMedium
                                else -> io.github.tukiza7_debug.vibecast.addonx.ui.theme.ConfidenceLow
                            },
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(step.step.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium)
                        Text(step.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        step.issues.forEach { issue ->
                            Text(issue, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        if (result.issues.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Before you export", style = MaterialTheme.typography.titleMedium)
                    result.issues.forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (result.mediaCandidates.isNotEmpty()) {
            Text("Media candidates: ${result.mediaCandidates.size}", style = MaterialTheme.typography.titleMedium)
            result.mediaCandidates.take(3).forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Extension name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onTestAll, enabled = !busy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.PlayCircle, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Test all")
            }
            OutlinedButton(onClick = onEdit, enabled = !busy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Edit, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Edit")
            }
        }

        Button(
            onClick = {
                val runExport = {
                    busy = true
                    status = null
                    onExport(name) { outcome ->
                        busy = false
                        outcome
                            .onSuccess { (json, code) ->
                                exported = json to code
                                status = "Extension is valid and saved to My Builds (${json.length} chars)"
                            }
                            .onFailure { status = "Export failed: ${it.message}" }
                    }
                }
                if (checklistSeen) runExport() else pendingAction = PendingAction("Validate & Export", runExport)
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Validate & Export")
            }
        }

        if (exported != null) {
            val (json, code) = exported ?: return@Column
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        val runSend = {
                            onSendToVibeCast(json) { result2 ->
                                status =
                                    if (result2.isSuccess) {
                                        "Sent to VibeCast - open its Extensions tab to confirm"
                                    } else {
                                        "VibeCast rejected it: ${result2.exceptionOrNull()?.message}"
                                    }
                            }
                        }
                        if (checklistSeen) runSend() else pendingAction = PendingAction("Send to VibeCast", runSend)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.SendToMobile, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Send to VibeCast")
                }
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("extension code", code))
                        status = "UNIX code copied to clipboard"
                    },
                ) {
                    Text("Copy code")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        busy = true
                        onPublish(json, publishPath) { result2 ->
                            busy = false
                            status =
                                result2.fold(
                                    { published -> "Published - GC code: ${published.gcCode}" },
                                    { "Publish failed: ${it.message}" },
                                )
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Publish")
                }
                OutlinedButton(
                    onClick = {
                        busy = true
                        onBuildApk(code, name) { result2 ->
                            busy = false
                            status =
                                result2.fold(
                                    { bytes -> "Site APK built (${bytes.size / 1024} KB) - check the Actions tab of your repo" },
                                    { "Build failed: ${it.message}" },
                                )
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Android, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Build APK")
                }
            }
            OutlinedTextField(
                value = publishPath,
                onValueChange = { publishPath = it },
                label = { Text("Publish path in your repo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ConfidenceChip(confidence: DomAnalyzer.Confidence) {
    AssistChip(
        onClick = {},
        label = { Text(confidence.name.lowercase() + " confidence") },
        leadingIcon = {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint =
                    when (confidence) {
                        DomAnalyzer.Confidence.HIGH -> io.github.tukiza7_debug.vibecast.addonx.ui.theme.ConfidenceHigh
                        DomAnalyzer.Confidence.MEDIUM -> io.github.tukiza7_debug.vibecast.addonx.ui.theme.ConfidenceMedium
                        else -> io.github.tukiza7_debug.vibecast.addonx.ui.theme.ConfidenceLow
                    },
                modifier = Modifier.size(16.dp),
            )
        },
    )
}

/** An action deferred behind the completeness checklist dialog. */
private data class PendingAction(
    val confirmLabel: String,
    val run: () -> Unit,
)
