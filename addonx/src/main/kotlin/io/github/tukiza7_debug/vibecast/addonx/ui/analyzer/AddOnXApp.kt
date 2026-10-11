package io.github.tukiza7_debug.vibecast.addonx.ui.analyzer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

@Composable
fun AddOnXApp() {
    val navController = rememberNavController()
    var tab by remember { mutableIntStateOf(0) }
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("builder" to Icons.Filled.Build, "settings" to Icons.Filled.Settings).forEach { (r, icon) ->
                    NavigationBarItem(
                        selected = route == r,
                        onClick = {
                            tab = if (r == "builder") 0 else 1
                            navController.navigate(r) { launchSingleTop = true }
                        },
                        icon = { Icon(icon, contentDescription = r) },
                        label = { Text(r.replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "builder",
            modifier = Modifier.padding(padding),
        ) {
            composable("builder") { BuilderScreen() }
            composable("settings") { io.github.tukiza7_debug.vibecast.addonx.ui.settings.SettingsScreen() }
        }
    }
}
