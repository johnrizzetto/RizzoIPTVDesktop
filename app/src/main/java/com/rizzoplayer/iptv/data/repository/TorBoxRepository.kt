package com.rizzoplayer.iptv.data.repository

import com.rizzoplayer.iptv.BuildConfig
import com.rizzoplayer.iptv.data.api.TorBoxApiService
import com.rizzoplayer.iptv.data.api.TorrentioService
import com.rizzoplayer.iptv.data.model.TorrentioStream
import com.rizzoplayer.iptv.data.model.TorBoxAddResult
import com.rizzoplayer.iptv.data.model.TorBoxFile
import com.rizzoplayer.iptv.data.model.TorBoxTorrent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed class StreamResolution {
    data object Searching : StreamResolution()
    data object Queuing : StreamResolution()
    data class Caching(val percent: Int) : StreamResolution()
    data class Ready(val url: String) : StreamResolution()
    data class Failed(val reason: String) : StreamResolution()
}

class TorBoxRepository(
    private val torBox: TorBoxApiService,
    private val torrentio: TorrentioService
) {
    private val TORBOX_CONFIG = "torbox=${BuildConfig.TORBOX_API_KEY}"

    private fun parseInfoHash(stream: TorrentioStream): String? {
        val url = stream.url
        return if (url.startsWith("magnet:?xt=urn:btih:")) {
            url.removePrefix("magnet:?xt=urn:btih:").split("&").firstOrNull()
        } else null
    }

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

    private fun rankStreams(streams: List<TorrentioStream>): List<TorrentioStream> =
        streams.sortedWith(
            compareByDescending<TorrentioStream> { parseQuality(it) }
                .thenByDescending { parseSeeders(it) }
        )

    private fun largestVideoFile(files: List<TorBoxFile>) =
        files.filter { f -> f.name.endsWith(".mp4") || f.name.endsWith(".mkv") || f.name.endsWith(".avi") }
            .maxByOrNull { it.size }

    private suspend fun getDownloadUrl(torrentId: Int, files: List<TorBoxFile>): String? {
        val video = largestVideoFile(files) ?: return null
        return torBox.requestDownloadLink(torrentId, video.id)
    }

    fun resolveMovie(imdbId: String): Flow<StreamResolution> = flow {
        emit(StreamResolution.Searching)

        val streams = try {
            torrentio.getMovieStream(TORBOX_CONFIG, imdbId).streams
        } catch (e: Exception) {
            emit(StreamResolution.Failed("Failed to search: ${e.message}")); return@flow
        }

        if (streams.isEmpty()) { emit(StreamResolution.Failed("No streams found")); return@flow }

        val ranked = rankStreams(streams)
        val hashes = ranked.mapNotNull { parseInfoHash(it) }.distinct()

        val cachedMap = try { torBox.checkCached(hashes) } catch (e: Exception) { emptyMap() }

        // Find best stream: prefer cached, else highest quality
        val bestStream = ranked.firstOrNull { s ->
            parseInfoHash(s)?.let { cachedMap[it] == true } == true
        } ?: ranked.first()

        val bestHash = parseInfoHash(bestStream)
        val isCached = bestHash?.let { cachedMap[it] == true } == true

        // Always call addMagnet to get a torrentId (works for both cached and uncached)
        emit(StreamResolution.Queuing)
        val result = try { torBox.addMagnet(bestStream.url) } catch (e: Exception) {
            TorBoxAddResult(success = false, error = e.message)
        }
        if (!result.success || result.torrentId == null) {
            emit(StreamResolution.Failed(result.message ?: "Failed to queue torrent")); return@flow
        }
        val torrentId = result.torrentId!!

        if (isCached) {
            // Cached: skip polling, go straight to getting the download link
            emit(StreamResolution.Caching(100))
            val info = try { torBox.getTorrentInfo(torrentId) } catch (e: Exception) { null }
            if (info != null) {
                val url = getDownloadUrl(torrentId, info.files)
                if (url != null) { emit(StreamResolution.Ready(url)); return@flow }
            }
            // Fall through to polling if something went wrong
        }

        // Poll until ready or timeout
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 90_000L) {
            delay(2_000)
            val info = try { torBox.getTorrentInfo(torrentId) } catch (e: Exception) { null }
            if (info != null) {
                val pct = (info.percentDone * 100).toInt().coerceIn(0, 99)
                if (info.isCompleted) {
                    val url = getDownloadUrl(torrentId, info.files)
                    if (url != null) { emit(StreamResolution.Ready(url)); return@flow }
                }
                emit(StreamResolution.Caching(pct))
            }
        }
        emit(StreamResolution.Failed("Timed out preparing movie"))
    }

    fun resolveEpisode(imdbId: String, season: Int, episode: Int): Flow<StreamResolution> = flow {
        emit(StreamResolution.Searching)

        val streams = try {
            torrentio.getEpisodeStream(TORBOX_CONFIG, imdbId, season, episode).streams
        } catch (e: Exception) {
            emit(StreamResolution.Failed("Failed to search: ${e.message}")); return@flow
        }

        if (streams.isEmpty()) { emit(StreamResolution.Failed("No streams found")); return@flow }

        val ranked = rankStreams(streams)
        val hashes = ranked.mapNotNull { parseInfoHash(it) }.distinct()
        val cachedMap = try { torBox.checkCached(hashes) } catch (e: Exception) { emptyMap() }

        val bestStream = ranked.firstOrNull { s ->
            parseInfoHash(s)?.let { cachedMap[it] == true } == true
        } ?: ranked.first()

        val bestHash = parseInfoHash(bestStream)
        val isCached = bestHash?.let { cachedMap[it] == true } == true

        emit(StreamResolution.Queuing)
        val result = try { torBox.addMagnet(bestStream.url) } catch (e: Exception) {
            TorBoxAddResult(success = false, error = e.message)
        }
        if (!result.success || result.torrentId == null) {
            emit(StreamResolution.Failed(result.message ?: "Failed to queue episode")); return@flow
        }
        val torrentId = result.torrentId!!

        if (isCached) {
            emit(StreamResolution.Caching(100))
            val info = try { torBox.getTorrentInfo(torrentId) } catch (e: Exception) { null }
            if (info != null) {
                val url = getDownloadUrl(torrentId, info.files)
                if (url != null) { emit(StreamResolution.Ready(url)); return@flow }
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
                    if (url != null) { emit(StreamResolution.Ready(url)); return@flow }
                }
                emit(StreamResolution.Caching(pct))
            }
        }
        emit(StreamResolution.Failed("Timed out preparing episode"))
    }
}
