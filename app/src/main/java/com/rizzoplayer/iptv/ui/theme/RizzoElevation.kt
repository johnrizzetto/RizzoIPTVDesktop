package com.rizzoplayer.iptv.ui.theme

import androidx.compose.ui.unit.dp

/**
 * RizzoIPTV elevation tokens — used for shadow/depth on cards, modals, sheets.
 */
object RizzoElevation {
    /** 0.dp — flat surfaces */
    val None = 0.dp

    /** 2.dp — card resting state */
    val Card = 2.dp

    /** 4.dp — card focused, nav items */
    val Focused = 8.dp

    /** 8.dp — dialogs, bottom sheets */
    val Modal = 8.dp

    /** 16.dp — toasts, tooltips */
    val Toast = 16.dp
}
