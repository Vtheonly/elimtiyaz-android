package com.elimtiyaz.feature.crm

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.WhatsApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.elimtiyaz.app.navigation.Route
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.common.PaymentMethod
import com.elimtiyaz.core.common.PaymentStatus
import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.common.TenancyTier
import com.elimtiyaz.core.designsystem.ElimtiyazColors
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.AvatarCircle
import com.elimtiyaz.core.ui.ElImtiyazCard
import com.elimtiyaz.core.ui.EmptyState
import com.elimtiyaz.core.ui.ErrorState
import com.elimtiyaz.core.ui.LoadingState
import com.elimtiyaz.core.ui.StatusChip
import com.elimtiyaz.core.ui.StatusTone
import com.elimtiyaz.domain.model.Installment
import com.elimtiyaz.domain.model.Parent
import com.elimtiyaz.domain.model.Payment
import com.elimtiyaz.domain.model.Student
import kotlinx.coroutines.launch

/**
 * ParentDetailScreen — full profile of a parent (Route.ParentDetail).
 *
 * Layout: TopAppBar (back / edit / delete) → scrollable content with header
 * card, quick action row, associated students, recent payments, installments
 * table. A FAB opens the "Ajustement de compte" bottom sheet (gated by
 * [Permission.AdjustAccount]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDetailScreen(
    nav: NavController,
    vm: ParentDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val canEdit = session?.can(Permission.EditParent) ?: false
    val canDelete = session?.can(Permission.DeleteParent) ?: false
    val canAdjust = session?.can(Permission.AdjustAccount) ?: false

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAdjustSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.parent?.code ?: "Parent") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    if (canEdit) {
                        IconButton(onClick = {
                            scope.launch {
                                snackbar.showSnackbar(
                                    "La modification du parent sera disponible dans une prochaine version.",
                                )
                            }
                        }) { Icon(Icons.Outlined.Edit, contentDescription = "Modifier") }
                    }
                    if (canDelete) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Supprimer")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (canAdjust && state.parent != null) {
                ExtendedFloatingActionButton(
                    onClick = { showAdjustSheet = true },
                    icon = { Icon(Icons.Outlined.Payments, contentDescription = null) },
                    text = { Text("Ajustement") },
                )
            }
        },
    ) { inner ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(inner))
            state.error != null -> ErrorState(state.error!!, onRetry = vm::reload, modifier = Modifier.padding(inner))
            state.parent == null -> EmptyState(
                title = "Parent introuvable",
                description = "Ce parent n'existe plus ou l'identifiant est invalide.",
                modifier = Modifier.padding(inner),
            )
            else -> ParentDetailContent(
                parent = state.parent!!,
                students = state.students,
                recentPayments = state.recentPayments,
                installments = state.installments,
                contentPadding = inner,
                onStudentClick = { nav.navigate(Route.StudentDetail.build(it)) },
                onCall = {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${state.parent!!.phone}"))
                    context.startActivity(intent)
                },
                onWhatsApp = {
                    val phone = state.parent!!.whatsapp ?: state.parent!!.phone
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone"))
                    context.startActivity(intent)
                },
                onEmail = {
                    val email = state.parent!!.email ?: return@ParentDetailContent
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
                    context.startActivity(intent)
                },
                onFinancialProfile = { nav.navigate(Route.Installments.build(state.parent!!.id)) },
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Supprimer le parent") },
            text = {
                val name = state.parent?.let { Formatters.fullName(it.firstName, it.lastName) } ?: ""
                Text("Confirmer la suppression de $name ? Cette action est irréversible.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    vm.deleteParent { ok, msg ->
                        scope.launch {
                            if (ok) nav.popBackStack() else snackbar.showSnackbar(msg ?: "Erreur")
                        }
                    }
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Annuler") } },
        )
    }

    if (showAdjustSheet && state.parent != null) {
        AdjustmentBottomSheet(
            parent = state.parent!!,
            onDismiss = { showAdjustSheet = false },
            onSubmit = { amount, reason ->
                vm.adjustAccount(amount, reason) { ok, msg ->
                    scope.launch {
                        if (ok) {
                            showAdjustSheet = false
                            snackbar.showSnackbar("Ajustement appliqué.")
                        } else {
                            snackbar.showSnackbar(msg ?: "Erreur lors de l'ajustement.")
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun ParentDetailContent(
    parent: Parent,
    students: List<Student>,
    recentPayments: List<Payment>,
    installments: List<Installment>,
    contentPadding: PaddingValues,
    onStudentClick: (String) -> Unit,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit,
    onEmail: () -> Unit,
    onFinancialProfile: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = ElimtiyazSpacing.x4,
            end = ElimtiyazSpacing.x4,
            top = contentPadding.calculateTopPadding() + ElimtiyazSpacing.x4,
            bottom = contentPadding.calculateBottomPadding() + ElimtiyazSpacing.x12,
        ),
        verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x4),
    ) {
        item { ParentHeaderCard(parent) }
        item { QuickActionsRow(onCall, onWhatsApp, onEmail, onFinancialProfile) }
        item { HorizontalDivider() }
        item { StudentsSection(students = students, onStudentClick = onStudentClick) }
        item { HorizontalDivider() }
        item { RecentPaymentsSection(payments = recentPayments) }
        item { HorizontalDivider() }
        item { InstallmentsSection(installments = installments) }
    }
}

@Composable
private fun ParentHeaderCard(parent: Parent) {
    val tier = TenancyTier.from(parent.cityTier)
    ElImtiyazCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ElimtiyazSpacing.x6),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AvatarCircle(
                initial = Formatters.initials(parent.firstName, parent.lastName),
                size = 80,
                backgroundColor = ElimtiyazColors.PrimaryBlue,
            )
            Spacer(Modifier.height(ElimtiyazSpacing.x3))
            Text(
                text = Formatters.fullName(parent.firstName, parent.lastName),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = parent.code,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(ElimtiyazSpacing.x4))
            InfoRow("Téléphone", parent.phone)
            InfoRow("WhatsApp", parent.whatsapp ?: "—")
            InfoRow("E-mail", parent.email ?: "—")
            InfoRow("Profession", parent.occupation ?: "—")
            InfoRow("Adresse", parent.address ?: "—")
            InfoRow("Zone", tier?.displayFr ?: parent.cityTier ?: "—")
            InfoRow("Langue préférée", parent.preferredLanguage)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
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
private fun QuickActionsRow(
    onCall: () -> Unit,
    onWhatsApp: () -> Unit,
    onEmail: () -> Unit,
    onFinancialProfile: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        QuickAction(Icons.Outlined.Call, "Appeler", onCall)
        QuickAction(Icons.Outlined.WhatsApp, "WhatsApp", onWhatsApp)
        QuickAction(Icons.Outlined.Mail, "E-mail", onEmail)
        QuickAction(Icons.Outlined.Payments, "Profil financier", onFinancialProfile)
    }
}

@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            onClick = onClick,
            modifier = Modifier.size(56.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(Modifier.height(ElimtiyazSpacing.x2))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun StudentsSection(students: List<Student>, onStudentClick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle("Élèves associés (${students.size})")
        Spacer(Modifier.height(ElimtiyazSpacing.x2))
        if (students.isEmpty()) {
            Text(
                "Aucun élève associé pour le moment.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3),
                contentPadding = PaddingValues(vertical = ElimtiyazSpacing.x1),
            ) {
                items(students, key = { it.id }) { student ->
                    StudentChip(student = student, onClick = { onStudentClick(student.id) })
                }
            }
        }
    }
}

@Composable
private fun StudentChip(student: Student, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick,
        modifier = Modifier.width(180.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ElimtiyazSpacing.x3),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AvatarCircle(
                initial = Formatters.initials(student.firstName, student.lastName),
                size = 48,
                backgroundColor = ElimtiyazColors.DeepBlue,
            )
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Text(
                text = Formatters.fullName(student.firstName, student.lastName),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = student.code,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentPaymentsSection(payments: List<Payment>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle("Paiements récents")
        Spacer(Modifier.height(ElimtiyazSpacing.x2))
        if (payments.isEmpty()) {
            Text(
                "Aucun paiement enregistré.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            payments.forEach { payment -> PaymentRow(payment) }
        }
    }
}

@Composable
private fun PaymentRow(payment: Payment) {
    val method = PaymentMethod.from(payment.method)
    val status = PaymentStatus.from(payment.status)
    val tone = when (status) {
        PaymentStatus.Paid -> StatusTone.Success
        PaymentStatus.Partial -> StatusTone.Warning
        PaymentStatus.Overdue -> StatusTone.Danger
        PaymentStatus.Pending -> StatusTone.Info
        PaymentStatus.Refunded -> StatusTone.Neutral
        PaymentStatus.Cancelled -> StatusTone.Neutral
        null -> StatusTone.Neutral
    }
    ElImtiyazCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ElimtiyazSpacing.x4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = CrmFormat.currency(payment.amount),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(ElimtiyazSpacing.x1))
                Text(
                    text = "${method?.displayFr ?: payment.method} • ${CrmFormat.date(payment.collectedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = payment.receiptNumber,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusChip(label = status?.displayFr ?: payment.status, tone = tone)
        }
    }
}

@Composable
private fun InstallmentsSection(installments: List<Installment>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle("Échéancier")
        Spacer(Modifier.height(ElimtiyazSpacing.x2))
        if (installments.isEmpty()) {
            Text(
                "Aucun échéancier défini.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            installments.forEach { InstallmentRow(it) }
        }
    }
}

@Composable
private fun InstallmentRow(installment: Installment) {
    val status = PaymentStatus.from(installment.status)
    val tone = when (status) {
        PaymentStatus.Paid -> StatusTone.Success
        PaymentStatus.Partial -> StatusTone.Warning
        PaymentStatus.Overdue -> StatusTone.Danger
        PaymentStatus.Pending -> StatusTone.Info
        else -> StatusTone.Neutral
    }
    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = installment.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                StatusChip(label = status?.displayFr ?: installment.status, tone = tone)
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Du", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(CrmFormat.currency(installment.amountDue), style = MaterialTheme.typography.bodyMedium)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Versé", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(CrmFormat.currency(installment.amountPaid), style = MaterialTheme.typography.bodyMedium, color = ElimtiyazColors.SuccessGreen)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Échéance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(CrmFormat.date(installment.dueDate), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdjustmentBottomSheet(
    parent: Parent,
    onDismiss: () -> Unit,
    onSubmit: (Double, String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amountText by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ElimtiyazSpacing.x6),
            verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3),
        ) {
            Text(
                text = "Ajustement de compte",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${Formatters.fullName(parent.firstName, parent.lastName)} • ${parent.code}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '-' || c == '.' } },
                label = { Text("Montant (DZD, négatif = débit)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Motif") },
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (amount == null || amount == 0.0) {
                        error = "Veuillez saisir un montant non nul."
                        return@Button
                    }
                    if (reason.isBlank()) {
                        error = "Veuillez indiquer un motif."
                        return@Button
                    }
                    error = null
                    onSubmit(amount, reason)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Appliquer l'ajustement") }
        }
    }
}
