package com.vibe.store.application.sales

import com.vibe.store.api.*

data class StoredSale(val receipt: Receipt, val key: String?, val version: Int?, val canonical: String?)
data class SubmittedSale(val key: String, val actorId: Long, val cashId: Long, val version: Int,
    val canonical: String, val state: String, val createdAt: String)
interface CashOperations {
    suspend fun current(actorId: Long): CashView?
    suspend fun page(actorId: Long, offset: Int): List<CashView>
    suspend fun nextId(): Long
    suspend fun insert(value: CashView)
    suspend fun close(value: CashView)
}
interface SaleOperations {
    suspend fun byKey(key: String): StoredSale?
    suspend fun find(id: String): StoredSale?
    suspend fun page(filter: SaleFilter): List<StoredSale>
    suspend fun insert(value: StoredSale)
    suspend fun insertLine(invoiceId: String, value: ReceiptLine)
    suspend fun capture(value: Receipt)
    suspend fun reverse(value: Receipt)
    suspend fun cost(productId: Long): Double?
    suspend fun submitted(key: String): SubmittedSale?
    suspend fun pending(actorId: Long): List<SubmittedSale>
    suspend fun submit(value: SubmittedSale)
    suspend fun acknowledge(key: String)
    suspend fun abandon(key: String)
}
enum class SaleBoundary { JOURNALED, INVOICE, LINE, STOCK, MOVEMENT, PAYMENT, BEFORE_COMMIT, AFTER_COMMIT }
fun interface SaleProbe { suspend fun reached(boundary: SaleBoundary) }
