package com.jkhx.player.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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

@Serializable
data class PluginManifest(
    val apiVersion: Int = 1,
    val id: String,
    val name: String,
    val version: String,
    val author: String = "JP",
    val description: String? = null,
    val entry: String = "plugin.js",
    val language: String = "vi",
    val isAdult: Boolean = false,
    val types: List<String> = listOf("movie", "tv"),
    val capabilities: List<String> = listOf("home", "search", "detail", "stream")
)

@Serializable
data class RepositoryManifest(
    val name: String = "JKHX Player Plugins",
    val version: Int = 1,
    val plugins: List<RepositoryPluginItem> = emptyList()
)

@Serializable
data class RepositoryPluginItem(
    val id: String,
    val name: String,
    val version: String,
    val manifest: String,
    val entry: String,
    val isAdult: Boolean = false
)

