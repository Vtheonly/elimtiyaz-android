# El-Imtiyaz Design System Map — `mobile` HEAD

> **Repo:** `/home/z/my-project/repos/mobile`
> **Design system root:** `app/src/main/java/com/example/ui/designsystem/`
> **Identity:** "Electric Violet & Sunshine" v2.0.0 (76 Kotlin files, largest 195 lines, ~5,600 LOC)
> **Investigation date:** 2026-08-01
> **Investigator:** Explore agent — modern design system
>
> The destructive commit `933c139` ("fk", 2026-08-01) wiped everything except the
> `designsystem/` package tree, `app/build.gradle.kts`, `gradle.properties`,
> `README.md`, and a stray screenshot test. The legacy UI files
> (`ElComponents.kt`, `ElComponentsExtended.kt`, `ModernTabs.kt`) were recovered
> from the previous commit `782bde1` via `git show` so the old→new mapping in
> §4 could be produced.

---

## 1. Theme System

The theme layer lives in `theme/` (12 files). All tokens are exposed via the
single `object ElTheme` accessor (`theme/ElTheme.kt`) so feature screens never
touch raw constants directly. The entry composable is `ElImtiyazTheme`
(`theme/Theme.kt`) — it publishes a Material 3 `ColorScheme` *and* the extended
`ElColors`, `ElSpacing`, `ElElevation`, `ElBorders`, `ElMotion`, and
`ElTextStyles` via `CompositionLocal`s.

### 1.1 Color palette (`theme/Color.kt` + `theme/ElColorSchemes.kt` + `theme/ElColors.kt`)

The palette is the **"Electric Violet & Sunshine"** identity. Light theme
primary = saturated violet `#4F46E5`, accent = sunshine amber `#F59E0B`. Dark
theme shifts to lighter shades (`#818CF8` violet, `#FBBF24` amber).

#### Brand colors (`Color.kt`)

| Token        | Hex         | Role                              |
|--------------|-------------|-----------------------------------|
| `Violet500`  | `#6366F1`   | Mid-brand violet                  |
| `Violet600`  | `#4F46E5`   | Primary (light)                   |
| `Violet700`  | `#4338CA`   | Gradient endpoint                 |
| `Violet400`  | `#818CF8`   | Primary (dark)                    |
| `Violet300`  | `#A5B4FC`   | Hover/pressed tint                |
| `Violet50`   | `#EEF2FF`   | `primaryContainer` (light)        |
| `Amber400`   | `#FBBF24`   | Accent (dark)                     |
| `Amber500`   | `#F59E0B`   | Accent (light)                    |
| `Amber600`   | `#D97706`   | Gradient endpoint                 |
| `Amber100`   | `#FEF3C7`   | Warning container                 |
| `Pink400`    | `#F472B6`   | Tertiary (dark)                   |
| `Pink500`    | `#EC4899`   | Tertiary (light)                  |
| `Pink600`    | `#DB2777`   | Gradient endpoint                 |

#### Semantic colors

| Token           | Light       | Dark        | Container (light) |
|-----------------|-------------|-------------|-------------------|
| `Emerald500/400`| `#10B981`   | `#34D399`   | `#D1FAE5`         |
| `Tangerine500/400`| `#F97316` | `#FB923C`   | `#FFEDD5`         |
| `Rose500/400`   | `#EF4444`   | `#F87171`   | `#FEE2E2`         |
| `Sky500/400`    | `#0EA5E9`   | `#38BDF8`   | `#E0F2FE`         |

#### Neutral surfaces

| Token                  | Light       | Dark        |
|------------------------|-------------|-------------|
| `LightBackground` / `DarkBackground`       | `#FAFAFB`   | `#0A0A0F`   |
| `LightSurface` / `DarkSurface`             | `#FFFFFF`   | `#13131A`   |
| `LightSurfaceVariant` / `DarkSurfaceVariant` | `#F1F2F6` | `#1C1C26`   |
| `LightSurfaceElevated` / `DarkSurfaceElevated` | `#FFFFFF` | `#23232F` |
| `LightInverseSurface` / `DarkInverseSurface` | `#1A1B22` | `#E6E7EC`   |

#### Text colors

| Token                | Light       | Dark        |
|----------------------|-------------|-------------|
| `LightTextPrimary` / `DarkTextPrimary`     | `#0F0F14` | `#F5F6FA` |
| `LightTextSecondary` / `DarkTextSecondary` | `#4B5563` | `#B4B9C7` |
| `LightTextMuted` / `DarkTextMuted`         | `#9CA3AF` | `#6B7280` |
| `LightTextOnColor` / `DarkTextOnColor`     | `#FFFFFF` | `#0F0F14` |

#### Outlines / borders

| Token                       | Light       | Dark        |
|-----------------------------|-------------|-------------|
| `LightOutline` / `DarkOutline`             | `#E5E7EB` | `#2A2A36` |
| `LightOutlineStrong` / `DarkOutlineStrong` | `#D1D5DB` | `#3A3A48` |
| `LightOutlineVariant` / `DarkOutlineVariant` | `#F3F4F6` | `#1F1F2A` |

#### Scrim / shadow / glass

| Token              | Light                          | Dark                            |
|--------------------|--------------------------------|---------------------------------|
| `LightScrim` / `DarkScrim`        | `#0F0F14` α 0.48   | `#000000` α 0.64   |
| `LightShadowColor` / `DarkShadowColor` | `#4F46E5` α 0.10 | `#000000` α 0.40 |
| `LightGlassTint` / `DarkGlassTint`     | `#FFFFFF` α 0.70 | `#FFFFFF` α 0.05 |
| `LightGlassBorder` / `DarkGlassBorder` | `#FFFFFF` α 0.90 | `#FFFFFF` α 0.10 |

#### Role accents (RBAC dashboards & avatars)

`RoleAdmin=Amber500`, `RoleFinancial=Violet600`, `RoleTeacher=Emerald500`,
`RoleSupport=Sky500`, `RoleManager=Pink500`, `RoleBuyer=#8B5CF6`,
`RoleDriver=Tangerine500`, `RoleWarehouse=#84CC16`, `RoleWorker=#64748B`.

#### Extended `ElColors` data class

`ElColors` (in `theme/ElColors.kt`) exposes ~35 fields beyond Material 3's
`ColorScheme`. The two light/dark instances live in `ElColorSchemes.kt`
(`LightElColors`, `DarkElColors`). The mapper
`ElColors.toMaterialScheme(): ColorScheme` bridges the extended palette into
stock Material 3 components.

**Gradient helpers** on `ElColors` (computed, theme-aware):

| Token                       | Colors                                  |
|-----------------------------|-----------------------------------------|
| `primaryGradient`           | `[primary, Violet700]`                  |
| `primaryGradientDiagonal`   | `[Violet700, primary, Violet400]`       |
| `accentGradient`            | `[primaryAccent, Amber600]`             |
| `tertiaryGradient`          | `[tertiary, Pink600]`                   |
| `successGradient`           | `[success, Emerald600]`                 |
| `dangerGradient`            | `[danger, Rose600]`                     |
| `warningGradient`           | `[warning, Tangerine600]`               |
| `heroGradient`              | `[Background, Surface]` (vertical)      |

**Brush helpers** (pre-built): `primaryBrush`, `primaryDiagonalBrush`,
`accentBrush`, `tertiaryBrush`, `successBrush`, `dangerBrush`,
`warningBrush`, `heroBrush`.

**Role lookup**: `ElColors.role(name: String): Color` — case-insensitive RBAC
string → color (e.g. `"teacher"` → `roleTeacher`).

### 1.2 Typography scale (`theme/Typography.kt` + `theme/ElTextStyles.kt`)

Material 3 `Typography` (file: `Typography.kt`). System default font family
(small APK, automatic Arabic/CJK fallback). Heavy display weights.

| Style           | Size | Weight    | Line height | Letter spacing |
|-----------------|------|-----------|-------------|----------------|
| `displayLarge`  | 56sp | Black     | 60sp        | −1.5sp         |
| `displayMedium` | 44sp | Black     | 48sp        | −1.0sp         |
| `displaySmall`  | 36sp | ExtraBold | 40sp        | −0.5sp         |
| `headlineLarge` | 32sp | ExtraBold | 38sp        | −0.5sp         |
| `headlineMedium`| 28sp | Bold      | 34sp        | −0.25sp        |
| `headlineSmall` | 24sp | Bold      | 30sp        | 0sp            |
| `titleLarge`    | 22sp | Bold      | 28sp        | 0sp            |
| `titleMedium`   | 16sp | SemiBold  | 24sp        | +0.1sp         |
| `titleSmall`    | 14sp | SemiBold  | 20sp        | +0.1sp         |
| `bodyLarge`     | 16sp | Normal    | 24sp        | +0.25sp        |
| `bodyMedium`    | 14sp | Normal    | 20sp        | +0.2sp         |
| `bodySmall`     | 12sp | Normal    | 16sp        | +0.4sp         |
| `labelLarge`    | 14sp | SemiBold  | 20sp        | +0.1sp         |
| `labelMedium`   | 12sp | SemiBold  | 16sp        | +0.5sp         |
| `labelSmall`    | 11sp | SemiBold  | 16sp        | +0.5sp         |

**Extended styles** (`ElTextStyles.kt`) — semantic styles that M3 lacks:

| Style          | Size | Weight | Usage                                  |
|----------------|------|--------|----------------------------------------|
| `numeric`      | 32sp | Black  | Amounts, balances, KPIs                |
| `numericHero`  | 48sp | Black  | Hero KPIs                              |
| `numericSmall` | 14sp | Bold   | Inline figures                         |
| `overline`     | 11sp | Bold   | Eyebrows above headlines (+1.5sp LS)   |
| `caption`      | 11sp | Medium | Image captions, footnotes              |
| `action`       | 14sp | Bold   | Buttons, links, CTAs                   |
| `badge`        | 10sp | Bold   | Counter / badge text                   |
| `statCentered` | 28sp | Black  | Centered stat block (align = Center)   |

Access: `ElTheme.typography` (M3 scale) and `ElTheme.textStyles` (extended).

### 1.3 Spacing tokens (`theme/Spacing.kt`)

4dp grid. Access: `ElTheme.spacing`.

| Token             | Value  | Notes                                  |
|-------------------|--------|----------------------------------------|
| `none`            | 0dp    |                                        |
| `xs`              | 4dp    |                                        |
| `sm`              | 8dp    |                                        |
| `md`              | 12dp   |                                        |
| `lg`              | 16dp   | Default screen-horizontal padding      |
| `xl`              | 24dp   | Default section gap                    |
| `xxl`             | 32dp   |                                        |
| `xxxl`            | 48dp   |                                        |
| `huge`            | 64dp   |                                        |
| `screenHorizontal`| 16dp   | (computed = `lg`)                      |
| `screenVertical`  | 16dp   | (computed = `lg`)                      |
| `itemGap`         | 8dp    | (computed = `sm`)                      |
| `sectionGap`      | 24dp   | (computed = `xl`)                      |
| `touchTarget`     | 48dp   | Material accessibility minimum         |

