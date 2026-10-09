package com.vibe.store.presentation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Local calendar context used to key a dashboard snapshot. */
data class DashboardTimeContext(val date: LocalDate, val zoneId: String) {
    fun nextLocalMidnight(): Instant = date.plusDays(1).atStartOfDay(ZoneId.of(zoneId)).toInstant()
}

/** Injectable seam for the system clock/calendar and its next relevant change. */
interface DashboardTimeSource {
    fun current(): DashboardTimeContext
    suspend fun awaitChange(context: DashboardTimeContext)
}

/** Uses local midnight (including DST length changes) plus Android clock/calendar broadcasts. */
class AndroidDashboardTimeSource(context: Context) : DashboardTimeSource {
    private val appContext = context.applicationContext

    override fun current(): DashboardTimeContext {
        val zone = ZoneId.systemDefault()
        return DashboardTimeContext(LocalDate.now(zone), zone.id)
    }

    override suspend fun awaitChange(context: DashboardTimeContext) {
        val signal = Channel<Unit>(Channel.CONFLATED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { signal.trySend(Unit) }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(receiver, filter)
        }
        try {
            // Close the small race between the caller's context read and receiver registration.
            if (current() != context) signal.trySend(Unit)
            coroutineScope {
                val remainingNanos = Duration.between(Instant.now(), context.nextLocalMidnight()).toNanos().coerceAtLeast(0L)
                val waitMillis = ((remainingNanos + 999_999L) / 1_000_000L).coerceAtLeast(1L)
                // Keep the long scheduled wakeup off Dispatchers.Main so Compose/Espresso
                // can become idle while the screen waits for local midnight.
                val timer = launch(Dispatchers.Default) { delay(waitMillis); signal.trySend(Unit) }
                try { signal.receive() } finally { timer.cancel() }
            }
        } finally {
            appContext.unregisterReceiver(receiver)
            signal.close()
        }
    }
}
