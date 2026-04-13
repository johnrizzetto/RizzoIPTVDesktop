package com.rizzoplayer.iptv.data.local

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Two-level API cache: memory (instant) + disk (survives app restarts).
 *
 * Each entry is stored as a JSON file named by the hash of its key.
 * TTL is enforced via file last-modified timestamp.
 */
class DiskCache(context: Context) {

    private val dir = File(context.cacheDir, "iptv_api").also { it.mkdirs() }

    // In-memory layer on top — avoids disk reads on hot paths
    private val mem = HashMap<String, Pair<String, Long>>() // key → (json, storedAt)

    companion object {
        val TTL_CATEGORIES = 24 * 60 * 60 * 1000L   // 24 h — categories rarely change
        val TTL_STREAMS    =      60 * 60 * 1000L   //  1 h — stream lists
        val TTL_SERIES_INFO = 6  * 60 * 60 * 1000L  //  6 h — episode lists
    }

    /**
     * Returns cached JSON if the entry exists and is within [ttlMs], otherwise null.
     */
    @Synchronized
    fun get(key: String, ttlMs: Long): String? {
        val now = System.currentTimeMillis()

        // 1. Memory hit
        mem[key]?.let { (json, storedAt) ->
            if (now - storedAt <= ttlMs) return json
            mem.remove(key)
        }

        // 2. Disk hit
        val file = file(key)
        if (!file.exists()) return null
        if (now - file.lastModified() > ttlMs) {
            file.delete()
            return null
        }
        return runCatching { file.readText().also { mem[key] = it to file.lastModified() } }
            .getOrNull()
    }

    /** Stores [json] to memory immediately and writes to disk on IO dispatcher. */
    suspend fun put(key: String, json: String) {
        val now = System.currentTimeMillis()
        synchronized(this) { mem[key] = json to now }
        withContext(Dispatchers.IO) {
            runCatching { file(key).writeText(json) }
        }
    }

    /** Wipes all cached entries. */
    @Synchronized
    fun clear() {
        mem.clear()
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun file(key: String): File {
        val name = key.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(dir, "$name.json")
    }
}
