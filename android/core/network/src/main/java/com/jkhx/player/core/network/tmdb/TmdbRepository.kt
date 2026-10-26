package com.jkhx.player.core.network.tmdb

import com.jkhx.player.core.model.CastMember
import com.jkhx.player.core.model.MovieDetail
import com.jkhx.player.core.model.MovieItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import com.jkhx.player.core.network.HttpBridgeClient
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit

class TmdbRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .proxySelector(object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> {
                val proxy = HttpBridgeClient.globalProxyProvider?.invoke()
                return if (proxy != null) listOf(proxy) else listOf(Proxy.NO_PROXY)
            }
            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: java.io.IOException?) {}
        })
        .build()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Lấy chi tiết thông tin phim từ TMDB qua CF Worker Proxy
     * Tự động điều hướng giữa /movie/{id} và /tv/{id}, có cơ chế thử lại nếu sai loại
     */
    suspend fun getTmdbDetail(
        tmdbId: String,
        type: String? = null,
        language: String = TmdbConfig.getAppLanguage()
    ): TmdbDetailResponse? = withContext(Dispatchers.IO) {
        val cleanId = tmdbId.trim()
        if (cleanId.isBlank()) return@withContext null

        val isTv = isTvType(type)
        val primaryMediaType = if (isTv) "tv" else "movie"
        val secondaryMediaType = if (isTv) "movie" else "tv"

        val cacheKey = "${primaryMediaType}_${cleanId}_${language}"
        TmdbCache.get(cacheKey)?.let { return@withContext it }

        // Gọi loại ưu tiên trước
        var response = fetchEndpoint(primaryMediaType, cleanId, language)
        if (response == null) {
            // Nếu không tìm thấy, thử fallback sang loại còn lại
            response = fetchEndpoint(secondaryMediaType, cleanId, language)
        }

        if (response != null) {
            TmdbCache.put(cacheKey, response)
        }
        response
    }

    private fun isTvType(type: String?): Boolean {
        val t = type?.lowercase() ?: ""
        return t == "series" || t == "tv" || t == "tvshows" || t == "hoathinh"
    }

    private fun fetchEndpoint(mediaType: String, tmdbId: String, language: String): TmdbDetailResponse? {
        val endpoint = "$mediaType/$tmdbId"
        val params = mapOf(
            "language" to language,
            "append_to_response" to "credits,videos,external_ids"
        )
        val bodyStr = fetchTmdbApi(endpoint, params) ?: return null
        return try {
            json.decodeFromString<TmdbDetailResponse>(bodyStr)
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchExternalImdbId(mediaType: String, tmdbId: String): String? {
        val endpoint = "$mediaType/$tmdbId/external_ids"
        val bodyStr = fetchTmdbApi(endpoint, emptyMap()) ?: return null
        return try {
            val root = json.parseToJsonElement(bodyStr).jsonObject
            root["imdb_id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Tra cứu IMDb ID từ TMDB ID (dùng cho Stremio Addon stream matching và subtitle resolution)
     * Tối ưu hóa: kiểm tra cache, gọi trực tiếp endpoint /external_ids siêu nhẹ (<100ms),
     * tự động fallback giữa movie/tv và gọi full detail nếu cần.
     */
    suspend fun getImdbId(tmdbId: String, type: String? = null): String? = withContext(Dispatchers.IO) {
        val cleanId = tmdbId.trim()
        if (cleanId.isBlank()) return@withContext null

        val isTv = isTvType(type)
        val primaryMediaType = if (isTv) "tv" else "movie"
        val secondaryMediaType = if (isTv) "movie" else "tv"

        // 1. Kiểm tra cache từ getTmdbDetail nếu đã có sẵn
        val cached = TmdbCache.get("${primaryMediaType}_${cleanId}_${TmdbConfig.getAppLanguage()}")
            ?: TmdbCache.get("${secondaryMediaType}_${cleanId}_${TmdbConfig.getAppLanguage()}")
        if (!cached?.effectiveImdbId.isNullOrBlank()) {
            return@withContext cached?.effectiveImdbId
        }

        // 2. Tra cứu trực tiếp endpoint /external_ids với loại ưu tiên
        var imdb = fetchExternalImdbId(primaryMediaType, cleanId)
        if (imdb.isNullOrBlank()) {
            // Thử fallback loại còn lại
            imdb = fetchExternalImdbId(secondaryMediaType, cleanId)
        }
        if (!imdb.isNullOrBlank()) {
            return@withContext imdb
        }

        // 3. Fallback sang toàn bộ chi tiết nếu external_ids không trả về kết quả
        val detail = getTmdbDetail(cleanId, type)
        detail?.effectiveImdbId
    }

    /**
     * Tra cứu ảnh đại diện (avatar) của diễn viên theo tên từ TMDB
     */
    suspend fun getActorProfile(actorName: String): Pair<Long?, String?> = withContext(Dispatchers.IO) {
        val clean = actorName.trim()
        if (clean.isBlank()) return@withContext null to null
        val params = mapOf(
            "query" to clean,
            "language" to TmdbConfig.getAppLanguage()
        )
        val searchBody = fetchTmdbApi("search/person", params) ?: return@withContext null to null
        try {
            val root = json.parseToJsonElement(searchBody).jsonObject
            val results = root["results"]?.jsonArray ?: return@withContext null to null
            val first = results.firstOrNull()?.jsonObject ?: return@withContext null to null
            val personId = first["id"]?.jsonPrimitive?.longOrNull
            val profilePath = first["profile_path"]?.jsonPrimitive?.contentOrNull
            val profileUrl = TmdbConfig.proxiedImageUrl(profilePath, "w185")
            personId to profileUrl
        } catch (_: Exception) {
            null to null
        }
    }

    /**
     * Hợp nhất dữ liệu phim: Ưu tiên metadata từ TMDB (backdrop/poster nét, điểm số,
     * dàn diễn viên có avatar, tagline, mô tả chuẩn), đồng thời giữ nguyên 100% luồng phát
     * và danh sách tập phim từ Plugin nguồn.
     */
    suspend fun enrichMovieDetail(original: MovieDetail): MovieDetail = withContext(Dispatchers.Default) {
        var resolvedTmdbId = original.tmdbId?.trim()
        if (resolvedTmdbId.isNullOrBlank()) {
            val query = original.originName?.ifBlank { null } ?: original.name
            val searchedId = searchTmdbId(query, original.type)
            if (searchedId != null) {
                resolvedTmdbId = searchedId.toString()
            }
        }
        if (resolvedTmdbId.isNullOrBlank()) return@withContext original

        val tmdb = getTmdbDetail(resolvedTmdbId, original.type) ?: return@withContext original

        val tmdbTitle = tmdb.title ?: tmdb.name
        val tmdbOriginTitle = tmdb.originalTitle ?: tmdb.originalName
        val tmdbPoster = TmdbConfig.proxiedImageUrl(tmdb.posterPath, "w500")
        val tmdbBackdrop = TmdbConfig.proxiedImageUrl(tmdb.backdropPath, "original")

        // Trích xuất năm phát hành từ release_date hoặc first_air_date
        val dateStr = tmdb.releaseDate ?: tmdb.firstAirDate
        val tmdbYear = dateStr?.take(4)?.toIntOrNull()

        // Tóm tắt nội dung: nếu TMDB có mô tả tiếng Việt/tiếng chỉ định thì ưu tiên;
        // nếu TMDB trả về rỗng (chưa có người dịch), fallback giữ nguyên mô tả từ plugin nguồn.
        val finalDescription = if (!tmdb.overview.isNullOrBlank()) {
            tmdb.overview.trim()
        } else {
            original.description
        }

        // Danh sách diễn viên kèm avatar chất lượng cao từ TMDB
        val castList = tmdb.credits?.cast?.map { c ->
            CastMember(
                id = c.id,
                name = c.name,
                character = c.character,
                profileUrl = TmdbConfig.proxiedImageUrl(c.profilePath, "w185")
            )
        } ?: emptyList()

        val actorNames = if (castList.isNotEmpty()) {
            castList.map { it.name }
        } else {
            original.actors
        }

        val tmdbGenres = if (tmdb.genres.isNotEmpty()) {
            tmdb.genres.map { it.name }
        } else {
            original.genres
        }

        val tmdbDirectors = tmdb.credits?.crew?.filter {
            it.job.equals("Director", ignoreCase = true)
        }?.map { it.name }?.filter { it.isNotBlank() } ?: emptyList()
        val directorsList = if (tmdbDirectors.isNotEmpty()) {
            tmdbDirectors
        } else {
            original.directors
        }

        val tmdbCountries = tmdb.productionCountries.map { it.name }.filter { it.isNotBlank() }
        val countriesList = if (tmdbCountries.isNotEmpty()) {
            tmdbCountries
        } else {
            original.countries
        }

        val trailerKey = tmdb.videos?.results?.firstOrNull {
            it.site.equals("YouTube", ignoreCase = true) &&
                (it.type.equals("Trailer", ignoreCase = true) || it.type.equals("Teaser", ignoreCase = true))
        }?.key ?: tmdb.videos?.results?.firstOrNull { it.site.equals("YouTube", ignoreCase = true) }?.key

        val finalName = if (!original.name.isNullOrBlank()) {
            original.name
        } else if (!tmdbTitle.isNullOrBlank()) {
            tmdbTitle
        } else {
            original.name
        }

        val finalOriginName = if (!original.originName.isNullOrBlank()) {
            original.originName
        } else if (!tmdbOriginTitle.isNullOrBlank()) {
            tmdbOriginTitle
        } else {
            original.originName
        }

        original.copy(
            name = finalName,
            originName = finalOriginName,
            posterUrl = tmdbPoster ?: original.posterUrl,
            backdropUrl = tmdbBackdrop ?: original.backdropUrl ?: original.posterUrl,
            thumbUrl = tmdbBackdrop ?: original.thumbUrl,
            year = tmdbYear ?: original.year,
            description = finalDescription,
            rating = tmdb.voteAverage ?: original.rating,
            voteCount = tmdb.voteCount ?: original.voteCount,
            tagline = tmdb.tagline?.ifBlank { null } ?: original.tagline,
            actors = actorNames,
            castMembers = castList,
            directors = directorsList,
            countries = countriesList,
            genres = tmdbGenres,
            tmdbId = resolvedTmdbId,
            imdbId = tmdb.effectiveImdbId ?: original.imdbId,
            trailerUrl = trailerKey?.let { "https://www.youtube.com/watch?v=$it" } ?: original.trailerUrl
        )
    }

    /**
     * Bổ sung thông tin cho MovieItem (dùng cho HeroBanner và MovieCard)
     */
    suspend fun enrichMovieItem(item: MovieItem): MovieItem = withContext(Dispatchers.Default) {
        var resolvedTmdbId = item.tmdbId?.trim()
        if (resolvedTmdbId.isNullOrBlank()) {
            val query = item.originName?.ifBlank { null } ?: item.name
            val searchedId = searchTmdbId(query, item.type)
            if (searchedId != null) {
                resolvedTmdbId = searchedId.toString()
            }
        }
        if (resolvedTmdbId.isNullOrBlank()) return@withContext item

        val tmdb = getTmdbDetail(resolvedTmdbId, item.type) ?: return@withContext item
        val tmdbPoster = TmdbConfig.proxiedImageUrl(tmdb.posterPath, "w500")
        val tmdbBackdrop = TmdbConfig.proxiedImageUrl(tmdb.backdropPath, "original")
        val tmdbTitle = tmdb.title ?: tmdb.name
        val tmdbOriginTitle = tmdb.originalTitle ?: tmdb.originalName
        val dateStr = tmdb.releaseDate ?: tmdb.firstAirDate
        val tmdbYear = dateStr?.take(4)?.toIntOrNull()
        val tmdbCountry = tmdb.productionCountries.firstOrNull()?.name

        val finalName = if (item.name.isNotBlank()) item.name else if (!tmdbTitle.isNullOrBlank()) tmdbTitle else item.name
        val finalOrigin = if (!item.originName.isNullOrBlank()) item.originName else tmdbOriginTitle

        item.copy(
            name = finalName,
            originName = finalOrigin,
            posterUrl = tmdbPoster ?: item.posterUrl,
            thumbUrl = tmdbBackdrop ?: item.thumbUrl,
            year = tmdbYear ?: item.year,
            rating = tmdb.voteAverage ?: item.rating,
            tmdbId = resolvedTmdbId,
            country = tmdbCountry ?: item.country
        )
    }

    /**
     * Tìm kiếm TMDB ID nhanh từ tên phim / tên gốc
     */
    suspend fun searchTmdbId(query: String, type: String? = null): Long? = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext null
        val isTv = isTvType(type)
        val endpoint = if (isTv) "search/tv" else "search/movie"
        val params = mapOf(
            "query" to clean,
            "language" to TmdbConfig.getAppLanguage()
        )
        val bodyStr = fetchTmdbApi(endpoint, params) ?: return@withContext null
        try {
            val root = json.parseToJsonElement(bodyStr).jsonObject
            val results = root["results"]?.jsonArray ?: return@withContext null
            val first = results.firstOrNull()?.jsonObject ?: return@withContext null
            first["id"]?.jsonPrimitive?.longOrNull
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Kiểm tra tính hợp lệ của API Key TMDB thông qua CF Worker Proxy
     */
    suspend fun testApiKey(apiKey: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isBlank()) return@withContext false to "API Key không được để trống"
        val target = "https://api.themoviedb.org/3/configuration?api_key=$key"
        val proxyUrl = "${TmdbConfig.CF_PROXY_BASE}${java.net.URLEncoder.encode(target, "UTF-8")}"
        val start = System.currentTimeMillis()
        try {
            val req = Request.Builder()
                .url(proxyUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) JkhxPlayer/1.0")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val latency = System.currentTimeMillis() - start
                val body = resp.body?.string() ?: ""
                if (resp.isSuccessful && !body.contains("status_code")) {
                    true to "Kết nối thành công (${latency}ms)"
                } else {
                    val msg = if (body.contains("Invalid API key") || resp.code == 401) {
                        "API Key không hợp lệ (TMDB từ chối)"
                    } else {
                        "Lỗi HTTP ${resp.code} (${latency}ms)"
                    }
                    false to msg
                }
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            false to (e.message?.take(40) ?: "Không thể kết nối tới proxy (${latency}ms)")
        }
    }

    private fun fetchHttpString(url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) JkhxPlayer/1.0")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Gọi API TMDB với Dual Fallback: thử Cloudflare Worker Proxy trước,
     * nếu thất bại tự động fallback gọi trực tiếp TMDB API.
     */
    fun fetchTmdbApi(endpoint: String, params: Map<String, String>): String? {
        val activeKey = TmdbConfig.apiKey.trim().ifBlank { TmdbConfig.DEFAULT_API_KEY }
        if (activeKey.isBlank()) return null
        val proxyUrl = TmdbConfig.proxiedApiUrl(endpoint, params)
        var bodyStr = fetchHttpString(proxyUrl)
        if (bodyStr.isNullOrBlank()) {
            val directQuery = StringBuilder("api_key=$activeKey")
            params.forEach { (k, v) ->
                directQuery.append("&").append(k).append("=").append(java.net.URLEncoder.encode(v, "UTF-8"))
            }
            val directUrl = "${TmdbConfig.TMDB_API_BASE}/$endpoint?$directQuery"
            bodyStr = fetchHttpString(directUrl)
        }
        return bodyStr
    }

    private fun parseMoviesFromJson(bodyStr: String, defaultType: String? = null): List<MovieItem> {
        return try {
            val root = json.parseToJsonElement(bodyStr).jsonObject
            val results = root["results"]?.jsonArray ?: root["cast"]?.jsonArray ?: return emptyList()
            results.mapNotNull { element ->
                try {
                    val obj = element.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                    val title = obj["title"]?.jsonPrimitive?.contentOrNull
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull
                    val movieName = title ?: name ?: return@mapNotNull null
                    val origin = obj["original_title"]?.jsonPrimitive?.contentOrNull ?: obj["original_name"]?.jsonPrimitive?.contentOrNull
                    val posterPath = obj["poster_path"]?.jsonPrimitive?.contentOrNull
                    val backdropPath = obj["backdrop_path"]?.jsonPrimitive?.contentOrNull
                    val dateStr = obj["release_date"]?.jsonPrimitive?.contentOrNull ?: obj["first_air_date"]?.jsonPrimitive?.contentOrNull
                    val year = dateStr?.take(4)?.toIntOrNull()
                    val voteAvg = obj["vote_average"]?.jsonPrimitive?.doubleOrNull
                    val mediaType = obj["media_type"]?.jsonPrimitive?.contentOrNull
                    val type = when {
                        mediaType != null -> if (mediaType == "movie") "movie" else "series"
                        title != null -> "movie"
                        defaultType != null -> defaultType
                        else -> "series"
                    }
                    MovieItem(
                        slug = movieName,
                        name = movieName,
                        originName = origin,
                        posterUrl = TmdbConfig.proxiedImageUrl(posterPath, "w500"),
                        thumbUrl = TmdbConfig.proxiedImageUrl(backdropPath ?: posterPath, "w500"),
                        year = year,
                        type = type,
                        rating = voteAvg,
                        providerId = "tmdb",
                        tmdbId = id.toString()
                    )
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Lấy danh sách phim theo diễn viên (Actor) từ TMDB
     */
    suspend fun getMoviesByPerson(
        personId: Long,
        language: String = TmdbConfig.getAppLanguage()
    ): List<MovieItem> = withContext(Dispatchers.IO) {
        val endpoint = "person/$personId/combined_credits"
        val params = mapOf("language" to language)
        val bodyStr = fetchTmdbApi(endpoint, params) ?: return@withContext emptyList()
        parseMoviesFromJson(bodyStr).take(15)
    }

    /**
     * Tìm kiếm diễn viên theo tên rồi lấy danh sách phim tham gia
     */
    suspend fun getMoviesByPersonName(
        personName: String,
        language: String = TmdbConfig.getAppLanguage()
    ): List<MovieItem> = withContext(Dispatchers.IO) {
        val clean = personName.trim()
        if (clean.isBlank()) return@withContext emptyList()
        val params = mapOf(
            "query" to clean,
            "language" to language
        )
        val searchBody = fetchTmdbApi("search/person", params) ?: return@withContext emptyList()
        val personId = try {
            val root = json.parseToJsonElement(searchBody).jsonObject
            val results = root["results"]?.jsonArray ?: return@withContext emptyList()
            results.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.longOrNull
        } catch (_: Exception) {
            null
        } ?: return@withContext emptyList()

        getMoviesByPerson(personId, language)
    }

    /**
     * Lấy danh sách phim theo thể loại (Genre ID) từ TMDB
     */
    suspend fun getMoviesByGenre(
        genreId: Int,
        isTv: Boolean = false,
        language: String = TmdbConfig.getAppLanguage()
    ): List<MovieItem> = withContext(Dispatchers.IO) {
        val endpoint = if (isTv) "discover/tv" else "discover/movie"
        val params = mapOf(
            "language" to language,
            "with_genres" to genreId.toString(),
            "sort_by" to "popularity.desc"
        )
        val bodyStr = fetchTmdbApi(endpoint, params) ?: return@withContext emptyList()
        parseMoviesFromJson(bodyStr, if (isTv) "series" else "movie").take(15)
    }

    /**
     * Lấy danh sách phim theo tên thể loại
     */
    suspend fun getMoviesByGenreName(
        genreName: String,
        isTv: Boolean = false,
        language: String = TmdbConfig.getAppLanguage()
    ): List<MovieItem> = withContext(Dispatchers.IO) {
        val genreId = resolveGenreId(genreName, isTv)
        if (genreId != null) {
            getMoviesByGenre(genreId, isTv, language)
        } else {
            emptyList()
        }
    }

    /**
     * Ánh xạ tên thể loại sang Genre ID chuẩn của TMDB
     */
    fun resolveGenreId(name: String, isTv: Boolean = false): Int? {
        val n = name.trim().lowercase()
        return when {
            n.contains("hành động") || n.contains("action") -> if (isTv) 10759 else 28
            n.contains("phiêu lưu") || n.contains("adventure") -> if (isTv) 10759 else 12
            n.contains("hoạt hình") || n.contains("animation") || n.contains("anime") -> 16
            n.contains("hài") || n.contains("comedy") -> 35
            n.contains("tội phạm") || n.contains("crime") || n.contains("hình sự") -> 80
            n.contains("tài liệu") || n.contains("documentary") -> 99
            n.contains("chính kịch") || n.contains("drama") || n.contains("tâm lý") -> 18
            n.contains("gia đình") || n.contains("family") -> if (isTv) 10762 else 10751
            n.contains("giả tưởng") || n.contains("fantasy") || n.contains("kỳ ảo") -> if (isTv) 10765 else 14
            n.contains("lịch sử") || n.contains("history") || n.contains("cổ trang") -> 36
            n.contains("kinh dị") || n.contains("horror") -> 27
            n.contains("âm nhạc") || n.contains("music") -> 10402
            n.contains("bí ẩn") || n.contains("mystery") -> 9648
            n.contains("lãng mạn") || n.contains("tình cảm") || n.contains("romance") -> 10749
            n.contains("khoa học") || n.contains("viễn tưởng") || n.contains("sci-fi") -> if (isTv) 10765 else 878
            n.contains("gay cấn") || n.contains("thriller") || n.contains("hồi hộp") -> 53
            n.contains("chiến tranh") || n.contains("war") -> if (isTv) 10768 else 10752
            n.contains("miền tây") || n.contains("western") -> 37
            n.contains("võ thuật") || n.contains("kiếm hiệp") -> 28
            else -> GENRES.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id?.takeIf { it > 0 }
        }
    }

    /**
     * Lấy danh sách phim theo quốc gia sản xuất từ TMDB
     */
    suspend fun getMoviesByCountry(
        countryNameOrIso: String,
        isTv: Boolean = false,
        language: String = TmdbConfig.getAppLanguage()
    ): List<MovieItem> = withContext(Dispatchers.IO) {
        val iso = resolveCountryIso(countryNameOrIso) ?: return@withContext emptyList()
        val endpoint = if (isTv) "discover/tv" else "discover/movie"
        val params = mapOf(
            "language" to language,
            "with_origin_country" to iso,
            "sort_by" to "popularity.desc"
        )
        val bodyStr = fetchTmdbApi(endpoint, params) ?: return@withContext emptyList()
        parseMoviesFromJson(bodyStr, if (isTv) "series" else "movie").take(15)
    }

    /**
     * Ánh xạ tên quốc gia (Tiếng Việt/Anh) sang ISO 3166-1 alpha-2
     */
    fun resolveCountryIso(country: String): String? {
        val c = country.trim().lowercase()
        return when {
            c.length == 2 -> country.trim().uppercase()
            c.contains("hàn") || c.contains("korea") -> "KR"
            c.contains("trung") || c.contains("china") -> "CN"
            c.contains("nhật") || c.contains("japan") -> "JP"
            c.contains("mỹ") || c.contains("hoa kỳ") || c.contains("united states") || c.contains("usa") || c.contains("âu mỹ") -> "US"
            c.contains("việt") || c.contains("vietnam") -> "VN"
            c.contains("thái") || c.contains("thailand") -> "TH"
            c.contains("hồng kông") || c.contains("hong kong") -> "HK"
            c.contains("đài loan") || c.contains("taiwan") -> "TW"
            c.contains("anh") || c.contains("united kingdom") || c.contains("uk") -> "GB"
            c.contains("pháp") || c.contains("france") -> "FR"
            c.contains("ấn độ") || c.contains("india") -> "IN"
            c.contains("tây ban nha") || c.contains("spain") -> "ES"
            c.contains("đức") || c.contains("germany") -> "DE"
            c.contains("ý") || c.contains("italy") -> "IT"
            c.contains("nga") || c.contains("russia") -> "RU"
            c.contains("canada") -> "CA"
            c.contains("úc") || c.contains("australia") -> "AU"
            else -> null
        }
    }

    /**
     * Lấy danh sách phim theo danh mục TMDB catalog
     * Hỗ trợ cơ chế kép (Dual Fallback): Thử qua Cloudflare Worker Proxy trước,
     * nếu gặp lỗi kết nối/chặn mạng thì tự động fallback gọi trực tiếp TMDB API.
     */
    suspend fun getCatalogMovies(
        catalogId: String,
        page: Int = 1,
        genreId: Int? = null,
        language: String = TmdbConfig.getAppLanguage()
    ): List<MovieItem> = withContext(Dispatchers.IO) {
        val (endpoint, params) = resolveCatalogEndpoint(catalogId, page, genreId, language)
        val bodyStr = fetchTmdbApi(endpoint, params) ?: return@withContext emptyList()
        val defaultType = if (catalogId.contains("tv")) "series" else "movie"
        parseMoviesFromJson(bodyStr, defaultType)
    }

    private fun resolveCatalogEndpoint(
        catalogId: String,
        page: Int,
        genreId: Int?,
        language: String
    ): Pair<String, Map<String, String>> {
        val baseParams = mutableMapOf(
            "language" to language,
            "page" to page.toString()
        )
        if (genreId != null && genreId > 0) {
            baseParams["with_genres"] = genreId.toString()
        }

        return when (catalogId) {
            // Movies catalogs (Hình 2: Popular, Now Playing, Upcoming, Top Rated)
            "tmdb_movie_popular" -> "movie/popular" to baseParams
            "tmdb_movie_now_playing" -> "movie/now_playing" to baseParams
            "tmdb_movie_upcoming" -> "movie/upcoming" to baseParams
            "tmdb_movie_top_rated" -> "movie/top_rated" to baseParams
            // TV Shows catalogs (Hình 2: Popular, Airing Today, On The Air, Top Rated)
            "tmdb_tv_popular" -> "tv/popular" to baseParams
            "tmdb_tv_airing_today" -> "tv/airing_today" to baseParams
            "tmdb_tv_on_the_air" -> "tv/on_the_air" to baseParams
            "tmdb_tv_top_rated" -> "tv/top_rated" to baseParams
            // Backward-compatible aliases
            "tmdb_now_playing" -> "movie/now_playing" to baseParams
            "tmdb_top_rated" -> "movie/top_rated" to baseParams
            "tmdb_on_the_air" -> "tv/on_the_air" to baseParams
            "tmdb_popular_movie" -> "movie/popular" to baseParams
            "tmdb_popular_series" -> "tv/popular" to baseParams
            else -> "movie/popular" to baseParams
        }
    }

    companion object {
        // 4 Catalog chính của Movies & 4 Catalog chính của TV Shows (Hình 2)
        val CATALOGS = listOf(
            // Movies (Phim lẻ / Chiếu rạp)
            TmdbCatalogDefinition("tmdb_movie_popular", "Phổ biến", "movie", "🌟"),
            TmdbCatalogDefinition("tmdb_movie_now_playing", "Đang chiếu rạp", "movie", "🍿"),
            TmdbCatalogDefinition("tmdb_movie_upcoming", "Sắp chiếu", "movie", "⏳"),
            TmdbCatalogDefinition("tmdb_movie_top_rated", "Đánh giá cao nhất", "movie", "🏆"),
            // TV Shows (Phim bộ / Truyền hình)
            TmdbCatalogDefinition("tmdb_tv_popular", "Phổ biến", "series", "🌟"),
            TmdbCatalogDefinition("tmdb_tv_airing_today", "Phát sóng hôm nay", "series", "📺"),
            TmdbCatalogDefinition("tmdb_tv_on_the_air", "Đang phát sóng", "series", "🔥"),
            TmdbCatalogDefinition("tmdb_tv_top_rated", "Đánh giá cao nhất", "series", "🏆")
        )

        val GENRES = listOf(
            TmdbGenre(0, "Tất cả thể loại"),
            TmdbGenre(28, "Hành Động"),
            TmdbGenre(12, "Phiêu Lưu"),
            TmdbGenre(16, "Hoạt Hình"),
            TmdbGenre(35, "Hài Hước"),
            TmdbGenre(80, "Tội Phạm"),
            TmdbGenre(99, "Tài Liệu"),
            TmdbGenre(18, "Chính Kịch"),
            TmdbGenre(10751, "Gia Đình"),
            TmdbGenre(14, "Giả Tưởng"),
            TmdbGenre(36, "Lịch Sử"),
            TmdbGenre(27, "Kinh Dị"),
            TmdbGenre(10402, "Âm Nhạc"),
            TmdbGenre(9648, "Bí Ẩn"),
            TmdbGenre(10749, "Lãng Mạn"),
            TmdbGenre(878, "Khoa Học Viễn Tưởng"),
            TmdbGenre(53, "Gay Cấn"),
            TmdbGenre(10752, "Chiến Tranh"),
            TmdbGenre(37, "Miền Tây")
        )
    }
}
