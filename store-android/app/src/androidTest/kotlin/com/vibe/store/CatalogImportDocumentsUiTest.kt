package com.vibe.store

import android.net.Uri
import android.graphics.Rect
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.vibe.store.api.BoundedDocumentInput
import com.vibe.store.infrastructure.documents.parser.AndroidPdfSubjectExtractor
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exercises the real DocumentsUI provider selection without opening or mutating STORE's user DB. */
class CatalogImportDocumentsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun documentsUiSelectedPdfReachesIsolatedParserAndStrictCodec() {
        val selectedForCreate = AtomicReference<Uri?>(null)
        val selectedForOpen = AtomicReference<Uri?>(null)
        val fileName = "i10_catalog_${UUID.randomUUID()}.pdf"
        val payload = "STORE_DATA_V1:eyJraW5kIjoic3RvY2tzIiwidmVyc2lvbiI6MSwicHJvZHVjdHMiOlt7Im5hbWUiOiJTeW50aGV0aWMgQ2Fmw6kiLCJoYXNodGFnIjoiI3N5bnRoZXRpYyIsImNhdGVnb3J5IjoiVGVzdCIsImRlc2NyaXB0aW9uIjoiVW50cnVzdGVkIGZpeHR1cmUiLCJwcmljZSI6MTIuMzQwMCwic3RvY2tRdWFudGl0eSI6NCwibWluU3RvY2tUaHJlc2hvbGQiOjF9XX0="

        compose.runOnUiThread {
            compose.activity.setContent {
                PickerHarness(
                    onCreated = { selectedForCreate.set(it) },
                    onOpened = { selectedForOpen.set(it) },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("create-fixture-pdf").performClick()
        waitForDocumentsUi()
        chooseDownloads()
        setFileNameAndSave(fileName)
        compose.waitUntil(10_000) { selectedForCreate.get() != null }
        val created = requireNotNull(selectedForCreate.get())
        val fixtureBytes = pdfWithSubject(payload)
        compose.activity.contentResolver.openOutputStream(created, "wt").use { stream ->
            requireNotNull(stream).write(fixtureBytes)
        }

        compose.onNodeWithTag("open-fixture-pdf").performClick()
        waitForDocumentsUi()
        chooseDownloads()
        compose.waitUntil(10_000) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
                ?.findAccessibilityNodeInfosByText(fileName)?.isNotEmpty() == true
        }
        assertTrue(clickDocumentRow(fileName))
        if (selectedForOpen.get() == null) clickText("Open")
        try { compose.waitUntil(10_000) { selectedForOpen.get() != null } }
        catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
            logPickerTree()
            throw failure
        }
        val opened = requireNotNull(selectedForOpen.get())
        assertEquals(created, opened)

        val selectedBytes = compose.activity.readBoundedPdf(opened).copyBytes()
        assertTrue(selectedBytes.contentEquals(fixtureBytes))
        val subjectBytes = runBlocking {
            AndroidPdfSubjectExtractor(compose.activity).extract(BoundedDocumentInput(selectedBytes))
        }
        val subject = String(subjectBytes, StandardCharsets.UTF_16LE)
        assertEquals(payload, subject)
        val structuredJson = String(Base64.decode(subject.substringAfter(':'), Base64.NO_WRAP), StandardCharsets.UTF_8)
        assertTrue(structuredJson.contains("\"name\":\"Synthetic Café\""))
        assertTrue(structuredJson.contains("\"price\":12.3400"))
        assertTrue(android.provider.DocumentsContract.deleteDocument(compose.activity.contentResolver, created))
    }

