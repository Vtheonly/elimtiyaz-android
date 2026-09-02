package com.example.infrastructure.sync

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T-128 / CROSS-103 — the Android refund flow must propagate the
 * locally-reverted installment state to the server.
 *
 * The defect: `LocalPaymentRepository.refund` ran the canonical waterfall
 * revert LOCALLY (Room) but never enqueued the reverted installments —
 * the payment-status push and the reversal ledger push do NOT trigger any
 * server-side waterfall replay (verified against the migration chain:
 * `upsert_ledger_entry_from_import` explicitly SKIPS reversal entries for
 * waterfall application, migration 0037:653-657; the canonical
 * `revert_payment_allocation` RPC is the only server-side revert and the
 * Android path does not call it — ARCH-003/ADR-005). Result: server-side
 * installments stayed stale after every Android refund.
 *
 * The fix: after `installmentDao.update(...)` in the revert loop, enqueue
 * an `installment` sync entity (operation `update`) with the REVERTED
 * values — the exact payload shape the batch-registration flow uses, so
 * the dispatcher pushes it via the idempotent
 * `upsert_installment_from_import` RPC (TIER 4, migration 0037:741).
 *
 * Source-scan pattern (RefundCorrectnessT017Test): the repository needs a
 * real Room + network stack for an E2E round-trip. The T-017 suite pins
 * the already-refunded guard (a second refund enqueues NOTHING), so this
 * suite only needs to pin the new propagation.
 *
 * Deviation note (recorded in the task entry): the T-017 entry framed the
 * installment enqueue as T-059/ADR-005 territory. This fix does NOT
 * rewire the write architecture — it extends the EXISTING sanctioned
 * import-RPC path (the same one batch-registration uses since TIER 4),
 * which is precisely how the interim architecture converges the data.
 */
class RefundInstallmentSyncT128Test {

    private fun source(): String =
        File("src/main/java/com/example/infrastructure/local/LocalRepositories.kt").readText()

    private fun refundBlock(src: String): String =
        Regex("override suspend fun refund\\(paymentId: String[\\s\\S]*?\\n    \\}")
            .find(src)?.value ?: error("refund flow not found")

    @Test
    fun `the refund revert loop enqueues the reverted installments (CROSS-103)`() {
        val block = refundBlock(source())
        assertTrue(
            "the revert loop must enqueue installment sync entities",
            block.contains("entity = \"installment\""),
        )
        assertTrue(
            "operation must be update (the installment already exists server-side)",
            block.contains("operation = \"update\""),
        )
    }

    @Test
    fun `the enqueued payload carries the REVERTED values, built from the updated entity`() {
        val block = refundBlock(source())
        // the local update and the enqueue must use the SAME reverted entity
        assertTrue("the loop must build a single reverted copy", block.contains("val reverted = ins.copy("))
        assertTrue("the local write persists it", block.contains("installmentDao.update(reverted)"))
        // payload fields mirror the batch-registration contract (TIER 4)
        for (field in listOf(
            "put(\"id\", reverted.id)", "put(\"tenantId\", reverted.tenantId)",
            "put(\"parentId\", reverted.parentId)", "put(\"parentCode\"",
            "put(\"studentId\"", "put(\"category\", reverted.category)",
            "put(\"label\", reverted.label)", "put(\"amountDue\", reverted.amountDue)",
            "put(\"amountPaid\", reverted.amountPaid)", "put(\"amountPending\", reverted.amountPending)",
            "put(\"dueDate\", reverted.dueDate)", "put(\"status\", reverted.status)",
        )) {
            assertTrue("payload field missing: $field", block.contains(field))
        }
    }

    @Test
    fun `the enqueue happens INSIDE the revert loop (per reverted installment)`() {
        val block = refundBlock(source())
        val loopIdx = block.indexOf("revert.reverts.forEach")
        val updateIdx = block.indexOf("installmentDao.update(reverted)")
        // search the installment enqueue AFTER the local update (the refund
        // flow's FIRST enqueue is the payment-status push — not this one)
        val enqueueIdx = block.indexOf("syncSupport?.enqueueOnly", updateIdx)
        assertTrue("revert loop must exist", loopIdx >= 0)
        assertTrue("local update must exist", updateIdx >= 0)
        assertTrue("installment enqueue must exist after the local update", enqueueIdx > updateIdx)
        assertTrue("both must be inside the revert loop", loopIdx < updateIdx && updateIdx < enqueueIdx)
    }

    @Test
    fun `the dispatcher still owns the installment push path (no parallel implementation)`() {
        val dispatcher = File("src/main/java/com/example/infrastructure/sync/SyncQueueDispatcher.kt").readText()
        assertTrue(
            "the dispatcher must keep its TIER 4 installment case",
            dispatcher.contains("\"installment\" -> pushInstallment"),
        )
        assertTrue(
            "the push must go through the canonical idempotent RPC",
            dispatcher.contains("rpc(\"upsert_installment_from_import\""),
        )
    }
}
