package com.example.ui.features.crm

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Whatsapp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.PaymentCategory
import com.example.core.formatDzd
import com.example.domain.model.Parent
import com.example.ui.components.ElAvatar
import com.example.ui.components.ElButton
import com.example.ui.components.ElCard
import com.example.ui.components.ElInfoRow
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElTag
import com.example.ui.components.ElTopBar
import com.example.ui.theme.DangerRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarmGold
import com.example.ui.theme.elDesignTokens
import com.example.ui.util.PhoneUtils

private val ADJUSTMENT_MOTIFS = listOf(
    "Remise fratrie",
    "Remise direction",
    "Bourse / aide sociale",
    "Pénalité de retard",
    "Correction d'erreur de saisie",
    "Autre",
)

@Composable
internal fun ReconLine(
    label: String,
    amount: Long,
    color: Color = MaterialTheme.colorScheme.onSurface,
    bold: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${if (amount < 0L) "−" else if (amount > 0L) "+" else ""}${(kotlin.math.abs(amount) / 100).formatDzd()} DZD",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium),
            color = color,
        )
    }
}

private val ADJUSTMENT_CATEGORIES = listOf(
    PaymentCategory.TUITION to "Scolarité",
    PaymentCategory.TRANSPORT to "Transport",
    PaymentCategory.CANTEEN to "Cantine",
    PaymentCategory.UNIFORM to "Uniforme",
    PaymentCategory.BOOKS to "Livres",
    PaymentCategory.OTHER to "Autre",
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun AdjustAccountDialog(
    outstanding: Long?,
    busy: Boolean,
    onConfirm: (amountCentimes: Long, category: PaymentCategory, reason: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var motif by remember { mutableStateOf(ADJUSTMENT_MOTIFS.first()) }
    var category by remember { mutableStateOf(ADJUSTMENT_CATEGORIES.first().first) }

    val amountDzd = amountText.replace(" ", "").replace(",", ".").toDoubleOrNull()
    val validAmount = amountDzd != null && amountDzd != 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajustement de compte") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (outstanding != null) "Solde en cours : ${(outstanding / 100).formatDzd()} DZD" else "Solde en cours : —",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { raw ->
                        amountText = raw.filter { it.isDigit() || it == '-' }.take(12)
                    },
                    label = { Text("Montant signé (DZD) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Positif = débit (pénalité / majoration) · Négatif = crédit (remise / avoir)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Motif *", style = MaterialTheme.typography.labelMedium)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ADJUSTMENT_MOTIFS.forEach { m ->
                        ElTag(text = m, selected = motif == m, color = PrimaryBlue, onClick = { motif = m })
                    }
                }
                Text("Catégorie (débits uniquement)", style = MaterialTheme.typography.labelMedium)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ADJUSTMENT_CATEGORIES.forEach { (c, label) ->
                        ElTag(text = label, selected = category == c, color = PrimaryBlue, onClick = { category = c })
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optionnel)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val dzd = amountText.replace(" ", "").replace(",", ".").toDoubleOrNull() ?: 0.0
                    val centimes = kotlin.math.round(dzd * 100).toLong()
                    val reason = if (note.isNotBlank()) "$motif — ${note.trim()}" else motif
                    onConfirm(centimes, category, reason)
                },
                enabled = !busy && validAmount,
            ) { Text("Appliquer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}

internal fun categoryFrenchLabel(category: PaymentCategory): String = when (category) {
    PaymentCategory.TUITION -> "Scolarité"
    PaymentCategory.TRANSPORT -> "Transport"
    PaymentCategory.CANTEEN -> "Cantine"
    PaymentCategory.UNIFORM -> "Uniforme"
    PaymentCategory.BOOKS -> "Livres"
    PaymentCategory.EXTRACURRICULAR -> "Club / Activité"
    PaymentCategory.THERAPY_PSYCHOLOGY -> "Psychologie"
    PaymentCategory.THERAPY_SPEECH -> "Orthophonie"
    PaymentCategory.PARENT_CREDIT -> "Crédit parent"
    PaymentCategory.SECOND_APRON -> "Tablier"
    PaymentCategory.OTHER -> "Autre"
}

@Composable
internal fun AddChildDialog(
    parentName: String,
    classes: List<com.example.domain.model.AcademicClass>,
    busy: Boolean,
    onConfirm: (
        firstName: String,
        lastName: String,
        birthDate: String,
        gender: String,
        gradeLevel: String,
        classId: String?,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf("") }
    var genderLabel by remember { mutableStateOf("Non précisé") }
    var gradeLevel by remember { mutableStateOf("") }
    var className by remember { mutableStateOf("Aucune") }

    val genderOptions = listOf("Non précisé", "Masculin", "Féminin")
    val genderCode = when (genderLabel) {
        "Masculin" -> "M"
        "Féminin" -> "F"
        else -> ""
    }
    val cycle = com.example.core.academicLevelForGradeCode(gradeLevel)
    val cycleClasses = classes.filter { it.level == cycle }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un enfant — $parentName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = firstName, onValueChange = { firstName = it }, label = { Text("Prénom *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = lastName, onValueChange = { lastName = it }, label = { Text("Nom") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = birthDate, onValueChange = { birthDate = it }, label = { Text("Date de naissance (AAAA-MM-JJ) *") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    genderOptions.forEach { opt ->
                        ElTag(text = opt, selected = genderLabel == opt, color = PrimaryBlue, onClick = { genderLabel = opt })
                    }
                }
                com.example.ui.components.ElDropdown(
                    label = "Niveau scolaire",
                    selectedValue = gradeLevel,
                    options = com.example.core.GRADE_LEVEL_CODES,
                    onSelected = {
                        gradeLevel = it
                        className = "Aucune"
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (gradeLevel.isNotBlank() && cycleClasses.isNotEmpty()) {
                    com.example.ui.components.ElDropdown(
                        label = "Classe (optionnel)",
                        selectedValue = className,
                        options = listOf("Aucune") + cycleClasses.map { it.name },
                        onSelected = { name -> className = name },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    "L'élève sera rattaché à ce parent (dépendance parent-first). La facturation est générée selon la tarification du niveau.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val classId = cycleClasses.firstOrNull { it.name == className }?.id
                    onConfirm(firstName.trim(), lastName.trim(), birthDate.trim(), genderCode, gradeLevel, classId)
                },
                enabled = !busy && firstName.isNotBlank() && birthDate.isNotBlank() && gradeLevel.isNotBlank(),
            ) { Text("Ajouter") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}
