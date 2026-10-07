package com.jkhx.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.jkhx.player.core.model.EpisodeData
import com.jkhx.player.ui.screens.detail.DetailScreen
import com.jkhx.player.ui.screens.home.HomeScreen
import com.jkhx.player.ui.screens.player.PlayerScreen
import com.jkhx.player.ui.screens.plugins.PluginsScreen
import com.jkhx.player.ui.screens.search.SearchScreen
import com.jkhx.player.ui.screens.settings.SettingsScreen
import com.jkhx.player.ui.theme.JkhxTheme
import kotlinx.serialization.json.Json
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JkhxTheme {
                MainAppNavigation()
            }
        }
    }
}

sealed class Screen(val route: String, val title: String) {
    object Home : Screen("home", "Trang Chủ")
    object Search : Screen("search", "Tìm Kiếm")
    object Plugins : Screen("plugins", "Plugins")
    object Settings : Screen("settings", "Cài Đặt")
}

@Composable
fun MainAppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination?.route

    // Chỉ hiện BottomBar ở 4 màn hình chính
    val showBottomBar = currentDestination in listOf(
        Screen.Home.route, Screen.Search.route, Screen.Plugins.route, Screen.Settings.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    val items = listOf(Screen.Home, Screen.Search, Screen.Plugins, Screen.Settings)
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = {
                                when (screen) {
                                    Screen.Home -> Icon(Icons.Default.Home, contentDescription = null)
                                    Screen.Search -> Icon(Icons.Default.Search, contentDescription = null)
                                    Screen.Plugins -> Icon(Icons.Default.Extension, contentDescription = null)
                                    Screen.Settings -> Icon(Icons.Default.Settings, contentDescription = null)
                                }
                            },
                            label = { Text(screen.title) },
                            selected = currentDestination == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onMovieClick = { providerId, slug ->
                        navController.navigate("detail/$providerId/${URLEncoder.encode(slug, "UTF-8")}")
                    }
                )
            }
            composable(Screen.Search.route) {
                SearchScreen(
                    onMovieClick = { providerId, slug ->
                        navController.navigate("detail/$providerId/${URLEncoder.encode(slug, "UTF-8")}")
                    }
                )
            }
            composable(Screen.Plugins.route) {
                PluginsScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }

            composable(
                route = "detail/{providerId}/{slug}",
                arguments = listOf(
                    navArgument("providerId") { type = NavType.StringType },
                    navArgument("slug") { type = NavType.StringType }
                )
            ) { entry ->
                val providerId = entry.arguments?.getString("providerId") ?: ""
                val rawSlug = entry.arguments?.getString("slug") ?: ""
                val slug = URLDecoder.decode(rawSlug, "UTF-8")

                DetailScreen(
                    providerId = providerId,
                    slug = slug,
                    onBack = { navController.popBackStack() },
                    onPlayEpisode = { episode ->
                        val epJson = URLEncoder.encode(Json.encodeToString(EpisodeData.serializer(), episode), "UTF-8")
                        navController.navigate("player/$providerId/$epJson")
                    }
                )
            }

            composable(
                route = "player/{providerId}/{episodeJson}",
                arguments = listOf(
                    navArgument("providerId") { type = NavType.StringType },
                    navArgument("episodeJson") { type = NavType.StringType }
                )
            ) { entry ->
                val providerId = entry.arguments?.getString("providerId") ?: ""
                val rawJson = entry.arguments?.getString("episodeJson") ?: ""
                val epJson = URLDecoder.decode(rawJson, "UTF-8")
                val episode = Json.decodeFromString(EpisodeData.serializer(), epJson)

                PlayerScreen(
                    providerId = providerId,
                    episode = episode,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