    private fun waitForDocumentsUi() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        compose.waitUntil(10_000) {
            automation.rootInActiveWindow?.packageName?.toString()
                ?.contains("documentsui", ignoreCase = true) == true
        }
    }

    private fun chooseDownloads() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val root = automation.rootInActiveWindow
        val breadcrumb = find(root) { it.viewIdResourceName == "com.google.android.documentsui:id/breadcrumb_text" }
        if (breadcrumb?.text?.toString() == "Downloads") return
        val drawer = find(root) { it.contentDescription?.toString()?.contains("Show roots", ignoreCase = true) == true }
        drawer?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        compose.waitUntil(5_000) {
            find(automation.rootInActiveWindow) { it.viewIdResourceName == "com.google.android.documentsui:id/roots_list" } != null
        }
        val roots = find(automation.rootInActiveWindow) { it.viewIdResourceName == "com.google.android.documentsui:id/roots_list" }
        val downloads = find(roots) { it.viewIdResourceName == "android:id/title" && it.text?.toString() == "Downloads" }
            ?: throw AssertionError("Downloads root absent in DocumentsUI")
        assertTrue(clickNodeOrAncestor(downloads))
        compose.waitUntil(5_000) {
            find(automation.rootInActiveWindow) { it.viewIdResourceName == "com.google.android.documentsui:id/breadcrumb_text" }
                ?.text?.toString() == "Downloads"
        }
    }

    private fun logPickerTree() {
        val dump = StringBuilder()
        fun append(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 20) return
            dump.append(" ".repeat(depth)).append(node.text ?: node.contentDescription ?: "")
                .append(" id=").append(node.viewIdResourceName).append(" class=").append(node.className)
                .append(" clickable=").append(node.isClickable).append('\n')
            for (index in 0 until node.childCount) append(node.getChild(index), depth + 1)
        }
        append(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow, 0)
        Log.e("I10_IMPORT_PICKER", dump.toString().take(12_000))
    }

    private fun setFileNameAndSave(fileName: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        compose.waitUntil(5_000) {
            find(automation.rootInActiveWindow) {
                it.className?.toString()?.endsWith("EditText") == true
            } != null
        }
        val field = requireNotNull(find(automation.rootInActiveWindow) {
            it.className?.toString()?.endsWith("EditText") == true
        })
        field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, fileName)
        })
        assertTrue(clickText("Save"))
    }

    private fun clickText(text: String): Boolean {
        val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow ?: return false
        val candidates = root.findAccessibilityNodeInfosByText(text)
        for (candidate in candidates) {
            if (clickNodeOrAncestor(candidate)) return true
        }
        val candidate = candidates.firstOrNull() ?: return false
        val bounds = Rect()
        candidate.getBoundsInScreen(bounds)
        if (!bounds.isEmpty) {
            val x = bounds.exactCenterX()
            val y = bounds.exactCenterY()
            val down = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, x, y, 0)
            val up = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis() + 50, MotionEvent.ACTION_UP, x, y, 0)
            return try {
                val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
                automation.injectInputEvent(down, true)
                automation.injectInputEvent(up, true)
            } finally { down.recycle(); up.recycle() }
        }
        return false
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

    private fun containsText(node: AccessibilityNodeInfo, value: String): Boolean {
        if (node.text?.toString() == value || node.contentDescription?.toString() == value) return true
        for (index in 0 until node.childCount) if (containsText(node.getChild(index), value)) return true
        return false
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

    private fun find(root: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (root == null) return null
        if (predicate(root)) return root
        for (index in 0 until root.childCount) {
            find(root.getChild(index), predicate)?.let { return it }
        }
        return null
    }

    private fun pdfWithSubject(subject: String): ByteArray {
        val escaped = subject.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [] /Count 0 >>",
            "<< /Title (Synthetic) /Subject ($escaped) >>",
        )
        val output = java.io.ByteArrayOutputStream()
        output.write("%PDF-1.4\n".toByteArray(StandardCharsets.US_ASCII))
        val offsets = mutableListOf(0)
        objects.forEachIndexed { index, body ->
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
private fun PickerHarness(onCreated: (Uri?) -> Unit, onOpened: (Uri?) -> Unit) {
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf"), onCreated)
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), onOpened)
    androidx.compose.foundation.layout.Column {
        Button(onClick = { create.launch("synthetic.pdf") }, modifier = Modifier.testTag("create-fixture-pdf")) {
            Text("Create fixture PDF")
        }
        Button(onClick = { open.launch(arrayOf("application/pdf")) }, modifier = Modifier.testTag("open-fixture-pdf")) {
            Text("Open fixture PDF")
        }
    }
}
