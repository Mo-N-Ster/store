package com.vibe.store.testing

import com.vibe.store.api.CatalogImportProduct
import com.vibe.store.api.CatalogImportRequest
import com.vibe.store.domain.CatalogImportPolicy
import com.vibe.store.domain.StructuredCatalogProduct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class CatalogImportPolicyTest {
    private fun item(
        name: String = "Item",
        hashtag: String = "tag",
        category: String = "Food",
        price: String = "12.345678900",
        stock: Long = 2,
        minimum: Long = 1,
    ) = StructuredCatalogProduct(name, hashtag, category, "Synthetic", BigDecimal(price), stock, minimum)

    @Test fun trimsCurrentCatalogFieldsAndPreservesExactDecimalValue() {
        val result = CatalogImportPolicy.validate(listOf(item(name = "  Item  ", category = " Food ")))
        assertTrue(result.issues.isEmpty())
        assertEquals("Item", result.products.single().name)
        assertEquals("Food", result.products.single().category)
        assertEquals(0, result.products.single().price.compareTo(BigDecimal("12.3456789")))
        assertEquals("12.3456789", result.products.single().price.toPlainString())
    }

    @Test fun duplicateNameAndHashtagWithinFileAreRejectedDeterministically() {
        val result = CatalogImportPolicy.validate(listOf(item(), item(name = " item ", hashtag = "other"), item(name = "third", hashtag = "TAG")))
        assertEquals(listOf("duplicate_in_file", "duplicate_in_file"), result.issues.map { it.code })
        assertEquals(listOf(2, 3), result.issues.map { it.row })
    }

    @Test fun invalidPricesAndQuantitiesAreRejectedWithoutRounding() {
        val result = CatalogImportPolicy.validate(listOf(item(price = "-0.0000001", stock = -1), item(minimum = Long.MAX_VALUE)))
        assertTrue(result.issues.any { it.field == "price" && it.code == "invalid_price" })
        assertTrue(result.issues.any { it.field == "stockQuantity" && it.code == "invalid_quantity" })
        assertTrue(result.issues.any { it.field == "minStockThreshold" && it.code == "invalid_quantity" })
    }

    @Test fun requiredNameAndCategoryUseExistingCatalogRule() {
        val result = CatalogImportPolicy.validate(listOf(item(name = "  ", category = "\t")))
        assertEquals(setOf("name", "category"), result.issues.map { it.field }.toSet())
    }

    @Test fun apiRequestRejectsOversizedRowsAndFieldsBeforeRetainingInput() {
        val valid = CatalogImportProduct("n", "", "c", "", BigDecimal.ONE, 0, 0)
        try {
            CatalogImportRequest(List(10_001) { valid })
            throw AssertionError("Expected row bound rejection")
        } catch (_: IllegalArgumentException) { }
        try {
            CatalogImportRequest(listOf(valid.copy(name = "n".repeat(64_001))))
            throw AssertionError("Expected field bound rejection")
        } catch (_: IllegalArgumentException) { }
        val mutable = mutableListOf(valid)
        val request = CatalogImportRequest(mutable)
        mutable.clear()
        assertEquals(1, request.products.size)
    }
}
