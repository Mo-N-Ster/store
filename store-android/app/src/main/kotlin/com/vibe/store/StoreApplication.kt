package com.vibe.store

import android.app.Application
import com.vibe.store.api.IdentityService
import com.vibe.store.api.CatalogService
import com.vibe.store.application.catalog.CatalogAuthority
import com.vibe.store.infrastructure.media.AndroidProductMedia
import com.vibe.store.application.security.IdentityAuthority
import com.vibe.store.infrastructure.persistence.AndroidPersistence
import com.vibe.store.infrastructure.security.BcryptPasswords

class StoreApplication : Application() {
    // Lazy: I01 informational foundation does not open the database. One owner
    // for the process, never Activity/ViewModel-owned and never saved/restored.
    private val persistence by lazy { AndroidPersistence.create(this) }
    private val authority by lazy {
        IdentityAuthority(persistence.owner, persistence.commands, BcryptPasswords())
    }
    val identity: IdentityService get() = authority
    val catalog: CatalogService by lazy { CatalogAuthority(authority, AndroidProductMedia.create(this)) }
}
