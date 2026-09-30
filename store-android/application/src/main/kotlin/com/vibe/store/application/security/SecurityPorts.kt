package com.vibe.store.application.security

/** Trusted-only material, never application-api. No generated diagnostic string. */
class AuthAccount(val id: Long, val username: String, val email: String?, val firstName: String,
    val lastName: String, val role: String, val active: Boolean, val employment: String?,
    val verifier: String, val question: String?, val recoveryVerifier: String?,
    val failures: Int, val lockedUntil: Long?, val recoveryFailures: Int, val recoveryLockedUntil: Long?)
class NewOwner(val username: String, val email: String, val firstName: String, val lastName: String,
    val verifier: String, val question: String, val recoveryVerifier: String, val stamp: String)
data class SecurityAudit(val actorId: Long?, val responsibleId: Long?, val action: String,
    val entity: String, val reference: String, val success: Boolean, val stamp: String)
interface SecurityRepository {
    suspend fun accounts(): List<AuthAccount>
    suspend fun account(id: Long): AuthAccount?
    suspend fun createOwner(input: NewOwner): Long
    suspend fun inherited(id: Long): Set<String>
    suspend fun denied(id: Long): Set<String>
    suspend fun replaceDenials(id: Long, codes: Set<String>)
    suspend fun loginResult(id: Long, failures: Int, until: Long?, successAt: String?)
    suspend fun recoveryResult(id: Long, failures: Int, until: Long?, verifier: String?)
    suspend fun hasOpenCash(id: Long): Boolean
    suspend fun appendAudit(event: SecurityAudit)
}
interface PasswordHasher { fun hash(value: String): String; fun verifies(value: String, verifier: String): Boolean }
interface SecretAdapter { fun seal(value: ByteArray): ByteArray; fun open(envelope: ByteArray): ByteArray? }
/** Trusted future operations own these guards; navigation/input cannot clear them. */
class OperationGuards(val critical: () -> Boolean = { false }, val unsavedCart: () -> Boolean = { false })
