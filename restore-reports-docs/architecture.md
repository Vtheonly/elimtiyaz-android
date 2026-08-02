# Architecture

> **Audience:** Developers who need to understand the codebase structure.
> **Read time:** ~15 minutes.

This document describes the folder structure, module responsibilities,
data flow, business logic flow, UI architecture, state management,
database interactions, API interactions, synchronization process, and
build process.

---

## 1. Folder Structure

```
elimtiyaz-android/
├── app/
│   ├── build.gradle.kts              # App module + dependencies + signing
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/example/
│       │   │   ├── ElImtiyazApplication.kt   # Hilt entry + WorkManager config
│       │   │   ├── MainActivity.kt           # Single-activity, Compose host
│       │   │   │
│       │   │   ├── core/                     # Pure business logic (no Android deps)
│       │   │   │   ├── AccountBalance.kt
│       │   │   │   ├── AuditActions.kt       # 60+ audit action constants
│       │   │   │   ├── Ledger.kt             # Ledger entry types + signed-amount convention
│       │   │   │   ├── LedgerEngine.kt       # Balance replay + parent summary
│       │   │   │   ├── LedgerEntryFactory.kt # Charge/Payment/Refund/Reversal factories
│       │   │   │   ├── ParentLedgerSummary.kt
│       │   │   │   ├── Pricing.kt            # Sibling discount + GPA + subject average
│       │   │   │   ├── PiiMask.kt            # Reversible PII masking
│       │   │   │   ├── Rbac.kt               # Role, Permission, Session, FeatureGate
│       │   │   │   ├── Reconcile.kt          # 8-check ledger integrity engine
│       │   │   │   └── Result.kt             # Result<T> sealed type + Errors factory
│       │   │   │
│       │   │   ├── domain/
│       │   │   │   ├── model/                # 20 @Serializable data classes
│       │   │   │   │   ├── Parent.kt
│       │   │   │   │   ├── Student.kt
│       │   │   │   │   ├── Payment.kt
│       │   │   │   │   ├── LedgerEntry.kt (in Models.kt)
│       │   │   │   │   ├── Assessment.kt
│       │   │   │   │   ├── Personnel.kt
│       │   │   │   │   ├── PricingConfig.kt
│       │   │   │   │   ├── DashboardKpi.kt
│       │   │   │   │   ├── DebtSummary.kt
│       │   │   │   │   ├── AppNotification.kt
│       │   │   │   │   └── ... (12 more)
│       │   │   │   └── repository/           # 20 repository interfaces
│       │   │   │       ├── AuthRepository.kt
│       │   │   │       ├── ParentRepository.kt
│       │   │   │       ├── StudentRepository.kt
│       │   │   │       ├── PaymentRepository.kt
│       │   │   │       ├── LedgerRepository.kt
│       │   │   │       ├── ExpenseRepository.kt
│       │   │   │       ├── AuditRepository.kt
│       │   │   │       ├── ClassRepository.kt
│       │   │   │       ├── SubjectRepository.kt
│       │   │   │       ├── GradeRepository.kt
│       │   │   │       ├── AttendanceRepository.kt
│       │   │   │       ├── HomeworkRepository.kt
│       │   │   │       ├── PersonnelRepository.kt
│       │   │   │       ├── DepartmentRepository.kt
│       │   │   │       ├── DashboardRepository.kt
│       │   │   │       ├── PricingRepository.kt
│       │   │   │       ├── InstallmentRepository.kt
│       │   │   │       ├── DebtRepository.kt
│       │   │   │       ├── NotificationRepository.kt
│       │   │   │       └── StorageRepository.kt
│       │   │   │
│       │   │   ├── infrastructure/
│       │   │   │   ├── supabase/              # 20 Supabase repository impls + DTOs
│       │   │   │   │   ├── SupabaseClientProvider.kt
│       │   │   │   │   ├── EncryptedSettingsStorage.kt   # Session persistence
│       │   │   │   │   ├── SupabaseAuthRepository.kt
│       │   │   │   │   ├── SupabaseParentRepository.kt   # Reference impl (cache+sync)
│       │   │   │   │   ├── SupabaseStudentRepository.kt
│       │   │   │   │   ├── SupabasePaymentRepository.kt  # tryThenEnqueue ✅
│       │   │   │   │   ├── SupabaseLedgerRepository.kt   # tryThenEnqueue ✅
│       │   │   │   │   ├── SupabaseExpenseRepository.kt
│       │   │   │   │   ├── SupabaseAuditRepository.kt
│       │   │   │   │   ├── SupabaseClassRepository.kt
│       │   │   │   │   ├── SupabaseSubjectRepository.kt
│       │   │   │   │   ├── SupabaseGradeRepository.kt
│       │   │   │   │   ├── SupabaseAttendanceRepository.kt # tryThenEnqueue ✅
│       │   │   │   │   ├── SupabaseHomeworkRepository.kt
│       │   │   │   │   ├── SupabasePersonnelRepository.kt
│       │   │   │   │   ├── SupabaseDepartmentRepository.kt
│       │   │   │   │   ├── SupabaseDashboardRepository.kt
│       │   │   │   │   ├── SupabasePricingRepository.kt
│       │   │   │   │   ├── SupabaseInstallmentRepository.kt
│       │   │   │   │   ├── SupabaseDebtRepository.kt
│       │   │   │   │   ├── SupabaseNotificationRepository.kt
│       │   │   │   │   ├── SupabaseStorageRepository.kt
│       │   │   │   │   ├── SupabaseSyncDao.kt             # Drain-side table writes
│       │   │   │   │   └── GradeDtos.kt
│       │   │   │   │
│       │   │   │   ├── room/                  # Offline cache + sync queue
│       │   │   │   │   ├── ElImtiyazDatabase.kt
│       │   │   │   │   ├── Entities.kt        # 9 cache entities + SyncQueueEntity
│       │   │   │   │   ├── Daos.kt            # 4 cache DAOs + SyncQueueDao
│       │   │   │   │   └── CacheMappers.kt    # Domain ↔ cache entity mappers
│       │   │   │   │
│       │   │   │   ├── sync/                  # Offline-first sync engine
│       │   │   │   │   ├── OnlineDetector.kt          # Connectivity + HEAD probe
│       │   │   │   │   ├── SyncService.kt             # Drain loop + public API
│       │   │   │   │   ├── SyncWorker.kt              # WorkManager 15-min periodic
│       │   │   │   │   ├── SyncQueueDispatcher.kt     # Entity → push handler routing
│       │   │   │   │   ├── SyncScheduler.kt           # WorkManager enqueue helper
│       │   │   │   │   ├── SyncSupport.kt             # tryThenEnqueue + cacheThenNetwork
│       │   │   │   │   ├── SyncSnapshot.kt            # Queue status snapshot
│       │   │   │   │   ├── SyncState.kt               # UI-facing sync state
│       │   │   │   │   └── DrainResult.kt
│       │   │   │   │
│       │   │   │   ├── notifications/
│       │   │   │   │   └── ElImtiyazMessagingService.kt  # FCM 4-channel push
│       │   │   │   │
│       │   │   │   └── stub/
│       │   │   │       └── StubRepositories.kt          # Emptied (kept for compat)
│       │   │   │
│       │   │   ├── di/                        # Hilt modules
│       │   │   │   ├── SupabaseModule.kt      # SupabaseClient singleton
│       │   │   │   ├── DatabaseModule.kt      # Room DB + DAOs + DataStore
│       │   │   │   └── RepositoryModule.kt    # 20 @Binds repository → impl
│       │   │   │
│       │   │   ├── session/
│       │   │   │   └── SessionManager.kt      # StateFlow<Session?> + restoreSession
│       │   │   │
│       │   │   └── ui/
│       │   │       ├── navigation/
│       │   │       │   ├── AppNavHost.kt      # 13 type-safe routes + RBAC gate
│       │   │       │   ├── Routes.kt          # Route types + RoutePermissions map
│       │   │       │   ├── rbacGate.kt        # Per-route permission check
│       │   │       │   ├── AppNavViewModel.kt
│       │   │       │   ├── LocalSession.kt    # CompositionLocal<Session?>
│       │   │       │   └── PermissionDeniedScreen.kt
│       │   │       │
│       │   │       ├── theme/                 # Legacy theme (brand palette + tokens)
│       │   │       │   ├── Color.kt
│       │   │       │   ├── ColorSchemes.kt    # DarkColorScheme + LightColorScheme
│       │   │       │   ├── ElDesignTokens.kt  # Gradients + glass + shimmer
│       │   │       │   ├── ElImtiyazTheme.kt  # M3 theme wrapper
│       │   │       │   ├── SemanticColors.kt
│       │   │       │   ├── Shapes.kt
│       │   │       │   └── Type.kt
│       │   │       │
│       │   │       ├── components/            # Legacy UI components (22 files)
│       │   │       │   ├── ElCard.kt
│       │   │       │   ├── ElButton.kt
│       │   │       │   ├── ElTextField.kt
│       │   │       │   ├── ElDropdown.kt
│       │   │       │   ├── ElScaffold.kt
│       │   │       │   ├── ElTopBar.kt
│       │   │       │   ├── ElDialog.kt
│       │   │       │   └── ... (14 more)
│       │   │       │
│       │   │       ├── designsystem/          # NEW design system (76 files)
│       │   │       │   ├── theme/             # 12 theme files
│       │   │       │   ├── foundation/        # 6 modifier files
│       │   │       │   ├── components/        # 36 component files
│       │   │       │   │   ├── button/        # 5
│       │   │       │   │   ├── card/          # 4
│       │   │       │   │   ├── data/          # 5 (charts + table)
│       │   │       │   │   ├── display/       # 9
│       │   │       │   │   ├── feedback/      # 4
│       │   │       │   │   ├── input/         # 7
│       │   │       │   │   ├── nav/           # 5
│       │   │       │   │   └── tabs/          # 1
│       │   │       │   ├── overlays/          # 10 overlay files
│       │   │       │   ├── gallery/           # 8 gallery files (design preview)
│       │   │       │   └── ElDesignSystem.kt  # Barrel re-export
│       │   │       │
│       │   │       └── features/              # 7 feature hubs
│       │   │           ├── auth/              # LoginScreen, ChangePasswordModal
│       │   │           ├── main/              # MainScreen (bottom-nav host)
│       │   │           ├── dashboard/         # 11 files (fully migrated to design system)
│       │   │           ├── crm/               # 6 files (CrmHub + Parents + Students)
│       │   │           ├── academics/         # 9 files (RollCall + Grades + Homework + Classes)
│       │   │           ├── financials/        # 9 files (Counter + Debt + Installments + Expenses + Proof)
│       │   │           ├── personnel/         # 7 files (Directory + Releve + Audit + SignOut)
│       │   │           └── settings/          # 14 files (Profile + Prefs + Security + Sync + Diagnostics + AuditLog)
│       │   │
│       │   └── res/                           # Drawables, strings, themes
│       │
│       └── test/java/com/example/             # 12 test files
│           ├── core/                          # 6 test files (LedgerEngine, Reconcile, PiiMask, FeatureGate, RolePermission, LedgerEntryFactory)
│           ├── session/                       # SessionManagerTest (7 tests)
│           ├── infrastructure/room/           # CacheMappersTest (5 tests)
│           └── ... (3 scaffolding tests)
│
├── gradle/
│   ├── wrapper/
│   │   ├── gradle-wrapper.jar
│   │   └── gradle-wrapper.properties          # Gradle 9.3.0
│   └── libs.versions.toml                     # Version catalog (~60 libraries)
│
├── build.gradle.kts                           # Root build script
├── settings.gradle.kts                        # Module declarations
├── gradle.properties                          # JVM args + AndroidX flags
├── gradlew / gradlew.bat                      # Wrapper scripts
├── .env.example                               # SUPABASE_URL + SUPABASE_ANON_KEY
├── .gitignore
├── metadata.json                              # AI Studio metadata
├── README.md                                  # Points to restore-reports-docs/
└── restore-reports-docs/                      # ← This documentation
```

