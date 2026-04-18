package com.rizzoplayer.iptv.data.repository

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
    private val TORBOX_CONFIG = "torbox"

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

        val cachedMap = try { torBox.checkCached(hashes).filterValues { it } } catch (e: Exception) { emptyMap() }

        val bestStream = ranked.firstOrNull { s ->
            parseInfoHash(s)?.let { cachedMap[it] == true } == true
        } ?: ranked.first()

        val isCached = parseInfoHash(bestStream)?.let { cachedMap[it] == true } == true

        var torrentId: Int? = null

        if (isCached) {
            emit(StreamResolution.Caching(100))
        } else {
            emit(StreamResolution.Queuing)
            val result = try { torBox.addMagnet(bestStream.url) } catch (e: Exception) {
                TorBoxAddResult(success = false, message = e.message)
            }
            if (!result.success || result.torrentId == null) {
                emit(StreamResolution.Failed(result.message ?: "Failed to queue torrent")); return@flow
            }
            torrentId = result.torrentId
        }

        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 90_000L) {
            delay(2_000)
            if (torrentId != null) {
                val info = try { torBox.getTorrentInfo(torrentId) } catch (e: Exception) { null }
                if (info != null) {
                    val pct = (info.percentDone * 100).toInt().coerceIn(0, 99)
                    if (info.isCompleted) {
                        val video = largestVideoFile(info.files)
                        if (video != null) {
                            val url = try { torBox.requestDownloadLink(torrentId, video.id) } catch (e: Exception) { null }
                            if (url != null) { emit(StreamResolution.Ready(url)); return@flow }
                        }
                    }
                    emit(StreamResolution.Caching(pct))
                }
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
        val cachedMap = try { torBox.checkCached(hashes).filterValues { it } } catch (e: Exception) { emptyMap() }

        val bestStream = ranked.firstOrNull { s ->
            parseInfoHash(s)?.let { cachedMap[it] == true } == true
        } ?: ranked.first()

        val isCached = parseInfoHash(bestStream)?.let { cachedMap[it] == true } == true

        var torrentId: Int? = null

        if (!isCached) {
            emit(StreamResolution.Queuing)
            val result = try { torBox.addMagnet(bestStream.url) } catch (e: Exception) {
                TorBoxAddResult(success = false, message = e.message)
            }
            if (!result.success || result.torrentId == null) {
                emit(StreamResolution.Failed(result.message ?: "Failed to queue episode")); return@flow
            }
            torrentId = result.torrentId
        } else {
            emit(StreamResolution.Caching(100))
        }

        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 90_000L) {
            delay(2_000)
            if (torrentId != null) {
                val info = try { torBox.getTorrentInfo(torrentId) } catch (e: Exception) { null }
                if (info != null) {
                    val pct = (info.percentDone * 100).toInt().coerceIn(0, 99)
                    if (info.isCompleted) {
                        val video = largestVideoFile(info.files)
                        if (video != null) {
                            val url = try { torBox.requestDownloadLink(torrentId, video.id) } catch (e: Exception) { null }
                            if (url != null) { emit(StreamResolution.Ready(url)); return@flow }
                        }
                    }
                    emit(StreamResolution.Caching(pct))
                }
            }
        }
        emit(StreamResolution.Failed("Timed out preparing episode"))
    }
}