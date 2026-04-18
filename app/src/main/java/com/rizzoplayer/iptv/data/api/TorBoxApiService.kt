package com.rizzoplayer.iptv.data.api

import android.content.Context
import com.google.gson.Gson
import com.rizzoplayer.iptv.BuildConfig
import com.rizzoplayer.iptv.data.model.TorBoxAddResult
import com.rizzoplayer.iptv.data.model.TorBoxTorrent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class TorBoxApiService(context: Context? = null) {

    private val client = OkHttpClient.Builder().apply {
        if (context != null) {
            cache(Cache(File(context.cacheDir, "okhttp_torbox_cache"), 5L * 1024 * 1024))
        }
        connectTimeout(15, TimeUnit.SECONDS)
        readTimeout(20, TimeUnit.SECONDS)
        protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        connectionPool(ConnectionPool(10, 2, TimeUnit.MINUTES))
    }.build()

    private val gson = Gson()
    private val baseUrl = "https://api.torbox.app/v1"
    private val authHeader = "Bearer ${BuildConfig.TORBOX_API_KEY}"

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader)
            .build()
        client.newCall(request).execute().use { it.body?.string() ?: "" }
    }

    private suspend fun post(url: String, body: FormBody): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader)
            .post(body)
            .build()
        client.newCall(request).execute().use { it.body?.string() ?: "" }
    }

    suspend fun addMagnet(magnet: String): TorBoxAddResult = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().add("magnet", magnet).build()
        val json = post("$baseUrl/api/torrents/createtorrent", body)
        try { gson.fromJson(json, TorBoxAddResult::class.java) } catch (e: Exception) {
            TorBoxAddResult(success = false, message = json)
        }
    }

    suspend fun getTorrentInfo(torrentId: Int): TorBoxTorrent? = withContext(Dispatchers.IO) {
        val json = get("$baseUrl/api/torrents/mylist?id=$torrentId")
        try { gson.fromJson(json, TorBoxTorrent::class.java) } catch (e: Exception) { null }
    }

    suspend fun requestDownloadLink(torrentId: Int, fileId: Int): String? = withContext(Dispatchers.IO) {
        val json = get("$baseUrl/api/torrents/requestdl?token=&torrent_id=$torrentId&file_id=$fileId")
        try {
            val resp = gson.fromJson(json, Map::class.java)
            (resp["url"] as? String) ?: (resp["download_url"] as? String)
        } catch (e: Exception) { null }
    }

    suspend fun checkCached(hashes: List<String>): Map<String, Boolean> = withContext(Dispatchers.IO) {
        if (hashes.isEmpty()) return@withContext emptyMap()
        val hashesParam = hashes.joinToString(",")
        val json = get("$baseUrl/api/torrents/checkcached?hash=$hashesParam")
        try {
            val resp = gson.fromJson(json, Map::class.java) as? Map<String, Any>
                ?: return@withContext emptyMap()
            resp.mapValues { (_, v) -> v == true }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}