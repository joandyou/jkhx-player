package com.jkhx.player.player

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.jkhx.player.ActivePlayerSession
import com.jkhx.player.R
import com.jkhx.player.core.model.EpisodeData
import com.jkhx.player.core.model.Subtitle

/**
 * Helper hỗ trợ phát video bằng các ứng dụng chuyên dụng ngoài (Out-App Player)
 * như VLC for Android, mpv-android, MX Player, Just Player, Vimu Player...
 * Tự động truyền HTTP Headers, resume position, subtitles và metadata qua Android Intent ACTION_VIEW.
 */
object ExternalPlayerHelper {

    fun launchFromActiveSession(
        context: Context,
        episode: EpisodeData,
        positionMs: Long = 0L
    ) {
        val activeStream = ActivePlayerSession.activeUnifiedStream
        val customStream = ActivePlayerSession.customStream
        val streamUrl = activeStream?.streamUrl ?: customStream?.url ?: ""
        val headers = activeStream?.headers ?: customStream?.headers ?: emptyMap()
        val subtitles = activeStream?.subtitles ?: customStream?.subtitles ?: emptyList()
        val movieDetail = ActivePlayerSession.movieDetail
        val movieName = ActivePlayerSession.movieName
        val title = if (episode.name.isNotBlank()) "$movieName - ${episode.name}" else movieName
        val posterUrl = movieDetail?.posterUrl ?: movieDetail?.backdropUrl ?: ActivePlayerSession.movieThumb

        openInExternalPlayer(
            context = context,
            streamUrl = streamUrl,
            title = title,
            posterUrl = posterUrl,
            headers = headers,
            subtitles = subtitles,
            positionMs = positionMs
        )
    }

    fun openInExternalPlayer(
        context: Context,
        streamUrl: String,
        title: String,
        posterUrl: String? = null,
        headers: Map<String, String> = emptyMap(),
        subtitles: List<Subtitle> = emptyList(),
        positionMs: Long = 0L
    ) {
        val cleanUrl = streamUrl.substringBefore("#").substringBefore("|").trim()
        if (cleanUrl.isBlank()) {
            Toast.makeText(context, context.getString(R.string.player_open_external_error), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = Uri.parse(cleanUrl)
            val mimeType = getMimeType(cleanUrl)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addCategory(Intent.CATEGORY_DEFAULT)

                // 1. Tiêu đề hiển thị
                putExtra(Intent.EXTRA_TITLE, title)
                putExtra("title", title)
                putExtra("forcename", title)

                // 2. Poster ảnh bìa
                if (!posterUrl.isNullOrBlank()) {
                    putExtra("poster", posterUrl)
                }

                // 3. Vị trí xem tiếp (Resume Position) - Hỗ trợ MX Player, mpv-android, Just Player, VLC, Vimu
                if (positionMs > 0L) {
                    val posInt = positionMs.toInt()
                    putExtra("position", posInt)
                    putExtra("startfrom", posInt)
                    putExtra("forceresume", true)
                    putExtra("from_start", false)
                }

                putExtra("return_result", true)
                putExtra("secure_uri", true)

                // 4. HTTP Headers (Gửi cả Bundle và Array để tương thích 100% mọi trình phát)
                if (headers.isNotEmpty()) {
                    val headerBundle = Bundle()
                    headers.forEach { (k, v) -> headerBundle.putString(k, v) }
                    putExtra("android.media.intent.extra.HTTP_HEADERS", headerBundle)
                    putExtra("android.provider.Browser.EXTRA_HEADERS", headerBundle)

                    val headerArray = headers.entries.map { "${it.key}: ${it.value}" }.toTypedArray()
                    putExtra("headers", headerArray)
                }

                // 5. Phụ đề rời (Subtitles)
                if (subtitles.isNotEmpty()) {
                    val validSubs = subtitles.filter { it.url.isNotBlank() }
                    if (validSubs.isNotEmpty()) {
                        val subUris = validSubs.map { Uri.parse(it.url) }.toTypedArray()
                        val subNames = validSubs.map { it.label.ifBlank { it.language } }.toTypedArray()
                        val subFilenames = validSubs.map { "${it.language}_${it.label.ifBlank { "sub" }}.${it.format.ifBlank { "srt" }}" }.toTypedArray()

                        // Cấp quyền đọc URI
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        val clipData = ClipData(
                            "subtitles",
                            arrayOf("application/x-subrip", "text/vtt", "text/plain"),
                            ClipData.Item(subUris.first())
                        )
                        subUris.drop(1).forEach { subUri ->
                            clipData.addItem(ClipData.Item(subUri))
                        }
                        setClipData(clipData)

                        // MX Player, mpv-android, Nova
                        putExtra("subs", subUris)
                        putExtra("subs.name", subNames)
                        putExtra("subs.filename", subFilenames)
                        putExtra("subs.enable", arrayOf(subUris.first()))

                        // Just Player
                        putExtra("subtitle_uri", subUris)
                        putExtra("subtitle_name", subNames)

                        // VLC (vị trí phụ đề chính)
                        putExtra("subtitles_location", subUris.first())

                        // Vimu Player
                        putExtra("forcedsrt", validSubs.first().url)
                    }
                }
            }

            val chooser = Intent.createChooser(intent, context.getString(R.string.player_open_external_chooser))
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.player_open_external_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun getMimeType(url: String): String {
        val normalized = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            normalized.endsWith(".m3u8") -> "application/x-mpegURL"
            normalized.endsWith(".mpd") -> "application/dash+xml"
            normalized.endsWith(".mkv") -> "video/x-matroska"
            normalized.endsWith(".webm") -> "video/webm"
            normalized.endsWith(".mp4") -> "video/mp4"
            normalized.endsWith(".ts") -> "video/mp2t"
            normalized.endsWith(".avi") -> "video/x-msvideo"
            normalized.endsWith(".mov") -> "video/quicktime"
            else -> "video/*"
        }
    }
}
