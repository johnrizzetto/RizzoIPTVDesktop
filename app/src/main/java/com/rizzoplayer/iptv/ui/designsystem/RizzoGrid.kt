package com.rizzoplayer.iptv.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * RizzoIPTV focus-aware vertical grid with [rizzoFocusGroup].
 *
 * Wraps [LazyVerticalGrid] with:
 * - [rizzoFocusGroup] for D-pad navigation coordination
 * - Grid-row focus helpers from [rizzoFocusGroup]
 *
 * @param items        Data set to render
 * @param columns      Number of columns in the grid
 * @param key          Stable key per item (required for correct focus restoration)
 * @param state        [LazyGridState] for programmatic scroll control
 * @param contentPadding Padding around the grid
 * @param verticalSpacing Vertical gap between rows
 * @param horizontalSpacing Horizontal gap between items
 * @param itemContent  Per-item composable
 */
@Composable
fun <T> RizzoGrid(
    items: List<T>,
    columns: Int,
    key: ((item: T) -> Any)? = null,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalSpacing: Dp = 12.dp,
    horizontalSpacing: Dp = 12.dp,
    itemContent: @Composable (item: T) -> Unit,
) {
    LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(columns),
        state = state,
        modifier = Modifier
            .fillMaxWidth()
            .rizzoFocusGroup(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
        userScrollEnabled = true,
    ) {
        items(
            items = items,
            key = key,
        ) { item ->
            itemContent(item)
        }
    }
}
