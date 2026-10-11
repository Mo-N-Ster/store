package com.vibe.store.infrastructure.persistence

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.vibe.store.application.persistence.IntegrityResult
import com.vibe.store.application.security.NewOwner
import com.vibe.store.domain.CatalogRecord
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch

private fun harnessRoot(context: android.content.Context, id: String): File {
    require(id.matches(Regex("[A-Za-z0-9-]{1,48}")))
    return File(context.noBackupFilesDir, "i12-process-tests/$id")
}

private fun File.writeDurably(value: String) {
    check(parentFile?.isDirectory == true)
    FileOutputStream(this).use { it.write(value.toByteArray(Charsets.UTF_8)); it.fd.sync() }
}

/** Synthetic-only fixture builder visible to app androidTest via the debug variant. */
object I12ProcessTestFixtures {
    @JvmStatic fun createGeneration(context: android.content.Context, base: File, productName: String, imageReference: String): File {
        val owner = ActiveStoreOwner(context, base)
        return kotlinx.coroutines.runBlocking {
          try {
            check(owner.verifyIntegrity() == IntegrityResult(true, true, true))
            val actorId = owner.transaction {
                security.createOwner(NewOwner("fixture", "owner@example.test", "Synthetic", "Owner",
                    "synthetic-verifier", "question", "answer", "2026-10-10T00:00:00Z"))
            }
            check(actorId == 1L)
            val media = File(base, "generations/${owner.generation.value}/media/articles/$imageReference")
            check(media.parentFile!!.mkdirs())
            media.writeBytes("RIFF".toByteArray() + byteArrayOf(0, 0, 0, 0) + "WEBP".toByteArray())
            owner.transaction {
                catalog.write(CatalogRecord(1, productName, "Synthetic", "#i12", "fixture", BigDecimal("1.2300"),
                    5, 1, "2026-10-10T00:00:00Z", "2026-10-10T00:00:00Z", null, imageReference))
                catalog.movement(1, 5, "initial", BigDecimal("1.2300"), "2026-10-10T00:00:00Z", null)
                catalog.price(1, BigDecimal("1.2300"), "2026-10-10T00:00:00Z")
            }
            owner.createBackup()
          } finally { owner.close() }
        }
    }
}

/** Debug-only service process used by instrumentation to kill recovery at durable protocol boundaries. */
class I12RecoveryCrashService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(EXTRA_ID) ?: error("Missing recovery harness id")
        val boundary = intent.getStringExtra(EXTRA_BOUNDARY) ?: error("Missing boundary")
        val actorId = intent.getLongExtra(EXTRA_ACTOR, -1)
        val operation = intent.getStringExtra(EXTRA_OPERATION) ?: "restore"
        scope.launch {
            val root = harnessRoot(applicationContext, id)
            val owner = ActiveStoreOwner(applicationContext, root, boundaryProbe = { point ->
                if (point == boundary) {
                    File(root, "boundary.pid").writeDurably("$point|${Process.myPid()}|${Process.myUid()}")
                    CountDownLatch(1).await()
                }
            })
            try {
                if (operation == "reset") owner.resetStore(actorId)
                else {
                    require(operation == "restore")
                    val archive = File(root, "input.storebackup")
                    owner.restoreBackup(archive.inputStream(), actorId)
                }
                File(root, "operation.result").writeDurably("COMPLETED|${Process.myPid()}")
            } catch (failure: Throwable) {
                File(root, "operation.result").writeDurably("FAILED|${failure.javaClass.name}")
            } finally {
                runCatching { owner.close() }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        const val EXTRA_ID = "i12.id"
        const val EXTRA_BOUNDARY = "i12.boundary"
        const val EXTRA_ACTOR = "i12.actor"
        const val EXTRA_OPERATION = "i12.operation"
    }
}

/** Separate post-kill process that verifies recovery only from durable Room/generation state. */
class I12RecoveryVerifyService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(I12RecoveryCrashService.EXTRA_ID) ?: error("Missing recovery harness id")
        val processStartToken = android.os.SystemClock.elapsedRealtimeNanos()
        scope.launch {
            val root = harnessRoot(applicationContext, id)
            val owner = ActiveStoreOwner(applicationContext, root)
            try {
                val integrity = owner.verifyIntegrity()
                val product = owner.read { catalog.find(1)?.name ?: "<missing>" }
                val activeOwners = owner.read { security.accounts().count { it.role == "owner" && it.active } }
                val generation = owner.generation.value
                val orphanStaging = File(root, "maintenance/staging").listFiles().orEmpty().size
                File(root, "verified.result").writeDurably(
                    "${Process.myPid()}|$processStartToken|$generation|$product|$activeOwners|${integrity.quick}|${integrity.full}|${integrity.foreignKeys}|$orphanStaging",
                )
            } catch (failure: Throwable) {
                File(root, "verified.result").writeDurably("${Process.myPid()}|$processStartToken|ERROR|${failure.javaClass.name}|0")
            } finally {
                runCatching { owner.close() }
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
