# TMDB + TorBox Integration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace IPTV VOD and Series sections with TMDB-backed catalog streamed via TorBox, keeping Live TV unchanged.

**Architecture:** TmdbApiService + TorrentioService (OkHttp/Gson, no Retrofit) wrapped by TmdbRepository with its own DiskCache("tmdb_api") instance. MainViewModel gains a `tmdbRepository` constructor param (second position), new `BrowseContent.TmdbMovies / TmdbShows / TmdbShowDetail` sealed subclasses, `loadTmdb()` helper (no credentials needed), and TMDB-specific play actions. HomeScreen gains `TmdbMovieGrid`, `TmdbShowGrid`, `TmdbShowDetailView` composables with overlay-based `PosterCard` focus expansion (no `AnimatedVisibility` inside grid items — prevents reflow).

**Tech Stack:** Kotlin, Jetpack Compose, OkHttp 4.12, Gson 2.10, Coil 2.6, Coroutines, StateFlow. No Retrofit. No new Gradle deps needed.

---

## File Map

| Action | Path |
|---|---|
| Create | `app/src/main/java/com/rizzoplayer/iptv/AppConfig.kt` |
| Create | `app/src/main/java/com/rizzoplayer/iptv/data/model/TmdbModels.kt` |
| Create | `app/src/main/java/com/rizzoplayer/iptv/data/api/TmdbApiService.kt` |
| Create | `app/src/main/java/com/rizzoplayer/iptv/data/api/TorrentioService.kt` |
| Create | `app/src/main/java/com/rizzoplayer/iptv/data/repository/TmdbRepository.kt` |
| Modify | `app/src/main/java/com/rizzoplayer/iptv/data/local/DiskCache.kt` |
| Modify | `app/src/main/java/com/rizzoplayer/iptv/data/model/Models.kt` |
| Modify | `app/src/main/java/com/rizzoplayer/iptv/ui/viewmodel/MainViewModel.kt` |
| Modify | `app/src/main/java/com/rizzoplayer/iptv/ui/viewmodel/ViewModelFactory.kt` |
| Modify | `app/src/main/java/com/rizzoplayer/iptv/MainActivity.kt` |
| Modify | `app/src/main/java/com/rizzoplayer/iptv/ui/screens/HomeScreen.kt` |

---

## Task 1: AppConfig + TmdbModels (foundation data)

**Files:**
- Create: `app/src/main/java/com/rizzoplayer/iptv/AppConfig.kt`
- Create: `app/src/main/java/com/rizzoplayer/iptv/data/model/TmdbModels.kt`

- [ ] **Step 1.1: Create AppConfig.kt**

```kotlin
// app/src/main/java/com/rizzoplayer/iptv/AppConfig.kt
package com.rizzoplayer.iptv

object AppConfig {
    const val TMDB_BEARER        = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJiNDk3ZjZhN2FlZWZhNGYxYWQ3ZmRmZmFhMmQ0NGI1YSIsIm5iZiI6MTcwNTMwNTk1Mi45NjIsInN1YiI6IjY1YTRlNzYwOGEwZTliMDEyYmI0NWMzNiIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ.Tk5zzydMxXC6DwBB3xKgzSPhuulFBrsyVBWbjltsgZE"
    const val TORBOX_API_KEY     = "4bdfd9b6-6052-4e45-b8a5-215cddc76ca3"
    const val TORRENTIO_BASE     = "https://torrentio.strem.fun"
    const val TMDB_IMAGE_BASE    = "https://image.tmdb.org/t/p"
    const val TMDB_POSTER_SIZE   = "w342"
    const val TMDB_BACKDROP_SIZE = "w780"
}
```

- [ ] **Step 1.2: Create TmdbModels.kt**

```kotlin
// app/src/main/java/com/rizzoplayer/iptv/data/model/TmdbModels.kt
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
```

- [ ] **Step 1.3: Verify build**

