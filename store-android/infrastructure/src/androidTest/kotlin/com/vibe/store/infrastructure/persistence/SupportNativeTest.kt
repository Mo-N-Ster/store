package com.vibe.store.infrastructure.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vibe.store.api.*
import com.vibe.store.application.persistence.CommandCoordinator
import com.vibe.store.application.security.IdentityAuthority
import com.vibe.store.application.security.SupportAuthority
import com.vibe.store.domain.*
import com.vibe.store.infrastructure.security.BcryptPasswords
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.util.UUID

/** I08: real Room v2, actual scoped receipts/deletions, restart, alerts and safe audit. */
class SupportNativeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private inner class Fixture {
        private val root = File(context.noBackupFilesDir, "i08/${UUID.randomUUID()}")
        val owner = RoomDatabaseOwner(context, root)
        val commands = CommandCoordinator(owner)
        val bcrypt = BcryptPasswords()
        val identity = IdentityAuthority(owner, commands, bcrypt)
        val support = SupportAuthority(identity)
        suspend fun setup() {
            identity.bootstrap(OwnerRegistration("owner", "owner@example.invalid", "Owner", "Synthetic",
                "Password-123", "Question", "Answer"))
            commands.execute {
                val dao = (this as RoomRepositories).dao
                val hash = bcrypt.hash("Password-123")
                val roles = dao.roles().associateBy { it.code }
                for ((id, role) in listOf(2L to "manager", 3L to "employee")) {
                    dao.user(UserEntity(id, role, hash, role, "Test", role, "TT", STAMP))
                    dao.userRole(UserRoleEntity(id, roles.getValue(role).id, STAMP))
                }
            }
            identity.login(Credentials("owner", "Password-123"))
        }
        suspend fun switch(who: String) { identity.switchUser(Credentials(who, "Password-123")) }
        suspend fun close() = owner.close()
    }
    private suspend fun withFixture(block: suspend (Fixture) -> Unit) {
        val f = Fixture()
        try { f.setup(); block(f) } finally { f.close() }
    }
    private suspend fun refused(code: SecurityError, block: suspend () -> Unit) {
        try { block(); fail("Expected $code") } catch (failure: SecurityFailure) { assertEquals(code, failure.code) }
    }
    private fun direct(id: Long = 3) = MessageDraft(UUID.randomUUID().toString(), id,
        false, "I08 synthetic", "Sensitive test message, not a password")

    @Test fun directMessageScopedReadHideIdempotencyAndRestart() = runBlocking { withFixture { f ->
        val input = direct()
        val sent = f.support.send(input)
        assertEquals(sent, f.support.send(input))
        assertEquals(1, f.owner.read { (this as RoomRepositories).dao.supportMessageCount() })
        f.owner.checkpointCloseReopen()
        f.switch("manager")
        assertTrue(f.support.messages().isEmpty())
        refused(SecurityError.FORBIDDEN) { f.support.markRead(sent.id) }
        refused(SecurityError.FORBIDDEN) { f.support.hideMessage(sent.id) }
        f.switch("employee")
        assertEquals(1, f.support.messages(unreadOnly = true).size)
        f.support.markRead(sent.id)
        assertTrue(f.support.messages(unreadOnly = true).isEmpty())
        assertTrue(f.support.messages().single().read)
        f.support.hideMessage(sent.id)
        assertTrue(f.support.messages().isEmpty())
        f.switch("owner")
        assertEquals(sent.id, f.support.messages().single().id)
        assertTrue(f.owner.verifyIntegrity().let { it.quick && it.full && it.foreignKeys })
    } }

    @Test fun broadcastOwnerOnlyRevocationAndAuditNoRawDetails() = runBlocking { withFixture { f ->
        val sent = f.support.send(MessageDraft(UUID.randomUUID().toString(), null, true,
            "I08 broadcast", "Synthetic notice"))
        f.switch("manager")
        assertEquals(sent.id, f.support.messages().single().id)
        refused(SecurityError.FORBIDDEN) { f.support.audit() }
        refused(SecurityError.FORBIDDEN) { f.support.send(MessageDraft(UUID.randomUUID().toString(), null, true, "X", "Y")) }
        f.switch("employee")
        assertEquals(sent.id, f.support.messages().single().id)
        refused(SecurityError.FORBIDDEN) { f.support.diagnostics() }
        f.switch("owner")
        val entries = f.support.audit(AuditFilter(action = "login"))
        assertTrue(entries.all { it.action == "login" })
        assertTrue(entries.none { it.toString().contains("Password-123") })
        val health = f.support.diagnostics()
        assertEquals(2, health.schemaVersion)
        assertTrue(health.integrityFull && health.foreignKeys)
        assertEquals(3, health.users)
        assertEquals(1, health.messages)
    } }

    @Test fun stockAlertsAreDurableBoundedAndResolvedOnRestock() = runBlocking { withFixture { f ->
        f.commands.execute {
            catalog.write(CatalogRecord(5, "Synthetic low-stock", "Test", "", "", BigDecimal("1.234567890123"),
                10, 3, STAMP, STAMP, null, null))
        }
        assertTrue(f.support.alerts().isEmpty())
        f.commands.execute {
            val old = checkNotNull(catalog.find(5))
            catalog.write(old.copy(stock = 3, updatedAt = "2026-10-09T10:00:00Z"))
        }
        assertEquals(1, f.support.alerts().size)
        val alert = f.support.alerts().single()
        f.support.markAlertRead(alert.id)
        assertTrue(f.support.alerts().single().read)
        f.commands.execute { catalog.write(checkNotNull(catalog.find(5)).copy(stock = 2)) }
        assertEquals(1, f.support.alerts().size)
        f.commands.execute { catalog.write(checkNotNull(catalog.find(5)).copy(stock = 9)) }
        assertNotNull(f.support.alerts().single().resolvedAt)
        f.owner.checkpointCloseReopen()
        assertTrue(f.support.alerts().single().read)
        assertEquals("1.234567890123", f.owner.read { catalog.find(5)!!.price.toPlainString() })
        assertTrue(f.owner.verifyIntegrity().foreignKeys)
    } }
}
