package com.jkhx.player.plugin.manager

import com.jkhx.player.core.database.dao.PluginDao
import com.jkhx.player.core.database.dao.RepositoryDao
import com.jkhx.player.core.database.entity.PluginEntity
import com.jkhx.player.core.database.entity.RepositoryEntity
import com.jkhx.player.core.model.*
import com.jkhx.player.plugin.api.LoadedPlugin
import com.jkhx.player.plugin.api.PluginRuntime
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
    private val runtime: PluginRuntime = QuickJsRuntime(),
    private val okHttpClient: OkHttpClient = OkHttpClient()
) : AutoCloseable {

    private val mutex = Mutex()
    private val installedPlugins = ConcurrentHashMap<String, InstalledPluginRecord>()
    private val loadedPlugins = ConcurrentHashMap<String, LoadedPlugin>()

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
                val dir = File(pluginsDir, entity.id)
                val scriptFile = File(dir, "plugin.js")
                if (scriptFile.exists()) {
                    try {
                        val manifest = json.decodeFromString<PluginManifest>(entity.manifestJson)
                            .copy(customDomain = entity.customDomain)
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
            _pluginsUpdateCounter.value += 1
        }
    }

    suspend fun addRepository(repoUrl: String): RepositoryManifest = withContext(Dispatchers.IO) {
        val manifest = fetchRepository(repoUrl)
        val manifestJson = json.encodeToString(RepositoryManifest.serializer(), manifest)
        repositoryDao?.insert(
            RepositoryEntity(
                url = repoUrl,
                name = manifest.name,
                version = manifest.version,
                manifestJson = manifestJson
            )
        )
        manifest
    }

    suspend fun removeRepository(repoUrl: String) = withContext(Dispatchers.IO) {
        repositoryDao?.delete(repoUrl)
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
                    supportedTypes = activeManifest.supportedTypes.joinToString(","),
                    isEnabled = true,
                    isAdult = activeManifest.isAdult,
                    manifestJson = manifestJson
                )
                pluginDao?.insert(entity)

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
            loadedPlugins.remove(pluginId)?.close()
            installedPlugins.remove(pluginId)
            pluginDao?.delete(pluginId)
            val dir = File(pluginsDir, pluginId)
            val deleted = if (dir.exists()) dir.deleteRecursively() else true
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
            pluginDao?.setEnabled(pluginId, true)
            if (!loadedPlugins.containsKey(pluginId)) {
                try {
                    val loaded = runtime.loadPlugin(record.manifest, record.scriptFile.readText())
                    loadedPlugins[pluginId] = loaded
                } catch (e: Exception) {
                    println("[PluginManager] Failed to load plugin on enable: ${e.message}")
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
            pluginDao?.setEnabled(pluginId, false)
            loadedPlugins.remove(pluginId)?.close()
            _pluginsUpdateCounter.value += 1
            true
        }
    }

    fun getInstalledPlugins(): List<PluginManifest> {
        return installedPlugins.values.map { it.manifest }
    }

    fun getInstalledRecords(): List<InstalledPluginRecord> {
        return installedPlugins.values.toList()
    }

    fun isPluginInstalled(pluginId: String): Boolean {
        return installedPlugins.containsKey(pluginId)
    }

    fun getInstalledVersion(pluginId: String): String? {
        return installedPlugins[pluginId]?.manifest?.version
    }

    fun getEnabledPlugins(): List<LoadedPlugin> {
        return installedPlugins.values
            .filter { it.isEnabled }
            .mapNotNull { loadedPlugins[it.manifest.id] }
    }

    fun getPlugin(pluginId: String): LoadedPlugin? {
        val record = installedPlugins[pluginId] ?: return null
        return if (record.isEnabled) loadedPlugins[pluginId] else null
    }

    fun searchAll(keyword: String, page: Int = 1): Flow<List<MovieItem>> = flow {
        val enabled = getEnabledPlugins()
        coroutineScope {
            val deferreds = enabled.map { plugin ->
                async {
                    try {
                        withTimeout(15000L) {
                            plugin.search(keyword, page).items
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
