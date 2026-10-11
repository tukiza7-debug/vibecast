package io.github.tukiza7_debug.vibecast.addonx.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tukiza7_debug.vibecast.addonx.data.BuildStore
import io.github.tukiza7_debug.vibecast.addonx.data.TokenStore
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val tokenStore: TokenStore,
    private val buildStore: BuildStore,
) : ViewModel() {

    val theme = buildStore.theme.stateIn(viewModelScope, SharingStarted.Eagerly, "dark")

    fun setTheme(value: String) = viewModelScope.launch { buildStore.setTheme(value) }

    fun saveToken(token: String?) = tokenStore.saveToken(token?.trim()?.takeIf { it.isNotBlank() })

    fun hasToken(): Boolean = tokenStore.token() != null
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val theme by viewModel.theme.collectAsState()
    var token by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Theme", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("dark", "light", "system").forEach { mode ->
                    FilterChip(selected = theme == mode, onClick = { viewModel.setTheme(mode) }, label = { Text(mode) })
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("GitHub access", style = MaterialTheme.typography.titleLarge)
            Text(
                "The token is stored in EncryptedSharedPreferences and is used to publish extensions " +
                    "and to trigger site APK builds in your own repository.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text(if (viewModel.hasToken()) "Replace GitHub token" else "GitHub token") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    viewModel.saveToken(token)
                    token = ""
                    message = if (viewModel.hasToken()) "Token saved" else "Token cleared"
                }) { Text("Save token") }
                OutlinedButton(onClick = {
                    viewModel.saveToken(null)
                    message = "Token cleared"
                }) { Text("Remove") }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
