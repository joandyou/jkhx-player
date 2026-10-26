package com.jkhx.player.core.player.subtitle.model

import kotlinx.serialization.Serializable

@Serializable
data class OnlineSubtitleItem(
    val id: String,
    val source: String, // "OpenSubtitles", "SubDL", "Subsource"
    val name: String,
    val language: String, // "vi", "en", etc.
    val languageName: String, // "Tiếng Việt", "English"
    val releaseName: String? = null,
    val downloadUrl: String? = null,
    val fileId: String? = null,
    val isAiTranslated: Boolean = false,
    val rating: Float? = null,
    val downloadCount: Int? = null,
    val format: String = "srt" // "srt", "vtt"
)

data class SubtitleSearchQuery(
    val imdbId: String? = null,
    val tmdbId: String? = null,
    val query: String,
    val year: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val languages: List<String> = listOf("vi", "en")
)

data class SubtitleCue(
    val index: Int,
    val startTime: String, // e.g. "00:01:23,456"
    val endTime: String,   // e.g. "00:01:25,789"
    val text: String,
    val startTimeMs: Long = 0L,
    val endTimeMs: Long = 0L
)

@Serializable
data class SubtitleLanguageOption(
    val code: String,
    val nameVi: String,
    val nativeName: String,
    val isMandatory: Boolean = false // Tiếng Anh bắt buộc để hỗ trợ dịch AI Gemini
)

object SubtitleSupportedLanguages {
    val ALL: List<SubtitleLanguageOption> = listOf(
        SubtitleLanguageOption("vi", "Tiếng Việt", "Tiếng Việt", isMandatory = false),
        SubtitleLanguageOption("en", "Tiếng Anh", "English", isMandatory = true),
        SubtitleLanguageOption("zh", "Tiếng Trung", "中文", isMandatory = false),
        SubtitleLanguageOption("ko", "Tiếng Hàn", "한국어", isMandatory = false),
        SubtitleLanguageOption("ja", "Tiếng Nhật", "日本語", isMandatory = false),
        SubtitleLanguageOption("fr", "Tiếng Pháp", "Français", isMandatory = false),
        SubtitleLanguageOption("es", "Tiếng Tây Ban Nha", "Español", isMandatory = false),
        SubtitleLanguageOption("de", "Tiếng Đức", "Deutsch", isMandatory = false),
        SubtitleLanguageOption("ru", "Tiếng Nga", "Русский", isMandatory = false),
        SubtitleLanguageOption("th", "Tiếng Thái", "ไทย", isMandatory = false),
        SubtitleLanguageOption("id", "Tiếng Indonesia", "Bahasa Indonesia", isMandatory = false),
        SubtitleLanguageOption("pt", "Tiếng Bồ Đào Nha", "Português", isMandatory = false)
    )

    fun getDisplayName(code: String): String {
        val found = ALL.firstOrNull { it.code.equals(code, ignoreCase = true) }
        return found?.nameVi ?: code.uppercase()
    }

    fun getNativeName(code: String): String {
        val found = ALL.firstOrNull { it.code.equals(code, ignoreCase = true) }
        return found?.nativeName ?: code.uppercase()
    }

    fun normalizeLanguageCode(raw: String): String {
        val lower = raw.trim().lowercase()
        return when {
            lower == "vi" || lower == "vie" || lower.startsWith("viet") -> "vi"
            lower == "en" || lower == "eng" || lower.startsWith("eng") -> "en"
            lower == "zh" || lower == "zho" || lower == "chi" || lower.startsWith("chin") || lower.startsWith("mand") || lower.startsWith("cant") -> "zh"
            lower == "ko" || lower == "kor" || lower.startsWith("kor") -> "ko"
            lower == "ja" || lower == "jpn" || lower.startsWith("jap") -> "ja"
            lower == "fr" || lower == "fra" || lower == "fre" || lower.startsWith("fren") || lower.startsWith("fra") -> "fr"
            lower == "es" || lower == "spa" || lower.startsWith("span") || lower.startsWith("esp") -> "es"
            lower == "de" || lower == "deu" || lower == "ger" || lower.startsWith("germ") || lower.startsWith("deu") -> "de"
            lower == "ru" || lower == "rus" || lower.startsWith("russ") -> "ru"
            lower == "th" || lower == "tha" || lower.startsWith("thai") -> "th"
            lower == "id" || lower == "ind" || lower.startsWith("indo") -> "id"
            lower == "pt" || lower == "por" || lower == "pob" || lower.startsWith("port") -> "pt"
            else -> lower.take(2)
        }
    }
}

