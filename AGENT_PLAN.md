# RizzoIPTVPlayer v4 — Agent Plan

**Branch:** `v4-polish` | **Orchestrator:** John Rizzetto | **Date:** 2026-04-23

---

## Exclusive File Ownership

No agent touches another agent's owned files without orchestrator approval. Shared files (MainActivity, build.gradle.kts, NavGraph) are edited only by the orchestrator.

---

### Agent 0 — Orchestrator (John Rizzetto)

**Owns:**
- `v4-polish` branch management
- `app/build.gradle.kts` flavor additions
- `v4/` resource directories (strings, icons)
- `AGENT_PLAN.md`, `AUDIT.md`, `DECISIONS.md`, `MIGRATION.md`, `CHANGELOG.md`, `POLISH_REPORT.md`
- `/polish-artifacts/` directory

**Coordinates:**
- Merges from agents 1–6
- Resolves cross-agent conflicts
- Final CI gate

---

### Agent 1 — Design System & Focus Foundation

**Owns:**
- `ui/theme/RizzoColors.kt` — color tokens (dark palette, AccentBlue → brand indigo for v4)
- `ui/theme/RizzoSpacing.kt` — spacing constants
- `ui/theme/RizzoRadii.kt` — corner radii
- `ui/theme/RizzoElevation.kt` — shadow/elevation tokens
- `ui/theme/RizzoTypography.kt` — 10-foot typography (min 16sp body, 18–20sp preferred, 28–48sp headings)
- `ui/theme/RizzoMotion.kt` — spring specs, durations, easings
- `ui/theme/Theme.kt` — RizzoTheme composable (dark-only)
- `ui/designsystem/rizzoFocusable.kt` — canonical `Modifier.rizzoFocusable()` extension
- `ui/designsystem/rizzoFocusGroup.kt` — `Modifier.rizzoFocusGroup()` for LazyRow/LazyVerticalGrid coordination
- `ui/designsystem/RizzoCard.kt` — focusable card primitive
- `ui/designsystem/RizzoPoster.kt` — poster with AspectRatio variants (2:3 / 16:9 / 1:1)
- `ui/designsystem/RizzoChip.kt`
- `ui/designsystem/RizzoButton.kt`
- `ui/designsystem/RizzoIconButton.kt`
- `ui/designsystem/RizzoTextField.kt` — TV-safe keyboard-first text field
- `ui/designsystem/RizzoDialog.kt`
- `ui/designsystem/RizzoBottomSheet.kt`
- `ui/designsystem/RizzoToast.kt`
- `ui/designsystem/RizzoSkeleton.kt`
- `ui/designsystem/RizzoProgressBar.kt`
- `ui/designsystem/RizzoRow.kt` — focus-aware LazyRow with snap-to-item
- `ui/designsystem/RizzoGrid.kt` — focus-aware LazyVerticalGrid
- `ui/designsystem/DESIGN_SYSTEM.md`

**Shared inputs (read-only):**
- `ui/theme/TvFocus.kt` — existing focus utilities to wrap/consolidate

**Blocks:** Agents 2, 3, 4, 6 (needs `rizzoFocusable` + primitives before they can use them)

**First sprint:** `rizzoFocusable()` + tokens + `RizzoCard` + `RizzoPoster` + `RizzoRow` + `DESIGN_SYSTEM.md`

---

### Agent 2 — Home, Browse, Search

**Owns:**
- `ui/screens/HomeScreen.kt` — **MUST SPLIT** — nav shell stays, content branches become separate screens
- `ui/screens/home/MoviesHome.kt` — **MUST SPLIT** — hero carousel extracted
- `ui/screens/home/SeriesHome.kt` — **MUST SPLIT** — hero carousel extracted
- `ui/screens/home/Sidebar.kt` — top-nav/side-nav shell
- `ui/screens/home/SearchBar.kt` — TV-safe search with D-pad keyboard
- `ui/screens/home/TmdbSearchResultsContent.kt` — **MUST TRIM** to ≤300 lines
- `ui/screens/home/ContinueWatchingStrip.kt` — **MUST TRIM** to ≤300 lines
- `ui/screens/home/CategoryComponents.kt`
- `ui/viewmodel/HomeViewModel.kt` — **MUST SPLIT** into `BrowseViewModel` + `HomeViewModel`

