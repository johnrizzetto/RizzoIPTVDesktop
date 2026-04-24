package com.rizzoplayer.iptv.ui.designsystem
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rizzoplayer.iptv.ui.theme.RizzoError
import com.rizzoplayer.iptv.ui.theme.RizzoInfo
import com.rizzoplayer.iptv.ui.theme.RizzoMotion
import com.rizzoplayer.iptv.ui.theme.RizzoRadii
import com.rizzoplayer.iptv.ui.theme.RizzoSpacing
import com.rizzoplayer.iptv.ui.theme.RizzoSuccess
import com.rizzoplayer.iptv.ui.theme.RizzoSurfaceElevated
import com.rizzoplayer.iptv.ui.theme.RizzoTextPrimary
import com.rizzoplayer.iptv.ui.theme.RizzoTextSecondary
import kotlinx.coroutines.delay

/**
 * RizzoIPTV ephemeral toast notification.
 *
 * - Appears at the bottom-center of the screen
 * - Auto-dismisses after [durationMs] (default 3 000 ms)
 * - Types: success (green), error (red), info (blue)
 * - Spring scale enter/exit
 *
 * This is a **composable that manages its own visibility state** — call it once
 * at screen level with a hoisted `visible` state:
 *
 * ```
 * var toastVisible by remember { mutableStateOf(false) }
 * var toastMessage by remember { mutableStateOf("") }
 * var toastType by remember { mutableStateOf(RizzoToastType.Info) }
 *
 * RizzoToast(
 *     message = toastMessage,
 *     type = toastType,
 *     visible = toastVisible,
 *     onDismiss = { toastVisible = false },
 * )
 *
 * // Show from anywhere:
 * toastMessage = "Saved!"
 * toastType = RizzoToastType.Success
 * toastVisible = true
 * ```
 */
@Composable
@Stable
fun RizzoToast(
    message: String,
    type: RizzoToastType = RizzoToastType.Info,
    visible: Boolean,
    onDismiss: () -> Unit,
    durationMs: Long = 3_000L,
    modifier: Modifier = Modifier,
) {
    val accentColor = when (type) {
        RizzoToastType.Success -> RizzoSuccess
        RizzoToastType.Error -> RizzoError
        RizzoToastType.Info -> RizzoInfo
    }

    val iconChar = when (type) {
        RizzoToastType.Success -> "✓"
        RizzoToastType.Error -> "✕"
        RizzoToastType.Info -> "i"
    }

    // Auto-dismiss timer
    LaunchedEffect(visible) {
        if (visible) {
            delay(durationMs)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(durationMillis = 200)) +
                scaleIn(initialScale = 0.8f, animationSpec = tween(durationMillis = 250)),
        exit = fadeOut(animationSpec = tween(durationMillis = 150)) +
                scaleOut(targetScale = 0.8f, animationSpec = tween(durationMillis = 150)),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .align(Alignment.BottomCenter)
                    .padding(bottom = RizzoSpacing.Xl)
                    .background(
                        color = RizzoSurfaceElevated,
                        shape = RoundedCornerShape(RizzoRadii.Md),
                    )
                    .padding(
                        horizontal = RizzoSpacing.Md,
                        vertical = RizzoSpacing.Md,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Accent bar
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 32.dp)
                        .background(
                            color = accentColor,
                            shape = RoundedCornerShape(2.dp),
                        ),
                )
                Spacer(modifier = Modifier.width(RizzoSpacing.Md))
                Text(
                    text = iconChar,
                    color = accentColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(RizzoSpacing.Md))
                Text(
                    text = message,
                    color = RizzoTextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}

/** Toast notification types with associated accent color */
enum class RizzoToastType {
    Success,
    Error,
    Info,
}
