package com.jkhx.player.local

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.jkhx.player.core.model.EpisodeData
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipInputStream

object LocalComicParser {

    private val COMIC_IMAGE_EXTS = setOf(
        "jpg", "jpeg", "png", "webp", "avif", "gif", "bmp"
    )

    data class ComicMetadata(
        val title: String,
        val coverUrl: String? = null,
        val chapters: List<EpisodeData>
    )

    fun isImageFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return COMIC_IMAGE_EXTS.contains(ext)
    }

    /**
     * Phân tích file .cbz hoặc .zip thành danh sách chương truyện tranh
     */
    fun parseZipComic(context: Context, uri: Uri, fileName: String, bookId: String): ComicMetadata {
        val baseTitle = fileName.substringBeforeLast('.')
        val allEntries = mutableListOf<String>()

        context.contentResolver.openInputStream(uri)?.use { inStream ->
            ZipInputStream(inStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && isImageFile(entry.name)) {
                        allEntries.add(entry.name)
                    }
                    entry = zis.nextEntry
                }
            }
        }

        if (allEntries.isEmpty()) {
            return ComicMetadata(title = baseTitle, chapters = emptyList())
        }

        // Nhóm các ảnh theo thư mục con
        val groups = allEntries.groupBy {
            val idx = it.lastIndexOf('/')
            if (idx != -1) it.substring(0, idx) else ""
        }

        val chapters = mutableListOf<EpisodeData>()
        val sortedGroupKeys = groups.keys.sortedWith(LocalFileResolver.naturalOrderComparator)

        // Trích xuất trang đầu tiên làm ảnh bìa nếu có
        var coverSavedPath: String? = null
        val firstImageEntry = allEntries.sortedWith(LocalFileResolver.naturalOrderComparator).firstOrNull()
        if (firstImageEntry != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inStream ->
                    ZipInputStream(inStream).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (entry.name == firstImageEntry) {
                                val coversDir = File(context.filesDir, "local_covers")
                                if (!coversDir.exists()) coversDir.mkdirs()
                                val coverFile = File(coversDir, "${bookId}.jpg")
                                coverFile.outputStream().use { out -> zis.copyTo(out) }
                                coverSavedPath = coverFile.absolutePath
                                break
                            }
                            entry = zis.nextEntry
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (sortedGroupKeys.size > 1 || (sortedGroupKeys.size == 1 && sortedGroupKeys.first().isNotEmpty())) {
            for ((idx, groupName) in sortedGroupKeys.withIndex()) {
                val chapterTitle = if (groupName.contains('/')) groupName.substringAfterLast('/') else groupName
                val cleanTitle = if (chapterTitle.isNotBlank()) chapterTitle else "Chương ${idx + 1}"
                val dataJson = buildString {
                    append("{\"type\":\"comic_zip\",")
                    append("\"uri\":\"").append(uri.toString()).append("\",")
                    append("\"bookId\":\"").append(bookId).append("\",")
                    append("\"prefix\":\"").append(groupName).append("\"}")
                }
                chapters.add(
                    EpisodeData(
                        id = "chap_${idx + 1}",
                        name = cleanTitle,
                        slug = "chap_${idx + 1}",
                        number = idx + 1,
                        data = Json.parseToJsonElement(dataJson)
                    )
                )
            }
        } else {
            // Chỉ có 1 chương phẳng
            val dataJson = buildString {
                append("{\"type\":\"comic_zip\",")
                append("\"uri\":\"").append(uri.toString()).append("\",")
                append("\"bookId\":\"").append(bookId).append("\",")
                append("\"prefix\":\"\"}")
            }
            chapters.add(
                EpisodeData(
                    id = "chap_1",
                    name = baseTitle,
                    slug = "chap_1",
                    number = 1,
                    data = Json.parseToJsonElement(dataJson)
                )
            )
        }

        return ComicMetadata(
            title = baseTitle,
            coverUrl = coverSavedPath,
            chapters = chapters
        )
    }

    /**
     * Giải nén các ảnh của 1 chương từ file zip vào cache và trả về danh sách URI file://
     */
    fun extractZipChapterImages(context: Context, uri: Uri, bookId: String, chapterId: String, prefix: String): List<String> {
        val chapterDir = File(context.cacheDir, "local_comic_cache/$bookId/$chapterId")
        if (chapterDir.exists() && chapterDir.listFiles()?.isNotEmpty() == true) {
            val cachedFiles = chapterDir.listFiles { _, name -> isImageFile(name) }
            if (!cachedFiles.isNullOrEmpty()) {
                return cachedFiles.map { it.absolutePath }
                    .sortedWith(LocalFileResolver.naturalOrderComparator)
                    .map { "file://$it" }
            }
        }

        chapterDir.mkdirs()
        val extractedFiles = mutableListOf<File>()

        context.contentResolver.openInputStream(uri)?.use { inStream ->
            ZipInputStream(inStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && isImageFile(entry.name)) {
                        val matchesPrefix = if (prefix.isEmpty()) {
                            !entry.name.contains('/')
                        } else {
                            entry.name.startsWith("$prefix/") || entry.name.startsWith(prefix)
                        }

                        if (matchesPrefix) {
                            val fileName = entry.name.substringAfterLast('/')
                            val targetFile = File(chapterDir, fileName)
                            targetFile.outputStream().use { out -> zis.copyTo(out) }
                            extractedFiles.add(targetFile)
                        }
                    }
                    entry = zis.nextEntry
                }
            }
        }

        return extractedFiles.map { it.absolutePath }
            .sortedWith(LocalFileResolver.naturalOrderComparator)
            .map { "file://$it" }
    }

    /**
     * Quét thư mục ảnh trên máy (DocumentTree)
     */
    fun scanComicFolder(context: Context, treeUri: Uri, bookId: String): ComicMetadata {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return ComicMetadata(title = "Truyện tranh", chapters = emptyList())
        val title = rootDoc.name ?: "Truyện tranh nội bộ"
        val children = rootDoc.listFiles()

        // Nếu thư mục gốc có chứa file video, đây không phải truyện tranh thuần
        val hasDirectVideos = children.any { it.isFile && LocalFileResolver.isVideoFile(it.name ?: "") }
        if (hasDirectVideos) {
            return ComicMetadata(title = title, coverUrl = null, chapters = emptyList())
        }

        val subDirs = children.filter {
            it.isDirectory && it.listFiles().any { f -> f.isFile && isImageFile(f.name ?: "") }
        }.sortedWith { d1, d2 -> LocalFileResolver.naturalOrderComparator.compare(d1.name ?: "", d2.name ?: "") }

        val chapters = mutableListOf<EpisodeData>()
        var coverUrl: String? = null

        if (subDirs.isNotEmpty()) {
            for ((idx, dir) in subDirs.withIndex()) {
                val dirName = dir.name ?: "Chương ${idx + 1}"
                val dataJson = buildString {
                    append("{\"type\":\"comic_folder\",")
                    append("\"uri\":\"").append(dir.uri.toString()).append("\"}")
                }
                chapters.add(
                    EpisodeData(
                        id = "chap_${idx + 1}",
                        name = dirName,
                        slug = "chap_${idx + 1}",
                        number = idx + 1,
                        data = Json.parseToJsonElement(dataJson)
                    )
                )

                // Lấy ảnh đầu tiên của chương đầu làm cover nếu có
                if (coverUrl == null) {
                    val firstImage = dir.listFiles().firstOrNull { it.isFile && isImageFile(it.name ?: "") }
                    if (firstImage != null) {
                        coverUrl = firstImage.uri.toString()
                    }
                }
            }
        } else {
            // Thư mục chỉ chứa ảnh trực tiếp
            val directImages = children.filter { it.isFile && isImageFile(it.name ?: "") }
            // Loại trừ trường hợp chỉ có ảnh bìa/poster/cover
            val nonCoverImages = directImages.filterNot { img ->
                val base = (img.name ?: "").substringBeforeLast('.').lowercase()
                base in setOf("poster", "cover", "folder", "thumb", "backdrop", "fanart")
            }
            if (nonCoverImages.isNotEmpty()) {
                val dataJson = buildString {
                    append("{\"type\":\"comic_folder\",")
                    append("\"uri\":\"").append(rootDoc.uri.toString()).append("\"}")
                }
                chapters.add(
                    EpisodeData(
                        id = "chap_1",
                        name = title,
                        slug = "chap_1",
                        number = 1,
                        data = Json.parseToJsonElement(dataJson)
                    )
                )
                coverUrl = directImages.firstOrNull()?.uri?.toString()
            }
        }

        return ComicMetadata(
            title = title,
            coverUrl = coverUrl,
            chapters = chapters
        )
    }

    /**
     * Lấy danh sách ảnh từ thư mục DocumentTree của 1 chương
     */
    fun getFolderChapterImages(context: Context, folderUri: Uri): List<String> {
        val dir = DocumentFile.fromTreeUri(context, folderUri)
            ?: DocumentFile.fromSingleUri(context, folderUri)
            ?: return emptyList()

        val imageFiles = dir.listFiles().filter { it.isFile && isImageFile(it.name ?: "") }
            .sortedWith { f1, f2 -> LocalFileResolver.naturalOrderComparator.compare(f1.name ?: "", f2.name ?: "") }

        return imageFiles.map { it.uri.toString() }
    }
}
