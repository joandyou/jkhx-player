package com.jkhx.player.ui.screens.detail

import android.content.Context
import android.content.Intent
import android.widget.Toast
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

    // 1. Phân giải danh sách máy chủ (servers)
    var selectedServerIndex by rememberSaveable(detail.slug) { mutableIntStateOf(0) }
    val safeServerIndex = selectedServerIndex.coerceIn(0, (detail.episodes.size - 1).coerceAtLeast(0))
    val currentServer = detail.episodes.getOrNull(safeServerIndex)
        ?: detail.episodes.firstOrNull()
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

    // Đảm bảo selectedSeasonNumber luôn hợp lệ
    val activeSeasonGroup = seasonGroups.find { it.seasonNumber == selectedSeasonNumber }
        ?: seasonGroups.firstOrNull()

    val currentSeasonEpisodes = activeSeasonGroup?.episodes ?: allEpisodes
    val displayedEpisodes = remember(currentSeasonEpisodes, isEpisodeSortDescending) {
        if (isEpisodeSortDescending) currentSeasonEpisodes.reversed() else currentSeasonEpisodes
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
                    val sNum = resumeEpisode!!.season ?: 1
                    val epNum = resumeEpisode.number ?: 1
                    val epCode = if (isSeries) "M$sNum T$epNum" else resumeEpisode.name
                    stringResource(R.string.detail_resume_episode, epCode)
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
                        containerColor = Color(0xFF388E3C), // Màu xanh lá rực rỡ đặc trưng Nuvio
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.03f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = playText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
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
                        modifier = Modifier.padding(bottom = 6.dp)
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
                                            text = cast.name.take(1).uppercase(),
                                            fontSize = 20.sp,
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
        // 7. MULTI-SERVER SELECTION (NẾU CÓ NHIỀU NGUỒN PHÁT, BỎ CHỮ "NGUỒN PHÁT")
        // =========================================================================
        if (detail.episodes.size > 1) {
            item(key = "movie_server_chips") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(detail.episodes) { index, srv ->
                        val isSelected = index == safeServerIndex
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedServerIndex = index },
                            label = {
                                Text(
                                    text = srv.serverName,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.05f)
                        )
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
                                    onClick = { selectedSeasonNumber = sGroup.seasonNumber },
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

            // Thanh đếm số tập & nút đảo thứ tự (Sort asc/desc)
            item(key = "movie_episodes_control_bar") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isSeries) {
                            stringResource(R.string.detail_episodes_progress, displayedEpisodes.size, currentSeasonEpisodes.size)
                        } else {
                            stringResource(R.string.detail_episodes_count, displayedEpisodes.size)
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (displayedEpisodes.size > 1) {
                        TextButton(
                            onClick = onToggleEpisodeSort,
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
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Danh sách các Card Tập Phim chuẩn Nuvio
            itemsIndexed(
                items = displayedEpisodes,
                key = { _, ep -> "${currentServer.serverName}_${ep.id}_${ep.slug}" }
            ) { displayIndex, episode ->
                val actualIndex = allEpisodes.indexOf(episode).coerceAtLeast(0)
                val isCurrentPlaying = watchHistory?.lastEpisodeId == episode.id ||
                    watchHistory?.lastEpisodeName == episode.name

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
                        watchProgressRatio = watchProgressRatio,
                        onClick = {
                            onPlayEpisode(currentServer, actualIndex, episode, isEpisodeSortDescending)
                        }
                    )
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
    watchProgressRatio: Float,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isCurrentPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            1.dp,
            if (isCurrentPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusableItem(cornerRadius = 12.dp, focusedScale = 1.02f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail 16:9 với góc bo tròn
            Box(
                modifier = Modifier
                    .width(115.dp)
                    .height(68.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = detail.backdropUrl ?: detail.posterUrl,
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
                val sNum = episode.season ?: seasonNumber
                val epNum = episode.number ?: (actualIndex + 1)
                val badgeCode = if (isSeries) "M$sNum T$epNum" else "T$epNum"

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier
                        .padding(4.dp)
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
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
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

            // Thông tin tập phim bên phải
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = episode.name,
                    fontSize = 14.sp,
                    fontWeight = if (isCurrentPlaying) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isCurrentPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (detail.quality != null) {
                        Text(
                            text = detail.quality ?: "HD",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (detail.year != null) {
                        Text(
                            text = "•",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${detail.year}",
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
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = if (isCurrentPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(18.dp)
            )
        }
    }
}

@Composable
private fun MovieSectionRow(
    title: String,
    movies: List<MovieItem>,
    onMovieClick: ((providerId: String, slug: String) -> Unit)?
) {
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
            items(movies, key = { it.slug }) { movie ->
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

