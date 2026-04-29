# Rizzo Player v5 — Handoff: Stream Resolution & Design System Uplift

**Date:** 2026-04-28
**Current Branch:** `v5/main`
**Commit:** `65f47c8` (plus uncommitted hotfixes)
**Device:** XR16A - 14 (Projector)

---

## 1. Major Fixes: Stream Resolution (TorBox + Torrentio)

### Root Cause of "No Valid Torrent Found"
The app previously failed to resolve many Torrentio links because:
1. **Parsing Mismatch:** Torrentio returns `infoHash` (camelCase), but the app's global `JsonNamingStrategy.SnakeCase` was looking for `info_hash`.
2. **Missing Hashes:** Some Torrentio links (debrid gateway URLs) didn't include the hash in the JSON at all.
3. **API Rejection:** TorBox's `addMagnet` API often rejected Torrentio's proxy URLs.

### Fixed Integration Logic
- **Robust Hash Extraction:** Updated `TorBoxRepository.parseInfoHash` to aggressively extract the SHA-1 or Base32 hash directly from the Torrentio URL structure if missing from the JSON fields.
- **Manual Magnet Synthesis:** The app now **manually synthesizes a clean magnet link** (`magnet:?xt=urn:btih:HASH`) for all Torrentio results. This ensures TorBox receives a format it reliably understands.
- **TorBox API Parsing:** Rewrote `getTorrentInfo`, `checkCached`, and `requestDownloadLink` to use `JsonElement` mapping. The previous `Map<String, Any>` approach was failing to correctly parse `id` fields (doubles vs ints) and boolean strings from the TorBox API.
- **Search API Resilience:** Fixed a crash in TorBox Search where the `files` field would return `0` (Int) instead of an `Array`. It is now handled as a `JsonElement`.

---

## 2. UI Uplift: Phase 2 Design System

### Canonical Focus System (`rizzoFocusable`)
We have completely removed the legacy `tvClickable` shim and manual `MutableInteractionSource` scale/border boilerplate.
- **Global Migration:** `MoviesHome`, `SeriesHome`, `Sidebar`, `Settings`, `StreamPicker`, and `Favorites` now all use `Modifier.rizzoFocusable()`.
- **Phase 2 Specs:** Built-in defaults now automatically apply:
  - **Focus Scale:** 1.06x
  - **Focus Border:** 4.dp (Color: `AccentBlue` or `RizzoAccent`)
  - **Focus Shadow:** 8.dp elevation
- **Interactive State:** Added `onLongClick` and `interactionSource` support to `rizzoFocusable` to allow components to respond to highlight states (e.g., changing text color when the row is focused).

---

## 3. Metadata & Polish

### Tag Detection Overhaul
- **Robust Regex:** `TorrentioParser` now uses case-insensitive regexes and supports standard lowercase formats (e.g., `4k`, `dv`, `hdr`).
- **Badge Visibility:** Fixed the `QualityBadge` and `VideoTechBadge` components to correctly display `4K`, `DV`, `HDR`, and `1080p` tags on stream cards.
- **Unified Modeling:** `UnifiedTorrent` now deduplicates results from TorBox and Torrentio more accurately by normalizing hashes to lowercase.

### Stability & Cancellation
- **Cancellation Audit:** Fixed "leaky" coroutines by ensuring `playbackPrepJob?.cancel()` is called in all dismissal paths (`goBack`, `dismissStreamPicker`, etc.).
- **Exception Handling:** Audited all `catch (e: Exception)` blocks in `TorBoxRepository` and `MainViewModel` to immediately rethrow `CancellationException`, preventing silent D-pad "hangs" during navigation.

---

## 4. Current Status & Next Steps

### Validation
- **Build:** `./gradlew :app:assembleV5Debug` passes.
- **Lint:** `./gradlew ktlintCheck` passes.
- **Deploy:** Successfully pushed to projector via `installV5Debug`.

### Immediate Next Steps (Phase 2 Feature Work)
1. **Split HomeScreen:** The `HomeScreen.kt` file is still too large. It should be split into dedicated screens for `MoviesHome`, `SeriesHome`, and `Search`.
2. **Hero Auto-Rotation:** Implement the timer-based hero banner rotation in `MoviesHome`.
3. **Episode Picking:** Verify that the now-wired `StreamPickerScreen` handles season/episode transitions smoothly.
4. **TorBox Search Errors:** Monitor logs for `Unexpected JSON token` errors; though `files` is fixed, the API format is occasionally inconsistent.
