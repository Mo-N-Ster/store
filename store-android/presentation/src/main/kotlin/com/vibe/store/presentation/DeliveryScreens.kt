package com.vibe.store.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vibe.store.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SmtpSettingsPanel(service: DeliveryService, french: Boolean, busy: Boolean, onSchedule: () -> Unit) {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("587") }
    var username by remember { mutableStateOf("") }
    var sender by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var testRecipient by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(SmtpSecurityMode.STARTTLS_REQUIRED) }
    var configured by remember { mutableStateOf(false) }
    var passwordAvailable by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val t: (String, String) -> String = { fr, en -> if (french) fr else en }
    LaunchedEffect(service) {
        try {
            val current = service.smtpConfiguration()
            host = current.host; port = if (current.port == 0) "587" else current.port.toString()
            username = current.username; sender = current.sender; mode = current.securityMode
            configured = current.passwordConfigured; passwordAvailable = current.passwordAvailable
        } catch (_: Exception) { notice = t("Configuration indisponible.", "Configuration unavailable.") }
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(t("Configuration du courrier", "Email delivery configuration"), style = MaterialTheme.typography.titleLarge)
        Text(t("TLS implicite ou STARTTLS obligatoire. Le secret est chiffré par Android Keystore et n’est jamais relu dans l’écran.",
            "Implicit TLS or required STARTTLS. The secret is encrypted with Android Keystore and never read back into the screen."),
            style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(host, { host = it }, Modifier.fillMaxWidth().testTag("smtp-host"), label = { Text("SMTP host") }, singleLine = true, enabled = !busy && !working)
        OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, Modifier.fillMaxWidth(), label = { Text(t("Port", "Port")) }, singleLine = true, enabled = !busy && !working)
        OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text(t("Utilisateur SMTP", "SMTP username")) }, singleLine = true, enabled = !busy && !working)
        OutlinedTextField(sender, { sender = it }, Modifier.fillMaxWidth(), label = { Text(t("Adresse expéditeur", "Sender address")) }, singleLine = true, enabled = !busy && !working)
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth().testTag("smtp-secret"), label = { Text(if (passwordAvailable) t("Remplacer le secret (facultatif)", "Replace secret (optional)") else t("Secret SMTP", "SMTP secret")) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy && !working)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == SmtpSecurityMode.STARTTLS_REQUIRED, onClick = { mode = SmtpSecurityMode.STARTTLS_REQUIRED }, label = { Text("STARTTLS") }, enabled = !busy && !working)
            FilterChip(selected = mode == SmtpSecurityMode.IMPLICIT_TLS, onClick = { mode = SmtpSecurityMode.IMPLICIT_TLS }, label = { Text(t("TLS implicite", "Implicit TLS")) }, enabled = !busy && !working)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy && !working, modifier = Modifier.testTag("smtp-save"), onClick = {
                working = true; notice = null
                scope.launch {
                    try {
                        val saved = service.configureSmtp(SmtpConfigurationInput(host, port.toInt(), username, sender, mode, password))
                        configured = saved.passwordConfigured; passwordAvailable = saved.passwordAvailable; password = ""
                        notice = t("Configuration enregistrée.", "Configuration saved."); onSchedule()
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { notice = t("Configuration refusée. Vérifiez les champs.", "Configuration rejected. Check the fields.") }
                    finally { working = false }
                }
            }) { Text(t("Enregistrer", "Save")) }
            TextButton(enabled = !busy && !working && passwordAvailable, onClick = {
                working = true; notice = null
                scope.launch {
                    try {
                        val result = service.testSmtp(testRecipient)
                        notice = when (result) {
                            SmtpProbeResult.ACCEPTED -> t("Courriel de test accepté par le serveur SMTP.", "Test email accepted by the SMTP server.")
                            SmtpProbeResult.CONFIGURATION_REQUIRED -> t("Secret SMTP à reconfigurer.", "SMTP secret must be configured again.")
                            SmtpProbeResult.REJECTED -> t("Test SMTP refusé.", "SMTP test rejected.")
                        }
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { notice = t("Test SMTP impossible.", "SMTP test unavailable.") }
                    finally { working = false }
                }
            }) { Text(t("Envoyer un test", "Send a test")) }
        }
        OutlinedTextField(testRecipient, { testRecipient = it }, Modifier.fillMaxWidth(), label = { Text(t("Destinataire du test (envoi réel)", "Test recipient (sends an email)")) }, singleLine = true, enabled = !busy && !working)
        if (configured && !passwordAvailable) Text(t("Secret absent ou Keystore indisponible. Reconfigurez le secret avant l’envoi.", "Secret missing or Keystore unavailable. Reconfigure it before sending."), color = MaterialTheme.colorScheme.error)
        notice?.let { Text(it, Modifier.testTag("smtp-status"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun EmailQueueScreen(service: DeliveryService, french: Boolean, onBack: () -> Unit, onSchedule: () -> Unit) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<EmailQueueEntry>?>(null) }
    var working by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val t: (String, String) -> String = { fr, en -> if (french) fr else en }
    suspend fun load() { rows = service.emailQueue(0, 100) }
    LaunchedEffect(service) { try { load() } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { rows = null; notice = t("Accès refusé ou liste indisponible.", "Access denied or queue unavailable.") } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(t("Courriers en attente", "Email queue"), style = MaterialTheme.typography.headlineSmall)
        if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
        notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        when {
            rows == null -> Text(t("Impossible de charger la queue.", "Could not load the queue."))
            rows!!.isEmpty() -> Text(t("Aucun courrier enregistré.", "No queued email."))
            else -> rows!!.forEach { row ->
                val statusLabel = when (row.status) {
                    EmailQueueStatus.PENDING -> t("En attente", "Pending")
                    EmailQueueStatus.SENDING -> t("Envoi en cours", "Sending")
                    EmailQueueStatus.SENT -> t("Accepté par le serveur", "Accepted by server")
                    EmailQueueStatus.FAILED -> t("Échec", "Failed")
                }
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(row.subject, style = MaterialTheme.typography.titleMedium)
                        Text("${row.recipient} · $statusLabel · ${row.attempts}/5")
                        row.nextAttemptAt?.let { Text(t("Prochaine tentative (UTC) : ", "Next attempt (UTC): ") + it.toString()) }
                        row.lastErrorCode?.let { Text(t("Code d’erreur : ", "Failure code: ") + it) }
                        if (row.status == EmailQueueStatus.FAILED) TextButton(enabled = !working, onClick = {
                            working = true
                            scope.launch {
                                try { service.retryEmail(row.id); load(); onSchedule() }
                                catch (cancel: CancellationException) { throw cancel }
                                catch (_: Exception) { notice = t("Relance refusée.", "Retry rejected.") }
                                finally { working = false }
                            }
                        }) { Text(t("Relancer", "Retry")) }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !working, onClick = { scope.launch { try { load() } catch (_: Exception) { notice = t("Actualisation impossible.", "Refresh failed.") } } }) { Text(t("Actualiser", "Refresh")) }
            TextButton(onClick = onBack) { Text(t("Retour", "Back")) }
        }
    }
}
