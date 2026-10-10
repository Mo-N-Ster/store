package com.vibe.store

import android.system.ErrnoException
import android.system.OsConstants
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.OutputStream

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
}
