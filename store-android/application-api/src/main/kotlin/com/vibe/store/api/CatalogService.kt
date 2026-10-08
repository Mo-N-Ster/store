package com.vibe.store.api

import java.math.BigDecimal

/** Opaque picker input. Never a filesystem path or an authoritative media reference. */
interface SelectedImage
sealed interface ImageEdit {
    data object Keep : ImageEdit
    data object Remove : ImageEdit
    class Replace(val selection: SelectedImage) : ImageEdit
}
data class ProductView(val id: Long, val name: String, val category: String, val hashtag: String,
    val description: String, val price: BigDecimal, val stock: Long, val minimumStock: Long,
    val archived: Boolean, val hasImage: Boolean, val updatedAt: String)
data class ProductDraft(val id: Long? = null, val name: String, val category: String,
    val hashtag: String = "", val description: String = "", val price: BigDecimal,
    val initialStock: Double = 0.0, val minimumStock: Double = 0.0,
    val expectedUpdatedAt: String? = null)
data class CatalogFilter(val search: String = "", val category: String = "", val archived: Boolean = false,
    val stock: String = "all", val offset: Int = 0, val limit: Int = 50)
data class CatalogPage(val items: List<ProductView>, val hasMore: Boolean)
data class MovementView(val id: Long, val productId: Long, val productName: String, val category: String,
    val quantity: Long, val reason: String, val unitPrice: BigDecimal, val createdAt: String, val reference: String?)
data class MovementFilter(val from: String = "", val to: String = "", val productId: Long? = null,
    val category: String = "", val type: String = "", val offset: Int = 0, val limit: Int = 50)
data class PriceView(val id: Long, val price: BigDecimal, val recordedAt: String)
class ProductImage(val mimeType: String, val bytes: ByteArray)
data class CatalogWrite(val product: ProductView, val cleanupDeferred: Boolean = false)
enum class CatalogError { INVALID_INPUT, NOT_FOUND, DUPLICATE, CONFLICT, INVALID_MEDIA, MEDIA_TOO_LARGE, MEDIA_UNAVAILABLE, STORAGE_UNAVAILABLE, RECOVERY_REQUIRED }
class CatalogFailure(val code: CatalogError) : Exception(code.name)
interface CatalogService {
    suspend fun list(filter: CatalogFilter = CatalogFilter()): CatalogPage
    suspend fun detail(id: Long): ProductView
    suspend fun save(draft: ProductDraft, image: ImageEdit = ImageEdit.Keep): CatalogWrite
    suspend fun archive(id: Long, expectedUpdatedAt: String): ProductView
    suspend fun adjustStock(id: Long, target: Double, reason: String, expectedStock: Long): ProductView
    suspend fun movements(filter: MovementFilter = MovementFilter()): List<MovementView>
    suspend fun prices(id: Long): List<PriceView>
    suspend fun image(id: Long): ProductImage?
    suspend fun deleteHistory(type: String?, ids: List<Long>): Int
}
