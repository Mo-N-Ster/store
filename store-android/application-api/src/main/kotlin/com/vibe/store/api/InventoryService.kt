package com.vibe.store.api

data class InventoryView(val id: Long, val reference: String, val status: InventoryStatus,
    val note: String, val createdBy: Long, val createdAt: String, val validatedBy: Long?, val validatedAt: String?)
data class InventoryLineView(val id: Long, val productId: Long, val expectedQuantity: Long,
    val countedQuantity: Long, val currentStock: Long)
data class InventoryFilter(val status: InventoryStatus? = null, val from: String = "", val to: String = "",
    val page: WorkflowPageRequest = WorkflowPageRequest())
data class StartInventory(val note: String = "")
data class RecordInventoryCount(val inventoryId: Long, val lineId: Long, val productId: Long,
    val countedQuantity: Long, val expectedPreviousCount: Long)
data class InventoryCountAttestation(val lineId: Long, val productId: Long, val countedQuantity: Long)
/** Opaque review handle, NOT confirmation or authority; no automatic attestation on reading it. */
data class InventoryReview(val inventoryId: Long, val token: String, val lineCount: Int)
class ValidateInventory(val inventoryId: Long, val reviewToken: String?,
    val physicallyConfirmed: Boolean, counts: List<InventoryCountAttestation>) {
    val counts: List<InventoryCountAttestation> = java.util.Collections.unmodifiableList(ArrayList(counts))
}
interface InventoryService {
    /** D1: current Owner/Manager AND effective STOCKS:READ; Employee explicitly denied. */
    suspend fun list(filter: InventoryFilter = InventoryFilter()): WorkflowPage<InventoryView>
    suspend fun detail(id: Long): InventoryView
    suspend fun lines(id: Long, page: WorkflowPageRequest = WorkflowPageRequest()): WorkflowPage<InventoryLineView>
    suspend fun review(id: Long): InventoryReview
    /** STOCKS:CREATE. Prefilled counts are not confirmed. */
    suspend fun start(command: StartInventory): InventoryView
    /** STOCKS:UPDATE; DRAFT only; invalidate existing reviews even on ABA count edits. */
    suspend fun recordCount(command: RecordInventoryCount): InventoryLineView
    /** STOCKS:VALIDATE + D2/D4. Read durable lines and current stock inside existing UoW.
     * Complete explicit attestation required; absent/stale/duplicate/missing counts reject.
     * Review belongs to current actor/session/generation and expires on process death. */
    suspend fun validate(command: ValidateInventory): InventoryView
}
