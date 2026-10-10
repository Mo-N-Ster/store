package com.vibe.store.api

import java.time.Instant
import java.util.Collections

enum class DocumentType { INVOICE, REPORT, CATALOG, HELP }

/** Immutable input snapshot for a future renderer; it grants no access or authority. */
sealed interface DocumentSnapshot {
    class Invoice(receipt: Receipt) : DocumentSnapshot {
        val receipt: Receipt = receipt.copy(lines = Collections.unmodifiableList(ArrayList(receipt.lines)))
    }
    class Report(val model: DocumentModel) : DocumentSnapshot
    class Catalog(products: List<ProductView>, val currency: String) : DocumentSnapshot {
        val products: List<ProductView> = Collections.unmodifiableList(ArrayList(products))
        init { require(currency.matches(Regex("[A-Z]{3}"))) }
    }
    class Help(val title: String, val body: String) : DocumentSnapshot
}

class DocumentRequest(
    val requestId: String,
    val type: DocumentType,
    val title: String,
    val createdAt: Instant,
    val snapshot: DocumentSnapshot,
    val language: String = "en",
) {
    init {
        require(requestId.isNotBlank() && requestId.length <= 160)
        require(title.isNotBlank() && title.length <= 500)
        require(language == "fr" || language == "en")
        require(when (type) {
            DocumentType.INVOICE -> snapshot is DocumentSnapshot.Invoice
            DocumentType.REPORT -> snapshot is DocumentSnapshot.Report
            DocumentType.CATALOG -> snapshot is DocumentSnapshot.Catalog
            DocumentType.HELP -> snapshot is DocumentSnapshot.Help
        })
    }
}

/** Defensive, bounded bytes. Limits are provisional parser resource ceilings from SP-02. */
class BoundedDocumentInput(bytes: ByteArray, maximumBytes: Int = MAX_DOCUMENT_BYTES) {
    private val value: ByteArray = bytes.copyOf()
    val size: Int get() = value.size
    init {
        require(maximumBytes in 1..MAX_DOCUMENT_BYTES)
        require(value.isNotEmpty() && value.size <= maximumBytes)
    }
    fun copyBytes(): ByteArray = value.copyOf()

    companion object {
        /** Provisional technical cap; not a permanent document-size/business rule. */
        const val MAX_DOCUMENT_BYTES: Int = 25_000_000
    }
}

class DocumentResult(
    val requestId: String,
    val type: DocumentType,
    val fileName: String,
    val mediaType: String,
    val content: BoundedDocumentInput,
) {
    init {
        require(requestId.isNotBlank() && requestId.length <= 160)
        require(fileName.matches(Regex("[A-Za-z0-9][A-Za-z0-9._ -]{0,179}")) && fileName != "." && fileName != "..")
        require(mediaType == "application/pdf")
    }
}

/** Pure document generation port; export destination selection stays in the Android app. */
interface DocumentService {
    suspend fun render(request: DocumentRequest): DocumentResult
}
