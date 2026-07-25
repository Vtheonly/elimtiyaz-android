package com.elimtiyaz.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.elimtiyaz.app.navigation.Route
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.common.Role
import com.elimtiyaz.core.designsystem.ElimtiyazColors
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.AvatarCircle
import com.elimtiyaz.core.ui.ElImtiyazCard
import com.elimtiyaz.core.ui.EmptyState
import com.elimtiyaz.core.ui.ErrorState
import com.elimtiyaz.core.ui.LoadingState
import com.elimtiyaz.core.ui.OfflineBanner
import com.elimtiyaz.core.ui.StatusChip
import com.elimtiyaz.core.ui.StatusTone
import com.elimtiyaz.domain.model.AppNotification
import com.elimtiyaz.domain.model.DashboardKpi
import com.elimtiyaz.domain.model.DebtByAgingBucket
import com.elimtiyaz.domain.model.DemographicSlice
import com.elimtiyaz.domain.model.NotificationType
import com.elimtiyaz.domain.model.RevenuePoint

/**
 * Root dashboard hub — the first of the 5 bottom-nav tabs (master plan §03.05,
 * "Hub 1 Dashboard"). Renders a vertical scroll of:
 *
 *  1. Greeting header (Bonjour, {session.displayName} + role chip)
 *  2. Quick KPI grid (2×2): élèves / parents / revenu mensuel / créances en retard
 *  3. Revenue bar chart — last 12 months, Canvas-drawn, tap a bar to reveal its amount
 *  4. Debt by aging bucket — horizontal stacked bar + legend
 *  5. Demographics by level — donut chart (Canvas) with legend
 *  6. Recent alerts — top 3 notifications
 *
 * TopAppBar actions:
 *  - leading avatar → Profile
 *  - search icon → GlobalSearch
 *  - reports icon → Reports
 *  - bell with badge count → Alerts
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    nav: NavController,
    vm: DashboardViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tableau de bord") },
                navigationIcon = {
                    IconButton(onClick = { nav.navigate(Route.Profile.route) }) {
                        val initial = state.currentSession?.displayName?.firstOrNull()?.toString() ?: "?"
                        AvatarCircle(initial = initial, size = 32)
                    }
                },
                actions = {
                    IconButton(onClick = { nav.navigate(Route.GlobalSearch.route) }) {
                        Icon(Icons.Outlined.Search, contentDescription = "Rechercher")
                    }
                    IconButton(onClick = { nav.navigate(Route.Reports.route) }) {
                        Icon(Icons.Outlined.Assessment, contentDescription = "Rapports")
                    }
                    BadgedBox(
                        badge = {
                            if (state.unreadAlertsCount > 0) {
                                Badge { Text(state.unreadAlertsCount.toString()) }
                            }
                        },
                    ) {
                        IconButton(onClick = { nav.navigate(Route.Alerts.route) }) {
                            Icon(Icons.Outlined.Notifications, contentDescription = "Alertes")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (state.isOffline) OfflineBanner()
            when {
                state.isLoading && state.kpis == null -> LoadingState(message = "Chargement du tableau de bord…")
                state.error != null && state.kpis == null -> ErrorState(
                    error = state.error!!,
                    onRetry = { vm.refresh() },
                )
                else -> DashboardContent(state = state, vm = vm, nav = nav)
            }
        }
    }
}

/** The scrollable body of the dashboard. */
@Composable
private fun DashboardContent(
    state: DashboardUiState,
    vm: DashboardViewModel,
    nav: NavController,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = ElimtiyazSpacing.x4,
            vertical = ElimtiyazSpacing.x4,
        ),
        verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x4),
    ) {
        item { GreetingHeader(state.currentSession) }

        item { KpiGrid(kpis = state.kpis, nav = nav) }

        item { RevenueChartCard(revenueSeries = state.revenueSeries) }

        item { DebtByAgingCard(debtByAging = state.debtByAging) }

        item { DemographicsCard(demographics = state.demographics) }

        item {
            RecentAlertsCard(
                notifications = state.recentNotifications.take(3),
                onSeeAll = { nav.navigate(Route.Alerts.route) },
                onTap = { n -> navigateToNotificationEntity(n, nav) },
            )
        }
    }
}

