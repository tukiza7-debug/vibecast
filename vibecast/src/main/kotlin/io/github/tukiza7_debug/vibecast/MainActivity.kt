package io.github.tukiza7_debug.vibecast

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import io.github.tukiza7_debug.vibecast.crash.CrashReporter
import io.github.tukiza7_debug.vibecast.importcode.ClipboardCodeWatcher
import io.github.tukiza7_debug.vibecast.importcode.ImportPromptBus
import io.github.tukiza7_debug.vibecast.ui.crash.CrashReportHost
import io.github.tukiza7_debug.vibecast.ui.detail.DetailScreen
import io.github.tukiza7_debug.vibecast.ui.extensions.ExtensionsScreen
import io.github.tukiza7_debug.vibecast.ui.home.HomeScreen
import io.github.tukiza7_debug.vibecast.ui.importsheet.ImportPromptController
import io.github.tukiza7_debug.vibecast.ui.importsheet.ImportSheetHost
import io.github.tukiza7_debug.vibecast.ui.library.LibraryScreen
import io.github.tukiza7_debug.vibecast.ui.nav.GlassNavBar
import io.github.tukiza7_debug.vibecast.ui.nav.GlassNavItem
import io.github.tukiza7_debug.vibecast.ui.nav.Routes
import io.github.tukiza7_debug.vibecast.ui.search.SearchScreen
import io.github.tukiza7_debug.vibecast.ui.settings.SettingsScreen
import io.github.tukiza7_debug.vibecast.ui.theme.VibeCastTheme
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var importPromptBus: ImportPromptBus

    @Inject lateinit var clipboardCodeWatcher: ClipboardCodeWatcher

    lateinit var importController: ImportPromptController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        importController = ImportPromptController(importPromptBus)
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    clipboardCodeWatcher.detectForegroundCode()?.let { code ->
                        importController.offer(code, origin = "clipboard")
                    }
                }
            },
        )
        handleIntent(intent)
        setContent {
            val settingsViewModel: io.github.tukiza7_debug.vibecast.ui.settings.SettingsViewModel = hiltViewModel()
            val theme by settingsViewModel.theme.collectAsState()
            VibeCastTheme(themeMode = theme) {
                AppScaffold(importController)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // stop the bus collector so the controller does not outlive the activity
        importController.close()
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> {
                val code = intent.data?.getQueryParameter("code")
                if (code != null) importController.offer(code, origin = "deep link")
            }
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val found = text?.let { io.github.tukiza7_debug.vibecast.core.codes.CodeDetector.findInText(it) }
                if (found != null) importController.offer(found, origin = "shared text")
            }
        }
    }
}

@Composable
fun AppScaffold(importController: ImportPromptController) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Every detail click goes through this guard: the route is remembered as a
    // crash breadcrumb and a bad route shows a snackbar instead of crashing.
    val openDetail: (String) -> Unit =
        { route ->
            CrashReporter.recordRoute(route)
            try {
                navController.navigate(route)
            } catch (_: Exception) {
                // unmatched route: stay on screen and tell the user instead of crashing
                scope.launch {
                    snackbarHostState.showSnackbar("Could not open this item")
                }
            }
        }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val navItems =
        listOf(
            GlassNavItem(Routes.HOME, Icons.Filled.Home, "Home"),
            GlassNavItem(Routes.SEARCH, Icons.Filled.Explore, "Search"),
            GlassNavItem(Routes.LIBRARY, Icons.Filled.Favorite, "Library"),
            GlassNavItem(Routes.EXTENSIONS, Icons.Filled.Extension, "Extensions"),
        )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (currentRoute in navItems.map { it.route }) {
                GlassNavBar(
                    items = navItems,
                    selectedRoute = currentRoute,
                    onSelect = { route ->
                        navController.navigate(route) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenDetail = openDetail,
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenExtensions = { navController.navigate(Routes.EXTENSIONS) { launchSingleTop = true } },
                    snackbarHostState = snackbarHostState,
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onOpenDetail = openDetail,
                    onOpenExtensions = { navController.navigate(Routes.EXTENSIONS) { launchSingleTop = true } },
                )
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onOpenDetail = openDetail,
                    onOpenHome = { navController.navigate(Routes.HOME) { launchSingleTop = true } },
                )
            }
            composable(Routes.EXTENSIONS) {
                ExtensionsScreen(snackbarHostState = snackbarHostState)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
            composable("detail/{extensionId}/{url}/{title}?cover={cover}&autoplay={autoplay}") { entry ->
                val args = entry.arguments
                if (args == null) {
                    return@composable
                }
                DetailScreen(
                    extensionId = args.getString("extensionId").orEmpty(),
                    itemUrl = args.getString("url").orEmpty(),
                    title = args.getString("title").orEmpty(),
                    cover = args.getString("cover")?.takeIf { it.isNotEmpty() },
                    // Query args arrive as strings; "1" is the autoplay marker
                    // written by Routes.detail().
                    autoplay = args.getString("autoplay") == "1",
                    onBack = { navController.popBackStack() },
                )
            }
        }
        ImportSheetHost(controller = importController)
        ExtensionsUndoHost(snackbarHostState, navController)
        CrashReportHost(snackbarHostState)
    }
}

@Composable
private fun ExtensionsUndoHost(
    snackbarHostState: SnackbarHostState,
    navController: NavHostController,
) {
    val vm: io.github.tukiza7_debug.vibecast.ui.extensions.ExtensionsViewModel = hiltViewModel()
    val bus = vm.importBus
    androidx.compose.runtime.LaunchedEffect(bus) {
        bus.additions.collect { added ->
            if (added.undoable) {
                val result =
                    snackbarHostState.showSnackbar(
                        message = "${added.name} v${added.version} added to VibeCast",
                        actionLabel = "Undo",
                        withDismissAction = true,
                    )
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                    vm.undoAdd(added.extensionId)
                }
            }
        }
    }
}
