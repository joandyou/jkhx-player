package com.jkhx.player.sync

import android.content.Context
import android.os.Build
import com.jkhx.player.JkhxApplication
import com.jkhx.player.core.database.entity.FavoriteEntity
import com.jkhx.player.core.database.entity.HistoryEntity
import com.jkhx.player.core.database.entity.PlaybackProgressEntity
import com.jkhx.player.core.database.entity.StremioAddonEntity
import com.jkhx.player.plugin.manager.PluginManager
import com.jkhx.player.settings.SettingsManager
import android.content.SharedPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    data class Success(val timestamp: Long) : SyncState()
    data class Error(val message: String) : SyncState()
}

class SyncManager private constructor(private val context: Context) {

    private val authManager = GoogleAuthManager.getInstance(context)
    private val settingsManager = SettingsManager.getInstance(context)
    private val driveClient = GoogleDriveSyncClient()

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    private val tombstonePrefs: SharedPreferences =
        context.getSharedPreferences("jkhx_sync_tombstones", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private fun getLocalDeletedHistory(): MutableMap<String, Long> {
        val raw = tombstonePrefs.getString("deleted_history_map", null) ?: return mutableMapOf()
        return try {
            json.decodeFromString<Map<String, Long>>(raw).toMutableMap()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun saveLocalDeletedHistory(map: Map<String, Long>) {
        try {
            tombstonePrefs.edit().putString("deleted_history_map", json.encodeToString(map)).apply()
        } catch (_: Exception) {}
    }

    private fun getLocalDeletedFavorites(): MutableMap<String, Long> {
        val raw = tombstonePrefs.getString("deleted_favorites_map", null) ?: return mutableMapOf()
        return try {
            json.decodeFromString<Map<String, Long>>(raw).toMutableMap()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun saveLocalDeletedFavorites(map: Map<String, Long>) {
        try {
            tombstonePrefs.edit().putString("deleted_favorites_map", json.encodeToString(map)).apply()
        } catch (_: Exception) {}
    }

    private fun getLocalHistoryClearedAt(): Long = tombstonePrefs.getLong("history_cleared_at", 0L)

    private fun saveLocalHistoryClearedAt(timestamp: Long) {
        tombstonePrefs.edit().putLong("history_cleared_at", timestamp).apply()
    }

    private fun getLocalFavoritesClearedAt(): Long = tombstonePrefs.getLong("favorites_cleared_at", 0L)

    private fun saveLocalFavoritesClearedAt(timestamp: Long) {
        tombstonePrefs.edit().putLong("favorites_cleared_at", timestamp).apply()
    }

    private fun getLocalDeletedPlugins(): MutableMap<String, Long> {
        val raw = tombstonePrefs.getString("deleted_plugins_map", null) ?: return mutableMapOf()
        return try {
            json.decodeFromString<Map<String, Long>>(raw).toMutableMap()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun saveLocalDeletedPlugins(map: Map<String, Long>) {
        try {
            tombstonePrefs.edit().putString("deleted_plugins_map", json.encodeToString(map)).apply()
        } catch (_: Exception) {}
    }

    private fun getLocalDeletedRepositories(): MutableMap<String, Long> {
        val raw = tombstonePrefs.getString("deleted_repositories_map", null) ?: return mutableMapOf()
        return try {
            json.decodeFromString<Map<String, Long>>(raw).toMutableMap()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun saveLocalDeletedRepositories(map: Map<String, Long>) {
        try {
            tombstonePrefs.edit().putString("deleted_repositories_map", json.encodeToString(map)).apply()
        } catch (_: Exception) {}
    }

    private fun getLocalDeletedStremioAddons(): MutableMap<String, Long> {
        val raw = tombstonePrefs.getString("deleted_stremio_addons_map", null) ?: return mutableMapOf()
        return try {
            json.decodeFromString<Map<String, Long>>(raw).toMutableMap()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun saveLocalDeletedStremioAddons(map: Map<String, Long>) {
        try {
            tombstonePrefs.edit().putString("deleted_stremio_addons_map", json.encodeToString(map)).apply()
        } catch (_: Exception) {}
    }

    private fun getLocalStremioToggleMap(): MutableMap<String, Long> {
        val raw = tombstonePrefs.getString("stremio_toggle_map", null) ?: return mutableMapOf()
        return try {
            json.decodeFromString<Map<String, Long>>(raw).toMutableMap()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun saveLocalStremioToggledAt(url: String, time: Long) {
        try {
            val normUrl = com.jkhx.player.core.network.stremio.StremioBridgeClient.normalizeManifestUrl(url)
            val map = getLocalStremioToggleMap()
            map[normUrl] = time
            if (url.trim() != normUrl) {
                map[url.trim()] = time
            }
            tombstonePrefs.edit().putString("stremio_toggle_map", json.encodeToString(map)).apply()
        } catch (_: Exception) {}
    }

    private fun removeLocalStremioToggledAt(url: String) {
        try {
            val normUrl = com.jkhx.player.core.network.stremio.StremioBridgeClient.normalizeManifestUrl(url)
            val map = getLocalStremioToggleMap()
            var modified = map.remove(normUrl) != null
            if (map.remove(url.trim()) != null) modified = true
            if (modified) {
                tombstonePrefs.edit().putString("stremio_toggle_map", json.encodeToString(map)).apply()
            }
        } catch (_: Exception) {}
    }

    private fun getLocalPluginInstalledMap(): MutableMap<String, Long> {
        val raw = tombstonePrefs.getString("installed_plugins_map", null) ?: return mutableMapOf()
        return try {
            json.decodeFromString<Map<String, Long>>(raw).toMutableMap()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun saveLocalPluginInstalledAt(pluginId: String, time: Long) {
        try {
            val map = getLocalPluginInstalledMap()
            map[pluginId] = time
            tombstonePrefs.edit().putString("installed_plugins_map", json.encodeToString(map)).apply()
        } catch (_: Exception) {}
    }

    private fun removeLocalPluginInstalledAt(pluginId: String) {
        try {
            val map = getLocalPluginInstalledMap()
            if (map.remove(pluginId) != null) {
                tombstonePrefs.edit().putString("installed_plugins_map", json.encodeToString(map)).apply()
            }
        } catch (_: Exception) {}
    }

    private val pendingLocalDeletedStremioAddons = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val pendingLocalDeletedRepos = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val pendingLocalDeletedPlugins = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var pendingSyncRequested: Boolean = false

    private fun mergeTombstoneMaps(
        localMap: Map<String, Long>,
        remoteMap: Map<String, Long>
    ): MutableMap<String, Long> {
        val result = localMap.toMutableMap()
        for ((key, remoteTs) in remoteMap) {
            val localTs = result[key]
            if (localTs == null || remoteTs > localTs) {
                result[key] = remoteTs
            }
        }
        return result
    }

    fun onPluginDeleted(id: String) {
        val now = System.currentTimeMillis()
        pendingLocalDeletedPlugins.add(id)
        val map = getLocalDeletedPlugins()
        map[id] = now
        saveLocalDeletedPlugins(map)
        removeLocalPluginInstalledAt(id)
        triggerSyncAfterLocalChange()
    }

    fun onPluginInstalled(id: String) {
        pendingLocalDeletedPlugins.remove(id)
        val map = getLocalDeletedPlugins()
        if (map.remove(id) != null) {
            saveLocalDeletedPlugins(map)
        }
        saveLocalPluginInstalledAt(id, System.currentTimeMillis())
        triggerSyncAfterLocalChange()
    }

    fun onRepositoryDeleted(url: String) {
        val cleanUrl = url.trim()
        val now = System.currentTimeMillis()
        pendingLocalDeletedRepos.add(cleanUrl)
        val map = getLocalDeletedRepositories()
        map[cleanUrl] = now
        if (url != cleanUrl) map[url] = now
        saveLocalDeletedRepositories(map)
        triggerSyncAfterLocalChange()
    }

    fun onRepositoryAdded(url: String) {
        val cleanUrl = url.trim()
        pendingLocalDeletedRepos.remove(cleanUrl)
        pendingLocalDeletedRepos.remove(url)
        val map = getLocalDeletedRepositories()
        val removedClean = map.remove(cleanUrl) != null
        val removedRaw = map.remove(url) != null
        if (removedClean || removedRaw) {
            saveLocalDeletedRepositories(map)
        }
        triggerSyncAfterLocalChange()
    }

    fun onStremioAddonDeleted(url: String) {
        val normalizedUrl = com.jkhx.player.core.network.stremio.StremioBridgeClient.normalizeManifestUrl(url)
        val now = System.currentTimeMillis()
        removeLocalStremioToggledAt(normalizedUrl)
        removeLocalStremioToggledAt(url.trim())
        pendingLocalDeletedStremioAddons.add(normalizedUrl)
        pendingLocalDeletedStremioAddons.add(url.trim())
        val map = getLocalDeletedStremioAddons()
        map[normalizedUrl] = now
        if (url.trim() != normalizedUrl) {
            map[url.trim()] = now
        }
        saveLocalDeletedStremioAddons(map)
        triggerSyncAfterLocalChange()
    }

    fun onStremioAddonInstalled(url: String) {
        val normalizedUrl = com.jkhx.player.core.network.stremio.StremioBridgeClient.normalizeManifestUrl(url)
        val now = System.currentTimeMillis()
        saveLocalStremioToggledAt(normalizedUrl, now)
        saveLocalStremioToggledAt(url.trim(), now)
        pendingLocalDeletedStremioAddons.remove(normalizedUrl)
        pendingLocalDeletedStremioAddons.remove(url.trim())
        val map = getLocalDeletedStremioAddons()
        val removedNorm = map.remove(normalizedUrl) != null
        val removedRaw = map.remove(url.trim()) != null
        if (removedNorm || removedRaw) {
            saveLocalDeletedStremioAddons(map)
        }
        triggerSyncAfterLocalChange()
    }

    fun onStremioAddonToggled(url: String, enabled: Boolean) {
        val now = System.currentTimeMillis()
        saveLocalStremioToggledAt(url, now)
        triggerSyncAfterLocalChange()
    }

    fun onHistoryDeleted(id: String) {
        val map = getLocalDeletedHistory()
        map[id] = System.currentTimeMillis()
        saveLocalDeletedHistory(map)
        triggerSyncAfterLocalChange()
    }

    fun onAllHistoryCleared() {
        val now = System.currentTimeMillis()
        saveLocalHistoryClearedAt(now)
        val map = getLocalDeletedHistory()
        map.clear()
        saveLocalDeletedHistory(map)
        triggerSyncAfterLocalChange()
    }

    fun onFavoriteDeleted(id: String) {
        val map = getLocalDeletedFavorites()
        map[id] = System.currentTimeMillis()
        saveLocalDeletedFavorites(map)
        triggerSyncAfterLocalChange()
    }

    fun onAllFavoritesCleared() {
        val now = System.currentTimeMillis()
        saveLocalFavoritesClearedAt(now)
        val map = getLocalDeletedFavorites()
        map.clear()
        saveLocalDeletedFavorites(map)
        triggerSyncAfterLocalChange()
    }

    fun triggerSyncAfterLocalChange() {
        if (!authManager.isSignedIn() || !settingsManager.isAutoSyncEnabled.value) return
        pendingSyncRequested = true
        syncScope.launch {
            try {
                syncNow(force = false)
            } catch (e: Exception) {
                println("[SyncManager] Sync after change error: ${e.message}")
            }
        }
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    @Volatile
    private var cachedDriveFileId: String? = null

    @Volatile
    private var lastAutoPullAttempt: Long = 0L

    private val deviceName: String = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

    fun triggerAutoPullSync() {
        if (!authManager.isSignedIn() || !settingsManager.isAutoSyncEnabled.value) return
        val now = System.currentTimeMillis()
        if (now - lastAutoPullAttempt < 15_000L) return
        lastAutoPullAttempt = now

        syncScope.launch {
            try {
                syncNow(force = false)
            } catch (e: Exception) {
                println("[SyncManager] Auto pull sync error: ${e.message}")
            }
        }
    }

    suspend fun syncNow(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!authManager.isSignedIn()) {
            _syncState.value = SyncState.Error("Chưa đăng nhập tài khoản Google")
            return@withContext false
        }

        if (!syncMutex.tryLock()) {
            println("[SyncManager] Sync already in progress, queuing follow-up sync")
            pendingSyncRequested = true
            return@withContext false
        }

        pendingSyncRequested = false
        _syncState.value = SyncState.Syncing

        try {
            var token = authManager.getAccessToken()
            if (token == null) {
                _syncState.value = SyncState.Error("Không thể lấy token xác thực Google")
                return@withContext false
            }

            // Retry with token refresh if DriveAuthException occurs
            var result = try {
                executeSync(token)
            } catch (authEx: DriveAuthException) {
                println("[SyncManager] Token expired, invalidating and retrying...")
                authManager.invalidateToken(token)
                val refreshedToken = authManager.getAccessToken()
                if (refreshedToken != null) {
                    executeSync(refreshedToken)
                } else {
                    throw authEx
                }
            }

            if (result) {
                val now = System.currentTimeMillis()
                settingsManager.setLastSyncTimestamp(now)
                _syncState.value = SyncState.Success(now)
            }
            result
        } catch (e: Exception) {
            println("[SyncManager] Sync failed: ${e.message}")
            _syncState.value = SyncState.Error(e.message ?: "Đồng bộ thất bại")
            false
        } finally {
            syncMutex.unlock()
            if (pendingSyncRequested) {
                pendingSyncRequested = false
                syncScope.launch {
                    try {
                        syncNow(force = false)
                    } catch (e: Exception) {
                        println("[SyncManager] Follow-up sync error: ${e.message}")
                    }
                }
            }
        }
    }

    private suspend fun executeSync(token: String): Boolean {
        val database = JkhxApplication.instance.database
        val pluginManager = JkhxApplication.instance.pluginManager

        val historyDao = database.historyDao()
        val favoriteDao = database.favoriteDao()
        val progressDao = database.playbackProgressDao()

        // 1. Locate file on Google Drive AppData
        val fileInfo = driveClient.findSyncFile(token)

        if (fileInfo == null) {
            // First time sync: push local data to Google Drive AppData
            println("[SyncManager] No remote backup found, creating new backup on Drive...")
            val localPayload = buildLocalPayload(database, pluginManager)
            val newFileId = driveClient.uploadNewSyncData(token, localPayload)
            if (newFileId != null) {
                cachedDriveFileId = newFileId
                return true
            }
            return false
        }

        cachedDriveFileId = fileInfo.id

        // 2. Download remote payload
        val remotePayload = driveClient.downloadSyncData(token, fileInfo.id) ?: run {
            println("[SyncManager] Failed to read remote payload, overwriting with local data")
            val localPayload = buildLocalPayload(database, pluginManager)
            return driveClient.updateSyncData(token, fileInfo.id, localPayload)
        }

        // 3. Bidirectional merge with Tombstone handling:
        val localDeletedHistory = getLocalDeletedHistory()
        val localDeletedFavs = getLocalDeletedFavorites()
        val localHistoryClearedAt = getLocalHistoryClearedAt()
        val localFavsClearedAt = getLocalFavoritesClearedAt()

        val effectiveHistoryClearedAt = maxOf(localHistoryClearedAt, remotePayload.historyClearedAt)
        val effectiveFavsClearedAt = maxOf(localFavsClearedAt, remotePayload.favoritesClearedAt)
        val effectiveDeletedHistory = mergeTombstoneMaps(localDeletedHistory, remotePayload.deletedHistory)
        val effectiveDeletedFavs = mergeTombstoneMaps(localDeletedFavs, remotePayload.deletedFavorites)

        val localDeletedPlugins = getLocalDeletedPlugins()
        val localDeletedRepos = getLocalDeletedRepositories()
        val localDeletedStremioAddons = getLocalDeletedStremioAddons()
        val localStremioToggleMap = getLocalStremioToggleMap()

        val effectiveDeletedPlugins = mergeTombstoneMaps(localDeletedPlugins, remotePayload.deletedPlugins)
        val effectiveDeletedRepos = mergeTombstoneMaps(localDeletedRepos, remotePayload.deletedRepositories)
        val effectiveDeletedStremioAddons = mergeTombstoneMaps(localDeletedStremioAddons, remotePayload.deletedStremioAddons)

        // A. Favorites
        val initialLocalFavorites = favoriteDao.getAllFavoritesSync()
        for (fav in initialLocalFavorites) {
            val deletedAt = effectiveDeletedFavs[fav.id]
            if (fav.addedAt <= effectiveFavsClearedAt || (deletedAt != null && deletedAt >= fav.addedAt)) {
                favoriteDao.delete(fav.id)
            } else if (deletedAt != null) {
                effectiveDeletedFavs.remove(fav.id)
            }
        }

        val currentLocalFavs = favoriteDao.getAllFavoritesSync().associateBy { it.id }
        val cloudFavMap = remotePayload.favorites.associateBy { it.id }
        val favsToInsertLocal = mutableListOf<FavoriteEntity>()
        val mergedFavorites = mutableListOf<SyncFavoriteItem>()
        val allFavIds = (currentLocalFavs.keys + cloudFavMap.keys).toSet()

        for (id in allFavIds) {
            val local = currentLocalFavs[id]
            val cloud = cloudFavMap[id]
            val deletedAt = effectiveDeletedFavs[id]

            if (local != null) {
                mergedFavorites.add(
                    SyncFavoriteItem(
                        id = local.id,
                        providerId = local.providerId,
                        movieSlug = local.movieSlug,
                        movieName = local.movieName,
                        thumbUrl = local.thumbUrl,
                        addedAt = local.addedAt
                    )
                )
            } else if (cloud != null) {
                val isDeleted = cloud.addedAt <= effectiveFavsClearedAt || (deletedAt != null && deletedAt >= cloud.addedAt)
                if (!isDeleted) {
                    favsToInsertLocal.add(
                        FavoriteEntity(
                            id = cloud.id,
                            providerId = cloud.providerId,
                            movieSlug = cloud.movieSlug,
                            movieName = cloud.movieName,
                            thumbUrl = cloud.thumbUrl,
                            addedAt = cloud.addedAt
                        )
                    )
                    mergedFavorites.add(cloud)
                }
            }
        }
        if (favsToInsertLocal.isNotEmpty()) {
            favoriteDao.insertAll(favsToInsertLocal)
        }

        // B. Watch History
        val initialLocalHistory = historyDao.getAllHistorySync()
        for (item in initialLocalHistory) {
            val deletedAt = effectiveDeletedHistory[item.id]
            if (item.updatedAt <= effectiveHistoryClearedAt || (deletedAt != null && deletedAt >= item.updatedAt)) {
                historyDao.delete(item.id)
            } else if (deletedAt != null) {
                effectiveDeletedHistory.remove(item.id)
            }
        }

        val currentLocalHistory = historyDao.getAllHistorySync().associateBy { it.id }
        val cloudHistoryMap = remotePayload.history.associateBy { it.id }
        val historyToUpsertLocal = mutableListOf<HistoryEntity>()
        val mergedHistory = mutableListOf<SyncHistoryItem>()
        val allHistoryIds = (currentLocalHistory.keys + cloudHistoryMap.keys).toSet()

        for (id in allHistoryIds) {
            val local = currentLocalHistory[id]
            val cloud = cloudHistoryMap[id]
            val deletedAt = effectiveDeletedHistory[id]

            when {
                local != null && cloud != null -> {
                    if (cloud.updatedAt > local.updatedAt) {
                        historyToUpsertLocal.add(
                            HistoryEntity(
                                id = cloud.id,
                                providerId = cloud.providerId,
                                movieSlug = cloud.movieSlug,
                                movieName = cloud.movieName,
                                thumbUrl = cloud.thumbUrl,
                                lastEpisodeId = cloud.lastEpisodeId,
                                lastEpisodeName = cloud.lastEpisodeName,
                                positionMs = cloud.positionMs,
                                durationMs = cloud.durationMs,
                                updatedAt = cloud.updatedAt
                            )
                        )
                        mergedHistory.add(cloud)
                    } else {
                        mergedHistory.add(
                            SyncHistoryItem(
                                id = local.id,
                                providerId = local.providerId,
                                movieSlug = local.movieSlug,
                                movieName = local.movieName,
                                thumbUrl = local.thumbUrl,
                                lastEpisodeId = local.lastEpisodeId,
                                lastEpisodeName = local.lastEpisodeName,
                                positionMs = local.positionMs,
                                durationMs = local.durationMs,
                                updatedAt = local.updatedAt
                            )
                        )
                    }
                }
                local != null -> {
                    mergedHistory.add(
                        SyncHistoryItem(
                            id = local.id,
                            providerId = local.providerId,
                            movieSlug = local.movieSlug,
                            movieName = local.movieName,
                            thumbUrl = local.thumbUrl,
                            lastEpisodeId = local.lastEpisodeId,
                            lastEpisodeName = local.lastEpisodeName,
                            positionMs = local.positionMs,
                            durationMs = local.durationMs,
                            updatedAt = local.updatedAt
                        )
                    )
                }
                cloud != null -> {
                    val isDeleted = cloud.updatedAt <= effectiveHistoryClearedAt || (deletedAt != null && deletedAt >= cloud.updatedAt)
                    if (!isDeleted) {
                        historyToUpsertLocal.add(
                            HistoryEntity(
                                id = cloud.id,
                                providerId = cloud.providerId,
                                movieSlug = cloud.movieSlug,
                                movieName = cloud.movieName,
                                thumbUrl = cloud.thumbUrl,
                                lastEpisodeId = cloud.lastEpisodeId,
                                lastEpisodeName = cloud.lastEpisodeName,
                                positionMs = cloud.positionMs,
                                durationMs = cloud.durationMs,
                                updatedAt = cloud.updatedAt
                            )
                        )
                        mergedHistory.add(cloud)
                    }
                }
            }
        }
        if (historyToUpsertLocal.isNotEmpty()) {
            historyDao.upsertAll(historyToUpsertLocal)
        }

        // C. Playback Progress
        val initialLocalProgress = progressDao.getAllProgressSync()
        for (prog in initialLocalProgress) {
            val histId = "${prog.providerId}:${prog.movieSlug}"
            val deletedAt = effectiveDeletedHistory[histId]
            if (prog.updatedAt <= effectiveHistoryClearedAt || (deletedAt != null && deletedAt >= prog.updatedAt)) {
                progressDao.delete(prog.id)
            }
        }

        val currentLocalProgress = progressDao.getAllProgressSync().associateBy { it.id }
        val cloudProgressMap = remotePayload.progress.associateBy { it.id }
        val progressToUpsertLocal = mutableListOf<PlaybackProgressEntity>()
        val mergedProgress = mutableListOf<SyncProgressItem>()
        val allProgressIds = (currentLocalProgress.keys + cloudProgressMap.keys).toSet()

        for (id in allProgressIds) {
            val local = currentLocalProgress[id]
            val cloud = cloudProgressMap[id]
            val histId = if (cloud != null) "${cloud.providerId}:${cloud.movieSlug}" else local?.let { "${it.providerId}:${it.movieSlug}" }
            val deletedAt = if (histId != null) effectiveDeletedHistory[histId] else null

            when {
                local != null && cloud != null -> {
                    if (cloud.updatedAt > local.updatedAt) {
                        progressToUpsertLocal.add(
                            PlaybackProgressEntity(
                                id = cloud.id,
                                providerId = cloud.providerId,
                                movieSlug = cloud.movieSlug,
                                episodeId = cloud.episodeId,
                                positionMs = cloud.positionMs,
                                durationMs = cloud.durationMs,
                                updatedAt = cloud.updatedAt
                            )
                        )
                        mergedProgress.add(cloud)
                    } else {
                        mergedProgress.add(
                            SyncProgressItem(
                                id = local.id,
                                providerId = local.providerId,
                                movieSlug = local.movieSlug,
                                episodeId = local.episodeId,
                                positionMs = local.positionMs,
                                durationMs = local.durationMs,
                                updatedAt = local.updatedAt
                            )
                        )
                    }
                }
                local != null -> {
                    mergedProgress.add(
                        SyncProgressItem(
                            id = local.id,
                            providerId = local.providerId,
                            movieSlug = local.movieSlug,
                            episodeId = local.episodeId,
                            positionMs = local.positionMs,
                            durationMs = local.durationMs,
                            updatedAt = local.updatedAt
                        )
                    )
                }
                cloud != null -> {
                    val isDeleted = cloud.updatedAt <= effectiveHistoryClearedAt || (deletedAt != null && deletedAt >= cloud.updatedAt)
                    if (!isDeleted) {
                        progressToUpsertLocal.add(
                            PlaybackProgressEntity(
                                id = cloud.id,
                                providerId = cloud.providerId,
                                movieSlug = cloud.movieSlug,
                                episodeId = cloud.episodeId,
                                positionMs = cloud.positionMs,
                                durationMs = cloud.durationMs,
                                updatedAt = cloud.updatedAt
                            )
                        )
                        mergedProgress.add(cloud)
                    }
                }
            }
        }
        if (progressToUpsertLocal.isNotEmpty()) {
            progressDao.saveProgressAll(progressToUpsertLocal)
        }

        saveLocalHistoryClearedAt(effectiveHistoryClearedAt)
        saveLocalFavoritesClearedAt(effectiveFavsClearedAt)
        saveLocalDeletedHistory(effectiveDeletedHistory)
        saveLocalDeletedFavorites(effectiveDeletedFavs)

        // D. Repositories (Bidirectional merge with tombstone)
        val initialLocalRepos = pluginManager.getRepositories()
        for (repo in initialLocalRepos) {
            val deletedAt = effectiveDeletedRepos[repo.url]
            if (deletedAt != null && deletedAt >= repo.addedAt && repo.url !in pendingLocalDeletedRepos) {
                try {
                    pluginManager.removeRepository(repo.url)
                } catch (e: Exception) {
                    println("[SyncManager] Error removing deleted repo ${repo.url}: ${e.message}")
                }
            } else if (deletedAt != null && repo.addedAt > deletedAt) {
                effectiveDeletedRepos.remove(repo.url)
            }
        }

        val currentLocalRepos = pluginManager.getRepositories()
        val currentLocalRepoUrls = currentLocalRepos.map { it.url }.toSet()
        val mergedRepos = currentLocalRepos.map {
            SyncRepositoryItem(url = it.url, name = it.name, addedAt = it.addedAt)
        }.toMutableList()

        for (cloudRepo in remotePayload.repositories) {
            var deletedAt = effectiveDeletedRepos[cloudRepo.url]
            val isPendingLocalDelete = cloudRepo.url in pendingLocalDeletedRepos ||
                (cloudRepo.url in localDeletedRepos && cloudRepo.url !in currentLocalRepoUrls)
            if (isPendingLocalDelete && (deletedAt == null || deletedAt < cloudRepo.addedAt)) {
                val bumpedTs = maxOf(System.currentTimeMillis(), cloudRepo.addedAt + 1L)
                effectiveDeletedRepos[cloudRepo.url] = bumpedTs
                deletedAt = bumpedTs
            }
            val isDeleted = deletedAt != null && (deletedAt >= cloudRepo.addedAt || isPendingLocalDelete)
            if (!isDeleted && cloudRepo.url !in currentLocalRepoUrls) {
                try {
                    pluginManager.addRepository(cloudRepo.url, addedAt = cloudRepo.addedAt)
                    mergedRepos.add(cloudRepo)
                } catch (e: Exception) {
                    println("[SyncManager] Failed to add remote repository ${cloudRepo.url}: ${e.message}")
                }
            }
            if (!isDeleted && deletedAt != null && cloudRepo.addedAt > deletedAt) {
                effectiveDeletedRepos.remove(cloudRepo.url)
            }
        }
        pendingLocalDeletedRepos.clear()

        // E. Installed Plugins (Bidirectional merge with tombstone)
        val localInstalledMap = getLocalPluginInstalledMap()
        val initialLocalPlugins = pluginManager.getInstalledRecords().filter { !it.manifest.isStremioAddon }
        for (p in initialLocalPlugins) {
            val deletedAt = effectiveDeletedPlugins[p.manifest.id]
            val installedAt = localInstalledMap[p.manifest.id] ?: 0L
            if (deletedAt != null && deletedAt >= installedAt && p.manifest.id !in pendingLocalDeletedPlugins) {
                try {
                    pluginManager.uninstallPlugin(p.manifest.id)
                } catch (e: Exception) {
                    println("[SyncManager] Error uninstalling deleted plugin ${p.manifest.id}: ${e.message}")
                }
            } else if (deletedAt != null && installedAt > deletedAt) {
                effectiveDeletedPlugins.remove(p.manifest.id)
            }
        }

        val currentLocalPlugins = pluginManager.getInstalledRecords().filter { !it.manifest.isStremioAddon }
        val currentLocalPluginIds = currentLocalPlugins.map { it.manifest.id }.toSet()
        val mergedPlugins = currentLocalPlugins.map { record ->
            SyncPluginItem(
                id = record.manifest.id,
                name = record.manifest.name,
                version = record.manifest.version,
                repoUrl = record.manifest.repoUrl,
                customDomain = record.customDomain,
                isEnabled = record.isEnabled
            )
        }.toMutableList()

        for (cloudPlugin in remotePayload.installedPlugins) {
            val deletedAt = effectiveDeletedPlugins[cloudPlugin.id]
            val isDeleted = deletedAt != null || cloudPlugin.id in pendingLocalDeletedPlugins
            if (!isDeleted && cloudPlugin.id !in currentLocalPluginIds) {
                val repoUrl = cloudPlugin.repoUrl
                if (!repoUrl.isNullOrBlank()) {
                    try {
                        val repoManifest = pluginManager.fetchRepository(repoUrl)
                        val scraper = repoManifest.allScrapers.firstOrNull { it.id == cloudPlugin.id }
                        if (scraper != null) {
                            pluginManager.installScraper(repoUrl, scraper)
                            if (!cloudPlugin.customDomain.isNullOrBlank()) {
                                pluginManager.updateCustomDomain(cloudPlugin.id, cloudPlugin.customDomain)
                            }
                            if (!cloudPlugin.isEnabled) {
                                pluginManager.disablePlugin(cloudPlugin.id)
                            }
                            saveLocalPluginInstalledAt(cloudPlugin.id, System.currentTimeMillis())
                            mergedPlugins.add(cloudPlugin)
                        }
                    } catch (e: Exception) {
                        println("[SyncManager] Failed to auto-install plugin ${cloudPlugin.id}: ${e.message}")
                    }
                }
            } else if (!isDeleted && cloudPlugin.id in currentLocalPluginIds) {
                // If already installed locally, sync customDomain if cloud has one
                if (!cloudPlugin.customDomain.isNullOrBlank()) {
                    val localRecord = currentLocalPlugins.firstOrNull { it.manifest.id == cloudPlugin.id }
                    if (localRecord != null && localRecord.customDomain != cloudPlugin.customDomain) {
                        pluginManager.updateCustomDomain(cloudPlugin.id, cloudPlugin.customDomain)
                    }
                }
            }
        }
        pendingLocalDeletedPlugins.clear()

        // F. Stremio Addons (Bidirectional merge with tombstone)
        val stremioAddonDao = database.stremioAddonDao()
        val initialLocalAddons = stremioAddonDao.getAllAddonsSync()
        for (addon in initialLocalAddons) {
            val normUrl = com.jkhx.player.core.network.stremio.StremioBridgeClient.normalizeManifestUrl(addon.url)
            val deletedAt = effectiveDeletedStremioAddons[normUrl] ?: effectiveDeletedStremioAddons[addon.url]
            if (deletedAt != null && deletedAt >= addon.addedAt) {
                try {
                    pluginManager.removeStremioAddon(addon.url)
                } catch (e: Exception) {
                    println("[SyncManager] Failed to remove deleted stremio addon ${addon.url}: ${e.message}")
                }
            } else if (deletedAt != null && addon.addedAt > deletedAt) {
                effectiveDeletedStremioAddons.remove(normUrl)
                effectiveDeletedStremioAddons.remove(addon.url)
            }
        }

        val currentLocalAddons = stremioAddonDao.getAllAddonsSync().associateBy {
            com.jkhx.player.core.network.stremio.StremioBridgeClient.normalizeManifestUrl(it.url)
        }
        for (cloudAddon in remotePayload.stremioAddons) {
            val normCloudUrl = com.jkhx.player.core.network.stremio.StremioBridgeClient.normalizeManifestUrl(cloudAddon.url)
            var deletedAt = maxOf(
                effectiveDeletedStremioAddons[normCloudUrl] ?: 0L,
                effectiveDeletedStremioAddons[cloudAddon.url] ?: 0L
            ).takeIf { it > 0L }

            val wasDeletedLocally = normCloudUrl in pendingLocalDeletedStremioAddons ||
                cloudAddon.url in pendingLocalDeletedStremioAddons ||
                ((normCloudUrl in localDeletedStremioAddons || cloudAddon.url in localDeletedStremioAddons) && normCloudUrl !in currentLocalAddons)

            if (wasDeletedLocally && (deletedAt == null || deletedAt < cloudAddon.addedAt)) {
                val bumpedTs = maxOf(System.currentTimeMillis(), cloudAddon.addedAt + 1L)
                effectiveDeletedStremioAddons[normCloudUrl] = bumpedTs
                if (cloudAddon.url != normCloudUrl) {
                    effectiveDeletedStremioAddons[cloudAddon.url] = bumpedTs
                }
                deletedAt = bumpedTs
            }

            val isDeleted = deletedAt != null && (deletedAt >= cloudAddon.addedAt || wasDeletedLocally)
            if (!isDeleted && normCloudUrl !in currentLocalAddons) {
                try {
                    val effectiveAddedAt = if (cloudAddon.addedAt > 0L) cloudAddon.addedAt else System.currentTimeMillis()
                    pluginManager.installStremioAddon(normCloudUrl, addedAt = effectiveAddedAt)
                    if (!cloudAddon.isEnabled) {
                        pluginManager.toggleStremioAddon(normCloudUrl, false)
                    }
                    val effectiveUpdatedAt = maxOf(effectiveAddedAt, cloudAddon.updatedAt)
                    saveLocalStremioToggledAt(normCloudUrl, effectiveUpdatedAt)
                } catch (e: Exception) {
                    println("[SyncManager] Network install failed for stremio addon ${cloudAddon.url}, fallback to DB: ${e.message}")
                    try {
                        val entity = StremioAddonEntity(
                            url = normCloudUrl,
                            id = cloudAddon.id,
                            name = cloudAddon.name,
                            version = cloudAddon.version,
                            description = cloudAddon.description,
                            iconUrl = cloudAddon.iconUrl,
                            manifestJson = cloudAddon.manifestJson,
                            isEnabled = cloudAddon.isEnabled,
                            orderIndex = cloudAddon.orderIndex,
                            addedAt = if (cloudAddon.addedAt > 0L) cloudAddon.addedAt else System.currentTimeMillis()
                        )
                        stremioAddonDao.insert(entity)
                        if (cloudAddon.isEnabled) {
                            pluginManager.toggleStremioAddon(normCloudUrl, true)
                        }
                        val effectiveUpdatedAt = maxOf(entity.addedAt, cloudAddon.updatedAt)
                        saveLocalStremioToggledAt(normCloudUrl, effectiveUpdatedAt)
                    } catch (inner: Exception) {
                        println("[SyncManager] Stremio fallback insert also failed: ${inner.message}")
                    }
                }
            } else if (!isDeleted && normCloudUrl in currentLocalAddons) {
                val localAddon = currentLocalAddons[normCloudUrl]
                if (localAddon != null) {
                    val localToggleTs = localStremioToggleMap[normCloudUrl] ?: localStremioToggleMap[localAddon.url] ?: localAddon.addedAt
                    val localUpdatedAt = maxOf(localAddon.addedAt, localToggleTs)
                    val cloudUpdatedAt = maxOf(cloudAddon.addedAt, cloudAddon.updatedAt)

                    if (cloudUpdatedAt > localUpdatedAt) {
                        if (localAddon.isEnabled != cloudAddon.isEnabled) {
                            pluginManager.toggleStremioAddon(normCloudUrl, cloudAddon.isEnabled)
                        }
                        saveLocalStremioToggledAt(normCloudUrl, cloudUpdatedAt)
                    }
                }
            }
            if (!isDeleted && deletedAt != null && cloudAddon.addedAt > deletedAt) {
                effectiveDeletedStremioAddons.remove(normCloudUrl)
                effectiveDeletedStremioAddons.remove(cloudAddon.url)
            }
        }
        pendingLocalDeletedStremioAddons.clear()

        val latestStremioToggleMap = getLocalStremioToggleMap()
        val mergedStremioAddons = stremioAddonDao.getAllAddonsSync().map {
            val normUrl = com.jkhx.player.core.network.stremio.StremioBridgeClient.normalizeManifestUrl(it.url)
            val toggleTs = latestStremioToggleMap[normUrl] ?: latestStremioToggleMap[it.url] ?: it.addedAt
            SyncStremioAddonItem(
                url = it.url,
                id = it.id,
                name = it.name,
                version = it.version,
                description = it.description,
                iconUrl = it.iconUrl,
                manifestJson = it.manifestJson,
                isEnabled = it.isEnabled,
                orderIndex = it.orderIndex,
                addedAt = it.addedAt,
                updatedAt = maxOf(it.addedAt, toggleTs)
            )
        }

        // G. Plugin Order
        val localOrder = pluginManager.getPluginOrder()
        val mergedOrder = if (localOrder.isNotEmpty()) localOrder else remotePayload.pluginOrder
        if (localOrder.isEmpty() && remotePayload.pluginOrder.isNotEmpty()) {
            pluginManager.applyPluginOrder(remotePayload.pluginOrder)
        }

        saveLocalHistoryClearedAt(effectiveHistoryClearedAt)
        saveLocalFavoritesClearedAt(effectiveFavsClearedAt)
        saveLocalDeletedHistory(effectiveDeletedHistory)
        saveLocalDeletedFavorites(effectiveDeletedFavs)
        saveLocalDeletedPlugins(effectiveDeletedPlugins)
        saveLocalDeletedRepositories(effectiveDeletedRepos)
        saveLocalDeletedStremioAddons(effectiveDeletedStremioAddons)

        // 4. Update merged payload to Google Drive
        val finalPayload = JkhxSyncPayload(
            version = 1,
            updatedAt = System.currentTimeMillis(),
            deviceName = deviceName,
            repositories = mergedRepos,
            installedPlugins = mergedPlugins,
            stremioAddons = mergedStremioAddons,
            pluginOrder = mergedOrder,
            favorites = mergedFavorites,
            history = mergedHistory,
            progress = mergedProgress,
            deletedFavorites = effectiveDeletedFavs,
            deletedHistory = effectiveDeletedHistory,
            deletedPlugins = effectiveDeletedPlugins,
            deletedRepositories = effectiveDeletedRepos,
            deletedStremioAddons = effectiveDeletedStremioAddons,
            historyClearedAt = effectiveHistoryClearedAt,
            favoritesClearedAt = effectiveFavsClearedAt
        )

        return driveClient.updateSyncData(token, fileInfo.id, finalPayload)
    }

    private suspend fun buildLocalPayload(
        database: com.jkhx.player.core.database.AppDatabase,
        pluginManager: PluginManager
    ): JkhxSyncPayload {
        val favs = database.favoriteDao().getAllFavoritesSync().map {
            SyncFavoriteItem(
                id = it.id,
                providerId = it.providerId,
                movieSlug = it.movieSlug,
                movieName = it.movieName,
                thumbUrl = it.thumbUrl,
                addedAt = it.addedAt
            )
        }

        val history = database.historyDao().getAllHistorySync().map {
            SyncHistoryItem(
                id = it.id,
                providerId = it.providerId,
                movieSlug = it.movieSlug,
                movieName = it.movieName,
                thumbUrl = it.thumbUrl,
                lastEpisodeId = it.lastEpisodeId,
                lastEpisodeName = it.lastEpisodeName,
                positionMs = it.positionMs,
                durationMs = it.durationMs,
                updatedAt = it.updatedAt
            )
        }

        val progress = database.playbackProgressDao().getAllProgressSync().map {
            SyncProgressItem(
                id = it.id,
                providerId = it.providerId,
                movieSlug = it.movieSlug,
                episodeId = it.episodeId,
                positionMs = it.positionMs,
                durationMs = it.durationMs,
                updatedAt = it.updatedAt
            )
        }

        val repos = pluginManager.getRepositories().map {
            SyncRepositoryItem(url = it.url, name = it.name, addedAt = it.addedAt)
        }

        val plugins = pluginManager.getInstalledRecords().filter { !it.manifest.isStremioAddon }.map {
            SyncPluginItem(
                id = it.manifest.id,
                name = it.manifest.name,
                version = it.manifest.version,
                repoUrl = it.manifest.repoUrl,
                customDomain = it.customDomain,
                isEnabled = it.isEnabled
            )
        }

        val stremioToggleMap = getLocalStremioToggleMap()
        val stremioAddons = database.stremioAddonDao().getAllAddonsSync().map {
            val normUrl = com.jkhx.player.core.network.stremio.StremioBridgeClient.normalizeManifestUrl(it.url)
            val toggleTs = stremioToggleMap[normUrl] ?: stremioToggleMap[it.url] ?: it.addedAt
            SyncStremioAddonItem(
                url = it.url,
                id = it.id,
                name = it.name,
                version = it.version,
                description = it.description,
                iconUrl = it.iconUrl,
                manifestJson = it.manifestJson,
                isEnabled = it.isEnabled,
                orderIndex = it.orderIndex,
                addedAt = it.addedAt,
                updatedAt = maxOf(it.addedAt, toggleTs)
            )
        }

        return JkhxSyncPayload(
            version = 1,
            updatedAt = System.currentTimeMillis(),
            deviceName = deviceName,
            repositories = repos,
            installedPlugins = plugins,
            stremioAddons = stremioAddons,
            pluginOrder = pluginManager.getPluginOrder(),
            favorites = favs,
            history = history,
            progress = progress,
            deletedFavorites = getLocalDeletedFavorites(),
            deletedHistory = getLocalDeletedHistory(),
            deletedPlugins = getLocalDeletedPlugins(),
            deletedRepositories = getLocalDeletedRepositories(),
            deletedStremioAddons = getLocalDeletedStremioAddons(),
            historyClearedAt = getLocalHistoryClearedAt(),
            favoritesClearedAt = getLocalFavoritesClearedAt()
        )
    }

    fun pushQuickProgressSync(
        providerId: String,
        movieSlug: String,
        episodeId: String,
        positionMs: Long,
        durationMs: Long,
        movieName: String,
        thumbUrl: String?,
        episodeName: String?
    ) {
        if (!authManager.isSignedIn() || !settingsManager.isAutoSyncEnabled.value) return
        if (positionMs <= 0 || durationMs <= 0) return

        syncScope.launch {
            try {
                val token = authManager.getAccessToken() ?: return@launch
                val fileId = cachedDriveFileId ?: driveClient.findSyncFile(token)?.id

                if (fileId == null) {
                    // Remote file doesn't exist yet, run a full sync
                    syncNow()
                    return@launch
                }

                // Download existing payload to update just this progress/history item
                val payload = driveClient.downloadSyncData(token, fileId) ?: return@launch
                val now = System.currentTimeMillis()

                val progressKey = "$providerId:$movieSlug:$episodeId"
                val updatedProgress = payload.progress.filter { it.id != progressKey } + SyncProgressItem(
                    id = progressKey,
                    providerId = providerId,
                    movieSlug = movieSlug,
                    episodeId = episodeId,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    updatedAt = now
                )

                val historyKey = "$providerId:$movieSlug"
                val updatedHistory = payload.history.filter { it.id != historyKey } + SyncHistoryItem(
                    id = historyKey,
                    providerId = providerId,
                    movieSlug = movieSlug,
                    movieName = movieName,
                    thumbUrl = thumbUrl,
                    lastEpisodeId = episodeId,
                    lastEpisodeName = episodeName,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    updatedAt = now
                )

                val localDeletedHistory = getLocalDeletedHistory()
                if (localDeletedHistory.remove(historyKey) != null) {
                    saveLocalDeletedHistory(localDeletedHistory)
                }

                val updatedDeletedHistory = payload.deletedHistory.toMutableMap().apply {
                    remove(historyKey)
                }

                val newPayload = payload.copy(
                    updatedAt = now,
                    deviceName = deviceName,
                    progress = updatedProgress,
                    history = updatedHistory,
                    deletedHistory = updatedDeletedHistory
                )

                driveClient.updateSyncData(token, fileId, newPayload)
                settingsManager.setLastSyncTimestamp(now)
            } catch (e: Exception) {
                println("[SyncManager] Quick progress sync error: ${e.message}")
            }
        }
    }

    companion object {
        @Volatile
        private var instance: SyncManager? = null

        fun getInstance(context: Context): SyncManager {
            return instance ?: synchronized(this) {
                instance ?: SyncManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