**Depends on:** Agent 1 (first sprint: `rizzoFocusable`, `RizzoRow`, `RizzoPoster`, `RizzoSkeleton`)

**Deliverables:**
- Hero carousel with backdrop + auto-rotate + pause-on-focus
- Horizontal category rows with `RizzoRow` (snap-to-item, prefetch)
- `RizzoSkeleton` shimmer replacing current shimmer
- D-pad nav polish (up→nav, left edge→sidebar, right edge no-wrap)
- Debounced TMDB search with DataStore-backed recent queries
- Filter chips (year/genre/type) in search
- Empty/error/offline states fully designed

**Screens to create:**
- `ui/screens/home/MoviesBrowseScreen.kt` — extracted from MoviesHome (hero + grid)
- `ui/screens/home/SeriesBrowseScreen.kt` — extracted from SeriesHome (hero + seasons + episodes)

---

### Agent 3 — Detail Screens, Favorites, Recently Watched

**Owns:**
- `ui/screens/home/TmdbDetailScreen.kt` — VOD detail, Series detail
- `ui/screens/home/FavoritesView.kt` — Favorites grid
- `ui/screens/home/ChannelComponents.kt` — Channel card components
- `ui/viewmodel/DetailViewModel.kt` — NEW (extracted from HomeViewModel)
- `ui/viewmodel/FavoritesViewModel.kt` — NEW

**Depends on:** Agent 1 (`rizzoFocusable`, `RizzoCard`, `RizzoPoster`) and Agent 2 (BrowseViewModel)

**Deliverables:**
- Detail layout: full-bleed backdrop + gradient scrim + poster + title/meta + synopsis + cast row
- "Play" + "Add to Favorites" as primary focus targets
- Series: season chip row + episode list with per-episode progress
- "Continue" vs "Play from start" logic from PlaybackPositionStore
- Recently Watched shelf on Home (sorted by timestamp)
- Favorites grid with remove-on-long-press (D-pad)
- Crossfade+scale transition (NOT shared element — Compose 1.7+ needed; document in DECISIONS.md)

---

### Agent 4 — Player UI & Stream Selection Overlay

**Owns:**
- `ui/screens/home/StreamSelectionOverlay.kt` — redesigned with state labels
- `ui/screens/home/StreamSelectionOverlay.kt` player controls section
- `ui/screens/home/EpgOverlay.kt` — NEW D-pad-navigable EPG overlay
- `ui/screens/home/PlayerControlsOverlay.kt` — NEW composable overlay for PlayerActivity

**Does NOT touch:** `PlaybackResolver`, `TorBoxRepository`, `TorBoxApiService`, `PlayerActivity.kt` player initialization, `ExoPlayer` configuration

**Depends on:** Agent 1 (`rizzoFocusable`, `RizzoDialog`, `RizzoProgressBar`)

**Deliverables:**
- Player overlay: auto-hide 4s, reveal on any D-pad press
- Scrubber: left/right seek, long-press fast-scrub, focusable
- Controls: play/pause, ±10s skip, audio track, subtitle track, speed, aspect ratio, PiP
- Resume-from-position prompt from PlaybackPositionStore
- Next episode card in last 20s
- Stream selection: Searching → Queuing → Caching → Ready/Failed states with progress + ETA
- EPG overlay: timeline grid navigable with D-pad (left/right=time, up/down=channel)
- Subtitle settings: size, color, background opacity read from Settings

---

### Agent 5 — Code Quality, Architecture, Performance

**Owns:**
- `ui/viewmodel/MainViewModel.kt` — **MUST SPLIT** into PlaybackViewModel + EpgViewModel + thin MainViewModel coordinator
- `ui/viewmodel/HomeViewModel.kt` — trim to ≤300 lines, split BrowseViewModel
- All files > 300 lines (cross-cutting)
- Compose performance: `@Immutable`/`@Stable`, `key()`, `derivedStateOf`
- `ui/theme/TvFocus.kt` — consolidate into Agent 1's `rizzoFocusable`
- `ui/navigation/FocusManager.kt` — document cross-screen focus restoration API
- `/polish-artifacts/compose-metrics/` — Compose Compiler Metrics baseline
- Gradle: ktlint + detekt + AndroidLint configuration
- `proguard-rules.pro` — audit for R8 minification
- Baseline Profile + macrobenchmark module (if not exists)

