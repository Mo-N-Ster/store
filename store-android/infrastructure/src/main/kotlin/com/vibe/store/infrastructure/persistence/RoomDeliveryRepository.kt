package com.vibe.store.infrastructure.persistence

import com.vibe.store.application.delivery.DeliveryRecords
import com.vibe.store.application.delivery.EmailRecord
import java.time.Instant

internal class RoomDeliveryRepository(
    private val dao: StoreDao,
    private val check: suspend (Boolean) -> Unit,
) : DeliveryRecords {
    override suspend fun nextEmailId(): Long { check(true); return dao.nextEmailId() }

    override suspend fun insertEmail(record: EmailRecord) {
        check(true)
        val attachment = record.attachment
        require(record.id > 0 && record.actorId > 0 && record.generation.isNotBlank())
        require(record.recipient.length <= 320 && record.subject.length in 1..500)
        require(record.filename.length <= 180 && record.message.length <= 10_000)
        require(attachment == null || attachment.size <= 25_000_000)
        require(record.status == "pending" && record.attempts == 0)
        dao.email(record.entity())
    }

    override suspend fun email(id: Long): EmailRecord? { check(false); return dao.email(id)?.record() }
    override suspend fun emailPage(actorId: Long, limit: Int, offset: Int): List<EmailRecord> {
        check(false); require(actorId > 0 && limit in 1..100 && offset >= 0)
        return dao.emailPage(actorId, limit, offset).map { it.record() }
    }
    override suspend fun dueEmail(actorId: Long, generation: String, now: Instant): EmailRecord? {
        check(false); require(actorId > 0 && generation.isNotBlank())
        return dao.dueEmail(actorId, generation, now.storeStamp())?.record()
    }
    override suspend fun nextPendingAt(actorId: Long, generation: String): Instant? {
        check(false); require(actorId > 0 && generation.isNotBlank())
        return dao.nextPendingEmailAt(actorId, generation)?.let(Instant::parse)
    }
    override suspend fun updateEmail(record: EmailRecord): Boolean {
        check(true); require(record.id > 0 && record.actorId > 0 && record.generation.isNotBlank())
        val attachment = record.attachment
        val lastError = record.lastError
        require(attachment == null || attachment.size <= 25_000_000)
        require(record.status in setOf("pending", "sending", "sent", "failed"))
        require(record.attempts in 0..5 && (lastError == null || lastError in SAFE_ERRORS))
        return dao.updateEmail(record.entity()) == 1
    }
    override suspend fun recoverInterrupted(actorId: Long, generation: String): Int {
        check(true); require(actorId > 0 && generation.isNotBlank())
        return dao.recoverInterruptedEmails(actorId, generation)
    }

    private fun EmailRecord.entity() = EmailEntity(id, recipient, subject, filename, message, attachment,
        status, attempts, createdAt.storeStamp(), nextAttemptAt?.storeStamp(), lastError,
        sentAt?.storeStamp(), actorId, generation)
    private fun EmailEntity.record() = EmailRecord(id, actorId, generation, recipient, subject, filename, message,
        attachment?.copyOf(), status, attempts, Instant.parse(createdAt), nextAttemptAt?.let(Instant::parse),
        lastError, sentAt?.let(Instant::parse))
    private fun Instant.storeStamp(): String = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withZone(java.time.ZoneOffset.UTC).format(this)

    private companion object {
        val SAFE_ERRORS = setOf("SMTP_TIMEOUT", "SMTP_TLS_FAILURE", "SMTP_AUTH_FAILURE", "SMTP_REJECTED",
            "SMTP_NETWORK_FAILURE", "SMTP_NOT_CONFIGURED", "SMTP_SECRET_UNAVAILABLE", "legacy_sender_unknown")
    }
}
