# RizzoIPTVPlayer v4 — Architecture Audit

**Branch:** `v4-polish` | **Genesis tag:** `v4-genesis` | **Date:** 2026-04-23

---

## 1. Architecture Map

```
com.rizzoplayer.iptv
├── data/
│   ├── api/
│   │   ├── NetworkClient.kt          — OkHttp/Retrofit singleton, 15s connect / 20s read timeout
│   │   ├── TmdbApiService.kt         — TMDB REST API (search, discovery, streams)
│   │   ├── TorBoxApiService.kt        — TorBox debrid resolution API
│   │   ├── TorBoxSearchService.kt     — search-api.torbox.app (cached torrent index)
│   │   ├── TorrentioService.kt        — torrentio.strem.fun scraper
│   │   ├── IPTVApiService.kt          — M3U playlist / Xtream API
│   │   ├── OpenSubtitlesService.kt    — Subtitles
│   │   └── TraktService.kt            — Trakt.tv integration
│   ├── local/
│   │   ├── CredentialsStore.kt         — DataStore "credentials" (auth tokens)
│   │   ├── FavoritesStore.kt          — DataStore "favorites" (serialized JSON)
│   │   ├── RecentlyWatchedStore.kt    — DataStore "recently_watched" (serialized JSON)
│   │   ├── PlaybackPositionStore.kt    — DataStore "playback_positions"
│   │   ├── ServersStore.kt             — DataStore "servers" (IPTV server configs)
│   │   ├── PreferencesStore.kt        — ⚠️ SharedPreferences "app_prefs" (should be DataStore)
│   │   └── DiskCache.kt               — File-based image cache (Coil disk cache wrapper)
│   ├── model/
│   │   ├── Models.kt                  — Core: Stream, Channel, Category, Section, BrowseContent, Credentials, RecentItem, Favorite, ServerConfig
│   │   ├── TmdbModels.kt              — TmdbMovie, TmdbShow, TmdbSeason, TmdbEpisode, TorrentioStream
│   │   ├── TorBoxModels.kt            — TorBox cache status, resolve response
│   │   └── TorBoxSearchModels.kt      — TorBoxSearchResponse, UnifiedTorrent, StreamSelectionState
│   └── repository/
│       ├── TmdbRepository.kt          — 320 lines. cachedList flow, coalescing, searchAll (FIXED: now uses cached searchMovies/searchShows with try-catch)
│       ├── TorBoxRepository.kt        — 388 lines. resolveFallback(hash), resolveByHashCached, addTorrentCached, pollCached
│       ├── IPTVRepository.kt          — M3U/Xtream parsing, channel/category loading
│       └── SubtitleRepository.kt       — OpenSubtitles search/download
├── ui/
│   ├── theme/
│   │   ├── Color.kt                  — AccentBlue (#2563EB), dark palette
│   │   ├── Theme.kt                  — DarkMaterialTheme composable
│   │   └── TvFocus.kt                — tvClickable() modifier, TvCardSpringSpec, gridRowFocus(), TV_CARD_FOCUS_SCALE=1.04f
│   ├── navigation/
│   │   ├── Screen.kt                 — Section (LIVE/VOD/SERIES/MOVIES/SEARCH), Screen enum
│   │   └── FocusManager.kt            — TV focus traversal utilities
│   ├── player/
│   │   ├── PlayerActivity.kt          — 1592 lines. ExoPlayer lifecycle, PiP, PlayerView wrapper
│   │   └── PlayerPool.kt              — ExoPlayer instance pool
│   ├── viewmodel/
│   │   ├── MainViewModel.kt          — 709 lines. ⚠️ TOO BIG — 4 responsibilities
│   │   ├── HomeViewModel.kt           — 576 lines. ⚠️ TOO BIG
│   │   ├── LoginViewModel.kt
│   │   └── ViewModelFactory.kt
│   └── screens/
│       ├── HomeScreen.kt              — 750 lines. ⚠️ TOO BIG
│       ├── LoginScreen.kt
│       ├── SettingsScreen.kt
│       ├── ParentalLockOverlay.kt
│       └── home/
│           ├── MoviesHome.kt          — 651 lines. ⚠️ TOO BIG
│           ├── SeriesHome.kt          — 566 lines. ⚠️ TOO BIG
│           ├── ContinueWatchingStrip.kt— 358 lines. ⚠️ TOO BIG
│           ├── TmdbDetailScreen.kt    — 217 lines.
│           ├── TmdbSearchResultsContent.kt — 308 lines. ⚠️ TOO BIG
│           ├── StreamSelectionOverlay.kt — 486 lines. ⚠️ TOO BIG
│           ├── CategoryComponents.kt
│           ├── ChannelComponents.kt
│           ├── FavoritesView.kt
│           ├── SearchBar.kt
│           └── Sidebar.kt
└── MainActivity.kt, RizzoApp.kt, AppConfig.kt
```

