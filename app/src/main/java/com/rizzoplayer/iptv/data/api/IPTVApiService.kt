package com.rizzoplayer.iptv.data.api

import android.content.Context
import android.util.Base64
import com.rizzoplayer.iptv.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Request

class IPTVApiService(context: Context) {

    private val client = NetworkClient.iptx(context)

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { it.body?.string() ?: "" }
    }

    private fun apiUrl(
        baseUrl: String, username: String, password: String,
        action: String, extra: String = ""
    ) = "$baseUrl/player_api.php?username=$username&password=$password&action=$action$extra"

    suspend fun getLiveCategories(baseUrl: String, u: String, p: String): List<Category> =
        fetchList(apiUrl(baseUrl, u, p, "get_live_categories"))

    suspend fun getVodCategories(baseUrl: String, u: String, p: String): List<Category> =
        fetchList(apiUrl(baseUrl, u, p, "get_vod_categories"))

    suspend fun getSeriesCategories(baseUrl: String, u: String, p: String): List<Category> =
        fetchList(apiUrl(baseUrl, u, p, "get_series_categories"))

    suspend fun getLiveStreams(baseUrl: String, u: String, p: String, catId: String): List<LiveStream> =
        fetchList(apiUrl(baseUrl, u, p, "get_live_streams", "&category_id=$catId"))

    suspend fun getVodStreams(baseUrl: String, u: String, p: String, catId: String): List<VodStream> =
        fetchList(apiUrl(baseUrl, u, p, "get_vod_streams", "&category_id=$catId"))

    suspend fun getSeries(baseUrl: String, u: String, p: String, catId: String): List<Series> =
        fetchList(apiUrl(baseUrl, u, p, "get_series", "&category_id=$catId"))

    suspend fun getSeriesInfo(baseUrl: String, u: String, p: String, seriesId: Int): SeriesInfo? =
        fetchObject(apiUrl(baseUrl, u, p, "get_series_info", "&series_id=$seriesId"))

    suspend fun getVodInfo(baseUrl: String, u: String, p: String, vodId: Int): VodInfo? =
        fetchObject(apiUrl(baseUrl, u, p, "get_vod_info", "&vod_id=$vodId"))

    suspend fun getShortEpg(baseUrl: String, u: String, p: String, streamId: Int): EpgResponse? =
        fetchObject(apiUrl(baseUrl, u, p, "get_short_epg", "&stream_id=$streamId&limit=2"))

    suspend fun testConnection(baseUrl: String, u: String, p: String): Boolean {
        return try {
            val text = get(apiUrl(baseUrl, u, p, "get_live_categories"))
            text.trimStart().startsWith("[") || text.trimStart().startsWith("{")
        } catch (e: Exception) {
            false
        }
    }

    fun decodeEpgTitle(title: String): String = try {
        String(Base64.decode(title, Base64.DEFAULT), Charsets.UTF_8)
    } catch (e: Exception) {
        title
    }

    private suspend inline fun <reified T> fetchList(url: String): List<T> {
        val text = get(url)
        return try {
            json.decodeFromString(text)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend inline fun <reified T> fetchObject(url: String): T? {
        val text = get(url)
        return try {
            json.decodeFromString<T>(text)
        } catch (e: Exception) {
            null
        }
    }
}