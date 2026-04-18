package com.rizzoplayer.iptv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rizzoplayer.iptv.data.model.Favorite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.favDataStore: DataStore<Preferences> by preferencesDataStore("favorites")

class FavoritesStore(private val context: Context) {

    private val gson = Gson()
    private val KEY = stringPreferencesKey("favorites_json")

    /** Order-insensitive — use for containsKey/membership checks only. */
    val favorites: Flow<Map<String, Favorite>> = context.favDataStore.data.map { prefs ->
        parseFavorites(prefs[KEY])
    }

    /**
     * Order-sensitive list for UI display.
     * Emits a new list whenever [move] changes item order,
     * solving the Map.equals() order-insensitivity bug.
     */
    val favoritesList: Flow<List<Favorite>> = favorites.map { it.values.toList() }

    suspend fun add(favorite: Favorite) {
        context.favDataStore.edit { prefs ->
            val current = parseFavorites(prefs[KEY]).toMutableMap()
            current[favorite.id] = favorite
            prefs[KEY] = gson.toJson(current)
        }
    }

    suspend fun remove(id: String) {
        context.favDataStore.edit { prefs ->
            val current = parseFavorites(prefs[KEY]).toMutableMap()
            current.remove(id)
            prefs[KEY] = gson.toJson(current)
        }
    }

    /** Move a favorite up (direction=-1) or down (direction=1) in the list. */
    suspend fun move(id: String, direction: Int) {
        context.favDataStore.edit { prefs ->
            val current = parseFavorites(prefs[KEY]).entries.toMutableList()
            val idx = current.indexOfFirst { it.key == id }
            if (idx < 0) return@edit
            val newIdx = (idx + direction).coerceIn(0, current.size - 1)
            if (newIdx == idx) return@edit
            val item = current.removeAt(idx)
            current.add(newIdx, item)
            val reordered = LinkedHashMap<String, Favorite>()
            current.forEach { reordered[it.key] = it.value }
            prefs[KEY] = gson.toJson(reordered)
        }
    }

    private fun parseFavorites(json: String?): Map<String, Favorite> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, Favorite>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
