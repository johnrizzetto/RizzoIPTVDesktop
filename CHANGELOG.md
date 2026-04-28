# Changelog

All notable changes to Rizzo Player v5 are documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

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
