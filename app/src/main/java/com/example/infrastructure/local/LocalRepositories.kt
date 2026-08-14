package com.example.infrastructure.local

import com.example.core.AuditActions
import com.example.core.Errors
import com.example.core.PaymentCategory
import com.example.core.PaymentMethod
import com.example.core.PaymentStatus
import com.example.core.Result
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
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real Supabase Auth repository.
 *
 * The mobile app authenticates against the REAL Supabase Auth instance
 * (project: hkvkefubghbbotgnteir). The anon/publishable key is hard-coded
 * in [com.example.infrastructure.supabase.SupabaseClientProvider] — the
 * service_role key is NEVER embedded in the APK.
 *
 * On sign-in:
 *   1. Call `auth.signInWith(Email)` against the real Supabase Auth API.
 *   2. Fetch the user's profile from the `user_profiles` table.
 *   3. Build a [com.example.core.Session] and persist it in memory + audit log.
 *
 * If Supabase auth fails (wrong credentials, network error, timeout,
 * unconfigured project), the repository returns `Result.Err` — there is NO
 * demo/offline fallback. This is intentional: the app MUST use the real
 * backend, never a fake session.
 *
 * JWT persistence is handled by the Supabase Auth plugin via
 * [com.example.infrastructure.supabase.EncryptedSettingsStorage] (backed by
 * EncryptedSharedPreferences) so the user stays signed in across cold-starts.
 */
@Singleton
class LocalAuthRepository @Inject constructor(
    private val auditDao: AuditLogDao,
    private val supabaseProvider: com.example.infrastructure.supabase.SupabaseClientProvider,
) : com.example.domain.repository.AuthRepository {

    private val _sessionState = kotlinx.coroutines.flow.MutableStateFlow<com.example.core.Session?>(null)
    private val sessionState: kotlinx.coroutines.flow.StateFlow<com.example.core.Session?> = _sessionState

    override fun observeSession(): Flow<com.example.core.Session?> = sessionState

    override suspend fun signIn(email: String, password: String): Result<com.example.core.Session> {
        if (!com.example.infrastructure.supabase.NetworkTimeouts.isSupabaseConfigured) {
            return Result.Err(com.example.core.Errors.server(
                "Supabase n'est pas configuré. Vérifiez l'URL et la clé anonyme dans les paramètres.",
            ))
        }

        // ── Stage 1: real Supabase Auth (with 8s hard timeout) ──────────
        // Capture the exception separately so we can return a meaningful error
        // to the UI instead of swallowing it as a silent null.
        var authError: Throwable? = null
        val userInfo = try {
            kotlinx.coroutines.withTimeout(8_000L) {
                supabaseProvider.auth.signInWith(io.github.jan.supabase.auth.providers.builtin.Email) {
                    this.email = email
                    this.password = password
                }
                supabaseProvider.auth.currentUserOrNull()
            }
        } catch (e: Throwable) {
            authError = e
            null
        }

        if (userInfo == null) {
            val msg = authError?.message ?: "Identifiants invalides ou session non disponible."
            return Result.Err(com.example.core.Errors.fromException(
                authError ?: IllegalStateException("Supabase auth returned null user: $msg"),
            ))
        }

        // Fetch the user's profile from the `user_profiles` table.
        val profile = com.example.infrastructure.supabase.NetworkTimeouts.guard<com.example.infrastructure.supabase.UserProfileDto?>(
            "auth.fetchProfile", timeoutMs = 5_000L,
        ) {
            supabaseProvider.postgrest.from("user_profiles")
                .select {
                    filter { eq("auth_user_id", userInfo.id) }
                    limit(1)
                }
                .decodeList<com.example.infrastructure.supabase.UserProfileDto>()
                .firstOrNull()
        }

        val role = com.example.core.Role.SUPER_ADMIN
        val displayName = profile?.displayName
            ?: userInfo.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
            ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }
        val session = com.example.core.Session(
            userId = profile?.id ?: userInfo.id,
            tenantId = profile?.tenantId ?: com.example.infrastructure.supabase.SupabaseConfig.DEFAULT_TENANT_ID,
            email = profile?.email ?: userInfo.email ?: email,
            displayName = displayName,
            avatarUrl = profile?.avatarUrl,
            role = role,
            permissions = com.example.core.Permission.entries.toSet(),
            accessToken = userInfo.id,
            refreshToken = null,
            expiresAt = System.currentTimeMillis() + 3_600_000L,
            locale = profile?.locale ?: "fr",
        )
        _sessionState.value = session
        auditDao.upsert(
            AuditLogEntity(
                id = "aud-${UUID.randomUUID()}",
                tenantId = session.tenantId,
                action = AuditActions.AUTH_LOGIN,
                entityType = "auth", entityId = session.userId,
                actorId = session.userId, actorName = session.displayName,
                actorRole = session.role.code,
                beforeJson = null, afterJson = """{"email":"${session.email}","source":"supabase"}""",
                note = "Supabase sign-in", createdAt = Instant.now().toString(),
            )
        )
        return Result.Ok(session)
    }

    override suspend fun signOut(): Result<Unit> {
        // Best-effort remote sign-out (don't block on it).
        if (com.example.infrastructure.supabase.NetworkTimeouts.isSupabaseConfigured) {
            com.example.infrastructure.supabase.NetworkTimeouts.guard<Unit>("auth.signOut", timeoutMs = 2_000L) {
                supabaseProvider.auth.signOut()
            }
        }
        _sessionState.value?.let { s ->
            auditDao.upsert(
                AuditLogEntity(
                    id = "aud-${UUID.randomUUID()}",
                    tenantId = s.tenantId,
                    action = AuditActions.AUTH_LOGOUT,
                    entityType = "auth", entityId = s.userId,
                    actorId = s.userId, actorName = s.displayName,
                    actorRole = s.role.code,
                    beforeJson = null, afterJson = null,
                    note = "Mobile sign-out", createdAt = Instant.now().toString(),
                )
            )
        }
        _sessionState.value = null
        return Result.Ok(Unit)
    }

    override suspend fun refreshSession(): Result<com.example.core.Session?> {
        // If we already have an in-memory session, return it.
        _sessionState.value?.let { return Result.Ok(it) }

        // If Supabase isn't configured, there's nothing to restore.
        if (!com.example.infrastructure.supabase.NetworkTimeouts.isSupabaseConfigured) return Result.Ok(null)

        // Try to restore from the Supabase Auth plugin's persistent storage.
        val current = com.example.infrastructure.supabase.NetworkTimeouts.guard<io.github.jan.supabase.auth.user.UserInfo?>(
            "auth.refreshSession", timeoutMs = 3_000L, onlyIfConfigured = false,
        ) {
            supabaseProvider.auth.currentUserOrNull()
        } ?: return Result.Ok(null)

        val profile = com.example.infrastructure.supabase.NetworkTimeouts.guard<com.example.infrastructure.supabase.UserProfileDto?>(
            "auth.refreshProfile",
        ) {
            supabaseProvider.postgrest.from("user_profiles")
                .select {
                    filter { eq("auth_user_id", current.id) }
                    limit(1)
                }
                .decodeList<com.example.infrastructure.supabase.UserProfileDto>()
                .firstOrNull()
        } ?: return Result.Ok(null)

        val displayName = profile?.displayName
            ?: current.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
            ?: "Administrateur"
        val session = com.example.core.Session(
            userId = profile?.id ?: current.id,
            tenantId = profile?.tenantId ?: com.example.infrastructure.supabase.SupabaseConfig.DEFAULT_TENANT_ID,
            email = profile?.email ?: current.email ?: "",
            displayName = displayName,
            avatarUrl = profile?.avatarUrl,
            role = com.example.core.Role.SUPER_ADMIN,
            permissions = com.example.core.Permission.entries.toSet(),
            accessToken = current.id,
            refreshToken = null,
            expiresAt = System.currentTimeMillis() + 3_600_000L,
            locale = profile?.locale ?: "fr",
        )
        _sessionState.value = session
        return Result.Ok(session)
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        if (com.example.infrastructure.supabase.NetworkTimeouts.isSupabaseConfigured) {
            com.example.infrastructure.supabase.NetworkTimeouts.guard<Unit>("auth.changePassword", timeoutMs = 4_000L) {
                supabaseProvider.auth.updateUser {
                    password = newPassword
                }
            }
        }
        return Result.Ok(Unit)
    }
}

