# Changelog

All notable changes to Rizzo Player v5 are documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [6.0.0] — 2026-05-05

### Fixed
- **Bug #1 — VOD curated rows -23/-24/-25 missing:** HBO Max (-23), Apple TV+ (-24), Peacock (-25) categories appeared in Movies but had no switch cases, silently falling through to Popular.
- **Bug #2 — Series curated rows -5 through -17, -23 missing:** Streaming platform and Moods & Discovery rows (Netflix, Prime, Disney+, Hulu, Paramount+, Peacock, Critically Acclaimed, Anime, Reality TV, Documentaries, Mini Series, Kids, Korean Dramas, DC Comics) had labels but no switch mappings, silently falling through to Popular.
- **Bug #3 — Series -18/-19 wrong content routing:** "📺 HBO Max" (-18) was routing to `getOscarWinnerMovies()`; "🍎 Apple TV+" (-19) was routing to `getOscarNominatedMovies()`. Remapped to `getHboMaxShows()` and `getAppleShows()` respectively.

### Added
- `docs/ARCHITECTURE-v6.md` — full architecture analysis of TMDB pipeline, category ID system, bug inventory, and data flow diagrams.

---

## [4.0.0-alpha.1] — YYYY-MM-DD

### Added
- v4 flavor fork — installs alongside v3 with isolated DataStore and indigo brand color
- `v4-genesis` tag at fork point; `v4-polish` branch for development

### Changed
- *(List of changes — populated by agents as work completes)*

### Deprecated
- *(Any deprecated features — populated as work progresses)*

### Removed
- *(Removed features — populated as work progresses)*

### Fixed
- *(Bug fixes — populated as work progresses)*

### Security
- *(Security-related changes — populated as work progresses)*

---

## [3.0.0] — Prior Version

See `claude/romantic-leakey-57e175-work` branch history for v3 changelog.

## [4.0.0-alpha.1] — v4/v5 Worktree Branch

### Fixed
- **Bug #1 — "No streams found" when torrents exist:** `fetchMovieStreams`/`fetchEpisodeStreams` now wrap each `await()` individually in try/catch so one source's exception doesn't poison the merged result.
- **Bug #2 — Clicking selected torrent shows premature failure:** `playSelectedTmdbStream` now calls new `resolveSelectedTorrent(stream, fallbackHashes)` which uses the explicitly chosen torrent directly instead of re-searching and picking #1.
