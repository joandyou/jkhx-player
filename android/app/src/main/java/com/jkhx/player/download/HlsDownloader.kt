package com.jkhx.player.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

object HlsDownloader {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val KEY_REGEX = Regex("""#EXT-X-KEY:METHOD=([^,]+),URI="([^"]+)"(?:,IV=([^,\s]+))?""")
    private val STREAM_INF_REGEX = Regex("""#EXT-X-STREAM-INF:(?:.*?BANDWIDTH=(\d+))?(?:.*?RESOLUTION=(\d+x\d+))?.*?\n([^\r\n]+)""")

    suspend fun downloadHls(
        task: DownloadTask,
        destFile: File,
        isCancelled: () -> Boolean,
        onProgress: (percent: Int, downloadedBytes: Long, speedText: String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            var playlistUrl = task.url
            var playlistContent = fetchText(playlistUrl, task.headers) ?: return@withContext false

            // 1. Nếu là Master Playlist, chọn luồng chất lượng cao nhất
            if (playlistContent.contains("#EXT-X-STREAM-INF")) {
                val bestVariantUrl = selectBestVariant(playlistUrl, playlistContent)
                if (bestVariantUrl != null) {
                    playlistUrl = bestVariantUrl
                    playlistContent = fetchText(playlistUrl, task.headers) ?: return@withContext false
                }
            }

            // 2. Bóc tách danh sách các segments .ts
            val segments = extractSegments(playlistUrl, playlistContent)
            if (segments.isEmpty()) {
                return@withContext false
            }

            // 3. Kiểm tra mã hóa AES-128
            var aesKey: ByteArray? = null
            var explicitIv: ByteArray? = null
            val keyMatch = KEY_REGEX.find(playlistContent)
            if (keyMatch != null) {
                val method = keyMatch.groupValues[1]
                if (method.equals("AES-128", ignoreCase = true)) {
                    val keyUri = keyMatch.groupValues[2]
                    val resolvedKeyUrl = URI(playlistUrl).resolve(keyUri).toString()
                    aesKey = fetchBytes(resolvedKeyUrl, task.headers)

                    val ivHex = keyMatch.groupValues.getOrNull(3)
                    if (!ivHex.isNullOrBlank() && ivHex.startsWith("0x", ignoreCase = true)) {
                        explicitIv = hexStringToByteArray(ivHex.substring(2))
                    }
                }
            }

            destFile.parentFile?.mkdirs()
            val outputStream = FileOutputStream(destFile, true)
            var totalBytesWritten = destFile.length()
            val totalSegments = segments.size
            var startTime = System.currentTimeMillis()
            var bytesSinceLastCheck = 0L

            for (index in segments.indices) {
                if (isCancelled()) {
                    outputStream.close()
                    return@withContext false
                }

                val segUrl = segments[index]
                var segBytes = fetchBytes(segUrl, task.headers)
                if (segBytes == null || segBytes.isEmpty()) {
                    // Thử lại 1 lần nếu mạng lag
                    segBytes = fetchBytes(segUrl, task.headers) ?: continue
                }

                // Giải mã nếu có AES-128
                val decryptedBytes = if (aesKey != null) {
                    val iv = explicitIv ?: defaultIv(index)
                    decryptAes(segBytes, aesKey, iv) ?: segBytes
                } else {
                    segBytes
                }

                outputStream.write(decryptedBytes)
                totalBytesWritten += decryptedBytes.size
                bytesSinceLastCheck += decryptedBytes.size

                val now = System.currentTimeMillis()
                val elapsed = now - startTime
                if (elapsed >= 1000) {
                    val speedBytesPerSec = (bytesSinceLastCheck * 1000) / elapsed
                    val speedText = formatSpeed(speedBytesPerSec)
                    val percent = (((index + 1) * 100) / totalSegments).coerceIn(0, 100)
                    onProgress(percent, totalBytesWritten, speedText)
                    startTime = now
                    bytesSinceLastCheck = 0L
                }
            }

            outputStream.flush()
            outputStream.close()
            onProgress(100, totalBytesWritten, "Hoàn tất")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun selectBestVariant(masterUrl: String, content: String): String? {
        val matches = STREAM_INF_REGEX.findAll(content).toList()
        if (matches.isEmpty()) return null
        var maxBandwidth = -1L
        var bestUrl: String? = null
        for (m in matches) {
            val bandwidth = m.groupValues[1].toLongOrNull() ?: 0L
            val relativeUri = m.groupValues[3].trim()
            if (bandwidth >= maxBandwidth) {
                maxBandwidth = bandwidth
                bestUrl = URI(masterUrl).resolve(relativeUri).toString()
            }
        }
        return bestUrl
    }

    private fun extractSegments(baseUrl: String, content: String): List<String> {
        val lines = content.lines()
        val segments = mutableListOf<String>()
        var isNextSegment = false
        val baseUri = URI(baseUrl)

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF:")) {
                isNextSegment = true
            } else if (isNextSegment && trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                try {
                    val fullUrl = baseUri.resolve(trimmed).toString()
                    segments.add(fullUrl)
                } catch (_: Exception) {
                    segments.add(trimmed)
                }
                isNextSegment = false
            }
        }
        return segments
    }

    private fun fetchText(url: String, headers: Map<String, String>): String? {
        return try {
            val reqBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchBytes(url: String, headers: Map<String, String>): ByteArray? {
        return try {
            val reqBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.bytes() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decryptAes(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray? {
        return try {
            val keySpec = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            cipher.doFinal(data)
        } catch (_: Exception) {
            null
        }
    }

    private fun defaultIv(index: Int): ByteArray {
        val seq = index + 1
        return ByteArray(16) {
            val fixed = seq / 256.0.pow(15 - it)
            (maxOf(0, fixed.toInt()) % 256).toByte()
        }
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
            bytesPerSec >= 1024 -> String.format(java.util.Locale.US, "%.0f KB/s", bytesPerSec / 1024.0)
            else -> "$bytesPerSec B/s"
        }
    }
}
