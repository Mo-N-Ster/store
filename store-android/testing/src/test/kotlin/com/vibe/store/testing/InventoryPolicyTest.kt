package com.vibe.store.testing

import com.vibe.store.domain.*
import com.vibe.store.api.*
import org.junit.Assert.*
import org.junit.Test

class InventoryPolicyTest {
    private val durable = listOf(InventoryPolicy.Count(10, 1, 4), InventoryPolicy.Count(20, 2, 0))
    private val review get() = InventoryPolicy.Review(1, "volatile-review", durable)
    private fun refused(block: () -> Unit) {
        try { block(); fail("Expected refusal") } catch (_: WorkflowViolation) { }
    }
    private fun confirm(counts: List<InventoryPolicy.Count> = durable,
        snapshot: InventoryPolicy.Review? = review, token: String? = "volatile-review", acknowledged: Boolean = true,
        current: List<InventoryPolicy.Count> = durable) =
        InventoryPolicy.confirm(1, "DRAFT", current, snapshot, token, acknowledged, counts)
    @Test fun d1ReadRequiresBothTrustedRoleAndEffectiveRight() {
        InventoryPolicy.readAccess("owner", setOf("STOCKS:READ"))
        InventoryPolicy.readAccess("manager", setOf("STOCKS:READ"))
        for (role in listOf("employee", "OWNER", "unknown", null)) refused {
            InventoryPolicy.readAccess(role, setOf("STOCKS:READ"))
        }
        refused { InventoryPolicy.readAccess("manager", emptySet()) }
        assertTrue("Existing employee stock consultation untouched", "STOCKS:READ" in RolePolicy.inherited("employee"))
    }
    @Test fun d2PrefilledAndMissingAcknowledgementNeverConfirm() {
        refused { confirm(acknowledged = false) }
        refused { confirm(snapshot = null) }
        refused { confirm(token = null) }
        refused { confirm(token = "") }
        confirm() // explicit acknowledgement can confirm an unchanged physical count.
    }
    @Test fun d2IncompleteDuplicateExtraAndMismatchedLinesReject() {
        refused { confirm(emptyList()) }
        refused { confirm(durable.take(1)) }
        refused { confirm(listOf(durable[0], durable[0])) }
        refused { confirm(durable + InventoryPolicy.Count(30, 3, 2)) }
        refused { confirm(listOf(durable[0].copy(productId = 2), durable[1])) }
        refused { confirm(listOf(durable[0].copy(lineId = 99), durable[1])) }
        refused { confirm(listOf(durable[0].copy(quantity = 5), durable[1])) }
        confirm(durable.reversed())
    }
    @Test fun d2DurableReReadAndReviewIdentityMustMatch() {
        refused { confirm(current = listOf(durable[0].copy(quantity = 5), durable[1])) }
        refused { confirm(snapshot = InventoryPolicy.Review(2, "volatile-review", durable)) }
        refused { confirm(snapshot = InventoryPolicy.Review(1, "new-review-after-edit", durable)) }
        refused { confirm(snapshot = InventoryPolicy.Review(1, "", durable), token = "") }
    }
    @Test fun d2ProcessDeathOrSessionInvalidationRequiresNewReview() {
        // No registry implementation in P1: model trusted lookup returning null after invalidation/death.
        refused { confirm(snapshot = null) }
        refused { confirm(snapshot = InventoryPolicy.Review(1, "new-session", durable)) }
        confirm(snapshot = InventoryPolicy.Review(1, "new-session", durable), token = "new-session")
    }
    @Test fun d2ReviewAndCommandCopyInputLists() {
        val mutable = durable.toMutableList()
        val snapshot = InventoryPolicy.Review(1, "volatile-review", mutable); mutable.clear()
        confirm(snapshot = snapshot)
        val payload = mutableListOf(InventoryCountAttestation(10, 1, 4))
        val command = ValidateInventory(1, "opaque", true, payload); payload.clear()
        assertEquals(1, command.counts.size)
    }
    @Test fun validationIsUniqueAndMalformedStatusesFailClosed() {
        InventoryPolicy.requireDraft("DRAFT")
        for (status in listOf("VALIDATED", "CANCELLED", "draft", "", "unknown", null))
            refused { InventoryPolicy.requireDraft(status) }
    }
    @Test fun d4ArchivedOrMissingArticleIsNotAdmissible() {
        InventoryPolicy.admissible(true, false)
        refused { InventoryPolicy.admissible(true, true) }
        refused { InventoryPolicy.admissible(false, false) }
    }
    @Test fun deltaUsesCurrentStockAndAllSafeIntegerExtremes() {
        assertEquals(-3L, InventoryPolicy.delta(4, 7))
        assertEquals(0L, InventoryPolicy.delta(4, 4))
        assertEquals(WorkflowRules.MAX_QUANTITY, InventoryPolicy.delta(WorkflowRules.MAX_QUANTITY, 0))
        assertEquals(-WorkflowRules.MAX_QUANTITY, InventoryPolicy.delta(0, WorkflowRules.MAX_QUANTITY))
        refused { InventoryPolicy.delta(-1, 0) }; refused { InventoryPolicy.delta(Long.MAX_VALUE, 0) }
        refused { confirm(listOf(durable[0].copy(quantity = -1), durable[1])) }
    }
    @Test fun inventoryFiltersRejectUnknownStatesBadDatesAndUnboundedPages() {
        InventoryPolicy.filter("CANCELLED", "", "", 0, 40) // historical readable, no cancel operation.
        refused { InventoryPolicy.filter("invalid", "", "", 0, 40) }
        refused { InventoryPolicy.filter(null, "2026-02-30", "", 0, 40) }
        refused { InventoryPolicy.filter(null, "", "", 0, 0) }
    }
}
