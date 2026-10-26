package com.jkhx.player.plugin.api

import com.jkhx.player.core.model.*

interface LoadedPlugin : AutoCloseable {
    val id: String
    val name: String
    val version: String

    // === CÁC HÀM BẮT BUỘC (MANDATORY) ===
    suspend fun getHome(): HomeResult
    suspend fun search(keyword: String, page: Int = 1): SearchResult
    suspend fun getDetail(slug: String): MovieDetail
    suspend fun getStream(episodeData: EpisodeData): EpisodeStream
    suspend fun getList(
        type: String?,
        slug: String?,
        page: Int = 1,
        filters: Map<String, String>? = null
    ): SearchResult = search(slug ?: type ?: "", page)
    suspend fun getMenu(forceRefresh: Boolean = false): List<MenuItem>? = null

    // === CÁC HÀM TÙY CHỌN (OPTIONAL) ===
    suspend fun getFilters(forceRefresh: Boolean = false): PluginFilterConfig? = null
    suspend fun getSettings(): List<PluginSettingItem>? = null
    suspend fun checkHealth(): PluginHealthStatus? = null

    // Quản lý bộ nhớ đệm & Hủy tác vụ
    fun clearCache() {}
    fun cancelTasks() {}
}

interface PluginStorage {
    fun get(pluginId: String, key: String): String?
    fun set(pluginId: String, key: String, value: String)
    fun remove(pluginId: String, key: String)
    fun clear(pluginId: String) {}
}

interface PluginLogger {
    fun info(pluginId: String, message: String)
    fun warn(pluginId: String, message: String)
    fun error(pluginId: String, message: String, throwable: Throwable? = null)
}

interface PluginRuntime : AutoCloseable {
    suspend fun loadPlugin(
        manifest: PluginManifest,
        scriptContent: String
    ): LoadedPlugin
}