---

## 2. Module Responsibilities

### 2.1 `core/` — Pure business logic

**No Android dependencies.** Pure Kotlin + kotlinx.serialization. This is
the layer that mirrors the desktop's `src/core/*` TypeScript modules.

| File | Responsibility |
|------|----------------|
| `LedgerEngine.kt` | Replay-based balance computation. `computeAccountBalance(entries, accountId, now)` and `computeParentSummary(...)`. Signed-amount convention: `+charge, -payment, ±adjustment, -refund, -reversal-of-original`. |
| `LedgerEntryFactory.kt` | Factory functions for each entry type. `deriveAccountId(parentId, category, studentId)` produces the canonical wire format `parent:{pid}:category:{cat}[:student:{sid}]`. |
| `Pricing.kt` | Financial formulas: `computeSiblingDiscount`, `computeOverallGpa`, `computeSubjectAverage`, `isPassing`, `validateScore`, `computeTuitionTotal`, `findDiscountByCode`. Mirrors desktop `domain/model/pricing.ts` + `academic.ts`. |
| `Reconcile.kt` | 8-check ledger integrity engine. 14 passing tests. |
| `PiiMask.kt` | Reversible PII masking (phone, email, name). 17 passing tests. |
| `Rbac.kt` | `Role` enum (11 roles), `Permission` enum (56 permissions), `Session` data class, `FeatureGate` (dead code — see `known-issues.md`). |
| `AuditActions.kt` | 60+ audit action constants matching desktop `core/audit-actions.ts`. |
| `Result.kt` | `Result<T>` sealed type (`Ok` / `Err`) + `Errors` factory with 10 error codes. |

