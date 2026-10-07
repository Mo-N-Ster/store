package com.vibe.store.infrastructure.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vibe.store.api.*
import com.vibe.store.application.catalog.CatalogAuthority
import com.vibe.store.application.persistence.CommandCoordinator
import com.vibe.store.application.purchases.*
import com.vibe.store.application.inventory.*
import com.vibe.store.application.security.IdentityAuthority
import com.vibe.store.domain.*
import com.vibe.store.infrastructure.media.AndroidProductMedia
import com.vibe.store.infrastructure.security.BcryptPasswords
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** I07-P2 only: real Room drafts/leases/authorization, no receipt or inventory use case. */
class I07PersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private inner class Fixture {
        val root = File(context.noBackupFilesDir, "i07-p2/${UUID.randomUUID()}")
        val owner = RoomDatabaseOwner(context, File(root, "database"))
        val commands = CommandCoordinator(owner)
        val passwords = BcryptPasswords()
        val identity = IdentityAuthority(owner, commands, passwords)
        val suppliers = SupplierAuthority(identity, { 1_791_072_000_000L })
        val catalog = CatalogAuthority(identity, AndroidProductMedia.fixture(File(root, "media")) { byteArrayOf().inputStream() })
        suspend fun initialize() {
            identity.bootstrap(OwnerRegistration("owner", "owner@example.invalid", "Synthetic", "Owner", "Password-123", "Question", "Answer"))
            identity.login(Credentials("owner", "Password-123"))
        }
        suspend fun user(id: Long, role: String) = commands.execute {
            val dao = (this as RoomRepositories).dao
            dao.user(UserEntity(id, role, passwords.hash("Password-123"), role, "Synthetic", role, "S", "2026-10-04"))
            dao.userRole(UserRoleEntity(id, dao.roles().single { it.code == role }.id, "2026-10-04"))
        }
        suspend fun product() = catalog.save(ProductDraft(name = "Synthetic article", category = "Test", price = 3.0, initialStock = 5.0)).product
    }
    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val f = Fixture(); try { f.initialize(); block(f) } finally { f.owner.close() }
    }
    private suspend fun workflow(code: WorkflowError, block: suspend () -> Unit) {
        try { block(); fail("Expected workflow refusal") } catch (e: WorkflowFailure) { assertEquals(code, e.code) }
    }
    private suspend fun security(block: suspend () -> Unit) {
        try { block(); fail("Expected security refusal") } catch (e: SecurityFailure) { assertTrue(e.code in setOf(SecurityError.FORBIDDEN, SecurityError.INVALID_CREDENTIALS)) }
    }
    private suspend fun rejected(block: suspend () -> Unit) {
        var failed = false
        try { block() } catch (_: Exception) { failed = true }
        assertTrue("Expected persistence refusal", failed)
    }
    private fun purchase(id: Long = 1, supplier: Long? = null, key: String? = "p2-key") = StoredPurchase(
        PurchaseDetail(PurchaseSummary(id, "P2-$id", supplier, PurchaseStatus.DRAFT, 0.0, 1, "2026-10-04T10:00:00Z"), "Supplier invoice", "Note", null, null, null, null, null), key)
    private fun inventory(id: Long = 1) = InventoryView(id, "INV-$id", InventoryStatus.DRAFT, "Count", 1, "2026-10-04T10:00:00Z", null, null)

    @Test fun suppliersCrudBoundedSearchContactsUniquenessAndInactiveHistory() = runBlocking { fixture { f ->
        val a = f.suppliers.save(SaveSupplier(name = " Alpha ", phone = " 123 ", email = " A@EXAMPLE.INVALID ", address = " City "))
        assertEquals("Alpha", a.name); assertEquals("a@example.invalid", a.email); assertEquals("123", a.phone)
        workflow(WorkflowError.CONFLICT) { f.suppliers.save(SaveSupplier(name = "Alpha")) }
        f.suppliers.save(SaveSupplier(name = "alpha")) // Binary SQLite uniqueness, not case-folded.
        f.suppliers.save(SaveSupplier(name = "Beta"))
        val first = f.suppliers.list(SupplierFilter(page = WorkflowPageRequest(0, 1)))
        assertEquals(1, first.items.size); assertTrue(first.hasMore)
        assertEquals(a.id, f.suppliers.list(SupplierFilter(search = "123")).items.single().id)
        assertTrue(f.suppliers.list(SupplierFilter(search = "%")).items.isEmpty())
        val inactive = f.suppliers.save(SaveSupplier(a.id, a.name, active = false, expectedUpdatedAt = a.updatedAt))
        assertNotEquals(a.updatedAt, inactive.updatedAt); assertEquals(a.createdAt, inactive.createdAt)
        assertEquals(inactive, f.suppliers.detail(a.id)); assertFalse(f.suppliers.list().items.any { it.id == a.id })
        assertEquals(a.id, f.suppliers.list(SupplierFilter(active = false)).items.single().id)
        workflow(WorkflowError.CONFLICT) { f.suppliers.save(SaveSupplier(a.id, "Stale", expectedUpdatedAt = a.updatedAt)) }
        for (name in listOf(" ", "x".repeat(201), "bad\u0000name")) workflow(WorkflowError.INVALID_INPUT) { f.suppliers.save(SaveSupplier(name = name)) }
        workflow(WorkflowError.INVALID_INPUT) { f.suppliers.list(SupplierFilter(search = "x".repeat(201))) }
        assertEquals(3, f.suppliers.list(SupplierFilter(active = null)).items.size)
        f.commands.execute { suppliers.insert(SupplierView(4, "Legacy date", "", "", "", true, "2026-10-01", "2026-10-01")) }
        val legacy = f.suppliers.save(SaveSupplier(4, "Legacy edited", expectedUpdatedAt = "2026-10-01"))
        assertEquals("2026-10-01", legacy.createdAt); assertNotEquals("2026-10-01", legacy.updatedAt)
        assertTrue(f.owner.verifyIntegrity().let { it.full && it.foreignKeys })
    } }
    @Test fun currentRightsEmployeeDirectCallsAndInvalidSessionsNeverMutate() = runBlocking { fixture { f ->
        val original = f.suppliers.save(SaveSupplier(name = "Existing"))
        f.user(2, "manager"); f.user(3, "employee")
        f.identity.switchUser(Credentials("manager", "Password-123"))
        assertEquals(original, f.suppliers.detail(original.id)); f.suppliers.save(SaveSupplier(name = "Manager"))
        f.commands.execute { this.security.replaceDenials(2, setOf("PURCHASES:READ", "PURCHASES:UPDATE")) }
        security { f.suppliers.list() }; security { f.suppliers.save(SaveSupplier(name = "Revoked")) }
        f.identity.switchUser(Credentials("employee", "Password-123"))
        security { f.suppliers.list() }; security { f.suppliers.detail(original.id) }
        security { f.suppliers.save(SaveSupplier(name = "Forged")) }
        f.identity.logout(); security { f.suppliers.save(SaveSupplier(name = "No session")) }
        f.identity.login(Credentials("manager", "Password-123"))
        f.commands.execute { val dao = (this as RoomRepositories).dao; dao.updateUser(dao.account(2)!!.copy(active = false)) }
        security { f.suppliers.list() }; security { f.suppliers.save(SaveSupplier(name = "Inactive actor")) }
        f.identity.login(Credentials("owner", "Password-123"))
        assertEquals(2, f.suppliers.list(SupplierFilter(active = null)).items.size)
        assertEquals(original, f.suppliers.detail(original.id))
    } }
    @Test fun durableDraftLinesAndCountsHaveZeroStockEffectAcrossReopen() = runBlocking { fixture { f ->
        val p = f.product(); val movements = f.catalog.movements(); val supplier = f.suppliers.save(SaveSupplier(name = "History"))
        f.commands.execute {
            purchaseRecords.insert(purchase(supplier = supplier.id)); inventoryRecords.insert(inventory())
            assertTrue(purchaseRecords.replaceDraftLine(1, PurchaseLineView(1, p.id, 3, 0.333, 3 * 0.333), null))
            assertTrue(purchaseRecords.updateDraftTotal(1, 3 * 0.333))
            inventoryRecords.insertLine(1, StoredInventoryLine(1, p.id, 5, 5))
            assertTrue(inventoryRecords.recordDraftCount(1, 1, p.id, 7, 5))
            assertFalse(inventoryRecords.recordDraftCount(1, 1, p.id, 8, 5))
        }
        f.suppliers.save(SaveSupplier(supplier.id, supplier.name, active = false, expectedUpdatedAt = supplier.updatedAt))
        f.owner.checkpointCloseReopen()
        f.owner.read {
            assertEquals(supplier.id, purchaseRecords.find(1)!!.detail.summary.supplierId)
            assertEquals(3L, purchaseRecords.lines(1, WorkflowPageRequest()).single().quantity)
            assertEquals(7L, inventoryRecords.lines(1, WorkflowPageRequest()).single().countedQuantity)
            assertEquals(PurchaseStatus.DRAFT, purchaseRecords.find(1)!!.detail.summary.status)
            assertEquals(InventoryStatus.DRAFT, inventoryRecords.find(1)!!.status)
            assertEquals(1, purchaseRecords.page(PurchaseFilter(productId = p.id, page = WorkflowPageRequest(0, 1))).size)
            assertTrue(purchaseRecords.page(PurchaseFilter(from = "2027-01-01")).isEmpty())
            assertTrue(inventoryRecords.page(InventoryFilter(page = WorkflowPageRequest(1, 1))).isEmpty())
        }
        assertEquals(p, f.catalog.detail(p.id)); assertEquals(movements, f.catalog.movements())
        assertTrue(f.owner.verifyIntegrity().let { it.quick && it.full && it.foreignKeys })
    } }
    @Test fun keyCollisionForeignKeysAndTransactionFailureRollbackWithoutMutation() = runBlocking { fixture { f ->
        val p = f.product(); val before = f.catalog.movements()
        f.commands.execute { purchaseRecords.insert(purchase()); inventoryRecords.insert(inventory()) }
        try { f.commands.execute { purchaseRecords.insert(purchase(2)) }; fail("Collision accepted") }
        catch (e: WorkflowViolation) { assertEquals(WorkflowRuleError.CONFLICT, e.code) }
        rejected { f.commands.execute {
            purchaseRecords.insert(purchase(3, key = "other"))
            purchaseRecords.replaceDraftLine(3, PurchaseLineView(1, 999, 1, 1.0, 1.0), null)
        } }
        rejected { f.commands.execute {
            inventoryRecords.recordDraftCount(1, 1, p.id, 0, 5)
            inventoryRecords.insertLine(1, StoredInventoryLine(1, 999, 0, 0))
        } }
        f.owner.read {
            assertNull(purchaseRecords.find(2)); assertNull(purchaseRecords.find(3))
            assertTrue(purchaseRecords.lines(1, WorkflowPageRequest()).isEmpty())
            assertTrue(inventoryRecords.lines(1, WorkflowPageRequest()).isEmpty())
        }
        assertEquals(p, f.catalog.detail(p.id)); assertEquals(before, f.catalog.movements())
        assertTrue(f.owner.verifyIntegrity().foreignKeys)
    } }
    @Test fun conditionalTransitionsPreserveHistoryAndRejectLateDraftWrites() = runBlocking { fixture { f ->
        val p = f.product(); val before = f.catalog.movements()
        f.commands.execute {
            purchaseRecords.insert(purchase()); inventoryRecords.insert(inventory())
            val line = PurchaseLineView(1, p.id, 1, 2.0, 2.0)
            assertTrue(purchaseRecords.replaceDraftLine(1, line, null))
            assertFalse(purchaseRecords.replaceDraftLine(1, line.copy(quantity = 2, total = 4.0), null))
            inventoryRecords.insertLine(1, StoredInventoryLine(1, p.id, 5, 5))
            val cancelled = purchase().copy(detail = purchase().detail.copy(summary = purchase().detail.summary.copy(status = PurchaseStatus.CANCELLED), cancelledBy = 1, cancelledAt = "2026-10-04", cancellationReason = "Synthetic cancellation"))
            assertTrue(purchaseRecords.transition(cancelled, PurchaseStatus.DRAFT))
            assertFalse(purchaseRecords.transition(cancelled, PurchaseStatus.DRAFT))
            assertFalse(purchaseRecords.replaceDraftLine(1, line, line)); assertFalse(purchaseRecords.updateDraftTotal(1, 0.0))
            val validated = inventory().copy(status = InventoryStatus.VALIDATED, validatedBy = 1, validatedAt = "2026-10-04")
            assertTrue(inventoryRecords.markValidated(validated)); assertFalse(inventoryRecords.markValidated(validated))
            assertFalse(inventoryRecords.recordDraftCount(1, 1, p.id, 0, 5))
        }
        // Storage CAS fixture, NOT a receipt/physical-confirmation use case.
        assertEquals(p, f.catalog.detail(p.id)); assertEquals(before, f.catalog.movements())
    } }
    @Test fun concurrentSupplierEditsHaveOneWinnerAndNoLostUpdate() = runBlocking { fixture { f ->
        val a = f.suppliers.save(SaveSupplier(name = "Concurrent"))
        val results = coroutineScope { (1..2).map { n -> async {
            try { f.suppliers.save(SaveSupplier(a.id, "Edit$n", expectedUpdatedAt = a.updatedAt)); true }
            catch (e: WorkflowFailure) { assertEquals(WorkflowError.CONFLICT, e.code); false }
        } }.awaitAll() }
        assertEquals(1, results.count { it }); assertEquals(1, f.suppliers.list().items.size)
    } }
    @Test fun readScopesAndExpiredLeasesCannotWriteOrEscape() = runBlocking { fixture { f ->
        lateinit var escaped: SupplierRecords
        f.owner.read { escaped = suppliers }
        rejected { escaped.find(1) }
        rejected { f.owner.read { suppliers.insert(SupplierView(1, "Forbidden", "", "", "", true, "date", "date")) } }
        rejected { f.owner.read { purchaseRecords.insert(purchase()) } }
        rejected { f.owner.read { inventoryRecords.insert(inventory()) } }
        assertTrue(f.suppliers.list().items.isEmpty())
        f.owner.read { assertNull(purchaseRecords.find(1)); assertNull(inventoryRecords.find(1)) }
    } }
    @Test fun freshOwnerAndIdentityReloadPersistedDraftsWithoutRestoringSession() = runBlocking { fixture { f ->
        val supplier = f.suppliers.save(SaveSupplier(name = "Restart"))
        val p = f.product()
        f.commands.execute {
            purchaseRecords.insert(purchase(supplier = supplier.id))
            inventoryRecords.insert(inventory())
            purchaseRecords.replaceDraftLine(1, PurchaseLineView(1, p.id, 2, 1.0, 2.0), null)
            purchaseRecords.updateDraftTotal(1, 2.0)
            inventoryRecords.insertLine(1, StoredInventoryLine(1, p.id, 5, 6))
        }
        f.owner.close()
        val reopened = RoomDatabaseOwner(context, File(f.root, "database"))
        try {
            val identity = IdentityAuthority(reopened, CommandCoordinator(reopened), f.passwords)
            val service = SupplierAuthority(identity)
            assertNull(identity.current()); security { service.detail(supplier.id) }
            identity.login(Credentials("owner", "Password-123")); assertEquals(supplier, service.detail(supplier.id))
            reopened.read {
                assertEquals(2.0, purchaseRecords.find(1)!!.detail.summary.total, 0.0)
                assertEquals(2L, purchaseRecords.lines(1, WorkflowPageRequest()).single().quantity)
                assertEquals(6L, inventoryRecords.lines(1, WorkflowPageRequest()).single().countedQuantity)
                assertEquals(5L, catalog.product(p.id)!!.stock.value)
            }
            assertTrue(reopened.verifyIntegrity().let { it.quick && it.full && it.foreignKeys })
        } finally { reopened.close() }
    } }
}
