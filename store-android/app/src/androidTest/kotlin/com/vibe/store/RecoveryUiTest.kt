package com.vibe.store

import android.net.Uri
import android.os.Bundle
import android.graphics.Rect
import android.graphics.Bitmap
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import android.os.IBinder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vibe.store.api.*
import com.vibe.store.presentation.RecoveryPanel
import com.vibe.store.presentation.SpatialPanel
import com.vibe.store.presentation.SpatialTheme
import com.vibe.store.infrastructure.persistence.I12RecoveryUiFixtureService
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecoveryUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun backupAndConfirmedRestoreUseExplicitSelections() {
        val backups = AtomicInteger()
        val inspections = AtomicInteger()
        val restores = AtomicInteger()
        val finished = AtomicInteger()
        val bytes = byteArrayOf(8, 6, 7)
        val service = object : RecoveryService {
            override suspend fun createBackup(target: RecoveryTarget): RecoveryReceipt {
                target.openOutputStream()!!.use { it.write(bytes) }; backups.incrementAndGet()
                return RecoveryReceipt("synthetic.storebackup", bytes.size.toLong(), "0".repeat(64), false)
            }
            override suspend fun inspect(source: RecoverySource): RecoveryPreview {
                assertEquals(bytes.size.toLong(), source.openInputStream()!!.use { it.readBytes().size.toLong() })
                inspections.incrementAndGet()
                return RecoveryPreview("3.0", 3, "2026-10-10T10:00:00Z",
                    listOf(RecoveryEntryView("database/store.db", 3, "0".repeat(64))) +
                        (1..60).map { RecoveryEntryView("media/entry-$it.webp", it.toLong(), "0".repeat(64)) }, 1833)
            }
            override suspend fun restore(source: RecoverySource, confirmed: Boolean) {
                check(confirmed)
                restores.incrementAndGet()
            }
            override suspend fun reset(password: String, confirmed: Boolean) = error("Unexpected reset")
        }
        val source = object : RecoverySource { override fun openInputStream() = ByteArrayInputStream(bytes) }
        val target = object : RecoveryTarget { override fun openOutputStream() = ByteArrayOutputStream() }
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(true) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                SpatialPanel {
                    RecoveryPanel(service, PublicIdentity(7, "owner", "Test", "Owner", "owner", emptySet()), true,
                        pickDestination = { it(target) }, pickSource = { it(source) }, onRestored = { finished.incrementAndGet() })
                }
            }
        } } }

        // Retain an actual rendered synthetic recovery screen for the current
        // emulator width. I12's evidence matrix runs this method at all three
        // contractual width classes and restores the AVD display settings.
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("i12-create-backup").assertIsDisplayed() }.isSuccess
        }
        compose.waitForIdle()
        val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val widthDp = context.resources.configuration.screenWidthDp
        val evidenceDir = requireNotNull(context.getExternalFilesDir("i12-evidence"))
        check(evidenceDir.exists() || evidenceDir.mkdirs())
        FileOutputStream(File(evidenceDir, "recovery-panel-${widthDp}dp.png")).use {
            check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        screenshot.recycle()

        compose.onNodeWithTag("i12-create-backup").performClick()
        compose.waitUntil(5_000) { backups.get() == 1 }
        compose.onNodeWithTag("i12-select-restore").performClick()
        compose.waitUntil(5_000) { inspections.get() == 1 }
        compose.onNodeWithTag("i12-preview").assertExists()
        compose.onNodeWithText("Aperçu limité aux 10 premières entrées sur 61.").assertExists()
        compose.onNodeWithText("media/entry-10.webp • 10 B").assertDoesNotExist()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("i12-restore-confirm").assertIsEnabled() }.isSuccess
        }
        compose.onNodeWithTag("i12-restore-confirm").performScrollTo().performClick()
        compose.onNodeWithTag("i12-confirm-restore-dialog").performClick()
        compose.waitUntil(5_000) { restores.get() == 1 && finished.get() == 1 }
    }

    @Test fun failedSecondArchiveInspectionClearsPriorPreviewAndSelection() {
        val inspections = AtomicInteger()
        val selections = AtomicInteger()
        val valid = object : RecoverySource { override fun openInputStream() = ByteArrayInputStream(byteArrayOf(1)) }
        val invalid = object : RecoverySource { override fun openInputStream() = ByteArrayInputStream(byteArrayOf(2)) }
        val service = object : RecoveryService {
            override suspend fun createBackup(target: RecoveryTarget) = error("Unexpected backup")
            override suspend fun inspect(source: RecoverySource): RecoveryPreview {
                inspections.incrementAndGet()
                if (source === invalid) throw RecoveryFailure(RecoveryError.INVALID_ARCHIVE)
                return RecoveryPreview("3.0", 3, "2026-10-10T10:00:00Z",
                    listOf(RecoveryEntryView("database/store.db", 1, "0".repeat(64))), 1)
            }
            override suspend fun restore(source: RecoverySource, confirmed: Boolean) = error("Stale source must not be restorable")
            override suspend fun reset(password: String, confirmed: Boolean) = error("Unexpected reset")
        }
        compose.runOnUiThread { compose.activity.setContent {
            RecoveryPanel(service, PublicIdentity(7, "owner", "Test", "Owner", "owner", emptySet()), false,
                pickDestination = { it(null) },
                pickSource = { it(if (selections.incrementAndGet() == 1) valid else invalid) },
                onRestored = {})
        } }

        compose.onNodeWithTag("i12-select-restore").performClick()
        compose.waitUntil(5_000) { inspections.get() == 1 }
        compose.onNodeWithTag("i12-preview").assertExists()
        compose.onNodeWithTag("i12-select-restore").performClick()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithText("Archive is invalid or unavailable.").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithTag("i12-preview").assertDoesNotExist()
        compose.onNodeWithTag("i12-restore-confirm").assertDoesNotExist()
    }

    @Test fun recoveryPanelExportsThroughRealDocumentsUiAndProvider() {
        val bytes = "STORE synthetic archive provider test".toByteArray()
        val fileName = "i12-provider-${java.util.UUID.randomUUID()}.storebackup"
        val saved = AtomicReference<Uri?>(null)
        val pending = AtomicReference<((RecoveryTarget?) -> Unit)?>(null)
        val exports = AtomicInteger()
        val service = object : RecoveryService {
            override suspend fun createBackup(target: RecoveryTarget): RecoveryReceipt {
                target.openOutputStream()!!.use { it.write(bytes) }
                exports.incrementAndGet()
                return RecoveryReceipt(fileName, bytes.size.toLong(), "0".repeat(64), false)
            }
            override suspend fun inspect(source: RecoverySource): RecoveryPreview = error("Unexpected inspect")
            override suspend fun restore(source: RecoverySource, confirmed: Boolean) = error("Unexpected restore")
            override suspend fun reset(password: String, confirmed: Boolean) = error("Unexpected reset")
        }
        compose.runOnUiThread { compose.activity.setContent {
            val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
                val callback = pending.getAndSet(null)
                saved.set(it)
                callback?.invoke(it?.let { uri -> object : RecoveryTarget {
                    override fun openOutputStream() = compose.activity.contentResolver.openOutputStream(uri, "wt")
                } })
            }
            RecoveryPanel(service, PublicIdentity(7, "owner", "Test", "Owner", "owner", emptySet()), false,
                pickDestination = { callback -> pending.set(callback); create.launch(fileName) },
                pickSource = { it(null) }, onRestored = {})
        } }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun find(root: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
            if (root == null) return null
            if (predicate(root)) return root
            for (i in 0 until root.childCount) find(root.getChild(i), predicate)?.let { return it }
            return null
        }
        fun clickText(value: String): Boolean {
            val root = automation.rootInActiveWindow ?: return false
            if (!root.packageName.toString().contains("documentsui", true)) return false
            val nodes = root.findAccessibilityNodeInfosByText(value) ?: return false
            for (node in nodes) {
                var current: AccessibilityNodeInfo? = node
                while (current != null) {
                    if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                    current = current.parent
                }
                if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            }
            val target = nodes.firstOrNull() ?: return false
            val bounds = Rect()
            target.getBoundsInScreen(bounds)
            if (bounds.isEmpty) return false
            val x = bounds.exactCenterX()
            val y = bounds.exactCenterY()
            val downTime = android.os.SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
            val up = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_UP, x, y, 0)
            return try {
                automation.injectInputEvent(down, true) && automation.injectInputEvent(up, true)
            } finally {
                down.recycle()
                up.recycle()
            }
        }

        compose.onNodeWithTag("i12-create-backup").performClick()
        compose.waitUntil(10_000) { automation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui", true) == true }
        assertTrue(automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
        compose.waitUntil(10_000) { pending.get() == null && automation.rootInActiveWindow?.packageName?.toString()?.contains("com.vibe.store", true) == true }
        assertEquals(0, exports.get())
        assertEquals(null, saved.get())

        compose.onNodeWithTag("i12-create-backup").performClick()
        compose.waitUntil(10_000) { automation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui", true) == true }
        find(automation.rootInActiveWindow) { it.contentDescription?.toString()?.contains("Show roots", true) == true }
            ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        compose.waitUntil(5_000) { automation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Downloads")?.isNotEmpty() == true }
        assertTrue(clickText("Downloads"))
        compose.waitUntil(5_000) { find(automation.rootInActiveWindow) { it.className?.toString()?.endsWith("EditText") == true } != null }
        val field = find(automation.rootInActiveWindow) { it.className?.toString()?.endsWith("EditText") == true }
        requireNotNull(field).performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, fileName)
        })
        assertTrue(clickText("Save"))
        compose.waitUntil(10_000) { exports.get() == 1 && pending.get() == null }
        val uri = requireNotNull(saved.get())
        assertTrue(compose.activity.contentResolver.openInputStream(uri).use { requireNotNull(it).readBytes() }.contentEquals(bytes))
        check(DocumentsContract.deleteDocument(compose.activity.contentResolver, uri)) { "Synthetic DocumentsUI file cleanup failed" }
    }

    @Test fun productionRecoveryAuthorityRoundTripsSyntheticRoomThroughDocumentsUi() {
        val fixtureId = "ui-${java.util.UUID.randomUUID()}"
        val connected = CountDownLatch(1)
        val fixture = AtomicReference<I12RecoveryUiFixtureService.FixtureBinder?>(null)
        val connection = object : android.content.ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                fixture.set(binder as I12RecoveryUiFixtureService.FixtureBinder)
                connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) { fixture.set(null) }
        }
        val intent = Intent(compose.activity, I12RecoveryUiFixtureService::class.java)
            .putExtra(I12RecoveryUiFixtureService.EXTRA_ID, fixtureId)
        assertTrue(compose.activity.bindService(intent, connection, Context.BIND_AUTO_CREATE))
        assertTrue("Synthetic recovery fixture failed to bind", connected.await(20, TimeUnit.SECONDS))

        val pendingTarget = AtomicReference<((RecoveryTarget?) -> Unit)?>(null)
        val pendingSource = AtomicReference<((RecoverySource?) -> Unit)?>(null)
        val saved = AtomicReference<Uri?>(null)
        val selected = AtomicReference<Uri?>(null)
        val sourceReturned = AtomicBoolean(false)
        val restored = AtomicInteger()
        val fileName = "i12-authority-${java.util.UUID.randomUUID()}.storebackup"
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation

        fun find(root: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
            if (root == null) return null
            if (predicate(root)) return root
            for (index in 0 until root.childCount) find(root.getChild(index), predicate)?.let { return it }
            return null
        }
        fun clickText(value: String): Boolean {
            val root = automation.rootInActiveWindow ?: return false
            if (!root.packageName.toString().contains("documentsui", true)) return false
            val node = (root.findAccessibilityNodeInfosByText(value) ?: return false)
                .firstOrNull { it.text?.toString()?.equals(value, ignoreCase = true) == true } ?: return false
            var current: AccessibilityNodeInfo? = node
            while (current != null) {
                if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                current = current.parent
            }
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.isEmpty) return false
            val now = android.os.SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, bounds.exactCenterX(), bounds.exactCenterY(), 0)
            val up = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, bounds.exactCenterX(), bounds.exactCenterY(), 0)
            return try { automation.injectInputEvent(down, true) && automation.injectInputEvent(up, true) }
            finally { down.recycle(); up.recycle() }
        }
        fun hasExactText(value: String): Boolean =
            (automation.rootInActiveWindow?.findAccessibilityNodeInfosByText(value) ?: emptyList())
                .any { it.text?.toString()?.equals(value, ignoreCase = true) == true }
        fun describeRoot(): String {
            val root = automation.rootInActiveWindow ?: return "no accessibility root"
            val parts = mutableListOf<String>()
            fun walk(node: AccessibilityNodeInfo?) {
                if (node == null) return
                val text = node.text?.toString().orEmpty()
                val description = node.contentDescription?.toString().orEmpty()
                if (text.isNotBlank() || description.isNotBlank() || node.isClickable) {
                    parts += "${node.className}#${node.viewIdResourceName} text=$text desc=$description click=${node.isClickable} selected=${node.isSelected}"
                }
                for (index in 0 until node.childCount) walk(node.getChild(index))
            }
            walk(root)
            return "${root.packageName}: ${parts.joinToString(" | ").take(6000)}"
        }
        fun chooseDownloads() {
            compose.waitUntil(15_000) { automation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui", true) == true }
            find(automation.rootInActiveWindow) { it.contentDescription?.toString()?.contains("Show roots", true) == true }
                ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            compose.waitUntil(10_000) { hasExactText("Downloads") }
            assertTrue("Downloads was not available in DocumentsUI", clickText("Downloads"))
        }

        try {
            val backend = requireNotNull(fixture.get())
            compose.runOnUiThread { compose.activity.setContent {
                val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
                    saved.set(uri)
                    pendingTarget.getAndSet(null)?.invoke(uri?.let { chosen -> object : RecoveryTarget {
                        override fun openOutputStream() = compose.activity.contentResolver.openOutputStream(chosen, "wt")
                    } })
                }
                val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    selected.set(uri)
                    sourceReturned.set(true)
                    pendingSource.getAndSet(null)?.invoke(uri?.let { chosen -> object : RecoverySource {
                        override fun openInputStream() = compose.activity.contentResolver.openInputStream(chosen)
                    } })
                }
                RecoveryPanel(
                    service = backend.recoveryService,
                    actor = PublicIdentity(1, "owner", "Synthetic", "Owner", "synthetic-owner", emptySet()),
                    french = false,
                    pickDestination = { callback -> pendingTarget.set(callback); create.launch(fileName) },
                    pickSource = { callback -> pendingSource.set(callback); open.launch(arrayOf("*/*")) },
                    onRestored = { restored.incrementAndGet() },
                )
            } }

            compose.onNodeWithTag("i12-create-backup").performClick()
            chooseDownloads()
            compose.waitUntil(10_000) { find(automation.rootInActiveWindow) { it.className?.toString()?.endsWith("EditText") == true } != null }
            val nameField = find(automation.rootInActiveWindow) { it.className?.toString()?.endsWith("EditText") == true }
            requireNotNull(nameField).performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            nameField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, fileName)
            })
            assertTrue("DocumentsUI Save action was unavailable", clickText("Save"))
            compose.waitUntil(15_000) { saved.get() != null }
            val backupUri = requireNotNull(saved.get())
            compose.waitUntil(20_000) { pendingTarget.get() == null }
            // Picker return only completes target selection; the backup write is a separate coroutine.
            compose.waitUntil(30_000) {
                runCatching {
                    compose.onNodeWithText("Backup written to the selected destination; provider durability is not verified.").assertExists()
                }.isSuccess
            }
            val zipHeader = compose.activity.contentResolver.openInputStream(backupUri).use { requireNotNull(it).readNBytes(2) }
            assertTrue("Backup provider output was not ZIP", zipHeader.contentEquals(byteArrayOf('P'.code.toByte(), 'K'.code.toByte())))

            backend.addCurrentOnlyProduct()
            compose.onNodeWithTag("i12-select-restore").performClick()
            chooseDownloads()
            compose.waitUntil(15_000) {
                sourceReturned.get() || (hasExactText(fileName) && clickText(fileName))
            }
            compose.waitUntil(20_000) {
                runCatching { compose.onNodeWithTag("i12-preview").assertExists() }.isSuccess ||
                    runCatching { compose.onNodeWithText("Archive is invalid or unavailable.").assertExists() }.isSuccess
            }
            val previewVisible = runCatching { compose.onNodeWithTag("i12-preview").assertExists() }.isSuccess
            assertTrue("DocumentsUI did not return a selected SAF URI", sourceReturned.get())
            if (!previewVisible) {
                val selectedUri = requireNotNull(selected.get()) { "DocumentsUI returned no selected URI" }
                val directResult = runCatching {
                    runBlocking {
                        backend.recoveryService.inspect(object : RecoverySource {
                            override fun openInputStream() = compose.activity.contentResolver.openInputStream(selectedUri)
                        })
                    }
                }
                val directDescription = directResult.exceptionOrNull()?.javaClass?.simpleName ?: "direct validation succeeded"
                throw AssertionError("Compose showed archive rejection; same selected SAF URI inspection=$directDescription")
            }
            compose.onNodeWithTag("i12-restore-confirm").performClick()
            compose.onNodeWithTag("i12-confirm-restore-dialog").performClick()
            compose.waitUntil(30_000) { restored.get() == 1 }

            val names = backend.currentProductNames().toSet()
            assertTrue("Archived synthetic product missing after restore", "Archived synthetic product" in names)
            assertTrue("Post-backup catalog row survived restore", "Current-only synthetic product" !in names)
            assertTrue("Could not remove exact synthetic provider document",
                DocumentsContract.deleteDocument(compose.activity.contentResolver, backupUri))
        } finally {
            runCatching { saved.get()?.let { DocumentsContract.deleteDocument(compose.activity.contentResolver, it) } }
            compose.activity.unbindService(connection)
        }
    }
}
