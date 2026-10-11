package com.vibe.store

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.vibe.store.infrastructure.persistence.I12RecoveryCrashService
import com.vibe.store.infrastructure.persistence.I12ProcessTestFixtures
import com.vibe.store.infrastructure.persistence.I12RecoveryVerifyService
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** External process-kill evidence for restore and reset generation-pointer protocols. */
class I12RecoveryProcessDeathTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private data class KillCase(val operation: String, val boundary: String, val newGenerationExpected: Boolean)
    private val cases = buildList {
        addAll(listOf(
            "restore_before_staging", "restore_during_media_staging", "restore_candidate_staged",
            "restore_journal_prepared", "generation_pointer_temp_synced", "restore_pointer_published",
            "restore_complete_before_staging_cleanup",
        ).map { KillCase("restore", it, it in setOf("restore_pointer_published", "restore_complete_before_staging_cleanup")) })
        add(KillCase("reset", "reset_before_pointer_publish", false))
        add(KillCase("reset", "generation_pointer_temp_synced", false))
        add(KillCase("reset", "reset_pointer_published", true))
    }

    @Test fun durableRestoreAndResetBoundariesRecoverAfterThreeExternalProcessKillsEach() {
        val seenVerificationTokens = mutableSetOf<Long>()
        for (testCase in cases) repeat(3) { repetition ->
            val operation = testCase.operation
            val boundary = testCase.boundary
            val expectedNew = testCase.newGenerationExpected
            val id = UUID.randomUUID().toString()
            val root = File(context.noBackupFilesDir, "i12-process-tests/$id").apply { check(mkdirs()) }
            val source = File(context.noBackupFilesDir, "i12-process-source-$id")
            val oldImage = "a1234567-89ab-4cde-8f01-23456789abcd.webp"
            val newImage = "b1234567-89ab-4cde-8f01-23456789abcd.webp"
            try {
                I12ProcessTestFixtures.createGeneration(context, root, "OLD-SYNTHETIC", oldImage)
                val archive = I12ProcessTestFixtures.createGeneration(context, source, "NEW-SYNTHETIC", newImage)
                check(archive.copyTo(File(root, "input.storebackup")).isFile)

                val restoreIntent = Intent(context, I12RecoveryCrashService::class.java)
                    .putExtra(I12RecoveryCrashService.EXTRA_ID, id)
                    .putExtra(I12RecoveryCrashService.EXTRA_BOUNDARY, boundary)
                    .putExtra(I12RecoveryCrashService.EXTRA_ACTOR, 1L)
                    .putExtra(I12RecoveryCrashService.EXTRA_OPERATION, operation)
                check(context.startService(restoreIntent) != null)
                val marker = File(root, "boundary.pid")
                awaitFile(marker, 20_000)
                val markerFields = marker.readText().split('|')
                assertEquals(boundary, markerFields[0])
                val killedPid = markerFields[1].toInt()
                assertNotEquals(Process.myPid(), killedPid)
                assertEquals(Process.myUid(), markerFields[2].toInt())

                // SIGKILL is issued from the separate instrumentation process; no service cleanup/finally runs.
                Process.killProcess(killedPid)
                awaitProcessGone(killedPid, 10_000)

                val verifyIntent = Intent(context, I12RecoveryVerifyService::class.java)
                    .putExtra(I12RecoveryCrashService.EXTRA_ID, id)
                check(context.startService(verifyIntent) != null)
                val result = File(root, "verified.result")
                awaitFile(result, 20_000)
                val fields = result.readText().split('|')
                assertEquals(9, fields.size)
                val verificationPid = fields[0].toInt()
                val verificationToken = fields[1].toLong()
                assertNotEquals(killedPid, verificationPid)
                assertTrue("Verification must run in a new process after each kill", seenVerificationTokens.add(verificationToken))
                assertEquals(if (expectedNew && operation == "reset") "<missing>" else if (expectedNew) "NEW-SYNTHETIC" else "OLD-SYNTHETIC", fields[3])
                assertEquals(if (expectedNew && operation == "reset") "0" else "1", fields[4])
                assertEquals("true", fields[5])
                assertEquals("true", fields[6])
                assertEquals("true", fields[7])
                assertEquals("Interrupted-operation staging must be removed after durable recovery", "0", fields[8])
                if (expectedNew) assertNotEquals("main", fields[2]) else assertEquals("main", fields[2])
                android.util.Log.i("I12_RECOVERY_KILL", "operation=$operation boundary=$boundary repetition=${repetition + 1} killedPid=$killedPid verifyPid=$verificationPid verifyToken=$verificationToken generation=${fields[2]} state=${fields[3]} owners=${fields[4]} integrity=OK")
                // Force a fresh verifier process for the next interruption point.
                Process.killProcess(verificationPid)
                awaitProcessGone(verificationPid, 10_000)
            } finally {
                // These roots are unique test-created noBackup fixtures, never application user data.
                root.deleteRecursively()
                source.deleteRecursively()
            }
        }
    }

    private fun awaitFile(file: File, timeoutMillis: Long) {
        val end = SystemClock.elapsedRealtime() + timeoutMillis
        while (!file.isFile && SystemClock.elapsedRealtime() < end) SystemClock.sleep(25)
        assertTrue("Timed out waiting for ${file.name}", file.isFile)
    }

    private fun awaitProcessGone(pid: Int, timeoutMillis: Long) {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val end = SystemClock.elapsedRealtime() + timeoutMillis
        while (manager.runningAppProcesses.orEmpty().any { it.pid == pid } && SystemClock.elapsedRealtime() < end) {
            SystemClock.sleep(25)
        }
        assertFalse("Killed process $pid is still running", manager.runningAppProcesses.orEmpty().any { it.pid == pid })
    }
}
