package dev.vimivo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.vimivo.ui.theme.VimivoTheme
import kotlinx.serialization.Serializable

@Serializable
private data object HomeRoute

@Serializable
private data object MusicRoute

@Serializable
private data object VideoRoute

@Serializable
private data object SettingsRoute

private data class Destination(val label: String, val route: Any, val icon: ImageVector)

@Composable
fun VimivoApp() = VimivoTheme {
    val navController = rememberNavController()
    val destinations = listOf(
        Destination("Home", HomeRoute, Icons.Outlined.Home),
        Destination("Music", MusicRoute, Icons.Outlined.LibraryMusic),
        Destination("Video", VideoRoute, Icons.Outlined.VideoLibrary),
        Destination("Settings", SettingsRoute, Icons.Outlined.Settings),
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                val entry by navController.currentBackStackEntryAsState()
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = entry?.destination?.hasRoute(destination.route::class) == true,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(HomeRoute) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(padding),
        ) {
            composable<HomeRoute> {
                PlaceholderScreen(
                    title = "Your media, in one place",
                    detail = "Scan this device to begin building your private library.",
                )
            }
            composable<MusicRoute> {
                PlaceholderScreen(
                    title = "Music",
                    detail = "Albums, artists, songs, and playlists will live here.",
                )
            }
            composable<VideoRoute> {
                PlaceholderScreen(
                    title = "Video",
                    detail = "Local videos and authorized streams will live here.",
                )
            }
            composable<SettingsRoute> {
                PlaceholderScreen(
                    title = "Settings",
                    detail = "Playback, library, appearance, and privacy controls.",
                )
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, detail: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            Text(detail, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
        }
    }
}
