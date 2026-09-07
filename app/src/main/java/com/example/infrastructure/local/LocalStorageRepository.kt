package com.example.infrastructure.local

import com.example.core.Errors
import com.example.core.Result
import com.example.core.absenceAlertThreshold
import com.example.core.currentTermWindow
import com.example.core.agingBucketFromDays
import com.example.core.daysBetweenFloor
import com.example.core.formatDzd
import com.example.core.LedgerEngine
import com.example.domain.model.AcademicClass
import com.example.domain.model.AppNotification
import com.example.domain.model.Assessment
import com.example.domain.model.AttendanceRecord
import com.example.domain.model.AuditLog
import com.example.domain.model.ClassRollCallStatus
import com.example.domain.model.DashboardKpi
import com.example.domain.model.DashboardOperationalAlert
import com.example.domain.model.DebtSummary
import com.example.domain.model.Department
import com.example.domain.model.Expense
import com.example.domain.model.GradeLevelTuition
import com.example.domain.model.Homework
import com.example.domain.model.Installment
import com.example.domain.model.Parent
import com.example.domain.model.Payment
import com.example.domain.model.PaymentMethodSummary
import com.example.domain.model.Personnel
import com.example.domain.model.PricingConfig
import com.example.domain.model.ReleveEntry
import com.example.domain.model.Student
import com.example.domain.model.Subject
import com.example.domain.repository.AuditFilter
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import com.example.domain.repository.ClassRepository
import com.example.domain.repository.CreateClassInput
import com.example.domain.repository.CreateDepartmentInput
import com.example.domain.repository.CreatePersonnelInput
import com.example.domain.repository.CreateSubjectInput
import com.example.domain.repository.DashboardRepository
import com.example.domain.repository.DebtRepository
import com.example.domain.repository.DepartmentRepository
import com.example.domain.repository.EnterGradeInput
import com.example.domain.repository.ExpenseRepository
import com.example.domain.repository.GradeRepository
import com.example.domain.repository.HomeworkRepository
import com.example.domain.repository.NotificationRepository
import com.example.domain.repository.ParentFinancialProfile
import com.example.domain.repository.PricingRepository
import com.example.domain.repository.PushHomeworkInput
import com.example.domain.repository.ReleveRepository
import com.example.domain.repository.RollCallEntry
import com.example.domain.repository.RoutingRepository
import com.example.domain.repository.StorageRepository
import com.example.domain.repository.SubmitExpenseInput
import com.example.domain.repository.SubjectRepository
import com.example.domain.repository.UpdateClassInput
import com.example.domain.repository.UpdatePersonnelInput
import com.example.domain.repository.UpdateSubjectInput
import com.example.domain.repository.WorkflowRepository
import com.example.domain.model.GeoPoint
import com.example.infrastructure.routing.OsrmClient
import com.example.infrastructure.routing.TspSolver
import com.example.infrastructure.room.AcademicClassDao
import com.example.infrastructure.room.AcademicClassEntity
import com.example.infrastructure.room.AssessmentDao
import com.example.infrastructure.room.AssessmentEntity
import com.example.infrastructure.room.AttendanceDao
import com.example.infrastructure.room.AttendanceEntity
import com.example.infrastructure.room.AuditLogDao
import com.example.infrastructure.room.AuditLogEntity
import com.example.infrastructure.room.ClassSubjectDao
import com.example.infrastructure.room.ClassSubjectEntity
import com.example.infrastructure.room.DepartmentDao
import com.example.infrastructure.room.DepartmentEntity
import com.example.infrastructure.room.ElImtiyazDatabase
import com.example.infrastructure.room.ExpenseDao
import com.example.infrastructure.room.ExpenseEntity
import com.example.infrastructure.room.HomeworkDao
import com.example.infrastructure.room.HomeworkEntity
import com.example.infrastructure.room.InstallmentEntity
import com.example.infrastructure.room.LedgerEntryEntity
import com.example.infrastructure.room.LocalMappers
import com.example.infrastructure.room.NotificationDao
import com.example.infrastructure.room.NotificationEntity
import com.example.infrastructure.room.ParentDao
import com.example.infrastructure.room.ParentEntity
import com.example.infrastructure.room.PaymentDao
import com.example.infrastructure.room.PaymentEntity
import com.example.infrastructure.room.PersonnelDao
import com.example.infrastructure.room.PersonnelEntity
import com.example.infrastructure.room.PricingConfigDao
import com.example.infrastructure.room.PricingConfigEntity
import com.example.infrastructure.room.PricingDiscountEntity
import com.example.infrastructure.room.ReleveEntryDao
import com.example.infrastructure.room.ReleveEntryEntity
import com.example.infrastructure.room.StudentDao
import com.example.infrastructure.room.StudentEntity
import com.example.infrastructure.room.SubjectDao
import com.example.infrastructure.room.SubjectEntity
import com.example.infrastructure.room.TransportPricingEntity
import com.example.infrastructure.room.TripLogDao
import com.example.infrastructure.room.TripLogEntity
import com.example.infrastructure.room.VehicleDao
import com.example.infrastructure.room.VehicleEntity
import com.example.infrastructure.room.RoutingStopDao
import com.example.infrastructure.room.RoutingStopEntity
import com.example.infrastructure.room.WorkflowRunDao
import com.example.infrastructure.room.WorkflowRunEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ─── Storage Repository ─────────────────────────────────────────────────────

