package com.vibe.store

import android.app.Application
import com.vibe.store.api.IdentityService
import com.vibe.store.application.security.IdentityAuthority
import com.vibe.store.infrastructure.persistence.AndroidPersistence
import com.vibe.store.infrastructure.security.BcryptPasswords

class StoreApplication : Application() {
    // Lazy: I01 informational foundation does not open the database. One owner
    // for the process, never Activity/ViewModel-owned and never saved/restored.
    val identity: IdentityService by lazy {
        val persistence = AndroidPersistence.create(this)
        IdentityAuthority(persistence.owner, persistence.commands, BcryptPasswords())
    }
}
