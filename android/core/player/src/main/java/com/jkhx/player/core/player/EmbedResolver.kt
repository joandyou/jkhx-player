package com.jkhx.player.core.player

import com.jkhx.player.core.model.EmbedResolutionError
import com.jkhx.player.core.model.EpisodeStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

interface EmbedResolver {
    suspend fun resolve(embedUrl: String): EpisodeStream
}

class UnsupportedEmbedResolver : EmbedResolver {
    override suspend fun resolve(embedUrl: String): EpisodeStream {
        throw EmbedResolutionError(embedUrl, "Trình phân giải iframe embed chưa hỗ trợ nhà phát này: $embedUrl")
    }
}

class GenericHtml5EmbedResolver(
    private val client: OkHttpClient = OkHttpClient()
) : EmbedResolver {
    override suspend fun resolve(embedUrl: String): EpisodeStream = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", embedUrl)
                .build()

            val res = client.newCall(req).execute()
            val html = res.body?.string() ?: ""

            // Regex trích xuất link m3u8
            val m3u8Match = Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""").find(html)
            if (m3u8Match != null) {
                return@withContext EpisodeStream(
                    type = "m3u8",
                    url = m3u8Match.value,
                    headers = mapOf("Referer" to embedUrl)
                )
            }

            // Regex trích xuất link mp4
            val mp4Match = Regex("""https?://[^\s"'<>]+\.mp4[^\s"'<>]*""").find(html)
            if (mp4Match != null) {
                return@withContext EpisodeStream(
                    type = "mp4",
                    url = mp4Match.value,
                    headers = mapOf("Referer" to embedUrl)
                )
            }

            throw EmbedResolutionError(embedUrl, "Không tìm thấy luồng m3u8 hoặc mp4 trong trang embed")
        } catch (e: Exception) {
            throw EmbedResolutionError(embedUrl, e.message ?: "Lỗi giải mã embed", e)
        }
    }
}

