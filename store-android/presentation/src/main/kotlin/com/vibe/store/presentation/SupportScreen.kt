package com.vibe.store.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.vibe.store.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

/** I08 UI is only a client of already-authorized use cases; no direct Room or role trust. */
@Composable
fun SupportScreen(
    support: SupportService, identity: IdentityService, actor: PublicIdentity, french: Boolean,
    onSettings: () -> Unit, onBack: () -> Unit, onNavigationLock: (Boolean) -> Unit,
    documents: DocumentService? = null,
    saveDocument: (DocumentResult, (PdfSaveOutcome) -> Unit) -> Unit = { _, complete -> complete(PdfSaveOutcome.Failed()) },
) {
    fun t(fr: String, en: String) = if (french) fr else en
    val admin = actor.role == "owner" && "ADMINISTRATION:READ" in actor.permissions
    val canManage = admin && "ADMINISTRATION:UPDATE" in actor.permissions
    var tab by remember(actor.id) { mutableStateOf("chat") }
    var busy by remember(actor.id) { mutableStateOf(false) }
    var failure by remember(actor.id) { mutableStateOf<String?>(null) }
    var notice by remember(actor.id) { mutableStateOf<String?>(null) }
    var recipients by remember(actor.id) { mutableStateOf(emptyList<SupportAccount>()) }
    var messages by remember(actor.id) { mutableStateOf(emptyList<LocalMessage>()) }
    var alerts by remember(actor.id) { mutableStateOf(emptyList<LocalAlert>()) }
    var audits by remember(actor.id) { mutableStateOf(emptyList<AuditEntry>()) }
    var health by remember(actor.id) { mutableStateOf<SupportHealth?>(null) }
    var recentFailures by remember(actor.id) { mutableStateOf(emptyList<AuditEntry>()) }
    var permission by remember(actor.id) { mutableStateOf<PermissionView?>(null) }
    var deniedDraft by remember(actor.id) { mutableStateOf(emptySet<String>()) }
    var selectedAccount by remember(actor.id) { mutableStateOf<Long?>(null) }
    var selectedRecipient by remember(actor.id) { mutableStateOf<Long?>(null) }
    var broadcast by remember(actor.id) { mutableStateOf(false) }
    var subject by remember(actor.id) { mutableStateOf("") }
    var body by remember(actor.id) { mutableStateOf("") }
    var requestId by remember(actor.id) { mutableStateOf(UUID.randomUUID().toString()) }
    var offset by remember(actor.id, tab) { mutableIntStateOf(0) }
    var unreadOnly by remember(actor.id) { mutableStateOf(false) }
    var auditFilter by remember(actor.id) { mutableStateOf(AuditFilter()) }
    var auditActorInput by remember(actor.id) { mutableStateOf("") }
    var helpSearch by remember(actor.id) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val hasDraft = tab == "chat" && (subject.isNotBlank() || body.isNotBlank())
    LaunchedEffect(hasDraft) { onNavigationLock(hasDraft) }
    DisposableEffect(actor.id) { onDispose { onNavigationLock(false) } }

    fun execute(action: suspend () -> Unit) {
        if (busy) return
        busy = true; failure = null; notice = null
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failureCode: SecurityFailure) { failure = failureCode.code.name }
            catch (_: Exception) { failure = t("Action indisponible ; réessayez.", "Action unavailable; retry.") }
            finally { busy = false }
        }
    }
    suspend fun refresh() {
        when (tab) {
            "chat" -> {
                recipients = support.recipients()
                messages = support.messages(offset, unreadOnly)
            }
            "admin" -> if (admin) recipients = support.recipients()
            "alerts" -> if (admin) alerts = support.alerts(offset)
            "audit" -> if (admin) audits = support.audit(auditFilter.copy(offset = offset))
            "diagnostics" -> if (admin) {
                health = support.diagnostics()
                recentFailures = support.audit(AuditFilter(outcome = "failure"))
            }
        }
    }
    LaunchedEffect(tab, actor.id, offset, unreadOnly) { execute { refresh() } }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
            .padding(16.dp).testTag("i08-support"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(t("Messagerie et assistance", "Messages and support"), style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = !busy && !hasDraft, onClick = onBack) { Text(t("Retour", "Back")) }
            TextButton(enabled = !busy, onClick = { execute { refresh() } }) { Text(t("Actualiser", "Refresh")) }
        }
        // Wrap tabs instead of squeezing six actions into the compact bottom navigation.
        listOf("chat" to t("Chat", "Chat"), "help" to t("Aide", "Help")).forEach { (target, label) ->
            if (target == tab) Text("• $label", style = MaterialTheme.typography.titleMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("chat" to "Chat", "help" to t("Aide", "Help")).forEach { (target, label) ->
                OutlinedButton(enabled = !busy && !hasDraft, onClick = { tab = target; failure = null },
                    modifier = Modifier.testTag("i08-tab-$target")) { Text(label) }
            }
        }
        if (admin) {
            listOf("admin" to t("Administration", "Administration"),
                "alerts" to t("Alertes", "Alerts"), "audit" to "Audit",
                "diagnostics" to t("Diagnostic", "Diagnostics")).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (target, label) ->
                        OutlinedButton(enabled = !busy && !hasDraft, onClick = { tab = target; failure = null },
                            modifier = Modifier.testTag("i08-tab-$target")) { Text(label) }
                    }
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        failure?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("i08-error")) }
        notice?.let { Text(it, modifier = Modifier.testTag("i08-notice")) }
        HorizontalDivider()
        when (tab) {
            "chat" -> {
                Text(t("Nouveau message", "New message"), style = MaterialTheme.typography.titleLarge)
                if (canManage) Row {
                    Checkbox(broadcast, enabled = !busy, onCheckedChange = {
                        broadcast = it; selectedRecipient = null
                    }, modifier = Modifier.testTag("i08-broadcast"))
                    Text(t("Diffusion à tous les comptes actifs", "Broadcast to all active accounts"))
                }
                if (!broadcast) {
                    Text(t("Destinataire", "Recipient"))
                    if (recipients.isEmpty()) Text(t("Aucun destinataire actif", "No active recipients"))
                    recipients.forEach { member ->
                        Row {
                            RadioButton(selectedRecipient == member.id, enabled = !busy,
                                onClick = { selectedRecipient = member.id },
                                modifier = Modifier.testTag("i08-recipient-${member.id}"))
                            Text("${member.fullName} (${member.username})")
                        }
                    }
                }
                OutlinedTextField(subject, { subject = it.take(200) }, Modifier.fillMaxWidth().testTag("i08-subject"),
                    label = { Text(t("Objet (200 max)", "Subject (200 max)")) }, enabled = !busy)
                OutlinedTextField(body, { body = it.take(10_000) }, Modifier.fillMaxWidth().heightIn(min = 110.dp).testTag("i08-body"),
                    label = { Text(t("Message (10000 max)", "Message (10000 max)")) }, enabled = !busy)
                if (hasDraft) Text(t("Brouillon non enregistré : terminer ou effacer avant de quitter.",
                    "Unsent draft: send or clear before leaving."), color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy && subject.isNotBlank() && body.isNotBlank() &&
                        (broadcast || selectedRecipient != null), modifier = Modifier.testTag("i08-send"), onClick = {
                        execute {
                            support.send(MessageDraft(requestId, selectedRecipient, broadcast, subject, body))
                            subject = ""; body = ""; requestId = UUID.randomUUID().toString()
                            notice = t("Message envoyé localement", "Local message sent")
                            messages = support.messages(0, unreadOnly); offset = 0
                        }
                    }) { Text(t("Envoyer", "Send")) }
                    TextButton(enabled = !busy && hasDraft, onClick = {
                        subject = ""; body = ""; requestId = UUID.randomUUID().toString()
                    }) { Text(t("Effacer brouillon", "Discard draft")) }
                }
                HorizontalDivider()
                Row {
                    Checkbox(unreadOnly, enabled = !busy && !hasDraft, onCheckedChange = { unreadOnly = it; offset = 0 },
                        modifier = Modifier.testTag("i08-unread"))
                    Text(t("Non lus uniquement", "Unread only"))
                }
                if (messages.isEmpty() && !busy) Text(t("Aucun message", "No messages"))
                messages.forEach { msg ->
                    Card(Modifier.fillMaxWidth().testTag("i08-message-${msg.id}")) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(msg.subject, style = MaterialTheme.typography.titleMedium)
                            Text("${msg.createdAt} — ${msg.senderName}" +
                                if (msg.outgoing) t(" · envoyé", " · sent") else if (!msg.read) t(" · non lu", " · unread") else "")
                            Text(msg.content)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (!msg.outgoing && !msg.read) TextButton(enabled = !busy, onClick = {
                                    execute { support.markRead(msg.id); messages = support.messages(offset, unreadOnly) }
                                }) { Text(t("Marquer lu", "Mark read")) }
                                TextButton(enabled = !busy, onClick = {
                                    execute { support.hideMessage(msg.id); messages = support.messages(offset, unreadOnly) }
                                }) { Text(t("Masquer pour moi", "Hide for me")) }
                            }
                        }
                    }
                }
                PagingControls(offset, !busy && !hasDraft, messages.size == 40) { offset = it }
            }
            "admin" -> if (admin) {
                Text(t("Comptes et permissions", "Accounts and permissions"), style = MaterialTheme.typography.titleLarge)
                Text(t("Les restrictions retirent des droits ; elles ne peuvent pas en ajouter.",
                    "Restrictions can only remove permissions, not grant new ones."))
                OutlinedButton(enabled = !busy, onClick = onSettings) { Text(t("Paramètres boutique", "Shop settings")) }
                recipients.forEach { member ->
                    TextButton(enabled = !busy, onClick = {
                        selectedAccount = member.id
                        execute {
                            val next = identity.permissions(member.id)
                            permission = next; deniedDraft = next.denied
                        }
                    }, modifier = Modifier.testTag("i08-account-${member.id}")) {
                        Text("${member.fullName} · ${member.role}")
                    }
                }
                permission?.takeIf { it.accountId == selectedAccount }?.let { p ->
                    Text("${p.role} · #${p.accountId}", style = MaterialTheme.typography.titleMedium)
                    p.inherited.sorted().forEach { code ->
                        Row {
                            Checkbox(checked = code in deniedDraft, enabled = canManage && !busy,
                                onCheckedChange = { enabled ->
                                    deniedDraft = if (enabled) deniedDraft + code else deniedDraft - code
                                }, modifier = Modifier.testTag("i08-deny-$code"))
                            Text(code)
                        }
                    }
                    Button(enabled = canManage && !busy && deniedDraft != p.denied, onClick = {
                        execute {
                            val next = identity.restrict(DenialChange(p.accountId, p.role, p.denied, deniedDraft))
                            permission = next; deniedDraft = next.denied
                            notice = t("Restrictions enregistrées", "Restrictions saved")
                        }
                    }, modifier = Modifier.testTag("i08-save-denials")) { Text(t("Appliquer restrictions", "Apply restrictions")) }
                }
            }
            "alerts" -> if (admin) {
                Text(t("Alertes locales", "Local alerts"), style = MaterialTheme.typography.titleLarge)
                if (alerts.isEmpty() && !busy) Text(t("Aucune alerte enregistrée", "No stored alerts"))
                alerts.forEach { alert -> Card(Modifier.fillMaxWidth().testTag("i08-alert-${alert.id}")) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${alert.type} · ${alert.createdAt}")
                        Text(alert.message)
                        Text(if (alert.resolvedAt == null) t("Active", "Active") else t("Résolue", "Resolved"))
                        if (!alert.read) TextButton(enabled = !busy, onClick = {
                            execute { support.markAlertRead(alert.id); alerts = support.alerts(offset) }
                        }) { Text(t("Marquer lu", "Mark read")) }
                        if (canManage && alert.resolvedAt == null) TextButton(enabled = !busy, onClick = {
                            execute { support.resolveAlert(alert.id); alerts = support.alerts(offset) }
                        }) { Text(t("Résoudre", "Resolve")) }
                    }
                } }
                PagingControls(offset, !busy, alerts.size == 40) { offset = it }
            }
            "audit" -> if (admin) {
                Text(t("Journal d’audit", "Audit journal"), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(auditFilter.action, { auditFilter = auditFilter.copy(action = it.take(100)) },
                    Modifier.fillMaxWidth().testTag("i08-audit-action"), label = { Text("Action exacte / Exact action") })
                OutlinedTextField(auditFilter.entity, { auditFilter = auditFilter.copy(entity = it.take(100)) },
                    Modifier.fillMaxWidth(), label = { Text(t("Entité", "Entity")) })
                OutlinedTextField(auditFilter.fromDate, { auditFilter = auditFilter.copy(fromDate = it.take(10)) },
                    Modifier.fillMaxWidth(), label = { Text(t("Depuis AAAA-MM-JJ", "From YYYY-MM-DD")) })
                OutlinedTextField(auditFilter.toDate, { auditFilter = auditFilter.copy(toDate = it.take(10)) },
                    Modifier.fillMaxWidth(), label = { Text(t("Jusqu’à AAAA-MM-JJ", "To YYYY-MM-DD")) })
                OutlinedTextField(auditActorInput, { auditActorInput = it.take(20).filter(Char::isDigit) },
                    Modifier.fillMaxWidth().testTag("i08-audit-actor"),
                    label = { Text(t("ID acteur (optionnel)", "Actor ID (optional)")) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("" to t("Tous", "All"), "success" to "SUCCESS", "failure" to "FAILURE").forEach { (value, label) ->
                        FilterChip(selected = auditFilter.outcome == value, enabled = !busy,
                            onClick = { auditFilter = auditFilter.copy(outcome = value) }, label = { Text(label) })
                    }
                }
                Button(enabled = !busy && (auditActorInput.isBlank() || auditActorInput.toLongOrNull() != null),
                    modifier = Modifier.testTag("i08-audit-apply"), onClick = {
                    val actorId = auditActorInput.toLongOrNull()
                    auditFilter = auditFilter.copy(actorId = actorId)
                    offset = 0
                    execute { audits = support.audit(auditFilter.copy(actorId = actorId, offset = 0)) }
                }) { Text(t("Appliquer", "Apply")) }
                if (audits.isEmpty() && !busy) Text(t("Aucun événement", "No events"))
                audits.forEach { event -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text("${event.at} · ${event.action} · ${event.outcome}")
                    Text("${event.entity} #${event.reference ?: "—"}")
                    Text("actor=${event.actorId ?: "—"} · responsible=${event.responsibleName ?: "—"}")
                    if (event.cashReference != null) Text("${event.cashReference}: ${event.cashAmount ?: "—"} ${event.cashCurrency ?: ""}")
                } } }
                PagingControls(offset, !busy, audits.size == 40) { offset = it }
            }
            "diagnostics" -> if (admin) {
                Text(t("État local (lecture seule)", "Local status (read-only)"), style = MaterialTheme.typography.titleLarge)
                health?.let { h ->
                    Text("Room schema: ${h.schemaVersion}")
                    Text("quick_check=${h.integrityQuick} · integrity_check=${h.integrityFull} · foreign_keys=${h.foreignKeys}")
                    Text("users=${h.users} · messages=${h.messages} · email queue=${h.pendingEmails}")
                    Text("DB=${h.databaseBytes?.toString() ?: "N/A"} bytes · free=${h.freeBytes?.toString() ?: "N/A"} bytes")
                    Text(t("Aucune réparation ni réinitialisation automatique.", "No automatic repair or reset."))
                    Text(t("Derniers refus/échecs audités (sans détails bruts)",
                        "Recent audited failures (without raw details)"))
                    recentFailures.take(10).forEach { event ->
                        Text("${event.at} · ${event.action} · ${event.entity} #${event.reference ?: "—"}")
                    }
                }
            }
            "help" -> {
                Text(t("Guide hors ligne", "Offline help"), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(helpSearch, { helpSearch = it.take(100) }, Modifier.fillMaxWidth().testTag("i08-help-search"),
                    label = { Text(t("Rechercher une procédure", "Search procedures")) })
                HelpArticles.all.filter { topic ->
                    val content = if (french) "${topic.titleFr} ${topic.bodyFr}" else "${topic.titleEn} ${topic.bodyEn}"
                    content.contains(helpSearch.trim(), ignoreCase = true)
                }.forEach { topic ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                        val title = if (french) topic.titleFr else topic.titleEn
                        val body = if (french) topic.bodyFr else topic.bodyEn
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(body)
                        if (documents != null) DocumentExportAction(documents, french,
                            t("Enregistrer l’aide en PDF", "Save help as PDF"),
                            request = { DocumentRequest("help-${topic.titleEn}", DocumentType.HELP, title,
                                java.time.Instant.now(), DocumentSnapshot.Help(title, body), if (french) "fr" else "en") },
                            save = saveDocument)
                    } }
                }
            }
        }
    }
}

