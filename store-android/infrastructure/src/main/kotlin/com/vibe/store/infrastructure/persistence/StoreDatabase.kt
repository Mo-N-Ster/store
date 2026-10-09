package com.vibe.store.infrastructure.persistence

import java.math.BigDecimal
import androidx.room.*
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Dao
internal interface StoreDao {
    @Query("SELECT * FROM invoices i WHERE i.invoiceDate>=:fromInclusive AND i.invoiceDate<:toExclusive AND (:afterId IS NULL OR i.id>:afterId) AND (:status IS NULL OR i.status=:status) AND (:productId IS NULL OR EXISTS(SELECT 1 FROM invoice_lines l WHERE l.invoiceId=i.id AND l.productId=:productId)) AND (:category IS NULL OR EXISTS(SELECT 1 FROM invoice_lines l WHERE l.invoiceId=i.id AND l.category=:category)) ORDER BY i.id LIMIT :limit")
    suspend fun reportInvoicesPage(fromInclusive: String, toExclusive: String, afterId: String?, status: String?, productId: Long?, category: String?, limit: Int): List<InvoiceEntity>
    @Query("SELECT * FROM invoices WHERE id IN (:ids)") suspend fun reportInvoices(ids: List<String>): List<InvoiceEntity>
    @Query("SELECT * FROM invoice_lines WHERE invoiceId IN (:ids) ORDER BY invoiceId,id") suspend fun reportInvoiceLines(ids: List<String>): List<InvoiceLineEntity>
    @Query("SELECT il.id AS lineId,il.invoiceId AS invoiceId,il.productId AS productId,il.productName AS productName,il.category AS category,il.quantity AS quantity,il.unitPrice AS unitPrice,il.totalLine AS totalLine,il.unitCost AS unitCost,i.invoiceDate AS invoiceDate,i.subtotal AS subtotal,i.totalAmount AS totalAmount FROM invoice_lines il JOIN invoices i ON i.id=il.invoiceId WHERE i.status='validated' AND i.invoiceDate>=:fromInclusive AND i.invoiceDate<:toExclusive AND il.productId IS NOT NULL AND (:productId IS NULL OR il.productId=:productId) AND (:category IS NULL OR il.category=:category) AND (:afterProductId IS NULL OR il.productId>:afterProductId OR (il.productId=:afterProductId AND il.id>:afterLineId)) ORDER BY il.productId,il.id LIMIT :limit")
    suspend fun reportProductSalesLines(fromInclusive: String, toExclusive: String, productId: Long?, category: String?, afterProductId: Long?, afterLineId: Long, limit: Int): List<ReportProductSaleLineProjection>
    @Query("SELECT il.id AS lineId,il.invoiceId AS invoiceId,il.productId AS productId,il.productName AS productName,il.category AS category,il.quantity AS quantity,il.unitPrice AS unitPrice,il.totalLine AS totalLine,il.unitCost AS unitCost,i.invoiceDate AS invoiceDate,i.subtotal AS subtotal,i.totalAmount AS totalAmount FROM invoice_lines il JOIN invoices i ON i.id=il.invoiceId WHERE i.status='validated' AND i.invoiceDate>=:fromInclusive AND i.invoiceDate<:toExclusive AND il.productId IS NOT NULL AND (:productId IS NULL OR il.productId=:productId) AND (:category IS NULL OR il.category=:category) AND (:afterCategory IS NULL OR il.category>:afterCategory OR (il.category=:afterCategory AND (il.productId>:afterProductId OR (il.productId=:afterProductId AND il.id>:afterLineId)))) ORDER BY il.category,il.productId,il.id LIMIT :limit")
    suspend fun reportCategorySalesLines(fromInclusive: String, toExclusive: String, productId: Long?, category: String?, afterCategory: String?, afterProductId: Long, afterLineId: Long, limit: Int): List<ReportProductSaleLineProjection>
    @Query("SELECT * FROM invoices WHERE invoiceDate>=:fromInclusive AND invoiceDate<:toExclusive AND (:status IS NULL OR status=:status) AND (:productId IS NULL OR EXISTS(SELECT 1 FROM invoice_lines l WHERE l.invoiceId=invoices.id AND l.productId=:productId)) AND (:category IS NULL OR EXISTS(SELECT 1 FROM invoice_lines l WHERE l.invoiceId=invoices.id AND l.category=:category)) ORDER BY invoiceDate DESC,id DESC LIMIT :limit")
    suspend fun reportInvoiceDisplay(fromInclusive: String, toExclusive: String, status: String?, productId: Long?, category: String?, limit: Int): List<InvoiceEntity>
    @Query("SELECT p.reference AS reference,p.createdAt AS createdAt,p.status AS status,COALESCE(s.name,'') AS supplierName FROM purchases p LEFT JOIN suppliers s ON s.id=p.supplierId WHERE p.createdAt>=:fromInclusive AND p.createdAt<:toExclusive AND (:status IS NULL OR p.status=:status) AND (:supplierId IS NULL OR p.supplierId=:supplierId) AND (:productId IS NULL OR EXISTS(SELECT 1 FROM purchase_items l WHERE l.purchaseId=p.id AND l.productId=:productId)) ORDER BY p.createdAt DESC,p.id DESC LIMIT :limit")
    suspend fun reportPurchaseDisplay(fromInclusive: String, toExclusive: String, status: String?, supplierId: Long?, productId: Long?, limit: Int): List<ReportPurchaseProjection>
    @Query("SELECT * FROM products WHERE deletedAt IS NULL AND (:productId IS NULL OR id=:productId) AND (:category IS NULL OR category=:category) AND (:afterId IS NULL OR id>:afterId) ORDER BY id LIMIT :limit")
    suspend fun reportProductsPage(afterId: Long?, productId: Long?, category: String?, limit: Int): List<ProductEntity>
    @Query("SELECT m.id,m.productId,m.quantity,m.reason,m.unitPrice,m.createdAt,m.referenceId,p.name AS productName,p.category AS productCategory FROM stock_movements m JOIN products p ON p.id=m.productId WHERE m.createdAt>=:fromInclusive AND m.createdAt<:toExclusive AND (:afterId IS NULL OR m.id>:afterId) AND (:productId IS NULL OR m.productId=:productId) AND (:category IS NULL OR p.category=:category) AND (:reason IS NULL OR m.reason=:reason OR (:reason='adjustment' AND m.reason LIKE 'adjustment:%')) ORDER BY m.id LIMIT :limit")
    suspend fun reportMovementsPage(fromInclusive: String, toExclusive: String, afterId: Long?, productId: Long?, category: String?, reason: String?, limit: Int): List<ReportMovementProjection>
    @Query("SELECT a.id,a.userId,a.startTime,a.endTime,a.status,u.firstName,u.lastName,u.role FROM attendances a JOIN users u ON u.id=a.userId WHERE a.startTime>=:fromInclusive AND a.startTime<:toExclusive AND (:afterId IS NULL OR a.id>:afterId) AND (:userId IS NULL OR a.userId=:userId) AND (:status IS NULL OR a.status=:status) AND (:onlyOpen=0 OR a.endTime IS NULL) ORDER BY a.id LIMIT :limit")
    suspend fun reportAttendancePage(fromInclusive: String, toExclusive: String, afterId: Long?, userId: Long?, status: String?, onlyOpen: Boolean, limit: Int): List<ReportAttendanceProjection>
    @Query("SELECT a.id,a.userId,a.startTime,a.endTime,a.status,u.firstName,u.lastName,u.role FROM attendances a JOIN users u ON u.id=a.userId WHERE a.startTime>=:fromInclusive AND a.startTime<:toExclusive AND (:userId IS NULL OR a.userId=:userId) AND (:status IS NULL OR a.status=:status) AND (:onlyOpen=0 OR a.endTime IS NULL) ORDER BY a.startTime DESC,a.id DESC LIMIT :limit")
    suspend fun reportAttendanceDisplay(fromInclusive: String, toExclusive: String, userId: Long?, status: String?, onlyOpen: Boolean, limit: Int): List<ReportAttendanceProjection>
    @Query("SELECT p.id AS id,p.status AS status FROM purchases p WHERE p.createdAt>=:fromInclusive AND p.createdAt<:toExclusive AND (:afterId IS NULL OR p.id>:afterId) AND (:status IS NULL OR p.status=:status) AND (:supplierId IS NULL OR p.supplierId=:supplierId) AND (:productId IS NULL OR EXISTS(SELECT 1 FROM purchase_items l WHERE l.purchaseId=p.id AND l.productId=:productId)) ORDER BY p.id LIMIT :limit")
    suspend fun reportPurchaseCountsPage(fromInclusive: String, toExclusive: String, afterId: Long?, status: String?, supplierId: Long?, productId: Long?, limit: Int): List<ReportPurchaseCountProjection>
    @Query("SELECT * FROM product_price_history WHERE productId IN (:productIds) AND recordedAt>=:fromInclusive AND recordedAt<:toExclusive ORDER BY recordedAt DESC,id DESC LIMIT :limit")
    suspend fun reportPriceHistory(productIds: List<Long>, fromInclusive: String, toExclusive: String, limit: Int): List<PriceEntity>
    @Query("SELECT * FROM suppliers WHERE id=:id") suspend fun findSupplier(id: Long): SupplierEntity?
    @Query("SELECT * FROM suppliers WHERE (:active IS NULL OR active=:active) AND (:search='' OR instr(lower(name || ' ' || COALESCE(phone,'') || ' ' || COALESCE(email,'') || ' ' || COALESCE(address,'')),lower(:search))>0) ORDER BY name COLLATE NOCASE,id LIMIT :limit OFFSET :offset")
    suspend fun supplierPage(search: String, active: Boolean?, limit: Int, offset: Int): List<SupplierEntity>
    @Query("SELECT EXISTS(SELECT 1 FROM suppliers WHERE name=:name AND id!=COALESCE(:exceptId,0))") suspend fun supplierNameExists(name: String, exceptId: Long?): Boolean
    @Query("SELECT COALESCE(MAX(id),0)+1 FROM suppliers") suspend fun nextSupplierId(): Long
    @Query("UPDATE suppliers SET name=:name,phone=:phone,email=:email,address=:address,active=:active,updatedAt=:stamp WHERE id=:id AND updatedAt=:expected")
    suspend fun editSupplier(id: Long, name: String, phone: String, email: String, address: String, active: Boolean, stamp: String, expected: String): Int
    @Query("SELECT EXISTS(SELECT 1 FROM purchases WHERE idempotencyKey=:key)") suspend fun purchaseKeyExists(key: String): Boolean
    @Query("SELECT * FROM purchases p WHERE (:status IS NULL OR p.status=:status) AND (:supplier IS NULL OR p.supplierId=:supplier) AND (:product IS NULL OR EXISTS(SELECT 1 FROM purchase_items l WHERE l.purchaseId=p.id AND l.productId=:product)) AND (:from='' OR substr(p.createdAt,1,10)>=:from) AND (:to='' OR substr(p.createdAt,1,10)<=:to) ORDER BY p.createdAt DESC,p.id DESC LIMIT :limit OFFSET :offset")
    suspend fun purchasePage(status: String?, supplier: Long?, product: Long?, from: String, to: String, limit: Int, offset: Int): List<PurchaseEntity>
    @Query("SELECT * FROM purchase_items WHERE purchaseId=:id ORDER BY id LIMIT :limit OFFSET :offset") suspend fun purchaseLines(id: Long, limit: Int, offset: Int): List<PurchaseLineEntity>
    @Query("SELECT * FROM purchase_items WHERE purchaseId=:id AND productId=:product") suspend fun purchaseItem(id: Long, product: Long): PurchaseLineEntity?
    @Query("SELECT COALESCE(MAX(id),0)+1 FROM purchases") suspend fun nextPurchaseId(): Long
    @Query("SELECT COALESCE(MAX(id),0)+1 FROM purchase_items") suspend fun nextPurchaseLineId(): Long
    @Query("UPDATE purchase_items SET quantity=:quantity,unitCost=:cost,totalLine=:total WHERE id=:line AND purchaseId=:id AND productId=:product AND quantity=:oldQuantity AND unitCost=:oldCost AND totalLine=:oldTotal AND EXISTS(SELECT 1 FROM purchases WHERE id=:id AND status='DRAFT')")
    suspend fun editPurchaseItem(id: Long, line: Long, product: Long, quantity: Long, cost: BigDecimal, total: BigDecimal, oldQuantity: Long, oldCost: BigDecimal, oldTotal: BigDecimal): Int
    @Query("UPDATE purchases SET totalAmount=:total WHERE id=:id AND status='DRAFT'") suspend fun draftPurchaseTotal(id: Long, total: BigDecimal): Int
    @Query("UPDATE purchases SET status=:status,validatedBy=:validator,validatedAt=:validated,cancelledBy=:canceller,cancelledAt=:cancelled,cancellationReason=:reason WHERE id=:id AND status=:expected")
    suspend fun purchaseTransition(id: Long, expected: String, status: String, validator: Long?, validated: String?, canceller: Long?, cancelled: String?, reason: String?): Int
    @Query("SELECT * FROM inventory_counts WHERE (:status IS NULL OR status=:status) AND (:from='' OR substr(createdAt,1,10)>=:from) AND (:to='' OR substr(createdAt,1,10)<=:to) ORDER BY createdAt DESC,id DESC LIMIT :limit OFFSET :offset")
    suspend fun inventoryPage(status: String?, from: String, to: String, limit: Int, offset: Int): List<InventoryEntity>
    @Query("SELECT * FROM inventory_count_lines WHERE inventoryId=:id ORDER BY id LIMIT :limit OFFSET :offset") suspend fun inventoryLines(id: Long, limit: Int, offset: Int): List<InventoryLineEntity>
    @Query("SELECT COALESCE(MAX(id),0)+1 FROM inventory_counts") suspend fun nextInventoryId(): Long
    @Query("SELECT COALESCE(MAX(id),0)+1 FROM inventory_count_lines") suspend fun nextInventoryLineId(): Long
    @Query("UPDATE inventory_count_lines SET countedQuantity=:count WHERE id=:line AND inventoryId=:id AND productId=:product AND countedQuantity=:expected AND EXISTS(SELECT 1 FROM inventory_counts WHERE id=:id AND status='DRAFT')")
    suspend fun editInventoryCount(id: Long, line: Long, product: Long, count: Long, expected: Long): Int
    @Query("UPDATE inventory_counts SET status='VALIDATED',validatedBy=:actor,validatedAt=:stamp WHERE id=:id AND status='DRAFT'")
    suspend fun inventoryValidated(id: Long, actor: Long, stamp: String): Int
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
    @Query("SELECT unitPrice FROM stock_movements WHERE productId=:id AND quantity>0 AND reason IN ('purchase','initial') ORDER BY createdAt DESC,id DESC LIMIT 1") suspend fun latestCost(id: Long): BigDecimal?
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
    @Query("SELECT * FROM payments WHERE cashId=:id AND status='CAPTURED' AND (:after IS NULL OR id>:after) ORDER BY id LIMIT 256")
    suspend fun capturedPaymentPage(id: Long, after: Long?): List<PaymentEntity>

