package com.rizzoplayer.iptv.ui.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rizzoplayer.iptv.ui.theme.RizzoRadii
import com.rizzoplayer.iptv.ui.theme.RizzoShimmerBase
import com.rizzoplayer.iptv.ui.theme.RizzoShimmerHighlight
import com.rizzoplayer.iptv.ui.theme.Spec

/**
 * Infinite transition for shimmer animation.
 */
@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1_200,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerTranslate",
    )

    return Brush.linearGradient(
        colors = listOf(
            RizzoShimmerBase,
            RizzoShimmerHighlight,
            RizzoShimmerBase,
        ),
        start = Offset(translate - 500f, 0f),
        end = Offset(translate, 0f),
    )
}

/**
 * Base shimmer rectangle with shimmer brush applied.
 */
@Composable
private fun ShimmerRect(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RizzoRadii.SmShape,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(shimmerBrush()),
    )
}

/**
 * Skeleton poster placeholder — a rounded rectangle with shimmer.
 * Shows shimmer only after skeletonGraceMs to avoid flashing on fast loads.
 *
 * @param ratio  Aspect ratio from [RizzoPosterRatio]
 * @param modifier Standard modifier
 */
@Composable
fun RizzoSkeletonPoster(
    modifier: Modifier = Modifier,
    ratio: Float = RizzoPosterRatio.Poster2x3,
) {
    var showShimmer by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(Spec.skeletonGraceMs)
        showShimmer = true
    }
    if (showShimmer) {
        ShimmerRect(
            modifier = modifier.aspectRatio(ratio),
        )
    } else {
        Box(modifier = modifier.aspectRatio(ratio))
    }
}

/**
 * Skeleton text line placeholder.
 * Shows shimmer only after skeletonGraceMs to avoid flashing on fast loads.
 *
 * @param width  Fraction of container width (0–1), or null for full-width
 * @param height Height of the line (default 16.dp)
 */
@Composable
fun RizzoSkeletonLine(
    modifier: Modifier = Modifier,
    widthFraction: Float? = null,
    height: Dp = 16.dp,
) {
    var showShimmer by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(Spec.skeletonGraceMs)
        showShimmer = true
    }
    val widthMod = if (widthFraction != null) Modifier.fillMaxWidth(widthFraction) else Modifier.fillMaxWidth()
    if (showShimmer) {
        ShimmerRect(
            modifier = modifier
                .then(widthMod)
                .height(height),
        )
    } else {
        Box(modifier = modifier.then(widthMod).height(height))
    }
}

/**
 * Skeleton card placeholder — poster + 2 text lines, mimics [RizzoCard].
 *
 * @param modifier Standard modifier
 */
@Composable
fun RizzoSkeletonCard(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        RizzoSkeletonPoster(
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        RizzoSkeletonLine(height = 14.dp)
        Spacer(modifier = Modifier.height(4.dp))
        RizzoSkeletonLine(height = 12.dp, widthFraction = 0.65f)
    }
}

/**
 * Skeleton row of [RizzoCard] placeholders.
 *
 * @param count Number of skeleton cards to show
 * @param posterRatio Aspect ratio of each card's poster
 */
@Composable
fun RizzoSkeletonRow(
    count: Int,
    posterRatio: Float = RizzoPosterRatio.Poster2x3,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(count) {
            RizzoSkeletonCard(
                modifier = Modifier
                    .width(140.dp)
                    .aspectRatio(posterRatio * 1.2f),
            )
        }
    }
}
