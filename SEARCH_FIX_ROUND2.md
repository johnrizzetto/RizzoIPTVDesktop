# Search Fix Round 2 — Diagnosis & Investigation File

**Generated:** 2026-04-24 21:36 ET
**Branch:** `v4-polish` (HEAD at `d2aa8f4`)
**Symptom:** Search loads forever (shimmer visible indefinitely)

---

## What Changed in Round 2

Four commits applied after `SEARCH_DIAGNOSIS_2.md`:

| Commit | Description |
|--------|-------------|
| `d2aa8f4` | **HomeScreen.kt** — Added `ErrorView` to Movies AND Shows `q.isNotEmpty()` search paths, checking `state.error != null` first |
| `d056e6f` | **MainViewModel.kt** — Added `clearError()` function, removed dead `setGridLoading()`/`clearGridLoading()` |
| `6a5083b` | **MainActivity.kt** — Added `DefaultCredentials` object + `autoLoginIfNeeded()` for hardcoded IPTV login |
| `b474752` | **MainViewModelErrorTest.kt** (new) — Regression tests for error state semantics |

---

## Current Code State

### HomeScreen.kt — Movies search path (lines ~334-352)

```kotlin
q.isNotEmpty() -> {
    if (state.error != null) {
        ErrorView(
            message = state.error,
            onDismiss = viewModel::clearError,
            onReload = viewModel::retryReload
        )
    }
    if (state.content is BrowseContent.TmdbSearchResults) {
        val searchContent = state.content as BrowseContent.TmdbSearchResults
        if (searchContent.movies.isNotEmpty()) {
            // ... render grid
        } else {
            EmptyHint("No movies found")
        }
    } else {
        LoadingView()
    }
}
```

### HomeScreen.kt — Shows search path (lines ~427-445)

Same pattern as Movies path above (identical structure).

### MainViewModel.kt — loadTmdb error path (lines ~1141-1147)

```kotlin
catch (e: Throwable) {
    Timber.e(e, "loadTmdb failed")
    _state.update {
        it.copy(
            error = e.message ?: "Unknown error",
            isLoading = false
            // NOTE: content is NOT updated to TmdbSearchResults here
        )
    }
    return@async
}
```

### MainViewModel.kt — clearError (lines ~641-644, newly added)

```kotlin
fun clearError() {
    _state.update { it.copy(error = null) }
}
```

### MainViewModel.kt — retryReload (lines ~645-651, pre-existing)

```kotlin
fun retryReload() {
    _state.update { it.copy(error = null) }
    val tmdbBlock = lastTmdbBlock
    if (tmdbBlock != null) {
        loadTmdb { tmdbBlock() }
    }
}
```

### MainViewModel.kt — lastTmdbBlock declaration (line ~300)

```kotlin
private var lastTmdbBlock: (suspend () -> BrowseContent)? = null
```

---

## The Loading Forever Problem

The shimmer shows when `isLoading = true`. If the search is loading forever, possible causes:

### Cause 1: loadTmdb never completes (network or exception)

If `loadTmdb` throws before setting `isLoading = false`, the shimmer never stops.

**Check:** Add Timber log in `loadTmdb` catch block:
```kotlin
catch (e: Throwable) {
    Timber.e(e, "loadTmdb failed")
    // ...
}
```
Is `Timber.e` logging visible in logcat? Filter: `adb -s adb-YSQAR2939YXB-8bG1iC._adb-tls-connect._tcp logcat | grep -i "loadTmdb\|tmdb\|error"`

### Cause 2: lastTmdbBlock is null — retryReload does nothing

When `retryReload()` is called, if `lastTmdbBlock == null`, the function updates error to null but never calls `loadTmdb`. The state would be stuck with `isLoading = false` and no content.

**Check:** Is `lastTmdbBlock` set BEFORE `loadTmdb` launches? Look at line ~1144:
```kotlin
lastTmdbBlock = block
loadTmdb { block() }  // ← is this line reached?
```

### Cause 3: The search query flow is not debouncing correctly

HomeScreen calls `viewModel.setSearchQuery(query)` on every keystroke. `snapshotFlow` debounces at 150ms. If `setSearchQuery` is being called but `loadTmdb` is not being triggered, the issue is upstream.

