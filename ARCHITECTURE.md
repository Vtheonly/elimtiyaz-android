# El-Imtiyaz Android — Architecture & Conventions

> **Single source of truth** for the new merged Android app. Read this before writing any code.

## 1. Project context

El-Imtiyaz is an Educational & Operational Management Platform with three front-ends
(Desktop Electron, **Staff Android**, Client Web Portal) sharing one **Supabase** backend
(PostgreSQL + Auth + RLS + Edge Functions + Storage). This repository is the **Staff Android**
app: a single, unified, production-ready Kotlin + Jetpack Compose application that replaces
the previous "El-Emtyaze-Mobile" + "El-Emtyaze-Mobile-Trafic" split.

The master plan lives at `https://github.com/Vtheonly/AgentGithubUplaod` and is the
authoritative spec. The two legacy apps are references only — **no code is copied**.

## 2. Module topology

We use a clean **5-module Gradle layout**:

```
:app              ← Application, MainActivity, NavHost, Hilt graph, root scaffold
:core             ← Design system, common UI, utilities, base classes (Compose + Hilt)
:domain           ← Pure Kotlin: models, repository contracts, use cases
:data             ← Supabase client, Room, DataStore, repository implementations
:feature          ← All feature screens, one package per feature
                    (auth, dashboard, crm, academics, financials,
                     personnel, routing, ai, settings)
```

Dependency direction: `app → feature → domain ← data`, with `core` available to all.
`domain` is **pure Kotlin** (no Android imports except `androidx.annotation`) so it stays
testable and reusable.

## 3. Tech stack

| Concern              | Choice                                                    |
| -------------------- | --------------------------------------------------------- |
| UI                   | Jetpack Compose + Material 3                              |
| Min SDK              | 26 (Android 8.0)                                          |
| Target SDK           | 35                                                        |
| Kotlin               | 2.0.21                                                    |
| AGP                  | 8.7.3                                                     |
| DI                   | Hilt 2.52                                                 |
| Navigation           | Compose Navigation 2.8.4 + type-safe routes               |
| Async                | Coroutines 1.9.0 + Flow                                   |
| Networking           | Ktor Client 3.0.3 (Supabase Kotlin SDK 2.6.1)             |
| Local cache          | Room 2.6.1 + DataStore Preferences 1.1.1                  |
| Image loading        | Coil 2.7.0                                                |
| Camera               | CameraX 1.4.0                                             |
| Maps                 | osmdroid-android 6.1.20                                   |
| Date/time            | kotlinx-datetime 0.6.1                                    |
| Serialization        | kotlinx.serialization 1.7.3                               |
| Logging              | Kermit 2.0.4                                              |
| Crashlytics          | (optional) Firebase Crashlytics                           |
| Testing              | JUnit4 + Turbine + MockK + Coroutines-Test + Compose-Test |

## 4. Design system (from master plan)

### 4.1 Color palette — dark-first

| Token              | Hex       | Role                                         |
| ------------------ | --------- | -------------------------------------------- |
| `PrimaryBlue`      | `#349BD4` | Brand, primary actions, selected tab         |
| `DeepBlue`         | `#2B7FB0` | Pressed / darker variant                     |
| `LightBlue`        | `#6EC1E4` | Hover, highlights, cyan glow                 |
| `SlateGray`        | `#3B464C` | Secondary text, dividers                     |
| `WarmGold`         | `#C8A98C` | Warning, accent, premium highlights          |
| `MutedBrown`       | `#836C68` | Tertiary text                                |
| `DarkBackground`   | `#242526` | App background                               |
| `PanelBackground`  | `#1E1F20` | Cards, bottom nav, drawers                   |
| `ElevatedSurface`  | `#2A2B2D` | Dialogs, bottom sheets, FAB                  |
| `OffWhite`         | `#EFF2F3` | Primary text on dark                         |
| `SuccessGreen`     | `#3FA66E` | Success, paid, present                       |
| `WarningGold`      | `#C8A98C` | Pending, partial                             |
| `DangerRed`        | `#C0504D` | Error, overdue, absent                       |

A light theme is derived by inverting the surface/text roles but keeping the brand colors.