Run from `/Users/johnrizzetto/RizzoIPTVPlayer`:
```bash
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 1.4: Commit**

```bash
cd /Users/johnrizzetto/RizzoIPTVPlayer
git init 2>/dev/null || true
git add app/src/main/java/com/rizzoplayer/iptv/AppConfig.kt
git add app/src/main/java/com/rizzoplayer/iptv/data/model/TmdbModels.kt
git commit -m "feat: add AppConfig and TMDB/Torrentio data models"
```

---

## Task 2: TmdbApiService (TMDB HTTP client)

**Files:**
- Create: `app/src/main/java/com/rizzoplayer/iptv/data/api/TmdbApiService.kt`

- [ ] **Step 2.1: Create TmdbApiService.kt**

```kotlin
// app/src/main/java/com/rizzoplayer/iptv/data/api/TmdbApiService.kt
package com.rizzoplayer.iptv.data.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rizzoplayer.iptv.AppConfig
import com.rizzoplayer.iptv.data.model.TmdbGenreResponse
import com.rizzoplayer.iptv.data.model.TmdbMovie
import com.rizzoplayer.iptv.data.model.TmdbPage
import com.rizzoplayer.iptv.data.model.TmdbSeason
import com.rizzoplayer.iptv.data.model.TmdbShow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class TmdbApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .dispatcher(Dispatcher().apply {
            maxRequests = 16
            maxRequestsPerHost = 8
        })
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("Authorization", "Bearer ${AppConfig.TMDB_BEARER}")
                    .header("Accept", "application/json")
                    .build()
            )
        }
        .build()

    private val gson = Gson()
    private val base = "https://api.themoviedb.org/3"

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string() ?: "" }
    }

    // ── Genres ────────────────────────────────────────────────────────────

    suspend fun getMovieGenres(): TmdbGenreResponse {
        val json = get("$base/genre/movie/list")
        return try { gson.fromJson(json, TmdbGenreResponse::class.java) } catch (_: Exception) { TmdbGenreResponse() }
    }

    suspend fun getTvGenres(): TmdbGenreResponse {
        val json = get("$base/genre/tv/list")
        return try { gson.fromJson(json, TmdbGenreResponse::class.java) } catch (_: Exception) { TmdbGenreResponse() }
    }

    // ── Catalog lists ─────────────────────────────────────────────────────

    /** [path] = "movie/popular" | "movie/top_rated" | "movie/now_playing" */
    suspend fun getMoviePage(path: String): List<TmdbMovie> {
        val json = get("$base/$path?page=1")
        return try {
            val type = object : TypeToken<TmdbPage<TmdbMovie>>() {}.type
            gson.fromJson<TmdbPage<TmdbMovie>>(json, type)?.results ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    /** [path] = "tv/popular" | "tv/top_rated" | "tv/on_the_air" */
    suspend fun getTvPage(path: String): List<TmdbShow> {
        val json = get("$base/$path?page=1")
        return try {
            val type = object : TypeToken<TmdbPage<TmdbShow>>() {}.type
            gson.fromJson<TmdbPage<TmdbShow>>(json, type)?.results ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    suspend fun discoverMovies(genreId: Int): List<TmdbMovie> {
        val json = get("$base/discover/movie?with_genres=$genreId&sort_by=popularity.desc&page=1")
        return try {
            val type = object : TypeToken<TmdbPage<TmdbMovie>>() {}.type
            gson.fromJson<TmdbPage<TmdbMovie>>(json, type)?.results ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    suspend fun discoverTvShows(genreId: Int): List<TmdbShow> {
        val json = get("$base/discover/tv?with_genres=$genreId&sort_by=popularity.desc&page=1")
        return try {
            val type = object : TypeToken<TmdbPage<TmdbShow>>() {}.type
            gson.fromJson<TmdbPage<TmdbShow>>(json, type)?.results ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    // ── Details (includes imdb_id via external_ids) ───────────────────────

    suspend fun getMovieDetail(id: Int): TmdbMovie? {
        val json = get("$base/movie/$id?append_to_response=external_ids")
        return try { gson.fromJson(json, TmdbMovie::class.java) } catch (_: Exception) { null }
    }

    suspend fun getTvDetail(id: Int): TmdbShow? {
        val json = get("$base/tv/$id?append_to_response=external_ids")
        return try { gson.fromJson(json, TmdbShow::class.java) } catch (_: Exception) { null }
    }

    suspend fun getTvSeason(showId: Int, season: Int): TmdbSeason? {
        val json = get("$base/tv/$showId/season/$season")
        return try { gson.fromJson(json, TmdbSeason::class.java) } catch (_: Exception) { null }
    }

    // ── Search ────────────────────────────────────────────────────────────

    suspend fun searchMovies(query: String): List<TmdbMovie> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val json = get("$base/search/movie?query=$encoded&page=1")
        return try {
            val type = object : TypeToken<TmdbPage<TmdbMovie>>() {}.type
            gson.fromJson<TmdbPage<TmdbMovie>>(json, type)?.results ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    suspend fun searchTvShows(query: String): List<TmdbShow> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val json = get("$base/search/tv?query=$encoded&page=1")
        return try {
            val type = object : TypeToken<TmdbPage<TmdbShow>>() {}.type
            gson.fromJson<TmdbPage<TmdbShow>>(json, type)?.results ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }
}
```

- [ ] **Step 2.2: Verify build**

```bash
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2.3: Commit**

```bash
git add app/src/main/java/com/rizzoplayer/iptv/data/api/TmdbApiService.kt
git commit -m "feat: add TmdbApiService (OkHttp TMDB client)"
```

---

## Task 3: TorrentioService (stream resolution)

**Files:**
- Create: `app/src/main/java/com/rizzoplayer/iptv/data/api/TorrentioService.kt`

**Verified URL format:** `https://torrentio.strem.fun/torbox={KEY}/stream/movie/{imdbId}.json`
Stream object: `{"url": "https://torrentio.strem.fun/resolve/...", "name": "[TB+] Torrentio\n4k", "title": "..."}`

- [ ] **Step 3.1: Create TorrentioService.kt**

```kotlin
// app/src/main/java/com/rizzoplayer/iptv/data/api/TorrentioService.kt
package com.rizzoplayer.iptv.data.api

import com.google.gson.Gson
import com.rizzoplayer.iptv.AppConfig
import com.rizzoplayer.iptv.data.model.TorrentioResponse
import com.rizzoplayer.iptv.data.model.TorrentioStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class TorrentioService {

    // Torrentio can be slow — longer timeouts
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // Config segment: torbox={API_KEY}
    private val config = "torbox=${AppConfig.TORBOX_API_KEY}"

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string() ?: "" }
    }

    /** Returns all TorBox-cached streams for a movie. Empty = not cached on TorBox. */
    suspend fun getMovieStreams(imdbId: String): List<TorrentioStream> {
        val url = "${AppConfig.TORRENTIO_BASE}/$config/stream/movie/$imdbId.json"
        val json = try { get(url) } catch (_: Exception) { return emptyList() }
        return try {
            gson.fromJson(json, TorrentioResponse::class.java)?.streams?.filter { it.url.isNotEmpty() } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    /** Returns all TorBox-cached streams for an episode. Empty = not cached on TorBox. */
    suspend fun getEpisodeStreams(imdbId: String, season: Int, episode: Int): List<TorrentioStream> {
        val url = "${AppConfig.TORRENTIO_BASE}/$config/stream/series/$imdbId:$season:$episode.json"
        val json = try { get(url) } catch (_: Exception) { return emptyList() }
        return try {
            gson.fromJson(json, TorrentioResponse::class.java)?.streams?.filter { it.url.isNotEmpty() } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }
}
```

- [ ] **Step 3.2: Verify build**

```bash
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3.3: Commit**

```bash
git add app/src/main/java/com/rizzoplayer/iptv/data/api/TorrentioService.kt
git commit -m "feat: add TorrentioService (stream resolution via TorBox)"
```

---

## Task 4: DiskCache + TmdbRepository

**Files:**
- Modify: `app/src/main/java/com/rizzoplayer/iptv/data/local/DiskCache.kt`
- Create: `app/src/main/java/com/rizzoplayer/iptv/data/repository/TmdbRepository.kt`

- [ ] **Step 4.1: Add `dirName` param to DiskCache**

The existing `DiskCache` always uses `"iptv_api"` as the directory name. We need `TmdbRepository` to use a separate `"tmdb_api"` directory.

In `DiskCache.kt`, replace:
```kotlin
class DiskCache(context: Context) {

    private val dir = File(context.cacheDir, "iptv_api").also { it.mkdirs() }
```

With:
```kotlin
class DiskCache(context: Context, dirName: String = "iptv_api") {

    private val dir = File(context.cacheDir, dirName).also { it.mkdirs() }
```

This is backwards-compatible — existing callers that pass only `Context` continue working unchanged.

- [ ] **Step 4.2: Create TmdbRepository.kt**

```kotlin
// app/src/main/java/com/rizzoplayer/iptv/data/repository/TmdbRepository.kt
package com.rizzoplayer.iptv.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rizzoplayer.iptv.data.api.TmdbApiService
import com.rizzoplayer.iptv.data.api.TorrentioService
import com.rizzoplayer.iptv.data.local.DiskCache
import com.rizzoplayer.iptv.data.model.TmdbGenre
import com.rizzoplayer.iptv.data.model.TmdbMovie
import com.rizzoplayer.iptv.data.model.TmdbSeason
import com.rizzoplayer.iptv.data.model.TmdbShow
import com.rizzoplayer.iptv.data.model.TorrentioStream
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class TmdbRepository(
    private val tmdb: TmdbApiService,
    private val torrentio: TorrentioService,
    private val diskCache: DiskCache,
    private val gson: Gson = Gson()
) {
    companion object {
        private val TTL_GENRES  = 24 * 60 * 60 * 1000L       // 24 h
        private val TTL_CATALOG =  6 * 60 * 60 * 1000L       //  6 h
        private val TTL_DETAILS =  7 * 24 * 60 * 60 * 1000L  //  7 days
    }

    // ── Cache helpers ─────────────────────────────────────────────────────

    private suspend inline fun <reified T> cachedList(
        key: String,
        ttlMs: Long,
        crossinline fetch: suspend () -> List<T>
    ): List<T> {
        diskCache.get(key, ttlMs)?.let { json ->
            return try {
                val type = object : TypeToken<List<T>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (_: Exception) { emptyList() }
        }
        val result = fetch()
        diskCache.put(key, gson.toJson(result))
        return result
    }

    private suspend inline fun <reified T> cachedObject(
        key: String,
        ttlMs: Long,
        crossinline fetch: suspend () -> T?
    ): T? {
        diskCache.get(key, ttlMs)?.let { json ->
            return try { gson.fromJson(json, T::class.java) } catch (_: Exception) { null }
        }
        val result = fetch() ?: return null
        diskCache.put(key, gson.toJson(result))
        return result
    }

    // ── Genres ────────────────────────────────────────────────────────────

    suspend fun getMovieGenres(): List<TmdbGenre> =
        cachedList("tmdb_movie_genres", TTL_GENRES) { tmdb.getMovieGenres().genres }

    suspend fun getTvGenres(): List<TmdbGenre> =
        cachedList("tmdb_tv_genres", TTL_GENRES) { tmdb.getTvGenres().genres }

    // ── Catalog lists ─────────────────────────────────────────────────────

    /** [listName] = "popular" | "top_rated" | "now_playing" */
    suspend fun getMovieList(listName: String): List<TmdbMovie> =
        cachedList("tmdb_movies_$listName", TTL_CATALOG) { tmdb.getMoviePage("movie/$listName") }

    /** [listName] = "popular" | "top_rated" | "on_the_air" */
    suspend fun getTvList(listName: String): List<TmdbShow> =
        cachedList("tmdb_shows_$listName", TTL_CATALOG) { tmdb.getTvPage("tv/$listName") }

    suspend fun getMoviesByGenre(genreId: Int): List<TmdbMovie> =
        cachedList("tmdb_movies_genre_$genreId", TTL_CATALOG) { tmdb.discoverMovies(genreId) }

    suspend fun getShowsByGenre(genreId: Int): List<TmdbShow> =
        cachedList("tmdb_shows_genre_$genreId", TTL_CATALOG) { tmdb.discoverTvShows(genreId) }

    // ── Details ───────────────────────────────────────────────────────────

    suspend fun getMovieDetail(id: Int): TmdbMovie =
        cachedObject<TmdbMovie>("tmdb_movie_detail_$id", TTL_DETAILS) { tmdb.getMovieDetail(id) }
            ?: throw Exception("Movie $id not found")

    suspend fun getShowDetail(id: Int): TmdbShow =
        cachedObject<TmdbShow>("tmdb_show_detail_$id", TTL_DETAILS) { tmdb.getTvDetail(id) }
            ?: throw Exception("Show $id not found")

    /** Fetches all seasons in parallel; filters out season 0 (Specials) unless it has episodes. */
    suspend fun getSeasons(showId: Int, count: Int): List<TmdbSeason> {
        val cacheKey = "tmdb_show_seasons_$showId"
        diskCache.get(cacheKey, TTL_DETAILS)?.let { json ->
            return try {
                val type = object : TypeToken<List<TmdbSeason>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (_: Exception) { emptyList() }
        }
        val seasons = coroutineScope {
            (1..count).map { n -> async { tmdb.getTvSeason(showId, n) } }.awaitAll()
        }.filterNotNull()
            .filter { it.seasonNumber > 0 || it.episodes.isNotEmpty() }
            .sortedBy { it.seasonNumber }
        diskCache.put(cacheKey, gson.toJson(seasons))
        return seasons
    }

    // ── Search (no cache) ─────────────────────────────────────────────────

    suspend fun searchMovies(query: String): List<TmdbMovie> = tmdb.searchMovies(query)

    suspend fun searchShows(query: String): List<TmdbShow> = tmdb.searchTvShows(query)

    // ── Streams (no cache — URLs are time-limited) ────────────────────────

    /** Returns the best available TorBox stream for a movie, or null if not cached. */
    suspend fun getMovieStream(imdbId: String): TorrentioStream? =
        torrentio.getMovieStreams(imdbId).firstOrNull()

    /** Returns the best available TorBox stream for an episode, or null if not cached. */
    suspend fun getEpisodeStream(imdbId: String, season: Int, episode: Int): TorrentioStream? =
        torrentio.getEpisodeStreams(imdbId, season, episode).firstOrNull()
}
```

- [ ] **Step 4.3: Verify build**

```bash
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4.4: Commit**

```bash
git add app/src/main/java/com/rizzoplayer/iptv/data/local/DiskCache.kt
git add app/src/main/java/com/rizzoplayer/iptv/data/repository/TmdbRepository.kt
git commit -m "feat: add TmdbRepository; add dirName param to DiskCache"
```

---

## Task 5: Models.kt + MainViewModel.kt updates

**Files:**
- Modify: `app/src/main/java/com/rizzoplayer/iptv/data/model/Models.kt`
- Modify: `app/src/main/java/com/rizzoplayer/iptv/ui/viewmodel/MainViewModel.kt`

### 5A — Models.kt: add `contentId` to PlayEvent

- [ ] **Step 5A.1: Add `contentId` field to PlayEvent**

In `Models.kt`, find the `PlayEvent` data class and replace it:

Old:
```kotlin
data class PlayEvent(
    val url: String,
    val title: String,
    val contentType: String = "live",   // "live", "vod", "episode"
    val recentChannels: List<ChannelRef> = emptyList(),
    val favoriteChannels: List<ChannelRef> = emptyList(),
    val resumeMs: Long = 0,
    val nextUrl: String = "",           // next episode URL for auto-advance
    val nextTitle: String = ""          // next episode title
)
```

New:
```kotlin
data class PlayEvent(
    val url: String,
    val title: String,
    val contentType: String = "live",   // "live", "vod", "episode", "tmdb_movie", "tmdb_episode"
    val recentChannels: List<ChannelRef> = emptyList(),
    val favoriteChannels: List<ChannelRef> = emptyList(),
    val resumeMs: Long = 0,
    val nextUrl: String = "",           // next episode URL for auto-advance
    val nextTitle: String = "",         // next episode title
    val contentId: String = ""          // stable ID for position tracking (TMDB ID or "showId:s:e")
)
```

### 5B — MainViewModel.kt: full rewrite

- [ ] **Step 5B.1: Replace MainViewModel.kt with updated version**

The entire file needs to be replaced. New content:

```kotlin
package com.rizzoplayer.iptv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizzoplayer.iptv.AppConfig
import com.rizzoplayer.iptv.data.local.PreferencesStore
import com.rizzoplayer.iptv.data.local.ServersStore
import com.rizzoplayer.iptv.data.model.Category
import com.rizzoplayer.iptv.data.model.Credentials
import com.rizzoplayer.iptv.data.model.Episode
import com.rizzoplayer.iptv.data.model.Favorite
import com.rizzoplayer.iptv.data.model.LiveStream
import com.rizzoplayer.iptv.data.model.PlayEvent
import com.rizzoplayer.iptv.data.model.RecentItem
import com.rizzoplayer.iptv.data.model.Series
import com.rizzoplayer.iptv.data.model.ServerConfig
import com.rizzoplayer.iptv.data.model.TmdbEpisode
import com.rizzoplayer.iptv.data.model.TmdbMovie
import com.rizzoplayer.iptv.data.model.TmdbSeason
import com.rizzoplayer.iptv.data.model.TmdbShow
import com.rizzoplayer.iptv.data.model.VodStream
import com.rizzoplayer.iptv.data.model.ChannelRef
import com.rizzoplayer.iptv.data.repository.IPTVRepository
import com.rizzoplayer.iptv.data.repository.TmdbRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Section { LIVE, VOD, SERIES, FAVORITES }

sealed class BrowseContent {
    object Empty : BrowseContent()
    data class Categories(val items: List<Category>, val mode: Section) : BrowseContent()
    data class LiveStreams(val items: List<LiveStream>) : BrowseContent()
    data class VodStreams(val items: List<VodStream>) : BrowseContent()
    data class SeriesList(val items: List<Series>) : BrowseContent()
    data class Episodes(val seasons: Map<String, List<Episode>>) : BrowseContent()
    data class Favorites(val items: Map<String, Favorite>) : BrowseContent()
    // TMDB content types
    data class TmdbMovies(val items: List<TmdbMovie>, val genreName: String) : BrowseContent()
    data class TmdbShows(val items: List<TmdbShow>, val genreName: String) : BrowseContent()
    data class TmdbShowDetail(val show: TmdbShow, val seasons: List<TmdbSeason>) : BrowseContent()
}

data class EpgInfo(
    val channelName: String = "",
    val nowTitle: String = "",
    val nowStart: String = "",
    val nextTitle: String = "",
    val nextStart: String = ""
)

data class MainUiState(
    val section: Section = Section.LIVE,
    val content: BrowseContent = BrowseContent.Empty,
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val epgInfo: EpgInfo? = null,
    val canGoBack: Boolean = false,
    val credentials: Credentials? = null,
    val nowPlaying: RecentItem? = null,
    val restoreScrollIndex: Int = -1
)

class MainViewModel(
    val repository: IPTVRepository,
    private val tmdbRepository: TmdbRepository,
    private val serversStore: ServersStore,
    private val preferencesStore: PreferencesStore
) : ViewModel() {

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private val _playEvent = MutableSharedFlow<PlayEvent>(extraBufferCapacity = 1)
    val playEvent: SharedFlow<PlayEvent> = _playEvent.asSharedFlow()

    val favorites: StateFlow<Map<String, Favorite>> = repository.favoritesStore.favorites
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val recentlyWatched: StateFlow<List<RecentItem>> = repository.recentlyWatchedStore.items
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val savedServers: StateFlow<List<ServerConfig>> = serversStore.servers
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var backStack: Pair<BrowseContent, Int>? = null
    private val preloaded = mutableSetOf<Section>()

    init {
        viewModelScope.launch {
            repository.credentialsStore.credentials.collect { creds ->
                _state.update { it.copy(credentials = creds) }
            }
        }

        // Debounced TMDB search — fires after 400ms of inactivity, min 2 chars
        viewModelScope.launch {
            androidx.compose.runtime.snapshotFlow { _state.value.searchQuery }
                .debounce(400)
                .distinctUntilChanged()
                .collectLatest { query ->
                    val section = _state.value.section
                    if (query.length < 2 || (section != Section.VOD && section != Section.SERIES)) return@collectLatest
                    loadTmdb {
                        if (section == Section.VOD)
                            BrowseContent.TmdbMovies(tmdbRepository.searchMovies(query), "Search: $query")
                        else
                            BrowseContent.TmdbShows(tmdbRepository.searchShows(query), "Search: $query")
                    }
                }
        }
    }

    // ── Restore last section ───────────────────────────────────────────────

    fun restoreLastSection(): Section {
        return try {
            Section.valueOf(preferencesStore.getLastSection())
        } catch (_: Exception) {
            Section.LIVE
        }
    }

    // ── Server switching ──────────────────────────────────────────────────

    fun switchServer(server: ServerConfig) {
        viewModelScope.launch {
            repository.credentialsStore.save(server.toCredentials())
            repository.clearCache()   // clears IPTV cache only — TMDB cache unaffected
            backStack = null
            preloaded.clear()
            selectSection(Section.LIVE)
        }
    }

    // ── Section navigation ────────────────────────────────────────────────

    fun selectSection(section: Section) {
        backStack = null
        _state.update { it.copy(section = section, canGoBack = false, searchQuery = "", epgInfo = null, restoreScrollIndex = -1) }
        preferencesStore.saveLastSection(section.name)
        when (section) {
            Section.LIVE      -> loadLiveCategories()
            Section.VOD       -> loadVodCategories()
            Section.SERIES    -> loadSeriesCategories()
            Section.FAVORITES -> loadFavorites()
        }
        preloadOtherCategories(section)
    }

    private fun preloadOtherCategories(current: Section) {
        val toPreload = listOf(Section.LIVE, Section.VOD, Section.SERIES).filter {
            it != current && it !in preloaded
        }
        if (toPreload.isEmpty()) return
        toPreload.forEach { preloaded.add(it) }
        val creds = _state.value.credentials
        viewModelScope.launch {
            toPreload.forEach { section ->
                launch {
                    try {
                        when (section) {
                            Section.LIVE   -> if (creds != null) repository.getLiveCategories(creds)
                            Section.VOD    -> tmdbRepository.getMovieGenres()
                            Section.SERIES -> tmdbRepository.getTvGenres()
                            else           -> {}
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun loadLiveCategories() = load {
        BrowseContent.Categories(repository.getLiveCategories(it).sortedByUS(), Section.LIVE)
    }

    private fun loadVodCategories() = loadTmdb {
        val genres = tmdbRepository.getMovieGenres()
        val pseudo = listOf(
            Category("-1", "🔥 Popular"),
            Category("-2", "⭐ Top Rated"),
            Category("-3", "🎬 Now Playing")
        )
        BrowseContent.Categories(pseudo + genres.map { Category(it.id.toString(), it.name) }, Section.VOD)
    }

    private fun loadSeriesCategories() = loadTmdb {
        val genres = tmdbRepository.getTvGenres()
        val pseudo = listOf(
            Category("-1", "🔥 Popular"),
            Category("-2", "⭐ Top Rated"),
            Category("-3", "📺 Airing Today")
        )
        BrowseContent.Categories(pseudo + genres.map { Category(it.id.toString(), it.name) }, Section.SERIES)
    }

    private fun loadFavorites() {
        viewModelScope.launch {
            val favs = repository.favoritesStore.favorites.first()
            _state.update { it.copy(content = BrowseContent.Favorites(favs), isLoading = false) }
        }
    }

    // ── Content selection ─────────────────────────────────────────────────

    fun selectCategory(category: Category, mode: Section, scrollPosition: Int = 0) {
        backStack = _state.value.content to scrollPosition
        when (mode) {
            Section.LIVE -> load(pushBack = true) { creds ->
                BrowseContent.LiveStreams(repository.getLiveStreams(creds, category.id))
            }
            Section.VOD -> {
                val genreId = category.id.toIntOrNull() ?: return
                if (genreId < 0) {
                    val listName = when (genreId) { -1 -> "popular"; -2 -> "top_rated"; else -> "now_playing" }
                    loadTmdb(pushBack = true) {
                        BrowseContent.TmdbMovies(tmdbRepository.getMovieList(listName), category.name)
                    }
                } else {
                    loadTmdb(pushBack = true) {
                        BrowseContent.TmdbMovies(tmdbRepository.getMoviesByGenre(genreId), category.name)
                    }
                }
            }
            Section.SERIES -> {
                val genreId = category.id.toIntOrNull() ?: return
                if (genreId < 0) {
                    val listName = when (genreId) { -1 -> "popular"; -2 -> "top_rated"; else -> "on_the_air" }
                    loadTmdb(pushBack = true) {
                        BrowseContent.TmdbShows(tmdbRepository.getTvList(listName), category.name)
                    }
                } else {
                    loadTmdb(pushBack = true) {
                        BrowseContent.TmdbShows(tmdbRepository.getShowsByGenre(genreId), category.name)
                    }
                }
            }
            else -> {}
        }
    }

    fun selectTmdbShow(show: TmdbShow) {
        loadTmdb(pushBack = true) {
            val detail = tmdbRepository.getShowDetail(show.id)
            val seasons = tmdbRepository.getSeasons(show.id, detail.numberOfSeasons)
            BrowseContent.TmdbShowDetail(detail, seasons)
        }
    }

    // Keep for IPTV episodes (still used if someone has IPTV series in favorites)
    fun selectSeries(series: Series) {
        load(pushBack = false) { creds ->
            val info = repository.getSeriesInfo(creds, series.id)
            BrowseContent.Episodes(info?.episodes ?: emptyMap())
        }
    }

    fun goBack() {
        val (savedContent, scrollPos) = backStack ?: run {
            selectSection(_state.value.section)
            return
        }
        backStack = null
        _state.update { it.copy(content = savedContent, canGoBack = false, searchQuery = "", restoreScrollIndex = scrollPos) }
    }

    fun clearScrollRestore() {
        if (_state.value.restoreScrollIndex >= 0) {
            _state.update { it.copy(restoreScrollIndex = -1) }
        }
    }

    // ── Retry ────────────────────────────────────────────────────────────

    fun retry() {
        _state.update { it.copy(error = null) }
        selectSection(_state.value.section)
    }

    // ── TMDB Play actions ─────────────────────────────────────────────────

    fun onPlayTmdbMovie(movie: TmdbMovie) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val detail = tmdbRepository.getMovieDetail(movie.id)
                val imdbId = detail.imdbId
                if (imdbId == null) {
                    _state.update { it.copy(isLoading = false, error = "Not available on TorBox") }
                    return@launch
                }
                val stream = tmdbRepository.getMovieStream(imdbId)
                if (stream == null) {
                    _state.update { it.copy(isLoading = false, error = "Not available on TorBox") }
                    return@launch
                }
                val posterUrl = detail.posterPath?.let { "${AppConfig.TMDB_IMAGE_BASE}/${AppConfig.TMDB_POSTER_SIZE}$it" }
                val recent = RecentItem(detail.id.toString(), detail.title, "tmdb_movie", icon = posterUrl)
                _state.update { it.copy(isLoading = false) }
                repository.recentlyWatchedStore.add(recent)
                _playEvent.tryEmit(
                    PlayEvent(
                        url         = stream.url,
                        title       = detail.title,
                        contentType = "tmdb_movie",
                        contentId   = detail.id.toString()
                    )
                )
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Error loading stream") }
            }
        }
    }

    fun onPlayTmdbMovieById(id: String) {
        val movieId = id.toIntOrNull() ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val detail = tmdbRepository.getMovieDetail(movieId)
                onPlayTmdbMovie(detail)
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Error") }
            }
        }
    }

    fun onPlayTmdbEpisode(show: TmdbShow, episode: TmdbEpisode) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val imdbId = show.imdbId
                if (imdbId == null) {
                    _state.update { it.copy(isLoading = false, error = "Not available on TorBox") }
                    return@launch
                }
                val stream = tmdbRepository.getEpisodeStream(imdbId, episode.seasonNumber, episode.episodeNumber)
                if (stream == null) {
                    _state.update { it.copy(isLoading = false, error = "Not available on TorBox") }
                    return@launch
                }
                val title = "${show.name} S${episode.seasonNumber}E${episode.episodeNumber} — ${episode.name}"
                val stableId = "${show.id}:${episode.seasonNumber}:${episode.episodeNumber}"
                _state.update { it.copy(isLoading = false) }
                _playEvent.tryEmit(
                    PlayEvent(
                        url         = stream.url,
                        title       = title,
                        contentType = "tmdb_episode",
                        contentId   = stableId
                    )
                )
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Error loading stream") }
            }
        }
    }

    fun onPlayTmdbEpisodeById(id: String) {
        // id format: "showId:season:episode"
        val parts = id.split(":")
        if (parts.size != 3) return
        val showId   = parts[0].toIntOrNull() ?: return
        val season   = parts[1].toIntOrNull() ?: return
        val episodeN = parts[2].toIntOrNull() ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val show = tmdbRepository.getShowDetail(showId)
                val imdbId = show.imdbId
                if (imdbId == null) {
                    _state.update { it.copy(isLoading = false, error = "Not available on TorBox") }
                    return@launch
                }
                val stream = tmdbRepository.getEpisodeStream(imdbId, season, episodeN)
                if (stream == null) {
                    _state.update { it.copy(isLoading = false, error = "Not available on TorBox") }
                    return@launch
                }
                val title = "${show.name} S${season}E${episodeN}"
                _state.update { it.copy(isLoading = false) }
                _playEvent.tryEmit(
                    PlayEvent(
                        url         = stream.url,
                        title       = title,
                        contentType = "tmdb_episode",
                        contentId   = id
                    )
                )
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Error") }
            }
        }
    }

    // ── IPTV Play helpers ────────────────────────────────────────────────

    private suspend fun buildRecentRefs(creds: Credentials, excludeId: String? = null): List<ChannelRef> =
        recentlyWatched.value
            .filter { it.id != excludeId }
            .take(8)
            .mapNotNull { item ->
                val id = item.id.toIntOrNull() ?: return@mapNotNull null
                val url = when (item.type) {
                    "live"    -> repository.getLiveUrl(creds, id)
                    "vod"     -> repository.getVodUrl(creds, id)
                    "episode" -> repository.getEpisodeUrl(creds, id, item.ext ?: "mp4")
                    else      -> return@mapNotNull null
                }
                ChannelRef(item.name, url, item.icon)
            }

    private suspend fun buildFavoriteRefs(creds: Credentials, excludeId: String? = null): List<ChannelRef> =
        favorites.value.values
            .filter { it.id != excludeId }
            .take(8)
            .mapNotNull { fav ->
                val id = fav.id.toIntOrNull() ?: return@mapNotNull null
                val url = when (fav.type) {
                    "live"    -> repository.getLiveUrl(creds, id)
                    "vod"     -> repository.getVodUrl(creds, id)
                    "episode" -> repository.getEpisodeUrl(creds, id, fav.ext ?: "mp4")
                    else      -> return@mapNotNull null
                }
                ChannelRef(fav.name, url, fav.icon)
            }

    // ── IPTV Play actions ─────────────────────────────────────────────────

    fun onPlayLive(stream: LiveStream) {
        val creds = _state.value.credentials ?: return
        val url = repository.getLiveUrl(creds, stream.id)
        val recent = RecentItem(stream.id.toString(), stream.name, "live", stream.icon)
        _state.update { it.copy(nowPlaying = recent) }
        loadEpg(stream.id, stream.name)
        viewModelScope.launch {
            repository.recentlyWatchedStore.add(recent)
            val recentRefs = buildRecentRefs(creds, stream.id.toString())
            val favRefs = buildFavoriteRefs(creds, stream.id.toString())
            _playEvent.tryEmit(PlayEvent(url, stream.name, "live", recentRefs, favRefs))
        }
    }

    fun onPlayVod(stream: VodStream) {
        val creds = _state.value.credentials ?: return
        viewModelScope.launch {
            val url = repository.getVodUrl(creds, stream.id)
            val recent = RecentItem(stream.id.toString(), stream.name, "vod", stream.icon)
            _state.update { it.copy(nowPlaying = recent) }
            repository.recentlyWatchedStore.add(recent)
            val recentRefs = buildRecentRefs(creds, stream.id.toString())
            val favRefs = buildFavoriteRefs(creds, stream.id.toString())
            _playEvent.tryEmit(PlayEvent(url, stream.name, "vod", recentRefs, favRefs))
        }
    }

    fun onPlayEpisode(episode: Episode) {
        val creds = _state.value.credentials ?: return
        val title = episode.title ?: "Episode ${episode.episodeNum}"
        val url = repository.getEpisodeUrl(creds, episode.id, episode.containerExtension)
        val recent = RecentItem(episode.id.toString(), title, "episode", ext = episode.containerExtension)
        _state.update { it.copy(nowPlaying = recent) }

        val episodes = (_state.value.content as? BrowseContent.Episodes)?.seasons
        var nextEp: Episode? = null
        if (episodes != null) {
            for ((_, seasonEps) in episodes.entries.sortedWith(compareBy { it.key.toIntOrNull() ?: Int.MAX_VALUE })) {
                val idx = seasonEps.indexOfFirst { it.id == episode.id }
                if (idx >= 0 && idx < seasonEps.size - 1) {
                    nextEp = seasonEps[idx + 1]
                    break
                }
            }
        }

        viewModelScope.launch {
            repository.recentlyWatchedStore.add(recent)
            val recentRefs = buildRecentRefs(creds, episode.id.toString())
            val favRefs = buildFavoriteRefs(creds, episode.id.toString())
            val nextUrl = nextEp?.let { repository.getEpisodeUrl(creds, it.id, it.containerExtension) } ?: ""
            val nextTitle = nextEp?.let { it.title ?: "Episode ${it.episodeNum}" } ?: ""
            _playEvent.tryEmit(PlayEvent(url, title, "episode", recentRefs, favRefs,
                nextUrl = nextUrl, nextTitle = nextTitle))
        }
    }

    fun onPlayRecent(item: RecentItem) {
        val creds = _state.value.credentials
        viewModelScope.launch {
            when (item.type) {
                "tmdb_movie"   -> onPlayTmdbMovieById(item.id)
                "tmdb_episode" -> onPlayTmdbEpisodeById(item.id)
                else -> {
                    if (creds == null) return@launch
                    val url = when (item.type) {
                        "live"    -> repository.getLiveUrl(creds, item.id.toIntOrNull() ?: return@launch)
                        "vod"     -> repository.getVodUrl(creds, item.id.toIntOrNull() ?: return@launch)
                        "episode" -> repository.getEpisodeUrl(creds, item.id.toIntOrNull() ?: return@launch, item.ext ?: "mp4")
                        else      -> return@launch
                    }
                    _state.update { it.copy(nowPlaying = item) }
                    repository.recentlyWatchedStore.add(item)
                    val recentRefs = buildRecentRefs(creds, item.id)
                    val favRefs = buildFavoriteRefs(creds, item.id)
                    _playEvent.tryEmit(PlayEvent(url, item.name, item.type, recentRefs, favRefs))
                }
            }
        }
    }

    fun onPlayFavorite(fav: Favorite) {
        val creds = _state.value.credentials
        viewModelScope.launch {
            when (fav.type) {
                "tmdb_movie"   -> onPlayTmdbMovieById(fav.id)
                "tmdb_episode" -> onPlayTmdbEpisodeById(fav.id)
                else -> {
                    if (creds == null) return@launch
                    val url = when (fav.type) {
                        "live"    -> repository.getLiveUrl(creds, fav.id.toIntOrNull() ?: return@launch)
                        "vod"     -> repository.getVodUrl(creds, fav.id.toIntOrNull() ?: return@launch)
                        "episode" -> repository.getEpisodeUrl(creds, fav.id.toIntOrNull() ?: return@launch, fav.ext ?: "mp4")
                        else      -> return@launch
                    }
                    val recentRefs = buildRecentRefs(creds, fav.id)
                    val favRefs = buildFavoriteRefs(creds, fav.id)
                    _playEvent.tryEmit(PlayEvent(url, fav.name, fav.type, recentRefs, favRefs))
                }
            }
        }
    }

    // ── EPG ──────────────────────────────────────────────────────────────

    fun loadEpg(streamId: Int, channelName: String) {
        val creds = _state.value.credentials ?: return
        viewModelScope.launch {
            try {
                val epg = repository.getShortEpg(creds, streamId) ?: return@launch
                val listings = epg.listings ?: return@launch
                val now  = listings.getOrNull(0)
                val next = listings.getOrNull(1)
                _state.update {
                    it.copy(
                        epgInfo = EpgInfo(
                            channelName = channelName,
                            nowTitle  = now?.let  { l -> repository.decodeEpgTitle(l.title) } ?: "",
                            nowStart  = now?.start?.drop(11)?.take(5) ?: "",
                            nextTitle = next?.let { l -> repository.decodeEpgTitle(l.title) } ?: "",
                            nextStart = next?.start?.drop(11)?.take(5) ?: ""
                        )
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun clearEpg() = _state.update { it.copy(epgInfo = null) }

    // ── Search ────────────────────────────────────────────────────────────

    fun setSearchQuery(q: String) = _state.update { it.copy(searchQuery = q) }

    // ── Favorites ─────────────────────────────────────────────────────────

    fun toggleFavorite(id: String, name: String, type: String, ext: String? = null, icon: String? = null) {
        viewModelScope.launch {
            val current = repository.favoritesStore.favorites.first()
            if (current.containsKey(id)) {
                repository.favoritesStore.remove(id)
            } else {
                repository.favoritesStore.add(Favorite(id, name, type, ext, icon))
            }
        }
    }

    fun moveFavorite(id: String, direction: Int) {
        viewModelScope.launch { repository.favoritesStore.move(id, direction) }
    }

    // ── Auth ──────────────────────────────────────────────────────────────

    fun logout() {
        viewModelScope.launch {
            repository.credentialsStore.clear()
            repository.clearCache()
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private fun load(pushBack: Boolean = false, block: suspend (Credentials) -> BrowseContent) {
        val creds = _state.value.credentials ?: return
        _state.update { it.copy(isLoading = true, error = null, searchQuery = "") }
        viewModelScope.launch {
            try {
                val content = withContext(Dispatchers.IO) { block(creds) }
                _state.update { it.copy(isLoading = false, content = content, canGoBack = pushBack) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Unknown error") }
            }
        }
    }

    private fun loadTmdb(pushBack: Boolean = false, block: suspend () -> BrowseContent) {
        _state.update { it.copy(isLoading = true, error = null, searchQuery = "") }
        viewModelScope.launch {
            try {
                val content = withContext(Dispatchers.IO) { block() }
                _state.update { it.copy(isLoading = false, content = content, canGoBack = pushBack) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Unknown error") }
            }
        }
    }

    private fun List<Category>.sortedByUS() = sortedWith { a, b ->
        val aUS = a.name.contains("US|") || a.name.startsWith("US") || a.name.contains("|US")
        val bUS = b.name.contains("US|") || b.name.startsWith("US") || b.name.contains("|US")
        when {
            aUS && !bUS  -> -1
            !aUS && bUS  ->  1
            else         -> a.name.compareTo(b.name, ignoreCase = true)
        }
    }
}
```

- [ ] **Step 5.3: Verify build**

```bash
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5.4: Commit**

```bash
git add app/src/main/java/com/rizzoplayer/iptv/data/model/Models.kt
git add app/src/main/java/com/rizzoplayer/iptv/ui/viewmodel/MainViewModel.kt
git commit -m "feat: update MainViewModel and PlayEvent for TMDB integration"
```

---

## Task 6: ViewModelFactory + MainActivity (dependency injection)

**Files:**
- Modify: `app/src/main/java/com/rizzoplayer/iptv/ui/viewmodel/ViewModelFactory.kt`
- Modify: `app/src/main/java/com/rizzoplayer/iptv/MainActivity.kt`

- [ ] **Step 6.1: Replace ViewModelFactory.kt**

```kotlin
// app/src/main/java/com/rizzoplayer/iptv/ui/viewmodel/ViewModelFactory.kt
package com.rizzoplayer.iptv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rizzoplayer.iptv.data.local.PreferencesStore
import com.rizzoplayer.iptv.data.local.ServersStore
import com.rizzoplayer.iptv.data.repository.IPTVRepository
import com.rizzoplayer.iptv.data.repository.TmdbRepository

class ViewModelFactory(
    private val repository: IPTVRepository,
    private val tmdbRepository: TmdbRepository,
    private val serversStore: ServersStore,
    private val preferencesStore: PreferencesStore
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(LoginViewModel::class.java) ->
            LoginViewModel(repository, serversStore) as T
        modelClass.isAssignableFrom(MainViewModel::class.java) ->
            MainViewModel(repository, tmdbRepository, serversStore, preferencesStore) as T
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
```

- [ ] **Step 6.2: Replace MainActivity.kt**

```kotlin
// app/src/main/java/com/rizzoplayer/iptv/MainActivity.kt
package com.rizzoplayer.iptv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.rizzoplayer.iptv.data.api.TmdbApiService
import com.rizzoplayer.iptv.data.api.TorrentioService
import com.rizzoplayer.iptv.data.local.CredentialsStore
import com.rizzoplayer.iptv.data.local.DiskCache
import com.rizzoplayer.iptv.data.local.FavoritesStore
import com.rizzoplayer.iptv.data.local.RecentlyWatchedStore
import com.rizzoplayer.iptv.data.local.ServersStore
import com.rizzoplayer.iptv.data.repository.IPTVRepository
import com.rizzoplayer.iptv.data.repository.TmdbRepository
import com.rizzoplayer.iptv.ui.player.PlayerActivity
import com.rizzoplayer.iptv.ui.screens.HomeScreen
import com.rizzoplayer.iptv.ui.screens.LoginScreen
import com.rizzoplayer.iptv.ui.theme.RizzoIPTVTheme
import com.rizzoplayer.iptv.ui.viewmodel.BrowseContent
import com.rizzoplayer.iptv.ui.viewmodel.LoginViewModel
import com.rizzoplayer.iptv.ui.viewmodel.MainViewModel
import com.rizzoplayer.iptv.ui.viewmodel.ViewModelFactory
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as RizzoApp

        // IPTV repository — uses its own "iptv_api" DiskCache
        val repository = IPTVRepository(
            credentialsStore     = CredentialsStore(applicationContext),
            favoritesStore       = FavoritesStore(applicationContext),
            recentlyWatchedStore = RecentlyWatchedStore(applicationContext),
            diskCache            = DiskCache(applicationContext)  // default dirName = "iptv_api"
        )

        // TMDB repository — uses its own separate "tmdb_api" DiskCache
        val tmdbRepository = TmdbRepository(
            tmdb      = TmdbApiService(),
            torrentio = TorrentioService(),
            diskCache = DiskCache(applicationContext, "tmdb_api")
        )

        val serversStore = ServersStore(applicationContext)
        val factory = ViewModelFactory(repository, tmdbRepository, serversStore, app.preferencesStore)

        setContent {
            RizzoIPTVTheme {
                var isLoggedIn by remember { mutableStateOf<Boolean?>(null) }

                LaunchedEffect(Unit) {
                    isLoggedIn = repository.credentialsStore.credentials.first() != null
                }

                if (isLoggedIn == null) return@RizzoIPTVTheme

                if (isLoggedIn == false) {
                    val loginVm: LoginViewModel = viewModel(factory = factory)
                    LoginScreen(
                        viewModel = loginVm,
                        onLoginSuccess = { isLoggedIn = true }
                    )
                } else {
                    val mainVm: MainViewModel = viewModel(factory = factory)

                    LaunchedEffect(Unit) {
                        if (mainVm.state.value.content is BrowseContent.Empty) {
                            mainVm.selectSection(mainVm.restoreLastSection())
                        }
                    }

                    val credentials by mainVm.repository.credentialsStore.credentials.collectAsState(null)
                    var credentialsSeen by remember { mutableStateOf(false) }
                    LaunchedEffect(credentials) {
                        if (credentials != null) credentialsSeen = true
                        if (credentialsSeen && credentials == null) isLoggedIn = false
                    }

                    LaunchedEffect(Unit) {
                        mainVm.playEvent.collect { event ->
                            val positionStore = app.playbackPositionStore
                            // For TMDB content, use stable contentId directly.
                            // For IPTV content, extract from URL as before.
                            val contentId = if (event.contentType.startsWith("tmdb_")) {
                                event.contentId
                            } else {
                                event.url.substringAfterLast("/").substringBefore(".")
                            }
                            val posKey = "${event.contentType}:$contentId"
                            val resumeMs = positionStore.getPosition(posKey)
                            val recentJson = if (event.recentChannels.isNotEmpty())
                                gson.toJson(event.recentChannels) else ""
                            val favJson = if (event.favoriteChannels.isNotEmpty())
                                gson.toJson(event.favoriteChannels) else ""
                            startActivity(
                                Intent(this@MainActivity, PlayerActivity::class.java).apply {
                                    putExtra(PlayerActivity.EXTRA_URL, event.url)
                                    putExtra(PlayerActivity.EXTRA_TITLE, event.title)
                                    putExtra(PlayerActivity.EXTRA_CONTENT_TYPE, event.contentType)
                                    putExtra(PlayerActivity.EXTRA_CONTENT_ID, contentId)
                                    putExtra(PlayerActivity.EXTRA_RESUME_MS, resumeMs)
                                    putExtra(PlayerActivity.EXTRA_RECENT_CHANNELS, recentJson)
                                    putExtra(PlayerActivity.EXTRA_FAVORITE_CHANNELS, favJson)
                                    putExtra(PlayerActivity.EXTRA_NEXT_URL, event.nextUrl)
                                    putExtra(PlayerActivity.EXTRA_NEXT_TITLE, event.nextTitle)
                                }
                            )
                        }
                    }

                    HomeScreen(viewModel = mainVm)
                }
            }
        }
    }
}
```

- [ ] **Step 6.3: Verify build**

```bash
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6.4: Commit**

```bash
git add app/src/main/java/com/rizzoplayer/iptv/ui/viewmodel/ViewModelFactory.kt
git add app/src/main/java/com/rizzoplayer/iptv/MainActivity.kt
git commit -m "feat: wire TmdbRepository into ViewModelFactory and MainActivity"
```

---

## Task 7: HomeScreen TMDB UI composables

**Files:**
- Modify: `app/src/main/java/com/rizzoplayer/iptv/ui/screens/HomeScreen.kt`

This task adds TMDB composables to HomeScreen.kt and updates the `ContentArea` when-block.

- [ ] **Step 7.1: Add imports to HomeScreen.kt**

At the top of HomeScreen.kt, add these imports after the existing import block:

```kotlin
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.rizzoplayer.iptv.AppConfig
import com.rizzoplayer.iptv.data.model.TmdbEpisode
import com.rizzoplayer.iptv.data.model.TmdbMovie
import com.rizzoplayer.iptv.data.model.TmdbSeason
import com.rizzoplayer.iptv.data.model.TmdbShow
```

Note: `gridItems` and `gridItemsIndexed` are import aliases — import them exactly as shown.

- [ ] **Step 7.2: Add TMDB branches to ContentArea when-block**

In the `ContentArea` function, the `when (content)` block currently ends with the `Favorites` branch and a closing `}`. Add three new branches **before** the closing `}`:

Old (last three lines of the when block):
```kotlin
        is BrowseContent.Favorites -> {
            FavoritesView(
                favorites = favorites,
                onPlay = viewModel::onPlayFavorite,
                onRemove = { fav -> viewModel.toggleFavorite(fav.id, fav.name, fav.type, fav.ext) },
                onMove = { fav, dir -> viewModel.moveFavorite(fav.id, dir) }
            )
        }
    }
}
```

New:
```kotlin
        is BrowseContent.Favorites -> {
            FavoritesView(
                favorites = favorites,
                onPlay = viewModel::onPlayFavorite,
                onRemove = { fav -> viewModel.toggleFavorite(fav.id, fav.name, fav.type, fav.ext) },
                onMove = { fav, dir -> viewModel.moveFavorite(fav.id, dir) }
            )
        }

        is BrowseContent.TmdbMovies -> {
            TmdbMovieGrid(
                content = content,
                favorites = favorites,
                onPlay = viewModel::onPlayTmdbMovie,
                onToggleFavorite = { movie ->
                    viewModel.toggleFavorite(
                        id   = movie.id.toString(),
                        name = movie.title,
                        type = "tmdb_movie",
                        icon = movie.posterPath?.let { "${AppConfig.TMDB_IMAGE_BASE}/${AppConfig.TMDB_POSTER_SIZE}$it" }
                    )
                }
            )
        }

        is BrowseContent.TmdbShows -> {
            TmdbShowGrid(
                content = content,
                favorites = favorites,
                onSelectShow = viewModel::selectTmdbShow,
                onToggleFavorite = { show ->
                    viewModel.toggleFavorite(
                        id   = show.id.toString(),
                        name = show.name,
                        type = "tmdb_show",
                        icon = show.posterPath?.let { "${AppConfig.TMDB_IMAGE_BASE}/${AppConfig.TMDB_POSTER_SIZE}$it" }
                    )
                }
            )
        }

        is BrowseContent.TmdbShowDetail -> {
            TmdbShowDetailView(
                content = content,
                onPlayEpisode = { episode -> viewModel.onPlayTmdbEpisode(content.show, episode) }
            )
        }
    }
}
```

- [ ] **Step 7.3: Add TmdbPosterCard composable**

Add this composable **at the end of HomeScreen.kt** (before the last closing brace of the file, or as a top-level private function):

```kotlin
// ═══════════════════════════════════════════════════════════════════════════
// TMDB POSTER CARD
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TmdbPosterCard(
    title: String,
    posterPath: String?,
    rating: Float,
    year: String,
    overview: String,
    isFocused: Boolean,
    isFavorite: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Base card — fixed 180dp width, never changes size (prevents grid reflow)
        Card(
            modifier = Modifier
                .width(180.dp)
                .onFocusChanged { onFocusChanged(it.isFocused) }
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .then(
                    if (isFocused)
                        Modifier.border(2.dp, AccentBlue, RoundedCornerShape(8.dp))
                    else Modifier
                ),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(if (isFocused) 8.dp else 2.dp)
        ) {
            Box {
                // Poster image — 2:3 aspect ratio
                AsyncImage(
                    model = posterPath?.let { "${AppConfig.TMDB_IMAGE_BASE}/${AppConfig.TMDB_POSTER_SIZE}$it" },
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
                    contentScale = ContentScale.Crop
                )
                // Rating badge — always visible at top-right
                if (rating > 0f) {
                    Text(
                        text = "⭐ ${"%.1f".format(rating)}",
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
                // Favorite heart — top-left when favorited
                if (isFavorite) {
                    Text(
                        text = "♥",
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(2.dp),
                        color = Color(0xFFFF4444),
                        fontSize = 12.sp
                    )
                }
            }
            // Title below poster
            Text(
                text = title,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                fontSize = 11.sp
            )
        }

        // Focus info overlay — floats ABOVE the card with zIndex(1f)
        // Positioned to expand upward so it doesn't push neighbors
        // Uses Box sibling so grid items never reflow
        if (isFocused) {
            Card(
                modifier = Modifier
                    .width(220.dp)
                    .align(Alignment.TopStart)
                    .zIndex(10f)
                    .offset(y = (-8).dp),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (year.isNotEmpty() || rating > 0f) {
                        Row(
                            modifier = Modifier.padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (year.isNotEmpty()) {
                                Text(year, style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                            }
                            if (rating > 0f) {
                                Text("⭐ ${"%.1f".format(rating)}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFD700))
                            }
                        }
                    }
                    if (overview.isNotEmpty()) {
                        Text(
                            text = overview,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.LightGray,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 7.4: Add TmdbMovieGrid composable**

Add after TmdbPosterCard:

```kotlin
// ═══════════════════════════════════════════════════════════════════════════
// TMDB MOVIE GRID
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun TmdbMovieGrid(
    content: BrowseContent.TmdbMovies,
    favorites: Map<String, Favorite>,
    onPlay: (TmdbMovie) -> Unit,
    onToggleFavorite: (TmdbMovie) -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(content.items) {
        try { firstFocus.requestFocus() } catch (_: Exception) {}
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(180.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        gridItems(content.items, key = { it.id }) { movie ->
            val isFirst = content.items.firstOrNull()?.id == movie.id
            var focused by remember { mutableStateOf(false) }
            TmdbPosterCard(
                title         = movie.title,
                posterPath    = movie.posterPath,
                rating        = movie.rating,
                year          = movie.releaseDate.take(4),
                overview      = movie.overview,
                isFocused     = focused,
                isFavorite    = favorites.containsKey(movie.id.toString()),
                onFocusChanged = { focused = it },
                onClick       = { onPlay(movie) },
                onLongClick   = { onToggleFavorite(movie) },
                modifier      = if (isFirst) Modifier.focusRequester(firstFocus) else Modifier
            )
        }
    }
}
```

- [ ] **Step 7.5: Add TmdbShowGrid composable**

Add after TmdbMovieGrid:

```kotlin
// ═══════════════════════════════════════════════════════════════════════════
// TMDB SHOW GRID
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun TmdbShowGrid(
    content: BrowseContent.TmdbShows,
    favorites: Map<String, Favorite>,
    onSelectShow: (TmdbShow) -> Unit,
    onToggleFavorite: (TmdbShow) -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(content.items) {
        try { firstFocus.requestFocus() } catch (_: Exception) {}
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(180.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        gridItems(content.items, key = { it.id }) { show ->
            val isFirst = content.items.firstOrNull()?.id == show.id
            var focused by remember { mutableStateOf(false) }
            TmdbPosterCard(
                title          = show.name,
                posterPath     = show.posterPath,
                rating         = show.rating,
                year           = show.firstAirDate.take(4),
                overview       = show.overview,
                isFocused      = focused,
                isFavorite     = favorites.containsKey(show.id.toString()),
                onFocusChanged  = { focused = it },
                onClick        = { onSelectShow(show) },
                onLongClick    = { onToggleFavorite(show) },
                modifier       = if (isFirst) Modifier.focusRequester(firstFocus) else Modifier
            )
        }
    }
}
```

- [ ] **Step 7.6: Add TmdbShowDetailView composable**

Add after TmdbShowGrid:

```kotlin
// ═══════════════════════════════════════════════════════════════════════════
// TMDB SHOW DETAIL VIEW
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TmdbShowDetailView(
    content: BrowseContent.TmdbShowDetail,
    onPlayEpisode: (TmdbEpisode) -> Unit
) {
    val show = content.show
    val seasons = content.seasons.filter { it.seasonNumber > 0 || it.episodes.isNotEmpty() }
        .sortedBy { it.seasonNumber }

    val firstEpisodeFocus = remember { FocusRequester() }
    val firstSeason = seasons.firstOrNull()

    LaunchedEffect(content) {
        try { firstEpisodeFocus.requestFocus() } catch (_: Exception) {}
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {

        // ── Show banner ───────────────────────────────────────────────────
        item(key = "banner") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                // Backdrop image
                AsyncImage(
                    model = show.backdropPath?.let { "${AppConfig.TMDB_IMAGE_BASE}/${AppConfig.TMDB_BACKDROP_SIZE}$it" },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Dark gradient scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                )
                // Show info overlaid bottom-left
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = show.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        if (show.rating > 0f) {
                            Text(
                                "⭐ ${"%.1f".format(show.rating)}",
                                color = Color(0xFFFFD700),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (show.firstAirDate.isNotEmpty()) {
                            Text(
                                show.firstAirDate.take(4),
                                color = Color.LightGray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (show.numberOfSeasons > 0) {
                            Text(
                                "${show.numberOfSeasons} season${if (show.numberOfSeasons > 1) "s" else ""}",
                                color = Color.LightGray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (show.overview.isNotEmpty()) {
                        Text(
                            text = show.overview,
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // ── Seasons & Episodes ────────────────────────────────────────────
        seasons.forEach { season ->
            stickyHeader(key = "season_${season.seasonNumber}") {
                Text(
                    text = season.name.ifEmpty { "Season ${season.seasonNumber}" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MainBg)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            season.episodes.forEachIndexed { idx, episode ->
                val isVeryFirst = season.seasonNumber == (firstSeason?.seasonNumber ?: -1) && idx == 0
                item(key = "ep_${episode.id}") {
                    var focused by remember { mutableStateOf(false) }
                    val focusMod = if (isVeryFirst) Modifier.focusRequester(firstEpisodeFocus) else Modifier
                    Row(
                        modifier = focusMod
                            .fillMaxWidth()
                            .onFocusChanged { focused = it.isFocused }
                            .focusable()
                            .clickable { onPlayEpisode(episode) }
                            .background(
                                if (focused) AccentBlue.copy(alpha = 0.2f) else Color.Transparent
                            )
                            .then(
                                if (focused)
                                    Modifier.border(1.dp, AccentBlue.copy(alpha = 0.5f))
                                else Modifier
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Episode number
                        Text(
                            text = "${episode.episodeNumber}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (focused) AccentBlue else TextMuted,
                            modifier = Modifier.width(36.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = episode.name.ifEmpty { "Episode ${episode.episodeNumber}" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (focused) Color.White else TextPrimary,
                                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (episode.overview.isNotEmpty()) {
                                Text(
                                    text = episode.overview,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                        // Runtime
                        episode.runtime?.let { rt ->
                            Text(
                                text = "${rt}m",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        // Play indicator on focus
                        if (focused) {
                            Text(
                                text = "▶",
                                color = AccentBlue,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 7.7: Add `MaterialTheme` import if missing**

Check that HomeScreen.kt has `import androidx.compose.material3.MaterialTheme` in its imports. If not, add it.

- [ ] **Step 7.8: Verify build**

```bash
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`. If you see import errors for `gridItems`, verify you have:
```kotlin
import androidx.compose.foundation.lazy.grid.items as gridItems
```

- [ ] **Step 7.9: Commit**

```bash
git add app/src/main/java/com/rizzoplayer/iptv/ui/screens/HomeScreen.kt
git commit -m "feat: add TMDB UI composables (TmdbMovieGrid, TmdbShowGrid, TmdbShowDetailView)"
```

---

## Task 8: Final integration build + ADB install

- [ ] **Step 8.1: Clean build**

```bash
cd /Users/johnrizzetto/RizzoIPTVPlayer
./gradlew clean assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8.2: Verify APK exists**

```bash
ls -lh app/build/outputs/apk/debug/app-debug.apk
```
Expected: File exists, size should be 5–15 MB.

- [ ] **Step 8.3: Connect to Android TV via ADB**

The TV (XGIMI Horizon 20 Max, IP 192.168.50.82) uses ADB wireless. On Android 11+, the connect port is different from the pairing port. If the TV is already connected, skip pairing:

```bash
adb devices
```

If the TV is not listed, re-pair via the TV's Developer Options → Wireless Debugging → Pair device with pairing code. Note the pairing port and code, then:
```bash
# Pair (use port and code from TV screen):
adb pair 192.168.50.82:<PAIRING_PORT> <CODE>

# Discover connect port:
adb mdns services
# Find the adb-XXXXXXXX-XXXX entry and note the port number

# Connect using the connect port:
adb connect 192.168.50.82:<CONNECT_PORT>
```

- [ ] **Step 8.4: Install APK**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Expected: `Success`

- [ ] **Step 8.5: Launch and verify**

```bash
adb shell am start -n com.rizzoplayer.iptv/.MainActivity
```

Manual verification checklist:
- [ ] App launches without crash
- [ ] Live TV section: categories load normally from IPTV
- [ ] VOD section: shows "🔥 Popular", "⭐ Top Rated", "🎬 Now Playing" + genre list from TMDB
- [ ] Tap "🔥 Popular" → grid of movie posters loads
- [ ] Focus on a movie → overlay card appears with title/year/rating/overview (no grid reflow)
- [ ] Tap a movie → loading spinner → movie streams via TorBox in ExoPlayer
- [ ] If movie not on TorBox → error message "Not available on TorBox" stays on browse screen
- [ ] Series section: shows TV genres from TMDB
- [ ] Tap a show → show detail view with backdrop banner + season/episode list
- [ ] Tap an episode → streams via TorBox
- [ ] Search in VOD/Series section: results appear after ~400ms typing pause

- [ ] **Step 8.6: Final commit**

```bash
git add -A
git commit -m "feat: TMDB + TorBox integration complete — Movies/TV from TMDB via TorBox"
```
