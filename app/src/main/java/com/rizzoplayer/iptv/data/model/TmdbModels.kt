package com.rizzoplayer.iptv.data.model

import kotlinx.serialization.Serializable
import androidx.compose.runtime.Immutable

@Immutable
@Serializable
data class TmdbGenre(
    @Serializable(with = IntSerializer::class) val id: Int,
    val name: String
)

@Immutable
@Serializable
data class TmdbGenreResponse(
    val genres: List<TmdbGenre> = emptyList()
)

@Immutable
@Serializable
data class TmdbMovie(
    @Serializable(with = IntSerializer::class) val id: Int,
    val imdbId: String? = null,
    val title: String = "",
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val overview: String = "",
    val releaseDate: String = "",
    val rating: Float = 0f,
    @Serializable(with = IntSerializer::class) val voteCount: Int = 0,
    val runtime: Int? = null,
    val genreIds: List<Int> = emptyList()
)

@Immutable
@Serializable
data class TmdbExternalIds(
    val imdbId: String? = null
)

@Immutable
@Serializable
data class TmdbShow(
    @Serializable(with = IntSerializer::class) val id: Int,
    val externalIds: TmdbExternalIds? = null,
    val name: String = "",
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val overview: String = "",
    val firstAirDate: String = "",
    val rating: Float = 0f,
    @Serializable(with = IntSerializer::class) val voteCount: Int = 0,
    @Serializable(with = IntSerializer::class) val numberOfSeasons: Int = 0,
    val genreIds: List<Int> = emptyList()
) {
    val imdbId: String? get() = externalIds?.imdbId
}

@Immutable
@Serializable
data class TmdbSeason(
    @Serializable(with = IntSerializer::class) val seasonNumber: Int = 0,
    val name: String = "",
    @Serializable(with = IntSerializer::class) val episodeCount: Int = 0,
    val episodes: List<TmdbEpisode> = emptyList()
)

@Immutable
@Serializable
data class TmdbEpisode(
    @Serializable(with = IntSerializer::class) val id: Int,
    @Serializable(with = IntSerializer::class) val episodeNumber: Int = 0,
    @Serializable(with = IntSerializer::class) val seasonNumber: Int = 0,
    val airDate: String? = null,
    val name: String = "",
    val overview: String = "",
    val stillPath: String? = null,
    val runtime: Int? = null
)

@Immutable
@Serializable
data class TmdbPage<T>(
    val results: List<T> = emptyList(),
    @Serializable(with = IntSerializer::class) val page: Int = 1,
    @Serializable(with = IntSerializer::class) val totalPages: Int = 1
)

@Immutable
@Serializable
data class TorrentioStream(
    val url: String = "",
    val title: String = "",
    val name: String = ""
)

@Immutable
@Serializable
data class TorrentioResponse(
    val streams: List<TorrentioStream> = emptyList()
)