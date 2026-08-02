package com.example.core

/**
 * Audit action constants — mirrors the desktop `src/core/audit-actions.ts`.
 * Wire-protocol: these strings appear in the `audit_logs.action` column in
 * Supabase. They must be preserved verbatim.
 */
object AuditActions {
    // Auth
    const val AUTH_LOGIN           = "auth.login"
    const val AUTH_LOGOUT          = "auth.logout"
    const val AUTH_PASSWORD_RESET  = "auth.password_reset"
    const val AUTH_PASSWORD_CHANGE = "auth.password_change"
    const val AUTH_SESSION_REVOKED = "auth.session_revoked"

    // Account approval
    const val ACCOUNT_APPROVAL_APPROVE      = "account_approval.approve"
    const val ACCOUNT_APPROVAL_REJECT       = "account_approval.reject"
    const val ACCOUNT_APPROVAL_EXPIRE_BATCH = "account_approval.expire_batch"
    const val ACTIVATION_CODE_BIND          = "activation_code.bind"
    const val ACTIVATION_CODE_GENERATE      = "activation_code.generate"

    // CRM
    const val PARENT_CREATE   = "parent.create"
    const val PARENT_UPDATE   = "parent.update"
    const val PARENT_DELETE   = "parent.delete"
    const val STUDENT_CREATE  = "student.create"
    const val STUDENT_UPDATE  = "student.update"
    const val STUDENT_PROMOTE = "student.promote"
    const val BATCH_REGISTER  = "crm.batch_register"

    // Academic
    const val CLASS_CREATE      = "class.create"
    const val CLASS_UPDATE      = "class.update"
    const val CLASS_DELETE      = "class.delete"
    const val SUBJECT_CREATE    = "subject.create"
    const val SUBJECT_UPDATE    = "subject.update"
    const val SUBJECT_ARCHIVE   = "subject.archive"
    const val SUBJECT_ASSIGN    = "subject.assign"
    const val GRADE_ENTER       = "grade.enter"
    const val ATTENDANCE_SUBMIT = "attendance.submit"
    const val ATTENDANCE_RECORD = "attendance.roll_call"
    const val HOMEWORK_PUSH     = "homework.push"
    const val ATTENDANCE_ALERT  = "attendance.alert"

    // Financial
    const val PAYMENT_COLLECT          = "payment.collect"
    const val PAYMENT_REFUND           = "payment.refund"
    const val PAYMENT_ADJUST           = "payment.adjust"
    const val RECEIPT_GENERATE         = "receipt.generate"
    const val INSTALLMENT_CREATE       = "installment.create"
    const val INSTALLMENT_MARK_PAID    = "installment.mark_paid"
    const val INSTALLMENT_RESCHEDULE   = "installment.reschedule"
    const val INSTALLMENT_REGENERATE   = "installment.regenerate"
    const val INSTALLMENT_FIND_OVERDUE = "installment.find_overdue"
    const val PRICING_UPDATE           = "pricing.update"
    const val DEBT_REMINDER_SENT       = "debt.reminder_sent"

    // Ledger (engine-level)
    const val LEDGER_ENTRY_APPEND     = "ledger.entry.append"
    const val LEDGER_ENTRY_APPEND_MANY = "ledger.entry.append_many"
    const val LEDGER_ENTRY_REVERSE    = "ledger.entry.reverse"
    const val LEDGER_RECONCILE        = "ledger.reconcile"

    // Expense
    const val EXPENSE_SUBMIT   = "expense.submit"
    const val EXPENSE_APPROVE  = "expense.approve"
    const val EXPENSE_REJECT   = "expense.reject"
    const val EXPENSE_DISBURSE = "expense.disburse"
    const val EXPENSE_SETTLE   = "expense.settle"

    // Personnel
    const val PERSONNEL_CREATE = "personnel.create"
    const val PERSONNEL_UPDATE = "personnel.update"
    const val PERSONNEL_DELETE = "personnel.delete"
    const val RELEVE_CREATE    = "releve.create"

    // Departments
    const val DEPARTMENT_CREATE    = "department.create"
    const val DEPARTMENT_ARCHIVE   = "department.archive"
    const val DEPARTMENT_UNARCHIVE = "department.unarchive"

    // Settings / System
    const val SETTINGS_UPDATE              = "settings.update"
    const val RBAC_MATRIX_UPDATE           = "rbac.matrix_update"
    const val BACKUP_CREATED               = "backup.created"
    const val BACKUP_RESTORED              = "backup.restored"
    const val BACKUP_PURGE                 = "backup.purge"
    const val WORKFLOW_PUBLISHED           = "workflow.published"
    const val WORKFLOW_TRIGGERED           = "workflow.triggered"
    const val WORKFLOW_RUN                 = "workflow.run"
    const val WORKFLOW_RETRY               = "workflow.retry"
    const val OVERDUE_SCAN_RUN             = "overdue_scan.run"
    const val MATERIALIZED_VIEWS_REFRESH   = "materialized_views.refresh"
    const val SERVER_SECRET_UPDATE         = "server_secret.update"

    // Routing
    const val ROUTING_OPTIMIZE   = "routing.optimize"
    const val ROUTING_TRIP_START = "routing.trip_start"
    const val ROUTING_TRIP_END   = "routing.trip_end"

    // AI
    const val AI_NARRATIVE_DRAFTED               = "ai.narrative_drafted"
    const val AI_NARRATIVE_APPROVED              = "ai.narrative_approved"
    const val AI_NARRATIVE_REJECTED              = "ai.narrative_rejected"
    const val AI_DRAFT_GENERATED                 = "ai.draft_generated"
    const val AI_DRAFT_SENT                      = "ai.draft_sent"
    const val AI_ANOMALY_FLAGGED                 = "ai.anomaly_flagged"
    const val AI_ANOMALY_JUSTIFICATION_REQUESTED = "ai.anomaly_justification_requested"
    const val AI_CONFIG_UPDATE                   = "ai.config_update"
    const val AI_CONFIG_TEST                     = "ai.config_test"

    // Sync (mobile-specific)
    const val SYNC_CONFLICT  = "sync.conflict"
    const val SYNC_PUSH_FAIL = "sync.push_failed"
}