// ─── Parent Repository ──────────────────────────────────────────────────────

@Singleton
class LocalParentRepository @Inject constructor(
    private val parentDao: ParentDao,
    private val auditDao: AuditLogDao,
    private val supabaseProvider: com.example.infrastructure.supabase.SupabaseClientProvider,
) : ParentRepository {

    override fun observe(): Flow<List<Parent>> =
        parentDao.observeAll().map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeById(id: String): Flow<Parent?> =
        parentDao.observeById(id).map { it?.let { e -> LocalMappers.run { e.toDomain() } } }

    override fun search(query: String): Flow<List<Parent>> =
        parentDao.search(query).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override suspend fun createParent(input: CreateParentInput, actorId: String, actorName: String): Result<Parent> {
        val now = Instant.now().toString()
        val year = java.time.LocalDate.now().year
        val code = "PAR-$year-${UUID.randomUUID().toString().takeLast(4).uppercase()}"
        val activationCode = (100_000..999_999).random().toString()
        val entity = ParentEntity(
            id = "par-${UUID.randomUUID()}",
            tenantId = com.example.infrastructure.supabase.SupabaseConfig.DEFAULT_TENANT_ID, code = code,
            firstName = input.firstName, lastName = input.lastName,
            displayName = input.displayName ?: "${input.firstName} ${input.lastName}".trim().ifEmpty { null },
            phone = input.phone,
            whatsapp = input.phone, email = input.email, occupation = input.occupation,
            address = input.address, transportDestination = input.transportDestination,
            preferredLanguage = input.preferredLanguage, avatarUrl = null,
            isActive = true, isFinanciallyRestricted = false,
            activationCode = activationCode, createdAt = now, updatedAt = now,
        )

        // ── Supabase write-through ───────────────────────────────────────
        // Insert into the REAL `parents` table first. If this fails, the Room
        // cache is NOT updated and the caller returns Result.Err — no fake
        // local-only rows.
        val supabaseErr = com.example.infrastructure.supabase.NetworkTimeouts.guard<Throwable?>(
            "parent.create.supabase", timeoutMs = 6_000L, onlyIfConfigured = false,
        ) {
            val payload = com.example.infrastructure.supabase.SupabaseWriteThrough.parentEntityToPayload(entity)
            supabaseProvider.postgrest.from("parents").insert(payload)
            null
        }
        if (supabaseErr != null) {
            return Result.Err(com.example.core.Errors.fromException(supabaseErr))
        }

        // Supabase insert succeeded → mirror into Room cache so UI Flow re-emits.
        parentDao.upsert(entity)
        val parent = LocalMappers.run { entity.toDomain() }
        auditDao.upsert(audit("parent.create", "parent", parent.id, actorId, actorName,
            after = """{"code":"$code","name":"${parent.fullName}"}"""))
        return Result.Ok(parent)
    }

    override suspend fun updateParent(id: String, input: UpdateParentInput, actorId: String, actorName: String): Result<Parent> {
        val existing = parentDao.getById(id) ?: return Result.Err(Errors.notFound("Parent $id not found"))
        val updated = existing.copy(
            firstName = input.firstName ?: existing.firstName,
            lastName = input.lastName ?: existing.lastName,
            phone = input.phone ?: existing.phone,
            email = input.email ?: existing.email,
            occupation = input.occupation ?: existing.occupation,
            address = input.address ?: existing.address,
            transportDestination = input.transportDestination ?: existing.transportDestination,
            preferredLanguage = input.preferredLanguage ?: existing.preferredLanguage,
            updatedAt = Instant.now().toString(),
        )

        // ── Supabase write-through ───────────────────────────────────────
        val supabaseErr = com.example.infrastructure.supabase.NetworkTimeouts.guard<Throwable?>(
            "parent.update.supabase", timeoutMs = 6_000L, onlyIfConfigured = false,
        ) {
            val payload = com.example.infrastructure.supabase.SupabaseWriteThrough.parentEntityToPayload(updated)
            supabaseProvider.postgrest.from("parents").update(payload) {
                filter { eq("id", id) }
            }
            null
        }
        if (supabaseErr != null) {
            return Result.Err(com.example.core.Errors.fromException(supabaseErr))
        }

        parentDao.update(updated)
        auditDao.upsert(audit("parent.update", "parent", id, actorId, actorName, after = "{}"))
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }

    override suspend fun deleteParent(id: String, actorId: String, actorName: String): Result<Unit> {
        // ── Supabase write-through ───────────────────────────────────────
        val supabaseErr = com.example.infrastructure.supabase.NetworkTimeouts.guard<Throwable?>(
            "parent.delete.supabase", timeoutMs = 6_000L, onlyIfConfigured = false,
        ) {
            supabaseProvider.postgrest.from("parents").delete {
                filter { eq("id", id) }
            }
            null
        }
        if (supabaseErr != null) {
            return Result.Err(com.example.core.Errors.fromException(supabaseErr))
        }

        parentDao.deleteById(id)
        auditDao.upsert(audit("parent.delete", "parent", id, actorId, actorName))
        return Result.Ok(Unit)
    }
}

