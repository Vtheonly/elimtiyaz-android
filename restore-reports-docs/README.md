# El-Imtiyaz Android — Restoration Documentation Hub

> **Single source of truth** for all project history, restoration reports,
> architecture, and forward planning. Anyone new to the repository should
> start here.

This folder consolidates every scattered Markdown file that previously
lived at the repository root (`README.md`, `worklog.md`) and in the
workspace (`iteration-3-restoration-report.md`,
`remaining-gaps-audit.md`). Redundant copies have been removed; every
topic now has **one authoritative location**.

---

## How to Navigate This Documentation

Read the documents in this order if you are new to the project:

| Step | Document | Purpose |
|------|----------|---------|
| 1 | [`project-overview.md`](project-overview.md) | Why this project exists, the problem it solves, the two-repo setup (mobile + desktop). |
| 2 | [`architecture.md`](architecture.md) | Folder structure, module responsibilities, data flow, business logic flow, UI/state/DB/API/sync/build. |
| 3 | [`commit-history-analysis.md`](commit-history-analysis.md) | Chronological timeline of every git commit, regressions, and the "disaster" commits. |
| 4 | [`iteration-history.md`](iteration-history.md) | Engineering journal — each restoration iteration's objectives, work, problems, solutions, lessons. |
| 5 | [`current-status.md`](current-status.md) | What works today, what is broken, technical debt, risks, blockers. |
| 6 | [`known-issues.md`](known-issues.md) | Bug catalog with severity, workaround, and fix link. |
| 7 | [`next-steps.md`](next-steps.md) | Prioritized roadmap for iteration 4+. |
| 8 | [`restoration-plan.md`](restoration-plan.md) | Overall strategy + methodology that governs all iterations. |
| 9 | [`decisions.md`](decisions.md) | Architectural + restoration decisions with rationale. |
| 10 | [`migration-report.md`](migration-report.md) | Consolidated technical report of what was migrated and how. |
| 11 | [`work-log.md`](work-log.md) | Chronological log of every agent's work (merged from `worklog.md`). |

---

## Documentation Standards

All documents in this folder follow these rules:

1. **One topic per file.** No document duplicates content that lives
   elsewhere. Cross-reference with relative links instead.
2. **Headings use sentence case** and follow a strict `#` → `##` → `###`
   hierarchy. No skipped levels.
3. **Tables for structured data** (commits, bugs, modules). Prose for
   narrative.
4. **Code references use backticks** with the full path:
   `app/src/main/java/com/example/core/LedgerEngine.kt`.
5. **Commit references use the short SHA** in backticks: `933c139`.
6. **Every claim is sourced** — either a file path, a commit SHA, or a
   link to another doc.
7. **Dates use ISO 8601** (`2026-08-02`).
8. **Status badges** at the top of status docs:
   - ✅ Done · 🟡 In progress · ⚠️ Partial · ❌ Broken · 🔴 Critical
9. **No emojis in prose** — only in status badges and section markers.
10. **Each file ends with a "See also" section** linking to related docs.

---

## Quick Reference

- **Mobile repo:** https://github.com/Vtheonly/elimtiyaz-android
- **Desktop repo (source of truth):** https://github.com/Vtheonly/AgentGithubUplaod
- **Current HEAD:** `82990e1` "mid" + iteration-3 fixes (uncommitted — see
  [`work-log.md`](work-log.md) § ITER3-BUILD-FIX)
- **Build status:** ✅ `./gradlew :app:assembleDebug` produces a 28 MB APK
- **Test status:** 98/100 unit tests pass (2 pre-existing test-only failures)
- **Last stable commit:** `782bde1` "aight mid" (2026-08-01 12:35) — see
  [`commit-history-analysis.md`](commit-history-analysis.md) § "Safe restoration points"

---

## File Index

| File | Lines | Last updated | Author |
|------|-------|--------------|--------|
| `README.md` | — | 2026-08-02 | Super Z (iter 3) |
| `project-overview.md` | — | 2026-08-02 | Super Z (iter 3) |
| `architecture.md` | — | 2026-08-02 | Super Z (iter 3) |
| `commit-history-analysis.md` | — | 2026-08-02 | Super Z (iter 3) |
| `iteration-history.md` | — | 2026-08-02 | Super Z (iter 3) |
| `current-status.md` | — | 2026-08-02 | Super Z (iter 3) |
| `known-issues.md` | — | 2026-08-02 | Super Z (iter 3) |
| `next-steps.md` | — | 2026-08-02 | Super Z (iter 3) |
| `restoration-plan.md` | — | 2026-08-02 | Super Z (iter 3) |
| `decisions.md` | — | 2026-08-02 | Super Z (iter 3) |
| `migration-report.md` | — | 2026-08-02 | Super Z (iter 3) |
| `work-log.md` | — | 2026-08-02 | Merged from `worklog.md` (iters 1–3) |

---

## Maintenance

When adding a new document:

1. Place it in this folder.
2. Add a row to the **File Index** above.
3. Add a row to the **How to Navigate** table if it's a top-level read.
4. Update `current-status.md` if the new doc changes the project state.
5. Update `work-log.md` with a new entry describing the doc addition.

When removing a document:

1. Search the entire folder for links to it (`grep -r "removed-doc.md"`).
2. Update or remove those links.
3. Delete the **File Index** row.
4. Delete the **How to Navigate** row if present.

---

See also: [`project-overview.md`](project-overview.md) for the project's
purpose and high-level architecture.
