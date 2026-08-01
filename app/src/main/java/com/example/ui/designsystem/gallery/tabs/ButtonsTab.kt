package com.example.ui.designsystem.gallery.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonSize
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.button.ElFab
import com.example.ui.designsystem.components.button.ElIconButton
import com.example.ui.designsystem.gallery.GallerySection
import com.example.ui.designsystem.theme.ElColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart

/** Buttons tab — all variants, sizes, states, icon buttons, FABs. */
fun LazyListScope.buttonsTab(@Suppress("UNUSED_PARAMETER") c: ElColors) {
    item { VariantsSection() }
    item { SizesSection() }
    item { StatesSection() }
    item { IconFabSection() }
}

@Composable
private fun VariantsSection() {
    GallerySection(title = "Variants", description = "Six button variants — same shape, same motion.") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ElButton("Primary CTA", onClick = {}, variant = ElButtonVariant.PRIMARY, icon = Icons.Default.Check)
            ElButton("Secondary", onClick = {}, variant = ElButtonVariant.SECONDARY, icon = Icons.Default.ShoppingCart)
            ElButton("Tonal", onClick = {}, variant = ElButtonVariant.TONAL)
            ElButton("Outlined", onClick = {}, variant = ElButtonVariant.OUTLINED, icon = Icons.Default.Search)
            ElButton("Ghost", onClick = {}, variant = ElButtonVariant.GHOST)
            ElButton("Danger", onClick = {}, variant = ElButtonVariant.DANGER, icon = Icons.Default.Delete)
        }
    }
}

@Composable
private fun SizesSection() {
    GallerySection(title = "Sizes") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ElButton("S", onClick = {}, size = ElButtonSize.SMALL)
            ElButton("M", onClick = {}, size = ElButtonSize.MEDIUM)
            ElButton("L", onClick = {}, size = ElButtonSize.LARGE)
        }
    }
}

@Composable
private fun StatesSection() {
    GallerySection(title = "States") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ElButton("Enabled", onClick = {})
            ElButton("Disabled", onClick = {}, enabled = false)
            ElButton("Loading", onClick = {}, loading = true)
        }
    }
}

@Composable
private fun IconFabSection() {
    GallerySection(title = "Icon Buttons & FAB") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ElIconButton(icon = Icons.Default.Search, onClick = {}, contentDescription = "Search")
            ElIconButton(icon = Icons.Default.Email, onClick = {}, contentDescription = "Email")
            ElFab(icon = Icons.Default.Add, onClick = {}, label = "New")
            ElFab(icon = Icons.Default.Add, onClick = {})
        }
    }
}