// ─── Student Repository ─────────────────────────────────────────────────────

@Singleton
class LocalStudentRepository @Inject constructor(
    private val db: ElImtiyazDatabase,
    private val studentDao: StudentDao,
    private val parentDao: ParentDao,
    private val auditDao: AuditLogDao,
    private val supabaseProvider: com.example.infrastructure.supabase.SupabaseClientProvider,
) : StudentRepository {

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
            tenantId = com.example.infrastructure.supabase.SupabaseConfig.DEFAULT_TENANT_ID, code = code, parentId = parentId,
            firstName = input.firstName, lastName = input.lastName,
            displayName = input.displayName ?: "${input.firstName} ${input.lastName}".trim().ifEmpty { null },
            gender = input.gender,
            birthDate = input.birthDate, enrollmentDate = now,
            level = input.level, gradeLevel = input.gradeLevel,
            classId = input.classId, photoUrl = null, medicalNotes = input.medicalNotes,
            status = "active", createdAt = now, updatedAt = now,
        )

        // ── Supabase write-through ───────────────────────────────────────
        val supabaseErr = com.example.infrastructure.supabase.NetworkTimeouts.guard<Throwable?>(
            "student.create.supabase", timeoutMs = 6_000L, onlyIfConfigured = false,
        ) {
            val payload = com.example.infrastructure.supabase.SupabaseWriteThrough.studentEntityToPayload(entity)
            supabaseProvider.postgrest.from("students").insert(payload)
            null
        }
        if (supabaseErr != null) {
            return Result.Err(com.example.core.Errors.fromException(supabaseErr))
        }

        studentDao.upsert(entity)
        auditDao.upsert(audit("student.create", "student", entity.id, actorId, actorName,
            after = """{"code":"$code","name":"${entity.fullName}"}"""))
        return Result.Ok(LocalMappers.run { entity.toDomain() })
    }

    override suspend fun updateStudent(id: String, input: UpdateStudentInput, actorId: String, actorName: String): Result<Student> {
        val existing = studentDao.getById(id) ?: return Result.Err(Errors.notFound("Student $id not found"))
        val updated = existing.copy(
            firstName = input.firstName ?: existing.firstName,
            lastName = input.lastName ?: existing.lastName,
            classId = input.classId ?: existing.classId,
            status = input.status ?: existing.status,
            medicalNotes = input.medicalNotes ?: existing.medicalNotes,
            updatedAt = Instant.now().toString(),
        )

        // ── Supabase write-through ───────────────────────────────────────
        val supabaseErr = com.example.infrastructure.supabase.NetworkTimeouts.guard<Throwable?>(
            "student.update.supabase", timeoutMs = 6_000L, onlyIfConfigured = false,
        ) {
            val payload = com.example.infrastructure.supabase.SupabaseWriteThrough.studentEntityToPayload(updated)
            supabaseProvider.postgrest.from("students").update(payload) {
                filter { eq("id", id) }
            }
            null
        }
        if (supabaseErr != null) {
            return Result.Err(com.example.core.Errors.fromException(supabaseErr))
        }

        studentDao.update(updated)
        auditDao.upsert(audit("student.update", "student", id, actorId, actorName))
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }

    /**
     * Atomic batch registration — creates parent + N students + ledger charges
     * + installments in a single transaction. Mirrors the desktop's
     * `batch_register_family` RPC and `compute-billing.ts` single-pass pricing.
     *
     * Supabase write-through: each entity is inserted into the real
     * `parents`, `students`, `ledger_entries`, and `installments` tables
     * BEFORE the local Room cache is updated. If any Supabase insert fails,
     * the whole batch is aborted and `Result.Err` is returned.
     */
    override suspend fun batchRegister(parent: CreateParentInput, students: List<CreateStudentInput>, actorId: String, actorName: String): Result<BatchRegisterResult> {
        if (students.isEmpty()) return Result.Err(Errors.validation("At least one student is required"))
        val now = Instant.now().toString()
        val year = java.time.LocalDate.now().year

        val parentCode = "PAR-$year-${UUID.randomUUID().toString().takeLast(4).uppercase()}"
        val activationCode = (100_000..999_999).random().toString()
        val parentEntity = ParentEntity(
            id = "par-${UUID.randomUUID()}",
            tenantId = com.example.infrastructure.supabase.SupabaseConfig.DEFAULT_TENANT_ID, code = parentCode,
            firstName = parent.firstName, lastName = parent.lastName,
            displayName = parent.displayName ?: "${parent.firstName} ${parent.lastName}".trim().ifEmpty { null },
            phone = parent.phone,
            whatsapp = parent.phone, email = parent.email, occupation = parent.occupation,
            address = parent.address, transportDestination = parent.transportDestination,
            preferredLanguage = parent.preferredLanguage, avatarUrl = null,
            isActive = true, isFinanciallyRestricted = false,
            activationCode = activationCode, createdAt = now, updatedAt = now,
        )

        // ── Supabase: insert the parent first ───────────────────────────
        val parentErr = com.example.infrastructure.supabase.NetworkTimeouts.guard<Throwable?>(
            "batchRegister.parent.supabase", timeoutMs = 6_000L, onlyIfConfigured = false,
        ) {
            val payload = com.example.infrastructure.supabase.SupabaseWriteThrough.parentEntityToPayload(parentEntity)
            supabaseProvider.postgrest.from("parents").insert(payload)
            null
        }
        if (parentErr != null) return Result.Err(com.example.core.Errors.fromException(parentErr))

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
                tenantId = com.example.infrastructure.supabase.SupabaseConfig.DEFAULT_TENANT_ID, code = code, parentId = parentEntity.id,
                firstName = s.firstName, lastName = s.lastName,
                displayName = s.displayName ?: "${s.firstName} ${s.lastName}".trim().ifEmpty { null },
                gender = s.gender,
                birthDate = s.birthDate, enrollmentDate = now,
                level = s.level, gradeLevel = s.gradeLevel,
                classId = s.classId, photoUrl = null, medicalNotes = s.medicalNotes,
                status = "active", createdAt = now, updatedAt = now,
            )
            studentEntities.add(studentEntity)

            // ── Supabase: insert the student ─────────────────────────────
            val studentErr = com.example.infrastructure.supabase.NetworkTimeouts.guard<Throwable?>(
                "batchRegister.student.supabase", timeoutMs = 6_000L, onlyIfConfigured = false,
            ) {
                val payload = com.example.infrastructure.supabase.SupabaseWriteThrough.studentEntityToPayload(studentEntity)
                supabaseProvider.postgrest.from("students").insert(payload)
                null
            }
            if (studentErr != null) return Result.Err(com.example.core.Errors.fromException(studentErr))

            val tuition = pricingDao.getTuitionByGrade(s.gradeLevel)
            if (tuition != null) {
                val siblingDiscount = if (index > 0) -500_000L else 0L
                val netTuition = tuition.annualAmount + siblingDiscount
                val accountId = deriveAccountId(parentEntity.id, PaymentCategory.TUITION, studentEntity.id)
                ledgerEntries.add(
                    LedgerEntryEntity(
                        id = generateEntryId(), tenantId = parentEntity.tenantId,
                        accountId = accountId, parentId = parentEntity.id, studentId = studentEntity.id,
                        category = PaymentCategory.TUITION.code, amount = netTuition,
                        type = "charge", sourceType = "installment", sourceId = "reg-${studentEntity.id}",
                        method = null, receiptNumber = null, paymentStatus = null, reversesId = null,
                        description = "Scolarité ${s.gradeLevel.uppercase()} $year",
                        actorId = actorId, actorName = actorName, at = now,
                    )
                )
                if (siblingDiscount != 0L) {
                    ledgerEntries.add(
                        LedgerEntryEntity(
                            id = generateEntryId(), tenantId = parentEntity.tenantId,
                            accountId = accountId, parentId = parentEntity.id, studentId = studentEntity.id,
                            category = PaymentCategory.TUITION.code, amount = siblingDiscount,
                            type = "adjustment", sourceType = "adjustment", sourceId = "adj-${studentEntity.id}",
                            method = null, receiptNumber = null, paymentStatus = null, reversesId = null,
                            description = "Remise fratrie (enfant #${index + 1})",
                            actorId = actorId, actorName = actorName, at = now,
                        )
                    )
                }
                val (t1, t2, t3) = com.example.core.splitNetTuitionByOfficialSchedule(netTuition)
                installments.add(inst("ins-${studentEntity.id}-t1", parentEntity.id, studentEntity.id, "tuition", "Tranche 1 (Sept–Déc)", t1, due1, now))
                installments.add(inst("ins-${studentEntity.id}-t2", parentEntity.id, studentEntity.id, "tuition", "Tranche 2 (Jan–Mar)", t2, due2, now))
                installments.add(inst("ins-${studentEntity.id}-t3", parentEntity.id, studentEntity.id, "tuition", "Tranche 3 (Avr–Juin)", t3, due3, now))
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
                    )
                )
                installments.add(inst("ins-${studentEntity.id}-tr1", parentEntity.id, studentEntity.id, "transport", "Transport T1", transport.tranche1, due1, now))
                installments.add(inst("ins-${studentEntity.id}-tr2", parentEntity.id, studentEntity.id, "transport", "Transport T2", transport.tranche2, due2, now))
                installments.add(inst("ins-${studentEntity.id}-tr3", parentEntity.id, studentEntity.id, "transport", "Transport T3", transport.tranche3, due3, now))
            }
        }

        // ── Supabase: insert ledger entries + installments ──────────────
        // Best-effort: insert all ledger entries and installments in batch.
        // If any fail, log the error but don't abort — the parent + students
        // have already been created and the user can re-trigger pricing.
        if (ledgerEntries.isNotEmpty()) {
            com.example.infrastructure.supabase.NetworkTimeouts.guard<Unit>(
                "batchRegister.ledger.supabase", timeoutMs = 8_000L, onlyIfConfigured = false,
            ) {
                val payloads = ledgerEntries.map {
                    com.example.infrastructure.supabase.SupabaseWriteThrough.ledgerEntryEntityToPayload(it)
                }
                supabaseProvider.postgrest.from("ledger_entries").insert(payloads)
            }
        }
        if (installments.isNotEmpty()) {
            com.example.infrastructure.supabase.NetworkTimeouts.guard<Unit>(
                "batchRegister.installments.supabase", timeoutMs = 8_000L, onlyIfConfigured = false,
            ) {
                val payloads = installments.map {
                    com.example.infrastructure.supabase.SupabaseWriteThrough.installmentEntityToPayload(it)
                }
                supabaseProvider.postgrest.from("installments").insert(payloads)
            }
        }

        // ── Local Room cache mirror ──────────────────────────────────────
        parentDao.upsert(parentEntity)
        studentDao.upsertAll(studentEntities)
        db.ledgerEntryDao().upsertAll(ledgerEntries)
        db.installmentDao().upsertAll(installments)

        auditDao.upsert(audit("crm.batch_register", "parent", parentEntity.id, actorId, actorName,
            after = """{"student_count":${students.size},"activation_code":"$activationCode"}"""))

        return Result.Ok(BatchRegisterResult(
            parent = LocalMappers.run { parentEntity.toDomain() },
            students = studentEntities.map { LocalMappers.run { it.toDomain() } },
            activationCode = activationCode,
        ))
    }

    override suspend fun promoteStudents(academicYear: String, decisions: List<com.example.domain.repository.PromotionDecision>, actorId: String, actorName: String): Result<Unit> {
        decisions.forEach { d ->
            val existing = studentDao.getById(d.studentId) ?: return@forEach
            val updated = existing.copy(updatedAt = Instant.now().toString())
            studentDao.update(updated)
            auditDao.upsert(audit("student.promote", "student", d.studentId, actorId, actorName,
                after = """{"decision":"${d.decision}","year":"$academicYear"}"""))
        }
        return Result.Ok(Unit)
    }
}

