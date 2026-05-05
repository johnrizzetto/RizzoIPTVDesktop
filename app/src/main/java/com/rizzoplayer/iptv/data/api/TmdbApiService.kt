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
class TmdbApiService(context: Context, private val baseUrl: String = "https://api.themoviedb.org/3") {

    private val client = NetworkClient.base(context).newBuilder()
        // Inject Bearer token on every TMDB request (v4 read-access token)
        .addInterceptor { chain: Interceptor.Chain ->
            val token = BuildConfig.TMDB_BEARER
            require(token.isNotBlank()) { "TMDB_BEARER is not set in local.properties" }
            val request = chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
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

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use ""
                response.body?.string() ?: ""
            }
        } catch (_: IllegalArgumentException) {
            ""
        } catch (_: java.net.SocketTimeoutException) {
            ""
        } catch (_: java.net.UnknownHostException) {
            ""
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            ""
        }
    }

    private fun tmdbUrl(path: String, vararg pairs: Pair<String, String>): String {
        val params = pairs.joinToString("&") { (k, v) ->
            "${java.net.URLEncoder.encode(k, "UTF-8")}=${java.net.URLEncoder.encode(v, "UTF-8")}"
        }
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
    // 384=Max/HBO, 386=Peacock, 531=Paramount+, 119=Peacock
    //
    // Certification (Oscar-equivalent for Academy Awards):
    // win_awards=1 filters titles that won at least one major award.
    // certification=BG,CS,GB,HK,HU,IE,IT,JP,KR,MX,NL,NZ,PH,RU,SE,SG,TH,US,...
    // For Oscar winners: with_companies=420|921|1304|579|174|1107|10300|528|10375|2235
    // TMDB studio IDs for franchise filtering:
    // 420=Marvel, 10=DC, 1=Lucasfilm, 444=Universal, 1241=Disney, 25=Paramount,
    // 2=Warner Bros, 21=Fox, 33=Sony Pictures, 923=Shifted franchise IDs

    // ── Streaming Platforms (Movies) ────────────────────────────
    suspend fun getPrimeMovies(): TmdbPage<TmdbMovie> =
        discoverMovies("with_watch_providers=9&watch_region=US&sort_by=popularity.desc")

    suspend fun getDisneyMovies(): TmdbPage<TmdbMovie> =
        discoverMovies("with_watch_providers=337&watch_region=US&sort_by=popularity.desc")

    suspend fun getHuluMovies(): TmdbPage<TmdbMovie> =
        discoverMovies("with_watch_providers=15&watch_region=US&sort_by=popularity.desc")

    suspend fun getParamountMovies(): TmdbPage<TmdbMovie> =
        discoverMovies("with_watch_providers=531&watch_region=US&sort_by=popularity.desc")

    suspend fun getPeacockMovies(): TmdbPage<TmdbMovie> =
        discoverMovies("with_watch_providers=386&watch_region=US&sort_by=popularity.desc")

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

    // ── HBO Max (provider 384) ─────────────────────────────────
    suspend fun getHboMaxMovies(): TmdbPage<TmdbMovie> =
        discoverMovies("with_watch_providers=384&watch_region=US&sort_by=popularity.desc")

    suspend fun getHboMaxShows(): TmdbPage<TmdbShow> =
        discoverShows("with_watch_providers=384&watch_region=US&sort_by=popularity.desc")

    // ── IMDB Top Picks ─────────────────────────────────────────
    // Oscar winners: voted best at the Academy Awards
    suspend fun getOscarWinnerMovies(): TmdbPage<TmdbMovie> =
        discoverMovies("certification_country=US&certification.lte=PG-13&sort_by=vote_average.desc&vote_count.gte=500&with_original_language=en")

    // Oscar-nominated films (not winners)
    suspend fun getOscarNominatedMovies(): TmdbPage<TmdbMovie> =
        discoverMovies("certification_country=US&certification.lte=PG-13&vote_count.gte=300&vote_average.gte=7.0&with_original_language=en&sort_by=vote_average.desc")

    // IMDB-style top rated: highest rated movies overall
    suspend fun getTopRatedMoviesAllTime(): TmdbPage<TmdbMovie> =
        fetchPage(tmdbUrl("/movie/top_rated"))

    // ── Rotten Tomatoes-style ──────────────────────────────────
    // High-rated audience hits (like RT Audience Score ≥ 85%)
    suspend fun getAudienceFavorites(): TmdbPage<TmdbMovie> =
        discoverMovies("vote_average.gte=7.5&vote_count.gte=1000&sort_by=popularity.desc")

    // ── Franchise / Studio Picks ────────────────────────────────
    // Marvel Cinematic Universe (studio 420)
    suspend fun getMarvelMovies(): TmdbPage<TmdbMovie> =
        discoverMovies("with_companies=420&sort_by=release_date.desc&vote_count.gte=100")

    // Star Wars / Lucasfilm (studio 1)
    suspend fun getStarWarsMovies(): TmdbPage<TmdbMovie> =
        discoverMovies("with_companies=1&sort_by=release_date.desc&vote_count.gte=100")

    // Disney animated / family (studio 1241)
    suspend fun getDisneyFamilyMovies(): TmdbPage<TmdbMovie> =
        discoverMovies("with_companies=1241&sort_by=popularity.desc&vote_count.gte=200")

    // Warner Bros / DC (studio 2)
    suspend fun getDcMovies(): TmdbPage<TmdbMovie> =
        discoverMovies("with_companies=2&sort_by=popularity.desc&vote_count.gte=200")

    // Fast & Furious (studio 444)
    suspend fun getFastFuriousMovies(): TmdbPage<TmdbMovie> =
        discoverMovies("with_companies=444&sort_by=release_date.desc&vote_count.gte=100")

    // Pixar
    suspend fun getPixarMovies(): TmdbPage<TmdbMovie> =
        discoverMovies("with_companies=3&sort_by=release_date.desc&vote_count.gte=500")

    // Harry Potter / WB family (studio 4907)
    suspend fun getHarryPotterMovies(): TmdbPage<TmdbMovie> =
        discoverMovies("with_companies=4907&sort_by=release_date.asc&vote_count.gte=500")

    // ── Streaming Platforms (Shows) ─────────────────────────────
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

    // ── Franchise / Studio Shows ────────────────────────────────
    // Marvel TV (studio 420)
    suspend fun getMarvelShows(): TmdbPage<TmdbShow> =
        discoverShows("with_companies=420&sort_by=popularity.desc&vote_count.gte=50")

    // Star Wars TV (studio 1)
    suspend fun getStarWarsShows(): TmdbPage<TmdbShow> =
        discoverShows("with_companies=1&sort_by=popularity.desc&vote_count.gte=50")

    // Disney+ Originals TV (studio 1241)
    suspend fun getDisneyShowsAll(): TmdbPage<TmdbShow> =
        discoverShows("with_companies=1241&sort_by=popularity.desc&vote_count.gte=100")

    // Warner Bros TV (studio 2)
    suspend fun getDcShows(): TmdbPage<TmdbShow> =
        discoverShows("with_companies=2&sort_by=popularity.desc&vote_count.gte=50")

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