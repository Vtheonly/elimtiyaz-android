package com.example.infrastructure.supabase

import com.example.core.AuditActions
import com.example.core.Result
import com.example.domain.model.ReleveActivity
import com.example.domain.model.ReleveEntry
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import com.example.domain.repository.ReleveRepository
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase implementation of ReleveRepository.
 *
 * Table: `releve_entries` (migration 0010). `duration_minutes` is computed
 * server-side as a GENERATED column (`extract(epoch from clock_out_at - clock_in_at) / 60`).
 *
 * Trigger `prevent_self_releve_entry` (BEFORE INSERT/UPDATE) forbids a
 * teacher from recording their own Relevé entry (plan §09.05).
 *
 * Audit action: `RELEVE_CREATE`.
 */
@Singleton
class SupabaseReleveRepository @Inject constructor(
    private val provider: SupabaseClientProvider,
    private val auditRepository: AuditRepository,
) : ReleveRepository {

    override fun observeByPersonnel(personnelId: String, fromIso: String, toIso: String) = flow<Result<List<ReleveEntry>>> {
        emit(try {
            Result.Ok(
                provider.postgrest.from("releve_entries")
                    .select {
                        filter {
                            eq("personnel_id", personnelId)
                            gte("date", fromIso)
                            lte("date", toIso)
                        }
                        order("recorded_at", Order.DESCENDING)
                        limit(500)
                    }
                    .decodeList<ReleveEntryDto>()
                    .map { it.toDomain() }
            )
        } catch (e: Exception) {
            Result.Ok(emptyList())
        })
    }

    override suspend fun logEntry(entry: ReleveEntry, actorId: String, actorName: String): Result<ReleveEntry> = try {
        require(entry.hoursIn.isNotBlank()) { "hours_in is required" }
        require(entry.hoursOut?.let { out -> out > entry.hoursIn } ?: true) { "hours_out must be > hours_in" }
        val dto = ReleveEntryInsertDto(
            id = entry.id.ifBlank { UUID.randomUUID().toString() },
            personnelId = entry.personnelId,
            personnelName = entry.personnelName,
            date = entry.date,
            hoursIn = entry.hoursIn,
            hoursOut = entry.hoursOut,
            activity = entry.activity.wireCode,
            classId = entry.classId,
            subjectId = entry.subjectId,
            taskId = entry.taskId,
            recordedBy = actorId,
            recordedAt = nowIso(),
        )
        val inserted = provider.postgrest.from("releve_entries").insert(dto) { select() }
            .decodeList<ReleveEntryDto>().first().toDomain()
        auditRepository.log(AuditLogInput(
            action = AuditActions.RELEVE_CREATE,
            entityType = "releve_entry",
            entityId = inserted.id,
            afterJson = """{"personnel":"${entry.personnelName}","activity":"${entry.activity.wireCode}","date":"${entry.date}"}""",
            note = "Relevé recorded by $actorName",
        ))
        Result.Ok(inserted)
    } catch (e: Exception) {
        com.example.core.Errors.fromException(e)
    }

    private fun nowIso(): String = java.time.Instant.now().toString()

    @Serializable
    data class ReleveEntryDto(
        val id: String,
        val personnelId: String,
        val personnelName: String,
        val date: String,
        val hoursIn: String,
        val hoursOut: String? = null,
        val activity: String,
        val classId: String? = null,
        val subjectId: String? = null,
        val taskId: String? = null,
        val recordedBy: String,
        val recordedAt: String,
        val durationMinutes: Long? = null,
    ) {
        fun toDomain() = ReleveEntry(
            id = id, personnelId = personnelId, personnelName = personnelName, date = date,
            hoursIn = hoursIn, hoursOut = hoursOut,
            activity = ReleveActivity.fromCode(activity),
            classId = classId, subjectId = subjectId, taskId = taskId,
            recordedBy = recordedBy, recordedAt = recordedAt,
            durationMinutes = durationMinutes,
        )
    }

    @Serializable
    data class ReleveEntryInsertDto(
        val id: String,
        val personnelId: String,
        val personnelName: String,
        val date: String,
        val hoursIn: String,
        val hoursOut: String?,
        val activity: String,
        val classId: String?,
        val subjectId: String?,
        val taskId: String?,
        val recordedBy: String,
        val recordedAt: String,
    )
}
