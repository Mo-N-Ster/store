package com.vibe.store.domain

import kotlin.math.abs
import kotlin.math.floor

object CatalogPolicy {
    fun quantity(value: Double): Long {
        require(value.isFinite() && value >= 0 && value <= 9_007_199_254_740_991.0 && value == floor(value))
        return value.toLong()
    }
    fun price(value: Double): Double {
        require(value.isFinite() && value >= 0 && abs(value * 100 - floor(value * 100 + 0.5)) <= 1e-8)
        return value // validation only, no new rounding
    }
    /** Source SQLite lower() folds ASCII; do not silently change accent semantics. */
    fun duplicateKey(value: String) = value.trim().map { if (it in 'A'..'Z') it + 32 else it }.joinToString("")
    fun adjustmentReason(value: String): String = value.trim().also { require(it.length >= 3) }
    fun historyType(value: Any?): String { require(value is String && value == "purchases"); return value }
}
enum class MediaPolicy(val maximumBytes: Int) { PRODUCT(5 * 1024 * 1024), PROFILE(512 * 1024) }
enum class ImageKind(val extension: String, val mimeType: String) { JPEG("jpg", "image/jpeg"), PNG("png", "image/png"), WEBP("webp", "image/webp") }
object MediaSignature {
    fun detect(bytes: ByteArray): ImageKind? {
        fun starts(vararg prefix: Int) = bytes.size >= prefix.size && prefix.indices.all { (bytes[it].toInt() and 255) == prefix[it] }
        return when {
            starts(255, 216, 255) -> ImageKind.JPEG
            starts(137, 80, 78, 71, 13, 10, 26, 10) -> ImageKind.PNG
            bytes.size >= 12 && bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) && bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray()) -> ImageKind.WEBP
            else -> null
        }
    }
    fun validReference(value: String) = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(jpg|png|webp)").matches(value)
}
data class CatalogRecord(val id: Long, val name: String, val category: String, val hashtag: String,
    val description: String, val price: Double, val stock: Long, val minimumStock: Long,
    val createdAt: String, val updatedAt: String, val deletedAt: String?, val imageRef: String?)
data class StockTrace(val id: Long, val productId: Long, val quantity: Long, val reason: String,
    val unitPrice: Double, val createdAt: String, val reference: String?)
data class PriceTrace(val id: Long, val productId: Long, val price: Double, val recordedAt: String)
