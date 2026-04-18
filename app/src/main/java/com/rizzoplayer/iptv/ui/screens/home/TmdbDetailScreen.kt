package com.rizzoplayer.iptv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.graphics.Brush
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
import com.rizzoplayer.iptv.data.model.TmdbMovie
import com.rizzoplayer.iptv.data.model.TmdbShow
import com.rizzoplayer.iptv.ui.theme.*

@Composable
fun TmdbMovieDetailView(
    movie: TmdbMovie,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val backdropUrl = movie.backdropPath?.let { "${AppConfig.TMDB_IMAGE_BASE}/${AppConfig.TMDB_BACKDROP_SIZE}$it" }
    val posterUrl = movie.posterPath?.let { "${AppConfig.TMDB_IMAGE_BASE}/${AppConfig.TMDB_POSTER_SIZE}$it" }
    val playFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { try { playFocus.requestFocus() } catch (_: Exception) {} }

    Box(modifier = Modifier.fillMaxSize()) {
        // Full-bleed backdrop
        if (backdropUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(backdropUrl)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.3f),
                                MainBg.copy(alpha = 0.7f),
                                MainBg
                            ),
                            startY = 0f
                        )
                    )
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(MainBg))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            item {
                // Top spacer for backdrop breathing room
                Spacer(modifier = Modifier.height(120.dp))
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Poster
                    if (posterUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(posterUrl)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .crossfade(true)
                                .build(),
                            contentDescription = movie.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .width(120.dp)
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(Modifier.width(20.dp))
                    }

                    Column(Modifier.weight(1f)) {
                        Text(
                            movie.title,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            movie.releaseDate.take(4).let { if (it.isNotEmpty()) Text(it, fontSize = 13.sp, color = TextMuted); Spacer(Modifier.width(10.dp)) }
                            if (movie.rating > 0) {
                                Text("★ %.1f".format(movie.rating), fontSize = 13.sp, color = BrandGold)
                                Spacer(Modifier.width(10.dp))
                            }
                            movie.runtime?.let { if (it > 0) Text("${it}m", fontSize = 13.sp, color = TextMuted) }
                        }
                        Spacer(Modifier.height(12.dp))

                        // Action buttons row
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            var playFocused by remember { mutableStateOf(false) }
                            Text(
                                "▶ Play",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (playFocused) MainBg else Color.White,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (playFocused) AccentBlue else AccentBlue.copy(alpha = 0.85f))
                                    .focusRequester(playFocus)
                                    .onFocusChanged { playFocused = it.isFocused }
                                    .focusable()
                                    .clickable(onClick = onPlay)
                                    .padding(horizontal = 20.dp, vertical = 10.dp)
                            )
                            var favFocused by remember { mutableStateOf(false) }
                            Text(
                                if (isFavorite) "♥" else "♡",
                                fontSize = 16.sp,
                                color = if (favFocused) AccentBlue else if (isFavorite) RedColor else TextMuted,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (favFocused) AccentBlue.copy(alpha = 0.15f) else Color.Transparent)
                                    .then(if (favFocused) Modifier.border(1.dp, AccentBlue.copy(alpha = 0.5f), RoundedCornerShape(8.dp)) else Modifier)
                                    .onFocusChanged { favFocused = it.isFocused }
                                    .focusable()
                                    .clickable(onClick = onToggleFavorite)
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
                if (movie.overview.isNotEmpty()) {
                    Text(
                        "Overview",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        movie.overview,
                        fontSize = 13.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        lineHeight = 19.sp
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
