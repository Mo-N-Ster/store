package com.vibe.store.infrastructure.persistence

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.room.*
import android.util.AtomicFile
import androidx.sqlite.*
import com.vibe.store.application.persistence.IntegrityResult
import com.vibe.store.application.security.NewOwner
import com.vibe.store.domain.CatalogRecord
import com.vibe.store.infrastructure.security.KeystoreSecrets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.UUID
import java.math.BigDecimal
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class ActiveStoreOwnerTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun writeDurable(path: File, value: String) {
        path.parentFile!!.mkdirs()
        val atomic = AtomicFile(path)
        val output = atomic.startWrite()
        try { output.write(value.toByteArray()); output.fd.sync(); atomic.finishWrite(output) }
        catch (failure: Throwable) { atomic.failWrite(output); throw failure }
    }

    @Test fun activationIsSerializedVerifiedAndSurvivesOwnerRecreation() = runBlocking {
        val base = File(context.noBackupFilesDir, "i12-owner-${UUID.randomUUID()}")
        val owner = ActiveStoreOwner(context, base)
        try {
            assertEquals("main", owner.generation.value)
            assertEquals(IntegrityResult(true, true, true), owner.verifyIntegrity())
            val next = "candidate-${UUID.randomUUID()}"
            val candidateRoot = File(base, "generations/$next").apply { check(mkdirs()) }
            val candidate = buildStore(context, File(candidateRoot, "store.db"))
            candidate.useWriterConnection { connection ->
                connection.usePrepared("PRAGMA quick_check") { assertTrue(it.step() && it.getText(0) == "ok") }
            }
            candidate.close()

            owner.maintenance { activateValidatedGeneration(next) }
            assertEquals(next, owner.generation.value)
            assertEquals(IntegrityResult(true, true, true), owner.verifyIntegrity())
            owner.close()

            val reopened = ActiveStoreOwner(context, base)
            try {
                assertEquals(next, reopened.generation.value)
                assertEquals(IntegrityResult(true, true, true), reopened.verifyIntegrity())
            } finally { reopened.close() }
        } finally { runCatching { owner.close() }; base.deleteRecursively() }
    }

    @Test fun startupCompletesPreparedGenerationWhosePointerAlreadySwitched() = runBlocking {
        val base = File(context.noBackupFilesDir, "i12-prepared-new-${UUID.randomUUID()}")
        val old = ActiveStoreOwner(context, base)
        try {
            old.verifyIntegrity()
            val next = "candidate-${UUID.randomUUID()}"
            val candidateRoot = File(base, "generations/$next").apply { check(mkdirs()) }
            buildStore(context, File(candidateRoot, "store.db")).close()
            old.close()
            writeDurable(File(base, "maintenance/active-generation"), next)
            writeDurable(File(base, "maintenance/recovery-transition"), "STORE_RECOVERY_V1\nRESTORE\nPREPARED\nmain\n$next")

            val restarted = ActiveStoreOwner(context, base)
            try {
                assertEquals(next, restarted.generation.value)
                assertEquals(IntegrityResult(true, true, true), restarted.verifyIntegrity())
                assertTrue(File(base, "maintenance/recovery-transition").readText().contains("\nCOMPLETE\n"))
            } finally { restarted.close() }
        } finally { runCatching { old.close() }; base.deleteRecursively() }
    }

    @Test fun startupRollsBackPreparedInvalidGenerationToVerifiedOldState() = runBlocking {
        val base = File(context.noBackupFilesDir, "i12-prepared-old-${UUID.randomUUID()}")
        val old = ActiveStoreOwner(context, base)
        try {
            old.verifyIntegrity()
            old.close()
            val next = "candidate-${UUID.randomUUID()}"
            val candidateRoot = File(base, "generations/$next").apply { check(mkdirs()) }
            File(candidateRoot, "store.db").writeBytes(byteArrayOf(1, 2, 3))
            writeDurable(File(base, "maintenance/active-generation"), next)
            writeDurable(File(base, "maintenance/recovery-transition"), "STORE_RECOVERY_V1\nRESTORE\nPREPARED\nmain\n$next")

            val restarted = ActiveStoreOwner(context, base)
            try {
                assertEquals(IntegrityResult(true, true, true), restarted.verifyIntegrity())
                assertEquals("main", restarted.generation.value)
                assertEquals("main", File(base, "maintenance/active-generation").readText())
                assertTrue(File(base, "maintenance/recovery-transition").readText().contains("\nABORTED\n"))
            } finally { restarted.close() }
        } finally { runCatching { old.close() }; base.deleteRecursively() }
    }

    @Test fun malformedGenerationPointerFailsClosed() {
        val base = File(context.noBackupFilesDir, "i12-pointer-${UUID.randomUUID()}")
        val directory = File(base, "maintenance").apply { check(mkdirs()) }
        File(directory, "active-generation").writeText("../generations/main")
        try { ActiveStoreOwner(context, base); fail("pointer traversal accepted") }
        catch (_: IllegalArgumentException) { }
        finally { base.deleteRecursively() }
    }

    @Test fun startupRemovesOnlyOrphanRecoveryStagingAfterGenerationValidation() = runBlocking {
        val base = File(context.noBackupFilesDir, "i12-orphan-staging-${UUID.randomUUID()}")
        val owner = ActiveStoreOwner(context, base)
        try {
            assertEquals(IntegrityResult(true, true, true), owner.verifyIntegrity())
            val active = owner.generation.value
            owner.close()
            val staging = File(base, "maintenance/staging/restore-interrupted").apply { check(mkdirs()) }
            File(staging, "partial-archive.bin").writeBytes(byteArrayOf(1, 2, 3))
            val outside = File(base, "must-survive.txt").apply { writeText("synthetic sentinel") }
            android.system.Os.symlink(outside.path, File(staging, "escape-link").path)

            val reopened = ActiveStoreOwner(context, base)
            try {
                assertEquals(active, reopened.generation.value)
                assertEquals(IntegrityResult(true, true, true), reopened.verifyIntegrity())
                assertFalse("Orphan staging is cleaned only after the active generation validates",
                    File(base, "maintenance/staging").exists())
                assertEquals("synthetic sentinel", outside.readText())
                assertTrue(File(base, "generations/$active/store.db").isFile)
            } finally { reopened.close() }
        } finally { runCatching { owner.close() }; base.deleteRecursively() }
    }

    @Test fun verifiedBackupRestoresThroughGenerationCommit() = runBlocking {
        val base = File(context.noBackupFilesDir, "i12-backup-${UUID.randomUUID()}")
        val owner = ActiveStoreOwner(context, base)
        try {
            assertEquals(IntegrityResult(true, true, true), owner.verifyIntegrity())
            val actorId = owner.transaction {
                security.createOwner(NewOwner("fixture", "owner@example.test", "Synthetic", "Owner",
                    "synthetic-verifier", "question", "answer", "2026-10-10T00:00:00Z"))
            }
            val original = owner.generation.value
            val reference = "a1234567-89ab-4cde-8f01-23456789abcd.webp"
            val image = File(base, "generations/$original/media/articles/$reference")
            check(image.parentFile!!.mkdirs())
            image.writeBytes("RIFF".toByteArray() + byteArrayOf(0, 0, 0, 0) + "WEBP".toByteArray())
            owner.transaction {
                catalog.write(CatalogRecord(1, "Synthetic product", "Food", "#i12", "fixture",
                    BigDecimal("1.2300"), 5, 1, "2026-10-10T00:00:00Z", "2026-10-10T00:00:00Z", null, reference))
                catalog.movement(1, 5, "initial", BigDecimal("1.2300"), "2026-10-10T00:00:00Z", null)
                catalog.price(1, BigDecimal("1.2300"), "2026-10-10T00:00:00Z")
            }
            val backup = owner.createBackup()
            assertTrue(backup.isFile && backup.length() > 0L)
            val restored = owner.restoreBackup(backup.inputStream(), actorId = actorId)
            assertTrue(restored != original)
            assertEquals(restored, owner.generation.value)
            assertEquals(IntegrityResult(true, true, true), owner.verifyIntegrity())
            assertEquals("Synthetic product", owner.read { catalog.find(1)?.name })
            assertEquals("recovery_restore_completed", owner.read { audit.audit(1)?.action })
            assertTrue(reference in owner.read { catalog.imageReferences() })
            assertTrue(File(base, "generations/$restored/media/articles/$reference").isFile)
            owner.close()

            val restarted = ActiveStoreOwner(context, base)
            try {
                assertEquals(restored, restarted.generation.value)
                assertEquals(IntegrityResult(true, true, true), restarted.verifyIntegrity())
            } finally { restarted.close() }
        } finally { runCatching { owner.close() }; base.deleteRecursively() }
    }

    @Test fun completeSyntheticBusinessDatabaseSurvivesBackupAndRestore() = runBlocking {
        val base = File(context.noBackupFilesDir, "i12-multidomain-${UUID.randomUUID()}")
        val owner = ActiveStoreOwner(context, base)
        try {
            owner.verifyIntegrity()
            val reference = "b1234567-89ab-4cde-8f01-23456789abcd.webp"
            owner.transaction {
                (this as RoomRepositories).dao.fixture()
                catalog.write(checkNotNull(catalog.find(1)).copy(imageRef = reference))
            }
            val media = File(base, "generations/${owner.generation.value}/media/articles/$reference")
            check(media.parentFile!!.mkdirs())
            media.writeBytes("RIFF".toByteArray() + byteArrayOf(0, 0, 0, 0) + "WEBP".toByteArray())
            val backup = owner.createBackup()
            val generation = owner.generation.value
            val restored = owner.restoreBackup(backup.inputStream(), actorId = 1)
            assertTrue(restored != generation)
            owner.read {
                assertEquals("Synthetic", catalog.find(1)?.name)
                assertTrue(reference in catalog.imageReferences())
                assertEquals("SYN-INV", sales.invoice("SYN-INV")?.id)
                assertEquals(1L, purchases.purchase(1)?.id)
                assertEquals(1L, inventories.inventory(1)?.id)
                assertEquals("SYN-1", employees.find(1)?.employeeCode)
                assertEquals("VALID", attendance.attendance(1)?.status)
                assertEquals("Synthetic", messaging.message(1)?.subject)
                assertEquals("pending", delivery.email(1)?.status)
                assertEquals("v1", settings.value("synthetic"))
                assertEquals("recovery_restore_completed", audit.audit(2)?.action)
            }
            assertTrue(File(base, "generations/$restored/media/articles/$reference").isFile)
            assertEquals(IntegrityResult(true, true, true), owner.verifyIntegrity())
        } finally { runCatching { owner.close() }; base.deleteRecursively() }
    }

    @Test fun backupContainsOnlyKeystoreSealedSmtpSecretNotPlaintext() = runBlocking {
        val base = File(context.noBackupFilesDir, "i12-secret-backup-${UUID.randomUUID()}")
        val alias = "i12-synthetic-${UUID.randomUUID()}"
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val secret = "I12-SYNTHETIC-SMTP-${UUID.randomUUID()}"
        val owner = ActiveStoreOwner(context, base)
        try {
            val envelope = KeystoreSecrets(alias).seal(secret.toByteArray(Charsets.UTF_8))
            val stored = android.util.Base64.encodeToString(envelope, android.util.Base64.NO_WRAP)
            owner.transaction { settings.put("smtpPasswordEnvelope", stored) }
            assertEquals(stored, owner.read { settings.value("smtpPasswordEnvelope") })

            val archive = owner.createBackup()
            val databaseBytes = ZipInputStream(archive.inputStream()).use { zip ->
                var found: ByteArray? = null
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == "database/store.db") {
                        found = zip.readBytes()
                        break
                    }
                }
                checkNotNull(found)
            }
            val databaseText = databaseBytes.toString(Charsets.ISO_8859_1)
            assertTrue("Backup must preserve the sealed envelope", databaseText.contains(stored))
            assertFalse("Backup must never contain SMTP plaintext", databaseText.contains(secret))
        } finally {
            runCatching { owner.close() }
            runCatching { keyStore.deleteEntry(alias) }
            base.deleteRecursively()
        }
    }

    @Test fun rejectedArchiveLeavesActiveGenerationUntouched() = runBlocking {
        val base = File(context.noBackupFilesDir, "i12-reject-${UUID.randomUUID()}")
        val owner = ActiveStoreOwner(context, base)
        try {
            assertEquals(IntegrityResult(true, true, true), owner.verifyIntegrity())
            val original = owner.generation.value
            val rejected = runCatching { owner.restoreBackup(byteArrayOf(0x50, 0x4b, 0x03).inputStream(), actorId = 7) }
            assertTrue(rejected.isFailure)
            assertEquals(original, owner.generation.value)
            assertEquals(IntegrityResult(true, true, true), owner.verifyIntegrity())
        } finally { runCatching { owner.close() }; base.deleteRecursively() }
    }

    @Test fun simulatedLowSpaceRejectsBackupAndRestoreWithoutPublishingOrSwitching() = runBlocking {
        val sourceBase = File(context.noBackupFilesDir, "i12-low-space-source-${UUID.randomUUID()}")
        val targetBase = File(context.noBackupFilesDir, "i12-low-space-target-${UUID.randomUUID()}")
        val source = ActiveStoreOwner(context, sourceBase)
        val target = ActiveStoreOwner(context, targetBase, availableBytes = { 0L })
        try {
            source.verifyIntegrity()
            val archive = source.createBackup()
            assertTrue(archive.isFile && archive.length() > 0L)
            target.verifyIntegrity()
            val original = target.generation.value

            assertTrue(runCatching { target.createBackup() }.isFailure)
            assertEquals(original, target.generation.value)
            assertEquals(0, File(targetBase, "backups").listFiles().orEmpty().count { it.name.endsWith(".storebackup") })
            assertEquals(IntegrityResult(true, true, true), target.verifyIntegrity())

            assertTrue(runCatching { target.restoreBackup(archive.inputStream(), actorId = 1L) }.isFailure)
            assertEquals(original, target.generation.value)
            assertFalse(File(targetBase, "maintenance/staging").listFiles().orEmpty().any { it.name.startsWith("restore-") })
            assertEquals(IntegrityResult(true, true, true), target.verifyIntegrity())
        } finally {
            runCatching { source.close() }; runCatching { target.close() }
            sourceBase.deleteRecursively(); targetBase.deleteRecursively()
        }
    }

    @Test fun restoreMigratesLegacySchemaOnlyInsideCandidateBeforePublishingGeneration() = runBlocking {
        val legacy = File(context.noBackupFilesDir, "i12-legacy-source-${UUID.randomUUID()}")
        val base = File(context.noBackupFilesDir, "i12-legacy-target-${UUID.randomUUID()}")
        legacyV1(legacy)
        val archive = ByteArrayOutputStream().also {
            RecoveryArchive.write(it, 1, "2026-10-10T00:00:00Z", listOf(RecoveryArchive.Source("database/store.db", File(legacy, "store.db"))))
        }.toByteArray()
        val owner = ActiveStoreOwner(context, base)
        try {
            owner.verifyIntegrity()
            val original = owner.generation.value
            val restored = owner.restoreBackup(archive.inputStream(), actorId = 1L)
            assertNotEquals(original, restored)
            assertEquals(restored, owner.generation.value)
            owner.read {
                val dao = (this as RoomRepositories).dao
                assertEquals("synthetic", dao.account(1)?.username)
                assertEquals("Synthetic", catalog.find(1)?.name)
                assertEquals("SYN-INV", sales.invoice("SYN-INV")?.id)
                assertEquals("Historical product", dao.invoiceLines("SYN-INV").single().productName)
            }
            assertEquals(IntegrityResult(true, true, true), owner.verifyIntegrity())
            owner.checkpointCloseReopen()
            androidx.sqlite.driver.bundled.BundledSQLiteDriver().open(File(base, "generations/$restored/store.db").path).use { connection ->
                assertEquals("3", connection.prepare("PRAGMA user_version").use { check(it.step()); it.getText(0) })
                assertEquals("text", connection.prepare("SELECT typeof(price) FROM products WHERE id=1").use { check(it.step()); it.getText(0) })
            }
        } finally {
            runCatching { owner.close() }
            legacy.deleteRecursively(); base.deleteRecursively()
        }
    }

    @Test fun archiveManifestSchemaMustMatchSqliteBeforeInspectionOrRestore() = runBlocking {
        val base = File(context.noBackupFilesDir, "i12-schema-mismatch-${UUID.randomUUID()}")
        val fixture = File(base, "fixture/store.db")
        check(fixture.parentFile!!.mkdirs())
        val database = buildStore(context, fixture)
        database.useWriterConnection { connection ->
            connection.usePrepared("PRAGMA user_version") { assertTrue(it.step() && it.getLong(0) == 3L) }
        }
        database.close()
        val archive = ByteArrayOutputStream().also {
            RecoveryArchive.write(it, 2, "2026-10-10T00:00:00Z", listOf(RecoveryArchive.Source("database/store.db", fixture)))
        }.toByteArray()
        val owner = ActiveStoreOwner(context, base)
        try {
            owner.verifyIntegrity()
            val original = owner.generation.value
            assertTrue(runCatching { owner.inspectBackup(archive.inputStream(), actorId = 1) }.isFailure)
            assertTrue(runCatching { owner.restoreBackup(archive.inputStream(), actorId = 1) }.isFailure)
            assertEquals(original, owner.generation.value)
            assertEquals(IntegrityResult(true, true, true), owner.verifyIntegrity())
        } finally { runCatching { owner.close() }; base.deleteRecursively() }
    }

    @Test fun archiveWithoutActiveOwnerIsRejectedBeforeGenerationSwitch() = runBlocking {
        val base = File(context.noBackupFilesDir, "i12-owner-archive-${UUID.randomUUID()}")
        val foreign = File(context.noBackupFilesDir, "i12-owner-source-${UUID.randomUUID()}")
        val owner = ActiveStoreOwner(context, base)
        val source = ActiveStoreOwner(context, foreign)
        try {
            owner.verifyIntegrity(); source.verifyIntegrity()
            val actorId = owner.transaction {
                security.createOwner(NewOwner("fixture", "owner@example.test", "Synthetic", "Owner",
                    "synthetic-verifier", "question", "answer", "2026-10-10T00:00:00Z"))
            }
            val original = owner.generation.value
            val archive = source.createBackup()
            assertTrue(runCatching { owner.inspectBackup(archive.inputStream(), actorId) }.isFailure)
            assertTrue(runCatching { owner.restoreBackup(archive.inputStream(), actorId) }.isFailure)
            assertEquals(original, owner.generation.value)
            assertEquals("recovery_restore_failed", owner.read { audit.audit(1)?.action })
            assertEquals(IntegrityResult(true, true, true), owner.verifyIntegrity())
        } finally {
            runCatching { owner.close() }; runCatching { source.close() }
            base.deleteRecursively(); foreign.deleteRecursively()
        }
    }

    @Test fun automaticRetentionKeepsSevenAndNeverDeletesManualArchives() = runBlocking {
        val base = File(context.noBackupFilesDir, "i12-retention-${UUID.randomUUID()}")
        val owner = ActiveStoreOwner(context, base)
        try {
            val manual = owner.createBackup()
            repeat(8) { owner.createBackup(automatic = true) }
            val backups = File(base, "backups").listFiles()!!.filter { it.isFile }
            assertEquals(7, backups.count { it.name.startsWith("auto-") })
            assertTrue(manual.isFile)
            assertEquals(IntegrityResult(true, true, true), owner.verifyIntegrity())
        } finally { runCatching { owner.close() }; base.deleteRecursively() }
    }

    @Test fun resetUsesVerifiedEmptyGenerationAndRetainsSafetyBackup() = runBlocking {
        val base = File(context.noBackupFilesDir, "i12-reset-${UUID.randomUUID()}")
        val owner = ActiveStoreOwner(context, base)
        try {
            owner.verifyIntegrity()
            val actorId = owner.transaction {
                security.createOwner(NewOwner("fixture", "owner@example.test", "Synthetic", "Owner",
                    "synthetic-verifier", "question", "answer", "2026-10-10T00:00:00Z"))
            }
            val old = owner.generation.value
            val reset = owner.resetStore(actorId = actorId)
            assertTrue(reset != old)
            assertEquals(reset, owner.generation.value)
            assertEquals(0, owner.read { security.accounts().size })
            assertEquals("recovery_reset_completed", owner.read { audit.audit(1)?.action })
            assertTrue(File(base, "backups").listFiles()!!.any { it.name.startsWith("manual-") && it.length() > 0L })
            assertEquals(IntegrityResult(true, true, true), owner.verifyIntegrity())
            owner.close()
            val restarted = ActiveStoreOwner(context, base)
            try { assertEquals(reset, restarted.generation.value); assertEquals(0, restarted.read { security.accounts().size }) }
            finally { restarted.close() }
        } finally { runCatching { owner.close() }; base.deleteRecursively() }
    }
}
