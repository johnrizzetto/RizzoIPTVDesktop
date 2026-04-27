# v4 Polish Report

**Branch:** `v4-polish` | **Commit:** `12fabf6` | **Tags:** `v4-genesis` (fork point), `v4-polish-final`

---

## What Was Built

### Design System — 16 Primitives

| File | Purpose |
|------|---------|
| `RizzoColors.kt` | Color tokens — RizzoIndigo, RizzoGold, RizzoBackground, RizzoSurface, etc. |
| `RizzoTypography.kt` | Type scale — Display, Headline, Title, Body, Label, Caption |
| `RizzoMotion.kt` | Animation specs — tween variants (Gentle, Standard, Quick), spring variants |
| `RizzoElevation.kt` | Elevation tokens — Level 0–5 with corresponding shadows |
| `RizzoRadii.kt` | Corner radius tokens |
| `RizzoSpacing.kt` | Spacing scale (4, 8, 12, 16, 24, 32, 48dp) |
| `rizzoFocusable.kt` | D-pad `focusable` modifier with `FocusedBorder` composable |
| `rizzoFocusGroup.kt` | `Modifier` extension — replaces `gridTopRowFocusRestorer()` + `focusKeyboardNavigation()` on LazyGrids/LazyRows |
| `RizzoCard.kt` | Poster card with info section |
| `RizzoPoster.kt` | Poster image with shimmer loading |
| `RizzoSkeleton.kt` | `RizzoSkeletonCard` + `RizzoSkeletonRow(count, posterRatio)` |
| `RizzoRow.kt` | Horizontal row with D-pad focus navigation |
| `RizzoGrid.kt` | Adaptive grid with focus management |
| `RizzoText.kt` | Text variants — Display, Headline, Title, Body |
| `RizzoIcon.kt` | Vector icon wrapper |
| `RizzoIconButton.kt` | Icon button with D-pad support |
| `RizzoButton.kt` | Primary / Secondary / Destructive / Text / Ghost variants, uses `text` param |
| `RizzoChip.kt` | Filter / Choice / Input / Dismiss variants, uses `selected` boolean |
| `RizzoDialog.kt` | Alert / Confirm / Error dialogs, `tween()` animations |
| `RizzoBottomSheet.kt` | Bottom sheet |
| `RizzoTextField.kt` | Text input with D-pad support |
| `RizzoToast.kt` | Success / Error / Info toast with progress bar, `tween()` animations |
| `RizzoProgressBar.kt` | Indeterminate (shimmer) + determinate variants |
| `DESIGN_SYSTEM.md` | Full documentation with color table, motion specs, component catalog |

### Home/Browse Polish

- `MoviesHome.kt` — shimmer replaced with `RizzoSkeletonRow(count=8, posterRatio=0.667f)`; grid uses `rizzoFocusGroup()`
- `SeriesHome.kt` — same shimmer + grid treatment
- `ContinueWatchingStrip.kt` — `LinearProgressIndicator` → `RizzoProgressBar`; LazyRow uses `rizzoFocusGroup()`
- `TmdbSearchResultsContent.kt` — trimmed from 308 → 207 lines; shimmer → `RizzoSkeletonRow`; `LinearProgressIndicator` → `RizzoProgressBar`
- `HomeScreen.kt` — unchanged (still 308 lines, was not trimmed)

### Detail Polish

- `TmdbDetailScreen.kt` — redesigned: `RizzoText` for title/tagline/overview, `RizzoButton` for Play, `RizzoIconButton` for favorite toggle

### Stream Overlay Polish

- `StreamSelectionOverlay.kt` — `LazyColumn` now uses `rizzoFocusGroup()` for D-pad traversal

### Code Quality

- `@Immutable` added to all remaining data classes in `Models.kt` (was partially applied): `ServerConfig`, `SeriesInfo`, `VodInfo`, `MovieData`, `EpgResponse`, `PlayEvent`
- `TmdbModels.kt` was already fully annotated
- `ktlint` configured in `build.gradle.kts` and passing (`ignoreFailures = false`)
- `ProGuard/R8` active (`isMinifyEnabled = true`)

### v4 Flavor

- `appIdSuffix = ".v4"` → `com.rizzoplayer.iptv.v4`
- `versionName = "4.0.0-alpha.1"`, `versionCode = 4`
- Indigo adaptive icon (`#7C3AED`) for v4
- "Rizzo IPTV v4" label in `v4/res/values/strings.xml`

---

## Build Status

```
./gradlew assembleV4Debug  →  BUILD SUCCESSFUL in ~17s
./gradlew ktlintCheck      →  BUILD SUCCESSFUL (0 errors)
```

APK: `app/build/outputs/apk/v4/debug/app-v4-debug.apk` (~21MB)

---

## v4 Flavor Coexists with v3

| | Package | Version |
|---|---|---|
| v3 | `com.rizzoplayer.iptv.v3` | 3.0.0 |
| v4 | `com.rizzoplayer.iptv.v4` | 4.0.0-alpha.1 |

Both can be installed on the same device simultaneously.

---

## Git History

```
12fabf6  v4 polish: design system tokens, rizzoFocusGroup, @Immutable, ktlint
d174734  feat: shimmer skeleton loading for TMDB grid and search screens
c0cd9c0  feat: search performance + scroll position restoration
0f5cc8c  fix(ci): trigger on work branch pushes
6d7598a  fix(player): raise live maxBuffer to 12s
```

**Tags:** `v4-genesis` (fork point) ← `v4-polish-final` (this commit)

---

## Key Decisions

1. **`spring()` type inference** — `spring()` returns `AnimationSpec<Float>`. Use `spring<Float>()` with explicit type param for `animateFloatAsState`.

2. **`AnimatedVisibility` specs** — Use `tween()` (finite) not spring specs. `RizzoMotion.GentleSpring` is `FiniteAnimationSpec<Float>` and CAN be used with `AnimatedVisibility`.

3. **`rizzoRow()` is a Composable** — Not a modifier. Use `RizzoSkeletonRow(count, posterRatio, modifier)` to replace shimmer rows.

4. **`RizzoButton` uses `text`** — Not `label`. `RizzoChip` uses `selected` boolean, not a Filter variant. `RizzoIconButton` has no `variant` param.

5. **`RizzoMotion.GentleSpring` incompatible with `animateFloatAsState`** — Use `tween()` or `spring<Float>()` instead.

6. **ktlint version conflict** — The plugin in `libs.versions.toml` is `12.1.1`. Removing `version.set("12.1.1")` from the config block resolved the resolution error.

---

## What's NOT Done

These were scoped to future work:

- **Agent 6** — D-pad audit + TalkBack accessibility pass
- **Agent 5** — MainViewModel split (owned by Agent 5, not yet started)
- **SearchBar** — new design system component for search input with D-pad support
- **HomeScreen.kt trim** — still 308 lines (target was ≤300)
- **PlayerOverlay polish** — player UI design system pass not yet done
- **StreamSelectorDialog** — design system integration not yet done
- **AGENT_PLAN.md** — some agent plan files are stale or were created from context compression summaries, not fresh plans
