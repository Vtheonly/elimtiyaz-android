# Architectural & Restoration Decisions

> **Audience:** Anyone who wants to understand *why* the codebase is the
> way it is.
> **Last updated:** 2026-08-02 (end of iteration 3).

This document records every significant architectural and restoration
decision, with rationale. Each decision has an ID (D-XX), status, and
links to related decisions.

---

## D-01 — Desktop is the source of truth

**Status:** ✅ Accepted (iteration 0)
**Supersedes:** —
**Related:** D-02, D-03, D-06

### Context
The mobile repo was wiped (commit `933c139`). The desktop repo contains
the complete business logic, database schema, and 1,180 passing tests.

### Decision
Whenever the mobile repo is incomplete or ambiguous, the desktop
implementation at `https://github.com/Vtheonly/AgentGithubUplaod` is
the authoritative reference.

### Rationale
- The desktop was built first (16 iterations) and is battle-tested.
- The desktop and mobile share the same Supabase backend, so wire
  formats must match exactly.
- Without the desktop reference, the mobile restoration would have been
  impossible (the wiped code was the only other reference).

### Consequences
- Mobile code must mirror desktop's wire-protocol strings (AuditActions,
  Role codes, Permission codes, LedgerEntryType, etc.).
- Renaming any value requires a Supabase migration.
- Mobile can deviate only when a feature is "impossible or inappropriate
  on Android" (per the conflict-resolution policy).

---

## D-02 — Clean Architecture (UI → Domain → Infrastructure → DI)

**Status:** ✅ Accepted (pre-wipe, preserved)
**Supersedes:** —
**Related:** D-01

### Context
The pre-wipe mobile code followed clean architecture. The restoration
needed to decide whether to preserve it or adopt a simpler pattern.

### Decision
Preserve the 4-layer clean architecture:
- **UI** (Compose + ViewModels) → **Domain** (interfaces + models) →
  **Infrastructure** (Supabase + Room + Sync) → **DI** (Hilt modules).

### Rationale
- The desktop uses the same layering (features → domain → infrastructure → app).
- Clean architecture makes the offline-first patterns (cache, sync) easier
  to insert at the infrastructure layer without polluting the UI.
- Hilt's `@Binds` makes the interface → implementation mapping trivial.

### Consequences
- Every new repository requires: interface in `domain/repository/`,
  implementation in `infrastructure/supabase/`, `@Binds` in
  `di/RepositoryModule.kt`.
- UI never touches Supabase directly — only via the ViewModel →
  repository interface.

---

## D-03 — Signed-amount ledger convention

**Status:** ✅ Accepted (pre-wipe, preserved)
**Supersedes:** —
**Related:** D-01

### Context
The financial engine needs a convention for representing charges vs.
payments in the ledger.

### Decision
Use signed amounts (Long centimes):
- `+amount` = charge (debit)
- `-amount` = payment (credit)
- `±amount` = adjustment
- `-amount` = refund
- `-original.amount` = reversal of an existing entry

### Rationale
- Mirrors the desktop's `LedgerEntry` convention exactly.
- Single `sumOf { it.amount }` computes the balance — no conditional logic.
- Long centimes (not Double) avoids floating-point rounding.

### Consequences
- Every `LedgerEntryFactory.createXxx()` function applies the correct sign.
- The `computeAccountBalance()` replay function is trivial.
- Reversed entries are excluded from typed totals but counted in `entryCount`.

---

## D-04 — `compileSdk = 35` (not 36)

**Status:** ✅ Accepted (iteration 2)
**Supersedes:** —
**Related:** —

