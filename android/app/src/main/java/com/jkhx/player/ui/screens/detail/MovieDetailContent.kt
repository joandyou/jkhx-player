package com.jkhx.player.ui.screens.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jkhx.player.R
import com.jkhx.player.core.database.entity.HistoryEntity
import com.jkhx.player.core.model.CastMember
import com.jkhx.player.core.model.EpisodeData
import com.jkhx.player.core.model.EpisodeServer
import com.jkhx.player.ui.screens.player.formatTime
import com.jkhx.player.core.model.MovieDetail
import com.jkhx.player.core.model.MovieItem
import com.jkhx.player.ui.components.MovieCard
import com.jkhx.player.ui.theme.AppTheme
import com.jkhx.player.ui.theme.ShapeTokens
import com.jkhx.player.ui.theme.tvFocusableItem

data class SeasonGroup(
    val seasonNumber: Int,
    val seasonLabel: String,
    val episodes: List<EpisodeData>
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MovieDetailContent(
    providerId: String,
    slug: String,
    detail: MovieDetail,
    providerDisplayName: String,
    watchHistory: HistoryEntity?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onRefreshSources: () -> Unit,
    isRefreshing: Boolean,
    onPlayEpisode: (server: EpisodeServer, episodeIndex: Int, episode: EpisodeData, isDescending: Boolean) -> Unit,
    onMovieClick: ((providerId: String, slug: String) -> Unit)? = null,
    onSearchMovie: ((query: String) -> Unit)? = null,
    actor1Movies: List<MovieItem>,
    actor1Name: String?,
    actor2Movies: List<MovieItem>,
    actor2Name: String?,
    genreMovies: List<MovieItem>,
    genreName: String?,
    countryMovies: List<MovieItem>,
    countryName: String?,
    isEpisodeSortDescending: Boolean,
    onToggleEpisodeSort: () -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 1. Phân giải danh sách máy chủ (servers) - Mặc định sử dụng server đầu tiên
    val currentServer = detail.episodes.firstOrNull()
        ?: EpisodeServer(serverName = providerDisplayName, episodes = emptyList())

    val allEpisodes = currentServer.episodes
    val isSeries = remember(detail.type, allEpisodes) {
        detail.type.equals("series", true) ||
            detail.type.equals("tv", true) ||
            detail.type.equals("tvshows", true) ||
            detail.type.equals("hoathinh", true) ||
            allEpisodes.size > 1 ||
            allEpisodes.any { (it.season ?: 0) > 0 }
    }

    // 2. Tự động bóc tách và phân nhóm các mùa (Seasons)
    val seasonGroups = remember(allEpisodes, isSeries) {
        if (!isSeries || allEpisodes.isEmpty()) {
            emptyList()
        } else {
            val hasExplicitSeasons = allEpisodes.any { (it.season ?: 0) > 0 }
            val map = if (hasExplicitSeasons) {
                allEpisodes.groupBy { it.season ?: 1 }
            } else {
                // Nhận diện regex từ tên tập: Mùa 1, Phần 2, S1, Season 1...
                val byName = allEpisodes.groupBy { ep ->
                    val match = Regex("""(?:mùa|phần|season|s)\s*(\d+)""", RegexOption.IGNORE_CASE).find(ep.name)
                    match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
                }
                if (byName.keys.size > 1) byName else mapOf(1 to allEpisodes)
            }
            map.entries.sortedBy { it.key }.map { (sNum, eps) ->
                SeasonGroup(
                    seasonNumber = sNum,
                    seasonLabel = "Mùa $sNum",
                    episodes = eps
                )
            }
        }
    }

    var selectedSeasonNumber by rememberSaveable(detail.slug, seasonGroups.size) {
        mutableIntStateOf(seasonGroups.firstOrNull()?.seasonNumber ?: 1)
    }

    var episodeSearchQuery by rememberSaveable(detail.slug) { mutableStateOf("") }
    var highlightedEpisodeId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Đảm bảo selectedSeasonNumber luôn hợp lệ
    val activeSeasonGroup = seasonGroups.find { it.seasonNumber == selectedSeasonNumber }
        ?: seasonGroups.firstOrNull()

    val currentSeasonEpisodes = activeSeasonGroup?.episodes ?: allEpisodes
    val displayedEpisodes = remember(currentSeasonEpisodes, episodeSearchQuery, isEpisodeSortDescending) {
        val filtered = if (episodeSearchQuery.isBlank()) {
            currentSeasonEpisodes
        } else {
            currentSeasonEpisodes.filterIndexed { index, ep ->
                ep.matchesEpisodeQuery(episodeSearchQuery, index)
            }
        }
        if (isEpisodeSortDescending) filtered.reversed() else filtered
    }

    // 3. Phân giải tập đang xem dở hoặc tập đầu tiên cho nút CTA Play chính
    val resumeEpisode = remember(allEpisodes, watchHistory) {
        if (watchHistory != null && !watchHistory.lastEpisodeName.isNullOrBlank()) {
            allEpisodes.find { it.id == watchHistory.lastEpisodeId || it.name == watchHistory.lastEpisodeName }
        } else null
    }
    val firstEpisode = displayedEpisodes.firstOrNull() ?: allEpisodes.firstOrNull()
    val primaryEpisode = resumeEpisode ?: firstEpisode

    // Hiệu ứng xoay icon Reload
    val rotationAnimatable = remember { androidx.compose.animation.core.Animatable(0f) }
    val rotationAngle by rotationAnimatable.asState()
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            while (true) {
                rotationAnimatable.animateTo(
                    targetValue = rotationAnimatable.value + 360f,
                    animationSpec = tween(850, easing = LinearEasing)
                )
            }
        } else if (rotationAnimatable.value > 0f) {
            val nextFull = kotlin.math.ceil(rotationAnimatable.value / 360f) * 360f
            rotationAnimatable.animateTo(
                targetValue = nextFull,
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            )
            rotationAnimatable.snapTo(0f)
        }
    }

    val castList = remember(detail.castMembers, detail.actors) {
        if (detail.castMembers.isNotEmpty()) detail.castMembers
        else detail.actors.map { CastMember(name = it) }
    }

    val infoRows = remember(detail) {
        buildList {
            val relInfo = detail.year?.toString()
            if (!relInfo.isNullOrBlank()) {
                add(R.string.detail_info_release to relInfo)
            }
            if (!detail.duration.isNullOrBlank()) {
                add(R.string.detail_info_duration to detail.duration!!)
            }
            if (detail.countries.isNotEmpty()) {
                add(R.string.detail_info_country to detail.countries.joinToString(", "))
            }
            if (detail.genres.isNotEmpty()) {
                add(R.string.detail_info_genres to detail.genres.joinToString(" • "))
            }
            if (!detail.status.isNullOrBlank()) {
                add(R.string.detail_info_status to detail.status!!)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp)
    ) {
        // =========================================================================
        // 1. CINEMA HERO HEADER (Backdrop Ambilight Glow + Title + Breadcrumbs)
        // =========================================================================
        item(key = "movie_hero") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .graphicsLayer()
                    .clipToBounds()
            ) {
                val glowUrl = detail.backdropUrl ?: detail.posterUrl
                // Lớp 1: Ambient Glow tỏa mờ phía sau
                AsyncImage(
                    model = remember(glowUrl) {
                        ImageRequest.Builder(context)
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

                // Lớp 2: Radial gradient tăng cường hào quang rực rỡ
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    Color(0xFF7B1FA2).copy(alpha = 0.15f),
                                    Color.Transparent
                                ),
                                radius = 800f
                            )
                        )
                )

                // Lớp 3: Backdrop chính sắc nét
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 14.dp, end = 14.dp, top = 52.dp, bottom = 12.dp)
                ) {
                    AsyncImage(
                        model = glowUrl,
                        contentDescription = detail.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                width = 1.dp,
                                brush = Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                    )
                }

                // Lớp 4: Gradient chuyển tiếp vào màu nền đen tổng thể
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color(0x33000000),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                )
            }
        }

        // =========================================================================
        // 2. TIÊU ĐỀ PHIM & THỂ LOẠI (BREADCRUMB NUVIO)
        // =========================================================================
        item(key = "movie_title_section") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Tên phim chính
                Text(
                    text = detail.name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    lineHeight = 30.sp
                )

                // Tên gốc (nếu có)
                if (!detail.originName.isNullOrBlank() && !detail.originName.equals(detail.name, ignoreCase = true)) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = detail.originName!!,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }

                // Tagline phim (nếu có)
                if (!detail.tagline.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "“${detail.tagline}”",
                        fontSize = 12.5.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center
                    )
                }

                // Thể loại dạng Breadcrumbs (Nuvio style: Phim Bí Ẩn • Phim Chính Kịch • Sci-Fi)
                if (detail.genres.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = detail.genres.take(4).joinToString(" • "),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // =========================================================================
        // 3. NÚT PLAY CHÍNH (PRIMARY CTA PILL) & CỤM NÚT ACTION TRÒN (NUVIO STYLE)
        // =========================================================================
        item(key = "movie_cta_buttons") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Nút Xem Ngay / Xem Tiếp (Lớn bo tròn dạng Pill nổi bật)
                val isResume = resumeEpisode != null
                val playText = if (isResume) {
                    val pos = watchHistory?.positionMs ?: 0L
                    val dur = watchHistory?.durationMs ?: 0L
                    val percent = if (dur > 0L) ((pos.toFloat() / dur.toFloat()) * 100).toInt().coerceIn(0, 100) else 0

                    if (isSeries) {
                        val sNum = resumeEpisode!!.season ?: 1
                        val epNum = resumeEpisode.number ?: 1
                        if (percent in 1..99) {
                            stringResource(R.string.detail_resume_episode, "M$sNum T$epNum ($percent%)")
                        } else {
                            stringResource(R.string.detail_resume_episode, "M$sNum T$epNum")
                        }
                    } else {
                        // Phim lẻ: Hiển thị "Xem tiếp (45%)" hoặc "Xem tiếp (12:34)"
                        if (percent in 1..99) {
                            stringResource(R.string.player_resume_continue, "$percent%")
                        } else if (pos > 0L) {
                            stringResource(R.string.player_resume_continue, formatTime(pos))
                        } else {
                            stringResource(R.string.home_continue_watching)
                        }
                    }
                } else {
                    stringResource(R.string.detail_watch_short)
                }

                Button(
                    onClick = {
                        val targetEp = primaryEpisode ?: allEpisodes.firstOrNull()
                        if (targetEp != null) {
                            val epIdx = allEpisodes.indexOf(targetEp).coerceAtLeast(0)
                            onPlayEpisode(currentServer, epIdx, targetEp, isEpisodeSortDescending)
                        }
                    },
                    enabled = primaryEpisode != null || allEpisodes.isNotEmpty(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.03f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = playText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Hàng nút chức năng tròn (Quick Action Circular Buttons)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Nút Yêu thích (Favorite)
                    Surface(
                        onClick = onToggleFavorite,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .size(44.dp)
                            .tvFocusableItem(cornerRadius = 22.dp, focusedScale = 1.08f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = stringResource(R.string.detail_btn_favorite),
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Nút Tải lại / quét nguồn (Refresh)
                    Surface(
                        onClick = onRefreshSources,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .size(44.dp)
                            .tvFocusableItem(cornerRadius = 22.dp, focusedScale = 1.08f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.streams_reload),
                                tint = if (isRefreshing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .size(20.dp)
                                    .rotate(rotationAngle)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Nút Chia sẻ (Share)
                    Surface(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, detail.name)
                                putExtra(Intent.EXTRA_TEXT, "${detail.name} - ${detail.originName ?: ""}")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.detail_share_stream_link)))
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .size(44.dp)
                            .tvFocusableItem(cornerRadius = 22.dp, focusedScale = 1.08f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.detail_share_stream_link),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 4. METADATA & RATINGS ROW (Năm • Độ tuổi • Rating IMDb ★ / TMDB)
        // =========================================================================
        item(key = "movie_meta_ratings") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Năm phát hành
                if (detail.year != null) {
                    Text(
                        text = "${detail.year}",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "•",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Phân loại / Chất lượng (TV-MA / 1080p / HD)
                val qualityTag = detail.quality ?: "HD"
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = qualityTag,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Điểm đánh giá IMDb / TMDB dạng Badge vàng
                val rating = detail.rating
                if (rating != null && rating > 0.0) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFF5C518).copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, Color(0xFFF5C518).copy(alpha = 0.4f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "IMDb",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFF5C518)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = String.format(java.util.Locale.US, "%.1f", rating),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Thời lượng (Duration) nếu có
                if (!detail.duration.isNullOrBlank()) {
                    Text(
                        text = "•",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = detail.duration!!,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // =========================================================================
        // 5. ĐẠO DIỄN & TÓM TẮT NỘI DUNG (OVERVIEW XEM THÊM / THU GỌN)
        // =========================================================================
        item(key = "movie_directors_overview") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                // Đạo diễn
                if (detail.directors.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.detail_director),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = detail.directors.joinToString(", "),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Biên kịch
                if (detail.writers.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.detail_writer),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = detail.writers.joinToString(", "),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Tóm tắt nội dung có Xem thêm / Thu gọn
                val synopsis = detail.description?.trim().orEmpty()
                if (synopsis.isNotBlank()) {
                    var isExpanded by remember { mutableStateOf(false) }
                    Column(modifier = Modifier.animateContentSize()) {
                        Text(
                            text = synopsis,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                            maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (synopsis.length > 140) {
                            Text(
                                text = if (isExpanded) "▲ ${stringResource(R.string.detail_read_less)}" else "▼ ${stringResource(R.string.detail_read_more)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { isExpanded = !isExpanded }
                                    .padding(top = 4.dp, bottom = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 6. DANH SÁCH DIỄN VIÊN (CAST SECTION - AVATAR TRÒN KÈM TÊN NHÂN VẬT)
        // =========================================================================
        if (castList.isNotEmpty()) {
            item(key = "movie_cast_section") {
                Column(modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) {
                    Text(
                        text = stringResource(R.string.detail_actors),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(castList.take(16)) { cast ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(76.dp)
                                    .clickable { onSearchMovie?.invoke(cast.name) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!cast.profileUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = remember(cast.profileUrl) {
                                                ImageRequest.Builder(context)
                                                    .data(cast.profileUrl)
                                                    .crossfade(true)
                                                    .build()
                                            },
                                            contentDescription = cast.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(
                                            text = cast.name.initials(),
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = cast.name,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                                if (!cast.character.isNullOrBlank()) {
                                    Text(
                                        text = cast.character!!,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }


        // =========================================================================
        // 8. ĐÁY MÀN HÌNH: CÁC MÙA & DANH SÁCH CARD TẬP PHIM (NUVIO EPISODE CARDS)
        // =========================================================================
        if (allEpisodes.isNotEmpty()) {
            // Header Các Mùa (nếu là Series)
            if (isSeries && seasonGroups.isNotEmpty()) {
                item(key = "movie_seasons_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.detail_seasons),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(seasonGroups) { sGroup ->
                                val isSelected = sGroup.seasonNumber == selectedSeasonNumber
                                Surface(
                                    onClick = {
                                        selectedSeasonNumber = sGroup.seasonNumber
                                        episodeSearchQuery = ""
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                    ),
                                    modifier = Modifier.tvFocusableItem(cornerRadius = 10.dp, focusedScale = 1.05f)
                                ) {
                                    Text(
                                        text = sGroup.seasonLabel,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Thanh điều hướng & tìm kiếm tập phim chuẩn Nuvio
            item(key = "movie_episodes_control_bar") {
                val currentSortOrder = if (isEpisodeSortDescending) EpisodeSortOrder.LastToFirst else EpisodeSortOrder.FirstToLast
                EpisodeNavigatorBar(
                    query = episodeSearchQuery,
                    onQueryChange = { episodeSearchQuery = it },
                    sortOrder = currentSortOrder,
                    onSortOrderChange = { newOrder ->
                        if (newOrder != currentSortOrder) {
                            onToggleEpisodeSort()
                        }
                    },
                    shownCount = displayedEpisodes.size,
                    totalCount = currentSeasonEpisodes.size,
                    seasonLabel = activeSeasonGroup?.seasonLabel ?: stringResource(R.string.detail_season_n, 1),
                    episodes = currentSeasonEpisodes,
                    isWatched = { ep ->
                        watchHistory?.let { h ->
                            h.lastEpisodeId == ep.id || h.lastEpisodeName == ep.name
                        } ?: false
                    },
                    upNextEpisodeId = resumeEpisode?.id ?: firstEpisode?.id,
                    onGoToEpisode = { targetEp ->
                        coroutineScope.launch {
                            val epIndex = displayedEpisodes.indexOf(targetEp)
                            if (epIndex >= 0) {
                                val baseIndex = 5 + (if (castList.isNotEmpty()) 1 else 0) + (if (isSeries && seasonGroups.isNotEmpty()) 1 else 0) + 1
                                listState.animateScrollToItem((baseIndex + epIndex).coerceAtLeast(0))
                                highlightedEpisodeId = targetEp.id
                                kotlinx.coroutines.delay(1600L)
                                if (highlightedEpisodeId == targetEp.id) highlightedEpisodeId = null
                            }
                        }
                    }
                )
            }

            // Giao diện khi tìm kiếm không có tập nào khớp
            if (displayedEpisodes.isEmpty() && episodeSearchQuery.isNotBlank()) {
                item(key = "movie_episodes_empty_state") {
                    EpisodeNavigatorEmptyState(
                        query = episodeSearchQuery,
                        onClear = { episodeSearchQuery = "" }
                    )
                }
            }

            // Danh sách các Card Tập Phim chuẩn Nuvio
            itemsIndexed(
                items = displayedEpisodes,
                key = { index, ep -> "${currentServer.serverName}_${ep.id}_${ep.slug}_$index" }
            ) { displayIndex, episode ->
                val actualIndex = allEpisodes.indexOf(episode).coerceAtLeast(0)
                val isCurrentPlaying = watchHistory?.lastEpisodeId == episode.id ||
                    watchHistory?.lastEpisodeName == episode.name
                val isHighlighted = highlightedEpisodeId == episode.id

                val watchProgressRatio = remember(watchHistory, isCurrentPlaying) {
                    if (isCurrentPlaying && (watchHistory?.durationMs ?: 0L) > 0L) {
                        (watchHistory?.positionMs?.toFloat() ?: 0f) / (watchHistory?.durationMs?.toFloat() ?: 1f)
                    } else 0f
                }

                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    MovieEpisodeCardItem(
                        detail = detail,
                        episode = episode,
                        seasonNumber = activeSeasonGroup?.seasonNumber ?: 1,
                        actualIndex = actualIndex,
                        isSeries = isSeries,
                        isCurrentPlaying = isCurrentPlaying,
                        isHighlighted = isHighlighted,
                        watchProgressRatio = watchProgressRatio,
                        onClick = {
                            onPlayEpisode(currentServer, actualIndex, episode, isEpisodeSortDescending)
                        }
                    )
                }
            }
        }

        // =========================================================================
        // 8.5. TRAILER & BẢNG THÔNG TIN CHI TIẾT (CHUẨN NUVIO)
        // =========================================================================
        if (!detail.trailerUrl.isNullOrBlank()) {
            item(key = "movie_trailer_section") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.detail_trailer),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val youtubeVideoId = remember(detail.trailerUrl) {
                        detail.trailerUrl?.let { url ->
                            Regex("""(?:v=|youtu\.be/|embed/)([a-zA-Z0-9_-]{11})""").find(url)?.groupValues?.get(1)
                        }
                    }
                    val trailerThumbnail = remember(youtubeVideoId, detail.backdropUrl, detail.posterUrl) {
                        if (youtubeVideoId != null) {
                            "https://img.youtube.com/vi/$youtubeVideoId/hqdefault.jpg"
                        } else {
                            detail.backdropUrl ?: detail.posterUrl
                        }
                    }

                    Surface(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(detail.trailerUrl))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .width(200.dp)
                            .height(116.dp)
                            .tvFocusableItem(cornerRadius = 12.dp, focusedScale = 1.03f)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (trailerThumbnail != null) {
                                AsyncImage(
                                    model = remember(trailerThumbnail) {
                                        ImageRequest.Builder(context)
                                            .data(trailerThumbnail)
                                            .crossfade(true)
                                            .build()
                                    },
                                    contentDescription = stringResource(R.string.detail_trailer),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(topStart = 4.dp, bottomEnd = 4.dp),
                                color = Color.Black.copy(alpha = 0.75f),
                                modifier = Modifier.align(Alignment.BottomStart)
                            ) {
                                Text(
                                    text = stringResource(R.string.detail_trailer),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bảng "Thông tin" chi tiết (Release info, duration, country, genres, status...)
        if (infoRows.isNotEmpty() && (!detail.tmdbId.isNullOrBlank() || !isSeries)) {
            item(key = "movie_additional_info_section") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.detail_info_section_title),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    infoRows.forEachIndexed { index, (labelRes, value) ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(labelRes),
                                    fontSize = 13.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = value,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 16.dp),
                                    fontSize = 13.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.End,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (index < infoRows.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(0.6.dp)
                                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                )
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 9. DƯỚI CÙNG: PHIM TƯƠNG TỰ & DIỄN VIÊN ĐỀ XUẤT TỪ TMDB (4 ROWS)
        // =========================================================================
        if (actor1Movies.isNotEmpty() && !actor1Name.isNullOrBlank()) {
            item(key = "movie_rec_actor1") {
                Spacer(modifier = Modifier.height(16.dp))
                MovieSectionRow(
                    title = "Phim của $actor1Name",
                    movies = actor1Movies,
                    onMovieClick = onMovieClick
                )
            }
        }

        if (actor2Movies.isNotEmpty() && !actor2Name.isNullOrBlank()) {
            item(key = "movie_rec_actor2") {
                Spacer(modifier = Modifier.height(12.dp))
                MovieSectionRow(
                    title = "Phim của $actor2Name",
                    movies = actor2Movies,
                    onMovieClick = onMovieClick
                )
            }
        }

        if (genreMovies.isNotEmpty() && !genreName.isNullOrBlank()) {
            item(key = "movie_rec_genre") {
                Spacer(modifier = Modifier.height(12.dp))
                MovieSectionRow(
                    title = "Phim thể loại $genreName",
                    movies = genreMovies,
                    onMovieClick = onMovieClick
                )
            }
        }

        if (countryMovies.isNotEmpty() && !countryName.isNullOrBlank()) {
            item(key = "movie_rec_country") {
                Spacer(modifier = Modifier.height(12.dp))
                MovieSectionRow(
                    title = "Phim $countryName nổi bật",
                    movies = countryMovies,
                    onMovieClick = onMovieClick
                )
            }
        }
    }
}

@Composable
private fun MovieEpisodeCardItem(
    detail: MovieDetail,
    episode: EpisodeData,
    seasonNumber: Int,
    actualIndex: Int,
    isSeries: Boolean,
    isCurrentPlaying: Boolean,
    isHighlighted: Boolean = false,
    watchProgressRatio: Float,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val hasTmdbId = !detail.tmdbId.isNullOrBlank()
    val sNum = episode.season ?: seasonNumber
    val epNum = episode.number ?: (actualIndex + 1)
    val badgeCode = if (isSeries) "M$sNum T$epNum" else "T$epNum"

    // Hàng 1: Tiêu đề
    // - Phim Bộ: Số tập - Tiêu đề tập (Nếu có TMDB ID) hoặc Số tập - Tên Phim (Nếu không có TMDB)
    // - Phim Lẻ: Giữ yêu cầu như phim bộ
    val cardTitle = remember(detail.name, episode.name, isSeries, episode.number, actualIndex, hasTmdbId) {
        val rawClean = episode.name.trim().replace(Regex("^(Tập\\s*)+", RegexOption.IGNORE_CASE), "Tập ").trim()
        if (isSeries) {
            if (hasTmdbId) {
                val cleanEpName = if (rawClean.isBlank() || rawClean.equals("phim lẻ", ignoreCase = true) || rawClean.equals("full", ignoreCase = true)) {
                    "Tập $epNum"
                } else if (!rawClean.startsWith("tập", ignoreCase = true) && !rawClean.startsWith("t", ignoreCase = true) && rawClean.toIntOrNull() != null) {
                    "Tập $rawClean"
                } else {
                    rawClean
                }
                if (cleanEpName.startsWith("tập", ignoreCase = true) || cleanEpName.startsWith("t$epNum", ignoreCase = true)) {
                    cleanEpName.replace(Regex("^(Tập\\s*)+", RegexOption.IGNORE_CASE), "Tập ")
                } else {
                    "Tập $epNum - $cleanEpName"
                }
            } else {
                if (rawClean.startsWith("tập", ignoreCase = true)) {
                    rawClean
                } else {
                    "Tập $epNum - ${detail.name}"
                }
            }
        } else {
            if (hasTmdbId && rawClean.isNotBlank() && !rawClean.equals("phim lẻ", ignoreCase = true) && !rawClean.equals("full", ignoreCase = true)) {
                rawClean.replace(Regex("^(Tập\\s*)+", RegexOption.IGNORE_CASE), "Tập ")
            } else {
                detail.name
            }
        }
    }

    // Hàng 2: Ngày tháng phát hành - Điểm TMDB
    val releaseDateStr = episode.releaseDate?.takeIf { it.isNotBlank() }
        ?: if (!isSeries) detail.year?.toString() else null
    val formattedDate = remember(releaseDateStr) {
        formatReleaseDateForDisplay(releaseDateStr)
    }
    val epRating = episode.rating?.takeIf { it > 0.0 }
        ?: if (!isSeries) detail.rating?.takeIf { it > 0.0 } else null

    // Hàng 3: Nội dung tóm tắt của tập phim (Nếu có TMDB ID)
    val epOverview = episode.overview?.trim()?.takeIf { it.isNotBlank() }
        ?: if (!isSeries) detail.description?.trim()?.takeIf { it.isNotBlank() } else null

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                else if (isCurrentPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            if (isHighlighted) 2.dp else 1.dp,
            if (isHighlighted) MaterialTheme.colorScheme.primary
            else if (isCurrentPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(118.dp)
            .tvFocusableItem(cornerRadius = 12.dp, focusedScale = 1.02f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail 16:9 với góc bo tròn
            val imageUrl = episode.thumbnail ?: detail.backdropUrl ?: detail.posterUrl
            Box(
                modifier = Modifier
                    .width(132.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = remember(imageUrl) {
                        ImageRequest.Builder(context)
                            .data(imageUrl)
                            .crossfade(true)
                            .build()
                    },
                    contentDescription = episode.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Lớp phủ tối nhẹ tăng độ tương phản
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.22f))
                )

                // Badge Mùa & Tập (M1 T1) ở góc trên bên trái
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.78f),
                    modifier = Modifier
                        .padding(5.dp)
                        .align(Alignment.TopStart)
                ) {
                    Text(
                        text = badgeCode,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 2.dp)
                    )
                }

                // Nếu đang phát, hiện icon Play ở giữa
                if (isCurrentPlaying) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Thanh tiến độ xem ở đáy thumbnail
                if (watchProgressRatio > 0f) {
                    LinearProgressIndicator(
                        progress = { watchProgressRatio.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Black.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Cột thông tin 3 hàng bên phải
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
            ) {
                // Hàng 1: Tiêu đề tập
                Text(
                    text = cardTitle,
                    fontSize = 14.5.sp,
                    fontWeight = if (isCurrentPlaying) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isCurrentPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Hàng 2: Ngày phát hành & Điểm TMDB
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!formattedDate.isNullOrBlank()) {
                        Text(
                            text = formattedDate,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (epRating != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF00B4D8).copy(alpha = 0.18f),
                            border = BorderStroke(0.6.dp, Color(0xFF00B4D8).copy(alpha = 0.65f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 1.5.dp)
                            ) {
                                Text(
                                    text = "TMDB",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF00B4D8)
                                )
                                Spacer(modifier = Modifier.width(3.5.dp))
                                Text(
                                    text = String.format(java.util.Locale.US, "%.1f", epRating),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00B4D8)
                                )
                            }
                        }
                    }

                    if (detail.quality != null && formattedDate == null) {
                        Text(
                            text = detail.quality ?: "HD",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isCurrentPlaying) {
                        Text(
                            text = "•",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.detail_status_watching),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Hàng 3: Nội dung tóm tắt của tập phim (Nếu có TMDB ID)
                if (hasTmdbId && !epOverview.isNullOrBlank()) {
                    Text(
                        text = epOverview,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun formatReleaseDateForDisplay(dateStr: String?): String? {
    if (dateStr.isNullOrBlank()) return null
    val raw = dateStr.trim()
    val match = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})""").find(raw)
    if (match != null) {
        val (y, m, d) = match.destructured
        val day = d.toIntOrNull() ?: d
        val month = m.toIntOrNull() ?: m
        return "$day Tháng $month, $y"
    }
    val yearMatch = Regex("""^(\d{4})""").find(raw)
    return yearMatch?.value ?: raw
}

private fun String.initials(): String {
    val parts = trim().split(" ").filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts.first().first().uppercaseChar()}${parts.last().first().uppercaseChar()}"
        parts.size == 1 -> parts.first().take(2).uppercase()
        else -> ""
    }
}

@Composable
private fun MovieSectionRow(
    title: String,
    movies: List<MovieItem>,
    onMovieClick: ((providerId: String, slug: String) -> Unit)?
) {
    if (movies.isEmpty()) return

    val uniqueMovies = remember(movies) {
        movies.distinctBy { it.tmdbId ?: it.slug }
    }

    Column {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(
                items = uniqueMovies,
                key = { index, movie -> "${movie.tmdbId ?: movie.slug}_$index" }
            ) { _, movie ->
                MovieCard(
                    movie = movie,
                    onClick = {
                        onMovieClick?.invoke("tmdb", movie.slug)
                    },
                    modifier = Modifier.width(115.dp)
                )
            }
        }
    }
}

