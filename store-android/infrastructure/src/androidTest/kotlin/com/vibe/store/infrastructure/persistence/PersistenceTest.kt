package com.vibe.store.infrastructure.persistence

import android.content.Context
import androidx.room.*
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.vibe.store.application.persistence.*
import com.vibe.store.domain.SourceMoneyParity
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.TimeZone
import kotlin.system.measureTimeMillis

private val tables = setOf("users", "roles", "permissions", "role_permissions", "user_roles", "user_permission_denials",
    "employees", "cash_sessions", "products", "product_price_history", "stock_movements", "invoices", "invoice_lines",
    "payments", "suppliers", "purchases", "purchase_items", "inventory_counts", "inventory_count_lines", "attendances",
    "messages", "message_reads", "message_deletions", "notifications", "email_report_logs", "settings", "audit_logs",
    "generation_metadata", "pending_commands")

class PersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun directory() = File(context.noBackupFilesDir, "i02/" + UUID.randomUUID())
    private class Fixture(context: Context, val root: File) {
        lateinit var db: StoreDatabase
        val owner = RoomDatabaseOwner(context, root, factory = { c, f -> buildStore(c, f).also { db = it } })
        val commands = CommandCoordinator(owner)
        suspend fun seed() = commands.execute { (this as RoomRepositories).dao.fixture() }
        suspend fun counts(): Map<String, Long> = owner.read { db.useReaderConnection { c -> tables.associateWith { table ->
            c.usePrepared("SELECT count(*) FROM `$table`") { check(it.step()); it.getLong(0) }
        } } }
        suspend fun sql(sql: String) = commands.execute { db.useWriterConnection { it.usePrepared(sql) { s -> s.step(); Unit } } }
    }
    private suspend fun rejected(block: suspend () -> Unit): Throwable {
        try { block() } catch (failure: Exception) { return failure }
        throw AssertionError("Operation unexpectedly accepted")
    }

    @Test fun effectiveConnectionsAndFreshV1() = runBlocking {
        val f = Fixture(context, directory())
        try {
            assertTrue(f.counts().values.all { it == 0L })
            for (writer in listOf(true, false)) {
                val p = f.owner.effectivePragmas(writer)
                println("I02 effective writer=$writer $p")
                assertEquals("wal", p["journal_mode"])
                assertEquals("1", p["foreign_keys"])
                assertEquals(BUSY_MILLIS.toString(), p["busy_timeout"])
                if (writer) assertEquals("2", p["synchronous"])
            }
            assertEquals(IntegrityResult(true, true, true), f.owner.verifyIntegrity())
        } finally { f.owner.close() }
    }

    @Test fun schemaCoverageAndEveryForeignKey() = runBlocking {
        val f = Fixture(context, directory())
        try {
            f.seed()
            assertEquals(tables.associateWith { 1L }, f.counts())
            val actual = f.owner.read { f.db.useReaderConnection { c -> c.usePrepared("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name!='room_master_table'") {
                buildSet { while (it.step()) add(it.getText(0)) }
            } } }
            assertEquals(tables, actual)
            var relations = 0
            for (table in tables) {
                val columns = f.owner.read { f.db.useReaderConnection { c -> c.usePrepared("PRAGMA foreign_key_list(`$table`)") {
                    buildList { while (it.step()) add(it.getText(3)) }
                } } }
                for (column in columns) {
                    val failure = rejected { f.sql("UPDATE `$table` SET `$column`=999999") }
                    assertTrue("$table.$column: $failure", failure.toString().contains("FOREIGN KEY", true))
                    relations++
                }
            }
            assertEquals("Complete exported relation graph", 37, relations)
            println("I02 FK relations rejected=$relations tables=${actual.size}")
            assertEquals(tables.associateWith { 1L }, f.counts())
            assertEquals(IntegrityResult(true, true, true), f.owner.verifyIntegrity())
        } finally { f.owner.close() }
    }

    @Test fun uniquenessConstraintsAndHistoricalSnapshots() = runBlocking {
        val f = Fixture(context, directory())
        try {
            f.seed()
            rejected { f.commands.execute { (this as RoomRepositories).dao.cash(CashEntity(2, "OTHER", 1, "OPEN", 0.0, STAMP)) } }
            rejected { f.commands.execute { (this as RoomRepositories).dao.payment(PaymentEntity(2, "SYN-INV", 1, "CASH", 25.0, 30.0, 5.0, "CAPTURED", STAMP)) } }
            rejected { f.commands.execute { (this as RoomRepositories).dao.invoice(InvoiceEntity("OTHER", 1, 1, STAMP, 25.0, 25.0, 0.0, "validated", "SYN-KEY")) } }
            rejected { f.commands.execute { (this as RoomRepositories).dao.inventoryLine(InventoryLineEntity(2, 1, 1, 0, 0)) } }
            for (sql in listOf("UPDATE products SET stock=-1", "UPDATE products SET stock=1.5", "UPDATE invoice_lines SET quantity=0",
                "UPDATE purchases SET status='UNKNOWN'", "UPDATE cash_sessions SET openingAmount=-1", "UPDATE attendances SET status='UNKNOWN'",
                "UPDATE invoices SET canonicalVersion=NULL", "UPDATE payments SET method='OTHER'")) rejected { f.sql(sql) }
            f.sql("UPDATE products SET name='Changed', price=99")
            f.sql("UPDATE users SET firstName='Changed'")
            f.owner.read { f.db.useReaderConnection { c ->
                c.usePrepared("SELECT productName, unitPrice, unitCost FROM invoice_lines") { assertTrue(it.step()); assertEquals("Historical product", it.getText(0)); assertEquals(12.5, it.getDouble(1), 0.0); assertEquals(9.0, it.getDouble(2), 0.0) }
                c.usePrepared("SELECT responsibleName,cashAmount FROM audit_logs") { assertTrue(it.step()); assertEquals("Historical responsible", it.getText(0)); assertEquals(45.0, it.getDouble(1), 0.0) }
            } }
        } finally { f.owner.close() }
    }

    @Test fun rollbackAtEveryWriteBoundary() = runBlocking {
        for (boundary in 1..FIXTURE_WRITES) {
            val f = Fixture(context, directory())
            try {
                val error = rejected { f.commands.execute { (this as RoomRepositories).dao.fixture(boundary) } }
                assertTrue(error.toString(), error.toString().contains("injected boundary"))
                assertTrue("half-state at $boundary", f.counts().values.all { it == 0L })
                assertEquals(IntegrityResult(true, true, true), f.owner.verifyIntegrity())
            } finally { f.owner.close() }
        }
        println("I02 rollback boundaries=$FIXTURE_WRITES")
    }

    @Test fun serializationAndScopeLifetime(): Unit = runBlocking {
        val f = Fixture(context, directory())
        try {
            f.commands.execute { settings.put("counter", "0") }
            coroutineScope { (1..20).map { async(Dispatchers.Default) {
                f.commands.execute { val n = settings.value("counter")!!.toInt(); yield(); settings.put("counter", (n + 1).toString()) }
            } }.awaitAll() }
            assertEquals("20", f.owner.read { settings.value("counter") })
            val escaped = f.owner.read { settings }
            rejected { escaped.value("counter") }
            rejected { f.commands.execute { coroutineScope { async { settings.put("detached", "bad") }.await() } } }
            assertNull(f.owner.read { settings.value("detached") })
            rejected { f.owner.read { f.owner.read { settings.value("counter") } } }
            rejected { f.commands.execute { f.commands.execute { settings.put("nested", "bad") } } }
            assertNull(f.owner.read { settings.value("nested") })
        } finally { f.owner.close() }
    }

    @Test fun maintenanceDrainsAndSingleOwnership() = runBlocking {
        val root = directory(); val f = Fixture(context, root)
        try {
            rejected { RoomDatabaseOwner(context, root) }
            val accepted = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
            val write = async { f.commands.execute { settings.put("before", "yes"); accepted.complete(Unit); finish.await() } }
            accepted.await()
            val maintenance = async { f.owner.checkpointCloseReopen() }
            delay(50); assertFalse(maintenance.isCompleted)
            val read = async { f.owner.read { settings.value("before") } }
            delay(50); assertFalse(read.isCompleted)
            finish.complete(Unit); write.await(); maintenance.await()
            assertEquals("yes", read.await())
            assertEquals(IntegrityResult(true, true, true), f.owner.verifyIntegrity())
        } finally { f.owner.close() }
        val next = Fixture(context, root)
        try { assertEquals("yes", next.owner.read { settings.value("before") }) } finally { next.owner.close() }
    }

    @Test fun boundedContentionNoRetry() = runBlocking {
        val f = Fixture(context, directory())
        try {
            f.commands.execute { settings.put("before", "yes") }
            // Explicit adversarial connection, TEST ONLY, never a repository.
            BundledSQLiteDriver().open(f.owner.file.path).use { adversary ->
                adversary.execSQL("BEGIN IMMEDIATE")
                try {
                    val elapsed = measureTimeMillis { rejected { f.commands.execute { settings.put("blocked", "bad") } } }
                    assertTrue("busy handling exceeded bound: $elapsed", elapsed < 5000)
                    println("I02 contention elapsedMs=$elapsed")
                } finally { adversary.execSQL("ROLLBACK") }
            }
            assertNull(f.owner.read { settings.value("blocked") })
        } finally { f.owner.close() }
    }

    @Test fun migrationSnapshotRestartAndFutureRefusal() = runBlocking {
        val root = directory(); val original = Fixture(context, root)
        original.seed(); original.owner.close()
        val before = sha256(File(root, "store.db"))
        var migrated = RoomDatabaseOwner(context, root, 2, ::syntheticV2)
        try { assertEquals(IntegrityResult(true, true, true), migrated.verifyIntegrity()) } finally { migrated.close() }
        assertEquals(before, sha256(File(context.noBackupFilesDir, "maintenance/${root.name}/pre-migration.db")))
        migrated = RoomDatabaseOwner(context, root, 2, ::syntheticV2)
        try { assertEquals("v1", migrated.read { settings.value("synthetic") }) } finally { migrated.close() }
        val futureHash = sha256(File(root, "store.db"))
        var opened = false
        val older = RoomDatabaseOwner(context, root, factory = { c, file -> opened = true; buildStore(c, file) })
        try { rejected { older.read { settings.value("synthetic") } }; assertFalse(opened) } finally { older.close() }
        assertEquals(futureHash, sha256(File(root, "store.db")))
    }

    @Test fun corruptRecoverySnapshotRefusesBeforeRoomOpen() = runBlocking {
        val root = directory(); val f = Fixture(context, root); f.seed(); f.owner.close()
        val upgraded = RoomDatabaseOwner(context, root, 2, ::syntheticV2)
        try { upgraded.verifyIntegrity() } finally { upgraded.close() }
        File(context.noBackupFilesDir, "maintenance/${root.name}/pre-migration.db").appendText("corruption")
        var opened = false
        val recover = RoomDatabaseOwner(context, root, 2) { c, file -> opened = true; syntheticV2(c, file) }
        try { rejected { recover.verifyIntegrity() }; assertFalse(opened) } finally { recover.close() }
    }

    @Test fun sourceMoneyAndCalendarParity() {
        assertEquals(1.0, SourceMoneyParity.roundTwo(1.005), 0.0)
        assertEquals(2.68, SourceMoneyParity.roundTwo(2.675), 0.0)
        assertEquals(-1.12, SourceMoneyParity.roundTwo(-1.125), 0.0)
        val calendar = SourceCalendar { TimeZone.getTimeZone("GMT-02:00") }
        assertEquals("1970-01-01", calendar.day(0, true))
        assertEquals("1969-12-31", calendar.day(0, false))
    }

    @Test fun processRestartPersistence() = runBlocking {
        val root = File(context.noBackupFilesDir, "i02/restart")
        val f = Fixture(context, root)
        try {
            when (InstrumentationRegistry.getArguments().getString("restartPhase")) {
                "prepare" -> { f.commands.execute { settings.put("restart", "durable-v1") }; println("I02 RESTART PREPARED PID=${android.os.Process.myPid()}") }
                "verify" -> { assertEquals("durable-v1", f.owner.read { settings.value("restart") }); println("I02 RESTART VERIFIED PID=${android.os.Process.myPid()}") }
                else -> { f.commands.execute { settings.put("restart", "durable-v1") }; assertEquals("durable-v1", f.owner.read { settings.value("restart") }) }
            }
        } finally { f.owner.close() }
    }
}
