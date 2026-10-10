package com.vibe.store.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
    save: (DocumentResult, (PdfSaveOutcome) -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var alive by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { alive = false } }
    Column(modifier) {
        Button(enabled = enabled && !busy, modifier = Modifier.fillMaxWidth().testTag("document-export"), onClick = {
            if (busy) return@Button
            busy = true; message = null
            scope.launch {
                try {
                    val result = documentService.render(request())
                    if (!alive) return@launch
                    save(result) { outcome ->
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
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("document-export-progress"))
        message?.let { Text(it, Modifier.testTag("document-export-status")) }
    }
}
