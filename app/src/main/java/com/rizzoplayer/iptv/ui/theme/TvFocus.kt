package com.rizzoplayer.iptv.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standard TV-remote-friendly focusable + clickable modifier.
 * - 2dp AccentBlue border ring on focus (visible at TV viewing distance)
 * - Subtle AccentBlue tint background on focus
 * - Scale-up on focus, scale-down on press for tactile feel
 * - Uses MutableInteractionSource (correct hot-observable focus tracking)
 * - No ripple (TV convention — ripple is phone/touch idiom)
 *
 * Usage: Modifier.tvClickable(shape = RoundedCornerShape(8.dp)) { doThing() }
 */
fun Modifier.tvClickable(
    onClick: () -> Unit,
    shape: Shape = RoundedCornerShape(8.dp),
    focusBorderWidth: Dp = 2.dp,
    focusBorderColor: Color = AccentBlue,
    focusBackgroundColor: Color = AccentBlue.copy(alpha = 0.14f),
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else if (isFocused) 1.04f else 1.0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "tvScale"
    )
    this
        .scale(scale)
        .background(if (isFocused) focusBackgroundColor else Color.Transparent, shape)
        .then(
            if (isFocused) Modifier.border(focusBorderWidth, focusBorderColor, shape)
            else Modifier
        )
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

/**
 * Consistent spring spec used across all TV-focusable cards.
 * Keeps scale animations uniform regardless of which screen/composable uses them.
 */
val TvCardSpringSpec = spring<Float>(
    stiffness = Spring.StiffnessMediumLow
)

/**
 * Wraps UP/DOWN focus traversal for a grid row.
 * Phase 4: fixes the "UP from first row goes somewhere random" problem by
 * passing the FocusRequester of the element directly above/below in the grid.
 *
 * @param upFocus   FocusRequester of the element in the row above (null = stop at top)
 * @param downFocus FocusRequester of the element in the row below (null = stop at bottom)
 */
fun Modifier.gridRowFocus(
    upFocus: FocusRequester?,
    downFocus: FocusRequester?
): Modifier = this.focusProperties {
    if (upFocus != null)   { up = upFocus }
    if (downFocus != null)  { down = downFocus }
}

/**
 * Marker modifier for the top element of a LazyVerticalGrid.
 * Phase 4: DOWN from the first row should wrap back to the first item.
 * Applied to the grid container; the single down target = the first item's FocusRequester.
 */
fun Modifier.gridTopRowFocus(firstItemFocus: FocusRequester): Modifier =
    this.focusProperties { down = firstItemFocus }

/**
 * Default TV card focus scale — 1.04x on focus, 0.95x on press.
 * Use with animateFloatAsState(targetValue = ..., animationSpec = TvCardSpringSpec).
 */
const val TV_CARD_FOCUS_SCALE = 1.04f
const val TV_CARD_PRESS_SCALE  = 0.95f
