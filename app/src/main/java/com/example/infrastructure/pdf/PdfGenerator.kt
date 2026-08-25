package com.example.infrastructure.pdf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.core.LedgerEntry
import com.example.core.LedgerEntryType
import com.example.core.ParentLedgerSummary
import com.example.core.PaymentCategory
import com.example.core.PaymentMethod
import com.example.core.PaymentStatus
import com.example.domain.model.Parent
import com.example.domain.model.Payment
import com.example.domain.model.Student
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.ZoneId

/**
 * Pure Canvas-based PDF renderer — Android port of the desktop's
 * `src/infrastructure/receipt-pdf/` generators (payment-receipt.ts,
 * account-statement.ts, shared.ts).
 *
 * Uses the framework [PdfDocument] + [Canvas] only (no extra dependency).
 * A4 portrait: 595 x 842 points. Layout mirrors the desktop 1:1:
 * brand bar, meta box, sections, tables, status banner, signature line,
 * and footer — all in French.
 *
 * Amounts arrive in centimes (Long) and are rendered as DZD with a space
 * thousands separator + " DA" suffix (e.g. `25 000 DA`).
 */
object PdfGenerator {

    // ── Layout constants (mirror desktop receipt-pdf/shared.ts) ─────────────
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 50f
    private const val CONTENT_W = PAGE_W - 2 * MARGIN
    private const val BRAND_BAR_H = 80f

