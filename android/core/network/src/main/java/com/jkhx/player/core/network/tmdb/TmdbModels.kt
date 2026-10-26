package com.jkhx.player.core.network.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TmdbGenre(
    val id: Int,
    val name: String
)

@Serializable
data class TmdbCast(
    val id: Long? = null,
    val name: String,
    val character: String? = null,
    @SerialName("profile_path")
    val profilePath: String? = null
)

@Serializable
data class TmdbCrew(
    val id: Long? = null,
    val name: String,
    val job: String? = null,
    val department: String? = null
)

@Serializable
data class TmdbCredits(
    val cast: List<TmdbCast> = emptyList(),
    val crew: List<TmdbCrew> = emptyList()
)

@Serializable
data class TmdbProductionCountry(
    @SerialName("iso_3166_1")
    val iso: String? = null,
    val name: String
)

@Serializable
data class TmdbVideo(
    val id: String? = null,
    val key: String,
    val site: String? = null,
    val type: String? = null
)

@Serializable
data class TmdbVideos(
    val results: List<TmdbVideo> = emptyList()
)

@Serializable
data class TmdbExternalIds(
    @SerialName("imdb_id")
    val imdbId: String? = null
)

@Serializable
data class TmdbDetailResponse(
    val id: Long? = null,
    val title: String? = null, // Movie
    val name: String? = null, // TV
    @SerialName("original_title")
    val originalTitle: String? = null,
    @SerialName("original_name")
    val originalName: String? = null,
    val overview: String? = null,
    @SerialName("backdrop_path")
    val backdropPath: String? = null,
    @SerialName("poster_path")
    val posterPath: String? = null,
    @SerialName("vote_average")
    val voteAverage: Double? = null,
    @SerialName("vote_count")
    val voteCount: Int? = null,
    @SerialName("release_date")
    val releaseDate: String? = null,
    @SerialName("first_air_date")
    val firstAirDate: String? = null,
    val runtime: Int? = null,
    val status: String? = null,
    val tagline: String? = null,
    val genres: List<TmdbGenre> = emptyList(),
    @SerialName("production_countries")
    val productionCountries: List<TmdbProductionCountry> = emptyList(),
    val credits: TmdbCredits? = null,
    val videos: TmdbVideos? = null,
    @SerialName("imdb_id")
    val imdbId: String? = null,
    @SerialName("external_ids")
    val externalIds: TmdbExternalIds? = null
) {
    val effectiveImdbId: String?
        get() = imdbId ?: externalIds?.imdbId
}

@Serializable
data class TmdbItemResult(
    val id: Long,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title")
    val originalTitle: String? = null,
    @SerialName("original_name")
    val originalName: String? = null,
    val overview: String? = null,
    @SerialName("poster_path")
    val posterPath: String? = null,
    @SerialName("backdrop_path")
    val backdropPath: String? = null,
    @SerialName("vote_average")
    val voteAverage: Double? = null,
    @SerialName("vote_count")
    val voteCount: Int? = null,
    @SerialName("release_date")
    val releaseDate: String? = null,
    @SerialName("first_air_date")
    val firstAirDate: String? = null,
    @SerialName("genre_ids")
    val genreIds: List<Int> = emptyList(),
    @SerialName("media_type")
    val mediaType: String? = null
)

@Serializable
data class TmdbPagedResponse(
    val page: Int = 1,
    val results: List<TmdbItemResult> = emptyList(),
    @SerialName("total_pages")
    val totalPages: Int = 1,
    @SerialName("total_results")
    val totalResults: Int = 0
)

data class TmdbCatalogDefinition(
    val id: String,
    val name: String,
    val type: String, // "movie", "series", "all"
    val icon: String
)
