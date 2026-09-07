package com.example.infrastructure.local

import com.example.BuildConfig
import com.example.core.AuditActions
import com.example.core.Errors
import com.example.core.PaymentCategory
import com.example.core.PaymentMethod
import com.example.core.PaymentStatus
import com.example.core.Permission
import com.example.core.Result
import com.example.core.Role
import com.example.core.Session
import com.example.core.allocatePaymentToInstallments
import com.example.core.createChargeEntry
import com.example.core.createPaymentEntry
import com.example.core.createReversalEntry
import com.example.core.deriveAccountId
import com.example.core.generateEntryId
import com.example.core.LedgerEngine
import com.example.core.WaterfallInstallment
import com.example.domain.model.Parent
import com.example.domain.model.Student
import com.example.domain.model.Payment
import com.example.domain.model.Installment
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import com.example.domain.repository.BatchRegisterResult
import com.example.domain.repository.CreateParentInput
import com.example.domain.repository.CreateStudentInput
import com.example.domain.repository.CollectPaymentInput
import com.example.domain.repository.InstallmentRepository
import com.example.domain.repository.LedgerRepository
import com.example.domain.repository.ParentRepository
import com.example.domain.repository.PaymentRepository
import com.example.domain.repository.StudentRepository
import com.example.domain.repository.UpdateParentInput
import com.example.domain.repository.UpdateStudentInput
import com.example.infrastructure.room.AuditLogDao
import com.example.infrastructure.room.AuditLogEntity
import com.example.infrastructure.room.ElImtiyazDatabase
import com.example.infrastructure.room.InstallmentDao
import com.example.infrastructure.room.InstallmentEntity
import com.example.infrastructure.room.LedgerEntryDao
import com.example.infrastructure.room.LedgerEntryEntity
import com.example.infrastructure.room.LocalMappers
import com.example.infrastructure.room.ParentDao
import com.example.infrastructure.room.ParentEntity
import com.example.infrastructure.room.PaymentDao
import com.example.infrastructure.room.PaymentEntity
import com.example.infrastructure.room.StudentDao
import com.example.infrastructure.room.StudentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ─── Student Repository ─────────────────────────────────────────────────────

