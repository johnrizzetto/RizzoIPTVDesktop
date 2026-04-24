package com.rizzoplayer.iptv.data.api

import android.content.Context
import android.util.Log
import com.rizzoplayer.iptv.BuildConfig
import com.rizzoplayer.iptv.data.model.TorBoxSearchResponse
import com.rizzoplayer.iptv.data.model.TorBoxSearchTorrent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Request

/**
 * TorBox Search API client.
 * Searches TorBox's indexed/cached torrent database by IMDB or TMDB ID.
 * This API NEVER touches public tracker domains — it's served from TorBox infrastructure,
 * so it's not blocked by ISP-level tracker blocking.
 *
 * API Base: https://search-api.torbox.app
 * Docs: https://docs.torbox.app/torbox/api/torbox-search-api
 */
class TorBoxSearchService(context: Context) {

    private val client = NetworkClient.base(context)
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val baseUrl = "https://search-api.torbox.app"
    private val apiToken = BuildConfig.TORBOX_API_KEY

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiToken")
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string() ?: throw Exception("Empty response body")
            if (!resp.isSuccessful) {
                Log.e("TorBoxSearch", "GET $url failed ${resp.code}: $body")
            }
            body
        }
    }

    /**
     * Search for movie torrents by IMDB ID.
     * @param imdbId IMDB ID (e.g., "tt1234567")
     * @param checkCache if true, only return cached results (faster, more reliable)
     * @param checkOwned if true, only return results owned/cached by this account
     */
    suspend fun searchMovieTorrents(
        imdbId: String,
        checkCache: Boolean = true,
        checkOwned: Boolean = true
    ): List<TorBoxSearchTorrent> = searchTorrents("imdb_id", imdbId, checkCache, checkOwned)

    /**
     * Search for TV episode torrents by IMDB ID + season + episode.
     */
    suspend fun searchEpisodeTorrents(
        imdbId: String,
        season: Int,
        episode: Int,
        checkCache: Boolean = true,
        checkOwned: Boolean = true
    ): List<TorBoxSearchTorrent> = searchTorrents(
        idType = "imdb_id",
        id = imdbId,
        checkCache = checkCache,
        checkOwned = checkOwned,
        season = season.toString(),
        episode = episode.toString()
    )

    /**
     * Search by TMDB ID (more reliable than IMDB for some content).
     */
    suspend fun searchMovieTorrentsByTmdb(
        tmdbId: String,
        checkCache: Boolean = true,
        checkOwned: Boolean = true
    ): List<TorBoxSearchTorrent> = searchTorrents("themoviedb_id", tmdbId, checkCache, checkOwned)

    private suspend fun searchTorrents(
        idType: String,
        id: String,
        checkCache: Boolean = true,
        checkOwned: Boolean = true,
        season: String? = null,
        episode: String? = null
    ): List<TorBoxSearchTorrent> = withContext(Dispatchers.IO) {
        val params = mutableListOf<String>()
        if (checkCache) params.add("check_cache=true")
        if (checkOwned) params.add("check_owned=true")
        if (season != null) params.add("season=$season")
        if (episode != null) params.add("episode=$episode")
        // metadata=false: skip extra metadata fetching for speed
        params.add("metadata=false")

        val query = params.joinToString("&")
        val url = "$baseUrl/torrents/$idType:$id?$query"
        Log.d("TorBoxSearch", "Searching: $url")

        try {
            val body = get(url)
            val resp = json.decodeFromString<TorBoxSearchResponse>(body)
            if (resp.success && resp.data != null) {
                val torrents = resp.data.torrents
                Log.d("TorBoxSearch", "Found ${torrents.size} torrents for $idType:$id")
                torrents
            } else {
                Log.w("TorBoxSearch", "Search failed or empty: $body")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("TorBoxSearch", "Search error for $idType:$id: ${e.message}")
            emptyList()
        }
    }
}
