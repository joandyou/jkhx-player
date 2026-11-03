package com.jkhx.player.ui.screens.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.util.TypedValue
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.jkhx.player.R
import com.jkhx.player.core.model.EpisodeServer
import com.jkhx.player.core.player.SubtitleTrackOption
import com.jkhx.player.core.player.subtitle.model.SubtitleCue
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AvailablePluginSource(
    val pluginId: String,
    val pluginName: String,
    val movieSlug: String,
    val movieTitle: String,
    val servers: List<EpisodeServer>,
    val isMatchedByTmdb: Boolean,
    val isCurrent: Boolean
)

internal const val DEFAULT_WEB_UA =
    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

internal fun embedHeadersFor(url: String, customHeaders: Map<String, String>? = null): Map<String, String> {
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

internal fun buildHtml5HlsPlayerPage(streamUrl: String, referer: String): String {
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

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun formatTime(ms: Long): String {
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

internal fun parseDurationStringToMs(durationStr: String?): Long {
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

internal fun applySubtitleAppearance(
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

internal fun isVietnameseTrack(track: SubtitleTrackOption): Boolean {
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

internal fun isEnglishTrack(track: SubtitleTrackOption): Boolean {
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

internal fun mergeSubtitleTracks(
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

internal fun cleanTitleForSubtitleSearch(raw: String): String {
    return raw
        .replace(Regex("""\s*[\(\[\{].*?[\)\]\}]"""), " ")
        .replace(Regex("""[._\s]+(\d{4}|\d{3,4}p|REPACK|UHD|Blu-?ray|Remux|WEB-?DL|WEBRip|HDR\d*|DV|HEVC|x264|x265|TrueHD|Atmos|3D|2D|IMAX|Vietsub|Thuyết\s*Minh|Lồng\s*Tiếng|Cam|HD|FHD).*""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\b(3D|2D|IMAX|4K|UHD|FHD|HD|Vietsub|Thuyết Minh|Lồng Tiếng)\b""", RegexOption.IGNORE_CASE), "")
        .replace('.', ' ')
        .trim()
}

internal fun buildStremioVideoId(
    imdbId: String?,
    tmdbId: String?,
    isSeries: Boolean,
    episodeId: String,
    episodeName: String,
    episodeNumber: Int?
): String? {
    val epMatchFromId = Regex(""":(\d+):(\d+)$""").find(episodeId)
    val epMatchFromName = Regex("""S(\d+)\s*[•\-:]?\s*E(\d+)""", RegexOption.IGNORE_CASE).find(episodeName)
    val seasonNum = epMatchFromId?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: epMatchFromName?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: 1
    val epNum = epMatchFromId?.groupValues?.getOrNull(2)?.toIntOrNull()
        ?: epMatchFromName?.groupValues?.getOrNull(2)?.toIntOrNull()
        ?: episodeNumber
        ?: Regex("""(\d+)""").find(episodeName)?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: 1

    return when {
        !imdbId.isNullOrBlank() -> if (isSeries) "$imdbId:$seasonNum:$epNum" else imdbId
        !tmdbId.isNullOrBlank() -> if (isSeries) "tmdb:$tmdbId:$seasonNum:$epNum" else "tmdb:$tmdbId"
        else -> null
    }
}

internal fun performAiSubtitleSync(
    pos: Long,
    cues: List<SubtitleCue>,
    currentSubOffsetMs: Long,
    apiKey: String
): Long? {
    val currentEffective = pos - currentSubOffsetMs
    val sortedCues = cues.sortedBy { kotlin.math.abs(it.startTimeMs - currentEffective) }.take(6)
    var calculatedOffset: Long? = null

    if (apiKey.isNotBlank()) {
        try {
            val cueContext = sortedCues.joinToString("\n") { "[${it.startTimeMs}ms - ${it.endTimeMs}ms]: ${it.text.replace("\n", " ")}" }
            val promptText = "You are an expert subtitle timecode synchronizer. A video is currently at playback time " + pos + " ms. Surrounding subtitles:\n" + cueContext + "\nDetermine the exact offset adjustment (current_position - subtitle_start_time) for the subtitle matching " + pos + " ms, and respond ONLY with: OFFSET:<integer_in_ms> (e.g. OFFSET:1200 or OFFSET:-450)"
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey
            val reqJson = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", promptText)
                    }))
                }))
            }
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 4000
                readTimeout = 4000
                doOutput = true
            }
            conn.outputStream.use { it.write(reqJson.toString().toByteArray()) }
            if (conn.responseCode in 200..299) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val match = Regex("""OFFSET:\s*([+-]?\d+)""").find(resp)
                if (match != null) {
                    calculatedOffset = match.groupValues[1].toLongOrNull()
                }
            }
        } catch (e: Exception) {
            println("[AI Sub Sync] Gemini API fallback: ${e.message}")
        }
    }

    if (calculatedOffset == null) {
        val nearestCue = sortedCues.firstOrNull() ?: cues.minByOrNull { kotlin.math.abs(it.startTimeMs - currentEffective) }
        if (nearestCue != null) {
            calculatedOffset = pos - nearestCue.startTimeMs
        }
    }
    return calculatedOffset
}

