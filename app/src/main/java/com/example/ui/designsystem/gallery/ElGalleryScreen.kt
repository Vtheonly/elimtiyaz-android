package com.example.ui.designsystem.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.components.tabs.ElTabRow
import com.example.ui.designsystem.gallery.tabs.buttonsTab
import com.example.ui.designsystem.gallery.tabs.foundationsTab
import com.example.ui.designsystem.gallery.tabs.inputsTab
import com.example.ui.designsystem.gallery.tabs.overlaysTab
import com.example.ui.designsystem.gallery.tabs.surfacesTab
import com.example.ui.designsystem.theme.ElTheme

private val GALLERY_TABS = listOf("Foundations", "Buttons", "Inputs", "Surfaces", "Overlays")

/**
 * The component gallery — every component in every variant.
 * Wire this to a route in your app for living documentation.
 *
 * The shell renders the header and tab switcher; each tab's content lives in
 * its own file under [com.example.ui.designsystem.gallery.tabs].
 */
@Composable
fun ElGalleryScreen() {
    val c = ElTheme.colors
    var selectedTab by remember { mutableStateOf(0) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background),
    ) {
        item { GalleryHeader() }
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                ElTabRow(
                    tabs = GALLERY_TABS,
                    selectedIndex = selectedTab,
                    onSelected = { selectedTab = it },
                )
            }
        }
        when (selectedTab) {
            0 -> foundationsTab(c)
            1 -> buttonsTab(c)
            2 -> inputsTab(c)
            3 -> surfacesTab(c)
            4 -> overlaysTab(c)
        }
    }
}

/** The "El-Imtiyaz Design System" title block at the top of the gallery. */
@Composable
private fun GalleryHeader() {
    val c = ElTheme.colors
    Column(modifier = Modifier.padding(24.dp)) {
        Text(
            text = "El-Imtiyaz Design System",
            color = c.textPrimary,
            style = ElTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Electric Violet & Sunshine · v2.0",
            color = c.textSecondary,
            style = ElTheme.typography.bodyMedium,
        )
    }
}
