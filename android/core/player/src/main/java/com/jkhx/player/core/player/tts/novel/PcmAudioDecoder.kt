package com.jkhx.player.core.player.tts.novel

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

/**
 * Dữ liệu PCM nguyên bản sau khi bóc tách từ WAV hoặc giải mã từ MediaCodec.
 */
data class RawPcmBuffer(
    val bytes: ByteArray,
    val sampleRate: Int,
    val channelCount: Int,
    val pcmEncoding: Int,
    val bitsPerSample: Int
)

/**
 * Bộ giải mã âm thanh TTS sang Raw PCM.
 * - Với WAV: Phân tích đầy đủ cấu trúc RIFF chunks (không giả định header 44 bytes),
 *   tìm chính xác chunk "fmt " và "data", kiểm tra encoding, sampleRate, channelCount, bitDepth.
 *   Chỉ bypass MediaCodec khi xác nhận PCM tương thích hoàn toàn.
 * - Với MP3 / Opus / WebM / WAV nén: Giải mã bằng MediaExtractor + MediaCodec chuẩn của Android.
 */
object PcmAudioDecoder {

    private const val WAVE_FORMAT_PCM = 0x0001
    private const val WAVE_FORMAT_IEEE_FLOAT = 0x0003
    private const val WAVE_FORMAT_EXTENSIBLE = 0xFFFE

    // Hằng số định dạng nội bộ bổ sung cho 24-bit và 32-bit integer khi parse WAV
    const val INTERNAL_ENCODING_PCM_24BIT = 1024
    const val INTERNAL_ENCODING_PCM_32BIT_INT = 1032

    fun decodeToPcm(audioFile: File): RawPcmBuffer? {
        if (!audioFile.exists() || audioFile.length() <= 12L) return null

        // 1. Kiểm tra nếu là RIFF WAVE hợp lệ và chứa PCM tương thích thì đọc trực tiếp
        try {
            val wavPcm = tryParseCompatibleWav(audioFile)
            if (wavPcm != null && wavPcm.bytes.isNotEmpty()) {
                return wavPcm
            }
        } catch (_: Exception) {
            // Nếu parse WAV thất bại, chuyển sang MediaCodec bên dưới
        }

        // 2. Giải mã bằng MediaExtractor + MediaCodec (dành cho MP3, Opus, Ogg, WebM, WAV ADPCM...)
        return decodeWithMediaCodec(audioFile)
    }

    /**
     * Phân tích RIFF/WAVE chunks theo đúng đặc tả RIFF:
     * Duyệt qua từng sub-chunk ("fmt ", "JUNK", "LIST", "fact", "data"...),
     * xử lý word-alignment (padding byte khi chunkSize lẻ).
     */
    private fun tryParseCompatibleWav(file: File): RawPcmBuffer? {
        val fileBytes = file.readBytes()
        if (fileBytes.size < 12) return null

        // Kiểm tra chữ ký "RIFF" (0..3) và "WAVE" (8..11)
        if (fileBytes[0] != 'R'.code.toByte() ||
            fileBytes[1] != 'I'.code.toByte() ||
            fileBytes[2] != 'F'.code.toByte() ||
            fileBytes[3] != 'F'.code.toByte() ||
            fileBytes[8] != 'W'.code.toByte() ||
            fileBytes[9] != 'A'.code.toByte() ||
            fileBytes[10] != 'V'.code.toByte() ||
            fileBytes[11] != 'E'.code.toByte()
        ) {
            return null
        }

        var fmtFound = false
        var effectiveFormat = 0
        var numChannels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var blockAlign = 0

        var dataOffset = -1
        var dataLength = 0

        var offset = 12
        val totalSize = fileBytes.size

        while (offset + 8 <= totalSize) {
            val c0 = fileBytes[offset].toInt().and(0xFF).toChar()
            val c1 = fileBytes[offset + 1].toInt().and(0xFF).toChar()
            val c2 = fileBytes[offset + 2].toInt().and(0xFF).toChar()
            val c3 = fileBytes[offset + 3].toInt().and(0xFF).toChar()
            val chunkId = "$c0$c1$c2$c3"

            val chunkSize = readUInt32LE(fileBytes, offset + 4)
            val chunkDataStart = offset + 8
            if (chunkDataStart > totalSize) break

            val availableChunkBytes = (totalSize - chunkDataStart).toLong()
            val safeChunkSize = minOf(chunkSize, availableChunkBytes).toInt()

            when (chunkId) {
                "fmt " -> {
                    if (safeChunkSize < 16) return null
                    val rawAudioFormat = readUInt16LE(fileBytes, chunkDataStart)
                    numChannels = readUInt16LE(fileBytes, chunkDataStart + 2)
                    sampleRate = readUInt32LE(fileBytes, chunkDataStart + 4).toInt()
                    blockAlign = readUInt16LE(fileBytes, chunkDataStart + 12)
                    bitsPerSample = readUInt16LE(fileBytes, chunkDataStart + 14)

                    effectiveFormat = if (rawAudioFormat == WAVE_FORMAT_EXTENSIBLE) {
                        if (safeChunkSize >= 40) {
                            // SubFormat GUID có 2 byte đầu là mã format thực tế
                            readUInt16LE(fileBytes, chunkDataStart + 24)
                        } else {
                            0
                        }
                    } else {
                        rawAudioFormat
                    }
                    fmtFound = true
                }
                "data" -> {
                    dataOffset = chunkDataStart
                    dataLength = safeChunkSize
                    if (fmtFound) {
                        break
                    }
                }
            }

            // Chuẩn RIFF yêu cầu mỗi chunk căn lề chẵn 2-byte (nếu chunkSize lẻ sẽ có 1 pad byte)
            val paddedSize = if (chunkSize % 2L != 0L) chunkSize + 1L else chunkSize
            val nextOffset = chunkDataStart.toLong() + paddedSize
            if (nextOffset <= offset || nextOffset > totalSize) break
            offset = nextOffset.toInt()
        }

        if (!fmtFound || dataOffset < 12 || dataLength <= 0) return null
        if (sampleRate !in 8000..96000) return null
        if (numChannels !in 1..8) return null

        val pcmEncoding = when (effectiveFormat) {
            WAVE_FORMAT_PCM -> {
                when (bitsPerSample) {
                    8 -> AudioFormat.ENCODING_PCM_8BIT
                    16 -> AudioFormat.ENCODING_PCM_16BIT
                    24 -> INTERNAL_ENCODING_PCM_24BIT
                    32 -> INTERNAL_ENCODING_PCM_32BIT_INT
                    else -> return null
                }
            }
            WAVE_FORMAT_IEEE_FLOAT -> {
                if (bitsPerSample == 32) {
                    AudioFormat.ENCODING_PCM_FLOAT
                } else {
                    return null
                }
            }
            else -> return null
        }

        val bytesPerSample = bitsPerSample / 8
        val expectedBlockAlign = bytesPerSample * numChannels
        if (expectedBlockAlign <= 0) return null
        if (blockAlign > 0 && blockAlign != expectedBlockAlign) return null

        // Đảm bảo độ dài dữ liệu chia hết cho kích thước 1 frame
        val alignedLength = (dataLength / expectedBlockAlign) * expectedBlockAlign
        if (alignedLength <= 0 || dataOffset + alignedLength > totalSize) return null

        val pcmBytes = fileBytes.copyOfRange(dataOffset, dataOffset + alignedLength)
        return RawPcmBuffer(
            bytes = pcmBytes,
            sampleRate = sampleRate,
            channelCount = numChannels,
            pcmEncoding = pcmEncoding,
            bitsPerSample = bitsPerSample
        )
    }

