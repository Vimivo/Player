package dev.vimivo.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.vimivo.ui.components.VimivoEmptyState
import dev.vimivo.ui.theme.LocalWindowSizeClass
import dev.vimivo.ui.theme.VimivoTheme
import dev.vimivo.ui.theme.WindowSizeClass
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

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val windowSize = WindowSizeClass.fromWidth(maxWidth.value)
        val isCompact = windowSize == WindowSizeClass.COMPACT

        CompositionLocalProvider(LocalWindowSizeClass provides windowSize) {
            val entry by navController.currentBackStackEntryAsState()

            Scaffold(
                bottomBar = {
                    if (isCompact) {
                        NavigationBar {
                            destinations.forEach { destination ->
                                val selected = entry?.destination?.hasRoute(destination.route::class) == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(destination.route) {
                                            popUpTo(HomeRoute) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            destination.icon,
                                            contentDescription = null,
                                        )
                                    },
                                    label = { Text(destination.label) },
                                    modifier = Modifier.semantics {
                                        role = Role.Tab
                                        val selText = if (selected) ", selected" else ""
                                        contentDescription = "${destination.label} navigation tab$selText"
                                    },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                Row(Modifier.fillMaxSize().padding(padding)) {
                    if (!isCompact) {
                        NavigationRail(
                            modifier = Modifier.fillMaxHeight(),
                        ) {
                            destinations.forEach { destination ->
                                val selected = entry?.destination?.hasRoute(destination.route::class) == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(destination.route) {
                                            popUpTo(HomeRoute) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            destination.icon,
                                            contentDescription = null,
                                        )
                                    },
                                    label = { Text(destination.label) },
                                    modifier = Modifier.semantics {
                                        role = Role.Tab
                                        val selText = if (selected) ", selected" else ""
                                        contentDescription = "${destination.label} navigation rail tab$selText"
                                    },
                                )
                            }
                        }
                    }
                    NavHost(
                        navController = navController,
                        startDestination = HomeRoute,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        composable<HomeRoute> {
                            VimivoEmptyState(
                                title = "Your media, in one place",
                                detail = "Scan this device to begin building your private library.",
                            )
                        }
                        composable<MusicRoute> {
                            VimivoEmptyState(
                                title = "Music Library",
                                detail = "Albums, artists, songs, and playlists will live here.",
                            )
                        }
                        composable<VideoRoute> {
                            VimivoEmptyState(
                                title = "Video Library",
                                detail = "Local videos and authorized streams will live here.",
                            )
                        }
                        composable<SettingsRoute> {
                            VimivoEmptyState(
                                title = "Settings",
                                detail = "Playback, library, appearance, and privacy controls.",
                            )
                        }
                    }
                }
            }
        }
    }
}
