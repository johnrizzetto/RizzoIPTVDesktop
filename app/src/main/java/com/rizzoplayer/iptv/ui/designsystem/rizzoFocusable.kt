package com.rizzoplayer.iptv.ui.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rizzoplayer.iptv.ui.theme.RizzoAccent
import com.rizzoplayer.iptv.ui.theme.RizzoAccentDim
import com.rizzoplayer.iptv.ui.theme.RizzoMotion

/**
 * Canonical TV-remote-friendly focusable + clickable modifier for RizzoIPTV.
 *
 * - 2.dp [RizzoAccent] border ring on focus (visible at 3–5m TV viewing distance)
 * - Subtle [RizzoAccentDim] tint background on focus
 * - Scale-up on focus (1.04x), scale-down on press (0.95x) — [RizzoMotion.DefaultSpring]
 * - Uses [MutableInteractionSource] for correct hot-observable focus tracking
 * - No ripple (TV convention — ripple is phone/touch idiom)
 *
 * Usage:
 * ```
 * Modifier.rizzoFocusable(shape = RizzoRadii.SmShape) { doThing() }
 * ```
 */
@Stable
fun Modifier.rizzoFocusable(
    onClick: () -> Unit,
    shape: Shape = RoundedCornerShape(8.dp),
    focusBorderWidth: Dp = 2.dp,
    focusBorderColor: Color = RizzoAccent,
    focusBackgroundColor: Color = RizzoAccentDim,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> RizzoMotion.PressScale
            isFocused -> RizzoMotion.FocusScale
            else -> 1f
        },
        animationSpec = RizzoMotion.DefaultSpring,
        label = "tvClickableScale",
    )

    this
        .scale(scale)
        .background(
            color = if (isFocused) focusBackgroundColor else Color.Transparent,
            shape = shape,
        )
        .then(
            if (isFocused) Modifier.border(focusBorderWidth, focusBorderColor, shape)
            else Modifier,
        )
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
}

/**
 * Attach a [FocusRequester] to this modifier for programmatic focus control.
 *
 * Usage:
 * ```
 * val focusRequester = remember { FocusRequester() }
 * Modifier.rizzoFocusable(onClick = {})
 *   .rizzoFocusRequester(focusRequester)
 * ```
 */
@Stable
fun Modifier.rizzoFocusRequester(focusRequester: FocusRequester): Modifier =
    this.focusRequester(focusRequester)

/**
 * Convenience overload that combines [rizzoFocusable] with [rizzoFocusRequester].
 *
 * Usage:
 * ```
 * val focusRequester = remember { FocusRequester() }
 * Modifier.rizzoFocusableWithRequester(
 *   focusRequester = focusRequester,
 *   onClick = {},
 * )
 * ```
 */
@Stable
fun Modifier.rizzoFocusableWithRequester(
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    shape: Shape = RoundedCornerShape(8.dp),
    focusBorderWidth: Dp = 2.dp,
    focusBorderColor: Color = RizzoAccent,
    focusBackgroundColor: Color = RizzoAccentDim,
): Modifier = this
    .rizzoFocusRequester(focusRequester)
    .rizzoFocusable(
        onClick = onClick,
        shape = shape,
        focusBorderWidth = focusBorderWidth,
        focusBorderColor = focusBorderColor,
        focusBackgroundColor = focusBackgroundColor,
    )

// ── Backwards-compatibility shim ──────────────────────────────────────────────

/**
 * @deprecated Use [Modifier.rizzoFocusable] directly
 */
@Deprecated(
    message = "Use Modifier.rizzoFocusable",
    replaceWith = ReplaceWith(
        "Modifier.rizzoFocusable(onClick, shape, focusBorderWidth, focusBorderColor, focusBackgroundColor)",
    ),
)
fun Modifier.tvClickable(
    onClick: () -> Unit,
    shape: Shape = RoundedCornerShape(8.dp),
    focusBorderWidth: Dp = 2.dp,
    focusBorderColor: Color = RizzoAccent,
    focusBackgroundColor: Color = RizzoAccentDim,
): Modifier = rizzoFocusable(
    onClick = onClick,
    shape = shape,
    focusBorderWidth = focusBorderWidth,
    focusBorderColor = focusBorderColor,
    focusBackgroundColor = focusBackgroundColor,
)
