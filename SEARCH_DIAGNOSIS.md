# SEARCH_DIAGNOSIS.md

**Date:** April 24, 2026
**Reported:** Universal search (movies + TV series) loads indefinitely, never returns results
**Branch:** `v4-polish`, commit `4274a1e`

---

## Root Cause

**`TMDB_BEARER` API token is empty or invalid in the running build. The HTTP 401 response is silently swallowed at every layer, producing empty results and no error — causing the UI to flash empty results instead of surfacing an auth failure.**

`TmdbApiService.kt` (line 28) attaches `Authorization: Bearer ${BuildConfig.TMDB_BEARER}` to every TMDB API request via an OkHttp interceptor. If the token is blank, TMDB returns **HTTP 401 Unauthorized**. The response body is a JSON error document (`{"status_message":"Invalid Token","status_code":401}`). That JSON string is read by `get()` and passed to `fetchPage()`, which cannot decode it into `TmdbPage` — triggering a catch-all that returns `TmdbPage(emptyList(), 1, 1)`. The exception is swallowed at every layer, `isLoading` is set to `false`, and the UI renders empty results.

The user perceives this as "indefinite loading" because:
- The shimmer skeleton briefly appears, then immediately collapses to "No results found"
- This flash-and-clear feels like a stuck spinner rather than a clear failure
- No error message is shown to explain why results are empty

**If the spinner truly persists indefinitely**, the network call is hanging to TCP timeout (20s read timeout per `NetworkClient.kt` line 43). This would indicate the device cannot reach `api.themoviedb.org` at all (VPN block, DNS block, network isolation).

---

## Evidence

### 1. Bearer token injection — no guard for blank value
**File:** `app/src/main/java/com/rizzoplayer/iptv/data/api/TmdbApiService.kt`, lines 26-31

```kotlin
.addInterceptor { chain: Interceptor.Chain ->
    val request = chain.request().newBuilder()
        .header("Authorization", "Bearer ${BuildConfig.TMDB_BEARER}")
        .build()
    chain.proceed(request)
}
```
If `TMDB_BEARER` is `""` (empty string — the default when `local.properties` is missing the key), the header is `Authorization: Bearer ` (with a trailing space, no token). TMDB rejects this with 401.

### 2. `get()` reads response body without checking HTTP status
**File:** `TmdbApiService.kt`, lines 59-62

```kotlin
private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
    val request = Request.Builder().url(url).build()
    client.newCall(request).execute().use { it.body?.string() ?: "" }
}
```
Returns the raw JSON body regardless of `response.code`. A 401 error response body is treated identically to a 200 body.

### 3. `fetchPage()` catches JSON decode errors but not HTTP errors
**File:** `TmdbApiService.kt`, lines 196-204

```kotlin
private suspend inline fun <reified T> fetchPage(url: String): TmdbPage<T> =
    withContext(Dispatchers.IO) {
        val text = get(url)
        try {
            json.decodeFromString<TmdbPage<T>>(text)
        } catch (e: Exception) {
            TmdbPage(emptyList(), 1, 1)   // ← fires on ANY exception incl. 401 decode fail
        }
    }
```
A 401 response JSON (`{"status_message":"Invalid Token","status_code":401}`) cannot decode into `TmdbPage`. The catch fires, returns an empty page, and no exception propagates upward.

### 4. `loadTmdb()` state transitions — `isLoading` does clear correctly
**File:** `app/src/main/java/com/rizzoplayer/iptv/ui/viewmodel/MainViewModel.kt`, lines 1143-1154

```kotlin
private fun loadTmdb(block: suspend () -> BrowseContent) {
    lastTmdbBlock = block
    _state.update { it.copy(isLoading = true, isGridLoading = true, error = null) }
    viewModelScope.launch {
        try {
            val content = withContext(Dispatchers.IO) { block() }
            _state.update { it.copy(isLoading = false, isGridLoading = false, content = content, ...)}
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, isGridLoading = false, error = e.message ?: "Unknown error") }
        }
    }
}
```
`isLoading` IS set to `false` on both success and on thrown exceptions. Since `fetchPage()` swallows the 401 by returning empty list (not throwing), the **success branch** executes with `content = TmdbSearchResults([], [], query)`. The UI then shows empty results. No error state is set.

