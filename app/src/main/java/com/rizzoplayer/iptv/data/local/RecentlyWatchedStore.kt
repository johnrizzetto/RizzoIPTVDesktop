package com.rizzoplayer.iptv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rizzoplayer.iptv.data.model.RecentItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.recentDataStore: DataStore<Preferences> by preferencesDataStore("recently_watched")

class RecentlyWatchedStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val KEY = stringPreferencesKey("items")

    companion object {
        const val MAX_ITEMS = 20
    }

    val items: Flow<List<RecentItem>> = context.recentDataStore.data.map { prefs ->
        parseItems(prefs[KEY])
    }

    suspend fun add(item: RecentItem) {
        context.recentDataStore.edit { prefs ->
            val current = parseItems(prefs[KEY]).toMutableList()
            current.removeAll { it.id == item.id && it.type == item.type }
            current.add(0, item.copy(watchedAt = System.currentTimeMillis()))
            if (current.size > MAX_ITEMS) current.subList(MAX_ITEMS, current.size).clear()
            prefs[KEY] = json.encodeToString(current)
        }
    }

    suspend fun updatePosition(id: String, type: String, positionMs: Long, durationMs: Long) {
        context.recentDataStore.edit { prefs ->
            val current = parseItems(prefs[KEY]).toMutableList()
            val idx = current.indexOfFirst { it.id == id && it.type == type }
            if (idx >= 0) {
                current[idx] = current[idx].copy(watchedMs = positionMs, durationMs = durationMs)
                prefs[KEY] = json.encodeToString(current)
            }
        }
    }

    suspend fun clear() = context.recentDataStore.edit { it.clear() }

    private fun parseItems(data: String?): List<RecentItem> {
        if (data.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<RecentItem>>(data)
        } catch (e: Exception) {
            emptyList()
        }
    }
}