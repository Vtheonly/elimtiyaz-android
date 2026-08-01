# El-Imtiyaz Desktop — Complete Reference for Mobile Rebuild

> **Repo:** `/home/z/my-project/repos/desktop/el-imtiyaz`
> **Stack:** Electron 33 · Vite 6 · React 18 · TypeScript 5.7 · Supabase (PostgreSQL + Auth + Storage + Edge Functions + Realtime)
> **Currency:** DZD (Algerian Dinar), formatted `12 500 DZD` (`fr-FR` grouping, non-breaking space, no decimals)
> **Locales:** `fr` (primary), `ar` (RTL), `en` (reserved)
> **Scale target:** ~5,000 users / ~300 DAU / ~50 peak concurrent
> **Iteration at time of writing:** 16 (1,180 passing tests)

This document is the master/source-of-truth reference for every business rule, formula, schema, workflow, and UI feature implemented in the desktop app. The mobile team should treat this as the spec to mirror or selectively rebuild against.

---

## 1. Architecture Overview

### 1.1 Folder Layout

```
el-imtiyaz/
├── electron/                          Electron main + preload + IPC handlers
│   ├── main.ts                        BrowserWindow, lifecycle, sets userData
│   ├── preload.ts                     Exposes window.elImtiyaz API surface
│   └── ipc-handlers.ts                config:read/write/delete, app:restart
├── src/
│   ├── main.tsx                       React entry — mounts <App/>
│   ├── app/                           App shell + React providers
│   │   ├── app.tsx                    Root: AuthProvider → RepositoryProvider → SyncProvider → ModalProvider → ToastProvider → AppShell
│   │   ├── app-shell.tsx              Sidebar + Topbar + <Routes> after auth
│   │   ├── splash-gate.tsx            Particle splash → login → shell
│   │   └── providers/
│   │       ├── auth-provider.tsx      Session state, signIn/signOut/changePassword
│   │       ├── repository-provider.tsx DI seam — wires mock or supabase repos
│   │       ├── sync-provider.tsx      SyncService context (status snapshot + actions)
│   │       ├── modal-provider.tsx     UnifiedModal host (modal queue)
│   │       ├── toast-provider.tsx     Toast viewport
│   │       └── user-preferences-provider.tsx  Locale, theme, density
│   ├── core/                          Pure, framework-agnostic primitives
│   │   ├── result.ts                  Result<T, AppError> discriminated union
│   │   ├── app-error.ts               AppError builders (network, validation, etc.)
│   │   ├── logger.ts                  Structured 6-level logger with PII redaction
│   │   ├── audit-actions.ts           Wire-protocol action codes (parent.create, payment.collect, etc.)
│   │   ├── format/
│   │   │   ├── currency.ts            formatDzd, formatDzdPlain, parseDzd
│   │   │   ├── date.ts                formatDate, formatRelative, currentAcademicYear
│   │   │   └── id.ts                  parentCode, studentCode, receiptCode, activationCode
│   │   └── rbac/
│   │       ├── roles.ts               11-role enum (super_admin, financial_officer, teacher, support_staff, manager, buyer, driver, warehouse_worker, worker, parent, student)
│   │       ├── permissions.ts         ~56 atomic permissions + DEFAULT_ROLE_PERMISSIONS
│   │       ├── access-requirement.ts  AccessRequirement DSL (empty | permanent | permission | anyOf | allOf | role)
│   │       ├── access-state.ts        AccessState (enabled | disabled | hidden)
│   │       ├── session.ts             Session interface, can(), hasRole(), isExpired()
│   │       ├── feature-registry.ts    Canonical FeatureNode tree (Dashboard, Crm, Academics, Financials, Personnel, WorkflowAutomation, Routing, Settings)
│   │       └── feature-gate.ts        Pure evaluate(requirement, ctx) → AccessState
│   ├── domain/                        Pure business model (no I/O, no React)
│   │   ├── model/                     14 entity modules (parent, student, academic, payment, expense, ledger, pricing, personnel, workforce, operations-workforce, operations, audit, backup, calendar, ai, workflow)
│   │   ├── repository/
│   │   │   ├── repository.ts          23 repository contracts (Auth, Parent, Student, Class, Subject, Grade, Attendance, Homework, Payment, Installment, Debt, Expense, Personnel, Releve, Audit, Notification, Dashboard, Pricing, Ledger, Workflow, WorkflowRun, Backup, AIConfig, Calendar, OverdueAlertGenerator)
│   │   │   ├── workforce-repository.ts 9 workforce repository contracts
│   │   │   └── operations-repository.ts 5 operations repository contracts
│   │   ├── kahn.ts                    Kahn's algorithm — DAG cycle detection (workflow editor)
│   │   ├── pii-mask.ts                Reversible PII masking (phone/email/IBAN/NN/names → placeholders)
│   │   └── reconcile.ts               Ledger reconciliation engine (8 integrity checks)
│   ├── infrastructure/                Adapters implementing domain contracts
│   │   ├── mock/                      In-memory reactive mock (seed data + repositories for ALL 37 contracts)
│   │   ├── supabase/                  Supabase adapter (auth + approvals + types — other repos fall back to mock)
│   │   ├── excel/
│   │   │   ├── import-engine/         Schema-driven Excel importer (4 schemas: ETAT, DEVIS, BON, REF)
│   │   │   ├── export-engine.ts       ExcelJS-based XLSX export
│   │   │   └── reports.ts             Pre-built report generators (revenue, debt, roster)
│   │   ├── sync/                      Offline-first sync queue (IndexedDB-backed, mock-data exclusion)
│   │   ├── backup/                    AES-256-GCM backup vault (IndexedDB, 365-day retention)
│   │   ├── ai/                        LLM adapter (mock + future Groq/OpenRouter)
│   │   ├── receipt-pdf.ts             pdf-lib receipt + statement generator
│   │   └── system-config.ts           Database-backed settings reader
│   ├── shared/                        Reusable presentation layer
│   │   ├── ui/                        18 shadcn-style primitives (button, card, input, select, dropdown-menu, modal-host, etc.)
│   │   ├── layout/                    sidebar, topbar, page-tabs, page-header, modal-host, gated-content, state-views
│   │   ├── hooks/                     use-observable, use-debounce
│   │   ├── search-index.ts            Command-palette fuzzy search
│   │   └── particle-engine/           Canvas particle physics (splash screen)
│   ├── features/                      10 feature hubs (one folder each)
│   │   ├── auth/                      splash-screen + login-screen
│   │   ├── dashboard/                 dashboard-page + calendar + alert modals + see-details + academic-year-selector
│   │   ├── crm/                       crm-page + parent-detail-drawer + student-detail-drawer + batch-registration-modal + excel-import-modal
│   │   ├── academics/                 academics-page + class-detail-page + roll-call-screen + grade-entry-screen + homework + narrative-generator
│   │   ├── financials/                financials-page + counter-payment-modal + installment-schedule-tab + expense-detail-drawer + receipts-tab + anomaly-explainer-modal
│   │   ├── personnel/                 personnel-page + dashboards/* + onboarding/wizard + management/* + releve-tab + workflow-monitor-tab
│   │   ├── workflow/                  workflow-page + dag-canvas + node-palette + run-detail-drawer
│   │   ├── settings/                  settings-page + 10 tab components (general, pricing, audit, rbac, approvals, configuration, sync, ai, backup, locked)
│   │   ├── profile/                   profile-page + change-password-modal
│   │   └── routing/                   routing-page (driver mode — stubbed)
│   └── i18n/                          fr.ts, ar.ts, en.ts (reserved) + i18n.ts + language-switcher
├── supabase/
│   ├── migrations/                    24 SQL migration files
│   ├── functions/                     11 Edge Functions (Deno)
│   └── docs/                          BACKUP_AND_SYNC.md, DEPLOYMENT.md
├── docs/                              18 markdown docs (DATABASE_SCHEMA, AUTHENTICATION_SETUP, EDGE_FUNCTIONS, ENVIRONMENT_VARIABLES, BACKEND_SETUP_GUIDE, STORAGE_SETUP, DEPLOYMENT, QUICKSTART, BACKUP_AND_SYNC, ITERATION-1 through ITERATION-16)
└── worklog.md                         Running work log of every iteration
```

### 1.2 Layering

Strict 5-layer architecture (no layer reaches across more than one layer below):

```
┌─────────────────────────────────────────────────────────────┐
│  features/ (React pages + modals — depends on shared, app,  │
│             domain, infrastructure via DI)                  │
├─────────────────────────────────────────────────────────────┤
│  app/providers (React contexts — depends on infrastructure) │
├─────────────────────────────────────────────────────────────┤
│  infrastructure (mock + supabase + sync + backup + ai —     │
│                  implements domain/repository contracts)    │
├─────────────────────────────────────────────────────────────┤
│  domain (pure business model — entities, formulas, repos)   │
├─────────────────────────────────────────────────────────────┤
│  core (Result<T>, AppError, RBAC, formatters, logger, audit)│
└─────────────────────────────────────────────────────────────┘
```

### 1.3 Dependency Injection

There is **no DI framework**. The pattern is a React context + factory:

```ts
// src/app/providers/repository-provider.tsx
export interface Repositories {
  readonly auth: AuthRepository;
  readonly parents: ParentRepository;
  readonly students: StudentRepository;
  // … 34 more repositories
}

export const mockRepositories: Repositories = { /* … */ };

export function selectDefaultRepositories(): Repositories {
  if (!useSupabase || !isSupabaseConfigured()) return mockRepositories;
  try { return getSupabaseRepositories(); }
  catch { return mockRepositories; } // graceful fallback
}

export function useRepositories(): Repositories {
  return useContext(RepositoryContext) ?? mockRepositories;
}
```

`getSupabaseRepositories()` starts from `mockRepositories` and overrides only `auth` + `approvals`. All other repositories stay on the mock layer until individually ported. This allows incremental migration without breaking the app.

### 1.4 State Management

- **Server state:** Supabase Realtime subscriptions wrapped in an `Observable<T>` pattern (mock uses a `SubjectBehavior`).
- **Local UI state:** `useState` + `useReducer`. No Redux/Zustand.
- **Async state:** `useObservable(() => repos.X.observe(), deps)` hook subscribes to a repository's reactive stream and re-renders on changes.
- **Session:** persisted to `localStorage["el-imtiyaz.session"]` (serialized Session object). Cleared on sign-out.
- **TanStack Query 5:** wired but not heavily used (mock layer is synchronous).

### 1.5 Routing

`HashRouter` (Electron-safe — no server required). Routes:

| Path | Component | Gate |
|---|---|---|
| `/` (or `/login`) | `LoginScreen` | unauthenticated |
| `/` | `DashboardPage` | role ∈ {SuperAdmin, FinancialOfficer, SupportStaff, Manager} — others redirect to `/personnel` |
| `/crm` | `CrmPage` | `view_roster` permission |
| `/academics` | `AcademicsPage` | `view_academics` |
| `/academics/class/:classId` | `ClassDetailPage` | `view_academics` |
| `/academics/class/:classId/roll-call` | `RollCallScreen` | `roll_call` |
| `/academics/class/:classId/grades/:subjectId` | `GradeEntryScreen` | `enter_grades` |
| `/financials` | `FinancialsPage` | `view_financials` |
| `/personnel` | `PersonnelPage` | `view_personnel` (every staff role) |
| `/workflow` | `WorkflowPage` | `manage_workflows` OR `view_workflow_runs` |
| `/routing` | `RoutingPage` | `access_driver_mode` (drivers only — currently stubbed) |
| `/settings` | `SettingsPage` | `manage_settings` OR `view_audit_log` OR `manage_backups` OR `manage_ai_config` |
| `/profile` | `ProfilePage` | authenticated |
| `*` | redirect to `/` | — |

### 1.6 Sync Model

Desktop is **online-first** with a sync queue (mobile is offline-first with Room cache):

1. Every Excel-imported mutation is also enqueued via `useSyncActions().enqueue({entity, operation, payload, isMock})`.
2. `SyncService` (singleton) persists the queue to IndexedDB (`el-imtiyaz-sync` DB).
3. `OnlineDetector` polls `https://www.google.com/generate_204` (HEAD, no-cors) every 30s when online, 120s when offline.
4. On online transition or new entry: drain pending queue via `defaultPushHandler` → upserts into `sync_queue` table on Supabase.
5. Retry with exponential backoff (1s × 2^attempts, max 5 attempts → marked `failed`).
6. **Mock-data invariant:** every entry carries `isMock: boolean`; if true, status auto-set to `skipped_mock` and NEVER pushed (defense-in-depth: re-checked at drain time).

---

## 2. Domain Model

Every entity is an **immutable TypeScript interface** (readonly fields). Mutations return new instances. The complete entity catalog:

### 2.1 CRM Entities

#### `Parent` (`src/domain/model/parent.ts`)
```ts
interface Parent {
  id, tenantId, code (PAR-2025-A4F9),
  firstName, lastName,
  gender: "male" | "female" | "unspecified",
  phone, whatsapp, email, occupation, address,
  cityTier: "t1" | "t2" | "t3" | null,                    // legacy
  transportDestination: TransportDestination | null,       // canonical
  preferredLanguage: "fr" | "ar" | "en",
  avatarUrl, createdAt, updatedAt
}

type TransportDestination =
  | "ville_boumerdes"
  | "tidjelabine_sahel_figuier_corso"
  | "boudouaou_thenia_zemmouri"
  | "autres";
```

#### `Student` (`src/domain/model/student.ts`)
```ts
interface Student {
  id, tenantId, code (ELV-2025-001234),
  parentId,                                            // NOT NULL — parent-first rule
  firstName, lastName, gender, birthDate, enrollmentDate,
  level: AcademicLevel,                                // "primaire" | "cem" | "lycee"
  gradeYear: number,                                   // 1..5/4/3
  gradeLevel: GradeLevel,                              // 14-value canonical enum
  classId, photoUrl, medicalNotes, transportTier,
  status: "active" | "graduated" | "transferred" | "suspended" | "withdrawn",
  createdAt, updatedAt
}

type GradeLevel =
  | "prescolaire_1" | "prescolaire_2"
  | "1ap" | "2ap" | "3ap" | "4ap" | "5ap"          // Primary
  | "1am" | "2am" | "3am" | "4am"                  // Middle (CEM)
  | "1ere_annee" | "2eme_annee" | "3eme_annee";   // High (Lycée)
```

Relationships:
- Parent 1 → N Students (no cap — the legacy 4-child limit was removed per plan §04.02)
- Student `parentId` is NOT NULL FK (parent-first rule, plan §04.01)
- `parent_student_links` junction table supports multi-guardian families (optional)

### 2.2 Academic Entities (`src/domain/model/academic.ts`)

```ts
interface AcademicClass { id, name, level, gradeYear, homeroomTeacherId, room, capacity, enrolledCount, academicYear }
interface Subject { id, name, nameAr, code, level, coefficient, isExtracurricular, passingGrade }
interface ClassSubject { id, classId, subjectId, teacherId, weeklyHours, coefficient }
interface Assessment { id, studentId, subjectId, classId, term: "T1"|"T2"|"T3", academicYear, devoir1, devoir2, examen, subjectAverage, coefficient, enteredBy, enteredAt }
interface AttendanceRecord { id, studentId, classId, date, session: "morning"|"afternoon"|"both", status: "present"|"absent_excused"|"absent_unexcused"|"late", note, recordedBy, recordedAt, syncedAt }
interface Homework { id, classId, subjectId, teacherId, title, description, dueDate, attachments, academicYear, pushedAt, acknowledgedCount }
interface AcademicHistoryEntry { academicYear, level, gradeYear, classId, gpa, rank, decision: "promoted"|"repeated"|"graduated"|"transferred", narrative }
```

### 2.3 Financial Entities

#### `Payment`, `Installment`, `AccountAdjustment`, `Receipt`, `DebtSummary`, `ParentFinancialProfile` (`src/domain/model/payment.ts`)

```ts
type PaymentMethod = "cash" | "check" | "transfer";
type PaymentStatus = "pending" | "partial" | "paid" | "overdue" | "refunded" | "cancelled";
type PaymentCategory = "tuition" | "transport" | "canteen" | "uniform" | "books" | "extracurricular" | "other";

interface Payment {
  id, tenantId, receiptNumber (REC-2025-000123),
  parentId, studentId,
  amount, method, status, category,
  installmentId, proofUrl, notes,
  collectedBy, collectedAt, createdAt, updatedAt
}

interface Installment {
  id, parentId, studentId, category, label ("Tranche 1/2/3"),
  amountDue, amountPaid, dueDate, paidDate, status,
  academicCycle?: "primaire" | "cem" | "lycee",
  customSchedule?: boolean,
  customScheduleNote?: string | null
}

interface AccountAdjustment { id, parentId, amount (+credit/-debit), reason, approvedBy, approvedAt, receiptRef }

interface ParentFinancialProfile {
  parentId, parentName,
  totalDue, totalPaid, totalOutstanding, overdueAmount,
  installments: Installment[],
  recentPayments: Payment[],
  adjustments: AccountAdjustment[]
}

interface DebtSummary {
  parentId, parentName, parentPhone, studentCount,
  outstandingAmount, daysOverdue, bucket: "0_30"|"31_60"|"61_90"|"91_180"|"180_plus"
}

interface Receipt { id, paymentId, receiptNumber, pdfUrl, generatedAt, generatedBy }
```

#### `LedgerEntry`, `AccountBalance`, `ParentLedgerSummary` (`src/domain/model/ledger.ts`)

```ts
type LedgerEntryType = "charge" | "payment" | "adjustment" | "refund" | "reversal" | "transfer";
type LedgerSourceType = "installment" | "payment" | "expense" | "adjustment" | "refund" | "bulk_import" | "manual_entry";

interface LedgerEntry {
  id ("led-{ISO}-{rand}"), tenantId, accountId, parentId, studentId,
  category: PaymentCategory,
  amount: number,                                       // SIGNED: + = debit (charge), - = credit (payment)
  type: LedgerEntryType,
  sourceType: LedgerSourceType, sourceId,
  method: PaymentMethod | null, receiptNumber: string | null,
  paymentStatus: PaymentStatus | null,
  reversesId: string | null,                            // for reversal entries
  description: string,                                  // never blank
  actorId, actorName, at: string,                       // ISO timestamp
  metadata: Record<string, string | number | boolean | null>
}

// Account ID is DERIVED, never stored as a separate entity:
//   parent:{parentId}:category:{category}[:student:{studentId}]
function deriveAccountId(parentId, category, studentId = null): string

interface AccountBalance {
  accountId, parentId, studentId, category,
  balance,                                              // sum of all entries
  totalCharged, totalPaid, totalAdjusted, totalRefunded,
  totalCleared, totalPending,
  entryCount, lastActivityAt
}

interface ParentLedgerSummary {
  parentId, parentName,
  totalOutstanding, totalOverdue,
  totalCharged, totalPaid, totalCleared, totalPending, totalAdjusted, totalRefunded,
  accounts: AccountBalance[], entryCount, lastActivityAt
}
```

#### `PricingConfig` (`src/domain/model/pricing.ts`)

