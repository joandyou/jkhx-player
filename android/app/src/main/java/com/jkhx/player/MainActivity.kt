package com.jkhx.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import com.jkhx.player.ui.theme.AppTheme
import com.jkhx.player.ui.components.glass.frostedGlassSurface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.jkhx.player.core.model.EpisodeData
import com.jkhx.player.core.model.EpisodeStream
import com.jkhx.player.core.model.MovieDetail
import com.jkhx.player.core.model.MovieItem
import com.jkhx.player.settings.DeviceDisplayMode
import com.jkhx.player.ui.screens.detail.DetailScreen
import com.jkhx.player.ui.screens.favorites.FavoritesScreen
import com.jkhx.player.ui.screens.history.WatchHistoryScreen
import com.jkhx.player.ui.screens.home.HomeScreen
import com.jkhx.player.ui.screens.movielist.MovieListScreen
import com.jkhx.player.ui.screens.player.PlayerScreen
import com.jkhx.player.ui.screens.reader.ReaderScreen
import com.jkhx.player.ui.screens.plugins.PluginsScreen
import com.jkhx.player.ui.screens.search.SearchScreen
import com.jkhx.player.ui.screens.settings.SettingsScreen
import com.jkhx.player.ui.screens.settings.AuxServicesSettingsScreen
import com.jkhx.player.ui.screens.stream.StreamSelectionScreen
import com.jkhx.player.ui.screens.stream.StreamAggregator
import com.jkhx.player.ui.screens.stream.UnifiedStreamItem
import kotlinx.coroutines.cancel
import android.content.res.Configuration
import androidx.compose.runtime.compositionLocalOf
import com.jkhx.player.ui.theme.JkhxTheme
import com.jkhx.player.ui.theme.LocalDisplayMode
import com.jkhx.player.ui.theme.tvFocusableItem
import kotlinx.serialization.json.Json
import java.net.URLDecoder
import java.net.URLEncoder

import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.jkhx.player.core.database.entity.HistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val LocalPipMode = compositionLocalOf { false }

class MainActivity : ComponentActivity() {
    private val _isInPipMode = mutableStateOf(false)

    override fun attachBaseContext(newBase: Context) {
        val settingsManager = com.jkhx.player.settings.SettingsManager.getInstance(newBase)
        val langCode = settingsManager.language.value.code
        val localizedContext = com.jkhx.player.settings.LocaleHelper.applyLocale(newBase, langCode)
        super.attachBaseContext(localizedContext)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        _isInPipMode.value = isInPictureInPictureMode
    }

    override fun onResume() {
        super.onResume()
        com.jkhx.player.sync.SyncManager.getInstance(this).triggerAutoPullSync()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableHighRefreshRate()
        setContent {
            val settingsManager = JkhxApplication.instance.settingsManager
            val themeMode by settingsManager.themeMode.collectAsState()
            val displayMode by settingsManager.displayMode.collectAsState()
            val language by settingsManager.language.collectAsState()

            LaunchedEffect(language) {
                com.jkhx.player.settings.LocaleHelper.updateConfiguration(this@MainActivity, language.code)
            }

            JkhxTheme(themeMode = themeMode) {
                CompositionLocalProvider(
                    LocalDisplayMode provides displayMode,
                    LocalPipMode provides _isInPipMode.value
                ) {
                    MainAppNavigation(displayMode = displayMode)
                }
            }
        }
    }

    private fun enableHighRefreshRate() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val currentDisplay = display
                val maxRate = currentDisplay?.supportedModes?.maxOfOrNull { it.refreshRate } ?: 120f
                window.attributes = window.attributes.apply {
                    preferredRefreshRate = maxRate
                }
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                val defaultDisplay = window.windowManager.defaultDisplay
                @Suppress("DEPRECATION")
                val maxMode = defaultDisplay?.supportedModes?.maxByOrNull { it.refreshRate }
                if (maxMode != null) {
                    window.attributes = window.attributes.apply {
                        preferredDisplayModeId = maxMode.modeId
                    }
                }
            }
        } catch (_: Exception) {}
    }
}

sealed class Screen(val route: String, val titleRes: Int) {
    object Home : Screen("home", R.string.nav_home)
    object Search : Screen("search", R.string.nav_discover)
    object Favorites : Screen("favorites", R.string.nav_favorites)
    object Plugins : Screen("plugins", R.string.nav_plugins)
    object Settings : Screen("settings", R.string.nav_settings)
}

object ActivePlayerSession {
    var providerId: String = ""
    var movieSlug: String = ""
    var movieName: String = ""
    var movieThumb: String? = null
    var episodes: List<EpisodeData> = emptyList()
    var currentEpisodeIndex: Int = 0
    var currentEpisode: EpisodeData? = null

