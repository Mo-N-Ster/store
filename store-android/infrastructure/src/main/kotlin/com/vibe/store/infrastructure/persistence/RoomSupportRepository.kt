package com.vibe.store.infrastructure.persistence

import com.vibe.store.api.*
import com.vibe.store.application.security.*

/** Only bounded, static Room queries. No general SQL or table names cross this layer. */
internal class RoomSupportRepository(
    private val dao: StoreDao,
    private val check: suspend (Boolean) -> Unit,
) : SupportRepository {
    private suspend fun map(value: MessageEntity, actor: Long): StoredMessage {
        val name = value.senderId?.let { dao.account(it)?.username } ?: "—"
        val receipt = dao.messageReceipt(value.id, actor)
        return StoredMessage(value.id, value.senderId, name, value.recipientId, value.recipientType,
            value.subject, value.content, value.createdAt, receipt != null && receipt.readAt != "PENDING")
    }
    override suspend fun messageFor(actor: Long, id: Long): StoredMessage? {
        check(false)
        return dao.visibleMessage(id, actor)?.let { map(it, actor) }
    }
    override suspend fun messageByRequest(key: String): StoredMessage? {
        check(false)
        val value = dao.messageByRequest(key) ?: return null
        // Only the authority may return a request-key match; its sender must still be checked.
        return StoredMessage(value.id, value.senderId, "", value.recipientId, value.recipientType,
            value.subject, value.content, value.createdAt, false)
    }
    override suspend fun messagesFor(actor: Long, unreadOnly: Boolean, limit: Int, offset: Int): List<StoredMessage> {
        check(false)
        return dao.visibleMessagePage(actor, unreadOnly, limit, offset).map { map(it, actor) }
    }
    override suspend fun insertMessage(sender: Long, recipient: Long?, broadcast: Boolean,
        subject: String, content: String, requestId: String, stamp: String): Long {
        check(true)
        val id = dao.nextMessageId()
        dao.message(MessageEntity(id, sender, if (broadcast) "ALL" else "USER", recipient,
            subject, content, "CHAT", false, stamp, requestId))
        return id
    }
    override suspend fun addRecipient(messageId: Long, recipientId: Long) {
        check(true); dao.putMessageReceipt(MessageReadEntity(messageId, recipientId, "PENDING"))
    }
    override suspend fun markMessageRead(messageId: Long, actor: Long, stamp: String) {
        check(true)
        // Restrict update to an existing recipient record, so sent items can never
        // acquire a forged read receipt and future broadcast recipients cannot appear.
        check(dao.markMessageReceipt(messageId, actor, stamp) == 1) { "Recipient receipt missing" }
    }
    override suspend fun hideMessage(messageId: Long, actor: Long, stamp: String) {
        check(true); dao.putMessageDeletion(MessageDeletionEntity(messageId, actor, stamp))
    }
    override suspend fun alerts(actor: Long, limit: Int, offset: Int): List<LocalAlert> {
        check(false)
        return dao.supportAlerts(limit, offset).map { LocalAlert(it.id, it.type, it.message,
            it.createdAt, it.resolvedAt, false) }
    }
    override suspend fun alertExists(id: Long): Boolean {
        check(false); return dao.supportAlertExists(id)
    }
    override suspend fun resolveAlert(id: Long, stamp: String): Boolean {
        check(true)
        return dao.supportResolveAlert(id, stamp) == 1
    }
    override suspend fun audit(filter: AuditFilter, limit: Int): List<AuditEntry> {
        check(false)
        return dao.supportAudit(filter.action.trim(), filter.entity.trim(), filter.outcome.uppercase(),
            filter.fromDate, filter.toDate, filter.actorId, limit, filter.offset).map {
            AuditEntry(it.id, it.createdAt, it.userId, it.responsibleId, it.responsibleName,
                it.action, it.entity, it.entityId, it.outcome, it.cashReference,
                it.cashAmount?.toPlainString(), it.cashCurrency)
        }
    }
    override suspend fun counts(): Triple<Int, Int, Int> {
        check(false)
        return Triple(dao.supportUserCount(), dao.supportMessageCount(), dao.supportEmailPendingCount())
    }
}
