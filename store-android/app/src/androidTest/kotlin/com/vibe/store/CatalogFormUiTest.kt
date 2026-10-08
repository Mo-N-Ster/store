package com.vibe.store

import java.math.BigDecimal
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import com.vibe.store.api.*
import com.vibe.store.presentation.CatalogScreen
import com.vibe.store.presentation.CatalogScreenState
import com.vibe.store.presentation.SpatialTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic presentation fixtures only: no database or real catalogue writes. */
class CatalogFormUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private class Service : CatalogService {
        val product = ProductView(1, "Synthetic rice", "Food", "rice", "Description", BigDecimal("12.34"), 10, 2, false, true, "2026-10-02")
        @Volatile var saves = 0
        var draft: ProductDraft? = null
        var imageEdit: ImageEdit? = null
        var failure = false
        var release: CompletableDeferred<Unit>? = null
        override suspend fun list(filter: CatalogFilter) = CatalogPage(listOf(product), false)
        override suspend fun detail(id: Long) = product
        override suspend fun save(draft: ProductDraft, image: ImageEdit): CatalogWrite {
            saves++; this.draft = draft; imageEdit = image
            release?.await()
            if (failure) throw CatalogFailure(CatalogError.INVALID_INPUT)
            return CatalogWrite(product.copy(name = draft.name, price = draft.price))
        }
        override suspend fun archive(id: Long, expectedUpdatedAt: String): ProductView = error("Not used")
        override suspend fun adjustStock(id: Long, target: Double, reason: String, expectedStock: Long): ProductView = error("Not used")
        override suspend fun movements(filter: MovementFilter) = emptyList<MovementView>()
        override suspend fun prices(id: Long) = emptyList<PriceView>()
        override suspend fun image(id: Long): ProductImage? = null
        override suspend fun deleteHistory(type: String?, ids: List<Long>): Int = error("Not used")
    }
    private fun show(service: Service, state: CatalogScreenState, update: Boolean = true,
        picker: ((SelectedImage?) -> Unit) -> Unit = { it(null) }) {
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            CatalogScreen(service, if (update) setOf("PRODUCTS:READ", "PRODUCTS:UPDATE") else setOf("PRODUCTS:READ"),
                false, state, picker) { }
        } } }
    }
    private fun field(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("catalog-list").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag).performScrollTo()
    }
    private fun imageAction(label: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("catalog-list").performScrollToNode(hasTestTag("product-image-section"))
        return compose.onNodeWithText(label).performScrollTo()
    }
    private fun replace(tag: String, value: String) { field(tag).performTextReplacement(value); field(tag).performImeAction() }
    private fun backNode(): SemanticsNodeInteraction {
        compose.onNodeWithTag("catalog-list").performScrollToNode(hasTestTag("catalog-back"))
        return compose.onNodeWithTag("catalog-back").performScrollTo()
    }
    private fun back() { backNode().performClick() }
    private fun newState() = CatalogScreenState().apply { edit(null) }

    @Test fun creationContinuousInputAndSingleSaveWhileBusy() {
        val service = Service().apply { release = CompletableDeferred() }; val state = newState()
        show(service, state)
        field("product-name").performTextInput("Riz")
        field("product-name").performTextInput(" local")
        field("product-name").assertTextContains("Riz local").assertIsFocused().performImeAction()
        replace("product-category", "Food"); replace("product-price", "12,34")
        field("product-stock").assertTextContains("0"); field("product-minimum").assertTextContains("0")
        field("product-save").performClick()
        compose.waitUntil { service.saves == 1 }
        field("product-save").assertIsNotEnabled()
        backNode().assertIsNotEnabled()
        Espresso.pressBack()
        compose.runOnIdle { assertTrue(state.editing); assertEquals(1, service.saves); service.release!!.complete(Unit) }
        compose.waitUntil { !state.editing }
        assertEquals(1, service.saves); assertEquals("Riz local", service.draft!!.name)
        assertTrue(BigDecimal("12.34").compareTo(service.draft!!.price) == 0); assertSame(ImageEdit.Keep, service.imageEdit)
    }
    @Test fun editingKeepsInitialValuesAndSavesExistingIdentity() {
        val service = Service(); val state = CatalogScreenState().apply { edit(service.product) }; show(service, state)
        for ((tag, value) in listOf("product-name" to "Synthetic rice", "product-category" to "Food", "product-hashtag" to "rice",
            "product-description" to "Description", "product-price" to "12.34", "product-minimum" to "2")) field(tag).assertTextContains(value)
        compose.onNodeWithTag("product-stock").assertDoesNotExist()
        replace("product-price", "15.00"); field("product-save").performClick()
        compose.waitUntil { !state.editing }
        assertEquals(1L, service.draft!!.id); assertEquals(service.product.updatedAt, service.draft!!.expectedUpdatedAt)
        assertTrue(BigDecimal("15.0").compareTo(service.draft!!.price) == 0); assertEquals(1, service.saves)
    }
    @Test fun cancelDiscardKeepsFieldsAndReplacementImage() {
        val service = Service(); val state = newState(); val selected = object : SelectedImage {}
        show(service, state, picker = { it(selected) }); replace("product-name", "Draft")
        imageAction("Choose image").performClick()
        back(); compose.onNodeWithTag("product-discard-dialog").assertIsDisplayed()
        compose.onNodeWithTag("product-discard-cancel").performClick()
        field("product-name").assertTextContains("Draft")
        compose.runOnIdle { assertSame(selected, (state.image as ImageEdit.Replace).selection); assertEquals(0, service.saves) }
    }
    @Test fun systemBackConfirmedDiscardClearsOnlyPresentationDraft() {
        val service = Service(); val state = CatalogScreenState().apply { edit(service.product) }; show(service, state)
        replace("product-name", "Changed"); Espresso.pressBack()
        compose.onNodeWithTag("product-discard-confirm").performClick()
        compose.runOnIdle {
            assertFalse(state.editing); assertEquals("", state.name); assertSame(ImageEdit.Keep, state.image)
            assertEquals(service.product, state.selected); assertEquals(0, service.saves)
        }
    }
    @Test fun unchangedAndRestoredValuesReturnWithoutConfirmation() {
        val service = Service(); val state = newState(); show(service, state)
        replace("product-name", "Temporary"); replace("product-name", "")
        back(); compose.onNodeWithTag("product-discard-dialog").assertDoesNotExist()
        compose.runOnIdle { assertFalse(state.editing); assertEquals(0, service.saves) }
    }
    @Test fun cancelledPickerPreservesExistingSelectionAndEntries() {
        val service = Service(); val state = newState(); val selected = object : SelectedImage {}; var first = true
        show(service, state, picker = { callback -> callback(if (first) selected else null); first = false })
        replace("product-description", "Keep this")
        repeat(2) { imageAction("Choose image").performClick() }
        compose.runOnIdle { assertEquals("Keep this", state.description); assertSame(selected, (state.image as ImageEdit.Replace).selection) }
        assertEquals(0, service.saves)
    }
    @Test fun failedSaveRetainsFormFieldsAndImageRemoval() {
        val service = Service().apply { failure = true }; val state = CatalogScreenState().apply { edit(service.product) }; show(service, state)
        replace("product-name", "Retain")
        imageAction("Remove image").performClick()
        field("product-save").performClick()
        field("catalog-error").assertIsDisplayed()
        field("product-name").assertTextContains("Retain")
        compose.runOnIdle { assertTrue(state.editing); assertSame(ImageEdit.Remove, state.image); assertEquals(1, service.saves) }
    }
    @Test fun readOnlyPermissionsDoNotExposeCreationOrEdit() {
        val service = Service(); val state = CatalogScreenState(); show(service, state, update = false)
        compose.onNodeWithTag("product-create").assertDoesNotExist()
        compose.onNodeWithTag("catalog-list").performScrollToNode(hasTestTag("product-1"))
        compose.onNodeWithTag("product-1").performClick()
        compose.onNodeWithText("Edit").assertDoesNotExist(); compose.onNodeWithTag("product-save").assertDoesNotExist()
        assertEquals(0, service.saves)
    }
    @Test fun imeDoneClearsFocusWithoutSavingThenExplicitSaveWorks() {
        val service = Service(); val state = newState(); show(service, state)
        field("product-name").performClick().performTextInput("Keyboard")
        field("product-name").performImeAction()
        field("product-name").assertIsNotFocused()
        assertEquals(0, service.saves)
        replace("product-category", "Food"); replace("product-price", "1.00")
        assertEquals(0, service.saves)
        field("product-save").assertIsDisplayed().performClick()
        compose.waitUntil { !state.editing }; assertEquals(1, service.saves)
    }
    @Test fun compactFormSectionsAndAllControlsRemainReachable() {
        val service = Service(); val state = newState(); show(service, state)
        compose.onNodeWithTag("catalog-layout-COMPACT").assertIsDisplayed()
        for (tag in listOf("product-name", "product-category", "product-hashtag", "product-description", "product-price", "product-stock", "product-minimum")) {
            field(tag).performClick().performImeAction()
            field(tag).assertIsNotFocused()
            field(tag).assertIsDisplayed()
        }
        imageAction("Choose image").assertIsDisplayed()
        field("product-save").assertIsDisplayed().assertIsEnabled()
        assertEquals(0, service.saves)
    }
}
