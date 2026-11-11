package com.jkhx.player.local

import android.content.Context
import android.net.Uri
import com.jkhx.player.core.model.EpisodeData
import kotlinx.serialization.json.Json
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.*
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

object LocalNovelParser {

    private val CHAPTER_TITLE_REGEX = Regex(
        "^\\s*(chương|chapter|hồi|quyển|phần|tiết|mục)\\s+([0-9ivxlcdm一二三四五六七八九十]+|[\\p{L}\\d]+)[:\\s.-].*",
        RegexOption.IGNORE_CASE
    )

    private val SIMPLE_CHAPTER_REGEX = Regex(
        "^\\s*(chương|chapter|hồi|quyển|phần|tiết|mục)\\s+([0-9ivxlcdm]+|[\\p{L}\\d]+)\\b.*",
        RegexOption.IGNORE_CASE
    )

    private val HTML_TAG_REGEX = Regex("<[^>]*>")

    private val HTML_ENTITIES = mapOf(
        "&nbsp;" to " ",
        "&amp;" to "&",
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&#39;" to "'",
        "&apos;" to "'",
        "&hellip;" to "...",
        "&mdash;" to "—",
        "&ndash;" to "–"
    )

    data class NovelMetadata(
        val title: String,
        val author: String? = null,
        val description: String? = null,
        val coverUrl: String? = null,
        val chapters: List<EpisodeData>
    )

