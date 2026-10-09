package com.vibe.store.api

/** I08 public DTOs are strict allowlists; no password verifier, free-form audit details, or raw logs. */
data class SupportAccount(val id: Long, val username: String, val fullName: String, val role: String)
data class LocalMessage(val id: Long, val senderId: Long, val senderName: String,
    val recipientId: Long?, val broadcast: Boolean, val subject: String, val content: String,
    val createdAt: String, val outgoing: Boolean, val read: Boolean)
data class MessageDraft(val requestId: String, val recipientId: Long?, val broadcast: Boolean,
    val subject: String, val content: String)
data class LocalAlert(val id: Long, val type: String, val message: String, val createdAt: String,
    val resolvedAt: String?, val read: Boolean)
data class AuditEntry(val id: Long, val at: String, val actorId: Long?, val responsibleId: Long?,
    val responsibleName: String?, val action: String, val entity: String, val reference: String?,
    val outcome: String, val cashReference: String?, val cashAmount: String?, val cashCurrency: String?)
data class AuditFilter(val action: String = "", val entity: String = "", val outcome: String = "",
    val fromDate: String = "", val toDate: String = "", val actorId: Long? = null, val offset: Int = 0)
data class SupportHealth(val schemaVersion: Int, val integrityQuick: Boolean, val integrityFull: Boolean,
    val foreignKeys: Boolean, val users: Int, val messages: Int, val pendingEmails: Int,
    val databaseBytes: Long?, val freeBytes: Long?)

interface SupportService {
    suspend fun recipients(): List<SupportAccount>
    suspend fun messages(offset: Int = 0, unreadOnly: Boolean = false): List<LocalMessage>
    suspend fun send(draft: MessageDraft): LocalMessage
    suspend fun markRead(id: Long)
    suspend fun hideMessage(id: Long)
    suspend fun alerts(offset: Int = 0): List<LocalAlert>
    suspend fun markAlertRead(id: Long)
    suspend fun resolveAlert(id: Long)
    suspend fun audit(filter: AuditFilter = AuditFilter()): List<AuditEntry>
    suspend fun diagnostics(): SupportHealth
}