### 1.4 Shape tokens (`theme/Shape.kt`)

Material 3 `Shapes` instance + named semantic shapes.

| Token                  | Radius           | Usage                              |
|------------------------|------------------|------------------------------------|
| `ElShapes.extraSmall`  | 8dp              | M3 fallback                        |
| `ElShapes.small`       | 12dp             | M3 fallback                        |
| `ElShapes.medium`      | 16dp             | M3 fallback                        |
| `ElShapes.large`       | 24dp             | M3 fallback                        |
| `ElShapes.extraLarge`  | 32dp             | M3 fallback                        |
| `ElCardShape`          | 24dp             | Signature card surface             |
| `ElCardShapeSmall`     | 16dp             | Compact card / list-item           |
| `ElPillShape`          | 50% (fully round)| Chips, badges, tabs                |
| `ElButtonShape`        | 14dp             | Buttons                            |
| `ElFieldShape`         | 14dp             | Text fields & dropdowns            |
| `ElSheetShape`         | 32dp top-only    | Bottom sheet                       |
| `ElSheetHandleShape`   | 50%              | Sheet drag handle                  |
| `ElAvatarShape`        | 50%              | Avatars, status dots               |
| `ElFabShape`           | 20dp             | FAB                                |
| `ElDialogShape`        | 28dp             | Modals / dialogs                   |
| `ElNotificationShape`  | 16dp             | Toasts                             |
| `ElTooltipShape`       | 10dp             | Tooltips                           |
| `ElContextMenuShape`   | 16dp             | Context menus                      |
| `ElSheetShapeFlat`     | 20dp top-only    | Flatter sheet variant              |
| `ElRectangleShape`     | Rectangle        | Full-bleed images / dividers       |

Access: `ElTheme.shapes` (M3 `Shapes`) or import the top-level `val`s
directly.

### 1.5 Elevation tokens (`theme/Elevation.kt`)

Tinted shadow specs (blur, y-offset, alpha). Light theme shadows are tinted
with `#4F46E5` (violet) at low alpha; dark theme uses black at higher alpha.

| Token      | blur | y    | α     | Usage                                |
|------------|------|------|-------|--------------------------------------|
| `none`     | 0dp  | 0dp  | 0.00  | Flat / outlined                      |
| `low`      | 4dp  | 2dp  | 0.06  | Subtle raised cards                  |
| `medium`   | 8dp  | 4dp  | 0.08  | Default cards (used by `ElCard`)     |
| `high`     | 16dp | 8dp  | 0.10  | Toggles, dropdowns, context menus    |
| `highest`  | 24dp | 12dp | 0.12  | Stacked cards                        |
| `floating` | 32dp | 16dp | 0.14  | Sheets, dialogs, FABs                |
| `overlay`  | 40dp | 20dp | 0.18  | Top-most scrim-anchored popups       |

Access: `ElTheme.elevation` (returns `ElElevation`). Shadow color via
`ElTheme.shadowColor`.

### 1.6 Motion tokens (`theme/Motion.kt`)

Mix of physics-based springs (interactive feedback) and duration-based tweens
(choreographed transitions).

**Springs** (`FiniteAnimationSpec<Float>`):

| Token      | Damping ratio        | Stiffness        | Usage                          |
|------------|----------------------|------------------|--------------------------------|
| `standard` | `MediumBouncy`       | `Medium`         | Default tap feedback           |
| `bouncy`   | `LowBouncy`          | `Low`            | FABs, success celebrations     |
| `gentle`   | `NoBouncy`           | `MediumLow`      | Sheets, large surfaces         |
| `snappy`   | `NoBouncy`           | `High`           | Tabs, selection                |

**Tweens** (`DurationBasedAnimationSpec<Float>`):

| Token   | Duration | Easing                  | Usage                  |
|---------|----------|-------------------------|------------------------|
| `quick` | 150ms    | `EmphasizedDecelerate`  | Quick fades            |
| `normal`| 250ms    | `EmphasizedDecelerate`  | Standard fades         |
| `slow`  | 400ms    | `EmphasizedStandard`    | Enter / exit           |

**Offset specs** (`FiniteAnimationSpec<IntOffset>`): `slideUp`, `slideDown`
(both `NoBouncy` / `MediumLow`).

**Easing curves** (top-level `val`s): `EmphasizedStandard`,
`EmphasizedDecelerate`, `EmphasizedAccelerate`, `StandardCurve` (all
`CubicBezierEasing`).

Access: `ElTheme.motion`.

### 1.7 Border tokens (`theme/Borders.kt`)

| Token       | Width | Usage                                |
|-------------|-------|--------------------------------------|
| `hairline`  | 0.5dp | Dense tables                         |
| `thin`      | 1dp   | Standard structural (default)        |
| `thick`     | 2dp   | Emphasis / focus                     |
| `heavy`     | 3dp   | Heavy emphasis                       |
| `standard`  | 1dp   | (computed = `thin`)                  |
| `focus`     | 2dp   | (computed = `thick`)                 |

Access: `ElTheme.borders`.

---

## 2. Foundation modifiers (`foundation/`, 5 files)

Every modifier in `foundation/` is the single source of truth for one visual
effect. Components compose them instead of repeating boilerplate.

### 2.1 `Modifier.noRippleClickable(...)`
**File:** `foundation/Clickable.kt`

```kotlin
fun Modifier.noRippleClickable(
    enabled: Boolean = true,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier
```

