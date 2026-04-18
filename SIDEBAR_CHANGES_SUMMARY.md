# Sidebar Navigation Overhaul — Verification Report

## Files edited

- `app/src/main/java/com/rizzoplayer/iptv/ui/theme/Color.kt`
- `app/src/main/java/com/rizzoplayer/iptv/ui/screens/HomeScreen.kt`

## Change 1 — Color.kt tokens

**File:** `Color.kt`

| Token | Old value | New value |
|---|---|---|
| `NavActive` | `Color(0xFF10102C)` | `Color(0xFF0D0D2E)` |
| `NavHover` | `Color(0xFF0C0C20)` | `Color(0xFF141430)` |
| `NavFocusBg` | does not exist | `Color(0xFF1A1A40)` ← **NEW** |

All other values (`MainBg`, `SidebarBg`, `AccentBlue`, `TextPrimary`, `RedColor`, etc.) are unchanged.

---

## Change 2 — HomeScreen.kt imports added

**File:** `HomeScreen.kt` — section lines ~64–67

Three imports were added alongside existing imports:
```kotlin
import androidx.compose.ui.draw.scale          // ← for animateFloatAsState scale effect
import androidx.compose.material3.AlertDialog   // ← for logout confirmation dialog
import androidx.compose.material3.TextButton    // ← for dialog confirm/dismiss buttons
```

---

## Change 3 — Sidebar width animation: tween → spring

**File:** `HomeScreen.kt` — `HomeScreen.kt` composable

Old:
```kotlin
animationSpec = tween(150, easing = FastOutSlowInEasing),
```
New:
```kotlin
animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy),
```

Also added trailing comma after `label = "sidebar"`.

---

## Change 4 — Focus restoration with FocusRequester

**File:** `HomeScreen.kt` — `HomeScreen` composable, `Sidebar` call site

Added `val navFocusRequesters = remember { NAV_ENTRIES.map { FocusRequester() } }` and updated the `Sidebar(...)` call to pass `navFocusRequesters` and a smarter `onFocusEnter` that restores focus to the active section item:

```kotlin
onFocusEnter = {
 sidebarExpanded = true
 val idx = NAV_ENTRIES.indexOfFirst { it.section == state.section }
 if (idx >= 0) navFocusRequesters[idx].requestFocus()
},
```

---

## Change 5 — Sidebar composable rewritten

**File:** `HomeScreen.kt` — the `Sidebar` composable replaced entirely.

Key new behavior:
- New parameter: `navFocusRequesters: List<FocusRequester>`
- New state: `showLogoutDialog by remember { mutableStateOf(false) }`
- AlertDialog shown when `showLogoutDialog = true`:
  - Title: `"Sign Out?"`
  - Body: `"You will need to re-enter your server credentials."`
  - "Sign Out" button → calls `onLogout()` (red text)
  - "Cancel" button → dismisses dialog
- `NAV_ENTRIES.forEach` loop changed to `forEachIndexed` to pass `navFocusRequesters[idx]`
- `SidebarNavItem` for Sign Out now passes `danger = true` and `onClick = { showLogoutDialog = true }`
- Added `Box(Modifier.fillMaxWidth().height(1.dp).background(BorderColor.copy(alpha = 0.4f)))` separator before Sign Out
- Changed `focusRequester` parameter added to each `SidebarNavItem` call
- Added `onClick = onBack` passed as a named parameter (was positional)

---

## Change 6 — SidebarNavItem composable rewritten

**File:** `HomeScreen.kt` — the `SidebarNavItem` composable replaced entirely.

Key new behavior:
- New parameters: `focusRequester: FocusRequester = remember { FocusRequester() }` and `danger: Boolean = false`
- Added scale animation:
  ```kotlin
  val scale by animateFloatAsState(
   targetValue = if (focused) 1.04f else 1f,
   animationSpec = spring(
    stiffness = Spring.StiffnessMediumLow,
    dampingRatio = Spring.DampingRatioNoBouncy,
   ),
   label = "navScale",
  )
  ```
- Updated background logic: includes `danger && focused -> Color(0xFF2A0A0A)`
- Updated icon/text color logic: `danger && focused -> RedColor`, `danger -> TextMuted.copy(alpha = 0.5f)`
- Added `.scale(scale)` modifier to the Row
- Added conditional border:
  ```kotlin
  .then(
   if (focused) Modifier.border(
    1.dp,
    if (danger) RedColor.copy(alpha = 0.45f)
    else AccentBlue.copy(alpha = 0.45f),
    RoundedCornerShape(6.dp)
   )
   else Modifier
  )
  ```
- Added `.focusRequester(focusRequester)` to the modifier chain
- Accent bar changed from 2dp×16dp to 3dp×20dp
- Label fontWeight changed from `FontWeight.SemiBold` to `FontWeight.Bold`

---

## Verification checklist for a reviewing model

```bash
# 1. Color.kt — verify the 3 changed/added tokens
grep -n "NavActive\|NavHover\|NavFocusBg" \
  app/src/main/java/com/rizzoplayer/iptv/ui/theme/Color.kt

# Expected output (line numbers may vary):
# val NavActive = Color(0xFF0D0D2E)
# val NavHover = Color(0xFF141430)
# val NavFocusBg = Color(0xFF1A1A40)

# 2. Imports present
grep -n "draw.scale\|AlertDialog\|TextButton" \
  app/src/main/java/com/rizzoplayer/iptv/ui/screens/HomeScreen.kt

# 3. Spring animation on sidebar width
grep -n "StiffnessMedium\|DampingRatioNoBouncy" \
  app/src/main/java/com/rizzoplayer/iptv/ui/screens/HomeScreen.kt | head -5

# 4. FocusRequester per nav item
grep -n "navFocusRequesters\|FocusRequester()" \
  app/src/main/java/com/rizzoplayer/iptv/ui/screens/HomeScreen.kt | grep -v "firstItem\|cancelFocus\|firstFocus"

# 5. showLogoutDialog and AlertDialog
grep -n "showLogoutDialog\|AlertDialog\|Sign Out?" \
  app/src/main/java/com/rizzoplayer/iptv/ui/screens/HomeScreen.kt

# 6. Scale animation in SidebarNavItem
grep -n "animateFloatAsState\|navScale\|\.scale(" \
  app/src/main/java/com/rizzoplayer/iptv/ui/screens/HomeScreen.kt

# 7. Danger style
grep -n "danger\|0xFF2A0A0A\|RedColor.copy" \
  app/src/main/java/com/rizzoplayer/iptv/ui/screens/HomeScreen.kt

# 8. NO tween(150) remaining
grep -n "tween(150" app/src/main/java/com/rizzoplayer/iptv/ui/screens/HomeScreen.kt && echo "FAIL: tween still present" || echo "PASS: no tween(150)"
```

## Build compile check

```bash
cd /Users/johnrizzetto/RizzoIPTVPlayer
./gradlew assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` with no Kotlin compilation errors.