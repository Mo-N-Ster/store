package com.vibe.store

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vibe.store.api.*
import com.vibe.store.presentation.CatalogPdfSelection
import com.vibe.store.presentation.CatalogScreen
import com.vibe.store.presentation.CatalogScreenState
import com.vibe.store.presentation.SpatialTheme
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic presentation-boundary tests; authorization and persistence are tested by Room authority tests. */
@RunWith(AndroidJUnit4::class)
class CatalogImportWorkflowUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val catalog = object : CatalogService {
        override suspend fun list(filter: CatalogFilter) = CatalogPage(emptyList(), false)
        override suspend fun detail(id: Long): ProductView = error("Unexpected detail")
        override suspend fun save(draft: ProductDraft, image: ImageEdit) = error("Unexpected save")
        override suspend fun archive(id: Long, expectedUpdatedAt: String) = error("Unexpected archive")
        override suspend fun adjustStock(id: Long, target: Double, reason: String, expectedStock: Long) = error("Unexpected stock adjustment")
        override suspend fun movements(filter: MovementFilter) = emptyList<MovementView>()
        override suspend fun prices(id: Long) = emptyList<PriceView>()
        override suspend fun image(id: Long): ProductImage? = null
        override suspend fun deleteHistory(type: String?, ids: List<Long>): Int = error("Unexpected history delete")
    }

    private fun previewService(confirmFailure: SecurityFailure? = null): Pair<CatalogImportService, AtomicIntegerArray> {
        val counts = AtomicIntegerArray()
        val product = CatalogImportProduct("Synthetic Café", "#synthetic", "Food", "Fixture",
            BigDecimal("12.34001"), 3, 1)
        val service = object : CatalogImportService {
            override suspend fun preview(request: CatalogImportRequest) = error("PDF route expected")
            override suspend fun previewPdf(input: BoundedDocumentInput): CatalogImportPreview {
                counts.preview.incrementAndGet()
                return CatalogImportPreview("synthetic-preview", listOf(product), emptyList(), "XOF")
            }
            override suspend fun discard(previewId: String) { counts.discard.incrementAndGet() }
            override suspend fun confirm(previewId: String): CatalogImportResult {
                counts.confirm.incrementAndGet()
                confirmFailure?.let { throw it }
                return CatalogImportResult(1)
            }
        }
        return service to counts
    }

    @Test fun defaultImportActionRequiresProductUpdatePermission() {
        val (importer, _) = previewService()
        val picker: (Long, (CatalogPdfSelection) -> Unit) -> Unit = { _, callback ->
            callback(CatalogPdfSelection.Cancelled)
        }
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(true) {
            CatalogScreen(catalog, setOf("PRODUCTS:READ"), false,
                CatalogScreenState(), { it(null) }, catalogImport = importer,
                pickCatalogPdf = picker) { }
        } } }
        compose.onNodeWithTag("catalog-import-pdf").assertDoesNotExist()
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(true) {
            CatalogScreen(catalog, setOf("PRODUCTS:READ", "PRODUCTS:UPDATE"), false,
                CatalogScreenState(), { it(null) }, catalogImport = importer,
                pickCatalogPdf = picker) { }
        } } }
        compose.onNodeWithTag("catalog-import-pdf").assertExists()
    }

    @Test fun syntheticSelectionShowsExactPreviewAndCancelDiscardsWithoutConfirming() {
        val (importer, counts) = previewService()
        val pickCount = AtomicInteger()
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(true) {
            CatalogScreen(catalog, setOf("PRODUCTS:READ", "PRODUCTS:UPDATE"), false,
                CatalogScreenState(), { it(null) }, catalogImport = importer, actorId = 41,
                pickCatalogPdf = { actor, callback ->
                    assertEquals(41L, actor)
                    pickCount.incrementAndGet()
                    callback(CatalogPdfSelection.Ready(BoundedDocumentInput(byteArrayOf(1, 2, 3))))
                }, catalogPdfImportEnabled = true) { }
        } } }
        compose.onNodeWithTag("catalog-import-pdf").performScrollTo().performClick()
        compose.waitUntil(5_000) { counts.preview.get() == 1 }
        compose.onNodeWithTag("catalog-import-preview").assertIsDisplayed()
        compose.onNodeWithText("Unauthenticated data · 1 product(s) · catalog currency XOF").assertIsDisplayed()
        compose.onNodeWithText("1. Synthetic Café · 12.34001 XOF · stock 3").assertIsDisplayed()
        compose.onNodeWithTag("catalog-import-cancel").performClick()
        compose.waitUntil(5_000) { counts.discard.get() == 1 }
        assertEquals(1, pickCount.get())
        assertEquals(0, counts.confirm.get())
        compose.onNodeWithTag("catalog-import-preview").assertDoesNotExist()
    }

    @Test fun confirmationRunsOnceAndSessionDenialNeverShowsSuccess() {
        val denied = SecurityFailure(SecurityError.FORBIDDEN)
        val (importer, counts) = previewService(confirmFailure = denied)
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(true) {
            CatalogScreen(catalog, setOf("PRODUCTS:READ", "PRODUCTS:UPDATE"), false,
                CatalogScreenState(), { it(null) }, catalogImport = importer, actorId = 41,
                pickCatalogPdf = { _, callback ->
                    callback(CatalogPdfSelection.Ready(BoundedDocumentInput(byteArrayOf(1, 2, 3))))
                }, catalogPdfImportEnabled = true) { }
        } } }
        compose.onNodeWithTag("catalog-import-pdf").performScrollTo().performClick()
        compose.waitUntil(5_000) { counts.preview.get() == 1 }
        compose.onNodeWithTag("catalog-import-confirm").assertIsEnabled().performClick()
        compose.waitUntil(5_000) { counts.confirm.get() == 1 }
        compose.onNodeWithTag("catalog-import-error").assertIsDisplayed()
        compose.onNodeWithTag("catalog-import-success").assertDoesNotExist()
        assertEquals(1, counts.confirm.get())
    }

    private class AtomicIntegerArray {
        val preview = java.util.concurrent.atomic.AtomicInteger()
        val discard = java.util.concurrent.atomic.AtomicInteger()
        val confirm = java.util.concurrent.atomic.AtomicInteger()
    }

}
