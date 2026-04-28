package com.rizzoplayer.iptv.ui.screens.home

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rizzoplayer.iptv.data.model.UnifiedTorrent
import com.rizzoplayer.iptv.ui.designsystem.rizzoFocusGroup
import com.rizzoplayer.iptv.ui.theme.*

@Composable
fun StreamPickerScreen(
    state: com.rizzoplayer.iptv.ui.viewmodel.BrowseContent.StreamPicker,
    onSelectStream: (UnifiedTorrent, Int) -> Unit,
    onBack: () -> Unit,
) {
    // Debounce: guard against rapid D-pad mashing firing multiple onSelect calls
    var selectedLocked by remember { mutableStateOf(false) }
    val parsedStreams = remember(state.streams) { state.streams.map { s -> s to TorrentioParser.parse(s) } }

    var focusedIdx by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val firstItemFocus = remember { FocusRequester() }

    // Reset focus index whenever a new stream list is presented
    LaunchedEffect(state.streams) {
        focusedIdx = 0
        kotlinx.coroutines.delay(80)
        try { firstItemFocus.requestFocus() } catch (_: Exception) {}
    }

    LaunchedEffect(focusedIdx) {
        listState.animateScrollToItem(focusedIdx.coerceIn(0, (parsedStreams.size - 1).coerceAtLeast(0)))
    }

    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MainBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // ── Header ──────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PanelBg.copy(alpha = 0.95f))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ButtonBg)
                            .border(1.dp, BorderColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .focusable()
                            .clickable(onClick = onBack)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("✕  Close", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // ── Column headers ─────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${state.streams.size} streams",
                    fontSize = 11.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                )
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(
                        "4K"  to BadgeGold,
                        "DV"  to BadgePurple,
                        "HDR" to BadgePurple,
                    ).forEach { (label, color) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(color.copy(alpha = 0.85f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(BorderColor.copy(alpha = 0.3f)))
            Spacer(Modifier.height(6.dp))

            // ── Stream list ──────────────────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .rizzoFocusGroup(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(
                    items = parsedStreams,
                    key = { _, (stream, _) -> stream.url }
                ) { idx, (stream, metadata) ->
                    val isFocused = idx == focusedIdx
                    val isLoading = state.loadingIndex == idx
                    val isError = state.errorIndex == idx
                    val errorMsg = if (isError) state.errorMessage else null

                    StreamPickerRow(
                        stream = stream,
                        metadata = metadata,
                        isFocused = isFocused,
                        isLoading = isLoading,
                        isError = isError,
                        errorMessage = errorMsg,
                        onSelect = {
                            if (!selectedLocked) {
                                selectedLocked = true
                                onSelectStream(stream, idx)
                            }
                        },
                        focusRequester = if (idx == 0) firstItemFocus else FocusRequester(),
                        onFocusChanged = { if (it.isFocused) focusedIdx = idx },
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamPickerRow(
    stream: UnifiedTorrent,
    metadata: TorrentioParser.StreamMetadata,
    isFocused: Boolean,
    isLoading: Boolean,
    isError: Boolean,
    errorMessage: String?,
    onSelect: () -> Unit,
    focusRequester: FocusRequester,
    onFocusChanged: (androidx.compose.ui.focus.FocusState) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged(onFocusChanged)
                // Row is no longer focusable itself; StreamItemCard handles focus.
                ,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Main card takes most space
            Box(modifier = Modifier.weight(1f)) {
                StreamItemCard(
                    stream = stream,
                    metadata = metadata,
                    isFailed = isError,
                    failedReason = errorMessage,
                    onSelect = onSelect,
                    interactionSource = interactionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isError) Modifier.border(1.dp, RizzoError.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            else Modifier
                        )
                )
            }

            // Inline status indicator on the right side
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            isLoading -> CardBgFocused.copy(alpha = 0.8f)
                            isError -> RizzoError.copy(alpha = 0.2f)
                            else -> Color.Transparent
                        }
                    )
                    .then(
                        if (isError) Modifier.border(1.dp, RizzoError.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = AccentBlue,
                            strokeWidth = 2.dp,
                        )
                    }
                    isError -> {
                        Text(
                            text = "✕",
                            fontSize = 18.sp,
                            color = RizzoError,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // Error message below the row
        if (isError && errorMessage != null) {
            Text(
                text = errorMessage,
                fontSize = 11.sp,
                color = RizzoError.copy(alpha = 0.8f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 2.dp, bottom = 4.dp)
            )
        }
    }
}
