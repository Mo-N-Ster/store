package com.vibe.store.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vibe.store.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.math.BigDecimal
import java.math.RoundingMode

private sealed interface DashboardLoadState {
    data object Loading : DashboardLoadState
    data object Empty : DashboardLoadState
    data object Denied : DashboardLoadState
    data object Error : DashboardLoadState
    class Content(val request: DashboardRequest, val document: DocumentModel) : DashboardLoadState
}

/** The dashboard displays only the authorized snapshot returned by ReportingService. */
@Composable
fun DashboardScreen(service: ReportingService, identity: PublicIdentity, french: Boolean,
    modifier: Modifier = Modifier, timeSource: DashboardTimeSource? = null) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val clock = timeSource ?: remember(context) { AndroidDashboardTimeSource(context.applicationContext) }
    var timeContext by remember(clock, identity.id) { mutableStateOf(clock.current()) }
    val request = remember(timeContext) { DashboardRequest(timeContext.date, timeContext.zoneId) }
    val permitted = "DASHBOARD:READ" in identity.permissions
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var timeRecheck by remember(lifecycle, identity.id) { mutableIntStateOf(0) }
    var retry by remember(identity.id) { mutableIntStateOf(0) }
    var state by remember(service, identity.id, identity.permissions) { mutableStateOf<DashboardLoadState>(DashboardLoadState.Loading) }

    DisposableEffect(lifecycle, identity.id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                state = DashboardLoadState.Loading
                active = false
            } else if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                active = true
                if (event == Lifecycle.Event.ON_RESUME) timeRecheck++
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(clock, identity.id, active, permitted, timeRecheck) {
        if (!active || !permitted) return@LaunchedEffect
        while (true) {
            val current = clock.current()
            if (current != timeContext) {
                state = DashboardLoadState.Loading
                timeContext = current
            }
            clock.awaitChange(timeContext)
            val changed = clock.current()
            if (changed != timeContext) {
                state = DashboardLoadState.Loading
                timeContext = changed
            }
        }
    }

    LaunchedEffect(service, identity.id, identity.permissions, request, retry, active, permitted) {
        state = DashboardLoadState.Loading
        if (!active || !permitted) return@LaunchedEffect
        try {
            val result = service.dashboard(request)
            currentCoroutineContext().ensureActive()
            state = if (result.rows.isEmpty() && result.series.isEmpty()) DashboardLoadState.Empty
                else DashboardLoadState.Content(request, result)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: SecurityFailure) {
            state = if (failure.code == SecurityError.FORBIDDEN) DashboardLoadState.Denied
                else DashboardLoadState.Error
        } catch (_: Exception) {
            state = DashboardLoadState.Error
        }
    }

    Column(modifier.fillMaxWidth().testTag("dashboard-screen"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(if (french) "Tableau de bord" else "Dashboard", style = MaterialTheme.typography.headlineSmall)
        when {
            !permitted -> DashboardMessage(if (french) "Accès au tableau de bord non autorisé." else "Dashboard access is not authorized.", "dashboard-denied")
            !active -> DashboardMessage(if (french) "Données privées masquées." else "Private data hidden.", "dashboard-loading")
            else -> when (val current = state) {
                DashboardLoadState.Loading -> DashboardMessage(if (french) "Chargement…" else "Loading…", "dashboard-loading")
                DashboardLoadState.Denied -> DashboardMessage(if (french) "Accès au tableau de bord non autorisé." else "Dashboard access is not authorized.", "dashboard-denied")
                DashboardLoadState.Empty -> DashboardMessage(if (french) "Aucune donnée disponible pour cette période." else "No dashboard data is available for this period.", "dashboard-empty")
                DashboardLoadState.Error -> {
                    Text(if (french) "Tableau de bord indisponible." else "Dashboard unavailable.",
                        Modifier.testTag("dashboard-error"), color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { retry++ }, modifier = Modifier.testTag("dashboard-retry")) {
                        Text(if (french) "Réessayer" else "Retry")
                    }
                }
                is DashboardLoadState.Content -> if (current.request == request)
                    DashboardContent(current.document, french, "PRESENCE:READ" in identity.permissions)
                else DashboardMessage(if (french) "Chargement…" else "Loading…", "dashboard-loading")
            }
        }
    }
}

