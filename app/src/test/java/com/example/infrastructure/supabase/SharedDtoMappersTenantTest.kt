package com.example.infrastructure.supabase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * DTO-TENANT (session 18) — closes the T-051 out-of-scope note:
 * "SharedDtoMappers still defaults null-tenant DTOs to the demo UUID on
 * the PULL side — that is a mapping boundary, not a local write; revisit
 * under ADR-005's pull design."
 *
 * The defect: nine pull-side toEntity() mappers (classes, subjects,
 * installments, departments, personnel, notifications, workflow runs,
 * payments, ledger entries) stamped a NULL server tenant_id with the DEMO
 * tenant UUID literal ("00000000-0000-0000-0000-000000000001") — the same
 * contamination pattern WEAK-012 had on the query side. A defensive null
 * became a silent attribution to the demo tenant.
 *
 * Fix under test: the pull default is the HONEST EMPTY STRING — same
 * convention the newer academic mappers (T-039) already used. Safe because
 * (verified by scan) nothing on the local read path filters by tenantId,
 * and pushes carry the session-stamped tenantId from AuditContext (T-051),
 * never these pull-side values.
 */
class SharedDtoMappersTenantTest {

    private val demoUuid = "00000000-0000-0000-0000-000000000001"

    private fun mappersSrc(): String =
        File("src/main/java/com/example/infrastructure/supabase/SharedDtoMappers.kt").readText()

    private fun daosSrc(): String =
        File("src/main/java/com/example/infrastructure/room/LocalDaos.kt").readText() +
            File("src/main/java/com/example/infrastructure/room/Daos.kt").readText()

    // ── The demo UUID is gone from the mapping boundary ─────────────────────

    @Test
    fun `source scan - no demo tenant literal anywhere in the pull mappers`() {
        assertFalse(
            "SharedDtoMappers must never default (or reference) the demo tenant UUID",
            mappersSrc().contains(demoUuid),
        )
        assertTrue(
            "the tenant convention must be documented at the top of the file",
            mappersSrc().contains("TENANT CONVENTION (DTO-TENANT"),
        )
    }

    @Test
    fun `source scan - no local read path filters by tenantId (empty tenant cannot hide rows)`() {
        // The "" default is only safe while no DAO query filters by tenant.
        // This scan pins that invariant: if a tenant filter is ever added to
        // a local query, the "" default must be revisited (ADR-005 pull
        // design) — this test fails as the tripwire.
        assertFalse(
            "no DAO query may filter by tenantId while pulled rows default to \"\"",
            Regex("WHERE[^;\"]*tenantId\\s*=").containsMatchIn(daosSrc()),
        )
    }

    @Test
    fun `demo tenant remains ONLY in the sanctioned homes (AuditContext + DatabaseSeeder)`() {
        val offenders = File("src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .mapNotNull { f ->
                if (demoUuid in f.readText() && "supabase" !in f.path) f.path else null
            }
            .toList()
        assertEquals(
            "the demo UUID may appear only in AuditContext (signed-out fallback) and " +
                "DatabaseSeeder (demo sandbox); found: $offenders",
            setOf(
                "src/main/java/com/example/infrastructure/local/AuditContext.kt",
                "src/main/java/com/example/infrastructure/room/DatabaseSeeder.kt",
            ),
            offenders.toSet(),
        )
    }

    // ── The nine previously-defaulting mappers now map null → "" ───────────

    @Test
    fun `null-tenant class, subject and installment rows map to the empty tenant`() {
        assertEquals(
            "",
            ClassDto(id = "c1", code = "1AP-A").toEntity().tenantId,
        )
        assertEquals(
            "",
            SubjectDto(id = "s1", code = "MATH", nameFr = "Mathématiques").toEntity().tenantId,
        )
        assertEquals(
            "",
            InstallmentDto(id = "i1", parentId = "p1", studentId = "st1", amountDue = 1000.0, dueDate = "2026-09-15")
                .toEntity().tenantId,
        )
    }

    @Test
    fun `null-tenant department, personnel and notification rows map to the empty tenant`() {
        assertEquals("", DepartmentDto(id = "d1", code = "PED", nameFr = "Pédagogie").toEntity().tenantId)
        assertEquals(
            "",
            PersonnelDto(id = "pe1", personnelCode = "ENS-1", firstName = "A", lastName = "B").toEntity().tenantId,
        )
        assertEquals("", NotificationDto(id = "n1", title = "T").toEntity().tenantId)
    }

    @Test
    fun `null-tenant workflow, payment and ledger rows map to the empty tenant`() {
        assertEquals("", WorkflowRunDto(id = "w1").toEntity().tenantId)
        assertEquals(
            "",
            PaymentDto(id = "pay1", paymentNumber = "R-1", parentId = "p1", amount = 1000.0, method = "cash", status = "paid")
                .toEntity().tenantId,
        )
        assertEquals("", LedgerEntryDto(id = "l1", parentId = "p1", accountId = "a1", category = "tuition", entryType = "charge", amount = 1000.0).toEntity().tenantId)
    }

    // ── Parity: non-null tenant ids pass through untouched ─────────────────

    @Test
    fun `a real tenant id passes through every mapper unchanged`() {
        val tenant = "11111111-2222-3333-4444-555555555555"
        assertEquals(tenant, ClassDto(id = "c1", tenantId = tenant, code = "1AP-A").toEntity().tenantId)
        assertEquals(tenant, PaymentDto(id = "pay1", tenantId = tenant, paymentNumber = "R-1", parentId = "p1", amount = 1000.0, method = "cash", status = "paid").toEntity().tenantId)
        assertEquals(tenant, NotificationDto(id = "n1", tenantId = tenant, title = "T").toEntity().tenantId)
        assertEquals(tenant, LedgerEntryDto(id = "l1", tenantId = tenant, parentId = "p1", accountId = "a1", category = "tuition", entryType = "charge", amount = 1000.0).toEntity().tenantId)
    }
}