---

## 2. Compose Anti-Patterns

### Files > 300 lines (must split in v4)
| File | Lines | Issues |
|------|-------|--------|
| `PlayerActivity.kt` | 1592 | Lifecycle + UI + player controls all in one file |
| `HomeScreen.kt` | 750 | TOO BIG — nav shell + multiple content branches |
| `MoviesHome.kt` | 651 | Single file handles movie browse + hero + grid |
| `SeriesHome.kt` | 566 | Series browse + hero + season/episode |
| `MainViewModel.kt` | 709 | 4 responsibilities (see section 4) |
| `HomeViewModel.kt` | 576 | 3 responsibilities |
| `TmdbSearchResultsContent.kt` | 308 | Slightly over, needs trim |
| `StreamSelectionOverlay.kt` | 486 | Stream UI + selection logic |
| `ContinueWatchingStrip.kt` | 358 | Over 300 |
| `TorBoxRepository.kt` | 388 | Repository, acceptable |
| `TmdbRepository.kt` | 320 | Repository, acceptable |

### Missing `@Immutable` / `@Stable`
- `MainUiState` has `@Immutable` on `EpgInfo` but NOT on the data class itself — should be `@Immutable`
- `BrowseContent` is a sealed class but its subclasses may hold mutable lists — audit each `results` field
- `StreamSelectionState`, `TmdbStreamSelectionState` — not annotated
- `PlaybackPositionStore` — no `@Immutable` on position data classes

### Recomposition Hotspots
- `HomeScreen` recomposes on every `MainUiState` change (single monolithic state object) — needs fine-grained state
- `TmdbSearchResultsContent` — lazy loading but no `key {}` on items
- `Sidebar` — likely recomposes on every section change when it only needs to highlight the selected item

### Missing `key()` in LazyLists
- `MoviesHome`, `SeriesHome`, `TmdbSearchResultsContent` — need audit for `items(key = ...)` in LazyRow/LazyVerticalGrid

### Coil configuration
- Not explicitly configured with `crossfade(true)` and explicit `size()` in image loaders — needs verification

---

## 3. Focus Handling

### Current State
`TvFocus.kt` provides:
- `tvClickable()` — the base modifier: scale 1.04x / 0.95x, AccentBlue border 2dp, MutableInteractionSource + collectIsFocusedAsState ✓
- `TvCardSpringSpec` — consistent spring spec
- `gridRowFocus(upFocus, downFocus)` — FocusRequester coordination for rows
- `gridTopRowFocus(firstItemFocus)` — DOWN from top row wraps to first item

### Gaps
1. **Inconsistent focus scale** — `tvClickable` uses 1.04x but some cards may use hardcoded values
2. **No `rizzoFocusable()` extension** — should be the single canonical Modifier extension wrapping all focus behavior
3. **No `rizzoFocusGroup()`** — no LazyRow/LazyVerticalGrid coordination primitive
4. **No cross-screen focus restoration** — focus is lost on navigation; no `remember { FocusRequester() }` persistence across screens
5. **Sidebar focus** — left/right edge at column 0 / last column edge handling is ad-hoc
6. **BackHandler** — there's a BackHandler in HomeScreen with an unreachable else-branch (already fixed in v3 branch)
7. **PiP button focus** — player controls may not all be focusable

---

## 4. ViewModel Sizing

### MainViewModel.kt (709 lines) — 4 RESPONSIBILITIES
1. **Home state** — section, content, loading, error, searchQuery, scroll positions
2. **Stream resolution** — TorBox add/poll/resolve, PlaybackPrep, stream selection state
3. **EPG** — EpgInfo loading and display
4. **Auth/session** — credentials, nowPlaying, parental lock

**Required split:**
- `MainViewModel` → thin coordinator exposing combined `MainUiState`
- `PlaybackViewModel` — stream resolution, PlaybackPrep, PlaybackPositionStore
- `EpgViewModel` — EPG loading/refresh
- `HomeViewModel` stays (but should be trimmed from 576 → ~400 lines)

### HomeViewModel.kt (576 lines) — 3 RESPONSIBILITIES
1. TMDB content loading (movies, shows, discover)
2. Scroll state management
3. Category/section selection

**Required split:**
- `BrowseViewModel` — content loading, scroll state, category selection
- Keep `HomeViewModel` for TMDB data coordination only

---

## 5. DataStore Hygiene

### Current State
| Store | Backend | File Name | v4 Status |
|-------|---------|-----------|-----------|
| CredentialsStore | DataStore | `credentials` | ⚠️ Same name across flavors |
| FavoritesStore | DataStore | `favorites` | ⚠️ Same name across flavors |
| RecentlyWatchedStore | DataStore | `recently_watched` | ⚠️ Same name across flavors |
| PlaybackPositionStore | DataStore | `playback_positions` | ⚠️ Same name across flavors |
| ServersStore | DataStore | `servers` | ⚠️ Same name across flavors |
| **PreferencesStore** | **SharedPreferences** | `app_prefs` | **⚠️ MUST MIGRATE to DataStore** |

