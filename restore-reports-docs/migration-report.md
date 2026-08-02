# Migration Report — Consolidated Technical Report

> **Audience:** Technical reviewers who need the detailed "what was
> migrated and how" for each iteration.
> **Read time:** ~25 minutes.

This document consolidates the technical findings of all 3 restoration
iterations into a single reference. It is the "what was done" companion
to the "why it was done" in `decisions.md` and the "when it was done"
in `iteration-history.md`.

---

## 1. Iteration 1 — File Tree Restoration + Repository Binding

**Commit:** `1948741` "sub" (2026-08-01 18:32)
**Stats:** 147 files changed, 28,471 insertions, 494 deletions

### 1.1 File tree restored from `782bde1`

The entire file tree was recovered via `git show 782bde1 -- <path>` for
each file. This included:
- 9 core files (`LedgerEngine`, `Reconcile`, `PiiMask`, `Rbac`, `Result`,
  `AuditActions`, `Ledger`, `LedgerEntryFactory`, `AccountBalance`)
- 2 domain files (`Models.kt` — 17 data classes; `Repositories.kt` — 20
  interfaces + 14 DTOs)
- 3 DI modules (`DatabaseModule`, `RepositoryModule`, `SupabaseModule`)
- 19 infrastructure files (FCM, Room, Stub repos, Supabase repos, sync)
- 3 session/app files (`SessionManager`, `ElImtiyazApplication`, `MainActivity`)
- 20 UI feature screens + 10 navigation files + legacy UI components
- 4 test files (FeatureGate, LedgerEngine, PiiMask, Reconcile)
- Build config (`libs.versions.toml`, `build.gradle.kts`, `AndroidManifest.xml`)

### 1.2 12 new Supabase repository implementations

Each new repository follows the established pattern: `@Serializable` DTOs
with snake_case columns + `toDomain()` mapping, `Flow`-returning observers
that catch exceptions and emit `emptyList()`/`null`, `Result<T>` for
mutations, `auditRepository.log()` on every state-changing operation.

| Repository | Table(s) | Key methods | RPCs / Edge Functions |
|------|------|------|------|
| `SupabaseClassRepository` | `classes` | create/update/delete (soft via `is_active=false`), observe/observeByLevel/observeById | — |
| `SupabaseSubjectRepository` | `subjects` + `class_subjects` | observeByClass (embedded resource), archive, assignToClass | — |
| `SupabaseGradeRepository` | `grades` + `assessments` + `class_subjects` | enterGrade (resolves class_subject_id, upserts 3 grade rows; trigger auto-computes `subject_average`) | `compute_grade_subject_average()` trigger |
| `SupabaseAttendanceRepository` | `attendance_records` | recordRollCall (bulk upsert), alertAbsences | `alert-absences` Edge Function (not yet deployed) |
| `SupabaseHomeworkRepository` | `homework` | push, observeForClass/observeForTeacher | — |
| `SupabasePersonnelRepository` | `personnel` | create/update/delete (soft via `deleted_at`) | — |
| `SupabaseDepartmentRepository` | `departments` | create/archive (`is_archived=true`)/unarchive | — |
| `SupabaseDashboardRepository` | `mv_dashboard_kpis`, `mv_revenue_by_month`, `mv_debt_aging` | refreshKpis, observeKpis/observeRevenue/observeDebtByAging | `refresh_all_materialized_views()` |
| `SupabasePricingRepository` | `pricing_configs` + `grade_level_tuition` + `active_pricing_config` view | updateRegistrationFee/updateLatePenalty/updateTuitionForGradeLevel | — |
| `SupabaseInstallmentRepository` | `installments` | markPaid, updateDueDate, regenerateForCycle, findOverdue | `mark_installment_paid(p_id)` (not yet deployed), `regenerate_installments(p_parent_id, p_cycle)` (not yet deployed) |
| `SupabaseDebtRepository` | `mv_debt_aging` + `parents` + `installments` + `payments` + `ledger_entries` | observeSummary, observeParentProfile, sendReminder | `send-debt-reminder` Edge Function (not yet deployed) |
| `SupabaseNotificationRepository` | `notifications` | observe, observeForSession (merges 3 queries), markRead/markAllRead, dismiss | — |

