package com.vibe.store

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.Modifier
import com.vibe.store.api.*
import com.vibe.store.presentation.DocumentExportAction
import com.vibe.store.presentation.PdfSaveOutcome
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import java.time.Instant
import java.util.UUID

class DocumentExportUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun show(content: @Composable () -> Unit) {
        compose.runOnUiThread { compose.activity.setContent { content() } }
        compose.waitForIdle()
    }
    private fun request() = DocumentRequest("synthetic-help", DocumentType.HELP, "Synthetic help", Instant.EPOCH,
        DocumentSnapshot.Help("Help", "Synthetic content"))
    private fun rendered() = DocumentResult("synthetic-help", DocumentType.HELP, "help_synthetic.pdf", "application/pdf",
        BoundedDocumentInput("%PDF-synthetic".toByteArray()))

    @Test fun safCancellationIsShownAndDoesNotRetryOrMutateBusinessData() {
        var saveCalls = 0
        show {
            DocumentExportAction(object : DocumentService {
                override suspend fun render(request: DocumentRequest) = rendered()
            }, false, "Save PDF", request = ::request, save = { _, complete -> saveCalls++; complete(PdfSaveOutcome.Cancelled) })
        }
        compose.onNodeWithTag("document-export").performClick()
        compose.waitUntil(5_000) { saveCalls == 1 }
        compose.onNodeWithText("Export cancelled.").assertExists()
    }

    @Test fun destinationWriteFailureIsShownAndDeniedSourceNeverOpensSaf() {
        var saveCalled = false
        show {
            DocumentExportAction(object : DocumentService {
                override suspend fun render(request: DocumentRequest) = rendered()
            }, false, "Save PDF", request = { throw SecurityFailure(SecurityError.FORBIDDEN) }, save = { _, _ -> saveCalled = true })
        }
        compose.onNodeWithTag("document-export").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("document-export-status").fetchSemanticsNodes().isNotEmpty() }
        assertFalse(saveCalled)
        compose.onNodeWithText("Access denied. No PDF was created.").assertExists()
    }

    @Test fun safDestinationWriteErrorHasRetryableStatus() {
        show {
            DocumentExportAction(object : DocumentService {
                override suspend fun render(request: DocumentRequest) = rendered()
            }, true, "Enregistrer PDF", request = ::request, save = { _, complete -> complete(PdfSaveOutcome.Failed()) })
        }
        compose.onNodeWithTag("document-export").performClick()
        compose.onNodeWithText("Échec de l’enregistrement. Réessayez.").assertExists()
    }

    @Test fun createDocumentLaunchesSystemPickerAndCancellationReturnsNull() {
        val selected = AtomicReference<Uri?>(Uri.EMPTY)
        val completed = AtomicReference(false)
        show {
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {
                selected.set(it); completed.set(true)
            }
            Button(onClick = { launcher.launch("i10_synthetic_cancel.pdf") }, modifier = Modifier.testTag("real-saf-cancel")) { androidx.compose.material3.Text("Open picker") }
        }
        compose.onNodeWithTag("real-saf-cancel").performClick()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        compose.waitUntil(10_000) {
            automation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui", ignoreCase = true) == true
        }
        assertTrue(automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
        compose.waitUntil(5_000) { completed.get() }
        assertNull(selected.get())
    }

    @Test fun createDocumentWritesToSelectedSystemProvider() {
        val selected = AtomicReference<Uri?>(null)
        val completed = AtomicReference(false)
        val fileName = "i10_synthetic_provider_${UUID.randomUUID()}.pdf"
        show {
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {
                selected.set(it); completed.set(true)
            }
            Button(onClick = { launcher.launch(fileName) }, modifier = Modifier.testTag("real-saf-save")) { androidx.compose.material3.Text("Choose destination") }
        }
        compose.onNodeWithTag("real-saf-save").performClick()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        compose.waitUntil(10_000) {
            automation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui", ignoreCase = true) == true
        }
        val tree = buildString {
            fun walk(node: AccessibilityNodeInfo, depth: Int) {
                append(" ".repeat(depth)).append(node.text ?: node.contentDescription ?: node.className).append('\n')
                for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it, depth + 1) }
            }
            automation.rootInActiveWindow?.let { walk(it, 0) }
        }
        Log.i("I10_SAF_UI", tree.take(8_000))
        fun find(root: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
            if (root == null) return null
            if (predicate(root)) return root
            for (i in 0 until root.childCount) find(root.getChild(i), predicate)?.let { return it }
            return null
        }
        fun clickByText(value: String): Boolean {
            val root = automation.rootInActiveWindow ?: return false
            val nodes = root.findAccessibilityNodeInfosByText(value)
            val match = nodes.firstOrNull() ?: return false
            var cursor: AccessibilityNodeInfo? = match
            while (cursor != null) {
                if (cursor.isClickable) return cursor.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                cursor = cursor.parent
            }
            return false
        }
        val drawer = find(automation.rootInActiveWindow) {
            it.contentDescription?.toString()?.contains("Show roots", ignoreCase = true) == true
        }
        if (drawer != null) drawer.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        compose.waitUntil(5_000) { automation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Downloads")?.isNotEmpty() == true }
        clickByText("Downloads")
        compose.waitUntil(5_000) { find(automation.rootInActiveWindow) { it.className?.toString()?.endsWith("EditText") == true } != null }
        val nameField = find(automation.rootInActiveWindow) { it.className?.toString()?.endsWith("EditText") == true }
        assertNotNull(nameField)
        nameField!!.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        nameField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, fileName)
        })
        assertTrue(clickByText("Save"))
        compose.waitUntil(10_000) { completed.get() }
        val uri = selected.get()
        assertNotNull(uri)
        compose.activity.contentResolver.openOutputStream(uri!!, "wt").use {
            requireNotNull(it).write("%PDF-synthetic-provider".toByteArray())
        }
        val bytes = compose.activity.contentResolver.openInputStream(uri).use { requireNotNull(it).readBytes() }
        assertTrue(bytes.contentEquals("%PDF-synthetic-provider".toByteArray()))
        assertTrue(DocumentsContract.deleteDocument(compose.activity.contentResolver, uri))
    }
}
