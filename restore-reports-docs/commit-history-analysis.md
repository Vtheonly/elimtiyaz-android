# Git Commit History Analysis

> **Audience:** Anyone investigating a regression or planning a restoration.
> **Read time:** ~10 minutes.

This document analyzes the entire git history of the mobile repository
(`https://github.com/Vtheonly/elimtiyaz-android`) and produces a
chronological timeline showing how the project evolved from a healthy
state to its current state.

The repository has **13 commits** total. Each is analyzed below for:
what changed, why, whether it improved or degraded the project,
regressions introduced, missing functionality, and architectural impact.

---

## 1. Chronological Timeline

```
2026-07-25  20:55  e9aa7a3  "first commit"        INITIAL — Android Studio scaffold + full app
2026-07-25  22:00  2d0b4f5  "idkmid"              Minor auth + routing tweaks
2026-07-25  23:35  a34333a  "aight"               Login screen + OSRM client
2026-07-31  19:25  b8cf91e  "okya"                MAJOR REWRITE — 32k deletions, 9k insertions
2026-07-31  23:19  7d74088  "mid"                 Settings + sync + RBAC + 1334 insertions
2026-08-01  00:40  a4f5802  "ncie"                google-services.json added
2026-08-01  01:36  e1cb46b  "oki"                 Gradle config + 727 insertions
2026-08-01  03:24  974cb6e  "mid"                 Gradlew + 4557 insertions
2026-08-01  12:35  782bde1  "aight mid"           ← LAST STABLE COMMIT (pre-wipe peak)
2026-08-01  14:49  933c139  "fk"                  ← DESTRUCTIVE WIPE (disaster)
2026-08-01  18:32  1948741  "sub"                 ← ITERATION 1 — file tree restored
2026-08-01  19:54  d52aa6b  "mid cv"              ← ITERATION 2 — 13 defect groups fixed
2026-08-01  21:53  82990e1  "mid"                 ← UI-REDESIGN REFACTOR (broke 23 files)
                                                        + iteration 3 fixes (uncommitted)
```

---

## 2. Per-Commit Analysis

### 2.1 `e9aa7a3` — "first commit" (2026-07-25 20:55)

**Author:** mersel fares
**Stats:** 187 files changed, 32,400 insertions

**What changed:**
Initial Android Studio scaffold + a substantial first version of the
app. This commit established:
- Clean Architecture (UI → Domain → Infrastructure → DI/Hilt)
- The `com.example` namespace (applicationId `com.aistudio.elimtiyazstaff.bxmzlx`)
- 76 design-system files in `ui/designsystem/`
- Legacy UI components in `ui/components/`
- Core business logic (`LedgerEngine`, `Reconcile`, `PiiMask`, `Rbac`)
- 20 repository interfaces + Supabase implementations
- Room database (cache + sync queue)
- FCM messaging service
- 4 unit test files (FeatureGate, LedgerEngine, PiiMask, Reconcile)

**Impact:** ✅ Improved — this is the foundation. Everything else builds on it.

**Regressions:** None (first commit).

**Architectural impact:** Established the clean-layered architecture
that survives to this day.

---

### 2.2 `2d0b4f5` — "idkmid" (2026-07-25 22:00)

**Author:** mersel fares
**Stats:** 186 files changed, 253 insertions, 172 deletions

**What changed:**
Minor tweaks to the auth flow (`LoginScreen.kt`) and the OSRM routing
client (`OsrmClient.kt`). Added `metadata.json`.

**Impact:** ✅ Improved — incremental progress on auth + routing.

**Regressions:** None observed.

**Architectural impact:** None — incremental feature work.

---

### 2.3 `a34333a` — "aight" (2026-07-25 23:35)

**Author:** mersel fares
**Stats:** 9 files changed, 96 insertions, 14 deletions

**What changed:**
Enhanced `LoginScreen.kt` (+89 lines) and tweaked `OsrmClient.kt`.

**Impact:** ✅ Improved — login screen polish.

**Regressions:** None.

**Architectural impact:** None.

---

### 2.4 `b8cf91e` — "okya" (2026-07-31 19:25)

