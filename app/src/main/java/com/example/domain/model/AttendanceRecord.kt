package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Attendance record — one student, one class-session, one date.
 * `status` is one of the 4 canonical wire-status codes.
 */
@Serializable
data class AttendanceRecord(
    val id: String,
    val tenantId: String,
    val studentId: String,
    val classId: String,
    val date: String,
    val session: String,                 // morning | afternoon | both
    val status: String,                  // present | absent_excused | absent_unexcused | late
    val note: String? = null,
    val recordedBy: String,
    val recordedAt: String,
    val syncedAt: String? = null,
)
