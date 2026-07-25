package com.elimtiyaz.core.common

/**
 * Six roles defined in the master plan §02.07.
 * The Android app serves the first four; the last two redirect to the Web Portal.
 */
enum class Role(val key: String, val displayFr: String, val displayAr: String) {
    SuperAdmin("super_admin",       "Super Administrateur", "مدير عام"),
    FinancialOfficer("financial_officer", "Agent Financier", "مسؤول مالي"),
    Teacher("teacher",              "Enseignant",           "معلم"),
    SupportStaff("support_staff",   "Personnel de Soutien", "موظف دعم"),
    Parent("parent",                "Parent",               "ولي أمر"),
    Student("student",              "Élève",                "تلميذ");

    companion object {
        fun fromKey(key: String?): Role? = values().firstOrNull { it.key == key }
    }
}

/**
 * Permission tokens. Check with [Session.can] in the UI layer to gate
 * visibility of tabs, FABs and menu items.
 *
 * Keep this list aligned with the RBAC matrix in the master plan §02.07.
 */
enum class Permission(val key: String) {
    // CRM
    ViewRoster("view_roster"),
    CreateParent("create_parent"),
    EditParent("edit_parent"),
    DeleteParent("delete_parent"),
    CreateStudent("create_student"),
    EditStudent("edit_student"),
    PromoteStudent("promote_student"),

    // Academic
    ViewAcademics("view_academics"),
    EnterGrades("enter_grades"),
    ManageSubjects("manage_subjects"),
    ManageClasses("manage_classes"),
    AssignHomework("assign_homework"),
    RollCall("roll_call"),

    // Financial
    ViewFinancials("view_financials"),
    CollectPayment("collect_payment"),
    RefundPayment("refund_payment"),
    AdjustAccount("adjust_account"),
    GenerateReceipt("generate_receipt"),
    ViewDebt("view_debt"),
    SendReminder("send_reminder"),

    // Expenses
    SubmitExpense("submit_expense"),
    ApproveExpense("approve_expense"),
    DisburseExpense("disburse_expense"),
    SettleExpenseProof("settle_expense_proof"),

    // HR / Personnel
    ViewPersonnel("view_personnel"),
    ManagePersonnel("manage_personnel"),
    ViewAuditLog("view_audit_log"),
    ViewReleve("view_releve"),

    // Routing
    AccessDriverMode("access_driver_mode"),

    // Settings
    ManageSettings("manage_settings"),
    ManageTenants("manage_tenants"),
}
