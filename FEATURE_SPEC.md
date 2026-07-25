# Feature Module Spec — El-Imtiyaz Android

This document is the shared contract every feature module MUST follow.
It complements `/home/z/my-project/download/elimtiyaz-android/ARCHITECTURE.md`.

## 1. Where feature code lives

```
/home/z/my-project/download/elimtiyaz-android/feature/src/main/java/com/elimtiyaz/feature/<name>/
```

Each feature is one package. Files in the package:

| File                    | Purpose                                                 |
| ----------------------- | ------------------------------------------------------- |
| `<Name>ViewModel.kt`    | Hilt ViewModel + UiState data class                     |
| `<Name>Screen.kt`       | Root Composable for the screen (called by NavGraph)     |
| `<Name>Graph.kt`        | `NavGraphBuilder.<name>Graph(nav: NavController)` extension |
| Sub-screens as needed   | One file per screen                                     |
| Components as needed    | Reusable composables specific to the feature            |

## 2. The UiState pattern

Every screen exposes a single `data class XxxUiState` and a single
`@HiltViewModel class XxxViewModel`. Screens collect state with
`collectAsStateWithLifecycle()`. Mutations are explicit functions on the VM,
not callbacks passed down.

```kotlin
data class DashboardUiState(
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val kpis: DashboardKpi? = null,
    val revenueSeries: List<RevenuePoint> = emptyList(),
    val debtByAging: List<DebtByAgingBucket> = emptyList(),
    val demographics: List<DemographicSlice> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboard: DashboardRepository,
    private val notifications: NotificationRepository,
    private val session: SessionProvider,  // or @SessionParam Session
) : ViewModel() { ... }
```

## 3. The NavGraph pattern

Each feature exposes ONE top-level extension on `NavGraphBuilder`:

```kotlin
fun NavGraphBuilder.dashboardGraph(nav: NavController) {
    composable(Route.Dashboard.route) { DashboardScreen(nav) }
    composable(Route.Alerts.route) { AlertsScreen(nav) }
    composable(Route.GlobalSearch.route) { GlobalSearchScreen(nav) }
    composable(Route.Reports.route) { ReportsScreen(nav) }
}
```

Sub-screens navigate via `nav.navigate(Route.X.build(id))` and pop via `nav.popBackStack()`.

## 4. The Routes available

Routes are defined in `/home/z/my-project/download/elimtiyaz-android/app/src/main/java/com/elimtiyaz/app/navigation/Routes.kt`. The Route sealed class is your source of truth. If you need a NEW route, ADD it to that file via the Edit tool (do not duplicate the sealed class).

## 5. Design system you MUST use

Import from `com.elimtiyaz.core.designsystem`:
- `ElimtiyazTheme` — applies automatically from the root
- `ElimtiyazColors` — `PrimaryBlue`, `DeepBlue`, `LightBlue`, `SlateGray`,
  `WarmGold`, `SuccessGreen`, `DangerRed`, `DarkBackground`, `PanelBackground`,
  `ElevatedSurface`, `OffWhite`
- `ElimtiyazSpacing` — `x1` (4dp), `x2`, `x3`, `x4`, `x6`, `x8`, `x12`
- `ElImtiyazRadius` — `sm`, `md`, `lg`, `xl`, `pill`

Import from `com.elimtiyaz.core.ui`:
- `LoadingState()`, `ErrorState(error, onRetry)`, `EmptyState(title, description)`
- `OfflineBanner(pendingCount)`
- `AsyncContent(isLoading, error, items, ...) { ... }`
- `StatusChip(label, tone)` where `tone ∈ {Success, Warning, Danger, Info, Neutral}`
- `ElImtiyazCard(onClick?) { ... }`, `AvatarCircle(initial)`, `ListRow(leading, title, subtitle, trailing)`

Import from `com.elimtiyaz.core.common`:
- `Result`, `AppError`, `Session`, `Role`, `Permission`, `AuditContext`
- `Formatters.currency(amount)`, `Formatters.date(iso)`, `Formatters.dateTime(iso)`,
  `Formatters.initials(first, last)`, `Formatters.fullName(first, last)`
