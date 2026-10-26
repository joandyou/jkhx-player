package com.jkhx.player.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class ContentId(
    val providerId: String,
    val slug: String
) {
    override fun toString(): String = "$providerId:$slug"

    companion object {
        fun fromString(value: String): ContentId {
            val parts = value.split(":", limit = 2)
            return if (parts.size == 2) ContentId(parts[0], parts[1]) else ContentId("", value)
        }
    }
}

@Serializable
data class MovieItem(
    val slug: String,
    val name: String,
    val originName: String? = null,
    val thumbUrl: String? = null,
    val posterUrl: String? = null,
    val year: Int? = null,
    val type: String? = null,
    val quality: String? = null,
    val language: String? = null,
    val providerId: String? = null,
    val category: String? = null,
    val episodeCurrent: String? = null,
    val rating: Double? = null,
    val tmdbId: String? = null,
    val country: String? = null
)

@Serializable
data class FilterOption(
    val name: String,
    val slug: String
)

@Serializable
data class PluginFilterConfig(
    val genres: List<FilterOption> = emptyList(),
    val countries: List<FilterOption> = emptyList(),
    val years: List<FilterOption> = emptyList(),
    val sortFields: List<FilterOption> = emptyList(),
    val sortTypes: List<FilterOption> = emptyList()
)

@Serializable
data class PluginSettingItem(
    val key: String,
    val title: String,
    val description: String? = null,
    val type: String = "text", // "text", "select", "boolean"
    val defaultValue: String = "",
    val currentValue: String? = null,
    val options: List<FilterOption> = emptyList()
)

@Serializable
data class PluginHealthStatus(
    val status: String = "ok", // "ok", "degraded", "blocked", "error"
    val message: String? = null,
    val latencyMs: Long? = null
)

@Serializable
data class MenuSubItem(
    val name: String? = null,
    val title: String? = null,
    val slug: String
) {
    val displayTitle: String get() = title ?: name ?: slug
}

@Serializable
data class MenuItem(
    val title: String,
    val isDropdown: Boolean = false,
    val prefix: String? = null,
    val type: String? = null,
    val slug: String? = null,
    val items: List<MenuSubItem> = emptyList(),
    val children: List<MenuSubItem> = emptyList()
) {
    val actualItems: List<MenuSubItem> get() = if (items.isNotEmpty()) items else children
    val hasDropdown: Boolean get() = isDropdown || actualItems.isNotEmpty()
    val categoryType: String get() = prefix ?: type ?: "danh-sach"
}

@Serializable
data class HomeSection(
    val title: String,
    val items: List<MovieItem> = emptyList(),
    val type: String? = null,
    val slug: String? = null
)

@Serializable
data class HomeResult(
    val sections: List<HomeSection> = emptyList(),
    val quickCategories: List<HomeSection> = emptyList()
)

@Serializable
data class SearchResult(
    val items: List<MovieItem> = emptyList(),
    val page: Int = 1,
    val hasNext: Boolean = false
)

@Serializable
data class EpisodeData(
    val id: String,
    val name: String,
    val slug: String,
    val number: Int? = null,
    val season: Int? = null,
    val serverName: String? = null,
    val data: JsonElement? = null
)

@Serializable
data class EpisodeServer(
    val serverName: String,
    val episodes: List<EpisodeData> = emptyList()
)

@Serializable
data class Subtitle(
    val label: String,
    val language: String = "vi",
    val url: String,
    val format: String = "vtt" // vtt, srt
)

@Serializable
data class EpisodeStream(
    val type: String, // m3u8, mp4, dash, embed, image_list, novel, text
    val url: String = "",
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<Subtitle> = emptyList(),
    val images: List<String> = emptyList(),
    val content: String? = null
)

@Serializable
data class CastMember(
    val id: Long? = null,
    val name: String,
    val character: String? = null,
    val profileUrl: String? = null
)

@Serializable
data class MovieDetail(
    val slug: String,
    val name: String,
    val originName: String? = null,
    val thumbUrl: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val year: Int? = null,
    val type: String? = null, // series, single, hoathinh, tvshows, comic, novel
    val status: String? = null, // completed, ongoing, trailer
    val quality: String? = null,
    val language: String? = null,
    val duration: String? = null,
    val currentEpisode: String? = null,
    val totalEpisodes: String? = null,
    val description: String? = null,
    val actors: List<String> = emptyList(),
    val castMembers: List<CastMember> = emptyList(),
    val directors: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val rating: Double? = null,
    val voteCount: Int? = null,
    val tagline: String? = null,
    val trailerUrl: String? = null,
    val tmdbId: String? = null,
    val imdbId: String? = null,
    val episodes: List<EpisodeServer> = emptyList()
)

object LenientStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientString", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): String {
        return if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            if (element is JsonPrimitive) element.content else element.toString()
        } else {
            decoder.decodeString()
        }
    }
    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

@Serializable
data class PluginManifest(
    val apiVersion: Int = 1,
    val id: String,
    val name: String,
    @Serializable(with = LenientStringSerializer::class)
    val version: String = "1.0.0",
    val author: String? = null,
    val description: String? = null,
    val manifest: String? = null,
    val entry: String? = null,
    val filename: String? = null,
    val language: String = "vi",
    val isAdult: Boolean = false,
    val isComic: Boolean = false,
    val isNovel: Boolean = false,
    val isLive: Boolean = false,
    val types: List<String> = emptyList(),
    val supportedTypes: List<String> = emptyList(),
    val formats: List<String> = listOf("m3u8", "mp4"),
    val logo: String? = null,
    val icon: String? = null,
    val iconUrl: String? = null,
    val contentLanguage: List<String> = listOf("vi"),
    val defaultDomain: String? = null,
    val customDomain: String? = null,
    val repoUrl: String? = null,
    val enabled: Boolean = true,
    val hasSettings: Boolean = false,
    val isStremioAddon: Boolean = false,
    val hasCatalogs: Boolean = true
) {
    val effectiveIconUrl: String?
        get() = iconUrl ?: icon ?: logo
    val categoryType: String
        get() = when {
            isStremioAddon -> "stremio"
            isNovel || types.any { it.equals("novel", ignoreCase = true) } -> "novel"
            isComic || types.any { it.equals("comic", ignoreCase = true) } -> "comic"
            isLive || types.any { it.equals("live", ignoreCase = true) } -> "live"
            isAdult || types.any { it.equals("adult", ignoreCase = true) } -> "adult"
            else -> "movie_tv"
        }

    val displayTypeLabel: String
        get() = when (categoryType) {
            "stremio" -> "STREMIO"
            "novel" -> "TIỂU THUYẾT"
            "comic" -> "COMIC"
            "live" -> "LIVE"
            "adult" -> "ADULT"
            else -> {
                val effective = types.ifEmpty { supportedTypes }
                if (effective.isNotEmpty()) {
                    effective.joinToString(", ").uppercase()
                } else {
                    "MOVIE, TV"
                }
            }
        }
}

@Serializable
data class RepositoryManifest(
    val name: String = "Plugins Repository",
    val description: String? = null,
    @Serializable(with = LenientStringSerializer::class)
    val version: String = "1.0.0",
    val scrapers: List<PluginManifest> = emptyList(),
    val plugins: List<PluginManifest> = emptyList()
) {
    val allScrapers: List<PluginManifest>
        get() = scrapers.ifEmpty { plugins }
}

