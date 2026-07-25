package com.elimtiyaz.core.rbac

import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.common.Role

/**
 * The canonical feature tree for the El-Imtiyaz Android app.
 *
 * This object is the single source of truth for the application's
 * hierarchical structure: **Section → Option → Page/Action/Feature**.
 * Every screen, every action, every cross-cutting capability has an entry here
 * that names it and declares its access requirement.
 *
 * ## Why a registry?
 *
 * 1. **One place to change rules.** When RBAC evolves (a new role, a new paid
 *    plan, a removed feature), you edit this file and the entire UI follows.
 *    Screens never inspect permissions directly.
 *
 * 2. **Discoverable structure.** New contributors can read this file and
 *    understand the entire app's surface area in 5 minutes.
 *
 * 3. **Future analytics/audit.** Every user action can carry the [FeatureNode.id]
 *    of the node they interacted with, giving product teams a stable taxonomy
 *    for analytics and auditors a stable taxonomy for the audit log.
 *
 * 4. **Greyed-out by default.** Because every node carries an [AccessRequirement],
 *    the gating UI helpers ([GatedContent], [GatedFloatingActionButton], etc.)
 *    can automatically render the correct disabled state without each screen
 *    re-implementing the check.
 *
 * ## Adding a new feature
 *
 * 1. Pick the parent section (or create a new one).
 * 2. Add a [FeatureNode] entry with a stable `id` (dotted path, lowercase).
 * 3. Set the [AccessRequirement] — usually `AccessRequirement.require(Permission.X)`.
 * 4. In your screen, pass the node to `GatedContent(node) { ... }` or use
 *    `accessStateOf(node)` to drive custom UI.
 *
 * ## Convention
 *
 * - IDs are dotted lowercase paths: `crm.parent.detail`, `fin.payment.collect`.
 * - IDs are stable forever — never rename a node, deprecate it instead.
 * - Leaf nodes (pages / actions) carry the requirement; section/option nodes
 *   typically have [AccessRequirement.None] and rely on their children's rules.
 * - For permanently-removed features (e.g. the legacy AI assistant), keep the
 *   node in the tree with [AccessRequirement.permanently] so users see it greyed
 *   out and know it existed. This matches the user's instruction that disabled
 *   features should appear greyed out, not hidden.
 */
