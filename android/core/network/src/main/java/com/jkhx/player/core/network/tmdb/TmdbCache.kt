package com.jkhx.player.core.network.tmdb

import java.util.concurrent.ConcurrentHashMap

/**
 * Bộ nhớ đệm In-Memory cho dữ liệu TMDB
 * Tránh lặp lại network request qua Proxy khi chuyển tab, cuộn phim hoặc quay lại màn hình
 */
object TmdbCache {
    private val memoryCache = ConcurrentHashMap<String, TmdbDetailResponse>()
    private val seasonCache = ConcurrentHashMap<String, TmdbSeasonResponse>()

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

    fun getSeason(key: String): TmdbSeasonResponse? = seasonCache[key]

    fun putSeason(key: String, response: TmdbSeasonResponse) {
        if (seasonCache.size > 200) {
            val iterator = seasonCache.keys().iterator()
            var count = 0
            while (iterator.hasNext() && count < 30) {
                seasonCache.remove(iterator.next())
                count++
            }
        }
        seasonCache[key] = response
    }

    fun clear() {
        memoryCache.clear()
        seasonCache.clear()
    }
}
