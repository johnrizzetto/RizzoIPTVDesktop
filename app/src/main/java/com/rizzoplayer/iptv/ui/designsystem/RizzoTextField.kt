package com.rizzoplayer.iptv.ui.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rizzoplayer.iptv.ui.theme.RizzoAccent
import com.rizzoplayer.iptv.ui.theme.RizzoMotion
import com.rizzoplayer.iptv.ui.theme.RizzoRadii
import com.rizzoplayer.iptv.ui.theme.RizzoSpacing
import com.rizzoplayer.iptv.ui.theme.RizzoSurfaceVariant
import com.rizzoplayer.iptv.ui.theme.RizzoTextPrimary
import com.rizzoplayer.iptv.ui.theme.RizzoTextSecondary
import com.rizzoplayer.iptv.ui.theme.RizzoTextTertiary

/**
 * RizzoIPTV text input field — TV-safe, D-pad compatible.
 *
 * Unlike phone-oriented text fields, this component:
 * - Handles D-pad navigation (no soft keyboard required)
 * - Uses [MutableInteractionSource] for focus tracking
 * - Shows accent border on focus
 * - Supports optional leading icon and trailing action
 * - Password masking via [VisualTransformation]
 *
 * @param value          Current text value
 * @param onValueChange  Called when text changes
 * @param onSubmit       Called on D-pad Select / IME action (default: IME action Done)
 * @param label          Optional floating label shown above the field
 * @param placeholder    Hint text shown when field is empty
 * @param modifier       Standard modifier
 * @param enabled        Whether the field is interactive
 * @param singleLine     If true (default), only one line; otherwise wraps
 * @param visualTransform For password masking etc. (default: none)
 * @param keyboardType   Keyboard type hint (default: Text)
 * @param imeAction      IME action shown on keyboard (default: Done)
 */
@Composable
@Stable
fun RizzoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit = {},
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val borderColor = when {
        !enabled -> RizzoSurfaceVariant
        isFocused -> RizzoAccent
        else -> RizzoTextTertiary.copy(alpha = 0.4f)
    }

    val textColor = when {
        !enabled -> RizzoTextTertiary.copy(alpha = 0.5f)
        else -> RizzoTextPrimary
    }

    val placeholderColor = RizzoTextTertiary

    val borderWidth = if (isFocused) 2.dp else 1.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RizzoRadii.Sm))
            .background(RizzoSurfaceVariant)
            .border(borderWidth, borderColor, RoundedCornerShape(RizzoRadii.Sm)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = RizzoSpacing.Md, vertical = RizzoSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                leadingIcon()
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = RizzoSpacing.Sm),
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    visualTransformation = visualTransformation,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = imeAction,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onSubmit() },
                        onNext = { onSubmit() },
                        onSearch = { onSubmit() },
                    ),
                    textStyle = TextStyle(
                        color = textColor,
                        fontSize = 16.sp,
                    ),
                    cursorBrush = SolidColor(RizzoAccent),
                    interactionSource = interactionSource,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Placeholder overlay
                if (value.isEmpty() && placeholder != null) {
                    Text(
                        text = placeholder,
                        color = placeholderColor,
                        fontSize = 16.sp,
                    )
                }
            }

            if (trailingIcon != null) {
                trailingIcon()
            }
        }
    }
}

/**
 * Convenience overload with a label shown above the field.
 */
@Composable
@Stable
fun RizzoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onSubmit: () -> Unit = {},
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    Box(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                color = RizzoTextSecondary,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(bottom = RizzoSpacing.Xs),
            )
        }
        RizzoTextField(
            value = value,
            onValueChange = onValueChange,
            onSubmit = onSubmit,
            modifier = Modifier.padding(top = if (label != null) RizzoSpacing.Md else 0.dp),
            placeholder = placeholder,
            enabled = enabled,
            singleLine = singleLine,
            visualTransformation = visualTransformation,
            keyboardType = keyboardType,
            imeAction = imeAction,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
        )
    }
}
