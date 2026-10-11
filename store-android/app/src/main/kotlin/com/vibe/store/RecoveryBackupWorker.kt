package com.vibe.store

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class RecoveryBackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val store = applicationContext as StoreApplication
        return try {
            // needsOwner() consults the persisted owner record, not the
            // volatile signed-in session, so a scheduled backup survives
            // process death while still stopping after Owner removal.
            if (store.identity.needsOwner()) Result.success()
            else { store.performAutomaticBackup(); Result.success() }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { Result.retry() }
    }
}

object RecoveryBackupScheduler {
    private const val NAME = "store-i12-daily-backup"
    fun enqueue(context: Context) {
        val request = PeriodicWorkRequestBuilder<RecoveryBackupWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .addTag("store-i12-daily-backup")
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            NAME, ExistingPeriodicWorkPolicy.KEEP, request,
        )
    }
}