**Depends on:** Agents 1–4 for code cleanup in their files

**Deliverables:**
- `MainViewModel` split complete
- `@Immutable`/`@Stable` on all UI state data classes
- `key()` in all LazyRow/LazyVerticalGrid `items {}` blocks
- Compose Compiler Metrics baseline committed
- ktlint + detekt + AndroidLint all green (add to CI)
- No file > 300 lines
- `PreferencesStore` migrated from SharedPreferences to DataStore

---

### Agent 6 — Input Parity, Accessibility, Final Polish

**Owns:**
- D-pad audit of all screens (start-to-finish with D-pad only, no focus traps)
- BACK on Home → confirmation dialog
- Remote hardware keys: MEDIA_PLAY_PAUSE, MEDIA_REWIND, MEDIA_FAST_FORWARD, MEDIA_NEXT, MEDIA_PREVIOUS, CHANNEL_UP/DOWN
- Accessibility: content descriptions, `Modifier.semantics {}`, TalkBack spot-check
- `prefers-reduced-motion` analog: `Settings.Global.ANIMATOR_DURATION_SCALE`
- Overscan safety: 5% margin on all screen edges
- `/polish-artifacts/recordings/` — `adb shell screenrecord` of every major flow
- `/polish-artifacts/` final screenshots

**Depends on:** All other agents (final polish pass)

**Deliverables:**
- Every screen fully D-pad navigable — zero focus traps
- BACK on Home: confirmation dialog
- All media hardware keys wired
- TalkBack working on Android TV emulator
- WCAG AA contrast on detail screen scrims
- Animations respect `ANIMATOR_DURATION_SCALE`
- 5% overscan margin on all screens
- Screen recordings in `/polish-artifacts/recordings/`

---

## Merge Order

```
Agent 1 Sprint 1 (rizzoFocusable + primitives) ──────────────────┐
                                                                ↓
Agent 2 ───┬─── Agent 3 ───┬─── Agent 4 ───┬─── Agent 6 ────────┘
           │               │               │
           └───────────────┴───────────────┘
                     ↓
              Agent 5 sweep
                     ↓
              Agent 6 final polish
                     ↓
              Orchestrator CI gate → POLISH_REPORT.md
```

---

## Flavor Collision Prevention

| Resource | v2 | v3 | v4 |
|----------|----|----|-----|
| App ID | `com.rizzoplayer.iptv.v2` | `com.rizzoplayer.iptv.v3` | `com.rizzoplayer.iptv.v4` |
| DataStore | scoped by package | scoped by package | scoped by package (SAFE) |
| App label | `Rizzo Player` | `Rizzo Player` | `Rizzo IPTV v4` |
| Icon tint | `#2563EB` | `#2563EB` | `#7C3AED` (indigo) |
| Version | 2.0.0 | 3.0.0 | 4.0.0-alpha.1 |

**DataStore files** are scoped by Android package (applicationId), so `.v2`, `.v3`, `.v4` each have isolated stores. No collision.

---

## Build & Install Commands

```bash
# Build v4 debug
./gradlew assembleV4Debug

# Install v4 alongside v3 on device
adb install -r app/build/outputs/apk/v4/debug/app-v4-debug.apk

# Install v3 (if needed)
adb install -r app/build/outputs/apk/v3/debug/app-v3-debug.apk
```

---

## Milestones

| Milestone | Definition |
|-----------|-----------|
| M1 | v4 flavor builds + runs side-by-side with v3 |
| M2 | Agent 1 primitives shipped, all other agents unblocked |
| M3 | Home/Browse/Search fully redesigned with design system |
| M4 | Detail/Favorites/RecentlyWatched complete |
| M5 | Player controls + stream selection overlay redesigned |
| M6 | MainViewModel split complete, Compose perf baseline green |
| M7 | D-pad audit passed, accessibility verified, recordings captured |
| M8 | CI green (ktlint + detekt + AndroidLint), Baseline Profile committed |
| M9 | POLISH_REPORT.md delivered |
