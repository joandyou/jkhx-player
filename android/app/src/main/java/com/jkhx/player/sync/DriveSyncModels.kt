package com.jkhx.player.sync

import kotlinx.serialization.Serializable

@Serializable
data class SyncFavoriteItem(
    val id: String, // "$providerId:$movieSlug"
    val providerId: String,
    val movieSlug: String,
    val movieName: String,
    val thumbUrl: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)

@Serializable
data class SyncHistoryItem(
    val id: String, // "$providerId:$movieSlug"
    val providerId: String,
    val movieSlug: String,
    val movieName: String,
    val thumbUrl: String? = null,
    val lastEpisodeId: String? = null,
    val lastEpisodeName: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class SyncProgressItem(
    val id: String, // "$providerId:$movieSlug:$episodeId"
    val providerId: String,
    val movieSlug: String,
    val episodeId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class SyncPluginItem(
    val id: String,
    val name: String,
    val version: String,
    val repoUrl: String? = null,
    val customDomain: String? = null,
    val isEnabled: Boolean = true
)

@Serializable
data class SyncRepositoryItem(
    val url: String,
    val name: String,
    val addedAt: Long = 0L
)

@Serializable
data class SyncStremioAddonItem(
    val url: String,
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val description: String? = null,
    val iconUrl: String? = null,
    val manifestJson: String,
    val isEnabled: Boolean = true,
    val orderIndex: Int = 0,
    val addedAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class JkhxSyncPayload(
    val version: Int = 1,
    val updatedAt: Long = System.currentTimeMillis(),
    val deviceName: String? = null,
    val repositories: List<SyncRepositoryItem> = emptyList(),
    val installedPlugins: List<SyncPluginItem> = emptyList(),
    val stremioAddons: List<SyncStremioAddonItem> = emptyList(),
    val pluginOrder: List<String> = emptyList(),
    val favorites: List<SyncFavoriteItem> = emptyList(),
    val history: List<SyncHistoryItem> = emptyList(),
    val progress: List<SyncProgressItem> = emptyList(),
    val deletedFavorites: Map<String, Long> = emptyMap(),
    val deletedHistory: Map<String, Long> = emptyMap(),
    val deletedPlugins: Map<String, Long> = emptyMap(),
    val deletedRepositories: Map<String, Long> = emptyMap(),
    val deletedStremioAddons: Map<String, Long> = emptyMap(),
    val historyClearedAt: Long = 0L,
    val favoritesClearedAt: Long = 0L
)

data class GoogleAccountInfo(
    val email: String,
    val displayName: String?,
    val photoUrl: String?
)

