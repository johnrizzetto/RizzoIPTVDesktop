package com.rizzoplayer.iptv.data.repository

import com.rizzoplayer.iptv.BuildConfig
import com.rizzoplayer.iptv.data.api.TmdbApiService
import com.rizzoplayer.iptv.data.api.TorrentioService
import com.rizzoplayer.iptv.data.local.DiskCache
import com.rizzoplayer.iptv.data.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

class TmdbRepository(
    private val tmdb: TmdbApiService,
    private val torrentio: TorrentioService,
    private val diskCache: DiskCache,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }
) {
    companion object {
        const val TTL_CATALOGS = 6 * 60 * 60 * 1000L
        const val TTL_GENRES   = 7 * 24 * 60 * 60 * 1000L
        const val TTL_DETAIL  = 24 * 60 * 60 * 1000L
        const val TTL_SEARCH  = 10 * 60 * 1000L
        const val TTL_TORRENTIO = 20 * 60 * 1000L
    }

    private val inFlightRequests = java.util.concurrent.ConcurrentHashMap<String, Deferred<Any>>()

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> coalesced(key: String, block: suspend () -> T): T {
        return (inFlightRequests.getOrPut(key) {
            coroutineScope {
                async(Dispatchers.IO) {
                    block() as Any
                }.also { it.invokeOnCompletion { inFlightRequests.remove(key) } }
            }
        } as Deferred<T>).await()
    }

    private suspend inline fun <reified T> cachedList(
        key: String,
        ttlMs: Long,
        coalesceKey: String? = null,
        crossinline fetch: suspend () -> List<T>
    ): List<T> {
        diskCache.get(key, ttlMs)?.let { text ->
            withContext(Dispatchers.Default) {
                try { json.decodeFromString<List<T>>(text) } catch (e: Exception) { null }
            }?.let { return it }
        }
        return try {
            val result = if (coalesceKey != null) coalesced(coalesceKey) { fetch() } else fetch()
            val text = withContext(Dispatchers.Default) { json.encodeToString(result) }
            diskCache.put(key, text)
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend inline fun <reified T> cachedObject(
        key: String,
        ttlMs: Long,
        coalesceKey: String? = null,
        crossinline fetch: suspend () -> T?
    ): T? {
        diskCache.get(key, ttlMs)?.let { text ->
            return withContext(Dispatchers.Default) {
                try { json.decodeFromString<T>(text) } catch (e: Exception) { null }
            }
        }
        return try {
            val result = if (coalesceKey != null) coalesced(coalesceKey) { fetch() } else fetch() ?: return null
            val text = withContext(Dispatchers.Default) { json.encodeToString(result) }
            diskCache.put(key, text)
            result
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getMovieGenres(): List<TmdbGenre> = cachedList("tmdb_v3_movie_genres", TTL_GENRES) {
        tmdb.getMovieGenres().genres
    }

    suspend fun getTvGenres(): List<TmdbGenre> = cachedList("tmdb_v3_tv_genres", TTL_GENRES) {
        tmdb.getTvGenres().genres
    }

    suspend fun getPopularMovies(): List<TmdbMovie> = cachedList("tmdb_v3_popular_movies", TTL_CATALOGS) {
        tmdb.getPopularMovies().results
    }

    suspend fun getTrendingMovies(): List<TmdbMovie> = cachedList("tmdb_v3_trending_movies", TTL_CATALOGS) {
        tmdb.getTrendingMovies().results
    }

    suspend fun getTopRatedMovies(): List<TmdbMovie> = cachedList("tmdb_v3_toprated_movies", TTL_CATALOGS) {
        tmdb.getTopRatedMovies().results
    }

    suspend fun getNowPlayingMovies(): List<TmdbMovie> = cachedList("tmdb_v3_nowplaying_movies", TTL_CATALOGS) {
        tmdb.getNowPlayingMovies().results
    }

    suspend fun getPopularShows(): List<TmdbShow> = cachedList("tmdb_v3_popular_shows", TTL_CATALOGS) {
        tmdb.getPopularShows().results
    }

    suspend fun getTrendingShows(): List<TmdbShow> = cachedList("tmdb_v3_trending_shows", TTL_CATALOGS) {
        tmdb.getTrendingShows().results
    }

    suspend fun getTopRatedShows(): List<TmdbShow> = cachedList("tmdb_v3_toprated_shows", TTL_CATALOGS) {
        tmdb.getTopRatedShows().results
    }

    suspend fun getOnTheAirShows(): List<TmdbShow> = cachedList("tmdb_v3_ontheair_shows", TTL_CATALOGS) {
        tmdb.getOnTheAirShows().results
    }

    suspend fun getNetflixMovies(): List<TmdbMovie> = cachedList("tmdb_v3_netflix_movies", TTL_CATALOGS) {
        tmdb.discoverMovies("with_watch_providers=8&watch_region=US&sort_by=popularity.desc").results
    }

    suspend fun getNetflixShows(): List<TmdbShow> = cachedList("tmdb_v3_netflix_shows", TTL_CATALOGS) {
        tmdb.discoverShows("with_networks=213&sort_by=popularity.desc").results
    }

    suspend fun getAppleMovies(): List<TmdbMovie> = cachedList("tmdb_v3_apple_movies", TTL_CATALOGS) {
        tmdb.discoverMovies("with_watch_providers=350&watch_region=US&sort_by=popularity.desc").results
    }

    suspend fun getAppleShows(): List<TmdbShow> = cachedList("tmdb_v3_apple_shows", TTL_CATALOGS) {
        tmdb.discoverShows("with_networks=2552&sort_by=popularity.desc").results
    }

    suspend fun getHboMovies(): List<TmdbMovie> = cachedList("tmdb_v3_hbo_movies", TTL_CATALOGS) {
        tmdb.discoverMovies("with_watch_providers=34&watch_region=US&sort_by=popularity.desc").results
    }

    suspend fun getHboShows(): List<TmdbShow> = cachedList("tmdb_v3_hbo_shows", TTL_CATALOGS) {
        tmdb.discoverShows("with_networks=49&sort_by=popularity.desc").results
    }

    // ── Streaming Platforms (Movies) ──────────────────────────
    suspend fun getPrimeMovies() = cachedList("tmdb_v3_prime_movies", TTL_CATALOGS) {
        tmdb.getPrimeMovies().results
    }
    suspend fun getDisneyMovies() = cachedList("tmdb_v3_disney_movies", TTL_CATALOGS) {
        tmdb.getDisneyMovies().results
    }
    suspend fun getHuluMovies() = cachedList("tmdb_v3_hulu_movies", TTL_CATALOGS) {
        tmdb.getHuluMovies().results
    }
    suspend fun getParamountMovies() = cachedList("tmdb_v3_paramount_movies", TTL_CATALOGS) {
        tmdb.getParamountMovies().results
    }

    // ── Streaming Platforms (Shows) ───────────────────────────
    suspend fun getPrimeShows() = cachedList("tmdb_v3_prime_shows", TTL_CATALOGS) {
        tmdb.getPrimeShows().results
    }
    suspend fun getDisneyShows() = cachedList("tmdb_v3_disney_shows", TTL_CATALOGS) {
        tmdb.getDisneyShows().results
    }
    suspend fun getHuluShows() = cachedList("tmdb_v3_hulu_shows", TTL_CATALOGS) {
        tmdb.getHuluShows().results
    }
    suspend fun getParamountShows() = cachedList("tmdb_v3_paramount_shows", TTL_CATALOGS) {
        tmdb.getParamountShows().results
    }
    suspend fun getPeacockShows() = cachedList("tmdb_v3_peacock_shows", TTL_CATALOGS) {
        tmdb.getPeacockShows().results
    }

    // ── Moods & Discovery (Movies) ────────────────────────────
    suspend fun getNewReleaseMovies() = cachedList("tmdb_v3_new_releases", TTL_CATALOGS / 2) {
        tmdb.getNewReleaseMovies().results
    }
    suspend fun getCriticallyAcclaimedMovies() = cachedList("tmdb_v3_acclaimed_movies", TTL_CATALOGS) {
        tmdb.getCriticallyAcclaimedMovies().results
    }
    suspend fun getBoxOfficeMovies() = cachedList("tmdb_v3_boxoffice_movies", TTL_CATALOGS) {
        tmdb.getBoxOfficeMovies().results
    }
    suspend fun getClassicMovies() = cachedList("tmdb_v3_classic_movies", TTL_GENRES) {
        tmdb.getClassicMovies().results
    }
    suspend fun getKoreanMovies() = cachedList("tmdb_v3_korean_movies", TTL_CATALOGS) {
        tmdb.getKoreanMovies().results
    }
    suspend fun getUpcomingMovies() = cachedList("tmdb_v3_upcoming_movies", TTL_CATALOGS / 2) {
        tmdb.getUpcomingMovies().results
    }

    // ── Moods & Discovery (Shows) ─────────────────────────────
    suspend fun getCriticallyAcclaimedShows() = cachedList("tmdb_v3_acclaimed_shows", TTL_CATALOGS) {
        tmdb.getCriticallyAcclaimedShows().results
    }
    suspend fun getAnimeShows() = cachedList("tmdb_v3_anime_shows", TTL_CATALOGS) {
        tmdb.getAnimeShows().results
    }
    suspend fun getRealityShows() = cachedList("tmdb_v3_reality_shows", TTL_CATALOGS) {
        tmdb.getRealityShows().results
    }
    suspend fun getDocumentaryShows() = cachedList("tmdb_v3_documentary_shows", TTL_CATALOGS) {
        tmdb.getDocumentaryShows().results
    }
    suspend fun getMiniSeries() = cachedList("tmdb_v3_miniseries", TTL_CATALOGS) {
        tmdb.getMiniSeries().results
    }
    suspend fun getKidsShows() = cachedList("tmdb_v3_kids_shows", TTL_CATALOGS) {
        tmdb.getKidsShows().results
    }
    suspend fun getKoreanDramas() = cachedList("tmdb_v3_korean_dramas", TTL_CATALOGS) {
        tmdb.getKoreanDramas().results
    }

    suspend fun getMoviesByGenre(tmdbGenreId: Int): List<TmdbMovie> = cachedList(
        "tmdb_v3_movies_g_$tmdbGenreId", TTL_CATALOGS
    ) { tmdb.discoverMovies("with_genres=$tmdbGenreId&sort_by=popularity.desc").results }

    suspend fun getShowsByGenre(tmdbGenreId: Int): List<TmdbShow> = cachedList(
        "tmdb_v3_shows_g_$tmdbGenreId", TTL_CATALOGS
    ) { tmdb.discoverShows("with_genres=$tmdbGenreId&sort_by=popularity.desc").results }

    suspend fun getMovieDetail(id: Int): TmdbMovie? = cachedObject(
        key = "tmdb_v3_movie_$id",
        ttlMs = TTL_DETAIL,
        coalesceKey = "fetch_movie_$id"
    ) { tmdb.getMovieDetail(id) }

    suspend fun getShowDetail(id: Int): TmdbShow? = cachedObject(
        key = "tmdb_v3_show_$id",
        ttlMs = TTL_DETAIL,
        coalesceKey = "fetch_show_$id"
    ) { tmdb.getShowDetail(id) }

    suspend fun getSeasons(showId: Int, count: Int): List<TmdbSeason> = cachedList(
        key = "tmdb_v3_seasons_$showId",
        ttlMs = TTL_DETAIL,
        coalesceKey = "fetch_seasons_$showId"
    ) {
        coroutineScope {
            (1..count).map { sn ->
                async {
                    tmdb.getShowSeason(showId, sn)
                }
            }.awaitAll().filterNotNull()
        }
    }

    suspend fun searchMovies(query: String): List<TmdbMovie> = cachedList(
        key = "tmdb_v3_search_movie_${query.trim().lowercase()}",
        ttlMs = TTL_SEARCH,
        coalesceKey = "tmdb_v3_search_movie_${query.trim().lowercase()}"
    ) { tmdb.searchMovies(query).results }

    suspend fun searchShows(query: String): List<TmdbShow> = cachedList(
        key = "tmdb_v3_search_show_${query.trim().lowercase()}",
        ttlMs = TTL_SEARCH,
        coalesceKey = "tmdb_v3_search_show_${query.trim().lowercase()}"
    ) { tmdb.searchShows(query).results }

    suspend fun searchAll(query: String): Pair<List<TmdbMovie>, List<TmdbShow>> = coroutineScope {
        val movies = async { tmdb.searchMovies(query).results }
        val shows  = async { tmdb.searchShows(query).results }
        movies.await() to shows.await()
    }

    private suspend fun cachedTorrentio(key: String, fetch: suspend () -> List<TorrentioStream>): List<TorrentioStream> {
        diskCache.get(key, TTL_TORRENTIO)?.let { text ->
            return withContext(Dispatchers.Default) {
                try { json.decodeFromString<List<TorrentioStream>>(text) } catch (e: Exception) { emptyList() }
            }
        }
        return try {
            val result = fetch()
            val text = withContext(Dispatchers.Default) { json.encodeToString(result) }
            diskCache.put(key, text)
            result
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getMovieStreams(imdbId: String): List<TorrentioStream> {
        val config = "torbox=${BuildConfig.TORBOX_API_KEY}"
        return cachedTorrentio("torrentio_movie_$imdbId") {
            coalesced("torrentio_movie_$imdbId") {
                torrentio.getMovieStream(config, imdbId).streams
            }
        }
    }

    suspend fun getEpisodeStreams(imdbId: String, season: Int, episode: Int): List<TorrentioStream> {
        val config = "torbox=${BuildConfig.TORBOX_API_KEY}"
        val key = "torrentio_ep_${imdbId}_s${season}e${episode}"
        return cachedTorrentio(key) {
            coalesced(key) {
                torrentio.getEpisodeStream(config, imdbId, season, episode).streams
            }
        }
    }
}