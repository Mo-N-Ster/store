package com.vibe.store.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.vibe.store.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Both standalone and purchase-context supplier creation use the same authorized service. */
@Composable
fun SupplierScreen(service: SupplierService, permissions: Set<String>, french: Boolean,
    onSelect: ((SupplierView) -> Unit)? = null, back: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf(WorkflowPage<SupplierView>(emptyList(), false)) }
    var search by rememberSaveable { mutableStateOf("") }
    var includeInactive by rememberSaveable { mutableStateOf(false) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var revision by remember { mutableIntStateOf(0) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<Long?>(null) }
    var updatedAt by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var active by rememberSaveable { mutableStateOf(true) }
    var confirmDiscard by remember { mutableStateOf(false) }
    fun t(fr: String, en: String) = wf(french, fr, en)
    fun clearEditor() {
        editing = false; editId = null; updatedAt = null
        name = ""; phone = ""; email = ""; address = ""; active = true
    }
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null; message = null
        scope.launch {
            try { action() } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = workflowError(french, failure) }
            finally { busy = false }
        }
    }
    fun leave() { if (busy) return; if (editing) confirmDiscard = true else back() }
    BackHandler { leave() }
    LaunchedEffect(service, search, includeInactive, offset, revision) {
        try { rows = service.list(SupplierFilter(search = search, active = if (includeInactive) null else true,
            page = WorkflowPageRequest(offset, 40))) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = workflowError(french, failure) }
    }
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false },
        title = { Text(t("Abandonner le formulaire ?", "Discard the form?")) },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; clearEditor() }) { Text(t("Abandonner", "Discard")) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(t("Continuer", "Keep editing")) } })
    val supplierResults: (@Composable ColumnScope.() -> Unit)? = if (editing) null else {
        {
                rows.items.forEach { supplier ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(supplier.name, style = MaterialTheme.typography.titleMedium)
                            Text(listOf(supplier.phone, supplier.email, supplier.address).filter { it.isNotBlank() }.joinToString(" · "))
                            if (!supplier.active) Text(t("Inactif", "Inactive"))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (onSelect != null && supplier.active) TextButton(enabled = !busy,
                                    modifier = Modifier.testTag("supplier-select-${supplier.id}"),
                                    onClick = { onSelect(supplier) }) { Text(t("Sélectionner", "Select")) }
                                if ("PURCHASES:UPDATE" in permissions) TextButton(enabled = !busy,
                                    onClick = {
                                        editId = supplier.id; updatedAt = supplier.updatedAt
                                        name = supplier.name; phone = supplier.phone; email = supplier.email
                                        address = supplier.address; active = supplier.active; editing = true
                                    }) { Text(t("Modifier", "Edit")) }
                            }
                        }
                    }
                }
                if (rows.items.isEmpty()) Text(t("Aucun fournisseur", "No supplier"))
                Row {
                    TextButton(enabled = !busy && offset > 0, onClick = { offset = maxOf(0, offset - 40) }) { Text(t("Précédent", "Previous")) }
                    TextButton(enabled = !busy && rows.hasMore, onClick = { offset += 40 }) { Text(t("Suivant", "Next")) }
                }
        }
    }
    WorkflowPageFrame(
        screen = "supplier",
        top = {
            WorkflowHeader(t("Fournisseurs", "Suppliers"), t("Retour", "Back"), busy, ::leave)
            WorkflowNotice(error, message)
        },
        primary = {
            if (editing) {
                WorkflowField(t("Nom", "Name"), name, "supplier-name", !busy) { name = it }
                WorkflowField(t("Téléphone", "Phone"), phone, "supplier-phone", !busy) { phone = it }
                WorkflowField("Email", email, "supplier-email", !busy) { email = it }
                WorkflowField(t("Adresse", "Address"), address, "supplier-address", !busy) { address = it }
                Row { Checkbox(active, { active = it }, enabled = !busy); Text(t("Fournisseur actif", "Active supplier")) }
                Button(enabled = !busy && "PURCHASES:UPDATE" in permissions && name.isNotBlank(),
                    modifier = Modifier.testTag("supplier-save"), onClick = { run {
                        val saved = service.save(SaveSupplier(editId, name, phone, email, address, active, updatedAt))
                        clearEditor(); revision++
                        if (onSelect != null && saved.active) onSelect(saved)
                        else message = t("Fournisseur enregistré.", "Supplier saved.")
                    } }) { Text(t("Enregistrer", "Save")) }
                TextButton(enabled = !busy, onClick = { confirmDiscard = true }) { Text(t("Annuler", "Cancel")) }
            } else {
                WorkflowField(t("Rechercher", "Search"), search, "supplier-search", !busy) { search = it; offset = 0 }
                Row { Checkbox(includeInactive, { includeInactive = it; offset = 0 }, enabled = !busy)
                    Text(t("Inclure les inactifs", "Include inactive")) }
                if ("PURCHASES:UPDATE" in permissions) Button(enabled = !busy,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("supplier-create"), onClick = { clearEditor(); editing = true }) {
                    Text(t("Nouveau fournisseur", "New supplier"))
                }
            }
        },
        secondary = supplierResults,
    )
}