### 2.2 `domain/` — Interfaces + models

**No Android dependencies.** Defines the contracts that the
infrastructure layer implements.

- `domain/model/` — 20 `@Serializable` data classes (Parent, Student,
  Payment, LedgerEntry, Assessment, Personnel, etc.).
- `domain/repository/` — 20 repository interfaces. Every interface has
  exactly one Supabase implementation in `infrastructure/supabase/`.

### 2.3 `infrastructure/` — Implementations

#### `infrastructure/supabase/` — 20 repository implementations

Each repository:
1. Implements its domain interface.
2. Uses `@Serializable` DTOs with snake_case column names + `toDomain()` mappers.
3. Returns `Flow<List<T>>` for reads (via `observe()` / `observeByXxx()`).
4. Returns `Result<T>` for mutations.
5. Calls `auditRepository.log(AuditLogInput(...))` on every state-changing operation.
6. Uses `provider.functions.invoke(...)` for Edge Functions and
   `provider.postgrest.rpc(...)` for PostgreSQL SECURITY DEFINER functions.

**Offline-first migration status** (see `known-issues.md` § K-04):

| Repository | `tryThenEnqueue` | `cacheThenNetwork` |
|------|:-:|:-:|
| `SupabaseParentRepository` | ✅ | ✅ |
| `SupabasePaymentRepository` | ✅ | ✅ |
| `SupabaseLedgerRepository` | ✅ | ✅ |
| `SupabaseAttendanceRepository` | ✅ | ❌ (no cache DAO) |
| `SupabaseStudentRepository` | ❌ | ❌ |
| `SupabaseExpenseRepository` | ❌ | ❌ |
| `SupabaseAuditRepository` | ❌ (Hilt cycle — see `decisions.md` D-07) | ❌ |
| Other 9 repositories | ❌ | ❌ |