/**
 * Local file-backed [StorageRepository] — REAL persistence (previously
 * `uploadProof` returned a fabricated `local://…` URL and silently DISCARDED
 * the bytes, so scanned payment/expense proofs were never actually stored).
 *
 * Proof files are written under `{filesDir}/proofs/{bucket}/{entityId}/` and
 * the returned `file://` URI resolves to a real on-device file that can be
 * re-opened, shared and re-uploaded later. When a Supabase Storage bucket is
 * configured, the bytes are ALSO pushed to the remote bucket and the remote
 * path is preferred (the local copy is kept as an offline cache).
 */
@Singleton
class LocalStorageRepository @Inject constructor(
    private val auditContext: AuditContext,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val provider: com.example.infrastructure.supabase.SupabaseClientProvider,
) : StorageRepository {

    override suspend fun uploadProof(
        bucket: String,
        entityId: String,
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
    ): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // ── Local file persistence (always — offline cache) ──
        val localFile = try {
            val dir = java.io.File(java.io.File(context.filesDir, "proofs"), "$bucket/$entityId")
            dir.mkdirs()
            val file = java.io.File(dir, fileName)
            file.writeBytes(bytes)
            file
        } catch (e: Exception) {
            null
        }

        // ── Remote (Supabase Storage) when configured ──
        val remotePath: String? = com.example.infrastructure.supabase.NetworkTimeouts.guard(
            "storage.uploadProof",
        ) {
            provider.storage.from(bucket).upload("$entityId/$fileName", bytes) {
                upsert = true
                contentType = io.ktor.http.ContentType.parse(mimeType)
            }
            "$entityId/$fileName"
        }
        if (remotePath != null) return@withContext Result.Ok(remotePath)

        // ── Offline / unconfigured: the real local file ──
        if (localFile != null) {
            Result.Ok("file://${localFile.absolutePath}")
        } else {
            Result.Err(
                com.example.core.Errors.unknown(
                    "uploadProof failed: could not write local proof file",
                    userMessage = "Échec de l'enregistrement du justificatif.",
                ),
            )
        }
    }

    override suspend fun createSignedUrl(bucket: String, path: String, expiresInSeconds: Long): Result<String> {
        // Try the remote bucket first when configured; otherwise resolve the
        // locally persisted file (an honest, resolvable file:// URI — the
        // previous implementation fabricated a URL that pointed at nothing).
        val remote = com.example.infrastructure.supabase.NetworkTimeouts.guard<String>(
            "storage.createSignedUrl",
        ) {
            provider.storage.from(bucket).createSignedUrl(path, kotlin.time.Duration.parseIsoString("PT${expiresInSeconds}S"))
        }
        if (remote != null) return Result.Ok(remote)

        val local = java.io.File(java.io.File(context.filesDir, "proofs"), "$bucket/$path")
        return if (local.exists()) {
            Result.Ok("file://${local.absolutePath}")
        } else {
            Result.Err(com.example.core.Errors.notFound("Aucun justificatif stocké pour $bucket/$path"))
        }
    }
}

// ─── Helper ─────────────────────────────────────────────────────────────────
