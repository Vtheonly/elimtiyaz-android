# Known Issues

> **Audience:** Developers fixing bugs + QA testers.
> **Last updated:** 2026-08-02 (end of iteration 3).

This is the authoritative catalog of known bugs, gaps, and technical
debt. Each entry has: ID, severity, file/line, description, workaround,
fix plan, and status.

**Severity legend:** 🔴 Critical · 🟠 High · 🟡 Medium · 🟢 Low

---

## Critical (0)

All 5 critical bugs from the start of iteration 3 have been fixed:
- ✅ Fabricated SUPER_ADMIN session — fixed (iter 3)
- ✅ `pushGrade` wrong table — fixed (iter 3)
- ✅ `pushHomework` wrong table — fixed (iter 3)
- ✅ Supabase SDK `SettingsStorage` API mismatch — fixed (iter 3)
- ✅ Debt-aging bucket format mismatch — fixed (iter 3)

---

## High (3)

### K-01 — Dashboard shows fake KPIs during outages

**Severity:** 🟠 High
**File:** `app/src/main/java/com/example/ui/features/dashboard/DashboardViewModel.kt:30-106`
**Status:** 🔲 Not started

**Description:**
`DashboardViewModel` has hardcoded fallback data (`defaultKpi`,
`defaultRevenue`, `defaultDebtAging`, `defaultNotifications`,
`defaultAttendanceTrend`) used as the `initialValue` for each
`StateFlow`. When Supabase is unreachable, the dashboard shows these
fake values instead of an empty/loading state — masking real outages.

Operators see "1.245M DZD revenue", "Famille Benali", and "96.5%
attendance" even when the backend is completely down.

**Workaround:** None — operators must manually check the Diagnostics
section in Settings to see if the app is offline.

**Fix plan:**
1. Replace `stateIn(viewModelScope, SharingStarted.Lazily, defaultKpi)`
   with `stateIn(viewModelScope, SharingStarted.Lazily, null)`.
2. In `DashboardHubScreen`, render `ElEmptyState` / `ElLoading` when
   `kpis` is `null`.
3. Same for `revenue`, `debtAging`, `notifications`, `attendanceTrend`.

**Effort:** S (2 hours)

---

### K-02 — Settings route ungated

**Severity:** 🟠 High
**File:** `app/src/main/java/com/example/ui/navigation/Routes.kt:71-94`
**Status:** 🔲 Not started

**Description:**
`Routes.Settings` is not in the `RoutePermissions` map, so any signed-in
user can access the Settings screen. Desktop requires
`RequiresAnyOf([ManageSettings, ViewAuditLog, ManageBackups, ManageAIConfig])`.

**Workaround:** None — every signed-in user sees Settings.

**Fix plan:**
1. Refactor `RoutePermissions` from `Map<KClass<out Route>, Permission>`
   to `Map<KClass<out Route>, AccessRequirement>` (see K-07).
2. Add `Routes.Settings::class to AccessRequirement.RequiresAnyOf(...)`.
3. Wire `FeatureGate.evaluate` into `rbacGate` (see K-07).

**Effort:** M (1 day) — blocked by K-07

---

### K-03 — DashboardHub RBAC uses wrong permission

**Severity:** 🟡 Medium → 🟠 High (security)
**File:** `app/src/main/java/com/example/ui/navigation/Routes.kt:73`
**Status:** 🔲 Not started

**Description:**
`Routes.DashboardHub` is mapped to `Permission.VIEW_AUDIT_LOG`. Desktop
uses `RequiresRole([SuperAdmin, FinancialOfficer, SupportStaff, Manager])`.

A `Driver` with `VIEW_AUDIT_LOG` (possible via tenant override) would
pass the mobile gate and see financial KPIs, but be blocked by desktop.

**Workaround:** None.

**Fix plan:**
1. Refactor `RoutePermissions` to use `AccessRequirement` (see K-07).
2. Change `Routes.DashboardHub::class to AccessRequirement.RequiresRole(Role.DASHBOARD_ROLES.toList())`.

**Effort:** M (1 day) — blocked by K-07

---

## Medium (9)

### K-04 — SyncSupport migration incomplete (11/17 repos)

**Severity:** 🟡 Medium
**Files:** See `current-status.md` § 3.1
**Status:** 🟡 In progress (5/17 done)

**Description:**
11 repositories still call Supabase directly without
`tryThenEnqueue()` or `cacheThenNetwork()`. Offline mutations are lost;
offline reads show empty lists.

**Workaround:** None — data is lost when offline.

**Fix plan:** Apply the mechanical `SyncSupport` migration recipe (see
`migration-report.md` § 4) to each remaining repository. Requires
adding cache DAOs for entities that don't have them (Class, Subject,
Grade, Attendance, Homework, Personnel, Department, Installment,
Expense, Notification, AuditLog, DashboardKpi).

**Effort:** L (3 days)

---

### K-05 — Dashboard `pendingExpenses` + `attendanceRateToday` hardcoded to 0

