package com.jkhx.player.core.player.subtitle

import android.content.Context
import com.jkhx.player.core.player.subtitle.cache.SubtitleCacheManager
import com.jkhx.player.core.player.subtitle.model.OnlineSubtitleItem
import com.jkhx.player.core.player.subtitle.model.SubtitleCue
import com.jkhx.player.core.player.subtitle.model.SubtitleSearchQuery
import com.jkhx.player.core.player.subtitle.parser.SrtParser
import com.jkhx.player.core.player.subtitle.provider.OpenSubtitlesProvider
import com.jkhx.player.core.player.subtitle.provider.SubdlProvider
import com.jkhx.player.core.player.subtitle.provider.SubsourceProvider
import com.jkhx.player.core.player.subtitle.provider.SubtitleProvider
import com.jkhx.player.core.player.subtitle.translator.GeminiSubtitleTranslator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

class SubtitleManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    openSubtitlesApiKeyProvider: () -> String = { "" },
    subdlApiKeyProvider: () -> String = { "" },
    subsourceApiKeyProvider: () -> String = { "" },
    geminiApiKeyProvider: () -> String = { "" },
    geminiModelProvider: () -> String = { "gemini-3.5-flash-lite" }
) {

    val cacheManager = SubtitleCacheManager(context)

    val openSubtitlesProvider = OpenSubtitlesProvider(okHttpClient, openSubtitlesApiKeyProvider)
    val subdlProvider = SubdlProvider(okHttpClient, subdlApiKeyProvider)
    val subsourceProvider = SubsourceProvider(okHttpClient, subsourceApiKeyProvider)

    val geminiTranslator = GeminiSubtitleTranslator(
        okHttpClient = okHttpClient,
        apiKeyProvider = geminiApiKeyProvider,
        modelProvider = geminiModelProvider
    )

    private val providers: List<SubtitleProvider> = listOf(
        openSubtitlesProvider,
        subdlProvider,
        subsourceProvider
    )

    /**
     * Tìm kiếm phụ đề đồng thời từ tất cả các provider (OpenSubtitles, SubDL, Subsource)
     * Có hỗ trợ bộ nhớ đệm danh sách (Metadata Cache) để tránh gọi API trùng lặp
     */
    suspend fun searchOnlineSubtitles(
        query: SubtitleSearchQuery,
        targetLanguage: String? = null,
        forceRefresh: Boolean = false
    ): List<OnlineSubtitleItem> = withContext(Dispatchers.IO) {
        val effectiveLang = targetLanguage?.lowercase()?.trim() ?: "all"
        val metaKey = cacheManager.makeMetaKey(
            tmdbId = query.tmdbId,
            movieSlug = query.query,
            episodeNumber = query.episodeNumber,
            language = effectiveLang
        )

        if (!forceRefresh) {
            val cachedList = cacheManager.getCachedSubtitleList(metaKey)
            if (!cachedList.isNullOrEmpty()) {
                return@withContext cachedList
            }
        }

        // Tùy chỉnh danh sách ngôn ngữ tìm kiếm theo targetLanguage
        val effectiveQuery = if (!targetLanguage.isNullOrBlank()) {
            query.copy(languages = listOf(targetLanguage.lowercase()))
        } else {
            query
        }

        val deferredList = providers.map { provider ->
            async {
                try {
                    provider.search(effectiveQuery)
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }

        val allResults = deferredList.awaitAll().flatten()

        // Lọc nghiêm ngặt theo targetLanguage nếu có chỉ định
        val filteredResults = if (!targetLanguage.isNullOrBlank()) {
            allResults.filter { it.language.equals(targetLanguage, ignoreCase = true) }
        } else {
            allResults
        }

        // Sắp xếp ưu tiên: Tiếng Việt (vi) -> Tiếng Anh (en) -> lượt tải cao nhất
        val sortedResults = filteredResults.sortedWith(
            compareBy<OnlineSubtitleItem> { item ->
                when (item.language.lowercase()) {
                    "vi" -> 0
                    "en" -> 1
                    else -> 2
                }
            }.thenByDescending { it.downloadCount ?: 0 }
        )

        if (sortedResults.isNotEmpty()) {
            cacheManager.saveSubtitleList(metaKey, sortedResults)
        }

        sortedResults
    }

    /**
     * Tải file phụ đề từ nguồn online và lưu vào cache cục bộ
     */
    suspend fun downloadSubtitle(
        item: OnlineSubtitleItem,
        providerId: String,
        movieSlug: String,
        episodeId: String
    ): File = withContext(Dispatchers.IO) {
        val cacheKey = cacheManager.makeKey(
            providerId = providerId,
            movieSlug = movieSlug,
            episodeId = episodeId,
            language = item.language,
            tag = "${item.source.lowercase()}_${item.id}"
        )

        val cachedFile = cacheManager.getCachedSubtitleFile(cacheKey)
        if (cachedFile != null) return@withContext cachedFile

        val targetProvider = providers.firstOrNull { it.name.equals(item.source, ignoreCase = true) }

        val content: String = if (targetProvider != null) {
            val tempFile = File(context.cacheDir, "temp_sub_${System.currentTimeMillis()}.${item.format}")
            val downloaded = targetProvider.download(item, tempFile)
            val text = downloaded.readText(Charsets.UTF_8)
            try { tempFile.delete() } catch (_: Exception) {}
            text
        } else if (!item.downloadUrl.isNullOrBlank() && item.downloadUrl.startsWith("http", ignoreCase = true)) {
            downloadRawSubtitleText(item.downloadUrl)
        } else {
            val tempFile = File(context.cacheDir, "temp_sub_${System.currentTimeMillis()}.${item.format}")
            val downloaded = openSubtitlesProvider.download(item, tempFile)
            val text = downloaded.readText(Charsets.UTF_8)
            try { tempFile.delete() } catch (_: Exception) {}
            text
        }

        // Parse và chuẩn hóa sang SRT UTF-8 sạch
        val cues = SrtParser.parse(content)
        val cleanSrt = SrtParser.formatToSrt(cues)

        // Nếu phụ đề AI từ Stremio Addon đang dịch dở ("[Đang dịch...]"), chỉ lưu file tạm để tránh lưu chết bản chưa dịch xong vào cache
        if (content.contains("[Đang dịch...") || cleanSrt.contains("[Đang dịch...")) {
            val partialFile = File(context.cacheDir, "partial_sub_${item.id.hashCode()}.srt")
            partialFile.writeText(cleanSrt, Charsets.UTF_8)
            return@withContext partialFile
        }

        cacheManager.saveSubtitle(cacheKey, cleanSrt)
    }

    /**
     * Tải trực tiếp nội dung phụ đề từ URL (hỗ trợ .srt, .vtt, .zip, UTF-8/UTF-16 và retry khi AI đang khởi tạo)
     */
    suspend fun downloadRawSubtitleText(url: String): String = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (attempt in 1..3) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Stremio/4.4.168 (Android)")
                    .header("Accept", "*/*")
                    .build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    val code = response.code
                    response.close()
                    if ((code == 404 || code == 502 || code == 503 || code == 429) && attempt < 3) {
                        delay(2500L)
                        continue
                    }
                    throw Exception("HTTP $code khi tải phụ đề")
                }
                val bytes = response.body?.bytes() ?: throw Exception("Nội dung phụ đề trống")
                if (bytes.isEmpty()) throw Exception("File phụ đề rỗng")

                // Kiểm tra nếu là file nén ZIP (PK header 0x50 0x4B 0x03 0x04)
                if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
                    bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
                ) {
                    ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val name = entry.name.lowercase()
                            if (!entry.isDirectory && (name.endsWith(".srt") || name.endsWith(".vtt") || name.endsWith(".sub") || name.endsWith(".ass"))) {
                                val entryBytes = zis.readBytes()
                                return@withContext decodeSubtitleBytes(entryBytes)
                            }
                            entry = zis.nextEntry
                        }
                    }
                }

                return@withContext decodeSubtitleBytes(bytes)
            } catch (e: Exception) {
                lastError = e
                if (attempt < 3) {
                    delay(2000L)
                }
            }
        }
        throw lastError ?: Exception("Không thể tải phụ đề từ $url")
    }

    private fun decodeSubtitleBytes(bytes: ByteArray): String {
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 0xFF && b1 == 0xFE) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            }
            if (b0 == 0xFE && b1 == 0xFF) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            }
        }
        if (bytes.size >= 3) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            val b2 = bytes[2].toInt() and 0xFF
            if (b0 == 0xEF && b1 == 0xBB && b2 == 0xBF) {
                return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            }
        }
        return String(bytes, Charsets.UTF_8)
    }

    /**
     * Dịch danh sách câu thoại (cues) hoặc file phụ đề sang Tiếng Việt bằng Gemini AI và lưu vào cache
     */
    suspend fun translateToVietnamese(
        cues: List<SubtitleCue>,
        providerId: String,
        movieSlug: String,
        episodeId: String,
        onProgress: (Float) -> Unit = {}
    ): Pair<File, List<SubtitleCue>> = withContext(Dispatchers.IO) {
        val cacheKey = cacheManager.makeKey(
            providerId = providerId,
            movieSlug = movieSlug,
            episodeId = episodeId,
            language = "vi",
            tag = "gemini_ai"
        )

        val cachedFile = cacheManager.getCachedSubtitleFile(cacheKey)
        if (cachedFile != null) {
            val cachedCues = SrtParser.parse(cachedFile.readText(Charsets.UTF_8))
            onProgress(1.0f)
            return@withContext cachedFile to cachedCues
        }

        val translatedCues = geminiTranslator.translateCues(
            cues = cues,
            targetLanguageName = "Tiếng Việt",
            onProgress = onProgress
        )

        val srtContent = SrtParser.formatToSrt(translatedCues)
        val file = cacheManager.saveSubtitle(cacheKey, srtContent)

        file to translatedCues
    }
}

