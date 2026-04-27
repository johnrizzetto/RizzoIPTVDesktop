# Search Diagnosis Round 2 — 2026-04-24

## Root cause

**`setGridLoading()` is defined but never called.** When the user navigates to Movies or Shows sections and types a search, `loadTmdb` sets `isGridLoading = true` AND `isLoading = true` simultaneously (line 1145). The shimmer shows because `isGridLoading = true`. When content arrives, both are set to `false` and the shimmer hides. **This path is correct.** However, there is a secondary bug: the error state from `loadTmdb`'s catch block (line 1151) is **never rendered in the search results path** — `state.error` is checked only in the general content `when` block, not in the `q.isNotEmpty()` search branch. An exception propagating from `searchAll` would set `error = "..."` but the UI would show `EmptyHint("No movies found")` instead of `ErrorView`.

Additionally, `setGridLoading()` and `clearGridLoading()` are dead code — defined (lines 1157-1164) but never called from any call site, suggesting an incomplete optimization attempt to show shimmer in only one section while keeping the hero visible.

---

## Evidence

### Finding 1: `setGridLoading()` / `clearGridLoading()` — dead code

**File:** `MainViewModel.kt`, lines 1156-1164

```kotlin
/** Set shimmer loading state immediately (before content fetch) so shimmer renders while hero stays visible. */
fun setGridLoading() {
    _state.update { it.copy(isGridLoading = true) }
}

/** Clear shimmer loading state. */
fun clearGridLoading() {
    _state.update { it.copy(isGridLoading = false) }
}
```

`setGridLoading` and `clearGridLoading` are never called from any call site. Verified with:
```bash
grep -rn "setGridLoading\|clearGridLoading" app/src/
```
Output: only the definitions themselves. No external callers.

**Interpretation:** `isGridLoading` is only ever set to `true` at line 1145 inside `loadTmdb`, and only ever set to `false` at lines 1149/1151 inside `loadTmdb`'s success/catch branches. `setGridLoading`/`clearGridLoading` were likely added as part of a planned per-section shimmer optimization that was never completed. They have no effect on current behavior.

### Finding 2: Error state from search is silently swallowed by the UI

**File:** `HomeScreen.kt`, lines 334–352 (Movies search path)

```kotlin
q.isNotEmpty() -> {
    if (state.content is BrowseContent.TmdbSearchResults) {
        val searchContent = state.content as BrowseContent.TmdbSearchResults
        if (searchContent.movies.isNotEmpty()) {
            TmdbSearchResultsContent(content=searchContent, ..., isLoading=state.isGridLoading)
        } else {
            EmptyHint("No movies found")   // ← fires for BOTH zero results AND errors
        }
    } else {
        LoadingView()                     // ← only while content type is still unknown
    }
}
```

The `else` branch (lines 351-352) only renders `LoadingView` while `content` is not yet `BrowseContent.TmdbSearchResults`. Once an error is thrown and `state.error` is set (MainViewModel.kt line 1151), `state.content` remains whatever it was before (e.g., `BrowseContent.None` or the previous content), so the `else` branch does NOT fire — instead `EmptyHint("No movies found")` renders because `content` is not `TmdbSearchResults` and `q.isNotEmpty()` is `true`.

**Contrast with the shows path** (`HomeScreen.kt` lines 424–440): identical structure. No `ErrorView` for search errors.

### Finding 3: `loadTmdb` error path sets `error` but not `content`

**File:** `MainViewModel.kt`, lines 1146–1152

```kotlin
viewModelScope.launch {
    try {
        val content = withContext(Dispatchers.IO) { block() }
        _state.update { it.copy(isLoading = false, isGridLoading = false, content = content, canGoBack = backStack.isNotEmpty()) }
    } catch (e: Exception) {
        _state.update { it.copy(isLoading = false, isGridLoading = false, error = e.message ?: "Unknown error") }
        // ↑ sets error but does NOT set content → HomeScreen goes to EmptyHint, not ErrorView
    }
}
```

---

## Fixes Already Applied Correctly

1. **`require(token.isNotBlank())` in OkHttp interceptor** — `TmdbApiService.kt` line 29. If token is blank, app crashes at first HTTP call with `IllegalStateException: TMDB_BEARER is not set in local.properties`. Clear failure mode.

2. **`throw IOException` on non-2xx** — `TmdbApiService.kt` lines 64–65. 401/403/5xx now throw instead of returning empty string. `loadTmdb`'s catch block catches this and sets `state.error`.

3. **`searchAll` no longer swallows exceptions** — `TmdbRepository.kt` lines 279–284. `async { searchMovies }` + `async { searchShows }` awaited directly without try/catch. Exceptions propagate to `loadTmdb`.

