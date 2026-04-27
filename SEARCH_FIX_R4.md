# Search Fix Round 4 — Code Review & QA Guide

## What this fixes

Universal search (Movies tab + Shows tab) was broken across five independent bugs:

1. **Query not URL-encoded** — `"the matrix"` produced `?query=the matrix` instead of `?query=the+matrix`, causing `OkHttp` to throw `IllegalArgumentException` synchronously before the request was ever made. Network never fired.
2. **Search job not cancellable** — `loadTmdb()` launched its own independent `viewModelScope.launch`, which `collectLatest` in `init {}` could not cancel. Keystroke "godfather" → "god" → "godf" stacked races; the slowest response wrote last and overwrote the correct result.
3. **Full-screen spinner on shared `isLoading` flag** — Every keystroke set `isLoading = true` via `loadTmdb`. If a stale prior search's `_state.update { isLoading=true }` arrived after a fresh one, spinner stuck forever.
4. **`coalesced()` didn't coalesce** — `coroutineScope { async { } }` blocked until completion inside `getOrPut`, so concurrent callers each launched their own request instead of sharing one in-flight request.
5. **Short/empty query left stale search content** — Backspacing below 2 chars returned early without clearing prior `TmdbSearchResults` from `state.content`.

---

## Files changed

| File | What changed |
|------|-------------|
| `app/src/main/java/com/rizzoplayer/iptv/data/api/TmdbApiService.kt` | `tmdbUrl()` now URL-encodes all query keys and values. `get()` now catches `IllegalArgumentException` and returns `""` instead of throwing. |
| `app/src/main/java/com/rizzoplayer/iptv/data/repository/TmdbRepository.kt` | `coalesced()` now uses a class-level `CoroutineScope(SupervisorJob() + Dispatchers.IO)` (`coalesceScope`) so concurrent callers share a single in-flight `Deferred`. |
| `app/src/main/java/com/rizzoplayer/iptv/ui/viewmodel/MainViewModel.kt` | `isSearchLoading: Boolean = false` added to `MainUiState`. `searchJob: Job?` added as a private field. The `collectLatest` search collector replaced with a `collect` that owns a cancellable `Job`, cancels the previous job on each new keystroke, uses `isSearchLoading` (not `isLoading`) for spinner state, clears stale search content on short query, and calls `ensureActive()` before writing results. |
| `app/src/main/java/com/rizzoplayer/iptv/ui/screens/HomeScreen.kt` | Movies and Shows `q.isNotEmpty()` branches updated to: (a) use `isSearchLoading` instead of `isGridLoading` for spinner, (b) match `searchContent.query == q` before showing results to prevent stale-content flash, (c) show `LoadingView` only when `isSearchLoading = true` during an active search. |

---

## Bug-by-bug review checklist

### Bug 1 — TmdbApiService.kt

**`tmdbUrl()` — URL encoding**

Find `tmdbUrl()` (~line 70). Verify the body is:

```kotlin
private fun tmdbUrl(path: String, vararg pairs: Pair<String, String>): String {
    val params = pairs.joinToString("&") { (k, v) ->
        "${java.net.URLEncoder.encode(k, "UTF-8")}=${java.net.URLEncoder.encode(v, "UTF-8")}"
    }
    return if (params.isEmpty()) "$baseUrl$path" else "$baseUrl$path?$params"
}
```

**Key:** both `k` and `v` are encoded. `"the matrix"` → `"the+matrix"`. Space, ampersand, question mark all safe.

**`get()` — catch `IllegalArgumentException`**

Find `get()` (~line 61). The body should be wrapped in `try/catch`:

```kotlin
private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
    try {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("TMDB API error: ${response.code} ${response.message}")
        }
        response.use { it.body?.string() ?: "" }
    } catch (_: IllegalArgumentException) {
        // malformed URL — treat as empty
        ""
    }
}
```

**Key:** `IllegalArgumentException` from `Request.Builder().url(url)` is caught and returns `""`. This means if encoding is ever missing elsewhere, the app shows empty results instead of crashing.