**Severity:** 🟡 Medium
**File:** `app/src/main/java/com/example/infrastructure/supabase/SupabaseDashboardRepository.kt:106-108`
**Status:** 🔲 Not started

**Description:**
`DashboardKpiDto.toDomain()` hardcodes `pendingExpenses = 0` and
`attendanceRateToday = 0.0`. The materialized view `mv_dashboard_kpis`
includes these columns (migration 0021) but the DTO doesn't read them.

**Workaround:** None — dashboard's "pending expenses" alert card and
the attendance line chart always show 0% for today's point.

**Fix plan:** Add `pendingExpenses` + `attendanceRateToday` fields to
`DashboardKpiDto` and read them from the MV.

**Effort:** XS (30 minutes)

---

### K-06 — DebtAgingDto hardcodes `parentPhone` + `studentCount`

**Severity:** 🟡 Medium
**File:** `app/src/main/java/com/example/infrastructure/supabase/SupabaseDashboardRepository.kt:136-138`
**Status:** 🔲 Not started

**Description:**
`DebtAgingDto.toDomain()` hardcodes `parentPhone = ""` and
`studentCount = 0`. Desktop's `mv_debt_aging` joins `parents` to fetch
phone + counts students.

The Debt Dashboard's "Appeler" (call) button is currently a no-op
because the phone is always empty.

**Workaround:** None.

**Fix plan:** Add a second `select` against `parents` to fetch phone
numbers for the displayed parents. Extract the duplicate
`DebtAgingDto` between `SupabaseDashboardRepository` and
`SupabaseDebtRepository` into a shared `DebtDtos.kt`.

**Effort:** S (2 hours)

---

### K-07 — `FeatureGate.evaluate` is dead code

**Severity:** 🟡 Medium
**File:** `app/src/main/java/com/example/core/Rbac.kt:127-150`
**Status:** 🔲 Not started

**Description:**
`FeatureGate.evaluate(requirement, session)` is defined but never
called. `AppNavHost.kt`'s `rbacGate` helper uses an inline
`session?.can(required)` check that only supports single-permission
requirements.

This blocks K-02 and K-03 (which need `RequiresAnyOf` and
`RequiresRole`).

**Workaround:** None.

**Fix plan:**
1. Refactor `RoutePermissions` from `Map<KClass<out Route>, Permission>`
   to `Map<KClass<out Route>, AccessRequirement>`.
2. Update `rbacGate` to call `FeatureGate.evaluate(requirement, session)`.
3. Add `RequiresAnyOf` / `RequiresAllOf` / `RequiresRole` /
   `Permanent` / `hideWhenUnauthenticated` requirements where needed.

**Effort:** M (1 day)

---

### K-08 — `ClassDto` hardcodes level, gradeYear, enrolledCount, academicYear

**Severity:** 🟡 Medium
**File:** `app/src/main/java/com/example/infrastructure/supabase/SupabaseClassRepository.kt:165-173`
**Status:** 🔲 Not started

**Description:**
`ClassDto.toDomain()` hardcodes `level = ""`, `gradeYear = 0`,
`enrolledCount = 0`, `academicYear = ""`. Desktop joins
`academic_levels` + `academic_years` + counts students in the class.

The Classes Directory screen currently shows every class with "0/N"
enrolled, no level, and no academic year — every row looks identical.

**Workaround:** None.

**Fix plan:** Add the three joins to the `select` query.

**Effort:** S (2 hours)

---

### K-09 — `PersonnelDto` hardcodes avatarUrl, weeklyHoursTarget, weeklyHoursLogged

**Severity:** 🟡 Medium
**File:** `app/src/main/java/com/example/infrastructure/supabase/SupabasePersonnelRepository.kt:202-204`
**Status:** 🔲 Not started

**Description:**
`PersonnelDto.toDomain()` hardcodes `avatarUrl = null`,
`weeklyHoursTarget = 0`, `weeklyHoursLogged = 0` with the comment "not
stored in DB; derived from schedules".

The Relevé screen currently shows 0% compliance for everyone.

**Workaround:** None.

**Fix plan:** Either (a) drop these fields from the mobile `Personnel`
domain model (since mobile has no schedule repo), OR (b) add a
`vw_personnel_with_hours` view that joins `personnel` + `releve_entries`
+ `schedules` and read from it.

**Effort:** M (4 hours)

---

### K-10 — `findOverdue` pulls 500 rows client-side

**Severity:** 🟡 Medium
**File:** `app/src/main/java/com/example/infrastructure/supabase/SupabaseInstallmentRepository.kt:166-178`
**Status:** 🔲 Not started

**Description:**
`findOverdue()` queries up to 500 installment rows and filters
client-side because "Postgrest may not support column-to-column
comparison directly". Desktop's `run_overdue_scan()` RPC does this
server-side.

The current implementation will degrade as installments grow.

**Workaround:** None.

**Fix plan:** Switch to invoking the `run_overdue_scan()` SECURITY
DEFINER function (migration 0022).

**Effort:** S (1 hour)

