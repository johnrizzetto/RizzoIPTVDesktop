package com.rizzoplayer.iptv.data.model

import com.google.gson.annotations.SerializedName

data class TmdbGenre(
    @SerializedName("id")   val id: Int,
    @SerializedName("name") val name: String
)

// Wrapper for /genre/movie/list and /genre/tv/list responses: {"genres": [...]}
data class TmdbGenreResponse(
    @SerializedName("genres") val genres: List<TmdbGenre> = emptyList()
)

data class TmdbMovie(
    @SerializedName("id")            val id: Int,
    @SerializedName("imdb_id")       val imdbId: String? = null,
    @SerializedName("title")         val title: String = "",
    @SerializedName("poster_path")   val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("overview")      val overview: String = "",
    @SerializedName("release_date")  val releaseDate: String = "",
    @SerializedName("vote_average")  val rating: Float = 0f,
    @SerializedName("vote_count")    val voteCount: Int = 0,
    @SerializedName("runtime")       val runtime: Int? = null,
    @SerializedName("genre_ids")     val genreIds: List<Int> = emptyList()
)

data class TmdbShow(
    @SerializedName("id")                val id: Int,
    @SerializedName("imdb_id")           val imdbId: String? = null,
    @SerializedName("name")              val name: String = "",
    @SerializedName("poster_path")       val posterPath: String? = null,
    @SerializedName("backdrop_path")     val backdropPath: String? = null,
    @SerializedName("overview")          val overview: String = "",
    @SerializedName("first_air_date")    val firstAirDate: String = "",
    @SerializedName("vote_average")      val rating: Float = 0f,
    @SerializedName("vote_count")        val voteCount: Int = 0,
    @SerializedName("number_of_seasons") val numberOfSeasons: Int = 0,
    @SerializedName("genre_ids")         val genreIds: List<Int> = emptyList()
)

data class TmdbSeason(
    @SerializedName("season_number")  val seasonNumber: Int = 0,
    @SerializedName("name")           val name: String = "",
    @SerializedName("episode_count")  val episodeCount: Int = 0,
    @SerializedName("episodes")       val episodes: List<TmdbEpisode> = emptyList()
)

data class TmdbEpisode(
    @SerializedName("id")             val id: Int = 0,
    @SerializedName("episode_number") val episodeNumber: Int = 0,
    @SerializedName("season_number")  val seasonNumber: Int = 0,
    @SerializedName("name")           val name: String = "",
    @SerializedName("overview")       val overview: String = "",
    @SerializedName("still_path")     val stillPath: String? = null,
    @SerializedName("runtime")        val runtime: Int? = null
)

data class TmdbPage<T>(
    @SerializedName("results")     val results: List<T> = emptyList(),
    @SerializedName("page")        val page: Int = 1,
    @SerializedName("total_pages") val totalPages: Int = 1
)

data class TorrentioStream(
    @SerializedName("url")   val url: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("name")  val name: String = ""
)

data class TorrentioResponse(
    @SerializedName("streams") val streams: List<TorrentioStream> = emptyList()
)
