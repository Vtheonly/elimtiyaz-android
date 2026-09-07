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

// ─── Parent Repository ──────────────────────────────────────────────────────

/** Canonical enrollment-status value set (SQL 0005 + migration 0037 superset). */
val CANONICAL_STUDENT_STATUSES: Set<String> = setOf(
    "inquiry", "quoted", "enrolled", "active", "suspended", "transferred", "withdrawn", "graduated",
)

@Singleton
class LocalParentRepository @Inject constructor(
    private val auditContext: AuditContext,
    private val parentDao: ParentDao,
    private val auditDao: AuditLogDao,
    // FIX (orphaned records): needed by deleteParent to refuse deleting a
    // parent that still has linked students (desktop parity).
    private val studentDao: StudentDao,
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
        // TIER 2 R15 — deterministic parent_code via FNV-1a hash.
        // Re-creating the same parent (or re-importing the same Excel row)
        // produces the SAME code → the `upsert_parent_from_import` RPC's
        // primary identity match (tenant_id, parent_code) succeeds →
        // idempotent upsert, no duplicate parents.
        val code = com.example.core.deterministicParentCode(
            year = year,
            input = com.example.core.ParentCodeInput(
                phone = input.phone,
                displayName = input.displayName,
                firstName = input.firstName,
                lastName = input.lastName,
            ),
        )
        // TIER 2 R15 — deterministic activation_code derived from
        // (parentCode, tenantId) so re-creating the same parent produces
        // the same code → the `bind-activation-code` edge function's
        // idempotency contract holds.
        val activationCode = com.example.core.deterministicActivationCode(
            parentCode = code,
            tenantId = auditContext.tenantId(),
        )
        val entity = ParentEntity(
            id = "par-${UUID.randomUUID()}",
            tenantId = auditContext.tenantId(), code = code,
            firstName = input.firstName, lastName = input.lastName,
            displayName = input.displayName ?: "${input.firstName} ${input.lastName}".trim().ifEmpty { null },
            phone = input.phone,
            // Vault §04.03 — secondary phone / national ID / relationship from
            // the batch-registration master info block.
            whatsapp = input.secondaryPhone ?: input.phone,
            email = input.email, occupation = input.occupation,
            address = input.address, transportDestination = input.transportDestination,
            nationalId = input.nationalId,
            relationship = input.relationship,
            preferredLanguage = input.preferredLanguage, avatarUrl = null,
            isActive = true, isFinanciallyRestricted = false,
            activationCode = activationCode, createdAt = now, updatedAt = now,
        )
        parentDao.upsert(entity)
        val parent = LocalMappers.run { entity.toDomain() }
        auditDao.upsert(auditContext.audit("parent.create", "parent", parent.id, actorId, actorName,
            after = """{"code":"$code","name":"${parent.fullName}"}"""))
        return Result.Ok(parent)
    }

    override suspend fun updateParent(id: String, input: UpdateParentInput, actorId: String, actorName: String): Result<Parent> {
        val existing = parentDao.getById(id) ?: return Result.Err(Errors.notFound("Parent $id not found"))
        val updated = existing.copy(
            firstName = input.firstName ?: existing.firstName,
            lastName = input.lastName ?: existing.lastName,
            // FIX (dropped field): `displayName` was accepted but never applied.
            // Only recompute when first/last actually change so unrelated
            // updates don't clobber an imported display name.
            displayName = when {
                input.displayName != null -> input.displayName
                input.firstName != null || input.lastName != null ->
                    listOfNotNull(
                        input.firstName ?: existing.firstName,
                        input.lastName ?: existing.lastName,
                    ).joinToString(" ").trim().ifEmpty { existing.displayName }
                else -> existing.displayName
            },
            phone = input.phone ?: existing.phone,
            email = input.email ?: existing.email,
            occupation = input.occupation ?: existing.occupation,
            address = input.address ?: existing.address,
            transportDestination = input.transportDestination ?: existing.transportDestination,
            // Vault §04.03 — master-info edits (secondary phone / national ID /
            // relationship). Nullable elvis keeps unset fields untouched.
            whatsapp = input.secondaryPhone ?: existing.whatsapp,
            nationalId = input.nationalId ?: existing.nationalId,
            relationship = input.relationship ?: existing.relationship,
            preferredLanguage = input.preferredLanguage ?: existing.preferredLanguage,
            updatedAt = Instant.now().toString(),
        )
        parentDao.update(updated)
        auditDao.upsert(auditContext.audit("parent.update", "parent", id, actorId, actorName, after = "{}"))
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }

    override suspend fun deleteParent(id: String, actorId: String, actorName: String): Result<Unit> {
        // FIX (orphaned records): deleting a parent left its students (and
        // their payments/ledger entries) orphaned — dangling parent_id FKs.
        // Mirrors the desktop semantics: refuse to delete a parent that still
        // has linked students.
        val children = studentDao.observeByParent(id).first()
        if (children.isNotEmpty()) {
            return Result.Err(Errors.conflict(
                "Impossible de supprimer ce parent : ${children.size} élève(s) y sont encore rattachés. " +
                    "Transférez ou retirez d'abord les élèves.",
            ))
        }
        parentDao.deleteById(id)
        auditDao.upsert(auditContext.audit("parent.delete", "parent", id, actorId, actorName))
        return Result.Ok(Unit)
    }
}
