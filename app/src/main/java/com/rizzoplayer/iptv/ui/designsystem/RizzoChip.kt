package com.rizzoplayer.iptv.ui.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rizzoplayer.iptv.ui.theme.RizzoAccent
import com.rizzoplayer.iptv.ui.theme.RizzoMotion
import com.rizzoplayer.iptv.ui.theme.RizzoRadii
import com.rizzoplayer.iptv.ui.theme.RizzoSpacing
import com.rizzoplayer.iptv.ui.theme.RizzoSurfaceVariant
import com.rizzoplayer.iptv.ui.theme.RizzoTextPrimary
import com.rizzoplayer.iptv.ui.theme.RizzoTextTertiary

/**
 * RizzoIPTV chip / tag component.
 *
 * - Selectable (toggle on D-pad Select) or non-selectable action chip
 * - Spring scale on focus (1.04x) / press (0.95x)
 * - Selected: [RizzoAccent] border + tint, [RizzoTextPrimary] label
 * - Unselected: subtle border, [RizzoTextTertiary] label
 * - Disabled: 40% opacity
 */
@Composable
@Stable
fun RizzoChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
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
        label = "chipScale",
    )

    val (backgroundColor, borderColor, textColor) = when {
        !enabled -> Triple(
            RizzoSurfaceVariant.copy(alpha = 0.4f),
            Color.Transparent,
            RizzoTextTertiary.copy(alpha = 0.4f),
        )
        selected -> Triple(
            RizzoAccent.copy(alpha = 0.15f),
            RizzoAccent,
            RizzoTextPrimary,
        )
        isFocused -> Triple(
            RizzoSurfaceVariant,
            RizzoAccent,
            RizzoTextPrimary,
        )
        else -> Triple(
            RizzoSurfaceVariant.copy(alpha = 0.5f),
            RizzoTextTertiary.copy(alpha = 0.5f),
            RizzoTextTertiary,
        )
    }

    val borderWidth = when {
        !enabled -> 0.dp
        selected -> 1.5.dp
        isFocused -> 1.dp
        else -> 0.5.dp
    }

    Box(
        modifier = modifier
            .scale(scale)
            .background(backgroundColor, RoundedCornerShape(RizzoRadii.Xs))
            .then(
                if (borderWidth > 0.dp)
                    Modifier.border(borderWidth, borderColor, RoundedCornerShape(RizzoRadii.Xs))
                else Modifier,
            )
            .then(
                if (enabled) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ) else Modifier,
            )
            .padding(horizontal = RizzoSpacing.Md, vertical = RizzoSpacing.Sm),
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (selected || isFocused) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/**
 * A group of [RizzoChip] components rendered horizontally.
 * Chips wrap if they overflow the available width.
 *
 * @param modifier Standard modifier
 * @param chips List of chip definitions: label + selected state + click handler
 */
@Composable
@Stable
fun RizzoChipGroup(
    modifier: Modifier = Modifier,
    chips: List<RizzoChipDefinition>,
) {
    androidx.compose.foundation.layout.Row(modifier = modifier) {
        chips.forEach { chip ->
            RizzoChip(
                label = chip.label,
                selected = chip.selected,
                enabled = chip.enabled,
                onClick = chip.onClick,
                modifier = Modifier.padding(end = RizzoSpacing.Sm),
            )
        }
    }
}

/** Definition for a single chip in a [RizzoChipGroup] */
data class RizzoChipDefinition(
    val label: String,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit = {},
)
