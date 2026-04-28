package com.rizzoplayer.iptv.data.repository

import android.util.Log
import com.rizzoplayer.iptv.BuildConfig
import com.rizzoplayer.iptv.data.api.TorBoxApiService
import com.rizzoplayer.iptv.data.api.TorBoxSearchService
import com.rizzoplayer.iptv.data.api.TorrentioService
import com.rizzoplayer.iptv.data.model.TorrentioStream
import com.rizzoplayer.iptv.data.model.TorBoxAddResult
import com.rizzoplayer.iptv.data.model.TorBoxFile
import com.rizzoplayer.iptv.data.model.TorBoxTorrent
import com.rizzoplayer.iptv.data.model.TorBoxSearchTorrent
import com.rizzoplayer.iptv.data.model.UnifiedTorrent
import com.rizzoplayer.iptv.data.model.TorrentSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.supervisorScope

sealed class StreamResolution {
    data object Searching : StreamResolution()
    data object Queuing : StreamResolution()
    data class Caching(val percent: Int) : StreamResolution()
    data class TryingNextStream(val attempt: Int, val total: Int) : StreamResolution()
    data class Ready(val url: String, val fallbackHashes: List<String> = emptyList()) : StreamResolution()
    data class Failed(val reason: String) : StreamResolution()
}