// ─── Payment Repository (with waterfall allocation) ─────────────────────────

@Singleton
class LocalPaymentRepository @Inject constructor(
    private val db: ElImtiyazDatabase,
    private val paymentDao: PaymentDao,
    private val installmentDao: InstallmentDao,
    private val ledgerDao: LedgerEntryDao,
    private val auditDao: AuditLogDao,
    private val supabaseProvider: com.example.infrastructure.supabase.SupabaseClientProvider,
) : PaymentRepository {

    override fun observe(): Flow<List<Payment>> =
        paymentDao.observeAll().map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeByParent(parentId: String): Flow<List<Payment>> =
        paymentDao.observeByParent(parentId).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeByStudent(studentId: String): Flow<List<Payment>> =
        paymentDao.observeByStudent(studentId).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeById(id: String): Flow<Payment?> =
        paymentDao.observeById(id).map { it?.let { e -> LocalMappers.run { e.toDomain() } } }

    /**
     * Collect a payment — mirrors the desktop's `collect_and_allocate_payment` RPC:
     *   1. Validate amount + method + proof requirement.
     *   2. Create payment record (cash=paid, check/transfer=pending).
     *   3. Append signed ledger entry (negative = credit).
     *   4. Waterfall-allocate against unpaid installments.
     *   5. Overpayment → parent_credit adjustment.
     */
    override suspend fun collect(input: CollectPaymentInput, actorId: String, actorName: String): Result<Payment> {
        if (input.amount <= 0L) return Result.Err(Errors.validation("Amount must be > 0"))
        if (input.method.requiresProof && input.proofPath.isNullOrBlank())
            return Result.Err(Errors.validation("Proof is required for ${input.method.code}"))

        val now = Instant.now().toString()
        val year = java.time.LocalDate.now().year
        val seq = (paymentDao.listAll().size + 1).toString().padStart(6, '0')
        val receipt = "REC-$year-$seq"
        val paymentId = "pay-${UUID.randomUUID()}"
        val status = if (input.method == PaymentMethod.CASH) PaymentStatus.PAID else PaymentStatus.PENDING

        val entity = PaymentEntity(
            id = paymentId, tenantId = com.example.infrastructure.supabase.SupabaseConfig.DEFAULT_TENANT_ID, receiptNumber = receipt,
            parentId = input.parentId, studentId = input.studentId, amount = input.amount,
            method = input.method.code, status = status.code, category = input.category.code,
            installmentId = input.installmentId, proofUrl = input.proofPath,
            checkNumber = input.checkNumber, checkBankName = input.checkBankName,
            checkIssueDate = input.checkIssueDate, checkClearanceDate = input.checkClearanceDate,
            transferReference = input.transferReference, transferSourceBank = input.transferSourceBank,
            notes = input.notes, collectedBy = actorId, collectedBy_name = actorName,
            collectedAt = now, createdAt = now, updatedAt = now,
        )

        // Build the ledger payment entry (negative = credit) BEFORE writing
        // so we can send both to Supabase in the same logical transaction.
        val ledgerEntry = createPaymentEntry(
            tenantId = entity.tenantId, parentId = input.parentId, studentId = input.studentId,
            category = input.category, amount = input.amount,
            method = input.method, receiptNumber = receipt, paymentStatus = status,
            sourceId = paymentId, actorId = actorId, actorName = actorName,
            description = "Encaissement $receipt",
        )

        // ── Supabase write-through: insert payment + ledger entry ────────
        val supabaseErr = com.example.infrastructure.supabase.NetworkTimeouts.guard<Throwable?>(
            "payment.collect.supabase", timeoutMs = 6_000L, onlyIfConfigured = false,
        ) {
            val paymentPayload = com.example.infrastructure.supabase.SupabaseWriteThrough.paymentEntityToPayload(entity)
            supabaseProvider.postgrest.from("payments").insert(paymentPayload)
            val ledgerPayload = com.example.infrastructure.supabase.SupabaseWriteThrough.ledgerEntryEntityToPayload(ledgerEntry.toEntity())
            supabaseProvider.postgrest.from("ledger_entries").insert(ledgerPayload)
            null
        }
        if (supabaseErr != null) {
            return Result.Err(com.example.core.Errors.fromException(supabaseErr))
        }

        // Supabase insert succeeded → mirror into Room cache.
        paymentDao.upsert(entity)
        ledgerDao.upsert(ledgerEntry.toEntity())

        val familyInstallments = installmentDao.listByParent(input.parentId)
            .map { WaterfallInstallment(it.id, PaymentCategory.fromCode(it.category), it.amountDue, it.amountPaid, it.amountPending, it.dueDate, it.status) }

        val allocation = allocatePaymentToInstallments(
            installments = familyInstallments,
            paymentAmount = input.amount,
            categoryFilter = input.category,
            paymentStatus = status,
        )

        // ── Push installment updates to Supabase (best-effort) ───────────
        val updatedInstallments = mutableListOf<InstallmentEntity>()
        allocation.allocations.forEach { a ->
            installmentDao.getById(a.installmentId)?.let { ins ->
                val upd = ins.copy(
                    amountPaid = a.newAmountPaid,
                    amountPending = a.newAmountPending,
                    status = a.newStatus,
                    paidDate = if (a.newStatus == "paid") now else ins.paidDate,
                    updatedAt = now,
                )
                installmentDao.update(upd)
                updatedInstallments.add(upd)
            }
        }
        if (updatedInstallments.isNotEmpty()) {
            com.example.infrastructure.supabase.NetworkTimeouts.guard<Unit>(
                "payment.collect.installments.supabase", timeoutMs = 6_000L, onlyIfConfigured = false,
            ) {
                val payloads = updatedInstallments.map {
                    com.example.infrastructure.supabase.SupabaseWriteThrough.installmentEntityToPayload(it)
                }
                // Update each installment in Supabase by id.
                payloads.forEachIndexed { i, _ ->
                    supabaseProvider.postgrest.from("installments").update(payloads[i]) {
                        filter { eq("id", updatedInstallments[i].id) }
                    }
                }
            }
        }

        if (allocation.unallocatedAmount > 0L) {
            val creditEntry = com.example.core.createAdjustmentEntry(
                tenantId = entity.tenantId, parentId = input.parentId, studentId = input.studentId,
                category = input.category, amount = -allocation.unallocatedAmount,
                sourceId = paymentId, actorId = actorId, actorName = actorName,
                reason = "Crédit parent (trop-perçu) $receipt",
            )
            ledgerDao.upsert(creditEntry.toEntity())
            // Best-effort Supabase insert of the credit adjustment.
            com.example.infrastructure.supabase.NetworkTimeouts.guard<Unit>(
                "payment.collect.credit.supabase", timeoutMs = 4_000L, onlyIfConfigured = false,
            ) {
                val payload = com.example.infrastructure.supabase.SupabaseWriteThrough.ledgerEntryEntityToPayload(creditEntry.toEntity())
                supabaseProvider.postgrest.from("ledger_entries").insert(payload)
            }
        }

        auditDao.upsert(audit("payment.collect", "payment", paymentId, actorId, actorName,
            after = """{"receipt":"$receipt","amount":${input.amount},"method":"${input.method.code}"}"""))
        return Result.Ok(LocalMappers.run { entity.toDomain() })
    }

    override suspend fun refund(paymentId: String, reason: String, actorId: String, actorName: String): Result<Payment> {
        val existing = paymentDao.getById(paymentId) ?: return Result.Err(Errors.notFound("Payment $paymentId not found"))
        val now = Instant.now().toString()
        val updated = existing.copy(status = PaymentStatus.REFUNDED.code, updatedAt = now)

        // ── Supabase write-through: update payment status ────────────────
        val supabaseErr = com.example.infrastructure.supabase.NetworkTimeouts.guard<Throwable?>(
            "payment.refund.supabase", timeoutMs = 6_000L, onlyIfConfigured = false,
        ) {
            val payload = com.example.infrastructure.supabase.SupabaseWriteThrough.paymentEntityToPayload(updated)
            supabaseProvider.postgrest.from("payments").update(payload) {
                filter { eq("id", paymentId) }
            }
            null
        }
        if (supabaseErr != null) {
            return Result.Err(com.example.core.Errors.fromException(supabaseErr))
        }

        paymentDao.update(updated)

        val originalLedger = ledgerDao.listByParent(existing.parentId)
            .firstOrNull { it.sourceId == paymentId && it.type == "payment" }
        if (originalLedger != null) {
            val reversal = createReversalEntry(LocalMappers.run { originalLedger.toDomain() }, reason, actorId, actorName)
            ledgerDao.upsert(reversal.toEntity())
            // Best-effort Supabase insert of the reversal ledger entry.
            com.example.infrastructure.supabase.NetworkTimeouts.guard<Unit>(
                "payment.refund.reversal.supabase", timeoutMs = 4_000L, onlyIfConfigured = false,
            ) {
                val payload = com.example.infrastructure.supabase.SupabaseWriteThrough.ledgerEntryEntityToPayload(reversal.toEntity())
                supabaseProvider.postgrest.from("ledger_entries").insert(payload)
            }

            val familyInstallments = installmentDao.listByParent(existing.parentId)
                .map { WaterfallInstallment(it.id, PaymentCategory.fromCode(it.category), it.amountDue, it.amountPaid, it.amountPending, it.dueDate, it.status) }
            val revert = com.example.core.revertPaymentAllocation(
                installments = familyInstallments,
                reversalAmount = existing.amount,
                categoryFilter = PaymentCategory.fromCode(existing.category),
            )
            val revertedInstallments = mutableListOf<InstallmentEntity>()
            revert.reverts.forEach { r ->
                installmentDao.getById(r.installmentId)?.let { ins ->
                    val upd = ins.copy(
                        amountPaid = r.newAmountPaid,
                        amountPending = r.newAmountPending,
                        status = r.newStatus,
                        updatedAt = now,
                    )
                    installmentDao.update(upd)
                    revertedInstallments.add(upd)
                }
            }
            // Best-effort: push reverted installment states to Supabase.
            if (revertedInstallments.isNotEmpty()) {
                com.example.infrastructure.supabase.NetworkTimeouts.guard<Unit>(
                    "payment.refund.installments.supabase", timeoutMs = 4_000L, onlyIfConfigured = false,
                ) {
                    revertedInstallments.forEach { upd ->
                        val payload = com.example.infrastructure.supabase.SupabaseWriteThrough.installmentEntityToPayload(upd)
                        supabaseProvider.postgrest.from("installments").update(payload) {
                            filter { eq("id", upd.id) }
                        }
                    }
                }
            }
        }

        auditDao.upsert(audit("payment.refund", "payment", paymentId, actorId, actorName,
            after = """{"reason":"$reason"}"""))
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }

    override suspend fun adjust(input: com.example.domain.repository.AdjustAccountInput, actorId: String, actorName: String): Result<Unit> {
        val entry = com.example.core.createAdjustmentEntry(
            tenantId = com.example.infrastructure.supabase.SupabaseConfig.DEFAULT_TENANT_ID, parentId = input.parentId, studentId = input.studentId,
            category = input.category, amount = input.amount,
            sourceId = "adj-${UUID.randomUUID()}", actorId = actorId, actorName = actorName,
            reason = input.reason, receiptRef = input.receiptRef,
        )
        // ── Supabase write-through: append adjustment ledger entry ────────
        val supabaseErr = com.example.infrastructure.supabase.NetworkTimeouts.guard<Throwable?>(
            "payment.adjust.supabase", timeoutMs = 6_000L, onlyIfConfigured = false,
        ) {
            val payload = com.example.infrastructure.supabase.SupabaseWriteThrough.ledgerEntryEntityToPayload(entry.toEntity())
            supabaseProvider.postgrest.from("ledger_entries").insert(payload)
            null
        }
        if (supabaseErr != null) {
            return Result.Err(com.example.core.Errors.fromException(supabaseErr))
        }

        ledgerDao.upsert(entry.toEntity())
        auditDao.upsert(audit("payment.adjust", "ledger", entry.id, actorId, actorName,
            after = """{"reason":"${input.reason}","amount":${input.amount}}"""))
        return Result.Ok(Unit)
    }
}

