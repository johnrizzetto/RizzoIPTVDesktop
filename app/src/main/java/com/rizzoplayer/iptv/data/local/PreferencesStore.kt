package com.rizzoplayer.iptv.data.local

import android.content.Context

/**
 * Lightweight app preferences using SharedPreferences.
 * For simple key-value settings that need synchronous reads.
 */
class PreferencesStore(context: Context) {

    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    fun getLastSection(): String = prefs.getString("last_section", "LIVE") ?: "LIVE"

    fun saveLastSection(section: String) {
        prefs.edit().putString("last_section", section).apply()
    }
}
