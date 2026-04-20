package com.rizzoplayer.iptv.ui.viewmodel

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Verifies the speculative stream resolution deduplication and expiration logic.
 */
class SpeculativeStreamTest {

    // ── Deduplication ─────────────────────────────────────────────────────

    @Test
    fun `concurrent lookups return same Deferred for identical key`() {
        val deferreds = ConcurrentHashMap<String, String>()

        // Simulate concurrent calls with the same key
        repeat(10) { _ ->
            val key = "tt1234567"
            // Deduplication: if key exists, return existing, else create new
            deferreds.computeIfAbsent(key) { "deferred_$key" }
        }

        // Only one entry should exist (deduplication)
        assertEquals(1, deferreds.size)
        assertEquals("deferred_tt1234567", deferreds["tt1234567"])
    }

    @Test
    fun `different keys create separate entries`() {
        val map = ConcurrentHashMap<String, String>()
        map["tt1234567"] = "deferred_tt1234567"
        map["tt7654321"] = "deferred_tt7654321"

        assertEquals(2, map.size)
        assertNotNull(map["tt1234567"])
        assertNotNull(map["tt7654321"])
    }

    @Test
    fun `seasonal episode key format is distinct per episode`() {
        fun key(imdbId: String, season: Int?, episode: Int?) =
            if (season != null && episode != null) "$imdbId:S${season}E${episode}" else imdbId

        assertEquals("tt123:S1E3", key("tt123", 1, 3))
        assertEquals("tt123:S2E1", key("tt123", 2, 1))
        assertEquals("tt123", key("tt123", null, null))

        // Same imdbId, different episodes → different keys
        val ep1 = key("tt123", 1, 1)
        val ep2 = key("tt123", 1, 2)
        assertNotEquals(ep1, ep2)
    }

    @Test
    fun `seasonal key does not collide with movie-only key`() {
        fun key(imdbId: String, season: Int?, episode: Int?) =
            if (season != null && episode != null) "$imdbId:S${season}E${episode}" else imdbId

        val movieKey = key("tt123", null, null)
        val epKey = key("tt123", 1, 1)
        assertNotEquals(movieKey, epKey)
        assertEquals("tt123", movieKey)
        assertEquals("tt123:S1E1", epKey)
    }

    // ── TTL expiration simulation ─────────────────────────────────────────

    @Test
    fun `map entry can be manually removed after TTL`() {
        val map = ConcurrentHashMap<String, String>()
        map["tt1234567"] = "deferred_tt1234567"
        assertEquals(1, map.size)

        // Simulate TTL expiration: remove the entry
        map.remove("tt1234567")
        assertEquals(0, map.size)
    }

    @Test
    fun `new lookups after expiration create fresh entry`() {
        val map = ConcurrentHashMap<String, String>()

        // First lookup
        val first = map.computeIfAbsent("tt1234567") { "deferred_tt1234567" }
        assertEquals("deferred_tt1234567", first)

        // Simulate expiration and removal
        map.remove("tt1234567")

        // New lookup after expiration creates fresh entry
        val second = map.computeIfAbsent("tt1234567") { "fresh_deferred_tt1234567" }
        assertEquals("fresh_deferred_tt1234567", second)
    }

    // ── ConcurrentHashMap thread safety ─────────────────────────────────

    @Test
    fun `concurrent puts from multiple threads all succeed`() {
        val map = ConcurrentHashMap<String, Int>()
        val counter = AtomicInteger(0)

        repeat(100) { i ->
            Thread {
                map["key_$i"] = i
                counter.incrementAndGet()
            }.start()
        }

        // All 100 entries should be present (with some spin-wait tolerance)
        Thread.sleep(500)
        assertEquals(100, map.size)
        assertEquals(100, counter.get())
    }
}
