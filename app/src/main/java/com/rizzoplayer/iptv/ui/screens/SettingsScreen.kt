package com.rizzoplayer.iptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rizzoplayer.iptv.data.local.PreferencesStore
import com.rizzoplayer.iptv.ui.theme.*

@Composable
fun SettingsScreen(
    preferencesStore: PreferencesStore,
    onClearCache: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
) {
    var cacheSizeMb by remember { mutableStateOf<Double?>(null) }
    var parentalLockEnabled by remember { mutableStateOf(preferencesStore.isParentalLockEnabled()) }
    var pinSetUp by remember { mutableStateOf(preferencesStore.getParentalLockPin() != null) }

    LaunchedEffect(Unit) {
        try {
            val cacheDir = java.io.File("/data/data/com.rizzoplayer.iptv/cache")
            val size = cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
            cacheSizeMb = size / (1024.0 * 1024.0)
        } catch (_: Exception) {}
    }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        try { firstFocus.requestFocus() } catch (_: Exception) {}
    }

    var focusedItem by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MainBg)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            "Settings",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
        Spacer(Modifier.height(24.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SettingsSectionHeader("Playback") }
            item { SettingsToggleRow("Autoplay next episode", true, {}) }
            item { SettingsToggleRow("Auto-skip intro", false, {}) }

            item { Spacer(Modifier.height(16.dp)); SettingsSectionHeader("Safety") }
            item {
                SettingsToggleRow(
                    label = "Parental lock",
                    checked = parentalLockEnabled,
                    onToggle = { enabled ->
                        parentalLockEnabled = enabled
                        preferencesStore.setParentalLockEnabled(enabled)
                    }
                )
            }

            item { Spacer(Modifier.height(16.dp)); SettingsSectionHeader("Display") }
            item { SettingsToggleRow("Show EPG in sidebar", true, {}) }
            item { SettingsToggleRow("Auto-hide controls", true, {}) }

            item { Spacer(Modifier.height(16.dp)); SettingsSectionHeader("Data") }
            item {
                SettingsInfoRow(
                    label = "Cache size",
                    value = cacheSizeMb?.let { "%.1f MB".format(it) } ?: "Calculating…",
                    isFocused = focusedItem == "cache",
                    onClick = {
                        onClearCache()
                        cacheSizeMb = 0.0
                    },
                    onFocus = { focusedItem = "cache" }
                )
            }

            item { Spacer(Modifier.height(16.dp)); SettingsSectionHeader("About") }
            item { SettingsInfoRow("Version", "1.0.0", isFocused = focusedItem == "version", onClick = {}, onFocus = { focusedItem = "version" }) }
            item { SettingsInfoRow("Build", "Release", isFocused = focusedItem == "build", onClick = {}, onFocus = { focusedItem = "build" }) }

            item { Spacer(Modifier.height(24.dp)) }
            item {
                SettingsActionRow(
                    label = "Sign out",
                    isDangerous = true,
                    isFocused = focusedItem == "logout",
                    onClick = onLogout,
                    onFocus = { focusedItem = "logout" },
                    modifier = Modifier.focusRequester(if (focusedItem == null) firstFocus else FocusRequester())
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(label: String) {
    Text(
        label,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = AccentBlue,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) NavFocusBg else Color.Transparent)
            .then(if (isFocused) Modifier.border(1.dp, AccentBlue.copy(alpha = 0.5f), RoundedCornerShape(8.dp)) else Modifier)
            .focusable(interactionSource = interactionSource)
            .clickable { onToggle(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = TextPrimary)
        Box(
            modifier = Modifier
                .size(40.dp, 22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (checked) AccentBlue else ButtonBg)
                .padding(2.dp)
                .clickable { onToggle(!checked) },
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.White)
            )
        }
    }
}

@Composable
private fun SettingsInfoRow(
    label: String,
    value: String,
    isFocused: Boolean,
    onClick: () -> Unit,
    onFocus: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) NavFocusBg else Color.Transparent)
            .then(if (isFocused) Modifier.border(1.dp, AccentBlue.copy(alpha = 0.5f), RoundedCornerShape(8.dp)) else Modifier)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = TextPrimary)
        Text(value, fontSize = 14.sp, color = TextMuted)
    }
}

@Composable
private fun SettingsActionRow(
    label: String,
    isDangerous: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color(0xFF2A0A0A) else Color.Transparent)
            .then(if (isFocused) Modifier.border(1.dp, RedColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp)) else Modifier)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = RedColor)
    }
}
