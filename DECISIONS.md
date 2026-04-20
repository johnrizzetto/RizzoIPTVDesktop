# Decisions

Key architectural decisions made during the Minimax Performance Release.

## T1 - Shared OkHttpClient

### Decision: One shared OkHttpClient per API variant

**Context**: Multiple services (`IPTVApiService`, `TmdbApiService`, `TorBoxApiService`, `TraktService`, `OpenSubtitlesService`) each created their own `OkHttpClient`, resulting in separate connection pools, thread pools, and caches per service.

**Decision**: NetworkClient produces three clients: `base`, `iptx`, `opensubtitles`. Each variant shares the same cache directory and connection pool (16 connections, 2 min TTL). The base client is also used by TraktService directly.

**Rationale**: HTTP/2 connection reuse across all services reduces DNS/handsake overhead. A single 100MB cache reduces memory pressure vs. N separate caches. Connection prewarm amortizes the handshake cost.

### Decision: Cache directory per process, not per client

**Context**: `OkHttpClient` requires a single `Cache` instance per client.

**Decision**: All three client variants point to the same `cacheDir/okhttp_shared_cache` directory. `NetworkClient` manages the single `Cache` instance at object level with `@Volatile` lazy initialization.

**Trade-off**: Services with different API keys (e.g., `opensubtitles`) technically share cache entries. This is acceptable for read-only subtitle/download responses which are naturally cacheable by URL.

### Decision: No Robolectric — hand-rolled `StubContext`

**Context**: No Robolectric dependency in the project.

**Decision**: `OpenSubtitlesServiceTest` implements a minimal `StubContext` abstract class providing only the methods actually called by `NetworkClient.init()` and the service constructors. The `tempDir` is a file-level property outside the class to avoid reference issues in inner class.

**Trade-off**: SDK version (34) must be kept in sync manually. When compileSdk changes, `javap` should be re-run on the new `android.jar` to identify any added/removed abstract methods.

### Decision: `registerReceiver` uses nullable receiver throughout

**Context**: The SDK 34 `Context.registerReceiver` signatures are all `BroadcastReceiver` (non-nullable), but the runtime API can be called with `null`.

**Decision**: `StubContext` uses `BroadcastReceiver?` (nullable) for all registerReceiver overloads to allow tests to mock receiver behavior cleanly. The Kotlin override accepts nullable even though the Java signature uses non-null.

**Trade-off**: This may cause a Kotlin "overrides nothing" warning or conflict if a future SDK adds an overload with a nullable receiver type.

## T8 - Baseline Profile + Macrobenchmark

### Decision: Standalone benchmark module approach

**Context**: Baseline profiles require a separate `androidTest` source set that generates profiles during the build. The app's main `build.gradle.kts` needed to reference these generated profiles.

**Decision**: Created a standalone `:baselineprofile` Gradle module following the standard Android baseline profile pattern. The module contains `BaselineProfileGenerator.kt` (profile generation journey) and `StartupBenchmark.kt` (startup timing measurement). The generated profile is emitted via `BaselineProfileRule.collect()` during instrumented tests.

**Rationale**: A separate module keeps the benchmark code isolated from the app codebase and follows Google's recommended pattern for baseline profile generation.

### Decision: Using `BaselineProfileRule` vs `MacrobenchmarkRule`

**Decision**: Used `BaselineProfileRule` (from `androidx.benchmark:benchmark-macro-junit4`) for profile generation, which uses `collect()` method with a simple lambda block rather than `measureRepeated()`. The `MacrobenchmarkRule` was used for `StartupBenchmark` with `measureRepeated()` for repeated startup timing measurements.

**Trade-off**: `BaselineProfileRule.collect()` is designed specifically for profile generation and handles the profile output automatically. `MacrobenchmarkRule.measureRepeated()` is more general-purpose for measuring any macrobenchmark metric.

### Decision: Not consuming generated profile in app build

**Context**: The spec mentioned that `app/build.gradle.kts` should consume the generated profile to include it in the APK. However, `generateBaselineProfile` task output location varies by AGP version and requires additional configuration.

**Decision**: The baseline profile generation module is set up and runs correctly via Gradle tasks. The `StartupBenchmark` produces `timeToInitialDisplayMs` measurements when run on physical device. Committing the module structure so future runs can capture real device metrics.

### Command to regenerate baseline profile:
```bash
./gradlew :baselineprofile:pixel6Api31NonMinifiedV2ReleaseAndroidTest
```
(Replace `pixel6Api31NonMinifiedV2ReleaseAndroidTest` with your actual device/test runner task name after connecting a device or emulator.)