#### `infrastructure/room/` — Offline cache + sync queue

- **4 cache DAOs**: `ParentCacheDao`, `StudentCacheDao`, `PaymentCacheDao`,
  `LedgerCacheDao`. Used by `SyncSupport.cacheThenNetwork()`.
- **`SyncQueueDao`**: persists offline mutations to the `sync_queue`
  table. Drained by `SyncService.drainPending()`.

#### `infrastructure/sync/` — Offline-first sync engine

| File | Responsibility |
|------|----------------|
| `OnlineDetector.kt` | Combines `ConnectivityManager` callback + 30s HEAD probe to `${SUPABASE_URL}/auth/v1/health`. Exposes `isOnline()` + `observeOnline(): Flow<Boolean>`. |
| `SyncService.kt` | `drainPending()` loop with `Mutex` re-entrancy guard, exponential backoff (`1000 × 2^attempts` ms, max 5), per-row failure isolation, audit-log surface for permanent failures. `syncNow()` for manual trigger. `schedulePeriodicSync()` for 15-min WorkManager. |
| `SyncWorker.kt` | Thin WorkManager wrapper around `syncService.drainPending()`. |
| `SyncSupport.kt` | `tryThenEnqueue(entity, operation, payload, isMock, sourceScreen) { mutation }` for writes. `cacheThenNetwork(cacheRead, cacheWrite, fetch)` for reads. |
| `SupabaseSyncDao.kt` | Drain-side table writes: `pushParent` → `parents` (upsert), `pushPayment` → `payments` (insert), `pushLedgerEntry` → `ledger_entries` (insert), etc. |

#### `infrastructure/notifications/` — FCM

