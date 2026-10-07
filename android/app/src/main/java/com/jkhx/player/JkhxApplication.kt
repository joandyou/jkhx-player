package com.jkhx.player

import android.app.Application
import com.jkhx.player.core.database.AppDatabase
import com.jkhx.player.plugin.manager.PluginManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

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

        // Pure clean client: NO hardcoded/bundled plugins copied from assets!
        // Plugins are installed dynamically by user from manifest.json repositories.
        pluginManager = PluginManager(
            pluginsDir = pluginsDir,
            pluginDao = database.pluginDao(),
            repositoryDao = database.repositoryDao()
        )

        applicationScope.launch(Dispatchers.IO) {
            pluginManager.loadInstalledPlugins()
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
