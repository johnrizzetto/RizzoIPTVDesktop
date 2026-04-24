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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed class StreamResolution {
    data object Searching : StreamResolution()
    data object Queuing : StreamResolution()
    data class Caching(val percent: Int) : StreamResolution()
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
     */
    suspend fun fetchMovieStreams(imdbId: String): List<UnifiedTorrent> = coroutineScope {
        val torboxDeferred = async { fetchTorBoxSearchMovies(imdbId) }
        val torrentioDeferred = async { fetchTorrentioMovies(imdbId) }

        val torbox = torboxDeferred.await()
        val tio = torrentioDeferred.await()

        val torboxHashes = torbox.mapNotNull { it.hash?.lowercase() }.toSet()
        val merged = torbox.toMutableList()
        merged += tio.filter { t -> t.hash?.lowercase()?.let { it !in torboxHashes } ?: true }

        Log.d(TAG, "fetchMovieStreams imdb=$imdbId: ${torbox.size} TorBox + ${tio.size} Torrentio = ${merged.size} merged")
        rankUnified(merged)
    }

    /**
     * Merged episode search: same strategy as movies.
     */
    suspend fun fetchEpisodeStreams(imdbId: String, season: Int, episode: Int): List<UnifiedTorrent> = coroutineScope {
        val torboxDeferred = async { fetchTorBoxSearchEpisodes(imdbId, season, episode) }
        val torrentioDeferred = async { fetchTorrentioEpisodes(imdbId, season, episode) }

        val torbox = torboxDeferred.await()
        val tio = torrentioDeferred.await()

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
        } catch (e: Exception) {
            Log.w(TAG, "TorBox Search movie failed: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchTorBoxSearchEpisodes(imdbId: String, season: Int, episode: Int): List<UnifiedTorrent> {
        return try {
            torBoxSearch.searchEpisodeTorrents(imdbId, season, episode, checkCache = true, checkOwned = true)
                .map { searchToUnified(it) }
        } catch (e: Exception) {
            Log.w(TAG, "TorBox Search episode failed: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchTorrentioMovies(imdbId: String): List<UnifiedTorrent> {
        return try {
            torrentio.getMovieStream(TORBOX_CONFIG, imdbId).streams.map { torrentioToUnified(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Torrentio movie failed: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchTorrentioEpisodes(imdbId: String, season: Int, episode: Int): List<UnifiedTorrent> {
        return try {
            torrentio.getEpisodeStream(TORBOX_CONFIG, imdbId, season, episode).streams.map { torrentioToUnified(it) }
        } catch (e: Exception) {
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

    fun resolveMovie(imdbId: String): Flow<StreamResolution> = flow {
        emit(StreamResolution.Searching)

        val unified = try {
            fetchMovieStreams(imdbId)
        } catch (e: Exception) {
            emit(StreamResolution.Failed("Search failed: ${e.message}")); return@flow
        }

        if (unified.isEmpty()) { emit(StreamResolution.Failed("No streams found")); return@flow }

        // Prefer best quality + cached (TorBox Search source)
        val hashes = unified.mapNotNull { it.hash }.distinct()
        val fallbackHashes = unified.drop(1).mapNotNull { it.hash }.take(5)

        val cachedMap = try { torBox.checkCached(hashes) } catch (e: Exception) { emptyMap() }

        val best = unified.firstOrNull { t ->
            t.hash?.let { cachedMap[it.lowercase()] == true || cachedMap[it] == true } == true
        } ?: unified.firstOrNull { it.hash != null } ?: unified.first()

        if (best.hash == null || best.url.isBlank()) {
            emit(StreamResolution.Failed("No valid torrent found")); return@flow
        }

        emit(StreamResolution.Queuing)
        val result = try { torBox.addMagnet(best.url) } catch (e: Exception) {
            TorBoxAddResult(success = false, error = e.message)
        }
        if (!result.success || result.torrentId == null) {
            emit(StreamResolution.Failed(result.message ?: "Failed to queue torrent")); return@flow
        }
        val torrentId = result.torrentId!!

        val isCached = best.hash?.let { cachedMap[it.lowercase()] == true || cachedMap[it] == true } == true

        if (isCached) {
            emit(StreamResolution.Caching(100))
            val info = try { torBox.getTorrentInfo(torrentId) } catch (e: Exception) { null }
            if (info != null) {
                val url = getDownloadUrl(torrentId, info.files)
                if (url != null) { emit(StreamResolution.Ready(url, fallbackHashes)); return@flow }
            }
        }

        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 90_000L) {
            delay(2_000)
            val info = try { torBox.getTorrentInfo(torrentId) } catch (e: Exception) { null }
            if (info != null) {
                val pct = (info.percentDone * 100).toInt().coerceIn(0, 99)
                if (info.isCompleted) {
                    val url = getDownloadUrl(torrentId, info.files)
                    if (url != null) { emit(StreamResolution.Ready(url, fallbackHashes)); return@flow }
                }
                emit(StreamResolution.Caching(pct))
            }
        }
        emit(StreamResolution.Failed("Timed out preparing movie"))
    }

    fun resolveEpisode(imdbId: String, season: Int, episode: Int): Flow<StreamResolution> = flow {
        emit(StreamResolution.Searching)

        val unified = try {
            fetchEpisodeStreams(imdbId, season, episode)
        } catch (e: Exception) {
            emit(StreamResolution.Failed("Search failed: ${e.message}")); return@flow
        }

        if (unified.isEmpty()) { emit(StreamResolution.Failed("No streams found")); return@flow }

        val hashes = unified.mapNotNull { it.hash }.distinct()
        val fallbackHashes = unified.drop(1).mapNotNull { it.hash }.take(5)

        val cachedMap = try { torBox.checkCached(hashes) } catch (e: Exception) { emptyMap() }

        val best = unified.firstOrNull { t ->
            t.hash?.let { cachedMap[it.lowercase()] == true || cachedMap[it] == true } == true
        } ?: unified.firstOrNull { it.hash != null } ?: unified.first()

        if (best.hash == null || best.url.isBlank()) {
            emit(StreamResolution.Failed("No valid torrent found")); return@flow
        }

        emit(StreamResolution.Queuing)
        val result = try { torBox.addMagnet(best.url) } catch (e: Exception) {
            TorBoxAddResult(success = false, error = e.message)
        }
        if (!result.success || result.torrentId == null) {
            emit(StreamResolution.Failed(result.message ?: "Failed to queue episode")); return@flow
        }
        val torrentId = result.torrentId!!

        val isCached = best.hash?.let { cachedMap[it.lowercase()] == true || cachedMap[it] == true } == true

        if (isCached) {
            emit(StreamResolution.Caching(100))
            val info = try { torBox.getTorrentInfo(torrentId) } catch (e: Exception) { null }
            if (info != null) {
                val url = getDownloadUrl(torrentId, info.files)
                if (url != null) { emit(StreamResolution.Ready(url, fallbackHashes)); return@flow }
            }
        }

        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 90_000L) {
            delay(2_000)
            val info = try { torBox.getTorrentInfo(torrentId) } catch (e: Exception) { null }
            if (info != null) {
                val pct = (info.percentDone * 100).toInt().coerceIn(0, 99)
                if (info.isCompleted) {
                    val url = getDownloadUrl(torrentId, info.files)
                    if (url != null) { emit(StreamResolution.Ready(url, fallbackHashes)); return@flow }
                }
                emit(StreamResolution.Caching(pct))
            }
        }
        emit(StreamResolution.Failed("Timed out preparing episode"))
    }

    fun resolveFallback(hash: String): Flow<StreamResolution> = flow {
        emit(StreamResolution.Queuing)
        val magnet = parseMagnetFromHash(hash)
        val result = try { torBox.addMagnet(magnet) } catch (e: Exception) {
            TorBoxAddResult(success = false, error = e.message)
        }
        if (!result.success || result.torrentId == null) {
            emit(StreamResolution.Failed(result.message ?: "Failed to queue fallback torrent")); return@flow
        }
        val torrentId = result.torrentId!!

        emit(StreamResolution.Caching(100))
        val info = try { torBox.getTorrentInfo(torrentId) } catch (e: Exception) { null }
        if (info != null) {
            val url = getDownloadUrl(torrentId, info.files)
            if (url != null) { emit(StreamResolution.Ready(url)); return@flow }
        }

        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 90_000L) {
            delay(2_000)
            val torrentInfo = try { torBox.getTorrentInfo(torrentId) } catch (e: Exception) { null }
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
