package com.jkhx.player.ui.screens.reader

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.jkhx.player.ActivePlayerSession
import com.jkhx.player.JkhxApplication
import com.jkhx.player.R
import com.jkhx.player.core.database.AppDatabase
import com.jkhx.player.core.database.entity.HistoryEntity
import com.jkhx.player.core.database.entity.PlaybackProgressEntity
import com.jkhx.player.core.model.EpisodeData
import com.jkhx.player.core.model.EpisodeStream
import com.jkhx.player.ui.theme.AppTheme
import com.jkhx.player.ui.theme.ShapeTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    providerId: String,
    episode: EpisodeData,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val pluginManager = JkhxApplication.instance.pluginManager
    val coroutineScope = rememberCoroutineScope()

    val database = remember { AppDatabase.getInstance(context) }
    val historyDao = remember { database.historyDao() }
    val playbackProgressDao = remember { database.playbackProgressDao() }

    val sessionEpisodes = ActivePlayerSession.episodes
    val movieSlug = ActivePlayerSession.movieSlug.ifBlank { episode.slug }
    val movieName = ActivePlayerSession.movieName.ifBlank { episode.name }
    val movieThumb = ActivePlayerSession.movieThumb

    var currentEpisodeIndex by remember {
        mutableStateOf(
            if (sessionEpisodes.isNotEmpty()) {
                val foundIdx = sessionEpisodes.indexOfFirst { it.id == episode.id }
                if (foundIdx >= 0) foundIdx else ActivePlayerSession.currentEpisodeIndex
            } else 0
        )
    }

    var currentEpisode by remember(currentEpisodeIndex) {
        mutableStateOf(
            sessionEpisodes.getOrNull(currentEpisodeIndex) ?: episode
        )
    }

    var resolvedStream by remember { mutableStateOf<EpisodeStream?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showControls by remember { mutableStateOf(false) }
    var showChapterSheet by remember { mutableStateOf(false) }
    var chapterSearchQuery by remember { mutableStateOf("") }
    var isSheetSortDescending by remember { mutableStateOf(false) }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomOffsetX by remember { mutableFloatStateOf(0f) }
    var zoomOffsetY by remember { mutableFloatStateOf(0f) }
    val isZoomed = zoomScale > 1.05f

    // Novel Reader settings state: Font size (sp) & Reading theme (Dark, Sepia, Light)
    var novelFontSize by remember { mutableIntStateOf(18) }
    var novelThemeIndex by remember { mutableIntStateOf(0) } // 0: Dark AMOLED, 1: Sepia, 2: Light

    val listState = rememberLazyListState()

    BackHandler {
        if (isZoomed) {
            zoomScale = 1f
            zoomOffsetX = 0f
            zoomOffsetY = 0f
        } else {
            onBack()
        }
    }

    // Load stream, images or novel content whenever currentEpisode changes
    LaunchedEffect(providerId, currentEpisode.id) {
        isLoading = true
        errorMessage = null
        resolvedStream = null
        zoomScale = 1f
        zoomOffsetX = 0f
        zoomOffsetY = 0f

        try {
            val plugin = pluginManager.getPlugin(providerId)
                ?: throw Exception(context.getString(R.string.player_plugin_unavailable, providerId))

            val stream = plugin.getStream(currentEpisode)
            resolvedStream = stream
            isLoading = false

            // Save history with existing progress if available
            withContext(Dispatchers.IO) {
                val existingProgress = playbackProgressDao.getProgress("$providerId:$movieSlug:${currentEpisode.id}")
                val initialPos = existingProgress?.positionMs ?: 0L
                val isNovel = !stream.content.isNullOrBlank()
                val totalDuration = if (isNovel) {
                    val tempParas = stream.content!!.split("\n\n", "\r\n\r\n", "\n").map { it.trim() }.filter { it.isNotBlank() }
                    maxOf(1L, (tempParas.size + 1).toLong())
                } else {
                    stream.images.size.toLong()
                }
                historyDao.upsert(
                    HistoryEntity(
                        id = "$providerId:$movieSlug",
                        providerId = providerId,
                        movieSlug = movieSlug,
                        movieName = movieName,
                        thumbUrl = movieThumb,
                        lastEpisodeId = currentEpisode.id,
                        lastEpisodeName = currentEpisode.name,
                        positionMs = initialPos,
                        durationMs = totalDuration,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            errorMessage = e.message ?: context.getString(R.string.reader_error)
            isLoading = false
        }
    }

    val isNovelMode = !resolvedStream?.content.isNullOrBlank()
    val novelText = resolvedStream?.content ?: ""
    val paragraphs = remember(novelText) {
        if (novelText.isBlank()) emptyList()
        else novelText.split("\n\n", "\r\n\r\n", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    // Khôi phục vị trí trang / đoạn văn đã dừng hoặc cuộn về đầu nếu chưa đọc
    LaunchedEffect(currentEpisode.id, resolvedStream != null, paragraphs.size) {
        if (resolvedStream != null) {
            try {
                val savedProgress = withContext(Dispatchers.IO) {
                    playbackProgressDao.getProgress("$providerId:$movieSlug:${currentEpisode.id}")
                }
                val maxLimit = if (isNovelMode) {
                    paragraphs.size + 1
                } else {
                    maxOf(0, (resolvedStream?.images?.size ?: 1) - 1)
                }
                val targetIndex = if (savedProgress != null && savedProgress.positionMs > 0L) {
                    savedProgress.positionMs.toInt().coerceIn(0, maxOf(0, maxLimit))
                } else 0

                if (targetIndex > 0) {
                    listState.scrollToItem(targetIndex)
                    withContext(Dispatchers.Main) {
                        if (isNovelMode) {
                            val percent = if (paragraphs.isNotEmpty()) {
                                ((targetIndex.toFloat() / maxOf(1, paragraphs.size).toFloat()) * 100).toInt().coerceIn(1, 100)
                            } else 0
                            Toast.makeText(
                                context,
                                context.getString(R.string.reader_resume_novel_toast, percent),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.reader_resume_toast, targetIndex + 1),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    listState.scrollToItem(0)
                }
            } catch (_: Exception) {}
        }
    }

    // Track reading progress & current page
    val currentImages = resolvedStream?.images ?: emptyList()

    val currentPage by remember(currentImages.size) {
        derivedStateOf {
            val total = currentImages.size
            if (total == 0) return@derivedStateOf 0
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                (listState.firstVisibleItemIndex + 1).coerceIn(1, total)
            } else {
                val viewportStart = layoutInfo.viewportStartOffset
                val viewportEnd = layoutInfo.viewportEndOffset
                // Tìm item có phần diện tích hiển thị lớn nhất trong viewport
                val bestItem = visibleItems.maxByOrNull { item ->
                    val visibleTop = maxOf(item.offset, viewportStart)
                    val visibleBottom = minOf(item.offset + item.size, viewportEnd)
                    (visibleBottom - visibleTop).coerceAtLeast(0)
                } ?: visibleItems.first()
                (bestItem.index + 1).coerceIn(1, total)
            }
        }
    }

    // Track reading progress for Novel (LazyColumn item index)
    val currentNovelItemIndex by remember(paragraphs.size) {
        derivedStateOf {
            if (paragraphs.isEmpty()) 0
            else listState.firstVisibleItemIndex.coerceIn(0, paragraphs.size + 1)
        }
    }

    var isScrollingActive by remember { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            isScrollingActive = true
        } else {
            kotlinx.coroutines.delay(1800)
            isScrollingActive = false
        }
    }

    // Lưu tiến độ đọc truyện tranh (Comic)
    LaunchedEffect(currentPage, currentImages.size) {
        if (currentImages.isNotEmpty() && currentPage > 0) {
            val pageIdx = (currentPage - 1).toLong()
            withContext(Dispatchers.IO) {
                playbackProgressDao.saveProgress(
                    PlaybackProgressEntity(
                        id = "$providerId:$movieSlug:${currentEpisode.id}",
                        providerId = providerId,
                        movieSlug = movieSlug,
                        episodeId = currentEpisode.id,
                        positionMs = pageIdx,
                        durationMs = currentImages.size.toLong(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
                historyDao.upsert(
                    HistoryEntity(
                        id = "$providerId:$movieSlug",
                        providerId = providerId,
                        movieSlug = movieSlug,
                        movieName = movieName,
                        thumbUrl = movieThumb,
                        lastEpisodeId = currentEpisode.id,
                        lastEpisodeName = currentEpisode.name,
                        positionMs = pageIdx,
                        durationMs = currentImages.size.toLong(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    // Lưu tiến độ đọc truyện chữ (Novel) với debounce để tránh spam DB khi vuốt lướt nhanh
    LaunchedEffect(currentNovelItemIndex, paragraphs.size) {
        if (isNovelMode && paragraphs.isNotEmpty() && currentNovelItemIndex >= 0) {
            kotlinx.coroutines.delay(400)
            val pos = currentNovelItemIndex.toLong()
            val total = (paragraphs.size + 1).toLong()
            withContext(Dispatchers.IO) {
                playbackProgressDao.saveProgress(
                    PlaybackProgressEntity(
                        id = "$providerId:$movieSlug:${currentEpisode.id}",
                        providerId = providerId,
                        movieSlug = movieSlug,
                        episodeId = currentEpisode.id,
                        positionMs = pos,
                        durationMs = total,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                historyDao.upsert(
                    HistoryEntity(
                        id = "$providerId:$movieSlug",
                        providerId = providerId,
                        movieSlug = movieSlug,
                        movieName = movieName,
                        thumbUrl = movieThumb,
                        lastEpisodeId = currentEpisode.id,
                        lastEpisodeName = currentEpisode.name,
                        positionMs = pos,
                        durationMs = total,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val boxWidthPx = constraints.maxWidth.toFloat()
        val boxHeightPx = constraints.maxHeight.toFloat()
        val maxOffsetX = ((boxWidthPx * (zoomScale - 1f)) / 2f).coerceAtLeast(0f)
        val maxOffsetY = ((boxHeightPx * (zoomScale - 1f)) / 2f).coerceAtLeast(0f)

        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            val newScale = (zoomScale * zoomChange).coerceIn(1f, 4f)
            zoomScale = newScale
            if (newScale > 1.05f) {
                val currMaxX = ((boxWidthPx * (newScale - 1f)) / 2f).coerceAtLeast(0f)
                val currMaxY = ((boxHeightPx * (newScale - 1f)) / 2f).coerceAtLeast(0f)
                zoomOffsetX = (zoomOffsetX + panChange.x * newScale).coerceIn(-currMaxX, currMaxX)
                zoomOffsetY = (zoomOffsetY + panChange.y * newScale).coerceIn(-currMaxY, currMaxY)
            } else {
                zoomScale = 1f
                zoomOffsetX = 0f
                zoomOffsetY = 0f
            }
        }
        // Image List (Webtoon Vertical Continuous Scroll)
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(AppTheme.dimens.space12))
                    Text(
                        text = stringResource(R.string.reader_loading),
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        } else if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppTheme.dimens.space24),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(AppTheme.dimens.space16))
                    Button(
                        onClick = {
                            // Retry
                            coroutineScope.launch {
                                isLoading = true
                                errorMessage = null
                                try {
                                    val plugin = pluginManager.getPlugin(providerId)
                                        ?: throw Exception(context.getString(R.string.player_plugin_unavailable, providerId))
                                    resolvedStream = plugin.getStream(currentEpisode)
                                } catch (e: Exception) {
                                    errorMessage = e.message ?: context.getString(R.string.reader_error)
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        shape = ShapeTokens.Button,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(stringResource(R.string.reader_retry))
                    }
                }
            }
        } else if (currentImages.isEmpty() && resolvedStream?.content.isNullOrBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.reader_no_content),
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(AppTheme.dimens.space24)
                )
            }
        } else if (isNovelMode) {
            // === CHẾ ĐỘ ĐỌC TRUYỆN CHỮ (NOVEL READER) ===

            // Theme colors for Novel Reader
            val (novelBgColor, novelTextColor) = when (novelThemeIndex) {
                1 -> Color(0xFFFBF0D9) to Color(0xFF3C2C1C) // Sepia Paper
                2 -> Color(0xFFFFFFFF) to Color(0xFF1A1A1A) // Light White
                else -> Color(0xFF121212) to Color(0xFFE2E2E2) // Dark AMOLED
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(novelBgColor)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                showControls = !showControls
                            }
                        )
                    }
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = AppTheme.dimens.space20,
                        end = AppTheme.dimens.space20,
                        top = if (showControls) 80.dp else 40.dp,
                        bottom = if (showControls) 120.dp else 60.dp
                    )
                ) {
                    item {
                        Text(
                            text = currentEpisode.name,
                            fontSize = (novelFontSize + 4).sp,
                            fontWeight = FontWeight.Bold,
                            color = novelTextColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = AppTheme.dimens.space24),
                            textAlign = TextAlign.Center
                        )
                    }

                    itemsIndexed(paragraphs) { pIdx, paragraph ->
                        Text(
                            text = paragraph,
                            fontSize = novelFontSize.sp,
                            lineHeight = (novelFontSize * 1.6f).sp,
                            color = novelTextColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = AppTheme.dimens.space14)
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(AppTheme.dimens.space32))
                        HorizontalDivider(
                            color = novelTextColor.copy(alpha = 0.15f),
                            modifier = Modifier.padding(vertical = AppTheme.dimens.space16)
                        )
                        Text(
                            text = "— Hết ${currentEpisode.name} —",
                            fontSize = 13.sp,
                            color = novelTextColor.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(AppTheme.dimens.space32))
                    }
                }
            }
        } else {
            val headers = resolvedStream?.headers ?: emptyMap()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoomScale
                        scaleY = zoomScale
                        translationX = zoomOffsetX
                        translationY = zoomOffsetY
                    }
                    .transformable(state = transformState)
                    .pointerInput(isZoomed) {
                        if (isZoomed) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                zoomOffsetX = (zoomOffsetX + dragAmount.x).coerceIn(-maxOffsetX, maxOffsetX)
                                zoomOffsetY = (zoomOffsetY + dragAmount.y).coerceIn(-maxOffsetY, maxOffsetY)
                            }
                        } else {
                            detectTapGestures(
                                onDoubleTap = {
                                    zoomScale = 2.5f
                                    zoomOffsetX = 0f
                                    zoomOffsetY = 0f
                                },
                                onTap = {
                                    showControls = !showControls
                                }
                            )
                        }
                    }
            ) {
                LazyColumn(
                    state = listState,
                    userScrollEnabled = !isZoomed,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    itemsIndexed(currentImages) { index, imageUrl ->
                        val imageRequest = remember(imageUrl, headers) {
                            ImageRequest.Builder(context)
                                .data(imageUrl)
                                .apply {
                                    headers.forEach { (key, value) ->
                                        addHeader(key, value)
                                    }
                                }
                                .crossfade(true)
                                .build()
                        }

                        var isImageLoading by remember { mutableStateOf(true) }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = imageRequest,
                                contentDescription = "Page ${index + 1}",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(),
                                onState = { state ->
                                    isImageLoading = state is AsyncImagePainter.State.Loading
                                }
                            )

                            if (isImageLoading) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(260.dp)
                                        .background(Color(0xFF151515)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Overlay: Top App Bar
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.9f),
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = AppTheme.dimens.space12, vertical = AppTheme.dimens.space8)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = Color.White
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = AppTheme.dimens.space8)
                            .clickable { showChapterSheet = true }
                    ) {
                        Text(
                            text = movieName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentEpisode.name,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val isNovelMode = !resolvedStream?.content.isNullOrBlank()

                    if (!isNovelMode) {
                        IconButton(
                            onClick = {
                                if (isZoomed) {
                                    zoomScale = 1f
                                    zoomOffsetX = 0f
                                    zoomOffsetY = 0f
                                } else {
                                    zoomScale = 2.5f
                                    zoomOffsetX = 0f
                                    zoomOffsetY = 0f
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isZoomed) Icons.Default.ZoomOut else Icons.Default.ZoomIn,
                                contentDescription = stringResource(R.string.reader_reset_zoom, zoomScale),
                                tint = if (isZoomed) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }
                    }

                    IconButton(onClick = { showChapterSheet = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(R.string.reader_chapters_title),
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Floating Reset Zoom Button: Hiện khi đang zoom để người dùng hoàn tác 1 chạm
        AnimatedVisibility(
            visible = isZoomed,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = if (showControls) 70.dp else AppTheme.dimens.space16)
        ) {
            Surface(
                onClick = {
                    zoomScale = 1f
                    zoomOffsetX = 0f
                    zoomOffsetY = 0f
                },
                shape = ShapeTokens.Chip,
                color = Color.Black.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = AppTheme.dimens.space12, vertical = AppTheme.dimens.space6),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomOut,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(AppTheme.dimens.space6))
                    Text(
                        text = stringResource(R.string.reader_reset_zoom, zoomScale),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }

        // Floating Page Indicator: Tự động nổi lên và cập nhật realtime khi người đọc lướt cuộn trang
        AnimatedVisibility(
            visible = !showControls && isScrollingActive && (currentImages.isNotEmpty() || (isNovelMode && paragraphs.isNotEmpty())) && !isZoomed,
            enter = fadeIn(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(400)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = AppTheme.dimens.space16, bottom = AppTheme.dimens.space16)
        ) {
            Surface(
                shape = ShapeTokens.Chip,
                color = Color.Black.copy(alpha = 0.75f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
            ) {
                val indicatorText = if (isNovelMode) {
                    val percent = if (paragraphs.isNotEmpty()) {
                        ((currentNovelItemIndex.toFloat() / maxOf(1, paragraphs.size).toFloat()) * 100).toInt().coerceIn(0, 100)
                    } else 0
                    "$percent%"
                } else {
                    stringResource(R.string.reader_page_indicator, currentPage, currentImages.size)
                }
                Text(
                    text = indicatorText,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = AppTheme.dimens.space12, vertical = AppTheme.dimens.space6)
                )
            }
        }

        // Overlay: Bottom Controls (Chapter Navigation & Chapter List Selector)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f),
                                Color.Black.copy(alpha = 0.95f)
                            )
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = AppTheme.dimens.space16, vertical = AppTheme.dimens.space10)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Điều khiển dành riêng cho Truyện Chữ (Novel Reader): Tùy chỉnh Font Size (A- / A+) & Đổi Theme Đọc
                    val isNovelMode = !resolvedStream?.content.isNullOrBlank()
                    if (isNovelMode) {
                        Surface(
                            shape = ShapeTokens.Chip,
                            color = Color.Black.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            modifier = Modifier.padding(bottom = AppTheme.dimens.space10)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = AppTheme.dimens.space12, vertical = AppTheme.dimens.space4),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space12)
                            ) {
                                // A- Button
                                Text(
                                    text = "A-",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier
                                        .clickable {
                                            if (novelFontSize > 14) novelFontSize -= 2
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )

                                Text(
                                    text = "${novelFontSize}sp",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )

                                // A+ Button
                                Text(
                                    text = "A+",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier
                                        .clickable {
                                            if (novelFontSize < 32) novelFontSize += 2
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )

                                Spacer(modifier = Modifier.width(AppTheme.dimens.space6))

                                // Theme Dots: Dark, Sepia, Light
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(Color(0xFF1E1E1E), shape = androidx.compose.foundation.shape.CircleShape)
                                        .border(
                                            width = if (novelThemeIndex == 0) 2.dp else 1.dp,
                                            color = if (novelThemeIndex == 0) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        )
                                        .clickable { novelThemeIndex = 0 }
                                )
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(Color(0xFFFBF0D9), shape = androidx.compose.foundation.shape.CircleShape)
                                        .border(
                                            width = if (novelThemeIndex == 1) 2.dp else 1.dp,
                                            color = if (novelThemeIndex == 1) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        )
                                        .clickable { novelThemeIndex = 1 }
                                )
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(Color(0xFFFFFFFF), shape = androidx.compose.foundation.shape.CircleShape)
                                        .border(
                                            width = if (novelThemeIndex == 2) 2.dp else 1.dp,
                                            color = if (novelThemeIndex == 2) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        )
                                        .clickable { novelThemeIndex = 2 }
                                )
                            }
                        }
                    }

                    // Chỉ báo số trang hiện tại khi mở controls (cho comic) hoặc tiến độ % (cho novel)
                    if (currentImages.isNotEmpty()) {
                        Surface(
                            shape = ShapeTokens.Chip,
                            color = Color.Black.copy(alpha = 0.75f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            modifier = Modifier.padding(bottom = AppTheme.dimens.space8)
                        ) {
                            Text(
                                text = stringResource(R.string.reader_page_indicator, currentPage, currentImages.size),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = AppTheme.dimens.space12, vertical = AppTheme.dimens.space4)
                            )
                        }
                    } else if (isNovelMode && paragraphs.isNotEmpty()) {
                        val novelPercent = ((currentNovelItemIndex.toFloat() / maxOf(1, paragraphs.size).toFloat()) * 100).toInt().coerceIn(0, 100)
                        Surface(
                            shape = ShapeTokens.Chip,
                            color = Color.Black.copy(alpha = 0.75f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            modifier = Modifier.padding(bottom = AppTheme.dimens.space8)
                        ) {
                            Text(
                                text = stringResource(R.string.reader_novel_progress, novelPercent),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = AppTheme.dimens.space12, vertical = AppTheme.dimens.space4)
                            )
                        }
                    }

                    // Hàng điều hướng chapter: [Chap trước] - [Chapter X (mở danh sách)] - [Chap sau]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val hasPrevChapter = currentEpisodeIndex > 0
                        val hasNextChapter = sessionEpisodes.isNotEmpty() && currentEpisodeIndex < sessionEpisodes.size - 1

                        // Previous Chapter Button (Lùi về chương trước trong mạch truyện)
                        FilledTonalButton(
                            onClick = {
                                if (hasPrevChapter) {
                                    val nextIdx = currentEpisodeIndex - 1
                                    currentEpisodeIndex = nextIdx
                                    sessionEpisodes.getOrNull(nextIdx)?.let {
                                        currentEpisode = it
                                        ActivePlayerSession.currentEpisodeIndex = nextIdx
                                        ActivePlayerSession.currentEpisode = it
                                    }
                                }
                            },
                            enabled = hasPrevChapter,
                            shape = ShapeTokens.Button,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledContainerColor = Color.White.copy(alpha = 0.1f),
                                disabledContentColor = Color.White.copy(alpha = 0.3f)
                            ),
                            contentPadding = PaddingValues(horizontal = AppTheme.dimens.space12, vertical = AppTheme.dimens.space6)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(AppTheme.dimens.space4))
                            Text(
                                text = stringResource(R.string.reader_prev_chapter),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Chapter Selector Button (Mở danh sách chapter)
                        FilledTonalButton(
                            onClick = { showChapterSheet = true },
                            shape = ShapeTokens.Button,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = AppTheme.dimens.space12, vertical = AppTheme.dimens.space6),
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(horizontal = AppTheme.dimens.space8)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.List,
                                contentDescription = stringResource(R.string.reader_chapters_title),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(AppTheme.dimens.space6))
                            Text(
                                text = currentEpisode.name,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Next Chapter Button (Tiến tới chương sau trong mạch truyện)
                        Button(
                            onClick = {
                                if (hasNextChapter) {
                                    val nextIdx = currentEpisodeIndex + 1
                                    currentEpisodeIndex = nextIdx
                                    sessionEpisodes.getOrNull(nextIdx)?.let {
                                        currentEpisode = it
                                        ActivePlayerSession.currentEpisodeIndex = nextIdx
                                        ActivePlayerSession.currentEpisode = it
                                    }
                                }
                            },
                            enabled = hasNextChapter,
                            shape = ShapeTokens.Button,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = Color.White.copy(alpha = 0.1f),
                                disabledContentColor = Color.White.copy(alpha = 0.3f)
                            ),
                            contentPadding = PaddingValues(horizontal = AppTheme.dimens.space12, vertical = AppTheme.dimens.space6)
                        ) {
                            Text(
                                text = stringResource(R.string.reader_next_chapter),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(AppTheme.dimens.space4))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Chapter Selection Modal Bottom Sheet
        if (showChapterSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val chapterListState = rememberLazyListState()

            val filteredEpisodes = remember(sessionEpisodes, chapterSearchQuery, isSheetSortDescending) {
                val indexed = sessionEpisodes.mapIndexed { idx, ep -> Pair(idx, ep) }
                val filtered = if (chapterSearchQuery.isBlank()) {
                    indexed
                } else {
                    val q = chapterSearchQuery.trim().lowercase()
                    indexed.filter { (_, ep) ->
                        ep.name.lowercase().contains(q) || ep.slug.lowercase().contains(q)
                    }
                }
                if (isSheetSortDescending) filtered.reversed() else filtered
            }

            LaunchedEffect(showChapterSheet) {
                if (chapterSearchQuery.isBlank()) {
                    val targetIdx = filteredEpisodes.indexOfFirst { it.first == currentEpisodeIndex }
                    if (targetIdx >= 0) {
                        chapterListState.scrollToItem(maxOf(0, targetIdx - 2))
                    }
                }
            }

            ModalBottomSheet(
                onDismissRequest = {
                    showChapterSheet = false
                    chapterSearchQuery = ""
                },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppTheme.dimens.space16)
                        .navigationBarsPadding()
                ) {
                    // Header của Sheet
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(AppTheme.dimens.space8))
                            Text(
                                text = stringResource(R.string.reader_chapters_count, sessionEpisodes.size),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { isSheetSortDescending = !isSheetSortDescending },
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
                                    text = stringResource(if (isSheetSortDescending) R.string.reader_sort_desc else R.string.reader_sort_asc),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            IconButton(
                                onClick = {
                                    showChapterSheet = false
                                    chapterSearchQuery = ""
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.common_close),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(AppTheme.dimens.space12))

                    // Ô tìm kiếm / nhảy chương nhanh
                    OutlinedTextField(
                        value = chapterSearchQuery,
                        onValueChange = { chapterSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.reader_search_chapter_placeholder),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (chapterSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { chapterSearchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = stringResource(R.string.search_clear),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = ShapeTokens.Card,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    )

                    Spacer(modifier = Modifier.height(AppTheme.dimens.space12))

                    // Danh sách chương
                    if (filteredEpisodes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.reader_no_matching_chapters),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            state = chapterListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .heightIn(max = 420.dp),
                            verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space6),
                            contentPadding = PaddingValues(bottom = AppTheme.dimens.space16)
                        ) {
                            items(filteredEpisodes, key = { it.second.id }) { (actualIndex, ep) ->
                                val isCurrent = actualIndex == currentEpisodeIndex
                                Surface(
                                    onClick = {
                                        currentEpisodeIndex = actualIndex
                                        currentEpisode = ep
                                        ActivePlayerSession.currentEpisodeIndex = actualIndex
                                        ActivePlayerSession.currentEpisode = ep
                                        showChapterSheet = false
                                        chapterSearchQuery = ""
                                    },
                                    shape = ShapeTokens.Card,
                                    color = if (isCurrent) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = AppTheme.dimens.space14, vertical = AppTheme.dimens.space10),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = if (isCurrent) Icons.Default.Check else Icons.AutoMirrored.Filled.MenuBook,
                                                contentDescription = null,
                                                tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(AppTheme.dimens.space10))
                                            Text(
                                                text = ep.name,
                                                fontSize = 13.5.sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        if (isCurrent) {
                                            Surface(
                                                shape = ShapeTokens.Chip,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(start = AppTheme.dimens.space8)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.reader_current_reading),
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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
    }
}

