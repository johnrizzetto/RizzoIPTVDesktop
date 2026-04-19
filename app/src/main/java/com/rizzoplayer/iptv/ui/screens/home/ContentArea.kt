package com.rizzoplayer.iptv.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.Coil
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.rizzoplayer.iptv.AppConfig
import com.rizzoplayer.iptv.R
import com.rizzoplayer.iptv.data.model.*
import com.rizzoplayer.iptv.ui.theme.*
import com.rizzoplayer.iptv.ui.viewmodel.BrowseContent
import com.rizzoplayer.iptv.ui.viewmodel.MainViewModel
import com.rizzoplayer.iptv.ui.screens.home.MoviesHome
import com.rizzoplayer.iptv.ui.screens.home.SeriesHome

@Composable
fun ContentArea(
    content: BrowseContent,
    searchQuery: String,
    favorites: Map<String, Favorite>,
    favoritesList: List<Favorite>,
    viewModel: MainViewModel
) {
    val q = searchQuery.trim().lowercase()
    val listState = rememberLazyListState()
    val restoreIndex = viewModel.state.collectAsState().value.restoreScrollIndex
    val continueWatching by viewModel.continueWatching.collectAsState()

    LaunchedEffect(restoreIndex) {
        if (restoreIndex >= 0) {
            listState.scrollToItem(restoreIndex)
            viewModel.clearScrollRestore()
        }
    }

    LaunchedEffect(content) {
        if (content is BrowseContent.Categories && content.items.size == 1) {
            viewModel.selectCategory(content.items.first(), content.mode, 0)
        }
    }

    when (content) {
        is BrowseContent.Empty -> EmptyHint()

        is BrowseContent.Categories -> {
            val filtered = remember(content, q) {
                if (q.isEmpty()) content.items
                else content.items.filter { it.name.lowercase().contains(q) }
            }
            CategoryList(
                items = filtered,
                listState = listState,
                onSelect = { viewModel.selectCategory(it, content.mode, listState.firstVisibleItemIndex) }
            )
        }

        is BrowseContent.LiveStreams -> {
            val filtered = remember(content, q) {
                if (q.isEmpty()) content.items
                else content.items.filter { it.name.lowercase().contains(q) }
            }
            ChannelList(
                items = filtered,
                favorites = favorites,
                listState = listState,
                onPlay = viewModel::onPlayLive,
                onFavToggle = { s ->
                    viewModel.toggleFavorite(s.id.toString(), s.name, "live", icon = s.icon)
                }
            )
        }

        is BrowseContent.VodStreams -> {}
        is BrowseContent.SeriesList -> {}
        is BrowseContent.Episodes -> {}

        is BrowseContent.Favorites -> {
            FavoritesView(
                favorites = favoritesList,
                onPlay = viewModel::onPlayFavorite,
                onRemove = { fav -> viewModel.toggleFavorite(fav.id, fav.name, fav.type, fav.ext) },
                onMove = { fav, dir -> viewModel.moveFavorite(fav.id, dir) }
            )
        }

        is BrowseContent.TmdbMovies -> {
            MoviesHome(
                content = content,
                favorites = favorites,
                continueWatchingItems = continueWatching,
                onSelectMovie = viewModel::selectTmdbMovie,
                onToggleFavorite = { movie ->
                    viewModel.toggleFavorite(
                        id   = movie.id.toString(),
                        name = movie.title,
                        type = "tmdb_movie",
                        icon = movie.posterPath?.let { "${AppConfig.TMDB_IMAGE_BASE}/${AppConfig.TMDB_POSTER_SIZE}$it" }
                    )
                },
                onPlayRecent = viewModel::onPlayRecent
            )
        }

        is BrowseContent.TmdbMovieDetail -> {
            TmdbMovieDetailView(
                movie = content.movie,
                isFavorite = favorites.containsKey(content.movie.id.toString()),
                onPlay = { viewModel.onPlayTmdbMovie(content.movie) },
                onToggleFavorite = {
                    viewModel.toggleFavorite(
                        id   = content.movie.id.toString(),
                        name = content.movie.title,
                        type = "tmdb_movie",
                        icon = content.movie.posterPath?.let { "${AppConfig.TMDB_IMAGE_BASE}/${AppConfig.TMDB_POSTER_SIZE}$it" }
                    )
                }
            )
        }

        is BrowseContent.TmdbShows -> {
            SeriesHome(
                content = content,
                favorites = favorites,
                continueWatchingItems = continueWatching,
                onSelectShow = viewModel::selectTmdbShow,
                onToggleFavorite = { show ->
                    viewModel.toggleFavorite(
                        id   = show.id.toString(),
                        name = show.name,
                        type = "tmdb_show",
                        icon = show.posterPath?.let { "${AppConfig.TMDB_IMAGE_BASE}/${AppConfig.TMDB_POSTER_SIZE}$it" }
                    )
                },
                onPlayRecent = viewModel::onPlayRecent
            )
        }

        is BrowseContent.TmdbSearchResults -> {
            TmdbSearchResultsView(
                content = content,
                favorites = favorites,
                onSelectMovie = viewModel::selectTmdbMovie,
                onSelectShow = viewModel::selectTmdbShow,
                onToggleMovieFavorite = { movie ->
                    viewModel.toggleFavorite(
                        id   = movie.id.toString(),
                        name = movie.title,
                        type = "tmdb_movie",
                        icon = movie.posterPath?.let { "${AppConfig.TMDB_IMAGE_BASE}/${AppConfig.TMDB_POSTER_SIZE}$it" }
                    )
                },
                onToggleShowFavorite = { show ->
                    viewModel.toggleFavorite(
                        id   = show.id.toString(),
                        name = show.name,
                        type = "tmdb_show",
                        icon = show.posterPath?.let { "${AppConfig.TMDB_IMAGE_BASE}/${AppConfig.TMDB_POSTER_SIZE}$it" }
                    )
                }
            )
        }

        is BrowseContent.TmdbShowDetail -> {
            TmdbShowDetailView(
                show = content.show,
                seasons = content.seasons,
                favorites = favorites,
                onPlay = { episode -> viewModel.onPlayTmdbEpisode(content.show, episode) },
                onFavToggle = { episode ->
                    viewModel.toggleFavorite(
                        id = "${content.show.id}:${episode.seasonNumber}:${episode.episodeNumber}",
                        name = episode.name.ifEmpty { "Episode ${episode.episodeNumber}" },
                        type = "tmdb_episode",
                        icon = episode.stillPath?.let { "${AppConfig.TMDB_IMAGE_BASE}/${AppConfig.TMDB_POSTER_SIZE}$it" }
                    )
                }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// CATEGORY LIST
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun CategoryList(
    items: List<Category>,
    listState: LazyListState,
    onSelect: (Category) -> Unit
) {
    val firstItemFocusRequester = remember { FocusRequester() }
    LaunchedEffect(items) {
        try { firstItemFocusRequester.requestFocus() } catch (_: Exception) {}
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        items(items, key = { it.id }, contentType = { "Category" }) { cat ->
            val isFirst = items.firstOrNull()?.id == cat.id
            CategoryRow(
                category = cat,
                onSelect = { onSelect(cat) },
                modifier = if (isFirst) Modifier.focusRequester(firstItemFocusRequester) else Modifier
            )
        }
    }
}

@Composable
private fun CategoryRow(category: Category, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) NavFocusBg else Color.Transparent)
            .then(if (focused) Modifier.border(1.dp, AccentBlue.copy(alpha = 0.4f), RoundedCornerShape(6.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("▣", fontSize = 18.sp, color = if (focused) AccentBlue else TextMuted)
        Spacer(Modifier.width(12.dp))
        Text(
            category.name,
            fontSize = 14.sp,
            color = if (focused) TextPrimary else TextMuted,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal
        )
        Spacer(Modifier.weight(1f))
        Text(
            "",
            fontSize = 12.sp,
            color = TextMuted
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// CHANNEL LIST
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ChannelList(
    items: List<LiveStream>,
    favorites: Map<String, Favorite>,
    listState: LazyListState,
    onPlay: (LiveStream) -> Unit,
    onFavToggle: (LiveStream) -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(items) { try { firstFocus.requestFocus() } catch (_: Exception) {} }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        items(items, key = { it.id }, contentType = { "LiveStream" }) { stream ->
            val isFav = favorites.containsKey(stream.id.toString())
            val isFirst = items.firstOrNull()?.id == stream.id
            ChannelRow(
                stream = stream,
                isFavorite = isFav,
                onPlay = { onPlay(stream) },
                onFavToggle = { onFavToggle(stream) },
                modifier = if (isFirst) Modifier.focusRequester(firstFocus) else Modifier
            )
        }
    }
}

@Composable
private fun ChannelRow(
    stream: LiveStream,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onFavToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) NavFocusBg else Color.Transparent)
            .then(if (focused) Modifier.border(1.dp, AccentBlue.copy(alpha = 0.4f), RoundedCornerShape(6.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onPlay)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelLogo(stream.icon, stream.name, 36)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(stream.name, fontSize = 13.sp, color = if (focused) TextPrimary else TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            if (isFavorite) "♥" else "♡",
            fontSize = 14.sp,
            color = if (isFavorite) RedColor else TextMuted,
            modifier = Modifier.padding(horizontal = 6.dp).clickable(onClick = onFavToggle)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// CONTENT GRID
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ContentGrid(
    items: List<VodStream>,
    favorites: Map<String, Favorite>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onPlay: (VodStream) -> Unit,
    onFavToggle: (VodStream) -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(items) { try { firstFocus.requestFocus() } catch (_: Exception) {} }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(items, key = { it.id }, contentType = { "VodStream" }) { vod ->
            val isFav = favorites.containsKey(vod.id.toString())
            val isFirst = items.firstOrNull()?.id == vod.id
            PosterCard(
                title = vod.name,
                icon = vod.icon,
                rating = null,
                year = null,
                isFavorite = isFav,
                onPlay = { onPlay(vod) },
                onFavToggle = { onFavToggle(vod) },
                modifier = if (isFirst) Modifier.focusRequester(firstFocus) else Modifier
            )
        }
    }
}

@Composable
private fun SeriesGrid(
    items: List<Series>,
    favorites: Map<String, Favorite>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onPlay: (Series) -> Unit,
    onFavToggle: (Series) -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(items) { try { firstFocus.requestFocus() } catch (_: Exception) {} }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(items, key = { it.id }, contentType = { "Series" }) { series ->
            val isFav = favorites.containsKey(series.id.toString())
            val isFirst = items.firstOrNull()?.id == series.id
            PosterCard(
                title = series.name,
                icon = series.cover,
                rating = null,
                year = null,
                isFavorite = isFav,
                onPlay = { onPlay(series) },
                onFavToggle = { onFavToggle(series) },
                modifier = if (isFirst) Modifier.focusRequester(firstFocus) else Modifier
            )
        }
    }
}

@Composable
private fun PosterCard(
    title: String,
    icon: String?,
    rating: Float?,
    year: String?,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onFavToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) CardFocused else CardBg)
            .then(if (focused) Modifier.border(1.dp, AccentBlue.copy(alpha = 0.5f), RoundedCornerShape(6.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onPlay)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelLogo(icon, title, 48)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, color = if (focused) TextPrimary else TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (year != null) Text(year, fontSize = 10.sp, color = TextMuted.copy(alpha = 0.7f))
        }
        if (rating != null) {
            Text("★ %.1f".format(rating), fontSize = 11.sp, color = BrandGold)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            if (isFavorite) "♥" else "♡",
            fontSize = 14.sp,
            color = if (isFavorite) RedColor else TextMuted,
            modifier = Modifier.padding(horizontal = 4.dp).clickable(onClick = onFavToggle)
        )
    }
}

@Composable
private fun PosterFallback(title: String) {
    Box(
        modifier = Modifier
            .size(120.dp, 180.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CardBg),
        contentAlignment = Alignment.Center
    ) {
        Text(title.take(2).uppercase(), color = TextMuted, fontSize = 24.sp)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// EPISODES VIEW
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun EpisodesView(
    episodes: List<Episode>,
    favorites: Map<String, Favorite>,
    onPlay: (Episode) -> Unit,
    onFavToggle: (Episode) -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(episodes) { try { firstFocus.requestFocus() } catch (_: Exception) {} }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(episodes, key = { it.id }, contentType = { "Episode" }) { ep ->
            val isFav = favorites.containsKey(ep.id.toString())
            EpisodeRow(
                episode = ep,
                isFavorite = isFav,
                onPlay = { onPlay(ep) },
                onFavToggle = { onFavToggle(ep) },
                modifier = if (episodes.indexOf(ep) == 0) Modifier.focusRequester(firstFocus) else Modifier
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onFavToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) NavFocusBg else Color.Transparent)
            .then(if (focused) Modifier.border(1.dp, AccentBlue.copy(alpha = 0.4f), RoundedCornerShape(6.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onPlay)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(36.dp)) {
            Text("E${episode.episodeNum}", fontSize = 12.sp, color = if (focused) AccentBlue else TextMuted, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(episode.title ?: "Episode ${episode.episodeNum}", fontSize = 12.sp, color = if (focused) TextPrimary else TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            if (isFavorite) "♥" else "♡",
            fontSize = 14.sp,
            color = if (isFavorite) RedColor else TextMuted,
            modifier = Modifier.padding(horizontal = 4.dp).clickable(onClick = onFavToggle)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// EMPTY / LOADING / ERROR
// ═══════════════════════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════════════════════
// TMDB SEARCH RESULTS
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun TmdbSearchResultsView(
    content: BrowseContent.TmdbSearchResults,
    favorites: Map<String, Favorite>,
    onSelectMovie: (TmdbMovie) -> Unit,
    onSelectShow: (TmdbShow) -> Unit,
    onToggleMovieFavorite: (TmdbMovie) -> Unit,
    onToggleShowFavorite: (TmdbShow) -> Unit
) {
    val listState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Results for \"${content.query}\"",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            if (content.movies.isNotEmpty()) {
                item(contentType = "MoviesSection") {
                    SearchMovieRow(
                        movies = content.movies,
                        favorites = favorites,
                        onSelect = onSelectMovie,
                        onToggleFavorite = onToggleMovieFavorite
                    )
                }
            }

            if (content.shows.isNotEmpty()) {
                item(contentType = "ShowsSection") {
                    SearchShowRow(
                        shows = content.shows,
                        favorites = favorites,
                        onSelect = onSelectShow,
                        onToggleFavorite = onToggleShowFavorite
                    )
                }
            }

            if (content.movies.isEmpty() && content.shows.isEmpty()) {
                item(contentType = "EmptySearch") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No results found", fontSize = 14.sp, color = TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchMovieRow(
    movies: List<TmdbMovie>,
    favorites: Map<String, Favorite>,
    onSelect: (TmdbMovie) -> Unit,
    onToggleFavorite: (TmdbMovie) -> Unit
) {
    val firstFocus = remember { FocusRequester() }

    Column {
        Text(
            "Movies",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextMuted,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
        Box(modifier = Modifier.fillMaxWidth().focusGroup()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(movies.size) { idx ->
                    val movie = movies[idx]
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
                        onClick = { onSelect(movie) },
                        onLongClick = { onToggleFavorite(movie) },
                        modifier = if (idx == 0) Modifier.focusRequester(firstFocus) else Modifier
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchShowRow(
    shows: List<TmdbShow>,
    favorites: Map<String, Favorite>,
    onSelect: (TmdbShow) -> Unit,
    onToggleFavorite: (TmdbShow) -> Unit
) {
    val firstFocus = remember { FocusRequester() }

    Column {
        Text(
            "TV Shows",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextMuted,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
        Box(modifier = Modifier.fillMaxWidth().focusGroup()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shows.size) { idx ->
                    val show = shows[idx]
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
                        onClick = { onSelect(show) },
                        onLongClick = { onToggleFavorite(show) },
                        modifier = if (idx == 0) Modifier.focusRequester(firstFocus) else Modifier
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyHint(message: String = "Select a category") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, fontSize = 14.sp, color = TextMuted)
    }
}
