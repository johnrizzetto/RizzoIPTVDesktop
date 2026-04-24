package com.rizzoplayer.iptv.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rizzoplayer.iptv.ui.theme.RizzoCardBg
import com.rizzoplayer.iptv.ui.theme.RizzoRadii

/**
 * Aspect ratio variants for [RizzoPoster].
 */
object RizzoPosterRatio {
    /** 2:3 — standard movie/show poster */
    const val Poster2x3 = 2f / 3f

    /** 16:9 — widescreen banner or landscape card */
    const val Wide16x9 = 16f / 9f

    /** 1:1 — square format */
    const val Square1x1 = 1f / 1f
}

/**
 * RizzoIPTV poster — image + overlay support with optional focus.
 *
 * @param imageUrl    URL of the poster/backdrop image (null = placeholder)
 * @param onClick     D-pad select action (null = non-focusable)
 * @param ratio       Aspect ratio from [RizzoPosterRatio] (default [RizzoPosterRatio.Poster2x3])
 * @param content     Optional overlay content (e.g. progress bar, badge)
 */
@Composable
fun RizzoPoster(
    imageUrl: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    ratio: Float = RizzoPosterRatio.Poster2x3,
    placeholderColor: Color = RizzoCardBg,
    content: @Composable (() -> Unit)? = null,
) {
    val shape = RizzoRadii.MdShape

    val imageModifier = modifier
        .aspectRatio(ratio)
        .clip(shape)
        .background(placeholderColor)

    Box(modifier = imageModifier) {
        if (imageUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        content?.invoke()
    }
}

/**
 * Focusable version of [RizzoPoster].
 */
@Composable
fun RizzoPosterCard(
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ratio: Float = RizzoPosterRatio.Poster2x3,
    placeholderColor: Color = RizzoCardBg,
    content: @Composable (() -> Unit)? = null,
) {
    RizzoPoster(
        imageUrl = imageUrl,
        onClick = onClick,
        modifier = modifier.rizzoFocusable(onClick = onClick),
        ratio = ratio,
        placeholderColor = placeholderColor,
        content = content,
    )
}
