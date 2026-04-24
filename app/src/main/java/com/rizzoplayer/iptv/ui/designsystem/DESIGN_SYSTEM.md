# RizzoIPTV v4 — Design System

**Branch:** `v4-polish` | **Owner:** Agent 1 | **Status:** Complete

---

## Overview

RizzoIPTV is an Android TV app viewed at 3–5 meter viewing distance. Every design decision prioritises legibility and D-pad navigability at that range. The design system provides a set of reusable tokens and components that enforce a consistent visual language and guarantee focus-safety across all screens.

---

## Color Tokens (`ui/theme/RizzoColors.kt`)

### Brand

| Token | Value | Use |
|---|---|---|
| `RizzoIndigo` | `#7C3AED` | Primary accent, focused state glow rings (v4 only) |
| `RizzoIndigoLight` | `#9F67FF` | Hover/focus highlights |
| `RizzoIndigoDark` | `#5B21B6` | Pressed states |

### Surfaces

| Token | Value | Use |
|---|---|---|
| `RizzoBackground` | `#0A0A0F` | App background |
| `RizzoSurface` | `#14141F` | Sidebar, panels |
| `RizzoSurfaceVariant` | `#1E1E2E` | Cards, chips |
| `RizzoSurfaceElevated` | `#252538` | Dialogs, bottom sheets |

### Text

| Token | Value | Use |
|---|---|---|
| `RizzoTextPrimary` | `#F5F5F7` | Headings, body |
| `RizzoTextSecondary` | `#A1A1AA` | Subtitles, metadata |
| `RizzoTextTertiary` | `#71717A` | Placeholders, disabled |
| `RizzoTextDisabled` | `#52525B` | Fully disabled text |

### Semantic

| Token | Value | Use |
|---|---|---|
| `RizzoSuccess` | `#22C55E` | Stream ready, positive actions |
| `RizzoWarning` | `#F59E0B` | Stream queuing, caution |
| `RizzoError` | `#EF4444` | Stream failed, destructive actions |
| `RizzoInfo` | `#3B82F6` | Informational toasts |

### Stream States

| Token | Value | Use |
|---|---|---|
| `RizzoStreamSearching` | `#F59E0B` | Searching for streams |
| `RizzoStreamQueuing` | `#3B82F6` | Waiting in queue |
| `RizzoStreamCaching` | `#8B5CF6` | Downloading/caching |
| `RizzoStreamReady` | `#22C55E` | Ready to play |
| `RizzoStreamFailed` | `#EF4444` | Failed to load |

### Skeleton / Shimmer

| Token | Value |
|---|---|
| `RizzoSkeletonBase` | `#1E1E2E` |
| `RizzoSkeletonHighlight` | `#2A2A3E` |

### Backward-Compatibility Aliases

All aliases marked `@deprecated` map old `Color.kt` names to the new token set. Prefer the new `Rizzo*` names. Aliases are provided only for incremental migration.

---

## Typography (`ui/theme/RizzoTypography.kt`)

**Principle:** Minimum 16sp body text. 18–20sp preferred for primary content. Headings 28–48sp.

Typography uses Material3 `Typography` as the base. Key sizes:

| Style | Size | Weight | Use |
|---|---|---|---|
| `displayLarge` | 48sp | Black | Hero titles |
| `displayMedium` | 40sp | Bold | Movie titles on detail |
| `displaySmall` | 32sp | Bold | Section headers |
| `headlineLarge` | 28sp | Bold | Screen titles |
| `headlineMedium` | 24sp | SemiBold | Card titles |
| `titleLarge` | 20sp | SemiBold | Row headers |
| `titleMedium` | 18sp | Medium | Sub-headings |
| `bodyLarge` | 18sp | Normal | Primary body text |
| `bodyMedium` | 16sp | Normal | Standard body (minimum) |
| `labelLarge` | 16sp | Medium | Chips, badges |
| `labelMedium` | 14sp | Medium | Secondary labels |

---

## Spacing (`ui/theme/RizzoSpacing.kt`)

```
Xxs = 2.dp   // Micro gaps
Xs  = 4.dp   // Icon-to-text gaps
Sm  = 8.dp   // Default padding
Md  = 12.dp  // Card padding
Lg  = 16.dp  // Screen margins
Xl  = 24.dp  // Section gaps
Xxl = 32.dp  // Large section gaps
```

---

## Radii (`ui/theme/RizzoRadii.kt`)

