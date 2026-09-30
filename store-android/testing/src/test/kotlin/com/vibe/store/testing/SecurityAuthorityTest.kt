package com.vibe.store.testing

import com.vibe.store.api.*
import com.vibe.store.application.persistence.*
import com.vibe.store.application.security.*
import com.vibe.store.domain.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Fast use-case fixtures; real Room/Keystore/lifecycle tested separately on Android. */
class SecurityAuthorityTest {
    private data class Account(val id: Long, val username: String, val role: String, val first: String = "Test",
        val last: String = "Only", val active: Boolean = true, val verifier: String = "TEST:Password-123",
        val recovery: String = "TEST:answer", val failures: Int = 0, val until: Long? = null,
        val recoveryFailures: Int = 0, val recoveryUntil: Long? = null, val employment: String? = null) {
        fun record() = AuthAccount(id, username, "$username@example.invalid", first, last, role, active, employment,
            verifier, "Synthetic question", recovery, failures, until, recoveryFailures, recoveryUntil)
    }
    private class Memory : DatabaseOwner, UnitOfWork, TransactionRepositories, SecurityRepository, SettingsWriter {
        override var generation = GenerationId("main")
        val users = mutableMapOf<Long, Account>(); val denials = mutableMapOf<Long, Set<String>>()
        val values = mutableMapOf<String, String>(); val events = mutableListOf<SecurityAudit>(); val cashUsers = mutableSetOf<Long>()
        override val security get() = this
        override val settings get() = this
        override val identity: IdentityRepository get() = error("unused")
        override val cash: CashRepository get() = error("unused")
        override val catalog: CatalogRepository get() = error("unused")
        override val sales: SalesRepository get() = error("unused")
        override val purchases: PurchaseRepository get() = error("unused")
        override val inventories: InventoryRepository get() = error("unused")
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
    @Test fun bootstrapUniqueNoDefaultAndDistinctSetup() = runBlocking {
        val f = Fixture(); assertTrue(f.authority.needsOwner())
        denied(SecurityError.INVALID_INPUT) { f.authority.bootstrap(OwnerRegistration("owner", "a@b", "Test", "Owner", "", "Q", "A")) }
        assertTrue(f.db.users.isEmpty())
        val registration = OwnerRegistration("owner", "test@example.invalid", "Test", "Owner", "Password-123", "Question", "Answer")
        val outcomes = coroutineScope { (1..2).map { async { runCatching { f.authority.bootstrap(registration) }.isSuccess } }.awaitAll() }
        assertEquals(1, outcomes.count { it }); assertEquals(1, f.db.users.size); assertNull(f.authority.current()); assertTrue(f.db.values.isEmpty())
        assertFalse(f.authority.needsOwner()); assertEquals("owner", f.login().role)
    }
    @Test fun identityNfcCaseSpacesAccentsAmbiguityAndInactive() = runBlocking {
        assertEquals("éloïse du pont", IdentityPolicy.fold("\uFEFF E\u0301LOÏSE\u00A0du\tPONT \uFEFF"))
        assertNotEquals(IdentityPolicy.fold("é"), IdentityPolicy.fold("e"))
        val f = Fixture(); f.seed(); f.db.users[3] = Account(3, "éloïse", "employee", "Éloïse", "Du Pont")
        assertEquals(3L, f.authority.login(Credentials(" du  pont E\u0301LOÏSE ", "Password-123")).id)
        f.authority.logout()
        denied(SecurityError.INVALID_CREDENTIALS) { f.authority.login(Credentials("éloïse", "password-123")) }
        f.db.users[4] = Account(4, "other", "employee", "Éloïse", "Du Pont")
        denied(SecurityError.INVALID_CREDENTIALS) { f.authority.login(Credentials("Éloïse Du Pont", "Password-123")) }
        f.db.users[3] = f.db.users.getValue(3).copy(active = false)
        denied(SecurityError.INVALID_CREDENTIALS) { f.authority.login(Credentials("éloïse", "Password-123")) }
        f.db.users[3] = f.db.users.getValue(3).copy(active = true, employment = "SUSPENDED")
        denied(SecurityError.INVALID_CREDENTIALS) { f.authority.login(Credentials("éloïse", "Password-123")) }
        denied(SecurityError.INVALID_INPUT) { f.authority.login(Credentials("owner", "Password-123", "invalid")) }
        assertNull(f.authority.current())
    }
    @Test fun boundedLockoutRecoveryAndRetry() = runBlocking {
        val f = Fixture(); f.seed()
        repeat(5) { denied(SecurityError.INVALID_CREDENTIALS) { f.authority.login(Credentials("owner", "wrong")) } }
        assertEquals(f.now + 900_000L, f.db.users.getValue(1).until)
        denied(SecurityError.INVALID_CREDENTIALS) { f.login() }; assertEquals(5, f.db.users.getValue(1).failures)
        f.now += 900_001; f.login(); f.authority.logout(); assertEquals(0, f.db.users.getValue(1).failures)
        repeat(5) { denied(SecurityError.INVALID_CREDENTIALS) { f.authority.recover(RecoveryProof(1, "wrong", "Replacement-123")) } }
        denied(SecurityError.UNAVAILABLE) { f.authority.recoveryQuestion("owner") }
        denied(SecurityError.INVALID_CREDENTIALS) { f.authority.recover(RecoveryProof(1, "answer", "Replacement-123")) }
        f.now += 900_001
        assertEquals(1L, f.authority.recoveryQuestion("owner").accountId)
        f.authority.recover(RecoveryProof(1, " ANSWER ", "Replacement-123"))
        assertEquals(1L, f.authority.login(Credentials("owner", "Replacement-123")).id)
        denied(SecurityError.UNAVAILABLE) { f.authority.recoveryQuestion("employee") }
        assertEquals(3, LockPolicy(3, 1).maxAttempts)
        assertTrue(runCatching { LockPolicy(2, 15) }.isFailure); assertTrue(runCatching { LockPolicy(5, 1441) }.isFailure)
    }
    @Test fun directCallsOwnerManagerAndZeroMutation() = runBlocking {
        val f = Fixture(); f.seed(); val before = f.db.users.toMap()
        denied(SecurityError.FORBIDDEN) { f.authority.configure(FoundationSettings("forged")) }
        denied(SecurityError.FORBIDDEN) { f.authority.definePassword(PasswordDefinition(1, "Replacement-123")) }
        f.login("manager")
        denied(SecurityError.FORBIDDEN) { f.authority.definePassword(PasswordDefinition(1, "Replacement-123")) }
        denied(SecurityError.FORBIDDEN) { f.authority.definePassword(PasswordDefinition(2, "Replacement-123")) }
        denied(SecurityError.FORBIDDEN) { f.authority.restrict(DenialChange(3, "employee", emptySet(), setOf("CASH:READ"))) }
        denied(SecurityError.FORBIDDEN) { f.authority.settings() }
        assertEquals(before.mapValues { it.value.verifier }, f.db.users.mapValues { it.value.verifier }); assertTrue(f.db.values.isEmpty()); assertTrue(f.db.denials.isEmpty())
        f.authority.definePassword(PasswordDefinition(3, "Replacement-123")); assertEquals("TEST:Replacement-123", f.db.users.getValue(3).verifier)
        f.authority.logout(); f.login()
        denied(SecurityError.FORBIDDEN) { f.authority.restrict(DenialChange(1, "owner", emptySet(), setOf("SETTINGS:READ"))) }
        denied(SecurityError.FORBIDDEN) { f.authority.definePassword(PasswordDefinition(1, "Replacement-123")) }
    }
    @Test fun subtractiveDenialsConflictCashAndRightsReread() = runBlocking {
        val f = Fixture(); f.seed(); f.login()
        denied(SecurityError.INVALID_INPUT) { f.authority.restrict(DenialChange(3, "employee", emptySet(), setOf("SETTINGS:READ"))) }
        f.db.cashUsers += 3
        denied(SecurityError.CASH_OPEN) { f.authority.restrict(DenialChange(3, "employee", emptySet(), setOf("CASH:READ"))) }
        assertTrue(f.db.denials.isEmpty()); f.db.cashUsers.clear()
        val result = f.authority.restrict(DenialChange(3, "employee", emptySet(), setOf("CASH:READ")))
        assertFalse("CASH:READ" in result.effective); assertTrue(result.inherited.containsAll(result.effective))
        denied(SecurityError.CONFLICT) { f.authority.restrict(DenialChange(3, "employee", emptySet(), emptySet())) }
        f.authority.switchUser(Credentials("manager", "Password-123"))
        f.db.denials[2] = setOf("EMPLOYEES:UPDATE") // changed after login, before mutation
        denied(SecurityError.FORBIDDEN) { f.authority.definePassword(PasswordDefinition(3, "Replacement-123")) }
        assertEquals("TEST:Password-123", f.db.users.getValue(3).verifier)
    }
    @Test fun switchingGuardsAndSessionGeneration() = runBlocking {
        val f = Fixture(); f.seed(); f.login()
        denied(SecurityError.INVALID_CREDENTIALS) { f.authority.switchUser(Credentials("manager", "wrong")) }
        assertEquals(1L, f.authority.current()!!.id)
        f.db.cashUsers += 1
        denied(SecurityError.CASH_OPEN) { f.authority.logout() }
        denied(SecurityError.CASH_OPEN) { f.authority.switchUser(Credentials("manager", "Password-123")) }
        denied(SecurityError.FORBIDDEN) { f.login("manager") } // no login bypass
        f.db.cashUsers.clear(); f.critical = true
        denied(SecurityError.OPERATION_ACTIVE) { f.authority.logout() }
        denied(SecurityError.OPERATION_ACTIVE) { f.authority.switchUser(Credentials("manager", "Password-123")) }
        f.critical = false; f.cart = true
        denied(SecurityError.CONFIRM_DISCARD) { f.authority.switchUser(Credentials("manager", "Password-123")) }
        assertEquals(2L, f.authority.switchUser(Credentials("manager", "Password-123"), true).id)
        assertFalse("SETTINGS:UPDATE" in f.authority.current()!!.permissions)
        f.db.generation = GenerationId("replacement"); assertNull(f.authority.current())
        val fresh = IdentityAuthority(f.db, CommandCoordinator(f.db), f.hasher); assertNull(fresh.current())
    }
    @Test fun settingsAllowlistAndPublicResponses() = runBlocking {
        val f = Fixture(); f.seed(); f.login()
        f.authority.configure(FoundationSettings("Synthetic shop", currency = "XAF", discountsEnabled = false))
        assertEquals("XAF", f.authority.settings().currency)
        val before = f.db.values.toMap()
        denied(SecurityError.INVALID_INPUT) { f.authority.configure(FoundationSettings("", currency = "UNKNOWN")) }; assertEquals(before, f.db.values)
        f.authority.preferences(DisplayPreferences("en", "dark")); assertEquals("en", f.authority.preferences().language)
        denied(SecurityError.INVALID_INPUT) { f.authority.preferences(DisplayPreferences("unknown", "dark")) }
        listOf(PublicIdentity::class.java, PermissionView::class.java, FoundationSettings::class.java, RecoveryQuestion::class.java).forEach { type ->
            assertFalse(type.declaredFields.any { it.name.contains(Regex("(?i)password|hash|answer|ciphertext|token")) })
        }
        assertFalse(Credentials("owner", "SECRET_MARKER").toString().contains("SECRET_MARKER"))
        assertFalse(f.db.events.toString().contains("Password-123"))
    }
}
