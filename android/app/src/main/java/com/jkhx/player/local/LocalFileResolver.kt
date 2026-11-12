package com.jkhx.player.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.jkhx.player.core.model.EpisodeData
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.regex.Pattern

enum class FolderMediaType {
    VIDEO,      // Thư mục chứa video / series
    COMIC,      // Thư mục chứa truyện tranh (ảnh)
    NOVEL,      // Thư mục chứa truyện chữ (.txt, .epub)
    MIXED,      // Thư mục chứa hỗn hợp nhiều loại
    EMPTY       // Thư mục trống hoặc không chứa file media hỗ trợ
}

data class FolderAnalysisResult(
    val folderName: String,
    val primaryType: FolderMediaType,
    val coverUrl: String? = null,
    val videoEpisodes: List<EpisodeData> = emptyList(),
    val comicMetadata: LocalComicParser.ComicMetadata? = null,
    val novelFiles: List<DocumentFile> = emptyList()
)

object LocalFileResolver {

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "avi", "webm", "ts", "mov", "flv", "m4v", "wmv", "3gp"
    )

    private val SUBTITLE_EXTENSIONS = setOf(
        "srt", "vtt", "ass", "ssa", "sub"
    )

    fun hashUri(uri: Uri): String {
        val bytes = MessageDigest.getInstance("MD5").digest(uri.toString().toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun takePersistablePermission(context: Context, uri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: Exception) {}
    }

    fun getFileName(context: Context, uri: Uri): String {
        if (uri.scheme == "file") {
            return uri.lastPathSegment ?: "unknown_file"
        }
        var name = "unknown_file"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex) ?: "unknown_file"
                }
            }
        } catch (_: Exception) {}
        return name
    }

    fun getFileSize(context: Context, uri: Uri): Long {
        if (uri.scheme == "file") {
            return java.io.File(uri.path ?: "").length()
        }
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        } catch (_: Exception) {}
        return size
    }

    fun isVideoFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return VIDEO_EXTENSIONS.contains(ext)
    }

    fun isSubtitleFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return SUBTITLE_EXTENSIONS.contains(ext)
    }

    /**
     * Sắp xếp tự nhiên (Natural Sort Order) ví dụ: Tập 2 trước Tập 10
     */
    val naturalOrderComparator = Comparator<String> { a, b ->
        val pattern = Pattern.compile("(\\d+)|(\\D+)")
        val matcherA = pattern.matcher(a)
        val matcherB = pattern.matcher(b)

        while (matcherA.find() && matcherB.find()) {
            val tokenA = matcherA.group()
            val tokenB = matcherB.group()

            if (tokenA != tokenB) {
                val isNumA = tokenA.all { it.isDigit() }
                val isNumB = tokenB.all { it.isDigit() }

                if (isNumA && isNumB) {
                    val cmp = tokenA.toLong().compareTo(tokenB.toLong())
                    if (cmp != 0) return@Comparator cmp
                } else {
                    val cmp = tokenA.compareTo(tokenB, ignoreCase = true)
                    if (cmp != 0) return@Comparator cmp
                }
            }
        }
        a.length.compareTo(b.length)
    }

    /**
     * Quét tất cả file video: ở thư mục gốc và thư mục con cấp 1 (ví dụ Season 1, Season 2...)
     */
    fun scanAllVideoFiles(rootDoc: DocumentFile): List<DocumentFile> {
        val result = mutableListOf<DocumentFile>()
        val children = rootDoc.listFiles()

        // 1. Video ở thư mục gốc
        val directVideos = children.filter { it.isFile && isVideoFile(it.name ?: "") }
        result.addAll(directVideos)

        // 2. Video ở các thư mục con cấp 1 (trừ thư mục ẩn)
        val subDirs = children.filter { it.isDirectory && !(it.name?.startsWith(".") == true) }
        for (subDir in subDirs) {
            val subVideos = subDir.listFiles().filter { it.isFile && isVideoFile(it.name ?: "") }
            result.addAll(subVideos)
        }

        return result.sortedWith { f1, f2 -> naturalOrderComparator.compare(f1.name ?: "", f2.name ?: "") }
    }

    /**
     * Quét tất cả file phụ đề: ở thư mục gốc, thư mục con và thư mục subs/subtitles
     */
    fun scanAllSubtitleFiles(rootDoc: DocumentFile): List<DocumentFile> {
        val result = mutableListOf<DocumentFile>()
        val children = rootDoc.listFiles()

        result.addAll(children.filter { it.isFile && isSubtitleFile(it.name ?: "") })

        val subDirs = children.filter { it.isDirectory && !(it.name?.startsWith(".") == true) }
        for (subDir in subDirs) {
            result.addAll(subDir.listFiles().filter { it.isFile && isSubtitleFile(it.name ?: "") })
        }

        return result
    }

    /**
     * Tìm ảnh bìa (poster / cover) cho thư mục
     */
    fun findFolderCoverUrl(rootDoc: DocumentFile): String? {
        val children = rootDoc.listFiles()
        val imageFiles = children.filter { it.isFile && LocalComicParser.isImageFile(it.name ?: "") }
        if (imageFiles.isEmpty()) return null

        val priorityKeywords = listOf("poster", "cover", "folder", "default", "thumb", "fanart")
        for (kw in priorityKeywords) {
            val matched = imageFiles.firstOrNull {
                (it.name ?: "").substringBeforeLast('.').contains(kw, ignoreCase = true)
            }
            if (matched != null) return matched.uri.toString()
        }

        return imageFiles.firstOrNull()?.uri?.toString()
    }

    /**
     * Quét thư mục cây (DocumentTree) để tìm danh sách các tập phim video
     */
    fun scanVideoFolder(context: Context, treeUri: Uri): List<EpisodeData> {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val videoFiles = scanAllVideoFiles(rootDoc)
        if (videoFiles.isEmpty()) return emptyList()

        val subFiles = scanAllSubtitleFiles(rootDoc)

        val episodes = mutableListOf<EpisodeData>()
        for ((index, file) in videoFiles.withIndex()) {
            val name = file.name ?: "Tập ${index + 1}"
            val baseName = name.substringBeforeLast('.')

            // Tìm phụ đề cùng tên trong danh sách phụ đề
            val matchedSub = subFiles.firstOrNull { sub ->
                val subName = sub.name ?: ""
                val subBase = subName.substringBeforeLast('.')
                subBase.equals(baseName, ignoreCase = true) ||
                subBase.startsWith(baseName, ignoreCase = true) ||
                baseName.startsWith(subBase, ignoreCase = true)
            }

            val subUri = matchedSub?.uri?.toString()
            val epDataJson = buildString {
                append("{\"type\":\"video\",\"uri\":\"").append(file.uri.toString()).append("\"")
                if (subUri != null) {
                    append(",\"subUri\":\"").append(subUri).append("\"")
                }
                append("}")
            }

            episodes.add(
                EpisodeData(
                    id = "ep_${index + 1}",
                    name = baseName,
                    slug = "ep_${index + 1}",
                    number = index + 1,
                    data = Json.parseToJsonElement(epDataJson)
                )
            )
        }
        return episodes
    }

    /**
     * Phân tích nội dung tổng thể của một thư mục (Smart Folder Detection)
     */
    fun analyzeFolder(context: Context, treeUri: Uri): FolderAnalysisResult {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            ?: return FolderAnalysisResult("Thư mục", FolderMediaType.EMPTY)

        val folderName = rootDoc.name ?: "Thư mục nội bộ"
        val hashId = hashUri(treeUri)
        val coverUrl = findFolderCoverUrl(rootDoc)

        // 1. Quét video
        val videoEpisodes = scanVideoFolder(context, treeUri)

        // 2. Quét comic (truyện tranh)
        val comicMeta = LocalComicParser.scanComicFolder(context, treeUri, hashId)
        val validComic = if (comicMeta.chapters.isNotEmpty()) comicMeta else null

        // 3. Quét novel (.epub hoặc .txt độc lập)
        val children = rootDoc.listFiles()
        val novelFiles = children.filter { file ->
            if (!file.isFile) return@filter false
            val ext = file.name?.substringAfterLast('.', "")?.lowercase() ?: ""
            if (ext == "epub") return@filter true
            if (ext == "txt") {
                if (videoEpisodes.isNotEmpty()) {
                    val base = file.name?.substringBeforeLast('.') ?: ""
                    val isVideoSubOrNote = videoEpisodes.any { it.name.equals(base, ignoreCase = true) }
                    !isVideoSubOrNote && file.length() > 30 * 1024
                } else {
                    file.length() > 1024
                }
            } else {
                false
            }
        }

        val hasVideo = videoEpisodes.isNotEmpty()
        val hasComic = validComic != null && validComic.chapters.isNotEmpty()
        val hasNovel = novelFiles.isNotEmpty()

        val typeCount = (if (hasVideo) 1 else 0) + (if (hasComic) 1 else 0) + (if (hasNovel) 1 else 0)

        val primaryType = when {
            typeCount > 1 -> FolderMediaType.MIXED
            hasVideo -> FolderMediaType.VIDEO
            hasComic -> FolderMediaType.COMIC
            hasNovel -> FolderMediaType.NOVEL
            else -> FolderMediaType.EMPTY
        }

        return FolderAnalysisResult(
            folderName = folderName,
            primaryType = primaryType,
            coverUrl = coverUrl ?: validComic?.coverUrl,
            videoEpisodes = videoEpisodes,
            comicMetadata = validComic,
            novelFiles = novelFiles
        )
    }

    /**
     * Regex nhận diện các mẫu tập phim phổ biến (S01E01, EP01, Tập 01, Part 1...)
     */
    private val seriesEpisodeRegex = Regex(
        "(?:[sS](\\d{1,2})[eE](\\d{1,3}))|" +
        "(?:(?:^|[\\s._\\-\\[(])(?:ep|e|tap|tập)[\\s._\\-]*(\\d{1,3})(?:[\\s._\\-\\])]|$))|" +
        "(?:(?:^|[\\s._\\-\\[(])(?:part|vol|chương)[\\s._\\-]*(\\d{1,3})(?:[\\s._\\-\\])]|$))",
        RegexOption.IGNORE_CASE
    )

    fun isSeriesEpisodePattern(fileName: String): Boolean {
        val base = fileName.substringBeforeLast('.')
        return seriesEpisodeRegex.containsMatchIn(base)
    }

    /**
     * Kiểm tra xem danh sách file video trong thư mục là một bộ phim dài tập (series) hay là tập hợp phim lẻ độc lập (movies).
     */
    fun isSeriesFolder(episodes: List<EpisodeData>): Boolean {
        if (episodes.size <= 1) return false

        // 1. Nếu trên 40% số file khớp mẫu đánh số tập chuẩn mực -> Series
        val episodePatternCount = episodes.count { isSeriesEpisodePattern(it.name) }
        if (episodePatternCount.toDouble() / episodes.size >= 0.4) {
            return true
        }

        // 2. Nếu tên các file có chung tiền tố dài và hậu tố là số thứ tự
        val names = episodes.map { it.name.trim() }
        val commonPrefix = getCommonPrefix(names)
        if (commonPrefix.length >= 4 && commonPrefix.length.toDouble() / names.first().length >= 0.35) {
            val allHaveNumberSuffix = episodes.all { ep ->
                val remainder = ep.name.removePrefix(commonPrefix).trim()
                remainder.matches(Regex("^[0-9]+.*")) || remainder.matches(Regex("^[IVXLCDM]+.*", RegexOption.IGNORE_CASE))
            }
            if (allHaveNumberSuffix) return true
        }

        return false
    }

    private fun getCommonPrefix(strings: List<String>): String {
        if (strings.isEmpty()) return ""
        var prefix = strings[0]
        for (i in 1 until strings.size) {
            while (!strings[i].startsWith(prefix, ignoreCase = true)) {
                prefix = prefix.substring(0, prefix.length - 1)
                if (prefix.isEmpty()) return ""
            }
        }
        return prefix
    }

    /**
     * Làm sạch tên phim từ tên file (loại bỏ tag độ phân giải, năm, nhóm encode...)
     */
    fun cleanMovieTitle(rawName: String): String {
        var name = rawName.substringBeforeLast('.')
        name = name.replace('.', ' ').replace('_', ' ')
        val tags = listOf(
            "1080p", "720p", "2160p", "4k", "bluray", "webrip", "web-dl", "remux", "hdr", "dovi",
            "x264", "x265", "hevc", "avc", "aac", "dts", "ac3", "yify", "yts", "rarbg", "galaxyrg"
        )
        for (t in tags) {
            name = name.replace(Regex("(?i)\\b$t\\b"), "")
        }
        return name.trim().replace(Regex("\\s+"), " ")
    }

    /**
     * Tạo 1 tập đơn từ file video lẻ
     */
    fun createSingleVideoEpisode(fileName: String, videoUri: Uri, subtitleUri: Uri? = null): EpisodeData {
        val baseName = fileName.substringBeforeLast('.')
        val epDataJson = buildString {
            append("{\"type\":\"video\",\"uri\":\"").append(videoUri.toString()).append("\"")
            if (subtitleUri != null) {
                append(",\"subUri\":\"").append(subtitleUri.toString()).append("\"")
            }
            append("}")
        }
        return EpisodeData(
            id = "ep_1",
            name = baseName,
            slug = "ep_1",
            number = 1,
            data = Json.parseToJsonElement(epDataJson)
        )
    }
}