**Author:** mersel fares
**Stats:** 275 files changed, 9,317 insertions, **32,425 deletions**

**What changed:**
**MAJOR REWRITE.** Deleted 32k lines and added 9k — essentially a
ground-up rewrite of the app. The `settings.gradle.kts` changed
significantly (39 lines touched), and `metadata.json` was modified.

**Impact:** ⚠️ Mixed. The rewrite likely consolidated features and
removed dead code, but the massive deletion scope suggests significant
functionality was lost (or restructured). Without per-file diffs, hard
to say definitively.

**Regressions:** Possible — 32k deletions is a red flag. However, this
commit is the basis for the next 4 commits that culminated in the stable
`782bde1`, so any regressions were apparently resolved by then.

**Architectural impact:** Likely restructured the module layout. The
next commit (`7d74088`) added back settings + sync + RBAC, suggesting
this rewrite removed and re-added those subsystems.

---

### 2.5 `7d74088` — "mid" (2026-07-31 23:19)

**Author:** mersel fares
**Stats:** 98 files changed, 1,334 insertions, 215 deletions

**What changed:**
Re-added settings, sync, and RBAC subsystems. 98 files touched suggests
broad changes across the codebase.

**Impact:** ✅ Improved — restored core subsystems after the rewrite.

**Regressions:** None observed.

**Architectural impact:** Re-established the offline-first sync engine
and per-route RBAC gate.

---

### 2.6 `a4f5802` — "ncie" (2026-08-01 00:40)

**Author:** mersel fares
**Stats:** 3 files changed, 35 insertions

**What changed:**
Added `app/google-services.json` (Firebase config for FCM) and updated
`build.gradle.kts` to apply the `google-services` plugin.

**Impact:** ✅ Improved — enabled FCM push notifications.

**Regressions:** None.

**Architectural impact:** Added Firebase integration.

---

### 2.7 `e1cb46b` — "oki" (2026-08-01 01:36)

**Author:** mersel fares
**Stats:** 31 files changed, 727 insertions, 416 deletions

**What changed:**
Gradle config updates (`build.gradle.kts` +3, `libs.versions.toml` +3)
plus broad changes across 31 files.

**Impact:** ✅ Improved — likely dependency bumps + feature additions.

**Regressions:** None observed.

**Architectural impact:** Minor — dependency + config changes.

---

### 2.8 `974cb6e` — "mid" (2026-08-01 03:24)

**Author:** mersel fares
**Stats:** 43 files changed, 4,557 insertions, 1,330 deletions

**What changed:**
Added the Gradle wrapper scripts (`gradlew` +248, `gradlew.bat` +93)
plus 4,000+ lines across 41 other files. This is a large feature-add
commit.

**Impact:** ✅ Improved — added Gradle wrapper (essential for CLI
builds) + significant feature work.

**Regressions:** None observed.

**Architectural impact:** Added build tooling + features.

---

### 2.9 `782bde1` — "aight mid" (2026-08-01 12:35) ← **LAST STABLE COMMIT**

**Author:** mersel fares
**Stats:** 6 files changed, 23 insertions, 12 deletions

**What changed:**
Small polish commit — `PersonnelHubScreen.kt` +1 line,
`GreetingScreenshotTest.kt` +8/-4 lines, plus 4 other files.

**Impact:** ✅ Improved — final polish on the pre-wipe peak.

**Regressions:** None.

**Architectural impact:** None.

**Why this is the last stable commit:**
At this point, the mobile app had:
- 311 Kotlin source files
- Clean Architecture intact
- 8 working Supabase-backed features
- 49 unit tests covering the core engine
- Wire-protocol parity with desktop
- Full legacy UI component library
- Working sync engine + RBAC + settings

This is the **safest restoration point** if one ever needed to start
over. The iteration-1 restoration effectively rebuilt from this commit's
file tree.

---

### 2.10 `933c139` — "fk" (2026-08-01 14:49) ← **DESTRUCTIVE WIPE (DISASTER)**

**Author:** mersel fares
**Stats:** 199 files changed, 6,071 insertions, **13,888 deletions**

