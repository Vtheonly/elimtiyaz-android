package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * The kind of activity a staff member records in their Relevé (clock-in/out ledger).
 *
 * Wire-protocol: lowercase snake_case, matching the desktop `ReleveActivity` enum.
 *
 * Note: matches desktop codes exactly (8 values, no 9th):
 * `course` / `meeting` / `supervision` / `correction` / `task` / `delivery` / `warehouse` / `other`.
 */
@Serializable
enum class ReleveActivity(val wireCode: String, val displayFr: String, val displayAr: String) {
    Course("course", "Cours", "حصة"),
    Meeting("meeting", "Réunion", "اجتماع"),
    Supervision("supervision", "Surveillance", "مراقبة"),
    Correction("correction", "Correction", "تصحيح"),
    Task("task", "Tâche", "مهمة"),
    Delivery("delivery", "Livraison", "تسليم"),
    Warehouse("warehouse", "Magasin", "مخزن"),
    Other("other", "Autre", "أخرى");

    companion object {
        fun fromCode(code: String?): ReleveActivity =
            entries.firstOrNull { it.wireCode.equals(code, ignoreCase = true) } ?: Other
    }
}

/**
 * A single Relevé entry — one row in a staff member's clock-in/out ledger.
 *
 * Per desktop plan §09.05: append-only. A teacher CANNOT record their own Relevé entry
 * (server trigger `prevent_self_releve_entry` enforces this).
 *
 * @param hoursIn Clock-in time as a string (ISO time or `HH:mm` — flexible for v1).
 * @param hoursOut Clock-out time, nullable for in-progress entries.
 * @param durationMinutes Computed server-side as `(hoursOut - hoursIn)` in minutes.
 */
@Serializable
data class ReleveEntry(
    val id: String,
    val personnelId: String,
    val personnelName: String,
    val date: String, // ISO yyyy-MM-dd
    val hoursIn: String,
    val hoursOut: String?,
    val activity: ReleveActivity = ReleveActivity.Course,
    val classId: String? = null,
    val subjectId: String? = null,
    val taskId: String? = null,
    val recordedBy: String,
    val recordedAt: String,
    val durationMinutes: Long? = null,
)

/**
 * Aggregate Relevé compliance for a staff member over a date range.
 *
 * Used by PersonnelDetailScreen to render the weekly hours progress bar.
 *
 * @param totalMinutesLogged Sum of all `durationMinutes` in the range.
 * @param weeklyTargetMinutes The staff member's `weeklyHoursTarget × 60` (0 if not configured).
 */
data class ReleveForPersonnel(
    val personnelId: String,
    val entries: List<ReleveEntry>,
    val totalMinutesLogged: Long,
    val weeklyTargetMinutes: Long,
) {
    val compliancePct: Double
        get() = if (weeklyTargetMinutes <= 0) 0.0
        else (totalMinutesLogged.toDouble() / weeklyTargetMinutes.toDouble() * 100.0).coerceIn(0.0, 100.0)
}
