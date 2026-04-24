package com.rizzoplayer.iptv.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from TorBox Search API (search-api.torbox.app)
 * Used to search for cached torrents by IMDB/TMDB ID without hitting blocked indexer domains.
 */
@Serializable
data class TorBoxSearchResponse(
    val success: Boolean = true,
    val data: TorBoxSearchData? = null
)

@Serializable
data class TorBoxSearchData(
    val torrents: List<TorBoxSearchTorrent> = emptyList()
)

@Serializable
data class TorBoxSearchTorrent(
    val title: String = "",
    val size: Long = 0L,
    val seeders: Int = 0,
    val leechers: Int = 0,
    val hash: String? = null,
    @SerialName("torrent_link")
    val torrentLink: String? = null,
    val indexer: String? = null,
    val id: Long = 0L,
    @SerialName("download_volume")
    val downloadVolume: Long? = null,
    @SerialName("free")
    val free: Boolean? = null,
    @SerialName("vip")
    val vip: Boolean? = null,
    val category: String? = null,
    val imdb: String? = null,
    @SerialName("tmdb_id")
    val tmdbId: String? = null,
    val tvdb: String? = null,
    @SerialName("file_id")
    val fileId: Int? = null,
    val files: List<TorBoxSearchFile>? = null
)

@Serializable
data class TorBoxSearchFile(
    val id: Int,
    val name: String,
    val size: Long
)

/**
 * Unified torrent model used in stream ranking.
 * Produced by merging TorBox Search results with Torrentio results.
 */
data class UnifiedTorrent(
    val title: String,
    val url: String,           // magnet URL
    val hash: String?,        // info hash
    val size: Long,
    val seeders: Int,
    val quality: Int,          // 2160, 1080, 720, etc.
    val indexer: String?,
    val source: TorrentSource
)

enum class TorrentSource {
    TORBOX_SEARCH,   // from search-api.torbox.app (direct API, no scrapers)
    TORRENTIO        // from torrentio.strem.fun (web scraper, may be blocked)
}
