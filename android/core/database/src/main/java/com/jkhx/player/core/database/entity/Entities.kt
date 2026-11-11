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
    val isComic: Boolean = false,
    val isNovel: Boolean = false,
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

@Entity(tableName = "stremio_addons")
data class StremioAddonEntity(
    @PrimaryKey val url: String,
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val description: String? = null,
    val iconUrl: String? = null,
    val manifestJson: String,
    val isEnabled: Boolean = true,
    val orderIndex: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "local_library",
    indices = [
        androidx.room.Index(value = ["type"]),
        androidx.room.Index(value = ["lastOpenedAt"])
    ]
)
data class LocalLibraryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val type: String, // "movie", "series", "novel", "comic"
    val uri: String,
    val subtitleUri: String? = null,
    val coverUrl: String? = null,
    val author: String? = null,
    val description: String? = null,
    val chapterCount: Int = 1,
    val episodesJson: String = "[]",
    val addedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = 0L
)

@Entity(
    tableName = "remote_servers",
    indices = [
        androidx.room.Index(value = ["protocol"]),
        androidx.room.Index(value = ["addedAt"])
    ]
)
data class RemoteServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val protocol: String, // "webdav", "smb", "sftp"
    val host: String,
    val port: Int,
    val useSsl: Boolean = false,
    val basePath: String = "/",
    val username: String? = null,
    val password: String? = null,
    val isAnonymous: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val lastConnectedAt: Long = 0L
)


