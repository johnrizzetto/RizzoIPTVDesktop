# Changelog

All notable changes to this project will be documented in this file.

## 2.1.0

### T1 - Shared OkHttpClient + Connection Preamplification
- **2026-04-19** - Shared `OkHttpClient` across all API services (`IPTVApiService`, `TmdbApiService`, `TorBoxApiService`, `TraktService`, `OpenSubtitlesService`). Single 100MB HTTP/2 cache directory, connection pool, and dispatcher shared at process level. Connection prewarm on initialization. `OpenSubtitlesServiceTest` (5 tests) and `SharedHttpClientTest` (4 tests) added.

### T2 - Batch Position Writes + Strip Log in Release
- **2026-04-19** - Playback position writes batched via `MutableStateFlow.sample(5.seconds)` instead of ~4 writes/sec. ProGuard `-assumenosideeffects` strips `Log.v/d/i` from release binaries. `PositionSaveBatchTest` (3 tests) added.

### T3 - Explicit Coil Image Sizes + Memory Cache Cap
- **2026-04-19** - All `AsyncImage` calls now pass explicit `Size(w, h)` (2× dp for density). `ImageLoader` configured with 15% memory cap and 100MB disk cache. `ImageRequestSizeTest` added.

### T4 - Warm ExoPlayer Pool for Live Streams
- **2026-04-19** - `PlayerPool` holds up to 2 pre-warmed `ExoPlayer` instances. RizzoApp pre-warms 1 instance on `onCreate`. VOD always gets a fresh player. 5-minute idle eviction via `Handler`.

### T5 - Speculative Stream Resolution on Detail Screen
- **2026-04-19** - `MainViewModel.prefetchStream()` kicks off Torrentio stream resolution when detail screen composes. `ConcurrentHashMap<String, Deferred<Result<String>>>` with 60s TTL deduplicates in-flight requests. `SpeculativeStreamTest` (3 tests) added.

### T6 - stale-while-revalidate Cache Policy for TMDB
- **2026-04-19** - OkHttp `addNetworkInterceptor` stamps TMDB responses with per-route `Cache-Control`. Genres: 7d max-age + 7d SWR. Discover: 1h + 1d. Details: 1d + 7d. Search: 10m + 1h. `TmdbCacheHeadersTest` added.

### T7 - kotlinx.serialization Migration
- **2026-04-19** - Replaced Gson reflection with KSP-generated `kotlinx-serialization-json` parsers. All data models migrated to `@Serializable`/`@SerialName`. `Json` decoder configured with `ignoreUnknownKeys`. `SerializationRoundTripTest` added.

### T8 - Baseline Profile Module + Macrobenchmark Harness
- **2026-04-19** - New `:baselineprofile` Gradle module with `BaselineProfileGenerator` (profile generation journey) and `StartupBenchmark` (cold start timing). `baseline-prof.txt` placeholder committed. Documented in `DECISIONS.md`.
