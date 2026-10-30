package com.jkhx.player.ui.screens.player.panels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jkhx.player.R
import com.jkhx.player.core.model.EpisodeData
import com.jkhx.player.core.model.EpisodeStream
import com.jkhx.player.ui.screens.stream.UnifiedStreamItem
import com.jkhx.player.ui.theme.tvFocusableItem
import kotlinx.coroutines.delay

@Composable
internal fun PlayerSourcePanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    initialFocusRequester: FocusRequester,
    movieName: String,
    movieTmdbId: String?,
    drawerStreams: List<UnifiedStreamItem>,
    activeUnifiedStream: UnifiedStreamItem?,
    activeProviderId: String,
    activeServerName: String,
    resolvedStream: EpisodeStream?,
    currentEpisode: EpisodeData,
    getPluginName: (String) -> String,
    isScanningSources: Boolean,
    sourceRotationAngle: Float,
    onRefreshSources: () -> Unit,
    onSelectStream: (UnifiedStreamItem) -> Unit,
    onCopyStreamUrl: (String) -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.20f))
                .pointerInput(Unit) {
                    detectTapGestures { onDismiss() }
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .widthIn(min = 340.dp, max = 460.dp)
                    .background(
                        Color.Black.copy(alpha = 0.52f),
                        RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                    )
                    .border(
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                    )
                    .pointerInput(Unit) {
                        detectTapGestures { }
                    }
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                var selectedSourceFilter by rememberSaveable { mutableStateOf<String?>(null) }

                val effectiveDrawerStreams = remember(drawerStreams, activeUnifiedStream, activeProviderId, activeServerName, resolvedStream, currentEpisode, movieName) {
                    val baseList = if (drawerStreams.isNotEmpty()) {
                        if (activeUnifiedStream != null && drawerStreams.none { it.id == activeUnifiedStream?.id || (it.providerId == activeUnifiedStream?.providerId && it.streamUrl == activeUnifiedStream?.streamUrl) }) {
                            listOf(activeUnifiedStream) + drawerStreams
                        } else {
                            drawerStreams
                        }
                    } else if (activeUnifiedStream != null) {
                        listOf(activeUnifiedStream)
                    } else {
                        val pName = getPluginName(activeProviderId)
                        val sUrl = resolvedStream?.url ?: currentEpisode.id
                        listOf(
                            UnifiedStreamItem(
                                id = "${activeProviderId}_${activeServerName}_${currentEpisode.id}",
                                groupName = pName,
                                providerName = pName,
                                serverName = activeServerName.ifBlank { "Mặc định" },
                                title = "$movieName - ${currentEpisode.name}",
                                techInfo = if (resolvedStream?.type == "hls") "⚡ HLS Direct (M3U8)" else "⚡ Direct Stream",
                                qualityBadge = "HD",
                                streamUrl = sUrl,
                                streamType = resolvedStream?.type ?: "hls",
                                headers = resolvedStream?.headers ?: emptyMap(),
                                subtitles = resolvedStream?.subtitles ?: emptyList(),
                                providerId = activeProviderId,
                                targetEpisode = currentEpisode
                            )
                        )
                    }
                    baseList
                }

                val playingStreamId = remember(effectiveDrawerStreams, activeUnifiedStream, activeProviderId, activeServerName, resolvedStream) {
                    effectiveDrawerStreams.firstOrNull {
                        activeUnifiedStream != null && it.id == activeUnifiedStream?.id
                    }?.id ?: effectiveDrawerStreams.firstOrNull {
                        activeUnifiedStream != null && it.providerId == activeProviderId && it.serverName == activeServerName && it.streamUrl == activeUnifiedStream?.streamUrl
                    }?.id ?: effectiveDrawerStreams.firstOrNull {
                        resolvedStream != null && it.streamUrl.isNotBlank() && it.streamUrl == resolvedStream?.url && it.providerId == activeProviderId
                    }?.id ?: effectiveDrawerStreams.firstOrNull {
                        it.providerId == activeProviderId && it.serverName.equals(activeServerName, ignoreCase = true)
                    }?.id ?: effectiveDrawerStreams.firstOrNull {
                        it.providerId == activeProviderId
                    }?.id
                }

                val orderedDrawerStreams = remember(effectiveDrawerStreams, playingStreamId) {
                    if (playingStreamId == null) effectiveDrawerStreams
                    else {
                        val playing = effectiveDrawerStreams.filter { it.id == playingStreamId }
                        val others = effectiveDrawerStreams.filterNot { it.id == playingStreamId }
                        playing + others
                    }
                }

                val availableSourceGroups = remember(orderedDrawerStreams) {
                    orderedDrawerStreams.map { it.groupName }.distinct()
                }
                val displayedStreams = remember(orderedDrawerStreams, selectedSourceFilter) {
                    if (selectedSourceFilter == null) orderedDrawerStreams
                    else orderedDrawerStreams.filter { it.groupName == selectedSourceFilter }
                }

                LaunchedEffect(visible) {
                    if (visible) {
                        delay(60)
                        try {
                            initialFocusRequester.requestFocus()
                        } catch (_: Exception) {}
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. Header trên cùng: Tiêu đề "Nguồn phát & Máy chủ" & Nút "Đóng" (Hình 3)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.player_sources_title),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier
                                .focusRequester(initialFocusRequester)
                                .tvFocusableItem(cornerRadius = 16.dp, focusedScale = 1.08f),
                            onClick = onDismiss
                        ) {
                            Text(
                                text = stringResource(R.string.common_close),
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // 2. Subtitle: Tên phim hiện tại & badge TMDB nếu có (Hình 3)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = movieName,
                            color = Color.White.copy(alpha = 0.70f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (!movieTmdbId.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "TMDB #${movieTmdbId}",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // 3. Hàng lọc Filter Chips ngang: [Refresh quay] + [Tất cả] + [Torrentio] + [Ytztvio]... (Hình 3)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Nút tròn Reload quét lại nguồn
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.10f),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(36.dp)
                                .tvFocusableItem(cornerRadius = 18.dp, focusedScale = 1.15f)
                                .clickable(enabled = !isScanningSources) { onRefreshSources() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.streams_reload),
                                    tint = if (isScanningSources) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .rotate(sourceRotationAngle)
                                )
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).focusGroup()
                        ) {
                            // Chip "Tất cả"
                            item {
                                FilterChip(
                                    selected = selectedSourceFilter == null,
                                    onClick = { selectedSourceFilter = null },
                                    modifier = Modifier.tvFocusableItem(cornerRadius = 16.dp, focusedScale = 1.08f),
                                    label = {
                                        Text(
                                            stringResource(R.string.streams_all_filter),
                                            fontSize = 12.sp,
                                            fontWeight = if (selectedSourceFilter == null) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White,
                                        containerColor = Color.White.copy(alpha = 0.08f),
                                        labelColor = Color.White.copy(alpha = 0.75f)
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    border = null
                                )
                            }

                            // Chips theo từng Provider / Addon
                            items(availableSourceGroups) { groupName ->
                                val isSelected = selectedSourceFilter == groupName
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedSourceFilter = if (isSelected) null else groupName
                                    },
                                    modifier = Modifier.tvFocusableItem(cornerRadius = 16.dp, focusedScale = 1.08f),
                                    label = {
                                        Text(
                                            text = groupName,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White,
                                        containerColor = Color.White.copy(alpha = 0.08f),
                                        labelColor = Color.White.copy(alpha = 0.75f)
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    border = null
                                )
                            }
                        }
                    }

                    // Trạng thái quét nguồn phát
                    if (isScanningSources) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.8.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.player_sources_searching),
                                color = Color.LightGray,
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    // 4. Danh sách các máy chủ & luồng phát dạng Cards hiển thị đầy đủ thông số chi tiết + Nút Copy
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = displayedStreams,
                            key = { it.id }
                        ) { item ->
                            val isThisStreamPlaying = item.id == playingStreamId

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvFocusableItem(cornerRadius = 12.dp, focusedScale = 1.02f),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isThisStreamPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f),
                                border = if (isThisStreamPlaying) BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                                onClick = { onSelectStream(item) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // Dòng 1: Icon + Tên Provider + Badge Đang phát
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isThisStreamPlaying) Icons.Default.PlayCircle else Icons.Default.Tv,
                                                contentDescription = null,
                                                tint = if (isThisStreamPlaying) MaterialTheme.colorScheme.primary else Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = item.providerName,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (isThisStreamPlaying) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.player_sources_current_badge),
                                                        color = Color.Black,
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Dòng 2: Tên Server / Audio (Vietsub #1, Thuyết Minh...)
                                        if (item.serverName.isNotBlank() && !item.serverName.equals(item.providerName, ignoreCase = true)) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = item.serverName,
                                                color = Color.White.copy(alpha = 0.9f),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        // Dòng 3: Tiêu đề chi tiết tập phim
                                        if (item.title.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = item.title,
                                                color = Color.White.copy(alpha = 0.78f),
                                                fontSize = 11.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Dòng 4: Thông tin kết nối kỹ thuật + Huy hiệu chất lượng (FHD, HD, 4K, WEB)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = item.techInfo,
                                                fontSize = 11.sp,
                                                color = Color.LightGray,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )

                                            if (item.qualityBadge != null) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color.White.copy(alpha = 0.10f),
                                                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.25f))
                                                ) {
                                                    Text(
                                                        text = item.qualityBadge,
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Bên phải: Tên nhóm nguồn + Nút Copy link luồng phát
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(start = 10.dp)
                                    ) {
                                        Text(
                                            text = item.groupName,
                                            fontSize = 10.sp,
                                            color = Color.White.copy(alpha = 0.55f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 110.dp)
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        IconButton(
                                            onClick = { onCopyStreamUrl(item.streamUrl) },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                                    shape = CircleShape
                                                )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = stringResource(R.string.stream_copied_toast),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (!isScanningSources && displayedStreams.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.player_sources_no_other_found),
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }
}

