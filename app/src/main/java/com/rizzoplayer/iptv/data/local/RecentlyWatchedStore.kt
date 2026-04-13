package com.rizzoplayer.iptv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rizzoplayer.iptv.data.model.RecentItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.recentDataStore: DataStore<Preferences> by preferencesDataStore("recently_watched")

class RecentlyWatchedStore(private val context: Context) {

    private val gson = Gson()
    private val KEY = stringPreferencesKey("items")

    companion object {
        const val MAX_ITEMS = 20
    }

    val items: Flow<List<RecentItem>> = context.recentDataStore.data.map { prefs ->
        parseItems(prefs[KEY])
    }

    /** Adds [item] to the top of the list, deduplicating by id+type, trimming to [MAX_ITEMS]. */
    suspend fun add(item: RecentItem) {
        context.recentDataStore.edit { prefs ->
            val current = parseItems(prefs[KEY]).toMutableList()
            current.removeAll { it.id == item.id && it.type == item.type }
            current.add(0, item.copy(watchedAt = System.currentTimeMillis()))
            if (current.size > MAX_ITEMS) current.subList(MAX_ITEMS, current.size).clear()
            prefs[KEY] = gson.toJson(current)
        }
    }

    /** Updates watchedMs and durationMs for an existing item (matched by id+type). */
    suspend fun updatePosition(id: String, type: String, positionMs: Long, durationMs: Long) {
        context.recentDataStore.edit { prefs ->
            val current = parseItems(prefs[KEY]).toMutableList()
            val idx = current.indexOfFirst { it.id == id && it.type == type }
            if (idx >= 0) {
                current[idx] = current[idx].copy(watchedMs = positionMs, durationMs = durationMs)
                prefs[KEY] = gson.toJson(current)
            }
        }
    }

    suspend fun clear() = context.recentDataStore.edit { it.clear() }

    private fun parseItems(json: String?): List<RecentItem> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<RecentItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
