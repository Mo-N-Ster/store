package com.vibe.store

import java.math.BigDecimal
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.vibe.store.api.*
import com.vibe.store.presentation.CatalogScreen
import com.vibe.store.presentation.CatalogScreenState
import com.vibe.store.presentation.SpatialTheme
import org.junit.Rule
import org.junit.Test

/** Only synthetic, non-persistent fixtures. Never alters a real catalog. */
class CatalogPresentationUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private class Service : CatalogService {
        @Volatile var listed = false
        @Volatile var pricesQueried = false
        @Volatile var movementsQueried = false
        private val products = listOf(
            ProductView(1, "Synthetic rice", "Food", "rice", "Test article", BigDecimal("12.34"), 10, 2,
                false, false, "2026-10-01T09:00:00.000Z"),
            ProductView(2, "Synthetic pasta", "Food", "pasta", "", BigDecimal("4.50"), 1, 2,
                false, false, "2026-10-01T09:00:00.000Z"),
            ProductView(3, "Synthetic oil", "Food", "oil", "", BigDecimal("9.90"), 0, 0,
                false, false, "2026-10-01T09:00:00.000Z"),
        )
        override suspend fun list(filter: CatalogFilter): CatalogPage {
            listed = true
            return CatalogPage(products.filter { filter.search.isBlank() ||
                it.name.contains(filter.search, ignoreCase = true) }, false)
        }
        override suspend fun detail(id: Long) = products.single { it.id == id }
        override suspend fun save(draft: ProductDraft, image: ImageEdit): CatalogWrite =
            error("Synthetic read-only test")
        override suspend fun archive(id: Long, expectedUpdatedAt: String): ProductView =
            error("Synthetic read-only test")
        override suspend fun adjustStock(id: Long, target: Double, reason: String,
            expectedStock: Long): ProductView = error("Synthetic read-only test")
        override suspend fun movements(filter: MovementFilter): List<MovementView> {
            movementsQueried = true
            return listOf(MovementView(4, 1, "Synthetic rice", "Food", -2, "sale", BigDecimal("12.34"),
                "2026-10-01T09:00:00.000Z", "SYNTHETIC-4"))
        }
        override suspend fun prices(id: Long): List<PriceView> {
            pricesQueried = true
            return listOf(PriceView(5, BigDecimal("12.34"), "2026-10-01T09:00:00.000Z"))
        }
        override suspend fun image(id: Long): ProductImage? = null
        override suspend fun deleteHistory(type: String?, ids: List<Long>): Int =
            error("Synthetic read-only test")
    }

    @Test fun cardsShowStockStatesAndStructuredAmounts() {
        val service = Service()
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            CatalogScreen(service, setOf("PRODUCTS:READ", "STOCKS:READ"), true,
                CatalogScreenState(), { it(null) }) { }
        } } }
        compose.waitUntil(10_000) { service.listed }
        compose.onNodeWithTag("catalog-list")
            .performScrollToNode(hasTestTag("product-1"))
        compose.onNodeWithTag("product-stock-status-1", useUnmergedTree = true).assertTextEquals("Disponible")
        compose.onNodeWithTag("catalog-list")
            .performScrollToNode(hasTestTag("product-2"))
        compose.onNodeWithTag("product-stock-status-2", useUnmergedTree = true).assertTextEquals("Stock faible")
        compose.onNodeWithTag("catalog-list")
            .performScrollToNode(hasTestTag("product-3"))
        compose.onNodeWithTag("product-stock-status-3", useUnmergedTree = true).assertTextEquals("Rupture de stock")
        compose.onNodeWithTag("product-create").assertDoesNotExist()
    }

    @Test fun detailAndHistoriesKeepReadableEntriesAndReadPermissions() {
        val service = Service()
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            CatalogScreen(service, setOf("PRODUCTS:READ", "STOCKS:READ"), false,
                CatalogScreenState(), { it(null) }) { }
        } } }
        compose.waitUntil(10_000) { service.listed }
        compose.onNodeWithTag("catalog-list")
            .performScrollToNode(hasTestTag("product-1"))
        compose.onNodeWithTag("product-1").performClick()
        compose.onNodeWithText("Edit").assertDoesNotExist()
        compose.onNodeWithText("Adjust stock").assertDoesNotExist()
        compose.onNodeWithText("Price history").performScrollTo().performClick()
        compose.waitUntil(10_000) { service.pricesQueried }
        compose.onNodeWithTag("catalog-list")
            .performScrollToNode(hasTestTag("catalog-price-history-5"))
        compose.onNodeWithTag("catalog-price-history-5").assertExists()
        compose.onNodeWithText("Stock movements").performScrollTo().performClick()
        compose.waitUntil(10_000) { service.movementsQueried }
        compose.onNodeWithTag("catalog-list")
            .performScrollToNode(hasTestTag("catalog-movement-4"))
        compose.onNodeWithTag("catalog-movement-4").assertExists()
        compose.onNodeWithText("Sale").assertExists()
    }
}