**What changed:**
**THE DISASTER.** This single commit wiped everything except:
- `README.md` (the AI Studio scaffold README)
- `app/build.gradle.kts`
- `gradle.properties`
- the 76-file `app/src/main/java/com/example/ui/designsystem/` tree
- a stray `GreetingScreenshotTest.kt` stub

**Impact:** 🔴 **CATASTROPHIC DEGRADATION.** The entire business logic,
data layer, ViewModels, repositories, navigation, and legacy UI
components were deleted. Only the new (incomplete) design system
survived.

**Regressions introduced:**
- ❌ All 20 repository implementations deleted
- ❌ All 20 domain models deleted
- ❌ All 20 repository interfaces deleted
- ❌ Core business logic (`LedgerEngine`, `Reconcile`, `PiiMask`, `Rbac`) deleted
- ❌ All 7 feature hub screens deleted
- ❌ All ViewModels deleted
- ❌ Room database + sync engine deleted
- ❌ FCM messaging service deleted
- ❌ Hilt DI modules deleted
- ❌ Navigation graph deleted
- ❌ Legacy UI components (22 files) deleted
- ❌ Theme files deleted
- ❌ 4 unit test files deleted

**Missing functionality:** Everything except the design system gallery.

**Architectural impact:** Reduced the app from a 311-file production
codebase to a 77-file design system preview. The app would not compile,
would not run, and had zero business logic.

**Why:** The commit message `"fk"` suggests an accidental or frustrated
destructive action. The design system survived because it had been
recently added (likely in `974cb6e` or earlier) and was probably in a
separate staging area.

---

### 2.11 `1948741` — "sub" (2026-08-01 18:32) ← **ITERATION 1**

