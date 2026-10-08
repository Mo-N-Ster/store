package com.vibe.store.infrastructure.persistence

import android.content.Context
import androidx.room.*
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import com.vibe.store.api.*
import com.vibe.store.application.catalog.CatalogAuthority
import com.vibe.store.application.persistence.CommandCoordinator
import com.vibe.store.application.persistence.IntegrityResult
import com.vibe.store.application.purchases.PurchaseAuthority
import com.vibe.store.application.sales.SaleAuthority
import com.vibe.store.application.security.IdentityAuthority
import com.vibe.store.infrastructure.media.AndroidProductMedia
import com.vibe.store.infrastructure.security.BcryptPasswords
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.util.UUID

/** All files are UUID-scoped synthetic test databases. No production/user DB is opened. */
class ExactMoneyPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun directory() = File(context.noBackupFilesDir, "money-01c/${UUID.randomUUID()}")
    private fun exact(expected: String, actual: BigDecimal?) = assertEquals(0, BigDecimal(expected).compareTo(checkNotNull(actual)))
    private suspend fun rejects(block: suspend () -> Unit) {
        try { block() } catch (_: Exception) { return }
        fail("Expected safe rejection")
    }

    @Test fun exactSalesPurchasesCashAndRestart() = runBlocking {
        val root = directory()
        val owner = RoomDatabaseOwner(context, File(root, "database"))
        val commands = CommandCoordinator(owner)
        val identity = IdentityAuthority(owner, commands, BcryptPasswords())
        val catalog = CatalogAuthority(identity, AndroidProductMedia.fixture(File(root, "media")) { byteArrayOf().inputStream() })
        val sales = SaleAuthority(identity)
        val purchases = PurchaseAuthority(identity, catalog)
        var productId = 0L
        var purchaseId = 0L
        var receiptId = ""
        var cashId = 0L
        try {
            identity.bootstrap(OwnerRegistration("owner", "owner@example.invalid", "Synthetic", "Owner", "Password-123", "Question", "Answer"))
            identity.login(Credentials("owner", "Password-123"))
            productId = catalog.save(ProductDraft(name = "Exact", category = "Test", price = BigDecimal("12.3456789"), initialStock = 20.0)).product.id
            val cash = sales.openCash(BigDecimal("10")); cashId = cash.id
            val command = SaleCommand("exact-sale", cash.id, listOf(SaleLine(productId, 3)), BigDecimal("0.0000001"))
            val receipt = sales.sell(command); receiptId = receipt.id
            exact("37.0370367", receipt.subtotal)
            exact("37.0370366", receipt.total)
            assertEquals(receipt, sales.sell(command))
            val draft = purchases.createDraft(CreatePurchaseDraft(idempotencyKey = "exact-purchase")); purchaseId = draft.summary.id
            purchases.saveLine(SavePurchaseLine(purchaseId, productId, 7, BigDecimal("0.123456789")))
            exact("0.864197523", purchases.validate(ValidatePurchase(purchaseId)).summary.total)
            val cancelled = sales.cancel(receipt.id, "Synthetic refund")
            exact("10", sales.currentCash()!!.expected)
            assertEquals(cancelled, sales.cancel(receipt.id, "Repeated refund"))
            exact("10", sales.currentCash()!!.expected)
            val closed = sales.closeCash(cash.id, BigDecimal("10.005"))
            exact("0.005", closed.difference)
            owner.checkpointCloseReopen()
            assertEquals("REFUNDED", sales.receipt(receipt.id).paymentStatus)
            exact("12.3456789", catalog.detail(productId).price)
            exact("0.864197523", purchases.detail(purchaseId).summary.total)
            assertEquals(IntegrityResult(true, true, true), owner.verifyIntegrity())
        } finally { owner.close() }
        val restarted = RoomDatabaseOwner(context, File(root, "database"))
        try {
            restarted.read {
                exact("12.3456789", (this as RoomRepositories).catalog.find(productId)!!.price)
                exact("0.864197523", purchaseRecords.find(purchaseId)!!.detail.summary.total)
                exact("37.0370366", saleOperations.find(receiptId)!!.receipt.total)
                exact("0.005", cashOperations.page(1, 0).single { it.id == cashId }.difference)
            }
        } finally { restarted.close() }
    }

    @Test fun allMonetaryColumnsAreTextAndMalformedWritesAreRejected() = runBlocking {
        val root = directory()
        lateinit var db: StoreDatabase
        val owner = RoomDatabaseOwner(context, root, factory = { c, file -> buildStore(c, file).also { db = it } })
        val commands = CommandCoordinator(owner)
        try {
            commands.execute { (this as RoomRepositories).dao.fixture() }
            owner.read { db.useReaderConnection { connection ->
                for ((table, columns) in MoneyColumns.tables) for (column in columns) {
                    connection.usePrepared("SELECT typeof(`$column`),`$column` FROM `$table`") { row ->
                        while (row.step()) if (!row.isNull(1)) {
                            assertEquals("$table.$column", "text", row.getText(0))
                            assertEquals(row.getText(1), MoneyText.canonical(MoneyText.parse(row.getText(1))))
                        }
                    }
                }
            } }
            for (value in listOf("1e2", "NaN", "Infinity", "1.00", "01", "-0", "-1", "0.", ".1", "1,2", "1.2.3", "", "+1", " 1", "1-2")) {
                rejects { commands.execute { db.useWriterConnection { connection ->
                    connection.usePrepared("UPDATE products SET price=? WHERE id=1") { it.bindText(1, value); it.step() }
                } } }
            }
            commands.execute { db.useWriterConnection { connection ->
                connection.usePrepared("UPDATE products SET price=? WHERE id=1") { it.bindText(1, "12.3456789"); it.step() }
                connection.usePrepared("UPDATE audit_logs SET cashAmount=? WHERE id=1") { it.bindText(1, "-0.005"); it.step() }
            } }
            exact("12.3456789", owner.read { catalog.find(1)!!.price })
            exact("-0.005", owner.read { (this as RoomRepositories).dao.findAudit(1)!!.cashAmount })
        } finally { owner.close() }
    }

    @Test fun capturedAggregationIsExactBoundedAndRefundsAreNotDoubleCounted() = runBlocking {
        val owner = RoomDatabaseOwner(context, directory())
        val commands = CommandCoordinator(owner)
        try {
            commands.execute {
                val dao = (this as RoomRepositories).dao
                dao.user(UserEntity(1, "synthetic", "NOT-A-REAL-HASH", "owner", "Test", "Only", "TO", STAMP))
                dao.cash(CashEntity(1, "EXACT-CASH", 1, "OPEN", BigDecimal.ZERO, STAMP))
                for (id in 1L..513L) {
                    val amount = when (id) { 1L -> BigDecimal("0.1"); 513L -> BigDecimal("0.2"); else -> BigDecimal.ZERO }
                    dao.invoice(InvoiceEntity("invoice-$id", 1, 1, STAMP, amount, amount, BigDecimal.ZERO, "validated"))
                    dao.payment(PaymentEntity(id, "invoice-$id", 1, "CASH", amount, amount, BigDecimal.ZERO, "CAPTURED", STAMP))
                }
            }
            exact("0.3", owner.read { cashOperations.current(1)!!.expected })
            commands.execute { (this as RoomRepositories).dao.refund("invoice-1") }
            exact("0.2", owner.read { cashOperations.current(1)!!.expected })
            commands.execute { (this as RoomRepositories).dao.refund("invoice-1") }
            exact("0.2", owner.read { cashOperations.current(1)!!.expected })
            owner.checkpointCloseReopen()
            exact("0.2", owner.read { cashOperations.current(1)!!.expected })
        } finally { owner.close() }
    }

    @Test fun productionMigrationPreservesRecordsRelationsIndexesAndPendingIntent() = runBlocking {
        val root = directory(); legacyV1(root)
        val source = File(root, "store.db")
        val before = sha256(source)
        val indexes = BundledSQLiteDriver().open(source.path, 1).use { connection ->
            connection.prepare("SELECT sql FROM sqlite_master WHERE type='index' AND sql IS NOT NULL ORDER BY name").use { row ->
                buildList { while (row.step()) add(row.getText(0)) }
            }
        }
        val owner = RoomDatabaseOwner(context, root)
        try {
            owner.read {
                val dao = (this as RoomRepositories).dao
                exact("12.3456789", dao.findProduct(1)!!.price)
                exact("-0.1", dao.session(1)!!.difference)
                exact("0.999", dao.findPurchase(1)!!.totalAmount)
                assertNull(dao.invoiceLines("SYN-INV").single().unitCost)
                assertEquals("Historical product", dao.invoiceLines("SYN-INV").single().productName)
                assertEquals("Historical details", dao.findAudit(1)!!.details)
                assertEquals("synthetic-command", dao.invoiceByKey("SYN-KEY")!!.canonicalRequest)
                assertEquals("synthetic-command", dao.pendingByKey("SYN-KEY")!!.canonicalRequest)
                assertEquals("SUBMITTED", dao.pendingByKey("SYN-KEY")!!.state)
                exact("-0.1", dao.findAudit(1)!!.cashAmount)
            }
            assertEquals(IntegrityResult(true, true, true), owner.verifyIntegrity())
            owner.checkpointCloseReopen()
            assertEquals("synthetic-command", owner.read { saleOperations.submitted("SYN-KEY")!!.canonical })
        } finally { owner.close() }
        val snapshot = File(context.noBackupFilesDir, "maintenance/${root.name}/pre-migration.db")
        assertEquals(before, sha256(snapshot))
        BundledSQLiteDriver().open(source.path, 1).use { connection ->
            assertEquals("2", connection.scalar("PRAGMA user_version"))
            val after = connection.prepare("SELECT sql FROM sqlite_master WHERE type='index' AND sql IS NOT NULL ORDER BY name").use { row ->
                buildList { while (row.step()) add(row.getText(0)) }
            }
            assertEquals(indexes, after)
            for ((table, columns) in MoneyColumns.tables) for (column in columns) {
                connection.prepare("SELECT typeof(`$column`) FROM `$table` WHERE `$column` IS NOT NULL").use { row ->
                    while (row.step()) assertEquals("text", row.getText(0))
                }
            }
        }
    }

    @Test fun migratedPendingCommandsResolveAndReplayWithoutDuplicateEffects() = runBlocking {
        val root = directory(); legacyV1(root)
        val passwords = BcryptPasswords()
        val verifier = passwords.hash("Password-123")
        val canonical = "2|0.1|E:30|1:2"
        // Synthetic v1 command journal simulates both after-commit and before-commit interruption.
        BundledSQLiteDriver().open(File(root, "store.db").path).use { connection ->
            connection.prepare("UPDATE users SET passwordHash=? WHERE id=1").use { it.bindText(1, verifier); it.step() }
            connection.execSQL("UPDATE cash_sessions SET status='OPEN',closingAmount=NULL,expectedAmount=NULL,difference=NULL WHERE id=1")
            connection.execSQL("INSERT INTO roles VALUES (1,'owner','Owner',1,'2026-01-02')")
            connection.execSQL("INSERT INTO permissions VALUES (1,'POS','VALIDATE','2026-01-02')")
            connection.execSQL("INSERT INTO permissions VALUES (2,'CASH','READ','2026-01-02')")
            connection.execSQL("INSERT INTO role_permissions VALUES (1,1)")
            connection.execSQL("INSERT INTO role_permissions VALUES (1,2)")
            connection.execSQL("INSERT INTO user_roles VALUES (1,1,'2026-01-02')")
            connection.prepare("UPDATE invoices SET canonicalVersion=2,canonicalRequest=? WHERE id='SYN-INV'").use { it.bindText(1, canonical); it.step() }
            connection.prepare("UPDATE pending_commands SET canonicalVersion=2,canonicalRequest=? WHERE commandKey='SYN-KEY'").use { it.bindText(1, canonical); it.step() }
            connection.execSQL("INSERT INTO pending_commands VALUES ('SYN-UNFINISHED','SYN-UNFINISHED',1,1,2,'2|0|O|1:1','SUBMITTED','2026-01-02')")
        }
        val owner = RoomDatabaseOwner(context, root)
        val identity = IdentityAuthority(owner, CommandCoordinator(owner), passwords)
        val sales = SaleAuthority(identity)
        try {
            identity.login(Credentials("synthetic", "Password-123"))
            val old = sales.resolve("SYN-KEY")
            assertEquals("SYN-INV", old.id)
            assertEquals(old, sales.sell(SaleCommand("SYN-KEY", 1, listOf(SaleLine(1, 2)), BigDecimal("0.1"), BigDecimal("30"))))
            exact("45", sales.currentCash()!!.expected)
            assertEquals(8L, owner.read { (this as RoomRepositories).catalog.find(1)!!.stock })
            val completed = sales.resolve("SYN-UNFINISHED")
            exact("12.3456789", completed.total)
            owner.checkpointCloseReopen()
            assertEquals(completed, sales.resolve("SYN-UNFINISHED"))
            assertEquals(7L, owner.read { (this as RoomRepositories).catalog.find(1)!!.stock })
            owner.read {
                val dao = (this as RoomRepositories).dao
                assertEquals(2, dao.salePage("", "", "", null, "", 40, 0).size)
                assertEquals(2, dao.capturedPaymentPage(1, null).size)
                assertEquals(canonical, dao.pendingByKey("SYN-KEY")!!.canonicalRequest)
            }
            rejects { sales.sell(SaleCommand("SYN-KEY", 1, listOf(SaleLine(1, 2)), BigDecimal("0.2"), BigDecimal("30"))) }
            assertEquals(IntegrityResult(true, true, true), owner.verifyIntegrity())
        } finally { owner.close() }
    }

    @Test fun nonfiniteLegacyMoneyFailsWithoutReplacingSource() = runBlocking {
        val root = directory(); legacyV1(root)
        val source = File(root, "store.db")
        BundledSQLiteDriver().open(source.path).use { it.execSQL("UPDATE audit_logs SET cashAmount=1e999 WHERE id=1") }
        val before = sha256(source)
        val owner = RoomDatabaseOwner(context, root)
        try { rejects { owner.verifyIntegrity() } } finally { owner.close() }
        assertEquals(before, sha256(File(context.noBackupFilesDir, "maintenance/${root.name}/pre-migration.db")))
        BundledSQLiteDriver().open(source.path, 1).use { connection ->
            assertEquals("1", connection.scalar("PRAGMA user_version"))
            assertEquals("Historical details", connection.scalar("SELECT details FROM audit_logs WHERE id=1"))
            assertEquals("SYN-KEY", connection.scalar("SELECT commandKey FROM pending_commands"))
            assertEquals(IntegrityResult(true, true, true), connection.healthy())
        }
    }

    @Test fun canonicalConverterRejectsMalformedTextAndPreservesNullableSigns() {
        val converter = MoneyText()
        assertNull(converter.encode(null)); assertNull(converter.decode(null))
        assertEquals("0", converter.encode(BigDecimal("0.000")))
        assertEquals("0.00000000000000000001", converter.encode(BigDecimal("1E-20")))
        exact("-0.005", converter.decode("-0.005"))
        for (text in listOf("NaN", "Infinity", "1e2", "1.00", "01", "-0", "+1", " 1", "1,2")) {
            try { converter.decode(text); fail("Accepted malformed TEXT: $text") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun failedMigrationRetainsV1AndVerifiedRecoverySnapshot() = runBlocking {
        val root = directory(); legacyV1(root, corrupt = true)
        val source = File(root, "store.db"); val before = sha256(source)
        val owner = RoomDatabaseOwner(context, root)
        try { rejects { owner.verifyIntegrity() } } finally { owner.close() }
        val snapshot = File(context.noBackupFilesDir, "maintenance/${root.name}/pre-migration.db")
        assertEquals(before, sha256(snapshot))
        BundledSQLiteDriver().open(source.path, 1).use { connection ->
            assertEquals("1", connection.scalar("PRAGMA user_version"))
            assertEquals("corrupt", connection.scalar("SELECT price FROM products WHERE id=1"))
            assertEquals("synthetic-command", connection.scalar("SELECT canonicalRequest FROM pending_commands"))
            assertEquals(IntegrityResult(true, true, true), connection.healthy())
        }
        val retry = RoomDatabaseOwner(context, root)
        try { rejects { retry.verifyIntegrity() } } finally { retry.close() }
        assertEquals(before, sha256(snapshot))
        assertTrue(File(context.noBackupFilesDir, "maintenance/${root.name}/migration-journal").isFile)
    }
}
