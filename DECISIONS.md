# RizzoIPTVPlayer v4 — Decisions Log

**Branch:** `v4-polish` | **Started:** 2026-04-23

---

## ADR-001: v4 Fork Strategy

**Date:** 2026-04-23

**Decision:** Fork from `claude/romantic-leakey-57e175-work` (current v3 HEAD) into `v4-polish` branch. Tag `v4-genesis` at fork point.

**Rationale:** v3 is actively developed; v4 polish work must not disrupt v3 shipping. Separate branch + tag provides safety net. No force-push to `v4-genesis`.

---

## ADR-002: Compose BOM Version

**Date:** 2026-04-23

**Decision:** Keep BOM `2024.05.00`. Do not bump unless an agent has a concrete compatibility reason.

**Rationale:** Stability over novelty. v3 works with this BOM; v4 inherits the same stack.

---

## ADR-003: DI Framework — No Introduction

**Date:** 2026-04-23

**Decision:** Do not introduce Hilt, Koin, or any DI framework.

**Rationale:** Manual constructor injection is already in place. Introducing DI would require rewriting the entire app's object graph. Clean up what exists; don't replace it.

---

## ADR-004: Design System Brand Color

**Date:** 2026-04-23

**Decision:** v4 brand accent color = `#7C3AED` (indigo). v2/v3 use `#2563EB` (blue). Distinct tint differentiates v4 from v3 on the home row.

**Implementation:** `RizzoColors.kt` (Agent 1 owns). v4 adaptive icon foreground color = `#7C3AED`.

---

## ADR-005: Shared Element Transitions

**Date:** 2026-04-23

**Decision:** Use crossfade + scale for poster → detail transitions. NOT SharedTransitionLayout.

**Rationale:** Compose `SharedTransitionLayout` requires Compose 1.7+. Current BOM is `2024.05.00` (Compose 1.6.x). Document as a future upgrade path when BOM bumps to 1.7+.

---

## ADR-006: PreferencesStore Migration

**Date:** 2026-04-23

**Decision:** Migrate `PreferencesStore` from SharedPreferences to DataStore.

**Rationale:** All other stores use DataStore. SharedPreferences is the only outlier, inconsistent and lacks coroutine-friendly API. Migration needed before v4 ships.

---

## ADR-007: MainViewModel Split

**Date:** 2026-04-23

**Decision:** Split MainViewModel (709 lines) into:
- `MainViewModel` — thin coordinator, combined MainUiState
- `PlaybackViewModel` — stream resolution, PlaybackPrep, PlaybackPositionStore
- `EpgViewModel` — EPG loading and refresh

**Rationale:** 4 distinct responsibilities in one ViewModel violates single-responsibility. Hard to test, hard to modify. Agent 5 owns this.

---

## ADR-008: No XML Layouts

**Date:** 2026-04-23

**Decision:** Compose only. No new XML layouts.

**Rationale:** App already uses Compose for all UI. No hybrid approach.

---

## ADR-009: Baseline Profile

**Date:** 2026-04-23

**Decision:** Generate a baseline profile for cold start → Home render. Add macrobenchmark module.

**Rationale:** No baseline exists. v4 changes may affect startup; having a baseline proves no regression.

---

## ADR-010: v4 Package Isolation

**Date:** 2026-04-23

**Decision:** v4 package = `com.rizzoplayer.iptv.v4`. DataStore files are scoped by Android package automatically.

**Rationale:** Android's DataStore uses the app's package as file scope. `.v4` suffix on applicationId means different file paths for each flavor. No manual DataStore name changes needed.

---

*Append new ADRs above this line. Format: ADR-NNN: Title*
