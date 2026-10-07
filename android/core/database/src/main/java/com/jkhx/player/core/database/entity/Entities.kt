package com.jkhx.player.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class HistoryEntity(
    @PrimaryKey val id: String, // "$providerId:$movieSlug"
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

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: String, // "$providerId:$movieSlug"
    val providerId: String,
    val movieSlug: String,
    val movieName: String,
    val thumbUrl: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playback_progress")
data class PlaybackProgressEntity(
    @PrimaryKey val id: String, // "$providerId:$movieSlug:$episodeId"
    val providerId: String,
    val movieSlug: String,
    val episodeId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "plugins")
data class PluginEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: String,
    val author: String? = null,
    val description: String? = null,
    val logo: String? = null,
    val filename: String? = null,
    val defaultDomain: String? = null,
    val customDomain: String? = null,
    val repoUrl: String? = null,
    val supportedTypes: String? = null,
    val isEnabled: Boolean = true,
    val isAdult: Boolean = false,
    val manifestJson: String
)

@Entity(tableName = "repositories")
data class RepositoryEntity(
    @PrimaryKey val url: String,
    val name: String,
    val version: String? = null,
    val manifestJson: String,
    val addedAt: Long = System.currentTimeMillis()
)