object FeatureRegistry {

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION: Dashboard
    // ─────────────────────────────────────────────────────────────────────────
    val Dashboard = FeatureNode(
        id = "dashboard",
        title = "Tableau de bord",
        description = "KPIs, alertes, recherche globale et rapports.",
        requirement = AccessRequirement.None,  // visible to all authenticated staff
        children = listOf(
            FeatureNode("dashboard.overview", "Vue d'ensemble"),
            FeatureNode(
                id = "dashboard.alerts",
                title = "Alertes",
                description = "Centre de notifications.",
                requirement = AccessRequirement.None,
            ),
            FeatureNode(
                id = "dashboard.search",
                title = "Recherche globale",
                requirement = AccessRequirement.None,
            ),
            FeatureNode(
                id = "dashboard.reports",
                title = "Rapports",
                description = "Catalogue de rapports (génération PDF sur desktop).",
                requirement = AccessRequirement.None,
            ),
        ),
    )

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION: CRM (Roster)
    // ─────────────────────────────────────────────────────────────────────────
    val Crm = FeatureNode(
        id = "crm",
        title = "Élèves & Parents",
        description = "Annuaire des parents et élèves, profils, inscription batch.",
        requirement = AccessRequirement.require(Permission.ViewRoster),
        children = listOf(
            FeatureNode("crm.parents", "Parents"),
            FeatureNode("crm.students", "Élèves"),
            FeatureNode(
                id = "crm.parent.create",
                title = "Créer un parent",
                requirement = AccessRequirement.require(Permission.CreateParent),
            ),
            FeatureNode(
                id = "crm.parent.edit",
                title = "Modifier un parent",
                requirement = AccessRequirement.require(Permission.EditParent),
            ),
            FeatureNode(
                id = "crm.parent.delete",
                title = "Supprimer un parent",
                requirement = AccessRequirement.require(Permission.DeleteParent),
            ),
            FeatureNode(
                id = "crm.student.create",
                title = "Inscrire un élève",
                requirement = AccessRequirement.require(Permission.CreateStudent),
            ),
            FeatureNode(
                id = "crm.student.edit",
                title = "Modifier un élève",
                requirement = AccessRequirement.require(Permission.EditStudent),
            ),
            FeatureNode(
                id = "crm.student.promote",
                title = "Promouvoir un élève",
                requirement = AccessRequirement.require(Permission.PromoteStudent),
            ),
            FeatureNode(
                id = "crm.batch_registration",
                title = "Inscription groupée",
                description = "Inscription atomique d'un parent et de ses enfants.",
                requirement = AccessRequirement.require(Permission.CreateParent),
            ),
        ),
    )

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION: Academics
    // ─────────────────────────────────────────────────────────────────────────
    val Academics = FeatureNode(
        id = "academics",
        title = "Pédagogie",
        description = "Classes, matières, présences, notes, devoirs.",
        requirement = AccessRequirement.require(Permission.ViewAcademics),
        children = listOf(
            FeatureNode("academics.classes", "Classes"),
            FeatureNode("academics.subjects", "Matières"),
            FeatureNode("academics.homework", "Devoirs"),
            FeatureNode(
                id = "academics.class.create",
                title = "Créer une classe",
                requirement = AccessRequirement.require(Permission.ManageClasses),
            ),
            FeatureNode(
                id = "academics.subject.manage",
                title = "Gérer les matières",
                requirement = AccessRequirement.require(Permission.ManageSubjects),
            ),
            FeatureNode(
                id = "academics.roll_call",
                title = "Appel (30 secondes)",
                requirement = AccessRequirement.require(Permission.RollCall),
            ),
            FeatureNode(
                id = "academics.grade.enter",
                title = "Saisie des notes",
                requirement = AccessRequirement.require(Permission.EnterGrades),
            ),
            FeatureNode(
                id = "academics.homework.push",
                title = "Diffuser un devoir",
                requirement = AccessRequirement.require(Permission.AssignHomework),
            ),
        ),
    )

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION: Financials
    // ─────────────────────────────────────────────────────────────────────────
    val Financials = FeatureNode(
        id = "financials",
        title = "Finances",
        description = "Paiements, reçus, échéanciers, créances, dépenses.",
        requirement = AccessRequirement.require(Permission.ViewFinancials),
        children = listOf(
            FeatureNode("financials.payments", "Paiements"),
            FeatureNode("financials.expenses", "Dépenses"),
            FeatureNode("financials.debt", "Créances"),
            FeatureNode(
                id = "financials.payment.collect",
                title = "Encaisser un paiement",
                requirement = AccessRequirement.require(Permission.CollectPayment),
            ),
            FeatureNode(
                id = "financials.payment.refund",
                title = "Rembourser un paiement",
                requirement = AccessRequirement.require(Permission.RefundPayment),
            ),
            FeatureNode(
                id = "financials.receipt.generate",
                title = "Générer un reçu",
                requirement = AccessRequirement.require(Permission.GenerateReceipt),
            ),
            FeatureNode(
                id = "financials.account.adjust",
                title = "Ajustement de compte",
                description = "Ajustement discrétionnaire (remplace les bourses dépréciées).",
                requirement = AccessRequirement.require(Permission.AdjustAccount),
            ),
            FeatureNode(
                id = "financials.debt.view",
                title = "Voir les créances",
                requirement = AccessRequirement.require(Permission.ViewDebt),
            ),
            FeatureNode(
                id = "financials.debt.remind",
                title = "Envoyer un rappel",
                requirement = AccessRequirement.require(Permission.SendReminder),
            ),
            FeatureNode(
                id = "financials.expense.submit",
                title = "Soumettre une dépense",
                requirement = AccessRequirement.require(Permission.SubmitExpense),
            ),
            FeatureNode(
                id = "financials.expense.approve",
                title = "Approuver une dépense",
                requirement = AccessRequirement.require(Permission.ApproveExpense),
            ),
            FeatureNode(
                id = "financials.expense.disburse",
                title = "Décaisser une dépense",
                requirement = AccessRequirement.require(Permission.DisburseExpense),
            ),
            FeatureNode(
                id = "financials.expense.settle",
                title = "Téléverser un justificatif",
                requirement = AccessRequirement.require(Permission.SettleExpenseProof),
            ),
        ),
    )

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION: Personnel
    // ─────────────────────────────────────────────────────────────────────────
    val Personnel = FeatureNode(
        id = "personnel",
        title = "Personnel",
        description = "Annuaire, relevé, journal d'audit, workflows.",
        requirement = AccessRequirement.require(Permission.ViewPersonnel),
        children = listOf(
            FeatureNode("personnel.directory", "Annuaire"),
            FeatureNode(
                id = "personnel.releve",
                title = "Relevé (heures)",
                requirement = AccessRequirement.require(Permission.ViewReleve),
            ),
            FeatureNode(
                id = "personnel.audit_log",
                title = "Journal d'audit",
                requirement = AccessRequirement.require(Permission.ViewAuditLog),
            ),
            FeatureNode(
                id = "personnel.workflow_monitor",
                title = "Workflows",
                description = "Suivi en lecture seule des Edge Functions (édition DAG sur desktop).",
                requirement = AccessRequirement.requireAny(Permission.ViewAuditLog, Permission.ViewPersonnel),
            ),
            FeatureNode(
                id = "personnel.manage",
                title = "Gérer le personnel",
                requirement = AccessRequirement.require(Permission.ManagePersonnel),
            ),
        ),
    )

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION: Routing (Driver mode)
    // ─────────────────────────────────────────────────────────────────────────
    val Routing = FeatureNode(
        id = "routing",
        title = "Tournées",
        description = "Optimisation de tournées pour les chauffeurs (fusion de l'ancienne app Traffic).",
        requirement = AccessRequirement.require(Permission.AccessDriverMode),
        children = listOf(
            FeatureNode("routing.vehicles", "Véhicules"),
            FeatureNode("routing.map", "Carte"),
            FeatureNode("routing.trip_history", "Historique"),
        ),
    )

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION: Settings
    // ─────────────────────────────────────────────────────────────────────────
    val Settings = FeatureNode(
        id = "settings",
        title = "Paramètres",
        description = "Apparence, langue, notifications, synchronisation, sécurité.",
        requirement = AccessRequirement.None,
        children = listOf(
            FeatureNode("settings.appearance", "Apparence"),
            FeatureNode("settings.language", "Langue"),
            FeatureNode("settings.notifications", "Notifications"),
            FeatureNode("settings.sync", "Synchronisation"),
            FeatureNode("settings.security", "Sécurité"),
            FeatureNode("settings.about", "À propos"),
            FeatureNode(
                id = "settings.manage",
                title = "Gérer les paramètres",
                requirement = AccessRequirement.require(Permission.ManageSettings),
            ),
            FeatureNode(
                id = "settings.tenants",
                title = "Gérer les tenants",
                requirement = AccessRequirement.require(Permission.ManageTenants),
            ),
        ),
    )

