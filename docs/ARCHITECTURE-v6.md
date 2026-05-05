# RizzoIPTVDesktop — Architecture Analysis v6.0.0

> Deep review of the TMDB data pipeline, category ID system, and curated row implementation.
> Reviewed: 2026-05-05. Branch: v5-p0-phase0.

---

## 1. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│  UI Layer (Compose Screens)                                              │
│  MoviesHome / SeriesHome / SearchScreen / MainScreen                     │
│         │ calls                        │ calls                          │
│         ▼                              ▼                                │
│  ┌─────────────────────────────────────────────────────────────┐        │
│  │  MainViewModel (AndroidViewModel — single source of truth)  │        │
│  │  - Manages ALL navigation state (Section, BrowseContent)   │        │
│  │  - Coordinates IPTV + TMDB + TorBox repositories           │        │
│  │  - Handles playback flow (prep, stream selection, fallback)│        │
│  └───────────────────────┬─────────────────────────────────────┘        │
│                          │                                               │
│           ┌──────────────┼──────────────┐                              │
│           ▼              ▼              ▼                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                    │
│  │IPTVRepository│  │TmdbRepository│ │TorBoxRepository│                  │
│  │ (IPTV API)  │  │(TMDB REST)   │  │(Torrentio)    │                  │
│  └──────┬──────┘  └──────┬──────┘  └──────┬───────┘                    │
│         │                 │                 │                             │
│         ▼                 ▼                 ▼                             │
│  NetworkClient      NetworkClient      TorBoxClient                      │
│  (OkHttp)           (OkHttp)          (OkHttp)                           │
│  + DiskCache        + OkHttp cache    + torrentio.org API               │
│                     + Bearer auth                                     │
│                     + stale-while-revalidate                            │
└─────────────────────────────────────────────────────────────────────────┘
```

### Key Design Decisions

| Pattern | Implementation | Benefit |
|---------|---------------|---------|
| Single ViewModel | `MainViewModel` owns ALL UI state | One source of truth, no sync issues |
| Sealed class `BrowseContent` | 12 exhaustive content types | Type-safe content rendering |
| `loadTmdb {}` wrapper | Sets `isLoading=true`, catches exceptions, manages back stack | Consistent loading UX |
| Request coalescing | `ConcurrentHashMap<String, Deferred>` in TmdbRepository | Prevents duplicate in-flight requests |
| Disk caching | TTL-based JSON serialization per endpoint type | Offline support, reduced API calls |
| Speculative stream prefetch | `ConcurrentHashMap` with 60s TTL | Near-instant playback for recently-browsed content |

---

## 2. TMDB Category ID System

Categories use a **signed integer ID** to route to content sources:

| ID Range | Type | Source | Example |
|----------|------|--------|---------|
| `> 0` (positive) | IPTV genre | `Category` from Xtream API | `Category("12", "Drama")` |
| `< 0` (negative) | Curated row | TMDB discover queries | `Category("-5", "🍿 Netflix")` |
| `"H:..."` (header) | Section divider | Purely visual, no-op on click | `Category("H:platforms", "─── STREAMING ───")` |

### VOD (Movies) Curated Row ID Map

| ID | Label | Switch → Repository Call | Notes |
|----|-------|--------------------------|-------|
| -18 | 🏆 Oscar Winners | `getOscarWinnerMovies()` | ✅ Correct |
| -19 | 🎖️ Oscar Nominated | `getOscarNominatedMovies()` | ✅ Correct |
| -20 | ⭐ IMDB Top Rated | `getTopRatedMoviesAllTime()` | ✅ Correct |
| -21 | 🍅 Certified Fresh | `getCriticallyAcclaimedMovies()` | ✅ RT-style filter |
| -22 | 🍿 Audience Favorites | `getAudienceFavorites()` | ✅ RT Audience ≥85% |
| -23 | 📺 HBO Max | ❌ **MISSING** | Fixed in v6 |
| -24 | 🍎 Apple TV+ | ❌ **MISSING** | Fixed in v6 |
| -25 | 🦚 Peacock | ❌ **MISSING** | Fixed in v6 |
| -34 | 🦸 Marvel | `getMarvelMovies()` | ✅ |
| -35 | 🚀 Star Wars | `getStarWarsMovies()` | ✅ |
| -36 | 🏰 Disney Family | `getDisneyFamilyMovies()` | ✅ |
| -37 | 🦇 DC Comics | `getDcMovies()` | ✅ |
| -38 | 🚗 Fast & Furious | `getFastFuriousMovies()` | ✅ |
| -39 | 🔮 Pixar | `getPixarMovies()` | ✅ |
| -40 | ⚡ Harry Potter | `getHarryPotterMovies()` | ✅ |

### Series (TV) Curated Row ID Map

| ID | Label | Switch → Repository Call | Notes |
|----|-------|--------------------------|-------|
| -1 | 🔥 Popular | `getPopularShows()` | ✅ |
| -2 | ⭐ Top Rated | `getTopRatedShows()` | ✅ |
| -3 | 📺 Airing Today | `getOnTheAirShows()` | ✅ |
| -4 | 📈 Trending | `getTrendingShows()` | ✅ |
| -5 | 🍿 Netflix | `getNetflixShows()` | ✅ Fixed in v6 |
| -6 | 🎬 Prime Video | `getPrimeShows()` | ✅ Fixed in v6 |
| -7 | ✨ Disney+ | `getDisneyShows()` | ✅ Fixed in v6 |
| -8 | 📺 Hulu | `getHuluShows()` | ✅ Fixed in v6 |
| -9 | 🎭 Paramount+ | `getParamountShows()` | ✅ Fixed in v6 |
| -10 | 🦚 Peacock | `getPeacockShows()` | ✅ Fixed in v6 |
| -11 | 🏆 Critically Acclaimed | `getCriticallyAcclaimedShows()` | ✅ Fixed in v6 |
| -12 | 🎌 Anime | `getAnimeShows()` | ✅ Fixed in v6 |
| -13 | 📺 Reality TV | `getRealityShows()` | ✅ Fixed in v6 |
| -14 | 🎬 Documentaries | `getDocumentaryShows()` | ✅ Fixed in v6 |
| -15 | 📱 Mini Series | `getMiniSeries()` | ✅ Fixed in v6 |
| -16 | 👶 Kids | `getKidsShows()` | ✅ Fixed in v6 |
| -17 | 🇰🇷 Korean Dramas | `getKoreanDramas()` | ✅ Fixed in v6 |
| -18 | 📺 HBO Max | ❌ **Wrong target** → was `getOscarWinnerMovies()` | Fixed in v6 |
| -19 | 🍎 Apple TV+ | ❌ **Wrong target** → was `getOscarNominatedMovies()` | Fixed in v6 |
| -20 | 🦸 Marvel | `getMarvelShows()` | ✅ Fixed in v6 |
| -21 | 🚀 Star Wars | `getStarWarsShows()` | ✅ Fixed in v6 |
| -22 | 🏰 Disney+ Originals | `getDisneyShowsAll()` | ✅ Fixed in v6 |
| -23 | 🦇 DC Comics | `getDcShows()` | ✅ Fixed in v6 |

---

## 3. TMDB API Layer (TmdbApiService)

**Location:** `app/src/main/java/com/rizzoplayer/iptv/data/api/TmdbApiService.kt`

### Network Stack
- **Client**: OkHttp with custom interceptors
- **Auth**: Bearer token injection via `Authorization: Bearer <token>` header (v4 read-access)
- **Cache**: `stale-while-revalidate` via `Cache-Control` header per endpoint type
- **Error handling**: Silent swallow on all network failures → returns empty result

### Cache-Control Strategy
```
/3/genre/*          → max-age=604800, stale-while-revalidate=604800 (1 week)
/3/discover/*       → max-age=3600,   stale-while-revalidate=86400 (1 day)
/3/movie/* or /3/tv/* → max-age=86400, stale-while-revalidate=604800 (1 week)
/3/search/*        → max-age=600,    stale-while-revalidate=3600 (1 hour)
default             → max-age=300,    stale-while-revalidate=3600
```

### Endpoint Count: 54 methods

### Streaming Provider IDs (US region)
```
8=Netflix, 9=Amazon Prime, 15=Hulu, 337=Disney+, 350=Apple TV+,
384=Max/HBO Max, 386=Peacock, 531=Paramount+
```

### Franchise/Studio IDs
```
420=Marvel, 10=DC, 1=Lucasfilm (Star Wars), 444=Universal, 1241=Disney,
3=Pixar, 4907=Warner Bros (Harry Potter)
```

---

## 4. Repository Layer (TmdbRepository)

**Location:** `app/src/main/java/com/rizzoplayer/iptv/data/repository/TmdbRepository.kt`

### Cache TTLs
```
TTL_CATALOGS      = 6 hours   (genres, curated rows, trending)
/watch providers)
TTL_GENRES        = 7 days
TTL_DETAIL        = 24 hours  (movie/show detail)
TTL_SEARCH        = 10 minutes
TTL_TORRENTIO     = 20 minutes
```

### Request Coalescing
```
coalesced(key) → ConcurrentHashMap<String, Deferred> → prevents duplicate in-flight
```
All detail fetches (getMovieDetail, getShowDetail) use coalescing keyed by content ID.
This means navigating back and forth to the same detail page does NOT refetch.

### Disk Cache Format
- **Path**: App internal storage (`/data/data/com.rizzoplayer.iptv/files/cache/`)
- **Format**: JSON serialized lists/objects
- **Key pattern**: `tmdb_v3_<type>_<id>`

---

## 5. Bugs Found & Fixed in v6.0.0

### BUG #1 — VOD selectCategory: -23/-24/-25 missing (Crash/Runtime)
**Severity**: High | **Type**: Missing switch cases

The VOD switch was missing cases for -23 (HBO Max), -24 (Apple TV+), -25 (Peacock).
These categories appeared in `loadVodCategories()` but tapping them fell through to `else → getPopularMovies()`.

**Fix**: Added cases -23, -24, -25 to VOD switch calling correct `get*HboMaxMovies()`, `getAppleMovies()`, `getPeacockMovies()`.

### BUG #2 — Series selectCategory: -18/-19 wrong target (Wrong Content)
**Severity**: High | **Type**: Incorrect mapping

Series category -18 labeled "📺 HBO Max" was routing to `getOscarWinnerMovies()` (Oscar winners — VOD content).
Series category -19 labeled "🍎 Apple TV+" was routing to `getOscarNominatedMovies()` (Oscar nominated — VOD content).

**Fix**: Remapped -18 → `getHboMaxShows()`, -19 → `getAppleShows()`.

### BUG #3 — Series selectCategory: -5 through -17, -23 missing (Silent Fallthrough)
**Severity**: High | **Type**: Missing switch cases

Series categories -5 (Netflix) through -17 (Korean Dramas), plus -23 (DC Comics), had labels defined in `loadSeriesCategories()` but no switch cases. Tapping any of them fell through to `getPopularShows()`.

**Fix**: Added all missing cases (-5 through -17, -23) to Series switch.

---

## 6. Build & Version

- **Previous version**: 5.0.0 (v5 flavor, `versionCode = 500`)
- **New version**: 6.0.0 (v5 flavor, `versionCode = 600`)
- **Changes**: Bug fixes only (no new features)
- **Breaking changes**: None

---

## 7. Testing Strategy (v6.0.0)

### Manual Test Checklist
- [ ] VOD: Tap "📺 HBO Max" → should show HBO Max movies
- [ ] VOD: Tap "🍎 Apple TV+" → should show Apple TV+ movies
- [ ] VOD: Tap "🦚 Peacock" → should show Peacock movies
- [ ] VOD: Tap "🏆 Oscar Winners" → should show Oscar-winning movies
- [ ] Series: Tap "📺 HBO Max" → should show HBO Max shows
- [ ] Series: Tap "🍎 Apple TV+" → should show Apple TV+ shows
- [ ] Series: Tap "🍿 Netflix" → should show Netflix shows
- [ ] Series: Tap "🎌 Anime" → should show anime shows
- [ ] Series: Tap "🦇 DC Comics" → should show DC Comics shows
- [ ] Back navigation preserves scroll position
- [ ] Offline: cached rows load from disk cache
- [ ] Error: network failure shows error state with retry

---

## 8. Data Flow: Curated Row Tap → Grid Display

```
User taps "🍿 Netflix" in Movies
    │
    ▼
MainViewModel.selectCategory(Category("-5", "🍿 Netflix"), Section.VOD)
    │
    ├─ backStack.push(currentContent)       // saves scroll position
    ├─ genreId = "-5".toIntOrNull() = -5   // negative = curated row
    └─ loadTmdb {                           // sets isLoading=true
            val items = tmdbRepository.getNetflixMovies()
                │
                ├─ diskCache.get("tmdb_v3_netflix_movies", TTL_CATALOGS)
                │     ├─ HIT: return deserialized List<TmdbMovie>
                │     └─ MISS: fetch from TmdbApiService
                │
                └─ TmdbApiService.getNetflixMovies()
                      ├─ discoverMovies("with_watch_providers=8&watch_region=US")
                      ├─ GET /3/discover/movie?with_watch_providers=8&watch_region=US
                      └─ Bearer token injected by OkHttp interceptor
        }
    │
    ▼
MainUiState updates:
  isLoading = false
  content = BrowseContent.TmdbMovies(items, "🍿 Netflix")
  canGoBack = true
    │
    ▼
MoviesHome recomposes:
  LazyVerticalGrid displays row cards with poster + title + rating
```
