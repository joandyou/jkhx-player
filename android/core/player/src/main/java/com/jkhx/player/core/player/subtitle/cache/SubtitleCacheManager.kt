package com.jkhx.player.core.player.subtitle.cache

import android.content.Context
import com.jkhx.player.core.player.subtitle.model.OnlineSubtitleItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class SubtitleCacheManager(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    companion object {
        // Biến tăng dần mỗi khi xóa cache để các màn hình nhận biết và làm mới in-memory state
        val cacheClearedCounter = java.util.concurrent.atomic.AtomicLong(0L)
    }

    // Thư mục lưu file .srt thực tế (luôn đảm bảo tồn tại kể cả sau khi cacheDir bị xóa)
    private val subDir: File
        get() = File(context.cacheDir, "subtitles").apply {
            if (!exists()) mkdirs()
        }

    // Thư mục lưu metadata danh sách phụ đề tìm kiếm (List<OnlineSubtitleItem>)
    private val metaDir: File
        get() = File(context.filesDir, "subtitles_meta").apply {
            if (!exists()) mkdirs()
        }

    fun makeKey(
        providerId: String,
        movieSlug: String,
        episodeId: String,
        language: String,
        tag: String = "online"
    ): String {
        val safeProvider = providerId.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val safeSlug = movieSlug.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val safeEp = episodeId.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val safeLang = language.lowercase().replace(Regex("[^a-zA-Z0-9]"), "")
        val safeTag = tag.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val rawName = "${safeProvider}_${safeSlug}_${safeEp}_${safeLang}_${safeTag}"
        
        // Tránh lỗi Linux/Android ext4/f2fs ENAMETOOLONG (tối đa 255 bytes cho filename)
        // Khi URL hoặc Addon ID quá dài, băm MD5 chuỗi đầy đủ và kết hợp tiền tố ngắn gọn
        return if (rawName.length > 70) {
            val hash = try {
                val md = java.security.MessageDigest.getInstance("MD5")
                val bytes = md.digest(rawName.toByteArray(Charsets.UTF_8))
                bytes.joinToString("") { "%02x".format(it) }
            } catch (_: Exception) {
                rawName.hashCode().toUInt().toString(16)
            }
            val shortPrefix = "${safeProvider.take(16)}_${safeSlug.take(16)}_${safeEp.take(12)}"
            "${shortPrefix}_${safeLang}_${hash}.srt"
        } else {
            "${rawName}.srt"
        }
    }

    fun getCachedSubtitleFile(key: String): File? {
        val file = File(subDir, key)
        return if (file.exists() && file.length() > 0) file else null
    }

    fun saveSubtitle(key: String, content: String): File {
        val dir = subDir
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, key)
        file.parentFile?.mkdirs()
        file.writeText(content, Charsets.UTF_8)
        return file
    }

    fun saveSubtitleBytes(key: String, bytes: ByteArray): File {
        val dir = subDir
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, key)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return file
    }

    // --- Metadata List Cache (Tối ưu hóa tránh gọi API tìm kiếm trùng lặp) ---

    fun makeMetaKey(tmdbId: String?, movieSlug: String, episodeNumber: Int?, language: String): String {
        val safeId = (tmdbId?.takeIf { it.isNotBlank() } ?: movieSlug).replace(Regex("[^a-zA-Z0-9_]"), "_")
        val safeEp = (episodeNumber ?: 0).toString()
        val safeLang = language.lowercase().replace(Regex("[^a-zA-Z0-9]"), "")
        return "sublist_${safeId}_ep${safeEp}_${safeLang}.json"
    }

    fun getCachedSubtitleList(key: String, maxAgeHours: Int = 168): List<OnlineSubtitleItem>? {
        val file = File(metaDir, key)
        if (!file.exists() || file.length() == 0L) return null
        val ageMs = System.currentTimeMillis() - file.lastModified()
        if (ageMs > maxAgeHours * 3600_000L) {
            file.delete()
            return null
        }
        return try {
            val jsonStr = file.readText(Charsets.UTF_8)
            json.decodeFromString<List<OnlineSubtitleItem>>(jsonStr)
        } catch (_: Exception) {
            null
        }
    }

    fun saveSubtitleList(key: String, items: List<OnlineSubtitleItem>) {
        if (items.isEmpty()) return
        try {
            val dir = metaDir
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, key)
            val jsonStr = json.encodeToString(items)
            file.writeText(jsonStr, Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    fun clearCache() {
        try {
            subDir.deleteRecursively()
            subDir.mkdirs()
        } catch (_: Exception) {}
    }

    /**
     * Tính tổng dung lượng toàn bộ cache của app (cacheDir + metadata)
     */
    fun getTotalAppCacheBytes(): Long {
        var total = 0L
        try {
            context.cacheDir?.walkTopDown()?.forEach { if (it.isFile) total += it.length() }
            metaDir.walkTopDown().forEach { if (it.isFile) total += it.length() }
        } catch (_: Exception) {}
        return total
    }

    /**
     * Xóa sạch toàn bộ cache tạm của app
     */
    fun clearAllAppCache() {
        try {
            context.cacheDir?.deleteRecursively()
            context.cacheDir?.mkdirs()
            File(context.cacheDir, "subtitles").mkdirs()
        } catch (_: Exception) {}
        try {
            val meta = File(context.filesDir, "subtitles_meta")
            meta.deleteRecursively()
            meta.mkdirs()
        } catch (_: Exception) {}
        try {
            selectedSubPrefs.edit().clear().apply()
        } catch (_: Exception) {}
        cacheClearedCounter.incrementAndGet()
    }

    // --- Quản lý lưu trữ & phục hồi độ trễ phụ đề (Subtitle Offset Persistence) ---

    private val offsetPrefs by lazy {
        context.getSharedPreferences("subtitle_offsets", Context.MODE_PRIVATE)
    }

    fun makeOffsetKey(providerId: String?, movieSlug: String?, episodeId: String?, trackId: String): String {
        val safeProvider = (providerId ?: "default_prov").replace(Regex("[^a-zA-Z0-9_]"), "_")
        val safeSlug = (movieSlug ?: "default_slug").replace(Regex("[^a-zA-Z0-9_]"), "_")
        val safeEp = (episodeId ?: "default_ep").replace(Regex("[^a-zA-Z0-9_]"), "_")
        val safeTrack = trackId.replace(Regex("[^a-zA-Z0-9_]"), "_")
        return "offset_${safeProvider}_${safeSlug}_${safeEp}_${safeTrack}"
    }

    fun makeUniversalOffsetKey(tmdbId: String?, movieSlug: String?, episodeId: String?, trackId: String): String {
        val safeId = (tmdbId?.takeIf { it.isNotBlank() }?.let { "tmdb_$it" } ?: (movieSlug ?: "default_slug")).replace(Regex("[^a-zA-Z0-9_]"), "_")
        val safeEp = (episodeId ?: "default_ep").replace(Regex("[^a-zA-Z0-9_]"), "_")
        val safeTrack = trackId.replace(Regex("[^a-zA-Z0-9_]"), "_")
        return "offset_uni_${safeId}_${safeEp}_${safeTrack}"
    }

    fun getSubtitleOffset(providerId: String?, movieSlug: String?, episodeId: String?, trackId: String, tmdbId: String? = null): Long {
        if (trackId.isBlank() || trackId == "off") return 0L
        val key = makeOffsetKey(providerId, movieSlug, episodeId, trackId)
        val offset = offsetPrefs.getLong(key, 0L)
        if (offset != 0L) return offset
        if (!tmdbId.isNullOrBlank()) {
            val tmdbUniKey = makeUniversalOffsetKey(tmdbId, movieSlug, episodeId, trackId)
            val tmdbOffset = offsetPrefs.getLong(tmdbUniKey, 0L)
            if (tmdbOffset != 0L) return tmdbOffset
        }
        val uniKey = makeUniversalOffsetKey(null, movieSlug, episodeId, trackId)
        return offsetPrefs.getLong(uniKey, 0L)
    }

    fun saveSubtitleOffset(providerId: String?, movieSlug: String?, episodeId: String?, trackId: String, offsetMs: Long, tmdbId: String? = null) {
        if (trackId.isBlank() || trackId == "off") return
        val key = makeOffsetKey(providerId, movieSlug, episodeId, trackId)
        val uniKey = makeUniversalOffsetKey(null, movieSlug, episodeId, trackId)
        val editor = offsetPrefs.edit()
            .putLong(key, offsetMs)
            .putLong(uniKey, offsetMs)
        if (!tmdbId.isNullOrBlank()) {
            val tmdbUniKey = makeUniversalOffsetKey(tmdbId, movieSlug, episodeId, trackId)
            editor.putLong(tmdbUniKey, offsetMs)
        }
        editor.apply()
    }

    // --- Quản lý lưu trữ & phục hồi phụ đề đã chọn (Selected Subtitle Track Persistence) ---

    private val selectedSubPrefs by lazy {
        context.getSharedPreferences("subtitle_selected_tracks", Context.MODE_PRIVATE)
    }

    fun makeSelectedTrackKey(providerId: String?, movieSlug: String?, episodeId: String?): String {
        val safeProvider = (providerId ?: "default_prov").replace(Regex("[^a-zA-Z0-9_]"), "_")
        val safeSlug = (movieSlug ?: "default_slug").replace(Regex("[^a-zA-Z0-9_]"), "_")
        val safeEp = (episodeId ?: "default_ep").replace(Regex("[^a-zA-Z0-9_]"), "_")
        return "selected_sub_${safeProvider}_${safeSlug}_${safeEp}"
    }

    fun makeUniversalTrackKey(tmdbId: String?, movieSlug: String?, episodeId: String?): String {
        val safeId = (tmdbId?.takeIf { it.isNotBlank() }?.let { "tmdb_$it" } ?: (movieSlug ?: "default_slug")).replace(Regex("[^a-zA-Z0-9_]"), "_")
        val safeEp = (episodeId ?: "default_ep").replace(Regex("[^a-zA-Z0-9_]"), "_")
        return "selected_sub_uni_${safeId}_${safeEp}"
    }

    fun getSavedSubtitleTrack(providerId: String?, movieSlug: String?, episodeId: String?, tmdbId: String? = null): String? {
        val key = makeSelectedTrackKey(providerId, movieSlug, episodeId)
        val specific = selectedSubPrefs.getString(key, null)
        if (!specific.isNullOrBlank()) return specific
        if (!tmdbId.isNullOrBlank()) {
            val tmdbUniKey = makeUniversalTrackKey(tmdbId, movieSlug, episodeId)
            val tmdbTrack = selectedSubPrefs.getString(tmdbUniKey, null)
            if (!tmdbTrack.isNullOrBlank()) return tmdbTrack
        }
        val uniKey = makeUniversalTrackKey(null, movieSlug, episodeId)
        return selectedSubPrefs.getString(uniKey, null)
    }

    fun saveSavedSubtitleTrack(
        providerId: String?,
        movieSlug: String?,
        episodeId: String?,
        trackId: String,
        label: String? = null,
        language: String? = null,
        tmdbId: String? = null
    ) {
        val key = makeSelectedTrackKey(providerId, movieSlug, episodeId)
        val uniKey = makeUniversalTrackKey(null, movieSlug, episodeId)
        val editor = selectedSubPrefs.edit()
            .putString(key, trackId)
            .putString(uniKey, trackId)
        if (!label.isNullOrBlank()) {
            editor.putString("${uniKey}_label", label)
        }
        if (!language.isNullOrBlank()) {
            editor.putString("${uniKey}_lang", language)
        }
        if (!tmdbId.isNullOrBlank()) {
            val tmdbUniKey = makeUniversalTrackKey(tmdbId, movieSlug, episodeId)
            editor.putString(tmdbUniKey, trackId)
            if (!label.isNullOrBlank()) {
                editor.putString("${tmdbUniKey}_label", label)
            }
            if (!language.isNullOrBlank()) {
                editor.putString("${tmdbUniKey}_lang", language)
            }
        }
        editor.apply()
    }

    fun getSavedSubtitleLabel(movieSlug: String?, episodeId: String?, tmdbId: String? = null): String? {
        if (!tmdbId.isNullOrBlank()) {
            val tmdbUniKey = makeUniversalTrackKey(tmdbId, movieSlug, episodeId)
            val label = selectedSubPrefs.getString("${tmdbUniKey}_label", null)
            if (!label.isNullOrBlank()) return label
        }
        val uniKey = makeUniversalTrackKey(null, movieSlug, episodeId)
        return selectedSubPrefs.getString("${uniKey}_label", null)
    }

    fun getSavedSubtitleLanguage(movieSlug: String?, episodeId: String?, tmdbId: String? = null): String? {
        if (!tmdbId.isNullOrBlank()) {
            val tmdbUniKey = makeUniversalTrackKey(tmdbId, movieSlug, episodeId)
            val lang = selectedSubPrefs.getString("${tmdbUniKey}_lang", null)
            if (!lang.isNullOrBlank()) return lang
        }
        val uniKey = makeUniversalTrackKey(null, movieSlug, episodeId)
        return selectedSubPrefs.getString("${uniKey}_lang", null)
    }

    fun clearSavedSubtitleTrack(providerId: String?, movieSlug: String?, episodeId: String?, tmdbId: String? = null) {
        val key = makeSelectedTrackKey(providerId, movieSlug, episodeId)
        val uniKey = makeUniversalTrackKey(null, movieSlug, episodeId)
        val editor = selectedSubPrefs.edit()
            .remove(key)
            .remove(uniKey)
            .remove("${uniKey}_label")
            .remove("${uniKey}_lang")
        if (!tmdbId.isNullOrBlank()) {
            val tmdbUniKey = makeUniversalTrackKey(tmdbId, movieSlug, episodeId)
            editor.remove(tmdbUniKey)
                .remove("${tmdbUniKey}_label")
                .remove("${tmdbUniKey}_lang")
        }
        editor.apply()
    }

    fun findCachedOnlineFile(
        providerId: String?,
        movieSlug: String?,
        episodeId: String?,
        source: String,
        itemId: String,
        tmdbId: String? = null
    ): File? {
        val dir = subDir
        if (!dir.exists()) return null
        val safeProvider = (providerId ?: "default_prov").replace(Regex("[^a-zA-Z0-9_]"), "_")
        val safeSlug = (movieSlug ?: "default_slug").replace(Regex("[^a-zA-Z0-9_]"), "_")
        val safeEp = (episodeId ?: "default_ep").replace(Regex("[^a-zA-Z0-9_]"), "_")
        val safeTag = "${source.lowercase()}_${itemId}".replace(Regex("[^a-zA-Z0-9_]"), "_")
        val safeItemId = itemId.replace(Regex("[^a-zA-Z0-9_]"), "_")

        // 1. Tìm chính xác theo provider + slug + episode + tag
        val exactMatch = dir.listFiles { _, name ->
            name.startsWith("${safeProvider}_${safeSlug}_${safeEp}_") && (name.contains(safeTag) || name.contains(safeItemId)) && name.endsWith(".srt")
        }?.firstOrNull { it.length() > 0 }
        if (exactMatch != null) return exactMatch

        // 2. Tìm theo tmdbId (nếu có)
        if (!tmdbId.isNullOrBlank()) {
            val safeTmdb = tmdbId.replace(Regex("[^a-zA-Z0-9_]"), "_")
            val tmdbMatch = dir.listFiles { _, name ->
                name.contains(safeTmdb) && (name.contains(safeTag) || name.contains(safeItemId)) && name.endsWith(".srt")
            }?.firstOrNull { it.length() > 0 }
            if (tmdbMatch != null) return tmdbMatch
        }

        // 3. Tìm theo slug + episode + tag (bỏ qua khác biệt providerId khi xem tiếp từ nguồn/máy chủ khác)
        val slugMatch = dir.listFiles { _, name ->
            name.contains("${safeSlug}_${safeEp}_") && (name.contains(safeTag) || name.contains(safeItemId)) && name.endsWith(".srt")
        }?.firstOrNull { it.length() > 0 }
        if (slugMatch != null) return slugMatch

        // 4. Fallback tìm file chứa safeTag hoặc safeItemId trong thư mục subtitles
        val tagMatch = dir.listFiles { _, name ->
            (name.contains(safeTag) || (safeItemId.isNotBlank() && name.contains(safeItemId))) && name.endsWith(".srt")
        }?.firstOrNull { it.length() > 0 }

        return tagMatch
    }
}
