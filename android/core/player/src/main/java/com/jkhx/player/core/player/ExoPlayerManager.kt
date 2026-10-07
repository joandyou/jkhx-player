package com.jkhx.player.core.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.jkhx.player.core.model.EpisodeStream
import com.jkhx.player.core.model.PlayerError
import okhttp3.OkHttpClient

class ExoPlayerManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient = OkHttpClient()
) {
    private var exoPlayer: ExoPlayer? = null

    val player: ExoPlayer
        get() = exoPlayer ?: throw IllegalStateException("Player not initialized")

    fun initializePlayer(): ExoPlayer {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
            }
        }
        return exoPlayer!!
    }

    fun prepareStream(stream: EpisodeStream, startPositionMs: Long = 0L) {
        val player = initializePlayer()

        // Configure custom DataSourceFactory with spoofed headers (Referer, User-Agent, Origin)
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient).apply {
            setUserAgent(stream.headers["User-Agent"] ?: "Mozilla/5.0 (Linux; Android 14) JKHXPlayer/1.0")
            setDefaultRequestProperties(stream.headers)
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

        val uri = Uri.parse(stream.url)
        val mediaItemBuilder = MediaItem.Builder().setUri(uri)

        // Set mime type according to stream type
        when (stream.type.lowercase()) {
            "m3u8" -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            "dash" -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            "mp4" -> mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP4)
        }

        // Configure external subtitles (WebVTT, SRT)
        val subtitleConfigs = stream.subtitles.map { sub ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                .setMimeType(if (sub.format == "srt") MimeTypes.APPLICATION_SUBRIP else MimeTypes.TEXT_VTT)
                .setLanguage(sub.language)
                .setLabel(sub.label)
                .setSelectionFlags(if (sub.language == "vi") C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }
        if (subtitleConfigs.isNotEmpty()) {
            mediaItemBuilder.setSubtitleConfigurations(subtitleConfigs)
        }

        val mediaSource = mediaSourceFactory.createMediaSource(mediaItemBuilder.build())
        player.setMediaSource(mediaSource)
        if (startPositionMs > 0) {
            player.seekTo(startPositionMs)
        }
        player.prepare()
        player.play()
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
    }
}
