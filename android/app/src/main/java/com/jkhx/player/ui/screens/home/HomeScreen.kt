package com.jkhx.player.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.res.painterResource
import com.jkhx.player.R
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.jkhx.player.JkhxApplication
import com.jkhx.player.core.database.entity.HistoryEntity
import com.jkhx.player.core.cache.SessionCacheManager
import com.jkhx.player.core.model.HomeSection
import com.jkhx.player.core.model.MenuItem
import com.jkhx.player.core.model.MovieItem
import com.jkhx.player.plugin.manager.InstalledPluginRecord
import com.jkhx.player.settings.AppThemeMode
import com.jkhx.player.settings.DeviceDisplayMode
import com.jkhx.player.ui.components.ContinueWatchingCard
import com.jkhx.player.ui.components.MovieCard
import com.jkhx.player.ui.theme.AppTheme
import com.jkhx.player.ui.theme.LocalDisplayMode
import com.jkhx.player.ui.theme.ShapeTokens
import com.jkhx.player.ui.theme.tvFocusableItem
import com.jkhx.player.ui.theme.PluginCategoryAdult
import com.jkhx.player.ui.theme.PluginCategoryComic
import com.jkhx.player.ui.theme.PluginCategoryLive
import com.jkhx.player.ui.theme.PluginCategoryMovie
import com.jkhx.player.ui.theme.PluginCategoryNovel
import androidx.compose.ui.res.stringResource
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import com.jkhx.player.ui.screens.player.findActivity
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onMovieClick: (providerId: String, slug: String) -> Unit,
    onCategoryClick: (providerId: String, section: HomeSection) -> Unit = { _, _ -> },
    onWatchHistoryClick: () -> Unit = {},
    onResumePlayback: ((HistoryEntity) -> Unit)? = null,
    onSelectStream: ((HistoryEntity) -> Unit)? = null,
    onSearchClick: () -> Unit = {},
    onOpenPluginsClick: () -> Unit = {}
) {
    val pluginManager = JkhxApplication.instance.pluginManager
    val settingsManager = JkhxApplication.instance.settingsManager
    val themeMode by settingsManager.themeMode.collectAsState()
    val isAdultEnabled by settingsManager.isAdultEnabled.collectAsState()
    val isComicEnabled by settingsManager.isComicEnabled.collectAsState()
    val isNovelEnabled by settingsManager.isNovelEnabled.collectAsState()
    val isLiveEnabled by settingsManager.isLiveEnabled.collectAsState()
    val updateCount by pluginManager.pluginsUpdateCounter.collectAsState()
    val isPluginsInitialized by pluginManager.isInitialized.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val displayMode = LocalDisplayMode.current
    val isTablet = displayMode == DeviceDisplayMode.TABLET
    val context = LocalContext.current

    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    var enabledPlugins by remember { mutableStateOf<List<InstalledPluginRecord>>(emptyList()) }
    var selectedProviderId by rememberSaveable { mutableStateOf<String?>(settingsManager.lastSelectedProvider.value) }
    var lastLoadedProviderId by remember { mutableStateOf<String?>(null) }
    var showProviderDropdown by remember { mutableStateOf(false) }

    BackHandler {
        if (showProviderDropdown) {
            showProviderDropdown = false
            return@BackHandler
        }
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPressTime < 2000L) {
            (context.findActivity() ?: (context as? Activity))?.finish()
        } else {
            lastBackPressTime = currentTime
            Toast.makeText(context, context.getString(R.string.app_exit_confirm), Toast.LENGTH_SHORT).show()
        }
    }

    val sections = remember { mutableStateListOf<HomeSection>() }
    val quickCategories = remember { mutableStateListOf<HomeSection>() }
    val menuItems = remember { mutableStateListOf<MenuItem>() }
    var expandedMenuIndex by remember { mutableStateOf<Int?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedMenuIndex by remember { mutableStateOf(0) }

    val historyDao = remember { JkhxApplication.instance.database.historyDao() }
    val rawWatchHistory by historyDao.getAllHistory().collectAsState(initial = emptyList())
    // Ẩn phim 18+, truyện, live khỏi danh sách đang xem khi chế độ phân nhóm bị tắt trong Cài đặt và gộp trùng phim khác nguồn (Universal Deduplication)
    val watchHistory = remember(rawWatchHistory, isAdultEnabled, isComicEnabled, isNovelEnabled, isLiveEnabled, updateCount) {
        val seen = mutableSetOf<String>()
        rawWatchHistory.filter { item ->
            val isVisible = pluginManager.isProviderVisible(
                pluginId = item.providerId,
                isAdultEnabled = isAdultEnabled,
                isComicEnabled = isComicEnabled,
                isNovelEnabled = isNovelEnabled,
                isLiveEnabled = isLiveEnabled
            )
            if (!isVisible) return@filter false

            val isComicOrNovel = pluginManager.isComicProvider(item.providerId) || pluginManager.isNovelProvider(item.providerId)
            val isLive = pluginManager.isLiveProvider(item.providerId)
            if (isComicOrNovel || isLive) {
                return@filter seen.add(item.id)
            }

            // Gộp trùng thông minh tuyệt đối cho Movie / TV:
            // 1. Trích xuất IMDb ID (tt...) nếu có
            val imdbMatch = Regex("""(tt\d+)""").find(item.id)?.groupValues?.getOrNull(1)
                ?: Regex("""(tt\d+)""").find(item.movieSlug)?.groupValues?.getOrNull(1)
            // 2. Bóc tách Root Title: Tiền tố trước dấu ":" hoặc "-" (loại bỏ phụ đề tiếng Việt kèm theo)
            // Ví dụ: "Tomb Raider: Huyền Thoại Bắt Đầu" -> "tomb raider" trùng hoàn toàn với AIOStreams "Tomb Raider"
            val rawRoot = item.movieName.substringBefore(":").substringBefore("-").trim()
            val cleanRoot = rawRoot.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
            val fullCleanTitle = item.movieName.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()

            val primaryKey = if (!imdbMatch.isNullOrBlank()) "imdb:$imdbMatch" else null
            val rootKey = if (cleanRoot.length >= 3) "root_title:$cleanRoot" else null
            val titleKey = if (fullCleanTitle.isNotBlank()) "title:$fullCleanTitle" else null

            // Kiểm tra xem đã có bản ghi nào cùng phim xuất hiện trước đó chưa
            if (primaryKey != null && seen.contains(primaryKey)) return@filter false
            if (rootKey != null && seen.contains(rootKey)) return@filter false
            if (titleKey != null && seen.contains(titleKey)) return@filter false
            if (seen.contains(item.id)) return@filter false

            // Đánh dấu các key đại diện cho bộ phim này
            if (primaryKey != null) seen.add(primaryKey)
            if (rootKey != null) seen.add(rootKey)
            if (titleKey != null) seen.add(titleKey)
            seen.add(item.id)
            true
        }
    }

    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    var loadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var selectedHistoryForAction by remember { mutableStateOf<HistoryEntity?>(null) }

    // Hiệu ứng xoay mượt mà cho nút Refresh — xoay tròn đều khi tải, xoay nốt vòng và hạ cánh êm dịu khi hoàn thành
    val rotationAnimatable = remember { androidx.compose.animation.core.Animatable(0f) }
    val rotationAngle by rotationAnimatable.asState()
    LaunchedEffect(isLoading) {
        if (isLoading) {
            while (isActive) {
                val nextTarget = rotationAnimatable.value + 360f
                rotationAnimatable.animateTo(
                    targetValue = nextTarget,
                    animationSpec = tween(durationMillis = 900, easing = LinearEasing)
                )
            }
        } else if (rotationAnimatable.value > 0f) {
            // Khi kết thúc tải: tiếp tục xoay mượt nốt vòng quay đến 360 độ gần nhất rồi hạ cánh êm dịu
            val current = rotationAnimatable.value
            val nextFull = kotlin.math.ceil(current / 360f) * 360f
            val remaining = nextFull - current
            if (remaining > 5f) {
                val animDuration = ((remaining / 360f) * 600).toInt().coerceIn(150, 600)
                rotationAnimatable.animateTo(
                    targetValue = nextFull,
                    animationSpec = tween(durationMillis = animDuration, easing = FastOutSlowInEasing)
                )
            }
            rotationAnimatable.snapTo(0f)
        }
    }

    // 1. Load feed whenever selected provider changes with task cancellation & cache support
    fun loadProviderFeed(providerId: String?, forceRefresh: Boolean = false) {
        val targetId = providerId?.ifBlank { null }
            ?: selectedProviderId?.ifBlank { null }
            ?: settingsManager.lastSelectedProvider.value?.ifBlank { null }
            ?: enabledPlugins.firstOrNull()?.manifest?.id
            ?: if (pluginManager.isReady) {
                pluginManager.getInstalledRecords().firstOrNull {
                    it.isEnabled &&
                    (isAdultEnabled || (!it.manifest.isAdult && it.manifest.categoryType != "adult")) &&
                    (isComicEnabled || (!it.manifest.isComic && it.manifest.categoryType != "comic")) &&
                    (isNovelEnabled || (!it.manifest.isNovel && it.manifest.categoryType != "novel")) &&
                    (isLiveEnabled || (!it.manifest.isLive && it.manifest.categoryType != "live"))
                }?.manifest?.id
            } else null

        if (targetId.isNullOrBlank()) {
            loadJob?.cancel()
            sections.clear()
            quickCategories.clear()
            menuItems.clear()
            selectedMenuIndex = 0
            expandedMenuIndex = null
            isLoading = false
            return
        }

        // Đánh dấu provider đã được gọi nạp để LaunchedEffect(selectedProviderId) không gọi lặp
        lastLoadedProviderId = targetId

        if (selectedProviderId != targetId) {
            selectedProviderId = targetId
        }
        if (enabledPlugins.any { it.manifest.id == targetId }) {
            settingsManager.setLastSelectedProvider(targetId)
        }

        // KIỂM TRA SESSION CACHE:
        // Nếu không yêu cầu làm mới (forceRefresh = false) và đã có trong SessionCacheManager:
        // Hiển thị ngay lập tức (0ms) mà không gọi mạng hay chạy vòng xoay loading!
        if (!forceRefresh) {
            val cached = SessionCacheManager.getHomeFeed(targetId)
            if (cached != null && cached.sections.isNotEmpty()) {
                loadJob?.cancel()
                sections.clear()
                sections.addAll(cached.sections)
                quickCategories.clear()
                quickCategories.addAll(cached.quickCategories)
                menuItems.clear()
                menuItems.addAll(cached.menuItems)
                selectedMenuIndex = 0
                expandedMenuIndex = null
                isLoading = false
                errorMessage = null
                return
            }
        } else {
            SessionCacheManager.removeHomeFeed(targetId)
        }

        // NẾU KHÔNG CÓ TRONG CACHE HOẶC LÀM MỚI (forceRefresh):
        // Xóa ngay lập tức dữ liệu cũ (0ms Transition) để không hiển thị UI cũ của plugin trước!
        loadJob?.cancel()
        sections.clear()
        quickCategories.clear()
        menuItems.clear()
        selectedMenuIndex = 0
        expandedMenuIndex = null
        isLoading = true
        errorMessage = null

        val currentName = enabledPlugins.find { it.manifest.id == targetId }?.manifest?.name ?: targetId
        loadJob = coroutineScope.launch {
            try {
                val plugin = pluginManager.getPlugin(targetId)
                if (plugin == null) {
                    if (!pluginManager.isReady) {
                        // PluginManager vẫn đang khởi tạo ở background, giữ trạng thái loading chờ LaunchedEffect
                        isLoading = true
                        return@launch
                    }
                    errorMessage = context.getString(R.string.home_provider_not_ready, targetId)
                    isLoading = false
                    return@launch
                }
                if (forceRefresh) {
                    plugin.clearCache()
                }

                // Cảnh báo trạng thái nguồn: kiểm tra bất đồng bộ checkHealth()
                launch {
                    try {
                        val health = plugin.checkHealth()
                        if (health != null && (health.status == "blocked" || health.status == "degraded")) {
                            val warn = health.message ?: if (health.status == "blocked") {
                                context.getString(R.string.home_source_warning_blocked)
                            } else {
                                context.getString(R.string.home_source_warning_degraded)
                            }
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.home_source_warning_toast, currentName, warn),
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    } catch (_: Exception) {}
                }

                // Row card và quick buttons lấy từ getHome
                val homeResult = plugin.getHome()
                val validSections = homeResult.sections.filter { it.items.isNotEmpty() }
                sections.clear()
                sections.addAll(validSections)

                quickCategories.clear()
                if (homeResult.quickCategories.isNotEmpty()) {
                    quickCategories.addAll(homeResult.quickCategories)
                } else {
                    quickCategories.addAll(validSections)
                }

                // Tự động làm giàu metadata TMDB cho các phim trên Hero Banner (áp dụng cho nguồn Phim)
                val firstSection = validSections.firstOrNull()
                val isMovieOrSeriesProvider = !pluginManager.isAdultProvider(targetId) &&
                    !pluginManager.isComicProvider(targetId) &&
                    !pluginManager.isNovelProvider(targetId) &&
                    !pluginManager.isLiveProvider(targetId)
                if (firstSection != null && firstSection.items.isNotEmpty() && isMovieOrSeriesProvider) {
                    launch {
                        try {
                            val heroCandidates = firstSection.items.take(8)
                            val enrichedHero = coroutineScope {
                                heroCandidates.map { item ->
                                    async {
                                        val itemRating = item.rating
                                        if (!item.tmdbId.isNullOrBlank() || itemRating == null || itemRating <= 0.0) {
                                            com.jkhx.player.JkhxApplication.instance.tmdbRepository.enrichMovieItem(item)
                                        } else {
                                            item
                                        }
                                    }
                                }.awaitAll()
                            }
                            if (sections.isNotEmpty()) {
                                val remaining = if (firstSection.items.size > 8) firstSection.items.drop(8) else emptyList()
                                sections[0] = firstSection.copy(items = enrichedHero + remaining)
                                SessionCacheManager.putHomeFeed(
                                    providerId = targetId,
                                    sections = sections.toList(),
                                    quickCategories = quickCategories.toList(),
                                    menuItems = menuItems.toList()
                                )
                            }
                        } catch (_: Exception) {}
                    }
                }

                if (sections.isEmpty()) {
                    errorMessage = context.getString(R.string.home_source_maintenance)
                }

                // Hàng menu trên cùng lấy từ getMenu của plugin (hỗ trợ cache in-memory)
                val remoteMenu = try { plugin.getMenu(forceRefresh = forceRefresh) } catch (e: Exception) { null }
                menuItems.clear()
                if (!remoteMenu.isNullOrEmpty()) {
                    menuItems.addAll(remoteMenu)
                } else {
                    // Fallback nếu plugin không hỗ trợ getMenu
                    menuItems.addAll(sections.map { sec ->
                        MenuItem(
                            title = sec.title,
                            isDropdown = false,
                            prefix = sec.type,
                            type = sec.type,
                            slug = sec.slug
                        )
                    })
                }
                selectedMenuIndex = 0
                expandedMenuIndex = null

                // Lưu vào SessionCacheManager để tái sử dụng tức thì trong phiên
                if (sections.isNotEmpty()) {
                    SessionCacheManager.putHomeFeed(
                        providerId = targetId,
                        sections = sections.toList(),
                        quickCategories = quickCategories.toList(),
                        menuItems = menuItems.toList()
                    )
                }

                if (forceRefresh) {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.home_refreshed_toast, currentName),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Tác vụ đã bị hủy khi chuyển provider hoặc bấm làm mới
                throw e
            } catch (e: Exception) {
                val err = e.message ?: context.getString(R.string.common_error)
                errorMessage = err
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.home_source_conn_error_toast, currentName, err),
                    android.widget.Toast.LENGTH_LONG
                ).show()
                if (forceRefresh) {
                    snackbarHostState.showSnackbar(context.getString(R.string.home_refresh_error, err))
                }
            } finally {
                isLoading = false
            }
        }
    }

    // 2. Sync enabled plugins list respecting Safe Mode / 18+, Comic, Novel and Live filter
    LaunchedEffect(isPluginsInitialized, updateCount, isAdultEnabled, isComicEnabled, isNovelEnabled, isLiveEnabled) {
        val records = pluginManager.getInstalledRecords()
            .filter {
                it.isEnabled &&
                (!it.manifest.isStremioAddon || it.manifest.hasCatalogs) &&
                (isAdultEnabled || !it.manifest.isAdult) &&
                (isComicEnabled || !it.manifest.isComic) &&
                (isNovelEnabled || (!it.manifest.isNovel && it.manifest.categoryType != "novel")) &&
                (isLiveEnabled || (!it.manifest.isLive && it.manifest.categoryType != "live"))
            }
        enabledPlugins = records

        // Nếu PluginManager chưa khởi tạo xong hoặc danh sách rỗng, không vội fallback
        if (!isPluginsInitialized || records.isEmpty()) {
            return@LaunchedEffect
        }

        val savedProvider = settingsManager.lastSelectedProvider.value
        // Tìm provider mục tiêu: Ưu tiên savedProvider -> selectedProviderId -> records.firstOrNull()
        val target = records.find { it.manifest.id == savedProvider }
            ?: records.find { it.manifest.id == selectedProviderId }
            ?: records.firstOrNull()

        val targetId = target?.manifest?.id
        if (!targetId.isNullOrBlank()) {
            if (selectedProviderId != targetId) {
                selectedProviderId = targetId
            }
            if (targetId != savedProvider) {
                settingsManager.setLastSelectedProvider(targetId)
            }
            // Tự động tải feed khi plugins vừa sẵn sàng nếu chưa được nạp hoặc danh sách feed đang trống
            if (lastLoadedProviderId != targetId || sections.isEmpty()) {
                loadProviderFeed(targetId, forceRefresh = false)
            }
        }
    }

    LaunchedEffect(selectedProviderId) {
        val pid = selectedProviderId
        if (!pid.isNullOrBlank() && pid != lastLoadedProviderId && pluginManager.isReady) {
            loadProviderFeed(pid)
        }
    }

    // Listen to Home button tap from Navigation Bar / Navigation Rail
    LaunchedEffect(Unit) {
        com.jkhx.player.HomeRefreshNotifier.reloadTrigger.collect {
            listState.animateScrollToItem(0)
            val activeId = selectedProviderId ?: enabledPlugins.firstOrNull()?.manifest?.id
            loadProviderFeed(activeId, forceRefresh = true)
        }
    }

    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 60
        }
    }
    val topBarAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 0.96f else 0f,
        animationSpec = tween(220),
        label = "topBarAlpha"
    )
    val surfaceColor = MaterialTheme.colorScheme.surface
    val isLightTheme = themeMode == AppThemeMode.LIGHT
    val topGradient = remember(surfaceColor, isLightTheme) {
        if (isLightTheme) {
            Brush.verticalGradient(
                listOf(
                    surfaceColor.copy(alpha = 0.96f),
                    surfaceColor.copy(alpha = 0.80f),
                    surfaceColor.copy(alpha = 0.0f)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color(0xEE000000),
                    Color(0x88000000),
                    Color.Transparent
                )
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // --- LỚP 1: NỘI DUNG CUỘN (TỪ ĐỈNH MÀN HÌNH LIỀN MẠCH VỚI HERO BANNER) ---
            if (enabledPlugins.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.home_empty_plugins_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.home_empty_plugins_desc),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onOpenPluginsClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = ShapeTokens.Button
                    ) {
                        Text(stringResource(R.string.home_open_plugins_btn))
                    }
                }
            } else if (isLoading && sections.isEmpty()) {
                HomeSkeletonLoading(
                    isTablet = isTablet
                )
            } else if (errorMessage != null && sections.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = errorMessage ?: stringResource(R.string.common_error),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val activeProviderId = selectedProviderId
                            ?: enabledPlugins.firstOrNull()?.manifest?.id
                        Button(
                            onClick = { loadProviderFeed(activeProviderId, forceRefresh = true) },
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = ShapeTokens.Button
                        ) {
                            Text(stringResource(R.string.common_retry))
                        }
                        OutlinedButton(
                            onClick = onOpenPluginsClick,
                            shape = ShapeTokens.Button
                        ) {
                            Text(stringResource(R.string.home_manage_plugins_btn))
                        }
                    }
                }
            } else if (sections.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.home_source_maintenance),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    val activeProviderId = selectedProviderId
                        ?: enabledPlugins.firstOrNull()?.manifest?.id
                    Button(
                        onClick = { loadProviderFeed(activeProviderId, forceRefresh = true) },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = ShapeTokens.Button
                    ) {
                        Text(stringResource(R.string.home_refresh_btn))
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    // Item 0: Hero Banner (8 movies from first section of getHome)
                    val heroMovies = sections.firstOrNull()?.items?.distinctBy { it.slug }?.take(8) ?: emptyList()
                    if (heroMovies.isNotEmpty()) {
                        item(key = "hero_banner") {
                            HeroBannerSection(
                                movies = heroMovies,
                                onMovieClick = { movie ->
                                    onMovieClick(movie.providerId ?: selectedProviderId ?: "", movie.slug)
                                }
                            )
                        }
                    } else {
                        item(key = "top_spacer") {
                            Spacer(modifier = Modifier.height(96.dp))
                        }
                    }

                    // Item 1: "Bạn đang quan tâm gì?" Quick Category Buttons (Dynamic from Plugin)
                    item(key = "quick_categories") {
                        QuickCategoriesSection(
                            sections = if (quickCategories.isNotEmpty()) quickCategories else sections,
                            onCategoryClick = { section ->
                                onCategoryClick(selectedProviderId ?: "", section)
                            }
                        )
                    }

                    // Item 2: Danh sách các phim đang xem (Continue Watching)
                    if (watchHistory.isNotEmpty()) {
                        item(key = "continue_watching") {
                            Column(modifier = Modifier.padding(vertical = AppTheme.dimens.space10)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onWatchHistoryClick() }
                                        .padding(horizontal = AppTheme.dimens.space16, vertical = AppTheme.dimens.space6)
                                ) {
                                    Text(
                                        text = stringResource(R.string.home_continue_watching),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 19.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = stringResource(R.string.home_view_more_watching),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = AppTheme.dimens.space16),
                                    horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space12)
                                ) {
                                    items(
                                        items = watchHistory,
                                        key = { it.id },
                                        contentType = { "watching_card" }
                                    ) { historyItem ->
                                        val providerName = remember(historyItem.providerId) {
                                            pluginManager.getPluginName(historyItem.providerId)
                                        }
                                        ContinueWatchingCard(
                                            history = historyItem,
                                            sourceBadge = providerName,
                                            modifier = Modifier.width(if (isTablet) AppTheme.dimens.cardWidthTablet else AppTheme.dimens.cardWidthMobile),
                                            onClick = {
                                                if (onResumePlayback != null) {
                                                    onResumePlayback(historyItem)
                                                } else {
                                                    onMovieClick(historyItem.providerId, historyItem.movieSlug)
                                                }
                                            },
                                            onLongClick = {
                                                selectedHistoryForAction = historyItem
                                            },
                                            onDelete = {
                                                coroutineScope.launch {
                                                    historyDao.delete(historyItem.id)
                                                    val isComicOrNovel = pluginManager.isComicProvider(historyItem.providerId) || pluginManager.isNovelProvider(historyItem.providerId)
                                                    val isLive = pluginManager.isLiveProvider(historyItem.providerId)
                                                    if (!isComicOrNovel && !isLive && historyItem.movieName.isNotBlank()) {
                                                        historyDao.deleteByName(historyItem.movieName)
                                                    }
                                                    com.jkhx.player.sync.SyncManager.getInstance(context).onHistoryDeleted(historyItem.id)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Items 3+: Movie Sections
                    itemsIndexed(sections, key = { index, section -> "${section.title}_$index" }) { index, section ->
                        Column(modifier = Modifier.padding(vertical = AppTheme.dimens.space10)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onCategoryClick(selectedProviderId ?: "", section)
                                    }
                                    .padding(horizontal = AppTheme.dimens.space16, vertical = AppTheme.dimens.space6)
                            ) {
                                Text(
                                    text = section.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 19.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = stringResource(R.string.home_view_more_section, section.title),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            val uniqueItems = remember(section.items) { section.items.distinctBy { it.slug } }
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = AppTheme.dimens.space16),
                                horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space12)
                            ) {
                                itemsIndexed(
                                    items = uniqueItems,
                                    key = { index, movie -> "${movie.providerId ?: selectedProviderId}_${movie.slug}_$index" },
                                    contentType = { _, _ -> "movie_card" }
                                ) { _, movie ->
                                    val providerName = remember(movie.providerId, selectedProviderId) {
                                        pluginManager.getPluginName(movie.providerId ?: selectedProviderId ?: "")
                                    }
                                    MovieCard(
                                        movie = movie,
                                        sourceBadge = providerName,
                                        modifier = Modifier.width(if (isTablet) AppTheme.dimens.cardWidthTablet else AppTheme.dimens.cardWidthMobile),
                                        onClick = {
                                            onMovieClick(movie.providerId ?: selectedProviderId ?: "", movie.slug)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- LỚP 2: FLOATING TRANSPARENT TOPBAR (ĐÈ LÊN TRÊN LIỀN MẠCH NHƯ APP VAX) ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .drawBehind {
                        val alpha = topBarAlpha
                        if (alpha > 0f) {
                            drawRect(color = surfaceColor.copy(alpha = alpha))
                        }
                        if (alpha < 0.96f) {
                            val gradientAlpha = (1f - (alpha / 0.96f)).coerceIn(0f, 1f)
                            if (gradientAlpha > 0f) {
                                drawRect(brush = topGradient, alpha = gradientAlpha)
                            }
                        }
                    }
                    .statusBarsPadding()
            ) {
                // TopBar Controls Row (~46dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Trên tablet ẩn logo & tiêu đề vì NavigationRail bên trái đã có
                    if (!isTablet) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_logo),
                                contentDescription = stringResource(R.string.home_logo_desc),
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(ShapeTokens.Chip)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
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
                                    fontSize = 18.5.sp,
                                    letterSpacing = 2.sp,
                                    brush = brandGradientBrush,
                                    shadow = Shadow(
                                        color = heroGlow,
                                        offset = Offset(0f, 0f),
                                        blurRadius = 14f
                                    )
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Provider Selector Chip (Gọn gàng như VAX)
                    val currentRecord = enabledPlugins.find { it.manifest.id == selectedProviderId }
                    val currentName = currentRecord?.manifest?.name ?: stringResource(R.string.home_select_provider)

                    Box {
                        Surface(
                            onClick = { showProviderDropdown = true },
                            shape = ShapeTokens.LargeChip,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .tvFocusableItem(cornerRadius = 18.dp, focusedScale = 1.05f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = currentName,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 130.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        ProviderSelectorModal(
                            visible = showProviderDropdown,
                            onDismissRequest = { showProviderDropdown = false },
                            enabledPlugins = enabledPlugins,
                            selectedProviderId = selectedProviderId,
                            onSelectProvider = { record ->
                                val newId = record.manifest.id
                                showProviderDropdown = false
                                if (newId != selectedProviderId) {
                                    selectedProviderId = newId
                                    settingsManager.setLastSelectedProvider(newId)
                                    loadProviderFeed(newId, forceRefresh = false)
                                } else {
                                    loadProviderFeed(newId, forceRefresh = true)
                                }
                            },
                            onOpenPluginsClick = {
                                showProviderDropdown = false
                                onOpenPluginsClick()
                            }
                        )
                    }

                    // Refresh button (Kích thước icon 24dp đồng bộ bottom bar)
                    val activeTopBarProviderId = selectedProviderId ?: enabledPlugins.firstOrNull()?.manifest?.id
                    IconButton(
                        onClick = { loadProviderFeed(activeTopBarProviderId, forceRefresh = true) },
                        enabled = !isLoading && enabledPlugins.isNotEmpty(),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.home_refresh_btn),
                            tint = if (isLoading) {
                                MaterialTheme.colorScheme.primary
                            } else if (enabledPlugins.isNotEmpty()) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(rotationAngle)
                        )
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    // Search button (Kích thước icon 24dp đồng bộ bottom bar)
                    IconButton(
                        onClick = onSearchClick,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.home_search_btn),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                if (isLoading && enabledPlugins.isNotEmpty()) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Category Menu Bar (Chữ trắng nổi bật trên nền trong suốt như app VAX)
                if (menuItems.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(menuItems) { index, menuItem ->
                            val isSelected = selectedMenuIndex == index
                            if (menuItem.hasDropdown) {
                                Box {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .tvFocusableItem(cornerRadius = 6.dp, focusedScale = 1.05f)
                                            .clickable {
                                                expandedMenuIndex = if (expandedMenuIndex == index) null else index
                                            }
                                            .padding(horizontal = 3.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = menuItem.title,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = expandedMenuIndex == index,
                                        onDismissRequest = { expandedMenuIndex = null },
                                        modifier = Modifier
                                            .heightIn(max = 360.dp)
                                            .background(MaterialTheme.colorScheme.surface)
                                    ) {
                                        menuItem.actualItems.forEach { subItem ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = subItem.displayTitle,
                                                        fontSize = 13.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                },
                                                onClick = {
                                                    expandedMenuIndex = null
                                                    selectedMenuIndex = index
                                                    onCategoryClick(
                                                        selectedProviderId ?: "",
                                                        HomeSection(
                                                            title = subItem.displayTitle,
                                                            type = menuItem.categoryType,
                                                            slug = subItem.slug
                                                        )
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .tvFocusableItem(cornerRadius = 6.dp, focusedScale = 1.05f)
                                        .clickable {
                                            selectedMenuIndex = index
                                            if (menuItem.slug == "home" || menuItem.slug.isNullOrBlank()) {
                                                coroutineScope.launch {
                                                    listState.animateScrollToItem(0)
                                                }
                                            } else {
                                                val matchingSectionIndex = sections.indexOfFirst {
                                                    it.title.equals(menuItem.title, ignoreCase = true) ||
                                                    (!it.slug.isNullOrBlank() && it.slug.equals(menuItem.slug, ignoreCase = true))
                                                }
                                                if (matchingSectionIndex >= 0) {
                                                    coroutineScope.launch {
                                                        val target = (matchingSectionIndex + 2).coerceAtMost(sections.size + 1)
                                                        listState.animateScrollToItem(target)
                                                    }
                                                } else {
                                                    onCategoryClick(
                                                        selectedProviderId ?: "",
                                                        HomeSection(
                                                            title = menuItem.title,
                                                            type = menuItem.categoryType,
                                                            slug = menuItem.slug
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                        .padding(horizontal = 3.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = menuItem.title,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Popup Context Menu chuẩn phong cách Nuvio khi nhấn giữ (long press) vào card Xem tiếp
        ContinueWatchingActionModal(
            history = selectedHistoryForAction,
            onDismissRequest = { selectedHistoryForAction = null },
            onOpenDetails = { item ->
                onMovieClick(item.providerId, item.movieSlug)
            },
            onSelectStream = { item ->
                if (onSelectStream != null) {
                    onSelectStream(item)
                } else {
                    onMovieClick(item.providerId, item.movieSlug)
                }
            },
            onRestartFromBeginning = { item ->
                com.jkhx.player.ActivePlayerSession.forceStartFromBeginning = true
                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    historyDao.upsert(item.copy(positionMs = 0L, updatedAt = System.currentTimeMillis()))
                    try {
                        val progressDao = com.jkhx.player.JkhxApplication.instance.database.playbackProgressDao()
                        if (!item.lastEpisodeId.isNullOrBlank()) {
                            progressDao.delete("${item.providerId}:${item.movieSlug}:${item.lastEpisodeId}")
                        }
                        progressDao.getAllProgressSync()
                            .filter { it.movieSlug == item.movieSlug }
                            .forEach { progressDao.delete(it.id) }
                    } catch (_: Exception) {}
                }
                if (onResumePlayback != null) {
                    onResumePlayback(item.copy(positionMs = 0L))
                } else {
                    onMovieClick(item.providerId, item.movieSlug)
                }
            },
            onDelete = { item ->
                coroutineScope.launch {
                    historyDao.delete(item.id)
                    val isComicOrNovel = pluginManager.isComicProvider(item.providerId) || pluginManager.isNovelProvider(item.providerId)
                    val isLive = pluginManager.isLiveProvider(item.providerId)
                    if (!isComicOrNovel && !isLive && item.movieName.isNotBlank()) {
                        historyDao.deleteByName(item.movieName)
                    }
                    com.jkhx.player.sync.SyncManager.getInstance(context).onHistoryDeleted(item.id)
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HeroBannerSection(
    movies: List<MovieItem>,
    onMovieClick: (MovieItem) -> Unit
) {
    if (movies.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { movies.size })
    val thumbnailListState = rememberLazyListState()
    val displayMode = LocalDisplayMode.current
    val isTablet = displayMode == DeviceDisplayMode.TABLET
    val currentMovie = movies.getOrNull(pagerState.currentPage) ?: movies.first()

    val coroutineScope = rememberCoroutineScope()
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    var isUserInteracting by remember { mutableStateOf(false) }
    var isHeroFocused by remember { mutableStateOf(false) }

    // Chiều cao thích ứng cho bố cục Adaptive Cinema Showcase
    val configuration = LocalConfiguration.current
    val bannerHeight = if (isTablet) 420.dp else (configuration.screenHeightDp.dp * 0.54f).coerceIn(405.dp, 460.dp)

    // Tự động cuộn dải Mini-Poster Thumbnail theo trang hiện tại
    LaunchedEffect(pagerState.currentPage) {
        val targetIndex = (pagerState.currentPage - 2).coerceAtLeast(0)
        thumbnailListState.animateScrollToItem(targetIndex)
    }

    // Tự động slide mỗi 5 giây, tạm dừng khi người dùng chạm/giữ, cuộn hoặc đang duyệt bằng Remote TV
    LaunchedEffect(movies.size, isDragged, isUserInteracting, isHeroFocused) {
        if (movies.size > 1 && !isDragged && !isUserInteracting && !isHeroFocused) {
            while (true) {
                delay(5000L)
                if (!pagerState.isScrollInProgress && !isDragged && !isUserInteracting && !isHeroFocused) {
                    val nextPage = (pagerState.currentPage + 1) % movies.size
                    pagerState.animateScrollToPage(nextPage)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(bannerHeight)
            .clipToBounds()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isUserInteracting = true
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        isUserInteracting = false
                    }
                }
            }
    ) {
        // --- 1. HorizontalPager hiển thị ảnh nền Backdrop mờ điện ảnh (hỗ trợ vuốt ngang toàn Banner) ---
        HorizontalPager(
            state = pagerState,
            key = { page -> movies.getOrNull(page)?.let { "${it.providerId ?: ""}_${it.slug}_$page" } ?: page },
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxSize()
                .focusGroup()
                .onFocusChanged { state ->
                    isHeroFocused = state.hasFocus || state.isFocused
                }
        ) { page ->
            val movie = movies[page]
            val heroContext = LocalContext.current
            val backdropUrl = movie.thumbUrl ?: movie.posterUrl

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onMovieClick(movie) }
            ) {
                if (!backdropUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = remember(backdropUrl) {
                            ImageRequest.Builder(heroContext)
                                .data(backdropUrl)
                                .size(1280)
                                .crossfade(false)
                                .allowHardware(true)
                                .memoryCacheKey(backdropUrl)
                                .build()
                        },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // --- 2. Lớp phủ Gradient Vignette đa hướng: Giữ nền trong trẻo sắc nét, bảo vệ tương phản chữ ---
        // Lớp đỉnh: Bảo vệ độ tương phản cho TopBar & Category Menu
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        0.0f to Color(0xB3000000),
                        0.55f to Color(0x44000000),
                        1.0f to Color.Transparent
                    )
                )
        )

        // Lớp bảo vệ chữ bên trái: Gradient ngang giúp chữ và chips luôn sắc nét mà không làm tối nền bên phải
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to Color(0xCC000000),
                        0.42f to Color(0x66000000),
                        0.72f to Color(0x15000000),
                        1.0f to Color.Transparent
                    )
                )
        )

        // Lớp đáy: Chuyển mượt xuống màu Background chính ở chân banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.45f to MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
                        0.80f to MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                        1.0f to MaterialTheme.colorScheme.background
                    )
                )
        )

        // --- 3. Bố cục Adaptive Cinema Showcase (Trái: Thông tin phim | Phải: Card Poster lớn | Đáy: Dải Mini-Poster full width) ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(
                    top = if (isTablet) 88.dp else 90.dp,
                    bottom = 8.dp
                ),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Khối chính ở giữa: 2 cột (Trái: Thông tin phim, Phải: Card poster lớn) căn giữa theo chiều dọc
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = if (isTablet) 24.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // CỘT TRÁI: Tiêu đề phim (nằm trên nhất, nếu dài thì xuống dòng) -> Tên gốc -> Hàng Chips bên dưới
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = if (isTablet) 24.dp else 14.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    // 1. Tiêu đề phim nằm trên nhất (nếu dài thì xuống dòng)
                    Text(
                        text = currentMovie.name,
                        fontWeight = FontWeight.Black,
                        fontSize = if (isTablet) 26.sp else 20.sp,
                        lineHeight = if (isTablet) 32.sp else 25.sp,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.85f),
                                offset = Offset(0f, 2f),
                                blurRadius = 8f
                            )
                        ),
                        modifier = Modifier.clickable { onMovieClick(currentMovie) }
                    )

                    // 2. Tên gốc (nếu có và khác tên hiển thị)
                    val originName = currentMovie.originName?.trim()
                    if (!originName.isNullOrBlank() && !originName.equals(currentMovie.name.trim(), ignoreCase = true)) {
                        Text(
                            text = originName,
                            fontSize = if (isTablet) 14.sp else 12.5.sp,
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Hàng Chips bên dưới Tiêu đề & Tên gốc
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 1. Điểm đánh giá [★ 8.5 TMDB] hoặc [★ 9.0 IMDb] (nếu có)
                        val movieRating = currentMovie.rating
                        if (movieRating != null && movieRating > 0.0) {
                            val isImdbSource = currentMovie.slug.contains(Regex("""tt\d+""")) && currentMovie.tmdbId.isNullOrBlank()
                            val ratingSourceLabel = if (isImdbSource) "IMDb" else "TMDB"
                            Surface(
                                color = Color(0x55000000),
                                shape = ShapeTokens.Chip,
                                border = BorderStroke(0.5.dp, Color(0xFFFFD700).copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "★ ${String.format(java.util.Locale.US, "%.1f", movieRating)} $ratingSourceLabel",
                                    fontSize = 11.sp,
                                    color = Color(0xFFFFD700),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // 2. Năm phát hành (VD: 2008)
                        val movieYear = currentMovie.year
                        if (movieYear != null && movieYear > 0) {
                            Surface(
                                color = Color(0x55000000),
                                shape = ShapeTokens.Chip,
                                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
                            ) {
                                Text(
                                    text = "$movieYear",
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // 3. Phân loại [Phim Lẻ / Phim Bộ]
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            shape = ShapeTokens.Chip,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = if (currentMovie.type == "series") stringResource(R.string.home_tag_series) else stringResource(R.string.home_tag_movie),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        // 4. Chất lượng [HD / FHD]
                        Surface(
                            color = Color(0x55000000),
                            shape = ShapeTokens.Chip,
                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            Text(
                                text = currentMovie.quality ?: stringResource(R.string.home_tag_hd),
                                fontSize = 11.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        // 5. Thể loại (nếu có)
                        if (!currentMovie.category.isNullOrBlank()) {
                            Surface(
                                color = Color(0x55000000),
                                shape = ShapeTokens.Chip,
                                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
                            ) {
                                Text(
                                    text = currentMovie.category!!,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                // CỘT PHẢI: CARD POSTER LỚN TỶ LỆ 2:3 CĂN GIỮA CHIỀU DỌC
                val posterContext = LocalContext.current
                val posterUrl = currentMovie.posterUrl ?: currentMovie.thumbUrl
                Card(
                    shape = RoundedCornerShape(if (isTablet) 16.dp else 14.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
                    modifier = Modifier
                        .fillMaxHeight(0.95f)
                        .aspectRatio(2f / 3f)
                        .border(
                            width = 1.5.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.55f),
                                    Color.White.copy(alpha = 0.15f)
                                )
                            ),
                            shape = RoundedCornerShape(if (isTablet) 16.dp else 14.dp)
                        )
                        .onFocusChanged { state ->
                            if (state.isFocused) isHeroFocused = true
                        }
                        .tvFocusableItem(cornerRadius = 16.dp, focusedScale = 1.05f)
                        .clickable { onMovieClick(currentMovie) }
                ) {
                    AsyncImage(
                        model = remember(posterUrl) {
                            ImageRequest.Builder(posterContext)
                                .data(posterUrl)
                                .size(600, 900)
                                .crossfade(true)
                                .allowHardware(true)
                                .memoryCacheKey(posterUrl)
                                .build()
                        },
                        contentDescription = currentMovie.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // DẢI 8 MINI-POSTER THUMBNAIL STRIP Ở ĐÁY HERO BANNER VÀ FULL CHIỀU RỘNG
            if (movies.size > 1) {
                Spacer(modifier = Modifier.height(8.dp))
                HeroThumbnailStrip(
                    movies = movies,
                    currentPage = pagerState.currentPage,
                    listState = thumbnailListState,
                    isTablet = isTablet,
                    onSelectPage = { index ->
                        if (pagerState.currentPage == index) {
                            onMovieClick(movies[index])
                        } else {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        }
                    },
                    onFocused = { isHeroFocused = it }
                )
            }
        }
    }
}

@Composable
private fun HeroThumbnailStrip(
    movies: List<MovieItem>,
    currentPage: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    isTablet: Boolean,
    onSelectPage: (Int) -> Unit,
    onFocused: (Boolean) -> Unit
) {
    val thumbContext = LocalContext.current
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = if (isTablet) 24.dp else 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
        itemsIndexed(
            items = movies,
            key = { index, item -> "hero_thumb_${item.slug}_$index" }
        ) { index, item ->
            val isSelected = currentPage == index
            val thumbUrl = item.posterUrl ?: item.thumbUrl
            val cardWidth = if (isTablet) {
                if (isSelected) 52.dp else 44.dp
            } else {
                if (isSelected) 44.dp else 37.dp
            }
            val cardHeight = if (isTablet) {
                if (isSelected) 74.dp else 62.dp
            } else {
                if (isSelected) 62.dp else 52.dp
            }

            Box(
                modifier = Modifier
                    .size(width = cardWidth, height = cardHeight)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = if (isSelected) 1.8.dp else 0.8.dp,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .alpha(if (isSelected) 1f else 0.52f)
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            onFocused(true)
                            onSelectPage(index)
                        }
                    }
                    .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.08f)
                    .clickable { onSelectPage(index) }
            ) {
                AsyncImage(
                    model = remember(thumbUrl) {
                        ImageRequest.Builder(thumbContext)
                            .data(thumbUrl)
                            .size(160, 240)
                            .crossfade(false)
                            .allowHardware(true)
                            .memoryCacheKey(thumbUrl)
                            .build()
                    },
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
}

@Composable
fun QuickCategoriesSection(
    sections: List<HomeSection>,
    onCategoryClick: (HomeSection) -> Unit
) {
    if (sections.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = stringResource(R.string.home_quick_categories_title),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        )

        val categoryGradients = listOf(
            listOf(Color(0xFFE91E63), Color(0xFFC2185B)),
            listOf(Color(0xFF5C6BC0), Color(0xFF3949AB)),
            listOf(Color(0xFF009688), Color(0xFF00796B)),
            listOf(Color(0xFFFF7043), Color(0xFFE64A19)),
            listOf(Color(0xFF8E24AA), Color(0xFF6A1B9A)),
            listOf(Color(0xFF0288D1), Color(0xFF01579B)),
            listOf(Color(0xFF43A047), Color(0xFF2E7D32)),
            listOf(Color(0xFFFB8C00), Color(0xFFEF6C00))
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(sections) { index, section ->
                val gradientColors = categoryGradients[index % categoryGradients.size]
                Surface(
                    onClick = { onCategoryClick(section) },
                    shape = ShapeTokens.Card,
                    color = gradientColors[0],
                    modifier = Modifier
                        .height(48.dp)
                        .tvFocusableItem(cornerRadius = 12.dp, focusedScale = 1.05f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .background(
                                androidx.compose.ui.graphics.Brush.horizontalGradient(gradientColors)
                            )
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = section.title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}



