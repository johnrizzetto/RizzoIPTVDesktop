# Phase 0 — Pickup Summary
**Branch:** `v5/p0-rename-cancellation`  
**Status:** Built ✅ | Pushed ✅ | Ready to install

---

## What Was Done

### TorBox Cancellation Overhaul (Phase A — COMPLETE)
**Commits on `v5/p0-rename-cancellation`:**
- `1feefb8` — fix(coroutines): rethrow CancellationException; consolidate timeouts on OkHttp callTimeout
- `79d3198` — fix(torbox): two-bug stream selection overhaul
- `cb3cc05` — fix(torbox): CancellationException rethrow; await isolation; resolveSelectedTorrent; search emitting; stream picker isFailed pass-through; contentType=vod

**What changed:**
- `TorBoxRepository.kt`: `supervisorScope` replaces `coroutineScope` in `fetchMovieStreams` and `fetchEpisodeStreams` — one source's failure no longer poisons the merged result
- `CancellationException` rethrown in all catch blocks (TmdbRepository + TorBoxRepository) — cancelled coroutines no longer silently update state
- New `resolveSelectedTorrent()` method — plays the exact torrent the user selected (no re-search, passes fallback hashes for retry chain)
- `Searching` state emitted in `resolveSelectedTorrent`
- Stream picker now correctly passes `isFailed`/`failedReason` to `StreamItemCard`

### v5/main Fixes — COMPLETE + PUSHED
**Commit `6777e1f` on `v5/main`:**
- `HomeScreen.kt` search branch now handles `BrowseContent.StreamPicker` — torrent list shows after pressing Play
- `MoviesHome.kt` — overview card overlay removed so multiple titles visible while scrolling

---

## What's Still Pending

### 1. v5/main — "No streams found" returns too fast
**The issue:** TorBox/Torrentio search may be completing or timing out — unclear which without logcat.

**To test:** Get logcat from the projector:
```bash
adb connect 192.168.50.82:45509
adb -s 192.168.50.82:45509 logcat -d | grep -i torbox
```
Look for whether `Searching` → `Queuing` → `Caching` fires, or if it goes straight to `Failed("No streams found")`.

### 2. Phase 0 — Remaining Phases B–D
The Phase 0 work was scoped to "cancellation + TorBox stream selection." The following were NOT done yet:

| Phase | What's needed |
|-------|--------------|
| **B** | Episode picker — TV show detail screen needs season/episode selection flow wired to `fetchEpisodeStreams` |
| **C** | Cancellable prep — `playbackPrepJob` cancellation on back/dismiss |
| **D** | Catch block audit — verify ALL catch blocks in `MainViewModel` and `TorBoxRepository` rethrow `CancellationException` |

### 3. TV Remote Navigation — Full Audit Needed
This was started but not finished. Key files to review:

**High priority — clunky navigation:**
- `Sidebar.kt` (218 lines) — nav items use raw `MutableInteractionSource` + `animateFloatAsState` — should be using `rizzoFocusable` throughout
- `MoviesHome.kt` (531 lines) — grid + hero — `gridTopRowFocus` used but may not be wired correctly; inconsistent focus scale (raw 1.04f vs `TV_CARD_FOCUS_SCALE`)
- `SeriesHome.kt` (567 lines) — season/episode selection — `onFocusChanged` auto-switches seasons (side effect) but `rizzoFocusable` not used for visual feedback
- `StreamSelectionOverlay.kt` (523 lines) — `StreamItemCard` uses `MutableInteractionSource` correctly but `isFailed` state wasn't being passed from parent
- `StreamPickerScreen.kt` (266 lines) — the `StreamPickerRow` wrapper passes data down but the nested `StreamItemCard` was missing `isFailed/failedReason`

**Known issues to fix:**
1. `Sidebar.kt` — mix of `MutableInteractionSource` (nav items) vs `rizzoFocusable` (should be canonical)
2. `MoviesHome.kt` — `gridTopRowFocus` from `TvFocus.kt` (old) vs `rizzoGridTopRowFocus` from `rizzoFocusGroup.kt` (new) — two parallel systems
3. `SeriesHome.kt` — `onFocusChanged` for season tab auto-switch works but uses raw scale animation instead of `rizzoFocusable`
4. `HomeScreen.kt` (843 lines) — `PlaybackPrepOverlay` cancel button has `canCancel` guard added this session — verify it works
5. `ContinueWatchingStrip.kt` (358 lines) — uses `rizzoFocusable`? need to verify consistency

**Design system:**
- `rizzoFocusable` in `rizzoFocusable.kt` — the correct canonical modifier (uses `RizzoAccent`, `RizzoAccentDim`, `RizzoMotion`)
- `rizzoFocusGroup`, `rizzoGridRowFocus`, `rizzoGridTopRowFocus` in `rizzoFocusGroup.kt` — correct grid coordination
- Old `tvClickable`, `gridRowFocus`, `gridTopRowFocus` in `TvFocus.kt` — deprecated, should migrate everything to the `rizzo*` versions

**Files using old focus system:**
```bash
grep -r "tvClickable\|gridRowFocus\|gridTopRowFocus" app/src/main/java/ \
  --include="*.kt" -l
# Expected after fix: TvFocus.kt should be the only file importing these
```

### 4. v5 Worktrees — Full Map
```
v5/main                   ← base for all v5 work (currently: 6777e1f)
├── v5/p0-rename-cancellation  ← Phase 0 cancellation work (currently: cb3cc05)
├── v5/p1-navigation            ← nav phase (needs review)
├── v5/p2-search               ← search phase
├── v5/p3-banner               ← branding
├── v5/p4-streams              ← stream picker
├── v5/p5-livetv               ← live TV
├── v5/p6-polish               ← polish
├── v5/p7-perf                 ← performance
└── v5/p8-ship                 ← shipping
```

---

## Install Command
```bash
adb connect 192.168.50.82:45509
adb install -r /Users/johnrizzetto/v5-p0-phase0/app/build/outputs/apk/v4/debug/app-v4-debug.apk
```

Or from main repo (once merged):
```bash
./gradlew :app:assembleV4Debug && ./gradlew :app:installV4Debug
```

---

## Build Commands
```bash
# Phase 0 worktree
cd /Users/johnrizzetto/v5-p0-phase0
./gradlew :app:assembleV4Debug

# v5/main
cd /Users/johnrizzetto/RizzoIPTVPlayer
git checkout v5/main
./gradlew :app:assembleV5Debug
```

---

*Last updated: 2026-04-28*
