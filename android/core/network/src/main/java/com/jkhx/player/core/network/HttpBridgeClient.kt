package com.jkhx.player.core.network

import com.jkhx.player.core.model.PluginNetworkError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
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
    private val defaultClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
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

        try {
            val response = client.newCall(requestBuilder.build()).execute()
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
        }
    }
}
