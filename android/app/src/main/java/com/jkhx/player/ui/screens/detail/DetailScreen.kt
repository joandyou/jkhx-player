package com.jkhx.player.ui.screens.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.jkhx.player.JkhxApplication
import com.jkhx.player.core.database.entity.FavoriteEntity
import com.jkhx.player.core.model.CastMember
import com.jkhx.player.core.model.EpisodeData
import com.jkhx.player.core.model.EpisodeServer
import com.jkhx.player.core.model.MovieDetail
import com.jkhx.player.core.model.MovieItem
import com.jkhx.player.core.network.tmdb.TmdbConfig
import com.jkhx.player.settings.DeviceDisplayMode
import com.jkhx.player.ui.components.MovieCard
import com.jkhx.player.ui.theme.LocalDisplayMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun setupActivePlayerSession(
    providerId: String,
    slug: String,
    detail: MovieDetail,
    server: EpisodeServer,
    episodeIndex: Int,
    episode: EpisodeData,
    rawName: String? = null,
    rawOriginName: String? = null,
    isDescending: Boolean = false
) {
    com.jkhx.player.ActivePlayerSession.providerId = providerId
    com.jkhx.player.ActivePlayerSession.movieSlug = slug
    com.jkhx.player.ActivePlayerSession.movieName = detail.name
    com.jkhx.player.ActivePlayerSession.movieThumb = detail.backdropUrl ?: detail.posterUrl
    com.jkhx.player.ActivePlayerSession.tmdbId = detail.tmdbId
    com.jkhx.player.ActivePlayerSession.imdbId = detail.imdbId
    com.jkhx.player.ActivePlayerSession.originName = detail.originName
    com.jkhx.player.ActivePlayerSession.movieYear = detail.year
    com.jkhx.player.ActivePlayerSession.availableServers = detail.episodes
    com.jkhx.player.ActivePlayerSession.currentServerName = server.serverName
    com.jkhx.player.ActivePlayerSession.isDescending = isDescending
    val sortedEpisodes = if (isDescending) server.episodes.reversed() else server.episodes
    com.jkhx.player.ActivePlayerSession.episodes = sortedEpisodes
    com.jkhx.player.ActivePlayerSession.currentEpisodeIndex = episodeIndex
    com.jkhx.player.ActivePlayerSession.currentEpisode = episode
    com.jkhx.player.ActivePlayerSession.rawPluginName = rawName ?: detail.name
    com.jkhx.player.ActivePlayerSession.rawPluginOriginName = rawOriginName ?: detail.originName
    com.jkhx.player.ActivePlayerSession.movieDetail = detail
    com.jkhx.player.ActivePlayerSession.customStream = null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    providerId: String,
    slug: String,
    onBack: () -> Unit,
    onMovieClick: ((providerId: String, slug: String) -> Unit)? = null,
    onSearchMovie: ((query: String) -> Unit)? = null,
    onPlayEpisode: (episode: EpisodeData) -> Unit
) {
    val context = LocalContext.current
    val pluginManager = JkhxApplication.instance.pluginManager
    val database = JkhxApplication.instance.database
    val historyDao = remember { database.historyDao() }
    val favoriteDao = remember { database.favoriteDao() }
    val coroutineScope = rememberCoroutineScope()
    val displayMode = LocalDisplayMode.current
    val isTablet = displayMode == DeviceDisplayMode.TABLET

    val watchHistory by historyDao.getHistoryById("$providerId:$slug").collectAsState(initial = null)
    val isFavorite by favoriteDao.isFavorite("$providerId:$slug").collectAsState(initial = false)

    var movieDetail by remember { mutableStateOf<MovieDetail?>(null) }
    var rawPluginName by remember { mutableStateOf<String?>(null) }
    var rawPluginOriginName by remember { mutableStateOf<String?>(null) }
    var providerDisplayName by remember(providerId) { mutableStateOf(providerId) }
    var selectedServerIndex by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val settingsManager = remember { JkhxApplication.instance.settingsManager }
    val isMovieSortDescending by settingsManager.isMovieEpisodeSortDescending.collectAsState()
    val isComicSortDescending by settingsManager.isComicEpisodeSortDescending.collectAsState()
    val listState = rememberLazyListState()

    // Danh sách 4 row phim gợi ý từ TMDB
    var actor1Movies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var actor1Name by remember { mutableStateOf<String?>(null) }
    var actor2Movies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var actor2Name by remember { mutableStateOf<String?>(null) }
    var genreMovies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var genreName by remember { mutableStateOf<String?>(null) }
    var countryMovies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var countryName by remember { mutableStateOf<String?>(null) }

    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 80
        }
    }
    val topBarAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 0.95f else 0f,
        animationSpec = tween(220),
        label = "detailTopBarAlpha"
    )

    LaunchedEffect(providerId, slug) {
        isLoading = true
        errorMessage = null
        try {
            val tmdbRepo = JkhxApplication.instance.tmdbRepository
            var baseDetail: MovieDetail? = null
            val plugin = pluginManager.getPlugin(providerId)

            if (plugin != null) {
                providerDisplayName = plugin.name
                var d = try {
                    plugin.getDetail(slug)
                } catch (e: Exception) {
                    null
                }

                if (d == null) {
                    val savedHist = try {
                        withContext(Dispatchers.IO) {
                            historyDao.getAllHistorySync().find { it.id == "$providerId:$slug" || it.movieSlug == slug }
                        }
                    } catch (_: Exception) { null }

                    if (savedHist != null && savedHist.movieName.isNotBlank()) {
                        val otherPlugins = pluginManager.getMovieTvPlugins().filter { it.id != providerId }
                        for (otherPlug in otherPlugins) {
                            try {
                                val sRes = otherPlug.search(savedHist.movieName, 1)
                                val match = sRes.items.firstOrNull { item ->
                                    item.name.equals(savedHist.movieName, ignoreCase = true) ||
                                    (!item.originName.isNullOrBlank() && item.originName.equals(savedHist.movieName, ignoreCase = true)) ||
                                    item.slug == slug
                                } ?: sRes.items.firstOrNull { item ->
                                    item.name.contains(savedHist.movieName, ignoreCase = true) ||
                                    savedHist.movieName.contains(item.name, ignoreCase = true)
                                }
                                if (match != null) {
                                    val otherDetail = otherPlug.getDetail(match.slug)
                                    if (otherDetail.episodes.isNotEmpty()) {
                                        d = otherDetail
                                        providerDisplayName = otherPlug.name
                                        break
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }

                if (d == null) {
                    throw Exception(context.getString(R.string.detail_load_error))
                }

                val slugTail = slug.substringAfterLast("__")
                val isDummy = d.name.isBlank() || d.name == slug || d.name == slugTail
                if (isDummy) {
                    val savedHist = try {
                        withContext(Dispatchers.IO) {
                            historyDao.getAllHistorySync().find { it.id == "$providerId:$slug" || it.movieSlug == slug }
                        }
                    } catch (_: Exception) { null }
                    if (savedHist != null && savedHist.movieName.isNotBlank()) {
                        d = d.copy(
                            name = savedHist.movieName,
                            originName = d.originName?.takeIf { it != slug && it != slugTail } ?: savedHist.movieName,
                            posterUrl = d.posterUrl ?: savedHist.thumbUrl,
                            backdropUrl = d.backdropUrl ?: savedHist.thumbUrl,
                            thumbUrl = d.thumbUrl ?: savedHist.thumbUrl
                        )
                    }
                }
                baseDetail = d
                rawPluginName = d.name
                rawPluginOriginName = d.originName
            } else if (providerId == "tmdb" || providerId.isBlank()) {
                providerDisplayName = "TMDB"
                // Phim điều hướng từ TMDB catalog hoặc recommendations
                val tmdbData = tmdbRepo.getTmdbDetail(slug)
                if (tmdbData != null) {
                    val mTitle = tmdbData.title ?: tmdbData.name ?: slug
                    val mOrigin = tmdbData.originalTitle ?: tmdbData.originalName
                    baseDetail = MovieDetail(
                        slug = slug,
                        name = mTitle,
                        originName = mOrigin,
                        posterUrl = TmdbConfig.proxiedImageUrl(tmdbData.posterPath, "w500"),
                        backdropUrl = TmdbConfig.proxiedImageUrl(tmdbData.backdropPath, "original"),
                        thumbUrl = TmdbConfig.proxiedImageUrl(tmdbData.backdropPath ?: tmdbData.posterPath, "w500"),
                        year = (tmdbData.releaseDate ?: tmdbData.firstAirDate)?.take(4)?.toIntOrNull(),
                        type = if (tmdbData.title != null) "single" else "series",
                        description = tmdbData.overview,
                        rating = tmdbData.voteAverage,
                        voteCount = tmdbData.voteCount,
                        tagline = tmdbData.tagline,
                        genres = tmdbData.genres.map { it.name },
                        countries = tmdbData.productionCountries.map { it.name },
                        castMembers = tmdbData.credits?.cast?.map { c ->
                            CastMember(
                                id = c.id,
                                name = c.name,
                                character = c.character,
                                profileUrl = TmdbConfig.proxiedImageUrl(c.profilePath, "w185")
                            )
                        } ?: emptyList(),
                        actors = tmdbData.credits?.cast?.map { it.name } ?: emptyList(),
                        directors = tmdbData.credits?.crew?.filter { it.job.equals("Director", ignoreCase = true) }?.map { it.name } ?: emptyList(),
                        tmdbId = tmdbData.id?.toString() ?: slug
                    )
                    rawPluginName = mTitle
                    rawPluginOriginName = mOrigin

                    // Thử tìm nguồn phát từ các plugin đang bật
                    val enabled = pluginManager.getEnabledPlugins()
                    for (plug in enabled) {
                        try {
                            val searchRes = plug.search(mTitle)
                            val candidateMovies = searchRes.items
                            val match = candidateMovies.firstOrNull { m ->
                                m.name.contains(mTitle, ignoreCase = true) || mTitle.contains(m.name, ignoreCase = true)
                            }
                            if (match != null) {
                                val d = plug.getDetail(match.slug)
                                if (d.episodes.isNotEmpty()) {
                                    baseDetail = baseDetail?.copy(episodes = d.episodes)
                                    break
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            if (baseDetail == null) {
                throw Exception(context.getString(R.string.detail_source_not_enabled, providerId))
            }

            movieDetail = baseDetail
            isLoading = false

            // Tự động làm giàu metadata từ TMDB (tự động tìm kiếm TMDB ID nếu plugin chưa cung cấp)
            launch {
                try {
                    var enriched = tmdbRepo.enrichMovieDetail(baseDetail)
                    // Nếu sau khi enrich, castMembers vẫn rỗng nhưng có danh sách tên diễn viên, tra cứu avatar cho top 12 diễn viên
                    if (enriched.castMembers.isEmpty() && enriched.actors.isNotEmpty()) {
                        val fetchedCast = enriched.actors.take(12).map { name ->
                            val (personId, profileUrl) = tmdbRepo.getActorProfile(name)
                            CastMember(id = personId, name = name, profileUrl = profileUrl)
                        }
                        if (fetchedCast.isNotEmpty()) {
                            enriched = enriched.copy(castMembers = fetchedCast)
                        }
                    }
                    movieDetail = enriched
                } catch (_: Exception) {
                    // Giữ nguyên dữ liệu gốc từ plugin nếu TMDB gặp lỗi
                }
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: context.getString(R.string.detail_load_error)
            isLoading = false
        }
    }

    // Tự động tải 4 row phim gợi ý từ TMDB sau khi có thông tin phim
    LaunchedEffect(movieDetail?.name, movieDetail?.tmdbId) {
        val detail = movieDetail ?: return@LaunchedEffect
        val isTv = detail.type.equals("series", ignoreCase = true) ||
                   detail.type.equals("tv", ignoreCase = true) ||
                   detail.type.equals("tvshows", ignoreCase = true) ||
                   detail.type.equals("hoathinh", ignoreCase = true)
        val tmdbRepo = JkhxApplication.instance.tmdbRepository

        val actor1 = detail.castMembers.getOrNull(0) ?: detail.actors.getOrNull(0)?.let { CastMember(name = it) }
        val actor2 = detail.castMembers.getOrNull(1) ?: detail.actors.getOrNull(1)?.let { CastMember(name = it) }

        // 1. Phim diễn viên 1
        launch {
            if (actor1 != null && actor1.name.isNotBlank()) {
                actor1Name = actor1.name
                val a1Id = actor1.id
                val movies = if (a1Id != null && a1Id > 0) {
                    tmdbRepo.getMoviesByPerson(a1Id)
                } else {
                    tmdbRepo.getMoviesByPersonName(actor1.name)
                }
                actor1Movies = movies.filter { it.tmdbId != detail.tmdbId && !it.name.equals(detail.name, ignoreCase = true) }
            } else {
                actor1Movies = emptyList()
                actor1Name = null
            }
        }

        // 2. Phim diễn viên 2
        launch {
            if (actor2 != null && actor2.name.isNotBlank()) {
                actor2Name = actor2.name
                val a2Id = actor2.id
                val movies = if (a2Id != null && a2Id > 0) {
                    tmdbRepo.getMoviesByPerson(a2Id)
                } else {
                    tmdbRepo.getMoviesByPersonName(actor2.name)
                }
                actor2Movies = movies.filter { it.tmdbId != detail.tmdbId && !it.name.equals(detail.name, ignoreCase = true) }
            } else {
                actor2Movies = emptyList()
                actor2Name = null
            }
        }

        // 3. Phim cùng thể loại
        val firstGenre = detail.genres.firstOrNull()
        if (!firstGenre.isNullOrBlank()) {
            genreName = firstGenre
            launch {
                val movies = tmdbRepo.getMoviesByGenreName(firstGenre, isTv = isTv)
                genreMovies = movies.filter { it.tmdbId != detail.tmdbId && !it.name.equals(detail.name, ignoreCase = true) }
            }
        } else {
            genreMovies = emptyList()
            genreName = null
        }

        // 4. Phim cùng quốc gia
        val firstCountry = detail.countries.firstOrNull()
        if (!firstCountry.isNullOrBlank()) {
            countryName = firstCountry
            launch {
                val movies = tmdbRepo.getMoviesByCountry(firstCountry, isTv = isTv)
                countryMovies = movies.filter { it.tmdbId != detail.tmdbId && !it.name.equals(detail.name, ignoreCase = true) }
            }
        } else {
            countryMovies = emptyList()
            countryName = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- LỚP 1: NỘI DUNG CUỘN TỪ ĐỈNH MÀN HÌNH (FULL BLEED TRÀN VIỀN) ---
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
        } else if (errorMessage != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(AppTheme.dimens.space24),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(AppTheme.dimens.space16))
                Button(
                    onClick = onBack,
                    shape = ShapeTokens.Button,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(stringResource(R.string.common_back))
                }
            }
        } else if (movieDetail != null) {
            val detail = movieDetail!!
            val isComic = detail.type == "comic" || detail.type == "novel" || pluginManager.isComicProvider(providerId) || pluginManager.isNovelProvider(providerId)
            val isEpisodeSortDescending = if (isComic) isComicSortDescending else isMovieSortDescending
            val hasTmdb = !detail.tmdbId.isNullOrBlank() || (detail.castMembers.isNotEmpty() && detail.rating != null)
            val unifiedServer = remember(detail.episodes, providerDisplayName) {
                val baseServer = detail.episodes.maxByOrNull { it.episodes.size } ?: detail.episodes.firstOrNull()
                baseServer?.copy(serverName = providerDisplayName)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 36.dp)
            ) {
                // 1. Cinema Ambient Glow Hero Header
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(310.dp)
                            .graphicsLayer()
                            .clipToBounds()
                    ) {
                        // Lớp 1: Hậu cảnh phóng to + Blur tạo quầng sáng màu (Ambilight glow)
                        val blurContext = LocalContext.current
                        val glowUrl = detail.backdropUrl ?: detail.posterUrl
                        AsyncImage(
                            model = remember(glowUrl) {
                                ImageRequest.Builder(blurContext)
                                    .data(glowUrl)
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
                                .blur(50.dp)
                        )

                        // Lớp 2: Radial Gradient hào quang điện ảnh đồng bộ theo theme (Cinematic Halo)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                                            Color(0xFF7B1FA2).copy(alpha = 0.16f),
                                            Color.Transparent
                                        ),
                                        radius = 800f
                                    )
                                )
                        )

                        // Lớp 3: Backdrop chính sắc nét với góc bo tròn và viền sáng mảnh (padding chừa chỗ cho TopBar)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 14.dp, end = 14.dp, top = 56.dp, bottom = 12.dp)
                        ) {
                            AsyncImage(
                                model = detail.backdropUrl ?: detail.posterUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(ShapeTokens.LargeChip)
                                    .border(
                                        width = 1.dp,
                                        brush = Brush.verticalGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.35f),
                                                Color.Transparent
                                            )
                                        ),
                                        shape = ShapeTokens.LargeChip
                                    )
                            )
                        }

                        // Lớp 4: Lớp chuyển tiếp mượt mà vào màu nền tổng thể
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            Color(0x44000000),
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                                            MaterialTheme.colorScheme.background
                                        )
                                    )
                                )
                        )
                    }
                }

                // 2. Info & Quick Play Section
                item {
                    Column(modifier = Modifier.padding(horizontal = AppTheme.dimens.space16, vertical = AppTheme.dimens.space6)) {
                        Text(
                            text = detail.name,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (!detail.originName.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = detail.originName ?: "",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Tagline từ TMDB
                        if (!detail.tagline.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "“${detail.tagline}”",
                                fontSize = 13.sp,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                            )
                        }

                        Spacer(modifier = Modifier.height(AppTheme.dimens.space10))

                        // Tags chips (Quốc gia, Điểm TMDB, Năm, chất lượng, ngôn ngữ)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space8),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (detail.countries.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "📍",
                                        fontSize = 11.5.sp
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = detail.countries.first(),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            val rating = detail.rating
                            val voteCount = detail.voteCount
                            if (rating != null && rating > 0.0) {
                                Surface(
                                    color = AppTheme.colors.warning.copy(alpha = 0.2f),
                                    shape = ShapeTokens.Chip,
                                    border = BorderStroke(1.dp, AppTheme.colors.warning.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "★",
                                            fontSize = 11.5.sp,
                                            color = AppTheme.colors.warning
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = String.format(java.util.Locale.US, "%.1f", rating),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AppTheme.colors.onSurfaceHighEmphasis
                                        )
                                        if (voteCount != null && voteCount > 0) {
                                            val countText = if (voteCount >= 1000) {
                                                String.format(java.util.Locale.US, "%.1fk", voteCount / 1000f)
                                            } else {
                                                voteCount.toString()
                                            }
                                            Text(
                                                text = " ($countText)",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            if (!detail.quality.isNullOrEmpty()) {
                                Surface(
                                    color = AppTheme.colors.glassBackground,
                                    shape = ShapeTokens.Chip,
                                    border = BorderStroke(1.dp, AppTheme.colors.glassBorder)
                                ) {
                                    Text(
                                        text = detail.quality ?: "",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AppTheme.colors.onSurfaceHighEmphasis,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            if (!detail.language.isNullOrEmpty()) {
                                Surface(
                                    color = AppTheme.colors.glassBackground,
                                    shape = ShapeTokens.Chip,
                                    border = BorderStroke(1.dp, AppTheme.colors.glassBorder)
                                ) {
                                    Text(
                                        text = detail.language ?: "",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = AppTheme.colors.onSurfaceHighEmphasis,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            if (detail.year != null) {
                                Surface(
                                    color = AppTheme.colors.glassBackground,
                                    shape = ShapeTokens.Chip,
                                    border = BorderStroke(1.dp, AppTheme.colors.glassBorder)
                                ) {
                                    Text(
                                        text = "${detail.year}",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = AppTheme.colors.onSurfaceHighEmphasis,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Danh sách thể loại (Genres) nếu không có TMDB metadata
                        if (!hasTmdb && detail.genres.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(AppTheme.dimens.space8))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space6),
                                verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space6)
                            ) {
                                detail.genres.forEach { genre ->
                                    Surface(
                                        color = AppTheme.colors.glassBackground,
                                        shape = ShapeTokens.Chip,
                                        border = BorderStroke(0.5.dp, AppTheme.colors.glassBorder)
                                    ) {
                                        Text(
                                            text = genre,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Hàng nút hành động: Xem ngay / Đọc ngay + Yêu thích + Download
                        val firstServer = unifiedServer ?: detail.episodes.firstOrNull()
                        val sortedFirstServerEpisodes = remember(firstServer?.episodes, isEpisodeSortDescending) {
                            val eps = firstServer?.episodes ?: emptyList()
                            if (isEpisodeSortDescending) eps.reversed() else eps
                        }
                        val firstEpisode = sortedFirstServerEpisodes.firstOrNull()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space12),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Nút Xem Ngay / Đọc Ngay chính (Bo góc pill, high contrast)
                            Button(
                                onClick = {
                                    if (firstServer != null && firstEpisode != null) {
                                        setupActivePlayerSession(
                                            providerId = providerId,
                                            slug = slug,
                                            detail = detail,
                                            server = firstServer,
                                            episodeIndex = 0,
                                            episode = firstEpisode,
                                            rawName = rawPluginName,
                                            rawOriginName = rawPluginOriginName,
                                            isDescending = isEpisodeSortDescending
                                        )
                                        onPlayEpisode(firstEpisode)
                                    }
                                },
                                enabled = firstEpisode != null,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.onSurface,
                                    contentColor = MaterialTheme.colorScheme.surface
                                ),
                                shape = ShapeTokens.BannerCard,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(AppTheme.dimens.buttonHeightMedium)
                            ) {
                                Icon(
                                    imageVector = if (isComic) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(AppTheme.dimens.space6))
                                Text(
                                    text = stringResource(if (isComic) R.string.detail_read_short else R.string.detail_watch_short),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.surface
                                )
                            }

                            // 2. Nút Favorites (Yêu thích)
                            Surface(
                                onClick = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val favId = "$providerId:$slug"
                                        if (isFavorite) {
                                            favoriteDao.delete(favId)
                                            com.jkhx.player.sync.SyncManager.getInstance(context).onFavoriteDeleted(favId)
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, context.getString(R.string.detail_favorite_removed), Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            favoriteDao.insert(
                                                FavoriteEntity(
                                                    id = favId,
                                                    providerId = providerId,
                                                    movieSlug = slug,
                                                    movieName = detail.name,
                                                    thumbUrl = detail.backdropUrl ?: detail.posterUrl,
                                                    addedAt = System.currentTimeMillis()
                                                )
                                            )
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, context.getString(R.string.detail_favorite_added), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                shape = CircleShape,
                                color = AppTheme.colors.glassBackground,
                                border = BorderStroke(1.dp, AppTheme.colors.glassBorder),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = stringResource(R.string.detail_btn_favorite),
                                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        // UI Xem tiếp... (Nếu có lịch sử xem phim/đọc truyện)
                        if (watchHistory != null && !watchHistory?.lastEpisodeName.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(AppTheme.dimens.space12))
                            Surface(
                                onClick = {
                                    val targetServer = detail.episodes.find { server ->
                                        server.episodes.any { it.id == watchHistory?.lastEpisodeId || it.name == watchHistory?.lastEpisodeName }
                                    } ?: detail.episodes.getOrNull(selectedServerIndex) ?: detail.episodes.firstOrNull()

                                    val targetEpisode = targetServer?.episodes?.find {
                                        it.id == watchHistory?.lastEpisodeId || it.name == watchHistory?.lastEpisodeName
                                    } ?: targetServer?.episodes?.firstOrNull()

                                    if (targetServer != null && targetEpisode != null) {
                                        val sortedTargetEps = if (isEpisodeSortDescending) targetServer.episodes.reversed() else targetServer.episodes
                                        val targetIndex = sortedTargetEps.indexOfFirst {
                                            it.id == targetEpisode.id || it.name == targetEpisode.name
                                        }.coerceAtLeast(0)
                                        setupActivePlayerSession(
                                            providerId = providerId,
                                            slug = slug,
                                            detail = detail,
                                            server = targetServer,
                                            episodeIndex = targetIndex,
                                            episode = targetEpisode,
                                            rawName = rawPluginName,
                                            rawOriginName = rawPluginOriginName,
                                            isDescending = isEpisodeSortDescending
                                        )
                                        onPlayEpisode(targetEpisode)
                                    }
                                },
                                shape = ShapeTokens.Card,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(AppTheme.dimens.space12),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isComic) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(AppTheme.dimens.space12))
                                    Column(modifier = Modifier.weight(1f)) {
                                        val isNovel = detail.type == "novel" || pluginManager.isNovelProvider(providerId)
                                        val continueTitle = if (isNovel) {
                                            val progressPercent = if ((watchHistory?.durationMs ?: 0L) > 0L) {
                                                ((watchHistory?.positionMs?.toFloat() ?: 0f) / (watchHistory?.durationMs?.toFloat() ?: 1f) * 100).toInt().coerceIn(0, 100)
                                            } else 0
                                            if (progressPercent > 0) {
                                                "${stringResource(R.string.detail_continue_reading, watchHistory?.lastEpisodeName ?: "")} ($progressPercent%)"
                                            } else {
                                                stringResource(R.string.detail_continue_reading, watchHistory?.lastEpisodeName ?: "")
                                            }
                                        } else if (isComic) {
                                            val pageNum = (watchHistory?.positionMs ?: 0L) + 1L
                                            if (pageNum > 1L) {
                                                "${stringResource(R.string.detail_continue_reading, watchHistory?.lastEpisodeName ?: "")} (Trang $pageNum)"
                                            } else {
                                                stringResource(R.string.detail_continue_reading, watchHistory?.lastEpisodeName ?: "")
                                            }
                                        } else {
                                            stringResource(R.string.detail_continue_watching, watchHistory?.lastEpisodeName ?: "")
                                        }
                                        Text(
                                            text = continueTitle,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = stringResource(R.string.detail_status_watching),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                    }
                }

                // 3. Server Tabs & Episodes (Đồng nhất hiển thị tên Plugin / Addon tại mục Chọn Server)
                if (unifiedServer != null && unifiedServer.episodes.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(AppTheme.dimens.space14))
                        Text(
                            text = stringResource(R.string.detail_select_server),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = AppTheme.dimens.space16, vertical = AppTheme.dimens.space4)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = AppTheme.dimens.space16),
                            horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space16)
                        ) {
                            item {
                                Column(
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = unifiedServer.serverName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .height(2.5.dp)
                                            .width(80.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = ShapeTokens.Badge
                                            )
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(AppTheme.dimens.space10))
                    }

                    val currentServer = unifiedServer
                    if (currentServer != null) {
                        if (currentServer.episodes.size == 1) {
                            item {
                                val singleEp = currentServer.episodes.first()
                                Surface(
                                    onClick = {
                                        setupActivePlayerSession(
                                            providerId = providerId,
                                            slug = slug,
                                            detail = detail,
                                            server = currentServer,
                                            episodeIndex = 0,
                                            episode = singleEp,
                                            rawName = rawPluginName,
                                            rawOriginName = rawPluginOriginName,
                                            isDescending = isEpisodeSortDescending
                                        )
                                        onPlayEpisode(singleEp)
                                    },
                                    shape = ShapeTokens.Card,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = AppTheme.dimens.space16)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(AppTheme.dimens.space10),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(90.dp)
                                                .height(54.dp)
                                                .clip(ShapeTokens.Chip)
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            AsyncImage(
                                                model = detail.backdropUrl ?: detail.posterUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.Black.copy(alpha = 0.4f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(AppTheme.dimens.space12))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = singleEp.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = detail.quality ?: "HD",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(AppTheme.dimens.space10))
                            }
                        } else {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = AppTheme.dimens.space16, vertical = AppTheme.dimens.space4),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(
                                            if (isComic) R.string.detail_chapter_list else R.string.detail_episode_list,
                                            currentServer.episodes.size
                                        ),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        letterSpacing = 0.5.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )

                                    TextButton(
                                        onClick = {
                                            val nextSort = !isEpisodeSortDescending
                                            if (isComic) {
                                                settingsManager.setComicEpisodeSortDescending(nextSort)
                                            } else {
                                                settingsManager.setMovieEpisodeSortDescending(nextSort)
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SwapVert,
                                            contentDescription = stringResource(R.string.detail_sort_chapters),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(if (isEpisodeSortDescending) R.string.detail_sort_desc else R.string.detail_sort_asc),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                            item {
                                val displayedEpisodes = remember(currentServer.episodes, isEpisodeSortDescending) {
                                    val indexed = currentServer.episodes.mapIndexed { index, ep -> Pair(index, ep) }
                                    if (isEpisodeSortDescending) indexed.reversed() else indexed
                                }
                                FlowRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = AppTheme.dimens.space16),
                                    horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space8),
                                    verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space8)
                                ) {
                                    displayedEpisodes.forEachIndexed { displayIndex, (actualIndex, episode) ->
                                        val isCurrentPlaying = watchHistory?.lastEpisodeId == episode.id || watchHistory?.lastEpisodeName == episode.name
                                        Surface(
                                            onClick = {
                                                setupActivePlayerSession(
                                                    providerId = providerId,
                                                    slug = slug,
                                                    detail = detail,
                                                    server = currentServer,
                                                    episodeIndex = displayIndex,
                                                    episode = episode,
                                                    rawName = rawPluginName,
                                                    rawOriginName = rawPluginOriginName,
                                                    isDescending = isEpisodeSortDescending
                                                )
                                                onPlayEpisode(episode)
                                            },
                                            shape = ShapeTokens.Chip,
                                            color = if (isCurrentPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isCurrentPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                            )
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isComic) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = if (isCurrentPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = episode.name,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = if (isCurrentPlaying) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isCurrentPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(AppTheme.dimens.space12))
                            }
                        }
                    }
                }

                // 4. Metadata Details (Đạo diễn, Thể loại, Diễn viên, Quốc gia, Nội dung)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppTheme.dimens.space16, vertical = AppTheme.dimens.space8)
                    ) {
                        // 1. Đạo diễn
                        if (detail.directors.isNotEmpty()) {
                            Row(modifier = Modifier.padding(vertical = 3.dp)) {
                                Text(
                                    text = stringResource(R.string.detail_director),
                                    fontSize = 13.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(85.dp)
                                )
                                Text(
                                    text = detail.directors.joinToString(", "),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // 2. Thể loại (Đưa lên trên Diễn viên theo yêu cầu)
                        if (detail.genres.isNotEmpty()) {
                            Row(modifier = Modifier.padding(vertical = 3.dp)) {
                                Text(
                                    text = stringResource(R.string.detail_genres),
                                    fontSize = 13.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(85.dp)
                                )
                                Text(
                                    text = detail.genres.joinToString(", "),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // 3. Diễn viên (Avatar card row kết hợp hình ảnh, tên và vai diễn)
                        val castList = if (detail.castMembers.isNotEmpty()) {
                            detail.castMembers
                        } else {
                            detail.actors.map { CastMember(name = it) }
                        }
                        if (castList.isNotEmpty()) {
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Text(
                                    text = stringResource(R.string.detail_actors),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                val avatarContext = LocalContext.current
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(castList.take(16)) { cast ->
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.width(72.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (!cast.profileUrl.isNullOrBlank()) {
                                                    AsyncImage(
                                                        model = remember(cast.profileUrl) {
                                                            ImageRequest.Builder(avatarContext)
                                                                .data(cast.profileUrl)
                                                                .crossfade(true)
                                                                .build()
                                                        },
                                                        contentDescription = cast.name,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.Person,
                                                        contentDescription = cast.name,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(30.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = cast.name,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onBackground,
                                                maxLines = 2,
                                                textAlign = TextAlign.Center,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val characterName = cast.character
                                            if (!characterName.isNullOrBlank()) {
                                                Text(
                                                    text = characterName,
                                                    fontSize = 9.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    maxLines = 1,
                                                    textAlign = TextAlign.Center,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 4. Quốc gia
                        if (detail.countries.isNotEmpty()) {
                            Row(modifier = Modifier.padding(vertical = 3.dp)) {
                                Text(
                                    text = stringResource(R.string.detail_country),
                                    fontSize = 13.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(85.dp)
                                )
                                Text(
                                    text = detail.countries.joinToString(", "),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // 5. Nội dung
                        if (!detail.description.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(AppTheme.dimens.space14))
                            Text(
                                text = stringResource(R.string.detail_synopsis),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(AppTheme.dimens.space6))
                            var isExpanded by remember { mutableStateOf(false) }
                            Text(
                                text = detail.description ?: "",
                                fontSize = 13.5.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(if (isExpanded) R.string.detail_read_less else R.string.detail_read_more),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .clickable { isExpanded = !isExpanded }
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }
                }

                // 5. Phim của Diễn viên 1
                if (actor1Movies.isNotEmpty() && !actor1Name.isNullOrBlank()) {
                    item(key = "rec_actor_1") {
                        DetailRecommendationSection(
                            title = "Phim của $actor1Name",
                            movies = actor1Movies,
                            isTablet = isTablet,
                            onMovieClick = { movie ->
                                if (onSearchMovie != null) {
                                    onSearchMovie(movie.name)
                                } else {
                                    onMovieClick?.invoke("tmdb", movie.name)
                                }
                            }
                        )
                    }
                }

                // 6. Phim của Diễn viên 2
                if (actor2Movies.isNotEmpty() && !actor2Name.isNullOrBlank()) {
                    item(key = "rec_actor_2") {
                        DetailRecommendationSection(
                            title = "Phim của $actor2Name",
                            movies = actor2Movies,
                            isTablet = isTablet,
                            onMovieClick = { movie ->
                                if (onSearchMovie != null) {
                                    onSearchMovie(movie.name)
                                } else {
                                    onMovieClick?.invoke("tmdb", movie.name)
                                }
                            }
                        )
                    }
                }

                // 7. Phim cùng thể loại
                if (genreMovies.isNotEmpty() && !genreName.isNullOrBlank()) {
                    item(key = "rec_genre") {
                        DetailRecommendationSection(
                            title = "Phim cùng thể loại: $genreName",
                            movies = genreMovies,
                            isTablet = isTablet,
                            onMovieClick = { movie ->
                                if (onSearchMovie != null) {
                                    onSearchMovie(movie.name)
                                } else {
                                    onMovieClick?.invoke("tmdb", movie.name)
                                }
                            }
                        )
                    }
                }

                // 8. Phim cùng quốc gia
                if (countryMovies.isNotEmpty() && !countryName.isNullOrBlank()) {
                    item(key = "rec_country") {
                        DetailRecommendationSection(
                            title = "Phim cùng quốc gia: $countryName",
                            movies = countryMovies,
                            isTablet = isTablet,
                            onMovieClick = { movie ->
                                if (onSearchMovie != null) {
                                    onSearchMovie(movie.name)
                                } else {
                                    onMovieClick?.invoke("tmdb", movie.name)
                                }
                            }
                        )
                    }
                }
            }
        }

        // --- LỚP 2: FLOATING TRANSPARENT TOPBAR (TRÀN VIỀN ĐỈNH CHUẨN ĐIỆN ẢNH) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .then(
                    if (isScrolled) {
                        Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = topBarAlpha))
                    } else {
                        Modifier.background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xCC000000),
                                    Color(0x66000000),
                                    Color.Transparent
                                )
                            )
                        )
                    }
                )
                .statusBarsPadding()
                .height(52.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Nút quay lại tròn bán trong suốt Glassmorphic
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = AppTheme.colors.glassBackground,
                border = BorderStroke(1.dp, AppTheme.colors.glassBorder),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.detail_back),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Tiêu đề phim (hiện mượt khi cuộn hoặc hiển thị thanh lịch)
            if (isScrolled && movieDetail != null) {
                Text(
                    text = movieDetail!!.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DetailRecommendationSection(
    title: String,
    movies: List<MovieItem>,
    isTablet: Boolean,
    onMovieClick: (MovieItem) -> Unit
) {
    if (movies.isEmpty()) return
    Column(modifier = Modifier.padding(vertical = AppTheme.dimens.space10)) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = AppTheme.dimens.space16, vertical = AppTheme.dimens.space6)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = AppTheme.dimens.space16),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space12)
        ) {
            items(
                items = movies,
                key = { movie -> "${movie.providerId ?: "tmdb"}_${movie.tmdbId ?: movie.slug}" },
                contentType = { "movie_card" }
            ) { movie ->
                MovieCard(
                    movie = movie,
                    sourceBadge = "TMDB",
                    modifier = Modifier.width(if (isTablet) AppTheme.dimens.cardWidthTablet else AppTheme.dimens.cardWidthMobile),
                    onClick = { onMovieClick(movie) }
                )
            }
        }
    }
}
