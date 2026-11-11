package com.jkhx.player.core.player.tts.novel

import android.media.AudioFormat
import kotlin.math.roundToInt

/**
 * Chuẩn hóa mọi nguồn dữ liệu PCM (từ System TTS WAV, MP3, Opus) về một định dạng đầu ra cố định duy nhất
 * cho AudioTrack trong toàn bộ phiên đọc:
 * - Sample Rate: 24000 Hz
 * - Channel: Mono (1 kênh)
 * - Encoding: PCM 16-bit Signed Little-Endian (2 bytes / frame)
 *
 * Bảo toàn nguyên vẹn khoảng nghỉ tự nhiên trong lời nói, không cắt âm đầu/cuối và không crossfade.
 */
object PcmNormalizer {

    const val TARGET_SAMPLE_RATE = 24000
    const val TARGET_CHANNEL_COUNT = 1
    const val TARGET_ENCODING = AudioFormat.ENCODING_PCM_16BIT
    const val BYTES_PER_FRAME = 2 // 16-bit Mono = 2 bytes / frame

    /**
     * Chuyển đổi RawPcmBuffer bất kỳ về mảng byte PCM chuẩn (24000Hz, Mono, 16-bit LE).
     */
    fun normalizeToTargetPcm(raw: RawPcmBuffer): ByteArray {
        if (raw.bytes.isEmpty()) return ByteArray(0)

        // Fast-path: Nếu nguồn đã đúng chuẩn 24000Hz, Mono, 16-bit LE thì trả về trực tiếp
        if (raw.sampleRate == TARGET_SAMPLE_RATE &&
            raw.channelCount == TARGET_CHANNEL_COUNT &&
            raw.pcmEncoding == AudioFormat.ENCODING_PCM_16BIT
        ) {
            val alignedSize = (raw.bytes.size / BYTES_PER_FRAME) * BYTES_PER_FRAME
            return if (alignedSize == raw.bytes.size) {
                raw.bytes
            } else {
                raw.bytes.copyOfRange(0, alignedSize)
            }
        }

        // Bước 1: Giải mã bit-depth & Downmix về mảng mẫu Mono (Float trong dải [-32768f, 32767f])
        val monoSamples = decodeAndDownmixToMonoFloat(raw)
        if (monoSamples.isEmpty()) return ByteArray(0)

        // Bước 2: Resample về TARGET_SAMPLE_RATE (24000 Hz) nếu cần
        val resampled = if (raw.sampleRate == TARGET_SAMPLE_RATE) {
            monoSamples
        } else {
            resampleLinear(monoSamples, raw.sampleRate, TARGET_SAMPLE_RATE)
        }

        // Bước 3: Lượng tử hóa về PCM 16-bit Signed Little-Endian (2 bytes / sample)
        val outBytes = ByteArray(resampled.size * BYTES_PER_FRAME)
        var byteIdx = 0
        for (i in resampled.indices) {
            val clamped = resampled[i].roundToInt().coerceIn(-32768, 32767)
            outBytes[byteIdx++] = (clamped and 0xFF).toByte()
            outBytes[byteIdx++] = ((clamped shr 8) and 0xFF).toByte()
        }
        return outBytes
    }

    private fun decodeAndDownmixToMonoFloat(raw: RawPcmBuffer): FloatArray {
        val bytes = raw.bytes
        val channels = raw.channelCount.coerceAtLeast(1)

        val bytesPerSample = when (raw.pcmEncoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_16BIT -> 2
            PcmAudioDecoder.INTERNAL_ENCODING_PCM_24BIT -> 3
            PcmAudioDecoder.INTERNAL_ENCODING_PCM_32BIT_INT -> 4
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            else -> 2
        }

        val frameSize = bytesPerSample * channels
        if (frameSize <= 0 || bytes.size < frameSize) return FloatArray(0)

        val frameCount = bytes.size / frameSize
        val mono = FloatArray(frameCount)
        val invChannels = 1.0f / channels.toFloat()

        var offset = 0
        for (frame in 0 until frameCount) {
            var sum = 0f
            for (ch in 0 until channels) {
                val sample = when (raw.pcmEncoding) {
                    AudioFormat.ENCODING_PCM_8BIT -> {
                        val u8 = bytes[offset].toInt() and 0xFF
                        ((u8 - 128) * 256).toFloat()
                    }
                    AudioFormat.ENCODING_PCM_16BIT -> {
                        val lo = bytes[offset].toInt() and 0xFF
                        val hi = bytes[offset + 1].toInt() // Giữ dấu (sign-extended)
                        ((hi shl 8) or lo).toFloat()
                    }
                    PcmAudioDecoder.INTERNAL_ENCODING_PCM_24BIT -> {
                        val b0 = bytes[offset].toInt() and 0xFF
                        val b1 = bytes[offset + 1].toInt() and 0xFF
                        val b2 = bytes[offset + 2].toInt() // Giữ dấu
                        val s24 = (b2 shl 16) or (b1 shl 8) or b0
                        s24.toFloat() / 256.0f
                    }
                    PcmAudioDecoder.INTERNAL_ENCODING_PCM_32BIT_INT -> {
                        val b0 = bytes[offset].toInt() and 0xFF
                        val b1 = bytes[offset + 1].toInt() and 0xFF
                        val b2 = bytes[offset + 2].toInt() and 0xFF
                        val b3 = bytes[offset + 3].toInt() // Giữ dấu
                        val s32 = (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
                        s32.toFloat() / 65536.0f
                    }
                    AudioFormat.ENCODING_PCM_FLOAT -> {
                        val b0 = bytes[offset].toInt() and 0xFF
                        val b1 = bytes[offset + 1].toInt() and 0xFF
                        val b2 = bytes[offset + 2].toInt() and 0xFF
                        val b3 = bytes[offset + 3].toInt() and 0xFF
                        val bits = (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
                        val flt = Float.fromBits(bits)
                        if (flt.isNaN() || flt.isInfinite()) 0f else (flt * 32767.0f).coerceIn(-32768f, 32767f)
                    }
                    else -> {
                        val lo = bytes[offset].toInt() and 0xFF
                        val hi = bytes[offset + 1].toInt()
                        ((hi shl 8) or lo).toFloat()
                    }
                }
                sum += sample
                offset += bytesPerSample
            }
            mono[frame] = if (channels == 1) sum else (sum * invChannels)
        }

        return mono
    }

    private fun resampleLinear(input: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
        val inFrames = input.size
        if (inFrames == 0 || srcRate <= 0 || dstRate <= 0) return FloatArray(0)
        if (srcRate == dstRate) return input

        val outFrames = ((inFrames.toLong() * dstRate.toLong()) / srcRate.toLong()).toInt()
        if (outFrames <= 0) return FloatArray(0)

        val output = FloatArray(outFrames)
        val ratio = srcRate.toDouble() / dstRate.toDouble()
        val maxIdx = inFrames - 1

        for (i in 0 until outFrames) {
            val srcPos = i * ratio
            val idx0 = srcPos.toInt().coerceIn(0, maxIdx)
            val idx1 = (idx0 + 1).coerceIn(0, maxIdx)
            val frac = (srcPos - idx0).toFloat()
            output[i] = input[idx0] + frac * (input[idx1] - input[idx0])
        }

        return output
    }
}
