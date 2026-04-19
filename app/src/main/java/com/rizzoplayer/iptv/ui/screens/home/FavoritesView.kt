package com.rizzoplayer.iptv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.rizzoplayer.iptv.AppConfig
import com.rizzoplayer.iptv.data.model.Favorite
import com.rizzoplayer.iptv.ui.theme.*

@Composable
fun FavoritesView(
    favorites: List<Favorite>,
    onPlay: (Favorite) -> Unit,
    onRemove: (Favorite) -> Unit,
    onMove: (Favorite, Int) -> Unit
) {
    if (favorites.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("♡", fontSize = 40.sp, color = TextMuted.copy(alpha = 0.4f))
                Spacer(Modifier.height(12.dp))
                Text("No favorites yet", fontSize = 14.sp, color = TextMuted)
                Text("Long-press any title to add one", fontSize = 11.sp, color = TextMuted.copy(alpha = 0.6f))
            }
        }
        return
    }

    val firstFocus = remember { FocusRequester() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(favorites, key = { _, f -> f.id }, contentType = { _, _ -> "Favorite" }) { idx, fav ->
            val isFirst = idx == 0
            FavoriteRow(
                fav = fav,
                onPlay = { onPlay(fav) },
                onRemove = { onRemove(fav) },
                onMoveUp = { if (idx > 0) onMove(fav, -1) },
                onMoveDown = { if (idx < favorites.size - 1) onMove(fav, 1) },
                modifier = if (isFirst) Modifier.focusRequester(firstFocus) else Modifier
            )
        }
    }
    LaunchedEffect(favorites) { try { firstFocus.requestFocus() } catch (_: Exception) {} }
}

@Composable
private fun FavoriteRow(
    fav: Favorite,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    if (showMenu) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(CardBg)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("↑ Move Up" to onMoveUp, "↓ Move Down" to onMoveDown, "✕ Remove" to {
                showMenu = false; onRemove()
            }).forEach { (label, action) ->
                var mFocused by remember { mutableStateOf(false) }
                Text(
                    label,
                    fontSize = 12.sp,
                    color = if (mFocused) AccentBlue else TextMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (mFocused) AccentBlue.copy(alpha = 0.15f) else Color.Transparent)
                        .onFocusChanged { mFocused = it.isFocused }
                        .focusable()
                        .clickable { action() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) CardFocused else CardBg)
            .then(if (focused) Modifier.border(2.dp, AccentBlue, RoundedCornerShape(6.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onPlay)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconUrl = fav.icon?.let {
            when {
                it.startsWith("http") -> it
                fav.type == "tmdb_movie" || fav.type == "tmdb_show" || fav.type == "tmdb_episode" ->
                    "${AppConfig.TMDB_IMAGE_BASE}/w342$it"
                else -> null
            }
        }
        if (iconUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(iconUrl)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(true)
                    .build(),
                contentDescription = fav.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(48.dp, 72.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
        } else {
            Box(
                Modifier
                    .size(48.dp, 72.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(CardBg),
                contentAlignment = Alignment.Center
            ) {
                Text(fav.name.take(1).uppercase(), color = TextMuted, fontSize = 20.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(fav.name, fontSize = 13.sp, color = if (focused) TextPrimary else TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                when (fav.type) {
                    "live" -> "Live TV"
                    "vod" -> "Movie"
                    "episode" -> "Episode"
                    "tmdb_movie" -> "Movie"
                    "tmdb_episode" -> "Episode"
                    "tmdb_show" -> "Show"
                    else -> fav.type
                },
                fontSize = 10.sp,
                color = TextMuted.copy(alpha = 0.7f)
            )
        }
        Text("♥", fontSize = 14.sp, color = RedColor)
        Spacer(Modifier.width(4.dp))
        Text(
            "⋮",
            fontSize = 16.sp,
            color = if (focused) AccentBlue else TextMuted,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable { showMenu = true }
                .padding(4.dp)
        )
    }
}
