# Bug Fix Progress — RizzoIPTVPlayer v5

## Context

User reports:
1. "No streams found" even when torrents exist
2. Clicking a torrent link shows premature failure instead of loading steps to TorBox

## Bug #1: "No streams found"

**Root cause**: `fetchMovieStreams` (TorBoxRepository.kt ~line 123) wraps the entire `coroutineScope` in a single try/catch. If either data source (TorBox Search OR Torrentio) throws, the whole scope returns `emptyList()`. When merged list is empty, `MainViewModel.kt` fires "No streams found".

**Fix needed**: Wrap EACH source fetch individually so one source's exception doesn't poison the merged result.

Current structure (approximately):
```kotlin
val unified = coroutineScope {
    launch { results + fetchTorBoxSearchMovies(imdbId) }
    launch { results + fetchTorrentioMovies(imdbId) }
} // entire coroutineScope in try/catch → one failure = empty list
```

Each source fetch already has its own try/catch that returns `emptyList()` on failure (lines 158-161 for movies, 167-172 for episodes, etc.). The bug is likely in the outer `coroutineScope` wrapper — need to see exact code.

**Bug #2: User-selected torrent ignored in playback**

**Root cause**: `playSelectedTmdbStream` (MainViewModel.kt ~line 839) calls `torBoxRepository.resolveMovie(selection.imdbId)` which does a **fresh search** and picks the #1 ranked torrent — completely ignoring the `UnifiedTorrent` the user actually selected.

Same issue in `onPlayTmdbEpisode` (MainViewModel.kt ~line 902) which calls `resolveEpisode`.

**Fix needed**: When user explicitly selects a torrent, bypass `resolveMovie`/`resolveEpisode`. Instead:
1. Call `torBoxRepository.addMagnetDirect(stream.url)` (new method that adds a specific magnet URL and returns torrentId)
2. Poll `getTorrentInfo(torrentId)` for progress
3. Emit `StreamResolution.Ready(url, fallbackHashes)` when done

`resolveFallback` (TorBoxRepository.kt line 326) is the correct pattern — it uses a known hash directly without re-searching.

## Key File References

- `/Users/johnrizzetto/v5-p0-phase0/app/src/main/java/com/rizzoplayer/iptv/data/repository/TorBoxRepository.kt`
  - `fetchMovieStreams` (~line 123) — Bug #1 location
  - `resolveMovie` (line 205) — fresh search, ignores user selection
  - `resolveEpisode` (~line 266) — same bug as resolveMovie
  - `resolveFallback` (line 326) — correct pattern for direct magnet

- `/Users/johnrizzetto/v5-p0-phase0/app/src/main/java/com/rizzoplayer/iptv/ui/viewmodel/MainViewModel.kt`
  - `playSelectedTmdbStream` (~line 839) — Bug #2 location
  - `onPlayTmdbEpisode` (~line 902) — Bug #2 location for episodes

## Exact Fix Instructions

### Fix #1: fetchMovieStreams error isolation

Read the full `fetchMovieStreams` function. The issue is that the outer `coroutineScope { }` itself is inside a try/catch that catches everything. Need to see the exact code to fix properly. Each `async { fetchXxx() }` call already has individual try/catch inside those functions — the outer try/catch is what's catching scope failures.

### Fix #2: Add direct magnet method + use it in playSelectedTmdbStream

1. In TorBoxRepository, add:
```kotlin
fun addMagnetDirect(magnetUrl: String): Flow<StreamResolution> = flow {
    emit(StreamResolution.Queuing)
    val result = try { torBox.addMagnet(magnetUrl) } catch (e: Exception) {
        TorBoxAddResult(success = false, error = e.message)
    }
    if (!result.success || result.torrentId == null) {
        emit(StreamResolution.Failed(result.message ?: "Failed to queue torrent")); return@flow
    }
    val torrentId = result.torrentId!!
    emit(StreamResolution.Caching(100))
    val info = try { torBox.getTorrentInfo(torrentId) } catch (e: Exception) { null }
    if (info != null) {
        val url = getDownloadUrl(torrentId, info.files)
        if (url != null) { emit(StreamResolution.Ready(url, emptyList())); return@flow }
    }
    // poll loop...
}
```

2. In MainViewModel `playSelectedTmdbStream`, replace the `resolveMovie` call with direct use of selected torrent:
   - Use `stream.hash` if available for `checkCached`
   - Use `stream.url` with `addMagnet`
   - Pass `fallbackHashes` from remaining streams

3. Same pattern for `onPlayTmdbEpisode`.