    // Metadata cho Multi-Source Matching & Server Switching
    var tmdbId: String? = null
    var imdbId: String? = null
    var originName: String? = null
    var movieYear: Int? = null
    var availableServers: List<com.jkhx.player.core.model.EpisodeServer> = emptyList()
    var currentServerName: String = ""

    // Lưu trữ tên gốc trực tiếp từ Plugin (tiếng Anh và tiếng Việt) để đảm bảo từ khóa tìm kiếm chính xác
    var rawPluginName: String? = null
    var rawPluginOriginName: String? = null

    // Trạng thái sắp xếp tập (Mới nhất trước: true / Cũ nhất trước: false)
    var isDescending: Boolean = false

    var movieDetail: MovieDetail? = null
    var movieType: String = "series"
    var customStream: EpisodeStream? = null
    var lastSelectedStreamGroup: String? = null
    var aggregatedStreams: List<UnifiedStreamItem> = emptyList()
    var activeUnifiedStream: UnifiedStreamItem? = null
}

object ActiveEpisodeHolder {
    var episode: EpisodeData?
        get() = ActivePlayerSession.currentEpisode
        set(value) {
            ActivePlayerSession.currentEpisode = value
        }

    var customStream: EpisodeStream?
        get() = ActivePlayerSession.customStream
        set(value) {
            ActivePlayerSession.customStream = value
        }

    var movieDetail: MovieDetail?
        get() = ActivePlayerSession.movieDetail
        set(value) {
            ActivePlayerSession.movieDetail = value
        }
}

object ActiveCategoryHolder {
    var initialMovies: List<MovieItem> = emptyList()
}

object HomeRefreshNotifier {
    val reloadTrigger = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    fun triggerReload() {
        reloadTrigger.tryEmit(Unit)
    }
}

