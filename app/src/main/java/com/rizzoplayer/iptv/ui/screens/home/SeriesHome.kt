package com.rizzoplayer.iptv.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.FlowPreview
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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
import coil.size.Size
import com.rizzoplayer.iptv.AppConfig
import com.rizzoplayer.iptv.data.model.Favorite
import com.rizzoplayer.iptv.ui.viewmodel.MainViewModel
import com.rizzoplayer.iptv.data.model.RecentItem
import com.rizzoplayer.iptv.data.model.TmdbEpisode
import com.rizzoplayer.iptv.data.model.TmdbSeason
import com.rizzoplayer.iptv.data.model.TmdbShow
import com.rizzoplayer.iptv.ui.theme.*
import com.rizzoplayer.iptv.ui.designsystem.rizzoGridTopRowFocus
import com.rizzoplayer.iptv.ui.designsystem.rizzoFocusGroup
import com.rizzoplayer.iptv.ui.designsystem.rizzoFocusable
import com.rizzoplayer.iptv.ui.viewmodel.BrowseContent

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeriesHome(
    content: BrowseContent.TmdbShows,
    favorites: Map<String, Favorite>,
    continueWatchingItems: List<com.rizzoplayer.iptv.data.model.WatchHistoryItem> = emptyList(),
    onSelectShow: (TmdbShow) -> Unit,
    onToggleFavorite: (TmdbShow) -> Unit,
    onPlayRecent: ((com.rizzoplayer.iptv.data.model.WatchHistoryItem) -> Unit)? = null,
    isGridLoading: Boolean = false,
    initialScrollIndex: Int = -1,
    onScrollRestored: () -> Unit = {},
    onScrollPositionChange: (Int) -> Unit = {}
) {
    // Hero auto-rotation state
    val heroItems = content.items.take(10) // showcase up to 10 items
    var heroIndex by remember { mutableIntStateOf(0) }
    val posterInteractionSource = remember { MutableInteractionSource() }
    val isPosterFocused by posterInteractionSource.collectIsFocusedAsState()

    // Auto-rotate hero every 8000ms, pausing when hero poster is focused
    LaunchedEffect(isPosterFocused) {
        if (!isPosterFocused && heroItems.size > 1) {
            while (true) {
                kotlinx.coroutines.delay(Spec.heroRotateMs)
                heroIndex = (heroIndex + 1) % heroItems.size
            }
        }
    }

    val hero = heroItems.getOrNull(heroIndex) ?: heroItems.firstOrNull()
    val heroBackdrop = hero?.backdropPath?.let {
        "${AppConfig.TMDB_IMAGE_BASE}/${AppConfig.TMDB_BACKDROP_SIZE}$it"
    }
    val heroPoster = hero?.posterPath?.let {
        "${AppConfig.TMDB_IMAGE_BASE}/${AppConfig.TMDB_POSTER_SIZE}$it"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val filteredContinueWatching = continueWatchingItems.filterIsInstance<com.rizzoplayer.iptv.data.model.WatchHistoryItem.Series>()
        if (filteredContinueWatching.isNotEmpty() && onPlayRecent != null) {
            ContinueWatchingStrip(
                items = filteredContinueWatching,
                onPlay = onPlayRecent
            )
        }
        // Hero backdrop
        if (heroBackdrop != null) {
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
                                .size(Size(160, 240))
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .crossfade(true)
                                .build(),
                            contentDescription = hero.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .width(80.dp)
                                .height(120.dp)
                                .rizzoFocusable(
                                    onClick = { hero?.let { onSelectShow(it) } },
                                    interactionSource = posterInteractionSource,
                                    focusBorderColor = AccentBlue,
                                    focusBackgroundColor = CardBg,
                                    shape = RoundedCornerShape(8.dp)
                                )
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

        if (content.items.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📺", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("No titles found", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextMuted)
                    Spacer(Modifier.height(4.dp))
                    Text("Try a different category or check your connection", fontSize = 12.sp, color = TextMuted.copy(alpha = 0.6f))
                }
            }
        } else {
            TmdbShowGrid(
                content = content,
                favorites = favorites,
                onSelectShow = onSelectShow,
                onToggleFavorite = onToggleFavorite,
                isGridLoading = isGridLoading,
                initialScrollIndex = initialScrollIndex,
                onScrollRestored = onScrollRestored,
                onScrollPositionChange = onScrollPositionChange
            )
        }
    }
}
@OptIn(ExperimentalFoundationApi::class, FlowPreview::class)
@Composable
fun TmdbShowGrid(
    content: BrowseContent.TmdbShows,
    favorites: Map<String, Favorite>,
    onSelectShow: (TmdbShow) -> Unit,
    onToggleFavorite: (TmdbShow) -> Unit,
    isGridLoading: Boolean = false,
    initialScrollIndex: Int = -1,
    onScrollRestored: () -> Unit = {},
    onScrollPositionChange: (Int) -> Unit = {}
) {
    val firstFocus = remember { FocusRequester() }
    val listState = rememberLazyGridState()
    val context = LocalContext.current
    val imageLoader = remember { Coil.imageLoader(context) }

    // Restore scroll position when returning via back navigation
    LaunchedEffect(initialScrollIndex) {
        if (initialScrollIndex >= 0 && listState.firstVisibleItemIndex == 0) {
            listState.scrollToItem(initialScrollIndex)
            onScrollRestored()
        }
    }

    LaunchedEffect(content.items) {
        withFrameNanos { } // ensure layout is attached before requesting focus
        try { firstFocus.requestFocus() } catch (_: Exception) {}
    }

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

    // Report scroll position to caller for back-navigation restoration
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .debounce(300L)
            .collect { idx -> onScrollPositionChange(idx) }
    }

    if (isGridLoading) {
        ShimmerShowGrid()
    } else {
        LazyVerticalGrid(
            state = listState,
            columns = GridCells.Adaptive(120.dp),
            modifier = Modifier
                .fillMaxSize()
                .rizzoFocusGroup()
                .rizzoGridTopRowFocus(firstFocus),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(content.items, key = { it.id }, contentType = { "TmdbShow" }) { show ->
                val isFirst = content.items.firstOrNull()?.id == show.id
                TmdbPosterCard(
                    title = show.name,
                    posterPath = show.posterPath,
                    rating = show.rating,
                    year = show.firstAirDate.take(4),
                    overview = show.overview,
                    isFavorite = favorites.containsKey(show.id.toString()),
                    onClick = { onSelectShow(show) },
                    onLongClick = { onToggleFavorite(show) },

                    modifier = if (isFirst) Modifier.focusRequester(firstFocus) else Modifier,
                    cardWidth = 120.dp,
                    posterHeight = 180.dp
                )
            }
        }
    }
}

