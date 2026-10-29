package com.jkhx.player.ui.screens.stream

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jkhx.player.core.cache.SessionCacheManager
import com.jkhx.player.ActivePlayerSession
import com.jkhx.player.JkhxApplication
import com.jkhx.player.R
import com.jkhx.player.core.model.EpisodeData
import com.jkhx.player.core.model.MovieDetail
import com.jkhx.player.ui.theme.tvFocusableItem
import com.jkhx.player.ui.screens.stream.badge.*
import com.jkhx.player.ui.theme.AppTheme
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamSelectionScreen(
    providerId: String,
    movieDetail: MovieDetail,
    episode: EpisodeData,
    onBack: () -> Unit,
    onStreamSelected: (UnifiedStreamItem) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val aggregator = remember { StreamAggregator() }

    val context = LocalContext.current
    val settingsManager = remember { JkhxApplication.instance.settingsManager }
    val badgeRulesJson by settingsManager.streamBadgeRulesJson.collectAsState()
    val badgePlacement by settingsManager.streamBadgePlacement.collectAsState()
    val showSizeBadges by settingsManager.showStreamSizeBadges.collectAsState()
    val showAddonIcon by settingsManager.showAddonIcon.collectAsState()

    val badgeRules = remember(badgeRulesJson) {
        StreamBadgeJsonHelper.decodeRules(badgeRulesJson)
    }
    val compiledFilters = remember(badgeRules) {
        StreamBadgeMatcher.getCompiledFilters(badgeRules)
    }

    val onCopyStream: (UnifiedStreamItem) -> Unit = remember(context) {
        { streamItem ->
            val url = streamItem.streamUrl.substringBefore("#").substringBefore("|").ifBlank { streamItem.streamUrl }
            if (url.isNotBlank()) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Stream Link", url))
                Toast.makeText(context, context.getString(R.string.stream_copied_toast), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val onDownloadShareStream: (UnifiedStreamItem) -> Unit = remember(context) {
        { streamItem ->
            val url = streamItem.streamUrl
            if (url.isNotBlank()) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Stream Link", url))
                Toast.makeText(context, context.getString(R.string.detail_copied_stream_link), Toast.LENGTH_SHORT).show()
                try {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, streamItem.title)
                        putExtra(Intent.EXTRA_TEXT, url)
                    }
                    val chooser = Intent.createChooser(shareIntent, context.getString(R.string.detail_share_stream_link)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooser)
                } catch (_: Exception) {}
            }
        }
    }

    var allStreams by remember { mutableStateOf<List<UnifiedStreamItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedGroupFilter by rememberSaveable { mutableStateOf(ActivePlayerSession.lastSelectedStreamGroup) }
    var refreshKey by remember { mutableIntStateOf(0) }

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

    val streamCacheKey = remember(providerId, movieDetail.slug, episode.id) {
        "$providerId:${movieDetail.slug}:${episode.id}"
    }

    LaunchedEffect(refreshKey, streamCacheKey) {
        val isForceRefresh = refreshKey > 0
        if (!isForceRefresh) {
            val cached = SessionCacheManager.getStreams(streamCacheKey)
            if (!cached.isNullOrEmpty()) {
                allStreams = cached
                ActivePlayerSession.aggregatedStreams = cached
                isLoading = false
                return@LaunchedEffect
            }
        } else {
            SessionCacheManager.removeStreams(streamCacheKey)
        }

        isLoading = true
        allStreams = emptyList()
        try {
            aggregator.aggregateStreams(providerId, movieDetail, episode)
                .catch {
                    // Xử lý lỗi nếu có ngoại lệ bất thường trong lúc tổng hợp luồng
                }
                .collect { list ->
                    allStreams = list
                    ActivePlayerSession.aggregatedStreams = list
                    SessionCacheManager.putStreams(streamCacheKey, list)
                }
        } finally {
            isLoading = false
        }
    }

    val availableGroups = remember(allStreams) {
        allStreams.map { it.groupName }.distinct()
    }

    val displayedStreams = remember(allStreams, selectedGroupFilter) {
        if (selectedGroupFilter == null) allStreams
        else allStreams.filter { it.groupName == selectedGroupFilter }
    }

    val groupedStreams = remember(displayedStreams) {
        displayedStreams.groupBy { it.groupName }
    }

    val seasonEpisodeBadge = remember(episode) {
        when {
            episode.season != null && episode.number != null -> "M${episode.season} T${episode.number}"
            episode.number != null -> "T${episode.number}"
            else -> episode.name
        }
    }

    val backdropUrl = movieDetail.backdropUrl ?: movieDetail.posterUrl

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Ảnh Backdrop mờ ở góc trên
        if (!backdropUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            ) {
                AsyncImage(
                    model = backdropUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.4f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                )
            }
        }

        // 2. Nội dung chính
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header: Nút Back
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier.size(40.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = Color.White
                        )
                    }
                }
            }

            // Info Section: Tên phim nổi bật đưa lên dòng đầu, bên dưới là thông tin Mùa/Tập & Tiêu đề tập
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
            ) {
                Text(
                    text = movieDetail.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = seasonEpisodeBadge,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (episode.name.isNotBlank() && !episode.name.equals(seasonEpisodeBadge, ignoreCase = true)) {
                        Text(
                            text = episode.name,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Hàng lọc Chips (Filter row)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Nút reload quét lại
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .padding(start = 16.dp, end = 8.dp)
                        .size(36.dp)
                        .tvFocusableItem(cornerRadius = 18.dp, focusedScale = 1.08f)
                        .clickable(enabled = !isLoading) { refreshKey++ }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.streams_reload),
                            tint = if (isLoading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(rotationAngle)
                        )
                    }
                }

                LazyRow(
                    contentPadding = PaddingValues(end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Chip "Tất cả"
                    item {
                        FilterChip(
                            selected = selectedGroupFilter == null,
                            onClick = {
                                selectedGroupFilter = null
                                ActivePlayerSession.lastSelectedStreamGroup = null
                            },
                            modifier = Modifier.tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.05f),
                            label = {
                                Text(
                                    stringResource(R.string.streams_all_filter),
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedGroupFilter == null) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }

                    // Chips theo từng Provider / Addon
                    items(availableGroups) { group ->
                        val isSelected = selectedGroupFilter == group
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val newFilter = if (isSelected) null else group
                                selectedGroupFilter = newFilter
                                ActivePlayerSession.lastSelectedStreamGroup = newFilter
                            },
                            modifier = Modifier.tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.05f),
                            label = {
                                Text(
                                    text = group,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            // Thanh Loading tiệm tiến
            AnimatedVisibility(visible = isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Danh sách các Card Luồng Phát (Stream Cards)
            if (allStreams.isEmpty() && !isLoading) {
                // Empty State
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.streams_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(onClick = { refreshKey++ }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.common_retry))
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    groupedStreams.forEach { (groupName, streamsInGroup) ->
                        // Header nhóm (Provider header)
                        item(key = "header_$groupName") {
                            Text(
                                text = groupName,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp, start = 4.dp)
                            )
                        }

                        // Danh sách Card luồng trong nhóm
                        items(
                            items = streamsInGroup,
                            key = { it.id }
                        ) { streamItem ->
                            StreamCardItem(
                                item = streamItem,
                                compiledFilters = compiledFilters,
                                badgePlacement = badgePlacement,
                                showSizeBadges = showSizeBadges,
                                showAddonIcon = showAddonIcon,
                                onClick = { onStreamSelected(streamItem) },
                                onLongClick = { onCopyStream(streamItem) },
                                onCopyStream = { onCopyStream(streamItem) }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(60.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StreamCardItem(
    item: UnifiedStreamItem,
    compiledFilters: List<CompiledStreamBadgeFilter>,
    badgePlacement: StreamBadgePlacement,
    showSizeBadges: Boolean,
    showAddonIcon: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCopyStream: () -> Unit
) {
    val matchedBadges = remember(item, compiledFilters) {
        StreamBadgeMatcher.matchBadges(item, compiledFilters)
    }
    val fileSize = remember(item, showSizeBadges) {
        if (showSizeBadges) item.extractFileSize() else null
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .tvFocusableItem(cornerRadius = 12.dp, focusedScale = 1.02f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
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
                // Nếu vị trí nhãn ở Phía trên: hiển thị hàng badges trước
                if (badgePlacement == StreamBadgePlacement.TOP) {
                    StreamBadgeRow(
                        badges = matchedBadges,
                        fileSize = fileSize,
                        fallbackQuality = item.qualityBadge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Dòng 1: Tên Provider
                Text(
                    text = item.providerName,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Dòng 2: Tên Server / Audio (Vietsub #1, Thuyết Minh...)
                Text(
                    text = item.serverName,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Dòng 3: Tiêu đề chi tiết tập phim
                Text(
                    text = item.title,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Dòng 4: Thông tin kết nối kỹ thuật + Badges (nếu ở phía dưới)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.techInfo,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Nếu vị trí nhãn ở phía dưới
                    if (badgePlacement == StreamBadgePlacement.BOTTOM) {
                        StreamBadgeRow(
                            badges = matchedBadges,
                            fileSize = fileSize,
                            fallbackQuality = item.qualityBadge
                        )
                    }
                }
            }

            // Bên phải: Tên kho nguồn nhỏ hiển thị mờ + Nút Sao chép link luồng phát
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(start = 10.dp)
            ) {
                if (showAddonIcon) {
                    Text(
                        text = item.groupName,
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                IconButton(
                    onClick = onCopyStream,
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
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

@Composable
private fun StreamBadgeRow(
    badges: List<StreamBadge>,
    fileSize: String?,
    fallbackQuality: String?,
    modifier: Modifier = Modifier
) {
    if (badges.isEmpty() && fileSize == null && fallbackQuality == null) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        badges.forEach { badge ->
            StreamBadgeChip(badge = badge)
        }
        if (fileSize != null) {
            StreamFileSizeBadge(fileSize = fileSize)
        }
        if (badges.isEmpty() && fallbackQuality != null) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            ) {
                Text(
                    text = fallbackQuality,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

