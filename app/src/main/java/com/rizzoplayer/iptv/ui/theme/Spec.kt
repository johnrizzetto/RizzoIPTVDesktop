package com.rizzoplayer.iptv.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Phase 2 design spec constants.
 * These values drive the world-class home, catalog, and detail screens.
 */
object Spec {
    /** Grid columns for catalog screens */
    const val gridColumns = 5

    /** Poster card width (2:3 ratio poster) */
    val cardWidthPoster: Dp = 120.dp

    /** 16x9 card width (backdrop/landscape cards) */
    val cardWidth16x9: Dp = 200.dp

    /** Vertical spacing between rows */
    val rowSpacing: Dp = 8.dp

    /** Horizontal padding for section headers */
    val sectionPadding: Dp = 14.dp

    /** Focus scale multiplier when a card gains focus */
    const val focusScale = 1.06f

    /** Border width on focused cards */
    val focusBorder: Dp = 4.dp

    /** Elevation on focused cards */
    val focusElevation: Dp = 8.dp

    /** Standard transition duration in ms */
    const val transitionMs = 200

    /** Grace period (ms) before skeleton shimmer appears */
    const val skeletonGraceMs = 200L

    /** Auto-rotation interval (ms) for the hero showcase */
    const val heroRotateMs = 8000L
}