package com.rizzoplayer.iptv.data.api

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.rizzoplayer.iptv.BuildConfig
import com.rizzoplayer.iptv.data.model.TorBoxAddResult
import com.rizzoplayer.iptv.data.model.TorBoxTorrent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.Request
import okhttp3.Response

class TorBoxApiService(context: Context) {

    private val client = NetworkClient.base(context)

    private val gson = Gson()
    private val baseUrl = "https://api.torbox.app/v1"
    private val apiToken = BuildConfig.TORBOX_API_KEY

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiToken")
            .build()
        client.newCall(request).execute().use { it.body?.string() ?: "" }
    }

    private suspend fun post(url: String, body: RequestBody): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiToken")
            .post(body)
            .build()
        client.newCall(request).execute().use { resp ->
            val bodyStr = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                Log.e("TorBoxApi", "POST $url failed ${resp.code}: $bodyStr")
            }
            bodyStr
        }
    }

    private suspend fun getRaw(url: String): Response = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiToken")
            .build()
        client.newCall(request).execute()
    }

    // POST /v1/api/torrents/createtorrent — multipart form
    suspend fun addMagnet(magnet: String): TorBoxAddResult = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/torrents/createtorrent"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("magnet", magnet)
            .addFormDataPart("seed", "1")
            .addFormDataPart("allow_zip", "false")
            .build()
        val json = post(url, body)
        Log.d("TorBoxApi", "addMagnet response: $json")
        try { gson.fromJson(json, TorBoxAddResult::class.java) } catch (e: Exception) {
            Log.e("TorBoxApi", "addMagnet parse error: $e, raw: $json")
            TorBoxAddResult(success = false, error = "Parse error: ${e.message} — raw: ${json.take(200)}")
        }
    }

    // GET /v1/api/torrents/mylist?bypass_cache=true — returns data as array
    suspend fun getTorrentInfo(torrentId: Int): TorBoxTorrent? = withContext(Dispatchers.IO) {
        val json = get("$baseUrl/api/torrents/mylist?bypass_cache=true")
        Log.d("TorBoxApi", "mylist response: $json")
        try {
            val root = gson.fromJson(json, Map::class.java) as? Map<String, Any>
                ?: return@withContext null
            val data = root["data"] as? List<*>
                ?: return@withContext null
            val torrentMap = data.filterIsInstance<Map<String, Any>>()
                .find { (it["id"] as? Number)?.toInt() == torrentId }
                ?: return@withContext null
            gson.fromJson(gson.toJson(torrentMap), TorBoxTorrent::class.java)
        } catch (e: Exception) {
            Log.e("TorBoxApi", "getTorrentInfo parse error: $e, raw: $json")
            null
        }
    }

    // GET /v1/api/torrents/requestdl?torrent_id=<id>&file_id=<id>
    suspend fun requestDownloadLink(torrentId: Int, fileId: Int): String? = withContext(Dispatchers.IO) {
        val json = get("$baseUrl/api/torrents/requestdl?token=$apiToken&torrent_id=$torrentId&file_id=$fileId")
        try {
            val resp = gson.fromJson(json, Map::class.java) as? Map<String, Any>
                ?: return@withContext null
            (resp["data"] as? String)?.takeIf { it.startsWith("http") }
        } catch (e: Exception) {
            Log.e("TorBoxApi", "requestDownloadLink parse error: $e, raw: $json")
            null
        }
    }

    // GET /v1/api/torrents/checkcached?hash=<hash1>,<hash2>&format=object
    // Returns {data: {<hash>: {name, size, hash}}}
    suspend fun checkCached(hashes: List<String>): Map<String, Boolean> = withContext(Dispatchers.IO) {
        if (hashes.isEmpty()) return@withContext emptyMap()
        val param = hashes.joinToString(",")
        val json = get("$baseUrl/api/torrents/checkcached?hash=$param&format=object")
        Log.d("TorBoxApi", "checkCached response: $json")
        try {
            val root = gson.fromJson(json, Map::class.java) as? Map<String, Any>
                ?: return@withContext emptyMap()
            val data = root["data"] as? Map<String, Any>
                ?: return@withContext emptyMap()
            hashes.associateWith { h -> data.containsKey(h.lowercase()) || data.containsKey(h) }
        } catch (e: Exception) {
            Log.e("TorBoxApi", "checkCached parse error: $e, raw: $json")
            emptyMap()
        }
    }
}