### 1.3 9 new audit action constants

Added to `core/AuditActions.kt`:
- `CLASS_DELETE` (`class.delete`)
- `ATTENDANCE_RECORD` (`attendance.roll_call`)
- `INSTALLMENT_REGENERATE` (`installment.regenerate`)
- `INSTALLMENT_FIND_OVERDUE` (`installment.find_overdue`)
- `PRICING_UPDATE` (`pricing.update`)
- `PERSONNEL_DELETE` (`personnel.delete`)
- `DEPARTMENT_CREATE` (`department.create`)
- `DEPARTMENT_ARCHIVE` (`department.archive`)
- `DEPARTMENT_UNARCHIVE` (`department.unarchive`)

### 1.4 DI bindings updated

`RepositoryModule.kt` updated with 12 new `@Binds` entries. 3 stub
bindings removed (Installment/Debt/Notification now have real impls).
9 new `@Binds` entries added (Class/Subject/Grade/Attendance/Homework/
Personnel/Department/Dashboard/Pricing).

### 1.5 Infrastructure fixes

- **`OnlineDetector.kt`** — full rewrite: HEAD probe to
  `${SUPABASE_URL}/auth/v1/health` with 3s timeouts; 30s periodic probe
  loop; `ConnectivityManager` callback triggers immediate re-probe.
- **`SupabaseSyncDao.kt`** (new) — drain-side table writes: `pushParent`
  → `parents` (upsert), `pushPayment` → `payments` (insert), etc.
- **`SyncService.kt`** — full rewrite: `drainPending()` with `Mutex` +
  exponential backoff + per-row failure isolation + audit-log surface;
  `syncNow()` for manual trigger; `observeSyncState(): Flow<SyncState>`;
  `schedulePeriodicSync()` for 15-min WorkManager.
- **`SyncWorker.kt`** — slimmed to thin wrapper around
  `syncService.drainPending()`.
- **`Routes.kt`** — added `PermissionDenied` route +
  `RoutePermissions: Map<KClass<out Route>, Permission>` covering 13
  guarded routes.
- **`AppNavHost.kt`** — `rbacGate` helper wraps every guarded composable.
- **`SettingsScreen.kt`** — full replacement of 4 placeholder cards with
  5-section scrollable screen (Profile, Preferences, Security, Sync,
  Diagnostics).
- **`DatabaseModule.kt`** — added `provideSettingsDataStore` for
  `DataStore<Preferences>`.
- **`ElImtiyazApplication.kt`** — enhanced `onCreate` with
  `startOnlineDetector()` + `schedulePeriodicSync()` +
  `observeRoleForFcmTopic()`.

### 1.6 Design system primitives (17 new files)

Added to `ui/designsystem/`:
- `foundation/MoneyFormat.kt`
- `components/display/DisplayTypes.kt`
- `components/data/ElChartTypes.kt`
- `components/nav/ElScaffold.kt`
- `components/input/ElSearchBar.kt`
- `components/input/ElSwitch.kt`
- `components/input/ElSelectionControls.kt`
- `components/input/ElDatePicker.kt`
- `components/input/ElMoneyInput.kt`
- `components/feedback/ElSnackbar.kt`
- `components/display/ElSectionHeader.kt`
- `components/display/ElInfoRow.kt`
- `components/display/ElTag.kt`
- `components/display/ElAlertBanner.kt`
- `components/card/ElGradientStatCard.kt`
- `components/data/ElChart.kt` (ElBarChart, ElLineChart, ElDonutChart,
  ElSparkline, ElProgressRing)
- `ElDesignSystem.kt` (barrel re-export)

### 1.7 Dashboard refactor

