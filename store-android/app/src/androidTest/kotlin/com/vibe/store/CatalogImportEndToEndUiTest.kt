package com.vibe.store

import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.graphics.Rect
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.vibe.store.api.*
import com.vibe.store.application.catalog.CatalogAuthority
import com.vibe.store.application.catalog.CatalogImportAuthority
import com.vibe.store.application.security.IdentityAuthority
import com.vibe.store.infrastructure.documents.parser.AndroidPdfSubjectExtractor
import com.vibe.store.infrastructure.media.AndroidProductMedia
import com.vibe.store.infrastructure.persistence.AndroidPersistence
import com.vibe.store.infrastructure.security.BcryptPasswords
import com.vibe.store.presentation.CatalogPdfSelection
import com.vibe.store.presentation.CatalogScreen
import com.vibe.store.presentation.CatalogScreenState
import com.vibe.store.presentation.SpatialTheme
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Full synthetic CatalogScreen -> DocumentsUI -> isolated parser -> authority -> Room flow. */
class CatalogImportEndToEndUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun realPickerPreviewConfirmationAndRoomPersistenceAreOneWorkflow() = runBlocking {
        val base = compose.activity
        val isolatedRoot = File(base.noBackupFilesDir, "i10-e2e-${UUID.randomUUID()}")
        val isolatedContext = object : ContextWrapper(base) {
            override fun getNoBackupFilesDir(): File = isolatedRoot
            override fun getApplicationContext(): Context = this
        }
        val persistence = AndroidPersistence.create(isolatedContext)
        val identity = IdentityAuthority(persistence.owner, persistence.commands, BcryptPasswords())
        var fixtureUri: Uri? = null
        try {
            val actor = identity.bootstrap(OwnerRegistration(
                "i10-e2e-owner", "i10-e2e@example.invalid", "Synthetic", "Owner",
                "Synthetic-Password-123", "Synthetic question", "Synthetic answer",
            ))
            identity.login(Credentials("i10-e2e-owner", "Synthetic-Password-123"))
            val catalog = CatalogAuthority(identity, AndroidProductMedia.create(base))
            val importer = CatalogImportAuthority(
                identity, System::currentTimeMillis, AndroidPdfSubjectExtractor(base),
            )
            val json = """{"kind":"stocks","version":1,"products":[{"name":"Synthetic Café Import","hashtag":"#i10-e2e","category":"Synthetic","description":"Untrusted fixture","price":12.3456789,"stockQuantity":4,"minStockThreshold":1}]}"""
            val payload = "STORE_DATA_V1:" + android.util.Base64.encodeToString(
                json.toByteArray(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP,
            )
            val fileName = "i10-e2e-${UUID.randomUUID()}.pdf"
            fixtureUri = writeSyntheticDownload(fileName, pdfWithSubject(payload))

            compose.runOnUiThread {
                compose.activity.setContent {
                    SpatialTheme(dark = true) {
                        CatalogImportEndToEndHost(
                            catalog = catalog,
                            importer = importer,
                            actorId = actor.id,
                            activity = compose.activity,
                        )
                    }
                }
            }
            compose.waitForIdle()
            assertTrue(catalog.list(CatalogFilter(limit = 20)).items.isEmpty())

            compose.onNodeWithTag("catalog-import-pdf").performScrollTo().performClick()
            waitForDocumentsUi()
            assertTrue("DocumentsUI back action failed", InstrumentationRegistry.getInstrumentation().uiAutomation
                .performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("catalog-import-pdf").fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue("cancelled picker must not mutate Room", catalog.list(CatalogFilter(limit = 20)).items.isEmpty())

            compose.onNodeWithTag("catalog-import-pdf").performScrollTo().performClick()
            waitForDocumentsUi()
            chooseDownloads()
            assertTrue(clickDocumentRow(fileName))
            compose.waitUntil(10_000) {
                InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
                    ?.findAccessibilityNodeInfosByText(fileName)?.isNotEmpty() == true
            }
            // Some DocumentsUI builds open a single-select item on row activation;
            // others leave a selected row and require the explicit Open action.
            if (isDocumentsUi()) clickText("Open")

            compose.waitUntil(20_000) { compose.onAllNodesWithTag("catalog-import-preview").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Synthetic Café Import", substring = true).assertExists()
            compose.onNodeWithText("12.3456789", substring = true).assertExists()
            assertTrue("preview must not write business data", catalog.list(CatalogFilter(limit = 20)).items.isEmpty())
            assertTrue("preview must not create stock movements", catalog.movements().isEmpty())

            compose.onNodeWithTag("catalog-import-confirm").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("catalog-import-success").fetchSemanticsNodes().isNotEmpty() }
            val imported = catalog.list(CatalogFilter(limit = 20)).items.single()
            assertEquals("Synthetic Café Import", imported.name)
            assertEquals(0, imported.price.compareTo(java.math.BigDecimal("12.3456789")))
            assertEquals(4L, imported.stock)
            assertEquals(1, catalog.prices(imported.id).size)
            assertEquals(1, catalog.movements().size)
            persistence.owner.checkpointCloseReopen()
            assertEquals("Synthetic Café Import", catalog.list(CatalogFilter(limit = 20)).items.single().name)
        } finally {
            fixtureUri?.let { runCatching { base.contentResolver.delete(it, null, null) } }
            persistence.owner.close()
            isolatedRoot.deleteRecursively()
        }
    }

    private fun writeSyntheticDownload(name: String, pdf: ByteArray): Uri {
        val resolver = compose.activity.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Synthetic Downloads provider refused fixture creation")
        try {
            resolver.openOutputStream(uri, "w")!!.use { it.write(pdf) }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            check(resolver.update(uri, values, null, null) == 1)
            return uri
        } catch (failure: Throwable) {
            resolver.delete(uri, null, null)
            throw failure
        }
    }

    private fun waitForDocumentsUi() {
        compose.waitUntil(10_000) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow?.packageName
                ?.toString()?.contains("documentsui", ignoreCase = true) == true
        }
    }

