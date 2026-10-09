package com.vibe.store.api

import java.math.BigDecimal
import java.time.LocalDate
import java.util.Collections

enum class ReportTab { SUMMARY, SALES, PRODUCTS, STOCK, PURCHASES, TEAM, FINANCES }
enum class ReportGrain { DAY, WEEK, MONTH }

/** Date interval is [fromInclusive, toExclusive); rowLimit bounds presentation only. */
class ReportFilter(
    val tab: ReportTab,
    val fromInclusive: LocalDate,
    val toExclusive: LocalDate,
    val grain: ReportGrain = ReportGrain.DAY,
    val productId: Long? = null,
    val category: String? = null,
    val supplierId: Long? = null,
    val employeeId: Long? = null,
    val status: String? = null,
    val rowLimit: Int = 200,
    /** IANA time-zone used to map calendar dates to stored UTC instants. */
    val timeZoneId: String = java.time.ZoneId.systemDefault().id,
) {
    init {
        require(!toExclusive.isBefore(fromInclusive) && toExclusive.isAfter(fromInclusive))
        require(java.time.temporal.ChronoUnit.DAYS.between(fromInclusive, toExclusive) <= 3660)
        require(productId == null || productId > 0)
        require(supplierId == null || supplierId > 0)
        require(employeeId == null || employeeId > 0)
        require(category == null || category.length <= 200)
        require(status == null || status.length <= 40)
        require(rowLimit in 1..200)
    }
}

/** Dashboard calendar anchor and zone are explicit so every KPI shares one local-day snapshot. */
class DashboardRequest(
    val asOfDate: LocalDate,
    val timeZoneId: String,
    val rowLimit: Int = 200,
) {
    init { require(rowLimit in 1..200); java.time.ZoneId.of(timeZoneId) }

    companion object {
        /** Maps only the legacy SUMMARY filter shape that represents one complete local day. */
        fun fromLegacyFilter(filter: ReportFilter): DashboardRequest {
            require(filter.tab == ReportTab.SUMMARY)
            require(filter.grain == ReportGrain.DAY)
            require(java.time.temporal.ChronoUnit.DAYS.between(filter.fromInclusive, filter.toExclusive) == 1L)
            require(filter.productId == null && filter.category == null && filter.supplierId == null &&
                filter.employeeId == null && filter.status == null)
            return DashboardRequest(filter.fromInclusive, filter.timeZoneId, filter.rowLimit)
        }
    }
}

class DashboardContext(
    val asOfDate: LocalDate,
    val timeZoneId: String,
    val todayFromInclusive: LocalDate,
    val todayToExclusive: LocalDate,
    val monthFromInclusive: LocalDate,
    val monthToExclusive: LocalDate,
    val rolling30FromInclusive: LocalDate,
    val rolling30ToExclusive: LocalDate,
)

enum class MarginDisclosure { ESTIMATED_SNAPSHOT_COST, ESTIMATED_WITH_PRICE_FALLBACK }
/** `XXX` is used only with UNAVAILABLE; callers must not format it as a currency. */
enum class ReportCurrencyAvailability { AVAILABLE, UNAVAILABLE }
class ReportMetricSnapshot(
    val revenue: BigDecimal,
    val transactionCount: Long,
    val averageBasket: BigDecimal,
    val cancelledCount: Long,
    val estimatedGrossMargin: BigDecimal,
    val marginDisclosure: MarginDisclosure,
    val currency: String? = null,
)

class ReportColumn(val key: String, val label: String)
class ReportCell(val text: String, val monetaryValue: BigDecimal? = null)
class ReportRow(cells: List<ReportCell>) {
    val cells: List<ReportCell> = Collections.unmodifiableList(ArrayList(cells))
    init { require(cells.size <= 200) }
}
class ReportSeries(val key: String, points: List<BigDecimal>, labels: List<String> = emptyList()) {
    val points: List<BigDecimal> = Collections.unmodifiableList(ArrayList(points))
    val labels: List<String> = Collections.unmodifiableList(ArrayList(labels))
    init {
        require(key.isNotBlank() && key.length <= 100 && points.size <= 3660)
        require(labels.isEmpty() || labels.size == points.size)
        require(labels.all { it.isNotBlank() && it.length <= 40 })
    }
}

/** Immutable handoff snapshot for a future document renderer. It is not a PDF contract. */
class DocumentModel(
    val reportId: String,
    val tab: ReportTab,
    val filter: ReportFilter,
    val currency: String,
    columns: List<ReportColumn>,
    rows: List<ReportRow>,
    series: List<ReportSeries>,
    val metrics: ReportMetricSnapshot,
    val currencyAvailability: ReportCurrencyAvailability = ReportCurrencyAvailability.AVAILABLE,
    val dashboardContext: DashboardContext? = null,
) {
    val columns: List<ReportColumn> = Collections.unmodifiableList(ArrayList(columns))
    val rows: List<ReportRow> = Collections.unmodifiableList(ArrayList(rows))
    val series: List<ReportSeries> = Collections.unmodifiableList(ArrayList(series))
    init {
        require(reportId.isNotBlank() && reportId.length <= 100)
        require(currency.matches(Regex("[A-Z]{3}")))
        require((currencyAvailability == ReportCurrencyAvailability.UNAVAILABLE) == (currency == "XXX"))
        require(rows.size <= 200)
        require(columns.size in 1..200 && series.size <= 20)
    }
}

interface ReportingService {
    /** Implementations aggregate the complete authorized filtered result before bounding displayed rows. */
    suspend fun snapshot(filter: ReportFilter): DocumentModel

    /** Dashboard authorization is distinct from FINANCES reports; the filter must select SUMMARY. */
    suspend fun dashboard(filter: ReportFilter): DocumentModel

    /** Additive calendar-aware entry point; older clients retain the original method. */
    suspend fun dashboard(request: DashboardRequest): DocumentModel = dashboard(
        ReportFilter(ReportTab.SUMMARY, request.asOfDate, request.asOfDate.plusDays(1),
            rowLimit = request.rowLimit, timeZoneId = request.timeZoneId)
    )
}
