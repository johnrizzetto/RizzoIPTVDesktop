# BLOCKERS.md

## User Action Required Before Device Test

The on-device cache on the projector/test device contains empty search results cached before the token fix was applied. These will persist until they expire (TTL = 10 minutes) or are cleared manually.

Before testing search on device, do ONE of the following:

**Option 1 (fastest):** On the Android device, go to:
```
Settings → Apps → Rizzo IPTV v4 → Storage → Clear Data
```

**Option 2:** Uninstall and reinstall app-v4-debug.apk

**Option 3:** Wait 10 minutes after installing the new APK before searching

Failure to do this will cause search to return empty results even after the fix, because the cache read path returns cached empty lists before making a network request.
