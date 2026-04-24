package com.rizzoplayer.iptv.ui.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rizzoplayer.iptv.ui.theme.RizzoAccent
import com.rizzoplayer.iptv.ui.theme.RizzoMotion
import com.rizzoplayer.iptv.ui.theme.RizzoRadii
import com.rizzoplayer.iptv.ui.theme.RizzoSpacing
import com.rizzoplayer.iptv.ui.theme.RizzoSurfaceElevated
import com.rizzoplayer.iptv.ui.theme.RizzoSurfaceVariant
import com.rizzoplayer.iptv.ui.theme.RizzoTextPrimary
import com.rizzoplayer.iptv.ui.theme.RizzoTextTertiary

/**
 * RizzoIPTV primary/secondary/text button.
 *
 * - Spring scale on focus (1.04x) / press (0.95x) via [RizzoMotion.DefaultSpring]
 * - Primary: [RizzoAccent] fill, [RizzoTextPrimary] label
 * - Secondary: [RizzoSurfaceVariant] fill, [RizzoTextPrimary] label
 * - Text: transparent fill, [RizzoAccent] label, no border
 * - Disabled: 40% opacity
 * - No ripple (TV convention)
 */
@Composable
@Stable
fun RizzoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: RizzoButtonVariant = RizzoButtonVariant.Primary,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
) {
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
        label = "buttonScale",
    )

    val (backgroundColor, textColor, borderColor) = when {
        !enabled -> Triple(
            RizzoSurfaceVariant.copy(alpha = 0.4f),
            RizzoTextTertiary,
            Color.Transparent,
        )
        variant == RizzoButtonVariant.Primary -> {
            if (isFocused) Triple(RizzoAccent, RizzoTextPrimary, RizzoAccent)
            else Triple(RizzoAccent, RizzoTextPrimary, Color.Transparent)
        }
        variant == RizzoButtonVariant.Secondary -> {
            if (isFocused) Triple(RizzoSurfaceElevated, RizzoTextPrimary, RizzoAccent)
            else Triple(RizzoSurfaceVariant, RizzoTextPrimary, Color.Transparent)
        }
        variant == RizzoButtonVariant.Text -> Triple(
            Color.Transparent,
            RizzoAccent,
            Color.Transparent,
        )
        else -> Triple(
            RizzoSurfaceVariant,
            RizzoTextPrimary,
            Color.Transparent,
        )
    }

    val borderWidth = if (isFocused && enabled) 1.dp else 0.dp

    Box(
        modifier = modifier
            .scale(scale)
            .then(
                if (borderWidth > 0.dp) Modifier.border(borderWidth, borderColor, RizzoRadii.SmShape)
                else Modifier,
            )
            .background(backgroundColor, RizzoRadii.SmShape)
            .then(
                if (enabled) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ) else Modifier,
            )
            .padding(horizontal = RizzoSpacing.Lg, vertical = RizzoSpacing.Md),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            // Icon + text horizontal row
            Box(modifier = Modifier) { icon() }
            Text(
                text = text,
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = RizzoSpacing.Sm),
            )
        } else {
            Text(
                text = text,
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** RizzoButton style variants */
enum class RizzoButtonVariant {
    /** Filled with [RizzoAccent] — primary CTA */
    Primary,
    /** Outlined / subtle fill — secondary actions */
    Secondary,
    /** No background — text-only actions */
    Text,
}

/**
 * RizzoIPTV text button — minimal, no background.
 * Convenience overload wrapping [RizzoButton] with [RizzoButtonVariant.Text].
 */
@Composable
@Stable
fun RizzoTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = RizzoButton(
    text = text,
    onClick = onClick,
    modifier = modifier,
    variant = RizzoButtonVariant.Text,
    enabled = enabled,
)
