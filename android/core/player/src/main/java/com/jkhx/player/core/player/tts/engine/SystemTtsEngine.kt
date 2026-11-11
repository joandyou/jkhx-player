package com.jkhx.player.core.player.tts.engine

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Locale
import java.util.UUID

class SystemTtsEngine(
    private val context: Context
) : TtsEngine {

    override val engineId: String = "system"

    private var tts: TextToSpeech? = null
    @Volatile
    private var isInitialized: Boolean = false
    private val initDeferred = CompletableDeferred<Boolean>()

    // Khóa Mutex bao trọn toàn bộ vòng đời synthesize (từ cấu hình đến khi nhận callback onDone/onError)
    private val synthesisMutex = Mutex()

    @Volatile
    private var activeUtteranceId: String? = null

    @Volatile
    private var activeDoneDeferred: CompletableDeferred<Boolean>? = null

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}

        override fun onDone(utteranceId: String?) {
            if (utteranceId != null && utteranceId == activeUtteranceId) {
                activeDoneDeferred?.complete(true)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            if (utteranceId != null && utteranceId == activeUtteranceId) {
                activeDoneDeferred?.complete(false)
            }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            if (utteranceId != null && utteranceId == activeUtteranceId) {
                activeDoneDeferred?.complete(false)
            }
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            if (utteranceId != null && utteranceId == activeUtteranceId) {
                activeDoneDeferred?.complete(false)
            }
        }
    }

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts
                val result = engine?.setLanguage(Locale("vi", "VN"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.setLanguage(Locale.getDefault())
                }
                engine?.setOnUtteranceProgressListener(progressListener)
                isInitialized = true
                initDeferred.complete(true)
            } else {
                initDeferred.complete(false)
            }
        }
    }

    override suspend fun synthesize(text: String, voiceId: String, speechRate: Float): ByteArray? = withContext(Dispatchers.IO) {
        val clean = text.replace(Regex("<[^>]*>"), "").trim()
        if (clean.isBlank()) return@withContext null

        synthesisMutex.withLock {
            ensureActive()

            if (!isInitialized) {
                val ok = withTimeoutOrNull(4000L) { initDeferred.await() } ?: false
                if (!ok) return@withLock null
            }

            val t = tts ?: return@withLock null
            t.setOnUtteranceProgressListener(progressListener)

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
            val pitch = when (normId) {
                "minh_quang" -> 0.68f
                "bao_nam"    -> 0.85f
                "tuan_khang" -> 0.98f
                "phuong_vy"  -> 1.15f
                "mai_chi"    -> 1.25f
                "anh_duong"  -> 1.40f
                else -> 0.85f
            }
            t.setPitch(pitch)

            // Giọng Nam Bộ nhịp điệu tự nhiên, hoạt bát hơn một chút (+5%)
            val rateFactor = if (isSouthern) 1.05f else 1.0f
            t.setSpeechRate((speechRate * rateFactor).coerceIn(0.5f, 2.0f))

            val utteranceId = UUID.randomUUID().toString()
            val doneDeferred = CompletableDeferred<Boolean>()
            val tempFile = File(context.cacheDir, "sys_tts_$utteranceId.wav")

            activeUtteranceId = utteranceId
            activeDoneDeferred = doneDeferred

            try {
                val params = Bundle()
                val ret = t.synthesizeToFile(clean, params, tempFile, utteranceId)
                if (ret != TextToSpeech.SUCCESS) {
                    return@withLock null
                }

                val timeoutMs = maxOf(8000L, clean.length * 35L)
                val completed = withTimeoutOrNull(timeoutMs) {
                    doneDeferred.await()
                } ?: false

                if (!completed) {
                    try {
                        t.stop()
                    } catch (_: Exception) {}
                    return@withLock null
                }

                if (tempFile.exists() && tempFile.length() > 44L) {
                    return@withLock tempFile.readBytes()
                }
                return@withLock null
            } catch (ce: CancellationException) {
                try {
                    t.stop()
                } catch (_: Exception) {}
                throw ce
            } catch (_: Exception) {
                try {
                    t.stop()
                } catch (_: Exception) {}
                return@withLock null
            } finally {
                if (activeUtteranceId == utteranceId) {
                    activeUtteranceId = null
                    activeDoneDeferred = null
                }
                try {
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun stop() {
        activeUtteranceId = null
        activeDoneDeferred?.complete(false)
        try {
            tts?.stop()
        } catch (_: Exception) {}
    }

    override fun release() {
        stop()
        try {
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        isInitialized = false
    }
}