/** "Bonjour, {name}" + role chip + a date stamp. */
@Composable
private fun GreetingHeader(session: com.elimtiyaz.core.common.Session?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = ElimtiyazSpacing.x2),
    ) {
        Text(
            text = "Bonjour,",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session?.displayName ?: "Invité",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(ElimtiyazSpacing.x1))
                if (session != null) {
                    StatusChip(
                        label = session.role.displayFr,
                        tone = roleTone(session.role),
                    )
                }
            }
            Text(
                text = Formatters.date(Formatters.nowIso()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Maps a [Role] to a status tone for the greeting chip. */
private fun roleTone(role: Role): StatusTone = when (role) {
    Role.SuperAdmin      -> StatusTone.Info
    Role.FinancialOfficer-> StatusTone.Info
    Role.Teacher         -> StatusTone.Success
    Role.SupportStaff    -> StatusTone.Neutral
    Role.Parent          -> StatusTone.Warning
    Role.Student         -> StatusTone.Warning
}

/** 2×2 KPI grid — Total élèves / Total parents / Revenu mensuel / Créances en retard. */
@Composable
private fun KpiGrid(kpis: DashboardKpi?, nav: NavController) {
    Column(verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3)) {
        Row(horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3)) {
            KpiCard(
                title = "Total élèves",
                value = kpis?.totalStudents?.toString() ?: "—",
                icon = Icons.Outlined.School,
                onClick = { nav.navigate(Route.Roster.route) },
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                title = "Total parents",
                value = kpis?.totalParents?.toString() ?: "—",
                icon = Icons.Outlined.Groups,
                onClick = { nav.navigate(Route.Roster.route) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3)) {
            KpiCard(
                title = "Revenu mensuel",
                value = kpis?.monthlyRevenue?.let { Formatters.currency(it) } ?: "—",
                icon = Icons.Outlined.Payments,
                onClick = { nav.navigate(Route.Financials.route) },
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                title = "Créances en retard",
                value = kpis?.outstandingDebt?.let { Formatters.currency(it) } ?: "—",
                icon = Icons.Outlined.ReportProblem,
                tone = ElimtiyazColors.DangerRed,
                onClick = { nav.navigate(Route.DebtDashboard.route) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Single tappable KPI card — icon chip + label + large value. */
@Composable
private fun KpiCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: Color = ElimtiyazColors.PrimaryBlue,
    onClick: () -> Unit,
) {
    ElImtiyazCard(modifier = modifier, onClick = onClick) {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(tone.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = tone, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(ElimtiyazSpacing.x2))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x3))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Revenue chart card — 12-month bar chart drawn with Compose Canvas. */
@Composable
private fun RevenueChartCard(revenueSeries: List<RevenuePoint>) {
    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Text(
                text = "Revenu — 12 derniers mois",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(ElimtiyazSpacing.x4))
            if (revenueSeries.isEmpty()) {
                EmptyState(
                    title = "Aucune donnée de revenu",
                    description = "Les encaissements apparaîtront ici dès qu'ils seront enregistrés.",
                    modifier = Modifier.height(180.dp),
                )
            } else {
                RevenueBarChart(data = revenueSeries)
            }
        }
    }
}

/** Canvas-drawn bar chart with tap-to-reveal value. */
@Composable
private fun RevenueBarChart(data: List<RevenuePoint>) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val maxAmount = remember(data) { data.maxOfOrNull { it.amount }?.coerceAtLeast(0.0) ?: 0.0 }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(data) {
                        detectTapGestures { offset ->
                            val barWidth = size.width / data.size
                            val idx = (offset.x / barWidth).toInt().coerceIn(0, data.size - 1)
                            selectedIndex = if (selectedIndex == idx) null else idx
                        }
                    },
            ) {
                drawRevenueBars(data, maxAmount, selectedIndex)
            }
            selectedIndex?.let { i ->
                val point = data[i]
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = ElimtiyazSpacing.x1)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = ElimtiyazSpacing.x3, vertical = ElimtiyazSpacing.x1),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = point.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = Formatters.currency(point.amount),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(ElimtiyazSpacing.x2))
        Row(modifier = Modifier.fillMaxWidth()) {
            data.forEachIndexed { i, point ->
                Text(
                    text = point.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == selectedIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    fontWeight = if (i == selectedIndex) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Draw the revenue bars on the canvas. */
private fun DrawScope.drawRevenueBars(
    data: List<RevenuePoint>,
    maxAmount: Double,
    selectedIndex: Int?,
) {
    if (data.isEmpty()) return
    val barWidth = size.width / data.size
    val barPadding = barWidth * 0.2f
    val chartHeight = size.height * 0.9f
    val baseline = size.height
    data.forEachIndexed { i, point ->
        val fraction = if (maxAmount > 0) (point.amount / maxAmount).toFloat() else 0f
        val barHeight = (fraction * chartHeight).coerceAtLeast(2.dp.toPx())
        val x = i * barWidth + barPadding / 2f
        val y = baseline - barHeight
        val isSelected = i == selectedIndex
        val color = if (isSelected) {
            ElimtiyazColors.PrimaryBlue
        } else {
            ElimtiyazColors.PrimaryBlue.copy(alpha = 0.4f)
        }
        drawRoundRect(
            color = color,
            topLeft = Offset(x, y),
            size = Size(barWidth - barPadding, barHeight),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
        )
    }
}

/** Debt-by-aging-bucket card — horizontal stacked bar + legend. */
@Composable
private fun DebtByAgingCard(debtByAging: List<DebtByAgingBucket>) {
    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Text(
                text = "Créances par tranche d'âge",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(ElimtiyazSpacing.x4))
            if (debtByAging.isEmpty()) {
                EmptyState(
                    title = "Aucune créance en souffrance",
                    description = "Toutes les tranches sont à jour.",
                    icon = Icons.Outlined.CheckCircle,
                    modifier = Modifier.height(120.dp),
                )
            } else {
                StackedDebtBar(debtByAging)
                Spacer(Modifier.height(ElimtiyazSpacing.x3))
                DebtLegend(debtByAging)
            }
        }
    }
}

/** Horizontal stacked bar drawn with Canvas — each bucket is a colored segment. */
@Composable
private fun StackedDebtBar(debtByAging: List<DebtByAgingBucket>) {
    val total = remember(debtByAging) { debtByAging.sumOf { it.amount }.coerceAtLeast(0.0) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (total <= 0.0) return@Canvas
            var startX = 0f
            debtByAging.forEachIndexed { i, bucket ->
                val fraction = (bucket.amount / total).toFloat()
                val segmentWidth = size.width * fraction
                drawRect(
                    color = agingColor(i),
                    topLeft = Offset(startX, 0f),
                    size = Size(segmentWidth, size.height),
                )
                startX += segmentWidth
            }
        }
    }
}

