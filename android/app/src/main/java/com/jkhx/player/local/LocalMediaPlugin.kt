package com.jkhx.player.local

import android.content.Context
import android.net.Uri
import com.jkhx.player.core.database.AppDatabase
import com.jkhx.player.core.database.dao.LocalLibraryDao
import com.jkhx.player.core.model.*
import com.jkhx.player.core.remote.client.RemoteClientFactory
import com.jkhx.player.plugin.api.LoadedPlugin
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class LocalMediaPlugin(
    private val context: Context,
    private val localLibraryDao: LocalLibraryDao
) : LoadedPlugin {

    override val id: String = "local"
    override val name: String = "Thư viện Offline"
    override val version: String = "1.0.0"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun getHome(): HomeResult = withContext(Dispatchers.IO) {
        val allItems = localLibraryDao.getAllSync()
        if (allItems.isEmpty()) {
            return@withContext HomeResult(sections = emptyList())
        }

        val videoItems = allItems.filter { it.type == "movie" || it.type == "series" }.map { it.toMovieItem() }
        val novelItems = allItems.filter { it.type == "novel" }.map { it.toMovieItem() }
        val comicItems = allItems.filter { it.type == "comic" }.map { it.toMovieItem() }

        val sections = mutableListOf<HomeSection>()
        if (videoItems.isNotEmpty()) {
            sections.add(HomeSection(title = "Phim & Video Nội Bộ", items = videoItems, type = "movie"))
        }
        if (novelItems.isNotEmpty()) {
            sections.add(HomeSection(title = "Truyện Chữ Nội Bộ", items = novelItems, type = "novel"))
        }
        if (comicItems.isNotEmpty()) {
            sections.add(HomeSection(title = "Truyện Tranh Nội Bộ", items = comicItems, type = "comic"))
        }

        HomeResult(sections = sections)
    }

    override suspend fun search(keyword: String, page: Int): SearchResult = withContext(Dispatchers.IO) {
        val allItems = localLibraryDao.getAllSync()
        val query = keyword.trim().lowercase()
        val filtered = allItems.filter {
            it.title.lowercase().contains(query) || (it.author?.lowercase()?.contains(query) == true)
        }.map { it.toMovieItem() }

        SearchResult(items = filtered, hasNext = false)
    }

    override suspend fun getDetail(slug: String): MovieDetail = withContext(Dispatchers.IO) {
        val item = localLibraryDao.getById(slug)
            ?: throw Exception("Không tìm thấy mục nội bộ có mã: $slug")

        val episodes: List<EpisodeData> = try {
            json.decodeFromString<List<EpisodeData>>(item.episodesJson)
        } catch (_: Exception) {
            emptyList()
        }

        val server = EpisodeServer(
            serverName = "Nội bộ",
            episodes = episodes
        )

        val genreList = item.genres?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val yearInt = item.year?.take(4)?.toIntOrNull()

        MovieDetail(
            slug = item.id,
            name = item.title,
            originName = item.originalTitle ?: item.author ?: item.title,
            type = item.type,
            posterUrl = item.coverUrl,
            backdropUrl = item.backdropUrl ?: item.coverUrl,
            description = item.description ?: "Tập tin trên thiết bị",
            year = yearInt,
            rating = item.rating,
            genres = genreList,
            tmdbId = item.tmdbId?.toString(),
            episodes = listOf(server)
        )
    }

    override suspend fun getStream(episodeData: EpisodeData): EpisodeStream = withContext(Dispatchers.IO) {
        val dataElem = episodeData.data ?: throw Exception("Dữ liệu tập tin nội bộ trống")

        val obj = try {
            dataElem.jsonObject
        } catch (e: Exception) {
            throw Exception("Lỗi đọc cấu trúc tập tin: ${e.message}")
        }

        val type = obj["type"]?.jsonPrimitive?.content ?: "video"

        when (type) {
            "video" -> {
                val uriStr = obj["uri"]?.jsonPrimitive?.content ?: throw Exception("Thiếu đường dẫn video")
                val subUriStr = obj["subUri"]?.jsonPrimitive?.content

                val subtitles = mutableListOf<Subtitle>()
                if (!subUriStr.isNullOrBlank()) {
                    subtitles.add(
                        Subtitle(
                            label = "Phụ đề đi kèm",
                            language = "vi",
                            url = subUriStr,
                            format = "srt"
                        )
                    )
                }

                EpisodeStream(
                    type = "mp4",
                    url = uriStr,
                    subtitles = subtitles
                )
            }

            "remote" -> {
                val serverId = obj["serverId"]?.jsonPrimitive?.content ?: throw Exception("Thiếu mã máy chủ")
                val path = obj["path"]?.jsonPrimitive?.content ?: throw Exception("Thiếu đường dẫn tệp tin")
                val subPath = obj["subPath"]?.jsonPrimitive?.content
                val protocol = obj["protocol"]?.jsonPrimitive?.content ?: "smb"

                val server = com.jkhx.player.core.database.AppDatabase.getInstance(context).remoteServerDao().getById(serverId)
                    ?: throw Exception("Không tìm thấy cấu hình máy chủ: $serverId")

                val client = com.jkhx.player.core.remote.client.RemoteClientFactory.getClient(server)

                val (streamUrl, headers) = when (protocol.lowercase()) {
                    "webdav" -> {
                        val webdavClient = client as com.jkhx.player.core.remote.client.WebDavClient
                        val fullUrl = webdavClient.getFullFileUrl(path)
                        val hdrs = mutableMapOf<String, String>()
                        val u = server.username
                        val p = server.password ?: ""
                        if (!u.isNullOrBlank()) {
                            val creds = okhttp3.Credentials.basic(u, p, Charsets.UTF_8)
                            hdrs["Authorization"] = creds
                        }
                        fullUrl to hdrs
                    }
                    "sftp" -> {
                        "sftp://$serverId/$path" to emptyMap<String, String>()
                    }
                    else -> {
                        "smb://$serverId/$path" to emptyMap<String, String>()
                    }
                }

                val subtitles = mutableListOf<Subtitle>()
                if (!subPath.isNullOrBlank()) {
                    val subUrl = if (protocol.lowercase() == "webdav") {
                        (client as com.jkhx.player.core.remote.client.WebDavClient).getFullFileUrl(subPath)
                    } else {
                        "$protocol://$serverId/$subPath"
                    }
                    subtitles.add(
                        Subtitle(
                            label = "Phụ đề mạng",
                            language = "vi",
                            url = subUrl,
                            format = subPath.substringAfterLast('.', "srt")
                        )
                    )
                }

                EpisodeStream(
                    type = "mp4",
                    url = streamUrl,
                    headers = headers,
                    subtitles = subtitles
                )
            }

            "novel_txt" -> {
                val uriStr = obj["uri"]?.jsonPrimitive?.content ?: throw Exception("Thiếu đường dẫn file txt")
                val offset = obj["offset"]?.jsonPrimitive?.longOrNull ?: 0L
                val length = obj["length"]?.jsonPrimitive?.longOrNull ?: 0L
                val charset = obj["charset"]?.jsonPrimitive?.content ?: "UTF-8"

                val content = LocalNovelParser.readTxtChapterContent(
                    context = context,
                    uri = Uri.parse(uriStr),
                    offset = offset,
                    length = length,
                    charsetName = charset
                )

                EpisodeStream(
                    type = "novel",
                    content = content
                )
            }

            "novel_epub" -> {
                val uriStr = obj["uri"]?.jsonPrimitive?.content ?: throw Exception("Thiếu đường dẫn file epub")
                val entryName = obj["entryName"]?.jsonPrimitive?.content ?: throw Exception("Thiếu entry epub")

                val content = LocalNovelParser.readEpubChapterContent(
                    context = context,
                    uri = Uri.parse(uriStr),
                    entryName = entryName
                )

                EpisodeStream(
                    type = "novel",
                    content = content
                )
            }

            "comic_zip" -> {
                val uriStr = obj["uri"]?.jsonPrimitive?.content ?: throw Exception("Thiếu đường dẫn zip")
                val bookId = obj["bookId"]?.jsonPrimitive?.content ?: "comic_${System.currentTimeMillis()}"
                val prefix = obj["prefix"]?.jsonPrimitive?.content ?: ""

                val imageList = LocalComicParser.extractZipChapterImages(
                    context = context,
                    uri = Uri.parse(uriStr),
                    bookId = bookId,
                    chapterId = episodeData.id,
                    prefix = prefix
                )

                EpisodeStream(
                    type = "image_list",
                    images = imageList
                )
            }

            "comic_folder" -> {
                val uriStr = obj["uri"]?.jsonPrimitive?.content ?: throw Exception("Thiếu đường dẫn thư mục ảnh")
                val imageList = LocalComicParser.getFolderChapterImages(
                    context = context,
                    folderUri = Uri.parse(uriStr)
                )

                EpisodeStream(
                    type = "image_list",
                    images = imageList
                )
            }

            "remote_comic_folder" -> {
                val serverId = obj["serverId"]?.jsonPrimitive?.content ?: throw Exception("Thiếu serverId")
                val remotePath = obj["path"]?.jsonPrimitive?.content ?: throw Exception("Thiếu đường dẫn thư mục ảnh")

                val server = AppDatabase.getInstance(context).remoteServerDao().getById(serverId)
                    ?: throw Exception("Không tìm thấy cấu hình máy chủ")

                val client = RemoteClientFactory.getClient(server)
                val filesResult = client.listFiles(remotePath)
                if (filesResult.isFailure) {
                    throw filesResult.exceptionOrNull() ?: Exception("Không thể đọc thư mục ảnh từ xa")
                }

                val imageItems = filesResult.getOrDefault(emptyList())
                    .filter { !it.isDirectory && LocalComicParser.isImageFile(it.name) }
                    .sortedWith { a, b -> LocalFileResolver.naturalOrderComparator.compare(a.name, b.name) }

                if (imageItems.isEmpty()) {
                    throw Exception("Không tìm thấy trang ảnh nào trong chương này")
                }

                val pageCacheDir = File(
                    context.cacheDir,
                    "remote_comic_pages/${serverId}_${remotePath.hashCode().toUInt()}"
                ).apply { mkdirs() }

                val imageUris = mutableListOf<String>()
                for ((idx, imgItem) in imageItems.withIndex()) {
                    val ext = imgItem.name.substringAfterLast('.', "jpg").lowercase()
                    val pageFile = File(pageCacheDir, String.format("%04d.%s", idx, ext))
                    if (!pageFile.exists() || pageFile.length() == 0L) {
                        val tmpFile = File(pageCacheDir, "${pageFile.name}.tmp")
                        client.openInputStream(imgItem.path).use { input ->
                            tmpFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (!tmpFile.renameTo(pageFile)) {
                            tmpFile.copyTo(pageFile, overwrite = true)
                            tmpFile.delete()
                        }
                    }
                    if (pageFile.exists() && pageFile.length() > 0L) {
                        imageUris.add(Uri.fromFile(pageFile).toString())
                    }
                }

                EpisodeStream(
                    type = "image_list",
                    images = imageUris
                )
            }

            else -> throw Exception("Định dạng nội bộ không hỗ trợ: $type")
        }
    }

    override fun close() {}

    private fun com.jkhx.player.core.database.entity.LocalLibraryEntity.toMovieItem(): MovieItem {
        val yearInt = year?.take(4)?.toIntOrNull()
        return MovieItem(
            slug = id,
            name = title,
            originName = originalTitle ?: author ?: title,
            type = type,
            posterUrl = coverUrl,
            thumbUrl = coverUrl,
            year = yearInt,
            rating = rating,
            tmdbId = tmdbId?.toString(),
            providerId = "local"
        )
    }
}
