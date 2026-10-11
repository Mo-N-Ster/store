package com.vibe.store.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.vibe.store.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun RecoveryPanel(
    service: RecoveryService,
    actor: PublicIdentity,
    french: Boolean,
    pickDestination: ((RecoveryTarget?) -> Unit) -> Unit,
    pickSource: ((RecoverySource?) -> Unit) -> Unit,
    onRestored: () -> Unit,
) {
    var working by remember(actor.id) { mutableStateOf(false) }
    var message by remember(actor.id) { mutableStateOf("") }
    var selected by remember(actor.id) { mutableStateOf<RecoverySource?>(null) }
    var preview by remember(actor.id) { mutableStateOf<RecoveryPreview?>(null) }
    var confirm by remember(actor.id) { mutableStateOf(false) }
    var resetConfirm by remember(actor.id) { mutableStateOf(false) }
    var resetPassword by remember(actor.id) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun text(fr: String, en: String) = if (french) fr else en
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text("Sauvegarde et récupération", "Backup and recovery"), style = MaterialTheme.typography.titleMedium)
        Text(text("Les sauvegardes contiennent des données commerciales sensibles et ne sont pas chiffrées par STORE. Gardez-les dans un emplacement privé sûr.",
            "Backups contain sensitive business data and are not encrypted by STORE. Keep them in a secure private location."),
            color = MaterialTheme.colorScheme.error)
        Button(enabled = !working, modifier = Modifier.testTag("i12-create-backup"), onClick = {
            pickDestination { destination ->
                if (destination != null) scope.launch {
                    working = true; message = ""
                    try {
                        service.createBackup(destination)
                        message = text("Sauvegarde créée. Vérifiez le fichier dans l’emplacement choisi.", "Backup written to the selected destination; provider durability is not verified.")
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { message = text("Échec de la sauvegarde.", "Backup failed.") }
                    finally { working = false }
                }
            }
        }) { Text(text("Créer une sauvegarde", "Create backup")) }
        OutlinedButton(enabled = !working, modifier = Modifier.testTag("i12-select-restore"), onClick = {
            pickSource { source ->
                if (source != null) scope.launch {
                    working = true; message = ""; preview = null; selected = null
                    try { preview = service.inspect(source); selected = source }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { message = text("Archive invalide ou inaccessible.", "Archive is invalid or unavailable.") }
                    finally { working = false }
                }
            }
        }) { Text(text("Vérifier une archive", "Inspect backup")) }
        if (message.isNotBlank()) Text(message, color = if (message.contains("failed", true) || message.contains("Échec", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        if (working) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("i12-progress"))
        preview?.let { item ->
            Text(text("Archive vérifiée : ${item.createdAt}, schéma ${item.schema}, ${item.entries.size} éléments, ${item.totalBytes} octets.",
                "Archive validated: ${item.createdAt}, schema ${item.schema}, ${item.entries.size} entries, ${item.totalBytes} bytes."),
                Modifier.testTag("i12-preview"))
            item.entries.take(10).forEach { entry -> Text("${entry.name} • ${entry.bytes} B") }
            if (item.entries.size > 10) Text(text(
                "Aperçu limité aux 10 premières entrées sur ${item.entries.size}.",
                "Preview limited to the first 10 of ${item.entries.size} entries.",
            ))
            Button(enabled = !working, modifier = Modifier.testTag("i12-restore-confirm"), onClick = { confirm = true }) {
                Text(text("Restaurer cette sauvegarde", "Restore this backup"))
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(text("Réinitialisation complète", "Full data reset"), style = MaterialTheme.typography.titleSmall)
        Text(text("Crée une sauvegarde de sécurité, puis efface les données locales après vérification du mot de passe propriétaire.",
            "Creates a safety backup, then replaces local data after verifying the Owner password."))
        PasswordField(text("Mot de passe propriétaire", "Owner password"), resetPassword, working, french,
            tag = "i12-reset-password") { resetPassword = it }
        OutlinedButton(enabled = !working && resetPassword.isNotEmpty(), modifier = Modifier.testTag("i12-reset-start"),
            onClick = { resetConfirm = true }) { Text(text("Réinitialiser STORE", "Reset STORE")) }
    }
    if (confirm && preview != null && selected != null) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(text("Remplacer les données actuelles ?", "Replace current data?")) },
        text = { Text(text("Une sauvegarde de sécurité sera créée avant la restauration. Votre session sera fermée.",
            "A safety backup will be created before restore. Your current session will be closed.")) },
        confirmButton = { TextButton(enabled = !working, modifier = Modifier.testTag("i12-confirm-restore-dialog"), onClick = {
            confirm = false; working = true
            scope.launch {
                try { service.restore(checkNotNull(selected), confirmed = true); preview = null; selected = null; onRestored() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { message = text("Restauration impossible ; les données existantes sont conservées.", "Restore failed; existing data remains active.") }
                finally { working = false }
            }
        }) { Text(text("Restaurer", "Restore")) } },
        dismissButton = { TextButton(enabled = !working, onClick = { confirm = false }) { Text(text("Annuler", "Cancel")) } },
    )
    if (resetConfirm) AlertDialog(
        onDismissRequest = { resetConfirm = false },
        title = { Text(text("Effacer les données locales ?", "Erase local data?")) },
        text = { Text(text("Cette action remplace la base locale par une base vide. La sauvegarde de sécurité sera conservée.",
            "This replaces the local database with an empty one. The safety backup will be retained.")) },
        confirmButton = { TextButton(enabled = !working, onClick = {
            resetConfirm = false; working = true; message = ""
            val password = resetPassword
            resetPassword = ""
            scope.launch {
                try { service.reset(password, confirmed = true); onRestored() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { message = text("Réinitialisation refusée ou impossible.", "Reset was denied or could not be completed.") }
                finally { working = false }
            }
        }) { Text(text("Effacer", "Erase")) } },
        dismissButton = { TextButton(enabled = !working, onClick = { resetConfirm = false }) { Text(text("Annuler", "Cancel")) } },
    )
}
