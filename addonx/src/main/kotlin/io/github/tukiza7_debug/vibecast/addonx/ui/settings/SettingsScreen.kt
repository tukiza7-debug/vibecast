package io.github.tukiza7_debug.vibecast.addonx.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Settings: appearance, GitHub access, export defaults, ad blocking summary,
 * local data and about. Every destructive action confirms first.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val theme by viewModel.theme.collectAsState()
    val repoOwner by viewModel.repoOwner.collectAsState()
    val repoName by viewModel.repoName.collectAsState()
    val publishPath by viewModel.publishPath.collectAsState()
    val hasToken by viewModel.hasToken.collectAsState()
    val adblockRules by viewModel.adblockRules.collectAsState()
    val blockedTotal by viewModel.blockedTotal.collectAsState()
    val easyListEnabled by viewModel.easyListEnabled.collectAsState()
    val easyPrivacyEnabled by viewModel.easyPrivacyEnabled.collectAsState()
    val versionName by viewModel.versionName.collectAsState()
    var token by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmClearData by remember { mutableStateOf(false) }
    var ownerField by remember(repoOwner) { mutableStateOf(repoOwner ?: "") }
    var nameField by remember(repoName) { mutableStateOf(repoName ?: "") }
    var pathField by remember(publishPath) { mutableStateOf(publishPath ?: "extensions/built.json") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        SettingsSection("Appearance") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("dark", "light", "system").forEach { mode ->
                    FilterChip(selected = theme == mode, onClick = { viewModel.setTheme(mode) }, label = { Text(mode) })
                }
            }
        }

        SettingsSection("GitHub access") {
            Text(
                "The token is stored in EncryptedSharedPreferences, never logged, and only sent to api.github.com for your own repo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text(if (hasToken) "Replace GitHub token" else "GitHub token") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    viewModel.saveToken(token)
                    token = ""
                    message = if (viewModel.hasToken()) "Token saved" else "Token cleared"
                }) { Text("Save token") }
                if (hasToken) {
                    OutlinedButton(onClick = {
                        viewModel.saveToken(null)
                        message = "Token removed"
                    }) { Text("Remove") }
                }
            }
        }

        SettingsSection("Export defaults") {
            Text(
                "Used by Publish and Build site APK on the Export tab and the Auto Import preview.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ownerField,
                    onValueChange = { ownerField = it },
                    label = { Text("Owner") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = nameField,
                    onValueChange = { nameField = it },
                    label = { Text("Repository") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = pathField,
                onValueChange = { pathField = it },
                label = { Text("Publish path") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = {
                viewModel.saveDefaults(ownerField, nameField, pathField)
                message = "Export defaults saved"
            }) { Text("Save defaults") }
        }

        SettingsSection("Ad blocking") {
            Text(
                "The analyzer's WebView and network client filter requests with the same engine VibeCast uses.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = easyListEnabled, onClick = { viewModel.setEasyList(!easyListEnabled) }, label = { Text("EasyList") })
                FilterChip(
                    selected = easyPrivacyEnabled,
                    onClick = { viewModel.setEasyPrivacy(!easyPrivacyEnabled) },
                    label = { Text("EasyPrivacy") },
                )
            }
            Text(
                "$adblockRules network rules loaded - $blockedTotal requests blocked so far",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SettingsSection("Local data") {
            Text(
                "Recent links, saved builds and cached filter lists stay on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { confirmClearData = true }) { Text("Clear local data") }
        }

        SettingsSection("About") {
            Text("AddOnX $versionName", style = MaterialTheme.typography.titleMedium)
            Text(
                "Part of the VibeCast family. AddOnX builds declarative extensions; DRM, anti-bot and pirated sources stay out of scope.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmClearData) {
        AlertDialog(
            onDismissRequest = { confirmClearData = false },
            title = { Text("Clear local data?") },
            text = {
                Text(
                    "Recent links, saved builds, export defaults and cached filter lists are removed. Your GitHub token stays.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearLocalData()
                    message = "Local data cleared"
                    confirmClearData = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClearData = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}