@Composable
fun TmdbShowDetailView(
    show: TmdbShow,
    seasons: List<TmdbSeason>,
    nextSeasonIdx: Int,
    nextEpisodeIdx: Int,
    favorites: Map<String, Favorite>,
    onPlay: (TmdbEpisode) -> Unit,
    onFavToggle: (TmdbEpisode) -> Unit,
    viewModel: MainViewModel,
) {
    val backdrop = show.backdropPath?.let { "${AppConfig.TMDB_IMAGE_BASE}/${AppConfig.TMDB_BACKDROP_SIZE}$it" }
    val poster = show.posterPath?.let { "${AppConfig.TMDB_IMAGE_BASE}/${AppConfig.TMDB_POSTER_SIZE}$it" }
    var selectedSeasonIdx by remember { mutableIntStateOf(nextSeasonIdx) }
    val episodeFocus = remember { FocusRequester() }
    // Tracks season changes to trigger scroll-to-focus after layout settles
    var seasonChangeId by remember { mutableIntStateOf(0) }
    val episodeListState = rememberLazyListState()
    // Focus anchor for the season tabs row — DOWN from the episode list routes here
    val seasonTabFocus = remember { FocusRequester() }

    // Speculatively prefetch stream for the first episode of the selected season.
    // Keyed to selectedSeasonIdx so it fires when the user actually switches seasons.
    LaunchedEffect(selectedSeasonIdx, seasons) {
        val firstEp = seasons.getOrNull(selectedSeasonIdx)?.episodes?.firstOrNull()
        firstEp?.let { ep ->
            show.imdbId?.let { imdbId ->
                viewModel.prefetchStream(imdbId, ep.seasonNumber, ep.episodeNumber)
            }
        }
    }

    // Scroll-to-focus: fires only when the user actually switches seasons (seasonChangeId increments).
    // Uses animateScrollToItem to guarantee the scroll is committed before requesting focus,
    // preventing the "cursor frozen on 4th item" bug that occurred when focus was requested
    // before the LazyColumn had scrolled to the target episode.
    LaunchedEffect(seasonChangeId, selectedSeasonIdx, nextEpisodeIdx) {
        val targetEpisodeIdx = if (selectedSeasonIdx == nextSeasonIdx) nextEpisodeIdx else 0
        episodeListState.animateScrollToItem(targetEpisodeIdx)
        // animateScrollToItem is suspending — it completes before we request focus
        withFrameNanos { } // one additional frame for LazyColumn to recompose at new scroll position
        try { episodeFocus.requestFocus() } catch (_: Exception) {}
    }

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
                                .size(Size(140, 210))
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

        // Season tabs — two-pane: LEFT/RIGHT navigate between tabs, DOWN goes to episode list
        if (seasons.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .focusRequester(seasonTabFocus),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                seasons.forEachIndexed { idx, season ->
                    val isSelected = idx == selectedSeasonIdx
                    SeasonTab(
                        label = season.name,
                        isSelected = isSelected,
                        episodeCount = season.episodeCount,
                        onSelect = {
                            if (idx != selectedSeasonIdx) {
                                selectedSeasonIdx = idx
                                seasonChangeId++
                            }
                        }
                    )
                }
            }
        }

        // Episodes list — two-pane: UP from first episode returns to season tabs,
        // DOWN from last episode wraps to first episode
        seasons.getOrNull(selectedSeasonIdx)?.episodes?.let { episodes ->
            val targetEpisodeIdx = if (selectedSeasonIdx == nextSeasonIdx) nextEpisodeIdx else 0
            LaunchedEffect(episodes) {
                // Only run on initial composition (episodes first available).
                // Season-change focus is handled by seasonChangeId effect above.
                if (targetEpisodeIdx == 0) {
                    try { episodeFocus.requestFocus() } catch (_: Exception) {}
                }
            }
            LazyColumn(
                state = episodeListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
                    .focusProperties {
                        // UP from episode list → season tabs
                        up = seasonTabFocus
                        // DOWN from episode list → wraps to first episode
                        down = episodeFocus
                    },
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                itemsIndexed(
                    episodes,
                    key = { _, it -> "${it.seasonNumber}-${it.episodeNumber}" },
                    contentType = { _, _ -> "TmdbEpisode" }
                ) { index, episode ->
                    val isFav = favorites.containsKey("${show.id}:${episode.seasonNumber}:${episode.episodeNumber}")
                    // Focus the calculated next episode on initial load, or the first episode if season changed
                    val shouldFocus = if (selectedSeasonIdx == nextSeasonIdx) index == nextEpisodeIdx else index == 0
                    TmdbEpisodeRow(
                        episode = episode,
                        showId = show.id.toString(),
                        isFavorite = isFav,
                        onPlay = { onPlay(episode) },
                        onFavToggle = { onFavToggle(episode) },
                        onFocus = if (shouldFocus) episodeFocus else null
                    )
                }
            }
        }
    }
}

