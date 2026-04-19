# Rizzo IPTV Player — Path to World-Class
## Dev + Test Plan for Minimax (Release 2.0)

**Author:** Senior Android TV engineering review
**Audience:** Minimax (executor)
**Date:** 2026-04-19
**Repo HEAD at plan time:** `dc78d40`

---

## 0. Vision & Guiding Principles

The app today is a solid IPTV + TMDB/TorBox browser. To become the **best streaming app on Android TV**, it needs to clear three bars:

1. **Feels native to the TV remote.** Voice search, every surface D-pad-complete, first-frame < 2s, 60fps scrolling always.
2. **Earns trust.** Crash-free > 99.5%, measurable with telemetry. Automated tests stop regressions. Release signing, CI, baseline profiles.
3. **Has killer features.** Chromecast, auto-subtitles (OpenSubtitles), next-up auto-play, Android TV Home Screen Channels, quality selection, Trakt.tv sync, downloads.

**Non-goals:** phone/tablet UX, ads, account signup, in-app billing.

**Principles:**
- No new architectural layers unless pulling weight. Stay on OkHttp + Gson + DataStore; resist Retrofit/Hilt/Room unless adding one unlocks 3+ features.
- Every new feature ships with tests (unit + instrumented). No test = not done.
- Every user-visible change ships with an entry in `CHANGELOG.md`.
- `local.properties` remains the only place secrets live.

---

## 1. Executive Summary

**Ship in Release 2.0 (this plan):**

| Workstream | Why it matters | Effort |
|---|---|---|
| **W1. Release hygiene** — signing, CI, Crashlytics, baseline profiles, ProGuard | Ships production-grade binary | M |
| **W2. Test infrastructure** — unit + instrumented + macrobenchmark + screenshot | Prevents regressions | M |
| **W3. Voice search** — Android TV remote mic | TV-native killer feature | S |
| **W4. Chromecast (Default Sender)** | Expected on any streaming app | M |
| **W5. Subtitles (OpenSubtitles + rendering) | Major quality bump for TMDB | M |
| **W6. Next-up + binge autoplay** | Doubles session length | S |
| **W7. Quality + track selection in player** | Power user essential | S |
| **W8. Android TV Home Screen Channels** | Discoverability via OS | M |
| **W9. Trakt.tv sync (optional, user-opt-in)** | Differentiator | L |
| **W10. Recommendations + hero rail** | Personalization | M |
| **W11. Downloads via TorBox cache** | Leverages TorBox unique advantage | L |
| **W12. Polish pass** — loading/empty/error, a11y, localization | Finishing touch | M |

**Fix the QA fails from the previous report first** (Section 14 below).

---

## 2. Workstream Specs

For each workstream: goal, files to add/modify, API contracts, acceptance criteria, test cases.

---

### W1. Release Hygiene

