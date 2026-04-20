package com.rizzoplayer.iptv.data.api

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rizzoplayer.iptv.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

class IPTVApiService(context: Context) {

    private val client = NetworkClient.iptx(context)

    private val gson = Gson()

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
            val json = get(apiUrl(baseUrl, u, p, "get_live_categories"))
            json.trimStart().startsWith("[") || json.trimStart().startsWith("{")
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
        val json = get(url)
        return try {
            val type = TypeToken.getParameterized(List::class.java, T::class.java).type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend inline fun <reified T> fetchObject(url: String): T? {
        val json = get(url)
        return try {
            gson.fromJson(json, T::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
