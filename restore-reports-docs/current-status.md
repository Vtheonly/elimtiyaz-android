# Current Status

> **Audience:** Anyone who needs to know what works today.
> **Last updated:** 2026-08-02 (end of iteration 3).

This document describes the current progress, completed modules,
incomplete modules, missing business logic, broken functionality,
technical debt, known bugs, risks, and blockers.

**Headline status:** ✅ Compiles · ✅ 28 MB APK builds · ✅ 98/100 tests pass · ✅ Modern UI preserved · 🟡 11 repos need SyncSupport migration · 🟡 36 screens need design-system migration.

---

## 1. Current Progress

### 1.1 Build

| Check | Status | Notes |
|------|------|------|
| `./gradlew :app:compileDebugKotlin` | ✅ Pass | 1m 35s, 12 deprecation warnings (AutoMirrored icons) |
| `./gradlew :app:assembleDebug` | ✅ Pass | 28 MB `app-debug.apk` |
| `./gradlew :app:testDebugUnitTest` | ⚠️ 98/100 | 2 pre-existing test-only failures (Robolectric+Hilt, GlobalScope timing) |
| Lint | Not run | Deferred to iteration 4 |

### 1.2 Source code

| Metric | Count |
|------|------|
| Kotlin source files | 312 |
| Test files | 12 |
| Design system files | 76 |
| Legacy UI component files | 22 |
| Feature hub screens | 7 hubs, 56 screens total |
| Supabase repository implementations | 20 |
| Domain repository interfaces | 20 |
| Domain models | 20 |
| Core business logic files | 9 |
| Hilt `@Binds` mappings | 20 |
| Type-safe navigation routes | 13 |

### 1.3 Iteration progress

| Iteration | Status | Commit |
|------|------|------|
| 0 — Investigation | ✅ Complete | — |
| 1 — File tree + repository binding | ✅ Complete | `1948741` |
| 2 — Defect fixes + offline-first wiring | ✅ Complete | `d52aa6b` |
| 3 — Build repair + critical bugs + SyncSupport | ✅ Complete | (uncommitted, on top of `82990e1`) |
| 4 — RBAC refactor + design-system migration | 🔲 Not started | — |

---

## 2. Completed Modules

### 2.1 Core business logic (`core/`)

| Module | Status | Tests | Notes |
|------|------|------|------|
| `LedgerEngine` | ✅ | 16 | Balance replay + parent summary |
| `LedgerEntryFactory` | ✅ | 12 | Charge/Payment/Refund/Reversal factories |
| `Reconcile` | ✅ | 13 | 8-check ledger integrity engine |
| `PiiMask` | ✅ | 17 | Reversible PII masking |
| `Rbac` (Role, Permission, Session) | ✅ | 26 | 11 roles, 56 permissions |
| `Pricing` (NEW in iter 3) | ✅ | 0 (TODO) | 7 financial formulas |
| `Result` + `Errors` | ✅ | — | Sealed type + 10 error codes |
| `AuditActions` | ✅ | — | 60+ action constants |
| `FeatureGate` | ⚠️ Dead code | 17 | Defined but not wired into `rbacGate` |

### 2.2 Domain layer (`domain/`)

| Module | Status | Notes |
|------|------|------|
| 20 domain models | ✅ | All `@Serializable`, snake_case DTOs |
| 20 repository interfaces | ✅ | All bound to Supabase implementations |

### 2.3 Infrastructure layer (`infrastructure/`)