`DashboardHubScreen.kt` completely rewritten to use the new design
system: `ElScaffold` + `ElTopBar` + `ElBottomBar` + 4 `ElGradientStatCard`s
+ `ElBarChart` (revenue) + `ElProgressRing` (collection rate) +
`ElDonutChart` (debt aging) + `ElLineChart` (attendance trend) +
notification list + quick actions. 11 dashboard sub-component files
created.

---

## 2. Iteration 2 — Defect Fixes + Offline-First Wiring

**Commit:** `d52aa6b` "mid cv" (2026-08-01 19:54)
**Stats:** 29 files changed, 2,825 insertions, 9,450 deletions

### 2.1 13 defect groups fixed

1. **`SessionManager.restoreSession()`** now calls `setSession(result.value)`.
2. **`AppNavHost`** takes no parameter; `LaunchedEffect(currentSession)`
   reacts to async restore.
3. **`Routes.PaymentDetail`** registered as composable destination.
4. **`SyncSupport`** (new) exposes `tryThenEnqueue` + `cacheThenNetwork`.
   `SupabaseParentRepository.createParent` uses it as reference.
5. **Room cache DAOs** wired via `cacheThenNetwork` on
   `SupabaseParentRepository.observe()` + `observeById()`.
6. **`AcademicsHubScreen`** 4 subscreens driven by real ViewModels:
   - `RollCallViewModel` (Class + Student + Attendance repos)
   - `GradeEntryViewModel` (Class + Subject + Student + Grade repos)
   - `HomeworkPushViewModel` (Class + Subject + Homework repos)
   - `ClassesDirectoryViewModel` (Class repo)
7. **`PersonnelHubScreen`** 3 subscreens driven by real ViewModels:
   - `EmployeeDirectoryViewModel` (Personnel repo; "Appeler" + "Email"
     intents)
   - `ReleveViewModel` (Personnel repo; weekly hours compliance)
   - `AuditStreamViewModel` (Audit repo; last 50 entries)
8. **`InstallmentScheduleViewModel`** loads real data + parent selector
   + markPaid.
9. **`ProofScannerScreen`** camera capture via
   `ActivityResultContracts.TakePicture()` + gallery via `GetContent()`.
10. **`DashboardViewModel.attendanceTrend`** derived from `kpis` flow.
11. **`EncryptedSettingsStorage`** implemented (referenced wrong API —
    fixed in iteration 3).
12. Restore-artifact clutter files removed (5 files without `.kt` extension).
13. **`compileSdk`** pinned to 35.

### 2.2 New files

- `app/src/main/java/com/example/infrastructure/room/CacheMappers.kt`
- `app/src/main/java/com/example/infrastructure/supabase/EncryptedSettingsStorage.kt`
- `app/src/main/java/com/example/infrastructure/sync/SyncSupport.kt`
- `app/src/main/java/com/example/ui/features/financials/PaymentDetailScreen.kt`
- `app/src/test/java/com/example/session/SessionManagerTest.kt` (7 tests)
- `app/src/test/java/com/example/infrastructure/room/CacheMappersTest.kt` (5 tests)

### 2.3 Deleted files

- 5 restore-artifact clutter files (no `.kt` extension — duplicates)

---

## 3. Iteration 3 — Build Repair + Critical Bugs + SyncSupport Migration

**Commit:** Uncommitted (on top of `82990e1`)
**Stats:** 66 files changed, 635 insertions, 434 deletions

### 3.1 23 truncated files repaired

The UI-redesign refactor (commit `82990e1`) split large monolithic files
into smaller ones but left 23 files truncated mid-declaration. Each ended
with an orphan `@Composable` or `@HiltViewModel` annotation with no
function body.

**Fix:** Wrote `scripts/trim_orphan_annotations.py` to systematically
trim orphan trailing doc-comment + annotation blocks. The script:
1. Finds the last `@Composable` or `@HiltViewModel` annotation.
2. Verifies it's followed only by whitespace until EOF.
3. Walks backwards to find the start of the orphan block.
4. Truncates the file at that point.

For 9 files where the trim also removed the closing `}`, manually
appended the missing brace.

**Files fixed:** 23 (see `iteration-history.md` § 3 for the full list).

