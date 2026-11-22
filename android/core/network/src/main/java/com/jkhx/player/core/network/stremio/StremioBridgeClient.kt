package com.jkhx.player.core.network.stremio

import com.jkhx.player.core.model.stremio.*
import com.jkhx.player.core.network.HttpBridgeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

        fun buildTorrServerStreamUrl(rawServerUrl: String, infoHash: String, fileIndex1Based: Int): String {
            val baseUrl = rawServerUrl.trim().trimEnd('/')
            val cleanHash = infoHash.trim().lowercase()
            val magnet = URLEncoder.encode("magnet:?xt=urn:btih:$cleanHash", StandardCharsets.UTF_8.name())
            val safeIdx = fileIndex1Based.coerceAtLeast(1)
            return "$baseUrl/stream?link=$magnet&index=$safeIdx&play"
        }

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

    /**
     * Chuẩn hóa URL máy chủ Stremio Streaming Server
     */
    fun normalizeServerUrl(rawUrl: String): String {
        var url = rawUrl.trim()
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            url = "http://$url"
        }
        return url.trimEnd('/')
    }

    /**
     * Kiểm tra trạng thái máy chủ Stremio Server (online, latency, cache size, version)
     */
    suspend fun checkStreamingServerHealth(rawServerUrl: String): StremioServerHealth = withContext(Dispatchers.IO) {
        val baseUrl = normalizeServerUrl(rawServerUrl)
        val start = System.currentTimeMillis()
        val fastClient = client.newBuilder()
            .connectTimeout(3500, TimeUnit.MILLISECONDS)
            .readTimeout(3500, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder()
            .url("$baseUrl/settings")
            .header("User-Agent", "Stremio/4.4.168 (Android)")
            .header("Accept", "application/json")
            .build()

        try {
            val response = fastClient.newCall(request).execute()
            val latency = System.currentTimeMillis() - start
            if (response.isSuccessful) {
                val bodyStr = response.body?.string().orEmpty()
                val settings = try {
                    json.decodeFromString<StremioServerSettings>(bodyStr)
                } catch (_: Exception) { null }

                StremioServerHealth(
                    isOnline = true,
                    latencyMs = latency,
                    version = settings?.values?.serverVersion ?: "v0.1.8",
                    cacheSizeFormatted = settings?.values?.cacheSizeFormatted,
                    rawCacheSize = settings?.values?.cacheSize?.toLong(),
                    btProfile = settings?.values?.btProfile,
                    maxConnections = settings?.values?.btMaxConnections
                )
            } else {
                // Thử fallback sang /stats.json
                val statsReq = Request.Builder()
                    .url("$baseUrl/stats.json")
                    .header("User-Agent", "Stremio/4.4.168 (Android)")
                    .build()
                val statsResp = fastClient.newCall(statsReq).execute()
                if (statsResp.isSuccessful) {
                    StremioServerHealth(
                        isOnline = true,
                        latencyMs = System.currentTimeMillis() - start,
                        version = "v0.1.8"
                    )
                } else {
                    StremioServerHealth(isOnline = false)
                }
            }
        } catch (_: Exception) {
            StremioServerHealth(isOnline = false)
        }
    }

    /**
     * Lấy thống kê torrent theo thời gian thực: GET /{infoHash}/stats.json
     */
    suspend fun fetchTorrentStats(rawServerUrl: String, infoHash: String): StremioTorrentStats? = withContext(Dispatchers.IO) {
        val baseUrl = normalizeServerUrl(rawServerUrl)
        val cleanHash = infoHash.trim().lowercase()
        val endpoint = "$baseUrl/$cleanHash/stats.json"

        val fastClient = client.newBuilder()
            .connectTimeout(2500, TimeUnit.MILLISECONDS)
            .readTimeout(2500, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder()
            .url(endpoint)
            .header("User-Agent", "Stremio/4.4.168 (Android)")
            .header("Accept", "application/json")
            .build()

        try {
            val response = fastClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string() ?: return@withContext null
            json.decodeFromString<StremioTorrentStats>(body)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Cập nhật cấu hình máy chủ Stremio Server: POST /settings
     * Body JSON ví dụ: {"cacheSize": 2147483648} hoặc {"btProfile": "custom"}
     */
    suspend fun updateStreamingServerSettings(rawServerUrl: String, jsonBody: String): Boolean = withContext(Dispatchers.IO) {
        val baseUrl = normalizeServerUrl(rawServerUrl)
        val endpoint = "$baseUrl/settings"
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = jsonBody.toRequestBody(mediaType)
        val request = Request.Builder()
            .url(endpoint)
            .post(body)
            .header("User-Agent", "Stremio/4.4.168 (Android)")
            .build()
        try {
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Tự động xác định index file video chính xác trong torrent:
     * - Nếu có streamName từ Stremio Server: tìm file có tên khớp.
     * - Nếu không: lọc danh sách files tìm file có định dạng video (.mp4, .mkv, .avi, ...) lớn nhất.
     */
    suspend fun resolveTorrentFileIdx(
        rawServerUrl: String,
        infoHash: String,
        preferredFileIdx: Int? = null
    ): Int = withContext(Dispatchers.IO) {
        if (preferredFileIdx != null && preferredFileIdx >= 0) {
            return@withContext preferredFileIdx
        }
        var attempts = 0
        while (attempts < 5) {
            val stats = fetchTorrentStats(rawServerUrl, infoHash)
            if (stats != null && stats.files.isNotEmpty()) {
                // 1. Khớp theo streamName nếu máy chủ Stremio đã gán stream mặc định
                val sName = stats.streamName
                if (!sName.isNullOrBlank()) {
                    val streamBaseName = sName.substringAfterLast('/')
                    val matchedIdx = stats.files.indexOfFirst {
                        it.name.equals(sName, ignoreCase = true) ||
                            it.name.substringAfterLast('/').equals(streamBaseName, ignoreCase = true) ||
                            it.path?.equals(sName, ignoreCase = true) == true
                    }
                    if (matchedIdx >= 0) return@withContext matchedIdx
                }

                // 2. Lọc các file video và lấy file có dung lượng lớn nhất
                val videoExtensions = listOf(".mp4", ".mkv", ".avi", ".webm", ".ts", ".mov", ".m4v", ".flv")
                val videoFilesWithIndex = stats.files.mapIndexed { idx, f -> idx to f }
                    .filter { (_, f) -> videoExtensions.any { ext -> f.name.endsWith(ext, ignoreCase = true) } }

                if (videoFilesWithIndex.isNotEmpty()) {
                    val best = videoFilesWithIndex.maxByOrNull { it.second.length }
                    if (best != null && best.second.length > 5L * 1024L * 1024L) {
                        return@withContext best.first
                    }
                }

                // 3. Fallback lấy file lớn nhất trong torrent (nếu > 20MB)
                val largest = stats.files.mapIndexed { idx, f -> idx to f }.maxByOrNull { it.second.length }
                if (largest != null && largest.second.length > 20L * 1024L * 1024L) {
                    return@withContext largest.first
                }
            }
            attempts++
            kotlinx.coroutines.delay(500)
        }
        0
    }

    /**
     * Kiểm tra trạng thái TorrServer: GET /echo (hỗ trợ Basic Auth).
     */
    suspend fun checkTorrServerHealth(rawServerUrl: String, authHeader: String?): StremioServerHealth = withContext(Dispatchers.IO) {
        val baseUrl = normalizeServerUrl(rawServerUrl)
        val fastClient = client.newBuilder()
            .connectTimeout(3500, TimeUnit.MILLISECONDS)
            .readTimeout(3500, TimeUnit.MILLISECONDS)
            .build()

        val reqBuilder = Request.Builder().url("$baseUrl/echo")
        if (!authHeader.isNullOrBlank()) {
            reqBuilder.header("Authorization", authHeader)
        }

        try {
            val start = System.currentTimeMillis()
            val resp = fastClient.newCall(reqBuilder.build()).execute()
            val latency = System.currentTimeMillis() - start
            if (resp.isSuccessful) {
                val ver = resp.body?.string()?.trim()?.take(32).orEmpty().ifBlank { "TorrServer" }
                StremioServerHealth(isOnline = true, latencyMs = latency, version = ver)
            } else if (resp.code == 401) {
                StremioServerHealth(isOnline = false, version = "Auth 401")
            } else {
                StremioServerHealth(isOnline = false)
            }
        } catch (_: Exception) {
            StremioServerHealth(isOnline = false)
        }
    }

    /**
     * Lấy thống kê torrent từ TorrServer: POST /torrents {"action":"get","hash":"..."}
     */
    suspend fun fetchTorrServerStats(
        rawServerUrl: String,
        authHeader: String?,
        infoHash: String
    ): StremioTorrentStats? = withContext(Dispatchers.IO) {
        val baseUrl = normalizeServerUrl(rawServerUrl)
        val cleanHash = infoHash.trim().lowercase()
        val payload = """{"action":"get","hash":"$cleanHash"}"""
        val body = payload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val fastClient = client.newBuilder()
            .connectTimeout(3000, TimeUnit.MILLISECONDS)
            .readTimeout(3000, TimeUnit.MILLISECONDS)
            .build()

        val reqBuilder = Request.Builder()
            .url("$baseUrl/torrents")
            .post(body)
            .header("Accept", "application/json")
        if (!authHeader.isNullOrBlank()) {
            reqBuilder.header("Authorization", authHeader)
        }

        try {
            val resp = fastClient.newCall(reqBuilder.build()).execute()
            if (!resp.isSuccessful) return@withContext null
            val text = resp.body?.string() ?: return@withContext null
            val obj = json.parseToJsonElement(text).jsonObject
            val torrentSize = obj["torrent_size"]?.jsonPrimitive?.longOrNull ?: 0L
            val loadedSize = obj["loaded_size"]?.jsonPrimitive?.longOrNull
                ?: obj["bytes_read"]?.jsonPrimitive?.longOrNull
                ?: 0L
            val progress = if (torrentSize > 0L) (loadedSize.toDouble() / torrentSize.toDouble()).coerceIn(0.0, 1.0) else 0.0

            val fileStatsArr = obj["file_stats"]?.jsonArray
            val files = mutableListOf<StremioTorrentFile>()
            if (fileStatsArr != null) {
                for (el in fileStatsArr) {
                    val fObj = el.jsonObject
                    val path = fObj["path"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    files.add(
                        StremioTorrentFile(
                            name = path.substringAfterLast('/'),
                            length = fObj["length"]?.jsonPrimitive?.longOrNull ?: 0L,
                            path = path
                        )
                    )
                }
            }

            val titleVal = obj["title"]?.jsonPrimitive?.contentOrNull
                ?: obj["name"]?.jsonPrimitive?.contentOrNull

            StremioTorrentStats(
                infoHash = obj["hash"]?.jsonPrimitive?.contentOrNull ?: cleanHash,
                name = titleVal?.ifBlank { null },
                peers = obj["active_peers"]?.jsonPrimitive?.intOrNull ?: 0,
                downloadSpeed = obj["download_speed"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                uploadSpeed = obj["upload_speed"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                downloaded = loadedSize,
                streamProgress = progress,
                streamLen = torrentSize,
                swarmConnections = obj["connected_seeders"]?.jsonPrimitive?.intOrNull ?: 0,
                swarmSize = obj["total_peers"]?.jsonPrimitive?.intOrNull ?: 0,
                files = files
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Xác định chỉ số file (1-based) cho TorrServer:
     * - Nếu addon đã cung cấp fileIdx (0-based), trả về fileIdx + 1.
     * - Nếu chưa có, gọi POST /torrents (action="add") và chọn file video lớn nhất trong file_stats.
     */
    suspend fun resolveTorrServerFileIndex(
        rawServerUrl: String,
        authHeader: String?,
        infoHash: String,
        preferredFileIdx: Int? = null
    ): Int = withContext(Dispatchers.IO) {
        val baseUrl = normalizeServerUrl(rawServerUrl)
        val cleanHash = infoHash.trim().lowercase()
        val payload = """{"action":"add","link":"magnet:?xt=urn:btih:$cleanHash","save_to_db":false}"""
        val body = payload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val reqBuilder = Request.Builder()
            .url("$baseUrl/torrents")
            .post(body)
            .header("Accept", "application/json")
        if (!authHeader.isNullOrBlank()) {
            reqBuilder.header("Authorization", authHeader)
        }

        try {
            val resp = client.newCall(reqBuilder.build()).execute()
            if (preferredFileIdx != null && preferredFileIdx >= 0) {
                resp.close()
                return@withContext preferredFileIdx + 1
            }
            if (resp.isSuccessful) {
                val text = resp.body?.string()
                if (!text.isNullOrBlank()) {
                    val obj = json.parseToJsonElement(text).jsonObject
                    val fileStatsArr = obj["file_stats"]?.jsonArray
                    if (fileStatsArr != null && fileStatsArr.isNotEmpty()) {
                        val videoExts = listOf(".mp4", ".mkv", ".avi", ".webm", ".ts", ".mov", ".m4v", ".flv")
                        var bestVideoId = -1
                        var bestVideoLen = -1L
                        var largestId = 1
                        var largestLen = -1L
                        fileStatsArr.forEachIndexed { idx, el ->
                            val fObj = el.jsonObject
                            val id = fObj["id"]?.jsonPrimitive?.intOrNull ?: (idx + 1)
                            val path = fObj["path"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            val len = fObj["length"]?.jsonPrimitive?.longOrNull ?: 0L
                            if (len > largestLen) {
                                largestLen = len
                                largestId = id
                            }
                            if (videoExts.any { path.endsWith(it, ignoreCase = true) } && len > bestVideoLen) {
                                bestVideoLen = len
                                bestVideoId = id
                            }
                        }
                        if (bestVideoId > 0) return@withContext bestVideoId
                        if (largestId > 0) return@withContext largestId
                    }
                }
            }
        } catch (_: Exception) {
        }
        1
    }
}