### Context
AGP 8.8.0 officially supports `compileSdk = 35`. The pre-wipe code used
`compileSdk = 36`, which emits a warning ("compileSdk 36 is higher than
the maximum supported 35") that could become a hard error under strict
mode.

### Decision
Pin `compileSdk = 35` + `targetSdk = 35`. Add a comment explaining the
constraint and how to restore 36 (bump `agp` to `8.9.1+`).

### Rationale
- AGP 8.8.0 is the version in `libs.versions.toml`; bumping it would
  risk other incompatibilities.
- API 35 is sufficient for all current features.

### Consequences
- Cannot use Android 16 (API 36) specific APIs.
- To target API 36 in the future, bump `agp` to `8.9.1+` and restore
  `compileSdk = 36`.

---

## D-05 — `SettingsSessionManager` for JWT persistence (not `SettingsStorage`)

**Status:** ✅ Accepted (iteration 3)
**Supersedes:** The iteration-2 attempt to use `SettingsStorage`
**Related:** —

### Context
Iteration 2's `EncryptedSettingsStorage` referenced
`io.github.jan.supabase.auth.settings.SettingsStorage`, which does NOT
exist in Supabase Kotlin SDK 3.1.1. The class was renamed to
`SettingsSessionManager` (in package `io.github.jan.supabase.auth`) and
now wraps `com.russhwolf.settings.Settings` from the
`multiplatform-settings-no-arg` library.

### Decision
- Add `multiplatform-settings-no-arg:1.3.0` +
  `multiplatform-settings-coroutines:1.3.0` dependencies.
- Rewrite `EncryptedSettingsStorage` as an `object` with
  `createSessionManager(context)` returning a `SettingsSessionManager`
  backed by EncryptedSharedPreferences via `SharedPreferencesSettings`.
- Pass the `SettingsSessionManager` to
  `install(Auth) { sessionManager = ... }` in `SupabaseClientProvider`.

### Rationale
- This is the correct API for Supabase SDK 3.x.
- The `multiplatform-settings` library is already a transitive
  dependency of `auth-kt` (per its POM), so adding it explicitly just
  makes the dependency direct.
- EncryptedSharedPreferences provides AES-256-GCM encryption at rest.

### Consequences
- JWT refresh tokens persist across app cold-starts.
- Users stay signed in.
- `EncryptedSettingsStorage.clear(context)` wipes the session on signOut.

---

## D-06 — Never fabricate a session

**Status:** ✅ Accepted (iteration 3)
**Supersedes:** The iteration-1 "demo fallback" branch in `refreshSession()`
**Related:** D-01

### Context
Iteration 1 added a "Demo / Offline Staff Fallback" in
`SupabaseAuthRepository.refreshSession()` that fabricated a SUPER_ADMIN
session on every cold-start. This was a privilege-escalation bug
masquerading as a "demo fallback".

### Decision
`refreshSession()` must NEVER fabricate a session. It either:
1. Returns the in-memory session if one exists.
2. Restores from `auth.currentUserOrNull()` + profile + roles + permissions.
3. Returns `Result.Ok(null)` if no persisted session exists —
   `AppNavHost` routes to Login.

### Rationale
- Fabricating a SUPER_ADMIN session on cold-start is a critical security
  hole — any user who launches the app gets all 56 permissions.
- RLS would still block server-side access (no real JWT), but the mobile
  UI trusted the local session and rendered every admin screen.

### Consequences
- Cold-start without a persisted session → Login screen (correct UX).
- Demo fallback should be gated behind `BuildConfig.DEBUG` if needed at
  all (deferred to iteration 4 — see `next-steps.md` #23).

---

## D-07 — Audit log writes are NOT enqueued via SyncSupport

**Status:** ✅ Accepted (iteration 3)
**Supersedes:** The iteration-3 attempt to migrate `SupabaseAuditRepository`
**Related:** D-02

### Context
Iteration 3 attempted to migrate `SupabaseAuditRepository.log()` to
`SyncSupport.tryThenEnqueue()`. This created a Hilt dependency cycle:
`SyncService` → `AuditRepository` (for failure logging) →
`SyncSupport` → `SyncService`.

### Decision
Audit log writes call `write_audit_log` RPC directly. On failure, the
error is surfaced to the caller; the user can retry the originating
action. Audit entries are NOT enqueued for later sync.

### Rationale
- The Hilt cycle is a hard blocker — the app won't compile.
- The desktop also writes audit logs directly without queueing.
- Server-side triggers + RLS enforce invariants regardless of whether
  the audit log is written.
- Losing an audit entry while offline is acceptable — the originating
  action's audit log is the critical one, and that action will fail
  (and be enqueued) if it's a mutation.

### Consequences
- `SupabaseAuditRepository` has no `SyncSupport` dependency.
- Audit entries lost while offline are gone (not retried).
- The `SupabaseSyncDao` has no `pushAuditLog` method.

---

## D-08 — `RoutePermissions` uses single `Permission` (deferred refactor)

**Status:** ⚠️ Accepted as technical debt (iteration 2)
**Supersedes:** —
**Related:** D-09

### Context
Desktop uses `AccessRequirement` (sealed type supporting
`RequiresPermission`, `RequiresAnyOf`, `RequiresAllOf`, `RequiresRole`,
`Permanent`, `empty`). The mobile `RoutePermissions` map uses only
`Map<KClass<out Route>, Permission>` (single permission per route).

### Decision
Defer the refactor to iteration 4. In the meantime:
- `Settings` route is ungated (any signed-in user can access).
- `DashboardHub` uses `VIEW_AUDIT_LOG` instead of a role-set.
- `BatchRegistration` uses only `CREATE_PARENT` (should be
  `RequiresAnyOf([CREATE_PARENT, CREATE_STUDENT])`).

### Rationale
- Iteration 2 + 3 focused on build repair + critical bugs.
- The RBAC refactor is mechanical but touches `Routes.kt`, `rbacGate.kt`,
  `Rbac.kt`, and `FeatureGate` — needs careful testing.
- No current user has been granted a tenant override that would expose
  the gap (per the default role-permission matrix).

### Consequences
- `FeatureGate.evaluate` is dead code.
- 3 RBAC mismatches with desktop (K-02, K-03, K-12 in `known-issues.md`).
- Iteration 4 must address this (see `next-steps.md` #1).

---

## D-09 — `FeatureGate.evaluate` is dead code

**Status:** ⚠️ Accepted as technical debt (iteration 2)
**Supersedes:** —
**Related:** D-08

### Context
`FeatureGate.evaluate(requirement, session)` is defined in `core/Rbac.kt`
but never called. `AppNavHost.kt`'s `rbacGate` helper uses an inline
`session?.can(required)` check.

### Decision
Leave `FeatureGate.evaluate` defined but unused until D-08 is addressed.

### Rationale
- Removing it would require re-adding it when D-08 is done.
- The 17 unit tests for `FeatureGate` still pass and document the
  intended behavior.

### Consequences
- 17 tests test dead code (but document intent).
- Iteration 4 must wire `FeatureGate.evaluate` into `rbacGate` (see
  `next-steps.md` #1).

---

## D-10 — `multiplatform-settings` version 1.3.0

**Status:** ✅ Accepted (iteration 3)
**Supersedes:** —
**Related:** D-05

### Context
The `auth-kt` POM declares `multiplatform-settings-no-arg:1.3.0` as a
dependency. We needed to add it explicitly to use `SettingsSessionManager`.

### Decision
Use version `1.3.0` (matching the transitive dependency).

### Rationale
- Using the same version as the transitive dependency avoids version
  conflicts.
- The `1.3.0` API is stable.

### Consequences
- If Supabase SDK bumps to a version that uses a different
  `multiplatform-settings` version, we need to bump ours too.

---

## D-11 — Preserve the modern UI; do not revert

**Status:** ✅ Accepted (iteration 0)
**Supersedes:** —
**Related:** D-01

### Context
The destructive wipe (commit `933c139`) preserved the 76-file design
system. The restoration could either (a) revert to the pre-wipe legacy
UI, or (b) preserve the new design system and rebuild around it.

### Decision
Preserve the modern design system. Integrate restored functionality
into the new UI. Do NOT revert to the old interface.

### Rationale
- The new design system is more polished (76 files, 36 components, 10
  overlays, animations, glassmorphism, tinted shadows).
- The user explicitly requested preserving the modern UI.
- The legacy UI components are still available (`ui/components/`) as a
  fallback during the progressive migration.

### Consequences
- 36 screens still use legacy `ui.components.*` imports (progressive
  migration ongoing).
- The dashboard is the only fully-migrated hub (11 files).
- Iteration 4+ will migrate the remaining screens mechanically.

---

## D-12 — Conflict resolution priority

**Status:** ✅ Accepted (iteration 0)
**Supersedes:** —
**Related:** D-01

### Context
The project is in Alpha state. Requirements may conflict with the latest
architecture, the current UI, newer implementations, stability, or
production readiness.

### Decision
When a requirement conflicts with any of the above, prioritize:
1. **Stability** — don't break the build or crash the app.
2. **Correctness** — match the desktop's business logic.
3. **Maintainability** — clean code, separation of concerns.
4. **Scalability** — patterns that scale to future features.
5. **Production readiness** — security, error handling, observability.

Never introduce regressions.

### Rationale
- A stable, correct, maintainable app is more valuable than a feature-rich
  but broken one.
- The desktop is the source of truth for correctness (D-01).

### Consequences
- Some desktop features are deferred (Excel import, workflow editor, AI
  narrative, backup) because they're "impossible or inappropriate on
  Android" or would compromise stability.
- The `next-steps.md` backlog is prioritized by impact, not by feature
  parity.

---

## D-13 — `sync_queue` table + WorkManager for offline writes

**Status:** ✅ Accepted (iteration 1)
**Supersedes:** —
**Related:** D-02

### Context
The mobile app needs to survive offline mutations (payments, roll call,
grades) and sync them when connectivity returns.

### Decision
- Persist offline mutations to a Room `sync_queue` table.
- Drain the queue via `SyncService.drainPending()` (called by
  `SyncWorker` every 15 minutes via WorkManager, or manually via
  `syncNow()`).
- Apply exponential backoff (`1000 × 2^attempts` ms, max 5 attempts).
- Mark `failed` + write `sync.push_failed` audit log entry after 5
  attempts.
- Each row's failure is isolated — one bad row does NOT block others.

### Rationale
- Mirrors the desktop's `sync-queue-store.ts` pattern.
- WorkManager is the Android-recommended way to schedule background work.
- 15 minutes is the minimum WorkManager periodic interval.
- Exponential backoff prevents hammering a failing server.

### Consequences
- Mutations can take up to 15 minutes to sync (or instantly via
  `syncNow()`).
- Mock data is NEVER pushed (defense-in-depth at enqueue + drain).
- `SupabaseSyncDao` does direct table writes (not through high-level
  repositories) to avoid replaying business logic.

---

## D-14 — `cacheThenNetwork` for reads

**Status:** ✅ Accepted (iteration 2)
**Supersedes:** —
**Related:** D-13

### Context
Offline users see empty lists when Supabase is unreachable, even if
Room has cached rows.

### Decision
`SyncSupport.cacheThenNetwork(cacheRead, cacheWrite, fetch)`:
1. Emit cached rows immediately (offline → cache only).
2. Fetch fresh rows from Supabase (online → cache updated).
3. Emit fresh rows.

### Rationale
- Mirrors the desktop's `cache-then-network` pattern.
- Users see last-known data instantly, then fresh data when available.

### Consequences
- Requires cache DAOs for each entity (only 4 exist today — Parent,
  Student, Payment, Ledger).
- Iteration 4 must add 12 more cache DAOs for full coverage.

---

## D-15 — `tryThenEnqueue` for writes

**Status:** ✅ Accepted (iteration 2)
**Supersedes:** —
**Related:** D-13

### Context
Offline mutations are lost if the Supabase call fails and there's no
queue.

### Decision
`SyncSupport.tryThenEnqueue(entity, operation, payload, isMock, sourceScreen) { mutation }`:
1. Attempt the direct Supabase call.
2. On network/offline/timeout error AND offline state, enqueue the
   mutation to `SyncService`.
3. Return `Result.Err(Errors.offline(...))` with a clear French user
   message.

### Rationale
- Mirrors the desktop's `useSyncActions().enqueue` pattern.
- Validation errors are NOT enqueued (they must be fixed by the user).
- Only network-class errors trigger the enqueue.

### Consequences
- 5/17 repositories migrated (Parent, Payment, Ledger, Attendance done;
  Audit reverted per D-07).
- 11 repositories still need migration (see `next-steps.md` #4).

---

## See also

- [`restoration-plan.md`](restoration-plan.md) for the overall strategy
- [`iteration-history.md`](iteration-history.md) for when each decision
  was made
- [`known-issues.md`](known-issues.md) for the consequences of deferred
  decisions
