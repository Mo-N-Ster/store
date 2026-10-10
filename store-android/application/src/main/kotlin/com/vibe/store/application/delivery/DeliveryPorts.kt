package com.vibe.store.application.delivery

import java.time.Instant

data class EmailRecord(
    val id: Long,
    val actorId: Long,
    val generation: String,
    val recipient: String,
    val subject: String,
    val filename: String,
    val message: String,
    val attachment: ByteArray?,
    val status: String,
    val attempts: Int,
    val createdAt: Instant,
    val nextAttemptAt: Instant?,
    val lastError: String?,
    val sentAt: Instant?,
)

interface DeliveryRecords {
    suspend fun nextEmailId(): Long
    suspend fun insertEmail(record: EmailRecord)
    suspend fun email(id: Long): EmailRecord?
    suspend fun emailPage(actorId: Long, limit: Int, offset: Int): List<EmailRecord>
    suspend fun dueEmail(actorId: Long, generation: String, now: Instant): EmailRecord?
    suspend fun nextPendingAt(actorId: Long, generation: String): Instant?
    suspend fun updateEmail(record: EmailRecord): Boolean
    suspend fun recoverInterrupted(actorId: Long, generation: String): Int
}

data class SmtpSettingsRecord(
    val host: String,
    val port: Int,
    val username: String,
    val sender: String,
    val implicitTls: Boolean,
    val secretEnvelope: String?,
)

interface SmtpTransport {
    /** Throws only a bounded internal error code; implementations must not expose server responses. */
    suspend fun test(settings: SmtpSettingsRecord, secret: CharArray, recipient: String)
    suspend fun send(settings: SmtpSettingsRecord, secret: CharArray, email: EmailRecord)
}

class SmtpTransportFailure(val code: String) : Exception(code) {
    init { require(code in setOf("SMTP_TIMEOUT", "SMTP_TLS_FAILURE", "SMTP_AUTH_FAILURE", "SMTP_REJECTED", "SMTP_NETWORK_FAILURE")) }
}
