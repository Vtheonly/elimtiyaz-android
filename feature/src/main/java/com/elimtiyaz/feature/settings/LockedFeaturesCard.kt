package com.elimtiyaz.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.rbac.AccessState
import com.elimtiyaz.core.rbac.FeatureNode
import com.elimtiyaz.core.rbac.FeatureRegistry
import com.elimtiyaz.core.rbac.PermanentState
import com.elimtiyaz.core.rbac.accessStateOf
import com.elimtiyaz.core.ui.ElImtiyazCard

/**
 * A reference card that lists all permanently-disabled features (removed AI
 * assistant + desktop-only features). Each row is rendered greyed-out with a
 * lock icon — exactly the visual treatment the user requested for disabled
 * features.
 *
 * This card is the canonical example of how the gating system renders
 * "visible but locked" UI. Other hubs can use the same pattern via
 * [com.elimtiyaz.core.rbac.GatedContent] for individual affordances.
 *
 * Display this card on the Settings screen so users can see what's not
 * available and why.
 */
@Composable
fun LockedFeaturesCard(
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "Fonctionnalités verrouillées",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.size(ElimtiyazSpacing.x2))
        Text(
            "Les fonctionnalités suivantes sont visibles mais actuellement indisponibles " +
                "sur mobile. Certaines sont réservées au terminal de bureau, d'autres ont été " +
                "retirées de cette version.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(ElimtiyazSpacing.x3))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
            modifier = Modifier.heightInMax(),
        ) {
            items(FeatureRegistry.PermanentlyDisabled) { node ->
                LockedFeatureRow(node)
            }
        }
    }
}

@Composable
private fun LockedFeatureRow(node: FeatureNode) {
    val state = accessStateOf(node)
    val reason = (state as? AccessState.Disabled)?.reason?.displayFr
        ?: (node.requirement.permanent?.displayFr ?: "Indisponible")
    ElImtiyazCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ElimtiyazSpacing.x3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .alpha(0.4f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.alpha(0.5f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = node.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Helper to apply a max height to LazyColumn without importing window-info. */
@Composable
private fun Modifier.heightInMax(): Modifier = this.then(
    Modifier.heightIn(max = 320.dp),
)