@Composable
fun MainAppNavigation(displayMode: DeviceDisplayMode = DeviceDisplayMode.MOBILE) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination?.route

    // Chỉ hiện thanh điều hướng ở 5 màn hình chính
    val showNav = currentDestination in listOf(
        Screen.Home.route, Screen.Search.route, Screen.Favorites.route, Screen.Plugins.route, Screen.Settings.route,
        "search?query={query}"
    ) || currentDestination?.startsWith("search") == true
    val navItems = listOf(Screen.Home, Screen.Search, Screen.Favorites, Screen.Plugins, Screen.Settings)
    val isTablet = displayMode == DeviceDisplayMode.TABLET

    fun handleNavClick(screen: Screen) {
        if (screen == Screen.Home) {
            if (currentDestination == Screen.Home.route) {
                HomeRefreshNotifier.triggerReload()
            } else {
                val popped = navController.popBackStack(Screen.Home.route, inclusive = false)
                if (!popped) {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(navController.graph.findStartDestination().id)
                        launchSingleTop = true
                    }
                }
            }
        } else {
            navController.navigate(screen.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    if (isTablet) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (showNav) {
                Surface(
                    modifier = Modifier
                        .padding(start = 16.dp, top = 16.dp, bottom = 16.dp)
                        .width(96.dp)
                        .fillMaxHeight()
                        .shadow(elevation = 16.dp, shape = RoundedCornerShape(32.dp)),
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 16.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_logo),
                            contentDescription = stringResource(R.string.home_logo_desc),
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val brandGradient = AppTheme.colors.brandGradient
                        val heroGlow = AppTheme.colors.heroGlow
                        val brandGradientBrush = remember(brandGradient) {
                            Brush.horizontalGradient(brandGradient)
                        }
                        Text(
                            text = "JKHX",
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.5.sp,
                                letterSpacing = 1.5.sp,
                                brush = brandGradientBrush,
                                shadow = Shadow(
                                    color = heroGlow,
                                    offset = Offset(0f, 0f),
                                    blurRadius = 12f
                                )
                            )
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        navItems.forEach { screen ->
                            val isSelected = if (screen == Screen.Search) {
                                currentDestination == screen.route || currentDestination?.startsWith("search") == true
                            } else {
                                currentDestination == screen.route
                            }
                            val iconColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                        else Color.Transparent
                                    )
                                    .border(
                                        width = if (isSelected) 1.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.Transparent,
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .tvFocusableItem(cornerRadius = 20.dp, focusedScale = 1.06f)
                                    .clickable { handleNavClick(screen) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    when (screen) {
                                        Screen.Home -> Icon(Icons.Default.Home, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
                                        Screen.Search -> Icon(Icons.Default.Explore, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
                                        Screen.Favorites -> Icon(Icons.Default.Favorite, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
                                        Screen.Plugins -> Icon(Icons.Default.Extension, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
                                        Screen.Settings -> Icon(Icons.Default.Settings, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(screen.titleRes),
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = iconColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                AppNavHost(navController = navController, modifier = Modifier.fillMaxSize())
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            AppNavHost(
                navController = navController,
                modifier = Modifier.fillMaxSize()
            )

            if (showNav) {
                NuvioFloatingBottomBar(
                    navItems = navItems,
                    currentDestination = currentDestination,
                    onNavigate = { handleNavClick(it) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                )
            }
        }
    }
}

@Composable
fun NuvioFloatingBottomBar(
    navItems: List<Screen>,
    currentDestination: String?,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = navItems.indexOfFirst { screen ->
        if (screen == Screen.Search) {
            currentDestination == screen.route || currentDestination?.startsWith("search") == true
        } else {
            currentDestination == screen.route
        }
    }.coerceAtLeast(0)

    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 340f
        ),
        label = "jellyPillMotion"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(66.dp)
            .frostedGlassSurface(
                shape = RoundedCornerShape(33.dp),
                containerColor = Color(0xFF14171D).copy(alpha = 0.88f),
                elevation = 18.dp,
                rimWidth = 0.85.dp
            )
    ) {
        val count = navItems.size.coerceAtLeast(1)
        val tabWidth = (maxWidth - 16.dp) / count

        // 1. Lớp Indicator Viên Thạch Trượt Đàn Hồi (Jelly Spring Selection Pill) chuẩn Nuvio
        Box(
            modifier = Modifier
                .padding(vertical = 7.dp)
                .offset(x = 8.dp + tabWidth * animatedIndex + (tabWidth * 0.04f))
                .width(tabWidth * 0.92f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                        )
                    ),
                    shape = RoundedCornerShape(26.dp)
                )
        )

        // 2. Hàng Tab Items
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEach { screen ->
                val isSelected = if (screen == Screen.Search) {
                    currentDestination == screen.route || currentDestination?.startsWith("search") == true
                } else {
                    currentDestination == screen.route
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(26.dp))
                        .tvFocusableItem(cornerRadius = 26.dp, focusedScale = 1.05f)
                        .clickable { onNavigate(screen) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val iconColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.60f)
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.15f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = 0.55f,
                                stiffness = 380f
                            ),
                            label = "navIconScale"
                        )

                        Box(modifier = Modifier.scale(iconScale)) {
                            when (screen) {
                                Screen.Home -> Icon(Icons.Default.Home, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
                                Screen.Search -> Icon(Icons.Default.Explore, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
                                Screen.Favorites -> Icon(Icons.Default.Favorite, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
                                Screen.Plugins -> Icon(Icons.Default.Extension, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
                                Screen.Settings -> Icon(Icons.Default.Settings, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = stringResource(screen.titleRes),
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pluginManager = remember { JkhxApplication.instance.pluginManager }

    val openPlaybackFromHistory: (HistoryEntity, Boolean) -> Unit = remember(navController, context) {
        { history, forceStreamSelection ->
            val isComicOrNovelInitial = pluginManager.isComicProvider(history.providerId) || pluginManager.isNovelProvider(history.providerId)
            if (!forceStreamSelection && !isComicOrNovelInitial) {
                Toast.makeText(context, context.getString(R.string.home_resuming_episode, history.movieName), Toast.LENGTH_SHORT).show()
                val initialEpId = history.lastEpisodeId?.takeIf { it.isNotBlank() } ?: history.movieSlug
                val initialEpName = history.lastEpisodeName?.takeIf { it.isNotBlank() } ?: "Tập 1"
                val initialEp = EpisodeData(
                    id = initialEpId,
                    name = initialEpName,
                    slug = "${history.movieSlug}__$initialEpId",
                    number = 1,
                    season = null,
                    serverName = ""
                )
                val jsonString = Json.encodeToString(EpisodeData.serializer(), initialEp)
                val safeToken = android.util.Base64.encodeToString(
                    jsonString.toByteArray(Charsets.UTF_8),
                    android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                )

                ActivePlayerSession.providerId = history.providerId
                ActivePlayerSession.movieSlug = history.movieSlug
                ActivePlayerSession.movieName = history.movieName
                ActivePlayerSession.movieThumb = history.thumbUrl
                ActivePlayerSession.tmdbId = null
                ActivePlayerSession.imdbId = history.movieSlug.takeIf { it.startsWith("tt", ignoreCase = true) }
                ActivePlayerSession.originName = history.movieName
                ActivePlayerSession.movieYear = null
                ActivePlayerSession.availableServers = emptyList()
                ActivePlayerSession.currentServerName = ""
                ActivePlayerSession.isDescending = false
                ActivePlayerSession.episodes = listOf(initialEp)
                ActivePlayerSession.currentEpisodeIndex = 0
                ActivePlayerSession.currentEpisode = initialEp
                ActivePlayerSession.rawPluginName = history.movieName
                ActivePlayerSession.rawPluginOriginName = history.movieName
                ActivePlayerSession.movieDetail = null
                ActivePlayerSession.customStream = null
                ActivePlayerSession.aggregatedStreams = emptyList()
                ActivePlayerSession.activeUnifiedStream = null
                ActiveEpisodeHolder.episode = initialEp
                ActiveEpisodeHolder.customStream = null

                navController.navigate("player/${history.providerId}/$safeToken")
            } else {
                coroutineScope.launch(Dispatchers.IO) {
                try {
                    val historyDao = JkhxApplication.instance.database.historyDao()
                    var activeProviderId = history.providerId
                    var activeMovieSlug = history.movieSlug
                    var plugin = pluginManager.getPlugin(activeProviderId)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.home_resuming_episode, history.movieName), Toast.LENGTH_SHORT).show()
                    }

                    var detail: MovieDetail? = try {
                        plugin?.getDetail(activeMovieSlug)
                    } catch (_: Exception) {
                        null
                    }

                    // TỰ PHỤC HỒI (SELF-HEALING): Nếu không tìm thấy detail từ plugin gốc (do lệch providerId hoặc API lỗi)
                    if (detail == null) {
                        // 1. Thử qua Cinemeta nếu là IMDb ID (tt...)
                        if (activeMovieSlug.startsWith("tt", ignoreCase = true)) {
                            val cinemetaPlugin = pluginManager.getPlugin("stremio_cinemeta")
                            if (cinemetaPlugin != null) {
                                val cDetail = try { cinemetaPlugin.getDetail(activeMovieSlug) } catch (_: Exception) { null }
                                if (cDetail != null && cDetail.name.isNotBlank()) {
                                    detail = cDetail
                                    plugin = cinemetaPlugin
                                    activeProviderId = "stremio_cinemeta"
                                }
                            }
                        }

                        // 2. Thử quét tìm theo tên phim trên các Movie/TV plugin khác đang hoạt động
                        if (detail == null && history.movieName.isNotBlank()) {
                            val candidatePlugins = pluginManager.getMovieTvPlugins()
                            for (cand in candidatePlugins) {
                                try {
                                    val sRes = cand.search(history.movieName, 1)
                                    val match = sRes.items.firstOrNull { item ->
                                        item.name.equals(history.movieName, ignoreCase = true) ||
                                        (!item.originName.isNullOrBlank() && item.originName.equals(history.movieName, ignoreCase = true)) ||
                                        item.slug == activeMovieSlug
                                    } ?: sRes.items.firstOrNull { item ->
                                        item.name.contains(history.movieName, ignoreCase = true) ||
                                        history.movieName.contains(item.name, ignoreCase = true)
                                    }
                                    if (match != null) {
                                        val candDetail = cand.getDetail(match.slug)
                                        if (candDetail.episodes.isNotEmpty()) {
                                            detail = candDetail
                                            plugin = cand
                                            activeProviderId = cand.id
                                            activeMovieSlug = match.slug

                                            // Tự động chữa lành bản ghi lịch sử trong Room DB
                                            historyDao.delete(history.id)
                                            historyDao.upsert(
                                                history.copy(
                                                    id = "${cand.id}:${match.slug}",
                                                    providerId = cand.id,
                                                    movieSlug = match.slug
                                                )
                                            )
                                            break
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }

                    if (detail == null || plugin == null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.home_cannot_resume_movie), Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }

                    var validDetail: MovieDetail = detail

                    // Nếu plugin hiện tại là Stremio Stream Addon (như AIOStreams) không lưu trữ meta cho slug này,
                    // khôi phục tên phim và ảnh bìa từ HistoryEntity đồng thời làm giàu TMDB/IMDb ID để StreamAggregator tìm được luồng phát
                    val slugTail = activeMovieSlug.substringAfterLast("__")
                    val isDummyName = validDetail.name.isBlank() || validDetail.name == activeMovieSlug || validDetail.name == slugTail
                    if (history.movieName.isNotBlank() && isDummyName) {
                        validDetail = validDetail.copy(
                            name = history.movieName,
                            originName = validDetail.originName?.takeIf { it != activeMovieSlug && it != slugTail } ?: history.movieName,
                            posterUrl = validDetail.posterUrl ?: history.thumbUrl,
                            backdropUrl = validDetail.backdropUrl ?: history.thumbUrl,
                            thumbUrl = validDetail.thumbUrl ?: history.thumbUrl
                        )
                    } else if (validDetail.posterUrl == null && !history.thumbUrl.isNullOrBlank()) {
                        validDetail = validDetail.copy(
                            posterUrl = history.thumbUrl,
                            backdropUrl = validDetail.backdropUrl ?: history.thumbUrl,
                            thumbUrl = validDetail.thumbUrl ?: history.thumbUrl
                        )
                    }

                    // Không chặn mở Player bằng việc gọi TMDB/IMDb qua mạng (PlayerScreen đã có coroutine chạy ngầm tự động enrich TMDB)
                    // Giúp mở PlayerScreen tức thì (< 200ms) ngay khi nhấn card xem tiếp

                    // Tìm server và episode tương ứng với lastEpisodeId hoặc lastEpisodeName
                    val targetServer = validDetail.episodes.find { server ->
                        server.episodes.any { it.id == history.lastEpisodeId || it.name == history.lastEpisodeName }
                    } ?: validDetail.episodes.firstOrNull()

                    val targetEpisode = targetServer?.episodes?.find {
                        it.id == history.lastEpisodeId || it.name == history.lastEpisodeName
                    } ?: if (!history.lastEpisodeId.isNullOrBlank()) {
                        val baseEp = targetServer?.episodes?.firstOrNull()
                        EpisodeData(
                            id = history.lastEpisodeId!!,
                            name = history.lastEpisodeName?.ifBlank { baseEp?.name ?: "Gốc" } ?: (baseEp?.name ?: "Gốc"),
                            slug = "${activeMovieSlug}__${history.lastEpisodeId}",
                            number = baseEp?.number ?: 1,
                            season = baseEp?.season,
                            serverName = targetServer?.serverName
                        )
                    } else {
                        targetServer?.episodes?.firstOrNull()
                    }

                    if (targetServer != null && targetEpisode != null) {
                        val isComicOrNovel = pluginManager.isComicProvider(activeProviderId) || pluginManager.isNovelProvider(activeProviderId)
                        val isStremio = activeProviderId.startsWith("stremio_") || plugin is com.jkhx.player.plugin.manager.stremio.StremioPluginWrapper
                        val sortedTargetEps = targetServer.episodes
                        val targetIndex = sortedTargetEps.indexOfFirst {
                            it.id == targetEpisode.id || it.name == targetEpisode.name
                        }.coerceAtLeast(0)

                        val jsonString = Json.encodeToString(EpisodeData.serializer(), targetEpisode)
                        val safeToken = android.util.Base64.encodeToString(
                            jsonString.toByteArray(Charsets.UTF_8),
                            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                        )

                        if (isComicOrNovel) {
                            withContext(Dispatchers.Main) {
                                ActivePlayerSession.providerId = activeProviderId
                                ActivePlayerSession.movieSlug = activeMovieSlug
                                ActivePlayerSession.movieName = validDetail.name
                                ActivePlayerSession.movieThumb = validDetail.backdropUrl ?: validDetail.posterUrl ?: history.thumbUrl
                                ActivePlayerSession.tmdbId = validDetail.tmdbId
                                ActivePlayerSession.imdbId = validDetail.imdbId
                                ActivePlayerSession.originName = validDetail.originName
                                ActivePlayerSession.movieYear = validDetail.year
                                ActivePlayerSession.availableServers = validDetail.episodes
                                ActivePlayerSession.currentServerName = targetServer.serverName
                                ActivePlayerSession.isDescending = false
                                ActivePlayerSession.episodes = sortedTargetEps
                                ActivePlayerSession.currentEpisodeIndex = targetIndex
                                ActivePlayerSession.currentEpisode = targetEpisode
                                ActivePlayerSession.rawPluginName = validDetail.name
                                ActivePlayerSession.rawPluginOriginName = validDetail.originName
                                ActivePlayerSession.movieDetail = validDetail
                                ActivePlayerSession.customStream = null
                                ActiveEpisodeHolder.episode = targetEpisode

                                navController.navigate("reader/$activeProviderId/$safeToken")
                            }
                        } else if (forceStreamSelection) {
                            withContext(Dispatchers.Main) {
                                ActivePlayerSession.providerId = activeProviderId
                                ActivePlayerSession.movieSlug = activeMovieSlug
                                ActivePlayerSession.movieName = validDetail.name
                                ActivePlayerSession.movieThumb = validDetail.backdropUrl ?: validDetail.posterUrl ?: history.thumbUrl
                                ActivePlayerSession.tmdbId = validDetail.tmdbId
                                ActivePlayerSession.imdbId = validDetail.imdbId
                                ActivePlayerSession.originName = validDetail.originName
                                ActivePlayerSession.movieYear = validDetail.year
                                ActivePlayerSession.availableServers = validDetail.episodes
                                ActivePlayerSession.currentServerName = targetServer.serverName
                                ActivePlayerSession.isDescending = false
                                ActivePlayerSession.episodes = sortedTargetEps
                                ActivePlayerSession.currentEpisodeIndex = targetIndex
                                ActivePlayerSession.currentEpisode = targetEpisode
                                ActivePlayerSession.rawPluginName = validDetail.name
                                ActivePlayerSession.rawPluginOriginName = validDetail.originName
                                ActivePlayerSession.movieDetail = validDetail
                                ActivePlayerSession.customStream = null
                                ActiveEpisodeHolder.episode = targetEpisode

                                navController.navigate("streams/$activeProviderId/$safeToken")
                            }
                        } else if (isStremio) {
                            // Tự động phân giải nhanh (Auto-resolve) stream tốt nhất trong tối đa 800ms
                            var bestStream: UnifiedStreamItem? = null
                            try {
                                kotlinx.coroutines.withTimeoutOrNull(800L) {
                                    val aggregator = StreamAggregator(pluginManager)
                                    aggregator.aggregateStreams(
                                        providerId = activeProviderId,
                                        movieDetail = validDetail,
                                        episode = targetEpisode
                                    ).collect { streamList ->
                                        if (streamList.isNotEmpty()) {
                                            bestStream = streamList.firstOrNull()
                                            if (bestStream != null) {
                                                cancel()
                                            }
                                        }
                                    }
                                }
                            } catch (_: Exception) { }

                            withContext(Dispatchers.Main) {
                                ActivePlayerSession.providerId = activeProviderId
                                ActivePlayerSession.movieSlug = activeMovieSlug
                                ActivePlayerSession.movieName = validDetail.name
                                ActivePlayerSession.movieThumb = validDetail.backdropUrl ?: validDetail.posterUrl ?: history.thumbUrl
                                ActivePlayerSession.tmdbId = validDetail.tmdbId
                                ActivePlayerSession.imdbId = validDetail.imdbId
                                ActivePlayerSession.originName = validDetail.originName
                                ActivePlayerSession.movieYear = validDetail.year
                                ActivePlayerSession.availableServers = validDetail.episodes
                                ActivePlayerSession.currentServerName = targetServer.serverName
                                ActivePlayerSession.isDescending = false
                                ActivePlayerSession.episodes = sortedTargetEps
                                ActivePlayerSession.currentEpisodeIndex = targetIndex
                                ActivePlayerSession.currentEpisode = targetEpisode
                                ActivePlayerSession.rawPluginName = validDetail.name
                                ActivePlayerSession.rawPluginOriginName = validDetail.originName
                                ActivePlayerSession.movieDetail = validDetail

                                if (bestStream != null) {
                                    ActivePlayerSession.customStream = bestStream!!.toEpisodeStream()
                                    ActiveEpisodeHolder.customStream = bestStream!!.toEpisodeStream()
                                    if (bestStream!!.targetEpisode != null) {
                                        ActiveEpisodeHolder.episode = bestStream!!.targetEpisode
                                    } else {
                                        ActiveEpisodeHolder.episode = targetEpisode
                                    }
                                    navController.navigate("player/$activeProviderId/$safeToken")
                                } else {
                                    // Không tìm thấy kịp trong 2.5s -> fallback sang màn hình chọn luồng phát
                                    ActivePlayerSession.customStream = null
                                    ActiveEpisodeHolder.episode = targetEpisode
                                    navController.navigate("streams/$activeProviderId/$safeToken")
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                ActivePlayerSession.providerId = activeProviderId
                                ActivePlayerSession.movieSlug = activeMovieSlug
                                ActivePlayerSession.movieName = validDetail.name
                                ActivePlayerSession.movieThumb = validDetail.backdropUrl ?: validDetail.posterUrl ?: history.thumbUrl
                                ActivePlayerSession.tmdbId = validDetail.tmdbId
                                ActivePlayerSession.imdbId = validDetail.imdbId
                                ActivePlayerSession.originName = validDetail.originName
                                ActivePlayerSession.movieYear = validDetail.year
                                ActivePlayerSession.availableServers = validDetail.episodes
                                ActivePlayerSession.currentServerName = targetServer.serverName
                                ActivePlayerSession.isDescending = false
                                ActivePlayerSession.episodes = sortedTargetEps
                                ActivePlayerSession.currentEpisodeIndex = targetIndex
                                ActivePlayerSession.currentEpisode = targetEpisode
                                ActivePlayerSession.rawPluginName = validDetail.name
                                ActivePlayerSession.rawPluginOriginName = validDetail.originName
                                ActivePlayerSession.movieDetail = validDetail
                                ActivePlayerSession.customStream = null
                                ActiveEpisodeHolder.episode = targetEpisode

                                navController.navigate("player/$activeProviderId/$safeToken")
                            }
                        }
                    } else {
                        // Không tìm thấy tập cụ thể -> Fallback an toàn sang DetailScreen
                        withContext(Dispatchers.Main) {
                            navController.navigate("detail/$activeProviderId/${URLEncoder.encode(activeMovieSlug, "UTF-8")}")
                        }
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.home_cannot_resume_movie), Toast.LENGTH_SHORT).show()
                    }
                }
                }
            }
        }
    }

    val resumePlaybackFromHistory: (HistoryEntity) -> Unit = remember(openPlaybackFromHistory) {
        { history -> openPlaybackFromHistory(history, false) }
    }

    val openStreamSelectionFromHistory: (HistoryEntity) -> Unit = remember(openPlaybackFromHistory) {
        { history -> openPlaybackFromHistory(history, true) }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onMovieClick = { providerId, slug ->
                        navController.navigate("detail/$providerId/${URLEncoder.encode(slug, "UTF-8")}")
                    },
                    onCategoryClick = { providerId, section ->
                        ActiveCategoryHolder.initialMovies = section.items
                        val encodedTitle = URLEncoder.encode(section.title, "UTF-8")
                        val typeVal = section.type?.let { URLEncoder.encode(it, "UTF-8") } ?: ""
                        val slugVal = section.slug?.let { URLEncoder.encode(it, "UTF-8") } ?: ""
                        navController.navigate("category/$providerId/$encodedTitle?type=$typeVal&slug=$slugVal")
                    },
                    onWatchHistoryClick = {
                        navController.navigate("history")
                    },
                    onResumePlayback = resumePlaybackFromHistory,
                    onSelectStream = openStreamSelectionFromHistory,
                    onSearchClick = {
                        navController.navigate(Screen.Search.route)
                    },
                    onOpenPluginsClick = {
                        navController.navigate(Screen.Plugins.route)
                    }
                )
            }
            composable("history") {
                WatchHistoryScreen(
                    onBack = { navController.popBackStack() },
                    onMovieClick = { providerId, slug ->
                        navController.navigate("detail/$providerId/${URLEncoder.encode(slug, "UTF-8")}")
                    },
                    onResumePlayback = resumePlaybackFromHistory,
                    onSelectStream = openStreamSelectionFromHistory
                )
            }
            composable(
                route = "category/{providerId}/{categoryTitle}?type={type}&slug={slug}",
                arguments = listOf(
                    navArgument("providerId") { type = NavType.StringType },
                    navArgument("categoryTitle") { type = NavType.StringType },
                    navArgument("type") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("slug") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { entry ->
                val providerId = entry.arguments?.getString("providerId") ?: ""
                val rawTitle = entry.arguments?.getString("categoryTitle") ?: ""
                val categoryTitle = URLDecoder.decode(rawTitle, "UTF-8")
                val rawType = entry.arguments?.getString("type")?.ifBlank { null }
                val type = rawType?.let { URLDecoder.decode(it, "UTF-8") }
                val rawSlug = entry.arguments?.getString("slug")?.ifBlank { null }
                val slug = rawSlug?.let { URLDecoder.decode(it, "UTF-8") }

                MovieListScreen(
                    providerId = providerId,
                    categoryTitle = categoryTitle,
                    type = type,
                    slug = slug,
                    initialMovies = ActiveCategoryHolder.initialMovies,
                    onBack = { navController.popBackStack() },
                    onMovieClick = { pId, s ->
                        navController.navigate("detail/$pId/${URLEncoder.encode(s, "UTF-8")}")
                    }
                )
            }
            composable(
                route = "search?query={query}",
                arguments = listOf(
                    navArgument("query") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { entry ->
                val rawQuery = entry.arguments?.getString("query")
                val initialQuery = rawQuery?.let {
                    try {
                        URLDecoder.decode(it, "UTF-8")
                    } catch (_: Exception) {
                        it
                    }
                }
                SearchScreen(
                    initialQuery = initialQuery,
                    onMovieClick = { providerId, slug ->
                        navController.navigate("detail/$providerId/${URLEncoder.encode(slug, "UTF-8")}")
                    },
                    onOpenPluginsClick = {
                        navController.navigate(Screen.Plugins.route)
                    }
                )
            }
            composable(Screen.Favorites.route) {
                FavoritesScreen(
                    onMovieClick = { providerId, slug ->
                        navController.navigate("detail/$providerId/${URLEncoder.encode(slug, "UTF-8")}")
                    },
                    onExploreClick = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Screen.Plugins.route) {
                PluginsScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToAuxServices = {
                        navController.navigate("settings/aux_services")
                    }
                )
            }
            composable("settings/aux_services") {
                AuxServicesSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
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
                    onMovieClick = { pId, s ->
                        if (pId == "tmdb") {
                            val encoded = URLEncoder.encode(s, "UTF-8")
                            navController.navigate("search?query=$encoded")
                        } else {
                            navController.navigate("detail/$pId/${URLEncoder.encode(s, "UTF-8")}")
                        }
                    },
                    onSearchMovie = { query ->
                        val encoded = URLEncoder.encode(query, "UTF-8")
                        navController.navigate("search?query=$encoded")
                    },
                    onPlayEpisode = { episode ->
                        ActiveEpisodeHolder.episode = episode
                        ActivePlayerSession.aggregatedStreams = emptyList()
                        ActivePlayerSession.activeUnifiedStream = null
                        val jsonString = Json.encodeToString(EpisodeData.serializer(), episode)
                        val safeToken = android.util.Base64.encodeToString(
                            jsonString.toByteArray(Charsets.UTF_8),
                            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                        )
                        val isComicOrNovel = JkhxApplication.instance.pluginManager.isComicProvider(providerId) || JkhxApplication.instance.pluginManager.isNovelProvider(providerId)
                        if (isComicOrNovel) {
                            navController.navigate("reader/$providerId/$safeToken")
                        } else {
                            navController.navigate("streams/$providerId/$safeToken")
                        }
                    }
                )
            }

            composable(
                route = "streams/{providerId}/{episodeJson}",
                arguments = listOf(
                    navArgument("providerId") { type = NavType.StringType },
                    navArgument("episodeJson") { type = NavType.StringType }
                )
            ) { entry ->
                val providerId = entry.arguments?.getString("providerId") ?: ""
                val safeToken = entry.arguments?.getString("episodeJson") ?: ""
                val episode = ActiveEpisodeHolder.episode ?: run {
                    try {
                        val decodedBytes = android.util.Base64.decode(
                            safeToken,
                            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                        )
                        Json.decodeFromString(EpisodeData.serializer(), String(decodedBytes, Charsets.UTF_8))
                    } catch (e: Exception) {
                        EpisodeData(id = safeToken, name = "Tập phim", slug = safeToken)
                    }
                }
                val movieDetail = ActivePlayerSession.movieDetail ?: MovieDetail(
                    slug = ActivePlayerSession.movieSlug,
                    name = ActivePlayerSession.movieName,
                    originName = ActivePlayerSession.originName ?: ActivePlayerSession.movieName,
                    type = ActivePlayerSession.movieType,
                    posterUrl = ActivePlayerSession.movieThumb,
                    backdropUrl = ActivePlayerSession.movieThumb,
                    episodes = ActivePlayerSession.availableServers
                )

                StreamSelectionScreen(
                    providerId = providerId,
                    movieDetail = movieDetail,
                    episode = episode,
                    onBack = { navController.popBackStack() },
                    onStreamSelected = { selectedStream ->
                        val epStream = selectedStream.toEpisodeStream()
                        ActivePlayerSession.providerId = selectedStream.providerId
                        ActivePlayerSession.currentServerName = selectedStream.serverName
                        ActivePlayerSession.customStream = epStream
                        ActivePlayerSession.activeUnifiedStream = selectedStream
                        ActiveEpisodeHolder.customStream = epStream
                        val nextEp = selectedStream.targetEpisode ?: episode
                        ActiveEpisodeHolder.episode = nextEp
                        val epJson = Json.encodeToString(EpisodeData.serializer(), nextEp)
                        val nextSafeToken = android.util.Base64.encodeToString(
                            epJson.toByteArray(Charsets.UTF_8),
                            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                        )
                        navController.navigate("player/${selectedStream.providerId}/$nextSafeToken")
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
                val safeToken = entry.arguments?.getString("episodeJson") ?: ""
                val episode = ActiveEpisodeHolder.episode ?: run {
                    try {
                        val decodedBytes = android.util.Base64.decode(
                            safeToken,
                            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                        )
                        Json.decodeFromString(EpisodeData.serializer(), String(decodedBytes, Charsets.UTF_8))
                    } catch (e: Exception) {
                        EpisodeData(id = safeToken, name = "Tập phim", slug = safeToken)
                    }
                }

                PlayerScreen(
                    providerId = providerId,
                    episode = episode,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "reader/{providerId}/{episodeJson}",
                arguments = listOf(
                    navArgument("providerId") { type = NavType.StringType },
                    navArgument("episodeJson") { type = NavType.StringType }
                )
            ) { entry ->
                val providerId = entry.arguments?.getString("providerId") ?: ""
                val safeToken = entry.arguments?.getString("episodeJson") ?: ""
                val episode = ActiveEpisodeHolder.episode ?: run {
                    try {
                        val decodedBytes = android.util.Base64.decode(
                            safeToken,
                            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                        )
                        Json.decodeFromString(EpisodeData.serializer(), String(decodedBytes, Charsets.UTF_8))
                    } catch (e: Exception) {
                        EpisodeData(id = safeToken, name = "Chương", slug = safeToken)
                    }
                }

                ReaderScreen(
                    providerId = providerId,
                    episode = episode,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }

