package com.rizzoplayer.iptv.data.api

import android.content.Context
import com.google.gson.Gson
import com.rizzoplayer.iptv.data.model.TorrentioResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class TorrentioService(context: Context? = null) {

    private val client = OkHttpClient.Builder().apply {
        if (context != null) {
            cache(Cache(File(context.cacheDir, "okhttp_torrentio_cache"), 5L * 1024 * 1024))
        }
        connectTimeout(10, TimeUnit.SECONDS)
        readTimeout(15, TimeUnit.SECONDS)
        protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        connectionPool(ConnectionPool(10, 2, TimeUnit.MINUTES))
    }.build()

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