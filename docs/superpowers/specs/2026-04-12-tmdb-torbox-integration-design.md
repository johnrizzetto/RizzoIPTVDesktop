# TMDB + TorBox Integration — Design Spec
**Date:** 2026-04-12
**Status:** Approved (rev 2 — post spec review)

## Summary

Replace the existing IPTV VOD (Movies) and Series (TV Shows) sections with TMDB-backed content browsed via TMDB API and streamed via Torrentio/TorBox. Live TV and Favorites are unchanged. The experience is: browse rich TMDB catalog → click to play → auto-selected best stream delivered through TorBox → ExoPlayer.

---

## Architecture

### New files

| File | Purpose |
|---|---|
| `app/src/main/java/com/rizzoplayer/iptv/AppConfig.kt` | API key constants (TMDB, TorBox, Torrentio base URL) |
| `data/api/TmdbApiService.kt` | TMDB REST calls (genres, discover, details, search, seasons) |
| `data/api/TorrentioService.kt` | Torrentio stream resolution with TorBox key embedded |
| `data/model/TmdbModels.kt` | All TMDB + Torrentio data classes with `@SerializedName` |
| `data/repository/TmdbRepository.kt` | Wraps TmdbApiService + TorrentioService; owns a separate DiskCache instance |

### Modified files

| File | Change |
|---|---|
| `ui/viewmodel/MainViewModel.kt` | Add `BrowseContent.TmdbMovies`, `BrowseContent.TmdbShows`, `BrowseContent.TmdbShowDetail` subclasses (sealed class lives here, not in Models.kt). Add `tmdbRepository` constructor param. Replace VOD/Series load functions. Add `loadTmdb()` overload. Fix `preloaded` to `Set<Section>`. Add search debounce via `collectLatest`. |
| `ui/screens/HomeScreen.kt` | Add `TmdbMovieGrid`, `TmdbShowGrid`, `TmdbShowDetailView` composables; enhance `PosterCard` with overlay-based focus expansion (not `AnimatedVisibility` inside grid items). |
| `MainActivity.kt` | Inject `TmdbRepository` into `ViewModelFactory`. Update `contentId` derivation to use stable TMDB ID for `"tmdb_movie"` / `"tmdb_episode"` content types. |
| `ui/viewmodel/ViewModelFactory.kt` | Accept `TmdbRepository` as fourth parameter. |

### Unchanged

- `Section.LIVE`, `Section.FAVORITES` — no changes
- `PlayerActivity` — receives same `EXTRA_URL` / `EXTRA_TITLE` as before; `isVod = contentType != "live"` correctly treats `"tmdb_movie"` and `"tmdb_episode"` as VOD
- `FavoritesStore`, `RecentlyWatchedStore`, `PlaybackPositionStore`
- `BrowseContent.Categories` — reused for TMDB genre lists
- All existing IPTV API code — untouched (live TV still uses it)

---

## Data Models

All TMDB JSON fields are snake_case — every field requires `@SerializedName`.