`ElImtiyazMessagingService` handles 4 notification channels:
`expense_pending`, `attendance_alert`, `payment_overdue`, `workflow_run`.
FCM topic subscriptions swap on role change (`role_${role.code}`).

### 2.4 `di/` — Hilt modules

- `SupabaseModule.kt` — provides `SupabaseClientProvider` (which lazily
  builds the `SupabaseClient` with Auth + Postgrest + Realtime + Storage
  + Functions plugins).
- `DatabaseModule.kt` — provides Room database + DAOs +
  `DataStore<Preferences>` for settings persistence.
- `RepositoryModule.kt` — 20 `@Binds` mappings from interface to Supabase
  implementation.

### 2.5 `ui/` — Jetpack Compose

#### `ui/navigation/`

- `AppNavHost.kt` — 13 type-safe routes. `LaunchedEffect(currentSession)`
  reacts to async session restore and navigates to `Routes.Main` once
  the session becomes non-null.
- `Routes.kt` — `Route` sealed types + `RoutePermissions` map (13 routes
  guarded by single `Permission` each — see `known-issues.md` K-07).
- `rbacGate.kt` — composition-time permission check; redirects to
  `Routes.PermissionDenied` on denial.

#### `ui/theme/` — Legacy brand theme

The "legacy" theme that most screens still use. Provides:
- `Color.kt` — brand palette (PrimaryBlue, WarmGold, SuccessGreen, etc.)
- `ColorSchemes.kt` — `DarkColorScheme` + `LightColorScheme` (M3)
- `ElDesignTokens.kt` — gradients, glass, shimmer, shadows
- `ElImtiyazTheme.kt` — M3 `MaterialTheme` wrapper with `LocalElDesignTokens`

#### `ui/components/` — Legacy UI components (22 files)

The original component library from pre-wipe commit `782bde1`. Still
used by 36 feature files. Being progressively migrated to `ui/designsystem/`.

#### `ui/designsystem/` — NEW design system (76 files)

The modern design system that survived the wipe. Provides:
- 12 theme files (color, typography, spacing, shape, elevation, motion, borders)
- 6 foundation modifiers (press-scale, clickable, glass, shadow, border)
- 36 component files in 7 sub-packages
- 10 overlay files (dialogs, bottom sheets, tooltips, context menus, toasts)
- 8 gallery files (design preview activity)

**Currently only `DashboardHubScreen` is fully migrated.** The other 36
screens still use legacy `ui.components.*` imports.

#### `ui/features/` — 7 feature hubs

| Hub | Files | Status |
|------|-------|--------|
| `auth/` | 4 | Legacy theme |
| `main/` | 1 | Legacy theme |
| `dashboard/` | 11 | ✅ Fully migrated to design system |
| `crm/` | 6 | Legacy theme |
| `academics/` | 9 | Legacy theme |
| `financials/` | 9 | Legacy theme |
| `personnel/` | 7 | Legacy theme |
| `settings/` | 14 | Legacy theme |

---

## 3. Data Flow

### 3.1 Read flow (cache-then-network)

```
UI (Composable)
  │
  ├── viewModel.someState.collectAsState()
  │
  ViewModel (Hilt)
  │
  ├── repository.observe()
  │
  Repository (Supabase impl)
  │
  ├── syncSupport.cacheThenNetwork(
  │       cacheRead = { cacheDao.listAll().map { it.toDomain() } },
  │       cacheWrite = { rows -> cacheDao.upsertAll(rows.map { it.toCacheEntity() }) },
  │       fetch = { provider.postgrest.from("table").select().decodeList<Dto>() }
  │   )
  │
  Flow emits:
    1. Cached rows immediately (offline → cache only)
    2. Fresh rows from Supabase (online → cache updated)
```

### 3.2 Write flow (try-then-enqueue)