```ts
type PricingCategory = "tuition" | "transport" | "registration" | "monthly" | "discount" | "penalty" | "additional" | "complementary";
type DiscountType = "percentage" | "fixed_amount";

type DiscountCode =
  | "passage_palier"        // −10,000 DA (fixed)
  | "seniority_5y"          // −5% (percentage)
  | "full_annual"           // −10% (percentage, before June 30)
  | "highest_average"       // −10% (percentage)
  | "sibling_fixed"         // −5,000 DA per additional child (fixed)
  | "sibling_10"            // legacy 2nd child −10%
  | "sibling_15"            // legacy 3rd+ child −15%
  | "early_bird"            // legacy −5%
  | "custom";

interface TuitionPricing { annualAmount: number; installments: readonly [number, number, number] }
interface TransportPricing { annualAmount: number; installments: readonly [number, number, number] }
interface ComplementaryServicePricing { semesterAmount: number; annualAmount: number }

interface PricingConfig {
  tuitionByGradeLevel: Record<GradeLevel, TuitionPricing>,        // 14 entries
  transportByDestination: Record<TransportDestination, TransportPricing>, // 4 entries
  registrationFee: number,
  monthlyByLevel: Partial<Record<AcademicLevel, number>>,
  latePenaltyPerDay: number,
  discounts: PricingEntry[],
  additionalServices: PricingEntry[],
  complementaryServices: (PricingEntry & ComplementaryServicePricing)[],
  secondApronFee: number                                            // 2,000 DA
}
```

### 2.4 Expense Entities (`src/domain/model/expense.ts`)

```ts
type ExpenseStatus = "draft" | "submitted" | "approved" | "rejected" | "disbursed" | "settled";
type ExpenseCategory = "utilities" | "supplies" | "maintenance" | "transport" | "event" | "salary" | "tax" | "rent" | "other";

interface Expense {
  id, tenantId, requestCode (EXP-2026-001234),
  title, description, amount, category, payee,
  status, submittedBy, submittedAt,
  approvedBy, approvedAt, approvalNote,
  disbursedBy, disbursedAt,
  proofUrl, proofUploadedBy, proofUploadedAt,
  anomalyScore (0..1), anomalyNote
}
```

### 2.5 Personnel / HR Entities (`src/domain/model/personnel.ts`)

```ts
type StaffCategory = "teacher" | "administration" | "support" | "maintenance" | "driver" | "buyer" | "warehouse" | "worker";
type PersonnelStatus = "active" | "on_leave" | "suspended" | "terminated" | "archived";
type ReleveActivity = "course" | "meeting" | "supervision" | "correction" | "task" | "delivery" | "warehouse" | "other";
type PayrollMethod = "cash" | "bank_transfer" | "check" | "mobile_money";

interface Personnel {
  id, tenantId, userId,                                  // links to auth user
  firstName, lastName, staffCategory,
  roleId: Role,                                          // RBAC role
  departmentId, supervisorId, position,
  phone, email, address, hireDate, terminationDate,
  salary, paymentMethod, bankAccount,
  weeklyHoursTarget, weeklyHoursLogged,
  avatarUrl, status,
  bonuses: BonusAdjustment[],
  documents: PersonnelDocument[],
  notes: { id, authorId, authorName, body, createdAt }[],
  emergencyContact: { name, phone, relation } | null,
  dateOfBirth, nationalId
}

interface ReleveEntry {
  id, personnelId, personnelName,
  date, hoursIn, hoursOut, activity,
  classId, subjectId, taskId?,
  recordedAt
}
```

### 2.6 Workforce Entities (`src/domain/model/workforce.ts`)

`Department`, `Shift`, `Schedule`, `Task`, `TaskAttachment`, `TaskComment`, `AttendanceEvent`, `LeaveRequest`, `PerformanceReview`, `ChatChannel`, `ChatMessage`, `OnboardingState`, `OnboardingData`.

### 2.7 Operations Entities (`src/domain/model/operations-workforce.ts`)

`Supplier`, `PurchaseRequest` (+ `PurchaseRequestLine`), `Delivery` (+ `DeliveryStop`), `InventoryItem`, `InventoryTransaction`, `PendingReceipt`, `PendingDispatch`.

### 2.8 Cross-cutting Entities

- `AuditEntry` (`src/domain/model/audit.ts`) — `{ id, tenantId, action, entityType, entityId, actorId, actorName, diff, note, ipAddress, userAgent, at }`
- `AppNotification`, `DashboardKpi`, `RevenuePoint`, `DebtByAgingBucket`, `DemographicSlice` (`src/domain/model/operations.ts`)
- `CalendarEvent` union (`src/domain/model/calendar.ts`) — 7 kinds: `payment_received`, `audit_log`, `expense_event`, `follow_up_call`, `reminder`, `meeting`, `custom`
- `BackupArchive`, `BackupRestoreResult` (`src/domain/model/backup.ts`)
- `Workflow`, `WorkflowNode`, `WorkflowEdge`, `WorkflowRun`, `WorkflowNodeResult` (`src/domain/model/workflow.ts`)
- `AIProviderConfig`, `AIRequest`, `AIResponse`, `NarrativeRequest`, `DraftingRequest`, `AnomalyExplanation` (`src/domain/model/ai.ts`)

---

## 3. Database Schema (24 Migrations)

Complete multi-tenant schema. Every tenant-scoped table has `tenant_id` NOT NULL FK to `tenants(id)` ON DELETE CASCADE. RLS is enabled and **forced** on every tenant-scoped table.

### 3.1 Migration Inventory

| # | File | Tables created | Key features |
|---|---|---|---|
| 0001 | `extensions.sql` | (none — schema setup) | pgcrypto, pgjwt, uuid-ossp, pg_trgm, btree_gist, pg_stat_statements + `public.gen_uuid()` wrapper |
| 0002 | `tenants_and_users.sql` | `tenants`, `user_profiles`, `account_approval_requests`, `sessions` | Trigger `handle_new_auth_user()` mirrors auth.users → user_profiles + auto-creates approval request. `touch_updated_at()` universal trigger. |
| 0003 | `rbac.sql` | `roles`, `permissions`, `role_permissions`, `tenant_role_overrides`, `role_assignments` | Helper SQL functions: `current_tenant_id()`, `current_user_profile_id()`, `current_user_roles()`, `current_user_permissions()` (with deny-wins overrides), `has_permission(code)`, `has_role(code)`, `has_any_role(text[])` |
| 0004 | `academic_structure.sql` | `academic_years`, `academic_levels`, `classes`, `subjects`, `class_subjects`, `assessments`, `grades`, `attendance_records`, `homework_assignments`, `academic_history` | Trigger `compute_grade_subject_average()` auto-computes `(D1+D2+2·Examen)/4` on grade insert/update |
| 0005 | `crm.sql` | `parents`, `students`, `parent_student_links`, `activation_codes`, `student_documents` | Functions: `generate_activation_code(tenant_id)`, `bind_activation_code(tenant_id, code, auth_user_id)`, `approve_account_request(...)`, `reject_account_request(...)` |
| 0006 | `pricing.sql` | `pricing_configs`, `grade_level_tuition`, `transport_destinations`, `complementary_services`, `additional_services`, `discounts`, `discount_applications` | CHECK constraint: tranches must sum to annual amount (±0.01). View: `active_pricing_config` |
| 0007 | `financial.sql` | `service_enrollments`, `invoices`, `installments`, `payments`, `account_adjustments`, `receipts`, `ledger_entries` | Triggers: `enforce_payment_proof()` (cash→paid auto, check/transfer→pending, proof mandatory), `update_installment_status()` (auto unpaid/partial/paid/overdue from amount_paid + due_date). Functions: `compute_account_balance(account_id)`, `compute_parent_balance(parent_id)`, `compute_parent_outstanding(parent_id)`, `compute_overdue_amount(parent_id, as_of)` |
| 0008 | `expenses.sql` | `expense_categories`, `expense_tickets`, `expense_state_transitions` | Trigger `enforce_expense_workflow_rules()`: no self-approval, receipt mandatory before settlement, final_spent_amount required, rejection reason required. Trigger `record_expense_state_transition()` auto-writes audit row on status change |
| 0009 | `attendance_hr.sql` | `personnel`, `releve_entries` | Trigger `prevent_self_releve_entry()` — a teacher CANNOT record their own Releve entry (plan §09.05) |
| 0010 | `workforce.sql` | `departments`, `shifts`, `schedules`, `tasks`, `task_comments`, `task_attachments`, `workforce_attendance_events`, `leave_requests`, `performance_reviews`, `chat_channels`, `chat_messages`, `onboarding_states` | GIN indexes on jsonb (`assignee_ids`, `member_ids`, `read_by`, `attachments`, `data_json`). Generated column `duration_minutes` on releve_entries |
| 0011 | `operations.sql` | `suppliers`, `purchase_requests`, `deliveries`, `inventory_items`, `inventory_transactions`, `pending_receipts`, `pending_dispatches` | Check `quantity_reserved <= quantity_on_hand` on inventory. Trigram indexes on supplier name, SKU |
| 0012 | `workflow.sql` | `workflows`, `workflow_runs`, `workflow_audit_links`, `ai_provider_configs`, `ai_request_logs` | `api_key_encrypted` is AES-256-GCM ciphertext (NEVER plaintext). Partial index on `workflows` WHERE `status='published'` |
| 0013 | `calendar_notifications_backup.sql` | `calendar_events`, `notifications`, `backup_archives` | Backup metadata only — ciphertext lives in IndexedDB per plan §13.03 |
| 0014 | `audit.sql` | `audit_logs` | Append-only: triggers `audit_logs_block_update` and `audit_logs_block_delete` raise exception. Function `write_audit_log(...)` canonical entry point. Views: `audit_log_with_actor`, `audit_log_by_entity` |
| 0018 | `storage.sql` | (10 storage buckets + RLS policies on `storage.objects`) | Buckets: `payment-proofs`, `expense-receipts`, `receipts`, `student-documents`, `homework-attachments`, `task-attachments`, `chat-attachments`, `tenant-assets`, `ai-reports`, `import-reports`. All private — signed URLs only. Folder structure `<tenant_id>/<entity_id>/<filename>` enforces tenant isolation |
| 0019 | `rls_policies.sql` | (60+ policies) | Every tenant-scoped table gets 4 policies: SELECT (tenant + role/permission + deleted_at IS NULL), INSERT, UPDATE, DELETE (super_admin only). Special: `user_profiles` allows reading own row; `audit_logs` has only INSERT (UPDATE/DELETE blocked by trigger); `ledger_entries` is immutable |
| 0020 | `indexes.sql` | (50+ indexes) | BRIN on time-series tables (audit_logs, ledger_entries, payments, chat_messages, releve_entries); partial indexes (`WHERE deleted_at IS NULL`, `WHERE status='pending'`); covering indexes with INCLUDE; GIN on jsonb + trigram; expression indexes (`LOWER(email)`) |
| 0021 | `views.sql` | 5 materialized + 5 regular views | Materialized: `mv_dashboard_kpis`, `mv_debt_aging`, `mv_top_debtors`, `mv_revenue_by_month`, `mv_grade_summary` (each with UNIQUE index for concurrent refresh). Regular: `vw_revenue_by_category`, `vw_student_roster`, `vw_personnel_directory`, etc. |
| 0022 | `functions.sql` | 14 SECURITY DEFINER RPC functions | `batch_register_family(tenant_id, parent_jsonb, students_jsonb, actor, code)` — atomic registration. `collect_payment(...)` — atomic payment + ledger + receipt + audit. `refund_payment(...)`. `approve_expense(...)`, `settle_expense(...)`. `record_roll_call(...)`. `compute_gpa(student_id, term, year)`. `promote_students(year)`. `run_overdue_scan(tenant_id, as_of)`. `purge_expired_backups()`. `search_entities(query)`. `get_parent_summary(parent_id)`. `refresh_all_materialized_views()`. `expire_pending_approvals()` |
| 0023 | `seed.sql` | (reference data) | 1 default tenant (`elimtiyaz-boumerdes`), 11 roles, 56 permissions, full role-permission matrix, 14 academic levels, 2026-2027 academic year, 9 expense categories, 4 default departments, full pricing config (14 tuitions + 4 transports + complementary services + 5 canonical discounts) |
| 0024 | `system_settings.sql` | `system_settings` | 40+ settings across 8 categories: `connection`, `ai`, `email`, `push`, `storage`, `backup`, `system`, `feature_flags`. Sensitive values stored as AES-256-GCM ciphertext in `value_encrypted`. Functions: `get_setting`, `get_setting_text`, `get_setting_bool`, `upsert_setting`, `upsert_secret_setting` |

### 3.2 Key Table Schemas (most critical)

#### `parents` (master billing entity)
```sql
create table public.parents (
  id              uuid primary key default public.gen_uuid(),
  tenant_id       uuid not null references public.tenants(id) on delete cascade,
  parent_code     text not null,                              -- 'PAR-2026-A4F9'
  first_name      text not null,
  last_name       text not null,
  primary_phone   text not null,
  secondary_phone text,
  email           text,
  national_id     text,                                       -- Algerian NN (10 digits)
  occupation, address, city, postal_code,
  relationship    text check (relationship in ('father','mother','guardian','other')),
  notes           text,
  is_active       boolean not null default true,
  is_financially_restricted boolean not null default false,
  auth_user_id    uuid,                                       -- bound on activation
  created_at, updated_at, deleted_at,
  unique (tenant_id, parent_code)
);
-- Unique partial indexes on email + national_id WHERE deleted_at IS NULL
-- GIN trigram index on (last_name, first_name) for fuzzy search
```

#### `payments` (money received)
```sql
create table public.payments (
  id                  uuid primary key default public.gen_uuid(),
  tenant_id           uuid not null references public.tenants(id) on delete cascade,
  payment_number      text not null,                          -- 'PAY-2026-001234'
  parent_id           uuid not null references public.parents(id) on delete restrict,
  student_id          uuid references public.students(id) on delete restrict,
  invoice_id          uuid references public.invoices(id) on delete set null,
  installment_id      uuid references public.installments(id) on delete set null,
  amount              numeric(10,2) not null check (amount > 0),
  method              text not null check (method in ('cash','check','transfer')),
  -- Method-specific fields:
  check_number        text,                                   -- required when method='check'
  check_bank_name     text,
  check_issue_date    date,
  check_clearance_date date,
  transfer_reference  text,                                   -- required when method='transfer'
  transfer_source_bank text,
  proof_path          text,                                   -- storage path; mandatory for check/transfer
  status              text not null check (status in ('paid','pending','unpaid','refunded','cancelled')),
  collected_at        timestamptz not null default now(),
  collected_by        uuid,                                   -- user_profiles.id (cashier)
  notes               text,
  reversal_of_payment_id uuid,                                -- set when this is a refund
  created_at, updated_at,
  unique (tenant_id, payment_number)
);
-- Trigger: enforce_payment_proof() BEFORE INSERT OR UPDATE
--   - check requires check_number + check_bank_name + proof_path
--   - transfer requires transfer_reference + proof_path
--   - status auto-set: cash→paid, check/transfer→pending (if not provided)
```

#### `ledger_entries` (IMMUTABLE accounting — single source of truth)
```sql
create table public.ledger_entries (
  id                  uuid primary key default public.gen_uuid(),
  tenant_id           uuid not null references public.tenants(id) on delete cascade,
  entry_number        text not null,                          -- 'LED-2026-001234'
  parent_id           uuid not null references public.parents(id) on delete restrict,
  student_id          uuid references public.students(id) on delete restrict,
  service_enrollment_id uuid,
  payment_id          uuid,
  adjustment_id       uuid,
  reverses_entry_id   uuid references public.ledger_entries(id) on delete set null,
  account_id          text not null,                          -- 'parent:{pid}:category:{cat}[:student:{sid}]'
  entry_type          text not null check (entry_type in ('charge','payment','adjustment','refund','reversal','transfer')),
  amount              numeric(12,2) not null check (amount <> 0),  -- SIGNED
  category            text not null,                          -- 'tuition', 'transport', etc.
  description         text,
  entry_date          timestamptz not null default now(),
  created_at          timestamptz not null default now(),
  unique (tenant_id, entry_number)
);
-- RLS: NO update, NO delete policies → immutable (RLS denies by default)
-- BRIN index on entry_date for time-series scans
```

#### `installments` (tranche-level billing)
```sql
create table public.installments (
  id                  uuid primary key default public.gen_uuid(),
  tenant_id           uuid not null,
  parent_id           uuid not null,
  student_id          uuid not null,
  service_enrollment_id uuid not null,
  invoice_id          uuid,
  tranche_number      integer not null check (tranche_number in (1, 2, 3)),
  amount_due          numeric(10,2) not null check (amount_due >= 0),
  amount_paid         numeric(10,2) not null default 0 check (amount_paid >= 0),
  due_date            date not null,
  paid_date           date,
  status              text not null default 'unpaid' check (status in ('unpaid','partial','paid','overdue')),
  academic_cycle      text check (academic_cycle in ('primaire','cem','lycee','prescolaire')),
  is_custom_schedule  boolean not null default false,
  custom_schedule_note text,
  created_at, updated_at
);
-- Trigger: update_installment_status() BEFORE INSERT OR UPDATE OF amount_paid, due_date
--   if amount_paid >= amount_due → 'paid' + set paid_date
--   elif amount_paid > 0 → 'partial'
--   else → 'unpaid' + clear paid_date
--   if due_date < current_date AND not fully paid → 'overdue'
```

#### `audit_logs` (append-only)
```sql
create table public.audit_logs (
  id              uuid primary key default public.gen_uuid(),
  tenant_id       uuid not null references public.tenants(id) on delete cascade,
  action          text not null,                              -- 'parent.create', 'payment.collect', etc.
  entity_type     text not null,
  entity_id       uuid,
  actor_id        uuid,                                       -- NULL only for system events
  actor_name      text,                                       -- denormalized
  actor_role      text,                                       -- role code at time of action
  session_id      uuid,
  before_json     jsonb,                                      -- NEVER truncated
  after_json      jsonb,                                      -- NEVER truncated
  note            text,
  ip_address      inet,
  user_agent      text,
  request_id      text,                                       -- correlation ID
  supersedes_id   uuid references public.audit_logs(id) on delete set null,
  occurred_at     timestamptz not null default now(),
  created_at      timestamptz not null default now()
);
-- Triggers: audit_logs_block_update + audit_logs_block_delete (raise exception)
-- Function: write_audit_log(tenant_id, action, entity_type, entity_id, actor_id, actor_name, ...) RETURNS uuid
-- Views: audit_log_with_actor, audit_log_by_entity
```

### 3.3 RLS Policy Pattern

Universal pattern for every tenant-scoped table:

```sql
-- 1. SELECT: tenant + role/permission + soft-delete filter
create policy <table>_select on public.<table>
  for select to authenticated
  using (tenant_id = public.current_tenant_id()
         AND <role/permission check>
         AND deleted_at IS NULL);

-- 2. INSERT: tenant + role/permission
create policy <table>_insert on public.<table>
  for insert to authenticated
  with check (tenant_id = public.current_tenant_id() AND <role check>);

-- 3. UPDATE: tenant + role/permission (both USING and WITH CHECK)
create policy <table>_update on public.<table>
  for update to authenticated
  using (tenant_id = public.current_tenant_id() AND <role check>)
  with check (tenant_id = public.current_tenant_id() AND <role check>);

-- 4. DELETE: super_admin only
create policy <table>_delete on public.<table>
  for delete to authenticated
  using (tenant_id = public.current_tenant_id() AND public.has_role('super_admin'));
```

