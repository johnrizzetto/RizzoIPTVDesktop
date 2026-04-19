package com.rizzoplayer.iptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rizzoplayer.iptv.data.local.PreferencesStore
import com.rizzoplayer.iptv.ui.theme.*

@Composable
fun ParentalLockOverlay(
    preferencesStore: PreferencesStore,
    onUnlock: () -> Unit,
    onCancel: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✓")
    val focusRequesters = remember { List(12) { FocusRequester() } }
    var focusedIdx by remember { mutableIntStateOf(9) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        try { focusRequesters[9].requestFocus() } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PanelBg)
                .border(1.dp, BorderColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(32.dp)
        ) {
            Text(" Parental Lock ", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text("Enter PIN to continue", fontSize = 12.sp, color = TextMuted)
            Spacer(Modifier.height(24.dp))

            // PIN dots
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) { i ->
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (i < pin.length) AccentBlue else BorderColor.copy(alpha = 0.4f)
                            )
                    )
                }
            }

            if (error) {
                Spacer(Modifier.height(8.dp))
                Text("Incorrect PIN — try again", fontSize = 11.sp, color = RedColor)
            }

            Spacer(Modifier.height(24.dp))

            // Keypad
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), listOf(9, 10, 11)).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { idx ->
                            val key = keys[idx]
                            val isFocused = focusedIdx == idx
                            Box(
                                modifier = Modifier
                                    .size(72.dp, 48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isFocused) NavFocusBg else ButtonBg)
                                    .then(
                                        if (isFocused) Modifier.border(1.5.dp, AccentBlue, RoundedCornerShape(8.dp))
                                        else Modifier.border(0.5.dp, BorderColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    )
                                    .focusRequester(focusRequesters[idx])
                                    .onFocusChanged { if (it.isFocused) focusedIdx = idx }
                                    .focusable()
                                    .clickable {
                                        when (key) {
                                            "⌫" -> {
                                                if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                                error = false
                                            }
                                            "✓" -> {
                                                if (preferencesStore.verifyParentalLockPin(pin)) {
                                                    onUnlock()
                                                } else {
                                                    error = true
                                                    pin = ""
                                                }
                                            }
                                            else -> {
                                                if (pin.length < 4) {
                                                    pin += key
                                                    error = false
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    key,
                                    fontSize = if (key == "✓") 20.sp else 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = when {
                                        key == "✓" -> GreenSeeders
                                        key == "⌫" -> TextMuted
                                        else -> TextPrimary
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Cancel",
                fontSize = 13.sp,
                color = TextMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onCancel)
                    .padding(8.dp)
            )
        }
    }
}