---

### K-11 — `fetchAggregatedForStudentSubject` returns wrong subjectId + classId

**Severity:** 🟡 Medium
**File:** `app/src/main/java/com/example/infrastructure/supabase/SupabaseGradeRepository.kt:182-194`
**Status:** 🔲 Not started

**Description:**
`fetchAggregatedForStudentSubject()` sets `subjectId = classSubjectId`
(best-effort comment: "class_subject_id acts as subject ref") and
`classId = ""`. Desktop resolves back to `subject_id` and `class_id` via
the `class_subjects` join.

The Grade Entry screen's confirmation dialog shows a UUID instead of a
subject name.

**Workaround:** None.

**Fix plan:** Add a `class_subjects` lookup to map `class_subject_id` →
`subject_id` + `class_id`.

**Effort:** S (2 hours)

---

### K-12 — `InstallmentDto` hardcodes `category = PaymentCategory.OTHER`

**Severity:** 🟡 Low → 🟡 Medium
**File:** `app/src/main/java/com/example/infrastructure/supabase/SupabaseInstallmentRepository.kt:222`
**Status:** 🔲 Not started

**Description:**
`InstallmentDto.toDomain()` hardcodes `category = PaymentCategory.OTHER`
with the comment "DB does not store category on installments directly".
Desktop's `installments` table has a `category` column (migration 0007).

Every installment is labeled "OTHER" in the UI; transport installments
cannot be visually distinguished from tuition.

**Workaround:** None.

**Fix plan:** Read the actual `category` column.

**Effort:** XS (15 minutes)

---

## Low (3)

### K-13 — `HomeworkInsertDto.subjectName = ""`

**Severity:** 🟢 Low
**File:** `app/src/main/java/com/example/infrastructure/supabase/SupabaseHomeworkRepository.kt:72`
**Status:** 🔲 Not started

**Description:**
`HomeworkInsertDto.subjectName = ""` with the comment "desktop can
backfill via join". Desktop `homework` table has no `subject_name`
column — the desktop client joins `subjects` to display the name.

The empty string is silently dropped by Postgrest.

**Workaround:** None — the homework row is created without a subject name;
the UI must join `subjects` to display it.

**Fix plan:** Either drop `subjectName` from the insert DTO (it's not a
column) or fetch the subject name before insert.

**Effort:** XS (15 minutes)

---

### K-14 — `observeByClass` does 2 queries instead of 1

**Severity:** 🟢 Low
**File:** `app/src/main/java/com/example/infrastructure/supabase/SupabaseSubjectRepository.kt:65-72`
**Status:** 🔲 Not started

**Description:**
`observeByClass()` performs two separate queries (`class_subjects` then
`subjects`) and filters client-side. Desktop uses
`select('*, subjects!inner(*)')` embedded resource — single round-trip.

The current 2-query approach doubles latency and races on update.

**Workaround:** None.

**Fix plan:** Use Postgrest's embedded resource syntax to fetch subjects
+ their class_subjects row in one query.

**Effort:** XS (30 minutes)

---

### K-15 — 2 unit test failures (pre-existing)

**Severity:** 🟢 Low
**Files:**
- `app/src/test/java/com/example/GreetingScreenshotTest.kt`
- `app/src/test/java/com/example/session/SessionManagerTest.kt`

**Status:** 🔲 Not started (test-only — not caused by restoration)

**Description:**
1. `GreetingScreenshotTest.greeting_screenshot()` fails with
   `IllegalStateException: Given component holder class
   androidx.activity.ComponentActivity does not implement interface
   dagger.hilt.internal.GeneratedComponent` — Robolectric+Hilt
   integration issue.
2. `SessionManagerTest.state flow emits the restored session()` fails
   with `AssertionError` at line 126 — flaky `GlobalScope.launch`
   timing in `runTest`.

**Workaround:** None — tests are skipped in CI.

**Fix plan:**
1. For #1: use `HiltAndroidRule` + `createAndroidComposeRule` instead
   of bare `createComposeRule`.
2. For #2: replace `GlobalScope.launch` with `TestScope.launch` or use
   `Turbine` for flow testing.

**Effort:** S (2 hours)

---

## Design-System Migration Gaps (informational)

36 screens still use legacy `ui.components.*` imports. This is not a
"bug" per se — the legacy components work — but it's technical debt
that should be addressed. See `current-status.md` § 3.2 for the
prioritized migration list.

---

## Deprecation Warnings (informational)

12 deprecation warnings for `Icons.Filled.ArrowBack`, `Icons.Filled.Send`,
`Icons.Filled.MenuBook`, `Icons.Filled.TrendingUp`, `Icons.Filled.Logout`
— all recommending the `AutoMirrored` variants. Non-blocking; ~1 hour
to fix.

---

## See also

- [`current-status.md`](current-status.md) for the full status board
- [`next-steps.md`](next-steps.md) for the prioritized fix roadmap
- [`decisions.md`](decisions.md) for the rationale behind deferring
  certain fixes