Special cases:
- `tenants` (root — no `tenant_id` column): SuperAdmin-only writes; others read own row.
- `user_profiles`: a user can read/update their own row; admins see all in their tenant.
- `audit_logs`: append-only — no UPDATE or DELETE policy (trigger blocks them).
- `ledger_entries`: immutable — no UPDATE or DELETE policy.
- `parents/students/personnel`: soft-delete via `deleted_at IS NULL` filter on SELECT.

### 3.4 Key SQL Functions (security_definer)

```sql
-- Atomic batch registration (plan §04.03)
public.batch_register_family(
  p_tenant_id uuid, p_parent jsonb, p_students jsonb,
  p_actor_profile_id uuid, p_activation_code text default null
) returns table(parent_id uuid, student_ids uuid[])

-- Atomic payment collection (plan §07.05)
public.collect_payment(
  p_tenant_id uuid, p_parent_id uuid, p_student_id uuid,
  p_amount numeric, p_method text,
  p_invoice_id uuid default null, p_installment_id uuid default null,
  p_actor_profile_id uuid default null, p_notes text default null,
  p_check_number text default null, p_check_bank_name text default null,
  p_check_issue_date date default null, p_check_clearance_date date default null,
  p_transfer_reference text default null, p_transfer_source_bank text default null,
  p_proof_path text default null
) returns table(payment_id uuid, receipt_id uuid, new_installment_status text)

-- Atomic refund (reversal)
public.refund_payment(p_tenant_id, p_payment_id, p_actor_profile_id, p_reason)

-- Auto-compute GPA from grades
public.compute_gpa(p_student_id uuid, p_term int, p_academic_year_id uuid) returns numeric(5,2)

-- Promote students for new academic year (one-click batch)
public.promote_students(p_academic_year_id uuid, p_actor_profile_id uuid)

-- Daily overdue scan (called by cron Edge Function)
public.run_overdue_scan(p_tenant_id uuid, p_as_of date default current_date)

-- Purge backups past retention
public.purge_expired_backups()

-- Search across entities (parent/student/personnel)
public.search_entities(p_query text, p_limit int default 20)
```

---

## 4. Repository Contracts

37 repository interfaces across 3 files. Every method returns `Promise<Result<T>>` so failures are explicit in the type system. Live reads expose `Observable<T>` so React re-renders on backend changes.

### 4.1 Core Repositories (`src/domain/repository/repository.ts`)

```ts
interface AuthRepository {
  signIn(email, password): Promise<Result<Session>>;
  signOut(): Promise<Result<void>>;
  refreshSession(): Promise<Result<Session | null>>;
}

interface ParentRepository {
  observe(): Observable<Parent[]>;
  observeById(id): Observable<Parent | null>;
  search(query): Promise<Result<Parent[]>>;
  createParent(input): Promise<Result<Parent>>;
  updateParent(id, input): Promise<Result<Parent>>;
  deleteParent(id): Promise<Result<void>>;
}

interface StudentRepository {
  observe(): Observable<Student[]>;
  observeByParent(parentId): Observable<Student[]>;
  observeByClass(classId): Observable<Student[]>;
  observeById(id): Observable<Student | null>;
  search(query): Promise<Result<Student[]>>;
  createStudent(parentId, input): Promise<Result<Student>>;
  updateStudent(id, updates): Promise<Result<Student>>;
  deleteStudent(id): Promise<Result<void>>;
  batchRegister(input: BatchRegistrationInput): Promise<Result<BatchRegistrationResult>>;
  promote(studentIds, academicYear): Promise<Result<Student[]>>;
}

interface ClassRepository { observe, observeByLevel, observeById, createClass, updateClass, deleteClass }
interface SubjectRepository { observe, observeByLevel, observeByClass, assignSubjectToClass, removeSubjectFromClass, createSubject, updateSubject, archiveSubject }
interface GradeRepository { observeForStudent, observeForClass, enterGrade }
interface AttendanceRepository { observeByClass, observeByStudent, recordRollCall, alertAbsences }
interface HomeworkRepository { observeForClass, observeByTeacher, push }

interface PaymentRepository {
  observe(): Observable<Payment[]>;
  observeByParent(parentId): Observable<Payment[]>;
  observeByStudent(studentId): Observable<Payment[]>;
  observeById(id): Observable<Payment | null>;
  collect(input, collectedBy): Promise<Result<Payment>>;
  refund(id): Promise<Result<Payment>>;
  adjust(parentId, amount, reason, approvedBy): Promise<Result<AccountAdjustment>>;
  generateReceipt(paymentId, generatedBy): Promise<Result<Receipt>>;
}

interface InstallmentRepository {
  observeByParent, observeByStudent, observeById;
  markPaid(id, paymentId): Promise<Result<Installment>>;
  updateDueDate(input: UpdateInstallmentDueDateInput): Promise<Result<Installment>>;
  regenerateForCycle(parentId, cycle: AcademicCycle, actorId, actorName): Promise<Result<readonly Installment[]>>;
  findOverdue(now?): Promise<Result<readonly Installment[]>>;
}

interface DebtRepository { observeSummary, observeParentProfile, sendReminder }
interface ExpenseRepository { observe, observeByStatus, observeById, submit, approve, reject, disburse, settleProof }
interface PersonnelRepository { observe, observeByCategory, observeById, observeByUserId, createPersonnel, updatePersonnel, deletePersonnel }
interface ReleveRepository { observeByPersonnel, logEntry }
interface AuditRepository { query(filter), byEntity(type, id), recent(limit), log(input) }
interface NotificationRepository { observe, observeForSession(session), markRead, markAllRead, clear, dismiss, create, update }
interface DashboardRepository {
  kpis(): Promise<Result<DashboardKpi>>;
  revenueLast12Months(): Promise<Result<RevenuePoint[]>>;
  debtByAging(): Promise<Result<DebtByAgingBucket[]>>;
  demographics(): Promise<Result<{ grade, gender, age, capacity }>>;
  kpisForRange(academicYear, range?: DateRange): Promise<Result<DashboardKpi>>;
  revenueForRange(academicYear, range?): Promise<Result<RevenuePoint[]>>;
  debtByAgingForRange(academicYear, range?): Promise<Result<DebtByAgingBucket[]>>;
}

interface PricingRepository {
  observe(): Observable<PricingConfig>;
  updateRegistration(amount, updatedBy);
  updateMonthly(level, amount, updatedBy);
  updateLatePenalty(amountPerDay, updatedBy);
  addDiscount(input, updatedBy);
  removeDiscount(id, updatedBy);
  addAdditionalService(input, updatedBy);
  removeAdditionalService(id, updatedBy);
  // Iteration 6 granular pricing:
  updateTuitionForGradeLevel(gradeLevel, annualAmount, installments: [n,n,n], updatedBy);
  updateTransportForDestination(destination, annualAmount, installments: [n,n,n], updatedBy);
  updateSecondApronFee(amount, updatedBy);
  addComplementaryService(input, updatedBy);
  removeComplementaryService(id, updatedBy);
}

interface LedgerRepository {
  observe(): Observable<LedgerEntry[]>;
  observeByParent(parentId): Observable<LedgerEntry[]>;
  observeByAccount(accountId): Observable<LedgerEntry[]>;
  append(entry): Promise<Result<LedgerEntry>>;
  appendMany(entries): Promise<Result<readonly LedgerEntry[]>>;
  reverse(originalId, reason, actorId, actorName): Promise<Result<LedgerEntry>>;
  summary(parentId): Promise<Result<ParentLedgerSummary>>;          // computed via replay
  reconcile(): Promise<Result<ReconciliationReport>>;
}

interface WorkflowRepository { observe, observeById, createWorkflow, updateWorkflow, deleteWorkflow, deploy, execute }
interface WorkflowRunRepository { observe, observeByWorkflow, observeById, retryRun }
interface AIConfigRepository { observe, updateConfig, testProvider }
interface BackupRepository { observe, observeById, runBackup, restore, deleteArchive, purgeExpired, getEncryptionKey }
interface CalendarRepository { observeForDate(date), observeForMonth(yearMonth), create, update, delete }
interface OverdueAlertGenerator { run(now?): Promise<Result<readonly AppNotification[]>> } // idempotent
```

### 4.2 Workforce Repositories (`src/domain/repository/workforce-repository.ts`)

`DepartmentRepository`, `ShiftRepository`, `ScheduleRepository`, `TaskRepository` (with `addComment`, `addAttachment`, `reassign`), `AttendanceRepository` (workforce — clock in/out events), `LeaveRequestRepository`, `PerformanceReviewRepository`, `ChatRepository` (channels + messages + read receipts), `OnboardingRepository`.

### 4.3 Operations Repositories (`src/domain/repository/operations-repository.ts`)

`SupplierRepository`, `PurchaseRequestRepository`, `DeliveryRepository`, `InventoryRepository` (with `transact` and `scan`), `WarehouseTaskRepository` (pending receipts + dispatches).

---

## 5. Financial Engine — Exact Formulas

This is the most critical section. Every formula is implemented in `src/domain/model/payment.ts` (computational helpers) and `src/domain/model/ledger.ts` (ledger-based accounting). The SQL layer mirrors the same logic in `supabase/migrations/0007_financial.sql` and `0022_functions.sql`.

### 5.1 Single Source of Truth Principle

> **Every balance, debt, payment total, or remaining amount in the application is computed by replaying the ledger via one of the helpers below. Hardcoding the same formula in 2+ places is forbidden.** — `src/domain/model/payment.ts` JSDoc

### 5.2 Account ID Derivation

Account IDs are **derived, never stored** as separate entities:

```ts
// src/domain/model/ledger.ts
export function deriveAccountId(
  parentId: string,
  category: PaymentCategory,
  studentId: string | null = null,
): string {
  const parts = ["parent", parentId, "category", category];
  if (studentId) parts.push("student", studentId);
  return parts.join(":");
}
// Example: "parent:abc-123:category:tuition:student:def-456"
```

### 5.3 Account Balance Computation (Replay)

The single source of truth for any balance. Replays all ledger entries for an account, sorted by timestamp then ID for stability:

```ts
// src/domain/model/ledger.ts
export function computeAccountBalance(
  entries: readonly LedgerEntry[],
  accountId: string,
  now: Date = new Date(),
): AccountBalance {
  const relevant = entries
    .filter(e => e.accountId === accountId)
    .filter(e => new Date(e.at).getTime() <= now.getTime())
    .sort((a, b) => {
      const t = new Date(a.at).getTime() - new Date(b.at).getTime();
      return t !== 0 ? t : a.id.localeCompare(b.id);
    });

  let balance = 0, totalCharged = 0, totalPaid = 0, totalAdjusted = 0,
      totalRefunded = 0, totalCleared = 0, totalPending = 0;
  let lastActivityAt: string | null = null;

  // Detect reversal chains — reversed entries are excluded from typed totals
  // (but their signed amount still contributes to balance via the reversal entry)
  const reversedIds = new Set(
    relevant.filter(e => e.reversesId).map(e => e.reversesId!)
  );

  for (const e of relevant) {
    balance += e.amount;                                  // signed: +charge, -payment
    if (reversedIds.has(e.id)) continue;                  // skip typed totals

    switch (e.type) {
      case "charge":    totalCharged += e.amount; break;
      case "payment":
        totalPaid += Math.abs(e.amount);                  // payments are negative
        if (e.paymentStatus === "paid") totalCleared += Math.abs(e.amount);
        else if (e.paymentStatus === "pending") totalPending += Math.abs(e.amount);
        break;
      case "adjustment": totalAdjusted += e.amount; break;
      case "refund":    totalRefunded += Math.abs(e.amount); break;
      // "reversal" and "transfer" don't contribute to typed totals
    }
    if (lastActivityAt === null || e.at > lastActivityAt) lastActivityAt = e.at;
  }
  return { accountId, balance, totalCharged, totalPaid, totalAdjusted,
           totalRefunded, totalCleared, totalPending, entryCount: relevant.length,
           lastActivityAt, /* parentId, studentId, category derived from first entry */ };
}
```

**SQL equivalent** (`compute_account_balance` in `0007_financial.sql`):
```sql
create or replace function public.compute_account_balance(p_account_id text)
returns numeric(12,2) language sql stable as $$
  select coalesce(sum(amount), 0)::numeric(12,2)
    from public.ledger_entries
   where account_id = p_account_id;
$$;
```

### 5.4 Parent Summary (Family-Level Aggregation)

Aggregates all of a parent's accounts into a single `ParentLedgerSummary`:

```ts
// src/domain/model/ledger.ts
export function computeParentSummary(
  entries: readonly LedgerEntry[],
  parentId: string,
  parentName: string,
  overdueCategoryDueDates: ReadonlyMap<string, Date> = new Map(),
  now: Date = new Date(),
): ParentLedgerSummary {
  const parentEntries = entries.filter(e => e.parentId === parentId);
  const accountIds = new Set(parentEntries.map(e => e.accountId));
  const accounts = [...accountIds].map(id => computeAccountBalance(parentEntries, id, now));

  let totalOutstanding = 0, totalOverdue = 0, totalCharged = 0,
      totalPaid = 0, totalCleared = 0, totalPending = 0,
      totalAdjusted = 0, totalRefunded = 0, entryCount = 0;
  let lastActivityAt: string | null = null;

  for (const acc of accounts) {
    totalOutstanding += acc.balance;
    totalCharged += acc.totalCharged;
    totalPaid += acc.totalPaid;
    totalCleared += acc.totalCleared;
    totalPending += acc.totalPending;
    totalAdjusted += acc.totalAdjusted;
    totalRefunded += acc.totalRefunded;
    entryCount += acc.entryCount;
    if (acc.lastActivityAt && (!lastActivityAt || acc.lastActivityAt > lastActivityAt))
      lastActivityAt = acc.lastActivityAt;

    // Overdue = balance > 0 AND latest charge on this account is past due
    const dueDate = overdueCategoryDueDates.get(acc.accountId);
    if (acc.balance > 0.001 && dueDate && dueDate.getTime() < now.getTime())
      totalOverdue += acc.balance;
  }
  return { parentId, parentName, totalOutstanding, totalOverdue, totalCharged,
           totalPaid, totalCleared, totalPending, totalAdjusted, totalRefunded,
           accounts, entryCount, lastActivityAt };
}
```

### 5.5 Payment Allocation Logic

When a payment is collected via `PaymentRepository.collect(input, collectedBy)`:

1. **Generate receipt number**: `REC-{year}-{6-digit seq}` (e.g., `REC-2026-000123`)
2. **Auto-set status**:
   - `method === "cash"` → `status = "paid"` (immediately cleared)
   - `method === "check"` or `"transfer"` → `status = "pending"` (bank clearance required)
   - The desktop `counter-payment-modal.tsx` enforces: cash → paid, check/transfer → pending (never manual PAID)
3. **Proof mandatory** for check/transfer (`proofRequiredFor(method) → method !== "cash"`); enforced at DB layer by `enforce_payment_proof()` trigger
4. **Installment linkage** (if `installmentId` provided):
   - `installments.amountPaid += payment.amount` (mock) / `UPDATE installments SET amount_paid = amount_paid + p_amount` (SQL)
   - The `update_installment_status()` trigger auto-recomputes status:
     - `amount_paid >= amount_due` → `paid` + set `paid_date`
     - `amount_paid > 0` → `partial`
     - else → `unpaid` + clear `paid_date`
     - If `due_date < current_date` AND not fully paid → `overdue`
5. **Ledger entry appended** (canonical accounting):
   ```ts
   createPaymentEntry({
     amount: payment.amount,                 // positive input
     method, receiptNumber, paymentStatus,
     sourceType: "payment", sourceId: payment.id,
     // ... the entry's `amount` field is `-payment.amount` (negative = credit)
   });
   // LedgerEntry.amount = -input.amount   // NEGATIVE for payments
   ```
6. **Receipt auto-generated** (no separate button — plan §07.05): PDF generated by `receipt-pdf.ts` via pdf-lib, saved to `receipts` bucket at path `<tenant_id>/<payment_id>/receipt-{receiptNumber}.pdf`
7. **Audit log entry** written: `action = "payment.create"`, `entityType = "payment"`, `entityId = payment.id`, with full before/after JSON

The atomic SQL equivalent is the `public.collect_payment(...)` SECURITY DEFINER function in `0022_functions.sql`, which wraps steps 1–7 in a single transaction.

### 5.6 Installment Schedule Generation

Tuition = 3 tranches per service per student. Transport = 3 tranches if applicable. Generated from `PricingConfig`:

```ts
// src/domain/model/pricing.ts
export function tuitionTranchesForGrade(
  config: PricingConfig,
  gradeLevel: GradeLevel,
): ReadonlyArray<{ label: string; amountDue: number }> {
  const pricing = tuitionForGradeLevel(config, gradeLevel);
  return [
    { label: "Tranche 1 (Sept–Déc)", amountDue: pricing.installments[0] },
    { label: "Tranche 2 (Jan–Mar)",  amountDue: pricing.installments[1] },
    { label: "Tranche 3 (Avr–Juin)", amountDue: pricing.installments[2] },
  ];
}

export function transportTranchesForDestination(
  config: PricingConfig,
  destination: TransportDestination,
): ReadonlyArray<{ label: string; amountDue: number }> {
  const pricing = transportForDestination(config, destination);
  return [
    { label: "Tranche 1 (À l'inscription)", amountDue: pricing.installments[0] },
    { label: "Tranche 2 (01 Déc – 15 Déc)",  amountDue: pricing.installments[1] },
    { label: "Tranche 3 (01 Mar – 15 Mar)",  amountDue: pricing.installments[2] },
  ];
}

// Fallback for ad-hoc pricing: equal 3-way split with remainder in tranche 3
export function tuitionTranches(totalAmount: number) {
  const perTranche = Math.round(totalAmount / 3);
  const last = totalAmount - perTranche * 2;        // remainder → tranche 3
  return [
    { label: "Tranche 1", amountDue: perTranche },
    { label: "Tranche 2", amountDue: perTranche },
    { label: "Tranche 3", amountDue: last },
  ];
}
```

**Default tranche due-date templates per academic cycle** (`src/domain/model/payment.ts`):

```ts
export const DEFAULT_CYCLE_TRANCHE_MONTHS: Record<AcademicCycle, readonly [number, number, number]> = {
  primaire: [9, 12, 3],   // Sept / Dec / March
  cem:      [9, 12, 4],   // Sept / Dec / April
  lycee:    [9, 1, 5],    // Sept / Jan / May
};
```

Per-parent overrides via `InstallmentRepository.updateDueDate(input)` set `customSchedule: true` + `customScheduleNote`. `regenerateForCycle(parentId, cycle, actorId, actorName)` re-templates pending/partial installments against a cycle's default tranche template (paid installments are preserved).

### 5.7 Debt Calculation & Aging

```ts
// src/domain/model/payment.ts
export function overdueAmount(
  installments: readonly Installment[],
  now: Date = new Date(),
): number {
  const nowMs = now.getTime();
  return installments
    .filter(i => i.status !== "paid" && new Date(i.dueDate).getTime() < nowMs)
    .reduce((sum, i) => sum + installmentRemaining(i), 0);
}

export function installmentRemaining(i: Installment): number {
  return Math.max(0, i.amountDue - i.amountPaid);
}

export function maxDaysOverdue(
  installments: readonly Installment[],
  now: Date = new Date(),
): number {
  const nowMs = now.getTime();
  const days = installments
    .filter(i => i.status !== "paid" && new Date(i.dueDate).getTime() < nowMs)
    .map(i => Math.floor((nowMs - new Date(i.dueDate).getTime()) / 86_400_000));
  return days.length === 0 ? 0 : Math.max(...days);
}

export function agingBucketFromDays(daysOverdue: number): AgingBucket {
  if (daysOverdue <= 30)  return "0_30";
  if (daysOverdue <= 60)  return "31_60";
  if (daysOverdue <= 90)  return "61_90";
  if (daysOverdue <= 180) return "91_180";
  return "180_plus";
}

export function totalOutstanding(installments: readonly Installment[]): number {
  return Math.max(0, sumInstallmentsDue(installments) - sumInstallmentsPaid(installments));
}
```

