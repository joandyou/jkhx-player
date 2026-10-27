package com.jkhx.player.core.network

import com.jkhx.player.core.model.PluginNetworkError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit

data class FetchRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val timeoutMs: Long = 15000L
)

data class FetchResponse(
    val status: Int,
    val ok: Boolean,
    val statusText: String,
    val headers: Map<String, String>,
    val body: String
)

class HttpBridgeClient(
    private val defaultClient: OkHttpClient = SslHelper.configureSsl(
        OkHttpClient.Builder()
            .dns(object : Dns {
                override fun lookup(hostname: String): List<java.net.InetAddress> =
                    Dns.SYSTEM.lookup(hostname).sortedBy { it is java.net.Inet6Address }
            })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .proxySelector(object : ProxySelector() {
                override fun select(uri: URI?): List<Proxy> {
                    val proxy = globalProxyProvider?.invoke()
                    return if (proxy != null) listOf(proxy) else listOf(Proxy.NO_PROXY)
                }
                override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {}
            })
    ).build()
) {
    companion object {
        @Volatile
        var globalProxyProvider: (() -> Proxy?)? = null
    }

    private val activeCalls = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap.KeySetView<Call, Boolean>>()

    /**
     * Hủy tất cả các network call đang chạy của một plugin cụ thể
     */
    fun cancelCalls(pluginId: String) {
        activeCalls[pluginId]?.forEach { call ->
            try { call.cancel() } catch (_: Exception) {}
        }
        activeCalls.remove(pluginId)
    }

    /**
     * Hủy tất cả network call đang chạy của tất cả plugins
     */
    fun cancelAllCalls() {
        activeCalls.values.forEach { set ->
            set.forEach { call ->
                try { call.cancel() } catch (_: Exception) {}
            }
        }
        activeCalls.clear()
    }

    suspend fun execute(pluginId: String, request: FetchRequest): FetchResponse = withContext(Dispatchers.IO) {
        val client = if (request.timeoutMs != 15000L) {
            defaultClient.newBuilder()
                .connectTimeout(request.timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(request.timeoutMs, TimeUnit.MILLISECONDS)
                .build()
        } else {
            defaultClient
        }

        val requestBuilder = Request.Builder().url(request.url)

        // Set default headers if missing
        val finalHeaders = request.headers.toMutableMap()
        if (!finalHeaders.keys.any { it.equals("User-Agent", ignoreCase = true) }) {
            finalHeaders["User-Agent"] = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        }

        finalHeaders.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }

        when (request.method.uppercase()) {
            "GET" -> requestBuilder.get()
            "HEAD" -> requestBuilder.head()
            "POST" -> {
                val contentType = finalHeaders["Content-Type"] ?: "application/json; charset=utf-8"
                val requestBody = (request.body ?: "").toRequestBody(contentType.toMediaTypeOrNull())
                requestBuilder.post(requestBody)
            }
            "PUT" -> {
                val contentType = finalHeaders["Content-Type"] ?: "application/json; charset=utf-8"
                val requestBody = (request.body ?: "").toRequestBody(contentType.toMediaTypeOrNull())
                requestBuilder.put(requestBody)
            }
            "DELETE" -> requestBuilder.delete()
            else -> requestBuilder.get()
        }

        val call = client.newCall(requestBuilder.build())
        val callSet = activeCalls.computeIfAbsent(pluginId) { java.util.concurrent.ConcurrentHashMap.newKeySet() }
        callSet.add(call)

        try {
            val response = call.execute()
            val responseBody = response.body?.string() ?: ""

            val respHeaders = mutableMapOf<String, String>()
            for (i in 0 until response.headers.size) {
                respHeaders[response.headers.name(i).lowercase()] = response.headers.value(i)
            }

            FetchResponse(
                status = response.code,
                ok = response.isSuccessful,
                statusText = response.message,
                headers = respHeaders,
                body = responseBody
            )
        } catch (e: IOException) {
            throw PluginNetworkError(pluginId, request.url, e.message ?: "Unknown IO error", e)
        } finally {
            callSet.remove(call)
        }
    }
}