```kotlin
// TmdbModels.kt
import com.google.gson.annotations.SerializedName

data class TmdbGenre(
    @SerializedName("id")   val id: Int,
    @SerializedName("name") val name: String
)

data class TmdbMovie(
    @SerializedName("id")             val id: Int,
    @SerializedName("imdb_id")        val imdbId: String? = null,   // only present in detail response
    @SerializedName("title")          val title: String,
    @SerializedName("poster_path")    val posterPath: String? = null,
    @SerializedName("backdrop_path")  val backdropPath: String? = null,
    @SerializedName("overview")       val overview: String = "",
    @SerializedName("release_date")   val releaseDate: String = "",
    @SerializedName("vote_average")   val rating: Float = 0f,
    @SerializedName("vote_count")     val voteCount: Int = 0,
    @SerializedName("runtime")        val runtime: Int? = null,     // only in detail response
    @SerializedName("genre_ids")      val genreIds: List<Int> = emptyList()
)

data class TmdbShow(
    @SerializedName("id")               val id: Int,
    @SerializedName("imdb_id")          val imdbId: String? = null,
    @SerializedName("name")             val name: String,
    @SerializedName("poster_path")      val posterPath: String? = null,
    @SerializedName("backdrop_path")    val backdropPath: String? = null,
    @SerializedName("overview")         val overview: String = "",
    @SerializedName("first_air_date")   val firstAirDate: String = "",
    @SerializedName("vote_average")     val rating: Float = 0f,
    @SerializedName("vote_count")       val voteCount: Int = 0,
    @SerializedName("number_of_seasons") val numberOfSeasons: Int = 0,
    @SerializedName("genre_ids")        val genreIds: List<Int> = emptyList()
)

data class TmdbSeason(
    @SerializedName("season_number")  val seasonNumber: Int,
    @SerializedName("name")          val name: String = "",
    @SerializedName("episode_count") val episodeCount: Int = 0,
    @SerializedName("episodes")      val episodes: List<TmdbEpisode> = emptyList()
)

data class TmdbEpisode(
    @SerializedName("id")             val id: Int,
    @SerializedName("episode_number") val episodeNumber: Int,
    @SerializedName("season_number")  val seasonNumber: Int,
    @SerializedName("name")          val name: String = "",
    @SerializedName("overview")      val overview: String = "",
    @SerializedName("still_path")    val stillPath: String? = null,
    @SerializedName("runtime")       val runtime: Int? = null
)

data class TmdbPage<T>(
    @SerializedName("results")      val results: List<T>,
    @SerializedName("page")         val page: Int,
    @SerializedName("total_pages")  val totalPages: Int
)

// External IDs wrapper returned by append_to_response=external_ids
data class TmdbExternalIds(
    @SerializedName("imdb_id") val imdbId: String? = null
)

data class TorrentioStream(
    @SerializedName("url")   val url: String,
    @SerializedName("title") val title: String = "",  // "1080p | BluRay | x265 | 8.5 GB"
    @SerializedName("name")  val name: String = ""    // "TorBox"
)

data class TorrentioResponse(
    @SerializedName("streams") val streams: List<TorrentioStream> = emptyList()
)
```

---

## BrowseContent additions

`BrowseContent` is a sealed class declared in `MainViewModel.kt` — new subclasses must be added there, not in `Models.kt`.

```kotlin
// Added inside MainViewModel.kt alongside existing BrowseContent subclasses:
data class TmdbMovies(val items: List<TmdbMovie>, val genreName: String) : BrowseContent()
data class TmdbShows(val items: List<TmdbShow>, val genreName: String) : BrowseContent()
data class TmdbShowDetail(val show: TmdbShow, val seasons: List<TmdbSeason>) : BrowseContent()
```

`BrowseContent.Categories` is reused for TMDB genre lists (same `Category` model: `id` = genre ID as string, `name` = genre name). Pseudo-category IDs use negative strings `"-1"`, `"-2"`, `"-3"` — safe since TMDB genre IDs are always positive. `BrowseContent.Episodes` is NOT reused — TMDB uses `TmdbShowDetail` to preserve richer metadata.

---

## API Services

### TmdbApiService

Base URL: `https://api.themoviedb.org/3`
Auth: `Authorization: Bearer {TMDB_BEARER}` header on every request.
Image base: `https://image.tmdb.org/t/p/w342{posterPath}` (posters), `w780` (backdrops).

Endpoints:

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/genre/movie/list` | Movie genre list |
| GET | `/genre/tv/list` | TV genre list |
| GET | `/movie/popular` | Popular movies |
| GET | `/movie/top_rated` | Top rated movies |
| GET | `/movie/now_playing` | Now playing |
| GET | `/tv/popular` | Popular shows |
| GET | `/tv/top_rated` | Top rated shows |
| GET | `/tv/on_the_air` | Currently airing |
| GET | `/discover/movie?with_genres={id}&sort_by=popularity.desc` | Movies by genre |
| GET | `/discover/tv?with_genres={id}&sort_by=popularity.desc` | Shows by genre |
| GET | `/movie/{id}?append_to_response=external_ids` | Movie detail + IMDB ID |
| GET | `/tv/{id}?append_to_response=external_ids` | Show detail + IMDB ID |
| GET | `/tv/{id}/season/{n}` | Season + episode list |
| GET | `/search/movie?query={q}` | Search movies |
| GET | `/search/tv?query={q}` | Search shows |

### TorrentioService

Base URL: `https://torrentio.strem.fun`

Stream resolution:
- Movie: `GET /{config}/stream/movie/{imdbId}.json`
- Episode: `GET /{config}/stream/series/{imdbId}:{season}:{episode}.json`

`{config}` encodes the TorBox provider and API key. **Exact path format must be verified against a live Torrentio instance before implementing this service** (may be `/providers=torbox|debridApiKey=KEY` or URL-encoded JSON). This is the first thing to resolve at implementation time.

