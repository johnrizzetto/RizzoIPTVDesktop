package com.rizzoplayer.iptv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary            = AccentBlue,
    onPrimary          = TextPrimary,
    primaryContainer   = NavActive,
    onPrimaryContainer = TextPrimary,
    background         = MainBg,
    onBackground       = TextPrimary,
    surface            = SidebarBg,
    onSurface          = TextPrimary,
    secondary          = AccentBlueDark,
    onSecondary        = TextPrimary,
    error              = RedColor,
    onError            = TextPrimary,
)

@Composable
fun RizzoIPTVTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
