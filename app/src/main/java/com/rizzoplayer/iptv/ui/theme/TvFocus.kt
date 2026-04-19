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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standard TV-remote-friendly focusable + clickable modifier.
 * - 2dp AccentBlue border ring on focus (visible at TV viewing distance)
 * - Subtle AccentBlue tint background on focus
 * - Scale-up on focus, scale-down on press for tactile feel
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
        targetValue = when {
            isPressed -> 0.95f
            isFocused -> 1.04f
            else -> 1.0f
        },
        animationSpec = spring(
            stiffness = Spring.StiffnessHigh,
            dampingRatio = Spring.DampingRatioNoBouncy
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
