package com.vibe.store.infrastructure.persistence

import androidx.room.*
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Dao
internal interface StoreDao {
    @Query("SELECT * FROM cash_sessions WHERE userId=:actor ORDER BY id DESC LIMIT 40 OFFSET :offset") suspend fun cashPage(actor: Long, offset: Int): List<CashEntity>
    @Query("SELECT COALESCE(MAX(id),0)+1 FROM cash_sessions") suspend fun nextCashId(): Long
    @Update suspend fun updateCash(value: CashEntity)
    @Query("SELECT * FROM invoices WHERE idempotencyKey=:key") suspend fun invoiceByKey(key: String): InvoiceEntity?
    @Query("SELECT * FROM invoice_lines WHERE invoiceId=:id ORDER BY id") suspend fun invoiceLines(id: String): List<InvoiceLineEntity>
    @Query("SELECT * FROM payments WHERE invoiceId=:id") suspend fun invoicePayment(id: String): PaymentEntity?
    @Query("SELECT COALESCE(MAX(id),0)+1 FROM invoice_lines") suspend fun nextInvoiceLineId(): Long
    @Query("SELECT COALESCE(MAX(id),0)+1 FROM payments") suspend fun nextPaymentId(): Long
    @Query("SELECT * FROM invoices i WHERE (:search='' OR instr(i.id,:search)>0) AND (:from='' OR substr(i.invoiceDate,1,10)>=:from) AND (:to='' OR substr(i.invoiceDate,1,10)<=:to) AND (:product IS NULL OR EXISTS(SELECT 1 FROM invoice_lines l WHERE l.invoiceId=i.id AND l.productId=:product)) AND (:category='' OR EXISTS(SELECT 1 FROM invoice_lines l WHERE l.invoiceId=i.id AND l.category=:category)) ORDER BY i.invoiceDate DESC,i.id DESC LIMIT :limit OFFSET :offset")
    suspend fun salePage(search: String, from: String, to: String, product: Long?, category: String, limit: Int, offset: Int): List<InvoiceEntity>
    @Update suspend fun updateInvoice(value: InvoiceEntity)
    @Query("UPDATE payments SET status='REFUNDED' WHERE invoiceId=:id AND status='CAPTURED'") suspend fun refund(id: String)
    @Query("SELECT unitPrice FROM stock_movements WHERE productId=:id AND quantity>0 AND unitPrice>=0 AND reason IN ('purchase','initial') ORDER BY createdAt DESC,id DESC LIMIT 1") suspend fun latestCost(id: Long): Double?
    @Query("SELECT * FROM pending_commands WHERE commandKey=:key") suspend fun pendingByKey(key: String): PendingCommandEntity?
    @Query("SELECT * FROM pending_commands WHERE actorId=:actor AND state='SUBMITTED' ORDER BY createdAt DESC LIMIT 100") suspend fun pendingFor(actor: Long): List<PendingCommandEntity>
    @Query("UPDATE pending_commands SET state='ACKNOWLEDGED' WHERE commandKey=:key") suspend fun acknowledgeCommand(key: String)
    @Query("UPDATE pending_commands SET state='DISCARDED' WHERE commandKey=:key") suspend fun abandonCommand(key: String)
    @Query("SELECT * FROM products WHERE ((:archived=0 AND deletedAt IS NULL) OR (:archived=1 AND deletedAt IS NOT NULL)) AND (:category='' OR category=:category) AND (:search='' OR instr(lower(name || ' ' || category || ' ' || COALESCE(hashtag,'')),lower(:search))>0) AND (:stock='all' OR (:stock='low' AND stock<=minimumStock) OR (:stock='out' AND stock=0)) ORDER BY name COLLATE NOCASE,id LIMIT :limit OFFSET :offset")
    suspend fun catalogPage(search: String, category: String, archived: Boolean, stock: String, limit: Int, offset: Int): List<ProductEntity>
    @Query("SELECT EXISTS(SELECT 1 FROM products WHERE deletedAt IS NULL AND id!=COALESCE(:exceptId,0) AND (lower(trim(name))=lower(:name) OR (:hashtag!='' AND lower(COALESCE(hashtag,''))=lower(:hashtag))))")
    suspend fun duplicateProduct(name: String, hashtag: String, exceptId: Long?): Boolean
    @Query("SELECT COALESCE(MAX(id),0)+1 FROM products") suspend fun nextProductId(): Long
    @Query("SELECT COALESCE(MAX(id),0)+1 FROM stock_movements") suspend fun nextMovementId(): Long
    @Query("SELECT COALESCE(MAX(id),0)+1 FROM product_price_history") suspend fun nextPriceId(): Long
    @Upsert suspend fun putProduct(value: ProductEntity)
    @Query("SELECT * FROM stock_movements WHERE (:from='' OR substr(createdAt,1,10)>=:from) AND (:to='' OR substr(createdAt,1,10)<=:to) AND (:productId IS NULL OR productId=:productId) AND (:category='' OR productId IN (SELECT id FROM products WHERE category=:category)) AND (:type='' OR reason=:type OR (:type='adjustment' AND reason LIKE 'adjustment:%')) ORDER BY createdAt DESC,id DESC LIMIT :limit OFFSET :offset")
    suspend fun movementPage(from: String, to: String, productId: Long?, category: String, type: String, limit: Int, offset: Int): List<MovementEntity>
    @Query("SELECT * FROM product_price_history WHERE productId=:id ORDER BY recordedAt DESC,id DESC LIMIT 200") suspend fun productPrices(id: Long): List<PriceEntity>
    @Query("DELETE FROM stock_movements WHERE id IN (:ids)") suspend fun deleteMovements(ids: List<Long>): Int
    @Query("SELECT DISTINCT imageRef FROM products WHERE imageRef IS NOT NULL") suspend fun productImageReferences(): List<String>
    @Query("SELECT * FROM users ORDER BY id") suspend fun accounts(): List<UserEntity>
    @Query("SELECT * FROM employees ORDER BY id") suspend fun employees(): List<EmployeeEntity>
    @Query("SELECT * FROM employees WHERE userId=:id LIMIT 1") suspend fun employeeForUser(id: Long): EmployeeEntity?
    @Query("SELECT COALESCE(MAX(id),0)+1 FROM employees") suspend fun nextEmployeeId(): Long
    @Query("SELECT EXISTS(SELECT 1 FROM employees WHERE lower(trim(code))=lower(trim(:code)) AND id!=COALESCE(:exceptId,0))")
    suspend fun duplicateEmployeeCode(code: String, exceptId: Long?): Boolean
    @Update suspend fun updateUser(value: UserEntity)
    @Update suspend fun updateEmployee(value: EmployeeEntity)
    @Query("SELECT status FROM employees WHERE userId=:id") suspend fun employment(id: Long): String?
    @Query("SELECT * FROM roles") suspend fun roles(): List<RoleEntity>
    @Query("SELECT * FROM permissions") suspend fun permissions(): List<PermissionEntity>
    @Query("SELECT p.module || ':' || p.action FROM user_roles ur JOIN role_permissions rp ON rp.roleId=ur.roleId JOIN permissions p ON p.id=rp.permissionId WHERE ur.userId=:id ORDER BY p.module,p.action") suspend fun inherited(id: Long): List<String>
    @Query("SELECT p.module || ':' || p.action FROM user_permission_denials d JOIN permissions p ON p.id=d.permissionId WHERE d.userId=:id ORDER BY p.module,p.action") suspend fun denied(id: Long): List<String>
    @Query("DELETE FROM user_permission_denials WHERE userId=:id") suspend fun deleteDenials(id: Long)
    @Query("SELECT * FROM cash_sessions WHERE userId=:id AND status='OPEN' ORDER BY id DESC LIMIT 1") suspend fun openCash(id: Long): CashEntity?
    @Query("SELECT COALESCE(SUM(amount),0) FROM payments WHERE cashId=:id AND status='CAPTURED'") suspend fun capturedPayments(id: Long): Double
    @Query("SELECT COALESCE(MAX(id),0)+1 FROM audit_logs") suspend fun nextAuditId(): Long
    @Insert suspend fun user(value: UserEntity)
    @Insert suspend fun role(value: RoleEntity)
    @Insert suspend fun permission(value: PermissionEntity)
    @Insert suspend fun rolePermission(value: RolePermissionEntity)
    @Insert suspend fun userRole(value: UserRoleEntity)
    @Upsert suspend fun putUserRole(value: UserRoleEntity)
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
    @Query("SELECT a.* FROM attendances a JOIN users u ON u.id=a.userId WHERE (:fromInclusive='' OR a.startTime>=:fromInclusive) AND (:toExclusive='' OR a.startTime<:toExclusive) AND (:userId IS NULL OR a.userId=:userId) AND (:role='' OR u.role=:role) AND (:status='' OR a.status=:status) ORDER BY a.startTime DESC,a.id DESC LIMIT :limit OFFSET :offset")
    suspend fun attendancePage(fromInclusive: String, toExclusive: String, userId: Long?, role: String, status: String, limit: Int, offset: Int): List<AttendanceEntity>
    @Query("SELECT * FROM attendances WHERE userId=:userId AND endTime IS NULL ORDER BY startTime DESC,id DESC LIMIT 1")
    suspend fun openAttendance(userId: Long): AttendanceEntity?
    @Query("SELECT COALESCE(MAX(id),0)+1 FROM attendances") suspend fun nextAttendanceId(): Long
    @Update suspend fun updateAttendance(value: AttendanceEntity)
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
