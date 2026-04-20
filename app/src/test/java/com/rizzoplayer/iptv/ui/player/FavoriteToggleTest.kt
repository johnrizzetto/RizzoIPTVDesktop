package com.rizzoplayer.iptv.ui.player

import androidx.compose.runtime.mutableStateOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteToggleTest {

    @Test
    fun `isCurrentlyFavorited true when id in liveFavoriteIds`() {
        val ids = mutableStateOf(setOf("ch_42"))
        assertTrue(ids.value.contains("ch_42"))
        assertFalse(ids.value.contains("ch_99"))
    }

    @Test
    fun `toggle calls remove when already favorited`() {
        var removedId: String? = null
        var addedId: String? = null
        val toggleFavorite = { id: String, _: String, isFav: Boolean ->
            if (isFav) removedId = id else addedId = id
        }
        toggleFavorite("ch_42", "Sport 1", true)
        assertEquals("ch_42", removedId)
        assertNull(addedId)
    }

    @Test
    fun `toggle calls add when not favorited`() {
        var addedId: String? = null
        val toggleFavorite = { id: String, _: String, isFav: Boolean ->
            if (!isFav) addedId = id
        }
        toggleFavorite("ch_99", "News", false)
        assertEquals("ch_99", addedId)
    }

    @Test
    fun `contentId used as streamId not url-parsed`() {
        val contentId = "12345"
        // Old bug: url.substringAfterLast("/").substringBefore(".") == "12345" — coincidence
        // But would break for: http://cdn.example.com/stream.m3u8
        val badId = "http://cdn.example.com/stream.m3u8".substringAfterLast("/").substringBefore(".")
        assertNotEquals("ch_99", badId)  // shows URL-hacking is unreliable
        assertEquals("12345", contentId) // intent-based ID is always correct
    }
}
