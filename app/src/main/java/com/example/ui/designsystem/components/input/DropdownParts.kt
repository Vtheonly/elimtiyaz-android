package com.example.ui.designsystem.components.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.designsystem.theme.ElFieldShape
import com.example.ui.designsystem.theme.ElTheme

/** The clickable trigger row that opens the dropdown. */
@Composable
internal fun DropdownTrigger(
    selectedLabel: String?,
    placeholder: String,
    selectedIcon: ImageVector?,
    enabled: Boolean,
    rotation: Float,
    onOpen: () -> Unit,
    interaction: MutableInteractionSource,
) {
    val c = ElTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(ElFieldShape)
            .background(c.surfaceVariant)
            .border(ElTheme.borders.thin, c.outline, ElFieldShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.DropdownList,
                onClick = onOpen,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (selectedIcon != null) {
            Icon(
                imageVector = selectedIcon,
                contentDescription = null,
                tint = c.textSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = selectedLabel ?: placeholder,
            color = if (selectedLabel != null) c.textPrimary else c.textMuted,
            style = ElTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = null,
            tint = c.textSecondary,
            modifier = Modifier.size(20.dp).rotate(rotation),
        )
    }
}

/** The modal popup containing the list of options. */
@Composable
internal fun DropdownPopup(
    options: List<ElDropdownOption>,
    selectedValue: String?,
    onSelected: (ElDropdownOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = ElTheme.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(ElFieldShape)
                .background(c.surface)
                .border(ElTheme.borders.thin, c.outline, ElFieldShape),
        ) {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(options) { opt ->
                    DropdownOptionRow(
                        option = opt,
                        isSelected = opt.value == selectedValue,
                        onSelect = { onSelected(opt) },
                    )
                }
            }
        }
    }
}

/** A single option row inside the popup. */
@Composable
internal fun DropdownOptionRow(
    option: ElDropdownOption,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val c = ElTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelect,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (option.icon != null) {
            Icon(
                imageVector = option.icon,
                contentDescription = null,
                tint = if (isSelected) c.primary else c.textSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = option.label,
            color = if (isSelected) c.primary else c.textPrimary,
            style = ElTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = c.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