- `PaymentStatus`, `PaymentMethod`, `AttendanceStatus`, `ExpenseStatus`,
  `AcademicLevel`, `TenancyTier` — all with `.from(key)` and `.displayFr`/`.displayAr`

## 6. Status tone mapping (for chips)

| Semantic state      | Tone     |
| ------------------- | -------- |
| Paid / Present / Approved / Settled / Promoted / Active / Success | `Success` |
| Partial / Pending / Late / Draft / Submitted / Disbursed / OnLeave | `Warning` (or `Info`) |
| Overdue / Rejected / Anomaly / AbsentUnexcused / Suspended / Cancelled | `Danger` |
| Info / Neutral / New / Note | `Info` or `Neutral` |

## 7. Domain repositories available (constructor-injected via Hilt)

From `:domain`:
- `AuthRepository` — `session: Flow<Session?>`, `signIn`, `signOut`, etc.
- `ParentRepository`, `StudentRepository`
- `ClassRepository`, `SubjectRepository`, `GradeRepository`,
  `AttendanceRepository`, `HomeworkRepository`
- `PaymentRepository`, `InstallmentRepository`, `DebtRepository`
- `ExpenseRepository`, `PersonnelRepository`, `ReleveRepository`,
  `AuditRepository`, `DashboardRepository`
- `RoutingRepository`
- `NotificationRepository`, `AiRepository`, `SettingsRepository`

## 8. Session access

Inject `AuthRepository` into your ViewModel and observe `auth.session: Flow<Session?>`.
For one-shot reads inside callbacks, expose a `currentSession: Session?` field on
the VM that mirrors the latest session value.

To check permission: `session.can(Permission.CollectPayment)` — gate FABs and
menu items in the Composable.

## 9. Offline-first read pattern

```kotlin
fun load() = viewModelScope.launch {
    _uiState.update { it.copy(isLoading = true, error = null) }
    dashboard.kpis().collect { result ->
        when (result) {
            is Result.Success -> _uiState.update { it.copy(isLoading = false, kpis = result.data) }
            is Result.Failure -> _uiState.update { it.copy(isLoading = false, error = result.error) }
        }
    }
}
```

Call `load()` from `init { }`. Use `repeatOnLifecycle(STARTED)` in the screen via
`collectAsStateWithLifecycle()`.

## 10. Camera capture workflow (for proof/homework/photo screens)

Use `rememberLauncherForActivityResult(ActivityResultContracts.TakePicture())`
with a temporary `Uri` from `FileProvider`. Save the resulting URI to the
repository; the repository uploads it to Supabase Storage and returns the
signed URL. For the CameraX live preview (e.g. continuous proof scanner),
use `androidx.camera.view.PreviewView` inside an `AndroidView`.

For permissions, use `com.google.accompanist.permissions.rememberPermissionState`.

## 11. Bottom-tab vs full-screen

The 5 hub routes get the bottom navigation bar automatically (the Scaffold in
`ElImtiyazNavHost` shows the bar when current route ∈ HubRoutes). All other
routes render full-screen with their own `Scaffold` + `TopAppBar` carrying
a back button.

## 12. Strings

UI strings should be inline French by default (the platform's primary language).
Bilingual AR/FR is a stretch goal — use simple inline French strings for now.
Shared strings live in `core/src/main/res/values/strings.xml` and `values-ar/`.

## 13. Naming and style

- Composables: `PascalCase` — `ParentDetailScreen`, `PaymentCard`, `InstallmentRow`.
- ViewModels: `<Screen>ViewModel`.
- UiState: `<Screen>UiState`.
- Use `Modifier.padding(ElimtiyazSpacing.x4)` not raw `dp` values.
- Use `MaterialTheme.colorScheme.*` for colors. Use `ElimtiyazColors.*` only
  for tokens not exposed by Material (e.g. status colors).
- Every public function has a KDoc comment.

## 14. Quality bar

- Every screen handles loading, error, and empty states (use `AsyncContent`).
- Every screen has a TopAppBar with a back button (except hub tabs).
- Every screen is fully scrollable (wrap content in `LazyColumn` or `verticalScroll`).
- Every form has validation and inline error messages.
- Every list item is tappable to a detail screen.
- No `TODO()` placeholders.
- No hardcoded user IDs — always read from `Session`.
