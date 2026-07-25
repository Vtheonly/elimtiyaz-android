package com.elimtiyaz.data.remote.dto

import com.elimtiyaz.domain.model.AcademicHistoryEntry
import com.elimtiyaz.domain.model.CreateStudentInput
import com.elimtiyaz.domain.model.Gender
import com.elimtiyaz.domain.model.PromotionDecision
import com.elimtiyaz.domain.model.Student
import com.elimtiyaz.domain.model.StudentStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire DTO for the `students` Supabase table. */
@Serializable
data class StudentDto(
    val id: String,
    @SerialName("tenant_id") val tenantId: String,
    val code: String,
    @SerialName("parent_id") val parentId: String,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    val gender: Gender = Gender.Unspecified,
    @SerialName("birth_date") val birthDate: String,
    @SerialName("enrollment_date") val enrollmentDate: String,
    val level: String,
    @SerialName("grade_year") val gradeYear: Int,
    @SerialName("class_id") val classId: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("medical_notes") val medicalNotes: String? = null,
    @SerialName("transport_tier") val transportTier: String? = null,
    val status: StudentStatus = StudentStatus.Active,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
) {
    /** Map to a domain [Student] (parent + history are joined at the repository layer). */
    fun toDomain(parent: com.elimtiyaz.domain.model.Parent? = null, history: List<AcademicHistoryEntry> = emptyList()): Student =
        Student(
            id = id,
            tenantId = tenantId,
            code = code,
            parentId = parentId,
            firstName = firstName,
            lastName = lastName,
            gender = gender,
            birthDate = birthDate,
            enrollmentDate = enrollmentDate,
            level = level,
            gradeYear = gradeYear,
            classId = classId,
            photoUrl = photoUrl,
            medicalNotes = medicalNotes,
            transportTier = transportTier,
            status = status,
            createdAt = createdAt,
            updatedAt = updatedAt,
            parent = parent,
            academicHistory = history,
        )

    companion object {
        /** Build a DTO from a domain [Student]. */
        fun fromDomain(s: Student): StudentDto = StudentDto(
            id = s.id,
            tenantId = s.tenantId,
            code = s.code,
            parentId = s.parentId,
            firstName = s.firstName,
            lastName = s.lastName,
            gender = s.gender,
            birthDate = s.birthDate,
            enrollmentDate = s.enrollmentDate,
            level = s.level,
            gradeYear = s.gradeYear,
            classId = s.classId,
            photoUrl = s.photoUrl,
            medicalNotes = s.medicalNotes,
            transportTier = s.transportTier,
            status = s.status,
            createdAt = s.createdAt,
            updatedAt = s.updatedAt,
        )

        /** Build a DTO from a [CreateStudentInput] using the supplied identifiers. */
        fun fromCreate(
            input: CreateStudentInput,
            id: String,
            tenantId: String,
            code: String,
            nowIso: String,
        ): StudentDto = StudentDto(
            id = id,
            tenantId = tenantId,
            code = code,
            parentId = input.parentId,
            firstName = input.firstName,
            lastName = input.lastName,
            gender = input.gender,
            birthDate = input.birthDate,
            enrollmentDate = nowIso,
            level = input.level,
            gradeYear = input.gradeYear,
            classId = input.classId,
            medicalNotes = input.medicalNotes,
            transportTier = input.transportTier,
            status = StudentStatus.Active,
            createdAt = nowIso,
            updatedAt = nowIso,
        )
    }
}

/** Wire DTO for a `student_academic_history` row. */
@Serializable
data class AcademicHistoryDto(
    val id: String,
    @SerialName("student_id") val studentId: String,
    @SerialName("academic_year") val academicYear: String,
    val level: String,
    @SerialName("grade_year") val gradeYear: Int,
    @SerialName("class_id") val classId: String? = null,
    @SerialName("class_name") val className: String? = null,
    val gpa: Double,
    val rank: Int? = null,
    val decision: PromotionDecision,
    val narrative: String? = null,
) {
    /** Convert to a domain [AcademicHistoryEntry]. */
    fun toDomain(): AcademicHistoryEntry = AcademicHistoryEntry(
        academicYear = academicYear,
        level = level,
        gradeYear = gradeYear,
        classId = classId,
        className = className,
        gpa = gpa,
        rank = rank,
        decision = decision,
        narrative = narrative,
    )
}
