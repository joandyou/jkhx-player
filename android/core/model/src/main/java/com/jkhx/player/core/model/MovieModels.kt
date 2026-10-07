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
    val providerId: String? = null
)

@Serializable
data class HomeSection(
    val title: String,
    val items: List<MovieItem> = emptyList()
)

@Serializable
data class HomeResult(
    val sections: List<HomeSection> = emptyList()
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
    val type: String, // m3u8, mp4, dash, embed
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<Subtitle> = emptyList()
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
    val type: String? = null, // series, single, hoathinh, tvshows
    val status: String? = null, // completed, ongoing, trailer
    val quality: String? = null,
    val language: String? = null,
    val duration: String? = null,
    val currentEpisode: String? = null,
    val totalEpisodes: String? = null,
    val description: String? = null,
    val actors: List<String> = emptyList(),
    val directors: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val rating: Double? = null,
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
    val types: List<String> = listOf("movie", "tv"),
    val supportedTypes: List<String> = listOf("movie", "tv"),
    val formats: List<String> = listOf("m3u8", "mp4"),
    val logo: String? = null,
    val contentLanguage: List<String> = listOf("vi"),
    val defaultDomain: String? = null,
    val customDomain: String? = null,
    val repoUrl: String? = null,
    val enabled: Boolean = true,
    val hasSettings: Boolean = false
)

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

