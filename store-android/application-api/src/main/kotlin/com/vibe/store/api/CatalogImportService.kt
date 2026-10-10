package com.vibe.store.api

import java.math.BigDecimal
import java.util.Collections

/** Untrusted catalog values already decoded from a supported STORE_DATA_V1 envelope. */
data class CatalogImportProduct(
    val name: String,
    val hashtag: String,
    val category: String,
    val description: String,
    val price: BigDecimal,
    val stockQuantity: Long,
    val minStockThreshold: Long,
)

/** Input is bounded and copied; it is not proof of document authenticity or authorization. */
class CatalogImportRequest(products: List<CatalogImportProduct>) {
    val products: List<CatalogImportProduct>
    init {
        require(products.size <= 10_000)
        require(products.all {
            it.name.length <= 64_000 && it.hashtag.length <= 64_000 &&
                it.category.length <= 64_000 && it.description.length <= 64_000
        })
        this.products = Collections.unmodifiableList(ArrayList(products))
    }
}

enum class CatalogImportField { NAME, CATEGORY, HASHTAG, DESCRIPTION, PRICE, STOCK_QUANTITY, MIN_STOCK_THRESHOLD }
enum class CatalogImportIssueCode { REQUIRED, INVALID_PRICE, INVALID_QUANTITY, DUPLICATE_IN_FILE, ALREADY_EXISTS }
data class CatalogImportIssue(val row: Int, val field: CatalogImportField, val code: CatalogImportIssueCode)

/** Preview is immutable and session-scoped. Authenticity is deliberately never asserted. */
class CatalogImportPreview(
    val previewId: String?,
    products: List<CatalogImportProduct>,
    issues: List<CatalogImportIssue>,
    val currency: String,
) {
    val products: List<CatalogImportProduct> = Collections.unmodifiableList(ArrayList(products))
    val issues: List<CatalogImportIssue> = Collections.unmodifiableList(ArrayList(issues))
    val authenticityVerified: Boolean = false
    val canImport: Boolean get() = previewId != null && issues.isEmpty()
}

data class CatalogImportResult(val importedCount: Int)
enum class CatalogImportFailureCode { INVALID_INPUT, DUPLICATE, STALE_PREVIEW, INVALID_CURRENCY, INVALID_DOCUMENT, PARSER_UNAVAILABLE }
class CatalogImportFailure(val code: CatalogImportFailureCode) : Exception(code.name)

/** Confirmation accepts only an opaque, volatile preview ID; request values are revalidated at commit. */
interface CatalogImportService {
    suspend fun preview(request: CatalogImportRequest): CatalogImportPreview
    /** Extracts only the PDF Subject, decodes STORE_DATA_V1 and returns an authorized preview. */
    suspend fun previewPdf(input: BoundedDocumentInput): CatalogImportPreview
    /** Discards only the matching volatile preview; it never mutates catalog data. */
    suspend fun discard(previewId: String)
    suspend fun confirm(previewId: String): CatalogImportResult
}

/** Infrastructure port. Implementations must isolate the untrusted PDF parser and return bounded UTF-16LE Subject bytes only. */
fun interface PdfSubjectExtractor {
    suspend fun extract(input: BoundedDocumentInput): ByteArray

    companion object {
        /** Technical Subject transport bound shared with the isolated parser protocol. */
        const val MAX_SUBJECT_BYTES: Int = 5_333_364
    }
}

enum class PdfSubjectFailureCode { REJECTED, UNAVAILABLE }
class PdfSubjectExtractionFailure(val code: PdfSubjectFailureCode) : Exception(code.name)