/**
 * Single season tab composable with proper D-pad focus routing.
 * onSelect is only triggered on click/OK press, not on focus enter,
 * preventing unwanted season switches during D-pad navigation.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeasonTab(
    label: String,
    isSelected: Boolean,
    episodeCount: Int?,
    onSelect: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val seasonTabFocus = remember { FocusRequester() }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            // Request focus on the row anchor so LazyColumn.up routes here
            try { seasonTabFocus.requestFocus() } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .focusRequester(seasonTabFocus)
            .background(
                if (isSelected) AccentBlue.copy(alpha = 0.2f)
                else if (isFocused) RizzoAccentDim
                else Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .then(
                if (isFocused && !isSelected) Modifier.border(1.5.dp, AccentBlue.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                else Modifier
            )
            .rizzoFocusable(
                onClick = onSelect,
                interactionSource = interactionSource,
                shape = RoundedCornerShape(4.dp),
                focusBorderColor = Color.Transparent,
                focusBackgroundColor = Color.Transparent
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                fontSize = 12.sp,
                color = if (isSelected) AccentBlue else if (isFocused) TextPrimary else TextMuted,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
            )
            if (episodeCount != null && episodeCount > 0) {
                Spacer(Modifier.width(4.dp))
                Text(
                    "($episodeCount)",
                    fontSize = 10.sp,
                    color = if (isSelected) AccentBlue.copy(alpha = 0.7f) else TextMuted.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun TmdbEpisodeRow(
    episode: TmdbEpisode,
    showId: String,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onFavToggle: () -> Unit,
    onFocus: FocusRequester?
) {
    val stillUrl = episode.stillPath?.let { "${AppConfig.TMDB_IMAGE_BASE}/w185$it" }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .rizzoFocusable(
                onClick = onPlay,
                interactionSource = interactionSource,
                shape = RoundedCornerShape(6.dp),
                focusBorderColor = AccentBlue,
                focusBackgroundColor = NavFocusBg
            )
            .then(if (onFocus != null) Modifier.focusRequester(onFocus) else Modifier)
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
                        .size(Size(240, 136))
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
            
            // Progress bar
            val playbackStore = (LocalContext.current.applicationContext as com.rizzoplayer.iptv.RizzoApp).playbackPositionStore
            var fraction by remember { mutableFloatStateOf(0f) }
            LaunchedEffect(episode, showId) {
                val key = "tmdb_episode:${showId}:${episode.seasonNumber}:${episode.episodeNumber}"
                val p = playbackStore.getProgress(key)
                fraction = if (p != null && p.durationMs > 0)
                    (p.positionMs.toFloat() / p.durationMs).coerceIn(0f, 1f) else 0f
            }
            
            if (fraction > 0f) {
                com.rizzoplayer.iptv.ui.designsystem.RizzoProgressBar(
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

        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                episode.name.ifEmpty { "Episode ${episode.episodeNumber}" },
                fontSize = 12.sp,
                color = if (isFocused) TextPrimary else TextMuted,
                fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
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
            if (isFocused && episode.overview.isNotEmpty()) {
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
