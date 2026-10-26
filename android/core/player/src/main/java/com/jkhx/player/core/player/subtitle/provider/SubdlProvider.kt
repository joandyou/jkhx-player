package com.jkhx.player.core.player.subtitle.provider

import com.jkhx.player.core.player.subtitle.model.OnlineSubtitleItem
import com.jkhx.player.core.player.subtitle.model.SubtitleSearchQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLEncoder
import java.util.zip.ZipInputStream

class SubdlProvider(
    private val okHttpClient: OkHttpClient,
    private val apiKeyProvider: () -> String = { "" }
) : SubtitleProvider {

    override val name: String = "SubDL"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    companion object {
        const val DEFAULT_API_KEY = "vH7J3qY9_subdl_public"
        private const val BASE_URL = "https://api.subdl.com/api/v1"
        private const val USER_AGENT = "JKHXPlayer v1.0.6"
    }

    private fun getActiveApiKey(): String {
        val userKey = apiKeyProvider().trim()
        return if (userKey.isNotBlank()) userKey else DEFAULT_API_KEY
    }

    override suspend fun search(query: SubtitleSearchQuery): List<OnlineSubtitleItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<OnlineSubtitleItem>()
        val apiKey = getActiveApiKey()

        val params = mutableListOf<String>()
        params.add("api_key=$apiKey")

        if (!query.imdbId.isNullOrBlank()) {
            val formattedImdb = if (query.imdbId.startsWith("tt")) query.imdbId else "tt${query.imdbId}"
            params.add("imdb_id=$formattedImdb")
        }
        if (!query.tmdbId.isNullOrBlank()) {
            params.add("tmdb_id=${query.tmdbId.trim()}")
        }
        if (query.query.isNotBlank()) {
            params.add("film_name=${URLEncoder.encode(query.query.trim(), "UTF-8")}")
        }
        if (query.seasonNumber != null && query.seasonNumber > 0) {
            params.add("season_number=${query.seasonNumber}")
            params.add("type=tv")
        }
        if (query.episodeNumber != null && query.episodeNumber > 0) {
            params.add("episode_number=${query.episodeNumber}")
        }

        val langs = query.languages.joinToString(",").ifBlank { "vi,en" }
        params.add("languages=$langs")

        val url = "$BASE_URL/subtitles?${params.joinToString("&")}"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()

        try {
            okHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string() ?: return@withContext emptyList()
                val root = json.parseToJsonElement(body).jsonObject
                val subtitles = root["subtitles"]?.jsonArray ?: return@withContext emptyList()

                for (elem in subtitles) {
                    try {
                        val obj = elem.jsonObject
                        val subUrl = obj["url"]?.jsonPrimitive?.content ?: continue
                        val release = obj["release_name"]?.jsonPrimitive?.content ?: obj["name"]?.jsonPrimitive?.content ?: "SubDL Item"
                        val lang = obj["lang"]?.jsonPrimitive?.content ?: "vi"
                        val author = obj["author"]?.jsonPrimitive?.content
                        val nameStr = if (!author.isNullOrBlank()) "$release ($author)" else release

                        val langCode = com.jkhx.player.core.player.subtitle.model.SubtitleSupportedLanguages.normalizeLanguageCode(lang)
                        val langName = com.jkhx.player.core.player.subtitle.model.SubtitleSupportedLanguages.getDisplayName(langCode)

                        // URL download đầy đủ của SubDL
                        val fullDownloadUrl = if (subUrl.startsWith("http")) subUrl else "https://dl.subdl.com$subUrl"

                        results.add(
                            OnlineSubtitleItem(
                                id = "subdl_${subUrl.hashCode()}",
                                source = name,
                                name = nameStr,
                                language = langCode,
                                languageName = langName,
                                releaseName = release,
                                downloadUrl = fullDownloadUrl,
                                format = if (fullDownloadUrl.endsWith(".vtt", ignoreCase = true)) "vtt" else "srt"
                            )
                        )
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}

        results
    }

    override suspend fun download(item: OnlineSubtitleItem, destFile: File): File = withContext(Dispatchers.IO) {
        val downloadUrl = item.downloadUrl ?: throw IllegalStateException("Không có đường dẫn tải SubDL")

        val req = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", USER_AGENT)
            .build()

        okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Tải phụ đề SubDL thất bại HTTP ${resp.code}")
            val rawBytes = resp.body?.bytes() ?: throw IllegalStateException("Nội dung phụ đề SubDL rỗng")

            // Kiểm tra nếu là file nén ZIP (Magic bytes: PK\x03\x04)
            if (rawBytes.size > 4 && rawBytes[0] == 0x50.toByte() && rawBytes[1] == 0x4B.toByte()) {
                val extracted = extractSrtFromZip(rawBytes)
                if (extracted != null) {
                    destFile.parentFile?.mkdirs()
                    destFile.writeBytes(extracted)
                    return@withContext destFile
                }
            }

            destFile.parentFile?.mkdirs()
            destFile.writeBytes(rawBytes)
        }

        destFile
    }

    private fun extractSrtFromZip(zipBytes: ByteArray): ByteArray? {
        try {
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name.lowercase()
                    if (entryName.endsWith(".srt") || entryName.endsWith(".vtt")) {
                        return zis.readBytes()
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (_: Exception) {}
        return null
    }

    suspend fun testConnection(apiKey: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) return@withContext false to "API Key không được để trống"
        val testUrl = "$BASE_URL/subtitles?api_key=$cleanKey&imdb_id=tt0111161"
        val req = Request.Builder()
            .url(testUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()

        val start = System.currentTimeMillis()
        try {
            okHttpClient.newCall(req).execute().use { resp ->
                val duration = System.currentTimeMillis() - start
                if (resp.isSuccessful) {
                    true to "Kết nối SubDL thành công (${duration}ms)"
                } else {
                    false to "SubDL phản hồi HTTP ${resp.code} (${duration}ms)"
                }
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - start
            false to "Lỗi kết nối SubDL (${duration}ms): ${e.message ?: "Timeout"}"
        }
    }
}
