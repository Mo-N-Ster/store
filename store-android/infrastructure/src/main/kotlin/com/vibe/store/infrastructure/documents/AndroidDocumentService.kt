package com.vibe.store.infrastructure.documents

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.vibe.store.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.math.BigDecimal

/** Native offline PDF renderer. It consumes snapshots only and never accesses persistence. */
class AndroidDocumentService : DocumentService {
    override suspend fun render(request: DocumentRequest): DocumentResult = withContext(Dispatchers.Default) {
        NativePdfRenderer.render(request)
    }
}

private object NativePdfRenderer {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val LEFT = 42f
    private const val RIGHT = 553f
    private const val BOTTOM = 786f
    private const val MAX_PAGES = 200
    private const val MAX_ROWS = 10_000
    private const val MAX_COLUMNS = 8
    private const val MAX_TEXT = 64_000
    private const val MAX_BYTES = BoundedDocumentInput.MAX_DOCUMENT_BYTES
    private const val LINE_GAP = 4f

    private data class Cell(val text: String, val numeric: Boolean = false)
    private data class Table(val headers: List<String>, val rows: List<List<Cell>>)

    fun render(request: DocumentRequest): DocumentResult {
        val pdf = PdfDocument()
        val output = ByteArrayOutputStream()
        val limited = BoundedOutputStream(output, MAX_BYTES)
        val writer = PageWriter(pdf, request)
        return try {
            when (val snapshot = request.snapshot) {
                is DocumentSnapshot.Invoice -> renderInvoice(writer, snapshot.receipt, request.language == "fr")
                is DocumentSnapshot.Report -> renderReport(writer, snapshot.model, request.language == "fr")
                is DocumentSnapshot.Catalog -> renderCatalog(writer, snapshot.products, snapshot.currency, request.language == "fr")
                is DocumentSnapshot.Help -> renderHelp(writer, snapshot.title, snapshot.body, request.language == "fr")
            }
            writer.close()
            pdf.writeTo(limited)
            val bytes = output.toByteArray()
            val filename = when (request.type) {
                DocumentType.INVOICE -> "invoice_${safeToken(request.requestId)}.pdf"
                DocumentType.REPORT -> "report_${safeToken(request.requestId)}.pdf"
                DocumentType.CATALOG -> "catalog_${safeToken(request.requestId)}.pdf"
                DocumentType.HELP -> "help_${safeToken(request.requestId)}.pdf"
            }
            DocumentResult(request.requestId, request.type, filename, "application/pdf", BoundedDocumentInput(bytes, MAX_BYTES))
        } finally {
            runCatching { writer.close() }
            pdf.close()
        }
    }

    private fun renderInvoice(writer: PageWriter, receipt: Receipt, french: Boolean) {
        if (receipt.lines.size > MAX_ROWS) error("DOCUMENT_TOO_LARGE")
        writer.paragraph(receipt.storeName, 17f, bold = true)
        writer.paragraph(listOf(receipt.address, receipt.phone, receipt.email).filter(String::isNotBlank).joinToString(" · "), 9f)
        writer.section(if (french) "Facture" else "Invoice")
        writer.paragraph("${if (french) "Référence" else "Reference"}: ${receipt.id}    ${if (french) "Date" else "Date"}: ${receipt.date}")
        writer.paragraph("${if (french) "Vendeur" else "Seller"}: ${receipt.actorId}    ${if (french) "État" else "Status"}: ${receipt.status}")
        if (receipt.status.equals("cancelled", true)) writer.notice(if (french) "Facture annulée — exclue des ventes validées." else "Cancelled invoice — excluded from validated sales.")
        val currency = receipt.currency
        val rows = receipt.lines.map { line -> listOf(
            Cell(line.name), Cell(line.quantity.toString(), true),
            Cell("${money(line.unitPrice, french)} $currency", true),
            Cell("${money(line.total, french)} $currency", true),
        ) }
        writer.table(Table(listOf(if (french) "Article" else "Product", if (french) "Qté" else "Qty", if (french) "Prix unitaire" else "Unit price", if (french) "Total" else "Total"), rows))
        writer.section(if (french) "Paiement" else "Payment")
        writer.paragraph("${if (french) "Sous-total" else "Subtotal"}: ${money(receipt.subtotal, french)} $currency")
        writer.paragraph("${if (french) "Remise" else "Discount"}: ${money(receipt.discount, french)} $currency")
        writer.paragraph("${if (french) "Total" else "Total"}: ${money(receipt.total, french)} $currency", 14f, bold = true)
        writer.paragraph("${if (french) "Reçu" else "Received"}: ${money(receipt.received, french)} $currency    ${if (french) "Monnaie rendue" else "Change"}: ${money(receipt.change, french)} $currency")
        writer.paragraph("${if (french) "État du paiement" else "Payment status"}: ${receipt.paymentStatus}")
        receipt.cancellationReason?.let { writer.notice("${if (french) "Motif d’annulation" else "Cancellation reason"}: $it") }
    }

