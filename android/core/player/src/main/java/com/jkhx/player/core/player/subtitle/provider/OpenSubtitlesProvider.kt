package com.jkhx.player.core.player.subtitle.provider

import com.jkhx.player.core.player.subtitle.model.OnlineSubtitleItem
import com.jkhx.player.core.player.subtitle.model.SubtitleSearchQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URLEncoder

class OpenSubtitlesProvider(
    private val okHttpClient: OkHttpClient,
    private val apiKeyProvider: () -> String = { "" }
) : SubtitleProvider {

    override val name: String = "OpenSubtitles"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    companion object {
        const val DEFAULT_API_KEY = "vYh0q1m3D9Y6aQ7D9BqA4Y1G3Z2a9X" // OpenSubtitles Community App Key
        private const val BASE_URL = "https://api.opensubtitles.com/api/v1"
        private const val USER_AGENT = "JKHXPlayer v1.0.6"
    }

    private fun getActiveApiKey(): String {
        val userKey = apiKeyProvider().trim()
        return if (userKey.isNotBlank()) userKey else DEFAULT_API_KEY
    }

    override suspend fun search(query: SubtitleSearchQuery): List<OnlineSubtitleItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<OnlineSubtitleItem>()
        val apiKey = getActiveApiKey()

        // Xây dựng query params
        val params = mutableListOf<String>()
        val cleanImdb = query.imdbId?.removePrefix("tt")?.trim()
        if (!cleanImdb.isNullOrBlank()) {
            params.add("imdb_id=$cleanImdb")
        }
        if (!query.tmdbId.isNullOrBlank()) {
            params.add("tmdb_id=${query.tmdbId.trim()}")
        }
        if (params.isEmpty() && query.query.isNotBlank()) {
            params.add("query=${URLEncoder.encode(query.query.trim(), "UTF-8")}")
        }
        if (query.year != null && query.year > 1900) {
            params.add("year=${query.year}")
        }
        if (query.seasonNumber != null && query.seasonNumber > 0) {
            params.add("season_number=${query.seasonNumber}")
        }
        if (query.episodeNumber != null && query.episodeNumber > 0) {
            params.add("episode_number=${query.episodeNumber}")
        }

        val langs = query.languages.joinToString(",").ifBlank { "vi,en" }
        params.add("languages=$langs")
        params.add("order_by=download_count")

        val url = "$BASE_URL/subtitles?${params.joinToString("&")}"

        val request = Request.Builder()
            .url(url)
            .header("Api-Key", apiKey)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()

        try {
            okHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string() ?: return@withContext emptyList()
                val root = json.parseToJsonElement(body).jsonObject
                val data = root["data"]?.jsonArray ?: return@withContext emptyList()

                for (elem in data) {
                    try {
                        val obj = elem.jsonObject
                        val subId = obj["id"]?.jsonPrimitive?.content ?: continue
                        val attrs = obj["attributes"]?.jsonObject ?: continue
                        val lang = attrs["language"]?.jsonPrimitive?.content ?: "vi"
                        val release = attrs["release"]?.jsonPrimitive?.content
                        val dlCount = attrs["download_count"]?.jsonPrimitive?.intOrNull
                        val ratings = attrs["ratings"]?.jsonPrimitive?.content?.toFloatOrNull()
                        val files = attrs["files"]?.jsonArray
                        val firstFile = files?.firstOrNull()?.jsonObject
                        val fileId = firstFile?.get("file_id")?.jsonPrimitive?.content ?: subId
                        val fileName = firstFile?.get("file_name")?.jsonPrimitive?.content ?: release ?: "OpenSubtitles $subId"

                        val normLang = com.jkhx.player.core.player.subtitle.model.SubtitleSupportedLanguages.normalizeLanguageCode(lang)
                        val langName = com.jkhx.player.core.player.subtitle.model.SubtitleSupportedLanguages.getDisplayName(normLang)

                        results.add(
                            OnlineSubtitleItem(
                                id = "opensub_$fileId",
                                source = name,
                                name = fileName,
                                language = normLang,
                                languageName = langName,
                                releaseName = release,
                                fileId = fileId,
                                rating = ratings,
                                downloadCount = dlCount,
                                format = "srt"
                            )
                        )
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}

        results
    }

    override suspend fun download(item: OnlineSubtitleItem, destFile: File): File = withContext(Dispatchers.IO) {
        val fileId = item.fileId ?: item.id.removePrefix("opensub_")
        val apiKey = getActiveApiKey()

        val downloadRequestUrl = "$BASE_URL/download"
        val jsonPayload = """{"file_id": $fileId}"""

        val postReq = Request.Builder()
            .url(downloadRequestUrl)
            .header("Api-Key", apiKey)
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .post(jsonPayload.toRequestBody("application/json".toMediaType()))
            .build()

        var directDownloadUrl: String? = item.downloadUrl

        if (directDownloadUrl.isNullOrBlank()) {
            okHttpClient.newCall(postReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val root = json.parseToJsonElement(body).jsonObject
                    directDownloadUrl = root["link"]?.jsonPrimitive?.content
                }
            }
        }

        if (directDownloadUrl.isNullOrBlank()) {
            throw IllegalStateException("Không lấy được đường dẫn tải từ OpenSubtitles (mã file: $fileId)")
        }

        // Tải nội dung file về destFile
        val getFileReq = Request.Builder()
            .url(directDownloadUrl!!)
            .header("User-Agent", USER_AGENT)
            .build()

        okHttpClient.newCall(getFileReq).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Tải phụ đề thất bại HTTP ${resp.code}")
            val bytes = resp.body?.bytes() ?: throw IllegalStateException("File phụ đề tải về rỗng")
            destFile.parentFile?.mkdirs()
            destFile.writeBytes(bytes)
        }

        destFile
    }

    suspend fun testConnection(apiKey: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val key = apiKey.trim().ifBlank { DEFAULT_API_KEY }
        val testUrl = "$BASE_URL/subtitles?imdb_id=0111161&languages=en"
        val req = Request.Builder()
            .url(testUrl)
            .header("Api-Key", key)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()

        val start = System.currentTimeMillis()
        try {
            okHttpClient.newCall(req).execute().use { resp ->
                val duration = System.currentTimeMillis() - start
                if (resp.isSuccessful) {
                    true to "Kết nối OpenSubtitles thành công (${duration}ms)"
                } else {
                    false to "OpenSubtitles phản hồi HTTP ${resp.code} (${duration}ms)"
                }
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - start
            false to "Lỗi kết nối OpenSubtitles (${duration}ms): ${e.message ?: "Timeout"}"
        }
    }
}
