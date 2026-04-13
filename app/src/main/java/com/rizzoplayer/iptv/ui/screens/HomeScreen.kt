package com.rizzoplayer.iptv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rizzoplayer.iptv.R
import com.rizzoplayer.iptv.RizzoApp
import com.rizzoplayer.iptv.data.local.PlaybackPositionStore
import com.rizzoplayer.iptv.data.model.*
import com.rizzoplayer.iptv.ui.theme.*
import com.rizzoplayer.iptv.ui.viewmodel.*

// ═══════════════════════════════════════════════════════════════════════════
// ROOT
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val state           by viewModel.state.collectAsState()
    val favorites       by viewModel.favorites.collectAsState()
    val recentlyWatched by viewModel.recentlyWatched.collectAsState()

    BackHandler(enabled = state.canGoBack) { viewModel.goBack() }

    var sidebarExpanded by remember { mutableStateOf(false) }
    val sidebarWidth by animateDpAsState(
        targetValue = if (sidebarExpanded) 180.dp else 52.dp,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "sidebar"
    )

    Row(Modifier.fillMaxSize().background(MainBg)) {

        Sidebar(
            widthDp = sidebarWidth,
            expanded = sidebarExpanded,
            currentSection = state.section,
            canGoBack = state.canGoBack,
            onFocusEnter = { sidebarExpanded = true },
            onFocusExit  = { sidebarExpanded = false },
            onSelect = { viewModel.selectSection(it) },
            onBack = viewModel::goBack,
            onLogout = viewModel::logout
        )

        Column(Modifier.weight(1f).fillMaxHeight()) {

            // Recently Watched strip
            if (recentlyWatched.isNotEmpty() && state.searchQuery.isEmpty()) {
                RecentlyWatchedStrip(items = recentlyWatched, onPlay = viewModel::onPlayRecent)
                // Thin separator line between recently-watched strip and content below
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
                onClear = { viewModel.setSearchQuery("") }
            )

            // EPG — minimal one-liner for live TV
            val content = state.content
            state.epgInfo?.let { epg ->
                if (epg.nowTitle.isNotEmpty()) {
                    Text(
                        "  ▶ ${epg.nowTitle}",
                        fontSize = 11.sp,
                        color = AccentBlue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            // Content
            Box(Modifier.weight(1f)) {
                when {
                    state.isLoading     -> LoadingView()
                    state.error != null -> ErrorView(state.error!!, onRetry = viewModel::retry)
                    else -> ContentArea(
                        content     = content,
                        searchQuery = state.searchQuery,
                        favorites   = favorites,
                        viewModel   = viewModel
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SIDEBAR — minimal, icons-first
// ═══════════════════════════════════════════════════════════════════════════

private data class NavEntry(val icon: String, val label: String, val section: Section)
private val NAV_ENTRIES = listOf(
    NavEntry("▶", "Live",      Section.LIVE),
    NavEntry("▣", "Movies",    Section.VOD),
    NavEntry("≡", "Shows",     Section.SERIES),
    NavEntry("♥", "Favorites", Section.FAVORITES)
)

@Composable
private fun Sidebar(
    widthDp: Dp,
    expanded: Boolean,
    currentSection: Section,
    canGoBack: Boolean,
    onFocusEnter: () -> Unit,
    onFocusExit: () -> Unit,
    onSelect: (Section) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(widthDp)
            .fillMaxHeight()
            .background(SidebarBg)
            .onFocusChanged { if (it.hasFocus) onFocusEnter() else onFocusExit() }
            .padding(vertical = 16.dp, horizontal = 4.dp),
        horizontalAlignment = if (expanded) Alignment.Start else Alignment.CenterHorizontally
    ) {
        // Logo
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = null,
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .then(if (!expanded) Modifier else Modifier.padding(start = 6.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(Modifier.height(16.dp))

        NAV_ENTRIES.forEach { entry ->
            SidebarNavItem(
                icon = entry.icon,
                label = entry.label,
                active = currentSection == entry.section,
                expanded = expanded,
                onClick = { onSelect(entry.section) }
            )
            Spacer(Modifier.height(2.dp))
        }

        if (canGoBack) {
            Spacer(Modifier.height(6.dp))
            SidebarNavItem(icon = "←", label = "Back", active = false, expanded = expanded, onClick = onBack)
        }

        Spacer(Modifier.weight(1f))

        SidebarNavItem(icon = "⇤", label = "Sign Out", active = false, expanded = expanded, onClick = onLogout)
    }
}

@Composable
private fun SidebarNavItem(
    icon: String,
    label: String,
    active: Boolean,
    expanded: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        active  -> NavActive
        focused -> NavHover
        else    -> Color.Transparent
    }
    val iconColor = when {
        active  -> AccentBlue
        focused -> AccentBlue
        else    -> TextMuted
    }
    val textColor = if (active || focused) TextPrimary else TextMuted

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = if (expanded) 8.dp else 0.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center
    ) {
        // Thin left accent line for active nav item
        if (active) {
            Box(
                Modifier
                    .width(2.dp)
                    .height(16.dp)
                    .background(AccentBlue, RoundedCornerShape(1.dp))
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(icon, fontSize = 15.sp, color = iconColor)
        if (expanded) {
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 13.sp, color = textColor, maxLines = 1,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SEARCH BAR — lightweight BasicTextField
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
    // On TV, BasicTextField traps D-pad events. Use a simple focusable row instead.
    // Text input is handled via the on-screen keyboard when the search bar is selected.
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CardBg)
            .then(
                if (focused) Modifier.border(1.dp, AccentBorder, RoundedCornerShape(8.dp))
                else Modifier
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("⌕", fontSize = 14.sp, color = if (focused) AccentBlue else TextMuted)
        Spacer(Modifier.width(8.dp))
        Text(
            if (query.isEmpty()) "Search…" else query,
            color = if (query.isEmpty()) TextMuted else TextPrimary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (query.isNotEmpty()) {
            Text(
                "✕",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier
                    .clickable(onClick = onClear)
                    .padding(4.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// RECENTLY WATCHED STRIP
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun RecentlyWatchedStrip(items: List<RecentItem>, onPlay: (RecentItem) -> Unit) {
    val playbackPositionStore = (LocalContext.current.applicationContext as RizzoApp).playbackPositionStore
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(items, key = { "${it.type}:${it.id}" }) { item ->
            RecentCard(item = item, store = playbackPositionStore, onClick = { onPlay(item) })
        }
    }
}

@Composable
private fun RecentCard(item: RecentItem, store: PlaybackPositionStore, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(item.id, item.type) {
        val p = store.getProgress("${item.type}:${item.id}")
        fraction = if (p != null && p.durationMs > 0)
            (p.positionMs.toFloat() / p.durationMs).coerceIn(0f, 1f) else 0f
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) AccentBlue.copy(alpha = 0.25f) else CardBg)
            .then(if (focused) Modifier.border(1.dp, AccentBlue.copy(alpha = 0.6f), RoundedCornerShape(6.dp)) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChannelLogo(url = item.icon, name = item.name, size = 24.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                item.name,
                fontSize = 11.sp,
                color = if (focused) Color.White else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 110.dp)
            )
        }
        if (fraction > 0f && fraction < 0.98f) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = AccentBlue,
                trackColor = CardBg
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// CONTENT AREA
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ContentArea(
    content: BrowseContent,
    searchQuery: String,
    favorites: Map<String, Favorite>,
    viewModel: MainViewModel
) {
    val q = searchQuery.trim().lowercase()
    val listState = rememberLazyListState()
    val restoreIndex = viewModel.state.collectAsState().value.restoreScrollIndex

    LaunchedEffect(restoreIndex) {
        if (restoreIndex >= 0) {
            listState.scrollToItem(restoreIndex)
            viewModel.clearScrollRestore()
        }
    }

    // Auto-skip single-category
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

        is BrowseContent.VodStreams -> {
            val filtered = remember(content, q) {
                if (q.isEmpty()) content.items
                else content.items.filter { it.name.lowercase().contains(q) }
            }
            ContentGrid(
                favorites = favorites,
                onPlay = { stream -> viewModel.onPlayVod(stream) },
                onFavToggle = { s ->
                    viewModel.toggleFavorite(s.id.toString(), s.name, "vod", icon = s.icon)
                },
                rawVod = filtered
            )
        }

        is BrowseContent.SeriesList -> {
            val filtered = remember(content, q) {
                if (q.isEmpty()) content.items
                else content.items.filter { it.name.lowercase().contains(q) }
            }
            SeriesGrid(
                items = filtered,
                favorites = favorites,
                onSelect = viewModel::selectSeries,
                onFavToggle = { s ->
                    viewModel.toggleFavorite(s.id.toString(), s.name, "series", icon = s.cover)
                }
            )
        }

        is BrowseContent.Episodes -> {
            EpisodesView(
                seasons = content.seasons,
                favorites = favorites,
                onPlay = viewModel::onPlayEpisode,
                onFavToggle = { ep ->
                    viewModel.toggleFavorite(
                        ep.id.toString(),
                        ep.title ?: "Episode ${ep.episodeNum}",
                        "episode", ep.containerExtension
                    )
                }
            )
        }

        is BrowseContent.Favorites -> {
            FavoritesView(
                favorites = favorites,
                onPlay = viewModel::onPlayFavorite,
                onRemove = { fav -> viewModel.toggleFavorite(fav.id, fav.name, fav.type, fav.ext) },
                onMove = { fav, dir -> viewModel.moveFavorite(fav.id, dir) }
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
        items(items, key = { it.id }) { cat ->
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
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) AccentBlue.copy(alpha = 0.25f) else Color.Transparent)
            .then(if (focused) Modifier.border(1.dp, AccentBlue.copy(alpha = 0.6f), RoundedCornerShape(6.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = category.name,
            fontSize = 14.sp,
            color = if (focused) Color.White else TextPrimary,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text("›", fontSize = 16.sp, color = if (focused) AccentBlue else TextMuted)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// CHANNEL LIST (Live TV)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ChannelList(
    items: List<LiveStream>,
    favorites: Map<String, Favorite>,
    listState: LazyListState,
    onPlay: (LiveStream) -> Unit,
    onFavToggle: (LiveStream) -> Unit
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
        items(items, key = { it.id }) { stream ->
            val isFirst = items.firstOrNull()?.id == stream.id
            ChannelRow(
                stream = stream,
                isFavorite = favorites.containsKey(stream.id.toString()),
                onPlay = { onPlay(stream) },
                onFavToggle = { onFavToggle(stream) },
                modifier = if (isFirst) Modifier.focusRequester(firstItemFocusRequester) else Modifier
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
    var rowFocused by remember { mutableStateOf(false) }
    var starFocused by remember { mutableStateOf(false) }
    val anyFocused = rowFocused || starFocused

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (anyFocused) AccentBlue.copy(alpha = 0.25f) else Color.Transparent)
            .then(if (anyFocused) Modifier.border(1.dp, AccentBlue.copy(alpha = 0.6f), RoundedCornerShape(6.dp)) else Modifier)
    ) {
        // Thin left accent line when focused
        if (anyFocused) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(AccentBlue)
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { rowFocused = it.isFocused }
                .focusable()
                .clickable(onClick = onPlay)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChannelLogo(url = stream.icon, name = stream.name, size = 32.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = stream.name,
                fontSize = 14.sp,
                color = if (anyFocused) Color.White else TextPrimary,
                fontWeight = if (anyFocused) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // Fav toggle
        Box(
            modifier = Modifier
                .size(40.dp)
                .onFocusChanged { starFocused = it.isFocused }
                .focusable()
                .clickable(onClick = onFavToggle),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (isFavorite) "♥" else "♡",
                fontSize = 15.sp,
                color = when {
                    isFavorite  -> Gold
                    starFocused -> AccentBlue
                    else        -> TextMuted
                }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// CONTENT GRID — VOD / Series poster grid
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ContentGrid(
    favorites: Map<String, Favorite>,
    onPlay: (VodStream) -> Unit,
    onFavToggle: (VodStream) -> Unit,
    rawVod: List<VodStream>
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        gridItems(rawVod, key = { it.id }) { stream ->
            PosterCard(
                title = stream.name,
                imageUrl = stream.icon,
                isFavorite = favorites.containsKey(stream.id.toString()),
                onPlay = { onPlay(stream) },
                onFavToggle = { onFavToggle(stream) }
            )
        }
    }
}

@Composable
private fun SeriesGrid(
    items: List<Series>,
    favorites: Map<String, Favorite>,
    onSelect: (Series) -> Unit,
    onFavToggle: (Series) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        gridItems(items, key = { it.id }) { series ->
            PosterCard(
                title = series.name,
                imageUrl = series.cover,
                isFavorite = favorites.containsKey(series.id.toString()),
                onPlay = { onSelect(series) },
                onFavToggle = { onFavToggle(series) }
            )
        }
    }
}

@Composable
private fun PosterCard(
    title: String,
    imageUrl: String?,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onFavToggle: () -> Unit
) {
    var cardFocused by remember { mutableStateOf(false) }
    var starFocused by remember { mutableStateOf(false) }
    val anyFocused = cardFocused || starFocused

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CardBg)
            .then(
                if (anyFocused) Modifier.border(2.dp, AccentBorder, RoundedCornerShape(8.dp))
                else Modifier
            )
    ) {
        Column {
            // Image area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .onFocusChanged { cardFocused = it.isFocused }
                    .focusable()
                    .clickable(onClick = onPlay)
            ) {
                if (!imageUrl.isNullOrBlank()) {
                    var isError by remember(imageUrl) { mutableStateOf(false) }
                    if (!isError) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imageUrl).crossfade(false).memoryCacheKey(imageUrl).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            onError = { isError = true }
                        )
                    } else {
                        PosterFallback(title)
                    }
                } else {
                    PosterFallback(title)
                }
            }

            // Title + fav
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = if (anyFocused) Color.White else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (isFavorite) "♥" else "♡",
                    fontSize = 12.sp,
                    color = if (isFavorite) Gold else TextMuted,
                    modifier = Modifier
                        .onFocusChanged { starFocused = it.isFocused }
                        .focusable()
                        .clickable(onClick = onFavToggle)
                        .padding(2.dp)
                )
            }
        }
    }
}

@Composable
private fun PosterFallback(title: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(CardBg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title.firstOrNull { it.isLetter() }?.uppercase() ?: "?",
            fontSize = 28.sp,
            color = TextMuted,
            fontWeight = FontWeight.Bold
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// EPISODES VIEW
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodesView(
    seasons: Map<String, List<Episode>>,
    favorites: Map<String, Favorite>,
    onPlay: (Episode) -> Unit,
    onFavToggle: (Episode) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        val sorted = seasons.entries.sortedWith(compareBy { it.key.toIntOrNull() ?: Int.MAX_VALUE })
        for ((season, episodes) in sorted) {
            stickyHeader(key = "hdr_s$season") {
                Text(
                    "SEASON $season  ·  ${episodes.size} ep",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandGold,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MainBg)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
            items(episodes, key = { "ep_${it.id}" }) { ep ->
                EpisodeRow(
                    episode = ep,
                    isFavorite = favorites.containsKey(ep.id.toString()),
                    onPlay = { onPlay(ep) },
                    onFavToggle = { onFavToggle(ep) }
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onFavToggle: () -> Unit
) {
    var rowFocused by remember { mutableStateOf(false) }
    var starFocused by remember { mutableStateOf(false) }
    val anyFocused = rowFocused || starFocused

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (anyFocused) AccentBlue.copy(alpha = 0.25f) else Color.Transparent)
            .then(if (anyFocused) Modifier.border(1.dp, AccentBlue.copy(alpha = 0.6f), RoundedCornerShape(6.dp)) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { rowFocused = it.isFocused }
                .focusable()
                .clickable(onClick = onPlay)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "%02d".format(episode.episodeNum),
                fontSize = 12.sp,
                color = if (anyFocused) AccentBlue else TextMuted,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                episode.title ?: "Episode ${episode.episodeNum}",
                fontSize = 13.sp,
                color = if (anyFocused) Color.White else TextPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .onFocusChanged { starFocused = it.isFocused }
                .focusable()
                .clickable(onClick = onFavToggle),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (isFavorite) "♥" else "♡",
                fontSize = 14.sp,
                color = if (isFavorite) Gold else if (starFocused) AccentBlue else TextMuted
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// FAVORITES VIEW
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun FavoritesView(
    favorites: Map<String, Favorite>,
    onPlay: (Favorite) -> Unit,
    onRemove: (Favorite) -> Unit,
    onMove: ((Favorite, Int) -> Unit)? = null
) {
    if (favorites.isEmpty()) {
        EmptyHint("No favorites yet")
        return
    }
    val sorted = remember(favorites) { favorites.values.toList() }
    var confirmDelete by remember { mutableStateOf<Favorite?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(sorted.size, key = { "fav_${sorted[it].id}_${sorted[it].type}" }) { index ->
                val fav = sorted[index]
                var rowFocused by remember { mutableStateOf(false) }
                var upFocused  by remember { mutableStateOf(false) }
                var downFocused by remember { mutableStateOf(false) }
                var rmFocused  by remember { mutableStateOf(false) }
                val anyFocused = rowFocused || upFocused || downFocused || rmFocused

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (anyFocused) AccentBlue.copy(alpha = 0.2f) else Color.Transparent)
                        .then(if (anyFocused) Modifier.border(1.dp, AccentBlue.copy(alpha = 0.5f), RoundedCornerShape(6.dp)) else Modifier)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { rowFocused = it.isFocused }
                            .focusable()
                            .clickable { onPlay(fav) }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChannelLogo(url = fav.icon, name = fav.name, size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                fav.name,
                                fontSize = 14.sp,
                                color = if (anyFocused) Color.White else TextPrimary,
                                fontWeight = if (rowFocused) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(fav.type.uppercase(), fontSize = 9.sp, color = TextMuted)
                        }
                    }
                    // Move up
                    if (onMove != null && sorted.size > 1) {
                        val canUp = index > 0
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .onFocusChanged { upFocused = it.isFocused }
                                .focusable()
                                .clickable(enabled = canUp) { onMove(fav, -1) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "▲", fontSize = 12.sp,
                                color = when {
                                    !canUp     -> TextMuted.copy(alpha = 0.2f)
                                    upFocused  -> AccentBlue
                                    else       -> TextMuted
                                }
                            )
                        }
                        // Move down
                        val canDown = index < sorted.size - 1
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .onFocusChanged { downFocused = it.isFocused }
                                .focusable()
                                .clickable(enabled = canDown) { onMove(fav, 1) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "▼", fontSize = 12.sp,
                                color = when {
                                    !canDown     -> TextMuted.copy(alpha = 0.2f)
                                    downFocused  -> AccentBlue
                                    else         -> TextMuted
                                }
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .onFocusChanged { rmFocused = it.isFocused }
                            .focusable()
                            .clickable { confirmDelete = fav },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", fontSize = 13.sp, color = if (rmFocused) RedColor else TextMuted)
                    }
                }
            }
        }

        // Confirm delete dialog
        confirmDelete?.let { fav ->
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .background(Color(0xFF0C0F1A), RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Remove Favorite?", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(fav.name, color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        val cancelFocus = remember { FocusRequester() }
                        Text("Cancel", color = AccentBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.focusRequester(cancelFocus).focusable()
                                .clickable { confirmDelete = null }.padding(horizontal = 16.dp, vertical = 8.dp))
                        Text("Remove", color = RedColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.focusable()
                                .clickable { onRemove(fav); confirmDelete = null }.padding(horizontal = 16.dp, vertical = 8.dp))
                        LaunchedEffect(Unit) { cancelFocus.requestFocus() }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// CHANNEL LOGO
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun ChannelLogo(url: String?, name: String, size: Dp) {
    val context = LocalContext.current
    if (!url.isNullOrBlank()) {
        var isError by remember(url) { mutableStateOf(false) }
        Box(Modifier.size(size).clip(RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
            if (isError) {
                LogoFallback(name, size)
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(url).crossfade(false).memoryCacheKey(url).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    onError = { isError = true }
                )
            }
        }
    } else {
        LogoFallback(name, size)
    }
}

@Composable
private fun LogoFallback(name: String, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(AccentBlue.copy(alpha = 0.1f), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.firstOrNull { it.isLetter() }?.uppercase() ?: "?",
            color = AccentBlue,
            fontSize = (size.value * 0.4f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// UTILITY VIEWS
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun LoadingView() {
    val infiniteTransition = rememberInfiniteTransition(label = "psyduck")
    val armOffset by infiniteTransition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "arms"
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(modifier = Modifier.size(120.dp, 150.dp)) {
                val w = size.width
                val h = size.height
                val yellow = Color(0xFFF8D030)
                val darkYellow = Color(0xFFE8B810)
                val beak = Color(0xFFFFF0D0)
                val black = Color(0xFF1A1A1A)
                val white = Color.White
                val armShift = armOffset * 4.dp.toPx()

                // Body
                drawOval(yellow, topLeft = Offset(w * 0.18f, h * 0.38f), size = Size(w * 0.64f, h * 0.52f))
                // Belly
                drawOval(Color(0xFFFFE44A), topLeft = Offset(w * 0.28f, h * 0.48f), size = Size(w * 0.44f, h * 0.32f), alpha = 0.3f)
                // Feet
                drawOval(darkYellow, topLeft = Offset(w * 0.22f, h * 0.86f), size = Size(w * 0.2f, h * 0.06f))
                drawOval(darkYellow, topLeft = Offset(w * 0.56f, h * 0.87f), size = Size(w * 0.2f, h * 0.06f))
                // Tail
                drawOval(darkYellow, topLeft = Offset(w * 0.1f, h * 0.58f), size = Size(w * 0.12f, h * 0.14f))

                // Left arm
                drawOval(yellow, topLeft = Offset(w * 0.08f + armShift, h * 0.26f), size = Size(w * 0.14f, h * 0.22f))
                // Right arm
                drawOval(yellow, topLeft = Offset(w * 0.78f - armShift, h * 0.28f), size = Size(w * 0.14f, h * 0.22f))

                // Head
                drawOval(yellow, topLeft = Offset(w * 0.15f, h * 0.08f), size = Size(w * 0.58f, h * 0.4f))

                // Hair
                drawLine(black, Offset(w * 0.42f, h * 0.12f), Offset(w * 0.38f, h * 0.01f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                drawLine(black, Offset(w * 0.48f, h * 0.1f), Offset(w * 0.48f, h * -0.01f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                drawLine(black, Offset(w * 0.54f, h * 0.12f), Offset(w * 0.56f, h * 0.02f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)

                // Eyes
                drawCircle(white, radius = w * 0.08f, center = Offset(w * 0.36f, h * 0.24f))
                drawCircle(white, radius = w * 0.09f, center = Offset(w * 0.58f, h * 0.23f))
                // Pupils
                drawCircle(black, radius = w * 0.03f, center = Offset(w * 0.38f, h * 0.25f))
                drawCircle(black, radius = w * 0.035f, center = Offset(w * 0.56f, h * 0.24f))
                // Eye shine
                drawCircle(white, radius = w * 0.018f, center = Offset(w * 0.35f, h * 0.22f))
                drawCircle(white, radius = w * 0.02f, center = Offset(w * 0.54f, h * 0.21f))

                // Beak
                drawOval(beak, topLeft = Offset(w * 0.28f, h * 0.3f), size = Size(w * 0.38f, h * 0.12f))
                drawLine(Color(0xFFD0B880), Offset(w * 0.3f, h * 0.36f), Offset(w * 0.64f, h * 0.36f), strokeWidth = 1.dp.toPx())
            }
            Spacer(Modifier.height(4.dp))
            Text("Loading…", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, color = RedColor, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        var focused by remember { mutableStateOf(false) }
        Text(
            "Retry",
            color = if (focused) AccentBlue else TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .clickable(onClick = onRetry)
                .padding(8.dp)
        )
    }
}

@Composable
private fun EmptyHint(message: String = "Select a category") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}
