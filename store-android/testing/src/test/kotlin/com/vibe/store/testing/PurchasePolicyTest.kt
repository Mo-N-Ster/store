package com.vibe.store.testing

import com.vibe.store.domain.*
import com.vibe.store.api.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class PurchasePolicyTest {
    private fun refused(code: WorkflowRuleError? = null, block: () -> Unit) {
        try { block(); fail("Expected refusal") } catch (failure: WorkflowViolation) {
            if (code != null) assertEquals(code, failure.code)
        }
    }
    @Test fun quantitiesRejectMalformedAndRespectSafeIntegerBound() {
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.0, 1.1, 9_007_199_254_740_992.0))
            refused { WorkflowRules.quantity(bad, true) }
        assertEquals(1L, WorkflowRules.quantity(1.0, true))
        assertEquals(WorkflowRules.MAX_QUANTITY, WorkflowRules.quantity(WorkflowRules.MAX_QUANTITY, true))
        refused { WorkflowRules.id(0) }; refused { WorkflowRules.id(Long.MAX_VALUE) }
    }
    @Test fun costsAndTotalsPreserveExactPrecision() {
        assertEquals(BigDecimal("3.70370367"), PurchasePolicy.lineTotal(3, BigDecimal("1.23456789")))
        assertEquals(BigDecimal.ZERO, PurchasePolicy.total(emptyList()))
        assertEquals(BigDecimal("0.3"), PurchasePolicy.total(listOf(
            PurchasePolicy.Line(1, 1, BigDecimal("0.1")), PurchasePolicy.Line(2, 1, BigDecimal("0.2")))))
        assertEquals(BigDecimal("0.999"), PurchasePolicy.total(listOf(PurchasePolicy.Line(1, 3, BigDecimal("0.333")))))
        refused { PurchasePolicy.lineTotal(1, BigDecimal("-0.01")) }
        val huge = BigDecimal("1e400")
        assertTrue(ExactMoney.same(huge.multiply(BigDecimal("2")), PurchasePolicy.lineTotal(2, huge)))
    }
    @Test fun lineUniquenessAndBoundedBodyAreRequired() {
        val line = PurchasePolicy.Line(1, 1, BigDecimal.ONE)
        refused { PurchasePolicy.total(listOf(line, line)) }
        refused { PurchasePolicy.total(List(WorkflowRules.MAX_LINES + 1) { line.copy(productId = it + 1L) }) }
        refused { PurchasePolicy.total(listOf(line.copy(productId = -1))) }
    }
    @Test fun transitionsFailClosedAndDoNotConflateCancellationEffects() {
        PurchasePolicy.editable("DRAFT")
        assertEquals(PurchasePolicy.Reception.APPLY, PurchasePolicy.reception("DRAFT"))
        assertEquals(PurchasePolicy.Reception.ALREADY_VALIDATED, PurchasePolicy.reception("VALIDATED"))
        refused(WorkflowRuleError.CONFLICT) { PurchasePolicy.reception("CANCELLED") }
        for (status in listOf(null, "", "draft", "unknown", " VALIDATED")) refused { PurchasePolicy.state(status) }
        refused { PurchasePolicy.editable("VALIDATED") }
        assertEquals(PurchasePolicy.Cancellation.DRAFT_ONLY, PurchasePolicy.cancellation("DRAFT", " why "))
        assertEquals(PurchasePolicy.Cancellation.COMPENSATE, PurchasePolicy.cancellation("VALIDATED", "why"))
        assertEquals(PurchasePolicy.Cancellation.ALREADY_CANCELLED, PurchasePolicy.cancellation("CANCELLED", "why"))
        refused { PurchasePolicy.cancellation("CANCELLED", " x ") }
    }
    @Test fun d3AnyCollisionConflictsWithoutReplay() {
        assertNull(PurchasePolicy.key(null))
        assertEquals("draft-1_2", PurchasePolicy.key("draft-1_2"))
        for (bad in listOf("", " ", "../key", "a".repeat(129))) refused { PurchasePolicy.key(bad) }
        PurchasePolicy.creationKeyAvailable(false)
        refused(WorkflowRuleError.CONFLICT) { PurchasePolicy.creationKeyAvailable(true) }
    }
    @Test fun d4NewReceptionRequiresSelectedActiveSupplierAndAdmissibleArticles() {
        val lines = listOf(PurchasePolicy.Line(1, 2, BigDecimal("0.333")))
        val articles = listOf(PurchasePolicy.Article(1, true, false))
        PurchasePolicy.receptionTargets(null, false, false, lines, articles)
        PurchasePolicy.receptionTargets(1, true, true, lines, articles)
        refused { PurchasePolicy.receptionTargets(1, false, true, lines, articles) }
        refused { PurchasePolicy.receptionTargets(1, true, false, lines, articles) }
        refused { PurchasePolicy.receptionTargets(null, false, false, lines, listOf(PurchasePolicy.Article(1, true, true))) }
        refused { PurchasePolicy.receptionTargets(null, false, false, lines, emptyList()) }
        refused { PurchasePolicy.receptionTargets(null, false, false, emptyList(), articles) }
        // Cancellation intentionally has NO supplier/archival activation requirement.
        assertEquals(PurchasePolicy.Cancellation.COMPENSATE, PurchasePolicy.cancellation("VALIDATED", "historic"))
        assertEquals(1L, PurchasePolicy.compensateStock(3, 2))
    }
    @Test fun additionAndCompensationBoundsPreventNegativeStockAndOverflow() {
        assertEquals(WorkflowRules.MAX_QUANTITY, PurchasePolicy.receiveStock(WorkflowRules.MAX_QUANTITY - 1, 1))
        refused { PurchasePolicy.receiveStock(WorkflowRules.MAX_QUANTITY, 1) }
        assertEquals(0L, PurchasePolicy.compensateStock(3, 3))
        refused(WorkflowRuleError.INSUFFICIENT_STOCK) { PurchasePolicy.compensateStock(2, 3) }
        refused { PurchasePolicy.compensateStock(-1, 1) }
    }
    @Test fun fieldsAndFiltersAreBoundedAndDatesStrict() {
        val value = PurchasePolicy.supplier("  Alpha ", " 1 ", " A@EXAMPLE.INVALID ", " here ")
        assertEquals("Alpha", value.name); assertEquals("a@example.invalid", value.email)
        assertNotEquals(value.name, PurchasePolicy.supplier("alpha", "", "", "").name)
        refused { PurchasePolicy.supplier("  ", "", "", "") }
        refused { PurchasePolicy.supplier("x".repeat(201), "", "", "") }
        refused { PurchasePolicy.header(null, "ok", "bad\u0000") }
        refused { PurchasePolicy.supplierNameAvailable(true) }
        PurchasePolicy.filter(null, null, null, "2024-02-29", "2024-03-01", 0, 200)
        for (date in listOf("2026-02-29", "2026-13-01", "2026-1-01", "2026-01-01x"))
            refused { PurchasePolicy.filter(null, null, null, date, "", 0, 40) }
        refused { PurchasePolicy.filter(null, null, null, "2026-02-01", "2026-01-01", 0, 40) }
        refused { PurchasePolicy.filter("", null, null, "", "", 0, 40) }
        refused { WorkflowRules.page(-1, 40) }; refused { WorkflowRules.page(0, 201) }
    }
    @Test fun contextualArticleContractHasZeroInitialStockOnly() {
        PurchasePolicy.contextualInitialStock(0)
        refused { PurchasePolicy.contextualInitialStock(1) }
        assertFalse(PurchaseArticleCreation::class.java.declaredFields.any { it.name == "initialStock" })
    }
    @Test fun publicPaginationIsBoundedAndSnapshotsCannotAliasMutableLists() {
        for (request in listOf(-1 to 40, 0 to 0, 0 to 201)) {
            try { WorkflowPageRequest(request.first, request.second); fail("Expected bounds") }
            catch (failure: WorkflowFailure) { assertEquals(WorkflowError.INVALID_INPUT, failure.code) }
        }
        val original = mutableListOf(1)
        val page = WorkflowPage(original, false); original.clear()
        assertEquals(listOf(1), page.items)
    }
}
