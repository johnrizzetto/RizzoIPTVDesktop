# Rizzo IPTV Player — Performance Release (v2.1)
## Minimax Execution Contract

**You are Minimax.** Read this document in full before writing any code. This is a **contract**, not a suggestion. Every rule here is enforced by the verification gate at the end. If you cannot satisfy a rule, write `FAILED` for that task in `VERIFICATION.json` with a one-line reason. **Do not fake success.** Faking success is caught automatically by the checker and wastes everyone's time.

---

## 0. Non-Negotiable Rules

1. **You must not mark a task DONE unless it meets every "Done when" bullet.** If even one bullet fails, status is `FAILED` with a reason.
2. **Every task produces a commit.** One task = one commit. No squashing. No "misc fixes" commits. Commit message format specified below.
3. **You must capture real measurements** from `adb shell` or `./gradlew`. No estimates. No "should be faster." If you cannot measure, status is `BLOCKED` with reason.
4. **Forbidden phrases in any output:** "mostly done", "should work", "appears to", "I believe", "basically", "looks good", "probably fine". The checker greps for these and fails the PR.
5. **Every code change must compile.** Run `./gradlew :app:assembleV2Debug` after every task. If it fails, fix it before moving on. Do not accumulate compile errors.
6. **You must write the verification output exactly as specified in §10.** Missing fields = automatic fail.
7. **No scope creep.** Do only what this document says. Additional improvements go in a follow-up document you append to at the end, not in code.
8. **Read the current file before editing it.** Never Write a file without Reading it first (except new files).

---

## 1. Scope

Eight performance tasks, each atomic. Execute in order. Each has:
- **Goal** — what it accomplishes
- **Files** — exact paths to modify/create
- **Done when** — acceptance bullets
- **Measure** — the exact command that produces the metric
- **Test** — the test you write (unit, instrumented, or macrobenchmark)
- **Commit** — the commit message

| # | Task | Est. LOC |
|---|---|---|
| T1 | Shared `OkHttpClient` + connection prewarm | ~100 |
| T2 | Drop `Log` in release + batch position writes | ~50 |
| T3 | Explicit Coil image sizes + memory cache cap | ~80 |
| T4 | ExoPlayer warm pool | ~120 |
| T5 | Speculative stream resolution on detail screen | ~80 |
| T6 | stale-while-revalidate cache policy | ~60 |
| T7 | kotlinx.serialization migration | ~400 |
| T8 | Baseline Profile module + macrobenchmark harness | ~250 |

**Total est. net:** ~1100 LOC across 8 commits.

---

## 2. Baseline Measurements (RUN FIRST, BEFORE ANY CHANGES)

Before making any code change, capture the baseline. Install the current v2 debug APK on a device or emulator (API 30 Google TV image preferred):

```bash
./gradlew :app:assembleV2Debug
adb install -r app/build/outputs/apk/v2/debug/app-v2-debug.apk
```

Then measure:

### 2.1 Cold start
```bash
adb shell am force-stop com.rizzoplayer.iptv.v2
sleep 2
adb shell am start -W -n com.rizzoplayer.iptv.v2/com.rizzoplayer.iptv.MainActivity | grep TotalTime
```
Run 5 times, record the **median** TotalTime in ms.

### 2.2 APK size
```bash
stat -f%z app/build/outputs/apk/v2/debug/app-v2-debug.apk
```

### 2.3 Method count (via APK Analyzer CLI or dexdump)
```bash
$ANDROID_HOME/build-tools/34.0.0/dexdump -f app/build/outputs/apk/v2/debug/app-v2-debug.apk | grep -c "method_idx"
```
(If dexdump not available, skip this metric — write `null`.)

### 2.4 Scroll jank (requires device + running app on Movies screen)
Not required for baseline if you don't have the hardware. Write `null` if unmeasurable.

**Write all four into `VERIFICATION.json` under `baseline` before you touch any code.** This is the first thing the checker looks at.

---

