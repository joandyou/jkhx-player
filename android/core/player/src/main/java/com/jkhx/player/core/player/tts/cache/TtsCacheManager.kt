package com.jkhx.player.core.player.tts.cache

import android.content.Context
import java.io.File
import java.security.MessageDigest

class TtsCacheManager(context: Context) {

    private val cacheDir = File(context.cacheDir, "tts_cache").apply {
        if (!exists()) mkdirs()
    }

    fun generateKey(engineId: String, voiceId: String, speechRate: Float, text: String): String {
        val raw = "$engineId:$voiceId:$speechRate:${text.trim()}"
        return md5(raw)
    }

    fun getAudioFile(key: String): File? {
        val candidates = listOf(
            File(cacheDir, "$key.opus"),
            File(cacheDir, "$key.webm"),
            File(cacheDir, "$key.mp3")
        )
        return candidates.firstOrNull { it.exists() && it.length() > 0 }
    }

    fun saveAudio(key: String, bytes: ByteArray): File {
        val ext = when {
            bytes.size >= 4 && bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() && bytes[2] == 0xDF.toByte() && bytes[3] == 0xA3.toByte() -> "opus"
            bytes.size >= 4 && bytes[0] == 0x4F.toByte() && bytes[1] == 0x67.toByte() && bytes[2] == 0x67.toByte() && bytes[3] == 0x53.toByte() -> "opus"
            else -> "mp3"
        }
        val file = File(cacheDir, "$key.$ext")
        file.writeBytes(bytes)
        trimCacheIfNeeded()
        return file
    }

    fun hasAudio(key: String): Boolean {
        return getAudioFile(key) != null
    }

    fun clearCache() {
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {}
    }

    private fun trimCacheIfNeeded() {
        try {
            val files = cacheDir.listFiles() ?: return
            val maxBytes = 150L * 1024L * 1024L // 150MB
            var totalSize = files.sumOf { it.length() }
            if (totalSize > maxBytes) {
                // Sắp xếp xóa file cũ nhất trước (LRU)
                val sorted = files.sortedBy { it.lastModified() }
                for (f in sorted) {
                    val size = f.length()
                    if (f.delete()) {
                        totalSize -= size
                        if (totalSize <= maxBytes * 0.7) break
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

