package com.example.infrastructure.supabase

import com.example.core.AuditActions
import com.example.core.Errors
import com.example.core.Result
import com.example.domain.model.AttendanceRecord
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import com.example.domain.repository.AttendanceRepository
import com.example.domain.repository.RollCallEntry
import com.example.infrastructure.sync.SyncSupport
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.request.setBody
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase implementation of AttendanceRepository.
 *
 * Table: `attendance_records` (migration 0004). Unique index on
 * `(tenant_id, student_id, class_id, date, coalesce(class_subject_id, '00..'))`
 * enables idempotent bulk upsert via [PostgrestQueryBuilder.upsert].
 *
 * The `session` field on the domain model is mobile-only — DB uses
 * `class_subject_id` (nullable for homereoom roll call). We map session
 * → class_subject_id is null = homeroom (whole-day); other sessions would
 * require a class_subject_id to be passed.
 *
 * `alertAbsences` calls the `alert-absences` Edge Function with the
 * `{student_ids: [...]}` payload. The Edge Function dispatches FCM/Web
 * notifications to parents of absent students.
 *
 * Audit action: `AuditActions.ATTENDANCE_RECORD` (`attendance.roll_call`).
 */
@Singleton
class SupabaseAttendanceRepository @Inject constructor(
    private val provider: SupabaseClientProvider,
    private val auditRepository: AuditRepository,
    private val syncSupport: SyncSupport,
) : AttendanceRepository {

    override fun observeByClass(classId: String, date: String) = flow {
        emit(try {
            provider.postgrest.from("attendance_records")
                .select {
                    filter {
                        eq("class_id", classId)
                        eq("date", date)
                    }
                    order("student_id", Order.ASCENDING)
                    limit(200)
                }
                .decodeList<AttendanceRecordDto>()
                .map { it.toDomain() }
        } catch (e: Exception) { emptyList() })
    }

    override fun observeByStudent(studentId: String) = flow {
        emit(try {
            provider.postgrest.from("attendance_records")
                .select {
                    filter { eq("student_id", studentId) }
                    order("date", Order.DESCENDING)
                    limit(200)
                }
                .decodeList<AttendanceRecordDto>()
                .map { it.toDomain() }
        } catch (e: Exception) { emptyList() })
    }

    override suspend fun recordRollCall(
        classId: String, date: String, session: String,
        records: List<RollCallEntry>,
        actorId: String, actorName: String,
    ): Result<Unit> {
        require(records.isNotEmpty()) { "Records list cannot be empty" }
        val dtos = records.map { entry ->
            AttendanceRecordUpsertDto(
                studentId = entry.studentId,
                classId = classId,
                date = date,
                status = entry.status,
                note = entry.note,
                recordedBy = actorId.takeIf { it.isNotBlank() },
            )
        }
        // Try direct bulk upsert; on offline, enqueue as attendance/record_roll_call
        // for [SyncWorker] to drain later. Teachers do roll call in classrooms
        // with poor signal — offline records MUST survive. The payload captures
        // the full batch (class_id, date, session, records) so the drain-side
        // replay can re-issue the bulk upsert.
        //
        // NOTE: the current [SupabaseSyncDao.pushAttendance] does a single-row
        // upsert and will not perfectly replay a batch payload — drain failures
        // are surfaced via the audit failure log after [SyncService.maxAttempts]
        // retries. Enhancing pushAttendance to handle batch payloads is a
        // follow-up task (out of scope for this migration).
        return syncSupport.tryThenEnqueue(
            entity = "attendance",
            operation = "record_roll_call",
            payload = {
                syncSupport.json().encodeToString(
                    RollCallPayload.serializer(),
                    RollCallPayload(classId, date, session, dtos),
                )
            },
            sourceScreen = "RollCall",
        ) {
            // Bulk upsert — relies on the unique index on (tenant_id, student_id, class_id, date, class_subject_id)
            // The server derives tenant_id from the JWT (RLS).
            provider.postgrest.from("attendance_records").upsert(dtos)

            auditRepository.log(AuditLogInput(
                action = AuditActions.ATTENDANCE_RECORD,
                entityType = "class",
                entityId = classId,
                afterJson = """{"date":"$date","session":"$session","record_count":${records.size}}""",
                note = "Roll call recorded from Android app",
            ))
            Unit
        }
    }

    override suspend fun alertAbsences(studentIds: List<String>, actorId: String, actorName: String): Result<Unit> = try {
        require(studentIds.isNotEmpty()) { "Student IDs list cannot be empty" }
        val params = buildJsonObject {
            put("student_ids", JsonArray(studentIds.map { kotlinx.serialization.json.JsonPrimitive(it) }))
        }
        provider.functions.invoke("alert-absences") {
            setBody(params)
        }
        auditRepository.log(AuditLogInput(
            action = AuditActions.ATTENDANCE_ALERT,
            entityType = "student",
            entityId = studentIds.first(),
            afterJson = """{"student_count":${studentIds.size}}""",
            note = "Absence alert dispatched from Android app",
        ))
        Result.Ok(Unit)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    @Serializable
    data class AttendanceRecordDto(
        val id: String,
        val tenantId: String,
        val studentId: String,
        val classId: String,
        val classSubjectId: String? = null,
        val date: String,
        val status: String,
        val arrivalTime: String? = null,
        val note: String? = null,
        val recordedBy: String? = null,
        val createdAt: String,
        val updatedAt: String,
    ) {
        fun toDomain() = AttendanceRecord(
            id = id,
            tenantId = tenantId,
            studentId = studentId,
            classId = classId,
            date = date,
            session = if (classSubjectId == null) "both" else "subject",
            status = status,
            note = note,
            recordedBy = recordedBy ?: "unknown",
            recordedAt = updatedAt,
            syncedAt = createdAt,
        )
    }

    @Serializable
    data class AttendanceRecordUpsertDto(
        val studentId: String,
        val classId: String,
        val date: String,
        val status: String,
        val note: String? = null,
        val recordedBy: String? = null,
    )

    /**
     * Payload captured when a `recordRollCall` batch is enqueued offline.
     * Wraps the contextual fields (classId/date/session) plus the full list of
     * [AttendanceRecordUpsertDto] entries so the drain-side replay can re-issue
     * the bulk upsert verbatim.
     */
    @Serializable
    data class RollCallPayload(
        val classId: String,
        val date: String,
        val session: String,
        val records: List<AttendanceRecordUpsertDto>,
    )
}
