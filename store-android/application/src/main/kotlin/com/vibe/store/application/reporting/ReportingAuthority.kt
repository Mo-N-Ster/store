package com.vibe.store.application.reporting

import com.vibe.store.api.*
import com.vibe.store.application.persistence.ReadRepositories
import com.vibe.store.application.security.IdentityAuthority
import com.vibe.store.domain.RolePolicy

/** Reporting is read-only; every projection is evaluated within IdentityAuthority's current read lease. */
class ReportingAuthority(
    private val identity: IdentityAuthority,
    private val repository: ReportingRepository? = null,
) : ReportingService {
    override suspend fun snapshot(filter: ReportFilter): DocumentModel =
        identity.authorizedRead("FINANCES:READ") { actor ->
            validateFilter(filter)
            if (filter.tab == ReportTab.PURCHASES) requireAdditionalRight(actor, "PURCHASES:READ")
            if (filter.tab == ReportTab.TEAM) requireAdditionalRight(actor, "PRESENCE:READ")
            (repository ?: reporting).snapshot(filter)
        }

    override suspend fun dashboard(filter: ReportFilter): DocumentModel =
        identity.authorizedRead("DASHBOARD:READ") { actor ->
            validateFilter(filter)
            if (filter.tab != ReportTab.SUMMARY) throw WorkflowFailure(WorkflowError.INVALID_INPUT)
            val request = try { DashboardRequest.fromLegacyFilter(filter) }
            catch (_: IllegalArgumentException) { throw WorkflowFailure(WorkflowError.INVALID_INPUT) }
            val includePresence = hasEffectiveRight(actor, "PRESENCE:READ")
            (repository ?: reporting).dashboard(request, includePresence)
        }

    override suspend fun dashboard(request: DashboardRequest): DocumentModel =
        identity.authorizedRead("DASHBOARD:READ") { actor ->
            validateZone(request.timeZoneId)
            val includePresence = hasEffectiveRight(actor, "PRESENCE:READ")
            (repository ?: reporting).dashboard(request, includePresence)
        }

    private suspend fun ReadRepositories.requireAdditionalRight(actor: Long, right: String) {
        if (!hasEffectiveRight(actor, right)) throw SecurityFailure(SecurityError.FORBIDDEN)
    }

    private suspend fun ReadRepositories.hasEffectiveRight(actor: Long, right: String): Boolean {
        val account = security.account(actor) ?: return false
        val inherited = security.inherited(actor).intersect(RolePolicy.inherited(account.role))
        val effective = if (account.role == "owner") inherited else inherited - security.denied(actor)
        return right in effective
    }

    private fun validateFilter(filter: ReportFilter) {
        validateZone(filter.timeZoneId)
        val statusAllowed = when (filter.tab) {
            ReportTab.SALES -> setOf("validated", "cancelled")
            ReportTab.PURCHASES -> setOf("DRAFT", "VALIDATED", "CANCELLED")
            ReportTab.TEAM -> setOf("VALID", "CORRECTED", "INTERRUPTED", "OPEN")
            ReportTab.STOCK -> setOf("sale", "invoice_reversal", "purchase", "purchase_cancellation", "inventory", "adjustment", "initial", "product_deletion")
            else -> emptySet()
        }
        if (filter.status != null && filter.status !in statusAllowed) throw WorkflowFailure(WorkflowError.INVALID_INPUT)
        if (filter.category?.isBlank() == true) throw WorkflowFailure(WorkflowError.INVALID_INPUT)
        if (filter.productId != null && filter.tab !in setOf(ReportTab.SALES, ReportTab.PRODUCTS, ReportTab.STOCK, ReportTab.PURCHASES))
            throw WorkflowFailure(WorkflowError.INVALID_INPUT)
        if (filter.tab !in setOf(ReportTab.SALES, ReportTab.PRODUCTS, ReportTab.STOCK) && filter.category != null)
            throw WorkflowFailure(WorkflowError.INVALID_INPUT)
        if (filter.tab != ReportTab.PURCHASES && filter.supplierId != null)
            throw WorkflowFailure(WorkflowError.INVALID_INPUT)
        if (filter.tab != ReportTab.TEAM && filter.employeeId != null)
            throw WorkflowFailure(WorkflowError.INVALID_INPUT)
        if (filter.grain != ReportGrain.DAY && filter.tab !in setOf(ReportTab.SUMMARY, ReportTab.SALES, ReportTab.FINANCES))
            throw WorkflowFailure(WorkflowError.INVALID_INPUT)
    }

    private fun validateZone(zoneId: String) {
        try { java.time.ZoneId.of(zoneId) }
        catch (_: Exception) { throw WorkflowFailure(WorkflowError.INVALID_INPUT) }
    }
}
