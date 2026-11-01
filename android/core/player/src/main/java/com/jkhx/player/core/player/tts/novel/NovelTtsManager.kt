package com.jkhx.player.core.player.tts.novel

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.jkhx.player.core.player.tts.cache.TtsCacheManager
import com.jkhx.player.core.player.tts.engine.GeminiTtsEngine
import com.jkhx.player.core.player.tts.engine.SystemTtsEngine
import com.jkhx.player.core.player.tts.engine.TtsEngine
import com.jkhx.player.core.player.tts.model.TtsConfig
import com.jkhx.player.core.player.tts.model.TtsEngineType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import java.io.File

/**
 * Quản lý phát âm thanh Text-To-Speech chuyên dụng cho Truyện chữ (Novel Reader).
 * Hỗ trợ prefetch 2 câu gối đầu mượt mà, highlight đoạn văn, auto-scroll,
 * hẹn giờ tắt (Sleep Timer), và tự động chuyển chương.
 */
class NovelTtsManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        const val SLEEP_TIMER_OFF = 0
        const val SLEEP_TIMER_END_OF_CHAPTER = -1

        @Volatile
        private var INSTANCE: NovelTtsManager? = null

        fun getInstance(context: Context, okHttpClient: OkHttpClient): NovelTtsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NovelTtsManager(context.applicationContext, okHttpClient).also { INSTANCE = it }
            }
        }

        fun getInstanceOrNull(): NovelTtsManager? = INSTANCE
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val cacheManager = TtsCacheManager(context)

    private val systemEngine by lazy { SystemTtsEngine(context) }
    private var geminiEngine: GeminiTtsEngine? = null

    private var exoPlayer: ExoPlayer? = null

    // State flows cho giao diện người dùng
    private val _storyTitle = MutableStateFlow("")
    val storyTitle: StateFlow<String> = _storyTitle.asStateFlow()

    private val _chapterTitle = MutableStateFlow("")
    val chapterTitle: StateFlow<String> = _chapterTitle.asStateFlow()

    private val _paragraphs = MutableStateFlow<List<String>>(emptyList())
    val paragraphs: StateFlow<List<String>> = _paragraphs.asStateFlow()

    private val _currentParagraphIndex = MutableStateFlow(0)
    val currentParagraphIndex: StateFlow<Int> = _currentParagraphIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _speechRate = MutableStateFlow(1.0f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _sleepTimerMinutes = MutableStateFlow(SLEEP_TIMER_OFF)
    val sleepTimerMinutes: StateFlow<Int> = _sleepTimerMinutes.asStateFlow()

    private val _sleepTimerSecondsRemaining = MutableStateFlow(0)
    val sleepTimerSecondsRemaining: StateFlow<Int> = _sleepTimerSecondsRemaining.asStateFlow()

    private val _config = MutableStateFlow(TtsConfig(engineType = TtsEngineType.OFFLINE_SYSTEM))
    val config: StateFlow<TtsConfig> = _config.asStateFlow()

    private var currentPlayJob: Job? = null
    private var prefetchJob: Job? = null
    private var sleepTimerJob: Job? = null

    var onChapterFinishedListener: (() -> Unit)? = null
    var onServiceStateChanged: ((isPlaying: Boolean, chapterTitle: String, paragraphIdx: Int, total: Int) -> Unit)? = null

    init {
        initPlayer()
        updateEngine()
    }

    private fun initPlayer() {
        if (exoPlayer == null) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .setUsage(C.USAGE_MEDIA)
                .build()

            exoPlayer = ExoPlayer.Builder(context).build().apply {
                setAudioAttributes(audioAttributes, true)
                setHandleAudioBecomingNoisy(true)
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            handleParagraphCompleted()
                        }
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                        notifyServiceState()
                    }
                })
            }
        }
    }

    private fun updateEngine() {
        val currentConf = _config.value
        geminiEngine?.release()
        geminiEngine = GeminiTtsEngine(
            okHttpClient = okHttpClient,
            engineId = currentConf.engineType.id,
            apiKeyProvider = { currentConf.apiKey },
            apiEndpointProvider = { currentConf.apiEndpoint },
            audioFormatProvider = { currentConf.audioFormat }
        )
    }

    fun updateConfig(newConfig: TtsConfig) {
        val clampedRate = newConfig.speechRate.coerceIn(0.95f, 1.25f)
        val sanitized = newConfig.copy(speechRate = clampedRate)

        val engineChanged = _config.value.engineType != sanitized.engineType ||
                _config.value.apiKey != sanitized.apiKey ||
                _config.value.apiEndpoint != sanitized.apiEndpoint ||
                _config.value.voiceId != sanitized.voiceId ||
                _config.value.audioFormat != sanitized.audioFormat

        _config.value = sanitized
        _speechRate.value = clampedRate

        if (engineChanged) {
            updateEngine()
        }

        // Cập nhật tốc độ đọc trực tiếp vào ExoPlayer
        exoPlayer?.playbackParameters = PlaybackParameters(clampedRate)

        // Chỉ phát lại nếu người dùng đổi engine hoặc đổi giọng đọc thực sự
        if (engineChanged && _isPlaying.value) {
            speakParagraph(_currentParagraphIndex.value)
        }
    }

    /**
     * Thu hẹp dải tốc độ đọc: 0.95x, 1.0x, 1.1x, 1.15x, 1.25x
     */
    fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.95f, 1.25f)
        _speechRate.value = clamped
        val updated = _config.value.copy(speechRate = clamped)
        _config.value = updated
        exoPlayer?.playbackParameters = PlaybackParameters(clamped)
    }

    /**
     * Nạp dữ liệu chương truyện chữ và chuẩn bị phát.
     */
    fun setChapter(
        storyTitle: String,
        chapterTitle: String,
        paragraphsList: List<String>,
        startIndex: Int = 0,
        autoPlay: Boolean = false
    ) {
        currentPlayJob?.cancel()
        prefetchJob?.cancel()
        _storyTitle.value = storyTitle
        _chapterTitle.value = chapterTitle
        _paragraphs.value = paragraphsList
        val validStart = startIndex.coerceIn(0, maxOf(0, paragraphsList.size - 1))
        _currentParagraphIndex.value = validStart

        if (autoPlay && paragraphsList.isNotEmpty()) {
            speakParagraph(validStart)
        }
    }

    fun play() {
        val player = exoPlayer ?: return
        if (player.playbackState == Player.STATE_READY && !player.isPlaying) {
            player.play()
            _isPlaying.value = true
            notifyServiceState()
        } else if (_paragraphs.value.isNotEmpty()) {
            speakParagraph(_currentParagraphIndex.value)
        }
    }

    fun pause() {
        exoPlayer?.pause()
        _isPlaying.value = false
        notifyServiceState()
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            play()
        }
    }

    fun seekToParagraph(index: Int) {
        val list = _paragraphs.value
        if (index in list.indices) {
            _currentParagraphIndex.value = index
            speakParagraph(index)
        }
    }

    fun nextParagraph() {
        val list = _paragraphs.value
        val nextIdx = _currentParagraphIndex.value + 1
        if (nextIdx in list.indices) {
            seekToParagraph(nextIdx)
        } else {
            // Đã hết chương, gọi listener sang chương kế tiếp
            onChapterFinishedListener?.invoke()
        }
    }

    fun previousParagraph() {
        val prevIdx = _currentParagraphIndex.value - 1
        if (prevIdx >= 0) {
            seekToParagraph(prevIdx)
        }
    }

    private fun getActiveEngine(): TtsEngine {
        val conf = _config.value
        return if (conf.engineType == TtsEngineType.OFFLINE_SYSTEM) {
            systemEngine
        } else {
            geminiEngine ?: systemEngine
        }
    }

    private fun speakParagraph(index: Int) {
        currentPlayJob?.cancel()
        prefetchJob?.cancel()

        val list = _paragraphs.value
        if (index !in list.indices) {
            _isPlaying.value = false
            return
        }

        _currentParagraphIndex.value = index
        notifyServiceState()

        val rawText = list[index]
        val cleanText = rawText.replace(Regex("<[^>]*>"), "").trim()
        if (cleanText.isBlank()) {
            // Bỏ qua đoạn rỗng
            if (index < list.size - 1) {
                speakParagraph(index + 1)
            } else {
                handleParagraphCompleted()
            }
            return
        }

        currentPlayJob = scope.launch {
            val conf = _config.value
            val engine = getActiveEngine()
            val cacheKey = cacheManager.generateKey(engine.engineId, conf.voiceId, conf.speechRate, cleanText)
            var audioFile = cacheManager.getAudioFile(cacheKey)

            if (audioFile == null || !audioFile.exists() || audioFile.length() == 0L) {
                _isLoading.value = true
                val synthesizedBytes = withContext(Dispatchers.IO) {
                    try {
                        engine.synthesize(cleanText, conf.voiceId, conf.speechRate)
                    } catch (e: Exception) {
                        null
                    }
                }
                _isLoading.value = false

                if (synthesizedBytes != null && synthesizedBytes.isNotEmpty()) {
                    audioFile = cacheManager.saveAudio(cacheKey, synthesizedBytes)
                }
            }

            if (audioFile != null && audioFile.exists()) {
                playAudioFile(audioFile)
                // Kích hoạt prefetch gối đầu 2 đoạn tiếp theo (N+1 và N+2) vào disk cache
                prefetchUpcomingParagraphs(index)
            } else {
                // Thử fallback sang System Engine nếu cloud engine lỗi
                if (engine != systemEngine) {
                    val fallbackKey = cacheManager.generateKey(systemEngine.engineId, conf.voiceId, conf.speechRate, cleanText)
                    val fallbackBytes = withContext(Dispatchers.IO) {
                        try {
                            systemEngine.synthesize(cleanText, conf.voiceId, conf.speechRate)
                        } catch (_: Exception) { null }
                    }
                    if (fallbackBytes != null) {
                        val fbFile = cacheManager.saveAudio(fallbackKey, fallbackBytes)
                        playAudioFile(fbFile)
                        prefetchUpcomingParagraphs(index)
                        return@launch
                    }
                }

                // Nếu vẫn thất bại, bỏ qua sang đoạn kế tiếp sau 500ms
                delay(500)
                if (index < list.size - 1) {
                    speakParagraph(index + 1)
                }
            }
        }
    }

    private fun playAudioFile(file: File) {
        val player = exoPlayer ?: return
        try {
            val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
            player.playbackParameters = PlaybackParameters(_speechRate.value)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
            _isPlaying.value = true
            notifyServiceState()
        } catch (_: Exception) {
            _isPlaying.value = false
        }
    }

    /**
     * Tải trước gối đầu 2 câu tiếp theo (N+1 và N+2) vào bộ nhớ đệm đĩa trong luồng ngầm IO.
     */
    private fun prefetchUpcomingParagraphs(fromIndex: Int) {
        val list = _paragraphs.value
        prefetchJob?.cancel()
        prefetchJob = scope.launch(Dispatchers.IO) {
            val conf = _config.value
            val engine = getActiveEngine()

            for (offset in 1..2) {
                val nextIdx = fromIndex + offset
                if (nextIdx !in list.indices) break
                val rawText = list[nextIdx]
                val cleanText = rawText.replace(Regex("<[^>]*>"), "").trim()
                if (cleanText.isBlank()) continue

                val cacheKey = cacheManager.generateKey(engine.engineId, conf.voiceId, conf.speechRate, cleanText)
                if (!cacheManager.hasAudio(cacheKey)) {
                    try {
                        val bytes = engine.synthesize(cleanText, conf.voiceId, conf.speechRate)
                        if (bytes != null && bytes.isNotEmpty()) {
                            cacheManager.saveAudio(cacheKey, bytes)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun handleParagraphCompleted() {
        val list = _paragraphs.value
        val curIdx = _currentParagraphIndex.value

        // Kiểm tra hẹn giờ kết thúc ở cuối chương
        if (_sleepTimerMinutes.value == SLEEP_TIMER_END_OF_CHAPTER && curIdx >= list.size - 1) {
            stop()
            setSleepTimer(SLEEP_TIMER_OFF)
            return
        }

        if (curIdx < list.size - 1) {
            speakParagraph(curIdx + 1)
        } else {
            // Đã hết toàn bộ đoạn văn của chương hiện tại
            _isPlaying.value = false
            notifyServiceState()
            onChapterFinishedListener?.invoke()
        }
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepTimerMinutes.value = minutes

        if (minutes == SLEEP_TIMER_OFF) {
            _sleepTimerSecondsRemaining.value = 0
            return
        }

        if (minutes == SLEEP_TIMER_END_OF_CHAPTER) {
            _sleepTimerSecondsRemaining.value = -1
            return
        }

        val totalSeconds = minutes * 60
        _sleepTimerSecondsRemaining.value = totalSeconds

        sleepTimerJob = scope.launch {
            var remaining = totalSeconds
            while (remaining > 0) {
                delay(1000L)
                remaining--
                _sleepTimerSecondsRemaining.value = remaining
            }
            // Hết giờ hẹn: Tạm dừng phát
            pause()
            _sleepTimerMinutes.value = SLEEP_TIMER_OFF
            _sleepTimerSecondsRemaining.value = 0
        }
    }

    private fun notifyServiceState() {
        onServiceStateChanged?.invoke(
            _isPlaying.value,
            _chapterTitle.value,
            _currentParagraphIndex.value,
            _paragraphs.value.size
        )
    }

    fun stop() {
        currentPlayJob?.cancel()
        prefetchJob?.cancel()
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        _isPlaying.value = false
        _isLoading.value = false
        notifyServiceState()
    }

    fun release() {
        stop()
        sleepTimerJob?.cancel()
        geminiEngine?.release()
        geminiEngine = null
        systemEngine.release()
        exoPlayer?.release()
        exoPlayer = null
        INSTANCE = null
    }
}
