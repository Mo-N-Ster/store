package com.vibe.store.infrastructure.documents

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.content.ContentValues
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.vibe.store.api.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocumentRendererNativeTest {
    @Test fun oversizedInvoiceIsRejectedBeforeDetailRowsAreMaterialized() = runBlocking {
        val lines = (1..10_001).map { index ->
            ReceiptLine(index.toLong(), "Synthetic $index", "Test", 1, BigDecimal("0.1"), BigDecimal("0.1"), null)
        }
        val receipt = Receipt("synthetic-oversize", 7, 2, "2026-10-09T12:00:00Z", "validated",
            BigDecimal("10001"), BigDecimal.ZERO, BigDecimal("10001"), BigDecimal("10001"), BigDecimal.ZERO,
            "PAID", "Synthetic", "", "", "", "XOF", lines)
        try {
            AndroidDocumentService().render(DocumentRequest("oversize", DocumentType.INVOICE, "Oversized", Instant.parse("2026-10-09T12:00:00Z"), DocumentSnapshot.Invoice(receipt)))
            throw AssertionError("oversized invoice should be rejected")
        } catch (failure: IllegalStateException) {
            assertEquals("DOCUMENT_TOO_LARGE", failure.message)
        }
    }

    @Test fun allFourSyntheticDocumentTypesRenderAndRasterizeEveryBoundedPage() = runBlocking {
        assertEquals("0.123456789", PdfMoneyText.format(BigDecimal("0.123456789"), false))
        assertEquals("0,123456789", PdfMoneyText.format(BigDecimal("0.123456789"), true))
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val out = File(context.cacheDir, "i10-native-pdf-${UUID.randomUUID()}").apply { mkdirs() }
        val renderer = AndroidDocumentService()
        val now = Instant.parse("2026-10-09T12:00:00Z")
        val receipt = Receipt("synthetic-invoice-1", 7, 2, "2026-10-09T12:00:00Z", "validated",
            BigDecimal("1.23456789"), BigDecimal("0.005"), BigDecimal("1.22956789"), BigDecimal("2"), BigDecimal("0.77043211"),
            "PAID", "STORE Synthétique", "1 rue du Café", "+33123456789", "synthetic@example.invalid", "XOF",
            (1..90).map { ReceiptLine(it.toLong(), "Café produit $it", "Boissons", 1, BigDecimal("0.123456789"), BigDecimal("0.123456789"), null) })
        val filter = ReportFilter(ReportTab.SALES, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-01"),
            ReportGrain.DAY, rowLimit = 200, timeZoneId = "Europe/Paris")
        val report = DocumentModel("synthetic-report", ReportTab.SALES, filter, "XOF",
            listOf(ReportColumn("date", "Date"), ReportColumn("total", "Total")),
            (1..200).map { ReportRow(listOf(ReportCell("2026-09-$it"), ReportCell("sale", BigDecimal("12.3456789")))) },
            listOf(ReportSeries("validatedRevenue", (1..90).map { BigDecimal("$it.123456789") }, (1..90).map { "2026-09-$it" })),
            ReportMetricSnapshot(BigDecimal("12.3456789"), 1, BigDecimal("12.3456789"), 0,
                BigDecimal("1.005"), MarginDisclosure.ESTIMATED_SNAPSHOT_COST, "XOF"))
        val catalog = DocumentSnapshot.Catalog((1..90).map {
            ProductView(it.toLong(), "Café $it", "Boissons", "#cafe", "Récolte locale — FCFA", BigDecimal("0.123456789"), 5, 1, false, false, "2026-10-09")
        }, "XOF")
        val requests = listOf(
            DocumentRequest(receipt.id, DocumentType.INVOICE, "Facture Synthétique", now, DocumentSnapshot.Invoice(receipt), "fr"),
            DocumentRequest(report.reportId, DocumentType.REPORT, "Ventes Septembre", now, DocumentSnapshot.Report(report), "fr"),
            DocumentRequest("catalog-synth", DocumentType.CATALOG, "Catalogue FCFA", now, catalog, "fr"),
            DocumentRequest("help-synth", DocumentType.HELP, "Guide Café", now,
                DocumentSnapshot.Help("Aide Café", ("Conseil utile : vérifier la caisse et les droits. FCFA.\n").repeat(1000)), "fr"),
        )
        requests.forEach { request ->
            val result = renderer.render(request)
            assertEquals(request.type, result.type)
            assertEquals("application/pdf", result.mediaType)
            val bytes = result.content.copyBytes()
            assertTrue(bytes.size > 500)
            assertTrue(String(bytes.copyOfRange(0, 5), Charsets.US_ASCII).startsWith("%PDF-"))
            val file = File(out, "${request.type.name.lowercase()}.pdf")
            file.writeBytes(bytes)
            val pages = ArrayList<File>()
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { pdf ->
                    assertTrue("${request.type} must paginate synthetic long content", pdf.pageCount > 1)
                    for (pageIndex in 0 until pdf.pageCount) {
                        val pageFile = File(out, "${request.type.name.lowercase()}-page-${pageIndex + 1}.png")
                        pdf.openPage(pageIndex).use { page ->
                            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                            try {
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                assertEquals("${request.type} page $pageIndex white page background", Color.WHITE, bitmap.getPixel(0, 0))
                                var ink = 0
                                var minX = page.width; var maxX = 0; var minY = page.height; var maxY = 0
                                for (y in 0 until page.height step 2) for (x in 0 until page.width step 2) {
                                    val pixel = bitmap.getPixel(x, y)
                                    if (android.graphics.Color.red(pixel) < 225 || android.graphics.Color.green(pixel) < 225 || android.graphics.Color.blue(pixel) < 225) {
                                        ink++; minX = minOf(minX, x); maxX = maxOf(maxX, x); minY = minOf(minY, y); maxY = maxOf(maxY, y)
                                    }
                                }
                                assertTrue("${request.type} page $pageIndex contains rendered content", ink > 10)
                                assertTrue("${request.type} page $pageIndex stays inside page margins: bounds=($minX,$minY)-($maxX,$maxY), page=${page.width}x${page.height}", minX >= 20 && maxX < page.width - 20 && minY >= 10 && maxY < page.height - 10)
                                FileOutputStream(pageFile).use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
                            } finally { bitmap.recycle() }
                        }
                        pages += pageFile
                    }
                    val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                    Log.i("I10_PDF_EVIDENCE", "${request.type} pages=${pdf.pageCount} bytes=${bytes.size} sha256=$hash images=${pages.joinToString { it.name }}")
                }
            }
        }
        val zipBytes = ByteArrayOutputStream().also { buffer ->
            ZipOutputStream(buffer).use { zip ->
                out.listFiles().orEmpty().filter { it.extension == "png" }.sortedBy { it.name }.forEach { image ->
                    zip.putNextEntry(ZipEntry(image.name))
                    image.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }.toByteArray()
        val archiveName = "I10_B_pdf_pages_${UUID.randomUUID()}.zip"
        val metadata = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, archiveName)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/I10-PDF-Evidence")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val archiveUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, metadata)
            ?: throw AssertionError("could not create synthetic raster evidence archive")
        context.contentResolver.openOutputStream(archiveUri, "w")!!.use { it.write(zipBytes) }
        context.contentResolver.update(archiveUri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        val archiveHash = MessageDigest.getInstance("SHA-256").digest(zipBytes).joinToString("") { "%02x".format(it) }
        Log.i("I10_PDF_EVIDENCE", "RASTER_ARCHIVE=$archiveName pages=${out.listFiles().orEmpty().count { it.extension == "png" }} bytes=${zipBytes.size} sha256=$archiveHash uri=$archiveUri")
        Unit
    }
}