    private fun renderReport(writer: PageWriter, model: DocumentModel, french: Boolean) {
        writer.paragraph(model.tab.name, 17f, bold = true)
        val filter = model.filter
        writer.paragraph("${if (french) "Période" else "Period"}: [${filter.fromInclusive}, ${filter.toExclusive})")
        writer.paragraph("${if (french) "Fuseau" else "Time zone"}: ${filter.timeZoneId} · ${if (french) "Granularité" else "Grain"}: ${filter.grain}")
        val dimensions = listOfNotNull(filter.productId?.let { "${if (french) "Article" else "Product"} #$it" },
            filter.category?.let { "${if (french) "Catégorie" else "Category"}: $it" },
            filter.supplierId?.let { "${if (french) "Fournisseur" else "Supplier"} #$it" },
            filter.employeeId?.let { "${if (french) "Employé" else "Employee"} #$it" },
            filter.status?.let { "${if (french) "État" else "Status"}: $it" })
        if (dimensions.isNotEmpty()) writer.paragraph(dimensions.joinToString(" · "))
        writer.paragraph("${if (french) "Référence du rapport" else "Report reference"}: ${model.reportId}")
        writer.paragraph("${if (french) "Détails affichés" else "Displayed details"}: ${model.rows.size} / ${filter.rowLimit} · ${if (french) "les indicateurs portent sur les résultats filtrés complets" else "metrics cover the complete filtered result"}", 9f)
        val unavailable = model.currencyAvailability == ReportCurrencyAvailability.UNAVAILABLE
        if (model.rows.size > MAX_ROWS || model.series.any { it.points.size > MAX_ROWS }) error("DOCUMENT_TOO_LARGE")
        val currency = model.metrics.currency ?: model.currency.takeUnless { unavailable || it == "XXX" }
        if (unavailable || currency == null) writer.notice(if (french) "Devise indisponible pour certains montants ; aucune valeur n’est substituée par zéro." else "Currency unavailable for some amounts; no value is substituted with zero.")
        else writer.paragraph("${if (french) "Devise" else "Currency"}: $currency")
        val metrics = model.metrics
        if (model.tab in setOf(ReportTab.SUMMARY, ReportTab.SALES, ReportTab.PRODUCTS, ReportTab.FINANCES)) {
            writer.section(if (french) "Indicateurs" else "Metrics")
            if (currency != null) writer.paragraph("${if (french) "Chiffre d’affaires validé" else "Validated revenue"}: ${money(metrics.revenue, french)} $currency")
            writer.paragraph("${if (french) "Transactions validées" else "Validated transactions"}: ${metrics.transactionCount}")
            if (currency != null) writer.paragraph("${if (french) "Panier moyen" else "Average basket"}: ${money(metrics.averageBasket, french)} $currency")
            writer.paragraph("${if (french) "Ventes annulées" else "Cancelled sales"}: ${metrics.cancelledCount}")
            if (currency != null) {
                val method = when (metrics.marginDisclosure) {
                    MarginDisclosure.ESTIMATED_SNAPSHOT_COST -> if (french) "coûts enregistrés" else "recorded snapshot costs"
                    MarginDisclosure.ESTIMATED_WITH_PRICE_FALLBACK -> if (french) "coûts de repli estimés" else "estimated fallback costs"
                }
                writer.notice("${if (french) "Marge brute estimée" else "Estimated gross margin"}: ${money(metrics.estimatedGrossMargin, french)} $currency · $method · ${if (french) "Ce n’est pas un bénéfice certifié." else "This is not certified profit."}")
            }
        }
        model.series.forEach { series ->
            writer.section("${localizedSeries(series.key, french)} (${series.points.size})")
            val isMoney = series.key == "validatedRevenue" || series.key.startsWith("productPrice:")
            val seriesRows = series.points.mapIndexed { i, value ->
                val label = series.labels.getOrNull(i) ?: (i + 1).toString()
                val unit = if (isMoney && currency != null) " $currency" else ""
                listOf(Cell(label), Cell(money(value, french) + unit, true))
            }
            writer.table(Table(listOf(if (french) "Période" else "Period", if (french) "Valeur" else "Value"), seriesRows))
        }
        if (model.tab == ReportTab.PRODUCTS) writer.notice(if (french) "L’historique de prix contient uniquement les observations enregistrées; il est borné." else "Price history contains recorded observations only and is bounded.")
        writer.section(if (french) "Détails" else "Details")
        val headers = model.columns.map { localizedColumn(it.key, it.label, french) }
        val rows = model.rows.map { row -> row.cells.mapIndexed { index, cell ->
            val text = cell.monetaryValue?.let { value ->
                if (unavailable) if (french) "Indisponible" else "Unavailable"
                else money(value, french) + (currency?.let { " $it" } ?: "")
            } ?: localizedCell(cell.text, model.columns.getOrNull(index)?.key.orEmpty(), french)
            Cell(text, cell.monetaryValue != null || headers.getOrNull(index)?.contains(Regex("(?i)(total|price|amount|revenue|montant|prix|chiffre)")) == true)
        } }
        writer.table(Table(headers, rows))
    }

