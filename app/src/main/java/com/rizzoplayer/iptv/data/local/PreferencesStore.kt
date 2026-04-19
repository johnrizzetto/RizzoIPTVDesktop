package com.rizzoplayer.iptv.data.local

import android.content.Context
import java.security.MessageDigest

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

    fun isParentalLockEnabled(): Boolean = prefs.getBoolean("parental_lock_enabled", false)

    fun setParentalLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("parental_lock_enabled", enabled).apply()
    }

    fun getParentalLockPin(): String? = prefs.getString("parental_lock_pin", null)

    fun setParentalLockPin(pin: String) {
        // SHA-256 hash the PIN before storing
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(pin.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        prefs.edit().putString("parental_lock_pin", hash).apply()
    }

    fun verifyParentalLockPin(pin: String): Boolean {
        val stored = getParentalLockPin() ?: return false
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(pin.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return stored == hash
    }
}