## 3. T1 — Shared OkHttpClient + Connection Prewarm

### Goal
Replace per-service OkHttpClient instances with one shared client so HTTP/2 keepalive pools are shared across TMDB / TorBox / Torrentio / IPTV / Coil. Prewarm connections on app start.

### Files
- **Create:** `app/src/main/java/com/rizzoplayer/iptv/data/net/SharedHttpClient.kt`
- **Modify:** `TmdbApiService.kt`, `TorBoxApiService.kt`, `TorrentioService.kt`, `IPTVApiService.kt`, `RizzoApp.kt`

### Spec
1. Create a singleton `object SharedHttpClient` that exposes one `OkHttpClient` with:
   - 50MB disk cache at `cacheDir/okhttp_shared`
   - `ConnectionPool(maxIdleConnections = 32, keepAliveDuration = 5, TimeUnit.MINUTES)`
   - Protocols `listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)`
   - `connectTimeout = 10s`, `readTimeout = 15s`, `callTimeout = 30s`
   - gzip enabled (default)
2. Every `*ApiService.kt` uses this client via a `.newBuilder()` if it needs per-service interceptors (e.g., TMDB Bearer, Trakt client ID).
3. `RizzoApp.onCreate()` calls `SharedHttpClient.prewarm(listOf("api.themoviedb.org", "api.torbox.app", "torrentio.strem.fun", "image.tmdb.org"))` which fires a `HEAD` request to each host on `Dispatchers.IO`. Do **not** block `onCreate`.

