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
- **Notifications read-path parity (session 27, 2026-09-05, T-172/NOTIF-200):** `pullNotifications` filters `dismissed_at IS NULL` as a TOP-LEVEL AND (before the or-block) — resolved server-side alerts must not enter Room. The overdue-alert lifecycle (hub ADR-013) resolves alerts when installments are paid. KNOWN GAP (hub task T-173): `NotificationEntity` has NO `dismissedAt` column, so rows cached locally BEFORE a server-side resolution linger in Room until role eviction — a future session must add the Room migration (and pull-side eviction), NOT a schema-less workaround.

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

- **T-284/T-285 (44th session, PARITY-002) — the dashboard-statistics mirror discipline:**
  every derived statistic (descriptive stats, bins, mixes, Pareto, aging census, funnel,
  collection rate, attendance) lives in `core/StatisticsEngine.kt` — the verbatim Kotlin mirror
  of the desktop `analytics-derivations.ts` (source commit b6fbbcd recorded in the file header,
  ADR-002). Repositories and UI NEVER re-implement it inline, and NEVER render hard-coded
  reference numbers as "fallbacks" (the previous agent shipped 0.49f / 26.9M-31.4M donut /
  58_355_700_00L / 54% / "6 fam. = 80%" — fabrication, §15.16). Two engine-level parity traps
  discovered by the T-285 suite, pinned forever: (1) money-valued roundings (mean, even median,
  sample σ, MA3) round at **DZD granularity** (`dzRound` = round(x/100)*100) because the
  DESKTOP rounds its integer-DZD numbers — centime-granularity rounding silently diverges
  (MA3 of 46k+20k+40k DZD: desktop 35 333 DZD, centime-round 35 333.33); (2) the aging
  FUNNEL consumes the per-installment census Σ of bucket family-counts (379 on the
  44th-session corpus = 196 in 91–180 j + 183 in 180+ j, families counted once PER BUCKET),
  NOT the distinct-parent count (196) and NOT a per-family worst-bucket assignment.
  The debt surfaces (dashboard KPIs, debt summary, debt-by-aging, PDF aging report) all use
  the ONE canonical derivation — per-parent outstanding = Σ INV-4 remaining over unpaid
  installments; the ledger-first/else-installment fallback is GONE (it produced numbers that
  differed from the desktop). The live proof: `LiveDatabaseEquivalenceTest` (env-gated:
  SUPABASE_URL + SUPABASE_SERVICE_KEY + SUPABASE_ACCESS_TOKEN, forwarded to the forked test
  JVM by the build script — `-D` flags do NOT cross the daemon boundary) asserts the engine
  output equals the server-side SQL truth (hub `scripts/verify_t-285.sql`) on EVERY value;
  the corpus proof: the `deriveAnalyticsStats` op in both runners + the triple comparator
  (category `analytics_statistics`).

- **T-289..T-292 (45th session, PARITY-003) — the visual-parity mirror discipline:** every
  chart in the mobile Analytique tab renders ENGINE-derivated values only — the 13-chart
  inventory (weekly rhythm, heatmap, YoY, tranche waves, demographics, trend explorer,
  mixes, histogram, Pareto, aging composition, funnel, demographics, attendance) is
  computed by `core/StatisticsEngine.kt` (the ADR-002 mirror; 5 families added this
  session with verbatim desktop semantics) at the REPOSITORY level (DashboardKpi carries
  the contract; the ViewModel's slicer state calls the SAME engine functions on the
  filtered slice — the desktop analytics-tab.tsx pattern, never a UI re-implementation).
  Two conventions pinned by the corpus: (1) the WEEKLY RHYTHM is a counter-activity view
  (ONLY `status === "refunded"` excluded — pending/partial ARE counted; Fri/Sat dropped)
  — deliberately different from the paid-only encaissé slice; (2) the YoY null deltas are
  HONEST "n/a" (a divide-by-zero is never a −100% trend), and the previous-year series
  derives from the SAME local payments stream (never fabricated). The chart colors come
  from `ElChartPalette` (the desktop dashboard-theme.ts hex mirror) — never ad-hoc
  colors. The proof: `CrossPlatformEquivalenceTest.kt` + the `analytics_visuals` corpus
  (then-blocks generated by the desktop's REAL TS derivations —
  hub `scripts/generate_analytics_visuals_corpus.ts`; regenerate after changing any
  derivation on EITHER platform, then fix the other side until identical).

