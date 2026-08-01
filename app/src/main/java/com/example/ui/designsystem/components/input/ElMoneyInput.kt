package com.example.ui.designsystem.components.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.foundation.elMoneyFormat
import com.example.ui.designsystem.foundation.elMoneyParse
import com.example.ui.designsystem.theme.ElFieldShape
import com.example.ui.designsystem.theme.ElTheme

/**
 * Money-formatted text input — stores and emits the amount as Long centimes,
 * while showing a localized, human-readable "1 234,56 DZD" string to the user.
 *
 * The field has two visual states:
 *  - **Focused (editing):** shows the raw decimal "1 234,56" without currency
 *    suffix so the user can edit cleanly.
 *  - **Unfocused (display):** shows the formatted "1 234,56 DZD" string via
 *    [elMoneyFormat].
 *
 * The field accepts the French/Algerian conventions (thin-space thousands,
 * comma decimal). It also tolerates the English conventions when pasting —
 * see [elMoneyParse].
 *
 * @param amount          Current amount in centimes (1 DZD = 100 centimes).
 * @param onAmountChange  Receives the new amount in centimes.
 * @param currency        ISO 4217 currency code appended in display state.
 * @param label           Field label, defaults to "Amount".
 * @param modifier        Outer modifier.
 * @param error           Optional error string rendered below the field.
 */
@Composable
fun ElMoneyInput(
    amount: Long,
    onAmountChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    currency: String = "DZD",
    label: String = "Amount",
    error: String? = null,
) {
    val c = ElTheme.colors
    val isError = error != null

    // Edit buffer holds the user's raw text. We seed it from `amount` whenever
    // the field gains focus (lazy re-sync). The display text switches between
    // the buffer (focused) and the canonical formatted string (unfocused).
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()
    var buffer by remember(amount) {
        mutableStateOf(elMoneyFormat(amount, currency, showCurrency = false))
    }

    val displayText = if (isFocused) {
        buffer
    } else {
        elMoneyFormat(amount, currency, showCurrency = true)
    }

    Column(modifier = modifier) {
        Text(
            text = label,
            color = if (isError) c.danger else c.textSecondary,
            style = ElTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(6.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .clip(ElFieldShape)
                .background(c.surfaceVariant)
                .border(
                    width = if (isFocused) ElTheme.borders.thick else ElTheme.borders.thin,
                    color = when {
                        isError -> c.danger
                        isFocused -> c.primary
                        else -> c.outline
                    },
                    shape = ElFieldShape,
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (displayText.isEmpty()) {
                    Text(
                        text = "0,00",
                        color = c.textMuted,
                        style = ElTheme.typography.bodyMedium,
                    )
                }
                BasicTextField(
                    value = displayText,
                    onValueChange = { typed ->
                        buffer = typed
                        onAmountChange(elMoneyParse(typed))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = ElTheme.typography.bodyMedium.copy(color = c.textPrimary),
                    cursorBrush = SolidColor(if (isError) c.danger else c.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    interactionSource = interaction,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = currency,
                color = c.textMuted,
                style = ElTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (isError && error != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = error,
                color = c.danger,
                style = ElTheme.typography.bodySmall,
            )
        }
    }
}
