package com.rizzoplayer.iptv.ui.designsystem

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rizzoplayer.iptv.ui.theme.RizzoTextPrimary
import com.rizzoplayer.iptv.ui.theme.RizzoTextSecondary
import com.rizzoplayer.iptv.ui.theme.RizzoTextTertiary

/**
 * RizzoIPTV typography — sealed hierarchy of named text styles.
 * All styles use the tokens from [RizzoTypography].
 *
 * Usage:
 * ```
 * RizzoText(style = RizzoText.DisplayLg, text = "Hello")
 * RizzoText(style = RizzoText.BodyMd, color = RizzoTextSecondary, text = "Subtitle")
 * ```
 */
sealed class RizzoTextStyle(
    val fontSize: Int,
    val lineHeight: Int,
    val fontWeight: FontWeight,
    val color: Color,
) {
    data object DisplayLg : RizzoTextStyle(
        fontSize = 48, lineHeight = 56, fontWeight = FontWeight.Bold,
        color = RizzoTextPrimary,
    )
    data object DisplayMd : RizzoTextStyle(
        fontSize = 40, lineHeight = 48, fontWeight = FontWeight.Bold,
        color = RizzoTextPrimary,
    )
    data object HeadingLg : RizzoTextStyle(
        fontSize = 32, lineHeight = 40, fontWeight = FontWeight.SemiBold,
        color = RizzoTextPrimary,
    )
    data object HeadingMd : RizzoTextStyle(
        fontSize = 28, lineHeight = 36, fontWeight = FontWeight.SemiBold,
        color = RizzoTextPrimary,
    )
    data object HeadingSm : RizzoTextStyle(
        fontSize = 22, lineHeight = 28, fontWeight = FontWeight.SemiBold,
        color = RizzoTextPrimary,
    )
    data object BodyLg : RizzoTextStyle(
        fontSize = 18, lineHeight = 28, fontWeight = FontWeight.Normal,
        color = RizzoTextPrimary,
    )
    data object BodyMd : RizzoTextStyle(
        fontSize = 16, lineHeight = 24, fontWeight = FontWeight.Normal,
        color = RizzoTextPrimary,
    )
    data object BodySm : RizzoTextStyle(
        fontSize = 14, lineHeight = 20, fontWeight = FontWeight.Normal,
        color = RizzoTextSecondary,
    )
    data object LabelLg : RizzoTextStyle(
        fontSize = 14, lineHeight = 20, fontWeight = FontWeight.Medium,
        color = RizzoTextPrimary,
    )
    data object LabelMd : RizzoTextStyle(
        fontSize = 12, lineHeight = 16, fontWeight = FontWeight.Medium,
        color = RizzoTextSecondary,
    )
    data object Caption : RizzoTextStyle(
        fontSize = 11, lineHeight = 16, fontWeight = FontWeight.Normal,
        color = RizzoTextTertiary,
    )
}

/**
 * RizzoIPTV text composable.
 *
 * @param style  Which typography style to apply (DisplayLg … Caption)
 * @param text  The string content
 * @param modifier Standard modifier
 * @param color  Override text color. Defaults to the style's own color.
 * @param maxLines  Max lines (default 1). Pass null for unlimited.
 * @param lineHeightOverride Override line height in sp (optional)
 */
@Composable
@Stable
fun RizzoText(
    style: RizzoTextStyle,
    text: String,
    modifier: Modifier = Modifier,
    color: Color = style.color,
    maxLines: Int? = 1,
    lineHeightOverride: Int? = null,
) {
    Text(
        text = text,
        style = TextStyle(
            fontSize = style.fontSize.sp,
            lineHeight = (lineHeightOverride ?: style.lineHeight).sp,
            fontWeight = style.fontWeight,
            color = color,
        ),
        maxLines = maxLines ?: Int.MAX_VALUE,
        modifier = modifier,
    )
}
