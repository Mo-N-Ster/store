package com.vibe.store.domain

object InventoryPolicy {
    data class Count(val lineId: Long, val productId: Long, val quantity: Long)
    /** Trusted, volatile review issued by application; never reconstructed from an incoming DTO. */
    class Review(val inventoryId: Long, val token: String, lines: List<Count>) {
        val lines: List<Count> = java.util.Collections.unmodifiableList(ArrayList(lines))
    }
    fun requireDraft(status: String?) {
        WorkflowRules.check(status in setOf("DRAFT", "VALIDATED", "CANCELLED"))
        WorkflowRules.check(status == "DRAFT", WorkflowRuleError.CONFLICT)
    }
    fun readAccess(trustedRole: String?, effectiveRights: Set<String>) =
        WorkflowRules.access(trustedRole, effectiveRights, "STOCKS:READ")
    private fun checked(lines: List<Count>, structuralError: WorkflowRuleError = WorkflowRuleError.INVALID_INPUT): Map<Long, Count> {
        WorkflowRules.check(lines.size in 1..WorkflowRules.MAX_LINES, structuralError)
        lines.forEach { WorkflowRules.id(it.lineId); WorkflowRules.id(it.productId); WorkflowRules.quantity(it.quantity) }
        WorkflowRules.check(lines.distinctBy { it.lineId }.size == lines.size &&
            lines.distinctBy { it.productId }.size == lines.size, structuralError)
        return lines.associateBy { it.lineId }
    }
    /** D2: compare review AND attestation against the durable draft re-read under UoW. */
    fun confirm(inventoryId: Long, status: String?, durable: List<Count>, review: Review?,
        suppliedToken: String?, physicallyConfirmed: Boolean, attested: List<Count>) {
        WorkflowRules.id(inventoryId); requireDraft(status)
        WorkflowRules.check(physicallyConfirmed && review != null, WorkflowRuleError.CONFLICT)
        val trusted = checkNotNull(review)
        WorkflowRules.check(trusted.inventoryId == inventoryId && trusted.token.isNotBlank() &&
            trusted.token == suppliedToken, WorkflowRuleError.CONFLICT)
        val current = checked(durable)
        WorkflowRules.check(checked(trusted.lines) == current && checked(attested, WorkflowRuleError.CONFLICT) == current, WorkflowRuleError.CONFLICT)
    }
    fun admissible(exists: Boolean, archived: Boolean) {
        WorkflowRules.check(exists && !archived, WorkflowRuleError.INADMISSIBLE_TARGET)
    }
    fun delta(counted: Long, currentStock: Long): Long {
        WorkflowRules.quantity(counted); WorkflowRules.quantity(currentStock)
        return counted - currentStock // both are bounded safe integers: subtraction cannot overflow Long.
    }
    fun filter(status: String?, from: String, to: String, offset: Int, limit: Int) {
        status?.let { WorkflowRules.check(it in setOf("DRAFT", "VALIDATED", "CANCELLED")) }
        WorkflowRules.dates(from, to); WorkflowRules.page(offset, limit)
    }
}
