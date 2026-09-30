package com.vibe.store.infrastructure.persistence

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Persistence-only records. Never exposed through application-api or presentation.
@Entity(tableName = "users", indices = [Index(value = ["username"], unique = true), Index(value = ["email"], unique = true)])
internal data class UserEntity(@PrimaryKey val id: Long, val username: String, val passwordHash: String,
    val role: String, val firstName: String, val lastName: String, val initials: String,
    val createdAt: String, val email: String? = null, val phone: String? = null, val hireDate: String? = null,
    val active: Boolean = true, val securityQuestion: String? = null, val securityAnswerHash: String? = null,
    val photo: String? = null, val failedLoginAttempts: Int = 0, val lockedUntil: String? = null,
    val lastLoginAt: String? = null, val failedRecoveryAttempts: Int = 0, val recoveryLockedUntil: String? = null)

@Entity(tableName = "roles", indices = [Index(value = ["code"], unique = true)])
internal data class RoleEntity(@PrimaryKey val id: Long, val code: String, val name: String, val isSystem: Boolean, val createdAt: String)
@Entity(tableName = "permissions", indices = [Index(value = ["module", "action"], unique = true)])
internal data class PermissionEntity(@PrimaryKey val id: Long, val module: String, val action: String, val createdAt: String)
@Entity(tableName = "role_permissions", primaryKeys = ["roleId", "permissionId"],
    foreignKeys = [ForeignKey(entity = RoleEntity::class, parentColumns = ["id"], childColumns = ["roleId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PermissionEntity::class, parentColumns = ["id"], childColumns = ["permissionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("permissionId")])
internal data class RolePermissionEntity(val roleId: Long, val permissionId: Long)
@Entity(tableName = "user_roles", foreignKeys = [ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = RoleEntity::class, parentColumns = ["id"], childColumns = ["roleId"])], indices = [Index("roleId")])
internal data class UserRoleEntity(@PrimaryKey val userId: Long, val roleId: Long, val assignedAt: String)
@Entity(tableName = "user_permission_denials", primaryKeys = ["userId", "permissionId"],
    foreignKeys = [ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PermissionEntity::class, parentColumns = ["id"], childColumns = ["permissionId"], onDelete = ForeignKey.CASCADE)], indices = [Index("permissionId")])
internal data class DenialEntity(val userId: Long, val permissionId: Long)
@Entity(tableName = "employees", foreignKeys = [ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index(value = ["code"], unique = true), Index(value = ["userId"], unique = true)])
internal data class EmployeeEntity(@PrimaryKey val id: Long, val code: String, val userId: Long?, val firstName: String,
    val lastName: String, val status: String, val createdAt: String, val updatedAt: String,
    val phone: String? = null, val email: String? = null, val address: String? = null, val hireDate: String? = null, val endDate: String? = null)

@Entity(tableName = "cash_sessions", foreignKeys = [ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"]),
    ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["closedBy"])],
    indices = [Index(value = ["reference"], unique = true), Index("userId"), Index("closedBy")])
internal data class CashEntity(@PrimaryKey val id: Long, val reference: String, val userId: Long, val status: String,
    val openingAmount: Double, val openedAt: String, val closedAt: String? = null, val closingAmount: Double? = null,
    val expectedAmount: Double? = null, val difference: Double? = null, val closedBy: Long? = null)
@Entity(tableName = "products", indices = [Index(value = ["reference"], unique = true)])
internal data class ProductEntity(@PrimaryKey val id: Long, val name: String, val category: String, val price: Double,
    val stock: Long, val minimumStock: Long, val createdAt: String, val updatedAt: String,
    val reference: String? = null, val hashtag: String? = null, val description: String = "",
    val deletedAt: String? = null, val imageRef: String? = null)
