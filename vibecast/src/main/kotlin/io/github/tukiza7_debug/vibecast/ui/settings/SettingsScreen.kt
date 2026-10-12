package io.github.tukiza7_debug.vibecast.ui.settings

import android.app.Activity
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tukiza7_debug.vibecast.BuildConfig
import io.github.tukiza7_debug.vibecast.data.prefs.SettingsStore
import io.github.tukiza7_debug.vibecast.data.remote.UpdateRepository
import io.github.tukiza7_debug.vibecast.data.repo.ExtensionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        val settings: SettingsStore,
        private val extensionRepository: ExtensionRepository,
        private val updateRepository: UpdateRepository,
        private val apkInstaller: io.github.tukiza7_debug.vibecast.data.remote.ApkInstaller,
        val adblockSettings: io.github.tukiza7_debug.vibecast.adblock.prefs.AdBlockSettings,
    ) : ViewModel() {
        val theme = settings.theme.stateIn(viewModelScope, SharingStarted.Eagerly, "dark")
        val defaultExtensionId = settings.defaultExtensionId.stateIn(viewModelScope, SharingStarted.Eagerly, null)
        val defaultSpeed = settings.defaultSpeed.stateIn(viewModelScope, SharingStarted.Eagerly, 1f)
        val autoUpdate = settings.autoUpdateCheck.stateIn(viewModelScope, SharingStarted.Eagerly, true)

        val extensions =
            extensionRepository.observeAll()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val adblockEnabled = adblockSettings.enabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
        val easyListEnabled = adblockSettings.easyListEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
        val easyPrivacyEnabled = adblockSettings.easyPrivacyEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
        val blockedTotal = adblockSettings.blockedTotal.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

        var updateMessage = mutableStateOf<String?>(null)
        var checkingUpdate = mutableStateOf(false)
        var availableUpdate = mutableStateOf<UpdateRepository.Release?>(null)

        fun setTheme(value: String) = viewModelScope.launch { settings.setTheme(value) }

        fun setDefaultExtension(id: String?) = viewModelScope.launch { settings.setDefaultExtensionId(id) }

        fun setSpeed(value: Float) = viewModelScope.launch { settings.setDefaultSpeed(value) }

        fun setAdblockEnabled(value: Boolean) = viewModelScope.launch { adblockSettings.setEnabled(value) }

        fun setEasyList(value: Boolean) = viewModelScope.launch { adblockSettings.setEasyListEnabled(value) }

        fun setEasyPrivacy(value: Boolean) = viewModelScope.launch { adblockSettings.setEasyPrivacyEnabled(value) }

        fun setAutoUpdate(value: Boolean) = viewModelScope.launch { settings.setAutoUpdateCheck(value) }

        fun clearCache() = viewModelScope.launch { settings.clearCache() }

        fun checkUpdate() {
            checkingUpdate.value = true
            viewModelScope.launch {
                when (val result = updateRepository.check(force = true)) {
                    is UpdateRepository.CheckResult.Available -> {
                        availableUpdate.value = result.release
                        updateMessage.value = "Version ${result.release.versionName} is available"
                    }
                    UpdateRepository.CheckResult.UpToDate -> {
                        availableUpdate.value = null
                        updateMessage.value = "You are on the latest version"
                    }
                    is UpdateRepository.CheckResult.Failed -> {
                        availableUpdate.value = null
                        updateMessage.value = "Update check failed: ${result.reason}"
                    }
                }
                checkingUpdate.value = false
            }
        }

        fun skipVersion(version: String) = viewModelScope.launch { settings.setSkippedVersion(version) }

        fun installUpdate(activity: android.app.Activity) {
            val release = availableUpdate.value ?: return
            checkingUpdate.value = true
            viewModelScope.launch {
                val outcome = apkInstaller.install(activity, release) { progress -> updateMessage.value = progress }
                checkingUpdate.value = false
                when (outcome) {
                    is io.github.tukiza7_debug.vibecast.data.remote.ApkInstaller.InstallOutcome.Success ->
                        updateMessage.value = outcome.message
                    is io.github.tukiza7_debug.vibecast.data.remote.ApkInstaller.InstallOutcome.PendingUserAction ->
                        updateMessage.value = outcome.message
                    is io.github.tukiza7_debug.vibecast.data.remote.ApkInstaller.InstallOutcome.Failure ->
                        updateMessage.value = "Install failed: ${outcome.reason}"
                }
            }
        }
    }

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val theme by viewModel.theme.collectAsState()
    val defaultExtensionId by viewModel.defaultExtensionId.collectAsState()
    val defaultSpeed by viewModel.defaultSpeed.collectAsState()
    val autoUpdate by viewModel.autoUpdate.collectAsState()
    val extensions by viewModel.extensions.collectAsState()
    val adblockEnabled by viewModel.adblockEnabled.collectAsState()
    val easyListEnabled by viewModel.easyListEnabled.collectAsState()
    val easyPrivacyEnabled by viewModel.easyPrivacyEnabled.collectAsState()
    val blockedTotal by viewModel.blockedTotal.collectAsState()
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Section("Theme") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("dark", "light", "system").forEach { mode ->
                    FilterChip(selected = theme == mode, onClick = { viewModel.setTheme(mode) }, label = { Text(mode) })
                }
            }
        }
        Section("Default extension") {
            extensions.forEach { ext ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = defaultExtensionId == ext.id,
                        onClick = { viewModel.setDefaultExtension(ext.id) },
                        label = { Text(ext.name) },
                    )
                }
            }
            if (extensions.isNotEmpty()) {
                OutlinedButton(onClick = { viewModel.setDefaultExtension(null) }) { Text("No default") }
            }
        }
        Section("Player") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                    FilterChip(
                        selected = kotlin.math.abs(defaultSpeed - speed) < 0.01f,
                        onClick = { viewModel.setSpeed(speed) },
                        label = { Text("${speed}x") },
                    )
                }
            }
        }
        Section("Ad blocking") {
            SettingSwitch("Enable ad blocking", adblockEnabled, viewModel::setAdblockEnabled)
            SettingSwitch("EasyList", easyListEnabled, viewModel::setEasyList)
            SettingSwitch("EasyPrivacy", easyPrivacyEnabled, viewModel::setEasyPrivacy)
            Text(
                "Blocked requests so far: $blockedTotal",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Section("Updates") {
            SettingSwitch("Check automatically", autoUpdate, viewModel::setAutoUpdate)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { viewModel.checkUpdate() }, enabled = !viewModel.checkingUpdate.value) {
                    Text("Check now")
                }
                if (viewModel.checkingUpdate.value) CircularProgressIndicator(Modifier.height(24.dp))
            }
            viewModel.updateMessage.value?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            viewModel.availableUpdate.value?.let { release ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val activity = context as? android.app.Activity ?: return@Button
                        viewModel.installUpdate(activity)
                    }) { Text("Install ${release.versionName}") }
                    OutlinedButton(onClick = { viewModel.skipVersion(release.versionName) }) { Text("Skip") }
                }
            }
        }
        Section("About") {
            Text("VibeCast ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            Text(
                "Package ${BuildConfig.APPLICATION_ID}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { viewModel.clearCache() }) { Text("Clear cache") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