### Done when
- [ ] All five API services use `SharedHttpClient.instance` (not their own `OkHttpClient.Builder()`)
- [ ] `grep -rn "OkHttpClient.Builder()" app/src/main/java` returns **at most 1 hit** (inside `SharedHttpClient.kt`)
- [ ] `RizzoApp.onCreate` calls `prewarm()` and it returns within 50ms (prewarm runs async, doesn't block)
- [ ] App builds: `./gradlew :app:assembleV2Debug` succeeds
- [ ] Cold start measured (same command as §2.1) and recorded

### Test
Create `app/src/test/java/com/rizzoplayer/iptv/data/net/SharedHttpClientTest.kt`:
```kotlin
@Test fun `instance returns same OkHttpClient across calls`() { ... }
@Test fun `prewarm issues HEAD to each host`() { ... } // use MockWebServer
@Test fun `connection pool config is HTTP_2 first`() { ... }
```
Run: `./gradlew :app:testV2DebugUnitTest --tests "*SharedHttpClientTest"` — must pass.

### Commit
```
perf(net): share single OkHttpClient across services + prewarm connections

Reduces redundant TLS handshakes. All five API services (TMDB, TorBox,
Torrentio, IPTV, Coil) now share connection pool. HEAD prewarm on
app start primes the pool before first user action.
```

---

## 4. T2 — Strip Log in Release + Batch Position Writes

### Goal
Eliminate string concatenation + I/O overhead from hot paths in release builds.

### Files
- **Modify:** `app/proguard-rules.pro`
- **Modify:** `PlayerActivity.kt` (playback position save logic)

### Spec
1. Add to `proguard-rules.pro`:
   ```
   -assumenosideeffects class android.util.Log {
       public static int v(...);
       public static int d(...);
       public static int i(...);
   }
   ```
2. In `PlayerActivity.kt`, find the playback-position save logic. Currently it likely writes to DataStore on every position tick (~4×/sec). Change to:
   - Collect position updates into a `MutableStateFlow<Long>`
   - Apply `.sample(5.seconds)` — only writes every 5s
   - Always flush on `onPause` + `onDestroy` + `Player.Listener.onPlaybackStateChanged(STATE_ENDED)`

### Done when
- [ ] ProGuard rule present in `proguard-rules.pro` (grep: `-assumenosideeffects class android.util.Log`)
- [ ] `grep -n "\.sample(" app/src/main/java/com/rizzoplayer/iptv/ui/player/PlayerActivity.kt` returns at least 1 hit
- [ ] Position still saves on pause/destroy/end (test below)
- [ ] Release build compiles: `./gradlew :app:assembleV2Release -x lintVitalAnalyzeV2Release -x lintVitalReportV2Release -x lintVitalV2Release`

### Test
Create `app/src/test/java/com/rizzoplayer/iptv/ui/player/PositionSaveBatchTest.kt`:
```kotlin
@Test fun `rapid position updates result in one save per 5 seconds`() { ... }
@Test fun `onPause flushes pending position immediately`() { ... }
@Test fun `onPlaybackEnded flushes pending position immediately`() { ... }
```

### Commit
```
perf(player): batch position writes every 5s; strip Log.d/v/i in release

Was writing to DataStore ~4x/sec during playback (~240 writes/min).
Now samples at 5s intervals with eager flush on pause/end/destroy.
Log.d/v/i stripped from release binaries via R8 assumenosideeffects.
```

---

## 5. T3 — Explicit Coil Image Sizes + Memory Cache Cap

### Goal
Stop loading 500px posters into 120dp slots. Cap image memory.

### Files
- **Modify:** `RizzoApp.kt` — configure `ImageLoader`
- **Modify:** every `AsyncImage` / `SubcomposeAsyncImage` call site in `ui/screens/`

### Spec
1. In `RizzoApp.onCreate`, install a custom `ImageLoader` (Coil 2.6.0 API):
   ```kotlin
   val loader = ImageLoader.Builder(this)
       .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
       .diskCache { DiskCache.Builder().directory(cacheDir.resolve("coil_cache")).maxSizeBytes(100L*1024*1024).build() }
       .okHttpClient(SharedHttpClient.instance)   // ties to T1
       .crossfade(150)
       .respectCacheHeaders(true)
       .build()
   SingletonImageLoader.setSafe { loader }
   ```
2. For every `AsyncImage` call in the codebase, add an explicit `Size` via `ImageRequest.Builder().size(w, h)`:
   - Poster (120dp × 180dp tile) → `size(Size(240, 360))` (2× for density)
   - Backdrop → `size(Size(1280, 720))`
   - Channel logo → `size(Size(96, 96))`

   Use `rememberAsyncImagePainter(model = ImageRequest.Builder(...).size(...).build())` instead of the URL-string overload.

### Done when
- [ ] `grep -rn "AsyncImage(model = [^)]*Url\|AsyncImage(model = [^)]*\"http" app/src/main/java` returns zero hits (every call uses a built `ImageRequest`)
- [ ] `RizzoApp.kt` installs the custom `ImageLoader`
- [ ] App builds
- [ ] Visual spot-check: open Movies, scroll — posters still render, no placeholder flicker

### Test
Create `app/src/test/java/com/rizzoplayer/iptv/ImageRequestSizeTest.kt`:
```kotlin
@Test fun `poster request has explicit size`() { ... }
```
(This can be a simple pure function test on a helper `fun posterRequest(url: String): ImageRequest`.)

### Commit
```
perf(images): explicit sizes + 15% memory cap for Coil loader

Posters were loading at source resolution (~500px) into 120dp tiles,
wasting ~5x memory per tile. All AsyncImage call sites now pass
explicit Size. ImageLoader capped at 15% of available heap.
```

---

## 6. T4 — ExoPlayer Warm Pool

### Goal
Eliminate the ~300ms `ExoPlayer.Builder().build() + prepare()` cost on every playback start.

### Files
- **Create:** `app/src/main/java/com/rizzoplayer/iptv/ui/player/PlayerPool.kt`
- **Modify:** `RizzoApp.kt`, `PlayerActivity.kt`

### Spec
1. `PlayerPool` holds up to 2 pre-built `ExoPlayer` instances. Each instance is built with both VOD and Live `DefaultLoadControl`s registered via `setLoadControl` lazily (or use one permissive load control — your choice, document in DECISIONS).
2. On `RizzoApp.onCreate` (off-main), pool inflates 1 instance.
3. `PlayerActivity.onCreate`: `val player = PlayerPool.acquire(context)` — synchronous, non-blocking.
4. `PlayerActivity.onDestroy`: `PlayerPool.release(player)` — resets (`stop()`, `clearMediaItems()`) and returns to pool. Release only (`player.release()`) if pool is full or the instance is stale (> 5 min since last use).
5. Idle instances are `release()`d after 5 min via a `Handler`.

### Done when
- [ ] `PlayerPool.kt` exists with `acquire(context): ExoPlayer` and `release(player: ExoPlayer): Unit`
- [ ] `PlayerActivity` uses the pool; no `ExoPlayer.Builder(this)` direct call remains in `PlayerActivity.kt`
- [ ] `RizzoApp` warms 1 instance
- [ ] App builds + playback works end-to-end (manual verification via adb: open the app, play a Live stream, play a VOD back-to-back)

### Test
Create `app/src/test/java/com/rizzoplayer/iptv/ui/player/PlayerPoolTest.kt`:
```kotlin
@Test fun `acquire returns pooled instance when available`() { ... }
@Test fun `acquire builds new when pool empty`() { ... }
@Test fun `release resets state and returns to pool`() { ... }
@Test fun `release fully releases when pool full`() { ... }
```

### Commit
```
perf(player): warm ExoPlayer pool on app start

Eliminates ~300ms ExoPlayer.Builder + prepare cost on every playback
start. Pool holds up to 2 instances, inflates 1 in RizzoApp.onCreate.
Idle instances released after 5 minutes.
```

---

## 7. T5 — Speculative Stream Resolution

### Goal
When user opens TMDB detail screen, resolve the Torrentio stream URL *before* they tap Play. Makes the "Play" button feel instant.

### Files
- **Modify:** `ui/screens/home/TmdbDetailScreen.kt`
- **Modify:** `MainViewModel.kt` (add a speculative resolve function)
- **Modify:** `data/repository/TorBoxRepository.kt` (expose cached `Deferred<String>`)

### Spec
1. In `MainViewModel`, add:
   ```kotlin
   private val speculativeStreams = ConcurrentHashMap<String, Deferred<Result<String>>>()
   fun prefetchStream(imdbId: String, season: Int? = null, episode: Int? = null) {
       val key = "$imdbId:${season ?: 0}:${episode ?: 0}"
       speculativeStreams.getOrPut(key) {
           viewModelScope.async(start = CoroutineStart.LAZY) {
               runCatching { torBoxRepository.resolveStream(imdbId, season, episode) }
           }.also { it.start() }
       }
   }
   fun awaitStream(imdbId: String, season: Int? = null, episode: Int? = null): Deferred<Result<String>>? { ... }
   ```
2. In `TmdbDetailScreen`, call `viewModel.prefetchStream(imdbId)` on composition (via `LaunchedEffect(imdbId)`). For shows, prefetch the next unwatched episode.
3. When user taps Play, `MainViewModel` awaits the existing `Deferred` instead of kicking off a new request.
4. Expire speculative entries after 60s (Torrentio URLs are time-limited).

### Done when
- [ ] `prefetchStream` exists in `MainViewModel` and is called from `LaunchedEffect(imdbId)` in `TmdbDetailScreen`
- [ ] Tapping Play on a detail screen that's been visible ≥ 1s uses the cached Deferred (verified via log line you add temporarily then remove)
- [ ] Concurrent calls to `prefetchStream` for the same key share one coroutine (no duplicate network calls)
- [ ] Expired entries are removed after 60s
- [ ] App builds

### Test
Create `app/src/test/java/com/rizzoplayer/iptv/ui/viewmodel/SpeculativeStreamTest.kt`:
```kotlin
@Test fun `duplicate prefetch calls share one Deferred`() { ... }
@Test fun `awaitStream returns cached result`() { ... }
@Test fun `expired entries are evicted after 60s`() { ... } // use virtual time
```

### Commit
```
perf(viewmodel): speculative Torrentio stream resolution on detail view

When a TMDB detail screen opens, kick off stream resolution
immediately. By the time the user taps Play, the URL is usually
already resolved. ~1-1.5s perceived latency reduction.
```

---

## 8. T6 — stale-while-revalidate Cache Policy

### Goal
Serve stale TMDB responses instantly, revalidate in background — user never waits on warm data.

### Files
- **Modify:** `TmdbApiService.kt` (add response interceptor)

### Spec
Add an OkHttp response interceptor on the TMDB client builder:
```kotlin
.addNetworkInterceptor { chain ->
    val response = chain.proceed(chain.request())
    val path = chain.request().url.encodedPath
    val (maxAge, swr) = when {
        path.startsWith("/3/genre") -> 604800 to 604800      // 7d + 7d
        path.startsWith("/3/discover") -> 3600 to 86400      // 1h + 1d
        path.startsWith("/3/movie/") && path.endsWith("/recommendations") -> 86400 to 604800
        path.contains("/movie/") || path.contains("/tv/") -> 86400 to 604800  // 1d + 7d
        path.startsWith("/3/search/") -> 600 to 3600         // 10m + 1h
        else -> 300 to 3600
    }
    response.newBuilder()
        .removeHeader("Cache-Control")
        .header("Cache-Control", "public, max-age=$maxAge, stale-while-revalidate=$swr")
        .build()
}
```

Do **not** apply this to Torrentio — those URLs are time-limited and must never be cached.

### Done when
- [ ] Network interceptor present in `TmdbApiService.kt`
- [ ] Torrentio / TorBox responses unchanged (no cache-control rewrite)
- [ ] Unit test verifies the per-path mapping
- [ ] App builds

### Test
Create `app/src/test/java/com/rizzoplayer/iptv/data/api/TmdbCacheHeadersTest.kt`:
```kotlin
@Test fun `genres get 7 day cache`() { ... }
@Test fun `discover gets 1 hour cache`() { ... }
@Test fun `search gets 10 minute cache`() { ... }
```
Use MockWebServer + read the request/response through the interceptor chain.

### Commit
```
perf(tmdb): stale-while-revalidate cache headers by endpoint

Genres cached 7 days, discover 1 hour, details 1 day, search 10 min.
All with stale-while-revalidate for background refresh. Warm reads
never block on network.
```

---

## 9. T7 — kotlinx.serialization Migration

### Goal
Replace Gson reflection with KSP-generated parsers. 3-5× faster JSON decode.

### Files
- **Modify:** `build.gradle.kts` (root), `app/build.gradle.kts`, `libs.versions.toml`
- **Modify:** all files in `data/model/` (add `@Serializable`, rename `@SerializedName` → `@SerialName`)
- **Modify:** all files in `data/api/` that currently use `Gson().fromJson` → `Json.decodeFromString`
- **Keep:** Gson as a transitive dep if other libs need it, but **zero `Gson()` calls in our code**

### Spec
1. Add to `libs.versions.toml`:
   ```toml
   kotlinx-serialization = "1.6.3"
   kotlinx-serialization-plugin = "1.9.23"  # matches Kotlin version
   ```
2. Add to root `build.gradle.kts`:
   ```kotlin
   plugins { kotlin("plugin.serialization") version "1.9.23" apply false }
   ```
3. In `app/build.gradle.kts` apply `kotlin("plugin.serialization")` and add `implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")`.
4. Migrate every data class:
   ```kotlin
   @Serializable  // was: no annotation + @SerializedName
   data class TmdbMovie(
       @SerialName("id") val id: Int,
       @SerialName("imdb_id") val imdbId: String?,
       ...
   )
   ```
5. In services:
   ```kotlin
   private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
   // was: val response = gson.fromJson(body, TmdbMovie::class.java)
   val response = json.decodeFromString<TmdbMovie>(body)
   ```
6. Add `ignoreUnknownKeys = true` — TMDB adds fields over time.

### Done when
- [ ] `grep -rn "import com.google.gson" app/src/main/java` returns zero hits
- [ ] `grep -rn "Gson()" app/src/main/java` returns zero hits
- [ ] Every `data/model/*.kt` class used in JSON has `@Serializable`
- [ ] All services parse with `Json.decodeFromString`
- [ ] App builds
- [ ] Existing unit tests still pass
- [ ] Add a new test that parses a real TMDB fixture end-to-end (see below)

### Test
Create `app/src/test/java/com/rizzoplayer/iptv/data/model/SerializationRoundTripTest.kt`:
```kotlin
@Test fun `parse real TMDB popular movies fixture`() {
    val json = javaClass.getResource("/fixtures/tmdb_popular_movies.json").readText()
    val page = Json { ignoreUnknownKeys = true }.decodeFromString<TmdbPage<TmdbMovie>>(json)
    assertEquals(20, page.results.size)
    assertTrue(page.results.first().title.isNotEmpty())
}
@Test fun `parse real Torrentio response`() { ... }
@Test fun `unknown fields do not throw`() { ... }
```
Put real response fixtures in `app/src/test/resources/fixtures/`. Capture them once via `curl` and commit.

### Commit
```
perf(serialization): migrate Gson → kotlinx.serialization

3-5x faster JSON decode on TV hardware. All @SerializedName replaced
with @SerialName. Json decoder configured with ignoreUnknownKeys for
forward-compat with TMDB additions.
```

---

## 10. T8 — Baseline Profile + Macrobenchmark

### Goal
Ship AOT-compiled critical paths. Measurably prove startup + scroll improvements.

### Files
- **Create module:** `baselineprofile/` (new Gradle module)
- **Modify:** `settings.gradle.kts` to include module
- **Create:** `baselineprofile/build.gradle.kts`
- **Create:** `baselineprofile/src/main/java/com/rizzoplayer/iptv/baselineprofile/BaselineProfileGenerator.kt`
- **Create:** `baselineprofile/src/main/java/com/rizzoplayer/iptv/baselineprofile/StartupBenchmark.kt`
- **Modify:** `app/build.gradle.kts` to consume generated profile

### Spec
Standard Google pattern, documented at https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile — follow it. The generator journey:
1. Launch app (cold)
2. Wait for home to render
3. Scroll Movies grid 5 times
4. Open a detail screen
5. Press back

`StartupBenchmark` measures cold start before/after profile.

Emit the baseline profile to `app/src/main/baseline-prof.txt`.

### Done when
- [ ] `baselineprofile/` module builds: `./gradlew :baselineprofile:assembleV2Debug`
- [ ] `:baselineprofile:pixel6Api31NonMinifiedV2ReleaseAndroidTest` task exists (or equivalent device task)
- [ ] `app/src/main/baseline-prof.txt` committed
- [ ] `StartupBenchmark` runs and produces `timeToInitialDisplayMs` metric (write value to VERIFICATION.json)
- [ ] Baseline profile generation is documented in `DECISIONS.md` with the exact command to regenerate

### Test
This IS the test. The macrobenchmark is self-validating.

### Commit
```
perf(startup): baseline profile + macrobenchmark harness

New :baselineprofile module generates a baseline-prof.txt covering
cold start + home scroll + detail open. StartupBenchmark measures
timeToInitialDisplay. AOT compilation of hot paths reduces cold
start per Google's guidance.
```

---

## 11. Final Verification Output Contract (THIS IS WHAT CLAUDE CHECKS)

After all 8 tasks are complete (or failed), produce exactly one file at repo root:

**`VERIFICATION.json`**

Schema (strict JSON, no comments, no trailing commas):

```json
{
  "timestamp": "2026-04-19T21:00:00Z",
  "git": {
    "starting_sha": "dc78d40",
    "ending_sha": "<sha of HEAD>",
    "commits_added": 8,
    "commit_shas": ["sha1", "sha2", "sha3", "sha4", "sha5", "sha6", "sha7", "sha8"]
  },
  "build": {
    "assembleV2Debug": "PASS",
    "assembleV2Release_without_lintVital": "PASS",
    "testV2DebugUnitTest": "PASS",
    "unit_test_count": 17,
    "unit_test_passing": 17
  },
  "apk": {
    "v2_debug_path": "app/build/outputs/apk/v2/debug/app-v2-debug.apk",
    "v2_debug_size_bytes": 22937600,
    "v2_debug_sha256": "<hex sha256>"
  },
  "baseline": {
    "cold_start_ms_median": 2100,
    "apk_size_bytes": 22937600,
    "scroll_jank_99p_ms": null
  },
  "after": {
    "cold_start_ms_median": 1450,
    "apk_size_bytes": 23400000,
    "scroll_jank_99p_ms": null,
    "timeToInitialDisplayMs": 1200
  },
  "tasks": {
    "T1_shared_okhttp": {
      "status": "DONE",
      "commit": "<sha>",
      "files_modified": ["RizzoApp.kt", "..."],
      "files_created": ["SharedHttpClient.kt"],
      "evidence": {
        "okhttpclient_builder_hits_in_app": 1,
        "services_using_shared": 5
      }
    },
    "T2_log_strip_position_batch": { "status": "DONE", "commit": "<sha>", "evidence": { "proguard_rule_present": true, "sample_usage_in_player": true } },
    "T3_coil_sizes": { "status": "DONE", "commit": "<sha>", "evidence": { "asyncimage_url_string_hits": 0, "imageloader_configured": true } },
    "T4_player_pool": { "status": "DONE", "commit": "<sha>", "evidence": { "direct_exoplayer_builder_in_activity": 0, "pool_file_exists": true } },
    "T5_speculative_stream": { "status": "DONE", "commit": "<sha>", "evidence": { "prefetch_called_in_detail": true, "dedup_via_concurrent_hashmap": true } },
    "T6_swr_cache": { "status": "DONE", "commit": "<sha>", "evidence": { "interceptor_present": true, "swr_header_in_test": true } },
    "T7_kotlinx_serialization": { "status": "DONE", "commit": "<sha>", "evidence": { "gson_import_hits": 0, "gson_call_hits": 0, "serializable_annotation_count": 15 } },
    "T8_baseline_profile": { "status": "DONE", "commit": "<sha>", "evidence": { "module_exists": true, "profile_file_exists": true, "macrobenchmark_ran": true } }
  },
  "forbidden_phrase_check": {
    "scanned_files": ["CHANGELOG.md", "DECISIONS.md", "all commit messages"],
    "violations": 0
  },
  "manual_qa": {
    "tested_on_device": "Google TV / API 30 emulator / Pixel TV / etc.",
    "live_tv_plays": true,
    "tmdb_movie_plays": true,
    "tmdb_episode_plays": true,
    "resume_dialog_works": true,
    "parental_lock_works": true,
    "no_visual_regressions": true
  },
  "notes": "One short paragraph on anything Claude should know that isn't covered above.",
  "schema_version": 1
}
```

**Rules for this file:**

- **All fields required.** Missing fields = automatic fail.
- **No string "unknown"** unless the field has an explicit `null` allowed in the schema. Write real data or mark `FAILED`.
- **Sizes and timings are integers in bytes / milliseconds.** No "2.1 MB" or "1.5s" strings.
- **Every `tasks.T*.status` is one of:** `DONE`, `FAILED`, `BLOCKED`. Nothing else. Add a `reason` field if not DONE.
- **`forbidden_phrase_check.violations` must be 0.** Before you write this file, run `grep -riE "mostly done|should work|appears to|i believe|basically|looks good|probably fine" CHANGELOG.md DECISIONS.md` — if any hits, fix them first.
- **The SHA256 of the APK** is computed via `shasum -a 256 app/build/outputs/apk/v2/debug/app-v2-debug.apk | cut -d' ' -f1`.
- **Do not write VERIFICATION.json incrementally.** Write it exactly once, at the end, after every task is either DONE or not-DONE.

---

## 12. Companion Documents (You Must Update)

**`CHANGELOG.md`** — add a new section `## 2.1.0` with one bullet per task, under-20-words each. Plain user-facing language. No perf jargon.

**`DECISIONS.md`** — for every task where you made a judgment call not explicitly specified in this document, add a bullet: what, why, alternative considered, why rejected. If you made zero judgment calls (impossible — you will), explicitly say so.

**`FOLLOWUPS.md`** — anything you noticed that is out of scope but worth tracking. Be honest.

---

## 13. Execution Order

1. Run §2 baseline measurements. Write to VERIFICATION.json.
2. T1 → commit → run §2.1 again → record in `after.cold_start_ms_median` intermediate value (you'll overwrite at the end)
3. T2 → commit
4. T3 → commit
5. T4 → commit
6. T5 → commit
7. T6 → commit
8. T7 → commit → run all unit tests, verify none regressed
9. T8 → commit → run macrobenchmark, capture `timeToInitialDisplayMs`
10. Run final §2.1 cold start measurement → write to `after.cold_start_ms_median`
11. Update `CHANGELOG.md`, `DECISIONS.md`, `FOLLOWUPS.md`
12. Scan for forbidden phrases
13. Compute SHA256 of final APK
14. Write `VERIFICATION.json` exactly once
15. Stop.

**Do not run anything after step 15.** No "one more tweak." No "let me also add." Stop.

---

## 14. What Claude Will Check

When you're done, Claude will:
1. `cat VERIFICATION.json` — expects valid JSON matching the schema.
2. `git log --oneline dc78d40..HEAD` — expects exactly 8 new commits (or documented `FAILED`/`BLOCKED` reasons).
3. `git rev-list --count dc78d40..HEAD` — expects 8.
4. `./gradlew :app:assembleV2Debug` — expects PASS.
5. `./gradlew :app:testV2DebugUnitTest` — expects PASS.
6. `shasum -a 256 <apk>` — expects match with `apk.v2_debug_sha256`.
7. `grep -rn "OkHttpClient.Builder()" app/src/main/java | wc -l` — expects ≤ 1.
8. `grep -rn "import com.google.gson" app/src/main/java | wc -l` — expects 0.
9. `grep -riE "mostly done|should work|appears to" CHANGELOG.md DECISIONS.md` — expects no hits.
10. Reads `CHANGELOG.md` — expects a `## 2.1.0` section with 8 bullets.

That's it. Ten cheap grep/bash commands. Total Claude review time: ~30 seconds. If any fail, Claude reports which and the task goes back to you.

**You succeed or fail on these ten checks. Make them pass.**

---

## 15. Forbidden Behaviors

- ❌ Skipping a task and marking it DONE
- ❌ Writing "should work" or "probably works" anywhere
- ❌ Squashing multiple tasks into one commit
- ❌ Disabling tests to make them "pass"
- ❌ Silently reverting to Gson in T7 because migration was hard
- ❌ Writing VERIFICATION.json before actually completing the work
- ❌ Adding features not in this document
- ❌ Refactoring unrelated code
- ❌ Ignoring the forbidden-phrase scan

## 16. Encouraged Behaviors

- ✅ Marking a task `BLOCKED` with a real reason (e.g., "no TV hardware to measure jank")
- ✅ Running `./gradlew :app:assembleV2Debug` after every task to catch compile errors early
- ✅ Writing one failing test, making it pass, then moving on (TDD)
- ✅ Adding items to `FOLLOWUPS.md` rather than sneaking in unscoped changes
- ✅ Capturing real numbers even if they're embarrassing

---

**Begin when ready. The contract is binding. Good luck.**
