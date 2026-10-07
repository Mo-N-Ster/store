package com.vibe.store

import android.app.Application
import com.vibe.store.infrastructure.media.AndroidProfilePhotoMedia
import com.vibe.store.application.team.TeamAuthority
import com.vibe.store.api.TeamService
import com.vibe.store.api.IdentityService
import com.vibe.store.api.CatalogService
import com.vibe.store.application.catalog.CatalogAuthority
import com.vibe.store.infrastructure.media.AndroidProductMedia
import com.vibe.store.application.security.IdentityAuthority
import com.vibe.store.infrastructure.persistence.AndroidPersistence
import com.vibe.store.infrastructure.security.BcryptPasswords

class StoreApplication : Application() {
    @Volatile var unsavedSale: Boolean = false
    private var saleAuthority: com.vibe.store.application.sales.SaleAuthority? = null
    private var purchaseAuthority: com.vibe.store.application.purchases.PurchaseAuthority? = null
    private var inventoryAuthority: com.vibe.store.application.inventory.InventoryAuthority? = null
    // Lazy: I01 informational foundation does not open the database. One owner
    // for the process, never Activity/ViewModel-owned and never saved/restored.
    private val persistence by lazy { AndroidPersistence.create(this) }
    private val authority by lazy {
        IdentityAuthority(persistence.owner, persistence.commands, BcryptPasswords(),
            com.vibe.store.application.security.OperationGuards(
                critical = { saleAuthority?.critical == true || purchaseAuthority?.critical == true || inventoryAuthority?.critical == true }, unsavedCart = { unsavedSale }))
    }
    val identity: IdentityService get() = authority
    val sales: com.vibe.store.api.SaleService by lazy {
        com.vibe.store.application.sales.SaleAuthority(authority).also { saleAuthority = it }
    }
    val catalog: CatalogService by lazy { CatalogAuthority(authority, AndroidProductMedia.create(this)) }
    val suppliers: com.vibe.store.api.SupplierService by lazy { com.vibe.store.application.purchases.SupplierAuthority(authority) }
    val purchases: com.vibe.store.api.PurchaseService by lazy {
        com.vibe.store.application.purchases.PurchaseAuthority(authority, catalog).also { purchaseAuthority = it }
    }
    val inventories: com.vibe.store.api.InventoryService by lazy {
        com.vibe.store.application.inventory.InventoryAuthority(authority).also { inventoryAuthority = it }
    }
    val team: TeamService by lazy {
        TeamAuthority(authority, AndroidProfilePhotoMedia.create(this))
    }
}
