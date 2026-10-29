package com.jkhx.player.ui.screens.search

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jkhx.player.JkhxApplication
import com.jkhx.player.R
import com.jkhx.player.core.cache.SessionCacheManager
import com.jkhx.player.core.database.entity.StremioAddonEntity
import com.jkhx.player.core.model.MovieItem
import com.jkhx.player.core.model.stremio.StremioCatalogDefinition
import com.jkhx.player.core.model.stremio.StremioManifest
import com.jkhx.player.plugin.manager.stremio.StremioPluginWrapper
import com.jkhx.player.settings.DeviceDisplayMode
import com.jkhx.player.ui.components.MovieCard
import com.jkhx.player.ui.theme.AppTheme
import com.jkhx.player.ui.theme.LocalDisplayMode
import com.jkhx.player.ui.theme.ShapeTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@OptIn(ExperimentalLayoutApi::class)
object ActiveSearchHolder {
    var query by mutableStateOf("")
    val searchResults = mutableStateListOf<MovieItem>()
    var selectedPluginId by mutableStateOf<String?>(null)
    var isSearching by mutableStateOf(false)

    // Discover cache
    var cachedStremioAddons by mutableStateOf<List<Pair<StremioAddonEntity, StremioManifest>>>(emptyList())
    val discoverMovies = mutableStateListOf<MovieItem>()
    var discoverPage by mutableIntStateOf(1)
    var hasMorePages by mutableStateOf(true)
    var lastCatalogKey by mutableStateOf("")

    fun clearSearch() {
        query = ""
        searchResults.clear()
        selectedPluginId = null
        isSearching = false
    }

    fun clearDiscoverCache() {
        cachedStremioAddons = emptyList()
        discoverMovies.clear()
        discoverPage = 1
        hasMorePages = true
        lastCatalogKey = ""
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    initialQuery: String? = null,
    onMovieClick: (providerId: String, slug: String) -> Unit,
    onOpenPluginsClick: (() -> Unit)? = null
) {
    val pluginManager = JkhxApplication.instance.pluginManager
    val settingsManager = JkhxApplication.instance.settingsManager
    val searchHistoryManager = JkhxApplication.instance.searchHistoryManager

    val isAdultEnabled by settingsManager.isAdultEnabled.collectAsState()
    val isComicEnabled by settingsManager.isComicEnabled.collectAsState()
    val isNovelEnabled by settingsManager.isNovelEnabled.collectAsState()
    val isLiveEnabled by settingsManager.isLiveEnabled.collectAsState()
    val searchHistory by searchHistoryManager.history.collectAsState()

    val displayMode = LocalDisplayMode.current
    val isTablet = displayMode == DeviceDisplayMode.TABLET

    val query = ActiveSearchHolder.query
    val searchResults = ActiveSearchHolder.searchResults
    val selectedPluginId = ActiveSearchHolder.selectedPluginId
    val isSearching = ActiveSearchHolder.isSearching
    val searchGridState = rememberLazyGridState()

    val coroutineScope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    val discoverMovies = ActiveSearchHolder.discoverMovies
    var isDiscoverLoading by remember { mutableStateOf(false) }
    var isDiscoverLoadingMore by remember { mutableStateOf(false) }

    val rotationAnimatable = remember { Animatable(0f) }
    val rotationAngle by rotationAnimatable.asState()

    val isAnyLoading = isSearching || isDiscoverLoading
    LaunchedEffect(isAnyLoading) {
        if (isAnyLoading) {
            while (isActive) {
                rotationAnimatable.animateTo(
                    targetValue = rotationAnimatable.value + 360f,
                    animationSpec = tween(durationMillis = 800, easing = LinearEasing)
                )
            }
        } else if (rotationAnimatable.value > 0f) {
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

    // ==================== TRẠNG THÁI KHÁM PHÁ THEO STREMIO ADDON ====================
    val json = remember { Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true } }
    var stremioAddons by remember { mutableStateOf(ActiveSearchHolder.cachedStremioAddons) }
    var isAddonsLoaded by remember { mutableStateOf(ActiveSearchHolder.cachedStremioAddons.isNotEmpty()) }
    var selectedAddonIndex by remember {
        val savedUrl = settingsManager.lastSelectedStremioAddonUrl.value
        val initialIdx = if (!savedUrl.isNullOrBlank()) {
            ActiveSearchHolder.cachedStremioAddons.indexOfFirst { it.first.url == savedUrl }.takeIf { it >= 0 } ?: 0
        } else 0
        mutableIntStateOf(initialIdx)
    }

    // Tải danh sách các Addon Stremio đang bật (chỉ lấy các Addon có catalogs) từ Room DB kèm auto-heal nếu thiếu options
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val entities = pluginManager.getStremioAddons()
            val enabled = entities.filter { it.isEnabled }
            val list = enabled.mapNotNull { entity ->
                try {
                    var manifest = json.decodeFromString<StremioManifest>(entity.manifestJson)
                    val needsRefresh = manifest.catalogs.any { cat ->
                        cat.extra.any { it.name.equals("genre", true) && it.options.isEmpty() }
                    }
                    if (needsRefresh) {
                        try {
                            val (freshManifest, raw) = pluginManager.getStremioClient().fetchManifestWithRaw(entity.url)
                            manifest = freshManifest
                            pluginManager.updateStremioAddonManifest(entity.url, raw)
                        } catch (_: Exception) {}
                    }
                    if (manifest.catalogs.isNotEmpty()) Pair(entity, manifest) else null
                } catch (_: Exception) {
                    null
                }
            }
            withContext(Dispatchers.Main) {
                ActiveSearchHolder.cachedStremioAddons = list
                stremioAddons = list
                isAddonsLoaded = true
                val savedUrl = settingsManager.lastSelectedStremioAddonUrl.value
                val matchedIdx = if (!savedUrl.isNullOrBlank()) {
                    list.indexOfFirst { it.first.url == savedUrl }.takeIf { it >= 0 } ?: 0
                } else 0
                selectedAddonIndex = matchedIdx
            }
        }
    }

