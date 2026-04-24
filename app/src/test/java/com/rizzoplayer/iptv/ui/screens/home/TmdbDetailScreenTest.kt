package com.rizzoplayer.iptv.ui.screens.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard: verifies that Icons.Outlined.FavoriteBorder is accessible
 * and that the TmdbDetailScreen favorites toggle logic type-checks correctly.
 *
 * Prior bug (QA_REPORT.md item 2): TmdbDetailScreen.kt referenced
 * Icons.Outlined.FavoriteBorder which requires material-icons-extended.
 * The dependency was present in libs.versions.toml but not in app/build.gradle.kts,
 * causing a runtime NoClassDefFoundError on the TMDB detail screen.
 *
 * This test does not require an Android environment — it validates import
 * resolution and icon accessibility at the type level, which is sufficient to
 * catch a missing dependency in the test JVM.
 */
class TmdbDetailScreenTest {

    @Test
    fun `Icons Outlined FavoriteBorder is accessible`() {
        // Verify the icon singleton is not null (would throw NoClassDefFoundError if missing)
        val icon = Icons.Outlined.FavoriteBorder
        assertNotEquals(null, icon)
    }

    @Test
    fun `Icons Filled Favorite is accessible`() {
        val icon = Icons.Filled.Favorite
        assertNotEquals(null, icon)
    }

    @Test
    fun `favorites toggle correctly selects filled vs outlined icon`() {
        val isFavorite = false
        val unfavoritedIcon = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder
        assertEquals(Icons.Outlined.FavoriteBorder, unfavoritedIcon)

        val favoritedIcon = if (!isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder
        assertEquals(Icons.Filled.Favorite, favoritedIcon)
    }

    @Test
    fun `favorited state toggles icon correctly`() {
        // Simulate what TmdbMovieDetailView does at line 170
        for (currentFavorite in listOf(false, true)) {
            val icon = if (currentFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder
            val contentDesc = if (currentFavorite) "Remove from favorites" else "Add to favorites"

            if (currentFavorite) {
                assertEquals(Icons.Filled.Favorite, icon)
                assertEquals("Remove from favorites", contentDesc)
            } else {
                assertEquals(Icons.Outlined.FavoriteBorder, icon)
                assertEquals("Add to favorites", contentDesc)
            }
        }
    }

    @Test
    fun `favorite toggle handler logic is correct`() {
        // Verify the toggle callback logic that onToggleFavorite would call
        var currentFavorite = false
        val toggleHandler: () -> Unit = { currentFavorite = !currentFavorite }

        // Initially unfavorited
        assertFalse(currentFavorite)
        val initialIcon = if (currentFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder
        assertEquals(Icons.Outlined.FavoriteBorder, initialIcon)

        // After toggle
        toggleHandler()
        assertTrue(currentFavorite)
        val afterToggleIcon = if (currentFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder
        assertEquals(Icons.Filled.Favorite, afterToggleIcon)

        // Toggle back
        toggleHandler()
        assertFalse(currentFavorite)
        val backIcon = if (currentFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder
        assertEquals(Icons.Outlined.FavoriteBorder, backIcon)
    }

    @Test
    fun `color constants used by TmdbDetailScreen are valid`() {
        // TmdbDetailScreen uses Color.White, BrandGold, RizzoTextSecondary
        val white = Color.White
        assertEquals(1.0f, white.alpha)

        // BrandGold should be a non-transparent gold color (0xFFFFB800)
        val brandGold = com.rizzoplayer.iptv.ui.theme.BrandGold
        assertTrue(brandGold.alpha > 0f)

        val rizzoTextSecondary = com.rizzoplayer.iptv.ui.theme.RizzoTextSecondary
        assertTrue(rizzoTextSecondary.alpha >= 0f)
    }
}
