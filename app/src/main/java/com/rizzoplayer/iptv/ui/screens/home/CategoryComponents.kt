package com.rizzoplayer.iptv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rizzoplayer.iptv.data.model.Category
import androidx.compose.ui.graphics.Color
import com.rizzoplayer.iptv.ui.theme.AccentBlue
import com.rizzoplayer.iptv.ui.theme.NavFocusBg
import com.rizzoplayer.iptv.ui.theme.TextMuted
import com.rizzoplayer.iptv.ui.theme.TextPrimary

@Composable
fun BackRow(
    label: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isFocused) NavFocusBg else Color.Transparent)
            .then(if (isFocused) Modifier.border(2.dp, AccentBlue, RoundedCornerShape(6.dp)) else Modifier)
            .focusable(interactionSource = interactionSource)
            .clickable(onClick = onBack)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("←", fontSize = 16.sp, color = if (isFocused) AccentBlue else TextMuted)
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isFocused) TextPrimary else TextMuted
        )
    }
}

@Composable
fun CategoryRow(
    category: Category,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isFocused) NavFocusBg else androidx.compose.ui.graphics.Color.Transparent)
            .then(if (isFocused) Modifier.border(2.dp, AccentBlue, RoundedCornerShape(6.dp)) else Modifier)
            .focusable(interactionSource = interactionSource)
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("▣", fontSize = 18.sp, color = if (isFocused) AccentBlue else TextMuted)
        Spacer(Modifier.width(12.dp))
        Text(
            category.name,
            fontSize = 14.sp,
            color = if (isFocused) TextPrimary else TextMuted,
            fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal
        )
        Spacer(Modifier.weight(1f))
        Text(
            "",
            fontSize = 12.sp,
            color = TextMuted
        )
    }
}

@Composable
fun CategoryDivider(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted.copy(alpha = 0.7f),
            letterSpacing = 1.sp
        )
    }
}