@Entity(tableName = "product_price_history", foreignKeys = [ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"])], indices = [Index("productId")])
internal data class PriceEntity(@PrimaryKey val id: Long, val productId: Long, val price: Double, val recordedAt: String)
@Entity(tableName = "stock_movements", foreignKeys = [ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"])], indices = [Index("productId")])
internal data class MovementEntity(@PrimaryKey val id: Long, val productId: Long, val quantity: Long, val reason: String,
    val unitPrice: Double, val createdAt: String, val referenceId: String? = null)

@Entity(tableName = "invoices", foreignKeys = [ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"]),
    ForeignKey(entity = CashEntity::class, parentColumns = ["id"], childColumns = ["cashId"]),
    ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["cancelledBy"])],
    indices = [Index(value = ["idempotencyKey"], unique = true), Index("userId"), Index("cashId"), Index("cancelledBy")])
internal data class InvoiceEntity(@PrimaryKey val id: String, val userId: Long, val cashId: Long?, val invoiceDate: String,
    val subtotal: Double, val totalAmount: Double, val discount: Double, val status: String,
    val idempotencyKey: String? = null, val canonicalVersion: Int? = null, val canonicalRequest: String? = null,
    val cancelledBy: Long? = null, val cancelledAt: String? = null, val cancellationReason: String? = null,
    val storeName: String? = null, val storeAddress: String? = null, val storePhone: String? = null,
    val storeEmail: String? = null, val currency: String? = null)
@Entity(tableName = "invoice_lines", foreignKeys = [ForeignKey(entity = InvoiceEntity::class, parentColumns = ["id"], childColumns = ["invoiceId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"])], indices = [Index("invoiceId"), Index("productId")])
internal data class InvoiceLineEntity(@PrimaryKey val id: Long, val invoiceId: String, val productId: Long?,
    val productName: String, val category: String, val quantity: Long, val unitPrice: Double, val totalLine: Double, val unitCost: Double? = null)
@Entity(tableName = "payments", foreignKeys = [ForeignKey(entity = InvoiceEntity::class, parentColumns = ["id"], childColumns = ["invoiceId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = CashEntity::class, parentColumns = ["id"], childColumns = ["cashId"])], indices = [Index(value = ["invoiceId"], unique = true), Index("cashId")])
internal data class PaymentEntity(@PrimaryKey val id: Long, val invoiceId: String, val cashId: Long, val method: String,
    val amount: Double, val received: Double, val change: Double, val status: String, val createdAt: String)

@Entity(tableName = "suppliers", indices = [Index(value = ["name"], unique = true)])
internal data class SupplierEntity(@PrimaryKey val id: Long, val name: String, val active: Boolean, val createdAt: String,
    val updatedAt: String, val phone: String? = null, val email: String? = null, val address: String? = null)
@Entity(tableName = "purchases", foreignKeys = [ForeignKey(entity = SupplierEntity::class, parentColumns = ["id"], childColumns = ["supplierId"]),
    ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["createdBy"]),
    ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["validatedBy"]),
    ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["cancelledBy"])],
    indices = [Index(value = ["reference"], unique = true), Index(value = ["idempotencyKey"], unique = true),
        Index("supplierId"), Index("createdBy"), Index("validatedBy"), Index("cancelledBy")])
internal data class PurchaseEntity(@PrimaryKey val id: Long, val reference: String, val supplierId: Long?, val status: String,
    val totalAmount: Double, val createdBy: Long, val createdAt: String, val idempotencyKey: String? = null,
    val supplierInvoice: String? = null, val note: String? = null, val validatedBy: Long? = null, val validatedAt: String? = null,
    val cancelledBy: Long? = null, val cancelledAt: String? = null, val cancellationReason: String? = null)
@Entity(tableName = "purchase_items", foreignKeys = [ForeignKey(entity = PurchaseEntity::class, parentColumns = ["id"], childColumns = ["purchaseId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"])],
    indices = [Index(value = ["purchaseId", "productId"], unique = true), Index("productId")])
internal data class PurchaseLineEntity(@PrimaryKey val id: Long, val purchaseId: Long, val productId: Long, val quantity: Long, val unitCost: Double, val totalLine: Double)
@Entity(tableName = "inventory_counts", foreignKeys = [ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["createdBy"]),
    ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["validatedBy"])],
    indices = [Index(value = ["reference"], unique = true), Index("createdBy"), Index("validatedBy")])
internal data class InventoryEntity(@PrimaryKey val id: Long, val reference: String, val status: String, val createdBy: Long,
    val createdAt: String, val note: String? = null, val validatedBy: Long? = null, val validatedAt: String? = null)
@Entity(tableName = "inventory_count_lines", foreignKeys = [ForeignKey(entity = InventoryEntity::class, parentColumns = ["id"], childColumns = ["inventoryId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"])],
    indices = [Index(value = ["inventoryId", "productId"], unique = true), Index("productId")])
internal data class InventoryLineEntity(@PrimaryKey val id: Long, val inventoryId: Long, val productId: Long, val expectedQuantity: Long, val countedQuantity: Long)

@Entity(tableName = "attendances", foreignKeys = [ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"]),
    ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["correctedBy"])],
    indices = [Index(value = ["sessionRef"], unique = true), Index("userId"), Index("correctedBy")])
internal data class AttendanceEntity(@PrimaryKey val id: Long, val userId: Long, val startTime: String, val source: String,
    val status: String, val endTime: String? = null, val sessionRef: String? = null, val originalStartTime: String? = null,
    val originalEndTime: String? = null, val correctionReason: String? = null, val correctedBy: Long? = null, val correctedAt: String? = null)
@Entity(tableName = "messages", foreignKeys = [ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["senderId"]),
    ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["recipientId"])],
    indices = [Index(value = ["requestId"], unique = true), Index("senderId"), Index("recipientId")])
