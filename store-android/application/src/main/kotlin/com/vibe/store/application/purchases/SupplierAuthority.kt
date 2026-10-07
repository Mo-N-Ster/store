package com.vibe.store.application.purchases

import com.vibe.store.api.*
import com.vibe.store.application.persistence.ReadRepositories
import com.vibe.store.application.security.IdentityAuthority
import com.vibe.store.application.security.SecurityAudit
import com.vibe.store.domain.*
import java.text.SimpleDateFormat
import java.util.*

class SupplierAuthority(private val identity: IdentityAuthority,
    private val clock: () -> Long = System::currentTimeMillis) : SupplierService {
    private fun fail(code: WorkflowError): Nothing = throw WorkflowFailure(code)
    private inline fun <T> validated(block: () -> T): T = try { block() }
        catch (e: WorkflowViolation) { fail(WorkflowError.valueOf(e.code.name)) }
    private suspend fun ReadRepositories.requireManager(actor: Long) {
        if (security.account(actor)?.role !in setOf("owner", "manager")) fail(WorkflowError.FORBIDDEN)
    }
    private fun stamp(previous: String? = null): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }
        // Keep optimistic version distinct even for two edits within the same millisecond.
        val prior = previous?.let { runCatching { format.parse(it)?.time }.getOrNull() } ?: Long.MIN_VALUE
        return format.format(Date(maxOf(clock(), if (prior == Long.MIN_VALUE) prior else Math.addExact(prior, 1))))
    }
    override suspend fun list(filter: SupplierFilter): WorkflowPage<SupplierView> {
        val search = validated { WorkflowRules.text(filter.search, 200) }
        return identity.authorizedRead("PURCHASES:READ") { actor ->
            requireManager(actor)
            val normalized = filter.copy(search = search)
            val rows = suppliers.page(normalized)
            val next = filter.page.offset.toLong() + rows.size
            val more = rows.size == filter.page.limit && next <= Int.MAX_VALUE &&
                suppliers.page(normalized.copy(page = WorkflowPageRequest(next.toInt(), 1))).isNotEmpty()
            WorkflowPage(rows, more)
        }
    }
    override suspend fun detail(id: Long): SupplierView {
        validated { WorkflowRules.id(id) }
        return identity.authorizedRead("PURCHASES:READ") { actor ->
            requireManager(actor); suppliers.find(id) ?: fail(WorkflowError.NOT_FOUND)
        }
    }
    override suspend fun save(command: SaveSupplier): SupplierView {
        val fields = validated {
            command.id?.let(WorkflowRules::id)
            PurchasePolicy.supplier(command.name, command.phone, command.email, command.address)
        }
        return identity.authorizedWrite("PURCHASES:UPDATE") { actor ->
            requireManager(actor)
            val old = command.id?.let { suppliers.find(it) ?: fail(WorkflowError.NOT_FOUND) }
            if (old != null && old.updatedAt != command.expectedUpdatedAt) fail(WorkflowError.CONFLICT)
            validated { PurchasePolicy.supplierNameAvailable(suppliers.nameExists(fields.name, command.id)) }
            val now = stamp(old?.updatedAt)
            val result = SupplierView(old?.id ?: suppliers.nextId(), fields.name, fields.phone,
                fields.email, fields.address, command.active, old?.createdAt ?: now, now)
            if (old == null) suppliers.insert(result)
            else if (!suppliers.update(result, old.updatedAt)) fail(WorkflowError.CONFLICT)
            security.appendAudit(SecurityAudit(actor, actor, if (old == null) "supplier_created" else "supplier_updated",
                "supplier", result.id.toString(), true, now))
            result
        }
    }
}
