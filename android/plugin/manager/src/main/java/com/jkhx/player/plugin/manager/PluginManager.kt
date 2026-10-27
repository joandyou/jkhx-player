package com.jkhx.player.plugin.manager

import com.jkhx.player.core.database.dao.PluginDao
import com.jkhx.player.core.database.dao.RepositoryDao
import com.jkhx.player.core.database.dao.StremioAddonDao
import com.jkhx.player.core.database.entity.PluginEntity
import com.jkhx.player.core.database.entity.RepositoryEntity
import com.jkhx.player.core.database.entity.StremioAddonEntity
import com.jkhx.player.core.model.*
import com.jkhx.player.core.model.stremio.StremioManifest
import com.jkhx.player.core.network.stremio.StremioBridgeClient
import com.jkhx.player.plugin.api.LoadedPlugin
import com.jkhx.player.plugin.api.PluginRuntime
import com.jkhx.player.plugin.manager.stremio.StremioPluginWrapper
import com.jkhx.player.plugin.runtime.QuickJsRuntime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class InstalledPluginRecord(
    var manifest: PluginManifest,
    val scriptFile: File,
    var isEnabled: Boolean = true,
    var customDomain: String? = null
)

class PluginManager(
    private val pluginsDir: File,
    private val pluginDao: PluginDao? = null,
    private val repositoryDao: RepositoryDao? = null,
    private val stremioAddonDao: StremioAddonDao? = null,
    private val runtime: PluginRuntime = QuickJsRuntime(),
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder().build(),
    private val stremioClient: StremioBridgeClient = StremioBridgeClient()
) : AutoCloseable {

    private val mutex = Mutex()
    private val installedPlugins = ConcurrentHashMap<String, InstalledPluginRecord>()
    private val loadedPlugins = ConcurrentHashMap<String, LoadedPlugin>()
    private val knownAdultPluginIds = ConcurrentHashMap.newKeySet<String>().apply {
        addAll(listOf("missav", "vlxx", "vsnguon", "xsub", "huangguoai", "st1", "st1work", "vnst1", "vnst1com"))
    }
    private val knownComicPluginIds = ConcurrentHashMap.newKeySet<String>().apply {
        addAll(listOf("nettruyen", "truyenqq"))
    }
    private val knownNovelPluginIds = ConcurrentHashMap.newKeySet<String>().apply {
        addAll(listOf("metruyenchu"))
    }
    private val knownLivePluginIds = ConcurrentHashMap.newKeySet<String>().apply {
        addAll(listOf("socolive", "colatv", "xoilacwf"))
    }

    private val pluginOrderList = mutableListOf<String>()
    private val orderFile get() = File(pluginsDir, "plugins_order.json")

    private fun loadPluginOrder() {
        if (orderFile.exists()) {
            try {
                val list = json.decodeFromString<List<String>>(orderFile.readText())
                pluginOrderList.clear()
                pluginOrderList.addAll(list)
            } catch (_: Exception) {}
        }
    }

    private fun savePluginOrder() {
        try {
            val jsonArray = "[" + pluginOrderList.joinToString(",") { "\"$it\"" } + "]"
            orderFile.writeText(jsonArray)
        } catch (_: Exception) {}
    }

    private val _pluginsUpdateCounter = kotlinx.coroutines.flow.MutableStateFlow(0)
    val pluginsUpdateCounter: kotlinx.coroutines.flow.StateFlow<Int> = _pluginsUpdateCounter

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    init {
        if (!pluginsDir.exists()) pluginsDir.mkdirs()
    }

    suspend fun loadInstalledPlugins() = withContext(Dispatchers.IO) {
        mutex.withLock {
            installedPlugins.clear()
            loadedPlugins.clear()

            // 1. Read only from Database
            val dbPlugins = pluginDao?.getAllPluginsSync() ?: emptyList()
            val dbPluginIds = dbPlugins.map { it.id }.toSet()

            // Clean up any zombie directories on disk that are NOT in DB
            val subdirs = pluginsDir.listFiles { f -> f.isDirectory } ?: emptyArray()
            for (dir in subdirs) {
                if (dir.name !in dbPluginIds) {
                    dir.deleteRecursively()
                }
            }

            for (entity in dbPlugins) {
                if (entity.isAdult) knownAdultPluginIds.add(entity.id)
                if (entity.isComic) knownComicPluginIds.add(entity.id)
                if (entity.isNovel) knownNovelPluginIds.add(entity.id)
                try {
                    val m = json.decodeFromString<PluginManifest>(entity.manifestJson)
                    if (m.isAdult || m.categoryType == "adult") knownAdultPluginIds.add(entity.id)
                    if (m.isComic || m.categoryType == "comic") knownComicPluginIds.add(entity.id)
                    if (m.isNovel || m.categoryType == "novel") knownNovelPluginIds.add(entity.id)
                    if (m.isLive || m.categoryType == "live") knownLivePluginIds.add(entity.id)
                } catch (_: Exception) {}

                val dir = File(pluginsDir, entity.id)
                val scriptFile = File(dir, "plugin.js")
                if (scriptFile.exists()) {
                    try {
                        val manifest = json.decodeFromString<PluginManifest>(entity.manifestJson)
                            .copy(customDomain = entity.customDomain)
                        if (manifest.isLive || manifest.categoryType == "live") {
                            knownLivePluginIds.add(entity.id)
                        }
                        val record = InstalledPluginRecord(
                            manifest = manifest,
                            scriptFile = scriptFile,
                            isEnabled = entity.isEnabled,
                            customDomain = entity.customDomain
                        )
                        installedPlugins[entity.id] = record

                        if (entity.isEnabled) {
                            val loaded = runtime.loadPlugin(manifest, scriptFile.readText())
                            loadedPlugins[entity.id] = loaded
                        }
                    } catch (e: Exception) {
                        println("[PluginManager] Error loading plugin ${entity.id}: ${e.message}")
                    }
                }
            }

            // 2. Tải các Stremio Addon từ Database
            val dbStremioAddons = stremioAddonDao?.getAllAddonsSync() ?: emptyList()
            for (addonEntity in dbStremioAddons) {
                try {
                    var sManifest = json.decodeFromString<StremioManifest>(addonEntity.manifestJson)
                    // Auto-heal nếu manifest cũ trong Room DB bị mất options do serializer
                    val hasMissingOptions = sManifest.catalogs.any { cat ->
                        cat.extra.any { it.name.equals("genre", true) && it.options.isEmpty() }
                    }
                    if (hasMissingOptions) {
                        try {
                            val (freshManifest, rawJson) = stremioClient.fetchManifestWithRaw(addonEntity.url)
                            sManifest = freshManifest
                            stremioAddonDao?.insert(addonEntity.copy(manifestJson = rawJson))
                        } catch (_: Exception) {}
                    }
                    val pluginId = StremioPluginWrapper.makePluginId(sManifest.id)
                    val virtualScriptFile = File(pluginsDir, "$pluginId/manifest.json")
                    val pluginManifest = PluginManifest(
                        id = pluginId,
                        name = sManifest.name,
                        version = sManifest.version,
                        description = sManifest.description,
                        iconUrl = sManifest.effectiveIconUrl,
                        logo = sManifest.effectiveIconUrl,
                        repoUrl = addonEntity.url,
                        types = sManifest.types.ifEmpty { listOf("movie", "series") },
                        isStremioAddon = true,
                        hasCatalogs = sManifest.catalogs.isNotEmpty(),
                        enabled = addonEntity.isEnabled
                    )
                    val record = InstalledPluginRecord(
                        manifest = pluginManifest,
                        scriptFile = virtualScriptFile,
                        isEnabled = addonEntity.isEnabled,
                        customDomain = null
                    )
                    installedPlugins[pluginId] = record

                    if (addonEntity.isEnabled) {
                        val wrapper = StremioPluginWrapper(
                            manifest = sManifest,
                            manifestUrl = addonEntity.url,
                            client = stremioClient,
                            activeStreamAddonsProvider = { getActiveStreamAddons() }
                        )
                        loadedPlugins[pluginId] = wrapper
                    }
                } catch (e: Exception) {
                    println("[PluginManager] Error loading Stremio addon ${addonEntity.url}: ${e.message}")
                }
            }

            // Quét kho nguồn để ghi nhận các scraper 18+, truyện tranh, truyện chữ và live đã biết
            repositoryDao?.getAllRepositoriesSync()?.forEach { repoEntity ->
                try {
                    val repoManifest = json.decodeFromString<RepositoryManifest>(repoEntity.manifestJson)
                    repoManifest.allScrapers.forEach { scraper ->
                        if (scraper.isAdult || scraper.categoryType == "adult") knownAdultPluginIds.add(scraper.id)
                        if (scraper.isComic || scraper.categoryType == "comic") knownComicPluginIds.add(scraper.id)
                        if (scraper.isNovel || scraper.categoryType == "novel") knownNovelPluginIds.add(scraper.id)
                        if (scraper.isLive || scraper.categoryType == "live") knownLivePluginIds.add(scraper.id)
                    }
                } catch (_: Exception) {}
            }

            // Sync thứ tự plugin
            loadPluginOrder()
            val currentIds = installedPlugins.keys.toList()
            pluginOrderList.retainAll(currentIds.toSet())
            for (id in currentIds) {
                if (id !in pluginOrderList) {
                    pluginOrderList.add(id)
                }
            }
            savePluginOrder()

            _pluginsUpdateCounter.value += 1
        }
    }

    suspend fun addRepository(repoUrl: String, addedAt: Long? = null): RepositoryManifest = withContext(Dispatchers.IO) {
        val manifest = fetchRepository(repoUrl)
        manifest.allScrapers.forEach { scraper ->
            if (scraper.isAdult || scraper.categoryType == "adult") knownAdultPluginIds.add(scraper.id)
            if (scraper.isComic || scraper.categoryType == "comic") knownComicPluginIds.add(scraper.id)
            if (scraper.isNovel || scraper.categoryType == "novel") knownNovelPluginIds.add(scraper.id)
            if (scraper.isLive || scraper.categoryType == "live") knownLivePluginIds.add(scraper.id)
        }
        val manifestJson = json.encodeToString(RepositoryManifest.serializer(), manifest)
        val existingRepo = repositoryDao?.getAllRepositoriesSync()?.firstOrNull { it.url == repoUrl }
        val effectiveAddedAt = when {
            addedAt != null && addedAt > 0L -> addedAt
            existingRepo != null && existingRepo.addedAt > 0L -> existingRepo.addedAt
            else -> System.currentTimeMillis()
        }
        repositoryDao?.insert(
            RepositoryEntity(
                url = repoUrl,
                name = manifest.name,
                version = manifest.version,
                manifestJson = manifestJson,
                addedAt = effectiveAddedAt
            )
        )
        manifest
    }

    suspend fun removeRepository(repoUrl: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val idsToDelete = mutableSetOf<String>()
            installedPlugins.values.filter { it.manifest.repoUrl == repoUrl }.forEach {
                idsToDelete.add(it.manifest.id)
            }
            pluginDao?.getPluginsByRepoUrl(repoUrl)?.forEach {
                idsToDelete.add(it.id)
            }

            for (pluginId in idsToDelete) {
                loadedPlugins.remove(pluginId)?.close()
                installedPlugins.remove(pluginId)
                pluginDao?.delete(pluginId)
                val dir = File(pluginsDir, pluginId)
                if (dir.exists()) dir.deleteRecursively()
            }

            repositoryDao?.delete(repoUrl)
            _pluginsUpdateCounter.value += 1
        }
    }

    suspend fun getRepositories(): List<RepositoryEntity> = withContext(Dispatchers.IO) {
        repositoryDao?.getAllRepositoriesSync() ?: emptyList()
    }

    suspend fun fetchRepository(repoUrl: String): RepositoryManifest = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(repoUrl).build()
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}: Không thể tải manifest từ $repoUrl")
        }
        val body = response.body?.string() ?: throw Exception("Empty repository manifest response")
        json.decodeFromString<RepositoryManifest>(body)
    }

    fun resolveUrl(baseUrl: String, rel: String): String {
        if (rel.startsWith("http://") || rel.startsWith("https://")) return rel
        val lastSlash = baseUrl.lastIndexOf('/')
        val base = if (lastSlash != -1) baseUrl.substring(0, lastSlash + 1) else "$baseUrl/"
        val cleanRel = rel.removePrefix("/")
        return base + cleanRel
    }

    suspend fun installScraper(repoUrl: String, scraper: PluginManifest): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                // Fetch sub-manifest if specified (e.g. "kkphim/manifest.json")
                var activeManifest = scraper
                val subManifestRel = scraper.manifest
                if (!subManifestRel.isNullOrBlank()) {
                    try {
                        val subManifestUrl = resolveUrl(repoUrl, subManifestRel)
                        val subReq = Request.Builder().url(subManifestUrl).build()
                        val subResp = okHttpClient.newCall(subReq).execute()
                        if (subResp.isSuccessful) {
                            val subBody = subResp.body?.string()
                            if (!subBody.isNullOrBlank()) {
                                val parsed = json.decodeFromString<PluginManifest>(subBody)
                                activeManifest = parsed.copy(
                                    id = scraper.id,
                                    name = scraper.name.ifBlank { parsed.name },
                                    version = scraper.version.ifBlank { parsed.version },
                                    manifest = scraper.manifest,
                                    entry = scraper.entry ?: parsed.entry,
                                    filename = scraper.filename ?: parsed.filename,
                                    logo = scraper.logo ?: parsed.logo,
                                    repoUrl = repoUrl
                                )
                            }
                        }
                    } catch (e: Exception) {
                        println("[PluginManager] Sub-manifest fetch skipped: ${e.message}")
                    }
                }

                // Determine script relative path
                val scriptRelative = when {
                    !activeManifest.entry.isNullOrBlank() && activeManifest.entry != "plugin.js" -> activeManifest.entry!!
                    !activeManifest.filename.isNullOrBlank() && activeManifest.filename != "plugin.js" -> activeManifest.filename!!
                    !activeManifest.entry.isNullOrBlank() -> activeManifest.entry!!
                    !activeManifest.filename.isNullOrBlank() -> activeManifest.filename!!
                    else -> "${activeManifest.id}/plugin.js"
                }

                var scriptUrl = resolveUrl(repoUrl, scriptRelative)

                // 1. Download script with fallback
                var request = Request.Builder().url(scriptUrl).build()
                var response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful && response.code == 404 && scriptRelative != "${activeManifest.id}/plugin.js") {
                    val fallbackUrl = resolveUrl(repoUrl, "${activeManifest.id}/plugin.js")
                    val fallbackReq = Request.Builder().url(fallbackUrl).build()
                    val fallbackResp = okHttpClient.newCall(fallbackReq).execute()
                    if (fallbackResp.isSuccessful) {
                        scriptUrl = fallbackUrl
                        response = fallbackResp
                    }
                }

                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}: Không thể tải file scraper từ $scriptUrl")
                }
                val scriptContent = response.body?.string()
                    ?: throw Exception("Empty script content from $scriptUrl")

                // 2. Write to disk
                val targetDir = File(pluginsDir, activeManifest.id)
                if (!targetDir.exists()) targetDir.mkdirs()

                val scriptFile = File(targetDir, "plugin.js")
                val manifestFile = File(targetDir, "manifest.json")

                val updatedManifest = activeManifest.copy(repoUrl = repoUrl)
                val manifestJson = json.encodeToString(PluginManifest.serializer(), updatedManifest)

                scriptFile.writeText(scriptContent)
                manifestFile.writeText(manifestJson)

                // 3. Save to database
                val entity = PluginEntity(
                    id = activeManifest.id,
                    name = activeManifest.name,
                    version = activeManifest.version,
                    author = activeManifest.author,
                    description = activeManifest.description,
                    logo = activeManifest.logo,
                    filename = activeManifest.filename ?: activeManifest.entry,
                    defaultDomain = activeManifest.defaultDomain,
                    customDomain = activeManifest.customDomain,
                    repoUrl = repoUrl,
                    supportedTypes = (activeManifest.types.ifEmpty { activeManifest.supportedTypes }).joinToString(","),
                    isEnabled = true,
                    isAdult = activeManifest.isAdult,
                    isComic = activeManifest.isComic,
                    isNovel = activeManifest.isNovel,
                    manifestJson = manifestJson
                )
                pluginDao?.insert(entity)
                if (activeManifest.isAdult) {
                    knownAdultPluginIds.add(activeManifest.id)
                }
                if (activeManifest.isComic) {
                    knownComicPluginIds.add(activeManifest.id)
                }
                if (activeManifest.isNovel || activeManifest.categoryType == "novel") {
                    knownNovelPluginIds.add(activeManifest.id)
                }
                if (activeManifest.isLive || activeManifest.categoryType == "live") {
                    knownLivePluginIds.add(activeManifest.id)
                }

                // 4. Update memory & runtime
                val record = InstalledPluginRecord(
                    manifest = updatedManifest,
                    scriptFile = scriptFile,
                    isEnabled = true,
                    customDomain = activeManifest.customDomain
                )
                installedPlugins[activeManifest.id] = record

                loadedPlugins.remove(activeManifest.id)?.close()
                val loaded = runtime.loadPlugin(updatedManifest, scriptContent)
                loadedPlugins[activeManifest.id] = loaded

                synchronized(pluginOrderList) {
                    if (activeManifest.id !in pluginOrderList) {
                        pluginOrderList.add(activeManifest.id)
                        savePluginOrder()
                    }
                }

                _pluginsUpdateCounter.value += 1
                true
            } catch (e: Exception) {
                println("[PluginManager] Install scraper failed: ${e.message}")
                throw e
            }
        }
    }

    suspend fun uninstallPlugin(pluginId: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val record = installedPlugins[pluginId]
            if (record?.manifest?.isStremioAddon == true) {
                val addonUrl = record.manifest.repoUrl ?: ""
                stremioAddonDao?.delete(addonUrl)
            }
            loadedPlugins.remove(pluginId)?.close()
            installedPlugins.remove(pluginId)
            pluginDao?.delete(pluginId)
            (runtime as? QuickJsRuntime)?.storage?.clear(pluginId)
            val dir = File(pluginsDir, pluginId)
            val deleted = if (dir.exists()) dir.deleteRecursively() else true

            synchronized(pluginOrderList) {
                pluginOrderList.remove(pluginId)
                savePluginOrder()
            }

            _pluginsUpdateCounter.value += 1
            deleted
        }
    }

    suspend fun updateCustomDomain(pluginId: String, customDomain: String?) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val record = installedPlugins[pluginId] ?: return@withLock
            record.customDomain = customDomain
            record.manifest = record.manifest.copy(customDomain = customDomain)
            pluginDao?.updateCustomDomain(pluginId, customDomain)

            // Reload plugin into runtime so custom domain takes effect
            if (record.isEnabled && record.scriptFile.exists()) {
                try {
                    loadedPlugins.remove(pluginId)?.close()
                    val loaded = runtime.loadPlugin(record.manifest, record.scriptFile.readText())
                    loadedPlugins[pluginId] = loaded
                } catch (e: Exception) {
                    println("[PluginManager] Failed to reload plugin after domain change: ${e.message}")
                }
            }
            _pluginsUpdateCounter.value += 1
        }
    }

    suspend fun enablePlugin(pluginId: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val record = installedPlugins[pluginId] ?: return@withLock false
            record.isEnabled = true
            if (record.manifest.isStremioAddon) {
                val addonUrl = record.manifest.repoUrl ?: ""
                stremioAddonDao?.setEnabled(addonUrl, true)
                if (!loadedPlugins.containsKey(pluginId)) {
                    val entity = stremioAddonDao?.getAddonByUrl(addonUrl)
                    if (entity != null) {
                        try {
                            val sManifest = json.decodeFromString<StremioManifest>(entity.manifestJson)
                            val wrapper = StremioPluginWrapper(
                                manifest = sManifest,
                                manifestUrl = entity.url,
                                client = stremioClient,
                                activeStreamAddonsProvider = { getActiveStreamAddons() }
                            )
                            loadedPlugins[pluginId] = wrapper
                        } catch (e: Exception) {
                            println("[PluginManager] Failed to load Stremio addon on enable: ${e.message}")
                        }
                    }
                }
            } else {
                pluginDao?.setEnabled(pluginId, true)
                if (!loadedPlugins.containsKey(pluginId)) {
                    try {
                        val loaded = runtime.loadPlugin(record.manifest, record.scriptFile.readText())
                        loadedPlugins[pluginId] = loaded
                    } catch (e: Exception) {
                        println("[PluginManager] Failed to load plugin on enable: ${e.message}")
                    }
                }
            }
            _pluginsUpdateCounter.value += 1
            true
        }
    }

    suspend fun disablePlugin(pluginId: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val record = installedPlugins[pluginId] ?: return@withLock false
            record.isEnabled = false
            if (record.manifest.isStremioAddon) {
                val addonUrl = record.manifest.repoUrl ?: ""
                stremioAddonDao?.setEnabled(addonUrl, false)
            } else {
                pluginDao?.setEnabled(pluginId, false)
            }
            loadedPlugins.remove(pluginId)?.close()
            _pluginsUpdateCounter.value += 1
            true
        }
    }

    fun getInstalledPlugins(): List<PluginManifest> {
        val orderMap = synchronized(pluginOrderList) {
            pluginOrderList.mapIndexed { idx, id -> id to idx }.toMap()
        }
        return installedPlugins.values
            .sortedBy { orderMap[it.manifest.id] ?: Int.MAX_VALUE }
            .map { it.manifest }
    }

    fun getInstalledRecords(): List<InstalledPluginRecord> {
        val orderMap = synchronized(pluginOrderList) {
            pluginOrderList.mapIndexed { idx, id -> id to idx }.toMap()
        }
        return installedPlugins.values.sortedBy { orderMap[it.manifest.id] ?: Int.MAX_VALUE }
    }

    fun movePluginOrder(pluginId: String, moveUp: Boolean): Boolean {
        val success = synchronized(pluginOrderList) {
            val index = pluginOrderList.indexOf(pluginId)
            if (index == -1) return@synchronized false
            val targetIndex = if (moveUp) index - 1 else index + 1
            if (targetIndex < 0 || targetIndex >= pluginOrderList.size) return@synchronized false
            pluginOrderList.removeAt(index)
            pluginOrderList.add(targetIndex, pluginId)
            savePluginOrder()
            true
        }
        if (success) {
            _pluginsUpdateCounter.value += 1
        }
        return success
    }

    fun canMovePlugin(pluginId: String, moveUp: Boolean): Boolean {
        synchronized(pluginOrderList) {
            val index = pluginOrderList.indexOf(pluginId)
            if (index == -1) return false
            return if (moveUp) index > 0 else index < pluginOrderList.size - 1
        }
    }

    fun getPluginOrder(): List<String> = synchronized(pluginOrderList) {
        pluginOrderList.toList()
    }

    fun applyPluginOrder(newOrder: List<String>) {
        synchronized(pluginOrderList) {
            val validIds = installedPlugins.keys.toSet()
            val cleaned = newOrder.filter { it in validIds }.toMutableList()
            for (id in validIds) {
                if (id !in cleaned) cleaned.add(id)
            }
            pluginOrderList.clear()
            pluginOrderList.addAll(cleaned)
            savePluginOrder()
        }
        _pluginsUpdateCounter.value += 1
    }

    suspend fun checkDomain(domain: String): PluginCheckResult = withContext(Dispatchers.IO) {
        val raw = domain.trim()
        if (raw.isBlank()) return@withContext PluginCheckResult(
            isAlive = false,
            defaultMessage = "Domain không được để trống",
            statusType = PluginCheckResult.StatusType.DOMAIN_EMPTY
        )
        val targetUrl = if (!raw.startsWith("http://") && !raw.startsWith("https://")) "https://$raw" else raw
        val start = System.currentTimeMillis()
        try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) JkhxPlayer/1.0")
                .head()
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                val latency = System.currentTimeMillis() - start
                if (resp.isSuccessful || resp.code in 200..399) {
                    PluginCheckResult(
                        isAlive = true,
                        defaultMessage = "Hoạt động (${resp.code} - ${latency}ms)",
                        statusType = PluginCheckResult.StatusType.DOMAIN_ACTIVE,
                        latencyMs = latency,
                        httpCode = resp.code
                    )
                } else {
                    PluginCheckResult(
                        isAlive = false,
                        defaultMessage = "Lỗi HTTP ${resp.code} (${latency}ms)",
                        statusType = PluginCheckResult.StatusType.DOMAIN_HTTP_ERROR,
                        latencyMs = latency,
                        httpCode = resp.code
                    )
                }
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            PluginCheckResult(
                isAlive = false,
                defaultMessage = e.message?.take(35) ?: "Không thể kết nối (${latency}ms)",
                statusType = PluginCheckResult.StatusType.CANNOT_CONNECT,
                latencyMs = latency,
                customMessage = e.message?.take(35)
            )
        }
    }

    suspend fun checkPluginHealth(pluginId: String, testDomain: String? = null): PluginCheckResult = withContext(Dispatchers.IO) {
        val cleanTestDomain = testDomain?.trim()?.ifBlank { null }
        if (cleanTestDomain != null) {
            // Khi người dùng nhập domain mới vào ô textbox, kiểm tra trực tiếp domain mới đó
            return@withContext checkDomain(cleanTestDomain)
        }

        // Khi không nhập domain mới (kiểm tra trạng thái nguồn thông thường):
        // 1. Ưu tiên kiểm tra hàm checkHealth() do chính plugin cung cấp
        try {
            val plugin = getPlugin(pluginId)
            val health = plugin?.checkHealth()
            if (health != null) {
                val isAlive = health.status == "ok"
                val latencyText = if (health.latencyMs != null) " (${health.latencyMs}ms)" else ""
                val msg = (health.message ?: if (isAlive) "Kết nối máy chủ ổn định" else "Trạng thái: ${health.status}") + latencyText
                return@withContext PluginCheckResult(
                    isAlive = isAlive,
                    defaultMessage = msg,
                    statusType = if (isAlive) PluginCheckResult.StatusType.OK else PluginCheckResult.StatusType.CUSTOM_STATUS,
                    latencyMs = health.latencyMs,
                    rawStatus = health.status,
                    customMessage = health.message
                )
            }
        } catch (e: Exception) {
            val errMsg = e.message?.take(40) ?: "Lỗi kết nối máy chủ"
            return@withContext PluginCheckResult(
                isAlive = false,
                defaultMessage = errMsg,
                statusType = PluginCheckResult.StatusType.SERVER_ERROR,
                customMessage = e.message?.take(40)
            )
        }

        // 2. Fallback nếu plugin không cài hàm checkHealth(): kiểm tra domain đang dùng
        val record = installedPlugins[pluginId]
        val activeDomain = record?.customDomain ?: record?.manifest?.defaultDomain
        if (!activeDomain.isNullOrBlank()) {
            return@withContext checkDomain(activeDomain)
        }

        PluginCheckResult(
            isAlive = false,
            defaultMessage = "Chưa cấu hình domain",
            statusType = PluginCheckResult.StatusType.NO_DOMAIN_CONFIGURED
        )
    }

    fun isPluginInstalled(pluginId: String): Boolean {
        return installedPlugins.containsKey(pluginId)
    }

    fun isPluginEnabled(pluginId: String): Boolean {
        val record = installedPlugins[pluginId] ?: return false
        return record.isEnabled
    }

    fun getInstalledVersion(pluginId: String): String? {
        return installedPlugins[pluginId]?.manifest?.version
    }

    suspend fun getPluginSettings(pluginId: String): List<PluginSettingItem>? = withContext(Dispatchers.IO) {
        val plugin = getPlugin(pluginId) ?: return@withContext null
        val items = plugin.getSettings() ?: return@withContext null
        items.map { item ->
            val savedVal = getPluginSetting(pluginId, item.key)
            item.copy(currentValue = savedVal ?: item.defaultValue)
        }
    }

    fun getPluginSetting(pluginId: String, key: String): String? {
        return (runtime as? QuickJsRuntime)?.storage?.get(pluginId, key)
    }

    fun setPluginSetting(pluginId: String, key: String, value: String) {
        (runtime as? QuickJsRuntime)?.storage?.set(pluginId, key, value)
        _pluginsUpdateCounter.value += 1
    }

    fun isAdultProvider(pluginId: String): Boolean {
        val lowerId = pluginId.lowercase()
        val record = installedPlugins[pluginId] ?: installedPlugins[lowerId]
        if (record != null) {
            val m = record.manifest
            if (m.isAdult || m.categoryType == "adult" || m.types.any { it.equals("adult", ignoreCase = true) } || m.supportedTypes.any { it.equals("adult", ignoreCase = true) }) {
                return true
            }
        }
        return knownAdultPluginIds.contains(pluginId) || knownAdultPluginIds.contains(lowerId)
    }

    fun isComicProvider(pluginId: String): Boolean {
        val lowerId = pluginId.lowercase()
        val record = installedPlugins[pluginId] ?: installedPlugins[lowerId]
        if (record != null) {
            val m = record.manifest
            if (m.isComic || m.categoryType == "comic" || m.types.any { it.equals("comic", ignoreCase = true) } || m.supportedTypes.any { it.equals("comic", ignoreCase = true) }) {
                return true
            }
        }
        return knownComicPluginIds.contains(pluginId) || knownComicPluginIds.contains(lowerId)
    }

    fun isNovelProvider(pluginId: String): Boolean {
        val lowerId = pluginId.lowercase()
        val record = installedPlugins[pluginId] ?: installedPlugins[lowerId]
        if (record != null) {
            val m = record.manifest
            if (m.isNovel || m.categoryType == "novel" || m.types.any { it.equals("novel", ignoreCase = true) } || m.supportedTypes.any { it.equals("novel", ignoreCase = true) }) {
                return true
            }
        }
        return knownNovelPluginIds.contains(pluginId) || knownNovelPluginIds.contains(lowerId)
    }

    fun isLiveProvider(pluginId: String): Boolean {
        val lowerId = pluginId.lowercase()
        val record = installedPlugins[pluginId] ?: installedPlugins[lowerId]
        if (record != null) {
            val m = record.manifest
            if (m.isLive || m.categoryType == "live" || m.types.any { it.equals("live", ignoreCase = true) } || m.supportedTypes.any { it.equals("live", ignoreCase = true) }) {
                return true
            }
        }
        return knownLivePluginIds.contains(pluginId) || knownLivePluginIds.contains(lowerId)
    }

    /**
     * Kiểm tra một nguồn/plugin có được phép hiển thị trong Lịch sử xem và Mục yêu thích hay không:
     * 1. Ẩn nếu phân nhóm tương ứng bị tắt trong Cài đặt (isAdultEnabled, isComicEnabled, isNovelEnabled, isLiveEnabled).
     * 2. KHÔNG ẨN nếu plugin bị xóa, chưa cài đặt hoặc bị tắt (isEnabled = false) trong màn hình Quản lý Plugin.
     */
    fun isProviderVisible(
        pluginId: String,
        isAdultEnabled: Boolean,
        isComicEnabled: Boolean,
        isNovelEnabled: Boolean,
        isLiveEnabled: Boolean
    ): Boolean {
        if (!isAdultEnabled && isAdultProvider(pluginId)) return false
        if (!isComicEnabled && isComicProvider(pluginId)) return false
        if (!isNovelEnabled && isNovelProvider(pluginId)) return false
        if (!isLiveEnabled && isLiveProvider(pluginId)) return false
        return true
    }

    fun isProviderAvailable(
        pluginId: String,
        isAdultEnabled: Boolean,
        isComicEnabled: Boolean,
        isNovelEnabled: Boolean,
        isLiveEnabled: Boolean
    ): Boolean = isProviderVisible(pluginId, isAdultEnabled, isComicEnabled, isNovelEnabled, isLiveEnabled)

    /**
     * Chỉ chấp nhận các plugin có types chứa "movie" hoặc "tv",
     * và tuyệt đối không thuộc các loại adult, comic, novel, live theo yêu cầu của người dùng.
     */
    fun isMovieTvProvider(pluginId: String): Boolean {
        if (knownAdultPluginIds.contains(pluginId) || knownComicPluginIds.contains(pluginId) || knownNovelPluginIds.contains(pluginId) || knownLivePluginIds.contains(pluginId)) {
            return false
        }
        val record = installedPlugins[pluginId] ?: return false
        val m = record.manifest
        if (m.isStremioAddon && !m.hasCatalogs) return false
        if (m.isAdult || m.isComic || m.isNovel || m.isLive) return false
        val effectiveTypes = m.types.ifEmpty { m.supportedTypes }
        val hasDisallowed = effectiveTypes.any {
            it.equals("adult", ignoreCase = true) ||
            it.equals("comic", ignoreCase = true) ||
            it.equals("novel", ignoreCase = true) ||
            it.equals("live", ignoreCase = true)
        }
        if (hasDisallowed) return false
        val hasMovieTv = effectiveTypes.any {
            it.equals("movie", ignoreCase = true) || it.equals("tv", ignoreCase = true)
        }
        return hasMovieTv || effectiveTypes.isEmpty()
    }

    suspend fun getMovieTvPlugins(): List<LoadedPlugin> = withContext(Dispatchers.IO) {
        val records = installedPlugins.values.filter { it.isEnabled && isMovieTvProvider(it.manifest.id) }
        records.mapNotNull { getPlugin(it.manifest.id) }
    }

    fun getEnabledPlugins(
        includeAdult: Boolean = true,
        includeComic: Boolean = true,
        includeNovel: Boolean = true,
        includeLive: Boolean = true
    ): List<LoadedPlugin> {
        val orderMap = synchronized(pluginOrderList) {
            pluginOrderList.mapIndexed { idx, id -> id to idx }.toMap()
        }
        return installedPlugins.values
            .filter {
                it.isEnabled &&
                (includeAdult || (!it.manifest.isAdult && !isAdultProvider(it.manifest.id))) &&
                (includeComic || (!it.manifest.isComic && !isComicProvider(it.manifest.id))) &&
                (includeNovel || (!it.manifest.isNovel && it.manifest.categoryType != "novel" && !isNovelProvider(it.manifest.id))) &&
                (includeLive || (!it.manifest.isLive && it.manifest.categoryType != "live" && !isLiveProvider(it.manifest.id)))
            }
            .sortedBy { orderMap[it.manifest.id] ?: Int.MAX_VALUE }
            .mapNotNull { loadedPlugins[it.manifest.id] }
    }

    suspend fun getPlugin(pluginId: String): LoadedPlugin? = withContext(Dispatchers.IO) {
        val record = installedPlugins[pluginId] ?: return@withContext null
        if (!record.isEnabled) return@withContext null

        loadedPlugins[pluginId]?.let { return@withContext it }

        mutex.withLock {
            loadedPlugins[pluginId]?.let { return@withLock it }
            if (record.manifest.isStremioAddon) {
                val addonUrl = record.manifest.repoUrl ?: ""
                val entity = stremioAddonDao?.getAddonByUrl(addonUrl)
                if (entity != null) {
                    try {
                        val sManifest = json.decodeFromString<StremioManifest>(entity.manifestJson)
                        val wrapper = StremioPluginWrapper(
                            manifest = sManifest,
                            manifestUrl = entity.url,
                            client = stremioClient,
                            activeStreamAddonsProvider = { getActiveStreamAddons() }
                        )
                        loadedPlugins[pluginId] = wrapper
                        return@withLock wrapper
                    } catch (e: Exception) {
                        println("[PluginManager] Failed to auto-load Stremio addon $pluginId: ${e.message}")
                    }
                }
            } else if (record.scriptFile.exists()) {
                try {
                    val loaded = runtime.loadPlugin(record.manifest, record.scriptFile.readText())
                    loadedPlugins[pluginId] = loaded
                    return@withLock loaded
                } catch (e: Exception) {
                    println("[PluginManager] Failed to auto-load plugin $pluginId: ${e.message}")
                }
            }
            null
        }
    }

    fun getPluginName(pluginId: String): String {
        return installedPlugins[pluginId]?.manifest?.name ?: pluginId
    }

    fun searchAll(
        keyword: String,
        page: Int = 1,
        includeAdult: Boolean = true,
        includeComic: Boolean = true,
        includeNovel: Boolean = true,
        includeLive: Boolean = true
    ): Flow<List<MovieItem>> = flow {
        val enabled = getEnabledPlugins(
            includeAdult = includeAdult,
            includeComic = includeComic,
            includeNovel = includeNovel,
            includeLive = includeLive
        )
        coroutineScope {
            val deferreds = enabled.map { plugin ->
                async {
                    try {
                        withTimeout(15000L) {
                            val items = plugin.search(keyword, page).items
                            items.mapNotNull { item ->
                                val effectiveItem = if (item.providerId.isNullOrBlank()) item.copy(providerId = plugin.id) else item
                                val pid = effectiveItem.providerId ?: plugin.id
                                val visible = (includeAdult || !isAdultProvider(pid)) &&
                                        (includeComic || !isComicProvider(pid)) &&
                                        (includeNovel || !isNovelProvider(pid)) &&
                                        (includeLive || !isLiveProvider(pid))
                                if (visible) effectiveItem else null
                            }
                        }
                    } catch (e: Exception) {
                        println("[SearchAll] Plugin ${plugin.id} error: ${e.message}")
                        emptyList()
                    }
                }
            }

            for (def in deferreds) {
                val res = def.await()
                if (res.isNotEmpty()) {
                    emit(res)
                }
            }
        }
    }

    fun getHomeFeed(): Flow<HomeSection> = flow {
        val enabled = getEnabledPlugins()
        coroutineScope {
            val deferreds = enabled.map { plugin ->
                async {
                    try {
                        withTimeout(30000L) {
                            val homeResult = plugin.getHome()
                            homeResult.sections.map { sec ->
                                sec.copy(items = sec.items.map { item ->
                                    if (item.providerId.isNullOrBlank()) item.copy(providerId = plugin.id) else item
                                })
                            }
                        }
                    } catch (e: Exception) {
                        println("[HomeFeed] Plugin ${plugin.id} error: ${e.message}")
                        emptyList()
                    }
                }
            }

            for (def in deferreds) {
                val sections = def.await()
                for (sec in sections) {
                    if (sec.items.isNotEmpty()) {
                        emit(sec)
                    }
                }
            }
        }
    }

    // ==================== STREMIO ADDONS MANAGEMENT ====================

    suspend fun getStremioAddons(): List<StremioAddonEntity> = withContext(Dispatchers.IO) {
        stremioAddonDao?.getAllAddonsSync() ?: emptyList()
    }

    fun getActiveStreamAddons(): List<Pair<String, StremioManifest>> {
        val result = mutableListOf<Pair<String, StremioManifest>>()
        for (plugin in loadedPlugins.values) {
            if (plugin is StremioPluginWrapper && plugin.manifest.hasResource("stream")) {
                result.add(Pair(plugin.manifestUrl, plugin.manifest))
            }
        }
        return result
    }

    fun getActiveSubtitleAddons(): List<Pair<String, StremioManifest>> {
        val result = mutableListOf<Pair<String, StremioManifest>>()
        for (plugin in loadedPlugins.values) {
            if (plugin is StremioPluginWrapper && plugin.manifest.hasResource("subtitles")) {
                result.add(Pair(plugin.manifestUrl, plugin.manifest))
            }
        }
        return result
    }

    fun getStremioClient(): StremioBridgeClient = stremioClient

    suspend fun installStremioAddon(rawUrl: String, addedAt: Long? = null): StremioAddonEntity = withContext(Dispatchers.IO) {
        mutex.withLock {
            val normalizedUrl = StremioBridgeClient.normalizeManifestUrl(rawUrl)
            val (manifest, rawManifestJson) = stremioClient.fetchManifestWithRaw(normalizedUrl)
            val pluginId = StremioPluginWrapper.makePluginId(manifest.id)

            val existingAddon = stremioAddonDao?.getAddonByUrl(normalizedUrl)
            val effectiveAddedAt = when {
                addedAt != null && addedAt > 0L -> addedAt
                existingAddon != null && existingAddon.addedAt > 0L -> existingAddon.addedAt
                else -> System.currentTimeMillis()
            }
            val entity = StremioAddonEntity(
                url = normalizedUrl,
                id = manifest.id,
                name = manifest.name,
                version = manifest.version,
                description = manifest.description,
                iconUrl = manifest.effectiveIconUrl,
                manifestJson = rawManifestJson,
                isEnabled = true,
                orderIndex = installedPlugins.size,
                addedAt = effectiveAddedAt
            )
            stremioAddonDao?.insert(entity)

            val virtualScriptFile = File(pluginsDir, "$pluginId/manifest.json")
            val pluginManifest = PluginManifest(
                id = pluginId,
                name = manifest.name,
                version = manifest.version,
                description = manifest.description,
                iconUrl = manifest.effectiveIconUrl,
                logo = manifest.effectiveIconUrl,
                repoUrl = normalizedUrl,
                types = manifest.types.ifEmpty { listOf("movie", "series") },
                isStremioAddon = true,
                hasCatalogs = manifest.catalogs.isNotEmpty(),
                enabled = true
            )
            val record = InstalledPluginRecord(
                manifest = pluginManifest,
                scriptFile = virtualScriptFile,
                isEnabled = true,
                customDomain = null
            )
            installedPlugins[pluginId] = record

            val wrapper = StremioPluginWrapper(
                manifest = manifest,
                manifestUrl = normalizedUrl,
                client = stremioClient,
                activeStreamAddonsProvider = { getActiveStreamAddons() }
            )
            loadedPlugins.remove(pluginId)?.close()
            loadedPlugins[pluginId] = wrapper

            synchronized(pluginOrderList) {
                if (pluginId !in pluginOrderList) {
                    pluginOrderList.add(pluginId)
                    savePluginOrder()
                }
            }

            _pluginsUpdateCounter.value += 1
            entity
        }
    }

    suspend fun removeStremioAddon(url: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val normalizedUrl = StremioBridgeClient.normalizeManifestUrl(url)
            val entity = stremioAddonDao?.getAddonByUrl(normalizedUrl) ?: stremioAddonDao?.getAddonByUrl(url) ?: return@withLock
            val pluginId = StremioPluginWrapper.makePluginId(entity.id)

            stremioAddonDao?.delete(entity.url)
            if (entity.url != normalizedUrl) {
                stremioAddonDao?.delete(normalizedUrl)
            }
            loadedPlugins.remove(pluginId)?.close()
            installedPlugins.remove(pluginId)

            synchronized(pluginOrderList) {
                pluginOrderList.remove(pluginId)
                savePluginOrder()
            }

            _pluginsUpdateCounter.value += 1
        }
    }

    suspend fun toggleStremioAddon(url: String, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val normalizedUrl = StremioBridgeClient.normalizeManifestUrl(url)
            val entity = stremioAddonDao?.getAddonByUrl(normalizedUrl) ?: return@withLock
            val pluginId = StremioPluginWrapper.makePluginId(entity.id)

            stremioAddonDao.setEnabled(normalizedUrl, isEnabled)
            installedPlugins[pluginId]?.let {
                it.isEnabled = isEnabled
                it.manifest = it.manifest.copy(enabled = isEnabled)
            }

            if (isEnabled) {
                try {
                    val sManifest = json.decodeFromString<StremioManifest>(entity.manifestJson)
                    val wrapper = StremioPluginWrapper(
                        manifest = sManifest,
                        manifestUrl = normalizedUrl,
                        client = stremioClient,
                        activeStreamAddonsProvider = { getActiveStreamAddons() }
                    )
                    loadedPlugins[pluginId] = wrapper
                } catch (e: Exception) {
                    println("[PluginManager] Failed to reload Stremio addon on toggle: ${e.message}")
                }
            } else {
                loadedPlugins.remove(pluginId)?.close()
            }

            _pluginsUpdateCounter.value += 1
        }
    }

    suspend fun updateStremioAddonManifest(url: String, rawManifest: String) = withContext(Dispatchers.IO) {
        val normalizedUrl = StremioBridgeClient.normalizeManifestUrl(url)
        val entity = stremioAddonDao?.getAddonByUrl(normalizedUrl) ?: return@withContext
        stremioAddonDao?.insert(entity.copy(manifestJson = rawManifest))
    }

    override fun close() {
        for ((_, plugin) in loadedPlugins) {
            try {
                plugin.close()
            } catch (e: Exception) {
                // ignore
            }
        }
        loadedPlugins.clear()
        runtime.close()
    }
}

data class PluginCheckResult(
    val isAlive: Boolean,
    val defaultMessage: String,
    val statusType: StatusType = StatusType.OK,
    val latencyMs: Long? = null,
    val httpCode: Int? = null,
    val rawStatus: String? = null,
    val customMessage: String? = null
) {
    enum class StatusType {
        OK,
        CUSTOM_STATUS,
        DOMAIN_ACTIVE,
        DOMAIN_HTTP_ERROR,
        DOMAIN_EMPTY,
        CANNOT_CONNECT,
        NO_DOMAIN_CONFIGURED,
        SERVER_ERROR
    }
}