| Module | Status | SyncSupport | Cache |
|------|------|:-:|:-:|
| `SupabaseClientProvider` | ✅ | — | — |
| `EncryptedSettingsStorage` | ✅ | — | — |
| `SupabaseAuthRepository` | ✅ | — | — |
| `SupabaseParentRepository` | ✅ | ✅ | ✅ |
| `SupabaseStudentRepository` | ✅ | ❌ | ❌ |
| `SupabasePaymentRepository` | ✅ | ✅ | ✅ |
| `SupabaseLedgerRepository` | ✅ | ✅ | ✅ |
| `SupabaseExpenseRepository` | ✅ | ❌ | ❌ |
| `SupabaseAuditRepository` | ✅ | ❌ (Hilt cycle) | ❌ |
| `SupabaseClassRepository` | ✅ | ❌ | ❌ |
| `SupabaseSubjectRepository` | ✅ | ❌ | ❌ |
| `SupabaseGradeRepository` | ✅ | ❌ | ❌ |
| `SupabaseAttendanceRepository` | ✅ | ✅ | ❌ (no cache DAO) |
| `SupabaseHomeworkRepository` | ✅ | ❌ | ❌ |
| `SupabasePersonnelRepository` | ✅ | ❌ | ❌ |
| `SupabaseDepartmentRepository` | ✅ | ❌ | ❌ |
| `SupabaseDashboardRepository` | ✅ | ❌ | ❌ |
| `SupabasePricingRepository` | ✅ | ❌ | ❌ |
| `SupabaseInstallmentRepository` | ✅ | ❌ | ❌ |
| `SupabaseDebtRepository` | ✅ | ❌ | ❌ |
| `SupabaseNotificationRepository` | ✅ | ❌ | ❌ |
| `SupabaseStorageRepository` | ✅ | — | — |
| `SupabaseSyncDao` | ✅ | — | — |
| Room (cache + sync queue) | ✅ | — | 4 cache DAOs + SyncQueueDao |
| Sync engine (OnlineDetector, SyncService, SyncWorker, SyncSupport) | ✅ | — | — |
| FCM messaging | ✅ | — | 4 channels |

### 2.4 UI layer (`ui/`)

| Hub | Screens | Design system | Status |
|------|------|:-:|------|
| `auth/` | 4 | ❌ | Legacy theme |
| `main/` | 1 | ❌ | Legacy theme |
| `dashboard/` | 11 | ✅ | Fully migrated |
| `crm/` | 6 | ❌ | Legacy theme |
| `academics/` | 9 | ❌ | Legacy theme |
| `financials/` | 9 | ❌ | Legacy theme |
| `personnel/` | 7 | ❌ | Legacy theme |
| `settings/` | 14 | ❌ | Legacy theme |

**Design-system migration: 11/47 screens (23%).**

---

## 3. Incomplete Modules

### 3.1 SyncSupport migration (5/17 done)

The following 11 repositories still need `tryThenEnqueue` + (where
applicable) `cacheThenNetwork` migration:

| Repository | Priority | Blocker |
|------|:-:|------|
| `SupabaseStudentRepository` | P0 | None — pattern is proven |
| `SupabaseExpenseRepository` | P0 | None |
| `SupabaseInstallmentRepository` | P1 | None |
| `SupabaseGradeRepository` | P1 | None |
| `SupabaseHomeworkRepository` | P1 | None |
| `SupabasePersonnelRepository` | P1 | None |
| `SupabaseClassRepository` | P1 | None |
| `SupabaseSubjectRepository` | P1 | None |
| `SupabaseDepartmentRepository` | P2 | None |
| `SupabasePricingRepository` | P2 | None |
| `SupabaseNotificationRepository` | P2 | None |
| `SupabaseDashboardRepository` | P2 | Read-only — no writes to enqueue |
| `SupabaseDebtRepository` | P2 | Read-only |
| `SupabaseAuditRepository` | — | Hilt cycle — see `decisions.md` D-07 |

### 3.2 Design-system migration (11/47 done)

36 screens still use legacy `ui.components.*` imports. The 6 highest-traffic
screens to migrate next:
1. `RollCallScreen`
2. `GradeEntryScreen`
3. `CounterPaymentScreen`
4. `InstallmentScheduleScreen`
5. `ParentsDirectoryScreen`
6. `StudentRosterScreen`

### 3.3 RBAC refactor

`RoutePermissions` uses `Map<KClass<out Route>, Permission>` (single
permission per route). Desktop uses `AccessRequirement` which supports
`RequiresPermission`, `RequiresAnyOf`, `RequiresAllOf`, `RequiresRole`,
`Permanent`, `empty`. `FeatureGate.evaluate` is defined but not wired.

---

## 4. Missing Business Logic

