package com.example.ui.features.personnel

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.Session
import com.example.domain.model.Personnel
import com.example.ui.components.ElAvatar
import com.example.ui.components.ElButton
import com.example.ui.components.ElButtonStyle
import com.example.ui.components.ElCard
import com.example.ui.components.ElEmptyState
import com.example.ui.components.ElScrollableTabRow
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElTag
import com.example.ui.theme.PrimaryBlue

@Composable
fun EmployeeDirectoryScreen(
    session: Session,
    viewModel: EmployeeDirectoryViewModel = hiltViewModel(),
) {
    val personnel by viewModel.personnel.collectAsState()
    val context = LocalContext.current

    var selectedCategoryTab by remember { mutableIntStateOf(0) }
    val categories = remember(personnel) {
        val distinct = personnel.map { it.staffCategory }.distinct().sorted()
        listOf("Tous") + distinct
    }
    val filteredStaff = remember(selectedCategoryTab, personnel) {
        if (selectedCategoryTab == 0) personnel
        else personnel.filter { it.staffCategory == categories[selectedCategoryTab] }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ElSectionHeader(title = "Registre du Personnel (${personnel.size})")

        if (categories.size > 1) {
            ElScrollableTabRow(
                tabs = categories,
                selectedTabIndex = selectedCategoryTab,
                onTabSelected = { selectedCategoryTab = it },
            )
        }

        if (filteredStaff.isEmpty()) {
            ElEmptyState(
                icon = Icons.Default.Phone,
                title = "Aucun personnel",
                message = "Aucun employé enregistré. Ajoutez-en depuis les paramètres ou contactez un administrateur.",
            )
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(filteredStaff) { staff ->
                ElCard(modifier = Modifier.fillMaxWidth(), compact = true) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ElAvatar(initials = staff.fullName, size = 44)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        staff.fullName,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp,
                                        ),
                                    )
                                    Text(
                                        staff.position,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            ElTag(text = staff.staffCategory, color = PrimaryBlue)
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Téléphone: ${staff.phone} · Embauché: ${staff.hireDate.take(10)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ElButton(
                                text = "Appeler",
                                onClick = {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${staff.phone}"))
                                    runCatching { context.startActivity(intent) }
                                },
                                style = ElButtonStyle.Secondary,
                                icon = Icons.Default.Phone,
                                modifier = Modifier.weight(1f),
                            )
                            ElButton(
                                text = "Email",
                                onClick = {
                                    staff.email?.let { email ->
                                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
                                        runCatching { context.startActivity(intent) }
                                    }
                                },
                                style = ElButtonStyle.Secondary,
                                icon = Icons.Default.Email,
                                modifier = Modifier.weight(1f),
                                enabled = !staff.email.isNullOrBlank(),
                            )
                        }
                    }
                }
            }
        }
    }
}
