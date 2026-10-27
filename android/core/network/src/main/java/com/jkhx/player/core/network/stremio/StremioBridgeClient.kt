package com.jkhx.player.core.network.stremio

import com.jkhx.player.core.model.stremio.*
import com.jkhx.player.core.network.HttpBridgeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class StremioBridgeClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
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

    companion object {
        const val OFFICIAL_CINEMETA_URL = "https://v3-cinemeta.strem.io/manifest.json"

        /**
         * Chuẩn hóa URL Stremio:
         * - Đổi stremio:// thành https://
         * - Tự động bổ sung https:// nếu thiếu scheme
         * - Tự động bổ sung /manifest.json nếu người dùng dán link domain gốc
         */
        fun normalizeManifestUrl(rawUrl: String): String {
            var url = rawUrl.trim()
            if (url.startsWith("stremio://", ignoreCase = true)) {
                url = "https://" + url.substring("stremio://".length)
            } else if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                url = "https://$url"
            }
            url = url.trimEnd('/')
            if (!url.endsWith("/manifest.json", ignoreCase = true) && !url.endsWith(".json", ignoreCase = true)) {
                url = "$url/manifest.json"
            }
            return url
        }

        fun extractBaseUrl(manifestUrl: String): String {
            val normalized = normalizeManifestUrl(manifestUrl)
            return if (normalized.endsWith("/manifest.json", ignoreCase = true)) {
                normalized.substring(0, normalized.length - "/manifest.json".length)
            } else {
                normalized.substringBeforeLast('/')
            }
        }
    }

    /**
     * Tải và phân tích manifest.json của Stremio Addon (trả về cả Manifest đã giải mã và chuỗi JSON nguyên bản)
     */
    suspend fun fetchManifestWithRaw(manifestUrl: String): Pair<StremioManifest, String> = withContext(Dispatchers.IO) {
        val targetUrl = normalizeManifestUrl(manifestUrl)
        val request = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", "Stremio/4.4.168 (Android)")
            .header("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}: Không thể tải manifest từ $targetUrl")
        }
        val bodyStr = response.body?.string() ?: throw Exception("Empty manifest body")
        val manifest = json.decodeFromString<StremioManifest>(bodyStr)
        Pair(manifest, bodyStr)
    }

    /**
     * Tải và phân tích manifest.json của Stremio Addon
     */
    suspend fun fetchManifest(manifestUrl: String): StremioManifest =
        fetchManifestWithRaw(manifestUrl).first

    /**
     * Tải danh sách phim (Catalog) theo chuẩn Stremio Protocol v3:
     * GET /catalog/{type}/{id}.json hoặc GET /catalog/{type}/{id}/{extraArgs}.json
     */
    suspend fun fetchCatalog(
        manifestUrl: String,
        type: String,
        id: String,
        skip: Int = 0,
        genre: String? = null,
        search: String? = null
    ): List<StremioMetaPreview> = withContext(Dispatchers.IO) {
        val baseUrl = extractBaseUrl(manifestUrl)

        val extraParts = mutableListOf<String>()
        if (!search.isNullOrBlank()) {
            val encodedSearch = URLEncoder.encode(search.trim(), StandardCharsets.UTF_8.toString())
            extraParts.add("search=$encodedSearch")
        }
        if (!genre.isNullOrBlank()) {
            val encodedGenre = URLEncoder.encode(genre.trim(), StandardCharsets.UTF_8.toString())
            extraParts.add("genre=$encodedGenre")
        }
        if (skip > 0) {
            extraParts.add("skip=$skip")
        }

        val endpointUrl = if (extraParts.isNotEmpty()) {
            val extraString = extraParts.joinToString("&")
            "$baseUrl/catalog/$type/$id/$extraString.json"
        } else {
            "$baseUrl/catalog/$type/$id.json"
        }

        val request = Request.Builder()
            .url(endpointUrl)
            .header("User-Agent", "Stremio/4.4.168 (Android)")
            .header("Accept", "application/json")
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext emptyList()
            }
            val body = response.body?.string() ?: return@withContext emptyList()
            val parsed = json.decodeFromString<StremioCatalogResponse>(body)
            parsed.metas
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Tải chi tiết thông tin phim & danh sách tập:
     * GET /meta/{type}/{id}.json
     * Hỗ trợ fallback sang Cinemeta nếu addon hiện tại không trả về meta và id là IMDb ("tt...")
     */
    suspend fun fetchMeta(
        manifestUrl: String,
        type: String,
        id: String
    ): StremioMetaDetail? = withContext(Dispatchers.IO) {
        val baseUrl = extractBaseUrl(manifestUrl)
        val encodedId = URLEncoder.encode(id, StandardCharsets.UTF_8.name()).replace("+", "%20")
        val endpointUrl = "$baseUrl/meta/$type/$encodedId.json"

        var meta = fetchMetaFromUrl(endpointUrl)
        val imdbMatch = Regex("""(tt\d+)""", RegexOption.IGNORE_CASE).find(id)?.groupValues?.get(1)
        if (meta == null && !imdbMatch.isNullOrBlank() && !manifestUrl.contains("cinemeta")) {
            // Fallback sang Cinemeta chính thức
            val cinemetaBase = extractBaseUrl(OFFICIAL_CINEMETA_URL)
            val cinemetaType = if (type.equals("series", ignoreCase = true) || type.equals("tv", ignoreCase = true)) "series" else "movie"
            meta = fetchMetaFromUrl("$cinemetaBase/meta/$cinemetaType/$imdbMatch.json")
            if (meta == null) {
                val altType = if (cinemetaType == "movie") "series" else "movie"
                meta = fetchMetaFromUrl("$cinemetaBase/meta/$altType/$imdbMatch.json")
            }
        }
        meta
    }

    private fun fetchMetaFromUrl(url: String): StremioMetaDetail? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Stremio/4.4.168 (Android)")
            .header("Accept", "application/json")
            .build()
        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val parsed = json.decodeFromString<StremioMetaResponse>(body)
            parsed.meta
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Tải danh sách luồng phát (Streams):
     * GET /stream/{type}/{videoId}.json
     * Ví dụ: /stream/movie/tt1234567.json hoặc /stream/series/tt0903747:1:1.json
     */
    suspend fun fetchStreams(
        manifestUrl: String,
        type: String,
        videoId: String
    ): List<StremioStream> = withContext(Dispatchers.IO) {
        val baseUrl = extractBaseUrl(manifestUrl)
        val encodedVideoId = URLEncoder.encode(videoId, StandardCharsets.UTF_8.name()).replace("+", "%20")
        val endpointUrl = "$baseUrl/stream/$type/$encodedVideoId.json"

        val request = Request.Builder()
            .url(endpointUrl)
            .header("User-Agent", "Stremio/4.4.168 (Android)")
            .header("Accept", "application/json")
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: return@withContext emptyList()
            val parsed = json.decodeFromString<StremioStreamResponse>(body)
            parsed.streams
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Tải danh sách phụ đề từ Addon Subtitles:
     * GET /subtitles/{type}/{videoId}.json
     * Ví dụ: /subtitles/movie/tt1234567.json hoặc /subtitles/series/tt0903747:1:1.json
     */
    suspend fun fetchSubtitles(
        manifestUrl: String,
        type: String,
        videoId: String
    ): List<StremioStreamSubtitle> = withContext(Dispatchers.IO) {
        val baseUrl = extractBaseUrl(manifestUrl)
        val encodedVideoId = URLEncoder.encode(videoId, StandardCharsets.UTF_8.name()).replace("+", "%20")
        val endpointUrl = "$baseUrl/subtitles/$type/$encodedVideoId.json"

        val request = Request.Builder()
            .url(endpointUrl)
            .header("User-Agent", "Stremio/4.4.168 (Android)")
            .header("Accept", "application/json")
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: return@withContext emptyList()
            val parsed = json.decodeFromString<StremioSubtitleResponse>(body)
            parsed.subtitles.filter { it.url.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Kiểm tra độ trễ và khả năng phản hồi của Addon
     */
    suspend fun checkHealth(manifestUrl: String): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            fetchManifest(manifestUrl)
            val latency = System.currentTimeMillis() - start
            Pair(true, latency)
        } catch (_: Exception) {
            val latency = System.currentTimeMillis() - start
            Pair(false, latency)
        }
    }
}

