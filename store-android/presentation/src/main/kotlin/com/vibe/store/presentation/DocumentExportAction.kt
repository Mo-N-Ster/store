package com.vibe.store.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.vibe.store.api.DocumentRequest
import com.vibe.store.api.DocumentResult
import com.vibe.store.api.DocumentService
import com.vibe.store.api.SecurityError
import com.vibe.store.api.SecurityFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

sealed interface PdfSaveOutcome {
    data object Saved : PdfSaveOutcome
    data object Cancelled : PdfSaveOutcome
    data class Failed(val reason: String? = null) : PdfSaveOutcome
}

/** A single explicit render-then-save action; export never invokes a business write. */
@Composable
fun DocumentExportAction(
    documentService: DocumentService,
    french: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    request: suspend () -> DocumentRequest,
    save: (Long, DocumentResult, (PdfSaveOutcome) -> Unit) -> Unit,
    printDocument: (suspend (Long, DocumentResult) -> Unit)? = null,
    shareDocument: (suspend (Long, DocumentResult) -> Unit)? = null,
    actorId: Long,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var alive by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { alive = false } }
    Column(modifier) {
        fun create(action: suspend (Long, DocumentResult) -> Unit) {
            if (busy) return
            busy = true; message = null
            scope.launch {
                try {
                    val result = documentService.render(request())
                    if (alive) action(actorId, result)
                }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { if (alive) message = if (french) "Document indisponible." else "Document unavailable." }
                finally { if (alive) busy = false }
            }
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = enabled && !busy, modifier = Modifier.fillMaxWidth().testTag("document-export"), onClick = {
            if (busy) return@Button
            busy = true; message = null
            scope.launch {
                try {
                    val result = documentService.render(request())
                    if (!alive) return@launch
                    save(actorId, result) { outcome ->
                        if (!alive) return@save
                        busy = false
                        message = when (outcome) {
                            PdfSaveOutcome.Saved -> if (french) "PDF enregistré." else "PDF saved."
                            PdfSaveOutcome.Cancelled -> if (french) "Export annulé." else "Export cancelled."
                            is PdfSaveOutcome.Failed -> if (french) "Échec de l’enregistrement. Réessayez." else "Could not save the PDF. Try again."
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: SecurityFailure) {
                    if (alive) {
                        busy = false
                        message = if (failure.code == SecurityError.FORBIDDEN)
                            if (french) "Accès refusé. Aucun PDF n’a été créé." else "Access denied. No PDF was created."
                        else if (french) "Session expirée. Reconnectez-vous puis réessayez." else "Session expired. Sign in and try again."
                    }
                } catch (_: Exception) {
                    if (alive) { busy = false; message = if (french) "PDF indisponible. Réessayez." else "PDF unavailable. Try again." }
                }
            }
        }) { Text(label) }
        if (printDocument != null) OutlinedButton(enabled = enabled && !busy, modifier = Modifier.fillMaxWidth().testTag("document-print"),
            onClick = { create(printDocument) }) { Text(if (french) "Imprimer" else "Print") }
        if (shareDocument != null) OutlinedButton(enabled = enabled && !busy, modifier = Modifier.fillMaxWidth().testTag("document-share"),
            onClick = { create(shareDocument) }) { Text(if (french) "Partager" else "Share") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("document-export-progress"))
        message?.let { Text(it, Modifier.testTag("document-export-status")) }
    }
}
