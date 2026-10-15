package com.example.infrastructure.supabase

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression test for the tranche dashboard's empty state when an installment
 * batch contains a valid row whose optional student_id is null.
 *
 * Supabase decodes the entire pull response as List<InstallmentDto>; a single
 * non-nullable DTO field previously threw during decoding and discarded every
 * installment in that batch, leaving the dashboard with no tranche rows.
 */
class InstallmentDtoNullStudentTest {

    @Test
    fun `a null student id does not abort the complete installment batch`() {
        val rows = Json { ignoreUnknownKeys = true }
            .decodeFromString<List<InstallmentDto>>(
                """
                [
                  {
                    "id": "installment-with-student",
                    "parent_id": "parent-1",
                    "student_id": "student-1",
                    "tranche_number": 1,
                    "amount_due": 10000.0,
                    "amount_paid": 0.0,
                    "amount_pending": 0.0,
                    "due_date": "2026-09-15",
                    "status": "unpaid",
                    "category": "tuition"
                  },
                  {
                    "id": "family-level-installment",
                    "parent_id": "parent-2",
                    "student_id": null,
                    "tranche_number": 0,
                    "amount_due": 5000.0,
                    "amount_paid": 0.0,
                    "amount_pending": 0.0,
                    "due_date": "2026-09-15",
                    "status": "unpaid",
                    "category": "tuition"
                  }
                ]
                """.trimIndent(),
            )

        assertEquals("both server rows must decode", 2, rows.size)
        assertNull("the source DTO must preserve the absent student", rows[1].studentId)

        val entities = rows.map { it.toEntity() }
        assertEquals("both installments must reach the Room mapper", 2, entities.size)
        assertNull("the Room entity must preserve the absent student", entities[1].studentId)
    }
}
