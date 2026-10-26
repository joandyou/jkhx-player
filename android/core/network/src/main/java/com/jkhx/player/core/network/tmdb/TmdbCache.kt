package com.jkhx.player.core.network.tmdb

import java.util.concurrent.ConcurrentHashMap

/**
 * Bộ nhớ đệm In-Memory cho dữ liệu TMDB
 * Tránh lặp lại network request qua Proxy khi chuyển tab, cuộn phim hoặc quay lại màn hình
 */
object TmdbCache {
    private val memoryCache = ConcurrentHashMap<String, TmdbDetailResponse>()

    fun get(key: String): TmdbDetailResponse? = memoryCache[key]

    fun put(key: String, response: TmdbDetailResponse) {
        if (memoryCache.size > 300) {
            val iterator = memoryCache.keys().iterator()
            var count = 0
            while (iterator.hasNext() && count < 50) {
                memoryCache.remove(iterator.next())
                count++
            }
        }
        memoryCache[key] = response
    }

    fun clear() {
        memoryCache.clear()
    }
}
