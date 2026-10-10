package com.vibe.store.infrastructure.documents.parser

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.Build
import android.os.IBinder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vibe.store.domain.CatalogCodecError
import com.vibe.store.domain.CatalogCodecException
import com.vibe.store.domain.StoreDataV1Codec
import com.vibe.store.api.BoundedDocumentInput
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.DeflaterOutputStream
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class PdfSubjectIsolatedServiceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun validStoreSubjectIsExtractedInsideIsolatedProcess() {
        val json = """{"kind":"stocks","version":1,"products":[{"name":"Café","hashtag":"#fcfa","category":"Food","description":"Synthetic","price":12.3456789,"stockQuantity":1,"minStockThreshold":0}]}"""
        val subject = "STORE_DATA_V1:" + android.util.Base64.encodeToString(json.toByteArray(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP)
        val response = request(pdf(subject))
        assertEquals(PdfSubjectIsolatedService.RESULT_OK, response.code)
        assertArrayEquals(subject.toByteArray(StandardCharsets.UTF_16LE), response.subject)
        val product = StoreDataV1Codec.decode(response.subject.toString(StandardCharsets.UTF_16LE)).payload.products.single()
        assertEquals(0, product.price.compareTo(java.math.BigDecimal("12.3456789")))
        assertEquals("Café", product.name)
    }

    @Test fun applicationExtractorUsesTheBoundedIsolatedBinderProtocol() = runBlocking {
        val expected = "STORE_DATA_V1:synthetic-subject"
        val actual = AndroidPdfSubjectExtractor(context).extract(BoundedDocumentInput(pdf(expected)))
        assertArrayEquals(expected.toByteArray(StandardCharsets.UTF_16LE), actual)
    }

    @Test fun applicationExtractorRejectsMalformedPdfWithoutReturningMetadata() = runBlocking {
        try {
            AndroidPdfSubjectExtractor(context).extract(BoundedDocumentInput(byteArrayOf(0x25, 0x50, 0x44, 0x46)))
            org.junit.Assert.fail("Malformed PDF must not return a Subject")
        } catch (failure: com.vibe.store.api.PdfSubjectExtractionFailure) {
            assertEquals(com.vibe.store.api.PdfSubjectFailureCode.REJECTED, failure.code)
        }
    }

    @Test fun syntheticLegacy201EnvelopeRetainsTheExactSubject() {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets
            .open("synthetic-2.0.1-envelope.pdf").use { it.readBytes() }
        val response = request(bytes)
        assertEquals(PdfSubjectIsolatedService.RESULT_OK, response.code)
        assertEquals(LEGACY_201_SUBJECT, response.subject.toString(StandardCharsets.UTF_16LE))
        assertEquals(3, StoreDataV1Codec.decode(LEGACY_201_SUBJECT).payload.products.size)
    }

    @Test fun malformedSubjectPayloadsAreRejectedByTheStrictCodec() {
        assertCodecRejects("STORE_DATA_V1:%%%", CatalogCodecError.INVALID_BASE64)
        val invalidUtf8 = android.util.Base64.encodeToString(byteArrayOf(0xC3.toByte(), 0x28), android.util.Base64.NO_WRAP)
        assertCodecRejects("STORE_DATA_V1:$invalidUtf8", CatalogCodecError.INVALID_UTF8)
        val unknownVersion = android.util.Base64.encodeToString(
            """{"kind":"stocks","version":2,"products":[]}""".toByteArray(StandardCharsets.UTF_8),
            android.util.Base64.NO_WRAP,
        )
        assertCodecRejects("STORE_DATA_V1:$unknownVersion", CatalogCodecError.UNSUPPORTED_VERSION)
    }

    @Test fun cyclicPageReferencesAndDeepMetadataRemainInsideTheFailureBoundary() {
        val result = request(pdf("bounded", nestedDepth = 256, cyclicPages = true))
        if (result.code == PdfSubjectIsolatedService.RESULT_OK) {
            assertArrayEquals("bounded".toByteArray(StandardCharsets.UTF_16LE), result.subject)
        } else {
            assertEquals(PdfSubjectIsolatedService.RESULT_REJECTED, result.code)
        }
        // A malformed graph must not poison the isolated parser service for the next request.
        assertEquals(PdfSubjectIsolatedService.RESULT_OK, request(pdf("recovered")).code)
    }

    @Test fun deeplyNestedMetadataIsRejectedOrExtractedWithoutPoisoningTheNextRequest() {
        val result = request(pdf("bounded", nestedDepth = 2_048))
        if (result.code == PdfSubjectIsolatedService.RESULT_OK) {
            assertArrayEquals("bounded".toByteArray(StandardCharsets.UTF_16LE), result.subject)
        } else {
            assertEquals(PdfSubjectIsolatedService.RESULT_REJECTED, result.code)
        }
        assertEquals(PdfSubjectIsolatedService.RESULT_OK, request(pdf("after-deep-metadata")).code)
    }

    @Test fun largeIndirectObjectGraphReturnsOnlyTheRequestedSubjectAndRecovers() {
        val largeGraph = pdfWithIndirectObjectGraph(subject = "graph-subject", objectCount = 4_096)
        assertTrue("bounded synthetic object graph must fit the input cap",
            largeGraph.size <= PdfSubjectIsolatedService.MAX_PDF_BYTES)
        val response = request(largeGraph)
        assertEquals(PdfSubjectIsolatedService.RESULT_OK, response.code)
        assertArrayEquals("graph-subject".toByteArray(StandardCharsets.UTF_16LE), response.subject)
        assertEquals(PdfSubjectIsolatedService.RESULT_OK, request(pdf("after-object-graph")).code)
    }

    @Test fun subjectExtractionDoesNotExpandACompressedPageStream() {
        val subject = "STORE_DATA_V1:synthetic-compression-boundary"
        val compressedPdf = pdfWithCompressedPageStream(subject, expandedBytes = 32 * 1024 * 1024)
        assertTrue("synthetic compressed PDF must remain within the configured input cap",
            compressedPdf.size <= PdfSubjectIsolatedService.MAX_PDF_BYTES)
        val response = request(compressedPdf)
        assertEquals(PdfSubjectIsolatedService.RESULT_OK, response.code)
        assertEquals(subject, response.subject.toString(StandardCharsets.UTF_16LE))
        assertEquals(PdfSubjectIsolatedService.RESULT_OK, request(pdf("after-compression-stream")).code)
    }

    @Test fun missingOrEmptySubjectIsRejected() {
        val response = request(pdf(null))
        assertEquals(PdfSubjectIsolatedService.RESULT_REJECTED, response.code)
        assertEquals(PdfSubjectIsolatedService.RESULT_REJECTED, request(pdf("")).code)
    }

    @Test fun malformedAndTruncatedPdfAreRejected() {
        assertEquals(PdfSubjectIsolatedService.RESULT_REJECTED, request("%PDF-1.7\n%%EOF".toByteArray()).code)
        val valid = pdf("small").toString(StandardCharsets.US_ASCII).toByteArray()
        assertEquals(PdfSubjectIsolatedService.RESULT_REJECTED, request(valid.copyOf(valid.size / 2)).code)
    }

    @Test fun passwordProtectedPdfWithoutCredentialsIsRejected() {
        val encrypted = InstrumentationRegistry.getInstrumentation().context.assets
            .open("encrypted-synthetic.pdf").use { it.readBytes() }
        assertEquals(PdfSubjectIsolatedService.RESULT_REJECTED, request(encrypted).code)
    }

    @Test fun subjectBeyondCanonicalCodecBoundIsRejected() {
        val overBoundSubject = "A".repeat(PdfSubjectIsolatedService.MAX_SUBJECT_BYTES / 2 + 1)
        assertEquals(PdfSubjectIsolatedService.RESULT_REJECTED, request(pdf(overBoundSubject)).code)
    }

    @Test fun repeatedMalformedRequestsDoNotReusePriorSuccess() {
        val malformed = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x00, 0x00, 0x00)
        repeat(8) { assertEquals(PdfSubjectIsolatedService.RESULT_REJECTED, request(malformed).code) }
    }

    @Test fun oversizedPdfIsRejectedAtTheNativeInputBoundary() {
        val tooLarge = ByteArray(PdfSubjectIsolatedService.MAX_PDF_BYTES + 1)
        assertEquals(PdfSubjectIsolatedService.RESULT_REJECTED, request(tooLarge).code)
    }

    @Test fun maximumAllowedPdfInputIsParsedWithinTheIsolatedProcessBudget() {
        val base = pdf("at-limit")
        var padding = PdfSubjectIsolatedService.MAX_PDF_BYTES - base.size - 128
        var maximum = pdf("at-limit", padding)
        padding += PdfSubjectIsolatedService.MAX_PDF_BYTES - maximum.size
        maximum = pdf("at-limit", padding)
        assertEquals(PdfSubjectIsolatedService.MAX_PDF_BYTES, maximum.size)
        val response = request(maximum)
        assertEquals(PdfSubjectIsolatedService.RESULT_OK, response.code)
        assertEquals("at-limit", response.subject.toString(StandardCharsets.UTF_16LE))
    }

    @Test fun subjectAtOutputLimitUsesBoundedPipeInsteadOfBinderPayload() {
        val nearLimit = "A".repeat(PdfSubjectIsolatedService.MAX_SUBJECT_BYTES / 2)
        val response = request(pdf(nearLimit))
        assertEquals(PdfSubjectIsolatedService.RESULT_OK, response.code)
        assertEquals(PdfSubjectIsolatedService.MAX_SUBJECT_BYTES, response.subject.size)
        assertTrue(response.subject.all { it == 0x41.toByte() || it == 0x00.toByte() })
    }

    @Test fun stalledInputTimesOutByTerminatingOnlyTheIsolatedServiceProcess() {
        val connected = CountDownLatch(1)
        val disconnected = CountDownLatch(1)
        val releaseWriter = CountDownLatch(1)
        val remote = AtomicReference<Messenger>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                remote.set(Messenger(binder)); connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) { disconnected.countDown() }
        }
        assertTrue(context.bindService(Intent(context, PdfSubjectIsolatedService::class.java), connection, Context.BIND_AUTO_CREATE))
        val pipe = ParcelFileDescriptor.createPipe()
        val writer = Thread {
            ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { out ->
                out.write("%PDF-1.4\n".toByteArray())
                releaseWriter.await(9, TimeUnit.SECONDS)
            }
        }.apply { name = "synthetic-stalled-pdf-writer"; start() }
        try {
            assertTrue("isolated parser did not bind", connected.await(10, TimeUnit.SECONDS))
            val message = Message.obtain(null, PdfSubjectIsolatedService.REQUEST_SUBJECT)
            message.replyTo = Messenger(Handler(Looper.getMainLooper()) { true })
            message.data = Bundle().apply { putParcelable(PdfSubjectIsolatedService.KEY_DESCRIPTOR, pipe[0]) }
            remote.get().send(message)
            pipe[0].close()
            assertTrue("native read did not terminate isolated process after timeout", disconnected.await(8, TimeUnit.SECONDS))
        } finally {
            releaseWriter.countDown()
            writer.join(2_000)
            pipe[0].close()
            context.unbindService(connection)
        }
        // Rebinding starts a fresh isolated process; a late result from the killed request
        // cannot satisfy this new request's distinct reply messenger.
        assertEquals(PdfSubjectIsolatedService.RESULT_OK, request(pdf("after-timeout")).code)
    }

    @Test fun concurrentRequestsAcceptAtMostOnePdfAtATime() {
        val connected = CountDownLatch(1)
        val releaseFirstWriter = CountDownLatch(1)
        val replies = LinkedBlockingQueue<Pair<Int, Int>>()
        val remote = AtomicReference<Messenger>()
        val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
            replies.offer(message.arg2 to message.arg1)
            true
        })
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                remote.set(Messenger(binder)); connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) { }
        }
        assertTrue(context.bindService(Intent(context, PdfSubjectIsolatedService::class.java), connection, Context.BIND_AUTO_CREATE))
        val firstPipe = ParcelFileDescriptor.createPipe()
        val secondPipe = ParcelFileDescriptor.createPipe()
        val firstWriter = Thread {
            ParcelFileDescriptor.AutoCloseOutputStream(firstPipe[1]).use { out ->
                out.write(pdf("first")); releaseFirstWriter.await(4, TimeUnit.SECONDS)
            }
        }.apply { start() }
        Thread { ParcelFileDescriptor.AutoCloseOutputStream(secondPipe[1]).use { it.write(pdf("second")) } }.start()
        try {
            assertTrue(connected.await(10, TimeUnit.SECONDS))
            val first = Message.obtain(null, PdfSubjectIsolatedService.REQUEST_SUBJECT, 1, 0).apply {
                replyTo = reply
                data = Bundle().apply { putParcelable(PdfSubjectIsolatedService.KEY_DESCRIPTOR, firstPipe[0]) }
            }
            remote.get().send(first)
            val second = Message.obtain(null, PdfSubjectIsolatedService.REQUEST_SUBJECT, 2, 0).apply {
                replyTo = reply
                data = Bundle().apply { putParcelable(PdfSubjectIsolatedService.KEY_DESCRIPTOR, secondPipe[0]) }
            }
            remote.get().send(second)
            firstPipe[0].close(); secondPipe[0].close()
            assertEquals(2 to PdfSubjectIsolatedService.RESULT_REJECTED, replies.poll(5, TimeUnit.SECONDS))
            releaseFirstWriter.countDown()
            firstWriter.join(2_000)
            assertEquals(1 to PdfSubjectIsolatedService.RESULT_OK, replies.poll(5, TimeUnit.SECONDS))
        } finally {
            releaseFirstWriter.countDown()
            firstWriter.join(2_000)
            firstPipe[0].close(); secondPipe[0].close()
            context.unbindService(connection)
        }
    }

    private data class Result(val code: Int, val subject: ByteArray)

    private fun request(pdf: ByteArray): Result {
        val connected = CountDownLatch(1)
        val received = CountDownLatch(1)
        val remote = AtomicReference<Messenger>()
        val result = AtomicReference<Result>()
        val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
            val data = message.data
            val descriptor = if (Build.VERSION.SDK_INT >= 33) {
                data?.getParcelable(PdfSubjectIsolatedService.KEY_SUBJECT_FD, ParcelFileDescriptor::class.java)
            } else {
                @Suppress("DEPRECATION")
                data?.getParcelable<ParcelFileDescriptor>(PdfSubjectIsolatedService.KEY_SUBJECT_FD)
            }
            val length = data?.getInt(PdfSubjectIsolatedService.KEY_SUBJECT_LENGTH, 0) ?: 0
            val bytes = if (descriptor != null && length in 1..PdfSubjectIsolatedService.MAX_SUBJECT_BYTES) {
                ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                    val output = ByteArrayOutputStream(length)
                    val buffer = ByteArray(16 * 1024)
                    while (output.size() < length) {
                        val count = input.read(buffer, 0, minOf(buffer.size, length - output.size()))
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
            } else byteArrayOf()
            result.set(Result(message.arg1, bytes))
            received.countDown()
            true
        })
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                remote.set(Messenger(binder)); connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) { }
        }
        assertTrue(context.bindService(Intent(context, PdfSubjectIsolatedService::class.java), connection, Context.BIND_AUTO_CREATE))
        try {
            assertTrue("isolated parser did not bind", connected.await(10, TimeUnit.SECONDS))
            val pipe = ParcelFileDescriptor.createPipe()
            val writer = Thread {
                try {
                    ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { it.write(pdf) }
                } catch (_: java.io.IOException) {
                    // Expected for inputs rejected before the producer has drained the pipe.
                }
            }.apply { name = "synthetic-pdf-pipe-writer"; start() }
            val message = Message.obtain(null, PdfSubjectIsolatedService.REQUEST_SUBJECT, 0x102, 0x201)
            message.replyTo = reply
            message.data = Bundle().apply { putParcelable(PdfSubjectIsolatedService.KEY_DESCRIPTOR, pipe[0]) }
            remote.get().send(message)
            pipe[0].close()
            writer.join(15_000)
            assertFalse("synthetic pipe writer stalled", writer.isAlive)
            assertTrue("isolated parser response timed out", received.await(12, TimeUnit.SECONDS))
            return result.get()
        } finally {
            context.unbindService(connection)
        }
    }

    /** Small synthetic PDF with no page content; only the Info Subject is relevant. */
    private fun pdf(
        subject: String?,
        trailingCommentCharacters: Int = 0,
        nestedDepth: Int = 0,
        cyclicPages: Boolean = false,
    ): ByteArray {
        val nested = if (nestedDepth == 0) "" else " /Nested ${"[".repeat(nestedDepth)}null${"]".repeat(nestedDepth)}"
        val objects = mutableListOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            if (cyclicPages) "<< /Type /Pages /Kids [2 0 R] /Count 1 >>" else "<< /Type /Pages /Kids [] /Count 0 >>",
            if (subject == null) "<< /Title (Synthetic)$nested >>" else "<< /Title (Synthetic) /Subject (${escape(subject)})$nested >>",
        )
        if (trailingCommentCharacters > 0) {
            objects += "<< /Type /Example >>\n%${"x".repeat(trailingCommentCharacters)}"
        }
        val out = ByteArrayOutputStream()
        out.write("%PDF-1.4\n".toByteArray(StandardCharsets.US_ASCII))
        val offsets = mutableListOf(0)
        objects.forEachIndexed { index, body ->
            offsets += out.size()
            out.write("${index + 1} 0 obj\n$body\nendobj\n".toByteArray(StandardCharsets.US_ASCII))
        }
        val xref = out.size()
        out.write("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n".toByteArray(StandardCharsets.US_ASCII))
        offsets.drop(1).forEach { out.write("%010d 00000 n \n".format(it).toByteArray(StandardCharsets.US_ASCII)) }
        out.write("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R /Info 3 0 R >>\nstartxref\n$xref\n%%EOF\n".toByteArray(StandardCharsets.US_ASCII))
        return out.toByteArray()
    }

    /** A valid small PDF whose unused page stream expands to 32 MiB if inflated. */
    private fun pdfWithCompressedPageStream(subject: String, expandedBytes: Int): ByteArray {
        val compressed = ByteArrayOutputStream().also { buffer ->
            DeflaterOutputStream(buffer).use { it.write(ByteArray(expandedBytes)) }
        }.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("%PDF-1.4\n".toByteArray(StandardCharsets.US_ASCII))
        val offsets = mutableListOf(0)
        listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] /Contents 4 0 R >>",
        ).forEachIndexed { index, body ->
            offsets += out.size()
            out.write("${index + 1} 0 obj\n$body\nendobj\n".toByteArray(StandardCharsets.US_ASCII))
        }
        offsets += out.size()
        out.write("4 0 obj\n<< /Length ${compressed.size} /Filter /FlateDecode >>\nstream\n".toByteArray(StandardCharsets.US_ASCII))
        out.write(compressed)
        out.write("\nendstream\nendobj\n".toByteArray(StandardCharsets.US_ASCII))
        offsets += out.size()
        out.write("5 0 obj\n<< /Subject (${escape(subject)}) >>\nendobj\n".toByteArray(StandardCharsets.US_ASCII))
        val xref = out.size()
        out.write("xref\n0 6\n0000000000 65535 f \n".toByteArray(StandardCharsets.US_ASCII))
        offsets.drop(1).forEach { out.write("%010d 00000 n \n".format(it).toByteArray(StandardCharsets.US_ASCII)) }
        out.write("trailer\n<< /Size 6 /Root 1 0 R /Info 5 0 R >>\nstartxref\n$xref\n%%EOF\n".toByteArray(StandardCharsets.US_ASCII))
        return out.toByteArray()
    }

    /** Valid bounded PDF with a long indirect-reference graph attached to its Info dictionary. */
    private fun pdfWithIndirectObjectGraph(subject: String, objectCount: Int): ByteArray {
        require(objectCount >= 4)
        val objectBodies = ArrayList<String>(objectCount)
        objectBodies += "<< /Type /Catalog /Pages 2 0 R >>"
        objectBodies += "<< /Type /Pages /Kids [] /Count 0 >>"
        objectBodies += "<< /Subject (${escape(subject)}) /Nested 4 0 R >>"
        for (objectNumber in 4..objectCount) {
            val next = if (objectNumber < objectCount) "/Next ${objectNumber + 1} 0 R" else "/Next null"
            objectBodies += "<< /Node $objectNumber $next >>"
        }
        val out = ByteArrayOutputStream()
        out.write("%PDF-1.4\n".toByteArray(StandardCharsets.US_ASCII))
        val offsets = IntArray(objectCount + 1)
        for (index in objectBodies.indices) {
            val objectNumber = index + 1
            offsets[objectNumber] = out.size()
            out.write("$objectNumber 0 obj\n${objectBodies[index]}\nendobj\n".toByteArray(StandardCharsets.US_ASCII))
        }
        val xrefOffset = out.size()
        out.write("xref\n0 ${objectCount + 1}\n0000000000 65535 f \n".toByteArray(StandardCharsets.US_ASCII))
        for (objectNumber in 1..objectCount) {
            out.write("%010d 00000 n \n".format(offsets[objectNumber]).toByteArray(StandardCharsets.US_ASCII))
        }
        out.write("trailer\n<< /Size ${objectCount + 1} /Root 1 0 R /Info 3 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n"
            .toByteArray(StandardCharsets.US_ASCII))
        return out.toByteArray()
    }

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")

    private fun assertCodecRejects(subject: String, expected: CatalogCodecError) {
        val response = request(pdf(subject))
        assertEquals(PdfSubjectIsolatedService.RESULT_OK, response.code)
        try {
            StoreDataV1Codec.decode(response.subject.toString(StandardCharsets.UTF_16LE))
            org.junit.Assert.fail("invalid Subject must fail the strict codec")
        } catch (failure: CatalogCodecException) {
            assertEquals(expected, failure.code)
        }
    }

    private companion object {
        const val LEGACY_201_SUBJECT = "STORE_DATA_V1:eyJwcm9kdWN0cyI6W3sibmFtZSI6IkNhZsOpIHRvcnLDqWZpw6kiLCJoYXNodGFnIjoiI2NhZsOpIiwiY2F0ZWdvcnkiOiLDiXBpY2VzIiwiZGVzY3JpcHRpb24iOiJSw6ljb2x0ZSBsb2NhbGUiLCJwcmljZSI6MTI1MC4yNSwic3RvY2tRdWFudGl0eSI6MjEsIm1pblN0b2NrVGhyZXNob2xkIjozfSx7Im5hbWUiOiJQaW1lbnQgc8OpY2jDqSIsImhhc2h0YWciOiIjcGltZW50IiwiY2F0ZWdvcnkiOiJBbGltZW50cyIsImRlc2NyaXB0aW9uIjoiR2/Du3QgcmVsZXbDqSIsInByaWNlIjowLjUsInN0b2NrUXVhbnRpdHkiOjAsIm1pblN0b2NrVGhyZXNob2xkIjoxfSx7Im5hbWUiOiJUaMOpIHZlcnQiLCJoYXNodGFnIjoiI3Row6kiLCJjYXRlZ29yeSI6IkJvaXNzb25zIiwiZGVzY3JpcHRpb24iOiJEb25uw6llcyBzeW50aMOpdGlxdWVzIiwicHJpY2UiOjk5OS45OSwic3RvY2tRdWFudGl0eSI6NywibWluU3RvY2tUaHJlc2hvbGQiOjJ9XSwidmVyc2lvbiI6MSwia2luZCI6InN0b2NrcyJ9"
    }
}
