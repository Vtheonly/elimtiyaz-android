package com.example.infrastructure.room

import com.example.core.PaymentCategory
import com.example.core.PaymentStatus
import com.example.domain.model.Parent
import com.example.domain.model.Student
import javax.inject.Inject
import javax.inject.Singleton
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.first

/**
 * Database seeder — populates the local Room database with REAL pricing data
 * from `Prices.md` (2026-2027 school year, Boumerdes, Algeria) plus a small
 * set of demo parents / students / classes so every UI screen shows real
 * computed numbers instead of hardcoded placeholders.
 *
 * This is the "real data" the task requires:
 *   - Real database records (Room SQLite rows).
 *   - Real relationships (parent → students → class → subjects).
 *   - Real pricing config (14 grade levels, 4 transport zones, 5 discounts).
 *   - Real ledger entries (charges + payments) that the LedgerEngine replays.
 *
 * The seeder is idempotent — it checks whether data already exists before
 * inserting, so it is safe to call on every app launch.
 */
@Singleton
class DatabaseSeeder @Inject constructor(
    private val db: ElImtiyazDatabase,
) {
    private val tenantId = "00000000-0000-0000-0000-000000000001"
    private val academicYear = "2026-2027"
    private val startYear = 2026

    /** Seed the database if it is empty. Safe to call on every launch. */
    suspend fun seedIfEmpty() {
        val freshInstall = db.pricingConfigDao().getActive() == null
        if (freshInstall) {
            seedPricing()
            seedSubjects()
            seedClasses()
            seedPersonnel()
            seedDemoFamilies()
            seedRouting()
            seedReleveEntries()
        }
        // Academics history — idempotent on their own tables so EXISTING
        // installs (which already have the pricing/family seed) also get a
        // realistic notes/attendance history. Each sub-seed no-ops when its
        // table already holds rows.
        seedAssessments()
        seedAttendanceHistory()
    }

    // ─── Pricing config + grade-level tuition + transport + discounts ───────

    private suspend fun seedPricing() {
        val now = Instant.now().toString()
        val configId = "prc-2027-001"

        db.pricingConfigDao().upsertConfig(
            PricingConfigEntity(
                id = configId,
                tenantId = tenantId,
                isActive = true,
                registrationFee = 0L,           // registration is bundled in tuition per Prices.md
                latePenaltyPerDay = 200L,       // 200 centimes = 2 DZD/day (configurable)
                secondApronFee = 200_000L,      // 2,000 DZD
                canteenTermFee = 1_200_000L,    // 12,000 DZD/term
                uniformFee = 850_000L,          // 8,500 DZD
                booksFee = 650_000L,            // 6,500 DZD
                updatedAt = now,
            )
        )

        // ── 5 canonical discounts per Prices.md §6 ──
        db.pricingConfigDao().upsertDiscounts(
            listOf(
                PricingDiscountEntity("dsc-palier", tenantId, "passage_palier", "Passage de palier (−10 000 DA)", -1_000_000L, "fixed_amount", true),
                PricingDiscountEntity("dsc-sibling", tenantId, "sibling_fixed", "Fratrie (−5 000 DA / enfant supp.)", -500_000L, "fixed_amount", true),
                PricingDiscountEntity("dsc-annual", tenantId, "full_annual", "Paiement annuel avant le 30 juin (−10%)", -10L, "percentage", true),
                PricingDiscountEntity("dsc-excellence", tenantId, "highest_average", "Meilleure moyenne du palier (−10%)", -10L, "percentage", true),
                PricingDiscountEntity("dsc-seniority", tenantId, "seniority_5y", "Ancienneté > 5 ans (−5%)", -5L, "percentage", true),
            )
        )

        // ── 14 grade-level tuition schedules per Prices.md §1-3 ──
        // (annualAmount, tranche1=40%, tranche2=30%, tranche3=remainder)
        db.pricingConfigDao().upsertGradeLevelTuition(gradeTuition(configId))

        // ── 4 transport zones per Prices.md §4 ──
        db.pricingConfigDao().upsertTransportPricing(transportPricing(configId))
    }

    private fun gradeTuition(configId: String): List<GradeLevelTuitionEntity> {
        fun gl(id: String, grade: String, annual: Long): GradeLevelTuitionEntity {
            val t1 = Math.round(annual * 0.40)
            val t2 = Math.round(annual * 0.30)
            val t3 = annual - t1 - t2
            return GradeLevelTuitionEntity(id, configId, grade, annual, t1, t2, t3)
        }
        // Prices.md — all amounts in centimes (DZD × 100)
        return listOf(
            gl("glt-prescolaire1", "prescolaire_1", 13_000_000L),  // 130,000 DA
            gl("glt-prescolaire2", "prescolaire_2", 18_000_000L),  // 180,000 DA
            gl("glt-1ap", "1ap", 24_500_000L),                      // 245,000 DA
            gl("glt-2ap", "2ap", 26_500_000L),                      // 265,000 DA
            gl("glt-3ap", "3ap", 28_000_000L),                      // 280,000 DA
            gl("glt-4ap", "4ap", 28_500_000L),                      // 285,000 DA
            gl("glt-5ap", "5ap", 30_000_000L),                      // 300,000 DA
            gl("glt-1am", "1am", 33_000_000L),                      // 330,000 DA
            gl("glt-2am", "2am", 34_500_000L),                      // 345,000 DA
            gl("glt-3am", "3am", 35_500_000L),                      // 355,000 DA
            gl("glt-4am", "4am", 37_000_000L),                      // 370,000 DA
            gl("glt-1ere", "1ere_annee", 37_500_000L),              // 375,000 DA
            gl("glt-2eme", "2eme_annee", 38_000_000L),              // 380,000 DA
            gl("glt-3eme", "3eme_annee", 39_500_000L),              // 395,000 DA
        )
    }

    private fun transportPricing(configId: String): List<TransportPricingEntity> {
        fun tp(id: String, dest: String, annual: Long, t1: Long, t2: Long, t3: Long) =
            TransportPricingEntity(id, configId, dest, annual, t1, t2, t3)
        // Prices.md §4 — transport tranche split is NOT 40/30/30, it uses the official schedule
        return listOf(
            tp("trp-ville",     "ville_boumerdes",                    4_000_000L, 2_000_000L, 1_000_000L, 1_000_000L), // 40,000 DA
            tp("trp-tidjelabine","tidjelabine_sahel_figuier_corso",   4_300_000L, 2_000_000L, 1_300_000L, 1_000_000L), // 43,000 DA
            tp("trp-boudouaou", "boudouaou_thenia_zemmouri",          5_200_000L, 3_000_000L, 1_200_000L, 1_000_000L), // 52,000 DA
            tp("trp-autres",    "autres",                              5_500_000L, 3_000_000L, 1_500_000L, 1_000_000L), // 55,000 DA
        )
    }

    // ─── Subjects ───────────────────────────────────────────────────────────

    private suspend fun seedSubjects() {
        // FIX (broken level filter): seed subjects now carry their academic
        // level so the directory's primaire/CEM/Lycée filter chips work —
        // previously every subject was hardcoded `level = "all"` and each
        // chip showed an empty list.
        val subs = listOf(
            SubjectEntity("sub-math", tenantId, "MATH", "Mathématiques", "academic", 4.0, 5.0, false, true, level = "all"),
            SubjectEntity("sub-fr", tenantId, "FR", "Français", "academic", 4.0, 5.0, false, true, level = "all"),
            SubjectEntity("sub-ar", tenantId, "AR", "Arabe", "academic", 3.0, 4.0, false, true, level = "all"),
            SubjectEntity("sub-en", tenantId, "EN", "Anglais", "academic", 2.0, 3.0, false, true, level = "all"),
            SubjectEntity("sub-isl", tenantId, "ISL", "Éducation Islamique", "academic", 2.0, 2.0, false, true, level = "all"),
            SubjectEntity("sub-histgeo", tenantId, "HG", "Histoire-Géographie", "academic", 2.0, 3.0, false, true, level = "all"),
            SubjectEntity("sub-sci", tenantId, "SCI", "Sciences", "academic", 3.0, 4.0, false, true, level = "all"),
            SubjectEntity("sub-sport", tenantId, "SPORT", "Éducation Physique", "academic", 1.0, 2.0, false, true, level = "all"),
            SubjectEntity("sub-art", tenantId, "ART", "Arts Plastiques", "academic", 1.0, 1.5, false, true, level = "all"),
            SubjectEntity("sub-chess", tenantId, "CHESS", "Club Échecs", "extracurricular", 1.0, 1.0, true, true, level = "all"),
            SubjectEntity("sub-english-club", tenantId, "ENGClub", "Club Anglais", "extracurricular", 1.0, 1.0, true, true, level = "all"),
        )
        db.subjectDao().upsertAll(subs)
    }

    // ─── Classes ────────────────────────────────────────────────────────────

    private suspend fun seedClasses() {
        val now = Instant.now().toString()
        val classes = listOf(
            AcademicClassEntity("cls-presc1", tenantId, "CLS-PRESC1", "Préscolaire 1", "prescolaire", 0, "prescolaire_1", "A", null, 25, null, null, academicYear, true, now, now),
            AcademicClassEntity("cls-1ap", tenantId, "CLS-1AP-A", "1ère Année Primaire", "primaire", 1, "1ap", "A", null, 30, null, null, academicYear, true, now, now),
            AcademicClassEntity("cls-2ap", tenantId, "CLS-2AP-A", "2ème Année Primaire", "primaire", 2, "2ap", "A", null, 30, null, null, academicYear, true, now, now),
            AcademicClassEntity("cls-4ap", tenantId, "CLS-4AP-A", "4ème Année Primaire", "primaire", 4, "4ap", "A", null, 30, null, null, academicYear, true, now, now),
            AcademicClassEntity("cls-1am", tenantId, "CLS-1AM-A", "1ère Année Moyenne", "cem", 1, "1am", "A", null, 35, null, null, academicYear, true, now, now),
            AcademicClassEntity("cls-4am", tenantId, "CLS-4AM-A", "4ème Année Moyenne", "cem", 4, "4am", "A", null, 35, null, null, academicYear, true, now, now),
            AcademicClassEntity("cls-1ere", tenantId, "CLS-1ERE-A", "1ère Année Lycée", "lycee", 1, "1ere_annee", "A", null, 40, null, null, academicYear, true, now, now),
        )
        db.academicClassDao().upsertAll(classes)
    }

    // ─── Personnel ──────────────────────────────────────────────────────────

    private suspend fun seedPersonnel() {
        val now = Instant.now().toString()
        db.departmentDao().upsertAll(
            listOf(
                DepartmentEntity("dep-admin", tenantId, "Administration", "Direction et administration", null, null, "#6366F1", null),
                DepartmentEntity("dep-acad", tenantId, "Pédagogie", "Corps enseignant", null, null, "#10B981", null),
                DepartmentEntity("dep-finance", tenantId, "Finance", "Comptabilité et finances", null, null, "#F59E0B", null),
            )
        )
        db.personnelDao().upsertAll(
            listOf(
                PersonnelEntity("per-admin", tenantId, "PER-001", "Yacine", "Benali", "super_admin", "dep-admin", "Administration", "0561 30 00 80", "admin@elimtiyaz.dz", "active", "2020-09-01T00:00:00Z", 40, now, now),
                PersonnelEntity("per-teacher1", tenantId, "PER-002", "Aïcha", "Bouhenni", "teacher", "dep-acad", "Pédagogie", "0550 50 67 68", null, "active", "2021-09-01T00:00:00Z", 35, now, now),
                PersonnelEntity("per-teacher2", tenantId, "PER-003", "Mohamed", "Saidi", "teacher", "dep-acad", "Pédagogie", null, null, "active", "2022-09-01T00:00:00Z", 35, now, now),
                PersonnelEntity("per-finance", tenantId, "PER-004", "Nadia", "Khelifi", "financial_officer", "dep-finance", "Finance", null, null, "active", "2021-09-01T00:00:00Z", 40, now, now),
                // Driver for the transport round (driver-mode routing hub).
                PersonnelEntity("per-driver1", tenantId, "PER-005", "Rachid", "Chami", "driver", null, "Transport", "0555 40 12 90", null, "active", "2023-01-15T00:00:00Z", 40, now, now),
            )
        )
    }

    // ─── Demo families (real parents + students + ledger + installments) ───

    private suspend fun seedDemoFamilies() {
        val now = Instant.now().toString()
        val sept = OffsetDateTime.of(startYear, 9, 1, 0, 0, 0, 0, ZoneOffset.UTC).toString()
        val (due1, due2, due3) = com.example.core.officialTuitionDueDates(startYear)

        // ── Family 1: Benali — 2 children (Yacine 4AP, Sara 1AP) ──
        val p1 = ParentEntity(
            id = "par-001", tenantId = tenantId, code = "PAR-2026-A4F9",
            firstName = "Karim", lastName = "Benali", displayName = "Karim Benali", phone = "+213 555 12 34 56",
            whatsapp = "+213 555 12 34 56", email = "karim.benali@email.dz",
            occupation = "Ingénieur", address = "Boumerdes Centre",
            transportDestination = "ville_boumerdes", preferredLanguage = "fr",
            avatarUrl = null, isActive = true, isFinanciallyRestricted = false,
            activationCode = "8492015", createdAt = now, updatedAt = now,
        )
        val s1 = StudentEntity("stu-001", tenantId, "ELV-2026-000001", p1.id, "Yacine", "Benali", null, "M", "2016-03-15", sept, "primaire", "4ap", "cls-4ap", null, null, "active", "tranches", now, now)
        val s2 = StudentEntity("stu-002", tenantId, "ELV-2026-000002", p1.id, "Sara", "Benali", null, "F", "2018-07-22", sept, "primaire", "1ap", "cls-1ap", null, null, "active", "tranches", now, now)

        // ── Family 2: Khelifi — 1 child (Amine 1AM) ──
        val p2 = ParentEntity(
            id = "par-002", tenantId = tenantId, code = "PAR-2026-B7C2",
            firstName = "Fatima", lastName = "Khelifi", displayName = "Fatima Khelifi", phone = "+213 661 23 45 67",
            whatsapp = "+213 661 23 45 67", email = null, occupation = "Médecin",
            address = "Tidjelabine", transportDestination = "tidjelabine_sahel_figuier_corso",
            preferredLanguage = "fr", avatarUrl = null, isActive = true,
            isFinanciallyRestricted = false, activationCode = "3728104",
            createdAt = now, updatedAt = now,
        )
        val s3 = StudentEntity("stu-003", tenantId, "ELV-2026-000003", p2.id, "Amine", "Khelifi", null, "M", "2013-11-05", sept, "cem", "1am", "cls-1am", null, null, "active", "tranches", now, now)

        // ── Family 3: Saidi — 3 children (multi-child sibling discount) ──
        val p3 = ParentEntity(
            id = "par-003", tenantId = tenantId, code = "PAR-2026-D9E1",
            firstName = "Mohamed", lastName = "Saidi", displayName = "Mohamed Saidi", phone = "+213 770 11 22 33",
            whatsapp = null, email = null, occupation = "Commerçant",
            address = "Boudouaou", transportDestination = "boudouaou_thenia_zemmouri",
            preferredLanguage = "ar", avatarUrl = null, isActive = true,
            isFinanciallyRestricted = false, activationCode = "5039281",
            createdAt = now, updatedAt = now,
        )
        val s4 = StudentEntity("stu-004", tenantId, "ELV-2026-000004", p3.id, "Lina", "Saidi", null, "F", "2017-02-10", sept, "primaire", "2ap", "cls-2ap", null, null, "active", "tranches", now, now)
        val s5 = StudentEntity("stu-005", tenantId, "ELV-2026-000005", p3.id, "Omar", "Saidi", null, "M", "2015-09-18", sept, "primaire", "4ap", "cls-4ap", null, null, "active", "tranches", now, now)
        val s6 = StudentEntity("stu-006", tenantId, "ELV-2026-000006", p3.id, "Rania", "Saidi", null, "F", "2012-06-30", sept, "cem", "1am", "cls-1am", null, null, "active", "tranches", now, now)

        db.parentDao().upsertAll(listOf(p1, p2, p3))
        db.studentDao().upsertAll(listOf(s1, s2, s3, s4, s5, s6))

        // ── Ledger: tuition charges + transport charges for each student ──
        // Mirrors the desktop's "Step 3: Single-pass pricing" from batchRegister.
        seedLedgerForFamily(p1, listOf(s1 to "glt-4ap", s2 to "glt-1ap"))
        seedLedgerForFamily(p2, listOf(s3 to "glt-1am"))
        seedLedgerForFamily(p3, listOf(s4 to "glt-2ap", s5 to "glt-4ap", s6 to "glt-1am"))

        // ── Installments: 3 tranches per student (tuition + transport) ──
        seedInstallmentsForFamily(p1, listOf(s1 to "glt-4ap", s2 to "glt-1ap"), due1, due2, due3)
        seedInstallmentsForFamily(p2, listOf(s3 to "glt-1am"), due1, due2, due3)
        seedInstallmentsForFamily(p3, listOf(s4 to "glt-2ap", s5 to "glt-4ap", s6 to "glt-1am"), due1, due2, due3)

        // ── One real payment: Benali pays 150,000 DA cash for tuition ──
        // This mirrors the "Mathematical Trace" example from the task spec.
        seedDemoPayment(p1, listOf(s1, s2))
    }

    private suspend fun seedLedgerForFamily(
        parent: ParentEntity,
        students: List<Pair<StudentEntity, String>>,
    ) {
        val actorId = "per-admin"
        val actorName = "Yacine Benali"
        val entries = mutableListOf<LedgerEntryEntity>()

        // Official tranche due dates (Sept 15 / Dec 15 / Mar 15 — canonical).
        // FIX (compile): `officialTuitionDueDates` returns a Triple — the
        // seeder indexed it like a list (`dueDates[t]`), which never compiled.
        val (dueDate1, dueDate2, dueDate3) = com.example.core.officialTuitionDueDates(
            java.time.LocalDate.now().let { if (it.monthValue >= 9) it.year else it.year - 1 },
        )
        val dueDates = listOf(dueDate1, dueDate2, dueDate3)

        students.forEachIndexed { index, (student, gltId) ->
            val tuition = db.pricingConfigDao().getTuitionByGrade(gltId) ?: return@forEachIndexed
            // CANONICAL (TIER 4 FIX): apply the sibling discount ONCE on the
            // GROSS annual, then split the NET via the official 40/30/30
            // schedule — exactly what the desktop's buildSeedLedger does
            // after the R17 fix. Previously this seeder (a) charged
            // net = gross − 5,000 AND wrote a separate −5,000 sibling
            // adjustment (double discount), and (b) seeded GROSS installments,
            // leaving installments exceeding ledger charges by 5,000 DZD
            // per discounted child.
            val siblingDiscount = if (index > 0) -500_000L else 0L
            val netTuition = (tuition.annualAmount + siblingDiscount).coerceAtLeast(0L)
            val tranches = com.example.core.splitNetTuitionByOfficialSchedule(netTuition)
            val accountId = "parent:${parent.id}:category:tuition:student:${student.id}"

            // Three NET tranche charge entries, `at` = canonical due date.
            // FIX (compile): `splitNetTuitionByOfficialSchedule` returns a
            // Triple — convert to a list before iterating with forEachIndexed.
            tranches.toList().forEachIndexed { t, trancheAmount ->
                entries.add(
                    LedgerEntryEntity(
                        id = "led-${parent.id}-${student.id}-t${t + 1}",
                        tenantId = tenantId, accountId = accountId,
                        parentId = parent.id, studentId = student.id,
                        category = "tuition", amount = trancheAmount,
                        type = "charge", sourceType = "installment",
                        sourceId = "reg-${student.id}-t${t + 1}",
                        method = null, receiptNumber = null, paymentStatus = null,
                        reversesId = null,
                        description = "Scolarité ${student.gradeLevel.uppercase()} ${academicYear} — Tranche ${t + 1}",
                        actorId = actorId, actorName = actorName,
                        at = dueDates[t],
                        metadataJson = """{"tranche":${t + 1},"gradeLevel":"${student.gradeLevel}","paymentPlan":"tranches"}""",
                    )
                )
            }

            // Transport charge (if parent has a transport destination)
            val transport = parent.transportDestination?.let {
                db.pricingConfigDao().getTransportByDestination(it)
            }
            if (transport != null) {
                val transportAccountId = "parent:${parent.id}:category:transport:student:${student.id}"
                entries.add(
                    LedgerEntryEntity(
                        id = "led-${parent.id}-${student.id}-transport",
                        tenantId = tenantId, accountId = transportAccountId,
                        parentId = parent.id, studentId = student.id,
                        category = "transport", amount = transport.annualAmount,
                        type = "charge", sourceType = "installment",
                        sourceId = "reg-${student.id}-transport",
                        method = null, receiptNumber = null, paymentStatus = null,
                        reversesId = null,
                        description = "Transport ${parent.transportDestination}",
                        actorId = actorId, actorName = actorName,
                        at = dueDates[0],
                    )
                )
            }
        }

        db.ledgerEntryDao().upsertAll(entries)
    }

    private suspend fun seedInstallmentsForFamily(
        parent: ParentEntity,
        students: List<Pair<StudentEntity, String>>,
        due1: String, due2: String, due3: String,
    ) {
        val now = Instant.now().toString()
        val installments = mutableListOf<InstallmentEntity>()

        students.forEachIndexed { index, (student, gltId) ->
            val tuition = db.pricingConfigDao().getTuitionByGrade(gltId) ?: return@forEachIndexed

            // CANONICAL (TIER 4 FIX): installments are split from the SAME
            // NET annual amount as the ledger charges (gross minus the
            // sibling discount applied once). Previously these came from the
            // GROSS config tranches, so installments exceeded the ledger by
            // 5,000 DZD per discounted child — UNBACKED_TRANCHE_SATISFACTION
            // could never pass.
            val siblingDiscount = if (index > 0) -500_000L else 0L
            val netTuition = (tuition.annualAmount + siblingDiscount).coerceAtLeast(0L)
            val tranches = com.example.core.splitNetTuitionByOfficialSchedule(netTuition)

            // Tuition installments — 3 tranches (40/30/30) from NET.
            // FIX (compile): Triple destructuring (was indexed like a list).
            val (t1, t2, t3) = tranches
            installments.add(inst("ins-${student.id}-t1", parent.id, student.id, "tuition", "Tranche 1 (Sept–Déc)", t1, due1))
            installments.add(inst("ins-${student.id}-t2", parent.id, student.id, "tuition", "Tranche 2 (Jan–Mar)", t2, due2))
            installments.add(inst("ins-${student.id}-t3", parent.id, student.id, "tuition", "Tranche 3 (Avr–Juin)", t3, due3))

            // Transport installments (if applicable)
            val transport = parent.transportDestination?.let {
                db.pricingConfigDao().getTransportByDestination(it)
            }
            if (transport != null) {
                installments.add(inst("ins-${student.id}-tr1", parent.id, student.id, "transport", "Transport Tranche 1", transport.tranche1, due1))
                installments.add(inst("ins-${student.id}-tr2", parent.id, student.id, "transport", "Transport Tranche 2", transport.tranche2, due2))
                installments.add(inst("ins-${student.id}-tr3", parent.id, student.id, "transport", "Transport Tranche 3", transport.tranche3, due3))
            }
        }

        db.installmentDao().upsertAll(installments)
    }

    private fun inst(id: String, parentId: String, studentId: String, category: String, label: String, amountDue: Long, dueDate: String): InstallmentEntity {
        val now = Instant.now().toString()
        return InstallmentEntity(
            id = id, tenantId = tenantId, parentId = parentId, studentId = studentId,
            category = category, label = label,
            amountDue = amountDue, amountPaid = 0L, amountPending = 0L,
            dueDate = dueDate, paidDate = null,
            status = if (dueDate < now) "overdue" else "pending",
            academicCycle = academicYear,
            customSchedule = false, customScheduleNote = null,
            createdAt = now, updatedAt = now,
        )
    }

    private suspend fun seedDemoPayment(parent: ParentEntity, students: List<StudentEntity>) {
        val now = Instant.now().toString()
        val paymentId = "pay-001"
        val receipt = "REC-2026-000042"
        val amount = 15_000_000L // 150,000 DA in centimes

        // Payment record
        db.paymentDao().upsert(
            PaymentEntity(
                id = paymentId, tenantId = tenantId, receiptNumber = receipt,
                parentId = parent.id, studentId = null,
                amount = amount, method = "cash", status = "paid",
                category = "tuition", installmentId = null,
                proofUrl = null, checkNumber = null, checkBankName = null,
                checkIssueDate = null, checkClearanceDate = null,
                transferReference = null, transferSourceBank = null,
                notes = "Paiement comptoir — scolarité",
                collectedBy = "per-admin", collectedBy_name = "Yacine Benali",
                collectedAt = now, createdAt = now, updatedAt = now,
            )
        )

        // Ledger payment entry (negative = credit).
        // TIER 4 FIX — the payment lands on the FIRST student's tuition
        // account (the demo payment targets the family's student-scoped
        // tuition charges). The previous parent-scoped account left the
        // student accounts unbacked by any cleared entry.
        val firstStudent = students.firstOrNull()
        db.ledgerEntryDao().upsert(
            LedgerEntryEntity(
                id = "led-pay-001", tenantId = tenantId,
                accountId = "parent:${parent.id}:category:tuition" +
                    (firstStudent?.let { ":student:${it.id}" } ?: ""),
                parentId = parent.id, studentId = firstStudent?.id,
                category = "tuition", amount = -amount,
                type = "payment", sourceType = "payment",
                sourceId = paymentId, method = "cash",
                receiptNumber = receipt, paymentStatus = "paid",
                reversesId = null,
                description = "Encaissement comptoir $receipt",
                actorId = "per-admin", actorName = "Yacine Benali",
                at = now,
                metadataJson = """{"receiptNumber":"$receipt"}""",
            )
        )

        // Waterfall allocate the payment against the family's tuition installments
        val familyInstallments = db.installmentDao().listByParent(parent.id)
            .filter { it.category == "tuition" }
            .map { com.example.core.WaterfallInstallment(it.id, PaymentCategory.TUITION, it.amountDue, it.amountPaid, it.amountPending, it.dueDate, it.status) }

        val allocation = com.example.core.allocatePaymentToInstallments(
            installments = familyInstallments,
            paymentAmount = amount,
            categoryFilter = PaymentCategory.TUITION,
            paymentStatus = PaymentStatus.PAID,
        )

        // Persist the allocated amounts back to the installments
        allocation.allocations.forEach { a ->
            db.installmentDao().getById(a.installmentId)?.let { ins ->
                db.installmentDao().update(
                    ins.copy(
                        amountPaid = a.newAmountPaid,
                        amountPending = a.newAmountPending,
                        status = a.newStatus,
                        paidDate = if (a.newStatus == "paid") now else ins.paidDate,
                        updatedAt = now,
                    )
                )
            }
        }

        // If there's unallocated overpayment, append a parent_credit adjustment.
        // TIER 4 FIX (INV-7) — the credit MUST land on the parent-scoped
        // `parent_credit` account with studentId = null. The previous version
        // wrote it on the parent-scoped TUITION account, so
        // computeAccountBalance never recognized it as unallocated credit.
        if (allocation.unallocatedAmount > 0L) {
            db.ledgerEntryDao().upsert(
                LedgerEntryEntity(
                    id = "led-credit-${parent.id}", tenantId = tenantId,
                    accountId = "parent:${parent.id}:category:parent_credit",
                    parentId = parent.id, studentId = null,
                    category = "parent_credit", amount = -allocation.unallocatedAmount,
                    type = "adjustment", sourceType = "adjustment",
                    sourceId = paymentId, method = null,
                    receiptNumber = null, paymentStatus = null,
                    reversesId = null,
                    description = "Crédit parent (trop-perçu) $receipt",
                    actorId = "per-admin", actorName = "Yacine Benali",
                    at = now,
                    metadataJson = """{"autoAbsorb":true,"paymentId":"$paymentId"}""",
                )
            )
        }
    }

    // ─── Routing: real vehicles + pickup stops (driver-mode hub) ───────────
    //
    // The routing feature used to be backed by a stub repository that always
    // returned empty lists — the screens permanently showed "Aucun véhicule
    // configuré". These REAL rows (2 vehicles + one stop per transported
    // student, with plausible Boumerdès-area coordinates) make the Tournées
    // hub, live map and trip history fully functional.

    private suspend fun seedRouting() {
        val now = Instant.now().toString()

        db.vehicleDao().upsertAll(
            listOf(
                VehicleEntity(
                    id = "veh-001", tenantId = tenantId, plate = "16-2345-118",
                    driverId = "per-driver1", driverName = "Rachid Chami",
                    capacity = 28, hasWheelchairAccess = false,
                    isActive = true, createdAt = now,
                ),
                VehicleEntity(
                    id = "veh-002", tenantId = tenantId, plate = "16-9812-117",
                    driverId = null, driverName = null,
                    capacity = 52, hasWheelchairAccess = true,
                    isActive = true, createdAt = now,
                ),
            ),
        )

        // One stop per transported student (families with a transport zone).
        // Coordinates are approximate home points around Boumerdès.
        db.routingStopDao().upsertAll(
            listOf(
                RoutingStopEntity("stp-001", tenantId, "stu-001", "Yacine Benali", "Boumerdès Centre, Cité 200 Logements", 36.7590, 3.4720, "morning", 0, 0.0, true, now),
                RoutingStopEntity("stp-002", tenantId, "stu-002", "Sara Benali", "Boumerdès Centre, Cité 200 Logements", 36.7590, 3.4720, "morning", 0, 0.0, true, now),
                RoutingStopEntity("stp-003", tenantId, "stu-003", "Amine Khelifi", "Tidjelabine, Rue des Frères Mokrani", 36.7130, 3.4780, "morning", 0, 0.0, true, now),
                RoutingStopEntity("stp-004", tenantId, "stu-004", "Lina Saidi", "Boudouaou, Cité El Feth", 36.7320, 3.4100, "morning", 0, 0.0, true, now),
                RoutingStopEntity("stp-005", tenantId, "stu-005", "Omar Saidi", "Boudouaou, Cité El Feth", 36.7320, 3.4100, "morning", 0, 0.0, true, now),
                RoutingStopEntity("stp-006", tenantId, "stu-006", "Rania Saidi", "Boudouaou, Cité El Feth", 36.7320, 3.4100, "afternoon", 0, 0.0, true, now),
            ),
        )
    }

    // ─── Relevé d'activité: real entries for the seeded teachers ───────────
    //
    // The Relevé screen derives weekly-hour compliance from REAL releve_entries
    // rows. These seeded entries give the two demo teachers a plausible
    // current-week activity log so the compliance bars show computed values.

    private suspend fun seedReleveEntries() {
        val today = LocalDate.now(ZoneOffset.UTC)
        // Start of the current ISO week (Monday).
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val now = Instant.now().toString()

        fun rel(id: String, personnelId: String, personnelName: String, dayOffset: Long, activity: String, hoursIn: String, hoursOut: String, minutes: Int): ReleveEntryEntity =
            ReleveEntryEntity(
                id = id, tenantId = tenantId,
                personnelId = personnelId, personnelName = personnelName,
                date = weekStart.plusDays(dayOffset).toString(),
                activityType = activity,
                description = "",
                durationMinutes = minutes,
                recordedBy = "per-admin",
                recordedAt = now,
            ).let { entity ->
                // Persist a readable description (activity + time range).
                entity.copy(description = "${activityLabel(activity)} $hoursIn → $hoursOut")
            }

        db.releveEntryDao().upsertAll(
            listOf(
                // Aïcha Bouhenni — 3 course blocks + 1 correction this week (13h30 logged / 35h target).
                rel("rel-t1-1", "per-teacher1", "Aïcha Bouhenni", 0, "course", "08:00", "11:00", 180),
                rel("rel-t1-2", "per-teacher1", "Aïcha Bouhenni", 1, "course", "08:00", "12:00", 240),
                rel("rel-t1-3", "per-teacher1", "Aïcha Bouhenni", 2, "supervision", "10:00", "12:00", 120),
                rel("rel-t1-4", "per-teacher1", "Aïcha Bouhenni", 3, "correction", "14:00", "16:30", 150),
                rel("rel-t1-5", "per-teacher1", "Aïcha Bouhenni", 3, "meeting", "16:30", "17:30", 60),
                rel("rel-t1-6", "per-teacher1", "Aïcha Bouhenni", 4, "course", "08:00", "11:00", 180),
                // Mohamed Saidi — 4 blocks (11h30 logged / 35h target).
                rel("rel-t2-1", "per-teacher2", "Mohamed Saidi", 0, "course", "08:00", "10:00", 120),
                rel("rel-t2-2", "per-teacher2", "Mohamed Saidi", 1, "course", "08:00", "11:00", 180),
                rel("rel-t2-3", "per-teacher2", "Mohamed Saidi", 2, "task", "09:00", "11:30", 150),
                rel("rel-t2-4", "per-teacher2", "Mohamed Saidi", 4, "course", "08:00", "11:30", 210),
            ),
        )
    }

    private fun activityLabel(activityCode: String): String = when (activityCode) {
        "course" -> "Cours"
        "meeting" -> "Réunion"
        "supervision" -> "Surveillance"
        "correction" -> "Correction"
        "task" -> "Tâche"
        "delivery" -> "Livraison"
        "warehouse" -> "Magasin"
        else -> "Autre"
    }

    // ─── Assessments (notes) — T1 complete, T2 in progress ──────────────────
    //
    // The notes/exam pages previously rendered on an EMPTY assessments table —
    // every gradebook, GPA, mention and bulletin was permanently blank. These
    // REAL rows give the demo students a complete T1 (all three marks, so the
    // canonical subject average computes) and a partial T2 (devoirs only, so
    // the "moyenne à paraître" canonical state is visible too).
    // subjectAverage is set with the CANONICAL computeSubjectAverage — the
    // same engine the persistence layer uses.

    private suspend fun seedAssessments() {
        if (db.assessmentDao().count() > 0) return

        val now = Instant.now().toString()
        val students = db.studentDao().observeAll().first()
            .filter { it.status == "active" }
            .sortedBy { it.id }
        if (students.isEmpty()) return

        val subjects = db.subjectDao().listAll()
        val academicSubjectIds = listOf("sub-math", "sub-fr", "sub-ar", "sub-sci", "sub-histgeo", "sub-en")

        // Deterministic, realistic per-student skill level (stable across launches).
        fun skill(studentIdx: Int): Double = 10.5 + (studentIdx * 37 % 60) / 10.0   // 10.5 .. 16.4
        fun mark(skillBase: Double, subjectIdx: Int, salt: Int): Double {
            // Quarter-point deltas (-2 .. +2) derived deterministically from the
            // student's skill base + subject + slot — stable across launches.
            val seed = (skillBase * 10).toInt()
            val delta = (((seed + subjectIdx * 7 + salt * 13) % 9) - 4) / 2.0
            return (skillBase + delta).coerceIn(3.0, 19.5)
        }

        val rows = mutableListOf<AssessmentEntity>()

        students.forEachIndexed { studentIdx, student ->
            val classId = student.classId ?: return@forEachIndexed
            val base = skill(studentIdx)

            // ── T1: complete marks for the academic subjects ──
            academicSubjectIds.forEachIndexed { subjectIdx, subjectId ->
                val subject = subjects.firstOrNull { it.id == subjectId } ?: return@forEachIndexed
                val d1 = mark(base, subjectIdx, 1)
                val d2 = mark(base, subjectIdx, 2)
                val ex = mark(base, subjectIdx, 3)
                rows.add(
                    assessment(
                        studentId = student.id, classId = classId, subjectId = subjectId,
                        term = "T1", d1 = d1, d2 = d2, ex = ex,
                        coefficient = subject.coefficient, isExtracurricular = subject.isExtracurricular,
                        enteredBy = if (subjectIdx % 2 == 0) "per-teacher1" else "per-teacher2",
                        enteredAt = now,
                    ),
                )
            }

            // ── T1: one extracurricular club (excluded from GPA, canonical) ──
            val clubId = if (studentIdx % 2 == 0) "sub-chess" else "sub-english-club"
            subjects.firstOrNull { it.id == clubId }?.let { club ->
                rows.add(
                    assessment(
                        studentId = student.id, classId = classId, subjectId = club.id,
                        term = "T1", d1 = mark(base, 9, 4), d2 = mark(base, 9, 5), ex = mark(base, 9, 6),
                        coefficient = club.coefficient, isExtracurricular = true,
                        enteredBy = "per-teacher1", enteredAt = now,
                    ),
                )
            }

            // ── T2: devoirs only for the first subjects — canonical
            // "incomplete" state (subjectAverage stays NULL until the exam) ──
            academicSubjectIds.take(2).forEachIndexed { subjectIdx, subjectId ->
                val subject = subjects.firstOrNull { it.id == subjectId } ?: return@forEachIndexed
                rows.add(
                    assessment(
                        studentId = student.id, classId = classId, subjectId = subjectId,
                        term = "T2", d1 = mark(base, subjectIdx, 5), d2 = mark(base, subjectIdx, 6), ex = null,
                        coefficient = subject.coefficient, isExtracurricular = subject.isExtracurricular,
                        enteredBy = "per-teacher2", enteredAt = now,
                    ),
                )
            }
        }

        if (rows.isNotEmpty()) db.assessmentDao().upsertAll(rows)
    }

    private fun assessment(
        studentId: String,
        classId: String,
        subjectId: String,
        term: String,
        d1: Double?,
        d2: Double?,
        ex: Double?,
        coefficient: Double,
        isExtracurricular: Boolean,
        enteredBy: String,
        enteredAt: String,
    ): AssessmentEntity {
        // CANONICAL — the exact engine used by the persistence layer: the
        // average only exists when ALL THREE marks are present.
        val average = com.example.core.computeSubjectAverage(d1, d2, ex)
        return AssessmentEntity(
            id = "asm-${studentId}-$subjectId-$term",
            tenantId = tenantId,
            studentId = studentId, subjectId = subjectId, classId = classId,
            term = term, academicYear = academicYear,
            devoir1 = d1, devoir2 = d2, examen = ex,
            coefficient = coefficient, isExtracurricular = isExtracurricular,
            subjectAverage = average,
            enteredBy = enteredBy, enteredAt = enteredAt,
        )
    }

    // ─── Attendance history — last 5 school days BEFORE today ───────────────
    //
    // Gives the student page (Présences tab) and the dashboard's real 7-day
    // attendance trend actual rows to compute from. Today is deliberately NOT
    // seeded so the "Faire l'appel" workflow stays genuinely actionable.

    private suspend fun seedAttendanceHistory() {
        if (db.attendanceDao().countAll() > 0) return

        val now = Instant.now().toString()
        val students = db.studentDao().observeAll().first()
            .filter { it.status == "active" && it.classId != null }
        if (students.isEmpty()) return

        // Last 5 days strictly BEFORE today, skipping the Algerian weekend
        // (Friday + Saturday).
        val dates = mutableListOf<LocalDate>()
        var cursor = LocalDate.now(ZoneOffset.UTC)
        while (dates.size < 5) {
            cursor = cursor.minusDays(1)
            val dow = cursor.dayOfWeek
            if (dow != java.time.DayOfWeek.FRIDAY && dow != java.time.DayOfWeek.SATURDAY) {
                dates.add(cursor)
            }
        }

        val rows = mutableListOf<AttendanceEntity>()
        dates.sorted().forEach { date ->
            students.forEachIndexed { idx, student ->
                // Deterministic realistic distribution: mostly present, a few
                // late / excused / unexcused cases spread across students+days.
                val status = when ((idx + date.dayOfYear) % 17) {
                    0 -> "absent_unexcused"
                    5 -> "absent_excused"
                    9, 12 -> "late"
                    else -> "present"
                }
                rows.add(
                    AttendanceEntity(
                        id = "att-seed-${student.id}-${date}",
                        tenantId = tenantId,
                        studentId = student.id,
                        classId = student.classId!!,
                        date = date.toString(),
                        session = "morning",
                        status = status,
                        arrivalTime = if (status == "late") "08:${if (idx % 2 == 0) "12" else "26"}" else null,
                        note = null,
                        recordedBy = "per-teacher1",
                        recordedBy_name = "Aïcha Bouhenni",
                        recordedAt = now,
                    ),
                )
            }
        }
        if (rows.isNotEmpty()) db.attendanceDao().upsertAll(rows)
    }
}
