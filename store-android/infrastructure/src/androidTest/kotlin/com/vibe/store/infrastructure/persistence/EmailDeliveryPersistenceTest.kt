package com.vibe.store.infrastructure.persistence

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import com.vibe.store.application.persistence.IntegrityResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Uses a same-UID disposable process so a real process death is testable in one instrumentation run. */
class EmailDeliveryPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun queuedEmailSurvivesWriterProcessDeath() = runBlocking {
        val token = UUID.randomUUID().toString().replace("-", "")
        val root = File(context.noBackupFilesDir, "i11/process-restart-$token")
        val dbFile = File(root, "store.db")
        assertTrue("A unique fixture path must not already exist", !dbFile.exists())
        val writerPid = AtomicReference<Int>()
        val error = AtomicReference<String?>()
        val ready = CountDownLatch(1)
        val reply = Messenger(Handler(Looper.getMainLooper()) { message: Message ->
            writerPid.set(message.data.getInt("pid"))
            error.set(message.data.getString("error"))
            ready.countDown()
            true
        })
        context.startService(Intent(context, EmailQueueWriterService::class.java)
            .putExtra(EmailQueueWriterService.EXTRA_TOKEN, token).putExtra(EmailQueueWriterService.EXTRA_REPLY, reply))

        assertTrue("Synthetic writer did not become ready", ready.await(30, TimeUnit.SECONDS))
        assertNull(error.get())
        val preparedPid = writerPid.get()
        assertNotEquals("The writer must be a distinct OS process", Process.myPid(), preparedPid)
        assertTrue("The prepared Room database must exist", dbFile.isFile)
        println("I11 EMAIL QUEUE PREPARED token=$token pid=$preparedPid file=${dbFile.name}")

        Process.killProcess(preparedPid)
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
        while (android.os.SystemClock.elapsedRealtime() < deadline &&
            activityManager.runningAppProcesses.orEmpty().any { it.pid == preparedPid }) {
            Thread.sleep(25)
        }
        assertTrue("Writer process $preparedPid remained alive", activityManager.runningAppProcesses.orEmpty().none { it.pid == preparedPid })

        val owner = RoomDatabaseOwner(context, root, factory = { c, file -> buildStore(c, file) })
        try {
            val row = owner.read { delivery.email(41) }
            assertEquals(7L, row?.actorId)
            assertEquals("synthetic-generation-$token", row?.generation)
            assertEquals("pending", row?.status)
            assertEquals(2, row?.attempts)
            assertEquals("SMTP_TIMEOUT", row?.lastError)
            assertEquals("synthetic@example.invalid", row?.recipient)
            assertEquals(listOf<Byte>(1, 2, 3), row?.attachment?.toList())
            assertEquals(IntegrityResult(true, true, true), owner.verifyIntegrity())
        } finally { owner.close() }
        println("I11 EMAIL QUEUE VERIFIED token=$token writerPid=$preparedPid verifierPid=${Process.myPid()}")
    }
}
