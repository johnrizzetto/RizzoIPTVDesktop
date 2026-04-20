package com.rizzoplayer.iptv.data.api

import android.content.Context
import com.google.gson.Gson
import com.rizzoplayer.iptv.data.model.TorrentioResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

class TorrentioService(context: Context) {

    private val client = NetworkClient.base(context)

    private val gson = Gson()
    private val baseUrl = "https://torrentio.strem.fun"

    private suspend fun get(endpoint: String): TorrentioResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl$endpoint")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw Exception("Empty body")
            gson.fromJson(body, TorrentioResponse::class.java)
        }
    }

    suspend fun getMovieStream(config: String, imdbId: String): TorrentioResponse =
        get("/$config/stream/movie/$imdbId.json")

    suspend fun getEpisodeStream(config: String, imdbId: String, season: Int, episode: Int): TorrentioResponse =
        get("/$config/stream/series/$imdbId:$season:$episode.json")
}