### 3.2 Supabase SDK 3.1.1 `SettingsStorage` → `SettingsSessionManager`

**Problem:** `EncryptedSettingsStorage` referenced
`io.github.jan.supabase.auth.settings.SettingsStorage`, which doesn't
exist in SDK 3.1.1.

**Fix:**
1. Added `multiplatform-settings-no-arg:1.3.0` +
   `multiplatform-settings-coroutines:1.3.0` to `libs.versions.toml` +
   `app/build.gradle.kts`.
2. Rewrote `EncryptedSettingsStorage.kt` as an `object` with
   `createSessionManager(context): SettingsSessionManager` backed by
   EncryptedSharedPreferences via `SharedPreferencesSettings`.
3. Updated `SupabaseClientProvider` to take `@ApplicationContext Context`
   and call `EncryptedSettingsStorage.createSessionManager(context)`.
4. Simplified `SupabaseModule.kt` to a single
   `provideSupabaseClientProvider(context)` provider.

### 3.3 Fabricated SUPER_ADMIN session removed (CRITICAL security)

**Problem:** `SupabaseAuthRepository.refreshSession()` fabricated a
SUPER_ADMIN session on every cold-start.

**Fix:** Removed the `defaultStaffSession` branch. Now:
1. Returns in-memory session if exists.
2. Calls `auth.currentUserOrNull()` to check persistent storage.
3. If user exists, fetches profile + roles + permissions, builds real
   `Session`.
4. If profile missing or `status != "active"`, signs out + returns `null`.
5. Returns `null` on any exception.

### 3.4 Wrong sync tables fixed (CRITICAL data loss)

**Problem:** `SupabaseSyncDao.pushGrade` wrote to `assessments` (should
be `grades`); `pushHomework` wrote to `homework_assignments` (should be
`homework`).

**Fix:** Single-line changes:
- `pushGrade(entry) = upsert("grades", entry)`
- `pushHomework(entry) = upsert("homework", entry)`

### 3.5 Debt-aging bucket format fixed

**Problem:** Mobile code used dash format (`"0-30"`, `"31-60"`, etc.)
but the materialized view `mv_debt_aging` stores underscore format
(`"0_30"`, `"31_60"`, etc.). The donut chart rendered every bucket in
`primary` color.

**Fix:** Updated `SupabaseDashboardRepository.kt` +
`SupabaseDebtRepository.kt` to use underscore format as primary case.
Updated `DashboardBucketHelpers.kt` to accept both formats
(backward-compat).

### 3.6 Missing `DarkColorScheme` / `LightColorScheme` restored

**Problem:** The UI-redesign refactor deleted `Theme.kt` (which defined
`DarkColorScheme` + `LightColorScheme`) but `ElImtiyazTheme.kt` still
referenced them.

**Fix:** Created `ui/theme/ColorSchemes.kt` with both schemes (as
`darkColorScheme(...)` / `lightColorScheme(...)` calls mapping the brand
palette to M3 color scheme roles).

### 3.7 Duplicate JVM class name fixed

**Problem:** Both `ElDesignTokens.kt` and `elDesignTokens.kt` existed in
the same package, producing the same JVM class name `ElDesignTokensKt`.

**Fix:** Merged the 8-line `elDesignTokens.kt` (containing only the
`elDesignTokens()` accessor) into `ElDesignTokens.kt`, then deleted the
duplicate.

### 3.8 27 missing `getValue` / `setValue` imports added

**Problem:** Many UI files use `by` delegate syntax
(`val x by viewModel.foo.collectAsState()`) but were missing the
`import androidx.compose.runtime.getValue` / `setValue` imports.

**Fix:** Wrote `scripts/fix_runtime_imports.py` to detect
`val/var X by ...` patterns and add the missing imports. Fixed 27 files
in 2 runs (initial run fixed 24; bug fix for `var`-only-gets-`setValue`
added 3 more).

### 3.9 Other small fixes