Returns `TorrentioResponse`. Take `streams[0]` (Torrentio pre-ranks by quality + TorBox cache availability). Empty `streams` list → "Not available on TorBox" — do not navigate to player.

**No caching.** Torrentio/TorBox URLs are time-limited.

---

## TmdbRepository

Owns its own `DiskCache` instance pointed at `"tmdb_api"` directory (distinct from IPTVRepository's `"iptv_api"` directory) so `switchServer()` clearing the IPTV cache does not evict TMDB data.

```kotlin
class TmdbRepository(
    private val tmdb: TmdbApiService,
    private val torrentio: TorrentioService,
    private val diskCache: DiskCache   // DiskCache("tmdb_api"), NOT the shared IPTV one
) {
    // Genres — 24h TTL
    suspend fun getMovieGenres(): List<TmdbGenre>
    suspend fun getTvGenres(): List<TmdbGenre>

    // Catalog — 6h TTL
    suspend fun getMovieList(listName: String): List<TmdbMovie>   // "popular"|"top_rated"|"now_playing"
    suspend fun getTvList(listName: String): List<TmdbShow>
    suspend fun getMoviesByGenre(genreId: Int): List<TmdbMovie>
    suspend fun getShowsByGenre(genreId: Int): List<TmdbShow>

    // Details — 7d TTL (includes imdbId, runtime/numberOfSeasons)
    suspend fun getMovieDetail(id: Int): TmdbMovie
    suspend fun getShowDetail(id: Int): TmdbShow
    suspend fun getSeasons(showId: Int, count: Int): List<TmdbSeason>   // fetches each season; filters out season 0 (Specials) unless it has episodes

    // Search — no cache
    suspend fun searchMovies(query: String): List<TmdbMovie>
    suspend fun searchShows(query: String): List<TmdbShow>

    // Streams — no cache
    suspend fun getMovieStream(imdbId: String): TorrentioStream?
    suspend fun getEpisodeStream(imdbId: String, season: Int, episode: Int): TorrentioStream?
}
```

---

## MainViewModel changes

### Constructor

```kotlin
class MainViewModel(
    val repository: IPTVRepository,
    private val tmdbRepository: TmdbRepository,   // new
    private val serversStore: ServersStore,
    private val preferencesStore: PreferencesStore
) : ViewModel()
```

### Two load helpers

The existing `load(block: suspend (Credentials) -> BrowseContent)` guards on credentials and is kept for IPTV calls. A new overload handles TMDB:

```kotlin
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
```

### Category loading

`Section.VOD` → loads TMDB movie genres prepended with pseudo-categories:
- `Category("-1", "🔥 Popular")`
- `Category("-2", "⭐ Top Rated")`
- `Category("-3", "🎬 Now Playing")`
- Then real genre list from TMDB

`Section.SERIES` → same pattern with:
- `Category("-1", "🔥 Popular")`
- `Category("-2", "⭐ Top Rated")`
- `Category("-3", "📺 Airing Today")`

### Content loading (selectCategory)

For `Section.VOD` / `Section.SERIES`, dispatch to TMDB:
```kotlin
val genreId = category.id.toIntOrNull() ?: return
if (genreId < 0) {
    // pseudo-category
    val listName = when (genreId) {
        -1 -> "popular"; -2 -> "top_rated"; -3 -> if (mode == VOD) "now_playing" else "on_the_air"; else -> "popular"
    }
    loadTmdb(pushBack = true) {
        if (mode == VOD) BrowseContent.TmdbMovies(tmdbRepository.getMovieList(listName), category.name)
        else BrowseContent.TmdbShows(tmdbRepository.getTvList(listName), category.name)
    }
} else {
    loadTmdb(pushBack = true) {
        if (mode == VOD) BrowseContent.TmdbMovies(tmdbRepository.getMoviesByGenre(genreId), category.name)
        else BrowseContent.TmdbShows(tmdbRepository.getShowsByGenre(genreId), category.name)
    }
}
```

### New actions

```kotlin
// User taps a movie → fetch detail (for imdbId + runtime), then resolve stream → play
fun onPlayTmdbMovie(movie: TmdbMovie) {
    viewModelScope.launch {
        _state.update { it.copy(isLoading = true) }
        val detail = tmdbRepository.getMovieDetail(movie.id)
        val imdbId = detail.imdbId
        if (imdbId == null) { showNotAvailableToast(); return@launch }
        val stream = tmdbRepository.getMovieStream(imdbId)
        if (stream == null) { showNotAvailableToast(); return@launch }
        _state.update { it.copy(isLoading = false) }
        _playEvent.tryEmit(PlayEvent(
            url = stream.url,
            title = detail.title,
            contentType = "tmdb_movie",
            contentId = detail.id.toString()   // stable TMDB ID for position tracking
        ))
    }
}

// User taps a show → fetch detail + seasons → TmdbShowDetail
fun selectTmdbShow(show: TmdbShow) {
    loadTmdb(pushBack = true) {
        val detail = tmdbRepository.getShowDetail(show.id)
        val seasons = tmdbRepository.getSeasons(show.id, detail.numberOfSeasons)
        BrowseContent.TmdbShowDetail(detail, seasons)
    }
}

// User taps an episode → resolve stream → play
fun onPlayTmdbEpisode(show: TmdbShow, episode: TmdbEpisode) {
    viewModelScope.launch {
        _state.update { it.copy(isLoading = true) }
        val imdbId = show.imdbId
        if (imdbId == null) { showNotAvailableToast(); return@launch }
        val stream = tmdbRepository.getEpisodeStream(imdbId, episode.seasonNumber, episode.episodeNumber)
        if (stream == null) { showNotAvailableToast(); return@launch }
        val title = "${show.name} S${episode.seasonNumber}E${episode.episodeNumber} — ${episode.name}"
        val stableId = "${show.id}:${episode.seasonNumber}:${episode.episodeNumber}"
        _state.update { it.copy(isLoading = false) }
        _playEvent.tryEmit(PlayEvent(url = stream.url, title = title, contentType = "tmdb_episode", contentId = stableId))
    }
}
```

`PlayEvent` gains a `contentId: String = ""` field. `MainActivity` uses this directly as `posKey` when `contentType` starts with `"tmdb_"`, bypassing the fragile URL substring extraction.

### Search

Search for TMDB sections uses `collectLatest` on the `searchQuery` StateFlow to auto-cancel in-flight queries when the user types again:

```kotlin
init {
    // Debounced TMDB search
    viewModelScope.launch {
        snapshotFlow { _state.value.searchQuery }
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
```

Minimum 2 characters before firing. 400ms debounce. `collectLatest` cancels prior request automatically. For IPTV sections, search remains a local filter (unchanged).

### Preloading

`preloaded` changes from `Boolean` to `private val preloaded = mutableSetOf<Section>()`. Each section is preloaded at most once:

```kotlin
private fun preloadOtherCategories(current: Section) {
    val toPreload = listOf(Section.LIVE, Section.VOD, Section.SERIES).filter {
        it != current && it !in preloaded
    }
    // IPTV sections preload via existing credentials-gated path
    // TMDB sections preload genre lists via loadTmdb (no credentials needed)
}
```

### Favorites and RecentlyWatched

TMDB content **can** be favorited and added to recently watched. New type strings: `"tmdb_movie"` and `"tmdb_episode"`.

`onPlayFavorite` and `onPlayRecent` gain new branches:

```kotlin
"tmdb_movie"   -> onPlayTmdbMovieById(item.id)
"tmdb_episode" -> onPlayTmdbEpisodeById(item.id)   // id = "showId:season:episode"
```

`onPlayTmdbMovieById(id: String)` fetches movie detail by TMDB ID, then resolves Torrentio stream. `onPlayTmdbEpisodeById` parses the composite `"showId:season:episode"` string, fetches show detail for IMDB ID, then resolves stream.

---

## UI Components

### Enhanced PosterCard (TMDB variant)

Card base size: `GridCells.Adaptive(180.dp)` (up from 150dp — legible text at 10 feet).

**No `AnimatedVisibility` inside grid items.** Grid item size is fixed. Extra info on focus is shown as an absolute overlay using `Box` + `zIndex(1f)` positioned above the card, so neighboring items do not reflow when focus changes:

```
Unfocused:            Focused (overlay):
┌─────────────┐       ┌─────────────┐
│   [poster]  │       │   [poster]  │  ← same size, no reflow
│             │       │             │
├─────────────┤       ├─────────────┤
│ Title       │       │ Title       │
│          ♡ │       │ 2024 · 2h9m │  ← extra info in overlay
└─────────────┘       │ ⭐ 8.4      │    floats ABOVE the card
                      │ Overview…   │    with zIndex elevation
                      └─────────────┘
```

Rating badge ("⭐ 8.4") is always visible at top-right corner of poster (not focus-dependent).

### TmdbMovieGrid / TmdbShowGrid

`LazyVerticalGrid(GridCells.Adaptive(180.dp))` using the enhanced `PosterCard`.

### TmdbShowDetailView

When a show is selected (`BrowseContent.TmdbShowDetail`):
- **Top banner** (~180dp): backdrop image + title + rating + overview (3 lines)
- **Below**: `LazyColumn` of season headers with episode rows underneath, using `stickyHeader` for season labels
- Each episode row: episode number, title, runtime
- `FocusRequester` anchored to the first episode of the first season; `LaunchedEffect(seasons)` requests focus after load
- User presses OK on an episode row → `onPlayTmdbEpisode(show, episode)`
- Season 0 (Specials) filtered out unless it has >0 episodes

---

## contentType strings

| Content | contentType | contentId |
|---|---|---|
| IPTV live stream | `"live"` | stream ID from URL |
| IPTV VOD | `"vod"` | VOD stream ID |
| IPTV episode | `"episode"` | episode ID |
| TMDB movie | `"tmdb_movie"` | TMDB movie ID (stable) |
| TMDB episode | `"tmdb_episode"` | `"tmdbShowId:season:episode"` (stable) |

`PlayerActivity.isVod = contentType != "live"` — all TMDB types correctly treated as VOD (large buffer, seek controls).

`MainActivity.contentId` derivation: if `contentType.startsWith("tmdb_")`, use `PlayEvent.contentId` directly. Otherwise use existing URL substring logic.

---

## Caching

| Key pattern | TTL | Cache instance |
|---|---|---|
| `tmdb_movie_genres` | 24 h | TMDB cache |
| `tmdb_tv_genres` | 24 h | TMDB cache |
| `tmdb_movies_{listName}` | 6 h | TMDB cache |
| `tmdb_shows_{listName}` | 6 h | TMDB cache |
| `tmdb_movies_genre_{id}` | 6 h | TMDB cache |
| `tmdb_shows_genre_{id}` | 6 h | TMDB cache |
| `tmdb_movie_detail_{id}` | 7 days | TMDB cache |
| `tmdb_show_detail_{id}` | 7 days | TMDB cache |
| `tmdb_show_seasons_{id}` | 7 days | TMDB cache |
| Torrentio streams | **No cache** | — |
| All IPTV data | unchanged | IPTV cache (`"iptv_api"`) |

`switchServer()` only clears the IPTV cache. TMDB cache is unaffected.

---

## Error handling

- **TMDB failure**: existing `ErrorView` with retry
- **Missing IMDB ID**: "Not available on TorBox" — stay on browse screen
- **Torrentio empty streams**: same — content not cached on TorBox
- **Torrentio network error**: same toast
- **`streams[0]` stalls in ExoPlayer**: ExoPlayer's existing error listener in `PlayerActivity` handles playback failures the same as any other stream error — no additional handling needed for MVP

---

## Credentials

```kotlin
// AppConfig.kt
object AppConfig {
    const val TMDB_BEARER        = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJiNDk3ZjZhN2FlZWZhNGYxYWQ3ZmRmZmFhMmQ0NGI1YSIsIm5iZiI6MTcwNTMwNTk1Mi45NjIsInN1YiI6IjY1YTRlNzYwOGEwZTliMDEyYmI0NWMzNiIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ.Tk5zzydMxXC6DwBB3xKgzSPhuulFBrsyVBWbjltsgZE"
    const val TORBOX_API_KEY     = "4bdfd9b6-6052-4e45-b8a5-215cddc76ca3"
    const val TORRENTIO_BASE     = "https://torrentio.strem.fun"
    const val TMDB_IMAGE_BASE    = "https://image.tmdb.org/t/p"
    const val TMDB_POSTER_SIZE   = "w342"
    const val TMDB_BACKDROP_SIZE = "w780"
}
```

---

## Open questions (resolve at implementation start)

1. **Torrentio TorBox URL format** — verify exact `{config}` path encoding against a live Torrentio instance before writing `TorrentioService`. This is the first step.
2. **Pagination** — page 1 only (20 items) for MVP. Infinite scroll is a follow-up.
