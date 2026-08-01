package com.example.domain.repository

import com.example.core.Result
import com.example.domain.model.AttendanceRecord
import kotlinx.coroutines.flow.Flow

/** Attendance repository contract. */
interface AttendanceRepository {
    fun observeByClass(classId: String, date: String): Flow<List<AttendanceRecord>>
    fun observeByStudent(studentId: String): Flow<List<AttendanceRecord>>
    suspend fun recordRollCall(classId: String, date: String, session: String, records: List<RollCallEntry>, actorId: String, actorName: String): Result<Unit>
    suspend fun alertAbsences(studentIds: List<String>, actorId: String, actorName: String): Result<Unit>
}

/** Single roll-call entry — one student's attendance status for one session. */
data class RollCallEntry(val studentId: String, val status: String, val note: String? = null)