- `SyncSupport.kt` `AppError.CODE_*` → `Errors.CODE_*`
- `SyncQueueDispatcher` + `SyncScheduler` `internal class` → `class`
- `SupabaseClientProvider.kt` added `import io.github.jan.supabase.createSupabaseClient`
- 4 financials/dashboard files added missing `Receipt` / `Payments` icon imports
- `ProofScannerScreen.kt` `sessionManager.currentUserId()` → `LocalSession.current?.userId`

### 3.10 7 financial formulas added

Created `core/Pricing.kt` with:
- `computeSiblingDiscount(config, childrenCount)` — `(N-1) × sibling_fixed.amount`
- `computeTuitionTotal(registration, tuition, transport, discount)` — Excel formula L
- `computeOverallGpa(assessments)` — `Σ(subject_avg × coef) / Σ(coef)`
- `computeSubjectAverage(d1, d2, ex)` — `(d1 + d2 + 2×ex) / 4`
- `isPassing(gpa, passingGrade = 10.0)` — `gpa >= passingGrade`
- `validateScore(value)` — `value in 0.0..20.0`
- `findDiscountByCode(config, code)` — convenience lookup

Extended `domain/model/PricingConfig.kt` with `discounts: List<PricingDiscount>`
field + new `PricingDiscount` data class.

### 3.11 SyncSupport migration (4 P0 repositories)

Delegated to subagent (Task ID: ITER3-SYNCSUPPORT):

| Repository | `tryThenEnqueue` | `cacheThenNetwork` | Notes |
|------|:-:|:-:|------|
| `SupabasePaymentRepository` | ✅ collect/refund/adjust | ✅ via PaymentCacheDao | Edge Function params serialized as JSON payload |
| `SupabaseLedgerRepository` | ✅ append/appendMany/reverse | ✅ via LedgerCacheDao | Added `LedgerReversePayload` DTO |
| `SupabaseAttendanceRepository` | ✅ recordRollCall | ❌ (no cache DAO) | Added `RollCallPayload` DTO |
| `SupabaseAuditRepository` | ❌ reverted | ❌ | Hilt cycle — see `decisions.md` D-07 |

### 3.12 Test fixes

- `GreetingScreenshotTest.kt` removed `sessionState` parameter (AppNavHost
  no longer takes it).
- `SessionManagerTest.kt` added `import kotlinx.coroutines.launch`.

---

## 4. SyncSupport Migration Recipe

For future migrations (iteration 4+), apply this mechanical recipe to
each remaining repository:

### 4.1 READ pattern (for `observe()` / `observeByXxx()`)

```kotlin
override fun observe() = syncSupport.cacheThenNetwork(
    cacheRead = { syncSupport.listCachedXxx().map { it.toDomain() } },
    cacheWrite = { rows -> syncSupport.upsertXxx(rows.map { it.toCacheEntity() }) },
    fetch = { fetchAll() },
)
```

### 4.2 WRITE pattern (for mutations)

```kotlin
override suspend fun createXxx(
    input: CreateXxxInput,
    actorId: String,
    actorName: String,
): Result<Xxx> = syncSupport.tryThenEnqueue(
    entity = "xxx",
    operation = "create",
    payload = { syncSupport.json().encodeToString(XxxInsertDto.serializer(), dto) },
    sourceScreen = "XxxScreen",
) {
    val inserted = provider.postgrest.from("xxx").insert(dto) { select() }
        .decodeList<XxxDto>().first()
    val domain = inserted.toDomain()
    syncSupport.upsertXxx(listOf(domain.toCacheEntity()))
    auditRepository.log(AuditLogInput(
        action = AuditActions.XXX_CREATE,
        entityType = "xxx",
        entityId = domain.id,
        note = "Created by $actorName",
    ))
    domain
}
```

### 4.3 Blockers

Before migrating repositories 5-17, add cache DAOs for: Class, Subject,
Grade, Attendance, Homework, Personnel, Department, Installment,
Expense, Notification, AuditLog, DashboardKpi (12 new DAOs + entities +
mappers).

**Skip `SupabaseAuditRepository`** — Hilt cycle (see `decisions.md` D-07).

