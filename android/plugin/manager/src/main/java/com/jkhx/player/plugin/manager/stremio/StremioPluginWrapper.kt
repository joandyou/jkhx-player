package com.jkhx.player.plugin.manager.stremio

import com.jkhx.player.core.model.*
import com.jkhx.player.core.model.stremio.StremioManifest
import com.jkhx.player.core.model.stremio.StremioMetaDetail
import com.jkhx.player.core.model.stremio.StremioMetaPreview
import com.jkhx.player.core.model.stremio.StremioStream
import com.jkhx.player.core.network.stremio.StremioBridgeClient
import com.jkhx.player.plugin.api.LoadedPlugin
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class StremioPluginWrapper(
    val manifest: StremioManifest,
    val manifestUrl: String,
    private val client: StremioBridgeClient,
    private val activeStreamAddonsProvider: (() -> List<Pair<String, StremioManifest>>)? = null
) : LoadedPlugin {

    companion object {
        const val DEFAULT_ANDROID_UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        private val HLS_META_REGEX = Regex("""\b(hls|m3u8)\b""", RegexOption.IGNORE_CASE)

        fun inferStreamType(streamUrl: String, rawType: String?, metaText: String = ""): String {
            val normType = rawType?.trim()?.lowercase()
            val lowerUrl = streamUrl.lowercase()
            val hasBase64M3u8 = streamUrl.contains("wbGF5bGlzdC5tM3U4") ||
                streamUrl.contains("cGxheWxpc3QubTN1OA") ||
                streamUrl.contains("Lm0zdTg")
            val hasMetaHls = metaText.isNotBlank() && HLS_META_REGEX.containsMatchIn(metaText)

            return when {
                normType in listOf("hls", "m3u8") ||
                    lowerUrl.contains(".m3u8") ||
                    lowerUrl.contains("/hls") ||
                    lowerUrl.contains("format=m3u8") ||
                    lowerUrl.contains("type=m3u8") ||
                    hasBase64M3u8 -> "m3u8"
                normType in listOf("dash", "mpd") || lowerUrl.contains(".mpd") -> "dash"
                normType == "flv" || lowerUrl.contains(".flv") -> "flv"
                normType == "mp4" || lowerUrl.contains(".mp4") || lowerUrl.contains(".m4v") -> "mp4"
                normType == "mkv" || lowerUrl.contains(".mkv") -> "mkv"
                normType == "avi" || lowerUrl.contains(".avi") -> "avi"
                normType == "webm" || lowerUrl.contains(".webm") -> "webm"
                hasMetaHls -> "m3u8"
                !normType.isNullOrBlank() -> normType
                else -> "direct"
            }
        }

        fun makePluginId(manifestId: String): String {
            val safe = manifestId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            return "stremio_$safe"
        }

        fun cleanCatalogTitle(rawTitle: String): String {
            val stripped = rawTitle.replace(Regex("""\[.*?\]"""), "").trim()
            return stripped.ifBlank { rawTitle }
        }
    }

    override val id: String = makePluginId(manifest.id)
    override val name: String = manifest.name
    override val version: String = manifest.version

    private fun StremioMetaPreview.toMovieItem(): MovieItem {
        val isSeries = type.equals("series", ignoreCase = true) || type.equals("tv", ignoreCase = true)
        val resolvedType = when {
            type.equals("movie", ignoreCase = true) -> "single"
            isSeries -> "series"
            else -> type
        }
        return MovieItem(
            slug = "${type}__${id}",
            name = displayName,
            originName = displayName,
            thumbUrl = displayPoster,
            posterUrl = displayPoster,
            year = displayYear,
            type = resolvedType,
            category = genres.firstOrNull(),
            providerId = this@StremioPluginWrapper.id,
            tmdbId = if (id.startsWith("tmdb:", ignoreCase = true)) id.substringAfter(":") else null
        )
    }

    override suspend fun getHome(): HomeResult = coroutineScope {
        if (manifest.catalogs.isEmpty()) {
            return@coroutineScope HomeResult()
        }

        // Chỉ lấy các catalog duyệt phim tự do (không bắt buộc tham số search hoặc genre)
        val browseableCatalogs = manifest.catalogs.filter { cat ->
            cat.extra.none { it.isRequired }
        }.take(5)

        val targetCatalogs = if (browseableCatalogs.isNotEmpty()) browseableCatalogs else manifest.catalogs.take(3)
        val deferredSections = targetCatalogs.map { catalog ->
            async {
                val metas = client.fetchCatalog(
                    manifestUrl = manifestUrl,
                    type = catalog.type,
                    id = catalog.id,
                    skip = 0
                )
                if (metas.isNotEmpty()) {
                    HomeSection(
                        title = cleanCatalogTitle(catalog.displayName),
                        items = metas.map { it.toMovieItem() },
                        type = catalog.type,
                        slug = catalog.id
                    )
                } else null
            }
        }

        val sections = deferredSections.awaitAll().filterNotNull()
        HomeResult(sections = sections)
    }

    override suspend fun getMenu(forceRefresh: Boolean): List<MenuItem>? {
        val cleanName = cleanCatalogTitle(manifest.name)
        return listOf(
            MenuItem(
                title = cleanName,
                slug = "home",
                isDropdown = false
            )
        )
    }

    override suspend fun getList(
        type: String?,
        slug: String?,
        page: Int,
        filters: Map<String, String>?
    ): SearchResult {
        val catalogType = type?.ifBlank { null } ?: manifest.catalogs.firstOrNull()?.type ?: "movie"
        val catalogId = slug?.ifBlank { null } ?: manifest.catalogs.firstOrNull { it.type == catalogType }?.id ?: "top"

        val skip = (page - 1).coerceAtLeast(0) * 20
        val genre = filters?.get("genre")

        val metas = client.fetchCatalog(
            manifestUrl = manifestUrl,
            type = catalogType,
            id = catalogId,
            skip = skip,
            genre = genre
        )

        return SearchResult(
            items = metas.map { it.toMovieItem() },
            page = page,
            hasNext = metas.size >= 15
        )
    }

    override suspend fun search(keyword: String, page: Int): SearchResult {
        if (keyword.isBlank()) return SearchResult()

        // Tìm catalog hỗ trợ search hoặc dùng catalog đầu tiên
        val searchCatalog = manifest.catalogs.firstOrNull { cat ->
            cat.extra.any { it.name.equals("search", ignoreCase = true) }
        } ?: manifest.catalogs.firstOrNull() ?: return SearchResult()

        val skip = (page - 1).coerceAtLeast(0) * 20
        val metas = client.fetchCatalog(
            manifestUrl = manifestUrl,
            type = searchCatalog.type,
            id = searchCatalog.id,
            skip = skip,
            search = keyword
        )

        return SearchResult(
            items = metas.map { it.toMovieItem() },
            page = page,
            hasNext = metas.size >= 15
        )
    }

    override suspend fun getDetail(slug: String): MovieDetail {
        val parts = slug.split("__", limit = 2)
        val type = when {
            parts.size == 2 -> parts[0]
            slug.startsWith("aiostreams::library", ignoreCase = true) -> "library"
            else -> "movie"
        }
        val id = if (parts.size == 2) parts[1] else slug

        val meta = client.fetchMeta(manifestUrl, type, id)
            ?: StremioMetaDetail(id = id, type = type, name = id)

        // Phân biệt Phim nhiều tập (Multi-video) hay Phim đơn lẻ (Single-video) theo chuẩn Stremio Protocol:
        // Lưu ý: Với type "library" (như AIOStreams PikPak Library), 1 phim lẻ cũng có meta.videos size = 1 với season = 0, episode = 0
        val hasMultiVideos = meta.videos.isNotEmpty()
        val isSeries = type.equals("series", ignoreCase = true) ||
            type.equals("tv", ignoreCase = true) ||
            meta.videos.size > 1 ||
            meta.videos.any { (it.season ?: 0) > 0 }

        val providerServerName = manifest.name
        val servers = if (hasMultiVideos) {
            val sortedVideos = meta.videos.sortedWith(
                compareBy({ it.season ?: 1 }, { it.episode ?: 0 })
            )
            val multiSeason = meta.videos.mapNotNull { it.season?.takeIf { s -> s > 0 } }.distinct().size > 1
            listOf(
                EpisodeServer(
                    serverName = providerServerName,
                    episodes = sortedVideos.map { v ->
                        val sNum = v.season?.takeIf { it > 0 } ?: if (isSeries) 1 else null
                        val epLabel = if (multiSeason && sNum != null) "M${sNum} - ${v.displayTitle}" else v.displayTitle
                        EpisodeData(
                            id = v.id,
                            name = epLabel,
                            slug = "${slug}__${v.id}",
                            number = v.episode?.takeIf { it > 0 } ?: 1,
                            season = sNum,
                            serverName = providerServerName
                        )
                    }
                )
            )
        } else if (isSeries) {
            // Fallback nếu type là series/tv nhưng meta không có video list: tạo 1 tập mặc định
            listOf(
                EpisodeServer(
                    serverName = providerServerName,
                    episodes = listOf(
                        EpisodeData(
                            id = "$id:1:1",
                            name = "Tập 1",
                            slug = "${slug}__$id:1:1",
                            number = 1,
                            season = 1,
                            serverName = providerServerName
                        )
                    )
                )
            )
        } else {
            // Phim lẻ (Single-video): 1 server với 1 video duy nhất mang id của meta
            listOf(
                EpisodeServer(
                    serverName = providerServerName,
                    episodes = listOf(
                        EpisodeData(
                            id = id,
                            name = "Phim Lẻ",
                            slug = "${slug}__$id",
                            number = 1,
                            serverName = providerServerName
                        )
                    )
                )
            )
        }

        val imdbId = Regex("""(tt\d+)""", RegexOption.IGNORE_CASE).find(id)?.groupValues?.get(1)
        val tmdbId = if (id.startsWith("tmdb:", ignoreCase = true)) id.substringAfter(":").substringBefore(":") else null

        // Bảo toàn custom mediaType (FreeReels, Anime, Tokusatsu, library, live, ...) hoặc map chuẩn sang series/single
        val resolvedDetailType = when {
            type.equals("movie", ignoreCase = true) -> "single"
            type.equals("series", ignoreCase = true) || type.equals("tv", ignoreCase = true) -> "series"
            isSeries -> "series"
            else -> type
        }

        return MovieDetail(
            slug = slug,
            name = meta.displayName,
            originName = meta.displayName,
            thumbUrl = meta.displayPoster,
            posterUrl = meta.displayPoster,
            backdropUrl = meta.background ?: meta.displayPoster,
            year = meta.displayYear,
            type = resolvedDetailType,
            status = "completed",
            quality = "HD",
            description = meta.description,
            actors = meta.cast,
            directors = meta.director,
            genres = meta.genres,
            imdbId = imdbId,
            tmdbId = tmdbId,
            episodes = servers
        )
    }

    override suspend fun getStream(episodeData: EpisodeData): EpisodeStream = coroutineScope {
        val parts = episodeData.slug.split("__")
        val rawCandidate = if (parts.isNotEmpty() && parts[0].isNotBlank()) parts[0].lowercase().trim() else ""
        val declaredTypes = manifest.types.map { it.lowercase().trim() }.toSet()
        val knownTypes = declaredTypes + setOf("movie", "series", "tv", "anime", "library", "events", "other", "channel", "special", "sport")

        val isSeriesEpisode = episodeData.season != null || episodeData.id.matches(Regex(".*:\\d+:\\d+.*"))
        val mediaType = if (knownTypes.contains(rawCandidate)) {
            rawCandidate
        } else if (declaredTypes.isNotEmpty() && !declaredTypes.contains("series") && !declaredTypes.contains("tv")) {
            declaredTypes.first()
        } else if (isSeriesEpisode) {
            "series"
        } else {
            "movie"
        }

        // Lấy videoId sạch (loại bỏ tiền tố slug/type nếu có)
        val videoId = when {
            episodeData.id.contains("__") -> episodeData.id.substringAfterLast("__")
            parts.size >= 2 && !episodeData.id.startsWith("tt") && !episodeData.id.startsWith("tmdb:") && parts.last().isNotBlank() -> parts.last()
            else -> episodeData.id
        }

        val allStreams = mutableListOf<StremioStream>()

        // 1. Nếu chính Addon này có resource "stream":
        if (manifest.hasResource("stream")) {
            val localStreams = client.fetchStreams(manifestUrl, mediaType, videoId)
            allStreams.addAll(localStreams)
            // Nếu mediaType tùy biến hoặc chưa tìm thấy, thử fallback sang series hoặc movie
            if (allStreams.isEmpty()) {
                val fallbackType = if (mediaType == "movie" && (episodeData.season != null || videoId.contains(":"))) "series" else if (mediaType != "movie") "movie" else null
                if (fallbackType != null) {
                    try {
                        val fallbackStreams = client.fetchStreams(manifestUrl, fallbackType, videoId)
                        allStreams.addAll(fallbackStreams)
                    } catch (_: Exception) {}
                }
            }
        }

        // 2. Nếu danh sách rỗng (hoặc Addon chỉ là Catalog), quét các Stremio Stream Addon khác đang bật:
        if (allStreams.isEmpty() && activeStreamAddonsProvider != null) {
            val otherAddons = activeStreamAddonsProvider.invoke().filter { it.first != manifestUrl }
            val deferredList = otherAddons.map { (url, oManifest) ->
                async {
                    try {
                        val supported = oManifest.types
                        val queryType = if (supported.contains(mediaType)) mediaType else if (episodeData.season != null || videoId.contains(":")) "series" else "movie"
                        client.fetchStreams(url, queryType, videoId)
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }
            allStreams.addAll(deferredList.awaitAll().flatten())
        }

        // Ưu tiên chọn stream trực tiếp hoặc stream đã cached trước, tránh phát trúng stream debrid hỏng hoặc uncached
        val validStream = allStreams.filter { !it.effectiveUrl.isNullOrBlank() }
            .sortedByDescending { stream ->
                val desc = "${stream.name ?: ""} ${stream.description ?: ""} ${stream.title ?: ""}".lowercase()
                val url = (stream.effectiveUrl ?: "").lowercase()
                when {
                    // Ưu tiên cao nhất: Luồng HLS direct hoặc MP4/MKV trực tiếp có đuôi rõ ràng hoặc chứa /hls
                    url.contains(".m3u8") || url.contains("/hls") || url.contains(".mp4") || url.contains(".mkv") -> 3
                    // Ưu tiên 2: Debrid stream đã cached sẵn hoặc EVA/Showbox có tia sét ⚡ / đám mây ☁️
                    desc.contains("⚡") || desc.contains("☁️") || desc.contains("cached") -> 2
                    // Tránh các stream chưa download xong hoặc stream torrent debrid thô không rõ trạng thái
                    desc.contains("⏳") || desc.contains("uncached") || desc.contains("downloading") -> 0
                    else -> 1
                }
            }
            .firstOrNull()
            ?: throw Exception("Không tìm thấy luồng phát hợp lệ từ các Addon Stremio")

        val streamUrl = validStream.effectiveUrl!!
        val headers = (validStream.behaviorHints?.allRequestHeaders ?: emptyMap()).toMutableMap()
        val hasUserAgent = headers.keys.any { it.equals("User-Agent", ignoreCase = true) }
        if (!hasUserAgent) {
            headers["User-Agent"] = DEFAULT_ANDROID_UA
        }
        val metaText = "${validStream.name.orEmpty()} ${validStream.title.orEmpty()} ${validStream.description.orEmpty()}"
        val streamType = inferStreamType(streamUrl, validStream.type, metaText)

        val subtitles = validStream.subtitles.map { sub ->
            Subtitle(
                label = sub.lang,
                language = sub.lang,
                url = sub.url,
                format = if (sub.url.contains(".vtt", ignoreCase = true)) "vtt" else "srt"
            )
        }

        EpisodeStream(
            type = streamType,
            url = streamUrl,
            headers = headers,
            subtitles = subtitles
        )
    }

    override suspend fun checkHealth(): PluginHealthStatus {
        val (ok, latency) = client.checkHealth(manifestUrl)
        return PluginHealthStatus(
            status = if (ok) "ok" else "error",
            latencyMs = latency,
            message = if (ok) "Kết nối ổn định" else "Không thể kết nối tới server"
        )
    }

    override fun close() {
        // Không giữ native context cần đóng
    }
}

