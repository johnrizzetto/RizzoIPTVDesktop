package com.rizzoplayer.iptv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rizzoplayer.iptv.R
import com.rizzoplayer.iptv.data.model.*
import com.rizzoplayer.iptv.ui.navigation.FocusManager
import com.rizzoplayer.iptv.ui.navigation.Screen
import com.rizzoplayer.iptv.ui.screens.home.*
import com.rizzoplayer.iptv.ui.theme.*
import com.rizzoplayer.iptv.ui.viewmodel.MainViewModel
import com.rizzoplayer.iptv.ui.viewmodel.BrowseContent
import com.rizzoplayer.iptv.ui.viewmodel.Section

// ═══════════════════════════════════════════════════════════════════════════
// ROOT — Sidebar + NavHost
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val state by viewModel.state.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val favoritesList by viewModel.favoritesList.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()

    // Which top-level screen is currently active — drives sidebar highlight
    // Observe NavHost directly so sidebar highlight stays in sync with actual navigation
    val navBackEntry by navController.currentBackStackEntryAsState()
    val currentRoute: String = navBackEntry?.destination?.route ?: Screen.Live.route

    BackHandler {
        when {
            state.canGoBack -> {
                viewModel.goBack()
                return@BackHandler
            }
            else -> {
                // At top-level with nowhere to go back to — absorb the back press silently
            }
        }
    }

    var sidebarExpanded by remember { mutableStateOf(false) }
    val sidebarWidth by animateDpAsState(
        targetValue = if (sidebarExpanded) 180.dp else 52.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "sidebar",
    )

    // Focus requester for the NavHost content area — sidebar pushes focus here on nav
    val contentFocusRestorer = remember { FocusRequester() }
    // Phase 3: Replace tick system (focusContentTick) with FocusManager boolean flag.
    // The tick system had a race: onFocusChanged in outer Box fired on every child focus
    // event, making focus unpredictable. FocusManager fires exactly once per sidebar click.
    LaunchedEffect(Unit) {
        FocusManager.shouldRestoreContentFocus.collect { should ->
            if (should) {
                withFrameNanos { } // one frame for NavHost to mount destination
                try { contentFocusRestorer.requestFocus() } catch (_: Exception) {}
                FocusManager.clearContentFocusRequest()
            }
        }
    }

    // Sidebar initial focus on first launch — zero delay, one frame
    val sidebarInitialFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        sidebarInitialFocus.requestFocus()
    }

    // Navigate + update ViewModel state in one call
    val navigateAndSelectSection: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        val section = when (route) {
            Screen.Live.route      -> Section.LIVE
            Screen.Movies.route   -> Section.VOD
            Screen.Shows.route     -> Section.SERIES
            Screen.Favorites.route -> Section.FAVORITES
            Screen.Settings.route -> null  // Settings has no section state
            else -> null
        }
        section?.let { viewModel.selectSection(it) }
    }

    Row(Modifier.fillMaxSize().background(MainBg)) {

        Sidebar(
            widthDp = sidebarWidth,
            expanded = sidebarExpanded,
            currentRoute = currentRoute,
            onSelectSection = navigateAndSelectSection,
            canGoBack = state.canGoBack,
            onFocusEnter = { sidebarExpanded = true },
            onFocusExit = { sidebarExpanded = false },
            onBack = viewModel::goBack,
            onLogout = viewModel::logout,
            initialSidebarFocus = sidebarInitialFocus,
        )

        // ── NavHost column ──────────────────────────────────────────────────
        Column(Modifier.weight(1f).fillMaxHeight()) {

            // Continue Watching strip
            if (continueWatching.isNotEmpty() && state.searchQuery.isEmpty()) {
                ContinueWatchingStrip(items = continueWatching, onPlay = viewModel::onPlayRecent)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(BorderColor)
                )
            }

            // Search bar
            SearchBar(
                query = state.searchQuery,
                onQueryChange = viewModel::setSearchQuery,
                onClear = { viewModel.setSearchQuery("") },
                onVoiceResult = { viewModel.setSearchQuery(it) },
                downTarget = contentFocusRestorer
            )

            // EPG — expanded strip for live TV
            state.epgInfo?.let { epg ->
                if (epg.nowTitle.isNotEmpty() || epg.nextTitle.isNotEmpty()) {
                    EpgStrip(epg = epg)
                }
            }

            // NavHost content — focusGroup routes requestFocus() to nearest child instead
            // of absorbing it directly.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .focusRequester(contentFocusRestorer)
                    .focusGroup()
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Live.route,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(Screen.Live.route) {
                        LiveContent(viewModel, state, favorites, favoritesList)
                    }
                    composable(Screen.Movies.route) {
                        MoviesContent(viewModel, state, favorites, favoritesList, continueWatching)
                    }
                    composable(Screen.Shows.route) {
                        ShowsContent(viewModel, state, favorites, favoritesList, continueWatching)
                    }
                    composable(Screen.Favorites.route) {
                        FavoritesContent(viewModel, favoritesList)
                    }
                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            preferencesStore = viewModel.preferencesStore,
                            onClearCache = {
                                viewModel.repository.clearCache()
                            },
                            onLogout = viewModel::logout,
                            onBack = viewModel::goBack,
                        )
                    }
                }
            }
        }

        // Overlays
        val toastMessage = state.toastMessage
        LaunchedEffect(toastMessage) {
            if (toastMessage != null) {
                kotlinx.coroutines.delay(3_000)
                viewModel.clearToast()
            }
        }
        if (toastMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 48.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    text = toastMessage,
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .background(Color(0xFF1A0808), RoundedCornerShape(8.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }

        state.tmdbStreamSelection?.let { selection ->
            PremiumStreamSelectionOverlay(
                title = selection.title,
                streams = selection.streams,
                failedStreamUrl = selection.failedStreamUrl,
                failedReason = selection.failedReason,
                onSelect = { stream -> viewModel.playSelectedTmdbStreamCommon(stream, selection.contentType) },
                onDismiss = viewModel::dismissTmdbStreamSelection,
            )
        }

        state.playbackPrep?.let { prep ->
            PlaybackPrepOverlay(
                prep = prep,
                onCancel = viewModel::cancelPlaybackPrep,
                onRetry = viewModel::retryPlaybackPrep,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// CONTENT COMPOSABLES (one per nav destination)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun LiveContent(
    viewModel: MainViewModel,
    state: com.rizzoplayer.iptv.ui.viewmodel.MainUiState,
    favorites: Map<String, Favorite>,
    favoritesList: List<Favorite>
) {
    val q = state.searchQuery.trim().lowercase()

    when {
        state.isLoading -> LoadingView()
        state.error != null -> ErrorView(
            message = state.error,
            onDismiss = viewModel::retryCurrent,
            onReload = viewModel::retryReload
        )
        else -> when (val content = state.content) {
            is BrowseContent.Categories -> {
                val filtered = remember(content, q) {
                    if (q.isEmpty()) content.items
                    else content.items.filter { it.name.lowercase().contains(q) }
                }
                LiveCategoriesContent(
                    items = filtered,
                    onSelect = { viewModel.selectCategory(it, Section.LIVE, 0) },
                    onPlay = viewModel::onPlayLive,
                    onFavToggle = { s -> viewModel.toggleFavorite(s.id.toString(), s.name, "live", icon = s.icon) }
                )
            }
            is BrowseContent.LiveStreams -> {
                val filtered = remember(content, q) {
                    if (q.isEmpty()) content.items
                    else content.items.filter { it.name.lowercase().contains(q) }
                }
                LiveStreamsContent(
                    items = filtered,
                    categoryName = content.categoryName,
                    favorites = favorites,
                    onPlay = viewModel::onPlayLive,
                    onFavToggle = { s -> viewModel.toggleFavorite(s.id.toString(), s.name, "live", icon = s.icon) }
                )
            }
            else -> EmptyHint("Select a category")
        }
    }
}

@Composable
private fun MoviesContent(
    viewModel: MainViewModel,
    state: com.rizzoplayer.iptv.ui.viewmodel.MainUiState,
    favorites: Map<String, Favorite>,
    favoritesList: List<Favorite>,
    continueWatching: List<RecentItem>
) {
    val q = state.searchQuery.trim().lowercase()

    when {
        state.isLoading -> LoadingView()
        state.error != null -> ErrorView(
            message = state.error,
            onDismiss = viewModel::retryCurrent,
            onReload = viewModel::retryReload
        )
        q.isNotEmpty() -> {
            when (val content = state.content) {
                is BrowseContent.StreamPicker -> StreamPickerScreen(
                    state = content,
                    onSelectStream = { stream, idx -> viewModel.selectStreamInPicker(stream, idx) },
                    onBack = viewModel::goBack,
                )
                is BrowseContent.TmdbMovieDetail -> TmdbMovieDetailView(
                    movie = content.movie,
                    isFavorite = favorites.containsKey(content.movie.id.toString()),
                    onPlay = { viewModel.onPlayTmdbMovie(content.movie) },
                    onToggleFavorite = {
                        viewModel.toggleFavorite(
                            id   = content.movie.id.toString(),
                            name = content.movie.title,
                            type = "tmdb_movie",
                            icon = content.movie.posterPath?.let { "${com.rizzoplayer.iptv.AppConfig.TMDB_IMAGE_BASE}/${com.rizzoplayer.iptv.AppConfig.TMDB_POSTER_SIZE}$it" }
                        )
                    },
                    viewModel = viewModel
                )
                is BrowseContent.TmdbSearchResults -> {
                    if (content.movies.isNotEmpty()) {
                        TmdbSearchResultsContent(
                            content = content,
                            favorites = favorites,
                            viewModel = viewModel,
                            filter = "movies",
                            isLoading = state.isSearchLoading
                        )
                    } else if (state.isSearchLoading) {
                        LoadingView()
                    } else {
                        EmptyHint("No movies found")
                    }
                }
                else -> {
                    if (state.isSearchLoading) LoadingView()
                    else EmptyHint("Type at least 2 characters")
                }
            }
        }
        else -> when (val content = state.content) {
            is BrowseContent.Categories -> {
                val filtered = remember(content, q) {
                    if (q.isEmpty()) content.items
                    else content.items.filter { it.name.lowercase().contains(q) }
                }
                TmdbCategoriesContent(
                    items = filtered,
                    onSelect = { viewModel.selectCategory(it, Section.VOD, 0) }
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
                            icon = movie.posterPath?.let { "${com.rizzoplayer.iptv.AppConfig.TMDB_IMAGE_BASE}/${com.rizzoplayer.iptv.AppConfig.TMDB_POSTER_SIZE}$it" }
                        )
                    },
                    onPlayRecent = viewModel::onPlayRecent,
                    isGridLoading = state.isGridLoading,
                    initialScrollIndex = state.restoreGridScrollIndex,
                    onScrollRestored = viewModel::clearGridScrollRestore,
                    onScrollPositionChange = { viewModel.updateGridScroll(it) },
                    onBack = viewModel::goBack,
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
                            icon = content.movie.posterPath?.let { "${com.rizzoplayer.iptv.AppConfig.TMDB_IMAGE_BASE}/${com.rizzoplayer.iptv.AppConfig.TMDB_POSTER_SIZE}$it" }
                        )
                    },
                    viewModel = viewModel
                )
            }
            is BrowseContent.StreamPicker -> {
                StreamPickerScreen(
                    state = content,
                    onSelectStream = { stream, idx -> viewModel.selectStreamInPicker(stream, idx) },
                    onBack = viewModel::goBack,
                )
            }
            else -> EmptyHint("Select a category")
        }
    }
}