@Composable
private fun PagingControls(offset: Int, enabled: Boolean, hasMore: Boolean, onPage: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = enabled && offset >= 40, onClick = { onPage(offset - 40) }) { Text("←") }
        Text("${offset + 1}…${offset + 40}")
        OutlinedButton(enabled = enabled && hasMore && offset < 10_000, onClick = { onPage(offset + 40) }) { Text("→") }
    }
}

private data class HelpArticle(val titleFr: String, val titleEn: String, val bodyFr: String, val bodyEn: String)
private object HelpArticles {
    val all = listOf(
        HelpArticle("Première utilisation", "First setup",
            "Créer un compte Owner puis renseigner les paramètres de la boutique. Aucun mot de passe par défaut.",
            "Create the Owner account, then configure shop settings. There is no default password."),
        HelpArticle("Connexion et comptes", "Sign-in and accounts",
            "Chaque personne utilise son identifiant et son mot de passe. La connexion ne signe pas la présence. Vérifier les droits avant toute opération.",
            "Each person signs in with their own credentials. Sign-in does not record attendance. Check rights before acting."),
        HelpArticle("Caisse et ventes", "Cash and sales",
            "Ouvrir la caisse autorisée, saisir les articles, vérifier les montants exacts puis valider. Un reçu est enregistré localement. Fermer la caisse avant de changer d’utilisateur.",
            "Open an authorized cash session, enter items, check exact amounts, and validate. The receipt is stored locally. Close cash before switching users."),
        HelpArticle("Achats et inventaires", "Purchases and inventory",
            "Un brouillon ne modifie pas le stock. Vérifier les quantités avant validation. Une opération validée ne doit jamais être validée deux fois.",
            "Drafts do not change stock. Check quantities before validating. A validated operation must not be applied twice."),
        HelpArticle("Messagerie locale", "Local chat",
            "Les messages restent sur cet appareil. Choisir le destinataire ou diffuser avec autorisation. Masquer un message ne le supprime pas chez les autres.",
            "Messages stay on this device. Choose a recipient or use authorized broadcast. Hiding a message does not remove it for others."),
        HelpArticle("Perte de l’appareil", "Lost device",
            "Les données sont locales. La sauvegarde produit et la restauration seront disponibles après I12 ; ne pas considérer le snapshot pré-migration comme une sauvegarde utilisateur.",
            "Data are local. Product backup and restore will be available after I12; the pre-migration snapshot is not a user backup."),
        HelpArticle("Documents et e-mail", "Documents and email",
            "La génération PDF est prévue en I10 et l’impression, le partage et le SMTP en I11. Aucune sortie externe n’est annoncée fonctionnelle avant leur validation.",
            "PDF generation is planned for I10 and printing, sharing, and SMTP for I11. External delivery is not available until validated."),
        HelpArticle("Urgence et passation", "Emergency and handover",
            "Arrêter les opérations en cas de défaut d’intégrité ; ne pas réinitialiser pour résoudre une erreur de connexion. Vérifier la caisse et l’identité active lors de la passation.",
            "Stop operations if integrity fails; never reset to fix a login failure. Check cash and current identity during handover."),
    )
}
