package com.jkhx.player.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

object DirectDownloader {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun downloadDirect(
        task: DownloadTask,
        destFile: File,
        isCancelled: () -> Boolean,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long, speedText: String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        try {
            destFile.parentFile?.mkdirs()
            val existingLength = if (destFile.exists()) destFile.length() else 0L

            val reqBuilder = Request.Builder().url(task.url)
            task.headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
            if (existingLength > 0) {
                reqBuilder.addHeader("Range", "bytes=$existingLength-")
            }

            val response = client.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful && response.code != 206) {
                return@withContext false
            }

            val body = response.body ?: return@withContext false
            val contentLength = body.contentLength()
            val totalBytes = if (contentLength > 0) existingLength + contentLength else -1L

            val isAppend = response.code == 206 && existingLength > 0
            outputStream = FileOutputStream(destFile, isAppend)
            var currentDownloaded = if (isAppend) existingLength else 0L

            inputStream = body.byteStream()
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            var startTime = System.currentTimeMillis()
            var bytesSinceLastCheck = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (isCancelled()) {
                    return@withContext false
                }

                outputStream.write(buffer, 0, bytesRead)
                currentDownloaded += bytesRead
                bytesSinceLastCheck += bytesRead

                val now = System.currentTimeMillis()
                val elapsed = now - startTime
                if (elapsed >= 1000) {
                    val speedBytesPerSec = (bytesSinceLastCheck * 1000) / elapsed
                    val speedText = formatSpeed(speedBytesPerSec)
                    val percent = if (totalBytes > 0) ((currentDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
                    onProgress(percent, currentDownloaded, totalBytes, speedText)
                    startTime = now
                    bytesSinceLastCheck = 0L
                }
            }

            outputStream.flush()
            onProgress(100, currentDownloaded, totalBytes, "Hoàn tất")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            try { outputStream?.close() } catch (_: Exception) {}
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
            bytesPerSec >= 1024 -> String.format(java.util.Locale.US, "%.0f KB/s", bytesPerSec / 1024.0)
            else -> "$bytesPerSec B/s"
        }
    }
}
