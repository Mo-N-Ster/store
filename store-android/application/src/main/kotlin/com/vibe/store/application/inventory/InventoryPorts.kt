package com.vibe.store.application.inventory

import com.vibe.store.api.*
import com.vibe.store.application.persistence.InventoryRepository
import com.vibe.store.domain.InventoryPolicy

/** No current stock persisted here: it is re-read from the accepted catalog at validation. */
data class StoredInventoryLine(val id: Long, val productId: Long, val expectedQuantity: Long, val countedQuantity: Long)
interface InventoryRecords : InventoryRepository {
    suspend fun find(id: Long): InventoryView?
    suspend fun page(filter: InventoryFilter): List<InventoryView>
    suspend fun lines(id: Long, page: WorkflowPageRequest): List<StoredInventoryLine>
    suspend fun nextId(): Long
    suspend fun nextLineId(): Long
    suspend fun insert(value: InventoryView)
    suspend fun insertLine(id: Long, line: StoredInventoryLine)
    suspend fun recordDraftCount(id: Long, lineId: Long, productId: Long, count: Long, expectedPrevious: Long): Boolean
    suspend fun markValidated(value: InventoryView): Boolean
}
/** Trusted session binding supplied by application, never accepted in a UI DTO. */
data class ReviewContext(val actorId: Long, val sessionNonce: String, val generation: String)
interface InventoryReviews {
    /** Issue from durable lines read under the identity gate. Unguessable fresh token; no disk storage. */
    fun issue(context: ReviewContext, inventoryId: Long, durable: List<InventoryPolicy.Count>): InventoryPolicy.Review
    fun find(context: ReviewContext, inventoryId: Long, token: String): InventoryPolicy.Review?
    /** Invalidate on any committed count edit (including ABA), validation, or session/generation loss.
     * Guard issue/find/invalidate with the SAME identity gate as writes. No parallel transaction engine. */
    fun invalidate(inventoryId: Long)
    fun clear()
}
