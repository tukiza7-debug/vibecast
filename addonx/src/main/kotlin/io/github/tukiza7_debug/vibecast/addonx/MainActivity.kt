package io.github.tukiza7_debug.vibecast.addonx

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tukiza7_debug.vibecast.addonx.data.BuildStore
import io.github.tukiza7_debug.vibecast.addonx.ui.theme.AddOnXTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var pendingAutoUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            val starterViewModel: MainActivityViewModel = hiltViewModel()
            val theme by starterViewModel.theme.collectAsState()
            val autoUrl by pendingAutoUrl
            AddOnXTheme(themeMode = theme) {
                io.github.tukiza7_debug.vibecast.addonx.ui.analyzer.AddOnXApp(
                    pendingAutoUrl = autoUrl,
                    onAutoUrlConsumed = { pendingAutoUrl.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                extractUrl(text)?.let { pendingAutoUrl.value = it }
            }
            Intent.ACTION_VIEW -> {
                val url = intent.data?.getQueryParameter("url") ?: intent.data?.toString()
                if (!url.isNullOrBlank()) pendingAutoUrl.value = url
            }
        }
    }

    /** First http(s) link inside shared text, or null when the share carried no link. */
    private fun extractUrl(text: String): String? = Regex("""https?://\S+""").find(text)?.value
}

@HiltViewModel
class MainActivityViewModel
    @Inject
    constructor(buildStore: BuildStore) : ViewModel() {
        val theme = buildStore.theme.stateIn(viewModelScope, SharingStarted.Eagerly, "dark")
    }
