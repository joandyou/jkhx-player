package com.jkhx.player.core.network.tmdb

import java.net.URLEncoder
import java.util.Locale

object TmdbConfig {
    const val DEFAULT_API_KEY = ""

    @Volatile
    var apiKey: String = DEFAULT_API_KEY

    const val TMDB_API_BASE = "https://api.themoviedb.org/3"
    const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p"

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
     * Tạo URL gọi trực tiếp TMDB API (hệ thống tự điều hướng qua SOCKS5 / HTTP Proxy nếu được cấu hình trong Cài đặt)
     */
    fun buildApiUrl(endpoint: String, params: Map<String, String> = emptyMap()): String {
        val activeKey = apiKey.trim().ifBlank { DEFAULT_API_KEY }
        val queryBuilder = StringBuilder("api_key=$activeKey")
        params.forEach { (key, value) ->
            queryBuilder.append("&")
                .append(key)
                .append("=")
                .append(URLEncoder.encode(value, "UTF-8"))
        }
        return "$TMDB_API_BASE/$endpoint?$queryBuilder"
    }

    /**
     * Tương thích ngược: chuyển hướng gọi trực tiếp
     */
    fun proxiedApiUrl(endpoint: String, params: Map<String, String> = emptyMap()): String {
        return buildApiUrl(endpoint, params)
    }

    /**
     * Tạo URL tải ảnh từ TMDB CDN (image.tmdb.org)
     * Kích thước khuyến nghị: w500 (poster), w1280 hoặc original (backdrop), w185 (thumbnail)
     */
    fun imageUrl(path: String?, size: String = "w500"): String? {
        if (path.isNullOrBlank()) return null
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return "$TMDB_IMAGE_BASE/$size$cleanPath"
    }

    /**
     * Tương thích ngược: chuyển hướng gọi trực tiếp
     */
    fun proxiedImageUrl(path: String?, size: String = "original"): String? {
        return imageUrl(path, size)
    }
}
