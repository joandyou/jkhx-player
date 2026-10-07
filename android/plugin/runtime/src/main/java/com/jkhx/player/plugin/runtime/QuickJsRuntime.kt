package com.jkhx.player.plugin.runtime

import com.jkhx.player.core.model.*
import com.jkhx.player.core.network.FetchRequest
import com.jkhx.player.core.network.HttpBridgeClient
import com.jkhx.player.plugin.api.LoadedPlugin
import com.jkhx.player.plugin.api.PluginLogger
import com.jkhx.player.plugin.api.PluginRuntime
import com.jkhx.player.plugin.api.PluginStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class QuickJsRuntime(
    private val httpBridgeClient: HttpBridgeClient = HttpBridgeClient(),
    private val storage: PluginStorage = DefaultPluginStorage(),
    private val logger: PluginLogger = DefaultPluginLogger(),
    private val memoryLimitBytes: Long = 64 * 1024 * 1024L,
    private val stackSizeBytes: Long = 1024 * 1024L
) : PluginRuntime {

    private val bridge = QuickJsBridge()
    private val rtPtr = bridge.nativeCreateRuntime(memoryLimitBytes, stackSizeBytes)
    private val ctxPtr = if (rtPtr != 0L) bridge.nativeCreateContext(rtPtr) else 0L

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    init {
        if (ctxPtr != 0L) {
            setupBridgeHandler()
            injectPolyfills()
        }
    }

    private fun setupBridgeHandler() {
        val handler = object {
            @Suppress("unused")
            fun onNativeCall(action: String, payloadJson: String): String {
                return try {
                    when (action) {
                        "fetch" -> handleFetch(payloadJson)
                        "storage_get" -> handleStorageGet(payloadJson)
                        "storage_set" -> handleStorageSet(payloadJson)
                        "storage_remove" -> handleStorageRemove(payloadJson)
                        "log_info" -> handleLogInfo(payloadJson)
                        "log_warn" -> handleLogWarn(payloadJson)
                        "log_error" -> handleLogError(payloadJson)
                        else -> "null"
                    }
                } catch (e: Exception) {
                    """{"error": "${e.message?.replace("\"", "\\\"")}"}"""
                }
            }
        }
        bridge.nativeRegisterBridge(ctxPtr, handler)
    }

    private fun handleFetch(payloadJson: String): String = runBlocking(Dispatchers.IO) {
        val root = json.parseToJsonElement(payloadJson).jsonObject
        val pluginId = root["pluginId"]?.jsonPrimitive?.content ?: "unknown"
        val url = root["url"]?.jsonPrimitive?.content ?: ""
        val options = root["options"]?.jsonObject

        val method = options?.get("method")?.jsonPrimitive?.content ?: "GET"
        val body = options?.get("body")?.jsonPrimitive?.contentOrNull
        val timeoutMs = options?.get("timeout")?.jsonPrimitive?.longOrNull ?: 15000L

        val headersMap = mutableMapOf<String, String>()
        options?.get("headers")?.jsonObject?.forEach { (k, v) ->
            headersMap[k] = v.jsonPrimitive.content
        }

        val res = httpBridgeClient.execute(
            pluginId,
            FetchRequest(
                url = url,
                method = method,
                headers = headersMap,
                body = body,
                timeoutMs = timeoutMs
            )
        )

        buildJsonObject {
            put("status", res.status)
            put("ok", res.ok)
            put("statusText", res.statusText)
            put("body", res.body)
            put("headers", buildJsonObject {
                res.headers.forEach { (k, v) -> put(k, v) }
            })
        }.toString()
    }

    private fun handleStorageGet(payloadJson: String): String {
        val root = json.parseToJsonElement(payloadJson).jsonObject
        val pluginId = root["pluginId"]?.jsonPrimitive?.content ?: ""
        val key = root["key"]?.jsonPrimitive?.content ?: ""
        val value = storage.get(pluginId, key)
        return if (value != null) "\"$value\"" else "null"
    }

    private fun handleStorageSet(payloadJson: String): String {
        val root = json.parseToJsonElement(payloadJson).jsonObject
        val pluginId = root["pluginId"]?.jsonPrimitive?.content ?: ""
        val key = root["key"]?.jsonPrimitive?.content ?: ""
        val value = root["value"]?.jsonPrimitive?.content ?: ""
        storage.set(pluginId, key, value)
        return "true"
    }

    private fun handleStorageRemove(payloadJson: String): String {
        val root = json.parseToJsonElement(payloadJson).jsonObject
        val pluginId = root["pluginId"]?.jsonPrimitive?.content ?: ""
        val key = root["key"]?.jsonPrimitive?.content ?: ""
        storage.remove(pluginId, key)
        return "true"
    }

    private fun handleLogInfo(payloadJson: String): String {
        val root = json.parseToJsonElement(payloadJson).jsonObject
        val pluginId = root["pluginId"]?.jsonPrimitive?.content ?: ""
        val msg = root["msg"]?.jsonPrimitive?.content ?: ""
        logger.info(pluginId, msg)
        return "true"
    }

    private fun handleLogWarn(payloadJson: String): String {
        val root = json.parseToJsonElement(payloadJson).jsonObject
        val pluginId = root["pluginId"]?.jsonPrimitive?.content ?: ""
        val msg = root["msg"]?.jsonPrimitive?.content ?: ""
        logger.warn(pluginId, msg)
        return "true"
    }

    private fun handleLogError(payloadJson: String): String {
        val root = json.parseToJsonElement(payloadJson).jsonObject
        val pluginId = root["pluginId"]?.jsonPrimitive?.content ?: ""
        val msg = root["msg"]?.jsonPrimitive?.content ?: ""
        logger.error(pluginId, msg)
        return "true"
    }

    private fun injectPolyfills() {
        val polyfillScript = """
            globalThis.__jkhx_plugins = {};
            globalThis.currentPluginId = "unknown";

            globalThis.fetch = async function(url, options = {}) {
                const payload = {
                    pluginId: globalThis.currentPluginId,
                    url: url,
                    options: options
                };
                const rawRes = globalThis.__native_dispatch("fetch", JSON.stringify(payload));
                if (!rawRes) throw new Error("Network request failed");
                return {
                    status: rawRes.status,
                    ok: rawRes.ok,
                    statusText: rawRes.statusText,
                    text: async () => rawRes.body,
                    json: async () => JSON.parse(rawRes.body),
                    headers: {
                        get: (name) => rawRes.headers ? rawRes.headers[name.toLowerCase()] : null
                    }
                };
            };

            globalThis.context = {
                storage: {
                    get: (key) => globalThis.__native_dispatch("storage_get", JSON.stringify({ pluginId: globalThis.currentPluginId, key })),
                    set: (key, value) => globalThis.__native_dispatch("storage_set", JSON.stringify({ pluginId: globalThis.currentPluginId, key, value })),
                    remove: (key) => globalThis.__native_dispatch("storage_remove", JSON.stringify({ pluginId: globalThis.currentPluginId, key }))
                },
                log: {
                    info: (...args) => globalThis.__native_dispatch("log_info", JSON.stringify({ pluginId: globalThis.currentPluginId, msg: args.map(a => typeof a === 'object' ? JSON.stringify(a) : String(a)).join(' ') })),
                    warn: (...args) => globalThis.__native_dispatch("log_warn", JSON.stringify({ pluginId: globalThis.currentPluginId, msg: args.map(a => typeof a === 'object' ? JSON.stringify(a) : String(a)).join(' ') })),
                    error: (...args) => globalThis.__native_dispatch("log_error", JSON.stringify({ pluginId: globalThis.currentPluginId, msg: args.map(a => typeof a === 'object' ? JSON.stringify(a) : String(a)).join(' ') }))
                }
            };

            globalThis.console = {
                log: (...args) => globalThis.context.log.info(...args),
                warn: (...args) => globalThis.context.log.warn(...args),
                error: (...args) => globalThis.context.log.error(...args)
            };
        """.trimIndent()
        bridge.nativeEval(ctxPtr, polyfillScript, "polyfills.js")
    }

    override suspend fun loadPlugin(
        manifest: PluginManifest,
        scriptContent: String
    ): LoadedPlugin = withContext(Dispatchers.Default) {
        val pluginId = manifest.id

        // Transform `export default` to `globalThis.__jkhx_plugins["<id>"] =`
        val transformedScript = scriptContent
            .replaceFirst(Regex("""export\s+default\s+"""), "globalThis.__jkhx_plugins[\"$pluginId\"] = ")

        val wrapScript = """
            globalThis.currentPluginId = "$pluginId";
            $transformedScript
        """.trimIndent()

        bridge.nativeSetTimeout(rtPtr, 15000L)
        try {
            bridge.nativeEval(ctxPtr, wrapScript, "${manifest.id}.js")
        } catch (e: Exception) {
            throw PluginLoadError(manifest.id, e.message ?: "Failed to evaluate script", e)
        } finally {
            bridge.nativeSetTimeout(rtPtr, 0L)
        }

        LoadedPluginImpl(
            manifest = manifest,
            json = json,
            bridge = bridge,
            rtPtr = rtPtr,
            ctxPtr = ctxPtr
        )
    }

    override fun close() {
        if (ctxPtr != 0L) bridge.nativeDestroyContext(ctxPtr)
        if (rtPtr != 0L) bridge.nativeDestroyRuntime(rtPtr)
    }
}