@Singleton
class LocalStudentRepository @Inject constructor(
    private val auditContext: AuditContext,
    private val db: ElImtiyazDatabase,
    private val studentDao: StudentDao,
    private val parentDao: ParentDao,
    private val auditDao: AuditLogDao,
    // CANONICAL-FINANCIAL-LOGIC.md §8.1 — wire SyncSupport so Android
    // batch-registration writes (parent + students + ledger entries +
    // installments) propagate to Supabase. Without this, the desktop
    // never sees families registered on Android.
    private val syncSupport: com.example.infrastructure.sync.SyncSupport? = null,
) : StudentRepository {

    /** JSON builder helper for sync payloads (mirrors LocalPaymentRepository). */
    private fun syncJson(builder: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): String =
        kotlinx.serialization.json.buildJsonObject(builder).toString()

    override fun observe(): Flow<List<Student>> =
        studentDao.observeAll().map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeByParent(parentId: String): Flow<List<Student>> =
        studentDao.observeByParent(parentId).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeByClass(classId: String): Flow<List<Student>> =
        studentDao.observeByClass(classId).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeById(id: String): Flow<Student?> =
        studentDao.observeById(id).map { it?.let { e -> LocalMappers.run { e.toDomain() } } }

    override fun search(query: String): Flow<List<Student>> =
        studentDao.search(query).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override suspend fun createStudent(input: CreateStudentInput, actorId: String, actorName: String): Result<Student> {
        val parentId = input.parentId ?: return Result.Err(Errors.validation("Parent ID is required"))
        val now = Instant.now().toString()
        val year = java.time.LocalDate.now().year
        val seq = (studentDao.countActive() + 1).toString().padStart(6, '0')
        val code = "ELV-$year-$seq"
        val entity = StudentEntity(
            id = "stu-${UUID.randomUUID()}",
            tenantId = auditContext.tenantId(), code = code, parentId = parentId,
            firstName = input.firstName, lastName = input.lastName,
            displayName = input.displayName ?: "${input.firstName} ${input.lastName}".trim().ifEmpty { null },
            gender = input.gender,
            birthDate = input.birthDate, enrollmentDate = now,
            level = input.level, gradeLevel = input.gradeLevel,
            classId = input.classId, photoUrl = null, medicalNotes = input.medicalNotes,
            status = "active", createdAt = now, updatedAt = now,
        )
        studentDao.upsert(entity)
        auditDao.upsert(auditContext.audit("student.create", "student", entity.id, actorId, actorName,
            after = """{"code":"$code","name":"${entity.fullName}"}"""))
        return Result.Ok(LocalMappers.run { entity.toDomain() })
    }

    override suspend fun updateStudent(id: String, input: UpdateStudentInput, actorId: String, actorName: String): Result<Student> {
        val existing = studentDao.getById(id) ?: return Result.Err(Errors.notFound("Student $id not found"))
        // TIER 4 FIX — validate enrollment status against the canonical value
        // set (mirrors the SQL CHECK after migration 0037). Previously any
        // arbitrary string was accepted locally and later crashed the server
        // CHECK on push.
        input.status?.let { status ->
            if (status !in CANONICAL_STUDENT_STATUSES) {
                return Result.Err(Errors.validation(
                    "Statut d'inscription invalide: '$status'. Valeurs autorisées: ${CANONICAL_STUDENT_STATUSES.joinToString(", ")}",
                ))
            }
        }
        val updated = existing.copy(
            firstName = input.firstName ?: existing.firstName,
            lastName = input.lastName ?: existing.lastName,
            // FIX (dropped field): `displayName` was accepted but never applied —
            // since imported students store their complete name ONLY in
            // displayName, those names could never be corrected.
            // Only recompute the derived name when first/last actually change,
            // so unrelated updates don't clobber an imported display name.
            displayName = when {
                input.displayName != null -> input.displayName
                input.firstName != null || input.lastName != null ->
                    listOfNotNull(
                        input.firstName ?: existing.firstName,
                        input.lastName ?: existing.lastName,
                    ).joinToString(" ").trim().ifEmpty { existing.displayName }
                else -> existing.displayName
            },
            birthDate = input.birthDate ?: existing.birthDate,
            level = input.level ?: existing.level,
            gradeLevel = input.gradeLevel ?: existing.gradeLevel,
            classId = input.classId ?: existing.classId,
            status = input.status ?: existing.status,
            medicalNotes = input.medicalNotes ?: existing.medicalNotes,
            updatedAt = Instant.now().toString(),
        )
        studentDao.update(updated)
        auditDao.upsert(auditContext.audit("student.update", "student", id, actorId, actorName))
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }

    override suspend fun batchRegister(parent: CreateParentInput, students: List<CreateStudentInput>, actorId: String, actorName: String): Result<BatchRegisterResult> {
        if (students.isEmpty()) return Result.Err(Errors.validation("At least one student is required"))
        val now = Instant.now().toString()
        val year = java.time.LocalDate.now().year

        val parentCode = com.example.core.deterministicParentCode(
            year = year,
            input = com.example.core.ParentCodeInput(
                phone = parent.phone,
                displayName = parent.displayName,
                firstName = parent.firstName,
                lastName = parent.lastName,
            ),
        )
        // TIER 2 R15 — deterministic activation_code derived from (parentCode, tenantId).
        val activationCode = com.example.core.deterministicActivationCode(
            parentCode = parentCode,
            tenantId = auditContext.tenantId(),
        )
        val parentEntity = ParentEntity(
            id = "par-${UUID.randomUUID()}",
            tenantId = auditContext.tenantId(), code = parentCode,
            firstName = parent.firstName, lastName = parent.lastName,
            displayName = parent.displayName ?: "${parent.firstName} ${parent.lastName}".trim().ifEmpty { null },
            phone = parent.phone,
            // Vault §04.03 — secondary phone / national ID / relationship from
            // the registration master-info block (Step 1).
            whatsapp = parent.secondaryPhone ?: parent.phone,
            email = parent.email, occupation = parent.occupation,
            address = parent.address, transportDestination = parent.transportDestination,
            nationalId = parent.nationalId,
            relationship = parent.relationship,
            preferredLanguage = parent.preferredLanguage, avatarUrl = null,
            isActive = true, isFinanciallyRestricted = false,
            activationCode = activationCode, createdAt = now, updatedAt = now,
        )
        parentDao.upsert(parentEntity)

        val (due1, due2, due3) = com.example.core.officialTuitionDueDates(year)
        val studentEntities = mutableListOf<StudentEntity>()
        val ledgerEntries = mutableListOf<LedgerEntryEntity>()
        val installments = mutableListOf<InstallmentEntity>()
        val pricingDao = db.pricingConfigDao()

        students.forEachIndexed { index, s ->
            val seq = (studentDao.countActive() + index + 1).toString().padStart(6, '0')
            val code = "ELV-$year-$seq"
            val studentEntity = StudentEntity(
                id = "stu-${UUID.randomUUID()}",
                tenantId = auditContext.tenantId(), code = code, parentId = parentEntity.id,
                firstName = s.firstName, lastName = s.lastName,
                displayName = s.displayName ?: "${s.firstName} ${s.lastName}".trim().ifEmpty { null },
                gender = s.gender,
                birthDate = s.birthDate, enrollmentDate = now,
                level = s.level, gradeLevel = s.gradeLevel,
                classId = s.classId, photoUrl = null, medicalNotes = s.medicalNotes,
                status = "active", createdAt = now, updatedAt = now,
            )
            studentEntities.add(studentEntity)

            val tuition = pricingDao.getTuitionByGrade(s.gradeLevel)
            if (tuition != null) {
                // CANONICAL-FINANCIAL-LOGIC.md §5 — apply ALL 5 discount rules
                // in a single pass on the GROSS annual tuition, then split the
                // net into 3 tranches (or 1 for full_annual). This mirrors the
                // desktop's `computeBilling` + `buildTuitionChargeEntries` so
                // both apps produce identical charge entries for the same
                // student.
                //
                // The previous implementation applied only the sibling
                // discount inline (missing passage_palier, full_annual,
                // highest_average, seniority_5y) — a structural divergence
                // that produced different charge entries for the same
                // student on the same day.
                val paymentPlan = com.example.core.PaymentPlan.fromCode(s.paymentPlan)
                val discountParams = com.example.core.EvaluateAllDiscountsParams(
                    grossTuition = tuition.annualAmount,
                    previousGradeLevel = s.previousGradeLevel,
                    currentGradeLevel = s.gradeLevel,
                    childIndex = index + 1,
                    paymentPlan = paymentPlan,
                    paymentDate = now,
                    academicYearStartYear = year,
                    academicYearStart = "${year}-09-15T00:00:00Z",
                    enrollmentDate = s.enrollmentDate ?: now,
                    previousRank = s.previousRank,
                )
                val evaluations = com.example.core.evaluateAllSystemDiscounts(discountParams)
                val totalDiscount = com.example.core.sumDiscounts(evaluations)
                // `totalDiscount` is NEGATIVE (reduces the gross). The charge
                // entry's `amount` is the NET tuition (gross + totalDiscount),
                // matching the desktop's `netTuition = max(0, gross + tuitionDiscount)`.
                val netTuition = (tuition.annualAmount + totalDiscount).coerceAtLeast(0L)
                val accountId = deriveAccountId(parentEntity.id, PaymentCategory.TUITION, studentEntity.id)

                // CANONICAL-FINANCIAL-LOGIC.md §6.1 — branches on `paymentPlan`:
                //   - `full_annual`: emit 1 charge entry with `metadata: { tranche: null, paymentPlan: "full_annual", ... }`
                //   - `tranches`: emit 3 charge entries with `metadata: { tranche: 1|2|3, paymentPlan: "tranches", ... }`
                val discountsMetadata = evaluations.map { ev ->
                    mapOf("code" to ev.code, "amount" to ev.amount, "reason" to ev.reason)
                }
                if (paymentPlan == com.example.core.PaymentPlan.FULL_ANNUAL) {
                    ledgerEntries.add(
                        LedgerEntryEntity(
                            id = generateEntryId(), tenantId = parentEntity.tenantId,
                            accountId = accountId, parentId = parentEntity.id, studentId = studentEntity.id,
                            category = PaymentCategory.TUITION.code, amount = netTuition,
                            type = "charge", sourceType = "installment", sourceId = "reg-${studentEntity.id}",
                            method = null, receiptNumber = null, paymentStatus = null, reversesId = null,
                            description = "Scolarité annuelle ${s.gradeLevel.uppercase()} $year (paiement intégral)",
                            actorId = actorId, actorName = actorName, at = now,
                            metadataJson = com.example.infrastructure.room.LocalMappers.serializeMetadataJson(
                                mapOf(
                                    "tranche" to null,
                                    "paymentPlan" to "full_annual",
                                    "academicCycle" to year.toString(),
                                    "gradeLevel" to s.gradeLevel,
                                    "discounts" to discountsMetadata,
                                    "netTuition" to netTuition,
                                    "grossTuition" to tuition.annualAmount,
                                    "totalDiscount" to totalDiscount,
                                ),
                            ),
                        )
                    )
                    installments.add(inst(auditContext.tenantId(), "ins-${studentEntity.id}-annual", parentEntity.id, studentEntity.id, "tuition", "Année complète", netTuition, due1, now))
                } else {
                    // Tranches — split the NET (post-discount) by 40/30/30.
                    val (t1, t2, t3) = com.example.core.splitNetTuitionByOfficialSchedule(netTuition)
                    data class TrancheSpec(val amount: Long, val number: Int, val label: String, val dueDate: String)
                    val trancheSpecs = listOf(
                        TrancheSpec(t1, 1, "Tranche 1 (Sept–Déc)", due1),
                        TrancheSpec(t2, 2, "Tranche 2 (Jan–Mar)", due2),
                        TrancheSpec(t3, 3, "Tranche 3 (Avr–Juin)", due3),
                    )
                    for (spec in trancheSpecs) {
                        val amt = spec.amount
                        val trancheNum = spec.number
                        val label = spec.label
                        val dueDate = spec.dueDate
                        ledgerEntries.add(
                            LedgerEntryEntity(
                                id = generateEntryId(), tenantId = parentEntity.tenantId,
                                accountId = accountId, parentId = parentEntity.id, studentId = studentEntity.id,
                                category = PaymentCategory.TUITION.code, amount = amt,
                                type = "charge", sourceType = "installment", sourceId = "reg-${studentEntity.id}-t$trancheNum",
                                method = null, receiptNumber = null, paymentStatus = null, reversesId = null,
                                description = "$label — Scolarité ${s.gradeLevel.uppercase()} $year",
                                actorId = actorId, actorName = actorName, at = now,
                                metadataJson = com.example.infrastructure.room.LocalMappers.serializeMetadataJson(
                                    mapOf(
                                        "tranche" to trancheNum,
                                        "paymentPlan" to "tranches",
                                        "academicCycle" to year.toString(),
                                        "gradeLevel" to s.gradeLevel,
                                        "discounts" to discountsMetadata,
                                        "netTuition" to netTuition,
                                        "grossTuition" to tuition.annualAmount,
                                        "totalDiscount" to totalDiscount,
                                    ),
                                ),
                            )
                        )
                        installments.add(inst(auditContext.tenantId(), "ins-${studentEntity.id}-t$trancheNum", parentEntity.id, studentEntity.id, "tuition", label, amt, dueDate, now))
                    }
                }
            }

            val transport = parent.transportDestination?.let { pricingDao.getTransportByDestination(it) }
            if (transport != null) {
                val accountId = deriveAccountId(parentEntity.id, PaymentCategory.TRANSPORT, studentEntity.id)
                ledgerEntries.add(
                    LedgerEntryEntity(
                        id = generateEntryId(), tenantId = parentEntity.tenantId,
                        accountId = accountId, parentId = parentEntity.id, studentId = studentEntity.id,
                        category = PaymentCategory.TRANSPORT.code, amount = transport.annualAmount,
                        type = "charge", sourceType = "installment", sourceId = "reg-${studentEntity.id}-transport",
                        method = null, receiptNumber = null, paymentStatus = null, reversesId = null,
                        description = "Transport ${parent.transportDestination}",
                        actorId = actorId, actorName = actorName, at = now,
                        metadataJson = "{}",
                    )
                )
                installments.add(inst(auditContext.tenantId(), "ins-${studentEntity.id}-tr1", parentEntity.id, studentEntity.id, "transport", "Transport T1", transport.tranche1, due1, now))
                installments.add(inst(auditContext.tenantId(), "ins-${studentEntity.id}-tr2", parentEntity.id, studentEntity.id, "transport", "Transport T2", transport.tranche2, due2, now))
                installments.add(inst(auditContext.tenantId(), "ins-${studentEntity.id}-tr3", parentEntity.id, studentEntity.id, "transport", "Transport T3", transport.tranche3, due3, now))
            }
        }

        studentDao.upsertAll(studentEntities)
        db.ledgerEntryDao().upsertAll(ledgerEntries)
        db.installmentDao().upsertAll(installments)

        // CANONICAL-FINANCIAL-LOGIC.md §8.1 — enqueue the parent + each
        // student + each ledger entry + each installment for sync push so
        // the desktop sees newly-registered families from Android. Without
        // this wiring, batch-registered families live in Android Room only.
        syncSupport?.run {
            // Parent
            enqueueOnly(
                entity = "parent",
                operation = "create",
                payload = syncJson {
                    put("id", parentEntity.id); put("tenantId", parentEntity.tenantId)
                    put("code", parentEntity.code); put("firstName", parentEntity.firstName)
                    put("lastName", parentEntity.lastName); put("displayName", parentEntity.displayName ?: "")
                    put("phone", parentEntity.phone); put("whatsapp", parentEntity.whatsapp ?: "")
                    put("email", parentEntity.email ?: ""); put("occupation", parentEntity.occupation ?: "")
                    put("address", parentEntity.address ?: "")
                    put("preferredLanguage", parentEntity.preferredLanguage)
                    put("transportDestination", parentEntity.transportDestination ?: "")
                    // Vault §04.03 — master-info fields ride the sync payload;
                    // the dispatcher's RPC signature is unchanged (it maps the
                    // params it knows, extra keys are ignored server-side).
                    parentEntity.nationalId?.let { put("nationalId", it) }
                    parentEntity.relationship?.let { put("relationship", it) }
                    put("isActive", parentEntity.isActive)
                    put("activationCode", parentEntity.activationCode)
                    put("createdAt", parentEntity.createdAt)
                },
                isMock = false, sourceScreen = "BatchRegistrationScreen",
            )
            // Students
            for (s in studentEntities) {
                enqueueOnly(
                    entity = "student",
                    operation = "create",
                    payload = syncJson {
                        put("id", s.id); put("tenantId", s.tenantId); put("code", s.code)
                        put("parentId", s.parentId); put("parentCode", parentEntity.code)
                            put("firstName", s.firstName)
                        put("lastName", s.lastName); put("displayName", s.displayName ?: "")
                        put("gender", s.gender); put("birthDate", s.birthDate ?: "")
                        put("enrollmentDate", s.enrollmentDate); put("level", s.level)
                        put("gradeLevel", s.gradeLevel); put("classId", s.classId ?: "")
                        put("medicalNotes", s.medicalNotes ?: "")
                        put("status", s.status); put("createdAt", s.createdAt)
                    },
                    isMock = false, sourceScreen = "BatchRegistrationScreen",
                )
            }
            // Ledger entries (tuition charges, transport charges, adjustments)
            for (e in ledgerEntries) {
                enqueueOnly(
                    entity = "ledger_entry",
                    operation = "create",
                    payload = syncJson {
                        put("id", e.id); put("tenantId", e.tenantId); put("accountId", e.accountId)
                        put("parentId", e.parentId); put("parentCode", parentEntity.code)
                        put("studentId", e.studentId ?: "")
                            put("category", e.category); put("amount", e.amount)
                        put("type", e.type); put("sourceType", e.sourceType)
                        put("sourceId", e.sourceId); put("method", e.method ?: "")
                        put("receiptNumber", e.receiptNumber ?: "")
                        put("paymentStatus", e.paymentStatus ?: "")
                        put("reversesId", e.reversesId ?: "")
                        put("description", e.description)
                        put("actorId", e.actorId); put("actorName", e.actorName)
                        put("at", e.at); put("metadataJson", e.metadataJson)
                    },
                    isMock = false, sourceScreen = "BatchRegistrationScreen",
                )
            }
            // Installments — pushed by the SyncQueueDispatcher's TIER 4
            // case (migration 0037: idempotent `upsert_installment_from_import`).
            // (Comment corrected 2026-09-02, T-128: the old note claimed the
            // dispatcher had no installment case — it does, and the refund
            // flow now relies on it too. See CROSS-103.)
            for (ins in installments) {
                enqueueOnly(
                    entity = "installment",
                    operation = "create",
                    payload = syncJson {
                        put("id", ins.id); put("tenantId", ins.tenantId)
                        put("parentId", ins.parentId); put("parentCode", parentEntity.code)
                            put("studentId", ins.studentId)
                        put("category", ins.category); put("label", ins.label)
                        put("amountDue", ins.amountDue); put("amountPaid", ins.amountPaid)
                        put("amountPending", ins.amountPending); put("dueDate", ins.dueDate)
                        put("status", ins.status)
                    },
                    isMock = false, sourceScreen = "BatchRegistrationScreen",
                )
            }
        }

        auditDao.upsert(auditContext.audit("crm.batch_register", "parent", parentEntity.id, actorId, actorName,
            after = """{"student_count":${students.size},"activation_code":"$activationCode"}"""))

        return Result.Ok(BatchRegisterResult(
            parent = LocalMappers.run { parentEntity.toDomain() },
            students = studentEntities.map { LocalMappers.run { it.toDomain() } },
            activationCode = activationCode,
        ))
    }

    override suspend fun promoteStudents(academicYear: String, decisions: List<com.example.domain.repository.PromotionDecision>, actorId: String, actorName: String): Result<Unit> {
        // TIER 4 FIX — the previous implementation was a stub: it bumped
        // `updatedAt` and wrote an audit row WITHOUT changing gradeLevel or
        // status, so mobile promotions silently diverged from the desktop's
        // canonical state transitions. It now applies the canonical Algerian
        // progression ladder (core/AcademicProgression.kt, ported 1:1 from the
        // desktop's getNextGradeProgression).
        val now = Instant.now().toString()
        decisions.forEach { d ->
            val existing = studentDao.getById(d.studentId) ?: return@forEach
            val progression = com.example.core.getNextGradeProgression(existing.gradeLevel)
            val updated = when (d.decision) {
                com.example.core.PromotionDecisions.PROMOTED -> {
                    val next = progression.nextGradeCode
                        ?: return@forEach // unknown ladder position — keep state
                    existing.copy(
                        gradeLevel = next,
                        level = progression.nextLevel ?: existing.level,
                        status = "active",
                        updatedAt = now,
                    )
                }
                com.example.core.PromotionDecisions.GRADUATED ->
                    existing.copy(status = "graduated", updatedAt = now)
                else -> existing.copy(status = "active", updatedAt = now) // repeated
            }
            studentDao.update(updated)
            auditDao.upsert(auditContext.audit("student.promote", "student", d.studentId, actorId, actorName,
                after = """{"decision":"${d.decision}","year":"$academicYear","from":"${existing.gradeLevel}","to":"${updated.gradeLevel}","status":"${updated.status}"}"""))
            // Propagate the promotion to Supabase so the desktop sees it.
            syncSupport?.enqueueOnly(
                entity = "student",
                operation = "promote",
                payload = syncJson {
                    put("id", updated.id); put("tenantId", updated.tenantId)
                    put("code", updated.code); put("parentId", updated.parentId)
                    put("firstName", updated.firstName); put("lastName", updated.lastName)
                    put("displayName", updated.displayName ?: "")
                    put("gradeLevel", updated.gradeLevel); put("level", updated.level)
                    put("status", updated.status)
                },
                isMock = false, sourceScreen = "PromotionScreen",
            )
        }
        return Result.Ok(Unit)
    }
}
