package com.vibe.store.infrastructure.persistence

import androidx.room.*
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Dao
internal interface StoreDao {
    @Insert suspend fun user(value: UserEntity)
    @Insert suspend fun role(value: RoleEntity)
    @Insert suspend fun permission(value: PermissionEntity)
    @Insert suspend fun rolePermission(value: RolePermissionEntity)
    @Insert suspend fun userRole(value: UserRoleEntity)
    @Insert suspend fun denial(value: DenialEntity)
    @Insert suspend fun employee(value: EmployeeEntity)
    @Insert suspend fun cash(value: CashEntity)
    @Insert suspend fun product(value: ProductEntity)
    @Insert suspend fun price(value: PriceEntity)
    @Insert suspend fun movement(value: MovementEntity)
    @Insert suspend fun invoice(value: InvoiceEntity)
    @Insert suspend fun invoiceLine(value: InvoiceLineEntity)
    @Insert suspend fun payment(value: PaymentEntity)
    @Insert suspend fun supplier(value: SupplierEntity)
    @Insert suspend fun purchase(value: PurchaseEntity)
    @Insert suspend fun purchaseLine(value: PurchaseLineEntity)
    @Insert suspend fun inventory(value: InventoryEntity)
    @Insert suspend fun inventoryLine(value: InventoryLineEntity)
    @Insert suspend fun attendance(value: AttendanceEntity)
    @Insert suspend fun message(value: MessageEntity)
    @Insert suspend fun messageRead(value: MessageReadEntity)
    @Insert suspend fun messageDeletion(value: MessageDeletionEntity)
    @Insert suspend fun notification(value: NotificationEntity)
    @Insert suspend fun email(value: EmailEntity)
    @Insert suspend fun audit(value: AuditEntity)
    @Insert suspend fun generation(value: GenerationEntity)
    @Insert suspend fun pending(value: PendingCommandEntity)
    @Upsert suspend fun setting(value: SettingEntity)
    @Query("SELECT * FROM users WHERE id=:id") suspend fun account(id: Long): UserEntity?
    @Query("SELECT * FROM cash_sessions WHERE id=:id") suspend fun session(id: Long): CashEntity?
    @Query("SELECT * FROM products WHERE id=:id") suspend fun findProduct(id: Long): ProductEntity?
    @Query("SELECT * FROM invoices WHERE id=:id") suspend fun findInvoice(id: String): InvoiceEntity?
    @Query("SELECT * FROM purchases WHERE id=:id") suspend fun findPurchase(id: Long): PurchaseEntity?
    @Query("SELECT * FROM inventory_counts WHERE id=:id") suspend fun findInventory(id: Long): InventoryEntity?
    @Query("SELECT * FROM attendances WHERE id=:id") suspend fun findAttendance(id: Long): AttendanceEntity?
    @Query("SELECT * FROM messages WHERE id=:id") suspend fun findMessage(id: Long): MessageEntity?
    @Query("SELECT * FROM audit_logs WHERE id=:id") suspend fun findAudit(id: Long): AuditEntity?
    @Query("SELECT value FROM settings WHERE `key`=:key") suspend fun settingValue(key: String): String?
    @Query("UPDATE products SET stock=:stock WHERE id=:id") suspend fun stock(id: Long, stock: Long)
}

@Database(entities = [UserEntity::class, RoleEntity::class, PermissionEntity::class,
    RolePermissionEntity::class, UserRoleEntity::class, DenialEntity::class, EmployeeEntity::class,
    CashEntity::class, ProductEntity::class, PriceEntity::class, MovementEntity::class,
    InvoiceEntity::class, InvoiceLineEntity::class, PaymentEntity::class, SupplierEntity::class,
    PurchaseEntity::class, PurchaseLineEntity::class, InventoryEntity::class, InventoryLineEntity::class,
    AttendanceEntity::class, MessageEntity::class, MessageReadEntity::class, MessageDeletionEntity::class,
    NotificationEntity::class, EmailEntity::class, SettingEntity::class, AuditEntity::class,
    GenerationEntity::class, PendingCommandEntity::class], version = 1, exportSchema = true)
internal abstract class StoreDatabase : RoomDatabase() { abstract fun records(): StoreDao }

internal object StoreConstraints {
    // Room has no CHECK annotation. Fixed internal triggers enforce the same
    // constraints on INSERT and UPDATE; not a renderer-selected table/SQL API.
    val checks = mapOf(
        "users" to "NEW.role IN ('owner','manager','employee') AND NEW.active IN (0,1)",
        "roles" to "NEW.isSystem IN (0,1)",
        "employees" to "NEW.status IN ('ACTIVE','ABSENT','SUSPENDED','RESIGNED','ARCHIVED')",
        "cash_sessions" to "NEW.status IN ('OPEN','CLOSED') AND NEW.openingAmount>=0 AND (NEW.closingAmount IS NULL OR NEW.closingAmount>=0)",
        "products" to "NEW.price>=0 AND typeof(NEW.stock)='integer' AND NEW.stock BETWEEN 0 AND 9007199254740991 AND NEW.minimumStock>=0",
        "product_price_history" to "NEW.price>=0",
        "inventory_counts" to "NEW.status IN ('DRAFT','VALIDATED','CANCELLED')",
        "inventory_count_lines" to "typeof(NEW.expectedQuantity)='integer' AND typeof(NEW.countedQuantity)='integer' AND NEW.expectedQuantity>=0 AND NEW.countedQuantity>=0",
        "suppliers" to "NEW.active IN (0,1)",
        "purchases" to "NEW.status IN ('DRAFT','VALIDATED','CANCELLED') AND NEW.totalAmount>=0",
        "purchase_items" to "typeof(NEW.quantity)='integer' AND NEW.quantity BETWEEN 1 AND 9007199254740991 AND NEW.unitCost>=0 AND NEW.totalLine>=0",
        "invoices" to "NEW.status IN ('validated','cancelled') AND ((NEW.canonicalRequest IS NULL AND NEW.canonicalVersion IS NULL) OR (NEW.canonicalRequest IS NOT NULL AND NEW.canonicalVersion IS NOT NULL AND NEW.canonicalVersion>0))",
        "invoice_lines" to "typeof(NEW.quantity)='integer' AND NEW.quantity BETWEEN 1 AND 9007199254740991",
        "payments" to "NEW.method='CASH' AND NEW.amount>=0 AND NEW.received>=0 AND NEW.change>=0 AND NEW.status IN ('CAPTURED','REFUNDED')",
        "attendances" to "NEW.status IN ('VALID','CORRECTED','INTERRUPTED')"
    )
    fun create(connection: SQLiteConnection) {
        checks.forEach { (table, condition) ->
            for (operation in listOf("INSERT", "UPDATE")) connection.execSQL(
                "CREATE TRIGGER check_${table}_${operation} BEFORE $operation ON `$table` WHEN NOT ($condition) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END")
        }
        // Room cannot export a partial-index predicate. Enforce conditional
        // uniqueness explicitly, without an untracked index that breaks migration validation.
        for (operation in listOf("INSERT", "UPDATE")) connection.execSQL(
            "CREATE TRIGGER cash_one_open_$operation BEFORE $operation ON cash_sessions WHEN NEW.status='OPEN' AND EXISTS(SELECT 1 FROM cash_sessions WHERE userId=NEW.userId AND status='OPEN' AND id!=NEW.id) BEGIN SELECT RAISE(ABORT, 'one open cash session'); END")
    }
}
