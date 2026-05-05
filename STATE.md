# State — v5/main Branch

## Branch
`v5/main` (worktree at `/Users/johnrizzetto/RizzoIPTVPlayer`)

## Active Session — Phase 2 & Phase 3 (2026-05-05)

**Commit:** `c8b7761` — "Phase 2 focus fixes + Phase 3 cinematic glow v5"

### Phase 2 — Navigation & Focus Bug Fixes

**Bug Fix 1 — BackHandler overlay priority (HomeScreen.kt):**
- Back press now checks overlays in order before navigating: `tmdbStreamSelection` → `playbackPrep` → `goBack()`
- Fixes: pressing Back while stream selection overlay was open would skip dismissal and jump to home
- File: `ui/screens/HomeScreen.kt`

**Bug Fix 2 — Episode row focus freeze (SeriesHome.kt TmdbShowDetailView):**
- Root cause: `LaunchedEffect(episodes)` fired `requestFocus()` on every LazyColumn recomposition — scroll events cascaded into focus races, freezing the cursor on the 4th episode
- Fix: `animateScrollToItem()` guarantees scroll is committed before focus is requested; `seasonChangeId` counter ensures focus only fires on explicit season changes, not on every scroll
- Files: `ui/screens/home/SeriesHome.kt`

**Bug Fix 3 — Season/episode two-pane D-pad routing (SeriesHome.kt):**
- Season tabs extracted to `SeasonTab` composable with explicit `FocusRequester` anchor
- `LazyColumn.focusProperties { up = seasonTabFocus; down = episodeFocus }` wires D-pad up from episode list back to season tabs
- `SeasonTab.onSelect` fires only on click/OK press — prevents accidental season switches during D-pad hover navigation
- New imports: `LazyListState`, `rememberLazyListState`, `focusProperties`

### Phase 3 — UI/UX Enhancement

**Added — Cinematic glow for focused cards (rizzoFocusable.kt):**
- New parameter: `hasGlow: Boolean = false` on `rizzoFocusable()`
- When `true`: doubled shadow elevation (16.dp) with `RizzoAccent`-tinted ambient/spot color → soft purple bloom halo
- Increased border opacity (0.85f vs 0.5f) for sharper focused edge
- Defaults to `false` — fully backwards-compatible, no existing call sites affected
- Files: `ui/designsystem/rizzoFocusable.kt`

**Typography — Pre-existing, verified sound (RizzoTypography.kt):**
- Min body: 16.sp, preferred: 18.sp; `displayLarge`: 48.sp; `FontWeight.Black` for titles
- No changes needed

### Build Verification
- `./gradlew assembleDebug` → BUILD SUCCESSFUL
- `./gradlew ktlintCheck` → BUILD SUCCESSFUL

---

## Prior Sessions

### v5 — TorBox Stream Selection Bug Fixes (COMPLETED)
**Branch:** `v5/p0-rename-cancellation` (worktree at `/Users/johnrizzetto/v5-p0-phase0`)
**Commit:** `79d3198`

**Bug #1 — "No streams found" when torrents exist:**
- Root cause: bare `.await()` calls in `fetchMovieStreams`/`fetchEpisodeStreams` — if either async block threw, the whole coroutineScope collapsed to empty
- Fix: wrap each `await()` individually in try/catch, log warning, return `emptyList()` — one source's failure no longer poisons the merged result

**Bug #2 — Clicking selected torrent shows premature failure:**
- Root cause: `playSelectedTmdbStream` called `resolveMovie(imdbId)` which re-searches and picks #1 ranked torrent, ignoring the user's actual selection
- Fix: new `resolveSelectedTorrent(stream, fallbackHashes)` in TorBoxRepository — plays the explicitly chosen UnifiedTorrent directly, no re-search, passes fallback hashes for retry chain

### v4 — Completed

## IPTV Credentials (Hardcoded)
- Username: `87bcb5ed3f`
- Password: `c46e2b805d`
- URLs: `http://line.trexgaminghub.xyz`, `http://line.gaminghubott.xyz`, `http://vpn.gaminghubott.xyz`, `http://line.gaminghubpro.xyz`, `http://vpn.gaminghubpro.xyz`
- Auto-login: `DefaultCredentials` object in `MainActivity.kt`, triggered via `autoLoginIfNeeded()` in `LaunchedEffect`

## Git Log (v4-genesis..HEAD)
```
c8b7761 Phase 2 focus fixes + Phase 3 cinematic glow v5
fa6fefb fix(search): five-bug search pipeline overhaul
d2aa8f4 fix(search): render ErrorView in Movies and Shows search paths when state.error is non-null
d056e6f chore(search): remove dead setGridLoading and clearGridLoading functions
6a5083b fix(auth): auto-login with hardcoded IPTV credentials on app launch
b474752 test(search): assert error state semantics in MainViewModel and BrowseContent
6cd0772 test(search): regression guard for 401 propagation
c8669ef fix(search): propagate exceptions from searchAll, skip caching empty results
1643b1d fix(search): require non-blank TMDB_BEARER, throw on non-2xx responses
```

## Files Changed This Session
- `ui/screens/HomeScreen.kt` — BackHandler overlay priority ordering
- `ui/screens/home/SeriesHome.kt` — scroll-to-focus coordination, SeasonTab composable, focusProperties imports
- `ui/designsystem/rizzoFocusable.kt` — hasGlow parameter, cinematic glow shadow
- `CHANGELOG.md` — Phase 2 and Phase 3 entries added

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
**Commit:** `79d3198`

**Bug #1 — "No streams found" when torrents exist:**
- Root cause: bare `.await()` calls in `fetchMovieStreams`/`fetchEpisodeStreams` — if either async block threw, the whole coroutineScope collapsed to empty
- Fix: wrap each `await()` individually in try/catch, log warning, return `emptyList()` — one source's failure no longer poisons the merged result

**Bug #2 — Clicking selected torrent shows premature failure:**
- Root cause: `playSelectedTmdbStream` called `resolveMovie(imdbId)` which re-searches and picks #1 ranked torrent, ignoring the user's actual selection
- Fix: new `resolveSelectedTorrent(stream, fallbackHashes)` in TorBoxRepository — plays the explicitly chosen UnifiedTorrent directly, no re-search, passes fallback hashes for retry chain

**Files changed:**
- `TorBoxRepository.kt`: await() try/catch isolation + `resolveSelectedTorrent()` method
- `MainViewModel.kt`: `playSelectedTmdbStream` now calls `resolveSelectedTorrent` instead of `resolveMovie`

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
