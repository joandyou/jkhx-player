package com.jkhx.player

import android.app.Application
import com.jkhx.player.core.database.AppDatabase
import com.jkhx.player.plugin.manager.PluginManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class JkhxApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var database: AppDatabase
        private set

    lateinit var pluginManager: PluginManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        val pluginsDir = File(filesDir, "plugins")

        // First launch: Copy bundled plugins from assets
        copyBundledPluginsIfFirstRun(pluginsDir)

        pluginManager = PluginManager(pluginsDir)

        applicationScope.launch(Dispatchers.IO) {
            pluginManager.loadInstalledPlugins()
        }
    }

    private fun copyBundledPluginsIfFirstRun(pluginsDir: File) {
        if (!pluginsDir.exists()) pluginsDir.mkdirs()

        try {
            val assetPlugins = assets.list("plugins") ?: return
            for (item in assetPlugins) {
                val targetItem = File(pluginsDir, item)
                if (!targetItem.exists()) {
                    copyAssetRecursively("plugins/$item", targetItem)
                }
            }
        } catch (e: Exception) {
            println("[JkhxApp] Error copying assets: ${e.message}")
        }
    }

    private fun copyAssetRecursively(assetPath: String, targetFile: File) {
        val list = assets.list(assetPath)
        if (list == null || list.isEmpty()) {
            // It's a file
            targetFile.parentFile?.mkdirs()
            assets.open(assetPath).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            // It's a directory
            targetFile.mkdirs()
            for (child in list) {
                copyAssetRecursively("$assetPath/$child", File(targetFile, child))
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        pluginManager.close()
    }

    companion object {
        lateinit var instance: JkhxApplication
            private set
    }
}
