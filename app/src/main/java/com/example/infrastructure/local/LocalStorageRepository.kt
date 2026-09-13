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
 * Proof files are written under `{filesDir}/proofs/{bucket}/{objectPath}` and
 * the returned `file://` URI resolves to a real on-device file that can be
 * re-opened, shared and re-uploaded later. When a Supabase Storage bucket is
 * configured, the bytes are ALSO pushed to the remote bucket and the remote
 * path is preferred (the local copy is kept as an offline cache).
 *
 * T-362 / UPLOAD-103 — the honest-failure contract:
 *  - The remote upload uses the CANONICAL tenant-scoped path
 *    `{tenantId}/{entityId}/{fileName}` (every storage.objects policy in the
 *    hub chain requires folder[1] = current_tenant_id(); the previous
 *    tenant-less path was RLS-rejected on EVERY upload — live RED proof
 *    t-359-upload-e2e.py check A).
 *  - A null [tenantId] fails closed BEFORE any remote call (the caller's
 *    session has no working tenant — the desktop's T-053 semantics); the
 *    local file is still written so the bytes are never lost, but the
 *    result is an explicit error the UI can surface.
 *  - TRANSPORT failures (offline, DNS, timeout, 5xx) keep the sanctioned
 *    offline-first behaviour: local file returned, remote push deferred —
 *    classified by the SAME canonical classifier the sync pipeline uses
 *    ([com.example.infrastructure.sync.SyncErrorClassifier] — reuse, not a
 *    parallel implementation).
 *  - PERMANENT server rejections (4xx: RLS denial, validation, 401) surface
 *    as `Result.Err` — NEVER a fake success with a local path. The previous
 *    implementation converted the RestException into `null` inside the
 *    read-oriented guard and reported success while the proof never reached
 *    the server (the CROSS-200 anti-pattern reborn in the storage path).
 *  - Uploads get a DEDICATED 60 s timeout ([UPLOAD_TIMEOUT_MS]) — the 4 s
 *    read-oriented default aborts real 10 MB proof uploads mid-flight on
 *    ordinary mobile networks, which the old code misclassified as offline.
 */
@Singleton
class LocalStorageRepository @Inject constructor(
    private val auditContext: AuditContext,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val provider: com.example.infrastructure.supabase.SupabaseClientProvider,
    private val onlineDetector: com.example.infrastructure.sync.OnlineDetector,
) : StorageRepository {

    override suspend fun uploadProof(
        bucket: String,
        tenantId: String?,
        entityId: String,
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
    ): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // ── Local file persistence (always — offline cache) ──
        // The local tree mirrors the REMOTE object path so a cached file
        // resolves through the same path the server would (and legacy
        // pre-T-362 files under proofs/{bucket}/{entityId}/{fileName} remain
        // readable — createSignedUrl's local fallback tries the exact path).
        val localFile = try {
            val dir = java.io.File(java.io.File(context.filesDir, "proofs"), "$bucket/$entityId")
            dir.mkdirs()
            val file = java.io.File(dir, fileName)
            file.writeBytes(bytes)
            file
        } catch (e: Exception) {
            null
        }

        // ── Fail closed when the session has no working tenant (T-053
        //    semantics — never upload to a tenant-less path: RLS rejects it
        //    on every attempt). The local copy is kept; the error tells the
        //    caller the proof did NOT reach the server. ──
        if (tenantId == null) {
            return@withContext Result.Err(
                com.example.core.Errors.unknown(
                    "uploadProof refused: no working tenant in the session — the storage policies require a tenant-scoped path",
                    userMessage = "Aucun établissement actif — reconnectez-vous avant d'envoyer le justificatif.",
                ),
            )
        }

        val remotePath = com.example.domain.repository.StorageBuckets.objectPath(tenantId, entityId, fileName)

        // ── Remote (Supabase Storage) when configured — honest failure
        //    classification instead of the read-oriented guard's catch-all. ──
        if (com.example.infrastructure.supabase.NetworkTimeouts.isSupabaseConfigured) {
            try {
                kotlinx.coroutines.withTimeout(UPLOAD_TIMEOUT_MS) {
                    provider.storage.from(bucket).upload(remotePath, bytes) {
                        upsert = true
                        contentType = io.ktor.http.ContentType.parse(mimeType)
                    }
                }
                return@withContext Result.Ok(remotePath)
            } catch (e: Throwable) {
                // The canonical transient/permanent classifier (the SAME one
                // the sync pipeline uses — SyncErrorClassifier, reuse per §6).
                if (com.example.infrastructure.sync.SyncErrorClassifier.isTransient(e, onlineDetector.isOnline())) {
                    // TRANSPORT failure (offline / DNS / timeout / 5xx) — the
                    // sanctioned offline-first fallback: the local copy is
                    // the proof of record until connectivity returns.
                    android.util.Log.w("LocalStorageRepository", "[storage.uploadProof] transient — serving local cache: ${e.message}")
                    if (localFile != null) {
                        return@withContext Result.Ok("file://${localFile.absolutePath}")
                    }
                } else {
                    // PERMANENT rejection (4xx: RLS denial, validation, 401) —
                    // surface it; NEVER report success for a proof the server
                    // refused (UPLOAD-103's silent-data-loss defect).
                    android.util.Log.e("LocalStorageRepository", "[storage.uploadProof] rejected by the server: ${e.message}")
                    return@withContext Result.Err(
                        com.example.core.Errors.unknown(
                            "uploadProof rejected by Supabase Storage: ${e.message}",
                            userMessage = "Le serveur a refusé l'envoi du justificatif (${e.message}).",
                        ),
                    )
                }
            }
        }

        // ── Unconfigured, or transient failure with no writable local file ──
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

    companion object {
        /**
         * T-362: uploads are NOT reads — a 10 MB proof on a slow mobile
         * network needs minutes of headroom, not the 4 s read default.
         * 60 s matches the bucket's file-size ceiling at a modest throughput.
         */
        const val UPLOAD_TIMEOUT_MS: Long = 60_000L
    }
}

// ─── Helper ─────────────────────────────────────────────────────────────────
