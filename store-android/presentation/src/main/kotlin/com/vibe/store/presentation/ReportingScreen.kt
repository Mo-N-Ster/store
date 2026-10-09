package com.vibe.store.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
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
import java.time.LocalDate
import java.time.ZoneId

private data class ReportFilterDraft(
    val tab: ReportTab,
    val fromDate: String,
    val throughDate: String,
    val grain: ReportGrain = ReportGrain.DAY,
    val productId: String = "",
    val category: String = "",
    val supplierId: String = "",
    val employeeId: String = "",
    val status: String = "",
)

private data class AppliedReport(val generation: Int, val filter: ReportFilter)
private sealed interface ReportScreenState {
    data object Draft : ReportScreenState
    data object Loading : ReportScreenState
    data object Empty : ReportScreenState
    data object Denied : ReportScreenState
    data object Error : ReportScreenState
    class Content(val generation: Int, val document: DocumentModel) : ReportScreenState
}

private val groupingTabs = setOf(ReportTab.SUMMARY, ReportTab.SALES, ReportTab.FINANCES)

private fun initialReportDraft(tab: ReportTab, zoneId: String): ReportFilterDraft {
    val today = LocalDate.now(ZoneId.of(zoneId))
    return ReportFilterDraft(tab, today.minusDays(6).toString(), today.toString())
}

private data class DraftValidation(val filter: ReportFilter?, val error: String? = null)

private fun validateReportDraft(draft: ReportFilterDraft, zoneId: String): DraftValidation {
    fun invalid(message: String) = DraftValidation(null, message)
    val from = runCatching { LocalDate.parse(draft.fromDate.trim()) }.getOrNull()
        ?: return invalid("date")
    val through = runCatching { LocalDate.parse(draft.throughDate.trim()) }.getOrNull()
        ?: return invalid("date")
    if (through.isBefore(from)) return invalid("date-order")
    val toExclusive = runCatching { through.plusDays(1) }.getOrNull() ?: return invalid("date")
    if (java.time.temporal.ChronoUnit.DAYS.between(from, toExclusive) > 3660) return invalid("date-range")
    if (draft.grain != ReportGrain.DAY && draft.tab !in groupingTabs) return invalid("grain")
    val productId = draft.productId.trim().takeIf(String::isNotEmpty)?.toLongOrNull()
        ?: if (draft.productId.isBlank()) null else return invalid("product")
    val supplierId = draft.supplierId.trim().takeIf(String::isNotEmpty)?.toLongOrNull()
        ?: if (draft.supplierId.isBlank()) null else return invalid("supplier")
    val employeeId = draft.employeeId.trim().takeIf(String::isNotEmpty)?.toLongOrNull()
        ?: if (draft.employeeId.isBlank()) null else return invalid("employee")
    if (productId != null && productId <= 0) return invalid("product")
    if (supplierId != null && supplierId <= 0) return invalid("supplier")
    if (employeeId != null && employeeId <= 0) return invalid("employee")
    val category = draft.category.trim().takeIf(String::isNotEmpty)
    if (category != null && category.length > 200) return invalid("category")
    val status = draft.status.trim().takeIf(String::isNotEmpty)
    val validStatuses = when (draft.tab) {
        ReportTab.SALES -> setOf("validated", "cancelled")
        ReportTab.PURCHASES -> setOf("DRAFT", "VALIDATED", "CANCELLED")
        ReportTab.TEAM -> setOf("VALID", "CORRECTED", "INTERRUPTED", "OPEN")
        ReportTab.STOCK -> setOf("sale", "invoice_reversal", "purchase", "purchase_cancellation", "inventory", "adjustment", "initial", "product_deletion")
        else -> emptySet()
    }
    if (status != null && status !in validStatuses) return invalid("status")
    if (draft.tab !in setOf(ReportTab.SALES, ReportTab.PRODUCTS, ReportTab.STOCK, ReportTab.PURCHASES) && productId != null)
        return invalid("product")
    if (draft.tab !in setOf(ReportTab.SALES, ReportTab.PRODUCTS, ReportTab.STOCK) && category != null)
        return invalid("category")
    if (draft.tab != ReportTab.PURCHASES && supplierId != null) return invalid("supplier")
    if (draft.tab != ReportTab.TEAM && employeeId != null) return invalid("employee")
    if (status != null && status !in validStatuses) return invalid("status")
    return runCatching {
        DraftValidation(ReportFilter(draft.tab, from, toExclusive, draft.grain, productId,
            category, supplierId, employeeId, status, 200, zoneId))
    }.getOrElse { invalid("filter") }
}