```
Xs   = 4.dp   // Chips, badges
Sm   = 8.dp   // Buttons, cards, text fields
Md   = 12.dp  // Large cards, posters
Lg   = 16.dp  // Modals, bottom sheets
Xl   = 24.dp  // Large panels
Full = 999.dp // Circular (icon buttons)
```

Shape accessors: `RizzoRadii.SmShape`, `RizzoRadii.MdShape`, `RizzoRadii.LgShape`

---

## Motion (`ui/theme/RizzoMotion.kt`)

### Spring Specs

All springs use `AnimationSpec<Float>` and are suitable for use with `animateFloatAsState`, `scaleIn`, `scaleOut`, etc.

| Name | dampingRatio | stiffness | Use |
|---|---|---|---|
| `RizzoMotion.DefaultSpring` | `DampingRatioLowBouncy` | `StiffnessMediumLow` | Card/item focus |
| `RizzoMotion.SnappySpring` | `DampingRatioMediumBouncy` | `StiffnessMedium` | Buttons, chips |
| `RizzoMotion.GentleSpring` | `DampingRatioLowBouncy` | `StiffnessLow` | Modals, sheets |

### Durations (ms)

```
DurationInstant = 0
DurationFast    = 100   // Icon press
DurationNormal  = 200   // Default
DurationSlow    = 350   // Cards, rows
DurationEnter   = 400   // Content appearing
DurationExit    = 250   // Content leaving
```

### Scale Constants

```
FocusScale  = 1.04f  // On focus
PressScale  = 0.95f  // On press
```

---

## Elevation (`ui/theme/RizzoElevation.kt`)

Uses `Modifier.shadow` with `RizzoIndigo.copy(alpha=0.25f)` for glow effects.

| Name | Elevation |
|---|---|
| `RizzoElevation.None` | 0.dp |
| `RizzoElevation.Low` | 2.dp |
| `RizzoElevation.Medium` | 4.dp |
| `RizzoElevation.High` | 8.dp |

---

## Components

### `Modifier.rizzoFocusable()` (`rizzoFocusable.kt`)

**The canonical D-pad focus modifier for RizzoIPTV.** All focusable components should use this.

```kotlin
Modifier.rizzoFocusable(
    onClick: () -> Unit,
    shape: Shape = RoundedCornerShape(8.dp),
    focusBorderWidth: Dp = 2.dp,
    focusBorderColor: Color = RizzoAccent,
    focusBackgroundColor: Color = RizzoAccentDim,
)
```

**What it does:**
- 2.dp `RizzoAccent` border ring on focus (visible at TV distance)
- Subtle `RizzoAccentDim` tint background on focus
- Scale-up on focus (1.04x), scale-down on press (0.95x) via `RizzoMotion.DefaultSpring`
- Uses `MutableInteractionSource` for correct hot-observable focus tracking
- No ripple (TV convention — ripple is phone idiom)

**Programmatic focus:**
```kotlin
val focusRequester = remember { FocusRequester() }
Modifier.rizzoFocusableWithRequester(focusRequester = focusRequester, onClick = { ... })
// Later:
focusRequester.requestFocus()
```

### `RizzoCard` (`RizzoCard.kt`)

Focusable card primitive. Wraps `rizzoFocusable` with poster/content slot.

### `RizzoPoster` (`RizzoPoster.kt`)

Poster image with gradient scrim. Aspect ratios: `ASPECT_2_3`, `ASPECT_16_9`, `ASPECT_1_1`.

### `RizzoRow` (`RizzoRow.kt`)

Focus-aware `LazyRow` with snap-to-item and `rizzoRow()` extension. Use for horizontal content rows.

### `RizzoGrid` (`RizzoGrid.kt`)

Focus-aware `LazyVerticalGrid` with `rizzoGrid()` extension. Use for category grids.

### `RizzoChip` (`RizzoChip.kt`)

D-pad-selectable chip/tag. States: default, focused, selected, disabled.

```kotlin
RizzoChip(label = "Action", selected = true, onClick = { ... })
```

### `RizzoButton` (`RizzoButton.kt`)

D-pad-navigable button with spring scale. Variants: `Primary`, `Secondary`, `Text`.

```kotlin
RizzoButton(text = "Play", onClick = { play() }, variant = RizzoButtonVariant.Primary)
RizzoTextButton(text = "Cancel", onClick = { cancel() })
```

### `RizzoIconButton` (`RizzoIconButton.kt`)

Circular icon-only button. Sizes: default 48.dp, `RizzoIconButtonSmall` 36.dp.

