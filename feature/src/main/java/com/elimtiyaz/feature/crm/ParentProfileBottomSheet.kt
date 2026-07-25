package com.elimtiyaz.feature.crm

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.WhatsApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.common.TenancyTier
import com.elimtiyaz.core.designsystem.ElimtiyazColors
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.AvatarCircle
import com.elimtiyaz.core.ui.ElImtiyazCard
import com.elimtiyaz.domain.model.Parent

/**
 * Reusable bottom-sheet that previews a parent's compact info without leaving
 * the current screen. Used by the financials module (and others) so users can
 * peek at a parent's identity + contact details before deciding to deep-link
 * into the full Parent Detail screen.
 *
 * The caller is responsible for loading the [parent]; this composable is purely
 * presentational and triggers the dial / WhatsApp / email intents directly.
 *
 * @param parent The parent to display (already loaded by the caller's VM).
 * @param onDismissRequest Called when the user wants to close the sheet.
 * @param onOpenDetail Optional "voir le profil complet" action — typically
 *  navigates to Route.ParentDetail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentProfileBottomSheet(
    parent: Parent,
    onDismissRequest: () -> Unit,
    onOpenDetail: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ElimtiyazSpacing.x6, vertical = ElimtiyazSpacing.x4),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AvatarCircle(
                initial = Formatters.initials(parent.firstName, parent.lastName),
                size = 72,
                backgroundColor = ElimtiyazColors.PrimaryBlue,
            )
            Spacer(Modifier.height(ElimtiyazSpacing.x3))
            Text(
                text = Formatters.fullName(parent.firstName, parent.lastName),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = parent.code,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(ElimtiyazSpacing.x3))

            // Compact contact grid.
            ElImtiyazCard {
                Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
                    InfoLine("Téléphone", parent.phone)
                    InfoLine("WhatsApp", parent.whatsapp ?: "—")
                    InfoLine("E-mail", parent.email ?: "—")
                    InfoLine("Profession", parent.occupation ?: "—")
                    InfoLine("Adresse", parent.address ?: "—")
                    val tier = TenancyTier.from(parent.cityTier)
                    InfoLine("Zone", tier?.displayFr ?: parent.cityTier ?: "—")
                    InfoLine("Langue", parent.preferredLanguage)
                    InfoLine("Élèves", "${parent.students.size} enfant(s)")
                }
            }

            Spacer(Modifier.height(ElimtiyazSpacing.x4))

            // Quick contact actions row.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ContactAction(Icons.Outlined.Call, "Appeler") {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${parent.phone}"))
                    context.startActivity(intent)
                }
                ContactAction(Icons.Outlined.WhatsApp, "WhatsApp") {
                    val phone = parent.whatsapp ?: parent.phone
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone"))
                    context.startActivity(intent)
                }
                ContactAction(Icons.Outlined.Mail, "E-mail") {
                    val email = parent.email ?: return@ContactAction
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
                    context.startActivity(intent)
                }
            }

            if (onOpenDetail != null) {
                Spacer(Modifier.height(ElimtiyazSpacing.x3))
                TextButton(onClick = {
                    onDismissRequest()
                    onOpenDetail()
                }) {
                    Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(ElimtiyazSpacing.x2))
                    Text("Voir le profil complet")
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ElimtiyazSpacing.x1),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ContactAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            onClick = onClick,
            modifier = Modifier.size(48.dp),
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.height(ElimtiyazSpacing.x1))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}