#### Deliverables
1. **Release signing config**
   - Add `signingConfigs.release` in `app/build.gradle.kts` sourcing from `local.properties`: `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. Fail the build if keystore path is set but file is missing.
   - Provide `keystore/README.md` documenting `keytool -genkey` command and storage policy.
2. **ProGuard / R8**
   - Full R8 already enabled. Add rules in `proguard-rules.pro` keeping: all Gson models (`com.rizzoplayer.iptv.data.model.**`), Media3 session callbacks, Cast framework, any reflection-used classes.
   - Run `./gradlew assembleRelease` and diff APK shrinkage before/after in `DECISIONS.md`.
3. **Baseline profiles**
   - Add `baselineprofile/` module using `androidx.benchmark:benchmark-macro-junit4:1.2.4`.
   - Record a profile for: cold start → LIVE category list scroll, HOME hero scroll, open VOD detail, start playback. Output `baseline-prof.txt` into app module.
   - Validate startup improvement with `StartupBenchmark` macrobenchmark test — must show ≥ 20% faster cold start on API 29+ emulator.
4. **Version management**
   - Move `versionCode` / `versionName` to `gradle/libs.versions.toml` (new) along with all dependency versions. First usage of version catalog — Gradle 8.4+ friendly.
5. **Crash reporting (Sentry, not Firebase — smaller, self-hostable)**
   - Add `io.sentry:sentry-android:7.8.0`.
   - Init in `RizzoApp.onCreate()` gated on `BuildConfig.DEBUG == false` and a DataStore flag `telemetryOptIn`. First-launch settings prompt to opt in.
   - Integrate Timber or simple `Log` wrapper to forward only non-PII breadcrumbs (no stream URLs, no credentials).
6. **CI**
   - `.github/workflows/ci.yml`:
     - On PR: `./gradlew :app:lintDebug :app:assembleDebug :app:testDebugUnitTest`
     - On push to `main`: add `:app:assembleRelease` + upload APK as artifact
     - Use `actions/setup-java@v4` (Zulu 17), `gradle/actions/setup-gradle@v3` with cache.
   - Add `.github/workflows/instrumented.yml` running on `macos-latest` with `reactivecircus/android-emulator-runner@v2` (API 30, `google_atv` target) on merge to main — tolerant of flakes (retry 2×).
   - `./gradlew ktlintCheck` via `org.jlleitschuh.gradle.ktlint` plugin (zero-config).

#### Acceptance
- `assembleRelease` produces signed APK under `app/build/outputs/apk/release/`.
- Sentry receives a synthetic crash from a hidden debug menu entry (Settings → About → tap version 7×).
- CI green on an empty PR.
- Cold start under 1.5s on Pixel TV emulator per macrobenchmark (median of 10 runs).

---

### W2. Test Infrastructure

#### Unit tests (JUnit4 + MockK + MockWebServer)

Create `app/src/test/java/com/rizzoplayer/iptv/`:

| Test file | Coverage target |
|---|---|
| `data/api/TmdbApiServiceTest.kt` | Each endpoint: correct path, bearer header, parses fixture JSON, handles 401/404/500 |
| `data/api/TorrentioServiceTest.kt` | URL shape for movie vs episode; quality ranking; empty streams; malformed JSON |
| `data/api/TorBoxApiServiceTest.kt` | Stream resolution, cached/uncached branches, error codes |
| `data/api/IPTVApiServiceTest.kt` | Xtream auth flow, base64 EPG decode, VOD/series endpoints |
| `data/repository/TmdbRepositoryTest.kt` | Cache TTL enforcement, request dedup (ConcurrentHashMap), concurrent identical calls share one coroutine |
| `data/repository/TorBoxRepositoryTest.kt` | Same |
| `data/repository/IPTVRepositoryTest.kt` | Disk cache read/write, memory/disk fallthrough |
| `data/local/PreferencesStoreTest.kt` (instrumented) | PIN hashing, parental lock persistence, opt-in flags |
| `ui/viewmodel/MainViewModelTest.kt` | Search debounce (400ms), section switching, favorites toggle, recent-item update, error propagation |
| `ui/player/SmartResumeTest.kt` | Dialog threshold (exactly 60_000ms), Resume seeks, Start over does not |
| `ui/player/KeyEventTest.kt` | Every keycode path, `event.repeatCount` branches for all seek keys |

Use JSON fixtures in `app/src/test/resources/fixtures/` — real TMDB responses captured once, committed.

#### Instrumented tests (AndroidX Test + Compose test)

`app/src/androidTest/java/com/rizzoplayer/iptv/`:

| Test file | Coverage |
|---|---|
| `ui/SettingsScreenTest.kt` | All sections focusable via D-pad, parental lock toggle, cache clear |
| `ui/ParentalLockOverlayTest.kt` | Wrong PIN shows error, correct PIN dismisses, BACK is blocked while locked |
| `ui/HomeNavigationTest.kt` | Sidebar → content focus handoff, no focus traps |
| `ui/TmdbDetailScreenTest.kt` | Detail renders, play button focuses, episode grid scrolls |
| `player/PlayerActivityUiTest.kt` | Resume dialog appears / hides based on EXTRA_RESUME_MS, track picker opens on MENU |

#### Macrobenchmark (baselineprofile module)

| Benchmark | Target |
|---|---|
| `StartupBenchmark` | Cold start < 1200ms, warm < 400ms |
| `ScrollBenchmark` | Movies grid 30s scroll: < 3 janky frames per 1000 |
| `PlayerStartBenchmark` | Open VOD → first frame < 2000ms (mock stream server) |

#### Screenshot tests (Paparazzi)

Add `app.cash.paparazzi:paparazzi:1.3.3`. Screenshot-test key composables in isolation at 1920×1080:
- `PosterCard` (focused + unfocused)
- `Sidebar` (each section selected)
- `TmdbDetailScreen` (movie vs show)
- `SettingsScreen`
- `TrackPickerPanel`
- `ParentalLockOverlay`

CI step: `./gradlew verifyPaparazziDebug` — fails PR on pixel diff.

#### Acceptance
- `./gradlew test` passes with **≥ 70% line coverage** on `data/` and `viewmodel/` packages (Jacoco).
- `./gradlew connectedAndroidTest` passes on API 30 emulator.
- All macrobenchmark thresholds met.
- Screenshot tests stable on CI.

---

### W3. Voice Search

#### Why
Every good Android TV app binds the mic button. Rizzo doesn't. This single change makes the app feel native.

#### Spec
- Declare `<intent-filter>` for `android.intent.action.SEARCH` in `AndroidManifest.xml` + `<meta-data android:name="android.app.searchable">`.
- Add `res/xml/searchable.xml` with TMDB label + hint.
- In `MainActivity.onNewIntent`, handle `Intent.ACTION_SEARCH`, extract `SearchManager.QUERY`, feed into `MainViewModel.search(query)`.
- Wire the `SearchBar` to also accept text from the Android TV voice-input IME (already works if EditText, but verify for Compose — use `rememberTextFieldState` with IME action).
- Add a mic icon button at right of the search bar. Pressing it fires `Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)` with `EXTRA_LANGUAGE_MODEL` = free-form and returns the top result.

#### Acceptance
- Press mic button on physical TV remote while on any screen → voice dialog appears → speak "Interstellar" → app navigates to Movies search results.
- Manual speech also works via the mic button.

#### Tests
- Instrumented test launches `ACTION_SEARCH` intent with `query = "test"`, asserts `MainViewModel.state.searchQuery == "test"` and results populate within 2s (MockWebServer fixture).

---

### W4. Chromecast (Default Sender)

#### Spec
- Add dependencies:
  - `com.google.android.gms:play-services-cast-framework:21.4.0`
  - `androidx.mediarouter:mediarouter:1.7.0`
- `RizzoApp` must implement `OptionsProvider` returning `CastOptions` with `"CC1AD845"` (default media receiver ID).
- Add `MediaRouteButton` in `PlayerActivity` top bar. Hide when no routes available.
- Implement `CastSession` lifecycle: on session start, build `MediaInfo` (URL, mime, title, poster URI) and load via `RemoteMediaClient`.
- When casting: local `ExoPlayer` pauses; UI shows "Playing on [device]" card with scrub, play/pause, stop (D-pad wired).
- On session end: local player resumes from cast playback position.

#### Edge cases
- Torrentio URLs sometimes return 302s. The default receiver handles them. Verify `MediaInfo` uses `CONTENT_TYPE_VIDEO_MP4` or `application/x-mpegURL` based on URL suffix.
- Subtitle tracks: pass as `MediaTrack` with subtype `SUBTITLES_SIDE_LOADED` once W5 lands.

#### Acceptance
- Cast button visible when a Chromecast is on the LAN.
- Casting a TMDB movie plays correctly on the receiver with title + poster visible.
- Stopping cast resumes local playback at the cast position (±5s tolerance).

#### Tests
- Unit test for `CastSessionController.buildMediaInfo()` — correct MIME detection, metadata fields.
- Manual QA only for the round-trip (no CI Cast device).

---

### W5. Subtitles (OpenSubtitles)

#### Spec
- API: OpenSubtitles REST v1 (`https://api.opensubtitles.com/api/v1`). Free tier: 5 requests/day without API key, 20/day with. Require user to paste API key in Settings → Playback → Subtitles. Store in DataStore.
- New file: `data/api/OpenSubtitlesService.kt` — `search(imdbId, season?, episode?, language)` → list of `.srt` download URLs; `download(fileId)` → bytes.
- New file: `data/repository/SubtitleRepository.kt` — caches `.srt` to `cacheDir/subs/{imdb}-{lang}-{season}-{episode}.srt`. TTL: none (subtitles don't change).
- In `PlayerActivity`, after `MediaItem.fromUri(url)`, attach `MediaItem.SubtitleConfiguration`s for each cached .srt in preferred languages (from Settings).
- ExoPlayer `TextOutput` already shown via `PlayerView`. Add a subtitle style dialog (Settings → Playback → Subtitles): text size (sp), color, background opacity, edge type (none/drop-shadow/outline). Store as `SubtitleStyle` DataStore entry, apply via `PlayerView.setSubtitleView(SubtitleView)` styling.

#### Acceptance
- Play any TMDB movie with IMDB ID → subtitles auto-fetch in user's preferred language → rendered under the video.
- Settings → Playback → Subtitles: changing text size updates live on next playback start.
- No subtitles found → no crash, toast "No subtitles found for this content".

#### Tests
- Unit: `OpenSubtitlesServiceTest` — fixture JSON parsing.
- Unit: `SubtitleRepositoryTest` — cache hit/miss, filename derivation.
- Instrumented: `SubtitleStyleTest` — DataStore read/write cycle.

---

### W6. Next-Up & Binge Autoplay

#### Spec
- When a TMDB show episode is ~30 seconds from the end, show a bottom-right "Up next: S2E4 · Title" card with 10-second countdown circle.
- D-pad: `OK` plays now; `BACK` or letting it expire dismisses (but still auto-plays on end). Add Settings → Playback → "Autoplay next episode" toggle (default ON).
- For movies, if current content has a known TMDB collection (e.g., *The Matrix Collection*), show "Up next: The Matrix Reloaded" similarly. Fetch via TMDB `/movie/{id}/similar` as fallback.
- Wire via `Player.Listener.onPositionDiscontinuity` + periodic check when `position > duration - 30_000`.

#### Acceptance
- Watching an episode: ~30s from end → card appears → countdown runs → auto-plays next episode at 0.
- Toggling "Autoplay next" off: card still appears but does not auto-advance.
- Last episode of last season: no card.

#### Tests
- Unit: `NextUpResolverTest` — correct next-episode derivation across season boundaries, last-episode returns null.
- Instrumented: `NextUpCardTest` — card renders with mock state, OK triggers callback.

---

### W7. Quality & Track Selection

#### Spec
- Extend `TrackPickerPanel` with a third row: **Quality** (adaptive → 2160p/1080p/720p/480p/auto).
- Use `TrackSelectionParameters` + `.setMaxVideoSize(w, h)` to constrain. "Auto" clears the constraint.
- Also add a **Playback speed** row (0.5× / 1× / 1.25× / 1.5× / 2×) applied via `player.setPlaybackSpeed()`.
- Persist last-used speed and quality preference in DataStore; restore on next playback.

#### Acceptance
- Open track picker → 4 rows (Audio, Subtitles, Quality, Speed) — all D-pad navigable with horizontal scroll.
- Changing quality from auto to 720p — Media3 renegotiates; verify via `player.videoFormat?.height` in debug overlay.

#### Tests
- Unit: `QualitySelectorTest` — `TrackSelectionParameters` build correctly for each preset.

---

### W8. Android TV Home Screen Channels

#### Why
Surfaces Rizzo content in the OS launcher row — users don't even need to open the app to discover.

#### Spec
- Implement `androidx.tvprovider:tvprovider:1.1.0-alpha01`.
- `ChannelsWorker` (WorkManager, daily) writes:
  1. **"Continue Watching"** channel populated from `RecentlyWatchedStore` where `watchedMs < durationMs`.
  2. **"Trending on Rizzo"** channel from TMDB Trending.
  3. **"Popular Movies"** / **"Popular Shows"** channels.
- Each `PreviewProgram` carries a deep link `rizzoiptv://play?type=tmdb_movie&id=550` — handled by `MainActivity` intent filter; routes straight to `PlayerActivity`.
- Add `androidx.work:work-runtime-ktx:2.9.0`.

#### Acceptance
- After first launch + watching one item, the Rizzo Continue Watching row appears on the Google TV home screen within 24h (or immediately in tests by running the worker manually).
- Tapping a tile opens straight into playback.

#### Tests
- Unit: `ChannelsWorkerTest` — correct channel/program shape given fixture state.
- Instrumented: deep-link handler test (`am start -a android.intent.action.VIEW -d "rizzoiptv://play?..."`).

---

### W9. Trakt.tv Sync (opt-in)

#### Spec
- OAuth 2 device-code flow (perfect for TV — shows code + URL, user authorizes on phone).
- New `data/api/TraktService.kt`, `data/repository/TraktRepository.kt`.
- On playback events (threshold: ≥ 80% complete for "scrobbled", else "paused" with position), POST to `/scrobble`.
- On app start: pull user's history → merge with local `RecentlyWatchedStore` (Trakt wins for last-watched-at; local wins for position — Trakt doesn't store position reliably).
- Settings → Integrations → Trakt.tv → "Connect" button → shows QR code + 8-char code.
- All behind `traktEnabled` flag (default OFF).

