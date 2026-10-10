package com.vibe.store

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

internal const val EMAIL_WORK = "store-authorized-email-delivery"

/** A single unique chain; every invocation resolves current identity and permissions in DeliveryService. */
class EmailDeliveryWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? StoreApplication ?: return Result.failure()
        return try {
            app.delivery.recoverInterruptedAuthorized()
            app.delivery.deliverNextAuthorized()
            val delay = app.delivery.nextAuthorizedDeliveryDelayMillis()
            if (delay != null) EmailDeliveryScheduler.enqueue(applicationContext, delay)
            Result.success()
        } catch (_: com.vibe.store.api.SecurityFailure) {
            // No authenticated session or effective right: wait for a user-initiated reschedule.
            Result.success()
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

object EmailDeliveryScheduler {
    fun enqueue(context: Context, delayMillis: Long = 0L) {
        val request = OneTimeWorkRequestBuilder<EmailDeliveryWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(delayMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(EMAIL_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
