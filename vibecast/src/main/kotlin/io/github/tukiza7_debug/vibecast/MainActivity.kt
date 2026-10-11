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
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import io.github.tukiza7_debug.vibecast.importcode.ClipboardCodeWatcher
import io.github.tukiza7_debug.vibecast.importcode.ImportPromptBus
import io.github.tukiza7_debug.vibecast.ui.detail.DetailScreen
import io.github.tukiza7_debug.vibecast.ui.extensions.ExtensionsScreen
import io.github.tukiza7_debug.vibecast.ui.home.HomeScreen
import io.github.tukiza7_debug.vibecast.ui.importsheet.ImportPromptController
import io.github.tukiza7_debug.vibecast.ui.importsheet.ImportSheetHost
import io.github.tukiza7_debug.vibecast.ui.library.LibraryScreen
import io.github.tukiza7_debug.vibecast.ui.nav.Routes
import io.github.tukiza7_debug.vibecast.ui.search.SearchScreen
import io.github.tukiza7_debug.vibecast.ui.settings.SettingsScreen
import io.github.tukiza7_debug.vibecast.ui.theme.VibeCastTheme
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
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val bottomRoutes = listOf(Routes.HOME, Routes.SEARCH, Routes.LIBRARY, Routes.EXTENSIONS)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (currentRoute in bottomRoutes) {
                NavigationBar {
                    bottomRoutes.forEach { route ->
                        NavigationBarItem(
                            selected = currentRoute == route,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(bottomIcon(route), contentDescription = route) },
                            label = { Text(route.replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
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
                    onOpenDetail = { navController.navigate(it) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenExtensions = { navController.navigate(Routes.EXTENSIONS) { launchSingleTop = true } },
                    snackbarHostState = snackbarHostState,
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onOpenDetail = { navController.navigate(it) },
                    onOpenExtensions = { navController.navigate(Routes.EXTENSIONS) { launchSingleTop = true } },
                )
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(onOpenDetail = { navController.navigate(it) })
            }
            composable(Routes.EXTENSIONS) {
                ExtensionsScreen(snackbarHostState = snackbarHostState)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
            composable("detail/{extensionId}/{url}/{title}?cover={cover}") { entry ->
                val args = entry.arguments
                if (args == null) {
                    return@composable
                }
                DetailScreen(
                    extensionId = args.getString("extensionId").orEmpty(),
                    itemUrl = args.getString("url").orEmpty(),
                    title = args.getString("title").orEmpty(),
                    cover = args.getString("cover")?.takeIf { it.isNotEmpty() },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        ImportSheetHost(controller = importController)
        ExtensionsUndoHost(snackbarHostState, navController)
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

private fun bottomIcon(route: String) =
    when (route) {
        Routes.HOME -> Icons.Filled.Home
        Routes.SEARCH -> Icons.Filled.Explore
        Routes.LIBRARY -> Icons.Filled.Favorite
        else -> Icons.Filled.Extension
    }