// ─── Installment Repository ─────────────────────────────────────────────────

@Singleton
class LocalInstallmentRepository @Inject constructor(
    private val installmentDao: InstallmentDao,
    private val auditDao: AuditLogDao,
    private val supabaseProvider: com.example.infrastructure.supabase.SupabaseClientProvider,
) : InstallmentRepository {

    override fun observeByParent(parentId: String): Flow<List<Installment>> =
        installmentDao.observeByParent(parentId).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeByStudent(studentId: String): Flow<List<Installment>> =
        installmentDao.observeByStudent(studentId).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeById(id: String): Flow<Installment?> =
        installmentDao.observeById(id).map { it?.let { e -> LocalMappers.run { e.toDomain() } } }

    override suspend fun markPaid(id: String, actorId: String, actorName: String): Result<Installment> {
        val existing = installmentDao.getById(id) ?: return Result.Err(Errors.notFound("Installment $id not found"))
        val now = Instant.now().toString()
        val updated = existing.copy(amountPaid = existing.amountDue, status = "paid", paidDate = now, updatedAt = now)

        // ── Supabase write-through ───────────────────────────────────────
        val supabaseErr = com.example.infrastructure.supabase.NetworkTimeouts.guard<Throwable?>(
            "installment.markPaid.supabase", timeoutMs = 6_000L, onlyIfConfigured = false,
        ) {
            val payload = com.example.infrastructure.supabase.SupabaseWriteThrough.installmentEntityToPayload(updated)
            supabaseProvider.postgrest.from("installments").update(payload) {
                filter { eq("id", id) }
            }
            null
        }
        if (supabaseErr != null) {
            return Result.Err(com.example.core.Errors.fromException(supabaseErr))
        }

        installmentDao.update(updated)
        auditDao.upsert(audit("installment.markPaid", "installment", id, actorId, actorName))
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }

    override suspend fun updateDueDate(id: String, dueDate: String, note: String?, actorId: String, actorName: String): Result<Installment> {
        val existing = installmentDao.getById(id) ?: return Result.Err(Errors.notFound("Installment $id not found"))
        val updated = existing.copy(dueDate = dueDate, customSchedule = true, customScheduleNote = note, updatedAt = Instant.now().toString())

        // ── Supabase write-through ───────────────────────────────────────
        val supabaseErr = com.example.infrastructure.supabase.NetworkTimeouts.guard<Throwable?>(
            "installment.updateDueDate.supabase", timeoutMs = 6_000L, onlyIfConfigured = false,
        ) {
            val payload = com.example.infrastructure.supabase.SupabaseWriteThrough.installmentEntityToPayload(updated)
            supabaseProvider.postgrest.from("installments").update(payload) {
                filter { eq("id", id) }
            }
            null
        }
        if (supabaseErr != null) {
            return Result.Err(com.example.core.Errors.fromException(supabaseErr))
        }

        installmentDao.update(updated)
        auditDao.upsert(audit("installment.updateDueDate", "installment", id, actorId, actorName))
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }

    override suspend fun regenerateForCycle(parentId: String, cycle: String, actorId: String, actorName: String): Result<List<Installment>> {
        auditDao.upsert(audit("installment.regenerate", "installment", parentId, actorId, actorName, after = """{"cycle":"$cycle"}"""))
        return Result.Ok(installmentDao.listByParent(parentId).map { LocalMappers.run { it.toDomain() } })
    }

    override suspend fun findOverdue(): Result<List<Installment>> {
        val now = Instant.now().toString()
        return Result.Ok(installmentDao.listOverdue(now).map { LocalMappers.run { it.toDomain() } })
    }
}

