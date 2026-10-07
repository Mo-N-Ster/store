package com.vibe.store.application.purchases

import com.vibe.store.api.*
import com.vibe.store.application.persistence.PurchaseRepository

/** Trusted records. Creation key is internal, not included in public projections. */
data class StoredPurchase(val detail: PurchaseDetail, val creationKey: String?)
interface SupplierRecords {
    suspend fun find(id: Long): SupplierView?
    suspend fun page(filter: SupplierFilter): List<SupplierView>
    suspend fun nameExists(name: String, exceptId: Long?): Boolean
    suspend fun nextId(): Long
    suspend fun insert(value: SupplierView)
    suspend fun update(value: SupplierView, expectedUpdatedAt: String): Boolean
}
/** Extension of the accepted read port; P2 will wire it to the SAME RoomRepositories/lease. */
interface PurchaseRecords : PurchaseRepository {
    suspend fun find(id: Long): StoredPurchase?
    suspend fun keyExists(key: String): Boolean
    suspend fun page(filter: PurchaseFilter): List<StoredPurchase>
    suspend fun lines(id: Long, page: WorkflowPageRequest): List<PurchaseLineView>
    suspend fun nextId(): Long
    suspend fun nextLineId(): Long
    suspend fun insert(value: StoredPurchase)
    suspend fun replaceDraftLine(id: Long, line: PurchaseLineView, expected: PurchaseLineView?): Boolean
    suspend fun updateDraftTotal(id: Long, total: Double): Boolean
    suspend fun transition(value: StoredPurchase, expectedStatus: PurchaseStatus): Boolean
}
// All writes require the existing authorized UnitOfWork. Stock/movements/audit use
// CatalogRecords/SecurityRepository in that same transaction, never another owner.
