package com.jkhx.player.ui.screens.player

import android.content.Context
import com.jkhx.player.ActiveEpisodeHolder
import com.jkhx.player.ActivePlayerSession
import com.jkhx.player.JkhxApplication
import com.jkhx.player.core.database.entity.HistoryEntity
import com.jkhx.player.core.model.EpisodeData
import com.jkhx.player.core.model.EpisodeServer
import com.jkhx.player.core.model.EpisodeStream
import com.jkhx.player.core.model.MovieDetail
import com.jkhx.player.plugin.manager.PluginManager
import com.jkhx.player.ui.screens.stream.StreamAggregator
import com.jkhx.player.ui.screens.stream.UnifiedStreamItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ResumeStreamRecord(
    val metaProviderId: String,
    val streamProviderId: String,
    val movieSlug: String,
    val movieName: String,
    val originName: String? = null,
    val movieThumb: String? = null,
    val tmdbId: String? = null,
    val imdbId: String? = null,
    val movieYear: Int? = null,
    val movieType: String = "series",
    val episode: EpisodeData,
    val unifiedStream: UnifiedStreamItem? = null,
    val episodeStream: EpisodeStream,
    val updatedAt: Long = System.currentTimeMillis()
)

data class ResolvedPlayerDetailResult(
    val detail: MovieDetail,
    val metaProviderId: String,
    val resolvedMovieSlug: String,
    val targetServer: EpisodeServer?,
    val targetEpisode: EpisodeData,
    val sortedEpisodes: List<EpisodeData>,
    val targetIndex: Int
)

