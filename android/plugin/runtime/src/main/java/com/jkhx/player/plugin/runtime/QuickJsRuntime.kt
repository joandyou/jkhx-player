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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.serialization.json.*

class QuickJsRuntime(
    private val context: Context? = null,
    private val httpBridgeClient: HttpBridgeClient = HttpBridgeClient(),
    storage: PluginStorage? = null,
    private val logger: PluginLogger = DefaultPluginLogger(),
    private val memoryLimitBytes: Long = 64 * 1024 * 1024L,
    private val stackSizeBytes: Long = 0L
) : PluginRuntime {

    val storage: PluginStorage = storage ?: (if (context != null) SharedPreferencesPluginStorage(context) else DefaultPluginStorage())

    private val bridge = QuickJsBridge()
    private val rtPtr = bridge.nativeCreateRuntime(memoryLimitBytes, stackSizeBytes)
    private val ctxPtr = if (rtPtr != 0L) bridge.nativeCreateContext(rtPtr) else 0L
    private val runtimeMutex = Mutex()

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
                        "webview_sniff" -> handleWebviewSniff(payloadJson)
                        "storage_get" -> handleStorageGet(payloadJson)
                        "storage_set" -> handleStorageSet(payloadJson)
                        "storage_remove" -> handleStorageRemove(payloadJson)
                        "log_info" -> handleLogInfo(payloadJson)
                        "log_warn" -> handleLogWarn(payloadJson)
                        "log_error" -> handleLogError(payloadJson)
                        "ui_toast" -> handleUiToast(payloadJson)
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

    private fun handleUiToast(payloadJson: String): String {
        val root = json.parseToJsonElement(payloadJson).jsonObject
        val msg = root["msg"]?.jsonPrimitive?.content ?: ""
        val isLong = root["isLong"]?.jsonPrimitive?.booleanOrNull ?: false
        if (msg.isNotBlank()) {
            val appContext = context ?: runCatching {
                val appClass = Class.forName("com.jkhx.player.JkhxApplication")
                val field = appClass.getDeclaredField("instance")
                field.isAccessible = true
                field.get(null) as? Context
            }.getOrNull()

            if (appContext != null) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(appContext, msg, if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
                }
            }
        }
        return "true"
    }

    private fun handleWebviewSniff(payloadJson: String): String = runBlocking(Dispatchers.IO) {
        val root = json.parseToJsonElement(payloadJson).jsonObject
        val url = root["url"]?.jsonPrimitive?.content ?: ""
        val injectJs = root["injectJs"]?.jsonPrimitive?.content ?: ""
        val timeoutMs = root["timeoutMs"]?.jsonPrimitive?.longOrNull ?: 10000L

        val headersMap = mutableMapOf<String, String>()
        root["headers"]?.jsonObject?.forEach { (k, v) ->
            headersMap[k] = v.jsonPrimitive.content
        }

        val appContext = context ?: runCatching {
            val appClass = Class.forName("com.jkhx.player.JkhxApplication")
            val field = appClass.getDeclaredField("instance")
            field.isAccessible = true
            field.get(null) as? Context
        }.getOrNull()

        if (appContext == null) {
            return@runBlocking "null"
        }

        val sniffer = HeadlessWebviewSniffer(appContext)
        val result = sniffer.sniff(url, injectJs, timeoutMs, headersMap)
        result ?: "null"
    }

    private fun injectPolyfills() {
        val polyfillScript = """
            globalThis.__jkhx_plugins = {};
            globalThis.__jkhx_plugin_domains = {};
            globalThis.currentPluginId = "unknown";

            Object.defineProperty(globalThis, 'CUSTOM_DOMAIN', {
                get: function() {
                    return (globalThis.__jkhx_plugin_domains && globalThis.currentPluginId)
                        ? (globalThis.__jkhx_plugin_domains[globalThis.currentPluginId] || undefined)
                        : undefined;
                },
                set: function(val) {
                    if (globalThis.__jkhx_plugin_domains && globalThis.currentPluginId) {
                        if (val) {
                            globalThis.__jkhx_plugin_domains[globalThis.currentPluginId] = String(val);
                        } else {
                            delete globalThis.__jkhx_plugin_domains[globalThis.currentPluginId];
                        }
                    }
                },
                configurable: true,
                enumerable: true
            });

            Object.defineProperty(globalThis, 'BASE_URL', {
                get: function() {
                    return (globalThis.__jkhx_plugin_domains && globalThis.currentPluginId)
                        ? (globalThis.__jkhx_plugin_domains[globalThis.currentPluginId] || undefined)
                        : undefined;
                },
                set: function(val) {
                    if (globalThis.__jkhx_plugin_domains && globalThis.currentPluginId) {
                        if (val) {
                            globalThis.__jkhx_plugin_domains[globalThis.currentPluginId] = String(val);
                        } else {
                            delete globalThis.__jkhx_plugin_domains[globalThis.currentPluginId];
                        }
                    }
                },
                configurable: true,
                enumerable: true
            });

            globalThis.fetch = async function(url, options = {}) {
                const payload = {
                    pluginId: globalThis.currentPluginId,
                    url: url,
                    options: options
                };
                const raw = globalThis.__native_dispatch("fetch", JSON.stringify(payload));
                if (!raw) throw new Error("Network request failed");
                const rawRes = typeof raw === "string" ? JSON.parse(raw) : raw;
                if (rawRes.error) {
                    throw new Error(rawRes.error);
                }
                return {
                    status: rawRes.status,
                    ok: Boolean(rawRes.ok),
                    statusText: rawRes.statusText || "",
                    text: async () => rawRes.body || "",
                    json: async () => JSON.parse(rawRes.body || "{}"),
                    headers: {
                        get: (name) => (rawRes.headers && name) ? rawRes.headers[name.toLowerCase()] : null
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
                },
                webview: {
                    sniff: async (options = {}) => {
                        const payload = {
                            pluginId: globalThis.currentPluginId,
                            url: options.url || "",
                            injectJs: options.injectJs || "",
                            timeoutMs: options.timeoutMs || 10000,
                            headers: options.headers || {}
                        };
                        const raw = globalThis.__native_dispatch("webview_sniff", JSON.stringify(payload));
                        if (!raw || raw === "null") return null;
                        try {
                            return JSON.parse(raw);
                        } catch (e) {
                            return raw;
                        }
                    }
                },
                ui: {
                    showToast: (msg, isLong = false) => {
                        if (!msg) return;
                        globalThis.__native_dispatch("ui_toast", JSON.stringify({ msg: String(msg), isLong: Boolean(isLong) }));
                    }
                }
            };

            if (typeof globalThis.btoa === 'undefined') {
                const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';
                globalThis.btoa = function(input) {
                    const str = String(input);
                    let output = '';
                    for (let block = 0, charCode, i = 0, map = chars;
                         str.charAt(i | 0) || (map = '=', i % 1);
                         output += map.charAt(63 & block >> 8 - i % 1 * 8)) {
                        charCode = str.charCodeAt(i += 3/4);
                        if (charCode > 0xFF) {
                            throw new Error("'btoa' failed: The string to be encoded contains characters outside of the Latin1 range.");
                        }
                        block = block << 8 | charCode;
                    }
                    return output;
                };
            }

            if (typeof globalThis.atob === 'undefined') {
                const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';
                globalThis.atob = function(input) {
                    const str = String(input).replace(/[=]+$/, '');
                    if (str.length % 4 === 1) throw new Error("'atob' failed: The string to be decoded is not correctly encoded.");
                    let output = '';
                    for (let bc = 0, bs = 0, buffer, i = 0;
                         buffer = str.charAt(i++);
                         ~buffer && (bs = bc % 4 ? bs * 64 + buffer : buffer,
                           bc++ % 4) ? output += String.fromCharCode(255 & bs >> (-2 * bc & 6)) : 0
                    ) {
                        buffer = chars.indexOf(buffer);
                    }
                    return output;
                };
            }

            globalThis.console = {
                log: (...args) => globalThis.context.log.info(...args),
                warn: (...args) => globalThis.context.log.warn(...args),
                error: (...args) => globalThis.context.log.error(...args)
            };

            if (typeof globalThis.URLSearchParams === 'undefined') {
                globalThis.URLSearchParams = class {
                    constructor(init) {
                        this.params = {};
                        if (typeof init === 'string') {
                            const str = init.startsWith('?') ? init.slice(1) : init;
                            if (str) {
                                for (const pair of str.split('&')) {
                                    if (!pair) continue;
                                    const idx = pair.indexOf('=');
                                    const k = idx >= 0 ? decodeURIComponent(pair.substring(0, idx)) : decodeURIComponent(pair);
                                    const v = idx >= 0 ? decodeURIComponent(pair.substring(idx + 1)) : '';
                                    this.append(k, v);
                                }
                            }
                        } else if (init && typeof init === 'object') {
                            for (const [k, v] of Object.entries(init)) {
                                if (v !== undefined && v !== null) {
                                    this.append(k, String(v));
                                }
                            }
                        }
                    }
                    append(name, value) {
                        if (!this.params[name]) this.params[name] = [];
                        this.params[name].push(String(value));
                    }
                    set(name, value) {
                        this.params[name] = [String(value)];
                    }
                    get(name) {
                        return this.params[name] ? this.params[name][0] : null;
                    }
                    getAll(name) {
                        return this.params[name] || [];
                    }
                    has(name) {
                        return Boolean(this.params[name]);
                    }
                    delete(name) {
                        delete this.params[name];
                    }
                    toString() {
                        const pairs = [];
                        for (const [k, values] of Object.entries(this.params)) {
                            for (const v of values) {
                                pairs.push(encodeURIComponent(k) + '=' + encodeURIComponent(v));
                            }
                        }
                        return pairs.join('&');
                    }
                };
            }

            // Thư viện Extractor trong QuickJS Sandbox dùng chung cho các plugins
            globalThis.extractors = {
                unpack: function(packedJs) {
                    if (!packedJs || typeof packedJs !== 'string') return "";
                    try {
                        const regex = /eval\(function\(p,a,c,k,e,[rd]\)\{.*\}\('(.*)',(\d+),(\d+),'(.*)'\.split\('\|'\)/;
                        const match = packedJs.match(regex);
                        if (!match) return packedJs;
                        let [, payload, radixStr, countStr, symsStr] = match;
                        let radix = parseInt(radixStr, 10);
                        let count = parseInt(countStr, 10);
                        let syms = symsStr.split('|');
                        function lookup(c) {
                            return (c < radix ? '' : lookup(Math.floor(c / radix))) +
                                ((c = c % radix) > 35 ? String.fromCharCode(c + 29) : c.toString(36));
                        }
                        while (count--) {
                            if (syms[count]) {
                                payload = payload.replace(new RegExp('\\b' + lookup(count) + '\\b', 'g'), syms[count]);
                            }
                        }
                        return payload;
                    } catch (e) {
                        return packedJs;
                    }
                }
            };
        """.trimIndent()
        bridge.nativeEval(ctxPtr, polyfillScript, "polyfills.js")
    }

    override suspend fun loadPlugin(
        manifest: PluginManifest,
        scriptContent: String
    ): LoadedPlugin = withContext(Dispatchers.Default) {
        val pluginId = manifest.id

        val domainSetupCode = if (!manifest.customDomain.isNullOrBlank()) {
            """globalThis.__jkhx_plugin_domains["$pluginId"] = "${manifest.customDomain}";"""
        } else {
            """delete globalThis.__jkhx_plugin_domains["$pluginId"];"""
        }

        val transformedScript = scriptContent
            .replaceFirst(Regex("""export\s+default\s+"""), "globalThis.__jkhx_plugins[\"$pluginId\"] = ")

        val wrapScript = """
            (() => {
                globalThis.currentPluginId = "$pluginId";
                $domainSetupCode
                const module = { exports: {} };
                const exports = module.exports;
                $transformedScript
                if (module.exports && (module.exports.getStreams || module.exports.getHome || module.exports.getDetail)) {
                    globalThis.__jkhx_plugins["$pluginId"] = module.exports;
                } else if (globalThis.getStreams && !globalThis.__jkhx_plugins["$pluginId"]) {
                    globalThis.__jkhx_plugins["$pluginId"] = { getStreams: globalThis.getStreams };
                }
            })();
        """.trimIndent()

        runtimeMutex.withLock {
            bridge.nativeSetTimeout(rtPtr, 15000L)
            try {
                bridge.nativeEval(ctxPtr, wrapScript, "${manifest.id}.js")
            } catch (e: Exception) {
                throw PluginLoadError(manifest.id, e.message ?: "Failed to evaluate script", e)
            } finally {
                bridge.nativeSetTimeout(rtPtr, 0L)
            }
        }

        LoadedPluginImpl(
            manifest = manifest,
            json = json,
            bridge = bridge,
            rtPtr = rtPtr,
            ctxPtr = ctxPtr,
            runtimeMutex = runtimeMutex,
            httpBridgeClient = httpBridgeClient
        )
    }

    override fun close() {
        runBlocking {
            runtimeMutex.withLock {
                if (ctxPtr != 0L) bridge.nativeDestroyContext(ctxPtr)
                if (rtPtr != 0L) bridge.nativeDestroyRuntime(rtPtr)
            }
        }
    }
}

