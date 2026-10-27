package com.jkhx.player.ui.screens.movielist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.res.stringResource
import com.jkhx.player.R
import com.jkhx.player.ui.theme.AppTheme
import com.jkhx.player.ui.theme.ShapeTokens
import androidx.compose.ui.platform.LocalContext
import com.jkhx.player.JkhxApplication
import com.jkhx.player.core.model.FilterOption
import com.jkhx.player.core.model.MovieItem
import com.jkhx.player.core.model.PluginFilterConfig
import com.jkhx.player.settings.DeviceDisplayMode
import com.jkhx.player.ui.components.MovieCard
import com.jkhx.player.ui.theme.LocalDisplayMode
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private class MovieListSessionState(
    initialItems: List<MovieItem>
) {
    val movies: MutableList<MovieItem> = initialItems.distinctBy { it.slug }.toMutableList()
    var currentPage: Int = 1
    var hasNextPage: Boolean = true
    var filterConfig: PluginFilterConfig? = null
    var filtersInitialized: Boolean = false
    var selectedGenre: FilterOption? = null
    var selectedCountry: FilterOption? = null
    var selectedYear: FilterOption? = null
    var selectedSortField: FilterOption? = null
    var selectedSortType: FilterOption? = null
    var activeFilters: Map<String, String>? = null
    var firstVisibleItemIndex: Int = 0
    var firstVisibleItemScrollOffset: Int = 0
}

private object MovieListSessionCache {
    private val map = mutableMapOf<String, MovieListSessionState>()

    fun getOrCreate(key: String, initialItems: List<MovieItem>): MovieListSessionState {
        return map.getOrPut(key) { MovieListSessionState(initialItems) }
    }

    fun remove(key: String) {
        map.remove(key)
    }
}

