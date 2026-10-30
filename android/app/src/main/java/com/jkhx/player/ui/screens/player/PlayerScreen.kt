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

data class AvailablePluginSource(
    val pluginId: String,
    val pluginName: String,
    val movieSlug: String,
    val movieTitle: String,
    val servers: List<com.jkhx.player.core.model.EpisodeServer>,
    val isMatchedByTmdb: Boolean,
    val isCurrent: Boolean
)

private const val DEFAULT_WEB_UA =
    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

private fun embedHeadersFor(url: String, customHeaders: Map<String, String>? = null): Map<String, String> {
    val origin = try {
        val u = Uri.parse(url)
        if (u.scheme != null && u.host != null) "${u.scheme}://${u.host}/" else null
    } catch (e: Exception) {
        null
    }
    return buildMap {
        put("User-Agent", DEFAULT_WEB_UA)
        origin?.let { put("Referer", it) }
        customHeaders?.forEach { (k, v) ->
            when {
                k.equals("User-Agent", ignoreCase = true) -> put("User-Agent", v)
                k.equals("Referer", ignoreCase = true) -> put("Referer", v)
                k.equals("Origin", ignoreCase = true) -> put("Origin", v)
                else -> put(k, v)
            }
        }
    }
}

private fun buildHtml5HlsPlayerPage(streamUrl: String, referer: String): String {
    val escapedUrl = streamUrl.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"")
    val escapedRef = referer.replace("\\", "\\\\").replace("'", "\\'")
    return """
        <!DOCTYPE html>
        <html lang="vi">
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
            <style>
                * { margin: 0 !important; padding: 0 !important; box-sizing: border-box !important; }
                html, body {
                    width: 100vw !important;
                    height: 100vh !important;
                    background: #000 !important;
                    overflow: hidden !important;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                }
                video {
                    width: 100vw !important;
                    height: 100vh !important;
                    max-width: 100vw !important;
                    max-height: 100vh !important;
                    object-fit: contain !important;
                    background: #000 !important;
                    outline: none !important;
                    border: none !important;
                }
            </style>
            <script>
                try {
                    if ('$escapedRef') {
                        Object.defineProperty(document, 'referrer', {
                            get: function() { return '$escapedRef'; },
                            configurable: true
                        });
                    }
                } catch (e) {}
            </script>
            <script src="https://cdn.jsdelivr.net/npm/hls.js@1.5.15/dist/hls.min.js"></script>
        </head>
        <body>
            <video id="video" controls autoplay playsinline preload="auto"></video>
            <script>
                (function() {
                    var video = document.getElementById('video');
                    var src = '$escapedUrl';
                    var lowerSrc = src.toLowerCase();
                    var isDirectContainer = lowerSrc.indexOf('.mp4') !== -1 || lowerSrc.indexOf('.webm') !== -1 || lowerSrc.indexOf('.mkv') !== -1;
                    if (!isDirectContainer && window.Hls && Hls.isSupported()) {
                        var hls = new Hls({
                            enableWorker: true,
                            lowLatencyMode: false,
                            backBufferLength: 60,
                            maxBufferLength: 60,
                            fragLoadingMaxRetry: 4,
                            manifestLoadingMaxRetry: 4
                        });
                        hls.loadSource(src);
                        hls.attachMedia(video);
                        hls.on(Hls.Events.MANIFEST_PARSED, function() {
                            var p = video.play();
                            if (p && p.catch) {
                                p.catch(function() {
                                    video.muted = true;
                                    video.play();
                                });
                            }
                        });
                        hls.on(Hls.Events.ERROR, function(event, data) {
                            if (data.fatal) {
                                switch (data.type) {
                                    case Hls.ErrorTypes.NETWORK_ERROR:
                                        hls.startLoad();
                                        break;
                                    case Hls.ErrorTypes.MEDIA_ERROR:
                                        hls.recoverMediaError();
                                        break;
                                    default:
                                        video.src = src;
                                        video.play();
                                        break;
                                }
                            }
                        });
                    } else {
                        video.src = src;
                        video.addEventListener('loadedmetadata', function() {
                            var p = video.play();
                            if (p && p.catch) {
                                p.catch(function() {
                                    video.muted = true;
                                    video.play();
                                });
                            }
                        });
                    }
                })();
            </script>
        </body>
        </html>
    """.trimIndent()
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun parseDurationStringToMs(durationStr: String?): Long {
    if (durationStr.isNullOrBlank()) return 0L
    try {
        val hMatch = Regex("""(\d+)\s*(?:h|giờ)""", RegexOption.IGNORE_CASE).find(durationStr)
        val mMatch = Regex("""(\d+)\s*(?:m|phút|min)""", RegexOption.IGNORE_CASE).find(durationStr)
        if (hMatch != null || mMatch != null) {
            val hours = hMatch?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
            val minutes = mMatch?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
            val totalMs = (hours * 3600 + minutes * 60) * 1000L
            if (totalMs > 0) return totalMs
        }
        val pureNum = Regex("""^(\d+)$""").find(durationStr.trim())?.groupValues?.getOrNull(1)?.toLongOrNull()
        if (pureNum != null && pureNum > 0) {
            return pureNum * 60 * 1000L
        }
        val parts = durationStr.trim().split(":")
        if (parts.size == 3) {
            val h = parts[0].toLongOrNull() ?: 0L
            val m = parts[1].toLongOrNull() ?: 0L
            val s = parts[2].toLongOrNull() ?: 0L
            return (h * 3600 + m * 60 + s) * 1000L
        } else if (parts.size == 2) {
            val m = parts[0].toLongOrNull() ?: 0L
            val s = parts[1].toLongOrNull() ?: 0L
            return (m * 60 + s) * 1000L
        }
    } catch (_: Exception) {}
    return 0L
}

private fun applySubtitleAppearance(
    subtitleView: SubtitleView?,
    textSizeSp: Float,
    isBold: Boolean,
    textColor: Int,
    textOpacityPercent: Int,
    isOutlineEnabled: Boolean,
    outlineColor: Int,
    bottomMarginPercent: Int,
    isLandscape: Boolean = true
) {
    if (subtitleView == null) return
    subtitleView.setApplyEmbeddedFontSizes(false)
    subtitleView.setApplyEmbeddedStyles(false)

    // Ở màn hình dọc (portrait), khung video co nhỏ chỉ chiếm phần trên màn hình (~220dp chiều cao),
    // tự động scale kích thước (~0.70f) để chữ không bị to quá cỡ so với hình ảnh video
    val effectiveTextSize = if (isLandscape) textSizeSp else (textSizeSp * 0.70f).coerceAtLeast(10f)
    subtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, effectiveTextSize)

    // Khoảng cách lề dưới: càng tăng giá trị thì phụ đề càng được đẩy lên trên xa đáy
    val bottomFraction = (bottomMarginPercent.toFloat() / 100f).coerceIn(0.02f, 0.95f)
    subtitleView.setBottomPaddingFraction(bottomFraction)

    val alpha = ((textOpacityPercent.coerceIn(10, 100) / 100f) * 255).toInt()
    val finalTextColor = android.graphics.Color.argb(
        alpha,
        android.graphics.Color.red(textColor),
        android.graphics.Color.green(textColor),
        android.graphics.Color.blue(textColor)
    )

    val edgeType = if (isOutlineEnabled) CaptionStyleCompat.EDGE_TYPE_OUTLINE else CaptionStyleCompat.EDGE_TYPE_NONE
    val typeface = if (isBold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT

    val style = CaptionStyleCompat(
        finalTextColor,
        android.graphics.Color.TRANSPARENT,
        android.graphics.Color.TRANSPARENT,
        edgeType,
        outlineColor,
        typeface
    )
    subtitleView.setStyle(style)
}

private fun isVietnameseTrack(track: SubtitleTrackOption): Boolean {
    val lang = track.language?.lowercase()?.trim()
    if (lang == "vi" || lang == "vie" || lang == "vietnamese") return true
    val label = track.label.lowercase()
    return label.contains("tiếng việt") ||
           label.contains("vietnamese") ||
           label.contains("việt") ||
           label.contains("(vi)") ||
           label.contains("[vi]") ||
           label.contains(".vi.") ||
           label.endsWith(".vi") ||
           label.contains("_vi_")
}

private fun isEnglishTrack(track: SubtitleTrackOption): Boolean {
    val lang = track.language?.lowercase()?.trim()
    if (lang == "en" || lang == "eng" || lang == "english") return true
    val label = track.label.lowercase()
    if (isVietnameseTrack(track)) return false
    return label.contains("tiếng anh") ||
           label.contains("english") ||
           label.contains("(en)") ||
           label.contains("[en]") ||
           label.contains(".en.") ||
           label.endsWith(".en") ||
           label.contains("_en_")
}

private fun mergeSubtitleTracks(
    customTracks: List<SubtitleTrackOption>,
    exoTracks: List<SubtitleTrackOption>
): List<SubtitleTrackOption> {
    val nonOnlineCustom = customTracks.filterNot { it.id.startsWith("online_") }
    val filteredExo = exoTracks.filterNot { s ->
        nonOnlineCustom.any { c ->
            c.id == s.id || (
                !c.id.startsWith("cached_gemini_") &&
                !c.id.startsWith("custom_gemini_") &&
                c.label.trim().equals(s.label.trim(), ignoreCase = true) &&
                (
                    c.language?.trim()?.lowercase() == s.language?.trim()?.lowercase() ||
                    (isVietnameseTrack(c) && isVietnameseTrack(s)) ||
                    (isEnglishTrack(c) && isEnglishTrack(s))
                )
            )
        }
    }
    return nonOnlineCustom + filteredExo
}

enum class HudIndicatorType { NONE, VOLUME, BRIGHTNESS, SEEK_FORWARD, SEEK_REWIND, SEEK_GESTURE }

