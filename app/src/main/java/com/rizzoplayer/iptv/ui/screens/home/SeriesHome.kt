package com.rizzoplayer.iptv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import coil.Coil
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.rizzoplayer.iptv.AppConfig
import com.rizzoplayer.iptv.data.model.Favorite
import com.rizzoplayer.iptv.data.model.TmdbEpisode
import com.rizzoplayer.iptv.data.model.TmdbSeason
import com.rizzoplayer.iptv.data.model.TmdbShow
import com.rizzoplayer.iptv.ui.theme.*
import com.rizzoplayer.iptv.ui.viewmodel.BrowseContent

@Composable
fun SeriesHome(
    content: BrowseContent.TmdbShows,
    favorites: Map<String, Favorite>,
    onSelectShow: (TmdbShow) -> Unit,
    onToggleFavorite: (TmdbShow) -> Unit
) {
    val hero = content.items.firstOrNull()
    val heroBackdrop = hero?.backdropPath?.let {
        "${AppConfig.TMDB_IMAGE_BASE}/${AppConfig.TMDB_BACKDROP_SIZE}$it"
    }
    val heroPoster = hero?.posterPath?.let {
        "${AppConfig.TMDB_IMAGE_BASE}/${AppConfig.TMDB_POSTER_SIZE}$it"
    }

    Column(modifier = Modifier.fillMaxSize()) {
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
                                )
                                ,
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
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .crossfade(true)
                                .build(),
                            contentDescription = hero.name,
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
                            hero.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            hero.firstAirDate.take(4).let { if (it.isNotEmpty()) { Text(it, fontSize = 12.sp, color = TextMuted); Spacer(Modifier.width(8.dp)) } }
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

        if (content.genreName.isNotEmpty()) {
            Text(
                content.genreName,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        TmdbShowGrid(
            content = content,
            favorites = favorites,
            onSelectShow = onSelectShow,
            onToggleFavorite = onToggleFavorite
        )
    }
}

@Composable
fun TmdbShowGrid(
    content: BrowseContent.TmdbShows,
    favorites: Map<String, Favorite>,
    onSelectShow: (TmdbShow) -> Unit,
    onToggleFavorite: (TmdbShow) -> Unit
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
        columns = GridCells.Adaptive(180.dp),
        modifier = Modifier.fillMaxSize().focusGroup(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(content.items, key = { it.id }, contentType = { "TmdbShow" }) { show ->
            val isFirst = content.items.firstOrNull()?.id == show.id
            var focused by remember { mutableStateOf(false) }
            TmdbPosterCard(
                title = show.name,
                posterPath = show.posterPath,
                rating = show.rating,
                year = show.firstAirDate.take(4),
                overview = show.overview,
                isFocused = focused,
                isFavorite = favorites.containsKey(show.id.toString()),
                onFocusChanged = { focused = it },
                onClick = { onSelectShow(show) },
                onLongClick = { onToggleFavorite(show) }
            )
        }
    }
}

@Composable
fun TmdbShowDetailView(
    show: TmdbShow,
    seasons: List<TmdbSeason>,
    favorites: Map<String, Favorite>,
    onPlay: (TmdbEpisode) -> Unit,
    onFavToggle: (TmdbEpisode) -> Unit
) {
    val backdrop = show.backdropPath?.let { "${AppConfig.TMDB_IMAGE_BASE}/${AppConfig.TMDB_BACKDROP_SIZE}$it" }
    val poster = show.posterPath?.let { "${AppConfig.TMDB_IMAGE_BASE}/${AppConfig.TMDB_POSTER_SIZE}$it" }
    var selectedSeasonIdx by remember { mutableIntStateOf(0) }
    val seasonFocus = remember { FocusRequester() }
    val episodeFocus = remember { FocusRequester() }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header with backdrop
        if (backdrop != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(backdrop)
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
                                colors = listOf(Color.Transparent, MainBg.copy(alpha = 0.7f), MainBg),
                                startY = 0f
                            )
                        )
                )
                Row(
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (poster != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(poster)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .crossfade(true)
                                .build(),
                            contentDescription = show.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .width(70.dp)
                                .height(105.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(show.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (show.rating > 0) {
                                Text("★ %.1f".format(show.rating), fontSize = 12.sp, color = BrandGold)
                                Spacer(Modifier.width(8.dp))
                            }
                            show.firstAirDate.take(4).let { if (it.isNotEmpty()) Text("$it", fontSize = 11.sp, color = TextMuted) }
                            if (show.numberOfSeasons > 0) {
                                Text(" · ${show.numberOfSeasons} seasons", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                        if (show.overview.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(show.overview, fontSize = 10.sp, color = TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        // Season tabs
        if (seasons.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                seasons.forEachIndexed { idx, season ->
                    var focused by remember { mutableStateOf(false) }
                    val isSelected = idx == selectedSeasonIdx
                    Text(
                        season.name,
                        fontSize = 12.sp,
                        color = if (isSelected) AccentBlue else if (focused) TextPrimary else TextMuted,
                        fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) AccentBlue.copy(alpha = 0.2f) else Color.Transparent)
                            .then(if (focused && !isSelected) Modifier.border(1.dp, AccentBlue.copy(alpha = 0.5f), RoundedCornerShape(4.dp)) else Modifier)
                            .onFocusChanged { focused = it.isFocused }
                            .focusable()
                            .clickable {
                                selectedSeasonIdx = idx
                                seasonFocus.requestFocus()
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Episodes list
        seasons.getOrNull(selectedSeasonIdx)?.episodes?.let { episodes ->
            LaunchedEffect(episodes) { try { episodeFocus.requestFocus() } catch (_: Exception) {} }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(episodes, key = { "${it.seasonNumber}-${it.episodeNumber}" }, contentType = { "TmdbEpisode" }) { episode ->
                    val isFav = favorites.containsKey("${show.id}:${episode.seasonNumber}:${episode.episodeNumber}")
                    TmdbEpisodeRow(
                        episode = episode,
                        isFavorite = isFav,
                        onPlay = { onPlay(episode) },
                        onFavToggle = { onFavToggle(episode) },
                        onFocus = episodeFocus
                    )
                }
            }
        }
    }
}

@Composable
fun TmdbEpisodeRow(
    episode: TmdbEpisode,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onFavToggle: () -> Unit,
    onFocus: FocusRequester
) {
    var focused by remember { mutableStateOf(false) }
    val stillUrl = episode.stillPath?.let { "${AppConfig.TMDB_IMAGE_BASE}/w185$it" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) NavFocusBg else Color.Transparent)
            .then(if (focused) Modifier.border(1.dp, AccentBlue.copy(alpha = 0.4f), RoundedCornerShape(6.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onPlay)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Still / thumbnail
        Box(
            modifier = Modifier
                .size(120.dp, 68.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CardBg),
            contentAlignment = Alignment.Center
        ) {
            if (stillUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(stillUrl)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    "E${episode.episodeNumber}",
                    fontSize = 18.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Bold
                )
            }
            // Episode number badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text("E${episode.episodeNumber}", fontSize = 9.sp, color = Color.White)
            }
        }

        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                episode.name.ifEmpty { "Episode ${episode.episodeNumber}" },
                fontSize = 12.sp,
                color = if (focused) TextPrimary else TextMuted,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                episode.airDate?.take(4)?.let { if (it.isNotEmpty()) Text(it, fontSize = 10.sp, color = TextMuted.copy(alpha = 0.7f)) }
                if (episode.runtime != null && episode.runtime > 0) {
                    Text(" · ${episode.runtime}m", fontSize = 10.sp, color = TextMuted.copy(alpha = 0.7f))
                }
            }
            if (focused && episode.overview.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    episode.overview,
                    fontSize = 10.sp,
                    color = TextMuted.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            if (isFavorite) "♥" else "♡",
            fontSize = 14.sp,
            color = if (isFavorite) RedColor else TextMuted,
            modifier = Modifier.padding(horizontal = 4.dp).clickable(onClick = onFavToggle)
        )
    }
}
