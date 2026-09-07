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

// ─── Workflow Repository ─────────────────────────────────────────────────────

@Singleton
class LocalWorkflowRepository @Inject constructor(
    private val workflowRunDao: WorkflowRunDao,
    private val provider: com.example.infrastructure.supabase.SupabaseClientProvider,
) : WorkflowRepository {

    private fun WorkflowRunEntity.toDomain(): com.example.domain.model.WorkflowRun {
        // T-231: resultJson carries the SERIALIZED node_results array (the
        // EF's per-node outcomes) — decode into the domain model.
        val nodeResults: List<com.example.domain.model.WorkflowNodeResult> =
            resultJson?.takeIf { it.isNotBlank() }?.let { raw ->
                runCatching {
                    kotlinx.serialization.json.Json.decodeFromString(
                        kotlinx.serialization.builtins.ListSerializer(
                            com.example.infrastructure.supabase.WorkflowNodeResultDto.serializer(),
                        ),
                        raw,
                    )
                }.getOrNull()
            }?.map { dto ->
                com.example.domain.model.WorkflowNodeResult(
                    nodeId = dto.nodeId,
                    nodeName = dto.nodeLabel ?: dto.nodeId,
                    nodeType = dto.nodeType ?: "action",
                    status = com.example.domain.model.WorkflowNodeStatus.fromCode(dto.status),
                    startedAt = dto.startedAt,
                    completedAt = dto.completedAt,
                    output = dto.output?.toString()?.take(200),
                    error = dto.error,
                )
            } ?: emptyList()
        return com.example.domain.model.WorkflowRun(
            id = id, workflowId = workflowId, workflowName = workflowName,
            // T-054 (WEAK-008): the REAL trigger from the entity column — the
            // old hardcode made every run display "Manuel".
            trigger = com.example.domain.model.WorkflowTrigger.fromCode(trigger),
            status = com.example.domain.model.WorkflowRunStatus.fromCode(status),
            startedAt = startedAt, completedAt = finishedAt,
            durationMs = runCatching {
                val start = Instant.parse(startedAt)
                val end = finishedAt?.let { Instant.parse(it) }
                end?.let { it.toEpochMilli() - start.toEpochMilli() }
            }.getOrNull(),
            actorId = startedBy, actorName = null,
            errorMessage = errorMessage,
            outputPreview = nodeResults.firstOrNull { it.output != null }?.output?.take(120),
            nodeResults = nodeResults,
        )
    }

    override fun observeRuns(limit: Int): Flow<Result<List<com.example.domain.model.WorkflowRun>>> =
        workflowRunDao.observeRecent().map { rows ->
            Result.Ok(rows.map { it.toDomain() })
        }

    override fun observeRunById(runId: String): Flow<Result<com.example.domain.model.WorkflowRun?>> =
        workflowRunDao.observeRecent().map { rows ->
            Result.Ok(rows.firstOrNull { it.id == runId }?.toDomain())
        }

    // FIX (success theater): retryRun previously inserted a fabricated run with
    // status="completed" the instant the button was pressed — no workflow ever
    // executed. Now the retry is honest:
    //   1. A new run row is created with status="running" (visible in monitor).
    //   2. If Supabase is configured, the server-side `workflow-execute` Edge
    //      Function is invoked (the workflow engine is server-only per plan
    //      §10.02) — the run stays "running" until the next pull finalizes it.
    //   3. Offline / unreachable → the run is finalized locally as "failed"
    //      with a truthful error and Result.Err is returned. No fake success.
    override suspend fun retryRun(runId: String, actorId: String, actorName: String): Result<String> {
        val original = workflowRunDao.getById(runId)
            ?: return Result.Err(Errors.notFound("Exécution $runId introuvable"))

        val newId = "wfr-${UUID.randomUUID()}"
        val now = Instant.now().toString()
        val newRun = WorkflowRunEntity(
            id = newId, tenantId = original.tenantId,
            workflowId = original.workflowId, workflowName = original.workflowName,
            // A user-initiated retry IS a manual run (matches the desktop
            // semantics for retried runs).
            trigger = "manual",
            status = "running", startedBy = actorId, startedAt = now,
            finishedAt = null, resultJson = null, errorMessage = null,
        )
        workflowRunDao.upsert(newRun)

        val invoked = com.example.infrastructure.supabase.NetworkTimeouts.guard(
            "workflow.retry", timeoutMs = 8_000L,
        ) {
            provider.functions.invoke(
                function = "workflow-execute",
                body = kotlinx.serialization.json.buildJsonObject {
                    put("workflowId", original.workflowId)
                    put("runId", newId)
                    put("triggeredBy", actorId)
                },
            )
        }

        return if (invoked != null && invoked.status.value in 200..299) {
            Result.Ok(newId)
        } else {
            val message = "Relance impossible : le moteur de workflows est côté serveur et n'est pas joignable."
            workflowRunDao.upsert(
                newRun.copy(status = "failed", finishedAt = Instant.now().toString(), errorMessage = message),
            )
            Result.Err(
                com.example.core.Errors.unknown(
                    "workflow retry failed (server unreachable)",
                    userMessage = message,
                ),
            )
        }
    }
}