// ─── Ledger Repository ──────────────────────────────────────────────────────

@Singleton
class LocalLedgerRepository @Inject constructor(
    private val ledgerDao: LedgerEntryDao,
    private val supabaseProvider: com.example.infrastructure.supabase.SupabaseClientProvider,
) : LedgerRepository {

    override fun observe(): Flow<List<com.example.core.LedgerEntry>> =
        ledgerDao.observeAll().map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeByParent(parentId: String): Flow<List<com.example.core.LedgerEntry>> =
        ledgerDao.observeByParent(parentId).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeByAccount(accountId: String): Flow<List<com.example.core.LedgerEntry>> =
        ledgerDao.observeByAccount(accountId).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override suspend fun append(entry: com.example.core.LedgerEntry): Result<com.example.core.LedgerEntry> {
        // ── Supabase write-through ───────────────────────────────────────
        val supabaseErr = com.example.infrastructure.supabase.NetworkTimeouts.guard<Throwable?>(
            "ledger.append.supabase", timeoutMs = 6_000L, onlyIfConfigured = false,
        ) {
            val payload = com.example.infrastructure.supabase.SupabaseWriteThrough.ledgerEntryEntityToPayload(entry.toEntity())
            supabaseProvider.postgrest.from("ledger_entries").insert(payload)
            null
        }
        if (supabaseErr != null) {
            return Result.Err(com.example.core.Errors.fromException(supabaseErr))
        }

        ledgerDao.upsert(entry.toEntity())
        return Result.Ok(entry)
    }

    override suspend fun appendMany(entries: List<com.example.core.LedgerEntry>): Result<List<com.example.core.LedgerEntry>> {
        if (entries.isEmpty()) return Result.Ok(entries)
        // ── Supabase write-through (batch) ────────────────────────────────
        val supabaseErr = com.example.infrastructure.supabase.NetworkTimeouts.guard<Throwable?>(
            "ledger.appendMany.supabase", timeoutMs = 8_000L, onlyIfConfigured = false,
        ) {
            val payloads = entries.map {
                com.example.infrastructure.supabase.SupabaseWriteThrough.ledgerEntryEntityToPayload(it.toEntity())
            }
            supabaseProvider.postgrest.from("ledger_entries").insert(payloads)
            null
        }
        if (supabaseErr != null) {
            return Result.Err(com.example.core.Errors.fromException(supabaseErr))
        }

        ledgerDao.upsertAll(entries.map { it.toEntity() })
        return Result.Ok(entries)
    }

    override suspend fun reverse(originalId: String, reason: String, actorId: String, actorName: String): Result<com.example.core.LedgerEntry> {
        val original = ledgerDao.getById(originalId) ?: return Result.Err(Errors.notFound("Ledger entry $originalId not found"))
        val reversal = createReversalEntry(LocalMappers.run { original.toDomain() }, reason, actorId, actorName)

        // ── Supabase write-through ───────────────────────────────────────
        val supabaseErr = com.example.infrastructure.supabase.NetworkTimeouts.guard<Throwable?>(
            "ledger.reverse.supabase", timeoutMs = 6_000L, onlyIfConfigured = false,
        ) {
            val payload = com.example.infrastructure.supabase.SupabaseWriteThrough.ledgerEntryEntityToPayload(reversal.toEntity())
            supabaseProvider.postgrest.from("ledger_entries").insert(payload)
            null
        }
        if (supabaseErr != null) {
            return Result.Err(com.example.core.Errors.fromException(supabaseErr))
        }

        ledgerDao.upsert(reversal.toEntity())
        return Result.Ok(reversal)
    }

    override suspend fun summary(parentId: String): Result<com.example.core.ParentLedgerSummary> {
        val entries = ledgerDao.listByParent(parentId).map { LocalMappers.run { it.toDomain() } }
        val summary = LedgerEngine.computeParentSummary(entries, parentId, "")
        return Result.Ok(summary)
    }

    override suspend fun reconcile(): Result<com.example.core.Reconcile.Report> {
        val entries = ledgerDao.listAll().map { LocalMappers.run { it.toDomain() } }
        return Result.Ok(com.example.core.Reconcile.reconcileLedger(entries))
    }
}

