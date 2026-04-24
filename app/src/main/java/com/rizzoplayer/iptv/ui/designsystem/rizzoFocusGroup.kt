package com.rizzoplayer.iptv.ui.designsystem

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties

/**
 * Marks this composable as a focus group for D-pad navigation coordination.
 *
 * Use this on the container of a [RizzoRow] or [RizzoGrid] so that the focus
 * system can properly route UP/DOWN/LEFT/RIGHT across rows and columns.
 *
 * Usage:
 * ```
 * LazyColumn(modifier = Modifier.rizzoFocusGroup()) {
 *   items(...) { Item() }
 * }
 * ```
 */
@Stable
fun Modifier.rizzoFocusGroup(): Modifier =
    this.focusGroup()

/**
 * Configures the custom focus traversal order for a grid row.
 *
 * Phase 4 fix: UP from the first row previously went somewhere random.
 * This explicitly maps UP/DOWN focus traversal to the correct sibling rows.
 *
 * @param upFocus     [FocusRequester] of the element in the row above (null = stop at top)
 * @param downFocus   [FocusRequester] of the element in the row below (null = stop at bottom)
 */
@Stable
fun Modifier.rizzoGridRowFocus(
    upFocus: FocusRequester?,
    downFocus: FocusRequester?,
): Modifier = this.focusProperties {
    if (upFocus != null) up = upFocus
    if (downFocus != null) down = downFocus
}

/**
 * Marks the top row of a [RizzoGrid] so that DOWN wraps back to the first item.
 *
 * Applied to the grid container; the single down target is the first item's [FocusRequester].
 *
 * @param firstItemFocus [FocusRequester] of the first item in the grid
 */
@Stable
fun Modifier.rizzoGridTopRowFocus(firstItemFocus: FocusRequester): Modifier =
    this.focusProperties { down = firstItemFocus }
