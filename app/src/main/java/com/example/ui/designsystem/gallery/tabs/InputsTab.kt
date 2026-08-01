@file:OptIn(ExperimentalLayoutApi::class)

package com.example.ui.designsystem.gallery.tabs

import androidx.compose.foundation.layout.ExperimentalLayoutApi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import com.example.ui.designsystem.components.display.ElBadge
import com.example.ui.designsystem.components.display.ElBadgeStyle
import com.example.ui.designsystem.components.display.ElBadgeTone
import com.example.ui.designsystem.components.display.ElChip
import com.example.ui.designsystem.components.display.ElChipVariant
import com.example.ui.designsystem.components.input.ElDropdown
import com.example.ui.designsystem.components.input.ElDropdownOption
import com.example.ui.designsystem.components.input.ElTextField
import com.example.ui.designsystem.gallery.GallerySection
import com.example.ui.designsystem.theme.ElColors

/** Inputs tab — text fields, dropdowns, chips, badges. */
fun LazyListScope.inputsTab(@Suppress("UNUSED_PARAMETER") c: ElColors) {
    item { TextFieldsSection() }
    item { DropdownsSection() }
    item { ChipsSection() }
    item { BadgesSection() }
}

@Composable
private fun TextFieldsSection() {
    GallerySection(title = "Text Fields") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            var text1 by remember { mutableStateOf("") }
            var text2 by remember { mutableStateOf("Parent Name") }
            var text3 by remember { mutableStateOf("abc") }
            ElTextField(
                value = text1, onValueChange = { text1 = it },
                label = "Email", placeholder = "you@school.edu",
                leadingIcon = Icons.Default.Email,
            )
            ElTextField(
                value = text2, onValueChange = { text2 = it },
                label = "Name", leadingIcon = Icons.Default.Person,
                trailingIcon = Icons.Default.Search, onTrailingIconClick = {},
            )
            ElTextField(
                value = text3, onValueChange = { text3 = it },
                label = "Password", leadingIcon = Icons.Default.Lock,
                isError = true, errorText = "Too short — minimum 8 characters.",
            )
        }
    }
}

@Composable
private fun DropdownsSection() {
    GallerySection(title = "Dropdowns") {
        var selected by remember { mutableStateOf<String?>("financial") }
        ElDropdown(
            options = listOf(
                ElDropdownOption("admin", "SuperAdmin", Icons.Default.Person),
                ElDropdownOption("financial", "Financial Officer", Icons.Default.ShoppingCart),
                ElDropdownOption("teacher", "Teacher", Icons.Default.Person),
            ),
            selectedValue = selected,
            onSelected = { selected = it.value },
            label = "Role",
        )
    }
}

@Composable
private fun ChipsSection() {
    GallerySection(title = "Chips") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ElChip("Assist", onClick = {})
            ElChip("Filter", variant = ElChipVariant.FILTER, selected = true, onClick = {})
            ElChip("Filter", variant = ElChipVariant.FILTER, selected = false, onClick = {})
            ElChip("Input", variant = ElChipVariant.INPUT, onDismiss = {}, icon = Icons.Default.Person)
            ElChip("Choice", variant = ElChipVariant.CHOICE, selected = true, onClick = {})
        }
    }
}

@Composable
private fun BadgesSection() {
    GallerySection(title = "Badges") {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ElBadge("Primary", tone = ElBadgeTone.PRIMARY, style = ElBadgeStyle.SOLID)
            ElBadge("Success", tone = ElBadgeTone.SUCCESS, style = ElBadgeStyle.SOFT, dot = true)
            ElBadge("Warning", tone = ElBadgeTone.WARNING, style = ElBadgeStyle.SOFT)
            ElBadge("Danger", tone = ElBadgeTone.DANGER, style = ElBadgeStyle.OUTLINED)
            ElBadge("Info", tone = ElBadgeTone.INFO, style = ElBadgeStyle.SOLID, icon = Icons.Default.Email)
        }
    }
}
