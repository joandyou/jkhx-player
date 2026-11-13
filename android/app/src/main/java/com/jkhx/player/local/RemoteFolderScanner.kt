package com.jkhx.player.local

import android.content.Context
import android.net.Uri
import com.jkhx.player.core.database.entity.LocalLibraryEntity
import com.jkhx.player.core.database.entity.RemoteServerEntity
import com.jkhx.player.core.model.EpisodeData
import com.jkhx.player.core.remote.client.RemoteClient
import com.jkhx.player.core.remote.client.RemoteClientFactory
import com.jkhx.player.core.remote.client.WebDavClient
import com.jkhx.player.core.remote.model.RemoteFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

data class RemoteFolderScanResult(
    val folderName: String,
    val episodes: List<EpisodeData> = emptyList(),
    val coverUrl: String? = null,
    val comics: List<LocalLibraryEntity> = emptyList(),
    val novels: List<LocalLibraryEntity> = emptyList()
)

object RemoteFolderScanner {

    private val COMIC_ARCHIVE_EXTS = setOf("cbz", "zip")
    private val NOVEL_EXTS = setOf("epub", "txt")

    fun isComicArchive(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return COMIC_ARCHIVE_EXTS.contains(ext)
    }

    fun isNovelFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return NOVEL_EXTS.contains(ext)
    }

    /**
     * Tải và lưu cache file từ Remote Server về bộ nhớ tạm để parser đọc qua Uri.
     */
    suspend fun ensureRemoteFileCached(
        context: Context,
        server: RemoteServerEntity,
        remotePath: String,
        fileName: String,
        expectedSize: Long = -1L
    ): Uri = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "remote_cache").apply { mkdirs() }
        val ext = fileName.substringAfterLast('.', "tmp").lowercase()
        val safeName = "${server.id}_${remotePath.hashCode().toUInt()}.$ext"
        val targetFile = File(cacheDir, safeName)

        if (targetFile.exists() && targetFile.length() > 0 && (expectedSize <= 0L || targetFile.length() == expectedSize)) {
            return@withContext Uri.fromFile(targetFile)
        }

        val client = RemoteClientFactory.getClient(server)
        val tmpFile = File(cacheDir, "$safeName.tmp")
        client.openInputStream(remotePath).use { input ->
            tmpFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        if (targetFile.exists()) targetFile.delete()
        if (!tmpFile.renameTo(targetFile)) {
            tmpFile.copyTo(targetFile, overwrite = true)
            tmpFile.delete()
        }
        Uri.fromFile(targetFile)
    }

    /**
     * Tải và lưu cache 1 ảnh bìa remote về local file để Coil hiển thị ổn định trên mọi giao thức.
     */
    private suspend fun cacheRemoteCover(
        context: Context,
        client: RemoteClient,
        server: RemoteServerEntity,
        imageItem: RemoteFileItem
    ): String? {
        return try {
            val coverDir = File(context.cacheDir, "remote_covers").apply { mkdirs() }
            val ext = imageItem.name.substringAfterLast('.', "jpg").lowercase()
            val coverFile = File(coverDir, "${server.id}_${imageItem.path.hashCode().toUInt()}.$ext")
            if (!coverFile.exists() || coverFile.length() == 0L) {
                client.openInputStream(imageItem.path).use { input ->
                    coverFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            if (coverFile.exists() && coverFile.length() > 0L) coverFile.absolutePath else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Nhập trực tiếp 1 tệp truyện tranh (.cbz/.zip) hoặc tiểu thuyết (.epub/.txt) từ Remote Server.
     */
    suspend fun importSingleRemoteFile(
        context: Context,
        server: RemoteServerEntity,
        item: RemoteFileItem
    ): Result<LocalLibraryEntity> = withContext(Dispatchers.IO) {
        try {
            val hashId = "remote_${server.id}_${item.path.hashCode()}"
            val localUri = ensureRemoteFileCached(context, server, item.path, item.name, item.size)
            val ext = item.name.substringAfterLast('.', "").lowercase()

            when {
                COMIC_ARCHIVE_EXTS.contains(ext) -> {
                    val meta = LocalComicParser.parseZipComic(context, localUri, item.name, hashId)
                    if (meta.chapters.isEmpty()) {
                        return@withContext Result.failure(Exception("Không tìm thấy trang ảnh nào trong ${item.name}"))
                    }
                    Result.success(
                        LocalLibraryEntity(
                            id = hashId,
                            title = meta.title,
                            type = "comic",
                            uri = "remote://${server.id}/${item.path}",
                            coverUrl = meta.coverUrl,
                            chapterCount = meta.chapters.size,
                            episodesJson = Json.encodeToString(meta.chapters)
                        )
                    )
                }

                ext == "epub" -> {
                    val meta = LocalNovelParser.parseEpubNovel(context, localUri, item.name, hashId)
                    if (meta.chapters.isEmpty()) {
                        return@withContext Result.failure(Exception("Không đọc được chương nào từ ${item.name}"))
                    }
                    Result.success(
                        LocalLibraryEntity(
                            id = hashId,
                            title = meta.title,
                            type = "novel",
                            uri = "remote://${server.id}/${item.path}",
                            coverUrl = meta.coverUrl,
                            author = meta.author,
                            description = meta.description,
                            chapterCount = meta.chapters.size,
                            episodesJson = Json.encodeToString(meta.chapters)
                        )
                    )
                }

                ext == "txt" -> {
                    val meta = LocalNovelParser.parseTxtNovel(context, localUri, item.name)
                    Result.success(
                        LocalLibraryEntity(
                            id = hashId,
                            title = meta.title,
                            type = "novel",
                            uri = "remote://${server.id}/${item.path}",
                            author = meta.author,
                            description = meta.description,
                            chapterCount = meta.chapters.size,
                            episodesJson = Json.encodeToString(meta.chapters)
                        )
                    )
                }

                else -> Result.failure(Exception("Định dạng không hỗ trợ: ${item.name}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun scanRemoteFolder(
        context: Context,
        server: RemoteServerEntity,
        folderPath: String
    ): Result<RemoteFolderScanResult> = withContext(Dispatchers.IO) {
        try {
            val client = RemoteClientFactory.getClient(server)
            val rootItemsResult = client.listFiles(folderPath)
            if (rootItemsResult.isFailure) {
                return@withContext Result.failure(rootItemsResult.exceptionOrNull() ?: Exception("Không thể đọc thư mục từ xa"))
            }

            val rootItems = rootItemsResult.getOrDefault(emptyList())
            val allVideoItems = mutableListOf<RemoteFileItem>()
            val allSubtitleItems = mutableListOf<RemoteFileItem>()
            val imageItems = mutableListOf<RemoteFileItem>()
            val comicArchiveItems = mutableListOf<RemoteFileItem>()
            val novelItems = mutableListOf<RemoteFileItem>()
            val comicChapterDirs = mutableListOf<Pair<RemoteFileItem, List<RemoteFileItem>>>()

            // 1. Phân loại tệp ở thư mục gốc
            for (item in rootItems) {
                if (!item.isDirectory) {
                    when {
                        LocalFileResolver.isVideoFile(item.name) -> allVideoItems.add(item)
                        LocalFileResolver.isSubtitleFile(item.name) -> allSubtitleItems.add(item)
                        LocalComicParser.isImageFile(item.name) -> imageItems.add(item)
                        isComicArchive(item.name) -> comicArchiveItems.add(item)
                        isNovelFile(item.name) -> novelItems.add(item)
                    }
                }
            }

            // 2. Quét các thư mục con cấp 1 (Season, Chapter truyện tranh, hoặc file truyện/tiểu thuyết con)
            val subDirs = rootItems.filter { it.isDirectory && !it.name.startsWith(".") }
            for (subDir in subDirs) {
                val subResult = client.listFiles(subDir.path)
                if (subResult.isSuccess) {
                    val subItems = subResult.getOrDefault(emptyList())
                    val subImages = mutableListOf<RemoteFileItem>()
                    var hasVideoInSub = false
                    for (subItem in subItems) {
                        if (!subItem.isDirectory) {
                            when {
                                LocalFileResolver.isVideoFile(subItem.name) -> {
                                    allVideoItems.add(subItem)
                                    hasVideoInSub = true
                                }
                                LocalFileResolver.isSubtitleFile(subItem.name) -> allSubtitleItems.add(subItem)
                                LocalComicParser.isImageFile(subItem.name) -> subImages.add(subItem)
                                isComicArchive(subItem.name) -> comicArchiveItems.add(subItem)
                                isNovelFile(subItem.name) -> novelItems.add(subItem)
                            }
                        }
                    }
                    if (!hasVideoInSub && subImages.isNotEmpty()) {
                        comicChapterDirs.add(subDir to subImages)
                    }
                }
            }

            val folderName = if (folderPath.isBlank() || folderPath == "/") {
                server.name
            } else {
                folderPath.trimEnd('/').substringAfterLast('/')
            }

            // Tìm ảnh bìa nếu có
            var coverUrl: String? = null
            if (imageItems.isNotEmpty()) {
                val priorityKeywords = listOf("poster", "cover", "folder", "thumb", "fanart")
                val matchedCover = priorityKeywords.firstNotNullOfOrNull { kw ->
                    imageItems.firstOrNull { it.name.substringBeforeLast('.').contains(kw, ignoreCase = true) }
                } ?: imageItems.firstOrNull()

                if (matchedCover != null) {
                    coverUrl = if (server.protocol == "webdav") {
                        (client as WebDavClient).getFullFileUrl(matchedCover.path)
                    } else {
                        cacheRemoteCover(context, client, server, matchedCover)
                    }
                }
            }

            // 3. Xây dựng danh sách Video
            val episodes = mutableListOf<EpisodeData>()
            if (allVideoItems.isNotEmpty()) {
                val sortedVideos = allVideoItems.sortedWith { a, b ->
                    LocalFileResolver.naturalOrderComparator.compare(a.name, b.name)
                }
                for ((index, video) in sortedVideos.withIndex()) {
                    val baseName = video.name.substringBeforeLast('.')
                    val matchedSub = allSubtitleItems.firstOrNull { sub ->
                        val subBase = sub.name.substringBeforeLast('.')
                        subBase.equals(baseName, ignoreCase = true) ||
                            subBase.startsWith(baseName, ignoreCase = true) ||
                            baseName.startsWith(subBase, ignoreCase = true)
                    }
                    val epJson = buildJsonObject {
                        put("type", "remote")
                        put("protocol", server.protocol)
                        put("serverId", server.id)
                        put("path", video.path)
                        if (matchedSub != null) {
                            put("subPath", matchedSub.path)
                        }
                    }
                    episodes.add(
                        EpisodeData(
                            id = "remote_ep_${index + 1}",
                            name = baseName,
                            slug = "remote_ep_${index + 1}",
                            number = index + 1,
                            data = epJson
                        )
                    )
                }
            }

            // 4. Xây dựng danh sách Truyện tranh (.cbz/.zip & thư mục ảnh)
            val comics = mutableListOf<LocalLibraryEntity>()
            for (archive in comicArchiveItems) {
                importSingleRemoteFile(context, server, archive).getOrNull()?.let { comics.add(it) }
            }

            if (comicChapterDirs.isNotEmpty()) {
                val sortedChapters = comicChapterDirs.sortedWith { a, b ->
                    LocalFileResolver.naturalOrderComparator.compare(a.first.name, b.first.name)
                }
                val chapterEpisodes = sortedChapters.mapIndexed { index, (dirItem, _) ->
                    EpisodeData(
                        id = "remote_ch_${index + 1}",
                        name = dirItem.name,
                        slug = "remote_ch_${index + 1}",
                        number = index + 1,
                        data = buildJsonObject {
                            put("type", "remote_comic_folder")
                            put("serverId", server.id)
                            put("path", dirItem.path)
                        }
                    )
                }
                val firstChapterFirstImage = sortedChapters.firstOrNull()?.second
                    ?.sortedWith { a, b -> LocalFileResolver.naturalOrderComparator.compare(a.name, b.name) }
                    ?.firstOrNull()
                val comicCover = coverUrl ?: firstChapterFirstImage?.let {
                    cacheRemoteCover(context, client, server, it)
                }
                comics.add(
                    LocalLibraryEntity(
                        id = "remote_comic_${server.id}_${folderPath.hashCode()}",
                        title = folderName,
                        type = "comic",
                        uri = "remote://${server.id}/$folderPath",
                        coverUrl = comicCover,
                        chapterCount = chapterEpisodes.size,
                        episodesJson = Json.encodeToString(chapterEpisodes)
                    )
                )
            } else if (allVideoItems.isEmpty() && imageItems.isNotEmpty()) {
                val sortedImages = imageItems.sortedWith { a, b ->
                    LocalFileResolver.naturalOrderComparator.compare(a.name, b.name)
                }
                val comicCover = coverUrl ?: sortedImages.firstOrNull()?.let {
                    cacheRemoteCover(context, client, server, it)
                }
                val singleChapter = EpisodeData(
                    id = "remote_ch_1",
                    name = folderName,
                    slug = "remote_ch_1",
                    number = 1,
                    data = buildJsonObject {
                        put("type", "remote_comic_folder")
                        put("serverId", server.id)
                        put("path", folderPath)
                    }
                )
                comics.add(
                    LocalLibraryEntity(
                        id = "remote_comic_${server.id}_${folderPath.hashCode()}",
                        title = folderName,
                        type = "comic",
                        uri = "remote://${server.id}/$folderPath",
                        coverUrl = comicCover,
                        chapterCount = 1,
                        episodesJson = Json.encodeToString(listOf(singleChapter))
                    )
                )
            }

            // 5. Xây dựng danh sách Tiểu thuyết (.epub/.txt)
            val novels = mutableListOf<LocalLibraryEntity>()
            for (novel in novelItems) {
                importSingleRemoteFile(context, server, novel).getOrNull()?.let { novels.add(it) }
            }

            if (episodes.isEmpty() && comics.isEmpty() && novels.isEmpty()) {
                return@withContext Result.failure(
                    Exception("Không tìm thấy video, truyện tranh hoặc tiểu thuyết nào trong thư mục này")
                )
            }

            Result.success(
                RemoteFolderScanResult(
                    folderName = folderName,
                    episodes = episodes,
                    coverUrl = coverUrl,
                    comics = comics,
                    novels = novels
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
