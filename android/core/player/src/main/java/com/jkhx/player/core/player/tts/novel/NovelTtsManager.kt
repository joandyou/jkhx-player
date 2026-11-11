package com.jkhx.player.core.player.tts.novel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import com.jkhx.player.core.player.tts.cache.TtsCacheManager
import com.jkhx.player.core.player.tts.engine.GeminiTtsEngine
import com.jkhx.player.core.player.tts.engine.SystemTtsEngine
import com.jkhx.player.core.player.tts.engine.TtsEngine
import com.jkhx.player.core.player.tts.model.TtsConfig
import com.jkhx.player.core.player.tts.model.TtsEngineType
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Gói dữ liệu PCM đã chuẩn hóa (24kHz, 16-bit Mono) trong hàng đợi giới hạn bộ nhớ.
 */
private data class PcmPacket(
    val generationId: Long,
    val paragraphIndex: Int,
    val pcmBytes: ByteArray,
    val frameCount: Int,
    val isFirstOfParagraph: Boolean,
    val isLastOfParagraph: Boolean,
    val isEndOfChapterSentinel: Boolean = false
)

/**
 * Bản ghi ánh xạ dải Frame âm thanh của từng đoạn văn trên trục thời gian (Frame-based Timeline).
 */
private data class ParagraphTimelineEntry(
    val generationId: Long,
    val paragraphIndex: Int,
    val startFrame: Long,
    var endFrame: Long,
    var isComplete: Boolean
)

/**
 * Hàng đợi FIFO Producer-Consumer có giới hạn dung lượng theo byte (Bounded PCM Queue).
 * Giữ tối đa ~1.92 MB (~40 giây âm thanh 24kHz Mono 16-bit) trong RAM.
 */
private class BoundedPcmQueue(private val maxBytes: Long) {
    private val mutex = Mutex()
    private val queue = ArrayDeque<PcmPacket>()
    private var bufferedBytes: Long = 0L

    private val notEmptySignal = Channel<Unit>(Channel.CONFLATED)
    private val notFullSignal = Channel<Unit>(Channel.CONFLATED)

    suspend fun enqueue(packet: PcmPacket, activeGenProvider: () -> Long) {
        val packetSize = packet.pcmBytes.size.toLong()
        while (true) {
            if (packet.generationId != activeGenProvider()) return
            var added = false
            mutex.withLock {
                if (packet.generationId != activeGenProvider()) return
                if (packet.isEndOfChapterSentinel || bufferedBytes + packetSize <= maxBytes || queue.isEmpty()) {
                    queue.addLast(packet)
                    bufferedBytes += packetSize
                    added = true
                }
            }
            if (added) {
                notEmptySignal.trySend(Unit)
                return
            }
            notFullSignal.receive()
        }
    }

    suspend fun dequeue(expectedGen: Long, activeGenProvider: () -> Long): PcmPacket? {
        while (true) {
            if (expectedGen != activeGenProvider()) return null
            var result: PcmPacket? = null
            var freedBytes = false
            mutex.withLock {
                while (queue.isNotEmpty()) {
                    val head = queue.removeFirst()
                    bufferedBytes = (bufferedBytes - head.pcmBytes.size.toLong()).coerceAtLeast(0L)
                    freedBytes = true
                    if (head.generationId == expectedGen) {
                        result = head
                        break
                    }
                }
            }
            if (freedBytes) {
                notFullSignal.trySend(Unit)
            }
            if (result != null) {
                return result
            }
            if (expectedGen != activeGenProvider()) return null
            notEmptySignal.receive()
        }
    }

    suspend fun clear() {
        mutex.withLock {
            queue.clear()
            bufferedBytes = 0L
        }
        notFullSignal.trySend(Unit)
        notEmptySignal.trySend(Unit)
    }

    fun currentBufferedBytes(): Long = bufferedBytes
}

/**
 * Quản lý phát âm thanh Text-To-Speech chuyên dụng cho Truyện chữ (Novel Reader).
 * Kiến trúc:
 * - Một AudioTrack duy nhất ở chế độ MODE_STREAM (24000 Hz, Mono, PCM 16-bit) cho toàn phiên đọc.
 * - Giải mã WAV (RIFF parser) / MP3 / Opus và chuẩn hóa qua PcmNormalizer.
 * - Hàng đợi BoundedPcmQueue giới hạn RAM (~40s audio) kết hợp Disk Cache bền vững.
 * - Đồng bộ Highlight đoạn văn chính xác theo Frame-based Timeline (playbackHeadPosition).
 * - Kiểm soát Seek, Pause, Flush, Chuyển chương bằng generationId nguyên tử.
 */
class NovelTtsManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        const val SLEEP_TIMER_OFF = 0
        const val SLEEP_TIMER_END_OF_CHAPTER = -1
        const val HEADER_PARAGRAPH_INDEX = -1

        // 200ms mỗi gói PCM ở 24000 Hz, 16-bit Mono (4800 frames = 9600 bytes)
        private const val PACKET_SIZE_BYTES = 9600
        // Giới hạn RAM cho PCM Queue: 1.92 MB (~40 giây âm thanh)
        private const val MAX_BUFFERED_PCM_BYTES = 1_920_000L

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
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val systemEngine by lazy { SystemTtsEngine(context) }
    private var geminiEngine: GeminiTtsEngine? = null

    // Quản lý AudioTrack duy nhất cho phiên đọc
    private val audioTrackLock = Any()
    private var audioTrack: AudioTrack? = null

    // Quản lý thế hệ luồng phát (tăng mỗi khi seek / đổi chương / stop)
    private val generationId = AtomicLong(0L)
    private val pcmQueue = BoundedPcmQueue(MAX_BUFFERED_PCM_BYTES)

    // Trục thời gian theo Frame (Frame-based Timeline)
    private val timelineLock = Any()
    private val timelineEntries = ArrayDeque<ParagraphTimelineEntry>()
    @Volatile
    private var writtenFramesInGen: Long = 0L
    private var baseHeadPosition: Long = 0L
    private var lastRawHead: Long = 0L
    private var headWrapCount: Long = 0L
    @Volatile
    private var producerCompletedGen: Long = -1L
    @Volatile
    private var consumerCompletedGen: Long = -1L

    // State flows cho giao diện người dùng (giữ nguyên cấu trúc 1-1)
    private val _storyTitle = MutableStateFlow("")
    val storyTitle: StateFlow<String> = _storyTitle.asStateFlow()

    private val _chapterTitle = MutableStateFlow("")
    val chapterTitle: StateFlow<String> = _chapterTitle.asStateFlow()

    private val _paragraphs = MutableStateFlow<List<String>>(emptyList())
    val paragraphs: StateFlow<List<String>> = _paragraphs.asStateFlow()

    private val _currentParagraphIndex = MutableStateFlow(0)
    val currentParagraphIndex: StateFlow<Int> = _currentParagraphIndex.asStateFlow()

    private val _isReadingHeader = MutableStateFlow(false)
    val isReadingHeader: StateFlow<Boolean> = _isReadingHeader.asStateFlow()

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

    private var producerJob: Job? = null
    private var consumerJob: Job? = null
    private var timelinePollerJob: Job? = null
    private var prefetchDiskJob: Job? = null
    private var sleepTimerJob: Job? = null

    private var hasAudioFocus = false
    private var resumeOnFocusGain = false
    private var focusRequest: AudioFocusRequest? = null
    private var noisyReceiverRegistered = false

    var onChapterFinishedListener: (() -> Unit)? = null
    var onServiceStateChanged: ((isPlaying: Boolean, chapterTitle: String, paragraphIdx: Int, total: Int) -> Unit)? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        scope.launch(Dispatchers.Main) {
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    resumeOnFocusGain = false
                    hasAudioFocus = false
                    pauseInternal(abandonFocus = false)
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    if (_isPlaying.value) {
                        resumeOnFocusGain = true
                        pauseInternal(abandonFocus = false)
                    }
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    hasAudioFocus = true
                    if (resumeOnFocusGain) {
                        resumeOnFocusGain = false
                        play()
                    }
                }
            }
        }
    }

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pause()
            }
        }
    }

    init {
        updateEngine()
    }

    /**
     * Đảm bảo chỉ khởi tạo một AudioTrack duy nhất ở chế độ MODE_STREAM với format cố định:
     * 24000 Hz, CHANNEL_OUT_MONO, ENCODING_PCM_16BIT.
     */
    private fun ensureAudioTrackLocked(): AudioTrack? {
        val existing = audioTrack
        if (existing != null && existing.state == AudioTrack.STATE_INITIALIZED) {
            return existing
        }
        try {
            existing?.release()
        } catch (_: Exception) {}

        return try {
            val minBuf = AudioTrack.getMinBufferSize(
                PcmNormalizer.TARGET_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                PcmNormalizer.TARGET_ENCODING
            )
            val bufferSize = maxOf(minBuf * 4, 48000) // ~1s hardware buffer tại 24kHz 16-bit Mono

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(PcmNormalizer.TARGET_SAMPLE_RATE)
                        .setEncoding(PcmNormalizer.TARGET_ENCODING)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            track
        } catch (_: Exception) {
            audioTrack = null
            null
        }
    }

    private fun resetFrameCountersLocked(track: AudioTrack?) {
        writtenFramesInGen = 0L
        val raw = if (track != null && track.state == AudioTrack.STATE_INITIALIZED) {
            try {
                track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
            } catch (_: Exception) {
                0L
            }
        } else {
            0L
        }
        baseHeadPosition = raw
        lastRawHead = raw
        headWrapCount = 0L
    }

    /**
     * Tính toán số Frame thực tế đã phát ra loa trong thế hệ hiện tại (xử lý wrap-around 32-bit unsigned
     * và bù trừ chính xác baseHeadPosition sau khi pause/flush/seek).
     */
    private fun getPlayedFramesInCurrentGen(): Long {
        synchronized(audioTrackLock) {
            val track = audioTrack ?: return 0L
            if (track.state != AudioTrack.STATE_INITIALIZED) return 0L

            val raw = try {
                track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
            } catch (_: Exception) {
                return 0L
            }

            if (raw < lastRawHead) {
                // Kiểm tra tràn số 32-bit unsigned (0xFFFFFFFF -> 0)
                if (lastRawHead > 0xF000_0000L && raw < 0x1000_0000L) {
                    headWrapCount++
                } else {
                    // Một số HAL reset playbackHeadPosition về 0 ngay khi bắt đầu play sau flush()
                    baseHeadPosition = raw
                    headWrapCount = 0L
                }
            }
            lastRawHead = raw

            val absoluteHead = (headWrapCount shl 32) + raw
            val played = absoluteHead - baseHeadPosition
            return played.coerceIn(0L, writtenFramesInGen)
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

        val engineOrVoiceChanged = _config.value.engineType != sanitized.engineType ||
                _config.value.apiKey != sanitized.apiKey ||
                _config.value.apiEndpoint != sanitized.apiEndpoint ||
                _config.value.voiceId != sanitized.voiceId ||
                _config.value.audioFormat != sanitized.audioFormat

        val rateChanged = kotlin.math.abs(_speechRate.value - clampedRate) > 0.005f

        _config.value = sanitized
        _speechRate.value = clampedRate

        if (engineOrVoiceChanged) {
            updateEngine()
        }

        if ((engineOrVoiceChanged || rateChanged) && _isPlaying.value) {
            val restartIdx = if (_isReadingHeader.value) HEADER_PARAGRAPH_INDEX else _currentParagraphIndex.value
            seekInternal(restartIdx)
        }
    }

    /**
     * Thu hẹp dải tốc độ đọc: 0.95x, 1.0x, 1.1x, 1.15x, 1.25x
     */
    fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.95f, 1.25f)
        val changed = kotlin.math.abs(_speechRate.value - clamped) > 0.005f
        _speechRate.value = clamped
        _config.value = _config.value.copy(speechRate = clamped)

        if (changed && _isPlaying.value) {
            val restartIdx = if (_isReadingHeader.value) HEADER_PARAGRAPH_INDEX else _currentParagraphIndex.value
            seekInternal(restartIdx)
        }
    }

    /**
     * Nạp dữ liệu chương truyện chữ và chuẩn bị phát.
     * Nếu startIndex <= 0 và có tiêu đề chương, hệ thống đọc tiêu đề chương (index = -1) trước,
     * sau đó đọc nối tiếp từng đoạn văn (0 .. N-1).
     */
    fun setChapter(
        storyTitle: String,
        chapterTitle: String,
        paragraphsList: List<String>,
        startIndex: Int = 0,
        autoPlay: Boolean = false
    ) {
        _storyTitle.value = storyTitle
        _chapterTitle.value = chapterTitle
        _paragraphs.value = paragraphsList

        val cleanTitle = chapterTitle.replace(Regex("<[^>]*>"), "").trim()
        val initialIdx = if (startIndex <= 0 && cleanTitle.isNotBlank()) {
            HEADER_PARAGRAPH_INDEX
        } else {
            startIndex.coerceIn(0, maxOf(0, paragraphsList.size - 1))
        }

        _isReadingHeader.value = (initialIdx == HEADER_PARAGRAPH_INDEX)
        _currentParagraphIndex.value = initialIdx

        if (autoPlay && (paragraphsList.isNotEmpty() || cleanTitle.isNotBlank())) {
            seekInternal(initialIdx)
        } else {
            abortCurrentStreamAndFlush()
            notifyServiceState()
        }
    }

    fun play() {
        val paras = _paragraphs.value
        val hasTitle = _chapterTitle.value.isNotBlank()
        if (paras.isEmpty() && !hasTitle) return

        if (!requestAudioFocus()) return
        registerNoisyReceiver()

        // Nếu đang ở trạng thái tạm dừng và vẫn còn dữ liệu trong tiến trình của cùng generation, tiếp tục phát
        val currentGen = generationId.get()
        val canResumeExisting = producerJob?.isActive == true ||
                consumerJob?.isActive == true ||
                (writtenFramesInGen > getPlayedFramesInCurrentGen() && writtenFramesInGen > 0L)

        if (canResumeExisting && currentGen > 0L) {
            synchronized(audioTrackLock) {
                val track = ensureAudioTrackLocked()
                try {
                    track?.play()
                } catch (_: Exception) {}
            }
            _isPlaying.value = true
            notifyServiceState()
            startTimelinePoller(currentGen)
        } else {
            val startIdx = if (_isReadingHeader.value) HEADER_PARAGRAPH_INDEX else _currentParagraphIndex.value
            seekInternal(startIdx)
        }
    }

    fun pause() {
        resumeOnFocusGain = false
        pauseInternal(abandonFocus = false)
    }

    private fun pauseInternal(abandonFocus: Boolean) {
        timelinePollerJob?.cancel()
        synchronized(audioTrackLock) {
            try {
                if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                    audioTrack?.pause()
                }
            } catch (_: Exception) {}
        }
        _isPlaying.value = false
        _isLoading.value = false
        if (abandonFocus) {
            abandonAudioFocus()
        }
        notifyServiceState()
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            play()
        }
    }

    fun seekToHeader() {
        if (_chapterTitle.value.isNotBlank()) {
            seekInternal(HEADER_PARAGRAPH_INDEX)
        } else if (_paragraphs.value.isNotEmpty()) {
            seekInternal(0)
        }
    }

    fun seekToParagraph(index: Int) {
        if (index < 0) {
            seekToHeader()
            return
        }
        val list = _paragraphs.value
        if (index in list.indices) {
            seekInternal(index)
        }
    }

    fun nextParagraph() {
        val list = _paragraphs.value
        val curIdx = if (_isReadingHeader.value) HEADER_PARAGRAPH_INDEX else _currentParagraphIndex.value
        val nextIdx = curIdx + 1
        if (nextIdx in list.indices) {
            seekInternal(nextIdx)
        } else {
            onChapterFinishedListener?.invoke()
        }
    }

    fun previousParagraph() {
        val curIdx = if (_isReadingHeader.value) HEADER_PARAGRAPH_INDEX else _currentParagraphIndex.value
        val prevIdx = curIdx - 1
        if (prevIdx >= 0) {
            seekInternal(prevIdx)
        } else if (prevIdx == HEADER_PARAGRAPH_INDEX && _chapterTitle.value.isNotBlank()) {
            seekInternal(HEADER_PARAGRAPH_INDEX)
        }
    }

    /**
     * Thực thi Seek nguyên tử:
     * 1. Tăng generationId để vô hiệu hóa toàn bộ công việc và gói PCM của thế hệ cũ.
     * 2. Pause & Flush AudioTrack ngay lập tức.
     * 3. Xóa sạch BoundedPcmQueue và FrameTimeline.
     * 4. Ưu tiên tổng hợp/giải mã đoạn đích và phát ngay.
     */
    private fun seekInternal(targetParagraphIndex: Int) {
        if (!requestAudioFocus()) return
        registerNoisyReceiver()

        val newGen = abortCurrentStreamAndFlush()

        _isReadingHeader.value = (targetParagraphIndex == HEADER_PARAGRAPH_INDEX)
        _currentParagraphIndex.value = targetParagraphIndex
        _isPlaying.value = true
        notifyServiceState()

        startStreamingPipeline(newGen, targetParagraphIndex)
    }

    private fun abortCurrentStreamAndFlush(): Long {
        val newGen = generationId.incrementAndGet()

        producerJob?.cancel()
        consumerJob?.cancel()
        timelinePollerJob?.cancel()
        prefetchDiskJob?.cancel()

        // Dừng ngay yêu cầu synthesize đang chạy dở trong SystemTtsEngine (nếu có)
        systemEngine.stop()

        synchronized(timelineLock) {
            timelineEntries.clear()
        }

        synchronized(audioTrackLock) {
            val track = audioTrack
            if (track != null && track.state == AudioTrack.STATE_INITIALIZED) {
                try {
                    track.pause()
                    track.flush()
                } catch (_: Exception) {}
            }
            resetFrameCountersLocked(track)
        }

        producerCompletedGen = -1L
        consumerCompletedGen = -1L

        scope.launch {
            pcmQueue.clear()
        }

        return newGen
    }

    private fun startStreamingPipeline(gen: Long, startParagraphIndex: Int) {
        // 1. Khởi chạy Producer: Tổng hợp -> Giải mã -> Chuẩn hóa PCM -> Đẩy vào BoundedPcmQueue
        producerJob = scope.launch(Dispatchers.IO) {
            pcmQueue.clear()
            val paras = _paragraphs.value
            val maxIdx = paras.size - 1

            for (idx in startParagraphIndex..maxIdx) {
                if (!isActive || generationId.get() != gen) break

                val rawText = if (idx == HEADER_PARAGRAPH_INDEX) {
                    _chapterTitle.value
                } else {
                    paras.getOrNull(idx) ?: ""
                }

                val cleanText = rawText.replace(Regex("<[^>]*>"), "").trim()
                if (cleanText.isBlank()) continue

                // Kích hoạt prefetch ngầm ra Disk Cache cho đoạn kế tiếp nếu dùng Cloud Engine
                triggerCloudDiskPrefetch(idx + 1, gen)

                val hasBufferedAhead = (writtenFramesInGen - getPlayedFramesInCurrentGen()) > (PcmNormalizer.TARGET_SAMPLE_RATE / 5) ||
                        pcmQueue.currentBufferedBytes() > 0L

                if (!hasBufferedAhead && idx == startParagraphIndex) {
                    _isLoading.value = true
                }

                val audioFile = getOrSynthesizeAudioFile(cleanText, gen)
                if (!isActive || generationId.get() != gen) break

                if (audioFile == null || !audioFile.exists()) {
                    continue
                }

                val rawPcm = PcmAudioDecoder.decodeToPcm(audioFile)
                if (!isActive || generationId.get() != gen) break
                if (rawPcm == null || rawPcm.bytes.isEmpty()) continue

                val normalizedBytes = PcmNormalizer.normalizeToTargetPcm(rawPcm)
                if (!isActive || generationId.get() != gen) break
                if (normalizedBytes.isEmpty()) continue

                _isLoading.value = false

                // Cắt mảng PCM đã chuẩn hóa thành các PcmPacket 200ms (9600 bytes) để nạp vào Queue
                var offset = 0
                val totalBytes = normalizedBytes.size
                while (offset < totalBytes && isActive && generationId.get() == gen) {
                    val end = minOf(offset + PACKET_SIZE_BYTES, totalBytes)
                    val alignedEnd = offset + ((end - offset) / PcmNormalizer.BYTES_PER_FRAME) * PcmNormalizer.BYTES_PER_FRAME
                    if (alignedEnd <= offset) break

                    val slice = normalizedBytes.copyOfRange(offset, alignedEnd)
                    val frames = slice.size / PcmNormalizer.BYTES_PER_FRAME
                    val isFirst = (offset == 0)
                    val isLast = (alignedEnd >= totalBytes)

                    pcmQueue.enqueue(
                        PcmPacket(
                            generationId = gen,
                            paragraphIndex = idx,
                            pcmBytes = slice,
                            frameCount = frames,
                            isFirstOfParagraph = isFirst,
                            isLastOfParagraph = isLast
                        ),
                        activeGenProvider = { generationId.get() }
                    )
                    offset = alignedEnd
                }
            }

            if (isActive && generationId.get() == gen) {
                producerCompletedGen = gen
                pcmQueue.enqueue(
                    PcmPacket(
                        generationId = gen,
                        paragraphIndex = maxOf(0, paras.size - 1),
                        pcmBytes = ByteArray(0),
                        frameCount = 0,
                        isFirstOfParagraph = false,
                        isLastOfParagraph = false,
                        isEndOfChapterSentinel = true
                    ),
                    activeGenProvider = { generationId.get() }
                )
            }
        }

        // 2. Khởi chạy Consumer: Lấy PcmPacket từ Queue -> Ghi vào AudioTrack -> Cập nhật FrameTimeline
        consumerJob = scope.launch(Dispatchers.IO) {
            while (isActive && generationId.get() == gen) {
                val packet = pcmQueue.dequeue(gen) { generationId.get() } ?: break
                if (packet.generationId != gen || generationId.get() != gen) break

                if (packet.isEndOfChapterSentinel) {
                    consumerCompletedGen = gen
                    break
                }

                if (packet.pcmBytes.isEmpty() || packet.frameCount <= 0) continue

                // Tạm chờ nếu người dùng đang Pause (giữ nguyên gói hiện tại)
                while (!_isPlaying.value && isActive && generationId.get() == gen) {
                    delay(40L)
                }
                if (!isActive || generationId.get() != gen) break

                // Đăng ký dải Frame của đoạn văn vào FrameTimeline trước khi ghi xuống phần cứng
                synchronized(timelineLock) {
                    val startFrame = writtenFramesInGen
                    val endFrame = startFrame + packet.frameCount
                    val lastEntry = timelineEntries.lastOrNull()
                    if (lastEntry != null && lastEntry.generationId == gen && lastEntry.paragraphIndex == packet.paragraphIndex) {
                        lastEntry.endFrame = endFrame
                        if (packet.isLastOfParagraph) {
                            lastEntry.isComplete = true
                        }
                    } else {
                        timelineEntries.addLast(
                            ParagraphTimelineEntry(
                                generationId = gen,
                                paragraphIndex = packet.paragraphIndex,
                                startFrame = startFrame,
                                endFrame = endFrame,
                                isComplete = packet.isLastOfParagraph
                            )
                        )
                    }
                }

                val track = synchronized(audioTrackLock) {
                    val t = ensureAudioTrackLocked()
                    if (t != null && _isPlaying.value && t.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        try {
                            t.play()
                        } catch (_: Exception) {}
                    }
                    t
                } ?: break

                var bytesOffset = 0
                val totalToWrite = packet.pcmBytes.size
                while (bytesOffset < totalToWrite && isActive && generationId.get() == gen) {
                    if (!_isPlaying.value) {
                        delay(40L)
                        continue
                    }
                    val written = try {
                        track.write(
                            packet.pcmBytes,
                            bytesOffset,
                            totalToWrite - bytesOffset,
                            AudioTrack.WRITE_BLOCKING
                        )
                    } catch (_: Exception) {
                        AudioTrack.ERROR_INVALID_OPERATION
                    }

                    if (written > 0) {
                        bytesOffset += written
                        val framesWrittenNow = (written / PcmNormalizer.BYTES_PER_FRAME).toLong()
                        writtenFramesInGen += framesWrittenNow
                    } else if (written == AudioTrack.ERROR_DEAD_OBJECT) {
                        // Khôi phục AudioTrack nếu AudioServer khởi động lại
                        synchronized(audioTrackLock) {
                            try {
                                audioTrack?.release()
                            } catch (_: Exception) {}
                            audioTrack = null
                            val recreated = ensureAudioTrackLocked()
                            resetFrameCountersLocked(recreated)
                            try {
                                recreated?.play()
                            } catch (_: Exception) {}
                        }
                        break
                    } else {
                        delay(20L)
                    }
                }
            }
        }

        // 3. Khởi chạy Timeline & Continuity Poller: Đồng bộ Highlight theo Frame thực tế đã phát
        startTimelinePoller(gen)
    }

    private fun startTimelinePoller(gen: Long) {
        timelinePollerJob?.cancel()
        timelinePollerJob = scope.launch(Dispatchers.Main) {
            var lastUnderrunCount = synchronized(audioTrackLock) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    try { audioTrack?.underrunCount ?: 0 } catch (_: Exception) { 0 }
                } else 0
            }
            var stagnantEndTicks = 0
            var lastPlayedFrames = -1L

            while (isActive && generationId.get() == gen && _isPlaying.value) {
                val playedFrames = getPlayedFramesInCurrentGen()
                val writtenFrames = writtenFramesInGen
                val hardwareFramesAhead = (writtenFrames - playedFrames).coerceAtLeast(0L)
                val queueFramesAhead = pcmQueue.currentBufferedBytes() / PcmNormalizer.BYTES_PER_FRAME
                val totalAheadMs = ((hardwareFramesAhead + queueFramesAhead) * 1000L) / PcmNormalizer.TARGET_SAMPLE_RATE

                // Phát hiện underrun khi Producer chưa kịp cung cấp dữ liệu
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val curUnderruns = synchronized(audioTrackLock) {
                        try { audioTrack?.underrunCount ?: lastUnderrunCount } catch (_: Exception) { lastUnderrunCount }
                    }
                    if (curUnderruns > lastUnderrunCount && consumerCompletedGen != gen && totalAheadMs < 80L) {
                        _isLoading.value = true
                    } else if (totalAheadMs >= 120L || consumerCompletedGen == gen) {
                        _isLoading.value = false
                    }
                    lastUnderrunCount = curUnderruns
                }

                // Đối chiếu playedFrames với FrameTimeline để cập nhật Highlight chính xác
                var matchedParagraph: Int? = null
                synchronized(timelineLock) {
                    // Dọn các entry đã phát qua hoàn toàn
                    while (timelineEntries.size > 1) {
                        val first = timelineEntries.first()
                        if (first.isComplete && playedFrames >= first.endFrame) {
                            timelineEntries.removeFirst()
                        } else {
                            break
                        }
                    }

                    val activeEntry = timelineEntries.firstOrNull { entry ->
                        entry.generationId == gen && playedFrames >= entry.startFrame &&
                                (playedFrames < entry.endFrame || !entry.isComplete)
                    } ?: timelineEntries.firstOrNull { it.generationId == gen }

                    if (activeEntry != null) {
                        matchedParagraph = activeEntry.paragraphIndex
                    }
                }

                if (matchedParagraph != null) {
                    val targetIdx = matchedParagraph!!
                    val isHeader = (targetIdx == HEADER_PARAGRAPH_INDEX)
                    if (_currentParagraphIndex.value != targetIdx || _isReadingHeader.value != isHeader) {
                        _currentParagraphIndex.value = targetIdx
                        _isReadingHeader.value = isHeader
                        notifyServiceState()
                    }
                }

                // Kiểm tra kết thúc toàn bộ chương khi Consumer đã ghi hết và phần cứng đã phát tới frame cuối
                if (consumerCompletedGen == gen && producerCompletedGen == gen) {
                    if (playedFrames == lastPlayedFrames) {
                        stagnantEndTicks++
                    } else {
                        stagnantEndTicks = 0
                    }
                    lastPlayedFrames = playedFrames

                    val remainingFrames = writtenFrames - playedFrames
                    if (remainingFrames <= 240L || (stagnantEndTicks >= 4 && remainingFrames <= 2400L)) {
                        handleChapterCompleted(gen)
                        break
                    }
                }

                delay(35L)
            }
        }
    }

    private fun handleChapterCompleted(gen: Long) {
        if (generationId.get() != gen) return

        if (_sleepTimerMinutes.value == SLEEP_TIMER_END_OF_CHAPTER) {
            stop()
            setSleepTimer(SLEEP_TIMER_OFF)
            return
        }

        _isPlaying.value = false
        _isLoading.value = false
        notifyServiceState()
        onChapterFinishedListener?.invoke()
    }

    private fun getActiveEngine(): TtsEngine {
        val conf = _config.value
        return if (conf.engineType == TtsEngineType.OFFLINE_SYSTEM) {
            systemEngine
        } else {
            geminiEngine ?: systemEngine
        }
    }

    private suspend fun getOrSynthesizeAudioFile(cleanText: String, gen: Long): File? = withContext(Dispatchers.IO) {
        if (cleanText.isBlank() || generationId.get() != gen) return@withContext null

        val conf = _config.value
        val engine = getActiveEngine()
        val cacheKey = cacheManager.generateKey(engine.engineId, conf.voiceId, conf.speechRate, cleanText)
        var audioFile = cacheManager.getAudioFile(cacheKey)

        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0L) {
            val synthesizedBytes = try {
                engine.synthesize(cleanText, conf.voiceId, conf.speechRate)
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                null
            }

            if (generationId.get() != gen) return@withContext null

            if (synthesizedBytes != null && synthesizedBytes.isNotEmpty()) {
                audioFile = cacheManager.saveAudio(cacheKey, synthesizedBytes)
            }
        }

        if (audioFile != null && audioFile.exists()) {
            return@withContext audioFile
        }

        // Fallback sang System TTS Engine nếu Cloud Engine gặp lỗi
        if (engine != systemEngine && generationId.get() == gen) {
            val fallbackKey = cacheManager.generateKey(systemEngine.engineId, conf.voiceId, conf.speechRate, cleanText)
            val cachedFallback = cacheManager.getAudioFile(fallbackKey)
            if (cachedFallback != null && cachedFallback.exists()) {
                return@withContext cachedFallback
            }

            val fallbackBytes = try {
                systemEngine.synthesize(cleanText, conf.voiceId, conf.speechRate)
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                null
            }

            if (generationId.get() != gen) return@withContext null
            if (fallbackBytes != null && fallbackBytes.isNotEmpty()) {
                return@withContext cacheManager.saveAudio(fallbackKey, fallbackBytes)
            }
        }

        return@withContext null
    }

    /**
     * Khi dùng Cloud Engine (Gemini / Edge-TTS), tải trước gối đầu 2 câu tiếp theo vào Disk Cache.
     * (Với SystemTtsEngine offline, Producer đã chạy tuần tự qua Mutex nên không tạo job tranh chấp).
     */
    private fun triggerCloudDiskPrefetch(fromParagraphIndex: Int, gen: Long) {
        val conf = _config.value
        if (conf.engineType == TtsEngineType.OFFLINE_SYSTEM) return

        val engine = geminiEngine ?: return
        val list = _paragraphs.value
        prefetchDiskJob?.cancel()
        prefetchDiskJob = scope.launch(Dispatchers.IO) {
            for (offset in 0..1) {
                if (!isActive || generationId.get() != gen) break
                val targetIdx = fromParagraphIndex + offset
                if (targetIdx !in list.indices) break
                val cleanText = list[targetIdx].replace(Regex("<[^>]*>"), "").trim()
                if (cleanText.isBlank()) continue

                val cacheKey = cacheManager.generateKey(engine.engineId, conf.voiceId, conf.speechRate, cleanText)
                if (!cacheManager.hasAudio(cacheKey)) {
                    try {
                        val bytes = engine.synthesize(cleanText, conf.voiceId, conf.speechRate)
                        if (bytes != null && bytes.isNotEmpty() && generationId.get() == gen) {
                            cacheManager.saveAudio(cacheKey, bytes)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true
        val res = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
                .also { focusRequest = it }
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        hasAudioFocus = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        hasAudioFocus = false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(focusChangeListener)
            }
        } catch (_: Exception) {}
    }

    private fun registerNoisyReceiver() {
        if (noisyReceiverRegistered) return
        try {
            context.registerReceiver(
                becomingNoisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            )
            noisyReceiverRegistered = true
        } catch (_: Exception) {}
    }

    private fun unregisterNoisyReceiver() {
        if (!noisyReceiverRegistered) return
        noisyReceiverRegistered = false
        try {
            context.unregisterReceiver(becomingNoisyReceiver)
        } catch (_: Exception) {}
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
            pause()
            _sleepTimerMinutes.value = SLEEP_TIMER_OFF
            _sleepTimerSecondsRemaining.value = 0
        }
    }

    private fun notifyServiceState() {
        onServiceStateChanged?.invoke(
            _isPlaying.value,
            _chapterTitle.value,
            if (_isReadingHeader.value) HEADER_PARAGRAPH_INDEX else _currentParagraphIndex.value,
            _paragraphs.value.size
        )
    }

    fun stop() {
        resumeOnFocusGain = false
        abortCurrentStreamAndFlush()
        _isPlaying.value = false
        _isLoading.value = false
        unregisterNoisyReceiver()
        abandonAudioFocus()
        notifyServiceState()
    }

    fun release() {
        stop()
        sleepTimerJob?.cancel()
        geminiEngine?.release()
        geminiEngine = null
        systemEngine.release()
        synchronized(audioTrackLock) {
            try {
                audioTrack?.release()
            } catch (_: Exception) {}
            audioTrack = null
        }
        INSTANCE = null
    }
}