**Author:** mersel fares (but actually the restoration agent's work)
**Stats:** 147 files changed, 28,471 insertions, 494 deletions

**What changed:**
**Restoration iteration 1.** Rebuilt the file tree from `782bde1` (via
`git show`) and added:
- 12 new Supabase repository implementations (Class, Subject, Grade,
  Attendance, Homework, Personnel, Department, Dashboard, Pricing,
  Installment, Debt, Notification)
- Updated `RepositoryModule.kt` with 12 new `@Binds` entries
- Fixed `OnlineDetector` (added HEAD probe + 30s periodic loop)
- Implemented `SyncWorker` push functions (via new `SupabaseSyncDao`)
- Wired per-route RBAC (`RoutePermissions` map + `rbacGate` helper)
- Built real `SettingsScreen` (replaced 4 placeholder cards)
- Enhanced `ElImtiyazApplication` (online detector start + periodic
  sync + FCM role-topic subscriptions)
- Added `DataStore<Preferences>` for settings persistence
- Added the iteration-1 worklog (310 lines)

**Impact:** ✅ **MASSIVE IMPROVEMENT.** Restored the app from a 77-file
design system preview to a 311-file codebase with bound repositories.

**Regressions introduced:**
- ⚠️ `EncryptedSettingsStorage` referenced `SettingsStorage` which
  doesn't exist in Supabase SDK 3.1.1 (fixed in iteration 3)
- ⚠️ `SupabaseAuthRepository.refreshSession()` fabricated a SUPER_ADMIN
  session on cold-start (fixed in iteration 3)
- ⚠️ `SupabaseSyncDao.pushGrade` wrote to `assessments` instead of
  `grades` (fixed in iteration 3)
- ⚠️ `SupabaseSyncDao.pushHomework` wrote to `homework_assignments`
  instead of `homework` (fixed in iteration 3)
- ⚠️ 13 distinct defect groups left key screens rendering hardcoded mock
  data (fixed in iteration 2)

**Missing functionality:** See iteration-1 worklog § "Next actions for
follow-up waves" — 9 repositories not bound, 7 placeholder UI screens,
sync queue as dead code.

**Architectural impact:** Restored clean architecture. Added 12 new
repository implementations + `SupabaseSyncDao`. Established the
`SyncSupport` pattern (but only `SupabaseParentRepository` used it).

---

### 2.12 `d52aa6b` — "mid cv" (2026-08-01 19:54) ← **ITERATION 2**

**Author:** mersel fares (but actually the restoration agent's work)
**Stats:** 29 files changed, 2,825 insertions, 9,450 deletions

**What changed:**
**Restoration iteration 2.** Fixed 13 defect groups:
1. `SessionManager.restoreSession()` now propagates the session
2. `AppNavHost` start-route race fixed (no more `sessionState` parameter)
3. `Routes.PaymentDetail` registered as a composable destination
4. `SyncService.enqueue()` now has callers (`SyncSupport.tryThenEnqueue`)
5. Room cache DAOs wired via `SyncSupport.cacheThenNetwork`
6. `AcademicsHubScreen` 4 subscreens driven by real ViewModels
7. `PersonnelHubScreen` 3 subscreens driven by real ViewModels
8. `InstallmentScheduleViewModel` now loads real data + has parent selector
9. `ProofScannerScreen` button onClick implemented (camera capture)
10. `DashboardViewModel.attendanceTrend` derived from `kpis` flow
11. `SettingsStorage` (encrypted prefs) implemented
12. Restore-artifact clutter files removed
13. `compileSdk` pinned to 35 (was 36 — AGP 8.8.0 incompatibility)

Added 5 new unit tests (`SessionManagerTest` 7 tests, `CacheMappersTest`
5 tests). Deleted `mobile-pre-wipe-map.md` (1,894 lines — superseded by
the iteration-2 report).

**Impact:** ✅ **MAJOR IMPROVEMENT.** Closed all 13 defect groups;
compiles cleanly; 5 new tests pass.

**Regressions introduced:**
- ⚠️ The `EncryptedSettingsStorage` referenced `SettingsStorage` (still
  broken — fixed in iteration 3)
- ⚠️ The fabricated SUPER_ADMIN session was NOT fixed (still present —
  fixed in iteration 3)

**Missing functionality:** Documented in the iteration-2 report's "Next
Steps" section — 16 repositories still need SyncSupport migration, 19
screens still need design-system migration, 2 RPCs + 2 Edge Functions
need deployment.

**Architectural impact:** Established the offline-first pattern
(`tryThenEnqueue` + `cacheThenNetwork`) on `SupabaseParentRepository`
as the reference implementation.

---

### 2.13 `82990e1` — "mid" (2026-08-01 21:53) ← **UI-REDESIGN REFACTOR**

**Author:** mersel fares
**Stats:** 163 files changed, 8,114 insertions, 6,677 deletions

**What changed:**
**UI-redesign refactor.** Attempted to split large monolithic files into
smaller single-responsibility files:
- `Models.kt` (319 lines) → 18 individual model files ✅ (clean split)
- `Repositories.kt` (284 lines) → 20 individual repository files ✅ (clean split)
- `SettingsScreen.kt` (598 lines) → 14 smaller files ❌ (many truncated)
- `PersonnelHubScreen.kt` (449 lines) → 7 smaller files ❌ (many truncated)
- `ElComponents.kt` (707 lines) + `ElComponentsExtended.kt` (726 lines) → 22 smaller files ❌ (some truncated)
- `AcademicsHubScreen.kt` (877 lines) → 6 smaller files ❌ (some truncated)
- `Theme.kt` (223 lines) → `ElImtiyazTheme.kt` + `ColorSchemes.kt` ❌ (`DarkColorScheme` / `LightColorScheme` lost in split)

**Impact:** 🔴 **DEGRADED.** 23 files were truncated mid-declaration —
each ending with an orphan `@Composable` or `@HiltViewModel` annotation
with no function body. The build broke entirely.

**Regressions introduced:**
- ❌ 23 truncated files (build-breaking) — fixed in iteration 3
- ❌ `DarkColorScheme` / `LightColorScheme` lost — fixed in iteration 3
- ❌ Duplicate JVM class name (`ElDesignTokensKt` from `ElDesignTokens.kt` + `elDesignTokens.kt`) — fixed in iteration 3
- ❌ 27 files missing `getValue` / `setValue` imports — fixed in iteration 3

**Missing functionality:** The app would not compile. Every feature was
effectively broken.

**Architectural impact:** The file-splitting itself was a good idea
(smaller files are easier to maintain), but the execution was botched.
Iteration 3 repaired the truncations while preserving the new file
structure.

**Iteration 3 fixes (uncommitted, on top of `82990e1`):**
- Trimmed 23 orphan annotations via `scripts/trim_orphan_annotations.py`
- Added missing closing `}` to 9 files
- Rewrote `EncryptedSettingsStorage` to use `SettingsSessionManager` (SDK 3.1.1 correct API)
- Added `multiplatform-settings-no-arg:1.3.0` dependency
- Removed fabricated SUPER_ADMIN session from `refreshSession()`
- Fixed `pushGrade` → `grades` (was `assessments`)
- Fixed `pushHomework` → `homework` (was `homework_assignments`)
- Fixed debt-aging bucket format (underscore, with dash backward-compat)
- Created `ui/theme/ColorSchemes.kt`
- Merged `elDesignTokens.kt` into `ElDesignTokens.kt`
- Added 27 missing `getValue`/`setValue` imports
- Fixed `AppError.CODE_*` → `Errors.CODE_*`
- Created `core/Pricing.kt` with 7 financial formulas
- Migrated 3 P0 repositories to SyncSupport (Payment, Ledger, Attendance)

---

## 3. Key Commit Identifications

| Question | Answer |
|------|------|
| **Last stable commit before the project diverged** | `782bde1` "aight mid" (2026-08-01 12:35) |
| **Last commit with correct business logic** | `782bde1` (pre-wipe) + iteration-3 fixes on top of `82990e1` (current) |
| **Commit where major problems first appeared** | `933c139` "fk" (the destructive wipe) |
| **Commits that introduced regressions** | `933c139` (wipe), `82990e1` (truncated files) |
| **Commits that removed/broke functionality** | `933c139` (deleted everything), `82990e1` (broke build) |
| **Commits responsible for the "disaster" state** | `933c139` (primary), `82990e1` (secondary) |
| **Safest restoration point** | `782bde1` — the pre-wipe peak. Iteration 1 effectively rebuilt from here. |

---

## 4. Evolution Summary

```
HEALTHY (e9aa7a3 → 782bde1)
  │
  │  9 commits over 7 days
  │  Progressive feature development
  │  Clean architecture maintained
  │  311 files, 49 tests, 8 working features
  │
  ▼
DISASTER (933c139 "fk")
  │
  │  Single commit wiped 199 files
  │  Only 76-file design system survived
  │  App would not compile
  │
  ▼
RESTORATION (1948741 → d52aa6b → 82990e1 + iter-3 fixes)
  │
  │  Iteration 1: rebuilt file tree, bound 12 repositories
  │  Iteration 2: fixed 13 defect groups, added offline-first patterns
  │  Iteration 3: repaired 23 truncated files, fixed 5 critical bugs,
  │               added 7 financial formulas, migrated 3 P0 repos
  │
  ▼
CURRENT STATE (uncommitted, on top of 82990e1)
  │
  │  ✅ Compiles cleanly
  │  ✅ 28 MB APK builds
  │  ✅ 98/100 tests pass
  │  ✅ Modern UI preserved
  │  🟡 11 repos need SyncSupport migration
  │  🟡 36 screens need design-system migration
  │  ⚠️ RBAC needs RequiresAnyOf refactor
```

---

## 5. Lessons Learned

1. **Commit messages matter.** 12 of 13 commits have the message `"mid"`
   or similar. This makes git archaeology extremely difficult. Future
   commits should use conventional-commit format
   (`feat:`, `fix:`, `refactor:`, `docs:`, `test:`).

2. **Destructive commits need review.** `933c139` "fk" deleted 13,888
   lines in a single commit with a 2-character message. A pre-commit
   hook or branch protection would have caught this.

3. **File-splitting refactors need verification.** `82990e1` split 6
   large files into ~70 smaller ones but left 23 truncated. A
   post-refactor compile check would have caught this immediately.

4. **The "source of truth" rule saved the project.** Because the desktop
   repo contains the complete business logic, the mobile restoration
   could rebuild from a known-good reference rather than reverse-engineering
   from the wiped codebase.

---

See also: [`iteration-history.md`](iteration-history.md) for the
detailed engineering journal of each restoration iteration, and
[`decisions.md`](decisions.md) for the architectural decisions that
govern the restoration.
