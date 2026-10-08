package com.vibe.store.infrastructure.persistence

import java.math.BigDecimal
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vibe.store.api.*
import com.vibe.store.application.persistence.CommandCoordinator
import com.vibe.store.application.sales.*
import com.vibe.store.application.security.*
import com.vibe.store.application.team.TeamAuthority
import com.vibe.store.infrastructure.media.AndroidProfilePhotoMedia
import com.vibe.store.infrastructure.security.BcryptPasswords
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** G-SALE/G-RF004/G-CANCEL: isolated real Room, not business data. */
class SaleNativeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private inner class Fixture {
        val owner = RoomDatabaseOwner(context, File(context.noBackupFilesDir, "i06-native/${UUID.randomUUID()}"))
        val commands = CommandCoordinator(owner)
        val identity = IdentityAuthority(owner, commands, BcryptPasswords())
        var failAt: SaleBoundary? = null
        val sales = SaleAuthority(identity, probe = SaleProbe { if (it == failAt) error("synthetic rollback") })
        var cashId = 0L
        suspend fun init() {
            identity.bootstrap(OwnerRegistration("owner", "owner@example.invalid", "Synthetic", "Owner", "Password-123", "Question", "Answer"))
            identity.login(Credentials("owner", "Password-123"))
            commands.execute {
                (this as RoomRepositories).dao.product(ProductEntity(1, "Rice", "Food", BigDecimal("10.0"), 20, 1, "2026-10-03", "2026-10-03"))
            }
            cashId = sales.openCash(BigDecimal("5.0")).id
        }
        fun command(key: String = "synthetic-key", qty: Long = 2) = SaleCommand(key, cashId, listOf(SaleLine(1, qty)), received = BigDecimal("25.0"))
        suspend fun snapshot(): List<Any> = owner.read {
            val dao = (this as RoomRepositories).dao
            listOf(dao.findProduct(1)!!.stock,
                dao.salePage("", "", "", null, "", 1000, 0),
                dao.nextInvoiceLineId(), dao.nextPaymentId(),
                catalog.movements(MovementFilter(limit = 200)))
        }
        suspend fun employee() {
            TeamAuthority(identity, AndroidProfilePhotoMedia.fixture { byteArrayOf().inputStream() }).create(
                EmployeeCreation("employee", "Test", "Employee", null, null, null, "employee", "EMP-I06", "ACTIVE", null,
                    "Password-123", false, null))
        }
    }
    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val f = Fixture()
        try { f.init(); block(f); assertTrue(f.owner.verifyIntegrity().let { it.quick && it.full && it.foreignKeys }) }
        finally { f.owner.close() }
    }
    private suspend fun denied(block: suspend () -> Unit) {
        try { block(); fail("Expected safe denial") }
        catch (_: SaleFailure) { }
        catch (_: SecurityFailure) { }
    }
    @Test fun exactConcurrentReplayAfterStockConsumptionHasOneDurableEffect() = runBlocking { fixture { f ->
        val command = SaleCommand("concurrent", f.cashId, listOf(SaleLine(1, 20)))
        val receipts = coroutineScope { (1..2).map { async { f.sales.sell(command) } }.awaitAll() }
        assertEquals(receipts[0], receipts[1])
        assertEquals(0L, f.owner.read { catalog.find(1)!!.stock })
        val snapshot = f.snapshot()
        assertEquals(receipts[0], f.sales.sell(command))
        assertEquals(snapshot, f.snapshot())
        f.owner.checkpointCloseReopen()
        assertEquals(receipts[0], f.sales.resolve(command.key))
        assertEquals(1, f.sales.history().items.size)
    } }
    @Test fun changedIntentAndHistoricalNullAreDeniedWithoutMutation() = runBlocking { fixture { f ->
        val command = f.command()
        val receipt = f.sales.sell(command)
        val snapshot = f.snapshot()
        for (other in listOf(
            SaleCommand(command.key, f.cashId, listOf(SaleLine(1, 1)), received = BigDecimal("25.0")),
            SaleCommand(command.key, f.cashId, command.lines, BigDecimal("0.001"), BigDecimal("25.0")),
            SaleCommand(command.key, f.cashId, command.lines, received = null),
            SaleCommand(command.key, f.cashId, listOf(SaleLine(1, 1), SaleLine(1, 1)), received = BigDecimal("25.0")),
            SaleCommand(command.key, f.cashId + 1, command.lines, received = BigDecimal("25.0")),
            SaleCommand(command.key, f.cashId, command.lines, received = BigDecimal("26.0")),
        )) { denied { f.sales.sell(other) }; assertEquals(snapshot, f.snapshot()) }
        f.commands.execute { val dao = (this as RoomRepositories).dao
            dao.updateInvoice(dao.findInvoice(receipt.id)!!.copy(canonicalVersion = null, canonicalRequest = null)) }
        val historical = f.snapshot()
        denied { f.sales.sell(command) }; assertEquals(historical, f.snapshot())
        f.commands.execute { val dao = (this as RoomRepositories).dao
            dao.updateInvoice(dao.findInvoice(receipt.id)!!.copy(canonicalVersion = 999, canonicalRequest = "unknown")) }
        val unknown = f.snapshot()
        denied { f.sales.sell(command) }; assertEquals(unknown, f.snapshot())
    } }
    @Test fun duplicateMultisetOrderAndOmittedReceiptRemainDistinct() = runBlocking { fixture { f ->
        val lines = listOf(SaleLine(1, 1), SaleLine(1, 2))
        val receipt = f.sales.sell(SaleCommand("multi", f.cashId, lines))
        assertEquals(2, receipt.lines.size)
        assertEquals(receipt, f.sales.sell(SaleCommand("multi", f.cashId, lines.reversed())))
        val snapshot = f.snapshot()
        denied { f.sales.sell(SaleCommand("multi", f.cashId, lines, received = BigDecimal("30.0"))) }
        assertEquals(snapshot, f.snapshot())
        denied { f.sales.sell(SaleCommand("oversell", f.cashId, listOf(SaleLine(1, 10), SaleLine(1, 10)))) }
        assertEquals(snapshot, f.snapshot())
    } }
    @Test fun actorPermissionCashAndCancellationCannotGrantReplayAuthority() = runBlocking { fixture { f ->
        f.employee()
        val receipt = f.sales.sell(f.command())
        val before = f.snapshot()
        f.sales.closeCash(f.cashId, BigDecimal("25.0"))
        denied { f.sales.sell(f.command()) }; assertEquals(before, f.snapshot())
        f.identity.switchUser(Credentials("employee", "Password-123"))
        val employeeCash = f.sales.openCash(BigDecimal("0.0"))
        denied { f.sales.sell(SaleCommand("synthetic-key", employeeCash.id, listOf(SaleLine(1, 2)), received = BigDecimal("25.0"))) }
        denied { f.sales.cancel(receipt.id, "Forbidden") }
        f.commands.execute { security.replaceDenials(2, setOf("POS:VALIDATE")) }
        denied { f.sales.sell(SaleCommand("denied", employeeCash.id, listOf(SaleLine(1, 1)))) }
        assertEquals(before, f.snapshot())
    } }
    @Test fun everyWriterBoundaryRollsBackAndJournalAllowsExplicitResolution() = runBlocking { fixture { f ->
        for (boundary in listOf(SaleBoundary.INVOICE, SaleBoundary.LINE, SaleBoundary.STOCK,
            SaleBoundary.MOVEMENT, SaleBoundary.PAYMENT, SaleBoundary.BEFORE_COMMIT)) {
            val before = f.snapshot()
            f.failAt = boundary
            try { f.sales.sell(f.command("rollback-${boundary.name}")); fail("Expected injection") }
            catch (failure: IllegalStateException) { assertEquals("synthetic rollback", failure.message) }
            f.failAt = null
            assertEquals(before, f.snapshot())
            f.owner.checkpointCloseReopen()
            assertEquals(before, f.snapshot())
            f.sales.abandon("rollback-${boundary.name}")
            denied { f.sales.resolve("rollback-${boundary.name}") }
        }
        assertTrue(f.sales.pending().isEmpty())
    } }
    @Test fun cancellationIsAtomicOnceAndDoesNotRewriteClosedCashSnapshot() = runBlocking { fixture { f ->
        val receipt = f.sales.sell(f.command())
        val closed = f.sales.closeCash(f.cashId, BigDecimal("25.0"))
        f.failAt = SaleBoundary.MOVEMENT
        val before = f.snapshot()
        try { f.sales.cancel(receipt.id, "Synthetic correction"); fail("Expected rollback") }
        catch (_: IllegalStateException) { }
        assertEquals(before, f.snapshot())
        f.failAt = null
        val cancelled = f.sales.cancel(receipt.id, "Synthetic correction")
        assertEquals("REFUNDED", cancelled.paymentStatus)
        assertEquals(20L, f.owner.read { catalog.find(1)!!.stock })
        val after = f.snapshot()
        assertEquals(cancelled, f.sales.cancel(receipt.id, "Repeated request"))
        assertEquals(after, f.snapshot())
        assertEquals(closed, f.sales.cashHistory().single())
        denied { f.sales.sell(f.command()) }
    } }
    @Test fun openIsIdempotentSwitchIsGuardedAndNoImplicitAttendance() = runBlocking { fixture { f ->
        f.employee()
        assertEquals(f.cashId, f.sales.openCash(BigDecimal("50.0")).id)
        denied { f.identity.switchUser(Credentials("employee", "Password-123")) }
        denied { f.identity.logout() }
        assertNull(f.owner.read { attendance.attendance(1) })
        f.sales.closeCash(f.cashId, BigDecimal("5.0"))
        f.identity.switchUser(Credentials("employee", "Password-123"))
        assertNull(f.sales.currentCash())
        assertNull(f.owner.read { attendance.attendance(1) })
    } }

    @Test fun closingDifferenceInvalidReasonsAndAuditAreDurable() = runBlocking { fixture { f ->
        val opened = f.sales.currentCash()!!
        assertTrue(BigDecimal("5.0").compareTo(opened.opening) == 0)
        assertTrue(BigDecimal("5.0").compareTo(opened.expected) == 0)

        val openedAudit = f.owner.read {
            val dao = (this as RoomRepositories).dao
            dao.findAudit(dao.nextAuditId() - 1)!!
        }
        assertEquals("cash_session_opened", openedAudit.action)

        val receipt = f.sales.sell(f.command("cash-audit-check"))
        assertTrue(BigDecimal("20.0").compareTo(receipt.total) == 0)

        val beforeClose = f.sales.currentCash()!!
        assertTrue(BigDecimal("25.0").compareTo(beforeClose.expected) == 0)

        val beforeInvalid = f.snapshot()

        for (reason in listOf("", " ", "ab", " a ")) {
            denied { f.sales.cancel(receipt.id, reason) }
            assertEquals(beforeInvalid, f.snapshot())
        }

        val closed = f.sales.closeCash(f.cashId, BigDecimal("23.0"))
        assertEquals("CLOSED", closed.status)
        assertTrue(BigDecimal("25.0").compareTo(closed.expected) == 0)
        assertTrue(BigDecimal("23.0").compareTo(closed.counted!!) == 0)
        assertTrue(BigDecimal("-2").compareTo(closed.difference!!) == 0)
        assertEquals(closed, f.sales.cashHistory().single())

        val closingAudit = f.owner.read {
            val dao = (this as RoomRepositories).dao
            dao.findAudit(dao.nextAuditId() - 1)!!
        }
        assertEquals("cash_session_closed", closingAudit.action)
        assertEquals("cash_session", closingAudit.entity)
        assertEquals(f.cashId.toString(), closingAudit.entityId)
        assertEquals("SUCCESS", closingAudit.outcome)

        val cancelled = f.sales.cancel(receipt.id, "Synthetic correction")
        assertEquals("REFUNDED", cancelled.paymentStatus)
        assertEquals(20L, f.owner.read { catalog.find(1)!!.stock })

        val cancellationAudit = f.owner.read {
            val dao = (this as RoomRepositories).dao
            dao.findAudit(dao.nextAuditId() - 1)!!
        }
        assertEquals("invoice_cancelled", cancellationAudit.action)
        assertEquals("invoice", cancellationAudit.entity)
        assertEquals(receipt.id, cancellationAudit.entityId)
        assertEquals("SUCCESS", cancellationAudit.outcome)

        assertEquals(closed, f.sales.cashHistory().single())
        assertNull(f.sales.currentCash())
    } }

    @Test fun revokedOrDisabledActorCannotReplayOwnSale() = runBlocking { fixture { f ->
        f.employee()
        f.sales.closeCash(f.cashId, BigDecimal("5.0"))
        f.identity.switchUser(Credentials("employee", "Password-123"))

        val actor = f.identity.current()!!.id
        val cash = f.sales.openCash(BigDecimal("0.0"))
        val command = SaleCommand(
            "actor-revocation",
            cash.id,
            listOf(SaleLine(1, 2)),
            received = BigDecimal("25.0")
        )

        // The actor must first create a genuinely authorized sale.
        val receipt = f.sales.sell(command)
        assertEquals(actor, receipt.actorId)
        assertEquals(18L, f.owner.read { catalog.find(1)!!.stock })

        // Test-only adversarial permission change. The ordinary
        // administration API correctly forbids this with an open cash.
        f.commands.execute {
            security.replaceDenials(actor, setOf("POS:VALIDATE"))
        }

        val beforeRevokedReplay = f.snapshot()
        val pendingBefore = f.owner.read {
            (this as RoomRepositories).dao.pendingByKey(command.key)
        }

        denied { f.sales.sell(command) }
        denied { f.sales.resolve(command.key) }

        assertEquals(beforeRevokedReplay, f.snapshot())
        assertEquals(pendingBefore, f.owner.read {
            (this as RoomRepositories).dao.pendingByKey(command.key)
        })

        // Restoring permission must allow only the exact original replay.
        f.commands.execute {
            security.replaceDenials(actor, emptySet())
        }

        assertEquals(receipt, f.sales.sell(command))
        assertEquals(beforeRevokedReplay, f.snapshot())

        // Test-only adversarial deactivation of the same signed-in actor.
        f.commands.execute {
            val dao = (this as RoomRepositories).dao
            val account = dao.account(actor)!!
            dao.updateUser(account.copy(active = false))
        }

        val beforeDisabledReplay = f.snapshot()

        denied { f.sales.sell(command) }
        denied { f.sales.resolve(command.key) }

        assertEquals(beforeDisabledReplay, f.snapshot())
        assertEquals(18L, f.owner.read { catalog.find(1)!!.stock })
        assertEquals(1, f.owner.read {
            (this as RoomRepositories).dao.salePage(
                "", "", "", null, "", 100, 0
            ).size
        })
    } }
}