    private fun renderCatalog(writer: PageWriter, products: List<ProductView>, currency: String, french: Boolean) {
        if (products.size > MAX_ROWS) error("DOCUMENT_TOO_LARGE")
        writer.paragraph(if (french) "Catalogue des articles" else "Product catalog", 17f, bold = true)
        writer.paragraph(if (french) "${products.size} article(s)" else "${products.size} product(s)")
        val rows = products.map { product -> listOf(
            Cell(product.name), Cell(product.category), Cell(product.hashtag),
            Cell("${money(product.price, french)} $currency", true), Cell(product.stock.toString(), true), Cell(product.minimumStock.toString(), true),
        ) }
        writer.table(Table(listOf(if (french) "Article" else "Product", if (french) "Catégorie" else "Category", "#",
            if (french) "Prix" else "Price", if (french) "Stock" else "Stock", if (french) "Seuil" else "Threshold"), rows))
        products.forEach { product ->
            if (product.description.isNotBlank()) writer.paragraph("${product.name}: ${product.description}", 9f)
        }
    }

    private fun renderHelp(writer: PageWriter, title: String, body: String, french: Boolean) {
        writer.paragraph(title, 17f, bold = true)
        writer.paragraph(if (french) "Guide hors ligne" else "Offline help", 10f)
        writer.paragraph(body, 12f)
    }

    private fun money(value: BigDecimal, french: Boolean): String = PdfMoneyText.format(value, french)

    private fun localizedSeries(key: String, french: Boolean): String = when (key) {
        "validatedRevenue" -> if (french) "Chiffre d’affaires validé" else "Validated revenue"
        "currentMonthValidatedRevenue" -> if (french) "Chiffre validé du mois" else "Current-month validated revenue"
        "movementEntries" -> if (french) "Entrées de stock" else "Stock entries"
        "movementExits" -> if (french) "Sorties de stock" else "Stock exits"
        "purchaseCountTotal" -> if (french) "Achats · total" else "Purchases · total"
        "purchaseCountValidated" -> if (french) "Achats · validés" else "Purchases · validated"
        "purchaseCountDraft" -> if (french) "Achats · brouillons" else "Purchases · draft"
        "purchaseCountCancelled" -> if (french) "Achats · annulés" else "Purchases · cancelled"
        "completedHours" -> if (french) "Heures terminées" else "Completed hours"
        "sessions" -> if (french) "Sessions" else "Sessions"
        "completed" -> if (french) "Terminées" else "Completed"
        "open" -> if (french) "Ouvertes" else "Open"
        "corrected" -> if (french) "Corrigées" else "Corrected"
        "interrupted" -> if (french) "Interrompues" else "Interrupted"
        else -> if (key.startsWith("productPrice:")) {
            if (french) "Historique des prix enregistrés" else "Recorded price history"
        } else if (french) "Série de données" else "Data series"
    }

    private fun localizedColumn(key: String, fallback: String, french: Boolean): String = when (key) {
        "date" -> if (french) "Date" else "Date"
        "period" -> if (french) "Période" else "Period"
        "status" -> if (french) "État" else "Status"
        "reference" -> if (french) "Référence" else "Reference"
        "name" -> if (french) "Nom" else "Name"
        "kind" -> if (french) "Type" else "Type"
        "quantity" -> if (french) "Quantité" else "Quantity"
        "hours" -> if (french) "Heures" else "Hours"
        "revenue" -> if (french) "Chiffre validé" else "Validated revenue"
        "total" -> if (french) "Total" else "Total"
        "supplier" -> if (french) "Fournisseur" else "Supplier"
        "employee" -> if (french) "Employé" else "Employee"
        "currency" -> if (french) "Devise" else "Currency"
        "value" -> if (french) "Valeur" else "Value"
        "metric" -> if (french) "Indicateur" else "Metric"
        "invoice" -> if (french) "Facture" else "Invoice"
        "start" -> if (french) "Début" else "Start"
        "end" -> if (french) "Fin" else "End"
        else -> fallback
    }

