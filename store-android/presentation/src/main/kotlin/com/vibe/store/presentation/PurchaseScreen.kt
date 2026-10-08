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
import java.util.UUID

/** Purchase draft state is durable in Room; this editor never auto-submits after lifecycle events. */
@Composable
fun PurchaseScreen(service: PurchaseService, suppliers: SupplierService, catalog: CatalogService,
    permissions: Set<String>, french: Boolean, back: () -> Unit) {
    fun t(fr: String, en: String) = wf(french, fr, en)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selected by remember { mutableStateOf<PurchaseDetail?>(null) }
    var lines by remember { mutableStateOf(WorkflowPage<PurchaseLineView>(emptyList(), false)) }
    var lineOffset by rememberSaveable { mutableIntStateOf(0) }
    var history by remember { mutableStateOf(WorkflowPage<PurchaseSummary>(emptyList(), false)) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var status by rememberSaveable { mutableStateOf("ALL") }
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    var supplierId by rememberSaveable { mutableStateOf("") }
    var invoice by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var creationAmbiguous by rememberSaveable { mutableStateOf(false) }
    var supplierMode by rememberSaveable { mutableStateOf(false) }
    var articleMode by rememberSaveable { mutableStateOf(false) }
    var articleName by rememberSaveable { mutableStateOf("") }
    var articleCategory by rememberSaveable { mutableStateOf("") }
    var articlePrice by rememberSaveable { mutableStateOf("") }
    var articleMin by rememberSaveable { mutableStateOf("0") }
    var productSearch by rememberSaveable { mutableStateOf("") }
    var productOffset by rememberSaveable { mutableIntStateOf(0) }
    var productPage by remember { mutableStateOf(CatalogPage(emptyList(), false)) }
    var productId by rememberSaveable { mutableStateOf<Long?>(null) }
    var quantity by rememberSaveable { mutableStateOf("1") }
    var cost by rememberSaveable { mutableStateOf("0") }
    var reason by rememberSaveable { mutableStateOf("") }
    var cancelConfirm by remember { mutableStateOf(false) }
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
        lines = service.lines(id, WorkflowPageRequest(lineOffset, 40))
    }
    LaunchedEffect(service, revision, selectedId, lineOffset) {
        val id = selectedId
        if (id != null) {
            try { loadSelected(id) } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = workflowError(french, failure) }
        }
    }
    LaunchedEffect(service, status, from, to, offset, revision) {
        try { history = service.list(PurchaseFilter(status = status.takeUnless { it == "ALL" }?.let(PurchaseStatus::valueOf),
            from = from, to = to, page = WorkflowPageRequest(offset, 40))) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = workflowError(french, failure) }
    }
    LaunchedEffect(catalog, productSearch, productOffset, revision) {
        try { productPage = catalog.list(CatalogFilter(search = productSearch, offset = productOffset, limit = 25)) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { if (selectedId != null) error = workflowError(french, failure) }
    }
    fun leave() {
        if (busy) return
        when {
            supplierMode -> supplierMode = false
            articleMode -> articleMode = false
            selectedId != null -> { selectedId = null; selected = null; lines = WorkflowPage(emptyList(), false); revision++ }
            creating -> creating = false
            else -> back()
        }
    }
    BackHandler { leave() }
    if (supplierMode) {
        SupplierScreen(suppliers, permissions, french,
            onSelect = { supplier -> supplierId = supplier.id.toString(); supplierMode = false },
            back = { supplierMode = false })
        return
    }
    if (cancelConfirm) AlertDialog(onDismissRequest = { cancelConfirm = false },
        title = { Text(t("Confirmer l'annulation ?", "Confirm cancellation?")) },
        text = { Text(t("L'opération ne pourra pas être rétablie.", "This action cannot be undone.")) },
        confirmButton = { TextButton(onClick = { cancelConfirm = false; selectedId?.let { id -> run {
            selected = service.cancel(CancelPurchase(id, reason)); reason = ""; revision++
        } } }) { Text(t("Annuler l'achat", "Cancel purchase")) } },
        dismissButton = { TextButton(onClick = { cancelConfirm = false }) { Text(t("Retour", "Back")) } })
    val purchaseHistory: (@Composable ColumnScope.() -> Unit)? =
        if (articleMode || creating || selectedId != null) null else {
            {
                history.items.forEach { item ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${item.reference} · ${item.status} · ${DecimalInput.display(item.total, french)}")
                            Text(item.createdAt)
                            TextButton(enabled = !busy, modifier = Modifier.testTag("purchase-detail-${item.id}"),
                                onClick = { selectedId = item.id; lineOffset = 0; productId = null }) {
                                Text(t("Détails", "Details"))
                            }
                        }
                    }
                }
                if (history.items.isEmpty()) Text(t("Aucun achat", "No purchase"))
                Row {
                    TextButton(enabled = !busy && offset > 0, onClick = { offset = maxOf(0, offset - 40) }) { Text(t("Précédent", "Previous")) }
                    TextButton(enabled = !busy && history.hasMore, onClick = { offset += 40 }) { Text(t("Suivant", "Next")) }
                    TextButton(enabled = !busy, onClick = { revision++ }) { Text(t("Actualiser", "Refresh")) }
                }
            }
        }
    WorkflowPageFrame(
        screen = "purchase",
        top = {
            WorkflowHeader(t("Achats et réceptions", "Purchases and receiving"), t("Retour", "Back"), busy, ::leave)
            WorkflowNotice(error, message)
        },
        primary = {
            if (articleMode) {
                Text(t("Créer un article sans stock initial", "Create product with zero initial stock"),
                    style = MaterialTheme.typography.titleMedium)
                WorkflowField(t("Nom", "Name"), articleName, "purchase-article-name", !busy) { articleName = it }
                WorkflowField(t("Catégorie", "Category"), articleCategory, "purchase-article-category", !busy) { articleCategory = it }
                WorkflowField(t("Prix de vente", "Selling price"), articlePrice, "purchase-article-price", !busy, true) { articlePrice = it }
                WorkflowField(t("Stock minimum", "Minimum stock"), articleMin, "purchase-article-min", !busy, true) { articleMin = it }
                Button(enabled = !busy && "PRODUCTS:UPDATE" in permissions && articleName.isNotBlank() &&
                    articleCategory.isNotBlank() && nonnegativeCost(articlePrice) != null && countLong(articleMin) != null,
                    modifier = Modifier.testTag("purchase-create-article"), onClick = { run {
                        val created = service.createArticle(PurchaseArticleCreation(articleName, articleCategory,
                            nonnegativeCost(articlePrice)!!, minimumStock = countLong(articleMin)!!))
                        productId = created.product.id; articleMode = false
                        productSearch = created.product.name; productOffset = 0
                        articleName = ""; articleCategory = ""; articlePrice = ""; articleMin = "0"
                        message = t("Article créé à stock nul. L'achat n'est pas validé.",
                            "Product created with zero stock. The purchase is not validated.")
                    } }) { Text(t("Créer et sélectionner", "Create and select")) }
                TextButton(enabled = !busy, onClick = { articleMode = false }) { Text(t("Annuler", "Cancel")) }
            } else if (creating && selectedId == null) {
                Text(t("Nouveau brouillon", "New draft"), style = MaterialTheme.typography.titleMedium)
                Text(t("La réception ne change pas le stock avant validation.", "Receiving does not change stock until validation."))
                Text(supplierId.ifBlank { t("Sans fournisseur", "No supplier") })
                TextButton(enabled = !busy && "PURCHASES:READ" in permissions,
                    onClick = { supplierMode = true }) { Text(t("Choisir/créer un fournisseur", "Select/create supplier")) }
                TextButton(enabled = !busy, onClick = { supplierId = "" }) { Text(t("Aucun fournisseur", "No supplier")) }
                WorkflowField(t("Facture fournisseur", "Supplier invoice"), invoice, "purchase-invoice", !busy) { invoice = it }
                WorkflowField(t("Note", "Note"), note, "purchase-note", !busy) { note = it }
                if (creationAmbiguous) {
                    Text(t("Création non confirmée : consultez les brouillons avant d'essayer à nouveau.",
                        "Creation unconfirmed: inspect drafts before trying again."), color = MaterialTheme.colorScheme.error)
                    Button(enabled = !busy, onClick = { creating = false; offset = 0; status = "ALL"; revision++ }) {
                        Text(t("Consulter les achats", "Inspect purchases"))
                    }
                } else Button(enabled = !busy && "PURCHASES:CREATE" in permissions &&
                    (supplierId.isBlank() || positiveLong(supplierId) != null),
                    modifier = Modifier.testTag("purchase-create-draft"), onClick = { run {
                        creationAmbiguous = true
                        val draft = service.createDraft(CreatePurchaseDraft(
                            supplierId = supplierId.takeIf { it.isNotBlank() }?.toLong(),
                            supplierInvoice = invoice, note = note, idempotencyKey = UUID.randomUUID().toString()))
                        selectedId = draft.summary.id; selected = draft; creating = false; creationAmbiguous = false
                        lineOffset = 0; revision++
                    } }) { Text(t("Enregistrer le brouillon", "Save draft")) }
            } else if (selectedId != null) {
                val detail = selected
                if (detail == null) Text(t("Chargement du détail…", "Loading details…"))
                else {
                    val summary = detail.summary
                    Text("${summary.reference} · ${summary.status} · ${DecimalInput.display(summary.total, french)}", style = MaterialTheme.typography.titleMedium)
                    Text("${t("Facture", "Invoice")}: ${detail.supplierInvoice} · ${detail.note}")
                    if (detail.cancellationReason != null) Text("${t("Motif", "Reason")}: ${detail.cancellationReason}")
                    lines.items.forEach { line ->
                        Text("#${line.productId} · ${t("Qté", "Qty")}: ${line.quantity} × ${DecimalInput.display(line.unitCost, french)} = ${DecimalInput.display(line.total, french)}")
                    }
                    if (lines.hasMore || lineOffset > 0) Row {
                        TextButton(enabled = !busy && lineOffset > 0, onClick = { lineOffset -= 40 }) { Text(t("Précédent", "Previous")) }
                        TextButton(enabled = !busy && lines.hasMore, onClick = { lineOffset += 40 }) { Text(t("Suivant", "Next")) }
                    }
                    if (summary.status == PurchaseStatus.DRAFT) {
                        if ("PURCHASES:UPDATE" in permissions) {
                            Text(t("Ajouter/remplacer une ligne", "Add/replace a line"), style = MaterialTheme.typography.titleMedium)
                            WorkflowField(t("Rechercher un article", "Search product"), productSearch,
                                "purchase-product-search", !busy) { productSearch = it; productOffset = 0 }
                            productPage.items.forEach { product ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(product.name, Modifier.weight(1f))
                                    TextButton(enabled = !busy, modifier = Modifier.testTag("purchase-product-${product.id}"),
                                        onClick = { productId = product.id; val old = lines.items.firstOrNull { it.productId == product.id }
                                            quantity = old?.quantity?.toString() ?: "1"; cost = old?.unitCost?.let { DecimalInput.display(it) } ?: "0"
                                        }) { Text(t("Choisir", "Select")) }
                                }
                            }
                            Row {
                                TextButton(enabled = !busy && productOffset > 0,
                                    onClick = { productOffset = maxOf(0, productOffset - 25) }) { Text(t("Précédent", "Previous")) }
                                TextButton(enabled = !busy && productPage.hasMore,
                                    onClick = { productOffset += 25 }) { Text(t("Suivant", "Next")) }
                            }
                            if ("PRODUCTS:UPDATE" in permissions) TextButton(enabled = !busy,
                                onClick = { articleMode = true }) { Text(t("Créer un article", "Create product")) }
                            productId?.let { id ->
                                Text("${t("Article", "Product")} #$id")
                                WorkflowField(t("Quantité entière", "Integer quantity"), quantity, "purchase-quantity", !busy, true) { quantity = it }
                                WorkflowField(t("Coût unitaire", "Unit cost"), cost, "purchase-unit-cost", !busy, true) { cost = it }
                                Button(enabled = !busy && positiveLong(quantity) != null && nonnegativeCost(cost) != null,
                                    modifier = Modifier.testTag("purchase-save-line"), onClick = { run {
                                        val previous = lines.items.firstOrNull { it.productId == id }
                                        service.saveLine(SavePurchaseLine(summary.id, id, positiveLong(quantity)!!,
                                            nonnegativeCost(cost)!!, previous))
                                        lineOffset = 0; loadSelected(summary.id); revision++
                                        message = t("Ligne enregistrée sans réception.", "Line saved without receiving stock.")
                                    } }) { Text(t("Enregistrer la ligne", "Save line")) }
                            }
                        }
                        if ("PURCHASES:VALIDATE" in permissions) Button(enabled = !busy,
                            modifier = Modifier.testTag("purchase-validate"), onClick = { run {
                                selected = service.validate(ValidatePurchase(summary.id)); lineOffset = 0; loadSelected(summary.id); revision++
                            } }) { Text(t("Valider la réception", "Validate receiving")) }
                    }
                    if (summary.status != PurchaseStatus.CANCELLED && "PURCHASES:DELETE" in permissions) {
                        WorkflowField(t("Motif d'annulation", "Cancellation reason"), reason, "purchase-cancel-reason", !busy) { reason = it }
                        Button(enabled = !busy && reason.trim().length >= 3,
                            modifier = Modifier.testTag("purchase-cancel"), onClick = { cancelConfirm = true }) {
                            Text(t("Annuler l'achat", "Cancel purchase"))
                        }
                    }
                    TextButton(enabled = !busy, onClick = { run { loadSelected(summary.id); revision++ } }) { Text(t("Actualiser le détail", "Refresh details")) }
                }
            } else {
                if ("PURCHASES:CREATE" in permissions) Button(enabled = !busy,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("purchase-new"), onClick = { creationAmbiguous = false; creating = true }) {
                    Text(t("Nouvel achat", "New purchase"))
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL", "DRAFT", "VALIDATED", "CANCELLED").forEach { next ->
                        FilterChip(selected = status == next, onClick = { status = next; offset = 0 },
                            label = { Text(next) }, enabled = !busy)
                    }
                }
                WorkflowField(t("Depuis (AAAA-MM-JJ)", "From (YYYY-MM-DD)"), from, "purchase-from", !busy) { from = it; offset = 0 }
                WorkflowField(t("Jusqu'au (AAAA-MM-JJ)", "To (YYYY-MM-DD)"), to, "purchase-to", !busy) { to = it; offset = 0 }
            }
        },
        secondary = purchaseHistory,
    )
}
