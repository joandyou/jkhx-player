package com.jkhx.player.core.player.tts

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.jkhx.player.core.player.subtitle.model.SubtitleCue
import com.jkhx.player.core.player.tts.cache.TtsCacheManager
import com.jkhx.player.core.player.tts.ducking.AudioDuckingController
import com.jkhx.player.core.player.tts.engine.GeminiTtsEngine
import com.jkhx.player.core.player.tts.engine.SystemTtsEngine
import com.jkhx.player.core.player.tts.engine.TtsEngine
import com.jkhx.player.core.player.tts.model.TtsConfig
import com.jkhx.player.core.player.tts.model.TtsEngineType
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class TtsManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val videoPlayerProvider: () -> ExoPlayer?,
    private var config: TtsConfig = TtsConfig()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val cacheManager = TtsCacheManager(context)
    val duckingController = AudioDuckingController(videoPlayerProvider)

    private val systemEngine by lazy { SystemTtsEngine(context) }
    private var geminiEngine: GeminiTtsEngine? = null

    private var ttsAudioPlayer: ExoPlayer? = null
    private var currentCues: List<SubtitleCue> = emptyList()

    private var lastPlayedCueIndex: Int = -1
    private var isPlayingAudio: Boolean = false
    private var speakJob: Job? = null
    private var restoreJob: Job? = null
    private var prefetchJob: Job? = null
    private var lastPrefetchedPositionMs: Long = -1L

    private val prefetchSemaphore = Semaphore(2)
    private val pendingFetchKeys = ConcurrentHashMap.newKeySet<String>()

    init {
        initAudioPlayer()
        updateEngine()
    }

    private fun initAudioPlayer() {
        if (ttsAudioPlayer == null) {
            ttsAudioPlayer = ExoPlayer.Builder(context).build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            isPlayingAudio = false
                            scheduleRestoreDucking()
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (!isPlaying && playbackState != Player.STATE_BUFFERING) {
                            isPlayingAudio = false
                            scheduleRestoreDucking()
                        } else if (isPlaying) {
                            restoreJob?.cancel()
                            isPlayingAudio = true
                        }
                    }
                })
            }
        }
    }

    fun updateConfig(newConfig: TtsConfig) {
        val engineChanged = config.engineType != newConfig.engineType ||
                config.apiKey != newConfig.apiKey ||
                config.apiEndpoint != newConfig.apiEndpoint
        val voiceChanged = !config.voiceId.equals(newConfig.voiceId, ignoreCase = true)
        val rateChanged = kotlin.math.abs(config.speechRate - newConfig.speechRate) > 0.01f

        config = newConfig
        duckingController.setBaseVolume(1.0f)

        if (engineChanged) {
            updateEngine()
        }

        if (voiceChanged || rateChanged || engineChanged) {
            stopAudio()
            lastPlayedCueIndex = -1
            prefetchJob?.cancel()
            pendingFetchKeys.clear()
            lastPrefetchedPositionMs = -1L
            val curPos = videoPlayerProvider()?.currentPosition ?: 0L
            triggerPrefetch(curPos, force = true)
        }

        if (!config.isEnabled) {
            stopAudio()
            duckingController.reset()
            prefetchJob?.cancel()
            pendingFetchKeys.clear()
            lastPrefetchedPositionMs = -1L
        }
    }

    private fun updateEngine() {
        geminiEngine?.release()
        geminiEngine = GeminiTtsEngine(
            okHttpClient = okHttpClient,
            engineId = config.engineType.id,
            apiKeyProvider = { config.apiKey },
            apiEndpointProvider = { config.apiEndpoint },
            audioFormatProvider = { config.audioFormat }
        )
    }

    fun setCues(cues: List<SubtitleCue>) {
        currentCues = cues.sortedBy { it.startTimeMs }
        lastPlayedCueIndex = -1
        lastPrefetchedPositionMs = -1L
        val curPos = videoPlayerProvider()?.currentPosition ?: 0L
        triggerPrefetch(curPos, force = true)
    }

    fun onPlaybackTick(currentPositionMs: Long, isVideoPlaying: Boolean, offsetMs: Long = 0L) {
        if (!config.isEnabled || currentCues.isEmpty()) return

        val player = ttsAudioPlayer ?: return

        // Đồng bộ trạng thái tạm dừng của video sang giọng đọc thuyết minh
        if (!isVideoPlaying) {
            if (player.isPlaying) {
                player.pause()
            }
            return
        } else {
            if (!player.isPlaying && isPlayingAudio && player.playbackState == Player.STATE_READY) {
                player.play()
            }
        }

        val effectivePos = currentPositionMs - offsetMs

        // Kích hoạt prefetch gối đầu cho các câu thoại tiếp theo (force = false)
        triggerPrefetch(effectivePos, force = false)

        // Tìm câu thoại khớp với vị trí hiện tại
        val activeCue = currentCues.firstOrNull { cue ->
            // Cửa sổ nhận diện: từ thời điểm bắt đầu - 80ms đến trước khi kết thúc
            effectivePos >= (cue.startTimeMs - 80) && effectivePos < (cue.endTimeMs - 100)
        } ?: return

        if (activeCue.index != lastPlayedCueIndex) {
            lastPlayedCueIndex = activeCue.index
            playCueAudio(activeCue)
        }
    }

    private fun playCueAudio(cue: SubtitleCue) {
        val cleanText = cue.text.replace(Regex("<[^>]*>"), "").trim()
        if (cleanText.isBlank()) return

        val key = cacheManager.generateKey(
            engineId = config.engineType.id,
            voiceId = config.voiceId,
            speechRate = config.speechRate,
            text = cleanText
        )

        val cachedFile = cacheManager.getAudioFile(key)
        if (cachedFile != null) {
            playAudioFile(cachedFile)
        } else {
            // Chưa kịp prefetch xong -> xử lý phát trực tiếp hoặc dùng fallback
            speakJob?.cancel()
            speakJob = scope.launch(Dispatchers.IO) {
                val engine = getActiveEngine()
                val bytes = engine.synthesize(cleanText, config.voiceId, config.speechRate)
                    ?: if (config.engineType.isCloud) {
                        // Fallback sang hệ thống nếu cloud không khả dụng
                        systemEngine.synthesize(cleanText, config.voiceId, config.speechRate)
                    } else null

                if (!isActive) return@launch

                if (bytes != null) {
                    val file = cacheManager.saveAudio(key, bytes)
                    withContext(Dispatchers.Main) {
                        if (isActive) {
                            playAudioFile(file)
                        }
                    }
                }
            }
        }
    }

    private fun playAudioFile(file: File) {
        val player = ttsAudioPlayer ?: return
        restoreJob?.cancel()
        isPlayingAudio = true

        // Hạ âm lượng phim gốc mượt mà
        duckingController.duck(
            targetVolume = config.duckingVolume,
            durationMs = 120L,
            scope = scope
        )

        try {
            val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
            player.setMediaItem(mediaItem)
            player.setPlaybackSpeed(config.speechRate.coerceIn(0.5f, 2.0f))
            player.prepare()
            player.play()
        } catch (_: Exception) {
            isPlayingAudio = false
            scheduleRestoreDucking()
        }
    }

    private fun scheduleRestoreDucking() {
        restoreJob?.cancel()
        restoreJob = scope.launch(Dispatchers.Main) {
            delay(300L) // Độ trễ ngắn để tránh giật âm lượng giữa các câu thoại liên tiếp
            if (!isPlayingAudio) {
                duckingController.restore(durationMs = 220L, scope = scope)
            }
        }
    }

    fun onSeek(newPositionMs: Long) {
        stopAudio()
        duckingController.reset()
        lastPlayedCueIndex = -1
        triggerPrefetch(newPositionMs, force = true)
    }

    fun stopAudio() {
        speakJob?.cancel()
        speakJob = null
        restoreJob?.cancel()
        isPlayingAudio = false
        try {
            ttsAudioPlayer?.stop()
            ttsAudioPlayer?.clearMediaItems()
        } catch (_: Exception) {}
        systemEngine.stop()
    }

    private fun triggerPrefetch(currentPositionMs: Long, force: Boolean = false) {
        if (!config.isEnabled || currentCues.isEmpty()) return

        // Nếu không phải force:
        // 1. Không hủy nếu coroutine tải trước cũ vẫn đang chạy tích cực
        // 2. Không quét lại nếu vị trí phim chưa tiến thêm quá 15 giây kể từ lần nạp trước
        if (!force) {
            if (prefetchJob?.isActive == true) return
            if (lastPrefetchedPositionMs >= 0L && kotlin.math.abs(currentPositionMs - lastPrefetchedPositionMs) < 15_000L) {
                return
            }
        }

        prefetchJob?.cancel()
        if (force) {
            pendingFetchKeys.clear()
        }
        lastPrefetchedPositionMs = currentPositionMs

        prefetchJob = scope.launch(Dispatchers.IO) {
            // Lấy trước 10-15 câu thoại trong vòng 60 giây tới
            val upcoming = currentCues.filter { cue ->
                cue.startTimeMs >= currentPositionMs && cue.startTimeMs <= (currentPositionMs + 60_000L)
            }.take(12)

            val engine = getActiveEngine()

            for (cue in upcoming) {
                if (!isActive) break
                val clean = cue.text.replace(Regex("<[^>]*>"), "").trim()
                if (clean.isBlank()) continue

                val key = cacheManager.generateKey(
                    engineId = config.engineType.id,
                    voiceId = config.voiceId,
                    speechRate = config.speechRate,
                    text = clean
                )

                if (cacheManager.hasAudio(key) || pendingFetchKeys.contains(key)) {
                    continue
                }

                pendingFetchKeys.add(key)
                launch {
                    prefetchSemaphore.withPermit {
                        try {
                            if (!isActive) return@withPermit
                            val bytes = engine.synthesize(clean, config.voiceId, config.speechRate)
                            if (bytes != null && isActive) {
                                cacheManager.saveAudio(key, bytes)
                            }
                        } catch (_: Exception) {
                        } finally {
                            pendingFetchKeys.remove(key)
                        }
                    }
                }
            }
        }
    }

    private fun getActiveEngine(): TtsEngine {
        return if (config.engineType == TtsEngineType.OFFLINE_SYSTEM) {
            systemEngine
        } else {
            geminiEngine ?: GeminiTtsEngine(
                okHttpClient = okHttpClient,
                engineId = config.engineType.id,
                apiKeyProvider = { config.apiKey },
                apiEndpointProvider = { config.apiEndpoint },
                audioFormatProvider = { config.audioFormat }
            ).also { geminiEngine = it }
        }
    }

    fun release() {
        speakJob?.cancel()
        speakJob = null
        scope.cancel()
        stopAudio()
        try {
            ttsAudioPlayer?.release()
        } catch (_: Exception) {}
        ttsAudioPlayer = null
        systemEngine.release()
        geminiEngine?.release()
        geminiEngine = null
        duckingController.reset()
    }
}