    /**
     * Giải mã các định dạng nén (MP3, Opus, Ogg, WebM...) sang Raw PCM bằng MediaCodec.
     */
    private fun decodeWithMediaCodec(file: File): RawPcmBuffer? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || inputFormat == null) return null
            extractor.selectTrack(audioTrackIndex)

            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return null
            var sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                24000
            }
            var channelCount = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                1
            }
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val outputStream = ByteArrayOutputStream(maxOf(32768, file.length().toInt() * 4))
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val timeoutUs = 10_000L

            while (!outputDone) {
                if (!inputDone) {
                    val inputBufferIndex = codec.dequeueInputBuffer(timeoutUs)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer: ByteBuffer? = codec.getInputBuffer(inputBufferIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                val presentationTimeUs = extractor.sampleTime
                                codec.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    sampleSize,
                                    maxOf(0L, presentationTimeUs),
                                    0
                                )
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                when {
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        if (newFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            pcmEncoding = newFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                    }
                    outputBufferIndex >= 0 -> {
                        val outputBuffer: ByteBuffer? = codec.getOutputBuffer(outputBufferIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val chunk = ByteArray(bufferInfo.size)
                            outputBuffer.get(chunk)
                            outputStream.write(chunk)
                        }
                        codec.releaseOutputBuffer(outputBufferIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            val decodedBytes = outputStream.toByteArray()
            if (decodedBytes.isEmpty()) return null

            val bitsPerSample = when (pcmEncoding) {
                AudioFormat.ENCODING_PCM_8BIT -> 8
                AudioFormat.ENCODING_PCM_FLOAT -> 32
                else -> 16
            }

            return RawPcmBuffer(
                bytes = decodedBytes,
                sampleRate = sampleRate.coerceIn(8000, 96000),
                channelCount = channelCount.coerceIn(1, 8),
                pcmEncoding = pcmEncoding,
                bitsPerSample = bitsPerSample
            )
        } catch (_: Exception) {
            return null
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {}
            try {
                codec?.release()
            } catch (_: Exception) {}
            try {
                extractor.release()
            } catch (_: Exception) {}
        }
    }

    private fun readUInt16LE(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readUInt32LE(bytes: ByteArray, offset: Int): Long {
        return (bytes[offset].toLong() and 0xFFL) or
                ((bytes[offset + 1].toLong() and 0xFFL) shl 8) or
                ((bytes[offset + 2].toLong() and 0xFFL) shl 16) or
                ((bytes[offset + 3].toLong() and 0xFFL) shl 24)
    }
}
