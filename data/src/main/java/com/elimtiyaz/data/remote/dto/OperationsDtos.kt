package com.elimtiyaz.data.remote.dto

import com.elimtiyaz.domain.model.AppNotification
import com.elimtiyaz.domain.model.AuditEntry
import com.elimtiyaz.domain.model.NotificationType
import com.elimtiyaz.domain.model.Personnel
import com.elimtiyaz.domain.model.PersonnelStatus
import com.elimtiyaz.domain.model.ReleveEntry
import com.elimtiyaz.domain.model.StaffCategory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire DTO for the `personnel` table. */
@Serializable
data class PersonnelDto(
    val id: String,
    @SerialName("tenant_id") val tenantId: String,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    @SerialName("staff_category") val staffCategory: StaffCategory,
    val phone: String,
    val email: String? = null,
    @SerialName("hire_date") val hireDate: String,
    val salary: Double? = null,
    @SerialName("weekly_hours_target") val weeklyHoursTarget: Int = 0,
    @SerialName("weekly_hours_logged") val weeklyHoursLogged: Int = 0,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val status: PersonnelStatus = PersonnelStatus.Active,
) {
    /** Convert to a domain [Personnel]. */
    fun toDomain(): Personnel = Personnel(
        id = id, tenantId = tenantId, firstName = firstName, lastName = lastName,
        staffCategory = staffCategory, phone = phone, email = email, hireDate = hireDate, salary = salary,
        weeklyHoursTarget = weeklyHoursTarget, weeklyHoursLogged = weeklyHoursLogged, avatarUrl = avatarUrl, status = status,
    )

    companion object {
        /** Build a DTO from a domain [Personnel]. */
        fun fromDomain(p: Personnel): PersonnelDto = PersonnelDto(
            id = p.id, tenantId = p.tenantId, firstName = p.firstName, lastName = p.lastName,
            staffCategory = p.staffCategory, phone = p.phone, email = p.email, hireDate = p.hireDate, salary = p.salary,
            weeklyHoursTarget = p.weeklyHoursTarget, weeklyHoursLogged = p.weeklyHoursLogged, avatarUrl = p.avatarUrl, status = p.status,
        )
    }
}

/** Wire DTO for the `releve_entries` table (teacher activity ledger). */
@Serializable
data class ReleveEntryDto(
    val id: String,
    @SerialName("personnel_id") val personnelId: String,
    @SerialName("personnel_name") val personnelName: String,
    val date: String,
    @SerialName("hours_in") val hoursIn: Double,
    @SerialName("hours_out") val hoursOut: Double? = null,
    val activity: String,
    @SerialName("class_id") val classId: String? = null,
    @SerialName("subject_id") val subjectId: String? = null,
    @SerialName("recorded_at") val recordedAt: String,
) {
    /** Convert to a domain [ReleveEntry]. */
    fun toDomain(): ReleveEntry = ReleveEntry(
        id = id, personnelId = personnelId, personnelName = personnelName, date = date,
        hoursIn = hoursIn, hoursOut = hoursOut, activity = activity, classId = classId,
        subjectId = subjectId, recordedAt = recordedAt,
    )

    companion object {
        /** Build a DTO from a domain [ReleveEntry]. */
        fun fromDomain(r: ReleveEntry): ReleveEntryDto = ReleveEntryDto(
            id = r.id, personnelId = r.personnelId, personnelName = r.personnelName, date = r.date,
            hoursIn = r.hoursIn, hoursOut = r.hoursOut, activity = r.activity, classId = r.classId,
            subjectId = r.subjectId, recordedAt = r.recordedAt,
        )
    }
}

/** Wire DTO for the `audit_log` table — written via Edge Function per §12.02. */
@Serializable
data class AuditEntryDto(
    val id: String,
    @SerialName("tenant_id") val tenantId: String,
    val action: String,
    @SerialName("entity_type") val entityType: String,
    @SerialName("entity_id") val entityId: String,
    @SerialName("actor_id") val actorId: String,
    @SerialName("actor_name") val actorName: String,
    val diff: String? = null,
    val note: String? = null,
    @SerialName("ip_address") val ipAddress: String? = null,
    @SerialName("user_agent") val userAgent: String? = null,
    val at: String,
) {
    /** Convert to a domain [AuditEntry]. */
    fun toDomain(): AuditEntry = AuditEntry(
        id = id, tenantId = tenantId, action = action, entityType = entityType, entityId = entityId,
        actorId = actorId, actorName = actorName, diff = diff, note = note, ipAddress = ipAddress,
        userAgent = userAgent, at = at,
    )

    companion object {
        /** Build a DTO from a domain [AuditEntry]. */
        fun fromDomain(a: AuditEntry): AuditEntryDto = AuditEntryDto(
            id = a.id, tenantId = a.tenantId, action = a.action, entityType = a.entityType, entityId = a.entityId,
            actorId = a.actorId, actorName = a.actorName, diff = a.diff, note = a.note, ipAddress = a.ipAddress,
            userAgent = a.userAgent, at = a.at,
        )
    }
}

/** Wire DTO for the `notifications` table. */
@Serializable
data class AppNotificationDto(
    val id: String,
    val title: String,
    val body: String,
    val type: NotificationType,
    @SerialName("entity_type") val entityType: String? = null,
    @SerialName("entity_id") val entityId: String? = null,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String,
) {
    /** Convert to a domain [AppNotification]. */
    fun toDomain(): AppNotification = AppNotification(
        id = id, title = title, body = body, type = type, entityType = entityType, entityId = entityId,
        readAt = readAt, createdAt = createdAt,
    )

    companion object {
        /** Build a DTO from a domain [AppNotification]. */
        fun fromDomain(n: AppNotification): AppNotificationDto = AppNotificationDto(
            id = n.id, title = n.title, body = n.body, type = n.type, entityType = n.entityType,
            entityId = n.entityId, readAt = n.readAt, createdAt = n.createdAt,
        )
    }
}
