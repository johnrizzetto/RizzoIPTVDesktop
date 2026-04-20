package com.rizzoplayer.iptv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rizzoplayer.iptv.data.model.Favorite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.favDataStore: DataStore<Preferences> by preferencesDataStore("favorites")

class FavoritesStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val KEY = stringPreferencesKey("favorites_json")

    val favorites: Flow<Map<String, Favorite>> = context.favDataStore.data.map { prefs ->
        parseFavorites(prefs[KEY])
    }

    val favoritesList: Flow<List<Favorite>> = favorites.map { it.values.toList() }

    suspend fun add(favorite: Favorite) {
        context.favDataStore.edit { prefs ->
            val current = parseFavorites(prefs[KEY]).toMutableMap()
            current[favorite.id] = favorite
            prefs[KEY] = json.encodeToString(current)
        }
    }

    suspend fun remove(id: String) {
        context.favDataStore.edit { prefs ->
            val current = parseFavorites(prefs[KEY]).toMutableMap()
            current.remove(id)
            prefs[KEY] = json.encodeToString(current)
        }
    }

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
            prefs[KEY] = json.encodeToString(reordered)
        }
    }

    private fun parseFavorites(data: String?): Map<String, Favorite> {
        if (data.isNullOrBlank()) return emptyMap()
        return try {
            json.decodeFromString<Map<String, Favorite>>(data)
        } catch (e: Exception) {
            emptyMap()
        }
    }
}