object ResumeStreamStore {
    private const val PREFS_NAME = "jkhx_resume_stream_store"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun save(context: Context, record: ResumeStreamRecord) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val encoded = json.encodeToString(record)
            val editor = prefs.edit()
            editor.putString("slug:${record.movieSlug}", encoded)
            editor.putString("slug_ep:${record.movieSlug}:${record.episode.id}", encoded)
            if (!record.tmdbId.isNullOrBlank()) {
                editor.putString("id:tmdb:${record.tmdbId}", encoded)
            }
            if (!record.imdbId.isNullOrBlank()) {
                editor.putString("id:imdb:${record.imdbId}", encoded)
            }
            editor.putString("id:${record.streamProviderId}:${record.movieSlug}", encoded)
            if (record.metaProviderId.isNotBlank()) {
                editor.putString("id:${record.metaProviderId}:${record.movieSlug}", encoded)
            }
            editor.apply()
        } catch (_: Exception) {}
    }

    fun saveFromActiveSession(
        context: Context,
        streamProviderId: String,
        episode: EpisodeData,
        unifiedStream: UnifiedStreamItem?,
        episodeStream: EpisodeStream,
        overrideMovieSlug: String? = null,
        overrideMovieName: String? = null,
        overrideTmdbId: String? = null
    ) {
        val slug = overrideMovieSlug?.takeIf { it.isNotBlank() } ?: ActivePlayerSession.movieSlug
        if (slug.isBlank()) return
        val metaProv = ActivePlayerSession.metaProviderId.takeIf {
            it.isNotBlank() && (!it.startsWith("stremio_") || it == "stremio_cinemeta")
        } ?: ActivePlayerSession.providerId.takeIf {
            it.isNotBlank() && (!it.startsWith("stremio_") || it == "stremio_cinemeta")
        } ?: ActivePlayerSession.metaProviderId.ifBlank { streamProviderId }

        val tmdb = overrideTmdbId?.takeIf { it.isNotBlank() }
            ?: ActivePlayerSession.tmdbId
            ?: ActivePlayerSession.movieDetail?.tmdbId
        val imdb = ActivePlayerSession.imdbId
            ?: ActivePlayerSession.movieDetail?.imdbId
            ?: slug.takeIf { it.startsWith("tt", ignoreCase = true) }

        val record = ResumeStreamRecord(
            metaProviderId = metaProv,
            streamProviderId = streamProviderId,
            movieSlug = slug,
            movieName = overrideMovieName?.takeIf { it.isNotBlank() } ?: ActivePlayerSession.movieName,
            originName = ActivePlayerSession.originName ?: ActivePlayerSession.movieDetail?.originName,
            movieThumb = ActivePlayerSession.movieThumb,
            tmdbId = tmdb,
            imdbId = imdb,
            movieYear = ActivePlayerSession.movieYear ?: ActivePlayerSession.movieDetail?.year,
            movieType = ActivePlayerSession.movieDetail?.type ?: ActivePlayerSession.movieType,
            episode = episode,
            unifiedStream = unifiedStream,
            episodeStream = episodeStream
        )
        save(context, record)
    }

    fun load(
        context: Context,
        historyId: String?,
        movieSlug: String,
        episodeId: String?
    ): ResumeStreamRecord? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val candidates = buildList {
                if (!episodeId.isNullOrBlank()) add("slug_ep:$movieSlug:$episodeId")
                if (!historyId.isNullOrBlank()) add("id:$historyId")
                add("slug:$movieSlug")
            }
            for (key in candidates) {
                val raw = prefs.getString(key, null) ?: continue
                val parsed = runCatching { json.decodeFromString<ResumeStreamRecord>(raw) }.getOrNull()
                if (parsed != null) return parsed
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun applyToSession(
        context: Context,
        history: HistoryEntity,
        fallbackEpisode: EpisodeData
    ): EpisodeData {
        val extractedTmdb = if (history.id.startsWith("tmdb:")) history.id.removePrefix("tmdb:") else null
        val extractedImdb = when {
            history.id.startsWith("imdb:") -> history.id.removePrefix("imdb:")
            history.movieSlug.startsWith("tt", ignoreCase = true) -> history.movieSlug
            else -> null
        }
        val record = load(context, history.id, history.movieSlug, history.lastEpisodeId)
        val isSameEpisode = record != null && (
            history.lastEpisodeId.isNullOrBlank() ||
            record.episode.id == history.lastEpisodeId ||
            record.episode.name == history.lastEpisodeName
        )
        val chosenEpisode = if (isSameEpisode && record != null) record.episode else fallbackEpisode
        val resolvedProvider = record?.streamProviderId?.takeIf { it.isNotBlank() } ?: history.providerId
        val metaProv = record?.metaProviderId?.takeIf { it.isNotBlank() }
            ?: history.providerId.takeIf { !it.startsWith("stremio_") || it == "stremio_cinemeta" }
            ?: ""

        ActivePlayerSession.providerId = resolvedProvider
        ActivePlayerSession.metaProviderId = metaProv
        ActivePlayerSession.movieSlug = history.movieSlug
        ActivePlayerSession.movieName = history.movieName
        ActivePlayerSession.movieThumb = history.thumbUrl ?: record?.movieThumb
        ActivePlayerSession.tmdbId = record?.tmdbId ?: extractedTmdb
        ActivePlayerSession.imdbId = record?.imdbId ?: extractedImdb
        ActivePlayerSession.originName = record?.originName ?: history.movieName
        ActivePlayerSession.movieYear = record?.movieYear
        ActivePlayerSession.movieType = record?.movieType ?: "series"
        ActivePlayerSession.availableServers = emptyList()
        ActivePlayerSession.currentServerName = record?.unifiedStream?.serverName
            ?: chosenEpisode.serverName.orEmpty()
        ActivePlayerSession.isDescending = false
        ActivePlayerSession.episodes = listOf(chosenEpisode)
        ActivePlayerSession.currentEpisodeIndex = 0
        ActivePlayerSession.currentEpisode = chosenEpisode
        ActivePlayerSession.rawPluginName = history.movieName
        ActivePlayerSession.rawPluginOriginName = record?.originName ?: history.movieName
        ActivePlayerSession.movieDetail = null
        ActivePlayerSession.aggregatedStreams = emptyList()

        if (isSameEpisode && record != null) {
            val streamToUse = record.unifiedStream?.toEpisodeStream() ?: record.episodeStream
            ActivePlayerSession.customStream = streamToUse
            ActivePlayerSession.activeUnifiedStream = record.unifiedStream
            ActiveEpisodeHolder.customStream = streamToUse
        } else {
            ActivePlayerSession.customStream = null
            ActivePlayerSession.activeUnifiedStream = null
            ActiveEpisodeHolder.customStream = null
        }
        ActiveEpisodeHolder.episode = chosenEpisode
        return chosenEpisode
    }

    fun isDummyStremioDetail(providerId: String, slug: String, detail: MovieDetail?): Boolean {
        if (detail == null) return true
        if (!providerId.startsWith("stremio_") || providerId == "stremio_cinemeta") return false
        val slugTail = slug.substringAfterLast("__")
        val isRawSlugName = detail.name.isBlank() || detail.name == slug || detail.name == slugTail
        val allEps = detail.episodes.flatMap { it.episodes }
        val hasOnlyFallbackFullEp = allEps.isEmpty() || (
            allEps.size == 1 && (allEps.first().id == slug || allEps.first().id == slugTail || allEps.first().name == "Full")
        )
        return isRawSlugName && hasOnlyFallbackFullEp && detail.tmdbId.isNullOrBlank() && detail.imdbId.isNullOrBlank()
    }

    suspend fun resolveDetailForPlayer(
        context: Context,
        pluginManager: PluginManager,
        activeProviderId: String,
        activeMovieSlug: String,
        movieName: String,
        movieThumb: String?,
        currentEpisode: EpisodeData
    ): ResolvedPlayerDetailResult? {
        val savedRecord = load(context, null, activeMovieSlug, currentEpisode.id)
        var resolvedMetaProviderId = ActivePlayerSession.metaProviderId.takeIf { it.isNotBlank() }
            ?: savedRecord?.metaProviderId?.takeIf { it.isNotBlank() }
            ?: activeProviderId
        var resolvedMovieSlug = activeMovieSlug
        var detail: MovieDetail? = null

        // 1. Nếu có metaProviderId gốc (khác với Stremio stream addon), ưu tiên lấy detail từ metaProviderId
        if (resolvedMetaProviderId.isNotBlank() && (!resolvedMetaProviderId.startsWith("stremio_") || resolvedMetaProviderId == "stremio_cinemeta")) {
            val metaPlugin = pluginManager.getPlugin(resolvedMetaProviderId)
            val d = try { metaPlugin?.getDetail(resolvedMovieSlug) } catch (_: Exception) { null }
            if (d != null && !isDummyStremioDetail(resolvedMetaProviderId, resolvedMovieSlug, d)) {
                detail = d
            }
        }

        // 2. Nếu slug dạng movie__ / tv__ của tmdb_catalog
        if (detail == null && (resolvedMovieSlug.startsWith("movie__") || resolvedMovieSlug.startsWith("tv__"))) {
            val tmdbPlugin = pluginManager.getPlugin("tmdb_catalog")
            val d = try { tmdbPlugin?.getDetail(resolvedMovieSlug) } catch (_: Exception) { null }
            if (d != null && d.episodes.isNotEmpty()) {
                detail = d
                resolvedMetaProviderId = "tmdb_catalog"
            }
        }

        // 3. Thử trên activeProviderId (nếu chưa có và không phải dummy Stremio detail)
        if (detail == null) {
            val plugin = pluginManager.getPlugin(activeProviderId)
            val d = try { plugin?.getDetail(resolvedMovieSlug) } catch (_: Exception) { null }
            if (d != null && !isDummyStremioDetail(activeProviderId, resolvedMovieSlug, d)) {
                detail = d
                resolvedMetaProviderId = activeProviderId
            }
        }

        // 4. Thử qua Cinemeta nếu có IMDb ID (tt...)
        val candidateImdb = ActivePlayerSession.imdbId
            ?: savedRecord?.imdbId
            ?: resolvedMovieSlug.takeIf { it.startsWith("tt", ignoreCase = true) }
        if (detail == null && !candidateImdb.isNullOrBlank()) {
            val cinemetaPlugin = pluginManager.getPlugin("stremio_cinemeta")
            if (cinemetaPlugin != null) {
                val cDetail = try { cinemetaPlugin.getDetail(candidateImdb) } catch (_: Exception) { null }
                if (cDetail != null && cDetail.name.isNotBlank() && !isDummyStremioDetail("stremio_cinemeta", candidateImdb, cDetail)) {
                    detail = cDetail
                    resolvedMetaProviderId = "stremio_cinemeta"
                }
            }
        }

        // 5. Quét tìm theo tên phim trên các Movie/TV plugin đang hoạt động
        if (detail == null && movieName.isNotBlank()) {
            val candidatePlugins = pluginManager.getMovieTvPlugins()
            for (cand in candidatePlugins) {
                try {
                    val sRes = cand.search(movieName, 1)
                    val match = sRes.items.firstOrNull { item ->
                        item.name.equals(movieName, ignoreCase = true) ||
                            (!item.originName.isNullOrBlank() && item.originName.equals(movieName, ignoreCase = true)) ||
                            item.slug == resolvedMovieSlug
                    } ?: sRes.items.firstOrNull { item ->
                        item.name.contains(movieName, ignoreCase = true) ||
                            movieName.contains(item.name, ignoreCase = true)
                    }
                    if (match != null) {
                        val candDetail = cand.getDetail(match.slug)
                        if (candDetail.episodes.isNotEmpty()) {
                            detail = candDetail
                            resolvedMetaProviderId = cand.id
                            if (!activeProviderId.startsWith("stremio_")) {
                                resolvedMovieSlug = match.slug
                            }
                            break
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        var validDetail = detail ?: return null
        val slugTail = resolvedMovieSlug.substringAfterLast("__")
        val isDummyName = validDetail.name.isBlank() || validDetail.name == resolvedMovieSlug || validDetail.name == slugTail
        if (movieName.isNotBlank() && isDummyName) {
            validDetail = validDetail.copy(
                name = movieName,
                originName = validDetail.originName?.takeIf { it != resolvedMovieSlug && it != slugTail } ?: movieName,
                posterUrl = validDetail.posterUrl ?: movieThumb,
                backdropUrl = validDetail.backdropUrl ?: movieThumb,
                thumbUrl = validDetail.thumbUrl ?: movieThumb
            )
        } else if (validDetail.posterUrl == null && !movieThumb.isNullOrBlank()) {
            validDetail = validDetail.copy(
                posterUrl = movieThumb,
                backdropUrl = validDetail.backdropUrl ?: movieThumb,
                thumbUrl = validDetail.thumbUrl ?: movieThumb
            )
        }

        val preservedTmdb = validDetail.tmdbId ?: ActivePlayerSession.tmdbId ?: savedRecord?.tmdbId
        val preservedImdb = validDetail.imdbId ?: ActivePlayerSession.imdbId ?: savedRecord?.imdbId ?: candidateImdb
        if (preservedTmdb != validDetail.tmdbId || preservedImdb != validDetail.imdbId) {
            validDetail = validDetail.copy(tmdbId = preservedTmdb, imdbId = preservedImdb)
        }

        if (activeProviderId.startsWith("stremio_") && validDetail.tmdbId.isNullOrBlank() && validDetail.imdbId.isNullOrBlank()) {
            try {
                validDetail = JkhxApplication.instance.tmdbRepository.enrichMovieDetail(validDetail)
            } catch (_: Exception) {}
        }

        val targetServer = validDetail.episodes.find { srv ->
            srv.episodes.any { it.id == currentEpisode.id || it.name == currentEpisode.name }
        } ?: validDetail.episodes.firstOrNull()

        val epNumber = currentEpisode.number ?: 0
        val targetEpisode = targetServer?.episodes?.find {
            it.id == currentEpisode.id || it.name == currentEpisode.name
        } ?: targetServer?.episodes?.find {
            epNumber > 0 && it.number == epNumber &&
                (currentEpisode.season == null || it.season == currentEpisode.season)
        } ?: if (activeProviderId.startsWith("stremio_")) {
            currentEpisode
        } else {
            targetServer?.episodes?.firstOrNull() ?: currentEpisode
        }

        val sortedEps = targetServer?.episodes ?: listOf(targetEpisode)
        val targetIdx = sortedEps.indexOfFirst {
            it.id == targetEpisode.id || it.name == targetEpisode.name
        }.coerceAtLeast(0)

        return ResolvedPlayerDetailResult(
            detail = validDetail,
            metaProviderId = resolvedMetaProviderId,
            resolvedMovieSlug = resolvedMovieSlug,
            targetServer = targetServer,
            targetEpisode = targetEpisode,
            sortedEpisodes = sortedEps,
            targetIndex = targetIdx
        )
    }

    suspend fun aggregateAndPickAddonStream(
        context: Context,
        pluginManager: PluginManager,
        activeProviderId: String,
        activeMovieSlug: String,
        movieName: String,
        movieOriginName: String?,
        movieThumb: String?,
        movieTmdbId: String?,
        movieYear: Int?,
        availableServers: List<EpisodeServer>,
        currentEpisode: EpisodeData,
        onStreamsUpdated: suspend (List<UnifiedStreamItem>) -> Unit
    ): UnifiedStreamItem? {
        val savedRecord = load(context, null, activeMovieSlug, currentEpisode.id)
        var detailForAgg = ActivePlayerSession.movieDetail ?: MovieDetail(
            slug = activeMovieSlug,
            name = movieName,
            originName = movieOriginName ?: savedRecord?.originName ?: movieName,
            type = ActivePlayerSession.movieType,
            posterUrl = movieThumb,
            backdropUrl = movieThumb,
            tmdbId = movieTmdbId ?: ActivePlayerSession.tmdbId ?: savedRecord?.tmdbId,
            imdbId = ActivePlayerSession.imdbId ?: savedRecord?.imdbId ?: activeMovieSlug.takeIf { it.startsWith("tt", true) },
            year = movieYear ?: savedRecord?.movieYear,
            episodes = availableServers
        )

        if (detailForAgg.tmdbId.isNullOrBlank() && detailForAgg.imdbId.isNullOrBlank()) {
            try {
                detailForAgg = withContext(Dispatchers.IO) {
                    JkhxApplication.instance.tmdbRepository.enrichMovieDetail(detailForAgg)
                }
            } catch (_: Exception) {}
        }

        val preferredHash = savedRecord?.unifiedStream?.torrentInfoHash ?: savedRecord?.episodeStream?.torrentInfoHash
        val preferredServer = savedRecord?.unifiedStream?.serverName
        var bestStream: UnifiedStreamItem? = null

        try {
            withTimeoutOrNull(12000L) {
                StreamAggregator(pluginManager).aggregateStreams(
                    providerId = activeProviderId,
                    movieDetail = detailForAgg,
                    episode = currentEpisode
                ).collect { list ->
                    if (list.isNotEmpty()) {
                        ActivePlayerSession.aggregatedStreams = list
                        onStreamsUpdated(list)
                        val exactMatch = list.firstOrNull { item ->
                            (!preferredHash.isNullOrBlank() && item.torrentInfoHash.equals(preferredHash, ignoreCase = true)) ||
                                (item.providerId == activeProviderId && !preferredServer.isNullOrBlank() && item.serverName == preferredServer)
                        }
                        val providerMatch = list.firstOrNull { it.providerId == activeProviderId }
                        bestStream = exactMatch ?: providerMatch ?: list.firstOrNull()
                        if (exactMatch != null || (preferredHash.isNullOrBlank() && providerMatch != null)) {
                            throw CancellationException("Found matching addon stream")
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return bestStream
    }

    suspend fun findSavedResumeProgress(
        playbackProgressDao: com.jkhx.player.core.database.dao.PlaybackProgressDao,
        historyDao: com.jkhx.player.core.database.dao.HistoryDao,
        activeProviderId: String,
        activeMovieSlug: String,
        movieTmdbId: String?,
        currentEpisode: EpisodeData
    ): com.jkhx.player.core.database.entity.PlaybackProgressEntity? {
        return try {
            playbackProgressDao.getProgress("$activeProviderId:$activeMovieSlug:${currentEpisode.id}")
                ?: playbackProgressDao.getAllProgressSync()
                    .filter { it.movieSlug == activeMovieSlug && it.episodeId == currentEpisode.id }
                    .maxByOrNull { it.updatedAt }
                ?: run {
                    val allHist = historyDao.getAllHistorySync()
                    val matchedHist = allHist.firstOrNull { h ->
                        (h.movieSlug == activeMovieSlug ||
                            (!movieTmdbId.isNullOrBlank() && h.id == "tmdb:$movieTmdbId") ||
                            (activeMovieSlug.startsWith("tt", ignoreCase = true) && h.id == "imdb:$activeMovieSlug")) &&
                            (h.lastEpisodeId == currentEpisode.id || h.lastEpisodeName == currentEpisode.name)
                    }
                    if (matchedHist != null && matchedHist.positionMs > 0L) {
                        com.jkhx.player.core.database.entity.PlaybackProgressEntity(
                            id = "$activeProviderId:$activeMovieSlug:${currentEpisode.id}",
                            providerId = activeProviderId,
                            movieSlug = activeMovieSlug,
                            episodeId = currentEpisode.id,
                            positionMs = matchedHist.positionMs,
                            durationMs = matchedHist.durationMs,
                            updatedAt = matchedHist.updatedAt
                        )
                    } else null
                }
        } catch (_: Exception) {
            null
        }
    }
}
