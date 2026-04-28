# RizzoIPTVPlayer — Handoff Prompt for Claude Code / Agent

## Context

You are continuing the RizzoIPTVPlayer Android TV app project. Two worktrees exist on this machine. The goal is to get to a clean, installable APK where TMDB search works, stream selection works, and TV remote navigation is smooth and consistent.

**Communication style:** John (the user) is direct and concise. No filler. Get to the point.

---

## Repos and Worktrees

### Main Repo
```
/Users/johnrizzetto/RizzoIPTVPlayer
```
- `v5/main` branch — current base for all v5 work
- Also has `v4-polish` (stable), and worktrees listed below

### Worktrees
```
/Users/johnrizzetto/v5-p0-phase0  →  branch v5/p0-rename-cancellation
/Users/johnrizzetto/v5-p1-nav     →  branch v5/p1-navigation
/Users/johnrizzetto/v5-p2-search  →  branch v5/p2-search
/Users/johnrizzetto/v5-p3-banner  →  branch v5/p3-banner
/Users/johnrizzetto/v5-p4-streams  →  branch v5/p4-streams
/Users/johnrizzetto/v5-p5-livetv  →  branch v5/p5-livetv
/Users/johnrizzetto/v5-p6-polish  →  branch v5/p6-polish
/Users/johnrizzetto/v5-p7-perf     →  branch v5/p7-perf
/Users/johnrizzetto/v5-p8-ship    →  branch v5/p8-ship
```

### Build Command (all worktrees)
```bash
./gradlew :app:assembleV4Debug
./gradlew :app:installV4Debug   # if device connected
```

### Install on Projector
```bash
adb connect 192.168.50.82:45509
adb install -r /Users/johnrizzetto/v5-p0-phase0/app/build/outputs/apk/v4/debug/app-v4-debug.apk
```
Note: Projector was offline last session. Ping `192.168.50.82` first to confirm it's up.

---

## What's Done

### v5/main (Pushed, Commit bc2b789)
**2 files changed:**
- `HomeScreen.kt` — search branch now handles `BrowseContent.StreamPicker`, so torrent list appears after pressing Play
- `MoviesHome.kt` — overview card overlay removed so multiple titles are visible while scrolling

### v5/p0-rename-cancellation (Pushed, Commit cb3cc05)
**Phase A — TorBox Cancellation Overhaul:**

1. **`TorBoxRepository.kt`** — `fetchMovieStreams` and `fetchEpisodeStreams` now use `supervisorScope` instead of `coroutineScope`. Each `async` source (TorBox Search + Torrentio) is individually wrapped in try/catch with `if (e is CancellationException) throw e` — one source failing no longer poisons the merged result.

2. **`TmdbRepository.kt`** — All catch blocks now rethrow `CancellationException`:
   ```kotlin
   } catch (e: Exception) {
       if (e is CancellationException) throw e
       emptyList()  // or null
   }
   ```

3. **New `resolveSelectedTorrent()` in `TorBoxRepository.kt`** — Takes an explicit `UnifiedTorrent` and plays it without re-searching. Passes fallback hashes for retry chain.

4. **`MainViewModel.kt`** — `playSelectedTmdbStreamCommon` now calls `resolveSelectedTorrent` instead of `resolveMovie`.

5. **`PlaybackPrepOverlay` in `HomeScreen.kt`** — Cancel button now guarded by `prep.canCancel` (appears after 5s grace period).

6. **`StreamPickerScreen.kt`** — `StreamItemCard` call now passes `isFailed = isError, failedReason = errorMessage`.

7. **`MainViewModel.kt`** — `TmdbStreamSelectionState` construction in `selectStreamInPicker` now includes `contentType = "vod"`.

---

## What's Still Pending

### HIGH — "No streams found" Returns Too Fast (v5/main)

**Symptom:** Press Play on a TMDB movie → torrent list briefly appears or doesn't → "No streams found" fires immediately.

**Likely cause:** `resolveSelectedTorrent` in `TorBoxRepository` emits `Searching` then calls `torBoxRepository.resolveMovie(imdbId).collect { ... }`. But `resolveMovie` re-searches and may be getting an empty result set, OR the timeout is too short.

**To diagnose:**
```bash
adb connect 192.168.50.82:45509
adb -s 192.168.50.82:45509 logcat -d | grep -iE "torbox|torrentio|resolve|searching|no.streams"
```
Look for the flow: `Searching` → does it progress to `Queuing`/`Caching`, or does it immediately hit `Failed("No streams found")`?

**Key file:** `TorBoxRepository.kt` — `resolveSelectedTorrent` (around line 362). It calls `torBoxRepository.resolveMovie(imdbId).collect { resolution -> ... }` — this re-searches! It should use the torrent's `hash` directly via `resolveFallback(stream.hash)` or similar. Check the `resolveFallback` method (line 326).

### HIGH — Phase B: Episode Picker

`SeriesHome.kt` → user selects a show → `TmdbDetailScreen.kt` shows seasons/episodes → pressing Play calls `fetchEpisodeStreams` (not `fetchMovieStreams`). This needs verifying and wiring.

