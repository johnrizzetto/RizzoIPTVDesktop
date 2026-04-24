package com.rizzoplayer.iptv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import com.rizzoplayer.iptv.AppConfig
import com.rizzoplayer.iptv.data.model.TmdbMovie
import com.rizzoplayer.iptv.data.model.TmdbShow
import com.rizzoplayer.iptv.ui.designsystem.RizzoButton
import com.rizzoplayer.iptv.ui.designsystem.RizzoIconButton
import com.rizzoplayer.iptv.ui.designsystem.RizzoText
import com.rizzoplayer.iptv.ui.designsystem.RizzoTextStyle
import com.rizzoplayer.iptv.ui.theme.AccentBlue
import com.rizzoplayer.iptv.ui.theme.BrandGold
import com.rizzoplayer.iptv.ui.theme.MainBg
import com.rizzoplayer.iptv.ui.theme.RedColor
import com.rizzoplayer.iptv.ui.theme.RizzoAccent
import com.rizzoplayer.iptv.ui.theme.RizzoTextSecondary
import com.rizzoplayer.iptv.ui.theme.RizzoTextTertiary

// ─── Movie Detail ────────────────────────────────────────────────────────────

@Composable
fun TmdbMovieDetailView(
    movie: TmdbMovie,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    viewModel: com.rizzoplayer.iptv.ui.viewmodel.MainViewModel,
) {
    LaunchedEffect(movie.imdbId) {
        movie.imdbId?.let { viewModel.prefetchStream(it) }
    }

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
                    .size(Size(1280, 720))
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
            item { Spacer(modifier = Modifier.height(120.dp)) }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Poster thumbnail
                    if (posterUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(posterUrl)
                                .size(Size(240, 360))
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
                        // Title
                        RizzoText(
                            style = RizzoTextStyle.HeadingSm,
                            text = movie.title,
                            color = Color.White,
                            maxLines = 3,
                        )
                        Spacer(Modifier.height(6.dp))

                        // Metadata row: year | rating | runtime
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            movie.releaseDate.take(4).let {
                                if (it.isNotEmpty()) {
                                    RizzoText(style = RizzoTextStyle.LabelMd, text = it)
                                    Spacer(Modifier.width(10.dp))
                                }
                            }
                            if (movie.rating > 0) {
                                RizzoText(
                                    style = RizzoTextStyle.LabelMd,
                                    text = "★ %.1f".format(movie.rating),
                                    color = BrandGold,
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            movie.runtime?.let { if (it > 0) RizzoText(style = RizzoTextStyle.LabelMd, text = "${it}m") }
                        }
                        Spacer(Modifier.height(12.dp))

                        // Action buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RizzoButton(
                                text = "Play",
                                onClick = onPlay,
                                modifier = Modifier.focusRequester(playFocus),
                            )
                            RizzoIconButton(
                                icon = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                onClick = onToggleFavorite,
                                contentDesc = if (isFavorite) "Remove from favorites" else "Add to favorites",
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
                if (movie.overview.isNotEmpty()) {
                    RizzoText(
                        style = RizzoTextStyle.LabelLg,
                        text = "Overview",
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    RizzoText(
                        style = RizzoTextStyle.BodySm,
                        text = movie.overview,
                        color = RizzoTextSecondary,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        lineHeightOverride = 20,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