    private fun localizedCell(text: String, column: String, french: Boolean): String {
        if (column == "metric") return when (text) {
            "validatedRevenue", "validatedSalesRevenue" -> if (french) "Chiffre d’affaires validé" else "Validated sales revenue"
            "validatedTransactions" -> if (french) "Transactions validées" else "Validated transactions"
            "averageBasket" -> if (french) "Panier moyen" else "Average basket"
            "cancelledTransactions" -> if (french) "Transactions annulées" else "Cancelled transactions"
            "estimatedGrossMargin" -> if (french) "Marge brute estimée" else "Estimated gross margin"
            "validatedPurchaseTotal" -> if (french) "Total des achats historiques" else "Historical purchase total"
            "salesMinusPurchasesNotProfit" -> if (french) "Écart non calculé (ce n’est pas un bénéfice)" else "Difference unavailable (not profit)"
            else -> text
        }
        if (column == "kind") return when (text.lowercase()) {
            "product" -> if (french) "Article" else "Product"
            "category" -> if (french) "Catégorie" else "Category"
            "stock" -> if (french) "Stock actuel" else "Current stock"
            "movement" -> if (french) "Mouvement" else "Movement"
            else -> text
        }
        if (column == "status") return when (text.uppercase()) {
            "VALIDATED" -> if (french) "Validé" else "Validated"
            "CANCELLED" -> if (french) "Annulé" else "Cancelled"
            "DRAFT" -> if (french) "Brouillon" else "Draft"
            "CORRECTED" -> if (french) "Corrigé" else "Corrected"
            "INTERRUPTED" -> if (french) "Interrompu" else "Interrupted"
            "OPEN" -> if (french) "Ouvert" else "Open"
            else -> text
        }
        return text
    }

    private fun safeToken(value: String): String = value.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(60).ifBlank { "document" }

    private class PageWriter(private val pdf: PdfDocument, private val request: DocumentRequest) {
        private var pageNumber = 0
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = 0f
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 10f; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
        private val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 10f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        private val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; strokeWidth = 0.7f }

        init { beginPage() }

        fun section(text: String) { paragraph(text, 12f, bold = true, topGap = 8f) }
        fun notice(text: String) { paragraph(text, 9.5f, bold = true, topGap = 4f) }

        fun paragraph(text: String, size: Float = 10f, bold: Boolean = false, topGap: Float = 0f) {
            checkText(text)
            y += topGap
            val paint = (if (bold) boldPaint else bodyPaint).apply { textSize = size }
            wrap(text, paint, RIGHT - LEFT).forEach { line ->
                ensure(LINE_GAP + size)
                canvas!!.drawText(line, LEFT, y + size, paint)
                y += size + LINE_GAP
            }
        }

        fun table(table: Table) {
            if (table.headers.isEmpty() || table.headers.size > MAX_COLUMNS) error("UNSUPPORTED_TABLE_WIDTH")
            if (table.rows.size > MAX_ROWS) error("DOCUMENT_TOO_LARGE")
            boldPaint.textSize = 10f
            bodyPaint.textSize = 9f
            val widths = FloatArray(table.headers.size) { (RIGHT - LEFT) / table.headers.size }
            fun drawHeader() {
                val wrapped = table.headers.mapIndexed { i, value -> wrap(value, boldPaint, widths[i] - 12f) }
                val height = (wrapped.maxOfOrNull { it.size } ?: 1) * 13f + 8f
                if (height > BOTTOM - 82f) error("TABLE_HEADER_TOO_TALL")
                ensure(height, repeatHeader = false)
                drawCells(wrapped, widths, boldPaint)
                y += height
                canvas!!.drawLine(LEFT, y, RIGHT, y, rulePaint)
                y += 4f
            }
            drawHeader()
            for (row in table.rows) {
                if (row.size > table.headers.size) error("ROW_COLUMNS_EXCEED_HEADER")
                val wrapped = row.mapIndexed { i, cell -> wrap(cell.text, bodyPaint, widths[i] - 12f) }
                val count = wrapped.maxOfOrNull { it.size } ?: 1
                var position = 0
                while (position < count) {
                    val lineCapacity = ((BOTTOM - y - 6f) / 14f).toInt()
                    if (lineCapacity <= 0) { nextPage(); drawHeader(); continue }
                    val take = minOf(lineCapacity, count - position)
                    val chunk = wrapped.map { lines -> (position until minOf(position + take, lines.size)).map(lines::getOrNull).mapNotNull { it } }
                    drawCells(chunk, widths, bodyPaint, row.map { it.numeric })
                    val h = take * 14f + 6f
                    y += h
                    canvas!!.drawLine(LEFT, y, RIGHT, y, rulePaint)
                    y += 3f
                    position += take
                    if (position < count) { nextPage(); drawHeader() }
                }
            }
        }