class TorBoxRepository(
    private val torBox: TorBoxApiService,
    private val torrentio: TorrentioService,
    private val torBoxSearch: TorBoxSearchService
) {
    private val TORBOX_CONFIG = "torbox=${BuildConfig.TORBOX_API_KEY}"
    private val TAG = "TorBoxRepo"

    // ─── Unified torrent model ────────────────────────────────────────────────

    fun parseInfoHash(stream: TorrentioStream): String? {
        val url = stream.url
        return if (url.startsWith("magnet:?xt=urn:btih:")) {
            url.removePrefix("magnet:?xt=urn:btih:").split("&").firstOrNull()
        } else null
    }

    fun parseMagnetFromHash(hash: String): String = "magnet:?xt=urn:btih:$hash"

    private fun parseQuality(s: TorrentioStream): Int {
        val t = s.title.lowercase()
        return when {
            t.contains("2160") || t.contains("4k") -> 2160
            t.contains("1080") -> 1080
            t.contains("720") -> 720
            t.contains("480") -> 480
            t.contains("360") -> 360
            else -> 0
        }
    }

    private fun parseSeeders(s: TorrentioStream): Int =
        Regex("👤\\s*(\\d+)").find(s.title)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    private fun parseSearchQuality(title: String): Int =
        when {
            title.contains("2160") || title.contains("4k") -> 2160
            title.contains("1080") -> 1080
            title.contains("720") -> 720
            title.contains("480") -> 480
            title.contains("360") -> 360
            else -> 0
        }

    private fun torrentioToUnified(t: TorrentioStream): UnifiedTorrent = UnifiedTorrent(
        title = t.title,
        url = t.url,
        hash = parseInfoHash(t),
        size = 0L,
        seeders = parseSeeders(t),
        quality = parseQuality(t),
        indexer = t.name,
        source = TorrentSource.TORRENTIO
    )

    private fun searchToUnified(t: TorBoxSearchTorrent): UnifiedTorrent {
        val hash = t.hash ?: t.torrentLink?.let { link ->
            Regex("urn:btih:([a-fA-F0-9]+)").find(link)?.groupValues?.get(1)
        } ?: ""
        val url = if (hash.isNotBlank()) parseMagnetFromHash(hash) else t.torrentLink ?: ""
        return UnifiedTorrent(
            title = t.title,
            url = url,
            hash = hash.takeIf { it.isNotBlank() },
            size = t.size,
            seeders = t.seeders,
            quality = parseSearchQuality(t.title),
            indexer = t.indexer,
            source = TorrentSource.TORBOX_SEARCH
        )
    }

    private fun rankUnified(torrents: List<UnifiedTorrent>): List<UnifiedTorrent> =
        torrents.sortedWith(
            compareByDescending<UnifiedTorrent> { parseSearchQuality(it.title) }
                .thenByDescending { it.seeders }
                .thenByDescending { it.source == TorrentSource.TORBOX_SEARCH }
        )

    // Legacy ranking for pure Torrentio calls
    private fun rankStreams(streams: List<TorrentioStream>): List<TorrentioStream> =
        streams.sortedWith(
            compareByDescending<TorrentioStream> { parseQuality(it) }
                .thenByDescending { parseSeeders(it) }
        )

    // ─── Merged search: TorBox Search API + Torrentio ──────────────────────────

    /**
     * Merged movie search: tries TorBox Search API first (parallel to Torrentio).
     * TorBox Search results are ranked first (cached, no blocked indexers).
     * Torrentio results fill gaps, deduplicated by hash.
     * Uses supervisorScope so one source failure doesn't poison the merged result.
     */
    suspend fun fetchMovieStreams(imdbId: String): List<UnifiedTorrent> = supervisorScope {
        val torboxDeferred = async { fetchTorBoxSearchMovies(imdbId) }
        val torrentioDeferred = async { fetchTorrentioMovies(imdbId) }

        val torbox = try { torboxDeferred.await() } catch (e: Exception) { if (e is CancellationException) throw e;
            Log.w(TAG, "TorBox Search await failed: ${e.message}")
            emptyList()
        }
        val tio = try { torrentioDeferred.await() } catch (e: Exception) { if (e is CancellationException) throw e;
            Log.w(TAG, "Torrentio await failed: ${e.message}")
            emptyList()
        }

        val torboxHashes = torbox.mapNotNull { it.hash?.lowercase() }.toSet()
        val merged = torbox.toMutableList()
        merged += tio.filter { t -> t.hash?.lowercase()?.let { it !in torboxHashes } ?: true }

        Log.d(TAG, "fetchMovieStreams imdb=$imdbId: ${torbox.size} TorBox + ${tio.size} Torrentio = ${merged.size} merged")
        rankUnified(merged)
    }

    /**
     * Merged episode search: same strategy as movies.
     * Uses supervisorScope so one source failure doesn't poison the merged result.
     */
    suspend fun fetchEpisodeStreams(imdbId: String, season: Int, episode: Int): List<UnifiedTorrent> = supervisorScope {
        val torboxDeferred = async { fetchTorBoxSearchEpisodes(imdbId, season, episode) }
        val torrentioDeferred = async { fetchTorrentioEpisodes(imdbId, season, episode) }

        val torbox = try { torboxDeferred.await() } catch (e: Exception) { if (e is CancellationException) throw e;
            Log.w(TAG, "TorBox Search episode await failed: ${e.message}")
            emptyList()
        }
        val tio = try { torrentioDeferred.await() } catch (e: Exception) { if (e is CancellationException) throw e;
            Log.w(TAG, "Torrentio episode await failed: ${e.message}")
            emptyList()
        }

        val torboxHashes = torbox.mapNotNull { it.hash?.lowercase() }.toSet()
        val merged = torbox.toMutableList()
        merged += tio.filter { t -> t.hash?.lowercase()?.let { it !in torboxHashes } ?: true }

        Log.d(TAG, "fetchEpisodeStreams imdb=$imdbId S${season}E${episode}: ${torbox.size} TorBox + ${tio.size} Torrentio = ${merged.size} merged")
        rankUnified(merged)
    }

    private suspend fun fetchTorBoxSearchMovies(imdbId: String): List<UnifiedTorrent> {
        return try {
            torBoxSearch.searchMovieTorrents(imdbId, checkCache = true, checkOwned = true)
                .map { searchToUnified(it) }
        } catch (e: Exception) { if (e is CancellationException) throw e;
            Log.w(TAG, "TorBox Search movie failed: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchTorBoxSearchEpisodes(imdbId: String, season: Int, episode: Int): List<UnifiedTorrent> {
        return try {
            torBoxSearch.searchEpisodeTorrents(imdbId, season, episode, checkCache = true, checkOwned = true)
                .map { searchToUnified(it) }
        } catch (e: Exception) { if (e is CancellationException) throw e;
            Log.w(TAG, "TorBox Search episode failed: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchTorrentioMovies(imdbId: String): List<UnifiedTorrent> {
        return try {
            torrentio.getMovieStream(TORBOX_CONFIG, imdbId).streams.map { torrentioToUnified(it) }
        } catch (e: Exception) { if (e is CancellationException) throw e;
            Log.w(TAG, "Torrentio movie failed: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchTorrentioEpisodes(imdbId: String, season: Int, episode: Int): List<UnifiedTorrent> {
        return try {
            torrentio.getEpisodeStream(TORBOX_CONFIG, imdbId, season, episode).streams.map { torrentioToUnified(it) }
        } catch (e: Exception) { if (e is CancellationException) throw e;
            Log.w(TAG, "Torrentio episode failed: ${e.message}")
            emptyList()
        }
    }

    // ─── Resolution (add to TorBox, poll, return download URL) ────────────────

    private fun largestVideoFile(files: List<TorBoxFile>) =
        files.filter { f -> f.name.endsWith(".mp4") || f.name.endsWith(".mkv") || f.name.endsWith(".avi") }
            .maxByOrNull { it.size }

    private suspend fun getDownloadUrl(torrentId: Int, files: List<TorBoxFile>): String? {
        val video = largestVideoFile(files) ?: return null
        return torBox.requestDownloadLink(torrentId, video.id)
    }

    /**
     * Auto-walk helper: tries each fallback torrent in sequence with a 30s budget each.
     * Emits TryingNextStream before each attempt, Ready on success, Failed when exhausted.
     */
    private suspend fun walkFallbacks(
        emit: (StreamResolution) -> Unit,
        fallbacks: List<UnifiedTorrent>,
        currentAttempt: Int,
        total: Int
    ) {
        if (fallbacks.isEmpty()) {
            emit(StreamResolution.Failed("All streams unavailable"))
            return
        }
        val next = fallbacks.first()
        val remaining = fallbacks.drop(1)
        emit(StreamResolution.TryingNextStream(attempt = currentAttempt, total = total))
        resolveSelectedTorrent(next, remaining.mapNotNull { it.hash }).collect { res ->
            emit(res)
            if (res is StreamResolution.Ready || res is StreamResolution.Failed) return@collect
        }
    }

    fun resolveMovie(imdbId: String): Flow<StreamResolution> = flow {
        emit(StreamResolution.Searching)

        val unified = try {
            fetchMovieStreams(imdbId)
        } catch (e: Exception) { if (e is CancellationException) throw e;
            emit(StreamResolution.Failed("Search failed: ${e.message}")); return@flow
        }

        if (unified.isEmpty()) { emit(StreamResolution.Failed("No streams found")); return@flow }

        val hashes = unified.mapNotNull { it.hash }.distinct()
        val fallbackHashes = unified.drop(1).mapNotNull { it.hash }.take(5)

        val cachedMap = try { torBox.checkCached(hashes) } catch (e: Exception) { if (e is CancellationException) throw e; emptyMap() }

        val best = unified.firstOrNull { t ->
            t.hash?.let { cachedMap[it.lowercase()] == true || cachedMap[it] == true } == true
        } ?: unified.firstOrNull { it.hash != null } ?: unified.first()

        if (best.hash == null || best.url.isBlank()) {
            emit(StreamResolution.Failed("No valid torrent found")); return@flow
        }

        emit(StreamResolution.Queuing)
        val result = try { torBox.addMagnet(best.url) } catch (e: Exception) { if (e is CancellationException) throw e;
            TorBoxAddResult(success = false, error = e.message)
        }
        if (!result.success || result.torrentId == null) {
            emit(StreamResolution.Failed(result.message ?: "Failed to queue torrent")); return@flow
        }
        val torrentId = result.torrentId!!

        val isCached = best.hash?.let { cachedMap[it.lowercase()] == true || cachedMap[it] == true } == true

        if (isCached) {
            emit(StreamResolution.Caching(100))
            val info = try { torBox.getTorrentInfo(torrentId) } catch (e: Exception) { if (e is CancellationException) throw e; null }
            if (info != null) {
                val url = getDownloadUrl(torrentId, info.files)
                if (url != null) { emit(StreamResolution.Ready(url, fallbackHashes)); return@flow }
            }
        }

        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 30_000L) {
            delay(2_000)
            val info = try { torBox.getTorrentInfo(torrentId) } catch (e: Exception) { if (e is CancellationException) throw e; null }
            if (info != null) {
                val pct = (info.percentDone * 100).toInt().coerceIn(0, 99)
                if (info.isCompleted) {
                    val url = getDownloadUrl(torrentId, info.files)
                    if (url != null) { emit(StreamResolution.Ready(url, fallbackHashes)); return@flow }
                }
                emit(StreamResolution.Caching(pct))
            }
        }

        // Timed out — walk fallbacks inline (30s per hash)
        val hash = best.hash!!
        if (fallbackHashes.isNotEmpty()) {
            val allHashes = listOf(hash) + fallbackHashes
            for ((idx, h) in allHashes.withIndex()) {
                if (idx == 0) continue // already tried idx=0 (the selected torrent)
                emit(StreamResolution.TryingNextStream(attempt = idx + 1, total = allHashes.size))
                // resolve this fallback torrent directly (no re-search needed)
                val magnet = parseMagnetFromHash(h)
                val addResult = try { torBox.addMagnet(magnet) } catch (e: Exception) { if (e is CancellationException) throw e;
                    null
                }
                if (addResult?.success == true && addResult.torrentId != null) {
                    val fbTorrentId = addResult.torrentId!!
                    val fbCached = try { torBox.checkCached(listOf(h)) } catch (e: Exception) { if (e is CancellationException) throw e; emptyMap() }
                    if (fbCached[h.lowercase()] == true || fbCached[h] == true) {
                        emit(StreamResolution.Caching(100))
                        val fbInfo = try { torBox.getTorrentInfo(fbTorrentId) } catch (e: Exception) { if (e is CancellationException) throw e; null }
                        if (fbInfo != null) {
                            val fbUrl = getDownloadUrl(fbTorrentId, fbInfo.files)
                            if (fbUrl != null) { emit(StreamResolution.Ready(fbUrl, emptyList())); return@flow }
                        }
                    }
                    val fbStart = System.currentTimeMillis()
                    while (System.currentTimeMillis() - fbStart < 30_000L) {
                        delay(2_000)
                        val fbInfo = try { torBox.getTorrentInfo(fbTorrentId) } catch (e: Exception) { if (e is CancellationException) throw e; null }
                        if (fbInfo != null) {
                            val fbPct = (fbInfo.percentDone * 100).toInt().coerceIn(0, 99)
                            if (fbInfo.isCompleted) {
                                val fbUrl = getDownloadUrl(fbTorrentId, fbInfo.files)
                                if (fbUrl != null) { emit(StreamResolution.Ready(fbUrl, emptyList())); return@flow }
                            }
                            emit(StreamResolution.Caching(fbPct))
                        }
                    }
                }
            }
            emit(StreamResolution.Failed("All streams unavailable"))
        } else {
            emit(StreamResolution.Failed("Timed out preparing movie"))
        }
    }

    /**
     * Playback for a user-selected torrent — no re-search.
     * Uses the provided UnifiedTorrent directly; fallbackHashes come from
     * the remaining streams in the selection list.
     * Each hash gets a 30s budget; falls back automatically on timeout.
     */
    fun resolveSelectedTorrent(
        selected: UnifiedTorrent,
        fallbackHashes: List<String>
    ): Flow<StreamResolution> = flow {
        emit(StreamResolution.Searching)

        if (selected.hash == null || selected.url.isBlank()) {
            emit(StreamResolution.Failed("No valid torrent found")); return@flow
        }

        val hash = selected.hash
        val cachedMap = try { torBox.checkCached(listOf(hash)) } catch (e: Exception) { if (e is CancellationException) throw e; emptyMap() }
        val isCached = cachedMap[hash.lowercase()] == true || cachedMap[hash] == true

        emit(StreamResolution.Queuing)
        val result = try { torBox.addMagnet(selected.url) } catch (e: Exception) { if (e is CancellationException) throw e;
            TorBoxAddResult(success = false, error = e.message)
        }
        if (!result.success || result.torrentId == null) {
            emit(StreamResolution.Failed(result.message ?: "Failed to queue torrent")); return@flow
        }
        val torrentId = result.torrentId!!

        if (isCached) {
            emit(StreamResolution.Caching(100))
            val info = try { torBox.getTorrentInfo(torrentId) } catch (e: Exception) { if (e is CancellationException) throw e; null }
            if (info != null) {
                val url = getDownloadUrl(torrentId, info.files)
                if (url != null) { emit(StreamResolution.Ready(url, fallbackHashes)); return@flow }
            }
        }

        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 30_000L) {
            delay(2_000)
            val info = try { torBox.getTorrentInfo(torrentId) } catch (e: Exception) { if (e is CancellationException) throw e; null }
            if (info != null) {
                val pct = (info.percentDone * 100).toInt().coerceIn(0, 99)
                if (info.isCompleted) {
                    val url = getDownloadUrl(torrentId, info.files)
                    if (url != null) { emit(StreamResolution.Ready(url, fallbackHashes)); return@flow }
                }
                emit(StreamResolution.Caching(pct))
            }
        }

        // Timed out — walk fallbacks inline (30s per hash)
        if (fallbackHashes.isNotEmpty()) {
            val allHashes = listOf(hash) + fallbackHashes
            for ((idx, h) in allHashes.withIndex()) {
                if (idx == 0) continue // already tried idx=0 (the selected torrent)
                emit(StreamResolution.TryingNextStream(attempt = idx + 1, total = allHashes.size))
                val magnet = try { parseMagnetFromHash(h) } catch (e: Exception) { if (e is CancellationException) throw e;
                    null
                }
                if (magnet != null) {
                    val addResult = try { torBox.addMagnet(magnet) } catch (e: Exception) { if (e is CancellationException) throw e;
                        null
                    }
                    if (addResult?.success == true && addResult.torrentId != null) {
                        val fbTorrentId = addResult.torrentId!!
                        val fbCached = try { torBox.checkCached(listOf(h)) } catch (e: Exception) { if (e is CancellationException) throw e; emptyMap() }
                        if (fbCached[h.lowercase()] == true || fbCached[h] == true) {
                            emit(StreamResolution.Caching(100))
                            val fbInfo = try { torBox.getTorrentInfo(fbTorrentId) } catch (e: Exception) { if (e is CancellationException) throw e; null }
                            if (fbInfo != null) {
                                val fbUrl = getDownloadUrl(fbTorrentId, fbInfo.files)
                                if (fbUrl != null) { emit(StreamResolution.Ready(fbUrl, emptyList())); return@flow }
                            }
                        }
                        val fbStart = System.currentTimeMillis()
                        while (System.currentTimeMillis() - fbStart < 30_000L) {
                            delay(2_000)
                            val fbInfo = try { torBox.getTorrentInfo(fbTorrentId) } catch (e: Exception) { if (e is CancellationException) throw e; null }
                            if (fbInfo != null) {
                                val fbPct = (fbInfo.percentDone * 100).toInt().coerceIn(0, 99)
                                if (fbInfo.isCompleted) {
                                    val fbUrl = getDownloadUrl(fbTorrentId, fbInfo.files)
                                    if (fbUrl != null) { emit(StreamResolution.Ready(fbUrl, emptyList())); return@flow }
                                }
                                emit(StreamResolution.Caching(fbPct))
                            }
                        }
                    }
                }
            }
            emit(StreamResolution.Failed("All streams unavailable"))
        } else {
            emit(StreamResolution.Failed("Timed out preparing movie"))
        }
    }

    /**
     * Playback for a user-selected episode torrent — no re-search.
     * Mirrors resolveSelectedTorrent for the episode case.
     */
    fun resolveSelectedEpisodeTorrent(
        selected: UnifiedTorrent,
        fallbackHashes: List<String>
    ): Flow<StreamResolution> = flow {
        emit(StreamResolution.Searching)

        if (selected.hash == null || selected.url.isBlank()) {
            emit(StreamResolution.Failed("No valid episode torrent found")); return@flow
        }

        val hash = selected.hash
        val cachedMap = try { torBox.checkCached(listOf(hash)) } catch (e: Exception) { if (e is CancellationException) throw e; emptyMap() }
        val isCached = cachedMap[hash.lowercase()] == true || cachedMap[hash] == true

        emit(StreamResolution.Queuing)
        val result = try { torBox.addMagnet(selected.url) } catch (e: Exception) { if (e is CancellationException) throw e;
            TorBoxAddResult(success = false, error = e.message)
        }
        if (!result.success || result.torrentId == null) {
            emit(StreamResolution.Failed(result.message ?: "Failed to queue episode")); return@flow
        }
        val torrentId = result.torrentId!!

        if (isCached) {
            emit(StreamResolution.Caching(100))
            val info = try { torBox.getTorrentInfo(torrentId) } catch (e: Exception) { if (e is CancellationException) throw e; null }
            if (info != null) {
                val url = getDownloadUrl(torrentId, info.files)
                if (url != null) { emit(StreamResolution.Ready(url, fallbackHashes)); return@flow }
            }
        }

        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 30_000L) {
            delay(2_000)
            val info = try { torBox.getTorrentInfo(torrentId) } catch (e: Exception) { if (e is CancellationException) throw e; null }
            if (info != null) {
                val pct = (info.percentDone * 100).toInt().coerceIn(0, 99)
                if (info.isCompleted) {
                    val url = getDownloadUrl(torrentId, info.files)
                    if (url != null) { emit(StreamResolution.Ready(url, fallbackHashes)); return@flow }
                }
                emit(StreamResolution.Caching(pct))
            }
        }

        // Timed out — walk fallbacks inline (30s per hash)
        if (fallbackHashes.isNotEmpty()) {
            val allHashes = listOf(hash) + fallbackHashes
            for ((idx, h) in allHashes.withIndex()) {
                if (idx == 0) continue // already tried idx=0
                emit(StreamResolution.TryingNextStream(attempt = idx + 1, total = allHashes.size))
                val magnet = parseMagnetFromHash(h)
                val addResult = try { torBox.addMagnet(magnet) } catch (e: Exception) { if (e is CancellationException) throw e;
                    null
                }
                if (addResult?.success == true && addResult.torrentId != null) {
                    val fbTorrentId = addResult.torrentId!!
                    val fbCached = try { torBox.checkCached(listOf(h)) } catch (e: Exception) { if (e is CancellationException) throw e; emptyMap() }
                    if (fbCached[h.lowercase()] == true || fbCached[h] == true) {
                        emit(StreamResolution.Caching(100))
                        val fbInfo = try { torBox.getTorrentInfo(fbTorrentId) } catch (e: Exception) { if (e is CancellationException) throw e; null }
                        if (fbInfo != null) {
                            val fbUrl = getDownloadUrl(fbTorrentId, fbInfo.files)
                            if (fbUrl != null) { emit(StreamResolution.Ready(fbUrl, emptyList())); return@flow }
                        }
                    }
                    val fbStart = System.currentTimeMillis()
                    while (System.currentTimeMillis() - fbStart < 30_000L) {
                        delay(2_000)
                        val fbInfo = try { torBox.getTorrentInfo(fbTorrentId) } catch (e: Exception) { if (e is CancellationException) throw e; null }
                        if (fbInfo != null) {
                            val fbPct = (fbInfo.percentDone * 100).toInt().coerceIn(0, 99)
                            if (fbInfo.isCompleted) {
                                val fbUrl = getDownloadUrl(fbTorrentId, fbInfo.files)
                                if (fbUrl != null) { emit(StreamResolution.Ready(fbUrl, emptyList())); return@flow }
                            }
                            emit(StreamResolution.Caching(fbPct))
                        }
                    }
                }
            }
            emit(StreamResolution.Failed("All streams unavailable"))
        } else {
            emit(StreamResolution.Failed("Timed out preparing episode"))
        }
    }

    fun resolveEpisode(imdbId: String, season: Int, episode: Int): Flow<StreamResolution> = flow {
        emit(StreamResolution.Searching)

        val unified = try {
            fetchEpisodeStreams(imdbId, season, episode)
        } catch (e: Exception) { if (e is CancellationException) throw e;
            emit(StreamResolution.Failed("Search failed: ${e.message}")); return@flow
        }

        if (unified.isEmpty()) { emit(StreamResolution.Failed("No streams found")); return@flow }

        val hashes = unified.mapNotNull { it.hash }.distinct()
        val fallbackHashes = unified.drop(1).mapNotNull { it.hash }.take(5)

        val cachedMap = try { torBox.checkCached(hashes) } catch (e: Exception) { if (e is CancellationException) throw e; emptyMap() }

        val best = unified.firstOrNull { t ->
            t.hash?.let { cachedMap[it.lowercase()] == true || cachedMap[it] == true } == true
        } ?: unified.firstOrNull { it.hash != null } ?: unified.first()

        if (best.hash == null || best.url.isBlank()) {
            emit(StreamResolution.Failed("No valid torrent found")); return@flow
        }

        emit(StreamResolution.Queuing)
        val result = try { torBox.addMagnet(best.url) } catch (e: Exception) { if (e is CancellationException) throw e;
            TorBoxAddResult(success = false, error = e.message)
        }
        if (!result.success || result.torrentId == null) {
            emit(StreamResolution.Failed(result.message ?: "Failed to queue episode")); return@flow
        }
        val torrentId = result.torrentId!!

        val isCached = best.hash?.let { cachedMap[it.lowercase()] == true || cachedMap[it] == true } == true

        if (isCached) {
            emit(StreamResolution.Caching(100))
            val info = try { torBox.getTorrentInfo(torrentId) } catch (e: Exception) { if (e is CancellationException) throw e; null }
            if (info != null) {
                val url = getDownloadUrl(torrentId, info.files)
                if (url != null) { emit(StreamResolution.Ready(url, fallbackHashes)); return@flow }
            }
        }

        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 30_000L) {
            delay(2_000)
            val info = try { torBox.getTorrentInfo(torrentId) } catch (e: Exception) { if (e is CancellationException) throw e; null }
            if (info != null) {
                val pct = (info.percentDone * 100).toInt().coerceIn(0, 99)
                if (info.isCompleted) {
                    val url = getDownloadUrl(torrentId, info.files)
                    if (url != null) { emit(StreamResolution.Ready(url, fallbackHashes)); return@flow }
                }
                emit(StreamResolution.Caching(pct))
            }
        }

        // Timed out — walk fallbacks inline (30s per hash)
        val hash = best.hash!!
        if (fallbackHashes.isNotEmpty()) {
            val allHashes = listOf(hash) + fallbackHashes
            for ((idx, h) in allHashes.withIndex()) {
                if (idx == 0) continue // already tried idx=0 (the selected torrent)
                emit(StreamResolution.TryingNextStream(attempt = idx + 1, total = allHashes.size))
                val magnet = try { parseMagnetFromHash(h) } catch (e: Exception) { if (e is CancellationException) throw e;
                    null
                }
                if (magnet != null) {
                    val addResult = try { torBox.addMagnet(magnet) } catch (e: Exception) { if (e is CancellationException) throw e;
                        null
                    }
                    if (addResult?.success == true && addResult.torrentId != null) {
                        val fbTorrentId = addResult.torrentId!!
                        val fbCached = try { torBox.checkCached(listOf(h)) } catch (e: Exception) { if (e is CancellationException) throw e; emptyMap() }
                        if (fbCached[h.lowercase()] == true || fbCached[h] == true) {
                            emit(StreamResolution.Caching(100))
                            val fbInfo = try { torBox.getTorrentInfo(fbTorrentId) } catch (e: Exception) { if (e is CancellationException) throw e; null }
                            if (fbInfo != null) {
                                val fbUrl = getDownloadUrl(fbTorrentId, fbInfo.files)
                                if (fbUrl != null) { emit(StreamResolution.Ready(fbUrl, emptyList())); return@flow }
                            }
                        }
                        val fbStart = System.currentTimeMillis()
                        while (System.currentTimeMillis() - fbStart < 30_000L) {
                            delay(2_000)
                            val fbInfo = try { torBox.getTorrentInfo(fbTorrentId) } catch (e: Exception) { if (e is CancellationException) throw e; null }
                            if (fbInfo != null) {
                                val fbPct = (fbInfo.percentDone * 100).toInt().coerceIn(0, 99)
                                if (fbInfo.isCompleted) {
                                    val fbUrl = getDownloadUrl(fbTorrentId, fbInfo.files)
                                    if (fbUrl != null) { emit(StreamResolution.Ready(fbUrl, emptyList())); return@flow }
                                }
                                emit(StreamResolution.Caching(fbPct))
                            }
                        }
                    }
                }
            }
            emit(StreamResolution.Failed("All streams unavailable"))
        } else {
            emit(StreamResolution.Failed("Timed out preparing episode"))
        }
    }

    fun resolveFallback(hash: String): Flow<StreamResolution> = flow {
        emit(StreamResolution.Queuing)
        val magnet = parseMagnetFromHash(hash)
        val result = try { torBox.addMagnet(magnet) } catch (e: Exception) { if (e is CancellationException) throw e;
            TorBoxAddResult(success = false, error = e.message)
        }
        if (!result.success || result.torrentId == null) {
            emit(StreamResolution.Failed(result.message ?: "Failed to queue fallback torrent")); return@flow
        }
        val torrentId = result.torrentId!!

        emit(StreamResolution.Caching(100))
        val info = try { torBox.getTorrentInfo(torrentId) } catch (e: Exception) { if (e is CancellationException) throw e; null }
        if (info != null) {
            val url = getDownloadUrl(torrentId, info.files)
            if (url != null) { emit(StreamResolution.Ready(url)); return@flow }
        }

        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 30_000L) {
            delay(2_000)
            val torrentInfo = try { torBox.getTorrentInfo(torrentId) } catch (e: Exception) { if (e is CancellationException) throw e; null }
            if (torrentInfo != null) {
                val pct = (torrentInfo.percentDone * 100).toInt().coerceIn(0, 99)
                if (torrentInfo.isCompleted) {
                    val dlUrl = getDownloadUrl(torrentId, torrentInfo.files)
                    if (dlUrl != null) { emit(StreamResolution.Ready(dlUrl)); return@flow }
                }
                emit(StreamResolution.Caching(pct))
            }
        }
        emit(StreamResolution.Failed("Timed out preparing fallback stream"))
    }

    // ─── Speculative prefetch (used for hero/preview) ─────────────────────────

    /**
     * Fast one-shot: get best cached magnet URL for a movie, no polling.
     * Uses merged search (TorBox Search + Torrentio).
     */
    suspend fun resolveFirstMovie(imdbId: String): String? {
        val unified = try { fetchMovieStreams(imdbId) } catch (_: Exception) { return null }
        val hashes = unified.mapNotNull { it.hash }.distinct()
        val cachedMap = try { torBox.checkCached(hashes) } catch (_: Exception) { emptyMap() }
        val best = unified.firstOrNull { t ->
            t.hash?.let { cachedMap[it.lowercase()] == true || cachedMap[it] == true } == true
        } ?: unified.firstOrNull { it.hash != null }
        return best?.url
    }

    /**
     * Fast one-shot: get best cached magnet URL for an episode, no polling.
     * Uses merged search (TorBox Search + Torrentio).
     */
    suspend fun resolveFirst(imdbId: String, season: Int, episode: Int): String? {
        val unified = try { fetchEpisodeStreams(imdbId, season, episode) } catch (_: Exception) { return null }
        val hashes = unified.mapNotNull { it.hash }.distinct()
        val cachedMap = try { torBox.checkCached(hashes) } catch (_: Exception) { emptyMap() }
        val best = unified.firstOrNull { t ->
            t.hash?.let { cachedMap[it.lowercase()] == true || cachedMap[it] == true } == true
        } ?: unified.firstOrNull { it.hash != null }
        return best?.url
    }
}
