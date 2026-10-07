package com.vibe.store.application.inventory

import com.vibe.store.api.*
import com.vibe.store.application.persistence.ReadRepositories
import com.vibe.store.application.security.IdentityAuthority
import com.vibe.store.application.security.SecurityAudit
import com.vibe.store.domain.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/** I07-P4 inventory workflow. Only the accepted IdentityAuthority/UoW owns DB writes. */
enum class InventoryBoundary { DRAFT, LINE, STOCK, MOVEMENT, TRANSITION, AUDIT, BEFORE_COMMIT, AFTER_COMMIT }
fun interface InventoryProbe { suspend fun reached(boundary: InventoryBoundary) }

class InventoryAuthority(
    private val identity: IdentityAuthority,
    private val clock: () -> Long = System::currentTimeMillis,
    private val probe: InventoryProbe = InventoryProbe {},
    private val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : InventoryService {
    private val operations = AtomicInteger()
    val critical: Boolean get() = operations.get() != 0
    private val reviews: InventoryReviews = VolatileInventoryReviews()
    private fun fail(code: WorkflowError): Nothing = throw WorkflowFailure(code)
    private inline fun <T> checked(block: () -> T): T = try { block() }
        catch (e: WorkflowViolation) { fail(WorkflowError.valueOf(e.code.name)) }
    private fun stamp(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(clock()))
    private suspend fun <T> mutation(
        afterCommit: suspend () -> Unit = {},
        block: suspend () -> T,
    ): T {
        operations.incrementAndGet()
        return applicationScope.async {
            try {
                val result = block()
                afterCommit()
                result
            } catch (e: WorkflowViolation) {
                fail(WorkflowError.valueOf(e.code.name))
            } finally {
                operations.decrementAndGet()
            }
        }.await()
    }
    private suspend fun ReadRepositories.requireManager(actor: Long) {
        if (security.account(actor)?.role !in setOf("owner", "manager")) fail(WorkflowError.FORBIDDEN)
    }
    private suspend fun ReadRepositories.allLines(id: Long): List<StoredInventoryLine> {
        val result = ArrayList<StoredInventoryLine>()
        while (true) {
            if (result.size == WorkflowRules.MAX_LINES) {
                if (inventoryRecords.lines(id, WorkflowPageRequest(result.size, 1)).isNotEmpty())
                    fail(WorkflowError.INVALID_INPUT)
                return result
            }
            val size = minOf(200, WorkflowRules.MAX_LINES - result.size)
            val rows = inventoryRecords.lines(id, WorkflowPageRequest(result.size, size))
            result.addAll(rows)
            if (rows.size < size) return result
        }
    }
    override suspend fun list(filter: InventoryFilter): WorkflowPage<InventoryView> {
        checked { InventoryPolicy.filter(filter.status?.name, filter.from, filter.to, filter.page.offset, filter.page.limit) }
        return identity.authorizedRead("STOCKS:READ") { actor ->
            requireManager(actor)
            val rows = inventoryRecords.page(filter)
            val next = filter.page.offset.toLong() + rows.size
            val more = rows.size == filter.page.limit && next <= Int.MAX_VALUE &&
                inventoryRecords.page(filter.copy(page = WorkflowPageRequest(next.toInt(), 1))).isNotEmpty()
            WorkflowPage(rows, more)
        }
    }
    override suspend fun detail(id: Long): InventoryView {
        checked { WorkflowRules.id(id) }
        return identity.authorizedRead("STOCKS:READ") { actor ->
            requireManager(actor); inventoryRecords.find(id) ?: fail(WorkflowError.NOT_FOUND)
        }
    }
    override suspend fun lines(id: Long, page: WorkflowPageRequest): WorkflowPage<InventoryLineView> {
        checked { WorkflowRules.id(id); WorkflowRules.page(page.offset, page.limit) }
        return identity.authorizedRead("STOCKS:READ") { actor ->
            requireManager(actor)
            inventoryRecords.find(id) ?: fail(WorkflowError.NOT_FOUND)
            val rows = inventoryRecords.lines(id, page)
            val next = page.offset.toLong() + rows.size
            val more = rows.size == page.limit && next <= Int.MAX_VALUE &&
                inventoryRecords.lines(id, WorkflowPageRequest(next.toInt(), 1)).isNotEmpty()
            WorkflowPage(rows.map { line ->
                InventoryLineView(line.id, line.productId, line.expectedQuantity, line.countedQuantity,
                    catalog.find(line.productId)?.stock ?: fail(WorkflowError.INADMISSIBLE_TARGET))
            }, more)
        }
    }
    override suspend fun start(command: StartInventory): InventoryView {
        val note = checked { WorkflowRules.text(command.note, 4000) }
        return mutation { identity.authorizedWrite("STOCKS:CREATE") { actor ->
            requireManager(actor)
            // Capture an exact, bounded scope before ANY durable write.
            val products = ArrayList<CatalogRecord>()
            while (true) {
                if (products.size == WorkflowRules.MAX_LINES) {
                    if (catalog.page(CatalogFilter(offset = products.size, limit = 1)).isNotEmpty())
                        fail(WorkflowError.INVALID_INPUT)
                    break
                }
                val size = minOf(200, WorkflowRules.MAX_LINES - products.size)
                val rows = catalog.page(CatalogFilter(offset = products.size, limit = size))
                products.addAll(rows)
                if (rows.size < size) break
            }
            if (products.isEmpty()) fail(WorkflowError.INVALID_INPUT)
            val now = stamp()
            val id = checked { WorkflowRules.id(inventoryRecords.nextId()) }
            val value = InventoryView(id, "INV-${UUID.randomUUID()}", InventoryStatus.DRAFT,
                note, actor, now, null, null)
            inventoryRecords.insert(value)
            probe.reached(InventoryBoundary.DRAFT)
            for (product in products) {
                checked { WorkflowRules.id(product.id); WorkflowRules.quantity(product.stock) }
                val lineId = checked { WorkflowRules.id(inventoryRecords.nextLineId()) }
                inventoryRecords.insertLine(id, StoredInventoryLine(lineId, product.id, product.stock, product.stock))
                probe.reached(InventoryBoundary.LINE)
            }
            security.appendAudit(SecurityAudit(actor, actor, "inventory_created", "inventory", id.toString(), true, now))
            probe.reached(InventoryBoundary.AUDIT)
            probe.reached(InventoryBoundary.BEFORE_COMMIT)
            value
        } }
    }
    override suspend fun recordCount(command: RecordInventoryCount): InventoryLineView {
        checked {
            WorkflowRules.id(command.inventoryId); WorkflowRules.id(command.lineId)
            WorkflowRules.id(command.productId); WorkflowRules.quantity(command.countedQuantity)
            WorkflowRules.quantity(command.expectedPreviousCount)
        }
        return mutation { identity.authorizedWriteWithSession("STOCKS:UPDATE",
            afterCommit = { reviews.invalidate(command.inventoryId) }) { actor, _, _ ->
            requireManager(actor)
            val inventory = inventoryRecords.find(command.inventoryId) ?: fail(WorkflowError.NOT_FOUND)
            checked { InventoryPolicy.requireDraft(inventory.status.name) }
            val old = allLines(command.inventoryId).singleOrNull {
                it.id == command.lineId && it.productId == command.productId
            } ?: fail(WorkflowError.NOT_FOUND)
            if (old.countedQuantity != command.expectedPreviousCount) fail(WorkflowError.CONFLICT)
            val product = catalog.find(command.productId)
            checked { InventoryPolicy.admissible(product != null, product?.deletedAt != null) }
            if (!inventoryRecords.recordDraftCount(command.inventoryId, command.lineId, command.productId,
                    command.countedQuantity, command.expectedPreviousCount)) fail(WorkflowError.CONFLICT)
            probe.reached(InventoryBoundary.LINE)
            val now = stamp()
            security.appendAudit(SecurityAudit(actor, actor, "inventory_count_recorded", "inventory",
                command.inventoryId.toString(), true, now))
            probe.reached(InventoryBoundary.AUDIT)
            probe.reached(InventoryBoundary.BEFORE_COMMIT)
            InventoryLineView(old.id, old.productId, old.expectedQuantity, command.countedQuantity,
                checkNotNull(product).stock)
        } }
    }
    override suspend fun review(id: Long): InventoryReview {
        checked { WorkflowRules.id(id) }
        return identity.authorizedReadWithSession("STOCKS:READ") { actor, nonce, generation ->
            requireManager(actor)
            val inventory = inventoryRecords.find(id) ?: fail(WorkflowError.NOT_FOUND)
            checked { InventoryPolicy.requireDraft(inventory.status.name) }
            val durable = allLines(id).map { InventoryPolicy.Count(it.id, it.productId, it.countedQuantity) }
            val issued = checked { reviews.issue(ReviewContext(actor, nonce, generation), id, durable) }
            InventoryReview(id, issued.token, issued.lines.size)
        }
    }
    override suspend fun validate(command: ValidateInventory): InventoryView {
        checked { WorkflowRules.id(command.inventoryId) }
        return mutation(afterCommit = { probe.reached(InventoryBoundary.AFTER_COMMIT) }) {
            identity.authorizedWriteWithSession("STOCKS:VALIDATE",
            afterCommit = { reviews.invalidate(command.inventoryId) }) { actor, nonce, generation ->
            requireManager(actor)
            val current = inventoryRecords.find(command.inventoryId) ?: fail(WorkflowError.NOT_FOUND)
            val durable = allLines(command.inventoryId)
            val token = command.reviewToken
            val reviewed = token?.let { reviews.find(ReviewContext(actor, nonce, generation), command.inventoryId, it) }
            checked {
                InventoryPolicy.confirm(command.inventoryId, current.status.name,
                    durable.map { InventoryPolicy.Count(it.id, it.productId, it.countedQuantity) },
                    reviewed, token, command.physicallyConfirmed,
                    command.counts.map { InventoryPolicy.Count(it.lineId, it.productId, it.countedQuantity) })
            }
            // Re-read all current stocks IN this transaction; preflight before first mutation.
            val changes = durable.map { line ->
                val product = catalog.find(line.productId)
                checked { InventoryPolicy.admissible(product != null, product?.deletedAt != null) }
                val old = checkNotNull(product)
                val delta = checked { InventoryPolicy.delta(line.countedQuantity, old.stock) }
                Triple(old, delta, line)
            }
            val now = stamp()
            for ((product, delta, _) in changes) {
                if (delta == 0L) continue
                catalog.write(product.copy(stock = product.stock + delta, updatedAt = now))
                probe.reached(InventoryBoundary.STOCK)
                catalog.movement(product.id, delta, "inventory", product.price, now, command.inventoryId.toString())
                probe.reached(InventoryBoundary.MOVEMENT)
            }
            val result = current.copy(status = InventoryStatus.VALIDATED, validatedBy = actor, validatedAt = now)
            if (!inventoryRecords.markValidated(result)) fail(WorkflowError.CONFLICT)
            probe.reached(InventoryBoundary.TRANSITION)
            security.appendAudit(SecurityAudit(actor, actor, "inventory_validated", "inventory",
                command.inventoryId.toString(), true, now))
            probe.reached(InventoryBoundary.AUDIT)
            probe.reached(InventoryBoundary.BEFORE_COMMIT)
            result
        } }
    }
}