#### Acceptance
- Device code flow: show code, user enters on trakt.tv/activate, polling succeeds, token stored.
- Watching a movie to 80% posts a scrobble visible on trakt.tv profile.
- Disconnecting clears the token and stops syncing.

#### Tests
- Unit: `TraktScrobbleThresholdTest`, `TraktMergeTest` for history merge logic.
- Manual QA for OAuth round-trip.

---

### W10. Recommendations & Hero Rail

#### Spec
- New `Section.HOME` becomes the landing screen (replaces LIVE as default).
- `HomeScreen` top: **hero banner** that cross-fades between 5 rotating items (auto-advance 8s, pauses on focus). Each item shows backdrop + title + overview snippet + "Play" / "More info" buttons.
- Rows below:
  1. Continue Watching (cross-source)
  2. Because You Watched *X* (TMDB `/movie/{id}/recommendations` or `/tv/{id}/recommendations` based on last watched)
  3. Trending Today (`/trending/all/day`)
  4. Live Now — your favorite live channels + their current EPG program
  5. New This Week (TMDB `/movie/now_playing`)
  6. Critically Acclaimed (TMDB discover sorted by `vote_average.desc` with `vote_count.gte=500`)

#### Acceptance
- Landing view is `HOME`, hero rotates, all rows D-pad navigable, focus restore on back.
- Rows populate within 1s on warm cache.

