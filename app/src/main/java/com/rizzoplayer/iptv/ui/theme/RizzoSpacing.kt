package com.rizzoplayer.iptv.ui.theme

import androidx.compose.ui.unit.dp

/**
 * RizzoIPTV spacing constants — TV-remote-optimised grid rhythm.
 *
 * All spacing values are multiples of 4.dp for visual coherence.
 * Overscan-safe screen margins are handled at the screen level (not here).
 */
object RizzoSpacing {
    /** 4.dp — tight internal padding, icon gaps */
    val Xs = 4.dp

    /** 8.dp — default internal padding, small gaps */
    val Sm = 8.dp

    /** 12.dp — card internal padding */
    val Md = 12.dp

    /** 16.dp — standard gap between elements */
    val Lg = 16.dp

    /** 24.dp — section spacing, row gaps */
    val Xl = 24.dp

    /** 32.dp — large section gaps */
    val Xxl = 32.dp

    /** 48.dp — hero area padding */
    val Hero = 48.dp
}