    // ── Brand palette (mirror desktop receipt-pdf/shared.ts) ────────────────
    private const val BRAND_BLUE = 0xFF349BD4.toInt()
    private const val BRAND_BLUE_DEEP = 0xFF2B7FB0.toInt()
    private const val TEXT_PRIMARY = 0xFF1E1F20.toInt()
    private const val TEXT_MUTED = 0xFF6B7075.toInt()
    private const val BORDER = 0xFFCCCCCC.toInt()
    private const val SUCCESS = 0xFF3FA66E.toInt()
    private const val WARNING = 0xFFC8A98C.toInt()
    private const val DANGER = 0xFFC0504D.toInt()
    private const val BG_SOFT = 0xFFF7F9FB.toInt()
    private const val BG_NOTE = 0xFFFAFAFA.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val DATETIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Render a generic macro-report as a paginated A4 table — backs the five
     * "Rapports" (revenu mensuel, créances âgées, effectifs, dépenses,
     * annuaire du personnel). Every value comes from the caller's REAL data;
     * this renderer only draws it.
     *
     * @param title   report title shown in the brand bar
     * @param columns table column headers (first column is left-aligned and
     *                gets the remaining width; the others are right-aligned
     *                and share the width proportionally to their header)
     * @param rows    table rows (each must match [columns] size)
     * @param summaryLines key figures rendered as summary boxes under the header
     * @param outputDir directory to write the file into (created if missing)
     * @param fileName target file name inside [outputDir]
     */
    fun generateTableReport(
        title: String,
        columns: List<String>,
        rows: List<List<String>>,
        summaryLines: List<Pair<String, String>> = emptyList(),
        outputDir: File,
        fileName: String,
    ): File {
        require(columns.isNotEmpty()) { "columns must not be empty" }
        rows.forEach { require(it.size == columns.size) { "row width mismatch: $it" } }

        val doc = PdfDocument()
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        var canvas = page.canvas
        var pageNo = 1
        var y = 0f

        fun newPage() {
            drawFooter(canvas, pageNo)
            doc.finishPage(page)
            pageNo += 1
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            canvas = page.canvas
            drawHeader(canvas, title)
            y = BRAND_BAR_H + 40f
        }

        fun ensureSpace(needed: Float) {
            if (y + needed > PAGE_H - 90f) newPage()
        }

        drawHeader(canvas, title)
        y = BRAND_BAR_H + 30f

        // ── Summary boxes (key figures) ──
        if (summaryLines.isNotEmpty()) {
            val boxW = (CONTENT_W - 2 * 10f) / 3f
            summaryLines.chunked(3).forEach { chunk ->
                chunk.forEachIndexed { idx, (label, value) ->
                    drawSummaryBox(
                        canvas,
                        MARGIN + idx * (boxW + 10f), y, boxW,
                        label, value, BRAND_BLUE_DEEP,
                    )
                }
                y += 60f
            }
            y += 10f
        }

        // ── Table ──
        val colCount = columns.size
        // First column eats ~40% of the width when there are ≥3 columns,
        // otherwise 55%; the rest is split evenly.
        val firstW = if (colCount >= 3) CONTENT_W * 0.40f else CONTENT_W * 0.55f
        val otherW = (CONTENT_W - firstW) / (colCount - 1).coerceAtLeast(1)
        fun colX(i: Int): Float = if (i == 0) MARGIN else MARGIN + firstW + (i - 1) * otherW

        val headerPaint = paint(9f, WHITE, bold = true)
        val cellPaint = paint(9f, TEXT_PRIMARY)
        val rowH = 22f

        fun colWidth(i: Int): Float = if (i == 0) firstW else otherW

        fun drawTableHeader() {
            drawBox(canvas, MARGIN, y, CONTENT_W, rowH, fill = BRAND_BLUE_DEEP)
            columns.forEachIndexed { i, col ->
                val text = truncate(col, headerPaint, colWidth(i) - 8f)
                if (i == 0) {
                    canvas.drawText(text, colX(i) + 4f, y + 15f, headerPaint)
                } else {
                    drawRightAligned(canvas, text, colX(i) + colWidth(i) - 4f, y + 15f, headerPaint)
                }
            }
            y += rowH
        }

        drawTableHeader()

        if (rows.isEmpty()) {
            ensureSpace(rowH * 2)
            drawBox(canvas, MARGIN, y, CONTENT_W, rowH * 2, fill = BG_NOTE, stroke = BORDER)
            canvas.drawText(
                "Aucune donnée à afficher pour cette période.",
                MARGIN + 8f, y + 30f, paint(9f, TEXT_MUTED, bold = true),
            )
            y += rowH * 2
        } else {
            rows.forEachIndexed { rowIdx, row ->
                ensureSpace(rowH)
                if (rowIdx % 2 == 1) {
                    drawBox(canvas, MARGIN, y, CONTENT_W, rowH, fill = BG_SOFT)
                }
                row.forEachIndexed { i, cell ->
                    val text = truncate(cell, cellPaint, colWidth(i) - 8f)
                    if (i == 0) {
                        canvas.drawText(text, colX(i) + 4f, y + 15f, cellPaint)
                    } else {
                        drawRightAligned(canvas, text, colX(i) + colWidth(i) - 4f, y + 15f, cellPaint)
                    }
                }
                canvas.drawLine(MARGIN, y + rowH, PAGE_W - MARGIN, y + rowH, strokePaint(BORDER, 0.3f))
                y += rowH
            }
        }

        // Row count note
        y += 14f
        ensureSpace(20f)
        canvas.drawText(
            "${rows.size} ligne(s) · généré depuis les données locales (Room)",
            MARGIN, y, paint(8f, TEXT_MUTED),
        )

        drawFooter(canvas, pageNo)
        doc.finishPage(page)
        return write(doc, outputDir, fileName)
    }

    /** Truncate text with an ellipsis when it exceeds [maxWidth]. */
    private fun truncate(text: String, p: Paint, maxWidth: Float): String {
        if (p.measureText(text) <= maxWidth || text.isEmpty()) return text
        var t = text
        while (t.length > 1 && p.measureText("$t…") > maxWidth) {
            t = t.dropLast(1)
        }
        return "$t…"
    }

