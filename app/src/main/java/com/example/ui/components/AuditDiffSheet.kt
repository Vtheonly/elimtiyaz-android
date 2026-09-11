package com.example.ui.components

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.ui.theme.DangerRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen

/* ------------------------------------------------------------------ */
/*  AuditDiffSheet — T-297 (OFFLINE-400), the Android half of the      */
/*  T-296 desktop AuditDiffDrawer.                                     */
/*                                                                     */
/*  Was (AuditStreamScreen): a bottom sheet FABRICATING a JSON payload */
/*  from 6 scalar fields, never reading beforeJson/afterJson.          */
/*  Now: the canonical FieldDiff engine (core/FieldDiff.kt, verbatim   */
/*  mirror of src/domain/calc/diff/field-diff.ts, commit 86dcf77)      */
/*  drives real field-level rows — OLD value red/struck, NEW value     */
/*  green, added green-only, removed red-only — plus the actor         */
/*  attribution block (Name + Account ID + Role) and the collapsible   */
/*  raw forensic view. Shared by AuditStreamScreen (Personnel) and     */
/*  AuditLogScreen (Settings) — ONE renderer, two entry points.        */
/* ------------------------------------------------------------------ */

/** Parse + compute once per entry (memoized on the raw strings). */
@Composable
private fun rememberAuditDiffRows(log: AuditLog): List<DiffRow> =
    remember(log.id, log.beforeJson, log.afterJson) {
        computeAuditDiffRows(log.beforeJson, log.afterJson)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditDiffSheet(
    log: AuditLog?,
    onDismiss: () -> Unit,
) {
    if (log == null) return
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        AuditDiffSheetContent(
            log = log,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        )
    }
}

/** The drawer content, sheet-chrome-free — the rendering test targets this. */
@Composable
fun AuditDiffSheetContent(
    log: AuditLog,
    modifier: Modifier = Modifier,
) {
    val rows = rememberAuditDiffRows(log)
    val counts = remember(rows) { countDiffRows(rows) }
    var showRaw by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Title + entity line ─────────────────────────────────────
        Text(
            "Diff — ${log.action}",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            "${log.entityType} · ID ${log.entityId}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
                        .padding(8.dp),
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
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (showRaw) "▾" else "▸",
                        fontSize = 12.sp,
                        color = PrimaryBlue,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "JSON brut (forensique)",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = PrimaryBlue,
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "OPÉRATEUR",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                tint = PrimaryBlue,
                modifier = Modifier.width(16.dp).height(16.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    actorName.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.testTag("audit_diff_actor_name"),
                )
                Text(
                    actorId,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
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
                    tint = PrimaryBlue,
                    modifier = Modifier.width(14.dp).height(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                ElTag(
                    text = actorRole,
                    color = PrimaryBlue,
                    modifier = Modifier.testTag("audit_diff_actor_role"),
                )
            } else {
                Text(
                    "rôle non enregistré",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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
    val total = added + removed + changed
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Diff par champ",
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        if (total > 0) {
            if (removed > 0) {
                ElTag(
                    text = "$removed supprimé${if (removed == 1) "" else "s"}",
                    color = DangerRed,
                    modifier = Modifier.testTag("audit_diff_summary_removed"),
                )
            }
            if (changed > 0) {
                ElTag(
                    text = "$changed modifié${if (changed == 1) "" else "s"}",
                    color = PrimaryBlue,
                    modifier = Modifier.testTag("audit_diff_summary_changed"),
                )
            }
            if (added > 0) {
                ElTag(
                    text = "$added ajouté${if (added == 1) "" else "s"}",
                    color = SuccessGreen,
                    modifier = Modifier.testTag("audit_diff_summary_added"),
                )
            }
        } else {
            Text(
                "aucune différence structurelle",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .testTag("audit_diff_table"),
    ) {
        // Header row — the 3 column labels.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TableHeaderText("Champ", Modifier.weight(0.30f), MaterialTheme.colorScheme.onSurfaceVariant)
            TableHeaderText("Avant (ancien)", Modifier.weight(0.35f), DangerRed)
            TableHeaderText("Après (nouveau)", Modifier.weight(0.35f), SuccessGreen)
        }
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 0.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
            DiffFieldRow(row)
        }
    }
}

@Composable
private fun TableHeaderText(text: String, modifier: Modifier = Modifier, color: Color) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
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
    val (accent, kindLabel) = when (row.kind) {
        FieldDiffKind.ADDED -> SuccessGreen to "ajouté"
        FieldDiffKind.REMOVED -> DangerRed to "supprimé"
        FieldDiffKind.CHANGED -> PrimaryBlue to "modifié"
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
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("audit_diff_field"),
            )
            if (row.path != row.field && row.path.contains(".")) {
                Text(
                    row.path,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    ),
                    modifier = Modifier.testTag("audit_diff_row_path"),
                )
            }
            Text(
                kindLabel,
                style = MaterialTheme.typography.labelSmall.copy(
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
                    color = DangerRed,
                    struck = true,
                    modifier = Modifier.testTag("diff-old-value"),
                )
            } else {
                Text(
                    "—",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
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
                    color = SuccessGreen,
                    struck = false,
                    bold = true,
                    modifier = Modifier.testTag("diff-new-value"),
                )
            } else {
                Text(
                    "—",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
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
    color: Color,
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
            style = MaterialTheme.typography.labelSmall.copy(
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(6.dp),
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (hasSnapshots) {
                "Avant et après sont structurellement identiques (aucun champ modifié)."
            } else {
                "Aucun instantané avant/après enregistré pour cette entrée."
            },
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        Text(
            raw,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
