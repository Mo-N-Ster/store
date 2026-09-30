package com.vibe.store.application.security

import com.vibe.store.api.*
import com.vibe.store.application.persistence.*
import com.vibe.store.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.*

/** Application-lifetime authority. Never serialize or save this object's session. */
class IdentityAuthority(private val owner: DatabaseOwner, private val commands: CommandCoordinator,
    private val hasher: PasswordHasher, private val guards: OperationGuards = OperationGuards(),
    private val clock: () -> Long = System::currentTimeMillis) : IdentityService {
    private class Session(val accountId: Long, val generation: GenerationId, val nonce: UUID = UUID.randomUUID())
    private var session: Session? = null
    private val gate = Mutex()
    // Trusted application composition only. Same session mutex and transaction-
    // local authorization as I03; never call current() and trust a UI snapshot.
    internal suspend fun <T> authorizedRead(right: String, block: suspend ReadRepositories.(Long) -> T): T = gate.withLock {
        owner.read { val actor = requireRight(right); block(actor.id) }
    }
    internal suspend fun <T> authorizedWrite(right: String, block: suspend TransactionRepositories.(Long) -> T): T = gate.withLock {
        commands.execute { val actor = requireRight(right); block(actor.id) }
    }
    private fun fail(code: SecurityError): Nothing = throw SecurityFailure(code)
    private fun stamp(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(clock()))
    private suspend fun hash(value: String) = withContext(Dispatchers.Default) { hasher.hash(value) }
    private suspend fun verify(value: String, verifier: String) = withContext(Dispatchers.Default) { hasher.verifies(value, verifier) }
    private fun valid(account: AuthAccount) = account.active && (account.employment == null || account.employment in setOf("ACTIVE", "ABSENT"))
    private suspend fun ReadRepositories.actor(): AuthAccount {
        val held = session ?: fail(SecurityError.FORBIDDEN)
        if (held.generation != owner.generation) { session = null; fail(SecurityError.FORBIDDEN) }
        return security.account(held.accountId)?.takeIf(::valid) ?: run { session = null; fail(SecurityError.FORBIDDEN) }
    }
    private suspend fun ReadRepositories.view(account: AuthAccount): PublicIdentity {
        val inherited = security.inherited(account.id).intersect(RolePolicy.inherited(account.role))
        val effective = if (account.role == "owner") inherited else inherited - security.denied(account.id)
        return PublicIdentity(account.id, account.username, account.firstName, account.lastName, account.role, effective)
    }
    private suspend fun ReadRepositories.requireRight(code: String): AuthAccount {
        if (code !in RolePolicy.codes) fail(SecurityError.FORBIDDEN)
        return actor().also { if (code !in view(it).permissions) fail(SecurityError.FORBIDDEN) }
    }
    private suspend fun ReadRepositories.lookup(identifier: String, role: String?): AuthAccount? {
        if (role != null && role !in setOf("owner", "manager", "employee")) fail(SecurityError.INVALID_INPUT)
        val folded = IdentityPolicy.fold(identifier)
        if (folded.isEmpty()) return null
        return security.accounts().filter { user ->
            (role == null || user.role == role || role == "manager" && user.role == "owner") &&
                listOfNotNull(user.username, user.email, "${user.firstName} ${user.lastName}", "${user.lastName} ${user.firstName}").any { IdentityPolicy.fold(it) == folded }
        }.singleOrNull()
    }
    private suspend fun ReadRepositories.policy(): LockPolicy {
        fun bounded(value: String?, fallback: Int, lo: Int, hi: Int): Int {
            val number = value?.trim()?.let { if (it.isEmpty()) 0.0 else it.toDoubleOrNull() } ?: return fallback
            return if (number.isFinite() && number % 1.0 == 0.0) number.coerceIn(lo.toDouble(), hi.toDouble()).toInt() else fallback
        }
        return LockPolicy(bounded(settings.value("authMaxAttempts"), 5, 3, 10), bounded(settings.value("authLockMinutes"), 15, 1, 1440))
    }
    private suspend fun TransactionRepositories.audit(actorId: Long?, action: String, entity: String, ref: String, success: Boolean = true) {
        security.appendAudit(SecurityAudit(actorId, session?.takeIf { it.generation == owner.generation }?.accountId, action, entity, ref, success, stamp()))
    }
    override suspend fun needsOwner(): Boolean = gate.withLock { owner.read { security.accounts().none { it.role == "owner" } } }
    override suspend fun bootstrap(input: OwnerRegistration): PublicIdentity = gate.withLock {
        if (input.username.trim().isEmpty() || !input.email.contains('@') || input.firstName.trim().isEmpty() || input.lastName.trim().isEmpty() || input.password.length < 8 || input.question.trim().isEmpty() || input.answer.trim().isEmpty()) fail(SecurityError.INVALID_INPUT)
        val verifier = hash(input.password); val recoveryVerifier = hash(IdentityPolicy.answer(input.answer))
        commands.execute {
            if (security.accounts().any { it.role == "owner" }) fail(SecurityError.ALREADY_INITIALIZED)
            val id = security.createOwner(NewOwner(input.username.trim(), input.email.trim().lowercase(Locale.ROOT), input.firstName.trim(), input.lastName.trim(), verifier, input.question.trim(), recoveryVerifier, stamp()))
            view(security.account(id) ?: fail(SecurityError.UNAVAILABLE))
        }
    }
    private suspend fun TransactionRepositories.authenticate(input: Credentials, switching: Boolean): PublicIdentity? {
        val user = lookup(input.identifier, if (switching) null else input.role)
        // Same cost for unknown/ambiguous identity; no supplied verifier crosses API.
        val matched = if (user == null) { hash(input.password); false } else verify(input.password, user.verifier)
        val now = clock(); val locked = (user?.lockedUntil ?: Long.MIN_VALUE) > now
        if (user == null || !valid(user) || locked || !matched) {
            if (user != null && !locked) {
                val (count, until) = policy().next(user.failures, now)
                security.loginResult(user.id, count, until, null)
            }
            audit(user?.id, if (switching) "user_switch_failed" else "login_failed", "session", user?.id?.toString() ?: "", false)
            return null // commit lockout/audit before returning safe refusal
        }
        security.loginResult(user.id, 0, null, stamp())
        audit(if (switching) session?.accountId else user.id,
            if (!switching) "login" else if (session?.accountId == user.id) "user_switch_same" else "user_switch", "session", user.id.toString())
        return view(user)
    }
    override suspend fun login(input: Credentials): PublicIdentity = gate.withLock {
        if (session != null) fail(SecurityError.FORBIDDEN) // login is not an unguarded switch
        val result = commands.execute { authenticate(input, false) } ?: fail(SecurityError.INVALID_CREDENTIALS)
        session = Session(result.id, owner.generation); result
    }
    override suspend fun current(): PublicIdentity? = gate.withLock {
        if (session == null) return@withLock null
        try { owner.read { view(actor()) } } catch (failure: SecurityFailure) {
            if (failure.code != SecurityError.FORBIDDEN) throw failure
            null
        }
    }
    private suspend fun ReadRepositories.switchGuard(confirm: Boolean): AuthAccount {
        val user = actor()
        if (security.hasOpenCash(user.id)) fail(SecurityError.CASH_OPEN)
        if (guards.critical()) fail(SecurityError.OPERATION_ACTIVE)
        if (guards.unsavedCart() && !confirm) fail(SecurityError.CONFIRM_DISCARD)
        return user
    }
    override suspend fun switchUser(input: Credentials, confirmDiscard: Boolean): PublicIdentity = gate.withLock {
        val result = commands.execute { switchGuard(confirmDiscard); authenticate(input, true) } ?: fail(SecurityError.INVALID_CREDENTIALS)
        session = Session(result.id, owner.generation); result
    }
    override suspend fun logout(confirmDiscard: Boolean): Unit = gate.withLock {
        commands.execute { val user = switchGuard(confirmDiscard); audit(user.id, "logout", "session", user.id.toString()) }
        session = null
    }
    override suspend fun recoveryQuestion(identifier: String): RecoveryQuestion = gate.withLock { owner.read {
        val user = lookup(identifier, "manager")
        if (user == null || !user.active || user.question.isNullOrBlank() || (user.recoveryLockedUntil ?: 0) > clock()) fail(SecurityError.UNAVAILABLE)
        RecoveryQuestion(user.id, user.question)
    } }
    override suspend fun recover(input: RecoveryProof): Unit = gate.withLock {
        if (input.newPassword.length < 8 || input.answer.trim().isEmpty()) fail(SecurityError.INVALID_INPUT)
        val success = commands.execute {
            val user = security.account(input.accountId)?.takeIf { it.active && it.role in setOf("owner", "manager") }
            val locked = (user?.recoveryLockedUntil ?: Long.MIN_VALUE) > clock()
            val correct = !locked && user?.recoveryVerifier?.let { verify(IdentityPolicy.answer(input.answer), it) } == true
            if (!correct) {
                if (user != null && !locked) { val (count, until) = policy().next(user.recoveryFailures, clock()); security.recoveryResult(user.id, count, until, null) }
                audit(user?.id, if (locked) "password_recovery_locked" else "password_recovery_failed", "user", input.accountId.toString(), false)
                false
            } else {
                security.recoveryResult(user!!.id, 0, null, hash(input.newPassword))
                audit(user.id, "password_recovered", "user", user.id.toString()); true
            }
        }
        if (!success) fail(SecurityError.INVALID_CREDENTIALS)
    }
    private suspend fun ReadRepositories.permissionView(id: Long): PermissionView {
        val user = security.account(id) ?: fail(SecurityError.INVALID_INPUT)
        return PermissionView(id, user.role, security.inherited(id), security.denied(id), if (user.active) view(user).permissions else emptySet())
    }
    override suspend fun definePassword(input: PasswordDefinition): Unit = gate.withLock { commands.execute {
        val actor = requireRight("EMPLOYEES:UPDATE")
        val target = security.account(input.accountId) ?: fail(SecurityError.INVALID_INPUT)
        if (!target.active || target.role == "owner" || target.role == "manager" && actor.role != "owner") fail(SecurityError.FORBIDDEN)
        if (input.newPassword.length < 8) fail(SecurityError.INVALID_INPUT)
        security.recoveryResult(target.id, 0, null, hash(input.newPassword))
        audit(actor.id, "password_reset", "user", target.id.toString())
    } }
    override suspend fun permissions(accountId: Long): PermissionView = gate.withLock { owner.read {
        val user = actor(); if (user.role != "owner" && user.id != accountId) fail(SecurityError.FORBIDDEN)
        permissionView(accountId)
    } }
    override suspend fun restrict(input: DenialChange): PermissionView = gate.withLock { commands.execute {
        val user = requireRight("ADMINISTRATION:UPDATE")
        if (user.role != "owner") fail(SecurityError.FORBIDDEN)
        val target = permissionView(input.accountId)
        if (target.role == "owner") fail(SecurityError.FORBIDDEN)
        if (input.denied.size > 70 || input.expectedDenied.size > 70 || !target.inherited.containsAll(input.denied)) fail(SecurityError.INVALID_INPUT)
        if (input.expectedRole != target.role || input.expectedDenied != target.denied) fail(SecurityError.CONFLICT)
        if (security.hasOpenCash(target.accountId)) fail(SecurityError.CASH_OPEN)
        security.replaceDenials(target.accountId, input.denied)
        audit(user.id, "permissions_restricted", "user", target.accountId.toString())
        permissionView(target.accountId)
    } }
    override suspend fun settings(): FoundationSettings = gate.withLock { owner.read {
        requireRight("SETTINGS:READ")
        FoundationSettings(settings.value("storeName") ?: "STORE", settings.value("address") ?: "", settings.value("phone") ?: "", settings.value("email") ?: "", settings.value("currency") ?: "EUR", settings.value("discountsEnabled") != "false")
    } }
    override suspend fun configure(input: FoundationSettings): Unit = gate.withLock { commands.execute {
        requireRight("SETTINGS:UPDATE")
        val values = mapOf("storeName" to input.storeName, "address" to input.address, "phone" to input.phone, "email" to input.email, "currency" to input.currency, "discountsEnabled" to input.discountsEnabled.toString())
        if (input.storeName.isBlank() || input.currency !in setOf("EUR", "XOF", "XAF", "CAD", "GBP", "CHF", "NGN", "GHS") || values.values.any { it.length > 1000 || it.any { c -> c == '\r' || c == '\n' || c == '\u0000' } } || input.email.isNotEmpty() && !Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+").matches(input.email)) fail(SecurityError.INVALID_INPUT)
        values.forEach { (key, value) -> settings.put(key, value) }
    } }
    override suspend fun preferences(): DisplayPreferences = gate.withLock { owner.read {
        DisplayPreferences(settings.value("displayLanguage") ?: "fr", settings.value("displayTheme") ?: "system")
    } }
    override suspend fun preferences(input: DisplayPreferences): Unit = gate.withLock {
        if (input.language !in setOf("fr", "en") || input.theme !in setOf("light", "dark", "system")) fail(SecurityError.INVALID_INPUT)
        commands.execute { settings.put("displayLanguage", input.language); settings.put("displayTheme", input.theme) }
    }
}
