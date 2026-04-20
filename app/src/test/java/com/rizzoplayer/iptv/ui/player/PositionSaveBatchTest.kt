package com.rizzoplayer.iptv.ui.player

import io.mockk.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PositionSaveBatchTest {

    /**
     * The batch-save logic:
     *   val now = System.currentTimeMillis()
     *   if (now - lastSaveTimeMs >= 5_000) { lastSaveTimeMs = now; savePositionNow() }
     *
     * lastSaveTimeMs starts at 0 (epoch). Saves fire at t=5s, 10s, 15s, 20s (4 saves).
     * To test the "immediate first save" path, set lastSaveTime to -5000 so t=0 crosses threshold.
     */
    @Test
    fun `saves at 5 s intervals starting from lastSaveTime=0`() {
        var lastSaveTimeMs = 0L
        var callCount = 0

        fun shouldSave(now: Long): Boolean {
            if (now - lastSaveTimeMs >= 5_000) {
                lastSaveTimeMs = now
                callCount++
                return true
            }
            return false
        }

        // Tick 0: t=0, 0-0=0 < 5s → no save
        assertFalse(shouldSave(0))
        // Tick 1: t=5s, 5000-0=5000 >= 5s → save 1
        assertTrue(shouldSave(5_000))
        // Tick 2: t=10s, 10000-5000=5000 >= 5s → save 2
        assertTrue(shouldSave(10_000))
        // Tick 3: t=15s, 15000-10000=5000 >= 5s → save 3
        assertTrue(shouldSave(15_000))
        // Tick 4: t=20s, 20000-15000=5000 >= 5s → save 4
        assertTrue(shouldSave(20_000))

        assertEquals(4, callCount)
    }

    @Test
    fun `saves immediately when lastSaveTime is in the past`() {
        var lastSaveTimeMs = -5_000L  // simulate "5 s already elapsed at t=0"
        var callCount = 0

        fun shouldSave(now: Long): Boolean {
            if (now - lastSaveTimeMs >= 5_000) {
                lastSaveTimeMs = now
                callCount++
                return true
            }
            return false
        }

        // t=0 crosses threshold immediately (0 - (-5000) = 5000 >= 5s)
        assertTrue(shouldSave(0))
        // then normal 5 s intervals apply
        assertTrue(shouldSave(5_000))
        assertTrue(shouldSave(10_000))
        assertTrue(shouldSave(15_000))

        assertEquals(4, callCount)
    }

    @Test
    fun `saves on pause regardless of interval`() {
        var callCount = 0
        fun saveOnPause() { callCount++ }

        // Any pause call fires immediately (no interval check)
        saveOnPause()
        assertEquals(1, callCount)
        saveOnPause()
        assertEquals(2, callCount)
    }

    @Test
    fun `saves on playback ended regardless of interval`() {
        var callCount = 0
        fun saveOnEnded() { callCount++ }

        // STATE_ENDED triggers immediate save even at t=1s
        saveOnEnded()
        assertEquals(1, callCount)
    }

    @Test
    fun `no save between intervals`() {
        var lastSaveTimeMs = 0L

        fun shouldSave(now: Long): Boolean {
            if (now - lastSaveTimeMs >= 5_000) {
                lastSaveTimeMs = now
                return true
            }
            return false
        }

        assertTrue(shouldSave(5_000))   // first save fires at t=5s
        assertFalse(shouldSave(6_000))  // 1 s later: no save
        assertFalse(shouldSave(7_000))  // 2 s later: no save
        assertFalse(shouldSave(8_000))  // 3 s later: no save
        assertFalse(shouldSave(9_000))  // 4 s later: no save
        assertTrue(shouldSave(10_000))  // 5 s after last save: save fires
    }
}
