package com.rizzoplayer.iptv.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rizzoplayer.iptv.ui.theme.RizzoCardBg
import com.rizzoplayer.iptv.ui.theme.RizzoElevation
import com.rizzoplayer.iptv.ui.theme.RizzoRadii

/**
 * RizzoIPTV focusable card primitive.
 *
 * A card is a simple rounded rectangle that:
 * - Responds to focus with a scale animation (via [rizzoFocusable])
 * - Has a subtle tinted background when focused
 * - Carries no internal layout — children define the content
 *
 * @param onClick    D-pad select action
 * @param modifier   Standard modifier chain
 * @param shape      Corner radius (default [RizzoRadii.MdShape])
 * @param background Background color (default [RizzoCardBg])
 * @param elevation  Resting elevation (default [RizzoElevation.Card])
 */
@Composable
fun RizzoCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RizzoRadii.MdShape,
    background: Color = RizzoCardBg,
    elevation: Dp = RizzoElevation.Card,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .background(background, shape)
            .rizzoFocusable(
                onClick = onClick,
                shape = shape,
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(RizzoElevation.Card),
            content = content,
        )
    }
}
