package com.example.infrastructure.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T-017 / BUSINESS-102 + CROSS-102 — Android refund correctness (interim fix
 * pending the ADR-005 write-architecture decision).
 *
 * The defects:
 *  - BUSINESS-102: `LocalPaymentRepository.refund` had NO already-refunded
 *    guard — a second refund call on the same payment enqueued a SECOND
 *    refund push and created a SECOND reversal ledger entry (double refund
 *    on both Room and the server after drain). A refunded payment is a
 *    TERMINAL state: the second call must be a no-op returning the current
 *    row.
 *  - CROSS-102: the refund reason existed ONLY in the local audit row — the
 *    synced payload carried no reason, so the server-side audit trail could
 *    not record WHY a refund happened.
 *
 * Fix under test (source-scan, SyncErrorSurfacingTest pattern — the
 * repository needs a real Room + network stack for an E2E round-trip):
 *  1. the guard returns the already-refunded row BEFORE any write/queue/
 *     audit side effect;
 *  2. `reason` is part of the refund sync payload.
 *
 * Left (ADR-005-gated, recorded in the task entry): enqueue the installment
 * state changes so the server converges without relying on the reversal
 * entry's server-side replay — that belongs to the canonical write-path
 * rollout (T-059), not an interim patch.
 */
class RefundCorrectnessT017Test {

    @Test
    fun `refund guards the already-refunded terminal state before any side effect`() {
        val src = File("src/main/java/com/example/infrastructure/local/LocalRepositories.kt").readText()
        val block = Regex(
            "override suspend fun refund\\(paymentId: String[\\s\\S]*?syncSupport\\?\\.enqueueOnly",
        ).find(src)?.value ?: error("refund flow not found")
        assertTrue(
            "the already-refunded guard must run BEFORE the status update / queue push",
            Regex("existing.status == PaymentStatus\\.REFUNDED\\.code").find(block) != null,
        )
        assertTrue(
            "the guard must return the existing row unchanged",
            block.contains("return Result.Ok(LocalMappers.run { existing.toDomain() })"),
        )
        // The guard is the FIRST thing after the fetch — no write below it yet.
        val guardIdx = block.indexOf("BUSINESS-102")
        val firstWrite = block.indexOf("paymentDao.update")
        val firstEnqueue = block.indexOf("enqueueOnly")
        assertTrue("guard must precede the status write", guardIdx in 0 until firstWrite)
        assertTrue("guard must precede the queue push", guardIdx in 0 until firstEnqueue)
    }

    @Test
    fun `refund sync payload carries the reason (CROSS-102)`() {
        val src = File("src/main/java/com/example/infrastructure/local/LocalRepositories.kt").readText()
        val block = Regex(
            "override suspend fun refund\\(paymentId: String[\\s\\S]*?\\n    \\}",
        ).find(src)?.value ?: error("refund flow not found")
        assertTrue(
            "the refund payload must include the reason",
            block.contains("put(\"reason\", reason)"),
        )
    }

    @Test
    fun `the refund audit row still records the reason locally (unchanged contract)`() {
        val src = File("src/main/java/com/example/infrastructure/local/LocalRepositories.kt").readText()
        assertTrue(
            "the local audit entry keeps the reason in its afterJson payload",
            src.contains("{\"reason\":"),
        )
        // The reason must NOT be dropped from the audit call.
        val block = Regex(
            "override suspend fun refund\\(paymentId: String[\\s\\S]*?\\n    \\}",
        ).find(src)?.value ?: error("refund flow not found")
        assertFalse(
            "the refund must not write an audit row without the reason",
            block.contains("after = \"\"\"{\"reason\":\"\"}\"\"\""),
        )
    }
}
