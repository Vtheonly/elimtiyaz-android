# Restoration Plan

> **Audience:** Anyone leading or contributing to the restoration effort.
> **Read time:** ~8 minutes.

This document describes the overall strategy, methodology, and
governance that govern all restoration iterations. It is the "how we
work" companion to the "what we work on" backlog in `next-steps.md`.

---

## 1. Mission

Restore the El-Imtiyaz Android mobile application to **full functional
parity with the Desktop reference implementation**, while:

1. **Preserving the modern UI** (the 76-file design system that survived
   the wipe).
2. **Mirroring the desktop's business logic exactly** (formulas,
   workflows, validation, wire formats).
3. **Never introducing regressions** (stability > correctness >
   maintainability > scalability > production readiness — see
   `decisions.md` D-12).

---

## 2. Sources of Truth

| Source | Role | Location |
|------|------|------|
| **Desktop repository** | Authoritative reference for business logic, schema, workflows, formulas | https://github.com/Vtheonly/AgentGithubUplaod |
| **Mobile repository** | Target project being restored | https://github.com/Vtheonly/elimtiyaz-android |
| **Pre-wipe commit `782bde1`** | Reference for the mobile's own pre-wipe architecture | `git show 782bde1` |
| **Desktop investigation report** | ~17,800-word reference of desktop's business logic | (produced in iteration 0; summarized in `iteration-history.md`) |
| **This documentation folder** | Single source of truth for restoration history + decisions | `restore-reports-docs/` |

---

## 3. Methodology

### 3.1 Investigate before coding

Every iteration begins with a thorough investigation phase:
- Read the relevant desktop source files.
- Read the relevant mobile source files.
- Identify the gap precisely (file, line, expected vs. actual).
- Document the gap in `known-issues.md` before fixing it.

**Rationale:** The iteration-0 investigation (3 parallel subagents,
~39,000 words of reports) saved days of trial-and-error by producing a
complete map before any code was written.

### 3.2 Parallelize independent work

When a wave of work has independent subtasks, delegate to parallel
subagents:
- Iteration 1 used 3 parallel waves (design primitives, domain repos,
  sync+RBAC+settings).
- Iteration 3 delegated the SyncSupport migration to a subagent while
  the main agent wrote the restoration report.

**Constraint:** Subagents do NOT have access to the full conversation
context. Pass a self-contained task description + Task ID + worklog
instructions.

### 3.3 Prove the pattern on one reference, then migrate mechanically

- Iteration 2 proved `SyncSupport.tryThenEnqueue()` +
  `cacheThenNetwork()` on `SupabaseParentRepository`.
- Iteration 3 migrated 3 more repositories (Payment, Ledger, Attendance)
  using the same pattern mechanically.
- Iteration 4 will migrate the remaining 11.

**Rationale:** Establishing the pattern on one reference surfaces edge
cases (like the Hilt cycle in D-07) before they propagate.

### 3.4 Verify after every change

After every file modification:
1. Run `./gradlew :app:compileDebugKotlin`.
2. If it passes, run `./gradlew :app:testDebugUnitTest`.
3. If it passes, run `./gradlew :app:assembleDebug`.
4. Commit (or stage for commit).

Never leave the build broken at the end of a work session.

### 3.5 Document as you go

- Append to `work-log.md` after every Task ID.
- Update `known-issues.md` when a new bug is discovered.
- Update `current-status.md` when a module's status changes.
- Update `decisions.md` when a significant decision is made.

---

## 4. Conflict Resolution Policy

When a requirement conflicts with the latest architecture, the current
UI, a newer implementation, stability, or production readiness:

1. **Do NOT blindly implement it.**
2. **Preserve stability** — don't break the build or crash the app.
3. **Document the conflict** in `decisions.md`.
4. **Defer the requirement** to a future iteration if needed.

Priority order (see `decisions.md` D-12):
1. Stability
2. Correctness
3. Maintainability
4. Scalability
5. Production readiness

---

## 5. What Is NOT Being Restored

Per the conflict resolution policy, the following desktop features are
**intentionally absent** from mobile:

| Feature | Reason | Reference |
|------|------|------|
| Workflow editor (DAG canvas) | Touch DnD impractical on mobile | Plan §11 |
| Excel import engine | Bulk import impractical on mobile | Plan §14 |
| Backup | Mobile PROHIBITED from backups | Plan §13.05 |
| AI narrative / anomaly explainer | Desktop-only per plan | Plan §11 |
| RBAC matrix editor | Too complex for mobile | — |

These are documented as "P2 — desktop-only" in `current-status.md` § 4
and `next-steps.md` § 3.

---

## 6. Iteration Cadence

- **Duration:** 1 week per iteration (typical).
- **Scope:** 3-5 items from `next-steps.md`, prioritized by impact.
- **Deliverable:** Compiling APK + updated `work-log.md` + updated
  `current-status.md` + (optional) new `iteration-N-report.md`.

### Iteration structure

1. **Day 1:** Investigation — read desktop + mobile source for the
   scoped items. Update `known-issues.md` with any new bugs discovered.
2. **Days 2-4:** Implementation — fix the bugs, add the features.
   Verify after every change.
3. **Day 5:** Validation — run full test suite, build APK, manual smoke
   test. Update `current-status.md` + `work-log.md`. Plan iteration N+1.

---

## 7. Quality Gates

Before an iteration is declared complete:

- [ ] `./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL
- [ ] `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
- [ ] `./gradlew :app:testDebugUnitTest` → no new failures (existing
  failures documented in `known-issues.md`)
- [ ] No `TODO()` / `NotImplementedError()` in production code
- [ ] No hardcoded sample data in production screens (except documented
  fallbacks — see K-01)
- [ ] `work-log.md` updated with the iteration's Task ID
- [ ] `current-status.md` updated to reflect the new state
- [ ] `known-issues.md` updated with any new bugs discovered
- [ ] No critical security holes (no fabricated sessions, no
  `service_role` key in APK)

---

## 8. Communication Protocol

- **Worklog:** Every agent (main or subagent) appends to
  `work-log.md` with a `Task ID`, `Agent`, `Task`, `Work Log`, and
  `Stage Summary` section.
- **Decisions:** Significant decisions recorded in `decisions.md` with
  rationale.
- **Status:** `current-status.md` is the live status board — update it
  whenever a module's status changes.

---

## 9. Risk Management

See `current-status.md` § 8 for the risk register. Top risks:
1. Supabase credentials leak in APK → mitigated by RLS + `anon` key only.
2. Offline mutation lost → mitigated by `sync_queue` + audit log.
3. Hilt dependency cycle on future SyncSupport migrations → check for
   cycles before adding `SyncSupport` to any repo that `SyncService`
   depends on.

---

## 10. Success Criteria

The restoration is "complete" when:

- ✅ All 17 repositories migrated to `SyncSupport` (offline-first).
- ✅ All 47 screens migrated to the new design system.
- ✅ Full RBAC parity with desktop (`AccessRequirement` + `FeatureGate`).
- ✅ All 4 external RPCs + Edge Functions deployed.
- ✅ All P0 + P1 bugs in `known-issues.md` fixed.
- ✅ UI tests for every screen.
- ✅ CI/CD with conventional-commit format.

**Current progress (end of iteration 3):** ~40% complete. See
`current-status.md` for the detailed status board.

---

## See also

- [`next-steps.md`](next-steps.md) for the prioritized backlog
- [`decisions.md`](decisions.md) for the architectural decisions
- [`iteration-history.md`](iteration-history.md) for the engineering journal
- [`work-log.md`](work-log.md) for the raw chronological log
