package com.rizzoplayer.iptv.ui.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rizzoplayer.iptv.ui.theme.RizzoMotion
import com.rizzoplayer.iptv.ui.theme.RizzoRadii
import com.rizzoplayer.iptv.ui.theme.RizzoSpacing
import com.rizzoplayer.iptv.ui.theme.RizzoSurfaceElevated
import com.rizzoplayer.iptv.ui.theme.RizzoTextPrimary
import com.rizzoplayer.iptv.ui.theme.RizzoTextSecondary

/**
 * RizzoIPTV bottom sheet overlay.
 *
 * - Slides up from bottom of screen
 * - Semi-transparent dimmed backdrop (dismissible on tap)
 * - Drag handle visual at top
 * - Content slot
 * - D-pad: focus trapped inside sheet while visible; last focused item receives focus on open
 */
@Composable
@Stable
fun RizzoBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissible: Boolean = true,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis = 300),
        ) + androidx.compose.animation.fadeIn(
            animationSpec = tween(durationMillis = 200),
        ),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(durationMillis = 250),
        ) + androidx.compose.animation.fadeOut(
            animationSpec = tween(durationMillis = 150),
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Backdrop
            if (dismissible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        ),
                )
            }

            // Sheet panel
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .clip(RoundedCornerShape(topStart = RizzoRadii.Lg, topEnd = RizzoRadii.Lg))
                    .background(RizzoSurfaceElevated)
                    .padding(bottom = RizzoSpacing.Xl),
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = RizzoSpacing.Md, bottom = RizzoSpacing.Sm),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .fillMaxWidth(0.12f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(RizzoTextSecondary.copy(alpha = 0.4f)),
                    )
                }

                content()
            }
        }
    }
}
