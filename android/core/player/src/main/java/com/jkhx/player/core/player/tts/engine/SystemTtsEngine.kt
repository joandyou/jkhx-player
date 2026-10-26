package com.jkhx.player.core.player.tts.engine

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.*
import java.io.File
import java.util.Locale
import java.util.UUID

class SystemTtsEngine(
    private val context: Context
) : TtsEngine {

    override val engineId: String = "system"

    private var tts: TextToSpeech? = null
    private var isInitialized: Boolean = false
    private val initDeferred = CompletableDeferred<Boolean>()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("vi", "VN"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Thử fallback Locale.getDefault()
                    tts?.setLanguage(Locale.getDefault())
                }
                isInitialized = true
                initDeferred.complete(true)
            } else {
                initDeferred.complete(false)
            }
        }
    }

    override suspend fun synthesize(text: String, voiceId: String, speechRate: Float): ByteArray? = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            val ok = withTimeoutOrNull(3000L) { initDeferred.await() } ?: false
            if (!ok) return@withContext null
        }

        val t = tts ?: return@withContext null
        val clean = text.replace(Regex("<[^>]*>"), "").trim()
        if (clean.isBlank()) return@withContext null

        val normId = when (voiceId.lowercase().trim()) {
            "alloy", "bao_nam" -> "bao_nam"
            "onyx", "minh_quang" -> "minh_quang"
            "echo", "tuan_khang" -> "tuan_khang"
            "fable", "phuong_vy" -> "phuong_vy"
            "nova", "female", "mai_chi" -> "mai_chi"
            "shimmer", "anh_duong" -> "anh_duong"
            else -> "bao_nam"
        }

        val isFemale = normId == "phuong_vy" || normId == "mai_chi" || normId == "anh_duong"
        val isSouthern = normId == "tuan_khang" || normId == "phuong_vy"

        try {
            val availableVoices = t.voices
            if (!availableVoices.isNullOrEmpty()) {
                val locale = t.voice?.locale ?: Locale("vi", "VN")
                val matchingVoices = availableVoices.filter {
                    it.locale.language == locale.language
                }
                if (matchingVoices.isNotEmpty()) {
                    val targetVoice = if (isFemale) {
                        val southernCandidate = if (isSouthern) {
                            matchingVoices.firstOrNull { v ->
                                (v.name.contains("south", ignoreCase = true) ||
                                 v.name.contains("sgn", ignoreCase = true) ||
                                 v.name.contains("hcm", ignoreCase = true) ||
                                 v.name.contains("vie", ignoreCase = true)) &&
                                (v.name.contains("female", ignoreCase = true) ||
                                 v.name.contains("vic", ignoreCase = true) ||
                                 v.name.contains("vie", ignoreCase = true) ||
                                 v.name.contains("-f-", ignoreCase = true))
                            }
                        } else null

                        southernCandidate ?: matchingVoices.firstOrNull {
                            it.name.contains("vic", ignoreCase = true) ||
                            it.name.contains("vie", ignoreCase = true) ||
                            it.name.contains("female", ignoreCase = true) ||
                            it.name.contains("woman", ignoreCase = true) ||
                            it.name.contains("-f-", ignoreCase = true) ||
                            it.name.contains("f0", ignoreCase = true) ||
                            it.features?.any { f -> f.contains("female", ignoreCase = true) } == true
                        } ?: matchingVoices.firstOrNull { it.name.endsWith("-2") || it.name.endsWith("-b") }
                    } else {
                        val southernCandidate = if (isSouthern) {
                            matchingVoices.firstOrNull { v ->
                                (v.name.contains("south", ignoreCase = true) ||
                                 v.name.contains("sgn", ignoreCase = true) ||
                                 v.name.contains("hcm", ignoreCase = true) ||
                                 v.name.contains("vif", ignoreCase = true)) &&
                                (v.name.contains("male", ignoreCase = true) ||
                                 v.name.contains("vid", ignoreCase = true) ||
                                 v.name.contains("vif", ignoreCase = true) ||
                                 v.name.contains("-m-", ignoreCase = true))
                            }
                        } else null

                        southernCandidate ?: matchingVoices.firstOrNull {
                            it.name.contains("vid", ignoreCase = true) ||
                            it.name.contains("vif", ignoreCase = true) ||
                            it.name.contains("male", ignoreCase = true) ||
                            it.name.contains("man", ignoreCase = true) ||
                            it.name.contains("-m-", ignoreCase = true) ||
                            it.name.contains("m0", ignoreCase = true) ||
                            it.features?.any { f -> f.contains("male", ignoreCase = true) } == true
                        } ?: matchingVoices.firstOrNull { it.name.endsWith("-1") || it.name.endsWith("-a") }
                    }
                    if (targetVoice != null) {
                        t.voice = targetVoice
                    }
                }
            }
        } catch (_: Exception) {}

        // Dải cao độ (pitch) phân định rạch ròi 100% giữa Nam (0.68 - 0.98) và Nữ (1.15 - 1.40)
        // và giữa các vùng miền / phong cách thể hiện
        val pitch = when (normId) {
            "minh_quang" -> 0.68f  // Nam Trầm • Bắc (trầm ấm, uy quyền)
            "bao_nam"    -> 0.85f  // Nam • Bắc (chuẩn mực, tự nhiên)
            "tuan_khang" -> 0.98f  // Nam • Nam Bộ (sáng giọng, hào sảng, phóng khoáng)
            "phuong_vy"  -> 1.15f  // Nữ • Nam Bộ (ngọt ngào, dịu dàng, mềm mại)
            "mai_chi"    -> 1.25f  // Nữ • Bắc (ấm áp, truyền cảm, tự nhiên)
            "anh_duong"  -> 1.40f  // Nữ Trẻ • Bắc (trong trẻo, thanh thoát, tươi sáng)
            else -> 0.85f
        }
        t.setPitch(pitch)

        // Giọng Nam Bộ nhịp điệu tự nhiên, hoạt bát hơn một chút (+5%)
        val rateFactor = if (isSouthern) 1.05f else 1.0f
        t.setSpeechRate((speechRate * rateFactor).coerceIn(0.5f, 2.0f))

        val tempFile = File(context.cacheDir, "sys_tts_${UUID.randomUUID()}.wav")
        val utteranceId = UUID.randomUUID().toString()
        val doneDeferred = CompletableDeferred<Boolean>()

        val listener = object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId) doneDeferred.complete(true)
            }
            override fun onError(id: String?) {
                if (id == utteranceId) doneDeferred.complete(false)
            }
        }
        t.setOnUtteranceProgressListener(listener)

        val params = Bundle()
        val ret = t.synthesizeToFile(clean, params, tempFile, utteranceId)
        if (ret != TextToSpeech.SUCCESS) {
            tempFile.delete()
            return@withContext null
        }

        val completed = withTimeoutOrNull(4000L) { doneDeferred.await() } ?: false
        if (completed && tempFile.exists() && tempFile.length() > 0) {
            val bytes = tempFile.readBytes()
            tempFile.delete()
            bytes
        } else {
            tempFile.delete()
            null
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
    }

    override fun release() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        isInitialized = false
    }
}

