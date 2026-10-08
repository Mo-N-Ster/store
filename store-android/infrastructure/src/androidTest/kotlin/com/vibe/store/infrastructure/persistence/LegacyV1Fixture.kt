package com.vibe.store.infrastructure.persistence

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File

/** Synthetic v1 fixture from committed StoreDatabase/1.json; no user database access. */
internal fun legacyV1(root: File, corrupt: Boolean = false) {
    check(root.isDirectory || root.mkdirs())
    BundledSQLiteDriver().open(File(root, "store.db").absolutePath).use { connection ->
        connection.execSQL("PRAGMA foreign_keys=ON")
        connection.execSQL("BEGIN IMMEDIATE")
        try {
            legacyV1Schema.forEach { connection.execSQL(it) }
            legacyV1Rows.forEach { connection.execSQL(it) }
            legacyV1Triggers.forEach { connection.execSQL(it) }
            if (corrupt) connection.execSQL("UPDATE products SET price='corrupt'")
            connection.execSQL("PRAGMA user_version=1")
            connection.execSQL("COMMIT")
        } catch (failure: Throwable) { connection.execSQL("ROLLBACK"); throw failure }
    }
}

private val legacyV1Schema = listOf(
    """CREATE TABLE IF NOT EXISTS `users` (`id` INTEGER NOT NULL, `username` TEXT NOT NULL, `passwordHash` TEXT NOT NULL, `role` TEXT NOT NULL, `firstName` TEXT NOT NULL, `lastName` TEXT NOT NULL, `initials` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `email` TEXT, `phone` TEXT, `hireDate` TEXT, `active` INTEGER NOT NULL, `securityQuestion` TEXT, `securityAnswerHash` TEXT, `photo` TEXT, `failedLoginAttempts` INTEGER NOT NULL, `lockedUntil` TEXT, `lastLoginAt` TEXT, `failedRecoveryAttempts` INTEGER NOT NULL, `recoveryLockedUntil` TEXT, PRIMARY KEY(`id`))""",
    """CREATE TABLE IF NOT EXISTS `roles` (`id` INTEGER NOT NULL, `code` TEXT NOT NULL, `name` TEXT NOT NULL, `isSystem` INTEGER NOT NULL, `createdAt` TEXT NOT NULL, PRIMARY KEY(`id`))""",
    """CREATE TABLE IF NOT EXISTS `permissions` (`id` INTEGER NOT NULL, `module` TEXT NOT NULL, `action` TEXT NOT NULL, `createdAt` TEXT NOT NULL, PRIMARY KEY(`id`))""",
    """CREATE TABLE IF NOT EXISTS `role_permissions` (`roleId` INTEGER NOT NULL, `permissionId` INTEGER NOT NULL, PRIMARY KEY(`roleId`, `permissionId`), FOREIGN KEY(`roleId`) REFERENCES `roles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`permissionId`) REFERENCES `permissions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
    """CREATE TABLE IF NOT EXISTS `user_roles` (`userId` INTEGER NOT NULL, `roleId` INTEGER NOT NULL, `assignedAt` TEXT NOT NULL, PRIMARY KEY(`userId`), FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`roleId`) REFERENCES `roles`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""",
    """CREATE TABLE IF NOT EXISTS `user_permission_denials` (`userId` INTEGER NOT NULL, `permissionId` INTEGER NOT NULL, PRIMARY KEY(`userId`, `permissionId`), FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`permissionId`) REFERENCES `permissions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
    """CREATE TABLE IF NOT EXISTS `employees` (`id` INTEGER NOT NULL, `code` TEXT NOT NULL, `userId` INTEGER, `firstName` TEXT NOT NULL, `lastName` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, `phone` TEXT, `email` TEXT, `address` TEXT, `hireDate` TEXT, `endDate` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )""",
    """CREATE TABLE IF NOT EXISTS `cash_sessions` (`id` INTEGER NOT NULL, `reference` TEXT NOT NULL, `userId` INTEGER NOT NULL, `status` TEXT NOT NULL, `openingAmount` REAL NOT NULL, `openedAt` TEXT NOT NULL, `closedAt` TEXT, `closingAmount` REAL, `expectedAmount` REAL, `difference` REAL, `closedBy` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`closedBy`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""",
    """CREATE TABLE IF NOT EXISTS `products` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `price` REAL NOT NULL, `stock` INTEGER NOT NULL, `minimumStock` INTEGER NOT NULL, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, `reference` TEXT, `hashtag` TEXT, `description` TEXT NOT NULL, `deletedAt` TEXT, `imageRef` TEXT, PRIMARY KEY(`id`))""",
    """CREATE TABLE IF NOT EXISTS `product_price_history` (`id` INTEGER NOT NULL, `productId` INTEGER NOT NULL, `price` REAL NOT NULL, `recordedAt` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""",
    """CREATE TABLE IF NOT EXISTS `stock_movements` (`id` INTEGER NOT NULL, `productId` INTEGER NOT NULL, `quantity` INTEGER NOT NULL, `reason` TEXT NOT NULL, `unitPrice` REAL NOT NULL, `createdAt` TEXT NOT NULL, `referenceId` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""",
    """CREATE TABLE IF NOT EXISTS `invoices` (`id` TEXT NOT NULL, `userId` INTEGER NOT NULL, `cashId` INTEGER, `invoiceDate` TEXT NOT NULL, `subtotal` REAL NOT NULL, `totalAmount` REAL NOT NULL, `discount` REAL NOT NULL, `status` TEXT NOT NULL, `idempotencyKey` TEXT, `canonicalVersion` INTEGER, `canonicalRequest` TEXT, `cancelledBy` INTEGER, `cancelledAt` TEXT, `cancellationReason` TEXT, `storeName` TEXT, `storeAddress` TEXT, `storePhone` TEXT, `storeEmail` TEXT, `currency` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`cashId`) REFERENCES `cash_sessions`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`cancelledBy`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""",
    """CREATE TABLE IF NOT EXISTS `invoice_lines` (`id` INTEGER NOT NULL, `invoiceId` TEXT NOT NULL, `productId` INTEGER, `productName` TEXT NOT NULL, `category` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `unitPrice` REAL NOT NULL, `totalLine` REAL NOT NULL, `unitCost` REAL, PRIMARY KEY(`id`), FOREIGN KEY(`invoiceId`) REFERENCES `invoices`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""",
    """CREATE TABLE IF NOT EXISTS `payments` (`id` INTEGER NOT NULL, `invoiceId` TEXT NOT NULL, `cashId` INTEGER NOT NULL, `method` TEXT NOT NULL, `amount` REAL NOT NULL, `received` REAL NOT NULL, `change` REAL NOT NULL, `status` TEXT NOT NULL, `createdAt` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`invoiceId`) REFERENCES `invoices`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`cashId`) REFERENCES `cash_sessions`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""",
    """CREATE TABLE IF NOT EXISTS `suppliers` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `active` INTEGER NOT NULL, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, `phone` TEXT, `email` TEXT, `address` TEXT, PRIMARY KEY(`id`))""",
    """CREATE TABLE IF NOT EXISTS `purchases` (`id` INTEGER NOT NULL, `reference` TEXT NOT NULL, `supplierId` INTEGER, `status` TEXT NOT NULL, `totalAmount` REAL NOT NULL, `createdBy` INTEGER NOT NULL, `createdAt` TEXT NOT NULL, `idempotencyKey` TEXT, `supplierInvoice` TEXT, `note` TEXT, `validatedBy` INTEGER, `validatedAt` TEXT, `cancelledBy` INTEGER, `cancelledAt` TEXT, `cancellationReason` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`supplierId`) REFERENCES `suppliers`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`createdBy`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`validatedBy`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`cancelledBy`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""",
    """CREATE TABLE IF NOT EXISTS `purchase_items` (`id` INTEGER NOT NULL, `purchaseId` INTEGER NOT NULL, `productId` INTEGER NOT NULL, `quantity` INTEGER NOT NULL, `unitCost` REAL NOT NULL, `totalLine` REAL NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`purchaseId`) REFERENCES `purchases`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""",
    """CREATE TABLE IF NOT EXISTS `inventory_counts` (`id` INTEGER NOT NULL, `reference` TEXT NOT NULL, `status` TEXT NOT NULL, `createdBy` INTEGER NOT NULL, `createdAt` TEXT NOT NULL, `note` TEXT, `validatedBy` INTEGER, `validatedAt` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`createdBy`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`validatedBy`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""",
    """CREATE TABLE IF NOT EXISTS `inventory_count_lines` (`id` INTEGER NOT NULL, `inventoryId` INTEGER NOT NULL, `productId` INTEGER NOT NULL, `expectedQuantity` INTEGER NOT NULL, `countedQuantity` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`inventoryId`) REFERENCES `inventory_counts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""",
    """CREATE TABLE IF NOT EXISTS `attendances` (`id` INTEGER NOT NULL, `userId` INTEGER NOT NULL, `startTime` TEXT NOT NULL, `source` TEXT NOT NULL, `status` TEXT NOT NULL, `endTime` TEXT, `sessionRef` TEXT, `originalStartTime` TEXT, `originalEndTime` TEXT, `correctionReason` TEXT, `correctedBy` INTEGER, `correctedAt` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`correctedBy`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""",
    """CREATE TABLE IF NOT EXISTS `messages` (`id` INTEGER NOT NULL, `senderId` INTEGER, `recipientType` TEXT NOT NULL, `recipientId` INTEGER, `subject` TEXT NOT NULL, `content` TEXT NOT NULL, `type` TEXT NOT NULL, `isRead` INTEGER NOT NULL, `createdAt` TEXT NOT NULL, `requestId` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`senderId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`recipientId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""",
    """CREATE TABLE IF NOT EXISTS `message_reads` (`messageId` INTEGER NOT NULL, `userId` INTEGER NOT NULL, `readAt` TEXT NOT NULL, PRIMARY KEY(`messageId`, `userId`), FOREIGN KEY(`messageId`) REFERENCES `messages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
    """CREATE TABLE IF NOT EXISTS `message_deletions` (`messageId` INTEGER NOT NULL, `userId` INTEGER NOT NULL, `deletedAt` TEXT NOT NULL, PRIMARY KEY(`messageId`, `userId`), FOREIGN KEY(`messageId`) REFERENCES `messages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
    """CREATE TABLE IF NOT EXISTS `notifications` (`id` INTEGER NOT NULL, `type` TEXT NOT NULL, `productId` INTEGER, `message` TEXT NOT NULL, `isRead` INTEGER NOT NULL, `createdAt` TEXT NOT NULL, `resolvedAt` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""",
    """CREATE TABLE IF NOT EXISTS `email_report_logs` (`id` INTEGER NOT NULL, `recipient` TEXT NOT NULL, `subject` TEXT NOT NULL, `filename` TEXT NOT NULL, `message` TEXT NOT NULL, `attachment` BLOB, `status` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `createdAt` TEXT NOT NULL, `nextAttemptAt` TEXT, `lastError` TEXT, `sentAt` TEXT, PRIMARY KEY(`id`))""",
    """CREATE TABLE IF NOT EXISTS `settings` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))""",
    """CREATE TABLE IF NOT EXISTS `audit_logs` (`id` INTEGER NOT NULL, `userId` INTEGER, `action` TEXT NOT NULL, `entity` TEXT NOT NULL, `entityId` TEXT, `details` TEXT, `outcome` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `responsibleId` INTEGER, `responsibleName` TEXT, `cashReference` TEXT, `cashAmount` REAL, `cashCurrency` TEXT, PRIMARY KEY(`id`))""",
    """CREATE TABLE IF NOT EXISTS `generation_metadata` (`id` TEXT NOT NULL, `createdAt` TEXT NOT NULL, PRIMARY KEY(`id`))""",
    """CREATE TABLE IF NOT EXISTS `pending_commands` (`id` TEXT NOT NULL, `commandKey` TEXT NOT NULL, `actorId` INTEGER NOT NULL, `cashId` INTEGER NOT NULL, `canonicalVersion` INTEGER NOT NULL, `canonicalRequest` TEXT NOT NULL, `state` TEXT NOT NULL, `createdAt` TEXT NOT NULL, PRIMARY KEY(`id`))""",
    """CREATE UNIQUE INDEX IF NOT EXISTS `index_users_username` ON `users` (`username`)""",
    """CREATE UNIQUE INDEX IF NOT EXISTS `index_users_email` ON `users` (`email`)""",
    """CREATE UNIQUE INDEX IF NOT EXISTS `index_roles_code` ON `roles` (`code`)""",
    """CREATE UNIQUE INDEX IF NOT EXISTS `index_permissions_module_action` ON `permissions` (`module`, `action`)""",
    """CREATE INDEX IF NOT EXISTS `index_role_permissions_permissionId` ON `role_permissions` (`permissionId`)""",
    """CREATE INDEX IF NOT EXISTS `index_user_roles_roleId` ON `user_roles` (`roleId`)""",
    """CREATE INDEX IF NOT EXISTS `index_user_permission_denials_permissionId` ON `user_permission_denials` (`permissionId`)""",
    """CREATE UNIQUE INDEX IF NOT EXISTS `index_employees_code` ON `employees` (`code`)""",
    """CREATE UNIQUE INDEX IF NOT EXISTS `index_employees_userId` ON `employees` (`userId`)""",
    """CREATE UNIQUE INDEX IF NOT EXISTS `index_cash_sessions_reference` ON `cash_sessions` (`reference`)""",
    """CREATE INDEX IF NOT EXISTS `index_cash_sessions_userId` ON `cash_sessions` (`userId`)""",
    """CREATE INDEX IF NOT EXISTS `index_cash_sessions_closedBy` ON `cash_sessions` (`closedBy`)""",
    """CREATE UNIQUE INDEX IF NOT EXISTS `index_products_reference` ON `products` (`reference`)""",
    """CREATE INDEX IF NOT EXISTS `index_product_price_history_productId` ON `product_price_history` (`productId`)""",
    """CREATE INDEX IF NOT EXISTS `index_stock_movements_productId` ON `stock_movements` (`productId`)""",
    """CREATE UNIQUE INDEX IF NOT EXISTS `index_invoices_idempotencyKey` ON `invoices` (`idempotencyKey`)""",
    """CREATE INDEX IF NOT EXISTS `index_invoices_userId` ON `invoices` (`userId`)""",
    """CREATE INDEX IF NOT EXISTS `index_invoices_cashId` ON `invoices` (`cashId`)""",
    """CREATE INDEX IF NOT EXISTS `index_invoices_cancelledBy` ON `invoices` (`cancelledBy`)""",
    """CREATE INDEX IF NOT EXISTS `index_invoice_lines_invoiceId` ON `invoice_lines` (`invoiceId`)""",
    """CREATE INDEX IF NOT EXISTS `index_invoice_lines_productId` ON `invoice_lines` (`productId`)""",
    """CREATE UNIQUE INDEX IF NOT EXISTS `index_payments_invoiceId` ON `payments` (`invoiceId`)""",
    """CREATE INDEX IF NOT EXISTS `index_payments_cashId` ON `payments` (`cashId`)""",
    """CREATE UNIQUE INDEX IF NOT EXISTS `index_suppliers_name` ON `suppliers` (`name`)""",
    """CREATE UNIQUE INDEX IF NOT EXISTS `index_purchases_reference` ON `purchases` (`reference`)""",
    """CREATE UNIQUE INDEX IF NOT EXISTS `index_purchases_idempotencyKey` ON `purchases` (`idempotencyKey`)""",
    """CREATE INDEX IF NOT EXISTS `index_purchases_supplierId` ON `purchases` (`supplierId`)""",
    """CREATE INDEX IF NOT EXISTS `index_purchases_createdBy` ON `purchases` (`createdBy`)""",
    """CREATE INDEX IF NOT EXISTS `index_purchases_validatedBy` ON `purchases` (`validatedBy`)""",
    """CREATE INDEX IF NOT EXISTS `index_purchases_cancelledBy` ON `purchases` (`cancelledBy`)""",
    """CREATE UNIQUE INDEX IF NOT EXISTS `index_purchase_items_purchaseId_productId` ON `purchase_items` (`purchaseId`, `productId`)""",
    """CREATE INDEX IF NOT EXISTS `index_purchase_items_productId` ON `purchase_items` (`productId`)""",
    """CREATE UNIQUE INDEX IF NOT EXISTS `index_inventory_counts_reference` ON `inventory_counts` (`reference`)""",
    """CREATE INDEX IF NOT EXISTS `index_inventory_counts_createdBy` ON `inventory_counts` (`createdBy`)""",
    """CREATE INDEX IF NOT EXISTS `index_inventory_counts_validatedBy` ON `inventory_counts` (`validatedBy`)""",
    """CREATE UNIQUE INDEX IF NOT EXISTS `index_inventory_count_lines_inventoryId_productId` ON `inventory_count_lines` (`inventoryId`, `productId`)""",
    """CREATE INDEX IF NOT EXISTS `index_inventory_count_lines_productId` ON `inventory_count_lines` (`productId`)""",
    """CREATE UNIQUE INDEX IF NOT EXISTS `index_attendances_sessionRef` ON `attendances` (`sessionRef`)""",
    """CREATE INDEX IF NOT EXISTS `index_attendances_userId` ON `attendances` (`userId`)""",
    """CREATE INDEX IF NOT EXISTS `index_attendances_correctedBy` ON `attendances` (`correctedBy`)""",
    """CREATE UNIQUE INDEX IF NOT EXISTS `index_messages_requestId` ON `messages` (`requestId`)""",
    """CREATE INDEX IF NOT EXISTS `index_messages_senderId` ON `messages` (`senderId`)""",
    """CREATE INDEX IF NOT EXISTS `index_messages_recipientId` ON `messages` (`recipientId`)""",
    """CREATE INDEX IF NOT EXISTS `index_message_reads_userId` ON `message_reads` (`userId`)""",
    """CREATE INDEX IF NOT EXISTS `index_message_deletions_userId` ON `message_deletions` (`userId`)""",
    """CREATE INDEX IF NOT EXISTS `index_notifications_productId` ON `notifications` (`productId`)""",
    """CREATE UNIQUE INDEX IF NOT EXISTS `index_pending_commands_commandKey` ON `pending_commands` (`commandKey`)""",
)

private val legacyV1Rows = listOf(
    """INSERT INTO users (id,username,passwordHash,role,firstName,lastName,initials,createdAt,active,failedLoginAttempts,failedRecoveryAttempts) VALUES (1,'synthetic','NOT-A-REAL-HASH','owner','Test','Only','TO','2026-01-02',1,0,0)""",
    """INSERT INTO cash_sessions (id,reference,userId,status,openingAmount,openedAt,closingAmount,expectedAmount,difference) VALUES (1,'SYN-CASH',1,'CLOSED',20.1,'2026-01-02',20,20.1,-0.1)""",
    """INSERT INTO products (id,name,category,price,stock,minimumStock,createdAt,updatedAt,description) VALUES (1,'Synthetic','Test',12.3456789,8,2,'2026-01-02','2026-01-02','')""",
    """INSERT INTO product_price_history VALUES (1,1,12.5,'2026-01-02')""",
    """INSERT INTO stock_movements VALUES (1,1,-2,'sale',12.5,'2026-01-02','SYN-INV')""",
    """INSERT INTO invoices (id,userId,cashId,invoiceDate,subtotal,totalAmount,discount,status,idempotencyKey,canonicalVersion,canonicalRequest,storeName,currency) VALUES ('SYN-INV',1,1,'2026-01-02',25,24.9,0.1,'validated','SYN-KEY',1,'synthetic-command','Historical store','XAF')""",
    """INSERT INTO invoice_lines VALUES (1,'SYN-INV',1,'Historical product','Historical category',2,12.5,25,NULL)""",
    """INSERT INTO payments VALUES (1,'SYN-INV',1,'CASH',24.9,30,5.1,'CAPTURED','2026-01-02')""",
    """INSERT INTO suppliers (id,name,active,createdAt,updatedAt) VALUES (1,'Synthetic supplier',1,'2026-01-02','2026-01-02')""",
    """INSERT INTO purchases (id,reference,supplierId,status,totalAmount,createdBy,createdAt,idempotencyKey) VALUES (1,'SYN-PUR',1,'DRAFT',0.999,1,'2026-01-02','SYN-PUR-KEY')""",
    """INSERT INTO purchase_items VALUES (1,1,1,3,0.333,0.999)""",
    """INSERT INTO audit_logs (id,userId,action,entity,entityId,details,outcome,createdAt,responsibleName,cashAmount) VALUES (1,1,'SYNTHETIC','test','1','Historical details','SUCCESS','2026-01-02','Historical responsible',-0.1)""",
    """INSERT INTO pending_commands VALUES ('SYN-PENDING','SYN-KEY',1,1,1,'synthetic-command','SUBMITTED','2026-01-02')""",
    """INSERT INTO settings VALUES ('synthetic','v1')""",
    """INSERT INTO generation_metadata VALUES ('main','2026-01-02')""",
)

private val legacyV1Triggers = listOf(
    """CREATE TRIGGER check_users_INSERT BEFORE INSERT ON `users` WHEN NOT (NEW.role IN ('owner','manager','employee') AND NEW.active IN (0,1)) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END""",
    """CREATE TRIGGER check_users_UPDATE BEFORE UPDATE ON `users` WHEN NOT (NEW.role IN ('owner','manager','employee') AND NEW.active IN (0,1)) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END""",
    """CREATE TRIGGER check_roles_INSERT BEFORE INSERT ON `roles` WHEN NOT (NEW.isSystem IN (0,1)) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END""",
    """CREATE TRIGGER check_roles_UPDATE BEFORE UPDATE ON `roles` WHEN NOT (NEW.isSystem IN (0,1)) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END""",
    """CREATE TRIGGER check_employees_INSERT BEFORE INSERT ON `employees` WHEN NOT (NEW.status IN ('ACTIVE','ABSENT','SUSPENDED','RESIGNED','ARCHIVED')) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END""",
    """CREATE TRIGGER check_employees_UPDATE BEFORE UPDATE ON `employees` WHEN NOT (NEW.status IN ('ACTIVE','ABSENT','SUSPENDED','RESIGNED','ARCHIVED')) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END""",
    """CREATE TRIGGER check_cash_sessions_INSERT BEFORE INSERT ON `cash_sessions` WHEN NOT (NEW.status IN ('OPEN','CLOSED') AND NEW.openingAmount>=0 AND (NEW.closingAmount IS NULL OR NEW.closingAmount>=0)) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END""",
    """CREATE TRIGGER check_cash_sessions_UPDATE BEFORE UPDATE ON `cash_sessions` WHEN NOT (NEW.status IN ('OPEN','CLOSED') AND NEW.openingAmount>=0 AND (NEW.closingAmount IS NULL OR NEW.closingAmount>=0)) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END""",
    """CREATE TRIGGER check_products_INSERT BEFORE INSERT ON `products` WHEN NOT (NEW.price>=0 AND typeof(NEW.stock)='integer' AND NEW.stock BETWEEN 0 AND 9007199254740991 AND NEW.minimumStock>=0) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END""",
    """CREATE TRIGGER check_products_UPDATE BEFORE UPDATE ON `products` WHEN NOT (NEW.price>=0 AND typeof(NEW.stock)='integer' AND NEW.stock BETWEEN 0 AND 9007199254740991 AND NEW.minimumStock>=0) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END""",
    """CREATE TRIGGER check_product_price_history_INSERT BEFORE INSERT ON `product_price_history` WHEN NOT (NEW.price>=0) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END""",
    """CREATE TRIGGER check_product_price_history_UPDATE BEFORE UPDATE ON `product_price_history` WHEN NOT (NEW.price>=0) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END""",
    """CREATE TRIGGER check_inventory_counts_INSERT BEFORE INSERT ON `inventory_counts` WHEN NOT (NEW.status IN ('DRAFT','VALIDATED','CANCELLED')) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END""",
    """CREATE TRIGGER check_inventory_counts_UPDATE BEFORE UPDATE ON `inventory_counts` WHEN NOT (NEW.status IN ('DRAFT','VALIDATED','CANCELLED')) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END""",
    """CREATE TRIGGER check_inventory_count_lines_INSERT BEFORE INSERT ON `inventory_count_lines` WHEN NOT (typeof(NEW.expectedQuantity)='integer' AND typeof(NEW.countedQuantity)='integer' AND NEW.expectedQuantity>=0 AND NEW.countedQuantity>=0) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END""",
    """CREATE TRIGGER check_inventory_count_lines_UPDATE BEFORE UPDATE ON `inventory_count_lines` WHEN NOT (typeof(NEW.expectedQuantity)='integer' AND typeof(NEW.countedQuantity)='integer' AND NEW.expectedQuantity>=0 AND NEW.countedQuantity>=0) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END""",
    """CREATE TRIGGER check_suppliers_INSERT BEFORE INSERT ON `suppliers` WHEN NOT (NEW.active IN (0,1)) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END""",
    """CREATE TRIGGER check_suppliers_UPDATE BEFORE UPDATE ON `suppliers` WHEN NOT (NEW.active IN (0,1)) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END""",
    """CREATE TRIGGER check_purchases_INSERT BEFORE INSERT ON `purchases` WHEN NOT (NEW.status IN ('DRAFT','VALIDATED','CANCELLED') AND NEW.totalAmount>=0) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END""",
    """CREATE TRIGGER check_purchases_UPDATE BEFORE UPDATE ON `purchases` WHEN NOT (NEW.status IN ('DRAFT','VALIDATED','CANCELLED') AND NEW.totalAmount>=0) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END""",
    """CREATE TRIGGER check_purchase_items_INSERT BEFORE INSERT ON `purchase_items` WHEN NOT (typeof(NEW.quantity)='integer' AND NEW.quantity BETWEEN 1 AND 9007199254740991 AND NEW.unitCost>=0 AND NEW.totalLine>=0) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END""",
    """CREATE TRIGGER check_purchase_items_UPDATE BEFORE UPDATE ON `purchase_items` WHEN NOT (typeof(NEW.quantity)='integer' AND NEW.quantity BETWEEN 1 AND 9007199254740991 AND NEW.unitCost>=0 AND NEW.totalLine>=0) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END""",
    """CREATE TRIGGER check_invoices_INSERT BEFORE INSERT ON `invoices` WHEN NOT (NEW.status IN ('validated','cancelled') AND ((NEW.canonicalRequest IS NULL AND NEW.canonicalVersion IS NULL) OR (NEW.canonicalRequest IS NOT NULL AND NEW.canonicalVersion IS NOT NULL AND NEW.canonicalVersion>0))) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END""",
    """CREATE TRIGGER check_invoices_UPDATE BEFORE UPDATE ON `invoices` WHEN NOT (NEW.status IN ('validated','cancelled') AND ((NEW.canonicalRequest IS NULL AND NEW.canonicalVersion IS NULL) OR (NEW.canonicalRequest IS NOT NULL AND NEW.canonicalVersion IS NOT NULL AND NEW.canonicalVersion>0))) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END""",
    """CREATE TRIGGER check_invoice_lines_INSERT BEFORE INSERT ON `invoice_lines` WHEN NOT (typeof(NEW.quantity)='integer' AND NEW.quantity BETWEEN 1 AND 9007199254740991) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END""",
    """CREATE TRIGGER check_invoice_lines_UPDATE BEFORE UPDATE ON `invoice_lines` WHEN NOT (typeof(NEW.quantity)='integer' AND NEW.quantity BETWEEN 1 AND 9007199254740991) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END""",
    """CREATE TRIGGER check_payments_INSERT BEFORE INSERT ON `payments` WHEN NOT (NEW.method='CASH' AND NEW.amount>=0 AND NEW.received>=0 AND NEW.change>=0 AND NEW.status IN ('CAPTURED','REFUNDED')) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END""",
    """CREATE TRIGGER check_payments_UPDATE BEFORE UPDATE ON `payments` WHEN NOT (NEW.method='CASH' AND NEW.amount>=0 AND NEW.received>=0 AND NEW.change>=0 AND NEW.status IN ('CAPTURED','REFUNDED')) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END""",
    """CREATE TRIGGER check_attendances_INSERT BEFORE INSERT ON `attendances` WHEN NOT (NEW.status IN ('VALID','CORRECTED','INTERRUPTED')) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END""",
    """CREATE TRIGGER check_attendances_UPDATE BEFORE UPDATE ON `attendances` WHEN NOT (NEW.status IN ('VALID','CORRECTED','INTERRUPTED')) BEGIN SELECT RAISE(ABORT, 'constraint violation'); END""",
    """CREATE TRIGGER cash_one_open_INSERT BEFORE INSERT ON cash_sessions WHEN NEW.status='OPEN' AND EXISTS(SELECT 1 FROM cash_sessions WHERE userId=NEW.userId AND status='OPEN' AND id!=NEW.id) BEGIN SELECT RAISE(ABORT, 'one open cash session'); END""",
    """CREATE TRIGGER cash_one_open_UPDATE BEFORE UPDATE ON cash_sessions WHEN NEW.status='OPEN' AND EXISTS(SELECT 1 FROM cash_sessions WHERE userId=NEW.userId AND status='OPEN' AND id!=NEW.id) BEGIN SELECT RAISE(ABORT, 'one open cash session'); END""",
)