- **T-231 (34th session) — the workflow_runs pull contract:** the DTO must mirror the LIVE
  columns (`trigger_type` / `actor_id` / `completed_at` / `node_results`, workflow name via the
  PostgREST embed — `select(Columns.raw("*, workflows(name)"))`; postgrest-kt 3.1.1 takes a
  `Columns` value, NOT a string). The pre-T-231 DTO targeted a *planned* schema that never
  existed, so every pulled run decoded with a null trigger and null results. The
  `node_results` array serializes into the entity's existing `resultJson` column (no Room
  schema change) and decodes into `WorkflowNodeResult`s on the domain side. The
  `WorkflowTrigger.fromCode` contract: manual_run/manual → Manual, schedule/scheduled →
  Scheduled, EVERYTHING else → Event (unknown codes are EVENTS — server-side automatic
  triggers — never silently "Manuel"). Pinned by
  `app/src/test/.../supabase/WorkflowRunContractT231Test.kt`.

- **T-310 (49th session) — jsonb columns in PostgREST DTOs are `JsonElement?`, NEVER
  `String?`:** PostgREST returns jsonb columns as PARSED JSON objects (verified live,
  2026-09-12). A `String?` DTO field decodes fine while every pulled row has NULL in that
  column — then throws `SerializationException` on the FIRST non-null snapshot, failing the
  WHOLE `decodeList` as a silent `Result.Err` (the 0086 audit triggers write full
  before/after objects, which silently broke the entire audit pull). The fix shape: type the
  field `JsonElement?`, stringify (`toString()` → compact JSON text) in the `toEntity()`
  mapper for the Room `String` columns, and re-parse on the read side. Related lesson from
  the same session: an RPC that writes its audit payload into a NON-canonical column while
  the canonical ones stay NULL orphaned every client mapper — when a server contract and
  its clients disagree, fix the contract AND keep a client fallback for the historical
  rows (`SharedDtoMappers.effectiveAuditSnapshots`). Pinned by
  `app/src/test/.../supabase/AuditLogDiffRecoveryT310Test.kt`.

- **Navigation stack + counter-payment member selection (35th session, 2026-09-08):**
  two UX defects closed in one pass. (1) Back-press previously reset the bottom-nav
  host to the Dashboard hub regardless of the user's tab trail; `MainScreen` now keeps a
  `rememberSaveable` `tabHistory` and its `BackHandler` pops to the previous TAB in the
  trail (deeper detail screens already popped correctly via `popBackStack` — the defect
  was only the in-host tab reset). `FinancialsHubScreen`/`DebtDashboardScreen` gained
  `BackHandler`s that step back one level (tab 0 / `onBack`) instead of escaping to the
  dashboard. (2) `CounterPayment` deep-links from Student/Parent/Debt screens used to
  skip straight to the payment form; the route now carries optional
  `parentId`/`studentId` prefill args and the screen enforces an explicit
  "Choisir le membre de la famille" step (`isFamilyMemberChosen`) — an internal
  `BackHandler` unwinds member → parent → list before letting the system back take over.
  State/logic moved to a new `CounterPaymentViewModel` (SavedStateHandle route args).
  ⚠ The provided patch file's `DebtDashboardScreen.kt` used `Modifier.width(1.dp)`
  without the `androidx.compose.foundation.layout.width` import — added during apply;
  if a future session re-bases this change, watch for the same omission.

