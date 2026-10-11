package com.vibe.store.infrastructure.persistence

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import com.vibe.store.api.*
import com.vibe.store.application.catalog.CatalogAuthority
import com.vibe.store.application.persistence.CommandCoordinator
import com.vibe.store.application.recovery.RecoveryAuthority
import com.vibe.store.application.security.IdentityAuthority
import com.vibe.store.infrastructure.media.AndroidProductMedia
import com.vibe.store.infrastructure.security.BcryptPasswords
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking

/** Debug-only fixture service for the production RecoveryAuthority/Room workflow UI test. */
class I12RecoveryUiFixtureService : Service() {
    private var fixture: FixtureBinder? = null

    override fun onBind(intent: Intent?): IBinder {
        fixture?.let { return it }
        val id = intent?.getStringExtra(EXTRA_ID) ?: error("Missing synthetic fixture id")
        require(id.matches(Regex("[A-Za-z0-9-]{1,48}")))
        val root = File(File(noBackupFilesDir, "i12-ui-fixtures"), id).apply { check(mkdirs()) }
        val owner = ActiveStoreOwner(this, root)
        val passwords = BcryptPasswords()
        val identity = IdentityAuthority(owner, CommandCoordinator(owner), passwords)
        val catalog = CatalogAuthority(identity, AndroidProductMedia.fixture(File(root, "unused-media")) { error("No image expected") })
        val recovery = RecoveryAuthority(identity, catalog, AndroidRecoveryBackend(owner))
        runBlocking {
            owner.verifyIntegrity()
            identity.bootstrap(OwnerRegistration("synthetic-owner", "owner@example.invalid", "Synthetic", "Owner",
                "Password-123", "Question", "Answer"))
            identity.login(Credentials("synthetic-owner", "Password-123"))
            catalog.save(ProductDraft(name = "Archived synthetic product", category = "Synthetic", price = java.math.BigDecimal("1.2300")))
        }
        return FixtureBinder(root, owner, identity, catalog, recovery).also { fixture = it }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        fixture?.closeFixture()
        fixture = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        fixture?.closeFixture()
        fixture = null
        super.onDestroy()
    }

    inner class FixtureBinder internal constructor(
        private val root: File,
        private val owner: ActiveStoreOwner,
        private val identity: IdentityAuthority,
        private val catalog: CatalogAuthority,
        val recoveryService: RecoveryService,
    ) : Binder() {
        fun addCurrentOnlyProduct() = runBlocking {
            catalog.save(ProductDraft(name = "Current-only synthetic product", category = "Synthetic", price = java.math.BigDecimal("9.99")))
        }

        fun currentProductNames(): List<String> = runBlocking {
            if (identity.current() == null) identity.login(Credentials("synthetic-owner", "Password-123"))
            catalog.list(CatalogFilter(limit = 20)).items.map { it.name }
        }

        fun closeFixture() {
            runCatching { runBlocking { owner.close() } }
            root.deleteRecursively()
        }
    }

    companion object { const val EXTRA_ID = "i12.fixture.id" }
}