### 4.2 Typography

- **Inter** — primary UI and body (400 / 500 / 600 / 700)
- **Noto Sans Arabic** — Arabic script fallback
- **JetBrains Mono** — IDs, currency, audit JSON diffs, codes

Loaded via `FontFamily` definitions in `:core/designsystem/Typography.kt`. Compose's
font fallback handles CJK/Arabic automatically when Noto Sans Arabic is listed second.

### 4.3 Spacing & shape

- Spacing scale: `0.5x = 2dp, 1x = 4dp, 2x = 8dp, 3x = 12dp, 4x = 16dp, 6x = 24dp, 8x = 32dp`
- Corner radius: `sm = 8dp, md = 12dp, lg = 16dp, xl = 24dp, pill = 999dp`
- Elevation: `0dp, 1dp, 2dp, 4dp, 8dp` (used sparingly — dark theme prefers borders)

### 4.4 Motion

- `shortDuration = 150ms`, `mediumDuration = 300ms`, `longDuration = 500ms`
- `fastOutSlowIn` for state changes, `linearOutSlowIn` for enter, `fastOutLinearIn` for exit

## 5. Navigation IA

The app uses a **5-tab bottom navigation** matching the 4 consolidated hubs from the plan
plus a fifth Personnel/Settings tab:

| # | Tab          | Icon               | Hub                             |
| - | ------------ | ------------------ | ------------------------------- |
| 1 | Home         | `dashboard`        | Dashboard + Alerts + Reports    |
| 2 | Roster       | `groups`           | Relationship Portal (CRM)       |
| 3 | Academics    | `school`           | Academic Management             |
| 4 | Financials   | `payments`         | Financial Portal                |
| 5 | Personnel    | `badge`            | Personnel + Audit + Settings    |

Routing mode (driver) is **not** a tab — it is a deep-linked destination reachable from
the Personnel tab when the user has `role = Driver`, plus an external launcher icon.

A **Profile / Settings** entry lives behind the top-bar avatar on every screen.

## 5.1 Feature gating & RBAC (future-proofed)

The app is designed so that **any** UI node — section, option, page, action, or
cross-cutting feature — can be declared once and gated by access rules without
touching the screen that renders it. Disabled nodes appear **greyed-out (visible
but locked)**, never hidden, per the platform requirement.

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│ FeatureRegistry (object)                                        │
│   The canonical Section → Option → Page/Action tree.            │
│   Every node carries an AccessRequirement.                      │
│   Single source of truth — edit one file to change all rules.   │
└──────────────────────┬──────────────────────────────────────────┘
                       │ read by
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│ FeatureGate (stateless object)                                  │
│   evaluate(node, session, flags) → AccessState                  │
│   Pure function — fully testable, no Android dependencies.      │
│   Encodes the rule ordering:                                    │
│     permanent > unauthenticated > flag > permission > role >    │
│     allOf > anyOf                                               │
└──────────────────────┬──────────────────────────────────────────┘
                       │ consumed by
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│ Compose helpers (in :core/rbac)                                 │
│   LocalSession / LocalFeatureFlagProvider (CompositionLocals)   │
│   accessStateOf(node) — read locals, call gate                 │
│   GatedContent(node) { ... } — wrap any content                 │
│   GatedFloatingActionButton / GatedIconButton /                 │
│   GatedNavigationBarItem — drop-in replacements                 │
│   DisabledOverlay / DisabledInline / DisabledPlaceholder —      │
│   three visual treatments for three contexts                    │
└─────────────────────────────────────────────────────────────────┘
```

### The three access states

| State      | Visual treatment                  | When                                                  |
| ---------- | --------------------------------- | ----------------------------------------------------- |
| `Enabled`  | Normal                            | All requirement clauses satisfied                     |
| `Disabled` | Greyed-out + lock icon, no click  | Missing permission/role, flag off, permanent, no session |
| `Hidden`   | Not rendered                      | `hideWhenUnauthenticated = true` AND no session       |

`Hidden` is reserved for cases where even revealing the existence of the feature
would leak information. **The default for disabled features is `Disabled`
(visible greyed-out), not `Hidden`** — per the user's instruction.

### The AccessRequirement type

```kotlin
data class AccessRequirement(
    val permission: Permission? = null,        // single permission
    val role: Role? = null,                     // single role
    val anyOf: Set<Permission> = emptySet(),    // OR semantics
    val allOf: Set<Permission> = emptySet(),    // AND semantics
    val featureFlag: String? = null,            // non-RBAC flag (paid plan, experiment)
    val permanent: PermanentState? = null,      // removed / desktop-only / not-yet-available
)
```

### Permanent states

`PermanentState` is for nodes that should NEVER be enabled regardless of session:

- `Removed` — feature was removed (e.g. the legacy AI assistant)
- `NotYetAvailable` — feature is on the roadmap
- `DesktopOnly` — feature exists but only on the desktop terminal (DAG editor, Excel import, backup)
- `PlanUpgradeRequired` — feature requires a higher subscription tier

Permanently-disabled nodes are kept in `FeatureRegistry.PermanentlyDisabled` and
rendered in the Settings screen's "Fonctionnalités verrouillées" card so users
can see what's not available and why.

### How to gate a new screen

```kotlin
// 1. Register the node in FeatureRegistry
val MyFeature = FeatureNode(
    id = "my_section.my_feature",
    title = "Ma fonctionnalité",
    requirement = AccessRequirement.require(Permission.MyPermission),
)

