package com.vibe.store.infrastructure.persistence

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers

internal const val STAMP = "2026-01-02T03:04:05.000Z"
internal const val FIXTURE_WRITES = 30

/** Only test data; never run from the application or by an upgrade callback. */
internal suspend fun StoreDao.fixture(failAfter: Int = Int.MAX_VALUE) {
    var writes = 0
    fun boundary() { if (++writes == failAfter) error("injected boundary $writes") }
    user(UserEntity(1, "synthetic", "NOT-A-REAL-HASH", "owner", "Test", "Only", "TO", STAMP)); boundary()
    role(RoleEntity(1, "owner", "Owner", true, STAMP)); boundary()
    permission(PermissionEntity(1, "catalog", "read", STAMP)); boundary()
    rolePermission(RolePermissionEntity(1, 1)); boundary()
    userRole(UserRoleEntity(1, 1, STAMP)); boundary()
    denial(DenialEntity(1, 1)); boundary()
    employee(EmployeeEntity(1, "SYN-1", 1, "Test", "Only", "ACTIVE", STAMP, STAMP)); boundary()
    cash(CashEntity(1, "SYN-CASH", 1, "OPEN", 20.0, STAMP)); boundary()
    product(ProductEntity(1, "Synthetic", "Test", 12.5, 8, 2, STAMP, STAMP)); boundary()
    price(PriceEntity(1, 1, 12.5, STAMP)); boundary()
    movement(MovementEntity(1, 1, -2, "sale", 12.5, STAMP, "SYN-INV")); boundary()
    invoice(InvoiceEntity("SYN-INV", 1, 1, STAMP, 25.0, 25.0, 0.0, "validated", "SYN-KEY", 1, "synthetic-command", storeName = "Historical store", currency = "XAF")); boundary()
    invoiceLine(InvoiceLineEntity(1, "SYN-INV", 1, "Historical product", "Historical category", 2, 12.5, 25.0, 9.0)); boundary()
    payment(PaymentEntity(1, "SYN-INV", 1, "CASH", 25.0, 30.0, 5.0, "CAPTURED", STAMP)); boundary()
    supplier(SupplierEntity(1, "Synthetic supplier", true, STAMP, STAMP)); boundary()
    purchase(PurchaseEntity(1, "SYN-PUR", 1, "DRAFT", 9.0, 1, STAMP)); boundary()
    purchaseLine(PurchaseLineEntity(1, 1, 1, 1, 9.0, 9.0)); boundary()
    inventory(InventoryEntity(1, "SYN-COUNT", "DRAFT", 1, STAMP)); boundary()
    inventoryLine(InventoryLineEntity(1, 1, 1, 8, 8)); boundary()
    attendance(AttendanceEntity(1, 1, STAMP, "EXPLICIT", "VALID", sessionRef = "SYN-ATT")); boundary()
    message(MessageEntity(1, 1, "user", 1, "Synthetic", "Test only", "message", false, STAMP, "SYN-MSG")); boundary()
    messageRead(MessageReadEntity(1, 1, STAMP)); boundary()
    messageDeletion(MessageDeletionEntity(1, 1, STAMP)); boundary()
    notification(NotificationEntity(1, "stock", 1, "Test only", false, STAMP)); boundary()
    email(EmailEntity(1, "nobody@example.invalid", "Test", "synthetic.pdf", "Test only", byteArrayOf(1, 2), "pending", 0, STAMP)); boundary()
    setting(SettingEntity("synthetic", "v1")); boundary()
    audit(AuditEntity(1, 1, "SYNTHETIC", "test", "1", null, "SUCCESS", STAMP, 1, "Historical responsible", "SYN-CASH", 45.0, "XAF")); boundary()
    generation(GenerationEntity("main", STAMP)); boundary()
    pending(PendingCommandEntity("SYN-PENDING", "SYN-KEY", 1, 1, 1, "synthetic-command", "SUBMITTED", STAMP)); boundary()
    stock(1, 6); boundary()
    check(writes == FIXTURE_WRITES)
}

@Entity(tableName = "synthetic_migration_marker")
internal data class MigrationMarker(@PrimaryKey val id: Long)
@Database(entities = [UserEntity::class, RoleEntity::class, PermissionEntity::class,
    RolePermissionEntity::class, UserRoleEntity::class, DenialEntity::class, EmployeeEntity::class,
    CashEntity::class, ProductEntity::class, PriceEntity::class, MovementEntity::class,
    InvoiceEntity::class, InvoiceLineEntity::class, PaymentEntity::class, SupplierEntity::class,
    PurchaseEntity::class, PurchaseLineEntity::class, InventoryEntity::class, InventoryLineEntity::class,
    AttendanceEntity::class, MessageEntity::class, MessageReadEntity::class, MessageDeletionEntity::class,
    NotificationEntity::class, EmailEntity::class, SettingEntity::class, AuditEntity::class,
    GenerationEntity::class, PendingCommandEntity::class, MigrationMarker::class], version = 2, exportSchema = true)
internal abstract class SyntheticV2 : StoreDatabase()

internal fun syntheticV2(context: Context, file: File): StoreDatabase =
    Room.databaseBuilder(context, SyntheticV2::class.java, file.absolutePath).setDriver(ConfiguredDriver())
        .setQueryCoroutineContext(Dispatchers.IO).setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .addCallback(configuration).addMigrations(object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                check(File(context.noBackupFilesDir, "maintenance/${file.parentFile!!.name}/pre-migration.db").isFile) { "Missing pre-migration snapshot" }
                connection.execSQL("CREATE TABLE IF NOT EXISTS synthetic_migration_marker (id INTEGER NOT NULL, PRIMARY KEY(id))")
            }
        }).build()
