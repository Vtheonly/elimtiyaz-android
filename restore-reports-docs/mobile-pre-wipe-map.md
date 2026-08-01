# Mobile Pre-Wipe Capability Map — Commit `782bde1` ("aight mid")

> **Authoritative reference** for the El-Imtiyaz Staff Android app at the last commit
> before the UI redesign wiped the codebase. Every business capability that existed,
> every formula, every screen, every RPC. Use this document to restore the app.
>
> Repo: `/home/z/my-project/repos/mobile`
> Commit hash (short): `782bde1`
> Commit message: "aight mid"
> Application ID: `com.aistudio.elimtiyazstaff.bxmzlx`
> Version: `2.0.0` (versionCode 2)
> compileSdk: 36 · minSdk: 24 · targetSdk: 36

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Domain Model](#2-domain-model)
3. [Repository Contracts](#3-repository-contracts)
4. [Core Business Logic](#4-core-business-logic)
5. [Infrastructure Layer](#5-infrastructure-layer)
6. [Screen-by-Screen Breakdown](#6-screen-by-screen-breakdown)
7. [Navigation Graph](#7-navigation-graph)
8. [Test Coverage](#8-test-coverage)
9. [Dependencies](#9-dependencies)
10. [Gap List](#10-gap-list)

---

## 1. Architecture Overview

### 1.1 Package Layout

```
com.example
├── ElImtiyazApplication.kt        ← @HiltAndroidApp, MultiDexApplication, WorkManager config, FCM channels
├── MainActivity.kt                 ← single-activity host (edge-to-edge, setContent { ElImtiyazTheme { AppNavHost() } })
├── core/                           ← pure business logic (no I/O, no Android deps except Result.fromException)
│   ├── AuditActions.kt             ← wire-protocol action constants (mirrors desktop src/core/audit-actions.ts)
│   ├── Ledger.kt                   ← LedgerEntry + 5 enums + formatDzd() helpers
│   ├── LedgerEngine.kt             ← pure functions: charge/payment/adjustment/refund/reversal factories + balance/summary
│   ├── PiiMask.kt                  ← reversible PII masking (6 patterns)
│   ├── Rbac.kt                     ← 11 roles, 56 permissions, Session, FeatureGate
│   ├── Reconcile.kt                ← 10-check ledger reconciler (28 violation codes)
│   └── Result.kt                   ← Result<T> sealed + Errors factory (10 error codes + Supabase mapper)
├── domain/
│   ├── model/Models.kt             ← 15 immutable @Serializable data classes (all amounts Long = centimes)
│   └── repository/Repositories.kt  ← 13 repository interfaces + 14 input/result DTOs + StorageBuckets
├── di/
│   ├── DatabaseModule.kt           ← Room database + 5 DAOs + WorkManager singletons
│   ├── RepositoryModule.kt         ← @Binds 11 repository interfaces → implementations (8 Supabase + 3 Stub)
│   └── SupabaseModule.kt           ← SupabaseClientProvider + SupabaseClient + EncryptedSharedPreferences
├── infrastructure/
│   ├── notifications/ElImtiyazMessagingService.kt  ← FCM service + FcmTokenRegistrar
│   ├── room/
│   │   ├── Daos.kt                 ← 5 @Dao interfaces (parent/student/payment/ledger cache + sync_queue)
│   │   ├── ElImtiyazDatabase.kt    ← Room database v1 (5 entities, exportSchema=false)
│   │   └── Entities.kt             ← 5 cache entities mirroring Supabase tables + SyncQueueEntity
│   ├── stub/StubRepositories.kt    ← 3 stub repos (Notification, Debt, Installment) — empty list / no-op
│   ├── supabase/
│   │   ├── SupabaseClientProvider.kt   ← lazy singleton client (Auth+Postgrest+Realtime+Storage+Functions)
│   │   ├── SupabaseAuthRepository.kt   ← signIn/signOut/refreshSession/changePassword + demo fallback
│   │   ├── SupabaseParentRepository.kt ← CRUD + soft delete via deleted_at + audit log on every mutation
│   │   ├── SupabaseStudentRepository.kt← CRUD + batchRegisterFamily RPC + promoteStudents RPC
│   │   ├── SupabasePaymentRepository.kt← collect/refund via Edge Functions + adjust via RPC + audit
│   │   ├── SupabaseLedgerRepository.kt ← append/appendMany/reverse + summary (client-side replay) + reconcile
│   │   ├── SupabaseExpenseRepository.kt← submit/approve/reject/disburse/settleProof with workflow triggers
│   │   ├── SupabaseAuditRepository.kt  ← query + log (via write_audit_log RPC)
│   │   └── SupabaseStorageRepository.kt← uploadProof + createSignedUrl (path = tenantId/entityId/fileName)
│   └── sync/
│       ├── OnlineDetector.kt       ← ConnectivityManager callback → StateFlow<OnlineState>
│       ├── SyncService.kt          ← enqueue + snapshot (online + counts by status)
│       └── SyncWorker.kt           ← @HiltWorker drains queue (backoff 1000*2^attempts, max 5 attempts)
├── session/SessionManager.kt       ← @Singleton StateFlow<Session?>, restoreSession(), RBAC helpers
└── ui/
    ├── components/
    │   ├── ElComponents.kt         ← 13 base composables (ElCard, ElButton, ElTextField, ElAvatar, …)
    │   ├── ElComponentsExtended.kt ← 12 composables (ElScaffold, ElFab, ElStatCard, ElAlertBanner, ElDialog, …)
    │   └── ModernTabs.kt           ← ModernSecondaryTabRow + ModernBottomNavBar
    ├── theme/
    │   ├── Color.kt                ← 30+ brand tokens, 9 gradients, 9 role accent colors
    │   ├── Shapes.kt               ← 10 semantic shapes (ElCardShape=20dp, ElPillShape=50, ElButtonShape=14dp…)
    │   ├── Theme.kt                ← ElImtiyazTheme + ElDesignTokens + SemanticColors CompositionLocals
    │   └── Type.kt                 ← full Material 3 typography scale (15 styles)
    ├── navigation/
    │   ├── AppNavHost.kt           ← NavHost with auth gate, 13 composable destinations
    │   ├── LocalSession.kt         ← CompositionLocals for Session + TenantContext
    │   └── Routes.kt               ← 14 type-safe @Serializable routes (Navigation 2.8+)
    └── features/
        ├── academics/AcademicsHubScreen.kt       ← 4 tabs: RollCall, GradeEntry, HomeworkPush, ClassesDirectory
        ├── auth/ChangePasswordModal.kt           ← password strength validator + changePassword
        ├── auth/LoginScreen.kt                   ← email/password + 9 demo accounts
        ├── crm/BatchRegistrationScreen.kt        ← parent + N children atomic registration
        ├── crm/CrmHubScreen.kt                   ← 3 tabs: Parents, Élèves, Inscription
        ├── crm/ParentDetailScreen.kt             ← parent + children + ledger summary + Call/WhatsApp
        ├── crm/ParentsDirectoryScreen.kt         ← search + list + tap-to-call
        ├── crm/StudentDetailScreen.kt            ← student + siblings + family ledger summary
        ├── crm/StudentRosterScreen.kt            ← search + list
        ├── dashboard/DashboardHubScreen.kt       ← KPI carousel + AI assistant drawer + receipt modal
        ├── financials/CounterPaymentScreen.kt    ← manual parent ID + amount + method/category → collect
        ├── financials/DebtDashboardScreen.kt     ← aging buckets + totals
        ├── financials/ExpenseApprovalScreen.kt   ← expense list with approve/reject/disburse actions
        ├── financials/FinancialsHubScreen.kt     ← 5 tabs: Encaissement, Preuves, Tranches, Créances, Dépenses
        ├── financials/InstallmentScheduleScreen.kt← progress bar + installment cards (stub data)
        ├── financials/ProofScannerScreen.kt      ← WebP compression + upload (CameraX not integrated)
        ├── main/MainScreen.kt                    ← Scaffold with bottom-nav (5 hubs, filtered by RBAC)
        ├── personnel/PersonnelHubScreen.kt       ← 4 tabs: Employees, Releve, Audit, SignOut
        ├── settings/AuditLogScreen.kt            ← audit log list (limit 200)
        └── settings/SettingsScreen.kt            ← 4 tabs: Général, Sync, Config, Sécurité (placeholders only)
```

### 1.2 Layers and Dependency Direction

```
   ┌─────────────────────────────────────────────────────────────┐
   │   UI layer (Compose)                                        │
   │   ui.features.* → ui.components, ui.theme, ui.navigation    │
   │   ViewModels @HiltViewModel inject repositories + sessions  │
   └─────────────────────────────────────────────────────────────┘
                              │ depends on (downward)
                              ▼
   ┌─────────────────────────────────────────────────────────────┐
   │   Domain layer (pure Kotlin, no Android)                    │
   │   domain.model.*      ← immutable @Serializable data classes│
   │   domain.repository.* ← interfaces only (no implementations)│
   │   core.*              ← LedgerEngine, PiiMask, Rbac, Result │
   └─────────────────────────────────────────────────────────────┘
                              │ implemented by
                              ▼
   ┌─────────────────────────────────────────────────────────────┐
   │   Infrastructure layer                                      │
   │   infrastructure.supabase.* ← Supabase SDK (Postgrest+Auth+ │
   │     Storage+Functions+Realtime)                             │
   │   infrastructure.room.*     ← Room offline cache + queue    │
   │   infrastructure.sync.*     ← OnlineDetector + SyncWorker   │
   │   infrastructure.stub.*     ← 3 stubs (Notification, Debt,  │
   │     Installment) for not-yet-implemented features           │
   │   infrastructure.notifications.* ← FCM + token registrar    │
   └─────────────────────────────────────────────────────────────┘
                              │ wired by
                              ▼
   ┌─────────────────────────────────────────────────────────────┐
   │   DI layer (Hilt)                                           │
   │   di.DatabaseModule  ← Room + WorkManager                   │
   │   di.RepositoryModule ← 11 @Binds mappings                  │
   │   di.SupabaseModule  ← SupabaseClient + EncryptedPrefs      │
   │   @HiltAndroidApp on ElImtiyazApplication                   │
   │   @AndroidEntryPoint on MainActivity                        │
   │   @HiltViewModel on every ViewModel                         │
   │   @HiltWorker on SyncWorker                                 │
   └─────────────────────────────────────────────────────────────┘
```

### 1.3 DI Setup

**Hilt** with `SingletonComponent` scope. Every singleton is `@Singleton @Inject constructor`.

`RepositoryModule` binds interfaces to implementations:

| Interface | Implementation | Notes |
|---|---|---|
| `AuthRepository` | `SupabaseAuthRepository` | Falls back to demo session if Supabase unreachable |
| `AuditRepository` | `SupabaseAuditRepository` | Uses `write_audit_log` RPC (never direct INSERT) |
| `ParentRepository` | `SupabaseParentRepository` | Soft delete via `deleted_at` |
| `StudentRepository` | `SupabaseStudentRepository` | `batchRegisterFamily` + `promoteStudents` RPCs |
| `PaymentRepository` | `SupabasePaymentRepository` | `collect-payment` + `refund-payment` Edge Functions |
| `LedgerRepository` | `SupabaseLedgerRepository` | `ledger_entries` is immutable (RLS blocks UPDATE/DELETE) |
| `ExpenseRepository` | `SupabaseExpenseRepository` | 4 RPCs: `approve_expense`, `reject_expense`, `disburse_expense`, `settle_expense` |
| `StorageRepository` | `SupabaseStorageRepository` | Path = `{tenantId}/{entityId}/{fileName}` (RLS-enforced) |
| `NotificationRepository` | `StubNotificationRepository` | ⚠ Stub — returns empty list |
| `DebtRepository` | `StubDebtRepository` | ⚠ Stub — returns empty list |
| `InstallmentRepository` | `StubInstallmentRepository` | ⚠ Stub — returns empty list / Err |

ClassRepository, SubjectRepository, GradeRepository, AttendanceRepository, HomeworkRepository, PersonnelRepository, DepartmentRepository, DashboardRepository, PricingRepository — **NOT bound** (no implementation exists; the interfaces are declared in Repositories.kt but no Supabase impl class exists at this commit).

### 1.4 Navigation Pattern

**Single-activity, type-safe Compose Navigation 2.8+** with `@Serializable` route objects.

- `MainActivity` hosts `ElImtiyazTheme { AppNavHost(sessionState) }`.
- `AppNavHost` chooses start destination based on session:
  - `session == null` → `Routes.Login`
  - `session != null` → `Routes.Main`
- `LocalSession` CompositionLocal provides the session to deeply nested composables for RBAC checks.
- `LocalTenantContext` declared but **not yet provided** by any composable.
- On first launch `AppNavViewModel.restoreSession()` calls `SessionManager.restoreSession()` which calls `authRepository.refreshSession()`. The Supabase impl returns a default staff session if no JWT is found, so the app boots straight to `Main` without forcing login in dev/demo mode.

### 1.5 Offline-First Design

**Supabase is the source of truth.** Room is an offline cache + sync queue.

- **Reads**: repositories emit `Flow<T>` from Supabase SELECT queries (with `try/catch` falling back to empty list). Room cache DAOs exist but **are not yet read from** by the Supabase repos (the wiring is incomplete).
- **Writes**: every mutation goes directly to Supabase (RPC or INSERT). The `SyncService.enqueue` API exists and the `SyncQueueEntity` table exists, but the Supabase repositories do NOT enqueue — they call Supabase directly. Sync queue integration is **stubbed/incomplete**.
- **SyncWorker**: drains `pending` entries, exponential backoff (`1000ms * 2^attempts`), max 5 attempts, `isMock` entries skipped at enqueue AND drain (defense in depth). Dispatch by `entity` type to per-entity push functions (`pushParent`, `pushStudent`, `pushPayment`, …) — **but these push functions are empty stubs** (commented "Skipped here for brevity").
- **OnlineDetector**: `ConnectivityManager.NetworkCallback` + StateFlow. The HTTP probe described in the docstring is **not implemented** (only `connectivityActive` is set; `probeOk` stays `false`, so `online = false` always — a known bug).

### 1.6 Wire-Protocol Compatibility

Every audit action string, role code, permission code, ledger type/source/category/method/status code, reconcile violation code, and error code is **identical** to the desktop app's `src/core/*` TypeScript modules. Renaming any value requires a Supabase migration.

### 1.7 FCM Push Notifications

- `ElImtiyazMessagingService` extends `FirebaseMessagingService`.
- 4 notification channels created in `ElImtiyazApplication.onCreate`:
  - `el_imtiyaz_urgent` — IMPORTANCE_HIGH (sound + heads-up)
  - `el_imtiyaz_high` — IMPORTANCE_DEFAULT
  - `el_imtiyaz_medium` — IMPORTANCE_LOW
  - `el_imtiyaz_low` — IMPORTANCE_MIN
- FCM data message fields: `title`, `body`, `priority` (urgent/high/medium/low), `type`.
- `FcmTokenRegistrar.register(token)` calls `register_fcm_token` RPC with `p_user_id`, `p_token`, `p_platform="android"`.

---

## 2. Domain Model

All models live in `app/src/main/java/com/example/domain/model/Models.kt`. All are `@Serializable`, immutable (`val`), and use **Long (centimes)** for money — never Double. Mirror the desktop `src/domain/model/`.

### 2.1 `Parent`
```kotlin
Parent(
  id: String, tenantId: String,
  code: String,                 // PAR-{year}-{4-char}
  firstName: String, lastName: String,
  phone: String, whatsapp: String? = null,
  email: String? = null, occupation: String? = null,
  address: String? = null, transportDestination: String? = null,
  preferredLanguage: String = "fr",   // fr | ar | en
  avatarUrl: String? = null,
  createdAt: String, updatedAt: String,
)
// computed: fullName = "$firstName $lastName"
```
**Relationships**: one-to-many with `Student` (via `parentId`), one-to-many with `Payment`, `Installment`, `LedgerEntry`.

### 2.2 `Student`
```kotlin
Student(
  id: String, tenantId: String,
  code: String,                 // ELV-{year}-{6-digit}
  parentId: String,
  firstName: String, lastName: String,
  gender: String, birthDate: String, enrollmentDate: String,
  level: String,                // primaire | cem | lycee
  gradeLevel: String,           // 14 codes: prescolaire_1 ... 3eme_annee
  classId: String? = null,
  photoUrl: String? = null, medicalNotes: String? = null,
  status: String = "active",    // active | graduated | transferred | suspended | withdrawn
  createdAt: String, updatedAt: String,
)
// computed: fullName
```
**Relationships**: belongs to `Parent` (parentId), may belong to `AcademicClass` (classId), has many `Assessment`, `AttendanceRecord`.

### 2.3 `AcademicClass`
```kotlin
AcademicClass(
  id: String, tenantId: String,
  name: String, level: String, gradeYear: Int,
  homeroomTeacherId: String? = null, homeroomTeacherName: String? = null,
  room: String? = null,
  capacity: Int, enrolledCount: Int,
  academicYear: String,
)
```

### 2.4 `Subject`
```kotlin
Subject(
  id: String, tenantId: String,
  name: String, nameAr: String? = null,
  code: String, level: String,
  coefficient: Int, isExtracurricular: Boolean,
  passingGrade: Double = 10.0,
)
```

### 2.5 `Payment`
```kotlin
Payment(
  id: String, tenantId: String,
  receiptNumber: String,
  parentId: String, studentId: String? = null,
  amount: Long,                // centimes
  method: PaymentMethod,       // CASH | CHECK | TRANSFER
  status: PaymentStatus,       // PAID | PENDING | PARTIAL | OVERDUE | REFUNDED | CANCELLED
  category: PaymentCategory,   // TUITION | TRANSPORT | CANTEEN | UNIFORM | BOOKS | EXTRACURRICULAR | OTHER
  installmentId: String? = null,
  proofUrl: String? = null, notes: String? = null,
  collectedBy: String, collectedAt: String,
  createdAt: String, updatedAt: String,
)
```

### 2.6 `Installment`
```kotlin
Installment(
  id: String, tenantId: String,
  parentId: String, studentId: String? = null,
  category: PaymentCategory,
  label: String,               // e.g. "Tranche 1"
  amountDue: Long, amountPaid: Long,
  dueDate: String, paidDate: String? = null,
  status: PaymentStatus,
  academicCycle: String? = null,
  customSchedule: Boolean = false,
  customScheduleNote: String? = null,
)
// computed: remaining = (amountDue - amountPaid).coerceAtLeast(0L)
```

### 2.7 `Expense`
```kotlin
Expense(
  id: String, tenantId: String,
  requestCode: String,         // EXP-{year}-{3-digit}
  title: String, description: String,
  amount: Long,                // centimes
  category: String,            // utilities | supplies | maintenance | transport | event | salary | tax | rent | other
  payee: String,
  status: String,              // draft | submitted | approved | rejected | disbursed | settled
  submittedBy: String, submittedAt: String,
  approvedBy: String? = null, approvedAt: String? = null, approvalNote: String? = null,
  disbursedBy: String? = null, disbursedAt: String? = null,
  proofUrl: String? = null, proofUploadedBy: String? = null, proofUploadedAt: String? = null,
  anomalyScore: Double? = null, anomalyNote: String? = null,
)
```
**State machine**: `draft → submitted → {approved | rejected} → disbursed → settled`. No-self-approval enforced server-side by `enforce_expense_workflow_rules` trigger.

### 2.8 `Personnel`
```kotlin
Personnel(
  id: String, tenantId: String,
  userId: String? = null,
  firstName: String, lastName: String,
  staffCategory: String,       // teacher | administration | support | maintenance | driver | buyer | warehouse | worker
  roleId: String,              // Role.code
  departmentId: String? = null,
  position: String, phone: String, email: String? = null,
  hireDate: String, terminationDate: String? = null,
  salary: Long? = null, status: String = "active",
  avatarUrl: String? = null,
  weeklyHoursTarget: Int = 0, weeklyHoursLogged: Int = 0,
)
// computed: fullName
```

### 2.9 `Department`
```kotlin
Department(
  id: String, tenantId: String,
  name: String, description: String? = null,
  headPersonnelId: String? = null,
  parentDepartmentId: String? = null,
  colorHex: String? = null,
  archivedAt: String? = null,
)
```

### 2.10 `AuditLog`
```kotlin
AuditLog(
  id: String, tenantId: String,
  action: String,              // from AuditActions constants
  entityType: String, entityId: String,
  actorId: String, actorName: String, actorRole: String? = null,
  beforeJson: String? = null, afterJson: String? = null,
  note: String? = null,
  ipAddress: String? = null, userAgent: String? = null,
  occurredAt: String,
)
```

### 2.11 `Assessment` (grade)
```kotlin
Assessment(
  id: String, tenantId: String,
  studentId: String, subjectId: String, classId: String,
  term: String,                // T1 | T2 | T3
  academicYear: String,
  devoir1: Double? = null, devoir2: Double? = null,
  examen: Double? = null,
  subjectAverage: Double? = null,
  coefficient: Int,
  enteredBy: String, enteredAt: String,
)
```
**Formula (mobile UI)**: `subjectAverage = (devoir1 + devoir2 + (examen × 2)) / 4.0` (from `AcademicsHubScreen.GradeEntryScreen`).

### 2.12 `AttendanceRecord`
```kotlin
AttendanceRecord(
  id: String, tenantId: String,
  studentId: String, classId: String,
  date: String,                // YYYY-MM-DD
  session: String,             // morning | afternoon | both
  status: String,              // present | absent_excused | absent_unexcused | late
  note: String? = null,
  recordedBy: String, recordedAt: String,
  syncedAt: String? = null,
)
```

### 2.13 `Homework`
```kotlin
Homework(
  id: String, tenantId: String,
  classId: String, subjectId: String, subjectName: String,
  teacherId: String, teacherName: String,
  title: String, description: String, dueDate: String,
  attachments: List<String> = emptyList(),
  academicYear: String, createdAt: String,
  pushedAt: String? = null,
  acknowledgedCount: Int = 0,
)
```

### 2.14 `AppNotification`
```kotlin
AppNotification(
  id: String, tenantId: String,
  title: String, body: String,
  type: String,                // payment_overdue | expense_pending | attendance_alert | homework | audit | system | message | custom
  priority: String,            // low | medium | high | urgent
  source: String,              // system | manual | workflow | schedule | audit
  sourceLabel: String,
  entityType: String? = null, entityId: String? = null,
  targetUserId: String? = null, targetRole: String? = null,
  triggeredAt: String? = null, readAt: String? = null,
  createdAt: String, createdBy: String,
)
```

### 2.15 `DashboardKpi`
```kotlin
DashboardKpi(
  totalStudents: Int, totalParents: Int, totalStaff: Int,
  monthlyRevenue: Long, outstandingDebt: Long,
  pendingExpenses: Int, attendanceRateToday: Double,
  overdueAlerts: Int,
)
```

### 2.16 `DebtSummary`
```kotlin
DebtSummary(
  parentId: String, parentName: String, parentPhone: String,
  studentCount: Int, outstandingAmount: Long,
  daysOverdue: Long,
  bucket: String,              // 0_30 | 31_60 | 61_90 | 91_180 | 180_plus
)
```

### 2.17 `PricingConfig` & `GradeLevelTuition`
```kotlin
PricingConfig(
  id: String, tenantId: String,
  isActive: Boolean,
  registrationFee: Long, latePenaltyPerDay: Long, secondApronFee: Long,
  updatedAt: String,
)

GradeLevelTuition(
  id: String, pricingConfigId: String,
  gradeLevel: String, annualAmount: Long,
  tranche1: Long, tranche2: Long, tranche3: Long,
)
```

### 2.18 Ledger value types (in `core/Ledger.kt`)

`LedgerEntry` itself lives in `core.Ledger` (not in domain/model), because it's the atomic unit of the accounting engine:

```kotlin
LedgerEntry(
  id: String, tenantId: String,
  accountId: String,           // derived: "parent:{p}:category:{c}[:student:{s}]"
  parentId: String, studentId: String?,
  category: PaymentCategory,
  amount: Long,                // signed: +charge, -payment, ±adjustment, -refund, -original=reversal
  type: LedgerEntryType,       // CHARGE | PAYMENT | ADJUSTMENT | REFUND | REVERSAL | TRANSFER
  sourceType: LedgerSourceType,// INSTALLMENT | PAYMENT | EXPENSE | ADJUSTMENT | REFUND | BULK_IMPORT | MANUAL_ENTRY
  sourceId: String,
  method: PaymentMethod?,
  receiptNumber: String?, paymentStatus: PaymentStatus?,
  reversesId: String?,
  description: String,
  actorId: String, actorName: String,
  at: String,                  // ISO timestamp
  metadata: Map<String, Any?>,
)
```

Plus two summary types in `core/LedgerEngine.kt`:
```kotlin
AccountBalance(accountId, parentId, studentId?, category, balance, totalCharged,
               totalPaid, totalAdjusted, totalRefunded, totalCleared, totalPending,
               entryCount, lastActivityAt?)

ParentLedgerSummary(parentId, parentName, totalOutstanding, totalOverdue,
                    totalCharged, totalPaid, totalCleared, totalPending,
                    totalAdjusted, totalRefunded, accounts: List<AccountBalance>,
                    entryCount, lastActivityAt?)
```

---

## 3. Repository Contracts

All contracts live in `app/src/main/java/com/example/domain/repository/Repositories.kt`. Mutations return `Result<T>`; live reads return `Flow<T>`. Every mutation is audit-logged via `write_audit_log` RPC.

### 3.1 `AuthRepository`
```kotlin
suspend fun signIn(email: String, password: String): Result<Session>
suspend fun signOut(): Result<Unit>
suspend fun refreshSession(): Result<Session?>
suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit>
fun observeSession(): Flow<Session?>
```

### 3.2 `ParentRepository`
```kotlin
fun observe(): Flow<List<Parent>>
fun observeById(id: String): Flow<Parent?>
fun search(query: String): Flow<List<Parent>>
suspend fun createParent(input: CreateParentInput, actorId: String, actorName: String): Result<Parent>
suspend fun updateParent(id: String, input: UpdateParentInput, actorId: String, actorName: String): Result<Parent>
suspend fun deleteParent(id: String, actorId: String, actorName: String): Result<Unit>  // soft delete
```
Inputs: `CreateParentInput(firstName, lastName, phone, email?, occupation?, address?, transportDestination?, preferredLanguage="fr")`, `UpdateParentInput` (all nullable).

### 3.3 `StudentRepository`
```kotlin
fun observe(): Flow<List<Student>>
fun observeByParent(parentId: String): Flow<List<Student>>
fun observeByClass(classId: String): Flow<List<Student>>
fun observeById(id: String): Flow<Student?>
fun search(query: String): Flow<List<Student>>
suspend fun createStudent(input: CreateStudentInput, actorId: String, actorName: String): Result<Student>
suspend fun updateStudent(id: String, input: UpdateStudentInput, actorId: String, actorName: String): Result<Student>
suspend fun batchRegister(parent: CreateParentInput, students: List<CreateStudentInput>, actorId: String, actorName: String): Result<BatchRegisterResult>
suspend fun promoteStudents(academicYear: String, decisions: List<PromotionDecision>, actorId: String, actorName: String): Result<Unit>
```
- `CreateStudentInput(firstName, lastName, gender, birthDate, level, gradeLevel, classId?, parentId?, medicalNotes?)`
- `BatchRegisterResult(parent: Parent, students: List<Student>, activationCode: String?)` — atomic via `batch_register_family` RPC.
- `PromotionDecision(studentId, decision, note?)`

### 3.4 `ClassRepository`
```kotlin
fun observe(): Flow<List<AcademicClass>>
fun observeByLevel(level: String): Flow<List<AcademicClass>>
fun observeById(id: String): Flow<AcademicClass?>
suspend fun createClass(input: CreateClassInput, actorId: String, actorName: String): Result<AcademicClass>
suspend fun updateClass(id: String, input: UpdateClassInput, actorId: String, actorName: String): Result<AcademicClass>
suspend fun deleteClass(id: String, actorId: String, actorName: String): Result<Unit>
```

### 3.5 `SubjectRepository`
```kotlin
fun observe(): Flow<List<Subject>>
fun observeByLevel(level: String): Flow<List<Subject>>
fun observeByClass(classId: String): Flow<List<Subject>>
suspend fun createSubject(input: CreateSubjectInput, actorId: String, actorName: String): Result<Subject>
suspend fun updateSubject(id: String, input: UpdateSubjectInput, actorId: String, actorName: String): Result<Subject>
suspend fun archiveSubject(id: String, actorId: String, actorName: String): Result<Unit>
suspend fun assignSubjectToClass(classId: String, subjectId: String, teacherId: String?, weeklyHours: Int, coefficient: Int, actorId: String, actorName: String): Result<Unit>
```

### 3.6 `GradeRepository`
```kotlin
fun observeForStudent(studentId: String, term: String, academicYear: String): Flow<List<Assessment>>
fun observeForClass(classId: String, subjectId: String, term: String, academicYear: String): Flow<List<Assessment>>
suspend fun enterGrade(input: EnterGradeInput, actorId: String, actorName: String): Result<Assessment>
```
`EnterGradeInput(studentId, subjectId, classId, term, academicYear, devoir1?, devoir2?, examen?, coefficient)`.

### 3.7 `AttendanceRepository`
```kotlin
fun observeByClass(classId: String, date: String): Flow<List<AttendanceRecord>>
fun observeByStudent(studentId: String): Flow<List<AttendanceRecord>>
suspend fun recordRollCall(classId: String, date: String, session: String, records: List<RollCallEntry>, actorId: String, actorName: String): Result<Unit>
suspend fun alertAbsences(studentIds: List<String>, actorId: String, actorName: String): Result<Unit>
```
`RollCallEntry(studentId, status, note?)`.

### 3.8 `HomeworkRepository`
```kotlin
fun observeForClass(classId: String): Flow<List<Homework>>
fun observeForTeacher(teacherId: String): Flow<List<Homework>>
suspend fun push(input: PushHomeworkInput, actorId: String, actorName: String): Result<Homework>
```
`PushHomeworkInput(classId, subjectId, title, description, dueDate, attachments, academicYear)`.

### 3.9 `PaymentRepository`
```kotlin
fun observe(): Flow<List<Payment>>
fun observeByParent(parentId: String): Flow<List<Payment>>
fun observeByStudent(studentId: String): Flow<List<Payment>>
fun observeById(id: String): Flow<Payment?>
suspend fun collect(input: CollectPaymentInput, actorId: String, actorName: String): Result<Payment>
suspend fun refund(paymentId: String, reason: String, actorId: String, actorName: String): Result<Payment>
suspend fun adjust(input: AdjustAccountInput, actorId: String, actorName: String): Result<Unit>
```
- `CollectPaymentInput(parentId, studentId?, amount, method, category, installmentId?, notes?, checkNumber?, checkBankName?, checkIssueDate?, checkClearanceDate?, transferReference?, transferSourceBank?, proofPath?)`
- `AdjustAccountInput(parentId, studentId?, category, amount, reason, receiptRef?)`

### 3.10 `InstallmentRepository`
```kotlin
fun observeByParent(parentId: String): Flow<List<Installment>>
fun observeByStudent(studentId: String): Flow<List<Installment>>
fun observeById(id: String): Flow<Installment?>
suspend fun markPaid(id: String, actorId: String, actorName: String): Result<Installment>
suspend fun updateDueDate(id: String, dueDate: String, note: String?, actorId: String, actorName: String): Result<Installment>
suspend fun regenerateForCycle(parentId: String, cycle: String, actorId: String, actorName: String): Result<List<Installment>>
suspend fun findOverdue(): Result<List<Installment>>
```
⚠ **Stubbed** — `StubInstallmentRepository` returns empty list / `Err(notFound)`.

### 3.11 `DebtRepository`
```kotlin
fun observeSummary(): Flow<List<DebtSummary>>
fun observeParentProfile(parentId: String): Flow<ParentFinancialProfile?>
suspend fun sendReminder(parentId: String, actorId: String, actorName: String): Result<Unit>
```
`ParentFinancialProfile(parentId, parentName, totalDue, totalPaid, totalOutstanding, overdueAmount, installments, recentPayments)`.
⚠ **Stubbed** — `StubDebtRepository` returns empty list / null / Ok.

### 3.12 `ExpenseRepository`
```kotlin
fun observe(): Flow<List<Expense>>
fun observeByStatus(status: String): Flow<List<Expense>>
fun observeById(id: String): Flow<Expense?>
suspend fun submit(input: SubmitExpenseInput, actorId: String, actorName: String): Result<Expense>
suspend fun approve(id: String, note: String, actorId: String, actorName: String): Result<Expense>
suspend fun reject(id: String, reason: String, actorId: String, actorName: String): Result<Expense>
suspend fun disburse(id: String, actorId: String, actorName: String): Result<Expense>
suspend fun settleProof(id: String, proofPath: String, finalAmount: Long, actorId: String, actorName: String): Result<Expense>
```
`SubmitExpenseInput(title, description, amount, category, payee, urgency="normal")`.

### 3.13 `PersonnelRepository`
```kotlin
fun observe(): Flow<List<Personnel>>
fun observeByCategory(category: String): Flow<List<Personnel>>
fun observeById(id: String): Flow<Personnel?>
fun observeByUserId(userId: String): Flow<Personnel?>
suspend fun createPersonnel(input: CreatePersonnelInput, actorId: String, actorName: String): Result<Personnel>
suspend fun updatePersonnel(id: String, input: UpdatePersonnelInput, actorId: String, actorName: String): Result<Personnel>
suspend fun deletePersonnel(id: String, actorId: String, actorName: String): Result<Unit>
```

### 3.14 `DepartmentRepository`
```kotlin
fun observe(): Flow<List<Department>>
fun observeById(id: String): Flow<Department?>
suspend fun createDepartment(input: CreateDepartmentInput, actorId: String, actorName: String): Result<Department>
suspend fun archiveDepartment(id: String, actorId: String, actorName: String): Result<Unit>
suspend fun unarchiveDepartment(id: String, actorId: String, actorName: String): Result<Unit>
```

### 3.15 `AuditRepository`
```kotlin
fun observe(limit: Int = 100): Flow<List<AuditLog>>
fun observeByEntity(entityType: String, entityId: String): Flow<List<AuditLog>>
suspend fun query(filter: AuditFilter): Result<List<AuditLog>>
suspend fun log(input: AuditLogInput): Result<AuditLog>     // via write_audit_log RPC
```
`AuditFilter(action?, entityType?, entityId?, actorId?, from?, to?, limit=100, offset=0)`.

### 3.16 `NotificationRepository`
```kotlin
fun observe(): Flow<List<AppNotification>>
fun observeForSession(session: Session): Flow<List<AppNotification>>
suspend fun markRead(id: String): Result<Unit>
suspend fun markAllRead(): Result<Unit>
suspend fun dismiss(id: String): Result<Unit>
```
⚠ **Stubbed** — `StubNotificationRepository` returns empty list / Ok.

### 3.17 `DashboardRepository`
```kotlin
fun observeKpis(): Flow<DashboardKpi?>
fun observeRevenueLast12Months(): Flow<List<RevenuePoint>>
fun observeDebtByAging(): Flow<List<DebtSummary>>
suspend fun refreshKpis(): Result<Unit>
```
`RevenuePoint(label, amount)`. **No implementation at this commit.**

### 3.18 `PricingRepository`
```kotlin
fun observe(): Flow<PricingConfig?>
fun observeGradeLevelTuition(): Flow<List<GradeLevelTuition>>
suspend fun updateRegistrationFee(amount: Long, actorId: String, actorName: String): Result<Unit>
suspend fun updateLatePenalty(amount: Long, actorId: String, actorName: String): Result<Unit>
suspend fun updateTuitionForGradeLevel(gradeLevel: String, annualAmount: Long, tranches: Triple<Long, Long, Long>, actorId: String, actorName: String): Result<Unit>
```
**No implementation at this commit.**

### 3.19 `LedgerRepository`
```kotlin
fun observe(): Flow<List<LedgerEntry>>
fun observeByParent(parentId: String): Flow<List<LedgerEntry>>
fun observeByAccount(accountId: String): Flow<List<LedgerEntry>>
suspend fun append(entry: LedgerEntry): Result<LedgerEntry>
suspend fun appendMany(entries: List<LedgerEntry>): Result<List<LedgerEntry>>
suspend fun reverse(originalId: String, reason: String, actorId: String, actorName: String): Result<LedgerEntry>
suspend fun summary(parentId: String): Result<ParentLedgerSummary>     // client-side replay
suspend fun reconcile(): Result<Reconcile.Report>
```

### 3.20 `StorageRepository`
```kotlin
suspend fun uploadProof(bucket: String, entityId: String, fileName: String, bytes: ByteArray, mimeType: String): Result<String>
suspend fun createSignedUrl(bucket: String, path: String, expiresInSeconds: Long = 300): Result<String>
```

### 3.21 `StorageBuckets` (object)
10 bucket constants:
- `PAYMENT_PROOFS = "payment-proofs"`
- `EXPENSE_RECEIPTS = "expense-receipts"`
- `RECEIPTS = "receipts"`
- `STUDENT_DOCUMENTS = "student-documents"`
- `HOMEWORK_ATTACHMENTS = "homework-attachments"`
- `TASK_ATTACHMENTS = "task-attachments"`
- `CHAT_ATTACHMENTS = "chat-attachments"`
- `TENANT_ASSETS = "tenant-assets"`
- `AI_REPORTS = "ai-reports"`
- `IMPORT_REPORTS = "import-reports"`

---

## 4. Core Business Logic

### 4.1 `AuditActions` (wire-protocol constants)

Object holding ~60 string constants. They appear verbatim in the `audit_logs.action` column in Supabase. Grouped:

- **Auth** (5): `auth.login`, `auth.logout`, `auth.password_reset`, `auth.password_change`, `auth.session_revoked`
- **Account approval** (5): `account_approval.approve`, `account_approval.reject`, `account_approval.expire_batch`, `activation_code.bind`, `activation_code.generate`
- **CRM** (7): `parent.create`, `parent.update`, `parent.delete`, `student.create`, `student.update`, `student.promote`, `crm.batch_register`
- **Academic** (9): `class.create`, `class.update`, `subject.create`, `subject.update`, `subject.archive`, `subject.assign`, `grade.enter`, `attendance.submit`, `homework.push`, `attendance.alert`
- **Financial** (8): `payment.collect`, `payment.refund`, `payment.adjust`, `receipt.generate`, `installment.create`, `installment.mark_paid`, `installment.reschedule`, `debt.reminder_sent`
- **Ledger** (4): `ledger.entry.append`, `ledger.entry.append_many`, `ledger.entry.reverse`, `ledger.reconcile`
- **Expense** (5): `expense.submit`, `expense.approve`, `expense.reject`, `expense.disburse`, `expense.settle`
- **Personnel** (2): `personnel.create`, `personnel.update`, `releve.create`
- **Settings/System** (12): `settings.update`, `rbac.matrix_update`, `backup.created`, `backup.restored`, `backup.purge`, `workflow.published`, `workflow.triggered`, `workflow.run`, `overdue_scan.run`, `materialized_views.refresh`, `server_secret.update`
- **AI** (9): `ai.narrative_drafted`, `ai.narrative_approved`, `ai.narrative_rejected`, `ai.draft_generated`, `ai.draft_sent`, `ai.anomaly_flagged`, `ai.anomaly_justification_requested`, `ai.config_update`, `ai.config_test`
- **Sync** (2, mobile-only): `sync.conflict`, `sync.push_failed`

### 4.2 `LedgerEngine` (pure functions)

**5 determinism invariants**: (1) complete audit trail, (2) determinism (replay = same balance), (3) no ambiguity, (4) reversibility (corrections are new entries with `reversesId`), (5) reconcilability (sum of entries = sum of balances).

**Signed-amount convention**:
- `CHARGE` → amount > 0 (parent owes more)
- `PAYMENT` → amount < 0 (parent owes less)
- `ADJUSTMENT` → amount ≠ 0 (signed; +debit / −credit)
- `REFUND` → amount < 0 (money returned)
- `REVERSAL` → amount = −original.amount
- `TRANSFER` → signed; net zero on balance

Amount is **Long (centimes)** — never Double, for determinism.

#### Key functions

```kotlin
fun deriveAccountId(parentId: String, category: PaymentCategory, studentId: String? = null): String
//  → "parent:{parentId}:category:{category.code}[:student:{studentId}]"
// Pure, deterministic, no DB lookup. NO `accounts` table exists.

fun generateEntryId(at: Instant = Instant.now()): String
//  → "led-{YYYYMMDD}-{8-char-UUID-hex}"

fun createChargeEntry(tenantId, parentId, studentId?, category, amount: Long,
    sourceType, sourceId, actorId, actorName, description,
    receiptNumber? = null, paymentStatus? = null,
    at: Instant = Instant.now(), metadata = emptyMap()): LedgerEntry
// require(amount > 0)
// require(description.isNotBlank())
// require(actorId.isNotBlank())
// require(actorName.isNotBlank())
// → entry with type=CHARGE, amount=+amount

fun createPaymentEntry(tenantId, parentId, studentId?, category, amount: Long,
    method: PaymentMethod, receiptNumber: String, paymentStatus: PaymentStatus,
    sourceId, actorId, actorName, description,
    at = Instant.now(), metadata = emptyMap()): LedgerEntry
// require(amount > 0)   ← caller passes POSITIVE; entry stores NEGATIVE
// → entry with type=PAYMENT, amount=-amount

fun createAdjustmentEntry(tenantId, parentId, studentId?, category, amount: Long,
    sourceId, actorId, actorName, reason, receiptRef? = null,
    at = Instant.now(), metadata = emptyMap()): LedgerEntry
// require(amount != 0L)
// require(reason.isNotBlank())
// → entry with type=ADJUSTMENT, amount=amount (signed)

fun createRefundEntry(tenantId, parentId, studentId?, category, amount: Long,
    sourceId, actorId, actorName, reason, method: PaymentMethod, receiptNumber?,
    at = Instant.now(), metadata = emptyMap()): LedgerEntry
// require(amount > 0)  ← caller passes POSITIVE; entry stores NEGATIVE
// → entry with type=REFUND, amount=-amount, paymentStatus=REFUNDED

fun createReversalEntry(original: LedgerEntry, reason: String,
    actorId: String, actorName: String,
    at: Instant = Instant.now()): LedgerEntry
// require(reason.isNotBlank())
// require(actorId.isNotBlank())
// → entry with type=REVERSAL, amount=-original.amount, reversesId=original.id,
//   description="REVERSAL of ${original.id}: $reason"
//   metadata={"reversedEntryId": original.id, "reason": reason}
```

#### Balance computation (THE formula)

```kotlin
fun computeAccountBalance(entries: List<LedgerEntry>, accountId: String,
                          now: Instant = Instant.now()): AccountBalance
```

**Algorithm**:
1. Filter `entries.accountId == accountId && entries.at <= nowIso` (as-of query support).
2. Sort by `(at, id)` ascending.
3. If empty → return zero AccountBalance.
4. Compute `reversedIds = set of all entry.reversesId` (entries that are reversed by another entry).
5. Iterate:
   - `balance += entry.amount` (ALWAYS — even for reversed entries, the reversal cancels them out).
   - If `entry.id in reversedIds` → this entry is reversed; skip the typed-totals update (but still update `lastActivityAt`).
   - Otherwise:
     - `CHARGE` → `totalCharged += entry.amount`
     - `PAYMENT` → `totalPaid += |entry.amount|`; if `paymentStatus==PAID` → `totalCleared += |amount|`; if `paymentStatus==PENDING` → `totalPending += |amount|`.
     - `ADJUSTMENT` → `totalAdjusted += entry.amount` (signed)
     - `REFUND` → `totalRefunded += |entry.amount|`
     - `REVERSAL`, `TRANSFER` → no typed-total contribution.
   - `lastActivityAt = max(lastActivityAt, entry.at)`.
6. Return AccountBalance with first entry's `parentId`/`studentId`/`category` (the account is consistent by construction).

**Critical correctness property**: a reversed entry contributes **zero** to both net balance (canceled by reversal) AND zero to typed totals (it's excluded from `totalCharged`/`totalPaid`/etc.). The `entryCount` includes both entries (original + reversal) because both are real ledger records.

#### Parent summary (aggregation)

```kotlin
fun computeParentSummary(entries: List<LedgerEntry>, parentId: String,
                         parentName: String,
                         overdueCategoryDueDates: Map<String, Instant> = emptyMap(),
                         now: Instant = Instant.now()): ParentLedgerSummary
```

- Filters `entries.parentId == parentId`.
- Groups by `accountId`, calls `computeAccountBalance` per account.
- Aggregates: `totalOutstanding = Σ acc.balance`; `totalCharged = Σ acc.totalCharged`; etc.
- **Overdue rule**: for each account, if `overdueCategoryDueDates[accountId]` exists AND `acc.balance > 100L` (centime tolerance) AND `dueDate.isBefore(now)` → `totalOverdue += acc.balance`.
- The `overdueCategoryDueDates` map is built from CHARGE entries via `buildOverdueDueDateMap`.

```kotlin
fun buildOverdueDueDateMap(entries: List<LedgerEntry>): Map<String, Instant>
// = entries.filter { type==CHARGE }.groupBy { accountId }.mapValues { max(at) }
// The "due date" is the date of the latest charge on that account.

fun maxDaysOverdueFromLedger(entries: List<LedgerEntry>, now: Instant = Instant.now()): Long
// = (now - oldestPastCharge.at) in days
```

### 4.3 `PiiMask` (reversible masking)

**6 placeholder prefixes**: `PHONE`, `EMAIL`, `IBAN`, `NN`, `PARENT`, `STUDENT`.

**Masking order is critical** (longest/most-specific first):
1. **IBAN** (Algerian): `DZ\d{2}(?:\s?\d{4}){5}` → `[IBAN_n]`
2. **Phone** (Algerian, multiple formats): `(?:(?:\+|00)?213|0)[\s\-.]?(?:[5-7][\s\-.]?\d{2}[\s\-.]?\d{3}[\s\-.]?\d{2,3}|[5-7]\d{8})` → `[PHONE_n]`
3. **Email**: `[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}` → `[EMAIL_n]`
4. **NN** (10-digit national ID): `(?<!\d)\d{10}(?!\d)` → `[NN_n]`
5. **Parent names** (sorted descending by length to avoid partial matches): `masked.replace(name, "[PARENT_n]")`
6. **Student names** (same): `masked.replace(name, "[STUDENT_n]")`

**Deduplication**: same value → same placeholder (via `seen` map). Counter per prefix increments only for new values.

**API**:
```kotlin
data class Result(val masked: String, val replacements: Map<String, String>)
data class Options(val parentNames: List<String> = emptyList(), val studentNames: List<String> = emptyList())

fun maskPII(text: String, options: Options = Options()): Result
fun unmaskPII(masked: String, replacements: Map<String, String>): String
```

### 4.4 `Rbac` (roles, permissions, feature gate)

**11 roles** (9 staff + 2 web-only):

| Role | code | Category |
|---|---|---|
| SUPER_ADMIN | `super_admin` | Administrative |
| FINANCIAL_OFFICER | `financial_officer` | Operational (financial) |
| TEACHER | `teacher` | Operational |
| SUPPORT_STAFF | `support_staff` | Operational |
| MANAGER | `manager` | Administrative |
| BUYER | `buyer` | Operational |
| DRIVER | `driver` | Operational |
| WAREHOUSE_WORKER | `warehouse_worker` | Operational |
| WORKER | `worker` | Operational |
| PARENT | `parent` | Web-portal only |
| STUDENT | `student` | Web-portal only |

**Role sets**:
- `STAFF_ROLES` = 9 (excludes PARENT, STUDENT)
- `ADMINISTRATIVE_ROLES` = {SUPER_ADMIN, MANAGER}
- `SUPERVISORY_ROLES` = {SUPER_ADMIN, MANAGER}
- `OPERATIONAL_ROLES` = {TEACHER, BUYER, DRIVER, WAREHOUSE_WORKER, WORKER, SUPPORT_STAFF}
- `DASHBOARD_ROLES` = {SUPER_ADMIN, FINANCIAL_OFFICER, SUPPORT_STAFF, MANAGER} ← "iteration 9 RBAC change"

**56 permissions** (snake_case codes): VIEW_ROSTER, CREATE_PARENT, EDIT_PARENT, DELETE_PARENT, CREATE_STUDENT, EDIT_STUDENT, PROMOTE_STUDENT, VIEW_ACADEMICS, ENTER_GRADES, MANAGE_SUBJECTS, MANAGE_CLASSES, ASSIGN_HOMEWORK, ROLL_CALL, VIEW_FINANCIALS, COLLECT_PAYMENT, REFUND_PAYMENT, ADJUST_ACCOUNT, GENERATE_RECEIPT, VIEW_DEBT, SEND_REMINDER, SUBMIT_EXPENSE, APPROVE_EXPENSE, DISBURSE_EXPENSE, SETTLE_EXPENSE_PROOF, VIEW_PERSONNEL, MANAGE_PERSONNEL, VIEW_AUDIT_LOG, VIEW_RELEVE, ACCESS_DRIVER_MODE, MANAGE_SETTINGS, MANAGE_TENANTS, MANAGE_PRICING, MANAGE_WORKFLOWS, VIEW_WORKFLOW_RUNS, EXECUTE_WORKFLOW, MANAGE_BACKUPS, USE_AI, MANAGE_AI_CONFIG, VIEW_DEPARTMENTS, MANAGE_DEPARTMENTS, MANAGE_EMPLOYEE_PROFILES, VIEW_SALARY, MANAGE_SCHEDULES, VIEW_ATTENDANCE, CLOCK_IN_OUT, APPROVE_REQUESTS, SUBMIT_REQUESTS, MANAGE_TASKS, VIEW_TASKS, UPDATE_TASK_STATUS, VIEW_PERFORMANCE, MANAGE_PERFORMANCE, USE_CHAT, MANAGE_CHAT_CHANNELS, MANAGE_PURCHASE_REQUESTS, MANAGE_SUPPLIERS, MANAGE_DELIVERIES, MANAGE_INVENTORY, MANAGE_ONBOARDING, VIEW_WORKFORCE_REPORTS.

**Default role-permission matrix** (computed at runtime from `Permission.DEFAULT_ROLE_PERMISSIONS`):

| Role | Permissions (high level) |
|---|---|
| SUPER_ADMIN | ALL 56 |
| FINANCIAL_OFFICER | view_financials, collect_payment, refund_payment, adjust_account, generate_receipt, view_debt, send_reminder, submit_expense, approve_expense, disburse_expense, settle_expense_proof, view_personnel, view_releve, view_audit_log, view_workflow_runs, execute_workflow, manage_backups, use_ai, view_workforce_reports, use_chat, view_salary (21) |
| TEACHER | view_academics, enter_grades, roll_call, assign_homework, view_tasks, update_task_status, clock_in_out, submit_requests, use_chat, view_releve (10) |
| SUPPORT_STAFF | view_roster, create_parent, create_student, collect_payment, generate_receipt, view_debt, submit_expense, view_tasks, update_task_status, clock_in_out, submit_requests, use_chat (12) |
| MANAGER | view_personnel, view_departments, manage_departments, view_tasks, manage_tasks, manage_schedules, view_attendance, approve_requests, view_performance, view_salary, view_workforce_reports, manage_chat_channels, use_chat, view_audit_log (14) |
| BUYER | manage_purchase_requests, manage_suppliers, use_chat, clock_in_out, submit_requests, submit_expense (6) |
| DRIVER | manage_deliveries, access_driver_mode, use_chat, clock_in_out, submit_requests (5) |
| WAREHOUSE_WORKER | manage_inventory, use_chat, clock_in_out, submit_requests (4) |
| WORKER | view_tasks, update_task_status, use_chat, clock_in_out, submit_requests (5) |
| PARENT | emptySet() |
| STUDENT | emptySet() |

**Session value** (`Rbac.kt`):
```kotlin
data class Session(
  userId, tenantId, email, displayName, avatarUrl?,
  role: Role,
  permissions: Set<Permission>,
  accessToken, refreshToken?, expiresAt: Long, locale: String,
)
fun can(permission: Permission): Boolean
fun hasRole(role: Role): Boolean
fun hasAnyRole(vararg roles: Role): Boolean
fun isExpired(now: Long = System.currentTimeMillis()): Boolean  // 60s safety margin
```

**Feature gate** (pure function, no side effects):
```kotlin
sealed class AccessRequirement {
  object Empty : AccessRequirement()
  data class Permanent(val state: PermanentState) : AccessRequirement()  // REMOVED, NOT_YET_AVAILABLE, DESKTOP_ONLY, PLAN_UPGRADE_REQUIRED
  data class RequiresPermission(val permission: Permission, val hideWhenUnauthenticated: Boolean = false)
  data class RequiresAnyOf(val permissions: List<Permission>, val hideWhenUnauthenticated: Boolean = false)
  data class RequiresAllOf(val permissions: List<Permission>, val hideWhenUnauthenticated: Boolean = false)
  data class RequiresRole(val roles: List<Role>, val hideWhenUnauthenticated: Boolean = false)
}

sealed class AccessState {
  object Enabled
  object Hidden
  data class Disabled(val reason: DisableReason)
}

sealed class DisableReason {
  object NotAuthenticated
  data class MissingPermission(val permission: Permission)
  data class MissingRole(val roles: List<Role>)
  data class FeatureFlagOff(val flag: String)
  data class Permanent(val state: PermanentState)
}

object FeatureGate {
  fun evaluate(requirement: AccessRequirement, session: Session?,
               flags: FeatureFlagProvider = AlwaysOnFlagProvider): AccessState
}
```

Behavior: `Empty → Enabled`. `Permanent → Disabled(Permanent(state))`. Permission variants: null session → Hidden (if `hideWhenUnauthenticated`) else `Disabled(NotAuthenticated)`. `RequiresAnyOf` reports the FIRST permission as missing if none match. `RequiresAllOf` reports the first MISSING permission.

### 4.5 `Reconcile` (ledger reconciler)

Pure function. **28 wire-protocol violation codes** + 3 severities (`ERROR`, `WARNING`, `INFO`).

**Report shape**:
```kotlin
data class Report(
  checkedAt: String, entryCount: Int, accountCount: Int,
  violations: List<Violation>,
) {
  val passed: Boolean   // no ERROR-severity violations
  val errorCount, warningCount, infoCount: Int
}

data class Violation(severity: Severity, code: String, message: String,
                     entryId: String? = null, accountId: String? = null,
                     details: Map<String, Any?> = emptyMap())
```

**The single entry point**:
```kotlin
fun reconcileLedger(entries: List<LedgerEntry>,
                    crossCheckInputs: CrossCheckInputs = CrossCheckInputs()): Report
```

**10 checks run in order**:

1. **`checkDuplicateIds`** → `DUPLICATE_ENTRY_ID` (ERROR) per duplicate row.
2. **`checkRequiredFields`**:
   - missing id → `MISSING_ID` (ERROR)
   - missing tenantId → `MISSING_TENANT_ID` (ERROR)
   - missing accountId → `MISSING_ACCOUNT_ID` (ERROR)
   - missing parentId → `MISSING_PARENT_ID` (ERROR)
   - amount == 0 && type != ADJUSTMENT → `INVALID_AMOUNT` (ERROR)
   - missing at → `MISSING_TIMESTAMP` (ERROR)
   - missing description → `MISSING_DESCRIPTION` (ERROR)
   - missing actorId → `MISSING_ACTOR_ID` (WARNING)
   - missing actorName → `MISSING_ACTOR_NAME` (WARNING)
3. **`checkSignedAmountConvention`**:
   - CHARGE amount ≤ 0 → `CHARGE_NOT_POSITIVE` (ERROR)
   - PAYMENT amount ≥ 0 → `PAYMENT_NOT_NEGATIVE` (ERROR)
   - REFUND amount ≥ 0 → `REFUND_NOT_NEGATIVE` (ERROR)
   - ADJUSTMENT amount == 0 → `ADJUSTMENT_ZERO` (ERROR)
4. **`checkAccountIdsMatch`** → `ACCOUNT_ID_MISMATCH` (ERROR) if `accountId != LedgerEngine.deriveAccountId(parentId, category, studentId)`.
5. **`checkReversalIntegrity`**:
   - reversal references non-existent entry → `ORPHAN_REVERSAL` (ERROR)
   - reversal amount ≠ −original.amount → `REVERSAL_AMOUNT_MISMATCH` (ERROR)
   - reversal accountId ≠ original.accountId → `REVERSAL_ACCOUNT_MISMATCH` (ERROR)
   - same original reversed > 1 time → `DOUBLE_REVERSAL` (ERROR)
6. **`checkDuplicateReceiptNumbers`** → `DUPLICATE_RECEIPT_NUMBER` (ERROR) within a tenant.
7. **`checkTenantConsistency`** → `TENANT_MISMATCH` (ERROR) if entries span > 1 tenant.
8. **`crossCheckPayments`** (if `inputs.payments != null`):
   - payment has no matching ledger entry (sourceType=PAYMENT, sourceId=payment.id) → `PAYMENT_WITHOUT_LEDGER_ENTRY` (WARNING)
   - |entry.amount| ≠ payment.amount → `PAYMENT_AMOUNT_MISMATCH` (ERROR)
   - entry.paymentStatus ≠ payment.status → `PAYMENT_STATUS_MISMATCH` (WARNING)
9. **`crossCheckInstallments`** (if `inputs.installments != null`):
   - installment has no matching CHARGE entry (sourceType=INSTALLMENT, sourceId=installment.id) → `INSTALLMENT_WITHOUT_LEDGER_ENTRY` (WARNING)
   - entry.amount ≠ installment.amountDue → `INSTALLMENT_AMOUNT_MISMATCH` (ERROR)
10. **`crossCheckBalanceSum`**:
    - Compute `sumOfEntries = Σ entries.amount`
    - Compute `sumOfBalances = Σ LedgerEngine.computeAccountBalance(entries, accountId).balance` (per distinct account)
    - drift = |sumOfEntries − sumOfBalances|
    - if drift > 100 (centime tolerance) → `BALANCE_SUM_MISMATCH` (ERROR)

### 4.6 `Result<T>` & `Errors`

```kotlin
sealed class Result<out T> {
  data class Ok<out T>(val value: T) : Result<T>()
  data class Err(val error: AppError) : Result<Nothing>()

  inline fun <R> map(transform: (T) -> R): Result<R>
  inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R>
  inline fun onSuccess(block: (T) -> Unit): Result<T>
  inline fun onFailure(block: (AppError) -> Unit): Result<T>
  fun getOrNull(): T?
  fun errorOrNull(): AppError?
  val isOk: Boolean
  val isErr: Boolean
}

data class AppError(code: String, message: String, userMessage: String, cause: Any? = null)
```

**10 error codes** + factory functions:
- `ERR_NETWORK`, `ERR_TIMEOUT`, `ERR_NOT_FOUND`, `ERR_VALIDATION`, `ERR_UNAUTHORIZED`, `ERR_FORBIDDEN`, `ERR_CONFLICT`, `ERR_SERVER`, `ERR_OFFLINE`, `ERR_UNKNOWN`.

`Errors.fromSupabase(errorCode, message)` maps Postgres/Supabase codes:
- `23505` / "duplicate key" → `conflict`
- `23503` / "foreign key" → `validation`
- `42501` / "permission denied" / "RLS" → `forbidden`
- `PGRST116` → `notFound`
- `401` / "JWT" / "auth" → `unauthorized`
- "network" / "fetch" → `network`
- "timeout" → `timeout`
- else → `server`

`Errors.fromException(e)`:
- `UnknownHostException`, `ConnectException` → `network`
- `SocketTimeoutException` → `timeout`
- else → `unknown`

All `userMessage` strings are in **French** (Algerian user base). Example: `"Erreur réseau. Vérifiez votre connexion."`.

---

## 5. Infrastructure Layer

### 5.1 Room Schema

**Database**: `ElImtiyazDatabase` (v1, `exportSchema=false`). File: `el_imtiyaz.db`. `fallbackToDestructiveMigrationOnDowngrade()` enabled.

**5 entities**:

#### `ParentCacheEntity` (table `parent_cache`)
Columns: `id` (PK), `tenantId`, `code`, `firstName`, `lastName`, `phone`, `whatsapp`, `email`, `occupation`, `address`, `transportDestination`, `preferredLanguage`, `avatarUrl`, `createdAt`, `updatedAt`, `syncedAt: Long`.
Indices: `tenantId`, `code`, `updatedAt`.

#### `StudentCacheEntity` (table `student_cache`)
Columns: `id` (PK), `tenantId`, `code`, `parentId`, `firstName`, `lastName`, `gender`, `birthDate`, `enrollmentDate`, `level`, `gradeLevel`, `classId`, `photoUrl`, `medicalNotes`, `status`, `createdAt`, `updatedAt`, `syncedAt: Long`.
Indices: `tenantId`, `parentId`, `classId`, `code`.

#### `PaymentCacheEntity` (table `payment_cache`)
Columns: `id` (PK), `tenantId`, `receiptNumber`, `parentId`, `studentId`, `amount: Long`, `method`, `status`, `category`, `installmentId`, `proofUrl`, `notes`, `collectedBy`, `collectedAt`, `createdAt`, `updatedAt`, `syncedAt: Long`.
Indices: `tenantId`, `parentId`, `studentId`, `receiptNumber`.

#### `LedgerCacheEntity` (table `ledger_cache`)
Columns: `id` (PK), `tenantId`, `accountId`, `parentId`, `studentId`, `category`, `amount: Long`, `type`, `sourceType`, `sourceId`, `method`, `receiptNumber`, `paymentStatus`, `reversesId`, `description`, `actorId`, `actorName`, `entryDate`, `syncedAt: Long`.
Indices: `tenantId`, `parentId`, `accountId`, `entryDate`.

#### `SyncQueueEntity` (table `sync_queue`)
Columns: `id` (PK, `sync_{timestamp_base36}_{random}`), `queuedAt`, `lastAttemptAt`, `entity`, `operation`, `tenantId`, `actorId`, `payload: String` (JSON), `isMock: Boolean`, `sourceScreen`, `status` (`pending` | `synced` | `failed` | `skipped_mock`), `attempts: Int`, `lastError`.
Indices: `status`, `tenantId`, `queuedAt`, `isMock`.

### 5.2 DAOs (`Daos.kt`)

**ParentCacheDao**: `observeAll()`, `observeById(id)`, `search(q)`, `upsertAll(rows)`, `deleteStale(before)`, `clear()`.

**StudentCacheDao**: `observeAll()` (LIMIT 500), `observeByParent`, `observeByClass`, `observeById`, `upsertAll`, `clear`.

**PaymentCacheDao**: `observeAll()` (LIMIT 200, DESC), `observeByParent`, `observeById`, `upsertAll`, `clear`.

**LedgerCacheDao**: `observeAll()` (LIMIT 1000, ASC), `observeByParent`, `observeByAccount`, `upsertAll`, `clear`.

**SyncQueueDao**: `observeByStatus`, `listPending()`, `listRecent(limit=50)`, `upsert`, `observePendingCount()`, `observeSyncedCount()`, `observeFailedCount()`, `observeSkippedMockCount()`, `pruneOld(before)`, `clear`.

### 5.3 Supabase Repositories

`SupabaseClientProvider` builds the singleton `SupabaseClient` from `BuildConfig.SUPABASE_URL` + `BuildConfig.SUPABASE_ANON_KEY`. Installs: `Auth`, `Postgrest`, `Realtime`, `Storage`, `Functions`. HTTP engine: `Ktor Android`. Falls back to `https://demo.supabase.co` / `"demo-key"` if URL/key missing or build fails.

#### `SupabaseAuthRepository`
- `signIn(email, password)`:
  1. If Supabase URL configured (starts with `https://` and not `"your-project"`):
     - `auth.signInWith(Email) { email; password }`
     - Fetch `user_profiles` row by `auth_user_id`.
     - Skip if `status != "active"`.
     - Fetch roles via `current_user_roles()` RPC.
     - Fetch permissions via `current_user_permissions()` RPC.
     - Build immutable `Session` (1h expiry).
  2. **Fallback**: if remote auth fails OR Supabase not configured, derive role from email substring (`admin`/`financial`/`teacher`/etc.), build a demo `Session` with all permissions and 24h expiry. **Demo mode is the default dev path.**
- `signOut()`: `auth.signOut()` (best-effort) + clear session state.
- `refreshSession()`: if session already set, return it; else build a default staff session (SUPER_ADMIN, all perms, 24h expiry). **This means the app auto-logs-in in dev mode.**
- `changePassword(current, new)`:
  1. Validate strength: `length ≥ 8 ∧ has lower ∧ has upper ∧ has digit`.
  2. Re-authenticate with `current` password.
  3. `auth.updateUser { password = new }` (Supabase auto-revokes other sessions).
  4. `auth.signOut()` + clear session.
  5. Audit log `auth.password_change` with note `"Password changed; all sessions revoked"`.

#### `SupabaseParentRepository`
Table: `parents`. Operations: observe (LIMIT 200, ordered by `last_name`), observeById, search (ILIKE on `first_name`/`last_name`/`phone`/`code`, LIMIT 50), create (server-set `code`), update (partial patch), delete (soft-delete via `UPDATE deleted_at = now()`). Every mutation writes an audit log via `AuditRepository.log`. Search uses `or { ilike(...) }` filter.

#### `SupabaseStudentRepository`
Table: `students`. Same pattern as parents + two RPCs:
- `batch_register_family` — atomic parent + N students + activation code in one transaction. Returns `BatchRegisterResponse(parentId, activationCode?, studentCount)`.
- `promote_students` — bulk promotion with `p_academic_year` + `p_decisions` JSON array. Writes one audit log per decision.

#### `SupabasePaymentRepository`
- Reads: `payments` table.
- `collect(input, actorId, actorName)`:
  - Client-side validation: amount > 0; method.requiresProof → proofPath not blank; CHECK → checkNumber + checkBankName; TRANSFER → transferReference.
  - Invokes Edge Function `collect-payment` with all input fields as JSON params.
  - Response: `CollectPaymentResponse(paymentId, receiptId, newInstallmentStatus?, message?)`.
  - Fetches the new payment row, writes audit log `payment.collect`.
  - The Edge Function server-side inserts the payment row, updates installment `amount_paid`, appends a `ledger_entries` row, generates a receipt, and writes its own audit log — all atomically via the `collect_payment` PostgreSQL SECURITY DEFINER function.
- `refund(paymentId, reason)`:
  - `reason.length >= 3` required.
  - Invokes Edge Function `refund-payment` with `payment_id` + `reason`.
  - Response: `RefundPaymentResponse(reversalPaymentId, message?)`.
  - Fetches the reversal payment, writes audit log `payment.refund`.
- `adjust(input, ...)`:
  - Direct RPC `create_account_adjustment` (no Edge Function).
  - Audit log `payment.adjust`.

#### `SupabaseLedgerRepository`
Table: `ledger_entries` (immutable — RLS blocks UPDATE/DELETE; client can only SELECT + INSERT).
- `observe()` → fetch up to 1000 entries ordered by `entry_date` ASC.
- `observeByParent` / `observeByAccount` → filtered selects.
- `append(entry)` → INSERT + audit log `ledger.entry.append`.
- `appendMany(entries)` → batch INSERT + audit log `ledger.entry.append_many` (entityId="batch").
- `reverse(originalId, reason, actorId, actorName)`:
  1. SELECT the original entry by id.
  2. `LedgerEngine.createReversalEntry(original, reason, actorId, actorName)`.
  3. INSERT the reversal.
  4. Audit log `ledger.entry.reverse`.
- `summary(parentId)`:
  1. SELECT all `ledger_entries` for parent.
  2. SELECT `parents.first_name, parents.last_name` for parent name (best-effort).
  3. `LedgerEngine.computeParentSummary(entries, parentId, parentName)` — **client-side replay**.
- `reconcile()`:
  1. Fetch all entries.
  2. `Reconcile.reconcileLedger(entries)`.
  3. Audit log `ledger.reconcile` with `passed`/`errors`/`warnings` counts.

#### `SupabaseExpenseRepository`
Table: `expense_tickets`. State transitions enforced server-side by `enforce_expense_workflow_rules` trigger (no-self-approval: `submittedBy !== approver`).
- `submit(input, actorId, actorName)` → INSERT (status auto-set to `submitted`) + audit log `expense.submit`.
- `approve(id, note, actorId, actorName)`:
  - `note.isBlank()` rejected.
  - RPC `approve_expense(p_ticket_id, p_approver_profile_id, p_note)`.
  - Audit log `expense.approve`.
- `reject(id, reason, actorId, actorName)`:
  - `reason.isBlank()` rejected.
  - RPC `reject_expense(p_ticket_id, p_approver_profile_id, p_reason)`.
  - Audit log `expense.reject`.
- `disburse(id, actorId, actorName)` → RPC `disburse_expense(p_ticket_id, p_disburser_profile_id)` + audit log `expense.disburse`.
- `settleProof(id, proofPath, finalAmount, actorId, actorName)`:
  - RPC `settle_expense(p_ticket_id, p_proof_path, p_final_amount, p_settler_profile_id)`.
  - Audit log `expense.settle`.

#### `SupabaseAuditRepository`
- Reads: `audit_logs` table (append-only server-side — BEFORE UPDATE/DELETE trigger blocks mutations).
- `log(input)`: RPC `write_audit_log(p_action, p_entity_type, p_entity_id, p_before_json, p_after_json, p_note)`. **`actor_id`, `actor_name`, `tenant_id` are derived server-side from the JWT** — the client never sends them. Returns the inserted row's id; the impl then SELECTs the row back to return the full `AuditLog`.
- `query(filter)`: SELECT with optional eq filters on `action`/`entity_type`/`entity_id`/`actor_id` + `gte`/`lte` on `occurred_at`. LIMIT honored; offset not supported by SDK version used.

#### `SupabaseStorageRepository`
- `uploadProof(bucket, entityId, fileName, bytes, mimeType)`:
  - Requires a session (`sessionProvider.current()?.tenantId`).
  - Path: `{tenantId}/{entityId}/{fileName}` — first segment MUST match caller's tenant (RLS-enforced server-side).
  - `provider.storage.from(bucket).upload(path, bytes) { upsert = false; contentType = mimeType }`.
- `createSignedUrl(bucket, path, expiresInSeconds)` → `createSignedUrl(path, Duration.parse("${expiresInSeconds}s"))`.

### 5.4 Sync Architecture

**`OnlineDetector`** (`@Singleton`, `@ApplicationContext`):
- Wraps `ConnectivityManager.NetworkCallback`.
- `OnlineState(connectivityActive, probeOk, online, changedAt)`.
- `online = connectivityActive && probeOk`.
- ⚠ The HTTP probe to the Supabase URL described in the docstring is **not implemented** — `probeOk` stays `false` → `online` is always `false`. **Known bug.**
- `start()` registers the callback; `stop()` unregisters.

**`SyncService`** (`@Singleton`):
- `enqueue(entity, operation, payload: String, isMock: Boolean, sourceScreen?)`:
  - Builds `SyncQueueEntity` with id `sync_{ts_base36}_{uuid8}`, tenant/actor from `SessionManager`.
  - Status set to `"skipped_mock"` if `isMock=true`, else `"pending"`.
  - Upserts to Room, refreshes snapshot.
- `snapshot: StateFlow<SyncSnapshot>` exposes online + 4 counts (pending/synced/failed/skipped_mock) + lastSyncAt + lastError.
- `refreshSnapshot()` updates counts from DAO.
- `clearQueue()` wipes the table.

**`SyncWorker`** (`@HiltWorker`):
- Constants: `maxAttempts = 5`, `backoffBaseMs = 1000L`. Backoff formula: `backoffMs = 1000L * (1L shl attempts.coerceAtMost(10))` → 1s, 2s, 4s, 8s, 16s.
- `doWork()`:
  1. Bail if `!onlineDetector.isOnline` (always true given the bug above) or `sessionManager.current() == null`.
  2. List pending entries.
  3. For each:
     - If `isMock` → mark `skipped_mock`, continue. **Defense in depth.**
     - Backoff: if `lastAttemptAt` set AND `now < lastAttemptAt + backoffMs` → skip.
     - `push(entry)` (dispatch by entity type).
     - Success → mark `synced`, clear `lastError`, increment attempts.
     - Failure → `attempts++`; if `>= 5` → `failed`; else keep `pending` with updated `lastAttemptAt` + `lastError`.
  4. Refresh snapshot.
- **Dispatch table**: `pushParent`, `pushStudent`, `pushPayment`, `pushInstallment`, `pushExpense`, `pushAttendance`, `pushGrade`, `pushHomework`, `pushLedgerEntry`. ⚠ **All push functions are empty stubs** with comments like "Skipped here for brevity" / "Implementation would use kotlinx.serialization to decode the payload and call provider.postgrest.from(...).upsert(parsed)". **SyncWorker cannot actually push anything.**

### 5.5 FCM Notifications

`ElImtiyazMessagingService` (`@AndroidEntryPoint`):
- `onMessageReceived(message)`:
  - Reads `data["title"]`, `data["body"]`, `data["priority"]` (default `"medium"`), `data["type"]` (default `"system"`).
  - Channel mapping: `urgent` → `CHANNEL_URGENT`, `high` → `CHANNEL_HIGH`, `low` → `CHANNEL_LOW`, else → `CHANNEL_MEDIUM`.
  - Builds `NotificationCompat` with priority matching channel.
  - Posts with random notification id.
- `onNewToken(token)`:
  - `FcmTokenRegistrar.register(token)` calls `register_fcm_token` RPC with `p_user_id`, `p_token`, `p_platform="android"`.

---

## 6. Screen-by-Screen Breakdown

### 6.1 `LoginScreen` (`ui/features/auth/LoginScreen.kt`)
**Purpose**: Email/password sign-in for staff.

**UI elements**:
- Branded gradient circle with "EI" monogram (80dp).
- Title "El-Imtiyaz Staff" + subtitle "Plateforme de gestion scolaire".
- Login card with email field (Person icon), password field (Lock icon + visibility toggle), error banner.
- "Déverrouiller l'espace" button (loading state).
- "Changer le mot de passe" link → routes to ChangePassword.
- Demo accounts grid: 9 chips (admin, financial, teacher, support, manager, buyer, driver, warehouse, worker). Tapping a chip fills `demo-{role}@elimtiyaz.dz` / `demo1234`.

**ViewModel**: `LoginViewModel` — `signIn(email, password)` validates email pattern, calls `authRepository.signIn`, sets session on success, exposes `signedIn` flag.

**Actions triggered**: `AuthRepository.signIn` → `SessionManager.setSession`.

### 6.2 `ChangePasswordModal` (`ui/features/auth/ChangePasswordModal.kt`)
**Purpose**: Change password flow with strength meter.

**UI elements**: `ElDialog` with 3 password fields (current, new, confirm) + 4 strength rows (8+ chars, lowercase, uppercase, digit). Red warning text: "⚠ Toutes les sessions seront révoquées sur tous vos appareils." Confirm/Cancel buttons.

**ViewModel**: `ChangePasswordViewModel` — validates matching passwords + strength, calls `authRepository.changePassword(current, new)`. On success → dismiss.

### 6.3 `MainScreen` (`ui/features/main/MainScreen.kt`)
**Purpose**: Bottom-nav host with 5 hubs, role-filtered.

**UI elements**:
- `Scaffold` with `TopAppBar` showing current hub label + settings icon.
- `ModernBottomNavBar` with up to 5 items.
- `HubTab` list:
  | Label | Icon | Permission | Role set |
  |---|---|---|---|
  | Tableau | Dashboard | (none) | DASHBOARD_ROLES (super_admin, financial_officer, support_staff, manager) |
  | CRM | Group | VIEW_ROSTER | (none) |
  | Pédagogie | MenuBook | VIEW_ACADEMICS | (none) |
  | Finances | Payments | VIEW_FINANCIALS | (none) |
  | Personnel | Person | VIEW_PERSONNEL | (none) |

- Tabs filtered by `session.can(perm) && session.role in roles`. If a teacher logs in, only Pédagogie + Personnel (if they have VIEW_PERSONNEL) are visible.

**Actions**: routes navigation callbacks to `AppNavHost`.

### 6.4 `DashboardHubScreen` (`ui/features/dashboard/DashboardHubScreen.kt`)
**Purpose**: KPI carousel + AI assistant + live payment feed + alerts.

**UI elements**:
- `ElGradientStatCard` hero: "Tableau de Bord Opérationnel — Bienvenue, {displayName}" + role + unread alerts count.
- KPI Carousel (`LazyRow` of 4 `ElStatCard`s):
  - Revenu Mensuel (monthlyRevenue DZD) — green, "Objectif: 15,000,000 DZD"
  - Créances Restantes (outstandingDebt DZD) — red, "Taux de recouvrement: 85.2%"
  - Élèves Inscrits (totalStudents) — blue, "Présence aujourd'hui: 96.5%"
  - Demandes Dépenses (pendingExpenses) — gold, "Tier 2 Validation Requise"
- AI Quick Actions card (gold accent) with shortcuts: "Résumé Encaisses", "Relance Boumerdès".
- "Flux des Encaissements" section: 4 sample `LivePaymentFeedItem` cards (student name, parent, amount, method, receipt book code, timestamp). Tapping opens a `ModalBottomSheet` receipt detail with "Partager / Imprimer le Reçu PDF" button.
- "Avis & Notifications" section: 3 sample `AppNotification`s with severity-colored `ElAlertBanner`s (urgent=red, high=orange, medium=blue).
- FAB: AutoAwesome icon → opens AI Assistant `ModalBottomSheet`.

**AI Assistant Drawer** (`AiAssistantDrawerContent`):
- Chat history (`mutableStateListOf<String>`).
- Suggestion chips: "Résumé Encaissements", "Transport Boumerdès", "Convocation Absence".
- Input field + send button (Send icon, 48dp, primary background).
- Response logic: keyword-based (`"résumer"` / `"encaissement"` → canned summary; `"transport"` → canned transport list; else generic).
- ⚠ **No actual LLM integration** — all responses are hard-coded strings.

**ViewModel**: `DashboardViewModel` — exposes hardcoded `DashboardKpi` defaults + 3 sample alerts via `NotificationRepository` (which is a stub returning empty list, so alerts are sample data not real).

### 6.5 `CrmHubScreen` (`ui/features/crm/CrmHubScreen.kt`)
**Purpose**: 3-tab CRM hub.

Tabs: **Parents** (ParentsDirectoryScreen), **Élèves** (StudentRosterScreen), **Inscription** (BatchRegistrationScreen).

### 6.6 `ParentsDirectoryScreen` (`ui/features/crm/ParentsDirectoryScreen.kt`)
**Purpose**: Search + list parents with tap-to-call.

**UI elements**: Search field (Person icon), `LazyColumn` of `ParentCard`s (avatar, name, code, phone, green call button).

**ViewModel**: `ParentsDirectoryViewModel` — `_query: StateFlow<String>`, `parents: StateFlow<List<Parent>>` via `flatMapLatest { parentRepository.search(q) }`. `deleteParent(parent, actorId, actorName)` (not currently exposed in UI).

**Actions**: tap card → navigate to ParentDetail; tap call button → `ACTION_DIAL` intent with `tel:{phone}`.

### 6.7 `ParentDetailScreen` (`ui/features/crm/ParentDetailScreen.kt`)
**Purpose**: Parent detail with children + financial summary + Call/WhatsApp.

**UI elements**:
- `ElTopBar` with parent's full name + back button.
- Header card: avatar (56dp), full name, code, two action buttons:
  - "Appeler" (green gradient) → `ACTION_DIAL` `tel:{phone}`.
  - "WhatsApp" (green gradient) → `ACTION_VIEW` `https://wa.me/{formatted}` (Algerian phone normalization: strip non-digits, replace leading `0` with `213`).
- Contact card: code, phone, email, address, occupation (via `ElInfoRow`s).
- Finances card: Total facturé, Total payé (green), Solde, En retard (red if > 0) — formatted `(cents/100).formatDzd() DZD`.
- Children card: list of `Student` with avatar + gradeLevel.

**ViewModel**: `ParentDetailViewModel` — loads parent + children + `LedgerRepository.summary(parentId)` (which replays ledger client-side).

### 6.8 `StudentRosterScreen` (`ui/features/crm/StudentRosterScreen.kt`)
**Purpose**: Search + list students.

**UI elements**: Search field, `LazyColumn` of `ElCard`s with avatar, name, code, gradeLevel • level, status tag (red if not active). Empty state with "Aucun élève trouvé".

**ViewModel**: `StudentRosterViewModel` — same pattern as ParentsDirectory.

### 6.9 `StudentDetailScreen` (`ui/features/crm/StudentDetailScreen.kt`)
**Purpose**: Student identity + siblings + family financial summary + per-account balances.

**UI elements**:
- Identity card (blue accent): code, birthDate, level/gradeLevel, status (green if active, red otherwise), medical notes.
- Family finances card (green accent): Total dû, Total payé, Solde restant, En retard. Per-account list: `acc.category.name` + `acc.balance` DZD.
- Siblings card: list of brother/sister students.

**ViewModel**: `StudentDetailViewModel` — loads student via `studentRepository.observeById`, siblings via `observeByParent(student.parentId)` filtered to exclude self, family summary via `ledgerRepository.summary(parentId)`.

### 6.10 `BatchRegistrationScreen` (`ui/features/crm/BatchRegistrationScreen.kt`)
**Purpose**: Atomic parent + N children registration.

**UI elements**:
- `ElTopBar` "Inscription famille" + `ElFab` (Add icon) to add child.
- Parent card (blue accent): firstName, lastName, phone, email (optional), occupation (optional), address (optional).
- Per-child card: "Enfant N" header with delete button (red), firstName, lastName, birthDate, gradeLevel.
- Error text, success card with activation code (green accent), "Inscrire la famille" button.

**ViewModel**: `BatchRegistrationViewModel`:
- Validates parent firstName/lastName/phone + ≥1 student.
- Calls `studentRepository.batchRegister(parent, students, actorId, actorName)` — invokes `batch_register_family` RPC.
- On success: exposes `activationCode` (displayed to user so they can give it to the parent for portal access).

**Action triggered**: `AuditActions.BATCH_REGISTER` audit log written server-side by the RPC.

### 6.11 `AcademicsHubScreen` (`ui/features/academics/AcademicsHubScreen.kt`)
**Purpose**: 4-tab academic hub.

Tabs: **Présences** (RollCall), **Notes** (GradeEntry), **Devoirs** (HomeworkPush), **Classes** (ClassesDirectory).

#### RollCallScreen
- "Appel — 30 Secondes" hero card.
- Class dropdown (`SAMPLE_CLASSES`: PRIM-CP A, PRIM-CE1 B, COLG-1AAM A, COLG-4AM C, LYC-3AS S).
- 6 sample students (`SAMPLE_STUDENTS` — Amine Benali, Sarra Khelifi, etc.) with `termAbsences` count.
- Per student: avatar, name, absences count, 4 status tags (Présent/green, Absent/red, Excusé/gold, Retard/blue). If `termAbsences + (absent?1:0) >= 3` → red "Alerte 3+" tag + red accent card.
- If status=LATE → show "Heure d'arrivée" text field (default "08:15").
- "Valider l'appel ({class})" button → on click: count threshold students, show success banner: "Appel enregistré! N élève(s) au seuil d'alerte notifiés au portail parents."
- ⚠ **Sample data only** — no actual class/student loading, no `AttendanceRepository.recordRollCall` call.

#### GradeEntryScreen
- "Saisie des Notes" hero showing `%.2f / 20` of computed average.
- Fields: Matière, Classe, Trimestre.
- Evaluation card for "Amine Benali": Devoir 1 (/20), Devoir 2 (/20), Examen (/20 - Coeff 2). Each field validated to be `0.0..20.0`.
- Computed average card (blue accent): `subjectAverage = (d1 + d2 + (ex × 2)) / 4.0` — formula text shown.
- "Enregistrer le bulletin" button → success banner.
- ⚠ **Sample data** — no `GradeRepository.enterGrade` call.

#### HomeworkPushScreen
- "Diffusion des Devoirs" hero.
- Fields: Classe Cible, Matière, Titre du Devoir, Consignes, Date de Rendu (YYYY-MM-DD).
- "Photo du Tableau" card with "Capturer" button (toggles photoAttached boolean).
- "Diffuser le Devoir" button → success banner.
- ⚠ **No `HomeworkRepository.push` call, no CameraX integration.**

#### ClassesDirectoryScreen
- Static list of 5 sample classes (name, teacher, capacity) using `ElListItem`.

### 6.12 `FinancialsHubScreen` (`ui/features/financials/FinancialsHubScreen.kt`)
**Purpose**: 5-tab financial hub.

Tabs: **Encaissement** (CounterPayment), **Preuves** (ProofScanner), **Tranches** (InstallmentSchedule), **Créances** (DebtDashboard), **Dépenses** (ExpenseApproval).

### 6.13 `CounterPaymentScreen` (`ui/features/financials/CounterPaymentScreen.kt`)
**Purpose**: Manual payment collection at the counter.

**UI elements**:
- `ElTopBar` "Encaissement" + back.
- Informations card: ID Parent (text), ID Eleve (text, optional), Montant (DZD, digits only), Notes.
- Mode de paiement card: 3 tags (Especes, Cheque, Virement). If CHECK → show check number + bank fields. If TRANSFER → show reference field.
- Catégorie card: 7 tags (Tuition, Transport, Canteen, Uniform, Books, Extracurricular, Other).
- Error banner, success card with receipt number (green).
- "Encaisser & generer recu" button (Payments icon).

**ViewModel**: `CounterPaymentViewModel.collect(input, onResult)`:
- Builds `CollectPaymentInput` (multiplies amount by 100 for centimes).
- Calls `paymentRepository.collect(input, actorId, actorName)`.
- On success → exposes `receiptNumber`, calls `onResult(receiptNumber)`.
- On error → exposes `error.userMessage`, calls `onResult(null)`.

**Server-side**: `collect-payment` Edge Function → `collect_payment` PG function → inserts payment + updates installment + appends ledger entry + generates receipt + audit log (atomic).

### 6.14 `ProofScannerScreen` (`ui/features/financials/ProofScannerScreen.kt`)
**Purpose**: Capture check/transfer proof and upload.

**UI elements**:
- `ElTopBar` "Scanner une preuve".
- 300dp placeholder card with camera icon + "Aperçu caméra" + "(CameraX integration required)".
- Error text, success card with storage path.
- "Capturer & téléverser" button (disabled while loading).

**ViewModel**: `ProofScannerViewModel.uploadProof(bitmap, entityId, bucket = PAYMENT_PROOFS)`:
- Rejects if `bitmap.width < 640 || bitmap.height < 480`.
- Scales down to max 1920x1080 preserving aspect ratio.
- Compresses to WebP quality 85.
- Generates filename `proof-{UUID}.webp`.
- Calls `storageRepository.uploadProof(bucket, entityId, fileName, bytes, "image/webp")`.
- ⚠ **No CameraX integration** — the button onClick is empty `{}`. The VM is wired but never invoked from UI.

### 6.15 `InstallmentScheduleScreen` (`ui/features/financials/InstallmentScheduleScreen.kt`)
**Purpose**: View installment progress.

**UI elements**:
- `ElScaffold` + `ElTopBar` "Tranches".
- Empty state: "Selectionnez un parent pour voir ses tranches."
- (If non-empty) Progression card (blue accent): `ElProgressBar` + "{paid} / {due} DZD".
- Per installment card: label, status tag (Payé/green, En retard/red, En attente/blue), Échéance, Montant, Payé, Restant.

**ViewModel**: `InstallmentScheduleViewModel` — `installments: StateFlow<List<Installment>>` via `flowOf(emptyList())` (stub). The repository itself is `StubInstallmentRepository`, so this screen always shows the empty state.

### 6.16 `DebtDashboardScreen` (`ui/features/financials/DebtDashboardScreen.kt`)
**Purpose**: Aging-bucket debt overview.

**UI elements**:
- `ElTopBar` "Créances".
- Total card (red accent): "Total en circulation" + headline amount + "En retard: N DZD" if > 0.
- `LazyColumn` of `DebtorCard`s: parent name + bucket tag (color varies: 0_30=primary, 31_60=tertiary, 61_90=secondary, else=red), phone, student count, amount, "En retard de N jours" if daysOverdue > 0.

**ViewModel**: `DebtDashboardViewModel` — `debtors: StateFlow<List<DebtSummary>>` via `debtRepository.observeSummary()` (stub → empty list).

### 6.17 `ExpenseApprovalScreen` (`ui/features/financials/ExpenseApprovalScreen.kt`)
**Purpose**: Approve / reject / disburse expenses.

**UI elements**:
- `Scaffold` + `TopAppBar` "Dépenses".
- `LazyColumn` of `ExpenseCard`s: title + status (color-coded), requestCode • category, description (2 lines max), amount (DZD), payee. If `anomalyScore > 0.5` → red warning. Action buttons:
  - `submitted` → "Approuver" + "Rejeter"
  - `approved` → "Débourser"
- ⚠ Buttons call VM methods with hardcoded notes: `approve(expense, "Approuvé")`, `reject(expense, "Rejeté")` — no input dialog for note/reason (server requires non-blank note).

**ViewModel**: `ExpenseApprovalViewModel` — `expenses: StateFlow<List<Expense>>` via `expenseRepository.observe()`. `approve/reject/disburse` call the corresponding repository methods.

### 6.18 `PersonnelHubScreen` (`ui/features/personnel/PersonnelHubScreen.kt`)
**Purpose**: 4-tab personnel hub.

Tabs: **Employés** (EmployeeDirectory), **Activité** (Releve), **Audit** (AuditStream), **Déconnexion** (SignOut).

#### EmployeeDirectoryScreen
- "Registre du Personnel" header.
- `ElScrollableTabRow` with 5 category filters: Tous, Administratif, Enseignants, Soin & Médical, Support & Logistique.
- `LazyColumn` of 5 `SAMPLE_STAFF` (Dr. Karim Bencherif, Samia Amrani, Redouane Saidi, Amina Ziani, Mourad Khelil). Card: avatar, name, role, category tag, assignment, "Appeler" + "Email" buttons (no-ops).
- ⚠ **Sample data only** — no `PersonnelRepository` call (and no impl exists).

#### ReleveScreen
- "Relevé d'Activité Enseignants" hero.
- 3 sample teacher compliance cards (98%, 92%, 100%) with hours logged / target and `ElProgressBar`.

#### AuditStreamScreen
- "Journal d'Audit" header with "Journal complet" action → navigates to AuditLogScreen.
- `LazyColumn` of 3 sample `AuditLog`s (`payment.recorded`, `grade.modified`, `expense.approved`).
- Tapping opens `ModalBottomSheet` JSON inspector showing pretty-printed audit payload (monospace font).

#### SignOutScreen
- "Session Utilisateur" hero with displayName.
- Info card: email, role.code, permissions count.
- "Se déconnecter" button (Danger style) → calls `MainViewModel.signOut` which calls `authRepository.signOut()` + clears session + navigates to Login.

### 6.19 `SettingsScreen` (`ui/features/settings/SettingsScreen.kt`)
**Purpose**: 4-tab settings.

Tabs: **Général**, **Sync**, **Config**, **Sécurité** — **all 4 are placeholder cards** with one-liner descriptions ("Thème, langue, devise, fuseau horaire", "Statut réseau, file d'attente, sync manuelle", "URL Supabase, anon key, mode mock", "Changer le mot de passe, révoquer les sessions"). No actual settings controls.

### 6.20 `AuditLogScreen` (`ui/features/settings/AuditLogScreen.kt`)
**Purpose**: Full audit log list.

**UI elements**: `ElScaffold` + `ElTopBar` "Journal d'audit" + `LazyColumn` of `AuditLogCard`s: action (blue, semi-bold), timestamp (trimmed to 19 chars), "actorName • entityType/entityId[:8]", note.

**ViewModel**: `AuditLogViewModel` — `logs: StateFlow<List<AuditLog>>` via `auditRepository.observe(limit=200)`.

---

## 7. Navigation Graph

### 7.1 Routes (`ui/navigation/Routes.kt`)

Type-safe `@Serializable` route objects (Navigation 2.8+):

```kotlin
object Splash : Route              // declared but unused (AppNavHost skips straight to Login or Main)
object Login : Route
object ChangePassword : Route
object Main : Route                // bottom-nav host

// Hub placeholders (declared but MainScreen switches hubs via state, not navigation)
object DashboardHub : Route
object CrmHub : Route
object AcademicsHub : Route
object FinancialsHub : Route
object PersonnelHub : Route

// CRM detail
data class StudentDetail(val studentId: String) : Route
data class ParentDetail(val parentId: String) : Route
object BatchRegistration : Route

// Financials detail
data class PaymentDetail(val paymentId: String) : Route     // declared, NOT registered in NavHost
data class ExpenseDetail(val expenseId: String) : Route     // registered in NavHost
object CounterPayment : Route
object ProofScanner : Route
object DebtDashboard : Route
object InstallmentSchedule : Route

// Settings
object Settings : Route
object AuditLog : Route
```

### 7.2 NavHost destinations (`AppNavHost.kt`)

13 `composable<Routes.X>` blocks:

| Route | Screen | Notes |
|---|---|---|
| `Login` | `LoginScreen` | `LocalSession provides currentSession` |
| `ChangePassword` | `ChangePasswordModal` | Pops back on dismiss |
| `Main` | `MainScreen` | All navigation callbacks wired |
| `StudentDetail` | `StudentDetailScreen` | Reads `route.studentId` |
| `ParentDetail` | `ParentDetailScreen` | Reads `route.parentId` |
| `BatchRegistration` | `BatchRegistrationScreen` | Pops back on success |
| `CounterPayment` | `CounterPaymentScreen` | Pops back |
| `ProofScanner` | `ProofScannerScreen` | Pops back |
| `DebtDashboard` | `DebtDashboardScreen` | Pops back |
| `InstallmentSchedule` | `InstallmentScheduleScreen` | Pops back |
| `ExpenseDetail` | `ExpenseApprovalScreen(expenseId)` | Reads `route.expenseId`; ExpenseApprovalScreen itself ignores the `expenseId` arg |
| `Settings` | `SettingsScreen` | Pops back |
| `AuditLog` | `AuditLogScreen` | Pops back |

### 7.3 Role-Based Access

**Auth gate** (in `AppNavHost`):
- `sessionState == null` → start at `Routes.Login`.
- `sessionState != null` → start at `Routes.Main`.

**Bottom-nav tab filtering** (in `MainScreen`):
```kotlin
val visibleTabs = HUB_TABS.filter { tab ->
  val permOk = tab.requiresPermission?.let { session.can(it) } ?: true
  val roleOk = tab.requiresRole?.let { session.role in it } ?: true
  permOk && roleOk
}
```

| Tab | Required permission | Required role |
|---|---|---|
| Tableau (Dashboard) | (none) | SUPER_ADMIN, FINANCIAL_OFFICER, SUPPORT_STAFF, MANAGER |
| CRM | VIEW_ROSTER | (any) |
| Pédagogie | VIEW_ACADEMICS | (any) |
| Finances | VIEW_FINANCIALS | (any) |
| Personnel | VIEW_PERSONNEL | (any) |

**Per-screen RBAC** is **NOT enforced** by the NavHost — any logged-in user can navigate to any registered route by URL. The intended pattern (`FeatureGate.evaluate(...)`) is declared in `Rbac.kt` but not yet wired into `AppNavHost` or individual screens.

---

## 8. Test Coverage

4 unit test files in `app/src/test/java/com/example/core/`. Run via `./gradlew :app:testDebugUnitTest`.

### 8.1 `FeatureGateTest.kt`
Validates the pure `FeatureGate.evaluate` function for all 6 `AccessRequirement` variants:

- `Empty` → Enabled for everyone (null session and admin).
- `Permanent(DESKTOP_ONLY)` → Disabled(Permanent(DESKTOP_ONLY)).
- `RequiresPermission`:
  - Teacher + ENTER_GRADES → Enabled (teacher has it).
  - Teacher + COLLECT_PAYMENT → Disabled(MissingPermission(COLLECT_PAYMENT)) (teacher lacks it).
  - null session + COLLECT_PAYMENT → Disabled(NotAuthenticated).
  - null session + COLLECT_PAYMENT + `hideWhenUnauthenticated=true` → Hidden.
- `RequiresAnyOf`:
  - Teacher + [COLLECT_PAYMENT, ENTER_GRADES] → Enabled.
  - Teacher + [COLLECT_PAYMENT, REFUND_PAYMENT] → Disabled(MissingPermission(COLLECT_PAYMENT)) (reports FIRST in list).
- `RequiresAllOf`:
  - Teacher + [ENTER_GRADES, ROLL_CALL] → Enabled.
  - Teacher + [ENTER_GRADES, COLLECT_PAYMENT] → Disabled(MissingPermission(COLLECT_PAYMENT)).
- `RequiresRole`:
  - admin + [SUPER_ADMIN, MANAGER] → Enabled.
  - teacher + [SUPER_ADMIN, MANAGER] → Disabled(MissingRole([SUPER_ADMIN, MANAGER])).
- **Session helpers**: `can()`, `hasRole()`, `hasAnyRole()`, `isExpired()` (with 60-second safety margin: expires in 30s → already expired).
- **Wire-protocol parity**: hard-coded `assertEquals("super_admin", Role.SUPER_ADMIN.code)` for all 11 roles + 9 sampled permissions.
- `Role.fromCode` / `Permission.fromCode` roundtrips.
- **Role groupings**: STAFF_ROLES size = 9 (no PARENT/STUDENT); ADMINISTRATIVE_ROLES = {SUPER_ADMIN, MANAGER}; DASHBOARD_ROLES = {SUPER_ADMIN, FINANCIAL_OFFICER, SUPPORT_STAFF, MANAGER} (matches "iteration 9 RBAC change").
- **Default matrix**: SUPER_ADMIN has all 56; Teacher has ENTER_GRADES + ROLL_CALL + ASSIGN_HOMEWORK but lacks COLLECT_PAYMENT/REFUND_PAYMENT; PARENT/STUDENT have empty sets.

### 8.2 `LedgerEngineTest.kt`
Most critical — validates all formulas. Test cases:

- **`deriveAccountId`**:
  - Deterministic: `deriveAccountId("par-001", TUITION, null) == "parent:par-001:category:tuition"`.
  - Changes with category.
  - Changes with student: `deriveAccountId("par-001", TUITION, "stu-001") == "parent:par-001:category:tuition:student:stu-001"`.

- **Factory invariants**:
  - `createChargeEntry` throws on `amount ≤ 0` (0L and -1000L both tested).
  - `createChargeEntry` throws on blank description.
  - `createPaymentEntry(amount = 5000_00L)` stores `-5000_00L` (NEGATIVE — credit).
  - `createPaymentEntry` throws on `amount = 0L`.
  - `createAdjustmentEntry` throws on `amount = 0L`; accepts both `+500_00L` (debit/penalty) and `-500_00L` (credit/discount).
  - `createReversalEntry` negates original amount, sets `reversesId`, copies `accountId`, prefixes description with `"REVERSAL of ${original.id}:"`, sets `metadata["reversedEntryId"]`.
  - `createReversalEntry` throws on blank reason.

- **Balance computation**:
  - Empty ledger → zero balance, 0 entries, null lastActivityAt.
  - Single charge of 50000 → balance=50000, totalCharged=50000, totalPaid=0, entryCount=1.
  - **Charge 50000 + Payment 30000** → balance=**20000** (`50000 - 30000`), totalCharged=50000, totalPaid=30000, totalCleared=30000.
  - Charge 50000 + Payment 50000 → balance=0 (fully paid).
  - Charge 50000 + Payment 60000 → balance=-10000 (overpayment → school owes parent).
  - **Pending vs Cleared split**: Charge 50000 + PaidPayment 20000 + PendingPayment 30000 → totalPaid=50000, totalCleared=20000, totalPending=30000, balance=0.
  - **Reversed entries contribute zero net balance AND zero typed totals**: original 50000 + reversal -50000 → balance=0, totalCharged=**0** (original excluded), entryCount=**2** (both counted).
  - **Reversal of payment restores balance**: charge 50000 + payment -30000 + reversal +30000 → balance=**50000** (as if payment never happened), totalPaid=**0** (payment excluded).
  - **As-of query**: yesterday charge 50000 + tomorrow charge 30000, `now=today` → balance=50000, entryCount=1 (future excluded).
  - **Determinism**: replay same entries twice → identical balances. Charge 50000 + Payment 20000 + Adjustment -5000 → balance=25000.

### 8.3 `PiiMaskTest.kt`
Validates all 6 patterns + deduplication + reversibility:

- **Phone (Algerian formats)**: `+213 555 123 456`, `0555 123 456`, `213-555-123-456`, `0555123456` all → `[PHONE_1]`.
- **Email**: `parent@example.com` → `[EMAIL_1]`.
- **IBAN**: `DZ35 0000 1111 2222 3333 4444` (with/without spaces) → `[IBAN_1]`.
- **NN (10 digits)**: `1234567890` → `[NN_1]`. Does NOT mask 9 or 11 digit numbers.
- **Masking order**: text with IBAN + NN → IBAN gets `[IBAN_1]` and the actual NN gets `[NN_1]` (NN regex doesn't grab IBAN digits because IBAN was masked first).
- **Parent/student names**: with `Options(parentNames=["BENALI Kamel"])` → `[PARENT_1]`. Multiple names get sequential placeholders.
- **Deduplication**: same phone twice → both `[PHONE_1]`, no `[PHONE_2]`. Different phones → `[PHONE_1]` + `[PHONE_2]`.
- **Reversibility**: `unmaskPII(masked, replacements) == original`. LLM-style response with placeholders also unmasks correctly.
- **All 6 patterns combined**: single text with all 6 PII types → all 6 placeholders present, no original values leak, reversible.
- **No-PII text passes through unchanged** with empty replacements map.

### 8.4 `ReconcileTest.kt`
Validates 10 checks produce correct wire-protocol violation codes:

- Empty ledger passes (0 violations).
- Single valid charge passes.
- **Duplicate IDs** → `DUPLICATE_ENTRY_ID` (ERROR).
- **Charge amount ≤ 0** → `CHARGE_NOT_POSITIVE`.
- **Payment amount ≥ 0** → `PAYMENT_NOT_NEGATIVE`.
- **Wrong accountId** → `ACCOUNT_ID_MISMATCH`.
- **Orphan reversal** (reversal references non-existent id) → `ORPHAN_REVERSAL`.
- **Double reversal** (same original reversed twice) → `DOUBLE_REVERSAL`.
- **Reversal amount mismatch** (reversal.amount ≠ -original.amount) → `REVERSAL_AMOUNT_MISMATCH`.
- **Duplicate receipt number** → `DUPLICATE_RECEIPT_NUMBER`.
- **Multi-tenant ledger** → `TENANT_MISMATCH`.
- **Missing actorId** → `MISSING_ACTOR_ID` (WARNING).
- **Balance sum invariant holds**: valid ledger with 2 charges + 1 payment → no `BALANCE_SUM_MISMATCH`.
- **Cross-check payment without ledger entry** → `PAYMENT_WITHOUT_LEDGER_ENTRY` (WARNING).
- **Cross-check payment amount mismatch** → `PAYMENT_AMOUNT_MISMATCH` (ERROR).

---

## 9. Dependencies

From `gradle/libs.versions.toml` (versions) + `app/build.gradle.kts` (resolved set):

### 9.1 Build tooling
- AGP `8.8.0`, Kotlin `2.0.21`, KSP `2.0.21-1.0.28`, Compose Compiler plugin (Kotlin 2.0.21).
- Secrets Gradle Plugin `2.0.1` (reads `.env` / `.env.example`).
- Google Services plugin `4.5.0` (with `MissingGoogleServicesStrategy.WARN`).
- Hilt plugin `2.52`.
- Kotlinx Serialization plugin `2.0.21`.
- Roborazzi plugin `1.59.0`.

### 9.2 AndroidX / Compose
- `androidx.core:core-ktx:1.15.0`
- `androidx.activity:activity-compose:1.10.1`
- `androidx.lifecycle:lifecycle-runtime-ktx:2.8.7`, `lifecycle-viewmodel-compose:2.8.7`, `lifecycle-runtime-compose:2.8.7`
- Compose BOM `2024.09.00` → material3, material-icons-core, material-icons-extended, ui, ui-graphics, ui-tooling, ui-tooling-preview, ui-test-junit4
- `androidx.navigation:navigation-compose:2.8.9`

### 9.3 Hilt
- `com.google.dagger:hilt-android:2.52` + `hilt-android-compiler` (KSP)
- `androidx.hilt:hilt-navigation-compose:1.2.0`, `hilt-work:1.2.0`, `hilt-compiler:1.2.0` (KSP)

### 9.4 Supabase
- `io.github.jan-tennert.supabase:supabase-kt:3.1.1` + `auth-kt`, `postgrest-kt`, `realtime-kt`, `storage-kt`, `functions-kt`
- `io.ktor:ktor-client-android:3.0.3` + `ktor-client-core`

### 9.5 Serialization / Coroutines
- `org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3`
- `org.jetbrains.kotlinx:kotlinx-datetime:0.6.1`
- `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2`, `kotlinx-coroutines-core:1.10.2`, `kotlinx-coroutines-test:1.10.2`

### 9.6 Room
- `androidx.room:room-runtime:2.7.0`, `room-ktx:2.7.0`, `room-compiler:2.7.0` (KSP)

### 9.7 WorkManager
- `androidx.work:work-runtime-ktx:2.10.0`

### 9.8 CameraX
- `androidx.camera:camera-camera2:1.5.0`, `camera-core:1.5.0`, `camera-lifecycle:1.5.0`, `camera-view:1.5.0`

### 9.9 Storage / Security
- `androidx.datastore:datastore-preferences:1.1.7`
- `androidx.security:security-crypto:1.1.0-alpha06` (EncryptedSharedPreferences)
- `androidx.multidex:multidex:2.0.1`

### 9.10 Permissions / Image loading / Location
- `com.google.accompanist:accompanist-permissions:0.37.3`
- `io.coil-kt:coil-compose:2.7.0`
- `com.google.android.gms:play-services-location:21.3.0`

### 9.11 Firebase
- `com.google.firebase:firebase-bom:34.15.0` → `firebase-messaging:24.1.0`, `firebase-appcheck-recaptcha`
- Also declared but unused: `firebase-ai`, `firebase-firestore`, `firebase-auth`

### 9.12 Legacy networking (kept but unused — Supabase SDK uses Ktor)
- `com.squareup.okhttp3:okhttp:4.10.0`, `logging-interceptor:4.10.0`
- `com.squareup.retrofit2:retrofit:2.12.0`, `converter-moshi:2.12.0`
- `com.squareup.moshi:moshi-kotlin:1.15.2`, `moshi-kotlin-codegen:1.15.2` (KSP)

### 9.13 Testing
- `junit:junit:4.13.2`
- `androidx.test.ext:junit:1.3.0`, `androidx.test:core:1.6.1`, `androidx.test:runner:1.6.2`
- `androidx.test.espresso:espresso-core:3.7.0`
- `org.robolectric:robolectric:4.16.1`
- `io.github.takahirom.roborazzi:roborazzi:1.59.0`, `roborazzi-compose:1.59.0`, `roborazzi-junit-rule:1.59.0`
- `androidx.compose.ui:ui-test-junit4`

### 9.14 Credentials (declared but unused)
- `androidx.credentials:credentials:1.5.0`, `credentials-play-services-auth:1.5.0`
- `com.google.android.libraries.identity:googleid:1.1.1`

### 9.15 AndroidManifest permissions
- `INTERNET`, `ACCESS_NETWORK_STATE`, `CAMERA`, `POST_NOTIFICATIONS`, `READ_MEDIA_IMAGES`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`.
- Camera hardware feature declared `required="false"`.

### 9.16 Application config
- `applicationId = "com.aistudio.elimtiyazstaff.bxmzlx"`
- `versionCode = 2`, `versionName = "2.0.0"`, `minSdk = 24`, `targetSdk = 36`, `compileSdk = 36`
- `multiDexEnabled = true`
- Default `SUPABASE_URL = "https://your-project.supabase.co"`, `SUPABASE_ANON_KEY = "your-anon-key"` (overridden by `.env` via secrets plugin).
- Release signing via `KEYSTORE_PATH` / `STORE_PASSWORD` / `KEY_PASSWORD` env vars; debug keystore fallback at `${rootDir}/debug.keystore`.
- `testOptions.unitTests.includeAndroidResources = true` (for Robolectric).

---

## 10. Gap List — What Mobile Had vs. What Production Needs

This list compares the commit `782bde1` mobile app to what a complete production parity with the desktop would require.

### 10.1 Working end-to-end (production-grade)
✅ **Auth** (with demo fallback) — sign-in/sign-out/change-password with strength check + global session revocation.
✅ **Parent CRUD** — full create/update/soft-delete via Supabase with audit logs.
✅ **Student CRUD** — full create/update + batch registration via `batch_register_family` RPC.
✅ **Student promotion** — `promote_students` RPC.
✅ **Payment collection** — Edge Function `collect-payment` (atomic: payment + installment + ledger + receipt + audit).
✅ **Payment refund** — Edge Function `refund-payment`.
✅ **Account adjustment** — `create_account_adjustment` RPC.
✅ **Ledger append/reverse/summary/reconcile** — full pure LedgerEngine + immutable Supabase table.
✅ **Expense workflow** — submit/approve/reject/disburse/settleProof via 4 RPCs.
✅ **Audit log** — `write_audit_log` RPC + observe/query.
✅ **Storage upload** — `tenantId/entityId/fileName` path convention, RLS-enforced.
✅ **FCM** — messaging service + 4 channels + token registration.
✅ **Theme** — full design system (Color, Shapes, Theme, Type, ElDesignTokens, SemanticColors).
✅ **Navigation** — type-safe 13 destinations with auth gate.
✅ **RBAC** — 11 roles, 56 permissions, default matrix, pure `FeatureGate.evaluate`.
✅ **LedgerEngine** — pure functions, 9 test cases, determinism verified.
✅ **Reconcile** — 10 checks, 28 violation codes, 15 test cases.
✅ **PiiMask** — 6 patterns, dedup, reversible, 15 test cases.

### 10.2 Incomplete / stubbed (UI exists, but data layer is fake)
⚠ **Notifications** — `StubNotificationRepository` returns empty list. Dashboard shows 3 hardcoded sample alerts.
⚠ **Debt dashboard** — `StubDebtRepository`. UI renders 0 debtors always.
⚠ **Installments** — `StubInstallmentRepository`. InstallmentScheduleScreen always shows "Selectionnez un parent".
⚠ **OnlineDetector** — `probeOk` never set to true → `online` always `false` → SyncWorker never runs. **Known bug.**
⚠ **SyncWorker.push\*** — all 9 entity push functions are empty stubs. Even if `online` were true, nothing would push.
⚠ **SyncService.observeCount** — uses `listPending().size` instead of the dedicated COUNT queries (placeholder).
⚠ **ProofScannerScreen** — ViewModel is wired and tested (WebP compression + min resolution check), but the "Capturer & téléverser" button onClick is `{}` (no CameraX integration, no proof file actually captured).
⚠ **ExpenseApprovalScreen** — `approve/reject` send hardcoded "Approuvé" / "Rejeté" as the note (no dialog to collect a real note from the user; server requires non-blank note).
⚠ **DashboardViewModel** — KPIs and alerts are hardcoded defaults, not from `DashboardRepository` (which is not even bound in DI).
⚠ **AI Assistant** — keyword-based canned responses, no LLM integration.
⚠ **AuditStreamScreen** — 3 hardcoded sample `AuditLog`s, not from `AuditRepository.observe`.

### 10.3 UI placeholder screens (no functionality at all)
❌ **SettingsScreen** — 4 tabs (Général/Sync/Config/Sécurité) each contain one card with a one-liner description. No actual settings controls.
❌ **ReleveScreen (PersonnelHub > Activité)** — 3 hardcoded teacher compliance cards.
❌ **EmployeeDirectoryScreen** — 5 hardcoded `SAMPLE_STAFF`. No `PersonnelRepository` impl exists.
❌ **RollCallScreen** — 6 hardcoded `SAMPLE_STUDENTS`. No `AttendanceRepository.recordRollCall` call.
❌ **GradeEntryScreen** — single hardcoded student "Amine Benali". No `GradeRepository.enterGrade` call.
❌ **HomeworkPushScreen** — no `HomeworkRepository.push` call, no CameraX for photo.
❌ **ClassesDirectoryScreen** — 5 hardcoded classes.

### 10.4 Domain interfaces declared but no implementation exists (would be `NullPointerException` if injected)
❌ `ClassRepository` — interface exists, no Supabase impl, no `@Binds`.
❌ `SubjectRepository` — same.
❌ `GradeRepository` — same.
❌ `AttendanceRepository` — same.
❌ `HomeworkRepository` — same.
❌ `PersonnelRepository` — same.
❌ `DepartmentRepository` — same.
❌ `DashboardRepository` — same.
❌ `PricingRepository` — same.

### 10.5 Other gaps vs. desktop parity
- **Realtime subscriptions** — `Realtime` plugin installed but **no `.realtime` channel subscriptions** anywhere in the codebase. UIs use one-shot Flow emissions.
- **Room cache reads** — DAOs exist but Supabase repositories **do not read from or write to Room**. Cache is unused.
- **`LocalTenantContext`** declared but **never provided** by any composable.
- **`Routes.Splash`** declared but unused.
- **`Routes.PaymentDetail`** declared but **not registered** in NavHost.
- **`Routes.ExpenseDetail(expenseId)`** is registered but the screen ignores the arg.
- **Per-route RBAC** — `FeatureGate` exists but `AppNavHost` does not call it. Any authenticated user can deep-link to any route.
- **Locale/i18n** — All UI strings are hard-coded French. No `strings.xml` resource files referenced (the manifest declares `@string/app_name` but UI text is inline).
- **Dark theme** — fully supported via `isSystemInDarkTheme()` + `ElDesignTokens`, but no in-app toggle (the placeholder SettingsScreen mentions "Thème" but has no control).
- **Backup/restore** — audit action constants exist (`backup.created`, `backup.restored`, `backup.purge`) but no UI or repository.
- **Workflow engine** — audit action constants exist (`workflow.published`, `workflow.triggered`, `workflow.run`) but no UI or repository.
- **AI narrative / anomaly / config** — audit action constants exist but no UI or repository.
- **Driver mode** — `Permission.ACCESS_DRIVER_MODE` + `Role.DRIVER` + audit action `manage.deliveries` exist but no driver-mode screen.
- **Chat** — `Permission.USE_CHAT`, `MANAGE_CHAT_CHANNELS` + `CHAT_ATTACHMENTS` bucket + audit actions exist but no chat UI.
- **Tasks** — `Permission.MANAGE_TASKS`, `VIEW_TASKS`, `UPDATE_TASK_STATUS` + `TASK_ATTACHMENTS` bucket exist but no task UI.
- **Pricing config** — `PricingRepository` interface + `PricingConfig` / `GradeLevelTuition` models exist but no UI or impl.
- **Personnel / Department / Relevé** — models + interfaces + audit actions exist but no Supabase impl and the only UI is hardcoded sample staff.
- **Activation code flow** — `ACTIVATION_CODE_BIND` / `ACTIVATION_CODE_GENERATE` audit actions + `BatchRegisterResult.activationCode` exist, but the parent-side activation code redemption is not in the mobile app (it's a web-portal feature).
- **Account approval flow** — `ACCOUNT_APPROVAL_APPROVE` / `REJECT` / `EXPIRE_BATCH` audit actions exist but no mobile UI.
- **Receipt PDF generation** — `RECEIPT_GENERATE` audit action exists, the Dashboard "Partager / Imprimer le Reçu PDF" button is wired to `{}` (no-op).
- **Materialized views refresh / overdue scan** — audit actions exist but no mobile UI.
- **Multi-tenant management** — `MANAGE_TENANTS` permission + `TenantContext` data class exist but no UI.
- **Wire-protocol parity tests** — only Role codes and 9 sampled Permission codes are hard-asserted in `FeatureGateTest`. Full parity with desktop is "verified by CI" but the test isn't in this repo.
- **Roborazzi screenshot tests** — only `GreetingScreenshotTest.kt` (default Android Studio template) exists; no real screenshot coverage of the actual screens.

### 10.6 Build / packaging observations
- `isMinifyEnabled = false` and `isShrinkResources = false` in release — no ProGuard/R8 shrinking configured.
- `exportSchema = false` on Room database — no JSON schema dumps for migration validation.
- `allowBackup = false` in manifest — correct for security but means no automatic restore.
- `googleServices { missingGoogleServicesStrategy = WARN }` — app builds even without `google-services.json` (FCM will silently fail).
- `packaging.resources.excludes` drops `META-INF/INDEX.LIST` and `META-INF/io.netty.versions.properties` — needed for Supabase/Netty dependencies.
- `applicationId` is `com.aistudio.elimtiyazstaff.bxmzlx` — the `.bxmzlx` suffix is unusual (likely an obfuscation/randomization choice for the Play Store listing).

---

**End of document.** This is the authoritative pre-wipe map. Use it as the spec for restoring the El-Imtiyaz Staff Android app.
