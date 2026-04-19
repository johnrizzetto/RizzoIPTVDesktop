package com.rizzoplayer.iptv.ui.navigation

/**
 * Top-level navigation routes. Each corresponds to a NavHost destination.
 */
sealed class Screen(val route: String) {
    data object Live      : Screen("live")
    data object Movies    : Screen("movies")
    data object Shows     : Screen("shows")
    data object Favorites : Screen("favorites")
    data object Settings  : Screen("settings")
}
