package com.vibe.store.infrastructure.persistence

import com.vibe.store.api.*
import com.vibe.store.application.reporting.ReportingRepository
import com.vibe.store.domain.*
import java.math.BigDecimal
import java.math.MathContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque

/** Parameterized, keyset-paged read projections. Every method stays inside RoomRepositories' lease. */
internal class RoomReportingRepository(
    private val dao: StoreDao,
    private val check: suspend (Boolean) -> Unit,
) : ReportingRepository {
    private val batchSize = 200

    override suspend fun snapshot(filter: ReportFilter): DocumentModel {
        check(false)
        val currency = currentCurrency()
        val sales = if (filter.tab in setOf(ReportTab.SUMMARY, ReportTab.SALES, ReportTab.PRODUCTS, ReportTab.FINANCES))
            salesMetrics(filter, currency) else emptySales(currency)
        val columns: List<ReportColumn>
        val rows: List<ReportRow>
        val series: List<ReportSeries>
        when (filter.tab) {
            ReportTab.SUMMARY -> {
                columns = listOf(ReportColumn("metric", "Metric"), ReportColumn("value", "Value"))
                rows = listOf(
                    metricRow("validatedRevenue", sales.metrics.revenue),
                    textMetricRow("validatedTransactions", sales.metrics.transactionCount.toString()),
                    metricRow("averageBasket", sales.metrics.averageBasket),
                    textMetricRow("cancelledTransactions", sales.metrics.cancelledCount.toString()),
                    metricRow("estimatedGrossMargin", sales.metrics.estimatedGrossMargin),
                )
                series = sales.dailySeries
            }
            ReportTab.SALES -> {
                columns = listOf(ReportColumn("invoice", "Invoice"), ReportColumn("date", "Date"), ReportColumn("status", "Status"), ReportColumn("revenue", "Recognized revenue"))
                rows = salesDisplay(filter)
                series = sales.dailySeries
            }
            ReportTab.PRODUCTS -> {
                columns = listOf(ReportColumn("kind", "Kind"), ReportColumn("name", "Product or category"), ReportColumn("quantity", "Quantity"), ReportColumn("revenue", "Validated revenue"))
                rows = rankedProductRows(filter)
                series = productPriceSeries(filter)
            }
            ReportTab.STOCK -> {
                val stockLimit = (filter.rowLimit + 1) / 2
                val movementLimit = filter.rowLimit / 2
                val stock = currentStock(filter, stockLimit)
                val movement = movementSummary(filter, movementLimit)
                columns = listOf(ReportColumn("kind", "Kind"), ReportColumn("name", "Product"), ReportColumn("quantity", "Quantity"), ReportColumn("period", "Recorded at"))
                rows = stock.rows + movement.rows
                series = listOf(ReportSeries("movementEntries", listOf(BigDecimal.valueOf(movement.entries))), ReportSeries("movementExits", listOf(BigDecimal.valueOf(movement.exits))))
            }
            ReportTab.PURCHASES -> {
                val purchase = purchaseSummary(filter)
                columns = listOf(ReportColumn("reference", "Purchase"), ReportColumn("date", "Created date"), ReportColumn("status", "Current status"), ReportColumn("total", "Total (unavailable)"), ReportColumn("supplier", "Supplier"))
                rows = purchase.rows
                series = listOf(
                    ReportSeries("purchaseCountTotal", listOf(BigDecimal.valueOf(purchase.total))),
                    ReportSeries("purchaseCountValidated", listOf(BigDecimal.valueOf(purchase.validated))),
                    ReportSeries("purchaseCountDraft", listOf(BigDecimal.valueOf(purchase.draft))),
                    ReportSeries("purchaseCountCancelled", listOf(BigDecimal.valueOf(purchase.cancelled))),
                )
            }
            ReportTab.TEAM -> {
                val team = teamSummary(filter)
                columns = listOf(ReportColumn("employee", "Employee"), ReportColumn("start", "Start"), ReportColumn("end", "End"), ReportColumn("status", "Status"), ReportColumn("hours", "Completed hours"))
                rows = team.rows
                series = team.statusCounts
            }
            ReportTab.FINANCES -> {
                columns = listOf(ReportColumn("metric", "Metric"), ReportColumn("value", "Value"), ReportColumn("currency", "Currency"))
                rows = listOf(
                    listRow("validatedSalesRevenue", sales.metrics.revenue, sales.currency),
                    unavailableListRow("validatedPurchaseTotal", "Unavailable"),
                    unavailableListRow("salesMinusPurchasesNotProfit", "Not calculated (purchase currency unavailable)"),
                )
                series = sales.dailySeries
            }
        }
        val currencyAvailability = if (filter.tab in setOf(ReportTab.PURCHASES, ReportTab.FINANCES))
            ReportCurrencyAvailability.UNAVAILABLE else ReportCurrencyAvailability.AVAILABLE
        val outputCurrency = if (currencyAvailability == ReportCurrencyAvailability.UNAVAILABLE) "XXX"
            else if (filter.tab in setOf(ReportTab.SUMMARY, ReportTab.SALES, ReportTab.PRODUCTS)) sales.currency else currency
        return DocumentModel("i09-${filter.tab.name.lowercase()}", filter.tab, filter, outputCurrency,
            columns, rows.take(filter.rowLimit), series, sales.metrics, currencyAvailability)
    }

    override suspend fun dashboard(filter: ReportFilter, includePresence: Boolean): DocumentModel {
        return dashboard(DashboardRequest.fromLegacyFilter(filter), includePresence)
    }

    override suspend fun dashboard(request: DashboardRequest, includePresence: Boolean): DocumentModel {
        check(false)
        val zone = ZoneId.of(request.timeZoneId)
        val today = request.asOfDate
        val tomorrow = today.plusDays(1)
        val monthStart = today.withDayOfMonth(1)
        val rollingStart = today.minusDays(29)
        val monthFilter = ReportFilter(ReportTab.SUMMARY, monthStart, tomorrow, ReportGrain.DAY,
            rowLimit = request.rowLimit, timeZoneId = request.timeZoneId)
        val trendFilter = ReportFilter(ReportTab.SUMMARY, rollingStart, tomorrow, ReportGrain.DAY,
            rowLimit = request.rowLimit, timeZoneId = request.timeZoneId)
        val currency = currentCurrency()
        val monthSales = salesMetrics(monthFilter, currency)
        val trendSales = salesMetrics(trendFilter, currency)
        if (monthSales.metrics.transactionCount > 0 && trendSales.metrics.transactionCount > 0 &&
            monthSales.currency != trendSales.currency) throw WorkflowFailure(WorkflowError.UNAVAILABLE)
        val todayRevenue = monthSales.byDate[today.toString()] ?: BigDecimal.ZERO
        val todayTransactions = monthSales.countByDate[today.toString()] ?: 0L
        val todayMetrics = metricsFor(todayRevenue, todayTransactions,
            monthSales.cancelledByDate[today.toString()] ?: 0L, monthSales.metrics.currency ?: currency)
        val stock = currentStock(monthFilter.copyForStock())
        val rows = ArrayList<ReportRow>()
        rows += metricRow("todayValidatedRevenue", todayMetrics.revenue)
        rows += textMetricRow("todayValidatedTransactions", todayMetrics.transactionCount.toString())
        rows += metricRow("todayAverageBasket", todayMetrics.averageBasket)
        rows += metricRow("currentMonthValidatedRevenue", monthSales.metrics.revenue)
        rows += textMetricRow("currentProducts", stock.productCount.toString())
        rows += textMetricRow("lowStockProducts", stock.lowStockCount.toString())
        rows += textMetricRow("outOfStockProducts", stock.outOfStockCount.toString())
        val series = ArrayList<ReportSeries>(trendSales.dailySeries)
        series += ReportSeries("currentMonthValidatedRevenue", listOf(monthSales.metrics.revenue), listOf(monthStart.toString()))
        val dashboardContext = DashboardContext(today, request.timeZoneId, today, tomorrow, monthStart, tomorrow,
            rollingStart, tomorrow)
        if (includePresence) {
            val monthlyTeam = teamSummary(ReportFilter(ReportTab.TEAM, monthStart, tomorrow, ReportGrain.DAY,
                rowLimit = request.rowLimit, timeZoneId = request.timeZoneId))
            rows += textMetricRow("monthCompletedAttendanceSessions", monthlyTeam.completed.toString())
            rows += textMetricRow("monthOpenAttendanceSessions", monthlyTeam.open.toString())
            rows += ReportRow(listOf(ReportCell("monthCompletedAttendanceHours"), ReportCell(
                monthlyTeam.statusCounts.single { it.key == "completedHours" }.points.single().toPlainString())))
        }
        val appliedFilter = trendFilter
        return DocumentModel("i09-dashboard", ReportTab.SUMMARY, appliedFilter,
            trendSales.currency, listOf(ReportColumn("metric", "Metric"), ReportColumn("value", "Value")),
            rows, series, todayMetrics, dashboardContext = dashboardContext)
    }

    private fun ReportFilter.copyForStock() = ReportFilter(
        ReportTab.STOCK, fromInclusive, toExclusive, grain, productId, category,
        rowLimit = rowLimit, timeZoneId = timeZoneId,
    )

    private data class SaleTotals(
        val metrics: ReportMetricSnapshot,
        val dailySeries: List<ReportSeries>,
        val currency: String,
        val byDate: Map<String, BigDecimal> = emptyMap(),
        val countByDate: Map<String, Long> = emptyMap(),
        val cancelledByDate: Map<String, Long> = emptyMap(),
    )

    private fun emptySales(currency: String) = SaleTotals(
        ReportMetricSnapshot(BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO, MarginDisclosure.ESTIMATED_SNAPSHOT_COST, currency),
        emptyList(), currency,
    )

    private fun metricsFor(revenue: BigDecimal, transactions: Long, cancelled: Long, currency: String): ReportMetricSnapshot {
        val average = if (transactions == 0L) BigDecimal.ZERO else try {
            revenue.divide(BigDecimal.valueOf(transactions))
        } catch (_: ArithmeticException) {
            revenue.divide(BigDecimal.valueOf(transactions), MathContext.DECIMAL128)
        }
        return ReportMetricSnapshot(ExactMoney.normalize(revenue), transactions, ExactMoney.normalize(average),
            cancelled, BigDecimal.ZERO, MarginDisclosure.ESTIMATED_SNAPSHOT_COST, currency)
    }

    private suspend fun salesMetrics(filter: ReportFilter, fallbackCurrency: String): SaleTotals {
        var cursor: String? = null
        var revenue = BigDecimal.ZERO
        var margin = BigDecimal.ZERO
        var transactions = 0L
        var cancelled = 0L
        var fallbackLines = 0L
        var resolvedCurrency: String? = null
        val byDate = sortedMapOf<String, BigDecimal>()
        val countByDate = sortedMapOf<String, Long>()
        val cancelledByDate = sortedMapOf<String, Long>()
        val (fromInstant, toInstant) = timestampBounds(filter)
        while (true) {
            check(false)
            val page = dao.reportInvoicesPage(
                fromInstant, toInstant, cursor,
                filter.status, filter.productId, filter.category, batchSize,
            )
            if (page.isEmpty()) break
            val ids = page.map { it.id }
            check(false)
            val lines = dao.reportInvoiceLines(ids).groupBy { it.invoiceId }
            for (invoice in page) {
                val currency = invoice.currency ?: throw WorkflowFailure(WorkflowError.UNAVAILABLE)
                if (resolvedCurrency == null) resolvedCurrency = currency
                else if (resolvedCurrency != currency) throw WorkflowFailure(WorkflowError.UNAVAILABLE)
                val invoiceLines = lines[invoice.id].orEmpty()
                val sale = invoice.toReportingSale(invoiceLines, ZoneId.of(filter.timeZoneId))
                if (sale.status == ReportingSaleStatus.CANCELLED) {
                    cancelled = Math.addExact(cancelled, 1L)
                    val date = invoiceLocalDate(invoice.invoiceDate, ZoneId.of(filter.timeZoneId)).toString()
                    cancelledByDate[date] = Math.addExact(cancelledByDate[date] ?: 0L, 1L)
                    continue
                }
                val allocations = ReportingCalculations.allocateDiscount(sale)
                var invoiceRevenue = BigDecimal.ZERO
                invoiceLines.forEachIndexed { index, line ->
                    if (!matchesLine(filter, line)) return@forEachIndexed
                    val amount = allocations[index].amount
                    invoiceRevenue = invoiceRevenue.add(amount)
                    val cost = line.unitCost ?: line.unitPrice.also { fallbackLines = Math.addExact(fallbackLines, 1L) }
                    margin = margin.add(amount.subtract(cost.multiply(BigDecimal.valueOf(line.quantity))))
                }
                revenue = revenue.add(invoiceRevenue)
                transactions = Math.addExact(transactions, 1L)
                val day = invoiceLocalDate(invoice.invoiceDate, ZoneId.of(filter.timeZoneId)).toString()
                byDate[day] = (byDate[day] ?: BigDecimal.ZERO).add(invoiceRevenue)
                countByDate[day] = Math.addExact(countByDate[day] ?: 0L, 1L)
            }
            cursor = page.last().id
            if (page.size < batchSize) break
        }
        val average = if (transactions == 0L) BigDecimal.ZERO else try {
            revenue.divide(BigDecimal.valueOf(transactions))
        } catch (_: ArithmeticException) {
            revenue.divide(BigDecimal.valueOf(transactions), MathContext.DECIMAL128)
        }
        val marginDisclosure = if (fallbackLines == 0L) MarginDisclosure.ESTIMATED_SNAPSHOT_COST
        else MarginDisclosure.ESTIMATED_WITH_PRICE_FALLBACK
        val dailyLabels = ArrayList<String>()
        val dailyValues = ArrayList<BigDecimal>()
        val grouped = linkedMapOf<String, BigDecimal>()
        var day = filter.fromInclusive
        while (day.isBefore(filter.toExclusive)) {
            val label = when (filter.grain) {
                ReportGrain.DAY -> day
                ReportGrain.WEEK -> day.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                ReportGrain.MONTH -> day.withDayOfMonth(1)
            }.toString()
            grouped[label] = (grouped[label] ?: BigDecimal.ZERO).add(byDate[day.toString()] ?: BigDecimal.ZERO)
            day = day.plusDays(1)
        }
        for ((label, value) in grouped) {
            dailyLabels += label
            dailyValues += value
        }
        return SaleTotals(
            ReportMetricSnapshot(
                ExactMoney.normalize(revenue), transactions, ExactMoney.normalize(average), cancelled,
                ExactMoney.normalize(margin), marginDisclosure, resolvedCurrency ?: fallbackCurrency,
            ),
            if (dailyLabels.isEmpty()) emptyList() else listOf(ReportSeries("validatedRevenue", dailyValues, dailyLabels)),
            resolvedCurrency ?: fallbackCurrency, byDate, countByDate, cancelledByDate,
        )
    }

    private fun matchesLine(filter: ReportFilter, line: InvoiceLineEntity) =
        (filter.productId == null || line.productId == filter.productId) &&
            (filter.category == null || line.category == filter.category)

    private fun InvoiceEntity.toReportingSale(lines: List<InvoiceLineEntity>, zone: ZoneId = ZoneId.systemDefault()) = ReportingSale(
        id = id,
        date = invoiceLocalDate(invoiceDate, zone),
        status = if (status == "validated") ReportingSaleStatus.VALIDATED else ReportingSaleStatus.CANCELLED,
        subtotal = subtotal,
        total = totalAmount,
        lines = lines.map { ReportingSaleLine(it.quantity, it.totalLine, it.unitPrice, it.unitCost) },
    )

    private suspend fun salesDisplay(filter: ReportFilter): List<ReportRow> {
        check(false)
        val (fromInstant, toInstant) = timestampBounds(filter)
        val page = dao.reportInvoiceDisplay(fromInstant, toInstant,
            filter.status, filter.productId, filter.category, filter.rowLimit)
        if (page.isEmpty()) return emptyList()
        check(false)
        val lines = dao.reportInvoiceLines(page.map { it.id }).groupBy { it.invoiceId }
        return page.map { invoice ->
            val invoiceLines = lines[invoice.id].orEmpty()
            var allocatedRevenue = BigDecimal.ZERO
            if (invoice.status == "validated") {
                val allocation = ReportingCalculations.allocateDiscount(invoice.toReportingSale(invoiceLines, ZoneId.of(filter.timeZoneId)))
                invoiceLines.forEachIndexed { index, line -> if (matchesLine(filter, line)) allocatedRevenue = allocatedRevenue.add(allocation[index].amount) }
            }
            val recognized = invoice.status == "validated"
            ReportRow(listOf(
                ReportCell(invoice.id), ReportCell(invoice.invoiceDate), ReportCell(invoice.status),
                if (recognized) ReportCell(allocatedRevenue.toPlainString(), allocatedRevenue) else ReportCell("Excluded"),
            ))
        }
    }

    private data class Ranked(val id: Long, val label: String, val category: String, var quantity: Long = 0, var revenue: BigDecimal = BigDecimal.ZERO)

    private suspend fun rankedProductRows(filter: ReportFilter): List<ReportRow> {
        val productLimit = (filter.rowLimit + 1) / 2
        val categoryLimit = filter.rowLimit / 2
        val products = rankSalesLines(filter, byCategory = false, productLimit)
        val categories = if (categoryLimit == 0) emptyList() else rankSalesLines(filter, byCategory = true, categoryLimit)
        return (products.map { rankedRow("PRODUCT", it.label, it.quantity, it.revenue) } +
            categories.map { rankedRow("CATEGORY", it.label, it.quantity, it.revenue) })
    }

    /** Price curves use only persisted observations; rows and series are capped before returning. */
    private suspend fun productPriceSeries(filter: ReportFilter): List<ReportSeries> {
        val selectedProductId = filter.productId
        // Price observations have no category snapshot. When category is supplied, first
        // establish candidate products from historical invoice-line category snapshots.
        val ranked = if (selectedProductId != null && filter.category != null)
            rankSalesLines(filter, byCategory = false, 1)
        else if (selectedProductId != null) listOf(Ranked(selectedProductId, "", ""))
        else rankSalesLines(filter, byCategory = false, minOf(10, filter.rowLimit))
        if (ranked.isEmpty()) return emptyList()
        val ids = ranked.map { it.id }.distinct()
        check(false)
        val (fromInstant, toInstant) = timestampBounds(filter)
        val observations = dao.reportPriceHistory(ids, fromInstant, toInstant, 200)
        val byProduct = observations.groupBy { it.productId }
        return ranked.mapNotNull { product ->
            val points = byProduct[product.id].orEmpty().sortedWith(compareBy<PriceEntity> { it.recordedAt }.thenBy { it.id })
            if (points.isEmpty()) null else ReportSeries(
                "productPrice:${product.id}", points.map { it.price }, points.map { it.recordedAt.take(40) },
            )
        }.take(10)
    }

    /** Streams line pages ordered by group; memory is limited to one batch, one active group and top N. */
    private suspend fun rankSalesLines(filter: ReportFilter, byCategory: Boolean, cap: Int): List<Ranked> {
        if (cap == 0) return emptyList()
        val top = ArrayList<Ranked>(cap)
        var afterProduct: Long? = null
        var afterCategory: String? = null
        var afterLine = 0L
        var current: Ranked? = null
        while (true) {
            check(false)
            val page = if (byCategory) dao.reportCategorySalesLines(
                timestampBounds(filter).first, timestampBounds(filter).second, filter.productId,
                filter.category, afterCategory, afterProduct ?: 0, afterLine, batchSize,
            ) else dao.reportProductSalesLines(
                timestampBounds(filter).first, timestampBounds(filter).second, filter.productId,
                filter.category, afterProduct, afterLine, batchSize,
            )
            if (page.isEmpty()) break
            val invoiceIds = page.map { it.invoiceId }.distinct()
            check(false)
            val invoices = dao.reportInvoices(invoiceIds).associateBy { it.id }
            check(false)
            val allLines = dao.reportInvoiceLines(invoiceIds).groupBy { it.invoiceId }
            val allocations = HashMap<Long, BigDecimal>(page.size)
            for (id in invoiceIds) {
                val invoice = invoices[id] ?: continue
                val invoiceLines = allLines[id].orEmpty()
                val values = ReportingCalculations.allocateDiscount(invoice.toReportingSale(invoiceLines, ZoneId.of(filter.timeZoneId)))
                invoiceLines.forEachIndexed { index, line -> allocations[line.id] = values[index].amount }
            }
            for (line in page) {
                val groupId = if (byCategory) 0L else line.productId
                val groupLabel = if (byCategory) line.category else line.productName
                val group = current
                if (group == null || group.id != groupId || (byCategory && group.label != groupLabel)) {
                    if (group != null) offer(top, group, cap)
                    current = Ranked(groupId, groupLabel, line.category)
                }
                val active = checkNotNull(current)
                active.quantity = Math.addExact(active.quantity, line.quantity)
                active.revenue = active.revenue.add(allocations[line.lineId] ?: BigDecimal.ZERO)
            }
            val last = page.last()
            afterProduct = last.productId
            afterCategory = last.category
            afterLine = last.lineId
            if (page.size < batchSize) break
        }
        current?.let { offer(top, it, cap) }
        return top.sortedWith(compareByDescending<Ranked> { it.revenue }.thenBy { it.label }.thenBy { it.id })
    }

    private fun offer(top: MutableList<Ranked>, candidate: Ranked, cap: Int) {
        if (top.size < cap) { top += candidate; return }
        val worst = top.indices.minWithOrNull(compareBy<Int> { top[it].revenue }.thenByDescending { top[it].label }) ?: return
        val old = top[worst]
        if (candidate.revenue > old.revenue || (candidate.revenue.compareTo(old.revenue) == 0 && candidate.label < old.label)) top[worst] = candidate
    }

    private data class StockSummary(val rows: List<ReportRow>, val productCount: Long, val lowStockCount: Long, val outOfStockCount: Long, val units: Long)
    private suspend fun currentStock(filter: ReportFilter, rowLimit: Int = filter.rowLimit): StockSummary {
        val rows = ArrayList<ReportRow>(rowLimit)
        var cursor: Long? = null
        var count = 0L; var low = 0L; var out = 0L; var units = 0L
        while (true) {
            check(false)
            val page = dao.reportProductsPage(cursor, filter.productId, filter.category, batchSize)
            for (product in page) {
                count = Math.addExact(count, 1L)
                units = Math.addExact(units, product.stock)
                if (product.stock <= product.minimumStock) low = Math.addExact(low, 1L)
                if (product.stock == 0L) out = Math.addExact(out, 1L)
                if (rows.size < rowLimit) rows += ReportRow(listOf(
                    ReportCell("CURRENT"), ReportCell("${product.name} · ${product.category}"), ReportCell(product.stock.toString()), ReportCell("minimum ${product.minimumStock}"),
                ))
            }
            if (page.isEmpty() || page.size < batchSize) break
            cursor = page.last().id
        }
        return StockSummary(rows, count, low, out, units)
    }

    private data class MovementSummary(val rows: List<ReportRow>, val entries: Long, val exits: Long, val count: Long)
    private suspend fun movementSummary(filter: ReportFilter, rowLimit: Int = filter.rowLimit): MovementSummary {
        var cursor: Long? = null; var entries = 0L; var exits = 0L; var count = 0L
        val recent = ArrayDeque<ReportRow>()
        val (fromInstant, toInstant) = timestampBounds(filter)
        while (true) {
            check(false)
            val page = dao.reportMovementsPage(fromInstant, toInstant, cursor,
                filter.productId, filter.category, filter.status, batchSize)
            for (movement in page) {
                count = Math.addExact(count, 1L)
                if (movement.quantity > 0) entries = Math.addExact(entries, movement.quantity)
                if (movement.quantity < 0) exits = Math.addExact(exits, Math.negateExact(movement.quantity))
                if (rowLimit > 0) {
                    if (recent.size == rowLimit) recent.removeFirst()
                    recent.addLast(ReportRow(listOf(
                        ReportCell(movement.reason), ReportCell(movement.productName), ReportCell(movement.quantity.toString()), ReportCell(movement.createdAt),
                    )))
                }
            }
            if (page.isEmpty() || page.size < batchSize) break
            cursor = page.last().id
        }
        return MovementSummary(recent.toList().asReversed(), entries, exits, count)
    }

    private data class PurchaseSummary(val rows: List<ReportRow>, val total: Long, val validated: Long, val draft: Long, val cancelled: Long)
    private suspend fun purchaseSummary(filter: ReportFilter): PurchaseSummary {
        check(false)
        val (fromInstant, toInstant) = timestampBounds(filter)
        val display = dao.reportPurchaseDisplay(fromInstant, toInstant, filter.status,
            filter.supplierId, filter.productId, filter.rowLimit)
        val rows = display.map { purchase ->
            ReportRow(listOf(ReportCell(purchase.reference), ReportCell(purchase.createdAt), ReportCell(purchase.status), ReportCell("Unavailable"), ReportCell(purchase.supplierName)))
        }
        var cursor: Long? = null
        var total = 0L; var validated = 0L; var draft = 0L; var cancelled = 0L
        while (true) {
            check(false)
            val page = dao.reportPurchaseCountsPage(fromInstant, toInstant, cursor, filter.status,
                filter.supplierId, filter.productId, batchSize)
            for (purchase in page) {
                total = Math.addExact(total, 1L)
                when (purchase.status) {
                    "VALIDATED" -> validated = Math.addExact(validated, 1L)
                    "DRAFT" -> draft = Math.addExact(draft, 1L)
                    "CANCELLED" -> cancelled = Math.addExact(cancelled, 1L)
                    else -> throw WorkflowFailure(WorkflowError.UNAVAILABLE)
                }
            }
            if (page.isEmpty() || page.size < batchSize) break
            cursor = page.last().id
        }
        return PurchaseSummary(rows, total, validated, draft, cancelled)
    }

    private data class TeamSummary(val rows: List<ReportRow>, val completed: Long, val open: Long, val statusCounts: List<ReportSeries>)
    private suspend fun teamSummary(filter: ReportFilter): TeamSummary {
        var cursor: Long? = null; var count = 0L; var completed = 0L; var open = 0L; var corrected = 0L; var interrupted = 0L
        var hours = BigDecimal.ZERO
        val (startInclusive, endExclusive) = attendanceBounds(filter)
        val onlyOpen = filter.status == "OPEN"
        val status = filter.status?.takeUnless { it == "OPEN" }
        while (true) {
            check(false)
            val page = dao.reportAttendancePage(startInclusive, endExclusive, cursor, filter.employeeId, status, onlyOpen, batchSize)
            for (item in page) {
                count = Math.addExact(count, 1L)
                when (item.status) { "CORRECTED" -> corrected++; "INTERRUPTED" -> interrupted++ }
                if (item.endTime == null) open = Math.addExact(open, 1L) else {
                    completed = Math.addExact(completed, 1L)
                    hours = hours.add(durationHours(item.startTime, item.endTime))
                }
            }
            if (page.isEmpty() || page.size < batchSize) break
            cursor = page.last().id
        }
        check(false)
        val rows = dao.reportAttendanceDisplay(startInclusive, endExclusive, filter.employeeId, status, onlyOpen, filter.rowLimit).map { item ->
            val hourValue = item.endTime?.let { durationHours(item.startTime, it) }
            ReportRow(listOf(ReportCell("${item.firstName} ${item.lastName}"), ReportCell(item.startTime), ReportCell(item.endTime ?: ""),
                ReportCell(item.status), ReportCell(hourValue?.toPlainString() ?: "")))
        }
        return TeamSummary(rows, completed, open, listOf(
            ReportSeries("sessions", listOf(BigDecimal.valueOf(count))),
            ReportSeries("completed", listOf(BigDecimal.valueOf(completed))),
            ReportSeries("open", listOf(BigDecimal.valueOf(open))),
            ReportSeries("corrected", listOf(BigDecimal.valueOf(corrected))),
            ReportSeries("interrupted", listOf(BigDecimal.valueOf(interrupted))),
            ReportSeries("completedHours", listOf(hours)),
        ))
    }

    private fun durationHours(start: String, end: String): BigDecimal {
        val duration = Duration.between(Instant.parse(start), Instant.parse(end))
        require(!duration.isNegative) { "Invalid attendance interval in persisted data" }
        val seconds = BigDecimal.valueOf(duration.seconds).add(BigDecimal.valueOf(duration.nano.toLong()).movePointLeft(9))
        return try { seconds.divide(BigDecimal.valueOf(3600)) }
        catch (_: ArithmeticException) { seconds.divide(BigDecimal.valueOf(3600), MathContext.DECIMAL128) }
    }

    /** Calendar-day bounds map through the requested zone, so DST days are 23/25-hour intervals when appropriate. */
    private fun attendanceBounds(filter: ReportFilter): Pair<String, String> {
        val zone = ZoneId.of(filter.timeZoneId)
        return timestampBounds(filter, zone)
    }

    private fun timestampBounds(filter: ReportFilter, zone: ZoneId = ZoneId.of(filter.timeZoneId)): Pair<String, String> {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
        val start = filter.fromInclusive.atStartOfDay(zone).toInstant()
        val end = filter.toExclusive.atStartOfDay(zone).toInstant()
        return formatter.format(start) to formatter.format(end)
    }

    private fun invoiceLocalDate(value: String, zone: ZoneId): LocalDate = try {
        Instant.parse(value).atZone(zone).toLocalDate()
    } catch (_: Exception) {
        LocalDate.parse(value.take(10))
    }

    private suspend fun currentCurrency(): String {
        check(false)
        val value = dao.settingValue("currency") ?: "EUR"
        if (value !in setOf("EUR", "XOF", "XAF", "CAD", "GBP", "CHF", "NGN", "GHS")) throw WorkflowFailure(WorkflowError.UNAVAILABLE)
        return value
    }

    private fun metricRow(key: String, amount: BigDecimal) = ReportRow(listOf(ReportCell(key), ReportCell(amount.toPlainString(), amount)))
    private fun textMetricRow(key: String, value: String) = ReportRow(listOf(ReportCell(key), ReportCell(value)))
    private fun listRow(key: String, amount: BigDecimal, currency: String) = ReportRow(listOf(ReportCell(key), ReportCell(amount.toPlainString(), amount), ReportCell(currency)))
    private fun unavailableListRow(key: String, value: String) = ReportRow(listOf(ReportCell(key), ReportCell(value), ReportCell("Unavailable")))
    private fun rankedRow(kind: String, name: String, quantity: Long, amount: BigDecimal) = ReportRow(listOf(ReportCell(kind), ReportCell(name), ReportCell(quantity.toString()), ReportCell(amount.toPlainString(), amount)))
}
