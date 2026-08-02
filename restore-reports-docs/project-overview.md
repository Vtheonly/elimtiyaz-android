# Project Overview

> **Audience:** Anyone new to the repository.
> **Read time:** ~5 minutes.

This document explains **why** the El-Imtiyaz Android project exists,
**what** problem it solves, and the **two-repository** setup that
governs the restoration effort.

---

## 1. What Is El-Imtiyaz?

El-Imtiyaz (Arabic: التمياز, "the distinction") is a **private school
management platform** built for an Algerian private school. The platform
manages:

- **CRM** — parents, students, family relationships, registration workflow.
- **Financials** — payments, installments, debt tracking, expense
  approval, receipts, ledger entries, financial dashboards.
- **Academics** — attendance (roll call), grades, homework, class
  management, subject averages, GPA computation.
- **Personnel** — staff directory, departments, activity ledger
  (relevé), audit stream.
- **Operations** — dashboards, role-based KPIs, RBAC, audit logging.

The platform serves **11 user roles** (SuperAdmin, FinancialOfficer,
Teacher, SupportStaff, Manager, Buyer, Driver, WarehouseWorker, Worker,
Parent, Student) with **56 atomic permissions** grouped by domain.

---

## 2. The Two-Repository Setup

The platform is split across two repositories:

| Repository | Stack | Role | URL |
|---|---|---|---|
| **Desktop** | Electron + React + TypeScript + Supabase (PostgreSQL) | **Source of truth** — complete business logic, financial engine, accounting rules, workflows, database schema, Edge Functions, Excel import engine, RBAC matrix, 1,180 passing tests. | https://github.com/Vtheonly/AgentGithubUplaod |
| **Mobile** | Android (Kotlin + Jetpack Compose + Hilt + Room + Supabase Kotlin SDK) | **Field companion** — offline-first Android app for teachers, financial officers, and managers on the go. | https://github.com/Vtheonly/elimtiyaz-android |

### Why two repos?

The desktop is the **master implementation** — it was built first over
16 iterations and contains the canonical business logic, database
schema (24 SQL migrations), and 11 Edge Functions. The mobile app is a
**downstream consumer** of the same Supabase backend; it mirrors the
desktop's business logic in Kotlin and adds mobile-specific concerns
(camera capture, FCM push, GPS, Room offline cache, WorkManager sync
queue).

### The "source of truth" rule

> **Whenever the mobile repo is incomplete or ambiguous, the desktop
> implementation is the authoritative reference.**

This rule governs every restoration decision. See
[`decisions.md`](decisions.md) § D-01 for the full rationale.

---

## 3. The Problem This Project Solves

### 3.1 The original mobile app (pre-wipe)

The mobile repo was originally developed over **9 commits** (2026-07-25
to 2026-08-01, commits `e9aa7a3` → `782bde1`). At its peak (`782bde1`
"aight mid"), it contained:

- 311 Kotlin source files
- Clean Architecture (UI → Domain → Infrastructure → DI/Hilt)
- A **ledger-based financial engine** mirroring the desktop's
  `computeAccountBalance` / `computeParentSummary` replay logic
- 8 working Supabase-backed features (Auth, Parent CRUD, Student CRUD,
  Payment collect/refund, Ledger append/reverse, Expense 4-RPC workflow,
  Audit log, Storage upload)
- 49 unit tests covering the core engine (FeatureGate, LedgerEngine,
  PiiMask, Reconcile)
- Wire-protocol parity with desktop (~60 AuditActions, 11 Role codes,
  56 Permission codes)

### 3.2 The destructive wipe (commit `933c139` "fk", 2026-08-01 14:49)

A single commit — message `"fk"` — **wiped everything** except:

- `README.md`
- `app/build.gradle.kts`
- `gradle.properties`
- the 76-file `app/src/main/java/com/example/ui/designsystem/` tree
  (a brand-new design system that had just been added)
- a stray `GreetingScreenshotTest.kt` stub

**199 files changed, 13,888 deletions, 6,071 insertions.** The entire
business logic, data layer, ViewModels, repositories, navigation, and
legacy UI components were deleted. Only the new (incomplete) design
system survived.

### 3.3 The UI-redesign refactor (commit `82990e1` "mid", 2026-08-01 21:53)

After iterations 1 and 2 restored the file tree and bound every
repository interface, a **third refactor** attempted to split large
monolithic files into smaller single-responsibility files. This
introduced **23 truncated files** — each ending with an orphan
`@Composable` or `@HiltViewModel` annotation with no function body —
which broke the build entirely.

### 3.4 The restoration effort