### Issues
1. **SharedPreferences in `PreferencesStore`** — the only remaining SharedPreferences. Must migrate to DataStore for v4.
2. **Flavor collision** — all DataStore files use the same name across v2/v3/v4. Since Android scopes DataStore by package, different `applicationId` (`.v2`, `.v3`, `.v4`) means different files. **Safe by default**, but add explicit flavor suffixes for clarity.
3. **No migration path** — if PreferencesStore moves to DataStore, old SharedPreferences data is lost. Add a migration.

---

## 6. Dependency Inventory

### Compose
- BOM: `2024.05.00` — pinned, do not bump without concrete reason
- Compose Compiler: `1.5.13`
- Compose UI, Foundation, Material3, Runtime, Animation

### Media
- `androidx.media3:media3-exoplayer` — via Media3
- `androidx.media3:media3-ui` — PlayerView
- `androidx.media3:media3-exoplayer-hls`
- `androidx.media3:media3-exoplayer-dash`

### Networking
- `com.squareup.retrofit2:retrofit` — REST
- `com.squareup.okhttp3:okhttp` + interceptors
- `com.squareup.okhttp3:logging-interceptor`

### Image Loading
- `io.coil-kt:coil-compose` — with `ImageLoader` custom config

### Serialization
- `org.jetbrains.kotlinx:kotlinx-serialization-json`

### Data
- `androidx.datastore:datastore-preferences`
- `androidx.lifecycle:lifecycle-viewmodel-compose`

### TV
- `androidx.leanback:leanback` — (partial use, not full Leanback fragments)

---

## 7. ExoPlayer Setup

- `PlayerPool.kt` — manages ExoPlayer instances (pool for concurrent playback)
- `PlayerActivity.kt` — owns the ExoPlayer lifecycle
- `PlaybackPositionStore` — persists position per content ID
- **DO NOT MODIFY** — core playback semantics off-limits

---

## 8. TorBox Client Wiring

```
TorBoxSearchService  →  TorBoxRepository.resolveFallback(hash)
                             → TorBoxApiService.addCachedTorrent(magnet)
                             → poll cached status
                             → resolve to direct URL
UnifiedTorrent       →  used by StreamSelectionState in MainViewModel
playSelectedTmdbStream → torBoxRepository.resolveFallback(stream.hash)
```

---

## 9. TMDB Contracts

```
TmdbApiService:
  searchMovies(query)    → TmdbPage<TmdbMovie>
  searchShows(query)     → TmdbPage<TmdbShow>
  fetchMovieStreams(id)  → Stream sources
  fetchShowStreams(id)   → Stream sources
  discoverMovies/sHOWs   → Browse content

TmdbRepository:
  searchMovies/SearchShows  — cachedList flow with coalescing
  discoverMovies/SHOWs     — cachedList flow
  searchAll(query)          — FIXED: uses cached methods with try-catch
```

---

## 10. PlaybackResolver State Machine

```
StreamSelectionState / TmdbStreamSelectionState
  streams: List<UnifiedTorrent>
  selectedIndex: Int
  status: StreamSelectionStatus (searching/queuing/caching/ready/failed)

playSelectedTmdbStream(UnifiedTorrent):
  hash == null  → direct HLS URL → playUrl()
  hash != null  → torBoxRepository.resolveFallback(hash) → PlaybackPrep → PlayerActivity
```

---

## 11. Anti-Pattern Summary (Priority Order)

1. **[CRITICAL]** `PreferencesStore` uses SharedPreferences — must migrate to DataStore
2. **[CRITICAL]** `MainViewModel` 709 lines / 4 responsibilities — must split
3. **[HIGH]** `HomeScreen` 750 lines — must split into nav shell + content screens
4. **[HIGH]** `HomeViewModel` 576 lines / 3 responsibilities — must split
5. **[HIGH]** `PlayerActivity` 1592 lines — must split player controls
6. **[MED]** `HomeScreen` recomposes on every state change — fine-grained state needed
7. **[MED]** Missing `key()` in LazyRow/LazyVerticalGrid items
8. **[MED]** `BrowseContent` sealed class subclasses need `@Immutable` audit
9. **[MED]** `StreamSelectionState`, `TmdbStreamSelectionState` need `@Immutable`
10. **[LOW]** `MoviesHome`, `SeriesHome` over 500 lines — split by section
11. **[LOW]** No Compose Compiler Metrics baseline
12. **[LOW]** Coil explicit `crossfade(true)` + `size()` not verified in all callers
