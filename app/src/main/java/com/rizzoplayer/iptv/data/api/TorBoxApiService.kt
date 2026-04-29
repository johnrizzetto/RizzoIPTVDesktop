package com.rizzoplayer.iptv.data.api

import android.content.Context
import android.util.Log
import com.rizzoplayer.iptv.BuildConfig
import com.rizzoplayer.iptv.data.model.TorBoxAddResult
import com.rizzoplayer.iptv.data.model.TorBoxTorrent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.Request

class TorBoxApiService(context: Context) {

    private val client = NetworkClient.base(context)

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
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

    private suspend fun getRaw(url: String) = withContext(Dispatchers.IO) {
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
        val text = post(url, body)
        Log.d("TorBoxApi", "addMagnet response: $text")
        try { json.decodeFromString<TorBoxAddResult>(text) } catch (e: Exception) {
            Log.e("TorBoxApi", "addMagnet parse error: $e, raw: $text")
            TorBoxAddResult(success = false, error = "Parse error: ${e.message} — raw: ${text.take(200)}")
        }
    }

    // GET /v1/api/torrents/mylist?bypass_cache=true
    suspend fun getTorrentInfo(torrentId: Int): TorBoxTorrent? = withContext(Dispatchers.IO) {
        val text = get("$baseUrl/api/torrents/mylist?bypass_cache=true")
        Log.d("TorBoxApi", "mylist response: $text")
        try {
            val root = json.decodeFromString<JsonObject>(text)
            val data = root["data"] as? JsonArray ?: return@withContext null
            val torrentObj = data.filterIsInstance<JsonObject>()
                .find { (it["id"] as? JsonPrimitive)?.intOrNull == torrentId }
                ?: return@withContext null
            
            json.decodeFromJsonElement<TorBoxTorrent>(torrentObj)
        } catch (e: Exception) {
            Log.e("TorBoxApi", "getTorrentInfo parse error: $e, raw: $text")
            null
        }
    }

    // GET /v1/api/torrents/requestdl?torrent_id=<id>&file_id=<id>
    suspend fun requestDownloadLink(torrentId: Int, fileId: Int): String? = withContext(Dispatchers.IO) {
        val text = get("$baseUrl/api/torrents/requestdl?token=$apiToken&torrent_id=$torrentId&file_id=$fileId")
        try {
            val resp = json.decodeFromString<JsonObject>(text)
            (resp["data"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.startsWith("http") }
        } catch (e: Exception) {
            Log.e("TorBoxApi", "requestDownloadLink parse error: $e, raw: $text")
            null
        }
    }

    // GET /v1/api/torrents/checkcached?hash=<hash1>,<hash2>&format=object
    suspend fun checkCached(hashes: List<String>): Map<String, Boolean> = withContext(Dispatchers.IO) {
        if (hashes.isEmpty()) return@withContext emptyMap()
        val param = hashes.joinToString(",")
        val text = get("$baseUrl/api/torrents/checkcached?hash=$param&format=object")
        Log.d("TorBoxApi", "checkCached response: $text")
        try {
            val root = json.decodeFromString<JsonObject>(text)
            val data = root["data"] as? JsonObject ?: return@withContext emptyMap()
            hashes.associateWith { h ->
                val entry = data[h.lowercase()] ?: data[h.uppercase()] ?: data[h]
                entry != null && entry !is JsonNull && entry.toString() != "false" && entry.toString() != "[]"
            }
        } catch (e: Exception) {
            Log.e("TorBoxApi", "checkCached parse error: $e, raw: $text")
            emptyMap()
        }
    }
}