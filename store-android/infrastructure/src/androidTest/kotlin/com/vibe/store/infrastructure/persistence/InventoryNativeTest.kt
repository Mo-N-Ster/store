package com.vibe.store.infrastructure.persistence

import java.math.BigDecimal
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vibe.store.api.*
import com.vibe.store.application.catalog.CatalogAuthority
import com.vibe.store.application.inventory.*
import com.vibe.store.application.persistence.CommandCoordinator
import com.vibe.store.application.security.IdentityAuthority
import com.vibe.store.infrastructure.media.AndroidProductMedia
import com.vibe.store.infrastructure.security.BcryptPasswords
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** I07-P4 synthetic real Room/UoW tests; separate real PID interruption remains P6. */
class InventoryNativeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private inner class Fixture(probe: InventoryProbe) {
        val root = File(context.noBackupFilesDir, "i07-p4/${UUID.randomUUID()}")
        val owner = RoomDatabaseOwner(context, File(root, "database"))
        val commands = CommandCoordinator(owner)
        val passwords = BcryptPasswords()
        val identity = IdentityAuthority(owner, commands, passwords)
        val catalog = CatalogAuthority(identity, AndroidProductMedia.fixture(File(root, "media")) { byteArrayOf().inputStream() })
        val inventories = InventoryAuthority(identity, probe = probe)
        suspend fun initialize() {
            identity.bootstrap(OwnerRegistration("owner", "owner@example.invalid", "Synthetic", "Owner", "Password-123", "Question", "Answer"))
            identity.login(Credentials("owner", "Password-123"))
        }
        suspend fun product(name: String, stock: Double = 5.0) = catalog.save(
            ProductDraft(name = name, category = "Test", price = BigDecimal("3.0"), initialStock = stock)).product
        suspend fun user(id: Long, role: String) = commands.execute {
            val dao = (this as RoomRepositories).dao
            dao.user(UserEntity(id, role, passwords.hash("Password-123"), role, "Synthetic", role, "S", "2026-10-04"))
            dao.userRole(UserRoleEntity(id, dao.roles().single { it.code == role }.id, "2026-10-04"))
        }
        suspend fun count(line: InventoryLineView, id: Long, value: Long) = inventories.recordCount(
            RecordInventoryCount(id, line.id, line.productId, value, line.countedQuantity))
        suspend fun confirmed(id: Long, physically: Boolean = true, token: String? = null): ValidateInventory {
            val handle = if (token == null) inventories.review(id).token else token
            val values = ArrayList<InventoryCountAttestation>()
            var offset = 0
            do {
                val page = inventories.lines(id, WorkflowPageRequest(offset, 200))
                values.addAll(page.items.map { InventoryCountAttestation(it.id, it.productId, it.countedQuantity) })
                offset += page.items.size
            } while (page.hasMore)
            return ValidateInventory(id, handle, physically, values)
        }
    }
    private suspend fun fixture(probe: InventoryProbe = InventoryProbe {}, block: suspend (Fixture) -> Unit) {
        val f = Fixture(probe)
        try { f.initialize(); block(f) } finally { f.owner.close() }
    }
    private suspend fun refused(code: WorkflowError, block: suspend () -> Unit) {
        try { block(); fail("Expected $code") } catch (e: WorkflowFailure) { assertEquals(code, e.code) }
    }
    private suspend fun forbidden(block: suspend () -> Unit) {
        try { block(); fail("Expected security refusal") }
        catch (e: SecurityFailure) { assertEquals(SecurityError.FORBIDDEN, e.code) }
        catch (e: WorkflowFailure) { assertEquals(WorkflowError.FORBIDDEN, e.code) }
    }
    @Test fun draftPrefillsAreDurableButHaveNoStockEffects() = runBlocking { fixture { f ->
        val p = f.product("Draft stock")
        val before = f.catalog.movements()
        val draft = f.inventories.start(StartInventory("Physical count"))
        assertEquals(InventoryStatus.DRAFT, draft.status)
        assertEquals(1, f.inventories.list().items.size)
        assertEquals(draft, f.inventories.detail(draft.id))
        val line = f.inventories.lines(draft.id).items.single()
        assertEquals(p.id, line.productId)
        assertEquals(5L, line.expectedQuantity)
        assertEquals(5L, line.countedQuantity)
        refused(WorkflowError.CONFLICT) {
            f.inventories.validate(ValidateInventory(draft.id, null, true,
                listOf(InventoryCountAttestation(line.id, p.id, 5))))
        }
        assertEquals(before, f.catalog.movements())
        assertEquals(5L, f.catalog.detail(p.id).stock)
        f.owner.checkpointCloseReopen()
        assertEquals(line, f.inventories.lines(draft.id).items.single())
        assertTrue(f.owner.verifyIntegrity().let { it.quick && it.full && it.foreignKeys })
    } }
    @Test fun explicitCompleteAttestationIsRequired() = runBlocking { fixture { f ->
        val a = f.product("First"); val b = f.product("Second")
        val draft = f.inventories.start(StartInventory())
        val review = f.inventories.review(draft.id)
        val rows = f.inventories.lines(draft.id).items
        assertEquals(2, review.lineCount)
        val complete = rows.map { InventoryCountAttestation(it.id, it.productId, it.countedQuantity) }
        refused(WorkflowError.CONFLICT) { f.inventories.validate(ValidateInventory(draft.id, review.token, false, complete)) }
        refused(WorkflowError.CONFLICT) { f.inventories.validate(ValidateInventory(draft.id, review.token, true, complete.dropLast(1))) }
        refused(WorkflowError.CONFLICT) { f.inventories.validate(ValidateInventory(draft.id, review.token, true, complete + complete.first())) }
        refused(WorkflowError.CONFLICT) { f.inventories.validate(ValidateInventory(draft.id, "forged", true, complete)) }
        refused(WorkflowError.CONFLICT) { f.inventories.validate(ValidateInventory(draft.id, review.token, true, complete.map {
            if (it.productId == a.id) it.copy(countedQuantity = it.countedQuantity + 1) else it
        })) }
        assertEquals(InventoryStatus.DRAFT, f.inventories.detail(draft.id).status)
        assertEquals(5L, f.catalog.detail(b.id).stock)
        assertEquals(InventoryStatus.VALIDATED, f.inventories.validate(ValidateInventory(draft.id, review.token, true, complete)).status)
    } }
    @Test fun reconcilingAlwaysUsesCurrentStockNotInitialSnapshot() = runBlocking { fixture { f ->
        val a = f.product("Changed after count")
        val b = f.product("Unchanged stock")
        val draft = f.inventories.start(StartInventory())
        val row = f.inventories.lines(draft.id).items.single { it.productId == a.id }
        f.count(row, draft.id, 7)
        f.catalog.adjustStock(a.id, 9.0, "Movement after draft", 5)
        val before = f.catalog.movements().count { it.reason == "inventory" }
        val confirm = f.confirmed(draft.id)
        f.inventories.validate(confirm)
        assertEquals(7L, f.catalog.detail(a.id).stock)
        assertEquals(5L, f.catalog.detail(b.id).stock)
        val movements = f.catalog.movements().filter { it.reason == "inventory" }
        assertEquals(before + 1, movements.size)
        assertEquals(-2L, movements.single().quantity)
        val stored = f.inventories.lines(draft.id).items.single { it.productId == a.id }
        assertEquals(5L, stored.expectedQuantity)
        assertEquals(7L, stored.countedQuantity)
    } }
    @Test fun repeatedAndConcurrentValidationNeverReapply() = runBlocking { fixture { f ->
        val p = f.product("Exactly once")
        val draft = f.inventories.start(StartInventory())
        val row = f.inventories.lines(draft.id).items.single()
        f.count(row, draft.id, 8)
        val command = f.confirmed(draft.id)
        val outcomes = coroutineScope { (1..2).map { async {
            try { f.inventories.validate(command); true }
            catch (e: WorkflowFailure) { assertEquals(WorkflowError.CONFLICT, e.code); false }
        } }.awaitAll() }
        assertEquals(1, outcomes.count { it })
        assertEquals(8L, f.catalog.detail(p.id).stock)
        assertEquals(1, f.catalog.movements().count { it.reason == "inventory" })
        refused(WorkflowError.CONFLICT) { f.inventories.validate(command) }
    } }
    @Test fun editingCountIncludingAbaRevokesPriorReview() = runBlocking { fixture { f ->
        val p = f.product("ABA")
        val draft = f.inventories.start(StartInventory())
        val row = f.inventories.lines(draft.id).items.single()
        val old = f.confirmed(draft.id)
        val changed = f.count(row, draft.id, 7)
        f.count(changed, draft.id, 5)
        refused(WorkflowError.CONFLICT) { f.inventories.validate(old) }
        assertEquals(InventoryStatus.VALIDATED, f.inventories.validate(f.confirmed(draft.id)).status)
        assertEquals(5L, f.catalog.detail(p.id).stock)
        assertTrue(f.catalog.movements().none { it.reason == "inventory" })
    } }
    @Test fun managerAllowedButEmployeeAndRevokedRightsDeniedDirectly() = runBlocking { fixture { f ->
        f.product("Rights")
        val draft = f.inventories.start(StartInventory())
        f.user(2, "manager"); f.user(3, "employee")
        f.identity.switchUser(Credentials("manager", "Password-123"))
        assertEquals(1, f.inventories.list().items.size)
        val review = f.inventories.review(draft.id)
        f.commands.execute { security.replaceDenials(2, setOf("STOCKS:READ", "STOCKS:VALIDATE")) }
        forbidden { f.inventories.list() }
        forbidden { f.inventories.review(draft.id) }
        forbidden { f.inventories.lines(draft.id) }
        forbidden { f.inventories.validate(ValidateInventory(draft.id, review.token, true, emptyList())) }
        f.identity.switchUser(Credentials("employee", "Password-123"))
        forbidden { f.inventories.list() }
        forbidden { f.inventories.detail(draft.id) }
        forbidden { f.inventories.start(StartInventory()) }
        forbidden { f.inventories.recordCount(RecordInventoryCount(draft.id, 1, 1, 9, 5)) }
        forbidden { f.inventories.review(draft.id) }
        forbidden { f.inventories.validate(ValidateInventory(draft.id, review.token, true, emptyList())) }
        f.identity.switchUser(Credentials("owner", "Password-123"))
        assertEquals(InventoryStatus.DRAFT, f.inventories.detail(draft.id).status)
    } }
    @Test fun archivedArticleIsRejectedWithoutUndoingPriorEffects() = runBlocking { fixture { f ->
        val p = f.product("Archived")
        val draft = f.inventories.start(StartInventory())
        val confirm = f.confirmed(draft.id)
        f.catalog.archive(p.id, p.updatedAt)
        val before = f.catalog.movements()
        refused(WorkflowError.INADMISSIBLE_TARGET) { f.inventories.validate(confirm) }
        assertEquals(before, f.catalog.movements())
        assertEquals(InventoryStatus.DRAFT, f.inventories.detail(draft.id).status)
    } }
    @Test fun failureAfterFirstStockWriteRollsBackEverythingAndMayRetry() = runBlocking {
        val applied = AtomicInteger()
        fixture(InventoryProbe { boundary ->
            if (boundary == InventoryBoundary.STOCK && applied.incrementAndGet() == 2) throw IllegalStateException("synthetic")
        }) { f ->
            val a = f.product("Atomic first"); val b = f.product("Atomic last")
            val draft = f.inventories.start(StartInventory())
            val rows = f.inventories.lines(draft.id).items
            rows.forEach { f.count(it, draft.id, 7) }
            val confirmed = f.confirmed(draft.id)
            val before = f.catalog.movements()
            try { f.inventories.validate(confirmed); fail("Expected injected failure") }
            catch (e: IllegalStateException) { assertEquals("synthetic", e.message) }
            assertEquals(InventoryStatus.DRAFT, f.inventories.detail(draft.id).status)
            assertEquals(5L, f.catalog.detail(a.id).stock); assertEquals(5L, f.catalog.detail(b.id).stock)
            assertEquals(before, f.catalog.movements())
            assertEquals(InventoryStatus.VALIDATED, f.inventories.validate(confirmed).status)
            assertEquals(7L, f.catalog.detail(a.id).stock); assertEquals(7L, f.catalog.detail(b.id).stock)
            assertTrue(f.owner.verifyIntegrity().let { it.full && it.foreignKeys })
        }
    }
    @Test fun freshAuthorityAfterDatabaseReopenRejectsLostVolatileReview() = runBlocking { fixture { f ->
        val p = f.product("Reopened")
        val draft = f.inventories.start(StartInventory())
        val old = f.confirmed(draft.id)
        f.owner.close()
        val reopened = RoomDatabaseOwner(context, File(f.root, "database"))
        try {
            val identity = IdentityAuthority(reopened, CommandCoordinator(reopened), f.passwords)
            val service = InventoryAuthority(identity)
            assertNull(identity.current())
            forbidden { service.detail(draft.id) }
            identity.login(Credentials("owner", "Password-123"))
            refused(WorkflowError.CONFLICT) { service.validate(old) }
            assertEquals(InventoryStatus.DRAFT, service.detail(draft.id).status)
            val row = service.lines(draft.id).items.single()
            assertEquals(5L, row.expectedQuantity); assertEquals(5L, row.countedQuantity)
            val newReview = service.review(draft.id)
            assertEquals(InventoryStatus.VALIDATED, service.validate(
                ValidateInventory(draft.id, newReview.token, true,
                    listOf(InventoryCountAttestation(row.id, row.productId, row.countedQuantity)))).status)
            assertEquals(5L, service.lines(draft.id).items.single().currentStock)
            assertTrue(reopened.verifyIntegrity().let { it.quick && it.full && it.foreignKeys })
        } finally { reopened.close() }
    } }
}
