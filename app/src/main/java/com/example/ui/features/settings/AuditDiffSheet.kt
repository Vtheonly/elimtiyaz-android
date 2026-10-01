package com.example.ui.features.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.DiffRow
import com.example.core.FieldDiffKind
import com.example.core.computeAuditDiffRows
import com.example.core.countDiffRows
import com.example.domain.model.AuditLog
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagSize
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.overlays.ElBottomSheet
import com.example.ui.designsystem.theme.ElTheme

/* ------------------------------------------------------------------ */
/*  AuditDiffSheet — T-297 (OFFLINE-400), the Android half of the      */
/*  T-296 desktop AuditDiffDrawer.                                     */
/*                                                                     */
/*  UI-unification pass (T-044 expansion, the issue's §2.9/§2.6 mixed */
/*  entry): the shared renderer moved out of the legacy ui/components  */
/*  kit onto the design system (ElBottomSheet chrome + ElTag tones +   */
/*  ElTheme tokens). Behaviour, testTags and every user-facing string  */
/*  are preserved — AuditDiffSheetTest pins the contract. Shared by    */
/*  AuditStreamScreen (Personnel) and AuditLogScreen (Settings) —      */
/*  ONE renderer, two entry points.                                    */
/* ------------------------------------------------------------------ */

/** Parse + compute once per entry (memoized on the raw strings). */
@Composable
private fun rememberAuditDiffRows(log: AuditLog): List<DiffRow> =
    remember(log.id, log.beforeJson, log.afterJson) {
        computeAuditDiffRows(log.beforeJson, log.afterJson)
    }

@Composable
fun AuditDiffSheet(
    log: AuditLog?,
    onDismiss: () -> Unit,
) {
    if (log == null) return
    ElBottomSheet(onDismissRequest = onDismiss) {
        AuditDiffSheetContent(
            log = log,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ElTheme.spacing.xl)
                .padding(bottom = ElTheme.spacing.xl),
        )
    }
}

