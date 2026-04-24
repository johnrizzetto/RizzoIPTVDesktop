package com.rizzoplayer.iptv.ui.screens.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.rizzoplayer.iptv.R
import com.rizzoplayer.iptv.ui.navigation.FocusManager
import com.rizzoplayer.iptv.ui.navigation.Screen
import com.rizzoplayer.iptv.ui.theme.*

private data class NavEntry(val icon: String, val label: String, val route: String)
private val NAV_ENTRIES = listOf(
    NavEntry("▶", "Live",      Screen.Live.route),
    NavEntry("▣", "Movies",    Screen.Movies.route),
    NavEntry("≡", "Shows",     Screen.Shows.route),
    NavEntry("♥", "Favorites", Screen.Favorites.route),
    NavEntry("⚙", "Settings",  Screen.Settings.route)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Sidebar(
    widthDp: Dp,
    expanded: Boolean,
    currentRoute: String,
    canGoBack: Boolean,
    onFocusEnter: () -> Unit,
    onFocusExit: () -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onSelectSection: (String) -> Unit,
    initialSidebarFocus: FocusRequester,
) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Sign Out?") },
            text = { Text("You will need to re-enter your server credentials.") },
            confirmButton = { TextButton(onClick = { showLogoutDialog = false; onLogout() }) { Text("Sign Out", color = RedColor) } },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") } },
        )
    }

    Column(
        modifier = Modifier
            .width(widthDp)
            .fillMaxHeight()
            .background(SidebarBg)
            .onFocusChanged { if (it.hasFocus) onFocusEnter() else onFocusExit() }
            .padding(vertical = 16.dp, horizontal = 4.dp),
        horizontalAlignment = if (expanded) Alignment.Start else Alignment.CenterHorizontally
    ) {
        // Phase 2: No manual upTarget/downTarget chain needed.
        // Compose focus system traverses sibling focusable elements naturally.
        // FocusManager tracks lastFocusedIndex for return navigation instead.

        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = null,
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .then(if (!expanded) Modifier else Modifier.padding(start = 6.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(Modifier.height(16.dp))

        NAV_ENTRIES.forEachIndexed { idx, entry ->
            SidebarNavItem(
                icon = entry.icon,
                label = entry.label,
                active = currentRoute == entry.route,
                expanded = expanded,
                // First nav item gets the initialSidebarFocus requester for first-launch
                fr = if (idx == 0) initialSidebarFocus else remember { FocusRequester() },
                onClick = {
                    // Track last focused index for return navigation
                    FocusManager.setSidebarFocusedIndex(idx)
                    onSelectSection(entry.route)
                    // Signal content to restore focus (Phase 1 FocusManager pattern)
                    FocusManager.requestContentFocus()
                },
            )
            Spacer(Modifier.height(2.dp))
        }

        if (canGoBack) {
            Spacer(Modifier.height(6.dp))
            SidebarNavItem(
                icon = "←",
                label = "Back",
                active = false,
                expanded = expanded,
                onClick = {
                    onBack()
                    FocusManager.requestContentFocus()
                },
            )
        }

        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(1.dp).background(BorderColor.copy(alpha = 0.4f)))
        Spacer(Modifier.height(8.dp))
        SidebarNavItem(
            icon = "⇤",
            label = "Sign Out",
            active = false,
            expanded = expanded,
            danger = true,
            onClick = { showLogoutDialog = true },
        )
    }
}

@Composable
private fun SidebarNavItem(
    icon: String,
    label: String,
    active: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    fr: FocusRequester = remember { FocusRequester() },
    danger: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy,
        ),
        label = "navScale",
    )
    val bg = when {
        danger && isFocused -> Color(0xFF2A0A0A)
        isFocused -> NavFocusBg
        active -> NavActive
        else -> Color.Transparent
    }
    val iconColor = when {
        danger && isFocused -> RedColor
        danger -> TextMuted.copy(alpha = 0.5f)
        active || isFocused -> AccentBlue
        else -> TextMuted
    }
    val textColor = when {
        danger && isFocused -> RedColor
        active || isFocused -> TextPrimary
        else -> TextMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .then(
                if (isFocused) Modifier.border(
                    2.dp,
                    if (danger) RedColor.copy(alpha = 0.45f)
                    else AccentBlue,
                    RoundedCornerShape(6.dp)
                )
                else Modifier
            )
            .onFocusChanged {
                // Track sidebar index for return navigation (Phase 2 FocusManager pattern)
                if (it.isFocused) {
                    FocusManager.setSidebarFocusedIndex(
                        NAV_ENTRIES.indexOfFirst { e -> e.label == label }.takeIf { i -> i >= 0 } ?: 0
                    )
                }
            }
            .focusRequester(fr)
            .focusable(interactionSource = interactionSource)
            .clickable(onClick = onClick)
            .padding(horizontal = if (expanded) 10.dp else 0.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 16.sp, color = iconColor)
        }
        if (expanded) {
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 13.sp, color = textColor, maxLines = 1)
        }
    }
}
