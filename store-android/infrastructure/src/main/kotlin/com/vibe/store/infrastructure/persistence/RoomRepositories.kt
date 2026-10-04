package com.vibe.store.infrastructure.persistence

import com.vibe.store.application.persistence.*
import com.vibe.store.domain.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext

internal class RoomRepositories(internal val dao: StoreDao, private val job: Job?, private val writable: Boolean) : TransactionRepositories {
    override val cashOperations = RoomCashOperations(dao, ::check)
    override val saleOperations = RoomSaleOperations(dao, ::check)
    override val security: com.vibe.store.application.security.SecurityRepository = RoomSecurityRepository(dao, ::check)
    private var active = true
    fun release() { active = false }
    private suspend fun check(write: Boolean = false) {
        check(active && job === currentCoroutineContext()[Job]) { "Expired or detached persistence scope" }
        check(!write || writable) { "Read scope cannot mutate" }
    }
    override val identity = object : IdentityRepository {
        override suspend fun account(id: Long): AccountRecord? { check(); return dao.account(id)?.let { AccountRecord(it.id, it.username, it.active) } }
    }
    override val cash = object : CashRepository {
        override suspend fun session(id: Long): CashRecord? { check(); return dao.session(id)?.let { CashRecord(it.id, it.userId, it.status, MoneyValue(it.openingAmount)) } }
    }
    override val catalog: CatalogRepository = RoomCatalogRepository(dao, ::check)
    override val sales = object : SalesRepository {
        override suspend fun invoice(id: String): InvoiceRecord? { check(); return dao.findInvoice(id)?.let { InvoiceRecord(it.id, it.userId, it.cashId, MoneyValue(it.totalAmount), it.status) } }
    }
    override val purchases = object : PurchaseRepository {
        override suspend fun purchase(id: Long): PurchaseRecord? { check(); return dao.findPurchase(id)?.let { PurchaseRecord(it.id, it.supplierId, it.status, MoneyValue(it.totalAmount)) } }
    }
    override val inventories = object : InventoryRepository {
        override suspend fun inventory(id: Long): InventoryRecord? { check(); return dao.findInventory(id)?.let { InventoryRecord(it.id, it.status) } }
    }
    override val employees: EmployeeRepository = RoomEmployeeRepository(dao, ::check)
    override val attendance: AttendanceRepository = RoomAttendanceRepository(dao, ::check)
    override val messaging = object : MessagingRepository {
        override suspend fun message(id: Long): MessageRecord? { check(); return dao.findMessage(id)?.let { MessageRecord(it.id, it.subject, it.content) } }
    }
    override val audit = object : AuditRepository {
        override suspend fun audit(id: Long): AuditRecord? { check(); return dao.findAudit(id)?.let { AuditRecord(it.id, it.action, it.responsibleName, it.cashAmount?.let(::MoneyValue)) } }
    }
    override val settings = object : SettingsWriter {
        override suspend fun value(key: String): String? { check(); return dao.settingValue(key) }
        override suspend fun put(key: String, value: String) { check(true); dao.setting(SettingEntity(key, value)) }
    }
}
