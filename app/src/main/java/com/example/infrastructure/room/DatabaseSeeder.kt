package com.example.infrastructure.room

import com.example.core.PaymentCategory
import com.example.core.PaymentStatus
import com.example.domain.model.Parent
import com.example.domain.model.Student
import javax.inject.Inject
import javax.inject.Singleton
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

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
    private val tenantId = com.example.infrastructure.supabase.SupabaseConfig.DEFAULT_TENANT_ID
    private val academicYear = "2026-2027"
    private val startYear = 2026

    /**
     * Seed the local Room database with REFERENCE / CATALOG data only.
     *
     * This is NOT demo data — it is the school's canonical pricing schedule
     * (from Prices.md, 2026-2027 school year, Boumerdes, Algeria), the
     * subject catalog, the class catalog, and the personnel directory.
     * These rows are needed by the local LedgerEngine + waterfall allocation
     * logic so financial calculations produce correct numbers regardless of
     * whether the Supabase backend is reachable.
     *
     * DEMO PARENTS / STUDENTS / PAYMENTS / LEDGER ENTRIES are intentionally
     * NOT seeded here. The app pulls all financial, parent, and student data
     * from the real Supabase backend via [com.example.infrastructure.sync.PullSyncRepository]
     * — there is no local demo data for those tables anymore.
     */
    suspend fun seedIfEmpty() {
        if (db.pricingConfigDao().getActive() != null) return
        seedPricing()
        seedSubjects()
        seedClasses()
        seedPersonnel()
        // NOTE: seedDemoFamilies() is intentionally removed.
        // Real parents / students / payments / installments / ledger entries
        // are pulled from Supabase via PullSyncRepository.pullAll().
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
        val subs = listOf(
            SubjectEntity("sub-math", tenantId, "MATH", "Mathématiques", "academic", 4, 5.0, false, true),
            SubjectEntity("sub-fr", tenantId, "FR", "Français", "academic", 4, 5.0, false, true),
            SubjectEntity("sub-ar", tenantId, "AR", "Arabe", "academic", 3, 4.0, false, true),
            SubjectEntity("sub-en", tenantId, "EN", "Anglais", "academic", 2, 3.0, false, true),
            SubjectEntity("sub-isl", tenantId, "ISL", "Éducation Islamique", "academic", 2, 2.0, false, true),
            SubjectEntity("sub-histgeo", tenantId, "HG", "Histoire-Géographie", "academic", 2, 3.0, false, true),
            SubjectEntity("sub-sci", tenantId, "SCI", "Sciences", "academic", 3, 4.0, false, true),
            SubjectEntity("sub-sport", tenantId, "SPORT", "Éducation Physique", "academic", 1, 2.0, false, true),
            SubjectEntity("sub-art", tenantId, "ART", "Arts Plastiques", "academic", 1, 1.5, false, true),
            SubjectEntity("sub-chess", tenantId, "CHESS", "Club Échecs", "extracurricular", 1, 1.0, true, true),
            SubjectEntity("sub-english-club", tenantId, "ENGClub", "Club Anglais", "extracurricular", 1, 1.0, true, true),
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
        val s1 = StudentEntity("stu-001", tenantId, "ELV-2026-000001", p1.id, "Yacine", "Benali", null, "M", "2016-03-15", sept, "primaire", "4ap", "cls-4ap", null, null, "active", now, now)
        val s2 = StudentEntity("stu-002", tenantId, "ELV-2026-000002", p1.id, "Sara", "Benali", null, "F", "2018-07-22", sept, "primaire", "1ap", "cls-1ap", null, null, "active", now, now)

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
        val s3 = StudentEntity("stu-003", tenantId, "ELV-2026-000003", p2.id, "Amine", "Khelifi", null, "M", "2013-11-05", sept, "cem", "1am", "cls-1am", null, null, "active", now, now)

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
        val s4 = StudentEntity("stu-004", tenantId, "ELV-2026-000004", p3.id, "Lina", "Saidi", null, "F", "2017-02-10", sept, "primaire", "2ap", "cls-2ap", null, null, "active", now, now)
        val s5 = StudentEntity("stu-005", tenantId, "ELV-2026-000005", p3.id, "Omar", "Saidi", null, "M", "2015-09-18", sept, "primaire", "4ap", "cls-4ap", null, null, "active", now, now)
        val s6 = StudentEntity("stu-006", tenantId, "ELV-2026-000006", p3.id, "Rania", "Saidi", null, "F", "2012-06-30", sept, "cem", "1am", "cls-1am", null, null, "active", now, now)

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

        students.forEachIndexed { index, (student, gltId) ->
            val tuition = db.pricingConfigDao().getTuitionByGrade(gltId) ?: return@forEachIndexed
            // Sibling discount: child #2+ gets −5,000 DA per additional child
            val siblingDiscount = if (index > 0) -500_000L else 0L
            val netTuition = tuition.annualAmount + siblingDiscount
            val accountId = "parent:${parent.id}:category:tuition:student:${student.id}"

            // Charge entry (positive = parent owes)
            entries.add(
                LedgerEntryEntity(
                    id = "led-${parent.id}-${student.id}-tuition",
                    tenantId = tenantId, accountId = accountId,
                    parentId = parent.id, studentId = student.id,
                    category = "tuition", amount = netTuition,
                    type = "charge", sourceType = "installment",
                    sourceId = "reg-${student.id}",
                    method = null, receiptNumber = null, paymentStatus = null,
                    reversesId = null,
                    description = "Scolarité ${student.gradeLevel.uppercase()} ${academicYear}",
                    actorId = actorId, actorName = actorName,
                    at = Instant.now().toString(),
                )
            )

            // Sibling discount adjustment (if applicable)
            if (siblingDiscount != 0L) {
                entries.add(
                    LedgerEntryEntity(
                        id = "led-${parent.id}-${student.id}-sibling",
                        tenantId = tenantId, accountId = accountId,
                        parentId = parent.id, studentId = student.id,
                        category = "tuition", amount = siblingDiscount,
                        type = "adjustment", sourceType = "adjustment",
                        sourceId = "adj-${student.id}-sibling",
                        method = null, receiptNumber = null, paymentStatus = null,
                        reversesId = null,
                        description = "Remise fratrie (−5 000 DA, enfant #${index + 1})",
                        actorId = actorId, actorName = actorName,
                        at = Instant.now().toString(),
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
                        at = Instant.now().toString(),
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

        students.forEach { (student, gltId) ->
            val tuition = db.pricingConfigDao().getTuitionByGrade(gltId) ?: return@forEach

            // Tuition installments — 3 tranches (40/30/30)
            installments.add(inst("ins-${student.id}-t1", parent.id, student.id, "tuition", "Tranche 1 (Sept–Déc)", tuition.tranche1, due1))
            installments.add(inst("ins-${student.id}-t2", parent.id, student.id, "tuition", "Tranche 2 (Jan–Mar)", tuition.tranche2, due2))
            installments.add(inst("ins-${student.id}-t3", parent.id, student.id, "tuition", "Tranche 3 (Avr–Juin)", tuition.tranche3, due3))

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

        // Ledger payment entry (negative = credit)
        db.ledgerEntryDao().upsert(
            LedgerEntryEntity(
                id = "led-pay-001", tenantId = tenantId,
                accountId = "parent:${parent.id}:category:tuition",
                parentId = parent.id, studentId = null,
                category = "tuition", amount = -amount,
                type = "payment", sourceType = "payment",
                sourceId = paymentId, method = "cash",
                receiptNumber = receipt, paymentStatus = "paid",
                reversesId = null,
                description = "Encaissement comptoir $receipt",
                actorId = "per-admin", actorName = "Yacine Benali",
                at = now,
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

        // If there's unallocated overpayment, append a parent_credit adjustment
        if (allocation.unallocatedAmount > 0L) {
            db.ledgerEntryDao().upsert(
                LedgerEntryEntity(
                    id = "led-credit-${parent.id}", tenantId = tenantId,
                    accountId = "parent:${parent.id}:category:tuition",
                    parentId = parent.id, studentId = null,
                    category = "tuition", amount = -allocation.unallocatedAmount,
                    type = "adjustment", sourceType = "adjustment",
                    sourceId = paymentId, method = null,
                    receiptNumber = receipt, paymentStatus = null,
                    reversesId = null,
                    description = "Crédit parent (trop-perçu) $receipt",
                    actorId = "per-admin", actorName = "Yacine Benali",
                    at = now,
                )
            )
        }
    }
}
