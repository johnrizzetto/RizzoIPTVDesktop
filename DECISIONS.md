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