    /**
     * Nhận diện bảng mã tiếng Việt từ byte đầu của InputStream
     */
    fun detectCharset(bytes: ByteArray): Charset {
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 0xFF && b1 == 0xFE) return Charsets.UTF_16LE
            if (b0 == 0xFE && b1 == 0xFF) return Charsets.UTF_16BE
        }
        if (bytes.size >= 3) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            val b2 = bytes[2].toInt() and 0xFF
            if (b0 == 0xEF && b1 == 0xBB && b2 == 0xBF) return Charsets.UTF_8
        }
        return Charsets.UTF_8
    }

    /**
     * Phân tích file TXT thành danh sách chương bằng cách quét offset (không nạp toàn bộ file lớn vào RAM)
     */
    fun parseTxtNovel(context: Context, uri: Uri, fileName: String): NovelMetadata {
        val headerBytes = ByteArray(1024)
        var bytesRead = 0
        context.contentResolver.openInputStream(uri)?.use {
            bytesRead = it.read(headerBytes)
        }
        val safeHeader = if (bytesRead > 0) headerBytes.copyOf(bytesRead) else headerBytes
        val charset = detectCharset(safeHeader)

        data class TmpChapter(val title: String, val startChar: Long, var lengthChar: Long = 0L)
        val chapters = mutableListOf<TmpChapter>()

        var totalChars = 0L
        var lineCount = 0

        context.contentResolver.openInputStream(uri)?.use { inStream ->
            BufferedReader(InputStreamReader(inStream, charset)).use { reader ->
                var currentLine: String?
                var currentChapterStart = 0L
                var currentChapterTitle = "Phần mở đầu"

                while (reader.readLine().also { currentLine = it } != null) {
                    val line = currentLine ?: break
                    lineCount++
                    val trimmed = line.trim()

                    val isChapterHeader = trimmed.length <= 80 && (
                        CHAPTER_TITLE_REGEX.matches(trimmed) || SIMPLE_CHAPTER_REGEX.matches(trimmed)
                    )

                    if (isChapterHeader) {
                        if (chapters.isNotEmpty()) {
                            val last = chapters.last()
                            last.lengthChar = totalChars - last.startChar
                        } else if (totalChars > 0) {
                            chapters.add(TmpChapter(currentChapterTitle, currentChapterStart, totalChars - currentChapterStart))
                        }
                        currentChapterTitle = trimmed
                        currentChapterStart = totalChars
                        chapters.add(TmpChapter(currentChapterTitle, currentChapterStart, 0L))
                    }

                    // +1 cho ký tự xuống dòng \n
                    totalChars += line.length + 1L
                }

                if (chapters.isNotEmpty()) {
                    val last = chapters.last()
                    last.lengthChar = totalChars - last.startChar
                } else {
                    // Nếu file không có tiêu đề chương rõ ràng: chia nhỏ mỗi đoạn ~3500 từ (~18.000 ký tự)
                    val chunkSize = 18000L
                    var offset = 0L
                    var partIdx = 1
                    while (offset < totalChars) {
                        val len = (totalChars - offset).coerceAtMost(chunkSize)
                        chapters.add(TmpChapter("Phần $partIdx", offset, len))
                        offset += len
                        partIdx++
                    }
                }
            }
        }

        val baseTitle = fileName.substringBeforeLast('.')
        val episodeList = chapters.mapIndexed { idx, ch ->
            val dataJson = buildString {
                append("{\"type\":\"novel_txt\",")
                append("\"uri\":\"").append(uri.toString()).append("\",")
                append("\"offset\":").append(ch.startChar).append(",")
                append("\"length\":").append(ch.lengthChar).append(",")
                append("\"charset\":\"").append(charset.name()).append("\"}")
            }
            EpisodeData(
                id = "chap_${idx + 1}",
                name = ch.title,
                slug = "chap_${idx + 1}",
                number = idx + 1,
                data = Json.parseToJsonElement(dataJson)
            )
        }

        return NovelMetadata(
            title = baseTitle,
            chapters = episodeList
        )
    }

    /**
     * Đọc nội dung 1 chương của file TXT theo offset và length
     */
    fun readTxtChapterContent(context: Context, uri: Uri, offset: Long, length: Long, charsetName: String): String {
        val charset = try { Charset.forName(charsetName) } catch (_: Exception) { Charsets.UTF_8 }
        val sb = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { inStream ->
            BufferedReader(InputStreamReader(inStream, charset)).use { reader ->
                var skipped = 0L
                while (skipped < offset) {
                    val n = reader.skip(offset - skipped)
                    if (n <= 0) break
                    skipped += n
                }
                val buffer = CharArray(4096)
                var remaining = if (length > 0) length else Long.MAX_VALUE
                while (remaining > 0) {
                    val toRead = remaining.coerceAtMost(buffer.size.toLong()).toInt()
                    val read = reader.read(buffer, 0, toRead)
                    if (read <= 0) break
                    sb.append(buffer, 0, read)
                    remaining -= read
                }
            }
        }
        return sb.toString().trim()
    }

    /**
     * Phân tích file EPUB bằng thuần Kotlin XmlPullParser & ZipInputStream
     */
    fun parseEpubNovel(context: Context, uri: Uri, fileName: String, bookId: String): NovelMetadata {
        var opfPath = ""
        val manifestItems = mutableMapOf<String, Pair<String, String>>() // id -> (href, media-type)
        val spineIdRefs = mutableListOf<String>()
        var title = fileName.substringBeforeLast('.')
        var author: String? = null
        var description: String? = null
        var coverHref: String? = null
        var coverSavedPath: String? = null

        // Bước 1: Tìm opfPath từ META-INF/container.xml
        context.contentResolver.openInputStream(uri)?.use { inStream ->
            ZipInputStream(inStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.equals("META-INF/container.xml", ignoreCase = true)) {
                        opfPath = parseContainerXml(zis)
                        break
                    }
                    entry = zis.nextEntry
                }
            }
        }

        if (opfPath.isBlank()) {
            opfPath = "content.opf" // fallback mặc định
        }

        val opfDir = if (opfPath.contains('/')) opfPath.substringBeforeLast('/') + "/" else ""

        // Bước 2: Đọc file OPF để lấy metadata, manifest, spine
        context.contentResolver.openInputStream(uri)?.use { inStream ->
            ZipInputStream(inStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.equals(opfPath, ignoreCase = true)) {
                        val factory = XmlPullParserFactory.newInstance()
                        val parser = factory.newPullParser()
                        val opfBytes = zis.readBytes()
                        parser.setInput(ByteArrayInputStream(opfBytes), "UTF-8")

                        var eventType = parser.eventType
                        var currentTag = ""
                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            if (eventType == XmlPullParser.START_TAG) {
                                currentTag = parser.name.lowercase()
                                when (currentTag) {
                                    "item" -> {
                                        val id = parser.getAttributeValue(null, "id") ?: ""
                                        val href = parser.getAttributeValue(null, "href") ?: ""
                                        val mediaType = parser.getAttributeValue(null, "media-type") ?: ""
                                        val properties = parser.getAttributeValue(null, "properties") ?: ""
                                        if (id.isNotBlank() && href.isNotBlank()) {
                                            manifestItems[id] = Pair(href, mediaType)
                                            if (properties.contains("cover-image") || id.contains("cover", ignoreCase = true)) {
                                                coverHref = href
                                            }
                                        }
                                    }
                                    "itemref" -> {
                                        val idref = parser.getAttributeValue(null, "idref") ?: ""
                                        if (idref.isNotBlank()) {
                                            spineIdRefs.add(idref)
                                        }
                                    }
                                }
                            } else if (eventType == XmlPullParser.TEXT) {
                                val text = parser.text?.trim().orEmpty()
                                if (text.isNotBlank()) {
                                    when (currentTag) {
                                        "dc:title", "title" -> if (title == fileName.substringBeforeLast('.')) title = text
                                        "dc:creator", "creator" -> if (author == null) author = text
                                        "dc:description", "description" -> if (description == null) description = text
                                    }
                                }
                            }
                            eventType = parser.next()
                        }
                        break
                    }
                    entry = zis.nextEntry
                }
            }
        }

        // Bước 3: Trích xuất ảnh bìa nếu có
        if (coverHref != null) {
            val fullCoverPath = resolveZipPath(opfDir, coverHref)
            try {
                context.contentResolver.openInputStream(uri)?.use { inStream ->
                    ZipInputStream(inStream).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (entry.name.equals(fullCoverPath, ignoreCase = true)) {
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

        // Bước 4: Tạo danh sách EpisodeData từ Spine
        val episodeList = mutableListOf<EpisodeData>()
        for ((idx, idref) in spineIdRefs.withIndex()) {
            val itemInfo = manifestItems[idref] ?: continue
            val relHref = itemInfo.first
            val fullEntryPath = resolveZipPath(opfDir, relHref)
            val dataJson = buildString {
                append("{\"type\":\"novel_epub\",")
                append("\"uri\":\"").append(uri.toString()).append("\",")
                append("\"entryName\":\"").append(fullEntryPath).append("\"}")
            }
            episodeList.add(
                EpisodeData(
                    id = "chap_${idx + 1}",
                    name = "Chương ${idx + 1}",
                    slug = "chap_${idx + 1}",
                    number = idx + 1,
                    data = Json.parseToJsonElement(dataJson)
                )
            )
        }

        return NovelMetadata(
            title = title,
            author = author,
            description = description,
            coverUrl = coverSavedPath,
            chapters = episodeList
        )
    }

    /**
     * Đọc nội dung 1 chương EPUB từ ZIP entry và làm sạch thẻ HTML sang văn bản thuần
     */
    fun readEpubChapterContent(context: Context, uri: Uri, entryName: String): String {
        var rawHtml = ""
        context.contentResolver.openInputStream(uri)?.use { inStream ->
            ZipInputStream(inStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.equals(entryName, ignoreCase = true)) {
                        rawHtml = String(zis.readBytes(), Charsets.UTF_8)
                        break
                    }
                    entry = zis.nextEntry
                }
            }
        }
        if (rawHtml.isBlank()) return ""

        return cleanHtmlToPlainText(rawHtml)
    }

    fun cleanHtmlToPlainText(html: String): String {
        var text = html
            .replace(Regex("<(script|style)[^>]*?>.*?</\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n\n")
            .replace(Regex("(?i)</div>"), "\n\n")
            .replace(Regex("(?i)</h[1-6]>"), "\n\n")
            .replace(HTML_TAG_REGEX, "")

        for ((entity, replacement) in HTML_ENTITIES) {
            text = text.replace(entity, replacement)
        }

        return text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }

    private fun parseContainerXml(inputStream: InputStream): String {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name.equals("rootfile", ignoreCase = true)) {
                val fullPath = parser.getAttributeValue(null, "full-path")
                if (!fullPath.isNullOrBlank()) {
                    return fullPath
                }
            }
            eventType = parser.next()
        }
        return ""
    }

    private fun resolveZipPath(baseDir: String, href: String): String {
        if (href.startsWith("/")) return href.removePrefix("/")
        val combined = baseDir + href
        val parts = combined.split('/')
        val stack = mutableListOf<String>()
        for (part in parts) {
            when (part) {
                "", "." -> {}
                ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
                else -> stack.add(part)
            }
        }
        return stack.joinToString("/")
    }
}
