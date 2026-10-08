package com.vibe.store.presentation

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import com.vibe.store.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** In-memory presentation state only. Never an authorization decision or saved session. */
class CatalogScreenState {
    var filter by mutableStateOf(CatalogFilter())
    var presentedFilter by mutableStateOf(CatalogFilter())
    val listState = LazyListState()
    var selected by mutableStateOf<ProductView?>(null)
    var editing by mutableStateOf(false)
    var name by mutableStateOf(""); var category by mutableStateOf("")
    var hashtag by mutableStateOf(""); var description by mutableStateOf("")
    var price by mutableStateOf(""); var stock by mutableStateOf("0"); var minimum by mutableStateOf("0")
    var image by mutableStateOf<ImageEdit>(ImageEdit.Keep)
    private var initialForm: List<Any> = emptyList()
    private fun formValues(): List<Any> = listOf(name, category, hashtag, description, price, stock, minimum,
        if (image is ImageEdit.Remove && selected?.hasImage != true) ImageEdit.Keep else image)
    val hasFormChanges: Boolean get() = editing && formValues() != initialForm
    fun edit(product: ProductView?) {
        selected = product; editing = true
        name = product?.name ?: ""; category = product?.category ?: ""
        hashtag = product?.hashtag ?: ""; description = product?.description ?: ""
        price = product?.price?.let { DecimalInput.display(it) } ?: ""; stock = "0"; minimum = product?.minimumStock?.toString() ?: "0"
        image = ImageEdit.Keep
        initialForm = formValues()
    }
    fun discardEdit() {
        editing = false; image = ImageEdit.Keep
        name = ""; category = ""; hashtag = ""; description = ""; price = ""; stock = "0"; minimum = "0"
        initialForm = emptyList()
    }
}