**Check:** In `snapshotFlow` block in MainViewModel (lines ~290-310), add Timber log:
```kotlin
Timber.d("snapshotFlow triggered, query=${it.searchQuery}, loading=${it.isLoading}")
```
Then search and check logcat.

### Cause 4: isLoading is true but the shimmer branch is not being reached

In the HomeScreen `q.isNotEmpty()` path, `LoadingView()` is only shown when `state.content` is NOT `TmdbSearchResults`. If `state.content` IS `TmdbSearchResults` but with empty lists, it shows `EmptyHint("No movies found")` instead.

**The shimmer shows when `isLoading = true` and `content` is NOT `TmdbSearchResults`.**

---

## How to Investigate on Device

### Step 1: Get logcat from the app

```bash
adb -s adb-YSQAR2939YXB-8bG1iC._adb-tls-connect._tcp logcat -c  # clear
# Then trigger search in app
adb -s adb-YSQAR2939YXB-8bG1iC._adb-tls-connect._tcp logcat | grep -iE "loadTmdb|setSearchQuery|lastTmdbBlock|retryReload|clearError|error|HomeScreen"
```

### Step 2: Check if the APK installed correctly

The APK was built from HEAD `d2aa8f4`. Verify:
```bash
adb -s adb-YSQAR2939YXB-8bG1iC._adb-tls-connect._tcp shell pm path com.rizzoplayer.iptv.v4
# Should return: package:/data/app/.../base.apk
```

### Step 3: Check build fingerprint

```bash
adb -s adb-YSQAR2939YXB-8bG1iC._adb-tls-connect._tcp shell dumpsys package com.rizzoplayer.iptv.v4 | grep versionName
```

### Step 4: Clear app data (if not done already)

```
Settings → Apps → Rizzo IPTV v4 → Storage → Clear Data
```

---

## Key Files

| File | Role |
|------|------|
| `app/src/main/java/com/rizzoplayer/iptv/ui/screens/HomeScreen.kt` | Search UI — Movies (~line 334) and Shows (~line 427) paths |
| `app/src/main/java/com/rizzoplayer/iptv/ui/viewmodel/MainViewModel.kt` | `loadTmdb`, `clearError`, `retryReload`, `lastTmdbBlock` |
| `app/src/main/java/com/rizzoplayer/iptv/data/repository/TmdbRepository.kt` | `searchAll`, `TTL_SEARCH = 10min`, cache logic |
| `app/src/main/java/com/rizzoplayer/iptv/data/api/TmdbApiService.kt` | `get()`, `require(token.isNotBlank())` guard, HTTP status check |
| `app/src/main/java/com/rizzoplayer/iptv/MainActivity.kt` | `DefaultCredentials`, `autoLoginIfNeeded()` |

---

## What's Still Unresolved

1. **The shimmer could be loading forever because `isLoading = true` is never set to `false`** — if `loadTmdb`'s `async` block never reaches its `finally` or normal completion path
2. **`lastTmdbBlock` might be null when `retryReload` is called** — the retry button (ErrorView's `onReload`) would silently do nothing
3. **The cache might still be serving stale results** — even after "Clear Data", there could be a memory cache in the process

---

## Pre-Flight Verification (round 2)

```bash
# Build must pass:
./gradlew clean assembleV4Debug  # ✓ PASSED

# Token guard still present:
grep -n "require(token.isNotBlank" app/src/main/java/com/rizzoplayer/iptv/data/api/TmdbApiService.kt  # ✓ line 49

# HTTP status check still present:
grep -n "isSuccessful" app/src/main/java/com/rizzoplayer/iptv/data/api/TmdbApiService.kt  # ✓ line 62

# searchAll exception propagate:
grep -n "moviesDeferred.await() to showsDeferred.await()" app/src/main/java/com/rizzoplayer/iptv/data/repository/TmdbRepository.kt  # ✓ line 87

# clearError exists:
grep -n "fun clearError" app/src/main/java/com/rizzoplayer/iptv/ui/viewmodel/MainViewModel.kt  # ✓ line 641

# setGridLoading / clearGridLoading are GONE:
grep -rn "setGridLoading\|clearGridLoading" app/src/  # ✓ empty (dead code removed)
```
