# State — v4-polish Branch

## Branch
`v4-polish` (based on `v4-genesis`)

## Completed

### Search Fixes — All Committed

|| Commit | Description ||
|--------|-------------||
| 1643b1d | fix(search): require non-blank TMDB_BEARER, throw on non-2xx responses |
| c8669ef | fix(search): propagate exceptions from searchAll, skip caching empty results |
| 6cd0772 | test(search): regression guard for 401 propagation |

### What Was Done (Round 1)
- Fix #2: Added `require(token.isNotBlank())` guard in TmdbApiService.kt
- Fix #3: Added HTTP status check — throw IOException on non-2xx response
- Fix #4: Removed catch block from `searchAll()` in TmdbRepository.kt
- Fix #5: Added `if (result.isNotEmpty())` guard before caching empty results
- Created TmdbApiServiceTest.kt — regression test for Fix #3 using MockWebServer (401 → IOException)
- Created QA_REPORT.md at repo root
- Created SEARCH_DIAGNOSIS.md (untracked, user manages)
- Created SEARCH_FIX_BLOCKED.md (untracked, documents test blocker)

### Search Fix Round 2 — Applied This Session
- **Fix A:** `ErrorView` added to Movies AND Shows `q.isNotEmpty()` search paths in `HomeScreen.kt`
- **Fix B:** Removed dead `setGridLoading()` / `clearGridLoading()` from `MainViewModel.kt`
- **Fix C:** Added `clearError()` to `MainViewModel.kt` (existing `retryReload()` handles search retry via `lastTmdbBlock`)
- Hardcoded IPTV credentials in `MainActivity.kt` via `DefaultCredentials` object — auto-login on launch
- Created `BLOCKERS.md` — user must clear app data on device before testing

### Search Fix Round 4 — Applied 2026-04-27
- **Bug 1:** URL-encode all query params in `tmdbUrl()` (was not encoding `query` param — special chars caused crash)
- **Bug 2:** Catch `IllegalArgumentException` in `get()` to prevent crashes on malformed URLs; remove `IOException` throw for non-2xx (downstream `fetchPage` tolerates empty/garbage)
- **Bug 3:** Replace `coroutineScope{}` wrapper with long-lived `coalesceScope` in `coalesced()` — prevents child-coroutine-scope cancellation from killing in-flight requests
- **Bug 4:** Add `isSearchLoading` flag; cancel previous `searchJob` on each new keystroke instead of using `collectLatest` — prevents race between concurrent searches
- **Bug 5:** Match `searchContent.query == q` before showing results in `MoviesContent` and `ShowsContent` — prevents stale results flash on fast type/erase
- Commit: `fa6fefb`

### Search Fix Round 5 — Applied 2026-04-27 (root cause fixes)
- **Bug 1 (HIGH):** `TmdbApiService.get()` catch-all `catch (e: Exception)` re-throws `CancellationException` instead of swallowing it. Prevents cancelled coroutines from racing to update state with stale results. Commit: `09b3df1`
- **Bug 2 (HIGH):** `MainViewModel` searchJob catch block reordered — `TimeoutCancellationException` now before `CancellationException`. Previously unreachable (subclass), leaving `isSearchLoading` stuck permanently on timeout. Commit: `09b3df1`
- **Bug 3 (MEDIUM):** `HomeScreen` Movies and Shows search branches now gate `LoadingView()` on `state.isSearchLoading`. Previously unconditional `else { LoadingView() }` left permanent spinner on 1-char queries and any state-update gap. Commit: `09b3df1`

## Verification
- `./gradlew assembleV4Debug` → BUILD SUCCESSFUL
- `./gradlew ktlintCheck` → BUILD SUCCESSFUL
- `./gradlew test` → 170 tasks, all pass
- APK: `app/build/outputs/apk/v4/debug/app-v4-debug.apk`
- Commit `09b3df1` pushed to `origin/v4-polish`

