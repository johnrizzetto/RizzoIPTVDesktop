# Rizzo Player — Android / Google TV

A clean, open-source IPTV player for Google TV and Android TV built with Kotlin + Jetpack Compose.
Supports any **Xtream Codes**-compatible IPTV service.

## Features
- Login screen — no hardcoded credentials, works with any Xtream Codes server
- Live TV, Movies, TV Shows with category browsing
- Favorites (heart icon on any stream)
- EPG (now/next program info for live channels)
- Search within any category
- HLS playback via ExoPlayer (Media3)
- D-pad / remote navigation optimized for TV

## Requirements

| Tool | Version |
|---|---|
| Android Studio | Hedgehog (2023.1.1) or later |
| Android Gradle Plugin | 8.4.0 |
| Kotlin | 1.9.23 |
| Min SDK | 23 (Android 6.0) |
| Target SDK | 34 (Android 14) |

## Build & Deploy

### From Android Studio
1. Open the `RizzoIPTVPlayer` folder in Android Studio.
2. Let Gradle sync finish.
3. Connect your Google TV / Android TV device via ADB (Settings → Device Preferences → Developer Options → ADB debugging ON, then run `adb connect <TV_IP>`).
4. Select your device in the run target dropdown and hit **Run**.

### Command line
```bash
# debug APK
./gradlew assembleDebug

# install directly to connected ADB device
./gradlew installDebug
```

### Sideloading the APK on Google TV
1. Build: `./gradlew assembleRelease` (or Debug for testing)
2. Transfer the APK from `app/build/outputs/apk/` to a USB drive or use ADB:
   ```bash
   adb connect <TV_IP_ADDRESS>
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```
3. The app will appear in **Apps → See all apps** on Google TV.

## Project Structure

```
app/src/main/java/com/rizzoplayer/iptv/
├── MainActivity.kt                  # Entry point, login/home routing
├── data/
│   ├── api/IPTVApiService.kt        # Xtream Codes HTTP API
│   ├── local/CredentialsStore.kt    # Server credentials (DataStore)
│   ├── local/FavoritesStore.kt      # Favorites persistence (DataStore)
│   ├── model/Models.kt              # Data classes
│   └── repository/IPTVRepository.kt # Data layer with caching
└── ui/
    ├── player/PlayerActivity.kt     # Full-screen ExoPlayer
    ├── screens/LoginScreen.kt       # Credential entry screen
    ├── screens/HomeScreen.kt        # Main browsing UI
    ├── theme/Color.kt               # Color palette
    ├── theme/Theme.kt               # Material 3 dark theme
    └── viewmodel/
        ├── LoginViewModel.kt
        ├── MainViewModel.kt
        └── ViewModelFactory.kt
```

## Open Source

This project is designed to be open-sourced. No credentials are stored in code.
All server details are entered at runtime and saved securely via Android DataStore.

## License

MIT — see LICENSE file.