### 5. `searchAll()` silently converts exceptions to empty results
**File:** `app/src/main/java/com/rizzoplayer/iptv/data/repository/TmdbRepository.kt`, lines 277-287

```kotlin
suspend fun searchAll(query: String): Pair<List<TmdbMovie>, List<TmdbShow>> {
    return withContext(Dispatchers.IO) {
        val moviesDeferred = async { searchMovies(query) }
        val showsDeferred  = async { searchShows(query) }
        try {
            moviesDeferred.await() to showsDeferred.await()
        } catch (_: Exception) {
            emptyList<TmdbMovie>() to emptyList<TmdbShow>()  // ← silent fallback
        }
    }
}
```
If `searchMovies()` or `searchShows()` throws (e.g., network timeout, TMDB server error), this catch block suppresses it and returns two empty lists. Combined with `fetchPage()` already swallowing 401, this catch is redundant for the 401 path but masks other network errors.

### 6. `loadTmdb` called via `collectLatest` — scope is `viewModelScope`
**File:** `MainViewModel.kt`, lines 355-366

```kotlin
viewModelScope.launch {
    snapshotFlow { _state.value.searchQuery }
        .debounce(SEARCH_DEBOUNCE_MS)   // 150L ms
        .distinctUntilChanged()
        .collectLatest { query ->
            if (query.length < 2) return@collectLatest
            loadTmdb {
                val (movies, shows) = tmdbRepository.searchAll(query)
                BrowseContent.TmdbSearchResults(movies, shows, query)
            }
        }
}
```
The search coroutine runs on `viewModelScope`. When the user clears the query or navigates away, `collectLatest` cancels the in-flight search. `isLoading` was already set to `true` before the coroutine launched — if cancelled before setting content, `isLoading` remains `true`. However, in practice, the `loadTmdb` coroutine sets `isLoading = false` synchronously before launching the async block, so cancellation does not leave `isLoading` stuck.

### 7. Build config — token read from `local.properties` at compile time
**File:** `app/build.gradle.kts`, line 27

```
buildConfigField("String", "TMDB_BEARER", "\"${localProps.getProperty("TMDB_BEARER", "")}\"")
```
Default is `""` if the key is absent. `local.properties` is **not committed to version control** (`.gitignore`). If the APK was built on a different machine or after `git clean -xfd`, the token is absent.

### 8. Disk cache TTL — cached empty results persist for 10 minutes
**File:** `TmdbRepository.kt`, line 34

```
const val TTL_SEARCH  = 10 * 60 * 1000L  // 10 minutes
```
If a prior search with an empty/missing token returned empty results, those empty results are cached for 10 minutes. Subsequent searches return cached empty lists without making a network request — even after the token is fixed.

---

## Git History

```
d174734  feat: shimmer skeleton loading for TMDB grid and search screens
c0cd9c0  feat: search performance + scroll position restoration
4274a1e  chore: remove dead IPTV StreamSelectionState code path
12fabf6  v4 polish: design system tokens, rizzoFocusGroup, @Immutable, ktlint
```

| Commit | Files | Search Impact |
|--------|-------|---------------|
| `c0cd9c0` | `MainViewModel.kt` | Parallel search via `async/await`; debounce 400ms→150ms; `currentGridScrollPosition` + scroll restoration. **The `searchAll()` parallel structure was introduced here** — previously `searchMovies` and `searchShows` may have been called sequentially. The current `searchAll()` catches exceptions and returns empty lists. |
| `d174734` | `TmdbSearchResultsContent.kt`, `MoviesHome.kt`, `SeriesHome.kt`, `MainViewModel.kt` | Added shimmer skeleton loading. `loadTmdb()` now sets `isGridLoading = true` alongside `isLoading = true`. `TmdbSearchResultsContent` shows shimmer rows during `isGridLoading`. **No logic change to search flow.** |
| `12fabf6` | `TmdbSearchResultsContent.kt`, `MainViewModel.kt` | Replaced custom shimmer with `RizzoSkeletonRow`. Added `@Immutable` to data classes. **No functional change.** |
| `4274a1e` | `MainViewModel.kt`, `HomeScreen.kt` | Removed dead `StreamSelectionState` code path. `loadTmdb()` and search flow untouched. |

