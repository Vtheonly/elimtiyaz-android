package com.example.infrastructure.room

import com.example.core.PaymentCategory
import com.example.core.PaymentMethod
import com.example.core.PaymentStatus
import com.example.domain.model.Parent
import com.example.domain.model.Payment
import com.example.domain.model.Student
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Round-trip tests for the [Parent] ↔ [ParentCacheEntity] mapper.
 *
 * The cache layer must preserve every domain field exactly — any data
 * lost in the round trip would silently degrade the offline experience
 * (cache-then-network would show fewer fields than the live network
 * response).
 */
class CacheMappersTest {

    @Test
    fun `parent round-trips through cache entity without loss`() {
        val original = Parent(
            id = "par-001",
            tenantId = "tenant-xyz",
            code = "PAR-2025-A4F9",
            firstName = "Karim",
            lastName = "Benali",
            phone = "+213 550 12 34 56",
            whatsapp = "+213 550 12 34 56",
            email = "k.benali@example.dz",
            occupation = "Engineer",
            address = "12 Rue des Martyrs, Boumerdès",
            transportDestination = "ville_boumerdes",
            preferredLanguage = "fr",
            avatarUrl = "https://example.com/avatar.jpg",
            createdAt = "2025-09-01T08:00:00Z",
            updatedAt = "2025-09-01T08:00:00Z",
        )

        val restored = original.toCacheEntity().toDomain()

        assertEquals(original.id, restored.id)
        assertEquals(original.tenantId, restored.tenantId)
        assertEquals(original.code, restored.code)
        assertEquals(original.firstName, restored.firstName)
        assertEquals(original.lastName, restored.lastName)
        assertEquals(original.phone, restored.phone)
        assertEquals(original.whatsapp, restored.whatsapp)
        assertEquals(original.email, restored.email)
        assertEquals(original.occupation, restored.occupation)
        assertEquals(original.address, restored.address)
        assertEquals(original.transportDestination, restored.transportDestination)
        assertEquals(original.preferredLanguage, restored.preferredLanguage)
        assertEquals(original.avatarUrl, restored.avatarUrl)
        assertEquals(original.createdAt, restored.createdAt)
        assertEquals(original.updatedAt, restored.updatedAt)
        // Computed field
        assertEquals(original.fullName, restored.fullName)
    }

    @Test
    fun `parent with null optional fields round-trips`() {
        val original = Parent(
            id = "par-002",
            tenantId = "tenant-xyz",
            code = "PAR-2025-B1C2",
            firstName = "Sarra",
            lastName = "Khelifi",
            phone = "0550123456",
            whatsapp = null,
            email = null,
            occupation = null,
            address = null,
            transportDestination = null,
            preferredLanguage = "fr",
            avatarUrl = null,
            createdAt = "2025-09-01T08:00:00Z",
            updatedAt = "2025-09-01T08:00:00Z",
        )

        val restored = original.toCacheEntity().toDomain()

        assertNull(restored.whatsapp)
        assertNull(restored.email)
        assertNull(restored.occupation)
        assertNull(restored.address)
        assertNull(restored.transportDestination)
        assertNull(restored.avatarUrl)
    }

    @Test
    fun `student round-trips through cache entity without loss`() {
        val original = Student(
            id = "stu-001",
            tenantId = "tenant-xyz",
            code = "ELV-2025-001234",
            parentId = "par-001",
            firstName = "Amine",
            lastName = "Benali",
            gender = "male",
            birthDate = "2015-03-15",
            enrollmentDate = "2021-09-01",
            level = "primaire",
            gradeLevel = "2ap",
            classId = "cls-cp-a",
            photoUrl = "https://example.com/photo.jpg",
            medicalNotes = "Asthme — inhalateur en infirmerie",
            status = "active",
            createdAt = "2021-09-01T08:00:00Z",
            updatedAt = "2025-09-01T08:00:00Z",
        )

        val restored = original.toCacheEntity().toDomain()

        assertEquals(original.id, restored.id)
        assertEquals(original.parentId, restored.parentId)
        assertEquals(original.code, restored.code)
        assertEquals(original.fullName, restored.fullName)
        assertEquals(original.gradeLevel, restored.gradeLevel)
        assertEquals(original.classId, restored.classId)
        assertEquals(original.medicalNotes, restored.medicalNotes)
        assertEquals(original.status, restored.status)
    }

    @Test
    fun `payment round-trips with enum preservation`() {
        val original = Payment(
            id = "pay-001",
            tenantId = "tenant-xyz",
            receiptNumber = "REC-2025-000123",
            parentId = "par-001",
            studentId = "stu-001",
            amount = 1_245_000_00L, // 12 450,00 DZD
            method = PaymentMethod.TRANSFER,
            status = PaymentStatus.PENDING,
            category = PaymentCategory.TUITION,
            installmentId = "inst-001",
            proofUrl = "https://example.com/proof.webp",
            notes = "Virement BNA #883921",
            collectedBy = "usr-001",
            collectedAt = "2025-09-01T10:15:30Z",
            createdAt = "2025-09-01T10:15:30Z",
            updatedAt = "2025-09-01T10:15:30Z",
        )

        val restored = original.toCacheEntity().toDomain()

        assertEquals(original.id, restored.id)
        assertEquals(original.amount, restored.amount)
        assertEquals(original.method, restored.method)
        assertEquals(original.status, restored.status)
        assertEquals(original.category, restored.category)
        assertEquals(original.installmentId, restored.installmentId)
        assertEquals(original.proofUrl, restored.proofUrl)
        assertEquals(original.notes, restored.notes)
    }

    @Test
    fun `payment with unknown enum string falls back to safe defaults`() {
        // Simulate a cache row from an older app version that stored a
        // method string the current enum doesn't recognize.
        val legacy = PaymentCacheEntity(
            id = "pay-legacy",
            tenantId = "t",
            receiptNumber = "REC-OLD",
            parentId = "p",
            studentId = null,
            amount = 100L,
            method = "mobile_money", // not in PaymentMethod enum
            status = "unknown_status",
            category = "registration", // not in PaymentCategory enum
            installmentId = null,
            proofUrl = null,
            notes = null,
            collectedBy = "u",
            collectedAt = "2020-01-01",
            createdAt = "2020-01-01",
            updatedAt = "2020-01-01",
            syncedAt = 0L,
        )

        val restored = legacy.toDomain()

        // Defaults prevent crashes — the lossy fields become safe values.
        assertEquals(PaymentMethod.CASH, restored.method)
        assertEquals(PaymentStatus.PENDING, restored.status)
        assertEquals(PaymentCategory.OTHER, restored.category)
        // Non-enum fields are preserved exactly.
        assertEquals("pay-legacy", restored.id)
        assertEquals(100L, restored.amount)
        assertEquals("REC-OLD", restored.receiptNumber)
    }
}