    /**
     * Render a single-payment receipt ("REÇU DE PAIEMENT").
     *
     * @param payment the payment to render (amounts in centimes)
     * @param parent the paying parent (nullable — renders "—" when unknown)
     * @param student the student the payment is scoped to, if any
     * @param breakdown ledger entries whose `sourceId` is this payment
     *        (the payment entry itself + any parent-credit overflow entry)
     * @param outputDir directory to write the file into (created if missing)
     */
    fun generatePaymentReceipt(
        payment: Payment,
        parent: Parent?,
        student: Student?,
        breakdown: List<LedgerEntry>,
        outputDir: File,
    ): File {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        val canvas = page.canvas

        drawHeader(canvas, "REÇU DE PAIEMENT")
        var y = BRAND_BAR_H + 26f

        // ── Receipt meta box ────────────────────────────────────────────────
        drawBox(canvas, MARGIN, y, CONTENT_W, 64f, fill = BG_SOFT, stroke = BORDER)
        drawKeyValue(canvas, MARGIN + 15f, y + 22f, "Reçu N°:", payment.receiptNumber)
        drawKeyValue(canvas, MARGIN + 15f, y + 42f, "Date:", formatDate(payment.collectedAt))
        drawKeyValue(canvas, MARGIN + 280f, y + 22f, "Statut:", statusLabel(payment.status))
        drawKeyValue(
            canvas, MARGIN + 280f, y + 42f, "Référence:",
            payment.id.take(8).uppercase(),
        )
        y += 84f

        // ── Payeur section ──────────────────────────────────────────────────
        drawSectionTitle(canvas, "PAYEUR", y)
        y += 8f
        drawBox(canvas, MARGIN, y, CONTENT_W, 52f, fill = BG_SOFT, stroke = BORDER)
        drawKeyValue(canvas, MARGIN + 15f, y + 20f, "Nom:", parent?.fullName ?: "—")
        drawKeyValue(canvas, MARGIN + 15f, y + 40f, "Code:", parent?.code ?: "—")
        drawKeyValue(canvas, MARGIN + 280f, y + 20f, "Téléphone:", parent?.phone ?: "—")
        drawKeyValue(canvas, MARGIN + 280f, y + 40f, "Élève:", student?.fullName ?: "—")
        y += 72f

        // ── Payment detail table ────────────────────────────────────────────
        drawSectionTitle(canvas, "DÉTAIL DU PAIEMENT", y)
        y += 8f
        drawBox(canvas, MARGIN, y, CONTENT_W, 20f, fill = BRAND_BLUE_DEEP)
        val headerWhite = paint(9f, WHITE, bold = true)
        canvas.drawText("Désignation", MARGIN + 15f, y + 14f, headerWhite)
        canvas.drawText("Méthode", MARGIN + 240f, y + 14f, headerWhite)
        canvas.drawText("Catégorie", MARGIN + 340f, y + 14f, headerWhite)
        canvas.drawText("Montant", MARGIN + 440f, y + 14f, headerWhite)
        y += 20f

        val amountStr = formatDa(payment.amount)
        val rowText = paint(10f, TEXT_PRIMARY)
        val rowBold = paint(10f, TEXT_PRIMARY, bold = true)
        val linePaint = strokePaint(BORDER)
        canvas.drawText("Paiement comptoir", MARGIN + 15f, y + 14f, rowText)
        canvas.drawText(methodLabel(payment.method), MARGIN + 240f, y + 14f, rowText)
        canvas.drawText(categoryLabel(payment.category), MARGIN + 340f, y + 14f, rowText)
        canvas.drawText(amountStr, MARGIN + 440f, y + 14f, rowBold)
        y += 22f
        canvas.drawLine(MARGIN, y, MARGIN + CONTENT_W, y, linePaint)
        y += 10f

        // ── Total box ───────────────────────────────────────────────────────
        val totalW = CONTENT_W - 320f
        drawBox(canvas, MARGIN + 320f, y, totalW, 36f, fill = SUCCESS)
        canvas.drawText("TOTAL PAYÉ", MARGIN + 335f, y + 14f, paint(9f, WHITE, bold = true))
        val totalValue = paint(14f, WHITE, bold = true)
        canvas.drawText(
            amountStr,
            MARGIN + CONTENT_W - 15f - totalValue.measureText(amountStr),
            y + 28f,
            totalValue,
        )
        y += 56f

        // ── Notes ───────────────────────────────────────────────────────────
        if (!payment.notes.isNullOrBlank()) {
            drawSectionTitle(canvas, "NOTES", y)
            y += 8f
            drawBox(canvas, MARGIN, y, CONTENT_W, 34f, fill = BG_NOTE, stroke = BORDER)
            wrapText(payment.notes, rowText, CONTENT_W - 30f).take(2).forEachIndexed { i, line ->
                canvas.drawText(line, MARGIN + 15f, y + 16f + i * 12f, rowText)
            }
            y += 44f
        }

        // ── Breakdown (ledger entries backing this payment) ─────────────────
        if (breakdown.isNotEmpty()) {
            drawSectionTitle(canvas, "RÉPARTITION (GRAND LIVRE)", y)
            y += 8f
            breakdown.forEach { entry ->
                canvas.drawText(entry.description, MARGIN + 15f, y + 12f, rowText)
                val entryAmount = formatDa(-entry.amount)
                canvas.drawText(
                    entryAmount,
                    MARGIN + CONTENT_W - 15f - rowBold.measureText(entryAmount),
                    y + 12f,
                    rowBold,
                )
                y += 16f
                canvas.drawLine(MARGIN, y - 4f, MARGIN + CONTENT_W, y - 4f, strokePaint(BORDER))
            }
            y += 10f
        }

        // ── Status banner ───────────────────────────────────────────────────
        val bannerColor = when (payment.status) {
            PaymentStatus.PAID -> SUCCESS
            PaymentStatus.PENDING, PaymentStatus.PENDING_CLEARANCE, PaymentStatus.PARTIAL -> WARNING
            else -> TEXT_MUTED
        }
        drawBox(canvas, MARGIN, y, CONTENT_W, 28f, fill = bannerColor)
        canvas.drawText(
            "STATUT : ${statusLabel(payment.status).uppercase()}",
            MARGIN + 15f, y + 19f,
            paint(11f, WHITE, bold = true),
        )

        // ── Signature line (fixed near the bottom, like the desktop) ────────
        val sigY = PAGE_H - 160f
        canvas.drawText("Signature & cachet", PAGE_W - MARGIN - 150f, sigY, paint(9f, TEXT_MUTED))
        canvas.drawLine(
            PAGE_W - MARGIN - 150f, sigY + 6f,
            PAGE_W - MARGIN, sigY + 6f,
            strokePaint(BORDER),
        )

        drawFooter(canvas)
        doc.finishPage(page)

        return write(doc, outputDir, "recu-${sanitize(payment.receiptNumber)}.pdf")
    }