    // ─────────────────────────────────────────────────────────────────────────
    // PERMANENTLY DISABLED FEATURES
    //
    // These nodes are kept in the registry so the UI can render them greyed-out
    // with a "removed" / "desktop-only" badge. Per the user's instruction:
    // disabled features should be visible, not hidden.
    // ─────────────────────────────────────────────────────────────────────────
    val RemovedAiAssistant = FeatureNode(
        id = "ai.assistant",
        title = "Assistant IA",
        description = "Assistant conversationnel — retiré de la version actuelle.",
        permanent = PermanentState.Removed,
    )

    val RemovedReportNarrative = FeatureNode(
        id = "ai.report_narrative",
        title = "Narratif de bulletin",
        description = "Génération automatique de commentaires de bulletins — retirée.",
        permanent = PermanentState.Removed,
    )

    val RemovedExpenseAnomaly = FeatureNode(
        id = "ai.expense_anomaly",
        title = "Détection d'anomalies",
        description = "Détection IA des dépenses anormales — retirée.",
        permanent = PermanentState.Removed,
    )

    /** Desktop-only features — visible but locked on mobile. */
    val DesktopOnlyDagEditor = FeatureNode(
        id = "workflows.dag_editor",
        title = "Éditeur de workflows (DAG)",
        description = "Éditeur visuel de workflows — disponible sur le terminal de bureau.",
        permanent = PermanentState.DesktopOnly,
    )

    val DesktopOnlyExcelImport = FeatureNode(
        id = "data.excel_import",
        title = "Import Excel en masse",
        description = "Import .xlsx des élèves — disponible sur le terminal de bureau.",
        permanent = PermanentState.DesktopOnly,
    )

    val DesktopOnlyBackup = FeatureNode(
        id = "system.backup",
        title = "Sauvegarde locale",
        description = "Sauvegarde AES-256 — interdite sur mobile (plan §13.05).",
        permanent = PermanentState.DesktopOnly,
    )

    /** The flat list of all permanently-disabled nodes (for the "locked features" list). */
    val PermanentlyDisabled: List<FeatureNode> = listOf(
        RemovedAiAssistant,
        RemovedReportNarrative,
        RemovedExpenseAnomaly,
        DesktopOnlyDagEditor,
        DesktopOnlyExcelImport,
        DesktopOnlyBackup,
    )

    /** The flat list of top-level sections (for the bottom-nav / drawer). */
    val Sections: List<FeatureNode> = listOf(
        Dashboard,
        Crm,
        Academics,
        Financials,
        Personnel,
        Routing,
        Settings,
    )

    /** Walk the entire tree. */
    fun all(): Sequence<FeatureNode> = sequence {
        Sections.forEach { yieldAll(it.walk()) }
        yieldAll(PermanentlyDisabled)
    }

    /** Find a node by id anywhere in the tree. */
    fun find(id: String): FeatureNode? = all().firstOrNull { it.id == id }
}
