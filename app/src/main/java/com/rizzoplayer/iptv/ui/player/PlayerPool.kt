package com.rizzoplayer.iptv.ui.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Warm pool of pre-built ExoPlayer instances for Live streams.
 *
 * Rationale: Media3 ExoPlayer requires load control at Builder construction
 * time, so pool only holds Live players. VOD builds fresh per session
 * (one-shot playback, build cost is negligible compared to network wait).
 *
 * Holds up to 2 Live instances. Idle instances are released after 5 min
 * via a Handler. Live players switching channels re-use pooled instances
 * without the ~300ms cold build penalty.
 */
object PlayerPool {

    private const val MAX_POOL_SIZE   = 2
    private const val IDLE_TIMEOUT_MS = 5 * 60 * 1_000L // 5 minutes

    private val pool = ConcurrentLinkedQueue<PooledPlayer>()
    private var cleanupHandler: Handler? = null

    // ──── Public API ───────────────────────────────────────────────────────

    /**
     * Acquire a Live player from the pool.
     *
     * For VOD playback, use [buildVodPlayer] directly — VOD has different
     * buffer requirements and only one active stream at a time.
     */
    fun acquireLive(context: Context): ExoPlayer {
        val pooled = pool.poll()
        if (pooled != null) {
            pooled.lastUsed = System.currentTimeMillis()
            return pooled.player
        }
        // Pool empty — build fresh (class loading is the main cost saved)
        return buildLivePlayer(context)
    }

    /**
     * Return a player to the pool. Stops + clears it and schedules idle timeout.
     * If the pool is already at capacity the player is fully released.
     */
    fun release(player: ExoPlayer) {
        player.stop()
        player.clearMediaItems()

        if (pool.size >= MAX_POOL_SIZE) {
            player.release()
            return
        }

        pool.offer(PooledPlayer(player, System.currentTimeMillis()))
        scheduleCleanup()
    }

    // ──── Warm-up (called from RizzoApp.onCreate, off-main thread) ─────────

    /**
     * Pre-warm one Live player in the pool before first user action.
     */
    fun warmUp(context: Context) {
        try {
            val player = buildLivePlayer(context)
            pool.offer(PooledPlayer(player, System.currentTimeMillis()))
        } catch (_: Exception) {
            // Silently skip warm-up failures (memory pressure, etc.)
        }
    }

    // ──── VOD player (always fresh — one per viewing session) ───────────────

    /**
     * Build a VOD player with large-buffer load control.
     * Always fresh — VOD playback is a one-shot and build cost is negligible
     * vs network latency.
     */
    fun buildVodPlayer(context: Context): ExoPlayer =
        ExoPlayer.Builder(context)
            .setLoadControl(VOD_LOAD_CONTROL)
            .build()

    // ──── Internal helpers ─────────────────────────────────────────────────

    private fun buildLivePlayer(context: Context): ExoPlayer =
        ExoPlayer.Builder(context)
            .setLoadControl(LIVE_LOAD_CONTROL)
            .build()

    private fun scheduleCleanup() {
        cleanupHandler?.removeMessages(MSG_CLEANUP)
        cleanupHandler?.sendEmptyMessageDelayed(MSG_CLEANUP, IDLE_TIMEOUT_MS)
    }

    private fun evictStale() {
        val cutoff = System.currentTimeMillis() - IDLE_TIMEOUT_MS
        while (pool.size > 1) {
            val pooled = pool.poll() ?: break
            if (pooled.lastUsed < cutoff) {
                pooled.player.release()
            } else {
                pool.offer(pooled)
                break
            }
        }
        if (pool.isNotEmpty()) scheduleCleanup()
    }

    // ──── Load control presets ─────────────────────────────────────────────

    private val VOD_LOAD_CONTROL = DefaultLoadControl.Builder()
        .setBufferDurationsMs(15_000, 90_000, 5_000, 10_000)
        .build()

    /**
     * Live load control tuned for low latency with light jitter protection.
     *
     * minBufferMs (3 s): slightly more than stock (2.5 s) to absorb brief
     *   network hiccups without triggering a stall.
     *
     * maxBufferMs (8 s): keeps the viewer close to the live edge (~3–5 s behind
     *   broadcast) while leaving a little headroom for the player to fill during
     *   good network windows.
     *
     * bufferForPlaybackMs (1 s): start quickly on channel switch.
     *
     * bufferForPlaybackAfterRebufferMs (2 s): resume promptly after a genuine stall.
     */
    private val LIVE_LOAD_CONTROL = DefaultLoadControl.Builder()
        .setBufferDurationsMs(3_000, 8_000, 1_000, 2_000)
        .build()

    // ──── Internal types ────────────────────────────────────────────────────

    private data class PooledPlayer(
        val player: ExoPlayer,
        var lastUsed: Long
    )

    private const val MSG_CLEANUP = 1

    init {
        cleanupHandler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                if (msg.what == MSG_CLEANUP) evictStale()
            }
        }
    }
}