4. **Empty search results not cached** — `TmdbRepository.kt` lines 63–67 (inside `cachedList`):
   ```kotlin
   if (result.isNotEmpty()) {
       val text = withContext(Dispatchers.Default) { json.encodeToString(result) }
       diskCache.put(key, text)
   }
   ```
   Empty results from a 401 response (before the token fix) would have been cached for TTL_SEARCH=10min in the old code. After the token fix, 401 now throws — so the catch block in `loadTmdb` fires and `error` is set instead of caching empty lists. **Poisoned cache from before the fix can still exist on-device** (see Cache Status below).

5. **Token confirmed valid** — `curl -H "Authorization: Bearer $TOKEN" "https://api.themoviedb.org/3/search/movie?query=inception"` returns `200`.

6. **BuildConfig confirmed** — `app/build/generated/source/buildConfig/v4/debug/com/rizzoplayer/iptv/BuildConfig.java` shows `TMDB_BEARER = "eyJhbG...sgZE"` (token baked in correctly).

---

## Fixes Missing or Incorrectly Applied

| Fix | Status | Evidence |
|-----|--------|---------|
| `require(token.isNotBlank())` | ✅ Applied | `TmdbApiService.kt:29` |
| `throw IOException` on non-2xx | ✅ Applied | `TmdbApiService.kt:64-65` |
| `searchAll` exception swallowing removed | ✅ Applied | `TmdbRepository.kt:279-284` |
| Skip caching empty results | ✅ Applied | `TmdbRepository.kt:63-67` |
| `setGridLoading()` / `clearGridLoading()` | ❌ Dead code | Never called anywhere |
| Error state rendered in search path | ❌ Missing | `HomeScreen.kt:334-352` — no `state.error` check in `q.isNotEmpty()` branch |

---

## Network Test Result

```
TOKEN=$(grep TMDB_BEARER local.properties | cut -d'=' -f2)
curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer $TOKEN" \
  "https://api.themoviedb.org/3/search/movie?query=inception"

Result: 200
```

Token is valid. Machine can reach TMDB API.

---

## Cache Status

**Possibly poisoned on-device.** The "skip caching empty results" fix (`c8669ef`) was applied to the **write path** (`cachedList` lines 63-67). However, the **read path** (`cachedList` lines 56-60) still reads from cache on any future call regardless of what was cached previously. If the device was used to search while the token was missing (401 response), the empty `TmdbPage` results were cached under keys like `tmdb_v3_search_movie_inception` for 10 minutes. Even after the fix, those **existing cache entries remain on-device** until they expire or are cleared.

The only way to purge: uninstall/reinstall APK, or clear app data from Android Settings → Apps → Rizzo IPTV v4 → Clear Data.

---

## Recommended Fix

### Fix A — Error state in search rendering path (primary)

**File:** `HomeScreen.kt`, around line 334

In the `q.isNotEmpty()` branch, add an error check before falling through to `EmptyHint`:

```kotlin
q.isNotEmpty() -> {
    // ← ADD THIS BLOCK
    if (state.error != null) {
        ErrorView(
            message = state.error,
            onDismiss = viewModel::retryCurrent,
            onReload = viewModel::retryReload
        )
        return@Crossfade
    }
    // ← END ADD

    if (state.content is BrowseContent.TmdbSearchResults) {
        val searchContent = state.content as BrowseContent.TmdbSearchResults
        if (searchContent.movies.isNotEmpty()) {
            TmdbSearchResultsContent(...)
        } else {
            EmptyHint("No movies found")
        }
    } else {
        LoadingView()
    }
}
```

Apply the same pattern to the Shows search path (around line 424).

Also update `retryCurrent` / `retryReload` in `MainViewModel` to also re-trigger the search query (currently they retry the current content, not specifically search).

### Fix B — Remove dead code `setGridLoading`/`clearGridLoading`

**File:** `MainViewModel.kt`, lines 1156–1164

Delete `setGridLoading()` and `clearGridLoading()` functions. They are never called and serve no purpose. `isGridLoading` is correctly managed exclusively by `loadTmdb`.

### Fix C — Verify `retryCurrent` handles search retry

Check that `retryCurrent` / `retryReload` in `MainViewModel` re-executes `lastTmdbBlock` when `state.error != null` during a search. Currently `retryCurrent` is used for general content errors — verify it works for search errors too.

---

## Risk

- **Fix A (error state in search):** Low risk. Adds a condition that only fires when `state.error != null`. `ErrorView` already exists on the same screen. No behavioral change for the happy path.
- **Fix B (remove dead code):** No risk. These functions have no callers. Removing them reduces confusion and shrinks the ViewModel.
- **Fix C (retry handles search):** Requires verifying `retryCurrent` calls `lastTmdbBlock`. If `lastTmdbBlock` is captured correctly in `loadTmdb` (line 1143: `lastTmdbBlock = block`), then retry should work. Verify before claiming complete.
