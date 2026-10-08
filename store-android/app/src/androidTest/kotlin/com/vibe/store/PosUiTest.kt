package com.vibe.store

import java.math.BigDecimal
import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.vibe.store.api.*
import com.vibe.store.presentation.*
import org.junit.Assert.*
import org.junit.*
import java.io.File

/** Synthetic public-port fixture only. Durable and authorization tests are native. */
class PosUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val state = PosState()
    private val product = ProductView(1, "Synthetic rice", "Food", "rice", "", BigDecimal("10.0"), 5, 0, false, false, "2026-10-03")
    private val catalog = object : CatalogService {
        override suspend fun list(filter: CatalogFilter) = CatalogPage(listOf(product, product.copy(id = 2, name = "Synthetic tea")), false)
        override suspend fun detail(id: Long) = product.copy(id = id)
        override suspend fun save(draft: ProductDraft, image: ImageEdit): CatalogWrite = error("not requested")
        override suspend fun archive(id: Long, expectedUpdatedAt: String): ProductView = error("not requested")
        override suspend fun adjustStock(id: Long, target: Double, reason: String, expectedStock: Long): ProductView = error("not requested")
        override suspend fun movements(filter: MovementFilter) = emptyList<MovementView>()
        override suspend fun prices(id: Long) = emptyList<PriceView>()
        override suspend fun image(id: Long): ProductImage? = null
        override suspend fun deleteHistory(type: String?, ids: List<Long>) = error("not requested")
    }
    private val sent = mutableListOf<SaleCommand>()
    private var failOnce = false
    private val service = object : SaleService {
        val cash = CashView(1, "SYNTHETIC-CASH", 1, "OPEN", BigDecimal("0.0"), BigDecimal("0.0"), "2026-10-03")
        override suspend fun currentCash() = cash
        override suspend fun openCash(opening: BigDecimal) = cash
        override suspend fun closeCash(cashId: Long, counted: BigDecimal) = cash.copy(status = "CLOSED")
        override suspend fun cashHistory(offset: Int) = listOf(cash)
        override suspend fun sell(command: SaleCommand): Receipt {
            sent += command
            if (failOnce) { failOnce = false; throw SaleFailure(SaleError.UNAVAILABLE) }
            return Receipt("SYNTHETIC-RECEIPT", 1, 1, "2026-10-03T12:00:00Z", "validated", BigDecimal("10.0"), BigDecimal("0.0"),
                BigDecimal("10.0"), command.received ?: BigDecimal("10.0"), BigDecimal("0.0"), "CAPTURED", "Synthetic STORE", "", "", "", "EUR",
                listOf(ReceiptLine(1, product.name, "Food", 1, BigDecimal("10.0"), BigDecimal("10.0"), BigDecimal("10.0"))))
        }
        override suspend fun pending() = emptyList<PendingSale>()
        override suspend fun resolve(key: String): Receipt = error("not requested")
        override suspend fun acknowledge(key: String) = Unit
        override suspend fun abandon(key: String) = Unit
        override suspend fun history(filter: SaleFilter) = SalePage(emptyList(), false)
        override suspend fun receipt(id: String): Receipt = error("not requested")
        override suspend fun cancel(id: String, reason: String): Receipt = error("not requested")
    }
    private val actor = PublicIdentity(1, "synthetic", "Synthetic", "Owner", "owner",
        setOf("POS:READ", "POS:VALIDATE", "CASH:READ", "CASH:CREATE", "CASH:VALIDATE", "PRODUCTS:READ"))
    private fun render(activity: MainActivity) { activity.setContent { SpatialTheme(false) {
        PosScreen(service, catalog, actor, true, state) { }
    } } }
    private val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) { if (activity is MainActivity) render(activity) }
        override fun onActivityCreated(activity: Activity, saved: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, out: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
    @Before fun setup() {
        compose.activity.application.registerActivityLifecycleCallbacks(callbacks)
        compose.runOnUiThread { render(compose.activity) }
    }
    @After fun cleanup() { compose.activity.application.unregisterActivityLifecycleCallbacks(callbacks) }
    private fun cart() {
        if (compose.onAllNodesWithTag("pos-cart-switch").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithTag("pos-cart-switch").performClick()
    }
    @Test fun zeroQuantityAndContinuousCommaPaymentReachRealSubmit() {
        compose.onNodeWithTag("pos-add-1").performScrollTo().performClick()
        compose.onNodeWithTag("pos-add-2").performScrollTo().performClick()
        cart()
        compose.onNodeWithTag("pos-quantity-2").performScrollTo().performTextReplacement("0")
        compose.onNodeWithTag("pos-received").performScrollTo().performTextInput("2")
        compose.onNodeWithTag("pos-received").performTextInput("5,50")
        compose.onNodeWithTag("pos-received").assertTextContains("25,50")
        compose.onNodeWithTag("pos-received").performImeAction()
        compose.onNodeWithTag("pos-submit").performScrollTo().performClick()
        compose.waitUntil { sent.isNotEmpty() }
        assertEquals(listOf(SaleLine(1, 1)), sent.single().lines)
        assertTrue(BigDecimal("25.5").compareTo(sent.single().received!!) == 0)
        compose.onNodeWithTag("pos-receipt").assertIsDisplayed()
    }
    @Test fun retryRetainsKeyAndActivityRecreationRetainsOnlyEphemeralCart() {
        compose.onNodeWithTag("pos-add-1").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        cart()
        compose.onNodeWithTag("pos-quantity-1").performScrollTo().assertTextContains("1")
        failOnce = true
        compose.onNodeWithTag("pos-submit").performScrollTo().performClick()
        compose.waitUntil { sent.size == 1 }
        compose.onNodeWithTag("pos-error").assertExists()
        compose.onNodeWithText("Résoudre la commande").performScrollTo().performClick()
        compose.waitUntil { sent.size == 2 }
        assertEquals(sent[0].key, sent[1].key)
        assertEquals(sent[0].lines, sent[1].lines)
    }
    @Test fun actualWindowClassAndIndependentPanels() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val expected = checkNotNull(InstrumentationRegistry.getArguments().getString("posWidth")).toInt()
        val density = compose.activity.resources.displayMetrics.density
        val expectedPixels = Math.round(expected * density)
        val actualPixels = compose.activity.windowManager.currentWindowMetrics.bounds.width()
        assertEquals(expectedPixels, actualPixels)
        val actual = actualPixels / density
        val mode = if (actual < 600) "COMPACT" else if (actual < 840) "MEDIUM" else "EXPANDED"
        compose.onNodeWithTag("pos-layout-$mode").assertIsDisplayed()
        compose.onNodeWithTag("pos-products").assertIsDisplayed()
        if (mode == "COMPACT") {
            compose.onNodeWithTag("pos-cart").assertDoesNotExist(); cart()
            compose.onNodeWithTag("pos-cart").assertIsDisplayed()
        } else {
            val products = compose.onNodeWithTag("pos-products").fetchSemanticsNode().boundsInRoot
            val cart = compose.onNodeWithTag("pos-cart").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(products.right <= cart.left)
            if (mode == "EXPANDED") assertTrue(products.width > cart.width * 1.2f)
        }
        compose.waitForIdle()
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(instrumentation.targetContext.getExternalFilesDir(null), "i06-$mode.png").outputStream().use {
            check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        screenshot.recycle()
        assertTrue(sent.isEmpty())
    }
}
