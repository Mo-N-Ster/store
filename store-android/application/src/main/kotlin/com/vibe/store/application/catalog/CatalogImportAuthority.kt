package com.vibe.store.application.catalog

import com.vibe.store.api.*
import com.vibe.store.api.CatalogImportPreview as ApiCatalogImportPreview
import com.vibe.store.application.security.IdentityAuthority
import com.vibe.store.application.persistence.ReadRepositories
import com.vibe.store.domain.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Parser-independent, session-scoped preview and all-or-nothing catalog import use case. */
class CatalogImportAuthority(
    private val identity: IdentityAuthority,
    private val clock: () -> Long = System::currentTimeMillis,
) : CatalogImportService {
    private var pdfSubjectExtractor: PdfSubjectExtractor? = null

    constructor(identity: IdentityAuthority, clock: () -> Long, extractor: PdfSubjectExtractor) : this(identity, clock) {
        pdfSubjectExtractor = extractor
    }

    private data class SessionRef(val actor: Long, val nonce: String, val generation: String)
    private data class Pending(
        val id: String,
        val actor: Long,
        val nonce: String,
        val generation: String,
        val currency: String,
        val products: List<StructuredCatalogProduct>,
    )

    // One bounded preview per authority. It is volatile, never persisted or trusted as proof.
    @Volatile private var pending: Pending? = null
    private val pendingLock = Any()
    private var previewGeneration: Long = 0

    /** A newer preview invalidates any older token before parsing or validation begins. */
    private fun beginPreview(): Long = synchronized(pendingLock) {
        previewGeneration += 1
        pending = null
        previewGeneration
    }

    private fun stamp() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(Date(clock()))

    override suspend fun preview(request: CatalogImportRequest): ApiCatalogImportPreview =
        identity.authorizedReadWithSession("PRODUCTS:UPDATE") { actor, nonce, generation ->
            createPreview(request, actor, nonce, generation, beginPreview(), this)
        }

    override suspend fun previewPdf(input: BoundedDocumentInput): ApiCatalogImportPreview {
        val extractor = pdfSubjectExtractor ?: throw CatalogImportFailure(CatalogImportFailureCode.PARSER_UNAVAILABLE)
        val start = identity.authorizedReadWithSession("PRODUCTS:UPDATE") { actor, nonce, generation ->
            SessionRef(actor, nonce, generation) to beginPreview()
        }
        val session = start.first
        val previewGeneration = start.second
        val bytes = extractor.extract(input)
        if (bytes.isEmpty() || bytes.size > PdfSubjectExtractor.MAX_SUBJECT_BYTES || bytes.size % 2 != 0) {
            throw CatalogImportFailure(CatalogImportFailureCode.INVALID_DOCUMENT)
        }
        val subject = try {
            StandardCharsets.UTF_16LE.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            throw CatalogImportFailure(CatalogImportFailureCode.INVALID_DOCUMENT)
        }
        val structural = try { StoreDataV1Codec.decode(subject) }
        catch (_: CatalogCodecException) { throw CatalogImportFailure(CatalogImportFailureCode.INVALID_DOCUMENT) }
        val request = CatalogImportRequest(structural.payload.products.map {
            CatalogImportProduct(it.name, it.hashtag, it.category, it.description, it.price, it.stockQuantity, it.minStockThreshold)
        })
        return identity.authorizedReadWithSession("PRODUCTS:UPDATE") { actor, nonce, generation ->
            if (SessionRef(actor, nonce, generation) != session) {
                throw CatalogImportFailure(CatalogImportFailureCode.STALE_PREVIEW)
            }
            createPreview(request, actor, nonce, generation, previewGeneration, this)
        }
    }

    private suspend fun createPreview(
        request: CatalogImportRequest,
        actor: Long,
        nonce: String,
        generation: String,
        previewGeneration: Long,
        repositories: ReadRepositories,
    ): ApiCatalogImportPreview {
        val structural = request.products.map {
            StructuredCatalogProduct(it.name, it.hashtag, it.category, it.description, it.price, it.stockQuantity, it.minStockThreshold)
        }
        val result = CatalogImportPolicy.validate(structural)
        val issues = result.issues.mapNotNull { it.toApiIssue() }.toMutableList()
        val currency = repositories.settings.value("currency") ?: "EUR"
        if (!currency.matches(Regex("[A-Z]{3}"))) throw CatalogImportFailure(CatalogImportFailureCode.INVALID_CURRENCY)

        // Duplicate diagnostics are independent: report in-file duplicates and
        // already-present catalog keys together when every row is otherwise valid.
        if (result.issues.none { it.code != "duplicate_in_file" }) {
            val names = result.products.map { CatalogPolicy.duplicateKey(it.name) }.toSet()
            val hashtags = result.products.map { it.hashtag }.filter { it.isNotEmpty() }.map(CatalogPolicy::duplicateKey).toSet()
            val matches = repositories.catalog.matchingDuplicateKeys(names, hashtags)
            result.products.forEachIndexed { index, product ->
                if (CatalogPolicy.duplicateKey(product.name) in matches.names) {
                    issues += CatalogImportIssue(index + 1, CatalogImportField.NAME, CatalogImportIssueCode.ALREADY_EXISTS)
                }
                if (product.hashtag.isNotEmpty() && CatalogPolicy.duplicateKey(product.hashtag) in matches.hashtags) {
                    issues += CatalogImportIssue(index + 1, CatalogImportField.HASHTAG, CatalogImportIssueCode.ALREADY_EXISTS)
                }
            }
        }
        val previewId = synchronized(pendingLock) {
            if (previewGeneration != this.previewGeneration) {
                throw CatalogImportFailure(CatalogImportFailureCode.STALE_PREVIEW)
            }
            if (issues.isEmpty()) UUID.randomUUID().toString().also {
                pending = Pending(it, actor, nonce, generation, currency, result.products)
            } else null
        }
        return ApiCatalogImportPreview(previewId, result.products.map { it.toApi() }, issues, currency)
    }

    override suspend fun confirm(previewId: String): CatalogImportResult {
        if (!previewId.matches(Regex("[0-9a-fA-F-]{36}"))) throw CatalogImportFailure(CatalogImportFailureCode.STALE_PREVIEW)
        return identity.authorizedWriteWithSession("PRODUCTS:UPDATE", afterCommit = {
            synchronized(pendingLock) { if (pending?.id == previewId) pending = null }
        }) { actor, nonce, generation ->
            val selected = synchronized(pendingLock) { pending?.takeIf {
                it.id == previewId && it.actor == actor && it.nonce == nonce && it.generation == generation
            } } ?: throw CatalogImportFailure(CatalogImportFailureCode.STALE_PREVIEW)
            val currency = settings.value("currency") ?: "EUR"
            if (currency != selected.currency || !currency.matches(Regex("[A-Z]{3}"))) {
                throw CatalogImportFailure(CatalogImportFailureCode.STALE_PREVIEW)
            }

            // Re-run business validation and duplicate checks inside the single write transaction.
            val validated = CatalogImportPolicy.validate(selected.products)
            if (validated.issues.isNotEmpty()) throw CatalogImportFailure(CatalogImportFailureCode.INVALID_INPUT)
            val names = validated.products.map { CatalogPolicy.duplicateKey(it.name) }.toSet()
            val hashtags = validated.products.map { it.hashtag }.filter { it.isNotEmpty() }.map(CatalogPolicy::duplicateKey).toSet()
            val matches = catalog.matchingDuplicateKeys(names, hashtags)
            if (matches.names.isNotEmpty() || matches.hashtags.isNotEmpty()) throw CatalogImportFailure(CatalogImportFailureCode.DUPLICATE)

            val firstId = catalog.nextProductId()
            if (firstId <= 0 || validated.products.size.toLong() > Long.MAX_VALUE - firstId) {
                throw CatalogImportFailure(CatalogImportFailureCode.INVALID_INPUT)
            }
            val createdAt = stamp()
            val records = validated.products.mapIndexed { index, product ->
                CatalogRecord(
                    id = firstId + index,
                    name = product.name,
                    category = product.category,
                    hashtag = product.hashtag,
                    description = product.description,
                    price = ExactMoney.normalize(product.price),
                    stock = product.stockQuantity,
                    minimumStock = product.minStockThreshold,
                    createdAt = createdAt,
                    updatedAt = createdAt,
                    deletedAt = null,
                    imageRef = null,
                )
            }
            catalog.insertImported(records)
            CatalogImportResult(records.size)
        }
    }

    override suspend fun discard(previewId: String) {
        synchronized(pendingLock) { if (pending?.id == previewId) pending = null }
    }

    private fun CatalogImportPolicyIssue.toApiIssue(): CatalogImportIssue? {
        val field = when (field) {
            "name" -> CatalogImportField.NAME
            "category" -> CatalogImportField.CATEGORY
            "hashtag" -> CatalogImportField.HASHTAG
            "description" -> CatalogImportField.DESCRIPTION
            "price" -> CatalogImportField.PRICE
            "stockQuantity" -> CatalogImportField.STOCK_QUANTITY
            "minStockThreshold" -> CatalogImportField.MIN_STOCK_THRESHOLD
            else -> return null
        }
        val code = when (code) {
            "required" -> CatalogImportIssueCode.REQUIRED
            "invalid_price", "limit_exceeded" -> CatalogImportIssueCode.INVALID_PRICE
            "invalid_quantity" -> CatalogImportIssueCode.INVALID_QUANTITY
            "duplicate_in_file" -> CatalogImportIssueCode.DUPLICATE_IN_FILE
            else -> return null
        }
        return CatalogImportIssue(row, field, code)
    }

    private fun StructuredCatalogProduct.toApi() = CatalogImportProduct(name, hashtag, category, description, price, stockQuantity, minStockThreshold)
}
