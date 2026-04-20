# Follow-ups

Known issues, future improvements, and post-release cleanup.

## T1 - Shared OkHttpClient + Connection Preamplification

### Hand-rolled StubContext fragile across SDK versions
The `StubContext` in `OpenSubtitlesServiceTest` must be manually kept in sync with `compileSdk`. When compileSdk advances, re-run:
```bash
cd "$ANDROID_SDK/platforms/android-N" && unzip -q -o android.jar -d tmp_android_jar && javap -public -classpath tmp_android_jar android.content.Context | grep abstract
```
Compare against the current StubContext and add/remove accordingly.

### installV2Debug requires connected device
The gradle install step cannot complete without a physical or emulator device connected. Install must be run manually when a device is available: `./gradlew :app:installV2Debug`.

### OpenSubtitlesService device code polling blocks test thread
`TraktService.pollForToken()` uses `Thread.sleep()` in a suspend context (blocks the thread). Consider replacing with a non-blocking delay loop using `kotlinx.coroutines.delay` in a follow-up refactor.

### DataStore preferences proto corruption crashes app on first launch
The DataStore files (`credentials.preferences_pb`, etc.) can become corrupted if the device loses power or is force-killed during a write. The app crashes immediately at startup with `CorruptionException: Unable to parse preferences proto`. Workaround: `run-as <package> rm -f files/datastore/*.preferences_pb` to clear corrupted files. Long-term fix: add corruption handling with delete-and-retry in each store class.
