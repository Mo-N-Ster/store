package com.vibe.store

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.vibe.store.api.OwnerRegistration
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryBackupSchedulerTest {
    private val restartWork = "i12-worker-process-restart"
    private val restartMarker get() = File(InstrumentationRegistry.getInstrumentation().targetContext.noBackupFilesDir,
        "i12-worker-process-restart-baseline.txt")

    @Test fun ownerAuthenticationSchedulesOnlyOnePeriodicBackup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = WorkManager.getInstance(context)
        val name = "store-i12-daily-backup"
        manager.cancelUniqueWork(name).result.get(10, TimeUnit.SECONDS)
        try {
            RecoveryBackupScheduler.enqueue(context)
            RecoveryBackupScheduler.enqueue(context)
            val work = manager.getWorkInfosForUniqueWork(name).get(10, TimeUnit.SECONDS)
            assertEquals(1, work.size)
            val scheduled = work.single()
            assertTrue(scheduled.state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING))
            assertTrue(scheduled.tags.contains(name))
            assertTrue(scheduled.constraints.requiresStorageNotLow())
        } finally { manager.cancelUniqueWork(name).result.get(10, TimeUnit.SECONDS) }
    }

    @Test fun actualWorkerSkipsWithoutOwnerThenWritesVerifiableAutomaticBackup() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as StoreApplication
        val manager = WorkManager.getInstance(context)
        val periodicName = "store-i12-daily-backup"
        val runName = "i12-worker-${UUID.randomUUID()}"
        val backupDirectory = File(context.noBackupFilesDir, "backups")
        fun automaticArchives() = (backupDirectory.listFiles() ?: emptyArray())
            .filter { it.isFile && it.name.startsWith("auto-") && it.name.endsWith(".storebackup") }
        fun executeWorker(): WorkInfo {
            val request = OneTimeWorkRequestBuilder<RecoveryBackupWorker>().build()
            manager.enqueueUniqueWork(runName, ExistingWorkPolicy.REPLACE, request).result.get(10, TimeUnit.SECONDS)
            val deadline = android.os.SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(90)
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                val info = manager.getWorkInfoById(request.id).get(10, TimeUnit.SECONDS) ?: continue
                if (info.state.isFinished) return info
                android.os.SystemClock.sleep(200)
            }
            error("Real WorkManager backup worker did not finish within 90 seconds")
        }
        try {
            val needsOwner = app.identity.needsOwner()
            if (needsOwner) {
                val before = automaticArchives().map { it.name }.toSet()
                assertEquals(WorkInfo.State.SUCCEEDED, executeWorker().state)
                assertEquals("Worker without a persisted Owner must not create an archive", before,
                    automaticArchives().map { it.name }.toSet())
                app.identity.bootstrap(OwnerRegistration("i12-worker-owner", "i12-worker@example.invalid", "Synthetic",
                    "Worker", "Synthetic-Worker-Password-123", "synthetic recovery question", "synthetic answer"))
            }
            // The real Worker is session-independent by design. A previous UI
            // test may have left a different synthetic Owner in this disposable
            // app profile, so do not assume or replace that account here.
            val before = automaticArchives().map { it.name }.toSet()
            assertEquals(WorkInfo.State.SUCCEEDED, executeWorker().state)
            val created = automaticArchives().filterNot { it.name in before }
            assertTrue("Worker should publish an automatic archive", created.isNotEmpty())
            created.forEach { archive ->
                assertTrue(archive.length() > 0L)
                ZipFile(archive).use { zip ->
                    val names = zip.entries().asSequence().map { it.name }.toSet()
                    assertTrue("Archive must contain its manifest", "manifest.txt" in names)
                    assertTrue("Archive must contain the database", "database/store.db" in names)
                    val header = zip.getInputStream(zip.getEntry("manifest.txt")).bufferedReader().use { it.readLine() }
                    assertTrue(header.startsWith("STORE_BACKUP_V1"))
                }
            }
        } finally {
            manager.cancelUniqueWork(runName).result.get(10, TimeUnit.SECONDS)
            manager.cancelUniqueWork(periodicName).result.get(10, TimeUnit.SECONDS)
        }
    }

    @Test fun actualWorkerRequestsRetryWhenSyntheticArchiveDestinationCannotBePublished() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = WorkManager.getInstance(context)
        val app = context.applicationContext as StoreApplication
        runBlocking {
            if (app.identity.needsOwner()) {
                app.identity.bootstrap(OwnerRegistration("i12-retry-owner", "i12-retry@example.invalid", "Synthetic",
                    "Worker", "Synthetic-Retry-Password-123", "synthetic recovery question", "synthetic answer"))
            }
        }

        val name = "i12-worker-write-failure-${UUID.randomUUID()}"
        val backupDirectory = File(context.noBackupFilesDir, "backups")
        val parkedDirectory = File(context.noBackupFilesDir, "i12-worker-backups-${UUID.randomUUID()}")
        val blocker = File(context.noBackupFilesDir, "i12-worker-backups-blocker-${UUID.randomUUID()}")
        require(backupDirectory.isDirectory || backupDirectory.mkdirs())
        require(backupDirectory.renameTo(parkedDirectory))
        try {
            blocker.writeText("synthetic non-directory destination")
            require(blocker.renameTo(backupDirectory))
            val request = OneTimeWorkRequestBuilder<RecoveryBackupWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            manager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request).result.get(10, TimeUnit.SECONDS)
            val deadline = android.os.SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(30)
            var info: WorkInfo? = null
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                info = manager.getWorkInfoById(request.id).get(10, TimeUnit.SECONDS)
                if ((info?.runAttemptCount ?: 0) > 0) break
                android.os.SystemClock.sleep(200)
            }
            assertEquals("A failed automatic archive must be retried", WorkInfo.State.ENQUEUED, info?.state)
            assertTrue("Worker should record the failed execution", (info?.runAttemptCount ?: 0) > 0)
        } finally {
            manager.cancelUniqueWork(name).result.get(10, TimeUnit.SECONDS)
            if (backupDirectory.exists() && !backupDirectory.isDirectory) require(backupDirectory.delete())
            if (!backupDirectory.exists() && parkedDirectory.exists()) require(parkedDirectory.renameTo(backupDirectory))
            if (blocker.exists()) require(blocker.delete())
        }
    }

    /** Phase 1. End the instrumentation/app process externally before this delayed work is due. */
    @Test fun prepareDelayedWorkerForExternalProcessRestart() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as StoreApplication
        if (app.identity.needsOwner()) {
            app.identity.bootstrap(OwnerRegistration("i12-restart-owner", "i12-restart@example.invalid", "Synthetic",
                "Worker", "Synthetic-Restart-Password-123", "synthetic recovery question", "synthetic answer"))
        }
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(restartWork).result.get(10, TimeUnit.SECONDS)
        manager.pruneWork().result.get(10, TimeUnit.SECONDS)
        val before = (File(context.noBackupFilesDir, "backups").listFiles() ?: emptyArray())
            .filter { it.isFile && it.name.startsWith("auto-") && it.name.endsWith(".storebackup") }
            .map { it.name }
        restartMarker.writeText("preparedPid=${android.os.Process.myPid()}\n" + before.joinToString("\n"))
        val request = OneTimeWorkRequestBuilder<RecoveryBackupWorker>()
            .setInitialDelay(60, TimeUnit.SECONDS)
            .build()
        manager.enqueueUniqueWork(restartWork, ExistingWorkPolicy.REPLACE, request).result.get(10, TimeUnit.SECONDS)
        assertEquals(WorkInfo.State.ENQUEUED, manager.getWorkInfoById(request.id).get(10, TimeUnit.SECONDS)?.state)
        restartMarker.appendText("\nrequest=${request.id}")
    }

    /** Phase 2. Run only after an external force-stop and explicit app launch. */
    @Test fun verifyDelayedWorkerCatchUpAfterExternalProcessRestart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = WorkManager.getInstance(context)
        val lines = restartMarker.readLines()
        val preparedPid = lines.single { it.startsWith("preparedPid=") }.substringAfter('=').toInt()
        assertTrue("Verification must run after the preparation process has ended", preparedPid != android.os.Process.myPid())
        val requestId = java.util.UUID.fromString(lines.single { it.startsWith("request=") }.substringAfter('='))
        val deadline = android.os.SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(90)
        var info: WorkInfo? = null
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            info = manager.getWorkInfoById(requestId).get(10, TimeUnit.SECONDS)
            if (info?.state?.isFinished == true) break
            android.os.SystemClock.sleep(200)
        }
        assertEquals("Due WorkManager request must resume after app process restart", WorkInfo.State.SUCCEEDED, info?.state)
        val previous = lines.filterNot { it.startsWith("request=") || it.startsWith("preparedPid=") }
            .filter(String::isNotBlank).toSet()
        val created = (File(context.noBackupFilesDir, "backups").listFiles() ?: emptyArray())
            .filter { it.isFile && it.name.startsWith("auto-") && it.name.endsWith(".storebackup") && it.name !in previous }
        assertTrue("Restarted Worker must publish a new automatic archive", created.isNotEmpty())
        created.forEach { archive -> ZipFile(archive).use { zip ->
            assertTrue(zip.getEntry("manifest.txt") != null)
            assertTrue(zip.getEntry("database/store.db") != null)
        } }
        manager.cancelUniqueWork(restartWork).result.get(10, TimeUnit.SECONDS)
        restartMarker.delete()
    }
}
