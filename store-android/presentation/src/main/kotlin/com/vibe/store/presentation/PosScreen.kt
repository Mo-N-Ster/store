package com.vibe.store.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.vibe.store.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

/** Only an ephemeral cart; never SavedState or disk. Submitted commands live in the service. */
class PosState : ViewModel() {
    var actor by mutableStateOf<Long?>(null)
    val cart = mutableStateMapOf<Long, Pair<ProductView, String>>()
    var discount by mutableStateOf("")
    var percent by mutableStateOf(false)
    var received by mutableStateOf("")
    var submitted by mutableStateOf<SaleCommand?>(null)
    var result by mutableStateOf<Receipt?>(null)
    val dirty: Boolean get() = cart.isNotEmpty() || submitted != null
    fun bind(id: Long) { if (actor != id) { clear(); actor = id } }
    fun clear() { cart.clear(); discount = ""; received = ""; submitted = null; result = null; percent = false }
}

@Composable
fun PosScreen(service: SaleService, catalog: CatalogService, actor: PublicIdentity,
    french: Boolean, state: PosState, back: () -> Unit) {
    fun t(fr: String, en: String) = if (french) fr else en
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cash by remember { mutableStateOf<CashView?>(null) }
    var pending by remember { mutableStateOf(emptyList<PendingSale>()) }
    var products by remember { mutableStateOf(CatalogPage(emptyList(), false)) }
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var offset by remember { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf("pos") }
    var amount by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(SalePage(emptyList(), false)) }
    var cashRows by remember { mutableStateOf(emptyList<CashView>()) }
    var historyOffset by remember { mutableIntStateOf(0) }
    var historySearch by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var selectedReceipt by remember { mutableStateOf<Receipt?>(null) }
    var resolvedKey by remember { mutableStateOf<String?>(null) }
    fun money(text: String): java.math.BigDecimal? = DecimalInput.nonnegative(text)
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: SecurityFailure) { error = t("Accès refusé ou session expirée.", "Access denied or session expired.") }
            catch (failure: SaleFailure) { error = when (failure.code) {
                SaleError.INSUFFICIENT_STOCK -> t("Stock insuffisant. Vérifiez les quantités.", "Insufficient stock. Check quantities.")
                SaleError.CASH_REQUIRED -> t("Votre caisse doit être ouverte.", "Your cash session must be open.")
                SaleError.CONFLICT -> t("Commande incompatible. Consultez l’historique ; ne recréez pas la vente.", "Incompatible command. Check history; do not recreate the sale.")
                else -> t("Opération refusée. Vérifiez les valeurs et réessayez.", "Operation rejected. Check values and retry.")
            } }
            catch (_: Exception) { error = t("Résultat non confirmé. Utilisez Résoudre, sans créer une nouvelle commande.", "Result unconfirmed. Use Resolve without creating another command.") }
            finally { busy = false }
        }
    }
    suspend fun refresh() {
        if ("CASH:READ" in actor.permissions) cash = service.currentCash()
        if ("POS:VALIDATE" in actor.permissions) pending = service.pending()
    }
    LaunchedEffect(actor.id, service) { state.bind(actor.id); run { refresh() } }
    LaunchedEffect(search, category, offset) {
        try { products = catalog.list(CatalogFilter(search = search, category = category, offset = offset, limit = 30)) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = t("Catalogue indisponible.", "Catalog unavailable.") }
    }
    BackHandler(enabled = true) { if (!busy) { if (selectedReceipt != null) selectedReceipt = null else if (mode != "pos") mode = "pos" else back() } }
    val width = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val layout = if (width < 600.dp) "COMPACT" else if (width < 840.dp) "MEDIUM" else "EXPANDED"
    val locked = busy || state.submitted != null
    @Composable fun header() {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(t("Caisse et ventes", "Cash and sales"), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = back, enabled = !busy) { Text(t("Retour", "Back")) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, Modifier.testTag("pos-error"), color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = !busy, onClick = { mode = "pos"; selectedReceipt = null }) { Text(t("Vente", "Sale")) }
            TextButton(enabled = !busy && "POS:READ" in actor.permissions, onClick = { run {
                historyOffset = 0; history = service.history(); selectedReceipt = null; mode = "history"
            } }) { Text(t("Factures", "Invoices")) }
            TextButton(enabled = !busy && "CASH:READ" in actor.permissions, onClick = { run {
                historyOffset = 0; cashRows = service.cashHistory(); mode = "cash"
            } }) { Text(t("Caisses", "Cash sessions")) }
        }
    }
    @Composable fun cashPanel() {
        ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
            Text(cash?.let { "${t("Caisse ouverte", "Open cash")} · ${it.reference}" } ?: t("Aucune caisse ouverte", "No open cash session"))
            cash?.let { Text("${t("Montant théorique", "Expected amount")} : ${DecimalInput.display(it.expected, french)}") }
            PosField(t("Montant initial / compté", "Opening / counted amount"), amount, "pos-cash-amount", !busy) { amount = it }
            if (cash == null && "CASH:CREATE" in actor.permissions) Button(enabled = !busy && money(amount) != null,
                modifier = Modifier.testTag("pos-open"), onClick = { run { cash = service.openCash(money(amount)!!); amount = "" } }) { Text(t("Ouvrir la caisse", "Open cash")) }
            if (cash != null && "CASH:VALIDATE" in actor.permissions) OutlinedButton(enabled = !busy && money(amount) != null,
                modifier = Modifier.testTag("pos-close"), onClick = { run { service.closeCash(cash!!.id, money(amount)!!); cash = null; amount = "" } }) { Text(t("Fermer la caisse", "Close cash")) }
        } }
    }
    @Composable fun productPanel(modifier: Modifier) {
        Column(modifier.verticalScroll(rememberScrollState()).testTag("pos-products"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PosField(t("Rechercher un article", "Search products"), search, "pos-search", !busy, false) { search = it; offset = 0 }
            PosField(t("Catégorie", "Category"), category, "pos-category", !busy, false) { category = it; offset = 0 }
            if (products.items.isEmpty()) Text(t("Aucun article", "No products"))
            products.items.forEach { product ->
                ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text(product.name, style = MaterialTheme.typography.titleMedium)
                    Text("${product.category} · ${DecimalInput.display(product.price, french)} · ${t("Stock", "Stock")}: ${product.stock}")
                    Button(modifier = Modifier.testTag("pos-add-${product.id}"), enabled = !locked,
                        onClick = { val old = state.cart[product.id]?.second?.toLongOrNull() ?: 0L
                            if (old < 9_007_199_254_740_991L) state.cart[product.id] = product to (old + 1).toString()
                        }) { Text(t("Ajouter", "Add")) }
                } }
            }
            Row {
                TextButton(enabled = offset > 0 && !busy, onClick = { offset = maxOf(0, offset - 30) }) { Text(t("Précédent", "Previous")) }
                TextButton(enabled = products.hasMore && !busy, onClick = { offset += 30 }) { Text(t("Suivant", "Next")) }
            }
        }
    }
    @Composable fun cartPanel(modifier: Modifier) {
        Column(modifier.verticalScroll(rememberScrollState()).testTag("pos-cart"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cashPanel()
            Text(t("Panier", "Cart"), style = MaterialTheme.typography.titleLarge)
            TextButton(enabled = !locked && state.cart.isNotEmpty(), onClick = { state.clear() }) { Text(t("Vider le panier", "Clear cart")) }
            if (state.cart.isEmpty()) Text(t("Panier vide", "Empty cart"))
            state.cart.toMap().forEach { (id, entry) ->
                Text(entry.first.name)
                PosField(t("Quantité entière (0 retire l’article)", "Integer quantity (0 removes item)"), entry.second,
                    "pos-quantity-$id", !locked) { state.cart[id] = entry.first to it }
                TextButton(enabled = !locked, onClick = { state.cart.remove(id) }) { Text(t("Retirer", "Remove")) }
            }
            PosField(t("Remise demandée", "Requested discount"), state.discount, "pos-discount", !locked) { state.discount = it }
            Row { Checkbox(checked = state.percent, enabled = !locked, onCheckedChange = { state.percent = it }); Text(t("Remise en pourcentage", "Percentage discount")) }
            PosField(t("Espèces reçues (vide : montant exact)", "Cash received (blank: exact amount)"), state.received, "pos-received", !locked) { state.received = it }
            Button(modifier = Modifier.testTag("pos-submit"), enabled = !locked && cash != null && state.cart.isNotEmpty() && pending.isEmpty() && "POS:VALIDATE" in actor.permissions,
                onClick = {
                    val quantities = state.cart.map { (id, pair) -> id to pair.second.toLongOrNull() }
                    val discount = if (state.discount.isBlank()) java.math.BigDecimal.ZERO else money(state.discount)
                    val received = if (state.received.isBlank()) null else money(state.received)
                    if (quantities.any { it.second == null || it.second!! !in 0..9_007_199_254_740_991L } || discount == null ||
                        (!state.received.isBlank() && received == null) || (state.percent && discount > java.math.BigDecimal("100"))) {
                        error = t("Valeurs non valides.", "Invalid values.")
                    } else {
                        quantities.filter { it.second == 0L }.forEach { state.cart.remove(it.first) }
                        val lines = quantities.filter { it.second!! > 0 }.map { SaleLine(it.first, it.second!!) }
                        if (lines.isEmpty()) error = t("Panier vide", "Empty cart") else {
                            val requested = if (state.percent) lines.fold(java.math.BigDecimal.ZERO) { sum, line ->
                                sum.add(state.cart.getValue(line.productId).first.price.multiply(java.math.BigDecimal.valueOf(line.quantity)))
                            }.multiply(discount).movePointLeft(2) else discount
                            val command = SaleCommand(UUID.randomUUID().toString(), cash!!.id, lines, requested, received)
                            state.submitted = command
                            run { state.result = service.sell(command); state.cart.clear(); state.discount = ""; state.received = ""; refresh() }
                        }
                    }
                }) { Text(t("Valider l’achat", "Complete sale")) }
            if (state.submitted != null && state.result == null) {
                Text(t("Commande soumise : ne pas créer une autre vente.", "Submitted command: do not create another sale."))
                Button(enabled = !busy, onClick = { run { state.result = service.sell(state.submitted!!); state.cart.clear(); refresh() } }) { Text(t("Résoudre la commande", "Resolve command")) }
                OutlinedButton(enabled = !busy, onClick = { run {
                    service.abandon(state.submitted!!.key); state.submitted = null; refresh()
                } }) { Text(t("Vérifier l’absence de vente puis corriger", "Confirm no sale then edit")) }
            }
            pending.forEach { item ->
                Text("${item.createdAt} · ${item.key}")
                OutlinedButton(enabled = !busy, onClick = { run { state.result = service.resolve(item.key); resolvedKey = item.key; state.cart.clear(); refresh() } }) { Text(t("Résoudre", "Resolve")) }
                TextButton(enabled = !busy, onClick = { run { service.abandon(item.key); refresh() } }) { Text(t("Abandonner si non validée", "Discard if not committed")) }
            }
        }
    }
    @Composable fun receiptPanel(receipt: Receipt) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("pos-receipt"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(receipt.storeName, style = MaterialTheme.typography.titleLarge)
            Text("${receipt.id}\n${receipt.date}\n${receipt.address}\n${receipt.phone} ${receipt.email}")
            Text("${t("Vendeur", "Seller")} #${receipt.actorId} · ${receipt.status}")
            receipt.lines.forEach { Text("${it.name} · ${it.quantity} × ${DecimalInput.display(it.unitPrice, french)} = ${DecimalInput.display(it.total, french)} ${receipt.currency}") }
            Text("${t("Sous-total", "Subtotal")}: ${DecimalInput.display(receipt.subtotal, french)}\n${t("Remise", "Discount")}: ${DecimalInput.display(receipt.discount, french)}")
            Text("Total: ${DecimalInput.display(receipt.total, french)} ${receipt.currency}", style = MaterialTheme.typography.headlineSmall)
            Text("${t("Reçu", "Received")}: ${DecimalInput.display(receipt.received, french)} · ${t("Monnaie", "Change")}: ${DecimalInput.display(receipt.change, french)}")
            receipt.cancellationReason?.let { Text(it) }
            if ("POS:DELETE" in actor.permissions && receipt.status == "validated") {
                PosField(t("Motif d’annulation", "Cancellation reason"), reason, "pos-cancel-reason", !busy, false) { reason = it }
                OutlinedButton(enabled = !busy && reason.trim().length >= 3, onClick = { run {
                    val result = service.cancel(receipt.id, reason); selectedReceipt = result
                    if (state.result?.id == result.id) state.result = result
                    refresh()
                } }) { Text(t("Annuler cette facture", "Cancel invoice")) }
            }
            Button(enabled = !busy, onClick = { run {
                val key = state.submitted?.key ?: resolvedKey
                if (key != null) service.acknowledge(key)
                state.clear(); resolvedKey = null; selectedReceipt = null; mode = "pos"; refresh()
            } }) { Text(t("Nouvelle vente", "New sale")) }
        }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(12.dp).testTag("pos-layout-$layout"),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        header()
        val receipt = selectedReceipt ?: state.result
        when {
            receipt != null -> receiptPanel(receipt)
            mode == "cash" -> Column(Modifier.verticalScroll(rememberScrollState())) {
                cashRows.forEach { Text("${it.reference}\n${it.status} · ${it.openedAt}\n${t("Attendu / compté / écart", "Expected / counted / difference")}: ${DecimalInput.display(it.expected, french)} / ${it.counted?.let { value -> DecimalInput.display(value, french) } ?: "—"} / ${it.difference?.let { value -> DecimalInput.display(value, french) } ?: "—"}") }
                TextButton(enabled = !busy && historyOffset > 0, onClick = { run { historyOffset -= 40; cashRows = service.cashHistory(historyOffset) } }) { Text(t("Précédent", "Previous")) }
                TextButton(enabled = !busy && cashRows.size == 40, onClick = { run { historyOffset += 40; cashRows = service.cashHistory(historyOffset) } }) { Text(t("Suivant", "Next")) }
            }
            mode == "history" -> Column(Modifier.verticalScroll(rememberScrollState())) {
                PosField(t("Référence", "Reference"), historySearch, "pos-history-search", !busy, false) { historySearch = it }
                PosField(t("Depuis AAAA-MM-JJ", "From YYYY-MM-DD"), from, "pos-history-from", !busy, false) { from = it }
                PosField(t("Jusqu’au AAAA-MM-JJ", "To YYYY-MM-DD"), to, "pos-history-to", !busy, false) { to = it }
                Button(enabled = !busy, onClick = { run { historyOffset = 0; history = service.history(SaleFilter(historySearch, from, to)) } }) { Text(t("Filtrer", "Filter")) }
                if (history.items.isEmpty()) Text(t("Aucune facture", "No invoices"))
                history.items.forEach { item -> TextButton(enabled = !busy, onClick = { run { selectedReceipt = service.receipt(item.id) } }) { Text("${item.date} · ${item.id} · ${DecimalInput.display(item.total, french)} ${item.currency} · ${item.status}") } }
                TextButton(enabled = !busy && historyOffset > 0, onClick = { run { historyOffset -= 40; history = service.history(SaleFilter(historySearch, from, to, offset = historyOffset)) } }) { Text(t("Précédent", "Previous")) }
                TextButton(enabled = !busy && history.hasMore, onClick = { run { historyOffset += 40; history = service.history(SaleFilter(historySearch, from, to, offset = historyOffset)) } }) { Text(t("Suivant", "Next")) }
            }
            layout == "COMPACT" -> Column(Modifier.weight(1f)) {
                var tab by remember { mutableStateOf(false) }
                Row { TextButton(onClick = { tab = false }) { Text(t("Articles", "Products")) }; TextButton(modifier = Modifier.testTag("pos-cart-switch"), onClick = { tab = true }) { Text(t("Panier", "Cart")) } }
                if (tab) cartPanel(Modifier.weight(1f)) else productPanel(Modifier.weight(1f))
            }
            else -> Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(if (layout == "EXPANDED") 24.dp else 12.dp)) {
                productPanel(Modifier.weight(if (layout == "EXPANDED") 1.4f else 1f))
                cartPanel(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PosField(label: String, value: String, tag: String, enabled: Boolean, numeric: Boolean = true, changed: (String) -> Unit) {
    val focus = LocalFocusManager.current
    OutlinedTextField(value, changed, Modifier.fillMaxWidth().testTag(tag), enabled = enabled,
        singleLine = true, label = { Text(label) }, keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
}
