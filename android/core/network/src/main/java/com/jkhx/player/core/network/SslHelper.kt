package com.jkhx.player.core.network

import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

/**
 * Tiện ích cấu hình SSL Socket Factory tương thích cao cho Android TV Box / Android cũ (Android 7.0/7.1 - API 24/25).
 * Tự động bỏ qua lỗi Trust anchor not found (DST Root CA X3 hết hạn) khi truy cập các server dùng Let's Encrypt.
 */
object SslHelper {

    val trustAllCerts = arrayOf<TrustManager>(
        object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
    )

    val trustAllManager = trustAllCerts[0] as X509TrustManager

    val permissiveHostnameVerifier = HostnameVerifier { _, _ -> true }

    fun configureSsl(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        return try {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())
            builder
                .sslSocketFactory(sslContext.socketFactory, trustAllManager)
                .hostnameVerifier(permissiveHostnameVerifier)
        } catch (_: Exception) {
            builder
        }
    }
}

