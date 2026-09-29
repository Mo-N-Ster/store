package com.vibe.store.infrastructure
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.vibe.store.application.FoundationEnvironment

/** Wiring only: no Room database class, entity, DAO, path, open or migration in I01. */
class RoomDriverWiring : FoundationEnvironment {
    private val driver = BundledSQLiteDriver()
    fun <T : RoomDatabase> configure(builder: RoomDatabase.Builder<T>): RoomDatabase.Builder<T> =
        builder.setDriver(driver)
    override fun storageAdapterName() = "Room / BundledSQLiteDriver"
}