| Feature | Desktop reference | Mobile status | Priority |
|------|------|------|:-:|
| Pricing config UI | `features/settings/pricing-tab.tsx` | Repository exists, no UI | P1 |
| Account approval flow | `features/settings/approvals-tab.tsx` + `supabase-approval-repository.ts` | No mobile equivalent | P1 |
| Department management UI | `features/personnel/management/department-management.tsx` | Repository exists, no UI | P1 |
| Employee create/edit form | `features/personnel/management/employee-form-modal.tsx` | Read-only directory | P1 |
| Task management | `features/personnel/management/task-management.tsx` | No mobile equivalent | P1 |
| Chat | `features/personnel/management/chat-panel.tsx` | No mobile equivalent | P1 |
| Receipt PDF generation | `features/financials/receipts-tab.tsx` (pdf-lib) | Read-only receipt view, no PDF | P1 |
| School calendar | `features/dashboard/dashboard-calendar.tsx` | No mobile equivalent | P1 |
| Driver mode / routing | `features/routing/routing-page.tsx` | No mobile equivalent | P1 |
| Role-specific dashboards (8) | `features/personnel/dashboards/*-dashboard.tsx` | Single dashboard for all roles | P1 |
| Excel import | `features/crm/excel-import-modal.tsx` | Desktop-only per plan §14 | P2 |
| Workflow editor (DAG canvas) | `features/workflow/workflow-page.tsx` | Desktop-only per plan §11 | P2 |
| AI narrative / anomaly | `features/financials/anomaly-explainer-modal.tsx` | Desktop-only per plan §11 | P2 |
| RBAC matrix editor | `features/settings/rbac-matrix-editor.tsx` | Desktop-only | P2 |
| Backup | `features/settings/backup-tab.tsx` | Mobile PROHIBITED per plan §13.05 | — |

---

## 5. Broken Functionality

| # | Issue | Severity | Workaround | Fix |
|---|------|:-:|------|------|
| 1 | `DashboardViewModel` shows fake KPIs when Supabase is down | 🟠 High | None — operators see "1.245M DZD revenue" even during outages | Replace `defaultKpi` with `null` + `ElEmptyState` |
| 2 | `DashboardViewModel.attendanceTrend` hardcodes 6 of 7 days | 🟠 High | None — chart shows fake trend | Add `dashboardRepository.observeAttendanceTrend7Days()` |
| 3 | `Routes.Settings` not in `RoutePermissions` map | 🟠 High | None — any signed-in user can access Settings | Add to map with `RequiresAnyOf` |
| 4 | `Routes.DashboardHub` uses `VIEW_AUDIT_LOG` instead of role-set | 🟡 Medium | None — wrong roles can see dashboard | Refactor to `RequiresRole` |
| 5 | `SupabaseDashboardRepository.DashboardKpiDto` hardcodes `pendingExpenses = 0` + `attendanceRateToday = 0.0` | 🟡 Medium | None — dashboard shows 0 for these | Read actual MV columns |
| 6 | `SupabaseDashboardRepository.DebtAgingDto` hardcodes `parentPhone = ""` + `studentCount = 0` | 🟡 Medium | None — "Appeler" button is a no-op | Add parent join |
| 7 | `SupabaseClassRepository.ClassDto` hardcodes `level = ""`, `gradeYear = 0`, `enrolledCount = 0`, `academicYear = ""` | 🟡 Medium | None — every class looks identical | Add 3 joins |
| 8 | `SupabasePersonnelRepository.PersonnelDto` hardcodes `avatarUrl = null`, `weeklyHoursTarget = 0`, `weeklyHoursLogged = 0` | 🟡 Medium | None — Relevé shows 0% compliance for everyone | Add `vw_personnel_with_hours` view |
| 9 | `SupabaseInstallmentRepository.findOverdue` pulls 500 rows client-side | 🟡 Medium | None — will degrade as installments grow | Switch to `run_overdue_scan()` RPC |
| 10 | `SupabaseGradeRepository.fetchAggregatedForStudentSubject` returns wrong `subjectId` + `classId` | 🟡 Medium | None — Grade Entry shows UUID instead of subject name | Add `class_subjects` lookup |
| 11 | `SupabaseInstallmentRepository.InstallmentDto` hardcodes `category = PaymentCategory.OTHER` | 🟡 Low | None — every installment labeled "OTHER" | Read actual `category` column |
| 12 | `SupabaseHomeworkRepository.HomeworkInsertDto.subjectName = ""` | 🟡 Low | None — empty string silently dropped | Drop field or fetch subject name |
| 13 | `SupabaseSubjectRepository.observeByClass` does 2 queries instead of 1 | 🟡 Low | None — doubles latency | Use Postgrest embedded resource |
| 14 | 2 unit test failures (Robolectric+Hilt, GlobalScope timing) | 🟢 Low | None — test-only | Fix test setup |

---

## 6. Technical Debt

