package com.jkhx.player.ui.screens.stream

import com.jkhx.player.core.model.EpisodeData
import com.jkhx.player.core.model.EpisodeStream
import com.jkhx.player.core.model.Subtitle

/**
 * Model đại diện cho một luồng stream hợp nhất từ bất kỳ nguồn nào
 * (Native Plugin hoặc Stremio Addon).
 */
data class UnifiedStreamItem(
    val id: String,
    val providerId: String,
    val providerName: String,
    val groupName: String,
    val serverName: String,
    val title: String,
    val techInfo: String,
    val qualityBadge: String?,
    val streamType: String = "auto",
    val streamUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<Subtitle> = emptyList(),
    val isAddon: Boolean = false,
    val isWebView: Boolean = false,
    val targetEpisode: EpisodeData? = null
) {
    fun toEpisodeStream(): EpisodeStream {
        val resolvedType = when {
            isWebView -> "embed"
            streamType != "auto" -> streamType
            else -> com.jkhx.player.plugin.manager.stremio.StremioPluginWrapper.inferStreamType(
                streamUrl = streamUrl,
                rawType = null,
                metaText = "$title $techInfo $serverName"
            )
        }
        return EpisodeStream(
            type = resolvedType,
            url = streamUrl,
            headers = headers,
            subtitles = subtitles
        )
    }

    fun extractFileSize(): String? {
        val regex = Regex("""(?:📦\s*)?(\d+(?:\.\d+)?\s*(?:GB|MB|GiB|MiB))""", RegexOption.IGNORE_CASE)
        val match = regex.find(techInfo) ?: regex.find(title)
        return match?.groupValues?.get(1)?.trim()
    }

    companion object {
        fun detectQualityBadge(vararg texts: String?): String? {
            val combined = texts.filterNotNull().joinToString(" ").uppercase()
            return when {
                combined.contains("4K") || combined.contains("2160") || combined.contains("UHD") -> "4K"
                combined.contains("1080") || combined.contains("FHD") || combined.contains("FULL HD") -> "FHD"
                combined.contains("720") || combined.contains("HD") -> "HD"
                combined.contains("WEB") || combined.contains("EMBED") || combined.contains("BROWSER") -> "WEB"
                combined.contains("480") || combined.contains("360") || combined.contains("SD") -> "SD"
                else -> null
            }
        }
    }
}

