package com.rizzoplayer.iptv.data.api

import android.content.Context
import com.rizzoplayer.iptv.BuildConfig
import com.rizzoplayer.iptv.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

@OptIn(ExperimentalSerializationApi::class)
class TmdbApiService(context: Context) {

    private val client = NetworkClient.base(context).newBuilder()
        // Inject Bearer token on every TMDB request (v4 read-access token)
        .addInterceptor { chain: Interceptor.Chain ->
            val request = chain.request().newBuilder()
                .header("Authorization", "Bearer ${BuildConfig.TMDB_BEARER}")
                .build()
            chain.proceed(request)
        }
        // stale-while-revalidate cache policy on responses
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
        namingStrategy = JsonNamingStrategy.SnakeCase
    }
    private val baseUrl = "https://api.themoviedb.org/3"

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { it.body?.string() ?: "" }
    }

    private fun tmdbUrl(path: String, vararg pairs: Pair<String, String>): String {
        val params = pairs.joinToString("&") { (k, v) -> "$k=$v" }
        return if (params.isEmpty()) "$baseUrl$path" else "$baseUrl$path?$params"
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
        fetchPage("$baseUrl/discover/movie?$queryParams")

    suspend fun discoverShows(queryParams: String): TmdbPage<TmdbShow> =
        fetchPage("$baseUrl/discover/tv?$queryParams")

    // TMDB Watch Provider IDs (US):
    // 8=Netflix, 9=Amazon Prime, 15=Hulu, 337=Disney+, 350=Apple TV+,
    // 384=Max/HBO, 386=Peacock, 531=Paramount+

    // ── Streaming Platforms (Movies) ────────────────────────────
    suspend fun getPrimeMovies(): TmdbPage<TmdbMovie> =
        discoverMovies("with_watch_providers=9&watch_region=US&sort_by=popularity.desc")

    suspend fun getDisneyMovies(): TmdbPage<TmdbMovie> =
        discoverMovies("with_watch_providers=337&watch_region=US&sort_by=popularity.desc")

    suspend fun getHuluMovies(): TmdbPage<TmdbMovie> =
        discoverMovies("with_watch_providers=15&watch_region=US&sort_by=popularity.desc")

    suspend fun getParamountMovies(): TmdbPage<TmdbMovie> =
        discoverMovies("with_watch_providers=531&watch_region=US&sort_by=popularity.desc")

    // ── Streaming Platforms (Shows) ─────────────────────────────
    suspend fun getPrimeShows(): TmdbPage<TmdbShow> =
        discoverShows("with_watch_providers=9&watch_region=US&sort_by=popularity.desc")

    suspend fun getDisneyShows(): TmdbPage<TmdbShow> =
        discoverShows("with_watch_providers=337&watch_region=US&sort_by=popularity.desc")

    suspend fun getHuluShows(): TmdbPage<TmdbShow> =
        discoverShows("with_watch_providers=15&watch_region=US&sort_by=popularity.desc")

    suspend fun getParamountShows(): TmdbPage<TmdbShow> =
        discoverShows("with_watch_providers=531&watch_region=US&sort_by=popularity.desc")

    suspend fun getPeacockShows(): TmdbPage<TmdbShow> =
        discoverShows("with_watch_providers=386&watch_region=US&sort_by=popularity.desc")

    // ── Moods & Discovery (Movies) ───────────────────────────────
    suspend fun getNewReleaseMovies(): TmdbPage<TmdbMovie> {
        val cutoff = java.time.LocalDate.now().minusDays(90).toString()
        return discoverMovies("primary_release_date.gte=$cutoff&sort_by=popularity.desc&vote_count.gte=50")
    }

    suspend fun getCriticallyAcclaimedMovies(): TmdbPage<TmdbMovie> =
        discoverMovies("vote_average.gte=8.0&vote_count.gte=500&sort_by=vote_average.desc")

    suspend fun getBoxOfficeMovies(): TmdbPage<TmdbMovie> =
        discoverMovies("sort_by=revenue.desc&vote_count.gte=100")

    suspend fun getClassicMovies(): TmdbPage<TmdbMovie> =
        discoverMovies("primary_release_date.lte=2000-01-01&vote_average.gte=7.5&vote_count.gte=200&sort_by=vote_average.desc")

    suspend fun getKoreanMovies(): TmdbPage<TmdbMovie> =
        discoverMovies("with_original_language=ko&sort_by=popularity.desc&vote_count.gte=50")

    suspend fun getUpcomingMovies(): TmdbPage<TmdbMovie> =
        fetchPage(tmdbUrl("/movie/upcoming"))

    // ── Moods & Discovery (Shows) ────────────────────────────────
    suspend fun getCriticallyAcclaimedShows(): TmdbPage<TmdbShow> =
        discoverShows("vote_average.gte=8.0&vote_count.gte=200&sort_by=vote_average.desc")

    suspend fun getAnimeShows(): TmdbPage<TmdbShow> =
        discoverShows("with_genres=16&with_original_language=ja&sort_by=popularity.desc")

    suspend fun getRealityShows(): TmdbPage<TmdbShow> =
        discoverShows("with_genres=10764&sort_by=popularity.desc")

    suspend fun getDocumentaryShows(): TmdbPage<TmdbShow> =
        discoverShows("with_genres=99&sort_by=popularity.desc")

    suspend fun getMiniSeries(): TmdbPage<TmdbShow> =
        discoverShows("with_type=3&sort_by=popularity.desc&vote_count.gte=50")

    suspend fun getKidsShows(): TmdbPage<TmdbShow> =
        discoverShows("with_genres=10762&sort_by=popularity.desc")

    suspend fun getKoreanDramas(): TmdbPage<TmdbShow> =
        discoverShows("with_original_language=ko&sort_by=popularity.desc&vote_count.gte=30")

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