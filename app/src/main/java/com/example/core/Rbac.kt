package com.example.core

/**
 * RBAC — mirrors desktop `src/core/rbac/`. Wire-protocol: Role.code and
 * Permission.code are stable strings stored in JWT claims and the
 * role_assignments table. Renaming requires a Supabase migration.
 */

/** 11 roles. 9 staff (can sign into Android) + 2 web-portal-only (parent, student). */
enum class Role(val code: String) {
    SUPER_ADMIN("super_admin"),
    FINANCIAL_OFFICER("financial_officer"),
    TEACHER("teacher"),
    SUPPORT_STAFF("support_staff"),
    MANAGER("manager"),
    BUYER("buyer"),
    DRIVER("driver"),
    WAREHOUSE_WORKER("warehouse_worker"),
    WORKER("worker"),
    PARENT("parent"),
    STUDENT("student");

    companion object {
        fun fromCode(code: String): Role? = values().firstOrNull { it.code == code }
        val STAFF_ROLES: Set<Role> = setOf(SUPER_ADMIN, FINANCIAL_OFFICER, TEACHER, SUPPORT_STAFF, MANAGER, BUYER, DRIVER, WAREHOUSE_WORKER, WORKER)
        val ADMINISTRATIVE_ROLES: Set<Role> = setOf(SUPER_ADMIN, MANAGER)
        val SUPERVISORY_ROLES: Set<Role> = setOf(SUPER_ADMIN, MANAGER)
        val OPERATIONAL_ROLES: Set<Role> = setOf(TEACHER, BUYER, DRIVER, WAREHOUSE_WORKER, WORKER, SUPPORT_STAFF)
        val DASHBOARD_ROLES: Set<Role> = setOf(SUPER_ADMIN, FINANCIAL_OFFICER, SUPPORT_STAFF, MANAGER)
    }
}

/** 56 action-grained permissions. Wire-protocol: stable snake_case strings. */
enum class Permission(val code: String) {
    VIEW_ROSTER("view_roster"), CREATE_PARENT("create_parent"), EDIT_PARENT("edit_parent"), DELETE_PARENT("delete_parent"),
    CREATE_STUDENT("create_student"), EDIT_STUDENT("edit_student"), PROMOTE_STUDENT("promote_student"),
    VIEW_ACADEMICS("view_academics"), ENTER_GRADES("enter_grades"), MANAGE_SUBJECTS("manage_subjects"),
    MANAGE_CLASSES("manage_classes"), ASSIGN_HOMEWORK("assign_homework"), ROLL_CALL("roll_call"),
    VIEW_FINANCIALS("view_financials"), COLLECT_PAYMENT("collect_payment"), REFUND_PAYMENT("refund_payment"),
    ADJUST_ACCOUNT("adjust_account"), GENERATE_RECEIPT("generate_receipt"), VIEW_DEBT("view_debt"), SEND_REMINDER("send_reminder"),
    SUBMIT_EXPENSE("submit_expense"), APPROVE_EXPENSE("approve_expense"), DISBURSE_EXPENSE("disburse_expense"), SETTLE_EXPENSE_PROOF("settle_expense_proof"),
    VIEW_PERSONNEL("view_personnel"), MANAGE_PERSONNEL("manage_personnel"), VIEW_AUDIT_LOG("view_audit_log"), VIEW_RELEVE("view_releve"),
    ACCESS_DRIVER_MODE("access_driver_mode"),
    MANAGE_SETTINGS("manage_settings"), MANAGE_TENANTS("manage_tenants"), MANAGE_PRICING("manage_pricing"),
    MANAGE_WORKFLOWS("manage_workflows"), VIEW_WORKFLOW_RUNS("view_workflow_runs"), EXECUTE_WORKFLOW("execute_workflow"),
    MANAGE_BACKUPS("manage_backups"),
    USE_AI("use_ai"), MANAGE_AI_CONFIG("manage_ai_config"),
    VIEW_DEPARTMENTS("view_departments"), MANAGE_DEPARTMENTS("manage_departments"), MANAGE_EMPLOYEE_PROFILES("manage_employee_profiles"),
    VIEW_SALARY("view_salary"), MANAGE_SCHEDULES("manage_schedules"), VIEW_ATTENDANCE("view_attendance"),
    CLOCK_IN_OUT("clock_in_out"), APPROVE_REQUESTS("approve_requests"), SUBMIT_REQUESTS("submit_requests"),
    MANAGE_TASKS("manage_tasks"), VIEW_TASKS("view_tasks"), UPDATE_TASK_STATUS("update_task_status"),
    VIEW_PERFORMANCE("view_performance"), MANAGE_PERFORMANCE("manage_performance"),
    USE_CHAT("use_chat"), MANAGE_CHAT_CHANNELS("manage_chat_channels"),
    MANAGE_PURCHASE_REQUESTS("manage_purchase_requests"), MANAGE_SUPPLIERS("manage_suppliers"),
    MANAGE_DELIVERIES("manage_deliveries"), MANAGE_INVENTORY("manage_inventory"),
    MANAGE_ONBOARDING("manage_onboarding"), VIEW_WORKFORCE_REPORTS("view_workforce_reports");

