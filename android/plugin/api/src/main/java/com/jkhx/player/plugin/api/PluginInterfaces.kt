package com.jkhx.player.plugin.api

import com.jkhx.player.core.model.*

interface LoadedPlugin : AutoCloseable {
    val id: String
    val name: String
    val version: String

    suspend fun getHome(): HomeResult
    suspend fun search(keyword: String, page: Int = 1): SearchResult
    suspend fun getDetail(slug: String): MovieDetail
    suspend fun getStream(episodeData: EpisodeData): EpisodeStream
}

interface PluginStorage {
    fun get(pluginId: String, key: String): String?
    fun set(pluginId: String, key: String, value: String)
    fun remove(pluginId: String, key: String)
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
