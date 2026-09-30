package com.vibe.store.infrastructure.persistence

import com.vibe.store.domain.CalendarParity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SourceCalendar(private val localZone: () -> TimeZone) : CalendarParity {
    override fun day(epochMillis: Long, utc: Boolean): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
        timeZone = if (utc) TimeZone.getTimeZone("UTC") else localZone()
    }.format(Date(epochMillis))
}
