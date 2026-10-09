package com.vibe.store.testing

import com.vibe.store.api.*
import com.vibe.store.application.persistence.*
import com.vibe.store.application.security.*
import com.vibe.store.domain.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** I08 synthetic authority checks; real Room semantics are covered by SupportNativeTest. */
class SupportAuthorityTest {
    private data class Account(val id: Long, val username: String, val role: String, val first: String = "Test",
        val last: String = "Only", val active: Boolean = true, val verifier: String = "TEST:Password-123",
        val recovery: String = "TEST:answer", val failures: Int = 0, val until: Long? = null,
        val recoveryFailures: Int = 0, val recoveryUntil: Long? = null, val employment: String? = null) {
        fun record() = AuthAccount(id, username, "$username@example.invalid", first, last, role, active, employment,
            verifier, "Synthetic question", recovery, failures, until, recoveryFailures, recoveryUntil)
    }
    private class Memory : DatabaseOwner, UnitOfWork, TransactionRepositories, SecurityRepository, SettingsWriter, SupportRepository {
        override var generation = GenerationId("main")
        val users = mutableMapOf<Long, Account>(); val denials = mutableMapOf<Long, Set<String>>()
        val values = mutableMapOf<String, String>(); val events = mutableListOf<SecurityAudit>(); val cashUsers = mutableSetOf<Long>()
        override val security get() = this
        override val settings get() = this
        override val support get() = this
        override val identity: IdentityRepository get() = error("unused")
        override val cash: CashRepository get() = error("unused")
        override val catalog: CatalogRepository get() = error("unused")
        override val sales: SalesRepository get() = error("unused")
        override val purchases: PurchaseRepository get() = error("unused")
        override val inventories: InventoryRepository get() = error("unused")
        // I05 adds these ports to the shared transaction contract. The I03
        // fixture intentionally exercises only identity/security operations.
        override val employees: EmployeeRepository get() = error("unused")
        override val attendance: AttendanceRepository get() = error("unused")
        override val messaging: MessagingRepository get() = error("unused")
        override val audit: AuditRepository get() = error("unused")
        override suspend fun <T> read(block: suspend ReadRepositories.() -> T) = block(this)
        override suspend fun <T> transaction(block: suspend TransactionRepositories.() -> T): T {
            val before = users.toMap(); val denied = denials.toMap(); val settings = values.toMap(); val audit = events.toList()
            try { return block(this) } catch (failure: Throwable) {
                users.clear(); users.putAll(before); denials.clear(); denials.putAll(denied); values.clear(); values.putAll(settings); events.clear(); events.addAll(audit); throw failure
            }
        }
        override suspend fun accounts() = users.values.map { it.record() }
        override suspend fun account(id: Long) = users[id]?.record()
        override suspend fun createOwner(input: NewOwner): Long {
            val id = (users.keys.maxOrNull() ?: 0) + 1
            users[id] = Account(id, input.username, "owner", input.firstName, input.lastName, verifier = input.verifier, recovery = input.recoveryVerifier); return id
        }
        override suspend fun createManaged(input: NewManagedAccount): Long = error("unused")
        override suspend fun updateManaged(input: ManagedAccountChange): Unit = error("unused")
        override suspend fun inherited(id: Long) = RolePolicy.inherited(users.getValue(id).role)
        override suspend fun denied(id: Long) = denials[id] ?: emptySet()
        override suspend fun replaceDenials(id: Long, codes: Set<String>) { denials[id] = codes }
        override suspend fun loginResult(id: Long, failures: Int, until: Long?, successAt: String?) { users[id] = users.getValue(id).copy(failures = failures, until = until) }
        override suspend fun recoveryResult(id: Long, failures: Int, until: Long?, verifier: String?) {
            val u = users.getValue(id); users[id] = u.copy(recoveryFailures = failures, recoveryUntil = until,
                verifier = verifier ?: u.verifier, failures = if (verifier != null) 0 else u.failures, until = if (verifier != null) null else u.until)
        }
        override suspend fun hasOpenCash(id: Long) = id in cashUsers
        override suspend fun appendAudit(event: SecurityAudit) { events += event }
        override suspend fun value(key: String) = values[key]
        override suspend fun put(key: String, value: String) { values[key] = value }
        val sent = linkedMapOf<Long, StoredMessage>()
        val request = linkedMapOf<String, Long>()
        val receipt = mutableMapOf<Pair<Long, Long>, Boolean>()
        val hidden = mutableSetOf<Pair<Long, Long>>()
        override suspend fun messageFor(actor: Long, id: Long): StoredMessage? = sent[id]?.takeIf {
            (it.senderId == actor || (id to actor) in receipt) && (id to actor) !in hidden
        }?.copy(read = receipt[id to actor] == true)
        override suspend fun messageByRequest(key: String): StoredMessage? = request[key]?.let { sent[it] }
        override suspend fun messagesFor(actor: Long, unreadOnly: Boolean, limit: Int, offset: Int): List<StoredMessage> =
            sent.values.toList().asReversed().mapNotNull { messageFor(actor, it.id) }
                .filter { !unreadOnly || (it.senderId != actor && !it.read) }.drop(offset).take(limit)
        override suspend fun insertMessage(sender: Long, recipient: Long?, broadcast: Boolean, subject: String,
            content: String, requestId: String, stamp: String): Long {
            val id = (sent.keys.maxOrNull() ?: 0L) + 1
            sent[id] = StoredMessage(id, sender, users.getValue(sender).username, recipient,
                if (broadcast) "ALL" else "USER", subject, content, stamp, false)
            request[requestId] = id
            return id
        }
        override suspend fun addRecipient(messageId: Long, recipientId: Long) { receipt[messageId to recipientId] = false }
        override suspend fun markMessageRead(messageId: Long, actor: Long, stamp: String) { check(messageId to actor in receipt); receipt[messageId to actor] = true }
        override suspend fun hideMessage(messageId: Long, actor: Long, stamp: String) { hidden += messageId to actor }
        override suspend fun alerts(actor: Long, limit: Int, offset: Int) = emptyList<LocalAlert>()
        override suspend fun alertExists(id: Long) = false
        override suspend fun resolveAlert(id: Long, stamp: String) = false
        override suspend fun audit(filter: AuditFilter, limit: Int) = events.mapIndexed { index, event ->
            AuditEntry(index.toLong(), event.stamp, event.actorId, event.responsibleId, null,
                event.action, event.entity, event.reference, if (event.success) "SUCCESS" else "FAILURE",
                null, null, null)
        }.take(limit)
        override suspend fun counts(): Triple<Int, Int, Int> = Triple(users.size, sent.size, 0)
        override suspend fun verifyIntegrity() = IntegrityResult(true, true, true)
        override suspend fun checkpointCloseReopen() = Unit
        override suspend fun close() = Unit
    }
    private class Fixture {
        val db = Memory(); var now = 1_000_000L; var critical = false; var cart = false
        val hasher = object : PasswordHasher { override fun hash(value: String) = "TEST:$value"; override fun verifies(value: String, verifier: String) = hash(value) == verifier }
        val authority = IdentityAuthority(db, CommandCoordinator(db), hasher, OperationGuards({ critical }, { cart }), { now })
        fun seed() { db.users[1] = Account(1, "owner", "owner"); db.users[2] = Account(2, "manager", "manager"); db.users[3] = Account(3, "employee", "employee") }
        suspend fun login(who: String = "owner") = authority.login(Credentials(who, "Password-123"))
    }
    private suspend fun denied(expected: SecurityError, action: suspend () -> Unit) {
        try { action(); fail("Expected refusal $expected") } catch (failure: SecurityFailure) { assertEquals(expected, failure.code) }
    }

