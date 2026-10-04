package com.vibe.store.api

data class SaleLine(val productId: Long, val quantity: Long)
class SaleCommand(val key: String, val cashId: Long, lines: List<SaleLine>,
    val discount: Double = 0.0, val received: Double? = null) {
    val lines: List<SaleLine> = java.util.Collections.unmodifiableList(ArrayList(lines))
}
data class CashView(val id: Long, val reference: String, val actorId: Long, val status: String,
    val opening: Double, val expected: Double, val openedAt: String, val closedAt: String? = null,
    val counted: Double? = null, val difference: Double? = null, val closedBy: Long? = null)
data class ReceiptLine(val productId: Long?, val name: String, val category: String,
    val quantity: Long, val unitPrice: Double, val total: Double, val unitCost: Double?)
data class Receipt(val id: String, val actorId: Long, val cashId: Long?, val date: String,
    val status: String, val subtotal: Double, val discount: Double, val total: Double,
    val received: Double, val change: Double, val paymentStatus: String,
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
    suspend fun openCash(opening: Double): CashView
    suspend fun closeCash(cashId: Long, counted: Double): CashView
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