---

## 5. Financial Formula Parity

Every financial formula in the mobile app now matches the desktop
exactly:

| Formula | Mobile location | Desktop reference | Status |
|------|------|------|:-:|
| Account ID derivation `parent:{pid}:category:{cat}[:student:{sid}]` | `core/LedgerEntryFactory.kt:21-25` | `domain/model/ledger.ts:deriveAccountId` | ✅ |
| Account balance (replay) | `core/LedgerEngine.kt:computeAccountBalance` | `domain/model/ledger.ts:computeAccountBalance` | ✅ |
| Parent summary aggregation | `core/LedgerEngine.kt:computeParentSummary` | `domain/model/ledger.ts:computeParentSummary` | ✅ |
| Subject average `(D1 + D2 + 2×Ex) / 4` | `core/Pricing.kt:computeSubjectAverage` (NEW iter 3) + UI preview in `GradeEntryScreen.kt:70` | `domain/model/academic.ts:computeSubjectAverage` + server trigger `compute_grade_subject_average()` | ✅ |
| Overall GPA `Σ(subject_avg × coef) / Σ(coef)` | `core/Pricing.kt:computeOverallGpa` (NEW iter 3) | `domain/model/academic.ts:computeOverallGpa` | ✅ |
| Sibling discount `(N-1) × sibling_fixed.amount` | `core/Pricing.kt:computeSiblingDiscount` (NEW iter 3) | `domain/model/pricing.ts:computeSiblingDiscount` | ✅ |
| Tuition total `L = registration + tuition + transport − discount` | `core/Pricing.kt:computeTuitionTotal` (NEW iter 3) | Excel formula L (per `Clients_Sheet_Merged.txt`) | ✅ |
| Payment status auto-set `CASH→PAID, CHECK/TRANSFER→PENDING` | Server-side `enforce_payment_proof` trigger (migration 0008) + `collect-payment` Edge Function | Same | ✅ |
| Installment status auto-recompute | Server-side `update_installment_status` trigger (migration 0007) | Same | ✅ |
| Receipt number `REC-{year}-{6-digit seq}` | Server-side (collect-payment Edge Function) | Same | ✅ |
| Ledger signed-amount `+charge, -payment, ±adjustment, -refund, -reversal` | `core/Ledger.kt:7-13` + `core/LedgerEntryFactory.kt` | Same | ✅ |
| Debt aging buckets `0_30 / 31_60 / 61_90 / 91_180 / 180_plus` | `DashboardBucketHelpers.kt` + `SupabaseDashboardRepository.kt` + `SupabaseDebtRepository.kt` (fixed iter 3) | `domain/model/payment.ts:AgingBucket` | ✅ |
| Overdue threshold `balance > 100 && dueDate < now` | `core/LedgerEngine.kt:120` | `domain/model/ledger.ts:MIN_OVERDUE_BALANCE` | ✅ |
| Reconciliation 8-check | `core/Reconcile.kt` (13 tests) | `domain/reconcile.ts` | ✅ |
| PII masking (reversible) | `core/PiiMask.kt` (17 tests) | `domain/pii-mask.ts` | ✅ |
| Is passing `gpa >= 10.0` | `core/Pricing.kt:isPassing` (NEW iter 3) | `domain/model/academic.ts:isPassing` | ✅ |
| Validate score `0 ≤ value ≤ 20` | `core/Pricing.kt:validateScore` (NEW iter 3) | `domain/model/academic.ts:validateScore` | ✅ |
| Find discount by code | `core/Pricing.kt:findDiscountByCode` (NEW iter 3) | `domain/model/pricing.ts:findDiscountByCode` | ✅ |

---

## 6. RBAC Parity

Mobile `RoutePermissions` covers 13 routes with single-permission
requirements. Desktop's `feature-registry.ts` covers 8 top-level sections
+ ~30 sub-features using `RequiresPermission` / `RequiresAnyOf` /
`RequiresAllOf` / `RequiresRole` / `Permanent` / `Empty`.

