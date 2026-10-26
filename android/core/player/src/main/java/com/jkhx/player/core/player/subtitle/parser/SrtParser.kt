package com.jkhx.player.core.player.subtitle.parser

import com.jkhx.player.core.player.subtitle.model.SubtitleCue
import java.util.Locale

object SrtParser {

    private val TAG_REGEX = Regex("<[^>]*>")
    private val ASS_TAG_REGEX = Regex("\\{\\\\[^}]*\\}")
    private val HTML_ENTITIES = mapOf(
        "&nbsp;" to " ",
        "&amp;" to "&",
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&#39;" to "'",
        "&apos;" to "'"
    )

    fun cleanVttText(text: String): String {
        var clean = text.replace("\uFEFF", "").replace(TAG_REGEX, "").replace(ASS_TAG_REGEX, "")
        for ((k, v) in HTML_ENTITIES) {
            clean = clean.replace(k, v)
        }
        return clean.trim()
    }

    /**
     * Chuyển chuỗi mốc thời gian (00:01:23,456 hoặc 00:01:23.456) thành mili-giây
     */
    fun timecodeToMs(timecode: String): Long {
        val clean = timecode.trim().replace(',', '.')
        val parts = clean.split(':')
        return try {
            when (parts.size) {
                3 -> {
                    val hours = parts[0].toLong()
                    val minutes = parts[1].toLong()
                    val secondsParts = parts[2].split('.')
                    val seconds = secondsParts[0].toLong()
                    val millis = if (secondsParts.size > 1) {
                        secondsParts[1].padEnd(3, '0').take(3).toLong()
                    } else 0L
                    (hours * 3600 + minutes * 60 + seconds) * 1000 + millis
                }
                2 -> {
                    val minutes = parts[0].toLong()
                    val secondsParts = parts[1].split('.')
                    val seconds = secondsParts[0].toLong()
                    val millis = if (secondsParts.size > 1) {
                        secondsParts[1].padEnd(3, '0').take(3).toLong()
                    } else 0L
                    (minutes * 60 + seconds) * 1000 + millis
                }
                else -> 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Chuyển mili-giây thành định dạng SRT chuẩn (HH:mm:ss,SSS)
     */
    fun msToSrtTimecode(totalMs: Long): String {
        val safeMs = if (totalMs < 0) 0L else totalMs
        val hours = safeMs / 3600000
        val minutes = (safeMs % 3600000) / 60000
        val seconds = (safeMs % 60000) / 1000
        val millis = safeMs % 1000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    /**
     * Bóc tách toàn bộ nội dung file SRT hoặc WebVTT thành danh sách SubtitleCue
     */
    fun parse(content: String): List<SubtitleCue> {
        val normalized = content.removePrefix("\uFEFF").replace("\r\n", "\n").replace("\r", "\n")
        val lines = normalized.lines()
        val result = mutableListOf<SubtitleCue>()

        var currentIndex = 1
        var currentStartTime = ""
        var currentEndTime = ""
        val currentTextLines = mutableListOf<String>()

        var currentStartMs = 0L
        var currentEndMs = 0L
        var inHeaderBlock = false

        fun flushCue() {
            if (currentStartTime.isNotBlank() && currentEndTime.isNotBlank() && currentTextLines.isNotEmpty()) {
                val fullText = currentTextLines.map { cleanVttText(it) }
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
                    .trim()
                if (fullText.isNotBlank()) {
                    result.add(
                        SubtitleCue(
                            index = currentIndex,
                            startTime = currentStartTime,
                            endTime = currentEndTime,
                            text = fullText,
                            startTimeMs = currentStartMs,
                            endTimeMs = currentEndMs
                        )
                    )
                    currentIndex++
                }
            }
            currentStartTime = ""
            currentEndTime = ""
            currentStartMs = 0L
            currentEndMs = 0L
            currentTextLines.clear()
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                inHeaderBlock = false
                flushCue()
                continue
            }

            // Bỏ qua header WebVTT (WEBVTT, NOTE, STYLE, REGION)
            if (trimmed.startsWith("WEBVTT", ignoreCase = true) ||
                trimmed.startsWith("NOTE", ignoreCase = true) ||
                trimmed.startsWith("STYLE", ignoreCase = true) ||
                trimmed.startsWith("REGION", ignoreCase = true)
            ) {
                inHeaderBlock = true
                continue
            }

            if (inHeaderBlock) {
                continue
            }

            // Kiểm tra dòng mốc thời gian: "00:01:23,456 --> 00:01:25,789"
            if (trimmed.contains("-->")) {
                val timeParts = trimmed.split("-->")
                if (timeParts.size == 2) {
                    // Nếu trước đó đang có cue dở dang chưa flush
                    if (currentStartTime.isNotBlank()) {
                        flushCue()
                    }
                    val startRaw = timeParts[0].trim().substringBefore(' ')
                    val endRaw = timeParts[1].trim().substringBefore(' ')
                    currentStartMs = timecodeToMs(startRaw)
                    currentEndMs = timecodeToMs(endRaw)
                    currentStartTime = msToSrtTimecode(currentStartMs)
                    currentEndTime = msToSrtTimecode(currentEndMs)
                    continue
                }
            }

            // Nếu dòng chỉ là số thứ tự index
            if (currentStartTime.isBlank() && trimmed.all { it.isDigit() }) {
                try {
                    currentIndex = trimmed.toInt()
                } catch (_: Exception) {}
                continue
            }

            // Dòng nội dung câu thoại
            if (currentStartTime.isNotBlank()) {
                currentTextLines.add(line)
            }
        }
        flushCue()

        return result
    }

    /**
     * Xuất danh sách SubtitleCue thành định dạng file SRT hoàn chỉnh
     */
    fun formatToSrt(cues: List<SubtitleCue>): String {
        val sb = StringBuilder()
        for ((idx, cue) in cues.withIndex()) {
            sb.append(idx + 1).append("\n")
            sb.append(cue.startTime).append(" --> ").append(cue.endTime).append("\n")
            sb.append(cue.text).append("\n\n")
        }
        return sb.toString().trimEnd() + "\n"
    }

    /**
     * Tinh chỉnh độ lệch thời gian phụ đề theo offsetMs (+/- mili-giây)
     */
    fun applyOffset(cues: List<SubtitleCue>, offsetMs: Long): List<SubtitleCue> {
        if (offsetMs == 0L) return cues
        return cues.map { cue ->
            val baseStart = if (cue.startTimeMs > 0L) cue.startTimeMs else timecodeToMs(cue.startTime)
            val baseEnd = if (cue.endTimeMs > 0L) cue.endTimeMs else timecodeToMs(cue.endTime)
            val startMs = (baseStart + offsetMs).coerceAtLeast(0L)
            val endMs = (baseEnd + offsetMs).coerceAtLeast(startMs + 100L)
            cue.copy(
                startTime = msToSrtTimecode(startMs),
                endTime = msToSrtTimecode(endMs),
                startTimeMs = startMs,
                endTimeMs = endMs
            )
        }
    }
}

