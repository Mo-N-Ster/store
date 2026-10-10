package com.vibe.store

import android.system.ErrnoException
import android.system.OsConstants
import android.content.ComponentName
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.OutputStream
import java.io.File
import java.util.UUID

/** Synthetic SAF-provider failures; avoids revoking real data or exhausting emulator storage. */
class SafProviderFailureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun revokedSelectedUriFailsClosedBeforeParsing() {
        val failure = runCatching {
            compose.activity.readBoundedPdfFrom {
                throw SecurityException("synthetic revoked SAF read grant")
            }
        }.exceptionOrNull()

        assertTrue(failure is SecurityException)
    }

    @Test fun providerStorageFullReturnsFailureAndClosesDestination() {
        var closed = false
        val destination = object : OutputStream() {
            override fun write(value: Int) {
                throw ErrnoException("synthetic SAF write", OsConstants.ENOSPC)
            }
            override fun close() { closed = true }
        }

        val result = compose.activity.writePdfDestination(byteArrayOf(1, 2, 3)) { destination }

        assertEquals(com.vibe.store.presentation.PdfSaveOutcome.Failed(), result)
        assertTrue("provider stream must close after ENOSPC", closed)
    }

    @Test fun shareProviderIsPrivateReadOnlyAndLimitedToSyntheticCacheDocuments() {
        val activity = compose.activity
        val provider = activity.packageManager.getProviderInfo(
            ComponentName(activity, FileProvider::class.java), android.content.pm.PackageManager.GET_META_DATA)
        assertEquals(false, provider.exported)
        assertEquals(true, provider.grantUriPermissions)

        val directory = File(activity.cacheDir, "shared-documents")
        assertTrue(directory.isDirectory || directory.mkdirs())
        val file = File(directory, "store-document-${UUID.randomUUID()}.pdf")
        val payload = "%PDF-synthetic-share".toByteArray()
        try {
            file.writeBytes(payload)
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.documents", file)
            assertEquals("application/pdf", activity.contentResolver.getType(uri))
            assertArrayEquals(payload, activity.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
            val outOfScope = File(activity.filesDir, "not-shared.pdf").apply { writeBytes(payload) }
            try {
                try { FileProvider.getUriForFile(activity, "${activity.packageName}.documents", outOfScope); throw AssertionError("private files must not be exposed") }
                catch (_: IllegalArgumentException) { }
            } finally { outOfScope.delete() }
        } finally { file.delete() }
    }

    @Test fun shellCallerWithoutGrantCannotReadSharedDocumentUri() {
        val activity = compose.activity
        val directory = File(activity.cacheDir, "shared-documents").apply { mkdirs() }
        val file = File(directory, "store-document-${UUID.randomUUID()}.pdf")
        val payload = "synthetic-private-share-payload".toByteArray()
        try {
            file.writeBytes(payload)
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.documents", file)
            val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("content read --uri $uri")
            val output = ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
            assertTrue("A distinct shell UID must not receive bytes without a temporary URI grant", !output.contentEquals(payload))
        } finally { file.delete() }
    }
}
