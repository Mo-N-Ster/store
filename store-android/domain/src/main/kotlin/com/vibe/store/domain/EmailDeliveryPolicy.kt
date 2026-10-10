package com.vibe.store.domain

import java.time.Instant

data class EmailRetryDecision(val attempts: Int, val status: String, val nextAttemptAt: Instant)

/** Matches the frozen STORE retry rule: five delivery errors, exponential minutes capped at 60.
 * Missing configuration delays work without consuming one of those error attempts. */
object EmailDeliveryPolicy {
    const val MAX_ATTEMPTS = 5
    fun failure(previousAttempts: Int, errorCode: String, now: Instant): EmailRetryDecision {
        require(previousAttempts >= 0)
        val missing = errorCode == "SMTP_NOT_CONFIGURED" || errorCode == "SMTP_SECRET_UNAVAILABLE"
        val attempts = if (missing) previousAttempts else if (previousAttempts == Int.MAX_VALUE) Int.MAX_VALUE else previousAttempts + 1
        val minutes = if (missing) 15L else (1L shl attempts.coerceIn(0, 6)).coerceAtMost(60L)
        return EmailRetryDecision(attempts, if (!missing && attempts >= MAX_ATTEMPTS) "failed" else "pending",
            now.plusSeconds(minutes * 60L))
    }

    fun mayRetry(status: String) = status == "pending" || status == "failed"
}
