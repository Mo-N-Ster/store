package com.vibe.store.domain

/** Binary64 is deliberate: no implicit cents/banker's-rounding conversion. */
@JvmInline value class MoneyValue(val value: Double) { init { require(value.isFinite()) } }
@JvmInline value class Quantity(val value: Long) { init { require(value in 0..9_007_199_254_740_991L) } }
@JvmInline value class GenerationId(val value: String) { init { require(value.matches(Regex("[a-zA-Z0-9-]{1,64}"))) } }

fun interface MoneyParity { fun roundTwo(value: Double): Double }
// Math.round(x * 100) / 100 with ties toward +infinity, unlike Kotlin round().
object SourceMoneyParity : MoneyParity {
    override fun roundTwo(value: Double): Double {
        require(value.isFinite())
        val scaled = value * 100
        if (!scaled.isFinite()) return scaled
        val lower = kotlin.math.floor(scaled)
        val rounded = if (scaled - lower < 0.5) lower else lower + 1
        return if (rounded == 0.0 && value < 0) -0.0 else rounded / 100
    }
}
/** Caller chooses UTC or local calendar per source report, never an implicit global correction. */
interface CalendarParity { fun day(epochMillis: Long, utc: Boolean): String }

data class AccountRecord(val id: Long, val username: String, val active: Boolean)
data class CashRecord(val id: Long, val userId: Long, val status: String, val openingAmount: MoneyValue)
data class ProductRecord(val id: Long, val name: String, val price: MoneyValue, val stock: Quantity)
data class InvoiceRecord(val id: String, val userId: Long, val cashId: Long?, val total: MoneyValue, val status: String)
data class PurchaseRecord(val id: Long, val supplierId: Long?, val status: String, val total: MoneyValue)
data class InventoryRecord(val id: Long, val status: String)
data class AttendanceRecord(val id: Long, val userId: Long, val startTime: String, val endTime: String?, val status: String)
data class MessageRecord(val id: Long, val subject: String, val content: String)
data class AuditRecord(val id: Long, val action: String, val responsibleName: String?, val cashAmount: MoneyValue?)
