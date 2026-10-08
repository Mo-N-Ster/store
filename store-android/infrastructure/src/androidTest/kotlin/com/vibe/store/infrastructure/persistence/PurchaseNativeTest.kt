package com.vibe.store.infrastructure.persistence

import java.math.BigDecimal
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vibe.store.api.*
import com.vibe.store.application.catalog.CatalogAuthority
import com.vibe.store.application.persistence.CommandCoordinator
import com.vibe.store.application.purchases.*
import com.vibe.store.application.sales.SaleAuthority
import com.vibe.store.application.security.IdentityAuthority
import com.vibe.store.infrastructure.media.AndroidProductMedia
import com.vibe.store.infrastructure.security.BcryptPasswords
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** I07-P3 synthetic real-Room qualification; no destructive or customer data. */
class PurchaseNativeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private inner class Fixture(probe: PurchaseProbe) {
        val root = File(context.noBackupFilesDir, "i07-p3/${UUID.randomUUID()}")
        val owner = RoomDatabaseOwner(context, File(root, "database"))
        val commands = CommandCoordinator(owner)
        val passwords = BcryptPasswords()
        val identity = IdentityAuthority(owner, commands, passwords)
        val catalog = CatalogAuthority(identity, AndroidProductMedia.fixture(File(root, "media")) { byteArrayOf().inputStream() })
        val purchases = PurchaseAuthority(identity, catalog, probe = probe)
        val suppliers = SupplierAuthority(identity)
        val sales = SaleAuthority(identity)
        suspend fun initialize() {
            identity.bootstrap(OwnerRegistration("owner", "owner@example.invalid", "Synthetic", "Owner", "Password-123", "Question", "Answer"))
            identity.login(Credentials("owner", "Password-123"))
        }
        suspend fun product(name: String, initial: Double = 5.0) = catalog.save(
            ProductDraft(name = name, category = "Test", price = BigDecimal("3.0"), initialStock = initial)).product
        suspend fun user(id: Long, role: String) = commands.execute {
            val dao = (this as RoomRepositories).dao
            dao.user(UserEntity(id, role, passwords.hash("Password-123"), role, "Synthetic", role, "S", "2026-10-04"))
            dao.userRole(UserRoleEntity(id, dao.roles().single { it.code == role }.id, "2026-10-04"))
        }
        suspend fun draft(product: Long, count: Long = 3, cost: BigDecimal = BigDecimal("2")): PurchaseDetail {
            val d = purchases.createDraft(CreatePurchaseDraft(idempotencyKey = UUID.randomUUID().toString()))
            purchases.saveLine(SavePurchaseLine(d.summary.id, product, count, cost))
            return purchases.detail(d.summary.id)
        }
    }
    private suspend fun fixture(probe: PurchaseProbe = PurchaseProbe {}, block: suspend (Fixture) -> Unit) {
        val f = Fixture(probe)
        try { f.initialize(); block(f) } finally { f.owner.close() }
    }
    private suspend fun refused(code: WorkflowError, block: suspend () -> Unit) {
        try { block(); fail("Expected $code") }
        catch (e: WorkflowFailure) { assertEquals(code, e.code) }
    }
    private suspend fun forbidden(block: suspend () -> Unit) {
        try { block(); fail("Expected security refusal") }
        catch (e: SecurityFailure) { assertEquals(SecurityError.FORBIDDEN, e.code) }
    }
    @Test fun draftReplacementCollisionPagingAndNoStockEffect() = runBlocking { fixture { f ->
        val p = f.product("Draft product")
        val before = f.catalog.movements()
        val d = f.purchases.createDraft(CreatePurchaseDraft(idempotencyKey = "unique-draft"))
        refused(WorkflowError.CONFLICT) { f.purchases.createDraft(CreatePurchaseDraft(idempotencyKey = "unique-draft")) }
        val line = f.purchases.saveLine(SavePurchaseLine(d.summary.id, p.id, 2, BigDecimal("0.333")))
        val updated = f.purchases.saveLine(SavePurchaseLine(d.summary.id, p.id, 3, BigDecimal("0.333"), line))
        assertEquals(line.id, updated.id)
        assertEquals(BigDecimal("0.999"), f.purchases.detail(d.summary.id).summary.total)
        assertEquals(1, f.purchases.lines(d.summary.id).items.size)
        assertEquals(d.summary.id, f.purchases.list().items.single().id)
        assertEquals(p.stock, f.catalog.detail(p.id).stock)
        assertEquals(before, f.catalog.movements())
        f.owner.checkpointCloseReopen()
        assertEquals(3L, f.purchases.lines(d.summary.id).items.single().quantity)
        assertTrue(f.owner.verifyIntegrity().let { it.quick && it.full && it.foreignKeys })
    } }
    @Test fun receivingTwiceAndConcurrentlyNeverDoubleApplies() = runBlocking { fixture { f ->
        val p = f.product("Once")
        val d = f.draft(p.id)
        val results = coroutineScope { (1..2).map { async { f.purchases.validate(ValidatePurchase(d.summary.id)) } }.awaitAll() }
        assertTrue(results.all { it.summary.status == PurchaseStatus.VALIDATED })
        assertEquals(8L, f.catalog.detail(p.id).stock)
        assertEquals(1, f.catalog.movements().count { it.reason == "purchase" })
        assertEquals(1, f.purchases.lines(d.summary.id).items.size)
        assertEquals(PurchaseStatus.VALIDATED, f.purchases.validate(ValidatePurchase(d.summary.id)).summary.status)
        assertEquals(1, f.catalog.movements().count { it.reason == "purchase" })
    } }
    @Test fun cancelDraftHasNoCompensationAndCancelValidatedOnlyOnce() = runBlocking { fixture { f ->
        val p = f.product("Cancellation")
        val draft = f.draft(p.id)
        assertEquals(PurchaseStatus.CANCELLED, f.purchases.cancel(CancelPurchase(draft.summary.id, "Not received")).summary.status)
        assertEquals(PurchaseStatus.CANCELLED, f.purchases.cancel(CancelPurchase(draft.summary.id, "Not received")).summary.status)
        assertEquals(5L, f.catalog.detail(p.id).stock)
        assertEquals(0, f.catalog.movements().count { it.reason == "purchase_cancellation" })
        val valid = f.draft(p.id)
        f.purchases.validate(ValidatePurchase(valid.summary.id))
        refused(WorkflowError.INVALID_INPUT) { f.purchases.cancel(CancelPurchase(valid.summary.id, "x")) }
        assertEquals(PurchaseStatus.CANCELLED, f.purchases.cancel(CancelPurchase(valid.summary.id, "Returned goods")).summary.status)
        assertEquals(5L, f.catalog.detail(p.id).stock)
        assertEquals(PurchaseStatus.CANCELLED, f.purchases.cancel(CancelPurchase(valid.summary.id, "Returned goods")).summary.status)
        assertEquals(1, f.catalog.movements().count { it.reason == "purchase_cancellation" })
    } }
    @Test fun insufficientStockOnLastLineRollsBackAllCompensation() = runBlocking { fixture { f ->
        val first = f.product("First")
        val last = f.product("Last")
        val d = f.draft(first.id)
        f.purchases.saveLine(SavePurchaseLine(d.summary.id, last.id, 3, BigDecimal("2.0")))
        f.purchases.validate(ValidatePurchase(d.summary.id))
        f.catalog.adjustStock(last.id, 2.0, "Synthetic consumption", 8)
        val before = f.catalog.movements()
        refused(WorkflowError.INSUFFICIENT_STOCK) { f.purchases.cancel(CancelPurchase(d.summary.id, "Return all")) }
        assertEquals(8L, f.catalog.detail(first.id).stock)
        assertEquals(2L, f.catalog.detail(last.id).stock)
        assertEquals(before, f.catalog.movements())
        assertEquals(PurchaseStatus.VALIDATED, f.purchases.detail(d.summary.id).summary.status)
    } }
    @Test fun supplierInactiveAndArchivedArticleBlockNewReceptionNotHistoricalCancellation() = runBlocking { fixture { f ->
        val p = f.product("Supplier target")
        val supplier = f.suppliers.save(SaveSupplier(name = "Supplier"))
        val d = f.purchases.createDraft(CreatePurchaseDraft(supplierId = supplier.id))
        f.purchases.saveLine(SavePurchaseLine(d.summary.id, p.id, 2, BigDecimal("1.0")))
        f.suppliers.save(SaveSupplier(supplier.id, supplier.name, active = false, expectedUpdatedAt = supplier.updatedAt))
        refused(WorkflowError.INADMISSIBLE_TARGET) { f.purchases.validate(ValidatePurchase(d.summary.id)) }
        assertEquals(5L, f.catalog.detail(p.id).stock)
        val active = f.suppliers.save(SaveSupplier(supplier.id, supplier.name, active = true, expectedUpdatedAt = f.suppliers.detail(supplier.id).updatedAt))
        f.purchases.validate(ValidatePurchase(d.summary.id))
        f.suppliers.save(SaveSupplier(active.id, active.name, active = false, expectedUpdatedAt = active.updatedAt))
        f.purchases.cancel(CancelPurchase(d.summary.id, "Historical cancellation"))
        assertEquals(5L, f.catalog.detail(p.id).stock)
        val archived = f.draft(p.id)
        val now = f.catalog.detail(p.id)
        f.catalog.archive(p.id, now.updatedAt)
        refused(WorkflowError.INADMISSIBLE_TARGET) { f.purchases.validate(ValidatePurchase(archived.summary.id)) }
    } }
    @Test fun currentRightsAndEmployeeDirectCallsCannotMutate() = runBlocking { fixture { f ->
        val p = f.product("RBAC")
        val d = f.draft(p.id)
        f.user(2, "manager"); f.user(3, "employee")
        f.identity.switchUser(Credentials("manager", "Password-123"))
        assertNotNull(f.purchases.detail(d.summary.id))
        f.commands.execute { security.replaceDenials(2, setOf("PURCHASES:VALIDATE", "PURCHASES:DELETE")) }
        forbidden { f.purchases.validate(ValidatePurchase(d.summary.id)) }
        forbidden { f.purchases.cancel(CancelPurchase(d.summary.id, "Denied action")) }
        f.identity.switchUser(Credentials("employee", "Password-123"))
        forbidden { f.purchases.list() }
        forbidden { f.purchases.createDraft(CreatePurchaseDraft()) }
        forbidden { f.purchases.saveLine(SavePurchaseLine(d.summary.id, p.id, 1, BigDecimal("1.0"))) }
        forbidden { f.purchases.validate(ValidatePurchase(d.summary.id)) }
        forbidden { f.purchases.cancel(CancelPurchase(d.summary.id, "Denied action")) }
        f.identity.switchUser(Credentials("owner", "Password-123"))
        assertEquals(PurchaseStatus.DRAFT, f.purchases.detail(d.summary.id).summary.status)
        assertEquals(5L, f.catalog.detail(p.id).stock)
    } }
    @Test fun injectedFaultAfterStockRollsBackEntireReception() = runBlocking {
        var inject = true
        fixture(PurchaseProbe { boundary ->
            if (inject && boundary == PurchaseBoundary.STOCK) error("Synthetic P3 fault")
        }) { f ->
            val p = f.product("Rollback")
            val d = f.draft(p.id)
            val before = f.catalog.movements()
            try { f.purchases.validate(ValidatePurchase(d.summary.id)); fail("Expected injected error") }
            catch (e: IllegalStateException) { assertEquals("Synthetic P3 fault", e.message) }
            assertEquals(5L, f.catalog.detail(p.id).stock)
            assertEquals(before, f.catalog.movements())
            assertEquals(PurchaseStatus.DRAFT, f.purchases.detail(d.summary.id).summary.status)
            inject = false
            f.purchases.validate(ValidatePurchase(d.summary.id))
            assertEquals(8L, f.catalog.detail(p.id).stock)
            assertEquals(1, f.catalog.movements().count { it.reason == "purchase" })
        }
    }
    @Test fun contextualArticleStartsWithZeroAndSaleSnapshotIsNotChangedByReceiving() = runBlocking { fixture { f ->
        val fresh = f.purchases.createArticle(PurchaseArticleCreation("Context article", "Test", BigDecimal("4.0"))).product
        assertEquals(0L, fresh.stock)
        val p = f.product("Invoiced article")
        val cash = f.sales.openCash(BigDecimal("0.0"))
        val sale = f.sales.sell(SaleCommand("p3-sale-key", cash.id, listOf(SaleLine(p.id, 1)), received = BigDecimal("3.0")))
        val baseline = f.sales.receipt(sale.id)
        val d = f.draft(p.id)
        f.purchases.validate(ValidatePurchase(d.summary.id))
        assertEquals(baseline, f.sales.receipt(sale.id))
        f.purchases.cancel(CancelPurchase(d.summary.id, "Compensation"))
        assertEquals(baseline, f.sales.receipt(sale.id))
        assertEquals(4L, f.catalog.detail(p.id).stock)
    } }
}
