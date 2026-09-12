package com.example.ui.features.crm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.display.ElAlertBanner
import com.example.ui.designsystem.components.display.ElAlertSeverity
import com.example.ui.designsystem.components.display.ElInfoRow
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.feedback.ElLinearProgress
import com.example.ui.designsystem.foundation.elMoneyFormat
import com.example.ui.designsystem.theme.ElTheme

/**
 * T-320 (55th session) — shared pieces of the 4-step registration wizard:
 * the step progress header, the success screen (codes + copy + WhatsApp
 * share), and the step-3 simulation renderers.
 *
 * All components pull their tokens from [ElTheme] (no hard-coded brand
 * colors — dark-theme safe) and import exclusively from `ui.designsystem`
 * (the canonical layer, DUP-003).
 */

// ── Step progress (stepper) ─────────────────────────────────────────────────

private val WIZARD_STEPS = listOf(
    "Parent" to "Tuteur légal",
    "Élèves" to "Enfants à inscrire",
    "Facturation" to "Simulation en direct",
    "Validation" to "Récapitulatif & envoi",
)

@Composable
internal fun RegistrationStepProgress(
    currentStep: Int,
    onStepClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WIZARD_STEPS.forEachIndexed { index, (label, _) ->
                val stepNumber = index + 1
                val isDone = stepNumber < currentStep
                val isCurrent = stepNumber == currentStep
                val circleColor = when {
                    isDone -> c.success
                    isCurrent -> c.primary
                    else -> c.surfaceVariant
                }
                val contentColor = when {
                    isDone -> c.onSuccess
                    isCurrent -> c.onPrimary
                    else -> c.textSecondary
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(enabled = stepNumber < currentStep) {
                        onStepClick(stepNumber)
                    },
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(circleColor),
                    ) {
                        if (isDone) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Étape $stepNumber terminée",
                                tint = contentColor,
                                modifier = Modifier.size(16.dp),
                            )
                        } else {
                            Text(
                                "$stepNumber",
                                color = contentColor,
                                style = ElTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Text(
                        label,
                        style = ElTheme.typography.labelLarge,
                        color = if (isCurrent) c.textPrimary else c.textSecondary,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                if (index < WIZARD_STEPS.lastIndex) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                            .height(2.dp)
                            .clip(CircleShape)
                            .background(if (stepNumber < currentStep) c.success else c.surfaceVariant),
                    )
                }
            }
        }
        ElLinearProgress(
            progress = currentStep / WIZARD_STEPS.size.toFloat(),
            modifier = Modifier.padding(horizontal = 16.dp),
            height = 4,
        )
    }
}

// ── Success screen ──────────────────────────────────────────────────────────

@Composable
internal fun RegistrationSuccessCard(
    parentName: String,
    parentCode: String,
    activationCode: String,
    studentCodes: List<String>,
    parentPhone: String,
    whatsappPhone: String?,
    onCopyCode: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onNewRegistration: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(c.successContainer),
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Inscription réussie",
                tint = c.success,
                modifier = Modifier.size(44.dp),
            )
        }
        Text(
            "Inscription réussie !",
            style = ElTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            "La famille a été enregistrée. Partagez le code d'activation avec le parent pour qu'il accède au portail web.",
            style = ElTheme.typography.bodyMedium,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
        )

        ElCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "CODE D'ACTIVATION PORTAIL",
                    style = ElTheme.typography.labelMedium,
                    color = c.textSecondary,
                    letterSpacing = 1.5.sp,
                )
                Text(
                    activationCode,
                    style = ElTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = c.warning,
                    letterSpacing = 4.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ElButton(
                        text = "Copier",
                        onClick = onCopyCode,
                        variant = ElButtonVariant.SECONDARY,
                        icon = Icons.Default.ContentCopy,
                        size = com.example.ui.designsystem.components.button.ElButtonSize.SMALL,
                    )
                    ElButton(
                        text = "Partager WhatsApp",
                        onClick = onShareWhatsApp,
                        variant = ElButtonVariant.PRIMARY,
                        size = com.example.ui.designsystem.components.button.ElButtonSize.SMALL,
                    )
                }
            }
        }

        ElCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Récapitulatif",
                    style = ElTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                ElInfoRow(label = "Parent", value = parentName.ifBlank { "—" }, valueTint = c.textPrimary)
                ElInfoRow(label = "Code famille", value = parentCode, valueTint = c.primary)
                ElInfoRow(label = "Téléphone", value = parentPhone.ifBlank { "—" }, valueTint = c.textPrimary)
                studentCodes.forEach { code ->
                    ElInfoRow(label = "Élève créé", value = code, valueTint = c.primary)
                }
            }
        }

        ElButton(
            text = "Nouvelle inscription",
            onClick = onNewRegistration,
            variant = ElButtonVariant.SECONDARY,
            fullWidth = true,
        )
        ElButton(
            text = "Terminer",
            onClick = onDone,
            variant = ElButtonVariant.PRIMARY,
            fullWidth = true,
        )
    }
}

