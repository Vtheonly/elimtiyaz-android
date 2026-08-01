package com.example.ui.designsystem.components.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElFieldShape
import com.example.ui.designsystem.theme.ElTheme

/**
 * Unified text field — supports label, leading/trailing icons, helper/error
 * text, and the full state palette. Uses [BasicTextField] for full styling
 * control.
 */
@Composable
fun ElTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    onTrailingIconClick: (() -> Unit)? = null,
    helperText: String? = null,
    errorText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val colors = ElTheme.colors
    val state = resolveFieldState(enabled, isError)

    Column(modifier = modifier) {
        if (label != null) {
            FieldLabel(text = label, isError = isError)
            Spacer(Modifier.height(6.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .clip(ElFieldShape)
                .background(fieldBackground(state, colors.surfaceVariant))
                .border(
                    fieldBorderWidth(state, ElTheme.borders),
                    fieldBorderColor(state, colors),
                    ElFieldShape,
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            FieldLeadingIcon(icon = leadingIcon, isError = isError)
            FieldInput(
                value = value,
                placeholder = placeholder,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                isError = isError,
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
                interactionSource = interactionSource,
                colors = colors,
                modifier = Modifier.weight(1f),
            )
            FieldTrailingIcon(
                icon = trailingIcon,
                isError = isError,
                enabled = enabled,
                onTrailingIconClick = onTrailingIconClick,
            )
        }

        FieldHelper(
            helperText = helperText,
            errorText = errorText,
            isError = isError,
        )
    }
}
