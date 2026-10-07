package com.vibe.store.api

data class PurchaseSummary(val id: Long, val reference: String, val supplierId: Long?,
    val status: PurchaseStatus, val total: Double, val createdBy: Long, val createdAt: String)
data class PurchaseDetail(val summary: PurchaseSummary, val supplierInvoice: String, val note: String,
    val validatedBy: Long?, val validatedAt: String?, val cancelledBy: Long?, val cancelledAt: String?,
    val cancellationReason: String?)
data class PurchaseLineView(val id: Long, val productId: Long, val quantity: Long,
    val unitCost: Double, val total: Double)
data class PurchaseFilter(val status: PurchaseStatus? = null, val supplierId: Long? = null,
    val productId: Long? = null, val from: String = "", val to: String = "",
    val page: WorkflowPageRequest = WorkflowPageRequest())
data class CreatePurchaseDraft(val supplierId: Long? = null, val supplierInvoice: String = "",
    val note: String = "", val idempotencyKey: String? = null)
data class SavePurchaseLine(val purchaseId: Long, val productId: Long, val quantity: Long,
    val unitCost: Double, val expectedLine: PurchaseLineView? = null)
data class ValidatePurchase(val purchaseId: Long)
data class CancelPurchase(val purchaseId: Long, val reason: String)
/** Deliberately no initialStock, actor, permissions or parent validation flag. */
data class PurchaseArticleCreation(val name: String, val category: String, val price: Double,
    val hashtag: String = "", val description: String = "", val minimumStock: Long = 0,
    val image: ImageEdit = ImageEdit.Keep)
interface PurchaseService {
    /** PURCHASES:READ, current trusted Owner/Manager. */
    suspend fun list(filter: PurchaseFilter = PurchaseFilter()): WorkflowPage<PurchaseSummary>
    suspend fun detail(id: Long): PurchaseDetail
    suspend fun lines(id: Long, page: WorkflowPageRequest = WorkflowPageRequest()): WorkflowPage<PurchaseLineView>
    /** PURCHASES:CREATE; every key collision is CONFLICT (D3), never replay SUCCESS. */
    suspend fun createDraft(command: CreatePurchaseDraft): PurchaseDetail
    /** PURCHASES:UPDATE; replaces the unique product line in DRAFT, never receives stock. */
    suspend fun saveLine(command: SavePurchaseLine): PurchaseLineView
    /** PURCHASES:VALIDATE; a new reception checks active supplier/admissible articles (D4). */
    suspend fun validate(command: ValidatePurchase): PurchaseDetail
    /** PURCHASES:DELETE; supplier inactivity does not block historical compensation. */
    suspend fun cancel(command: CancelPurchase): PurchaseDetail
    /** PRODUCTS:UPDATE; future adapter reuses CatalogService, forcing initialStock=0.
     * Independent creation, no implicit draft validation, no modification to general catalog behavior. */
    suspend fun createArticle(command: PurchaseArticleCreation): CatalogWrite
}
