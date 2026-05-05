package com.rizzoplayer.iptv.ui.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rizzoplayer.iptv.ui.theme.RizzoAccent
import com.rizzoplayer.iptv.ui.theme.RizzoAccentDim
import com.rizzoplayer.iptv.ui.theme.RizzoMotion
import com.rizzoplayer.iptv.ui.theme.Spec

/**
 * Canonical TV-remote-friendly focusable + clickable modifier for RizzoIPTV.
 *
 * - Phase 2 Spec: 4.dp [focusBorderColor] ring on focus
 * - Subtle [focusBackgroundColor] tint background on focus
 * - Scale-up on focus (1.06x), scale-down on press (0.95x)
 * - Shadow elevation (8.dp) on focus
 * - Optional cinematic glow: soft outer bloom using [RizzoAccentGlow]
 *
 * @param hasGlow When true, adds a soft 12.dp blur glow in RizzoAccent color behind the card
 *                on focus — creates the "Stremio-style" cinematic bloom at TV viewing distance.
 */
@OptIn(ExperimentalFoundationApi::class)
@Stable
fun Modifier.rizzoFocusable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(8.dp),
    focusBorderWidth: Dp = Spec.focusBorder,
    focusBorderColor: Color = RizzoAccent,
    focusBackgroundColor: Color = RizzoAccentDim,
    interactionSource: MutableInteractionSource? = null,
    hasGlow: Boolean = false,
): Modifier = composed {
    val actualInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isFocused by actualInteractionSource.collectIsFocusedAsState()
    val isPressed by actualInteractionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> RizzoMotion.PressScale
            isFocused -> Spec.focusScale
            else -> 1f
        },
        animationSpec = RizzoMotion.DefaultSpring,
        label = "tvClickableScale",
    )

    // Animated glow alpha — swells in when focused, fades out when unfocused
    val glowAlpha by animateFloatAsState(
        targetValue = if (isFocused && hasGlow) 1f else 0f,
        animationSpec = RizzoMotion.GentleSpring,
        label = "glowAlpha",
    )

    this
        .scale(scale)
        .then(
            if (isFocused) {
                // Cinematic glow via tinted shadow: uses RizzoAccent as the shadow color
                // (ambientColor / spotColor) to produce a soft colored bloom behind the card.
                // For hasGlow=true, we double the elevation and tighten the alpha for a
                // more pronounced "Stremio-style" halo. The glow is entirely handled by
                // the shadow modifier — no drawBehind needed inside composed.
                val glowElevation = if (hasGlow) Spec.focusElevation * 2 else Spec.focusElevation
                val glowAmbient = if (hasGlow) RizzoAccent.copy(alpha = 0.40f) else RizzoAccent.copy(alpha = 0.25f)
                val glowSpot = if (hasGlow) RizzoAccent.copy(alpha = 0.40f) else RizzoAccent.copy(alpha = 0.25f)
                Modifier.shadow(glowElevation, shape, ambientColor = glowAmbient, spotColor = glowSpot)
            } else Modifier
        )
        .background(
            color = if (isFocused) focusBackgroundColor else Color.Transparent,
            shape = shape,
        )
        .then(
            if (isFocused) Modifier.border(focusBorderWidth, focusBorderColor.copy(alpha = if (hasGlow) 0.85f else 0.5f), shape)
            else Modifier,
        )
        .combinedClickable(
            interactionSource = actualInteractionSource,
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick
        )
}

/**
 * Attach a [FocusRequester] to this modifier for programmatic focus control.
 */
@Stable
fun Modifier.rizzoFocusRequester(focusRequester: FocusRequester): Modifier =
    this.focusRequester(focusRequester)

/**
 * Convenience overload that combines [rizzoFocusable] with [rizzoFocusRequester].
 */
@Stable
fun Modifier.rizzoFocusableWithRequester(
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(8.dp),
    focusBorderWidth: Dp = Spec.focusBorder,
    focusBorderColor: Color = RizzoAccent,
    focusBackgroundColor: Color = RizzoAccentDim,
    interactionSource: MutableInteractionSource? = null,
): Modifier = this
    .rizzoFocusRequester(focusRequester)
    .rizzoFocusable(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = shape,
        focusBorderWidth = focusBorderWidth,
        focusBorderColor = focusBorderColor,
        focusBackgroundColor = focusBackgroundColor,
        interactionSource = interactionSource,
    )

// ── Backwards-compatibility shim ──────────────────────────────────────────────

/**
 * @deprecated Use [Modifier.rizzoFocusable] directly
 */
@Deprecated(
    message = "Use Modifier.rizzoFocusable",
    replaceWith = ReplaceWith(
        "Modifier.rizzoFocusable(onClick, onLongClick, shape, focusBorderWidth, focusBorderColor, focusBackgroundColor, interactionSource)",
    ),
)
fun Modifier.tvClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(8.dp),
    focusBorderWidth: Dp = Spec.focusBorder,
    focusBorderColor: Color = RizzoAccent,
    focusBackgroundColor: Color = RizzoAccentDim,
    interactionSource: MutableInteractionSource? = null,
): Modifier = rizzoFocusable(
    onClick = onClick,
    onLongClick = onLongClick,
    shape = shape,
    focusBorderWidth = focusBorderWidth,
    focusBorderColor = focusBorderColor,
    focusBackgroundColor = focusBackgroundColor,
    interactionSource = interactionSource,
)