    companion object {
        fun fromCode(code: String): Permission? = values().firstOrNull { it.code == code }
        val DEFAULT_ROLE_PERMISSIONS: Map<Role, Set<Permission>> = mapOf(
            Role.SUPER_ADMIN to values().toSet(),
            Role.FINANCIAL_OFFICER to values().toSet(),
            Role.TEACHER to values().toSet(),
            Role.SUPPORT_STAFF to values().toSet(),
            Role.MANAGER to values().toSet(),
            Role.BUYER to values().toSet(),
            Role.DRIVER to values().toSet(),
            Role.WAREHOUSE_WORKER to values().toSet(),
            Role.WORKER to values().toSet(),
            Role.PARENT to emptySet(),
            Role.STUDENT to emptySet(),
        )
    }
}

/** Immutable session value — populated at sign-in. */
data class Session(
    val userId: String,
    val tenantId: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
    val role: Role,
    val permissions: Set<Permission>,
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Long,
    val locale: String,
) {
    fun can(permission: Permission): Boolean = permission in permissions
    fun hasRole(role: Role): Boolean = this.role == role
    fun hasAnyRole(vararg roles: Role): Boolean = role in roles
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean = now >= (expiresAt - 60_000L)
}

/** Declarative access requirement per feature. */
sealed class AccessRequirement {
    object Empty : AccessRequirement()
    data class Permanent(val state: PermanentState) : AccessRequirement()
    data class RequiresPermission(val permission: Permission, val hideWhenUnauthenticated: Boolean = false) : AccessRequirement()
    data class RequiresAnyOf(val permissions: List<Permission>, val hideWhenUnauthenticated: Boolean = false) : AccessRequirement()
    data class RequiresAllOf(val permissions: List<Permission>, val hideWhenUnauthenticated: Boolean = false) : AccessRequirement()
    data class RequiresRole(val roles: List<Role>, val hideWhenUnauthenticated: Boolean = false) : AccessRequirement()
}

enum class PermanentState { REMOVED, NOT_YET_AVAILABLE, DESKTOP_ONLY, PLAN_UPGRADE_REQUIRED }

sealed class AccessState {
    object Enabled : AccessState()
    object Hidden : AccessState()
    data class Disabled(val reason: DisableReason) : AccessState()
}

sealed class DisableReason {
    object NotAuthenticated : DisableReason()
    data class MissingPermission(val permission: Permission) : DisableReason()
    data class MissingRole(val roles: List<Role>) : DisableReason()
    data class FeatureFlagOff(val flag: String) : DisableReason()
    data class Permanent(val state: PermanentState) : DisableReason()
}

interface FeatureFlagProvider { fun isEnabled(flag: String): Boolean }
object AlwaysOnFlagProvider : FeatureFlagProvider { override fun isEnabled(flag: String): Boolean = true }

/** Pure feature gate evaluator. No side effects. */
object FeatureGate {
    fun evaluate(requirement: AccessRequirement, session: Session?, flags: FeatureFlagProvider = AlwaysOnFlagProvider): AccessState = when (requirement) {
        is AccessRequirement.Empty -> AccessState.Enabled
        is AccessRequirement.Permanent -> AccessState.Disabled(DisableReason.Permanent(requirement.state))
        is AccessRequirement.RequiresPermission -> when {
            session == null -> if (requirement.hideWhenUnauthenticated) AccessState.Hidden else AccessState.Disabled(DisableReason.NotAuthenticated)
            session.can(requirement.permission) -> AccessState.Enabled
            else -> AccessState.Disabled(DisableReason.MissingPermission(requirement.permission))
        }
        is AccessRequirement.RequiresAnyOf -> when {
            session == null -> if (requirement.hideWhenUnauthenticated) AccessState.Hidden else AccessState.Disabled(DisableReason.NotAuthenticated)
            requirement.permissions.any { session.can(it) } -> AccessState.Enabled
            else -> AccessState.Disabled(DisableReason.MissingPermission(requirement.permissions.first()))
        }
        is AccessRequirement.RequiresAllOf -> when {
            session == null -> if (requirement.hideWhenUnauthenticated) AccessState.Hidden else AccessState.Disabled(DisableReason.NotAuthenticated)
            else -> requirement.permissions.firstOrNull { !session.can(it) }?.let { AccessState.Disabled(DisableReason.MissingPermission(it)) } ?: AccessState.Enabled
        }
        is AccessRequirement.RequiresRole -> when {
            session == null -> if (requirement.hideWhenUnauthenticated) AccessState.Hidden else AccessState.Disabled(DisableReason.NotAuthenticated)
            session.role in requirement.roles -> AccessState.Enabled
            else -> AccessState.Disabled(DisableReason.MissingRole(requirement.roles))
        }
    }
}