- **The UI/UX-overhaul session (57th session, 2026-09-13, T-320..T-325) — four pinned
  lessons for any future screen rewrite:**

  1. **The two component layers have DIFFERENT APIs — the DS one is stricter.**
     The designsystem `ElDropdown` takes `List<ElDropdownOption>` +
     `onSelected: (ElDropdownOption) -> Unit` (match `selectedValue` against
     `option.value`); the legacy string-based overload exists ONLY in
     `ui.components`. The DS `ElListItem` has NO composable `leading`/`trailing`
     slots (use `leadingIcon: ImageVector?`/`trailingBadge`/`trailingText`, or
     build an `ElCard` row when you need a tag + button trailing). The DS `ElTag`
     has NO `onClick`/`selected`/raw-`color` — interactive pills are `ElChip`;
     static labels take `ElTagTone`. `ElAlertSeverity` in the DS layer is
     UPPERCASE (INFO/WARNING/DANGER); the legacy enum is TitleCase (Info/Danger)
     — mixing them is a compile error.
  2. **Result-state reset ≠ data refresh.** `CounterPaymentViewModel.collect`'s
     success path used to call `selectParent(current)` to refresh the ledger
     data — but `selectParent` RESETS `_receiptNumber`, so the success state
     was erased in the same frame and the receipt NEVER displayed. The pattern
     now: `refreshFamilyData(parentId)` cancels + restarts the family jobs
     WITHOUT touching result state. Rule: any ViewModel refresh triggered from
     a success path must NOT route through a state-resetting entry point. The
     same invisible-success class existed in BatchRegistration (the VM called
     `onSuccess()` — a tab switch — immediately after setting the code).
  3. **The RealtimeSyncT069Test helper/assertion race is now at its THIRD
     occurrence** — assertions updated when a table joins the canonical
     subscription set (4 → 6 → 7 tables), helper threshold forgotten each
     time. `waitForSubscriptions()` must poll the SAME count the assertions
     expect. When you add a table to the set, update BOTH in one commit.
  4. **The gradle daemon in this 4GB container OOM-kills at the old memory
     settings** under a full test+assemble gate: `gradle.properties` now runs
     `-Xmx1400m -XX:MaxMetaspaceSize=640m` (was 2048m/1024m). If you see
     "Gradle build daemon disappeared unexpectedly", check memory FIRST, and
     do not raise the heap back past ~1.5G. The toolchain re-provision script
     is `/home/z/my-project/scripts/android-env.sh` (JDK 21.0.5 + SDK 35 +
     the ROOT `.env` with non-empty keys — §8.1).

- **God-file modularization + ebb833b cleanup (36th session, 2026-09-08):** the 5
  files >800 lines were split into 37 focused same-package files (behaviour-neutral
  moves; zero logic changes except one, see below): `LocalRepositories.kt` (1859) and
  `LocalRepositories2.kt` (2048) → one file per repository class in
  `infrastructure/local/`; `StudentDetailScreen.kt` (1423) → ViewModel + screen +
  `StudentDetailAcademicComponents.kt`; `ParentDetailScreen.kt` (996) → screen +
  `ParentDetailDialogs.kt`; `FinancialsHubScreen.kt` (801) → screen + 5 tab files +
  `FinancialsSharedComponents.kt`. Private top-level declarations referenced across
  the new file boundaries became `internal`. Largest file after the split: 770 lines.
  ⚠ **CRITICAL discovery — the test suite is partially SOURCE-ANCHORED:** 7 test
  classes (`TenantStampingT051Test`, `HollowImplementationsT054Test`,
  `LocalAuthRepositoryTest` SEC-102 guard, `RefundCorrectnessT017Test`,
  `RefundInstallmentSyncT128Test`, `HomeworkPromotionT024Test`,
  `OverdueRuleT026Test`) open source FILES by hardcoded path and pin their content —
  any file rename/split in `infrastructure/local/` MUST update those scans (they now
  read a directory union; `AuditContext.kt` is excluded from the demo-UUID scan because
  it is the sanctioned single home of `DEMO_TENANT_ID`). Also fixed en passant:
  `LocalLedgerRepository.reconcile()` called `computeParentSummary` WITHOUT the
  due-date map (bare-call latent bug — T-026/WEAK-007 contract; the old test scope
  missed it) — now passes `buildOverdueDueDateMap(parentEntries)`. The stray
  `fix.patch` (1229 lines, corrupt at line 490, fully absorbed into the tree,
  committed by ebb833b as drift bait — same pattern as CROSS-003) was REMOVED.

