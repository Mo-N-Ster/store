package com.vibe.store.api

import java.time.Instant

enum class SmtpSecurityMode { IMPLICIT_TLS, STARTTLS_REQUIRED }
enum class EmailQueueStatus { PENDING, SENDING, SENT, FAILED }
enum class SmtpProbeResult { ACCEPTED, CONFIGURATION_REQUIRED, REJECTED }

/** Secret values are accepted only on an explicit Owner configuration call and are never returned. */
class SmtpConfiguration(
    val host: String,
    val port: Int,
    val username: String,
    val sender: String,
    val securityMode: SmtpSecurityMode,
    val passwordConfigured: Boolean,
    val passwordAvailable: Boolean,
)

class SmtpConfigurationInput(
    val host: String,
    val port: Int,
    val username: String,
    val sender: String,
    val securityMode: SmtpSecurityMode,
    /** Empty preserves the existing encrypted secret. */
    val replacementPassword: String = "",
    val clearPassword: Boolean = false,
)

class EmailQueueEntry(
    val id: Long,
    val recipient: String,
    val subject: String,
    val fileName: String,
    val status: EmailQueueStatus,
    val attempts: Int,
    val createdAt: Instant,
    val nextAttemptAt: Instant?,
    /** A bounded allowlisted failure code, never a raw server response. */
    val lastErrorCode: String?,
)

class EmailReportRequest(
    val filter: ReportFilter,
    val recipient: String,
    val note: String = "",
    val language: String = "en",
) {
    init { require(language == "fr" || language == "en") }
}

enum class DeliveryRunResult { SENT, NOTHING_DUE, CONFIGURATION_REQUIRED, RETAINED_FOR_RETRY, DENIED }

/** No API returns attachment bytes or SMTP secret material. Queue operations are session-bound. */
interface DeliveryService {
    suspend fun smtpConfiguration(): SmtpConfiguration
    suspend fun configureSmtp(input: SmtpConfigurationInput): SmtpConfiguration
    suspend fun testSmtp(recipient: String): SmtpProbeResult
    suspend fun queueReport(request: EmailReportRequest): EmailQueueEntry
    suspend fun emailQueue(offset: Int = 0, limit: Int = 50): List<EmailQueueEntry>
    suspend fun retryEmail(id: Long): Boolean
    /** Called by the unique internal Worker; revalidates the current session and rights. */
    suspend fun deliverNextAuthorized(): DeliveryRunResult
    suspend fun recoverInterruptedAuthorized(): Int
    /** Milliseconds until the oldest pending entry is due, or null when the authorized queue is empty. */
    suspend fun nextAuthorizedDeliveryDelayMillis(): Long?
}