    /**
     * Render a parent account statement ("RELEVÉ DE COMPTE"):
     * parent info, per-account balances table with totals row, and the
     * most recent ledger movements.
     */
    fun generateAccountStatement(
        parent: Parent,
        summary: ParentLedgerSummary,
        entries: List<LedgerEntry>,
        outputDir: File,
    ): File {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        val canvas = page.canvas

        drawHeader(canvas, "RELEVÉ DE COMPTE")
        var y = BRAND_BAR_H + 26f

        // ── Statement meta box ──────────────────────────────────────────────
        drawBox(canvas, MARGIN, y, CONTENT_W, 82f, fill = BG_SOFT, stroke = BORDER)
        drawKeyValue(canvas, MARGIN + 15f, y + 20f, "Parent:", parent.fullName)
        drawKeyValue(canvas, MARGIN + 15f, y + 40f, "Code:", parent.code)
        drawKeyValue(canvas, MARGIN + 15f, y + 60f, "Téléphone:", parent.phone)
        drawKeyValue(canvas, MARGIN + 280f, y + 20f, "E-mail:", parent.email ?: "—")
        drawKeyValue(
            canvas, MARGIN + 280f, y + 40f, "Élèves:",
            summary.accounts.mapNotNull { it.studentId }.distinct().size.toString(),
        )
        drawKeyValue(canvas, MARGIN + 280f, y + 60f, "Émis le:", formatDate(Instant.now().toString()))
        y += 102f

        // ── Summary boxes ───────────────────────────────────────────────────
        drawSectionTitle(canvas, "SYNTHÈSE", y)
        y += 8f
        val boxW = (CONTENT_W - 16f) / 3f
        drawSummaryBox(canvas, MARGIN, y, boxW, "Total facturé", formatDa(summary.totalCharged), BRAND_BLUE_DEEP)
        drawSummaryBox(canvas, MARGIN + boxW + 8f, y, boxW, "Total payé", formatDa(summary.totalPaid), SUCCESS)
        drawSummaryBox(
            canvas, MARGIN + 2 * (boxW + 8f), y, boxW,
            "Reste dû", formatDa(summary.totalOutstanding),
            if (summary.totalOutstanding > 0) DANGER else SUCCESS,
        )
        y += 68f

        // ── Per-account balances table ──────────────────────────────────────
        drawSectionTitle(canvas, "SOLDES PAR COMPTE", y)
        y += 8f
        drawBox(canvas, MARGIN, y, CONTENT_W, 18f, fill = BRAND_BLUE_DEEP)
        val headerWhite = paint(8f, WHITE, bold = true)
        canvas.drawText("Compte", MARGIN + 10f, y + 12f, headerWhite)
        drawRightAligned(canvas, "Facturé", MARGIN + 322f, y + 12f, headerWhite)
        drawRightAligned(canvas, "Payé", MARGIN + 408f, y + 12f, headerWhite)
        drawRightAligned(canvas, "Solde", MARGIN + CONTENT_W - 10f, y + 12f, headerWhite)
        y += 18f

        val rowText = paint(9f, TEXT_PRIMARY)
        val rowBold = paint(9f, TEXT_PRIMARY, bold = true)
        val linePaint = strokePaint(BORDER)
        val accounts = summary.accounts.sortedWith(
            compareBy({ it.category.code }, { it.studentId ?: "" }),
        )
        if (accounts.isEmpty()) {
            canvas.drawText("Aucun compte actif.", MARGIN + 10f, y + 12f, paint(9f, TEXT_MUTED))
            y += 20f
        } else {
            accounts.forEach { account ->
                val label = buildString {
                    append(categoryLabel(account.category))
                    account.studentId?.let {
                        append(" — élève ")
                        append(it.take(8).uppercase())
                    }
                }
                canvas.drawText(label, MARGIN + 10f, y + 12f, rowText)
                drawRightAligned(canvas, formatDa(account.totalCharged), MARGIN + 322f, y + 12f, rowText)
                drawRightAligned(canvas, formatDa(account.totalPaid), MARGIN + 408f, y + 12f, rowText)
                val balanceColor = if (account.balance > 0) DANGER else SUCCESS
                drawRightAligned(
                    canvas, formatDa(account.balance), MARGIN + CONTENT_W - 10f, y + 12f,
                    paint(9f, balanceColor, bold = true),
                )
                y += 16f
                canvas.drawLine(MARGIN, y - 4f, MARGIN + CONTENT_W, y - 4f, linePaint)
            }
            // Totals row (total charged / paid / outstanding).
            canvas.drawText("TOTAL", MARGIN + 10f, y + 12f, rowBold)
            drawRightAligned(canvas, formatDa(summary.totalCharged), MARGIN + 322f, y + 12f, rowBold)
            drawRightAligned(canvas, formatDa(summary.totalPaid), MARGIN + 408f, y + 12f, rowBold)
            drawRightAligned(
                canvas, formatDa(summary.totalOutstanding), MARGIN + CONTENT_W - 10f, y + 12f,
                paint(9f, if (summary.totalOutstanding > 0) DANGER else SUCCESS, bold = true),
            )
            y += 24f
        }
        y += 10f

        // ── Recent ledger movements ─────────────────────────────────────────
        drawSectionTitle(canvas, "MOUVEMENTS RÉCENTS", y)
        y += 8f
        drawBox(canvas, MARGIN, y, CONTENT_W, 18f, fill = BRAND_BLUE_DEEP)
        canvas.drawText("Date", MARGIN + 10f, y + 12f, headerWhite)
        canvas.drawText("Opération", MARGIN + 90f, y + 12f, headerWhite)
        drawRightAligned(canvas, "Montant", MARGIN + CONTENT_W - 10f, y + 12f, headerWhite)
        y += 18f

        // Adaptive row budget: never overflow the footer zone (single page).
        val rowBudget = (((PAGE_H - 90f) - y) / 15f).toInt().coerceAtLeast(0)
        val recent = entries.sortedByDescending { it.at }.take(minOf(18, rowBudget))
        if (entries.isEmpty()) {
            canvas.drawText("Aucun mouvement.", MARGIN + 10f, y + 12f, paint(9f, TEXT_MUTED))
            y += 20f
        } else {
            recent.forEach { entry ->
                canvas.drawText(formatDate(entry.at), MARGIN + 10f, y + 12f, rowText)
                canvas.drawText(movementLabel(entry), MARGIN + 90f, y + 12f, rowText)
                drawRightAligned(
                    canvas, formatDa(-entry.amount), MARGIN + CONTENT_W - 10f, y + 12f, rowBold,
                )
                y += 15f
                canvas.drawLine(MARGIN, y - 4f, MARGIN + CONTENT_W, y - 4f, strokePaint(BORDER))
            }
            if (entries.size > recent.size) {
                canvas.drawText(
                    "… et ${entries.size - recent.size} autres mouvements non affichés.",
                    MARGIN + 10f, y + 12f, paint(8f, TEXT_MUTED),
                )
                y += 14f
            }
        }

        drawFooter(canvas)
        doc.finishPage(page)

        return write(doc, outputDir, "releve-${sanitize(parent.code)}.pdf")
    }