// 2. In the screen, use GatedContent
GatedContent(node = FeatureRegistry.MyFeature) {
    // ... actual screen content ...
}

// 3. Or read the state directly for custom UI
when (val state = accessStateOf(FeatureRegistry.MyFeature)) {
    is AccessState.Enabled  -> MyContent()
    is AccessState.Disabled -> CustomDisabledView(state.reason)
    is AccessState.Hidden   -> { /* nothing */ }
}
```

### How to gate a FAB or icon button

```kotlin
GatedFloatingActionButton(
    requirement = AccessRequirement.require(Permission.CollectPayment),
    onClick = { /* ... */ },
    icon = Icons.Outlined.Add,
    contentDescription = "Encaisser",
    expanded = true,
    text = "Encaisser",
)
```

When the user lacks the permission, the FAB renders at 40% alpha with a lock
icon instead of the add icon; clicks are silently ignored.

### How to gate a bottom-nav tab

The 5 hub tabs already use `GatedNavigationBarItem` (see `ElImtiyazNavHost`).
A user without `Permission.ViewFinancials` sees the Financials tab greyed-out
with a lock icon; tapping it does nothing.

### Rule: never inspect permissions directly

Screens MUST NOT call `session.can(Permission.X)` directly. Always go through
`accessStateOf(node)` or `GatedContent(node) { ... }`. This keeps the gating
rules in `FeatureRegistry` + `FeatureGate` and makes future changes (new role,
new paid plan, A/B test) a one-file edit.

The legacy `Session.can()` API remains available for repository / use-case
code that needs to enforce server-side authorization, but the UI layer should
exclusively use the `FeatureRegistry` + `FeatureGate` system.

## 6. State management pattern

Every screen follows the **unidirectional UiState pattern**:

```kotlin
data class FooUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val content: FooContent = FooContent()
)

