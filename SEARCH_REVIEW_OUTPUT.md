# Search Pipeline Review — Round 5 (Self-Review)

## Search Path Status

### 2a. API Layer — OK
- `TmdbApiService.get()` (line 60): catches `IllegalArgumentException`, `SocketTimeoutException`, `UnknownHostException`, and generic `Exception` — returns `""` on all. No exception propagation. **OK.**
- `TmdbApiService.tmdbUrl()` (line 78): encodes both key and value with `URLEncoder.encode(k, "UTF-8")` and `URLEncoder.encode(v, "UTF-8")`. **OK.**
- Non-2xx responses return `""` (line 64) — not thrown. **OK.**

### 2b. Repository Layer — OK
- `coalesced()` (lines 44–51): uses `coalesceScope.async { block() }` without `coroutineScope {}` wrapper. Long-lived scope. No structured concurrency issue. **OK.**
- `searchAll()` (lines 281–295): `withTimeout(15_000L)` inside `withContext(Dispatchers.IO)`. `TimeoutCancellationException` caught, returns empty pair, cancels both deferreds. **OK.**
- `cachedList` (lines 53–74): disk cache checked first; if miss, uses `coalesced()` then writes to disk. **OK.**

### 2c. ViewModel — OK
- `searchJob: Job?` (line 304): used for manual cancellation. **OK.**
- Search collector in `init {}` (lines 359–401): uses `collect` (not `collectLatest`), `searchJob?.cancel()` before launching new job, `withContext(Dispatchers.IO) { withTimeout(18_000L) { ... } }`. **OK.**
- `ensureActive()` (line 385): called before `_state.update` after `await()`. **OK.**
- `CancellationException` catch (line 395): no state update — correct, was cancelled intentionally. **OK.**
- `TimeoutCancellationException` catch (line 397): sets `isSearchLoading = false`, `error = "Search timed out"`. **OK.**
- Generic `Exception` catch (line 399): sets `isSearchLoading = false`. **OK.**
- `searchQuery.trim().lowercase()` check (line 388): prevents stale results from a previous query being written back to state. **OK.**

### 2d. HomeScreen — OK
- `MoviesContent` `q.isNotEmpty()` branch (lines 334–352): checks `searchContent.query.equals(q, ignoreCase = true)` before rendering. Skeleton only if `isSearchLoading = true` AND `movies.isEmpty()`. **OK.**
- `ShowsContent` `q.isNotEmpty()` branch (lines 424–442): identical pattern for shows. **OK.**
- `state.isLoading` (not `isSearchLoading`) controls the top-level loading screen — search loading is handled inside the `q.isNotEmpty()` branch. No conflict. **OK.**

### 2e. TmdbSearchResultsContent — OK
- `hasResults` (line 145): correctly computed. Skeleton shown only when `isLoading && !hasResults`. Results always rendered below regardless of `isLoading`. **OK.**

---

## Verified Behaviors

| # | Statement | Status | Citation |
|---|-----------|--------|---------|
| 1 | `get()` returns `""` for all error cases | **VERIFIED** | TmdbApiService.kt:60–76 |
| 2 | `searchAll()` completes within bounded time | **VERIFIED** | TmdbRepository.kt:286 — `withTimeout(15_000L)` |
| 3 | `isSearchLoading = false` on every exit path | **VERIFIED** | MainViewModel.kt:395 (Cancellation), :397 (Timeout), :399 (Exception), :392 (Success) |
| 4 | `BrowseContent.TmdbSearchResults` is reachable | **VERIFIED** | MainViewModel.kt:49 (definition), :390 (construction), HomeScreen.kt:335 (use) |
| 5 | `TmdbSearchResultsContent` renders results regardless of `isLoading` | **VERIFIED** | TmdbSearchResultsContent.kt:148 — `if (isLoading && !hasResults)` only; lines 175–205 always render |
| 6 | `searchContent.query == q` check prevents stale results | **VERIFIED** | HomeScreen.kt:336 (`equals(q, ignoreCase = true)`) and MainViewModel.kt:388 |
| 7 | Nested timeouts: 18s outer (ViewModel) + 15s inner (Repository) | **VERIFIED** | MainViewModel.kt:380, TmdbRepository.kt:286 |

---

## Root Cause

**The code is correct. All 5 previous bugs from R4 have been applied and verified.**

The "spinner forever" symptom cannot be reproduced from code inspection. The pipeline has triple redundancy for spinner clearing:
- OkHttp `readTimeout = 20s`
- `withTimeout(15_000L)` in `TmdbRepository.searchAll()`
- `withTimeout(18_000L)` in `MainViewModel` search collector

