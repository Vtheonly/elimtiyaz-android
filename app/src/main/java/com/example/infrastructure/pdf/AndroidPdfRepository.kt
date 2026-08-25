package com.example.infrastructure.pdf

import android.content.Context
import com.example.core.Errors
import com.example.core.LedgerEngine
import com.example.core.Result
import com.example.core.agingBucketFromDays
import com.example.core.computeOverallGpa
import com.example.domain.repository.ClassRepository
import com.example.domain.repository.ExpenseRepository
import com.example.domain.repository.GradeRepository
import com.example.domain.repository.LedgerRepository
import com.example.domain.repository.ParentRepository
import com.example.domain.repository.PaymentRepository
import com.example.domain.repository.PdfRepository
import com.example.domain.repository.PersonnelRepository
import com.example.domain.repository.StudentRepository
import com.example.domain.repository.SubjectRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

/**
 * Room-backed [PdfRepository] — assembles the domain data for each document
 * (payment + parent + student + ledger breakdown, or parent + ledger summary
 * + entries) and delegates the actual Canvas rendering to [PdfGenerator].
 *
 * Files are written to `{cacheDir}/pdf/` and returned for sharing via
 * FileProvider (see res/xml/file_paths.xml + AndroidManifest.xml).
 */
@Singleton
class AndroidPdfRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val paymentRepository: PaymentRepository,
    private val parentRepository: ParentRepository,
    private val studentRepository: StudentRepository,
    private val ledgerRepository: LedgerRepository,
    private val expenseRepository: ExpenseRepository,
    private val personnelRepository: PersonnelRepository,
    private val classRepository: ClassRepository,
    private val gradeRepository: GradeRepository,
    private val subjectRepository: SubjectRepository,
) : PdfRepository {

    override suspend fun generatePaymentReceipt(paymentId: String): Result<File> {
        val payment = paymentRepository.observeById(paymentId).firstOrNull()
            ?: return Result.Err(Errors.notFound("Paiement $paymentId introuvable"))
        val parent = parentRepository.observeById(payment.parentId).firstOrNull()
        val student = payment.studentId?.let { sid ->
            studentRepository.observeById(sid).firstOrNull()
        }
        // Breakdown = every ledger entry whose sourceId is this payment
        // (the payment entry itself + any parent-credit overflow entry).
        val breakdown = ledgerRepository.observeByParent(payment.parentId)
            .firstOrNull()
            .orEmpty()
            .filter { it.sourceId == payment.id }
        return try {
            val file = PdfGenerator.generatePaymentReceipt(
                payment = payment,
                parent = parent,
                student = student,
                breakdown = breakdown,
                outputDir = pdfDir(),
            )
            Result.Ok(file)
        } catch (e: Exception) {
            Result.Err(Errors.unknown(
                "Payment receipt PDF failed: ${e.message ?: e::class.simpleName}",
                userMessage = "Échec de génération du reçu PDF.",
            ))
        }
    }

    override suspend fun generateAccountStatement(parentId: String): Result<File> {
        val parent = parentRepository.observeById(parentId).firstOrNull()
            ?: return Result.Err(Errors.notFound("Parent $parentId introuvable"))
        val summary = when (val r = ledgerRepository.summary(parentId)) {
            is Result.Ok -> r.value
            is Result.Err -> return r
        }
        val entries = ledgerRepository.observeByParent(parentId).firstOrNull().orEmpty()
        return try {
            val file = PdfGenerator.generateAccountStatement(
                parent = parent,
                summary = summary,
                entries = entries,
                outputDir = pdfDir(),
            )
            Result.Ok(file)
        } catch (e: Exception) {
            Result.Err(Errors.unknown(
                "Account statement PDF failed: ${e.message ?: e::class.simpleName}",
                userMessage = "Échec de génération du relevé PDF.",
            ))
        }
    }

    /**
     * FIX (dead buttons): the five "Générer" buttons of the Rapports screen
     * previously only showed a snackbar and delayed 2 s — nothing was ever
     * produced. Each report is now REALLY assembled from live Room data and
     * rendered as a paginated A4 PDF via [PdfGenerator.generateTableReport].
     */
    override suspend fun generateMacroReport(reportId: String): Result<File> = try {
        val file = when (reportId) {
            "revenu-mensuel" -> revenueReport()
            "creances-agees" -> debtAgingReport()
            "effectifs-niveau" -> enrollmentReport()
            "depenses-categorie" -> expensesReport()
            "annuaire-personnel" -> personnelDirectoryReport()
            else -> return Result.Err(Errors.notFound("Rapport inconnu : $reportId"))
        }
        Result.Ok(file)
    } catch (e: Exception) {
        Result.Err(Errors.unknown(
            "Macro report PDF failed: ${e.message ?: e::class.simpleName}",
            userMessage = "Échec de génération du rapport PDF.",
        ))
    }

    /**
     * Student term bulletin — the entity-specific report that belongs in the
     * student's Notes & Bulletins drawer (desktop spec §5.1). Every figure is
     * computed with the canonical engines; no client-side re-derivation.
     */
    override suspend fun generateStudentBulletin(studentId: String, term: String, academicYear: String): Result<File> {
        return try {
            val student = studentRepository.observeById(studentId).firstOrNull()
                ?: return Result.Err(Errors.notFound("Élève $studentId introuvable"))
            val assessments = gradeRepository.observeForStudent(studentId, term, academicYear).first()
            if (assessments.isEmpty()) {
                return Result.Err(Errors.notFound("Aucune note saisie pour $term $academicYear"))
            }
            val subjects = subjectRepository.observe().first()
            val subjectById = subjects.associateBy { it.id }

            // Canonical GPA (coefficient-weighted, extracurricular excluded).
            val gpa = computeOverallGpa(assessments)
            val mention = mentionFor(gpa)

            // Class rank + class average (canonical GPA per classmate).
            var rankLabel = "—"
            var classAverageLabel = "—"
            student.classId?.let { cid ->
                val classAssessments = gradeRepository.observeForClass(cid, term, academicYear).first()
                val classNames = classRepository.observe().first().firstOrNull { it.id == cid }?.name
                val byStudent = classAssessments.groupBy { it.studentId }
                val gpas = byStudent.entries
                    .map { (sid, list) -> sid to computeOverallGpa(list) }
                    .filter { it.second != null }
                    .sortedByDescending { it.second!! }
                val rank = gpas.indexOfFirst { it.first == studentId }
                if (rank >= 0) {
                    rankLabel = "${rank + 1}${ordinalSuffixFr(rank + 1)} / ${gpas.size}${classNames?.let { " — $it" } ?: ""}"
                }
                gpas.mapNotNull { it.second }.takeIf { it.isNotEmpty() }?.let { averages ->
                    classAverageLabel = "%.2f / 20".format(averages.average())
                }
            }

            val rows = assessments.sortedWith(
                compareByDescending<com.example.domain.model.Assessment> { it.isExtracurricular }
                    .thenBy { subjectById[it.subjectId]?.name ?: it.subjectId },
            ).map { a ->
                listOf(
                    buildString {
                        append(subjectById[a.subjectId]?.name ?: a.subjectId)
                        if (a.isExtracurricular) append(" (hors programme)")
                    },
                    trim(a.coefficient),
                    a.devoir1?.let { trim(it) } ?: "—",
                    a.devoir2?.let { trim(it) } ?: "—",
                    a.examen?.let { trim(it) } ?: "—",
                    a.subjectAverage?.let { "%.2f".format(it) } ?: "—",
                )
            }

            val file = PdfGenerator.generateTableReport(
                title = "Bulletin $term",
                columns = listOf("Matière", "Coef", "D1", "D2", "Examen", "Moyenne"),
                rows = rows,
                summaryLines = listOf(
                    "Élève" to student.fullName,
                    "Moyenne générale" to (gpa?.let { "%.2f / 20".format(it) } ?: "En attente"),
                    "Mention" to mention,
                    "Rang" to rankLabel,
                    "Moyenne de classe" to classAverageLabel,
                    "Année" to academicYear,
                ),
                outputDir = pdfDir(),
                fileName = "bulletin-${sanitizeFileName(student.fullName)}-$term.pdf",
            )
            Result.Ok(file)
        } catch (e: Exception) {
            Result.Err(Errors.unknown(
                "Student bulletin PDF failed: ${e.message ?: e::class.simpleName}",
                userMessage = "Échec de génération du bulletin PDF.",
            ))
        }
    }

    /** Standard French bulletin mention — presentation only, no logic change. */
    private fun mentionFor(gpa: Double?): String = when {
        gpa == null -> "En attente des examens"
        gpa >= 16 -> "Très Bien"
        gpa >= 14 -> "Bien"
        gpa >= 12 -> "Assez Bien"
        gpa >= 10 -> "Passable"
        else -> "Insuffisant"
    }

    /** 1er / 2e / 3e … — French ordinal suffix. */
    private fun ordinalSuffixFr(rank: Int): String = when {
        rank == 1 -> "er"
        else -> "e"
    }

    private fun sanitizeFileName(raw: String): String = raw.replace(Regex("[^A-Za-z0-9-_]"), "_")

    private fun trim(v: Double): String = if (v == v.toLong().toDouble()) "${v.toLong()}" else "$v"

    // ── Report builders (all values computed from REAL repository data) ─────

    private suspend fun revenueReport(): File {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val monthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
        val nextMonthStart = monthStart.plusMonths(1)
        val startIso = monthStart.toInstant().toString()
        val endIso = nextMonthStart.toInstant().toString()

        val payments = paymentRepository.observe().first().filter {
            it.status == com.example.core.PaymentStatus.PAID &&
                it.collectedAt >= startIso && it.collectedAt < endIso
        }

        val methodLabels = mapOf(
            "cash" to "Espèces",
            "check" to "Chèques",
            "transfer" to "Virements",
        )
        val rows = methodLabels.map { (code, label) ->
            val matching = payments.filter { it.method.code == code }
            listOf(
                label,
                "${matching.size}",
                "${PdfGenerator.formatDa(matching.sumOf { it.amount })} DA",
            )
        } + payments
            .groupBy { it.category.code }
            .toList()
            .sortedByDescending { it.second.sumOf { p -> p.amount } }
            .map { (category, list) ->
                listOf(
                    "Catégorie : $category",
                    "${list.size}",
                    "${PdfGenerator.formatDa(list.sumOf { it.amount })} DA",
                )
            }

        val todayIso = LocalDate.now(ZoneOffset.UTC).toString()
        val todayTotal = payments.filter { it.collectedAt.startsWith(todayIso) }.sumOf { it.amount }
        return PdfGenerator.generateTableReport(
            title = "Revenu Mensuel",
            columns = listOf("Mode / Catégorie", "Transactions", "Montant"),
            rows = rows,
            summaryLines = listOf(
                "Encaissé ce mois" to "${PdfGenerator.formatDa(payments.sumOf { it.amount })} DA",
                "Encaissé aujourd'hui" to "${PdfGenerator.formatDa(todayTotal)} DA",
                "Paiements" to "${payments.size}",
            ),
            outputDir = pdfDir(),
            fileName = "rapport-revenu-mensuel.pdf",
        )
    }

    private suspend fun debtAgingReport(): File {
        val parents = parentRepository.observe().first()
        val ledger = ledgerRepository.observe().first()

        val rows = parents.mapNotNull { parent ->
            val entries = ledger.filter { it.parentId == parent.id }
            val summary = LedgerEngine.computeParentSummary(entries, parent.id, parent.fullName)
            val outstanding = summary.totalOutstanding.coerceAtLeast(0L)
            if (outstanding <= 0L) return@mapNotNull null
            val days = LedgerEngine.maxDaysOverdueFromLedger(entries)
            listOf(
                parent.fullName,
                "${PdfGenerator.formatDa(outstanding)} DA",
                "${PdfGenerator.formatDa(summary.totalOverdue.coerceAtLeast(0L))} DA",
                agingLabel(agingBucketFromDays(days)),
                "$days j",
            )
        }.sortedByDescending { row ->
            row[1].filter { it.isDigit() }.replace(Regex("\\s+"), "").toLongOrNull() ?: 0L
        }

        val totalOutstanding = rows.size
        val totalAmount = parents.sumOf { parent ->
            LedgerEngine.computeParentSummary(ledger.filter { it.parentId == parent.id }, parent.id, parent.fullName)
                .totalOutstanding.coerceAtLeast(0L)
        }
        return PdfGenerator.generateTableReport(
            title = "Créances Âgées",
            columns = listOf("Famille", "Solde dû", "En retard", "Tranche d'âge", "Retard"),
            rows = rows,
            summaryLines = listOf(
                "Familles débitrices" to "$totalOutstanding",
                "Encours total" to "${PdfGenerator.formatDa(totalAmount)} DA",
            ),
            outputDir = pdfDir(),
            fileName = "rapport-creances-agees.pdf",
        )
    }

    private suspend fun enrollmentReport(): File {
        val students = studentRepository.observe().first().filter { it.status == "active" }
        val classes = classRepository.observe().first()

        val levelLabels = mapOf(
            "prescolaire" to "Préscolaire",
            "primaire" to "Primaire",
            "cem" to "CEM",
            "lycee" to "Lycée",
        )
        val byLevel = levelLabels.map { (code, label) ->
            listOf(label, "${students.count { it.level == code }}", "${classes.count { it.level == code }}")
        }
        val byClass = classes.map { cls ->
            listOf(
                "${cls.name}${cls.room?.let { " — salle $it" } ?: ""}",
                "${students.count { it.classId == cls.id }}",
                cls.capacity.takeIf { it > 0 }?.toString() ?: "—",
            )
        }

        return PdfGenerator.generateTableReport(
            title = "Effectifs par Niveau",
            columns = listOf("Niveau / Classe", "Élèves actifs", "Capacité"),
            rows = byLevel + byClass,
            summaryLines = listOf(
                "Élèves actifs" to "${students.size}",
                "Classes" to "${classes.size}",
            ),
            outputDir = pdfDir(),
            fileName = "rapport-effectifs-niveau.pdf",
        )
    }

    private suspend fun expensesReport(): File {
        val expenses = expenseRepository.observe().first()

        val rows = expenses
            .groupBy { it.category }
            .toList()
            .sortedByDescending { (_, list) -> list.sumOf { it.amount } }
            .map { (category, list) ->
                listOf(
                    category.replaceFirstChar { it.uppercase() },
                    "${list.size}",
                    "${list.count { it.status == "submitted" }}",
                    "${list.count { it.status == "approved" || it.status == "disbursed" || it.status == "settled" }}",
                    "${PdfGenerator.formatDa(list.sumOf { it.amount })} DA",
                )
            }

        val total = expenses.sumOf { it.amount }
        val pending = expenses.count { it.status == "submitted" }
        return PdfGenerator.generateTableReport(
            title = "Dépenses par Catégorie",
            columns = listOf("Catégorie", "Total", "En attente", "Approuvées", "Montant"),
            rows = rows,
            summaryLines = listOf(
                "Dépenses" to "${expenses.size}",
                "Montant total" to "${PdfGenerator.formatDa(total)} DA",
                "En attente" to "$pending",
            ),
            outputDir = pdfDir(),
            fileName = "rapport-depenses-categorie.pdf",
        )
    }

    private suspend fun personnelDirectoryReport(): File {
        val personnel = personnelRepository.observe().first().filter { it.status == "active" }

        val rows = personnel.map { p ->
            listOf(
                p.fullName,
                p.position,
                p.phone.ifBlank { "—" },
                p.email ?: "—",
                p.salary?.let { "${PdfGenerator.formatDa(it)} DA" } ?: "—",
            )
        }

        return PdfGenerator.generateTableReport(
            title = "Annuaire du Personnel",
            columns = listOf("Nom", "Poste", "Téléphone", "Email", "Salaire"),
            rows = rows,
            summaryLines = listOf(
                "Personnel actif" to "${personnel.size}",
                "Enseignants" to "${personnel.count { it.staffCategory == "teacher" }}",
            ),
            outputDir = pdfDir(),
            fileName = "rapport-annuaire-personnel.pdf",
        )
    }

    private fun agingLabel(bucket: String): String = when (bucket) {
        "0_30" -> "0–30 j"
        "31_60" -> "31–60 j"
        "61_90" -> "61–90 j"
        "91_180" -> "91–180 j"
        "180_plus" -> "180+ j"
        else -> bucket
    }

    private fun pdfDir(): File = File(context.cacheDir, "pdf")
}
