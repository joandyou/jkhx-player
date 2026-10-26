package com.jkhx.player.ui.screens.stream

import com.jkhx.player.JkhxApplication
import com.jkhx.player.core.model.EpisodeData
import com.jkhx.player.core.model.MovieDetail
import com.jkhx.player.core.model.Subtitle
import com.jkhx.player.core.player.ExoPlayerManager.Companion.DEFAULT_ANDROID_UA
import com.jkhx.player.core.model.stremio.StremioManifest
import com.jkhx.player.core.model.stremio.StremioStream
import com.jkhx.player.plugin.manager.PluginManager
import com.jkhx.player.plugin.manager.stremio.StremioPluginWrapper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class StreamAggregator(
    private val pluginManager: PluginManager = JkhxApplication.instance.pluginManager
) {
    /**
     * Quét và tổng hợp toàn bộ luồng phát khả dụng cho tập phim từ:
     * 1. Plugin Native hiện tại (duyệt qua toàn bộ máy chủ Vietsub, Thuyết minh, Lồng tiếng...).
     * 2. Toàn bộ Stremio Addon đang bật (hỗ trợ resource "stream", tự động bắt chéo ID theo slug/TMDB/IMDb/tìm kiếm).
     * 3. Các Plugin Native Movie/TV khác đã cài (khớp theo slug, TMDB ID hoặc tên phim).
     */
    fun aggregateStreams(
        providerId: String,
        movieDetail: MovieDetail,
        episode: EpisodeData
    ): Flow<List<UnifiedStreamItem>> = channelFlow {
        val collectedStreams = mutableListOf<UnifiedStreamItem>()
        val mutex = Mutex()

        // Helper gửi danh sách luồng tiệm tiến an toàn từ đa luồng (channelFlow + Mutex)
        suspend fun emitItem(item: UnifiedStreamItem) {
            val snapshot = mutex.withLock {
                val isDuplicate = collectedStreams.any {
                    it.id == item.id ||
                        (it.streamUrl.isNotBlank() && it.streamUrl.equals(item.streamUrl, ignoreCase = true)) ||
                        (it.providerId == item.providerId && it.serverName.equals(item.serverName, ignoreCase = true) && it.title.equals(item.title, ignoreCase = true))
                }
                if (!isDuplicate) {
                    collectedStreams.add(item)
                    // Sắp xếp ưu tiên: Native plugin hoặc Debrid Cloud Cached lên đầu, Uncached (chờ tải) xuống cuối
                    collectedStreams.sortedWith(
                        compareByDescending<UnifiedStreamItem> {
                            val desc = "${it.title} ${it.techInfo} ${it.serverName}".lowercase()
                            when {
                                !it.isAddon -> 2
                                desc.contains("⚡") || desc.contains("☁️") || desc.contains("cached") || desc.contains("cloud ready") -> 2
                                desc.contains("⏳") || desc.contains("cần lưu") || desc.contains("uncached") || desc.contains("downloading") -> 0
                                else -> 1
                            }
                        }
                    )
                } else null
            }
            if (snapshot != null) {
                send(snapshot)
            }
        }

        // Bóc tách số thứ tự tập chuẩn xác từ episode.number hoặc từ tên tập (vd: "Tập 05" -> 5)
        val parsedEpNum = episode.number ?: Regex("""(\d+)""").find(episode.name)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        val seasonNum = episode.season ?: 1

        // Trích xuất custom mediaType gốc (như FreeReels, Anime, Tokusatsu,... từ Stremio slug type__id)
        val rawCustomType = if (movieDetail.slug.contains("__") && !movieDetail.slug.startsWith("http")) {
            movieDetail.slug.substringBefore("__").trim()
        } else {
            movieDetail.type ?: ""
        }

        val isSeries = (episode.season != null && episode.season!! > 0) ||
            movieDetail.type.equals("series", ignoreCase = true) ||
            movieDetail.type.equals("tv", ignoreCase = true) ||
            movieDetail.type.equals("tvshows", ignoreCase = true) ||
            movieDetail.type.equals("hoathinh", ignoreCase = true) ||
            movieDetail.episodes.any { it.episodes.size > 1 } ||
            Regex(""":\d+:\d+$""").containsMatchIn(episode.id)

        val standardMediaType = when {
            movieDetail.type.equals("thethao", ignoreCase = true) -> "thethao"
            movieDetail.type.equals("live", ignoreCase = true) || movieDetail.type.equals("channel", ignoreCase = true) -> "channel"
            movieDetail.type.equals("comic", ignoreCase = true) -> "comic"
            movieDetail.type.equals("novel", ignoreCase = true) -> "novel"
            isSeries -> "series"
            else -> "movie"
        }

        // Làm sạch slug gốc của phim (bỏ tiền tố series__, movie__, jnguonc_, jkkphim_, vsmov_ và hậu tố :1:1)
        val rawMovieSlug = movieDetail.slug
            .substringAfter("__")
            .substringBefore("__")
            .substringBefore(":")
            .replace(Regex("^(jnguonc_|jkkphim_|vsmov_|kkphim_|ophim_|nguonc_)"), "")
            .trim()

        coroutineScope {
            val jobs = mutableListOf<Job>()

            // =========================================================================
            // NHÁNH 1: HOST NATIVE PLUGIN HIỆN TẠI (CHỈ ÁP DỤNG CHO PLUGIN NATIVE CÓ SERVER)
            // =========================================================================
            val currentPlugin = pluginManager.getPlugin(providerId)
            val isHostStremio = currentPlugin is StremioPluginWrapper || providerId.startsWith("stremio_")
            if (currentPlugin != null && !isHostStremio) {
                val j = launch(Dispatchers.IO) {
                    for (server in movieDetail.episodes) {
                        val matchingEp = server.episodes.find {
                            (episode.number != null && it.number == episode.number) ||
                                it.id == episode.id ||
                                it.name.trim().equals(episode.name.trim(), ignoreCase = true) ||
                                (Regex("""(\d+)""").find(it.name)?.groupValues?.getOrNull(1)?.toIntOrNull() == parsedEpNum && server.episodes.size > 1)
                        } ?: server.episodes.firstOrNull()

                        if (matchingEp != null) {
                            launch {
                                try {
                                    val stream = withTimeout(9000L) {
                                        currentPlugin.getStream(matchingEp)
                                    }
                                    if (stream.url.isNotBlank()) {
                                        val isWeb = stream.type == "embed" &&
                                            !stream.url.contains(".m3u8", true) &&
                                            !stream.url.contains(".mp4", true)

                                        val qualityBadge = UnifiedStreamItem.detectQualityBadge(
                                            movieDetail.quality,
                                            server.serverName,
                                            stream.type
                                        ) ?: "FHD"

                                        val isStremio = providerId.startsWith("stremio_")
                                        val cleanName = currentPlugin.name.replace(Regex("""\[.*?\]"""), "").trim().ifBlank { currentPlugin.name }
                                        val groupLabel = if (currentPlugin.name.startsWith("[")) {
                                            currentPlugin.name
                                        } else if (isStremio) {
                                            "[Addon] $cleanName"
                                        } else {
                                            "[JKHX] $cleanName"
                                        }

                                        val item = UnifiedStreamItem(
                                            id = "host_${currentPlugin.id}_${server.serverName}_${matchingEp.id}",
                                            providerId = currentPlugin.id,
                                            providerName = cleanName,
                                            groupName = groupLabel,
                                            serverName = server.serverName,
                                            title = "📺 [$cleanName] ${movieDetail.name} - ${matchingEp.name}",
                                            techInfo = if (isWeb) "🌐 Mở Trình Phát Web Gốc" else "⚡ HLS Direct (${stream.type.uppercase()})",
                                            qualityBadge = qualityBadge,
                                            streamType = stream.type,
                                            streamUrl = stream.url,
                                            headers = stream.headers,
                                            subtitles = stream.subtitles,
                                            isAddon = isStremio,
                                            isWebView = isWeb,
                                            targetEpisode = matchingEp
                                        )
                                        emitItem(item)
                                    }
                                } catch (_: Exception) {
                                    // Bỏ qua lỗi của 1 server cụ thể
                                }
                            }
                        }
                    }
                }
                jobs.add(j)
            }

            // =========================================================================
            // NHÁNH 2: QUÉT CÁC STREMIO STREAM ADDON ĐANG BẬT
            // =========================================================================
            val activeStremioAddons = pluginManager.getActiveStreamAddons()
            if (activeStremioAddons.isNotEmpty()) {
                val stremioClient = pluginManager.getStremioClient()
                val tmdbRepo = JkhxApplication.instance.tmdbRepository

                // Phân giải trước TMDB ID & IMDb ID 1 lần duy nhất cho toàn bộ Stremio Addons
                var preResolvedImdbId = movieDetail.imdbId
                    ?: Regex("""(tt\d+)""").find(movieDetail.slug)?.groupValues?.getOrNull(1)
                    ?: Regex("""(tt\d+)""").find(episode.id)?.groupValues?.getOrNull(1)
                    ?: Regex("""(tt\d+)""").find(episode.slug)?.groupValues?.getOrNull(1)

                var preResolvedTmdbId = movieDetail.tmdbId
                    ?: Regex("""tmdb:(\d+)""", RegexOption.IGNORE_CASE).find(movieDetail.slug)?.groupValues?.getOrNull(1)
                    ?: Regex("""tmdb:(\d+)""", RegexOption.IGNORE_CASE).find(episode.id)?.groupValues?.getOrNull(1)

                if (preResolvedTmdbId.isNullOrBlank() && preResolvedImdbId.isNullOrBlank() && movieDetail.name.isNotBlank()) {
                    val cleanQuery = movieDetail.name
                        .replace(Regex("""[._](\d{4}|\d{3,4}p|REPACK|UHD|Blu-?ray|Remux|WEB-?DL|WEBRip|HDR|DV|HEVC|x264|x265|TrueHD|Atmos).*""", RegexOption.IGNORE_CASE), "")
                        .replace('.', ' ')
                        .trim()
                        .ifBlank { movieDetail.name.trim() }
                    try {
                        preResolvedTmdbId = withTimeoutOrNull(3500L) {
                            tmdbRepo.searchTmdbId(cleanQuery, movieDetail.type)?.toString()
                        }
                    } catch (_: Exception) {}
                }

                if (preResolvedImdbId.isNullOrBlank() && !preResolvedTmdbId.isNullOrBlank()) {
                    try {
                        preResolvedImdbId = withTimeoutOrNull(3000L) {
                            tmdbRepo.getImdbId(preResolvedTmdbId!!)
                        }
                    } catch (_: Exception) {}
                }

                // Ưu tiên Addon hiện tại (nếu người dùng đang mở từ Addon Stremio) lên đầu tiên
                val hostManifestUrl = (currentPlugin as? StremioPluginWrapper)?.manifestUrl
                val sortedStremioAddons = activeStremioAddons.sortedByDescending { (addonUrl, sManifest) ->
                    addonUrl == hostManifestUrl || StremioPluginWrapper.makePluginId(sManifest.id) == providerId
                }

                for ((addonUrl, sManifest) in sortedStremioAddons) {
                    val j = launch(Dispatchers.IO) {
                        try {
                            // 0. Kiểm tra tài nguyên 'stream' và tính tương thích của MediaType theo chuẩn Stremio
                            val streamResource = sManifest.resources.find { it.name.equals("stream", ignoreCase = true) }
                                ?: return@launch

                            val supportedTypes = if (streamResource.types.isNotEmpty()) {
                                streamResource.types
                            } else {
                                sManifest.types
                            }

                            if (supportedTypes.isNotEmpty()) {
                                val isTypeSupported = supportedTypes.any { st ->
                                    val t = st.trim().lowercase()
                                    (rawCustomType.isNotBlank() && t == rawCustomType.lowercase()) ||
                                    when (standardMediaType.lowercase()) {
                                        "movie" -> t in setOf("movie", "movies", "film", "single")
                                        "series" -> t in setOf("series", "tv", "tvshows", "shows", "hoathinh", "anime")
                                        "thethao" -> t in setOf("thethao", "sports", "sport", "live", "channel")
                                        "live", "channel" -> t in setOf("live", "tv", "channel", "thethao", "sports")
                                        else -> t == standardMediaType.lowercase()
                                    }
                                }
                                if (!isTypeSupported) {
                                    // Addon không phục vụ loại nội dung hiện tại (vd: Addon thể thao không phục vụ phim)
                                    return@launch
                                }
                            }

                            // Xác định MediaType gửi tới Addon này: ưu tiên rawCustomType nếu addon này hỗ trợ, nếu không dùng standardMediaType
                            val targetMediaType = if (rawCustomType.isNotBlank() && supportedTypes.any { it.equals(rawCustomType, ignoreCase = true) }) {
                                rawCustomType
                            } else {
                                standardMediaType
                            }

                            // Tập hợp idPrefixes mà Addon chấp nhận (nếu có khai báo)
                            val acceptedPrefixes = buildSet {
                                if (streamResource.idPrefixes.isNotEmpty()) {
                                    addAll(streamResource.idPrefixes)
                                } else {
                                    addAll(sManifest.idPrefixes)
                                }
                            }

                            val candidateIds = LinkedHashSet<String>()

                            // 1. Nếu tập phim hiện tại đã có Stremio ID (vd: jnguonc_slug:1:1 hoặc tt123456:1:1)
                            if (episode.id.isNotBlank() && !episode.id.startsWith("http", ignoreCase = true)) {
                                if (isSeries && !episode.id.contains(":") && !episode.id.startsWith("aiostreams::library", true)) {
                                    candidateIds.add("${episode.id}:$seasonNum:$parsedEpNum")
                                }
                                candidateIds.add(episode.id)
                            }

                            // 2. Nếu episode.slug chứa __videoId
                            if (episode.slug.contains("__")) {
                                val tailId = episode.slug.substringAfterLast("__")
                                if (tailId.isNotBlank() && !tailId.startsWith("http", ignoreCase = true)) {
                                    if (isSeries && !tailId.contains(":") && !tailId.startsWith("aiostreams::library", true)) {
                                        candidateIds.add("$tailId:$seasonNum:$parsedEpNum")
                                    }
                                    candidateIds.add(tailId)
                                }
                            }

                            // 3. Tự động sinh Cross-ID theo các idPrefixes của Addon cho phim (chỉ áp dụng cho movie/series)
                            if ((standardMediaType == "movie" || standardMediaType == "series") &&
                                rawMovieSlug.isNotBlank() &&
                                !rawMovieSlug.startsWith("tt", true) &&
                                !rawMovieSlug.startsWith("tmdb:", true) &&
                                !rawMovieSlug.startsWith("aiostreams", true)
                            ) {
                                val allowedMovieCrossPrefixes = setOf(
                                    "jnguonc_", "jkkphim_", "vsmov_", "kkphim_", "ophim_", "nguonc_"
                                )
                                val addonPrefixes = buildSet {
                                    addAll(sManifest.idPrefixes.filter { it.endsWith("_") && (it in allowedMovieCrossPrefixes || it.startsWith("j")) })
                                    addAll(streamResource.idPrefixes.filter { it.endsWith("_") && (it in allowedMovieCrossPrefixes || it.startsWith("j")) })
                                    addAll(allowedMovieCrossPrefixes)
                                }

                                for (prefix in addonPrefixes) {
                                    val crossId = if (isSeries) {
                                        "${prefix}${rawMovieSlug}:$seasonNum:$parsedEpNum"
                                    } else {
                                        "${prefix}${rawMovieSlug}"
                                    }
                                    candidateIds.add(crossId)
                                }
                            }

                            // 4. Nếu có TMDB ID (từ movieDetail hoặc phân giải trước)
                            val effectiveTmdbId = movieDetail.tmdbId ?: preResolvedTmdbId
                            if (!effectiveTmdbId.isNullOrBlank()) {
                                val cleanTmdb = effectiveTmdbId.removePrefix("tmdb:")
                                val tmdbVid = if (isSeries) "tmdb:$cleanTmdb:$seasonNum:$parsedEpNum" else "tmdb:$cleanTmdb"
                                candidateIds.add(tmdbVid)
                            }

                            // 5. Nếu có IMDb ID (từ movieDetail hoặc phân giải trước)
                            val effectiveImdbId = movieDetail.imdbId ?: preResolvedImdbId
                            if (!effectiveImdbId.isNullOrBlank()) {
                                val imdbVid = if (isSeries) "$effectiveImdbId:$seasonNum:$parsedEpNum" else effectiveImdbId
                                candidateIds.add(imdbVid)
                            }

                            var foundAnyStream = false

                            fun isInvalidStream(str: StremioStream): Boolean {
                                val meta = "${str.name.orEmpty()} ${str.title.orEmpty()} ${str.description.orEmpty()}".lowercase()
                                val errKeywords = listOf(
                                    "unavailable", "no stream", "no streams", "not available", "chưa có luồng",
                                    "please configure", "configure addon", "invalid api", "requires configuration",
                                    "failed to fetch", "error", "unsupported", "quota exceeded", "rate limit"
                                )
                                if (errKeywords.any { meta.contains(it) }) return true
                                val effUrl = str.effectiveUrl?.lowercase().orEmpty()
                                if (effUrl.isBlank() || effUrl.contains("/error") || effUrl.contains("/unavailable")) return true
                                return false
                            }

                            suspend fun processStremioStreams(vId: String, streams: List<StremioStream>): Boolean {
                                val validStreams = streams.filter { !it.effectiveUrl.isNullOrBlank() && !isInvalidStream(it) }
                                if (validStreams.isEmpty()) return false

                                for ((idx, str) in validStreams.withIndex()) {
                                    val item = mapStremioStreamToUnifiedItem(
                                        addonUrl = addonUrl,
                                        sManifest = sManifest,
                                        vId = vId,
                                        idx = idx,
                                        str = str,
                                        movieDetail = movieDetail,
                                        episode = episode
                                    )
                                    emitItem(item)
                                }
                                return true
                            }

                            // Lọc các candidate videoId phù hợp với idPrefixes của Addon (nếu addon có khai báo)
                            val validCandidateIds = if (acceptedPrefixes.isNotEmpty()) {
                                candidateIds.filter { vid ->
                                    acceptedPrefixes.any { prefix -> vid.startsWith(prefix, ignoreCase = true) }
                                }
                            } else {
                                candidateIds.toList()
                            }

                            // Thử lần lượt các candidate videoId hợp lệ
                            for (vId in validCandidateIds) {
                                val effectiveTypeForVid = when {
                                    vId.startsWith("aiostreams::library", ignoreCase = true) -> "library"
                                    targetMediaType.equals("library", ignoreCase = true) &&
                                        (vId.startsWith("tt", ignoreCase = true) || vId.startsWith("tmdb:", ignoreCase = true)) -> standardMediaType
                                    else -> targetMediaType
                                }

                                var streams = try {
                                    withTimeout(28000L) {
                                        stremioClient.fetchStreams(addonUrl, effectiveTypeForVid, vId)
                                    }
                                } catch (_: Exception) {
                                    emptyList()
                                }
                                // Nếu effectiveTypeForVid là customType và không có stream, thử fallback sang standardMediaType
                                if (streams.isEmpty() && effectiveTypeForVid != standardMediaType) {
                                    streams = try {
                                        withTimeout(15000L) {
                                            stremioClient.fetchStreams(addonUrl, standardMediaType, vId)
                                        }
                                    } catch (_: Exception) { emptyList() }
                                }

                                if (processStremioStreams(vId, streams)) {
                                    foundAnyStream = true
                                    break
                                }
                            }

                            // 6. Fallback thông minh: Nếu chưa khớp theo ID, tìm theo tên phim trên Catalog của Addon
                            if (!foundAnyStream && movieDetail.name.isNotBlank() && sManifest.catalogs.isNotEmpty()) {
                                val searchCat = sManifest.catalogs.firstOrNull { cat ->
                                    cat.extra.any { it.name.equals("search", ignoreCase = true) } &&
                                        (supportedTypes.isEmpty() || supportedTypes.any { st -> st.equals(cat.type, ignoreCase = true) })
                                } ?: sManifest.catalogs.firstOrNull { cat ->
                                    supportedTypes.isEmpty() || supportedTypes.any { st -> st.equals(cat.type, ignoreCase = true) }
                                }

                                if (searchCat != null) {
                                    val queries = listOfNotNull(
                                        movieDetail.name.trim(),
                                        movieDetail.originName?.trim()
                                    ).filter { it.isNotBlank() }.distinct()

                                    for (q in queries) {
                                        val metas = try {
                                            withTimeout(5500L) {
                                                stremioClient.fetchCatalog(
                                                    manifestUrl = addonUrl,
                                                    type = searchCat.type,
                                                    id = searchCat.id,
                                                    search = q
                                                )
                                            }
                                        } catch (_: Exception) {
                                            emptyList()
                                        }

                                        fun cleanStr(s: String) = s.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
                                        val qClean = cleanStr(q)
                                        val matchedMeta = metas.firstOrNull { m ->
                                            val mClean = cleanStr(m.displayName)
                                            mClean == qClean || (mClean.length >= 4 && qClean.length >= 4 && (mClean.startsWith(qClean) || qClean.startsWith(mClean)))
                                        }

                                        if (matchedMeta != null) {
                                            val resolvedVid = if (isSeries && !matchedMeta.id.contains(":")) {
                                                "${matchedMeta.id}:$seasonNum:$parsedEpNum"
                                            } else {
                                                matchedMeta.id
                                            }
                                            if (acceptedPrefixes.isEmpty() || acceptedPrefixes.any { resolvedVid.startsWith(it, ignoreCase = true) }) {
                                                var fallbackStreams = try {
                                                    withTimeout(28000L) {
                                                        stremioClient.fetchStreams(addonUrl, targetMediaType, resolvedVid)
                                                    }
                                                } catch (_: Exception) {
                                                    emptyList()
                                                }
                                                if (fallbackStreams.isEmpty() && targetMediaType != standardMediaType) {
                                                    fallbackStreams = try {
                                                        withTimeout(15000L) {
                                                            stremioClient.fetchStreams(addonUrl, standardMediaType, resolvedVid)
                                                        }
                                                    } catch (_: Exception) { emptyList() }
                                                }
                                                if (processStremioStreams(resolvedVid, fallbackStreams)) {
                                                    break
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            // Bỏ qua lỗi của từng addon riêng lẻ
                        }
                    }
                    jobs.add(j)
                }
            }

            // =========================================================================
            // NHÁNH 3: CÁC NATIVE PLUGINS KHÁC ĐÃ CÀI (MULTI-SOURCE SCANNING)
            // =========================================================================
            val otherPlugins = pluginManager.getMovieTvPlugins().filter {
                it.id != providerId && !it.id.startsWith("stremio_")
            }
            if (otherPlugins.isNotEmpty() && movieDetail.name.isNotBlank()) {
                for (otherPlugin in otherPlugins) {
                    val j = launch(Dispatchers.IO) {
                        try {
                            var otherDetail: MovieDetail? = null

                            // 1. Thử lấy trực tiếp theo rawMovieSlug trước (vì KKPhim / OPhim / NguonC thường dùng chung slug chuẩn)
                            if (rawMovieSlug.isNotBlank() && !rawMovieSlug.startsWith("tt", true)) {
                                otherDetail = try {
                                    withTimeout(5000L) { otherPlugin.getDetail(rawMovieSlug) }
                                } catch (_: Exception) { null }
                            }

                            fun cleanTitle(s: String) = s.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()

                            // 2. Kiểm tra xem detail lấy theo slug có trùng tên phim không; nếu không thì tìm kiếm theo tên
                            val isSlugValid = otherDetail != null &&
                                otherDetail.episodes.isNotEmpty() &&
                                (cleanTitle(otherDetail.name) == cleanTitle(movieDetail.name) ||
                                    (!movieDetail.originName.isNullOrBlank() && cleanTitle(otherDetail.name) == cleanTitle(movieDetail.originName!!)) ||
                                    (!otherDetail.originName.isNullOrBlank() && cleanTitle(otherDetail.originName!!) == cleanTitle(movieDetail.name)) ||
                                    (!movieDetail.originName.isNullOrBlank() && !otherDetail.originName.isNullOrBlank() && cleanTitle(otherDetail.originName!!) == cleanTitle(movieDetail.originName!!)))

                            if (!isSlugValid) {
                                otherDetail = null
                                val searchCandidates = listOfNotNull(
                                    movieDetail.name.trim(),
                                    movieDetail.originName?.trim()
                                ).filter { it.isNotBlank() }.distinct()

                                var matchedSlug: String? = null
                                for (q in searchCandidates) {
                                    val sRes = try {
                                        withTimeout(5500L) { otherPlugin.search(q, 1) }
                                    } catch (_: Exception) { null } ?: continue

                                    val items = sRes.items
                                    if (items.isEmpty()) continue

                                    if (!movieDetail.tmdbId.isNullOrBlank()) {
                                        val byTmdb = items.firstOrNull { it.tmdbId?.equals(movieDetail.tmdbId, ignoreCase = true) == true }
                                        if (byTmdb != null) {
                                            matchedSlug = byTmdb.slug
                                            break
                                        }
                                    }

                                    val byName = items.firstOrNull { c ->
                                        val cClean = cleanTitle(c.name)
                                        val cOriginClean = c.originName?.let { cleanTitle(it) }
                                        val qClean = cleanTitle(q)
                                        val rootC = cleanTitle(c.name.substringBefore(":").substringBefore("-"))
                                        val rootQ = cleanTitle(q.substringBefore(":").substringBefore("-"))
                                        val isExact = cClean == qClean || (cOriginClean != null && cOriginClean == qClean)
                                        val isRoot = rootC.length >= 4 && rootC == rootQ
                                        isExact || isRoot
                                    }
                                    if (byName != null) {
                                        matchedSlug = byName.slug
                                        break
                                    }
                                }

                                if (matchedSlug != null) {
                                    otherDetail = try {
                                        withTimeout(6000L) { otherPlugin.getDetail(matchedSlug) }
                                    } catch (_: Exception) { null }
                                }
                            }

                            if (otherDetail != null && otherDetail.episodes.isNotEmpty()) {
                                val cleanName = otherPlugin.name.replace(Regex("""\[.*?\]"""), "").trim().ifBlank { otherPlugin.name }
                                val groupLabel = if (otherPlugin.name.startsWith("[")) otherPlugin.name else "[JKHX] $cleanName"

                                for (srv in otherDetail.episodes) {
                                    val otherEp = srv.episodes.find {
                                        (episode.number != null && it.number == episode.number) ||
                                            it.name.trim().equals(episode.name.trim(), ignoreCase = true) ||
                                            (Regex("""(\d+)""").find(it.name)?.groupValues?.getOrNull(1)?.toIntOrNull() == parsedEpNum && srv.episodes.size > 1)
                                    } ?: srv.episodes.firstOrNull()

                                    if (otherEp != null) {
                                        launch {
                                            try {
                                                val st = withTimeout(6500L) { otherPlugin.getStream(otherEp) }
                                                if (st.url.isNotBlank()) {
                                                    val isWeb = st.type == "embed" && !st.url.contains(".m3u8", true)
                                                    val item = UnifiedStreamItem(
                                                        id = "other_${otherPlugin.id}_${srv.serverName}_${otherEp.id}",
                                                        providerId = otherPlugin.id,
                                                        providerName = cleanName,
                                                        groupName = groupLabel,
                                                        serverName = srv.serverName,
                                                        title = "📺 [$cleanName] ${otherDetail.name} - ${otherEp.name}",
                                                        techInfo = if (isWeb) "🌐 Mở Trình Phát Web Gốc" else "⚡ HLS Direct (${st.type.uppercase()})",
                                                        qualityBadge = UnifiedStreamItem.detectQualityBadge(otherDetail.quality, srv.serverName, st.type) ?: "FHD",
                                                        streamType = st.type,
                                                        streamUrl = st.url,
                                                        headers = st.headers,
                                                        subtitles = st.subtitles,
                                                        isAddon = false,
                                                        isWebView = isWeb,
                                                        targetEpisode = otherEp
                                                    )
                                                    emitItem(item)
                                                }
                                            } catch (_: Exception) {}
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    jobs.add(j)
                }
            }

            // Chờ tất cả các nhánh quét hoàn tất
            jobs.joinAll()
        }
    }.flowOn(Dispatchers.IO)

    private fun mapStremioStreamToUnifiedItem(
        addonUrl: String,
        sManifest: StremioManifest,
        vId: String,
        idx: Int,
        str: StremioStream,
        movieDetail: MovieDetail,
        episode: EpisodeData
    ): UnifiedStreamItem {
        val strUrl = str.effectiveUrl.orEmpty()
        val isWeb = (str.url == null && str.externalUrl != null) ||
            str.type == "embed" || str.type == "web"
        val metaText = "${str.name.orEmpty()} ${str.title.orEmpty()} ${str.description.orEmpty()}"
        val sType = if (isWeb) {
            "embed"
        } else {
            StremioPluginWrapper.inferStreamType(strUrl, str.type, metaText)
        }

        val defaultTechInfo = when (sType) {
            "m3u8" -> "⚡ HLS Direct"
            "flv" -> "⚡ FLV Live Stream"
            "dash" -> "⚡ DASH Stream"
            "mkv" -> "⚡ Direct MKV"
            "avi" -> "⚡ Direct AVI"
            "mp4" -> "⚡ Direct MP4"
            "webm" -> "⚡ Direct WEBM"
            "embed" -> "🌐 Mở Trình Phát Web Gốc"
            else -> "⚡ Direct Stream"
        }

        // Bóc tách str.name (VD: "NguonC\nVietsub" hoặc "VSMOV\nVietsub #1")
        val nameLines = (str.name ?: "")
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val cleanManifestName = StremioPluginWrapper.cleanCatalogTitle(sManifest.name)
        val parsedProviderName = nameLines.firstOrNull() ?: cleanManifestName
        val serverFromName = if (nameLines.size >= 2) nameLines.drop(1).joinToString(" - ") else null

        // Bóc tách str.title / str.description (VD: "📺 [JKHX-NGUONC] Ngọc Đệm Thu - Tập 1\n⚡ HLS Direct (Tiết kiệm băng thông)")
        val rawTitleText = str.title ?: str.description ?: ""
        val titleLines = rawTitleText
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val (serverName, displayTitle, techInfo) = when {
            serverFromName != null -> {
                val dTitle = titleLines.getOrNull(0) ?: "📺 [${sManifest.name}] ${movieDetail.name} - ${episode.name}"
                val tInfo = titleLines.getOrNull(1) ?: defaultTechInfo
                Triple(serverFromName, dTitle, tInfo)
            }
            titleLines.size >= 3 -> {
                Triple(titleLines[0], titleLines[1], titleLines[2])
            }
            titleLines.size == 2 -> {
                val firstLine = titleLines[0]
                if (firstLine.startsWith("📺")) {
                    Triple(parsedProviderName, firstLine, titleLines[1])
                } else {
                    Triple(firstLine, "📺 [${sManifest.name}] ${movieDetail.name} - ${episode.name}", titleLines[1])
                }
            }
            titleLines.size == 1 -> {
                val singleLine = titleLines[0]
                val sName = if (singleLine.isNotBlank() && singleLine.length <= 30 && !singleLine.startsWith("📺")) {
                    "$parsedProviderName ($singleLine)"
                } else {
                    parsedProviderName
                }
                Triple(
                    sName,
                    singleLine,
                    defaultTechInfo
                )
            }
            else -> {
                Triple(
                    parsedProviderName,
                    "📺 [${sManifest.name}] ${movieDetail.name} - ${episode.name}",
                    defaultTechInfo
                )
            }
        }

        val qualityBadge = UnifiedStreamItem.detectQualityBadge(
            str.quality,
            str.description,
            str.title,
            str.name,
            techInfo
        ) ?: if (isWeb) "WEB" else "FHD"

        val enhancedTechInfo = if (isWeb) "🌐 Mở Trình Phát Web Gốc" else techInfo

        val groupName = sManifest.name.let {
            if (it.startsWith("[")) it else "📺 [Addon] $it"
        }

        val streamHeaders = (str.behaviorHints?.allRequestHeaders ?: emptyMap()).toMutableMap()
        val hasUserAgent = streamHeaders.keys.any { it.equals("User-Agent", ignoreCase = true) }
        if (!hasUserAgent) {
            streamHeaders["User-Agent"] = DEFAULT_ANDROID_UA
        }

        return UnifiedStreamItem(
            id = "stremio_${sManifest.id}_${vId}_$idx",
            providerId = StremioPluginWrapper.makePluginId(sManifest.id),
            providerName = parsedProviderName,
            groupName = groupName,
            serverName = serverName,
            title = displayTitle,
            techInfo = enhancedTechInfo,
            qualityBadge = qualityBadge,
            streamType = sType,
            streamUrl = strUrl,
            headers = streamHeaders,
            subtitles = str.subtitles.map { sub ->
                Subtitle(
                    label = sub.lang,
                    language = sub.lang,
                    url = sub.url,
                    format = if (sub.url.contains(".vtt", true)) "vtt" else "srt"
                )
            },
            isAddon = true,
            isWebView = isWeb,
            targetEpisode = episode
        )
    }
}
