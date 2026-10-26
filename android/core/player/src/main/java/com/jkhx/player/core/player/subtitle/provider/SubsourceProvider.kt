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

class SubsourceProvider(
    private val okHttpClient: OkHttpClient,
    private val apiKeyProvider: () -> String = { "" }
) : SubtitleProvider {

    override val name: String = "Subsource"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    companion object {
        private const val BASE_URL = "https://api.subsource.net/api/v1"
        private const val USER_AGENT = "JKHXPlayer v1.0.7"
    }

    suspend fun testConnection(apiKey: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) return@withContext false to "API Key không được để trống"

        val url = "$BASE_URL/movies/search?searchType=text&q=Avatar"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .header("X-API-Key", cleanKey)
            .build()

        val start = System.currentTimeMillis()
        try {
            okHttpClient.newCall(req).execute().use { resp ->
                val duration = System.currentTimeMillis() - start
                val body = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    true to "Kết nối Subsource thành công (${duration}ms)"
                } else if (resp.code == 401 || resp.code == 403) {
                    false to "API Key Subsource không chính xác hoặc hết hạn (HTTP ${resp.code})"
                } else {
                    false to "Subsource phản hồi HTTP ${resp.code} (${duration}ms): $body"
                }
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - start
            false to "Lỗi kết nối Subsource (${duration}ms): ${e.message ?: "Timeout/Error"}"
        }
    }

    override suspend fun search(query: SubtitleSearchQuery): List<OnlineSubtitleItem> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank()) return@withContext emptyList()

        val results = mutableListOf<OnlineSubtitleItem>()
        val encodedQuery = URLEncoder.encode(query.query.trim(), "UTF-8")
        if (encodedQuery.isBlank() && query.imdbId.isNullOrBlank()) return@withContext emptyList()

        // Bước 1: Tra cứu phim trên Subsource để lấy movieId
        val movieSearchUrl = if (!query.imdbId.isNullOrBlank()) {
            val cleanImdb = query.imdbId.trim().let { if (it.startsWith("tt")) it else "tt$it" }
            "$BASE_URL/movies/search?searchType=imdb&q=$cleanImdb"
        } else {
            "$BASE_URL/movies/search?searchType=text&q=$encodedQuery"
        }

        val movieReq = Request.Builder()
            .url(movieSearchUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .header("X-API-Key", apiKey)
            .build()

        val candidateMovieIds = mutableListOf<Pair<String, String>>() // Pair<movieId, title>
        try {
            okHttpClient.newCall(movieReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val root = json.parseToJsonElement(body).jsonObject
                    val movieList = root["data"]?.jsonArray ?: root["movies"]?.jsonArray
                    movieList?.forEach { elem ->
                        try {
                            val obj = elem.jsonObject
                            val mId = obj["movieId"]?.jsonPrimitive?.content ?: obj["id"]?.jsonPrimitive?.content
                            val mTitle = obj["title"]?.jsonPrimitive?.content ?: query.query
                            if (mId != null) {
                                candidateMovieIds.add(mId to mTitle)
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}

        if (candidateMovieIds.isEmpty()) return@withContext emptyList()

        // Bước 2: Với các phim tìm được, gọi /subtitles?movieId=... để lấy danh sách phụ đề
        for ((mId, mTitle) in candidateMovieIds.take(2)) {
            val subUrl = "$BASE_URL/subtitles?movieId=$mId"
            val subReq = Request.Builder()
                .url(subUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("X-API-Key", apiKey)
                .build()

            try {
                okHttpClient.newCall(subReq).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string() ?: return@use
                    val root = json.parseToJsonElement(body).jsonObject
                    val subList = root["data"]?.jsonArray ?: root["subtitles"]?.jsonArray ?: return@use

                    for (elem in subList) {
                        try {
                            val obj = elem.jsonObject
                            val subId = obj["subtitleId"]?.jsonPrimitive?.content ?: obj["id"]?.jsonPrimitive?.content ?: continue
                            val lang = obj["language"]?.jsonPrimitive?.content ?: "english"
                            val releaseList = obj["releaseInfo"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content }
                            val release = releaseList?.firstOrNull() ?: obj["release_name"]?.jsonPrimitive?.content ?: "$mTitle ($lang)"
                            val downloads = obj["downloads"]?.jsonPrimitive?.content?.toIntOrNull()

                            val langCode = com.jkhx.player.core.player.subtitle.model.SubtitleSupportedLanguages.normalizeLanguageCode(lang)
                            val langName = com.jkhx.player.core.player.subtitle.model.SubtitleSupportedLanguages.getDisplayName(langCode)

                            if (query.languages.isNotEmpty() && !query.languages.any { it.equals(langCode, true) }) {
                                continue
                            }

                            val dlUrl = "$BASE_URL/subtitles/$subId/download"

                            results.add(
                                OnlineSubtitleItem(
                                    id = "subsource_$subId",
                                    source = name,
                                    name = release,
                                    language = langCode,
                                    languageName = langName,
                                    releaseName = release,
                                    downloadUrl = dlUrl,
                                    downloadCount = downloads,
                                    format = "srt"
                                )
                            )
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }

        results
    }

    override suspend fun download(item: OnlineSubtitleItem, destFile: File): File = withContext(Dispatchers.IO) {
        val dlUrl = item.downloadUrl ?: throw IllegalStateException("Không tìm thấy link tải Subsource")
        val apiKey = apiKeyProvider().trim()
        val reqBuilder = Request.Builder()
            .url(dlUrl)
            .header("User-Agent", USER_AGENT)
        if (apiKey.isNotBlank()) {
            reqBuilder.header("X-API-Key", apiKey)
        }
        val req = reqBuilder.build()

        okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Tải Subsource thất bại HTTP ${resp.code}")
            val bytes = resp.body?.bytes() ?: throw IllegalStateException("Dữ liệu phụ đề Subsource rỗng")
            destFile.parentFile?.mkdirs()

            // Kiểm tra Magic Bytes của ZIP archive (PK\x03\x04)
            val isZip = bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
            if (isZip) {
                var extracted = false
                java.util.zip.ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val entryName = entry.name.lowercase()
                        if (!entry.isDirectory && (entryName.endsWith(".srt") || entryName.endsWith(".vtt"))) {
                            destFile.outputStream().use { fos -> zis.copyTo(fos) }
                            extracted = true
                            break
                        }
                        entry = zis.nextEntry
                    }
                }
                if (!extracted) {
                    java.util.zip.ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                        val firstEntry = zis.nextEntry
                        if (firstEntry != null && !firstEntry.isDirectory) {
                            destFile.outputStream().use { fos -> zis.copyTo(fos) }
                            extracted = true
                        }
                    }
                }
                if (!extracted) {
                    destFile.writeBytes(bytes)
                }
            } else {
                destFile.writeBytes(bytes)
            }
        }
        destFile
    }
}