## TMDB Keys
- `TMDB_BEARER` present in local.properties
- curl returns HTTP 200 — token is valid

## Status

### v5 — TorBox Stream Selection Bug Fixes (COMPLETED)

**Branch:** `v5/p0-rename-cancellation` (worktree at `/Users/johnrizzetto/v5-p0-phase0`)
**Latest commit:** `94e4cbc` (parent: `cb3cc05`)

**Bug #1 — "No valid torrent hash found" for ALL torrents:**
- Root cause: `TorBoxApiService.addMagnet()` posted form param `"magnet"` but TorBox API expects `"magnet_uri"`
- Fix: changed `.addFormDataPart("magnet", magnet)` → `.addFormDataPart("magnet_uri", magnet)` in `TorBoxApiService.kt`
- Impact: ALL torrents were being rejected regardless of validity

**Bug #2 — selectStreamInPicker ignored user's torrent selection:**
- Root cause: `selectStreamInPicker` called `torBoxRepository.resolveMovie(picker.imdbId)` — fresh search picking ranked #1, ignoring user's selection
- Fix: replaced with `torBoxRepository.resolveSelectedTorrent(stream, currentFallbackHashes).collect { handleStreamResolution(...) }`
- Now respects the `stream` (UnifiedTorrent) the user actually selected in StreamPickerScreen

**Bug #3 — "No streams found" when either source throws (prior session):**
- Root cause: bare `.await()` calls in `fetchMovieStreams`/`fetchEpisodeStreams`
- Fix: individual try/catch per await, one source's failure no longer poisons merged result

**Files changed:**
- `TorBoxApiService.kt`: addMagnet param `magnet_uri`
- `MainViewModel.kt`: selectStreamInPicker now uses resolveSelectedTorrent

**Build:** `./gradlew assembleV4Debug` → BUILD SUCCESSFUL
**ktlint:** BUILD SUCCESSFUL

---

### v4 — Completed

## IPTV Credentials (Hardcoded)
- Username: `87bcb5ed3f`
- Password: `c46e2b805d`
- URLs: `http://line.trexgaminghub.xyz`, `http://line.gaminghubott.xyz`, `http://vpn.gaminghubott.xyz`, `http://line.gaminghubpro.xyz`, `http://vpn.gaminghubpro.xyz`
- Auto-login: `DefaultCredentials` object in `MainActivity.kt`, triggered via `autoLoginIfNeeded()` in `LaunchedEffect`

## Git Log (v4-genesis..HEAD)
```
fa6fefb fix(search): five-bug search pipeline overhaul
d2aa8f4 fix(search): render ErrorView in Movies and Shows search paths when state.error is non-null
d056e6f chore(search): remove dead setGridLoading and clearGridLoading functions
6a5083b fix(auth): auto-login with hardcoded IPTV credentials on app launch
b474752 test(search): assert error state semantics in MainViewModel and BrowseContent
6cd0772 test(search): regression guard for 401 propagation
c8669ef fix(search): propagate exceptions from searchAll, skip caching empty results
1643b1d fix(search): require non-blank TMDB_BEARER, throw on non-2xx responses
4274a1e chore: remove dead IPTV StreamSelectionState code path
92f4321 test: add regression guard for TmdbDetailScreen extended icons
12fabf6 v4 polish: design system tokens, rizzoFocusGroup, @Immutable, ktlint
```

## Files Changed This Session
- `app/src/main/java/com/rizzoplayer/iptv/ui/viewmodel/MainViewModel.kt` — added `clearError()`, removed `setGridLoading`/`clearGridLoading`
- `app/src/main/java/com/rizzoplayer/iptv/ui/screens/HomeScreen.kt` — added `ErrorView` to Movies + Shows search paths
- `app/src/main/java/com/rizzoplayer/iptv/MainActivity.kt` — added `DefaultCredentials`, `autoLoginIfNeeded()`
- `BLOCKERS.md` — created
- `SEARCH_DIAGNOSIS_2.md` — created
- `STATE.md` — updated
