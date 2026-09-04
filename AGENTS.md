# AGENTS.md — elimtiyaz-android Repository Manual

> Operating manual for AI coding agents working in **this** repository. System-level documentation (architecture, canonical rules, problem registry, task registry, ADRs) lives in the **hub repository** `AgentGithubUplaod` — check it out as a sibling or consult it before any non-trivial change. Start there: `AgentGithubUplaod/AGENTS.md`.

## 1. What this repository is

The **Android staff application** of the El-Imtiyaz school-management system: Kotlin + Jetpack Compose, offline-first, Room as the local primary store, Supabase (Kotlin SDK) for auth + RPC push/pull + FCM. It shares one Supabase backend with the desktop app (`AgentGithubUplaod`) and the parent portal (`elimtiyaz-website`).

- Package namespace: `com.example` · applicationId: `com.aistudio.elimtiyazstaff.bxmzlx`
- Build: `./gradlew assembleDebug` · Tests: `./gradlew test` · Lint: `./gradlew lint`

## 2. Repository map

```
app/src/main/java/com/example/
├── core/              # domain engines: LedgerEngine, IdentityCodes, AuditActions (Kotlin mirrors of desktop canonical)
├── domain/            # domain models + repository interfaces
├── infrastructure/
│   ├── local/         # Local*Repository implementations (Room-first, sync-enqueued writes)
│   ├── room/          # Room DB (v11), entities, DAOs  ⚠ two parallel layers (legacy cache + Local*) — DUP-005
│   ├── supabase/      # SupabaseClientProvider, DTOs, mappers, SupabaseRealtimeEventSource (T-069)
│   ├── sync/          # SyncService, SyncQueueDispatcher, PullSyncRepository, OnlineDetector, SyncSupport,
│   │                  #   RealtimeSyncManager (T-069: postgres-change subscriptions → granular pulls;
│   │                  #   session-reactive; the 15-min cycle is the fallback)
│   └── notifications/ # FCM service + registrar
├── di/                # Hilt modules (RepositoryModule binds EVERYTHING to Local*)  ⚠ ARCH-003
├── session/           # SessionManager
└── ui/                # Compose UI (designsystem/ = canonical, ui/components/ = legacy)  ⚠ DUP-003/004
app/src/test/          # unit + Robolectric tests (+ equivalence runner reading the desktop's corpus)
```

> **Migrations (T-048, 2026-08-31):** the six stale migration copies
> (0034/0035/0036/0040/0041/0042) that used to live in `supabase/migrations/`
> were REMOVED — the hub repo owns the canonical chain (ADR-001) and the
> duplicates were documentation-only drift bait (CROSS-003). NEVER recreate
> a `supabase/` directory in this repo; the `supabase/` folder in this
> repo's git history is a stale partial copy — never apply it, never edit
> it, never treat it as schema truth.

## 3. Role in the system & critical context

- **CURRENT state:** all repositories bind to `Local*Repository`; server writes go Room → sync queue → `upsert_*_from_import` RPCs. The canonical financial RPCs (`collect_and_allocate_payment`, `revert_payment_allocation`) are **never called** from this app (problem ARCH-003 / CROSS-005). The target architecture (write through canonical RPCs; ADR-005 in the hub) is **Proposed — do not partially rewire** `RepositoryModule` until it is accepted.
- The Kotlin engines in `core/` are **mirrors** of the desktop canonical engine; behaviour changes must come from the hub's canonical implementation first, then port, then pass equivalence (`AgentGithubUplaod/docs/testing/cross-platform.md`).
- The `supabase/migrations/` folder here is a stale partial copy — never apply it, never edit it, never treat it as schema truth (CROSS-003).
- Two auth bypasses are the repository's most dangerous defects: offline-fallback SUPER_ADMIN sessions (SEC-101) and email-substring role inference (SEC-102). Fix task: T-002. *(Both closed 2026-08-29 by T-002 — fail-closed sign-in, server-side role resolution; see hub change-log.)*
- **Chat (session 14, 2026-08-31):** chat is now a COMMITTED cross-platform feature (ADR-008, owner decision) and live on desktop + website + backend (migration 0061). This app has **NO chat UI at all** (only `USE_CHAT` / `MANAGE_CHAT_CHANNELS` permission constants in `core/Rbac.kt`) — a scope gap registered as ANDR-CHAT-200 / task T-102 in the hub. Do not assume chat exists here; building it (read-side + online sends first) or pruning the dead permission codes is the task.
- **FCM token lifecycle (session 8, 2026-08-30):** `register` and `deactivate` both go through caller-verified canonical RPCs (hub migration 0050 — `register_fcm_token` verifies auth.uid() owns p_user_id; `deactivate_fcm_tokens(p_user_id, p_platform)` is the shared sign-out path). `LocalAuthRepository.signOut` deactivates Android tokens BEFORE revoking the JWT — called directly on the provider, NOT via `FcmTokenRegistrar` (that injection would create a Hilt cycle: LocalAuthRepository → FcmTokenRegistrar → SessionManager → AuthRepository).

## 4. Before changing anything (mandatory)