#### Tests
- Unit: `HomeRowResolverTest` — correct TMDB calls and merging.
- Screenshot: `HomeScreenTest` with fixture state.

---

### W11. Downloads via TorBox

#### Why
TorBox already caches — surface it. Users can "download" = request a TorBox-cached copy + get a direct URL that plays without streaming.

#### Spec
- Settings → Storage → "Downloads" list shows queued + completed.
- "Download" button on any VOD / episode detail page triggers `TorBoxService.requestDownload(magnet/infohash)`, polls until status = cached, stores resulting URL with a 30-day TTL.
- Optionally: actual disk copy for offline. Use `androidx.media3.exoplayer.offline.DownloadManager`. Gate behind "Offline downloads" toggle (OFF by default — bandwidth/storage concern).

#### Acceptance
- Requesting a download moves the item into the Downloads list with a progress indicator.
- Opening a completed download plays instantly without new Torrentio resolution.

#### Tests
- Unit: `TorBoxDownloadQueueTest` — state transitions (queued → caching → ready → expired).

---

### W12. Polish Pass

#### 12a. Empty / Error / Loading states
- New `UiState<T>` sealed class (`Loading | Empty(message, cta) | Error(type, retry) | Data(value)`).
- Shimmer placeholders for poster grids (custom `Modifier.drawWithCache` — no new dep).
- `UiError` sealed: `NoNetwork`, `Unauthorized`, `NotFound`, `StreamUnavailable`, `RateLimited`, `Unknown(msg)`. Friendly text per type, always with retry action.

