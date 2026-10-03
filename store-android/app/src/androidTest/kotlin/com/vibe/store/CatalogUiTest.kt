package com.vibe.store

import androidx.activity.compose.setContent
import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.vibe.store.api.*
import com.vibe.store.presentation.CatalogScreen
import com.vibe.store.presentation.CatalogScreenState
import com.vibe.store.presentation.SpatialTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt

class CatalogUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private class Service : CatalogService {
        var saved: ProductDraft? = null
        var imageEdit: ImageEdit? = null
        val product = ProductView(1, "Synthetic rice", "Food", "rice", "", 12.34, 10, 2, false, false, "2026-09-30")
        override suspend fun list(filter: CatalogFilter) = CatalogPage(if (filter.search.isEmpty() || product.name.contains(filter.search)) listOf(product) else emptyList(), false)
        override suspend fun detail(id: Long) = product
        override suspend fun save(draft: ProductDraft, image: ImageEdit): CatalogWrite { saved = draft; imageEdit = image; return CatalogWrite(product.copy(name = draft.name)) }
        override suspend fun archive(id: Long, expectedUpdatedAt: String) = error("Not requested by this UI test")
        override suspend fun adjustStock(id: Long, target: Double, reason: String, expectedStock: Long) = error("Not requested by this UI test")
        override suspend fun movements(filter: MovementFilter) = emptyList<MovementView>()
        override suspend fun prices(id: Long) = emptyList<PriceView>()
        override suspend fun image(id: Long): ProductImage? = null
        override suspend fun deleteHistory(type: String?, ids: List<Long>): Int = error("Not requested by this UI test")
    }
    @Test fun frenchContinuousEntryAndCancelledPickerPreserveForm() {
        val service = Service()
        val state = CatalogScreenState()
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            CatalogScreen(service, setOf("PRODUCTS:READ", "PRODUCTS:UPDATE"), true, state, { it(null) }) { }
        } } }
        compose.onNodeWithTag("product-create").performScrollTo().performClick()
        compose.onNodeWithTag("product-name").performScrollTo().performClick().performTextInput("Riz")
        compose.onNodeWithTag("product-name").performTextInput(" local")
        compose.onNodeWithTag("product-name").assertTextContains("Riz local").assertIsFocused()
        compose.onNodeWithTag("product-category").performScrollTo().performTextInput("Alimentation")
        compose.onNodeWithTag("product-price").performScrollTo().performTextInput("12,34")
        compose.onNodeWithText("Choisir une image").performScrollTo().performClick()
        compose.onNodeWithText("Aucune image").assertExists()
        compose.onNodeWithTag("catalog-list").performScrollToNode(hasTestTag("product-save"))
        compose.onNodeWithTag("product-save").performScrollTo().performClick()
        compose.waitUntil { service.saved != null }
        assertEquals("Riz local", service.saved!!.name)
        assertEquals(12.34, service.saved!!.price, 0.0)
        assertSame(ImageEdit.Keep, service.imageEdit)
    }
    @Test fun englishReadOnlyCatalogSearchDetailAndReturn() {
        val service = Service()
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(true) {
            CatalogScreen(service, setOf("PRODUCTS:READ", "STOCKS:READ"), false, CatalogScreenState(), { it(null) }) { }
        } } }
        compose.onNodeWithText("Catalog and stock").assertIsDisplayed()
        compose.onNodeWithTag("product-create").assertDoesNotExist()
        compose.onNodeWithTag("catalog-list").performScrollToNode(hasTestTag("product-1"))
        compose.onNodeWithTag("product-1").performClick()
        compose.onNodeWithText("Edit").assertDoesNotExist()
        compose.onNodeWithText("Adjust stock").assertDoesNotExist()
        compose.onNodeWithText("Archive").assertDoesNotExist()
        compose.onNodeWithTag("catalog-back").performScrollTo().performClick()
        compose.onNodeWithTag("catalog-search").performScrollTo().performTextInput("missing")
        compose.onNodeWithText("No products").assertExists()
    }

    @Test fun adaptiveWindowThresholdsRecomputeAndKeepCatalogUsable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)
        ).bufferedReader().use { it.readText() }
        val originalSize = shell("wm size")
        val originalOverride = Regex("Override size: (\\d+x\\d+)").find(originalSize)?.groupValues?.get(1)
        val density = compose.activity.resources.displayMetrics.density
        val app = compose.activity.application
        val state = CatalogScreenState()
        val base = Service()
        val product = base.product.copy(description = "Synthetic description")
        val service = object : CatalogService by base {
            override suspend fun list(filter: CatalogFilter) = CatalogPage(
                (1L..3L).map { product.copy(id = it, name = "Synthetic rice $it") }
                    .filter { it.name.contains(filter.search) }, false)
            override suspend fun detail(id: Long) = product.copy(id = id, name = "Synthetic rice $id")
        }
        fun render(activity: MainActivity) {
            activity.setContent { SpatialTheme(false) {
                CatalogScreen(service, setOf("PRODUCTS:READ", "PRODUCTS:UPDATE", "STOCKS:READ"), false, state, { it(null) }) { }
            } }
        }
        // Resizing may recreate the Activity. Reattach this same in-memory test
        // fixture; do not inject a width or replace Android's window information.
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { if (activity is MainActivity) render(activity) }
            override fun onActivityCreated(activity: Activity, saved: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, out: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        app.registerActivityLifecycleCallbacks(callbacks)
        try {
            compose.runOnUiThread { render(compose.activity) }
            for ((dp, layout, columns) in listOf(
                Triple(599, "COMPACT", 1), Triple(600, "MEDIUM", 2),
                Triple(839, "MEDIUM", 2), Triple(840, "EXPANDED", 3),
                Triple(599, "COMPACT", 1),
            )) {
                val pixels = (dp * density).roundToInt()
                shell("wm size ${pixels}x${(1100 * density).roundToInt()}")
                compose.waitUntil(timeoutMillis = 10_000) {
                    var observed = false
                    compose.activityRule.scenario.onActivity { activity ->
                        val actual = activity.windowManager.currentWindowMetrics.bounds.width() / activity.resources.displayMetrics.density
                        observed = kotlin.math.abs(actual - dp) < 0.1f
                    }
                    observed && compose.onAllNodesWithTag("catalog-layout-$layout").fetchSemanticsNodes().size == 1
                }
                compose.onNodeWithTag("catalog-layout-$layout").assertIsDisplayed()
                compose.onNodeWithTag("catalog-search").performScrollTo().performTextReplacement("Synthetic")
                compose.onNodeWithTag("catalog-list").performScrollToNode(hasTestTag("catalog-row-1"))
                for (id in 1..3) {
                    val membership = hasAnyAncestor(hasTestTag("catalog-row-1"))
                    if (id <= columns) compose.onNodeWithTag("product-$id").assert(membership)
                    else compose.onAllNodes(hasTestTag("product-$id") and membership).assertCountEquals(0)
                }
                compose.onNodeWithTag("product-1").performClick()
                for (label in listOf("Synthetic rice 1", "Food · rice", "Synthetic description", "Price: 12.34 · Stock: 10", "Threshold: 2")) {
                    compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
                }
                compose.onNodeWithText("Edit").performScrollTo().performClick()
                for ((tag, value) in listOf("product-name" to "Synthetic rice 1", "product-category" to "Food",
                    "product-hashtag" to "rice", "product-description" to "Synthetic description", "product-price" to "12.34", "product-minimum" to "2")) {
                    compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().assertTextContains(value)
                }
                compose.onNodeWithTag("product-save").performScrollTo().assertIsEnabled()
                compose.onNodeWithTag("catalog-back").performScrollTo().performClick()
                compose.onNodeWithTag("catalog-back").performScrollTo().performClick()
                android.util.Log.i("I04_ADAPTIVE", "window=${dp}dp class=$layout columns=$columns search/detail/form=PASS")
            }
        } finally {
            app.unregisterActivityLifecycleCallbacks(callbacks)
            shell(if (originalOverride == null) "wm size reset" else "wm size $originalOverride")
            assertEquals("Restore the original display override", originalSize.trim(), shell("wm size").trim())
        }
    }
}