class LoadedPluginImpl(
    private val manifest: PluginManifest,
    private val json: Json,
    private val bridge: QuickJsBridge,
    private val rtPtr: Long,
    private val ctxPtr: Long,
    private val runtimeMutex: Mutex,
    private val httpBridgeClient: HttpBridgeClient
) : LoadedPlugin {

    override val id: String = manifest.id
    override val name: String = manifest.name
    override val version: String = manifest.version

    // Bộ nhớ đệm in-memory cho getMenu và getFilters
    private var cachedMenu: List<MenuItem>? = null
    private var cachedFilters: PluginFilterConfig? = null

    private suspend fun <T> invokeMethod(
        methodName: String,
        argsJson: String = "[]",
        timeoutMs: Long = 15000L,
        deserializer: (String) -> T
    ): T = withContext(Dispatchers.Default) {
        val domainSetupCode = if (!manifest.customDomain.isNullOrBlank()) {
            """globalThis.__jkhx_plugin_domains["${manifest.id}"] = "${manifest.customDomain}";"""
        } else {
            """delete globalThis.__jkhx_plugin_domains["${manifest.id}"];"""
        }

        runtimeMutex.withLock {
            bridge.nativeSetTimeout(rtPtr, timeoutMs)
            val script = """
                (async () => {
                    globalThis.currentPluginId = "${manifest.id}";
                    $domainSetupCode
                    const p = globalThis.__jkhx_plugins["${manifest.id}"];
                    if (!p) throw new Error("Plugin not registered");
                    let res;
                    if (typeof p["$methodName"] === 'function') {
                        res = await p["$methodName"](...$argsJson);
                    } else if ("$methodName" === "getStream" && typeof p["getStreams"] === 'function') {
                        const ep = ($argsJson && $argsJson[0]) || {};
                        const streams = await p["getStreams"](ep.slug || ep.id, "movie", ep.season || 1, ep.number || 1, {});
                        if (Array.isArray(streams) && streams.length > 0) {
                            const s = streams[0];
                            res = {
                                type: s.type || "m3u8",
                                url: s.url,
                                headers: s.headers || {},
                                subtitles: (s.subtitles || []).map(sub => ({
                                    url: sub.url,
                                    label: sub.label || sub.language || "vi",
                                    language: sub.language || "vi",
                                    format: sub.format || "vtt"
                                }))
                            };
                        } else {
                            throw new Error("No playable streams found");
                        }
                    } else {
                        throw new Error("Method '$methodName' not found on plugin");
                    }
                    return JSON.stringify(res);
                })()
            """.trimIndent()

            try {
                val rawRes = bridge.nativeEval(ctxPtr, script, "${manifest.id}_$methodName.js")
                    ?: throw PluginExecutionError(manifest.id, methodName, "Empty response from plugin")

                val unquoted = if (rawRes.startsWith("\"") && rawRes.endsWith("\"") && rawRes.length >= 2) {
                    try {
                        json.parseToJsonElement(rawRes).jsonPrimitive.content
                    } catch (e: Exception) {
                        rawRes
                    }
                } else {
                    rawRes
                }
                deserializer(unquoted)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    cancelTasks()
                    throw e
                }
                if (e.message?.contains("timeout", ignoreCase = true) == true) {
                    throw PluginTimeout(manifest.id, methodName, timeoutMs)
                }
                throw PluginExecutionError(manifest.id, methodName, e.message ?: "Unknown error", e)
            } finally {
                bridge.nativeSetTimeout(rtPtr, 0L)
            }
        }
    }

    override suspend fun getHome(): HomeResult {
        return invokeMethod("getHome", timeoutMs = 30000L) { raw ->
            val trimmed = raw.trim()
            if (trimmed.startsWith("[")) {
                val sections = json.decodeFromString<List<HomeSection>>(trimmed)
                HomeResult(sections = sections)
            } else {
                json.decodeFromString(HomeResult.serializer(), trimmed)
            }
        }
    }

    override suspend fun search(keyword: String, page: Int): SearchResult {
        val args = buildJsonArray {
            add(keyword)
            add(page)
        }.toString()
        return invokeMethod("search", args, timeoutMs = 20000L) { raw ->
            val trimmed = raw.trim()
            if (trimmed.startsWith("[")) {
                val items = json.decodeFromString<List<MovieItem>>(trimmed)
                SearchResult(items = items, page = page, hasNext = false)
            } else {
                json.decodeFromString(SearchResult.serializer(), trimmed)
            }
        }
    }

    override suspend fun getDetail(slug: String): MovieDetail {
        val args = buildJsonArray { add(slug) }.toString()
        return invokeMethod("getDetail", args, timeoutMs = 20000L) { raw ->
            json.decodeFromString(MovieDetail.serializer(), raw.trim())
        }
    }

    override suspend fun getStream(episodeData: EpisodeData): EpisodeStream {
        val epJson = json.encodeToString(EpisodeData.serializer(), episodeData)
        val args = "[$epJson]"
        return invokeMethod("getStream", args, timeoutMs = 20000L) { raw ->
            json.decodeFromString(EpisodeStream.serializer(), raw.trim())
        }
    }

    override suspend fun getList(
        type: String?,
        slug: String?,
        page: Int,
        filters: Map<String, String>?
    ): SearchResult {
        val args = buildJsonArray {
            add(type ?: "")
            add(slug ?: "")
            add(page)
            if (filters != null) {
                add(buildJsonObject {
                    filters.forEach { (k, v) -> put(k, v) }
                })
            }
        }.toString()
        return try {
            invokeMethod("getList", args, timeoutMs = 25000L) { raw ->
                val trimmed = raw.trim()
                if (trimmed.startsWith("[")) {
                    val items = json.decodeFromString<List<MovieItem>>(trimmed)
                    SearchResult(items = items, page = page, hasNext = items.isNotEmpty())
                } else {
                    json.decodeFromString(SearchResult.serializer(), trimmed)
                }
            }
        } catch (e: Exception) {
            val query = slug ?: type ?: ""
            if (query.isNotBlank()) {
                search(query, page)
            } else {
                SearchResult(items = emptyList(), page = page, hasNext = false)
            }
        }
    }

    override suspend fun getFilters(forceRefresh: Boolean): PluginFilterConfig? {
        if (!forceRefresh && cachedFilters != null) {
            return cachedFilters
        }
        return try {
            val result = invokeMethod("getFilters", timeoutMs = 15000L) { raw ->
                val trimmed = raw.trim()
                if (trimmed.isEmpty() || trimmed == "null" || trimmed == "undefined") null
                else json.decodeFromString(PluginFilterConfig.serializer(), trimmed)
            }
            if (result != null) cachedFilters = result
            result
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    override suspend fun getMenu(forceRefresh: Boolean): List<MenuItem>? {
        if (!forceRefresh && cachedMenu != null) {
            return cachedMenu
        }
        return try {
            val result = invokeMethod("getMenu", timeoutMs = 15000L) { raw ->
                val trimmed = raw.trim()
                if (trimmed.isEmpty() || trimmed == "null" || trimmed == "undefined") null
                else if (trimmed.startsWith("[")) {
                    json.decodeFromString<List<MenuItem>>(trimmed)
                } else {
                    null
                }
            }
            if (result != null) cachedMenu = result
            result
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    override suspend fun getSettings(): List<PluginSettingItem>? {
        return try {
            invokeMethod("getSettings", timeoutMs = 10000L) { raw ->
                val trimmed = raw.trim()
                if (trimmed.isEmpty() || trimmed == "null" || trimmed == "undefined") null
                else json.decodeFromString<List<PluginSettingItem>>(trimmed)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    override suspend fun checkHealth(): PluginHealthStatus? {
        return try {
            invokeMethod("checkHealth", timeoutMs = 10000L) { raw ->
                val trimmed = raw.trim()
                if (trimmed.isEmpty() || trimmed == "null" || trimmed == "undefined") null
                else json.decodeFromString(PluginHealthStatus.serializer(), trimmed)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    override fun clearCache() {
        cachedMenu = null
        cachedFilters = null
    }

    override fun cancelTasks() {
        httpBridgeClient.cancelCalls(manifest.id)
    }

    override fun close() {
        cancelTasks()
        clearCache()
        // Clear plugin reference & custom domain routing
        runBlocking {
            runtimeMutex.withLock {
                bridge.nativeEval(
                    ctxPtr,
                    """
                    delete globalThis.__jkhx_plugins["${manifest.id}"];
                    if (globalThis.__jkhx_plugin_domains) {
                        delete globalThis.__jkhx_plugin_domains["${manifest.id}"];
                    }
                    """.trimIndent(),
                    "cleanup.js"
                )
            }
        }
    }
}

class SharedPreferencesPluginStorage(private val context: Context) : PluginStorage {
    private val prefs = context.getSharedPreferences("jkhx_plugin_storage", Context.MODE_PRIVATE)
    override fun get(pluginId: String, key: String): String? = prefs.getString("$pluginId:$key", null)
    override fun set(pluginId: String, key: String, value: String) {
        prefs.edit().putString("$pluginId:$key", value).apply()
    }
    override fun remove(pluginId: String, key: String) {
        prefs.edit().remove("$pluginId:$key").apply()
    }
    override fun clear(pluginId: String) {
        val prefix = "$pluginId:"
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
    }
}

class DefaultPluginStorage : PluginStorage {
    private val memory = mutableMapOf<String, String>()
    override fun get(pluginId: String, key: String): String? = memory["$pluginId:$key"]
    override fun set(pluginId: String, key: String, value: String) { memory["$pluginId:$key"] = value }
    override fun remove(pluginId: String, key: String) { memory.remove("$pluginId:$key") }
    override fun clear(pluginId: String) {
        val prefix = "$pluginId:"
        val keysToRemove = memory.keys.filter { it.startsWith(prefix) }
        keysToRemove.forEach { memory.remove(it) }
    }
}

class DefaultPluginLogger : PluginLogger {
    override fun info(pluginId: String, message: String) = println("[$pluginId INFO] $message")
    override fun warn(pluginId: String, message: String) = println("[$pluginId WARN] $message")
    override fun error(pluginId: String, message: String, throwable: Throwable?) =
        println("[$pluginId ERROR] $message ${throwable?.message ?: ""}")
}

