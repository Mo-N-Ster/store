package com.vibe.store.domain

import java.math.BigDecimal
import java.util.Collections

data class CatalogImportPolicyIssue(val row: Int, val field: String, val code: String)
class CatalogImportPolicyResult(products: List<StructuredCatalogProduct>, issues: List<CatalogImportPolicyIssue>) {
    val products: List<StructuredCatalogProduct> = Collections.unmodifiableList(ArrayList(products))
    val issues: List<CatalogImportPolicyIssue> = Collections.unmodifiableList(ArrayList(issues))
}

/** Reuses current catalog rules; it does not create import-only price, stock, or duplicate semantics. */
object CatalogImportPolicy {
    private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L

    fun validate(source: List<StructuredCatalogProduct>): CatalogImportPolicyResult {
        if (source.size > StoreDataV1Bounds.MAX_PRODUCTS) {
            return CatalogImportPolicyResult(emptyList(), listOf(CatalogImportPolicyIssue(0, "products", "limit_exceeded")))
        }
        val issues = ArrayList<CatalogImportPolicyIssue>()
        val seenNames = HashSet<String>()
        val seenHashtags = HashSet<String>()
        val normalized = ArrayList<StructuredCatalogProduct>(source.size)
        source.forEachIndexed { index, input ->
            val row = index + 1
            val name = input.name.trim()
            val category = input.category.trim()
            val hashtag = input.hashtag.trim()
            if (name.isEmpty()) issues += CatalogImportPolicyIssue(row, "name", "required")
            if (category.isEmpty()) issues += CatalogImportPolicyIssue(row, "category", "required")
            val nameKey = if (name.isEmpty()) null else CatalogPolicy.duplicateKey(name)
            if (nameKey != null && !seenNames.add(nameKey)) issues += CatalogImportPolicyIssue(row, "name", "duplicate_in_file")
            val hashtagKey = if (hashtag.isEmpty()) null else CatalogPolicy.duplicateKey(hashtag)
            if (hashtagKey != null && !seenHashtags.add(hashtagKey)) issues += CatalogImportPolicyIssue(row, "hashtag", "duplicate_in_file")

            val price = try { ExactMoney.normalize(CatalogPolicy.price(input.price)) }
            catch (_: IllegalArgumentException) { issues += CatalogImportPolicyIssue(row, "price", "invalid_price"); input.price }
            if (plainLength(price) > StoreDataV1Bounds.MAX_NUMBER_CHARS) issues += CatalogImportPolicyIssue(row, "price", "limit_exceeded")
            if (input.stockQuantity !in 0..MAX_SAFE_INTEGER) issues += CatalogImportPolicyIssue(row, "stockQuantity", "invalid_quantity")
            if (input.minStockThreshold !in 0..MAX_SAFE_INTEGER) issues += CatalogImportPolicyIssue(row, "minStockThreshold", "invalid_quantity")
            normalized += StructuredCatalogProduct(name, hashtag, category, input.description, price, input.stockQuantity, input.minStockThreshold)
        }
        return CatalogImportPolicyResult(normalized, issues)
    }

    private fun plainLength(value: BigDecimal): Long {
        val precision = value.precision().toLong()
        val scale = value.scale().toLong()
        val chars = if (scale <= 0) precision - scale else maxOf(precision, scale + 1) + 1
        return chars + if (value.signum() < 0) 1 else 0
    }
}
