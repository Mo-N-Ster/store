package com.vibe.store.testing

import com.vibe.store.domain.*
import com.vibe.store.api.*
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ReportingCalculationsTest {
    private fun d(value: String) = BigDecimal(value)
    private fun sale(
        id: String = "sale-1", status: ReportingSaleStatus = ReportingSaleStatus.VALIDATED,
        subtotal: String = "12.3456789", total: String = "12.3456789",
        lines: List<ReportingSaleLine> = listOf(ReportingSaleLine(1, d(subtotal), d(subtotal), d("2"))),
        date: LocalDate = LocalDate.parse("2026-10-08"),
    ) = ReportingSale(id, date, status, d(subtotal), d(total), lines)

    @Test fun exactRevenueAndZeroSales() {
        val result = ReportingCalculations.calculate(listOf(sale()))
        assertEquals(0, result.validatedRevenue.compareTo(d("12.3456789")))
        assertEquals(1L, result.transactionCount)
        val zero = ReportingCalculations.calculate(emptyList())
        assertEquals(0, zero.averageBasket.compareTo(BigDecimal.ZERO))
        assertEquals(0L, zero.transactionCount)
    }

    @Test fun revenueAggregatesExactDecimalsAndAverageUsesInternalPrecision() {
        val first = sale("a", subtotal = "0.1", total = "0.1",
            lines = listOf(ReportingSaleLine(1, d("0.1"), d("0.1"), d("0"))))
        val second = sale("b", subtotal = "0.2", total = "0.2",
            lines = listOf(ReportingSaleLine(1, d("0.2"), d("0.2"), d("0"))))
        val result = ReportingCalculations.calculate(listOf(first, second))
        assertEquals(0, result.validatedRevenue.compareTo(d("0.3")))
        assertEquals(0, result.averageBasket.compareTo(d("0.15")))
        val third = sale("c", subtotal = "10", total = "10",
            lines = listOf(ReportingSaleLine(1, d("10"), d("10"), d("0"))))
        val repeatingAverage = ReportingCalculations.calculate(listOf(third, first, second)).averageBasket
        assertEquals(34, repeatingAverage.precision())
        assertTrue(repeatingAverage.toPlainString().startsWith("3.433333333333333333333333333333333"))
    }

    @Test fun cancelledSalesAreSeparateAndNeverAffectRevenueOrMargin() {
        val result = ReportingCalculations.calculate(listOf(sale(), sale("cancelled", ReportingSaleStatus.CANCELLED)))
        assertEquals(1L, result.transactionCount)
        assertEquals(1L, result.cancelledCount)
        assertEquals(0, result.validatedRevenue.compareTo(d("12.3456789")))
    }

    @Test fun lineDiscountAllocationPreservesTerminatingPrecisionAndBalancesExactly() {
        val invoice = sale(
            subtotal = "10", total = "9.99",
            lines = listOf(
                ReportingSaleLine(1, d("1"), d("1"), d("0.5")),
                ReportingSaleLine(1, d("9"), d("9"), d("4")),
            ),
        )
        val allocations = ReportingCalculations.allocateDiscount(invoice)
        assertEquals(0, allocations[0].amount.compareTo(d("0.999")))
        assertEquals(0, allocations[1].amount.compareTo(d("8.991")))
        assertEquals(0, allocations.fold(BigDecimal.ZERO) { acc, item -> acc + item.amount }.compareTo(d("9.99")))
    }

    @Test fun threeLineRepeatingAllocationIsBoundedConservedAndDeterministic() {
        val invoice = sale(
            subtotal = "3", total = "1",
            lines = listOf(
                ReportingSaleLine(1, d("1"), d("1"), d("0")),
                ReportingSaleLine(1, d("1"), d("1"), d("0")),
                ReportingSaleLine(1, d("1"), d("1"), d("0")),
            ),
        )
        val first = ReportingCalculations.allocateDiscount(invoice)
        val second = ReportingCalculations.allocateDiscount(invoice)
        assertEquals(first, second)
        assertEquals(0, first.fold(BigDecimal.ZERO) { acc, item -> acc + item.amount }.compareTo(d("1")))
        first.forEach { assertTrue(it.amount.signum() >= 0 && it.amount <= d("1")) }
        assertEquals(34, first[0].amount.precision())
    }

    @Test fun highPrecisionInputsAndTinyTotalsAreNotRoundedToCurrencyCents() {
        val lines = listOf(
            ReportingSaleLine(1, d("0.00000001"), d("0.00000001"), d("0")),
            ReportingSaleLine(1, d("0.00000001"), d("0.00000001"), d("0")),
            ReportingSaleLine(1, d("0.00000001"), d("0.00000001"), d("0")),
        )
        val allocated = ReportingCalculations.allocateDiscount(sale(subtotal = "0.00000003", total = "0.00000001", lines = lines))
        assertEquals(0, allocated.fold(BigDecimal.ZERO) { acc, item -> acc + item.amount }.compareTo(d("0.00000001")))
        allocated.forEach { assertTrue(it.amount.signum() >= 0 && it.amount <= d("0.00000001")) }
        assertEquals(0, ReportingCalculations.calculate(listOf(sale())).validatedRevenue.compareTo(d("12.3456789")))
    }

    @Test fun zeroValueLinesAndOneHundredPercentDiscountRespectBounds() {
        val invoice = sale(
            subtotal = "2", total = "0",
            lines = listOf(
                ReportingSaleLine(1, d("0"), d("1"), d("0")),
                ReportingSaleLine(1, d("1"), d("1"), d("0")),
                ReportingSaleLine(1, d("1"), d("1"), d("0")),
            ),
        )
        val allocations = ReportingCalculations.allocateDiscount(invoice)
        assertEquals(3, allocations.size)
        allocations.forEachIndexed { index, item ->
            assertEquals(0, item.amount.compareTo(BigDecimal.ZERO))
            assertTrue(item.amount <= invoice.lines[index].lineTotal)
        }
        assertEquals(0, allocations.fold(BigDecimal.ZERO) { acc, item -> acc + item.amount }.compareTo(invoice.total))
    }

    @Test fun nonTerminatingAverageUsesDocumentedDecimal128Precision() {
        val sales = listOf("0.1", "0.2", "0.7").mapIndexed { index, total ->
            sale("avg-$index", subtotal = total, total = total,
                lines = listOf(ReportingSaleLine(1, d(total), d(total), d("0"))))
        }
        val average = ReportingCalculations.calculate(sales).averageBasket
        assertEquals(34, average.precision())
        assertEquals(34, average.scale())
        assertTrue(average.toPlainString().startsWith("0.3333333333333333333333333333333333"))
    }

    @Test fun marginIsAlwaysEstimatedAndMissingCostIsDisclosed() {
        val missing = sale(lines = listOf(ReportingSaleLine(2, d("12.3456789"), d("7"), null)))
        val margin = ReportingCalculations.calculate(listOf(missing)).estimatedGrossMargin
        assertEquals(1L, margin.fallbackLineCount)
        assertEquals(EstimatedMarginDisclosure.PRICE_FALLBACK_USED, margin.disclosure)
        assertEquals(0, margin.amount.compareTo(d("-1.6543211")))
    }

    @Test fun dateRangeIsStartInclusiveAndEndExclusive() {
        val start = LocalDate.parse("2026-10-01")
        val end = LocalDate.parse("2026-11-01")
        assertTrue(ReportingCalculations.containsDate(start, start, end))
        assertTrue(ReportingCalculations.containsDate(end.minusDays(1), start, end))
        assertFalse(ReportingCalculations.containsDate(end, start, end))
    }

    @Test fun reportFiltersValidateBoundedDatesAndRows() {
        val from = LocalDate.parse("2026-10-01")
        val to = LocalDate.parse("2026-11-01")
        assertEquals(200, ReportFilter(ReportTab.SALES, from, to).rowLimit)
        assertThrows(IllegalArgumentException::class.java) { ReportFilter(ReportTab.SALES, to, from) }
        assertThrows(IllegalArgumentException::class.java) { ReportFilter(ReportTab.SALES, from, to, rowLimit = 201) }
        assertThrows(IllegalArgumentException::class.java) {
            ReportFilter(ReportTab.SALES, from, from.plusDays(3661))
        }
    }

    @Test fun immutableSnapshotsDefensivelyCopyNestedLists() {
        val cells = mutableListOf(ReportCell("1"))
        val rows = mutableListOf(ReportRow(cells))
        val seriesPoints = mutableListOf(d("1"))
        val series = mutableListOf(ReportSeries("revenue", seriesPoints))
        val columns = mutableListOf(ReportColumn("revenue", "Revenue"))
        val snapshot = DocumentModel(
            "r1", ReportTab.SALES,
            ReportFilter(ReportTab.SALES, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-11-01")),
            "EUR", columns, rows, series,
            ReportMetricSnapshot(d("1"), 1, d("1"), 0, d("0"), MarginDisclosure.ESTIMATED_SNAPSHOT_COST),
        )
        cells.clear(); rows.clear(); seriesPoints.clear(); series.clear(); columns.clear()
        assertEquals(1, snapshot.rows.size)
        assertEquals(1, snapshot.rows.single().cells.size)
        assertEquals(1, snapshot.series.single().points.size)
        assertEquals(1, snapshot.columns.size)
    }
}
