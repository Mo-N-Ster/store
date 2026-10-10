package com.vibe.store

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.platform.ComposeView
import androidx.test.platform.app.InstrumentationRegistry
import com.vibe.store.api.*
import com.vibe.store.presentation.EmailQueueScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class EmailDeliveryUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun failedQueueEntryShowsSafeStatusAndExplicitRetry() {
        var retryCount = 0
        var scheduled = 0
        val entry = EmailQueueEntry(9, "synthetic@example.invalid", "Synthetic report", "report.pdf",
            EmailQueueStatus.FAILED, 2, Instant.EPOCH, Instant.parse("2026-10-10T12:00:00Z"), "SMTP_TLS_FAILURE")
        val service = object : DeliveryService {
            override suspend fun smtpConfiguration() = SmtpConfiguration("", 587, "", "", SmtpSecurityMode.STARTTLS_REQUIRED, false, false)
            override suspend fun configureSmtp(input: SmtpConfigurationInput) = smtpConfiguration()
            override suspend fun testSmtp(recipient: String) = SmtpProbeResult.CONFIGURATION_REQUIRED
            override suspend fun queueReport(request: EmailReportRequest) = entry
            override suspend fun emailQueue(offset: Int, limit: Int) = listOf(entry)
            override suspend fun retryEmail(id: Long): Boolean { retryCount++; return id == entry.id }
            override suspend fun deliverNextAuthorized() = DeliveryRunResult.NOTHING_DUE
            override suspend fun recoverInterruptedAuthorized() = 0
            override suspend fun nextAuthorizedDeliveryDelayMillis(): Long? = null
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            compose.activity.addContentView(
                ComposeView(compose.activity).apply {
                    this.setContent { EmailQueueScreen(service, french = false, onBack = {}, onSchedule = { scheduled++ }) }
                },
                android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT),
            )
        }

        compose.onNodeWithText("Synthetic report").assertExists()
        compose.onNodeWithText("synthetic@example.invalid · Failed · 2/5").assertExists()
        compose.onNodeWithText("Failure code: SMTP_TLS_FAILURE").assertExists()
        compose.onNodeWithText("Retry").performClick()
        compose.waitUntil(5_000) { retryCount == 1 && scheduled == 1 }
        assertEquals(1, retryCount)
    }
}
