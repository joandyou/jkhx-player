package com.jkhx.player.plugin.manager

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
    val manifest: PluginManifest,
    val scriptFile: File,
    var isEnabled: Boolean = true
)

class PluginManager(
    private val pluginsDir: File,
    private val runtime: PluginRuntime = QuickJsRuntime(),
    private val okHttpClient: OkHttpClient = OkHttpClient()
) : AutoCloseable {

    private val mutex = Mutex()
    private val installedPlugins = ConcurrentHashMap<String, InstalledPluginRecord>()
    private val loadedPlugins = ConcurrentHashMap<String, LoadedPlugin>()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    init {
        if (!pluginsDir.exists()) pluginsDir.mkdirs()
    }

    suspend fun loadInstalledPlugins() = withContext(Dispatchers.IO) {
        mutex.withLock {
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

                        // Activate in runtime
                        val loaded = runtime.loadPlugin(manifest, scriptFile.readText())
                        loadedPlugins[manifest.id] = loaded
                    } catch (e: Exception) {
                        println("[PluginManager] Error loading plugin from ${dir.name}: ${e.message}")
                    }
                }
            }
        }
    }

    suspend fun installPlugin(manifest: PluginManifest, scriptContent: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val targetDir = File(pluginsDir, manifest.id)
            if (!targetDir.exists()) targetDir.mkdirs()

            val manifestFile = File(targetDir, "manifest.json")
            val scriptFile = File(targetDir, "plugin.js")

            manifestFile.writeText(json.encodeToString(PluginManifest.serializer(), manifest))
            scriptFile.writeText(scriptContent)

            val record = InstalledPluginRecord(manifest, scriptFile, isEnabled = true)
            installedPlugins[manifest.id] = record

            try {
                loadedPlugins[manifest.id]?.close()
                val loaded = runtime.loadPlugin(manifest, scriptContent)
                loadedPlugins[manifest.id] = loaded
                true
            } catch (e: Exception) {
                println("[PluginManager] Failed to instantiate plugin ${manifest.id}: ${e.message}")
                false
            }
        }
    }

    suspend fun uninstallPlugin(pluginId: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadedPlugins.remove(pluginId)?.close()
            installedPlugins.remove(pluginId)
            val dir = File(pluginsDir, pluginId)
            if (dir.exists()) dir.deleteRecursively() else true
        }
    }

    suspend fun enablePlugin(pluginId: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val record = installedPlugins[pluginId] ?: return@withLock false
            record.isEnabled = true
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
            loadedPlugins.remove(pluginId)?.close()
            true
        }
    }

    fun getInstalledPlugins(): List<PluginManifest> {
        return installedPlugins.values.map { it.manifest }
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

    suspend fun fetchRepository(repoUrl: String): RepositoryManifest = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(repoUrl).build()
        val response = okHttpClient.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Empty repository manifest response")
        json.decodeFromString<RepositoryManifest>(body)
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
                    emit(sec)
                }
            }
        }
    }

    override fun close() {
        loadedPlugins.values.forEach { it.close() }
        loadedPlugins.clear()
        runtime.close()
    }
}

