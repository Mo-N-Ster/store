package com.vibe.store.infrastructure.persistence

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import android.content.Context
import android.os.Process
import com.vibe.store.api.*
import com.vibe.store.application.security.*
import com.vibe.store.infrastructure.security.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.KeyStore
import java.util.UUID

class IdentityNativeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private class Fixture(context: Context, root: File) {
        val owner = RoomDatabaseOwner(context, root)
        val commands = com.vibe.store.application.persistence.CommandCoordinator(owner)
        val bcrypt = BcryptPasswords()
        val authority = IdentityAuthority(owner, commands, bcrypt)
        suspend fun bootstrap() { authority.bootstrap(OwnerRegistration("owner", "owner@example.invalid", "Synthetic", "Owner", "Password-123", "Test question", "Answer")) }
        suspend fun login() = authority.login(Credentials("owner", "Password-123"))
        suspend fun seedStaff() = commands.execute {
            val dao = (this as RoomRepositories).dao
            val hash = bcrypt.hash("Password-123")
            dao.user(UserEntity(2, "manager", hash, "manager", "Synthetic", "Manager", "SM", STAMP, securityQuestion = "Question", securityAnswerHash = bcrypt.hash("answer")))
            dao.user(UserEntity(3, "employee", hash, "employee", "Synthetic", "Employee", "SE", STAMP))
            val roles = dao.roles().associateBy { it.code }
            dao.userRole(UserRoleEntity(2, roles.getValue("manager").id, STAMP)); dao.userRole(UserRoleEntity(3, roles.getValue("employee").id, STAMP))
        }
        suspend fun cash(id: Long) = commands.execute { (this as RoomRepositories).dao.cash(CashEntity(id, "I03-CASH-$id", id, "OPEN", 25.0, STAMP)) }
    }
    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val f = Fixture(context, File(context.noBackupFilesDir, "i03/${UUID.randomUUID()}"))
        try { block(f) } finally { f.owner.close() }
    }
    private suspend fun refused(code: SecurityError, block: suspend () -> Unit) {
        try { block(); fail("Expected $code") } catch (failure: SecurityFailure) { assertEquals(code, failure.code) }
    }
    @Test fun bootstrapRepositoryAndDeniedReadScope() = runBlocking { fixture { f ->
        f.bootstrap(); assertFalse(f.authority.needsOwner()); f.seedStaff(); val user = f.login()
        assertEquals(70, user.permissions.size)
        refused(SecurityError.ALREADY_INITIALIZED) { f.bootstrap() }
        f.owner.read {
            assertEquals(3, security.accounts().size)
            assertTrue(security.account(1)!!.verifier.startsWith("\$2b\$10\$"))
            assertTrue(runCatching { security.replaceDenials(3, setOf("CASH:READ")) }.isFailure)
        }
        assertTrue(f.owner.verifyIntegrity().let { it.quick && it.full && it.foreignKeys })
    } }
    @Test fun realTransactionPermissionsCashAndZeroBusinessMutation() = runBlocking { fixture { f ->
        f.bootstrap(); f.seedStaff(); f.login(); f.cash(3)
        refused(SecurityError.CASH_OPEN) { f.authority.restrict(DenialChange(3, "employee", emptySet(), setOf("CASH:READ"))) }
        assertTrue(f.owner.read { security.denied(3).isEmpty() })
        f.authority.switchUser(Credentials("manager", "Password-123"))
        refused(SecurityError.FORBIDDEN) { f.authority.definePassword(PasswordDefinition(1, "Other-password")) }
        refused(SecurityError.FORBIDDEN) { f.authority.restrict(DenialChange(1, "owner", emptySet(), emptySet())) }
        assertTrue(f.owner.read { security.hasOpenCash(3) })
        assertTrue(f.owner.read { f.bcrypt.verifies("Password-123", security.account(1)!!.verifier) })
        assertNull(f.owner.read { attendance.attendance(1) }); assertNull(f.owner.read { sales.invoice("forged") })
        f.authority.switchUser(Credentials("employee", "Password-123"))
        refused(SecurityError.CASH_OPEN) { f.authority.logout() }
        refused(SecurityError.CASH_OPEN) { f.authority.switchUser(Credentials("owner", "Password-123")) }
        assertEquals(3L, f.authority.current()!!.id)
    } }
    @Test fun lockoutAndRecoveryAreDurableWithoutPublicSecrets() = runBlocking { fixture { f ->
        f.bootstrap()
        repeat(5) { refused(SecurityError.INVALID_CREDENTIALS) { f.authority.login(Credentials("owner", "wrong")) } }
        f.owner.checkpointCloseReopen()
        assertEquals(5, f.owner.read { security.account(1)!!.failures })
        refused(SecurityError.INVALID_CREDENTIALS) { f.login() }
        f.authority.recover(RecoveryProof(1, " ANSWER ", "Replacement-123"))
        val user = f.authority.login(Credentials("owner", "Replacement-123"))
        assertFalse(user.toString().contains("Replacement-123")); assertFalse(user.toString().contains("\$2b\$"))
        assertEquals(0, f.owner.read { security.account(1)!!.failures })
        assertTrue(f.owner.verifyIntegrity().foreignKeys)
    } }
    @Test fun auditUsesTrustedResponsibleCashSnapshot() = runBlocking { fixture { f ->
        f.bootstrap(); f.seedStaff(); f.login(); f.cash(1)
        f.authority.definePassword(PasswordDefinition(3, "Replacement-123"))
        val record = f.owner.read { audit.audit(2) }!!
        assertEquals("Synthetic Owner", record.responsibleName); assertEquals(25.0, record.cashAmount!!.value, 0.0)
        assertNull(f.owner.read { attendance.attendance(1) })
    } }
    @Test fun keystoreLossTamperAndReconfiguration() {
        val alias = "store.i03.test.${UUID.randomUUID()}"
        val secrets = KeystoreSecrets(alias)
        val clear = "Synthetic secret only".toByteArray()
        val first = secrets.seal(clear); val second = secrets.seal(clear)
        assertFalse(first.contentEquals(second)); assertArrayEquals(clear, secrets.open(first))
        assertFalse(first.toString(Charsets.UTF_8).contains("Synthetic secret"))
        val corrupted = first.copyOf(); corrupted[corrupted.lastIndex] = (corrupted.last().toInt() xor 1).toByte()
        assertNull(secrets.open(corrupted))
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }; keys.deleteEntry(alias)
        assertNull(secrets.open(first)); assertFalse(keys.containsAlias(alias))
        val reconfigured = secrets.seal(clear); assertArrayEquals(clear, secrets.open(reconfigured)); assertNull(secrets.open(first))
        keys.deleteEntry(alias); clear.fill(0)
    }
    @Test fun realProcessDeathRequiresAuthenticationWithoutCashOrAttendanceChanges() = runBlocking {
        val phase = InstrumentationRegistry.getArguments().getString("identityRestartPhase")
        require(phase in setOf("prepare", "verify")) { "Explicit prepare/verify process phase required" }
        val f = Fixture(context, File(context.noBackupFilesDir, "i03-process"))
        try {
            if (phase == "prepare") {
                assertTrue(f.authority.needsOwner()); f.bootstrap(); f.login(); f.cash(1)
                f.commands.execute { settings.put("i03-process-pid", Process.myPid().toString()) }
                assertNotNull(f.authority.current()); println("I03 PREPARED PID=${Process.myPid()}")
            } else {
                val previous = f.owner.read { settings.value("i03-process-pid") }!!.toInt()
                assertNotEquals(previous, Process.myPid()); assertNull(f.authority.current())
                refused(SecurityError.FORBIDDEN) { f.authority.settings() }
                assertTrue(f.owner.read { security.hasOpenCash(1) }); assertNull(f.owner.read { attendance.attendance(1) })
                assertEquals(1L, f.login().id); assertTrue(f.owner.read { security.hasOpenCash(1) })
                println("I03 VERIFIED previousPID=$previous PID=${Process.myPid()}")
            }
        } finally { f.owner.close() }
    }
}