```kotlin
RizzoIconButton(
    icon = Icons.Default.Close,
    onClick = { close() },
    contentDesc = "Close",
)
```

### `RizzoTextField` (`RizzoTextField.kt`)

TV-safe text input with D-pad navigation, accent border on focus.

```kotlin
RizzoTextField(
    value = query,
    onValueChange = { query = it },
    onSubmit = { search() },
    label = "Search",
    placeholder = "Movies, shows...",
)
```

### `RizzoDialog` (`RizzoDialog.kt`)

Modal dialog with backdrop dim. D-pad navigable, scale+fade enter/exit.

```kotlin
RizzoDialog(
    visible = showDialog,
    onDismiss = { showDialog = false },
    title = "Exit?",
    body = "Are you sure you want to exit?",
) {
    RizzoButton(text = "Cancel", onClick = { showDialog = false })
    RizzoButton(text = "Exit", onClick = { exit() })
}
```

Use `TwoActionDialog` for standard confirm/cancel dialogs.

### `RizzoBottomSheet` (`RizzoBottomSheet.kt`)

Slides up from bottom. Drag handle, dim backdrop (dismissible).

```kotlin
RizzoBottomSheet(
    visible = showSheet,
    onDismiss = { showSheet = false },
) {
    // Sheet content
}
```

### `RizzoToast` (`RizzoToast.kt`)

Ephemeral bottom-center notification. Auto-dismisses. Types: `Success`, `Error`, `Info`.

```kotlin
var toastVisible by remember { mutableStateOf(false) }
RizzoToast(
    message = "Stream ready!",
    type = RizzoToastType.Success,
    visible = toastVisible,
    onDismiss = { toastVisible = false },
)
```

### `RizzoProgressBar` (`RizzoProgressBar.kt`)

Determinate (0f..1f progress) or indeterminate (animated shimmer).

```kotlin
RizzoProgressBar(progress = 0.65f)           // Determinate
RizzoProgressBar()                            // Indeterminate
```

### `RizzoSkeleton` (`RizzoSkeleton.kt`)

Shimmer skeleton loader. Wraps any content with shimmer effect.

### `RizzoText` (`RizzoText.kt`)

Typed text composable for consistent Material3 typography usage.

### `RizzoIcon` (`RizzoIcon.kt`)

Icon wrapper with consistent sizing and tinting.

### `RizzoSpacer` (`RizzoSpacer.kt`)

Spacing utilities for consistent layout rhythm.

---

## D-pad Interaction Patterns

### Focus Ring

All interactive elements must show a 2.dp `RizzoAccent` border ring when focused. This is non-negotiable for TV usability — without it, users cannot see which element is selected.

### No Ripple

**Never use ripple on TV.** Ripple is a phone/tablet interaction idiom. On TV, use spring scale feedback (`rizzoFocusable` handles this automatically).

### Up Navigation

When focus reaches the top of a `LazyRow` or `LazyVerticalGrid`, pressing Up should move focus to the previous screen's last focused item or to a navigation chrome element (e.g., sidebar). Use `focusRequester` to implement this.

### Sidebar Navigation

When focus is at the left edge of the content area, pressing Left should move focus to the sidebar. Ensure the sidebar has a `FocusRequester` and the content area has a `focusProperties { previous() }` link.

### Skip Padding

Add `Modifier.focusRequester` and `focusProperties { previous() }` to skip decorative or non-interactive elements (logos, dividers, empty spaces).

---

## Backward Compatibility

The existing `Color.kt` tokens (`BrandGold`, `AccentBlue`, `TextPrimary`, etc.) are still used throughout the existing codebase. The design system does NOT require immediate migration of all call sites — it provides aliases in `RizzoColors.kt` to avoid breaking existing code.

**Migration path:** Replace old tokens with `Rizzo*` equivalents one screen at a time. The alias layer means no screen will break during migration.

---

## Migration Checklist

When updating a screen to use the design system:

- [ ] Replace `Color.*` with `RizzoColors.*` tokens
- [ ] Replace custom `Modifier.scale(...)` with `rizzoFocusable`
- [ ] Replace shimmer implementation with `RizzoSkeleton`
- [ ] Replace category rows with `RizzoRow` / `rizzoRow()`
- [ ] Replace search bar with `RizzoTextField`
- [ ] Replace generic buttons with `RizzoButton` / `RizzoTextButton`
- [ ] Verify all interactive elements have a visible focus ring
- [ ] Run full D-pad walkthrough with no focus traps
