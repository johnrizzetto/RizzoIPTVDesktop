package com.rizzoplayer.iptv.data.api

import android.content.Context
import com.rizzoplayer.iptv.BuildConfig
import com.rizzoplayer.iptv.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

class TmdbApiService(context: Context) {

    private val client = NetworkClient.base(context).newBuilder()
        .addNetworkInterceptor { chain: Interceptor.Chain ->
            val response = chain.proceed(chain.request())
            val path = chain.request().url.encodedPath
            val (maxAge, swr) = when {
                path.startsWith("/3/genre") -> 604800 to 604800
                path.startsWith("/3/discover") -> 3600 to 86400
                path.startsWith("/3/movie/") && path.endsWith("/recommendations") -> 86400 to 604800
                path.contains("/movie/") || path.contains("/tv/") -> 86400 to 604800
                path.startsWith("/3/search/") -> 600 to 3600
                else -> 300 to 3600
            }
            response.newBuilder()
                .removeHeader("Cache-Control")
                .header("Cache-Control", "public, max-age=$maxAge, stale-while-revalidate=$swr")
                .build()
        }
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }
    private val baseUrl = "https://api.themoviedb.org/3"

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { it.body?.string() ?: "" }
    }

    private fun tmdbUrl(path: String, vararg pairs: Pair<String, String>): String {
        val params = pairs.joinToString("&") { (k, v) -> "$k=$v" }
        return "$baseUrl$path?api_key=${BuildConfig.TMDB_BEARER}&$params"
    }

    suspend fun getPopularMovies(page: Int = 1): TmdbPage<TmdbMovie> =
        fetchPage(tmdbUrl("/movie/popular", "page" to page.toString()))

    suspend fun getNowPlayingMovies(page: Int = 1): TmdbPage<TmdbMovie> =
        fetchPage(tmdbUrl("/movie/now_playing", "page" to page.toString()))

    suspend fun getTopRatedMovies(page: Int = 1): TmdbPage<TmdbMovie> =
        fetchPage(tmdbUrl("/movie/top_rated", "page" to page.toString()))

    suspend fun getPopularShows(page: Int = 1): TmdbPage<TmdbShow> =
        fetchPage(tmdbUrl("/tv/popular", "page" to page.toString()))

    suspend fun getTopRatedShows(page: Int = 1): TmdbPage<TmdbShow> =
        fetchPage(tmdbUrl("/tv/top_rated", "page" to page.toString()))

    suspend fun getOnTheAirShows(page: Int = 1): TmdbPage<TmdbShow> =
        fetchPage(tmdbUrl("/tv/on_the_air", "page" to page.toString()))

    suspend fun getMovieDetail(movieId: Int): TmdbMovie? =
        fetchObject(tmdbUrl("/movie/$movieId", "append_to_response" to "external_ids"))

    suspend fun getShowDetail(showId: Int): TmdbShow? =
        fetchObject(tmdbUrl("/tv/$showId", "append_to_response" to "external_ids"))

    suspend fun getShowSeason(showId: Int, seasonNumber: Int): TmdbSeason? =
        fetchObject(tmdbUrl("/tv/$showId/season/$seasonNumber"))

    suspend fun searchMovies(query: String, page: Int = 1): TmdbPage<TmdbMovie> =
        fetchPage(tmdbUrl("/search/movie", "query" to query, "page" to page.toString()))

    suspend fun searchShows(query: String, page: Int = 1): TmdbPage<TmdbShow> =
        fetchPage(tmdbUrl("/search/tv", "query" to query, "page" to page.toString()))

    suspend fun getMovieGenres(): TmdbGenreResponse =
        fetchObject(tmdbUrl("/genre/movie/list")) ?: TmdbGenreResponse()

    suspend fun getTvGenres(): TmdbGenreResponse =
        fetchObject(tmdbUrl("/genre/tv/list")) ?: TmdbGenreResponse()

    suspend fun getTrendingMovies(): TmdbPage<TmdbMovie> =
        fetchPage(tmdbUrl("/trending/movie/week"))

    suspend fun getTrendingShows(): TmdbPage<TmdbShow> =
        fetchPage(tmdbUrl("/trending/tv/week"))

    suspend fun discoverMovies(queryParams: String): TmdbPage<TmdbMovie> =
        fetchPage("$baseUrl/discover/movie?api_key=${BuildConfig.TMDB_BEARER}&$queryParams")

    suspend fun discoverShows(queryParams: String): TmdbPage<TmdbShow> =
        fetchPage("$baseUrl/discover/tv?api_key=${BuildConfig.TMDB_BEARER}&$queryParams")

    private suspend inline fun <reified T> fetchPage(url: String): TmdbPage<T> =
        withContext(Dispatchers.IO) {
            val text = get(url)
            try {
                json.decodeFromString<TmdbPage<T>>(text)
            } catch (e: Exception) {
                TmdbPage(emptyList(), 1, 1)
            }
        }

    private suspend inline fun <reified T> fetchObject(url: String): T? =
        withContext(Dispatchers.IO) {
            val text = get(url)
            try { json.decodeFromString<T>(text) } catch (e: Exception) { null }
        }
}