fun copyStreamUrlToClipboard(context: Context, rawUrl: String) {
    val cleanUrl = rawUrl.substringBefore("#").substringBefore("|").ifBlank { rawUrl }
    if (cleanUrl.isNotBlank()) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Stream Link", cleanUrl))
        android.widget.Toast.makeText(context, context.getString(com.jkhx.player.R.string.stream_copied_toast), android.widget.Toast.LENGTH_SHORT).show()
    }
}

fun extractEmbedFromEpisodeData(episode: com.jkhx.player.core.model.EpisodeData): String? {
    return try {
        val dataObj = episode.data as? kotlinx.serialization.json.JsonObject
        (dataObj?.get("embed") as? kotlinx.serialization.json.JsonPrimitive)?.content
    } catch (_: Exception) {
        null
    }
}

fun computeEffectiveEmbedFallback(
    rawEmbedUrl: String?,
    fallbackEmbedFromData: String?,
    stream: com.jkhx.player.core.model.EpisodeStream?
): String? {
    if (!rawEmbedUrl.isNullOrBlank()) return rawEmbedUrl
    if (!fallbackEmbedFromData.isNullOrBlank()) return fallbackEmbedFromData
    val s = stream ?: return null
    val isKkphimEmbed = s.url.contains("player.phimapi.com/player/?url=")
    val isDirectMedia = (s.url.contains(".m3u8", true) || s.url.contains(".mp4", true) || s.url.contains(".mkv", true)) && !isKkphimEmbed
    return if (s.type.equals("embed", true) && !isDirectMedia) s.url else null
}

suspend fun fetchSubtitleCuesFromUrl(
    url: String,
    headers: Map<String, String> = emptyMap()
): List<SubtitleCue> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val client = com.jkhx.player.JkhxApplication.instance.okHttpClient
        val reqBuilder = okhttp3.Request.Builder().url(url)
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }
        if (!headers.containsKey("User-Agent")) {
            reqBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        }
        val response = client.newCall(reqBuilder.build()).execute()
        if (response.isSuccessful) {
            val bodyStr = response.body?.string().orEmpty()
            if (bodyStr.isNotBlank()) {
                return@withContext com.jkhx.player.core.player.subtitle.parser.SrtParser.parse(bodyStr)
            }
        }
    } catch (_: Exception) {}
    emptyList()
}

fun formatPlaybackErrorMessage(context: Context, error: androidx.media3.common.PlaybackException): String {
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
    return context.getString(R.string.player_error_format, error.errorCodeName, detailedMessage)
}

fun formatP2pBufferingStats(p2pStats: com.jkhx.player.core.model.stremio.StremioTorrentStats): String {
    val downloaded = p2pStats.downloadedFormatted
    val peers = p2pStats.peers
    val speed = p2pStats.downloadSpeedFormatted
    return if (downloaded.isNotBlank() && downloaded != "0 B") {
        "$downloaded đã tải xuống • $peers peer • $speed"
    } else {
        "P2P: $peers peers • $speed"
    }
}

fun computeP2pInitialProgress(downloadedBytes: Long, targetBytes: Long = 15L * 1024L * 1024L): Float {
    if (downloadedBytes <= 0L) return 0f
    return (downloadedBytes.toFloat() / targetBytes.toFloat()).coerceIn(0.05f, 0.95f)
}

suspend fun resolveTorrentStreamUrl(
    stream: com.jkhx.player.core.model.EpisodeStream,
    activeUnifiedStream: com.jkhx.player.ui.screens.stream.UnifiedStreamItem?,
    settingsManager: com.jkhx.player.settings.SettingsManager,
    stremioClient: com.jkhx.player.core.network.stremio.StremioBridgeClient
): com.jkhx.player.core.model.EpisodeStream {
    val torrentHash = stream.torrentInfoHash ?: activeUnifiedStream?.torrentInfoHash
    if (torrentHash.isNullOrBlank()) return stream
    val srv = stream.stremioServerUrl ?: activeUnifiedStream?.stremioServerUrl
        ?: settingsManager.stremioActiveServerUrl.value.trim().trimEnd('/')
    if (srv.isBlank()) return stream
    val realIdx = stremioClient.resolveTorrentFileIdx(srv, torrentHash)
    return stream.copy(
        url = "$srv/$torrentHash/$realIdx",
        stremioServerUrl = srv,
        torrentInfoHash = torrentHash
    )
}



