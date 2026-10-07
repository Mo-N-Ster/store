package com.vibe.store.api

data class SupplierView(val id: Long, val name: String, val phone: String, val email: String,
    val address: String, val active: Boolean, val createdAt: String, val updatedAt: String)
data class SupplierFilter(val search: String = "", val active: Boolean? = true,
    val page: WorkflowPageRequest = WorkflowPageRequest())
data class SaveSupplier(val id: Long? = null, val name: String, val phone: String = "",
    val email: String = "", val address: String = "", val active: Boolean = true,
    val expectedUpdatedAt: String? = null)
interface SupplierService {
    /** PURCHASES:READ, trusted current Owner/Manager. No physical deletion endpoint. */
    suspend fun list(filter: SupplierFilter = SupplierFilter()): WorkflowPage<SupplierView>
    suspend fun detail(id: Long): SupplierView
    /** PURCHASES:UPDATE for BOTH creation and editing; uniqueness checked in the UoW. */
    suspend fun save(command: SaveSupplier): SupplierView
}
