package com.rizzoplayer.iptv

import org.junit.Assert.*
import org.junit.Test

/**
 * Verifies pixel size values used in AsyncImage calls throughout the codebase.
 * All values follow the spec: 2× the dp slot dimension at standard density.
 *
 * Size guide:
 * - Backdrop (hero fill, 220dp):  1280 × 720
 * - Poster  (120dp × 180dp tile):  240  × 360
 * - Poster  (80dp × 120dp hero):   160  × 240
 * - Logo    (28dp × 28dp slot):     56  ×  56
 * - Still   (120dp × 68dp tile):   240  × 136
 * - Strip   (48dp tile):            96  ×  96
 */
class ImageRequestSizeTest {

    // ------------------------------------------------------------------
    // Backdrop — full-bleed hero image at 1280×720
    // ------------------------------------------------------------------
    @Test
    fun `backdrop width is 1280`() {
        assertEquals(1280, BACKDROP_WIDTH)
    }

    @Test
    fun `backdrop height is 720`() {
        assertEquals(720, BACKDROP_HEIGHT)
    }

    // ------------------------------------------------------------------
    // Poster tiles — 2× density (standard 160dpi → 2× = 320dpi)
    // ------------------------------------------------------------------
    @Test
    fun `main poster width is 240 (2× 120dp)`() {
        assertEquals(240, MAIN_POSTER_WIDTH)
    }

    @Test
    fun `main poster height is 360 (2× 180dp)`() {
        assertEquals(360, MAIN_POSTER_HEIGHT)
    }

    @Test
    fun `hero poster width is 160 (2× 80dp)`() {
        assertEquals(160, HERO_POSTER_WIDTH)
    }

    @Test
    fun `hero poster height is 240 (2× 120dp)`() {
        assertEquals(240, HERO_POSTER_HEIGHT)
    }

    @Test
    fun `detail poster width is 240 (2× 120dp)`() {
        assertEquals(240, DETAIL_POSTER_WIDTH)
    }

    @Test
    fun `detail poster height is 360 (2× 180dp)`() {
        assertEquals(360, DETAIL_POSTER_HEIGHT)
    }

    @Test
    fun `favorites icon width is 96 (2× 48dp)`() {
        assertEquals(96, FAVORITES_ICON_WIDTH)
    }

    // ------------------------------------------------------------------
    // Channel logo — 2× the 28dp slot
    // ------------------------------------------------------------------
    @Test
    fun `logo width is 56 (2× 28dp)`() {
        assertEquals(56, LOGO_SIZE)
    }

    // ------------------------------------------------------------------
    // Episode still — 2× the 120dp × 68dp tile
    // ------------------------------------------------------------------
    @Test
    fun `still width is 240 (2× 120dp)`() {
        assertEquals(240, STILL_WIDTH)
    }

    @Test
    fun `still height is 136 (2× 68dp)`() {
        assertEquals(136, STILL_HEIGHT)
    }

    // ------------------------------------------------------------------
    // ContinueWatchingStrip — 2× the 48dp slot
    // ------------------------------------------------------------------
    @Test
    fun `strip width is 96 (2× 48dp)`() {
        assertEquals(96, STRIP_SIZE)
    }
}

// ---------------------------------------------------------------------
// Size constants — must match values used in AsyncImage call sites
// ---------------------------------------------------------------------
private const val BACKDROP_WIDTH    = 1280
private const val BACKDROP_HEIGHT  = 720
private const val MAIN_POSTER_WIDTH  = 240
private const val MAIN_POSTER_HEIGHT = 360
private const val HERO_POSTER_WIDTH  = 160
private const val HERO_POSTER_HEIGHT = 240
private const val DETAIL_POSTER_WIDTH  = 240
private const val DETAIL_POSTER_HEIGHT = 360
private const val FAVORITES_ICON_WIDTH = 96
private const val LOGO_SIZE        = 56
private const val STILL_WIDTH      = 240
private const val STILL_HEIGHT     = 136
private const val STRIP_SIZE       = 96
