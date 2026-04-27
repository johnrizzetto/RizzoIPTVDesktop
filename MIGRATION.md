# RizzoIPTVPlayer v4 — Migration Guide

**From:** v3 (`com.rizzoplayer.iptv.v3`) | **To:** v4 (`com.rizzoplayer.iptv.v4`)

---

## What Changed for the User

- **App name:** Now shows "Rizzo IPTV v4" on the home row (vs "Rizzo Player" for v2/v3)
- **Icon tint:** Indigo (`#7C3AED`) instead of blue (`#2563EB`) — easy visual distinguish
- **Package:** `com.rizzoplayer.iptv.v4` — fully isolated from v3
- **DataStore:** Completely separate from v3 (different applicationId = different files)
- **Settings/credentials:** Not shared with v3 — log in again on first launch
- **Favorites:** Not shared with v3 — start fresh
- **Playback history:** Not shared with v3

---

## What Changed for the Developer

### Flavor Setup
```kotlin
// build.gradle.kts
create("v4") {
    dimension = "version"
    applicationIdSuffix = ".v4"
    versionName = "4.0.0-alpha.1"
    versionCode = 4
}
```

### v4 Resource Overrides
```
app/src/v4/res/values/strings.xml          → app_name = "Rizzo IPTV v4"
app/src/v4/res/mipmap-*/ic_launcher.xml    → foreground color #7C3AED
```

### DataStore Files
All stores remain at the same logical names; Android scopes by package automatically.

### SharedPreferences → DataStore Migration (PreferencesStore)
`PreferencesStore` migrated from `SharedPreferences("app_prefs")` to `DataStorePreferences("preferences")`. No user action needed on upgrade (prefs are app-only).

### MainViewModel Split (Breaking for downstream consumers)
If any external code references `MainViewModel`'s internal classes directly, these may need updating:
- `StreamSelectionState` — still exists but may have changed fields
- `TmdbStreamSelectionState` — still exists but may have changed fields
- `PlaybackPrep` — moved to `PlaybackViewModel`

**Internal APIs only** — `MainUiState` remains the public state contract.

### Design System Primitives
New: `Modifier.rizzoFocusable()`, `RizzoCard`, `RizzoPoster`, `RizzoRow`, etc. All existing `tvClickable()` usages should migrate to `rizzoFocusable()` (both still work; `tvClickable` is a bridge).

---

## Compatibility

| Feature | v3 | v4 |
|---------|----|----|
| TorBox API | ✓ | ✓ (same) |
| TMDB API | ✓ | ✓ (same) |
| Torrentio | ✓ | ✓ (same) |
| ExoPlayer version | ✓ | ✓ (same) |
| IPTV / M3U | ✓ | ✓ (same) |
| DataStore | ✓ | ✓ (new files) |
| SharedPreferences | ✓ | ✗ (migrated) |
| v3 APK on same device | ✓ | ✓ (coexist) |
