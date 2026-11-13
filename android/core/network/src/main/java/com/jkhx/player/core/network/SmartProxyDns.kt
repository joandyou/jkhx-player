package com.jkhx.player.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Bộ phân giải DNS thông minh tích hợp chống đầu độc DNS (DNS Poisoning) và hỗ trợ SOCKS5/HTTP Proxy.
 * Khi dùng SOCKS5 Proxy, OkHttp mặc định phân giải tên miền qua DNS nội bộ thiết bị trước khi kết nối proxy;
 * nếu nhà mạng chặn/đầu độc DNS (trả về 127.0.0.1, 0.0.0.0, IP LAN giả hoặc UnknownHostException),
 * SmartProxyDns sẽ tự động fallback sang DNS-over-HTTPS (1.1.1.1 / 8.8.8.8) đi qua chính Proxy đang bật.
 */
object SmartProxyDns : Dns {
    private const val TAG = "SmartProxyDns"
    private const val CACHE_TTL_MS = 10 * 60 * 1000L

    private val cache = ConcurrentHashMap<String, Pair<Long, List<InetAddress>>>()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val dohClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(Dns.SYSTEM) // Chỉ truy vấn IP literal (1.1.1.1 / 8.8.8.8), không đệ quy
            .proxySelector(object : ProxySelector() {
                override fun select(uri: URI?): List<Proxy> {
                    val proxy = HttpBridgeClient.globalProxyProvider?.invoke()
                    return if (proxy != null) listOf(proxy) else listOf(Proxy.NO_PROXY)
                }

                override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: java.io.IOException?) {}
            })
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val cleanHost = hostname.trim()
        if (cleanHost.isBlank()) throw UnknownHostException("Empty hostname")

        // 1. Nếu là IP literal hoặc tên miền nội bộ, dùng thẳng Dns.SYSTEM
        if (isIpLiteral(cleanHost) || !cleanHost.contains('.') ||
            cleanHost.endsWith(".local", ignoreCase = true) ||
            cleanHost.endsWith(".lan", ignoreCase = true)
        ) {
            return Dns.SYSTEM.lookup(cleanHost).sortedBy { it is Inet6Address }
        }

        // 2. Kiểm tra cache
        val now = System.currentTimeMillis()
        cache[cleanHost]?.let { (ts, addrs) ->
            if (now - ts < CACHE_TTL_MS && addrs.isNotEmpty()) {
                return addrs
            }
        }

        val activeProxy = HttpBridgeClient.globalProxyProvider?.invoke()
        val isSocksProxy = activeProxy?.type() == Proxy.Type.SOCKS
        val isTmdbDomain = cleanHost.endsWith("themoviedb.org", ignoreCase = true) ||
            cleanHost.endsWith("tmdb.org", ignoreCase = true)

        // 3. Nếu đang dùng SOCKS5 Proxy cho TMDB, ưu tiên DoH trước để tránh DNS nhà mạng đầu độc
        if (isSocksProxy && isTmdbDomain) {
            val dohAddrs = resolveViaDoh(cleanHost)
            if (dohAddrs.isNotEmpty()) {
                cache[cleanHost] = now to dohAddrs
                return dohAddrs
            }
        }

        // 4. Thử Dns.SYSTEM và lọc bỏ IP bị đầu độc (127.0.0.1, 0.0.0.0, LAN giả)
        val systemAddrs = try {
            Dns.SYSTEM.lookup(cleanHost).filter { addr ->
                !addr.isLoopbackAddress && !addr.isAnyLocalAddress && !addr.isSiteLocalAddress
            }.sortedBy { it is Inet6Address }
        } catch (_: Exception) {
            emptyList()
        }

        if (systemAddrs.isNotEmpty()) {
            cache[cleanHost] = now to systemAddrs
            return systemAddrs
        }

        // 5. Fallback sang DoH (Cloudflare 1.1.1.1 / Google 8.8.8.8)
        val dohAddrs = resolveViaDoh(cleanHost)
        if (dohAddrs.isNotEmpty()) {
            cache[cleanHost] = now to dohAddrs
            return dohAddrs
        }

        // 6. Fallback cuối cùng về Dns.SYSTEM gốc (ném UnknownHostException nếu vẫn thất bại)
        return Dns.SYSTEM.lookup(cleanHost).sortedBy { it is Inet6Address }
    }

    fun clearCache() {
        cache.clear()
    }

    private fun isIpLiteral(host: String): Boolean {
        return host.all { it.isDigit() || it == '.' } || host.contains(':')
    }

    private fun resolveViaDoh(hostname: String): List<InetAddress> {
        val dohEndpoints = listOf(
            "https://1.1.1.1/dns-query?name=$hostname&type=A",
            "https://8.8.8.8/resolve?name=$hostname&type=A"
        )
        for (url in dohEndpoints) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("Accept", "application/dns-json")
                    .get()
                    .build()
                dohClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string() ?: return@use
                    val root = json.parseToJsonElement(body).jsonObject
                    val answers = root["Answer"]?.jsonArray ?: return@use
                    val parsed = answers.mapNotNull { el ->
                        val obj = el.jsonObject
                        val type = obj["type"]?.jsonPrimitive?.intOrNull
                        val data = obj["data"]?.jsonPrimitive?.content
                        if (type == 1 && !data.isNullOrBlank()) {
                            try {
                                InetAddress.getByName(data)
                            } catch (_: Exception) {
                                null
                            }
                        } else {
                            null
                        }
                    }.filter { !it.isLoopbackAddress && !it.isAnyLocalAddress && !it.isSiteLocalAddress }

                    if (parsed.isNotEmpty()) {
                        return parsed.sortedBy { it is Inet6Address }
                    }
                }
            } catch (e: Exception) {
                System.err.println("$TAG: DoH lookup failed ($url) for $hostname: ${e.message}")
            }
        }
        return emptyList()
    }
}
