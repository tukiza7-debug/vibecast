package io.github.tukiza7_debug.vibecast.addonx.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tukiza7_debug.vibecast.adblock.FilterEngine
import io.github.tukiza7_debug.vibecast.adblock.prefs.AdBlockSettings
import io.github.tukiza7_debug.vibecast.addonx.data.BuildStore
import io.github.tukiza7_debug.vibecast.addonx.data.HistoryStore
import io.github.tukiza7_debug.vibecast.addonx.data.TokenStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val tokenStore: TokenStore,
        private val buildStore: BuildStore,
        private val historyStore: HistoryStore,
        private val adblockSettings: AdBlockSettings,
        private val filterEngine: FilterEngine,
    ) : ViewModel() {
        val theme: StateFlow<String> = buildStore.theme.stateIn(viewModelScope, SharingStarted.Eagerly, "dark")

        val repoOwner: StateFlow<String?> = buildStore.repoOwner.stateIn(viewModelScope, SharingStarted.Eagerly, null)
        val repoName: StateFlow<String?> = buildStore.repoName.stateIn(viewModelScope, SharingStarted.Eagerly, null)
        val publishPath: StateFlow<String?> = buildStore.publishPath.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        val easyListEnabled: StateFlow<Boolean> = adblockSettings.easyListEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
        val easyPrivacyEnabled: StateFlow<Boolean> =
            adblockSettings.easyPrivacyEnabled.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                true,
            )
        val blockedTotal: StateFlow<Long> = adblockSettings.blockedTotal.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

        val adblockRules: StateFlow<Int> =
            kotlinx.coroutines.flow.flow { emit(filterEngine.stats().networkRules) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

        val versionName: StateFlow<String> =
            kotlinx.coroutines.flow.flowOf(io.github.tukiza7_debug.vibecast.addonx.BuildConfig.VERSION_NAME)
                .stateIn(viewModelScope, SharingStarted.Eagerly, "")

        private val _hasToken = kotlinx.coroutines.flow.MutableStateFlow(false)
        val hasToken: StateFlow<Boolean> = _hasToken.asStateFlow()

        init {
            viewModelScope.launch {
                _hasToken.value = tokenStore.token() != null
            }
        }

        fun setTheme(value: String) = viewModelScope.launch { buildStore.setTheme(value) }

        fun saveToken(token: String?) {
            viewModelScope.launch {
                tokenStore.saveToken(token?.trim()?.takeIf { it.isNotBlank() })
                _hasToken.value = tokenStore.token() != null
            }
        }

        fun hasToken(): Boolean = tokenStore.token() != null

        fun saveDefaults(
            owner: String,
            name: String,
            path: String,
        ) = viewModelScope.launch {
            if (owner.isNotBlank() && name.isNotBlank()) buildStore.setRepo(owner, name)
            if (path.isNotBlank()) buildStore.setPublishPath(path)
        }

        fun setEasyList(value: Boolean) = viewModelScope.launch { adblockSettings.setEasyListEnabled(value) }

        fun setEasyPrivacy(value: Boolean) = viewModelScope.launch { adblockSettings.setEasyPrivacyEnabled(value) }

        fun clearLocalData() {
            viewModelScope.launch {
                historyStore.clearRecents()
                historyStore.clearBuilds()
                buildStore.reset()
            }
        }
    }
