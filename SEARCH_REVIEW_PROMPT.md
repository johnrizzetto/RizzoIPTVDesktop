# Search Pipeline Review Prompt

**Context:** RizzoIPTVPlayer is a Jetpack Compose Android IPTV player. TMDB search returns results (confirmed via curl) but the app UI shows a loading spinner forever — results never appear.

**You are reviewing the search pipeline end-to-end to identify why results are not rendering.**

---

## Step 1 — Gather the code

Read these files in full:

1. `app/src/main/java/com/rizzoplayer/iptv/data/api/TmdbApiService.kt`
2. `app/src/main/java/com/rizzoplayer/iptv/data/repository/TmdbRepository.kt`
3. `app/src/main/java/com/rizzoplayer/iptv/ui/viewmodel/MainViewModel.kt` — focus on the search-related state (`searchQuery`, `isSearchLoading`, `BrowseContent.TmdbSearchResults`), the `init` block search collector, and the `searchAll` function
4. `app/src/main/java/com/rizzoplayer/iptv/ui/screens/HomeScreen.kt` — focus on `MoviesContent` and `ShowsContent`, specifically how they handle `BrowseContent.TmdbSearchResults`
5. `app/src/main/java/com/rizzoplayer/iptv/ui/screens/home/TmdbSearchResultsContent.kt`
6. `app/src/main/java/com/rizzoplayer/iptv/domain/model/BrowseContent.kt`

---

## Step 2 — Trace the search path (code walkthrough)

For each step below, state: **"OK"** if you can confirm it, or **"BROKEN: [reason]"** if you find a bug.

### 2a. API call
- `TmdbApiService.tmdbUrl()` — does it properly encode query params? Does `get()` handle non-2xx responses gracefully? Does it throw any exception that would propagate upward?

### 2b. Repository
- `TmdbRepository.searchAll()` — does it use `Dispatchers.IO` for network calls? Does `coalesced()` use `coroutineScope {}` wrapper (anti-pattern that causes structured concurrency issues)?
- Are there any timeouts on the HTTP calls?

### 2c. ViewModel
- `MainUiState.searchQuery` — how is it set? Is it debounced?
- `MainUiState.isSearchLoading` — where is it set to `true`? Where is it set to `false`? Is there any code path where it gets stuck `true`?
- `MainUiState.content` — when `BrowseContent.TmdbSearchResults` is set, is it reachable from the UI?
- The search collector in `init { ... }` — does it use `collect` or `collectLatest`? Does it cancel the previous job on new keystrokes? Does `ensureActive()` appear before `_state.update`?
- Any `catch` blocks in the search flow — do they set `isSearchLoading = false` on all code paths?

### 2d. HomeScreen rendering
- `MoviesContent` — when `state.content is BrowseContent.TmdbSearchResults`, does it check `searchContent.query == q`? Does it guard on `state.isSearchLoading`?
- `ShowsContent` — same questions.
- `TmdbSearchResultsContent` — when does it show results vs. skeleton vs. error? Does `isSearchLoading` being `true` ever prevent results from rendering?

### 2e. State consistency
- After a successful search, what is the relationship between `searchQuery`, `content`, and `isSearchLoading`?
- If the user types a short query (< 2 chars) or empties the query, what happens to `content` and `isSearchLoading`?

---

## Step 3 — Verify known-good behaviors

For each statement below, confirm with a code citation or flag as "UNVERIFIED: [reason]".

1. `TmdbApiService.get()` returns `""` for all error cases (no exception propagation)
2. `TmdbRepository.searchAll()` completes within a bounded time even if the network is slow (has a timeout)
3. `isSearchLoading` is set to `false` on every code path out of the search collector (success, error, cancellation, timeout)
4. `BrowseContent.TmdbSearchResults` is reachable from `HomeScreen` when `state.content` holds it
5. `TmdbSearchResultsContent` renders results when `content` has data, regardless of `isSearchLoading` value
6. `searchContent.query == q` check prevents showing stale results from a previous query

---

## Step 4 — Identify the root cause

Based on the trace above, answer:

1. **What is the single most likely cause of "spinner forever"?** Pick ONE from:
   - HTTP call throws an exception that is not caught
   - HTTP call hangs indefinitely (no timeout)
   - `isSearchLoading` never set to `false`
   - `BrowseContent.TmdbSearchResults` never reaches the UI
   - `BrowseContent.TmdbSearchResults` reaches the UI but `TmdbSearchResultsContent` won't render results while `isSearchLoading = true`
   - Query mismatch: `searchContent.query != q` so the results are filtered out
   - Stale results from a previous query are shown
   - Other: [describe]

2. **Is the fix already applied?** Check if the R4 fixes are present:
   - URL-encoding in `tmdbUrl()`
   - `coroutineScope {}` removed from `coalesced()`
   - `isSearchLoading` flag added
   - `searchJob?.cancel()` on new keystroke
   - `ensureActive()` before `_state.update`
   - `searchContent.query == q` check in `MoviesContent` and `ShowsContent`

---

## Step 5 — Write the fix prompt

If you found bugs, write a **single, self-contained prompt** that:
- Describes the exact bug (file, line/function, what is wrong, what should happen)
- Gives the exact code change needed (before/after, or just the correct code)
- States what correct behavior looks like after the fix
- Does NOT suggest changes outside the minimal fix

Format it so it can be copy-pasted directly to another model to implement.

---

## Step 6 — Output

Return your findings in this structure:

```
## Search Path Status
[Each of the checks from Step 2: OK or BROKEN]

## Verified Behaviors
[Step 3 results]

## Root Cause
[Step 4 answer]

## Fix Prompt
[Step 5: the copy-pasteable prompt]
```
