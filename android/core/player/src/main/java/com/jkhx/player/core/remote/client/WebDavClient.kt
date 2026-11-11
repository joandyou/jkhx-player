package com.jkhx.player.core.remote.client

import com.jkhx.player.core.remote.model.RemoteFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.StringReader
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

class WebDavClient(
    val host: String,
    val port: Int,
    val useSsl: Boolean = false,
    val basePath: String = "/",
    val username: String? = null,
    val password: String? = null
) : RemoteClient {

    val cleanHost: String
    val effectivePort: Int
    init {
        var h = host.trim()
        if (h.startsWith("http://", ignoreCase = true)) h = h.substring(7)
        if (h.startsWith("https://", ignoreCase = true)) h = h.substring(8)
        h = h.substringBefore('/')
        if (h.contains(':')) {
            val parts = h.split(':')
            cleanHost = parts[0]
            effectivePort = parts.getOrNull(1)?.toIntOrNull() ?: port
        } else {
            cleanHost = h
            effectivePort = if (port > 0) port else (if (useSsl) 443 else 80)
        }
    }

    private val scheme = if (useSsl) "https" else "http"
    val baseUrl: String = run {
        val cleanBase = if (basePath.startsWith("/")) basePath else "/$basePath"
        val withEndSlash = if (cleanBase.endsWith("/")) cleanBase else "$cleanBase/"
        val portSuffix = if ((useSsl && effectivePort == 443) || (!useSsl && effectivePort == 80)) "" else ":$effectivePort"
        "$scheme://$cleanHost$portSuffix$withEndSlash"
    }

    private val authHeader: String? = if (!username.isNullOrBlank()) {
        Credentials.basic(username, password ?: "", Charsets.UTF_8)
    } else null

    private val okHttpClient: OkHttpClient = run {
        val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
            object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            }
        )
        val sslSocketFactory = try {
            val sslContext = javax.net.ssl.SSLContext.getInstance("TLS").apply {
                init(null, trustAllCerts, java.security.SecureRandom())
            }
            sslContext.socketFactory
        } catch (_: Exception) {
            null
        }

        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .hostnameVerifier { _, _ -> true }

        if (sslSocketFactory != null) {
            builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
        }
        builder.build()
    }

    private fun buildRequest(url: String, method: String, depth: String? = null): Request {
        val builder = Request.Builder().url(url)
        if (authHeader != null) {
            builder.header("Authorization", authHeader)
        }
        if (depth != null) {
            builder.header("Depth", depth)
        }

        if (method.equals("PROPFIND", ignoreCase = true)) {
            val emptyBody = "".toRequestBody(null)
            builder.method("PROPFIND", emptyBody)
        } else {
            builder.method(method, null)
        }
        return builder.build()
    }

    fun getFullFileUrl(path: String): String {
        val cleanPath = path.trimStart('/')
        return "$baseUrl$cleanPath"
    }

    override suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(baseUrl, "PROPFIND", depth = "0")
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 207) {
                    Result.success(true)
                } else if (response.code == 401 || response.code == 403) {
                    Result.failure(Exception("Xác thực thất bại: Tên đăng nhập hoặc mật khẩu không đúng (HTTP ${response.code})"))
                } else {
                    Result.failure(Exception("Máy chủ WebDAV phản hồi mã lỗi: HTTP ${response.code} ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Không thể kết nối đến máy chủ WebDAV ($cleanHost:$effectivePort): ${e.message}"))
        }
    }

    override suspend fun listFiles(path: String): Result<List<RemoteFileItem>> = withContext(Dispatchers.IO) {
        try {
            val targetUrl = if (path.isBlank() || path == "/") {
                baseUrl
            } else {
                val cleanPath = path.trimStart('/').let { if (it.endsWith("/")) it else "$it/" }
                "$baseUrl$cleanPath"
            }

            val request = buildRequest(targetUrl, "PROPFIND", depth = "1")
            val xmlResponse = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 207) {
                    return@withContext Result.failure(Exception("Lỗi đọc thư mục WebDAV: HTTP ${response.code}"))
                }
                response.body?.string() ?: ""
            }

            val items = parseWebDavMultistatusXml(xmlResponse, targetUrl)
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseWebDavMultistatusXml(xml: String, requestUrl: String): List<RemoteFileItem> {
        val result = mutableListOf<RemoteFileItem>()
        if (xml.isBlank()) return result

        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        var inResponse = false
        var currentHref = ""
        var currentDisplayName = ""
        var currentContentLength = 0L
        var isCollection = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.lowercase() ?: ""

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "response" -> {
                            inResponse = true
                            currentHref = ""
                            currentDisplayName = ""
                            currentContentLength = 0L
                            isCollection = false
                        }
                        "href" -> {
                            if (inResponse) {
                                currentHref = parser.nextText().trim()
                            }
                        }
                        "displayname" -> {
                            if (inResponse) {
                                currentDisplayName = parser.nextText().trim()
                            }
                        }
                        "getcontentlength" -> {
                            if (inResponse) {
                                currentContentLength = parser.nextText().trim().toLongOrNull() ?: 0L
                            }
                        }
                        "collection" -> {
                            if (inResponse) {
                                isCollection = true
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name == "response" && inResponse) {
                        inResponse = false
                        val decodedHref = try {
                            URLDecoder.decode(currentHref, "UTF-8")
                        } catch (_: Exception) {
                            currentHref
                        }

                        // Chuẩn hóa tên file và đường dẫn
                        val cleanHref = decodedHref.trimEnd('/')
                        val itemDisplayName = if (currentDisplayName.isNotBlank()) {
                            currentDisplayName
                        } else {
                            cleanHref.substringAfterLast('/')
                        }

                        // Bỏ qua chính thư mục hiện tại (Self entry)
                        val reqDecoded = try {
                            URLDecoder.decode(requestUrl, "UTF-8").trimEnd('/')
                        } catch (_: Exception) {
                            requestUrl.trimEnd('/')
                        }

                        val isSelf = cleanHref.equals(reqDecoded, ignoreCase = true) ||
                            cleanHref.endsWith(reqDecoded.substringAfter("://").substringAfter("/")) ||
                            itemDisplayName.isBlank()

                        if (!isSelf && itemDisplayName.isNotBlank()) {
                            val cleanBaseForRel = basePath.trim('/')
                            val relativePath = if (cleanBaseForRel.isNotBlank() && decodedHref.contains(cleanBaseForRel)) {
                                decodedHref.substringAfter(cleanBaseForRel).trimStart('/')
                            } else {
                                cleanHref.substringAfterLast('/')
                            }

                            result.add(
                                RemoteFileItem(
                                    name = itemDisplayName,
                                    path = relativePath,
                                    isDirectory = isCollection || decodedHref.endsWith("/"),
                                    size = currentContentLength
                                )
                            )
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return result
    }

    override suspend fun openInputStream(path: String, offset: Long): InputStream = withContext(Dispatchers.IO) {
        val fileUrl = getFullFileUrl(path)
        val reqBuilder = Request.Builder().url(fileUrl)
        if (authHeader != null) {
            reqBuilder.header("Authorization", authHeader)
        }
        if (offset > 0L) {
            reqBuilder.header("Range", "bytes=$offset-")
        }
        val response = okHttpClient.newCall(reqBuilder.build()).execute()
        if (!response.isSuccessful && response.code != 206) {
            throw Exception("Không thể mở tệp tin WebDAV: HTTP ${response.code}")
        }
        response.body?.byteStream() ?: throw Exception("Nội dung tệp WebDAV rỗng")
    }

    override suspend fun getFileSize(path: String): Long = withContext(Dispatchers.IO) {
        val fileUrl = getFullFileUrl(path)
        val reqBuilder = Request.Builder().url(fileUrl).head()
        if (authHeader != null) {
            reqBuilder.header("Authorization", authHeader)
        }
        okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
            response.header("Content-Length")?.toLongOrNull() ?: 0L
        }
    }

    override fun close() {
        okHttpClient.dispatcher.executorService.shutdown()
        okHttpClient.connectionPool.evictAll()
    }
}
