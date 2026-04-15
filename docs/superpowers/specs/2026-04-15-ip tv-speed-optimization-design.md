# RizzoIPTVPlayer — Speed Optimization Plan

**Date:** 2026-04-15
**Goal:** Eliminate measurable latency, jank, and unnecessary waits — make the app feel instant like VLC and as smooth as Stremio.
**Constraint:** No UI design, color, layout, or feature changes. Pure performance only.

---

## Phase 1 — Critical Fixes (Highest Impact)

### 1.1 — Cache EPG data (`IPTVRepository.kt`)
- `getShortEpg()` currently makes a raw network call every time a user opens a live channel.
- Add 10-minute TTL cache using existing `DiskCache` instance.
- Cache key: `"epg_${streamId}"`. Use `DiskCache.TTL_STREAMS`.
- Follow existing `cachedList()` pattern already in the same file.

### 1.2 — Cache Torrentio stream responses (`TmdbRepository.kt`)
- Torrentio stream fetches (~line 123) have zero caching — users wait 2–5s on every TMDB play.
- Add 20-minute TTL cache via `DiskCache`.
- Cache key: `"torrentio_${imdbId}"`.

### 1.3 — Async playback position save (`PlaybackPositionStore.kt`)
- `writeToDisk()` called synchronously in `PlayerActivity.onDestroy()`.
- Change to `lifecycleScope.launch(Dispatchers.IO) { playbackPositionStore.writeToDisk() }`.

---

## Phase 2 — JSON Parsing Off Main Thread

- Every `gson.fromJson(...)` in `IPTVApiService.kt` and `TmdbApiService.kt`.
- Wrap with `withContext(Dispatchers.IO) { ... }`.
- Critical for responses parsing 100+ items (live streams, VOD, series) — prevents 10–50ms UI thread stutter.

---

## Phase 3 — Compose Recomposition Optimization

**Option C chosen (full extraction — maximum performance, full judgment call):**

### Data Models (`data/model/*.kt`)
- Add `@Stable` annotation to all data classes passed as `@Composable` function parameters.
- Annotate classes with `@Immutable` where lists never mutate after creation.

### `HomeScreen.kt` (1,669 lines)
- Extract large `@Composable` functions into sub-composables.
- Add `key(item.id) { … }` wrappers around every `LazyColumn`/`LazyRow` item block.
- Goal: list items recompose only when their own data changes, not siblings.

---

## Phase 4 — Network Request Deduplication

- Add `ConcurrentHashMap<String, Deferred<*>>` to `IPTVRepository.kt`.
- Implement `coalesced()` helper to prevent duplicate in-flight requests during rapid navigation.
- Wrap live streams, VOD streams, and series list fetches with `coalesced(cacheKey) { ... }`.

---

## Phase 5 — ExoPlayer Buffer Tuning

**Live TV:**
| Parameter       | Current | New    |
|-----------------|---------|--------|
| setMinBufferMs  | 1500ms  | 2000ms |
| setMaxBufferMs  | 8000ms  | 12000ms|
| setBufferForPlaybackMs | 1000ms | 800ms |
| setBufferForPlaybackAfterRebufferMs | 1500ms | 1200ms |

**VOD:** `setMaxBufferMs` 60s → 90s.

- Prefer hardware-decodable codecs via `setPreferredVideoMimeTypes("video/avc", "video/hevc")`.
- Add `setMaxVideoBitrate(Int.MAX_VALUE)`.

---

## Phase 6 — OkHttp HTTP/2 and Connection Optimization

- Enable HTTP/2: `.protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))`.
- Connection pool: `ConnectionPool(10, 2, TimeUnit.MINUTES)`.
- Add HTTP-level cache: `Cache(File(context.cacheDir, "okhttp_cache"), 10L * 1024 * 1024)`.

---

## Phase 7 — Category Preloading Prioritization

- In `MainViewModel.kt` (around line 148), reorder preload to:
  1. Last-visited section first (from `PreferencesStore`)
  2. Then: LIVE → VOD → SERIES on subsequent launches.

---

## Phase 8 — Coil Image Loading Optimization

- Add to `ImageLoader.Builder`:
  - `.fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(4))`
  - `.decoderCoroutineContext(Dispatchers.Default.limitedParallelism(2))`
  - `.networkCachePolicy(CachePolicy.ENABLED)`
  - `.diskCachePolicy(CachePolicy.ENABLED)`
  - `.memoryCachePolicy(CachePolicy.ENABLED)`
- Add `placeholder` to `AsyncImage` calls in `HomeScreen.kt`.

---

## Phase 9 — Lazy Loading for Large Stream Lists

- Find stream list `LazyColumn`/`LazyRow` calls in `HomeScreen.kt`.
- Show first 50 items initially.
- Trigger "load more" when scroll reaches end via `LazyListState.isScrolledToEnd()`.
- Prevents Compose diffing algorithm from processing 500+ items on initial render.

---

## Phase 10 — Build Optimization

- Add `android.enableR8.fullMode=true` to `gradle.properties`.
- Add ABI splits (`arm64-v8a`, `x86_64`) — drops ~30% APK size.
- Add baseline profile support:
  - Create empty `app/src/main/baseline-prof.txt`.
  - Add `implementation("androidx.profileinstaller:profileinstaller:1.3.1")`.

---

## Testing Protocol (after each phase)

1. `./gradlew assembleDebug` — must succeed
2. Cache validation — log hits vs network calls; verify cache hit on second open
3. Thread check — zero StrictMode violations for disk I/O on main thread
4. Recomposition check — list items flash green only on initial load, not scroll
5. Playback start latency — target under 2s on 50Mbps connection
6. Memory check — heap stabilizes after first pass (LruCache eviction working)

## Rules
- No string resources, colors, drawables, or layout dimension changes
- No library version upgrades
- No new dependencies except where Phase 10 explicitly requires it
- Compile clean before moving to the next phase
- Commit after each phase with message: `perf: [Phase N] <description>`