/** The drawer content, sheet-chrome-free — the rendering test targets this. */
@Composable
fun AuditDiffSheetContent(
    log: AuditLog,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    val rows = rememberAuditDiffRows(log)
    val counts = remember(rows) { countDiffRows(rows) }
    var showRaw by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.md),
    ) {
        // ── Title + entity line ─────────────────────────────────────
        Text(
            "Diff — ${log.action}",
            style = ElTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = c.textPrimary,
        )
        Text(
            "${log.entityType} · ID ${log.entityId}",
            style = ElTheme.typography.bodyMedium,
            color = c.textSecondary,
        )

        // ── Actor attribution block (Name + Account ID + Role) ──────
        ActorAttributionBlock(
            actorName = log.actorName,
            actorId = log.actorId,
            actorRole = log.actorRole,
        )

        // ── Note (when present) ─────────────────────────────────────
        log.note?.takeIf { it.isNotBlank() }?.let { note ->
            Column {
                Text(
                    "NOTE",
                    style = ElTheme.typography.labelSmall.copy(color = c.textSecondary),
                )
                Spacer(Modifier.height(ElTheme.spacing.xs))
                Text(
                    note,
                    style = ElTheme.typography.bodySmall,
                    color = c.textPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(c.surfaceVariant.copy(alpha = 0.08f))
                        .padding(ElTheme.spacing.sm),
                )
            }
        }

        // ── Field-level diff summary badges ─────────────────────────
        DiffSummaryRow(counts.added, counts.removed, counts.changed)

        // ── The red/green TABLE (T-308, 48th session — the owner's explicit
        //    request: a table with the old values in red and the new values
        //    in green, much clearer than the JSON view) ───────────────────
        if (rows.isNotEmpty()) {
            DiffTable(rows)
        } else {
            HonestEmptyDiffState(
                hasSnapshots = !log.beforeJson.isNullOrBlank() || !log.afterJson.isNullOrBlank(),
            )
        }

        // ── Collapsible raw JSON (forensic view) ─────────────────────
        if (!log.beforeJson.isNullOrBlank() || !log.afterJson.isNullOrBlank()) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { showRaw = !showRaw }
                        .padding(vertical = ElTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (showRaw) "▾" else "▸",
                        fontSize = 12.sp,
                        color = c.primary,
                    )
                    Spacer(Modifier.width(ElTheme.spacing.xs + 2.dp))
                    Text(
                        "JSON brut (forensique)",
                        style = ElTheme.typography.labelMedium.copy(
                            color = c.primary,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
                if (showRaw) {
                    RawJsonBlock(label = "Avant", raw = log.beforeJson)
                    RawJsonBlock(label = "Après", raw = log.afterJson)
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Actor attribution — the mandate's "every change shows the          */
/*  operator's Name, Account ID and Role".                             */
/* ------------------------------------------------------------------ */

@Composable
private fun ActorAttributionBlock(
    actorName: String,
    actorId: String,
    actorRole: String?,
) {
    val c = ElTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(c.surfaceVariant.copy(alpha = 0.08f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "OPÉRATEUR",
            style = ElTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                color = c.textSecondary,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = c.primary,
                modifier = Modifier.width(16.dp).height(16.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    actorName.ifBlank { "—" },
                    style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = c.textPrimary,
                    modifier = Modifier.testTag("audit_diff_actor_name"),
                )
                Text(
                    actorId,
                    fontFamily = FontFamily.Monospace,
                    style = ElTheme.typography.labelSmall.copy(color = c.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("audit_diff_actor_id"),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (actorRole != null) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.width(14.dp).height(14.dp),
                )
                Spacer(Modifier.width(ElTheme.spacing.xs))
                ElTag(
                    text = actorRole,
                    tone = ElTagTone.INFO,
                    size = ElTagSize.MD,
                    modifier = Modifier.testTag("audit_diff_actor_role"),
                )
            } else {
                Text(
                    "rôle non enregistré",
                    style = ElTheme.typography.labelSmall.copy(
                        fontStyle = FontStyle.Italic,
                        color = c.textSecondary.copy(alpha = 0.6f),
                    ),
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Summary badges: "N supprimés / N modifiés / N ajoutés"             */
/* ------------------------------------------------------------------ */

@Composable
private fun DiffSummaryRow(added: Int, removed: Int, changed: Int) {
    val c = ElTheme.colors
    val total = added + removed + changed
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Diff par champ",
            style = ElTheme.typography.labelSmall.copy(color = c.textSecondary),
        )
        if (total > 0) {
            if (removed > 0) {
                ElTag(
                    text = "$removed supprimé${if (removed == 1) "" else "s"}",
                    tone = ElTagTone.DANGER,
                    size = ElTagSize.MD,
                    modifier = Modifier.testTag("audit_diff_summary_removed"),
                )
            }
            if (changed > 0) {
                ElTag(
                    text = "$changed modifié${if (changed == 1) "" else "s"}",
                    tone = ElTagTone.INFO,
                    size = ElTagSize.MD,
                    modifier = Modifier.testTag("audit_diff_summary_changed"),
                )
            }
            if (added > 0) {
                ElTag(
                    text = "$added ajouté${if (added == 1) "" else "s"}",
                    tone = ElTagTone.SUCCESS,
                    size = ElTagSize.MD,
                    modifier = Modifier.testTag("audit_diff_summary_added"),
                )
            }
        } else {
            Text(
                "aucune différence structurelle",
                style = ElTheme.typography.labelSmall.copy(
                    fontStyle = FontStyle.Italic,
                    color = c.textSecondary,
                ),
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/*  The diff TABLE — T-308 (48th session): 3 columns                  */
/*  Champ | Avant (RED, struck) | Après (GREEN, bold). The owner's     */
/*  explicit presentation request, mirroring the desktop drawer's     */
/*  audit-diff-table (same engine rows, same color convention).       */
/* ------------------------------------------------------------------ */

@Composable
fun DiffTable(rows: List<DiffRow>, modifier: Modifier = Modifier) {
    val c = ElTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(ElTheme.borders.thin, c.outlineVariant, RoundedCornerShape(8.dp))
            .testTag("audit_diff_table"),
    ) {
        // Header row — the 3 column labels.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.surfaceVariant.copy(alpha = 0.35f))
                .padding(vertical = ElTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TableHeaderText("Champ", Modifier.weight(0.30f), c.textSecondary)
            TableHeaderText("Avant (ancien)", Modifier.weight(0.35f), c.danger)
            TableHeaderText("Après (nouveau)", Modifier.weight(0.35f), c.success)
        }
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 0.dp),
                    thickness = ElTheme.borders.hairline,
                    color = c.outlineVariant.copy(alpha = 0.5f),
                )
            }
            DiffFieldRow(row)
        }
    }
}

@Composable
private fun TableHeaderText(text: String, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color) {
    Text(
        text.uppercase(),
        style = ElTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
        ),
        color = color,
        modifier = modifier.padding(horizontal = 10.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/* ------------------------------------------------------------------ */
/*  One table row — the visual convention:                            */
/*  Avant = RED struck-through value, Après = GREEN bold value.       */
/* ------------------------------------------------------------------ */

@Composable
fun DiffFieldRow(row: DiffRow, modifier: Modifier = Modifier) {
    val c = ElTheme.colors
    val (accent, kindLabel) = when (row.kind) {
        FieldDiffKind.ADDED -> c.success to "ajouté"
        FieldDiffKind.REMOVED -> c.danger to "supprimé"
        FieldDiffKind.CHANGED -> c.primary to "modifié"
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("audit_diff_row_${row.path}")
            .background(accent.copy(alpha = 0.04f))
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Column 1 — Champ (short label primary, full dotted path caption).
        Column(
            modifier = Modifier
                .weight(0.30f)
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                row.field,
                fontFamily = FontFamily.Monospace,
                style = ElTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = c.textPrimary,
                modifier = Modifier.testTag("audit_diff_field"),
            )
            if (row.path != row.field && row.path.contains(".")) {
                Text(
                    row.path,
                    fontFamily = FontFamily.Monospace,
                    style = ElTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        color = c.textSecondary.copy(alpha = 0.7f),
                    ),
                    modifier = Modifier.testTag("audit_diff_row_path"),
                )
            }
            Text(
                kindLabel,
                style = ElTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = accent,
                ),
            )
        }
        // Column 2 — Avant (OLD value, RED struck). Added rows show the em-dash.
        Box(
            modifier = Modifier
                .weight(0.35f)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            if (row.kind != FieldDiffKind.ADDED) {
                ValueChip(
                    text = row.oldDisplay,
                    color = c.danger,
                    struck = true,
                    modifier = Modifier.testTag("diff-old-value"),
                )
            } else {
                Text(
                    "—",
                    style = ElTheme.typography.labelSmall.copy(
                        fontStyle = FontStyle.Italic,
                        color = c.textSecondary.copy(alpha = 0.5f),
                    ),
                )
            }
        }
        // Column 3 — Après (NEW value, GREEN bold). Removed rows show the em-dash.
        Box(
            modifier = Modifier
                .weight(0.35f)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            if (row.kind != FieldDiffKind.REMOVED) {
                ValueChip(
                    text = row.newDisplay,
                    color = c.success,
                    struck = false,
                    bold = true,
                    modifier = Modifier.testTag("diff-new-value"),
                )
            } else {
                Text(
                    "—",
                    style = ElTheme.typography.labelSmall.copy(
                        fontStyle = FontStyle.Italic,
                        color = c.textSecondary.copy(alpha = 0.5f),
                    ),
                )
            }
        }
    }
}

/** A compact mono value chip — red/struck for old, green/bold for new. */
@Composable
private fun ValueChip(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    struck: Boolean,
    bold: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            fontFamily = FontFamily.Monospace,
            style = ElTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                color = color,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                textDecoration = if (struck) TextDecoration.LineThrough else TextDecoration.None,
            ),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/* ------------------------------------------------------------------ */
/*  The honest empty states                                            */
/* ------------------------------------------------------------------ */

@Composable
private fun HonestEmptyDiffState(hasSnapshots: Boolean) {
    val c = ElTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(
                width = ElTheme.borders.thin,
                color = c.textSecondary.copy(alpha = 0.3f),
                shape = RoundedCornerShape(6.dp),
            )
            .padding(ElTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (hasSnapshots) {
                "Avant et après sont structurellement identiques (aucun champ modifié)."
            } else {
                "Aucun instantané avant/après enregistré pour cette entrée."
            },
            style = ElTheme.typography.bodySmall.copy(color = c.textSecondary),
            modifier = Modifier.testTag("audit_diff_empty_state"),
        )
    }
}

/* ------------------------------------------------------------------ */
/*  Raw forensic blocks                                                */
/* ------------------------------------------------------------------ */

@Composable
private fun RawJsonBlock(label: String, raw: String?) {
    if (raw.isNullOrBlank()) return
    val c = ElTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.xs)) {
        Text(
            label,
            style = ElTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = c.textSecondary,
            ),
        )
        Text(
            raw,
            fontFamily = FontFamily.Monospace,
            style = ElTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = c.textSecondary,
        )
    }
}