#### 12b. Accessibility
- Every `Image` has real `contentDescription` (poster alt-text = movie title + year).
- Every `clickable` paired with `semantics { role = Role.Button }`.
- Focus order audit on every screen. Compose `testTag` on every major surface.
- Run TalkBack smoke test — every flow reachable with ears only.

#### 12c. Localization
- Extract every hardcoded UI string to `res/values/strings.xml`.
- Provide `values-es/`, `values-fr/`, `values-de/`, `values-pt-rBR/` with DeepL-quality translations (manual review).
- Date / time formatting via `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())`.

#### 12d. Theming
- Refine color palette: MainBg `#0A0C14`, CardBg `#14182A`, Accent `#5C8CFF`, Focus ring `#8FB3FF`, positive `#3DDC97`, warning `#FFB84C`, error `#FF5E6C`.
- Hero banner gradient overlay: vertical `Color.Transparent → 0x99000000 → 0xE6000000`.
- Typography scale normalized to Material 3 tokens, no inline `.sp` constants outside `Theme.kt`.

#### 12e. Logging cleanup
- Introduce `RizzoLog` (thin `Log.d` wrapper) with tag-per-module constants. Strip in release via ProGuard rule `-assumenosideeffects class com.rizzoplayer.iptv.util.RizzoLog { *; }`.

---

## 3. Fixes From Previous QA Report

These must land in PR #1 of this release:

