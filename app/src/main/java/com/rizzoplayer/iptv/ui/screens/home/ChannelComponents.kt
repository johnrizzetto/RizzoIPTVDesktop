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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rizzoplayer.iptv.data.model.LiveStream
import com.rizzoplayer.iptv.ui.theme.AccentBlue
import com.rizzoplayer.iptv.ui.theme.CardBg
import com.rizzoplayer.iptv.ui.theme.NavFocusBg
import com.rizzoplayer.iptv.ui.theme.RedColor
import com.rizzoplayer.iptv.ui.theme.TextMuted
import com.rizzoplayer.iptv.ui.theme.TextPrimary

@Composable
fun ChannelRow(
    stream: LiveStream,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onFavToggle: () -> Unit,
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
            .clickable(onClick = onPlay)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelLogo(stream.icon, stream.name, 36)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(stream.name, fontSize = 13.sp, color = if (isFocused) TextPrimary else TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            if (isFavorite) "♥" else "♡",
            fontSize = 14.sp,
            color = if (isFavorite) RedColor else TextMuted,
            modifier = Modifier.padding(horizontal = 6.dp).clickable(onClick = onFavToggle)
        )
    }
}
