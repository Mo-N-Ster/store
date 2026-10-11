package com.vibe.store.infrastructure.persistence

import android.content.Context
import android.util.AtomicFile
import android.system.ErrnoException
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
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
internal fun fileSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { stream -> val bytes = ByteArray(65536); while (true) {
        val n = stream.read(bytes); if (n < 0) break; digest.update(bytes, 0, n)
    } }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
internal fun sha256(file: File): String = fileSha256(file)
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
        .setQueryCoroutineContext(Dispatchers.IO).addCallback(configuration)
        .addMigrations(MoneyMigration1To2, DeliveryMigration2To3).build()

/** Composition-only entry point; no path/table/SQL accepted from UI. No DB opened by the smoke UI. */
object AndroidPersistence {
    fun create(context: Context): PersistenceHandle {
        val owner = ActiveStoreOwner(context.applicationContext)
        return PersistenceHandle(owner, CommandCoordinator(owner), owner, AndroidRecoveryBackend(owner))
    }
    fun activeGenerationRoot(context: Context): File = File(context.noBackupFilesDir,
        "generations/${GenerationPointer(context.noBackupFilesDir).read()}")
}
class PersistenceHandle internal constructor(val owner: DatabaseOwner, val commands: CommandCoordinator,
    internal val maintenance: ActiveStoreOwner, val recovery: com.vibe.store.application.recovery.RecoveryBackend)

private class GenerationPointer(private val base: File, private val boundaryProbe: (String) -> Unit = {}) {
    private val directory = File(base, "maintenance")
    private val file = AtomicFile(File(directory, "active-generation"))
    init { check(directory.isDirectory || directory.mkdirs()) { "Cannot create generation pointer directory" } }
    fun read(): String {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return "main"
        val value = file.openRead().use { it.readBytes().toString(Charsets.US_ASCII) }
        require(value.matches(Regex("[A-Za-z0-9-]{1,48}"))) { "Invalid active generation pointer; recovery required" }
        return value
    }
    fun write(value: String) {
        require(value.matches(Regex("[A-Za-z0-9-]{1,48}")))
        val output = file.startWrite()
        try {
            output.write(value.toByteArray(Charsets.US_ASCII)); output.fd.sync()
            boundaryProbe("generation_pointer_temp_synced")
            file.finishWrite(output); syncDirectory(file.baseFile.parentFile!!)
        }
        catch (failure: Throwable) { file.failWrite(output); throw failure }
    }
}

private data class RecoveryTransition(val operation: String, val state: String, val old: String, val next: String)
private class RecoveryJournal(base: File) {
    private val directory = File(base, "maintenance")
    private val file = AtomicFile(File(directory, "recovery-transition"))
    init { check(directory.isDirectory || directory.mkdirs()) { "Cannot create recovery journal directory" } }
    fun read(): RecoveryTransition? {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        val fields = file.openRead().use { it.readBytes().toString(Charsets.US_ASCII).split('\n') }
        require(fields.size == 5 && fields[0] == "STORE_RECOVERY_V1" && fields[1] in setOf("RESTORE", "RESET") &&
            fields[2] in setOf("PREPARED", "COMPLETE", "ABORTED") && fields[3].matches(Regex("[A-Za-z0-9-]{1,48}")) &&
            fields[4].matches(Regex("[A-Za-z0-9-]{1,48}"))) { "Invalid recovery transition journal" }
        return RecoveryTransition(fields[1], fields[2], fields[3], fields[4])
    }
    fun write(operation: String, state: String, old: String, next: String) {
        require(operation in setOf("RESTORE", "RESET") && state in setOf("PREPARED", "COMPLETE", "ABORTED"))
        require(old.matches(Regex("[A-Za-z0-9-]{1,48}")) && next.matches(Regex("[A-Za-z0-9-]{1,48}")))
        val stream = file.startWrite()
        try {
            stream.write("STORE_RECOVERY_V1\n$operation\n$state\n$old\n$next".toByteArray(Charsets.US_ASCII))
            stream.fd.sync(); file.finishWrite(stream); syncDirectory(file.baseFile.parentFile!!)
        } catch (failure: Throwable) { file.failWrite(stream); throw failure }
    }
}