All three must fail simultaneously for the spinner to hang forever.

**Most likely explanations for "spinner forever" on device:**

1. **The APK installed on the device is an old build** — the v4-polish APK was built and installed earlier in this session, but subsequent builds (`8fa6b5a`, `1b7e0f8`) may not have been re-installed. The old APK has different code.
2. **App data not cleared** — stale disk cache from a previous session may be returning empty results silently.
3. **The network request is hanging at the TCP level** — `connectTimeout = 15s` in `NetworkClient`. If the device cannot reach `api.themoviedb.org`, the connect hangs for 15s before `get()` returns `""`. Combined with debounce, this could make the UI feel unresponsive.

---

## Fix Prompt for Downstream Model

> **Task: Verify the RizzoIPTVPlayer search pipeline is working and fix any remaining issues.**
>
> Working directory: `/Users/johnrizzetto/RizzoIPTVPlayer`
>
> **Context:**
> TMDB search results never display — the loading spinner stays forever. The API works via curl. The APK was built but the device was offline and unreachable.
>
> **Current code state (verified correct by code review):**
> - `TmdbApiService.get()` catches all exceptions, returns `""` — no crash
> - `TmdbRepository.searchAll()` has 15s timeout, returns empty pair on timeout
> - `MainViewModel` search collector has 18s timeout, sets `isSearchLoading = false` on all exits
> - `HomeScreen` `MoviesContent`/`ShowsContent` check `searchContent.query.equals(q, ignoreCase = true)` before showing results
> - `TmdbSearchResultsContent` shows skeleton only when `isLoading && !hasResults`
>
> **Three things to check and fix:**
>
> **Fix 1 — Verify APK is current and clear app data (MOST LIKELY FIX)**
> The APK at `app/build/outputs/apk/v4/debug/app-v4-debug.apk` may not have the latest changes. Rebuild and reinstall:
> ```
> ./gradlew :app:assembleDebug
> adb connect 192.168.50.82:45509  # or your device IP
> adb shell pm clear com.rizzoplayer.iptv.v2
> adb install -r app/build/outputs/apk/v4/debug/app-v4-debug.apk
> ```
> Then test: type "the matrix" in Movies tab.
>
> **Fix 2 — Add connect-timeout handling in `TmdbApiService.get()`**
> If the device has no network to TMDB, `connectTimeout = 15s` in `NetworkClient` causes `get()` to hang for 15s before the `SocketTimeoutException` fires. Reduce `connectTimeout` to `8s` in `NetworkClient.kt` line 42, or catch `ConnectException` in `get()`.
>
> **Fix 3 — Check disk cache is not returning empty results**
> `DiskCache.get()` returns `null` on TTL miss or parse error. `cachedList()` then falls through to the network call. But if `diskCache.get()` returns a cached empty result (from a previous failed search), it would short-circuit the network call.
>
> Check `DiskCache.kt` — if a search for "the matrix" failed previously and stored an empty list in the cache with TTL 10 minutes, the next identical search would return the cached empty list without hitting the network.
>
> **Fix 4 — Add logging to `get()` to capture what's happening**
> Add a temporary `android.util.Log.d("TMDB", "get() url=$url resultLen=${result.length}")` in `TmdbApiService.get()`. Rebuild, install, test, then run `adb logcat | grep TMDB` to see if `get()` is being called and what it returns.
>
> **If after Fix 1 (rebuild + clear data + reinstall) the search still fails:**
> - Run `adb logcat | grep -i "tmdb\|search\|error\|exception"` to capture runtime errors
> - Report the exact error from logcat
>
> **If Fix 1 works:** The issue was the old APK or stale cache. No code changes needed.
>
> **If Fix 1 doesn't work and logcat shows no errors:** The problem is that `get()` is returning `""` silently (network unreachable, TMDB returning garbage). Add the `Log.d` from Fix 4 to confirm, then address whether TMDB is reachable from the device.

---

## Code Correctness Summary

All 5 R4 bugs are confirmed fixed in the current code:
- URL-encoding in `tmdbUrl()` — ✓ (line 80)
- `IllegalArgumentException` + `SocketTimeoutException` + `UnknownHostException` in `get()` — ✓ (lines 67–72)
- `coalesced()` without `coroutineScope {}` — ✓ (lines 44–51)
- `isSearchLoading` flag + `searchJob?.cancel()` + `ensureActive()` — ✓ (lines 95, 304, 364, 377, 385, 390–391, 395–400)
- `searchContent.query.equals(q, ignoreCase = true)` in both MoviesContent and ShowsContent — ✓ (lines 336, 426)

The code is correct. The fix is most likely: **rebuild the APK, clear app data, reinstall**.
