package com.rizzoplayer.iptv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.rizzoplayer.iptv.R
import com.rizzoplayer.iptv.RizzoApp
import com.rizzoplayer.iptv.data.local.PlaybackPositionStore
import com.rizzoplayer.iptv.data.model.*
import com.rizzoplayer.iptv.ui.theme.*
import com.rizzoplayer.iptv.ui.viewmodel.*
import com.rizzoplayer.iptv.ui.screens.home.*

// ═══════════════════════════════════════════════════════════════════════════
// ROOT
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val favoritesList by viewModel.favoritesList.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()

    BackHandler(enabled = state.canGoBack) { viewModel.goBack() }

    var sidebarExpanded by remember { mutableStateOf(false) }
    val sidebarWidth by animateDpAsState(
        targetValue = if (sidebarExpanded) 180.dp else 52.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "sidebar",
    )
    val navFocusRequesters = remember { NAV_ENTRIES.map { FocusRequester() } }

    LaunchedEffect(sidebarExpanded) {
        if (sidebarExpanded) {
            kotlinx.coroutines.delay(50)
            val idx = NAV_ENTRIES.indexOfFirst { it.section == state.section }
            if (idx >= 0) {
                try { navFocusRequesters[idx].requestFocus() } catch (_: Exception) {}
            }
        }
    }

    Row(Modifier.fillMaxSize().background(MainBg)) {

        Sidebar(
            widthDp = sidebarWidth,
            expanded = sidebarExpanded,
            currentSection = state.section,
            canGoBack = state.canGoBack,
            navFocusRequesters = navFocusRequesters,
            onFocusEnter = { sidebarExpanded = true },
            onFocusExit = { sidebarExpanded = false },
            onSelect = { viewModel.selectSection(it) },
            onBack = viewModel::goBack,
            onLogout = viewModel::logout,
        )

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
                onClear = { viewModel.setSearchQuery("") }
            )

            // EPG — minimal one-liner for live TV
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
                    state.isLoading -> LoadingView()
                    state.error != null -> ErrorView(
                        message = state.error!!,
                        onDismiss = viewModel::retryCurrent,
                        onReload = viewModel::retryReload
                    )
                    else -> ContentArea(
                        content = state.content,
                        searchQuery = state.searchQuery,
                        favorites = favorites,
                        favoritesList = favoritesList,
                        viewModel = viewModel
                    )
                }
            }
        }

        // Overlays — toast and stream picker
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

        state.streamSelection?.let { selection ->
            StreamPickerDialog(
                selection = selection,
                onSelect = viewModel::playSelectedStream,
                onDismiss = viewModel::dismissStreamSelection
            )
        }

        state.playbackPrep?.let { prep ->
            PlaybackPrepOverlay(
                prep = prep,
                onCancel = viewModel::cancelPlaybackPrep
            )
        }
    }
}

private data class NavEntry(val icon: String, val label: String, val section: Section)
private val NAV_ENTRIES = listOf(
    NavEntry("▶", "Live",      Section.LIVE),
    NavEntry("▣", "Movies",    Section.VOD),
    NavEntry("≡", "Shows",     Section.SERIES),
    NavEntry("♥", "Favorites", Section.FAVORITES)
)

// ═══════════════════════════════════════════════════════════════════════════
// OVERLAYS & DIALOGS
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun StreamPickerDialog(
    selection: StreamSelectionState,
    onSelect: (TorrentioStream) -> Unit,
    onDismiss: () -> Unit
) {
    var focusedIdx by remember { mutableIntStateOf(0) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(selection.title, color = TextPrimary) },
        text = {
            Column {
                selection.streams.forEachIndexed { idx, stream ->
                    val isSelected = idx == focusedIdx
                    Text(
                        stream.title,
                        fontSize = 13.sp,
                        color = if (isSelected) AccentBlue else TextMuted,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) AccentBlue.copy(alpha = 0.15f) else Color.Transparent)
                            .onFocusChanged { if (it.isFocused) focusedIdx = idx }
                            .focusable()
                            .clickable { onSelect(stream) }
                            .padding(8.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun LoadingView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AccentBlue, strokeWidth = 2.dp)
    }
}

@Composable
fun ErrorView(message: String, onDismiss: () -> Unit, onReload: () -> Unit) {
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
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onDismiss).padding(8.dp)
            )
            Text(
                "Retry",
                fontSize = 13.sp,
                color = AccentBlue,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onReload).padding(8.dp)
            )
        }
    }
}

@Composable
fun PlaybackPrepOverlay(prep: PlaybackPrep, onCancel: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }

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
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = AccentBlue,
                strokeWidth = 3.dp
            )
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
                PlaybackPrep.Stage.SEARCHING -> "Searching for streams…"
                PlaybackPrep.Stage.QUEUING -> "Adding to TorBox…"
                PlaybackPrep.Stage.CACHING -> "Caching torrent…"
                PlaybackPrep.Stage.READY -> "Ready!"
                PlaybackPrep.Stage.FAILED -> "Failed"
            }
            Text(
                stageLabel,
                color = when (prep.stage) {
                    PlaybackPrep.Stage.FAILED -> RedColor
                    PlaybackPrep.Stage.READY -> Color(0xFF4DFF4D)
                    else -> AccentBlue
                },
                fontSize = 11.sp
            )
            Spacer(Modifier.height(24.dp))
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