```
UI (Composable)
  │
  ├── viewModel.createXxx(input)
  │
  ViewModel
  │
  ├── viewModelScope.launch {
  │       repository.createXxx(input, actorId, actorName)
  │           .onSuccess { /* update UI state */ }
  │           .onFailure { err -> /* show err.userMessage */ }
  │   }
  │
  Repository (Supabase impl)
  │
  ├── syncSupport.tryThenEnqueue(
  │       entity = "xxx",
  │       operation = "create",
  │       payload = { json.encodeToString(XxxInsertDto.serializer(), dto) },
  │       sourceScreen = "XxxScreen",
  │   ) {
  │       // Direct Supabase call
  │       val inserted = provider.postgrest.from("xxx").insert(dto) { select() }.decodeList<XxxDto>().first()
  │       val domain = inserted.toDomain()
  │       // Update cache
  │       cacheDao.upsert(domain.toCacheEntity())
  │       // Audit log
  │       auditRepository.log(AuditLogInput(action = "xxx.create", ...))
  │       domain
  │   }
  │
  On network failure:
    → Enqueue to sync_queue table
    → Return Result.Err(Errors.offline("..."))
  On success:
    → Return Result.Ok(domain)
```

### 3.3 Sync drain flow (WorkManager, every 15 minutes)

```
WorkManager (15-min periodic)
  │
  ├── SyncWorker.doWork()
  │
  SyncService.drainPending()
  │
  ├── Mutex.withLock {
  │       for (entry in syncQueueDao.listPending()) {
  │           try {
  │               queueDispatcher.dispatch(entry)  // → supabaseSyncDao.pushXxx(entry)
  │               entry.status = "synced"
  │           } catch (e) {
  │               entry.attempts++
  │               if (entry.attempts >= 5) {
  │                   entry.status = "failed"
  │                   auditRepository.log(AuditActions.SYNC_PUSH_FAILED, ...)
  │               } else {
  │                   delay(1000 * 2^entry.attempts)  // exponential backoff
  │               }
  │           }
  │       }
  │   }
```

---

## 4. Business Logic Flow

### 4.1 Financial engine (ledger-based)

Every financial operation (charge, payment, refund, adjustment, reversal)
appends an immutable `LedgerEntry` to the `ledger_entries` table. Balances
are computed by **replaying** the entries:

```kotlin
fun computeAccountBalance(entries: List<LedgerEntry>, accountId: String, now: Instant): Long {
    val active = entries.filter { it.accountId == accountId && !it.isReversed }
    return active.sumOf { it.amount }  // signed: +charge, -payment, ±adjustment, -refund
}
```

Account IDs are derived (never stored as separate entities):
```
parent:{parentId}:category:{category}[:student:{studentId}]
```

### 4.2 Subject average + GPA

```kotlin
// Per-subject (computed server-side by compute_grade_subject_average() trigger):
val subjectAverage = (devoir1 + devoir2 + 2 * examen) / 4.0

// Overall GPA (computed client-side in core/Pricing.kt):
val gpa = assessments.filter { it.subjectAverage != null }
    .let { list -> list.sumOf { it.subjectAverage!! * it.coefficient } } / list.sumOf { it.coefficient }
```

### 4.3 Debt aging

5 buckets, computed nightly by the `mv_debt_aging` materialized view:
- `0_30` — 0 to 30 days overdue
- `31_60` — 31 to 60 days
- `61_90` — 61 to 90 days
- `91_180` — 91 to 180 days
- `180_plus` — 180+ days

### 4.4 Discounts

5 canonical discount codes (per plan §06.04):
- `passage_palier` — −10,000 DZD fixed
- `seniority_5y` — −5%
- `full_annual` — −10% (before June 30)
- `highest_average` — −10%
- `sibling_fixed` — −5,000 DZD per additional child

`computeSiblingDiscount(config, N) = (N - 1) × sibling_fixed.amount`

---

## 5. UI Architecture

- **Single-activity** (`MainActivity`) hosting a Compose `AppNavHost`.
- **Type-safe navigation** via `androidx.navigation:navigation-compose`
  with 13 `@Serializable` route objects.
- **Hilt-injected ViewModels** via `androidx.hilt:hilt-navigation-compose`.
- **State management** via `StateFlow<T>` exposed by ViewModels, collected
  in Composables with `collectAsState()`.
- **CompositionLocals**: `LocalSession` (current `Session?`),
  `LocalElDesignTokens` (theme tokens).
- **Theme**: `ElImtiyazTheme {}` wraps M3 `MaterialTheme` with brand
  color schemes + design tokens.

---

## 6. State Management

| State scope | Mechanism | Example |
|------|------|------|
| **App-global** | `SessionManager.state: StateFlow<Session?>` (Hilt singleton) | Current user session |
| **Per-screen** | `@HiltViewModel` + `StateFlow<T>` | `DashboardViewModel.kpis` |
| **Per-composable** | `remember { mutableStateOf(...) }` | Form field values |
| **Persisted** | `DataStore<Preferences>` | Dark mode, language, force-offline |
| **Sync queue** | Room `sync_queue` table | Offline mutations |

