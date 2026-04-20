package com.rizzoplayer.iptv.data.repository

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import com.rizzoplayer.iptv.data.api.IPTVApiService
import com.rizzoplayer.iptv.data.local.CredentialsStore
import com.rizzoplayer.iptv.data.local.DiskCache
import com.rizzoplayer.iptv.data.local.FavoritesStore
import com.rizzoplayer.iptv.data.local.RecentlyWatchedStore
import com.rizzoplayer.iptv.data.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class IPTVRepository(
    val credentialsStore: CredentialsStore,
    val favoritesStore: FavoritesStore,
    val recentlyWatchedStore: RecentlyWatchedStore,
    private val diskCache: DiskCache,
    private val api: IPTVApiService,
    private val json: Json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
) {
    private suspend inline fun <reified T> cachedList(
        key: String,
        ttlMs: Long,
        crossinline fetch: suspend () -> List<T>
    ): List<T> {
        diskCache.get(key, ttlMs)?.let { text ->
            return withContext(Dispatchers.Default) {
                try { json.decodeFromString<List<T>>(text) } catch (e: Exception) { emptyList() }
            }
        }
        val result = fetch()
        val text = withContext(Dispatchers.Default) { json.encodeToString(result) }
        diskCache.put(key, text)
        return result
    }

    private suspend inline fun <reified T> cachedObject(
        key: String,
        ttlMs: Long,
        crossinline fetch: suspend () -> T?
    ): T? {
        diskCache.get(key, ttlMs)?.let { text ->
            return withContext(Dispatchers.Default) {
                try { json.decodeFromString<T>(text) } catch (e: Exception) { null }
            }
        }
        val result = fetch() ?: return null
        val text = withContext(Dispatchers.Default) { json.encodeToString(result) }
        diskCache.put(key, text)
        return result
    }

    private val inFlightRequests = java.util.concurrent.ConcurrentHashMap<String, Deferred<Any>>()

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> coalesced(key: String, block: suspend () -> T): T {
        return (inFlightRequests.getOrPut(key) {
            coroutineScope {
                async(Dispatchers.IO) {
                    block() as Any
                }.also { it.invokeOnCompletion { inFlightRequests.remove(key) } }
            }
        } as Deferred<T>).await()
    }

    suspend fun getLiveCategories(c: Credentials): List<Category> =
        cachedList("live_cats:${c.url}", DiskCache.TTL_CATEGORIES) {
            api.getLiveCategories(c.baseUrl, c.username, c.password)
        }

    suspend fun getVodCategories(c: Credentials): List<Category> =
        cachedList("vod_cats:${c.url}", DiskCache.TTL_CATEGORIES) {
            api.getVodCategories(c.baseUrl, c.username, c.password)
        }

    suspend fun getSeriesCategories(c: Credentials): List<Category> =
        cachedList("series_cats:${c.url}", DiskCache.TTL_CATEGORIES) {
            api.getSeriesCategories(c.baseUrl, c.username, c.password)
        }

    suspend fun getLiveStreams(c: Credentials, catId: String): List<LiveStream> =
        cachedList("live:$catId:${c.url}", DiskCache.TTL_STREAMS) {
            api.getLiveStreams(c.baseUrl, c.username, c.password, catId)
        }

    suspend fun getVodStreams(c: Credentials, catId: String): List<VodStream> =
        cachedList("vod:$catId:${c.url}", DiskCache.TTL_STREAMS) {
            api.getVodStreams(c.baseUrl, c.username, c.password, catId)
        }

    suspend fun getSeries(c: Credentials, catId: String): List<Series> =
        cachedList("series:$catId:${c.url}", DiskCache.TTL_STREAMS) {
            api.getSeries(c.baseUrl, c.username, c.password, catId)
        }

    suspend fun getSeriesInfo(c: Credentials, seriesId: Int): SeriesInfo? =
        cachedObject("series_info:$seriesId:${c.url}", DiskCache.TTL_SERIES_INFO) {
            api.getSeriesInfo(c.baseUrl, c.username, c.password, seriesId)
        }

    suspend fun getVodUrl(c: Credentials, vodId: Int): String {
        val info = cachedObject<VodInfo>("vod_info:$vodId:${c.url}", DiskCache.TTL_STREAMS) {
            api.getVodInfo(c.baseUrl, c.username, c.password, vodId)
        }
        val ext = info?.movieData?.containerExtension ?: "mp4"
        return "${c.baseUrl}/movie/${c.username}/${c.password}/$vodId.$ext"
    }

    fun getLiveUrl(c: Credentials, streamId: Int) =
        "${c.baseUrl}/live/${c.username}/${c.password}/$streamId.m3u8"

    fun getEpisodeUrl(c: Credentials, episodeId: Int, ext: String) =
        "${c.baseUrl}/series/${c.username}/${c.password}/$episodeId.$ext"

    suspend fun getShortEpg(c: Credentials, streamId: Int): EpgResponse? =
        cachedObject("epg_${streamId}", 10 * 60 * 1000L) {
            api.getShortEpg(c.baseUrl, c.username, c.password, streamId)
        }

    suspend fun testConnection(c: Credentials): Boolean =
        api.testConnection(c.baseUrl, c.username, c.password)

    fun decodeEpgTitle(title: String) = api.decodeEpgTitle(title)

    fun clearCache() = diskCache.clear()
}