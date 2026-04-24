package com.rizzoplayer.iptv.ui.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rizzoplayer.iptv.ui.theme.RizzoAccent
import com.rizzoplayer.iptv.ui.theme.RizzoAccentGlow
import com.rizzoplayer.iptv.ui.theme.RizzoMotion
import com.rizzoplayer.iptv.ui.theme.RizzoRadii
import com.rizzoplayer.iptv.ui.theme.RizzoSpacing
import com.rizzoplayer.iptv.ui.theme.RizzoSurfaceVariant
import com.rizzoplayer.iptv.ui.theme.RizzoTextPrimary
import com.rizzoplayer.iptv.ui.theme.RizzoTextTertiary

/**
 * RizzoIPTV circular icon-only button.
 *
 * A compact, circular D-pad-navigable button intended for toolbar actions
 * (close, settings, play/pause, etc.). On focus, the button scales up (1.08x)
 * with a glowing accent ring for visibility at TV distance.
 *
 * Fires [onClick] on D-pad Select.
 *
 * @param icon        [ImageVector] icon to display (e.g. from Material Icons)
 * @param onClick     D-pad Select action
 * @param modifier    Standard modifier chain
 * @param size        Diameter of the circular button (default 48.dp)
 * @param enabled     Whether the button is interactive (default true)
 * @param contentDesc Accessibility content description (required for TalkBack)
 */
@Composable
@Stable
fun RizzoIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    enabled: Boolean = true,
    contentDesc: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> RizzoMotion.PressScale
            isFocused -> 1.08f
            else -> 1f
        },
        animationSpec = RizzoMotion.DefaultSpring,
        label = "iconButtonScale",
    )

    val (backgroundColor, borderColor, iconColor) = when {
        !enabled -> Triple(
            RizzoSurfaceVariant.copy(alpha = 0.4f),
            Color.Transparent,
            RizzoTextTertiary,
        )
        isFocused -> Triple(
            RizzoSurfaceVariant,
            RizzoAccent,
            RizzoTextPrimary,
        )
        else -> Triple(
            RizzoSurfaceVariant.copy(alpha = 0.6f),
            Color.Transparent,
            RizzoTextPrimary,
        )
    }

    val borderWidth = if (isFocused && enabled) 2.dp else 0.dp

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .background(backgroundColor, CircleShape)
            .then(
                if (borderWidth > 0.dp)
                    Modifier.border(borderWidth, borderColor, CircleShape)
                else
                    Modifier,
            )
            .then(
                if (isFocused && enabled)
                    Modifier.border(4.dp, RizzoAccentGlow, CircleShape)
                else
                    Modifier,
            )
            .then(
                if (enabled)
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                else
                    Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = iconColor,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/**
 * Smaller variant of [RizzoIconButton] — 36.dp diameter.
 * Use for inline toolbar actions where 48.dp would be too large.
 */
@Composable
fun RizzoIconButtonSmall(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDesc: String? = null,
) = RizzoIconButton(
    icon = icon,
    onClick = onClick,
    modifier = modifier,
    size = 36.dp,
    enabled = enabled,
    contentDesc = contentDesc,
)