---

## 7. Database Interactions

### 7.1 Supabase (PostgreSQL) — source of truth

The mobile app talks to the **same Supabase backend** as the desktop:
- 24 SQL migrations (50+ tables, 60+ RLS policies, 14 SECURITY DEFINER
  functions, 5 materialized views, 50+ performance indexes)
- 11 Edge Functions (collect-payment, refund-payment, run-overdue-scan,
  refresh-materialized-views, etc.)

**RLS enforces tenant isolation server-side.** The mobile client only
uses the `anon` key (never `service_role`).

### 7.2 Room — offline cache + sync queue

- **Database:** `ElImtiyazDatabase` (version 1, `fallbackToDestructiveMigrationOnDowngrade`)
- **Tables:** `parents_cache`, `students_cache`, `payments_cache`,
  `ledger_cache`, `sync_queue`
- **DAOs:** 4 cache DAOs (read + upsert) + `SyncQueueDao` (insert +
  listPending + updateStatus)

---

## 8. API Interactions

### 8.1 Postgrest (CRUD)

```kotlin
provider.postgrest.from("parents")
    .select { filter { eq("id", parentId) } }
    .decodeList<ParentDto>()
```

### 8.2 RPC (SECURITY DEFINER functions)

```kotlin
provider.postgrest.rpc("write_audit_log", buildJsonObject {
    put("p_action", input.action)
    put("p_entity_type", input.entityType)
    // ...
}).decodeAs<String>()
```

### 8.3 Edge Functions

```kotlin
provider.functions.invoke("collect-payment", parameters = buildJsonObject {
    put("payment", paymentDto)
})
```

### 8.4 Storage

```kotlin
provider.storage.from("payment-proofs")
    .upload("$entityId/$fileName", bytes) { contentType = "image/webp" }
```

### 8.5 Realtime

Subscribed in `ElImtiyazApplication` for live updates (currently
underutilized — see `next-steps.md`).

---

## 9. Synchronization Process

See `infrastructure/sync/` (§ 2.3 above) and `work-log.md` (iteration 1
WAVE1-C-SYNC-RBAC + iteration 3 ITER3-SYNCSUPPORT).

**Key invariants:**
1. Mock data is NEVER pushed (defense-in-depth at enqueue + drain).
2. Exponential backoff: `1000 × 2^attempts` ms, max 5 attempts.
3. Each row's failure is isolated — one bad row does NOT block others.
4. Permanent failures are surfaced via `sync.push_failed` audit log entry.
5. Tenant isolation via RLS (server-side).
6. No main-thread I/O (all DAO/network on `Dispatchers.IO`).

---

## 10. Build Process

### 10.1 Toolchain

- **AGP:** 8.8.0
- **Kotlin:** 2.0.21
- **KSP:** 2.0.21-1.0.28
- **Compose BOM:** 2024.09.00
- **Hilt:** 2.52
- **Supabase Kotlin SDK:** 3.1.1
- **Room:** 2.7.0
- **WorkManager:** 2.10.0
- **Gradle:** 9.3.0
- **JDK:** 21
- **compileSdk:** 35 (AGP 8.8.0 doesn't officially support 36 — see `decisions.md` D-04)
- **minSdk:** 24
- **targetSdk:** 35

### 10.2 Build commands

```bash
# Compile
./gradlew :app:compileDebugKotlin

# Run unit tests
./gradlew :app:testDebugUnitTest

# Build debug APK (outputs to app/build/outputs/apk/debug/app-debug.apk)
./gradlew :app:assembleDebug

# Build release APK (requires signing config)
./gradlew :app:assembleRelease
```

### 10.3 Configuration

1. `local.properties` — points to Android SDK (`sdk.dir=...`)
2. `.env` — Supabase credentials (`SUPABASE_URL`, `SUPABASE_ANON_KEY`).
   The `secrets-gradle-plugin` reads these and generates `BuildConfig`
   fields.
3. `google-services.json` — Firebase config (for FCM). Place in `app/`.

---

See also: [`current-status.md`](current-status.md) for what's working
and what's broken, and [`decisions.md`](decisions.md) for the
architectural decisions that shaped this structure.
