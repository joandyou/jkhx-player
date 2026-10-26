package com.jkhx.player.core.database.dao

import androidx.room.*
import com.jkhx.player.core.database.entity.FavoriteEntity
import com.jkhx.player.core.database.entity.HistoryEntity
import com.jkhx.player.core.database.entity.PlaybackProgressEntity
import com.jkhx.player.core.database.entity.PluginEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY updatedAt DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE id = :id")
    fun getHistoryById(id: String): Flow<HistoryEntity?>

    @Query("SELECT * FROM watch_history ORDER BY updatedAt DESC")
    suspend fun getAllHistorySync(): List<HistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(history: HistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<HistoryEntity>)

    @Query("DELETE FROM watch_history WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM watch_history WHERE id != :currentId AND movieName = :movieName")
    suspend fun deleteDuplicatesByName(currentId: String, movieName: String)

    @Query("DELETE FROM watch_history WHERE id != :currentId AND (movieName = :movieName OR movieSlug = :movieSlug)")
    suspend fun deleteDuplicatesByNameOrSlug(currentId: String, movieName: String, movieSlug: String)

    @Query("DELETE FROM watch_history WHERE movieName = :movieName")
    suspend fun deleteByName(movieName: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearAll()
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    suspend fun getAllFavoritesSync(): List<FavoriteEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :id)")
    fun isFavorite(id: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM favorites")
    suspend fun clearAll()
}

@Dao
interface PlaybackProgressDao {
    @Query("SELECT * FROM playback_progress WHERE id = :id")
    suspend fun getProgress(id: String): PlaybackProgressEntity?

    @Query("SELECT * FROM playback_progress")
    suspend fun getAllProgressSync(): List<PlaybackProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: PlaybackProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgressAll(items: List<PlaybackProgressEntity>)

    @Query("DELETE FROM playback_progress WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM playback_progress")
    suspend fun clearAll()
}

@Dao
interface PluginDao {
    @Query("SELECT * FROM plugins")
    fun getAllPlugins(): Flow<List<PluginEntity>>

    @Query("SELECT * FROM plugins")
    suspend fun getAllPluginsSync(): List<PluginEntity>

    @Query("SELECT * FROM plugins WHERE id = :id")
    suspend fun getPlugin(id: String): PluginEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plugin: PluginEntity)

    @Query("UPDATE plugins SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("UPDATE plugins SET customDomain = :customDomain WHERE id = :id")
    suspend fun updateCustomDomain(id: String, customDomain: String?)

    @Query("DELETE FROM plugins WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM plugins WHERE repoUrl = :repoUrl")
    suspend fun getPluginsByRepoUrl(repoUrl: String): List<PluginEntity>

    @Query("DELETE FROM plugins WHERE repoUrl = :repoUrl")
    suspend fun deleteByRepoUrl(repoUrl: String)
}

@Dao
interface RepositoryDao {
    @Query("SELECT * FROM repositories ORDER BY addedAt DESC")
    fun getAllRepositories(): Flow<List<com.jkhx.player.core.database.entity.RepositoryEntity>>

    @Query("SELECT * FROM repositories ORDER BY addedAt DESC")
    suspend fun getAllRepositoriesSync(): List<com.jkhx.player.core.database.entity.RepositoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(repository: com.jkhx.player.core.database.entity.RepositoryEntity)

    @Query("DELETE FROM repositories WHERE url = :url")
    suspend fun delete(url: String)
}

@Dao
interface StremioAddonDao {
    @Query("SELECT * FROM stremio_addons ORDER BY orderIndex ASC, addedAt ASC")
    fun getAllAddons(): Flow<List<com.jkhx.player.core.database.entity.StremioAddonEntity>>

    @Query("SELECT * FROM stremio_addons ORDER BY orderIndex ASC, addedAt ASC")
    suspend fun getAllAddonsSync(): List<com.jkhx.player.core.database.entity.StremioAddonEntity>

    @Query("SELECT * FROM stremio_addons WHERE isEnabled = 1 ORDER BY orderIndex ASC, addedAt ASC")
    suspend fun getEnabledAddonsSync(): List<com.jkhx.player.core.database.entity.StremioAddonEntity>

    @Query("SELECT * FROM stremio_addons WHERE url = :url")
    suspend fun getAddonByUrl(url: String): com.jkhx.player.core.database.entity.StremioAddonEntity?

    @Query("SELECT * FROM stremio_addons WHERE id = :id LIMIT 1")
    suspend fun getAddonById(id: String): com.jkhx.player.core.database.entity.StremioAddonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(addon: com.jkhx.player.core.database.entity.StremioAddonEntity)

    @Query("UPDATE stremio_addons SET isEnabled = :enabled WHERE url = :url")
    suspend fun setEnabled(url: String, enabled: Boolean)

    @Query("DELETE FROM stremio_addons WHERE url = :url")
    suspend fun delete(url: String)

    @Query("DELETE FROM stremio_addons")
    suspend fun clearAll()
}