class LoadedPluginImpl(
    private val manifest: PluginManifest,
    private val json: Json,
    private val bridge: QuickJsBridge,
    private val rtPtr: Long,
    private val ctxPtr: Long
) : LoadedPlugin {

    override val id: String = manifest.id
    override val name: String = manifest.name
    override val version: String = manifest.version

    private suspend fun <T> invokeMethod(
        methodName: String,
        argsJson: String = "[]",
        timeoutMs: Long = 15000L,
        deserializer: (String) -> T
    ): T = withContext(Dispatchers.Default) {
        bridge.nativeSetTimeout(rtPtr, timeoutMs)
        val script = """
            (async () => {
                globalThis.currentPluginId = "${manifest.id}";
                const p = globalThis.__jkhx_plugins["${manifest.id}"];
                if (!p) throw new Error("Plugin not registered");
                if (typeof p["$methodName"] !== 'function') throw new Error("Method '$methodName' not found on plugin");
                const res = await p["$methodName"](...$argsJson);
                return JSON.stringify(res);
            })()
        """.trimIndent()

        try {
            val rawRes = bridge.nativeEval(ctxPtr, script, "${manifest.id}_$methodName.js")
                ?: throw PluginExecutionError(manifest.id, methodName, "Empty response from plugin")

            // The result from nativeEval is a JSON-stringified string if it was returned by JSON.stringify
            val unquoted = if (rawRes.startsWith("\"") && rawRes.endsWith("\"")) {
                json.parseToJsonElement(rawRes).jsonPrimitive.content
            } else {
                rawRes
            }
            deserializer(unquoted)
        } catch (e: Exception) {
            if (e.message?.contains("timeout", ignoreCase = true) == true) {
                throw PluginTimeout(manifest.id, methodName, timeoutMs)
            }
            throw PluginExecutionError(manifest.id, methodName, e.message ?: "Unknown error", e)
        } finally {
            bridge.nativeSetTimeout(rtPtr, 0L)
        }
    }

    override suspend fun getHome(): HomeResult {
        return invokeMethod("getHome") { raw ->
            json.decodeFromString(HomeResult.serializer(), raw)
        }
    }

    override suspend fun search(keyword: String, page: Int): SearchResult {
        val args = json.encodeToString(buildJsonArray {
            add(keyword)
            add(page)
        })
        return invokeMethod("search", args) { raw ->
            json.decodeFromString(SearchResult.serializer(), raw)
        }
    }

    override suspend fun getDetail(slug: String): MovieDetail {
        val args = json.encodeToString(buildJsonArray { add(slug) })
        return invokeMethod("getDetail", args) { raw ->
            json.decodeFromString(MovieDetail.serializer(), raw)
        }
    }

    override suspend fun getStream(episodeData: EpisodeData): EpisodeStream {
        val epJson = json.encodeToString(EpisodeData.serializer(), episodeData)
        val args = "[$epJson]"
        return invokeMethod("getStream", args) { raw ->
            json.decodeFromString(EpisodeStream.serializer(), raw)
        }
    }

    override fun close() {
        // Clear plugin reference
        bridge.nativeEval(ctxPtr, "delete globalThis.__jkhx_plugins[\"${manifest.id}\"];", "cleanup.js")
    }
}

class DefaultPluginStorage : PluginStorage {
    private val memory = mutableMapOf<String, String>()
    override fun get(pluginId: String, key: String): String? = memory["$pluginId:$key"]
    override fun set(pluginId: String, key: String, value: String) { memory["$pluginId:$key"] = value }
    override fun remove(pluginId: String, key: String) { memory.remove("$pluginId:$key") }
}

class DefaultPluginLogger : PluginLogger {
    override fun info(pluginId: String, message: String) = println("[$pluginId INFO] $message")
    override fun warn(pluginId: String, message: String) = println("[$pluginId WARN] $message")
    override fun error(pluginId: String, message: String, throwable: Throwable?) =
        println("[$pluginId ERROR] $message ${throwable?.message ?: ""}")
}
