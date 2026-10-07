package com.vibe.store.infrastructure.persistence

import com.vibe.store.api.*
import com.vibe.store.application.purchases.SupplierRecords
import com.vibe.store.domain.*

internal class RoomSupplierRepository(private val dao: StoreDao, private val check: suspend (Boolean) -> Unit) : SupplierRecords {
    private fun SupplierEntity.view() = SupplierView(id, name, phone.orEmpty(), email.orEmpty(), address.orEmpty(), active, createdAt, updatedAt)
    override suspend fun find(id: Long): SupplierView? { check(false); WorkflowRules.id(id); return dao.findSupplier(id)?.view() }
    override suspend fun page(filter: SupplierFilter): List<SupplierView> {
        check(false); WorkflowRules.text(filter.search, 200); WorkflowRules.page(filter.page.offset, filter.page.limit)
        return dao.supplierPage(filter.search, filter.active, filter.page.limit, filter.page.offset).map { it.view() }
    }
    override suspend fun nameExists(name: String, exceptId: Long?): Boolean { check(false); return dao.supplierNameExists(name, exceptId) }
    override suspend fun nextId(): Long { check(true); return WorkflowRules.id(dao.nextSupplierId()) }
    override suspend fun insert(value: SupplierView) {
        check(true); WorkflowRules.id(value.id); PurchasePolicy.supplier(value.name, value.phone, value.email, value.address)
        dao.supplier(SupplierEntity(value.id, value.name, value.active, value.createdAt, value.updatedAt, value.phone, value.email, value.address))
    }
    override suspend fun update(value: SupplierView, expectedUpdatedAt: String): Boolean {
        check(true); WorkflowRules.id(value.id); PurchasePolicy.supplier(value.name, value.phone, value.email, value.address)
        return dao.editSupplier(value.id, value.name, value.phone, value.email, value.address, value.active, value.updatedAt, expectedUpdatedAt) == 1
    }
}