    // Invoked only through repositories on their existing confined reader/writer lease.
    suspend fun capturedPayments(id: Long): BigDecimal {
        var total = BigDecimal.ZERO
        var after: Long? = null
        while (true) {
            val page = capturedPaymentPage(id, after)
            for (payment in page) total = total.add(payment.amount)
            if (page.size < 256) return total
            after = page.last().id
        }
    }
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
    // I08: static, recipient-scoped local chat queries. Broadcast receipts snapshot recipients at send time.
    @Query("SELECT COALESCE(MAX(id),0)+1 FROM messages") suspend fun nextMessageId(): Long
    @Query("SELECT * FROM messages WHERE requestId=:key LIMIT 1") suspend fun messageByRequest(key: String): MessageEntity?
    @Query("SELECT * FROM message_reads WHERE messageId=:id AND userId=:actor LIMIT 1")
    suspend fun messageReceipt(id: Long, actor: Long): MessageReadEntity?
    @Upsert suspend fun putMessageReceipt(value: MessageReadEntity)
    @Upsert suspend fun putMessageDeletion(value: MessageDeletionEntity)
    @Query("UPDATE message_reads SET readAt=:stamp WHERE messageId=:id AND userId=:actor")
    suspend fun markMessageReceipt(id: Long, actor: Long, stamp: String): Int
    @Query("SELECT m.* FROM messages m WHERE m.id=:id AND " +
        "(m.senderId=:actor OR EXISTS(SELECT 1 FROM message_reads r WHERE r.messageId=m.id AND r.userId=:actor)) AND " +
        "NOT EXISTS(SELECT 1 FROM message_deletions d WHERE d.messageId=m.id AND d.userId=:actor) LIMIT 1")
    suspend fun visibleMessage(id: Long, actor: Long): MessageEntity?
    @Query("SELECT m.* FROM messages m WHERE " +
        "(m.senderId=:actor OR EXISTS(SELECT 1 FROM message_reads r WHERE r.messageId=m.id AND r.userId=:actor)) AND " +
        "NOT EXISTS(SELECT 1 FROM message_deletions d WHERE d.messageId=m.id AND d.userId=:actor) AND " +
        "(:unreadOnly=0 OR (m.senderId!=:actor AND EXISTS(SELECT 1 FROM message_reads r WHERE r.messageId=m.id AND r.userId=:actor AND r.readAt='PENDING'))) " +
        "ORDER BY m.createdAt DESC,m.id DESC LIMIT :limit OFFSET :offset")
    suspend fun visibleMessagePage(actor: Long, unreadOnly: Boolean, limit: Int, offset: Int): List<MessageEntity>
    @Query("SELECT COALESCE(MAX(id),0)+1 FROM notifications") suspend fun nextNotificationId(): Long
    @Query("UPDATE notifications SET resolvedAt=:stamp WHERE type='LOW_STOCK' AND productId=:product AND resolvedAt IS NULL")
    suspend fun resolveStockAlert(product: Long, stamp: String): Int
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC,id DESC LIMIT :limit OFFSET :offset")
    suspend fun supportAlerts(limit: Int, offset: Int): List<NotificationEntity>
    @Query("SELECT EXISTS(SELECT 1 FROM notifications WHERE id=:id)") suspend fun supportAlertExists(id: Long): Boolean
    @Query("UPDATE notifications SET resolvedAt=:stamp WHERE id=:id AND resolvedAt IS NULL")
    suspend fun supportResolveAlert(id: Long, stamp: String): Int
    @Query("SELECT * FROM audit_logs WHERE (:action='' OR action=:action) AND (:entity='' OR entity=:entity) AND " +
        "(:outcome='' OR outcome=:outcome) AND (:fromDate='' OR substr(createdAt,1,10)>=:fromDate) AND " +
        "(:toDate='' OR substr(createdAt,1,10)<=:toDate) AND (:actor IS NULL OR userId=:actor) " +
        "ORDER BY createdAt DESC,id DESC LIMIT :limit OFFSET :offset")
    suspend fun supportAudit(action: String, entity: String, outcome: String, fromDate: String, toDate: String,
        actor: Long?, limit: Int, offset: Int): List<AuditEntity>
    @Query("SELECT COUNT(*) FROM users") suspend fun supportUserCount(): Int
    @Query("SELECT COUNT(*) FROM messages") suspend fun supportMessageCount(): Int
    @Query("SELECT COUNT(*) FROM email_report_logs WHERE status IN ('pending','sending','failed')")
    suspend fun supportEmailPendingCount(): Int
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
    GenerationEntity::class, PendingCommandEntity::class], version = 2, exportSchema = true)
