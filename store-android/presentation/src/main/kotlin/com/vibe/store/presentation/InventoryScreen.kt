package com.vibe.store.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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

/** Physical confirmation is deliberately ephemeral, never reconstructed from persisted prefill. */
@Composable
fun InventoryScreen(service: InventoryService, permissions: Set<String>, french: Boolean, back: () -> Unit) {
    fun t(fr: String, en: String) = wf(french, fr, en)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var history by remember { mutableStateOf(WorkflowPage<InventoryView>(emptyList(), false)) }
    var status by rememberSaveable { mutableStateOf("ALL") }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var note by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selected by remember { mutableStateOf<InventoryView?>(null) }
    var lines by remember { mutableStateOf<List<InventoryLineView>>(emptyList()) }
    var lineOffset by rememberSaveable { mutableIntStateOf(0) }
    val entered = remember { mutableStateMapOf<Long, String>() }
    val confirmed = remember { mutableStateMapOf<Long, Boolean>() }
    var physicalConfirmation by remember { mutableStateOf(false) }
    var confirmDialog by remember { mutableStateOf(false) }
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null; message = null
        scope.launch {
            try { action() } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = workflowError(french, failure) }
            finally { busy = false }
        }
    }
    suspend fun loadSelected(id: Long) {
        selected = service.detail(id)
        val next = ArrayList<InventoryLineView>()
        while (true) {
            if (next.size >= 10_000) {
                if (service.lines(id, WorkflowPageRequest(next.size, 1)).items.isNotEmpty())
                    throw WorkflowFailure(WorkflowError.INVALID_INPUT)
                break
            }
            val page = service.lines(id, WorkflowPageRequest(next.size, minOf(200, 10_000 - next.size)))
            next.addAll(page.items)
            if (!page.hasMore) break
        }
        lines = next
        entered.clear(); confirmed.clear(); physicalConfirmation = false
        next.forEach { entered[it.id] = it.countedQuantity.toString() }
    }
    LaunchedEffect(service, status, offset, revision) {
        try { history = service.list(InventoryFilter(status = status.takeUnless { it == "ALL" }?.let(InventoryStatus::valueOf),
            page = WorkflowPageRequest(offset, 40))) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = workflowError(french, failure) }
    }
    LaunchedEffect(service, selectedId, revision) {
        val id = selectedId
        if (id != null) try { loadSelected(id) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = workflowError(french, failure) }
    }
    fun leave() {
        if (busy) return
        if (selectedId != null) {
            selectedId = null; selected = null; lines = emptyList()
            entered.clear(); confirmed.clear(); physicalConfirmation = false; lineOffset = 0; revision++
        } else back()
    }
    BackHandler { leave() }
    if (confirmDialog) AlertDialog(onDismissRequest = { confirmDialog = false },
        title = { Text(t("Valider l'inventaire ?", "Validate inventory?")) },
        text = { Text(t("Le stock courant sera rapproché avec chaque quantité physiquement confirmée.",
            "Current stock will be reconciled with every physically confirmed quantity.")) },
        confirmButton = { TextButton(onClick = { confirmDialog = false; selectedId?.let { id -> run {
            if (!physicalConfirmation || lines.isEmpty() || lines.any { confirmed[it.id] != true ||
                    entered[it.id] != it.countedQuantity.toString() }) throw WorkflowFailure(WorkflowError.CONFLICT)
            val review = service.review(id)
            if (review.lineCount != lines.size) throw WorkflowFailure(WorkflowError.CONFLICT)
            val attestation = lines.map { InventoryCountAttestation(it.id, it.productId, it.countedQuantity) }
            selected = service.validate(ValidateInventory(id, review.token, true, attestation))
            confirmed.clear(); physicalConfirmation = false; lineOffset = 0
            message = t("Inventaire validé.", "Inventory validated.")
        } } }) { Text(t("Valider", "Validate")) } },
        dismissButton = { TextButton(onClick = { confirmDialog = false }) { Text(t("Retour", "Back")) } })
    val inventoryHistory: (@Composable ColumnScope.() -> Unit)? = if (selectedId != null) null else {
        {
                history.items.forEach { item ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${item.reference} · ${item.status}")
                            Text(item.createdAt)
                            TextButton(enabled = !busy, modifier = Modifier.testTag("inventory-detail-${item.id}"),
                                onClick = { selectedId = item.id; lineOffset = 0 }) { Text(t("Détails", "Details")) }
                        }
                    }
                }
                if (history.items.isEmpty()) Text(t("Aucun inventaire", "No inventory"))
                Row {
                    TextButton(enabled = !busy && offset > 0,
                        onClick = { offset = maxOf(0, offset - 40) }) { Text(t("Précédent", "Previous")) }
                    TextButton(enabled = !busy && history.hasMore,
                        onClick = { offset += 40 }) { Text(t("Suivant", "Next")) }
                    TextButton(enabled = !busy, onClick = { revision++ }) { Text(t("Actualiser", "Refresh")) }
                }
        }
    }
    WorkflowPageFrame(
        screen = "inventory",
        top = {
            WorkflowHeader(t("Inventaires", "Inventories"), t("Retour", "Back"), busy, ::leave)
            WorkflowNotice(error, message)
        },
        primary = {
            val current = selected
            if (selectedId != null) {
                if (current == null) Text(t("Chargement de l'inventaire…", "Loading inventory…"))
                else {
                    Text("${current.reference} · ${current.status}", style = MaterialTheme.typography.titleMedium)
                    Text(current.note)
                    Text(t("Stock attendu initial / quantité comptée / stock courant", "Original expected / counted quantity / current stock"))
                    lines.drop(lineOffset).take(40).forEach { line ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${t("Article", "Product")} #${line.productId}", style = MaterialTheme.typography.titleMedium)
                                Text("${t("Attendu", "Expected")}: ${line.expectedQuantity} · " +
                                    "${t("Courant", "Current")}: ${line.currentStock} · " +
                                    "${t("Écart proposé", "Proposed delta")}: ${line.countedQuantity - line.currentStock}")
                                if (current.status == InventoryStatus.DRAFT) {
                                    WorkflowField(t("Quantité comptée", "Counted quantity"), entered[line.id] ?: "",
                                        "inventory-count-${line.id}", !busy && "STOCKS:UPDATE" in permissions, true) {
                                        entered[line.id] = it; confirmed.remove(line.id); physicalConfirmation = false
                                    }
                                    Button(enabled = !busy && "STOCKS:UPDATE" in permissions &&
                                        countLong(entered[line.id] ?: "") != null,
                                        modifier = Modifier.testTag("inventory-record-${line.id}"), onClick = { run {
                                        service.recordCount(RecordInventoryCount(current.id, line.id, line.productId,
                                            countLong(entered[line.id] ?: "")!!, line.countedQuantity))
                                        loadSelected(current.id)
                                        message = t("Comptage enregistré sans ajuster le stock.", "Count saved without adjusting stock.")
                                    } }) { Text(t("Enregistrer le comptage", "Save count")) }
                                    Row {
                                        Checkbox(checked = confirmed[line.id] == true,
                                            enabled = !busy && entered[line.id] == line.countedQuantity.toString(),
                                            onCheckedChange = { checked ->
                                                if (entered[line.id] == line.countedQuantity.toString()) {
                                                    confirmed[line.id] = checked; physicalConfirmation = false
                                                }
                                            }, modifier = Modifier.testTag("inventory-confirm-${line.id}"))
                                        Text(t("Compté physiquement", "Physically counted"))
                                    }
                                } else Text("${t("Compté", "Counted")}: ${line.countedQuantity}")
                            }
                        }
                    }
                    Row {
                        TextButton(enabled = !busy && lineOffset > 0,
                            onClick = { lineOffset = maxOf(0, lineOffset - 40) }) { Text(t("Précédent", "Previous")) }
                        TextButton(enabled = !busy && lineOffset + 40 < lines.size,
                            onClick = { lineOffset += 40 }) { Text(t("Suivant", "Next")) }
                    }
                    Text("${t("Lignes confirmées", "Confirmed lines")}: ${confirmed.count { it.value }}/${lines.size}")
                    if (current.status == InventoryStatus.DRAFT) {
                        val complete = lines.isNotEmpty() && lines.all {
                            confirmed[it.id] == true && entered[it.id] == it.countedQuantity.toString()
                        }
                        Row {
                            Checkbox(checked = physicalConfirmation, enabled = !busy && complete,
                                onCheckedChange = { physicalConfirmation = it },
                                modifier = Modifier.testTag("inventory-final-confirm"))
                            Text(t("J'atteste avoir vérifié physiquement toutes les lignes.",
                                "I attest to physically checking every line."))
                        }
                        Button(enabled = !busy && "STOCKS:VALIDATE" in permissions && complete && physicalConfirmation,
                            modifier = Modifier.testTag("inventory-validate"), onClick = { confirmDialog = true }) {
                            Text(t("Valider et rapprocher le stock courant", "Validate against current stock"))
                        }
                    }
                    TextButton(enabled = !busy, onClick = { selectedId?.let { id -> run { loadSelected(id) } } }) {
                        Text(t("Actualiser (efface les confirmations)", "Refresh (clears confirmations)"))
                    }
                }
            } else {
                WorkflowField(t("Note du nouvel inventaire", "New inventory note"), note, "inventory-note", !busy) { note = it }
                if ("STOCKS:CREATE" in permissions) Button(enabled = !busy,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("inventory-start"), onClick = { run {
                        val draft = service.start(StartInventory(note))
                        selectedId = draft.id; selected = draft; note = ""; lineOffset = 0; revision++
                    } }) { Text(t("Démarrer un brouillon", "Start draft")) }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL", "DRAFT", "VALIDATED", "CANCELLED").forEach { next ->
                        FilterChip(selected = status == next, enabled = !busy,
                            onClick = { status = next; offset = 0 }, label = { Text(next) })
                    }
                }
            }
        },
        secondary = inventoryHistory,
    )
}
