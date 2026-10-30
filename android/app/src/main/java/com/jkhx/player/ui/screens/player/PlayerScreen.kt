package com.jkhx.player.ui.screens.player

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.blur
import com.jkhx.player.plugin.manager.stremio.StremioPluginWrapper
import com.jkhx.player.core.model.stremio.StremioStream
import com.jkhx.player.ActiveEpisodeHolder
import androidx.compose.runtime.CompositionLocalProvider
import com.jkhx.player.ui.components.glass.LocalLiquidGlassBackdrop
import com.jkhx.player.ui.components.glass.playerLiquidGlass
import com.jkhx.player.ui.components.glass.rememberVideoFrameBackdrop
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import kotlinx.coroutines.isActive
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import android.util.TypedValue
import androidx.compose.foundation.border
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.compose.ui.res.stringResource
import com.jkhx.player.R
import com.jkhx.player.ActivePlayerSession
import com.jkhx.player.JkhxApplication
import com.jkhx.player.core.model.MovieDetail
import com.jkhx.player.ui.screens.stream.StreamAggregator
import com.jkhx.player.ui.screens.stream.UnifiedStreamItem
import com.jkhx.player.LocalPipMode
import com.jkhx.player.ui.theme.AppTheme
import com.jkhx.player.ui.theme.ShapeTokens
import com.jkhx.player.ui.theme.tvFocusableItem
import com.jkhx.player.core.database.AppDatabase
import com.jkhx.player.core.database.entity.HistoryEntity
import com.jkhx.player.core.database.entity.PlaybackProgressEntity
import com.jkhx.player.core.model.EpisodeData
import com.jkhx.player.core.model.EpisodeStream
import com.jkhx.player.core.player.AudioTrackOption
import com.jkhx.player.core.player.ExoPlayerManager
import com.jkhx.player.core.player.GenericHtml5EmbedResolver
import com.jkhx.player.core.player.SubtitleTrackOption
import com.jkhx.player.core.player.VideoTrackOption
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Tv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.jkhx.player.ui.screens.player.panels.PlayerResumeDialog
import com.jkhx.player.ui.screens.player.panels.PlayerSettingsPanel
import com.jkhx.player.ui.screens.player.panels.PlayerEpisodesPanel
import com.jkhx.player.ui.screens.player.panels.PlayerAudioPanel
import com.jkhx.player.ui.screens.player.panels.PlayerSourcePanel
import com.jkhx.player.ui.screens.player.panels.PlayerSubtitlePanel
import com.jkhx.player.ui.screens.player.overlays.PlayerHudOverlays
import com.jkhx.player.ui.screens.player.overlays.PlayerWebViewPlayer
import com.jkhx.player.ui.screens.player.overlays.PlayerWebViewTopBar
import com.jkhx.player.ui.screens.player.overlays.PlayerNativeView
import com.jkhx.player.ui.screens.player.overlays.PlayerErrorOverlay
import com.jkhx.player.ui.screens.player.controls.PlayerControlsOverlay
import com.jkhx.player.ui.screens.player.components.RepeatingIconButton
import com.jkhx.player.ui.screens.player.components.SettingsOptionCard

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlayerScreen(
    providerId: String,
    episode: EpisodeData,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val pluginManager = JkhxApplication.instance.pluginManager
    val exoPlayerManager = remember { ExoPlayerManager(context) }
    val embedResolver = remember { GenericHtml5EmbedResolver() }
    val isPipMode = LocalPipMode.current
    val coroutineScope = rememberCoroutineScope()

    // Room Database cho Watch History & Resume Progress
    val database = remember { AppDatabase.getInstance(context) }
    val playbackProgressDao = remember { database.playbackProgressDao() }
    val historyDao = remember { database.historyDao() }

    // Thông tin phim và danh sách tập từ session
    val sessionEpisodes = ActivePlayerSession.episodes
    val movieSlug = ActivePlayerSession.movieSlug.ifBlank { episode.slug }
    val defaultMovieTitle = stringResource(R.string.player_playing_default_title)
    var movieName by remember { mutableStateOf(ActivePlayerSession.movieName.ifBlank { defaultMovieTitle }) }
    var movieThumb by remember { mutableStateOf(ActivePlayerSession.movieThumb) }
    var movieTmdbId by remember { mutableStateOf(ActivePlayerSession.tmdbId) }
    var movieOriginName by remember { mutableStateOf(ActivePlayerSession.originName) }
    var movieYear by remember { mutableStateOf(ActivePlayerSession.movieYear) }
    var availableServers by remember { mutableStateOf(ActivePlayerSession.availableServers) }
    var rawPluginName by remember { mutableStateOf(ActivePlayerSession.rawPluginName) }
    var rawPluginOriginName by remember { mutableStateOf(ActivePlayerSession.rawPluginOriginName) }

    // Quản lý Nguồn phát & Máy chủ đang phát
    var activeProviderId by remember { mutableStateOf(ActivePlayerSession.providerId.ifBlank { providerId }) }
    var activeMovieSlug by remember { mutableStateOf(movieSlug) }
    var activeEpisodes by remember { mutableStateOf(sessionEpisodes) }
    var activeServerName by remember { mutableStateOf(ActivePlayerSession.currentServerName) }
    var pendingResumePositionMs by remember { mutableStateOf(0L) }

    // Dialog & Trạng thái chọn Nguồn & Danh sách tập
    var showSourceSelectorDrawer by remember { mutableStateOf(false) }
    var showEpisodesDrawer by remember { mutableStateOf(false) }
    var isScanningSources by remember { mutableStateOf(false) }
    var scannedSources by remember { mutableStateOf<List<AvailablePluginSource>>(emptyList()) }
    var drawerStreams by remember { mutableStateOf(ActivePlayerSession.aggregatedStreams) }
    var activeUnifiedStream by remember { mutableStateOf(ActivePlayerSession.activeUnifiedStream) }
    var selectedSourceFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var refreshSourcesKey by remember { mutableIntStateOf(0) }
    val stremioStreamMap = remember { mutableMapOf<String, EpisodeStream>() }

    val onCopyStreamUrl: (String) -> Unit = remember(context) {
        { rawUrl ->
            val cleanUrl = rawUrl.substringBefore("#").substringBefore("|").ifBlank { rawUrl }
            if (cleanUrl.isNotBlank()) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Stream Link", cleanUrl))
                Toast.makeText(context, context.getString(R.string.stream_copied_toast), Toast.LENGTH_SHORT).show()
            }
        }
    }

    var currentEpisodeIndex by remember {
        mutableStateOf(
            if (activeEpisodes.isNotEmpty()) {
                val foundIdx = activeEpisodes.indexOfFirst { it.id == episode.id }
                if (foundIdx >= 0) foundIdx else ActivePlayerSession.currentEpisodeIndex
            } else 0
        )
    }
    var currentEpisode by remember(currentEpisodeIndex, activeEpisodes) {
        mutableStateOf(
            activeEpisodes.getOrNull(currentEpisodeIndex) ?: episode
        )
    }

    // Trích xuất link embed gốc từ episode.data (nếu có) để làm fallback
    val fallbackEmbedFromData = remember(currentEpisode) {
        try {
            val dataObj = currentEpisode.data as? JsonObject
            (dataObj?.get("embed") as? JsonPrimitive)?.content
        } catch (e: Exception) {
            null
        }
    }

    var isCustomStreamActive by remember { mutableStateOf(ActiveEpisodeHolder.customStream != null) }
    var rawEmbedUrl by remember { mutableStateOf<String?>(if (ActiveEpisodeHolder.customStream != null) null else fallbackEmbedFromData) }
    var resolvedStream by remember { mutableStateOf<EpisodeStream?>(null) }

    fun getEffectiveEmbedFallback(): String? {
        val activeStreamUrl = resolvedStream?.url?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        return rawEmbedUrl ?: fallbackEmbedFromData ?: activeStreamUrl
    }
    var isLoading by remember { mutableStateOf(true) }
    var isPlayerBuffering by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Chế độ phát WebView dự phòng
    var isWebViewMode by remember { mutableStateOf(false) }
    var webViewUrl by remember { mutableStateOf<String?>(null) }
    var webViewHeaders by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    var webCustomView by remember { mutableStateOf<View?>(null) }
    var webCustomViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    // Trạng thái Player UI
    var reloadStreamTrigger by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var hasPlaybackStarted by remember(currentEpisode.id, activeServerName, reloadStreamTrigger) { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    val isPlaybackStartedEffective = hasPlaybackStarted || isPlaying || currentPosition > 0L
    var totalDuration by remember { mutableStateOf(0L) }
    val fallbackDurationMs = remember(ActivePlayerSession.movieDetail?.duration) {
        parseDurationStringToMs(ActivePlayerSession.movieDetail?.duration)
    }
    val effectiveTotalDuration = if (totalDuration > 0L) totalDuration else fallbackDurationMs
    var bufferedPosition by remember { mutableStateOf(0L) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var isDraggingGesture by remember { mutableStateOf(false) }
    var sliderDragRatio by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }
    var exoPlayerRetryCount by remember { mutableIntStateOf(0) }

    // Controls Overlay
    var showControls by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }
    val videoBackdrop = rememberVideoFrameBackdrop(enabled = showControls && !isPipMode && !isWebViewMode)

    // Aspect ratio / Resize mode (Fit -> Fill -> Zoom)
    val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to stringResource(R.string.player_fit_mode_fit),
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to stringResource(R.string.player_fit_mode_zoom),
        AspectRatioFrameLayout.RESIZE_MODE_FILL to stringResource(R.string.player_fit_mode_stretch)
    )
    var resizeModeIndex by remember { mutableStateOf(0) }
    var activePlayerView by remember { mutableStateOf<PlayerView?>(null) }

    // Cài đặt phụ đề Nuvio từ SettingsManager
    val settingsManager = remember { JkhxApplication.instance.settingsManager }
    val subtitleTextSize by settingsManager.subtitleTextSize.collectAsState()
    val subtitleIsBold by settingsManager.subtitleIsBold.collectAsState()
    val subtitleTextColor by settingsManager.subtitleTextColor.collectAsState()
    val subtitleTextOpacity by settingsManager.subtitleTextOpacity.collectAsState()
    val subtitleIsOutline by settingsManager.subtitleIsOutline.collectAsState()
    val subtitleOutlineColor by settingsManager.subtitleOutlineColor.collectAsState()
    val subtitleBottomMargin by settingsManager.subtitleBottomMargin.collectAsState()

    var selectedLangGroup by remember { mutableStateOf("vi") }
    val configuredSubLanguages by settingsManager.subtitleSearchLanguages.collectAsState()
    val onlineSubsMap = remember { mutableStateMapOf<String, List<com.jkhx.player.core.player.subtitle.model.OnlineSubtitleItem>>() }
    val stremioAddonSubsMap = remember { mutableStateMapOf<String, List<com.jkhx.player.core.player.subtitle.model.OnlineSubtitleItem>>() }
    var isStremioSubsLoading by remember { mutableStateOf(false) }
    var hasLoadedStremioSubsForEp by remember(currentEpisode.id) { mutableStateOf(false) }
    val loadingSubsMap = remember { mutableStateMapOf<String, Boolean>() }
    var downloadingSubItemId by remember { mutableStateOf<String?>(null) }
    var customSubSearchQuery by remember {
        mutableStateOf(
            ActivePlayerSession.originName?.ifBlank { null }
                ?: ActivePlayerSession.rawPluginOriginName?.ifBlank { null }
                ?: ActivePlayerSession.movieName
        )
    }
    var isEditingSubSearchQuery by remember { mutableStateOf(false) }
    var tempSubSearchQuery by remember { mutableStateOf(customSubSearchQuery ?: "") }
    var activeOnlineSubId by remember { mutableStateOf<String?>(null) }
    var hasAutoAppliedInitialSub by remember { mutableStateOf(false) }

    // Tốc độ phát (Speed)
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    var currentSpeed by remember { mutableStateOf(1.0f) }
    var isLongPressing2x by remember { mutableStateOf(false) }

    // Dialog chọn cài đặt: Tốc độ, Chất lượng HLS
    var showSettingsDialog by remember { mutableStateOf(false) }
    var videoTracks by remember { mutableStateOf<List<VideoTrackOption>>(emptyList()) }
    var audioTracks by remember { mutableStateOf<List<AudioTrackOption>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<SubtitleTrackOption>>(emptyList()) }
    var selectedVideoTrackId by remember { mutableStateOf<String?>("auto") }
    var selectedAudioTrackId by remember { mutableStateOf<String?>(null) }
    var selectedSubtitleTrackId by remember { mutableStateOf<String?>("off") }
    var showAudioSheet by remember { mutableStateOf(false) }

    // Quản lý phụ đề & Dịch AI riêng biệt
    val subtitleManager = remember { JkhxApplication.instance.subtitleManager }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var currentSubCues by remember { mutableStateOf<List<com.jkhx.player.core.player.subtitle.model.SubtitleCue>>(emptyList()) }
    var currentSubOffsetMs by remember { mutableStateOf(0L) }
    var isTranslatingWithGemini by remember { mutableStateOf(false) }
    var geminiTranslateProgress by remember { mutableStateOf(0f) }
    var isAiSyncing by remember { mutableStateOf(false) }
    val subtitleCuesMap = remember { mutableStateMapOf<String, List<com.jkhx.player.core.player.subtitle.model.SubtitleCue>>() }
    var isSubtitleDisabled by remember { mutableStateOf(false) }
    var hasInitialSubSelected by remember { mutableStateOf(false) }
    val customSubtitleTracks = remember { mutableStateListOf<SubtitleTrackOption>() }
    var showResumePromptDialog by remember { mutableStateOf(false) }
    var resumeTargetPositionMs by remember { mutableStateOf(0L) }
    var hasCheckedInitialResume by remember { mutableStateOf(false) }
    var applySubJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var lastObservedCacheVersion by remember { mutableStateOf(com.jkhx.player.core.player.subtitle.cache.SubtitleCacheManager.cacheClearedCounter.get()) }
    val translatedEnglishSubIds = remember { mutableStateListOf<String>() }

    fun updateSubOffset(newOffset: Long) {
        currentSubOffsetMs = newOffset
        val trackId = selectedSubtitleTrackId
        if (!trackId.isNullOrBlank() && trackId != "off") {
            subtitleManager.cacheManager.saveSubtitleOffset(activeProviderId, activeMovieSlug, currentEpisode.id, trackId, newOffset, movieTmdbId)
            subtitleManager.cacheManager.saveSavedSubtitleTrack(activeProviderId, activeMovieSlug, currentEpisode.id, trackId, tmdbId = movieTmdbId)
        }
    }

    fun loadStreamSubtitles(stream: EpisodeStream) {
        if (stream.subtitles.isEmpty()) return
        coroutineScope.launch(Dispatchers.IO) {
            val client = JkhxApplication.instance.okHttpClient
            for (sub in stream.subtitles) {
                try {
                    val subKey = subtitleManager.cacheManager.makeKey(
                        providerId = activeProviderId ?: "stream",
                        movieSlug = activeMovieSlug ?: "movie",
                        episodeId = currentEpisode.id,
                        language = sub.language.ifBlank { "vi" },
                        tag = "stream_${sub.url.hashCode()}"
                    )

                    var vttContent: String? = null
                    val cachedFile = subtitleManager.cacheManager.getCachedSubtitleFile(subKey)
                    if (cachedFile != null && cachedFile.length() > 0) {
                        vttContent = cachedFile.readText(Charsets.UTF_8)
                    }

                    if (vttContent.isNullOrBlank()) {
                        val reqBuilder = okhttp3.Request.Builder().url(sub.url)
                        stream.headers.forEach { (k, v) ->
                            reqBuilder.header(k, v)
                        }
                        if (!stream.headers.containsKey("User-Agent")) {
                            reqBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        }
                        val response = client.newCall(reqBuilder.build()).execute()
                        if (response.isSuccessful) {
                            val bodyStr = response.body?.string()
                            if (!bodyStr.isNullOrBlank()) {
                                vttContent = bodyStr
                                subtitleManager.cacheManager.saveSubtitle(subKey, bodyStr)
                            }
                        }
                    }

                    if (!vttContent.isNullOrBlank()) {
                        val parsedCues = com.jkhx.player.core.player.subtitle.parser.SrtParser.parse(vttContent)
                        if (parsedCues.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                subtitleCuesMap[sub.url] = parsedCues
                                subtitleCuesMap[sub.label] = parsedCues

                                val savedTrack = subtitleManager.cacheManager.getSavedSubtitleTrack(activeProviderId, activeMovieSlug, currentEpisode.id, movieTmdbId)
                                val isSavedSub = (selectedSubtitleTrackId == sub.url || selectedSubtitleTrackId == sub.label) ||
                                                 (savedTrack != null && (savedTrack == sub.url || savedTrack == sub.label || savedTrack == "stream_${sub.url.hashCode()}"))
                                val isViSub = sub.language.equals("vi", true) || sub.label.contains("việt", true) || sub.label.contains("viet", true)
                                val shouldAutoApply = !isSubtitleDisabled && (
                                    isSavedSub ||
                                    (currentSubCues.isEmpty() && isViSub && selectedSubtitleTrackId != "off" && (selectedSubtitleTrackId == null || (selectedSubtitleTrackId?.startsWith("online_") != true && selectedSubtitleTrackId?.startsWith("cached_gemini_") != true)))
                                )

                                if (shouldAutoApply) {
                                    currentSubCues = parsedCues
                                    selectedSubtitleTrackId = sub.url
                                    val savedOffset = subtitleManager.cacheManager.getSubtitleOffset(
                                        activeProviderId, activeMovieSlug, currentEpisode.id, sub.url, movieTmdbId
                                    ).takeIf { it != 0L } ?: subtitleManager.cacheManager.getSubtitleOffset(
                                        activeProviderId, activeMovieSlug, currentEpisode.id, sub.label, movieTmdbId
                                    )
                                    currentSubOffsetMs = savedOffset

                                    val streamTrack = SubtitleTrackOption(
                                        id = sub.url,
                                        label = sub.label,
                                        language = sub.language.ifBlank { "vi" },
                                        isSelected = true,
                                        groupIndex = -1,
                                        trackIndex = -1
                                    )
                                    customSubtitleTracks.removeAll { it.id == sub.url || it.id == sub.label }
                                    customSubtitleTracks.add(0, streamTrack)
                                    val currentExoSubs = exoPlayerManager.getSubtitleTracks()
                                    subtitleTracks = mergeSubtitleTracks(customSubtitleTracks, currentExoSubs)

                                    exoPlayerManager.player.trackSelectionParameters = exoPlayerManager.player.trackSelectionParameters
                                        .buildUpon()
                                        .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                                        .build()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("[PlayerScreen] Lỗi nạp phụ đề stream '${sub.label}': ${e.message}")
                }
            }
        }
    }

    fun checkAndLoadCachedGeminiSub(): Boolean {
        val geminiKey = subtitleManager.cacheManager.makeKey(
            providerId = activeProviderId,
            movieSlug = activeMovieSlug,
            episodeId = currentEpisode.id,
            language = "vi",
            tag = "gemini_ai"
        )
        val cachedFile = subtitleManager.cacheManager.getCachedSubtitleFile(geminiKey)
        if (cachedFile != null && cachedFile.length() > 0) {
            try {
                val cues = com.jkhx.player.core.player.subtitle.parser.SrtParser.parse(cachedFile.readText(Charsets.UTF_8))
                val customId = "cached_gemini_${activeProviderId}_${currentEpisode.id}"
                val label = "Tiếng Việt (✨ Bản dịch Gemini AI)"
                val track = SubtitleTrackOption(
                    id = customId,
                    label = label,
                    language = "vi",
                    isSelected = false,
                    groupIndex = -1,
                    trackIndex = -1
                )
                customSubtitleTracks.removeAll { it.id.startsWith("cached_gemini_") || it.id.startsWith("custom_gemini_") }
                customSubtitleTracks.add(0, track)
                subtitleCuesMap[customId] = cues
                subtitleCuesMap[label] = cues
                val streamSubs = exoPlayerManager.getSubtitleTracks()
                subtitleTracks = mergeSubtitleTracks(customSubtitleTracks, streamSubs)
                return true
            } catch (_: Exception) {}
        }
        return false
    }

    fun applyOnlineSub(item: com.jkhx.player.core.player.subtitle.model.OnlineSubtitleItem) {
        applySubJob?.cancel()
        activePlayerView?.subtitleView?.setCues(emptyList())
        currentSubCues = emptyList()
        applySubJob = coroutineScope.launch {
            downloadingSubItemId = item.id
            var customId = ""
            var initialSrtContent = ""
            var initialCuesCount = 0
            try {
                val srtFile = subtitleManager.downloadSubtitle(
                    item = item,
                    providerId = activeProviderId,
                    movieSlug = activeMovieSlug,
                    episodeId = currentEpisode.id
                )
                val srtContent = srtFile.readText()
                initialSrtContent = srtContent
                val parsedCues = com.jkhx.player.core.player.subtitle.parser.SrtParser.parse(srtContent)
                initialCuesCount = parsedCues.size
                customId = "online_${item.source}_${item.id}"
                val savedOffset = subtitleManager.cacheManager.getSubtitleOffset(activeProviderId, activeMovieSlug, currentEpisode.id, customId, movieTmdbId)
                currentSubCues = parsedCues
                currentSubOffsetMs = savedOffset

                isSubtitleDisabled = false
                selectedSubtitleTrackId = customId
                activeOnlineSubId = item.id
                subtitleCuesMap[customId] = parsedCues

                val trackLabel = "${item.name} [${item.source}]"
                customSubtitleTracks.removeAll { it.id.startsWith("online_") }
                val currentExoSubs = exoPlayerManager.getSubtitleTracks()
                subtitleTracks = mergeSubtitleTracks(customSubtitleTracks, currentExoSubs)

                subtitleManager.cacheManager.saveSavedSubtitleTrack(
                    activeProviderId,
                    activeMovieSlug,
                    currentEpisode.id,
                    customId,
                    label = trackLabel,
                    language = item.language,
                    tmdbId = movieTmdbId
                )
                if (item.language.startsWith("vi", ignoreCase = true)) {
                    selectedLangGroup = "vi"
                } else if (item.language.startsWith("en", ignoreCase = true)) {
                    selectedLangGroup = "en"
                }
                exoPlayerManager.setSubtitleTrack(null)
                exoPlayerManager.player.trackSelectionParameters = exoPlayerManager.player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                    .build()
                Toast.makeText(context, context.getString(R.string.player_subtitles_selected_track, item.name, item.source, item.languageName), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Toast.makeText(context, "Lỗi tải phụ đề: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                downloadingSubItemId = null
            }

            // Nếu là phụ đề AI từ Stremio Addon đang dịch từng phần ("[Đang dịch...]"), tự động poll cập nhật ngầm mỗi 5 giây
            val dlUrl = item.downloadUrl
            val needsLivePolling = !dlUrl.isNullOrBlank() && dlUrl.startsWith("http", ignoreCase = true) && customId.isNotBlank() && (
                initialSrtContent.contains("[Đang dịch...") ||
                item.name.contains("Đang Dịch", ignoreCase = true) ||
                item.name.contains("Translating", ignoreCase = true) ||
                (item.isAiTranslated && initialCuesCount in 0..3)
            )
            if (needsLivePolling && dlUrl != null) {
                var pollAttempts = 0
                while (isActive && selectedSubtitleTrackId == customId && pollAttempts < 120) {
                    delay(5000L)
                    pollAttempts++
                    try {
                        val freshRaw = subtitleManager.downloadRawSubtitleText(dlUrl)
                        val freshCues = com.jkhx.player.core.player.subtitle.parser.SrtParser.parse(freshRaw)
                        if (freshCues.isNotEmpty()) {
                            currentSubCues = freshCues
                            subtitleCuesMap[customId] = freshCues
                        }
                        if (!freshRaw.contains("[Đang dịch...") && freshCues.size > 3) {
                            val cleanSrt = com.jkhx.player.core.player.subtitle.parser.SrtParser.formatToSrt(freshCues)
                            val cacheKey = subtitleManager.cacheManager.makeKey(
                                providerId = activeProviderId,
                                movieSlug = activeMovieSlug,
                                episodeId = currentEpisode.id,
                                language = item.language,
                                tag = "${item.source.lowercase()}_${item.id}"
                            )
                            subtitleManager.cacheManager.saveSubtitle(cacheKey, cleanSrt)
                            break
                        }
                    } catch (_: Exception) {
                        // Bỏ qua lỗi mạng tạm thời trong lúc poll
                    }
                }
            }
        }
    }

    fun loadStremioAddonSubtitles(force: Boolean = false) {
        val activeProviderId = ActivePlayerSession.providerId
        val movieDetail = ActivePlayerSession.movieDetail
        val isNotMovieOrSeries = (activeProviderId?.let {
            pluginManager.isLiveProvider(it) ||
                pluginManager.isAdultProvider(it) ||
                pluginManager.isComicProvider(it) ||
                pluginManager.isNovelProvider(it)
        } == true) || movieDetail?.type?.lowercase() in listOf("live", "adult", "comic", "novel")
        if (isNotMovieOrSeries) return

        val subtitleAddons = pluginManager.getActiveSubtitleAddons()
        if (subtitleAddons.isEmpty()) return
        if (!force && hasLoadedStremioSubsForEp && stremioAddonSubsMap.isNotEmpty()) return

        hasLoadedStremioSubsForEp = true
        isStremioSubsLoading = true
        coroutineScope.launch {
            try {
                val grouped = withContext(Dispatchers.IO) {
                    val movieDetail = ActivePlayerSession.movieDetail
                    val tmdbRepo = JkhxApplication.instance.tmdbRepository
                    val stremioClient = pluginManager.getStremioClient()

                    var imdbId = movieDetail?.imdbId
                        ?: ActivePlayerSession.imdbId
                        ?: Regex("""(tt\d+)""").find(activeMovieSlug)?.groupValues?.getOrNull(1)
                        ?: Regex("""(tt\d+)""").find(currentEpisode.id)?.groupValues?.getOrNull(1)
                        ?: Regex("""(tt\d+)""").find(currentEpisode.slug)?.groupValues?.getOrNull(1)

                    var tmdbId = ActivePlayerSession.tmdbId
                        ?: movieDetail?.tmdbId
                        ?: Regex("""tmdb:(\d+)""", RegexOption.IGNORE_CASE).find(activeMovieSlug)?.groupValues?.getOrNull(1)
                        ?: Regex("""tmdb:(\d+)""", RegexOption.IGNORE_CASE).find(currentEpisode.id)?.groupValues?.getOrNull(1)

                    val isSeries = movieDetail?.type?.lowercase() in listOf("series", "tv", "hoathinh") ||
                        activeMovieSlug.startsWith("series__", ignoreCase = true) ||
                        activeMovieSlug.startsWith("tv__", ignoreCase = true) ||
                        activeEpisodes.size > 1 ||
                        Regex("""tt\d+:\d+:\d+""").containsMatchIn(currentEpisode.id)

                    val mediaType = if (isSeries) "series" else "movie"

                    // --- TẦNG 1: ĐÃ CÓ SẴN TRỰC TIẾP IMDB ID ---
                    if (!imdbId.isNullOrBlank()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.player_subtitles_identified_imdb, imdbId), Toast.LENGTH_SHORT).show()
                        }
                    } else if (!tmdbId.isNullOrBlank()) {
                        // --- TẦNG 2: CÓ TMDB ID -> GỌI EXTERNAL_IDS TMDB API ĐỂ LẤY IMDB ID ---
                        try {
                            imdbId = kotlinx.coroutines.withTimeoutOrNull(3500L) {
                                tmdbRepo.getImdbId(tmdbId!!, mediaType)
                            }
                        } catch (_: Exception) {}

                        if (!imdbId.isNullOrBlank()) {
                            ActivePlayerSession.imdbId = imdbId
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, context.getString(R.string.player_subtitles_found_imdb_from_tmdb, tmdbId, imdbId), Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        // --- TẦNG 3: KHÔNG CÓ CẢ IMDB VÀ TMDB ID -> DÙNG REGEX LỌC TÊN VÀ TÌM KIẾM TRÊN TMDB ---
                        fun cleanForSearch(raw: String): String {
                            return raw
                                .replace(Regex("""\s*[\(\[\{].*?[\)\]\}]"""), " ")
                                .replace(Regex("""[._\s]+(\d{4}|\d{3,4}p|REPACK|UHD|Blu-?ray|Remux|WEB-?DL|WEBRip|HDR\d*|DV|HEVC|x264|x265|TrueHD|Atmos|3D|2D|IMAX|Vietsub|Thuyết\s*Minh|Lồng\s*Tiếng|Cam|HD|FHD).*""", RegexOption.IGNORE_CASE), "")
                                .replace(Regex("""\b(3D|2D|IMAX|4K|UHD|FHD|HD|Vietsub|Thuyết Minh|Lồng Tiếng)\b""", RegexOption.IGNORE_CASE), "")
                                .replace('.', ' ')
                                .trim()
                        }

                        val rawOrigin = ActivePlayerSession.originName?.ifBlank { null }
                            ?: ActivePlayerSession.rawPluginOriginName?.ifBlank { null }
                        val searchTitle = cleanForSearch(rawOrigin ?: movieName)
                        if (searchTitle.isNotBlank()) {
                            // withContext(Dispatchers.Main) {
                            //     Toast.makeText(context, context.getString(R.string.player_subtitles_searching_title, searchTitle), Toast.LENGTH_SHORT).show()
                            // }
                            try {
                                val foundId = kotlinx.coroutines.withTimeoutOrNull(3500L) {
                                    tmdbRepo.searchTmdbId(searchTitle, mediaType)?.toString()
                                } ?: kotlinx.coroutines.withTimeoutOrNull(2500L) {
                                    tmdbRepo.searchTmdbId(searchTitle, if (isSeries) "movie" else "series")?.toString()
                                }
                                if (!foundId.isNullOrBlank()) {
                                    tmdbId = foundId
                                    ActivePlayerSession.tmdbId = foundId
                                }
                            } catch (_: Exception) {}
                        }

                        // Nếu tìm bằng originName thất bại hoặc chưa có, thử tìm bằng movieName
                        if (tmdbId.isNullOrBlank() && movieName.isNotBlank() && movieName != rawOrigin) {
                            val cleanMovie = cleanForSearch(movieName)
                            if (cleanMovie.isNotBlank() && cleanMovie != searchTitle) {
                                try {
                                    val foundId = kotlinx.coroutines.withTimeoutOrNull(3000L) {
                                        tmdbRepo.searchTmdbId(cleanMovie, mediaType)?.toString()
                                    }
                                    if (!foundId.isNullOrBlank()) {
                                        tmdbId = foundId
                                        ActivePlayerSession.tmdbId = foundId
                                    }
                                } catch (_: Exception) {}
                            }
                        }

                        // Nếu tìm được TMDB ID từ tầng 3, gọi API external_ids để lấy IMDb ID
                        if (!tmdbId.isNullOrBlank()) {
                            try {
                                imdbId = kotlinx.coroutines.withTimeoutOrNull(3500L) {
                                    tmdbRepo.getImdbId(tmdbId!!, mediaType)
                                }
                            } catch (_: Exception) {}
                            if (!imdbId.isNullOrBlank()) {
                                ActivePlayerSession.imdbId = imdbId
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, context.getString(R.string.player_subtitles_matched_imdb, imdbId), Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                println("[PlayerScreen] Stremio subtitles: TMDb ID found ($tmdbId) but IMDb ID not found")
                            }
                        } else {
                            println("[PlayerScreen] Stremio subtitles: Could not match TMDb or IMDb ID for $searchTitle")
                        }
                    }

                    if (!tmdbId.isNullOrBlank() && ActivePlayerSession.tmdbId.isNullOrBlank()) {
                        ActivePlayerSession.tmdbId = tmdbId
                    }
                    if (!imdbId.isNullOrBlank() && ActivePlayerSession.imdbId.isNullOrBlank()) {
                        ActivePlayerSession.imdbId = imdbId
                    }

                    val epMatchFromId = Regex(""":(\d+):(\d+)$""").find(currentEpisode.id)
                    val epMatchFromName = Regex("""S(\d+)\s*[•\-:]?\s*E(\d+)""", RegexOption.IGNORE_CASE).find(currentEpisode.name)
                    val seasonNum = epMatchFromId?.groupValues?.getOrNull(1)?.toIntOrNull()
                        ?: epMatchFromName?.groupValues?.getOrNull(1)?.toIntOrNull()
                        ?: 1
                    val epNum = epMatchFromId?.groupValues?.getOrNull(2)?.toIntOrNull()
                        ?: epMatchFromName?.groupValues?.getOrNull(2)?.toIntOrNull()
                        ?: currentEpisode.number
                        ?: Regex("""(\d+)""").find(currentEpisode.name)?.groupValues?.getOrNull(1)?.toIntOrNull()
                        ?: 1

                    val videoId = when {
                        !imdbId.isNullOrBlank() -> if (isSeries) "$imdbId:$seasonNum:$epNum" else imdbId
                        currentEpisode.id.isNotBlank() && !currentEpisode.id.startsWith("http", ignoreCase = true) -> currentEpisode.id
                        else -> null
                    }

                    if (videoId.isNullOrBlank()) {
                        println("[PlayerScreen] Stremio subtitles: No videoId available to fetch subtitles")
                        emptyMap()
                    } else {
                        val allowedLangs = configuredSubLanguages.ifEmpty { setOf("vi", "en") }
                        val allAddonSubs = subtitleAddons.map { (addonUrl, sManifest) ->
                            async {
                                try {
                                    val subs = stremioClient.fetchSubtitles(addonUrl, mediaType, videoId)
                                    val cleanAddonName = sManifest.name.replace(Regex("""\[.*?\]"""), "").trim().ifBlank { sManifest.name }
                                    subs.mapIndexedNotNull { idx, s ->
                                        val normLang = com.jkhx.player.core.player.subtitle.model.SubtitleSupportedLanguages.normalizeLanguageCode(s.lang)
                                        // Quy tắc người dùng: Áp dụng Cài đặt Ngôn ngữ tìm kiếm phụ đề cho Addon, chỉ tìm và tải phụ đề theo ngôn ngữ được bật
                                        if (!allowedLangs.contains(normLang.lowercase())) return@mapIndexedNotNull null
                                        val langDisplay = com.jkhx.player.core.player.subtitle.model.SubtitleSupportedLanguages.getDisplayName(normLang)
                                        val rawTitle = s.displayTitle?.trim().takeIf { !it.isNullOrBlank() } ?: "$cleanAddonName ($langDisplay)"
                                        val format = if (s.url.lowercase().contains(".vtt")) "vtt" else "srt"
                                        com.jkhx.player.core.player.subtitle.model.OnlineSubtitleItem(
                                            id = s.id?.takeIf { it.isNotBlank() } ?: "stremio_${sManifest.id}_${idx}_${s.url.hashCode()}",
                                            source = cleanAddonName,
                                            name = rawTitle,
                                            language = normLang,
                                            languageName = langDisplay,
                                            downloadUrl = s.url,
                                            isAiTranslated = rawTitle.contains("AI", ignoreCase = true) ||
                                                rawTitle.contains("Gemini", ignoreCase = true) ||
                                                rawTitle.contains("Groq", ignoreCase = true) ||
                                                cleanAddonName.contains("Gemini", ignoreCase = true) ||
                                                cleanAddonName.contains("Groq", ignoreCase = true),
                                            format = format
                                        )
                                    }
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            }
                        }.awaitAll().flatten()

                        withContext(Dispatchers.Main) {
                            if (allAddonSubs.isNotEmpty()) {
                                Toast.makeText(context, context.getString(R.string.player_subtitles_loaded_addons, allAddonSubs.size), Toast.LENGTH_SHORT).show()
                            }
                        }

                        allAddonSubs.groupBy { it.language.lowercase() }
                    }
                }
                if (force) {
                    stremioAddonSubsMap.clear()
                }
                for ((langKey, items) in grouped) {
                    stremioAddonSubsMap[langKey] = items
                }
            } catch (e: Exception) {
                println("[PlayerScreen] Lỗi nạp phụ đề từ Stremio Addons: ${e.message}")
            } finally {
                isStremioSubsLoading = false
            }
        }
    }

    fun executeOnlineSubSearch(lang: String, force: Boolean = false) {
        val queryKeyword = customSubSearchQuery?.ifBlank { null }
            ?: ActivePlayerSession.originName?.ifBlank { null }
            ?: ActivePlayerSession.rawPluginOriginName?.ifBlank { null }
            ?: ActivePlayerSession.movieName
        if (queryKeyword.isBlank()) return

        coroutineScope.launch {
            loadingSubsMap[lang] = true
            try {
                val query = com.jkhx.player.core.player.subtitle.model.SubtitleSearchQuery(
                    query = queryKeyword,
                    imdbId = ActivePlayerSession.imdbId ?: ActivePlayerSession.movieDetail?.imdbId,
                    tmdbId = ActivePlayerSession.tmdbId ?: ActivePlayerSession.movieDetail?.tmdbId,
                    year = ActivePlayerSession.movieYear,
                    seasonNumber = null,
                    episodeNumber = currentEpisode.name.filter { it.isDigit() }.toIntOrNull(),
                    languages = listOf(lang)
                )
                val results = subtitleManager.searchOnlineSubtitles(query, targetLanguage = lang, forceRefresh = force)
                onlineSubsMap[lang] = results
            } catch (e: Exception) {
                println("[PlayerScreen Subtitle] Lỗi tìm sub $lang: ${e.message}")
            } finally {
                loadingSubsMap[lang] = false
            }
        }
    }

    fun translateOnlineSubWithGemini(item: com.jkhx.player.core.player.subtitle.model.OnlineSubtitleItem) {
        applySubJob?.cancel()
        applySubJob = coroutineScope.launch {
            downloadingSubItemId = item.id
            isTranslatingWithGemini = true
            geminiTranslateProgress = 0f
            try {
                Toast.makeText(context, context.getString(R.string.player_subtitles_downloading), Toast.LENGTH_SHORT).show()
                val srtFile = subtitleManager.downloadSubtitle(
                    item = item,
                    providerId = activeProviderId,
                    movieSlug = activeMovieSlug,
                    episodeId = currentEpisode.id
                )
                val srtContent = srtFile.readText()
                val parsedCues = com.jkhx.player.core.player.subtitle.parser.SrtParser.parse(srtContent)

                val (file, translatedCues) = subtitleManager.translateToVietnamese(
                    cues = parsedCues,
                    providerId = activeProviderId,
                    movieSlug = activeMovieSlug,
                    episodeId = currentEpisode.id,
                    onProgress = { p -> geminiTranslateProgress = p }
                )
                val label = "Tiếng Việt (✨ Gemini AI: ${item.name.take(24)})"
                val customId = "custom_gemini_${item.source}_${item.id}"
                val savedOffset = subtitleManager.cacheManager.getSubtitleOffset(activeProviderId, activeMovieSlug, currentEpisode.id, customId, movieTmdbId)
                activePlayerView?.subtitleView?.setCues(emptyList())
                currentSubCues = translatedCues
                currentSubOffsetMs = savedOffset
                val customTrack = SubtitleTrackOption(
                    id = customId,
                    label = label,
                    language = "vi",
                    isSelected = true,
                    groupIndex = -1,
                    trackIndex = -1
                )
                customSubtitleTracks.removeAll { it.id.startsWith("custom_gemini_") || it.id.startsWith("cached_gemini_") || it.label.contains("Gemini AI", true) }
                customSubtitleTracks.add(0, customTrack)
                val streamSubs = exoPlayerManager.getSubtitleTracks()
                subtitleTracks = mergeSubtitleTracks(customSubtitleTracks, streamSubs)
                isSubtitleDisabled = false
                selectedSubtitleTrackId = customId
                activeOnlineSubId = null
                subtitleCuesMap[customId] = translatedCues
                subtitleCuesMap[label] = translatedCues
                subtitleManager.cacheManager.saveSavedSubtitleTrack(
                    activeProviderId,
                    activeMovieSlug,
                    currentEpisode.id,
                    customId,
                    label = label,
                    language = "vi",
                    tmdbId = movieTmdbId
                )
                if (!translatedEnglishSubIds.contains(item.id)) {
                    translatedEnglishSubIds.add(item.id)
                }
                selectedLangGroup = "vi"
                exoPlayerManager.setSubtitleTrack(null)
                exoPlayerManager.player.trackSelectionParameters = exoPlayerManager.player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                    .build()
                Toast.makeText(context, context.getString(R.string.player_subtitles_translation_success), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Toast.makeText(context, context.getString(R.string.player_subtitles_translation_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            } finally {
                downloadingSubItemId = null
                isTranslatingWithGemini = false
            }
        }
    }

    LaunchedEffect(showSubtitleSheet, selectedLangGroup, customSubSearchQuery) {
        if (showSubtitleSheet) {
            val currentCacheVersion = com.jkhx.player.core.player.subtitle.cache.SubtitleCacheManager.cacheClearedCounter.get()
            if (currentCacheVersion != lastObservedCacheVersion) {
                lastObservedCacheVersion = currentCacheVersion
                onlineSubsMap.clear()
                stremioAddonSubsMap.clear()
                customSubtitleTracks.clear()
                subtitleCuesMap.clear()
                hasAutoAppliedInitialSub = false
                loadStremioAddonSubtitles(force = true)
                if (selectedLangGroup != "none") {
                    executeOnlineSubSearch(selectedLangGroup, force = true)
                }
            } else {
                checkAndLoadCachedGeminiSub()
                loadStremioAddonSubtitles(force = false)
                if (selectedLangGroup != "none" && onlineSubsMap[selectedLangGroup] == null && loadingSubsMap[selectedLangGroup] != true) {
                    executeOnlineSubSearch(selectedLangGroup, force = false)
                }
            }
        }
    }

    // Thuyết minh AI (TTS)
    val isTtsEnabled by settingsManager.isTtsEnabled.collectAsState()
    val ttsEngine by settingsManager.ttsEngine.collectAsState()
    val ttsVoice by settingsManager.ttsVoice.collectAsState()
    val ttsSpeechRate by settingsManager.ttsSpeechRate.collectAsState()
    val ttsDuckingVolume by settingsManager.ttsDuckingVolume.collectAsState()
    val ttsApiEndpoint by settingsManager.ttsApiEndpoint.collectAsState()
    val ttsApiKey by settingsManager.ttsApiKey.collectAsState()
    val ttsAudioFormat by settingsManager.ttsAudioFormat.collectAsState()
    val geminiApiKey by settingsManager.geminiApiKey.collectAsState()

    val ttsManager = remember {
        com.jkhx.player.core.player.tts.TtsManager(
            context = context,
            okHttpClient = com.jkhx.player.JkhxApplication.instance.okHttpClient,
            videoPlayerProvider = { exoPlayerManager.player },
            config = com.jkhx.player.core.player.tts.model.TtsConfig(
                isEnabled = settingsManager.isTtsEnabled.value,
                engineType = com.jkhx.player.core.player.tts.model.TtsEngineType.entries.firstOrNull { it.id == settingsManager.ttsEngine.value }
                    ?: com.jkhx.player.core.player.tts.model.TtsEngineType.GEMINI_38_FLASH_TTS,
                voiceId = settingsManager.ttsVoice.value,
                speechRate = settingsManager.ttsSpeechRate.value,
                duckingVolume = settingsManager.ttsDuckingVolume.value,
                apiEndpoint = settingsManager.ttsApiEndpoint.value,
                apiKey = settingsManager.getEffectiveTtsApiKey(),
                audioFormat = settingsManager.ttsAudioFormat.value
            )
        )
    }

    DisposableEffect(ttsManager) {
        onDispose {
            ttsManager.release()
        }
    }

    DisposableEffect(exoPlayerManager.player) {
        val player = exoPlayerManager.player
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: androidx.media3.common.Player.PositionInfo,
                newPosition: androidx.media3.common.Player.PositionInfo,
                reason: Int
            ) {
                if (reason == androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK) {
                    ttsManager.onSeek(newPosition.positionMs)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    LaunchedEffect(isTtsEnabled, ttsEngine, ttsVoice, ttsSpeechRate, ttsDuckingVolume, ttsApiEndpoint, ttsApiKey, geminiApiKey, ttsAudioFormat) {
        ttsManager.updateConfig(
            com.jkhx.player.core.player.tts.model.TtsConfig(
                isEnabled = isTtsEnabled,
                engineType = com.jkhx.player.core.player.tts.model.TtsEngineType.entries.firstOrNull { it.id == ttsEngine }
                    ?: com.jkhx.player.core.player.tts.model.TtsEngineType.GEMINI_38_FLASH_TTS,
                voiceId = ttsVoice,
                speechRate = ttsSpeechRate,
                duckingVolume = ttsDuckingVolume,
                apiEndpoint = ttsApiEndpoint,
                apiKey = settingsManager.getEffectiveTtsApiKey(),
                audioFormat = ttsAudioFormat
            )
        )
    }

    LaunchedEffect(currentSubCues) {
        ttsManager.setCues(currentSubCues)
    }

    LaunchedEffect(isTtsEnabled, currentSubCues, currentSubOffsetMs) {
        if (!isTtsEnabled || currentSubCues.isEmpty()) {
            ttsManager.stopAudio()
            return@LaunchedEffect
        }
        while (true) {
            val p = exoPlayerManager.player
            ttsManager.onPlaybackTick(
                currentPositionMs = p.currentPosition,
                isVideoPlaying = p.isPlaying,
                offsetMs = currentSubOffsetMs
            )
            delay(50)
        }
    }

    // Cử chỉ HUD (Volume & Brightness & Double Tap Seek & Swipe Seek)
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val windowInfo = LocalWindowInfo.current
    var hasCustomBrightness by remember { mutableStateOf(false) }
    var lastCustomBrightnessValue by remember { mutableStateOf(-1f) }

    // Khi cửa sổ mất tiêu điểm (người dùng kéo Quick Settings / thanh thông báo / vuốt đa nhiệm):
    // Ngay lập tức nhả quyền điều khiển độ sáng về hệ thống để không khóa thanh trượt độ sáng của Android
    LaunchedEffect(windowInfo.isWindowFocused) {
        val window = activity?.window ?: return@LaunchedEffect
        val lp = window.attributes
        if (!windowInfo.isWindowFocused) {
            if (lp.screenBrightness >= 0f) {
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = lp
            }
        } else {
            if (hasCustomBrightness && lastCustomBrightnessValue in 0.01f..1.0f) {
                lp.screenBrightness = lastCustomBrightnessValue
                window.attributes = lp
            }
        }
    }

    var hudType by remember { mutableStateOf(HudIndicatorType.NONE) }
    var hudValue by remember { mutableStateOf(0f) } // 0.0 to 1.0
    var hudHideJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var seekGestureDeltaMs by remember { mutableStateOf(0L) }
    var seekGestureTargetMs by remember { mutableStateOf(0L) }
    var savedMuteVolume by remember { mutableStateOf(-1) }

    fun showHud(type: HudIndicatorType, value: Float) {
        hudType = type
        hudValue = value
        hudHideJob?.cancel()
        hudHideJob = coroutineScope.launch {
            delay(1200)
            hudType = HudIndicatorType.NONE
        }
    }

    // Quản lý hướng xoay màn hình (Ghi nhớ trạng thái người dùng đã chọn)
    val savedLandscape = remember { settingsManager.isPlayerLandscape.value }
    var isLandscape by remember { mutableStateOf(savedLandscape) }

    LaunchedEffect(subtitleTextSize, subtitleIsBold, subtitleTextColor, subtitleTextOpacity, subtitleIsOutline, subtitleOutlineColor, subtitleBottomMargin, isLandscape, activePlayerView) {
        applySubtitleAppearance(
            subtitleView = activePlayerView?.subtitleView,
            textSizeSp = subtitleTextSize,
            isBold = subtitleIsBold,
            textColor = subtitleTextColor,
            textOpacityPercent = subtitleTextOpacity,
            isOutlineEnabled = subtitleIsOutline,
            outlineColor = subtitleOutlineColor,
            bottomMarginPercent = subtitleBottomMargin,
            isLandscape = isLandscape
        )
    }

    fun exitWebCustomView() {
        val decor = activity?.window?.decorView as? ViewGroup
        webCustomView?.let { decor?.removeView(it) }
        webCustomViewCallback?.onCustomViewHidden()
        webCustomView = null
        webCustomViewCallback = null
    }

    // === Giữ màn hình sáng, xoay màn hình, ẩn system bars và ăn tràn viền màn hình ===
    DisposableEffect(activity, isLandscape) {
        val window = activity?.window
        val previousOrientation = activity?.requestedOrientation
            ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window?.let { win ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                win.attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            WindowCompat.setDecorFitsSystemWindows(win, false)
        }
        activity?.requestedOrientation = if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        insetsController?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = previousOrientation
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            window?.let { win ->
                WindowCompat.setDecorFitsSystemWindows(win, true)
            }
        }
    }

    // Tự động ẩn Controls sau 4 giây khi phim đã bắt đầu phát thực sự
    LaunchedEffect(showControls, isPlaying, lastInteractionTime, isPlaybackStartedEffective, isLoading) {
        if (showControls && isPlaying && isPlaybackStartedEffective && !isLoading && !isLocked) {
            delay(4000)
            showControls = false
        }
    }

    // Tạm dừng khi app xuống nền, tiếp tục khi quay lại
    DisposableEffect(lifecycleOwner, exoPlayerManager) {
        var wasPlaying = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    wasPlaying = exoPlayerManager.player.isPlaying
                    val pos = exoPlayerManager.player.currentPosition.takeIf { it > 0 } ?: currentPosition
                    val dur = (exoPlayerManager.player.duration.takeIf { it > 0 } ?: totalDuration).takeIf { it > 0 } ?: effectiveTotalDuration
                    if (pos > 0 && dur > 0) {
                        com.jkhx.player.sync.SyncManager.getInstance(context).pushQuickProgressSync(
                            providerId = activeProviderId,
                            movieSlug = activeMovieSlug,
                            episodeId = currentEpisode.id,
                            positionMs = pos,
                            durationMs = dur,
                            movieName = movieName,
                            thumbUrl = movieThumb,
                            episodeName = currentEpisode.name
                        )
                    }
                    exoPlayerManager.pause()
                    activeWebView?.onPause()
                    activity?.window?.let { win ->
                        val lp = win.attributes
                        if (lp.screenBrightness >= 0f) {
                            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                            win.attributes = lp
                        }
                    }
                }
                Lifecycle.Event.ON_START -> {
                    activeWebView?.onResume()
                    if (wasPlaying) exoPlayerManager.play()
                    val currentCacheVersion = com.jkhx.player.core.player.subtitle.cache.SubtitleCacheManager.cacheClearedCounter.get()
                    if (currentCacheVersion != lastObservedCacheVersion) {
                        lastObservedCacheVersion = currentCacheVersion
                        onlineSubsMap.clear()
                        customSubtitleTracks.clear()
                        subtitleCuesMap.clear()
                        hasAutoAppliedInitialSub = false
                        if (selectedLangGroup != "none") {
                            executeOnlineSubSearch(selectedLangGroup, force = true)
                        }
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            activity?.window?.let { win ->
                val lp = win.attributes
                if (lp.screenBrightness >= 0f) {
                    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    win.attributes = lp
                }
            }
        }
    }

    // Nút Back đóng các Sheet/Drawer hoặc thoát fullscreen HTML5 của WebView trước khi thoát màn hình
    val isAnyOverlayOpen = showSubtitleSheet || showAudioSheet || showSettingsDialog ||
            showEpisodesDrawer || showSourceSelectorDrawer || showResumePromptDialog
    BackHandler(enabled = webCustomView != null || isAnyOverlayOpen) {
        when {
            webCustomView != null -> exitWebCustomView()
            showSubtitleSheet -> showSubtitleSheet = false
            showAudioSheet -> showAudioSheet = false
            showSettingsDialog -> showSettingsDialog = false
            showEpisodesDrawer -> showEpisodesDrawer = false
            showSourceSelectorDrawer -> showSourceSelectorDrawer = false
            showResumePromptDialog -> showResumePromptDialog = false
        }
    }

    // Quản lý trạng thái ExoPlayer và Listener
    LaunchedEffect(exoPlayerManager) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) {
                    hasPlaybackStarted = true
                    showControls = true
                    lastInteractionTime = System.currentTimeMillis()
                }
            }

            override fun onRenderedFirstFrame() {
                hasPlaybackStarted = true
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                videoTracks = exoPlayerManager.getVideoTracks()
                audioTracks = exoPlayerManager.getAudioTracks()
                val subs = exoPlayerManager.getSubtitleTracks()
                subtitleTracks = mergeSubtitleTracks(customSubtitleTracks, subs)

                if (isSubtitleDisabled || selectedSubtitleTrackId == "off") {
                    selectedSubtitleTrackId = "off"
                    currentSubCues = emptyList()
                    activePlayerView?.subtitleView?.setCues(emptyList())
                    return
                }

                if (customSubtitleTracks.any { it.id == selectedSubtitleTrackId } || (selectedSubtitleTrackId?.startsWith("online_") == true)) {
                    return
                }

                val savedTrackId = subtitleManager.cacheManager.getSavedSubtitleTrack(activeProviderId, activeMovieSlug, currentEpisode.id, movieTmdbId)
                if (savedTrackId != null && savedTrackId != "off" && !savedTrackId.startsWith("online_") && !savedTrackId.startsWith("cached_gemini_") && !savedTrackId.startsWith("custom_gemini_")) {
                    val matchedStreamTrack = subs.firstOrNull { it.id == savedTrackId || it.label == savedTrackId }
                    if (matchedStreamTrack != null) {
                        val cues = subtitleCuesMap[matchedStreamTrack.id] ?: subtitleCuesMap[matchedStreamTrack.label]
                        selectedSubtitleTrackId = matchedStreamTrack.id
                        currentSubOffsetMs = subtitleManager.cacheManager.getSubtitleOffset(activeProviderId, activeMovieSlug, currentEpisode.id, matchedStreamTrack.id, movieTmdbId)
                        if (cues != null && cues.isNotEmpty()) {
                            currentSubCues = cues
                            exoPlayerManager.player.trackSelectionParameters = exoPlayerManager.player.trackSelectionParameters
                                .buildUpon()
                                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                                .build()
                        } else {
                            exoPlayerManager.setSubtitleTrack(matchedStreamTrack)
                        }
                        return
                    }
                }

                val selectedSub = subs.firstOrNull { it.isSelected }
                if (selectedSub != null) {
                    selectedSubtitleTrackId = selectedSub.id
                    val cues = subtitleCuesMap[selectedSub.id] ?: subtitleCuesMap[selectedSub.label]
                    if (cues != null && cues.isNotEmpty()) {
                        currentSubCues = cues
                        exoPlayerManager.player.trackSelectionParameters = exoPlayerManager.player.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                            .build()
                    }
                } else if (!hasInitialSubSelected && subs.isNotEmpty() && savedTrackId == null) {
                    hasInitialSubSelected = true
                    exoPlayerManager.selectDefaultSubtitleTrack()
                    val recheckedSubs = exoPlayerManager.getSubtitleTracks()
                    val chosen = recheckedSubs.firstOrNull { it.isSelected } ?: subs.first()
                    selectedSubtitleTrackId = chosen.id
                    val cues = subtitleCuesMap[chosen.id] ?: subtitleCuesMap[chosen.label]
                    if (cues != null && cues.isNotEmpty()) {
                        currentSubCues = cues
                        exoPlayerManager.player.trackSelectionParameters = exoPlayerManager.player.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                            .build()
                    }
                } else {
                    selectedSubtitleTrackId = "off"
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isPlayerBuffering = (playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) {
                    exoPlayerRetryCount = 0
                    if (exoPlayerManager.player.playWhenReady || exoPlayerManager.player.isPlaying) {
                        hasPlaybackStarted = true
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    // Tự động phát tập kế tiếp nếu có
                    if (activeEpisodes.isNotEmpty() && currentEpisodeIndex < activeEpisodes.size - 1) {
                        Toast.makeText(context, context.getString(R.string.player_next_episode_toast), Toast.LENGTH_SHORT).show()
                        showResumePromptDialog = false
                        resumeTargetPositionMs = 0L
                        currentEpisodeIndex += 1
                        ActivePlayerSession.currentEpisodeIndex = currentEpisodeIndex
                        ActivePlayerSession.currentEpisode = activeEpisodes[currentEpisodeIndex]
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                println("[PlayerScreen] ExoPlayer error (${error.errorCodeName}): ${error.message}")
                val isContainerError = error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
                val isHtmlError = error.message?.contains("HTML", ignoreCase = true) == true ||
                        error.cause?.message?.contains("HTML", ignoreCase = true) == true

                // Nếu luồng được nhận diện là direct/mp4/mkv nhưng thực chất máy chủ trả về playlist HLS (#EXTM3U)
                // không có đuôi .m3u8 khiến ProgressiveMediaSource báo ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED:
                // Tự động chuyển sang HlsMediaSource (type = "m3u8") ngay trong ExoPlayer!
                val currentResolved = resolvedStream
                if (isContainerError && !isHtmlError && currentResolved != null && currentResolved.type != "m3u8" && currentResolved.type != "hls") {
                    println("[PlayerScreen] Phát hiện lỗi container trên luồng '${currentResolved.type}' -> Tự động chuyển sang giao thức HLS (m3u8) trên ExoPlayer")
                    val hlsStream = currentResolved.copy(type = "m3u8")
                    resolvedStream = hlsStream
                    stremioStreamMap[currentEpisode.id] = hlsStream
                    stremioStreamMap[hlsStream.url] = hlsStream
                    val currentPos = exoPlayerManager.player.currentPosition.coerceAtLeast(0L)
                    exoPlayerManager.prepareStream(hlsStream, startPositionMs = currentPos)
                    return
                }

                val embedFallback = getEffectiveEmbedFallback()

                if ((isContainerError || isHtmlError) && !embedFallback.isNullOrEmpty()) {
                    println("[PlayerScreen] Gặp lỗi container / chặn HTML -> Tự động chuyển sang WebView Player: $embedFallback")
                    webViewUrl = embedFallback
                    webViewHeaders = embedHeadersFor(embedFallback, resolvedStream?.headers)
                    isWebViewMode = true
                    errorMessage = null
                    return
                }

                if (exoPlayerRetryCount < 2) {
                    exoPlayerRetryCount++
                    val retryDelayMs = if (exoPlayerRetryCount == 1) 1500L else 3000L
                    println("[PlayerScreen] Đang thử phục hồi ExoPlayer (lần $exoPlayerRetryCount sau ${retryDelayMs}ms)...")
                    coroutineScope.launch {
                        Toast.makeText(context, "Đang thử kết nối lại luồng phát (lần $exoPlayerRetryCount/2)...", Toast.LENGTH_SHORT).show()
                        delay(retryDelayMs)
                        val currentPos = exoPlayerManager.player.currentPosition.coerceAtLeast(0L)
                        exoPlayerManager.player.seekTo(currentPos)
                        exoPlayerManager.player.prepare()
                        exoPlayerManager.player.play()
                    }
                    return
                }

                if (!embedFallback.isNullOrEmpty()) {
                    println("[PlayerScreen] Hết lượt thử lại -> Tự động chuyển sang WebView Player: $embedFallback")
                    webViewUrl = embedFallback
                    webViewHeaders = embedHeadersFor(embedFallback, resolvedStream?.headers)
                    isWebViewMode = true
                    errorMessage = null
                } else {
                    var httpCode: Int? = null
                    var currentEx: Throwable? = error
                    while (currentEx != null) {
                        if (currentEx is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                            httpCode = currentEx.responseCode
                            break
                        }
                        currentEx = currentEx.cause
                    }

                    val fallbackMsg = context.getString(R.string.player_error_cannot_connect)
                    val detailedMessage = when (httpCode) {
                        502 -> "Lỗi HTTP 502 Bad Gateway: Máy chủ nguồn/Debrid quá tải hoặc lỗi upstream"
                        503 -> "Lỗi HTTP 503 Service Unavailable: Máy chủ nguồn đang bảo trì hoặc tạm ngưng"
                        504 -> "Lỗi HTTP 504 Gateway Timeout: Máy chủ nguồn phản hồi quá lâu"
                        403 -> "Lỗi HTTP 403 Forbidden: Máy chủ nguồn từ chối truy cập (Chặn IP/WAF)"
                        404 -> "Lỗi HTTP 404 Not Found: Luồng phát không tồn tại hoặc đã bị xóa"
                        429 -> "Lỗi HTTP 429 Too Many Requests: Vượt quá giới hạn request đến máy chủ"
                        in 400..599 -> "Lỗi HTTP $httpCode: ${error.message ?: fallbackMsg}"
                        else -> error.message ?: fallbackMsg
                    }
                    errorMessage = context.getString(R.string.player_error_format, error.errorCodeName, detailedMessage)
                }
            }
        }
        exoPlayerManager.player.addListener(listener)
    }

    // Coroutine cập nhật vị trí phát, thời lượng và tự động lưu Watch Progress định kỳ
    LaunchedEffect(exoPlayerManager, currentEpisode.id) {
        while (true) {
            val p = exoPlayerManager.player
            if (!isDraggingSlider && !isSeeking) {
                currentPosition = p.currentPosition
            }
            totalDuration = p.duration.coerceAtLeast(0L)
            bufferedPosition = p.bufferedPosition

            if (p.isPlaying || currentPosition > 0L) {
                if (!hasPlaybackStarted && !isLoading) {
                    hasPlaybackStarted = true
                    showControls = true
                    lastInteractionTime = System.currentTimeMillis()
                }
            }

            if (!isLoading && hasPlaybackStarted && currentPosition > 0L && (p.isPlaying || p.playbackState == Player.STATE_READY)) {
                val durationToSave = if (totalDuration > 0L) totalDuration else effectiveTotalDuration
                withContext(Dispatchers.IO) {
                    try {
                        playbackProgressDao.saveProgress(
                            PlaybackProgressEntity(
                                id = "$activeProviderId:$activeMovieSlug:${currentEpisode.id}",
                                providerId = activeProviderId,
                                movieSlug = activeMovieSlug,
                                episodeId = currentEpisode.id,
                                positionMs = currentPosition,
                                durationMs = durationToSave
                            )
                        )
                        val universalHistoryId = when {
                            !movieTmdbId.isNullOrBlank() -> "tmdb:$movieTmdbId"
                            activeMovieSlug.startsWith("tt", ignoreCase = true) -> "imdb:$activeMovieSlug"
                            else -> "$activeProviderId:$activeMovieSlug"
                        }
                        historyDao.upsert(
                            HistoryEntity(
                                id = universalHistoryId,
                                providerId = activeProviderId,
                                movieSlug = activeMovieSlug,
                                movieName = movieName,
                                thumbUrl = movieThumb,
                                lastEpisodeId = currentEpisode.id,
                                lastEpisodeName = currentEpisode.name,
                                positionMs = currentPosition,
                                durationMs = durationToSave
                            )
                        )
                        if (universalHistoryId.startsWith("tmdb:") || universalHistoryId.startsWith("imdb:")) {
                            historyDao.deleteDuplicatesByNameOrSlug(universalHistoryId, movieName, activeMovieSlug)
                            historyDao.delete("$activeProviderId:$activeMovieSlug")
                        } else {
                            historyDao.deleteDuplicatesByName(universalHistoryId, movieName)
                        }
                    } catch (_: Exception) {}
                }
            }
            delay(1000)
        }
    }

    // Coroutine thời gian thực hiển thị phụ đề với độ trễ (Offset) tuỳ chỉnh
    LaunchedEffect(currentSubCues, currentSubOffsetMs, selectedSubtitleTrackId, activePlayerView) {
        if (selectedSubtitleTrackId == "off" || selectedSubtitleTrackId == null || currentSubCues.isEmpty()) {
            activePlayerView?.subtitleView?.setCues(emptyList())
            return@LaunchedEffect
        }
        try {
            while (true) {
                val p = exoPlayerManager.player
                val pos = p.currentPosition
                val effectivePos = pos - currentSubOffsetMs
                val matching = currentSubCues.filter { cue ->
                    cue.startTimeMs <= effectivePos && effectivePos <= cue.endTimeMs
                }
                if (matching.isEmpty()) {
                    activePlayerView?.subtitleView?.setCues(emptyList())
                } else {
                    // Ghép nối các dòng phụ đề đồng thời thành các dòng riêng biệt (<br/>) trong 1 Cue duy nhất
                    // để SubtitleView vẽ các dòng xếp tầng mượt mà, triệt tiêu 100% hiện tượng đè chồng chữ
                    val combinedHtml = matching
                        .map { it.text.trim() }
                        .filter { it.isNotBlank() }
                        .joinToString("<br/>")
                    if (combinedHtml.isNotBlank()) {
                        val cue = androidx.media3.common.text.Cue.Builder()
                            .setText(android.text.Html.fromHtml(combinedHtml, android.text.Html.FROM_HTML_MODE_LEGACY))
                            .build()
                        activePlayerView?.subtitleView?.setCues(listOf(cue))
                    } else {
                        activePlayerView?.subtitleView?.setCues(emptyList())
                    }
                }
                delay(40)
            }
        } finally {
            if (selectedSubtitleTrackId == "off" || selectedSubtitleTrackId == null || currentSubCues.isEmpty()) {
                activePlayerView?.subtitleView?.setCues(emptyList())
            }
        }
    }

    // Hiệu ứng xoay tròn mượt mà cho nút Refresh nguồn phát
    val sourceRotationAnimatable = remember { androidx.compose.animation.core.Animatable(0f) }
    val sourceRotationAngle by sourceRotationAnimatable.asState()
    LaunchedEffect(isScanningSources) {
        if (isScanningSources) {
            while (isActive) {
                val nextTarget = sourceRotationAnimatable.value + 360f
                sourceRotationAnimatable.animateTo(
                    targetValue = nextTarget,
                    animationSpec = tween(durationMillis = 900, easing = LinearEasing)
                )
            }
        } else if (sourceRotationAnimatable.value > 0f) {
            val current = sourceRotationAnimatable.value
            val nextFull = kotlin.math.ceil(current / 360f) * 360f
            val remaining = nextFull - current
            if (remaining > 5f) {
                val animDuration = ((remaining / 360f) * 600).toInt().coerceIn(150, 600)
                sourceRotationAnimatable.animateTo(
                    targetValue = nextFull,
                    animationSpec = tween(durationMillis = animDuration, easing = FastOutSlowInEasing)
                )
            }
            sourceRotationAnimatable.snapTo(0f)
        }
    }

    // Tự động tổng hợp luồng phát đồng nhất cho Drawer Nguồn phát & Máy chủ
    LaunchedEffect(activeProviderId, currentEpisode.id, movieTmdbId, movieOriginName, movieName, refreshSourcesKey) {
        val isAdultOrComicOrLive = pluginManager.isAdultProvider(activeProviderId) ||
                pluginManager.isComicProvider(activeProviderId) ||
                pluginManager.isNovelProvider(activeProviderId) ||
                pluginManager.isLiveProvider(activeProviderId)

        if (refreshSourcesKey == 0 && ActivePlayerSession.aggregatedStreams.isNotEmpty()) {
            drawerStreams = ActivePlayerSession.aggregatedStreams
            if (isAdultOrComicOrLive) return@LaunchedEffect
        }

        if (isAdultOrComicOrLive) {
            return@LaunchedEffect
        }

        isScanningSources = true
        try {
            val detailForAgg = ActivePlayerSession.movieDetail ?: MovieDetail(
                slug = activeMovieSlug,
                name = movieName,
                originName = movieOriginName ?: movieName,
                type = ActivePlayerSession.movieType,
                posterUrl = movieThumb,
                backdropUrl = movieThumb,
                tmdbId = movieTmdbId,
                imdbId = ActivePlayerSession.imdbId,
                year = movieYear,
                episodes = availableServers
            )
            val aggregator = StreamAggregator(pluginManager)
            aggregator.aggregateStreams(
                providerId = activeProviderId,
                movieDetail = detailForAgg,
                episode = currentEpisode
            ).collect { list ->
                val merged = if (activeUnifiedStream != null && list.none { it.id == activeUnifiedStream?.id || (it.providerId == activeUnifiedStream?.providerId && it.streamUrl == activeUnifiedStream?.streamUrl) }) {
                    listOf(activeUnifiedStream!!) + list
                } else {
                    list
                }
                drawerStreams = merged
                ActivePlayerSession.aggregatedStreams = merged
            }
        } catch (e: Exception) {
            println("[PlayerScreen StreamAggregator] Scan error: ${e.message}")
        } finally {
            isScanningSources = false
        }
    }

    fun switchToUnifiedStream(item: UnifiedStreamItem) {
        val currentPos = if (currentPosition > 5_000L) currentPosition else 0L
        pendingResumePositionMs = currentPos

        val epStream = item.toEpisodeStream()
        ActivePlayerSession.customStream = epStream
        ActiveEpisodeHolder.customStream = epStream
        ActivePlayerSession.activeUnifiedStream = item
        activeUnifiedStream = item

        val targetEp = item.targetEpisode ?: currentEpisode
        activeProviderId = item.providerId
        activeServerName = item.serverName
        currentEpisode = targetEp

        ActivePlayerSession.providerId = item.providerId
        ActivePlayerSession.currentServerName = item.serverName
        ActivePlayerSession.currentEpisode = targetEp
        ActiveEpisodeHolder.episode = targetEp

        reloadStreamTrigger++
        showSourceSelectorDrawer = false
        Toast.makeText(
            context,
            context.getString(R.string.player_sources_switch_success, item.providerName, item.serverName),
            Toast.LENGTH_SHORT
        ).show()
    }

    // Tải và phát luồng Stream khi thay đổi episode hoặc nguồn hoặc bấm Thử lại
    LaunchedEffect(activeProviderId, currentEpisode.id, currentEpisodeIndex, reloadStreamTrigger) {
        hasPlaybackStarted = false
        showControls = true
        lastInteractionTime = System.currentTimeMillis()
        isLoading = true
        errorMessage = null
        isWebViewMode = false
        webViewUrl = null
        showResumePromptDialog = false
        resumeTargetPositionMs = 0L
        // Reset ngay tiến độ thời gian về 0 để tránh hiển thị nhầm thời gian của tập trước
        currentPosition = 0L
        bufferedPosition = 0L
        totalDuration = 0L
        try {
            exoPlayerManager.player.seekTo(0L)
            exoPlayerManager.player.stop()
        } catch (_: Exception) {}

        try {
            // Nếu mở trực tiếp từ Lịch sử xem tiếp (0ms) mà chưa nạp danh sách tập/detail: nạp ngay trong PlayerScreen
            if (availableServers.isEmpty() && ActivePlayerSession.customStream == null && !currentEpisode.id.startsWith("http://") && !currentEpisode.id.startsWith("https://")) {
                withContext(Dispatchers.IO) {
                    var resolvedProviderId = activeProviderId
                    var resolvedMovieSlug = activeMovieSlug
                    var plugin = pluginManager.getPlugin(resolvedProviderId)
                    var detail: MovieDetail? = try {
                        plugin?.getDetail(resolvedMovieSlug)
                    } catch (_: Exception) {
                        null
                    }

                    if (detail == null) {
                        if (resolvedMovieSlug.startsWith("tt", ignoreCase = true)) {
                            val cinemetaPlugin = pluginManager.getPlugin("stremio_cinemeta")
                            if (cinemetaPlugin != null) {
                                val cDetail = try { cinemetaPlugin.getDetail(resolvedMovieSlug) } catch (_: Exception) { null }
                                if (cDetail != null && cDetail.name.isNotBlank()) {
                                    detail = cDetail
                                    plugin = cinemetaPlugin
                                    resolvedProviderId = "stremio_cinemeta"
                                }
                            }
                        }

                        if (detail == null && movieName.isNotBlank()) {
                            val candidatePlugins = pluginManager.getMovieTvPlugins()
                            for (cand in candidatePlugins) {
                                try {
                                    val sRes = cand.search(movieName, 1)
                                    val match = sRes.items.firstOrNull { item ->
                                        item.name.equals(movieName, ignoreCase = true) ||
                                        (!item.originName.isNullOrBlank() && item.originName.equals(movieName, ignoreCase = true)) ||
                                        item.slug == resolvedMovieSlug
                                    } ?: sRes.items.firstOrNull { item ->
                                        item.name.contains(movieName, ignoreCase = true) ||
                                        movieName.contains(item.name, ignoreCase = true)
                                    }
                                    if (match != null) {
                                        val candDetail = cand.getDetail(match.slug)
                                        if (candDetail.episodes.isNotEmpty()) {
                                            detail = candDetail
                                            plugin = cand
                                            resolvedProviderId = cand.id
                                            resolvedMovieSlug = match.slug
                                            break
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }

                    if (detail != null) {
                        var validDetail = detail
                        val slugTail = resolvedMovieSlug.substringAfterLast("__")
                        val isDummyName = validDetail.name.isBlank() || validDetail.name == resolvedMovieSlug || validDetail.name == slugTail
                        if (movieName.isNotBlank() && isDummyName) {
                            validDetail = validDetail.copy(
                                name = movieName,
                                originName = validDetail.originName?.takeIf { it != resolvedMovieSlug && it != slugTail } ?: movieName,
                                posterUrl = validDetail.posterUrl ?: movieThumb,
                                backdropUrl = validDetail.backdropUrl ?: movieThumb,
                                thumbUrl = validDetail.thumbUrl ?: movieThumb
                            )
                        } else if (validDetail.posterUrl == null && !movieThumb.isNullOrBlank()) {
                            validDetail = validDetail.copy(
                                posterUrl = movieThumb,
                                backdropUrl = validDetail.backdropUrl ?: movieThumb,
                                thumbUrl = validDetail.thumbUrl ?: movieThumb
                            )
                        }

                        val targetServer = validDetail.episodes.find { srv ->
                            srv.episodes.any { it.id == currentEpisode.id || it.name == currentEpisode.name }
                        } ?: validDetail.episodes.firstOrNull()

                        val targetEpisode = targetServer?.episodes?.find {
                            it.id == currentEpisode.id || it.name == currentEpisode.name
                        } ?: targetServer?.episodes?.firstOrNull() ?: currentEpisode

                        val sortedEps = targetServer?.episodes ?: listOf(targetEpisode)
                        val targetIdx = sortedEps.indexOfFirst {
                            it.id == targetEpisode.id || it.name == targetEpisode.name
                        }.coerceAtLeast(0)

                        withContext(Dispatchers.Main) {
                            activeProviderId = resolvedProviderId
                            activeMovieSlug = resolvedMovieSlug
                            movieName = validDetail.name.ifBlank { movieName }
                            movieThumb = validDetail.backdropUrl ?: validDetail.posterUrl ?: movieThumb
                            movieTmdbId = validDetail.tmdbId
                            movieOriginName = validDetail.originName
                            movieYear = validDetail.year
                            availableServers = validDetail.episodes
                            rawPluginName = validDetail.name
                            rawPluginOriginName = validDetail.originName
                            activeServerName = targetServer?.serverName ?: ""
                            activeEpisodes = sortedEps
                            currentEpisodeIndex = targetIdx
                            currentEpisode = targetEpisode

                            ActivePlayerSession.providerId = resolvedProviderId
                            ActivePlayerSession.movieSlug = resolvedMovieSlug
                            ActivePlayerSession.movieName = movieName
                            ActivePlayerSession.movieThumb = movieThumb
                            ActivePlayerSession.tmdbId = validDetail.tmdbId
                            ActivePlayerSession.imdbId = validDetail.imdbId
                            ActivePlayerSession.originName = validDetail.originName
                            ActivePlayerSession.movieYear = validDetail.year
                            ActivePlayerSession.availableServers = validDetail.episodes
                            ActivePlayerSession.currentServerName = activeServerName
                            ActivePlayerSession.episodes = sortedEps
                            ActivePlayerSession.currentEpisodeIndex = targetIdx
                            ActivePlayerSession.currentEpisode = targetEpisode
                            ActivePlayerSession.rawPluginName = validDetail.name
                            ActivePlayerSession.rawPluginOriginName = validDetail.originName
                            ActivePlayerSession.movieDetail = validDetail
                            ActiveEpisodeHolder.episode = targetEpisode
                        }
                    }
                }
            }

            val preResolvedStream = ActivePlayerSession.customStream
            var stream = if (preResolvedStream != null) {
                ActivePlayerSession.customStream = null
                ActiveEpisodeHolder.customStream = null
                isCustomStreamActive = true
                rawEmbedUrl = if (preResolvedStream.type == "embed") preResolvedStream.url else null
                preResolvedStream
            } else if (activeProviderId.startsWith("stremio_")) {
                isCustomStreamActive = true
                rawEmbedUrl = null
                stremioStreamMap[currentEpisode.id]
                    ?: stremioStreamMap[currentEpisode.slug]
                    ?: run {
                        if (currentEpisode.id.startsWith("http://") || currentEpisode.id.startsWith("https://")) {
                            val inferredType = StremioPluginWrapper.inferStreamType(
                                streamUrl = currentEpisode.id,
                                rawType = null,
                                metaText = "${currentEpisode.name} ${currentEpisode.serverName}"
                            )
                            val cachedHeaders = stremioStreamMap[currentEpisode.id]?.headers
                                ?: stremioStreamMap[currentEpisode.slug]?.headers
                                ?: emptyMap()
                            com.jkhx.player.core.model.EpisodeStream(
                                type = inferredType,
                                url = currentEpisode.id,
                                headers = cachedHeaders
                            )
                        } else {
                            val directPluginStream = try {
                                pluginManager.getPlugin(activeProviderId)?.getStream(currentEpisode)?.takeIf { it.url.isNotBlank() }
                            } catch (_: Exception) {
                                null
                            }
                            if (directPluginStream != null) {
                                directPluginStream
                            } else {
                                val detailForAgg = ActivePlayerSession.movieDetail ?: MovieDetail(
                                    slug = activeMovieSlug,
                                    name = movieName,
                                    originName = movieOriginName ?: movieName,
                                    type = ActivePlayerSession.movieType,
                                    posterUrl = movieThumb,
                                    backdropUrl = movieThumb,
                                    tmdbId = movieTmdbId,
                                    imdbId = ActivePlayerSession.imdbId,
                                    year = movieYear,
                                    episodes = availableServers
                                )
                                var bestStream: UnifiedStreamItem? = null
                                try {
                                    kotlinx.coroutines.withTimeoutOrNull(12000L) {
                                        StreamAggregator(pluginManager).aggregateStreams(
                                            providerId = activeProviderId,
                                            movieDetail = detailForAgg,
                                            episode = currentEpisode
                                        ).collect { list ->
                                            if (list.isNotEmpty()) {
                                                ActivePlayerSession.aggregatedStreams = list
                                                withContext(Dispatchers.Main) {
                                                    drawerStreams = list
                                                }
                                                if (bestStream == null) {
                                                    bestStream = list.firstOrNull()
                                                    if (bestStream != null) {
                                                        throw kotlinx.coroutines.CancellationException("Found bestStream")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (_: Exception) {}
                                val chosen = bestStream
                                    ?: throw Exception(context.getString(R.string.streams_empty))
                                withContext(Dispatchers.Main) {
                                    activeUnifiedStream = chosen
                                    ActivePlayerSession.activeUnifiedStream = chosen
                                    activeProviderId = chosen.providerId
                                    activeServerName = chosen.serverName
                                    ActivePlayerSession.providerId = chosen.providerId
                                    ActivePlayerSession.currentServerName = chosen.serverName
                                }
                                chosen.toEpisodeStream()
                            }
                        }
                    }
            } else if (currentEpisode.id.startsWith("http://") || currentEpisode.id.startsWith("https://")) {
                isCustomStreamActive = true
                rawEmbedUrl = null
                val inferredType = StremioPluginWrapper.inferStreamType(
                    streamUrl = currentEpisode.id,
                    rawType = null,
                    metaText = "${currentEpisode.name} ${currentEpisode.serverName}"
                )
                val cachedHeaders = stremioStreamMap[currentEpisode.id]?.headers
                    ?: stremioStreamMap[currentEpisode.slug]?.headers
                    ?: emptyMap()
                com.jkhx.player.core.model.EpisodeStream(
                    type = inferredType,
                    url = currentEpisode.id,
                    headers = cachedHeaders
                )
            } else {
                isCustomStreamActive = false
                rawEmbedUrl = fallbackEmbedFromData
                val plugin = pluginManager.getPlugin(activeProviderId)
                    ?: throw Exception(context.getString(R.string.player_plugin_unavailable, activeProviderId))
                plugin.getStream(currentEpisode)
            }

            // Lưu link embed gốc nếu plugin trả về embed
            if (stream.type == "embed") {
                rawEmbedUrl = stream.url
                try {
                    stream = embedResolver.resolve(stream.url)
                } catch (e: Exception) {
                    println("[PlayerScreen] Embed resolver skipped: ${e.message}")
                }
            }

            if (stream.type == "embed" && !stream.url.contains(".m3u8") && !stream.url.contains(".mp4")) {
                println("[PlayerScreen] Stream type là embed -> Chuyển sang WebView: ${stream.url}")
                webViewUrl = stream.url
                webViewHeaders = stream.headers
                isWebViewMode = true
                resolvedStream = stream
            } else {
                println("[PlayerScreen] Stream resolved thành công: type=${stream.type}, url=${stream.url.take(80)}")
                resolvedStream = stream

                // Nếu vừa chuyển đổi nguồn có pendingResumePositionMs thì ưu tiên tiếp tục
                val startPos = if (pendingResumePositionMs > 0L) {
                    val pos = pendingResumePositionMs
                    pendingResumePositionMs = 0L
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.player_resume_toast, formatTime(pos)), Toast.LENGTH_SHORT).show()
                    }
                    pos
                } else if (!hasCheckedInitialResume) {
                    hasCheckedInitialResume = true
                    // Kiểm tra tiến độ đã xem trước đó để hỏi người dùng xem tiếp hay từ đầu (chỉ hỏi khi mới vào Player)
                    val savedProgress = withContext(Dispatchers.IO) {
                        try {
                            playbackProgressDao.getProgress("$activeProviderId:$activeMovieSlug:${currentEpisode.id}")
                        } catch (e: Exception) {
                            null
                        }
                    }

                    if (savedProgress != null && savedProgress.positionMs > 10_000L &&
                        (savedProgress.durationMs <= 0 || savedProgress.positionMs < savedProgress.durationMs - 15_000L)
                    ) {
                        withContext(Dispatchers.Main) {
                            resumeTargetPositionMs = savedProgress.positionMs
                            showResumePromptDialog = true
                        }
                    }
                    0L
                } else {
                    0L
                }

                withContext(Dispatchers.Main) {
                    exoPlayerRetryCount = 0
                    hasInitialSubSelected = false
                    hasAutoAppliedInitialSub = false
                    activeOnlineSubId = null
                    isSubtitleDisabled = false
                    customSubtitleTracks.clear()
                    subtitleCuesMap.clear()
                    currentSubCues = emptyList()
                    currentSubOffsetMs = 0L
                    exoPlayerManager.prepareStream(stream, startPositionMs = startPos)
                    exoPlayerManager.setPlaybackSpeed(currentSpeed)
                    stremioAddonSubsMap.clear()
                    loadStreamSubtitles(stream)
                    loadStremioAddonSubtitles(force = false)

                    // Khôi phục phụ đề đã chọn (Saved Subtitle Track Persistence & Cache Fallback)
                    var subRestored = false
                    val savedTrackId = subtitleManager.cacheManager.getSavedSubtitleTrack(activeProviderId, activeMovieSlug, currentEpisode.id, movieTmdbId)

                    if (savedTrackId == "off") {
                        isSubtitleDisabled = true
                        selectedSubtitleTrackId = "off"
                        currentSubCues = emptyList()
                        activePlayerView?.subtitleView?.setCues(emptyList())
                        exoPlayerManager.setSubtitleTrack(null)
                        exoPlayerManager.player.trackSelectionParameters = exoPlayerManager.player.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                            .build()
                        subRestored = true
                    } else if (savedTrackId != null) {
                        if (savedTrackId.startsWith("cached_gemini_") || savedTrackId.startsWith("custom_gemini_")) {
                            val geminiKey = subtitleManager.cacheManager.makeKey(
                                providerId = activeProviderId ?: "",
                                movieSlug = activeMovieSlug ?: "",
                                episodeId = currentEpisode.id,
                                language = "vi",
                                tag = "gemini_ai"
                            )
                            val cachedFile = subtitleManager.cacheManager.getCachedSubtitleFile(geminiKey)
                            if (cachedFile != null && cachedFile.length() > 0) {
                                try {
                                    val cues = com.jkhx.player.core.player.subtitle.parser.SrtParser.parse(cachedFile.readText(Charsets.UTF_8))
                                    if (cues.isNotEmpty()) {
                                        val customId = "cached_gemini_${activeProviderId}_${currentEpisode.id}"
                                        val label = "Tiếng Việt (✨ Bản dịch Gemini AI)"
                                        val track = SubtitleTrackOption(
                                            id = customId,
                                            label = label,
                                            language = "vi",
                                            isSelected = true,
                                            groupIndex = -1,
                                            trackIndex = -1
                                        )
                                        customSubtitleTracks.removeAll { it.id.startsWith("cached_gemini_") || it.id.startsWith("custom_gemini_") }
                                        customSubtitleTracks.add(0, track)
                                        subtitleCuesMap[customId] = cues
                                        subtitleCuesMap[label] = cues
                                        val streamSubs = exoPlayerManager.getSubtitleTracks()
                                        subtitleTracks = mergeSubtitleTracks(customSubtitleTracks, streamSubs)

                                        currentSubCues = cues
                                        currentSubOffsetMs = subtitleManager.cacheManager.getSubtitleOffset(activeProviderId, activeMovieSlug, currentEpisode.id, customId, movieTmdbId)
                                        isSubtitleDisabled = false
                                        selectedSubtitleTrackId = customId
                                        activeOnlineSubId = null
                                        selectedLangGroup = "vi"
                                        exoPlayerManager.player.trackSelectionParameters = exoPlayerManager.player.trackSelectionParameters
                                            .buildUpon()
                                            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                                            .build()
                                        subRestored = true
                                    }
                                } catch (_: Exception) {}
                            }
                        } else if (savedTrackId.startsWith("online_")) {
                            val parts = savedTrackId.removePrefix("online_").split("_", limit = 2)
                            val source = parts.getOrNull(0) ?: ""
                            val itemId = parts.getOrNull(1) ?: ""
                            val cachedFile = subtitleManager.cacheManager.findCachedOnlineFile(activeProviderId, activeMovieSlug, currentEpisode.id, source, itemId, movieTmdbId)
                            if (cachedFile != null && cachedFile.length() > 0) {
                                try {
                                    val cues = com.jkhx.player.core.player.subtitle.parser.SrtParser.parse(cachedFile.readText(Charsets.UTF_8))
                                    if (cues.isNotEmpty()) {
                                        currentSubCues = cues
                                        currentSubOffsetMs = subtitleManager.cacheManager.getSubtitleOffset(activeProviderId, activeMovieSlug, currentEpisode.id, savedTrackId, movieTmdbId)
                                        isSubtitleDisabled = false
                                        selectedSubtitleTrackId = savedTrackId
                                        activeOnlineSubId = itemId
                                        subtitleCuesMap[savedTrackId] = cues

                                        val savedLang = subtitleManager.cacheManager.getSavedSubtitleLanguage(activeMovieSlug, currentEpisode.id, movieTmdbId)
                                            ?: "vi"
                                        customSubtitleTracks.removeAll { it.id.startsWith("online_") }
                                        val streamSubs = exoPlayerManager.getSubtitleTracks()
                                        subtitleTracks = mergeSubtitleTracks(customSubtitleTracks, streamSubs)
                                        selectedLangGroup = savedLang

                                        exoPlayerManager.setSubtitleTrack(null)
                                        exoPlayerManager.player.trackSelectionParameters = exoPlayerManager.player.trackSelectionParameters
                                            .buildUpon()
                                            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                                            .build()
                                        subRestored = true
                                    }
                                } catch (_: Exception) {}
                            }
                        } else {
                            // savedTrackId là phụ đề từ luồng phát (Stream Subtitle) hoặc bản dịch có sẵn
                            val matchedStreamSub = stream.subtitles.firstOrNull { it.url == savedTrackId || it.label == savedTrackId }
                            if (matchedStreamSub != null) {
                                selectedSubtitleTrackId = matchedStreamSub.url
                                subRestored = true
                            } else {
                                val containerSubs = exoPlayerManager.getSubtitleTracks()
                                val matchedContainerSub = containerSubs.firstOrNull { it.id == savedTrackId || it.label == savedTrackId }
                                if (matchedContainerSub != null) {
                                    val cues = subtitleCuesMap[matchedContainerSub.id] ?: subtitleCuesMap[matchedContainerSub.label]
                                    selectedSubtitleTrackId = matchedContainerSub.id
                                    currentSubOffsetMs = subtitleManager.cacheManager.getSubtitleOffset(activeProviderId, activeMovieSlug, currentEpisode.id, matchedContainerSub.id, movieTmdbId)
                                    if (cues != null && cues.isNotEmpty()) {
                                        currentSubCues = cues
                                        exoPlayerManager.player.trackSelectionParameters = exoPlayerManager.player.trackSelectionParameters
                                            .buildUpon()
                                            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                                            .build()
                                    } else {
                                        exoPlayerManager.setSubtitleTrack(matchedContainerSub)
                                    }
                                    subRestored = true
                                } else {
                                    selectedSubtitleTrackId = savedTrackId
                                }
                            }
                        }
                    }

                    // Nếu chưa khôi phục được sub và người dùng không tắt sub: chạy fallback kiểm tra Gemini cache hoặc auto-search
                    if (!subRestored && !isSubtitleDisabled) {
                        // Kiểm tra xem đã có bản dịch Gemini AI lưu cache cho tập phim này chưa
                        val hasCachedGemini = checkAndLoadCachedGeminiSub()
                        val hasViSub = stream.subtitles.any { it.language.equals("vi", true) || it.label.contains("việt", true) || it.label.contains("viet", true) }
                        if (hasCachedGemini && !hasViSub) {
                            val geminiTrack = customSubtitleTracks.firstOrNull { it.id.startsWith("cached_gemini_") }
                            if (geminiTrack != null) {
                                val cues = subtitleCuesMap[geminiTrack.id] ?: emptyList()
                                currentSubCues = cues
                                currentSubOffsetMs = subtitleManager.cacheManager.getSubtitleOffset(activeProviderId, activeMovieSlug, currentEpisode.id, geminiTrack.id, movieTmdbId)
                                isSubtitleDisabled = false
                                selectedSubtitleTrackId = geminiTrack.id
                                activeOnlineSubId = null
                                selectedLangGroup = "vi"
                                exoPlayerManager.player.trackSelectionParameters = exoPlayerManager.player.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                                    .build()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            val embedFallback = getEffectiveEmbedFallback()
            println("[PlayerScreen] Lỗi getStream: ${e.message}, fallback: $embedFallback")
            if (!embedFallback.isNullOrEmpty()) {
                webViewUrl = embedFallback
                webViewHeaders = embedHeadersFor(embedFallback, resolvedStream?.headers)
                isWebViewMode = true
            } else {
                errorMessage = e.message ?: context.getString(R.string.player_load_stream_error)
            }
        } finally {
            isLoading = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val finalPos = exoPlayerManager.player.currentPosition.takeIf { it > 0 } ?: currentPosition
            val finalDur = (exoPlayerManager.player.duration.takeIf { it > 0 } ?: totalDuration).takeIf { it > 0 } ?: effectiveTotalDuration
            if (finalPos > 0 && finalDur > 0) {
                com.jkhx.player.sync.SyncManager.getInstance(context).pushQuickProgressSync(
                    providerId = activeProviderId,
                    movieSlug = activeMovieSlug,
                    episodeId = currentEpisode.id,
                    positionMs = finalPos,
                    durationMs = finalDur,
                    movieName = movieName,
                    thumbUrl = movieThumb,
                    episodeName = currentEpisode.name
                )
            }
            exitWebCustomView()
            exoPlayerManager.release()
            activeWebView?.apply {
                loadUrl("about:blank")
                stopLoading()
                destroy()
            }
            activeWebView = null
            activity?.window?.let { win ->
                val lp = win.attributes
                if (lp.screenBrightness >= 0f) {
                    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    win.attributes = lp
                }
            }
        }
    }

    val gestureModifier = if (!isWebViewMode) {
        Modifier
            .pointerInput(isLocked) {
                // Nhận diện chạm đơn, chạm đúp và nhấn giữ tăng tốc 2X
                detectTapGestures(
                    onTap = {
                        if (isLocked) {
                            showControls = !showControls
                        } else {
                            if (hudType == HudIndicatorType.NONE && !isDraggingGesture) {
                                showControls = !showControls
                            } else {
                                hudType = HudIndicatorType.NONE
                            }
                            lastInteractionTime = System.currentTimeMillis()
                        }
                    },
                    onDoubleTap = { offset ->
                        if (!isLocked) {
                            lastInteractionTime = System.currentTimeMillis()
                            val width = size.width
                            val player = exoPlayerManager.player
                            if (offset.x < width / 2) {
                                // Tua lùi 10s
                                val target = (player.currentPosition - 10_000).coerceAtLeast(0L)
                                player.seekTo(target)
                                showHud(HudIndicatorType.SEEK_REWIND, 10f)
                            } else {
                                // Tua tới 10s
                                val maxDur = player.duration.coerceAtLeast(0L)
                                val target = if (maxDur > 0) (player.currentPosition + 10_000).coerceAtMost(maxDur) else player.currentPosition + 10_000
                                player.seekTo(target)
                                showHud(HudIndicatorType.SEEK_FORWARD, 10f)
                            }
                        }
                    },
                    onLongPress = {
                        if (!isLocked) {
                            isLongPressing2x = true
                            exoPlayerManager.setPlaybackSpeed(2.0f)
                        }
                    }
                )
            }
            .pointerInput(isLocked) {
                // Nhận diện vuốt: Ngang (Tua Seek) hoặc Dọc (Độ sáng / Âm lượng)
                var isLeftRegion = false
                var brightnessAccumulator = 0.5f
                var volumeAccumulator = 0f
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
                var lastSetVolumeInt = -1

                var dragDirection = 0 // 0: chưa xác định, 1: Tua Seek (ngang), 2: Độ sáng / Âm lượng (dọc)
                var totalDragX = 0f
                var totalDragY = 0f
                var dragStartPosMs = 0L
                var currentSeekDeltaMs = 0L

                detectDragGestures(
                    onDragStart = { offset ->
                        if (!isLocked) {
                            lastInteractionTime = System.currentTimeMillis()
                            isLeftRegion = offset.x < size.width / 2
                            dragDirection = 0
                            totalDragX = 0f
                            totalDragY = 0f
                            currentSeekDeltaMs = 0L
                            dragStartPosMs = exoPlayerManager.player.currentPosition.coerceAtLeast(0L)

                            if (isLeftRegion) {
                                val window = activity?.window
                                val lp = window?.attributes
                                brightnessAccumulator = if (lp != null && lp.screenBrightness >= 0f) {
                                    lp.screenBrightness
                                } else {
                                    try {
                                        val sysBrightness = android.provider.Settings.System.getInt(
                                            context.contentResolver,
                                            android.provider.Settings.System.SCREEN_BRIGHTNESS
                                        )
                                        (sysBrightness / 255f).coerceIn(0.01f, 1.0f)
                                    } catch (_: Exception) {
                                        0.5f
                                    }
                                }
                            } else {
                                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                                volumeAccumulator = currentVol
                                lastSetVolumeInt = currentVol.toInt()
                            }
                        }
                    },
                    onDragEnd = {
                        if (isLongPressing2x) {
                            isLongPressing2x = false
                            exoPlayerManager.setPlaybackSpeed(currentSpeed)
                        }
                        if (dragDirection == 1) {
                            // Chỉ thực hiện seekTo khi người dùng thả tay ra khỏi cảm ứng
                            val maxDur = (exoPlayerManager.player.duration.takeIf { it > 0 } ?: totalDuration).coerceAtLeast(0L)
                            val finalTarget = if (maxDur > 0) {
                                (dragStartPosMs + currentSeekDeltaMs).coerceIn(0L, maxDur)
                            } else {
                                (dragStartPosMs + currentSeekDeltaMs).coerceAtLeast(0L)
                            }
                            exoPlayerManager.player.seekTo(finalTarget)
                            currentPosition = finalTarget
                            dragDirection = 0
                            hudHideJob?.cancel()
                            hudHideJob = coroutineScope.launch {
                                delay(600)
                                if (hudType == HudIndicatorType.SEEK_GESTURE) {
                                    hudType = HudIndicatorType.NONE
                                }
                                isDraggingGesture = false
                            }
                        } else {
                            dragDirection = 0
                            coroutineScope.launch {
                                delay(400)
                                isDraggingGesture = false
                            }
                        }
                    },
                    onDragCancel = {
                        if (isLongPressing2x) {
                            isLongPressing2x = false
                            exoPlayerManager.setPlaybackSpeed(currentSpeed)
                        }
                        dragDirection = 0
                        hudType = HudIndicatorType.NONE
                        isDraggingGesture = false
                    },
                    onDrag = { change, dragAmount ->
                        if (!isLocked) {
                            lastInteractionTime = System.currentTimeMillis()
                            change.consume()

                            totalDragX += dragAmount.x
                            totalDragY += dragAmount.y

                            // Xác định hướng nếu chưa khóa trục cử chỉ
                            if (dragDirection == 0) {
                                val absX = kotlin.math.abs(totalDragX)
                                val absY = kotlin.math.abs(totalDragY)
                                val slop = 16f
                                if (absX > slop || absY > slop) {
                                    dragDirection = if (absX >= absY) 1 else 2
                                    isDraggingGesture = true
                                    showControls = false // Tự động ẩn controls khi người dùng bắt đầu thao tác cử chỉ
                                }
                            }

                            if (dragDirection == 1) {
                                // Cử chỉ vuốt ngang: Tua tới (phải) / Tua lùi (trái)
                                val maxSeekWindow = 90_000L // 90 giây khi vuốt hết chiều rộng màn hình
                                val deltaFractionX = dragAmount.x / size.width.toFloat()
                                currentSeekDeltaMs += (deltaFractionX * maxSeekWindow).toLong()

                                val maxDur = ((exoPlayerManager.player.duration.takeIf { it > 0 } ?: totalDuration).takeIf { it > 0 } ?: effectiveTotalDuration).coerceAtLeast(0L)
                                val targetMs = if (maxDur > 0) {
                                    (dragStartPosMs + currentSeekDeltaMs).coerceIn(0L, maxDur)
                                } else {
                                    (dragStartPosMs + currentSeekDeltaMs).coerceAtLeast(0L)
                                }
                                seekGestureDeltaMs = targetMs - dragStartPosMs
                                seekGestureTargetMs = targetMs
                                hudType = HudIndicatorType.SEEK_GESTURE
                                hudHideJob?.cancel()
                            } else if (dragDirection == 2) {
                                // Cử chỉ vuốt dọc: Độ sáng (nửa trái) và Âm lượng (nửa phải)
                                val deltaFraction = -dragAmount.y / (size.height * 0.75f)

                                if (isLeftRegion) {
                                    brightnessAccumulator = (brightnessAccumulator + deltaFraction).coerceIn(0.01f, 1.0f)
                                    hasCustomBrightness = true
                                    lastCustomBrightnessValue = brightnessAccumulator
                                    val window = activity?.window
                                    val lp = window?.attributes
                                    if (lp != null) {
                                        lp.screenBrightness = brightnessAccumulator
                                        window.attributes = lp
                                    }
                                    showHud(HudIndicatorType.BRIGHTNESS, brightnessAccumulator)
                                } else {
                                    if (maxVolume > 0f) {
                                        volumeAccumulator = (volumeAccumulator + deltaFraction * maxVolume).coerceIn(0f, maxVolume)
                                        val targetVolInt = kotlin.math.round(volumeAccumulator).toInt()
                                        if (targetVolInt != lastSetVolumeInt) {
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolInt, 0)
                                            lastSetVolumeInt = targetVolInt
                                        }
                                        showHud(HudIndicatorType.VOLUME, volumeAccumulator / maxVolume)
                                    }
                                }
                            }
                        }
                    }
                )
            }
    } else {
        Modifier
    }

    val rootPlayerFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val capsulePillFocusRequester = remember { FocusRequester() }
    val sourceSelectorInitialFocusRequester = remember { FocusRequester() }
    val episodesDrawerInitialFocusRequester = remember { FocusRequester() }
    val subtitleSheetInitialFocusRequester = remember { FocusRequester() }
    val audioSheetInitialFocusRequester = remember { FocusRequester() }
    val settingsDialogInitialFocusRequester = remember { FocusRequester() }

    LaunchedEffect(showControls, isPlaybackStartedEffective, isAnyOverlayOpen, isLoading, isPlayerBuffering) {
        if (isAnyOverlayOpen) return@LaunchedEffect
        if (showControls && isPlaybackStartedEffective && !isLoading && !isPlayerBuffering) {
            delay(100) // Chờ layout AnimatedVisibility mount xong controls
            try {
                playPauseFocusRequester.requestFocus()
            } catch (_: Throwable) {}
        } else {
            delay(50)
            try {
                rootPlayerFocusRequester.requestFocus()
            } catch (_: Throwable) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootPlayerFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                lastInteractionTime = System.currentTimeMillis()
                val nativeCode = keyEvent.nativeKeyEvent.keyCode

                // Phím Media chuyên dụng trên Remote TV / HDMI-CEC hoạt động mọi lúc
                when (nativeCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (isPlaying) exoPlayerManager.pause() else exoPlayerManager.play()
                        showControls = true
                        return@onPreviewKeyEvent true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        exoPlayerManager.play()
                        showControls = true
                        return@onPreviewKeyEvent true
                    }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        exoPlayerManager.pause()
                        showControls = true
                        return@onPreviewKeyEvent true
                    }
                    KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        val target = (exoPlayerManager.player.currentPosition - 10_000).coerceAtLeast(0L)
                        exoPlayerManager.player.seekTo(target)
                        showHud(HudIndicatorType.SEEK_REWIND, 10f)
                        return@onPreviewKeyEvent true
                    }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        val maxDur = exoPlayerManager.player.duration.coerceAtLeast(0L)
                        val target = if (maxDur > 0) (exoPlayerManager.player.currentPosition + 10_000).coerceAtMost(maxDur) else exoPlayerManager.player.currentPosition + 10_000
                        exoPlayerManager.player.seekTo(target)
                        showHud(HudIndicatorType.SEEK_FORWARD, 10f)
                        return@onPreviewKeyEvent true
                    }
                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        if (activeEpisodes.isNotEmpty() && currentEpisodeIndex < activeEpisodes.size - 1) {
                            showResumePromptDialog = false
                            resumeTargetPositionMs = 0L
                            currentEpisodeIndex += 1
                            ActivePlayerSession.currentEpisodeIndex = currentEpisodeIndex
                            ActivePlayerSession.currentEpisode = activeEpisodes[currentEpisodeIndex]
                        }
                        return@onPreviewKeyEvent true
                    }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        if (activeEpisodes.isNotEmpty() && currentEpisodeIndex > 0) {
                            showResumePromptDialog = false
                            resumeTargetPositionMs = 0L
                            currentEpisodeIndex -= 1
                            ActivePlayerSession.currentEpisodeIndex = currentEpisodeIndex
                            ActivePlayerSession.currentEpisode = activeEpisodes[currentEpisodeIndex]
                        }
                        return@onPreviewKeyEvent true
                    }
                }

                // Khi không mở Sheet/Drawer và Controls đang ẩn: Phím D-Pad điều khiển trực tiếp Player
                if (!isAnyOverlayOpen && !isWebViewMode && !isLocked && !showControls) {
                    when (nativeCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            val target = (exoPlayerManager.player.currentPosition - 10_000).coerceAtLeast(0L)
                            exoPlayerManager.player.seekTo(target)
                            showHud(HudIndicatorType.SEEK_REWIND, 10f)
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            val maxDur = exoPlayerManager.player.duration.coerceAtLeast(0L)
                            val target = if (maxDur > 0) (exoPlayerManager.player.currentPosition + 10_000).coerceAtMost(maxDur) else exoPlayerManager.player.currentPosition + 10_000
                            exoPlayerManager.player.seekTo(target)
                            showHud(HudIndicatorType.SEEK_FORWARD, 10f)
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            if (isPlaying) exoPlayerManager.pause() else exoPlayerManager.play()
                            showControls = true
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent.KEYCODE_DPAD_DOWN,
                        KeyEvent.KEYCODE_MENU,
                        KeyEvent.KEYCODE_INFO -> {
                            showControls = true
                            return@onPreviewKeyEvent true
                        }
                    }
                }
                false
            }
            .then(gestureModifier)
    ) {
        // 1. Chế độ phát Web Embed (WebView Player)
        if (isWebViewMode && webViewUrl != null) {
            PlayerWebViewPlayer(
                webViewUrl = webViewUrl!!,
                webViewHeaders = webViewHeaders,
                resolvedStream = resolvedStream,
                activity = activity,
                onActiveWebViewChange = { activeWebView = it },
                webCustomView = webCustomView,
                onShowCustomView = { view, callback ->
                    webCustomView = view
                    webCustomViewCallback = callback
                },
                onHideCustomView = { exitWebCustomView() }
            )
        }
        // 2. Chế độ phát ExoPlayer Native (.m3u8, .mp4, DASH)
        else if (resolvedStream != null) {
            PlayerNativeView(
                exoPlayerManager = exoPlayerManager,
                resizeMode = resizeModes[resizeModeIndex].first,
                subtitleTextSize = subtitleTextSize,
                subtitleIsBold = subtitleIsBold,
                subtitleTextColor = subtitleTextColor,
                subtitleTextOpacity = subtitleTextOpacity,
                subtitleIsOutline = subtitleIsOutline,
                subtitleOutlineColor = subtitleOutlineColor,
                subtitleBottomMargin = subtitleBottomMargin,
                isLandscape = isLandscape,
                onActivePlayerViewChange = { activePlayerView = it }
            )
        }
        // 3-6. HUD Overlays, Buffering & Lock indicators
        PlayerHudOverlays(
            isWebViewMode = isWebViewMode,
            errorMessage = errorMessage,
            isPlaybackStartedEffective = isPlaybackStartedEffective,
            isLoading = isLoading,
            isPlayerBuffering = isPlayerBuffering,
            movieThumb = movieThumb,
            movieName = movieName,
            currentEpisodeName = currentEpisode.name,
            activeServerName = activeServerName,
            isLandscape = isLandscape,
            isLongPressing2x = isLongPressing2x,
            isTranslatingWithGemini = isTranslatingWithGemini,
            geminiTranslateProgress = geminiTranslateProgress,
            hudType = hudType,
            hudValue = hudValue,
            seekGestureDeltaMs = seekGestureDeltaMs,
            seekGestureTargetMs = seekGestureTargetMs,
            effectiveTotalDuration = effectiveTotalDuration,
            isLocked = isLocked,
            isPipMode = isPipMode,
            showControls = showControls,
            onUnlock = {
                isLocked = false
                showControls = true
                lastInteractionTime = System.currentTimeMillis()
                Toast.makeText(context, context.getString(R.string.player_unlock_controls), Toast.LENGTH_SHORT).show()
            }
        )
        // 7.1 Hàng nút điều khiển cố định ở đỉnh màn hình cho WebView Player Fallback (Luôn hiển thị, không chiếm trọn màn hình)
        if (isWebViewMode && !isPipMode) {
            val embedFallback = getEffectiveEmbedFallback()
            PlayerWebViewTopBar(
                movieName = movieName,
                episodeName = currentEpisode.name,
                onRotateScreen = {
                    val nextLandscape = !isLandscape
                    isLandscape = nextLandscape
                    settingsManager.setPlayerLandscape(nextLandscape)
                    activity?.requestedOrientation = if (nextLandscape) {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                    Toast.makeText(
                        context,
                        if (nextLandscape) context.getString(R.string.player_orientation_landscape) else context.getString(R.string.player_orientation_portrait),
                        Toast.LENGTH_SHORT
                    ).show()
                },
                hasMultipleEpisodes = activeEpisodes.size > 1,
                isEpisodesDrawerOpen = showEpisodesDrawer,
                onOpenEpisodesDrawer = {
                    showEpisodesDrawer = true
                    lastInteractionTime = System.currentTimeMillis()
                },
                isSourceDrawerOpen = showSourceSelectorDrawer,
                onOpenSourceDrawer = {
                    showSourceSelectorDrawer = true
                    lastInteractionTime = System.currentTimeMillis()
                },
                canSwitchToNative = !embedFallback.isNullOrEmpty() && resolvedStream != null && errorMessage == null,
                onSwitchToNative = {
                    isWebViewMode = false
                    activeWebView?.loadUrl("about:blank")
                    exoPlayerRetryCount = 0
                    resolvedStream?.let { exoPlayerManager.prepareStream(it) }
                },
                onBack = onBack
            )
        }
        // 7. Giao diện điều khiển Player Compose Controls cho Native Player (Top Bar, Center Controls, Bottom Bar)
        if (!isLocked && !isPipMode && !isWebViewMode) {
            val embedFallback = getEffectiveEmbedFallback()
            PlayerControlsOverlay(
                showControls = showControls,
                videoBackdrop = videoBackdrop,
                movieName = movieName,
                currentEpisode = currentEpisode,
                activeServerName = activeServerName,
                isLandscape = isLandscape,
                onRotateScreen = {
                    val nextLandscape = !isLandscape
                    isLandscape = nextLandscape
                    settingsManager.setPlayerLandscape(nextLandscape)
                    activity?.requestedOrientation = if (nextLandscape) {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                    Toast.makeText(
                        context,
                        if (nextLandscape) context.getString(R.string.player_orientation_landscape) else context.getString(R.string.player_orientation_portrait),
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onBack = onBack,
                onLockScreen = {
                    isLocked = true
                    lastInteractionTime = System.currentTimeMillis()
                    Toast.makeText(context, context.getString(R.string.player_lock_controls), Toast.LENGTH_SHORT).show()
                },
                activity = activity,
                onEnterPip = {
                    try {
                        showControls = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            activity?.enterPictureInPictureMode(
                                PictureInPictureParams.Builder().build()
                            )
                        }
                    } catch (e: Exception) {
                        println("[PlayerScreen] Lỗi vào PiP: ${e.message}")
                    }
                },
                activeEpisodes = activeEpisodes,
                currentEpisodeIndex = currentEpisodeIndex,
                onSelectEpisodeIndex = { currentEpisodeIndex = it },
                showEpisodesDrawer = showEpisodesDrawer,
                onOpenEpisodesDrawer = {
                    lastInteractionTime = System.currentTimeMillis()
                    showEpisodesDrawer = true
                },
                onOpenSettings = {
                    videoTracks = exoPlayerManager.getVideoTracks()
                    audioTracks = exoPlayerManager.getAudioTracks()
                    showSettingsDialog = true
                },
                showSourceSelectorDrawer = showSourceSelectorDrawer,
                onOpenSourceDrawer = {
                    lastInteractionTime = System.currentTimeMillis()
                    showSourceSelectorDrawer = true
                },
                embedFallback = embedFallback,
                onSwitchToWebView = {
                    exoPlayerManager.pause()
                    errorMessage = null
                    webViewUrl = embedFallback
                    webViewHeaders = embedHeadersFor(embedFallback ?: "", resolvedStream?.headers)
                    isWebViewMode = true
                },
                isPlaybackStartedEffective = isPlaybackStartedEffective,
                isLoading = isLoading,
                isPlayerBuffering = isPlayerBuffering,
                hudType = hudType,
                isDraggingGesture = isDraggingGesture,
                isPlaying = isPlaying,
                exoPlayerManager = exoPlayerManager,
                currentPosition = currentPosition,
                onCurrentPositionChange = { currentPosition = it },
                effectiveTotalDuration = effectiveTotalDuration,
                isDraggingSlider = isDraggingSlider,
                onDraggingSliderChange = { isDraggingSlider = it },
                sliderDragRatio = sliderDragRatio,
                onSliderDragRatioChange = { sliderDragRatio = it },
                isSeeking = isSeeking,
                onSeekingChange = { isSeeking = it },
                coroutineScope = coroutineScope,
                isMuted = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) <= 0,
                onMuteToggle = {
                    lastInteractionTime = System.currentTimeMillis()
                    val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    if (curVol > 0) {
                        savedMuteVolume = curVol
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                        showHud(HudIndicatorType.VOLUME, 0f)
                    } else {
                        val restore = if (savedMuteVolume > 0) savedMuteVolume else (maxVol / 2)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, restore, 0)
                        showHud(HudIndicatorType.VOLUME, restore.toFloat() / maxVol)
                    }
                },
                resizeModes = resizeModes,
                resizeModeIndex = resizeModeIndex,
                onResizeModeIndexChange = { resizeModeIndex = it },
                activePlayerView = activePlayerView,
                capsulePillFocusRequester = capsulePillFocusRequester,
                speeds = speeds,
                currentSpeed = currentSpeed,
                onSpeedChange = { currentSpeed = it },
                selectedAudioTrackId = selectedAudioTrackId,
                showAudioSheet = showAudioSheet,
                onOpenAudioSheet = {
                    showAudioSheet = true
                    lastInteractionTime = System.currentTimeMillis()
                },
                selectedSubtitleTrackId = selectedSubtitleTrackId,
                showSubtitleSheet = showSubtitleSheet,
                onOpenSubtitleSheet = {
                    showSubtitleSheet = true
                    lastInteractionTime = System.currentTimeMillis()
                },
                isAiDubbingEnabled = isTtsEnabled,
                onToggleAiDubbing = {
                    val newState = !isTtsEnabled
                    if (newState && currentSubCues.isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.player_subtitles_no_cues_to_sync), Toast.LENGTH_SHORT).show()
                    }
                    settingsManager.setTtsEnabled(newState)
                    val toast = if (newState) {
                        context.getString(R.string.player_tts_active_toast)
                    } else {
                        context.getString(R.string.player_tts_inactive_toast)
                    }
                    Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
                },
                onUserInteraction = {
                    lastInteractionTime = System.currentTimeMillis()
                }
            )
        }
        // 8. Panel Cài Đặt Trình Phát (Right Side Drawer Panel sang trọng)
        PlayerSettingsPanel(
            visible = showSettingsDialog,
            onDismiss = { showSettingsDialog = false },
            initialFocusRequester = settingsDialogInitialFocusRequester,
            currentSpeed = currentSpeed,
            speeds = speeds,
            onSpeedSelected = { speed ->
                currentSpeed = speed
                exoPlayerManager.setPlaybackSpeed(speed)
                Toast.makeText(context, context.getString(R.string.player_speed_toast, "${speed}x"), Toast.LENGTH_SHORT).show()
            },
            videoTracks = videoTracks,
            selectedVideoTrackId = selectedVideoTrackId,
            onVideoTrackSelected = { track ->
                selectedVideoTrackId = track?.id
                exoPlayerManager.setVideoTrack(track)
                showSettingsDialog = false
            },
            audioTracks = audioTracks,
            selectedAudioTrackId = selectedAudioTrackId,
            formatAudioSpecs = { opt -> exoPlayerManager.formatAudioSpecs(opt) },
            onAudioTrackSelected = { track ->
                selectedAudioTrackId = track.id
                exoPlayerManager.setAudioTrack(track)
                showSettingsDialog = false
            }
        )

        // 8.1. Panel Danh Sách Phụ Đề Nuvio 3 Cột Hiện Đại
        PlayerSubtitlePanel(
            visible = showSubtitleSheet,
            onDismiss = { showSubtitleSheet = false },
            isLandscape = isLandscape,
            initialFocusRequester = subtitleSheetInitialFocusRequester,
            configuredSubLanguages = settingsManager.subtitleSearchLanguages.value,
            selectedLangGroup = selectedLangGroup,
            onSelectLangGroup = { selectedLangGroup = it },
            onSelectNoneTrack = {
                selectedSubtitleTrackId = null
                activeOnlineSubId = null
                ttsManager.stopAudio()
                currentSubCues = emptyList()
                exoPlayerManager.setSubtitleTrack(null)
            },
            subtitleTracks = subtitleTracks,
            selectedSubtitleTrackId = selectedSubtitleTrackId,
            activeOnlineSubId = activeOnlineSubId,
            stremioAddonSubsMap = stremioAddonSubsMap,
            onlineSubsMap = onlineSubsMap,
            loadingSubsMap = loadingSubsMap,
            isStremioSubsLoading = isStremioSubsLoading,
            customSubSearchQuery = customSubSearchQuery,
            onSearchQuerySubmit = { q ->
                customSubSearchQuery = q
                executeOnlineSubSearch(selectedLangGroup, force = true)
            },
            onRefreshOnlineSubs = { lang, force ->
                executeOnlineSubSearch(lang, force)
            },
            onSelectStreamTrack = { opt ->
                selectedSubtitleTrackId = opt.id
                activeOnlineSubId = null
                ttsManager.stopAudio()
                currentSubCues = emptyList()
                exoPlayerManager.setSubtitleTrack(opt)
            },
            downloadingSubItemId = downloadingSubItemId,
            translatedEnglishSubIds = translatedEnglishSubIds,
            isTranslatingWithGemini = isTranslatingWithGemini,
            onApplyOnlineSub = { item ->
                applyOnlineSub(item)
            },
            onTranslateWithGemini = { item ->
                translateOnlineSubWithGemini(item)
            },
            currentSubCues = currentSubCues,
            currentSubOffsetMs = currentSubOffsetMs,
            onUpdateSubOffset = { offset ->
                updateSubOffset(offset)
            },
            isAiSyncing = isAiSyncing,
            onAiSync = {
                if (isAiSyncing) return@PlayerSubtitlePanel
                if (currentSubCues.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.player_subtitles_no_cues_to_sync), Toast.LENGTH_SHORT).show()
                    return@PlayerSubtitlePanel
                }
                coroutineScope.launch {
                    isAiSyncing = true
                    try {
                        val pos = exoPlayerManager.player.currentPosition
                        val currentEffective = pos - currentSubOffsetMs
                        val sortedCues = currentSubCues.sortedBy { kotlin.math.abs(it.startTimeMs - currentEffective) }.take(6)

                        var calculatedOffset: Long? = null
                        val apiKey = ttsApiKey.trim()

                        if (apiKey.isNotBlank()) {
                            withContext(Dispatchers.IO) {
                                try {
                                    val cueContext = sortedCues.joinToString("\n") { "[${it.startTimeMs}ms - ${it.endTimeMs}ms]: ${it.text.replace("\n", " ")}" }
                                    val promptText = "You are an expert subtitle timecode synchronizer. A video is currently at playback time " + pos + " ms. Surrounding subtitles:\n" + cueContext + "\nDetermine the exact offset adjustment (current_position - subtitle_start_time) for the subtitle matching " + pos + " ms, and respond ONLY with: OFFSET:<integer_in_ms> (e.g. OFFSET:1200 or OFFSET:-450)"
                                    val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey
                                    val reqJson = org.json.JSONObject().apply {
                                        put("contents", org.json.JSONArray().put(org.json.JSONObject().apply {
                                            put("parts", org.json.JSONArray().put(org.json.JSONObject().apply {
                                                put("text", promptText)
                                            }))
                                        }))
                                    }
                                    val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                                        requestMethod = "POST"
                                        setRequestProperty("Content-Type", "application/json")
                                        connectTimeout = 4000
                                        readTimeout = 4000
                                        doOutput = true
                                    }
                                    conn.outputStream.use { it.write(reqJson.toString().toByteArray()) }
                                    if (conn.responseCode in 200..299) {
                                        val resp = conn.inputStream.bufferedReader().use { it.readText() }
                                        val jsonResp = org.json.JSONObject(resp)
                                        val text = jsonResp.optJSONArray("candidates")?.optJSONObject(0)
                                            ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text") ?: ""
                                        val match = Regex("OFFSET:\\s*([+-]?\\d+)").find(text)
                                        if (match != null) {
                                            calculatedOffset = match.groupValues[1].toLongOrNull()
                                        }
                                    }
                                } catch (e: Exception) {
                                    println("[AI Sub Sync] Gemini API fallback: ${e.message}")
                                }
                            }
                        }

                        val finalOffset = calculatedOffset ?: run {
                            val nearestCue = sortedCues.firstOrNull() ?: currentSubCues.minByOrNull { kotlin.math.abs(it.startTimeMs - currentEffective) }
                            if (nearestCue != null) {
                                pos - nearestCue.startTimeMs
                            } else 0L
                        }

                        updateSubOffset(finalOffset)
                        val offsetStr = "${if (finalOffset >= 0) "+" else ""}${String.format(java.util.Locale.US, "%.3f", finalOffset / 1000f)}s"
                        Toast.makeText(
                            context,
                            context.getString(R.string.player_subtitles_ai_sync_success, offsetStr),
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "AI Sync: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isAiSyncing = false
                    }
                }
            },
            isTtsEnabled = isTtsEnabled,
            ttsVoice = ttsVoice,
            ttsSpeechRate = ttsSpeechRate,
            ttsDuckingVolume = ttsDuckingVolume,
            ttsApiKey = ttsApiKey,
            subtitleTextSize = subtitleTextSize,
            subtitleIsBold = subtitleIsBold,
            subtitleTextColor = subtitleTextColor,
            subtitleTextOpacity = subtitleTextOpacity,
            subtitleIsOutline = subtitleIsOutline,
            subtitleOutlineColor = subtitleOutlineColor,
            subtitleBottomMargin = subtitleBottomMargin,
            settingsManager = settingsManager
        )

        // 8.2. Panel Chọn Track Âm Thanh
        PlayerAudioPanel(
            visible = showAudioSheet,
            onDismiss = { showAudioSheet = false },
            isLandscape = isLandscape,
            initialFocusRequester = audioSheetInitialFocusRequester,
            audioTracks = audioTracks,
            selectedAudioTrackId = selectedAudioTrackId,
            formatAudioSpecs = { opt -> exoPlayerManager.formatAudioSpecs(opt) },
            onAudioTrackSelected = { track ->
                selectedAudioTrackId = track.id
                exoPlayerManager.setAudioTrack(track)
                showAudioSheet = false
            }
        )

        // 8.3. Multi-source Server Selection Panel
        PlayerSourcePanel(
            visible = showSourceSelectorDrawer,
            onDismiss = { showSourceSelectorDrawer = false },
            initialFocusRequester = sourceSelectorInitialFocusRequester,
            movieName = movieName,
            movieTmdbId = movieTmdbId,
            drawerStreams = drawerStreams,
            activeUnifiedStream = activeUnifiedStream,
            activeProviderId = activeProviderId,
            activeServerName = activeServerName,
            resolvedStream = resolvedStream,
            currentEpisode = currentEpisode,
            getPluginName = { id -> pluginManager.getPluginName(id) },
            isScanningSources = isScanningSources,
            sourceRotationAngle = sourceRotationAngle,
            onRefreshSources = {
                refreshSourcesKey++
            },
            onSelectStream = { item ->
                switchToUnifiedStream(item)
                showSourceSelectorDrawer = false
            },
            onCopyStreamUrl = onCopyStreamUrl
        )

        // 8.4. Panel Danh Sách Tập
        PlayerEpisodesPanel(
            visible = showEpisodesDrawer,
            onDismiss = { showEpisodesDrawer = false },
            initialFocusRequester = episodesDrawerInitialFocusRequester,
            movieName = movieName,
            activeEpisodes = activeEpisodes,
            currentEpisodeIndex = currentEpisodeIndex,
            availableServers = availableServers,
            activeServerName = activeServerName,
            onServerSelect = { srv ->
                activeServerName = srv.serverName
                activeEpisodes = srv.episodes
                val matchedIdx = srv.episodes.indexOfFirst { it.number == currentEpisode.number }.takeIf { it >= 0 } ?: 0
                currentEpisodeIndex = matchedIdx
                ActivePlayerSession.currentServerName = srv.serverName
                ActivePlayerSession.episodes = srv.episodes
                ActivePlayerSession.currentEpisodeIndex = matchedIdx
                ActivePlayerSession.currentEpisode = srv.episodes.getOrNull(matchedIdx) ?: currentEpisode
            },
            onEpisodeSelect = { idx, ep ->
                showResumePromptDialog = false
                resumeTargetPositionMs = 0L
                currentEpisodeIndex = idx
                ActivePlayerSession.currentEpisodeIndex = idx
                ActivePlayerSession.currentEpisode = ep
                showEpisodesDrawer = false
            }
        )

        // 8.5. Dialog xác nhận Resume
        PlayerResumeDialog(
            visible = showResumePromptDialog,
            resumeTargetPositionMs = resumeTargetPositionMs,
            isLandscape = isLandscape,
            onResume = {
                showResumePromptDialog = false
                currentPosition = resumeTargetPositionMs
                exoPlayerManager.seekTo(resumeTargetPositionMs)
                Toast.makeText(context, context.getString(R.string.player_resume_toast, formatTime(resumeTargetPositionMs)), Toast.LENGTH_SHORT).show()
            },
            onRestart = {
                showResumePromptDialog = false
                resumeTargetPositionMs = 0L
                currentPosition = 0L
                exoPlayerManager.seekTo(0L)
            },
            onDismiss = {
                showResumePromptDialog = false
            }
        )
        // 9. Thông báo lỗi nếu có
        if (errorMessage != null) {
            val embedFallback = getEffectiveEmbedFallback()
            PlayerErrorOverlay(
                errorMessage = errorMessage!!,
                embedFallback = embedFallback,
                onSwitchToWebView = {
                    errorMessage = null
                    webViewUrl = embedFallback
                    webViewHeaders = embedHeadersFor(embedFallback ?: "", resolvedStream?.headers)
                    isWebViewMode = true
                },
                onRetry = {
                    errorMessage = null
                    reloadStreamTrigger++
                },
                onBack = onBack,
                videoBackdrop = videoBackdrop
            )
        }
    }
}