Three iterations of restoration work (2026-08-01 to 2026-08-02) have
rebuilt the mobile app from the wreckage:

| Iteration | Commit | What it did |
|---|---|---|
| **1** | `1948741` "sub" | Restored the file tree from `782bde1`, bound 12 Supabase repository implementations, fixed OnlineDetector/SyncWorker/RBAC/SettingsScreen. |
| **2** | `d52aa6b` "mid cv" | Fixed 13 defect groups: session restore, cache-then-network reads, try-then-enqueue writes, 7 broken screens driven by real ViewModels, compileSdk 35 fix, 5 new unit tests. |
| **3** | (uncommitted, on top of `82990e1`) | Repaired 23 truncated files, fixed Supabase SDK 3.1.1 `SettingsStorage` API mismatch, removed fabricated SUPER_ADMIN session, fixed wrong sync tables for grades + homework, added 7 financial formulas, migrated 3 P0 repositories to SyncSupport. |

See [`iteration-history.md`](iteration-history.md) for the full
engineering journal.

---

## 4. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                       Supabase Backend                          │
│  (shared with desktop — 24 migrations, 11 Edge Functions, RLS)  │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTPS (Postgrest + Auth + Realtime + Storage + Functions)
                             │
        ┌────────────────────┴────────────────────┐
        │              Mobile App (Kotlin)        │
        │                                         │
        │  ┌──────────────────────────────────┐   │
        │  │  UI Layer (Jetpack Compose)      │   │
        │  │  - design system (76 files)      │   │
        │  │  - legacy components (22 files)  │   │
        │  │  - 7 feature hubs                │   │
        │  └──────────────┬───────────────────┘   │
        │                 │ ViewModels (Hilt)      │
        │  ┌──────────────┴───────────────────┐   │
        │  │  Domain Layer                    │   │
        │  │  - 20 repository interfaces      │   │
        │  │  - 20 domain models              │   │
        │  │  - core/ (LedgerEngine, Rbac,    │   │
        │  │           Pricing, Reconcile,    │   │
        │  │           PiiMask, Result)       │   │
        │  └──────────────┬───────────────────┘   │
        │                 │                        │
        │  ┌──────────────┴───────────────────┐   │
        │  │  Infrastructure Layer            │   │
        │  │  - 20 Supabase repositories      │   │
        │  │  - Room (cache + sync queue)     │   │
        │  │  - Sync (SyncService + Worker)   │   │
        │  │  - FCM (push notifications)      │   │
        │  └──────────────┬───────────────────┘   │
        │                 │                        │
        │  ┌──────────────┴───────────────────┐   │
        │  │  DI Layer (Hilt)                 │   │
        │  │  - SupabaseModule                │   │
        │  │  - DatabaseModule                │   │
        │  │  - RepositoryModule (20 @Binds)  │   │
        │  └──────────────────────────────────┘   │
        └─────────────────────────────────────────┘
```

See [`architecture.md`](architecture.md) for the full breakdown.

---

## 5. Current State (2026-08-02)

- ✅ **Build:** `./gradlew :app:assembleDebug` produces a 28 MB APK
- ✅ **Compile:** 312 Kotlin source files compile cleanly
- ✅ **Tests:** 98/100 unit tests pass
- ✅ **Modern UI:** 76 design-system files preserved, dashboard fully
  migrated to new design system
- 🟡 **Business logic:** Financial engine parity with desktop verified
  for 14 formulas; 3 P0 repositories migrated to offline-first
  SyncSupport patterns
- ⚠️ **Remaining gaps:** 11 repositories still need SyncSupport migration;
  36 screens still use legacy `ui.components.*` imports; RBAC needs
  refactor to support `RequiresAnyOf` / `RequiresRole`

See [`current-status.md`](current-status.md) for the full status board.

---

## 6. Who Should Read What

| Role | Read these docs |
|------|-----------------|
| **New developer joining the project** | `project-overview.md` → `architecture.md` → `iteration-history.md` → `current-status.md` |
| **Developer fixing a bug** | `known-issues.md` → `work-log.md` (find the relevant iteration) → the file's KDoc |
| **Developer planning the next iteration** | `next-steps.md` → `restoration-plan.md` → `decisions.md` |
| **Developer investigating a regression** | `commit-history-analysis.md` → `iteration-history.md` |
| **QA / tester** | `current-status.md` → `known-issues.md` |
| **Project manager** | `project-overview.md` → `current-status.md` → `next-steps.md` |

---

See also: [`architecture.md`](architecture.md) for the detailed folder
structure and module responsibilities.
