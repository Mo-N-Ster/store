package com.vibe.store

import java.math.BigDecimal
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.vibe.store.api.*
import com.vibe.store.presentation.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class WorkflowNavigationUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun managementRouteAppearsOnlyWithCapability() {
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            var page by remember { mutableStateOf("home") }
            Column {
                StoreNavigationBar(page, setOf("PURCHASES:READ", "STOCKS:READ"),
                    hasCatalog = false, hasTeam = false, french = true, enabled = true,
                    hasWorkflows = true) { page = it }
                Text(page, Modifier.testTag("workflow-route"))
            }
        } } }
        compose.onNodeWithTag("nav-operations").assertExists().performClick()
        compose.onNodeWithTag("workflow-route").assertTextEquals("operations")
    }

    @Test fun managementRouteHiddenWithoutCapability() {
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            StoreNavigationBar("home", setOf("STOCKS:READ"), false, false,
                true, true, hasWorkflows = false) { error("Forbidden") }
        } } }
        compose.onNodeWithTag("nav-operations").assertDoesNotExist()
    }

    @Test fun supplierCreationCallsServiceAndReloadsList() {
        var saved by mutableStateOf<SupplierView?>(null)
        val service = object : SupplierService {
            override suspend fun list(filter: SupplierFilter): WorkflowPage<SupplierView> =
                WorkflowPage(listOfNotNull(saved), false)
            override suspend fun detail(id: Long): SupplierView = requireNotNull(saved)
            override suspend fun save(command: SaveSupplier): SupplierView {
                val result = SupplierView(1, command.name, command.phone, command.email,
                    command.address, command.active, "2026-10-04", "2026-10-04")
                saved = result
                return result
            }
        }
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            SupplierScreen(service, setOf("PURCHASES:READ", "PURCHASES:UPDATE"), true) {}
        } } }
        compose.onNodeWithTag("supplier-create").performClick()
        compose.onNodeWithTag("supplier-name").performTextInput("Fournisseur synthetic")
        compose.onNodeWithTag("supplier-save").performClick()
        compose.waitUntil(10_000) { saved != null }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Fournisseur synthetic").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun adaptiveWorkflowScreensUseRealWidthAndAccessiblePrimaryActions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val expectedWidth = checkNotNull(
            arguments.getString("workflowWidth")
        ).toInt()
        val workflowSize = checkNotNull(
            arguments.getString("workflowSize")
        )

        fun shell(command: String): String =
            ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(command)
            ).bufferedReader().use { it.readText() }

        /*
         * Apply the synthetic display only after ActivityScenario has launched
         * MainActivity. External pre-launch rotation is emulator-state dependent.
         */
        shell("wm density 320")
        shell("wm size $workflowSize")

        compose.waitUntil(10_000) {
            val currentDensity =
                compose.activity.resources.displayMetrics.density
            val currentWidth =
                compose.activity.windowManager
                    .currentWindowMetrics.bounds.width() / currentDensity

            kotlin.math.abs(
                currentWidth - expectedWidth.toFloat()
            ) <= 1.5f
        }

        val density =
            compose.activity.resources.displayMetrics.density
        val actualWidth =
            compose.activity.windowManager
                .currentWindowMetrics.bounds.width() / density

        assertEquals(
            expectedWidth.toFloat(),
            actualWidth,
            1.5f,
        )

        println(
            "I07_UI_DISPLAY_READY " +
                "requestedSize=$workflowSize " +
                "actualDp=$actualWidth"
        )

        val mode = when {
            actualWidth < 600f -> "COMPACT"
            actualWidth < 840f -> "MEDIUM"
            else -> "EXPANDED"
        }

        fun assertPrimaryAction(tag: String) {
            val bounds = compose
                .onNodeWithTag(tag)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot

            assertTrue(
                "$tag width must be >=48dp",
                bounds.width / density >= 48f,
            )
            assertTrue(
                "$tag height must be >=48dp",
                bounds.height / density >= 48f,
            )
        }

        fun capture(screen: String) {
            compose.waitForIdle()
            val screenshot =
                checkNotNull(instrumentation.uiAutomation.takeScreenshot())

            val output = File(
                checkNotNull(
                    instrumentation.targetContext.getExternalFilesDir(null)
                ),
                "i07-$mode-$screen.png",
            )

            output.outputStream().use {
                check(
                    screenshot.compress(
                        Bitmap.CompressFormat.PNG,
                        100,
                        it,
                    )
                )
            }

            screenshot.recycle()
            println("I07_UI_CAPTURE=${output.absolutePath}")
        }

        val supplierRows = (1L..30L).map { id ->
            SupplierView(
                id,
                "Synthetic supplier $id",
                "000$id",
                "supplier$id@example.test",
                "Address $id",
                true,
                "2026-10-07",
                "2026-10-07",
            )
        }

        val supplierService = object : SupplierService {
            override suspend fun list(filter: SupplierFilter) =
                WorkflowPage(supplierRows, false)

            override suspend fun detail(id: Long): SupplierView =
                error("not requested")

            override suspend fun save(command: SaveSupplier): SupplierView =
                error("not requested")
        }

        val catalogService = object : CatalogService {
            override suspend fun list(filter: CatalogFilter) =
                CatalogPage(emptyList(), false)

            override suspend fun detail(id: Long): ProductView =
                error("not requested")

            override suspend fun save(
                draft: ProductDraft,
                image: ImageEdit,
            ): CatalogWrite = error("not requested")

            override suspend fun archive(
                id: Long,
                expectedUpdatedAt: String,
            ): ProductView = error("not requested")

            override suspend fun adjustStock(
                id: Long,
                target: Double,
                reason: String,
                expectedStock: Long,
            ): ProductView = error("not requested")

            override suspend fun movements(filter: MovementFilter) =
                emptyList<MovementView>()

            override suspend fun prices(id: Long) =
                emptyList<PriceView>()

            override suspend fun image(id: Long): ProductImage? = null

            override suspend fun deleteHistory(
                type: String?,
                ids: List<Long>,
            ) = error("not requested")
        }

        val purchaseService = object : PurchaseService {
            override suspend fun list(filter: PurchaseFilter) =
                WorkflowPage<PurchaseSummary>(emptyList(), false)

            override suspend fun detail(id: Long): PurchaseDetail =
                error("not requested")

            override suspend fun lines(
                id: Long,
                page: WorkflowPageRequest,
            ) = WorkflowPage<PurchaseLineView>(emptyList(), false)

            override suspend fun createDraft(
                command: CreatePurchaseDraft,
            ): PurchaseDetail = error("not requested")

            override suspend fun saveLine(
                command: SavePurchaseLine,
            ): PurchaseLineView = error("not requested")

            override suspend fun validate(
                command: ValidatePurchase,
            ): PurchaseDetail = error("not requested")

            override suspend fun cancel(
                command: CancelPurchase,
            ): PurchaseDetail = error("not requested")

            override suspend fun createArticle(
                command: PurchaseArticleCreation,
            ): CatalogWrite = error("not requested")
        }

        val inventoryService = object : InventoryService {
            override suspend fun list(filter: InventoryFilter) =
                WorkflowPage<InventoryView>(emptyList(), false)

            override suspend fun detail(id: Long): InventoryView =
                error("not requested")

            override suspend fun lines(
                id: Long,
                page: WorkflowPageRequest,
            ) = WorkflowPage<InventoryLineView>(emptyList(), false)

            override suspend fun review(id: Long): InventoryReview =
                error("not requested")

            override suspend fun start(
                command: StartInventory,
            ): InventoryView = error("not requested")

            override suspend fun recordCount(
                command: RecordInventoryCount,
            ): InventoryLineView = error("not requested")

            override suspend fun validate(
                command: ValidateInventory,
            ): InventoryView = error("not requested")
        }

        compose.runOnUiThread {
            compose.activity.setContent {
                SpatialTheme(false) {
                    SupplierScreen(
                        supplierService,
                        setOf(
                            "PURCHASES:READ",
                            "PURCHASES:UPDATE",
                        ),
                        true,
                    ) {}
                }
            }
        }

        compose.waitForIdle()
        compose.onNodeWithTag("workflow-screen-supplier")
            .assertIsDisplayed()
        compose.onNodeWithTag("workflow-layout-$mode")
            .assertIsDisplayed()
        assertPrimaryAction("supplier-create")

        val supplierPrimary =
            compose.onNodeWithTag("workflow-primary-supplier")
                .assertIsDisplayed()

        val supplierSecondary =
            compose.onNodeWithTag("workflow-secondary-supplier")
                .assertExists()

        capture("supplier")

        if (mode != "COMPACT") {
            supplierSecondary.assertIsDisplayed()

            val firstSupplier =
                compose.onNodeWithText("Synthetic supplier 1")
                    .assertIsDisplayed()

            val primaryTopBefore =
                supplierPrimary.fetchSemanticsNode().boundsInRoot.top

            val supplierTopBefore =
                firstSupplier.fetchSemanticsNode().boundsInRoot.top

            supplierSecondary.performTouchInput { swipeUp() }
            compose.waitForIdle()

            val primaryTopAfter =
                supplierPrimary.fetchSemanticsNode().boundsInRoot.top

            val supplierTopAfter =
                firstSupplier.fetchSemanticsNode().boundsInRoot.top

            assertEquals(
                "primary pane must not move when secondary pane scrolls",
                primaryTopBefore,
                primaryTopAfter,
                1f,
            )

            assertTrue(
                "secondary supplier pane must scroll independently",
                supplierTopAfter < supplierTopBefore,
            )

            println(
                "I07_UI_SCROLL_PASS screen=supplier mode=$mode " +
                    "primaryTop=$primaryTopAfter " +
                    "supplierBefore=$supplierTopBefore " +
                    "supplierAfter=$supplierTopAfter"
            )
        }

        compose.runOnUiThread {
            compose.activity.setContent {
                SpatialTheme(false) {
                    PurchaseScreen(
                        purchaseService,
                        supplierService,
                        catalogService,
                        setOf(
                            "PURCHASES:READ",
                            "PURCHASES:CREATE",
                            "PURCHASES:UPDATE",
                            "PURCHASES:VALIDATE",
                            "PURCHASES:DELETE",
                            "PRODUCTS:UPDATE",
                        ),
                        true,
                    ) {}
                }
            }
        }

        compose.waitForIdle()
        compose.onNodeWithTag("workflow-screen-purchase")
            .assertIsDisplayed()
        compose.onNodeWithTag("workflow-layout-$mode")
            .assertIsDisplayed()
        assertPrimaryAction("purchase-new")
        compose.onNodeWithTag("workflow-primary-purchase")
            .assertIsDisplayed()
        compose.onNodeWithTag("workflow-secondary-purchase")
            .assertExists()
        if (mode != "COMPACT") {
            compose.onNodeWithTag("workflow-secondary-purchase")
                .assertIsDisplayed()
        }
        capture("purchase")

        compose.runOnUiThread {
            compose.activity.setContent {
                SpatialTheme(false) {
                    InventoryScreen(
                        inventoryService,
                        setOf(
                            "STOCKS:READ",
                            "STOCKS:CREATE",
                            "STOCKS:UPDATE",
                            "STOCKS:VALIDATE",
                        ),
                        true,
                    ) {}
                }
            }
        }

        compose.waitForIdle()
        compose.onNodeWithTag("workflow-screen-inventory")
            .assertIsDisplayed()
        compose.onNodeWithTag("workflow-layout-$mode")
            .assertIsDisplayed()
        assertPrimaryAction("inventory-start")
        compose.onNodeWithTag("workflow-primary-inventory")
            .assertIsDisplayed()
        compose.onNodeWithTag("workflow-secondary-inventory")
            .assertExists()
        if (mode != "COMPACT") {
            compose.onNodeWithTag("workflow-secondary-inventory")
                .assertIsDisplayed()
        }
        capture("inventory")

        println(
            "I07_UI_WIDTH_PASS mode=$mode " +
                "actualDp=$actualWidth expectedDp=$expectedWidth"
        )
    }


    @Test fun workflowFrenchEnglishAndLightDarkRender() {
        val service = object : SupplierService {
            override suspend fun list(filter: SupplierFilter) =
                WorkflowPage<SupplierView>(emptyList(), false)

            override suspend fun detail(id: Long): SupplierView =
                error("not requested")

            override suspend fun save(command: SaveSupplier): SupplierView =
                error("not requested")
        }

        fun render(french: Boolean, dark: Boolean): Int {
            var background: Int? = null

            compose.runOnUiThread {
                compose.activity.setContent {
                    SpatialTheme(dark) {
                        val themeBackground =
                            MaterialTheme.colorScheme.background
                        SideEffect {
                            background = themeBackground.toArgb()
                        }

                        SupplierScreen(
                            service,
                            setOf(
                                "PURCHASES:READ",
                                "PURCHASES:UPDATE",
                            ),
                            french,
                        ) {}
                    }
                }
            }

            compose.waitForIdle()

            compose.onNodeWithText(
                if (french) "Fournisseurs" else "Suppliers"
            ).assertIsDisplayed()

            compose.onNodeWithText(
                if (french) "Retour" else "Back"
            ).assertIsDisplayed()

            compose.onNodeWithText(
                if (french) "Nouveau fournisseur" else "New supplier"
            ).assertIsDisplayed()

            return checkNotNull(background)
        }

        val frLight = render(french = true, dark = false)
        val enLight = render(french = false, dark = false)
        val frDark = render(french = true, dark = true)
        val enDark = render(french = false, dark = true)

        assertEquals(frLight, enLight)
        assertEquals(frDark, enDark)

        assertNotEquals(
            "light/dark backgrounds must differ",
            frLight,
            frDark,
        )

        println(
            "I07_UI_THEME_LOCALE_PASS " +
                "frLight=$frLight enLight=$enLight " +
                "frDark=$frDark enDark=$enDark"
        )
    }

    @Test fun workflowImeFocusAndDecimalCommaPointRemainUsable() {
        val supplierService = object : SupplierService {
            override suspend fun list(filter: SupplierFilter) =
                WorkflowPage<SupplierView>(emptyList(), false)

            override suspend fun detail(id: Long): SupplierView =
                error("not requested")

            override suspend fun save(command: SaveSupplier): SupplierView =
                error("not requested")
        }

        val catalogService = object : CatalogService {
            override suspend fun list(filter: CatalogFilter) =
                CatalogPage(emptyList(), false)

            override suspend fun detail(id: Long): ProductView =
                error("not requested")

            override suspend fun save(
                draft: ProductDraft,
                image: ImageEdit,
            ): CatalogWrite = error("not requested")

            override suspend fun archive(
                id: Long,
                expectedUpdatedAt: String,
            ): ProductView = error("not requested")

            override suspend fun adjustStock(
                id: Long,
                target: Double,
                reason: String,
                expectedStock: Long,
            ): ProductView = error("not requested")

            override suspend fun movements(filter: MovementFilter) =
                emptyList<MovementView>()

            override suspend fun prices(id: Long) =
                emptyList<PriceView>()

            override suspend fun image(id: Long): ProductImage? = null

            override suspend fun deleteHistory(
                type: String?,
                ids: List<Long>,
            ) = error("not requested")
        }

        val draft = PurchaseDetail(
            PurchaseSummary(
                1L,
                "PUR-SYNTHETIC-1",
                null,
                PurchaseStatus.DRAFT,
                BigDecimal("0.0"),
                1L,
                "2026-10-07T00:00:00Z",
            ),
            "",
            "",
            null,
            null,
            null,
            null,
            null,
        )

        val capturedPrices = mutableListOf<java.math.BigDecimal>()

        val purchaseService = object : PurchaseService {
            override suspend fun list(filter: PurchaseFilter) =
                WorkflowPage<PurchaseSummary>(emptyList(), false)

            override suspend fun detail(id: Long) = draft

            override suspend fun lines(
                id: Long,
                page: WorkflowPageRequest,
            ) = WorkflowPage<PurchaseLineView>(emptyList(), false)

            override suspend fun createDraft(
                command: CreatePurchaseDraft,
            ) = draft

            override suspend fun saveLine(
                command: SavePurchaseLine,
            ): PurchaseLineView = error("not requested")

            override suspend fun validate(
                command: ValidatePurchase,
            ): PurchaseDetail = error("not requested")

            override suspend fun cancel(
                command: CancelPurchase,
            ): PurchaseDetail = error("not requested")

            override suspend fun createArticle(
                command: PurchaseArticleCreation,
            ): CatalogWrite {
                capturedPrices += command.price
                throw WorkflowFailure(WorkflowError.INVALID_INPUT)
            }
        }

        compose.runOnUiThread {
            compose.activity.setContent {
                SpatialTheme(false) {
                    PurchaseScreen(
                        purchaseService,
                        supplierService,
                        catalogService,
                        setOf(
                            "PURCHASES:READ",
                            "PURCHASES:CREATE",
                            "PURCHASES:UPDATE",
                            "PRODUCTS:UPDATE",
                        ),
                        false,
                    ) {}
                }
            }
        }

        compose.onNodeWithTag("purchase-new")
            .performClick()

        compose.onNodeWithTag("purchase-create-draft")
            .performClick()

        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("purchase-product-search")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        compose.onNodeWithText("Create product")
            .performClick()

        compose.onNodeWithTag("purchase-article-name")
            .performTextInput("Synthetic product")

        compose.onNodeWithTag("purchase-article-category")
            .performTextInput("Synthetic")

        val price =
            compose.onNodeWithTag("purchase-article-price")

        price.performClick()
        price.assertIsFocused()

        val inputMethod = checkNotNull(
            compose.activity.getSystemService(
                InputMethodManager::class.java
            )
        )

        compose.runOnUiThread {
            val focused =
                checkNotNull(compose.activity.currentFocus)

            inputMethod.showSoftInput(
                focused,
                InputMethodManager.SHOW_IMPLICIT,
            )
        }

        compose.waitUntil(5_000) {
            compose.activity.window.decorView.rootWindowInsets
                ?.isVisible(WindowInsets.Type.ime()) == true
        }

        price.performTextReplacement("12,50")
        price.assertIsFocused()

        compose.onNodeWithTag("purchase-create-article")
            .assertIsEnabled()

        assertTrue(
            "IME must remain visible after comma input",
            compose.activity.window.decorView.rootWindowInsets
                ?.isVisible(WindowInsets.Type.ime()) == true,
        )

        price.performTextReplacement("13.25")
        price.assertIsFocused()

        compose.onNodeWithTag("purchase-create-article")
            .assertIsEnabled()

        assertTrue(
            "IME must remain visible after point input",
            compose.activity.window.decorView.rootWindowInsets
                ?.isVisible(WindowInsets.Type.ime()) == true,
        )

        price.performTextReplacement("12,50")
        price.assertIsFocused()

        compose.runOnUiThread {
            val focused =
                compose.activity.currentFocus
                    ?: compose.activity.window.decorView

            inputMethod.hideSoftInputFromWindow(
                focused.windowToken,
                0,
            )
        }

        compose.onNodeWithTag("purchase-create-article")
            .assertIsEnabled()
            .performScrollTo()
            .performClick()

        compose.waitUntil(10_000) {
            capturedPrices.size == 1
        }

        assertEquals(0, capturedPrices[0].compareTo(java.math.BigDecimal("12.5")))

        price.performScrollTo()
            .performClick()
        price.performTextReplacement("13.25")
        price.assertIsFocused()

        compose.onNodeWithTag("purchase-create-article")
            .assertIsEnabled()
            .performScrollTo()
            .performClick()

        compose.waitUntil(10_000) {
            capturedPrices.size == 2
        }

        assertEquals(0, capturedPrices[1].compareTo(java.math.BigDecimal("13.25")))

        println(
            "I07_UI_IME_DECIMAL_PASS " +
                "comma=${capturedPrices[0]} " +
                "point=${capturedPrices[1]}"
        )
    }

}
