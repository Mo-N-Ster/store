package com.vibe.store.infrastructure.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vibe.store.api.*
import com.vibe.store.application.catalog.CatalogAuthority
import com.vibe.store.application.persistence.CommandCoordinator
import com.vibe.store.application.recovery.RecoveryAuthority
import com.vibe.store.application.recovery.RecoveryBackend
import com.vibe.store.application.sales.SaleAuthority
import com.vibe.store.application.security.IdentityAuthority
import com.vibe.store.application.security.NewManagedAccount
import com.vibe.store.infrastructure.media.AndroidProductMedia
import com.vibe.store.infrastructure.security.BcryptPasswords
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.io.File
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

/** Product authority plus real Room recovery backend, restricted to synthetic noBackup fixtures. */
class RecoveryAuthorityNativeTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun ownerBackupPreviewConfirmationRestoreAndSessionInvalidationUseRealRoomGeneration() = runBlocking {
        val base = File(context.noBackupFilesDir, "i12-authority-${UUID.randomUUID()}")
        val owner = ActiveStoreOwner(context, base)
        val passwords = BcryptPasswords()
        val identity = IdentityAuthority(owner, CommandCoordinator(owner), passwords)
        val catalog = CatalogAuthority(identity, AndroidProductMedia.fixture(File(base, "unused-media")) { error("No image expected") })
        val recovery = RecoveryAuthority(identity, catalog, AndroidRecoveryBackend(owner))
        try {
            identity.bootstrap(OwnerRegistration("synthetic-owner", "owner@example.invalid", "Synthetic", "Owner", "Password-123", "Question", "Answer"))
            identity.login(Credentials("synthetic-owner", "Password-123"))
            catalog.save(ProductDraft(name = "Recovery fixture", category = "Synthetic", price = BigDecimal("1.2300")))

            val bytes = ByteArrayOutputStream()
            val receipt = recovery.createBackup(object : RecoveryTarget { override fun openOutputStream(): OutputStream = bytes })
            assertEquals(bytes.size().toLong(), receipt.bytes)
            assertFalse(receipt.externallyVerified)
            val archive = bytes.toByteArray()
            val source = object : RecoverySource { override fun openInputStream(): InputStream = ByteArrayInputStream(archive) }
            val preview = recovery.inspect(source)
            assertTrue(preview.entries.any { it.name == "database/store.db" })
            assertTrue(preview.entries.any { it.name.startsWith("media/") }.not())
            val generationBefore = owner.generation.value

            try { recovery.restore(source, confirmed = false); fail("Unconfirmed restore was accepted") }
            catch (failure: RecoveryFailure) { assertEquals(RecoveryError.CONFIRMATION_REQUIRED, failure.code) }
            assertEquals(generationBefore, owner.generation.value)

            recovery.restore(source, confirmed = true)
            assertNotEquals(generationBefore, owner.generation.value)
            assertNull("Restore invalidates the old authenticated session", identity.current())
            identity.login(Credentials("synthetic-owner", "Password-123"))
            assertEquals("Recovery fixture", catalog.list(CatalogFilter(limit = 10)).items.single().name)
            assertEquals(0, catalog.list(CatalogFilter(limit = 10)).items.single().price.compareTo(BigDecimal("1.23")))
            assertEquals(com.vibe.store.application.persistence.IntegrityResult(true, true, true), owner.verifyIntegrity())
        } finally {
            runCatching { owner.close() }
            base.deleteRecursively()
        }
    }

    @Test fun employeeAndSignedOutSessionsCannotInspectExportRestoreOrReset() = runBlocking {
        val base = File(context.noBackupFilesDir, "i12-denial-${UUID.randomUUID()}")
        val owner = ActiveStoreOwner(context, base)
        val passwords = BcryptPasswords()
        val identity = IdentityAuthority(owner, CommandCoordinator(owner), passwords)
        val catalog = CatalogAuthority(identity, AndroidProductMedia.fixture(File(base, "unused-media")) { error("No image expected") })
        val recovery = RecoveryAuthority(identity, catalog, AndroidRecoveryBackend(owner))
        var targetOpened = false
        val target = object : RecoveryTarget { override fun openOutputStream(): OutputStream { targetOpened = true; return ByteArrayOutputStream() } }
        val source = object : RecoverySource { override fun openInputStream(): InputStream = ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
        try {
            identity.bootstrap(OwnerRegistration("synthetic-owner", "owner@example.invalid", "Synthetic", "Owner", "Password-123", "Question", "Answer"))
            identity.login(Credentials("synthetic-owner", "Password-123"))
            owner.transaction {
                security.createManaged(NewManagedAccount("synthetic-employee", null, "Synthetic", "Employee", "SE", "employee",
                    passwords.hash("Employee-123"), null, null, true, null, "2026-10-10T00:00:00Z"))
            }
            identity.switchUser(Credentials("synthetic-employee", "Employee-123"), confirmDiscard = true)
            val generation = owner.generation.value
            try { recovery.createBackup(target); fail("Employee backup export was accepted") }
            catch (failure: SecurityFailure) { assertEquals(SecurityError.FORBIDDEN, failure.code) }
            assertFalse(targetOpened)
            try { recovery.inspect(source); fail("Employee archive inspection was accepted") }
            catch (failure: SecurityFailure) { assertEquals(SecurityError.FORBIDDEN, failure.code) }
            try { recovery.restore(source, confirmed = true); fail("Employee restore was accepted") }
            catch (failure: SecurityFailure) { assertEquals(SecurityError.FORBIDDEN, failure.code) }
            try { recovery.reset("Password-123", confirmed = true); fail("Employee reset was accepted") }
            catch (failure: SecurityFailure) { assertEquals(SecurityError.FORBIDDEN, failure.code) }
            assertEquals(generation, owner.generation.value)
            assertEquals(0, File(base, "backups").listFiles().orEmpty().size)

            identity.logout(confirmDiscard = true)
            try { recovery.createBackup(target); fail("Signed-out backup export was accepted") }
            catch (_: SecurityFailure) { }
            assertFalse(targetOpened)
            assertEquals(generation, owner.generation.value)
        } finally {
            runCatching { owner.close() }
            base.deleteRecursively()
        }
    }

    @Test fun revokedSourceAndInterruptedDestinationFailWithoutChangingActiveGeneration() = runBlocking {
        val base = File(context.noBackupFilesDir, "i12-provider-failure-${UUID.randomUUID()}")
        val owner = ActiveStoreOwner(context, base)
        val identity = IdentityAuthority(owner, CommandCoordinator(owner), BcryptPasswords())
        val catalog = CatalogAuthority(identity, AndroidProductMedia.fixture(File(base, "unused-media")) { error("No image expected") })
        val recovery = RecoveryAuthority(identity, catalog, AndroidRecoveryBackend(owner))
        try {
            identity.bootstrap(OwnerRegistration("synthetic-owner", "owner@example.invalid", "Synthetic", "Owner", "Password-123", "Question", "Answer"))
            identity.login(Credentials("synthetic-owner", "Password-123"))
            catalog.save(ProductDraft(name = "Provider failure fixture", category = "Synthetic", price = BigDecimal("2.00")))
            val generation = owner.generation.value
            val partial = ByteArrayOutputStream()
            val failingTarget = object : RecoveryTarget {
                override fun openOutputStream(): OutputStream = object : OutputStream() {
                    override fun write(value: Int) { throw IOException("synthetic provider interruption") }
                    override fun write(bytes: ByteArray, offset: Int, length: Int) {
                        partial.write(bytes, offset, minOf(length, 32))
                        throw IOException("synthetic provider interruption")
                    }
                }
            }
            try { recovery.createBackup(failingTarget); fail("Partial SAF write was reported as a valid backup") }
            catch (failure: RecoveryFailure) { assertEquals(RecoveryError.STORAGE_UNAVAILABLE, failure.code) }
            assertTrue(partial.size() > 0)
            assertEquals(generation, owner.generation.value)
            assertEquals(com.vibe.store.application.persistence.IntegrityResult(true, true, true), owner.verifyIntegrity())

            val revokedSource = object : RecoverySource { override fun openInputStream(): InputStream? = null }
            try { recovery.inspect(revokedSource); fail("Revoked provider URI was accepted") }
            catch (failure: RecoveryFailure) { assertEquals(RecoveryError.STORAGE_UNAVAILABLE, failure.code) }
            try { recovery.restore(revokedSource, confirmed = true); fail("Restore proceeded without provider bytes") }
            catch (failure: RecoveryFailure) { assertEquals(RecoveryError.STORAGE_UNAVAILABLE, failure.code) }
            assertEquals(generation, owner.generation.value)
            assertEquals("Provider failure fixture", catalog.list(CatalogFilter(limit = 10)).items.single().name)
            assertEquals(com.vibe.store.application.persistence.IntegrityResult(true, true, true), owner.verifyIntegrity())
        } finally {
            runCatching { owner.close() }
            base.deleteRecursively()
        }
    }

    @Test fun scheduledAutomaticBackupSerializesWithLivePosSale() = runBlocking {
        val base = File(context.noBackupFilesDir, "i12-pos-worker-${UUID.randomUUID()}")
        val owner = ActiveStoreOwner(context, base)
        val identity = IdentityAuthority(owner, CommandCoordinator(owner), BcryptPasswords())
        val catalog = CatalogAuthority(identity, AndroidProductMedia.fixture(File(base, "unused-media")) { error("No image expected") })
        val recovery = RecoveryAuthority(identity, catalog, AndroidRecoveryBackend(owner))
        val sales = SaleAuthority(identity)
        try {
            identity.bootstrap(OwnerRegistration("synthetic-owner", "owner@example.invalid", "Synthetic", "Owner", "Password-123", "Question", "Answer"))
            identity.login(Credentials("synthetic-owner", "Password-123"))
            val product = catalog.save(ProductDraft(name = "Concurrent POS fixture", category = "Synthetic",
                price = BigDecimal("2.50"), initialStock = 5.0)).product
            val cash = sales.openCash(BigDecimal.ZERO)

            val backupJob = async { recovery.createAutomaticBackup() }
            val saleJob = async {
                sales.sell(SaleCommand("i12-${UUID.randomUUID()}", cash.id,
                    listOf(SaleLine(product.id, 1)), received = BigDecimal("2.50")))
            }
            val backupReceipt = requireNotNull(backupJob.await())
            val receipt = saleJob.await()
            assertEquals("validated", receipt.status)
            assertTrue(backupReceipt.bytes > 0)
            assertEquals(1, sales.history(SaleFilter(limit = 10)).items.count { it.id == receipt.id })
            val archived = (File(base, "backups").listFiles() ?: emptyArray())
                .single { it.name.startsWith("auto-") && it.name.endsWith(".storebackup") }
            val preview = recovery.inspect(object : RecoverySource { override fun openInputStream() = archived.inputStream() })
            assertEquals(3, preview.schema)
            assertTrue(preview.entries.any { it.name == "database/store.db" })
            assertEquals(com.vibe.store.application.persistence.IntegrityResult(true, true, true), owner.verifyIntegrity())
        } finally {
            runCatching { owner.close() }
            base.deleteRecursively()
        }
    }

    @Test fun resetPasswordFailuresLockOwnerWithoutReplacingDataThenPermitAuthorizedReset() = runBlocking {
        val base = File(context.noBackupFilesDir, "i12-reset-auth-${UUID.randomUUID()}")
        val owner = ActiveStoreOwner(context, base)
        var now = 1_800_000_000_000L
        val identity = IdentityAuthority(owner, CommandCoordinator(owner), BcryptPasswords(), clock = { now })
        val catalog = CatalogAuthority(identity, AndroidProductMedia.fixture(File(base, "unused-media")) { error("No image expected") })
        val recovery = RecoveryAuthority(identity, catalog, AndroidRecoveryBackend(owner))
        try {
            identity.bootstrap(OwnerRegistration("synthetic-owner", "owner@example.invalid", "Synthetic", "Owner", "Password-123", "Question", "Answer"))
            identity.login(Credentials("synthetic-owner", "Password-123"))
            catalog.save(ProductDraft(name = "Must survive denied reset", category = "Synthetic", price = BigDecimal("3.25")))
            val generation = owner.generation.value

            repeat(5) {
                try { recovery.reset("Wrong-Password", confirmed = true); fail("Incorrect Owner password reset the store") }
                catch (failure: SecurityFailure) { assertEquals(SecurityError.INVALID_CREDENTIALS, failure.code) }
                assertEquals(generation, owner.generation.value)
                assertEquals("Must survive denied reset", catalog.list(CatalogFilter(limit = 5)).items.single().name)
            }
            try { recovery.reset("Password-123", confirmed = true); fail("Lockout did not block reset") }
            catch (failure: SecurityFailure) { assertEquals(SecurityError.INVALID_CREDENTIALS, failure.code) }
            assertEquals(generation, owner.generation.value)

            now += 15 * 60_000L + 1
            recovery.reset("Password-123", confirmed = true)
            assertNotEquals(generation, owner.generation.value)
            assertTrue(identity.needsOwner())
            assertNull(identity.current())
            assertEquals(1, (File(base, "backups").listFiles() ?: emptyArray()).count {
                it.name.startsWith("manual-") && it.name.endsWith(".storebackup")
            })
            assertEquals(com.vibe.store.application.persistence.IntegrityResult(true, true, true), owner.verifyIntegrity())
        } finally {
            runCatching { owner.close() }
            base.deleteRecursively()
        }
    }

    @Test fun accountSwitchWaitsForAuthorizedPreviewAndRevokesRestoreBeforeConfirmation() = runBlocking {
        val base = File(context.noBackupFilesDir, "i12-session-preview-${UUID.randomUUID()}")
        val owner = ActiveStoreOwner(context, base)
        val passwords = BcryptPasswords()
        val identity = IdentityAuthority(owner, CommandCoordinator(owner), passwords)
        val catalog = CatalogAuthority(identity, AndroidProductMedia.fixture(File(base, "unused-media")) { error("No image expected") })
        val recovery = RecoveryAuthority(identity, catalog, AndroidRecoveryBackend(owner))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            identity.bootstrap(OwnerRegistration("synthetic-owner", "owner@example.invalid", "Synthetic", "Owner", "Password-123", "Question", "Answer"))
            identity.login(Credentials("synthetic-owner", "Password-123"))
            catalog.save(ProductDraft(name = "Session-bound archive", category = "Synthetic", price = BigDecimal("1.00")))
            owner.transaction {
                security.createManaged(NewManagedAccount("synthetic-employee", null, "Synthetic", "Employee", "SE", "employee",
                    passwords.hash("Employee-123"), null, null, true, null, "2026-10-10T00:00:00Z"))
            }
            val bytes = ByteArrayOutputStream()
            recovery.createBackup(object : RecoveryTarget { override fun openOutputStream() = bytes })
            val archive = bytes.toByteArray()
            val generation = owner.generation.value
            val previewJob = async(Dispatchers.IO) {
                recovery.inspect(object : RecoverySource {
                    override fun openInputStream(): InputStream {
                        entered.countDown()
                        check(release.await(10, TimeUnit.SECONDS)) { "Synthetic preview release timed out" }
                        return ByteArrayInputStream(archive)
                    }
                })
            }
            assertTrue("Preview did not enter its provider stream", entered.await(10, TimeUnit.SECONDS))
            val switchStarted = CountDownLatch(1)
            val switching = async(Dispatchers.IO) {
                switchStarted.countDown()
                identity.switchUser(Credentials("synthetic-employee", "Employee-123"), confirmDiscard = true)
            }
            // The switch must wait behind the preview's shared identity gate.
            assertTrue("Account switch coroutine did not start", switchStarted.await(5, TimeUnit.SECONDS))
            assertFalse("Account switch must wait for authorized preview", switching.isCompleted)
            release.countDown()
            assertTrue(previewJob.await().entries.any { it.name == "database/store.db" })
            assertEquals("employee", switching.await().role)
            try {
                recovery.restore(object : RecoverySource { override fun openInputStream() = ByteArrayInputStream(archive) }, confirmed = true)
                fail("A previewed archive remained restorable after account switch")
            } catch (failure: SecurityFailure) { assertEquals(SecurityError.FORBIDDEN, failure.code) }
            assertEquals(generation, owner.generation.value)
            assertEquals("Session-bound archive", catalog.list(CatalogFilter(limit = 10)).items.single().name)
            assertEquals(com.vibe.store.application.persistence.IntegrityResult(true, true, true), owner.verifyIntegrity())
        } finally {
            release.countDown()
            runCatching { owner.close() }
            base.deleteRecursively()
        }
    }
}
