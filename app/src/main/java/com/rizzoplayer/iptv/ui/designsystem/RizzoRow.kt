package com.rizzoplayer.iptv.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * RizzoIPTV focus-aware lazy horizontal row with snap-to-item behaviour.
 *
 * Wraps [LazyRow] with:
 * - [rizzoFocusGroup] for D-pad navigation coordination
 * - Configurable item spacing
 * - Exposed [LazyListState] for programmatic scroll control
 *
 * @param items         Data set to render
 * @param key           Stable key per item (required for correct focus restoration)
 * @param state         [LazyListState] for scroll control (shared across rows)
 * @param contentPadding Padding around the entire row
 * @param itemSpacing   Horizontal gap between items (default 12.dp)
 * @param itemContent   Per-item composable
 */
@Composable
fun <T> RizzoRow(
    items: List<T>,
    key: ((item: T) -> Any)? = null,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    itemSpacing: androidx.compose.ui.unit.Dp = 12.dp,
    itemContent: @Composable (item: T, index: Int) -> Unit,
) {
    LazyRow(
        state = state,
        modifier = Modifier
            .fillMaxWidth()
            .rizzoFocusGroup(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        userScrollEnabled = true,
    ) {
        itemsIndexed(
            items = items,
            key = if (key != null) { _, item -> key(item) } else null,
        ) { index, item ->
            itemContent(item, index)
        }
    }
}

/**
 * Non-lazy row for a fixed set of composable children (e.g. a button group).
 */
@Composable
fun RizzoRowFixed(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .fillMaxWidth()
            .rizzoFocusGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}
