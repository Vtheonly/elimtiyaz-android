package com.example.ui.designsystem.components.input

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElFieldShape
import com.example.ui.designsystem.theme.ElTheme
import androidx.compose.foundation.layout.Box

/** Renders the optional label above the field. */
@Composable
internal fun FieldLabel(text: String, isError: Boolean) {
    val colors = ElTheme.colors
    Text(
        text = text,
        color = if (isError) colors.danger else colors.textSecondary,
        style = ElTheme.typography.labelMedium,
    )
}

/** Renders the optional leading icon inside the field. */
@Composable
internal fun FieldLeadingIcon(icon: ImageVector?, isError: Boolean) {
    if (icon == null) return
    val colors = ElTheme.colors
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (isError) colors.danger else colors.textSecondary,
        modifier = Modifier.size(18.dp),
    )
    Spacer(Modifier.width(10.dp))
}

/** The actual text input area with placeholder. */
@Composable
internal fun FieldInput(
    value: String,
    placeholder: String?,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    singleLine: Boolean,
    isError: Boolean,
    keyboardOptions: KeyboardOptions,
    visualTransformation: VisualTransformation,
    interactionSource: MutableInteractionSource,
    colors: com.example.ui.designsystem.theme.ElColors,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        if (value.isEmpty() && placeholder != null) {
            Text(
                text = placeholder,
                color = colors.textMuted,
                style = ElTheme.typography.bodyMedium,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = singleLine,
            textStyle = ElTheme.typography.bodyMedium.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(if (isError) colors.danger else colors.primary),
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            interactionSource = interactionSource,
        )
    }
}

/** Renders the optional trailing icon with tap area. */
@Composable
internal fun FieldTrailingIcon(
    icon: ImageVector?,
    isError: Boolean,
    enabled: Boolean,
    onTrailingIconClick: (() -> Unit)?,
) {
    if (icon == null) return
    val colors = ElTheme.colors
    Spacer(Modifier.width(10.dp))
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(ElFieldShape)
            .then(
                if (enabled && onTrailingIconClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTrailingIconClick,
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isError) colors.danger else colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Renders the helper or error text below the field. */
@Composable
internal fun FieldHelper(
    helperText: String?,
    errorText: String?,
    isError: Boolean,
) {
    if (errorText != null && isError) {
        Spacer(Modifier.size(6.dp))
        Text(
            text = errorText,
            color = ElTheme.colors.danger,
            style = ElTheme.typography.bodySmall,
        )
    } else if (helperText != null) {
        Spacer(Modifier.size(6.dp))
        Text(
            text = helperText,
            color = ElTheme.colors.textMuted,
            style = ElTheme.typography.bodySmall,
        )
    }
}
