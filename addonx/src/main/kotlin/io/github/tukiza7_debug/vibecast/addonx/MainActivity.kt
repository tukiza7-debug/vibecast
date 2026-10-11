package io.github.tukiza7_debug.vibecast.addonx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tukiza7_debug.vibecast.addonx.data.BuildStore
import io.github.tukiza7_debug.vibecast.addonx.ui.theme.AddOnXTheme
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val starterViewModel: MainActivityViewModel = hiltViewModel()
            val theme by starterViewModel.theme.collectAsState()
            AddOnXTheme(themeMode = theme) {
                io.github.tukiza7_debug.vibecast.addonx.ui.analyzer.AddOnXApp()
            }
        }
    }
}

@HiltViewModel
class MainActivityViewModel @Inject constructor(buildStore: BuildStore) : ViewModel() {
    val theme = buildStore.theme.stateIn(viewModelScope, SharingStarted.Eagerly, "dark")
}