@HiltViewModel
class FooViewModel @Inject constructor(
    private val getFoo: GetFooUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(FooUiState(isLoading = true))
    val uiState: StateFlow<FooUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, error = null) }
        getFoo().collect { result ->
            when (result) {
                is Result.Success -> _uiState.update { it.copy(isLoading = false, content = result.data) }
                is Result.Failure -> _uiState.update { it.copy(isLoading = false, error = result.message) }
            }
        }
    }
}
```

Screens collect the state with `collectAsStateWithLifecycle()`.

## 7. Repository contract

All repository methods return `Flow<Result<T>>` (cold streams) or
`suspend Result<T>` (one-shots). `Result` is in `:core/common`.

```kotlin
// domain/repository/ParentRepository.kt
interface ParentRepository {
    fun parents(): Flow<Result<List<Parent>>>
    fun parent(id: String): Flow<Result<Parent>>
    suspend fun createParent(input: CreateParentInput): Result<Parent>
    suspend fun updateParent(id: String, input: UpdateParentInput): Result<Parent>
    suspend fun deleteParent(id: String): Result<Unit>
}
```

## 8. Networking — Supabase

We use the official **Supabase Kotlin SDK** (`io.github.jan-tennert.supabase`):

- `postgrest` module for tables
- `auth` module for sign-in / JWT / refresh
- `realtime` module for live updates (attendance, payments)
- `storage` module for media vaulting (receipts, proofs, photos)
- `functions` module for Edge Functions (DAG triggers, AI)

The client is constructed in `:data/di/SupabaseModule.kt` and configured from
`local.properties` (`SUPABASE_URL`, `SUPABASE_ANON_KEY`). When those keys are absent,
the app falls back to a **mock repository** seeded with realistic data so the UI is
fully demoable. The mock switch is a single `Boolean` flag in `DataModule`.

## 9. Offline & sync

- All list screens read from Room first, then refresh from Supabase.
- Writes go to a `sync_queue` table; a `WorkManager` worker flushes on connectivity.
- Banners appear when offline or when pending writes exist.
- **Mobile Backup Prohibition**: no local DB export, no `BACKUP` permission, no
  `android:allowBackup="true"`. (Per master plan §13.05.)

## 10. Camera capture workflow (canonical)

Per master plan §18.03:

1. Request `CAMERA` permission via `rememberLauncherForActivityResult`.
2. Launch CameraX `ImageCapture` use case in a dedicated capture screen.
3. On capture, write to a temporary `File` via `ImageCapture.OutputFileOptions`.
4. Upload to Supabase Storage signed-URL path under `tenants/{tenantId}/{module}/{entityId}/{uuid}.jpg`.
5. Persist the storage key on the parent entity (e.g. `payment.proof_url`).
6. Show the captured image inline via Coil with the signed URL.

## 11. RBAC

Six roles from the plan:

| Role key           | Display               |
| ------------------ | --------------------- |
| `super_admin`      | Super Admin           |
| `financial_officer`| Financial Officer     |
| `teacher`          | Teacher / Faculty     |
| `support_staff`    | Support Staff         |
| `parent`           | Parent                |
| `student`          | Student               |

The `Session` exposes `can(permission: Permission): Boolean` and `hasRole(role): Boolean`.
Top-bar tabs, FABs, and menu items are gated by these checks. The parent/student roles
redirect to a "use the web portal" screen on this app.

## 12. Audit

Every mutating call passes a `AuditContext(action, entityType, entityId, diff, actorId, tenantId)`
to the `AuditRepository.log()` suspend function. On Supabase this hits the
`audit_log` table via an Edge Function that also writes the JSON diff.

## 13. What is explicitly NOT on mobile

Per the plan, the following stay desktop-only and **must not** be implemented here:

- Visual DAG workflow canvas editor
- `.xlsx` bulk student import / export
- Local database backup generation (prohibited)
- RBAC matrix configuration UI
- Point-in-time restoration UI

## 14. File & package conventions

- Package root: `com.elimtiyaz`
- Modules: `com.elimtiyaz.core`, `com.elimtiyaz.domain`, `com.elimtiyaz.data`,
  `com.elimtiyaz.app`, `com.elimtiyaz.feature.<name>`
- Composables are `PascalCase`; ViewModels are `<Screen>ViewModel`; UiState is `<Screen>UiState`.
- All public composables are annotated `@Composable`; preview composables live alongside.
- Resources (strings, drawables) live in `:core` and are namespaced `el_imtiyaz_*`.

## 15. Build flavors

Single flavor for v1: `prod`. A `demo` flavor may be added later for sample data;
currently demo data is gated by the runtime mock switch (§8).

## 16. Testing conventions

- Unit tests in `src/test/kotlin` test ViewModels with Turbine + MockK.
- Compose UI tests in `src/androidTest/kotlin` use `createAndroidComposeRule`.
- Repository contracts have a `MockFooRepository` in `:data` used by both demos and tests.

---

This document is the contract every contributor (human or agent) must follow.
