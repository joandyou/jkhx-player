package com.jkhx.player.local

import com.jkhx.player.core.database.entity.RemoteServerEntity
import com.jkhx.player.core.model.EpisodeData
import com.jkhx.player.core.remote.client.RemoteClientFactory
import com.jkhx.player.core.remote.client.WebDavClient
import com.jkhx.player.core.remote.model.RemoteFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

data class RemoteFolderScanResult(
    val folderName: String,
    val episodes: List<EpisodeData>,
    val coverUrl: String? = null
)

object RemoteFolderScanner {

    suspend fun scanRemoteFolder(
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

            // 1. Phân loại tệp ở thư mục gốc
            for (item in rootItems) {
                if (!item.isDirectory) {
                    if (LocalFileResolver.isVideoFile(item.name)) {
                        allVideoItems.add(item)
                    } else if (LocalFileResolver.isSubtitleFile(item.name)) {
                        allSubtitleItems.add(item)
                    } else if (LocalComicParser.isImageFile(item.name)) {
                        imageItems.add(item)
                    }
                }
            }

            // 2. Quét các thư mục con cấp 1 (Season 1, Season 2...)
            val subDirs = rootItems.filter { it.isDirectory && !it.name.startsWith(".") }
            for (subDir in subDirs) {
                val subResult = client.listFiles(subDir.path)
                if (subResult.isSuccess) {
                    val subItems = subResult.getOrDefault(emptyList())
                    for (subItem in subItems) {
                        if (!subItem.isDirectory) {
                            if (LocalFileResolver.isVideoFile(subItem.name)) {
                                allVideoItems.add(subItem)
                            } else if (LocalFileResolver.isSubtitleFile(subItem.name)) {
                                allSubtitleItems.add(subItem)
                            }
                        }
                    }
                }
            }

            if (allVideoItems.isEmpty()) {
                return@withContext Result.failure(Exception("Không tìm thấy file video nào trong thư mục này"))
            }

            // Sắp xếp tự nhiên
            val sortedVideos = allVideoItems.sortedWith { a, b ->
                LocalFileResolver.naturalOrderComparator.compare(a.name, b.name)
            }

            // Tìm ảnh bìa
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
                        "${server.protocol}://${server.id}/${matchedCover.path}"
                    }
                }
            }

            val episodes = mutableListOf<EpisodeData>()
            for ((index, video) in sortedVideos.withIndex()) {
                val baseName = video.name.substringBeforeLast('.')

                val matchedSub = allSubtitleItems.firstOrNull { sub ->
                    val subBase = sub.name.substringBeforeLast('.')
                    subBase.equals(baseName, ignoreCase = true) ||
                    subBase.startsWith(baseName, ignoreCase = true) ||
                    baseName.startsWith(subBase, ignoreCase = true)
                }

                val epDataJson = buildString {
                    append("{\"type\":\"remote\",")
                    append("\"protocol\":\"").append(server.protocol).append("\",")
                    append("\"serverId\":\"").append(server.id).append("\",")
                    append("\"path\":\"").append(video.path).append("\"")
                    if (matchedSub != null) {
                        append(",\"subPath\":\"").append(matchedSub.path).append("\"")
                    }
                    append("}")
                }

                episodes.add(
                    EpisodeData(
                        id = "remote_ep_${index + 1}",
                        name = baseName,
                        slug = "remote_ep_${index + 1}",
                        number = index + 1,
                        data = Json.parseToJsonElement(epDataJson)
                    )
                )
            }

            val folderName = if (folderPath.isBlank() || folderPath == "/") {
                server.name
            } else {
                folderPath.trimEnd('/').substringAfterLast('/')
            }

            Result.success(
                RemoteFolderScanResult(
                    folderName = folderName,
                    episodes = episodes,
                    coverUrl = coverUrl
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