**Ledger-aware variant** (`maxDaysOverdueFromLedger` in `ledger.ts`) computes days overdue from charge entries' `at` timestamp instead of installments.

**SQL equivalent** for `compute_overdue_amount(parent_id, as_of)`:
```sql
-- Outstanding charges with NO matching payment by the as-of date
select coalesce(sum(le.amount), 0)::numeric(12,2)
  from public.ledger_entries le
 where le.parent_id = p_parent_id
   and le.amount > 0                              -- charges
   and le.entry_type = 'charge'
   and le.entry_date::date <= p_as_of
   and not exists (
     select 1 from public.ledger_entries pay
      where pay.parent_id = p_parent_id
        and pay.account_id = le.account_id
        and pay.amount < 0                        -- payments
        and pay.entry_date::date <= p_as_of
   );
```

### 5.8 Family Balance Aggregation

A parent with N children has multiple accounts (one per child per category). The family's total outstanding is the sum of all account balances:

```ts
// src/domain/model/ledger.ts → computeParentSummary() (above)
// totalOutstanding = sum of all account balances for this parent
// totalOverdue = sum of balances on accounts whose latest charge is past due
```

The Desktop's `ParentFinancialProfile` (in `payment.ts`) is a parallel structure that aggregates installment data:

```ts
export interface ParentFinancialProfile {
  parentId, parentName,
  totalDue:        number,                          // sumInstallmentsDue
  totalPaid:       number,                          // sumInstallmentsPaid
  totalOutstanding: number,                         // totalDue - totalPaid
  overdueAmount:   number,                          // overdueAmount(installments)
  installments:    Installment[],
  recentPayments:  Payment[],
  adjustments:     AccountAdjustment[]
}
```

### 5.9 Discounts — Types, Codes, Application Order

#### Canonical discount codes (per official 2026-2027 fee schedule)

| Code | Type | Amount | Trigger |
|---|---|---|---|
| `passage_palier` | fixed_amount | −10,000 DA | Grade-level transition (e.g., primaire → CEM) |
| `seniority_5y` | percentage | −5% | More than 5 years seniority at the school |
| `full_annual` | percentage | −10% | Full annual payment before June 30 |
| `highest_average` | percentage | −10% | Student with highest average in grade level |
| `sibling_fixed` | fixed_amount | −5,000 DA per additional child | Each child beyond the first |

#### Application formula

```ts
// src/domain/model/pricing.ts
export function applyDiscount(
  baseAmount: number,
  discount: { amount: number; discountType: DiscountType },
): number {
  if (discount.discountType === "percentage") {
    const pct = Math.max(0, Math.min(100, discount.amount));    // clamp 0-100
    return Math.round(baseAmount * (1 - pct / 100));
  }
  // fixed_amount — stored as a NEGATIVE number; subtract the negative to apply
  return Math.max(0, baseAmount + discount.amount);
}

export function computeSiblingDiscount(
  config: PricingConfig,
  childrenCount: number,
): number {
  if (childrenCount <= 1) return 0;
  const entry = findDiscountByCode(config, "sibling_fixed");
  if (!entry) return 0;
  // amount is stored as a negative number for fixed_amount discounts
  return entry.amount * (childrenCount - 1);
  // Example: 3 children → 2 × sibling_fixed discount (i.e., 2 × −5,000 DA = −10,000 DA)
}
```

#### Discounts applied to installments

The ledger's `buildTuitionChargeEntries()` applies each discount to every tranche's `amountDue`:

```ts
// src/domain/model/ledger.ts
export function buildTuitionChargeEntries(input: {
  tenantId, parentId, studentId, level, gradeLevel?,
  config: PricingConfig,
  academicYear,
  trancheDueDates: readonly [string, string, string],
  actorId, actorName, sourceId,
  discounts?: readonly PricingEntry[],            // applied per-tranche
}): LedgerEntry[] {
  const tranches = input.gradeLevel
    ? tuitionTranchesForGrade(input.config, input.gradeLevel)
    : (() => {
        const tuition = tuitionForLevel(input.config, input.level);
        return tuitionTranches(tuition);
      })();

  return tranches.map((t, i) => {
    let amount = t.amountDue;
    if (input.discounts && input.discounts.length > 0) {
      for (const d of input.discounts) {
        if (d.discountType) {
          amount = applyDiscount(amount, { amount: d.amount, discountType: d.discountType });
        }
      }
    }
    return createChargeEntry({
      category: "tuition",
      amount,                                       // positive = debit
      sourceType: "installment",
      sourceId: `${input.sourceId}-t${i + 1}`,
      description: `Scolarité ${input.academicYear} — Tranche ${i + 1} (${input.gradeLevel ?? input.level})`,
      at: input.trancheDueDates[i],
      metadata: { tranche: i + 1, level: input.level, gradeLevel: input.gradeLevel ?? null, baseAmount: t.amountDue },
    });
  });
}
```

### 5.10 Receipt Generation

PDFs are **auto-generated** on payment entry (no manual button — plan §07.05). Uses `pdf-lib` (no native deps, runs in browser + Node).

Two formats (`src/infrastructure/receipt-pdf.ts`):

1. **Recent Payment Receipt** (`generatePaymentReceiptPdf(payment, parent?)`) — single transaction receipt
   - Format code: `RCP-2026-XXXXX` (matches `payment.receiptNumber`)
   - Fields: Amount Paid, Payment Method, Date, Receipt ID, Billed Services
2. **Account Statement / Balance Sheet** (`generateAccountStatementPdf(payments, parent)`) — complete ledger
   - Complete historical ledger of all payments since enrollment
   - Itemized active enrolled services
   - Total historical billed amount
   - Cumulative total paid amount
   - Current net balance due / outstanding debt

Layout: A4 (595×842 pt), 50pt margins. Brand blue header bar (`#349BD4`), EL-IMTIYAZ wordmark, document title right-aligned. Sanitizes text via `NFD` normalization for WinAnsi font compatibility.

### 5.11 Revenue Recognition

Only **`status === "paid"`** payments count as revenue. Pending check/transfer payments are tracked but excluded:

```ts
// src/domain/model/payment.ts
export function sumPaidPayments(payments: readonly Payment[]): number {
  return payments.filter(p => p.status === "paid").reduce((s, p) => s + p.amount, 0);
}

export function revenueByMonth(payments, now = new Date()) {
  // 12-bucket month array (oldest → newest), each { label, amount }
  // Only paid payments allocated to buckets by collectedAt month
}

export function revenueByCategory(payments, now = new Date()) {
  // Paid payments grouped by PaymentCategory for current month
}

export function monthlyRevenue(payments, now = new Date()): number {
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1).getTime();
  const monthEnd   = new Date(now.getFullYear(), now.getMonth() + 1, 1).getTime();
  return payments
    .filter(p => p.status === "paid")
    .filter(p => {
      const t = new Date(p.collectedAt).getTime();
      return t >= monthStart && t < monthEnd;
    })
    .reduce((s, p) => s + p.amount, 0);
}
```

### 5.12 Reconciliation Engine (`src/domain/reconcile.ts`)

Pure function `reconcileLedger(entries): ReconciliationReport` runs 8 integrity checks:

1. `checkDuplicateIds` — entry IDs unique
2. `checkRequiredFields` — id, tenantId, accountId, parentId, amount, type, sourceType, sourceId, description, actorId, actorName, at all populated
3. `checkSignedAmountConvention` — `charge > 0`, `payment < 0`, `refund < 0`, `adjustment ≠ 0`
4. `checkAccountIdsMatch` — `entry.accountId === deriveAccountId(parentId, category, studentId)`
5. `checkReversalIntegrity` — reversesId exists, single reversal per original, amount = -original.amount, accountId matches
6. `checkDuplicateReceiptNumbers` — receipt numbers unique within tenant
7. `checkTenantConsistency` — all entries share tenantId

Plus cross-checks:
- `crossCheckPayments(payments, ledgerEntries)` — every payment has matching ledger entry, amounts match, status matches
- `crossCheckInstallments(installments, ledgerEntries)` — every installment has matching charge entry, amountDue matches
- `crossCheckBalanceSum(entries, accountBalances)` — sum of entries === sum of balances (within 0.01)

### 5.13 Default 2026-2027 Pricing Seed

From `src/infrastructure/mock/pricing-seed.ts` (the only place amounts are hardcoded — admins edit at runtime via Settings → Pricing):

#### Tuition (14 grade levels, 3-tranche schedule)

| Grade Level | Annual (DZD) | T1 | T2 | T3 |
|---|---:|---:|---:|---:|
| Préscolaire 01 | 130,000 | 52,000 | 39,000 | 39,000 |
| Préscolaire 02 | 180,000 | 72,000 | 54,000 | 54,000 |
| 1AP | 245,000 | 98,000 | 73,500 | 73,500 |
| 2AP | 265,000 | 106,000 | 79,500 | 79,500 |
| 3AP | 280,000 | 112,000 | 84,000 | 84,000 |
| 4AP | 285,000 | 114,000 | 85,500 | 85,500 |
| 5AP | 300,000 | 120,000 | 90,000 | 90,000 |
| 1AM | 330,000 | 132,000 | 99,000 | 99,000 |
| 2AM | 345,000 | 138,000 | 103,500 | 103,500 |
| 3AM | 355,000 | 142,000 | 106,500 | 106,500 |
| 4AM | 370,000 | 148,000 | 111,000 | 111,000 |
| 1ère Année | 375,000 | 150,000 | 112,500 | 112,500 |
| 2ème Année | 380,000 | 152,000 | 114,000 | 114,000 |
| 3ème Année | 395,000 | 158,000 | 118,500 | 118,500 |

Constraint enforced at DB layer (`grade_level_tuition` table): `abs((tranche_1 + tranche_2 + tranche_3) - annual_amount) < 0.01`.

#### Transport (4 destinations, 3-tranche schedule)

| Destination | Annual | T1 (at registration) | T2 (Dec 01–15) | T3 (Mar 01–15) |
|---|---:|---:|---:|---:|
| Ville Boumerdès | 40,000 | 20,000 | 10,000 | 10,000 |
| Tidjelabine – Sahel – Figuier – Corso | 43,000 | 20,000 | 13,000 | 10,000 |
| Boudouaou – Thénia – Zemmouri | 52,000 | 30,000 | 12,000 | 10,000 |
| Autres | 55,000 | 30,000 | 15,000 | 10,000 |

#### Other pricing

- Registration fee: 5,000 DA (default in DB seed; editable)
- Late penalty per day: 100 DA (default)
- 2nd apron surcharge: 2,000 DA (flat)
- Early payment bonus: 5% if paid before June 30 (configurable)
- Complementary services: psychology 10,000/semester · 20,000/annual; speech therapy same

---

## 6. CRM Workflows

### 6.1 Batch Registration Flow (plan §04.03)

4-step atomic wizard (`src/features/crm/batch-registration-modal.tsx`):

1. **Parent info** — firstName, lastName, gender, phone, whatsapp, email, occupation, address, cityTier/transportDestination, preferredLanguage. Phone regex `/^[+]?[0-9\s]{8,15}$/`, email regex.
2. **N children** — unlimited ("Add Another Child" button per §04.02). Per-student: firstName, lastName, gender, birthDate, level, gradeYear, transportTier, medicalNotes.
3. **Billing config** — auto-computed from `PricingConfig`:
   - Per student: tuition (by gradeLevel) + transport (by destination) + registration fee
   - 3-tranche breakdown shown per student
   - Optional: includeRegistration toggle, per-student discounts
4. **Review + atomic submit** — calls `StudentRepository.batchRegister(input)` which wraps Parent + N Students in a single transaction. On failure, full rollback. On success: writes audit entry `parent.batch_register`, issues activation code, generates ledger charge entries for each tranche.

Atomic SQL equivalent: `public.batch_register_family(tenant_id, parent_jsonb, students_jsonb, actor, code)`.

### 6.2 Single Parent/Student CRUD

- `createParent` / `updateParent` / `deleteParent` (soft-delete via `deleted_at`)
- `createStudent(parentId, input)` — parent must exist (parent-first rule)
- `updateStudent` / `deleteStudent`
- All mutations audit-logged with `before_json`/`after_json` diff

### 6.3 Family Relationship Rules

- **Parent-first**: `students.parent_id` is NOT NULL FK (DB constraint, plan §04.01)
- **Unlimited children**: 1 parent → N students (no cap — the legacy 4-child limit was removed)
- **Bidirectional navigation**: parent drawer shows children; student drawer shows parent + siblings
- **Multi-guardian** (optional): `parent_student_links` junction table supports `is_primary` flag and `relationship` (`father`/`mother`/`guardian`/`other`)
- **Activation code binding**: 6-7 digit single-use code links parent's `auth_user_id` to their master profile (web portal login flow)

### 6.4 Search & Filter

- `ParentRepository.search(query)` — trigram GIN index on `(last_name, first_name)` in DB
- `StudentRepository.search(query)` — same trigram index
- Debounced search input (220ms) in the Counter Payment modal's parent picker
- CRM page has filter buttons (level, status) + search input
- `public.search_entities(query, limit)` SQL function searches across parents + students + personnel

---

## 7. Academic Module

### 7.1 Attendance

**4-status roll call** (maximum — no 5th "CUSTOM" status allowed per plan §09.02):

| Status | FR | Short | Tone |
|---|---|---|---|
| `present` | Présent | P | success |
| `absent_excused` | Absence excusée | AE | info |
| `absent_unexcused` | Absence non excusée | AN | danger |
| `late` | Retard | R | warning |

**30-second workflow** (`src/features/academics/roll-call-screen.tsx`):

1. Select class + date + session (Matin / Après-midi / Les deux)
2. Roster loads with every student defaulting to PRESENT
3. Per-student 4-button row: P / AE / AN / R
4. Sticky "Tous présents" button + absence counter
5. Sticky bottom save bar
6. On save: `AttendanceRepository.recordRollCall({ classId, date, session, statuses: Map<studentId, status>, recordedBy })`
7. If any non-Present status: `alertAbsences(studentIds)` fires automated absence alerts

DB uniqueness constraint: `(tenant_id, student_id, class_id, date, coalesce(class_subject_id, '00000000-...'))` — prevents duplicate roll call records for the same student/class/date/session.

### 7.2 Grades

**Layout**: Élève | D1 | D2 | Examen | Moy. (`src/features/academics/grade-entry-screen.tsx`)

**Subject Average formula** (plan §06.02):

$$\text{Subject Average} = \frac{D1 + D2 + 2 \cdot \text{Examen}}{4}$$

```ts
// src/domain/model/academic.ts
export function computeSubjectAverage(devoir1, devoir2, examen): number | null {
  if (devoir1 == null && devoir2 == null && examen == null) return null;
  const d1 = devoir1 ?? 0;
  const d2 = devoir2 ?? 0;
  const ex = examen ?? 0;
  return (d1 + d2 + 2 * ex) / 4;
}
```

**Overall GPA formula** (plan §06.03):

$$\text{Overall GPA} = \frac{\sum (\text{Subject Average}_i \times \text{Coefficient}_i)}{\sum \text{Coefficient}_i}$$

```ts
export function computeOverallGpa(assessments: ReadonlyArray<{ subjectAverage, coefficient }>): number | null {
  let weightedSum = 0, coefSum = 0;
  for (const a of assessments) {
    if (a.subjectAverage == null) continue;
    weightedSum += a.subjectAverage * a.coefficient;
    coefSum += a.coefficient;
  }
  return coefSum === 0 ? null : weightedSum / coefSum;
}
```

**Passing threshold**: 10.0 / 20.0 (admin-configurable). `isPassing(gpa, passingGrade = 10.0) → gpa >= passingGrade`.

**Validation**: `validateScore(value) → Number.isFinite(value) && value >= 0 && value <= 20`.

**Auto-compute trigger** (DB layer, `compute_grade_subject_average()` in `0004_academic_structure.sql`): fires BEFORE INSERT OR UPDATE OF score on `grades` table. Looks up D1/D2/Examen for the same student/subject/term and sets `subject_average = round((d1 + d2 + 2*ex) / 4.0, 2)`.

### 7.3 Homework

