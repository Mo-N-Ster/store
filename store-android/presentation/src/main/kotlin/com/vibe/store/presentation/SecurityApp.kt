package com.vibe.store.presentation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.window.core.layout.WindowWidthSizeClass
import com.vibe.store.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Navigation renders authority responses. No role supplied here can grant rights. */
@Composable
fun SecurityApp(service: IdentityService, back: () -> Unit) {
    var identity by remember { mutableStateOf<PublicIdentity?>(null) }
    var page by remember { mutableStateOf("loading") }
    var prefs by remember { mutableStateOf(DisplayPreferences()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<SecurityError?>(null) }
    var question by remember { mutableStateOf<RecoveryQuestion?>(null) }
    var config by remember { mutableStateOf(FoundationSettings()) }
    val scope = rememberCoroutineScope()
    val fr = prefs.language == "fr"
    fun text(french: String, english: String) = if (fr) french else english
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { action() } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: SecurityFailure) { error = failure.code }
            catch (_: Exception) { error = SecurityError.UNAVAILABLE }
            finally { busy = false }
        }
    }
    suspend fun refresh() {
        prefs = service.preferences(); identity = service.current()
        page = if (identity != null) "home" else if (service.needsOwner()) "bootstrap" else "login"
    }
    LaunchedEffect(service) { run { refresh() } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, service) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) identity = null // hide cached private data
            if (event == Lifecycle.Event.ON_RESUME) run { refresh() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val width = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass
    val widthLabel = when (width) { WindowWidthSizeClass.COMPACT -> "COMPACT"; WindowWidthSizeClass.MEDIUM -> "MEDIUM"; else -> "EXPANDED" }
    val dark = when (prefs.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    SpatialTheme(dark) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().fillMaxSize().verticalScroll(rememberScrollState())
                .padding(if (widthLabel == "COMPACT") SpatialTokens.compactInset else SpatialTokens.wideInset),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("STORE · 57€|2£!/v9", Modifier.testTag("security-brand"), style = MaterialTheme.typography.titleLarge)
                Text(widthLabel, Modifier.testTag("security-width"), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(enabled = !busy, onClick = { run { val next = prefs.copy(language = if (fr) "en" else "fr"); service.preferences(next); prefs = next } }) { Text(if (fr) "EN" else "FR") }
                    TextButton(enabled = !busy, onClick = { run { val next = prefs.copy(theme = if (dark) "light" else "dark"); service.preferences(next); prefs = next } }) { Text(text("Thème", "Theme")) }
                    TextButton(enabled = !busy, onClick = back) { Text(text("Fondation", "Foundation")) }
                }
                if (widthLabel == "EXPANDED") Text(text("Identité locale · données privées · accès contrôlé", "Local identity · private data · controlled access"), style = MaterialTheme.typography.headlineSmall)
                SpatialPanel(Modifier.widthIn(max = if (widthLabel == "COMPACT") 480.dp else 640.dp).fillMaxWidth()) {
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let {
                        Text(when (it) {
                            SecurityError.CASH_OPEN -> text("Fermez votre caisse avant de changer d’utilisateur ou de vous déconnecter.", "Close your cash session before switching or signing out.")
                            SecurityError.OPERATION_ACTIVE -> text("Une opération est en cours.", "An operation is in progress.")
                            SecurityError.INVALID_CREDENTIALS -> text("Identifiants invalides ou accès temporairement verrouillé.", "Invalid credentials or temporarily locked access.")
                            SecurityError.INVALID_INPUT -> text("Vérifiez les champs saisis.", "Check the entered fields.")
                            else -> text("Opération indisponible ou non autorisée. Vous pouvez réessayer.", "Operation unavailable or not authorized. You can retry.")
                        }, Modifier.testTag("security-error"), color = MaterialTheme.colorScheme.error)
                    }
                    // key disposes every secret field when leaving a form; never rememberSaveable.
                    key(page) {
                        when (page) {
                            "loading" -> Text(text("Vérification du stockage…", "Checking storage…"))
                            "bootstrap" -> {
                                Text(text("Créer le propriétaire", "Create owner"), style = MaterialTheme.typography.headlineSmall)
                                var username by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }
                                var first by remember { mutableStateOf("") }; var last by remember { mutableStateOf("") }
                                var password by remember { mutableStateOf("") }; var prompt by remember { mutableStateOf("") }; var answer by remember { mutableStateOf("") }
                                Field(text("Identifiant", "Username"), username, "username", busy) { username = it }
                                Field("Email", email, "email", busy) { email = it }
                                Field(text("Prénom", "First name"), first, "first-name", busy) { first = it }
                                Field(text("Nom", "Last name"), last, "last-name", busy) { last = it }
                                PasswordField(text("Mot de passe (8 caractères minimum)", "Password (minimum 8 characters)"), password, busy, fr) { password = it }
                                Field(text("Question de récupération", "Recovery question"), prompt, "question", busy) { prompt = it }
                                PasswordField(text("Réponse personnelle", "Personal answer"), answer, busy, fr, "answer") { answer = it }
                                Button(enabled = !busy, onClick = { run { service.bootstrap(OwnerRegistration(username, email, first, last, password, prompt, answer)); page = "login" } }) { Text(text("Créer le propriétaire", "Create owner")) }
                                Text(text("La configuration de la boutique reste une opération séparée après connexion.", "Shop configuration is a separate operation after login."))
                            }
                            "login", "switch" -> {
                                val switching = page == "switch"
                                Text(if (switching) text("Changer d’utilisateur", "Switch user") else text("Connexion", "Sign in"), Modifier.testTag("login-title"), style = MaterialTheme.typography.headlineSmall)
                                var identifier by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
                                Field(text("Identifiant, email ou nom complet", "Username, email or full name"), identifier, "identifier", busy) { identifier = it }
                                PasswordField(text("Mot de passe", "Password"), password, busy, fr) { password = it }
                                Button(modifier = Modifier.testTag("login-submit"), enabled = !busy, onClick = { run {
                                    identity = if (switching) service.switchUser(Credentials(identifier, password)) else service.login(Credentials(identifier, password)); page = "home"
                                } }) { Text(text("Continuer", "Continue")) }
                                TextButton(enabled = !busy, onClick = { error = null; page = if (switching) "home" else "recovery" }) { Text(if (switching) text("Annuler", "Cancel") else text("Mot de passe oublié", "Forgot password")) }
                            }
                            "recovery" -> {
                                var identifier by remember { mutableStateOf("") }
                                Text(text("Récupération du compte", "Account recovery"), style = MaterialTheme.typography.headlineSmall)
                                Field(text("Identifiant", "Identifier"), identifier, "identifier", busy) { identifier = it }
                                Button(enabled = !busy, onClick = { run { question = service.recoveryQuestion(identifier); page = "answer" } }) { Text(text("Continuer", "Continue")) }
                                TextButton(enabled = !busy, onClick = { page = "login"; error = null }) { Text(text("Retour connexion", "Back to login")) }
                            }
                            "answer" -> {
                                var answer by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
                                Text(question?.question ?: "")
                                PasswordField(text("Réponse", "Answer"), answer, busy, fr, "answer") { answer = it }
                                PasswordField(text("Nouveau mot de passe", "New password"), password, busy, fr) { password = it }
                                Button(enabled = !busy, onClick = { run { service.recover(RecoveryProof(question!!.accountId, answer, password)); question = null; page = "login" } }) { Text(text("Modifier", "Update")) }
                                TextButton(enabled = !busy, onClick = { question = null; page = "login"; error = null }) { Text(text("Annuler", "Cancel")) }
                            }
                            "home" -> identity?.let { user ->
                                Text("${user.firstName} ${user.lastName}", Modifier.testTag("authenticated-name"), style = MaterialTheme.typography.headlineSmall)
                                Text(text("Session locale authentifiée", "Authenticated local session"))
                                Text(text("Aucune présence n’est signée par la connexion.", "Signing in does not record attendance."))
                                Button(enabled = !busy, onClick = { page = "switch" }) { Text(text("Changer d’utilisateur", "Switch user")) }
                                if ("SETTINGS:READ" in user.permissions) Button(enabled = !busy, onClick = { run { config = service.settings(); page = "settings" } }) { Text(text("Configurer la boutique", "Configure shop")) }
                                OutlinedButton(enabled = !busy, onClick = { run { service.logout(); identity = null; page = "login" } }) { Text(text("Déconnexion", "Sign out")) }
                            }
                            "settings" -> {
                                var draft by remember { mutableStateOf(config) }
                                Text(text("Configuration de la boutique", "Shop configuration"), style = MaterialTheme.typography.headlineSmall)
                                Field(text("Nom boutique", "Shop name"), draft.storeName, "store-name", busy) { draft = draft.copy(storeName = it) }
                                Field(text("Adresse", "Address"), draft.address, "address", busy) { draft = draft.copy(address = it) }
                                Field(text("Téléphone", "Phone"), draft.phone, "phone", busy) { draft = draft.copy(phone = it) }
                                Field("Email", draft.email, "shop-email", busy) { draft = draft.copy(email = it) }
                                Field(text("Devise : EUR/XOF/XAF/CAD/GBP/CHF/NGN/GHS", "Currency: EUR/XOF/XAF/CAD/GBP/CHF/NGN/GHS"), draft.currency, "currency", busy) { draft = draft.copy(currency = it) }
                                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = draft.discountsEnabled, enabled = !busy, onCheckedChange = { draft = draft.copy(discountsEnabled = it) }); Text(text("Autoriser les remises", "Enable discounts")) }
                                Button(enabled = !busy, onClick = { run { service.configure(draft); page = "home" } }) { Text(text("Enregistrer", "Save")) }
                                TextButton(enabled = !busy, onClick = { page = "home" }) { Text(text("Retour", "Back")) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Field(label: String, value: String, tag: String, busy: Boolean, changed: (String) -> Unit) {
    OutlinedTextField(value, changed, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(tag), label = { Text(label) }, singleLine = true, enabled = !busy)
}

@Composable fun PasswordField(label: String, value: String, busy: Boolean, french: Boolean, tag: String = "password", changed: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val iconColor = MaterialTheme.colorScheme.onSurfaceVariant
    val eyeLabel = if (visible) { if (french) "Masquer le mot de passe" else "Hide password" } else { if (french) "Afficher le mot de passe" else "Show password" }
    OutlinedTextField(value, changed, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(tag),
        label = { Text(label) }, singleLine = true, enabled = !busy,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = { IconButton(enabled = !busy, modifier = Modifier.testTag("$tag-eye").semantics { contentDescription = eyeLabel }, onClick = { visible = !visible }) {
            Canvas(Modifier.size(24.dp)) {
                drawOval(iconColor, Offset(1.dp.toPx(), 6.dp.toPx()), Size(22.dp.toPx(), 12.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                drawCircle(iconColor, 3.dp.toPx(), style = Stroke(1.5.dp.toPx()))
                if (visible) drawLine(iconColor, Offset(2.dp.toPx(), 2.dp.toPx()), Offset(22.dp.toPx(), 22.dp.toPx()), 1.5.dp.toPx())
            }
        } })
}