@Composable
fun MovieListScreen(
    providerId: String,
    categoryTitle: String,
    type: String? = null,
    slug: String? = null,
    initialMovies: List<MovieItem> = emptyList(),
    onBack: () -> Unit,
    onMovieClick: (providerId: String, slug: String) -> Unit
) {
    val context = LocalContext.current
    val pluginManager = JkhxApplication.instance.pluginManager
    val coroutineScope = rememberCoroutineScope()
    val displayMode = LocalDisplayMode.current
    val isTablet = displayMode == DeviceDisplayMode.TABLET

    val sessionKey = remember(providerId, categoryTitle, type, slug) {
        "$providerId|$categoryTitle|${type.orEmpty()}|${slug.orEmpty()}"
    }
    val cachedSession = remember(sessionKey) {
        MovieListSessionCache.getOrCreate(sessionKey, initialMovies)
    }

    val handleBack: () -> Unit = remember(sessionKey, onBack) {
        {
            MovieListSessionCache.remove(sessionKey)
            onBack()
        }
    }
    BackHandler(onBack = handleBack)

    val gridState = remember(sessionKey) {
        LazyGridState(
            firstVisibleItemIndex = cachedSession.firstVisibleItemIndex,
            firstVisibleItemScrollOffset = cachedSession.firstVisibleItemScrollOffset
        )
    }

    val movies = remember(sessionKey) {
        mutableStateListOf<MovieItem>().apply {
            addAll(cachedSession.movies)
        }
    }

    var currentPage by remember(sessionKey) { mutableStateOf(cachedSession.currentPage) }
    var isLoadingFirst by remember(sessionKey) { mutableStateOf(cachedSession.movies.isEmpty()) }
    var isLoadingMore by remember(sessionKey) { mutableStateOf(false) }
    var hasNextPage by remember(sessionKey) { mutableStateOf(cachedSession.hasNextPage) }
    var errorMessage by remember(sessionKey) { mutableStateOf<String?>(null) }

    // Lưu vị trí cuộn theo thời gian thực để khôi phục chính xác khi Back từ DetailScreen
    LaunchedEffect(gridState, sessionKey) {
        snapshotFlow {
            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            cachedSession.firstVisibleItemIndex = index
            cachedSession.firstVisibleItemScrollOffset = offset
        }
    }

    // Hiệu ứng xoay mượt mà cho nút Refresh — xoay tròn đều khi tải, xoay nốt vòng và hạ cánh êm dịu khi hoàn thành
    val rotationAnimatable = remember { androidx.compose.animation.core.Animatable(0f) }
    val rotationAngle by rotationAnimatable.asState()
    LaunchedEffect(isLoadingFirst) {
        if (isLoadingFirst) {
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

    // Find provider name
    val providerRecord = remember(providerId) {
        pluginManager.getInstalledRecords().find { it.manifest.id == providerId }
    }
    val providerName = providerRecord?.manifest?.name ?: providerId

    // Dynamic Filter Config loaded strictly from plugin; null means provider doesn't support filters
    var filterConfig by remember(sessionKey) { mutableStateOf(cachedSession.filterConfig) }

    // Selected filter states
    var selectedGenre by remember(sessionKey) { mutableStateOf(cachedSession.selectedGenre) }
    var selectedCountry by remember(sessionKey) { mutableStateOf(cachedSession.selectedCountry) }
    var selectedYear by remember(sessionKey) { mutableStateOf(cachedSession.selectedYear) }
    var selectedSortField by remember(sessionKey) { mutableStateOf(cachedSession.selectedSortField) }
    var selectedSortType by remember(sessionKey) { mutableStateOf(cachedSession.selectedSortType) }
    var activeFilters by remember(sessionKey) { mutableStateOf(cachedSession.activeFilters) }

    // Fetch dynamic filter options strictly from plugin; if unsupported, filterConfig remains null
    LaunchedEffect(providerId, sessionKey) {
        if (cachedSession.filtersInitialized) return@LaunchedEffect
        try {
            val plugin = pluginManager.getPlugin(providerId)
            val remoteFilters = plugin?.getFilters()
            if (remoteFilters != null && (
                remoteFilters.genres.isNotEmpty() ||
                remoteFilters.countries.isNotEmpty() ||
                remoteFilters.years.isNotEmpty() ||
                remoteFilters.sortFields.isNotEmpty()
            )) {
                filterConfig = remoteFilters
                selectedSortField = remoteFilters.sortFields.firstOrNull()
                selectedSortType = remoteFilters.sortTypes.firstOrNull()
                cachedSession.filterConfig = remoteFilters
                cachedSession.selectedSortField = selectedSortField
                cachedSession.selectedSortType = selectedSortType
            } else {
                filterConfig = null
                cachedSession.filterConfig = null
            }
            cachedSession.filtersInitialized = true
        } catch (_: Exception) {
            filterConfig = null
            cachedSession.filterConfig = null
            cachedSession.filtersInitialized = true
        }
    }

    fun loadPage(page: Int, isRefresh: Boolean = false) {
        if (providerId.isBlank()) return
        if (page == 1 && !isRefresh && movies.isNotEmpty()) {
            // Initial render already has items
        } else if (page == 1) {
            isLoadingFirst = true
        } else {
            isLoadingMore = true
        }
        errorMessage = null

        coroutineScope.launch {
            try {
                val plugin = pluginManager.getPlugin(providerId)
                if (plugin == null) {
                    errorMessage = context.getString(R.string.movie_list_source_not_ready, providerName)
                    isLoadingFirst = false
                    isLoadingMore = false
                    return@launch
                }

                val result = plugin.getList(
                    type = type,
                    slug = slug ?: categoryTitle,
                    page = page,
                    filters = activeFilters
                )

                if (isRefresh || page == 1) {
                    val uniqueItems = result.items.distinctBy { it.slug }
                    movies.clear()
                    movies.addAll(uniqueItems)
                    currentPage = 1
                    cachedSession.movies.clear()
                    cachedSession.movies.addAll(uniqueItems)
                    cachedSession.currentPage = 1
                    if (isRefresh) {
                        cachedSession.firstVisibleItemIndex = 0
                        cachedSession.firstVisibleItemScrollOffset = 0
                        gridState.scrollToItem(0)
                    }
                } else {
                    val existingSlugs = movies.map { it.slug }.toSet()
                    val newUniqueItems = result.items.filter { it.slug !in existingSlugs }.distinctBy { it.slug }
                    movies.addAll(newUniqueItems)
                    currentPage = page
                    cachedSession.movies.clear()
                    cachedSession.movies.addAll(movies)
                    cachedSession.currentPage = page
                }

                hasNextPage = result.hasNext || (result.items.size >= 12 && page < 50)
                cachedSession.hasNextPage = hasNextPage
            } catch (e: Exception) {
                if (movies.isEmpty()) {
                    errorMessage = e.message ?: context.getString(R.string.movie_list_load_error)
                }
            } finally {
                isLoadingFirst = false
                isLoadingMore = false
            }
        }
    }

    // Trigger initial load
    LaunchedEffect(providerId, categoryTitle, slug, type) {
        if (movies.isEmpty()) {
            loadPage(1)
        }
    }

    // Infinite scroll detection optimized with snapshotFlow (smooth scrolling, zero frame drops)
    LaunchedEffect(gridState, hasNextPage, isLoadingFirst, isLoadingMore) {
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 4
        }
        .distinctUntilChanged()
        .collect { shouldLoad ->
            if (shouldLoad && !isLoadingFirst && !isLoadingMore && hasNextPage) {
                loadPage(currentPage + 1)
            }
        }
    }

    fun applyFilters() {
        val filters = mutableMapOf<String, String>()
        selectedGenre?.let { if (it.slug.isNotBlank()) filters["category"] = it.slug }
        selectedCountry?.let { if (it.slug.isNotBlank()) filters["country"] = it.slug }
        selectedYear?.let { if (it.slug.isNotBlank()) filters["year"] = it.slug }
        selectedSortField?.let { if (it.slug.isNotBlank()) filters["sort_field"] = it.slug }
        selectedSortType?.let { if (it.slug.isNotBlank()) filters["sort_type"] = it.slug }
        activeFilters = filters.ifEmpty { null }
        cachedSession.selectedGenre = selectedGenre
        cachedSession.selectedCountry = selectedCountry
        cachedSession.selectedYear = selectedYear
        cachedSession.selectedSortField = selectedSortField
        cachedSession.selectedSortType = selectedSortType
        cachedSession.activeFilters = activeFilters
        loadPage(1, isRefresh = true)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- 1. CINEMA AMBIENT GLOW BACKDROP (HÀO QUANG ĐIỆN ẢNH ĐỈNH TRANG) ---
        val ambientPoster = movies.firstOrNull()?.posterUrl
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .graphicsLayer()
                .clipToBounds()
        ) {
            if (ambientPoster != null) {
                val mlBlurContext = LocalContext.current
                AsyncImage(
                    model = remember(ambientPoster) {
                        ImageRequest.Builder(mlBlurContext)
                            .data(ambientPoster)
                            .size(240, 360)
                            .crossfade(false)
                            .allowHardware(true)
                            .build()
                    },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(1.4f)
                        .blur(52.dp)
                )
            }

            // Lớp tỏa sắc Cinematic Ambient Halo
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                Color(0xFF7B1FA2).copy(alpha = 0.16f),
                                Color.Transparent
                            ),
                            radius = 800f
                        )
                    )
            )

            // Lớp chuyển tiếp mượt mà vào nền tối tổng thể
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.45f),
                                Color(0xCC0F0F14),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )
        }

        // --- 2. CẤU TRÚC GIAO DIỆN CHÍNH (HEADER LIỀN MẠCH + BỘ LỌC + LƯỚI PHIM) ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Thanh điều hướng TopBar phong cách điện ảnh tràn viền
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Nút quay lại tròn mờ chuẩn Glassmorphic
                Surface(
                    onClick = handleBack,
                    shape = CircleShape,
                    color = AppTheme.colors.glassBackground,
                    border = BorderStroke(1.dp, AppTheme.colors.glassBorder),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Cụm thông tin Tiêu đề danh mục & Nguồn máy chủ
                Column(modifier = Modifier.weight(1f)) {
                    val formattedHeader = if (categoryTitle.startsWith("DANH SÁCH", ignoreCase = true) || categoryTitle.startsWith("MOVIES", ignoreCase = true)) {
                        categoryTitle.uppercase()
                    } else {
                        stringResource(R.string.movie_list_list_prefix, categoryTitle.uppercase())
                    }
                    Text(
                        text = formattedHeader,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = 0.8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (providerName.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.movie_list_server_label, providerName.uppercase()),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Nút Refresh tròn mờ có hiệu ứng xoay 360 độ
                Surface(
                    onClick = { loadPage(1, isRefresh = true) },
                    shape = CircleShape,
                    color = AppTheme.colors.glassBackground,
                    border = BorderStroke(1.dp, AppTheme.colors.glassBorder),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.common_refresh),
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(rotationAngle)
                        )
                    }
                }
            }

            // Thanh Bộ Lọc Động Glassmorphism (Chỉ hiển thị khi nguồn plugin hỗ trợ)
            val currentConfig = filterConfig
            if (currentConfig != null) {
                val placeholderGenre = stringResource(R.string.movie_list_filter_genre_placeholder)
                val placeholderCountry = stringResource(R.string.movie_list_filter_country_placeholder)
                val placeholderYear = stringResource(R.string.movie_list_filter_year_placeholder)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Dropdown 1: Thể loại
                    if (currentConfig.genres.isNotEmpty()) {
                        val genreOptions = remember(currentConfig.genres) {
                            if (currentConfig.genres.none { it.slug.isBlank() }) {
                                listOf(FilterOption(placeholderGenre, "")) + currentConfig.genres
                            } else currentConfig.genres
                        }
                        MovieListFilterDropdown(
                            selectedText = selectedGenre?.name ?: placeholderGenre,
                            options = genreOptions,
                            onSelect = {
                                selectedGenre = if (it.slug.isBlank()) null else it
                                cachedSession.selectedGenre = selectedGenre
                            }
                        )
                    }

                    // Dropdown 2: Quốc gia
                    if (currentConfig.countries.isNotEmpty()) {
                        val countryOptions = remember(currentConfig.countries) {
                            if (currentConfig.countries.none { it.slug.isBlank() }) {
                                listOf(FilterOption(placeholderCountry, "")) + currentConfig.countries
                            } else currentConfig.countries
                        }
                        MovieListFilterDropdown(
                            selectedText = selectedCountry?.name ?: placeholderCountry,
                            options = countryOptions,
                            onSelect = {
                                selectedCountry = if (it.slug.isBlank()) null else it
                                cachedSession.selectedCountry = selectedCountry
                            }
                        )
                    }

                    // Dropdown 3: Năm
                    if (currentConfig.years.isNotEmpty()) {
                        val yearOptions = remember(currentConfig.years) {
                            if (currentConfig.years.none { it.slug.isBlank() }) {
                                listOf(FilterOption(placeholderYear, "")) + currentConfig.years
                            } else currentConfig.years
                        }
                        MovieListFilterDropdown(
                            selectedText = selectedYear?.name ?: placeholderYear,
                            options = yearOptions,
                            onSelect = {
                                selectedYear = if (it.slug.isBlank()) null else it
                                cachedSession.selectedYear = selectedYear
                            }
                        )
                    }

                    // Dropdown 4: Sắp xếp theo trường
                    if (currentConfig.sortFields.isNotEmpty()) {
                        MovieListFilterDropdown(
                            selectedText = selectedSortField?.name ?: currentConfig.sortFields.first().name,
                            options = currentConfig.sortFields,
                            onSelect = {
                                selectedSortField = it
                                cachedSession.selectedSortField = selectedSortField
                            }
                        )
                    }

                    // Dropdown 5: Thứ tự tăng/giảm
                    if (currentConfig.sortTypes.isNotEmpty()) {
                        MovieListFilterDropdown(
                            selectedText = selectedSortType?.name ?: currentConfig.sortTypes.first().name,
                            options = currentConfig.sortTypes,
                            onSelect = {
                                selectedSortType = it
                                cachedSession.selectedSortType = selectedSortType
                            }
                        )
                    }

                    // Nút [LỌC] phong cách Cinema Red
                    Button(
                        onClick = { applyFilters() },
                        shape = ShapeTokens.Button,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.movie_list_filter_btn),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.movie_list_filter_btn),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            // Danh sách lưới phim LazyVerticalGrid
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    isLoadingFirst && movies.isEmpty() -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(42.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = stringResource(R.string.movie_list_loading),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }

                    errorMessage != null && movies.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(AppTheme.dimens.space24),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = errorMessage ?: stringResource(R.string.movie_list_load_error),
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(AppTheme.dimens.space16))
                            Button(
                                onClick = { loadPage(1, isRefresh = true) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = ShapeTokens.Button
                            ) {
                                Text(stringResource(R.string.common_retry), color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }

                    movies.isEmpty() && !isLoadingFirst -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(AppTheme.dimens.space24),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(60.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.movie_list_empty_filter),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(AppTheme.dimens.space16))
                            OutlinedButton(
                                onClick = handleBack,
                                shape = ShapeTokens.Button
                            ) {
                                Text(stringResource(R.string.common_back), color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }

                    else -> {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(if (isTablet) 5 else 3),
                            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                count = movies.size,
                                key = { index -> "${movies[index].providerId ?: providerId}_${movies[index].slug}_$index" },
                                contentType = { "movie_card" }
                            ) { index ->
                                val movie = movies[index]
                                MovieCard(
                                    movie = movie,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        cachedSession.firstVisibleItemIndex = gridState.firstVisibleItemIndex
                                        cachedSession.firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset
                                        onMovieClick(movie.providerId ?: providerId, movie.slug)
                                    }
                                )
                            }

                            // Bottom loading/completion status
                            if (isLoadingMore) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 18.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp),
                                            strokeWidth = 3.dp
                                        )
                                    }
                                }
                            } else if (!hasNextPage && movies.size >= 12) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.movie_list_all_displayed),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dropdown Selector theo phong cách Cinema Glassmorphic mờ ảo bo tròn cao cấp
 */
@Composable
fun MovieListFilterDropdown(
    selectedText: String,
    options: List<FilterOption>,
    onSelect: (FilterOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = ShapeTokens.Button,
            color = AppTheme.colors.glassBackground,
            border = BorderStroke(1.dp, AppTheme.colors.glassBorder)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
            ) {
                Text(
                    text = selectedText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppTheme.colors.onSurfaceHighEmphasis,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .border(BorderStroke(1.dp, AppTheme.colors.glassBorder), ShapeTokens.Button)
                .heightIn(max = 280.dp)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.name,
                            fontSize = 13.sp,
                            fontWeight = if (option.name == selectedText) FontWeight.Bold else FontWeight.Normal,
                            color = if (option.name == selectedText) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
