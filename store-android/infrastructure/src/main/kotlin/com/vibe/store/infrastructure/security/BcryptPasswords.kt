package com.vibe.store.infrastructure.security

import at.favre.lib.crypto.bcrypt.BCrypt
import at.favre.lib.crypto.bcrypt.LongPasswordStrategies
import com.vibe.store.application.security.PasswordHasher

class BcryptPasswords : PasswordHasher {
    private val version = BCrypt.Version.VERSION_2B
    private val strategy = LongPasswordStrategies.truncate(version)
    override fun hash(value: String): String {
        val bytes = value.toByteArray(Charsets.UTF_8)
        return try { BCrypt.with(version, strategy).hash(10, bytes).toString(Charsets.UTF_8) } finally { bytes.fill(0) }
    }
    override fun verifies(value: String, verifier: String): Boolean {
        val bytes = value.toByteArray(Charsets.UTF_8)
        return try { BCrypt.verifyer(version, strategy).verify(bytes, verifier.toByteArray(Charsets.US_ASCII)).verified }
        catch (_: IllegalArgumentException) { false } finally { bytes.fill(0) }
    }
}
