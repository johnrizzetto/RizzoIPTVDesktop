package com.rizzoplayer.iptv.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class PlaybackPositionStore(context: Context) {

    @Serializable
    data class Progress(
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        val isWatched: Boolean = false
    )

    private val file = File(context.filesDir, "playback_positions.json")
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val positions = HashMap<String, Progress>()

    init {
        if (file.exists()) {
            try {
                runBlocking(Dispatchers.IO) {
                    val loaded: Map<String, Progress>? = json.decodeFromString(file.readText())
                    if (loaded != null) synchronized(positions) { positions.putAll(loaded) }
                }
            } catch (_: Exception) {}
        }
    }

    @Synchronized
    fun getPosition(key: String): Long = positions[key]?.positionMs ?: 0L

    @Synchronized
    fun getProgress(key: String): Progress? = positions[key]

    fun save(key: String, positionMs: Long, durationMs: Long = 0) {
        synchronized(positions) {
            val old = positions[key]
            val watched = old?.isWatched ?: (durationMs > 0 && positionMs >= durationMs * 0.9)
            positions[key] = Progress(positionMs, durationMs, watched)
        }
        writeToDisk()
    }

    suspend fun saveAsync(key: String, positionMs: Long, durationMs: Long = 0) {
        synchronized(positions) {
            val old = positions[key]
            val watched = old?.isWatched ?: (durationMs > 0 && positionMs >= durationMs * 0.9)
            positions[key] = Progress(positionMs, durationMs, watched)
        }
        withContext(Dispatchers.IO) { writeToDisk() }
    }

    suspend fun toggleWatched(key: String) {
        synchronized(positions) {
            val old = positions[key] ?: Progress()
            positions[key] = old.copy(isWatched = !old.isWatched)
        }
        withContext(Dispatchers.IO) { writeToDisk() }
    }

    private fun writeToDisk() {
        try {
            synchronized(positions) {
                file.writeText(json.encodeToString(positions))
            }
        } catch (_: Exception) {}
    }
}