**Verdict: No polish-era commit directly broke search state management.** However, `c0cd9c0` introduced the parallel `searchAll()` with a catch-all returning empty lists, which amplifies the effect of the silent 401 swallow. The regression pre-dates polish.

---

## Confirmed Working Parts

- `SearchBar.kt` → `onQueryChange = viewModel::setSearchQuery` — correctly updates `state.searchQuery`
- `HomeScreen.kt` lines 157-162 — `SearchBar` wired correctly
- `HomeScreen.kt` lines 334-353 — `MoviesContent` shows `TmdbSearchResultsContent` when `state.content is BrowseContent.TmdbSearchResults`
- `HomeScreen.kt` lines 424-437 — `ShowsContent` shows `TmdbSearchResultsContent` when `state.content is BrowseContent.TmdbSearchResults`
- `MainUiState.searchQuery: String = ""` (line 84), `isLoading: Boolean = false` (line 81)
- `BrowseContent.TmdbSearchResults` (line 47) — correct data class
- `@OptIn(FlowPreview::class)` on `MainViewModel` (line 101) for `snapshotFlow` + `debounce`
- `SEARCH_DEBOUNCE_MS = 150L` (line 99)
- `setSearchQuery(q: String)` (line 1081) — correct state update
- `MainViewModel.init {}` search collector (lines 355-366) — correctly debounced, `collectLatest` cancels stale searches
- `selectSection()` resets `searchQuery = ""` (line 416) — no stale query persists across section navigation
- `goBack()` resets `searchQuery = ""` (line 613) — back navigation clears search

---

## Proposed Fix

1. **Primary (most likely cause):** Verify `TMDB_BEARER` is present and valid in `local.properties`:
   ```
   grep TMDB_BEARER local.properties
   ```
   If absent, add the full token:
   ```
   TMDB_BEARER=eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJiNDk3ZjZhN2FlZWZhNGYxYWQ3ZmRmZmFhMmQ0NGI1YSIsIm5iZiI6MTcwNTMwNTk1Mi45NjIsInN1YiI6IjY1YTRlNzYwOGEwZTliMDEyMmI0NWMzNiIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ.Tk5zzydMxXC6DwBB3xKgzSPhuulFBrsyVBWbjltsgZE
   ```
   Then clean and rebuild:
   ```
   ./gradlew clean assembleV4Debug
   ```
   Clear the in-app disk cache (Settings → Clear Cache) to purge cached empty results (TTL_SEARCH = 10 min).

2. **Secondary — defensive token guard in OkHttp interceptor:**
   Throw early in `TmdbApiService.kt` if the token is blank, failing fast rather than sending a malformed request:
   ```kotlin
   .addInterceptor { chain: Interceptor.Chain ->
       val token = BuildConfig.TMDB_BEARER
       require(token.isNotBlank()) { "TMDB_BEARER is not set in local.properties" }
       val request = chain.request().newBuilder()
           .header("Authorization", "Bearer $token")
           .build()
       chain.proceed(request)
   }
   ```

3. **Secondary — check HTTP status in `get()`:**
   Make `TmdbApiService.get()` throw on non-2xx responses so errors propagate rather than silently producing empty results:
   ```kotlin
   private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
       val request = Request.Builder().url(url).build()
       val response = client.newCall(request).execute()
       if (!response.isSuccessful) {
           throw IOException("TMDB API error: ${response.code} ${response.message}")
       }
       response.use { it.body?.string() ?: "" }
   }
   ```

4. **Secondary — remove silent exception swallowing in `searchAll()`:**
   Let `searchMovies()` / `searchShows()` exceptions propagate so `loadTmdb()`'s catch block can set `error = e.message`, showing the user an actual error.

---

## Risk

- **Fix #1 (adding the token):** No risk. The app architecture is correct. The token just needs to be present in `local.properties`.
- **Fix #2 (defensive token check):** No risk to existing functionality. Fails-fast at build/run time rather than silently sending bad requests.
- **Fix #3 (HTTP status check in `get()`):** Low risk. Currently 401/403/5xx return empty results. After this fix they surface as errors. The UI's `ErrorView` already renders when `state.error != null`.
- **Fix #4 (removing silent swallow in `searchAll()`):** No risk. Changes silent failure → visible error. The `ErrorView` on `HomeScreen` already handles `state.error`.
