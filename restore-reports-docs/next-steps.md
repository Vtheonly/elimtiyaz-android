# Next Steps — Prioritized Roadmap

> **Audience:** Developers planning the next iteration.
> **Last updated:** 2026-08-02 (end of iteration 3).

This document is the prioritized backlog for iteration 4+. Each item
has: priority, effort, dependency, and a link to the detailed
description in `known-issues.md` or `current-status.md`.

**Priority legend:** P0 (blocking production) · P1 (important) · P2 (nice to have)
**Effort legend:** XS (<1h) · S (1-4h) · M (1 day) · L (3 days) · XL (1 week+)

---

## 1. Top 10 by Impact

| # | Item | Priority | Effort | Dependency | Detail |
|---|------|:-:|:-:|---|---|
| 1 | **Refactor `RoutePermissions` to use `AccessRequirement` + wire `FeatureGate.evaluate`** | P0 | M | None | `known-issues.md` K-07 |
| 2 | **Remove hardcoded demo data from `DashboardViewModel`** | P0 | S | None | `known-issues.md` K-01 |
| 3 | **Migrate 6 high-traffic screens to the new design system** | P1 | L | None | `current-status.md` § 3.2 |
| 4 | **Migrate remaining 11 Supabase repositories to `SyncSupport`** | P1 | L | Cache DAOs (item #8) | `known-issues.md` K-04 |
| 5 | **Add Account Approval flow** (mobile UI + bind approval repository) | P1 | L | None | `current-status.md` § 4 |
| 6 | **Fix `DashboardKpiDto` hardcoded `pendingExpenses` + `attendanceRateToday`** | P1 | XS | None | `known-issues.md` K-05 |
| 7 | **Fix `DebtAgingDto` hardcoded `parentPhone` + `studentCount`** | P1 | S | None | `known-issues.md` K-06 |
| 8 | **Add 12 cache DAOs + entities + mappers** (for full `cacheThenNetwork` coverage) | P1 | L | None | `known-issues.md` K-04 |
| 9 | **Add unit tests for `Pricing.kt` formulas** | P1 | S | None | `current-status.md` § 6 |
| 10 | **Deploy 2 RPCs + 2 Edge Functions** (desktop team) | P1 | — | External | `current-status.md` § 9 |

---

## 2. Detailed Iteration 4 Plan

### 2.1 RBAC Refactor (P0, M, 1 day)

**Why first:** Unblocks K-02 (Settings ungated) and K-03 (DashboardHub
wrong permission). Both are security issues.

**Steps:**
1. Define `AccessRequirement` sealed type in `core/Rbac.kt`:
   - `RequiresPermission(Permission)`
   - `RequiresAnyOf(List<Permission>)`
   - `RequiresAllOf(List<Permission>)`
   - `RequiresRole(List<Role>)`
   - `Permanent` (always visible)
   - `Empty` (never visible)
2. Refactor `RoutePermissions` from `Map<KClass<out Route>, Permission>`
   to `Map<KClass<out Route>, AccessRequirement>`.
3. Update `permissionFor(route)` to return `AccessRequirement?`.
4. Update `rbacGate` to call `FeatureGate.evaluate(requirement, session)`.
5. Add `Routes.Settings::class to AccessRequirement.RequiresAnyOf(...)`.
6. Change `Routes.DashboardHub::class to AccessRequirement.RequiresRole(...)`.
7. Add tests for each requirement type.

**Validation:** `./gradlew :app:testDebugUnitTest` passes; manual test
that a `Driver` role cannot access DashboardHub.

---

### 2.2 Remove Hardcoded Demo Data (P0, S, 2 hours)

**Why:** Operators see fake KPIs during outages — masks real problems.

**Steps:**
1. In `DashboardViewModel`, change every `stateIn(..., defaultX)` to
   `stateIn(..., null)` or `stateIn(..., emptyList())`.
2. Delete `defaultKpi`, `defaultRevenue`, `defaultDebtAging`,
   `defaultNotifications`, `defaultAttendanceTrend`.
3. In `DashboardHubScreen`, render `ElEmptyState` / `ElLoading` when
   `kpis` is `null`.
4. For `attendanceTrend`, either (a) remove the chart entirely until a
   7-day RPC exists, or (b) add `dashboardRepository.observeAttendanceTrend7Days()`.

**Validation:** Disconnect network → dashboard shows "Chargement…"
instead of fake KPIs.

---

### 2.3 Design-System Migration (P1, L, 3 days)

**Why:** 36 screens still use legacy `ui.components.*`. The new design
system is more consistent, accessible, and animated.

**Migrate these 6 screens first (highest traffic):**
1. `RollCallScreen` (teachers use daily)
2. `GradeEntryScreen` (teachers use daily)
3. `CounterPaymentScreen` (financial officers use daily)
4. `InstallmentScheduleScreen` (financial officers use daily)
5. `ParentsDirectoryScreen` (all staff use)
6. `StudentRosterScreen` (all staff use)

**Migration recipe** (per screen):
1. Read `ui/designsystem/ElDesignSystem.kt` (the barrel) to see available
   components.
2. Replace `import com.example.ui.components.ElCard` with
   `import com.example.ui.designsystem.components.card.ElCard`.
3. Replace `MaterialTheme.colors` with `ElTheme.colors`.
4. Replace `DangerRed` / `PrimaryBlue` / `SuccessGreen` / `WarmGold` with
   `ElTheme.colors.danger` / `.primary` / `.success` / `.warning`.
5. Run `./gradlew :app:compileDebugKotlin` after each screen.
6. Manually verify the screen renders correctly.

**Defer** the remaining 30 screens to iteration 5+.

---

### 2.4 SyncSupport Migration (P1, L, 3 days)

**Why:** 11 repositories still lose data when offline.

**Blocker:** Need to add 12 cache DAOs + entities + mappers first (item #8).

**Steps:**
1. Add cache DAOs for: Class, Subject, Grade, Attendance, Homework,
   Personnel, Department, Installment, Expense, Notification, AuditLog,
   DashboardKpi.
2. Add `@Entity` data classes for each (mirroring the domain model).
3. Add `CacheMappers.kt` bidirectional mappers.
4. Register the DAOs in `DatabaseModule.kt`.
5. For each of the 11 remaining repositories:
   a. Add `SyncSupport` to the `@Inject constructor`.
   b. Wrap `observe()` in `syncSupport.cacheThenNetwork(...)`.
   c. Wrap each mutation in `syncSupport.tryThenEnqueue(...)`.
   d. Add the corresponding `pushXxx` method to `SupabaseSyncDao`.
6. **Skip `SupabaseAuditRepository`** — Hilt cycle (see `decisions.md` D-07).

**Validation:** `./gradlew :app:testDebugUnitTest` passes; manual test
that an offline mutation syncs when connectivity returns.

---

### 2.5 Account Approval Flow (P1, L, 3 days)

**Why:** Admins can't approve new signups from mobile — must use desktop.

**Steps:**
1. Read desktop `supabase-approval-repository.ts` (60 lines).
2. Port to mobile as `SupabaseApprovalRepository.kt` implementing a new
   `ApprovalRepository` interface.
3. Add `Routes.AccountApprovals` to `Routes.kt` with `RequiresPermission(MANAGE_APPROVALS)`.
4. Create `AccountApprovalsScreen.kt` + `AccountApprovalsViewModel.kt`
   that lists pending requests + approve/reject buttons.
5. Bind in `RepositoryModule.kt`.

**Validation:** Manual test — approve a pending signup from mobile.

---

### 2.6 Quick Fixes (P1, XS-S, half a day)

These are small fixes that can be batched into a single PR:

| Fix | Effort | Detail |
|------|:-:|---|
| `DashboardKpiDto` read `pendingExpenses` + `attendanceRateToday` | XS | K-05 |
| `DebtAgingDto` read `parentPhone` + `studentCount` | S | K-06 |
| `ClassDto` add 3 joins | S | K-08 |
| `findOverdue` switch to `run_overdue_scan()` RPC | S | K-10 |
| `fetchAggregatedForStudentSubject` add `class_subjects` lookup | S | K-11 |
| `InstallmentDto` read `category` column | XS | K-12 |
| `HomeworkInsertDto` drop `subjectName` | XS | K-13 |
| `observeByClass` use embedded resource | XS | K-14 |

---

## 3. Deferred to Iteration 5+ (P2)

| # | Item | Priority | Effort | Notes |
|---|------|:-:|:-:|---|
| 11 | Pricing config UI tab | P2 | L | Repository exists, no UI |
| 12 | Department management UI | P2 | M | Repository exists, no UI |
| 13 | Employee create/edit form | P2 | M | Currently read-only |
| 14 | Task management + Chat | P2 | XL | New subsystem |
| 15 | Receipt PDF generation | P2 | L | Requires PDF library (iText 7 or Android Print Framework) |
| 16 | School calendar | P2 | L | New screen + Edge Function |
| 17 | Driver mode / routing | P2 | XL | GPS + OSRM + offline maps |
| 18 | Role-specific dashboards (8) | P2 | L | Currently single dashboard |
| 19 | Migrate remaining 30 screens to design system | P2 | L | Mechanical |
| 20 | Add UI tests | P2 | L | Currently 0 UI tests |
| 21 | Adopt conventional-commit format | P2 | XS | Going forward only |
| 22 | Bump AGP to 8.9.1+ + restore `compileSdk = 36` | P2 | XS | When ready for Android 16 features |
| 23 | Gate demo auth fallback behind `BuildConfig.DEBUG` | P2 | XS | Security hardening |
| 24 | Fix 12 `AutoMirrored` deprecation warnings | P2 | XS | Cosmetic |
| 25 | Fix 2 pre-existing test failures (K-15) | P2 | S | Test-only |

---

## 4. External Dependencies

These items require the desktop team to deploy something:

| # | Item | Owner | Blocks |
|---|------|------|------|
| 26 | Deploy `mark_installment_paid(p_id)` RPC | Desktop | `InstallmentRepository.markPaid` |
| 27 | Deploy `regenerate_installments(p_parent_id, p_cycle)` RPC | Desktop | `InstallmentRepository.regenerateForCycle` |
| 28 | Deploy `alert-absences` Edge Function | Desktop | `AttendanceRepository.alertAbsences` |
| 29 | Deploy `send-debt-reminder` Edge Function | Desktop | `DebtRepository.sendReminder` |

Until these are deployed, the mobile calls will fail with `PGRST202` /
function-not-found. The calling screens surface `Result.Err` gracefully
(no crash), but the feature is non-functional.

---

## 5. Recommended Iteration 4 Scope

**Duration:** 1 week
**Items:** #1 (RBAC refactor) + #2 (remove demo data) + #6 (quick fixes) + #9 (Pricing tests)
**Stretch:** #3 (design-system migration of 6 screens)

**Rationale:** #1 + #2 are P0 security/correctness fixes. #6 is a batch
of small fixes that close most of the "hardcoded field" issues. #9 adds
test coverage for the new `Pricing.kt` formulas. #3 is stretch because
it's mechanical but time-consuming.

---

## 6. Long-Term Vision

By the end of iteration 6, the mobile app should:
- ✅ Have full RBAC parity with desktop (`AccessRequirement` + `FeatureGate`).
- ✅ Have all 17 repositories migrated to `SyncSupport` (offline-first).
- ✅ Have all 47 screens migrated to the new design system.
- ✅ Have all 4 external RPCs + Edge Functions deployed.
- ✅ Have role-specific dashboards for the 8 staff roles.
- ✅ Have the Account Approval flow + Pricing config UI + Department
  management UI.
- ✅ Have UI tests for every screen.
- ✅ Have CI/CD with conventional-commit format + auto-generated changelog.

Features that will likely **remain desktop-only** (per plan §11, §13.05,
§14):
- Workflow editor (DAG canvas) — touch DnD impractical on mobile.
- Excel import engine — bulk import impractical on mobile.
- Backup — mobile PROHIBITED from backups.
- AI narrative / anomaly explainer — desktop-only per plan §11.
- RBAC matrix editor — too complex for mobile.

---

See also: [`restoration-plan.md`](restoration-plan.md) for the overall
strategy, [`decisions.md`](decisions.md) for architectural decisions,
and [`known-issues.md`](known-issues.md) for the bug catalog.