**Check:**
- `TmdbDetailScreen.kt` — what happens when user presses Play on a TV show episode?
- `MainViewModel` — is `playSelectedTmdbStream` handling `episode` content type?
- `fetchEpisodeStreams` in `TorBoxRepository` — takes `(imdbId, season, episode)` — is this being called with real values?

### HIGH — Phase C: Cancellable Prep

`playbackPrepJob` in `MainViewModel` is not cancelled when user presses Back or dismisses the stream picker. When user goes back and selects a different stream, two jobs may be racing.

**Fix:** Call `playbackPrepJob?.cancel()` in:
- `dismissStreamPicker()`
- `dismissTmdbStreamSelection()`
- `goBack()` if playback prep is in progress

### MED — Phase D: Catch Block Audit

Verify ALL catch blocks in `MainViewModel` and `TorBoxRepository` rethrow `CancellationException`. Search for `catch (e: Exception)` and check each one.

```bash
grep -n "catch (e: Exception)" app/src/main/java/com/rizzoplayer/iptv/ui/viewmodel/MainViewModel.kt
grep -n "catch (e: Exception)" app/src/main/java/com/rizzoplayer/iptv/data/repository/TorBoxRepository.kt
```

Each should have `if (e is CancellationException) throw e` as the first line.

---

## TV Remote Navigation — Full Audit + Standardization

### The Problem

Two parallel focus systems exist:

**Old (in `TvFocus.kt`):**
- `tvClickable()` — visual focus (scale 1.04x, AccentBlue border)
- `gridRowFocus(upFocus, downFocus)` — grid row traversal
- `gridTopRowFocus(firstItemFocus)` — top row wraps to first item
- `TV_CARD_FOCUS_SCALE = 1.04f`

**New/Correct (in `rizzoFocusable.kt` + `rizzoFocusGroup.kt`):**
- `rizzoFocusable()` — same visual, uses `RizzoAccent`, `RizzoMotion.DefaultSpring`
- `rizzoFocusGroup()` — wraps `focusGroup()` for D-pad coordination
- `rizzoGridRowFocus(upFocus, downFocus)` — grid row traversal
- `rizzoGridTopRowFocus(firstItemFocus)` — top row wraps

**Rule from `tv-remote-focus-refactor` skill:**
- `MutableInteractionSource` + `collectIsFocusedAsState` → visual feedback only
- `onFocusChanged` → behavioral side effects (auto-switch tabs, focus restoration)
- Both can coexist on the same composable

### Files to Audit and Fix

#### 1. `Sidebar.kt` (218 lines)
- Nav items use raw `MutableInteractionSource` + `animateFloatAsState` for scale
- Should use `rizzoFocusable` throughout
- `onFocusChanged` on each nav item only tracks `FocusManager.setSidebarFocusedIndex` — that's a behavioral side effect, keep it
- `focusable(interactionSource = interactionSource)` + `clickable` is the old way — replace with `rizzoFocusable`

#### 2. `MoviesHome.kt` (531 lines)
- Imports `gridTopRowFocus` from `TvFocus.kt` (old) — should use `rizzoGridTopRowFocus` from `rizzoFocusGroup.kt`
- Hero focus handling may use raw `animateFloatAsState` with hardcoded `1.04f` — should use `RizzoMotion.FocusScale`
- Grid uses `focusGroup()` — should be `rizzoFocusGroup()`
- `TvCardSpringSpec` from `TvFocus.kt` — should be `RizzoMotion.DefaultSpring`

#### 3. `SeriesHome.kt` (567 lines)
- Season tab auto-switch via `onFocusChanged` — **keep this side effect, it works**
- But visual feedback uses raw `animateFloatAsState` + `scale` — should migrate to `rizzoFocusable`
- `gridTopRowFocus` from `TvFocus.kt` → `rizzoGridTopRowFocus`
- Episode grid same issues as MoviesHome

#### 4. `StreamSelectionOverlay.kt` (523 lines)
- `StreamItemCard` uses `MutableInteractionSource` + `collectIsPressedAsState` + `animateFloatAsState` — visual-only, no side effects
- Should migrate to `rizzoFocusable` for consistency
- The `failedReason` display and `isFailed` state are already wired (fixed last session)

#### 5. `ContinueWatchingStrip.kt` (358 lines)
- Verify it uses `rizzoFocusable` throughout
- Uses `CombinedMovieCard` — check that composable too

#### 6. `TmdbDetailScreen.kt` (200 lines)
- Play button focus — verify it uses `rizzoFocusable`
- Season/episode list — verify focus traversal is D-pad friendly

#### 7. `HomeScreen.kt` (843 lines)
- `PlaybackPrepOverlay` cancel button has `canCancel` guard — verify it works
- Overlay/dialog navigation: can D-pad navigate through all buttons?
- Sidebar → content focus handoff on section switch — verify focus lands in content area

#### 8. `SettingsScreen.kt` (224 lines)
- Settings rows — verify `rizzoFocusable` or at minimum consistent focus handling

#### 9. `LoginScreen.kt` (171 lines)
- Focus into text fields and button — verify D-pad Enter works

### Migration Pattern

For each composable, the transformation is:

