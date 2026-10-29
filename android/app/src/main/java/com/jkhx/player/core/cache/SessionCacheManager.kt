package com.jkhx.player.core.cache

import com.jkhx.player.core.model.HomeSection
import com.jkhx.player.core.model.MenuItem
import com.jkhx.player.core.model.MovieItem
import com.jkhx.player.ui.screens.stream.UnifiedStreamItem
import java.util.concurrent.ConcurrentHashMap

data class CachedHomeFeed(
    val sections: List<HomeSection>,
    val quickCategories: List<HomeSection>,
    val menuItems: List<MenuItem>
)

/**
 * Quản lý bộ đệm In-Memory trong phiên hoạt động hiện tại (Session Cache).
 * - Lưu trữ tạm thời dữ liệu trong RAM giúp truy cập tức thì (0ms) khi chuyển đổi qua lại
 *   giữa các màn hình, chuyển đổi plugin/addon, hoặc khi từ Player quay về Chọn luồng.
 * - Chỉ tồn tại trong tiến trình (process) hiện tại của ứng dụng. Khi thoát/kill app trong Android
 *   và mở lại, toàn bộ bộ nhớ được giải phóng và ứng dụng sẽ tải mới lại từ đầu.
 * - Dữ liệu chỉ được làm mới khi người dùng chủ động nhấn nút Refresh (Làm mới) trên giao diện.
 */
object SessionCacheManager {
    // 1. Home Feed Cache: key là providerId (ID của plugin / Stremio Addon)
    private val homeFeedCache = ConcurrentHashMap<String, CachedHomeFeed>()

    // 2. Stream Results Cache: key là "$providerId:$movieSlug:$episodeId"
    private val streamCache = ConcurrentHashMap<String, List<UnifiedStreamItem>>()

    // 3. Search Results Cache: key là từ khóa tìm kiếm (chuẩn hóa lowercase)
    private val searchCache = ConcurrentHashMap<String, List<MovieItem>>()

    // 4. Discover Stremio Catalog Cache: key là "${addonUrl}_${type}_${catalogId}_${filterOption}"
    private val discoverCache = ConcurrentHashMap<String, List<MovieItem>>()

    // ==================== HOME FEED ====================
    fun getHomeFeed(providerId: String): CachedHomeFeed? {
        return homeFeedCache[providerId]
    }

    fun putHomeFeed(
        providerId: String,
        sections: List<HomeSection>,
        quickCategories: List<HomeSection>,
        menuItems: List<MenuItem>
    ) {
        if (providerId.isBlank()) return
        if (homeFeedCache.size >= 30) {
            homeFeedCache.keys.firstOrNull()?.let { homeFeedCache.remove(it) }
        }
        homeFeedCache[providerId] = CachedHomeFeed(sections, quickCategories, menuItems)
    }

    fun removeHomeFeed(providerId: String) {
        homeFeedCache.remove(providerId)
    }

    fun clearHomeFeeds() {
        homeFeedCache.clear()
    }

    // ==================== STREAMS ====================
    fun getStreams(key: String): List<UnifiedStreamItem>? {
        return streamCache[key]
    }

    fun putStreams(key: String, streams: List<UnifiedStreamItem>) {
        if (key.isBlank() || streams.isEmpty()) return
        if (streamCache.size >= 50) {
            streamCache.keys.firstOrNull()?.let { streamCache.remove(it) }
        }
        streamCache[key] = streams
    }

    fun removeStreams(key: String) {
        streamCache.remove(key)
    }

    fun clearStreams() {
        streamCache.clear()
    }

    // ==================== SEARCH ====================
    fun getSearchResult(query: String): List<MovieItem>? {
        val cleanKey = query.trim().lowercase()
        return searchCache[cleanKey]
    }

    fun putSearchResult(query: String, results: List<MovieItem>) {
        val cleanKey = query.trim().lowercase()
        if (cleanKey.isBlank()) return
        if (searchCache.size >= 50) {
            searchCache.keys.firstOrNull()?.let { searchCache.remove(it) }
        }
        searchCache[cleanKey] = results
    }

    fun removeSearchResult(query: String) {
        val cleanKey = query.trim().lowercase()
        searchCache.remove(cleanKey)
    }

    // ==================== DISCOVER CATALOG ====================
    fun getDiscoverCatalog(key: String): List<MovieItem>? {
        return discoverCache[key]
    }

    fun putDiscoverCatalog(key: String, items: List<MovieItem>) {
        if (key.isBlank() || items.isEmpty()) return
        if (discoverCache.size >= 30) {
            discoverCache.keys.firstOrNull()?.let { discoverCache.remove(it) }
        }
        discoverCache[key] = items
    }

    fun removeDiscoverCatalog(key: String) {
        discoverCache.remove(key)
    }

    // ==================== CLEAR ALL ====================
    fun clearAll() {
        homeFeedCache.clear()
        streamCache.clear()
        searchCache.clear()
        discoverCache.clear()
    }
}

