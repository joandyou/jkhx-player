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

            // 1. Try reading from Database
            val dbPlugins = pluginDao?.getAllPluginsSync() ?: emptyList()
            if (dbPlugins.isNotEmpty()) {
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
                return@withLock
            }

            // 2. Fallback: Scan disk
            val subdirs = pluginsDir.listFiles { f -> f.isDirectory } ?: return@withLock
            for (dir in subdirs) {
                val manifestFile = File(dir, "manifest.json")
                val scriptFile = File(dir, "plugin.js")
                if (manifestFile.exists() && scriptFile.exists()) {
                    try {
                        val manifestJson = manifestFile.readText()
                        val manifest = json.decodeFromString<PluginManifest>(manifestJson)
                        val record = InstalledPluginRecord(manifest, scriptFile, isEnabled = true)
                        installedPlugins[manifest.id] = record

                        val loaded = runtime.loadPlugin(manifest, scriptFile.readText())
                        loadedPlugins[manifest.id] = loaded

                        // Sync into DB
                        pluginDao?.insert(
                            PluginEntity(
                                id = manifest.id,
                                name = manifest.name,
                                version = manifest.version,
                                author = manifest.author,
                                description = manifest.description,
                                logo = manifest.logo,
                                filename = manifest.filename,
                                defaultDomain = manifest.defaultDomain,
                                customDomain = manifest.customDomain,
                                repoUrl = manifest.repoUrl,
                                supportedTypes = manifest.supportedTypes.joinToString(","),
                                isEnabled = true,
                                isAdult = manifest.isAdult,
                                manifestJson = manifestJson
                            )
                        )
                    } catch (e: Exception) {
                        println("[PluginManager] Error scanning disk plugin ${dir.name}: ${e.message}")
                    }
                }
            }
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
                val scriptRelative = if (scraper.filename.isNotBlank()) scraper.filename else scraper.entry
                val scriptUrl = resolveUrl(repoUrl, scriptRelative)

                // 1. Download script
                val request = Request.Builder().url(scriptUrl).build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}: Không thể tải file scraper từ $scriptUrl")
                }
                val scriptContent = response.body?.string()
                    ?: throw Exception("Empty script content from $scriptUrl")

                // 2. Write to disk
                val targetDir = File(pluginsDir, scraper.id)
                if (!targetDir.exists()) targetDir.mkdirs()

                val scriptFile = File(targetDir, "plugin.js")
                val manifestFile = File(targetDir, "manifest.json")

                val updatedManifest = scraper.copy(repoUrl = repoUrl)
                val manifestJson = json.encodeToString(PluginManifest.serializer(), updatedManifest)

                scriptFile.writeText(scriptContent)
                manifestFile.writeText(manifestJson)

                // 3. Save to database
                val entity = PluginEntity(
                    id = scraper.id,
                    name = scraper.name,
                    version = scraper.version,
                    author = scraper.author,
                    description = scraper.description,
                    logo = scraper.logo,
                    filename = scraper.filename,
                    defaultDomain = scraper.defaultDomain,
                    customDomain = scraper.customDomain,
                    repoUrl = repoUrl,
                    supportedTypes = scraper.supportedTypes.joinToString(","),
                    isEnabled = true,
                    isAdult = scraper.isAdult,
                    manifestJson = manifestJson
                )
                pluginDao?.insert(entity)

                // 4. Update memory & runtime
                val record = InstalledPluginRecord(
                    manifest = updatedManifest,
                    scriptFile = scriptFile,
                    isEnabled = true,
                    customDomain = scraper.customDomain
                )
                installedPlugins[scraper.id] = record

                loadedPlugins.remove(scraper.id)?.close()
                val loaded = runtime.loadPlugin(updatedManifest, scriptContent)
                loadedPlugins[scraper.id] = loaded
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
            if (dir.exists()) dir.deleteRecursively() else true
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
        }
    }

    suspend fun enablePlugin(pluginId: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val record = installedPlugins[pluginId] ?: return@withLock false
            record.isEnabled = true
            pluginDao?.setEnabled(pluginId, true)
            if (!loadedPlugins.containsKey(pluginId)) {
                val loaded = runtime.loadPlugin(record.manifest, record.scriptFile.readText())
                loadedPlugins[pluginId] = loaded
            }
            true
        }
    }

    suspend fun disablePlugin(pluginId: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val record = installedPlugins[pluginId] ?: return@withLock false
            record.isEnabled = false
            pluginDao?.setEnabled(pluginId, false)
            loadedPlugins.remove(pluginId)?.close()
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
                        withTimeout(15000L) {
                            plugin.getHome().sections
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
