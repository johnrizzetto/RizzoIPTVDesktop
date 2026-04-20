# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased] - Minimax Performance Release

### T1 - Shared OkHttpClient + Connection Preamplification
- **2026-04-19** - Shared `OkHttpClient` across all API services (`IPTVApiService`, `TmdbApiService`, `TorBoxApiService`, `TraktService`, `OpenSubtitlesService`). Single 100MB HTTP/2 cache directory, connection pool, and dispatcher shared at process level. Connection prewarm on initialization. `OpenSubtitlesServiceTest` (5 tests) and `SharedHttpClientTest` (4 tests) added.
