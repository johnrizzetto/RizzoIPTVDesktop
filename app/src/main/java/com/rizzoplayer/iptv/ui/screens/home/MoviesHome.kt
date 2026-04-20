package com.rizzoplayer.iptv.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.Coil
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import com.rizzoplayer.iptv.AppConfig
import com.rizzoplayer.iptv.data.model.Favorite
import com.rizzoplayer.iptv.data.model.RecentItem
import com.rizzoplayer.iptv.data.model.TmdbMovie
import com.rizzoplayer.iptv.ui.theme.*
import com.rizzoplayer.iptv.ui.viewmodel.BrowseContent
import androidx.compose.animation.core.animateFloatAsState

@Composable
fun MoviesHome(
    content: BrowseContent.TmdbMovies,
    favorites: Map<String, Favorite>,
    continueWatchingItems: List<RecentItem> = emptyList(),
    onSelectMovie: (TmdbMovie) -> Unit,
    onToggleFavorite: (TmdbMovie) -> Unit,
    onPlayRecent: ((RecentItem) -> Unit)? = null,
    onFocusPrefetch: ((Int) -> Unit)? = null
) {
    val hero = content.items.firstOrNull()
    val heroBackdrop = hero?.backdropPath?.let {
        "${AppConfig.TMDB_IMAGE_BASE}/${AppConfig.TMDB_BACKDROP_SIZE}$it"
    }
    val heroPoster = hero?.posterPath?.let {
        "${AppConfig.TMDB_IMAGE_BASE}/${AppConfig.TMDB_POSTER_SIZE}$it"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val filteredContinueWatching = continueWatchingItems.filter { it.type == "vod" || it.type == "tmdb_movie" }
        if (filteredContinueWatching.isNotEmpty() && onPlayRecent != null) {
            ContinueWatchingStrip(
                items = filteredContinueWatching,
                onPlay = onPlayRecent
            )
        }
        // Hero backdrop
        if (heroBackdrop != null && hero != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(heroBackdrop)
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
                                    Color.Transparent,
                                    MainBg.copy(alpha = 0.6f),
                                    MainBg
                                ),
                                startY = 40f
                            )
                        )
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (heroPoster != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(heroPoster)
                                .size(Size(160, 240))
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .crossfade(true)
                                .build(),
                            contentDescription = hero.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .width(80.dp)
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(14.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            hero.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            hero.releaseDate.take(4).let { if (it.isNotEmpty()) { Text(it, fontSize = 12.sp, color = TextMuted); Spacer(Modifier.width(8.dp)) } }
                            if (hero.rating > 0) {
                                Text("★ %.1f".format(hero.rating), fontSize = 12.sp, color = BrandGold)
                                Spacer(Modifier.width(8.dp))
                            }
                            content.genreName.let { if (it.isNotEmpty()) Text(it, fontSize = 11.sp, color = TextMuted.copy(alpha = 0.8f)) }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            hero.overview,
                            fontSize = 11.sp,
                            color = TextMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Genre label below hero
        if (content.genreName.isNotEmpty()) {
            Text(
                content.genreName,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        // Movie grid
        TmdbMovieGrid(
            content = content,
            favorites = favorites,
            onSelectMovie = onSelectMovie,
            onToggleFavorite = onToggleFavorite,
            onFocusPrefetch = onFocusPrefetch
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TmdbMovieGrid(
    content: BrowseContent.TmdbMovies,
    favorites: Map<String, Favorite>,
    onSelectMovie: (TmdbMovie) -> Unit,
    onToggleFavorite: (TmdbMovie) -> Unit,
    onFocusPrefetch: ((Int) -> Unit)? = null
) {
    val firstFocus = remember { FocusRequester() }
    val listState = rememberLazyGridState()
    val context = LocalContext.current
    val imageLoader = remember { Coil.imageLoader(context) }

    LaunchedEffect(content.items) { try { firstFocus.requestFocus() } catch (_: Exception) {} }

    LaunchedEffect(listState.firstVisibleItemIndex) {
        val firstVisible = listState.firstVisibleItemIndex
        val preloadAhead = 6
        val gridSpan = 3
        val startIndex = firstVisible * gridSpan
        (startIndex until minOf(startIndex + preloadAhead * gridSpan, content.items.size)).forEach { idx ->
            val posterPath = content.items.getOrNull(idx)?.posterPath ?: return@forEach
            val url = "${AppConfig.TMDB_IMAGE_BASE}/${AppConfig.TMDB_POSTER_SIZE}$posterPath"
            val request = ImageRequest.Builder(context)
                .data(url)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
            imageLoader.enqueue(request)
        }
    }

    LazyVerticalGrid(
        state = listState,
        columns = GridCells.Adaptive(120.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(content.items, key = { it.id }, contentType = { "TmdbMovie" }) { movie ->
            val isFirst = content.items.firstOrNull()?.id == movie.id
            var focused by remember { mutableStateOf(false) }
            TmdbPosterCard(
                title = movie.title,
                posterPath = movie.posterPath,
                rating = movie.rating,
                year = movie.releaseDate.take(4),
                overview = movie.overview,
                isFocused = focused,
                isFavorite = favorites.containsKey(movie.id.toString()),
                onFocusChanged = { focused = it },
                onClick = { onSelectMovie(movie) },
                onLongClick = { onToggleFavorite(movie) },
                onFocusPrefetch = onFocusPrefetch,
                prefetchId = movie.id,
                modifier = if (isFirst) Modifier.focusRequester(firstFocus) else Modifier
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TmdbPosterCard(
    title: String,
    posterPath: String?,
    rating: Float,
    year: String,
    overview: String,
    isFocused: Boolean,
    isFavorite: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: androidx.compose.ui.unit.Dp = 120.dp,
    posterHeight: androidx.compose.ui.unit.Dp = 180.dp,
    onFocusPrefetch: ((Int) -> Unit)? = null,
    prefetchId: Int? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isCardFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isCardFocused) 1.05f else 1f, label = "cardScale")

    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .width(cardWidth)
                .align(Alignment.TopCenter)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .focusable(interactionSource = interactionSource)
                .clickable(onClick = onClick)
                .onFocusChanged {
                onFocusChanged(it.isFocused)
                if (it.isFocused && prefetchId != null && onFocusPrefetch != null) {
                    onFocusPrefetch.invoke(prefetchId)
                }
            }
                .then(
                    if (isCardFocused) Modifier.border(2.dp, AccentBlue, RoundedCornerShape(8.dp))
                    else Modifier
                ),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isCardFocused) 8.dp else 2.dp)
        ) {
            Column {
                Box {
                    if (posterPath != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data("${AppConfig.TMDB_IMAGE_BASE}/${AppConfig.TMDB_POSTER_SIZE}$posterPath")
                                .size(Size(240, 360))
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .crossfade(true)
                                .build(),
                            contentDescription = title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(posterHeight)
                        )
                    } else {
                        PosterFallback(title, posterHeight)
                    }
                    if (rating > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.65f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text("★ %.1f".format(rating), fontSize = 10.sp, color = BrandGold)
                        }
                    }
                    if (isFavorite) {
                        Text(
                            "♥",
                            fontSize = 16.sp,
                            color = RedColor,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(2.dp)
                        )
                    }
                }
                Column(Modifier.padding(8.dp)) {
                    Text(
                        title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCardFocused) TextPrimary else TextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (year.isNotEmpty()) {
                        Text(year, fontSize = 10.sp, color = TextMuted.copy(alpha = 0.7f))
                    }
                }
            }
        }

        // Expanded info on focus
        if (isCardFocused && overview.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.95f)
                    .offset(y = 4.dp)
                    .border(1.dp, AccentBlue.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = CardFocused.copy(alpha = 0.95f)),
            ) {
                Text(
                    overview,
                    fontSize = 10.sp,
                    color = TextPrimary.copy(alpha = 0.85f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun PosterFallback(title: String, height: androidx.compose.ui.unit.Dp = 210.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(CardBg),
        contentAlignment = Alignment.Center
    ) {
        Text(title.take(2).uppercase(), color = TextMuted, fontSize = 28.sp)
    }
}
