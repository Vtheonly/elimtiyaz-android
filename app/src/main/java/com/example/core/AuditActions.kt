package com.example.core

/**
 * Audit action constants — the actions the Android app ACTUALLY writes.
 *
 * Wire-protocol: these strings appear in the `audit_logs.action` column in
 * Supabase and must match the desktop registry verbatim. The full canonical
 * action registry lives in the DESKTOP repo
 * (`elimtiyaz-desktop/src/core/audit-actions.ts` — see the hub problem
 * registry, DEAD-007): Android does not duplicate all 80+ desktop actions —
 * only the constants it references are declared here. When a new Android
 * audit write is added, declare its constant here at the same time (the
 * raw-string habit — `audit("x.y", …)` — bypassed this object for most of
 * the app's history; do not extend that habit).
 *
 * T-062 (DEAD-007): 76 never-referenced constants were removed after a
 * per-constant reachability scan (rg over app/src/main + app/src/test):
 * ACCOUNT_APPROVAL_*, ACTIVATION_CODE_*, BACKUP_*, WORKFLOW_*, AI_*,
 * ROUTING_*, OVERDUE_SCAN_RUN, MATERIALIZED_VIEWS_REFRESH,
 * SERVER_SECRET_UPDATE, SYNC_CONFLICT and the CRM/financial action
 * families (the repositories write those audit rows with raw string
 * literals, not these constants — see the note above).
 */
object AuditActions {
    // Auth — written by LocalAuthRepository.
    const val AUTH_LOGIN = "auth.login"
    const val AUTH_LOGOUT = "auth.logout"
    const val AUTH_PASSWORD_CHANGE = "auth.password_change"

    // Academics — written by LocalSubjectRepository.
    const val SUBJECT_CREATE = "subject.create"
    const val SUBJECT_UPDATE = "subject.update"

    // Sync — written by SyncService's failure logger.
    const val SYNC_PUSH_FAIL = "sync.push_fail"
}
