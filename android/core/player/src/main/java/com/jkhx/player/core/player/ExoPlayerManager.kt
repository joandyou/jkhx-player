package com.jkhx.player.core.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.jkhx.player.core.model.EpisodeStream
import com.jkhx.player.core.model.PlayerError
import com.jkhx.player.core.model.Subtitle
import com.jkhx.player.core.network.HttpBridgeClient
import okhttp3.OkHttpClient
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit

import okhttp3.ResponseBody
import okio.BufferedSource
import okio.ByteString

data class VideoTrackOption(
    val id: String,
    val label: String,
    val width: Int,
    val height: Int,
    val bitrate: Int,
    val isSelected: Boolean,
    val groupIndex: Int,
    val trackIndex: Int
)

data class SubtitleTrackOption(
    val id: String,
    val label: String,
    val language: String?,
    val isSelected: Boolean,
    val groupIndex: Int,
    val trackIndex: Int
)

class ExoPlayerManager(
    private val context: Context
) {
    companion object {
        const val DEFAULT_ANDROID_UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        private val PNG_MAGIC = ByteString.of(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte())
        private val IEND_MAGIC = ByteString.of(0x49.toByte(), 0x45.toByte(), 0x4E.toByte(), 0x44.toByte())
    }

    private val okHttpClient: OkHttpClient = run {
        val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
            object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            }
        )
        val sslSocketFactory = try {
            val sslContext = javax.net.ssl.SSLContext.getInstance("TLS").apply {
                init(null, trustAllCerts, java.security.SecureRandom())
            }
            sslContext.socketFactory
        } catch (_: Exception) {
            null
        }

        val builder = OkHttpClient.Builder()
            .hostnameVerifier { _, _ -> true }
            .dns(object : okhttp3.Dns {
                override fun lookup(hostname: String): List<java.net.InetAddress> =
                    okhttp3.Dns.SYSTEM.lookup(hostname).sortedBy { it is java.net.Inet6Address }
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .proxySelector(object : ProxySelector() {
                override fun select(uri: URI?): List<Proxy> {
                    val proxy = HttpBridgeClient.globalProxyProvider?.invoke()
                    return if (proxy != null) listOf(proxy) else listOf(Proxy.NO_PROXY)
                }
                override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: java.io.IOException?) {}
            })

        if (sslSocketFactory != null) {
            builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
        }

        builder.addInterceptor { chain ->
            val origReq = chain.request()
            val origBuilder = origReq.newBuilder()
            var modified = false

            // Đảm bảo không bao giờ bị đúp User-Agent ở tầng OkHttp network
            val uaList = origReq.headers("User-Agent")
            if (uaList.size > 1) {
                origBuilder.removeHeader("User-Agent")
                origBuilder.header("User-Agent", uaList.last())
                modified = true
            }

            val request = if (modified) origBuilder.build() else origReq
            val response = chain.proceed(request)
            val body = response.body
            if (response.isSuccessful && body != null) {
                val contentType = response.header("Content-Type")?.lowercase() ?: ""
                val reqUrl = request.url.toString().lowercase()
                val isMediaUrl = reqUrl.contains(".m3u8") || reqUrl.contains(".ts") || reqUrl.contains("/s/") || reqUrl.contains("/m/") || reqUrl.contains("/hls")
                if (isMediaUrl && contentType.contains("text/html")) {
                    throw java.io.IOException("Máy chủ video trả về nội dung HTML (Cloudflare WAF / Anti-bot) thay vì dữ liệu luồng video")
                }

                val source = body.source()
                // Phát hiện phân đoạn media bị ngụy trang bằng header ảnh PNG để chống leech
                if (source.request(8) && source.buffer.rangeEquals(0, PNG_MAGIC)) {
                    if (source.request(128)) {
                        val iendIdx = source.buffer.indexOf(IEND_MAGIC)
                        if (iendIdx in 0..128) {
                            val skipBytes = iendIdx + 8 // 4 bytes 'IEND' + 4 bytes CRC
                            source.skip(skipBytes)
                            val originalLength = body.contentLength()
                            val newLength = if (originalLength >= skipBytes) originalLength - skipBytes else -1L
                            val unmaskedBody = object : ResponseBody() {
                                override fun contentType() = body.contentType()
                                override fun contentLength() = newLength
                                override fun source(): BufferedSource = source
                            }
                            return@addInterceptor response.newBuilder().body(unmaskedBody).build()
                        }
                    }
                }
            }
            response
        }
        .build()
    }
    private var exoPlayer: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null

    val player: ExoPlayer
        get() = exoPlayer ?: initializePlayer()

    fun initializePlayer(): ExoPlayer {
        if (exoPlayer == null) {
            trackSelector = DefaultTrackSelector(context).apply {
                parameters = buildUponParameters()
                    .setPreferredTextLanguage("vi")
                    .setSelectUndeterminedTextLanguage(true)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .build()
            }
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 30_000,
                    /* maxBufferMs = */ 60_000,
                    /* bufferForPlaybackMs = */ 2_500,
                    /* bufferForPlaybackAfterRebufferMs = */ 5_000
                )
                .setBackBuffer(
                    /* backBufferDurationMs = */ 30_000,
                    /* retainBackBufferFromKeyframe = */ true
                )
                .build()

            exoPlayer = ExoPlayer.Builder(context)
                .setTrackSelector(trackSelector!!)
                .setLoadControl(loadControl)
                .build().apply {
                    playWhenReady = true
                    repeatMode = Player.REPEAT_MODE_OFF
                }
        }
        return exoPlayer!!
    }

    private var currentStream: EpisodeStream? = null
    private val dynamicSubtitles = mutableListOf<Subtitle>()
    private var currentMediaSourceFactory: MediaSource.Factory? = null
    var subtitleOffsetMs: Long = 0L

    fun prepareStream(stream: EpisodeStream, startPositionMs: Long = 0L) {
        val player = initializePlayer()
        player.stop()
        currentStream = stream
        dynamicSubtitles.clear()
        subtitleOffsetMs = 0L

        // Configure custom DataSourceFactory with spoofed headers (Referer, User-Agent, Origin, Cookie)
        // Wrapped in DefaultDataSource.Factory to support data:, file:, and http(s): streams
        val requestHeaders = HashMap(stream.headers)
        val streamUrlStr = stream.url
        if (!requestHeaders.containsKey("Cookie") && (streamUrlStr.startsWith("http://") || streamUrlStr.startsWith("https://"))) {
            try {
                val cookieManager = android.webkit.CookieManager.getInstance()
                val cookies = cookieManager.getCookie(streamUrlStr)
                    ?: requestHeaders["Referer"]?.let { cookieManager.getCookie(it) }
                if (!cookies.isNullOrBlank()) {
                    requestHeaders["Cookie"] = cookies
                }
            } catch (_: Exception) {}
        }

        // Lấy User-Agent từ stream headers (nếu addon chỉ định) hoặc dùng User-Agent chuẩn di động Android
        val customUserAgent = requestHeaders.entries
            .firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
            ?.value

        val finalUserAgent = if (!customUserAgent.isNullOrBlank()) {
            customUserAgent
        } else {
            DEFAULT_ANDROID_UA
        }

        // BẮT BUỘC: Xóa tất cả các biến thể của "User-Agent" khỏi requestHeaders
        // Lý do: OkHttpDataSource trong Media3 khi có cả userAgent != null và key "User-Agent" trong defaultRequestProperties
        // sẽ gọi requestBuilder.addHeader("User-Agent", ...) khiến HTTP request bị gửi ĐÚP 2 lần User-Agent!
        // Cloudflare sẽ chặn ngay lập tức với lỗi HTTP 400 (Duplicate 'User-Agent' header found) hoặc trả về 404 HTML.
        val uaKeysToRemove = requestHeaders.keys.filter { it.equals("User-Agent", ignoreCase = true) }.toList()
        for (k in uaKeysToRemove) {
            requestHeaders.remove(k)
        }

        // Tự động suy diễn header Origin nếu có Referer mà chưa có Origin
        val referer = requestHeaders.entries.firstOrNull { it.key.equals("Referer", ignoreCase = true) }?.value
        val hasOrigin = requestHeaders.keys.any { it.equals("Origin", ignoreCase = true) }
        if (!hasOrigin && !referer.isNullOrBlank()) {
            try {
                val refUri = Uri.parse(referer)
                val scheme = refUri.scheme ?: "https"
                val host = refUri.host
                if (!host.isNullOrBlank()) {
                    val portStr = if (refUri.port > 0 && refUri.port != 80 && refUri.port != 443) ":${refUri.port}" else ""
                    requestHeaders["Origin"] = "$scheme://$host$portStr"
                }
            } catch (_: Exception) {}
        }

        if (!requestHeaders.containsKey("Accept")) {
            requestHeaders["Accept"] = "*/*"
        }
        if (!requestHeaders.containsKey("Accept-Language")) {
            requestHeaders["Accept-Language"] = "vi,en-US;q=0.9,en;q=0.8"
        }

        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient).apply {
            setUserAgent(finalUserAgent)
            setDefaultRequestProperties(requestHeaders)
        }

        val upstreamDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
            )
            .setTsExtractorTimestampSearchBytes(200_000 * 188)

        val uri = Uri.parse(stream.url)
        val mediaItemBuilder = MediaItem.Builder().setUri(uri)

        // Set mime type according to stream type
        val lowerType = stream.type.lowercase()
        val lowerUrl = stream.url.lowercase()
        val hasBase64M3u8 = stream.url.contains("wbGF5bGlzdC5tM3U4") ||
            stream.url.contains("cGxheWxpc3QubTN1OA") ||
            stream.url.contains("Lm0zdTg")
        val isHls = lowerType in listOf("m3u8", "hls") ||
            lowerUrl.contains(".m3u8") ||
            lowerUrl.contains("/hls") ||
            lowerUrl.contains("format=m3u8") ||
            lowerUrl.contains("type=m3u8") ||
            hasBase64M3u8
        val isDash = lowerType in listOf("dash", "mpd") || lowerUrl.contains(".mpd")

        when {
            isHls -> {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }
            isDash -> {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            }
            lowerType == "flv" || lowerUrl.contains(".flv") -> {
                mediaItemBuilder.setMimeType(MimeTypes.VIDEO_FLV)
            }
            // Không ép MIME type cho direct video (MKV/MP4/TS/AVI/WEBM).
            // Các Debrid (PikPak/AIOStreams) có thể đặt đuôi .mkv nhưng trả về stream MPEG-TS.
            // Để DefaultExtractorsFactory tự động sniff magic header (0x47, EBML, ftyp) để hỗ trợ seek chuẩn xác.
        }

        val mediaSourceFactory: MediaSource.Factory = DefaultMediaSourceFactory(context, extractorsFactory)
            .setDataSourceFactory(upstreamDataSourceFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(3))

        currentMediaSourceFactory = mediaSourceFactory

        // Configure external subtitles (WebVTT, SRT)
        val subtitleConfigs = stream.subtitles.map { sub ->
            val isVietnamese = sub.language.equals("vi", ignoreCase = true) ||
                    sub.label.contains("việt", ignoreCase = true) ||
                    sub.label.contains("viet", ignoreCase = true)
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                .setId(sub.url)
                .setMimeType(if (sub.format.contains("srt", ignoreCase = true)) MimeTypes.APPLICATION_SUBRIP else MimeTypes.TEXT_VTT)
                .setLanguage(sub.language.ifBlank { "vi" })
                .setLabel(sub.label)
                .setSelectionFlags(if (isVietnamese) (C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED) else C.SELECTION_FLAG_DEFAULT)
                .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
                .build()
        }
        if (subtitleConfigs.isNotEmpty()) {
            mediaItemBuilder.setSubtitleConfigurations(subtitleConfigs)
            // Kích hoạt text track và ưu tiên tiếng Việt
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage("vi")
                .setSelectUndeterminedTextLanguage(true)
                .build()
        }

        val mediaSource = mediaSourceFactory.createMediaSource(mediaItemBuilder.build())
        // Luôn reset position về vị trí startPositionMs để tránh bị nhảy tiếp thời gian của tập trước
        player.setMediaSource(mediaSource, /* resetPosition = */ true)
        if (startPositionMs > 0L) {
            player.seekTo(startPositionMs)
        }
        player.prepare()
        player.play()
    }

    /**
     * Nạp phụ đề động từ file ngoài (tải online hoặc dịch AI) vào luồng phát hiện tại
     */
    fun addDynamicSubtitle(
        file: java.io.File,
        label: String,
        language: String = "vi",
        autoSelect: Boolean = true
    ) {
        val player = exoPlayer ?: return
        val stream = currentStream ?: return
        val factory = currentMediaSourceFactory ?: return

        val newSub = Subtitle(
            label = label,
            language = language,
            url = Uri.fromFile(file).toString(),
            format = if (file.name.endsWith(".vtt", ignoreCase = true)) "vtt" else "srt"
        )

        dynamicSubtitles.removeAll { it.url == newSub.url || it.label == newSub.label }
        dynamicSubtitles.add(newSub)

        val allSubs = stream.subtitles + dynamicSubtitles
        val subtitleConfigs = allSubs.map { sub ->
            val isVietnamese = sub.language.equals("vi", ignoreCase = true) ||
                    sub.label.contains("việt", ignoreCase = true) ||
                    sub.label.contains("viet", ignoreCase = true)
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                .setId(sub.url)
                .setMimeType(if (sub.format.contains("srt", ignoreCase = true)) MimeTypes.APPLICATION_SUBRIP else MimeTypes.TEXT_VTT)
                .setLanguage(sub.language.ifBlank { "vi" })
                .setLabel(sub.label)
                .setSelectionFlags(if (isVietnamese) (C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED) else C.SELECTION_FLAG_DEFAULT)
                .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
                .build()
        }

        val currentItem = player.currentMediaItem ?: return
        val updatedItem = currentItem.buildUpon()
            .setSubtitleConfigurations(subtitleConfigs)
            .build()

        val pos = player.currentPosition
        val isPlaying = player.playWhenReady
        val mediaSource = factory.createMediaSource(updatedItem)
        player.setMediaSource(mediaSource, /* resetPosition = */ false)
        player.seekTo(pos)
        player.playWhenReady = isPlaying

        if (autoSelect) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage(language)
                .build()
        }
    }

    fun getDynamicSubtitles(): List<Subtitle> = dynamicSubtitles.toList()

    fun setPlaybackSpeed(speed: Float) {
        val currentPitch = exoPlayer?.playbackParameters?.pitch ?: 1.0f
        exoPlayer?.playbackParameters = PlaybackParameters(speed, currentPitch)
    }

    fun getVideoTracks(): List<VideoTrackOption> {
        val currentTracks = exoPlayer?.currentTracks ?: return emptyList()
        val list = mutableListOf<VideoTrackOption>()
        var gIdx = 0
        for (group in currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_VIDEO) {
                for (tIdx in 0 until group.length) {
                    val format = group.getTrackFormat(tIdx)
                    val width = format.width
                    val height = format.height
                    val bitrate = format.bitrate
                    val label = when {
                        height > 0 -> "${height}p" + (if (bitrate > 0) " (${bitrate / 1000} kbps)" else "")
                        format.label != null -> format.label!!
                        else -> "Video Track ${tIdx + 1}"
                    }
                    val isSelected = group.isTrackSelected(tIdx)
                    list.add(
                        VideoTrackOption(
                            id = format.id ?: "$gIdx:$tIdx",
                            label = label,
                            width = width,
                            height = height,
                            bitrate = bitrate,
                            isSelected = isSelected,
                            groupIndex = gIdx,
                            trackIndex = tIdx
                        )
                    )
                }
            }
            gIdx++
        }
        return list
    }

    fun setVideoTrack(option: VideoTrackOption?) {
        val player = exoPlayer ?: return
        val currentTracks = player.currentTracks
        if (option == null) {
            // Auto: Xóa override chất lượng video
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .build()
        } else {
            var gIdx = 0
            for (group in currentTracks.groups) {
                if (group.type == C.TRACK_TYPE_VIDEO && gIdx == option.groupIndex) {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(
                            TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex)
                        )
                        .build()
                    break
                }
                gIdx++
            }
        }
    }

    fun getSubtitleTracks(): List<SubtitleTrackOption> {
        val currentTracks = exoPlayer?.currentTracks ?: return emptyList()
        val list = mutableListOf<SubtitleTrackOption>()
        var gIdx = 0
        for (group in currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (tIdx in 0 until group.length) {
                    val format = group.getTrackFormat(tIdx)
                    // Bỏ qua các track rỗng không có label và không có language (triệt tiêu "Phụ đề 1")
                    if (format.label == null && format.language == null) {
                        continue
                    }

                    val lang = format.language
                    val label = format.label ?: if (!lang.isNullOrBlank()) {
                        when (lang.lowercase()) {
                            "vi", "vie", "vietnamese" -> "Tiếng Việt"
                            "en", "eng", "english" -> "Tiếng Anh"
                            "zh", "chi", "zho", "chinese" -> "Tiếng Trung"
                            "ko", "kor", "korean" -> "Tiếng Hàn"
                            "ja", "jpn", "japanese" -> "Tiếng Nhật"
                            "fr", "fra", "french" -> "Tiếng Pháp"
                            "es", "spa", "spanish" -> "Tiếng Tây Ban Nha"
                            "ru", "rus", "russian" -> "Tiếng Nga"
                            "th", "tha", "thai" -> "Tiếng Thái"
                            else -> lang.uppercase()
                        }
                    } else "Phụ đề ${tIdx + 1}"

                    val isSelected = group.isTrackSelected(tIdx)
                    list.add(
                        SubtitleTrackOption(
                            id = format.id ?: "$gIdx:$tIdx",
                            label = label,
                            language = format.language,
                            isSelected = isSelected,
                            groupIndex = gIdx,
                            trackIndex = tIdx
                        )
                    )
                }
            }
            gIdx++
        }
        return list
    }

    fun setSubtitleTrack(option: SubtitleTrackOption?) {
        val player = exoPlayer ?: return
        val currentTracks = player.currentTracks
        if (option == null) {
            // Tắt phụ đề
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .setPreferredTextLanguage(null)
                .setPreferredTextRoleFlags(0)
                .build()
        } else {
            var gIdx = 0
            for (group in currentTracks.groups) {
                if (group.type == C.TRACK_TYPE_TEXT && gIdx == option.groupIndex) {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(
                            TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex)
                        )
                        .build()
                    break
                }
                gIdx++
            }
        }
    }

    fun selectDefaultSubtitleTrack() {
        val player = exoPlayer ?: return
        val currentTracks = player.currentTracks
        var gIdx = 0
        var foundOption: SubtitleTrackOption? = null
        for (group in currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (tIdx in 0 until group.length) {
                    val format = group.getTrackFormat(tIdx)
                    val label = format.label ?: format.language ?: "Phụ đề"
                    val lang = format.language ?: ""
                    val isVi = lang.equals("vi", ignoreCase = true) ||
                            label.contains("việt", ignoreCase = true) ||
                            label.contains("viet", ignoreCase = true)
                    val opt = SubtitleTrackOption(
                        id = format.id ?: "$gIdx:$tIdx",
                        label = label,
                        language = format.language,
                        isSelected = group.isTrackSelected(tIdx),
                        groupIndex = gIdx,
                        trackIndex = tIdx
                    )
                    if (isVi) {
                        foundOption = opt
                        break
                    }
                    if (foundOption == null) {
                        foundOption = opt
                    }
                }
            }
            if (foundOption != null && (foundOption.language == "vi" || foundOption.label.contains("việt", ignoreCase = true))) {
                break
            }
            gIdx++
        }
        if (foundOption != null) {
            setSubtitleTrack(foundOption)
        }
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun play() {
        exoPlayer?.play()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    fun release() {
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
        trackSelector = null
    }
}

