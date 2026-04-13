package com.rizzoplayer.iptv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rizzoplayer.iptv.data.model.Credentials
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.credDataStore: DataStore<Preferences> by preferencesDataStore("credentials")

class CredentialsStore(private val context: Context) {

    companion object {
        private val KEY_URL      = stringPreferencesKey("url")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_PASSWORD = stringPreferencesKey("password")
    }

    val credentials: Flow<Credentials?> = context.credDataStore.data.map { prefs ->
        val url      = prefs[KEY_URL]      ?: return@map null
        val username = prefs[KEY_USERNAME] ?: return@map null
        val password = prefs[KEY_PASSWORD] ?: return@map null
        if (url.isBlank() || username.isBlank()) null
        else Credentials(url, username, password)
    }

    suspend fun save(credentials: Credentials) {
        context.credDataStore.edit { prefs ->
            prefs[KEY_URL]      = credentials.url
            prefs[KEY_USERNAME] = credentials.username
            prefs[KEY_PASSWORD] = credentials.password
        }
    }

    suspend fun clear() {
        context.credDataStore.edit { it.clear() }
    }
}