    val currentPair = stremioAddons.getOrNull(selectedAddonIndex) ?: stremioAddons.firstOrNull()
    val currentAddon = currentPair?.first
    val currentManifest = currentPair?.second
    val currentPluginId = remember(currentManifest) {
        currentManifest?.let { StremioPluginWrapper.makePluginId(it.id) } ?: ""
    }

    // 1. Danh sách Types từ Manifest (movie, series, anime...)
    val availableTypes = remember(currentManifest) {
        currentManifest?.types?.ifEmpty { listOf("movie", "series") } ?: listOf("movie", "series")
    }
    var selectedType by remember(currentManifest) {
        val savedType = settingsManager.lastSelectedStremioType.value
        val initialType = if (!savedType.isNullOrBlank() && availableTypes.any { it.equals(savedType, ignoreCase = true) }) {
            availableTypes.first { it.equals(savedType, ignoreCase = true) }
        } else {
            availableTypes.firstOrNull() ?: "movie"
        }
        mutableStateOf(initialType)
    }

    // 2. Danh sách Catalogs thuộc Type đã chọn (bỏ qua search catalogs)
    val availableCatalogs = remember(currentManifest, selectedType) {
        currentManifest?.catalogs?.filter { cat ->
            cat.type.equals(selectedType, ignoreCase = true) &&
            !cat.extra.any { ext -> ext.name.equals("search", ignoreCase = true) && ext.isRequired }
        } ?: emptyList()
    }
    var selectedCatalog by remember(availableCatalogs) {
        val savedCatId = settingsManager.lastSelectedStremioCatalogId.value
        val initialCat = if (!savedCatId.isNullOrBlank()) {
            availableCatalogs.find { it.id == savedCatId } ?: availableCatalogs.firstOrNull()
        } else {
            availableCatalogs.firstOrNull()
        }
        mutableStateOf(initialCat)
    }

