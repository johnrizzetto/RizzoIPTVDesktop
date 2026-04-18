package com.rizzoplayer.iptv.data.local

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import android.util.LruCache

/**
 * Two-level API cache: memory (instant) + disk (survives app restarts).
 *
 * Each entry is stored as a JSON file named by the hash of its key.
 * TTL is enforced via file last-modified timestamp.
 */
class DiskCache(context: Context, dirName: String = "iptv_api") {

    private val dir = File(context.cacheDir, dirName).also { it.mkdirs() }

    // In-memory layer on top — bounded to 50 items to avoid OOM
    private val mem = LruCache<String, Pair<String, Long>>(50) // key → (json, storedAt)

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
        mem.get(key)?.let { (json, storedAt) ->
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
        return runCatching { file.readText().also { mem.put(key, it to file.lastModified()) } }
            .getOrNull()
    }

    /** Stores [json] to memory immediately and writes to disk on IO dispatcher. */
    suspend fun put(key: String, json: String) {
        val now = System.currentTimeMillis()
        synchronized(this) { mem.put(key, json to now) }
        withContext(Dispatchers.IO) {
            runCatching { file(key).writeText(json) }
        }
    }

    /** Wipes all cached entries. */
    @Synchronized
    fun clear() {
        mem.evictAll()
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun file(key: String): File {
        val name = key.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(dir, "$name.json")
    }
}