    private fun isDocumentsUi(): Boolean = InstrumentationRegistry.getInstrumentation().uiAutomation
        .rootInActiveWindow?.packageName?.toString()?.contains("documentsui", ignoreCase = true) == true

    private fun chooseDownloads() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val breadcrumb = find(automation.rootInActiveWindow) {
            it.viewIdResourceName == "com.google.android.documentsui:id/breadcrumb_text"
        }
        if (breadcrumb?.text?.toString() == "Downloads") return
        find(automation.rootInActiveWindow) {
            it.contentDescription?.toString()?.contains("Show roots", ignoreCase = true) == true
        }?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        compose.waitUntil(5_000) {
            find(automation.rootInActiveWindow) { it.viewIdResourceName == "com.google.android.documentsui:id/roots_list" } != null
        }
        val downloads = find(automation.rootInActiveWindow) {
            it.viewIdResourceName == "android:id/title" && it.text?.toString() == "Downloads"
        } ?: error("Downloads root absent in DocumentsUI")
        clickNodeOrAncestor(downloads)
        compose.waitUntil(5_000) {
            find(automation.rootInActiveWindow) { it.viewIdResourceName == "com.google.android.documentsui:id/breadcrumb_text" }
                ?.text?.toString() == "Downloads"
        }
    }

    private fun clickDocumentRow(name: String): Boolean {
        val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow ?: return false
        val rows = ArrayList<AccessibilityNodeInfo>()
        fun collect(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.viewIdResourceName == "com.google.android.documentsui:id/item_root" && containsText(node, name)) rows += node
            for (index in 0 until node.childCount) collect(node.getChild(index))
        }
        collect(root)
        return rows.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun clickText(value: String): Boolean {
        val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow ?: return false
        val candidates = root.findAccessibilityNodeInfosByText(value)
        if (candidates.any(::clickNodeOrAncestor)) return true
        val bounds = Rect()
        candidates.firstOrNull()?.getBoundsInScreen(bounds)
        if (bounds.isEmpty) return false
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, bounds.exactCenterX(), bounds.exactCenterY(), 0)
        val up = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, bounds.exactCenterX(), bounds.exactCenterY(), 0)
        return try {
            InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(down, true)
            InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(up, true)
        } finally { down.recycle(); up.recycle() }
    }

    private fun clickNodeOrAncestor(candidate: AccessibilityNodeInfo): Boolean {
        if (candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        var node: AccessibilityNodeInfo? = candidate.parent
        while (node != null) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            node = node.parent
        }
        return false
    }

    private fun containsText(node: AccessibilityNodeInfo, value: String): Boolean {
        if (node.text?.toString() == value || node.contentDescription?.toString() == value) return true
        return (0 until node.childCount).any { containsText(node.getChild(it), value) }
    }

    private fun find(root: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (root == null) return null
        if (predicate(root)) return root
        for (index in 0 until root.childCount) find(root.getChild(index), predicate)?.let { return it }
        return null
    }

    private fun pdfWithSubject(subject: String): ByteArray {
        val escaped = subject.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
        val output = ByteArrayOutputStream()
        output.write("%PDF-1.4\n".toByteArray(StandardCharsets.US_ASCII))
        val offsets = mutableListOf(0)
        listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [] /Count 0 >>",
            "<< /Title (Synthetic) /Subject ($escaped) >>",
        ).forEachIndexed { index, body ->
            offsets += output.size()
            output.write("${index + 1} 0 obj\n$body\nendobj\n".toByteArray(StandardCharsets.US_ASCII))
        }
        val xref = output.size()
        output.write("xref\n0 4\n0000000000 65535 f \n".toByteArray(StandardCharsets.US_ASCII))
        offsets.drop(1).forEach { output.write("%010d 00000 n \n".format(it).toByteArray(StandardCharsets.US_ASCII)) }
        output.write("trailer\n<< /Size 4 /Root 1 0 R /Info 3 0 R >>\nstartxref\n$xref\n%%EOF\n".toByteArray(StandardCharsets.US_ASCII))
        return output.toByteArray()
    }
}

@Composable
private fun CatalogImportEndToEndHost(
    catalog: CatalogService,
    importer: CatalogImportService,
    actorId: Long,
    activity: MainActivity,
) {
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<((CatalogPdfSelection) -> Unit)?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val callback = pending
        pending = null
        if (callback != null) {
            if (uri == null) callback(CatalogPdfSelection.Cancelled)
            else scope.launch {
                val selection = try {
                    withContext(Dispatchers.IO) { CatalogPdfSelection.Ready(activity.readBoundedPdf(uri)) }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) { CatalogPdfSelection.Failed }
                callback(selection)
            }
        }
    }
    CatalogScreen(
        service = catalog,
        permissions = setOf("PRODUCTS:READ", "PRODUCTS:UPDATE"),
        french = false,
        state = CatalogScreenState(),
        pickImage = { it(null) },
        catalogImport = importer,
        actorId = actorId,
        pickCatalogPdf = { _, callback -> pending = callback; picker.launch(arrayOf("application/pdf")) },
    ) { }
}
