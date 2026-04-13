package com.rizzoplayer.iptv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rizzoplayer.iptv.data.model.ServerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.serversDataStore: DataStore<Preferences> by preferencesDataStore("servers")

class ServersStore(private val context: Context) {

    private val gson = Gson()
    private val KEY = stringPreferencesKey("servers_json")

    val servers: Flow<List<ServerConfig>> = context.serversDataStore.data.map { prefs ->
        parse(prefs[KEY])
    }

    suspend fun addOrUpdate(server: ServerConfig) {
        context.serversDataStore.edit { prefs ->
            val current = parse(prefs[KEY]).toMutableList()
            current.removeAll { it.url == server.url && it.username == server.username }
            current.add(0, server)
            prefs[KEY] = gson.toJson(current)
        }
    }

    suspend fun remove(id: String) {
        context.serversDataStore.edit { prefs ->
            val current = parse(prefs[KEY]).toMutableList()
            current.removeAll { it.id == id }
            prefs[KEY] = gson.toJson(current)
        }
    }

    private fun parse(json: String?): List<ServerConfig> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<ServerConfig>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }
}