- **Login ANR — Supabase client built on the MAIN thread (37th session, 2026-09-08):**
  user report: "the app blocks when you enter a credential, then stops working."
  Root cause (visible only on CONFIGURED builds — i.e. since `.env` keys are baked
  in, every delivered APK): the FIRST `SupabaseClientProvider.build()` call ran
  inside `NetworkTimeouts.guard { supabaseProvider.auth… }` launched from
  `viewModelScope` (Dispatchers.Main.immediate) — at the first sign-in tap OR the
  cold-start `refreshSession()`. `build()` performs blocking Android-Keystore
  (`MasterKey`), `EncryptedSharedPreferences` creation, and the initial synchronous
  SharedPreferences disk load — NONE of which has a suspension point, so
  `withTimeout` could never interrupt it: a slow/contended Keystore froze the main
  thread → ANR exactly at credential submission. Three-layer fix: (1)
  `NetworkTimeouts.guard`/`guardSyncPush` now wrap every block in
  `withContext(Dispatchers.IO)` (27 call sites — all infrastructure, none touch UI);
  (2) `ElImtiyazApplication.onCreate` pre-warms the client via
  `supabaseClientProvider.warmUp()` on the application IO scope;
  `SupabaseClientProvider.build()` logs a warning if ever invoked on the main thread;
  (3) `LoginViewModel.signIn` gained a catch-all so an unexpected throw can never
  strand `isLoading=true` (spinner-forever) or crash `viewModelScope`. Also fixed:
  the LoginScreen safety-net navigation to Main lacked `launchSingleTop` → raced
  the session observer's `LaunchedEffect(currentSession)` in the same frame and
  double-pushed Main (duplicate back-stack entry). Regression-pinned by
  `NetworkTimeoutsAnrRegressionTest` (3 tests). Fallout fix:
  `RealtimeSyncT069Test.waitForSubscriptions` polled a stale `>=4` threshold while
  assertions expect 6 tables (chat pair joined in T-102-follow-up) — a latent race
  any scheduler shift could surface; now `>=6`. ⚠ Known inconsistency left
  deliberately untouched: `AuthEnvironment.fromBuildConfig()` uses
  `NetworkTimeouts.isSupabaseConfigured` (BuildConfig-only) while
  `SupabaseClientProvider.isConfigured()` also honours the runtime SharedPreferences
  override — a user configuring Supabase ONLY via Settings gets the demo sandbox
  (debug) instead of real auth. Fixing this changes SEC-101 semantics and needs its
  own task. Same class of debt: `SupabaseChatRepository` methods call
  `provider.postgrest` directly (no `withContext(IO)`) — post-warm this is jank-only,
  not an ANR, but a future task should route them through the guard.

## 9. Forbidden in this repository

- Rewiring `RepositoryModule` bindings toward Supabase repositories before ADR-005 is Accepted.
- Editing or applying `supabase/migrations/*` (hub-owned, ADR-001). The stale copies were REMOVED from this repo in T-048 (2026-08-31) — never restore them.
- Client-side role/permission decisions (roles come from `role_assignments`, least-privilege fallback).
- New random/sequential ID generators; new local receipt numbering.
- Creating documentation or task lists here — everything belongs in the hub (ADR-007). This file is the only documentation this repo carries.
- History rewrites of any kind.
