# State — RizzoIPTVDesktop v6.0.0

## Branch
`v5/p0-rename-cancellation` — pushed to `https://github.com/johnrizzetto/RizzoIPTVDesktop`

## Latest Commit
`1e87fe0` — feat(desktop): v6.0.0 - Stremio-style drill-in navigation + curated row overhaul

## v6.0.0 — Stremio-Style Navigation + Curated Row Overhaul (THIS SESSION)

### Stremio-Style Drill-In Navigation
- **MoviesHome**: detect non-Discover genre → hide hero + curated rows, show clean full-screen grid with back affordance
- **SeriesHome**: same for TV shows
- **TmdbMovieGrid/TmdbShowGrid**: accept `onBack` callback, render BackRow header with left-arrow indicator
- **CategoryComponents**: new `BackRow` composable (bold label + `‹` indicator, AccentBlue tint, D-pad focusable)
- **HomeScreen**: wire `onBack = viewModel::goBack` at all MoviesHome/SeriesHome call sites

### Curated Rows — VOD
Added missing rows (previously invisible because switch cases were never implemented):
- IMDB/Rotten Tomatoes: Oscar Winners (-18), Oscar Nominated (-19), IMDB Top Rated (-20), Certified Fresh (-21), Audience Favorites (-22)
- Franchise Hub: Marvel (-34), Star Wars (-35), Disney Family (-36), DC Comics (-37), Fast & Furious (-38), Pixar (-39), Harry Potter (-40)
- Streaming: HBO Max (-23), Apple TV+ (-24), Peacock (-25)
- Reorganized section headers: IMDB Top Picks, Rotten Tomatoes, Franchise Hub, Streaming, Moods & Discovery

### Curated Rows — Series
- Added Netflix (-5) back to Series curated rows

### API Additions (TmdbApiService + TmdbRepository)
- `getOscarWinners()`, `getOscarNominated()`, `getCertifiedFresh()`, `getAudienceFavorites()`
- `getMarvelMovies()`, `getStarWarsMovies()`, `getDisneyFamilyMovies()`, `getDcMovies()`, `getFastFuriousMovies()`, `getPixarMovies()`, `getHarryPotterMovies()`
- `getHboMaxShows()`, `getAppleTvShows()`

### Version Bump
- `versionName`: 5.0.0 → 6.0.0
- `versionCode`: 500 → 600

## Build Verification
- `./gradlew :app:assembleV5Debug` → BUILD SUCCESSFUL
- `./gradlew ktlintCheck` → BUILD SUCCESSFUL

## GitHub
- Repo: https://github.com/johnrizzetto/RizzoIPTVDesktop
- Pushed branch: `v5/p0-rename-cancellation`

---

## v5 — TorBox Stream Selection Bug Fixes (COMPLETED)

### Bug #1 — "No valid torrent hash found" for ALL torrents
- Root cause: `TorBoxApiService.addMagnet()` posted form param `"magnet"` but TorBox API expects `"magnet_uri"`
- Fix: changed `.addFormDataPart("magnet", magnet)` → `.addFormDataPart("magnet_uri", magnet)`

### Bug #2 — selectStreamInPicker ignored user's torrent selection
- Root cause: `selectStreamInPicker` called `torBoxRepository.resolveMovie(picker.imdbId)` — fresh search picking ranked #1
- Fix: uses `torBoxRepository.resolveSelectedTorrent(stream, currentFallbackHashes)` — respects user's pick

### Bug #3 — "No streams found" when either source throws
- Root cause: bare `.await()` calls in `fetchMovieStreams`/`fetchEpisodeStreams`
- Fix: individual try/catch per await, one source's failure no longer poisons merged result

---

## IPTV Credentials
- Username: `87bcb5ed3f`
- Password: `c46e2b805d`
- URLs: `http://line.trexgaminghub.xyz`, `http://line.gaminghubott.xyz`, `http://vpn.gaminghubott.xyz`, `http://line.gaminghubpro.xyz`, `http://vpn.gaminghubpro.xyz`
- Auto-login: `DefaultCredentials` object in `MainActivity.kt`, triggered via `autoLoginIfNeeded()` in `LaunchedEffect`

## TMDB Keys
- `TMDB_BEARER` present in local.properties

## Active Blocker
- **Android projector "loading forever"**: Need logcat from device `adb-YSQAR2939YXB-8bG1iC._adb-tls-connect._tcp, 192.168.50.82:45509` to diagnose TMDB pipeline root cause

## Next Session
1. Capture logcat from Android projector → diagnose "loading forever" in TMDB pipeline
2. Install v6.0.0 desktop build on Mac: `app/build/outputs/apk/v5/debug/app-v5-debug.apk` (rename to .app)
3. Deep UI test: click VOD category → verify drill-in grid + back navigation