---

### Bug 4 — TmdbRepository.kt

**`coalesceScope` field**

Find the class-level fields (~line 38). Verify:

```kotlin
private val inFlightRequests = java.util.concurrent.ConcurrentHashMap<String, Deferred<Any>>()
private val coalesceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
```

**Key:** `CoroutineScope` (not `GlobalScope`) with `SupervisorJob`. This is a proper non-global scope scoped to the repository instance.

**`coalesced()` function**

Verify the body is:

```kotlin
@Suppress("UNCHECKED_CAST")
private suspend fun <T> coalesced(key: String, block: suspend () -> T): T {
    val deferred = inFlightRequests.getOrPut(key) {
        coalesceScope.async { block() as Any }
            .also { it.invokeOnCompletion { inFlightRequests.remove(key) } }
    } as Deferred<T>
    return deferred.await()
}
```

**Key:** No `coroutineScope {}` wrapper. `getOrPut` returns immediately with the existing `Deferred` if present — concurrent callers get the same `Deferred` and all `await()` on it. The `also { it.invokeOnCompletion {...} }` removes the entry when the async completes.

---

### Bugs 2 & 3 — MainViewModel.kt

**`isSearchLoading` in `MainUiState`**

Find `MainUiState` (~line 78). Verify:

```kotlin
val isSearchLoading: Boolean = false,
```

This is a new dedicated flag for search spinner, separate from `isLoading` (browse content loading).

**`searchJob` field**

Find private fields (~line 303). Verify:

```kotlin
private var searchJob: Job? = null
```

**Search collector in `init {}`**

Find the `snapshotFlow { _state.value.searchQuery }` collector (~line 355). Verify it:

1. Uses `collect { rawQuery ->` (not `collectLatest`)
2. Calls `searchJob?.cancel()` before doing anything
3. On `query.length < 2`: clears `content` to `BrowseContent.Empty` if it was `TmdbSearchResults`, sets `isSearchLoading = false`, then `return@collect`
4. Sets `searchJob = launch { ... }` with `isSearchLoading = true` and `error = null`
5. Inside the `try` block: calls `tmdbRepository.searchAll(query)` via `withContext(Dispatchers.IO)`
6. Calls `ensureActive()` after the IO call — this is the cancellation checkpoint
7. Uses `if (it.searchQuery.trim() != query) it` guard before writing results — stale response from a cancelled job is dropped
8. `catch (_: CancellationException)` does nothing (expected on next keystroke)
9. `catch (e: Exception)` sets `isSearchLoading = false` and `error = e.message`

The critical pattern:

```kotlin
val (movies, shows) = withContext(Dispatchers.IO) { tmdbRepository.searchAll(query) }
ensureActive()  // ← throws CancellationException if job was cancelled
_state.update {
    if (it.searchQuery.trim() != query) it  // ← stale response guard
    else it.copy(content = BrowseContent.TmdbSearchResults(...), isSearchLoading = false, ...)
}
```

---

### Bug 5 — HomeScreen.kt

**Movies search path** — find `q.isNotEmpty()` in `MoviesContent` (~line 334). Verify:

```kotlin
q.isNotEmpty() -> {
    val searchContent = state.content as? BrowseContent.TmdbSearchResults
    if (searchContent != null && searchContent.query == q) {
        if (searchContent.movies.isNotEmpty()) {
            TmdbSearchResultsContent(
                content = searchContent,
                favorites = favorites,
                viewModel = viewModel,
                filter = "movies",
                isLoading = state.isSearchLoading   // ← NOT isGridLoading
            )
        } else if (state.isSearchLoading) {
            LoadingView()
        } else {
            EmptyHint("No movies found")
        }
    } else {
        LoadingView()
    }
}
```

**Key checks:**
- `searchContent.query == q` — prevents showing results from a previous search while the new one is loading
- `isLoading = state.isSearchLoading` — search-specific flag, not the browse flag
- `LoadingView()` only shows when `isSearchLoading = true` (active search in progress with empty results), not whenever `content` isn't `TmdbSearchResults`

