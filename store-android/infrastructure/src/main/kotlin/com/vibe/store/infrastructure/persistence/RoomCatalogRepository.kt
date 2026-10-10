package com.vibe.store.infrastructure.persistence

import java.math.BigDecimal
import com.vibe.store.api.*
import com.vibe.store.application.persistence.CatalogRepository
import com.vibe.store.domain.*

internal class RoomCatalogRepository(private val dao: StoreDao, private val check: suspend (Boolean) -> Unit) : CatalogRepository {
    private fun ProductEntity.record() = CatalogRecord(id, name, category, hashtag ?: "", description, price, stock, minimumStock, createdAt, updatedAt, deletedAt, imageRef)
    override suspend fun product(id: Long): ProductRecord? { check(false); return dao.findProduct(id)?.let { ProductRecord(it.id, it.name, MoneyValue(it.price), Quantity(it.stock)) } }
    override suspend fun find(id: Long): CatalogRecord? { check(false); return dao.findProduct(id)?.record() }
    override suspend fun page(filter: CatalogFilter): List<CatalogRecord> {
        check(false); return dao.catalogPage(filter.search, filter.category, filter.archived, filter.stock, filter.limit, filter.offset).map { it.record() }
    }
    override suspend fun duplicate(name: String, hashtag: String, exceptId: Long?): Boolean { check(false); return dao.duplicateProduct(name, hashtag, exceptId) }
    override suspend fun nextProductId(): Long { check(true); return dao.nextProductId() }
    override suspend fun write(product: CatalogRecord) {
        check(true)
        val previous = dao.findProduct(product.id)
        val reference = previous?.reference
        dao.putProduct(ProductEntity(product.id, product.name, product.category, product.price, product.stock, product.minimumStock,
            product.createdAt, product.updatedAt, reference, product.hashtag, product.description, product.deletedAt, product.imageRef))
        // I08 durable low-stock alert in the SAME transaction as stock mutation.
        // Repeated writes while still low never create duplicate alerts.
        val low = product.deletedAt == null && product.stock <= product.minimumStock
        val wasLow = previous?.let { it.deletedAt == null && it.stock <= it.minimumStock } ?: false
        if (low && !wasLow) {
            dao.notification(NotificationEntity(dao.nextNotificationId(), "LOW_STOCK", product.id,
                "${product.name.take(120)} : stock ${product.stock} / seuil ${product.minimumStock}",
                false, product.updatedAt))
        } else if (!low && wasLow) {
            dao.resolveStockAlert(product.id, product.updatedAt)
        }
    }
    override suspend fun movement(productId: Long, delta: Long, reason: String, price: BigDecimal, stamp: String, reference: String?) {
        check(true); dao.movement(MovementEntity(dao.nextMovementId(), productId, delta, reason, price, stamp, reference))
    }
    override suspend fun price(productId: Long, price: BigDecimal, stamp: String) { check(true); dao.price(PriceEntity(dao.nextPriceId(), productId, price, stamp)) }
    override suspend fun movements(filter: MovementFilter): List<Pair<StockTrace, CatalogRecord>> {
        check(false); return dao.movementPage(filter.from, filter.to, filter.productId, filter.category, filter.type, filter.limit, filter.offset).map {
            StockTrace(it.id, it.productId, it.quantity, it.reason, it.unitPrice, it.createdAt, it.referenceId) to checkNotNull(dao.findProduct(it.productId)).record()
        }
    }
    override suspend fun prices(productId: Long): List<PriceTrace> { check(false); return dao.productPrices(productId).map { PriceTrace(it.id, it.productId, it.price, it.recordedAt) } }
    override suspend fun deleteMovements(ids: List<Long>): Int { check(true); return dao.deleteMovements(ids) }
    override suspend fun imageReferences(): Set<String> { check(false); return dao.productImageReferences().toSet() }

    override suspend fun matchingDuplicateKeys(names: Set<String>, hashtags: Set<String>): com.vibe.store.application.catalog.CatalogDuplicateKeys {
        val matchedNames = LinkedHashSet<String>()
        val matchedHashtags = LinkedHashSet<String>()
        names.toList().chunked(400).forEach { keys -> check(false); matchedNames += dao.duplicateProductNames(keys) }
        hashtags.toList().chunked(400).forEach { keys -> check(false); matchedHashtags += dao.duplicateProductHashtags(keys) }
        return com.vibe.store.application.catalog.CatalogDuplicateKeys(matchedNames, matchedHashtags)
    }

    /** Batch insertion is only called inside the authorized owner transaction. */
    override suspend fun insertImported(products: List<CatalogRecord>) {
        check(true)
        require(products.size <= 10_000 && products.all { it.id > 0 && it.deletedAt == null && it.imageRef == null })
        if (products.isEmpty()) return
        val productRows = products.map { product ->
            ProductEntity(product.id, product.name, product.category, product.price, product.stock, product.minimumStock,
                product.createdAt, product.updatedAt, null, product.hashtag, product.description, null, null)
        }
        check(true)
        dao.importedProducts(productRows)

        val withInitialStock = products.filter { it.stock > 0 }
        if (withInitialStock.isNotEmpty()) {
            check(true)
            val firstMovementId = dao.nextMovementId()
            require(firstMovementId > 0 && withInitialStock.size.toLong() <= Long.MAX_VALUE - firstMovementId)
            check(true)
            dao.importedMovements(withInitialStock.mapIndexed { index, product ->
                MovementEntity(firstMovementId + index, product.id, product.stock, "initial", product.price, product.createdAt, null)
            })
        }

        check(true)
        val firstPriceId = dao.nextPriceId()
        require(firstPriceId > 0 && products.size.toLong() <= Long.MAX_VALUE - firstPriceId)
        check(true)
        dao.importedPrices(products.mapIndexed { index, product ->
            PriceEntity(firstPriceId + index, product.id, product.price, product.createdAt)
        })

        val lowStock = products.filter { it.stock <= it.minimumStock }
        if (lowStock.isNotEmpty()) {
            check(true)
            val firstNotificationId = dao.nextNotificationId()
            require(firstNotificationId > 0 && lowStock.size.toLong() <= Long.MAX_VALUE - firstNotificationId)
            check(true)
            dao.importedNotifications(lowStock.mapIndexed { index, product ->
                NotificationEntity(firstNotificationId + index, "LOW_STOCK", product.id,
                    "${product.name.take(120)} : stock ${product.stock} / seuil ${product.minimumStock}", false, product.createdAt)
            })
        }
    }
}