    // ── Shared drawing primitives (mirror desktop receipt-pdf/shared.ts) ────

    private fun drawHeader(canvas: Canvas, title: String) {
        val fill = paint(1f, BRAND_BLUE).apply { style = Paint.Style.FILL }
        canvas.drawRect(0f, 0f, PAGE_W.toFloat(), BRAND_BAR_H, fill)
        canvas.drawText("EL-IMTIYAZ", MARGIN, 40f, paint(22f, WHITE, bold = true))
        canvas.drawText(
            "Établissement Scolaire Privé — El-Imtiyaz Boumerdès",
            MARGIN, 58f, paint(9f, WHITE),
        )
        val titlePaint = paint(14f, WHITE, bold = true)
        canvas.drawText(
            title,
            PAGE_W - MARGIN - titlePaint.measureText(title),
            45f,
            titlePaint,
        )
    }

    private fun drawFooter(canvas: Canvas, pageNo: Int = 1) {
        val lineY = PAGE_H - 60f
        canvas.drawLine(MARGIN, lineY, PAGE_W - MARGIN, lineY, strokePaint(BORDER))
        val footer = paint(8f, TEXT_MUTED)
        canvas.drawText(
            "El-Imtiyaz · Boumerdès, Algérie · Email: contact@elimtiyaz.dz",
            MARGIN, PAGE_H - 44f, footer,
        )
        val generated = "Généré le ${formatDateTime(Instant.now().toString())} · Page $pageNo"
        canvas.drawText(
            generated,
            PAGE_W - MARGIN - 200f, PAGE_H - 44f, footer,
        )
    }

