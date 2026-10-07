package com.vibe.store.application.inventory

import com.vibe.store.domain.InventoryPolicy
import com.vibe.store.domain.WorkflowRules
import java.util.UUID

/** Memory-only, bounded, session/generation-scoped proofs of reviewed durable counts.
 * All calls must be made while holding IdentityAuthority's session gate. */
internal class VolatileInventoryReviews : InventoryReviews {
    private data class Entry(val context: ReviewContext, val review: InventoryPolicy.Review)
    private val entries = LinkedHashMap<Long, Entry>()
    override fun issue(context: ReviewContext, inventoryId: Long, durable: List<InventoryPolicy.Count>): InventoryPolicy.Review {
        WorkflowRules.id(inventoryId)
        WorkflowRules.check(durable.isNotEmpty() && durable.size <= WorkflowRules.MAX_LINES)
        // A changed session/generation cannot retain an old, usable review.
        entries.entries.removeAll { it.value.context != context }
        entries.remove(inventoryId)
        if (entries.size >= 128) entries.remove(entries.keys.first())
        val review = InventoryPolicy.Review(inventoryId, UUID.randomUUID().toString(), durable)
        entries[inventoryId] = Entry(context, review)
        return review
    }
    override fun find(context: ReviewContext, inventoryId: Long, token: String): InventoryPolicy.Review? {
        val entry = entries[inventoryId] ?: return null
        if (entry.context != context) {
            entries.remove(inventoryId)
            return null
        }
        return entry.review.takeIf { it.token == token }
    }
    override fun invalidate(inventoryId: Long) { entries.remove(inventoryId) }
    override fun clear() { entries.clear() }
}