// ── Step-3 simulation renderers ─────────────────────────────────────────────

@Composable
internal fun SimulationGrandTotalCard(
    simulation: BillingSimulation,
    childCount: Int,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    ElCard(modifier = modifier.fillMaxWidth(), gradient = c.primaryBrush) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "TOTAL ANNUEL PRÉVU",
                    style = ElTheme.typography.labelLarge,
                    color = c.onPrimary.copy(alpha = 0.85f),
                    letterSpacing = 1.sp,
                )
                ElTag(
                    text = "$childCount ${if (childCount > 1) "élèves" else "élève"}",
                    tone = ElTagTone.INFO,
                )
            }
            Text(
                elMoneyFormat(simulation.grandTotal),
                style = ElTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = c.onPrimary,
            )
            Text(
                "Scolarité nette ${elMoneyFormat(simulation.tuitionNetTotal, showCurrency = false)}" +
                    if (simulation.transportTotal > 0L) " + transport ${elMoneyFormat(simulation.transportTotal, showCurrency = false)}" else "",
                style = ElTheme.typography.bodySmall,
                color = c.onPrimary.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
internal fun SimulationChildCard(
    billing: CalculatedChildBilling,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    ElCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        billing.childName,
                        style = ElTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (billing.gradeLevel.isNotBlank()) {
                        Text(
                            billing.gradeLevel.uppercase(),
                            style = ElTheme.typography.labelMedium,
                            color = c.textSecondary,
                        )
                    }
                }
                Text(
                    elMoneyFormat(billing.totalChild),
                    style = ElTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = c.primary,
                )
            }

            when {
                billing.tuitionGross == null -> {
                    ElAlertBanner(
                        title = "Aucun barème pour ce niveau",
                        message = "Ce niveau n'a pas de scolarité enregistrée : AUCUN frais de scolarité ne sera facturé. Revenez à l'étape Élèves pour corriger.",
                        severity = ElAlertSeverity.WARNING,
                    )
                }
                billing.discounts < 0L -> {
                    ElInfoRow(
                        label = "Scolarité (brute)",
                        value = elMoneyFormat(billing.tuitionGross),
                        valueTint = c.textPrimary,
                    )
                    billing.discountLabels.forEach { label ->
                        ElTag(text = label, tone = ElTagTone.SUCCESS)
                    }
                    ElInfoRow(label = "Scolarité (nette)", value = elMoneyFormat(billing.netTuition))
                }
                else -> {
                    ElInfoRow(label = "Scolarité annuelle", value = elMoneyFormat(billing.netTuition))
                }
            }

            if ((billing.transportAnnual ?: 0L) > 0L) {
                ElInfoRow(label = "Transport (annuel)", value = elMoneyFormat(billing.transportAnnual ?: 0L))
            }

            if (billing.tranchePreview.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    billing.tranchePreview.forEach { (label, amount) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ElTheme.shapes.small)
                                .background(c.surfaceVariant)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(label, style = ElTheme.typography.bodySmall, color = c.textSecondary)
                            Text(
                                elMoneyFormat(amount, showCurrency = false),
                                style = ElTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = c.textPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Step-4 "atomic transaction" reassurance banner. */
@Composable
internal fun AtomicTransactionBanner(modifier: Modifier = Modifier) {
    val c = ElTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ElTheme.shapes.medium)
            .background(c.successContainer)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Default.VerifiedUser,
            contentDescription = null,
            tint = c.success,
            modifier = Modifier.size(22.dp),
        )
        Column {
            Text(
                "Transaction atomique sécurisée",
                style = ElTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary,
            )
            Text(
                "Le parent, tous les élèves et les frais seront créés ensemble — en tout ou rien. Aucune inscription partielle n'est possible.",
                style = ElTheme.typography.bodySmall,
                color = c.textSecondary,
            )
        }
    }
}

/** Shared WhatsApp welcome message for the activation-code share (T-320). */
internal fun activationShareMessage(parentFirstName: String, activationCode: String): String =
    "Bonjour $parentFirstName,\n" +
        "Bienvenue à l'Établissement El-Imtiyaz !\n" +
        "Votre code d'activation pour accéder au portail web des parents est : $activationCode\n" +
        "Lien du portail : https://elimtiyaz.dz"
