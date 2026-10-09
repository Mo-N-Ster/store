package com.vibe.store.application.security

import com.vibe.store.api.*

/** Trusted persistence contract. Repository methods are only available inside the owner lease. */
data class StoredMessage(val id: Long, val senderId: Long?, val senderName: String, val recipientId: Long?,
    val recipientType: String, val subject: String, val content: String, val createdAt: String,
    val read: Boolean)
interface SupportRepository {
    suspend fun messageFor(actor: Long, id: Long): StoredMessage?
    suspend fun messageByRequest(key: String): StoredMessage?
    suspend fun messagesFor(actor: Long, unreadOnly: Boolean, limit: Int, offset: Int): List<StoredMessage>
    suspend fun insertMessage(sender: Long, recipient: Long?, broadcast: Boolean, subject: String,
        content: String, requestId: String, stamp: String): Long
    suspend fun addRecipient(messageId: Long, recipientId: Long)
    suspend fun markMessageRead(messageId: Long, actor: Long, stamp: String)
    suspend fun hideMessage(messageId: Long, actor: Long, stamp: String)
    suspend fun alerts(actor: Long, limit: Int, offset: Int): List<LocalAlert>
    suspend fun alertExists(id: Long): Boolean
    suspend fun resolveAlert(id: Long, stamp: String): Boolean
    suspend fun audit(filter: AuditFilter, limit: Int): List<AuditEntry>
    suspend fun counts(): Triple<Int, Int, Int>
}
