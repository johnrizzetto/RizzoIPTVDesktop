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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
fun ContinueWatchingStrip(
    items: List<com.rizzoplayer.iptv.data.model.WatchHistoryItem>,
    onPlay: (com.rizzoplayer.iptv.data.model.WatchHistoryItem) -> Unit,
    focusRestorer: FocusRequester? = null,
) {
    val firstFocus = remember { FocusRequester() }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            "Continue Watching",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
        )
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .rizzoFocusGroup()
                .then(if (focusRestorer != null) Modifier.focusRequester(firstFocus) else Modifier),
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { it.id }, contentType = { "WatchHistoryItem" }) { item ->
                WatchHistoryCard(
                    item = item,
                    onClick = { onPlay(item) },
                    firstFocus = if (focusRestorer != null && item == items.firstOrNull()) firstFocus else null
                )
            }
        }
    }
}

@Composable
private fun WatchHistoryCard(
    item: com.rizzoplayer.iptv.data.model.WatchHistoryItem,
    onClick: () -> Unit,
    firstFocus: FocusRequester? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    var fraction = 0f
    var subtitle = ""

    when (item) {
        is com.rizzoplayer.iptv.data.model.WatchHistoryItem.Movie -> {
            if (item.durationMs > 0) {
                fraction = (item.watchedMs.toFloat() / item.durationMs).coerceIn(0f, 1f)
            }
            subtitle = "Movie"
        }
        is com.rizzoplayer.iptv.data.model.WatchHistoryItem.Series -> {
            if (item.episodeDurationMs > 0) {
                fraction = (item.episodeWatchedMs.toFloat() / item.episodeDurationMs).coerceIn(0f, 1f)
            }
            subtitle = "S${item.seasonNumber} E${item.episodeNumber}"
        }
    }

    Column(
        modifier = Modifier
            .width(100.dp)
            .then(if (firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier)
            .rizzoFocusable(
                onClick = onClick,
                interactionSource = interactionSource,
                shape = RoundedCornerShape(8.dp),
                focusBorderColor = AccentBlue,
                focusBackgroundColor = CardBg
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CardBg)
        ) {
            if (item.posterPath != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data("${AppConfig.TMDB_IMAGE_BASE}/${AppConfig.TMDB_POSTER_SIZE}${item.posterPath}")
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(item.title.take(1).uppercase(), fontSize = 32.sp, color = TextMuted)
                }
            }

            // Progress bar overlay at the bottom of the poster
            if (fraction > 0f && fraction < 0.98f) {
                RizzoProgressBar(
                    progress = fraction,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    fillColor = AccentBlue,
                    trackColor = Color.Black.copy(alpha = 0.5f)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.title,
            fontSize = 11.sp,
            color = if (isFocused) Color.White else TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            subtitle,
            fontSize = 10.sp,
            color = if (isFocused) AccentBlue else TextMuted,
            maxLines = 1,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
        )
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
