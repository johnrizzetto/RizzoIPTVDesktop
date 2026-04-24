package com.rizzoplayer.iptv.ui.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rizzoplayer.iptv.ui.theme.RizzoAccent
import com.rizzoplayer.iptv.ui.theme.RizzoSurfaceVariant

/**
 * RizzoIPTV linear progress bar.
 *
 * Two modes:
 * - **Determinate** — [progress] is a Float in 0f..1f, shows fill fraction
 * - **Indeterminate** — [progress] is null, shows animated shimmer sweep
 *
 * @param modifier         Standard modifier
 * @param progress         Float in 0f..1f for determinate mode, null for indeterminate
 * @param trackColor      Background track color (default: [RizzoSurfaceVariant])
 * @param fillColor        Progress fill color (default: [RizzoAccent])
 * @param trackHeight      Height of the track (default: 6.dp)
 * @param trackRadius      Corner radius of the track (default: 3.dp)
 */
@Composable
@Stable
fun RizzoProgressBar(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    trackColor: Color = RizzoSurfaceVariant,
    fillColor: Color = RizzoAccent,
    trackHeight: Dp = 6.dp,
    trackRadius: Dp = 3.dp,
) {
    val isIndeterminate = progress == null

    // Determinate: animate fill width
    val animatedProgress by animateFloatAsState(
        targetValue = progress ?: 0f,
        animationSpec = tween(durationMillis = 300),
        label = "progress",
    )

    // Indeterminate: shimmer sweep animation
    val infiniteTransition = rememberInfiniteTransition(label = "indeterminate")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1_400,
                easing = LinearEasing,
            ),
        ),
        label = "shimmerOffset",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight)
            .clip(RoundedCornerShape(trackRadius))
            .background(trackColor),
    ) {
        if (isIndeterminate) {
            // Indeterminate shimmer bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.45f)
                    .fillMaxHeight()
                    .background(
                        color = fillColor.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(trackRadius),
                    ),
            ) {
                // Animate the shimmer position using translation
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.4f)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    fillColor.copy(alpha = 0.9f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }
        } else {
            // Determinate fill
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(trackRadius))
                    .background(fillColor),
            )
        }
    }
}
