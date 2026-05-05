# Changelog

All notable changes to Rizzo Player v5 are documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [4.0.0-alpha.2] — YYYY-MM-DD

### Phase 2 — Navigation & Focus Bug Fixes

#### Fixed — BackHandler Overlay Priority
- **BackHandler priority ordering (HomeScreen.kt):** Back presses now correctly dismiss overlays before navigating the view hierarchy.
  - Priority 1: `tmdbStreamSelection` overlay dismissed via `dismissTmdbStreamSelection()`
  - Priority 2: `playbackPrep` overlay cancelled via `cancelPlaybackPrep()`
  - Priority 3: Standard back-stack navigation via `goBack()`
  - At root: back press absorbed silently (no spurious home-screen jumps)
  - File: `app/src/main/java/com/rizzoplayer/iptv/ui/screens/HomeScreen.kt`

#### Fixed — Episode Row Focus Freeze (4th Item)
- **Root cause:** `LaunchedEffect(episodes)` at line 479 fired `requestFocus()` on every LazyColumn recomposition — every scroll event triggered a cascade of recompositions that raced with the focus system, causing the cursor to freeze.
- **Fix (SeriesHome.kt — TmdbShowDetailView):**
  - Replaced unstable `LaunchedEffect(episodes)` trigger with `seasonChangeId` counter — only increments when user explicitly changes season
  - Added `rememberLazyListState()` + `animateScrollToItem()` before requesting focus — ensures the target episode is scrolled into view and committed before focus is requested
  - Removed `episodeFocusTrigger` int state — replaced by single `seasonChangeId` driving a coherent scroll-then-focus sequence
  - `LaunchedEffect` now keys only to `seasonChangeId + selectedSeasonIdx + nextEpisodeIdx`, not `episodes`
- File: `app/src/main/java/com/rizzoplayer/iptv/ui/screens/home/SeriesHome.kt`

#### Fixed — Season/Episode D-Pad Navigation (Two-Pane Layout)
- **Season tabs (Row):** Extracted to `SeasonTab` composable with explicit `seasonTabFocus` anchor — LEFT/RIGHT navigate between tabs natively
- **Episode list (LazyColumn):** Added `focusProperties { up = seasonTabFocus; down = episodeFocus }` — UP from first episode returns focus to season tabs, DOWN wraps to first episode
- **SeasonTab composable:** `onSelect` only fires on click/OK press, not on focus enter — prevents accidental season switching during D-pad navigation
- **New imports:** `LazyListState`, `rememberLazyListState`, `focusProperties`
- File: `app/src/main/java/com/rizzoplayer/iptv/ui/screens/home/SeriesHome.kt`

### Phase 3 — UI/UX Enhancement

#### Added — Cinematic Glow for Focused Cards
- **New parameter `hasGlow: Boolean = false`** on `rizzoFocusable()` — opt-in cinematic glow for premium surfaces (movie grids, hero rows)
- When `hasGlow = true`, focused cards receive:
  - Doubled shadow elevation (8.dp → 16.dp) with `RizzoAccent` tint as `ambientColor`/`spotColor` — produces a soft purple bloom halo visible at TV viewing distance
  - Increased border opacity (0.5f → 0.85f) for sharper edge definition on larger screens
  - All glow values driven by `RizzoMotion.GentleSpring` for smooth fade-in/out
- **Backwards compatible:** `hasGlow` defaults to `false`, all existing `rizzoFocusable` calls compile and render identically
- Files: `app/src/main/java/com/rizzoplayer/iptv/ui/designsystem/rizzoFocusable.kt`

### Phase 3 — Typography (Pre-Existing, Verified Sound)
- **RizzoTypography.kt** already fully optimised for 10-foot TV viewing:
  - `displayLarge`: 48.sp, `displayMedium`: 40.sp, `headlineLarge`: 28.sp
  - Minimum body text: 16.sp (`bodyMedium`); preferred: 18.sp (`bodyLarge`)
  - `FontWeight.Black` for display/headline titles
- No changes required — architecture verified intact

---

## [4.0.0-alpha.1] — YYYY-MM-DD

### Added
- v4 flavor fork — installs alongside v3 with isolated DataStore and indigo brand color
- `v4-genesis` tag at fork point; `v4-polish` branch for development

### Changed
- *(List of changes — populated by agents as work completes)*

### Deprecated
- *(Any deprecated features — populated as work progresses)*

### Removed
- *(Removed features — populated as work progresses)*

### Fixed
- *(Bug fixes — populated as work progresses)*

### Security
- *(Security-related changes — populated as work progresses)*

---

## [3.0.0] — Prior Version

See `claude/romantic-leakey-57e175-work` branch history for v3 changelog.

## [4.0.0-alpha.1] — v4/v5 Worktree Branch

### Fixed
- **Bug #1 — "No streams found" when torrents exist:** `fetchMovieStreams`/`fetchEpisodeStreams` now wrap each `await()` individually in try/catch so one source's exception doesn't poison the merged result.
- **Bug #2 — Clicking selected torrent shows premature failure:** `playSelectedTmdbStream` now calls new `resolveSelectedTorrent(stream, fallbackHashes)` which uses the explicitly chosen torrent directly instead of re-searching and picking #1.