**Visual effect:** A `Modifier.clickable` with `indication = null` (no Material
ripple). The bold geometric system relies on press-scale animation rather than
ripples for tap feedback. Used everywhere a silent tap target is needed
(lists, badges, chips' inner dismiss icon, top bars, etc.).

### 2.2 `Modifier.pressClickable(...)`
**File:** `foundation/Clickable.kt`

```kotlin
fun Modifier.pressClickable(
    pressedScale: Float = 0.96f,
    enabled: Boolean = true,
    role: Role? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier
```

**Visual effect:** Combines `pressScale(...)` + `noRippleClickable(...)` in one
call. The standard interactive surface treatment for buttons, cards, chips,
and FABs. Accepts an optional shared `interactionSource` so callers can
read press state elsewhere.

### 2.3 `Modifier.pressScale(...)`
**File:** `foundation/PressScaleModifier.kt`

```kotlin
fun Modifier.pressScale(
    pressedScale: Float = 0.96f,
    animationSpec: FiniteAnimationSpec<Float>? = null,
    interactionSource: MutableInteractionSource,
): Modifier
```

**Visual effect:** Animated scale-down on press using `ElTheme.motion.standard`
(or a custom spec). Returns to 1.0 on release. Playful springy feedback
without coupling to the click handler.

### 2.4 `Modifier.elShadow(...)`
**File:** `foundation/ShadowModifier.kt`

```kotlin
fun Modifier.elShadow(
    spec: ElElevationSpec,
    shape: Shape = RoundedCornerShape(24.dp),
    color: Color? = null,
): Modifier

fun Modifier.elShadowFallback(
    elevation: Dp,
    shape: Shape = RoundedCornerShape(24.dp),
): Modifier
```

**Visual effect:** Draws a tinted, blurred shadow behind the element using
`drawBehind` + Android's `BlurMaskFilter`. The shadow color defaults to
`ElTheme.shadowColor` (violet-tinted in light theme, deep black in dark).
Skipped entirely when `spec.alpha <= 0f` (so `none` elevation is truly flat).
The fallback uses Compose's native `Modifier.shadow` for environments where
the blur draw path is unavailable.

### 2.5 `Modifier.elGlass(shape)`
**File:** `foundation/GlassModifier.kt`

```kotlin
@Composable
fun Modifier.elGlass(shape: Shape): Modifier
```

**Visual effect:** Frosted-glass surface — clips to `shape`, paints a
translucent `glassTint` background, adds a 1dp `glassBorder`. Use for premium
overlays and floating bars. Reads `ElTheme.colors.glassTint`/`glassBorder`.

### 2.6 `Modifier.elBorder(...)` + `Modifier.elGradientBackground(brush)`
**File:** `foundation/BorderModifier.kt`

```kotlin
@Composable
fun Modifier.elBorder(
    width: Dp? = null,        // defaults to ElTheme.borders.thin
    color: Color? = null,     // defaults to ElTheme.colors.outline
    shape: Shape,
): Modifier

fun Modifier.elGradientBackground(brush: Brush): Modifier
```

**Visual effect:** `elBorder` is a token-aware `Modifier.border` (defaults
pull from theme). `elGradientBackground` clips to a flat rectangle and paints
a brush; pass a clipped modifier upstream for shaped gradient backgrounds.

---

## 3. Component inventory

The component layer is organized into 7 packages: `button/`, `card/`, `data/`,
`display/`, `feedback/`, `input/`, `nav/`, plus `tabs/`. All composables use
the `El` prefix and live under
`com.example.ui.designsystem.components.*`.

### 3.1 Buttons (`components/button/`, 5 files)

#### `ElButton` — primary CTA
**File:** `components/button/ElButton.kt`

```kotlin
@Composable
fun ElButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ElButtonVariant = ElButtonVariant.PRIMARY,
    size: ElButtonSize = ElButtonSize.MEDIUM,
    icon: ImageVector? = null,
    iconEnd: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    shape: Shape = ElButtonShape,
    fullWidth: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
)
```

**Variants** (`ButtonStyleResolver.resolveButtonStyle` output):

| Variant      | Background brush            | Content color           | Border                  |
|--------------|-----------------------------|-------------------------|-------------------------|
| `PRIMARY`    | `colors.primaryBrush`       | `textOnColor` (white)   | none                    |
| `SECONDARY`  | solid `primaryAccent` (amber) | `onPrimaryAccent`     | none                    |
| `TONAL`      | solid `primaryContainer`    | `onPrimaryContainer`    | none                    |
| `OUTLINED`   | transparent                 | `primary`               | 2dp `primary` (`borders.thick`) |
| `GHOST`      | transparent                 | `primary`               | none                    |
| `DANGER`     | `colors.dangerBrush`        | `onDanger` (white)      | none                    |

**Sizes** (`ButtonTypes.buttonPadding` / `buttonMinHeight` / `buttonTextStyle`
/ `buttonIconSize`):

| Size    | Min height | Padding (h×v)      | Text style          | Icon size |
|---------|------------|--------------------|---------------------|-----------|
| `SMALL` | 32dp       | 12×8dp             | `labelMedium`       | 14px      |
| `MEDIUM`| 44dp       | 18×12dp            | `labelLarge`        | 18px      |
| `LARGE` | 56dp       | 24×16dp            | `titleMedium`       | 22px      |

**States:** `enabled=false` → 0.4 alpha; `loading=true` → spinner replaces
content, click disabled; press-scale 0.96 via `pressClickable`.

**Usage:**
```kotlin
ElButton(
    text = "Record Payment",
    onClick = vm::pay,
    variant = ElButtonVariant.PRIMARY,
    icon = Icons.Default.Check,
    fullWidth = true,
    loading = state.isSubmitting,
)
```

**Screen patterns:** Primary CTAs, dialog confirm/cancel, empty-state CTAs,
form submit buttons, list-row trailing actions.

#### `ElIconButton` — icon-only button
**File:** `components/button/ElIconButton.kt`

```kotlin
@Composable
fun ElIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    enabled: Boolean = true,
    tint: Color = ElTheme.colors.textPrimary,
    background: Color = ElTheme.colors.surfaceVariant,
    size: Int = 44,
    iconSize: Int = 20,
    shape: Shape = CircleShape,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
)
```

**Visual:** 44dp circle (default) with `surfaceVariant` background, press-scale
0.90. Override `shape` for squircle / pill variants.

**Screen patterns:** Toolbar actions, top-bar back button, list-row trailing
icons, search-field trailing clear.

#### `ElFab` — floating action button
**File:** `components/button/ElFab.kt`

```kotlin
@Composable
fun ElFab(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    label: String? = null,        // when set → extended FAB
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
)
```

**Visual:** 56dp square when `label == null`; wider extended FAB when label is
set. Brand `primaryBrush` gradient background. Press-scale 0.92.

**Usage:**
```kotlin
ElFab(icon = Icons.Default.Add, onClick = ::onCreate, label = "New Student")
```

**Screen patterns:** Create actions on directory screens (Students, Parents,
Expenses), quick-add on dashboards.

### 3.2 Cards (`components/card/`, 4 files)

#### `ElCard` — universal surface
**File:** `components/card/ElCard.kt`

```kotlin
@Composable
fun ElCard(
    modifier: Modifier = Modifier,
    variant: ElCardVariant = ElCardVariant.ELEVATED,
    size: ElCardSize = ElCardSize.STANDARD,
    shape: Shape = defaultCardShape(size),
    elevation: ElElevationSpec? = ElTheme.elevation.medium,
    onClick: (() -> Unit)? = null,
    border: BorderStroke? = null,
    background: Color? = null,
    gradient: Brush? = null,
    content: @Composable ColumnScope.() -> Unit,
)
```

**Variants** (`CardStyleResolver.resolveCardStyle`):

| Variant    | Background                 | Border                                | Shadow? |
|------------|----------------------------|---------------------------------------|---------|
| `OUTLINED` | `surface`                  | 1dp `outline`                         | no      |
| `ELEVATED` | `surface`                  | none                                  | yes (medium) |
| `FILLED`   | `surfaceVariant`           | none                                  | yes     |
| `GRADIENT` | `primaryBrush` (violet→violet700) | none                           | yes     |
| `GLASS`    | `glassTint`                | 1dp `glassBorder`                     | yes     |

**Sizes** (`CardTypes.cardPadding`):

| Size          | Padding | Default shape      |
|---------------|---------|--------------------|
| `COMPACT`     | 12dp    | `ElCardShapeSmall` (16dp) |
| `STANDARD`    | 16dp    | `ElCardShape` (24dp)      |
| `COMFORTABLE` | 24dp    | `ElCardShape` (24dp)      |

**Behavior:** `onClick != null` → press-scale 0.98 (interactive card). `border`
and `gradient`/`background` overrides beat the variant defaults.

**Usage:**
```kotlin
ElCard(variant = ElCardVariant.ELEVATED, onClick = ::openDetail) {
    Text("Student summary", style = ElTheme.typography.titleMedium)
    Text("Grade 6-B", style = ElTheme.typography.bodyMedium)
}
```

**Screen patterns:** Every surface container — list cards, form sections,
dashboard tiles, dialog content hosts, filter chips containers.

#### `ElStatCard` — KPI tile
**File:** `components/card/ElStatCard.kt`

```kotlin
@Composable
fun ElStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trend: String? = null,
    trendPositive: Boolean = true,
    accentColor: Color = ElTheme.colors.primary,
    onClick: (() -> Unit)? = null,
)
```

**Visual:** Wraps `ElCard(ELEVATED, STANDARD)`. Layout: tinted icon block →
label (`labelMedium`) → value (`textStyles.numeric`, 32sp Black) → optional
trend (green if `trendPositive`, red otherwise).

**Usage:**
```kotlin
ElStatCard(
    label = "Outstanding", value = "DZD 248K",
    icon = Icons.Default.Warning,
    trend = "+12% vs last month", trendPositive = false,
)
```

**Screen patterns:** Dashboard KPIs, financial summary tiles, attendance
counters.

### 3.3 Data display (`components/data/`, 5 files)

#### `ElTable` — sortable table
**File:** `components/data/ElTable.kt`

```kotlin
@Composable
fun ElTable(
    columns: List<ElTableColumn>,
    rows: List<ElTableRow>,
    modifier: Modifier = Modifier,
    sortColumn: Int? = null,
    sortAscending: Boolean = true,
    onSortToggle: ((Int) -> Unit)? = null,
    emptyState: @Composable (() -> Unit)? = null,
)
```

**Supporting types** (`TableTypes.kt`):

```kotlin
enum class ElColumnAlign { START, CENTER, END }

data class ElTableColumn(
    val title: String,
    val weight: Float = 1f,
    val align: ElColumnAlign = ElColumnAlign.START,
    val sortable: Boolean = false,
)

data class ElTableRow(
    val id: String,
    val cells: List<String>,
    val onClick: (() -> Unit)? = null,
)
```

**Visual:** A `Column` clipped to `ElCardShape` (24dp) with `surface`
background + 1dp outline border. Header row uses `surfaceVariant` background
and uppercase overline titles. Sortable columns render an `ArrowUpward` /
`ArrowDownward` indicator when `sortColumn == index`. Body rows are
weight-distributed `Text`s; row taps fire `row.onClick`; dividers between rows.

**Internal helpers** (`internal`): `TableHeader`, `TableColumnHeader`,
`TableBodyRow`.

**Usage:**
```kotlin
ElTable(
    columns = listOf(
        ElTableColumn("Student", 2f),
        ElTableColumn("Grade", 1f, ElColumnAlign.CENTER),
        ElTableColumn("Balance", 1.2f, ElColumnAlign.END, sortable = true),
    ),
    rows = students.map { ElTableRow(it.id, listOf(it.name, it.grade, it.balance), onClick = ::open) },
    sortColumn = 2,
    sortAscending = false,
    onSortToggle = vm::onSort,
    emptyState = { ElEmptyState(title = "No students", icon = Icons.Default.Inbox) },
)
```

**Screen patterns:** Student/parent/employee directories, financial ledgers,
audit logs, attendance rosters.

#### `ElListItem` — universal list row
**File:** `components/data/ElList.kt`

```kotlin
@Composable
fun ElListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    leadingAvatarUrl: String? = null,
    leadingInitials: String? = null,
    leadingTint: Color = ElTheme.colors.primary,
    trailingText: String? = null,
    trailingIcon: ImageVector? = null,
    trailingBadge: String? = null,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    showDivider: Boolean = true,
)
```

**Visual:** Full-width row with three slots:
- **Leading**: `ElAvatar` (if `leadingAvatarUrl` or `leadingInitials` set) OR
  icon-in-circle (if `leadingIcon` set); else empty.
- **Middle**: title (`titleSmall`, 1 line) + optional subtitle (`bodySmall`,
  2 lines, ellipsized).
- **Trailing**: optional badge (PRIMARY SOLID `ElBadge`), text (`labelMedium`,
  `textMuted`), or icon.

`selected` paints the row with `primary.copy(alpha = 0.08f)`. `onClick` uses
`noRippleClickable`. Optional divider at the bottom when `showDivider`.

**Screen patterns:** Inbox rows, settings items, search results, student/
parent/employee roster rows, notification list, audit log entries.

### 3.4 Display components (`components/display/`, 9 files)

#### `ElAvatar` — image / initials / icon avatar
**File:** `components/display/ElAvatar.kt`

```kotlin
@Composable
fun ElAvatar(
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    initials: String? = null,
    icon: ImageVector? = null,
    size: ElAvatarSize = ElAvatarSize.M,
    accentColor: Color = ElTheme.colors.primary,
    statusDot: Color? = null,
)
```

**Sizes** (`AvatarTypes.avatarDp` / `avatarTextSize`):

| Size | Diameter | Initials font size |
|------|----------|--------------------|
| `XS` | 24dp     | 10sp               |
| `S`  | 32dp     | 12sp               |
| `M`  | 40dp     | 14sp               |
| `L`  | 56dp     | 18sp               |
| `XL` | 72dp     | 24sp               |

**Behavior:** Renders `AsyncImage` (Coil) if `imageUrl` set; else initials
(taken as first 2 chars, uppercased); else icon. Background is a linear
gradient of `accentColor` α 0.85 → `accentColor`. 2dp `surface` border.
Optional `statusDot` anchored bottom-end (e.g. online indicator).

**Screen patterns:** Profile pictures, list-row leading avatars, dashboard
role tiles, comment threads.

#### `ElBadge` — status / count / tag
**File:** `components/display/ElBadge.kt`

```kotlin
@Composable
fun ElBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: ElBadgeTone = ElBadgeTone.PRIMARY,
    style: ElBadgeStyle = ElBadgeStyle.SOFT,
    icon: ImageVector? = null,
    dot: Boolean = false,
)
```

**Tones** (`BadgePalettes.ElColors.badgePalette`): `PRIMARY`, `SECONDARY`,
`TERTIARY`, `NEUTRAL`, `SUCCESS`, `WARNING`, `DANGER`, `INFO`. Each maps to a
`(fg, bg, border)` triple derived from the semantic colors.

**Styles** (`ElBadgeStyle`):

| Style      | Background           | Foreground      | Border             |
|------------|----------------------|-----------------|--------------------|
| `SOLID`    | palette.border       | `textOnColor`   | none               |
| `SOFT`     | palette.bg α 0.14/0.20 | palette.fg    | none               |
| `OUTLINED` | transparent          | palette.fg      | palette.border 1dp |

**Visual:** Pill shape, `textStyles.badge` (10sp Bold), 8×3dp padding. Optional
dot prefix or icon prefix.

**Screen patterns:** Payment status (PAID / OVERDUE / PENDING), notification
counts on nav items, role labels, audit-log action tags.

#### `ElChip` — assist / filter / input / choice chip
**File:** `components/display/ElChip.kt`

```kotlin
@Composable
fun ElChip(
    text: String,
    modifier: Modifier = Modifier,
    variant: ElChipVariant = ElChipVariant.ASSIST,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
)
```

**Variants** (`ChipTypes.kt`): `ASSIST`, `FILTER`, `INPUT`, `CHOICE`.

**Color resolution** (`resolveChipColors`):
- `!enabled` → muted `surfaceVariant` α 0.5, `textMuted`
- `selected` → `primary` bg, `textOnColor` fg
- else → `surfaceVariant` bg, `textPrimary` fg, 1dp `outline` border

Press-scale 0.94. `onDismiss != null` renders a Close icon (used by `INPUT`
variant for chip-input fields).

**Screen patterns:** Filter chips on directory screens, role filters, tag
input, choice selections in forms.

#### `ElChipGroup` — flow-laid chip cluster
**File:** `components/display/ElChipGroup.kt`

```kotlin
@Composable
fun ElChipGroup(
    chips: List<Pair<String, Boolean>>,
    onToggle: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
)
```

**Visual:** `FlowRow` of `ElChip(FILTER, selected, onClick)` with 8dp
horizontal/vertical spacing. Each entry is `(label, selected)`.

**Screen patterns:** Multi-select filter bars (e.g. grade filters, payment
status filters).

#### `ElDivider` + `ElSectionDivider`
**File:** `components/display/ElDivider.kt`

```kotlin
@Composable
fun ElDivider(
    modifier: Modifier = Modifier,
    verticalPadding: Int = 0,
)

@Composable
fun ElSectionDivider(
    label: String,
    modifier: Modifier = Modifier,
)
```

**Visual:** `ElDivider` is a 1dp `outlineVariant` horizontal line with optional
vertical padding. `ElSectionDivider` is an overline label + a 1dp weight=1
line beside it — for eyebrow section breaks.

**Screen patterns:** Inside cards to separate sections, between list groups,
above action rows in dialogs.

### 3.5 Feedback (`components/feedback/`, 4 files)

#### `ElLinearProgress`
**File:** `components/feedback/ElProgress.kt`

```kotlin
@Composable
fun ElLinearProgress(
    progress: Float,                // 0f..1f
    modifier: Modifier = Modifier,
    trackColor: Color = ElTheme.colors.surfaceVariant,
    gradient: List<Color> = ElTheme.colors.primaryGradient,
    height: Int = 8,
)
```

**Visual:** Rounded bar (50% radius) with `trackColor` background and an
animated (`ElTheme.motion.normal`) foreground gradient fill of width
`progress.coerceIn(0f, 1f)`.

**Screen patterns:** Sync progress, payment installments (paid vs total),
storage usage, attendance %.

#### `ElSpinner`
**File:** `components/feedback/ElProgress.kt`

```kotlin
@Composable
fun ElSpinner(
    modifier: Modifier = Modifier,
    size: Int = 32,
    strokeWidth: Int = 3,
    color: Color = ElTheme.colors.primary,
)
```

Wraps `CircularProgressIndicator` with theme `primary` + `surfaceVariant`
track.

#### `ElLinearLoader` (indeterminate)
**File:** `components/feedback/ElLoading.kt`

```kotlin
@Composable
fun ElLinearLoader(
    modifier: Modifier = Modifier,
    color: Color = ElTheme.colors.primary,
    height: Int = 4,
)
```

**Visual:** 4dp-tall rounded bar with a `linearGradient(Transparent → color →
Transparent)` that shifts across via `rememberInfiniteTransition` (1200ms
repeat). For unknown-duration operations.

**Screen patterns:** Initial data fetch, post-save refresh, dashboard
background sync.

#### `ElLoadingBlock`
**File:** `components/feedback/ElLoading.kt`

```kotlin
@Composable
fun ElLoadingBlock(
    modifier: Modifier = Modifier,
    message: String? = "Loading…",
)
```

Centered `ElSpinner(36)` + optional message (`bodyMedium`, `textSecondary`).

**Screen patterns:** Full-screen loading state when a screen is bootstrapping.

#### `ElSkeletonBox` / `ElSkeletonCircle` / `ElSkeletonLine` / `ElSkeletonCard`
**File:** `components/feedback/ElSkeleton.kt`

```kotlin
@Composable
fun ElSkeletonBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp))
@Composable
fun ElSkeletonCircle(sizeDp: Int = 40, modifier: Modifier = Modifier)
@Composable
fun ElSkeletonLine(modifier: Modifier = Modifier, widthFraction: Float = 1f, height: Int = 12)
@Composable
fun ElSkeletonCard(modifier: Modifier = Modifier)
```

**Visual:** Shimmering placeholder blocks with α animating 0.35↔0.75 over
900ms (reverse repeat) on `surfaceVariant`. `ElSkeletonCard` pre-composes an
avatar + 2 text lines for a typical list-row placeholder.

**Screen patterns:** List loading states, dashboard tile placeholders,
detail-screen bootstrapping.

#### `ElEmptyState`
**File:** `components/feedback/ElEmptyState.kt`

```kotlin
@Composable
fun ElEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
)
```

**Visual:** Centered column — 72dp circular tinted icon block (`primary` α
0.10) → title (`titleMedium`, centered) → optional subtitle (`bodyMedium`,
centered) → optional CTA (`ElButton(PRIMARY, MEDIUM)`).

**Screen patterns:** Empty directory states, no-search-results, no-permission
gated views, first-run onboarding prompts.

### 3.6 Inputs (`components/input/`, 7 files)

#### `ElTextField` — full-state text input
**File:** `components/input/ElTextField.kt`

```kotlin
@Composable
fun ElTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    onTrailingIconClick: (() -> Unit)? = null,
    helperText: String? = null,
    errorText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
)
```

**State machine** (`TextFieldTypes.kt` + `FieldStyleResolver.kt`):
`ElTextFieldState { DEFAULT, FOCUSED, ERROR, DISABLED }`. The resolver
`resolveFieldState(enabled, isError)` returns DISABLED / ERROR / DEFAULT.
*(Note: `FOCUSED` is currently unreachable from `resolveFieldState`; see §5 —
known issue.)*

| State     | Background                       | Border color  | Border width |
|-----------|----------------------------------|---------------|--------------|
| DEFAULT   | `surfaceVariant`                 | `outline`     | `thin` (1dp) |
| FOCUSED   | `surfaceVariant`                 | `primary`     | `thick` (2dp) |
| ERROR     | `surfaceVariant`                 | `danger`      | `thin` (1dp) |
| DISABLED  | `surfaceVariant` α 0.5           | `outline`     | `thin` (1dp) |

Layout: optional label (`labelMedium`, danger if `isError`) → 52dp-min row
clipped to `ElFieldShape` containing leading icon + `BasicTextField` +
trailing icon → optional helper/error text below (`bodySmall`, muted or
danger). Uses `BasicTextField` for full styling control.

**Internal parts** (`FieldParts.kt`, `internal`): `FieldLabel`,
`FieldLeadingIcon`, `FieldInput`, `FieldTrailingIcon`, `FieldHelper`.

**Screen patterns:** Login forms, search bars (with trailing clear icon),
registration modals, filter sheets, expense entry.

#### `ElDropdown` — modal select
**File:** `components/input/ElDropdown.kt`

```kotlin
@Composable
fun ElDropdown(
    options: List<ElDropdownOption>,
    selectedValue: String?,
    onSelected: (ElDropdownOption) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "Select…",
    enabled: Boolean = true,
)
```

**Supporting type** (`DropdownOption.kt`):

```kotlin
data class ElDropdownOption(
    val value: String,
    val label: String,
    val icon: ImageVector? = null,
)
```

**Visual:** Trigger row matches `ElTextField` styling (`ElFieldShape`,
`surfaceVariant`, 1dp outline, 52dp min height, trailing `ArrowDropDown` that
rotates 180° when expanded). Tap opens a `Dialog` popup with `LazyColumn` of
options (max 360dp tall), each row showing optional icon + label + check icon
if selected.

**Internal parts** (`DropdownParts.kt`, `internal`): `DropdownTrigger`,
`DropdownPopup`, `DropdownOptionRow`.

**Screen patterns:** Role pickers in forms, grade/class selectors, payment
method selectors, filter-sheet dropdowns.

### 3.7 Navigation (`components/nav/`, 5 files)

#### `ElNavDestination` — destination model
**File:** `components/nav/NavTypes.kt`

```kotlin
data class ElNavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val badge: String? = null,
)
```

#### `ElBottomBar` — main bottom nav
**File:** `components/nav/ElBottomBar.kt`

```kotlin
@Composable
fun ElBottomBar(
    destinations: List<ElNavDestination>,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
)
```

**Visual:** Full-width `surface` background, `navigationBarsPadding`,
`SpaceEvenly`-distributed `NavItem`s. 64dp tall.

#### `ElNavRail` — tablet / landscape side rail
**File:** `components/nav/ElNavRail.kt`

```kotlin
@Composable
fun ElNavRail(
    destinations: List<ElNavDestination>,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
)
```

**Visual:** 80dp-wide column, `surface` background, vertically stacked rail
items. Same `selectedIcon`/badge logic as `ElBottomBar` but more compact (no
pill bg, just 10% tint behind a circular icon area).

#### `NavItem` (internal)
**File:** `components/nav/NavItem.kt`

Renders a single bottom-bar item: 56dp-tall clipped circle with `primary` α
0.10 background when selected, icon + label below, optional danger-colored
badge.

#### `ElTopBar` — top app bar
**File:** `components/nav/ElTopBar.kt`

```kotlin
@Composable
fun ElTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null,
)
```

**Visual:** Transparent background (edge-to-edge friendly), 56dp tall, 8dp
padding. Optional back button (`ElIconButton` with transparent bg + ArrowBack),
title (`titleLarge`) + optional subtitle (`bodySmall`), and trailing actions
slot.

**Screen patterns:** Every screen header — directories, detail screens,
dashboards, settings.

### 3.8 Tabs (`components/tabs/`, 1 file)

#### `ElTabRow` — segmented pill tab row
**File:** `components/tabs/ElTabs.kt`

```kotlin
@Composable
fun ElTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
)
```

**Visual:** Pill-shaped container (`ElPillShape`, `surfaceVariant` background,
4dp padding). Inside: `SpaceBetween` row of tab items, each `weight(1f)`.
Selected tab gets `primaryBrush` background + `textOnColor` foreground.
Unselected gets `textSecondary`. Animated color via `animateColorAsState`.

**Screen patterns:** Sub-screen navigation (Hub → tabs), filter rows,
segmented controls.

#### `ElVerticalTabList` — settings-style vertical tabs
**File:** `components/tabs/ElTabs.kt`

```kotlin
@Composable
fun ElVerticalTabList(
    items: List<Triple<ImageVector, String, Boolean>>,  // (icon, label, selected)
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
)
```

**Visual:** Vertical column of full-width rows. Each row: 32dp circle (filled
`primary` when selected, `surfaceVariant` otherwise) holding an icon + label
(`labelLarge`, `primary` when selected else `textPrimary`). Selected row gets
`primary` α 0.10 background.

**Screen patterns:** Settings screen section list, dashboard side filters,
multi-section detail screens.

### 3.9 Overlays (`overlays/`, 10 files)

Every overlay shares the same `ElDialogShape`/`ElSheetShape` family,
`ElTheme.elevation.floating`/`high` tinted shadow, and `ElTheme.colors.scrim`
backdrop.

#### `ElDialogShell` — base modal shell
**File:** `overlays/ElDialogShell.kt`

```kotlin
@Composable
fun ElDialogShell(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnScrimTap: Boolean = true,
    dismissOnBackPress: Boolean = true,
    content: @Composable () -> Unit,
)
```

**Visual:** Wraps `androidx.compose.ui.window.Dialog` with
`usePlatformDefaultWidth = false` + `decorFitsSystemWindows = false`. Fills the
screen with `scrim`, centers the content surface (24dp horizontal padding).
Surface uses `ElDialogShape` (28dp), `surface` background, 1dp
`outlineVariant` border, `elevation.floating` shadow, scale-in 0.92→1.0 over
220ms (`EmphasizedDecelerate`) + 180ms fade.

#### `ElDialogContent` — standard inner layout
**File:** `overlays/ElDialogContent.kt`

```kotlin
@Composable
fun ElDialogContent(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector? = null,
    actions: @Composable RowScope.() -> Unit = {},
)
```

**Visual:** 24dp-padded `Column`: optional tinted icon block (16dp-radius,
`primary` α 0.10, 12dp padding) → title (`headlineSmall`) → optional message
(`bodyMedium`, `textSecondary`) → spacer → end-aligned actions row.

#### `ElConfirmationDialog` — pre-built confirm/cancel
**File:** `overlays/ElConfirmationDialog.kt`

```kotlin
@Composable
fun ElConfirmationDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector? = null,
    confirmLabel: String = "Confirm",
    cancelLabel: String = "Cancel",
    destructive: Boolean = false,
)
```

Wraps `ElDialogShell` + `ElDialogContent` with a GHOST cancel button and a
PRIMARY (or DANGER when `destructive`) confirm button. Confirm fires
`onConfirm()` then auto-dismisses.

**Usage:**
```kotlin
ElConfirmationDialog(
    title = "Delete student?",
    message = "This permanently removes the student and their payment history.",
    icon = Icons.Default.Delete,
    confirmLabel = "Delete",
    destructive = true,
    onConfirm = vm::delete,
    onDismiss = { showConfirm = false },
)
```

**Screen patterns:** Destructive confirmations (delete, archive, revoke),
save-changes prompts, sign-out confirmations.

#### `ElBottomSheet` — slide-up panel
**File:** `overlays/ElBottomSheet.kt`

```kotlin
@Composable
fun ElBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnScrimTap: Boolean = true,
    showHandle: Boolean = true,
    title: String? = null,
    content: @Composable () -> Unit,
)
```

**Visual:** Full-screen `Box` with `scrim` (tap dismisses if
`dismissOnScrimTap`). Aligned bottom: a `Column` clipped to `ElSheetShape`
(32dp top corners), `surface` background, 1dp `outlineVariant` border,
`elevation.floating` shadow, `navigationBarsPadding`. Optional 36×4dp drag
handle (`outlineStrong`, `ElSheetHandleShape`). Optional title (`titleLarge`).
Then caller content.

#### `ElSheetContent` — standard sheet inner layout
**File:** `overlays/ElSheetContent.kt`

```kotlin
@Composable
fun ElSheetContent(
    modifier: Modifier = Modifier,
    body: @Composable () -> Unit,
    actions: @Composable () -> Unit = {},
)
```

**Visual:** 24dp-h-pad / 8dp-v-pad column: body → 16dp spacer → actions row.

**Screen patterns:** Filter sheets, form sheets (create/edit), detail drawers
on mobile, multi-step wizards.

#### `ElContextMenu` — modal popup menu
**File:** `overlays/ElContextMenu.kt`

```kotlin
@Composable
fun ElContextMenu(
    items: List<ElContextMenuItem>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
)
```

**Supporting type** (`ContextMenuTypes.kt`):

```kotlin
data class ElContextMenuItem(
    val label: String,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)
```

**Visual:** Full-screen `scrim` (tap dismisses), centered `Column` clipped to
`ElContextMenuShape` (16dp), `surface` bg, 1dp `outlineVariant` border,
`elevation.high` shadow. Items render as rows with optional leading icon
(danger-tinted if `destructive`) + label (danger-tinted if destructive).
Dividers between items. Tapping an item fires its `onClick` then dismisses.

**Screen patterns:** Long-press menus on list rows, "more" actions on cards,
row-level edit/delete/duplicate.

#### `ElToast` — top-of-screen toast / snackbar
**File:** `overlays/ElToast.kt`

```kotlin
@Composable
fun ElToast(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: ElToastTone = ElToastTone.NEUTRAL,
    durationMs: Long = 3000,
    onDismiss: () -> Unit = {},
)
```

**Tones** (`ToastTypes.kt`): `NEUTRAL` (primary), `SUCCESS`, `WARNING`,
`DANGER`, `INFO`. `toastAccent(tone)` resolves the accent color.

**Visual:** Top-anchored `AnimatedVisibility` that slides in from above +
fades. Body: full-width row, `ElNotificationShape` (16dp), `surface` bg,
`elevation.high` shadow. Left edge: 4×24dp accent bar. Optional icon (accent
tinted) + message (`bodyMedium`, `textPrimary`). Auto-dismisses after
`durationMs` (default 3s); `onDismiss` fires after the animation completes.

**Screen patterns:** Sync success/failure, payment recorded, form validation
errors, background sync status.

#### `ElTooltip` — short-lived hint
**File:** `overlays/ElTooltip.kt`

```kotlin
@Composable
fun ElTooltip(
    text: String,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    durationMs: Long = 2200,
    onDismiss: () -> Unit = {},
)
```

**Visual:** `AnimatedVisibility` (fade + scale 0.85→1.0). Box clipped to
`ElTooltipShape` (10dp), `inverseSurface` bg, `elevation.low` shadow, 10×6dp
padding. Text in `inverseOnSurface` color (`labelMedium`). Auto-dismisses after
`durationMs`.

> Caller is responsible for anchoring — wrap the target in a `Box` and place
> the tooltip at the desired alignment.

**Screen patterns:** Icon button hints, form field explanations, "what's this?"
on dashboard tiles.

---

## 4. Old → New component mapping (CRITICAL for restoration)

The destructive commit `933c139` wiped the legacy UI files. They were recovered
from `782bde1` to produce this mapping. The legacy package was
`com.example.ui.components` (3 files: `ElComponents.kt`, `ElComponentsExtended.kt`,
`ModernTabs.kt`). The new design system lives in
`com.example.ui.designsystem.*` and has **richer APIs** (more variants, sizes,
states, supporting types) — restoration team should expect signature changes,
not just package moves.

### 4.1 `ElComponents.kt` → modern equivalents

| Legacy composable (in v1 `ui/components/`) | Modern equivalent(s) (in `ui/designsystem/`) | Migration notes |
|--------------------------------------------|----------------------------------------------|-----------------|
| `ElCard(modifier, gradient, accent, compact, onClick, content)` | `components/card/ElCard.kt` → `ElCard(modifier, variant, size, shape, elevation, onClick, border, background, gradient, content)` | `gradient: Boolean` → `variant = ElCardVariant.GRADIENT`. `compact: Boolean` → `size = ElCardSize.COMPACT`. `accent: Color?` (left-edge bar) → **no direct equivalent**; wrap content in a `Row` with a 4dp `Box` of the accent color. |
| `ElButtonStyle { Primary, Secondary, Danger, Ghost }` | `ElButtonVariant { PRIMARY, SECONDARY, TONAL, OUTLINED, GHOST, DANGER }` | Added `TONAL` and `OUTLINED`. `Secondary` still exists. |
| `ElButton(text, onClick, modifier, style, enabled, loading, icon, fullWidth)` | `ElButton(text, onClick, modifier, variant, size, icon, iconEnd, enabled, loading, shape, fullWidth, interactionSource)` | `style: ElButtonStyle` → `variant: ElButtonVariant`. New `size`, `iconEnd`, `shape`, `interactionSource`. |
| `ElTextField(value, onValueChange, label, modifier, placeholder, leadingIcon, trailingIcon, singleLine, minLines, keyboardOptions, visualTransformation, isError, enabled, readOnly)` | `ElTextField(value, onValueChange, modifier, label, placeholder, leadingIcon, trailingIcon, onTrailingIconClick, helperText, errorText, isError, enabled, singleLine, keyboardOptions, visualTransformation, interactionSource)` | `trailingIcon` was a `@Composable` slot, now an `ImageVector` + `onTrailingIconClick`. `minLines` and `readOnly` parameters were dropped (use `singleLine = false` for multi-line; `enabled = false` for read-only). Added `helperText`, `errorText`, `onTrailingIconClick`. **FOCUSED state currently broken** — see §5. |
| `ElAvatar(...)` (v1 simple initials avatar) | `components/display/ElAvatar.kt` → `ElAvatar(modifier, imageUrl, initials, icon, size, accentColor, statusDot)` | v1 had no enum for size; v2 introduces `ElAvatarSize { XS, S, M, L, XL }`. Added `imageUrl` (Coil AsyncImage), `icon` fallback, `statusDot`. |
| `ElTag(text, ...)` | **REMOVED** — use `components/display/ElBadge` with `style = ElBadgeStyle.SOFT` or `ElChip(variant = ASSIST)` | No 1:1 replacement; `ElTag` semantics split between `ElBadge` (status display) and `ElChip` (interactive). |
| `ElProgressBar(progress, ...)` | `components/feedback/ElProgress.kt` → `ElLinearProgress(progress, modifier, trackColor, gradient, height)` | Renamed. Added theme-aware `trackColor` and `gradient`. |
| `ElBadge(text, ...)` (v1 simple badge) | `components/display/ElBadge.kt` → `ElBadge(text, modifier, tone, style, icon, dot)` | v1 had no tone/style enums. v2 introduces 8 tones × 3 styles = 24 combinations. Added `dot` and `icon` prefixes. |
| `ElTopBar(title, ...)` (v1) | `components/nav/ElTopBar.kt` → `ElTopBar(title, modifier, subtitle, onBack, actions)` | Added `subtitle` and `actions` slot. `onBack` is now a lambda (was an icon-based param). |
| `ElEmptyState(...)` (v1) | `components/feedback/ElEmptyState.kt` → `ElEmptyState(title, modifier, subtitle, icon, actionLabel, onAction)` | API roughly the same; now uses `ElButton` for the CTA. |
| `ElSectionHeader(title, ...)` | `components/display/ElDivider.kt` → `ElSectionDivider(label, modifier)` OR `gallery/GallerySection.kt` → `GallerySection(title, modifier, description, content)` | `ElSectionHeader` is now `ElSectionDivider`. For full section blocks (with content), use `GallerySection` (gallery-only) or just `Column { ElSectionDivider(...); content() }`. |
| `ElGradientHeader(title, subtitle, ...)` | **REMOVED** — compose manually: `ElCard(variant = ElCardVariant.GRADIENT) { Text(title, style = ElTheme.typography.displaySmall, color = ElTheme.colors.textOnColor); Text(subtitle, ...) }` | No direct replacement. The pattern is "gradient card with display type." |
| `ElInfoRow(label, value, ...)` | **REMOVED** — compose manually with `Row { Text(label, style = ElTheme.textStyles.overline); Spacer(weight=1f); Text(value, style = ElTheme.typography.bodyMedium) }` | No 1:1 replacement; trivial to compose from primitives. |

### 4.2 `ElComponentsExtended.kt` → modern equivalents

| Legacy composable | Modern equivalent(s) | Migration notes |
|-------------------|----------------------|-----------------|
| `ElScaffold(modifier, topBar, bottomBar, floatingActionButton, content)` | **REMOVED** — use stock `androidx.compose.material3.Scaffold` inside `ElImtiyazTheme {}`. The gallery does this in `gallery/ElGalleryActivity.kt`. | v1's `ElScaffold` painted a `heroBrush` background; the new system relies on `ElTheme.colors.background` + a `Box(Modifier.background(ElTheme.colors.background))` wrapper if you need the gradient. The FAB slot is also dropped — place an `ElFab` manually with `Modifier.align(Alignment.BottomEnd)`. |
| `ElFab(icon, onClick, modifier, contentDescription, gradient)` | `components/button/ElFab.kt` → `ElFab(icon, onClick, modifier, contentDescription, label, enabled, interactionSource)` | `gradient: Brush?` parameter dropped — FAB always uses `colors.primaryBrush`. Added `label` (extends to wide FAB) and `enabled`. |
| `ElStatCard(title, value, subtitle, icon, accentColor, modifier, onClick)` | `components/card/ElStatCard.kt` → `ElStatCard(label, value, modifier, icon, trend, trendPositive, accentColor, onClick)` | `title` renamed to `label`. `subtitle` renamed to `trend` and now renders in success/danger color based on `trendPositive`. Different layout (icon on top instead of beside title). |
| `ElListItem(title, subtitle, modifier, leading, trailing, onClick, accentColor)` | `components/data/ElList.kt` → `ElListItem(title, modifier, subtitle, leadingIcon, leadingAvatarUrl, leadingInitials, leadingTint, trailingText, trailingIcon, trailingBadge, onClick, selected, showDivider)` | `leading`/`trailing` were generic `@Composable` slots — now typed (icon/avatar/initials/badge/text). Much less flexible but more consistent. Lost: arbitrary composable trailing. Gained: `selected`, `showDivider`, badge shortcut. |
| `ElAlertSeverity { Info, Success, Warning, Danger }` | `ElToastTone { NEUTRAL, SUCCESS, WARNING, DANGER, INFO }` (in `overlays/ToastTypes.kt`) + `ElBadgeTone { …, INFO }` (in `components/display/BadgeTypes.kt`) | Severity concept split: transient alerts → `ElToast` + `ElToastTone`; inline alert badges → `ElBadge` + `ElBadgeTone`. |
| `ElAlertBanner(message, severity, modifier, title, onDismiss)` | **REMOVED as a discrete composable** — use `ElToast(message, tone = ..., icon = ..., onDismiss = ...)` for transient top-of-screen alerts, OR `ElCard(variant = ElCardVariant.FILLED)` with a colored leading bar for inline alert banners. | The closest modern primitive is `ElToast`. For persistent inline banners (e.g. "sync failed" at top of screen), compose manually: `Row { Box(4dp wide accent bar); Column { Text(title); Text(message) }; if (onDismiss != null) ElIconButton(Icons.Default.Close, onDismiss) }` inside an `ElCard`. |
| `ElDivider(modifier, thickness)` | `components/display/ElDivider.kt` → `ElDivider(modifier, verticalPadding)` | `thickness: Int` parameter dropped — always 1dp (`outlineVariant`). If you need a thicker divider, use `Modifier.height(N.dp).background(ElTheme.colors.outline)`. |
| `ElDialog(...)` (v1 dialog with gradient header) | `overlays/ElDialogShell.kt` → `ElDialogShell(onDismissRequest, modifier, dismissOnScrimTap, dismissOnBackPress, content)` + `overlays/ElDialogContent.kt` → `ElDialogContent(title, modifier, message, icon, actions)` | Split into shell + content. The "gradient header" is gone — dialogs now use flat `surface` + tinted shadow. For destructive confirms, use the pre-built `overlays/ElConfirmationDialog.kt`. |
| `ElDropdown(options, selectedIndex, onSelected, label, modifier, ...)` | `components/input/ElDropdown.kt` → `ElDropdown(options: List<ElDropdownOption>, selectedValue: String?, onSelected: (ElDropdownOption) -> Unit, modifier, label, placeholder, enabled)` | `selectedIndex: Int` → `selectedValue: String?` (key-based, not positional). Options are now `ElDropdownOption(value, label, icon)` data class instances, not just strings. |
| `ElScrollableTabRow(...)` | `components/tabs/ElTabs.kt` → `ElTabRow(tabs: List<String>, selectedIndex, onSelected, modifier)` | Renamed. `ElTabRow` does not currently support horizontal scrolling — see §5 (Missing). For now, keep tab counts low (≤5). |
| `ElGradientStatCard(...)` (v1 stat card with brand gradient bg) | `components/card/ElStatCard.kt` → `ElStatCard(...)` with `accentColor = ElTheme.colors.primary` AND wrap in `ElCard(variant = ElCardVariant.GRADIENT)` manually, OR just use `ElStatCard` (which is already `ELEVATED`). | The dedicated "gradient stat card" was removed. To recreate: put an `ElStatCard` *inside* an `ElCard(variant = GRADIENT)` and override colors for contrast. |
| `ElIconButton(icon, onClick, modifier, contentDescription, enabled, ...)` (v1) | `components/button/ElIconButton.kt` → `ElIconButton(icon, onClick, modifier, contentDescription, enabled, tint, background, size, iconSize, shape, interactionSource)` | API expanded with explicit `tint`, `background`, `size`, `iconSize`, `shape`. v1 used fixed 40dp circle; v2 defaults to 44dp circle (meeting Material 48dp touch target with padding). |

### 4.3 `ModernTabs.kt` → modern equivalents

| Legacy composable | Modern equivalent(s) | Migration notes |
|-------------------|----------------------|-----------------|
| `ModernSecondaryTabRow(tabs: List<String>, selectedTabIndex, onTabSelected, modifier)` | `components/tabs/ElTabs.kt` → `ElTabRow(tabs: List<String>, selectedIndex, onSelected, modifier)` | Direct 1:1 rename. Same pill indicator style. Drop-in replacement. |
| `ModernBottomNavBar(tabs: List<HubTab>, selectedTabIndex, onTabSelected, modifier)` | `components/nav/ElBottomBar.kt` → `ElBottomBar(destinations: List<ElNavDestination>, currentRoute: String, onNavigate: (String) -> Unit, modifier)` | **Significant API change.** v1 used `HubTab` + index-based selection; v2 uses `ElNavDestination` (route/label/icon/selectedIcon/badge) + route-string-based selection. Convert your `HubTab` list to `List<ElNavDestination>` and track `currentRoute: String` instead of `selectedTabIndex: Int`. The animated indicator pill style is preserved. |

### 4.4 Legacy themes (`ui/theme/`)

The destructive commit also wiped `app/src/main/java/com/example/ui/theme/`
(`Color.kt`, `Shapes.kt`, `Theme.kt`, `Type.kt`). The new design system
replaces all four with the `theme/` package documented in §1.

| Legacy file | Modern replacement |
|-------------|--------------------|
| `ui/theme/Color.kt` | `designsystem/theme/Color.kt` + `ElColorSchemes.kt` + `ElColors.kt` |
| `ui/theme/Shapes.kt` | `designsystem/theme/Shape.kt` |
| `ui/theme/Theme.kt` (`ElImtiyazTheme`) | `designsystem/theme/Theme.kt` (`ElImtiyazTheme` — same name, drop-in swap) |
| `ui/theme/Type.kt` | `designsystem/theme/Typography.kt` + `ElTextStyles.kt` |
| `elDesignTokens()` composable accessor | `ElTheme` object (`ElTheme.colors`, `ElTheme.spacing`, etc.) |

The README confirms: "Both themes are named `ElImtiyazTheme` and accept the
same parameters, so this is a one-line import swap."

```kotlin
// Before
import com.example.ui.theme.ElImtiyazTheme

// After
import com.example.ui.designsystem.theme.ElImtiyazTheme
```

---

## 5. What's MISSING from the design system

The restoration team will need these primitives, which are **not** present in
the current design system. Listed in rough priority order based on the wiped
legacy feature screens (CRM, Financials, Academics, Personnel, Settings):

### 5.1 High priority — needed by most feature screens

| Missing primitive | Why needed | Workaround until added |
|-------------------|------------|------------------------|
| **`ElScaffold` / screen scaffold wrapper** | Every screen needs a consistent shell (top bar + body + bottom bar + FAB slot + status-bar padding). v1 had `ElScaffold`; v2 dropped it. | Use stock `androidx.compose.material3.Scaffold` inside `ElImtiyazTheme {}` — the gallery does this. Add a small wrapper if you want gradient backgrounds. |
| **`ElSearchBar` / `ElSearchField`** | Every directory screen (Students, Parents, Employees, Audit Log) has a search field. Currently you'd compose `ElTextField` with a `Search` leading icon + `Close` trailing icon, but there's no built-in debounced search bar with collapse/expand. | Compose from `ElTextField(leadingIcon = Icons.Default.Search, trailingIcon = Icons.Default.Close, onTrailingIconClick = ::clear)`. Add `LaunchedEffect(value) { delay(300); onSearch(value) }` for debounce. |
| **`ElSwitch` / `ElToggleButton`** | Settings screen has dozens of toggles (notifications, sync, biometric auth, dark mode). No switch primitive exists. | Use stock `androidx.compose.material3.Switch` and wrap with `ElTheme.colors` if needed. |
| **`ElSlider`** | Settings (font size, sync interval, data retention) and academic (grade thresholds). Not present. | Use stock `androidx.compose.material3.Slider`. |
| **`ElCheckbox` / `ElRadioButton`** | Form screens (batch registration, attendance roll-call, RBAC matrix editor). Not present. | Use stock M3 `Checkbox` / `RadioButton` and wrap with theme colors. |
| **`ElDatePicker` / `ElTimePicker`** | Financials (payment due dates, installment schedules), Academics (calendar, homework due dates), Personnel (task deadlines). Not present. | Use `androidx.compose.material3.DatePicker` / `TimePicker` from M3 1.2+ (the BOM includes it). Wrap in `ElDialogShell` for consistent styling. |
| **`ElSlider`/`ElRangeSlider`** | Filter sheets (date range, amount range). Not present. | Use stock M3. |
| **Snackbar / persistent bottom banner** | `ElToast` is top-anchored and auto-dismisses after 3s. Many flows need a bottom-anchored snackbar with an action button (e.g. "Undo"). | Wrap `androidx.compose.material3.SnackbarHost` in `ElImtiyazTheme`, or use `ElToast` and accept the top-anchored style. |
| **`ElSegmentedControl`** | v1 used `ModernSecondaryTabRow` for this; `ElTabRow` works but lacks the segmented (iOS-style) appearance some designs need. | Use `ElTabRow` for now. |

### 5.2 Medium priority — needed by specific screens

| Missing primitive | Why needed |
|-------------------|------------|
| **`ElAccordion` / `ElExpansionPanel`** | Settings sub-sections, FAQ, filter groups in sheets. |
| **`ElCarousel`** | Dashboard hero banners, onboarding screens. Not present. |
| **`ElImagePicker` / `ElAvatarUploader`** | Profile photo upload, receipt scanner preview framing. Currently `ElAvatar` *displays* images but there's no built-in picker UI. |
| **`ElFileUpload` / drop zone** | Excel import modal (batch registration, expense import), receipt upload. |
| **`ElProgressRing` / circular determinate progress** | `ElSpinner` is indeterminate only. Storage usage, profile completion %, sync progress need determinate circular. |
| **`ElChart*` (bar, line, pie, sparkline)** | Dashboards (financial trends, attendance over time, debt by class). The desktop app has these; mobile has none. |
| **`ElCalendar` (month grid)** | Dashboard calendar widget (the desktop app has `dashboard-calendar.tsx`). |
| **`ElMapView`** | Driver dashboards (route visualization). Not present — likely use a third-party library. |
| **`ElBarcodeScanner` / `ElCameraPreview`** | Receipt scanner, student ID scanner. CameraX deps are in `build.gradle.kts` but no design-system wrapper. |
| **`ElRichText` / Markdown renderer** | Narrative generator (academic comments), AI responses. Not present. |
| **`ElCodeBlock`** | Settings → AI config tab. Not present. |
| **`ElKeyValue` / definition list** | Detail screens (student profile, employee profile). Currently composed manually. |
| **`ElBreadcrumbs`** | Multi-level navigation (Academics → Class → Student). Not present. |
| **`ElPagination` / page-size selector** | Long directories. Currently the table has no built-in pagination. |
| **`ElMultiSelectDropdown`** | Filter sheets (multiple grades, multiple roles). `ElDropdown` is single-select only. |
| **`ElCascadingDropdown`** | Location picker (country → region → city). Not present. |

### 5.3 Low priority — polish / nice-to-haves

| Missing primitive | Why needed |
|-------------------|------------|
| **`ElOTPInput`** | Phone auth, parent verification. |
| **`ElCreditCardInput`** | Counter payment screen. |
| **`ElMoneyInput`** with currency formatting | Financial entry — desktop has `money-input.tsx`. |
| **`ElPinMap` / location picker** | Driver dashboard. |
| **`ElColorPicker`** | Theme customization settings. |
| **`ElRatingBar`** | Teacher feedback, parent survey. |
| **`ElTimeline`** | Audit log visualization, workflow history. |
| **`ElTreeView`** | RBAC matrix editor, org chart. |
| **`ElKanbanBoard`** | Personnel task management. |
| **`ElGanttChart`** | Workflow page (desktop has `dag-canvas.tsx`). |

### 5.4 Known issues / bugs in current design system

These aren't "missing" but the restoration team should be aware:

1. **`ElTextField` FOCUSED state never triggers.** `FieldStyleResolver.kt`'s
   `resolveFieldState(enabled, isError)` only returns `DEFAULT`, `ERROR`, or
   `DISABLED` — never `FOCUSED`, even though `fieldBorderColor` and
   `fieldBorderWidth` have a `FOCUSED` branch. To fix: pass the
   `interactionSource.collectIsFocusedAsState()` into the resolver, or
   hard-code the focus border in `ElTextField`. **The legacy v1 ElTextField
   had animated focus borders** — this is a regression.

2. **`ElDialogShell` motion doesn't actually animate.** The `DialogSurface`
   uses `animateFloatAsState(targetValue = 1f, ...)` with no initial value, so
   the scale and alpha start at 1.0 and never change. The intended scale-in
   0.92→1.0 needs an `Animatable` with `LaunchedEffect(Unit)` or a
   `MutableState` flipped on first composition. Cosmetic, not blocking.

3. **`ElContextMenu` is anchored top-center, not to the trigger.** The
   composable fills the screen and centers the menu; callers can't pass an
   anchor position. v1 may have had different behavior — verify before
   restoring context menus on list rows.

4. **`ElTabRow` doesn't scroll horizontally.** With 6+ tabs, they'll get
   squished. The legacy `ElScrollableTabRow` had scroll support; the new
   `ElTabRow` does not.

5. **`ElListItem` lost generic composable slots.** v1's `leading` and
   `trailing` were `@Composable (() -> Unit)?` — you could put anything there.
   v2 only accepts typed params (icon/avatar/initials/badge/text). If you need
   a custom trailing (e.g. a toggle, a small chart), you'll need to fork
   `ElListItem` or use a raw `Row`.

6. **`ElStatCard` layout changed.** v1 had icon + title in a row; v2 stacks
   icon on top, then label, then value. If pixel-matching v1 dashboards
   matters, you'll need to tweak.

7. **No `ElScaffold`** — see §5.1. The gallery uses raw M3 `Scaffold`, which
   is fine but loses v1's gradient background. If you want the gradient back,
   wrap `Scaffold` in a `Box(Modifier.background(ElTheme.colors.heroBrush))`.

8. **`ElAlertBanner` is gone.** v1's inline alert banner (with severity
   colors and dismiss button) has no direct equivalent. `ElToast` is the
   closest but is transient and top-anchored.