| # | Fix | Location |
|---|---|---|
| 1 | Long-press `KEYCODE_3` → +30s (use `event.repeatCount > 0`). Mirror for `KEYCODE_1` → -30s. | [PlayerActivity.kt:320-329](app/src/main/java/com/rizzoplayer/iptv/ui/player/PlayerActivity.kt:320) |
| 2 | Annotate `PlaybackPrep` `@Immutable` (currently `@Stable`). | [MainViewModel.kt:56](app/src/main/java/com/rizzoplayer/iptv/ui/viewmodel/MainViewModel.kt:56) |
| 3 | Annotate `StreamSelectionState` `@Immutable`. | [MainViewModel.kt:65](app/src/main/java/com/rizzoplayer/iptv/ui/viewmodel/MainViewModel.kt:65) |
| 4 | Annotate `TmdbStreamSelectionState` `@Immutable`. | [MainViewModel.kt:74](app/src/main/java/com/rizzoplayer/iptv/ui/viewmodel/MainViewModel.kt:74) |
| 5 | Bump `TmdbApiService` and `IPTVApiService` OkHttp disk caches to 50MB for consistency. | [TmdbApiService.kt:22](app/src/main/java/com/rizzoplayer/iptv/data/api/TmdbApiService.kt:22), [IPTVApiService.kt:24](app/src/main/java/com/rizzoplayer/iptv/data/api/IPTVApiService.kt:24) |

---

## 4. PR Sequencing

Ship in this order — each PR is independently mergeable and < 800 LOC net:

| PR | Title | Depends on |
|---|---|---|
| #1 | fix: QA regressions + annotation cleanup | — |
| #2 | chore: version catalog + release signing + CI skeleton | #1 |
| #3 | test: unit test infra + MockWebServer fixtures | #2 |
| #4 | test: instrumented + Paparazzi screenshot tests | #3 |
| #5 | feat: voice search + mic button | #2 |
| #6 | feat: quality + speed selectors in track picker | #1 |
| #7 | feat: next-up autoplay | #3 |
| #8 | feat: OpenSubtitles integration | #3 |
| #9 | feat: Chromecast default sender | #4 |
| #10 | feat: Home screen + hero rail + recommendations | #4 |
| #11 | feat: Android TV Home Screen Channels | #10 |
| #12 | perf: baseline profiles + macrobenchmark | #2 |
| #13 | chore: Sentry + telemetry opt-in | #2 |
| #14 | feat: Trakt.tv device-code sync (opt-in) | #3 |
| #15 | feat: TorBox downloads | #3 |
| #16 | polish: empty/error/loading + a11y + l10n + theming | all |

---

## 5. Definition of Done (per PR)

A PR is not done until **all** of the following:

1. ✅ `./gradlew assembleDebug assembleRelease` passes locally.
2. ✅ `./gradlew test` passes (unit) — new code ≥ 70% line coverage.
3. ✅ `./gradlew connectedAndroidTest` passes on API 30 emulator (instrumented).
4. ✅ `./gradlew verifyPaparazziDebug` passes (screenshot diffs).
5. ✅ `./gradlew ktlintCheck` passes.
6. ✅ Manual QA on a physical Android TV / Google TV device — checklist in PR description.
7. ✅ `CHANGELOG.md` updated under `## Unreleased`.
8. ✅ `DECISIONS.md` updated if any non-obvious judgment calls were made.
9. ✅ PR description follows template: **Summary / Screenshots / Test plan / Rollout**.
10. ✅ No new warnings in `lintDebug`.

---

## 6. Test Plan Summary

### 6.1 Pyramid targets at Release 2.0 ship

| Level | Target count | Coverage target |
|---|---|---|
| Unit tests | ~180 | 70%+ on `data/` and `viewmodel/` |
| Instrumented | ~40 | All user-facing flows |
| Screenshot (Paparazzi) | ~25 | All major composables |
| Macrobenchmark | 5 | Startup, scroll, player start, search, home render |
| Manual smoke | 30-item checklist | See 6.2 |

### 6.2 Release smoke test — manual, on physical hardware

Run on a real Google TV device + a 4K LG webOS Chromecast target:

1. Fresh install → first-run onboarding → login → lands on HOME.
2. HOME hero rotates, each row loads within 1s.
3. Voice search "Interstellar" → results appear → open detail → play → first frame < 3s.
4. Track picker → switch audio + subtitles + quality (1080p) + speed (1.25×) — all persist across relaunch.
5. Cast to Chromecast → playback on TV → stop → local resumes within 3s of cast position.
6. Next-up card appears near end of episode → auto-advances.
7. Parental lock enable → navigate to Movies → PIN overlay → wrong PIN shows error → correct PIN succeeds.
8. Kill app during playback → reopen → smart resume dialog at correct timestamp.
9. Deep link `adb shell am start -a android.intent.action.VIEW -d "rizzoiptv://play?type=tmdb_movie&id=550"` → opens into playback.
10. Settings → Clear cache → cache size returns to 0.0 MB.
11. Toggle Trakt.tv → scrobble appears on trakt.tv profile.
12. Download via TorBox → list shows completed → play from list is instant.
13. Test all 5 locales — no text truncation, no missing strings.
14. TalkBack on → navigate entire HOME with ears closed.
15. Sentry receives a synthetic crash.
16. Cold start (killed process + cleared RAM) < 2s on real hardware.
17. Scroll Movies grid for 30s → no perceivable jank.
18. Network airplane mode → friendly error with retry, not a crash.
19. Network flapping mid-playback → ExoPlayer recovers (verify via DevicePolicyController test).
20. Exit via HOME button → re-entry resumes on the same screen.
21. Reorder favorites (D-pad long-press left/right on favorite row) → persists.
22. Parental lock: if PIN set + app killed → reopen must re-prompt.
23. Subtitles: rendering correctly in all preferred languages; no timing drift.
24. Next-up dialog: BACK dismisses without advancing.
25. Play speed persists across content.
26. Screenshot: Share a screenshot from the Android TV share sheet → fires without crash.
27. Lint: `./gradlew lintRelease` produces zero new issues.
28. Profile: hero carousel renders at > 58fps per `dumpsys gfxinfo`.
29. Memory: no leaks after 10 min browsing (LeakCanary in debug builds, add it).
30. Release APK size ≤ 20MB per ABI after R8.

### 6.3 Performance budgets (macrobenchmark-enforced)

| Metric | Budget | Current (est.) |
|---|---|---|
| Cold start | 1200ms | ~2000ms |
| Warm start | 400ms | ~600ms |
| Open VOD detail | 500ms | ~800ms |
| Stream start (first frame) | 2000ms | ~3000ms |
| Scroll jank (per 1000 frames) | < 3 | unknown |
| APK size (arm64-v8a) | < 20MB | ~15MB |
| Memory steady-state | < 180MB | unknown |

Budgets enforced in CI — PR that regresses any metric by > 10% is blocked.

---

## 7. Risks & Mitigations

| Risk | Mitigation |
|---|---|
| OpenSubtitles rate limits | Cache aggressively + allow user API key |
| Torrentio returning dead magnets | Fall back to next quality; surface "try another source" button |
| Cast receiver mime-type mismatch | Default receiver is permissive; doc custom receiver as Release 2.1 option |
| Trakt.tv OAuth UX on TV | Device-code flow (QR + short code) — well-trodden pattern |
| TMDB API outage | 24-hour cache mitigates; show cached data + small "offline" chip |
| Baseline profile drift | Re-record on every major release; block merge if startup regresses > 10% |
| R8 breaking reflection-heavy libs (Gson, Sentry) | Explicit keep rules + CI check that release build launches |

---

## 8. Out of Scope for 2.0 (parking lot)

- Phone/tablet adaptive UI
- In-app purchases / paid tier
- Multi-user profiles (parked for 2.1 — needs storage redesign)
- Custom Cast receiver (branded)
- Offline-first refactor
- Mobile companion app for remote control

---

## 9. Expected Outcome

When this plan ships:

- **Perceived quality:** indistinguishable from Infuse / Stremio on first 5 min of use.
- **Crash-free rate:** > 99.5% per Sentry.
- **Session length:** up 2–3× (next-up autoplay + home hub drive binge sessions).
- **Cold start:** < 1.2s on mid-tier Google TV.
- **First frame:** < 2s on VOD.
- **Test coverage:** ≥ 70% on business logic; CI-enforced regression protection.
- **Discoverability:** Rizzo content surfaces on Google TV home screen.
- **Platform features:** Voice, Cast, HSC, Trakt — box checked on every expected TV capability.

This is a shippable, marketable, press-releasable v2.0. Execute in the PR order above, respect the Definition of Done, and the app becomes best-in-class.