        private fun drawCells(lines: List<List<String>>, widths: FloatArray, paint: Paint, rightAlign: List<Boolean> = emptyList()) {
            var x = LEFT
            lines.forEachIndexed { index, cellLines ->
                val isRight = rightAlign.getOrNull(index) == true
                cellLines.forEachIndexed { lineIndex, line ->
                    val baseline = y + 11f + lineIndex * 14f
                    val drawX = if (isRight) x + widths[index] - 6f - paint.measureText(line) else x + 6f
                    canvas!!.drawText(line, drawX, baseline, paint)
                }
                x += widths[index]
            }
        }

        private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
            checkText(text)
            val result = ArrayList<String>()
            text.split('\n').forEach { source ->
                var remaining = source
                if (remaining.isEmpty()) { result += ""; return@forEach }
                while (remaining.isNotEmpty()) {
                    val fit = paint.breakText(remaining, true, maxWidth.coerceAtLeast(16f), null).coerceAtLeast(1)
                    var end = fit
                    if (fit < remaining.length) {
                        val space = remaining.lastIndexOf(' ', fit - 1)
                        if (space > 0) end = space
                    }
                    result += remaining.substring(0, end).trimEnd()
                    remaining = remaining.substring(end).trimStart()
                }
            }
            return result
        }

        private fun ensure(height: Float, repeatHeader: Boolean = false) {
            if (y + height <= BOTTOM) return
            nextPage()
            if (repeatHeader) Unit
        }

        private fun nextPage() { finishPage(); beginPage() }
        private fun beginPage() {
            if (pageNumber >= MAX_PAGES) error("DOCUMENT_PAGE_LIMIT")
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            page = pdf.startPage(info)
            canvas = page!!.canvas
            canvas!!.drawColor(Color.WHITE)
            val headerPaint = boldPaint.apply { textSize = 9f }
            canvas!!.drawText("STORE · ${request.type.name}", LEFT, 28f, headerPaint)
            val datePaint = bodyPaint.apply { textSize = 9f }
            canvas!!.drawText(request.createdAt.toString(), RIGHT - datePaint.measureText(request.createdAt.toString()), 28f, datePaint)
            canvas!!.drawLine(LEFT, 38f, RIGHT, 38f, rulePaint)
            val titlePaint = boldPaint.apply { textSize = 15f }
            val titleLines = wrap(request.title, titlePaint, RIGHT - LEFT)
            titleLines.forEachIndexed { index, line -> canvas!!.drawText(line, LEFT, 58f + index * 18f, titlePaint) }
            val titleBottom = 63f + titleLines.size * 18f
            canvas!!.drawLine(LEFT, titleBottom, RIGHT, titleBottom, rulePaint)
            y = titleBottom + 8f
        }
        private fun finishPage() {
            val active = page ?: return
            val footer = "${safeToken(request.requestId).take(40)} · Page $pageNumber"
            canvas!!.drawLine(LEFT, 807f, RIGHT, 807f, rulePaint)
            canvas!!.drawText(footer, RIGHT - bodyPaint.measureText(footer), 824f, bodyPaint)
            pdf.finishPage(active)
            page = null; canvas = null
        }
        fun close() { finishPage() }
        private fun checkText(text: String) { if (text.length > MAX_TEXT) error("DOCUMENT_TEXT_LIMIT") }
    }

    private class BoundedOutputStream(private val target: OutputStream, private val maximum: Int) : OutputStream() {
        private var count = 0
        override fun write(value: Int) { reserve(1); target.write(value) }
        override fun write(bytes: ByteArray, offset: Int, length: Int) { reserve(length); target.write(bytes, offset, length) }
        override fun flush() = target.flush()
        override fun close() = target.close()
        private fun reserve(size: Int) { if (size < 0 || count.toLong() + size > maximum) error("DOCUMENT_BYTE_LIMIT"); count += size }
    }
}

/** Shared exact decimal presentation used by every monetary PDF cell. */
internal object PdfMoneyText {
    fun format(value: BigDecimal, french: Boolean): String {
        val plain = value.stripTrailingZeros().toPlainString()
        return if (french) plain.replace('.', ',') else plain
    }
}