**Shows search path** — identical structure, `filter = "shows"`, `searchContent.shows.isNotEmpty()`, `EmptyHint("No shows found")`.

---

## Expected behavior after fix

| Action | Before | After |
|--------|--------|-------|
| Type `"the matrix"` | Spinner forever (URL throws) | Results within ~1s |
| Type `"godfather"` → backspace to `"godf"` | Race: slowest write wins; wrong results | Only final query's results shown |
| Backspace to empty | Stale search results persist | Normal browse content returns |
| Fast typing (`g`, `go`, `god`...) | Stale `isLoading = true` from old job | Old jobs cancelled; spinner tracks only active job |
| App left idle 10 min, return, type `"inception"` | Poisoned cache or stale empty result | Fresh network call; results render |

---

## How to test manually

### Prerequisites
1. Install `app/build/outputs/apk/v4/debug/app-v4-debug.apk`
2. **Clear app data** before first launch: Android Settings → Apps → Rizzo IPTV v4 → Storage → Clear Data

### Test 1: Space in query (URL encoding)
- Go to Movies tab
- Type `the matrix` (contains space)
- Expected: results appear within ~2s, no spinner after that
- Pass: poster grid of "The Matrix" movies renders
- Fail: spinner runs forever, no results

### Test 2: Stale result overwrite prevention
- Go to Movies tab
- Type `godfather` and wait for results to load
- While looking at results, quickly backspace to `godf` and stop
- Expected: results change to match "godf", no flash of "godfather" content
- Pass: only "godfather" or "godf" results show, consistent with final query
- Fail: wrong results, or results from previous query visible briefly

### Test 3: Spinner unsticks after error
- Go to Movies tab
- Type a query that was failing before (e.g. after token was fixed)
- Expected: spinner shows briefly, then results OR error message
- Pass: spinner disappears when results arrive; `ErrorView` shows on genuine error
- Fail: spinner stuck indefinitely

### Test 4: Backspace clears search
- Go to Movies tab
- Type `inception` and wait for results
- Backspace until query is empty
- Expected: search results disappear, normal browse content (genre rows) returns
- Pass: no search results visible, browse categories visible
- Fail: stale search results remain on screen

### Test 5: Fast typing doesn't flicker
- Go to Shows tab
- Rapidly type: `h`, `hu`, `hul`, `hulu`
- Expected: spinner for brief moments, final "hulu" results only
- Pass: only "hulu" show results, no intermediate flicker
- Fail: results flash for intermediate queries

---

## How to verify network calls are actually made

After clearing app data, the first search for `"test"` should hit TMDB API. You can verify with:

```bash
# On the device/emulator, check HTTP traffic via logcat
adb logcat | grep -i "TmdbApi\|search/movie\|search/tv"
```

Or add a temporary log in `TmdbApiService.get()`:

```kotlin
println("TMDB REQUEST: $url")
```

Then watch `adb logcat` for the encoded URL:

```
TMDB REQUEST: https://api.themoviedb.org/3/search/movie?query=the+matrix&page=1
```

---

## Commit

```
fix(search): five-bug search pipeline overhaul

- URL-encode all query params in tmdbUrl() (TmdbApiService)
- Catch IllegalArgumentException in get() to prevent crashes (TmdbApiService)
- Replace coroutineScope{} with coalesceScope in coalesced() (TmdbRepository)
- Add isSearchLoading flag; cancel previous search job on new keystroke (MainViewModel)
- Match searchContent.query == q before showing results (HomeScreen)
- Clear stale TmdbSearchResults on short/empty query (MainViewModel)
```

---

## Files that must NOT have been touched

- `TmdbApiService.kt` — only `get()` and `tmdbUrl()` changed
- `TmdbRepository.kt` — only `coalesced()` and imports changed
- `MainViewModel.kt` — only `isSearchLoading` field, `searchJob` field, and the search `collect` block changed
- `HomeScreen.kt` — only the `q.isNotEmpty()` branches in `MoviesContent` and `ShowsContent` changed
- All other files untouched
