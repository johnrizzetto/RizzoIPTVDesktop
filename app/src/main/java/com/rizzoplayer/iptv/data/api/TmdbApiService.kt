package com.rizzoplayer.iptv.data.api

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rizzoplayer.iptv.BuildConfig
import com.rizzoplayer.iptv.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class TmdbApiService(context: Context? = null) {

    private val client = OkHttpClient.Builder().apply {
        if (context != null) {
            cache(Cache(File(context.cacheDir, "okhttp_tmdb_cache"), 50L * 1024 * 1024))
        }
        connectTimeout(10, TimeUnit.SECONDS)
        readTimeout(15, TimeUnit.SECONDS)
        protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        connectionPool(ConnectionPool(10, 2, TimeUnit.MINUTES))
        addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("Authorization", "Bearer ${BuildConfig.TMDB_BEARER}")
                .header("accept", "application/json")
                .build()
            chain.proceed(req)
        }
    }.build()

    private val gson = Gson()
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
            val json = get(url)
            try {
                val type = TypeToken.getParameterized(TmdbPage::class.java, T::class.java).type
                gson.fromJson<TmdbPage<T>>(json, type) ?: TmdbPage(emptyList(), 1, 1)
            } catch (e: Exception) {
                TmdbPage(emptyList(), 1, 1)
            }
        }

    private suspend inline fun <reified T> fetchObject(url: String): T? =
        withContext(Dispatchers.IO) {
            val json = get(url)
            try { gson.fromJson(json, T::class.java) } catch (e: Exception) { null }
        }
}