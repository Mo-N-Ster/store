package com.vibe.store.application.catalog

import com.vibe.store.api.*
import com.vibe.store.application.security.IdentityAuthority
import com.vibe.store.application.security.SecurityAudit
import com.vibe.store.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.*

class CatalogAuthority(private val identity: IdentityAuthority, private val media: ProductMedia,
    private val clock: () -> Long = System::currentTimeMillis,
    private val probe: CatalogProbe = CatalogProbe {}) : CatalogService {
    private val mediaGate = Mutex()
    private fun fail(code: CatalogError): Nothing = throw CatalogFailure(code)
    private fun valid(condition: Boolean) { if (!condition) fail(CatalogError.INVALID_INPUT) }
    private fun stamp() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(clock()))
    private fun CatalogRecord.view() = ProductView(id, name, category, hashtag, description, price, stock, minimumStock, deletedAt != null, imageRef != null, updatedAt)
    private inline fun <T> validated(block: () -> T): T = try { block() } catch (_: IllegalArgumentException) { fail(CatalogError.INVALID_INPUT) }
    private fun bounds(offset: Int, limit: Int) = valid(offset >= 0 && limit in 1..200)
    override suspend fun list(filter: CatalogFilter): CatalogPage {
        bounds(filter.offset, filter.limit); valid(filter.stock in setOf("all", "low", "out"))
        return identity.authorizedRead("PRODUCTS:READ") {
            val items = catalog.page(filter.copy(limit = filter.limit + 1))
            CatalogPage(items.take(filter.limit).map { it.view() }, items.size > filter.limit)
        }
    }
    override suspend fun detail(id: Long): ProductView {
        valid(id > 0); return identity.authorizedRead("PRODUCTS:READ") { (catalog.find(id) ?: fail(CatalogError.NOT_FOUND)).view() }
    }
    override suspend fun save(draft: ProductDraft, image: ImageEdit): CatalogWrite = mediaGate.withLock {
        valid(draft.name.trim().isNotEmpty() && draft.category.trim().isNotEmpty() && (draft.id?.let { it > 0 } != false))
        val price = validated { ExactMoney.normalize(CatalogPolicy.price(draft.price)) }
        val initial = validated { CatalogPolicy.quantity(draft.initialStock) }
        val threshold = validated { CatalogPolicy.quantity(draft.minimumStock) }
        // Source policy is PRODUCTS:UPDATE for both create and edit.
        identity.authorizedRead("PRODUCTS:UPDATE") { Unit }
        val prepared = if (image is ImageEdit.Replace) media.prepare(image.selection) else null
        if (prepared != null) probe.reached(CatalogBoundary.PREPARED)
        var obsolete: String? = null
        // Failure before commit leaves at worst an unreferenced durable copy.
        // No compensating deletion can race an ambiguous transaction result.
        val result = identity.authorizedWrite("PRODUCTS:UPDATE") {
            val old = draft.id?.let { catalog.find(it) ?: fail(CatalogError.NOT_FOUND) }
            if (old?.deletedAt != null) fail(CatalogError.NOT_FOUND)
            if (old != null && draft.expectedUpdatedAt != old.updatedAt) fail(CatalogError.CONFLICT)
            if (catalog.duplicate(draft.name.trim(), draft.hashtag.trim(), draft.id)) fail(CatalogError.DUPLICATE)
            prepared?.let { media.read(it.reference) } // verified durable object exists before reference
            probe.reached(CatalogBoundary.BEFORE_REFERENCE)
            val now = stamp()
            val record = CatalogRecord(old?.id ?: catalog.nextProductId(), draft.name.trim(), draft.category.trim(), draft.hashtag.trim(),
                draft.description, price, old?.stock ?: initial, threshold, old?.createdAt ?: now, now, null,
                prepared?.reference ?: if (image is ImageEdit.Remove) null else old?.imageRef)
            catalog.write(record)
            if (old == null && initial > 0) catalog.movement(record.id, initial, "initial", price, now, null)
            if (old == null || !ExactMoney.same(old.price, price)) catalog.price(record.id, price, now)
            obsolete = old?.imageRef?.takeIf { it != record.imageRef }
            record.view()
        }
        probe.reached(CatalogBoundary.AFTER_REFERENCE)
        var deferred = false
        obsolete?.let { ref ->
            try {
                // Hold the accepted owner read/maintenance barrier during both
                // reference inspection and deletion, including archived products.
                identity.authorizedRead("PRODUCTS:UPDATE") { media.removeUnreferenced(ref, catalog.imageReferences()) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: CatalogFailure) { deferred = true }
            catch (_: SecurityFailure) { deferred = true }
        }
        CatalogWrite(result, deferred)
    }
    override suspend fun archive(id: Long, expectedUpdatedAt: String): ProductView {
        valid(id > 0)
        return identity.authorizedWrite("PRODUCTS:DELETE") { actor ->
            val old = catalog.find(id) ?: fail(CatalogError.NOT_FOUND)
            if (old.deletedAt != null) return@authorizedWrite old.view()
            if (old.updatedAt != expectedUpdatedAt) fail(CatalogError.CONFLICT)
            val now = stamp()
            if (old.stock > 0) catalog.movement(id, -old.stock, "product_deletion", old.price, now, null)
            val archived = old.copy(stock = 0, deletedAt = now, updatedAt = now)
            catalog.write(archived)
            security.appendAudit(SecurityAudit(actor, actor, "delete", "product", id.toString(), true, now))
            archived.view()
        }
    }
    override suspend fun adjustStock(id: Long, target: Double, reason: String, expectedStock: Long): ProductView {
        valid(id > 0)
        val quantity = validated { CatalogPolicy.quantity(target) }; val why = validated { CatalogPolicy.adjustmentReason(reason) }
        return identity.authorizedWrite("STOCKS:UPDATE") { actor ->
            val old = catalog.find(id)?.takeIf { it.deletedAt == null } ?: fail(CatalogError.NOT_FOUND)
            if (old.stock != expectedStock) fail(CatalogError.CONFLICT)
            if (quantity == old.stock) return@authorizedWrite old.view()
            val now = stamp(); val changed = old.copy(stock = quantity, updatedAt = now)
            catalog.write(changed); catalog.movement(id, quantity - old.stock, "adjustment:$why", old.price, now, "ADJ-${clock()}")
            security.appendAudit(SecurityAudit(actor, actor, "stock_adjusted", "product", id.toString(), true, now))
            changed.view()
        }
    }
    override suspend fun movements(filter: MovementFilter): List<MovementView> {
        bounds(filter.offset, filter.limit); valid(filter.productId?.let { it > 0 } != false)
        valid(filter.type in setOf("", "initial", "adjustment", "sale", "invoice_reversal", "purchase", "purchase_cancellation", "inventory", "product_deletion"))
        fun date(value: String): Boolean = value.isEmpty() || Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(value) && runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }.parse(value) != null
        }.getOrDefault(false)
        valid(date(filter.from) && date(filter.to) && (filter.from.isEmpty() || filter.to.isEmpty() || filter.from <= filter.to))
        return identity.authorizedRead("STOCKS:READ") { catalog.movements(filter).map { (m, p) -> MovementView(m.id, p.id, p.name, p.category, m.quantity, m.reason, m.unitPrice, m.createdAt, m.reference) } }
    }
    override suspend fun prices(id: Long): List<PriceView> {
        valid(id > 0); return identity.authorizedRead("PRODUCTS:READ") { catalog.prices(id).map { PriceView(it.id, it.price, it.recordedAt) } }
    }
    override suspend fun image(id: Long): ProductImage? = mediaGate.withLock {
        valid(id > 0); identity.authorizedRead("PRODUCTS:READ") { val product = catalog.find(id) ?: fail(CatalogError.NOT_FOUND); product.imageRef?.let { media.read(it) } }
    }
    override suspend fun deleteHistory(type: String?, ids: List<Long>): Int {
        validated { CatalogPolicy.historyType(type) }; valid(ids.all { it > 0 && it <= 9_007_199_254_740_991 })
        return identity.authorizedWrite("FINANCES:DELETE") { actor ->
            val distinct = ids.distinct(); val count = if (distinct.isEmpty()) 0 else catalog.deleteMovements(distinct)
            if (distinct.isNotEmpty()) security.appendAudit(SecurityAudit(actor, actor, "delete_history", "purchases", distinct.joinToString(","), true, stamp()))
            count
        }
    }
    /** Non-destructive inspection, reusable after restart; never orphan sweeping. */
    suspend fun inspectMedia(): MediaInspection = mediaGate.withLock {
        identity.authorizedRead("PRODUCTS:READ") { media.inspect(catalog.imageReferences()) }
    }
}
