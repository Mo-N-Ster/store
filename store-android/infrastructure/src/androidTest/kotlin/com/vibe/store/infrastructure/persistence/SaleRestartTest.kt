package com.vibe.store.infrastructure.persistence

import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.vibe.store.api.*
import com.vibe.store.application.persistence.CommandCoordinator
import com.vibe.store.application.sales.*
import com.vibe.store.application.security.IdentityAuthority
import com.vibe.store.infrastructure.security.BcryptPasswords
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

/** External runner MUST force-stop after ready. Missing phase is not a skipped PASS. */
class SaleRestartTest {
    @Test fun realDeathPreservesSubmittedCommandWithoutAutomaticSale() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val phase = args.getString("salePhase")
        val boundary = args.getString("saleBoundary")
        val run = args.getString("saleRun") ?: error("Explicit run required")
        require(phase in setOf("prepare", "verify") && boundary in setOf("before", "after") && run.matches(Regex("[A-Za-z0-9_-]{1,64}")))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.noBackupFilesDir, "i06-restart-$run-$boundary")
        if (phase == "prepare") require(!File(root, "store.db").exists()) { "Synthetic run already exists" }
        val owner = RoomDatabaseOwner(context, root)
        val commands = CommandCoordinator(owner)
        val identity = IdentityAuthority(owner, commands, BcryptPasswords())
        val marker = File(root, "ready")
        val sales = SaleAuthority(identity, probe = SaleProbe { reached ->
            if (phase == "prepare" && reached == if (boundary == "before") SaleBoundary.BEFORE_COMMIT else SaleBoundary.AFTER_COMMIT) {
                FileOutputStream(marker).use { it.write("${Process.myPid()}\n$boundary\n$run".toByteArray()); it.fd.sync() }
                awaitCancellation()
            }
        })
        try {
            if (phase == "prepare") {
                identity.bootstrap(OwnerRegistration("owner", "owner@example.invalid", "Synthetic", "Owner", "Password-123", "Question", "Answer"))
                identity.login(Credentials("owner", "Password-123"))
                commands.execute { (this as RoomRepositories).dao.product(ProductEntity(1, "Synthetic", "Food", 10.0, 2, 0, "2026-10-03", "2026-10-03")) }
                val cash = sales.openCash(0.0)
                sales.sell(SaleCommand("kill-$run", cash.id, listOf(SaleLine(1, 2))))
                fail("External process death required")
            } else {
                val previous = marker.readLines()
                assertNotEquals(previous[0].toInt(), Process.myPid())
                assertNull(identity.current())
                // Opening and authentication must not submit the retained command.
                suspend fun durable(): Pair<Int, Long> = owner.read {
                    (this as RoomRepositories).dao.salePage("", "", "", null, "", 40, 0).size to catalog.find(1)!!.stock
                }
                val expected = if (boundary == "before") 0 to 2L else 1 to 0L
                assertEquals(expected, durable())
                identity.login(Credentials("owner", "Password-123"))
                assertEquals(expected, durable())
                assertNull(owner.read { attendance.attendance(1) })
                val pending = sales.pending().single()
                assertEquals("kill-$run", pending.key)
                assertEquals("OPEN", sales.currentCash()!!.status)
                val receipt = sales.resolve(pending.key) // explicit authorized resolution
                assertEquals(1 to 0L, durable())
                assertEquals(receipt, sales.resolve(pending.key))
                assertEquals(1 to 0L, durable())
                assertTrue(owner.verifyIntegrity().let { it.quick && it.full && it.foreignKeys })
                println("I06 VERIFIED boundary=$boundary oldPid=${previous[0]} newPid=${Process.myPid()} invoices=1 stock=0")
            }
        } finally { owner.close() }
    }
}