    // 3. Danh sách Options từ Extra hoặc Genres (Thể loại / Quốc gia / Năm / Danh mục con)
    val genreExtra = remember(selectedCatalog) {
        selectedCatalog?.extra?.find { it.options.isNotEmpty() }
    }
    val filterOptions = remember(selectedCatalog, genreExtra) {
        val extraOpts = genreExtra?.options ?: emptyList()
        if (extraOpts.isNotEmpty()) extraOpts else (selectedCatalog?.genres ?: emptyList())
    }
    var selectedFilterOption by remember(selectedCatalog, filterOptions) {
        val savedFilter = settingsManager.lastSelectedStremioFilter.value
        val initialFilter = if (!savedFilter.isNullOrBlank() && filterOptions.contains(savedFilter)) {
            savedFilter
        } else {
            filterOptions.firstOrNull()
        }
        mutableStateOf(initialFilter)
    }

    // Dropdown menus expand state
    var addonMenuExpanded by remember { mutableStateOf(false) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var catalogMenuExpanded by remember { mutableStateOf(false) }
    var filterMenuExpanded by remember { mutableStateOf(false) }

    val gridState = rememberLazyGridState()
    var discoverJob by remember { mutableStateOf<Job?>(null) }

    // Hàm tải danh sách phim từ Stremio Catalog
    fun fetchDiscover(reset: Boolean = false, forceRefresh: Boolean = false) {
        val addon = currentAddon ?: return
        val catalog = selectedCatalog ?: return
        val client = pluginManager.getStremioClient()
        val cacheKey = "${addon.url}_${selectedType}_${catalog.id}_${selectedFilterOption ?: ""}"

        if (reset) {
            discoverJob?.cancel()
            ActiveSearchHolder.discoverPage = 1
            ActiveSearchHolder.hasMorePages = true
            isDiscoverLoadingMore = false

            if (!forceRefresh) {
                val cached = SessionCacheManager.getDiscoverCatalog(cacheKey)
                if (!cached.isNullOrEmpty()) {
                    discoverMovies.clear()
                    discoverMovies.addAll(cached)
                    isDiscoverLoading = false
                    return
                }
            } else {
                SessionCacheManager.removeDiscoverCatalog(cacheKey)
            }

            discoverMovies.clear()
            isDiscoverLoading = true
        } else {
            if (!ActiveSearchHolder.hasMorePages || isDiscoverLoadingMore || isDiscoverLoading || discoverJob?.isActive == true) return
            isDiscoverLoadingMore = true
        }

        val pageToFetch = if (reset) 1 else ActiveSearchHolder.discoverPage
        val skip = (pageToFetch - 1) * 20

        discoverJob = coroutineScope.launch {
            try {
                val metas = withContext(Dispatchers.IO) {
                    client.fetchCatalog(
                        manifestUrl = addon.url,
                        type = selectedType,
                        id = catalog.id,
                        skip = skip,
                        genre = selectedFilterOption
                    )
                }

                val items = metas.map { m ->
                    val isSeries = m.type.equals("series", true) || m.type.equals("tv", true)
                    MovieItem(
                        slug = "${m.type}__${m.id}",
                        name = m.displayName,
                        originName = m.displayName,
                        thumbUrl = m.displayPoster,
                        posterUrl = m.displayPoster,
                        year = m.displayYear,
                        type = if (isSeries) "series" else "single",
                        category = m.genres.firstOrNull() ?: selectedFilterOption,
                        providerId = currentPluginId,
                        tmdbId = if (m.id.startsWith("tmdb:", true)) m.id.substringAfter(":") else null
                    )
                }

                if (reset) {
                    discoverMovies.clear()
                    discoverMovies.addAll(items)
                } else {
                    val existingKeys = discoverMovies.map { it.slug }.toSet()
                    val uniqueNewItems = items.filter { it.slug !in existingKeys }
                    discoverMovies.addAll(uniqueNewItems)
                }

                if (discoverMovies.isNotEmpty()) {
                    SessionCacheManager.putDiscoverCatalog(cacheKey, discoverMovies.toList())
                }

                if (items.isEmpty() || items.size < 15) {
                    ActiveSearchHolder.hasMorePages = false
                } else {
                    ActiveSearchHolder.discoverPage = pageToFetch + 1
                }
            } catch (e: Exception) {
                println("[SearchScreen Discover] Lỗi nạp catalog: ${e.message}")
            } finally {
                if (reset) {
                    isDiscoverLoading = false
                } else {
                    isDiscoverLoadingMore = false
                }
            }
        }
    }

    // Tự động tải lại khi đổi Addon, Type, Catalog hoặc Filter
    LaunchedEffect(currentAddon?.url, selectedType, selectedCatalog?.id, selectedFilterOption) {
        val addon = currentAddon
        val catalog = selectedCatalog
        if (addon != null && catalog != null) {
            settingsManager.setLastSelectedStremioAddonUrl(addon.url)
            settingsManager.setLastSelectedStremioType(selectedType)
            settingsManager.setLastSelectedStremioCatalogId(catalog.id)
            settingsManager.setLastSelectedStremioFilter(selectedFilterOption)
            val key = "${addon.url}_${selectedType}_${catalog.id}_${selectedFilterOption ?: ""}"
            if (ActiveSearchHolder.lastCatalogKey != key || discoverMovies.isEmpty()) {
                ActiveSearchHolder.lastCatalogKey = key
                fetchDiscover(reset = true)
            }
        }
    }

    // Tự động kích hoạt tải thêm khi cuộn gần cuối danh sách
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItemIndex >= totalItems - 4
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && ActiveSearchHolder.hasMorePages && !isDiscoverLoading && !isDiscoverLoadingMore && discoverJob?.isActive != true) {
            fetchDiscover(reset = false)
        }
    }

    // ==================== TÌM KIẾM ĐA NGUỒN ====================
    fun triggerSearch(keyword: String, saveToHistory: Boolean = false, forceRefresh: Boolean = false) {
        searchJob?.cancel()
        ActiveSearchHolder.selectedPluginId = null
        val cleanKeyword = keyword.trim()
        if (cleanKeyword.isBlank()) {
            ActiveSearchHolder.searchResults.clear()
            ActiveSearchHolder.isSearching = false
            return
        }

        if (!forceRefresh) {
            val cached = SessionCacheManager.getSearchResult(cleanKeyword)
            if (cached != null) {
                ActiveSearchHolder.searchResults.clear()
                ActiveSearchHolder.searchResults.addAll(cached)
                ActiveSearchHolder.isSearching = false
                if (saveToHistory) {
                    searchHistoryManager.addQuery(cleanKeyword)
                }
                return
            }
        } else {
            SessionCacheManager.removeSearchResult(cleanKeyword)
        }

        searchJob = coroutineScope.launch {
            try {
                if (!forceRefresh) {
                    delay(400) // debounce
                }
                ActiveSearchHolder.searchResults.clear()
                ActiveSearchHolder.isSearching = true
                pluginManager.searchAll(
                    cleanKeyword,
                    includeAdult = isAdultEnabled,
                    includeComic = isComicEnabled,
                    includeNovel = isNovelEnabled,
                    includeLive = isLiveEnabled
                ).collect { items ->
                    ActiveSearchHolder.searchResults.addAll(items)
                    if (saveToHistory && cleanKeyword.isNotBlank()) {
                        searchHistoryManager.addQuery(cleanKeyword)
                    }
                }
                if (ActiveSearchHolder.searchResults.isNotEmpty() && cleanKeyword.isNotBlank()) {
                    searchHistoryManager.addQuery(cleanKeyword)
                    SessionCacheManager.putSearchResult(cleanKeyword, ActiveSearchHolder.searchResults.toList())
                }
            } finally {
                ActiveSearchHolder.isSearching = false
            }
        }
    }

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank() && initialQuery != ActiveSearchHolder.query) {
            ActiveSearchHolder.query = initialQuery
            triggerSearch(initialQuery, saveToHistory = true)
        }
    }

    val availablePluginIds by remember {
        derivedStateOf {
            searchResults.mapNotNull { it.providerId }.distinct()
        }
    }

    val filteredResults by remember(selectedPluginId) {
        derivedStateOf {
            val activePluginId = ActiveSearchHolder.selectedPluginId
            if (activePluginId == null) {
                searchResults.toList()
            } else {
                searchResults.filter { it.providerId == activePluginId }
            }
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = AppTheme.dimens.space16, vertical = AppTheme.dimens.space10)
            ) {
                // Tiêu đề lớn "Tìm kiếm"
                Text(
                    text = stringResource(R.string.discover_title_search),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Thanh tìm kiếm phong cách Nuvio bo cong mềm mại
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        ActiveSearchHolder.query = it
                        triggerSearch(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_placeholder_nuvio),
                            fontSize = 14.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = {
                                searchJob?.cancel()
                                ActiveSearchHolder.clearSearch()
                            }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.search_clear),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (query.isNotBlank()) {
                                triggerSearch(query, saveToHistory = true)
                            }
                        }
                    ),
                    shape = RoundedCornerShape(22.dp)
                )

                // Khi có kết quả tìm kiếm: Hiển thị bộ lọc theo từng Plugin và nút Làm mới (Refresh)
                if (query.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Nút reload / refresh xoay vòng
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier
                                .padding(end = if (searchResults.isNotEmpty()) 8.dp else 0.dp)
                                .size(36.dp)
                                .clickable(enabled = !isSearching && query.isNotBlank()) {
                                    triggerSearch(query, saveToHistory = true, forceRefresh = true)
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.search_refresh),
                                    tint = if (isSearching) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .rotate(rotationAngle)
                                )
                            }
                        }

                        if (searchResults.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space8),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                item {
                                    val isAllSelected = selectedPluginId == null
                                    Surface(
                                        onClick = {
                                            ActiveSearchHolder.selectedPluginId = null
                                            coroutineScope.launch { searchGridState.scrollToItem(0) }
                                        },
                                        shape = ShapeTokens.SmallPill,
                                        color = if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                        )
                                    ) {
                                        Text(
                                            text = "${stringResource(R.string.search_filter_all)} (${searchResults.size})",
                                            fontSize = 12.5.sp,
                                            fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isAllSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }

                                items(availablePluginIds, key = { it }) { pId ->
                                    val isSelected = selectedPluginId == pId
                                    val pName = pluginManager.getPluginName(pId)
                                    val count = searchResults.count { it.providerId == pId }

                                    Surface(
                                        onClick = {
                                            ActiveSearchHolder.selectedPluginId = if (isSelected) null else pId
                                            coroutineScope.launch { searchGridState.scrollToItem(0) }
                                        },
                                        shape = ShapeTokens.SmallPill,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                        )
                                    ) {
                                        Text(
                                            text = "$pName ($count)",
                                            fontSize = 12.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (query.isBlank()) {
                // ==================== CHẾ ĐỘ KHÁM PHÁ (DISCOVER THEO STREMIO ADDON) ====================
                if (isAddonsLoaded && stremioAddons.isEmpty()) {
                    // Empty State: Chưa có Addon nào
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(68.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Chưa có Addon Stremio nào",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Cài đặt Addon Stremio để khám phá kho danh mục phim phong phú và luồng phát chất lượng cao.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { onOpenPluginsClick?.invoke() },
                                shape = ShapeTokens.Button,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Đi tới Quản lý Addon", fontSize = 13.5.sp)
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Tiêu đề phụ "Khám phá" bên trái + Nút Refresh xoay vòng và Dropdown chọn Addon nguồn bên phải
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppTheme.dimens.space16, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.discover_title_main),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Nút reload / refresh xoay vòng cho màn hình Khám phá
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clickable(enabled = !isDiscoverLoading) {
                                            fetchDiscover(reset = true, forceRefresh = true)
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = stringResource(R.string.search_refresh),
                                            tint = if (isDiscoverLoading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .rotate(rotationAngle)
                                        )
                                    }
                                }

                                if (stremioAddons.size > 1) {
                                    Box {
                                        val addonCleanName = currentManifest?.name?.let {
                                            StremioPluginWrapper.cleanCatalogTitle(it)
                                        } ?: "Addon"

                                        Surface(
                                            onClick = { addonMenuExpanded = true },
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.CloudQueue,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = addonCleanName,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.widthIn(max = 140.dp)
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDropDown,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = addonMenuExpanded,
                                            onDismissRequest = { addonMenuExpanded = false }
                                        ) {
                                        stremioAddons.forEachIndexed { idx, pair ->
                                            val m = pair.second
                                            val cleanName = StremioPluginWrapper.cleanCatalogTitle(m.name)
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = cleanName,
                                                        fontWeight = if (idx == selectedAddonIndex) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (idx == selectedAddonIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                    )
                                                },
                                                onClick = {
                                                    if (selectedAddonIndex != idx) {
                                                        settingsManager.setLastSelectedStremioAddonUrl(pair.first.url)
                                                        settingsManager.setLastSelectedStremioType(null)
                                                        settingsManager.setLastSelectedStremioCatalogId(null)
                                                        settingsManager.setLastSelectedStremioFilter(null)
                                                        selectedAddonIndex = idx
                                                    }
                                                    addonMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                        // Lịch sử tìm kiếm (nếu có, hiển thị dạng chip nhỏ gọn)
                        if (searchHistory.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AppTheme.dimens.space16, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items(searchHistory.take(8)) { keyword ->
                                    Surface(
                                        shape = ShapeTokens.SmallPill,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                        modifier = Modifier.clickable {
                                            ActiveSearchHolder.query = keyword
                                            triggerSearch(keyword, saveToHistory = true)
                                        }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.History,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = keyword,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = stringResource(R.string.search_clear),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier
                                                    .size(13.dp)
                                                    .clickable {
                                                        searchHistoryManager.removeQuery(keyword)
                                                    }
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        // ==================== CỤM 3 DROPDOWNS KHÁM PHÁ ADDON ====================
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppTheme.dimens.space16, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Chip 1: Loại nội dung (Types: movie, series...)
                            item {
                                Box {
                                    val typeDisplayName = when (selectedType.lowercase()) {
                                        "movie" -> stringResource(R.string.discover_type_movie)
                                        "series", "tv" -> stringResource(R.string.discover_type_series)
                                        "anime" -> "Hoạt hình"
                                        else -> selectedType.replaceFirstChar { it.uppercase() }
                                    }

                                    Surface(
                                        onClick = { typeMenuExpanded = true },
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                        ) {
                                            Text(
                                                text = typeDisplayName,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = typeMenuExpanded,
                                        onDismissRequest = { typeMenuExpanded = false }
                                    ) {
                                        availableTypes.forEach { typeKey ->
                                            val label = when (typeKey.lowercase()) {
                                                "movie" -> stringResource(R.string.discover_type_movie)
                                                "series", "tv" -> stringResource(R.string.discover_type_series)
                                                "anime" -> "Hoạt hình"
                                                else -> typeKey.replaceFirstChar { it.uppercase() }
                                            }
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = label,
                                                        fontWeight = if (typeKey == selectedType) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                onClick = {
                                                    if (selectedType != typeKey) {
                                                        settingsManager.setLastSelectedStremioType(typeKey)
                                                        settingsManager.setLastSelectedStremioCatalogId(null)
                                                        settingsManager.setLastSelectedStremioFilter(null)
                                                        selectedType = typeKey
                                                    }
                                                    typeMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Chip 2: Danh Mục Catalog của Addon
                            if (availableCatalogs.isNotEmpty()) {
                                item {
                                    Box {
                                        val catalogCleanTitle = selectedCatalog?.let {
                                            StremioPluginWrapper.cleanCatalogTitle(it.displayName)
                                        } ?: "Danh mục"

                                        Surface(
                                            onClick = { catalogMenuExpanded = true },
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                            ) {
                                                Text(
                                                    text = catalogCleanTitle,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.widthIn(max = 160.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDropDown,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = catalogMenuExpanded,
                                            onDismissRequest = { catalogMenuExpanded = false }
                                        ) {
                                            availableCatalogs.forEach { cat ->
                                                val clean = StremioPluginWrapper.cleanCatalogTitle(cat.displayName)
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = clean,
                                                            fontWeight = if (cat.id == selectedCatalog?.id) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    },
                                                    onClick = {
                                                        if (selectedCatalog?.id != cat.id) {
                                                            settingsManager.setLastSelectedStremioCatalogId(cat.id)
                                                            settingsManager.setLastSelectedStremioFilter(null)
                                                            selectedCatalog = cat
                                                        }
                                                        catalogMenuExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Chip 3: Thể loại / Bộ lọc con (Extra Options)
                            if (filterOptions.isNotEmpty()) {
                                item {
                                    Box {
                                        val filterLabel = selectedFilterOption ?: "Tất cả"

                                        Surface(
                                            onClick = { filterMenuExpanded = true },
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                            ) {
                                                Text(
                                                    text = filterLabel,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.widthIn(max = 140.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDropDown,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = filterMenuExpanded,
                                            onDismissRequest = { filterMenuExpanded = false }
                                        ) {
                                            filterOptions.forEach { opt ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = opt,
                                                            fontWeight = if (opt == selectedFilterOption) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    },
                                                    onClick = {
                                                        if (selectedFilterOption != opt) {
                                                            settingsManager.setLastSelectedStremioFilter(opt)
                                                            selectedFilterOption = opt
                                                        }
                                                        filterMenuExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Nhãn trạng thái nguồn: Tên Addon • Tên Catalog • Tên Thể loại / Bộ lọc
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppTheme.dimens.space16, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val addonName = currentManifest?.name ?: "Addon"
                            val catTitle = selectedCatalog?.let { StremioPluginWrapper.cleanCatalogTitle(it.displayName) } ?: ""
                            val filterTitle = if (!selectedFilterOption.isNullOrBlank()) " • $selectedFilterOption" else ""

                            Text(
                                text = "$addonName • $catTitle$filterTitle",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // ==================== LƯỚI PHIM KHÁM PHÁ (NUVIO CARDS) ====================
                        if (isDiscoverLoading && discoverMovies.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        } else if (discoverMovies.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.discover_empty),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedButton(
                                        onClick = { fetchDiscover(reset = true) }
                                    ) {
                                        Text(text = stringResource(R.string.common_retry))
                                    }
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Fixed(if (isTablet) 5 else 3),
                                contentPadding = PaddingValues(
                                    start = AppTheme.dimens.space12,
                                    end = AppTheme.dimens.space12,
                                    top = AppTheme.dimens.space8,
                                    bottom = 100.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space10),
                                verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space12),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                ) {
                                itemsIndexed(
                                    items = discoverMovies,
                                    key = { index, movie -> "${movie.slug}_$index" }
                                ) { _, movie ->
                                    NuvioDiscoverCard(
                                        movie = movie,
                                        onClick = {
                                            // Mở trực tiếp màn hình chi tiết phim từ Addon Stremio
                                            onMovieClick(movie.providerId ?: currentPluginId, movie.slug)
                                        }
                                    )
                                }

                                // Loading indicator khi cuộn tải trang tiếp theo
                                if (isDiscoverLoadingMore && ActiveSearchHolder.hasMorePages) {
                                    item(span = { GridItemSpan(maxLineSpan) }, key = "discover_loading_more_indicator") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                color = MaterialTheme.colorScheme.primary,
                                                strokeWidth = 2.5.dp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (isSearching && searchResults.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (filteredResults.isEmpty() && !isSearching) {
                Text(
                    text = stringResource(R.string.search_empty_result, query),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyVerticalGrid(
                    state = searchGridState,
                    columns = GridCells.Fixed(if (isTablet) 5 else 3),
                    contentPadding = PaddingValues(
                        start = AppTheme.dimens.space14,
                        end = AppTheme.dimens.space14,
                        top = AppTheme.dimens.space14,
                        bottom = 100.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space10),
                    verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space14),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        count = filteredResults.size,
                        key = { index -> "${filteredResults[index].providerId ?: ""}_${filteredResults[index].slug}_$index" },
                        contentType = { "movie_card" }
                    ) { index ->
                        val movie = filteredResults[index]
                        val providerName = remember(movie.providerId) {
                            movie.providerId?.let { pluginManager.getPluginName(it) }
                        }
                        MovieCard(
                            movie = movie,
                            sourceBadge = providerName,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (query.isNotBlank()) {
                                    searchHistoryManager.addQuery(query)
                                }
                                onMovieClick(movie.providerId ?: "unknown", movie.slug)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Thẻ phim hiển thị trong lưới Khám phá (Nuvio Movie Card)
 * Tỷ lệ 2:3 với poster, tên phim và năm phát hành ở dưới
 */
@Composable
fun NuvioDiscoverCard(
    movie: MovieItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
            shadowElevation = 2.dp
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(movie.posterUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = movie.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = movie.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
            lineHeight = 17.sp
        )

        movie.year?.let { y ->
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = y.toString(),
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

