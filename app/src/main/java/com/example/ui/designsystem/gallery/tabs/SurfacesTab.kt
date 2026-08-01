package com.example.ui.designsystem.gallery.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.card.ElCardVariant
import com.example.ui.designsystem.components.card.ElStatCard
import com.example.ui.designsystem.components.data.ElColumnAlign
import com.example.ui.designsystem.components.data.ElListItem
import com.example.ui.designsystem.components.data.ElTable
import com.example.ui.designsystem.components.data.ElTableColumn
import com.example.ui.designsystem.components.data.ElTableRow
import com.example.ui.designsystem.components.display.ElAvatar
import com.example.ui.designsystem.components.display.ElAvatarSize
import com.example.ui.designsystem.components.feedback.ElEmptyState
import com.example.ui.designsystem.components.feedback.ElLinearProgress
import com.example.ui.designsystem.components.feedback.ElLoadingBlock
import com.example.ui.designsystem.components.feedback.ElSkeletonCard
import com.example.ui.designsystem.components.feedback.ElSpinner
import com.example.ui.designsystem.gallery.GallerySection
import com.example.ui.designsystem.theme.ElColors
import com.example.ui.designsystem.theme.ElTheme

/** Surfaces tab — cards, stat cards, lists, avatars, table, progress, empty state. */
fun LazyListScope.surfacesTab(c: ElColors) {
    item { CardVariantsSection(c) }
    item { StatCardSection(c) }
    item { ListItemsSection(c) }
    item { AvatarsSection(c) }
    item { TableSection() }
    item { ProgressLoadingSection(c) }
    item { EmptyStateSection(c) }
}

@Composable
private fun CardVariantsSection(c: ElColors) {
    GallerySection(title = "Card Variants") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ElCard(variant = ElCardVariant.ELEVATED) {
                Text("Elevated card — default for most surfaces.", color = c.textPrimary, style = ElTheme.typography.bodyMedium)
            }
            ElCard(variant = ElCardVariant.OUTLINED) {
                Text("Outlined card — structural, flat.", color = c.textPrimary, style = ElTheme.typography.bodyMedium)
            }
            ElCard(variant = ElCardVariant.FILLED) {
                Text("Filled card — secondary grouping.", color = c.textPrimary, style = ElTheme.typography.bodyMedium)
            }
            ElCard(variant = ElCardVariant.GRADIENT) {
                Text("Gradient card — hero / premium content.", color = c.textOnColor, style = ElTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun StatCardSection(c: ElColors) {
    GallerySection(title = "Stat Card") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ElStatCard(
                label = "Outstanding", value = "DZD 248K",
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Warning,
                trend = "+12% vs last month", trendPositive = false,
            )
            ElStatCard(
                label = "Collected", value = "DZD 1.2M",
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Check,
                trend = "+8% vs last month", trendPositive = true,
                accentColor = c.success,
            )
        }
    }
}

@Composable
private fun ListItemsSection(c: ElColors) {
    GallerySection(title = "List Items") {
        ElCard(variant = ElCardVariant.OUTLINED, size = ElCardSize.COMPACT) {
            ElListItem(
                title = "Amira Bensaïd", subtitle = "Grade 6-B · Parent: Karim B.",
                leadingInitials = "AB", leadingTint = c.primary,
                trailingText = "Paid", showDivider = true,
            )
            ElListItem(
                title = "Yacine Larbi", subtitle = "Grade 4-A · Parent: Nadia L.",
                leadingInitials = "YL", leadingTint = c.warning,
                trailingBadge = "OVERDUE", showDivider = true,
            )
            ElListItem(
                title = "Mehdi Toumi", subtitle = "Grade 9-C · Parent: Sofiane T.",
                leadingInitials = "MT", leadingTint = c.success,
                trailingText = "Paid", showDivider = false,
            )
        }
    }
}

@Composable
private fun AvatarsSection(c: ElColors) {
    GallerySection(title = "Avatars") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ElAvatar(initials = "AB", size = ElAvatarSize.S, accentColor = c.primary)
            ElAvatar(initials = "YL", size = ElAvatarSize.M, accentColor = c.warning, statusDot = c.success)
            ElAvatar(icon = Icons.Default.Person, size = ElAvatarSize.L, accentColor = c.tertiary)
            ElAvatar(initials = "MT", size = ElAvatarSize.XL, accentColor = c.success)
        }
    }
}

@Composable
private fun TableSection() {
    GallerySection(title = "Table") {
        ElTable(
            columns = listOf(
                ElTableColumn("Student", 2f),
                ElTableColumn("Grade", 1f, ElColumnAlign.CENTER),
                ElTableColumn("Balance", 1.2f, ElColumnAlign.END, sortable = true),
            ),
            rows = listOf(
                ElTableRow("1", listOf("Amira Bensaïd", "6-B", "DZD 0")),
                ElTableRow("2", listOf("Yacine Larbi", "4-A", "DZD 12,400")),
                ElTableRow("3", listOf("Mehdi Toumi", "9-C", "DZD 0")),
            ),
            sortColumn = 2,
            sortAscending = false,
            onSortToggle = {},
        )
    }
}

@Composable
private fun ProgressLoadingSection(c: ElColors) {
    GallerySection(title = "Progress & Loading") {
        val p by remember { mutableStateOf(0.65f) }
        ElLinearProgress(progress = p)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ElSpinner(size = 24)
            ElSpinner(size = 32, color = c.success)
            ElLoadingBlock(message = "Syncing…")
        }
        Spacer(Modifier.height(12.dp))
        ElSkeletonCard()
    }
}

@Composable
private fun EmptyStateSection(c: ElColors) {
    GallerySection(title = "Empty State") {
        ElCard(variant = ElCardVariant.OUTLINED) {
            ElEmptyState(
                title = "No students found",
                subtitle = "Try adjusting your search filters or register a new student.",
                icon = Icons.Default.Inbox,
                actionLabel = "Register Student",
                onAction = {},
            )
        }
    }
}