@Composable
private fun RepeatingIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    initialDelayMs: Long = 350L,
    repeatIntervalMs: Long = 80L,
    content: @Composable () -> Unit
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val coroutineScope = rememberCoroutineScope()
    Box(
        modifier = modifier
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                     keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
                     keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                    currentOnClick()
                    true
                } else false
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    currentOnClick()
                    val job = coroutineScope.launch {
                        delay(initialDelayMs)
                        while (isActive) {
                            currentOnClick()
                            delay(repeatIntervalMs)
                        }
                    }
                    waitForUpOrCancellation()
                    job.cancel()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

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
            key(webViewUrl) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            activeWebView = this
                            setBackgroundColor(android.graphics.Color.BLACK)
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                loadWithOverviewMode = false
                                useWideViewPort = false
                                allowContentAccess = true
                                allowFileAccess = true
                                setSupportMultipleWindows(false)
                                javaScriptCanOpenWindowsAutomatically = false
                                userAgentString = webViewHeaders["User-Agent"] ?: DEFAULT_WEB_UA
                            }

                            val cookieManager = android.webkit.CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            val refererForPage = webViewHeaders["Referer"]
                                ?: embedHeadersFor(webViewUrl ?: "")["Referer"]
                                ?: ""

                            val referrerJs = """
                                (function() {
                                    try {
                                        if ('$refererForPage') {
                                            Object.defineProperty(document, 'referrer', {
                                                get: function() { return '$refererForPage'; },
                                                configurable: true
                                            });
                                        }
                                    } catch(e) {}
                                    try {
                                        window.open = function() { return null; };
                                    } catch(e) {}
                                    try {
                                        var style = document.createElement('style');
                                        style.textContent = '* { box-sizing: border-box !important; } html, body { overflow: hidden !important; margin: 0 !important; padding: 0 !important; width: 100% !important; height: 100% !important; background: #000 !important; } .player, #hrm-player, .jwplayer, #player, #v-player-wrapper { width: 100% !important; height: 100% !important; margin: 0 !important; padding: 0 !important; } video, iframe { width: 100% !important; height: 100% !important; object-fit: contain !important; border: none !important; margin: 0 !important; padding: 0 !important; }';
                                        (document.head || document.documentElement).appendChild(style);
                                    } catch(e) {}
                                })();
                            """.trimIndent()

                            val currentUrl = webViewUrl ?: ""
                            val currentUrlHost = try { Uri.parse(currentUrl).host?.lowercase() } catch (_: Exception) { null }
                            val inferredStreamType = StremioPluginWrapper.inferStreamType(
                                streamUrl = currentUrl,
                                rawType = if (currentUrl == resolvedStream?.url) resolvedStream?.type else null,
                                metaText = ""
                            )
                            val isDirectMediaStream = !currentUrl.contains("/embed", ignoreCase = true) &&
                                !currentUrl.contains("player.php", ignoreCase = true) &&
                                (inferredStreamType in setOf("m3u8", "hls", "mp4", "mkv", "webm", "mov", "avi") ||
                                    (currentUrl == resolvedStream?.url && resolvedStream?.type != "embed"))

                            val embedHttpClient = OkHttpClient.Builder()
                                .dns(object : okhttp3.Dns {
                                    override fun lookup(hostname: String): List<java.net.InetAddress> =
                                        okhttp3.Dns.SYSTEM.lookup(hostname).sortedBy { it is java.net.Inet6Address }
                                })
                                .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                                .followRedirects(true)
                                .followSslRedirects(true)
                                .build()

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val targetUrl = request?.url?.toString() ?: return false
                                    // Chặn toàn bộ scheme không phải HTTP/HTTPS (intent, market, tel...)
                                    if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                                        return true
                                    }

                                    // Chỉ chặn redirect của main frame nếu cố tình nhảy sang web quảng cáo / cá độ khác
                                    if (request?.isForMainFrame == true) {
                                        val currentUri = Uri.parse(webViewUrl ?: "")
                                        val targetUri = request.url
                                        val currentHost = currentUri.host?.lowercase()
                                        val targetHost = targetUri?.host?.lowercase()
                                        val refererHost = try {
                                             if (refererForPage.isNotBlank()) Uri.parse(refererForPage).host?.lowercase() else null
                                        } catch (_: Exception) { null }

                                        if (currentHost != null && targetHost != null) {
                                            val isAllowed = targetHost == currentHost ||
                                                    targetHost.endsWith(".$currentHost") ||
                                                    currentHost.endsWith(".$targetHost") ||
                                                    (refererHost != null && (targetHost == refererHost || targetHost.endsWith(".$refererHost") || refererHost.endsWith(".$targetHost")))

                                            if (!isAllowed) {
                                                println("[PlayerScreen WebView] Chặn redirect quảng cáo ngoại vi: $targetUrl")
                                                return true
                                            }
                                        }
                                    }

                                    return false
                                }

                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val reqUrl = request?.url?.toString() ?: return null

                                    // 0. HLS / DIRECT MEDIA STREAM PROXY INTERCEPTION:
                                    // Khi WebView đang đóng vai trò trình phát HTML5 + hls.js cho luồng HLS/Direct video,
                                    // tự động đính kèm User-Agent, Referer, Origin từ webViewHeaders và cấp header CORS
                                    val reqHost = request.url?.host?.lowercase()
                                    val isMediaSubRequest = isDirectMediaStream && !request.isForMainFrame && (
                                        reqUrl == currentUrl ||
                                        (currentUrlHost != null && reqHost != null && (reqHost == currentUrlHost || reqHost.endsWith(".$currentUrlHost"))) ||
                                        reqUrl.contains(".m3u8", ignoreCase = true) ||
                                        reqUrl.contains(".ts", ignoreCase = true) ||
                                        reqUrl.contains(".m4s", ignoreCase = true) ||
                                        reqUrl.contains("/m/") ||
                                        reqUrl.contains("/s/") ||
                                        reqUrl.contains("/hls")
                                    ) && !reqUrl.endsWith(".js", ignoreCase = true) && !reqUrl.endsWith(".css", ignoreCase = true)

                                    if (isMediaSubRequest) {
                                        if (request.method.equals("OPTIONS", ignoreCase = true)) {
                                            val corsHeaders = mapOf(
                                                "Access-Control-Allow-Origin" to "*",
                                                "Access-Control-Allow-Methods" to "GET, HEAD, OPTIONS",
                                                "Access-Control-Allow-Headers" to "*",
                                                "Access-Control-Max-Age" to "86400"
                                            )
                                            return WebResourceResponse(
                                                "text/plain",
                                                "utf-8",
                                                200,
                                                "OK",
                                                corsHeaders,
                                                ByteArrayInputStream(ByteArray(0))
                                            )
                                        }

                                        try {
                                            val reqBuilder = Request.Builder()
                                                .url(reqUrl)
                                                .header("User-Agent", webViewHeaders["User-Agent"] ?: DEFAULT_WEB_UA)

                                            if (refererForPage.isNotBlank()) {
                                                reqBuilder.header("Referer", refererForPage)
                                                val originHeader = webViewHeaders["Origin"] ?: try {
                                                    Uri.parse(refererForPage).let { "${it.scheme}://${it.host}" }
                                                } catch (_: Exception) { refererForPage }
                                                reqBuilder.header("Origin", originHeader)
                                            }

                                            request.requestHeaders["Range"]?.let { rangeVal ->
                                                reqBuilder.header("Range", rangeVal)
                                            }

                                            val mediaRes = embedHttpClient.newCall(reqBuilder.build()).execute()
                                            val rawContentType = mediaRes.header("Content-Type") ?: "application/octet-stream"
                                            val mimeType = rawContentType.substringBefore(";").trim().ifBlank { "application/octet-stream" }
                                            val charset = rawContentType.substringAfter("charset=", "").substringBefore(";").trim().ifBlank { null }

                                            val respHeaders = mutableMapOf<String, String>()
                                            for (i in 0 until mediaRes.headers.size) {
                                                val hName = mediaRes.headers.name(i)
                                                if (!hName.equals("content-security-policy", ignoreCase = true) &&
                                                    !hName.equals("x-frame-options", ignoreCase = true) &&
                                                    !hName.equals("access-control-allow-origin", ignoreCase = true)
                                                ) {
                                                    respHeaders[hName] = mediaRes.headers.value(i)
                                                }
                                            }
                                            respHeaders["Access-Control-Allow-Origin"] = "*"
                                            respHeaders["Access-Control-Expose-Headers"] = "*"

                                            return WebResourceResponse(
                                                mimeType,
                                                charset,
                                                mediaRes.code,
                                                mediaRes.message.ifBlank { "OK" },
                                                respHeaders,
                                                mediaRes.body?.byteStream()
                                            )
                                        } catch (e: Exception) {
                                            println("[PlayerScreen WebView] Media proxy intercept error: ${e.message}")
                                        }
                                    }

                                    // 1. MAIN FRAME INTERCEPTION: Nạp và chuẩn hóa HTML5 Viewport chuẩn cho trang Embed
                                    if (request.isForMainFrame && !isDirectMediaStream && refererForPage.isNotBlank() &&
                                        (reqUrl == (webViewUrl ?: "") || reqUrl.contains("/embed") || reqUrl.contains("player.php"))
                                    ) {
                                        try {
                                            val okReq = Request.Builder()
                                                .url(reqUrl)
                                                .header("Referer", refererForPage)
                                                .header("Origin", try { Uri.parse(refererForPage).let { "${it.scheme}://${it.host}" } } catch (_: Exception) { refererForPage })
                                                .header("User-Agent", webViewHeaders["User-Agent"] ?: DEFAULT_WEB_UA)
                                                .build()

                                            val okRes = embedHttpClient.newCall(okReq).execute()
                                            if (okRes.isSuccessful) {
                                                val rawHtml = okRes.body?.string() ?: ""
                                                val resContentType = okRes.header("Content-Type")?.lowercase() ?: ""
                                                val isRawM3u8 = rawHtml.trimStart().startsWith("#EXTM3U") ||
                                                    resContentType.contains("mpegurl") ||
                                                    resContentType.contains("application/x-mpegurl")
                                                val isFullDoc = rawHtml.contains("<html", ignoreCase = true) || rawHtml.contains("<!DOCTYPE", ignoreCase = true)

                                                val modifiedHtml = if (isRawM3u8) {
                                                    buildHtml5HlsPlayerPage(reqUrl, refererForPage)
                                                } else if (isFullDoc) {
                                                    val injectHeader = """
                                                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
                                                        <style>
                                                            * { margin: 0 !important; padding: 0 !important; box-sizing: border-box !important; }
                                                            html, body { width: 100% !important; height: 100% !important; margin: 0 !important; padding: 0 !important; overflow: hidden !important; background: #000 !important; }
                                                            #v-player-wrapper, .player, #hrm-player, .jwplayer, #player { width: 100% !important; height: 100% !important; margin: 0 !important; padding: 0 !important; }
                                                            video, iframe { width: 100% !important; height: 100% !important; object-fit: contain !important; border: none !important; margin: 0 !important; padding: 0 !important; }
                                                        </style>
                                                        <script>
                                                            try { Object.defineProperty(document, 'referrer', { get: function() { return '$refererForPage'; }, configurable: true }); } catch(e) {}
                                                            try { window.open = function() { return null; }; } catch(e) {}
                                                        </script>
                                                    """.trimIndent()

                                                    if (rawHtml.contains("<head>", ignoreCase = true)) {
                                                        rawHtml.replaceFirst("<head>", "<head>$injectHeader", ignoreCase = true)
                                                    } else {
                                                        rawHtml.replaceFirst("<html>", "<html><head>$injectHeader</head>", ignoreCase = true)
                                                    }
                                                } else {
                                                    // Trường hợp player.php chỉ trả về raw <iframe>: bọc thành tài liệu HTML5 chuẩn
                                                    """
                                                    <!DOCTYPE html>
                                                    <html lang="vi">
                                                    <head>
                                                        <meta charset="utf-8">
                                                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
                                                        <style>
                                                            * { margin: 0 !important; padding: 0 !important; box-sizing: border-box !important; }
                                                            html, body { width: 100vw !important; height: 100vh !important; margin: 0 !important; padding: 0 !important; overflow: hidden !important; background: #000 !important; }
                                                            #v-player-wrapper { position: fixed !important; inset: 0 !important; width: 100vw !important; height: 100vh !important; background: #000 !important; }
                                                            iframe { position: absolute !important; top: 0 !important; left: 0 !important; width: 100% !important; height: 100% !important; border: none !important; margin: 0 !important; padding: 0 !important; }
                                                        </style>
                                                        <script>
                                                            try { Object.defineProperty(document, 'referrer', { get: function() { return '$refererForPage'; }, configurable: true }); } catch(e) {}
                                                            try { window.open = function() { return null; }; } catch(e) {}
                                                        </script>
                                                    </head>
                                                    <body style="margin: 0; padding: 0; background: #000; overflow: hidden;">
                                                        <div id="v-player-wrapper">
                                                            $rawHtml
                                                        </div>
                                                    </body>
                                                    </html>
                                                    """.trimIndent()
                                                }

                                                val respHeaders = mutableMapOf<String, String>()
                                                for (i in 0 until okRes.headers.size) {
                                                    val hName = okRes.headers.name(i)
                                                    if (!hName.equals("content-security-policy", ignoreCase = true) &&
                                                        !hName.equals("x-frame-options", ignoreCase = true)) {
                                                        respHeaders[hName] = okRes.headers.value(i)
                                                    }
                                                }

                                                return WebResourceResponse(
                                                    "text/html",
                                                    "utf-8",
                                                    okRes.code,
                                                    okRes.message.ifBlank { "OK" },
                                                    respHeaders,
                                                    ByteArrayInputStream(modifiedHtml.toByteArray(Charsets.UTF_8))
                                                )
                                            }
                                        } catch (e: Exception) {
                                            println("[PlayerScreen] shouldInterceptRequest main frame error: ${e.message}")
                                        }
                                    }

                                    // 2. SUBFRAME INTERCEPTION (Iframe Player con như streamfree.vip):
                                    // Bóc tách và tiêm CSS Lớp 2 để triệt tiêu padding-bottom 56.25% gây tràn & lẹm đáy video
                                    val isSubframeCandidate = !request.isForMainFrame &&
                                            (reqUrl.contains("streamfree.vip") || reqUrl.contains("/embed/") || reqUrl.contains("player")) &&
                                            (request.requestHeaders["Accept"]?.contains("text/html") == true || reqUrl.contains("/embed/v/"))

                                    if (isSubframeCandidate) {
                                        try {
                                            val subReferer = webViewUrl ?: refererForPage
                                            val subReq = Request.Builder()
                                                .url(reqUrl)
                                                .header("Referer", subReferer)
                                                .header("User-Agent", webViewHeaders["User-Agent"] ?: DEFAULT_WEB_UA)
                                                .build()

                                            val subRes = embedHttpClient.newCall(subReq).execute()
                                            if (subRes.isSuccessful) {
                                                val subHtml = subRes.body?.string() ?: ""
                                                val subInject = """
                                                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
                                                    <style>
                                                        * { box-sizing: border-box !important; }
                                                        html, body { width: 100% !important; height: 100% !important; margin: 0 !important; padding: 0 !important; overflow: hidden !important; background: #000 !important; }
                                                        .player, #hrm-player, .jwplayer, #player, #v-player-wrapper { width: 100% !important; height: 100% !important; margin: 0 !important; padding: 0 !important; }
                                                        video, iframe { width: 100% !important; height: 100% !important; object-fit: contain !important; border: none !important; margin: 0 !important; padding: 0 !important; }
                                                    </style>
                                                """.trimIndent()

                                                val modifiedSubHtml = if (subHtml.contains("<head>", ignoreCase = true)) {
                                                    subHtml.replaceFirst("<head>", "<head>$subInject", ignoreCase = true)
                                                } else if (subHtml.contains("<html>", ignoreCase = true)) {
                                                    subHtml.replaceFirst("<html>", "<html><head>$subInject</head>", ignoreCase = true)
                                                } else {
                                                    subInject + subHtml
                                                }

                                                val respHeaders = mutableMapOf<String, String>()
                                                for (i in 0 until subRes.headers.size) {
                                                    val hName = subRes.headers.name(i)
                                                    if (!hName.equals("content-security-policy", ignoreCase = true) &&
                                                        !hName.equals("x-frame-options", ignoreCase = true)) {
                                                        respHeaders[hName] = subRes.headers.value(i)
                                                    }
                                                }

                                                return WebResourceResponse(
                                                    "text/html",
                                                    "utf-8",
                                                    subRes.code,
                                                    subRes.message.ifBlank { "OK" },
                                                    respHeaders,
                                                    ByteArrayInputStream(modifiedSubHtml.toByteArray(Charsets.UTF_8))
                                                )
                                            }
                                        } catch (e: Exception) {
                                            println("[PlayerScreen] shouldInterceptRequest subframe error: ${e.message}")
                                        }
                                    }

                                    return super.shouldInterceptRequest(view, request)
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    view?.evaluateJavascript(referrerJs, null)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    view?.evaluateJavascript(referrerJs, null)
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                // Chặn 100% lệnh mở cửa sổ mới từ mã quảng cáo (window.open, target="_blank")
                                override fun onCreateWindow(
                                    view: WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: android.os.Message?
                                ): Boolean {
                                    return false
                                }

                                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                    if (view == null) return
                                    if (webCustomView != null) {
                                        callback?.onCustomViewHidden()
                                        return
                                    }
                                    val decor = activity?.window?.decorView as? ViewGroup ?: return
                                    view.setBackgroundColor(android.graphics.Color.BLACK)
                                    decor.addView(
                                        view,
                                        FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    )
                                    webCustomView = view
                                    webCustomViewCallback = callback
                                }

                                override fun onHideCustomView() {
                                    exitWebCustomView()
                                }
                            }

                            val referer = webViewHeaders["Referer"]
                                ?: embedHeadersFor(currentUrl)["Referer"]

                            val finalHeaders = webViewHeaders.toMutableMap()
                            if (!finalHeaders.containsKey("Referer") && referer != null) {
                                finalHeaders["Referer"] = referer
                            }
                            if (!finalHeaders.containsKey("User-Agent")) {
                                finalHeaders["User-Agent"] = DEFAULT_WEB_UA
                            }

                            tag = currentUrl
                            if (isDirectMediaStream) {
                                val baseOrigin = try {
                                    if (refererForPage.isNotBlank()) {
                                        val u = Uri.parse(refererForPage)
                                        if (u.scheme != null && u.host != null) "${u.scheme}://${u.host}/" else refererForPage
                                    } else {
                                        val u = Uri.parse(currentUrl)
                                        if (u.scheme != null && u.host != null) "${u.scheme}://${u.host}/" else "https://localhost/"
                                    }
                                } catch (_: Exception) {
                                    "https://localhost/"
                                }
                                loadDataWithBaseURL(
                                    baseOrigin,
                                    buildHtml5HlsPlayerPage(currentUrl, refererForPage),
                                    "text/html",
                                    "utf-8",
                                    null
                                )
                            } else {
                                loadUrl(currentUrl, finalHeaders)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        // 2. Chế độ phát ExoPlayer Native (.m3u8, .mp4, DASH)
        else if (resolvedStream != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        activePlayerView = this
                        player = exoPlayerManager.player
                        keepScreenOn = true
                        useController = false // Dùng toàn bộ custom Jetpack Compose Player Controls
                        resizeMode = resizeModes[resizeModeIndex].first
                        subtitleView?.apply {
                            applySubtitleAppearance(
                                this,
                                subtitleTextSize,
                                subtitleIsBold,
                                subtitleTextColor,
                                subtitleTextOpacity,
                                subtitleIsOutline,
                                subtitleOutlineColor,
                                subtitleBottomMargin,
                                isLandscape
                            )
                        }
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { pv ->
                    pv.resizeMode = resizeModes[resizeModeIndex].first
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 3. Nuvio-style Buffering / Loading Overlay Backdrop
        if (!isWebViewMode && errorMessage == null && !isPlaybackStartedEffective) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // Dimmed backdrop image if available (Hiển thị poster sắc nét, điện ảnh)
                if (!movieThumb.isNullOrBlank()) {
                    AsyncImage(
                        model = movieThumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(8.dp),
                        alpha = 0.72f
                    )
                }

                // Scrim overlay nhẹ nhàng, bảo toàn độ sáng và chi tiết của poster
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.30f),
                                    Color.Black.copy(alpha = 0.20f),
                                    Color.Black.copy(alpha = 0.58f)
                                )
                            )
                        )
                )
            }
        } else if (!isWebViewMode && errorMessage == null && isPlaybackStartedEffective && (isLoading || isPlayerBuffering)) {
            // Buffer mid-stream sau khi phim đã bắt đầu phát: Hiển thị khối thông tin phim trang trọng đồng bộ màu Theme
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(24.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(46.dp),
                        strokeWidth = 3.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = movieName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isLandscape) 18.sp else 16.sp
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val epAndServer = buildString {
                        append(currentEpisode.name)
                        if (activeServerName.isNotBlank()) {
                            append(" • ")
                            append(activeServerName)
                        }
                    }
                    Text(
                        text = epAndServer,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = if (isLandscape) 13.sp else 12.sp
                        ),
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.player_buffering_title),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.player_buffering_hint),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // 4. Badge hiển thị khi đang giữ ngón tay tăng tốc 2X
        if (isLongPressing2x) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 28.dp),
                shape = ShapeTokens.Card,
                color = Color.Black.copy(alpha = 0.75f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.player_fast_forward_2x), color = Color.Yellow, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // 4.1. HUD hiển thị % tiến độ dịch phụ đề Gemini AI
        if (isTranslatingWithGemini) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (isLandscape) 36.dp else 24.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        progress = { geminiTranslateProgress.coerceIn(0.05f, 1f) },
                        modifier = Modifier.size(17.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.width(9.dp))
                    Text(
                        text = "Gemini AI đang dịch phụ đề: ${(geminiTranslateProgress * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 5. Cử chỉ HUD (Volume ở rìa phải, Brightness ở rìa trái, Double-tap Seek ở giữa)
        // 5.1 HUD Âm lượng (Rìa phải màn hình - Vertical Pill)
        AnimatedVisibility(
            visible = hudType == HudIndicatorType.VOLUME,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .width(46.dp)
                    .height(175.dp)
                    .background(Color.Black.copy(alpha = 0.75f), ShapeTokens.LargeChip)
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    if (hudValue <= 0.01f) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )

                // Thanh trượt đứng
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(95.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(hudValue.coerceIn(0f, 1f))
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                    )
                }

                Text(
                    text = "${(hudValue * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 5.2 HUD Độ sáng (Rìa trái màn hình - Vertical Pill)
        AnimatedVisibility(
            visible = hudType == HudIndicatorType.BRIGHTNESS,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .width(46.dp)
                    .height(175.dp)
                    .background(Color.Black.copy(alpha = 0.75f), ShapeTokens.LargeChip)
                    .clip(ShapeTokens.LargeChip)
                    .clickable {
                        // Chạm vào HUD để khôi phục về độ sáng theo hệ thống (mở khóa thanh trượt Android)
                        hasCustomBrightness = false
                        lastCustomBrightnessValue = -1f
                        activity?.window?.let { win ->
                            val lp = win.attributes
                            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                            win.attributes = lp
                        }
                        showHud(HudIndicatorType.NONE, 0f)
                        Toast.makeText(context, context.getString(R.string.player_brightness_system_restored), Toast.LENGTH_SHORT).show()
                    }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    Icons.Default.BrightnessMedium,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )

                // Thanh trượt đứng
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(95.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(hudValue.coerceIn(0f, 1f))
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                    )
                }

                Text(
                    text = "${(hudValue * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 5.3 HUD Tua lùi / Tua tới (Chạm đúp - Ở giữa màn hình)
        AnimatedVisibility(
            visible = hudType == HudIndicatorType.SEEK_FORWARD || hudType == HudIndicatorType.SEEK_REWIND,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = ShapeTokens.Card,
                color = Color.Black.copy(alpha = 0.82f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                shadowElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    if (hudType == HudIndicatorType.SEEK_FORWARD) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Forward10, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("+10s", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Replay10, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("-10s", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }

        // 5.4 HUD Kéo trượt tua ngang (Swipe to Seek Preview)
        AnimatedVisibility(
            visible = hudType == HudIndicatorType.SEEK_GESTURE,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.85f), ShapeTokens.Card)
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isForward = seekGestureDeltaMs >= 0
                    Icon(
                        if (isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        val deltaSec = seekGestureDeltaMs / 1000
                        val deltaSign = if (isForward) "+" else ""
                        Text(
                            text = "$deltaSign${deltaSec}s",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "${formatTime(seekGestureTargetMs)} / ${formatTime(effectiveTotalDuration)}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 6. Nút Mở khóa khi đang bật Khóa màn hình (Screen Lock)
        if (isLocked && !isPipMode) {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp)
            ) {
                IconButton(
                    onClick = {
                        isLocked = false
                        showControls = true
                        lastInteractionTime = System.currentTimeMillis()
                        Toast.makeText(context, context.getString(R.string.player_unlock_controls), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = stringResource(R.string.player_unlock_controls), tint = Color.White)
                }
            }
        }

        // 7.1 Hàng nút điều khiển cố định ở đỉnh màn hình cho WebView Player Fallback (Luôn hiển thị, không chiếm trọn màn hình)
        if (isWebViewMode && !isPipMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = Color.White)
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = movieName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${currentEpisode.name} • Web Player",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }

                // Nút Xoay màn hình (Ngang <-> Dọc)
                IconButton(
                    onClick = {
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
                    }
                ) {
                    Icon(
                        Icons.Default.ScreenRotation,
                        contentDescription = stringResource(R.string.player_rotate),
                        tint = Color.White
                    )
                }

                // Nút Danh sách tập (cho phim bộ trong Web Player)
                if (activeEpisodes.size > 1) {
                    IconButton(
                        onClick = {
                            showEpisodesDrawer = true
                            lastInteractionTime = System.currentTimeMillis()
                        }
                    ) {
                        Icon(
                            Icons.Default.VideoLibrary,
                            contentDescription = stringResource(R.string.player_episodes_sheet_title),
                            tint = if (showEpisodesDrawer) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f) else Color.White
                        )
                    }
                }

                // Nút Chọn Nguồn Phát & Server (Multi-source Selector)
                IconButton(
                    onClick = {
                        showSourceSelectorDrawer = true
                        lastInteractionTime = System.currentTimeMillis()
                    }
                ) {
                    Icon(
                        Icons.Default.Dns,
                        contentDescription = stringResource(R.string.player_sources_btn),
                        tint = if (showSourceSelectorDrawer) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f) else Color.White
                    )
                }

                // Nút chuyển đổi nhanh WebView -> ExoPlayer Native
                val embedFallback = getEffectiveEmbedFallback()
                if (!embedFallback.isNullOrEmpty() && resolvedStream != null && errorMessage == null) {
                    IconButton(
                        onClick = {
                            isWebViewMode = false
                            activeWebView?.loadUrl("about:blank")
                            exoPlayerRetryCount = 0
                            resolvedStream?.let { exoPlayerManager.prepareStream(it) }
                        }
                    ) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = stringResource(R.string.player_switch_to_native),
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // 7. Giao diện điều khiển Player Compose Controls cho Native Player (Top Bar, Center Controls, Bottom Bar)
        if (!isLocked && !isPipMode && !isWebViewMode) {
            CompositionLocalProvider(LocalLiquidGlassBackdrop provides videoBackdrop) {
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.8f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.85f)
                                    )
                                )
                            )
                    ) {
                        // --- TOP BAR: Back, Tiêu đề phim & tập, Nút Khóa, Nút Tỉ lệ, Nút PiP, Nút Cài đặt ---
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(42.dp)
                                    .playerLiquidGlass(CircleShape, elevation = 4.dp)
                                    .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.12f)
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = Color.White)
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp)
                            ) {
                                Text(
                                    text = movieName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = currentEpisode.name,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Nút Khóa màn hình
                                IconButton(
                                    onClick = {
                                        isLocked = true
                                        lastInteractionTime = System.currentTimeMillis()
                                        Toast.makeText(context, context.getString(R.string.player_lock_controls), Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .playerLiquidGlass(CircleShape, elevation = 4.dp)
                                        .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.12f)
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.player_lock_controls), tint = Color.White, modifier = Modifier.size(20.dp))
                                }

                                // Nút Xoay màn hình (Ngang <-> Dọc)
                                IconButton(
                                    onClick = {
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
                                    modifier = Modifier
                                        .size(40.dp)
                                        .playerLiquidGlass(CircleShape, elevation = 4.dp)
                                        .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.12f)
                                ) {
                                    Icon(
                                        Icons.Default.ScreenRotation,
                                        contentDescription = stringResource(R.string.player_rotate),
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Nút Picture-in-Picture (PiP)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    IconButton(
                                        onClick = {
                                            try {
                                                showControls = false
                                                activity?.enterPictureInPictureMode(
                                                    PictureInPictureParams.Builder().build()
                                                )
                                            } catch (e: Exception) {
                                                println("[PlayerScreen] Lỗi vào PiP: ${e.message}")
                                            }
                                        },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .playerLiquidGlass(CircleShape, elevation = 4.dp)
                                            .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.12f)
                                    ) {
                                        Icon(Icons.Default.PictureInPictureAlt, contentDescription = stringResource(R.string.player_pip), tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                }

                                // Nút Danh sách tập (cho phim bộ)
                                if (activeEpisodes.size > 1) {
                                    IconButton(
                                        onClick = {
                                            lastInteractionTime = System.currentTimeMillis()
                                            showEpisodesDrawer = true
                                        },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .playerLiquidGlass(CircleShape, elevation = 4.dp)
                                            .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.12f)
                                    ) {
                                        Icon(
                                            Icons.Default.VideoLibrary,
                                            contentDescription = stringResource(R.string.player_episodes_sheet_title),
                                            tint = if (showEpisodesDrawer) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f) else Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                // Nút Cài đặt (Tốc độ, Chất lượng HLS)
                                if (!isWebViewMode) {
                                    IconButton(
                                        onClick = {
                                            videoTracks = exoPlayerManager.getVideoTracks()
                                            audioTracks = exoPlayerManager.getAudioTracks()
                                            showSettingsDialog = true
                                        },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .playerLiquidGlass(CircleShape, elevation = 4.dp)
                                            .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.12f)
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.player_settings_title), tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                }

                                // Nút chuyển đổi nhanh WebView <-> ExoPlayer
                                val embedFallback = getEffectiveEmbedFallback()
                                if (!embedFallback.isNullOrEmpty() && resolvedStream != null && errorMessage == null) {
                                    IconButton(
                                        onClick = {
                                            if (isWebViewMode) {
                                                isWebViewMode = false
                                                activeWebView?.loadUrl("about:blank")
                                                exoPlayerRetryCount = 0
                                                resolvedStream?.let { exoPlayerManager.prepareStream(it) }
                                            } else {
                                                exoPlayerManager.pause()
                                                webViewUrl = embedFallback
                                                webViewHeaders = embedHeadersFor(embedFallback, resolvedStream?.headers)
                                                isWebViewMode = true
                                            }
                                        },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .playerLiquidGlass(CircleShape, elevation = 4.dp)
                                    ) {
                                        Icon(
                                            if (isWebViewMode) Icons.Default.PlayCircle else Icons.Default.Language,
                                            contentDescription = if (isWebViewMode) stringResource(R.string.player_switch_to_native) else stringResource(R.string.player_switch_to_webview),
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // --- CENTER CONTROLS: Buffering HUD khi chưa phát, hoặc [Tua lùi 10s, Tập trước, Play/Pause/Buffer, Tập sau, Tua tới 10s] ---
                        if (!isWebViewMode && errorMessage == null) {
                            val isCenterHudActive = hudType == HudIndicatorType.SEEK_FORWARD ||
                                    hudType == HudIndicatorType.SEEK_REWIND ||
                                    hudType == HudIndicatorType.SEEK_GESTURE
                            val isBufferingActive = isLoading || isPlayerBuffering

                            if (!isPlaybackStartedEffective) {
                                // Tích hợp Buffering HUD vào chính giữa vùng điều khiển theo Theme Kính Lỏng
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .fillMaxWidth(0.85f)
                                        .padding(24.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(46.dp),
                                        strokeWidth = 3.5.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(18.dp))
                                    Text(
                                        text = movieName,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = if (isLandscape) 18.sp else 16.sp
                                        ),
                                        color = Color.White,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val epAndServer = buildString {
                                        append(currentEpisode.name)
                                        if (activeServerName.isNotBlank()) {
                                            append(" • ")
                                            append(activeServerName)
                                        }
                                    }
                                    Text(
                                        text = epAndServer,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = if (isLandscape) 13.sp else 12.sp
                                        ),
                                        color = Color.White.copy(alpha = 0.7f),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Box(
                                        modifier = Modifier.playerLiquidGlass(
                                            shape = RoundedCornerShape(20.dp),
                                            tint = Color(0xFF10141C).copy(alpha = 0.65f),
                                            elevation = 6.dp
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (isLoading) stringResource(R.string.player_buffering_connecting) else stringResource(R.string.player_buffering_title),
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.player_buffering_hint),
                                        color = Color.White.copy(alpha = 0.45f),
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else if (!isBufferingActive && !isCenterHudActive && !isDraggingGesture && !isDraggingSlider) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .focusGroup(),
                                    horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 22.dp else 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Tập trước
                                    IconButton(
                                        onClick = {
                                            if (activeEpisodes.isNotEmpty() && currentEpisodeIndex > 0) {
                                                showResumePromptDialog = false
                                                resumeTargetPositionMs = 0L
                                                currentEpisodeIndex -= 1
                                                ActivePlayerSession.currentEpisodeIndex = currentEpisodeIndex
                                                ActivePlayerSession.currentEpisode = activeEpisodes[currentEpisodeIndex]
                                            }
                                        },
                                        enabled = activeEpisodes.isNotEmpty() && currentEpisodeIndex > 0,
                                        modifier = Modifier
                                            .size(46.dp)
                                            .playerLiquidGlass(CircleShape, elevation = 4.dp)
                                            .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.12f)
                                    ) {
                                        Icon(
                                            Icons.Default.SkipPrevious,
                                            contentDescription = stringResource(R.string.player_prev_episode),
                                            tint = if (activeEpisodes.isNotEmpty() && currentEpisodeIndex > 0) Color.White else Color.Gray,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }

                                    // Tua lùi 10s
                                    IconButton(
                                        onClick = {
                                            val target = (exoPlayerManager.player.currentPosition - 10_000).coerceAtLeast(0L)
                                            exoPlayerManager.player.seekTo(target)
                                            showHud(HudIndicatorType.SEEK_REWIND, 10f)
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .playerLiquidGlass(CircleShape, elevation = 4.dp)
                                            .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.12f)
                                    ) {
                                        Icon(Icons.Default.Replay10, contentDescription = stringResource(R.string.player_rewind_10s), tint = Color.White, modifier = Modifier.size(32.dp))
                                    }

                                    // Play / Pause (Tâm điểm Kính lỏng)
                                    IconButton(
                                        onClick = {
                                            if (isPlaying) exoPlayerManager.pause() else exoPlayerManager.play()
                                        },
                                        modifier = Modifier
                                            .size(68.dp)
                                            .focusRequester(playPauseFocusRequester)
                                            .playerLiquidGlass(
                                                shape = CircleShape,
                                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.58f),
                                                elevation = 10.dp
                                            )
                                            .tvFocusableItem(cornerRadius = 34.dp, focusedScale = 1.12f, focusColor = Color.White)
                                    ) {
                                        Icon(
                                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) stringResource(R.string.player_pause) else stringResource(R.string.player_play),
                                            tint = Color.White,
                                            modifier = Modifier.size(42.dp)
                                        )
                                    }

                                    // Tua tới 10s
                                    IconButton(
                                        onClick = {
                                            val maxDur = exoPlayerManager.player.duration.coerceAtLeast(0L)
                                            val target = if (maxDur > 0) (exoPlayerManager.player.currentPosition + 10_000).coerceAtMost(maxDur) else exoPlayerManager.player.currentPosition + 10_000
                                            exoPlayerManager.player.seekTo(target)
                                            showHud(HudIndicatorType.SEEK_FORWARD, 10f)
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .playerLiquidGlass(CircleShape, elevation = 4.dp)
                                            .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.12f)
                                    ) {
                                        Icon(Icons.Default.Forward10, contentDescription = stringResource(R.string.player_forward_10s), tint = Color.White, modifier = Modifier.size(32.dp))
                                    }

                                    // Tập sau
                                    IconButton(
                                        onClick = {
                                            if (activeEpisodes.isNotEmpty() && currentEpisodeIndex < activeEpisodes.size - 1) {
                                                showResumePromptDialog = false
                                                resumeTargetPositionMs = 0L
                                                currentEpisodeIndex += 1
                                                ActivePlayerSession.currentEpisodeIndex = currentEpisodeIndex
                                                ActivePlayerSession.currentEpisode = activeEpisodes[currentEpisodeIndex]
                                            }
                                        },
                                        enabled = activeEpisodes.isNotEmpty() && currentEpisodeIndex < activeEpisodes.size - 1,
                                        modifier = Modifier
                                            .size(46.dp)
                                            .playerLiquidGlass(CircleShape, elevation = 4.dp)
                                            .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.12f)
                                    ) {
                                        Icon(
                                            Icons.Default.SkipNext,
                                            contentDescription = stringResource(R.string.player_next_episode),
                                            tint = if (activeEpisodes.isNotEmpty() && currentEpisodeIndex < activeEpisodes.size - 1) Color.White else Color.Gray,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                            }
                        }

                    // --- BOTTOM BAR (Nuvio Style): Seekbar Slider, Timeline (Current / Total), Centered Pill Actions ---
                    if (!isWebViewMode) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 10.dp)
                        ) {
                            val displayPosition = if (isDraggingSlider) (sliderDragRatio * effectiveTotalDuration).toLong() else currentPosition

                            // 1. Seekbar Timeline
                            Slider(
                                value = if (isDraggingSlider) sliderDragRatio else if (effectiveTotalDuration > 0) (currentPosition.toFloat() / effectiveTotalDuration.toFloat()).coerceIn(0f, 1f) else 0f,
                                onValueChange = { ratio ->
                                    isDraggingSlider = true
                                    sliderDragRatio = ratio
                                    lastInteractionTime = System.currentTimeMillis()
                                },
                                onValueChangeFinished = {
                                    val targetMs = (sliderDragRatio * effectiveTotalDuration).toLong()
                                    currentPosition = targetMs
                                    isSeeking = true
                                    exoPlayerManager.seekTo(targetMs)
                                    isDraggingSlider = false
                                    lastInteractionTime = System.currentTimeMillis()
                                    coroutineScope.launch {
                                        delay(800)
                                        isSeeking = false
                                    }
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                                    activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier.fillMaxWidth().height(24.dp)
                            )

                            // 2. Timeline Numbers: Left = Nút Play/Stop nhỏ + Current Time, Right = Total Duration & Volume Mute Button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            lastInteractionTime = System.currentTimeMillis()
                                            if (isPlaying) exoPlayerManager.pause() else exoPlayerManager.play()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) stringResource(R.string.player_pause) else stringResource(R.string.player_play),
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Text(
                                        text = formatTime(displayPosition),
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = formatTime(effectiveTotalDuration),
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )

                                    val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    val isMuted = curVol <= 0

                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .playerLiquidGlass(
                                                shape = CircleShape,
                                                tint = if (isMuted) Color(0xFFE53935).copy(alpha = 0.70f) else Color.White.copy(alpha = 0.12f),
                                                elevation = 3.dp
                                            )
                                            .clickable {
                                                lastInteractionTime = System.currentTimeMillis()
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
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                                            contentDescription = stringResource(R.string.player_audio_pill_label),
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // 3. Dàn nút điều khiển Capsule nằm phía dưới Seekbar chuẩn Nuvio Liquid Glass (Kích thước to rõ, Icon + Label)
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .playerLiquidGlass(
                                            shape = RoundedCornerShape(26.dp),
                                            tint = Color(0xFF10141C).copy(alpha = 0.60f),
                                            elevation = 8.dp
                                        )
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    val btnSize = if (isLandscape) 40.dp else 36.dp
                                    val iconSize = if (isLandscape) 20.dp else 18.dp
                                    val pillSpacing = if (isLandscape) 6.dp else 4.dp

                                    Row(
                                        modifier = Modifier
                                            .horizontalScroll(rememberScrollState())
                                            .focusGroup(),
                                        horizontalArrangement = Arrangement.spacedBy(pillSpacing),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 3.1. Tỉ lệ khung hình (Resize Mode - Ẩn tiêu đề, chỉ icon)
                                        val isResizeCustom = resizeModeIndex != 0
                                        Box(
                                            modifier = Modifier
                                                .size(btnSize)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isResizeCustom) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f)
                                                )
                                                .then(
                                                    if (isResizeCustom) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.50f), CircleShape) else Modifier
                                                )
                                                .focusRequester(capsulePillFocusRequester)
                                                .tvFocusableItem(cornerRadius = 20.dp, focusedScale = 1.15f)
                                                .clickable {
                                                    lastInteractionTime = System.currentTimeMillis()
                                                    resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
                                                    val (mode, label) = resizeModes[resizeModeIndex]
                                                    activePlayerView?.resizeMode = mode
                                                    Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AspectRatio,
                                                contentDescription = resizeModes[resizeModeIndex].second,
                                                tint = if (isResizeCustom) MaterialTheme.colorScheme.primary else Color.White,
                                                modifier = Modifier.size(iconSize)
                                            )
                                        }

                                        // 3.2. Tốc độ phát (Speed - Hiển thị số 1.0x / 1.5x hoặc icon)
                                        val isSpeedCustom = currentSpeed != 1.0f
                                        Box(
                                            modifier = Modifier
                                                .size(btnSize)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSpeedCustom) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f)
                                                )
                                                .then(
                                                    if (isSpeedCustom) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.50f), CircleShape) else Modifier
                                                )
                                                .tvFocusableItem(cornerRadius = 20.dp, focusedScale = 1.15f)
                                                .clickable {
                                                    lastInteractionTime = System.currentTimeMillis()
                                                    val nextIdx = (speeds.indexOf(currentSpeed) + 1) % speeds.size
                                                    currentSpeed = speeds[nextIdx]
                                                    exoPlayerManager.setPlaybackSpeed(currentSpeed)
                                                    Toast.makeText(context, context.getString(R.string.player_speed_toast, "${currentSpeed}x"), Toast.LENGTH_SHORT).show()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSpeedCustom) {
                                                Text(
                                                    text = "${currentSpeed}x",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontSize = if (isLandscape) 12.sp else 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Speed,
                                                    contentDescription = stringResource(R.string.player_speed_title),
                                                    tint = Color.White,
                                                    modifier = Modifier.size(iconSize)
                                                )
                                            }
                                        }

                                        // 3.3. Âm thanh (Audio Tracks - Ẩn tiêu đề)
                                        val hasMultiAudio = audioTracks.size > 1
                                        val isCustomAudio = selectedAudioTrackId != null
                                        val isAudioActive = hasMultiAudio || isCustomAudio
                                        Box(
                                            modifier = Modifier
                                                .size(btnSize)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isAudioActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f)
                                                )
                                                .then(
                                                    if (isAudioActive) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.50f), CircleShape) else Modifier
                                                )
                                                .tvFocusableItem(cornerRadius = 20.dp, focusedScale = 1.15f)
                                                .clickable {
                                                    lastInteractionTime = System.currentTimeMillis()
                                                    audioTracks = exoPlayerManager.getAudioTracks()
                                                    showAudioSheet = true
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Audiotrack,
                                                contentDescription = stringResource(R.string.player_audio_pill_label),
                                                tint = if (isAudioActive) MaterialTheme.colorScheme.primary else Color.White,
                                                modifier = Modifier.size(iconSize)
                                            )
                                        }

                                        // 3.4. Phụ đề (Subtitles Panel - Ẩn tiêu đề)
                                        val hasActiveSub = selectedSubtitleTrackId != "off" && selectedSubtitleTrackId != null
                                        Box(
                                            modifier = Modifier
                                                .size(btnSize)
                                                .clip(CircleShape)
                                                .background(
                                                    if (hasActiveSub) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f)
                                                )
                                                .then(
                                                    if (hasActiveSub) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.50f), CircleShape) else Modifier
                                                )
                                                .tvFocusableItem(cornerRadius = 20.dp, focusedScale = 1.15f)
                                                .clickable {
                                                    lastInteractionTime = System.currentTimeMillis()
                                                    subtitleTracks = mergeSubtitleTracks(customSubtitleTracks, exoPlayerManager.getSubtitleTracks())
                                                    if (!hasActiveSub) {
                                                        selectedLangGroup = "none"
                                                    }
                                                    showSubtitleSheet = true
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Subtitles,
                                                contentDescription = stringResource(R.string.player_subtitles_pill_label),
                                                tint = if (hasActiveSub) MaterialTheme.colorScheme.primary else Color.White,
                                                modifier = Modifier.size(iconSize)
                                            )
                                        }

                                        // 3.5. Thuyết minh AI (TTS - Ẩn tiêu đề)
                                        Box(
                                            modifier = Modifier
                                                .size(btnSize)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isTtsEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f)
                                                )
                                                .then(
                                                    if (isTtsEnabled) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.50f), CircleShape) else Modifier
                                                )
                                                .tvFocusableItem(cornerRadius = 20.dp, focusedScale = 1.15f)
                                                .clickable {
                                                    lastInteractionTime = System.currentTimeMillis()
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
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.RecordVoiceOver,
                                                contentDescription = stringResource(R.string.player_tts_pill_label),
                                                tint = if (isTtsEnabled) MaterialTheme.colorScheme.primary else Color.White,
                                                modifier = Modifier.size(iconSize)
                                            )
                                        }

                                        // 3.6. Danh sách tập (khi có nhiều tập - Ẩn tiêu đề)
                                        if (activeEpisodes.size > 1) {
                                            Box(
                                                modifier = Modifier
                                                    .size(btnSize)
                                                    .clip(CircleShape)
                                                    .background(Color.White.copy(alpha = 0.06f))
                                                    .tvFocusableItem(cornerRadius = 20.dp, focusedScale = 1.15f)
                                                    .clickable {
                                                        lastInteractionTime = System.currentTimeMillis()
                                                        showEpisodesDrawer = true
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.VideoLibrary,
                                                    contentDescription = stringResource(R.string.player_episodes_sheet_title),
                                                    tint = Color.White,
                                                    modifier = Modifier.size(iconSize)
                                                )
                                            }
                                        }

                                        // 3.7. Nguồn (Sources / Server - Ẩn tiêu đề)
                                        Box(
                                            modifier = Modifier
                                                .size(btnSize)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.06f))
                                                .tvFocusableItem(cornerRadius = 20.dp, focusedScale = 1.15f)
                                                .clickable {
                                                    lastInteractionTime = System.currentTimeMillis()
                                                    showSourceSelectorDrawer = true
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Cloud,
                                                contentDescription = stringResource(R.string.player_source_pill_label),
                                                tint = Color.White,
                                                modifier = Modifier.size(iconSize)
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

        // 8. Panel Cài Đặt Trình Phát (Right Side Drawer Panel sang trọng)
        AnimatedVisibility(
            visible = showSettingsDialog,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .pointerInput(Unit) {
                        detectTapGestures { showSettingsDialog = false }
                    }
            ) {
                // Panel trượt từ bên phải
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(360.dp)
                        .background(
                            Color.Black.copy(alpha = 0.65f),
                            RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                        )
                        .pointerInput(Unit) {
                            // Chặn chạm xuyên qua ra màn hình chính
                            detectTapGestures { }
                        }
                        .padding(20.dp)
                ) {
                    LaunchedEffect(showSettingsDialog) {
                        if (showSettingsDialog) {
                            delay(60)
                            try {
                                settingsDialogInitialFocusRequester.requestFocus()
                            } catch (_: Exception) {}
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.player_settings_title),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = { showSettingsDialog = false },
                                modifier = Modifier
                                    .focusRequester(settingsDialogInitialFocusRequester)
                                    .tvFocusableItem(cornerRadius = 18.dp, focusedScale = 1.12f)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 12.dp))

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Mục 1: Tốc độ phát video
                            item {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.player_speed_title).uppercase(),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = "${currentSpeed}x",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    // Lưới nút tốc độ phát chia đều 3 cột x 2 hàng
                                    val speedChunks = speeds.chunked(3)
                                    speedChunks.forEach { rowSpeeds ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            rowSpeeds.forEach { sp ->
                                                val isSelected = currentSpeed == sp
                                                Surface(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(36.dp)
                                                        .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.08f),
                                                    shape = ShapeTokens.Button,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                                    onClick = {
                                                        currentSpeed = sp
                                                        exoPlayerManager.setPlaybackSpeed(sp)
                                                    }
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = "${sp}x",
                                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                            fontSize = 13.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                            }

                            // Mục 2: Chất lượng Video HLS
                            item {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                                Text(
                                    text = stringResource(R.string.player_quality_title).uppercase(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                SettingsOptionCard(
                                    title = stringResource(R.string.player_quality_auto),
                                    subtitle = stringResource(R.string.player_quality_auto_desc),
                                    isSelected = selectedVideoTrackId == "auto",
                                    onClick = {
                                        selectedVideoTrackId = "auto"
                                        exoPlayerManager.setVideoTrack(null)
                                    }
                                )
                            }

                            items(videoTracks) { track ->
                                SettingsOptionCard(
                                    title = track.label,
                                    subtitle = if (track.width > 0 && track.height > 0) "${track.width}x${track.height}" else null,
                                    isSelected = selectedVideoTrackId == track.id,
                                    onClick = {
                                        selectedVideoTrackId = track.id
                                        exoPlayerManager.setVideoTrack(track)
                                    }
                                )
                            }

                            // Mục 3: Luồng âm thanh (Audio Tracks)
                            if (audioTracks.isNotEmpty()) {
                                item {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                                    Text(
                                        text = stringResource(R.string.player_audio_title).uppercase(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                items(audioTracks) { track ->
                                    val specs = exoPlayerManager.formatAudioSpecs(track)
                                    val isSelected = if (selectedAudioTrackId != null) selectedAudioTrackId == track.id else track.isSelected
                                    SettingsOptionCard(
                                        title = track.label,
                                        subtitle = specs.ifBlank { null },
                                        isSelected = isSelected,
                                        onClick = {
                                            selectedAudioTrackId = track.id
                                            exoPlayerManager.setAudioTrack(track)
                                            audioTracks = exoPlayerManager.getAudioTracks()
                                            Toast.makeText(context, context.getString(R.string.player_audio_selected_toast, track.label), Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        }
                    }
                }
            }
        }

        // 8.1.5 Panel Tùy Chỉnh Phụ Đề Chuẩn Nuvio (3 Cột: Ngôn ngữ | Phụ đề | Kiểu)
        AnimatedVisibility(
            visible = showSubtitleSheet,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.52f))
                    .pointerInput(Unit) {
                        detectTapGestures { showSubtitleSheet = false }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .then(
                            if (isLandscape) {
                                Modifier
                                    .align(Alignment.Center)
                                    .fillMaxWidth(0.78f)
                                    .fillMaxHeight(0.78f)
                                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            } else {
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.72f)
                                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            }
                        )
                        .pointerInput(Unit) { detectTapGestures { } }
                ) {
                    LaunchedEffect(showSubtitleSheet) {
                        if (showSubtitleSheet) {
                            delay(60)
                            try {
                                subtitleSheetInitialFocusRequester.requestFocus()
                            } catch (_: Exception) {}
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Header: Tiêu đề "Phụ đề" & Nút đóng
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = if (isLandscape) 12.dp else 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.player_subtitles_pill_label),
                                color = Color.White,
                                fontSize = if (isLandscape) 22.sp else 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { showSubtitleSheet = false },
                                modifier = Modifier
                                    .size(36.dp)
                                    .focusRequester(subtitleSheetInitialFocusRequester)
                                    .tvFocusableItem(cornerRadius = 18.dp, focusedScale = 1.12f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.common_close),
                                    tint = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }

                        // 3 Cột: Ngôn ngữ | Phụ đề | Kiểu
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 16.dp else 8.dp)
                        ) {
                            // CỘT 1: Ngôn ngữ
                            Column(
                                modifier = Modifier
                                    .weight(if (isLandscape) 1f else 0.95f)
                                    .fillMaxHeight()
                            ) {
                                Text(
                                    text = stringResource(R.string.player_subtitles_language_header),
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = if (isLandscape) 13.sp else 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = if (isLandscape) 10.dp else 6.dp)
                                )

                                val allActiveLangCodes = remember(configuredSubLanguages, subtitleTracks) {
                                    val list = mutableListOf<String>()
                                    if (configuredSubLanguages.contains("vi")) list.add("vi")
                                    if (configuredSubLanguages.contains("en")) list.add("en")
                                    configuredSubLanguages.filter { it != "vi" && it != "en" }.sorted().forEach { list.add(it) }
                                    // Chỉ bổ sung thêm các ngôn ngữ nhúng sẵn trong track stream của video đang phát
                                    subtitleTracks.mapNotNull { it.language?.lowercase()?.trim() }
                                        .filter { it.isNotBlank() && !list.contains(it) && it != "und" }
                                        .distinct()
                                        .forEach { list.add(it) }
                                    list
                                }

                                val col1ItemHeight = if (isLandscape) 42.dp else 36.dp
                                val col1PaddingH = if (isLandscape) 14.dp else 8.dp
                                val col1FontSize = if (isLandscape) 13.5.sp else 12.sp
                                val col1BadgeSize = if (isLandscape) 22.dp else 18.dp
                                val col1BadgeFontSize = if (isLandscape) 11.sp else 9.5.sp

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 6.dp)
                                ) {
                                    // 1. "Không có"
                                    item {
                                        val isNoneSelected = selectedLangGroup == "none" || selectedSubtitleTrackId == "off"
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(col1ItemHeight)
                                                .tvFocusableItem(cornerRadius = col1ItemHeight / 2, focusedScale = 1.05f),
                                            shape = RoundedCornerShape(col1ItemHeight / 2),
                                            color = if (isNoneSelected && selectedSubtitleTrackId == "off") MaterialTheme.colorScheme.primary else Color.Transparent,
                                            onClick = {
                                                selectedLangGroup = "none"
                                                selectedSubtitleTrackId = "off"
                                                activeOnlineSubId = null
                                                isSubtitleDisabled = true
                                                currentSubCues = emptyList()
                                                activePlayerView?.subtitleView?.setCues(emptyList())
                                                exoPlayerManager.setSubtitleTrack(null)
                                                subtitleManager.cacheManager.saveSavedSubtitleTrack(activeProviderId, activeMovieSlug, currentEpisode.id, "off", tmdbId = movieTmdbId)
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxSize().padding(horizontal = col1PaddingH),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.player_subtitles_none),
                                                    color = Color.White,
                                                    fontSize = col1FontSize,
                                                    fontWeight = if (isNoneSelected && selectedSubtitleTrackId == "off") FontWeight.Bold else FontWeight.Normal,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    // 2. Danh sách các ngôn ngữ (Được cấu hình + có sẵn trong stream)
                                    items(allActiveLangCodes) { langCode ->
                                        val isLangSelected = selectedLangGroup == langCode
                                        val streamCount = subtitleTracks.count {
                                            when (langCode) {
                                                "vi" -> isVietnameseTrack(it)
                                                "en" -> isEnglishTrack(it)
                                                else -> it.language?.equals(langCode, true) == true
                                            }
                                        }
                                        val onlineCount = ((stremioAddonSubsMap[langCode] ?: emptyList()) + (onlineSubsMap[langCode] ?: emptyList()))
                                            .distinctBy { it.downloadUrl ?: it.id }
                                            .size
                                        val totalCount = streamCount + onlineCount
                                        val displayName = com.jkhx.player.core.player.subtitle.model.SubtitleSupportedLanguages.getDisplayName(langCode)

                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(col1ItemHeight)
                                                .tvFocusableItem(cornerRadius = col1ItemHeight / 2, focusedScale = 1.05f),
                                            shape = RoundedCornerShape(col1ItemHeight / 2),
                                            color = if (isLangSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            onClick = {
                                                selectedLangGroup = langCode
                                                if (onlineSubsMap[langCode] == null && loadingSubsMap[langCode] != true) {
                                                    executeOnlineSubSearch(langCode, force = false)
                                                }
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxSize().padding(horizontal = col1PaddingH),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = displayName,
                                                    color = Color.White,
                                                    fontSize = col1FontSize,
                                                    fontWeight = if (isLangSelected) FontWeight.Bold else FontWeight.Normal,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (totalCount > 0) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(col1BadgeSize)
                                                            .background(if (isLangSelected) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary, CircleShape),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "$totalCount",
                                                            color = Color.White,
                                                            fontSize = col1BadgeFontSize,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // CỘT 2: Phụ đề (Unified Stream + Online Subtitles)
                            Column(
                                modifier = Modifier
                                    .weight(if (isLandscape) 1.35f else 1.3f)
                                    .fillMaxHeight()
                            ) {
                                // Header: Tiêu đề theo nhóm ngôn ngữ + Nút Sửa từ khóa + Nút Làm mới
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = if (isLandscape) 8.dp else 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = when (selectedLangGroup) {
                                            "vi" -> stringResource(R.string.player_subtitles_vi_header)
                                            "en" -> stringResource(R.string.player_subtitles_en_header)
                                            "none" -> stringResource(R.string.player_subtitles_none)
                                            else -> stringResource(R.string.player_subtitles_stream_header, selectedLangGroup.uppercase())
                                        },
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = if (isLandscape) 13.sp else 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )

                                    if (selectedLangGroup == "vi" || selectedLangGroup == "en") {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            // Nút sửa từ khóa
                                            IconButton(
                                                onClick = {
                                                    isEditingSubSearchQuery = !isEditingSubSearchQuery
                                                    if (isEditingSubSearchQuery) {
                                                        tempSubSearchQuery = customSubSearchQuery ?: ""
                                                    }
                                                },
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .tvFocusableItem(cornerRadius = 15.dp, focusedScale = 1.15f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = stringResource(R.string.player_subtitles_edit_query),
                                                    tint = if (isEditingSubSearchQuery) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            // Nút làm mới danh sách (force refresh online)
                                            val isLangLoading = (loadingSubsMap[selectedLangGroup] == true) || isStremioSubsLoading
                                            IconButton(
                                                onClick = {
                                                    if (!isLangLoading) {
                                                        if (selectedLangGroup != "none") {
                                                            loadStremioAddonSubtitles(force = true)
                                                            executeOnlineSubSearch(selectedLangGroup, force = true)
                                                        }
                                                    }
                                                },
                                                enabled = !isLangLoading,
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .tvFocusableItem(cornerRadius = 15.dp, focusedScale = 1.15f)
                                            ) {
                                                if (isLangLoading) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(14.dp),
                                                        strokeWidth = 2.dp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = stringResource(R.string.player_subtitles_refresh),
                                                        tint = Color.White.copy(alpha = 0.7f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Ô sửa từ khóa tìm kiếm (khi bật Edit)
                                if (isEditingSubSearchQuery && selectedLangGroup != "none") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        BasicTextField(
                                            value = tempSubSearchQuery,
                                            onValueChange = { tempSubSearchQuery = it },
                                            singleLine = true,
                                            textStyle = androidx.compose.ui.text.TextStyle(
                                                fontSize = 12.sp,
                                                color = Color.White,
                                                fontWeight = FontWeight.Normal
                                            ),
                                            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp),
                                            decorationBox = { innerTextField ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color(0xFF161920), RoundedCornerShape(8.dp))
                                                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Search,
                                                        contentDescription = null,
                                                        tint = Color.White.copy(alpha = 0.4f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                                        if (tempSubSearchQuery.isEmpty()) {
                                                            Text(
                                                                stringResource(R.string.player_subtitles_edit_search_hint),
                                                                fontSize = 11.5.sp,
                                                                color = Color.White.copy(alpha = 0.35f)
                                                            )
                                                        }
                                                        innerTextField()
                                                    }
                                                    if (tempSubSearchQuery.isNotEmpty()) {
                                                        IconButton(
                                                            onClick = { tempSubSearchQuery = "" },
                                                            modifier = Modifier.size(20.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Close,
                                                                contentDescription = "Clear",
                                                                tint = Color.White.copy(alpha = 0.5f),
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                        Button(
                                            onClick = {
                                                customSubSearchQuery = tempSubSearchQuery.trim()
                                                isEditingSubSearchQuery = false
                                                if (selectedLangGroup != "none") {
                                                    executeOnlineSubSearch(selectedLangGroup, force = true)
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                            modifier = Modifier
                                                .height(36.dp)
                                                .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.06f)
                                        ) {
                                            Text(stringResource(R.string.player_subtitles_search_action), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }

                                // Danh sách các bản phụ đề
                                val isLangLoading = (loadingSubsMap[selectedLangGroup] == true) || isStremioSubsLoading
                                val onlineItems = ((stremioAddonSubsMap[selectedLangGroup] ?: emptyList()) + (onlineSubsMap[selectedLangGroup] ?: emptyList()))
                                    .distinctBy { it.downloadUrl ?: it.id }
                                val streamItems = remember(subtitleTracks, selectedLangGroup) {
                                    when (selectedLangGroup) {
                                        "vi" -> subtitleTracks.filter { isVietnameseTrack(it) }
                                        "en" -> subtitleTracks.filter { isEnglishTrack(it) }
                                        "none" -> emptyList()
                                        else -> subtitleTracks.filter { it.language?.equals(selectedLangGroup, true) == true }
                                    }
                                }

                                if (selectedLangGroup == "none") {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.player_subtitle_off),
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 13.sp
                                        )
                                    }
                                } else if (isLangLoading && streamItems.isEmpty() && onlineItems.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                text = stringResource(R.string.player_subtitles_loading_online),
                                                color = Color.White.copy(alpha = 0.6f),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                } else if (streamItems.isEmpty() && onlineItems.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = stringResource(R.string.player_subtitles_no_results),
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 12.5.sp,
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            TextButton(
                                                onClick = {
                                                    loadStremioAddonSubtitles(force = true)
                                                    executeOnlineSubSearch(selectedLangGroup, force = true)
                                                }
                                            ) {
                                                Text(stringResource(R.string.player_subtitles_refresh), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // 1. Phụ đề có sẵn từ luồng phát (Stream / Plugin / Custom)
                                        items(streamItems) { track ->
                                            val isSelected = selectedSubtitleTrackId == track.id
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .tvFocusableItem(cornerRadius = 10.dp, focusedScale = 1.03f),
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF1E222B),
                                                border = if (isSelected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                                onClick = {
                                                    applySubJob?.cancel()
                                                    isSubtitleDisabled = false
                                                    selectedSubtitleTrackId = track.id
                                                    activeOnlineSubId = null
                                                    val savedOffset = subtitleManager.cacheManager.getSubtitleOffset(activeProviderId, activeMovieSlug, currentEpisode.id, track.id, movieTmdbId)
                                                    val cues = subtitleCuesMap[track.id] ?: subtitleCuesMap[track.label]
                                                    if (cues != null && cues.isNotEmpty()) {
                                                        currentSubCues = cues
                                                        currentSubOffsetMs = savedOffset
                                                        exoPlayerManager.player.trackSelectionParameters = exoPlayerManager.player.trackSelectionParameters
                                                            .buildUpon()
                                                            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                                                            .build()
                                                    } else {
                                                        currentSubCues = emptyList()
                                                        currentSubOffsetMs = savedOffset
                                                        activePlayerView?.subtitleView?.setCues(emptyList())
                                                        exoPlayerManager.setSubtitleTrack(track)
                                                        resolvedStream?.let { loadStreamSubtitles(it) }
                                                    }
                                                    subtitleManager.cacheManager.saveSavedSubtitleTrack(
                                                        activeProviderId,
                                                        activeMovieSlug,
                                                        currentEpisode.id,
                                                        track.id,
                                                        label = track.label,
                                                        language = track.language,
                                                        tmdbId = movieTmdbId
                                                    )
                                                    Toast.makeText(context, context.getString(R.string.player_subtitles_selected_stream_track, track.label), Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        val badgeText = when {
                                                            track.label.contains("Gemini", true) -> "✨ [JT-SUBTITLES] Gemini AI"
                                                            track.label.contains("OpenSubtitles", true) -> "[OpenSubtitles]"
                                                            track.label.contains("SubDL", true) -> "[SubDL]"
                                                            track.label.contains("Subsource", true) -> "[Subsource]"
                                                            else -> stringResource(R.string.player_subtitles_stream_badge)
                                                        }
                                                        Text(
                                                            text = badgeText,
                                                            color = if (isSelected) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            modifier = Modifier
                                                                .background(
                                                                    if (isSelected) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                                                    RoundedCornerShape(4.dp)
                                                                )
                                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                                        )
                                                        Spacer(modifier = Modifier.height(3.dp))
                                                        Text(
                                                            text = track.label,
                                                            color = Color.White,
                                                            fontSize = 12.5.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    if (isSelected) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // 2. Phụ đề trực tuyến (Online Subtitles: OpenSubtitles, SubDL, Subsource)
                                        items(onlineItems) { item ->
                                            val customItemId = "online_${item.source}_${item.id}"
                                            val isSelected = (selectedSubtitleTrackId == customItemId) || (activeOnlineSubId == item.id)
                                            val isDownloadingThis = downloadingSubItemId == item.id

                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .tvFocusableItem(cornerRadius = 10.dp, focusedScale = 1.03f),
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF1E222B),
                                                border = if (isSelected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                                onClick = {
                                                    if (!isDownloadingThis && !isTranslatingWithGemini) {
                                                        applyOnlineSub(item)
                                                    }
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Text(
                                                                text = "[${item.source}]",
                                                                color = if (isSelected) Color.White.copy(alpha = 0.95f) else MaterialTheme.colorScheme.primary,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier
                                                                    .background(
                                                                        if (isSelected) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                                                        RoundedCornerShape(4.dp)
                                                                    )
                                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                                            )
                                                            val dlCount = item.downloadCount
                                                            if (dlCount != null && dlCount > 0) {
                                                                Text(
                                                                    text = "⬇ $dlCount",
                                                                    color = Color.White.copy(alpha = 0.6f),
                                                                    fontSize = 10.5.sp
                                                                )
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.height(3.dp))
                                                        Text(
                                                            text = item.name,
                                                            color = Color.White,
                                                            fontSize = 12.5.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }

                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        // Nút Dịch AI trực tiếp sang Tiếng Việt cho các bản sub ngoại ngữ
                                                        if (selectedLangGroup != "vi" && selectedLangGroup != "none") {
                                                            val isAlreadyTranslated = translatedEnglishSubIds.contains(item.id)
                                                            if (isAlreadyTranslated) {
                                                                Surface(
                                                                    shape = RoundedCornerShape(6.dp),
                                                                    color = Color(0xFF2E7D32),
                                                                    modifier = Modifier.tvFocusableItem(cornerRadius = 6.dp, focusedScale = 1.08f),
                                                                    onClick = {
                                                                        selectedLangGroup = "vi"
                                                                    }
                                                                ) {
                                                                    Row(
                                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.Check,
                                                                            contentDescription = null,
                                                                            tint = Color.White,
                                                                            modifier = Modifier.size(12.dp)
                                                                        )
                                                                        Text(
                                                                            text = "Đã dịch AI",
                                                                            color = Color.White,
                                                                            fontSize = 10.5.sp,
                                                                            fontWeight = FontWeight.Bold
                                                                        )
                                                                    }
                                                                }
                                                            } else {
                                                                Surface(
                                                                    shape = RoundedCornerShape(6.dp),
                                                                    color = Color(0xFF1565C0),
                                                                    modifier = Modifier.tvFocusableItem(cornerRadius = 6.dp, focusedScale = 1.08f),
                                                                    onClick = {
                                                                        if (!isDownloadingThis && !isTranslatingWithGemini) {
                                                                            translateOnlineSubWithGemini(item)
                                                                        }
                                                                    }
                                                                ) {
                                                                    Row(
                                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.AutoAwesome,
                                                                            contentDescription = null,
                                                                            tint = Color.Yellow,
                                                                            modifier = Modifier.size(12.dp)
                                                                        )
                                                                        Text(
                                                                            text = stringResource(R.string.player_subtitles_translate_ai),
                                                                            color = Color.White,
                                                                            fontSize = 10.5.sp,
                                                                            fontWeight = FontWeight.Bold
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        if (isDownloadingThis) {
                                                            CircularProgressIndicator(
                                                                modifier = Modifier.size(18.dp),
                                                                strokeWidth = 2.dp,
                                                                color = Color.White
                                                            )
                                                        } else if (isSelected) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = null,
                                                                tint = Color.White,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                        // CỘT 3: Kiểu
                        Column(
                            modifier = Modifier
                                .weight(if (isLandscape) 1.2f else 1.15f)
                                .fillMaxHeight()
                        ) {
                            Text(
                                text = stringResource(R.string.player_subtitles_style_header),
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = if (isLandscape) 13.sp else 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = if (isLandscape) 10.dp else 6.dp)
                            )

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 12.dp else 8.dp)
                            ) {
                                // 1. Độ trễ phụ đề (Offset)
                                item {
                                    Text(
                                        text = stringResource(R.string.player_subtitles_delay_label),
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Nút Giảm (-) có hỗ trợ nhấn giữ tăng tốc
                                        RepeatingIconButton(
                                            onClick = { updateSubOffset(currentSubOffsetMs - 500L) },
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF262A34))
                                                .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.12f)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(34.dp)
                                                .background(Color(0xFF1E222B), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${if (currentSubOffsetMs >= 0) "+" else ""}${String.format(java.util.Locale.US, "%.3f", currentSubOffsetMs / 1000f)}s",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1
                                            )
                                        }

                                        // Nút Tăng (+) có hỗ trợ nhấn giữ tăng tốc
                                        RepeatingIconButton(
                                            onClick = { updateSubOffset(currentSubOffsetMs + 500L) },
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF262A34))
                                                .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.12f)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(5.dp))

                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(30.dp)
                                            .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.04f),
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF262A34),
                                        onClick = { updateSubOffset(0L) }
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(
                                                text = stringResource(R.string.player_subtitles_offset_reset),
                                                color = Color.White.copy(alpha = 0.85f),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                // 1.2. Tự đồng bộ bằng AI (AI Auto-Sync độc lập)
                                item {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(38.dp)
                                            .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.04f),
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isAiSyncing) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary,
                                        onClick = {
                                            if (isAiSyncing) return@Surface
                                            if (currentSubCues.isEmpty()) {
                                                Toast.makeText(context, context.getString(R.string.player_subtitles_no_cues_to_sync), Toast.LENGTH_SHORT).show()
                                                return@Surface
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
                                                                val promptText = "You are an expert subtitle timecode synchronizer. A video is currently at playback time $pos ms. Surrounding subtitles:\n$cueContext\nDetermine the exact offset adjustment (current_position - subtitle_start_time) for the subtitle matching $pos ms, and respond ONLY with: OFFSET:<integer_in_ms> (e.g. OFFSET:1200 or OFFSET:-450)"
                                                                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
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
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isAiSyncing) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = Color.White
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = stringResource(R.string.player_subtitles_ai_sync_loading),
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFFD54F),
                                                    modifier = Modifier.size(17.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = stringResource(R.string.player_subtitles_ai_sync_btn),
                                                    color = Color.White,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }

                                // 1.3. Thuyết minh AI (TTS / Voiceover)
                                item {
                                    Divider(color = Color.White.copy(alpha = 0.12f), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.RecordVoiceOver,
                                                contentDescription = null,
                                                tint = if (isTtsEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f),
                                                modifier = Modifier.size(17.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(R.string.player_tts_title),
                                                color = if (isTtsEnabled) Color.White else Color.White.copy(alpha = 0.8f),
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        Switch(
                                            checked = isTtsEnabled,
                                            onCheckedChange = { enabled ->
                                                if (enabled) {
                                                    if (currentSubCues.isEmpty()) {
                                                        Toast.makeText(context, context.getString(R.string.player_subtitles_no_cues_to_sync), Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                settingsManager.setTtsEnabled(enabled)
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                                uncheckedTrackColor = Color(0xFF262A34)
                                            ),
                                            modifier = Modifier
                                                .scale(0.8f)
                                                .tvFocusableItem(cornerRadius = 14.dp, focusedScale = 1.15f)
                                        )
                                    }

                                    if (isTtsEnabled) {
                                        Spacer(modifier = Modifier.height(6.dp))


                                        // Giọng đọc (Voice)
                                        Text(
                                            text = stringResource(R.string.player_tts_voice_header),
                                            color = Color.White.copy(alpha = 0.65f),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val voiceOptions = com.jkhx.player.core.player.tts.model.TtsVoices.ALL
                                        val activeNormVoice = com.jkhx.player.core.player.tts.model.TtsVoices.normalizeVoiceId(ttsVoice)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            voiceOptions.take(3).forEach { opt ->
                                                val isSelected = activeNormVoice == opt.id
                                                Surface(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(36.dp)
                                                        .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.08f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF262A34),
                                                    onClick = {
                                                        settingsManager.setTtsVoice(opt.id)
                                                    }
                                                ) {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = opt.name,
                                                            color = Color.White,
                                                            fontSize = 11.5.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            voiceOptions.drop(3).forEach { opt ->
                                                val isSelected = activeNormVoice == opt.id
                                                Surface(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(36.dp)
                                                        .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.08f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF262A34),
                                                    onClick = {
                                                        settingsManager.setTtsVoice(opt.id)
                                                    }
                                                ) {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = opt.name,
                                                            color = Color.White,
                                                            fontSize = 11.5.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Âm lượng phim nền (Ducking)
                                        Text(
                                            text = "${stringResource(R.string.player_tts_ducking_header)}: ${(ttsDuckingVolume * 100).toInt()}%",
                                            color = Color.White.copy(alpha = 0.65f),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(0.20f to "20%", 0.35f to "35%", 0.50f to "50%").forEach { (vol, label) ->
                                                val isSelected = kotlin.math.abs(ttsDuckingVolume - vol) < 0.05f
                                                Surface(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(28.dp)
                                                        .tvFocusableItem(cornerRadius = 6.dp, focusedScale = 1.08f),
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF262A34),
                                                    onClick = { settingsManager.setTtsDuckingVolume(vol) }
                                                ) {
                                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = label,
                                                            color = Color.White,
                                                            fontSize = 11.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Tốc độ đọc (Speech rate)
                                        Text(
                                            text = "${stringResource(R.string.player_tts_speech_rate_header)}: ${ttsSpeechRate}x",
                                            color = Color.White.copy(alpha = 0.65f),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(1.0f to "1.0x", 1.15f to "1.15x", 1.25f to "1.25x", 1.4f to "1.4x").forEach { (rate, label) ->
                                                val isSelected = kotlin.math.abs(ttsSpeechRate - rate) < 0.05f
                                                Surface(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(28.dp)
                                                        .tvFocusableItem(cornerRadius = 6.dp, focusedScale = 1.08f),
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF262A34),
                                                    onClick = { settingsManager.setTtsSpeechRate(rate) }
                                                ) {
                                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = label,
                                                            color = Color.White,
                                                            fontSize = 11.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Divider(color = Color.White.copy(alpha = 0.12f), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                                }

                                // 2. Cỡ chữ
                                item {
                                    Text(
                                        text = stringResource(R.string.player_subtitles_font_size_label),
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.12f),
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF262A34),
                                            onClick = {
                                                val newSize = (subtitleTextSize - 2f).coerceAtLeast(12f)
                                                settingsManager.setSubtitleTextSize(newSize)
                                            }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Remove, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp)
                                                .background(Color(0xFF1E222B), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${subtitleTextSize.toInt()}sp",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        Surface(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.12f),
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF262A34),
                                            onClick = {
                                                val newSize = (subtitleTextSize + 2f).coerceAtMost(36f)
                                                settingsManager.setSubtitleTextSize(newSize)
                                            }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }

                                // 3. In đậm
                                item {
                                    Text(
                                        text = stringResource(R.string.player_subtitles_bold_label),
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        modifier = Modifier
                                            .height(34.dp)
                                            .tvFocusableItem(cornerRadius = 17.dp, focusedScale = 1.06f),
                                        shape = RoundedCornerShape(17.dp),
                                        color = if (subtitleIsBold) MaterialTheme.colorScheme.primary else Color(0xFF262A34),
                                        onClick = { settingsManager.setSubtitleIsBold(!subtitleIsBold) }
                                    ) {
                                        Box(modifier = Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                                            Text(
                                                text = if (subtitleIsBold) stringResource(R.string.player_subtitles_btn_on) else stringResource(R.string.player_subtitles_btn_off),
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                // 4. Màu sắc
                                item {
                                    Text(
                                        text = stringResource(R.string.player_subtitles_color_label),
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val textColors = listOf(
                                        0xFFFFFFFF.toInt(),
                                        0xFFFFEB3B.toInt(),
                                        0xFF00E5FF.toInt(),
                                        0xFFFF5252.toInt(),
                                        0xFF00E676.toInt(),
                                        0xFFE040FB.toInt(),
                                        0xFFFF9100.toInt()
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        textColors.forEach { col ->
                                            val isSelected = subtitleTextColor == col
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .background(Color(col), CircleShape)
                                                    .border(
                                                        width = if (isSelected) 2.5.dp else 1.dp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f),
                                                        shape = CircleShape
                                                    )
                                                    .tvFocusableItem(cornerRadius = 13.dp, focusedScale = 1.25f)
                                                    .clickable { settingsManager.setSubtitleTextColor(col) }
                                            )
                                        }
                                    }
                                }

                                // 5. Độ mờ chữ
                                item {
                                    Text(
                                        text = stringResource(R.string.player_subtitles_opacity_label),
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.12f),
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF262A34),
                                            onClick = {
                                                val newOp = (subtitleTextOpacity - 10).coerceAtLeast(20)
                                                settingsManager.setSubtitleTextOpacity(newOp)
                                            }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Remove, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp)
                                                .background(Color(0xFF1E222B), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${subtitleTextOpacity}%",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        Surface(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.12f),
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF262A34),
                                            onClick = {
                                                val newOp = (subtitleTextOpacity + 10).coerceAtMost(100)
                                                settingsManager.setSubtitleTextOpacity(newOp)
                                            }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }

                                // 6. Viền chữ
                                item {
                                    Text(
                                        text = stringResource(R.string.player_subtitles_outline_label),
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        modifier = Modifier
                                            .height(34.dp)
                                            .tvFocusableItem(cornerRadius = 17.dp, focusedScale = 1.06f),
                                        shape = RoundedCornerShape(17.dp),
                                        color = if (subtitleIsOutline) MaterialTheme.colorScheme.primary else Color(0xFF262A34),
                                        onClick = { settingsManager.setSubtitleIsOutline(!subtitleIsOutline) }
                                    ) {
                                        Box(modifier = Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                                            Text(
                                                text = if (subtitleIsOutline) stringResource(R.string.player_subtitles_btn_on) else stringResource(R.string.player_subtitles_btn_off),
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                // 7. Màu viền
                                item {
                                    Text(
                                        text = stringResource(R.string.player_subtitles_outline_color_label),
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val outlineColors = listOf(
                                        0xFF000000.toInt(),
                                        0xFFFFFFFF.toInt(),
                                        0xFF00E5FF.toInt(),
                                        0xFFFF5252.toInt()
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        outlineColors.forEach { col ->
                                            val isSelected = subtitleOutlineColor == col
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .background(Color(col), CircleShape)
                                                    .border(
                                                        width = if (isSelected) 2.5.dp else 1.dp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                                                        shape = CircleShape
                                                    )
                                                    .tvFocusableItem(cornerRadius = 13.dp, focusedScale = 1.25f)
                                                    .clickable { settingsManager.setSubtitleOutlineColor(col) }
                                            )
                                        }
                                    }
                                }

                                // 8. Khoảng cách lề dưới (Bottom Margin - càng tăng càng đẩy lên cao)
                                item {
                                    Text(
                                        text = stringResource(R.string.player_subtitles_bottom_margin_label),
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.player_subtitles_bottom_margin_hint),
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.12f),
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF262A34),
                                            onClick = {
                                                val newMargin = (subtitleBottomMargin - 5).coerceAtLeast(0)
                                                settingsManager.setSubtitleBottomMargin(newMargin)
                                            }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Remove, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp)
                                                .background(Color(0xFF1E222B), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "$subtitleBottomMargin",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        Surface(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.12f),
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF262A34),
                                            onClick = {
                                                val newMargin = (subtitleBottomMargin + 5).coerceAtMost(100)
                                                settingsManager.setSubtitleBottomMargin(newMargin)
                                            }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }

                                // 9. Đặt lại mặc định
                                item {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Button(
                                        onClick = { settingsManager.resetSubtitleStyleDefaults() },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF262A34),
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(36.dp)
                                            .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.04f)
                                    ) {
                                        Text(stringResource(R.string.player_subtitles_reset_defaults), fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

        // 8.1.6 Panel Chọn Luồng Âm Thanh Phong Cách Nuvio (Audio Tracks Sheet)
        AnimatedVisibility(
            visible = showAudioSheet,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.52f))
                    .pointerInput(Unit) {
                        detectTapGestures { showAudioSheet = false }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .then(
                            if (isLandscape) {
                                Modifier
                                    .align(Alignment.Center)
                                    .fillMaxWidth(0.55f)
                                    .fillMaxHeight(0.70f)
                                    .background(Color.Black.copy(alpha = 0.70f), RoundedCornerShape(16.dp))
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 20.dp, vertical = 16.dp)
                            } else {
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.55f)
                                    .background(Color.Black.copy(alpha = 0.70f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                                    .padding(horizontal = 16.dp, vertical = 16.dp)
                            }
                        )
                        .pointerInput(Unit) { detectTapGestures { } }
                ) {
                    LaunchedEffect(showAudioSheet) {
                        if (showAudioSheet) {
                            delay(60)
                            try {
                                audioSheetInitialFocusRequester.requestFocus()
                            } catch (_: Exception) {}
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Header: Tiêu đề "Luồng Âm Thanh" & Nút đóng
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Audiotrack,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.player_audio_title),
                                    color = Color.White,
                                    fontSize = if (isLandscape) 20.sp else 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (audioTracks.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "${audioTracks.size}",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = { showAudioSheet = false },
                                modifier = Modifier
                                    .size(36.dp)
                                    .focusRequester(audioSheetInitialFocusRequester)
                                    .tvFocusableItem(cornerRadius = 18.dp, focusedScale = 1.12f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.common_close),
                                    tint = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }

                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.padding(bottom = 10.dp)
                        )

                        if (audioTracks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Audiotrack,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.35f),
                                        modifier = Modifier.size(46.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = stringResource(R.string.player_audio_auto),
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.player_audio_auto_desc),
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Danh sách các luồng âm thanh chi tiết
                                items(audioTracks) { track ->
                                    val isTrackSelected = if (selectedAudioTrackId != null) selectedAudioTrackId == track.id else track.isSelected
                                    val specs = exoPlayerManager.formatAudioSpecs(track)
                                    val isDubbed = (track.roleFlags and androidx.media3.common.C.ROLE_FLAG_DUB) != 0
                                    val isOriginal = (track.roleFlags and androidx.media3.common.C.ROLE_FLAG_MAIN) != 0

                                    Surface(
                                        onClick = {
                                            selectedAudioTrackId = track.id
                                            exoPlayerManager.setAudioTrack(track)
                                            audioTracks = exoPlayerManager.getAudioTracks()
                                            showAudioSheet = false
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.player_audio_selected_toast, track.label),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isTrackSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color(0xFF1E222B),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (isTrackSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .tvFocusableItem(cornerRadius = 10.dp, focusedScale = 1.03f)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = track.label,
                                                        color = if (isTrackSelected) MaterialTheme.colorScheme.primary else Color.White,
                                                        fontWeight = if (isTrackSelected) FontWeight.Bold else FontWeight.Medium,
                                                        fontSize = 14.5.sp
                                                    )
                                                    if (isDubbed) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Surface(
                                                            color = Color(0xFFFF9800).copy(alpha = 0.22f),
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                text = stringResource(R.string.player_audio_dubbed_badge),
                                                                color = Color(0xFFFFB74D),
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    } else if (isOriginal) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Surface(
                                                            color = Color(0xFF4CAF50).copy(alpha = 0.22f),
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                text = stringResource(R.string.player_audio_original_badge),
                                                                color = Color(0xFF81C784),
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                if (specs.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(3.dp))
                                                    Text(
                                                        text = specs,
                                                        color = Color.White.copy(alpha = 0.65f),
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                            if (isTrackSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
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

        // 8.1.8 Dialog Xác Nhận Tiếp Tục Xem Hoặc Xem Từ Đầu
        if (showResumePromptDialog && resumeTargetPositionMs > 0L) {
            AlertDialog(
                onDismissRequest = { showResumePromptDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier.fillMaxWidth(if (isLandscape) 0.55f else 0.9f),
                containerColor = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(16.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.player_resume_dialog_title),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }
                },
                text = {
                    Text(
                        text = stringResource(R.string.player_resume_dialog_msg, formatTime(resumeTargetPositionMs)),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showResumePromptDialog = false
                            exoPlayerManager.seekTo(resumeTargetPositionMs)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.player_resume_continue, formatTime(resumeTargetPositionMs)),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            showResumePromptDialog = false
                            exoPlayerManager.seekTo(0L)
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White.copy(alpha = 0.8f)
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.player_resume_restart),
                            fontSize = 13.sp
                        )
                    }
                }
            )
        }


        // 8.2 Panel Chọn Nguồn Phát & Server (Right Side Drawer) - Phong cách Nuvio đồng nhất (Hình 3)
        AnimatedVisibility(
            visible = showSourceSelectorDrawer,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.20f))
                    .pointerInput(Unit) {
                        detectTapGestures { showSourceSelectorDrawer = false }
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
                    val effectiveDrawerStreams = remember(drawerStreams, activeUnifiedStream, activeProviderId, activeServerName, resolvedStream, currentEpisode, movieName) {
                        val baseList = if (drawerStreams.isNotEmpty()) {
                            if (activeUnifiedStream != null && drawerStreams.none { it.id == activeUnifiedStream?.id || (it.providerId == activeUnifiedStream?.providerId && it.streamUrl == activeUnifiedStream?.streamUrl) }) {
                                listOf(activeUnifiedStream!!) + drawerStreams
                            } else {
                                drawerStreams
                            }
                        } else if (activeUnifiedStream != null) {
                            listOf(activeUnifiedStream!!)
                        } else {
                            val pName = pluginManager.getPluginName(activeProviderId)
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

                    LaunchedEffect(showSourceSelectorDrawer) {
                        if (showSourceSelectorDrawer) {
                            delay(60)
                            try {
                                sourceSelectorInitialFocusRequester.requestFocus()
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
                                    .focusRequester(sourceSelectorInitialFocusRequester)
                                    .tvFocusableItem(cornerRadius = 16.dp, focusedScale = 1.08f),
                                onClick = { showSourceSelectorDrawer = false }
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
                                    .clickable(enabled = !isScanningSources) { refreshSourcesKey++ }
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
                                    onClick = {
                                        switchToUnifiedStream(item)
                                    }
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

        // 8.3 Panel Danh Sách Tập Phim (Right Side Drawer cho Phim Bộ)
        AnimatedVisibility(
            visible = showEpisodesDrawer,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .pointerInput(Unit) {
                        detectTapGestures { showEpisodesDrawer = false }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(360.dp)
                        .background(
                            Color.Black.copy(alpha = 0.75f),
                            RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                        )
                        .pointerInput(Unit) {
                            detectTapGestures { }
                        }
                        .padding(20.dp)
                ) {
                    LaunchedEffect(showEpisodesDrawer) {
                        if (showEpisodesDrawer) {
                            delay(60)
                            try {
                                episodesDrawerInitialFocusRequester.requestFocus()
                            } catch (_: Exception) {}
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header: Title + Close Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.player_episodes_sheet_title),
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = { showEpisodesDrawer = false },
                                modifier = Modifier
                                    .focusRequester(episodesDrawerInitialFocusRequester)
                                    .tvFocusableItem(cornerRadius = 18.dp, focusedScale = 1.12f)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close), tint = Color.White.copy(alpha = 0.8f))
                            }
                        }

                        // Info row: Movie Name & Episode Count
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = movieName,
                                color = Color.Gray,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.player_episodes_count, activeEpisodes.size),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Server tabs (nếu có nhiều server trong availableServers)
                        if (availableServers.size > 1) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp),
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
                                                activeServerName = srv.serverName
                                                activeEpisodes = srv.episodes
                                                val matchedIdx = srv.episodes.indexOfFirst { it.number == currentEpisode.number }.takeIf { it >= 0 } ?: 0
                                                currentEpisodeIndex = matchedIdx
                                                ActivePlayerSession.currentServerName = srv.serverName
                                                ActivePlayerSession.episodes = srv.episodes
                                                ActivePlayerSession.currentEpisodeIndex = matchedIdx
                                                ActivePlayerSession.currentEpisode = srv.episodes.getOrNull(matchedIdx) ?: currentEpisode
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

                        HorizontalDivider(color = Color.White.copy(alpha = 0.12f), modifier = Modifier.padding(bottom = 12.dp))

                        // Grid danh sách các tập phim
                        val episodeListState = rememberLazyGridState()
                        LaunchedEffect(showEpisodesDrawer) {
                            if (showEpisodesDrawer && currentEpisodeIndex >= 0) {
                                try {
                                    episodeListState.scrollToItem(currentEpisodeIndex.coerceAtLeast(0))
                                } catch (_: Exception) {}
                            }
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 64.dp),
                            state = episodeListState,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(activeEpisodes, key = { idx, ep -> "${ep.id}_$idx" }) { idx, ep ->
                                val isCurrent = idx == currentEpisodeIndex
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f),
                                    border = BorderStroke(
                                        width = if (isCurrent) 1.5.dp else 1.dp,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .tvFocusableItem(cornerRadius = 10.dp, focusedScale = 1.08f)
                                        .clickable {
                                            if (idx != currentEpisodeIndex) {
                                                showResumePromptDialog = false
                                                resumeTargetPositionMs = 0L
                                                currentEpisodeIndex = idx
                                                ActivePlayerSession.currentEpisodeIndex = idx
                                                ActivePlayerSession.currentEpisode = ep
                                                showEpisodesDrawer = false
                                            }
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = ep.name.ifBlank { "Tập ${idx + 1}" },
                                            color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else Color.White,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isCurrent) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = stringResource(R.string.player_episodes_playing),
                                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.SemiBold
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

        // 9. Thông báo lỗi nếu có
        if (errorMessage != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = errorMessage ?: "",
                    color = Color.White,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                val embedFallback = getEffectiveEmbedFallback()
                if (!embedFallback.isNullOrEmpty()) {
                    Button(
                        onClick = {
                            errorMessage = null
                            webViewUrl = embedFallback
                            webViewHeaders = embedHeadersFor(embedFallback, resolvedStream?.headers)
                            isWebViewMode = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.player_switch_to_webview))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            errorMessage = null
                            reloadStreamTrigger++
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.common_retry))
                    }

                    OutlinedButton(onClick = onBack) {
                        Text(stringResource(R.string.common_back), color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsOptionCard(
    title: String,
    subtitle: String? = null,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .tvFocusableItem(cornerRadius = 12.dp, focusedScale = 1.02f),
        shape = ShapeTokens.Card,
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
