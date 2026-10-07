package com.vibe.store.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.vibe.store.api.PublicIdentity

private data class HomeAction(val route: String, val french: String, val english: String,
    val descriptionFr: String, val descriptionEn: String, val symbol: String)

/** Uses only permissions and services already available. No invented sales or report metrics. */
@Composable
fun StoreHome(identity: PublicIdentity, shopName: String, french: Boolean, enabled: Boolean,
    hasCatalog: Boolean, hasTeam: Boolean, onNavigate: (String) -> Unit,
    onSwitch: () -> Unit, onLogout: () -> Unit, hasPurchases: Boolean = false, hasInventories: Boolean = false) {
    fun label(fr: String, en: String) = if (french) fr else en
    val actions = buildList {
        if (hasCatalog && "PRODUCTS:READ" in identity.permissions)
            add(HomeAction("catalog", "Catalogue et stock", "Catalog and stock",
                "Articles, recherche et quantités", "Products, search and quantities", "C"))
        if (hasTeam && "EMPLOYEES:READ" in identity.permissions)
            add(HomeAction("employees", "Équipe", "Team",
                "Fiches et comptes autorisés", "Authorized profiles and accounts", "É"))
        if (hasTeam && "PRESENCE:READ" in identity.permissions)
            add(HomeAction("today", "Présences", "Attendance",
                "Pointage du jour et historique", "Today's attendance and history", "P"))
        if (identity.role in setOf("owner", "manager") && hasPurchases && "PURCHASES:READ" in identity.permissions) {
            add(HomeAction("purchases", "Achats", "Purchases", "Brouillons, réceptions et annulations", "Drafts, receiving and cancellations", "A"))
            add(HomeAction("suppliers", "Fournisseurs", "Suppliers", "Partenaires et coordonnées", "Partners and contact details", "F"))
        }
        if (identity.role in setOf("owner", "manager") && hasInventories && "STOCKS:READ" in identity.permissions)
            add(HomeAction("inventories", "Inventaires", "Inventories", "Comptages et rapprochements", "Counts and reconciliation", "I"))
        if ("SETTINGS:READ" in identity.permissions)
            add(HomeAction("settings", "Réglages", "Settings",
                "Informations de la boutique", "Shop information", "R"))
    }
    Column(Modifier.fillMaxWidth().testTag("home-dashboard"),
        verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(shopName.ifBlank { "STORE" }, style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("${identity.firstName} ${identity.lastName}", Modifier.testTag("authenticated-name"),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(label("Bienvenue dans votre espace de travail", "Welcome to your workspace"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Text(label("Accès rapides", "Quick access"), style = MaterialTheme.typography.titleLarge)
        if (actions.isEmpty()) {
            Text(label("Aucune rubrique supplémentaire n’est autorisée pour ce compte.",
                "No additional section is authorized for this account."))
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 600.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        actions.forEach { item ->
                            HomeActionCard(item, french, enabled, Modifier.fillMaxWidth(), onNavigate)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        actions.chunked(2).forEach { line ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                HomeActionCard(line[0], french, enabled, Modifier.weight(1f), onNavigate)
                                if (line.size == 2) HomeActionCard(line[1], french, enabled,
                                    Modifier.weight(1f), onNavigate)
                                else Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        Text(label("Compte et session", "Account and session"),
            style = MaterialTheme.typography.titleMedium)
        Text(label("La connexion ne déclenche aucun pointage.",
            "Signing in does not record attendance."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 440.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onSwitch, enabled = enabled,
                        modifier = Modifier.fillMaxWidth().testTag("account-switch")) {
                        Text(label("Changer d’utilisateur", "Switch user"))
                    }
                    TextButton(onClick = onLogout, enabled = enabled,
                        modifier = Modifier.fillMaxWidth().testTag("account-logout")) {
                        Text(label("Déconnexion", "Sign out"))
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onSwitch, enabled = enabled,
                        modifier = Modifier.testTag("account-switch")) {
                        Text(label("Changer d’utilisateur", "Switch user"))
                    }
                    TextButton(onClick = onLogout, enabled = enabled,
                        modifier = Modifier.testTag("account-logout")) {
                        Text(label("Déconnexion", "Sign out"))
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeActionCard(action: HomeAction, french: Boolean, enabled: Boolean,
    modifier: Modifier, onNavigate: (String) -> Unit) {
    ElevatedCard(onClick = { onNavigate(action.route) }, enabled = enabled,
        modifier = modifier.testTag("home-quick-${action.route}"),
        shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    Text(action.symbol, style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (french) action.french else action.english,
                    style = MaterialTheme.typography.titleSmall)
                Text(if (french) action.descriptionFr else action.descriptionEn,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
