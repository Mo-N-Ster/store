package com.vibe.store.api

enum class WorkflowError { INVALID_INPUT, CONFLICT, FORBIDDEN, NOT_FOUND, INADMISSIBLE_TARGET, INSUFFICIENT_STOCK, UNAVAILABLE }
class WorkflowFailure(val code: WorkflowError) : Exception(code.name)
data class WorkflowPageRequest(val offset: Int = 0, val limit: Int = 40) {
    init { if (offset < 0 || limit !in 1..200) throw WorkflowFailure(WorkflowError.INVALID_INPUT) }
}
class WorkflowPage<T>(items: List<T>, val hasMore: Boolean) {
    val items: List<T> = java.util.Collections.unmodifiableList(ArrayList(items))
    init { if (items.size > 200) throw WorkflowFailure(WorkflowError.INVALID_INPUT) }
}
enum class PurchaseStatus { DRAFT, VALIDATED, CANCELLED }
enum class InventoryStatus { DRAFT, VALIDATED, CANCELLED }
// CANCELLED inventory is readable historical state, never an I07 cancellation command.
