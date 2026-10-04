package com.vibe.store.application.sales

import com.vibe.store.api.*
import com.vibe.store.application.security.IdentityAuthority
import com.vibe.store.application.security.SecurityAudit
import com.vibe.store.domain.SalePolicy
import com.vibe.store.domain.SourceMoneyParity
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/** All decisions are made under the existing identity gate and shared UoW. */
class SaleAuthority(private val identity: IdentityAuthority,
    private val clock: () -> Long = System::currentTimeMillis,
    private val probe: SaleProbe = SaleProbe {},
    private val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : SaleService {
    private val operations = AtomicInteger()
    val critical: Boolean get() = operations.get() != 0
    private fun stamp() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(clock()))
    private fun fail(code: SaleError): Nothing = throw SaleFailure(code)
    private inline fun <T> validate(block: () -> T): T = try { block() }
        catch (_: IllegalArgumentException) { fail(SaleError.INVALID_INPUT) }
    private suspend fun <T> mutation(block: suspend () -> T): T {
        // Accepted mutations outlive Activity cancellation, but not real process death.
        operations.incrementAndGet()
        return applicationScope.async { try { block() } finally { operations.decrementAndGet() } }.await()
    }
    override suspend fun currentCash() = identity.authorizedRead("CASH:READ") { actor -> cashOperations.current(actor) }
    override suspend fun cashHistory(offset: Int): List<CashView> {
        validate { require(offset >= 0) }
        return identity.authorizedRead("CASH:READ") { actor -> cashOperations.page(actor, offset) }
    }
    override suspend fun openCash(opening: Double): CashView {
        val amount = validate { SalePolicy.amount(opening) }
        return mutation { identity.authorizedWrite("CASH:CREATE") { actor ->
            cashOperations.current(actor)?.let { return@authorizedWrite it }
            val now = stamp()
            val value = CashView(cashOperations.nextId(), "CAISSE-${UUID.randomUUID()}", actor, "OPEN", amount, amount, now)
            cashOperations.insert(value)
            security.appendAudit(SecurityAudit(actor, actor, "cash_session_opened", "cash_session", value.id.toString(), true, now))
            value
        } }
    }
    override suspend fun closeCash(cashId: Long, counted: Double): CashView {
        val amount = validate { require(cashId > 0); SalePolicy.amount(counted) }
        return mutation { identity.authorizedWrite("CASH:VALIDATE") { actor ->
            val cash = cashOperations.current(actor)?.takeIf { it.id == cashId } ?: fail(SaleError.CASH_REQUIRED)
            val now = stamp()
            val closed = cash.copy(status = "CLOSED", closedAt = now, counted = amount,
                difference = SourceMoneyParity.roundTwo(amount - cash.expected), closedBy = actor)
            security.appendAudit(SecurityAudit(actor, actor, "cash_session_closed", "cash_session", cash.id.toString(), true, now))
            cashOperations.close(closed)
            closed
        } }
    }
    override suspend fun sell(command: SaleCommand): Receipt {
        val canonical = validate {
            SalePolicy.key(command.key); require(command.cashId > 0)
            SalePolicy.canonical(command.lines.map { it.productId to it.quantity }, command.discount, command.received)
        }
        return mutation {
            val submitted = identity.authorizedWrite("POS:VALIDATE") { actor ->
                val cash = cashOperations.current(actor)?.takeIf { it.id == command.cashId } ?: fail(SaleError.CASH_REQUIRED)
                val existing = saleOperations.submitted(command.key)
                if (existing != null) {
                    if (existing.state == "DISCARDED" || existing.actorId != actor || existing.cashId != cash.id || existing.version != SalePolicy.VERSION ||
                        existing.canonical != canonical) fail(SaleError.CONFLICT)
                    existing
                } else SubmittedSale(command.key, actor, cash.id, SalePolicy.VERSION, canonical, "SUBMITTED", stamp())
                    .also { saleOperations.submit(it) }
            }
            probe.reached(SaleBoundary.JOURNALED)
            execute(submitted)
        }
    }
    private suspend fun execute(submitted: SubmittedSale): Receipt {
        val intent = validate { require(submitted.version == SalePolicy.VERSION); SalePolicy.decode(submitted.canonical) }
        val result = identity.authorizedWrite("POS:VALIDATE") { actor ->
            val cash = cashOperations.current(actor) ?: fail(SaleError.CASH_REQUIRED)
            if (actor != submitted.actorId || cash.id != submitted.cashId) fail(SaleError.CONFLICT)
            if (saleOperations.submitted(submitted.key)?.state == "DISCARDED") fail(SaleError.CONFLICT)
            val existing = saleOperations.byKey(submitted.key)
            if (existing != null) {
                if (existing.receipt.actorId != actor || existing.receipt.cashId != cash.id ||
                    existing.receipt.status != "validated" || existing.version != SalePolicy.VERSION ||
                    existing.canonical != submitted.canonical) fail(SaleError.CONFLICT)
                return@authorizedWrite existing.receipt
            }
            val quantities = mutableMapOf<Long, Long>()
            intent.lines.forEach { (id, quantity) ->
                val old = quantities[id] ?: 0L
                if (quantity > SalePolicy.MAX_QUANTITY - old) fail(SaleError.INVALID_INPUT)
                quantities[id] = old + quantity
            }
            val products = quantities.mapValues { (id, qty) ->
                (catalog.find(id)?.takeIf { it.deletedAt == null && it.stock >= qty }
                    ?: fail(SaleError.INSUFFICIENT_STOCK))
            }
            val totals = validate { SalePolicy.totals(intent.lines.map { products.getValue(it.first).price to it.second },
                intent.discount, intent.received, settings.value("discountsEnabled") != "false") }
            val now = stamp()
            val lines = intent.lines.map { (id, qty) ->
                val product = products.getValue(id)
                ReceiptLine(id, product.name, product.category, qty, product.price, product.price * qty,
                    saleOperations.cost(id) ?: product.price)
            }
            val receipt = Receipt("VENTE-${UUID.randomUUID()}", actor, cash.id, now, "validated",
                totals.subtotal, totals.discount, totals.total, totals.received, totals.change, "CAPTURED",
                settings.value("storeName") ?: "STORE", settings.value("address") ?: "", settings.value("phone") ?: "",
                settings.value("email") ?: "", settings.value("currency") ?: "EUR", lines)
            saleOperations.insert(StoredSale(receipt, submitted.key, SalePolicy.VERSION, submitted.canonical))
            probe.reached(SaleBoundary.INVOICE)
            for (line in lines) {
                saleOperations.insertLine(receipt.id, line)
                probe.reached(SaleBoundary.LINE)
            }
            for ((id, qty) in quantities) {
                val product = products.getValue(id)
                catalog.write(product.copy(stock = product.stock - qty, updatedAt = now))
                probe.reached(SaleBoundary.STOCK)
            }
            for (line in lines) {
                catalog.movement(line.productId!!, -line.quantity, "sale", line.unitPrice, now, receipt.id)
                probe.reached(SaleBoundary.MOVEMENT)
            }
            saleOperations.capture(receipt)
            probe.reached(SaleBoundary.PAYMENT)
            probe.reached(SaleBoundary.BEFORE_COMMIT)
            receipt
        }
        probe.reached(SaleBoundary.AFTER_COMMIT)
        return result
    }
    override suspend fun pending() = identity.authorizedRead("POS:VALIDATE") { actor ->
        saleOperations.pending(actor).map { PendingSale(it.key, it.cashId, it.createdAt) }
    }
    override suspend fun resolve(key: String): Receipt {
        validate { SalePolicy.key(key) }
        return mutation {
            val submitted = identity.authorizedRead("POS:VALIDATE") { actor ->
                saleOperations.submitted(key)?.takeIf { it.actorId == actor } ?: fail(SaleError.NOT_FOUND)
            }
            execute(submitted)
        }
    }
    override suspend fun acknowledge(key: String) {
        validate { SalePolicy.key(key) }
        identity.authorizedWrite("POS:VALIDATE") { actor ->
            val pending = saleOperations.submitted(key)?.takeIf { it.actorId == actor } ?: fail(SaleError.NOT_FOUND)
            val sale = saleOperations.byKey(key)
            if (sale == null || sale.receipt.actorId != actor || sale.canonical != pending.canonical) fail(SaleError.CONFLICT)
            saleOperations.acknowledge(key)
        }
    }
    override suspend fun abandon(key: String) {
        validate { SalePolicy.key(key) }
        mutation { identity.authorizedWrite("POS:VALIDATE") { actor ->
            val pending = saleOperations.submitted(key)
            if (pending != null && pending.actorId != actor) fail(SaleError.CONFLICT)
            if (saleOperations.byKey(key) != null) fail(SaleError.CONFLICT)
            if (pending != null) saleOperations.abandon(key)
        } }
    }
    override suspend fun history(filter: SaleFilter): SalePage {
        validate {
            require(filter.limit in 1..100 && filter.offset >= 0 && filter.search.length <= 120 && filter.category.length <= 120)
            require(filter.productId?.let { it > 0 } != false)
            require(listOf(filter.from, filter.to).all { it.isEmpty() || it.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) })
            require(filter.from.isEmpty() || filter.to.isEmpty() || filter.from <= filter.to)
        }
        return identity.authorizedRead("POS:READ") {
            val rows = saleOperations.page(filter.copy(limit = filter.limit + 1))
            SalePage(rows.take(filter.limit).map { it.receipt }, rows.size > filter.limit)
        }
    }
    override suspend fun receipt(id: String) = identity.authorizedRead("POS:READ") {
        saleOperations.find(id)?.receipt ?: fail(SaleError.NOT_FOUND)
    }
    override suspend fun cancel(id: String, reason: String): Receipt {
        val why = validate { reason.trim().also { require(it.length in 3..1000) } }
        return mutation { identity.authorizedWrite("POS:DELETE") { actor ->
            val old = saleOperations.find(id)?.receipt ?: fail(SaleError.NOT_FOUND)
            if (old.status == "cancelled") return@authorizedWrite old
            if (old.status != "validated") fail(SaleError.CONFLICT)
            val now = stamp()
            for (line in old.lines) {
                val product = line.productId?.let { catalog.find(it) } ?: continue
                if (line.quantity > SalePolicy.MAX_QUANTITY - product.stock) fail(SaleError.CONFLICT)
                catalog.write(product.copy(stock = product.stock + line.quantity, updatedAt = now))
                probe.reached(SaleBoundary.STOCK)
                catalog.movement(product.id, line.quantity, "invoice_reversal", line.unitPrice, now, id)
                probe.reached(SaleBoundary.MOVEMENT)
            }
            val cancelled = old.copy(status = "cancelled", paymentStatus = "REFUNDED", cancelledBy = actor,
                cancelledAt = now, cancellationReason = why)
            saleOperations.reverse(cancelled)
            security.appendAudit(SecurityAudit(actor, actor, "invoice_cancelled", "invoice", id, true, now))
            probe.reached(SaleBoundary.BEFORE_COMMIT)
            cancelled
        } }
    }
}
