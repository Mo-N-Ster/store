package com.vibe.store.presentation

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Resolve the app's explicit language preference even when it differs from the device locale. */
@Composable
internal fun localizedStoreString(@StringRes id: Int, french: Boolean): String {
    val context = LocalContext.current
    return remember(context, id, french) { localizedStoreString(context, id, french) }
}

internal fun localizedStoreString(context: Context, @StringRes id: Int, french: Boolean): String {
    val configuration = Configuration(context.resources.configuration).apply {
        setLocale(if (french) Locale.FRENCH else Locale.ENGLISH)
    }
    return context.createConfigurationContext(configuration).resources.getString(id)
}

/** Compact, keyboard-focusable info action; detail is shown only after activation. */
@Composable
fun ContextualHelp(
    title: String,
    explanation: String,
    french: Boolean,
    modifier: Modifier = Modifier,
    onOpenHelp: (() -> Unit)? = null,
    onRequestExplanation: ((String, String) -> Unit)? = null,
    testTag: String = "contextual-help",
) {
    var expanded by remember(title, explanation) { mutableStateOf(false) }
    val label = localizedStoreString(R.string.help_kpi_label, french).format(title)
    TextButton(
        onClick = { onRequestExplanation?.invoke(title, explanation); expanded = true },
        modifier = modifier.focusable().semantics { contentDescription = label }.testTag("$testTag-action"),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
    ) { Text("ⓘ", style = MaterialTheme.typography.titleMedium) }
    if (expanded) Surface(
        modifier = Modifier.fillMaxWidth().testTag("$testTag-details"),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(explanation, Modifier.testTag("$testTag-explanation"), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { expanded = false }) { Text(localizedStoreString(R.string.help_close, french)) }
                if (onOpenHelp != null) TextButton(onClick = { expanded = false; onOpenHelp() },
                    modifier = Modifier.testTag("$testTag-open-help")) { Text(localizedStoreString(R.string.help_open_center, french)) }
            }
        }
    }
}