@Composable
private fun ShowsContent(
    viewModel: MainViewModel,
    state: com.rizzoplayer.iptv.ui.viewmodel.MainUiState,
    favorites: Map<String, Favorite>,
    favoritesList: List<Favorite>,
    continueWatching: List<RecentItem>
) {
    val q = state.searchQuery.trim().lowercase()

    when {
        state.isLoading -> LoadingView()
        state.error != null -> ErrorView(
            message = state.error,
            onDismiss = viewModel::retryCurrent,
            onReload = viewModel::retryReload
        )
        q.isNotEmpty() -> {
            val detail = state.content as? BrowseContent.TmdbShowDetail
            if (detail != null) {
                TmdbShowDetailView(
                    show = detail.show,
                    seasons = detail.seasons,
                    favorites = favorites,
                    onPlay = { episode -> viewModel.onPlayTmdbEpisode(detail.show, episode) },
                    onFavToggle = { episode ->
                        viewModel.toggleFavorite(
                            id = "${detail.show.id}:${episode.seasonNumber}:${episode.episodeNumber}",
                            name = episode.name.ifEmpty { "Episode ${episode.episodeNumber}" },
                            type = "tmdb_episode",
                            icon = episode.stillPath?.let { "${com.rizzoplayer.iptv.AppConfig.TMDB_IMAGE_BASE}/${com.rizzoplayer.iptv.AppConfig.TMDB_POSTER_SIZE}$it" }
                        )
                    },
                    viewModel = viewModel
                )
            } else {
                val searchContent = state.content as? BrowseContent.TmdbSearchResults
                if (searchContent != null && searchContent.query.equals(q, ignoreCase = true)) {
                    if (searchContent.shows.isNotEmpty()) {
                        TmdbSearchResultsContent(
                            content = searchContent,
                            favorites = favorites,
                            viewModel = viewModel,
                            filter = "shows",
                            isLoading = state.isSearchLoading
                        )
                    } else if (state.isSearchLoading) {
                        LoadingView()
                    } else {
                        EmptyHint("No shows found")
                    }
                } else if (state.isSearchLoading) {
                    LoadingView()
                } else {
                    EmptyHint("Type at least 2 characters")
                }
            }
        }
        else -> when (val content = state.content) {
            is BrowseContent.Categories -> {
                val filtered = remember(content, q) {
                    if (q.isEmpty()) content.items
                    else content.items.filter { it.name.lowercase().contains(q) }
                }
                TmdbCategoriesContent(
                    items = filtered,
                    onSelect = { viewModel.selectCategory(it, Section.SERIES, 0) }
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
                            icon = show.posterPath?.let { "${com.rizzoplayer.iptv.AppConfig.TMDB_IMAGE_BASE}/${com.rizzoplayer.iptv.AppConfig.TMDB_POSTER_SIZE}$it" }
                        )
                    },
                    onPlayRecent = viewModel::onPlayRecent,
                    isGridLoading = state.isGridLoading,
                    initialScrollIndex = state.restoreGridScrollIndex,
                    onScrollRestored = viewModel::clearGridScrollRestore,
                    onScrollPositionChange = { viewModel.updateGridScroll(it) },
                    onBack = viewModel::goBack,
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
                            icon = episode.stillPath?.let { "${com.rizzoplayer.iptv.AppConfig.TMDB_IMAGE_BASE}/${com.rizzoplayer.iptv.AppConfig.TMDB_POSTER_SIZE}$it" }
                        )
                    },
                    viewModel = viewModel
                )
            }
            else -> EmptyHint("Select a category")
        }
    }
}

