package com.example.ui.features.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.Session
import com.example.ui.designsystem.components.display.ElAvatar
import com.example.ui.designsystem.components.display.ElAvatarSize
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.theme.ElTheme

@Composable
internal fun ProfileCard(session: Session?) {
    val c = ElTheme.colors
    if (session == null) return
    ElCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(ElTheme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ElAvatar(
                initials = session.displayName.take(2).uppercase(),
                size = ElAvatarSize.L,
            )
            Spacer(Modifier.width(ElTheme.spacing.lg))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.displayName,
                    style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = c.textPrimary,
                )
                Text(
                    text = session.email,
                    style = ElTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
                Spacer(Modifier.height(ElTheme.spacing.sm))
                ElTag(
                    text = roleLabel(session.role),
                    tone = roleTone(session.role),
                )
            }
        }
    }
}
