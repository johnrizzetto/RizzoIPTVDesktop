package com.rizzoplayer.iptv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rizzoplayer.iptv.data.model.WatchHistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.watchHistoryStore: DataStore<Preferences> by preferencesDataStore("watch_history")

class WatchHistoryStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val KEY = stringPreferencesKey("history_items")

    companion object {
        const val MAX_ITEMS = 100 // Scale up since we track individual series
    }

    val history: Flow<Map<String, WatchHistoryItem>> = context.watchHistoryStore.data.map { prefs ->
        parseItems(prefs[KEY])
    }

    val historyList: Flow<List<WatchHistoryItem>> = history.map { map ->
        map.values.sortedByDescending { it.watchedAt }
    }

    suspend fun saveMovie(movie: WatchHistoryItem.Movie) {
        context.watchHistoryStore.edit { prefs ->
            val current = parseItems(prefs[KEY]).toMutableMap()
            current[movie.id] = movie
            prefs[KEY] = json.encodeToString(trimMap(current))
        }
    }

    suspend fun saveSeries(series: WatchHistoryItem.Series) {
        context.watchHistoryStore.edit { prefs ->
            val current = parseItems(prefs[KEY]).toMutableMap()
            // If the series exists, only update if the new watch time is newer, 
            // OR if it's a progress update for the same episode
            val existing = current[series.id] as? WatchHistoryItem.Series
            
            val updatedSeries = if (existing != null) {
                // If we are updating an older episode while a newer one is tracked, we might not want to overwrite.
                // For simplicity, we just overwrite the series progress with the latest action.
                series
            } else {
                series
            }
            
            current[series.id] = updatedSeries
            prefs[KEY] = json.encodeToString(trimMap(current))
        }
    }

    suspend fun updateMovieProgress(movieId: String, watchedMs: Long, durationMs: Long) {
        context.watchHistoryStore.edit { prefs ->
            val current = parseItems(prefs[KEY]).toMutableMap()
            val movie = current[movieId] as? WatchHistoryItem.Movie ?: return@edit
            val isCompleted = durationMs > 0 && watchedMs >= durationMs * 0.9
            current[movieId] = movie.copy(
                watchedMs = watchedMs,
                durationMs = durationMs,
                isCompleted = isCompleted,
                watchedAt = System.currentTimeMillis()
            )
            prefs[KEY] = json.encodeToString(trimMap(current))
        }
    }

    suspend fun updateSeriesProgress(seriesId: String, seasonNumber: Int, episodeNumber: Int, watchedMs: Long, durationMs: Long) {
        context.watchHistoryStore.edit { prefs ->
            val current = parseItems(prefs[KEY]).toMutableMap()
            val series = current[seriesId] as? WatchHistoryItem.Series ?: return@edit
            
            // Only update if it's the currently tracked episode, OR if it's a newer episode
            val isSameEpisode = series.seasonNumber == seasonNumber && series.episodeNumber == episodeNumber
            val isNewerEpisode = seasonNumber > series.seasonNumber || (seasonNumber == series.seasonNumber && episodeNumber > series.episodeNumber)
            
            if (isSameEpisode || isNewerEpisode) {
                val isEpisodeCompleted = durationMs > 0 && watchedMs >= durationMs * 0.9
                current[seriesId] = series.copy(
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    episodeWatchedMs = watchedMs,
                    episodeDurationMs = durationMs,
                    isEpisodeCompleted = isEpisodeCompleted,
                    watchedAt = System.currentTimeMillis()
                )
                prefs[KEY] = json.encodeToString(trimMap(current))
            }
        }
    }

    suspend fun clear() = context.watchHistoryStore.edit { it.clear() }

    private fun trimMap(map: MutableMap<String, WatchHistoryItem>): Map<String, WatchHistoryItem> {
        if (map.size <= MAX_ITEMS) return map
        // Keep the most recently watched MAX_ITEMS
        val sorted = map.values.sortedByDescending { it.watchedAt }.take(MAX_ITEMS)
        return sorted.associateBy { it.id }
    }

    private fun parseItems(data: String?): Map<String, WatchHistoryItem> {
        if (data.isNullOrBlank()) return emptyMap()
        return try {
            json.decodeFromString<Map<String, WatchHistoryItem>>(data)
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
