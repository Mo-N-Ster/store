package com.vibe.store
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.content.ClipData
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PageRange
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.os.Bundle
import android.print.PrintManager
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import com.vibe.store.api.SelectedImage
import com.vibe.store.infrastructure.media.AndroidProductMedia
import com.vibe.store.infrastructure.media.AndroidProfilePhotoMedia
import com.vibe.store.application.FoundationServiceImpl
import com.vibe.store.infrastructure.RoomDriverWiring
import com.vibe.store.presentation.FoundationApp
import com.vibe.store.presentation.SecurityApp
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

/** Composition only. This public launcher accepts no privileged command or data. */
class MainActivity : ComponentActivity() {
    private data class PendingPdfSave(val actorId: Long, val result: com.vibe.store.api.DocumentResult,
        val complete: (com.vibe.store.presentation.PdfSaveOutcome) -> Unit)
    private data class PendingCatalogPdfSelection(
        val actorId: Long,
        val epoch: Int,
        val callback: (com.vibe.store.presentation.CatalogPdfSelection) -> Unit,
    )
    private val posState by lazy { androidx.lifecycle.ViewModelProvider(this)[com.vibe.store.presentation.PosState::class.java] }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val service = FoundationServiceImpl(RoomDriverWiring())
        setContent {
            var access by rememberSaveable { mutableStateOf(false) }
            var selectionResult by remember { mutableStateOf<((SelectedImage?) -> Unit)?>(null) }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                val callback = selectionResult; selectionResult = null
                callback?.invoke(uri?.let(AndroidProductMedia::selection))
            }
            var profileSelectionResult by remember { mutableStateOf<((SelectedImage?) -> Unit)?>(null) }
            val profilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                val callback = profileSelectionResult; profileSelectionResult = null
                callback?.invoke(uri?.let(AndroidProfilePhotoMedia::selection))
            }
            var pendingPdf by remember { mutableStateOf<PendingPdfSave?>(null) }
            val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
                val pending = pendingPdf; pendingPdf = null
                if (pending != null) {
                    val result = pending.result
                    if (uri == null) pending.complete(com.vibe.store.presentation.PdfSaveOutcome.Cancelled)
                    else lifecycleScope.launch {
                        val outcome = try {
                            checkDocumentAuthorization(pending.actorId, result)
                            withContext(Dispatchers.IO) {
                                val bytes = result.content.copyBytes()
                                try { writePdfDestination(bytes) { contentResolver.openOutputStream(uri, "wt") } }
                                finally { bytes.fill(0) }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) { com.vibe.store.presentation.PdfSaveOutcome.Failed() }
                        pending.complete(outcome)
                    }
                }
            }
            var catalogSelectionResult by remember { mutableStateOf<PendingCatalogPdfSelection?>(null) }
            var catalogImportEpoch by remember { mutableIntStateOf(0) }
            val catalogPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                val pending = catalogSelectionResult; catalogSelectionResult = null
                if (pending != null) {
                    if (uri == null) pending.callback(com.vibe.store.presentation.CatalogPdfSelection.Cancelled)
                    else lifecycleScope.launch {
                        val selected = try {
                            val current = (application as StoreApplication).identity.current()
                            if (pending.epoch != catalogImportEpoch || current?.id != pending.actorId) {
                                return@launch
                            }
                            com.vibe.store.presentation.CatalogPdfSelection.Ready(
                                withContext(Dispatchers.IO) { readBoundedPdf(uri) },
                            )
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { com.vibe.store.presentation.CatalogPdfSelection.Failed }
                        if (pending.epoch == catalogImportEpoch) pending.callback(selected)
                    }
                }
            }
            val store = application as StoreApplication
            SideEffect { store.unsavedSale = posState.dirty }
            if (access) SecurityApp(store.identity, store.catalog, support = store.support, pickImage = { callback ->
                selectionResult = callback; picker.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
            }, team = store.team, sales = store.sales, posState = posState, suppliers = store.suppliers,
                reporting = store.reporting,
                delivery = store.delivery,
                scheduleDelivery = { EmailDeliveryScheduler.enqueue(this@MainActivity) },
                printDocument = { actorId, result -> printPdf(actorId, result) },
                shareDocument = { actorId, result -> sharePdf(actorId, result) },
                catalogImport = store.catalogImport,
                pickCatalogPdf = { actorId, callback ->
                    if (catalogSelectionResult != null) callback(com.vibe.store.presentation.CatalogPdfSelection.Failed)
                    else {
                        catalogSelectionResult = PendingCatalogPdfSelection(actorId, catalogImportEpoch, callback)
                        catalogPicker.launch(arrayOf("application/pdf"))
                    }
                },
                invalidatePendingCatalogPdf = { catalogSelectionResult = null; catalogImportEpoch++ },
                purchases = store.purchases, inventories = store.inventories, pickProfilePhoto = { callback ->
                profileSelectionResult = callback; profilePicker.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
            }, documents = store.documents,
                saveDocument = { actorId, result, complete ->
                    if (pendingPdf != null) complete(com.vibe.store.presentation.PdfSaveOutcome.Failed())
                    else { pendingPdf = PendingPdfSave(actorId, result, complete); pdfPicker.launch(result.fileName) }
                }, invalidatePendingDocument = { pendingPdf = null }
            ) { pendingPdf = null; catalogSelectionResult = null; access = false }
            else FoundationApp(service) { access = true }
        }
    }

    private suspend fun printPdf(actorId: Long, result: com.vibe.store.api.DocumentResult) {
        checkDocumentAuthorization(actorId, result)
        val manager = getSystemService(PRINT_SERVICE) as PrintManager
        val bytes = result.content.copyBytes()
        try { manager.print(result.fileName.removeSuffix(".pdf"), object : PrintDocumentAdapter() {
            override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal, callback: LayoutResultCallback, extras: Bundle?) {
                if (cancellationSignal.isCanceled) { callback.onLayoutCancelled(); return }
                callback.onLayoutFinished(PrintDocumentInfo.Builder(result.fileName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build(), oldAttributes != newAttributes)
            }
            override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal, callback: WriteResultCallback) {
                if (cancellationSignal.isCanceled) { bytes.fill(0); callback.onWriteCancelled(); return }
                lifecycleScope.launch {
                    val writeBytes = try {
                        checkDocumentAuthorization(actorId, result)
                        if (cancellationSignal.isCanceled) {
                            callback.onWriteCancelled()
                            return@launch
                        }
                        bytes.copyOf()
                    } catch (cancelled: CancellationException) {
                        callback.onWriteCancelled()
                        return@launch
                    } catch (_: Exception) {
                        callback.onWriteFailed("Access denied or document unavailable")
                        return@launch
                    }
                    try {
                        withContext(Dispatchers.IO) {
                            FileOutputStream(destination.fileDescriptor).use { output ->
                                output.write(writeBytes)
                                output.flush()
                            }
                        }
                        if (cancellationSignal.isCanceled) callback.onWriteCancelled()
                        else callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (_: CancellationException) {
                        callback.onWriteCancelled()
                    } catch (_: Exception) {
                        callback.onWriteFailed("Unable to print this document")
                    } finally { writeBytes.fill(0) }
                }
            }
            override fun onFinish() { bytes.fill(0); super.onFinish() }
        }, null) } catch (failure: Exception) { bytes.fill(0); throw failure }
    }

    private suspend fun sharePdf(actorId: Long, result: com.vibe.store.api.DocumentResult) {
        checkDocumentAuthorization(actorId, result)
        val bytes = result.content.copyBytes()
        val file = try { withContext(Dispatchers.IO) {
                val directory = File(cacheDir, "shared-documents")
                check(directory.isDirectory || directory.mkdirs())
                directory.listFiles()?.filter { it.isFile && it.name.startsWith("store-document-") }
                    ?.sortedByDescending { it.lastModified() }?.drop(2)?.forEach { it.delete() }
                check(directory.usableSpace >= bytes.size.toLong() + 1_048_576L) { "Insufficient cache space" }
                val target = File.createTempFile("store-document-", ".pdf", directory)
                try { FileOutputStream(target).use { it.write(bytes); it.fd.sync() } }
                catch (failure: Exception) { target.delete(); throw failure }
                target
            } } finally { bytes.fill(0) }
        try { checkDocumentAuthorization(actorId, result) }
        catch (failure: Exception) { file.delete(); throw failure }
        val uri = FileProvider.getUriForFile(this, "$packageName.documents", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(contentResolver, result.fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { startActivity(Intent.createChooser(send, result.fileName)) }
        catch (failure: Exception) { file.delete(); throw failure }
    }

    private suspend fun checkDocumentAuthorization(actorId: Long, result: com.vibe.store.api.DocumentResult) {
        val identity = (application as StoreApplication).identity.current()
            ?: throw com.vibe.store.api.SecurityFailure(com.vibe.store.api.SecurityError.FORBIDDEN)
        val required = when (result.type) {
            com.vibe.store.api.DocumentType.REPORT -> "FINANCES:READ"
            com.vibe.store.api.DocumentType.INVOICE -> "POS:READ"
            com.vibe.store.api.DocumentType.CATALOG -> "PRODUCTS:READ"
            com.vibe.store.api.DocumentType.HELP -> null
        }
        if (identity.id != actorId || required != null && required !in identity.permissions)
            throw com.vibe.store.api.SecurityFailure(com.vibe.store.api.SecurityError.FORBIDDEN)
    }

    /** Reads the user-selected SAF stream with a strict technical byte cap; never resolves a path. */
    internal fun readBoundedPdf(uri: Uri): com.vibe.store.api.BoundedDocumentInput {
        return readBoundedPdfFrom(com.vibe.store.api.BoundedDocumentInput.MAX_DOCUMENT_BYTES) {
            contentResolver.openInputStream(uri)
        }
    }

    /** Small stream boundary used to qualify provider revocation and I/O failure without filling storage. */
    internal fun readBoundedPdfFrom(
        maximum: Int = com.vibe.store.api.BoundedDocumentInput.MAX_DOCUMENT_BYTES,
        open: () -> InputStream?,
    ): com.vibe.store.api.BoundedDocumentInput {
        val stream = open() ?: throw IOException("Selected document unavailable")
        val output = ByteArrayOutputStream()
        stream.use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                if (output.size() > maximum - count) throw IOException("Selected document exceeds technical limit")
                output.write(buffer, 0, count)
            }
        }
        return com.vibe.store.api.BoundedDocumentInput(output.toByteArray(), maximum)
    }

    /** Writes one SAF-selected destination and closes it on success or provider failure. */
    internal fun writePdfDestination(content: ByteArray, open: () -> OutputStream?): com.vibe.store.presentation.PdfSaveOutcome {
        return try {
            val stream = open() ?: throw IOException("Destination unavailable")
            stream.use { it.write(content); it.flush() }
            com.vibe.store.presentation.PdfSaveOutcome.Saved
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            com.vibe.store.presentation.PdfSaveOutcome.Failed()
        }
    }
}
