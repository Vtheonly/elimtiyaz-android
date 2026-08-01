package com.example.ui.features.crm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Whatsapp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.domain.model.Parent
import com.example.ui.components.ElAvatar
import com.example.ui.components.ElCard
import com.example.ui.components.ElInfoRow
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElTopBar
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.elDesignTokens

@Composable
fun ParentDetailScreen(
    parentId: String,
    onBack: () -> Unit,
    viewModel: ParentDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(parentId) { viewModel.load(parentId) }
    val parent by viewModel.parent.collectAsState()
    val children by viewModel.children.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val error by viewModel.error.collectAsState()
    val context = LocalContext.current
    val tokens = elDesignTokens()

    Scaffold(
        topBar = { ElTopBar(title = parent?.fullName ?: "Parent", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            parent?.let { p ->
                ElCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ElAvatar(initials = p.fullName, size = 56)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(p.fullName, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                                Text(p.code, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(tokens.successBrush)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                                                data = android.net.Uri.parse("tel:${p.phone}")
                                            }
                                            context.startActivity(intent)
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Appeler", color = Color.White, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(tokens.successBrush)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            val cleanPhone = (p.whatsapp ?: p.phone).replace("[^0-9]".toRegex(), "")
                                            val formatted = if (cleanPhone.startsWith("0")) "213${cleanPhone.substring(1)}" else cleanPhone
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                data = android.net.Uri.parse("https://wa.me/$formatted")
                                            }
                                            context.startActivity(intent)
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Whatsapp, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("WhatsApp", color = Color.White, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                                }
                            }
                        }
                    }
                }

                ElCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ElSectionHeader(title = "Contact")
                        Spacer(Modifier.height(4.dp))
                        ElInfoRow(label = "Code", value = p.code)
                        ElInfoRow(label = "Téléphone", value = p.phone)
                        p.email?.let { ElInfoRow(label = "Email", value = it) }
                        p.address?.let { ElInfoRow(label = "Adresse", value = it) }
                        p.occupation?.let { ElInfoRow(label = "Profession", value = it) }
                    }
                }
            }

            summary?.let { s ->
                ElCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ElSectionHeader(title = "Finances")
                        Spacer(Modifier.height(4.dp))
                        ElInfoRow(label = "Total facturé", value = "${(s.totalCharged / 100).formatDzd()} DZD")
                        ElInfoRow(label = "Total payé", value = "${(s.totalPaid / 100).formatDzd()} DZD", valueColor = SuccessGreen)
                        ElInfoRow(label = "Solde", value = "${(s.totalOutstanding / 100).formatDzd()} DZD")
                        if (s.totalOverdue > 0) {
                            ElInfoRow(label = "En retard", value = "${(s.totalOverdue / 100).formatDzd()} DZD", valueColor = DangerRed)
                        }
                    }
                }
            }

            ElCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ElSectionHeader(title = "Enfants (${children.size})")
                    children.forEach { kid ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ElAvatar(initials = kid.fullName, size = 36)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(kid.fullName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                Text(kid.gradeLevel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
