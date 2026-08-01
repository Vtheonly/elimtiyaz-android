package com.example.ui.designsystem.components.input

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.designsystem.theme.ElFieldShape
import com.example.ui.designsystem.theme.ElTheme

/**
 * Unified dropdown / select. Opens a modal popup with options.
 * Matches [ElTextField] visual styling.
 */
@Composable
fun ElDropdown(
    options: List<ElDropdownOption>,
    selectedValue: String?,
    onSelected: (ElDropdownOption) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "Select…",
    enabled: Boolean = true,
) {
    val c = ElTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val selected = options.firstOrNull { it.value == selectedValue }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "dd-arrow")

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                color = c.textSecondary,
                style = ElTheme.typography.labelMedium,
            )
            Spacer(Modifier.size(6.dp))
        }
        DropdownTrigger(
            selectedLabel = selected?.label,
            placeholder = placeholder,
            selectedIcon = selected?.icon,
            enabled = enabled,
            rotation = rotation,
            onOpen = { expanded = true },
            interaction = interaction,
        )
    }

    if (expanded) {
        DropdownPopup(
            options = options,
            selectedValue = selectedValue,
            onSelected = { opt ->
                onSelected(opt)
                expanded = false
            },
            onDismiss = { expanded = false },
        )
    }
}