@TypeConverters(MoneyText::class)
internal abstract class StoreDatabase : RoomDatabase() { abstract fun records(): StoreDao }

internal data class ReportMovementProjection(
    val id: Long, val productId: Long, val quantity: Long, val reason: String, val unitPrice: BigDecimal,
    val createdAt: String, val referenceId: String?, val productName: String, val productCategory: String,
)

internal data class ReportAttendanceProjection(
    val id: Long, val userId: Long, val startTime: String, val endTime: String?, val status: String,
    val firstName: String, val lastName: String, val role: String,
)

internal data class ReportProductSaleLineProjection(
    val lineId: Long, val invoiceId: String, val productId: Long, val productName: String, val category: String,
    val quantity: Long, val unitPrice: BigDecimal, val totalLine: BigDecimal, val unitCost: BigDecimal?,
    val invoiceDate: String, val subtotal: BigDecimal, val totalAmount: BigDecimal,
)

internal data class ReportPurchaseProjection(val reference: String, val createdAt: String, val status: String, val supplierName: String)
internal data class ReportPurchaseCountProjection(val id: Long, val status: String)

internal object StoreConstraints {
    // Room has no CHECK annotation. Fixed internal triggers enforce the same
    // constraints on INSERT and UPDATE; not a renderer-selected table/SQL API.
    val checks = mapOf(
        "users" to "NEW.role IN ('owner','manager','employee') AND NEW.active IN (0,1)",
        "roles" to "NEW.isSystem IN (0,1)",
        "employees" to "NEW.status IN ('ACTIVE','ABSENT','SUSPENDED','RESIGNED','ARCHIVED')",
        "cash_sessions" to "NEW.status IN ('OPEN','CLOSED')",
        "products" to "typeof(NEW.stock)='integer' AND NEW.stock BETWEEN 0 AND 9007199254740991 AND NEW.minimumStock>=0",
        "product_price_history" to "1",
        "inventory_counts" to "NEW.status IN ('DRAFT','VALIDATED','CANCELLED')",
        "inventory_count_lines" to "typeof(NEW.expectedQuantity)='integer' AND typeof(NEW.countedQuantity)='integer' AND NEW.expectedQuantity>=0 AND NEW.countedQuantity>=0",
        "suppliers" to "NEW.active IN (0,1)",
        "purchases" to "NEW.status IN ('DRAFT','VALIDATED','CANCELLED')",
        "purchase_items" to "typeof(NEW.quantity)='integer' AND NEW.quantity BETWEEN 1 AND 9007199254740991",
        "invoices" to "NEW.status IN ('validated','cancelled') AND ((NEW.canonicalRequest IS NULL AND NEW.canonicalVersion IS NULL) OR (NEW.canonicalRequest IS NOT NULL AND NEW.canonicalVersion IS NOT NULL AND NEW.canonicalVersion>0))",
        "invoice_lines" to "typeof(NEW.quantity)='integer' AND NEW.quantity BETWEEN 1 AND 9007199254740991",
        "payments" to "NEW.method='CASH' AND NEW.status IN ('CAPTURED','REFUNDED')",
        "attendances" to "NEW.status IN ('VALID','CORRECTED','INTERRUPTED')"
    )
    fun create(connection: SQLiteConnection) {
        val combined = checks.toMutableMap()
        MoneyColumns.tables.forEach { (table, columns) ->
            val moneyChecks = columns.joinToString(" AND ") { column -> MoneyText.sqlCheck("NEW.`$column`", MoneyColumns.signed(table, column)) }
            combined[table] = "(${combined[table] ?: "1"}) AND ($moneyChecks)"
        }
        combined.forEach { (table, condition) ->
            for (operation in listOf("INSERT", "UPDATE")) connection.execSQL(
                "CREATE TRIGGER check_${table}_${operation} BEFORE $operation ON `$table` WHEN NOT ($condition) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END")
        }
        // Room cannot export a partial-index predicate. Enforce conditional
        // uniqueness explicitly, without an untracked index that breaks migration validation.
        for (operation in listOf("INSERT", "UPDATE")) connection.execSQL(
            "CREATE TRIGGER cash_one_open_$operation BEFORE $operation ON cash_sessions WHEN NEW.status='OPEN' AND EXISTS(SELECT 1 FROM cash_sessions WHERE userId=NEW.userId AND status='OPEN' AND id!=NEW.id) BEGIN SELECT RAISE(ABORT, 'one open cash session'); END")
    }
}
