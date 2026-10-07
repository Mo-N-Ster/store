package com.vibe.store.application.purchases

import com.vibe.store.api.*
import com.vibe.store.application.persistence.ReadRepositories
import com.vibe.store.application.security.IdentityAuthority
import com.vibe.store.application.security.SecurityAudit
import com.vibe.store.domain.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/** I07-P3: the existing identity gate, coordinator and Room UoW own every decision. */
enum class PurchaseBoundary { DRAFT, LINE, STOCK, MOVEMENT, TRANSITION, AUDIT, BEFORE_COMMIT, AFTER_COMMIT }
fun interface PurchaseProbe { suspend fun reached(boundary: PurchaseBoundary) }

class PurchaseAuthority(
    private val identity: IdentityAuthority,
    private val catalogService: CatalogService,
    private val clock: () -> Long = System::currentTimeMillis,
    private val probe: PurchaseProbe = PurchaseProbe {},
    private val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : PurchaseService {
    private val operations = AtomicInteger()
    val critical: Boolean get() = operations.get() != 0
    private fun fail(error: WorkflowError): Nothing = throw WorkflowFailure(error)
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
    /** Read only from the active lease; exact bounds, no truncated receipt or compensation. */
    private suspend fun ReadRepositories.allLines(id: Long): List<PurchaseLineView> {
        val result = ArrayList<PurchaseLineView>()
        while (true) {
            if (result.size == WorkflowRules.MAX_LINES) {
                if (purchaseRecords.lines(id, WorkflowPageRequest(result.size, 1)).isNotEmpty())
                    fail(WorkflowError.INVALID_INPUT)
                return result
            }
            val size = minOf(200, WorkflowRules.MAX_LINES - result.size)
            val page = purchaseRecords.lines(id, WorkflowPageRequest(result.size, size))
            result.addAll(page)
            if (page.size < size) return result
        }
    }
    private fun details(value: StoredPurchase): PurchaseDetail = value.detail

    override suspend fun list(filter: PurchaseFilter): WorkflowPage<PurchaseSummary> {
        checked { PurchasePolicy.filter(filter.status?.name, filter.supplierId, filter.productId,
            filter.from, filter.to, filter.page.offset, filter.page.limit) }
        return identity.authorizedRead("PURCHASES:READ") { actor ->
            requireManager(actor)
            val rows = purchaseRecords.page(filter)
            val next = filter.page.offset.toLong() + rows.size
            val more = rows.size == filter.page.limit && next <= Int.MAX_VALUE &&
                purchaseRecords.page(filter.copy(page = WorkflowPageRequest(next.toInt(), 1))).isNotEmpty()
            WorkflowPage(rows.map { it.detail.summary }, more)
        }
    }
    override suspend fun detail(id: Long): PurchaseDetail {
        checked { WorkflowRules.id(id) }
        return identity.authorizedRead("PURCHASES:READ") { actor ->
            requireManager(actor)
            purchaseRecords.find(id)?.let(::details) ?: fail(WorkflowError.NOT_FOUND)
        }
    }
    override suspend fun lines(id: Long, page: WorkflowPageRequest): WorkflowPage<PurchaseLineView> {
        checked { WorkflowRules.id(id); WorkflowRules.page(page.offset, page.limit) }
        return identity.authorizedRead("PURCHASES:READ") { actor ->
            requireManager(actor)
            purchaseRecords.find(id) ?: fail(WorkflowError.NOT_FOUND)
            val rows = purchaseRecords.lines(id, page)
            val next = page.offset.toLong() + rows.size
            val more = rows.size == page.limit && next <= Int.MAX_VALUE &&
                purchaseRecords.lines(id, WorkflowPageRequest(next.toInt(), 1)).isNotEmpty()
            WorkflowPage(rows, more)
        }
    }
    override suspend fun createDraft(command: CreatePurchaseDraft): PurchaseDetail {
        val invoice = checked { WorkflowRules.text(command.supplierInvoice, 200) }
        val note = checked { WorkflowRules.text(command.note, 4000) }
        val key = checked { PurchasePolicy.header(command.supplierId, invoice, note); PurchasePolicy.key(command.idempotencyKey) }
        return mutation { identity.authorizedWrite("PURCHASES:CREATE") { actor ->
            requireManager(actor)
            if (key != null) checked { PurchasePolicy.creationKeyAvailable(purchaseRecords.keyExists(key)) }
            command.supplierId?.let { id ->
                if (suppliers.find(id)?.active != true) fail(WorkflowError.INADMISSIBLE_TARGET)
            }
            val now = stamp()
            val id = checked { WorkflowRules.id(purchaseRecords.nextId()) }
            val summary = PurchaseSummary(id, "ACHAT-${UUID.randomUUID()}", command.supplierId,
                PurchaseStatus.DRAFT, 0.0, actor, now)
            val value = StoredPurchase(PurchaseDetail(summary, invoice, note, null, null, null, null, null), key)
            purchaseRecords.insert(value)
            probe.reached(PurchaseBoundary.DRAFT)
            security.appendAudit(SecurityAudit(actor, actor, "purchase_created", "purchase", id.toString(), true, now))
            probe.reached(PurchaseBoundary.AUDIT)
            probe.reached(PurchaseBoundary.BEFORE_COMMIT)
            value.detail
        } }
    }
    override suspend fun saveLine(command: SavePurchaseLine): PurchaseLineView {
        checked {
            WorkflowRules.id(command.purchaseId); WorkflowRules.id(command.productId)
            PurchasePolicy.lineTotal(command.quantity, command.unitCost)
            command.expectedLine?.let { old ->
                WorkflowRules.id(old.id)
                WorkflowRules.check(old.productId == command.productId &&
                    old.total == PurchasePolicy.lineTotal(old.quantity, old.unitCost))
            }
        }
        return mutation { identity.authorizedWrite("PURCHASES:UPDATE") { actor ->
            requireManager(actor)
            val existing = purchaseRecords.find(command.purchaseId) ?: fail(WorkflowError.NOT_FOUND)
            checked { PurchasePolicy.editable(existing.detail.summary.status.name) }
            if (catalog.find(command.productId)?.takeIf { it.deletedAt == null } == null)
                fail(WorkflowError.INADMISSIBLE_TARGET)
            val before = allLines(command.purchaseId)
            val previous = before.singleOrNull { it.productId == command.productId }
            if (previous != command.expectedLine) fail(WorkflowError.CONFLICT)
            if (previous == null && before.size >= WorkflowRules.MAX_LINES) fail(WorkflowError.INVALID_INPUT)
            val updated = PurchaseLineView(previous?.id ?: purchaseRecords.nextLineId(), command.productId,
                command.quantity, command.unitCost, checked { PurchasePolicy.lineTotal(command.quantity, command.unitCost) })
            val changed = before.filterNot { it.productId == command.productId } + updated
            val total = checked { PurchasePolicy.total(changed.sortedBy { it.id }.map { PurchasePolicy.Line(it.productId, it.quantity, it.unitCost) }) }
            if (!purchaseRecords.replaceDraftLine(command.purchaseId, updated, previous)) fail(WorkflowError.CONFLICT)
            probe.reached(PurchaseBoundary.LINE)
            if (!purchaseRecords.updateDraftTotal(command.purchaseId, total)) fail(WorkflowError.CONFLICT)
            val now = stamp()
            security.appendAudit(SecurityAudit(actor, actor, "purchase_line_saved", "purchase", command.purchaseId.toString(), true, now))
            probe.reached(PurchaseBoundary.AUDIT)
            probe.reached(PurchaseBoundary.BEFORE_COMMIT)
            updated
        } }
    }
    override suspend fun validate(command: ValidatePurchase): PurchaseDetail {
        checked { WorkflowRules.id(command.purchaseId) }
        return mutation(afterCommit = { probe.reached(PurchaseBoundary.AFTER_COMMIT) }) {
            identity.authorizedWrite("PURCHASES:VALIDATE") { actor ->
            requireManager(actor)
            val existing = purchaseRecords.find(command.purchaseId) ?: fail(WorkflowError.NOT_FOUND)
            val current = existing.detail
            when (checked { PurchasePolicy.reception(current.summary.status.name) }) {
                PurchasePolicy.Reception.ALREADY_VALIDATED -> return@authorizedWrite current
                PurchasePolicy.Reception.APPLY -> Unit
            }
            val lines = allLines(command.purchaseId)
            val records = lines.map { line -> line to catalog.find(line.productId) }
            val supplier = current.summary.supplierId?.let { suppliers.find(it) }
            checked {
                PurchasePolicy.receptionTargets(current.summary.supplierId, supplier != null, supplier?.active == true,
                    lines.map { PurchasePolicy.Line(it.productId, it.quantity, it.unitCost) },
                    records.map { (line, product) -> PurchasePolicy.Article(line.productId, product != null, product?.deletedAt != null) })
            }
            if (current.summary.total != checked { PurchasePolicy.total(lines.map { PurchasePolicy.Line(it.productId, it.quantity, it.unitCost) }) })
                fail(WorkflowError.CONFLICT)
            // Preflight EVERY product before the first durable mutation.
            val changed = records.map { (line, product) ->
                val original = product ?: fail(WorkflowError.INADMISSIBLE_TARGET)
                val nextStock = checked { PurchasePolicy.receiveStock(original.stock, line.quantity) }
                Triple(line, original, nextStock)
            }
            val now = stamp()
            for ((line, original, nextStock) in changed) {
                catalog.write(original.copy(stock = nextStock, updatedAt = now))
                probe.reached(PurchaseBoundary.STOCK)
                catalog.movement(line.productId, line.quantity, "purchase", line.unitCost, now, command.purchaseId.toString())
                probe.reached(PurchaseBoundary.MOVEMENT)
            }
            val result = current.copy(summary = current.summary.copy(status = PurchaseStatus.VALIDATED),
                validatedBy = actor, validatedAt = now)
            if (!purchaseRecords.transition(existing.copy(detail = result), PurchaseStatus.DRAFT)) fail(WorkflowError.CONFLICT)
            probe.reached(PurchaseBoundary.TRANSITION)
            security.appendAudit(SecurityAudit(actor, actor, "purchase_validated", "purchase", command.purchaseId.toString(), true, now))
            probe.reached(PurchaseBoundary.AUDIT)
            probe.reached(PurchaseBoundary.BEFORE_COMMIT)
            result
        } }
    }
    override suspend fun cancel(command: CancelPurchase): PurchaseDetail {
        checked { WorkflowRules.id(command.purchaseId); WorkflowRules.text(command.reason, 4000, required = true)
            PurchasePolicy.cancellation("DRAFT", command.reason) }
        return mutation(afterCommit = { probe.reached(PurchaseBoundary.AFTER_COMMIT) }) {
            identity.authorizedWrite("PURCHASES:DELETE") { actor ->
            requireManager(actor)
            val existing = purchaseRecords.find(command.purchaseId) ?: fail(WorkflowError.NOT_FOUND)
            val current = existing.detail
            val action = checked { PurchasePolicy.cancellation(current.summary.status.name, command.reason) }
            if (action == PurchasePolicy.Cancellation.ALREADY_CANCELLED) return@authorizedWrite current
            val changed = if (action == PurchasePolicy.Cancellation.COMPENSATE) {
                allLines(command.purchaseId).map { line ->
                    val product = catalog.find(line.productId) ?: fail(WorkflowError.INADMISSIBLE_TARGET)
                    val nextStock = checked { PurchasePolicy.compensateStock(product.stock, line.quantity) }
                    Triple(line, product, nextStock)
                }
            } else emptyList()
            val now = stamp()
            for ((line, product, nextStock) in changed) {
                catalog.write(product.copy(stock = nextStock, updatedAt = now))
                probe.reached(PurchaseBoundary.STOCK)
                catalog.movement(line.productId, -line.quantity, "purchase_cancellation", line.unitCost,
                    now, command.purchaseId.toString())
                probe.reached(PurchaseBoundary.MOVEMENT)
            }
            val result = current.copy(summary = current.summary.copy(status = PurchaseStatus.CANCELLED),
                cancelledBy = actor, cancelledAt = now, cancellationReason = command.reason.trim())
            if (!purchaseRecords.transition(existing.copy(detail = result), current.summary.status)) fail(WorkflowError.CONFLICT)
            probe.reached(PurchaseBoundary.TRANSITION)
            security.appendAudit(SecurityAudit(actor, actor, "purchase_cancelled", "purchase", command.purchaseId.toString(), true, now))
            probe.reached(PurchaseBoundary.AUDIT)
            probe.reached(PurchaseBoundary.BEFORE_COMMIT)
            result
        } }
    }
    override suspend fun createArticle(command: PurchaseArticleCreation): CatalogWrite {
        checked {
            WorkflowRules.text(command.name, 200, true); WorkflowRules.text(command.category, 200, true)
            WorkflowRules.quantity(command.minimumStock); PurchasePolicy.cost(command.price)
        }
        // Independent catalog use case: never called within an active purchase UoW.
        return catalogService.save(ProductDraft(name = command.name, category = command.category,
            hashtag = command.hashtag, description = command.description, price = command.price,
            initialStock = 0.0, minimumStock = command.minimumStock.toDouble()), command.image)
    }
}
