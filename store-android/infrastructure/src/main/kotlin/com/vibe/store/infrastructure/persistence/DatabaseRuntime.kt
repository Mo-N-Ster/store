package com.vibe.store.infrastructure.persistence

import android.content.Context
import android.util.AtomicFile
import android.system.Os
import android.system.OsConstants
import androidx.room.*
import androidx.sqlite.*
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.vibe.store.application.persistence.*
import com.vibe.store.domain.GenerationId
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.security.MessageDigest
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

internal const val BUSY_MILLIS = 750
internal fun SQLiteConnection.scalar(sql: String): String = prepare(sql).use { check(it.step()); it.getText(0) }
internal fun SQLiteConnection.healthy(full: Boolean = true): IntegrityResult {
    val quick = scalar("PRAGMA quick_check") == "ok"
    val integrity = !full || scalar("PRAGMA integrity_check") == "ok"
    val fk = prepare("PRAGMA foreign_key_check").use { !it.step() }
    return IntegrityResult(quick, integrity, fk)
}
internal fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { stream -> val bytes = ByteArray(65536); while (true) {
        val n = stream.read(bytes); if (n < 0) break; digest.update(bytes, 0, n)
    } }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
private fun syncDirectory(directory: File) {
    check(directory.isDirectory)
    val descriptor = Os.open(directory.path, OsConstants.O_RDONLY, 0)
    try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
}
internal class ConfiguredDriver : SQLiteDriver {
    private val bundled = BundledSQLiteDriver()
    override fun open(fileName: String): SQLiteConnection {
        val connection = bundled.open(fileName)
        try {
            connection.execSQL("PRAGMA foreign_keys=ON")
            connection.execSQL("PRAGMA synchronous=FULL")
            connection.execSQL("PRAGMA busy_timeout=$BUSY_MILLIS")
            return connection
        } catch (failure: Throwable) { connection.close(); throw failure }
    }
}
internal val configuration = object : RoomDatabase.Callback() {
    override fun onCreate(connection: SQLiteConnection) { StoreConstraints.create(connection) }
    override fun onOpen(connection: SQLiteConnection) {
        // Room configures NORMAL for WAL before this callback. Restore the
        // required writer durability, then test on the effective leased writer.
        connection.execSQL("PRAGMA synchronous=FULL")
        connection.execSQL("PRAGMA foreign_keys=ON")
        connection.execSQL("PRAGMA busy_timeout=$BUSY_MILLIS")
    }
}
internal fun buildStore(context: Context, file: File): StoreDatabase =
    Room.databaseBuilder(context, StoreDatabase::class.java, file.absolutePath)
        .setDriver(ConfiguredDriver()).setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .setQueryCoroutineContext(Dispatchers.IO).addCallback(configuration).build()

/** Composition-only entry point; no path/table/SQL accepted from UI. No DB opened by the smoke UI. */
object AndroidPersistence {
    fun create(context: Context): PersistenceHandle {
        val root = File(context.noBackupFilesDir, "generations/main")
        val owner = RoomDatabaseOwner(context.applicationContext, root)
        return PersistenceHandle(owner, CommandCoordinator(owner))
    }
}
class PersistenceHandle internal constructor(val owner: DatabaseOwner, val commands: CommandCoordinator)

private class Lease : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<Lease>
}

/** One OS lock and one Room instance. Even readers are serialized in this initial
 * foundation: maintenance cannot overtake accepted work or copy an active DB. */
