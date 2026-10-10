package com.vibe.store.infrastructure.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vibe.store.api.*
import com.vibe.store.application.catalog.CatalogImportAuthority
import com.vibe.store.application.persistence.CatalogRepository
import com.vibe.store.application.persistence.CommandCoordinator
import com.vibe.store.application.persistence.TransactionRepositories
import com.vibe.store.application.persistence.UnitOfWork
import com.vibe.store.application.security.IdentityAuthority
import com.vibe.store.infrastructure.media.AndroidProductMedia
import com.vibe.store.infrastructure.security.BcryptPasswords
import com.vibe.store.domain.StoreDataV1Codec
import com.vibe.store.domain.StructuredCatalogPayload
import com.vibe.store.domain.StructuredCatalogProduct
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import java.util.UUID

class CatalogImportNativeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private inner class Fixture {
        val root = File(context.noBackupFilesDir, "i10-import/${UUID.randomUUID()}")
        val owner = RoomDatabaseOwner(context, File(root, "database"))
        val commands = CommandCoordinator(owner)
        val passwords = BcryptPasswords()
        val identity = IdentityAuthority(owner, commands, passwords)
        val media = AndroidProductMedia.fixture(File(root, "media")) { error("Import must not touch media") }
        val catalog = com.vibe.store.application.catalog.CatalogAuthority(identity, media)
        val importer = CatalogImportAuthority(identity) { 1_798_000_000_000L }
        suspend fun initialize() {
            identity.bootstrap(OwnerRegistration("owner", "owner@example.invalid", "Synthetic", "Owner", "Password-123", "Question", "Answer"))
            identity.login(Credentials("owner", "Password-123"))
        }
    }
    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val f = Fixture()
        try { f.initialize(); block(f) } finally { f.owner.close() }
    }
    private fun product(name: String, price: String, stock: Long = 0, minimum: Long = 0, hashtag: String = "") =
        CatalogImportProduct(name, hashtag, "Synthetic", "Synthetic only", BigDecimal(price), stock, minimum)
    private fun request(vararg values: CatalogImportProduct) = CatalogImportRequest(values.toList())

    @Test fun authorizedImportWritesExactPricesAndInitialStockHistoryAtomically() = runBlocking { fixture { f ->
        val preview = f.importer.preview(request(
            product("Exact A", "12.3456789", stock = 3, minimum = 1, hashtag = "A"),
            product("Exact B", "0.00000005", stock = 0, minimum = 2, hashtag = "B"),
        ))
        assertTrue(preview.canImport)
        assertFalse(preview.authenticityVerified)
        assertEquals("EUR", preview.currency)
        assertEquals(0, preview.products.first().price.compareTo(BigDecimal("12.3456789")))
        assertEquals(2, f.importer.confirm(checkNotNull(preview.previewId)).importedCount)

        val rows = f.catalog.list(CatalogFilter(limit = 10)).items.associateBy { it.name }
        assertEquals(setOf("Exact A", "Exact B"), rows.keys)
        assertEquals(0, rows.getValue("Exact A").price.compareTo(BigDecimal("12.3456789")))
        assertEquals(3L, rows.getValue("Exact A").stock)
        assertEquals(1, f.catalog.prices(rows.getValue("Exact A").id).size)
        assertEquals(1, f.catalog.prices(rows.getValue("Exact B").id).size)
        assertEquals(listOf(3L), f.catalog.movements().map { it.quantity })
        val lowStock = f.owner.read { (this as RoomRepositories).dao.supportAlerts(100, 0).filter { it.type == "LOW_STOCK" } }
        assertEquals(1, lowStock.size)
        assertEquals(rows.getValue("Exact B").id, lowStock.single().productId)
        f.owner.checkpointCloseReopen()
        assertEquals(0, f.catalog.detail(rows.getValue("Exact A").id).price.compareTo(BigDecimal("12.3456789")))
    } }

    @Test fun invalidAndExistingDuplicatesHaveNoPartialEffects() = runBlocking { fixture { f ->
        f.catalog.save(ProductDraft(name = "Existing", category = "Synthetic", price = BigDecimal("1")))
        val duplicate = f.importer.preview(request(product(" existing ", "2", hashtag = "new"), product("Other", "3", hashtag = "new")))
        assertFalse(duplicate.canImport)
        assertTrue(duplicate.issues.any { it.code == CatalogImportIssueCode.ALREADY_EXISTS })
        assertTrue(duplicate.issues.any { it.code == CatalogImportIssueCode.DUPLICATE_IN_FILE })
        assertEquals(1, f.catalog.list(CatalogFilter(limit = 20)).items.size)
        assertEquals(0, f.catalog.movements().size)
        assertEquals(1, f.catalog.prices(f.catalog.list().items.single().id).size)

        val invalid = f.importer.preview(request(product("Valid", "4", stock = 2), product(" ", "-0.1")))
        assertFalse(invalid.canImport)
        assertNull(invalid.previewId)
        assertEquals(1, f.catalog.list(CatalogFilter(limit = 20)).items.size)
        assertEquals(0, f.catalog.movements().size)
    } }

    @Test fun accountSwitchRechecksCurrentRightsAndRejectsOldPreview() = runBlocking { fixture { f ->
        val preview = f.importer.preview(request(product("Private preview", "1")))
        f.commands.execute {
            val dao = (this as RoomRepositories).dao
            dao.user(UserEntity(2, "employee", f.passwords.hash("Password-123"), "employee", "Synthetic", "Employee", "SE", STAMP))
            dao.userRole(UserRoleEntity(2, dao.roles().single { it.code == "employee" }.id, STAMP))
        }
        f.identity.switchUser(Credentials("employee", "Password-123"))
        try { f.importer.confirm(checkNotNull(preview.previewId)); fail("Old preview accepted after account switch") }
        catch (failure: SecurityFailure) { assertEquals(SecurityError.FORBIDDEN, failure.code) }
        assertTrue(f.catalog.list(CatalogFilter(limit = 20)).items.isEmpty())
    } }

    @Test fun pdfPreviewUsesCurrentRightsAndDoesNotMutateUntilExplicitConfirmation() = runBlocking { fixture { f ->
        val encoded = StoreDataV1Codec.encode(StructuredCatalogPayload(listOf(
            StructuredCatalogProduct("PDF synthetic", "#pdf", "Synthetic", "untrusted", BigDecimal("12.3456789"), 2, 1),
        )))
        val calls = AtomicInteger()
        val importer = CatalogImportAuthority(f.identity, { 1_798_000_000_000L }, PdfSubjectExtractor {
            calls.incrementAndGet()
            encoded.toByteArray(StandardCharsets.UTF_16LE)
        })
        val preview = importer.previewPdf(BoundedDocumentInput(byteArrayOf(1, 2, 3)))
        assertEquals(1, calls.get())
        assertTrue(preview.canImport)
        assertFalse(preview.authenticityVerified)
        assertEquals(0, preview.products.single().price.compareTo(BigDecimal("12.3456789")))
        assertTrue(f.catalog.list(CatalogFilter(limit = 10)).items.isEmpty())
        assertTrue(f.catalog.movements().isEmpty())
        assertEquals(1, importer.confirm(checkNotNull(preview.previewId)).importedCount)
        val imported = f.catalog.list(CatalogFilter(limit = 10)).items.single()
        assertEquals(0, imported.price.compareTo(BigDecimal("12.3456789")))
        assertEquals(2L, imported.stock)
        assertEquals(1, f.catalog.prices(imported.id).size)
        assertEquals(1, f.catalog.movements().size)
    } }

    @Test fun syntheticLegacyPdfTraversesIsolatedParserCodecPreviewAndAtomicImport() = runBlocking { fixture { f ->
        val pdf = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
            .open("synthetic-2.0.1-envelope.pdf").use { it.readBytes() }
        val importer = CatalogImportAuthority(
            f.identity,
            { 1_798_000_000_000L },
            com.vibe.store.infrastructure.documents.parser.AndroidPdfSubjectExtractor(context),
        )

        val preview = importer.previewPdf(BoundedDocumentInput(pdf))
        assertTrue(preview.canImport)
        assertFalse(preview.authenticityVerified)
        assertEquals(3, preview.products.size)
        assertEquals(0, preview.products.first().price.compareTo(BigDecimal("1250.25")))
        assertTrue(f.catalog.list(CatalogFilter(limit = 20)).items.isEmpty())
        assertTrue(f.catalog.movements().isEmpty())

        assertEquals(3, importer.confirm(checkNotNull(preview.previewId)).importedCount)
        val products = f.catalog.list(CatalogFilter(limit = 20)).items.associateBy { it.name }
        assertEquals(setOf("Café torréfié", "Piment séché", "Thé vert"), products.keys)
        assertEquals(21L, products.getValue("Café torréfié").stock)
        assertEquals(0, products.getValue("Café torréfié").price.compareTo(BigDecimal("1250.25")))
        assertEquals(3, products.values.sumOf { f.catalog.prices(it.id).size })
        assertEquals(2, f.catalog.movements().size)
        assertTrue(f.owner.verifyIntegrity().let { it.quick && it.full && it.foreignKeys })

        val replay = importer.previewPdf(BoundedDocumentInput(pdf))
        assertFalse(replay.canImport)
        assertTrue(replay.issues.all { it.code == CatalogImportIssueCode.ALREADY_EXISTS })
        assertEquals(3, f.catalog.list(CatalogFilter(limit = 20)).items.size)
        assertEquals(2, f.catalog.movements().size)
    } }

    @Test fun cancellingPdfPreviewDiscardsItWithoutAnyBusinessMutation() = runBlocking { fixture { f ->
        val pdf = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
            .open("synthetic-2.0.1-envelope.pdf").use { it.readBytes() }
        val importer = CatalogImportAuthority(
            f.identity,
            { 1_798_000_000_000L },
            com.vibe.store.infrastructure.documents.parser.AndroidPdfSubjectExtractor(context),
        )
        val preview = importer.previewPdf(BoundedDocumentInput(pdf))
        val id = checkNotNull(preview.previewId)
        importer.discard(id)
        try { importer.confirm(id); fail("Cancelled preview must not be importable") }
        catch (failure: CatalogImportFailure) { assertEquals(CatalogImportFailureCode.STALE_PREVIEW, failure.code) }
        assertTrue(f.catalog.list(CatalogFilter(limit = 20)).items.isEmpty())
        assertTrue(f.catalog.movements().isEmpty())
        assertTrue(f.owner.read { (this as RoomRepositories).dao.productPrices(1L).isEmpty() })
    } }

    @Test fun rightsAreCheckedBeforeParserAndSessionChangeDuringParsingRejectsPreview() = runBlocking { fixture { f ->
        val encoded = StoreDataV1Codec.encode(StructuredCatalogPayload(listOf(
            StructuredCatalogProduct("Untrusted", "", "Synthetic", "", BigDecimal.ONE, 0, 0),
        ))).toByteArray(StandardCharsets.UTF_16LE)
        val calls = AtomicInteger()
        val deniedImporter = CatalogImportAuthority(f.identity, System::currentTimeMillis, PdfSubjectExtractor {
            calls.incrementAndGet(); encoded
        })
        f.commands.execute {
            val dao = (this as RoomRepositories).dao
            dao.user(UserEntity(2, "employee", f.passwords.hash("Password-123"), "employee", "Synthetic", "Employee", "SE", STAMP))
            dao.userRole(UserRoleEntity(2, dao.roles().single { it.code == "employee" }.id, STAMP))
        }
        f.identity.switchUser(Credentials("employee", "Password-123"))
        try { deniedImporter.previewPdf(BoundedDocumentInput(byteArrayOf(1))); fail("Parser must not run without update rights") }
        catch (failure: SecurityFailure) { assertEquals(SecurityError.FORBIDDEN, failure.code) }
        assertEquals(0, calls.get())

        f.identity.logout()
        f.identity.login(Credentials("owner", "Password-123"))
        val changingSession = CatalogImportAuthority(f.identity, System::currentTimeMillis, PdfSubjectExtractor {
            f.identity.logout()
            f.identity.login(Credentials("owner", "Password-123"))
            encoded
        })
        try { changingSession.previewPdf(BoundedDocumentInput(byteArrayOf(1))); fail("Changed session must invalidate parsed preview") }
        catch (failure: CatalogImportFailure) { assertEquals(CatalogImportFailureCode.STALE_PREVIEW, failure.code) }
        assertTrue(f.catalog.list(CatalogFilter(limit = 10)).items.isEmpty())
        assertTrue(f.catalog.movements().isEmpty())
    } }

    @Test fun partialRepositoryFailureRollsBackProductsStockHistoryAndAlerts() = runBlocking { fixture { f ->
        val failingWork = object : UnitOfWork {
            override suspend fun <T> transaction(block: suspend TransactionRepositories.() -> T): T = f.owner.transaction {
                val real = this
                val scope = object : TransactionRepositories by real {
                    override val catalog: CatalogRepository = object : CatalogRepository by real.catalog {
                        override suspend fun insertImported(products: List<com.vibe.store.domain.CatalogRecord>) {
                            real.catalog.insertImported(products.take(1))
                            throw IOException("Synthetic mid-import failure")
                        }
                    }
                }
                block(scope)
            }
        }
        val identity = IdentityAuthority(f.owner, CommandCoordinator(failingWork), f.passwords)
        identity.login(Credentials("owner", "Password-123"))
        val importer = CatalogImportAuthority(identity)
        val preview = importer.preview(request(product("Rollback A", "1", stock = 4, minimum = 1), product("Rollback B", "2", minimum = 2)))
        try { importer.confirm(checkNotNull(preview.previewId)); fail("Expected transactional failure") }
        catch (failure: IOException) { assertEquals("Synthetic mid-import failure", failure.message) }

        assertTrue(f.catalog.list(CatalogFilter(limit = 20)).items.isEmpty())
        assertTrue(f.catalog.movements().isEmpty())
        assertTrue(f.owner.read { (this as RoomRepositories).dao.supportAlerts(100, 0).isEmpty() })
        f.owner.read { assertTrue((this as RoomRepositories).dao.productPrices(1L).isEmpty()) }
        assertTrue(f.owner.verifyIntegrity().let { it.quick && it.full && it.foreignKeys })
    } }
}
