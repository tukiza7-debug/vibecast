package io.github.tukiza7_debug.vibecast.addonx.ui.analyzer

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tukiza7_debug.vibecast.addonx.data.HistoryStore
import io.github.tukiza7_debug.vibecast.addonx.ui.auto.AutoResultScreen
import io.github.tukiza7_debug.vibecast.addonx.ui.builds.BuildsScreen
import io.github.tukiza7_debug.vibecast.addonx.ui.home.AutoImportScreen
import io.github.tukiza7_debug.vibecast.addonx.ui.home.AutoImportViewModel
import io.github.tukiza7_debug.vibecast.addonx.ui.onboarding.OnboardingScreen
import io.github.tukiza7_debug.vibecast.addonx.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val historyStore: HistoryStore,
    ) : ViewModel() {
        val done = historyStore.onboardingDone.stateIn(viewModelScope, SharingStarted.Eagerly, true)

        fun finish() {
            viewModelScope.launch { historyStore.setOnboardingDone() }
        }
    }

/** Top-level shell: Home (Auto Import), Analyzer, My Builds, Settings. */
@Composable
fun AddOnXApp(
    pendingAutoUrl: String? = null,
    onAutoUrlConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBottomBar = route in listOf("home", "analyzer", "builds", "settings")

    // The Auto Import VM lives at activity scope so the home and result routes
    // share one analysis and one draft hand-off. Outside the NavHost the
    // ViewModelStoreOwner is the Activity itself.
    val activityOwner =
        requireNotNull(androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current) {
            "AddOnX requires a ViewModelStoreOwner"
        }
    val autoViewModel: AutoImportViewModel = hiltViewModel(viewModelStoreOwner = activityOwner)
    val onboardingViewModel: OnboardingViewModel = hiltViewModel(viewModelStoreOwner = activityOwner)
    val onboarded by onboardingViewModel.done.collectAsState()

    androidx.compose.runtime.LaunchedEffect(pendingAutoUrl) {
        if (!pendingAutoUrl.isNullOrBlank()) {
            autoViewModel.analyze(pendingAutoUrl)
            onAutoUrlConsumed()
        }
    }

    if (!onboarded) {
        OnboardingScreen(onDone = { onboardingViewModel.finish() })
        return
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val tabs =
                        listOf(
                            Triple("home", Icons.Filled.AutoAwesome, "Home"),
                            Triple("analyzer", Icons.Filled.Handyman, "Analyzer"),
                            Triple("builds", Icons.Filled.Extension, "Builds"),
                            Triple("settings", Icons.Filled.Settings, "Settings"),
                        )
                    tabs.forEach { (r, icon, label) ->
                        NavigationBarItem(
                            selected = route == r,
                            onClick = {
                                navController.navigate(r) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding),
        ) {
            composable("home") {
                AutoImportScreen(
                    onOpenResult = { navController.navigate("auto_result") { launchSingleTop = true } },
                    onOpenBuilds = { navController.navigate("builds") { launchSingleTop = true } },
                    viewModel = autoViewModel,
                )
            }
            composable("analyzer") { BuilderScreen() }
            composable("builds") { BuildsScreen() }
            composable("settings") { SettingsScreen() }
            composable("auto_result") {
                AutoResultScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAnalyzer = {
                        navController.navigate("analyzer") { launchSingleTop = true }
                    },
                    viewModel = autoViewModel,
                )
            }
        }
    }
}