1. Read the hub `AGENTS.md` and the relevant hub docs (source-of-truth registry first).
2. Read your task in `AgentGithubUplaod/docs/recovery/task-registry.md` and its problem entries (this repo owns many: SEC-101/102, ARCH-003/004, CROSS-005/200, DUP-003/004/005, WEAK-006/007/009/010/011/012, SYNC-103/104/106/107, …). When you need the full end-to-end trace or git forensics behind a problem ID, read the raw finding in `AgentGithubUplaod/docs/audits/` (read-only archive; see its README for ID-mapping rules).
3. Search this repo AND the hub repo for existing implementations before writing anything.
4. Check `AgentGithubUplaod/docs/recovery/unknowns.md` for anything your change depends on (UNKNOWN-002 blocks the write-architecture work).
5. Follow the hub's workflow (`docs/agents/workflow.md`) and commit standard (`docs/agents/git-workflow.md`).

## 5. During implementation (Android-specific rules)

- **Reuse `core/` engines and `IdentityCodes`** — never re-implement financial logic inline in repositories or ViewModels.
- **Deterministic identity codes only** (ADR-003): no `Math.random()`, no `count+1` sequences for `PAR-`/`ELV-`/receipt numbers.
- **Room schema changes ship explicit migrations** — `fallbackToDestructiveMigration(true)` is enabled (ARCH-004, fix T-046); until removed, treat every schema bump as potentially wiping user data and write the migration + `MigrationTestHelper` test.
- **Sync writes must surface errors** — the Kotlin SDK's `rpc()` returns an `HttpResponse`; read it; never mark a rejected write "synced" (CROSS-200, fix T-019).
- **Tenant + actor identity**: local writes stamp the signed-in user's tenant (no DEMO UUID) and audit entries capture the actor role (WEAK-011, fix T-051).
- Keep UI in the **designsystem** components (`ui/designsystem/`); the legacy `ui/components/` layer is slated for removal (DUP-003, task T-044).

## 6. Before finishing

1. `./gradlew lint` + `./gradlew test` green (plus the suites the task requires).
2. Inspect the full diff; confirm no duplicate implementation, no unrelated changes.
3. Cross-platform check: if you changed shared behaviour, verify against the desktop/website (equivalence suites for financial/academic changes).
4. Update the hub registries (problem status, task status, change-log) and commit per the git standard.
5. Never claim VERIFIED without evidence — see `AgentGithubUplaod/docs/recovery/definition-of-done.md`.

## 7. Commit rule (applies to every commit in this repo)

Every commit body must answer five questions (hub `AGENTS.md` §14, full template in `AgentGithubUplaod/docs/agents/git-workflow.md`): **which task was completed** (`Task:` — T-ID + status reached) · **what is left** (`Left:`) · **what was changed** (`Change:` + `Preserved:`) · **what was verified** (`Verified:` — real commands and real results, e.g. `./gradlew test` with the test count) · **the next task** (`Next:` — T-ID + one-line reason). The commit records progress for the next agent, not just the change for git.

## 8. Verification commands (quick reference)

```bash
./gradlew lint                  # static analysis
./gradlew test                  # unit + Robolectric
./gradlew assembleDebug         # build sanity
# equivalence (requires hub repo checked out as sibling):
./gradlew test --tests "com.example.equivalence.AndroidEquivalenceTest"
```

### 8.1 The `.env` / secrets-plugin location quirk (25th session, 2026-09-04 — T-159)

The secrets-gradle-plugin **2.0.1 resolves BOTH `propertiesFileName` (`.env`) and
`defaultPropertiesFileName` (`.env.example`) against the ROOT project** — not
against `app/`. Bytecode + live-evidence verified: an `app/.env` is NEVER read;
the generated `BuildConfig` fields come from the ROOT-level files only.

- The committed ROOT `.env.example` intentionally ships `SUPABASE_ANON_KEY=`
  and `SUPABASE_PUBLISHABLE_KEY=` EMPTY (template design) — those empty defaults
  are what produce the bare `SUPABASE_ANON_KEY = ;` literals that fail
  compilation (the AGENTS.md §11 hub quirk; the 22nd session diagnosed the
  empty-values mechanism correctly but did not record the root-vs-app location).
- **The fix, every session:** create a ROOT-level `.env` (gitignored — verified)
  next to `gradle.properties` with the KEY values filled from the canonical
  public publishable key (hub `docs/operations/credentials.md` §1; ADR-009 dual
  acceptance — never service_role/sb_secret/sbp_ tokens). The re-runnable
  provisioning recipe is `/home/z/my-project/scripts/android-env.sh` (container
  resets wipe the toolchain; the script also re-creates this `.env` guidance).
- Symptom → cause map for future agents: `SUPABASE_ANON_KEY = ;` in the generated
  `BuildConfig.java` = the plugin found NO root-level `.env` (or the committed
  root `.env.example`'s empty defaults won). It is NOT a `app/.env` problem.

## 9. Forbidden in this repository

- Rewiring `RepositoryModule` bindings toward Supabase repositories before ADR-005 is Accepted.
- Editing or applying `supabase/migrations/*` (hub-owned, ADR-001). The stale copies were REMOVED from this repo in T-048 (2026-08-31) — never restore them.
- Client-side role/permission decisions (roles come from `role_assignments`, least-privilege fallback).
- New random/sequential ID generators; new local receipt numbering.
- Creating documentation or task lists here — everything belongs in the hub (ADR-007). This file is the only documentation this repo carries.
- History rewrites of any kind.
