package com.vibe.store.domain

import java.text.Normalizer
import java.util.Locale

/** ECMAScript whitespace, deliberately not Java's ASCII-only default \s. */
object IdentityPolicy {
    private val spaces = Regex("[\\u0009-\\u000D\\u0020\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+")
    fun fold(value: String): String = spaces.replace(Normalizer.normalize(value, Normalizer.Form.NFC), " ").trim(' ').lowercase(Locale.ROOT)
    fun answer(value: String): String = value.trim { spaces.matches(it.toString()) }.lowercase(Locale.ROOT)
}

object RolePolicy {
    val modules = listOf("DASHBOARD", "PRESENCE", "CASH", "POS", "PRODUCTS", "STOCKS", "PURCHASES", "EMPLOYEES", "FINANCES", "ADMINISTRATION", "SETTINGS", "BACKUPS", "RESTORE", "RESET")
    val actions = listOf("READ", "CREATE", "UPDATE", "DELETE", "VALIDATE")
    val codes = modules.flatMap { module -> actions.map { "$module:$it" } }.toSet()
    fun inherited(role: String): Set<String> = when (role) {
        "owner" -> codes
        "manager" -> codes.filter { it.substringBefore(':') in modules.take(9) && (it.substringBefore(':') != "PRESENCE" || it.endsWith(":READ")) }.toSet()
        "employee" -> setOf("DASHBOARD:READ", "PRESENCE:READ", "PRODUCTS:READ", "STOCKS:READ") +
            listOf("CASH", "POS").flatMap { module -> actions.filter { it != "DELETE" }.map { "$module:$it" } }
        else -> emptySet()
    }
}

data class LockPolicy(val maxAttempts: Int = 5, val minutes: Int = 15) {
    init { require(maxAttempts in 3..10 && minutes in 1..1440) }
    fun next(previous: Int, now: Long): Pair<Int, Long?> {
        val attempts = maxOf(0, previous).coerceAtMost(Int.MAX_VALUE - 1) + 1
        return attempts to if (attempts >= maxAttempts) now + minutes * 60_000L else null
    }
}
