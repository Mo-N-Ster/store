package com.vibe.store.infrastructure.persistence

import java.math.BigDecimal
import android.content.Context
import android.net.Uri
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.vibe.store.api.*
import com.vibe.store.application.catalog.*
import com.vibe.store.application.persistence.CommandCoordinator
import com.vibe.store.application.security.IdentityAuthority
import com.vibe.store.infrastructure.media.AndroidProductMedia
import com.vibe.store.infrastructure.security.BcryptPasswords
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

/** Explicit external force-stop phases; never replaces process death with recreation. */
class CatalogRestartTest {
    @Test fun realInterruptionPreservesMediaReferences() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val phase = args.getString("catalogPhase")
        val boundary = args.getString("catalogBoundary")
        val run = args.getString("catalogRun")
        require(phase in setOf("prepare", "verify") && boundary in setOf("before", "after") && run != null && Regex("[a-z0-9-]{1,40}").matches(run)) {
            "Explicit catalogPhase, catalogBoundary and unique catalogRun required"
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.noBackupFilesDir, "i04-restart-$run-$boundary")
        if (phase == "prepare") require(!root.exists()) { "Use a new synthetic run identifier; never reset existing data" }
        val owner = RoomDatabaseOwner(context, File(root, "database"))
        val identity = IdentityAuthority(owner, CommandCoordinator(owner), BcryptPasswords())
        val media = AndroidProductMedia.fixture(File(root, "media")) { byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10).inputStream() }
        val marker = File(root, "ready")
        try {
            if (phase == "prepare") {
                identity.bootstrap(OwnerRegistration("owner", "owner@example.invalid", "Synthetic", "Owner", "Password-123", "Question", "Answer"))
                identity.login(Credentials("owner", "Password-123"))
                val authority = CatalogAuthority(identity, media, probe = CatalogProbe {
                    val reached = if (boundary == "before") CatalogBoundary.BEFORE_REFERENCE else CatalogBoundary.AFTER_REFERENCE
                    if (it == reached) {
                        FileOutputStream(marker).use { output -> output.write(Process.myPid().toString().toByteArray()); output.fd.sync() }
                        awaitCancellation() // external adb force-stop, not a simulated exception
                    }
                })
                authority.save(ProductDraft(name = "Restart product", category = "Synthetic", price = BigDecimal("1.0"), initialStock = 3.0),
                    ImageEdit.Replace(AndroidProductMedia.selection(Uri.parse("content://synthetic/restart"))))
                fail("Expected external process termination")
            } else {
                assertTrue(marker.isFile); assertNotEquals(marker.readText().toInt(), Process.myPid())
                assertNull(identity.current())
                identity.login(Credentials("owner", "Password-123"))
                val authority = CatalogAuthority(identity, media)
                val products = authority.list().items
                if (boundary == "before") {
                    assertTrue(products.isEmpty()); assertTrue(authority.movements().isEmpty())
                    assertEquals(1, authority.inspectMedia().orphaned)
                } else {
                    assertEquals(1, products.size); assertEquals(3L, products.single().stock)
                    assertNotNull(authority.image(products.single().id)); assertEquals(1, authority.movements().size)
                    assertEquals(1, authority.prices(products.single().id).size)
                }
                assertEquals(0, authority.inspectMedia().missing)
                assertTrue(owner.verifyIntegrity().let { it.quick && it.full && it.foreignKeys })
            }
        } finally { owner.close() }
    }
}
