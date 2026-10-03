package com.vibe.store.domain

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object TeamPolicy {
    val employmentStates = setOf(
        "ACTIVE",
        "ABSENT",
        "SUSPENDED",
        "RESIGNED",
        "ARCHIVED",
    )

    val attendanceStates = setOf(
        "VALID",
        "CORRECTED",
        "INTERRUPTED",
    )

    val accountRoles = setOf(
        "owner",
        "manager",
        "employee",
    )

    fun employment(value: String): String =
        value.trim().also { require(it in employmentStates) }

    fun role(value: String): String =
        value.trim().also { require(it in accountRoles) }

    fun requiredText(value: String): String =
        value.trim().also {
            require(it.isNotEmpty())
            require(it.none { c -> c == '\u0000' || c == '\r' || c == '\n' })
        }

    fun optionalText(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() }?.also {
            require(it.none { c -> c == '\u0000' || c == '\r' || c == '\n' })
        }

    fun email(value: String?): String? {
        val normalized = optionalText(value)?.lowercase(Locale.ROOT) ?: return null
        require(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+").matches(normalized))
        return normalized
    }

    fun password(value: String): String =
        value.also { require(it.length >= 8) }

    fun correctionReason(value: String): String =
        requiredText(value).also { require(it.length >= 3) }

    fun timestamp(value: String): Long {
        // Persisted attendance timestamps are UTC and lexicographically
        // searchable in SQLite; reject parseable but non-canonical strings.
        require(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z").matches(value))
        val formatter = SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            Locale.ROOT,
        ).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }

        val position = ParsePosition(0)
        val parsed = formatter.parse(value, position)

        require(parsed != null)
        require(position.index == value.length)
        require(formatter.format(parsed) == value)

        return parsed.time
    }

    fun interval(start: String, end: String): Long {
        val startMillis = timestamp(start)
        val endMillis = timestamp(end)

        require(endMillis > startMillis)

        return endMillis - startMillis
    }

    fun completedDuration(start: String, end: String?): Long =
        if (end == null) 0L else interval(start, end)

    fun validateAttendance(
        status: String,
        start: String,
        end: String?,
    ) {
        require(status in attendanceStates)
        timestamp(start)

        if (end != null) {
            interval(start, end)
        }
    }
}
