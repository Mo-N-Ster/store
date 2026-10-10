package com.vibe.store
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
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
            var pendingPdf by remember { mutableStateOf<Pair<com.vibe.store.api.DocumentResult, (com.vibe.store.presentation.PdfSaveOutcome) -> Unit>?>(null) }
            val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
                val pending = pendingPdf; pendingPdf = null
                if (pending != null) {
                    val (result, completed) = pending
                    if (uri == null) completed(com.vibe.store.presentation.PdfSaveOutcome.Cancelled)
                    else lifecycleScope.launch {
                        val outcome = try {
                            withContext(Dispatchers.IO) {
                                writePdfDestination(result.content.copyBytes()) {
                                    contentResolver.openOutputStream(uri, "wt")
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) { com.vibe.store.presentation.PdfSaveOutcome.Failed() }
                        completed(outcome)
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
                saveDocument = { result, complete ->
                    if (pendingPdf != null) complete(com.vibe.store.presentation.PdfSaveOutcome.Failed())
                    else { pendingPdf = result to complete; pdfPicker.launch(result.fileName) }
                }, invalidatePendingDocument = { pendingPdf = null }
            ) { pendingPdf = null; catalogSelectionResult = null; access = false }
            else FoundationApp(service) { access = true }
        }
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