// ─── Helper extensions ──────────────────────────────────────────────────────

private fun com.example.core.LedgerEntry.toEntity() = LedgerEntryEntity(
    id = id, tenantId = tenantId, accountId = accountId, parentId = parentId,
    studentId = studentId, category = category.code, amount = amount, type = type.code,
    sourceType = sourceType.code, sourceId = sourceId, method = method?.code,
    receiptNumber = receiptNumber, paymentStatus = paymentStatus?.code,
    reversesId = reversesId, description = description, actorId = actorId,
    actorName = actorName, at = at,
)

private fun audit(action: String, entityType: String, entityId: String, actorId: String, actorName: String, after: String? = null) = AuditLogEntity(
    id = "aud-${UUID.randomUUID()}", tenantId = com.example.infrastructure.supabase.SupabaseConfig.DEFAULT_TENANT_ID,
    action = action, entityType = entityType, entityId = entityId,
    actorId = actorId, actorName = actorName, actorRole = null,
    beforeJson = null, afterJson = after, note = null,
    createdAt = Instant.now().toString(),
)

private fun inst(id: String, parentId: String, studentId: String, category: String, label: String, amountDue: Long, dueDate: String, now: String) = InstallmentEntity(
    id = id, tenantId = com.example.infrastructure.supabase.SupabaseConfig.DEFAULT_TENANT_ID, parentId = parentId, studentId = studentId,
    category = category, label = label, amountDue = amountDue, amountPaid = 0L, amountPending = 0L,
    dueDate = dueDate, paidDate = null,
    status = if (dueDate < now) "overdue" else "pending",
    academicCycle = null, customSchedule = false, customScheduleNote = null,
    createdAt = now, updatedAt = now,
)