    private fun draft(to: Long? = 3, all: Boolean = false, key: String = UUID.randomUUID().toString()) =
        MessageDraft(key, to, all, "Synthetic subject", "Synthetic body")
    @Test fun directAndBroadcastVisibilityReadHideAndReplay() = runBlocking {
        val f = Fixture(); f.seed(); val support = SupportAuthority(f.authority, { f.now })
        f.login("owner")
        val input = draft(); val first = support.send(input)
        assertEquals(first, support.send(input))
        assertEquals(1, f.db.sent.size)
        assertEquals(1, support.messages().size)
        f.authority.switchUser(Credentials("manager", "Password-123"))
        assertTrue(support.messages().isEmpty())
        denied(SecurityError.FORBIDDEN) { support.markRead(first.id) }
        denied(SecurityError.FORBIDDEN) { support.hideMessage(first.id) }
        denied(SecurityError.FORBIDDEN) { support.send(draft(to = null, all = true)) }
        f.authority.switchUser(Credentials("employee", "Password-123"))
        assertEquals(1, support.messages(unreadOnly = true).size)
        support.markRead(first.id)
        assertTrue(support.messages(unreadOnly = true).isEmpty())
        assertTrue(support.messages().single().read)
        support.hideMessage(first.id)
        assertTrue(support.messages().isEmpty())
        f.authority.switchUser(Credentials("owner", "Password-123"))
        assertEquals(1, support.messages().size) // recipient's private hide cannot hide sender's copy
        val broadcast = support.send(draft(to = null, all = true))
        f.db.users[4] = Account(4, "late", "employee")
        f.authority.switchUser(Credentials("late", "Password-123"))
        assertTrue(support.messages().isEmpty()) // no retrospective broadcast to late joiner
        denied(SecurityError.FORBIDDEN) { support.markRead(broadcast.id) }
    }
    @Test fun forbiddenOwnerDataAndInvalidRequestsNeverMutate() = runBlocking {
        val f = Fixture(); f.seed(); val support = SupportAuthority(f.authority, { f.now })
        denied(SecurityError.FORBIDDEN) { support.messages() }
        f.login("employee")
        denied(SecurityError.FORBIDDEN) { support.audit() }
        denied(SecurityError.FORBIDDEN) { support.diagnostics() }
        denied(SecurityError.FORBIDDEN) { support.alerts() }
        denied(SecurityError.FORBIDDEN) { support.resolveAlert(1) }
        denied(SecurityError.INVALID_INPUT) { support.send(draft().copy(content = "")) }
        denied(SecurityError.INVALID_INPUT) { support.send(draft().copy(subject = "x".repeat(201))) }
        denied(SecurityError.INVALID_INPUT) { support.send(draft().copy(requestId = "invalid")) }
        denied(SecurityError.INVALID_INPUT) { support.send(draft(to = -1)) }
        denied(SecurityError.INVALID_INPUT) { support.messages(offset = -1) }
        assertTrue(f.db.sent.isEmpty())
        f.authority.logout()
        denied(SecurityError.FORBIDDEN) { support.recipients() }
    }
    @Test fun collisionAndCurrentAccountRevocationPreventForgedRecipients() = runBlocking {
        val f = Fixture(); f.seed(); val support = SupportAuthority(f.authority, { f.now })
        f.login("owner")
        val first = draft()
        support.send(first)
        denied(SecurityError.CONFLICT) { support.send(first.copy(content = "changed")) }
        denied(SecurityError.INVALID_INPUT) { support.send(draft(to = 1)) }
        f.db.users[3] = f.db.users.getValue(3).copy(active = false)
        denied(SecurityError.INVALID_INPUT) { support.send(draft(to = 3)) }
        assertEquals(1, f.db.sent.size)
        assertTrue(support.recipients().none { it.id == 3L })
    }
}
