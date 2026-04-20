package com.rizzoplayer.iptv.data.api

import android.content.Context
import com.rizzoplayer.iptv.data.model.TorrentioResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Request

class TorrentioService(context: Context) {

    private val client = NetworkClient.base(context)

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val baseUrl = "https://torrentio.strem.fun"

    private suspend fun get(endpoint: String): TorrentioResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl$endpoint")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw Exception("Empty body")
            json.decodeFromString<TorrentioResponse>(body)
        }
    }

    suspend fun getMovieStream(config: String, imdbId: String): TorrentioResponse =
        get("/$config/stream/movie/$imdbId.json")

    suspend fun getEpisodeStream(config: String, imdbId: String, season: Int, episode: Int): TorrentioResponse =
        get("/$config/stream/series/$imdbId:$season:$episode.json")
}