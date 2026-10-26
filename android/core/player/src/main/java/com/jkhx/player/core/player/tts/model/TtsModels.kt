package com.jkhx.player.core.player.tts.model

import kotlinx.serialization.Serializable

enum class TtsEngineType(val id: String, val displayName: String, val isCloud: Boolean) {
    OFFLINE_SYSTEM("system", "Google TTS (Offline - Tích hợp máy)", false),
    GEMINI_38_FLASH_TTS("gemini-3.8-flash-tts", "Gemini 3.8 Flash TTS (Studio Voice)", true),
    GEMINI_38_FLASH_LITE_TTS("gemini-3.8-flash-lite-tts", "Gemini 3.8 Flash Lite TTS (Siêu nhanh)", true),
    GEMINI_31_FLASH_TTS("gemini-3.1-flash-tts", "Gemini 3.1 Flash TTS (Tự nhiên)", true),
    GEMINI_35_LIVE_TRANSLATE("gemini-3.5-live-translate", "Gemini 3.5 Live Translate (Dịch & Thuyết minh)", true)
}

data class TtsVoiceOption(
    val id: String,
    val name: String,
    val gender: String, // "male", "female"
    val region: String = "north", // "north", "south"
    val tag: String = "",
    val language: String = "vi"
)

object TtsVoices {
    val ALL = listOf(
        TtsVoiceOption("bao_nam", "Bảo Nam", "male", "north", "Nam • Bắc"),
        TtsVoiceOption("minh_quang", "Minh Quang", "male", "north", "Nam Trầm • Bắc"),
        TtsVoiceOption("tuan_khang", "Tuấn Khang", "male", "south", "Nam • Nam Bộ"),
        TtsVoiceOption("phuong_vy", "Phương Vy", "female", "south", "Nữ • Nam Bộ"),
        TtsVoiceOption("mai_chi", "Mai Chi", "female", "north", "Nữ • Bắc"),
        TtsVoiceOption("anh_duong", "Ánh Dương", "female", "north", "Nữ Trẻ • Bắc")
    )

    fun normalizeVoiceId(id: String): String = when (id.lowercase().trim()) {
        "alloy", "bao_nam" -> "bao_nam"
        "onyx", "minh_quang" -> "minh_quang"
        "echo", "tuan_khang" -> "tuan_khang"
        "fable", "phuong_vy" -> "phuong_vy"
        "nova", "female", "mai_chi" -> "mai_chi"
        "shimmer", "anh_duong" -> "anh_duong"
        else -> "bao_nam"
    }

    fun findVoice(id: String): TtsVoiceOption {
        val norm = normalizeVoiceId(id)
        return ALL.firstOrNull { it.id == norm } ?: ALL.first()
    }
}

data class TtsConfig(
    val isEnabled: Boolean = false,
    val engineType: TtsEngineType = TtsEngineType.GEMINI_38_FLASH_TTS,
    val voiceId: String = "bao_nam", // bao_nam, minh_quang, tuan_khang, phuong_vy, mai_chi, anh_duong
    val speechRate: Float = 1.15f,
    val duckingVolume: Float = 0.35f, // Âm lượng phim gốc khi có thuyết minh (20% - 50%)
    val apiEndpoint: String = "",
    val apiKey: String = "",
    val audioFormat: String = "opus"
)

data class TtsCueAudio(
    val cueIndex: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String,
    val audioFilePath: String? = null,
    val isSynthesized: Boolean = false
)
