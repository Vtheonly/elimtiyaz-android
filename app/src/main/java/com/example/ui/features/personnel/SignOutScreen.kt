package com.example.ui.features.personnel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.Session
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.display.ElAvatar
import com.example.ui.designsystem.components.display.ElAvatarSize
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagSize
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.theme.ElTheme
import com.example.ui.features.settings.roleLabel

/**
 * T-460 pass J (issue #3 F-11) — the session-surface consolidation (the
 * owner decision: ProfileScreen is the single full session surface).
 *
 * This surface is now a REDIRECT: the compact session summary (name, email,
 * the French [roleLabel] — not the raw role code) plus the "Ouvrir le
 * profil" CTA. The sign-out action itself lives ONLY on ProfileScreen
 * (its ElDialogShell confirmation + the canonical path:
 * authRepository.signOut() → FCM deactivation BEFORE the JWT revoke →
 * setSession(null)). The previous parallel sign-out button here went
 * through the same repository calls but presented a THIRD session surface —
 * the exact duplication F-11 registered (three surfaces, two visual
 * languages, raw role codes vs French labels).
 *
 * The Settings summary (ProfileCard) remains intentionally — it is a
 * read-only digest, not a management surface.
 */
@Composable
fun SignOutScreen(session: Session, onNavigateToProfile: () -> Unit) {
    val c = ElTheme.colors
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.md),
    ) {
        ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.STANDARD) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(ElTheme.spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ElTheme.spacing.md),
            ) {
                ElAvatar(
                    initials = session.displayName.take(2).uppercase(),
                    icon = null,
                    size = ElAvatarSize.L,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        session.displayName,
                        style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = c.textPrimary,
                    )
                    session.email?.let { email ->
                        Text(email, style = ElTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    Spacer(Modifier.height(ElTheme.spacing.sm))
                    ElTag(
                        text = roleLabel(session.role),
                        tone = ElTagTone.INFO,
                        size = ElTagSize.MD,
                    )
                }
                Icon(
                    Icons.Default.Verified,
                    contentDescription = null,
                    tint = c.success,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Text(
            "La session (informations, permissions, activité et déconnexion) est gérée depuis l'écran Profil.",
            style = ElTheme.typography.bodySmall,
            color = c.textSecondary,
        )

        ElButton(
            text = "Ouvrir le profil",
            onClick = onNavigateToProfile,
            variant = ElButtonVariant.PRIMARY,
            icon = Icons.Default.Verified,
            fullWidth = true,
        )
    }
}
