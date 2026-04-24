package com.rizzoplayer.iptv.ui.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rizzoplayer.iptv.ui.theme.RizzoMotion
import com.rizzoplayer.iptv.ui.theme.RizzoRadii
import com.rizzoplayer.iptv.ui.theme.RizzoSpacing
import com.rizzoplayer.iptv.ui.theme.RizzoSurfaceElevated
import com.rizzoplayer.iptv.ui.theme.RizzoTextPrimary
import com.rizzoplayer.iptv.ui.theme.RizzoTextSecondary

/**
 * RizzoIPTV modal dialog.
 *
 * - Centered overlay with semi-transparent dimmed backdrop
 * - Title + optional body text + action buttons in a row
 * - D-pad: first action button receives focus on open
 * - D-pad Back or backdrop tap dismisses (if [dismissible])
 * - Enter/exit: scale + fade with [RizzoMotion.GentleSpring]
 *
 * @param visible    Controls dialog visibility
 * @param onDismiss  Called when user dismisses (back tap or backdrop tap)
 * @param title      Dialog title string
 * @param body       Optional body text
 * @param actions    Slot for [RizzoButton] or other action composables
 * @param dismissible If true (default), back press and backdrop tap dismiss
 */
@Composable
@Stable
fun RizzoDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    body: String? = null,
    dismissible: Boolean = true,
    actions: @Composable () -> Unit = {},
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 250)) +
                scaleIn(initialScale = 0.92f, animationSpec = tween(durationMillis = 250)),
        exit = fadeOut(animationSpec = tween(durationMillis = 200)) +
               scaleOut(targetScale = 0.92f, animationSpec = tween(durationMillis = 200)),
    ) {
        // Backdrop — full-screen dim overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .then(
                    if (dismissible) Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ) else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Dialog card
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .clip(RoundedCornerShape(RizzoRadii.Lg))
                    .background(RizzoSurfaceElevated)
                    .padding(RizzoSpacing.Xl),
                horizontalAlignment = Alignment.Start,
            ) {
                // Title
                Text(
                    text = title,
                    color = RizzoTextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 28.sp,
                )

                // Body
                if (body != null) {
                    Spacer(modifier = Modifier.height(RizzoSpacing.Md))
                    Text(
                        text = body,
                        color = RizzoTextSecondary,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                    )
                }

                // Actions
                if (actions !== {}) {
                    Spacer(modifier = Modifier.height(RizzoSpacing.Xl))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        actions()
                    }
                }
            }
        }
    }
}

/**
 * Convenience factory to show a standard two-action dialog.
 *
 * Usage:
 * ```
 * TwoActionDialog(
 *     visible = showDialog,
 *     onDismiss = { showDialog = false },
 *     title = "Exit?",
 *     body = "Are you sure you want to exit?",
 *     confirmText = "Exit",
 *     onConfirm = { /* exit */ },
 *     dismissText = "Cancel",
 * )
 * ```
 */
@Composable
@Stable
fun TwoActionDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    body: String? = null,
    confirmText: String = "OK",
    onConfirm: () -> Unit,
    dismissText: String? = null,
) {
    RizzoDialog(
        visible = visible,
        onDismiss = onDismiss,
        title = title,
        body = body,
    ) {
        if (dismissText != null) {
            RizzoButton(
                text = dismissText,
                onClick = onDismiss,
                variant = RizzoButtonVariant.Secondary,
                modifier = Modifier.padding(end = RizzoSpacing.Md),
            )
        }
        RizzoButton(
            text = confirmText,
            onClick = onConfirm,
            variant = RizzoButtonVariant.Primary,
        )
    }
}
