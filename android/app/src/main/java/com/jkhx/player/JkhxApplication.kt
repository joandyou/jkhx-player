package com.jkhx.player

import android.app.Application
import com.jkhx.player.core.database.AppDatabase
import com.jkhx.player.plugin.manager.PluginManager
import com.jkhx.player.plugin.runtime.QuickJsRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody

class JkhxApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        val settings = com.jkhx.player.settings.SettingsManager.getInstance(this)
        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .proxySelector(object : java.net.ProxySelector() {
                override fun select(uri: java.net.URI?): List<java.net.Proxy> {
                    val proxy = settings.getProxy()
                    return if (proxy != null) listOf(proxy) else listOf(java.net.Proxy.NO_PROXY)
                }
                override fun connectFailed(uri: java.net.URI?, sa: java.net.SocketAddress?, ioe: java.io.IOException?) {}
            })
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val originalUrlStr = originalRequest.url.toString()
                val builder = originalRequest.newBuilder()

                var finalUrlStr = originalUrlStr
                var decryptAlgo: String? = null
                var decryptKey: String? = null
                var decryptIv: String? = null

                // Generic Protocol: Bóc tách metadata headers & decryption từ hash fragment (#Referer=..., #headers=..., #decrypt=...)
                val hashIdx = finalUrlStr.indexOf('#')
                if (hashIdx != -1) {
                    val fragment = finalUrlStr.substring(hashIdx + 1)
                    finalUrlStr = finalUrlStr.substring(0, hashIdx)

                    val parts = fragment.split("&")
                    for (part in parts) {
                        val trimmed = part.trim()
                        if (trimmed.startsWith("Referer=", ignoreCase = true)) {
                            val refVal = trimmed.substringAfter("=").trim()
                            val decodedRef = try { java.net.URLDecoder.decode(refVal, "UTF-8") } catch (_: Exception) { refVal }
                            builder.header("Referer", decodedRef)
                        } else if (trimmed.startsWith("headers=", ignoreCase = true)) {
                            val headersJson = trimmed.substringAfter("=").trim()
                            val decoded = try { java.net.URLDecoder.decode(headersJson, "UTF-8") } catch (_: Exception) { headersJson }
                            try {
                                val jsonObj = org.json.JSONObject(decoded)
                                val keys = jsonObj.keys()
                                while (keys.hasNext()) {
                                    val k = keys.next()
                                    builder.header(k, jsonObj.optString(k))
                                }
                            } catch (_: Exception) {}
                        } else if (trimmed.startsWith("decrypt=", ignoreCase = true)) {
                            decryptAlgo = trimmed.substringAfter("=").trim()
                        } else if (trimmed.startsWith("key=", ignoreCase = true)) {
                            decryptKey = trimmed.substringAfter("=").trim()
                        } else if (trimmed.startsWith("iv=", ignoreCase = true)) {
                            decryptIv = trimmed.substringAfter("=").trim()
                        }
                    }
                }

                // Generic Protocol: Bóc tách suffix pipe (|Referer=...)
                val pipeIdx = finalUrlStr.indexOf('|')
                if (pipeIdx != -1) {
                    val extra = finalUrlStr.substring(pipeIdx + 1)
                    finalUrlStr = finalUrlStr.substring(0, pipeIdx)
                    if (extra.startsWith("Referer=", ignoreCase = true)) {
                        val refVal = extra.substringAfter("=").trim()
                        val decodedRef = try { java.net.URLDecoder.decode(refVal, "UTF-8") } catch (_: Exception) { refVal }
                        builder.header("Referer", decodedRef)
                    }
                }

                if (originalRequest.header("User-Agent").isNullOrBlank()) {
                    builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                }

                if (finalUrlStr != originalUrlStr) {
                    builder.url(finalUrlStr)
                }

                val response = chain.proceed(builder.build())

                // Generic Protocol: Tự động giải mã nếu phát hiện tham số #decrypt=aes-128-cbc
                if (decryptAlgo.equals("aes-128-cbc", ignoreCase = true) && !decryptKey.isNullOrBlank() && !decryptIv.isNullOrBlank() && response.isSuccessful) {
                    val body = response.body
                    if (body != null) {
                        try {
                            val encBytes = body.bytes()
                            val cipher = javax.crypto.Cipher.getInstance("AES/CBC/NoPadding")
                            val keySpec = javax.crypto.spec.SecretKeySpec(decryptKey.toByteArray(Charsets.UTF_8), "AES")
                            val ivSpec = javax.crypto.spec.IvParameterSpec(decryptIv.toByteArray(Charsets.UTF_8))
                            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, ivSpec)
                            val decBytes = cipher.doFinal(encBytes)
                            val mediaType = "image/jpeg".toMediaTypeOrNull()
                            val newBody = decBytes.toResponseBody(mediaType)
                            return@addInterceptor response.newBuilder().body(newBody).build()
                        } catch (_: Exception) {}
                    }
                }

                response
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.35)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .allowHardware(true)
            .crossfade(false)
            .build()
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var database: AppDatabase
        private set

    lateinit var pluginManager: PluginManager
        private set

    lateinit var settingsManager: com.jkhx.player.settings.SettingsManager
        private set

    val searchHistoryManager: com.jkhx.player.settings.SearchHistoryManager by lazy {
        com.jkhx.player.settings.SearchHistoryManager(this)
    }

    val tmdbRepository: com.jkhx.player.core.network.tmdb.TmdbRepository by lazy {
        com.jkhx.player.core.network.tmdb.TmdbRepository()
    }

    val okHttpClient: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .proxySelector(object : java.net.ProxySelector() {
                override fun select(uri: java.net.URI?): List<java.net.Proxy> {
                    val proxy = settingsManager.getProxy()
                    return if (proxy != null) listOf(proxy) else listOf(java.net.Proxy.NO_PROXY)
                }
                override fun connectFailed(uri: java.net.URI?, sa: java.net.SocketAddress?, ioe: java.io.IOException?) {}
            })
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    val subtitleManager: com.jkhx.player.core.player.subtitle.SubtitleManager by lazy {
        com.jkhx.player.core.player.subtitle.SubtitleManager(
            context = this,
            okHttpClient = okHttpClient,
            openSubtitlesApiKeyProvider = { settingsManager.openSubtitlesApiKey.value },
            subdlApiKeyProvider = { settingsManager.subdlApiKey.value },
            subsourceApiKeyProvider = { settingsManager.subsourceApiKey.value },
            geminiApiKeyProvider = { settingsManager.geminiApiKey.value },
            geminiModelProvider = { settingsManager.geminiModel.value }
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        settingsManager = com.jkhx.player.settings.SettingsManager.getInstance(this)
        com.jkhx.player.core.network.HttpBridgeClient.globalProxyProvider = { settingsManager.getProxy() }
        com.jkhx.player.settings.LocaleHelper.updateConfiguration(this, settingsManager.language.value.code)
        database = AppDatabase.getInstance(this)
        val pluginsDir = File(filesDir, "plugins")

        pluginManager = PluginManager(
            pluginsDir = pluginsDir,
            pluginDao = database.pluginDao(),
            repositoryDao = database.repositoryDao(),
            stremioAddonDao = database.stremioAddonDao(),
            runtime = QuickJsRuntime(context = this)
        )

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityStarted(activity: android.app.Activity) {}
            override fun onActivityResumed(activity: android.app.Activity) { currentActivity = activity }
            override fun onActivityPaused(activity: android.app.Activity) { if (currentActivity === activity) currentActivity = null }
            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) { if (currentActivity === activity) currentActivity = null }
        })

        // Pure clean client: NO hardcoded/bundled plugins copied from assets!
        // Plugins are installed dynamically by user from manifest.json repositories.
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

        @JvmField
        var currentActivity: android.app.Activity? = null
    }
}