private fun statusOptions(tab: ReportTab): List<String> = when (tab) {
    ReportTab.SALES -> listOf("validated", "cancelled")
    ReportTab.PURCHASES -> listOf("DRAFT", "VALIDATED", "CANCELLED")
    ReportTab.TEAM -> listOf("VALID", "CORRECTED", "INTERRUPTED", "OPEN")
    ReportTab.STOCK -> listOf("sale", "invoice_reversal", "purchase", "purchase_cancellation", "inventory", "adjustment", "initial", "product_deletion")
    else -> emptyList()
}

/** Native, filter-driven reporting UI. All metrics and rows come from one immutable service snapshot. */
@Composable
fun ReportingScreen(service: ReportingService, identity: PublicIdentity, french: Boolean,
    modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    val zoneId = remember(identity.id) { ZoneId.systemDefault().id }
    var draft by remember(service, identity.id, zoneId) { mutableStateOf(initialReportDraft(ReportTab.SUMMARY, zoneId)) }
    var applied by remember(service, identity.id) { mutableStateOf<AppliedReport?>(null) }
    var generation by remember(service, identity.id) { mutableIntStateOf(0) }
    var retry by remember(service, identity.id) { mutableIntStateOf(0) }
    var state by remember(service, identity.id, identity.permissions) { mutableStateOf<ReportScreenState>(ReportScreenState.Draft) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    val permitted = "FINANCES:READ" in identity.permissions
    val validation = remember(draft, zoneId) { validateReportDraft(draft, zoneId) }
    fun label(fr: String, en: String) = if (french) fr else en

    DisposableEffect(lifecycle, identity.id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                state = ReportScreenState.Loading
                active = false
            } else if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) active = true
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(service, identity.id, identity.permissions, applied?.generation, retry, permitted, active) {
        val selected = applied ?: return@LaunchedEffect
        state = if (permitted) ReportScreenState.Loading else ReportScreenState.Denied
        if (!permitted) return@LaunchedEffect
        if (!active) return@LaunchedEffect
        try {
            val document = service.snapshot(selected.filter)
            currentCoroutineContext().ensureActive()
            state = if (document.rows.isEmpty() && document.series.isEmpty()) ReportScreenState.Empty
                else ReportScreenState.Content(selected.generation, document)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: SecurityFailure) {
            state = if (failure.code == SecurityError.FORBIDDEN) ReportScreenState.Denied else ReportScreenState.Error
        } catch (_: Exception) {
            state = ReportScreenState.Error
        }
    }

    Column(modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp)
        .testTag("reports-screen"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label("Rapports", "Reports"), style = MaterialTheme.typography.headlineSmall)
            if (onBack != null) TextButton(onClick = onBack, modifier = Modifier.testTag("reports-back")) {
                Text(label("Accueil", "Home"))
            }
        }
        if (!permitted) {
            ReportMessage(label("Accès aux rapports non autorisé.", "Report access is not authorized."), "reports-denied")
        } else {
            val tabs = ReportTab.entries
            val tabIndex = tabs.indexOf(draft.tab).coerceAtLeast(0)
            ScrollableTabRow(selectedTabIndex = tabIndex, modifier = Modifier.testTag("reports-tabs")) {
                tabs.forEach { tab ->
                    Tab(selected = tab == draft.tab, onClick = {
                        if (tab != draft.tab) {
                            draft = draft.copy(tab = tab, grain = ReportGrain.DAY,
                                productId = "", category = "", supplierId = "", employeeId = "", status = "")
                            applied = null
                            state = ReportScreenState.Draft
                        }
                    }, modifier = Modifier.testTag("report-tab-${tab.name}"),
                        text = { Text(tabLabel(tab, french), maxLines = 1) })
                }
            }
            Text(label("Filtres en préparation", "Draft filters"), style = MaterialTheme.typography.titleMedium)
            Text(label("Dates inclusives · fuseau", "Inclusive dates · timezone") + ": $zoneId",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DateField(label("Début (AAAA-MM-JJ)", "Start (YYYY-MM-DD)"), draft.fromDate,
                    Modifier.weight(1f), "reports-from") { draft = draft.copy(fromDate = it) }
                DateField(label("Fin incluse (AAAA-MM-JJ)", "Inclusive end (YYYY-MM-DD)"), draft.throughDate,
                    Modifier.weight(1f), "reports-through") { draft = draft.copy(throughDate = it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { draft = draft.copy(fromDate = LocalDate.now(ZoneId.of(zoneId)).minusDays(6).toString(), throughDate = LocalDate.now(ZoneId.of(zoneId)).toString()) },
                    modifier = Modifier.testTag("reports-preset-seven")) { Text(label("7 jours", "7 days")) }
                OutlinedButton(onClick = { draft = draft.copy(fromDate = LocalDate.now(ZoneId.of(zoneId)).minusDays(29).toString(), throughDate = LocalDate.now(ZoneId.of(zoneId)).toString()) },
                    modifier = Modifier.testTag("reports-preset-thirty")) { Text(label("30 jours", "30 days")) }
            }
            if (draft.tab in groupingTabs) {
                Text(label("Regroupement", "Grouping"), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportGrain.entries.forEach { grain ->
                        FilterChip(selected = draft.grain == grain, onClick = { draft = draft.copy(grain = grain) },
                            label = { Text(grain.name) }, modifier = Modifier.testTag("reports-grain-${grain.name}"))
                    }
                }
            } else {
                Text(label("Ce rapport utilise un regroupement quotidien.", "This report supports daily grouping only."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (draft.tab in setOf(ReportTab.SALES, ReportTab.PRODUCTS, ReportTab.STOCK, ReportTab.PURCHASES)) {
                DateField(label("ID produit (facultatif)", "Product ID (optional)"), draft.productId,
                    Modifier.fillMaxWidth(), "reports-product-id", KeyboardType.Number) { draft = draft.copy(productId = it) }
            }
            if (draft.tab in setOf(ReportTab.SALES, ReportTab.PRODUCTS, ReportTab.STOCK)) {
                DateField(label("Catégorie historique (facultatif)", "Historical category (optional)"), draft.category,
                    Modifier.fillMaxWidth(), "reports-category") { draft = draft.copy(category = it) }
            }
            if (draft.tab == ReportTab.PURCHASES) {
                DateField(label("ID fournisseur (facultatif)", "Supplier ID (optional)"), draft.supplierId,
                    Modifier.fillMaxWidth(), "reports-supplier-id", KeyboardType.Number) { draft = draft.copy(supplierId = it) }
            }
            if (draft.tab == ReportTab.TEAM) {
                DateField(label("ID employé (facultatif)", "Employee ID (optional)"), draft.employeeId,
                    Modifier.fillMaxWidth(), "reports-employee-id", KeyboardType.Number) { draft = draft.copy(employeeId = it) }
            }
            val statuses = statusOptions(draft.tab)
            if (statuses.isNotEmpty()) StatusPicker(draft.status, statuses, french,
                Modifier.testTag("reports-status")) { draft = draft.copy(status = it) }

            validation.error?.let { Text(validationMessage(it, french), Modifier.testTag("reports-filter-error"),
                color = MaterialTheme.colorScheme.error) }
            applied?.let { appliedFilter ->
                val filter = appliedFilter.filter
                val dimensions = buildList {
                    filter.productId?.let { add(label("Produit $it", "Product $it")) }
                    filter.category?.let { add(label("Catégorie historique $it", "Historical category $it")) }
                    filter.supplierId?.let { add(label("Fournisseur $it", "Supplier $it")) }
                    filter.employeeId?.let { add(label("Employé $it", "Employee $it")) }
                    filter.status?.let { add(label("Statut $it", "Status $it")) }
                }
                val details = if (dimensions.isEmpty()) "" else " · ${dimensions.joinToString(" · ")}"
                Text(label("Filtres appliqués", "Applied filters") + ": ${filter.fromInclusive} → ${filter.toExclusive.minusDays(1)} · ${filter.grain} · ${tabLabel(filter.tab, french)}$details",
                    Modifier.testTag("reports-applied-filters"), style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = validation.filter != null, onClick = {
                    validation.filter?.let { filter ->
                        generation++
                        applied = AppliedReport(generation, filter)
                        state = ReportScreenState.Loading
                    }
                }, modifier = Modifier.testTag("reports-apply")) { Text(label("Appliquer", "Apply")) }
                OutlinedButton(onClick = {
                    val defaults = initialReportDraft(draft.tab, zoneId)
                    val filter = validateReportDraft(defaults, zoneId).filter
                    if (filter != null) {
                        generation++
                        draft = defaults
                        applied = AppliedReport(generation, filter)
                        state = ReportScreenState.Loading
                    }
                }, modifier = Modifier.testTag("reports-reset")) { Text(label("Réinitialiser", "Reset")) }
            }
            when {
                !active -> ReportMessage(label("Rapports privés masqués.", "Private reports hidden."), "reports-stopped")
                else -> when (val current = state) {
                ReportScreenState.Draft -> ReportMessage(label("Modifiez les filtres puis appliquez-les pour charger un instantané.",
                    "Edit filters and apply them to load a snapshot."), "reports-draft")
                ReportScreenState.Loading -> ReportMessage(label("Chargement des rapports…", "Loading reports…"), "reports-loading")
                ReportScreenState.Empty -> ReportMessage(label("Aucune donnée pour les filtres appliqués.", "No data for the applied filters."), "reports-empty")
                ReportScreenState.Denied -> ReportMessage(label("Accès refusé. Aucune donnée de rapport n’est affichée.",
                    "Access denied. No report data is displayed."), "reports-denied")
                ReportScreenState.Error -> {
                    ReportMessage(label("Rapport indisponible.", "Report unavailable."), "reports-error")
                    OutlinedButton(onClick = { retry++ }, modifier = Modifier.testTag("reports-retry")) {
                        Text(label("Réessayer", "Retry"))
                    }
                }
                is ReportScreenState.Content -> if (applied?.generation == current.generation &&
                    current.document.tab == draft.tab) ReportDocument(current.document, french)
                }
            }
        }
    }
}

@Composable
private fun ReportMessage(text: String, tag: String) {
    Text(text, Modifier.fillMaxWidth().testTag(tag), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DateField(label: String, value: String, modifier: Modifier, tag: String,
    keyboard: KeyboardType = KeyboardType.Ascii, changed: (String) -> Unit) {
    OutlinedTextField(value, changed, modifier = modifier.testTag(tag), label = { Text(label) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = keyboard))
}

@Composable
private fun StatusPicker(value: String, values: List<String>, french: Boolean, modifier: Modifier,
    changed: (String) -> Unit) {
    var expanded by remember(values) { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.testTag("reports-status-button")) {
            Text(if (value.isEmpty()) if (french) "Tous les statuts" else "All statuses" else value)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(if (french) "Tous les statuts" else "All statuses") },
                onClick = { changed(""); expanded = false })
            values.forEach { status -> DropdownMenuItem(text = { Text(status) },
                onClick = { changed(status); expanded = false }) }
        }
    }
}

@Composable
private fun ReportDocument(document: DocumentModel, french: Boolean) {
    val unavailable = document.currencyAvailability == ReportCurrencyAvailability.UNAVAILABLE
    val metricCurrencyApplies = document.tab in setOf(ReportTab.SUMMARY, ReportTab.SALES, ReportTab.PRODUCTS, ReportTab.FINANCES) &&
        document.metrics.currency != null
    val monetarySeries = document.series.any { series ->
        val key = series.key.lowercase()
        key.contains("revenue") || key.contains("price") || key.contains("cost") || key.contains("amount")
    }
    val monetaryCells = document.rows.any { row -> row.cells.any { it.monetaryValue != null } }
    val hasMonetaryContent = metricCurrencyApplies || monetarySeries || monetaryCells
    val label: (String, String) -> String = { fr, en -> if (french) fr else en }
    Text(label("Instantané", "Snapshot") + " · ${tabLabel(document.tab, french)}",
        Modifier.testTag("reports-document-title"), style = MaterialTheme.typography.titleLarge)
    Text("${document.filter.fromInclusive} → ${document.filter.toExclusive.minusDays(1)} · ${document.filter.timeZoneId} · ${document.filter.grain}",
        Modifier.testTag("reports-document-period"), style = MaterialTheme.typography.bodySmall)
    Text(label("Jusqu’à 200 lignes affichées; les agrégats portent sur tous les résultats filtrés.",
        "Up to 200 rows shown; aggregates cover all filtered matches."),
        Modifier.testTag("reports-bounded-disclosure"), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (unavailable) {
        Text(label("Devise indisponible pour les montants historiques d’achat; ces montants ne sont pas assimilés à zéro.",
            "Currency unavailable for historical purchase amounts; unavailable values are not treated as zero."),
            Modifier.testTag("reports-currency-unavailable"), color = MaterialTheme.colorScheme.error)
    } else if (hasMonetaryContent) {
        Text(label("Devise", "Currency") + ": ${document.currency}", Modifier.testTag("reports-currency"),
            style = MaterialTheme.typography.labelLarge)
    }
    if (document.tab == ReportTab.PRODUCTS) {
        Text(label("Les catégories suivent l’instantané de la ligne de vente. L’historique de prix ne montre que les observations enregistrées; il est borné et aucune valeur manquante n’est inventée.",
            "Categories use the sale-line snapshot. Price history shows recorded observations only; it is bounded and missing values are not inferred."),
            Modifier.testTag("reports-product-history-disclosure"), style = MaterialTheme.typography.bodySmall)
    }
    if (document.tab in setOf(ReportTab.SUMMARY, ReportTab.SALES, ReportTab.PRODUCTS, ReportTab.FINANCES)) {
        ReportMetrics(document, french)
    }
    if (document.tab in setOf(ReportTab.SUMMARY, ReportTab.SALES, ReportTab.PRODUCTS, ReportTab.FINANCES) &&
        (document.metrics.estimatedGrossMargin.signum() != 0 ||
            document.rows.any { it.cells.firstOrNull()?.text == "estimatedGrossMargin" })) {
        Text(label("La marge brute est une estimation fondée sur les coûts disponibles; ce n’est pas un bénéfice certifié.",
            "Gross margin is estimated from available costs; it is not certified profit."),
            Modifier.testTag("reports-margin-disclosure"), style = MaterialTheme.typography.bodySmall)
    }
    document.series.forEach { series ->
        val seriesCurrencyUnavailable = unavailable &&
            !(series.key == "validatedRevenue" && document.metrics.currency != null)
        ReportSeriesCard(series, document.metrics.currency ?: document.currency, seriesCurrencyUnavailable, french)
    }
    var tableExpanded by remember(document.reportId, document.filter) { mutableStateOf(false) }
    OutlinedButton(onClick = { tableExpanded = !tableExpanded },
        modifier = Modifier.fillMaxWidth().testTag("reports-table-toggle")
            .semantics { contentDescription = if (tableExpanded) label("Réduire le tableau détaillé", "Collapse detailed table") else label("Développer le tableau détaillé", "Expand detailed table") }) {
        Text(if (tableExpanded) label("Masquer les détails", "Hide details") else label("Afficher les détails (${document.rows.size})", "Show details (${document.rows.size})"))
    }
    if (tableExpanded) {
        Text(label("Lignes de l’instantané", "Snapshot rows"), style = MaterialTheme.typography.titleMedium)
        if (document.rows.isEmpty()) ReportMessage(label("Aucune ligne.", "No rows."), "reports-no-rows")
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Content width excludes the screen's 16dp insets on both sides.
            val compactTable = maxWidth < 568.dp
            if (!compactTable) ReportTableHeader(document.columns, french)
            document.rows.forEachIndexed { rowIndex, row ->
                ElevatedCard(Modifier.fillMaxWidth().testTag("reports-row-$rowIndex")) {
                    if (compactTable) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            row.cells.forEachIndexed { cellIndex, cell ->
                                val title = document.columns.getOrNull(cellIndex)?.let { translatedColumn(it.key, it.label, french) }.orEmpty()
                                val value = reportCellValue(cell, cellIndex, unavailable, document.tab, french)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text(if (title.isNotBlank()) title else label("Valeur", "Value"),
                                        Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(value, Modifier.weight(1f).testTag("reports-cell-$rowIndex-$cellIndex"),
                                        style = MaterialTheme.typography.bodyLarge,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.End)
                                }
                            }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.Top) {
                            row.cells.forEachIndexed { cellIndex, cell ->
                                val value = reportCellValue(cell, cellIndex, unavailable, document.tab, french)
                                val columnKey = document.columns.getOrNull(cellIndex)?.key.orEmpty()
                                val numeric = columnKey in setOf("quantity", "hours", "revenue", "total", "value")
                                Text(value, Modifier.weight(reportColumnWeight(columnKey)).testTag("reports-cell-$rowIndex-$cellIndex"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = if (numeric) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start)
                            }
                            repeat((document.columns.size - row.cells.size).coerceAtLeast(0)) {
                                Spacer(Modifier.weight(reportColumnWeight(document.columns[row.cells.size].key)))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportTableHeader(columns: List<ReportColumn>, french: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp).testTag("reports-table-header"),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        columns.forEach { column ->
            val numeric = column.key in setOf("quantity", "hours", "revenue", "total", "value")
            Text(translatedColumn(column.key, column.label, french), Modifier.weight(reportColumnWeight(column.key)),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (numeric) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start)
        }
    }
}

private fun reportColumnWeight(key: String): Float = when (key) {
    "name", "supplier", "employee" -> 1.5f
    "date", "period", "status" -> 1.2f
    "quantity", "hours", "currency" -> 0.8f
    else -> 1f
}

private fun reportCellValue(cell: ReportCell, cellIndex: Int, unavailable: Boolean, tab: ReportTab, french: Boolean): String {
    val label = if (french) "Indisponible" else "Unavailable"
    val isMoney = cell.monetaryValue != null
    val isUnavailableText = unavailable && (cell.text.equals("Unavailable", true) || cell.text.contains("not calculated", true))
    return when {
        isUnavailableText -> label
        isMoney && unavailable && tab != ReportTab.FINANCES -> label
        isMoney -> DecimalInput.display(cell.monetaryValue!!, french)
        cellIndex == 0 -> translatedMetric(cell.text, french)
        else -> cell.text.ifBlank { "—" }
    }
}

@Composable
private fun ReportMetrics(document: DocumentModel, french: Boolean) {
    val m = document.metrics
    // Finance snapshots can have unavailable purchase currency while the sales metric still
    // carries its own authoritative currency snapshot.
    val currency = m.currency
    val monetaryMetricsAvailable = currency != null
    val cards = buildList {
        if (monetaryMetricsAvailable) add(Triple("revenue", if (french) "Chiffre d’affaires validé" else "Validated revenue", "$currency ${DecimalInput.display(m.revenue, french)}"))
        add(Triple("transactions", if (french) "Transactions validées" else "Validated transactions", m.transactionCount.toString()))
        if (monetaryMetricsAvailable) add(Triple("average-basket", if (french) "Panier moyen" else "Average basket", "$currency ${DecimalInput.display(m.averageBasket, french)}"))
        add(Triple("cancelled", if (french) "Ventes annulées" else "Cancelled sales", m.cancelledCount.toString()))
        if (monetaryMetricsAvailable) add(Triple("estimated-margin", if (french) "Marge brute estimée" else "Estimated gross margin", "$currency ${DecimalInput.display(m.estimatedGrossMargin, french)}"))
    }
    Text(if (french) "Indicateurs" else "Key metrics", style = MaterialTheme.typography.titleMedium)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = when { maxWidth < 448.dp -> 1; maxWidth < 808.dp -> 2; else -> 3 }
        val width = (maxWidth - 12.dp * (columns - 1)) / columns
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            cards.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (key, title, value) ->
                        ElevatedCard(Modifier.width(width).testTag("reports-kpi-$key")) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(value, Modifier.testTag("reports-kpi-value-$key"), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.width(width)) }
                }
            }
        }
    }
}

@Composable
private fun ReportSeriesCard(series: ReportSeries, currency: String, currencyUnavailable: Boolean, french: Boolean) {
    val isMoney = series.key == "validatedRevenue" || series.key.startsWith("productPrice:")
    val seriesLabel = translatedSeries(series.key, french)
    val numeric = remember(series, currency, currencyUnavailable, french) {
        series.points.mapIndexed { index, value ->
            val pointLabel = series.labels.getOrNull(index) ?: (index + 1).toString()
            val formatted = DecimalInput.display(value, french)
            if (isMoney && !currencyUnavailable) "$pointLabel · $currency $formatted" else "$pointLabel · $formatted"
        }
    }
    val description = numeric.joinToString("; ")
    val chartColor = MaterialTheme.colorScheme.primary
    SpatialPanel(Modifier.fillMaxWidth().testTag("reports-series-${series.key}")) {
        val unit = when {
            isMoney && !currencyUnavailable -> currency
            isMoney -> if (french) "montant · devise indisponible" else "amount · currency unavailable"
            series.key == "completedHours" -> if (french) "heures" else "hours"
            series.key.startsWith("movement") -> if (french) "unités" else "units"
            else -> if (french) "nombre" else "count"
        }
        Text("$seriesLabel ($unit)", style = MaterialTheme.typography.titleSmall)
        if (series.points.size <= 1) {
            numeric.singleOrNull()?.let { Text(it, Modifier.testTag("reports-series-values"), style = MaterialTheme.typography.bodyLarge) }
        } else {
            val min = series.points.minOrNull() ?: BigDecimal.ZERO
            val max = series.points.maxOrNull() ?: min
            val xLabels = "${series.labels.firstOrNull().orEmpty()}  —  ${series.labels.lastOrNull().orEmpty()}"
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${if (isMoney && !currencyUnavailable) "$currency " else ""}${DecimalInput.display(min, french)}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${if (isMoney && !currencyUnavailable) "$currency " else ""}${DecimalInput.display(max, french)}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Canvas(Modifier.fillMaxWidth().height(72.dp)
                .semantics { contentDescription = description }.testTag("reports-chart-${series.key}")) {
                val values = series.points
                val minimum = values.minOrNull() ?: BigDecimal.ZERO
                val range = (values.maxOrNull() ?: minimum).subtract(minimum)
                val scaled = values.map { value -> if (range.signum() == 0) 500 else
                    value.subtract(minimum).multiply(BigDecimal(1000)).divide(range, 0, RoundingMode.HALF_UP)
                        .intValueExact().coerceIn(0, 1000) }
                val dx = size.width / (scaled.size - 1)
                val points = scaled.mapIndexed { index, y -> Offset(index * dx, size.height - size.height * (y / 1000f)) }
                drawLine(chartColor.copy(alpha = 0.22f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1.dp.toPx())
                points.zipWithNext().forEach { (start, end) -> drawLine(chartColor, start, end, strokeWidth = 3.dp.toPx()) }
                points.forEach { drawCircle(chartColor, 3.dp.toPx(), it) }
            }
            Text(xLabels, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.fillMaxWidth()
                .height((numeric.size * 28).coerceAtMost(220).coerceAtLeast(28).dp)
                .testTag("reports-series-values")) {
                items(numeric) { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

private fun tabLabel(tab: ReportTab, french: Boolean) = when (tab) {
    ReportTab.SUMMARY -> if (french) "Synthèse" else "Summary"
    ReportTab.SALES -> if (french) "Ventes" else "Sales"
    ReportTab.PRODUCTS -> if (french) "Articles" else "Products"
    ReportTab.STOCK -> if (french) "Stock" else "Stock"
    ReportTab.PURCHASES -> if (french) "Achats" else "Purchases"
    ReportTab.TEAM -> if (french) "Équipe" else "Team"
    ReportTab.FINANCES -> if (french) "Finances" else "Finances"
}

private fun translatedColumn(key: String, fallback: String, french: Boolean) = when (key) {
    "metric" -> if (french) "Indicateur" else "Metric"
    "value" -> if (french) "Valeur" else "Value"
    "invoice" -> if (french) "Facture" else "Invoice"
    "date" -> if (french) "Date" else "Date"
    "status" -> if (french) "Statut" else "Status"
    "revenue" -> if (french) "Chiffre d’affaires reconnu" else "Recognized revenue"
    "kind" -> if (french) "Type" else "Kind"
    "name" -> if (french) "Article ou catégorie" else "Product or category"
    "quantity" -> if (french) "Quantité" else "Quantity"
    "period" -> if (french) "Date enregistrée" else "Recorded at"
    "reference" -> if (french) "Achat" else "Purchase"
    "total" -> if (french) "Total (indisponible)" else "Total (unavailable)"
    "supplier" -> if (french) "Fournisseur" else "Supplier"
    "employee" -> if (french) "Employé" else "Employee"
    "start" -> if (french) "Début" else "Start"
    "end" -> if (french) "Fin" else "End"
    "hours" -> if (french) "Heures terminées" else "Completed hours"
    "currency" -> if (french) "Devise" else "Currency"
    else -> fallback
}

private fun translatedMetric(value: String, french: Boolean) = when (value) {
    "validatedRevenue", "validatedSalesRevenue" -> if (french) "Ventes validées" else "Validated sales revenue"
    "validatedTransactions" -> if (french) "Transactions validées" else "Validated transactions"
    "averageBasket" -> if (french) "Panier moyen" else "Average basket"
    "cancelledTransactions" -> if (french) "Transactions annulées" else "Cancelled transactions"
    "estimatedGrossMargin" -> if (french) "Marge brute estimée" else "Estimated gross margin"
    "validatedPurchaseTotal" -> if (french) "Total des achats validés" else "Validated purchase total"
    "salesMinusPurchasesNotProfit" -> if (french) "Écart non calculé comme bénéfice" else "Sales less purchases (not profit)"
    "Unavailable" -> if (french) "Indisponible" else "Unavailable"
    else -> value
}

private fun translatedSeries(key: String, french: Boolean) = when (key) {
    "validatedRevenue" -> if (french) "Ventes validées" else "Validated revenue"
    "purchaseCountTotal" -> if (french) "Achats (total)" else "Purchases (total)"
    "purchaseCountValidated" -> if (french) "Achats validés" else "Validated purchases"
    "purchaseCountDraft" -> if (french) "Achats brouillons" else "Draft purchases"
    "purchaseCountCancelled" -> if (french) "Achats annulés" else "Cancelled purchases"
    "movementEntries" -> if (french) "Entrées" else "Stock entries"
    "movementExits" -> if (french) "Sorties" else "Stock exits"
    "sessions" -> if (french) "Sessions" else "Sessions"
    "completed" -> if (french) "Terminées" else "Completed"
    "open" -> if (french) "En cours" else "Open"
    "corrected" -> if (french) "Corrigées" else "Corrected"
    "interrupted" -> if (french) "Interrompues" else "Interrupted"
    "completedHours" -> if (french) "Heures terminées" else "Completed hours"
    else -> if (key.startsWith("productPrice:")) {
        val id = key.removePrefix("productPrice:")
        if (french) "Historique des prix · article #$id" else "Price history · product #$id"
    } else key
}

private fun validationMessage(error: String, french: Boolean) = when (error) {
    "date", "date-order", "date-range" -> if (french) "Dates invalides: utilisez AAAA-MM-JJ et une période maximale de dix ans." else "Invalid dates: use YYYY-MM-DD and a maximum ten-year period."
    "grain" -> if (french) "Ce regroupement n’est pas pris en charge pour ce rapport." else "This grouping is not supported for this report."
    "product" -> if (french) "L’ID produit doit être un entier positif pour ce rapport." else "Product ID must be a positive integer supported by this report."
    "category" -> if (french) "Cette catégorie n’est pas prise en charge ou dépasse 200 caractères." else "Category is unsupported for this report or exceeds 200 characters."
    "supplier" -> if (french) "L’ID fournisseur doit être positif et n’est disponible que pour les achats." else "Supplier ID must be positive and is supported only for purchases."
    "employee" -> if (french) "L’ID employé doit être positif et n’est disponible que pour l’équipe." else "Employee ID must be positive and is supported only for team reports."
    "status" -> if (french) "Le statut sélectionné n’est pas pris en charge pour ce rapport." else "The selected status is unsupported for this report."
    else -> if (french) "Combinaison de filtres non prise en charge." else "Unsupported filter combination."
}
