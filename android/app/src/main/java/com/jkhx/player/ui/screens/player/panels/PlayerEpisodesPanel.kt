package com.jkhx.player.ui.screens.player.panels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jkhx.player.ActivePlayerSession
import com.jkhx.player.R
import com.jkhx.player.core.model.EpisodeData
import com.jkhx.player.core.model.EpisodeServer
import com.jkhx.player.ui.theme.tvFocusableItem
import kotlinx.coroutines.delay

@Composable
internal fun PlayerEpisodesPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    initialFocusRequester: FocusRequester,
    movieName: String,
    activeEpisodes: List<EpisodeData>,
    currentEpisodeIndex: Int,
    availableServers: List<EpisodeServer>,
    activeServerName: String,
    onServerSelect: (EpisodeServer) -> Unit,
    onEpisodeSelect: (Int, EpisodeData) -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
        exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it })
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .pointerInput(Unit) {
                    detectTapGestures { onDismiss() }
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .widthIn(min = 360.dp, max = 460.dp)
                    .width(440.dp)
                    .background(
                        Color(0xFF141715).copy(alpha = 0.95f),
                        RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
                    )
                    .pointerInput(Unit) {
                        detectTapGestures { }
                    }
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                LaunchedEffect(visible) {
                    if (visible) {
                        delay(60)
                        try {
                            initialFocusRequester.requestFocus()
                        } catch (_: Exception) {}
                    }
                }

                // Phân nhóm Mùa
                val seasonGroups = remember(activeEpisodes) {
                    val hasExplicit = activeEpisodes.any { (it.season ?: 0) > 0 }
                    val map = if (hasExplicit) {
                        activeEpisodes.groupBy { it.season ?: 1 }
                    } else {
                        val byName = activeEpisodes.groupBy { ep ->
                            val match = Regex("""(?:mùa|phần|season|s)\s*(\d+)""", RegexOption.IGNORE_CASE).find(ep.name)
                            match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
                        }
                        if (byName.keys.size > 1) byName else mapOf(1 to activeEpisodes)
                    }
                    map.entries.sortedBy { it.key }
                }

                val availableSeasons = remember(seasonGroups) {
                    seasonGroups.map { it.key }
                }

                val currentEpisode = activeEpisodes.getOrNull(currentEpisodeIndex)
                val initialSeason = currentEpisode?.season?.takeIf { it > 0 }
                    ?: availableSeasons.firstOrNull() ?: 1

                var selectedSeason by rememberSaveable(activeEpisodes.size, initialSeason) {
                    mutableIntStateOf(initialSeason)
                }

                if (availableSeasons.isNotEmpty() && selectedSeason !in availableSeasons) {
                    selectedSeason = availableSeasons.first()
                }

                val seasonEpisodes = remember(seasonGroups, selectedSeason) {
                    seasonGroups.find { it.key == selectedSeason }?.value ?: activeEpisodes
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Header: "Tập phim" + Nút "Đóng" pill button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.player_episodes_title_nuvio),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Surface(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                            modifier = Modifier
                                .focusRequester(initialFocusRequester)
                                .tvFocusableItem(cornerRadius = 20.dp, focusedScale = 1.08f)
                        ) {
                            Text(
                                text = stringResource(R.string.common_close),
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Multi-Server selection (nếu có nhiều nguồn phát)
                    if (availableServers.size > 1) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(availableServers) { srv ->
                                val isSelected = srv.serverName == activeServerName
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f)),
                                    modifier = Modifier
                                        .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.06f)
                                        .clickable {
                                            if (srv.serverName != activeServerName) {
                                                onServerSelect(srv)
                                            }
                                        }
                                ) {
                                    Text(
                                        text = srv.serverName,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Hàng chọn Mùa (Season Selector Row dạng viên thuốc tròn Nuvio)
                    if (availableSeasons.size > 1) {
                        Spacer(modifier = Modifier.height(14.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(availableSeasons, key = { it }) { seasonNum ->
                                val isSelected = selectedSeason == seasonNum
                                val seasonShape = RoundedCornerShape(24.dp)
                                Surface(
                                    onClick = { selectedSeason = seasonNum },
                                    shape = seasonShape,
                                    color = if (isSelected) Color.White else Color(0xFF222824),
                                    border = BorderStroke(1.dp, if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.12f)),
                                    modifier = Modifier.tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.08f)
                                ) {
                                    Text(
                                        text = stringResource(R.string.detail_season_n, seasonNum),
                                        color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.85f),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.5.sp,
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Danh sách tập phim dạng cuộn dọc (LazyColumn)
                    val episodeListState = rememberLazyListState()

                    LaunchedEffect(visible, selectedSeason) {
                        if (visible && seasonEpisodes.isNotEmpty()) {
                            val currentEpInSeason = seasonEpisodes.indexOfFirst {
                                it.id == currentEpisode?.id || (it.season == currentEpisode?.season && it.number == currentEpisode?.number)
                            }
                            if (currentEpInSeason >= 0) {
                                try {
                                    episodeListState.scrollToItem(currentEpInSeason.coerceAtLeast(0))
                                } catch (_: Exception) {}
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    LazyColumn(
                        state = episodeListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        itemsIndexed(
                            items = seasonEpisodes,
                            key = { idx, ep -> "${ep.id}_${ep.season}_${ep.number}_$idx" }
                        ) { seasonIdx, episode ->
                            val isCurrent = episode.id == currentEpisode?.id ||
                                (episode.season == currentEpisode?.season && episode.number == currentEpisode?.number) ||
                                (currentEpisodeIndex >= 0 && activeEpisodes.getOrNull(currentEpisodeIndex)?.id == episode.id)

                            val originalIndex = activeEpisodes.indexOf(episode).takeIf { it >= 0 } ?: seasonIdx

                            NuvioPlayerEpisodeCard(
                                episode = episode,
                                seasonNumber = selectedSeason,
                                actualIndex = originalIndex,
                                isCurrent = isCurrent,
                                movieFallbackImage = ActivePlayerSession.movieDetail?.backdropUrl
                                    ?: ActivePlayerSession.movieDetail?.posterUrl
                                    ?: ActivePlayerSession.movieThumb,
                                onClick = {
                                    onEpisodeSelect(originalIndex, episode)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NuvioPlayerEpisodeCard(
    episode: EpisodeData,
    seasonNumber: Int,
    actualIndex: Int,
    isCurrent: Boolean,
    movieFallbackImage: String?,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val cardShape = RoundedCornerShape(16.dp)

    Surface(
        onClick = onClick,
        shape = cardShape,
        color = if (isCurrent) Color(0xFF4CAF50).copy(alpha = 0.08f) else Color(0xFF1B221E),
        border = BorderStroke(
            width = if (isCurrent) 2.dp else 1.dp,
            color = if (isCurrent) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.08f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusableItem(cornerRadius = 16.dp, focusedScale = 1.02f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Thumbnail 16:9 với góc bo tròn
            val thumbUrl = episode.thumbnail ?: movieFallbackImage
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (!thumbUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = remember(thumbUrl) {
                            ImageRequest.Builder(context)
                                .data(thumbUrl)
                                .size(240, 144)
                                .crossfade(true)
                                .build()
                        },
                        contentDescription = episode.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Lớp phủ tối nhẹ tăng độ tương phản
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.22f))
                )

                // Badge M1 T1 ở góc dưới bên trái (Alignment.BottomStart) theo phong cách Nuvio
                val sNum = episode.season ?: seasonNumber
                val epNum = episode.number ?: (actualIndex + 1)
                val badgeCode = "M$sNum T$epNum"

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.78f),
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.BottomStart)
                ) {
                    Text(
                        text = badgeCode,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                // Nếu đang phát, hiện icon Play ở giữa
                if (isCurrent) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF4CAF50),
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // 2. Cột thông tin tập phim bên phải
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Hàng 1: Tiêu đề tập
                val epNum = episode.number ?: (actualIndex + 1)
                val cleanTitle = remember(episode.name, epNum) {
                    val raw = episode.name.trim()
                    if (raw.isBlank() || raw.equals("phim lẻ", true) || raw.equals("full", true)) {
                        "Tập $epNum"
                    } else if (raw.startsWith("tập", true) || raw.startsWith("t$epNum", true)) {
                        raw
                    } else if (raw.toIntOrNull() != null) {
                        "Tập $raw"
                    } else {
                        "Tập $epNum - $raw"
                    }
                }

                Text(
                    text = cleanTitle,
                    color = Color.White,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Hàng 2: Ngày phát hành
                val formattedDate = remember(episode.releaseDate) {
                    formatReleaseDateForDisplay(episode.releaseDate)
                }

                if (!formattedDate.isNullOrBlank()) {
                    Text(
                        text = formattedDate,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Hàng 3 (tùy chọn nếu có overview tóm tắt)
                val overview = episode.overview?.trim()
                if (!overview.isNullOrBlank()) {
                    Text(
                        text = overview,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
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