/** Color palette for the 5 aging buckets — graduated from cool blue → warm red. */
private fun agingColor(index: Int): Color = when (index % 5) {
    0 -> ElimtiyazColors.SuccessGreen
    1 -> ElimtiyazColors.LightBlue
    2 -> ElimtiyazColors.WarmGold
    3 -> ElimtiyazColors.WarningGold
    else -> ElimtiyazColors.DangerRed
}

/** Legend for the debt aging stacked bar — color dot + label + amount + debtor count. */
@Composable
private fun DebtLegend(debtByAging: List<DebtByAgingBucket>) {
    Column(verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2)) {
        debtByAging.forEachIndexed { i, bucket ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(agingColor(i)),
                )
                Spacer(Modifier.width(ElimtiyazSpacing.x3))
                Text(
                    text = bucket.bucket,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${bucket.debtorCount} débiteur(s)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(ElimtiyazSpacing.x4))
                Text(
                    text = Formatters.currency(bucket.amount),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Demographics card — donut chart by level + legend. */
@Composable
private fun DemographicsCard(demographics: List<DemographicSlice>) {
    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Text(
                text = "Effectifs par niveau",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(ElimtiyazSpacing.x4))
            if (demographics.isEmpty()) {
                EmptyState(
                    title = "Aucune donnée démographique",
                    description = "Les effectifs par niveau apparaîtront ici.",
                    modifier = Modifier.height(160.dp),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(140.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        DemographicsDonut(demographics)
                    }
                    Spacer(Modifier.width(ElimtiyazSpacing.x6))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3),
                    ) {
                        demographics.forEachIndexed { i, slice ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(demographicColor(i)),
                                )
                                Spacer(Modifier.width(ElimtiyazSpacing.x2))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = slice.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "${slice.count} élève(s) · ${"%.0f".format(slice.percent)}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Donut chart drawn with Canvas arcs — each slice is a colored arc segment. */
@Composable
private fun DemographicsDonut(demographics: List<DemographicSlice>) {
    val total = remember(demographics) { demographics.sumOf { it.count }.coerceAtLeast(1) }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val diameter = minOf(size.width, size.height)
        val stroke = diameter * 0.22f
        val arcSize = Size(diameter - stroke, diameter - stroke)
        val topLeft = Offset(stroke / 2f, stroke / 2f)
        var startAngle = -90f // start at 12 o'clock
        demographics.forEachIndexed { i, slice ->
            val sweep = if (total > 0) (slice.count.toFloat() / total.toFloat()) * 360f else 0f
            if (sweep > 0f) {
                drawArc(
                    color = demographicColor(i),
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
                startAngle += sweep
            }
        }
        // Center label — total
        // (drawn in the composable tree below, not on canvas, for proper text rendering)
    }
}

/** Color palette for the demographics slices. */
private fun demographicColor(index: Int): Color = when (index % 4) {
    0 -> ElimtiyazColors.PrimaryBlue
    1 -> ElimtiyazColors.LightBlue
    2 -> ElimtiyazColors.WarmGold
    else -> ElimtiyazColors.SuccessGreen
}

/** Recent alerts card — top 3 notifications + "Voir tout" link. */
@Composable
private fun RecentAlertsCard(
    notifications: List<AppNotification>,
    onSeeAll: () -> Unit,
    onTap: (AppNotification) -> Unit,
) {
    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Alertes récentes",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Voir tout",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onSeeAll),
                )
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x3))
            if (notifications.isEmpty()) {
                EmptyState(
                    title = "Aucune alerte",
                    description = "Vous êtes à jour.",
                    icon = Icons.Outlined.CheckCircle,
                    modifier = Modifier.height(120.dp),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2)) {
                    notifications.forEach { n ->
                        NotificationRow(notification = n, onClick = { onTap(n) })
                    }
                }
            }
        }
    }
}

