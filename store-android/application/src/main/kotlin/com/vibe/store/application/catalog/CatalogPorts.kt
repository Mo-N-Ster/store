package com.vibe.store.application.catalog

import java.math.BigDecimal
import com.vibe.store.api.*
import com.vibe.store.domain.*

interface CatalogRecords {
    suspend fun find(id: Long): CatalogRecord?
    suspend fun page(filter: CatalogFilter): List<CatalogRecord>
    suspend fun duplicate(name: String, hashtag: String, exceptId: Long?): Boolean
    suspend fun nextProductId(): Long
    suspend fun write(product: CatalogRecord)
    suspend fun movement(productId: Long, delta: Long, reason: String, price: BigDecimal, stamp: String, reference: String?)
    suspend fun price(productId: Long, price: BigDecimal, stamp: String)
    suspend fun movements(filter: MovementFilter): List<Pair<StockTrace, CatalogRecord>>
    suspend fun prices(productId: Long): List<PriceTrace>
    suspend fun deleteMovements(ids: List<Long>): Int
    suspend fun imageReferences(): Set<String>
}
class PreparedMedia(val reference: String)
data class MediaInspection(val missing: Int, val orphaned: Int)
/** Trusted filesystem adapter; no paths/references accepted through public UI API. */
interface ProductMedia {
    suspend fun prepare(selection: SelectedImage?, policy: MediaPolicy = MediaPolicy.PRODUCT): PreparedMedia?
    suspend fun read(reference: String): ProductImage
    suspend fun removeUnreferenced(reference: String, references: Set<String>)
    suspend fun inspect(references: Set<String>): MediaInspection
}
enum class CatalogBoundary { PREPARED, BEFORE_REFERENCE, AFTER_REFERENCE }
fun interface CatalogProbe { suspend fun reached(boundary: CatalogBoundary) }
