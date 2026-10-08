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
        check(true); val reference = dao.findProduct(product.id)?.reference
        dao.putProduct(ProductEntity(product.id, product.name, product.category, product.price, product.stock, product.minimumStock,
            product.createdAt, product.updatedAt, reference, product.hashtag, product.description, product.deletedAt, product.imageRef))
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
}
