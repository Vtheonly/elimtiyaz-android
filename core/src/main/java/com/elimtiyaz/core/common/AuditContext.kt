package com.elimtiyaz.core.common

/**
 * Audit context — every mutating call carries one of these so the
 * AuditRepository can persist a row matching the contextual schema
 * (master plan §12.02). All fields are required.
 */
data class AuditContext(
    val action: String,                 // e.g. "payment.create", "expense.approve"
    val entityType: String,             // e.g. "payment", "student"
    val entityId: String,               // the row id affected
    val actorId: String,                // session.userId
    val tenantId: String,               // session.tenantId
    val diff: String? = null,           // JSON-serialised before/after
    val note: String? = null,
)

object AuditActions {
    const val ParentCreate       = "parent.create"
    const val ParentUpdate       = "parent.update"
    const val StudentCreate      = "student.create"
    const val StudentUpdate      = "student.update"
    const val StudentPromote     = "student.promote"
    const val PaymentCreate      = "payment.create"
    const val PaymentRefund      = "payment.refund"
    const val PaymentAdjust      = "payment.adjust"
    const val ReceiptGenerate    = "receipt.generate"
    const val ExpenseSubmit      = "expense.submit"
    const val ExpenseApprove     = "expense.approve"
    const val ExpenseReject      = "expense.reject"
    const val ExpenseDisburse    = "expense.disburse"
    const val ExpenseSettle      = "expense.settle"
    const val AttendanceSubmit   = "attendance.submit"
    const val GradeEnter         = "grade.enter"
    const val HomeworkPush       = "homework.push"
    const val AuthLogin          = "auth.login"
    const val AuthLogout         = "auth.logout"
    const val AuthPasswordReset  = "auth.password_reset"
}
