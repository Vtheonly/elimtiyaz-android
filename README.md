# El-Imtiyaz Design System v2 — "Electric Violet & Sunshine"

A complete, opinionated Jetpack Compose design system for the El-Imtiyaz Staff
Android app — **v2 refactored architecture** with strict SRP, small focused
files, and a clear hierarchical folder structure.

**Visual identity:** "Electric Violet & Sunshine" — a confident, saturated
violet primary paired with a high-contrast amber accent and a playful pink
tertiary. Heavy display typography, chunky 24dp card corners, tinted shadows,
and springy press-scale motion give the app its distinctive character.

**Compatibility:** minSdk 24 · targetSdk 36 · Jetpack Compose BOM · Material 3

---

## What changed in v2

| Metric              | v1       | v2       | Improvement             |
|---------------------|----------|----------|-------------------------|
| Kotlin files        | 34       | 76       | 2.2× more files         |
| Largest file        | 430 lines| 195 lines| 55% smaller             |
| Files > 200 lines   | 8        | **0**    | All under guideline     |
| Avg file size       | 138 lines| 73 lines | 47% smaller             |
| Reusable abstractions | 2      | 6        | 3× more shared logic    |

**Key refactors:**

- **Theme split** — `Theme.kt` (360 lines) → 4 focused files: `ElColors.kt` (data class), `ElColorSchemes.kt` (light/dark instances + Material mapper), `ElTheme.kt` (accessor object), `Theme.kt` (entry composable)
- **Type split** — `Type.kt` (190 lines) → `Typography.kt` (Material 3 scale) + `ElTextStyles.kt` (extended styles)
- **Button split** — `ElButton.kt` (280 lines) → 5 files: `ElButton` + `ElIconButton` + `ElFab` + `ButtonTypes` + `ButtonStyleResolver`
- **Card split** — `ElCard.kt` (190 lines) → 4 files: `ElCard` + `ElStatCard` + `CardTypes` + `CardStyleResolver`
- **Navigation split** — `ElNavigation.kt` (250 lines) → 5 files: `ElBottomBar` + `ElNavRail` + `ElTopBar` + `NavItem` + `NavTypes`
- **Dialog split** — `ElDialog.kt` (165 lines) → `ElDialogShell` + `ElDialogContent`
- **BottomSheet split** — `ElBottomSheet.kt` (140 lines) → `ElBottomSheet` + `ElSheetContent`
- **Gallery split** — `ElGalleryScreen.kt` (430 lines) → shell + 5 tab files under `gallery/tabs/`
- **New reusable abstractions** — `noRippleClickable` + `pressClickable` helpers in `foundation/Clickable.kt` eliminate ~30 repetitions of clickable boilerplate across components

---

## Architecture

The design system follows a strict **layered architecture** with one-way
dependency flow:

```
┌─────────────────────────────────────────────────────────────┐
│  gallery/            (showcase — depends on everything)     │
├─────────────────────────────────────────────────────────────┤
│  overlays/           (dialogs, sheets, menus, toasts)       │
│  components/         (buttons, cards, inputs, nav, etc.)    │
├─────────────────────────────────────────────────────────────┤
│  foundation/         (modifiers, clickable helpers)         │
├─────────────────────────────────────────────────────────────┤
│  theme/              (color, type, shape, motion tokens)    │
└─────────────────────────────────────────────────────────────┘
```