/** Single notification row — icon + title + body + time + read/unread styling. */
@Composable
internal fun NotificationRow(
    notification: AppNotification,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isUnread = notification.readAt == null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isUnread) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(ElimtiyazSpacing.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(notificationTypeColor(notification.type).copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = notificationTypeIcon(notification.type),
                contentDescription = null,
                tint = notificationTypeColor(notification.type),
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(ElimtiyazSpacing.x3))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (isUnread) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(ElimtiyazColors.PrimaryBlue),
                    )
                }
            }
            Text(
                text = notification.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            Spacer(Modifier.height(ElimtiyazSpacing.x1))
            Text(
                text = Formatters.dateTime(notification.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Icon for a [NotificationType]. */
internal fun notificationTypeIcon(type: NotificationType): ImageVector = when (type) {
    NotificationType.PaymentOverdue  -> Icons.Outlined.Payments
    NotificationType.ExpensePending  -> Icons.Outlined.Assessment
    NotificationType.AttendanceAlert -> Icons.Outlined.School
    NotificationType.Homework        -> Icons.Outlined.Badge
    NotificationType.Audit           -> Icons.Outlined.CheckCircle
    NotificationType.System          -> Icons.Outlined.Notifications
    NotificationType.Message         -> Icons.Outlined.Person
}

/** Color for a [NotificationType]. */
internal fun notificationTypeColor(type: NotificationType): Color = when (type) {
    NotificationType.PaymentOverdue  -> ElimtiyazColors.DangerRed
    NotificationType.ExpensePending  -> ElimtiyazColors.WarningGold
    NotificationType.AttendanceAlert -> ElimtiyazColors.WarmGold
    NotificationType.Homework        -> ElimtiyazColors.PrimaryBlue
    NotificationType.Audit           -> ElimtiyazColors.SlateGray
    NotificationType.System          -> ElimtiyazColors.SlateGray
    NotificationType.Message         -> ElimtiyazColors.LightBlue
}

/** Status tone for a [NotificationType] (used by AlertsScreen filter chips). */
internal fun notificationTone(type: NotificationType): StatusTone = when (type) {
    NotificationType.PaymentOverdue  -> StatusTone.Danger
    NotificationType.ExpensePending  -> StatusTone.Warning
    NotificationType.AttendanceAlert -> StatusTone.Warning
    NotificationType.Homework        -> StatusTone.Info
    NotificationType.Audit           -> StatusTone.Neutral
    NotificationType.System          -> StatusTone.Info
    NotificationType.Message         -> StatusTone.Info
}

/** French label for a [NotificationType]. */
internal fun notificationTypeLabel(type: NotificationType): String = when (type) {
    NotificationType.PaymentOverdue  -> "Paiement en retard"
    NotificationType.ExpensePending  -> "Dépense en attente"
    NotificationType.AttendanceAlert -> "Présence"
    NotificationType.Homework        -> "Devoir"
    NotificationType.Audit           -> "Audit"
    NotificationType.System          -> "Système"
    NotificationType.Message         -> "Message"
}

/**
 * Resolve the deep-link target for a notification. Falls back to no-op when
 * the entity type is unknown — the parent screen still marks the notification
 * as read on tap.
 */
internal fun navigateToNotificationEntity(n: AppNotification, nav: NavController) {
    when (n.entityType) {
        "parent" -> n.entityId?.let { nav.navigate(Route.ParentDetail.build(it)) }
        "student" -> n.entityId?.let { nav.navigate(Route.StudentDetail.build(it)) }
        "payment" -> n.entityId?.let { nav.navigate(Route.PaymentDetail.build(it)) }
        "expense" -> n.entityId?.let { nav.navigate(Route.ExpenseDetail.build(it)) }
        "class" -> n.entityId?.let { nav.navigate(Route.ClassDetail.build(it)) }
        else -> { /* no destination — notification still marked read by the VM */ }
    }
}
