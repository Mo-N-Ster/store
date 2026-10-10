package com.vibe.store.infrastructure.persistence

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.Process
import com.vibe.store.application.persistence.IntegrityResult
import java.io.File
import java.util.concurrent.CountDownLatch

/** Test-only writer that closes Room, reports its PID, then remains alive until the test kills it. */
class EmailQueueWriterService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val token = intent?.getStringExtra(EXTRA_TOKEN)
        val reply = intent?.getParcelableExtra<Messenger>(EXTRA_REPLY)
        if (token == null || !token.matches(Regex("[A-Fa-f0-9]{32}")) || reply == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        Thread {
            val response = Message.obtain()
            try {
                val root = File(noBackupFilesDir, "i11/process-restart-$token")
                check(!File(root, "store.db").exists()) { "Refusing to replace synthetic fixture" }
                kotlinx.coroutines.runBlocking {
                    val owner = RoomDatabaseOwner(this@EmailQueueWriterService, root, factory = { c, file -> buildStore(c, file) })
                    try {
                        owner.transaction {
                            (this as RoomRepositories).dao.email(EmailEntity(
                                id = 41, actorId = 7, generation = "synthetic-generation-$token",
                                recipient = "synthetic@example.invalid", subject = "Synthetic restart",
                                filename = "synthetic.pdf", message = "Test only", attachment = byteArrayOf(1, 2, 3),
                                status = "pending", attempts = 2, createdAt = "2026-10-10T10:00:00.000Z",
                                nextAttemptAt = "2026-10-10T10:05:00.000Z", lastError = "SMTP_TIMEOUT", sentAt = null,
                            ))
                        }
                        check(owner.verifyIntegrity() == IntegrityResult(true, true, true))
                    } finally { owner.close() }
                }
                response.data.putInt("pid", Process.myPid())
                println("I11 EMAIL QUEUE CHILD READY token=$token pid=${Process.myPid()}")
            } catch (failure: Exception) {
                response.data.putString("error", failure.javaClass.simpleName)
                response.data.putInt("pid", Process.myPid())
            }
            try { reply.send(response) } catch (_: Exception) { stopSelf(startId); return@Thread }
            CountDownLatch(1).await()
        }.start()
        return START_NOT_STICKY
    }

    companion object {
        const val EXTRA_TOKEN = "syntheticToken"
        const val EXTRA_REPLY = "replyMessenger"
    }
}
