# QA Report — v4-polish

## Search Fix — Completed

**Status:** READY FOR DEVICE TEST
**Pre-flight:** PASSED (TMDB_BEARER token present in local.properties)
**Build:** SUCCESS
**Tests:** 48/48 pass
**ktlint:** CLEAN

### Fixes Applied

| # | Commit | Fix | File |
|---|--------|-----|------|
| #2 | 1643b1d | Require non-blank TMDB_BEARER token in OkHttp interceptor | TmdbApiService.kt |
| #3 | 1643b1d | Throw IOException on non-2xx HTTP responses | TmdbApiService.kt |
| #4 | c8669ef | Remove silent exception swallowing in searchAll() | TmdbRepository.kt |
| #5 | c8669ef | Skip caching empty TMDB search results | TmdbRepository.kt |

### Regression Tests

| Test | Status | Notes |
|------|--------|-------|
| TmdbApiServiceTest (401 → IOException) | PASS | Committed 6cd0772 |
| TmdbRepository exception propagation | BLOCKED | TmdbApiService is final class; cannot mock/subclass without java-agent |

### Root Cause
BuildConfig.TMDB_BEARER was empty at compile time (token not in local.properties) → OkHttp Authorization header sent "Bearer " (empty) → TMDB returned 401 → JSON decode failed → exception silently swallowed → empty search results displayed to user.

### Blocker
TmdbApiService is a final Kotlin class. Without a mockk Java agent configured, it cannot be mocked or subclassed for unit testing. The Fix #4 regression test (searchAll exception propagation) is blocked without instrumentation tooling.

---

## Search Fix Round 2 — 2026-04-24

**Status:** READY FOR DEVICE TEST (after user clears app data per BLOCKERS.md)
**Build:** PASSING
**Tests:** All pass (170 tasks executed)

### Root Cause
Error state from `loadTmdb` catch block never rendered in the search UI path. When a TMDB API exception propagated (e.g., 401 after the token fix), `state.error` was set but the `q.isNotEmpty()` branch in HomeScreen never checked `state.error` — it fell through to `EmptyHint("No movies found")` instead of showing `ErrorView`. Additionally, the on-device cache was poisoned with empty results cached before the token fix was applied.

### Fixes Applied

| Fix | Description | File |
|-----|-------------|------|
| A | Added `ErrorView` to Movies search path (`q.isNotEmpty()` branch) with `onDismiss=clearError`, `onReload=retryReload` | HomeScreen.kt |
| A | Added `ErrorView` to Shows search path (`q.isNotEmpty()` branch) with same params | HomeScreen.kt |
| B | Removed dead `setGridLoading()` and `clearGridLoading()` functions | MainViewModel.kt |
| C | Added `clearError()` function to MainViewModel (existing `retryReload()` handles search retry via `lastTmdbBlock`) | MainViewModel.kt |
| D | Hardcoded IPTV credentials via `DefaultCredentials` object in MainActivity — auto-login on launch | MainActivity.kt |

### User Action Required
Before testing search on the device, the user must clear app data:
```
Settings → Apps → Rizzo IPTV v4 → Storage → Clear Data
```
This clears the poisoned cache so the app fetches fresh results from TMDB. See BLOCKERS.md for details.
