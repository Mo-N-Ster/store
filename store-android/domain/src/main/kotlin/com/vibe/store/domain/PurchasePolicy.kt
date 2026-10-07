package com.vibe.store.domain

object PurchasePolicy {
    enum class State { DRAFT, VALIDATED, CANCELLED }
    enum class Reception { APPLY, ALREADY_VALIDATED }
    enum class Cancellation { DRAFT_ONLY, COMPENSATE, ALREADY_CANCELLED }
    fun state(raw: String?): State = State.entries.firstOrNull { it.name == raw }
        ?: throw WorkflowViolation(WorkflowRuleError.INVALID_INPUT)
    fun editable(raw: String?) { WorkflowRules.check(state(raw) == State.DRAFT, WorkflowRuleError.CONFLICT) }
    fun reception(raw: String?): Reception = when (state(raw)) {
        State.DRAFT -> Reception.APPLY
        State.VALIDATED -> Reception.ALREADY_VALIDATED
        State.CANCELLED -> throw WorkflowViolation(WorkflowRuleError.CONFLICT)
    }
    fun cancellation(raw: String?, reason: String): Cancellation {
        WorkflowRules.check(WorkflowRules.text(reason, 4000).length >= 3)
        return when (state(raw)) {
            State.DRAFT -> Cancellation.DRAFT_ONLY
            State.VALIDATED -> Cancellation.COMPENSATE
            State.CANCELLED -> Cancellation.ALREADY_CANCELLED
        }
    }
    fun key(value: String?): String? = value?.also {
        WorkflowRules.check(it.matches(Regex("[A-Za-z0-9_-]{1,128}")))
    }
    /** D3: even an otherwise identical creation conflicts. No existing result is returned. */
    fun creationKeyAvailable(collision: Boolean) { WorkflowRules.check(!collision, WorkflowRuleError.CONFLICT) }
    fun cost(value: Double): Double = value.also { WorkflowRules.check(it.isFinite() && it >= 0) }
    fun lineTotal(quantity: Long, unitCost: Double): Double {
        WorkflowRules.quantity(quantity, positive = true); cost(unitCost)
        return (quantity * unitCost).also { WorkflowRules.check(it.isFinite()) }
    }
    data class Line(val productId: Long, val quantity: Long, val unitCost: Double)
    /** Source stores q*cost and SUM(total_line), without POS cent rounding. */
    fun total(lines: List<Line>): Double {
        WorkflowRules.check(lines.size <= WorkflowRules.MAX_LINES)
        WorkflowRules.check(lines.map { it.productId }.distinct().size == lines.size)
        var total = 0.0
        for (line in lines) {
            WorkflowRules.id(line.productId)
            total += lineTotal(line.quantity, line.unitCost)
            WorkflowRules.check(total.isFinite())
        }
        return total
    }
    fun receiveStock(current: Long, quantity: Long): Long {
        WorkflowRules.quantity(current); WorkflowRules.quantity(quantity, true)
        WorkflowRules.check(quantity <= WorkflowRules.MAX_QUANTITY - current)
        return current + quantity
    }
    fun compensateStock(current: Long, quantity: Long): Long {
        WorkflowRules.quantity(current); WorkflowRules.quantity(quantity, true)
        WorkflowRules.check(current >= quantity, WorkflowRuleError.INSUFFICIENT_STOCK)
        return current - quantity
    }
    data class Article(val id: Long, val exists: Boolean, val archived: Boolean)
    /** D4 applies only to a NEW reception, not a historical cancellation/no-op. */
    fun receptionTargets(supplierId: Long?, supplierExists: Boolean, supplierActive: Boolean,
        lines: List<Line>, articles: List<Article>) {
        WorkflowRules.check(lines.isNotEmpty()); total(lines)
        supplierId?.let {
            WorkflowRules.id(it)
            WorkflowRules.check(supplierExists && supplierActive, WorkflowRuleError.INADMISSIBLE_TARGET)
        }
        WorkflowRules.check(articles.map { it.id }.distinct().size == articles.size)
        val byId = articles.associateBy { it.id }
        for (line in lines) WorkflowRules.check(byId[line.productId]?.let { it.exists && !it.archived } == true,
            WorkflowRuleError.INADMISSIBLE_TARGET)
    }
    data class SupplierFields(val name: String, val phone: String, val email: String, val address: String)
    fun supplier(name: String, phone: String, email: String, address: String) = SupplierFields(
        WorkflowRules.text(name, 200, true), WorkflowRules.text(phone, 120),
        WorkflowRules.text(email, 320).lowercase(java.util.Locale.ROOT), WorkflowRules.text(address, 2000))
    // Do not case-fold the name: preserve the existing DB uniqueness semantics.
    fun supplierNameAvailable(exists: Boolean) { WorkflowRules.check(!exists, WorkflowRuleError.CONFLICT) }
    fun header(supplierId: Long?, invoice: String, note: String) {
        supplierId?.let(WorkflowRules::id); WorkflowRules.text(invoice, 200); WorkflowRules.text(note, 4000)
    }
    fun filter(status: String?, supplierId: Long?, productId: Long?, from: String, to: String, offset: Int, limit: Int) {
        status?.let(::state); supplierId?.let(WorkflowRules::id); productId?.let(WorkflowRules::id)
        WorkflowRules.dates(from, to); WorkflowRules.page(offset, limit)
    }
    fun contextualInitialStock(value: Long) { WorkflowRules.check(value == 0L) }
}