9. **No public API barrel.** `ElDesignSystem.kt` is just version metadata
   (`VERSION = "1.0.0"`, `NAME = "Electric Violet & Sunshine"`). It does
   **not** re-export composables. You must import each composable from its
   full package path. (The README's "import this object for one-line access"
   comment is aspirational, not implemented.)

---

## 6. Build configuration

### 6.1 Current HEAD state

The destructive commit `933c139` deleted `settings.gradle.kts`,
`gradle/libs.versions.toml`, the `gradle/wrapper/` directory, `gradlew`,
`gradlew.bat`, `metadata.json`, `app/src/main/AndroidManifest.xml`,
`app/proguard-rules.pro`, `app/.gitignore`, `app/google-services.json`, all
`app/src/main/res/` resources, all `app/src/test/` and `app/src/androidTest/`
tests, and the entire `app/src/main/java/com/example/` tree except
`ui/designsystem/`.

**What survived at HEAD:**
- `README.md` (the design system docs, 461 lines)
- `app/build.gradle.kts` (the app module's build script, 207 lines)
- `gradle.properties` (project-wide Gradle settings)
- `app/src/main/java/com/example/ui/designsystem/` (the full 76-file design system)
- `app/src/test/java/com/example/GreetingScreenshotTest.kt` (a stray Roborazzi test stub — references a `Greeting` composable that no longer exists)

**What's MISSING at HEAD (must be restored before the app builds):**
- `settings.gradle.kts` — root project name + plugin/dependency repos + `:app` include
- `gradle/libs.versions.toml` — version catalog referenced by `app/build.gradle.kts` via `libs.*`
- `gradle/wrapper/gradle-wrapper.jar` + `gradle-wrapper.properties` — Gradle 8.8+ wrapper
- `gradlew` / `gradlew.bat` — wrapper scripts
- `app/src/main/AndroidManifest.xml` — application class, launcher activity, permissions, FCM service, WorkManager init
- `app/src/main/res/` — icons (`ic_launcher*`), strings, themes, backup rules
- `app/src/main/java/com/example/MainActivity.kt` — launcher (must host `ElImtiyazTheme` + nav host)
- `app/src/main/java/com/example/ElImtiyazApplication.kt` — Hilt application class
- `app/proguard-rules.pro` — ProGuard rules (referenced in `build.gradle.kts` line 58)
- `app/google-services.json` — Firebase config (referenced by `google-services` plugin)
- `app/.gitignore`
- `.env` / `.env.example` — Supabase config (referenced by `secrets` plugin)

### 6.2 `app/build.gradle.kts` (HEAD)

- **Plugins:** `android.application`, `kotlin.android`, `kotlin.compose`,
  `google.devtools.ksp`, `roborazzi`, `secrets` (maps-platform secrets-gradle-plugin),
  `google.services`, `hilt`, `kotlin.serialization`
- **Namespace:** `com.example`
- **Application ID:** `com.aistudio.elimtiyazstaff.bxmzlx`
- **compileSdk:** 36
- **minSdk:** 24
- **targetSdk:** 36
- **versionCode:** 2 · **versionName:** "2.0.0"
- **multiDexEnabled:** true
- **Java/Kotlin target:** 11 (with core library desugaring for `java.time` on API 24)
- **Signing:** release config reads keystore path from `KEYSTORE_PATH` env var
  (default `${rootDir}/my-upload-key.jks`) and passwords from
  `STORE_PASSWORD` / `KEY_PASSWORD` env vars. Debug config uses optional
  `debug.keystore` if present.
- **Build features:** `compose = true`, `buildConfig = true`
- **Test options:** `unitTests.isIncludeAndroidResources = true` (for Robolectric)
- **Secrets plugin:** reads `.env` then `.env.example`
- **BuildConfig fields:** `SUPABASE_URL`, `SUPABASE_ANON_KEY` (defaults; overridden by `.env`)

### 6.3 Dependencies (from `app/build.gradle.kts`, versioned via `libs.versions.toml`)

The version catalog file is missing at HEAD. The legacy `libs.versions.toml`
(recovered from `782bde1`) is what the build expects. Key versions:

| Category | Dependency | Version (from legacy catalog) |
|----------|------------|-------------------------------|
| **Build** | AGP | 8.8.0 |
| | Kotlin | 2.0.21 |
| | Compose BOM | 2024.09.00 |
| | KSP | 2.0.21-1.0.28 |
| **AndroidX** | core-ktx | 1.15.0 |
| | activity-compose | 1.10.1 |
| | lifecycle-runtime-compose / -viewmodel-compose | 2.8.7 |
| | navigation-compose | 2.8.9 |
| | room (ktx / runtime / compiler) | 2.7.0 |
| | work-runtime-ktx | 2.10.0 |
| | datastore-preferences | 1.1.7 |
| | security-crypto | 1.1.0-alpha06 |
| | camera-* | 1.5.0 |
| | multidex | 2.0.1 |
| **Compose** | material3, material-icons-core/extended, ui, ui-graphics, ui-tooling-preview | via BOM |
| **DI** | hilt-android | 2.52 |
| | hilt-navigation-compose, hilt-work | 1.2.0 |
| | hilt-compiler (androidx) | 1.2.0 |
| **Backend** | supabase-kt (auth / postgrest / realtime / storage / functions) | 3.1.1 |
| | ktor-client-android / -core | 3.0.3 |
| | kotlinx-serialization-json | 1.7.3 |
| | kotlinx-datetime | 0.6.1 |
| | kotlinx-coroutines-android / -core | 1.10.2 |
| **Networking (legacy)** | okhttp | 4.10.0 |
| | retrofit | 2.12.0 |
| | moshi-kotlin | 1.15.2 |
| **Images** | coil-compose | 2.7.0 |
| **Firebase** | firebase-bom | 34.15.0 |
| | firebase-messaging | 24.1.0 |
| | firebase-appcheck-recaptcha | via BOM |
| **Other** | accompanist-permissions | 0.37.3 |
| | play-services-location | 21.3.0 |
| **Testing** | robolectric | 4.16.1 |
| | roborazzi | 1.59.0 |
| | kotlinx-coroutines-test | 1.10.2 |
| | junit | 4.13.2 |
| | androidx.test.ext:junit | 1.3.0 |
| | espresso-core | 3.7.0 |
| | androidx.test:core / runner | 1.6.1 / 1.6.2 |

### 6.4 `gradle.properties` (HEAD)

```
org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8
org.gradle.parallel=true
kotlin.code.style=official
android.nonTransitiveRClass=true
org.gradle.caching=true
org.gradle.configuration-cache=true
org.gradle.workers.max=2
kotlin.compiler.execution.strategy=in-process
googleServices.missing.passthrough=true
android.useAndroidX=true
android.suppressUnsupportedCompileSdk=36
org.gradle.tooling.parallel=true
```

### 6.5 Legacy `settings.gradle.kts` (recovered from `782bde1`)

```kotlin
pluginManagement {
  repositories {
    google { content { /* com.android.*, com.google.*, androidx.* */ } }
    mavenCentral()
    gradlePluginPortal()
  }
}
plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories { google(); mavenCentral() }
}
rootProject.name = "El-Imtiyaz Staff"
include(":app")
```

### 6.6 Legacy `AndroidManifest.xml` (recovered from `782bde1`)

- **Application class:** `com.example.ElImtiyazApplication`
- **Launcher activity:** `.MainActivity` (`adjustResize` soft input)
- **Permissions:** INTERNET, ACCESS_NETWORK_STATE, CAMERA, POST_NOTIFICATIONS,
  READ_MEDIA_IMAGES, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION
- **FCM service:** `.infrastructure.notifications.ElImtiyazMessagingService`
- **WorkManager:** `InitializationProvider` with `WorkManagerInitializer` removed
  (deferred to on-demand)
- **Theme:** `@style/Theme.MyApplication` (was defined in `res/values/themes.xml`,
  now missing)

### 6.7 Restoration build checklist

To get the app building again, restoration team must:

1. Restore `settings.gradle.kts` (use the recovered legacy version above).
2. Restore `gradle/libs.versions.toml` (use the recovered legacy file).
3. Restore `gradle/wrapper/gradle-wrapper.{jar,properties}` and `gradlew{.bat}`.
4. Restore `app/src/main/AndroidManifest.xml` (use the recovered legacy file,
   but add the gallery activity registration if you want the gallery
   accessible — see `gallery/ElGalleryActivity.kt` for the snippet).
5. Restore `app/src/main/res/` (icons, strings, themes, backup rules).
6. Restore `app/proguard-rules.pro`.
7. Restore `app/google-services.json` (or accept the
   `googleServices.missing.passthrough=true` warning).
8. Restore `app/src/main/java/com/example/MainActivity.kt` — must host
   `ElImtiyazTheme` and a NavHost.
9. Restore `app/src/main/java/com/example/ElImtiyazApplication.kt` — must be
   `@HiltAndroidApp` and initialize WorkManager / Supabase client.
10. Restore `.env.example` with `SUPABASE_URL` and `SUPABASE_ANON_KEY` keys.
11. Delete the stray `app/src/test/java/com/example/GreetingScreenshotTest.kt`
    — references a non-existent `Greeting` composable; will fail to compile.

---

## 7. Integration notes — how feature screens consume the design system

### 7.1 Wrap your app in the theme

```kotlin
import com.example.ui.designsystem.theme.ElImtiyazTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ElImtiyazTheme {
                // App content — nav host, screens, etc.
                AppNavHost()
            }
        }
    }
}
```

`ElImtiyazTheme` follows system dark mode by default. Pass `darkTheme = false`
to force light, or `dynamicColor = true` (Android 12+) to use Material You
wallpaper colors (off by default to preserve the brand).

### 7.2 Read tokens via `ElTheme`

```kotlin
import com.example.ui.designsystem.theme.ElTheme

@Composable
fun MyScreen() {
    val c = ElTheme.colors          // ElColors
    val s = ElTheme.spacing         // ElSpacing
    val e = ElTheme.elevation       // ElElevation
    val b = ElTheme.borders         // ElBorders
    val m = ElTheme.motion          // ElMotion
    val t = ElTheme.typography      // Material 3 Typography
    val ts = ElTheme.textStyles     // ElTextStyles (numeric, overline, badge, …)
    val sh = ElTheme.shapes         // Material 3 Shapes
    val sc = ElTheme.shadowColor    // Color

    Box(
        Modifier
            .background(c.surface)
            .padding(s.lg)
    ) {
        Text("Hello", style = t.titleMedium, color = c.textPrimary)
    }
}
```

`ElTheme.colors` etc. are `@Composable @ReadOnlyComposable` getters — safe to
call from any composable, will recompose on theme change.

### 7.3 Use components

```kotlin
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.button.ElButtonSize
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElCardVariant
import com.example.ui.designsystem.components.card.ElStatCard
import com.example.ui.designsystem.components.data.ElListItem
import com.example.ui.designsystem.components.data.ElTable
import com.example.ui.designsystem.components.data.ElTableColumn
import com.example.ui.designsystem.components.data.ElTableRow
import com.example.ui.designsystem.components.display.ElBadge
import com.example.ui.designsystem.components.display.ElBadgeTone
import com.example.ui.designsystem.components.display.ElBadgeStyle
import com.example.ui.designsystem.components.feedback.ElEmptyState
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.components.nav.ElBottomBar
import com.example.ui.designsystem.components.nav.ElNavDestination
import com.example.ui.designsystem.overlays.ElConfirmationDialog
```

### 7.4 Use modifiers

```kotlin
import com.example.ui.designsystem.foundation.pressClickable
import com.example.ui.designsystem.foundation.noRippleClickable
import com.example.ui.designsystem.foundation.elShadow
import com.example.ui.designsystem.foundation.elGlass
import com.example.ui.designsystem.foundation.elBorder
import com.example.ui.designsystem.foundation.pressScale

Box(
    Modifier
        .clip(ElCardShape)
        .background(ElTheme.colors.surface)
        .elShadow(ElTheme.elevation.medium, ElCardShape)
        .elBorder(shape = ElCardShape)
        .pressClickable(onClick = { /* tap */ })
)
```

### 7.5 Use semantic shapes directly

```kotlin
import com.example.ui.designsystem.theme.ElCardShape
import com.example.ui.designsystem.theme.ElPillShape
import com.example.ui.designsystem.theme.ElButtonShape
// … (see §1.4 for the full list)
```

### 7.6 Use role colors for RBAC

```kotlin
val c = ElTheme.colors
val roleColor = c.role(session.user.role)  // "teacher" → c.roleTeacher (emerald)
ElAvatar(initials = user.initials, accentColor = roleColor)
```

### 7.7 Gallery preview

The design system ships a self-contained gallery for visual QA. Register the
activity in `AndroidManifest.xml`:

```xml
<activity
    android:name="com.example.ui.designsystem.gallery.ElGalleryActivity"
    android:exported="false" />
```

Launch via adb:
```bash
adb shell am start -n com.aistudio.elimtiyazstaff.bxmzlx/com.example.ui.designsystem.gallery.ElGalleryActivity
```

The gallery has 5 tabs: Foundations (colors / surfaces / type / spacing),
Buttons (variants / sizes / states / icon buttons / FABs), Inputs (text
fields / dropdowns / chips / badges), Surfaces (cards / stat cards / lists /
avatars / table / progress / empty state), Overlays (dialogs / sheets / menus
/ dividers).

### 7.8 Public API barrel — caveat

`ElDesignSystem.kt` is documented as a "public API barrel" but currently only
contains version metadata:

```kotlin
object ElDesignSystem {
    const val VERSION = "1.0.0"
    const val NAME = "Electric Violet & Sunshine"
}
```

It does **not** re-export composables. You must import each composable from
its full package path (e.g.
`com.example.ui.designsystem.components.button.ElButton`). If the restoration
team wants a true barrel, they'd need to add `val ElButton =
::ElButton`-style aliases or top-level re-export functions.

### 7.9 Stock Material 3 components still work

`ElImtiyazTheme` publishes a standard M3 `ColorScheme` via
`ElColors.toMaterialScheme()`. Any stock M3 component (`Scaffold`,
`LazyColumn`, `Switch`, `Checkbox`, `DatePicker`, `Slider`, `SnackbarHost`,
etc.) will pick up the brand colors automatically. Use these freely when the
design system doesn't have a branded equivalent (see §5 for the gap list).

---

## Appendix A — File manifest (76 Kotlin files)

```
designsystem/
├── ElDesignSystem.kt                       (version metadata)
├── theme/           (12 files)
│   ├── Color.kt
│   ├── ElColors.kt
│   ├── ElColorSchemes.kt
│   ├── ElTheme.kt
│   ├── Theme.kt
│   ├── Typography.kt
│   ├── ElTextStyles.kt
│   ├── Shape.kt
│   ├── Spacing.kt
│   ├── Elevation.kt
│   ├── Motion.kt
│   └── Borders.kt
├── foundation/      (5 files)
│   ├── Clickable.kt
│   ├── PressScaleModifier.kt
│   ├── ShadowModifier.kt
│   ├── GlassModifier.kt
│   └── BorderModifier.kt
├── components/
│   ├── button/      (5 files)  — ButtonTypes, ButtonStyleResolver, ElButton, ElIconButton, ElFab
│   ├── card/        (4 files)  — CardTypes, CardStyleResolver, ElCard, ElStatCard
│   ├── data/        (5 files)  — TableTypes, TableHeader, TableBodyRow, ElTable, ElList
│   ├── display/     (9 files)  — BadgeTypes, BadgePalettes, ElBadge, AvatarTypes, ElAvatar, ElDivider, ChipTypes, ElChip, ElChipGroup
│   ├── feedback/    (4 files)  — ElProgress, ElLoading, ElSkeleton, ElEmptyState
│   ├── input/       (7 files)  — TextFieldTypes, FieldStyleResolver, FieldParts, ElTextField, DropdownOption, DropdownParts, ElDropdown
│   ├── nav/         (5 files)  — NavTypes, NavItem, ElBottomBar, ElNavRail, ElTopBar
│   └── tabs/        (1 file)   — ElTabs (ElTabRow + ElVerticalTabList)
├── overlays/        (10 files) — ElDialogShell, ElDialogContent, ElConfirmationDialog, ElBottomSheet, ElSheetContent, ContextMenuTypes, ElContextMenu, ElTooltip, ToastTypes, ElToast
└── gallery/         (8 files)  — ElGalleryActivity, ElGalleryScreen, GallerySection, tabs/{FoundationsTab, ButtonsTab, InputsTab, SurfacesTab, OverlaysTab}
```

**Total: 76 Kotlin files · largest file 195 lines · ~5,600 LOC.**

## Appendix B — Survivors of the destructive commit

```
mobile/HEAD
├── README.md                                          (461 lines — design system docs)
├── gradle.properties                                  (Gradle config)
├── app/
│   ├── build.gradle.kts                               (207 lines — module build script)
│   └── src/
│       ├── main/java/com/example/ui/designsystem/     (76 files — the design system)
│       └── test/java/com/example/GreetingScreenshotTest.kt  (stray stub — references non-existent Greeting composable; should be deleted)
```

**Deleted by `933c139` and requiring restoration:** see §6.7 checklist.

---

*End of report. Generated by the Explore agent (modern design system) on
2026-08-01 for the El-Imtiyaz mobile restoration effort.*
