package com.vibe.store.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.vibe.store.api.*
import kotlinx.coroutines.CancellationException

internal fun wf(fr: Boolean, french: String, english: String) = if (fr) french else english

internal fun workflowError(fr: Boolean, failure: Throwable): String {
    if (failure is CancellationException) throw failure
    if (failure is SecurityFailure) return wf(fr, "Accès refusé ou session expirée.", "Access denied or session expired.")
    if (failure is CatalogFailure) return wf(fr, "Création de l'article refusée. Vérifiez les champs et l'image.", "Product creation rejected. Check fields and image.")
    return when ((failure as? WorkflowFailure)?.code) {
        WorkflowError.INVALID_INPUT -> wf(fr, "Saisie invalide : vérifiez les valeurs et les limites.", "Invalid input: check values and limits.")
        WorkflowError.CONFLICT -> wf(fr, "Les données ont changé ou l'opération existe déjà. Rechargez le détail.", "Data changed or the operation already exists. Reload details.")
        WorkflowError.INSUFFICIENT_STOCK -> wf(fr, "Stock insuffisant : l'opération a été refusée en totalité.", "Insufficient stock: the entire operation was refused.")
        WorkflowError.FORBIDDEN -> wf(fr, "Accès refusé.", "Access denied.")
        WorkflowError.NOT_FOUND -> wf(fr, "Élément introuvable. Rechargez la liste.", "Item not found. Reload the list.")
        WorkflowError.INADMISSIBLE_TARGET -> wf(fr, "Fournisseur ou article devenu indisponible.", "Supplier or product is no longer admissible.")
        else -> wf(fr, "Résultat non confirmé. Rechargez le détail avant toute nouvelle action.", "Unconfirmed result. Reload details before any new action.")
    }
}

@Composable
internal fun WorkflowField(label: String, value: String, tag: String, enabled: Boolean,
    numeric: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, enabled = enabled,
        label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
        modifier = Modifier.fillMaxWidth().testTag(tag))
}

@Composable
internal fun WorkflowHeader(title: String, backLabel: String, busy: Boolean, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = onBack, enabled = !busy, modifier = Modifier.testTag("workflow-back")) { Text(backLabel) }
    }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
}

@Composable
internal fun WorkflowNotice(error: String?, message: String?) {
    error?.let { Text(it, Modifier.fillMaxWidth().testTag("workflow-error"), color = MaterialTheme.colorScheme.error) }
    message?.let { Text(it, Modifier.fillMaxWidth().testTag("workflow-notice"), color = MaterialTheme.colorScheme.primary) }
}

@Composable
internal fun WorkflowPageFrame(
    screen: String,
    top: @Composable ColumnScope.() -> Unit,
    primary: @Composable ColumnScope.() -> Unit,
    secondary: (@Composable ColumnScope.() -> Unit)? = null,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .testTag("workflow-screen-$screen")
    ) {
        val layout = when {
            maxWidth < 600.dp -> "COMPACT"
            maxWidth < 840.dp -> "MEDIUM"
            else -> "EXPANDED"
        }
        val widthFraction = when (layout) {
            "COMPACT" -> 1f
            "MEDIUM" -> 0.94f
            else -> 0.88f
        }
        val horizontalPadding = when (layout) {
            "COMPACT" -> 12.dp
            "MEDIUM" -> 20.dp
            else -> 24.dp
        }
        val secondaryContent = secondary

        Box(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(widthFraction)
                    .testTag("workflow-layout-$layout")
                    .padding(horizontal = horizontalPadding, vertical = 16.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = top,
                )
                Spacer(Modifier.height(12.dp))

                if (layout == "COMPACT" || secondaryContent == null) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .testTag("workflow-primary-$screen"),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        primary()

                        secondaryContent?.let { content ->
                            Spacer(Modifier.height(12.dp))
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .testTag("workflow-secondary-$screen"),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                content = content,
                            )
                        }
                    }
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(
                            if (layout == "EXPANDED") 24.dp else 16.dp
                        ),
                    ) {
                        Column(
                            Modifier
                                .weight(if (layout == "EXPANDED") 0.8f else 1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .testTag("workflow-primary-$screen"),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            content = primary,
                        )

                        Column(
                            Modifier
                                .weight(if (layout == "EXPANDED") 1.2f else 1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .testTag("workflow-secondary-$screen"),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            content = secondaryContent,
                        )
                    }
                }
            }
        }
    }
}

internal fun positiveLong(text: String): Long? = text.trim().toLongOrNull()?.takeIf { it in 1..9_007_199_254_740_991L }
internal fun countLong(text: String): Long? = text.trim().toLongOrNull()?.takeIf { it in 0..9_007_199_254_740_991L }
internal fun nonnegativeCost(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
