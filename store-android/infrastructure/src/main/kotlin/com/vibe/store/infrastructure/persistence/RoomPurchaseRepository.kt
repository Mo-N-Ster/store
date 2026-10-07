package com.vibe.store.infrastructure.persistence

import com.vibe.store.api.*
import com.vibe.store.application.purchases.*
import com.vibe.store.domain.*

internal class RoomPurchaseRepository(private val dao: StoreDao, private val check: suspend (Boolean) -> Unit) : PurchaseRecords {
    private fun PurchaseEntity.record() = StoredPurchase(PurchaseDetail(PurchaseSummary(id, reference, supplierId,
        PurchaseStatus.valueOf(status), totalAmount, createdBy, createdAt), supplierInvoice.orEmpty(), note.orEmpty(),
        validatedBy, validatedAt, cancelledBy, cancelledAt, cancellationReason), idempotencyKey)
    private fun PurchaseLineEntity.view() = PurchaseLineView(id, productId, quantity, unitCost, totalLine)
    override suspend fun purchase(id: Long): PurchaseRecord? { check(false); return dao.findPurchase(id)?.let { PurchaseRecord(it.id, it.supplierId, it.status, MoneyValue(it.totalAmount)) } }
    override suspend fun find(id: Long): StoredPurchase? { check(false); WorkflowRules.id(id); return dao.findPurchase(id)?.record() }
    override suspend fun keyExists(key: String): Boolean { check(false); PurchasePolicy.key(key); return dao.purchaseKeyExists(key) }
    override suspend fun page(filter: PurchaseFilter): List<StoredPurchase> {
        check(false); PurchasePolicy.filter(filter.status?.name, filter.supplierId, filter.productId, filter.from, filter.to, filter.page.offset, filter.page.limit)
        return dao.purchasePage(filter.status?.name, filter.supplierId, filter.productId, filter.from, filter.to, filter.page.limit, filter.page.offset).map { it.record() }
    }
    override suspend fun lines(id: Long, page: WorkflowPageRequest): List<PurchaseLineView> {
        check(false); WorkflowRules.id(id); WorkflowRules.page(page.offset, page.limit)
        return dao.purchaseLines(id, page.limit, page.offset).map { it.view() }
    }
    override suspend fun nextId(): Long { check(true); return WorkflowRules.id(dao.nextPurchaseId()) }
    override suspend fun nextLineId(): Long { check(true); return WorkflowRules.id(dao.nextPurchaseLineId()) }
    override suspend fun insert(value: StoredPurchase) {
        check(true); val d = value.detail; val s = d.summary
        WorkflowRules.id(s.id); WorkflowRules.id(s.createdBy); PurchasePolicy.header(s.supplierId, d.supplierInvoice, d.note)
        PurchasePolicy.key(value.creationKey)
        value.creationKey?.let { PurchasePolicy.creationKeyAvailable(dao.purchaseKeyExists(it)) }
        WorkflowRules.check(s.status == PurchaseStatus.DRAFT && s.total == 0.0 && d.validatedBy == null && d.validatedAt == null && d.cancelledBy == null && d.cancelledAt == null && d.cancellationReason == null)
        dao.purchase(PurchaseEntity(s.id, s.reference, s.supplierId, s.status.name, s.total, s.createdBy, s.createdAt, value.creationKey, d.supplierInvoice, d.note))
    }
    override suspend fun replaceDraftLine(id: Long, line: PurchaseLineView, expected: PurchaseLineView?): Boolean {
        check(true); WorkflowRules.id(id); WorkflowRules.id(line.id); WorkflowRules.id(line.productId)
        WorkflowRules.check(line.total == PurchasePolicy.lineTotal(line.quantity, line.unitCost))
        if (dao.findPurchase(id)?.status != "DRAFT") return false
        val old = dao.purchaseItem(id, line.productId)?.view()
        if (old != expected || (old != null && old.id != line.id)) return false
        if (old == null) dao.purchaseLine(PurchaseLineEntity(line.id, id, line.productId, line.quantity, line.unitCost, line.total))
        else return dao.editPurchaseItem(id, line.id, line.productId, line.quantity, line.unitCost, line.total, old.quantity, old.unitCost, old.total) == 1
        return true
    }
    override suspend fun updateDraftTotal(id: Long, total: Double): Boolean {
        check(true); WorkflowRules.id(id); PurchasePolicy.cost(total)
        return dao.draftPurchaseTotal(id, total) == 1
    }
    /** Storage transition only. Future authority owns stock/audit effects in the same UoW. */
    override suspend fun transition(value: StoredPurchase, expectedStatus: PurchaseStatus): Boolean {
        check(true); val d = value.detail; val s = d.summary
        val old = dao.findPurchase(s.id)?.record() ?: return false
        if (old.detail.summary.status != expectedStatus) return false
        WorkflowRules.check(s.copy(status = expectedStatus) == old.detail.summary && value.creationKey == old.creationKey &&
            d.note == old.detail.note && d.supplierInvoice == old.detail.supplierInvoice)
        WorkflowRules.check((expectedStatus == PurchaseStatus.DRAFT && s.status in setOf(PurchaseStatus.VALIDATED, PurchaseStatus.CANCELLED)) ||
            (expectedStatus == PurchaseStatus.VALIDATED && s.status == PurchaseStatus.CANCELLED))
        if (s.status == PurchaseStatus.VALIDATED) {
            WorkflowRules.id(d.validatedBy ?: throw WorkflowViolation(WorkflowRuleError.INVALID_INPUT))
            WorkflowRules.check(!d.validatedAt.isNullOrBlank() && d.cancelledBy == null && d.cancelledAt == null && d.cancellationReason == null)
        } else {
            WorkflowRules.id(d.cancelledBy ?: throw WorkflowViolation(WorkflowRuleError.INVALID_INPUT))
            PurchasePolicy.cancellation(expectedStatus.name, d.cancellationReason.orEmpty())
            WorkflowRules.check(!d.cancelledAt.isNullOrBlank() && d.validatedBy == old.detail.validatedBy && d.validatedAt == old.detail.validatedAt)
        }
        return dao.purchaseTransition(s.id, expectedStatus.name, s.status.name, d.validatedBy, d.validatedAt, d.cancelledBy, d.cancelledAt, d.cancellationReason) == 1
    }
}
