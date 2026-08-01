package com.example.ui.designsystem.components.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.overlays.ElDialogShell
import com.example.ui.designsystem.theme.ElFieldShape
import com.example.ui.designsystem.theme.ElTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Date picker field — renders like a readonly [ElTextField] with a calendar
 * trailing icon. Tapping opens an [ElDialogShell] containing the Material 3
 * [DatePicker].
 *
 * The wire format is ISO-8601 (`yyyy-MM-dd`); the display format is
 * `dd MMM yyyy` for human readability.
 *
 * @param value          ISO date string `yyyy-MM-dd`, or null when empty.
 * @param onValueChange  Receives the new ISO date string (or null when cleared).
 * @param label          Field label, defaults to "Date".
 * @param modifier       Outer modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElDatePicker(
    value: String?,
    onValueChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Date",
) {
    val c = ElTheme.colors
    var open by remember { mutableStateOf(false) }

    // Pre-populate the M3 DatePicker state with the current value, if any.
    val initialMillis = remember(value) {
        if (value.isNullOrBlank()) {
            System.currentTimeMillis()
        } else {
            runCatching {
                LocalDate.parse(value)
                    .atStartOfDay(ZoneId.of("UTC"))
                    .toInstant()
                    .toEpochMilli()
            }.getOrDefault(System.currentTimeMillis())
        }
    }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    val displayText = remember(value) {
        if (value.isNullOrBlank()) ""
        else runCatching {
            LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
        }.getOrDefault(value)
    }

    Column(modifier = modifier) {
        Text(
            text = label,
            color = c.textSecondary,
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
                .border(ElTheme.borders.thin, c.outline, ElFieldShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.DropdownList,
                    onClick = { open = true },
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = displayText.ifBlank { "Select date…" },
                color = if (displayText.isBlank()) c.textMuted else c.textPrimary,
                style = ElTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = c.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }

    if (open) {
        ElDialogShell(
            onDismissRequest = { open = false },
        ) {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                DatePicker(state = datePickerState)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { open = false }) {
                        Text("Cancel", color = c.textSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                val iso = Instant.ofEpochMilli(millis)
                                    .atZone(ZoneId.of("UTC"))
                                    .toLocalDate()
                                    .toString()
                                onValueChange(iso)
                            }
                            open = false
                        },
                    ) {
                        Text("OK", color = c.primary)
                    }
                }
            }
        }
    }
}