| Area | Debt | Effort to fix |
|------|------|------|
| Commit messages | 12 of 13 commits have message `"mid"` — git archaeology is very hard | Adopt conventional-commit format going forward |
| Design-system migration | 36 screens still use legacy `ui.components.*` | ~18 dev-hours (mechanical) |
| SyncSupport migration | 11 repositories still call Supabase directly | ~3 dev-days (mechanical) |
| RBAC | `FeatureGate.evaluate` is dead code; `RoutePermissions` only supports single `Permission` | ~1 dev-day to refactor |
| Hardcoded demo data | `DashboardViewModel` has `defaultKpi`, `defaultRevenue`, `defaultDebtAging`, `defaultNotifications`, `defaultAttendanceTrend` | ~2 hours to replace with `null` + empty states |
| Cache DAOs | Only 4 cache DAOs exist (Parent, Student, Payment, Ledger). 12 more needed for full `cacheThenNetwork` coverage | ~1 dev-day per DAO |
| `SupabaseSyncDao` | `pushAttendance` does single-row upsert — won't perfectly replay batch `RollCallPayload` | Enhance to extract `records` array + bulk-upsert |
| `SupabaseSyncDao` | No `pushAuditLog` method — audit_log drain-side replay not implemented | Add method (but audit logs aren't enqueued — see `decisions.md` D-07) |
| Tests | 0 UI tests beyond scaffolding; 0 tests for the new `Pricing.kt` formulas | ~3 dev-days |
| Deprecation warnings | 12 `AutoMirrored` icon warnings | ~1 hour |
| `.env` defaults to demo | App uses demo fallback when `.env` not configured — masks real auth failures in production | Gate demo fallback behind `BuildConfig.DEBUG` |

---

## 7. Known Bugs

See [`known-issues.md`](known-issues.md) for the full bug catalog with
severity, workaround, and fix link.

**Critical (0):** None remaining — all 5 critical bugs from iteration 3
start are fixed.

**High (3):**
- K-01: Dashboard shows fake KPIs during outages
- K-02: Settings route ungated
- K-03: DashboardHub RBAC uses wrong permission

**Medium (9):** K-04 through K-12 (see `known-issues.md`).

**Low (3):** K-13, K-14, K-15.

---

## 8. Risks

| Risk | Likelihood | Impact | Mitigation |
|------|:-:|:-:|------|
| Supabase backend credentials leak in APK | Low | 🔴 Critical | Only `anon` key shipped; `service_role` never in app. RLS enforces server-side. |
| Offline mutation lost if sync queue corrupted | Low | 🟠 High | Room WAL + `fallbackToDestructiveMigrationOnDowngrade`. Audit log surfaces permanent failures. |
| Hilt dependency cycle on future SyncSupport migrations | Medium | 🟡 Medium | Check for cycles before adding `SyncSupport` to any repo that `SyncService` depends on (Audit, Auth, Session). |
| Design-system migration introduces regressions | Medium | 🟡 Medium | Migrate one screen at a time; run unit tests after each. |
| `compileSdk = 35` blocks future Android 16 features | Low | 🟡 Low | Bump AGP to 8.9.1+ and restore `compileSdk = 36` when ready. |
| 2 un-deployed RPCs (`mark_installment_paid`, `regenerate_installments`) | High | 🟡 Medium | Mobile calls will fail with `PGRST202` until desktop team deploys them. |
| 2 un-deployed Edge Functions (`alert-absences`, `send-debt-reminder`) | High | 🟡 Low | Mobile calls will fail silently; features degrade gracefully. |

---

## 9. Blockers

| Blocker | Status | Owner | Notes |
|------|------|------|------|
| None | — | — | The project compiles, builds APK, and passes 98/100 tests. No hard blockers. |

The 2 un-deployed RPCs + 2 un-deployed Edge Functions are **soft
blockers** — the mobile code calls them and will get `PGRST202` /
function-not-found errors, but the calling screens surface `Result.Err`
gracefully. They don't block the build or the app launch.

---

## 10. What's Next

See [`next-steps.md`](next-steps.md) for the prioritized iteration-4
backlog.

**Top 5 by impact:**
1. Refactor `RoutePermissions` to use `AccessRequirement` + wire `FeatureGate.evaluate`
2. Remove hardcoded demo data from `DashboardViewModel`
3. Migrate 6 high-traffic screens to the new design system
4. Migrate remaining 11 Supabase repositories to `SyncSupport`
5. Add Account Approval flow (mobile UI + bind approval repository)

---

See also: [`known-issues.md`](known-issues.md) for the bug catalog and
[`next-steps.md`](next-steps.md) for the roadmap.