- `HomeworkRepository.push({ classId, subjectId, teacherId, title, description, dueDate, attachments })`
- Attachments stored in `homework-attachments` Supabase Storage bucket (10 MB max, jpeg/png/webp/pdf)
- **Immutability rule** (plan §05.07): homework is locked after due date — `is_locked` computed at query time as `due_date < current_date` (can't be a stored generated column because `current_date` is STABLE not IMMUTABLE)
- Acknowledgement counter (`acknowledgedCount`) tracks parent/student views

### 7.4 Class Management

- `AcademicClass`: name, level (primaire/cem/lycee), gradeYear, homeroomTeacherId, room, capacity, enrolledCount, academicYear
- Class detail page (`/academics/class/:classId`) has 4 sub-tabs: Élèves / Matières / Présences / Notes
- Quick actions: Appel (Roll Call), Notes (Grade Entry), Devoirs

### 7.5 Academic Period Structure

- **3-term structure**: T1 (Sept–Dec), T2 (Jan–Mar), T3 (Apr–Jun)
- `AcademicYear`: `2026-2027`, start_date (Sept 1), end_date (June 30), `term_structure` ∈ `semester`/`trimester`/`quarter`
- Academic year computed from current month: `currentAcademicYear(now)` returns `YYYY-YYYY+1` if month ≥ September (8), else `YYYY-1-YYYY`
- Academic levels: 14 grade levels grouped into 4 cycles (prescolaire, primaire, cem, lycee)

### 7.6 One-Click Batch Promotion (plan §06.04)

4-step flow (implemented as `promote_students(p_academic_year_id, p_actor_profile_id)` SQL function):

1. Calculate yearly GPAs for all enrolled students
2. Auto-flag: `GPA >= 10` → `APPROVED_FOR_PROMOTION`; `GPA < 10` → `RETAINED_SAME_YEAR`
3. Admin reviews queue, applies manual overrides (medical, relocation, etc.)
4. Execute: advances approved students to next grade level, archives previous year's records to `academic_history` (append-only), re-enrolls retained students in current grade

### 7.7 Scolarité vs Extracurricular Boundary

Hard boundary (plan §05.01): `Subject.isExtracurricular` flag enforces separation. Club grades NEVER bleed into Scolarité GPA. The `domain` field on subjects ∈ `scolarite`/`club`/`therapy`/`auxiliary` provides additional categorization.

---

## 8. Personnel Module

### 8.1 Onboarding Wizard (`src/features/personnel/onboarding/onboarding-wizard.tsx`)

11-step wizard (plan §10.10):

```
welcome → departments → roles → employees → admins → managers →
working_hours → shift_types → permissions → review → done
```

- `OnboardingState.completedSteps: Set<OnboardingStep>` allows non-linear navigation
- `OnboardingData` captures: departments (name/color/headId), roles (role/count), employeeCount, adminIds, managerAssignments, workingHours (start/end/weekdays), shiftTypes, permissionOverrides
- `OnboardingRepository.isComplete()` gates the Personnel page — wizard shows until complete (SuperAdmin only)
- 11 default departments seeded (Administration, Managers, Teachers, Buyers, Drivers, Warehouse, Sales, Accounting, Security, HR, Maintenance)

### 8.2 Management Sub-modules

Located in `src/features/personnel/management/`:

- **Employee Directory** (`employee-directory.tsx`) — searchable/filterable directory. Row click opens `EmployeeProfileDrawer`. "Nouvel employé" button opens `EmployeeFormModal`. XLSX export button.
- **Employee Profile Drawer** (`employee-profile-drawer.tsx`) — 8 sections: personal info, employment info (salary gated to SuperAdmin/FinancialOfficer), weekly hours + shifts, assigned tasks, attendance history (last 30 days), performance reviews, documents, internal notes
- **Employee Form Modal** (`employee-form-modal.tsx`) — 4 sections (Identité, Informations professionnelles, Paie, Contact d'urgence). Role picker auto-sets staffCategory via `staffCategoryForRole(role)`.
- **Department Management** (`department-management.tsx`) — grid of department cards with color swatch, headcount, archive/restore actions
- **Task Management** (`task-management.tsx`) — 5-column Kanban (pending / assigned / in_progress / blocked / completed — cancelled hidden). Filter by priority/department/assignee. Click card → `TaskDetailDrawer` with status changer, progress bar, comments thread.
- **Chat Panel** (`chat-panel.tsx`) — two-pane (channel list + messages). Channel types: direct (1:1), group, department, announcement. Read receipts. File attachments.

### 8.3 Role-Specific Dashboards (`src/features/personnel/dashboards/`)

`RoleDashboardRouter` dispatches based on `session.role`:

| Role | Dashboard | Key features |
|---|---|---|
| SuperAdmin / FinancialOfficer / SupportStaff | `AdministratorDashboard` | Full org overview + employee directory + department management |
| Manager | `ManagerDashboard` | Team tasks, attendance, performance reviews, approval queue |
| Buyer | `BuyerDashboard` | Purchase requests, suppliers, orders |
| Driver | `DriverDashboard` | Deliveries, routes, vehicle assignments |
| WarehouseWorker | `WarehouseWorkerDashboard` | Pending receipts, dispatches, inventory, scan |
| Teacher | `TeacherDashboard` | Classes, roll call, grade entry, homework, narrative generator |
| Worker | `WorkerDashboard` | Assigned tasks, clock in/out, leave requests |

### 8.4 Releve (Teacher Activity Ledger)

- Append-only (`releve_entries` table — no UPDATE/DELETE policies)
- **Self-entry prevention**: `prevent_self_releve_entry()` trigger raises exception if `recorded_by === personnel.user_id` (plan §09.05)
- `duration_minutes` is a generated column: `extract(epoch from (clock_out_at - clock_in_at))::integer / 60`
- Activities: `course`, `meeting`, `supervision`, `correction`, `task`, `delivery`, `warehouse`, `admin`, `other`
- Used as audit basis for payroll

### 8.5 Personnel Page Tabs

7 tabs (secondary navigation): Mon espace (role dashboard) · Annuaire (admin-only) · Tâches · Messagerie · Relevé · Alertes · Workflows

---

## 9. Dashboards

### 9.1 Main Dashboard (`src/features/dashboard/dashboard-page.tsx`)

**Access control** (spec §1.1): restricted to `{SuperAdmin, FinancialOfficer, SupportStaff, Manager}`. Teachers + non-admin staff redirected to `/personnel`.

**Tabs**: Overview / Alerts / Reports (Analytics tab removed — merged into Overview per spec §2.2).

#### Overview Tab

- **Header**: `<AcademicYearSelector />` (interactive — supports YTD / current month / current quarter / custom date range; all metrics re-fetch on change)
- **KPI cards** (4): Total Students, Monthly Revenue, Outstanding Debt, Pending Expenses — each clickable to drill down into `SeeDetailsModal`
- **Revenue chart** (last 12 months, bar chart via Recharts)
- **Debt aging chart** (5 buckets: 0-30, 31-60, 61-90, 91-180, 180+)
- **Demographic pies**: grade distribution, gender distribution, age buckets, capacity fill rate
- **`<DashboardCalendar />`** embedded directly in Overview (spec §3.1) — month grid + daily activity panel

#### Alerts Tab

- `AppNotification` list sorted by priority (urgent > high > medium > low) then by createdAt
- Filter by source (system/manual/workflow/schedule/audit)
- Click alert → `AlertDetailModal` drawer
- "Créer une alerte" button → `AlertCreatorModal`

#### Reports Tab

- Macro/org-level aggregate reports only (entity-specific reports live in profile drawers per spec §5.2)
- Export buttons: revenue report (XLSX), outstanding debt report (XLSX), student roster (XLSX)
- Generated via `src/infrastructure/excel/reports.ts`

### 9.2 See Details Modal (`see-details-modal.tsx`)

Drill-down modal with sub-tabs: Revenue / Debt / Demographics / Departments / Calendar. Pre-selects the relevant sub-tab based on which KPI/chart was clicked.

### 9.3 Dashboard KPIs (computed)

```ts
interface DashboardKpi {
  totalStudents: number;
  totalParents: number;
  totalStaff: number;
  monthlyRevenue: number;          // sumPaidPayments collected this month
  outstandingDebt: number;         // sum of ledger balances > 0
  pendingExpenses: number;         // count of expense_tickets with status='submitted'
  attendanceRateToday: number;     // present / total enrolled today
  overdueAlerts: number;           // count of overdue installments
}
```

Materialized view `mv_dashboard_kpis` (refreshed nightly by `refresh_all_materialized_views()`):

```sql
-- collection_rate_pct = (sum |payments|) / (sum charges) * 100
case
  when (select coalesce(sum(amount), 0) from ledger_entries where amount > 0) = 0 then 0
  else (
    (select coalesce(sum(abs(amount)), 0) from ledger_entries where amount < 0)
    / nullif((select coalesce(sum(amount), 0) from ledger_entries where amount > 0), 0)
  ) * 100
end as collection_rate_pct
```

### 9.4 Materialized Views

| View | Purpose | Refresh |
|---|---|---|
| `mv_dashboard_kpis` | Per-tenant KPI snapshot | nightly |
| `mv_debt_aging` | Per-parent debt bucketed by aging tier | nightly |
| `mv_top_debtors` | Top 20 families by outstanding amount | nightly |
| `mv_revenue_by_month` | Last 12 months of paid payments | nightly |
| `mv_grade_summary` | Per-student subject averages + weighted scores | nightly |

Each has a UNIQUE index for concurrent refresh via `REFRESH MATERIALIZED VIEW CONCURRENTLY`.

---

## 10. Workflow Module

### 10.1 Visual DAG Editor (`src/features/workflow/`)

- **Node palette** (`node-palette.tsx`) — drag nodes onto canvas
- **DAG canvas** (`dag-canvas.tsx`) — visual editor with position coordinates
- **Cycle detection** via Kahn's algorithm (`src/domain/kahn.ts`):

```ts
export function detectCycle(
  nodes: readonly Readonly<{ id: string }>[],
  edges: readonly Readonly<{ from: string; to: string }>[],
): CycleDetectionResult {
  // 1. Build adjacency list + in-degree map (deduplicate edges)
  // 2. Initialize queue with all in-degree-0 nodes
  // 3. Process queue: decrement successor in-degrees, enqueue new in-degree-0 nodes
  // 4. If processedCount === nodes.length → no cycle
  // 5. Otherwise: unprocessed nodes (in-degree > 0) participate in cycle
  // Returns { hasCycle, cycleNodeIds, cycleEdgeKeys }
}
```

Runs on **every canvas save** (not just on publish — plan §18.04). Provides visual feedback (red edges) when a connection would create a cycle.

### 10.2 Node Types & Subtypes

| Type | Subtypes |
|---|---|
| **trigger** | `payment_overdue`, `student_enrolled`, `payment_recorded`, `schedule`, `absence_limit_exceeded`, `manual_run` |
| **condition** | `debt_over_threshold`, `payment_method_match`, `student_status_match` |
| **action** | `send_email`, `apply_discount`, `create_invoice`, `push_notification`, `log_audit` |
| **delay** | `wait_duration` |
| **transform** | `database_query`, `extract_field` |

### 10.3 Workflow Status

- `draft` — editable, not eligible to run
- `published` (aka `deployed`) — eligible to run; `lastDeployedAt` set
- `disabled` — paused, history preserved

### 10.4 Workflow Run Lifecycle

States: `pending` → `running` → `succeeded` / `failed` / `timeout` / `cancelled`

Execution (`workflow-execute` Edge Function):
1. POST `{ workflow_id, trigger_type?, actor_note? }` with JWT + `execute_workflow` permission
2. Fetch workflow (must be `published`)
3. Check daily execution limit (`max_daily_executions`, default 100)
4. Insert `workflow_runs` row with `status='running'`
5. Parse DAG definition, topologically sort, detect cycles
6. Walk nodes in topo order:
   - Trigger → mark succeeded (entry point)
   - Condition → evaluate, activate true/false downstream branch, prune the other
   - Action → execute via stub (TODO: wire real Resend/FCM/Postgres integrations)
   - Unreachable nodes → mark `skipped`
7. On failure: mark run `failed`, capture error in `node_results`, skip downstream
8. Update run with final status, `duration_ms`, `node_results`
9. Write audit log `workflow.run`

### 10.5 Workflow Audit Links

M:N junction table `workflow_audit_links` links `workflow_runs` to `audit_logs`. Composite PK prevents duplicate links.

---

## 11. Excel Import Engine

Schema-driven, generic engine (`src/infrastructure/excel/import-engine/`). Adding a new Excel format = adding a new `ImportSchema`, no engine changes.

### 11.1 Architecture

```
ImportEngine
  ├── ExcelParser         (exceljs wrapper — opens .xlsx, iterates rows)
  ├── SheetDetector       (two-tier detection: sheet name regex → header signature)
  ├── RowValidator        (per-field coercion + summary-row detection)
  │     └── FieldCoercer  (type-aware: string/email/phone/phoneList/number/numberOrRef/enum/date/monthlyArray)
  │           └── rules/  (required, email, phone, positive-number, enum, min-length)
  ├── UpsertMatcher       (extracts identity keys from coerced record; partial match supported)
  ├── StorageAdapter      (InMemoryAdapter default; SqliteAdapter/JsonAdapter available in standalone)
  ├── JsonReporter        (downloads import-report-{runId}.json)
  └── ExcelReporter       (downloads import-report-{runId}.xlsx with errors/warnings highlighted)
```

### 11.2 Schemas (`schemas/`)

#### ETAT schema (`etat-schema.ts`) — the master client/student roster

Sheet matcher: `/^ETAT/i`, `/^ETAT\s*\d+/i`
Required headers: `NOM`, `niveau`, `CLASSE`, `DEVIS ANNUEL`
Identity: `["NEM", "NOM"]` with `strategy: "upsert"`

| Header | Key | Type | Required | Notes |
|---|---|---|---|---|
| `INFOS` | `infos` | string | no | Free-text notes about the family |
| `E-MAIL` | `email` | email | no | Invalid emails on optional fields → warnings (not errors) |
| `NEM` | `nem` | phoneList | no | "Purely informational" — many valid students have no parent phone |
| `TUTEUR` | `tuteur` | string | no | Tutor/guardian name |
| `NOM` | `nom` | string | yes | minLength: 2 |
| `niveau` | `niveau` | enum | yes | 14 codes: PRIM, COLG, LYC, GS, MS, PS, TPS, AUTISTE, NV2, NV3, NV4, NV5, CLYC, LYCI + `tolerateUnknown: true` |
| `CLASSE` | `classe` | string | yes | — |
| `OPTION` | `option` | enum | no | TRNSP, TENSP, TRNP, "" + `tolerateUnknown: true` (typos accepted) |
| `REMISE` | `remise` | number | no | default 0, min 0 |
| `JUSTIFICATION` | `justification` | string | no | — |
| `DEVIS ANNUEL` | `devisAnnuel` | number | yes | min 0 |
| `REMBOURCEMENT` | `remboursement` | number | no | default 0, min 0 (note: misspelled in source) |
| `DETTES` | `dettes` | number | no | default 0, min 0 |
| `REGLEMENTS DETTES` | `reglements` | monthlyArray | no | count: 12, monthLabels: sep, oct, nov, dec, jan, feb, mar, apr, may, jun, jul, aug |

#### DEVIS schema (`devis-schema.ts`) — quote engine

Sheet matcher: `/^DEVIS$/i`, `/^DEVIS\s/i`
Required headers: `Prenom élève`
Identity: `["client", "devisNumero"]` with `strategy: "upsert"`

Fields: Client, Devis n°, Date, Prenom élève, Classe, Frais d'inscription, Frais de scolarisation, Services, Total (last 4 are `numberOrRef` — accepts number or formula reference).

#### BON schema (`bon-schema.ts`) — client statement

Sheet matcher: `/^BON\s*$/i`, `/^BONS?$/i`
Required headers: `ELEVES`
Identity: `["eleve"]` with `strategy: "upsert"`

Fields: CLIENT, DATE, DEVIS ANNUEL, ELEVES, DEVIS, TOTAL VERSE, RESTE VERSE.

#### REF schema (`ref-schema.ts`) — reference data (teachers/classes/localities)

Sheet matcher: `/^REF$/i`, `/^REFERENCES?$/i`
`headerRow: 0` (sentinel — no header row, synthetic A/B/C/D headers generated)
Identity: `{ fields: [], strategy: "insert" }` — deduplicated via UNIQUE constraints on each ref table

`extractAs` map drives multi-table fan-out — one REF row can produce up to 3 inserts across `ref_enseignants`, `ref_classes`, `ref_localites`.

### 11.3 Field Coercion Pipeline

`FieldCoercer.coerce(rawValue, field)` follows strictly-ordered pipeline:

1. **Excel error detection** (`#REF!`, `#N/A`, `#VALUE!`, `#NAME?`, `#DIV/0!`, `#NULL!`, `#NUM!`) → error if required, warning if optional
2. **Required check** → error if missing
3. **Empty optional** → use `field.default`
4. **Type dispatch**:
   - `string` — trim + optional `uppercase`/`lowercase`
   - `email` — regex validate; on optional fields, invalid → warning + keep raw string
   - `phone` / `phoneList` — Algerian phone regex, normalize, split on `/` or `,`
   - `number` / `numberOrRef` — `parseNumber` handles French locale (comma decimal, space thousands); `numberOrRef` accepts formula references as warnings
   - `enum` — check `field.values`; `tolerateUnknown: true` → warning + uppercase raw value
   - `date` — `new Date()` parse, warning if invalid
   - `monthlyArray` — aggregate N contiguous columns starting after `field.header` into `{ monthLabel: number }` map
5. **Structural rules** — `minLength` for string/email

### 11.4 Validation Tolerances (Iteration 14)

The real `Suivis clients 2026_2027.xlsx` file has 1,031 rows. Initial schema rejected 637 (62%) for invalid reasons. Iteration 14 fixes:

- `niveau` enum expanded from 4 to 14 codes + `tolerateUnknown: true`
- `OPTION` enum accepts documented typos `TENSP` and `TRNP`
- `NEM` made optional (`required: false`) per business doc
- `requiredHeaders` reduced from 5 to 4
- Optional email fields with invalid values → warnings (not errors)
- `UpsertMatcher.extractIdentity` skips empty identity fields rather than failing — built from whichever fields are present, returns `null` only when ALL identity fields are empty

### 11.5 Deduplication Rules

`UpsertMatcher` (`dedupe/upsert-matcher.ts`):

- Builds `headerToKey` map at construction (translates schema header names → camelCase keys)
- `extractIdentity(record)`: iterates `schema.identity.fields`, looks up value by header-translated key, skips empty values, joins arrays with `,`, normalizes dates to ISO. Returns `Record<string, string | number>` with at least one entry, or `null` if all empty.
- `sameIdentity(a, b)`: compares all identity field values (arrays joined, dates ISO-normalized)
- `strategy()`: returns `"upsert"` (update existing) or `"insert"` (always insert, dedup via UNIQUE constraint)

Storage adapter's `upsertRecord(schema, record, identityKeys, runId)` looks up existing record by partial identity match → returns `{ action: "insert" | "update" | "skip", id? }`.

### 11.6 Reporters

- **JsonReporter** — downloads `import-report-{runId}.json` with full `ImportContext.toJSON()` (run ID, file checksum, stats, sheet results, errors, warnings)
- **ExcelReporter** — downloads `import-report-{runId}.xlsx` with errors/warnings highlighted per row

### 11.7 Audit Integration

Every import run emits:
- `import.run_started` — on engine start (with file path, checksum, size, options)
- `import.run_completed` — on success (with stats: rowsImported, rowsUpdated, rowsSkipped, rowsRejected, durationMs)
- Per-row actions: `import.row_inserted`, `import.row_updated`, `import.row_skipped`, `import.row_rejected`

### 11.8 Sync Integration

Each successfully imported row is also enqueued via `useSyncActions().enqueue({entity, operation, payload, isMock: false, sourceFile, importRunId})` — the sync service pushes it to Supabase when online.

---

## 12. Sync Architecture

### 12.1 Offline-First Design

- **Desktop**: online-first (no local cache, every read is a network call) — the 24h backup daemon is the offline safety net
- **Mobile**: offline-first with local Room DB cache (separate from desktop)
- **Web portal**: read-only (parents/students)

### 12.2 SyncService (`src/infrastructure/sync/sync-service.ts`)

Singleton service. Responsibilities:

1. Queue mutations to IndexedDB (`el-imtiyaz-sync` DB, `queue` object store)
2. **Mock-data invariant**: `isMock: true` entries auto-marked `skipped_mock` — NEVER pushed (defense-in-depth: re-checked at drain time)
3. Detect online/offline transitions via `OnlineDetector`
4. When online + Supabase configured: drain pending queue via registered `push` handler
5. Auto-sync triggers:
   - On app startup (if online + configured)
   - When network comes back online (transition offline → online)
   - When new entries are queued (debounced 2s)
   - On manual `syncNow()` call
6. Retry with exponential backoff: `BACKOFF_BASE_MS * 2^attempts` (1s, 2s, 4s, 8s, 16s). After `maxAttempts` (default 5): marked `failed`
7. Emit status snapshots via `subscribe()` for UI (topbar indicator, settings page)

### 12.3 OnlineDetector (`src/infrastructure/sync/online-detector.ts`)

- Wraps `navigator.onLine` + window `online`/`offline` events
- Performs HTTP probe to `https://www.google.com/generate_204` (HEAD, no-cors, 5s timeout) every 30s when online, 120s when offline
- Combined state: `online = navigatorOnline && probeOk`
- Throttled to max 1 probe per 5s

### 12.4 SyncQueueStore (`src/infrastructure/sync/sync-queue-store.ts`)

- IndexedDB-backed (`el-imtiyaz-sync` DB, version 1, `queue` object store keyed by `id`)
- Indexes: `status`, `queuedAt`, `isMock`
- Falls back to in-memory `Map` if IndexedDB unavailable (with console warning — queue will NOT survive restart)

### 12.5 Default Push Handler

```ts
// src/app/providers/sync-provider.tsx
async function defaultPushHandler(entry: SyncQueueEntry): Promise<void> {
  const { getSupabaseClient } = await import("../../infrastructure/supabase/supabase-client");
  const client = getSupabaseClient();
  const { error } = await client.from("sync_queue").upsert({
    id: entry.id,
    entity: entry.entity,
    operation: entry.operation,
    tenant_id: entry.tenantId,
    actor_id: entry.actorId,
    payload: entry.payload,
    source_file: entry.sourceFile ?? null,
    import_run_id: entry.importRunId ?? null,
    queued_at: entry.queuedAt,
    status: "pending",
  });
  if (error) throw error;
}
```

### 12.6 Conflict Resolution (Mobile-specific, documented for reference)

- Every mobile row carries `client_updated_at`
- Server tables carry `updated_at` (set by DB trigger)
- On sync, if `client_updated_at < server.updated_at` → conflict
- Resolution:
  - **Last-write-wins** for non-critical fields (notes, descriptions)
  - **Surface to user** for critical fields (payment amounts, grades, attendance) — diff shown, user picks version
- All conflicts logged to `audit_logs` with `action = "sync.conflict"`

---

## 13. Auth & RBAC

### 13.1 Roles (11 total — 9 staff + 2 web-only)

| Code | FR Label | Staff? | Web? |
|---|---|---|---|
| `super_admin` | Super Administrateur | ✓ | |
| `financial_officer` | Agent Financier | ✓ | |
| `teacher` | Enseignant | ✓ | |
| `support_staff` | Personnel de Soutien | ✓ | |
| `manager` | Responsable | ✓ | |
| `buyer` | Acheteur | ✓ | |
| `driver` | Chauffeur | ✓ | |
| `warehouse_worker` | Magasinier | ✓ | |
| `worker` | Ouvrier | ✓ | |
| `parent` | Parent | | ✓ |
| `student` | Élève | | ✓ |

### 13.2 Auth Flow

**Staff (Desktop + Mobile):**
1. User enters email + password
2. `supabase.auth.signInWithPassword({ email, password })`
3. Supabase returns access_token + refresh_token
4. Desktop fetches `user_profiles` (status must be `active`, not `pending`/`suspended`)
5. Desktop calls `current_user_roles()` + `current_user_permissions()` RPCs
6. Builds `Session` object with role + permissions precomputed
7. Persists to `localStorage["el-imtiyaz.session"]`
8. RLS policies gate all subsequent queries

**Parents (Web Portal):**
1. Sign in via Google OAuth (`supabase.auth.signInWithOAuth({ provider: 'google' })`)
2. After redirect, enter 6-7 digit activation code
3. `bind-activation-code` Edge Function calls `bind_activation_code()` RPC:
   - Validates code (exists, not used, not expired — 30-day window)
   - Marks code as bound (single-use enforcement via `FOR UPDATE` lock)
   - Updates `parents.auth_user_id` to caller's `auth.users.id`
4. RLS now grants parent read access to their N children's data

### 13.3 Approval Workflow (Web-first registration)

1. Web visitor signs up via Supabase Auth (Google OAuth or email/password)
2. `handle_new_auth_user()` trigger fires AFTER INSERT on `auth.users`:
   - Inserts `user_profiles` row (status=`pending`)
   - Inserts `account_approval_requests` row (status=`pending`, expires in 7 days)
3. Admin opens Desktop → Settings → Inscriptions → reviews pending requests
4. Admin calls `approve-signup-request` Edge Function with one of:
   - **Approve + bind to existing parent**: `target_parent_id` provided
   - **Approve + bind to existing student**: `target_student_id` provided (student role)
   - **Approve + create new parent**: `create_new_parent=true` + `new_parent` object
   - **Reject**: `decision_note` mandatory
5. On approve: `account_approval_requests.status` → `approved`, `user_profiles.status` → `active`, role assigned via `role_assignments`, parent/student `auth_user_id` bound
6. On reject: `account_approval_requests.status` → `rejected`, `user_profiles.status` → `suspended` (cannot sign in)

### 13.4 Password Governance (plan §12.04)

`SupabaseAuthRepository.changePassword(currentPassword, newPassword)`:

1. **Strength validation** (client-side):
   - Min 8 characters
   - At least 1 lowercase
   - At least 1 uppercase
   - At least 1 digit
2. **Re-authentication**: `signInWithPassword(email, currentPassword)` — verifies current password
3. **Update**: `supabase.auth.updateUser({ password: newPassword })`
4. **Global session revocation**: `supabase.auth.signOut({ scope: 'global' })` — revokes ALL sessions across ALL devices for this user
5. **Audit log**: writes `auth.password_change` audit entry

`expire-pending-approvals` Edge Function runs daily via cron to expire approval requests past their 7-day window.

### 13.5 Permissions Matrix

56 atomic permissions grouped by domain: `crm`, `academic`, `financial`, `expense`, `hr`, `workflow`, `routing`, `settings`, `backup`, `ai`, `operations`, `workforce`, `audit`, `notification`, `calendar`.

Default role → permission matrix in `DEFAULT_ROLE_PERMISSIONS` (`src/core/rbac/permissions.ts`). Key examples:

- **SuperAdmin**: all permissions (unrestricted)
- **FinancialOfficer**: ViewRoster, ViewFinancials, CollectPayment, RefundPayment, AdjustAccount, GenerateReceipt, ViewDebt, SendReminder, SubmitExpense, ApproveExpense, DisburseExpense, SettleExpenseProof, ViewPersonnel, ViewAuditLog, ViewReleve, ManageSettings, UseAI, ViewWorkflowRuns, ManageBackups, + iteration 8 workforce reports/approve purchases
- **Teacher**: ViewRoster, ViewAcademics, EnterGrades, AssignHomework, RollCall, SubmitExpense, ViewPersonnel, ViewReleve, UseAI, ViewTasks, UpdateTaskStatus, ViewAttendance, ClockInOut, SubmitRequests, UseChat
- **SupportStaff**: ViewRoster, CreateParent, EditParent, CreateStudent, EditStudent, ViewAcademics, CollectPayment, GenerateReceipt, SubmitExpense, ViewPersonnel
- **Manager**: View + ManageTasks, ManageSchedules, ApproveRequests, ViewPerformance, ViewWorkforceReports, UseChat, ManageChatChannels
- **Buyer**: ManagePurchaseRequests, ManageSuppliers
- **Driver**: ManageDeliveries, AccessDriverMode
- **WarehouseWorker**: ManageInventory
- **Worker**: UpdateTaskStatus, ClockInOut, SubmitRequests, UseChat

Per-tenant overrides via `tenant_role_overrides` table — `action` ∈ `grant`/`deny`, **deny wins**.

### 13.6 Feature Gate Evaluation

```ts
// src/core/rbac/feature-gate.ts
export function evaluate(
  requirement: AccessRequirement,
  ctx: { session: Session | null; flags: FeatureFlagProvider },
): AccessState {
  // 1. empty → enabled
  // 2. permanent → disabled(permanent)
  // 3. session == null → hidden or disabled(not_authenticated)
  // 4. permission → check session.permissions.has(permission)
  // 5. anyOfPermission → some permissions held
  // 6. allOfPermission → all permissions held
  // 7. role → session.role in roles
}
```

`FeatureRegistry` (`feature-registry.ts`) is the canonical tree of every feature/page/action — single source of truth for gating rules. UI consumes via `<GatedContent node={...}>`.

---

## 14. Audit Logging

### 14.1 Universal Action Traceability (plan §12)

> Every DB write → audit entry. Authentication events → audit entry. Permission alterations → audit entry. System exports (PDF, XLSX) → audit entry. Sensitive record views → audit entry. Truncated before_json/after_json is FORBIDDEN. System-initiated actions attribute to a system user ID, never anonymous. Append-only: no edits, no deletes (trigger-enforced). Corrections require a new audit-logged entry that supersedes the original.

### 14.2 Audit Action Codes (`src/core/audit-actions.ts`)

Wire-protocol constants — never rename without a migration:

| Domain | Actions |
|---|---|
| Auth | `auth.login`, `auth.logout`, `auth.password_reset`, `auth.session_revoked` |
| CRM | `parent.create`, `parent.update`, `parent.delete`, `student.create`, `student.update`, `student.promote`, `crm.batch_register` |
| Academic | `class.create`, `class.update`, `subject.create`, `subject.update`, `subject.archive`, `subject.assign`, `grade.enter`, `attendance.submit`, `homework.push` |
| Financial | `payment.create`, `payment.refund`, `payment.adjust`, `receipt.generate`, `installment.create`, `installment.mark_paid`, `debt.reminder_sent` |
| Expense | `expense.submit`, `expense.approve`, `expense.reject`, `expense.disburse`, `expense.settle` |
| Personnel | `personnel.create`, `personnel.update`, `releve.create` |
| Settings/System | `settings.update`, `rbac.matrix_update`, `backup.created`, `backup.restored`, `workflow.published`, `workflow.triggered` |
| AI | `ai.narrative_drafted`, `ai.narrative_approved`, `ai.narrative_rejected`, `ai.draft_generated`, `ai.draft_sent`, `ai.anomaly_flagged`, `ai.anomaly_justification_requested`, `ai.config_update`, `ai.config_test` |
| Excel Import | `import.run_started`, `import.run_completed`, `import.row_inserted`, `import.row_updated`, `import.row_skipped`, `import.row_rejected` |

### 14.3 Audit Entry Shape

```ts
interface AuditEntry {
  id, tenantId,
  action: string,                  // from AuditActions
  entityType: string,              // 'parent', 'student', 'payment', etc.
  entityId: string,
  actorId, actorName,
  diff: string | null,             // JSON diff { before, after } — NEVER truncated
  note: string | null,
  ipAddress: string | null,
  userAgent: string | null,
  at: string                       // ISO timestamp
}
```

### 14.4 Append-Only Enforcement

```sql
-- 0014_audit.sql
create or replace function public.enforce_audit_log_append_only()
returns trigger language plpgsql security definer as $$
begin
  raise exception 'audit_logs is append-only (plan §12). UPDATE and DELETE are forbidden. Use a new entry with supersedes_id for corrections.';
end;
$$;

create trigger audit_logs_block_update before update on public.audit_logs
  for each row execute function public.enforce_audit_log_append_only();

create trigger audit_logs_block_delete before delete on public.audit_logs
  for each row execute function public.enforce_audit_log_append_only();
```

### 14.5 Retention

No automatic truncation — storage is cheap, complete audit trail is essential. The `supersedes_id` column allows corrections: a new entry references the original, both remain in the log.

---

## 15. Notifications

### 15.1 Notification Types

`AppNotification` carries: `kind` (alert/info/warning/success/error/system), `priority` (low/medium/high/urgent), `source` (system/manual/workflow/schedule/audit), `sourceLabel`, `targetUserId`, `targetRole`, `triggeredAt`, `readAt`, `expiresAt`, `linkEntityType`, `linkEntityId`.

### 15.2 What Triggers Notifications

| Source | Trigger | Priority | Target |
|---|---|---|---|
| `system` | Overdue installment (daily scan via `run-overdue-scan` cron) | urgent (>90 days) / high (31-90) / medium (0-30) | `financial_officer` role |
| `system` | Expense pending approval | high | `financial_officer` + `manager` roles |
| `system` | Backup failure | urgent | `super_admin` role |
| `system` | Disk space >80% | high | `super_admin` role |
| `workflow` | Workflow run completed/failed | varies | workflow initiator |
| `schedule` | Calendar reminder triggered | varies | event assignee |
| `audit` | Sensitive record viewed | low | record owner |
| `manual` | User-created alert via `AlertCreatorModal` | user-selected | user/role/broadcast |

### 15.3 Targeting Rules

```ts
// src/domain/model/operations.ts
export function isAlertVisibleTo(alert: AppNotification, session: { userId, role }): boolean {
  if (alert.targetUserId && alert.targetRole) {
    return alert.targetUserId === session.userId || alert.targetRole === session.role;
  }
  if (alert.targetUserId) return alert.targetUserId === session.userId;
  if (alert.targetRole) return alert.targetRole === session.role;
  return true; // broadcast (both null) → visible to everyone
}
```

### 15.4 Overdue Alert Generator (Idempotent)

`OverdueAlertGenerator.run(now?)`:

1. Scans installments via `InstallmentRepository.findOverdue(now)`
2. For each overdue installment:
   - Computes `daysOverdue` + `amountOverdue`
   - Determines priority: `>90 days` → urgent, `31-90` → high, `0-30` → medium
   - Inserts notification (idempotent on `(targetRole=financial_officer, linkEntityType=installment, linkEntityId=installmentId)`)
3. Returns list of newly created alerts (empty if all overdue installments already had alerts)

### 15.5 FCM / Push Notifications (Android)

- `FCM_SERVER_KEY` + `FCM_SENDER_ID` stored as Edge Function secrets (set via `update-server-secret` function)
- Mobile app registers FCM token on sign-in
- Workflow `push_notification` action node sends via FCM
- Web portal uses browser push API (future — not yet implemented)

### 15.6 Email (Resend)

- `RESEND_API_KEY` + `EMAIL_FROM_ADDRESS` + `EMAIL_FROM_NAME` stored as Edge Function secrets
- Workflow `send_email` action node sends via Resend
- Used for: overdue reminders, approval notifications, expense status changes

---

## 16. AI Features

### 16.1 Provider Stack

- **Primary**: Groq (`llama-3.3-70b-versatile` default model)
- **Fallback**: OpenRouter (`meta-llama/llama-3.3-70b-instruct:free` default)
- **BYOK** (Bring Your Own Key): API keys stored AES-256-GCM encrypted in localStorage (`el-imtiyaz:ai-config`) and in `ai_provider_configs.api_key_encrypted` DB column
- **Production path**: all AI calls proxy through `ai-proxy` Edge Function so API keys NEVER leave the server

### 16.2 PII Masking (`src/domain/pii-mask.ts`)

Before sending any content to the LLM, PII is masked:

```
Order matters: IBAN → phone → email → NN (10-digit) → parent_names → student_names
```

- Phone: `+213 555 123 456` → `[PHONE_1]`
- Email: `john@doe.com` → `[EMAIL_1]`
- IBAN: `DZ` + 22 digits → `[IBAN_1]`
- National ID (NN): 10 consecutive digits (with lookarounds) → `[NN_1]`
- Parent/student names: from options arrays, sorted longest-first → `[PARENT_1]`, `[STUDENT_1]`

Each UNIQUE occurrence gets its own placeholder. Same value twice → same placeholder. Reversible via `unmaskPII(masked, replacements)`.

### 16.3 Three AI Features

#### Report Card Narrative Generator (plan §11.05)

- **Input** (`NarrativeRequest`): studentId, studentName, grades `[{ subject, average }]`, attendanceRate, teacherNotes, term
- **Mandatory**: teacher review before publishing — AI output is a draft, never auto-published
- **Audit**: `ai.narrative_drafted` → on teacher approval `ai.narrative_approved` or rejection `ai.narrative_rejected`
- **Mock response**: 3-paragraph French narrative (engagement, difficulties, overall assessment)

#### Administrative Drafting Assistant (plan §11.06)

- **Input** (`DraftingRequest`): `draftType` ∈ `convocation` / `parent_alert` / `policy_notice`, keyPoints[], recipient?
- **Human review required** before sending
- **Audit**: `ai.draft_generated` → on send `ai.draft_sent`
- **Mock response**: formal French administrative document (Objet, Madame/Monsieur, body, signature)

#### Expense Anomaly Detector (plan §11.07)

- **Input**: expense ticket + historical context
- **Output** (`AnomalyExplanation`): 3 signal types — `duplicate`, `missing_proof`, `budget_overrun`, `new_vendor` — each with severity low/medium/high
- **CRITICAL**: AI is a signal, not a verdict. Human always decides. Never auto-reject based on anomaly score.
- **Audit**: `ai.anomaly_flagged` → on justification request `ai.anomaly_justification_requested`
- **Mock response**: 3-signal pattern (duplication, new vendor, budget overrun) with recommendation to ask submitter for justification

### 16.4 LLM Adapter Contract

```ts
// src/infrastructure/ai/llm-adapter.ts
export interface LLMAdapter {
  generate(request: AIRequest): Promise<Result<AIResponse>>;
}

export const mockLLMAdapter: LLMAdapter = { /* 800ms delay + canned responses */ };
export const defaultLLMAdapter: LLMAdapter = mockLLMAdapter;
```

Production will swap `defaultLLMAdapter` for a router that picks between `groqLLMAdapter` and `openrouterLLMAdapter` based on BYOK config.

### 16.5 AI Request Logging

`ai_request_logs` table tracks every call: feature (`narrative`/`drafting`/`anomaly`), provider, model, prompt_token_count, completion_token_count, latency_ms, success, error_message. Used for audit, cost tracking, and tenant rate limiting (`rate_limit_per_minute` default 60).

---

## 17. Backup Strategy

### 17.1 Design Principles (plan §13)

- **24-hour backup cycle**: scheduler ticks every 24h (production) / 5min (dev)
- **AES-256-GCM encryption** (Web Crypto API, PBKDF2 100k iterations, 12-byte random IV per archive)
- **Local IndexedDB vault** + offsite vault stub (S3 Glacier / Backblaze B2 future)
- **365-day rolling retention**: older archives auto-purged by weekly cron
- **Point-in-time restore UI**: admin selects archive by date, enters passphrase, restores
- **Mobile PROHIBITED** from generating/downloading/storing backups (plan §13.05)
- **Backups NEVER reside inside Supabase** — Postgres stores ONLY metadata; ciphertext lives in IndexedDB

### 17.2 Backup Pipeline (`src/infrastructure/backup/backup-service.ts`)

```
1. Serialize   → snapshot { parents, students, payments, ledger, expenses, personnel } to JSON
2. Compress    → gzip via CompressionStream('gzip')
3. Encrypt     → AES-256-GCM with fresh 12-byte random IV
4. Checksum    → SHA-256 hex of ciphertext (defense-in-depth alongside GCM auth tag)
5. Store       → IndexedDB vault (el-imtiyaz-backup-vault DB, archives object store)
6. Audit       → write 'backup.run' audit entry (sizeBytes, checksum, vaultLocation)
```

### 17.3 Encryption Details (`src/infrastructure/backup/aes-256.ts`)

```ts
// Key derivation: PBKDF2 with 100,000 iterations + per-tenant salt + SHA-256
export async function generateKey(passphrase: string, salt: Uint8Array): Promise<CryptoKey> {
  const baseKey = await subtle.importKey("raw", encodeUtf8(passphrase), { name: "PBKDF2" }, false, ["deriveKey"]);
  return subtle.deriveKey(
    { name: "PBKDF2", salt, iterations: 100_000, hash: "SHA-256" },
    baseKey,
    { name: "AES-GCM", length: 256 },
    /* extractable */ false,                    // NEVER exportable
    ["encrypt", "decrypt"],
  );
}

// Encrypt: fresh 12-byte IV per call (NEVER reuse IV with same key — GCM catastrophe)
export async function encrypt(plaintext, key): Promise<{ ciphertext, iv }> {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const cipherBuf = await subtle.encrypt({ name: "AES-GCM", iv, tagLength: 128 }, key, plaintext);
  return { ciphertext: new Uint8Array(cipherBuf), iv };
}

// Decrypt: Web Crypto auto-verifies GCM auth tag — throws on tamper
export async function decrypt(ciphertext, iv, key): Promise<Uint8Array> { /* ... */ }
```

### 17.4 IndexedDB Vault (`src/infrastructure/backup/indexed-db-vault.ts`)

- DB name: `el-imtiyaz-backup-vault`
- Object store: `archives` (keyPath: `id`)
- Indexes: `createdAt`, `retentionExpiresAt`
- Record shape: `{ id, metadata: BackupArchive, ciphertext: Uint8Array, iv: Uint8Array }`
- Metadata-only reads (`listArchiveMetadata`) — ciphertext fetched on-demand during restore
- `purgeExpired(maxAgeDays = 365)` — finds archives past retention, deletes them, returns IDs for audit

### 17.5 Backup Scheduler (`src/infrastructure/backup/backup-scheduler.ts`)

```ts
const PROD_TICK_MS = 24 * 60 * 60 * 1000;     // 24 hours
const DEV_TICK_MS  = 5 * 60 * 1000;            // 5 minutes

export function startBackupScheduler(repos: Repositories, getActor: () => SchedulerActor | null): () => void {
  const tickMs = import.meta.env.DEV ? DEV_TICK_MS : PROD_TICK_MS;
  const handle = setInterval(async () => {
    const actor = getActor() ?? { id: "system", name: "Système (scheduler)" };
    try {
      const result = await repos.backups.runBackup(actor.id, actor.name);
      // log success/failure
    } catch (err) {
      // defensive: scheduler never crashes from a backup failure
    }
  }, tickMs);
  return () => clearInterval(handle);
}
```

Started in `AppShell.tsx` after authentication, using current session user as actor at tick-time (not start-time).

### 17.6 Restore Flow

1. Admin opens Desktop → Settings → Sauvegardes
2. Selects archive by date (e.g., `backup-2026-07-22-020000.db`)
3. Enters passphrase
4. Clicks "Restaurer"
5. Pipeline:
   - Fetch ciphertext from IndexedDB
   - Decrypt (GCM auth tag verification — throws on tamper)
   - Decompress (gzip)
   - Verify SHA-256 checksum (bit-rot detection)
   - **Mock**: logs only, no state mutation
   - **Production (TODO)**: writes deserialized records back to Supabase in a single transaction via `restore_from_backup(archive_id)` SECURITY DEFINER function
6. Audit entry: `backup.restore` with durationMs + sizeBytes

### 17.7 Multi-Location Storage (Production Hardening)

`backup_archives.vault_location` column tracks where each copy lives:

- `indexeddb` (primary, fast restore)
- `local_drive` (secondary, USB-attached)
- `offsite_vault` (tertiary, S3 Glacier / Backblaze B2)

Production daemon should store primary in IndexedDB, copy to local external drive, upload to offsite vault.

### 17.8 Test Restore Discipline

- **Weekly**: automated test restore in staging
- **Monthly**: manual restore drill
- **Quarterly**: full disaster recovery simulation (delete staging, restore from offsite, measure RTO)

---

## 18. Business Workflow / Excel Logic

This section extracts the actual accounting rules from `Entire_Project_Plan.txt` (138 notes, 7,495 lines) and `Clients_Sheet_Merged.txt` (46 notes, 8,427 lines).

### 18.1 The Four-Layer Excel Model

The school's `Suivis clients 2026_2027.xlsx` workbook has 4 sheets forming a 4-layer data flow:

```
REF (foundation — vocabulary: parents, classes, towns)
  ↓ (named ranges — currently broken)
Devis (Layer 1 — Quote: 10 family quote templates)
  ↓ (MANUAL handoff — operator types formula)
ETAT 20262027 (Layer 2 — Ledger: 390 students, L/P/Q formulas — the engine)
  ↓ (VLOOKUPs — currently all #REF!)
BON (Layer 3 — Statement: print template)
```

### 18.2 ETAT Sheet — The Master Ledger

390 student rows × 54 columns. Column groups:

| Columns | Group | Purpose |
|---|---|---|
| B–K | Identity | INFOS, E-MAIL, NEM (phone), TUTEUR, NOM, niveau, CLASSE, OPTION, REMISE, JUSTIFICATION |
| L–Q | Pricing & Balance | DEVIS ANNUEL (formula), REMBOURCEMENT, DETTES, REGLEMENTS DETTES, TOTAL VERSEMENTS (formula), TOTAL*CREANCE (formula) |
| R–Y | Installments | FI (registration), V2 (2nd tuition), 2V (alt 2nd), v3 (3rd tuition), DISTINATION (town), 1T/T2/t3 (transport tranches) |
| Z–AE | Special Services | PSY1, PSY2, ORTH1, ORTH2, E-PLANT, Ratrapage (NOT in P formula) |
| AF–AL | Term Tracking | CREANCES SEPTEMBRE/DECEMBRE/MARS, etc. |
| AM | Hidden Payment Log | Cell comments with receipt details (e.g., `30000/05/05B11` = 30,000 DZD on 05/05, receipt book B11 #11) |

### 18.3 The Three Core Formulas (L, P, Q)

#### L — DEVIS ANNUEL (Annual Quote)

Pattern: `=registration + tuition + transport − discount`

```
L2: =25000+205000+35000-J2
```

Decoded:
- `25000` = registration fee (FI) for primary
- `205000` = tuition (Frais Scolarisation) for primary
- `35000` = transport for nearby town
- `-J2` = subtract discount typed in column J

**There is no automatic lookup** — the operator chooses components from the price table based on the student's `niveau` (G), `CLASSE` (H), `OPTION` (I), and `DISTINATION` (V), then types the formula by hand. This is a **manual handoff** from the Devis quote sheet.

The desktop app's `buildTuitionChargeEntries()` in `ledger.ts` automates this — it reads `PricingConfig.tuitionByGradeLevel[gradeLevel]` and generates 3 charge entries (one per tranche) with the discount applied.

#### P — TOTAL VERSEMENTS (Total Paid)

```
P2: =R2+S2+T2+U2+W2+X2+Y2
```

Sums 7 payment columns:
- R (FI) — registration fee paid
- S (V2) — 2nd tuition installment
- T (2V) — alternate 2nd installment (rarely used)
- U (v3) — 3rd tuition installment
- W (1T) — 1st transport tranche
- X (T2) — 2nd transport tranche
- Y (t3) — 3rd transport tranche

**Does NOT include** V (DISTINATION — text attribute, not a payment) or Z–AE (special services — tracked separately).

The desktop's `sumInstallmentsPaid(installments)` is the equivalent.

#### Q — TOTAL*CREANCE (Outstanding Balance)

```
Q2: =L2-P2
```

**Just `L - P`. Deliberately simple.**

> **Common mistake (documented in Clients_Sheet_Merged.txt)**: An earlier conceptual summary guessed the formula was `=L+DETTES−REMISE−P` (including prior-year debts and re-subtracting discount). That's NOT accurate. The actual formula is just `=L−P`. The discount is already baked into L (via `-J` in the L formula). DETTES (column N) is informational only — not used by any formula.

Edge cases:
- `Q = 0`: family paid in full
- `Q > 0`: family still owes (normal case)
- `Q < 0`: family overpaid (rare; should trigger reimbursement in column M)

The desktop's `totalOutstanding(installments)` is the equivalent.

### 18.4 Columns M, N, O (Stored but NOT in Formulas)

| Column | Header | Purpose | In formula? |
|---|---|---|---|
| M | REMBOURCEMENT (misspelled) | Money school owes back to family (overpayment, withdrawal refund, retroactive discount) | **No** — Devis subtracts it, ETAT does not |
| N | DETTES | Prior-year unpaid debts, carried forward | **No** — informational only |
| O | REGLEMENTS DETTES | Payments toward prior-year debts | **No** — informational only |

**Conceptual vs actual formula**: If you wanted Q to include prior-year debts, you'd change `=L-P` to `=L+N-P-O`. But the current file does NOT do this. The desktop app's ledger model is more accurate — every financial event is a ledger entry, and the balance is always computed by replay.

### 18.5 Payment Column Conventions

Typical values from 390 real student rows:

| Column | Typical | Notes |
|---|---|---|
| R (FI) | 25,000 / 30,000 / 18,000 | Matches registration component of L (primary=25K, collège/lycée=30K, pre-school=18K) |
| S (V2) | 70,000–150,000 | Big tuition installment. Often a formula like `=122000-25000` (base minus discount) |
| T (2V) | (mostly empty) | Used in ~10-20 rows for split 2nd payments |
| U (v3) | 70,000–90,000 | Final tuition installment |
| W (1T) | 30,000 | 1st transport tranche — almost always exactly 30,000 |
| X (T2) | 15,000 | 2nd transport tranche — almost always exactly 15,000 |
| Y (t3) | 10,000 | 3rd transport tranche — almost always exactly 10,000 |

**Transport tranches sum to 55,000** (highest tier) — the school's transport pricing is split into a 30/15/10 payment plan, NOT 3 equal tranches.

### 18.6 Discount (REMISE) Structure

Column J holds the discount, typed as a literal number OR an arithmetic formula showing components:

```
J7: =20000+25000           (discount = 45,000)
J14: =5000+10000+10000     (discount = 25,000)
```

Common discount amounts and meanings (from real data):

| Amount (DZD) | Likely reason |
|---|---|
| 5,000 | Sibling discount (small) |
| 10,000 | Sibling discount (medium) or early-payment |
| 15,000 | Staff-family discount |
| 18,000 | Hardship discount |
| 20,000 | Larger sibling discount |
| 22,000 | Negotiated discount |
| 25,000 | Promotional discount |
| 30,000 | Large negotiated discount |
| 50,000 | Major discount (full transport waiver?) |

The desktop app's 5 canonical discount codes (per official 2026-2027 schedule) codify these:

| Code | Amount | Trigger |
|---|---|---|
| `passage_palier` | −10,000 DA fixed | Grade-level transition |
| `seniority_5y` | −5% percentage | >5 years seniority |
| `full_annual` | −10% percentage | Full annual payment before June 30 |
| `highest_average` | −10% percentage | Highest average in grade level |
| `sibling_fixed` | −5,000 DA fixed per additional child | Each child beyond the first |

`computeSiblingDiscount(config, childrenCount)`: 3 children → 2 × sibling_fixed = −10,000 DA.

### 18.7 5% Early-Payment Bonus (Devis Sheet)

On the Devis sheet, each block computes:

```
D35: =+SUM(F15:F26)*0.05
```

5% of total tuition (column F), shown as a note on the printed quote. **Not automatically applied** — operator manually subtracts from L if family qualifies. The desktop's `full_annual` discount code (−10%) is more generous and replaces this.

### 18.8 Price Table (Reconstructed from L Formulas)

The school has **no single authoritative price list** — prices live only in L formulas + Devis blocks + operator memory. The desktop app's `pricing-seed.ts` codifies the official 2026-2027 schedule (see §5.13 above).

#### Registration Fees (FI)

| Amount (DZD) | Level |
|---|---|
| 18,000 | Pre-school (MS, GS) |
| 25,000 | Primary (PRIM) — most common |
| 28,000 | Variant (Devis only) |
| 30,000 | Collège (COLG) / Lycée (LYC) |
| 33,000 | Variant (Devis only) |

#### Tuition (Frais Scolarisation)

Pre-school: 125,000 (MS, GS)
Primary: 165K–230K (varies by class; 205K most common for CP/CE1/CE2, 210K for CM1/CM2)
Collège: 250K–330K (305K most common for 1AAM-4AAM)
Lycée: 340K–365K (1st year 340K, 2nd year 340-355K, 3rd year 355-365K)

#### Transport Fees (by distance)

| Amount (DZD) | Tier | Towns |
|---|---|---|
| 35,000 | T1 (nearby) | Boumerdès, Corso, Sahel, Figuier, Benyounes |
| 43,000 | T2 | (seen on Devis, rarely on ETAT) |
| 52,000 | T3 (medium) | Boudouaou, Ouled Moussa, Khemis Khenchela, Tidjelabine |
| 55,000 | T4 (far) | Cap Djenet, Bordj Mnaïl, Isser, Si Mustapha, Reghaia, Rouiba |

The desktop app's 4 transport destinations map roughly:
- `ville_boumerdes` (40,000 DA) — close to T1
- `tidjelabine_sahel_figuier_corso` (43,000 DA) — T2
- `boudouaou_thenia_zemmouri` (52,000 DA) — T3
- `autres` (55,000 DA) — T4

### 18.9 Special Services (Columns Z–AE)

| Service | Column | Typical (DZD) |
|---|---|---|
| Psychology session 1 | Z (PSY1) | 2,000–5,000 |
| Psychology session 2 | AA (PSY2) | 2,000–5,000 |
| Speech therapy 1 | AB (ORTH1) | 3,000–8,000 |
| Speech therapy 2 | AC (ORTH2) | 3,000–8,000 |
| E-PLANT (unclear) | AD | varies |
| Catch-up class | AE (Ratrapage) | 5,000–15,000 |

**NOT included in P formula** — tracked separately. The desktop's `complementaryServices` in `PricingConfig` codifies psychology (10,000/semester, 20,000/annual) and speech therapy (same).

### 18.10 Level Codes (niveau)

14 codes appear in the real sheet (per `Clients_Sheet_Merged.txt` §01 Level Codes):

| Code | Meaning |
|---|---|
| PRIM | Primary (broad) |
| COLG | Collège (middle school) |
| LYC | Lycée (high school) |
| GS | Grande Section (pre-school) |
| MS | Moyenne Section (pre-school) |
| PS | Petite Section (pre-school) |
| TPS | Toute Petite Section (pre-school) |
| AUTISTE | Special-needs class |
| NV2, NV3, NV4, NV5 | Non-gradeable variants |
| CLYC, LYCI | Lycée variants (typos in source data) |

The desktop's `GradeLevel` enum (14 values: prescolaire_1/2, 1ap-5ap, 1am-4am, 1ere/2eme/3eme_annee) is the canonical version. The ETAT schema's `niveau` enum accepts all 14 codes + `tolerateUnknown: true` for operator-invented variants.

### 18.11 OPTION Codes

| Code | Meaning | Notes |
|---|---|---|
| TRNSP | Transport needed | Canonical |
| TENSP | Variant / probable typo | 4 occurrences in real sheet — accepted |
| TRNP | Missing S from TRNSP | 1 occurrence — accepted |
| (empty) | No transport | — |

### 18.12 Daily Operational Workflow (from Clients_Sheet_Merged.txt §03 End-to-End Data Flow)

Traces one family (MAHAMED OUSSAID, 3 children) through the workbook:

1. **Quote generation (Devis)**: operator types family + children + fee components → formula sums → subtotal − discount − reimbursement = grand total. 5% early-payment bonus computed as note.
2. **Enrollment (ETAT)**: operator creates 3 rows, manually reconstructs L formula from Devis total: `L7 = =30000+250000+20000+52000-J7+1000 = 258000`
3. **First payment (registration, R)**: types 30,000 into R7. P updates to 30,000. Q updates to 228,000. Operator leaves comment on AM7: `30000/05/05B11`
4. **Second payment (1st tuition, S)**: types 100,000 into S7. P → 130,000. Q → 128,000.
5. **Transport payment (1T)**: types 30,000 into W7. P → 160,000. Q → 98,000.
6. **Statement (BON)**: family asks for printed statement — currently broken (all #REF!), operator prints directly from ETAT filtered by parent name.

The desktop app automates steps 2-5: `batch_register_family()` creates parent + students + ledger charge entries; `collect_payment()` handles payment + installment update + ledger entry + receipt + audit atomically.

### 18.13 Audit Trail (Column AM Comments)

Column AM holds cell comments with hand-typed receipt details:

```
Format: {amount}/{DD}/{MM}/{receiptBook}{receiptNumber}
Example: 30000/05/05B11 → 30,000 DZD on 05/MM, receipt book B, receipt 11
Example: 250000/07/05B11 → 250,000 DZD on 07/05
```

~80 comments exist in the real file. The desktop app replaces this with the formal `audit_logs` table — every payment collection writes a `payment.create` audit entry with full before/after JSON, receipt number, actor, timestamp, IP, user agent.

### 18.14 Excel Engine Deprecation (plan §14, §16)

> The embedded Excel formula engine (SUM, VLOOKUP, INDEX, MATCH, IF, etc.), Devis quote sheet reproduction, cell-matching logic, and column-AM text parsers are **100% deprecated and purged**. Excel survives only as a two-way data bridge: bulk student import and report export.

The desktop's Excel import engine (`src/infrastructure/excel/import-engine/`) is a **schema-driven data bridge** — it parses cells, validates, coerces types, deduplicates, and stores records. It does NOT evaluate formulas, reproduce Devis sheets, or parse column-AM comments.

---

## 19. Iteration History

16 iterations shipped. Final state: 1,180 passing tests, 0 typecheck errors, build clean.

| Iteration | Date | Headline | Tests |
|---|---|---|---|
| **1** | 2026-07 | Project foundation: Electron + Vite + React + TS + Tailwind. Architecture layers (core/domain/infrastructure/features). 8 entity modules, 17 repository contracts, mock layer with seed data, 7 feature hubs. | ~80 |
| **2** | 2026-07 | Admin Pricing Configuration (no hardcoded amounts). CRM Batch Registration (4-step atomic wizard). Parent Detail Drawer. Counter Payment Modal with proof capture. Expense workflow (submit/approve/disburse/settle). | ~120 |
| **3** | 2026-07 | Unified Modal System (`UnifiedModal` with dialog/drawer variants). PageTabs primitive. Refactored 5 existing modals. Profile page route. | ~158 |
| **4** | 2026-07 | Critical CSS pipeline fix (tailwind.config.js was gitignored). Truly unified modals (0 raw Dialog call sites). Consistent tab navigation with icons + count badges. 158 Vitest tests. Vite code-splitting (10 vendor chunks). | 158 |
| **5** | 2026-07 | **Ledger-based accounting engine** (single source of truth — every balance computed by replay). Dynamic schema-driven Excel bulk import. Reconciliation engine (8 integrity checks). 115 new tests (273 total). | 273 |
| **6** | 2026-07-28 | **Default pricing overhaul** — official 2026-2027 fee schedule: 14 grade-level tuitions, 4 transport destinations, 5 canonical discounts, complementary services, 2,000 DA 2nd apron. 7 mock stub fixes. Dynamic Excel importer wired into UI. | 330 |
| **7** | 2026-07-28 | Final P3 roadmap closure. Cmd+K command palette unified (0 raw Dialog exceptions). Sliding ink-bar/pill tab indicators. Workflow monitor. AES-256 backup daemon. DAG editor. AI scaffold (mock LLM adapter). Arabic RTL. Search index. | 393 |
| **8** | 2026-07-28 | **Personnel module expansion** — 5 new staff roles (Manager, Buyer, Driver, WarehouseWorker, Worker). 7 role-based dashboards. Onboarding wizard (11 steps). Task management (Kanban). Internal chat. | 723 |
| **9** | 2026-07-29 | Comprehensive requirements overhaul. Dashboard access control (teachers redirected to /personnel). Integrated Calendar View. Alert Creator + Alert Detail modals. Flexible installment schedules (per-parent due-date overrides). Automated overdue alert generator. | 807 |
| **10** | 2026-07-29 | Plan compliance sweep — read Entire_Project_Plan.txt (138 notes, 7,495 lines). Teacher Activity Ledger (Releve). Audit Log placement. Password Governance (re-auth + global session revocation). | 836 |
| **11** | 2026-07-29 | **Excel Import Engine reintegration** — ported standalone CommonJS package to TypeScript ESM. Multi-schema support (ETAT + BON + Devis + REF). Idempotent upsert. Per-run audit trail. JSON + Excel report generation. Particle animation engine reintegration. | 980 |
| **12** | 2026-07-29 | **Supabase integration** — 24 SQL migrations (~2,500 LOC). 11 Edge Functions. 60+ RLS policies. 50+ performance indexes. 5 materialized views. 14 SECURITY DEFINER functions. Complete multi-tenant schema. Unified Approval Workflow. | 1,004 |
| **13** | 2026-07-29 | **UI-driven configuration** — eliminated need to edit `.env` files. `system_settings` table (40+ settings across 8 categories). `update-server-secret` Edge Function. Electron IPC handlers for local config. SystemConfig service. | 1,015 |
| **14** | 2026-07-31 | **Sync fixes + Excel import alignment**. Fixed `selectDefaultRepositories()` crash. Schema aligned with business reality (14 niveau codes, OPTION typos, optional NEM). `tolerateUnknown` flag for enum fields. Mock-data sync exclusion (defense-in-depth). Auto-sync on internet reconnect. | 1,027 |
| **15** | 2026-07-31 | **Settings page complete redesign**. Audited non-functional settings (100% decorative GeneralTab, no-op RBAC save, hardcoded backup config). Removed duplicate content (4 divergent language storage layers). Design system consistency (all settings use shared primitives). | 1,149 |
| **16** | 2026-07-31 | **Settings tab navigation refactor + codebase structure cleanup**. Settings page now uses default `variant="elevated"` tab navigation (matches every other Hub). Extracted 10 tab components into separate files. Dead code removal (confirm-dialog.tsx, mock-data-flag.ts). | 1,180 |

---

## 20. Edge Functions Reference (11 Total)

All Edge Functions are Deno/TypeScript serverless functions running on Supabase's edge network. They handle:
- Sensitive operations (API key proxying for AI, email, push)
- Atomic database operations (payment collection, refunds)
- Scheduled tasks (overdue scan, backup purge)
- Complex business logic (workflow DAG execution)

### 20.1 Shared Utilities (`_shared/`)

- `cors.ts` — CORS headers, `handleOptions()`, `jsonError()`, `jsonOk()`
- `supabase.ts`:
  - `createServiceRoleClient()` — bypasses RLS (server-side only, NEVER in client)
  - `createAnonClient()` — gated by RLS
  - `extractAuthContext(req)` — extracts `{ userId, userProfileId, tenantId, email, role, roles, permissions }` from JWT
  - `requirePermission(ctx, permission)` — boolean check (super_admin always true)
  - `requireRole(ctx, role)` — boolean check (super_admin always true)
  - `writeAuditLog(tenantId, action, entityType, entityId, actorId, actorName, before, after, note, requestId)`

### 20.2 Function Inventory

| Function | Method | Auth | Purpose |
|---|---|---|---|
| `approve-signup-request` | POST | JWT + super_admin/support_staff | Approve/reject web-initiated registration. Binds auth.users.id to parent/student profile. |
| `bind-activation-code` | POST | JWT (Google OAuth parent) | Bind 6-7 digit activation code to caller's auth.users.id. Calls `bind_activation_code()` RPC. |
| `update-server-secret` | POST/DELETE | JWT + super_admin | Update Edge Function env vars via Supabase Management API. Allow-list of 11 keys. |
| `collect-payment` | POST | JWT + collect_payment | Atomic payment collection — payment + installment update + ledger entry + receipt + audit. Wraps `public.collect_payment()` RPC. |
| `refund-payment` | POST | JWT + refund_payment | Atomic refund — marks original payment refunded, inserts reversal, reverses ledger entry, updates installment. Wraps `public.refund_payment()` RPC. |
| `ai-proxy` | POST | JWT + use_ai | Proxy AI requests to Groq/OpenRouter. API keys held in Edge Function env (never sent to client). Logs to `ai_request_logs`. |
| `workflow-execute` | POST | JWT + execute_workflow | Execute a published workflow DAG. Topological sort, cycle detection, node-by-node execution. |
| `run-overdue-scan` | POST (manual) or cron | JWT + view_financials (manual) / service_role (cron) | Daily overdue installment scan. Generates idempotent alerts. Called by Supabase Cron daily at 08:00 UTC. |
| `expire-pending-approvals` | cron | service_role | Expire approval requests past 7-day window. Daily cron. |
| `refresh-materialized-views` | cron | service_role | Refresh all 5 materialized views concurrently. Nightly cron. |
| `purge-expired-backups` | POST (manual) or cron | JWT + manage_backups (manual) / service_role (cron) | Purge backup archives past 365-day retention. Weekly cron (Sunday 03:00 UTC). |

### 20.3 Cron Schedule (in `supabase/config.toml`)

```toml
# Daily at 08:00 UTC — overdue scan
[cron.run_overdue_scan]
schedule = "0 8 * * *"
function = "run-overdue-scan"

# Daily at 04:00 UTC — expire pending approvals
[cron.expire_pending_approvals]
schedule = "0 4 * * *"
function = "expire-pending-approvals"

# Daily at 03:00 UTC — refresh materialized views
[cron.refresh_materialized_views]
schedule = "0 3 * * *"
function = "refresh-materialized-views"

# Weekly Sunday at 02:00 UTC — purge expired backups
[cron.purge_expired_backups]
schedule = "0 2 * * 0"
function = "purge-expired-backups"
```

---

## 21. Storage Setup

### 21.1 Ten Buckets (all private — signed URLs only)

| # | Bucket | Purpose | Max Size | MIME Types |
|---|---|---|---|---|
| 1 | `payment-proofs` | Check scans + transfer receipts | 10 MB | jpeg, png, webp, pdf |
| 2 | `expense-receipts` | Vendor receipts | 10 MB | jpeg, png, webp, pdf |
| 3 | `receipts` | Auto-generated PDF receipts | 5 MB | pdf |
| 4 | `student-documents` | Birth certs, medical, contracts | 10 MB | jpeg, png, webp, pdf |
| 5 | `homework-attachments` | Teacher-uploaded PDFs/photos | 10 MB | jpeg, png, webp, pdf |
| 6 | `task-attachments` | Files attached to tasks | 10 MB | jpeg, png, webp, pdf, docx, xlsx, txt, csv |
| 7 | `chat-attachments` | Files shared in chat | 10 MB | jpeg, png, webp, pdf, xlsx, txt |
| 8 | `tenant-assets` | Logos, branding | 5 MB | jpeg, png, svg, webp |
| 9 | `ai-reports` | AI-generated PDFs | 5 MB | pdf, txt |
| 10 | `import-reports` | Excel/JSON import reports | 10 MB | xlsx, json, csv |

### 21.2 Folder Structure (Enforced by RLS)

```
<tenant_id>/<entity_id>/<filename>
```

Example: `00000000-0000-0000-0000-000000000001/payment-uuid-12345/check-scan.jpg`

RLS policy on `storage.objects` checks the first path segment matches the caller's `tenant_id`:

```sql
create policy "payment_proofs_write" on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'payment-proofs'
    and (storage.foldername(name))[1] = public.current_tenant_id()::text
    and public.has_any_role(array['super_admin', 'financial_officer', 'support_staff'])
  );
```

### 21.3 Per-Bucket Role Matrix

| Bucket | Read | Write | Delete |
|---|---|---|---|
| `payment-proofs` | super_admin, financial_officer, support_staff | super_admin, financial_officer, support_staff | super_admin |
| `expense-receipts` | super_admin, financial_officer, manager, buyer | all staff | super_admin |
| `receipts` | super_admin, financial_officer, support_staff, parent (own) | super_admin, financial_officer, support_staff (system) | super_admin |
| `student-documents` | super_admin, financial_officer, support_staff, teacher, manager | super_admin, support_staff | super_admin |
| `homework-attachments` | super_admin, teacher, parent, student, support_staff | super_admin, teacher | super_admin |
| `task-attachments` | all staff | all staff | super_admin |
| `chat-attachments` | all staff | all staff | super_admin |
| `tenant-assets` | super_admin, financial_officer | super_admin | super_admin |
| `ai-reports` | super_admin, financial_officer, teacher | super_admin, financial_officer | super_admin |
| `import-reports` | super_admin, financial_officer, support_staff | super_admin, financial_officer, support_staff | super_admin |

### 21.4 Signed URLs

- Default expiry: 5 minutes (configurable via `get_signed_url(bucket, path, expires_seconds)`)
- **Never cache signed URLs client-side** (plan §13.04)
- No public URLs — every access requires a signed URL

---

## 22. Key Files Quick Reference

### 22.1 Financial Engine (most critical)

| File | Purpose |
|---|---|
| `src/domain/model/payment.ts` | Payment/Installment/Receipt entities + `sumPaidPayments`, `overdueAmount`, `maxDaysOverdue`, `agingBucketFromDays`, `revenueByMonth`, `revenueByCategory`, `monthlyRevenue`, `totalOutstanding`, `installmentRemaining` |
| `src/domain/model/ledger.ts` | `LedgerEntry` + `deriveAccountId`, `computeAccountBalance` (replay), `computeParentSummary`, `createChargeEntry`/`createPaymentEntry`/`createAdjustmentEntry`/`createRefundEntry`/`createReversalEntry` factories, `buildTuitionChargeEntries`, `buildTransportChargeEntry`, `buildTransportChargeEntriesForDestination`, `maxDaysOverdueFromLedger`, `buildOverdueDueDateMap` |
| `src/domain/model/pricing.ts` | `PricingConfig` + `tuitionForGradeLevel`, `tuitionTranchesForGrade`, `transportForDestination`, `transportTranchesForDestination`, `applyDiscount`, `computeSiblingDiscount`, `findDiscountByCode` |
| `src/domain/reconcile.ts` | `reconcileLedger(entries)` + 8 integrity checks + 3 cross-checks |
| `src/infrastructure/mock/pricing-seed.ts` | Default 2026-2027 pricing (the ONLY hardcoded amounts) |
| `src/infrastructure/receipt-pdf.ts` | `generatePaymentReceiptPdf`, `generateAccountStatementPdf` (pdf-lib) |
| `supabase/migrations/0007_financial.sql` | SQL schema + `enforce_payment_proof` trigger + `update_installment_status` trigger + `compute_account_balance`/`compute_parent_balance`/`compute_parent_outstanding`/`compute_overdue_amount` functions |
| `supabase/migrations/0022_functions.sql` | `collect_payment()`, `refund_payment()` SECURITY DEFINER RPCs |

### 22.2 RBAC

| File | Purpose |
|---|---|
| `src/core/rbac/roles.ts` | 11-role enum + `STAFF_ROLES`, `ADMINISTRATIVE_ROLES`, `SUPERVISORY_ROLES`, `OPERATIONAL_ROLES` |
| `src/core/rbac/permissions.ts` | ~56 permissions + `DEFAULT_ROLE_PERMISSIONS` per role |
| `src/core/rbac/feature-registry.ts` | Canonical FeatureNode tree (Dashboard, Crm, Academics, Financials, Personnel, WorkflowAutomation, Routing, Settings) |
| `src/core/rbac/feature-gate.ts` | Pure `evaluate(requirement, ctx)` → AccessState |
| `src/core/rbac/session.ts` | `Session` interface + `can()`, `hasRole()`, `hasAnyRole()`, `isExpired()` |

### 22.3 Excel Import Engine

| File | Purpose |
|---|---|
| `src/infrastructure/excel/import-engine/import-engine.ts` | `ImportEngine` class — orchestrates parse → validate → upsert → report |
| `src/infrastructure/excel/import-engine/schemas/etat-schema.ts` | ETAT schema (master ledger — 14 fields, identity: NEM+NOM) |
| `src/infrastructure/excel/import-engine/schemas/devis-schema.ts` | DEVIS schema (quote engine — 8 fields) |
| `src/infrastructure/excel/import-engine/schemas/bon-schema.ts` | BON schema (client statement — 7 fields) |
| `src/infrastructure/excel/import-engine/schemas/ref-schema.ts` | REF schema (reference data — 3 fields, multi-table fan-out) |
| `src/infrastructure/excel/import-engine/validators/field-coercer.ts` | Type-aware coercion + `tolerateUnknown` for enum fields |
| `src/infrastructure/excel/import-engine/validators/row-validator.ts` | Per-row validation + summary-row detection + monthlyArray aggregation |
| `src/infrastructure/excel/import-engine/dedupe/upsert-matcher.ts` | Identity extraction (partial match supported) |
| `src/infrastructure/excel/import-engine/parsers/sheet-detector.ts` | Two-tier detection: sheet name regex → header signature |

### 22.4 Sync + Backup + AI

| File | Purpose |
|---|---|
| `src/infrastructure/sync/sync-service.ts` | `SyncService` singleton — queue, drain, retry, mock exclusion |
| `src/infrastructure/sync/online-detector.ts` | `OnlineDetector` — navigator.onLine + HTTP probe |
| `src/infrastructure/sync/sync-queue-store.ts` | IndexedDB-backed queue persistence |
| `src/infrastructure/backup/backup-service.ts` | `runBackup`, `restore`, `purgeExpired`, `deleteArchive` |
| `src/infrastructure/backup/aes-256.ts` | `generateKey` (PBKDF2 100k), `encrypt`, `decrypt`, `sha256` |
| `src/infrastructure/backup/indexed-db-vault.ts` | IndexedDB vault (`el-imtiyaz-backup-vault`) |
| `src/infrastructure/backup/backup-scheduler.ts` | 24h scheduler (5min in dev) |
| `src/infrastructure/ai/llm-adapter.ts` | `LLMAdapter` contract + `mockLLMAdapter` |
| `src/infrastructure/ai/ai-config-storage.ts` | BYOK config storage (AES-256-GCM in localStorage) |
| `src/domain/pii-mask.ts` | `maskPII`, `unmaskPII` (reversible) |

---

## 23. Mobile Rebuild — Priority Checklist

Based on the desktop implementation, here's what the mobile team should prioritize:

### 23.1 Must-Have (P0 — Core Business)

1. **Ledger-based accounting engine** — port `computeAccountBalance`, `computeParentSummary`, `createChargeEntry`/`createPaymentEntry` factories. Every balance MUST be computed by replay, never stored.
2. **PricingConfig** — port the 14 grade-level tuitions + 4 transport destinations + 5 canonical discounts. Use `pricing-seed.ts` values as defaults.
3. **Payment collection** — atomic `collect_payment()` with method-specific proof validation (cash→paid, check/transfer→pending, proof mandatory for non-cash).
4. **Installment schedule** — 3-tranche generation from PricingConfig, per-parent due-date overrides, cycle-based regeneration.
5. **Auth + RBAC** — Supabase Auth + role/permission resolution via `current_user_roles()`/`current_user_permissions()` RPCs. Same 11 roles + 56 permissions.
6. **Offline-first sync** — Room DB cache + sync queue + conflict resolution (last-write-wins for non-critical, surface-to-user for critical).
7. **Audit logging** — append-only, every state change, complete before/after JSON.
8. **Receipt PDF generation** — auto on payment entry, no manual button.

### 23.2 Should-Have (P1 — Operational)

9. **CRM batch registration** — atomic parent + N students + activation code.
10. **Attendance roll call** — 4 statuses, 30-second workflow.
11. **Grade entry** — D1/D2/Examen → `(D1+D2+2·Examen)/4` subject average, coefficient-weighted GPA.
12. **Expense workflow** — two-tier approval, no self-approval, receipt mandatory before settlement.
13. **Debt dashboard** — aging buckets (0-30/31-60/61-90/91-180/180+), top debtors.
14. **Overdue alert generator** — idempotent, daily scan, priority by days overdue.

### 23.3 Nice-to-Have (P2 — Enhancement)

15. **Excel import** — schema-driven, multi-sheet (ETAT/DEVIS/BON/REF), tolerant validation.
16. **Workflow DAG editor** — touch-friendly version of the canvas, Kahn's cycle detection.
17. **AI features** — narrative generator, drafting assistant, anomaly detector (all with PII masking + teacher/human review mandatory).
18. **Backup** — **MOBILE PROHIBITED** per plan §13.05. Do NOT implement.
19. **Calendar** — daily activity log + manual event scheduling.
20. **Chat** — direct/group/department/announcement channels.

### 23.4 Mobile-Specific Considerations

- **Camera capture** for payment proofs and expense receipts (plan §18.03)
- **FCM push notifications** — register token on sign-in, receive workflow alerts
- **GPS coordinates** on workforce attendance events (field staff)
- **QR code scanning** for activation codes and inventory SKUs
- **Offline Room DB** with sync queue (desktop is online-first; mobile MUST be offline-first)
- **Conflict resolution UI** — surface diffs for critical fields (payment amounts, grades, attendance)
- **NO backup functionality** — plan §13.05 explicitly prohibits mobile from generating/downloading/storing backups

---

## Appendix A: Currency & Locale

- **Currency**: DZD (Algerian Dinar), ISO 4217
- **Format**: `12 500 DZD` (fr-FR grouping with non-breaking space, no decimals, suffix ` DZD`)
- **Compact**: `12.5K DZD` for amounts ≥ 10,000
- **Parse**: `parseDzd("12 500 DZD") → 12500` (strips spaces, `DZD` suffix, comma→dot)
- **Locales**: `fr` (primary), `ar` (RTL, secondary), `en` (reserved)
- **Timezone**: `Africa/Algiers` (default)
- **Academic year**: `YYYY-YYYY+1` if current month ≥ September, else `YYYY-1-YYYY`

## Appendix B: ID Format Conventions

| Entity | Format | Example |
|---|---|---|
| Parent code | `PAR-{year}-{4-char suffix}` | `PAR-2025-A4F9` |
| Student code | `ELV-{year}-{6-digit seq}` | `ELV-2025-001234` |
| Receipt # | `REC-{year}-{6-digit seq}` | `REC-2025-000123` |
| Payment # | `PAY-{year}-{6-digit seq}` | `PAY-2026-001234` |
| Invoice # | `INV-{year}-{6-digit seq}` | `INV-2026-001234` |
| Ledger entry # | `LED-{year}-{6-digit seq}` | `LED-2026-001234` |
| Personnel ID | `EMP-{year}-{3-digit seq}` | `EMP-2025-014` |
| Backup file | `backup-YYYY-MM-DD-HHMMSS.db` | `backup-2026-07-22-020000.db` |
| Activation code | 6-7 digit numeric | `1234567` |

Random suffix alphabet for parent codes: `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` (no `I`, `O`, `0`, `1` — avoids confusion).

## Appendix C: Test Counts by Iteration

| Iteration | Tests |
|---|---|
| 1 | ~80 |
| 2 | ~120 |
| 3 | 158 |
| 4 | 158 |
| 5 | 273 |
| 6 | 330 |
| 7 | 393 |
| 8 | 723 |
| 9 | 807 |
| 10 | 836 |
| 11 | 980 |
| 12 | 1,004 |
| 13 | 1,015 |
| 14 | 1,027 |
| 15 | 1,149 |
| 16 | 1,180 |

Test methodology: unit (domain logic, RBAC, validators), integration (repositories, workflows), component (modals, dashboards), end-to-end (Excel real-file import).

---

**End of reference document.** For implementation details of any specific feature, refer to the cited source files. The desktop codebase is the canonical implementation — when in doubt, read the source.
