package com.vibe.store.application.persistence

import com.vibe.store.domain.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

// Trusted application ports, deliberately absent from application-api/UI DTOs.
interface IdentityRepository { suspend fun account(id: Long): AccountRecord? }
interface CashRepository { suspend fun session(id: Long): CashRecord? }
interface CatalogRepository { suspend fun product(id: Long): ProductRecord? }
interface SalesRepository { suspend fun invoice(id: String): InvoiceRecord? }
interface PurchaseRepository { suspend fun purchase(id: Long): PurchaseRecord? }
interface InventoryRepository { suspend fun inventory(id: Long): InventoryRecord? }
interface AttendanceRepository { suspend fun attendance(id: Long): AttendanceRecord? }
interface MessagingRepository { suspend fun message(id: Long): MessageRecord? }
interface AuditRepository { suspend fun audit(id: Long): AuditRecord? }
interface SettingsReader { suspend fun value(key: String): String? }
interface SettingsWriter : SettingsReader { suspend fun put(key: String, value: String) }

interface ReadRepositories {
    val identity: IdentityRepository; val cash: CashRepository; val catalog: CatalogRepository
    val sales: SalesRepository; val purchases: PurchaseRepository; val inventories: InventoryRepository
    val attendance: AttendanceRepository; val messaging: MessagingRepository; val audit: AuditRepository
    val settings: SettingsReader
}
interface TransactionRepositories : ReadRepositories { override val settings: SettingsWriter }
interface UnitOfWork { suspend fun <T> transaction(block: suspend TransactionRepositories.() -> T): T }
data class IntegrityResult(val quick: Boolean, val full: Boolean, val foreignKeys: Boolean)
interface DatabaseOwner {
    val generation: GenerationId
    suspend fun <T> read(block: suspend ReadRepositories.() -> T): T
    suspend fun verifyIntegrity(): IntegrityResult
    suspend fun checkpointCloseReopen()
    suspend fun close()
}
class CommandCoordinator(private val unitOfWork: UnitOfWork) {
    private val mutations = Mutex()
    // No retries, detached jobs or external-effect orchestration.
    suspend fun <T> execute(block: suspend TransactionRepositories.() -> T): T {
        check(currentCoroutineContext()[CommandLease] == null) { "Nested command rejected" }
        return mutations.withLock { withContext(CommandLease()) { coroutineScope { unitOfWork.transaction(block) } } }
    }
}
private class CommandLease : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<CommandLease>
}
