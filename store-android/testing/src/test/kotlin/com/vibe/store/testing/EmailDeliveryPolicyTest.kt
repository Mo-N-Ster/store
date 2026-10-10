package com.vibe.store.testing

import com.vibe.store.domain.EmailDeliveryPolicy
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class EmailDeliveryPolicyTest {
    private val now = Instant.parse("2026-10-10T12:00:00Z")

    @Test fun missingConfigurationDoesNotConsumeAttemptsAndWaitsFifteenMinutes() {
        val result = EmailDeliveryPolicy.failure(3, "SMTP_NOT_CONFIGURED", now)
        assertEquals(3, result.attempts)
        assertEquals("pending", result.status)
        assertEquals(now.plusSeconds(900), result.nextAttemptAt)
    }

    @Test fun ordinaryFailuresUseCappedExponentialBackoffAndFailAtFive() {
        val first = EmailDeliveryPolicy.failure(0, "SMTP_TIMEOUT", now)
        assertEquals(1, first.attempts)
        assertEquals(now.plusSeconds(120), first.nextAttemptAt)
        val fifth = EmailDeliveryPolicy.failure(4, "SMTP_NETWORK_FAILURE", now)
        assertEquals(5, fifth.attempts)
        assertEquals("failed", fifth.status)
        assertEquals(now.plusSeconds(1920), fifth.nextAttemptAt)
    }

    @Test fun veryLargeAttemptCounterCannotOverflowBackoff() {
        val result = EmailDeliveryPolicy.failure(Int.MAX_VALUE, "SMTP_TIMEOUT", now)
        assertEquals(Int.MAX_VALUE, result.attempts)
        assertEquals(now.plusSeconds(3600), result.nextAttemptAt)
    }
}