/** Serializes every DB operation and every maintenance transition around one active Room owner. */
internal class ActiveStoreOwner(
    private val context: Context,
    private val base: File = context.noBackupFilesDir,
    private val boundaryProbe: (String) -> Unit = {},
    private val availableBytes: (File) -> Long = { it.usableSpace },
) : DatabaseOwner, UnitOfWork {
    private val gate = Mutex()
    private val pointer = GenerationPointer(base, boundaryProbe)
    private val journal = RecoveryJournal(base)
    private var active = pointer.read()
    private var delegate = create(active)
    private var recoveryChecked = false
    override val generation: GenerationId get() = GenerationId(active)
    private fun create(id: String) = RoomDatabaseOwner(context, File(base, "generations/$id"))

    private suspend fun ensureRecovery() {
        if (recoveryChecked) return
        val transition = journal.read()
        if (transition != null) {
            when (transition.state) {
                "PREPARED" -> when (active) {
                    transition.old -> journal.write(transition.operation, "ABORTED", transition.old, transition.next)
                    transition.next -> {
                        if (validateGeneration()) {
                            journal.write(transition.operation, "COMPLETE", transition.old, transition.next)
                        } else {
                            // Candidate did not survive the durable switch. Keep the intact prior
                            // generation authoritative; never expose a mixed or unverified state.
                            delegate.close()
                            active = transition.old
                            delegate = create(active)
                            check(validateGeneration()) { "Neither prepared nor prior generation validates" }
                            pointer.write(active)
                            journal.write(transition.operation, "ABORTED", transition.old, transition.next)
                        }
                    }
                    else -> error("Recovery pointer does not match transition journal")
                }
                "COMPLETE" -> check(active == transition.next) { "Completed recovery pointer mismatch" }
                "ABORTED" -> check(active == transition.old) { "Aborted recovery pointer mismatch" }
            }
        }
        check(validateGeneration()) { "Active generation integrity or media check failed" }
        cleanupUnreferencedStaging()
        recoveryChecked = true
    }

    /** Remove private, non-authoritative staging left by an interrupted operation. */
    private fun cleanupUnreferencedStaging() {
        val maintenance = File(base, "maintenance")
        val staging = File(maintenance, "staging")
        val stagingStat = try { Os.lstat(staging.path) } catch (failure: ErrnoException) {
            if (failure.errno == OsConstants.ENOENT) return else throw failure
        }
        check(!OsConstants.S_ISLNK(stagingStat.st_mode)) { "Recovery staging root is a symbolic link" }
        check(OsConstants.S_ISDIR(stagingStat.st_mode)) { "Recovery staging root is not a directory" }
        val maintenanceStat = Os.lstat(maintenance.path)
        check(!OsConstants.S_ISLNK(maintenanceStat.st_mode) && OsConstants.S_ISDIR(maintenanceStat.st_mode)) {
            "Recovery maintenance path is not a private directory"
        }
        check(staging.canonicalFile.parentFile == maintenance.canonicalFile) { "Recovery staging escaped maintenance storage" }
        val children = staging.listFiles() ?: error("Cannot enumerate recovery staging")
        for (child in children) {
            check(child.absoluteFile.parentFile == staging.absoluteFile) { "Recovery staging child escaped its root" }
            deleteStagingEntry(child)
        }
        Os.remove(staging.path)
    }

    /** lstat/unlink ensure a staged symlink is removed as a link, never traversed. */
    private fun deleteStagingEntry(entry: File) {
        val mode = Os.lstat(entry.path).st_mode
        when {
            OsConstants.S_ISLNK(mode) -> Os.remove(entry.path)
            OsConstants.S_ISDIR(mode) -> {
                val children = entry.listFiles() ?: error("Cannot enumerate recovery staging child")
                children.forEach(::deleteStagingEntry)
                Os.remove(entry.path)
            }
            else -> Os.remove(entry.path)
        }
    }
    private suspend fun validateGeneration(): Boolean {
        val db = runCatching { delegate.verifyIntegrity() }.getOrNull() ?: return false
        if (db != IntegrityResult(true, true, true)) return false
        val references = runCatching { delegate.read { catalog.imageReferences() } }.getOrNull() ?: return false
        return validateMedia(active, references)
    }
    override suspend fun <T> read(block: suspend ReadRepositories.() -> T): T = gate.withLock { ensureRecovery(); delegate.read(block) }
    override suspend fun <T> transaction(block: suspend TransactionRepositories.() -> T): T = gate.withLock { ensureRecovery(); delegate.transaction(block) }
    override suspend fun verifyIntegrity(): IntegrityResult = gate.withLock { ensureRecovery(); delegate.verifyIntegrity() }
    override suspend fun checkpointCloseReopen() = gate.withLock { ensureRecovery(); delegate.checkpointCloseReopen() }
    override suspend fun close() = gate.withLock { delegate.close() }

    /** Callback sees a closed, exclusively owned active generation. Activation only accepts a prevalidated directory. */
    internal suspend fun <T> maintenance(block: suspend MaintenanceSession.() -> T): T = gate.withLock {
        val original = active
        ensureRecovery()
        delegate.checkpointCloseReopen()
        delegate.close()
        var selected = original
        var operation = "RESTORE"
        val session = MaintenanceSession(base, original) { candidate ->
            require(candidate != original)
            require(candidate.matches(Regex("[A-Za-z0-9-]{1,48}")))
            val directory = File(base, "generations/$candidate")
            require(directory.isDirectory && File(directory, "store.db").isFile) { "Candidate generation is incomplete" }
            selected = candidate
        }
        try {
            val result = session.block()
            operation = session.operation
            if (selected != original) {
                check(validateGenerationDirectory(selected)) { "Candidate generation did not validate" }
                journal.write(operation, "PREPARED", original, selected)
                pointer.write(selected)
            }
            active = selected
            delegate = create(active)
            check(validateGeneration()) { "Activated generation failed integrity checks" }
            if (selected != original) journal.write(operation, "COMPLETE", original, selected)
            recoveryChecked = true
            result
        } catch (failure: Throwable) {
            if (selected != original) runCatching { pointer.write(original) }
            active = original
            if (selected != original) runCatching { journal.write(operation, "ABORTED", original, selected) }
            runCatching { delegate.close() }
            delegate = create(original)
            recoveryChecked = runCatching { validateGeneration() }.getOrDefault(false)
            throw failure
        }
    }

    /** Creates a verified private archive while the single owner holds the maintenance barrier. */
    internal suspend fun createBackup(automatic: Boolean = false): File = gate.withLock {
        ensureRecovery()
        val refs = delegate.read { catalog.imageReferences() }
        val archiveId = UUID.randomUUID().toString()
        val work = File(base, "maintenance/staging/backup-$archiveId")
        check(work.mkdirs()) { "Cannot create backup staging directory" }
        val backups = File(base, "backups")
        check(backups.isDirectory || backups.mkdirs())
        val partial = File(backups, ".$archiveId.partial")
        val result = File(backups, "${if (automatic) "auto" else "manual"}-$archiveId.storebackup")
        val completed = try {
            delegate.checkpointCloseReopen()
            delegate.close()
            val snapshot = File(work, "snapshot")
            val dbDir = File(snapshot, "database")
            check(dbDir.mkdirs())
            val activeMedia = File(base, "generations/$active/media/articles")
            val requiredBytes = File(base, "generations/$active/store.db").length() + refs.sumOf { File(activeMedia, it).length() }
            check(availableBytes(base) >= requiredBytes + maxOf(4L * 1024 * 1024, requiredBytes / 20)) { "Insufficient space for a consistent snapshot" }
            copyDurable(File(base, "generations/$active/store.db"), File(dbDir, "store.db"))
            val sources = mutableListOf(RecoveryArchive.Source("database/store.db", File(dbDir, "store.db")))
            for (reference in refs.sorted()) {
                require(com.vibe.store.domain.MediaSignature.validReference(reference))
                val source = File(activeMedia, reference)
                require(source.isFile && source.canonicalFile.parentFile == activeMedia.canonicalFile)
                val copy = File(snapshot, "media/$reference")
                check(copy.parentFile!!.isDirectory || copy.parentFile!!.mkdirs())
                copyDurable(source, copy)
                sources += RecoveryArchive.Source("media/$reference", copy)
            }
            check(validateSnapshot(snapshot)) { "Snapshot validation failed" }
            partial.outputStream().buffered().use { RecoveryArchive.write(it, 3, timestamp(), sources) }
            java.io.FileOutputStream(partial, true).use { it.fd.sync() }
            syncDirectory(backups)
            check(partial.renameTo(result)) { "Cannot publish complete backup" }
            syncDirectory(backups)
            if (automatic) retainAutomatic(backups)
            result
        } catch (failure: Throwable) {
            partial.delete()
            throw failure
        } finally {
            work.deleteRecursively()
            delegate = create(active)
            check(validateGeneration()) { "Active generation failed after backup" }
            recoveryChecked = true
        }
        completed
    }

    internal suspend fun inspectBackup(input: java.io.InputStream, actorId: Long): com.vibe.store.api.RecoveryPreview = gate.withLock {
        ensureRecovery()
        val work = File(base, "maintenance/staging/inspect-${UUID.randomUUID()}")
        check(work.mkdirs())
        try {
            val extracted = File(work, "extracted")
            val manifest = RecoveryArchive.read(input, extracted)
            require(manifest.schema in 1..3) { "Unsupported database schema" }
            val data = File(extracted, "database")
            require(databaseSchema(File(data, "store.db")) == manifest.schema) { "Archive schema does not match its database" }
            val candidate = RoomDatabaseOwner(context, data)
            try {
                check(candidate.verifyIntegrity() == IntegrityResult(true, true, true))
                check(candidate.read { security.account(actorId)?.let { it.role == "owner" && it.active } == true }) {
                    "Archive does not retain the active Owner"
                }
                val refs = candidate.read { catalog.imageReferences() }
                check(validateMediaDirectory(File(extracted, "media"), refs))
                candidate.checkpointCloseReopen()
            } finally { candidate.close() }
            val total = manifest.entries.sumOf { it.size }
            com.vibe.store.api.RecoveryPreview("3.0", manifest.schema, manifest.createdAt,
                manifest.entries.map { com.vibe.store.api.RecoveryEntryView(it.name, it.size, it.sha256) }, total)
        } finally { work.deleteRecursively() }
    }

    /** Validates to private staging first; the active generation is untouched until the pointer protocol. */
    internal suspend fun restoreBackup(input: java.io.InputStream, actorId: Long): String = gate.withLock {
        ensureRecovery()
        boundaryProbe("restore_before_staging")
        val id = UUID.randomUUID().toString()
        val work = File(base, "maintenance/staging/restore-$id")
        check(work.mkdirs())
        val original = active
        var pointerChanged = false
        try {
            val manifest = RecoveryArchive.read(input, File(work, "extracted"))
            require(manifest.schema in 1..3) { "Unsupported database schema" }
            val extracted = File(work, "extracted")
            val sourceRoot = File(extracted, "database")
            require(File(sourceRoot, "store.db").isFile)
            require(databaseSchema(File(sourceRoot, "store.db")) == manifest.schema) { "Archive schema does not match its database" }
            val candidateBytes = manifest.entries.sumOf { it.size }
            check(availableBytes(base) >= candidateBytes + maxOf(4L * 1024 * 1024, candidateBytes / 20)) { "Insufficient space for a restore candidate" }
            val candidateRoot = File(base, "generations/$id")
            check(candidateRoot.mkdirs())
            copyDurable(File(sourceRoot, "store.db"), File(candidateRoot, "store.db"))
            val mediaEntries = manifest.entries.filter { it.name.startsWith("media/") }
            for ((index, entry) in mediaEntries.withIndex()) {
                val name = entry.name.removePrefix("media/")
                val target = File(candidateRoot, "media/articles/$name")
                check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
                copyDurable(File(extracted, entry.name), target)
                if (index == 0) boundaryProbe("restore_during_media_staging")
            }
            // Opening an isolated candidate is allowed to run only declared Room migrations on that copy.
            val candidate = RoomDatabaseOwner(context, candidateRoot)
            try {
                check(candidate.verifyIntegrity() == IntegrityResult(true, true, true))
                check(candidate.read { security.account(actorId)?.let { it.role == "owner" && it.active } == true }) {
                    "Archive does not retain the active Owner"
                }
                val refs = candidate.read { catalog.imageReferences() }
                check(validateMedia(id, refs))
                candidate.checkpointCloseReopen()
            } finally { candidate.close() }
            boundaryProbe("restore_candidate_staged")
            val safety = createBackupLocked(reopen = false)
            check(safety.isFile && safety.length() > 0L) { "Required pre-restore backup failed" }
            journal.write("RESTORE", "PREPARED", original, id)
            boundaryProbe("restore_journal_prepared")
            pointer.write(id)
            pointerChanged = true
            boundaryProbe("restore_pointer_published")
            active = id
            delegate = create(id)
            check(validateGeneration()) { "Restored generation failed after activation" }
            appendRecoveryAudit(actorId, "recovery_restore_completed", id)
            journal.write("RESTORE", "COMPLETE", original, id)
            boundaryProbe("restore_complete_before_staging_cleanup")
            recoveryChecked = true
            work.deleteRecursively() // non-referenced cleanup is best-effort after the durable commit
            id
        } catch (failure: Throwable) {
            if (pointerChanged) {
                val rolledBack = runCatching {
                    pointer.write(original)
                    active = original
                    runCatching { delegate.close() }
                    delegate = create(original)
                    check(validateGeneration())
                    journal.write("RESTORE", "ABORTED", original, id)
                    recoveryChecked = true
                }.isSuccess
                if (!rolledBack) recoveryChecked = false
            } else {
                runCatching { delegate.close() }
                active = original
                delegate = create(original)
                recoveryChecked = runCatching { validateGeneration() }.getOrDefault(false)
            }
            if (recoveryChecked) runCatching { appendRecoveryAudit(actorId, "recovery_restore_failed", id) }
            work.deleteRecursively()
            if (active != id) File(base, "generations/$id").deleteRecursively()
            throw failure
        } finally {
            if (active != id && work.exists()) work.deleteRecursively()
        }
    }

    internal suspend fun resetStore(actorId: Long): String = gate.withLock {
        ensureRecovery()
        check(delegate.read { security.account(actorId)?.let { it.role == "owner" && it.active } == true }) {
            "Reset actor is not an active Owner"
        }
        val original = active
        val id = UUID.randomUUID().toString()
        val candidateRoot = File(base, "generations/$id")
        check(candidateRoot.mkdirs())
        val candidateDb = buildStore(context, File(candidateRoot, "store.db"))
        candidateDb.close()
        try {
            check(validateGenerationDirectory(id)) { "New empty generation failed validation" }
            val safety = createBackupLocked(reopen = false)
            check(safety.isFile && safety.length() > 0L) { "Required pre-reset backup failed" }
            journal.write("RESET", "PREPARED", original, id)
            boundaryProbe("reset_before_pointer_publish")
            pointer.write(id)
            boundaryProbe("reset_pointer_published")
            active = id
            delegate = create(id)
            check(validateGeneration()) { "Reset generation failed after activation" }
            appendRecoveryAudit(actorId, "recovery_reset_completed", id)
            journal.write("RESET", "COMPLETE", original, id)
            recoveryChecked = true
            id
        } catch (failure: Throwable) {
            val pointerRestored = runCatching { pointer.write(original) }.isSuccess
            runCatching { delegate.close() }
            active = if (pointerRestored) original else runCatching { pointer.read() }.getOrDefault(id)
            delegate = create(active)
            recoveryChecked = if (pointerRestored) runCatching { validateGeneration() }.getOrDefault(false) else false
            if (recoveryChecked) runCatching { appendRecoveryAudit(actorId, "recovery_reset_failed", id) }
            if (pointerRestored && recoveryChecked) runCatching { journal.write("RESET", "ABORTED", original, id) }
            if (pointerRestored) candidateRoot.deleteRecursively()
            throw failure
        }
    }

    private suspend fun createBackupLocked(reopen: Boolean): File {
        // Reentrant maintenance is intentionally avoided; this helper is reached only while the owner gate is held.
        // Release/reacquire would permit an ordinary read between validation and pointer publication.
        val refs = delegate.read { catalog.imageReferences() }
        val token = UUID.randomUUID().toString()
        val work = File(base, "maintenance/staging/backup-$token")
        check(work.mkdirs())
        val backups = File(base, "backups")
        check(backups.isDirectory || backups.mkdirs())
        val partial = File(backups, ".$token.partial")
        val result = File(backups, "manual-$token.storebackup")
        try {
            delegate.checkpointCloseReopen(); delegate.close()
            val snapshot = File(work, "snapshot")
            val dbDir = File(snapshot, "database"); check(dbDir.mkdirs())
            val activeMedia = File(base, "generations/$active/media/articles")
            val requiredBytes = File(base, "generations/$active/store.db").length() + refs.sumOf { File(activeMedia, it).length() }
            check(availableBytes(base) >= requiredBytes + maxOf(4L * 1024 * 1024, requiredBytes / 20)) { "Insufficient space for a consistent snapshot" }
            copyDurable(File(base, "generations/$active/store.db"), File(dbDir, "store.db"))
            val sources = mutableListOf(RecoveryArchive.Source("database/store.db", File(dbDir, "store.db")))
            refs.sorted().forEach { ref ->
                val source = File(activeMedia, ref); require(source.isFile && source.canonicalFile.parentFile == activeMedia.canonicalFile)
                val target = File(snapshot, "media/$ref"); check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
                copyDurable(source, target); sources += RecoveryArchive.Source("media/$ref", target)
            }
            partial.outputStream().buffered().use { RecoveryArchive.write(it, 3, timestamp(), sources) }
            java.io.FileOutputStream(partial, true).use { it.fd.sync() }; syncDirectory(backups)
            check(partial.renameTo(result)); syncDirectory(backups)
            return result
        } finally {
            work.deleteRecursively()
            if (reopen) { delegate = create(active); check(validateGeneration()); recoveryChecked = true }
        }
    }

    private suspend fun appendRecoveryAudit(actorId: Long, action: String, reference: String) {
        require(actorId > 0 && action.matches(Regex("recovery_[a-z_]{1,48}")) && reference.matches(Regex("[A-Za-z0-9._-]{1,96}")))
        delegate.transaction {
            security.appendAudit(com.vibe.store.application.security.SecurityAudit(
                actorId, null, action, "recovery", reference, true, timestamp(),
            ))
        }
    }

    private fun validateSnapshot(root: File): Boolean {
        val database = File(root, "database/store.db")
        return runCatching {
            val opened = BundledSQLiteDriver().open(database.absolutePath, 1)
            opened.use { it.healthy() == IntegrityResult(true, true, true) }
        }.getOrDefault(false)
    }

    /** Checks the untrusted archive claim against SQLite before Room can migrate the candidate. */
    private fun databaseSchema(file: File): Int {
        require(file.isFile)
        return BundledSQLiteDriver().open(file.absolutePath, 1).use {
            it.scalar("PRAGMA user_version").toInt()
        }
    }

    private fun copyDurable(from: File, to: File) {
        require(from.isFile && to.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
        from.inputStream().use { input -> FileOutputStream(to).use { output ->
            input.copyTo(output, 64 * 1024); output.fd.sync()
        } }
        syncDirectory(to.parentFile!!)
    }

    private fun timestamp(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(Date())

    private fun retainAutomatic(directory: File) {
        val automatic = (directory.listFiles() ?: error("Cannot enumerate backups"))
            .filter { it.isFile && it.name.matches(Regex("auto-[0-9a-f-]{36}\\.storebackup")) && it.canonicalFile.parentFile == directory.canonicalFile }
            .sortedByDescending(File::lastModified)
        automatic.drop(7).forEach { file -> check(file.delete()) { "Cannot prune old automatic backup" } }
        syncDirectory(directory)
    }
    private suspend fun validateGenerationDirectory(id: String): Boolean {
        val candidateOwner = create(id)
        return try {
            val integrity = candidateOwner.verifyIntegrity()
            if (integrity != IntegrityResult(true, true, true)) return false
            val refs = candidateOwner.read { catalog.imageReferences() }
            validateMedia(id, refs)
        } catch (_: Exception) { false } finally { candidateOwner.close() }
    }
    private fun validateMedia(id: String, references: Set<String>): Boolean {
        return validateMediaDirectory(File(base, "generations/$id/media/articles"), references)
    }
    private fun validateMediaDirectory(directory: File, references: Set<String>): Boolean {
        if (references.isEmpty()) return true
        if (!directory.isDirectory) return false
        return references.all { reference ->
            if (!com.vibe.store.domain.MediaSignature.validReference(reference)) return@all false
            val file = File(directory, reference)
            if (!file.isFile || file.length() !in 12..(com.vibe.store.domain.MediaPolicy.PRODUCT.maximumBytes.toLong())) return@all false
            if (file.canonicalFile.parentFile != directory.canonicalFile) return@all false
            val prefix = ByteArray(12)
            val count = file.inputStream().use { it.read(prefix) }
            val kind = if (count < 0) null else com.vibe.store.domain.MediaSignature.detect(prefix.copyOf(count))
            kind != null && reference.endsWith(".${kind.extension}")
        }
    }
}

internal class MaintenanceSession internal constructor(
    base: File,
    val oldGeneration: String,
    private val select: (String) -> Unit,
) {
    val root = File(base, "generations/$oldGeneration")
    val backups = File(base, "backups")
    val staging = File(base, "maintenance/staging")
    internal var operation: String = "RESTORE"
    fun activateValidatedGeneration(id: String) = select(id)
    fun markReset() { operation = "RESET" }
}

private class Lease : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<Lease>
}

/** One OS lock and one Room instance. Even readers are serialized in this initial
 * foundation: maintenance cannot overtake accepted work or copy an active DB. */
internal class RoomDatabaseOwner(
    private val context: Context,
    internal val root: File,
    private val targetVersion: Int = 3,
    private val factory: (Context, File) -> StoreDatabase = ::buildStore
) : DatabaseOwner, UnitOfWork {
    override val generation = GenerationId(root.name)
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
        check(snapshot.exists() && fileSha256(snapshot) == record[3]) { "Recovery snapshot missing or corrupt" }
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
        check(fileSha256(file) == fileSha256(snapshot))
        BundledSQLiteDriver().open(snapshot.absolutePath, 1).use { check(it.healthy() == IntegrityResult(true, true, true)) }
        val stream = journal.startWrite()
        try {
            stream.write("PREPARED\n$oldVersion\n$targetVersion\n${fileSha256(snapshot)}".toByteArray())
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
                connection.usePrepared("PRAGMA integrity_check") { check(it.step() && it.getText(0) == "ok") }
                connection.usePrepared("PRAGMA foreign_key_check") { check(!it.step()) }
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
    override suspend fun <T> read(block: suspend ReadRepositories.() -> T): T = accepted { db -> reader(db) { connection ->
        // A stable SQLite snapshot across bounded exact-payment pages and the opening balance.
        connection.withTransaction(Transactor.SQLiteTransactionType.DEFERRED) {
            val repositories = RoomRepositories(db.records(), currentCoroutineContext()[Job], false)
            try { repositories.block() } finally { repositories.release() }
        }
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