    private fun drawBox(
        canvas: Canvas,
        x: Float, y: Float, w: Float, h: Float,
        fill: Int? = null,
        stroke: Int? = null,
    ) {
        fill?.let {
            canvas.drawRect(x, y, x + w, y + h, paint(1f, it).apply { style = Paint.Style.FILL })
        }
        stroke?.let {
            canvas.drawRect(
                x, y, x + w, y + h,
                paint(1f, it).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 0.5f
                },
            )
        }
    }

    private fun drawKeyValue(canvas: Canvas, x: Float, y: Float, label: String, value: String) {
        canvas.drawText(label, x, y, paint(9f, TEXT_MUTED))
        canvas.drawText(value, x + 110f, y, paint(10f, TEXT_PRIMARY))
    }

    private fun drawSectionTitle(canvas: Canvas, title: String, y: Float) {
        canvas.drawText(title, MARGIN, y, paint(10f, TEXT_PRIMARY, bold = true))
    }

    private fun drawSummaryBox(
        canvas: Canvas,
        x: Float, y: Float, w: Float,
        label: String, value: String, accent: Int,
    ) {
        drawBox(canvas, x, y, w, 50f, fill = BG_SOFT, stroke = BORDER)
        canvas.drawText(label, x + 12f, y + 18f, paint(9f, TEXT_MUTED))
        canvas.drawText(value, x + 12f, y + 38f, paint(12f, accent, bold = true))
    }

    private fun drawRightAligned(canvas: Canvas, text: String, rightX: Float, y: Float, p: Paint) {
        canvas.drawText(text, rightX - p.measureText(text), y, p)
    }

    // ── Paint / text helpers ────────────────────────────────────────────────

    private fun paint(size: Float, color: Int, bold: Boolean = false): Paint = Paint().apply {
        this.color = color
        textSize = size
        typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.SANS_SERIF
        isAntiAlias = true
    }

    private fun strokePaint(color: Int, width: Float = 0.5f): Paint = Paint().apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
        isAntiAlias = true
    }

    /** Greedy word-wrap using the measuring paint (mirrors desktop `wrapText`). */
    private fun wrapText(text: String, p: Paint, maxWidth: Float): List<String> {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val lines = mutableListOf<String>()
        var current = ""
        words.forEach { word ->
            val test = if (current.isEmpty()) word else "$current $word"
            if (p.measureText(test) > maxWidth && current.isNotEmpty()) {
                lines.add(current)
                current = word
            } else {
                current = test
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        return lines
    }

    /** Deterministic DZD formatting: `25 000 DA` (space thousands separator). */
    internal fun formatDa(centimes: Long): String {
        val negative = centimes < 0
        val dzd = kotlin.math.abs(centimes) / 100
        val digits = dzd.toString()
        val sb = StringBuilder()
        var count = 0
        for (i in digits.length - 1 downTo 0) {
            sb.append(digits[i])
            count++
            if (count % 3 == 0 && i > 0) sb.append(' ')
        }
        val grouped = sb.reverse().toString()
        return (if (negative) "-" else "") + grouped + " DA"
    }

    private fun formatDate(iso: String?): String = try {
        DATE_FMT.withZone(ZoneId.systemDefault()).format(Instant.parse(iso))
    } catch (_: Exception) {
        iso?.take(10) ?: "—"
    }

    private fun formatDateTime(iso: String?): String = try {
        DATETIME_FMT.withZone(ZoneId.systemDefault()).format(Instant.parse(iso))
    } catch (_: Exception) {
        iso?.take(16)?.replace('T', ' ') ?: "—"
    }

    /** Keep only filename-safe characters. */
    private fun sanitize(raw: String): String =
        raw.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60)

    private fun write(doc: PdfDocument, outputDir: File, fileName: String): File {
        outputDir.mkdirs()
        val file = File(outputDir, fileName)
        FileOutputStream(file).use { out -> doc.writeTo(out) }
        doc.close()
        return file
    }

    // ── French labels (mirror desktop PAYMENT_*_LABELS_FR) ──────────────────

    private fun methodLabel(method: PaymentMethod): String = when (method) {
        PaymentMethod.CASH -> "Espèces"
        PaymentMethod.CHECK -> "Chèque"
        PaymentMethod.TRANSFER -> "Virement"
    }

    private fun statusLabel(status: PaymentStatus): String = when (status) {
        PaymentStatus.PAID -> "Payé"
        PaymentStatus.PENDING -> "En attente"
        PaymentStatus.PARTIAL -> "Partiel"
        PaymentStatus.OVERDUE -> "En retard"
        PaymentStatus.REFUNDED -> "Remboursé"
        PaymentStatus.CANCELLED -> "Annulé"
        PaymentStatus.PENDING_CLEARANCE -> "En cours d'encaissement"
        PaymentStatus.UNPAID -> "Non payé"
    }

    internal fun categoryLabel(category: PaymentCategory): String = when (category) {
        PaymentCategory.TUITION -> "Scolarité"
        PaymentCategory.TRANSPORT -> "Transport"
        PaymentCategory.CANTEEN -> "Cantine"
        PaymentCategory.UNIFORM -> "Uniforme"
        PaymentCategory.BOOKS -> "Livres"
        PaymentCategory.EXTRACURRICULAR -> "Activité parascolaire"
        PaymentCategory.THERAPY_PSYCHOLOGY -> "Psychologie"
        PaymentCategory.THERAPY_SPEECH -> "Orthophonie"
        PaymentCategory.SECOND_APRON -> "2ème Tablier"
        PaymentCategory.PARENT_CREDIT -> "Crédit Parent"
        PaymentCategory.OTHER -> "Autre"
    }

    private fun movementLabel(entry: LedgerEntry): String {
        val type = when (entry.type) {
            LedgerEntryType.CHARGE -> "Facturation"
            LedgerEntryType.PAYMENT -> "Paiement"
            LedgerEntryType.ADJUSTMENT -> "Ajustement"
            LedgerEntryType.REFUND -> "Remboursement"
            LedgerEntryType.REVERSAL -> "Extourne"
            LedgerEntryType.TRANSFER -> "Transfert"
        }
        return "$type — ${entry.description}".take(70)
    }
}
