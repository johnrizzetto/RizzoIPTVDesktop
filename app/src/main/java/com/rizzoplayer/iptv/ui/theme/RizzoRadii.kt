package com.rizzoplayer.iptv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * RizzoIPTV corner radii — consistent rounded corners across all components.
 */
object RizzoRadii {
    /** 4.dp — small chips, badges */
    val Xs = 4.dp

    /** 8.dp — buttons, cards, text fields */
    val Sm = 8.dp

    /** 12.dp — large cards, posters */
    val Md = 12.dp

    /** 16.dp — modals, bottom sheets */
    val Lg = 16.dp

    /** 24.dp — large panels */
    val Xl = 24.dp

    /** Fully round — icon buttons, FABs */
    val Full = 999.dp

    /** Convenient RoundedCornerShape accessors */
    val SmShape = RoundedCornerShape(Sm)
    val MdShape = RoundedCornerShape(Md)
    val LgShape = RoundedCornerShape(Lg)
}