@Composable
private fun FavoritesContent(
    viewModel: MainViewModel,
    favoritesList: List<Favorite>
) {
    FavoritesView(
        favorites = favoritesList,
        onPlay = viewModel::onPlayFavorite,
        onRemove = { fav -> viewModel.toggleFavorite(fav.id, fav.name, fav.type, fav.ext) },
        onMove = { fav, dir -> viewModel.moveFavorite(fav.id, dir) }
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// TMDB CATEGORIES SHARED CONTENT
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun TmdbCategoriesContent(
    items: List<Category>,
    onSelect: (Category) -> Unit
) {
    val listState = rememberLazyListState()
    val firstItemFocusRequester = remember { FocusRequester() }
    // First *selectable* item — skip non-focusable header rows (id starts with "H:")
    val firstSelectableId = remember(items) { items.firstOrNull { !it.id.startsWith("H:") }?.id }

    // Single effect: focus first item when content first mounts (nav sidebar click).
    // No tick/toggle needed — each NavHost destination gets its own fresh composable instance.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        try { firstItemFocusRequester.requestFocus() } catch (_: Exception) {}
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        items(items, key = { it.id }, contentType = { if (it.id.startsWith("H:")) "Header" else "Category" }) { cat ->
            val isFirstSelectable = cat.id == firstSelectableId
            if (cat.id.startsWith("H:")) {
                CategoryDivider(label = cat.name)
            } else {
                CategoryRow(
                    category = cat,
                    onSelect = { onSelect(cat) },
                    modifier = if (isFirstSelectable) Modifier.focusRequester(firstItemFocusRequester) else Modifier
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// LIVE STREAMS (categories + channel list)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun LiveCategoriesContent(
    items: List<Category>,
    onSelect: (Category) -> Unit,
    onPlay: (LiveStream) -> Unit,
    onFavToggle: (LiveStream) -> Unit
) {
    val listState = rememberLazyListState()
    val firstItemFocusRequester = remember { FocusRequester() }
    // Single effect: focus first item when content first mounts.
    LaunchedEffect(Unit) {
        withFrameNanos { }
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
private fun LiveStreamsContent(
    items: List<LiveStream>,
    categoryName: String,
    favorites: Map<String, Favorite>,
    onPlay: (LiveStream) -> Unit,
    onFavToggle: (LiveStream) -> Unit
) {
    val listState = rememberLazyListState()
    val firstFocus = remember { FocusRequester() }
    // Single effect: focus first item when content first mounts.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        try { firstFocus.requestFocus() } catch (_: Exception) {}
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        // Category header
        if (categoryName.isNotEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = categoryName,
                        fontSize = 12.sp,
                        color = AccentBlue,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
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

// ═══════════════════════════════════════════════════════════════════════════
// OVERLAYS & DIALOGS
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun LoadingView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AccentBlue, strokeWidth = 2.dp)
    }
}

@Composable
fun ErrorView(message: String, onDismiss: () -> Unit, onReload: () -> Unit) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { retryFocus.requestFocus() }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("⚠", fontSize = 40.sp, color = RedColor)
        Spacer(Modifier.height(12.dp))
        Text(message, fontSize = 14.sp, color = TextMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Dismiss",
                fontSize = 13.sp,
                color = TextMuted,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).focusable().clickable(onClick = onDismiss).padding(8.dp)
            )
            Text(
                "Retry",
                fontSize = 13.sp,
                color = AccentBlue,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .focusRequester(retryFocus)
                    .focusable()
                    .clickable(onClick = onReload)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
fun PlaybackPrepOverlay(
    prep: com.rizzoplayer.iptv.ui.viewmodel.PlaybackPrep,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val cancelFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }

    val isFailed = prep.stage == com.rizzoplayer.iptv.ui.viewmodel.PlaybackPrep.Stage.FAILED

    LaunchedEffect(isFailed) {
        if (isFailed) {
            kotlinx.coroutines.delay(100)
            retryFocus.requestFocus()
        } else {
            cancelFocus.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0C0F1A))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isFailed) {
                // Failure icon
                Text("✕", fontSize = 36.sp, color = RedColor)
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = AccentBlue,
                    strokeWidth = 3.dp
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                prep.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                prep.message,
                color = TextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            val stageLabel = when (prep.stage) {
                com.rizzoplayer.iptv.ui.viewmodel.PlaybackPrep.Stage.SEARCHING -> "Searching for streams…"
                com.rizzoplayer.iptv.ui.viewmodel.PlaybackPrep.Stage.QUEUING -> "Adding to TorBox…"
                com.rizzoplayer.iptv.ui.viewmodel.PlaybackPrep.Stage.CACHING -> "Caching torrent…"
                com.rizzoplayer.iptv.ui.viewmodel.PlaybackPrep.Stage.READY -> "Ready!"
                com.rizzoplayer.iptv.ui.viewmodel.PlaybackPrep.Stage.FAILED -> "Failed"
            }
            Text(
                stageLabel,
                color = when (prep.stage) {
                    com.rizzoplayer.iptv.ui.viewmodel.PlaybackPrep.Stage.FAILED -> RedColor
                    com.rizzoplayer.iptv.ui.viewmodel.PlaybackPrep.Stage.READY -> Color(0xFF4DFF4D)
                    else -> AccentBlue
                },
                fontSize = 11.sp
            )
            Spacer(Modifier.height(24.dp))

            if (isFailed) {
                // Retry + Cancel row
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    var retryFocused by remember { mutableStateOf(false) }
                    Text(
                        "Retry",
                        color = if (retryFocused) GreenSeeders else TextMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .focusRequester(retryFocus)
                            .onFocusChanged { retryFocused = it.isFocused }
                            .focusable()
                            .clickable(onClick = onRetry)
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                    var cancelFocused by remember { mutableStateOf(false) }
                    Text(
                        "Cancel",
                        color = if (cancelFocused) AccentBlue else TextMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .focusRequester(cancelFocus)
                            .onFocusChanged { cancelFocused = it.isFocused }
                            .focusable()
                            .clickable(onClick = onCancel)
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            } else {
                // Cancel button only appears after the 5s grace period (canCancel = true)
                if (prep.canCancel) {
                    var cancelFocused by remember { mutableStateOf(false) }
                    Text(
                        "Cancel",
                        color = if (cancelFocused) AccentBlue else TextMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .focusRequester(cancelFocus)
                            .onFocusChanged { cancelFocused = it.isFocused }
                            .focusable()
                            .clickable(onClick = onCancel)
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