internal class RoomDatabaseOwner(
    private val context: Context,
    internal val root: File,
    private val targetVersion: Int = 1,
    private val factory: (Context, File) -> StoreDatabase = ::buildStore
) : DatabaseOwner, UnitOfWork {
    override val generation = GenerationId("main")
    private val gate = Mutex()
    internal val file = File(root, "store.db")
    private val lockFile: RandomAccessFile
    private val processLock: FileLock
    private var database: StoreDatabase? = null
    private var closed = false
    private val maintenanceDirectory = File(context.noBackupFilesDir, "maintenance/${root.name}")
    private val snapshot = File(maintenanceDirectory, "pre-migration.db")
    private val journal = AtomicFile(File(maintenanceDirectory, "migration-journal"))
    init {
        check(root.isDirectory || root.mkdirs())
        check(maintenanceDirectory.isDirectory || maintenanceDirectory.mkdirs())
        lockFile = RandomAccessFile(File(root, "owner.lock"), "rw")
        try { processLock = lockFile.channel.tryLock() ?: error("Database already owned") }
        catch (failure: Throwable) { lockFile.close(); throw failure }
    }
    private fun readOnlyVersion(): Int = if (!file.exists()) 0 else
        BundledSQLiteDriver().open(file.absolutePath, 1).use { it.scalar("PRAGMA user_version").toInt() }
    private fun recoverBeforeOpen() {
        if (!journal.baseFile.exists() && !File(journal.baseFile.path + ".bak").exists()) return
        val record = journal.openRead().use { it.readBytes().toString(Charsets.UTF_8) }.split('\n')
        check(record.size == 4 && record[0] == "PREPARED") { "Unrecognized recovery journal; refusing open" }
        check(record[2].toInt() == targetVersion) { "Different migration plan; explicit recovery required" }
        check(snapshot.exists() && sha256(snapshot) == record[3]) { "Recovery snapshot missing or corrupt" }
        val version = readOnlyVersion()
        check(version == record[1].toInt() || version == record[2].toInt()) { "Ambiguous migration; recovery required" }
    }
    private fun verifiedSnapshot(oldVersion: Int) {
        check(database == null) { "Cannot snapshot open Room" }
        // Exclusively owned maintenance connection, closed before any copy.
        BundledSQLiteDriver().open(file.absolutePath).use {
            it.execSQL("PRAGMA busy_timeout=$BUSY_MILLIS")
            it.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use { result ->
                check(result.step() && result.getLong(0) == 0L && result.getLong(1) == result.getLong(2)) { "Checkpoint busy" }
            }
            check(it.healthy() == IntegrityResult(true, true, true))
        }
        // Preserve the first verified pre-operation snapshot across recovery.
        if (journal.baseFile.exists()) return
        FileOutputStream(snapshot).use { output -> file.inputStream().use { it.copyTo(output) }; output.fd.sync() }
        syncDirectory(maintenanceDirectory)
        check(sha256(file) == sha256(snapshot))
        BundledSQLiteDriver().open(snapshot.absolutePath, 1).use { check(it.healthy() == IntegrityResult(true, true, true)) }
        val stream = journal.startWrite()
        try {
            stream.write("PREPARED\n$oldVersion\n$targetVersion\n${sha256(snapshot)}".toByteArray())
            journal.finishWrite(stream)
            syncDirectory(maintenanceDirectory)
        } catch (failure: Throwable) { journal.failWrite(stream); throw failure }
    }
    private suspend fun open(): StoreDatabase {
        check(!closed) { "Owner closed" }
        database?.let { return it }
        val before = readOnlyVersion()
        require(before <= targetVersion) { "Future database schema refused" }
        require(!file.exists() || before >= 1) { "Unrecognized existing schema; refusing implicit initialization" }
        recoverBeforeOpen()
        if (file.exists() && before < targetVersion) verifiedSnapshot(before)
        val candidate = factory(context, file)
        try {
            candidate.useWriterConnection { connection ->
                connection.usePrepared("PRAGMA user_version") { check(it.step() && it.getLong(0) == targetVersion.toLong()) }
                connection.usePrepared("PRAGMA quick_check") { check(it.step() && it.getText(0) == "ok") }
            }
            database = candidate
            return candidate
        } catch (failure: Throwable) { candidate.close(); throw failure }
    }
    private suspend fun <T> accepted(block: suspend (StoreDatabase) -> T): T {
        check(currentCoroutineContext()[Lease] == null) { "Nested owner operation" }
        return gate.withLock { withContext(Dispatchers.IO + Lease()) { block(open()) } }
    }
    private suspend fun <T> reader(db: StoreDatabase, block: suspend (Transactor) -> T): T =
        db.useReaderConnection { connection ->
            // Room initializes reader connections after driver.open and does not
            // invoke the writer onOpen callback for them. Set the bound on each
            // leased reader; all repository reads reuse this same confined lease.
            connection.usePrepared("PRAGMA busy_timeout=$BUSY_MILLIS") { it.step() }
            block(connection)
        }
    override suspend fun <T> read(block: suspend ReadRepositories.() -> T): T = accepted { db -> reader(db) {
        val repositories = RoomRepositories(db.records(), currentCoroutineContext()[Job], false)
        try { repositories.block() } finally { repositories.release() }
    } }
    override suspend fun <T> transaction(block: suspend TransactionRepositories.() -> T): T = accepted { db ->
        db.useWriterConnection { writer -> writer.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) {
            val repositories = RoomRepositories(db.records(), currentCoroutineContext()[Job], true)
            try { repositories.block() } finally { repositories.release() }
        } }
    }
    override suspend fun verifyIntegrity(): IntegrityResult = accepted { db -> reader(db) { reader ->
        val quick = reader.usePrepared("PRAGMA quick_check") { it.step() && it.getText(0) == "ok" }
        val full = reader.usePrepared("PRAGMA integrity_check") { it.step() && it.getText(0) == "ok" }
        val fk = reader.usePrepared("PRAGMA foreign_key_check") { !it.step() }
        IntegrityResult(quick, full, fk)
    } }
    private suspend fun checkpoint(db: StoreDatabase) {
        db.useWriterConnection { writer -> writer.usePrepared("PRAGMA wal_checkpoint(TRUNCATE)") {
            check(it.step() && it.getLong(0) == 0L && it.getLong(1) == it.getLong(2)) { "Checkpoint busy; maintenance aborted" }
        } }
    }
    override suspend fun checkpointCloseReopen() {
        check(currentCoroutineContext()[Lease] == null)
        gate.withLock { withContext(Dispatchers.IO) {
            val db = open(); checkpoint(db); db.close(); database = null; open()
        } }
    }
    override suspend fun close() {
        check(currentCoroutineContext()[Lease] == null)
        gate.withLock { withContext(Dispatchers.IO + NonCancellable) {
            if (!closed) {
                try { database?.let { checkpoint(it) } }
                finally { database?.close(); database = null; closed = true; processLock.release(); lockFile.close() }
            }
        } }
    }
    // Infrastructure-only diagnostics; fixed pragmas, never a public query API.
    internal suspend fun effectivePragmas(writer: Boolean): Map<String, String> = accepted { db ->
        suspend fun inspect(connection: Transactor): Map<String, String> =
            listOf("journal_mode", "synchronous", "foreign_keys", "busy_timeout").associateWith { pragma ->
                connection.usePrepared("PRAGMA $pragma") { check(it.step()); it.getText(0) }
            }
        if (writer) db.useWriterConnection { inspect(it) } else reader(db) { inspect(it) }
    }
}
