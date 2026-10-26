package com.jkhx.player.core.network.tmdb

import java.net.URLEncoder
import java.util.Locale

object TmdbConfig {
    const val DEFAULT_API_KEY = ""

    @Volatile
    var apiKey: String = DEFAULT_API_KEY

    const val TMDB_API_BASE = "https://api.themoviedb.org/3"
    const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p"
    const val CF_PROXY_BASE = "https://j-proxy-fallback.khx.workers.dev/?url="

    /**
     * Tự động lấy ngôn ngữ hệ thống hiện tại của App (ưu tiên vi-VN, sau này hỗ trợ en-US)
     */
    fun getAppLanguage(): String {
        val lang = Locale.getDefault().language.lowercase()
        return when {
            lang.startsWith("vi") -> "vi-VN"
            lang.startsWith("en") -> "en-US"
            else -> "vi-VN"
        }
    }

    /**
     * Tạo URL gọi TMDB API qua Cloudflare Worker Proxy
     */
    fun proxiedApiUrl(endpoint: String, params: Map<String, String> = emptyMap()): String {
        val activeKey = apiKey.trim().ifBlank { DEFAULT_API_KEY }
        val queryBuilder = StringBuilder("api_key=$activeKey")
        params.forEach { (key, value) ->
            queryBuilder.append("&")
                .append(key)
                .append("=")
                .append(URLEncoder.encode(value, "UTF-8"))
        }
        val targetUrl = "$TMDB_API_BASE/$endpoint?$queryBuilder"
        return "$CF_PROXY_BASE${URLEncoder.encode(targetUrl, "UTF-8")}"
    }

    /**
     * Tạo URL tải ảnh từ TMDB CDN (image.tmdb.org) qua Cloudflare Worker Proxy
     * Tránh triệt để tình trạng các nhà mạng VN chặn dải IP/domain ảnh của TMDB
     */
    fun proxiedImageUrl(path: String?, size: String = "original"): String? {
        if (path.isNullOrBlank()) return null
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        val targetUrl = "$TMDB_IMAGE_BASE/$size$cleanPath"
        return "$CF_PROXY_BASE${URLEncoder.encode(targetUrl, "UTF-8")}"
    }
}
