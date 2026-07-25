# El-Imtiyaz Android — Staff Mobile Platform

A production-ready, single, unified Android application built from scratch in **Kotlin + Jetpack Compose + Gradle** that implements the **mobile component** of the El-Imtiyaz Educational & Operational Management Platform.

This repository **replaces** the legacy split between [`El-Emtyaze-Mobile`](https://github.com/Vtheonly/El-Emtyaze-Mobile) (Staff app) and [`El-Emtyaze-Mobile-Trafic`](https://github.com/Vtheonly/El-Emtyaze-Mobile-Trafic) (School Route Finder), merging their functionality into one cohesive app while following the master plan at [`AgentGithubUplaod`](https://github.com/Vtheonly/AgentGithubUplaod).

> **No code is copied** from the legacy apps. They were studied for patterns, screens and UX, then a brand-new implementation was written that improves on every weakness identified in the analysis.

---

## 1. Highlights

- **Clean 5-module Gradle architecture**: `:core`, `:domain`, `:data`, `:feature`, `:app`
- **Modern Android stack**: Kotlin 2.0.21, AGP 8.7.3, Compose + Material 3, minSdk 26 / targetSdk 35
- **Hilt** for dependency injection across all modules
- **Supabase** Kotlin SDK (Postgrest + Auth + Realtime + Storage + Functions) with **automatic mock fallback** when no Supabase keys are configured — the app is fully demoable offline
- **Room** offline cache + **WorkManager** sync queue with exponential backoff
- **CameraX** proof capture, **Coil** image loading, **osmdroid** maps
- **Dark-first Material 3 theme** using the canonical El-Imtiyaz color palette and design tokens
- **39 navigation routes** covering every mobile-relevant workflow from the master plan
- **9 feature packages** organized under a single `:feature` module
- **Bilingual-ready** with French primary + Arabic strings

---

## 2. What's implemented

### Hub 1 — Dashboard (`feature/dashboard/`)
- Dashboard KPI grid (students, parents, monthly revenue, outstanding debt)
- Revenue last 12 months bar chart (Compose Canvas — no chart library)
- Debt by aging bucket chart (5 buckets)
- Demographics donut chart (Primaire / CEM / Lycée)
- Alerts notification center with filters + mark-read
- Global search across parents, students, payments, expenses with debounce
- Reports catalog with share intent (PDF generation is desktop-only per plan §13)

### Hub 2 — Roster / CRM (`feature/crm/`)
- Parents list with avatars, codes (PAR-…), student count badges
- Students list with level + class
- Parent detail: header card, quick actions (Call/WhatsApp/Email), associated students, recent payments, installments, account adjustments
- Student detail: tabs for Infos / Académique / Présences / Paiements
- **Dynamic batch registration wizard** — atomic transaction for parent + unlimited children per plan §04.03
- Bottom sheet parent profile preview

### Hub 3 — Academics (`feature/academics/`)
- Classes directory grouped by level (Primaire 5y / CEM 4y / Lycée 3y)
- Subjects directory with code, level, coefficient, extracurricular flag
- Class detail with roster, subjects, attendance summary, recent grades
- **30-second roll call** (plan §09.01) — bulk status set, one-click "Tous présents", 3-absence auto-alert trigger
- **Grade entry table** — D1, D2, Examen with live subject average = `(D1+D2+2·Examen)/4` and class GPA
- Homework push engine with attachment support

### Hub 4 — Financials (`feature/financials/`)
- Hub with 4 KPI cards + 3-tab TabRow (Paiements / Dépenses / Créances)
- **Counter payment** — Cash / Check / Transfer with mandatory proof capture for Check & Transfer (plan §18.03), auto-allocates to oldest installment, generates PDF receipt
- Payment detail with proof image, refund action (RBAC-gated)
- Installment schedule per parent
- **Debt dashboard** with aging buckets chart, top-20 debtors, WhatsApp reminder intent
- **Two-tier expense workflow** — Submit → Approve/Reject → Disburse → Settle-Proof (with camera capture)
- Expense anomaly score badge

### Hub 5 — Personnel (`feature/personnel/`)
- Personnel directory by category (Teacher / Administration / Support / Maintenance / Driver)
- Personnel detail with weekly hours bar chart
- **Relevé clock-in/out ledger** (plan §09.05)
- **Audit log** with action/entity/actor filters + expandable JSON diff (Mono font)
- Read-only workflow monitor (DAG canvas editor is desktop-only per plan §13)

### Driver Mode — Routing (`feature/routing/`)
Merged from the legacy School Route Finder app, significantly upgraded:
- Vehicle list with capacity, wheelchair-lift matching, driver assignment
- **Real OSRM API integration** for road-network routing (replaces straight-line Haversine from the legacy app) with graceful fallback to Haversine + custom nearest-neighbor + 2-opt TSP solver (max 50 iterations)
- Full-screen osmdroid map with numbered stop markers, polyline, live vehicle position
- **Foreground service** for background GPS tracking with sticky notification
- Real FusedLocationProviderClient (not simulated)
- Drag-to-reorder stops with persistence
- Trip history

### Auth (`feature/auth/`)
- Login (email + password) with "remember me"
- Account activation via OTP
- Forgot password flow
- **Web Portal Redirect** for parent/student sessions (per plan §01.06 — parents and students use the Web Portal, not this app)

### Settings & Profile (`feature/settings/`)
- Appearance: theme mode (System/Light/Dark), dynamic colors
- Language: French / Arabic
- Notifications: per-category toggles
- Sync: status, pending count, "Sync now", mock-mode toggle
- Security: change password, biometric auth stub, session expiry
- About + bug report (email intent)
- **Mobile Backup Prohibition** notice per plan §13.05
- **Locked features card** — lists all permanently-disabled features (removed AI assistant, desktop-only DAG editor / Excel import / backup) rendered greyed-out with lock icons
- Profile: avatar, role badge, permissions grid, recent activity, sign-out

> The AI Assistant feature (chat, report narrative generator, expense anomaly
> detector) has been **removed** from this version. Its entries are kept in
> `FeatureRegistry.PermanentlyDisabled` and rendered as greyed-out rows in
> the Settings → Locked Features card so users see that the feature existed.

---

## 3. Feature gating & RBAC architecture

The app is designed so that **any** UI node — section, option, page, action, or
cross-cutting feature — can be declared once in a central `FeatureRegistry` and
gated by access rules without touching the screen that renders it.

**Disabled nodes appear greyed-out (visible but locked), never hidden** — per the
platform requirement. This applies to:
- Removed features (rendered with a "Fonctionnalité retirée" badge)
- Desktop-only features (DAG editor, Excel import, backup — rendered with a "Disponible sur le terminal de bureau" badge)
- Features the current user's role cannot access (rendered with a lock icon)

**The three-layer system** (all in `:core/rbac/`):

1. `FeatureRegistry` — the canonical Section → Option → Page/Action tree, every
   node carrying an `AccessRequirement`. Single source of truth.
2. `FeatureGate` — a pure, stateless evaluator: `evaluate(node, session, flags) → AccessState`.
3. Compose helpers — `GatedContent`, `GatedFloatingActionButton`, `GatedIconButton`,
   `GatedNavigationBarItem`, plus `DisabledOverlay` / `DisabledInline` / `DisabledPlaceholder`
   visual treatments.

**Rule**: screens never call `session.can(Permission.X)` directly. They call
`accessStateOf(node)` or wrap content in `GatedContent(node) { ... }`. This
keeps all gating rules in one file (`FeatureRegistry.kt`) so future RBAC changes
(new role, paid plan, A/B test, feature removal) are a one-file edit.

The 5 bottom-nav tabs already use `GatedNavigationBarItem` — a user without
`ViewFinancials` sees the Financials tab greyed-out with a lock icon. See
`ARCHITECTURE.md §5.1` for the full design.

---

## 4. Module layout

```
elimtiyaz-android/
├── settings.gradle.kts          ← 5-module include
├── build.gradle.kts             ← plugin aliases only
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml       ← version catalog
│   └── wrapper/
│       └── gradle-wrapper.properties
├── local.properties.example     ← Supabase keys + SDK location
├── ARCHITECTURE.md              ← READ THIS FIRST
├── FEATURE_SPEC.md              ← feature module contract
├── README.md                    ← this file
│
├── core/                        ← design system + common utilities + UI components
│   └── src/main/java/com/elimtiyaz/core/
│       ├── designsystem/        ← Color, Typography, Theme, Spacing
│       ├── common/              ← Result, AppError, Session, Role, Permission, AuditContext, Formatters, status enums
│       └── ui/                  ← StateViews, StatusChip, Card
│
├── domain/                      ← pure Kotlin: models + repository contracts
│   └── src/main/java/com/elimtiyaz/domain/
│       ├── model/               ← Parent, Student, Academic, Payment, Expense, Operations, Routing
│       └── repository/          ← 9 repository contracts
│
├── data/                        ← Supabase + Room + DataStore + mock fallback
│   └── src/main/java/com/elimtiyaz/data/
│       ├── remote/              ← SupabaseClient, DTOs
│       ├── local/               ← Room database, DAOs, entities
│       ├── repository/          ← 9 SupabaseXxxRepository implementations
│       ├── mock/                ← 7 MockXxxRepository with realistic seed data
│       ├── sync/                ← SyncQueueWorker (WorkManager)
│       └── di/                  ← NetworkModule, DatabaseModule, DataModule, DispatchersModule, SettingsModule
│
├── feature/                     ← 9 feature packages, 84 Kotlin files
│   └── src/main/java/com/elimtiyaz/feature/
│       ├── auth/                ← LoginScreen, ActivationScreen, ForgotPasswordScreen, WebPortalRedirectScreen
│       ├── dashboard/           ← DashboardScreen, AlertsScreen, GlobalSearchScreen, ReportsScreen
│       ├── crm/                 ← RosterScreen, ParentDetailScreen, StudentDetailScreen, BatchRegistrationScreen
│       ├── academics/           ← AcademicsHub, ClassDetail, RollCall, GradeEntry, HomeworkPush
│       ├── financials/          ← FinancialsHub, CounterPayment, PaymentDetail, Installments, DebtDashboard, ExpenseDetail, ExpenseSubmit
│       ├── personnel/           ← PersonnelHub, PersonnelDetail, Releve, AuditLog, WorkflowMonitor
│       ├── routing/             ← RoutingScreen, RoutingMapScreen, TripHistory, ForegroundService, TspSolver, OsrmClient
│       └── settings/            ← SettingsScreen, ProfileScreen, LockedFeaturesCard
│
└── app/                         ← Application, MainActivity, NavHost, DI
    └── src/main/java/com/elimtiyaz/app/
        ├── ElImtiyazApp.kt      ← @HiltAndroidApp + WorkManager bootstrap
        ├── MainActivity.kt
        ├── di/AppModule.kt
        └── navigation/          ← Routes sealed class + ElImtiyazNavHost
```

**Totals**: 156 Kotlin files, 12 XML resources, 39 navigation routes.

---

## 5. Build & run

### Prerequisites
- **Android Studio Ladybug (2024.2)** or newer
- **JDK 17** (bundled with Android Studio)
- **Android SDK** with platform 35 + build-tools 35.0.0
- **Kotlin 2.0.21** (auto-fetched by Gradle)
- **Internet connection** for first build (downloads dependencies)

### Setup
1. **Clone or copy** this entire `elimtiyaz-android/` directory to your machine.
2. **Generate the Gradle wrapper** (this repo ships the properties file but not the wrapper JAR):
   ```bash
   cd elimtiyaz-android
   gradle wrapper --gradle-version 8.10.2
   ```
   (If you don't have `gradle` installed, install it via `brew install gradle` on macOS, `sdk install gradle` via SDKMAN on Linux, or download from https://gradle.org/install/.)
3. **Copy `local.properties.example` to `local.properties`** and edit:
   ```properties
   sdk.dir=/path/to/Android/Sdk
   # Optional — if absent, the app runs in mock mode with seed data
   SUPABASE_URL=https://your-project.supabase.co
   SUPABASE_ANON_KEY=your-anon-key
   ```
4. **Open in Android Studio**: File → Open → select the `elimtiyaz-android/` folder.
5. **Let Gradle sync** (first sync downloads ~250 MB of dependencies).
6. **Run** the `app` configuration on an emulator or device (Android 8.0+).

### Mock mode (no Supabase required)
If you skip step 3's Supabase keys, the app starts in **mock mode** with realistic seed data:
- 8 parents, 15 students, 6 classes, 12 subjects
- 30 payments, 5 expenses, 10 personnel, 12 routing stops
- All screens are fully demoable

**Demo credentials** (mock mode only):
| Role              | Email                      | Password   |
| ----------------- | -------------------------- | ---------- |
| Super Admin       | `admin@elimtiyaz.dz`       | `admin123` |
| Financial Officer | `financial@elimtiyaz.dz`   | `fin123`   |
| Teacher           | `teacher@elimtiyaz.dz`     | `teach123` |
| Driver            | `driver@elimtiyaz.dz`      | `drive123` |

---

## 6. Architecture summary

```
┌─────────────────────────────────────────────────────────────────┐
│ :app — ElImtiyazApp (Hilt) + MainActivity + ElImtiyazNavHost    │
│       Single NavHost, 5-tab bottom scaffold, 39 routes          │
└──────────┬──────────────────────────────────────────────────────┘
           │ depends on
           ▼
┌─────────────────────────────────────────────────────────────────┐
│ :feature — 9 packages (auth, dashboard, crm, academics,         │
│            financials, personnel, routing, ai, settings)        │
│   Each screen: @HiltViewModel + UiState + collectAsStateWithLifecycle │
└──────┬───────────────────────────────────┬──────────────────────┘
       │ depends on                        │ depends on
       ▼                                   ▼
┌──────────────────────────┐    ┌────────────────────────────────┐
│ :domain (pure Kotlin)    │    │ :data (Supabase + Room + Mock) │
│  - models                │    │  - SupabaseClient              │
│  - repository contracts  │◄───┤  - SupabaseXxxRepository impls │
│  - use cases (in repos)  │    │  - MockXxxRepository (fallback)│
└──────────────────────────┘    │  - Room cache + SyncQueueWorker│
       ▲                        │  - Hilt DI modules             │
       │                        └────────────────────────────────┘
       │ depends on                        ▲
       │                                    │ depends on
┌──────┴───────────────────┐                │
│ :core (Compose + pure)   │────────────────┘
│  - designsystem (tokens) │
│  - common (Result, etc.) │
│  - ui (StateViews, etc.) │
└──────────────────────────┘
```

### Key patterns
- **Unidirectional UiState**: every screen exposes `data class XxxUiState` + `@HiltViewModel`; mutations are explicit VM functions.
- **Offline-first reads**: `Flow` first emits Room cache, then fetches from Supabase, persists, emits fresh.
- **Atomic writes**: every mutating call wraps in `Result.runCatching` and queues to `SyncQueue` on failure.
- **Universal audit**: every mutation calls `AuditRepository.log(...)` after success.
- **RBAC**: `Session.can(Permission.X)` gates FABs, menu items, and entire screens.
- **Mobile Backup Prohibition**: `android:allowBackup="false"` + `dataExtractionRules.xml` excludes everything.

---

## 7. Tech stack

| Concern              | Choice                                            |
| -------------------- | ------------------------------------------------- |
| UI                   | Jetpack Compose + Material 3                      |
| Min SDK              | 26 (Android 8.0)                                  |
| Target SDK           | 35                                                |
| Kotlin               | 2.0.21                                            |
| AGP                  | 8.7.3                                             |
| DI                   | Hilt 2.52                                         |
| Navigation           | Compose Navigation 2.8.4                          |
| Async                | Coroutines 1.9.0 + Flow                           |
| Backend              | Supabase Kotlin SDK 3.0.3 (Postgrest + Auth + Realtime + Storage + Functions) |
| HTTP                 | Ktor Client 3.0.3                                 |
| Local cache          | Room 2.6.1 + DataStore Preferences 1.1.1          |
| Image                | Coil 2.7.0                                        |
| Camera               | CameraX 1.4.0                                     |
| Maps                 | osmdroid 6.1.20                                   |
| Routing              | OSRM public API + custom TSP (nearest-neighbor + 2-opt) |
| Background work      | WorkManager 2.10.0 + Hilt-Work                    |
| Date/time            | kotlinx-datetime 0.6.1                            |
| Serialization        | kotlinx-serialization 1.7.3                       |
| Logging              | Kermit 2.0.4                                      |
| Testing              | JUnit4 + Turbine + MockK + Coroutines-Test        |

---

## 8. What is NOT on mobile (by design)

Per the master plan, the following stay **desktop-only**:

- Visual DAG workflow canvas editor (touchscreen-impractical)
- `.xlsx` student bulk import / export
- Local database backup generation (**prohibited** on mobile per §13.05)
- RBAC matrix configuration UI
- Point-in-time restoration UI

The app shows informative "available on desktop" notices for these.

---

## 9. Roadmap (out of scope for v1)

- Biometric authentication (Keystore + BiometricPrompt) — UI stub exists
- Real FCM push notifications — repository contract exists, wiring needed
- Dynamic color (Material You) — UI toggle exists, full implementation requires `dynamicColorScheme()`
- Locale switching without Activity.recreate() — requires Compose-localized string resolution
- OR-Tools VRP for multi-vehicle routing with capacity constraints
- Offline encrypted media vault (currently uses Supabase Storage signed URLs)

---

## 10. License

Proprietary — © El-Imtiyaz. All rights reserved.

---

## 11. Acknowledgements

Built using the master plan at https://github.com/Vtheonly/AgentGithubUplaod as the single source of truth, with the legacy apps https://github.com/Vtheonly/El-Emtyaze-Mobile and https://github.com/Vtheonly/El-Emtyaze-Mobile-Trafic studied for reference and inspiration. No code was copied from either legacy app.