internal data class MessageEntity(@PrimaryKey val id: Long, val senderId: Long?, val recipientType: String, val recipientId: Long?,
    val subject: String, val content: String, val type: String, val isRead: Boolean, val createdAt: String, val requestId: String? = null)
@Entity(tableName = "message_reads", primaryKeys = ["messageId", "userId"],
    foreignKeys = [ForeignKey(entity = MessageEntity::class, parentColumns = ["id"], childColumns = ["messageId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE)], indices = [Index("userId")])
internal data class MessageReadEntity(val messageId: Long, val userId: Long, val readAt: String)
@Entity(tableName = "message_deletions", primaryKeys = ["messageId", "userId"],
    foreignKeys = [ForeignKey(entity = MessageEntity::class, parentColumns = ["id"], childColumns = ["messageId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE)], indices = [Index("userId")])
internal data class MessageDeletionEntity(val messageId: Long, val userId: Long, val deletedAt: String)
@Entity(tableName = "notifications", foreignKeys = [ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"])], indices = [Index("productId")])
internal data class NotificationEntity(@PrimaryKey val id: Long, val type: String, val productId: Long?, val message: String,
    val isRead: Boolean, val createdAt: String, val resolvedAt: String? = null)
@Entity(tableName = "email_report_logs")
internal data class EmailEntity(@PrimaryKey val id: Long, val recipient: String, val subject: String, val filename: String,
    val message: String, val attachment: ByteArray?, val status: String, val attempts: Int, val createdAt: String,
    val nextAttemptAt: String? = null, val lastError: String? = null, val sentAt: String? = null)
@Entity(tableName = "settings")
internal data class SettingEntity(@PrimaryKey val key: String, val value: String)
// Actor/responsible IDs intentionally not FKs: historical attribution survives deletion.
@Entity(tableName = "audit_logs")
internal data class AuditEntity(@PrimaryKey val id: Long, val userId: Long?, val action: String, val entity: String,
    val entityId: String?, val details: String?, val outcome: String, val createdAt: String,
    val responsibleId: Long? = null, val responsibleName: String? = null, val cashReference: String? = null,
    val cashAmount: Double? = null, val cashCurrency: String? = null)

@Entity(tableName = "generation_metadata")
internal data class GenerationEntity(@PrimaryKey val id: String, val createdAt: String)
@Entity(tableName = "pending_commands", indices = [Index(value = ["commandKey"], unique = true)])
internal data class PendingCommandEntity(@PrimaryKey val id: String, val commandKey: String, val actorId: Long,
    val cashId: Long, val canonicalVersion: Int, val canonicalRequest: String, val state: String, val createdAt: String)