**Before (visual-only focus):**
```kotlin
val interactionSource = remember { MutableInteractionSource() }
val isFocused by interactionSource.collectIsFocusedAsState()
val scale by animateFloatAsState(
    targetValue = if (isFocused) 1.04f else 1f,
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
)
Box(
    modifier = Modifier
        .scale(scale)
        .background(if (isFocused) AccentBlue.copy(alpha = 0.14f) else Color.Transparent, shape)
        .border(if (isFocused) 2.dp else 0.dp, AccentBlue, shape)
        .clickable(interactionSource = interactionSource, indication = null) { onClick() }
)
```

**After:**
```kotlin
Box(
    modifier = Modifier.rizzoFocusable(onClick = onClick, shape = shape)
)
```

**Both (visual + side effect):**
```kotlin
val interactionSource = remember { MutableInteractionSource() }
val isFocused by interactionSource.collectIsFocusedAsState()

Box(
    modifier = Modifier
        .rizzoFocusable(
            onClick = { onClick(); someSideEffect() },
            interactionSource = interactionSource,  // needed to share the source
        )
        .onFocusChanged {
            if (it.isFocused && !isSelected) {
                isSelected = true
                triggerDataLoad()
            }
        }
)
```

### Quick Audit Command
```bash
# Find all files still using old tvClickable
grep -rl "tvClickable\|gridRowFocus\|gridTopRowFocus\|TV_CARD_FOCUS_SCALE" \
  app/src/main/java/com/rizzoplayer/iptv/ui \
  --include="*.kt" | grep -v TvFocus.kt | grep -v "rizzoFocusGroup.kt"
```

Expected after full migration: only `TvFocus.kt` should reference the old names.

---

## Design System Reference

**Colors:** `RizzoAccent` (#2563EB blue), `RizzoAccentDim` (tinted bg), `RizzoError` (red), `TextPrimary`, `TextMuted`, `CardBg`, `CardBgFocused`, `BorderColor`

**Motion:** `RizzoMotion.FocusScale` (1.04f), `RizzoMotion.PressScale` (0.95f), `RizzoMotion.DefaultSpring`

**Focus modifiers (canonical — use these):**
- `Modifier.rizzoFocusable(onClick, shape, ...)` — single focusable element
- `Modifier.rizzoFocusRequester(FocusRequester)` — attach to a composable
- `Modifier.rizzoFocusableWithRequester(focusRequester, onClick, ...)` — both at once
- `Modifier.rizzoFocusGroup()` — container for D-pad coordination
- `Modifier.rizzoGridRowFocus(upFocus, downFocus)` — row traversal
- `Modifier.rizzoGridTopRowFocus(firstItemFocus)` — top row wraps

---

## Key Files and Their State

| File | Lines | Notes |
|------|-------|-------|
| `MainViewModel.kt` | 1556 | Phase A done; still needs B, C, D |
| `HomeScreen.kt` | 843 | Search + PlaybackPrep overlays; nav shell |
| `MoviesHome.kt` | 531 | Grid; needs focus migration |
| `SeriesHome.kt` | 567 | Episode picker; needs focus migration |
| `StreamSelectionOverlay.kt` | 523 | Stream picker UI; needs focus migration |
| `StreamPickerScreen.kt` | 266 | Inline stream picker; isFailed fix applied |
| `Sidebar.kt` | 218 | Nav; needs `rizzoFocusable` migration |
| `TmdbDetailScreen.kt` | 200 | Detail + episode list; verify |
| `TorBoxRepository.kt` | ~430 | Phase A done; resolveMovie still re-searches (bug) |
| `TmdbRepository.kt` | ~330 | Phase A done |
| `TvFocus.kt` | 106 | Old system; deprecate after migration |
| `rizzoFocusable.kt` | 150 | **Use this** — canonical |
| `rizzoFocusGroup.kt` | 53 | **Use this** — grid coordination |

---

## Known Bugs to Fix First

1. **`resolveMovie` re-searches instead of using selected torrent's hash** — `resolveSelectedTorrent` calls `resolveMovie(imdbId)` which re-searches. Should call `resolveFallback(stream.hash)` with the actual selected torrent's hash. This is likely the root cause of "No streams found" — the re-search finds nothing for that imdbId.

2. **`playbackPrepJob` not cancelled on back** — race condition between old and new prep jobs.

3. **Episode picker not wired** — `fetchEpisodeStreams` may not be called correctly for TV shows.

---

## Test Plan

1. **Search:** Type a query → results appear → tap result → torrent list shows → tap torrent → video plays
2. **Cancel flow:** Start playback prep → immediately press Back → prep cancels → no stuck state
3. **Stream picker:** See 5+ streams → pick one → if it fails, pick another → retry works
4. **TV nav:** D-pad through every screen — no elements unreachable, no focus lost, scale/border consistent

---

## When Done

- Commit each phase separately with clear messages
- Push to the appropriate branch
- Update `PHASE0_PICKUP.md` with what's complete
- `./gradlew :app:assembleV4Debug` must pass
- `./gradlew ktlintCheck` must pass

---

*Last updated: 2026-04-28*
