package com.vibe.store.infrastructure.persistence

import java.math.BigDecimal
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.vibe.store.api.*
import com.vibe.store.application.catalog.*
import com.vibe.store.application.persistence.CommandCoordinator
import com.vibe.store.application.persistence.CatalogRepository
import com.vibe.store.application.persistence.TransactionRepositories
import com.vibe.store.application.persistence.UnitOfWork
import com.vibe.store.application.security.IdentityAuthority
import com.vibe.store.infrastructure.media.AndroidProductMedia
import com.vibe.store.infrastructure.security.BcryptPasswords
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.io.IOException
import java.util.UUID

class CatalogNativeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val png = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 1, 2, 3)
    private val selected get() = AndroidProductMedia.selection(Uri.parse("content://synthetic/image"))
    private inner class Fixture {
        val root = File(context.noBackupFilesDir, "i04/${UUID.randomUUID()}")
        val owner = RoomDatabaseOwner(context, File(root, "database"))
        val commands = CommandCoordinator(owner)
        val passwords = BcryptPasswords()
        val identity = IdentityAuthority(owner, commands, passwords)
        val media = AndroidProductMedia.fixture(File(root, "media")) { png.inputStream() }
        val catalog = CatalogAuthority(identity, media)
        suspend fun initialize() {
            identity.bootstrap(OwnerRegistration("owner", "owner@example.invalid", "Synthetic", "Owner", "Password-123", "Question", "Answer"))
            identity.login(Credentials("owner", "Password-123"))
        }
        fun draft(name: String = "Article", stock: Double = 5.0) = ProductDraft(name = name, category = "Food", price = BigDecimal("12.34"), initialStock = stock)
    }
    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val f = Fixture()
        try { f.initialize(); block(f) } finally { f.owner.close() }
    }
    private suspend fun denied(code: CatalogError, action: suspend () -> Unit) {
        try { action(); fail("Expected $code") } catch (failure: CatalogFailure) { assertEquals(code, failure.code) }
    }
    @Test fun catalogCrudFiltersDuplicatesPriceAndArchiveHistory() = runBlocking { fixture { f ->
        val initial = f.catalog.save(f.draft(), ImageEdit.Replace(selected)).product
        assertTrue(initial.hasImage)
        assertEquals(5L, initial.stock)
        assertEquals(1, f.catalog.prices(initial.id).size)
        denied(CatalogError.DUPLICATE) { f.catalog.save(f.draft("article")) }
        assertEquals(initial.id, f.catalog.list(CatalogFilter(search = "art", category = "Food")).items.single().id)
        assertTrue(f.catalog.list(CatalogFilter(category = "Other")).items.isEmpty())
        val edited = f.catalog.save(f.draft().copy(id = initial.id, price = BigDecimal("15"), expectedUpdatedAt = initial.updatedAt)).product
        assertEquals(2, f.catalog.prices(initial.id).size)
        assertEquals(5L, edited.stock)
        val adjusted = f.catalog.adjustStock(initial.id, 7.0, "Physical count", 5)
        assertEquals(7L, adjusted.stock)
        val archived = f.catalog.archive(initial.id, adjusted.updatedAt)
        assertTrue(archived.archived); assertEquals(0L, archived.stock)
        assertTrue(f.catalog.list().items.isEmpty())
        assertEquals(1, f.catalog.list(CatalogFilter(archived = true)).items.size)
        assertEquals(listOf(-7L, 2L, 5L), f.catalog.movements().map { it.quantity })
        assertEquals(2, f.catalog.prices(initial.id).size)
        val references = f.owner.read { catalog.imageReferences() }
        f.media.removeUnreferenced(references.single(), references)
        assertNotNull(f.catalog.image(initial.id))
        f.owner.checkpointCloseReopen()
        assertEquals(0, f.catalog.inspectMedia().missing)
        assertTrue(f.owner.verifyIntegrity().let { it.quick && it.full && it.foreignKeys })
    } }
    @Test fun invalidStockHistoryAndEmployeeRequestsHaveZeroMutation() = runBlocking { fixture { f ->
        val p = f.catalog.save(f.draft()).product
        f.commands.execute { (this as RoomRepositories).dao.attendance(AttendanceEntity(1, 1, "2026-09-30T08:00:00Z", "MANUAL", "VALID")) }
        for (target in listOf(-1.0, 1.5, Double.NaN)) denied(CatalogError.INVALID_INPUT) { f.catalog.adjustStock(p.id, target, "Count", p.stock) }
        for (type in listOf(null, "", "personnel", "sales", "unknown", "PURCHASES")) {
            denied(CatalogError.INVALID_INPUT) { f.catalog.deleteHistory(type, listOf(1)) }
            assertEquals(1, f.catalog.movements().size); assertEquals(5L, f.catalog.detail(p.id).stock)
            assertNotNull(f.owner.read { attendance.attendance(1) })
        }
        f.commands.execute {
            val dao = (this as RoomRepositories).dao
            dao.user(UserEntity(2, "employee", f.passwords.hash("Password-123"), "employee", "Synthetic", "Employee", "SE", "2026-09-30"))
            dao.userRole(UserRoleEntity(2, dao.roles().single { it.code == "employee" }.id, "2026-09-30"))
        }
        f.identity.switchUser(Credentials("employee", "Password-123"))
        val operations: List<suspend () -> Unit> = listOf(
            { f.catalog.save(f.draft("Forged")); Unit },
            { f.catalog.adjustStock(p.id, 8.0, "Forged count", 5); Unit },
            { f.catalog.archive(p.id, p.updatedAt); Unit },
            { f.catalog.deleteHistory("purchases", listOf(1)); Unit },
        )
        for (operation in operations) {
            try { operation(); fail("Employee mutation accepted") } catch (failure: SecurityFailure) { assertEquals(SecurityError.FORBIDDEN, failure.code) }
            assertEquals(5L, f.catalog.detail(p.id).stock); assertEquals(1, f.catalog.movements().size)
        }
        f.identity.switchUser(Credentials("owner", "Password-123"))
        assertEquals(1, f.catalog.deleteHistory("purchases", listOf(1, 1)))
        assertNotNull(f.owner.read { attendance.attendance(1) })
    } }
    @Test fun stockAndMovementRollbackTogetherOnPersistenceFailure() = runBlocking { fixture { f ->
        val p = f.catalog.save(f.draft()).product
        val failingWork = object : UnitOfWork {
            override suspend fun <T> transaction(block: suspend TransactionRepositories.() -> T): T = f.owner.transaction {
                val real = this
                val scope = object : TransactionRepositories by real {
                    override val catalog = object : CatalogRepository by real.catalog {
                        override suspend fun movement(productId: Long, delta: Long, reason: String, price: BigDecimal, stamp: String, reference: String?) {
                            throw IOException("Synthetic movement write failure")
                        }
                    }
                }
                block(scope)
            }
        }
        val identity = IdentityAuthority(f.owner, CommandCoordinator(failingWork), f.passwords)
        identity.login(Credentials("owner", "Password-123"))
        try { CatalogAuthority(identity, f.media).adjustStock(p.id, 9.0, "Count", p.stock); fail("Expected rollback") } catch (_: IOException) { }
        assertEquals(p, f.catalog.detail(p.id)); assertEquals(1, f.catalog.movements().size)
        assertEquals(1, f.catalog.prices(p.id).size)
    } }
    @Test fun rollbackBeforeReferenceAndPostCommitCleanupAreSafe() = runBlocking { fixture { f ->
        val failing = CatalogAuthority(f.identity, f.media, probe = CatalogProbe {
            if (it == CatalogBoundary.BEFORE_REFERENCE) throw IOException("Synthetic transaction failure")
        })
        try { failing.save(f.draft(), ImageEdit.Replace(selected)); fail("Expected failure") } catch (_: IOException) { }
        assertTrue(f.catalog.list().items.isEmpty()); assertEquals(0, f.catalog.inspectMedia().missing)
        assertEquals(1, f.catalog.inspectMedia().orphaned)
        val p = f.catalog.save(f.draft(), ImageEdit.Replace(selected)).product
        val refusingCleanup = object : ProductMedia by f.media {
            override suspend fun removeUnreferenced(reference: String, references: Set<String>) { throw CatalogFailure(CatalogError.STORAGE_UNAVAILABLE) }
        }
        val result = CatalogAuthority(f.identity, refusingCleanup).save(f.draft().copy(id = p.id, expectedUpdatedAt = p.updatedAt), ImageEdit.Replace(selected))
        assertTrue(result.cleanupDeferred); assertNotNull(f.catalog.image(p.id))
        f.owner.checkpointCloseReopen(); assertEquals(0, f.catalog.inspectMedia().missing)
    } }
    @Test fun mediaFormatsCancellationInvalidOversizedRevokedAndPartialCopy() = runBlocking { fixture { f ->
        assertNull(f.media.prepare(null)); assertFalse(File(f.root, "media").exists())
        for (bytes in listOf(png, byteArrayOf(-1, -40, -1), "RIFF0000WEBP".toByteArray())) {
            val media = AndroidProductMedia.fixture(File(f.root, "formats")) { bytes.inputStream() }
            val prepared = media.prepare(selected)!!
            assertArrayEquals(bytes, media.read(prepared.reference).bytes)
        }
        val invalid = AndroidProductMedia.fixture(File(f.root, "invalid")) { "fake.jpg".byteInputStream() }
        denied(CatalogError.INVALID_MEDIA) { invalid.prepare(selected) }
        val oversized = AndroidProductMedia.fixture(File(f.root, "large")) { ByteArray(5 * 1024 * 1024 + 1).inputStream() }
        denied(CatalogError.MEDIA_TOO_LARGE) { oversized.prepare(selected) }
        val revoked = AndroidProductMedia.fixture(File(f.root, "revoked")) { throw SecurityException("Synthetic revoked grant") }
        denied(CatalogError.MEDIA_UNAVAILABLE) { revoked.prepare(selected) }
        val partial = AndroidProductMedia.fixture(File(f.root, "partial")) {
            object : InputStream() {
                var reads = 0
                override fun read(): Int { if (reads++ < 3) return 255; throw IOException("Synthetic interrupted copy") }
            }
        }
        denied(CatalogError.STORAGE_UNAVAILABLE) { partial.prepare(selected) }
        val fullDisk = AndroidProductMedia.fixture(File(f.root, "full"), beforeWrite = { throw android.system.ErrnoException("Synthetic write", android.system.OsConstants.ENOSPC) }) { png.inputStream() }
        denied(CatalogError.STORAGE_UNAVAILABLE) { fullDisk.prepare(selected) }
        val profile = AndroidProductMedia.fixture(File(f.root, "profile")) { ByteArray(512 * 1024 + 1).inputStream() }
        denied(CatalogError.MEDIA_TOO_LARGE) { profile.prepare(selected, com.vibe.store.domain.MediaPolicy.PROFILE) }
        assertTrue(f.owner.read { catalog.imageReferences().isEmpty() })
    } }
}
