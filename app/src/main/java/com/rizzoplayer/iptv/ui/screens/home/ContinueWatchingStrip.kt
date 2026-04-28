package com.rizzoplayer.iptv.ui.screens.home

import com.rizzoplayer.iptv.ui.designsystem.rizzoFocusable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import com.rizzoplayer.iptv.AppConfig
import com.rizzoplayer.iptv.RizzoApp
import com.rizzoplayer.iptv.data.local.PlaybackPositionStore
import com.rizzoplayer.iptv.data.model.RecentItem
import com.rizzoplayer.iptv.ui.designsystem.rizzoFocusGroup
import com.rizzoplayer.iptv.ui.designsystem.RizzoProgressBar
import com.rizzoplayer.iptv.ui.theme.*

@Composable
fun ContinueWatchingStrip(items: List<RecentItem>, onPlay: (RecentItem) -> Unit) {
    val playbackPositionStore = (LocalContext.current.applicationContext as RizzoApp).playbackPositionStore
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            "Continue Watching",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth().rizzoFocusGroup(),
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(items, key = { "${it.type}:${it.id}" }, contentType = { "RecentItem" }) { item ->
                RecentCard(item = item, store = playbackPositionStore, onClick = { onPlay(item) })
            }
        }
    }
}

@Composable
private fun RecentCard(item: RecentItem, store: PlaybackPositionStore, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    var fraction by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(item.id, item.type) {
        val p = store.getProgress("${item.type}:${item.id}")
        fraction = if (p != null && p.durationMs > 0)
            (p.positionMs.toFloat() / p.durationMs).coerceIn(0f, 1f) else 0f
    }
    Column(
        modifier = Modifier
            .background(CardBg, RoundedCornerShape(6.dp))
            .rizzoFocusable(
                onClick = onClick,
                interactionSource = interactionSource,
                shape = RoundedCornerShape(6.dp),
                focusBorderColor = AccentBlue.copy(alpha = 0.6f),
                focusBackgroundColor = AccentBlue.copy(alpha = 0.25f)
            )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChannelLogo(url = item.icon, name = item.name, size = 24)
            Spacer(Modifier.width(6.dp))
            Text(
                item.name,
                fontSize = 11.sp,
                color = if (isFocused) Color.White else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 110.dp)
            )
        }
        if (fraction > 0f && fraction < 0.98f) {
            RizzoProgressBar(
                progress = fraction,
                modifier = Modifier.fillMaxWidth().height(2.dp),
                fillColor = AccentBlue,
                trackColor = CardBg
            )
        }
    }
}

@Composable
fun ChannelLogo(url: String?, name: String, size: Int) {
    if (url.isNullOrBlank()) {
        LogoFallback(name, size)
    } else {
        val fullUrl = when {
            url.startsWith("http") -> url
            url.startsWith("/") -> "${AppConfig.TMDB_IMAGE_BASE}/w92$url"
            else -> "$url"
        }
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(fullUrl)
                .size(Size(size * 2, size * 2))
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(true)
                .build(),
            contentDescription = name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size.dp).clip(RoundedCornerShape(4.dp))
        )
    }
}

@Composable
private fun LogoFallback(name: String, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(CardBg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.take(1).uppercase(),
            color = TextMuted,
            fontSize = (size * 0.45f).sp
        )
    }
}