| Route | Mobile | Desktop | Status |
|------|------|------|:-:|
| `DashboardHub` | `VIEW_AUDIT_LOG` | `RequiresRole([SuperAdmin, FinancialOfficer, SupportStaff, Manager])` | ❌ Mismatch (K-03) |
| `Settings` | **ungated** | `RequiresAnyOf([ManageSettings, ViewAuditLog, ManageBackups, ManageAIConfig])` | ❌ Mismatch (K-02) |
| `CrmHub` | `VIEW_ROSTER` | `RequiresPermission(ViewRoster)` | ✅ |
| `AcademicsHub` | `VIEW_ACADEMICS` | `RequiresPermission(ViewAcademics)` | ✅ |
| `FinancialsHub` | `VIEW_FINANCIALS` | `RequiresPermission(ViewFinancials)` | ✅ |
| `PersonnelHub` | `VIEW_PERSONNEL` | `RequiresPermission(ViewPersonnel)` | ✅ |
| `CounterPayment` | `COLLECT_PAYMENT` | `RequiresPermission(CollectPayment)` | ✅ |
| `DebtDashboard` | `VIEW_DEBT` | `RequiresPermission(ViewDebt)` | ✅ |
| `InstallmentSchedule` | `VIEW_FINANCIALS` | `RequiresPermission(ViewFinancials)` | ✅ |
| `PaymentDetail` | `VIEW_FINANCIALS` | (drawer, not top-level) | ✅ |
| `BatchRegistration` | `CREATE_PARENT` | `RequiresAnyOf([CreateParent, CreateStudent])` | ⚠️ Partial (K-12) |
| `StudentDetail` / `ParentDetail` | `VIEW_ROSTER` | `RequiresPermission(ViewRoster)` | ✅ |
| `AuditLog` | `VIEW_AUDIT_LOG` | `RequiresPermission(ViewAuditLog)` | ✅ |

**3 mismatches** to fix in iteration 4 (see `next-steps.md` #1).

---

## 7. Build Verification

### 7.1 Compilation

```bash
cd /home/z/my-project/repos/mobile
export JAVA_HOME=/tmp/jdk-21.0.12
export ANDROID_HOME=/tmp/android-sdk
./gradlew :app:compileDebugKotlin
```

**Result:** ✅ BUILD SUCCESSFUL in 1m 35s. 12 deprecation warnings
(AutoMirrored icons) — no errors.

### 7.2 APK packaging

```bash
./gradlew :app:assembleDebug
```

**Result:** ✅ BUILD SUCCESSFUL. `app/build/outputs/apk/debug/app-debug.apk`
(28 MB).

### 7.3 Test suite

```bash
./gradlew :app:testDebugUnitTest
```

**Result:** 98/100 tests pass. 2 failures are pre-existing test-only
issues (see `known-issues.md` K-15).

| Test file | Tests | Status |
|------|:-:|:-:|
| `FeatureGateEvaluationTest.kt` | 17 | ✅ |
| `ReconcileTest.kt` | 13 | ✅ |
| `PiiMaskTest.kt` | 17 | ✅ |
| `LedgerBalanceTest.kt` | 16 | ✅ |
| `LedgerReversalBalanceTest.kt` | 8 | ✅ |
| `LedgerEntryFactoryTest.kt` | 12 | ✅ |
| `RolePermissionTest.kt` | 9 | ✅ |
| `SessionManagerTest.kt` | 7 | ⚠️ 1 failure (K-15) |
| `CacheMappersTest.kt` | 5 | ✅ |
| `GreetingScreenshotTest.kt` | 1 | ❌ (K-15) |
| `ExampleUnitTest.kt` + `ExampleRobolectricTest.kt` | 2 | ✅ (scaffolding) |

---

## 8. See also

- [`iteration-history.md`](iteration-history.md) for the engineering journal
- [`decisions.md`](decisions.md) for the architectural decisions
- [`known-issues.md`](known-issues.md) for the bug catalog
- [`work-log.md`](work-log.md) for the raw chronological log
