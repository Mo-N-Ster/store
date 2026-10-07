package com.vibe.store.infrastructure.persistence

import com.vibe.store.api.*
import com.vibe.store.application.inventory.*
import com.vibe.store.domain.*

internal class RoomInventoryRepository(private val dao: StoreDao, private val check: suspend (Boolean) -> Unit) : InventoryRecords {
    private fun InventoryEntity.view() = InventoryView(id, reference, InventoryStatus.valueOf(status), note.orEmpty(), createdBy, createdAt, validatedBy, validatedAt)
    override suspend fun inventory(id: Long): InventoryRecord? { check(false); return dao.findInventory(id)?.let { InventoryRecord(it.id, it.status) } }
    override suspend fun find(id: Long): InventoryView? { check(false); WorkflowRules.id(id); return dao.findInventory(id)?.view() }
    override suspend fun page(filter: InventoryFilter): List<InventoryView> {
        check(false); InventoryPolicy.filter(filter.status?.name, filter.from, filter.to, filter.page.offset, filter.page.limit)
        return dao.inventoryPage(filter.status?.name, filter.from, filter.to, filter.page.limit, filter.page.offset).map { it.view() }
    }
    override suspend fun lines(id: Long, page: WorkflowPageRequest): List<StoredInventoryLine> {
        check(false); WorkflowRules.id(id); WorkflowRules.page(page.offset, page.limit)
        return dao.inventoryLines(id, page.limit, page.offset).map { StoredInventoryLine(it.id, it.productId, it.expectedQuantity, it.countedQuantity) }
    }
    override suspend fun nextId(): Long { check(true); return WorkflowRules.id(dao.nextInventoryId()) }
    override suspend fun nextLineId(): Long { check(true); return WorkflowRules.id(dao.nextInventoryLineId()) }
    override suspend fun insert(value: InventoryView) {
        check(true); WorkflowRules.id(value.id); WorkflowRules.id(value.createdBy); WorkflowRules.text(value.note, 4000)
        WorkflowRules.check(value.status == InventoryStatus.DRAFT && value.validatedBy == null && value.validatedAt == null)
        dao.inventory(InventoryEntity(value.id, value.reference, value.status.name, value.createdBy, value.createdAt, value.note))
    }
    override suspend fun insertLine(id: Long, line: StoredInventoryLine) {
        check(true); WorkflowRules.id(id); WorkflowRules.id(line.id); WorkflowRules.id(line.productId)
        WorkflowRules.quantity(line.expectedQuantity); WorkflowRules.quantity(line.countedQuantity)
        InventoryPolicy.requireDraft(dao.findInventory(id)?.status)
        dao.inventoryLine(InventoryLineEntity(line.id, id, line.productId, line.expectedQuantity, line.countedQuantity))
    }
    override suspend fun recordDraftCount(id: Long, lineId: Long, productId: Long, count: Long, expectedPrevious: Long): Boolean {
        check(true); WorkflowRules.id(id); WorkflowRules.id(lineId); WorkflowRules.id(productId)
        WorkflowRules.quantity(count); WorkflowRules.quantity(expectedPrevious)
        return dao.editInventoryCount(id, lineId, productId, count, expectedPrevious) == 1
    }
    /** Storage CAS only, not a physical confirmation or application validation endpoint. */
    override suspend fun markValidated(value: InventoryView): Boolean {
        check(true); val old = dao.findInventory(value.id)?.view() ?: return false
        if (old.status != InventoryStatus.DRAFT) return false
        WorkflowRules.check(value.status == InventoryStatus.VALIDATED &&
            value.copy(status = old.status, validatedBy = old.validatedBy, validatedAt = old.validatedAt) == old)
        val validator = WorkflowRules.id(value.validatedBy ?: throw WorkflowViolation(WorkflowRuleError.INVALID_INPUT))
        WorkflowRules.check(!value.validatedAt.isNullOrBlank())
        return dao.inventoryValidated(value.id, validator, value.validatedAt!!) == 1
    }
}
