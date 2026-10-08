package com.vibe.store.infrastructure.persistence

import java.math.BigDecimal
import com.vibe.store.api.*
import com.vibe.store.domain.ExactMoney
import com.vibe.store.application.sales.*

internal class RoomCashOperations(private val dao: StoreDao, private val check: suspend (Boolean) -> Unit) : CashOperations {
    private suspend fun CashEntity.view() = CashView(id, reference, userId, status, openingAmount,
        if (status == "OPEN") ExactMoney.add(openingAmount, dao.capturedPayments(id)) else checkNotNull(expectedAmount),
        openedAt, closedAt, closingAmount, difference, closedBy)
    override suspend fun current(actorId: Long): CashView? { check(false); return dao.openCash(actorId)?.view() }
    override suspend fun page(actorId: Long, offset: Int): List<CashView> { check(false); return dao.cashPage(actorId, offset).map { it.view() } }
    override suspend fun nextId(): Long { check(true); return dao.nextCashId() }
    override suspend fun insert(value: CashView) {
        check(true); dao.cash(CashEntity(value.id, value.reference, value.actorId, "OPEN", value.opening, value.openedAt))
    }
    override suspend fun close(value: CashView) {
        check(true)
        val old = checkNotNull(dao.session(value.id)); require(old.status == "OPEN" && old.userId == value.actorId)
        dao.updateCash(old.copy(status = "CLOSED", closedAt = value.closedAt, closingAmount = value.counted,
            expectedAmount = value.expected, difference = value.difference, closedBy = value.closedBy))
    }
}
internal class RoomSaleOperations(private val dao: StoreDao, private val check: suspend (Boolean) -> Unit) : SaleOperations {
    private suspend fun InvoiceEntity.stored(): StoredSale {
        val payment = checkNotNull(dao.invoicePayment(id)) { "Invoice payment unavailable" }
        return StoredSale(Receipt(id, userId, cashId, invoiceDate, status, subtotal, discount, totalAmount,
            payment.received, payment.change, payment.status, storeName ?: "", storeAddress ?: "",
            storePhone ?: "", storeEmail ?: "", currency ?: "", dao.invoiceLines(id).map {
                ReceiptLine(it.productId, it.productName, it.category, it.quantity, it.unitPrice, it.totalLine, it.unitCost)
            }, cancelledBy, cancelledAt, cancellationReason), idempotencyKey, canonicalVersion, canonicalRequest)
    }
    override suspend fun byKey(key: String): StoredSale? { check(false); return dao.invoiceByKey(key)?.stored() }
    override suspend fun find(id: String): StoredSale? { check(false); return dao.findInvoice(id)?.stored() }
    override suspend fun page(filter: SaleFilter): List<StoredSale> {
        check(false); return dao.salePage(filter.search, filter.from, filter.to, filter.productId,
            filter.category, filter.limit, filter.offset).map { it.stored() }
    }
    override suspend fun insert(value: StoredSale) {
        check(true); val r = value.receipt
        dao.invoice(InvoiceEntity(r.id, r.actorId, r.cashId, r.date, r.subtotal, r.total, r.discount, r.status,
            value.key, value.version, value.canonical, storeName = r.storeName, storeAddress = r.address,
            storePhone = r.phone, storeEmail = r.email, currency = r.currency))
    }
    override suspend fun insertLine(invoiceId: String, value: ReceiptLine) {
        check(true); dao.invoiceLine(InvoiceLineEntity(dao.nextInvoiceLineId(), invoiceId, value.productId,
            value.name, value.category, value.quantity, value.unitPrice, value.total, value.unitCost))
    }
    override suspend fun capture(value: Receipt) {
        check(true); dao.payment(PaymentEntity(dao.nextPaymentId(), value.id, checkNotNull(value.cashId), "CASH",
            value.total, value.received, value.change, "CAPTURED", value.date))
    }
    override suspend fun reverse(value: Receipt) {
        check(true); val old = checkNotNull(dao.findInvoice(value.id)); require(old.status == "validated")
        dao.updateInvoice(old.copy(status = "cancelled", cancelledBy = value.cancelledBy,
            cancelledAt = value.cancelledAt, cancellationReason = value.cancellationReason))
        dao.refund(value.id)
    }
    override suspend fun cost(productId: Long): BigDecimal? { check(false); return dao.latestCost(productId) }
    private fun PendingCommandEntity.value() = SubmittedSale(commandKey, actorId, cashId, canonicalVersion, canonicalRequest, state, createdAt)
    override suspend fun submitted(key: String): SubmittedSale? { check(false); return dao.pendingByKey(key)?.value() }
    override suspend fun pending(actorId: Long): List<SubmittedSale> { check(false); return dao.pendingFor(actorId).map { it.value() } }
    override suspend fun submit(value: SubmittedSale) {
        check(true); dao.pending(PendingCommandEntity(value.key, value.key, value.actorId, value.cashId,
            value.version, value.canonical, value.state, value.createdAt))
    }
    override suspend fun acknowledge(key: String) { check(true); dao.acknowledgeCommand(key) }
    override suspend fun abandon(key: String) { check(true); dao.abandonCommand(key) }
}