@Composable
fun CatalogScreen(service: CatalogService, permissions: Set<String>, french: Boolean,
    state: CatalogScreenState, pickImage: ((SelectedImage?) -> Unit) -> Unit, back: () -> Unit) {
    fun t(fr: String, en: String) = if (french) fr else en
    var page by remember { mutableStateOf(CatalogPage(emptyList(), false)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var adjustment by remember { mutableStateOf(false) }
    var archive by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf("") }; var reason by remember { mutableStateOf("") }
    var prices by remember { mutableStateOf<List<PriceView>>(emptyList()) }
    var movements by remember { mutableStateOf<List<MovementView>>(emptyList()) }
    var history by remember { mutableStateOf("") }
    var movementFilter by remember { mutableStateOf(MovementFilter()) }
    var confirmDiscard by remember(state) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: CatalogFailure) {
                error = when (failure.code) {
                    CatalogError.DUPLICATE -> t("Nom ou hashtag déjà utilisé.", "Name or hashtag already in use.")
                    CatalogError.CONFLICT -> t("L’article a changé. Rechargez sa fiche.", "The product changed. Reload its details.")
                    CatalogError.INVALID_INPUT -> t("Vérifiez les champs : quantités entières, prix décimal valide, motif requis.", "Check fields: integer quantities, valid decimal price, required reason.")
                    CatalogError.MEDIA_TOO_LARGE -> t("Image trop volumineuse (5 Mio maximum).", "Image too large (maximum 5 MiB).")
                    else -> t("Opération impossible. Vérifiez l’image et le stockage, puis réessayez.", "Operation unavailable. Check image and storage, then retry.")
                }
            }
            catch (_: SecurityFailure) { error = t("Accès refusé ou session expirée.", "Access denied or session expired.") }
            catch (_: Exception) { error = t("Erreur de stockage. Aucune réussite n’est confirmée.", "Storage error. No success is confirmed.") }
            finally { busy = false }
        }
    }
    val filter = state.filter
    LaunchedEffect(service, filter, revision) {
        if (state.presentedFilter != filter) {
            state.listState.scrollToItem(0)
            state.presentedFilter = filter
        }
        try { page = service.list(filter) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { page = CatalogPage(emptyList(), false); error = t("Catalogue indisponible. Réessayez.", "Catalog unavailable. Retry.") }
    }
    val product = state.selected
    fun goBack() {
        if (busy) return
        when {
            state.editing -> if (state.hasFormChanges) confirmDiscard = true else state.discardEdit()
            history.isNotEmpty() -> history = ""
            product != null -> state.selected = null
            else -> back()
        }
    }
    BackHandler { goBack() }
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val columns = if (windowWidth < 600.dp) 1 else if (windowWidth < 840.dp) 2 else 3
    val layoutClass = when (columns) { 1 -> "COMPACT"; 2 -> "MEDIUM"; else -> "EXPANDED" }
    Box(Modifier.fillMaxSize().testTag("catalog-layout-$layoutClass").safeDrawingPadding().imePadding().padding(SpatialTokens.compactInset)) {
        LazyColumn(Modifier.fillMaxSize().testTag("catalog-list"), state = state.listState,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("S", Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("STORE", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Text(t("Catalogue et stock", "Catalog and stock"),
                            style = MaterialTheme.typography.headlineSmall)
                    }
                    TextButton(
    modifier = Modifier.testTag("catalog-back"),
    enabled = !busy,
    onClick = ::goBack
) { Text(t("Retour", "Back")) }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, Modifier.testTag("catalog-error"), color = MaterialTheme.colorScheme.error) }
            }
            if (state.editing) {
                item {
                    SpatialPanel(Modifier.fillMaxWidth().testTag("product-general-section")) {
                        Text(if (product == null) t("Nouvel article", "New product") else t("Modifier l’article", "Edit product"), style = MaterialTheme.typography.titleLarge)
                        Text(t("Informations générales", "General information"), Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
                        CatalogFormField(t("Nom", "Name"), state.name, "product-name", busy) { state.name = it }
                        CatalogFormField(t("Catégorie", "Category"), state.category, "product-category", busy) { state.category = it }
                        CatalogFormField("Hashtag", state.hashtag, "product-hashtag", busy) { state.hashtag = it }
                        CatalogFormField("Description", state.description, "product-description", busy) { state.description = it }
                    }
                }
                item {
                    SpatialPanel(Modifier.fillMaxWidth().testTag("product-values-section")) {
                        Text(t("Prix et stock", "Price and stock"), Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.titleMedium)
                        CatalogFormField(t("Prix unitaire", "Unit price"), state.price, "product-price", busy, true) { state.price = it }
                        if (product == null) CatalogFormField(t("Stock initial", "Initial stock"), state.stock, "product-stock", busy, true) { state.stock = it }
                        CatalogFormField(t("Seuil de stock", "Stock threshold"), state.minimum, "product-minimum", busy, true) { state.minimum = it }
                    }
                }
                item {
                    SpatialPanel(Modifier.fillMaxWidth().testTag("product-image-section")) {
                        Text("Image", Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.titleMedium)
                        Text(t("JPEG, PNG ou WebP · 5 Mio maximum", "JPEG, PNG or WebP · maximum 5 MiB"))
                        OutlinedButton(enabled = !busy, onClick = { pickImage { selection -> if (selection != null) state.image = ImageEdit.Replace(selection) } }) { Text(t("Choisir une image", "Choose image")) }
                        Text(when (state.image) { is ImageEdit.Replace -> t("Nouvelle image sélectionnée", "New image selected"); ImageEdit.Remove -> t("Image à retirer", "Image will be removed"); else -> if (product?.hasImage == true) t("Image actuelle conservée", "Current image retained") else t("Aucune image", "No image") })
                        TextButton(enabled = !busy, onClick = { state.image = ImageEdit.Remove }) { Text(t("Retirer l’image", "Remove image")) }
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(modifier = Modifier.testTag("product-save"), enabled = !busy, onClick = { run {
                            val result = service.save(ProductDraft(product?.id, state.name, state.category, state.hashtag, state.description,
                                DecimalInput.nonnegative(state.price) ?: throw CatalogFailure(CatalogError.INVALID_INPUT),
                                state.stock.toDoubleOrNull() ?: Double.NaN, state.minimum.toDoubleOrNull() ?: Double.NaN, product?.updatedAt), state.image)
                            state.selected = result.product; state.editing = false; state.image = ImageEdit.Keep; revision++
                            if (result.cleanupDeferred) error = t("Article enregistré. Nettoyage de l’ancienne image différé.", "Product saved. Old image cleanup deferred.")
                        } }) { Text(t("Enregistrer", "Save")) }
                    }
                }
            } else if (product != null) {
                item {
                    SpatialPanel(Modifier.fillMaxWidth()) {
                        Text(t("FICHE ARTICLE", "PRODUCT DETAILS"), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                        Text(product.name, style = MaterialTheme.typography.headlineSmall)
                        Text("${product.category} · ${product.hashtag}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        CatalogStockStatus(product, french)
                        if (product.description.isNotBlank()) Text(product.description)
                        if (product.hasImage) CatalogImage(service, product, french)
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CatalogMetric(t("Prix", "Price"), catalogAmount(product.price, french),
                                Modifier.weight(1f))
                            CatalogMetric(t("En stock", "In stock"), product.stock.toString(),
                                Modifier.weight(1f))
                        }
                        // Retain the exact legacy summary for existing I04 UI assertions.
                        Text(t("Prix : ${catalogAmount(product.price, french)} · Stock : ${product.stock}",
                            "Price: ${catalogAmount(product.price, french)} · Stock: ${product.stock}"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(t("Seuil : ${product.minimumStock}", "Threshold: ${product.minimumStock}"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (product.archived) Text(t("Archivé · historique conservé", "Archived · history retained"))
                        if (!product.archived && "PRODUCTS:UPDATE" in permissions)
                            Button(enabled = !busy, onClick = { state.edit(product) }) { Text(t("Modifier", "Edit")) }
                        if (!product.archived && "STOCKS:UPDATE" in permissions)
                            OutlinedButton(enabled = !busy, onClick = { target = product.stock.toString(); reason = ""; adjustment = true }) {
                                Text(t("Ajuster le stock", "Adjust stock"))
                            }
                        if (!product.archived && "PRODUCTS:DELETE" in permissions)
                            TextButton(enabled = !busy, onClick = { archive = true }) { Text(t("Archiver", "Archive")) }
                        HorizontalDivider()
                        Text(t("Historique", "History"), style = MaterialTheme.typography.titleMedium)
                        TextButton(enabled = !busy, onClick = { run { prices = service.prices(product.id); history = "prices" } }) {
                            Text(t("Historique des prix", "Price history"))
                        }
                        if ("STOCKS:READ" in permissions)
                            TextButton(enabled = !busy, onClick = { run { movementFilter = MovementFilter(productId = product.id); movements = service.movements(movementFilter); history = "stock" } }) {
                                Text(t("Mouvements de stock", "Stock movements"))
                            }
                    }
                }
                if (history == "prices") {
                    item {
                        Text(t("Historique des prix", "Price history"), style = MaterialTheme.typography.titleMedium)
                        Text(t("200 derniers prix maximum", "Up to 200 latest prices"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (prices.isEmpty()) Text(t("Aucun changement de prix", "No price changes"))
                    }
                    items(prices, key = { it.id }) { price ->
                        SpatialPanel(Modifier.fillMaxWidth().testTag("catalog-price-history-${price.id}")) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(t("Prix enregistré", "Recorded price"), style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(catalogLocalTime(price.recordedAt, french), style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(catalogAmount(price.price, french), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
                if (history == "stock") {
                    item {
                        Text(t("Mouvements · pages de 50", "Movements · 50 per page"))
                        CatalogField(t("Du (AAAA-MM-JJ)", "From (YYYY-MM-DD)"), movementFilter.from, "movement-from", busy) { movementFilter = movementFilter.copy(from = it, offset = 0) }
                        CatalogField(t("Au (AAAA-MM-JJ)", "To (YYYY-MM-DD)"), movementFilter.to, "movement-to", busy) { movementFilter = movementFilter.copy(to = it, offset = 0) }
                        Button(enabled = !busy, onClick = { run { movements = service.movements(movementFilter) } }) { Text(t("Filtrer", "Filter")) }
                    }
                    items(movements, key = { it.id }) { movement ->
                        SpatialPanel(Modifier.fillMaxWidth().testTag("catalog-movement-${movement.id}")) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(movementLabel(movement.reason, french), style = MaterialTheme.typography.titleSmall)
                                    Text(catalogLocalTime(movement.createdAt, french),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(movement.quantity.toString(), style = MaterialTheme.typography.titleMedium,
                                    color = if (movement.quantity < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                            }
                            movement.reference?.takeIf { it.isNotBlank() }?.let { reference ->
                                Text(t("Référence : $reference", "Reference: $reference"),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(enabled = !busy && movementFilter.offset > 0, onClick = { run { movementFilter = movementFilter.copy(offset = maxOf(0, movementFilter.offset - 50)); movements = service.movements(movementFilter) } }) { Text(t("Précédent", "Previous")) }
                        OutlinedButton(enabled = !busy && movements.size == 50, onClick = { run { movementFilter = movementFilter.copy(offset = movementFilter.offset + 50); movements = service.movements(movementFilter) } }) { Text(t("Suivant", "Next")) }
                    } }
                }
            } else {
                item {
                    CatalogField(t("Rechercher nom, catégorie ou hashtag", "Search name, category or hashtag"), filter.search, "catalog-search", false) { state.filter = filter.copy(search = it, offset = 0) }
                    CatalogField(t("Catégorie exacte (vide : toutes)", "Exact category (empty: all)"), filter.category, "catalog-category", false) { state.filter = filter.copy(category = it, offset = 0) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = filter.archived, onClick = { state.filter = filter.copy(archived = !filter.archived, offset = 0) }, label = { Text(t("Archives", "Archived")) })
                        FilterChip(selected = filter.stock == "low", onClick = { state.filter = filter.copy(stock = if (filter.stock == "low") "all" else "low", offset = 0) }, label = { Text(t("Stock faible", "Low stock")) })
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(t("${page.items.size} articles sur cette page", "${page.items.size} products on this page"),
                            Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(t("Page ${filter.offset / maxOf(1, filter.limit) + 1}",
                            "Page ${filter.offset / maxOf(1, filter.limit) + 1}"),
                            style = MaterialTheme.typography.labelMedium)
                    }
                    if ("PRODUCTS:UPDATE" in permissions) Button(onClick = { state.edit(null) }, modifier = Modifier.testTag("product-create")) { Text(t("Nouvel article", "New product")) }
                    TextButton(onClick = { revision++ }) { Text(t("Actualiser", "Refresh")) }
                    if (page.items.isEmpty()) Text(t("Aucun article", "No products"))
                }
                items(page.items.chunked(columns), key = { it.first().id }) { row ->
                    Row(Modifier.testTag("catalog-row-${row.first().id}"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { p ->
                            CatalogProductCard(p, french, Modifier.weight(1f),
                                onClick = { run { state.selected = service.detail(p.id); history = "" } })
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(enabled = filter.offset > 0, onClick = { state.filter = filter.copy(offset = maxOf(0, filter.offset - filter.limit)) }) { Text(t("Précédent", "Previous")) }
                    OutlinedButton(enabled = page.hasMore, onClick = { state.filter = filter.copy(offset = filter.offset + filter.limit) }) { Text(t("Suivant", "Next")) }
                } }
            }
        }
    }
    if (confirmDiscard && state.editing) AlertDialog(
        modifier = Modifier.testTag("product-discard-dialog"),
        onDismissRequest = { if (!busy) confirmDiscard = false },
        title = { Text(t("Abandonner les modifications ?", "Discard changes?")) },
        text = { Text(t("Les saisies non enregistrées seront perdues. L’article enregistré reste inchangé.", "Unsaved entries will be lost. The saved product remains unchanged.")) },
        confirmButton = { TextButton(modifier = Modifier.testTag("product-discard-confirm"), enabled = !busy,
            onClick = { if (!busy) { state.discardEdit(); confirmDiscard = false } }) { Text(t("Abandonner", "Discard")) } },
        dismissButton = { TextButton(modifier = Modifier.testTag("product-discard-cancel"), enabled = !busy,
            onClick = { confirmDiscard = false }) { Text(t("Continuer la saisie", "Keep editing")) } })
    if (adjustment && product != null) AlertDialog(onDismissRequest = { if (!busy) adjustment = false },
        title = { Text(t("Stock cible justifié", "Justified target stock")) },
        text = { Column { CatalogField(t("Quantité cible", "Target quantity"), target, "stock-target", busy, true) { target = it }; CatalogField(t("Motif (3 caractères minimum)", "Reason (minimum 3 characters)"), reason, "stock-reason", busy) { reason = it }; error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
        confirmButton = { TextButton(enabled = !busy, onClick = { run { state.selected = service.adjustStock(product.id, target.toDoubleOrNull() ?: Double.NaN, reason, product.stock); adjustment = false; revision++ } }) { Text(t("Confirmer", "Confirm")) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { adjustment = false }) { Text(t("Annuler", "Cancel")) } })
    if (archive && product != null) AlertDialog(onDismissRequest = { if (!busy) archive = false }, title = { Text(t("Archiver cet article ?", "Archive this product?")) },
        text = { Text(t("Le stock passera à zéro avec un mouvement traçable. L’historique et l’image seront conservés.", "Stock becomes zero with a traceable movement. History and image are retained.")) },
        confirmButton = { TextButton(enabled = !busy, onClick = { run { state.selected = service.archive(product.id, product.updatedAt); archive = false; revision++ } }) { Text(t("Archiver", "Archive")) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { archive = false }) { Text(t("Annuler", "Cancel")) } })
}


/** UI-only formatting. No shop currency is assumed until configuration exposes it. */
private fun catalogAmount(price: java.math.BigDecimal, french: Boolean): String =
    DecimalInput.display(price, french)

/** UTC is retained in persistence; legacy/unparseable timestamps remain visible. */
private fun catalogLocalTime(raw: String, french: Boolean): String = runCatching {
    val parsed = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'").firstNotNullOfOrNull { format ->
        runCatching {
            SimpleDateFormat(format, Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(raw)
        }.getOrNull()
    } ?: return@runCatching raw
    SimpleDateFormat("dd MMM yyyy · HH:mm", if (french) Locale.FRENCH else Locale.ENGLISH).apply {
        timeZone = TimeZone.getDefault()
    }.format(parsed)
}.getOrDefault(raw)

@Composable
private fun CatalogStockStatus(product: ProductView, french: Boolean) {
    val out = product.stock == 0L && !product.archived
    val low = product.stock > 0L && product.stock <= product.minimumStock && !product.archived
    val status = when {
        product.archived -> if (french) "Archivé" else "Archived"
        out -> if (french) "Rupture de stock" else "Out of stock"
        low -> if (french) "Stock faible" else "Low stock"
        else -> if (french) "Disponible" else "Available"
    }
    val color = when {
        out || low -> MaterialTheme.colorScheme.errorContainer
        product.archived -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val onColor = when {
        out || low -> MaterialTheme.colorScheme.onErrorContainer
        product.archived -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(shape = RoundedCornerShape(10.dp), color = color) {
        Text(status, Modifier.testTag("product-stock-status-${product.id}")
            .padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium, color = onColor)
    }
}

@Composable
private fun CatalogMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun CatalogProductCard(product: ProductView, french: Boolean, modifier: Modifier = Modifier,
    onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = modifier.testTag("product-${product.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer,
                    RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Text(product.name.take(1).uppercase(Locale.getDefault()).ifBlank { "#" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text(product.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Text(product.category, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            CatalogStockStatus(product, french)
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(if (french) "Prix" else "Price", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(catalogAmount(product.price, french), style = MaterialTheme.typography.titleMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (french) "En stock" else "In stock", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(product.stock.toString(), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Form-only IME behavior, identical to TeamField; never submits a mutation. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable private fun CatalogFormField(label: String, value: String, tag: String, busy: Boolean, numeric: Boolean = false, change: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(value, change, Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag(tag), enabled = !busy,
        label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(force = true); keyboard?.hide() }))
}

@Composable private fun CatalogField(label: String, value: String, tag: String, busy: Boolean, numeric: Boolean = false, change: (String) -> Unit) {
    OutlinedTextField(value, change, Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag(tag), enabled = !busy, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text))
}

private fun movementLabel(reason: String, fr: Boolean): String = when {
    reason == "initial" -> if (fr) "Stock initial" else "Initial stock"
    reason == "product_deletion" -> if (fr) "Archivage" else "Archive"
    reason.startsWith("adjustment:") -> (if (fr) "Ajustement : " else "Adjustment: ") + reason.substringAfter(':')
    reason == "sale" -> if (fr) "Vente" else "Sale"
    reason == "invoice_reversal" -> if (fr) "Annulation de vente" else "Sale reversal"
    reason == "purchase" -> if (fr) "Achat" else "Purchase"
    reason == "purchase_cancellation" -> if (fr) "Annulation d’achat" else "Purchase cancellation"
    reason == "inventory" -> if (fr) "Inventaire" else "Inventory"
    reason == "adjustment" -> if (fr) "Ajustement" else "Adjustment"
    else -> reason
}

@Composable private fun CatalogImage(service: CatalogService, product: ProductView, fr: Boolean) {
    var bitmap by remember(product.id, product.updatedAt) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var failed by remember(product.id, product.updatedAt) { mutableStateOf(false) }
    LaunchedEffect(product.id, product.updatedAt) {
        try {
            val image = service.image(product.id)
            bitmap = withContext(Dispatchers.Default) {
                image?.bytes?.let { bytes ->
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }
            failed = bitmap == null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    bitmap?.let { Image(it.asImageBitmap(), product.name, Modifier.fillMaxWidth().heightIn(max = 240.dp), contentScale = ContentScale.Fit) }
    if (failed) Text(if (fr) "Aperçu de l’image indisponible" else "Image preview unavailable")
}
