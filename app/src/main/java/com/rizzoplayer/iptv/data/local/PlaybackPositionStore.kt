package com.rizzoplayer.iptv.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Persistent playback position + duration store.
 * Keys are "type:id" (e.g. "vod:123", "episode:456").
 * In-memory HashMap for instant reads; JSON file for persistence across restarts.
 */
class PlaybackPositionStore(context: Context) {

    data class Progress(val positionMs: Long = 0, val durationMs: Long = 0)

    private val file = File(context.filesDir, "playback_positions.json")
    private val gson = Gson()
    private val positions = HashMap<String, Progress>()

    init {
        if (file.exists()) {
            try {
                val type = object : TypeToken<HashMap<String, Progress>>() {}.type
                val loaded: HashMap<String, Progress>? = gson.fromJson(file.readText(), type)
                if (loaded != null) positions.putAll(loaded)
            } catch (_: Exception) {}
        }
    }

    /** Get saved position in ms. Returns 0 if none saved. */
    @Synchronized
    fun getPosition(key: String): Long = positions[key]?.positionMs ?: 0L

    /** Get full progress (position + duration). Returns null if never saved. */
    @Synchronized
    fun getProgress(key: String): Progress? = positions[key]

    /** Save position + duration. Writes to memory instantly, disk async. */
    fun save(key: String, positionMs: Long, durationMs: Long = 0) {
        synchronized(positions) {
            positions[key] = Progress(positionMs, durationMs)
        }
        // Synchronous disk write — file is small (<50KB), takes <1ms
        writeToDisk()
    }

    /** Suspend-friendly save for use from coroutines. */
    suspend fun saveAsync(key: String, positionMs: Long, durationMs: Long = 0) {
        synchronized(positions) {
            positions[key] = Progress(positionMs, durationMs)
        }
        withContext(Dispatchers.IO) { writeToDisk() }
    }

    private fun writeToDisk() {
        try {
            synchronized(positions) {
                file.writeText(gson.toJson(positions))
            }
        } catch (_: Exception) {}
    }
}
