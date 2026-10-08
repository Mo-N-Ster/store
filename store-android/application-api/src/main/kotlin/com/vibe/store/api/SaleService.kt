package com.vibe.store.api

import java.math.BigDecimal

data class SaleLine(val productId: Long, val quantity: Long)
class SaleCommand(val key: String, val cashId: Long, lines: List<SaleLine>,
    val discount: BigDecimal = BigDecimal.ZERO, val received: BigDecimal? = null) {
    val lines: List<SaleLine> = java.util.Collections.unmodifiableList(ArrayList(lines))
}
data class CashView(val id: Long, val reference: String, val actorId: Long, val status: String,
    val opening: BigDecimal, val expected: BigDecimal, val openedAt: String, val closedAt: String? = null,
    val counted: BigDecimal? = null, val difference: BigDecimal? = null, val closedBy: Long? = null)
data class ReceiptLine(val productId: Long?, val name: String, val category: String,
    val quantity: Long, val unitPrice: BigDecimal, val total: BigDecimal, val unitCost: BigDecimal?)
data class Receipt(val id: String, val actorId: Long, val cashId: Long?, val date: String,
    val status: String, val subtotal: BigDecimal, val discount: BigDecimal, val total: BigDecimal,
    val received: BigDecimal, val change: BigDecimal, val paymentStatus: String,
    val storeName: String, val address: String, val phone: String, val email: String,
    val currency: String, val lines: List<ReceiptLine>, val cancelledBy: Long? = null,
    val cancelledAt: String? = null, val cancellationReason: String? = null)
data class SaleFilter(val search: String = "", val from: String = "", val to: String = "",
    val productId: Long? = null, val category: String = "", val offset: Int = 0, val limit: Int = 40)
data class SalePage(val items: List<Receipt>, val hasMore: Boolean)
data class PendingSale(val key: String, val cashId: Long, val createdAt: String)
enum class SaleError { INVALID_INPUT, CASH_REQUIRED, CONFLICT, NOT_FOUND, INSUFFICIENT_STOCK, UNAVAILABLE }
class SaleFailure(val code: SaleError) : Exception(code.name)
interface SaleService {
    suspend fun currentCash(): CashView?
    suspend fun openCash(opening: BigDecimal): CashView
    suspend fun closeCash(cashId: Long, counted: BigDecimal): CashView
    suspend fun cashHistory(offset: Int = 0): List<CashView>
    suspend fun sell(command: SaleCommand): Receipt
    suspend fun pending(): List<PendingSale>
    /** Explicit same-command retry only; never invoked automatically on startup. */
    suspend fun resolve(key: String): Receipt
    suspend fun acknowledge(key: String)
    /** Only succeeds when the backend proves that no invoice exists for the key. */
    suspend fun abandon(key: String)
    suspend fun history(filter: SaleFilter = SaleFilter()): SalePage
    suspend fun receipt(id: String): Receipt
    suspend fun cancel(id: String, reason: String): Receipt
}