@Composable
private fun DashboardMessage(value: String, tag: String) {
    Text(value, Modifier.testTag(tag), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DashboardContent(document: DocumentModel, french: Boolean, allowPresence: Boolean) {
    val values = remember(document, allowPresence) { document.rows.mapNotNull { row ->
        val key = row.cells.getOrNull(0)?.text ?: return@mapNotNull null
        if (!allowPresence && key.startsWith("monthCompletedAttendance")) return@mapNotNull null
        if (!allowPresence && key.startsWith("monthOpenAttendance")) return@mapNotNull null
        val value = row.cells.getOrNull(1) ?: return@mapNotNull null
        key to value
    }.toMap() }
    val currency = if (document.currencyAvailability == ReportCurrencyAvailability.AVAILABLE)
        document.currency else if (french) "Devise indisponible" else "Currency unavailable"
    val metricLabels = listOf(
        "todayValidatedRevenue" to (if (french) "Ventes validées aujourd’hui" else "Today's validated sales"),
        "todayValidatedTransactions" to (if (french) "Transactions aujourd’hui" else "Today's transactions"),
        "todayAverageBasket" to (if (french) "Panier moyen aujourd’hui" else "Today's average basket"),
        "currentMonthValidatedRevenue" to (if (french) "Ventes validées ce mois" else "Month-to-date validated sales"),
        "outOfStockProducts" to (if (french) "Articles en rupture" else "Out-of-stock products"),
        "lowStockProducts" to (if (french) "Articles sous le seuil" else "Low-stock products"),
    )
    val moneyKeys = setOf("todayValidatedRevenue", "todayAverageBasket", "currentMonthValidatedRevenue")
    val metrics = metricLabels.mapNotNull { (key, label) -> values[key]?.let { Triple(key, label, it) } }.toMutableList()
    values["monthCompletedAttendanceSessions"]?.let {
        metrics += Triple("monthCompletedAttendanceSessions", if (french) "Présences terminées ce mois" else "Completed attendance this month", it)
    }
    values["monthOpenAttendanceSessions"]?.let {
        metrics += Triple("monthOpenAttendanceSessions", if (french) "Présences en cours ce mois" else "Open attendance this month", it)
    }
    values["monthCompletedAttendanceHours"]?.let {
        metrics += Triple("monthCompletedAttendanceHours", if (french) "Heures terminées ce mois" else "Completed hours this month", it)
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
      val columns = when {
          maxWidth < 600.dp -> 1
          maxWidth < 840.dp -> 2
          else -> 3
      }
      val widthLabel = when (columns) { 1 -> "COMPACT"; 2 -> "MEDIUM"; else -> "EXPANDED" }
      Column(Modifier.fillMaxWidth().testTag("dashboard-layout-$widthLabel"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text((if (french) "Devise : " else "Currency: ") + currency,
            Modifier.testTag("dashboard-currency"), style = MaterialTheme.typography.labelLarge)
        metrics.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (key, label, cell) ->
                    val value = cell.monetaryValue?.let { DecimalInput.display(it, french) } ?: cell.text
                    MetricCard(label, if (key in moneyKeys) "$currency $value" else value, Modifier.weight(1f))
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        val trend = document.series.firstOrNull { it.key == "validatedRevenue" }
        if (trend != null) TrendCard(trend, currency, french)
        Text(if (french) "Le chiffre d’affaires inclut uniquement les ventes validées. Les ventes annulées sont exclues. Toute marge affichée ailleurs est estimée." else "Revenue includes validated sales only; cancelled sales are excluded. Any margin shown elsewhere is estimated.",
            Modifier.testTag("dashboard-disclosure"), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier.testTag("dashboard-metric-${label.hashCode()}")) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun TrendCard(series: ReportSeries, currency: String, french: Boolean) {
    val description = remember(series, currency) {
        series.labels.zip(series.points).joinToString(separator = "; ") { (day, amount) -> "$day $currency ${DecimalInput.display(amount, french)}" }
    }
    val color = MaterialTheme.colorScheme.primary
    SpatialPanel(Modifier.fillMaxWidth()) {
        Text(if (french) "Tendance des ventes validées · 30 jours" else "Validated sales trend · 30 days",
            style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = description }.testTag("dashboard-trend-chart")) {
            val points = series.points
            if (points.isNotEmpty()) {
                val minimum = points.minOrNull() ?: BigDecimal.ZERO
                val maximum = points.maxOrNull() ?: minimum
                val range = maximum.subtract(minimum)
                val scaled = points.map { point ->
                    if (range.signum() == 0) 500 else point.subtract(minimum).multiply(BigDecimal(1000))
                        .divide(range, 0, RoundingMode.HALF_UP).intValueExact().coerceIn(0, 1000)
                }
                val dx = if (scaled.size <= 1) 0f else size.width / (scaled.size - 1)
                val coordinates = scaled.mapIndexed { index, y ->
                    Offset(index * dx, size.height - size.height * (y / 1000f))
                }
                coordinates.zipWithNext().forEach { (start, end) -> drawLine(color, start, end, strokeWidth = 3.dp.toPx()) }
                coordinates.forEach { drawCircle(color, 3.dp.toPx(), it) }
            }
        }
        Text(description, Modifier.testTag("dashboard-trend-numeric"), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
