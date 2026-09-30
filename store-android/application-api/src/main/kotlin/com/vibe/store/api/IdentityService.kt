package com.vibe.store.api

// Public responses are allowlists. Credentials are input-only, never data classes
// (generated toString would expose them), never saved state or logged.
class Credentials(val identifier: String, val password: String, val role: String? = null)
class OwnerRegistration(val username: String, val email: String, val firstName: String,
    val lastName: String, val password: String, val question: String, val answer: String)
class RecoveryProof(val accountId: Long, val answer: String, val newPassword: String)
class PasswordDefinition(val accountId: Long, val newPassword: String)
data class PublicIdentity(val id: Long, val username: String, val firstName: String,
    val lastName: String, val role: String, val permissions: Set<String>)
data class RecoveryQuestion(val accountId: Long, val question: String)
data class PermissionView(val accountId: Long, val role: String, val inherited: Set<String>, val denied: Set<String>, val effective: Set<String>)
data class DenialChange(val accountId: Long, val expectedRole: String, val expectedDenied: Set<String>, val denied: Set<String>)
data class FoundationSettings(val storeName: String = "STORE", val address: String = "", val phone: String = "",
    val email: String = "", val currency: String = "EUR", val discountsEnabled: Boolean = true)
data class DisplayPreferences(val language: String = "fr", val theme: String = "system")
enum class SecurityError { INVALID_INPUT, INVALID_CREDENTIALS, UNAVAILABLE, FORBIDDEN, CONFLICT, CASH_OPEN, OPERATION_ACTIVE, CONFIRM_DISCARD, ALREADY_INITIALIZED }
class SecurityFailure(val code: SecurityError) : Exception(code.name)
interface IdentityService {
    suspend fun needsOwner(): Boolean
    suspend fun bootstrap(input: OwnerRegistration): PublicIdentity
    suspend fun login(input: Credentials): PublicIdentity
    suspend fun current(): PublicIdentity?
    suspend fun switchUser(input: Credentials, confirmDiscard: Boolean = false): PublicIdentity
    suspend fun logout(confirmDiscard: Boolean = false)
    suspend fun recoveryQuestion(identifier: String): RecoveryQuestion
    suspend fun recover(input: RecoveryProof)
    suspend fun definePassword(input: PasswordDefinition)
    suspend fun permissions(accountId: Long): PermissionView
    suspend fun restrict(input: DenialChange): PermissionView
    suspend fun settings(): FoundationSettings
    suspend fun configure(input: FoundationSettings)
    suspend fun preferences(): DisplayPreferences
    suspend fun preferences(input: DisplayPreferences)
}