**Dependency rules:**
- `theme/` depends on nothing but Compose/Material
- `foundation/` depends only on `theme/`
- `components/` depends on `theme/` and `foundation/`
- `overlays/` depends on `theme/`, `foundation/`, and `components/`
- `gallery/` depends on everything (it's a showcase)

No layer depends upward. No circular dependencies.

---

## Folder structure

```
app/src/main/java/com/example/ui/designsystem/
├── ElDesignSystem.kt                  # Version metadata + public API barrel
│
├── theme/                             # Design tokens (12 files)
│   ├── Color.kt                       # Raw color literals (light + dark)
│   ├── ElColors.kt                    # ElColors data class + LocalElColors
│   ├── ElColorSchemes.kt              # LightElColors, DarkElColors, toMaterialScheme()
│   ├── ElTheme.kt                     # Accessor object (single entry point)
│   ├── Theme.kt                       # ElImtiyazTheme composable + edge-to-edge
│   ├── Typography.kt                  # Material 3 typography scale
│   ├── ElTextStyles.kt                # Extended styles (numeric, overline, etc.)
│   ├── Shape.kt                       # Shape tokens + semantic shapes
│   ├── Spacing.kt                     # 4dp spacing grid
│   ├── Elevation.kt                   # Tinted shadow specs
│   ├── Motion.kt                      # Spring specs + tween easings
│   └── Borders.kt                     # Border width tokens
│
├── foundation/                        # Reusable modifiers (5 files)
│   ├── Clickable.kt                   # noRippleClickable, pressClickable (NEW)
│   ├── ShadowModifier.kt              # elShadow with tinted blur
│   ├── PressScaleModifier.kt          # Animated press-scale
│   ├── GlassModifier.kt               # Frosted-glass surface
│   └── BorderModifier.kt              # elBorder, elGradientBackground
│
├── components/
│   ├── button/                        # Buttons (5 files)
│   │   ├── ButtonTypes.kt             # ElButtonVariant, ElButtonSize, sizing helpers
│   │   ├── ButtonStyleResolver.kt     # Variant → ButtonStyle mapping
│   │   ├── ElButton.kt                # Main button composable
│   │   ├── ElIconButton.kt            # Icon-only button
│   │   └── ElFab.kt                   # Floating action button
│   │
│   ├── card/                          # Cards (4 files)
│   │   ├── CardTypes.kt               # ElCardVariant, ElCardSize, padding/shape helpers
│   │   ├── CardStyleResolver.kt       # Variant → CardStyle mapping
│   │   ├── ElCard.kt                  # Main card composable
│   │   └── ElStatCard.kt              # KPI stat card
│   │
│   ├── input/                         # Inputs (7 files)
│   │   ├── TextFieldTypes.kt          # ElTextFieldState, resolveFieldState()
│   │   ├── FieldStyleResolver.kt      # State → bg/border color
│   │   ├── FieldParts.kt              # Label, leading icon, input, trailing, helper
│   │   ├── ElTextField.kt             # Main text field composable
│   │   ├── DropdownOption.kt          # ElDropdownOption data class
│   │   ├── DropdownParts.kt           # Trigger, popup, option row
│   │   └── ElDropdown.kt              # Main dropdown composable
│   │
│   ├── nav/                           # Navigation (5 files)
│   │   ├── NavTypes.kt                # ElNavDestination
│   │   ├── NavItem.kt                 # Shared nav item (icon + label + badge)
│   │   ├── ElBottomBar.kt             # Bottom navigation bar
│   │   ├── ElNavRail.kt               # Side navigation rail
│   │   └── ElTopBar.kt                # Top app bar
│   │
│   ├── display/                       # Display components (9 files)
│   │   ├── BadgeTypes.kt              # ElBadgeTone, ElBadgeStyle
│   │   ├── BadgePalettes.kt           # Tone → BadgePalette mapping
│   │   ├── ElBadge.kt                 # Badge composable
│   │   ├── AvatarTypes.kt             # ElAvatarSize, sizing helpers
│   │   ├── ElAvatar.kt                # Avatar composable
│   │   ├── ElDivider.kt               # Divider + section divider
│   │   ├── ChipTypes.kt               # ElChipVariant
│   │   ├── ElChip.kt                  # Chip composable
│   │   └── ElChipGroup.kt             # Chip group layout
│   │
│   ├── data/                          # Data display (5 files)
│   │   ├── TableTypes.kt              # ElColumnAlign, ElTableColumn, ElTableRow
│   │   ├── TableHeader.kt             # Header row with sort indicators
│   │   ├── TableBodyRow.kt            # Body row with dividers
│   │   ├── ElTable.kt                 # Main table composable
│   │   └── ElList.kt                  # List item composable + parts
│   │
│   ├── feedback/                      # Loading & feedback (4 files)
│   │   ├── ElProgress.kt              # Linear progress + spinner
│   │   ├── ElLoading.kt               # Linear loader + loading block
│   │   ├── ElSkeleton.kt              # Skeleton box, circle, line, card
│   │   └── ElEmptyState.kt            # Empty state with icon + CTA
│   │
│   └── tabs/
│       └── ElTabs.kt                  # Tab row + vertical tab list
│
├── overlays/                          # Modals & popups (10 files)
│   ├── ElDialogShell.kt               # Base dialog shell (scrim, motion, elevation)
│   ├── ElDialogContent.kt             # Standard content layout (title/body/actions)
│   ├── ElConfirmationDialog.kt        # Pre-built confirm/cancel dialog
│   ├── ElBottomSheet.kt               # Bottom sheet shell
│   ├── ElSheetContent.kt              # Standard sheet content layout
│   ├── ContextMenuTypes.kt            # ElContextMenuItem
│   ├── ElContextMenu.kt               # Context menu composable + item rows
│   ├── ElTooltip.kt                   # Auto-dismissing tooltip
│   ├── ToastTypes.kt                  # ElToastTone, toastAccent()
│   └── ElToast.kt                     # Top-of-screen toast
│
└── gallery/                           # Living showcase (8 files)
    ├── ElGalleryActivity.kt           # Standalone launcher activity
    ├── ElGalleryScreen.kt             # Shell + tab switcher
    ├── GallerySection.kt              # Section helper
    └── tabs/
        ├── FoundationsTab.kt          # Colors, surfaces, typography, spacing
        ├── ButtonsTab.kt              # All button variants & states
        ├── InputsTab.kt               # Text fields, dropdowns, chips, badges
        ├── SurfacesTab.kt             # Cards, lists, avatars, table, progress
        └── OverlaysTab.kt             # Dialogs, sheets, menus, dividers
```

**Total: 76 Kotlin files · ~5,600 lines · largest file 195 lines.**

---

## Design principles applied

### Single Responsibility Principle

Every file does **one thing**:

- `ButtonTypes.kt` — only defines button enums and size helpers
- `ButtonStyleResolver.kt` — only maps variant → style
- `ElButton.kt` — only the button composable itself
- `Clickable.kt` — only the no-ripple clickable modifier

### No god classes / god files

Before: `ElButton.kt` had 280 lines containing 3 composables, 4 enums, 4
helper functions, and inline color-resolution logic.

After: split into 5 files, each ≤123 lines. The composables are thin shells
that delegate styling to `ButtonStyleResolver` and sizing to `ButtonTypes`.

### Reusable abstractions

The `noRippleClickable` and `pressClickable` modifiers in
`foundation/Clickable.kt` eliminate ~30 repetitions of:

```kotlin
Modifier.clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    role = Role.Tab,
    onClick = onClick,
)
```

…across the codebase. Every interactive component now uses one of these
helpers.

Style resolvers (`ButtonStyleResolver`, `CardStyleResolver`,
`BadgePalettes`, `FieldStyleResolver`, `ToastTypes.toastAccent`) extract
variant → appearance mapping into testable pure functions, keeping
composables thin.

### Clean dependency direction

- `theme/` is the leaf — depends on nothing internal
- `foundation/` depends only on `theme/`
- `components/` depend on `theme/` and `foundation/`
- `overlays/` depend on `theme/`, `foundation/`, `components/`
- `gallery/` depends on everything

No layer reaches upward. No circular dependencies.

### Naming conventions

- **Files**: PascalCase matching the primary composable or class (`ElButton.kt`, `ButtonTypes.kt`)
- **Composables**: `El` prefix for all design system components (`ElButton`, `ElCard`, `ElDialog`)
- **Enums**: `El` prefix + noun (`ElButtonVariant`, `ElCardSize`, `ElBadgeTone`)
- **Style resolvers**: `resolve*Style()` (e.g. `resolveButtonStyle`, `resolveCardStyle`)
- **Token accessors**: `ElTheme.colors`, `ElTheme.spacing`, etc. (single object)
- **Internal helpers**: `internal` visibility, private composables for parts

---

## Installation

### Drop-in (recommended)

1. Unzip this archive at the repo root. It places the `designsystem/`
   package under `app/src/main/java/com/example/ui/`.

2. Register the gallery activity in `AndroidManifest.xml` (optional, for preview):

   ```xml
   <activity
       android:name="com.example.ui.designsystem.gallery.ElGalleryActivity"
       android:exported="false"
       android:theme="@style/Theme.MyApplication" />
   ```

3. Launch the gallery via adb:

   ```bash
   adb shell am start -n com.aistudio.elimtiyazstaff.bxmzlx/com.example.ui.designsystem.gallery.ElGalleryActivity
   ```

### Full migration

Replace the theme import in `MainActivity.kt`:

```kotlin
// Before
import com.example.ui.theme.ElImtiyazTheme

// After
import com.example.ui.designsystem.theme.ElImtiyazTheme
```

Both themes are named `ElImtiyazTheme` and accept the same parameters, so
this is a one-line import swap.

---

## Usage

### Read tokens via `ElTheme`

```kotlin
import com.example.ui.designsystem.theme.ElTheme

@Composable
fun MyScreen() {
    val c = ElTheme.colors          // ElColors
    val s = ElTheme.spacing         // ElSpacing
    val e = ElTheme.elevation       // ElElevation
    val m = ElTheme.motion          // ElMotion
    val t = ElTheme.typography      // Material 3 Typography
    val ts = ElTheme.textStyles     // ElTextStyles (numeric, overline, etc.)
    val sh = ElTheme.shapes         // Material 3 Shapes

    Box(
        Modifier
            .background(c.surface)
            .padding(s.lg)
    ) { /* ... */ }
}
```

### Use components

```kotlin
import com.example.ui.designsystem.components.button.*
import com.example.ui.designsystem.components.card.*

@Composable
fun PaymentSummary(state: PaymentState) {
    val c = ElTheme.colors
    Column(Modifier.padding(16.dp)) {
        ElStatCard(
            label = "Outstanding",
            value = "DZD ${state.balance}",
            icon = Icons.Default.Warning,
            trend = state.trendLabel,
            trendPositive = state.trendPositive,
            accentColor = if (state.isOverdue) c.danger else c.success,
        )
        Spacer(Modifier.height(12.dp))
        ElButton(
            text = "Record Payment",
            onClick = state::onPay,
            variant = ElButtonVariant.PRIMARY,
            icon = Icons.Default.Check,
            fullWidth = true,
            loading = state.isSubmitting,
        )
    }
}
```

### Use overlays

```kotlin
import com.example.ui.designsystem.overlays.*

if (showConfirm) {
    ElConfirmationDialog(
        title = "Delete student?",
        message = "This permanently removes the student and their payment history.",
        icon = Icons.Default.Delete,
        confirmLabel = "Delete",
        destructive = true,
        onConfirm = { viewModel.delete() },
        onDismiss = { showConfirm = false },
    )
}
```

---

## Design system reference

### Color tokens (selected)

| Token         | Light       | Dark        | Usage                                |
|---------------|-------------|-------------|--------------------------------------|
| primary       | `#4F46E5`   | `#818CF8`   | CTAs, selected states                |
| primaryAccent | `#F59E0B`   | `#FBBF24`   | Secondary CTA, highlights            |
| tertiary      | `#EC4899`   | `#F472B6`   | Third-level accents                  |
| success       | `#10B981`   | `#34D399`   | Positive states, paid indicators     |
| warning       | `#F97316`   | `#FB923C`   | Caution, overdue (non-critical)      |
| danger        | `#EF4444`   | `#F87171`   | Destructive actions, errors          |
| info          | `#0EA5E9`   | `#38BDF8`   | Informational badges                 |
| background    | `#FAFAFB`   | `#0A0A0F`   | App background                       |
| surface       | `#FFFFFF`   | `#13131A`   | Cards, sheets, dialogs               |

### Typography scale (selected)

| Style          | Size | Weight    | Usage                          |
|----------------|------|-----------|--------------------------------|
| displaySmall   | 36sp | ExtraBold | Hero numbers, screen titles    |
| headlineSmall  | 24sp | Bold      | Section headers                |
| titleMedium    | 16sp | SemiBold  | Card titles, list titles       |
| bodyLarge      | 16sp | Regular   | Primary body text              |
| labelLarge     | 14sp | SemiBold  | Buttons, chips                 |
| overline       | 11sp | Bold      | Eyebrows, section labels       |
| numericHero    | 48sp | Black     | KPI hero figures               |

### Spacing scale (4dp grid)

`none=0 · xs=4 · sm=8 · md=12 · lg=16 · xl=24 · xxl=32 · xxxl=48 · huge=64`

### Shape scale

| Token            | Radius | Usage                          |
|------------------|--------|--------------------------------|
| ElCardShape      | 24dp   | Signature card surface         |
| ElCardShapeSmall | 16dp   | Compact cards, list items      |
| ElButtonShape    | 14dp   | Buttons, text fields           |
| ElPillShape      | 50%    | Chips, badges, tabs            |
| ElSheetShape     | 32dp top | Bottom sheets                |
| ElDialogShape    | 28dp   | Dialogs, modals                |
| ElAvatarShape    | 50%    | Avatars, status dots           |
| ElFabShape       | 20dp   | FABs                           |

### Elevation (tinted shadows)

`none · low(4/2/.06) · medium(8/4/.08) · high(16/8/.10) · highest(24/12/.12) · floating(32/16/.14)`

### Motion

| Token    | Type   | Spec                                          | Usage                       |
|----------|--------|----------------------------------------------|-----------------------------|
| standard | spring | dampingRatio=MediumBouncy, stiffness=Medium  | Default press feedback      |
| bouncy   | spring | dampingRatio=LowBouncy, stiffness=Low        | FABs, success celebrations  |
| gentle   | spring | dampingRatio=NoBouncy, stiffness=MediumLow   | Sheets, large surfaces      |
| snappy   | spring | dampingRatio=NoBouncy, stiffness=High        | Tabs, selection             |
| quick    | tween  | 150ms, EmphasizedDecelerate                  | Quick fades                 |
| normal   | tween  | 250ms, EmphasizedDecelerate                  | Standard fades              |
| slow     | tween  | 400ms, EmphasizedStandard                    | Enter/exit                  |

### Component states (every component supports)

- Default
- Hovered (API 24+ via hover indication where applicable)
- Pressed (animated press-scale 0.90–0.98 via `pressScale`)
- Focused (2dp primary border)
- Disabled (0.4 alpha)
- Selected (filled primary / 10% primary tint)
- Loading (spinner replaces content; click disabled)
- Error (danger border + helper text where applicable)

---

## Migration from v1

The v1 → v2 migration is purely a code-organization refactor. **No public API
changes** — the same composables (`ElButton`, `ElCard`, `ElDialog`, etc.)
behave identically. The only change is internal file structure and import
paths.

### If you adopted v1

1. Replace `app/src/main/java/com/example/ui/designsystem/` with this v2 version.
2. Update any direct imports of internal files (rare — most callers only
   import the top-level composables which kept their package paths).
3. Rebuild.

### If you're adopting for the first time

Skip v1 entirely — v2 is the recommended version.

---

## Customization

- **Change the primary color:** edit `Violet600` / `Violet400` in `theme/Color.kt`. Every component reads from `ElColors` which derives from these tokens.
- **Add a new component variant:** extend the relevant enum in the package's `*Types.kt` and add a branch in the `*StyleResolver.kt`. No need to touch the composable.
- **Tune motion:** edit the spring specs in `theme/Motion.kt`. All components pick up the change automatically.
- **Add a new component:** create a new package directory under `components/` with `*Types.kt`, `*StyleResolver.kt` (if needed), and the composable file. Follow the existing pattern.

---

Built for El-Imtiyaz Staff. v2.0.0.
