package com.vibe.store.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Collections

enum class ReportingSaleStatus { VALIDATED, CANCELLED }

class ReportingSaleLine(
    val quantity: Long,
    val lineTotal: BigDecimal,
    val unitPrice: BigDecimal,
    val snapshotUnitCost: BigDecimal?,
) {
    init {
        require(quantity > 0)
        require(lineTotal.signum() >= 0 && unitPrice.signum() >= 0)
        require(snapshotUnitCost == null || snapshotUnitCost.signum() >= 0)
    }
}

class ReportingSale(
    val id: String,
    val date: LocalDate,
    val status: ReportingSaleStatus,
    val subtotal: BigDecimal,
    val total: BigDecimal,
    lines: List<ReportingSaleLine>,
) {
    val lines: List<ReportingSaleLine> = Collections.unmodifiableList(ArrayList(lines))
    init {
        require(id.isNotBlank())
        require(subtotal.signum() >= 0 && total.signum() >= 0 && total <= subtotal)
        require(lines.isNotEmpty())
        require(lines.fold(BigDecimal.ZERO) { acc, line -> acc + line.lineTotal }.compareTo(subtotal) == 0)
        require(subtotal.signum() != 0 || total.signum() == 0)
    }
}

enum class EstimatedMarginDisclosure { SNAPSHOT_COSTS, PRICE_FALLBACK_USED }
class EstimatedGrossMargin(
    val amount: BigDecimal,
    val disclosure: EstimatedMarginDisclosure,
    val fallbackLineCount: Long,
)
class SalesReportCalculation(
    val validatedRevenue: BigDecimal,
    val transactionCount: Long,
    val averageBasket: BigDecimal,
    val cancelledCount: Long,
    val estimatedGrossMargin: EstimatedGrossMargin,
) {
    init { require(validatedRevenue.signum() >= 0 && transactionCount >= 0 && cancelledCount >= 0) }
}

data class AllocatedLineRevenue(val saleId: String, val lineIndex: Int, val amount: BigDecimal)

/** Pure report math. Call with the complete filtered set, never a display page. */
object ReportingCalculations {
    /** Bounded 34-significant-digit precision for non-terminating report quotients. */
    private val REPORT_PRECISION = MathContext.DECIMAL128

    /** Inclusive start, exclusive end; this uses calendar dates and has no implicit timezone. */
    fun containsDate(date: LocalDate, fromInclusive: LocalDate, toExclusive: LocalDate): Boolean =
        !date.isBefore(fromInclusive) && date.isBefore(toExclusive)

    fun allocateDiscount(sale: ReportingSale): List<AllocatedLineRevenue> {
        if (sale.status != ReportingSaleStatus.VALIDATED) return emptyList()
        if (sale.subtotal.signum() == 0) {
            return sale.lines.mapIndexed { i, _ -> AllocatedLineRevenue(sale.id, i, BigDecimal.ZERO) }
        }
        var allocated = BigDecimal.ZERO
        return sale.lines.mapIndexed { index, line ->
            // Keep terminating divisions exact. For repeating ratios, round down at 34 significant
            // digits so this line never exceeds its exact proportional share. The final source line
            // receives the residual, preserving invoice-total conservation without a currency scale.
            val amount = if (index == sale.lines.lastIndex) sale.total.subtract(allocated)
            else proportionalAllocation(line.lineTotal, sale.total, sale.subtotal)
            require(amount.signum() >= 0 && amount <= line.lineTotal) {
                "Discount allocation exceeds line bounds"
            }
            allocated = allocated.add(amount)
            AllocatedLineRevenue(sale.id, index, ExactMoney.normalize(amount))
        }.also { result ->
            require(result.fold(BigDecimal.ZERO) { sum, item -> sum.add(item.amount) }.compareTo(sale.total) == 0)
        }
    }

    private fun proportionalAllocation(
        lineTotal: BigDecimal,
        invoiceTotal: BigDecimal,
        invoiceSubtotal: BigDecimal,
    ): BigDecimal {
        val numerator = lineTotal.multiply(invoiceTotal)
        return try {
            numerator.divide(invoiceSubtotal)
        } catch (_: ArithmeticException) {
            // Allocation is non-negative; DOWN guarantees no per-line over-allocation.
            numerator.divide(invoiceSubtotal, MathContext(REPORT_PRECISION.precision, RoundingMode.DOWN))
        }
    }

    fun calculate(sales: List<ReportingSale>): SalesReportCalculation {
        require(sales.map { it.id }.toSet().size == sales.size) { "Duplicate sale id" }
        val validated = sales.filter { it.status == ReportingSaleStatus.VALIDATED }
        var revenue = BigDecimal.ZERO
        var margin = BigDecimal.ZERO
        var fallbackCount = 0L
        validated.forEach { sale ->
            val allocations = allocateDiscount(sale)
            revenue = revenue.add(allocations.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.amount) })
            allocations.forEach { allocation ->
                val line = sale.lines[allocation.lineIndex]
                val cost = line.snapshotUnitCost ?: line.unitPrice.also { fallbackCount++ }
                margin = margin.add(allocation.amount.subtract(cost.multiply(BigDecimal.valueOf(line.quantity))))
            }
        }
        val count = validated.size.toLong()
        val average = if (count == 0L) BigDecimal.ZERO else try {
            revenue.divide(BigDecimal.valueOf(count))
        } catch (_: ArithmeticException) {
            revenue.divide(BigDecimal.valueOf(count), REPORT_PRECISION)
        }
        return SalesReportCalculation(
            ExactMoney.normalize(revenue), count, ExactMoney.normalize(average),
            sales.count { it.status == ReportingSaleStatus.CANCELLED }.toLong(),
            EstimatedGrossMargin(
                ExactMoney.normalize(margin),
                if (fallbackCount == 0L) EstimatedMarginDisclosure.SNAPSHOT_COSTS
                else EstimatedMarginDisclosure.PRICE_FALLBACK_USED,
                fallbackCount,
            ),
        )
    }
}
