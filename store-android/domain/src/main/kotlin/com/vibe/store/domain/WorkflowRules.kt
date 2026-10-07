package com.vibe.store.domain

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

enum class WorkflowRuleError { INVALID_INPUT, CONFLICT, FORBIDDEN, INADMISSIBLE_TARGET, INSUFFICIENT_STOCK }
class WorkflowViolation(val code: WorkflowRuleError) : IllegalArgumentException(code.name)

/** Pure input rules. Bounds are transport/work limits, not an alternative stock engine. */
object WorkflowRules {
    const val MAX_QUANTITY = 9_007_199_254_740_991L
    const val MAX_LINES = 10_000
    fun check(ok: Boolean, code: WorkflowRuleError = WorkflowRuleError.INVALID_INPUT) {
        if (!ok) throw WorkflowViolation(code)
    }
    fun id(value: Long): Long = value.also { check(it in 1..MAX_QUANTITY) }
    fun quantity(value: Long, positive: Boolean = false): Long = value.also {
        check(it in (if (positive) 1L else 0L)..MAX_QUANTITY)
    }
    fun quantity(value: Double, positive: Boolean = false): Long {
        check(value.isFinite() && value >= 0 && value <= MAX_QUANTITY.toDouble() && value == kotlin.math.floor(value))
        return quantity(value.toLong(), positive)
    }
    fun text(value: String, maximum: Int, required: Boolean = false): String = value.trim().also {
        check(it.length <= maximum && (!required || it.isNotEmpty()) && '\u0000' !in it)
    }
    fun page(offset: Int, limit: Int) { check(offset >= 0 && limit in 1..200) }
    fun dates(from: String, to: String) {
        for (value in listOf(from, to)) if (value.isNotEmpty()) {
            check(value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
            val position = ParsePosition(0)
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
                isLenient = false; timeZone = TimeZone.getTimeZone("UTC")
            }.parse(value, position)
            check(parsed != null && position.index == value.length)
        }
        check(from.isEmpty() || to.isEmpty() || from <= to)
    }
    /** Trusted actor/effective rights must come from IdentityAuthority, never from a command DTO. */
    fun access(role: String?, effectiveRights: Set<String>, right: String) {
        check(role in setOf("owner", "manager") && right in effectiveRights, WorkflowRuleError.FORBIDDEN)
    }
}
