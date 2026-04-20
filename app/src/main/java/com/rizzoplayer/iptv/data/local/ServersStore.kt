package com.rizzoplayer.iptv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rizzoplayer.iptv.data.model.ServerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.serversDataStore: DataStore<Preferences> by preferencesDataStore("servers")

class ServersStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val KEY = stringPreferencesKey("servers_json")

    val servers: Flow<List<ServerConfig>> = context.serversDataStore.data.map { prefs ->
        parse(prefs[KEY])
    }

    suspend fun addOrUpdate(server: ServerConfig) {
        context.serversDataStore.edit { prefs ->
            val current = parse(prefs[KEY]).toMutableList()
            current.removeAll { it.url == server.url && it.username == server.username }
            current.add(0, server)
            prefs[KEY] = json.encodeToString(current)
        }
    }

    suspend fun remove(id: String) {
        context.serversDataStore.edit { prefs ->
            val current = parse(prefs[KEY]).toMutableList()
            current.removeAll { it.id == id }
            prefs[KEY] = json.encodeToString(current)
        }
    }

    private fun parse(data: String?): List<ServerConfig> {
        if (data.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<ServerConfig>>(data)
        } catch (e: Exception) { emptyList() }
    }
}