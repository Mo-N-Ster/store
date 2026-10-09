package com.vibe.store.application.security

import com.vibe.store.api.*
import com.vibe.store.application.persistence.*
import java.text.SimpleDateFormat
import java.util.*

/** I08 local-only operations. Every call authorizes against the current identity inside its lease. */
class SupportAuthority(
    private val identity: IdentityAuthority,
    private val clock: () -> Long = System::currentTimeMillis,
    private val storage: () -> Pair<Long?, Long?> = { null to null },
) : SupportService {
    private fun deny(): Nothing = throw SecurityFailure(SecurityError.FORBIDDEN)
    private fun invalid(): Nothing = throw SecurityFailure(SecurityError.INVALID_INPUT)
    private fun conflict(): Nothing = throw SecurityFailure(SecurityError.CONFLICT)
    private fun stamp(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(clock()))
    private fun checkOffset(value: Int) { if (value !in 0..10_000) invalid() }
    private fun message(value: StoredMessage, actor: Long) = LocalMessage(
        value.id, value.senderId ?: 0, value.senderName, value.recipientId,
        value.recipientType == "ALL", value.subject, value.content, value.createdAt,
        value.senderId == actor, value.read,
    )
    private suspend fun ReadRepositories.requireAdmin(actor: Long, right: String) {
        val account = security.account(actor) ?: deny()
        if (account.role != "owner") deny()
        val inherited = security.inherited(actor)
        if (right !in inherited || right in security.denied(actor)) deny()
    }

    override suspend fun recipients(): List<SupportAccount> = identity.sessionRead { actor ->
        security.accounts().asSequence().filter { it.active &&
            (it.employment == null || it.employment in setOf("ACTIVE", "ABSENT")) && it.id != actor }
            .sortedBy { it.username }.take(500)
            .map { SupportAccount(it.id, it.username, "${it.firstName} ${it.lastName}", it.role) }.toList()
    }
    override suspend fun messages(offset: Int, unreadOnly: Boolean): List<LocalMessage> {
        checkOffset(offset)
        return identity.sessionRead { actor ->
            support.messagesFor(actor, unreadOnly, 40, offset).map { message(it, actor) }
        }
    }
    override suspend fun send(draft: MessageDraft): LocalMessage {
        if (draft.subject.isBlank() || draft.subject.length > 200 || draft.content.isBlank() || draft.content.length > 10_000 ||
            draft.subject.any { it == '\u0000' } || draft.content.any { it == '\u0000' } ||
            !Regex("[0-9a-fA-F-]{36}").matches(draft.requestId) ||
            runCatching { UUID.fromString(draft.requestId) }.getOrNull()?.toString() != draft.requestId.lowercase(Locale.ROOT) ||
            draft.broadcast == (draft.recipientId != null)) invalid()
        return identity.sessionWrite { actor ->
            if (draft.broadcast) requireAdmin(actor, "ADMINISTRATION:UPDATE")
            else {
                val recipient = security.account(draft.recipientId!!) ?: invalid()
                if (recipient.id == actor || !recipient.active ||
                    recipient.employment != null && recipient.employment !in setOf("ACTIVE", "ABSENT")) invalid()
            }
            val existing = support.messageByRequest(draft.requestId)
            if (existing != null) {
                if (existing.senderId != actor || existing.recipientId != draft.recipientId ||
                    (existing.recipientType == "ALL") != draft.broadcast ||
                    existing.subject != draft.subject || existing.content != draft.content) conflict()
                val visible = support.messageFor(actor, existing.id) ?: conflict()
                return@sessionWrite message(visible, actor)
            }
            val at = stamp()
            val id = support.insertMessage(actor, draft.recipientId, draft.broadcast,
                draft.subject, draft.content, draft.requestId, at)
            if (draft.broadcast) {
                security.accounts().filter { it.id != actor && it.active &&
                    (it.employment == null || it.employment in setOf("ACTIVE", "ABSENT")) }
                    .forEach { support.addRecipient(id, it.id) }
            } else support.addRecipient(id, draft.recipientId!!)
            message(support.messageFor(actor, id) ?: error("New message invisible to sender"), actor)
        }
    }
    override suspend fun markRead(id: Long): Unit = identity.sessionWrite { actor ->
        val target = support.messageFor(actor, id) ?: deny()
        if (target.senderId == actor) deny()
        support.markMessageRead(id, actor, stamp())
    }
    override suspend fun hideMessage(id: Long): Unit = identity.sessionWrite { actor ->
        support.messageFor(actor, id) ?: deny()
        support.hideMessage(id, actor, stamp())
    }
    override suspend fun alerts(offset: Int): List<LocalAlert> {
        checkOffset(offset)
        return identity.sessionRead { actor ->
            requireAdmin(actor, "ADMINISTRATION:READ")
            support.alerts(actor, 40, offset).map {
                it.copy(read = settings.value("i08.alert.read.$actor.${it.id}") == "true")
            }
        }
    }
    override suspend fun markAlertRead(id: Long): Unit = identity.sessionWrite { actor ->
        requireAdmin(actor, "ADMINISTRATION:READ")
        if (!support.alertExists(id)) deny()
        settings.put("i08.alert.read.$actor.$id", "true")
    }
    override suspend fun resolveAlert(id: Long): Unit = identity.sessionWrite { actor ->
        requireAdmin(actor, "ADMINISTRATION:UPDATE")
        if (!support.resolveAlert(id, stamp())) deny()
        security.appendAudit(SecurityAudit(actor, actor, "alert_resolved", "notification", id.toString(), true, stamp()))
    }
    override suspend fun audit(filter: AuditFilter): List<AuditEntry> {
        checkOffset(filter.offset)
        if (filter.action.length > 100 || filter.entity.length > 100 ||
            filter.outcome !in setOf("", "success", "failure") ||
            listOf(filter.fromDate, filter.toDate).any { it.isNotEmpty() && !Regex("\\d{4}-\\d{2}-\\d{2}").matches(it) } ||
            filter.actorId?.let { it <= 0 } == true || filter.fromDate > filter.toDate && filter.toDate.isNotEmpty()) invalid()
        return identity.sessionRead { actor ->
            requireAdmin(actor, "ADMINISTRATION:READ")
            support.audit(filter, 40)
        }
    }
    override suspend fun diagnostics(): SupportHealth {
        val integrity = identity.supportIntegrity()
        val counts = identity.sessionRead { actor ->
            requireAdmin(actor, "ADMINISTRATION:READ")
            support.counts()
        }
        val (used, free) = storage()
        return SupportHealth(2, integrity.quick, integrity.full, integrity.foreignKeys,
            counts.first, counts.second, counts.third, used, free)
    }
}
